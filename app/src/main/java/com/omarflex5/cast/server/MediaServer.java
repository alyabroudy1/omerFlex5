package com.omarflex5.cast.server;

import android.util.Log;
import com.omarflex5.util.NetworkUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

/**
 * Local Proxy Server.
 * Acts as a middleman between the TV/Cast device and the upstream video server.
 * Handles:
 * 1. Injecting Headers (which DLNA/Chromecast can't do natively or reliably).
 * 2. Rewriting HLS manifests to route segment requests through the proxy.
 * 3. Handling CORS preflight requests.
 */
public class MediaServer extends NanoHTTPD {
    private static final String TAG = "MediaServer";
    private static MediaServer instance;

    // Server state
    private String currentVideoUrl;
    private Map<String, String> currentHeaders;

    private MediaServer(int port) {
        super("0.0.0.0", port); // Explicitly bind to all interfaces, not just localhost
    }

    public static synchronized MediaServer getInstance() {
        if (instance == null) {
            instance = new MediaServer(8080);
        }
        return instance;
    }

    /**
     * Starts the server for a specific video session.
     * 
     * @param videoUrl The direct (upstream) URL of the video/manifest.
     * @param headers  The headers required to play the video.
     * @return The local proxy URL to give to the Cast device (e.g.,
     *         http://192.168.1.X:8080/video).
     */
    public synchronized String startServer(String videoUrl, Map<String, String> headers) {
        this.currentVideoUrl = videoUrl;
        this.currentHeaders = headers;

        try {
            if (wasStarted()) {
                stop();
            }
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

            String localIp = NetworkUtils.getLocalIpAddress();
            String serverUrl = "http://" + localIp + ":" + getListeningPort() + "/video";

            Log.d(TAG, "=== Media Server Started ===");
            Log.d(TAG, "Server URL: " + serverUrl);
            Log.d(TAG, "Proxying: " + videoUrl);
            Log.d(TAG, "Listening on port: " + getListeningPort());
            Log.d(TAG, "Local IP: " + localIp);

            // Diagnostic: List all network interfaces
            try {
                java.util.List<java.net.NetworkInterface> interfaces = java.util.Collections
                        .list(java.net.NetworkInterface.getNetworkInterfaces());
                Log.d(TAG, "=== Available Network Interfaces ===");
                for (java.net.NetworkInterface intf : interfaces) {
                    java.util.List<java.net.InetAddress> addrs = java.util.Collections.list(intf.getInetAddresses());
                    for (java.net.InetAddress addr : addrs) {
                        Log.d(TAG, intf.getName() + ": " + addr.getHostAddress() + " (isLoopback="
                                + addr.isLoopbackAddress() + ")");
                    }
                }
                Log.d(TAG, "===================================");
            } catch (Exception e) {
                Log.e(TAG, "Failed to list network interfaces", e);
            }

            Log.d(TAG, "===========================");
            return serverUrl;

        } catch (IOException e) {
            Log.e(TAG, "Failed to start media server", e);
            return null;
        }
    }

    public void stopServer() {
        if (wasStarted()) {
            stop();
            Log.d(TAG, "Media server stopped");
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, "Request: " + method + " " + uri);

        // 1. CORS Preflight
        if (Method.OPTIONS.equals(method)) {
            return createCorsResponse(Response.Status.OK, "text/plain", "");
        }

        if (!Method.GET.equals(method)) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed");
        }

        try {
            // 2. Handle /proxy?url=... requests (from rewritten manifests)
            if (uri.startsWith("/proxy")) {
                String targetUrl = session.getParms().get("url");
                if (targetUrl != null) {
                    return proxyRequest(session, URLDecoder.decode(targetUrl, "UTF-8"));
                }
            }

            // 3. Handle main /video URL and relative HLS paths
            String remoteUrl;
            if ("/video".equals(uri)) {
                // This is the entry point
                remoteUrl = currentVideoUrl;
                if (remoteUrl == null) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No video configured");
                }
            } else {
                // Determine absolute URL for relative path (HLS segment or sub-manifest)
                remoteUrl = buildRemoteUrl(currentVideoUrl, uri);
                if (remoteUrl == null) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Resource not found");
                }
            }

            return proxyRequest(session, remoteUrl);

        } catch (Exception e) {
            Log.e(TAG, "Error serving request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain",
                    "Internal Error: " + e.getMessage());
        }
    }

    private Response proxyRequest(IHTTPSession session, String remoteUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(remoteUrl);
            connection = (HttpURLConnection) url.openConnection();

            // Handle HEAD requests separately
            boolean isHead = Method.HEAD.equals(session.getMethod());
            connection.setRequestMethod(isHead ? "HEAD" : "GET");

            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);

            // Inject Headers
            if (currentHeaders != null) {
                for (Map.Entry<String, String> header : currentHeaders.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            // Forward Range header for seeking
            String range = session.getHeaders().get("range");
            if (range != null) {
                connection.setRequestProperty("Range", range);
            }

            int responseCode = connection.getResponseCode();

            // Handle Content
            InputStream inputStream = (responseCode >= 400) ? connection.getErrorStream() : connection.getInputStream();
            String contentType = connection.getContentType();
            long contentLength = connection.getContentLengthLong();

            // 1. Force valid MIME type for DLNA
            if (contentType == null || contentType.equals("application/octet-stream")) {
                if (remoteUrl.contains(".m3u8"))
                    contentType = "application/vnd.apple.mpegurl";
                else
                    contentType = "video/mp4"; // robust default
            }

            // 2. Rewrite HLS Manifests (only on GET)
            if (!isHead && (contentType.contains("mpegurl") || remoteUrl.endsWith(".m3u8"))) {
                String manifest = readStreamToString(inputStream);
                String processedManifest = rewriteHlsManifest(manifest, remoteUrl);
                return createCorsResponse(Response.Status.OK, "application/vnd.apple.mpegurl", processedManifest);
            }

            // 3. Serve Content (HEAD or GET)
            Response response;
            if (isHead) {
                response = newFixedLengthResponse(Response.Status.lookup(responseCode), contentType, "");
            } else {
                if (contentLength > 0) {
                    // Use fixed length if known (better for seeking)
                    response = newFixedLengthResponse(Response.Status.lookup(responseCode), contentType, inputStream,
                            contentLength);
                } else {
                    // Fallback to chunked
                    response = newChunkedResponse(Response.Status.lookup(responseCode), contentType, inputStream);
                }
            }

            // Forward critical headers
            if (contentLength > 0) {
                response.addHeader("Content-Length", String.valueOf(contentLength));
            }

            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null) {
                response.addHeader("Content-Range", contentRange);
            }

            String acceptRanges = connection.getHeaderField("Accept-Ranges");
            if (acceptRanges != null) {
                response.addHeader("Accept-Ranges", acceptRanges);
            } else {
                // DLNA often needs this to try seeking
                response.addHeader("Accept-Ranges", "bytes");
            }

            return addCorsHeaders(response);

        } catch (Exception e) {
            Log.e(TAG, "Proxy error for " + remoteUrl, e);
            if (connection != null)
                connection.disconnect();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Proxy Error");
        }
    }

    private String rewriteHlsManifest(String manifest, String baseUrl) {
        // Simple line-by-line rewrite
        StringBuilder sb = new StringBuilder();
        String localBase = "http://" + NetworkUtils.getLocalIpAddress() + ":" + getListeningPort();

        for (String line : manifest.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#EXT")) {
                // If it's a key or media URI inside a tag, we might need advanced parsing.
                // For now, simpler implementation: assume segment URLs are on their own lines
                // or handle URI="..." attributes if needed.
                if (trimmed.contains("URI=\"")) {
                    // Regex or simple string replacement could go here for encryption
                    // keys/subtitles
                }
                sb.append(line).append("\n");
                continue;
            }

            // It's a segment URL
            try {
                URL absoluteUrl = new URL(new URL(baseUrl), trimmed);
                String proxyUrl = localBase + "/proxy?url=" + URLEncoder.encode(absoluteUrl.toString(), "UTF-8");
                sb.append(proxyUrl).append("\n");
            } catch (Exception e) {
                sb.append(line).append("\n"); // Fallback
            }
        }
        return sb.toString();
    }

    private String buildRemoteUrl(String base, String path) {
        try {
            if (path.startsWith("/"))
                path = path.substring(1);
            return new URL(new URL(base), path).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String readStreamToString(InputStream has) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(has));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
            sb.append(line).append("\n");
        return sb.toString();
    }

    private Response createCorsResponse(Response.Status status, String mimeType, String txt) {
        Response response = newFixedLengthResponse(status, mimeType, txt);
        return addCorsHeaders(response);
    }

    private Response addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Headers", "Range, Content-Type, User-Agent, Accept");
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        response.addHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range, Accept-Ranges");
        return response;
    }
}
