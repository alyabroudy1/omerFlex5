package com.omarflex5.temp.webtest;

import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class WebViewDataSource extends BaseDataSource implements HttpDataSource {

    private static final String TAG = "WebViewDataSource";
    private static final int TIMEOUT_MS = 8000; // Reduced timeout for faster fallback

    private final WebViewFetcher fetcher;
    private final String userAgent;
    private DataSpec dataSpec;
    private boolean opened = false;
    private long bytesRead = 0;
    private long contentLength = -1;

    // Queue for receiving data chunks from WebView
    private BlockingQueue<DataChunk> dataQueue;

    // Fallback DataSource
    private HttpDataSource fallbackDataSource;
    private boolean useFallback = false;

    public WebViewDataSource(WebViewFetcher fetcher, String userAgent) {
        super(true); // isNetwork = true
        this.fetcher = fetcher;
        this.userAgent = userAgent;
    }

    @Override
    public long open(@NonNull DataSpec dataSpec) throws HttpDataSourceException {
        Log.d(TAG, "Opening DataSource for: " + dataSpec.uri);
        this.dataSpec = dataSpec;
        this.opened = true;
        this.bytesRead = 0;
        this.useFallback = false;
        this.dataQueue = new LinkedBlockingQueue<>();

        // 1. Attempt WebView Fetch
        String url = dataSpec.uri.toString();
        fetcher.fetchUrl(url, new WebViewFetcher.FetchCallback() {
            @Override
            public void onDataReceived(byte[] data, int length) {
                queueChunk(new DataChunk(data, length, false, null));
            }

            @Override
            public void onComplete(long totalBytes) {
                contentLength = totalBytes;
                queueChunk(new DataChunk(null, 0, true, null));
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "WebView fetch error: " + error);
                queueChunk(new DataChunk(null, 0, true, error));
            }
        });

        // 2. Wait for first chunk or error to decide strategy
        try {
            DataChunk firstChunk = dataQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (firstChunk == null) {
                Log.w(TAG, "Timeout waiting for WebView, switching to fallback");
                return switchToFallback(dataSpec);
            }

            if (firstChunk.error != null) {
                Log.w(TAG, "WebView reported error (" + firstChunk.error + "), switching to fallback");
                return switchToFallback(dataSpec);
            }

            // Success! We have data.
            currentChunk = firstChunk;
            return contentLength;

        } catch (InterruptedException e) {
            throw new HttpDataSourceException(new IOException("Interrupted while opening", e), dataSpec,
                    HttpDataSourceException.TYPE_OPEN, 1);
        } catch (IOException e) {
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_OPEN, 1);
        }
    }

    private void queueChunk(DataChunk chunk) {
        try {
            dataQueue.put(chunk);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to queue chunk", e);
        }
    }

    private long switchToFallback(DataSpec dataSpec) throws IOException {
        useFallback = true;
        Log.d(TAG, "Initializing fallback DefaultHttpDataSource");

        fallbackDataSource = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)
                .createDataSource();

        // Copy cookies from WebView
        String cookies = CookieManager.getInstance().getCookie(dataSpec.uri.toString());
        if (cookies != null) {
            Log.d(TAG, "Using cookies for fallback: " + cookies);
            fallbackDataSource.setRequestProperty("Cookie", cookies);
        }

        // Forward any other headers if needed (not implemented here as WebView handled
        // them)

        return fallbackDataSource.open(dataSpec);
    }

    // Buffer for current chunk being read
    private DataChunk currentChunk = null;
    private int currentChunkOffset = 0;

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws HttpDataSourceException {
        if (!opened)
            return -1;

        if (useFallback) {
            try {
                return fallbackDataSource.read(buffer, offset, length);
            } catch (IOException e) {
                throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_READ, 1);
            }
        }

        try {
            // If we don't have a current chunk, get one from the queue
            if (currentChunk == null) {
                currentChunk = dataQueue.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                currentChunkOffset = 0;

                if (currentChunk == null) {
                    throw new IOException("Timeout waiting for data stream");
                }
            }

            // Check for error in stream
            if (currentChunk.error != null) {
                throw new IOException("Stream error: " + currentChunk.error);
            }

            // Check for end of stream
            if (currentChunk.isEnd) {
                return -1;
            }

            // Calculate how much we can copy
            int bytesAvailableInChunk = currentChunk.length - currentChunkOffset;
            int bytesToCopy = Math.min(bytesAvailableInChunk, length);

            // Copy data
            System.arraycopy(currentChunk.data, currentChunkOffset, buffer, offset, bytesToCopy);

            // Update state
            currentChunkOffset += bytesToCopy;
            bytesRead += bytesToCopy;

            // If we've read the entire chunk, clear it so we get the next one next time
            if (currentChunkOffset >= currentChunk.length) {
                currentChunk = null;
                currentChunkOffset = 0;
            }

            return bytesToCopy;

        } catch (InterruptedException e) {
            throw new HttpDataSourceException(new IOException("Interrupted", e), dataSpec,
                    HttpDataSourceException.TYPE_READ, 1);
        } catch (IOException e) {
            throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_READ, 1);
        }
    }

    @Override
    public Uri getUri() {
        return useFallback && fallbackDataSource != null ? fallbackDataSource.getUri()
                : (dataSpec != null ? dataSpec.uri : null);
    }

    @Override
    public void close() throws HttpDataSourceException {
        opened = false;
        if (useFallback && fallbackDataSource != null) {
            try {
                fallbackDataSource.close();
            } catch (IOException e) {
                throw new HttpDataSourceException(e, dataSpec, HttpDataSourceException.TYPE_CLOSE, 1);
            }
        }
        if (dataQueue != null) {
            dataQueue.clear();
        }
    }

    // HttpDataSource interface methods
    @Override
    public void setRequestProperty(@NonNull String name, @NonNull String value) {
        if (useFallback && fallbackDataSource != null) {
            fallbackDataSource.setRequestProperty(name, value);
        }
    }

    @Override
    public void clearRequestProperty(@NonNull String name) {
        if (useFallback && fallbackDataSource != null) {
            fallbackDataSource.clearRequestProperty(name);
        }
    }

    @Override
    public void clearAllRequestProperties() {
        if (useFallback && fallbackDataSource != null) {
            fallbackDataSource.clearAllRequestProperties();
        }
    }

    @Override
    public int getResponseCode() {
        return useFallback && fallbackDataSource != null ? fallbackDataSource.getResponseCode() : 200;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return useFallback && fallbackDataSource != null ? fallbackDataSource.getResponseHeaders()
                : Collections.emptyMap();
    }

    // Data chunk holder
    private static class DataChunk {
        final byte[] data;
        final int length;
        final boolean isEnd;
        final String error;

        DataChunk(byte[] data, int length, boolean isEnd, String error) {
            this.data = data;
            this.length = length;
            this.isEnd = isEnd;
            this.error = error;
        }
    }

    // Factory for creating WebViewDataSource instances
    public static class Factory implements HttpDataSource.Factory {
        private final WebViewFetcher fetcher;
        private final String userAgent;

        public Factory(WebViewFetcher fetcher, String userAgent) {
            this.fetcher = fetcher;
            this.userAgent = userAgent;
        }

        @NonNull
        @Override
        public HttpDataSource createDataSource() {
            return new WebViewDataSource(fetcher, userAgent);
        }

        @NonNull
        @Override
        public HttpDataSource.Factory setDefaultRequestProperties(
                @NonNull Map<String, String> defaultRequestProperties) {
            return this;
        }
    }
}
