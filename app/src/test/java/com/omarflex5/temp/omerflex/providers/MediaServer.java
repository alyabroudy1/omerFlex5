package com.omarflex5.temp.omerflex.providers;

import android.util.Log;
import com.omerflex.entity.Movie;
import com.omerflex.service.utils.NetworkUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

public class MediaServer extends NanoHTTPD {
    private static final String TAG = "MediaServer";
    private static MediaServer instance;
    private Movie currentMovie;
    private Map<String, String> headers;

    // Patterns to identify different file types
    private static final Pattern HLS_PATTERN = Pattern.compile("\\.m3u8", Pattern.CASE_INSENSITIVE);
    private static final Pattern TS_PATTERN = Pattern.compile("\\.ts", Pattern.CASE_INSENSITIVE);
    private static final Pattern MP4_PATTERN = Pattern.compile("\\.mp4", Pattern.CASE_INSENSITIVE);

    public static MediaServer getInstance() {
        if (instance == null) {
            instance = new MediaServer(8080);
        }
        return instance;
    }

    private MediaServer(int port) {
        super(port);
    }

    public String startServer(Movie movie, Map<String, String> headers) {
        this.currentMovie = movie;
        this.headers = headers;

        try {
            if (isRunning()) {
                stop();
            }
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

            String localIp = NetworkUtils.getLocalIpAddress();
            String serverUrl = "http://" + localIp + ":" + getListeningPort() + "/video";

            Log.d(TAG, "Media server started: " + serverUrl);
            Log.d(TAG, "Original URL: " + movie.getVideoUrl());
            return serverUrl;

        } catch (IOException e) {
            Log.e(TAG, "Failed to start media server", e);
            return null;
        }
    }

    public void stopServer() {
        if (isRunning()) {
            stop();
            Log.d(TAG, "Media server stopped");
        }
    }

    public boolean isRunning() {
        return wasStarted();
    }



    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();

        // Handle CORS preflight requests
        if (Method.OPTIONS.equals(session.getMethod())) {
            Response response = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "*, Content-Type, Range, User-Agent");
            response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD");
            response.addHeader("Access-Control-Max-Age", "86400");
            return response;
        }
        Log.d(TAG, "Request: " + session.getMethod().name() + " " + uri);

        if (!Method.GET.equals(session.getMethod())) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed");
        }


        try {
            if (uri.startsWith("/proxy")) {
                Map<String, String> params = session.getParms();
                String targetUrl = params.get("url");
                if (targetUrl != null) {
                    try {
                        String decodedUrl = URLDecoder.decode(targetUrl, "UTF-8");
                        return proxyRequest(session, decodedUrl);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in proxy endpoint", e);
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid URL");
                    }
                }
            }

            String originalUrl = currentMovie.getVideoUrl().split("\\|")[0];
            String remoteUrl;

            if ("/video".equals(uri)) {
                remoteUrl = originalUrl;
                Log.d(TAG, "Serving main video from: " + remoteUrl);
            } else {
                // Handle HLS segments and other relative URLs
                remoteUrl = buildRemoteUrl(originalUrl, uri);
                if (remoteUrl == null) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
                }
                Log.d(TAG, "Serving segment from: " + remoteUrl);
            }

            return proxyRequest(session, remoteUrl);

        } catch (Exception e) {
            Log.e(TAG, "Error serving request for URI: " + uri, e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Internal server error: " + e.getMessage());
        }
    }

    private String buildRemoteUrl(String originalUrl, String requestUri) {
        try {
            // Remove leading slash if present
            String relativePath = requestUri.startsWith("/") ? requestUri.substring(1) : requestUri;

            // For proxy URLs
            if (requestUri.startsWith("/proxy")) {
                return null; // Let the proxy handling deal with it
            }

            // Build the full URL from the original base
            URL baseUrl = new URL(originalUrl);
            String basePath = baseUrl.getPath();

            // Find the directory of the original URL
            int lastSlash = basePath.lastIndexOf('/');
            String baseDirectory = lastSlash != -1 ? basePath.substring(0, lastSlash + 1) : "/";

            // Construct the full URL
            String fullPath = baseDirectory + relativePath;
            URL fullUrl = new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(), fullPath);

            return fullUrl.toString();

        } catch (Exception e) {
            Log.e(TAG, "Error building remote URL for: " + requestUri, e);
            return null;
        }
    }



    private Response proxyRequest(IHTTPSession session, String remoteUrl) {
        HttpURLConnection connection = null;
        try {
            Log.d(TAG, "Proxying to: " + remoteUrl);

            URL url = new URL(remoteUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000); // Reduced from 30s
            connection.setReadTimeout(15000);    // Reduced from 30s
            connection.setInstanceFollowRedirects(true); // Handle redirects automatically

            // Add original headers
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            // Copy important headers from client request
            Map<String, String> clientHeaders = session.getHeaders();
            String rangeHeader = clientHeaders.get("range");
            if (rangeHeader != null) {
                connection.setRequestProperty("Range", rangeHeader);
                Log.d(TAG, "Forwarding Range header: " + rangeHeader);
            }

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Remote server response: " + responseCode);

            // Handle redirects
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                String location = connection.getHeaderField("Location");
                if (location != null) {
                    Log.d(TAG, "Following redirect to: " + location);
                    return proxyRequest(session, location);
                }
            }

            String contentType = getContentType(remoteUrl, connection.getContentType());
            long contentLength = connection.getContentLengthLong();

            InputStream inputStream;
            try {
                inputStream = connection.getInputStream();
            } catch (IOException e) {
                inputStream = connection.getErrorStream();
            }

            // SPECIAL HANDLING FOR HLS MANIFESTS
            if (contentType.equals("application/vnd.apple.mpegurl") ||
                    contentType.equals("application/x-mpegurl")) {

                // Read the entire manifest to process it
                String manifestContent = readStreamToString(inputStream);
                String processedManifest = processHlsManifestForCast(manifestContent, remoteUrl);

                Log.d(TAG, "Processed HLS manifest for Cast device");
                Response response = newFixedLengthResponse(Response.Status.OK, contentType, processedManifest);
                response.addHeader("Access-Control-Allow-Origin", "*");
                response.addHeader("Access-Control-Allow-Headers", "*, Content-Type, Range, User-Agent");
                response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD");
                response.addHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range");
                response.addHeader("Access-Control-Max-Age", "86400");
                return response;
            }

            Response response = newChunkedResponse(
                    Response.Status.lookup(responseCode),
                    contentType,
                    inputStream
            );

            // Copy headers from remote response
            copyHeaders(connection, response);

            // Add CORS headers for browser compatibility
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "*");
            response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS");

            Log.d(TAG, "Successfully proxied: " + remoteUrl + " as " + contentType);
            return response;

        } catch (Exception e) {
            Log.e(TAG, "Error proxying request for: " + remoteUrl, e);
            if (connection != null) {
                connection.disconnect();
            }
            // Return a proper error response instead of crashing
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain",
                    "Unable to fetch resource: " + e.getMessage());
        }
    }

    private String readStreamToString(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        reader.close();
        return stringBuilder.toString();
    }

    private String processHlsManifestForCast(String manifestContent, String baseUrl) {
        // Then try a SIMPLE approach - minimal processing
        return minimalHlsProcessing(manifestContent, baseUrl);
    }

    private String minimalHlsProcessing(String manifestContent, String baseUrl) {
        try {
            String serverBase = "http://" + NetworkUtils.getLocalIpAddress() + ":" + getListeningPort();
            String[] lines = manifestContent.split("\n");
            StringBuilder processed = new StringBuilder();

            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty()) {
                    processed.append(line).append("\n");
                    continue;
                }

                if (trimmedLine.startsWith("#")) {
                    // It's a tag. Check for a URI attribute.
                    int uriIndex = trimmedLine.indexOf("URI=\"");
                    if (uriIndex != -1) {
                        int startIndex = uriIndex + 5;
                        int endIndex = trimmedLine.indexOf("\"", startIndex);
                        if (endIndex != -1) {
                            String uri = trimmedLine.substring(startIndex, endIndex);
                            URL base = new URL(baseUrl);
                            URL absoluteUrl = new URL(base, uri);
                            String proxyPath = "/proxy?url=" + URLEncoder.encode(absoluteUrl.toString(), "UTF-8");
                            String finalUrl = serverBase + proxyPath;
                            String rewrittenLine = line.replace(uri, finalUrl);
                            processed.append(rewrittenLine).append("\n");
                        } else {
                            processed.append(line).append("\n");
                        }
                    } else {
                        processed.append(line).append("\n");
                    }
                } else {
                    // It's a URL on its own line.
                    try {
                        URL base = new URL(baseUrl);
                        URL absoluteUrl = new URL(base, trimmedLine);
                        String proxyPath = "/proxy?url=" + URLEncoder.encode(absoluteUrl.toString(), "UTF-8");
                        String finalUrl = serverBase + proxyPath;
                        processed.append(finalUrl).append("\n");
                    } catch (Exception e) {
                        Log.w(TAG, "HLS line rewrite failed for: " + line, e);
                        processed.append(line).append("\n");
                    }
                }
            }

            Log.d(TAG, "✅ minimalHlsProcessing: All segment URLs rewritten for Cast compatibility");
            return processed.toString();

        } catch (Exception e) {
            Log.e(TAG, "Error in minimal HLS processing", e);
            return manifestContent;
        }
    }






    private String getContentType(String url, String originalContentType) {
        // HLS manifests
        if (url.contains(".m3u8") ||
                (originalContentType != null &&
                        (originalContentType.contains("mpegurl") ||
                                originalContentType.contains("vnd.apple.mpegurl")))) {
            return "application/vnd.apple.mpegurl";
        }
        // TS segments
        else if (url.contains(".ts")) {
            return "video/mp2t";
        }
        // MP4 files
        else if (url.contains(".mp4")) {
            return "video/mp4";
        }
        // Fallback to original or default
        else if (originalContentType != null) {
            return originalContentType;
        } else {
            return "application/octet-stream";
        }
    }

    private void copyHeaders(HttpURLConnection connection, Response response) {
        try {
            Map<String, List<String>> headerFields = connection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    for (String value : entry.getValue()) {
                        String key = entry.getKey();

                        // Skip problematic headers
                        if ("Content-Encoding".equalsIgnoreCase(key) ||
                                "Transfer-Encoding".equalsIgnoreCase(key) ||
                                "Content-Length".equalsIgnoreCase(key) ||
                                "Connection".equalsIgnoreCase(key)) {
                            continue;
                        }

                        response.addHeader(key, value);
                    }
                }
            }

            // Essential CORS headers for TV compatibility
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.addHeader("Access-Control-Allow-Headers", "*, Content-Type, Range, User-Agent");
            response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD");
            response.addHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range");
            response.addHeader("Access-Control-Max-Age", "86400");

            // Important for HLS
            response.addHeader("Accept-Ranges", "bytes");
            response.addHeader("Connection", "keep-alive");

        } catch (Exception e) {
            Log.w(TAG, "Error copying headers", e);
        }
    }
}