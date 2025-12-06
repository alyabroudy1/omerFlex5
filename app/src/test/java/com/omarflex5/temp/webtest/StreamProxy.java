package com.omarflex5.temp.webtest;

import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreamProxy implements Runnable {

    private static final String TAG = "StreamProxy";
    private int port;
    private ServerSocket socket;
    private Thread thread;
    private boolean isRunning = true;
    private ExecutorService executorService;
    private Map<String, String> headers;
    private Map<String, String> contentCache; // Cache for m3u8 and other content

    public StreamProxy(Map<String, String> headers, Map<String, String> cache) {
        this.headers = headers;
        this.contentCache = cache;
        executorService = Executors.newFixedThreadPool(4); // Handle multiple segments concurrently
    }

    public void init() {
        try {
            socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
            socket.setSoTimeout(5000);
            port = socket.getLocalPort();
            Log.d(TAG, "Proxy started on port " + port);
            thread = new Thread(this);
            thread.start();
        } catch (IOException e) {
            Log.e(TAG, "Error starting proxy", e);
        }
    }

    public String getProxyUrl(String originalUrl) {
        return String.format("http://127.0.0.1:%d/%s", port, originalUrl);
    }

    public void stop() {
        isRunning = false;
        if (thread != null) {
            thread.interrupt();
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                Socket client = socket.accept();
                if (client == null)
                    continue;
                executorService.execute(new ProxyClient(client));
            } catch (SocketException e) {
                // Socket closed
                Log.d(TAG, "Socket closed");
            } catch (IOException e) {
                Log.e(TAG, "Error accepting connection", e);
            }
        }
    }

    private class ProxyClient implements Runnable {
        private final Socket client;

        public ProxyClient(Socket client) {
            this.client = client;
        }

        @Override
        public void run() {
            HttpURLConnection connection = null;
            InputStream is = null;
            OutputStream os = null;
            Socket socketToClose = client;

            try {
                Log.d(TAG, "[ProxyClient] Starting to handle request");

                // 1. Read Request from Client (ExoPlayer)
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String requestLine = reader.readLine();

                if (requestLine == null) {
                    Log.w(TAG, "[ProxyClient] Request line is null, returning");
                    return;
                }

                Log.d(TAG, "[ProxyClient] Request: " + requestLine);

                // Read and discard remaining headers from client
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    Log.v(TAG, "[ProxyClient] Client header: " + line);
                }

                // Parse URL from "GET /https://example.com/video.m3u8 HTTP/1.1"
                String[] parts = requestLine.split(" ");
                if (parts.length < 2) {
                    Log.e(TAG, "[ProxyClient] Invalid request format: " + requestLine);
                    return;
                }

                String urlStr = parts[1].substring(1); // Remove leading slash
                if (!urlStr.startsWith("http")) {
                    Log.e(TAG, "[ProxyClient] Invalid URL format: " + urlStr);
                    return;
                }

                Log.d(TAG, "[ProxyClient] Proxying to: " + urlStr);

                // CHECK CACHE FIRST
                // If this URL has cached content (from WebView), serve it directly
                if (urlStr.contains(".m3u8") && contentCache != null) {
                    String cachedContent = contentCache.get(urlStr);
                    if (cachedContent != null) {
                        Log.d(TAG,
                                "[ProxyClient] CACHE HIT! Serving cached m3u8 (" + cachedContent.length() + " bytes)");

                        // Send success response
                        os = client.getOutputStream();
                        os.write("HTTP/1.1 200 OK\r\n".getBytes());
                        os.write("Content-Type: application/vnd.apple.mpegurl\r\n".getBytes());

                        byte[] contentBytes = cachedContent.getBytes("UTF-8");
                        os.write(("Content-Length: " + contentBytes.length + "\r\n").getBytes());
                        os.write("\r\n".getBytes());
                        os.write(contentBytes);
                        os.flush();

                        Log.d(TAG, "[ProxyClient] Cached content served successfully");
                        return; // Done!
                    } else {
                        Log.d(TAG, "[ProxyClient] Cache miss, fetching from server");
                    }
                }

                // 2. Connect to Real Server
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);

                // 3. Add Headers (with filtering)
                Log.d(TAG, "[ProxyClient] Adding " + (headers != null ? headers.size() : 0) + " headers");
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();

                        // Skip Host header to let HttpURLConnection handle it
                        if ("Host".equalsIgnoreCase(key)) {
                            Log.v(TAG, "[ProxyClient] Skipping Host header");
                            continue;
                        }

                        // CRITICAL: Filter out sec-ch-ua headers that identify as WebView
                        if (key.toLowerCase().startsWith("sec-ch-ua")) {
                            Log.d(TAG, "[ProxyClient] FILTERED identifying header: " + key);
                            continue;
                        }

                        // Filter out Origin if it doesn't match the actual video domain
                        if ("Origin".equalsIgnoreCase(key)) {
                            Log.d(TAG, "[ProxyClient] FILTERED Origin header (cross-origin issue)");
                            continue;
                        }

                        connection.setRequestProperty(key, value);
                        Log.d(TAG, "[ProxyClient] Header: " + key + " = " +
                                (value.length() > 50 ? value.substring(0, 50) + "..." : value));
                    }
                }

                // 4. Execute Request
                Log.d(TAG, "[ProxyClient] Connecting to server...");
                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();
                Log.d(TAG, "[ProxyClient] Server Response: " + responseCode + " " + responseMessage);

                // 5. Send Response to Client
                os = client.getOutputStream();
                String statusLine = "HTTP/1.1 " + responseCode + " " + responseMessage + "\r\n";
                os.write(statusLine.getBytes());

                // Send Headers (Forward relevant ones)
                String contentType = connection.getContentType();
                if (contentType != null) {
                    os.write(("Content-Type: " + contentType + "\r\n").getBytes());
                    Log.d(TAG, "[ProxyClient] Content-Type: " + contentType);
                }
                int contentLength = connection.getContentLength();
                if (contentLength != -1) {
                    os.write(("Content-Length: " + contentLength + "\r\n").getBytes());
                    Log.d(TAG, "[ProxyClient] Content-Length: " + contentLength);
                }
                os.write("\r\n".getBytes()); // End of headers

                // 6. Stream Body
                if (responseCode == 200 || responseCode == 206) {
                    Log.d(TAG, "[ProxyClient] Streaming response body...");
                    is = new BufferedInputStream(connection.getInputStream());
                    byte[] buffer = new byte[8192];
                    int read;
                    long totalBytes = 0;
                    while ((read = is.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                        totalBytes += read;
                    }
                    os.flush();
                    Log.d(TAG, "[ProxyClient] Streamed " + totalBytes + " bytes successfully");
                } else {
                    Log.w(TAG, "[ProxyClient] Error response, forwarding error body");
                    // Forward error body if possible
                    is = connection.getErrorStream();
                    if (is != null) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                        os.flush();
                    }
                }

                Log.d(TAG, "[ProxyClient] Request completed successfully");

            } catch (Exception e) {
                Log.e(TAG, "[ProxyClient] Proxy Error: " + e.getMessage(), e);
            } finally {
                try {
                    if (is != null)
                        is.close();
                    if (os != null)
                        os.close();
                    if (socketToClose != null)
                        socketToClose.close();
                    if (connection != null)
                        connection.disconnect();
                    Log.d(TAG, "[ProxyClient] Cleanup completed");
                } catch (IOException e) {
                    Log.e(TAG, "[ProxyClient] Cleanup error", e);
                }
            }
        }
    }
}
