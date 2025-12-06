package com.omarflex5.temp.webtest;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements WebViewFetcher {

    private WebView webView;
    private TextView statusLog;
    private ProgressBar progressBar;
    private static final String TAG = "WebTest";
    // private static final String TARGET_URL = "https://cima4u.info"; // Example
    // Cloudflare site
    // private static final String TARGET_URL =
    // "https://z.laroza.now/search.php?keywords=%D8%A7%D9%8A%D9%84%D9%88%D9%84"; //
    // Example
    private static final String TARGET_URL = "https://r.laroza.now/play.php?vid=Y8EdR9tPk"; // Example
    // private static final String TARGET_URL = "https://www.faselhds.biz/movies";
    // // Example
    // Cloudflare
    // site

    private String cookies = "";
    private String currentUrl = "";
    private java.util.Map<String, java.util.Map<String, String>> videoHeadersMap = new java.util.HashMap<>();

    // Storage for cached m3u8 content
    private java.util.Map<String, String> m3u8CacheMap = new java.util.HashMap<>();

    private boolean isPlayerActive = false;
    private String lastDetectedVideoUrl = ""; // Prevent duplicate launches

    @Override
    protected void onResume() {
        super.onResume();
        // Reset the flag when returning to the activity
        isPlayerActive = false;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI controls
        webView = findViewById(R.id.webView);
        statusLog = findViewById(R.id.statusLog);
        progressBar = findViewById(R.id.progressBar);

        // Set up WebView
        setupWebView();

        // Register as global fetcher
        FetcherManager.getInstance().setFetcher(this);

        logStatus("Starting... Loading " + TARGET_URL);

        // Optional: Clear cookies for fresh session (comment out to keep existing
        // cookies)
        // Uncomment the lines below if you want to start fresh each time
        // CookieManager.getInstance().removeAllCookies(null);
        // CookieManager.getInstance().flush();
        // android.webkit.WebStorage.getInstance().deleteAllData();

        webView.loadUrl(TARGET_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        // CRITICAL: Enable JavaScript (required for video players)
        settings.setJavaScriptEnabled(true);

        // Enable WebView debugging for Chrome DevTools
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // Add JavaScript interface for AndroidFetcher
        webView.addJavascriptInterface(this, "Android");

        // Enable DOM storage and other features needed for video playback
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Disable cache to always get fresh content
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        // settings.setAppCacheEnabled(false);

        // Hardware acceleration for better JavaScript performance
        // This helps with Cloudflare's JavaScript challenges
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Modern Chrome Mobile User-Agent Strategy:
        // Use a realistic Chrome mobile user-agent to avoid bot detection.
        // This mimics a real mobile browser and helps bypass Cloudflare protection.
        settings.setUserAgentString(
                // CRITICAL: This User-Agent MUST match the Cronet version used in
                // PlayerActivity
                // Cronet 142.0.7432.0 sends "Chromium";v="142" in Client Hints.
                // If this string says "Chrome/120", Cloudflare detects the mismatch and blocks
                // it.
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.7432.0 Mobile Safari/537.36");

        // Enable Mixed Content (HTTP/HTTPS)
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // Cookie Manager - Enable persistence for Cloudflare cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        // Ensure cookies are persisted to disk
        cookieManager.flush();

        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        // Register MainActivity as AndroidFetcherBridge for data streaming
        webView.addJavascriptInterface(this, "AndroidFetcherBridge");

        // Set Custom WebViewClient
        webView.setWebViewClient(new SmartWebViewClient(this, "laroza.now", settings.getUserAgentString()));

        // Set WebChromeClient for console logging
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                Log.d("WebViewConsole", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return true;
            }
        });
    }

    public void logStatus(String message) {
        runOnUiThread(() -> {
            statusLog.setText(message + "\n" + statusLog.getText());
            Log.d(TAG, message);
        });
    }

    public void showRedirectionDialog(String url) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("External Link")
                    .setMessage("Do you want to visit: " + url)
                    .setPositiveButton("Yes", (dialog, which) -> webView.loadUrl(url))
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    public void onCloudflareDetected(boolean detected) {
        runOnUiThread(() -> {
            if (detected) {
                logStatus("Cloudflare Challenge Detected!");
                Toast.makeText(this, "Please update 'Android System WebView' from Play Store", Toast.LENGTH_LONG)
                        .show();
            } else {
                logStatus("Cloudflare Check Passed!");
            }
        });
    }

    public void onHtmlExtracted(String html, String jsCookies) {
        runOnUiThread(() -> {
            String url = webView.getUrl();
            if (url == null) {
                logStatus("WARNING: WebView URL is null, cannot extract cookies.");
                return;
            }
            String cookies = CookieManager.getInstance().getCookie(url);
            logStatus("HTML Extracted (" + html.length() + " chars)");
            logStatus("Cookies: " + cookies);

            if (cookies != null && cookies.contains("cf_clearance")) {
                logStatus("SUCCESS: cf_clearance found!");
            } else {
                logStatus("WARNING: cf_clearance NOT found yet.");
            }
        });
    }

    public void storeVideoHeaders(String url, java.util.Map<String, String> headers) {
        // Store headers for this video URL
        videoHeadersMap.put(url, new java.util.HashMap<>(headers));
        logStatus("Stored " + headers.size() + " headers for video");
    }

    public void cacheM3U8Content(String url, String content) {
        m3u8CacheMap.put(url, content);
        logStatus("Cached m3u8 content (" + content.length() + " bytes)");
    }

    public String getCachedM3U8Content(String url) {
        return m3u8CacheMap.get(url);
    }

    public void onVideoDetected(String url) {
        // Directly launch player without checking size/network
        // The network check (HEAD request) was likely consuming single-use tokens,
        // causing ExoPlayer to get a 403 error.
        runOnUiThread(() -> {
            if (isPlayerActive) {
                Log.d(TAG, "Player already active, ignoring: " + url);
                return;
            }

            // Capture values on UI thread (MUST be done here as this method is called from
            // background thread)
            final String userAgent = webView.getSettings().getUserAgentString();
            final String currentUrl = webView.getUrl();

            // CRITICAL FIX: Fetch cookies for the VIDEO URL, not the page URL!
            // Cloudflare cookies (cf_clearance) are domain-specific.
            // The video CDN (cdnz.quest) needs its OWN cookie, not the one for the main
            // site (laroza.now).
            final String cookies = CookieManager.getInstance().getCookie(url);

            Log.d(TAG, "Cookies for video URL (" + url + "): " + cookies);

            logStatus("VIDEO DETECTED: " + url);

            // CRITICAL: Prevent duplicate launches
            boolean isMasterPlaylist = url.contains("master.m3u8") || url.contains("playlist.m3u8");
            boolean isNewVideo = !url.equals(lastDetectedVideoUrl) &&
                    !lastDetectedVideoUrl.contains(url.substring(0, Math.min(50, url.length())));

            if (!isMasterPlaylist && !isNewVideo) {
                logStatus("Skipping duplicate/child playlist: " + url);
                return;
            }

            lastDetectedVideoUrl = url;
            isPlayerActive = true;

            // Launch PlayerActivity
            android.content.Intent intent = new android.content.Intent(this, PlayerActivity.class);
            intent.putExtra("VIDEO_URL", url);
            intent.putExtra("COOKIES", cookies);
            intent.putExtra("REFERER", currentUrl);
            intent.putExtra("USER_AGENT", userAgent);

            // Pass captured headers if available
            java.util.Map<String, String> capturedHeaders = videoHeadersMap.get(url);
            if (capturedHeaders != null && !capturedHeaders.isEmpty()) {
                // Convert map to bundle for intent
                Bundle headersBundle = new Bundle();
                for (java.util.Map.Entry<String, String> entry : capturedHeaders.entrySet()) {
                    headersBundle.putString(entry.getKey(), entry.getValue());
                }
                intent.putExtra("CAPTURED_HEADERS", headersBundle);
                logStatus("Passing " + capturedHeaders.size() + " captured headers to player");
            }

            // Pass cached m3u8 content if available
            String cachedContent = m3u8CacheMap.get(url);
            if (cachedContent != null) {
                intent.putExtra("CACHED_M3U8", cachedContent);
                logStatus("Passing cached m3u8 content (" + cachedContent.length() + " bytes)");
            }

            startActivity(intent);
            Toast.makeText(this, "Video Found! Launching Player...", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // WebViewFetcher implementation
    private java.util.Map<String, FetchCallback> fetchCallbacks = new java.util.HashMap<>();
    private int nextFetchId = 0;

    @Override
    public void fetchUrl(String url, FetchCallback callback) {
        final int fetchId = nextFetchId++;
        fetchCallbacks.put(String.valueOf(fetchId), callback);

        runOnUiThread(() -> {
            String js = String.format(
                    "if (window.AndroidFetcher) { window.AndroidFetcher.fetch('%d', '%s'); } else { AndroidFetcherBridge.onFetchError('%d', 'AndroidFetcher not initialized'); }",
                    fetchId,
                    url.replace("'", "\\'"),
                    fetchId);
            webView.evaluateJavascript(js, result -> {
                Log.d(TAG, "Fetch request evaluated, result: " + result);
            });
            Log.d(TAG, "Requested WebView to fetch: " + url);
        });
    }

    // Called from JavaScript when data is received
    @android.webkit.JavascriptInterface
    public void onFetchData(String fetchId, String base64Data) {
        Log.d(TAG, "onFetchData called for fetch " + fetchId + ", data length: " + base64Data.length());
        FetchCallback callback = fetchCallbacks.get(fetchId);
        if (callback != null) {
            try {
                byte[] data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                callback.onDataReceived(data, data.length);
                Log.d(TAG, "Data decoded and passed to callback: " + data.length + " bytes");
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode data", e);
                callback.onError("Failed to decode data: " + e.getMessage());
            }
        } else {
            Log.w(TAG, "No callback found for fetch " + fetchId);
        }
    }

    // Called from JavaScript when fetch completes
    @android.webkit.JavascriptInterface
    public void onFetchComplete(String fetchId, String totalBytesStr) {
        Log.d(TAG, "onFetchComplete called for fetch " + fetchId + ", total bytes: " + totalBytesStr);
        FetchCallback callback = fetchCallbacks.get(fetchId);
        if (callback != null) {
            try {
                long totalBytes = Long.parseLong(totalBytesStr);
                callback.onComplete(totalBytes);
                fetchCallbacks.remove(fetchId);
                Log.d(TAG, "Fetch complete, callback removed");
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse total bytes", e);
            }
        } else {
            Log.w(TAG, "No callback found for fetch " + fetchId);
        }
    }

    // Called from JavaScript when fetch fails
    @android.webkit.JavascriptInterface
    public void onFetchError(String fetchId, String error) {
        Log.e(TAG, "onFetchError called for fetch " + fetchId + ": " + error);
        FetchCallback callback = fetchCallbacks.get(fetchId);
        if (callback != null) {
            callback.onError(error);
            fetchCallbacks.remove(fetchId);
        } else {
            Log.w(TAG, "No callback found for fetch " + fetchId);
        }
    }
}