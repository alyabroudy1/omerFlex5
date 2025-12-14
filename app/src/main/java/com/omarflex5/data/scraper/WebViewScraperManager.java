package com.omarflex5.data.scraper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omarflex5.data.local.entity.ServerEntity;
import com.omarflex5.data.repository.ServerRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages WebView scraping for Cloudflare-protected servers.
 *
 * Flow:
 * 1. Load server URL in hidden WebView
 * 2. Wait for CF challenge to complete
 * 3. Extract cookies from CookieManager
 * 4. Save cookies to ServerEntity for reuse
 * 5. Return page HTML for parsing
 */
public class WebViewScraperManager {

    private static final String TAG = "WebViewScraper";
    private static final long CF_WAIT_TIMEOUT_MS = 30000; // 30 seconds
    private static final long PAGE_LOAD_TIMEOUT_MS = 20000; // 20 seconds

    private static volatile WebViewScraperManager INSTANCE;

    private final Context context;
    private final ServerRepository serverRepository;
    private final Handler mainHandler;
    private final Gson gson;

    private WebView webView;
    private Dialog visibleDialog; // Dialog to hold WebView if visible
    private boolean isWebViewReady = false;

    private WebViewScraperManager(Context context) {
        this.context = context.getApplicationContext();
        this.serverRepository = ServerRepository.getInstance(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
    }

    public static WebViewScraperManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (WebViewScraperManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new WebViewScraperManager(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Initialize the hidden WebView on the main thread.
     */
    public void initialize() {
        mainHandler.post(() -> {
            if (webView == null) {
                webView = new WebView(context);
                configureWebView(webView);
                isWebViewReady = true;
                Log.d(TAG, "WebView initialized");
            }
        });
    }

    private void configureWebView(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        // CRITICAL: Use MOBILE User-Agent. Cloudflare Turnstile checks for touch events
        // + mobile UA consistency.
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        settings.setBlockNetworkImage(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE); // Don't cache CF challenges

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null); // Hardware accel for canvas challenges
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                Log.d("WebViewConsole", cm.message() + " -- From line " + cm.lineNumber() + " of " + cm.sourceId());
                return true;
            }
        });

        // Critical for CF Turnstile
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        CookieManager.getInstance().flush();
    }

    /**
     * Load a URL and extract CF cookies + page HTML.
     */
    public void loadWithCfBypass(ServerEntity server, String url, Activity activity, ScraperCallback callback) {
        if (!isWebViewReady) {
            initialize();
            // Retry after a short delay
            mainHandler.postDelayed(() -> loadWithCfBypass(server, url, activity, callback), 500);
            return;
        }

        mainHandler.post(() -> {
            Log.d(TAG, "Loading URL with CF Bypass: " + url);

            // Show Dialog if Activity provided
            if (activity != null && !activity.isFinishing()) {
                showWebViewDialog(activity);
            }

            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicBoolean cfDetected = new AtomicBoolean(false);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String finishedUrl) {
                    Log.d(TAG, "Page loaded: " + finishedUrl);

                    // Check if still on CF challenge page
                    view.evaluateJavascript("document.title", title -> {
                        String pageTitle = title != null ? title.replace("\"", "") : "";

                        // Update visual status if available
                        if (view.getTag() instanceof android.widget.TextView) {
                            android.widget.TextView st = (android.widget.TextView) view.getTag(); // Safe cast
                            st.post(() -> st.setText("Status: " + (pageTitle.isEmpty() ? "Loading..." : pageTitle)));
                        }

                        if (pageTitle.contains("Cloudflare") ||
                                pageTitle.contains("Just a moment") ||
                                pageTitle.contains("Checking your browser")) {
                            // Still on CF challenge
                            cfDetected.set(true);
                            Log.d(TAG, "CF challenge detected, waiting...");
                            if (view.getTag() instanceof android.widget.TextView) {
                                ((android.widget.TextView) view.getTag())
                                        .post(() -> ((android.widget.TextView) view.getTag())
                                                .setText("⚠️ Cloudflare Detected. Please verify."));
                            }
                        } else if (cfDetected.get() || !pageTitle.isEmpty()) {
                            // CF passed or normal page
                            if (!completed.getAndSet(true)) {
                                checkAndHandleRedirect(server, finishedUrl);
                                extractAndSave(view, server, callback);
                            }
                        }
                    });
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    // Allow all redirects (needed for CF)
                    return false;
                }
            });

            // Timeout handler
            mainHandler.postDelayed(() -> {
                if (!completed.getAndSet(true)) {
                    Log.w(TAG, "Timeout waiting for page");
                    extractAndSave(webView, server, callback);
                }
            }, CF_WAIT_TIMEOUT_MS);

            // Clear cookies for clean start if they're expired
            if (server.needsCookieRefresh()) {
                CookieManager.getInstance().removeAllCookies(null);
            }

            webView.loadUrl(url);
        });
    }

    private void showWebViewDialog(Activity activity) {
        try {
            if (visibleDialog != null && visibleDialog.isShowing()) {
                visibleDialog.dismiss();
            }

            // Remove WebView from any previous parent
            if (webView.getParent() != null) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }

            visibleDialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            visibleDialog.setCancelable(true);

            // Create a FrameLayout to hold WebView + Status Bar Overlay
            android.widget.FrameLayout container = new android.widget.FrameLayout(activity);
            container.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            // Add WebView
            container.addView(webView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            // Add Status Bar Overlay (Top)
            android.widget.LinearLayout statusBar = new android.widget.LinearLayout(activity);
            statusBar.setOrientation(android.widget.LinearLayout.VERTICAL);
            statusBar.setBackgroundColor(0xCC000000); // Semi-transparent black
            statusBar.setPadding(16, 16, 16, 16);
            android.widget.FrameLayout.LayoutParams statusParams = new android.widget.FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            statusParams.gravity = android.view.Gravity.TOP;

            android.widget.TextView statusText = new android.widget.TextView(activity);
            statusText.setText("Status: Initialize...");
            statusText.setTextColor(0xFFFFFFFF);
            statusText.setTextSize(14f);
            statusBar.addView(statusText);

            container.addView(statusBar, statusParams);

            visibleDialog.setContentView(container);
            visibleDialog.setOnDismissListener(dialog -> {
                // Optional: Cancel loading?
            });
            visibleDialog.show();
            webView.requestFocus();
            Log.d(TAG, "WebView Dialog shown with Status Bar.");

            // Store status text reference to update later (simple hack since we don't have
            // a class member for it yet)
            webView.setTag(statusText);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show WebView Dialog", e);
        }
    }

    private void dismissDialog() {
        mainHandler.post(() -> {
            try {
                if (visibleDialog != null && visibleDialog.isShowing()) {
                    visibleDialog.dismiss();
                    visibleDialog = null;
                }
                // Detach webview to be safe?
                if (webView != null && webView.getParent() != null) {
                    ((ViewGroup) webView.getParent()).removeView(webView);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing dialog", e);
            }
        });
    }

    /**
     * Extract cookies and HTML from loaded page.
     */
    private void extractAndSave(WebView view, ServerEntity server, ScraperCallback callback) {
        // Dismiss UI
        dismissDialog();

        // Get cookies
        String cookieString = CookieManager.getInstance().getCookie(server.getBaseUrl());
        Map<String, String> cookies = parseCookies(cookieString);

        // Save cookies to server
        if (!cookies.isEmpty()) {
            String cookiesJson = gson.toJson(cookies);
            long expiresAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24);
            serverRepository.saveCfCookies(server.getId(), cookiesJson, expiresAt);
            Log.d(TAG, "Saved " + cookies.size() + " cookies for " + server.getName());
        }

        // Get page HTML
        view.evaluateJavascript(
                "(function() { return document.documentElement.outerHTML; })();",
                html -> {
                    if (html != null && !html.equals("null")) {
                        // Unescape the JS string
                        String unescaped = unescapeJsString(html);
                        callback.onSuccess(unescaped, cookies);
                        serverRepository.recordSuccess(server);
                    } else {
                        callback.onError("Failed to get page HTML");
                        serverRepository.recordFailure(server);
                    }
                });
    }

    /**
     * Search a server using WebView (for CF-protected searches).
     */
    /**
     * Search a server using WebView (for CF-protected searches).
     */
    public void search(ServerEntity server, String query, boolean allowWebViewFallback, Activity activity,
            ScraperCallback callback) {
        String searchUrl = buildSearchUrl(server, query);
        loadHybrid(server, searchUrl, allowWebViewFallback, activity, callback);
    }

    /**
     * Build search URL from server config.
     */
    private String buildSearchUrl(ServerEntity server, String query) {
        String pattern = server.getSearchUrlPattern();
        if (pattern == null || pattern.isEmpty()) {
            pattern = "/?s={query}"; // Default WordPress-style
        }

        String encodedQuery;
        try {
            encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            encodedQuery = query.replace(" ", "+");
        }

        return server.getBaseUrl() + pattern.replace("{query}", encodedQuery);
    }

    // ==================== HELPER METHODS ====================

    private Map<String, String> parseCookies(String cookieString) {
        Map<String, String> cookies = new HashMap<>();
        if (cookieString == null || cookieString.isEmpty())
            return cookies;

        String[] parts = cookieString.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                cookies.put(kv[0], kv[1]);
            }
        }
        return cookies;
    }

    private String unescapeJsString(String jsString) {
        if (jsString == null)
            return null;
        // Remove surrounding quotes if present
        if (jsString.startsWith("\"") && jsString.endsWith("\"")) {
            jsString = jsString.substring(1, jsString.length() - 1);
        }
        return jsString.replace("\\u003C", "<")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private void checkAndHandleRedirect(ServerEntity server, String currentUrl) {
        if (currentUrl == null || server == null)
            return;
        String base = server.getBaseUrl();
        // Simple check: if current URL domain differs from base URL domain
        try {
            Uri currentUri = Uri.parse(currentUrl);
            Uri baseUri = Uri.parse(base);
            if (!currentUri.getHost().equals(baseUri.getHost())) {
                // It's a redirect!
                Log.i(TAG, "Redirect detected: " + base + " -> " + currentUrl);
                // We could update the DB here if we wanted to be proactive
                // serverRepository.updateBaseUrl(server.getName(), currentUri.getScheme() +
                // "://" + currentUri.getHost());
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking redirect: " + e.getMessage());
        }
    }

    private Map<String, String> getSavedCookies(ServerEntity server) {
        if (server.getCfCookiesJson() != null && !server.getCfCookiesJson().isEmpty()) { // FIXED: getCfCookiesJson
            try {
                return gson.fromJson(server.getCfCookiesJson(), new TypeToken<Map<String, String>>() {
                }.getType());
            } catch (Exception e) {
                Log.e(TAG, "Error parsing saved cookies: " + e.getMessage());
            }
        }
        return new HashMap<>();
    }

    // ==================== HYBRID REQUEST LOGIC ====================

    private final okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    /**
     * Try direct request first. If CF detected, fallback to WebView.
     */
    /**
     * Try direct request first. If CF detected, fallback to WebView.
     */
    public void loadHybrid(ServerEntity server, String url, boolean allowWebViewFallback, Activity activity,
            ScraperCallback callback) {
        new Thread(() -> {
            try {
                // 1. Prepare Direct Request
                okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

                // Attach cookies if available
                Map<String, String> cookies = getSavedCookies(server);
                if (!cookies.isEmpty()) {
                    StringBuilder cookieHeader = new StringBuilder();
                    for (Map.Entry<String, String> entry : cookies.entrySet()) {
                        if (cookieHeader.length() > 0)
                            cookieHeader.append("; ");
                        cookieHeader.append(entry.getKey()).append("=").append(entry.getValue());
                    }
                    builder.header("Cookie", cookieHeader.toString());
                }

                // 2. Execute
                okhttp3.Response response = okHttpClient.newCall(builder.build()).execute();
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "";
                response.close();

                // 3. Check for Cloudflare
                boolean isCf = code == 403 || code == 503;
                if (isCf && (body.contains("Just a moment") || body.contains("Cloudflare")
                        || body.contains("Checking your browser"))) {

                    if (allowWebViewFallback) {
                        // Failover to WebView
                        Log.d(TAG, "Direct request hit Cloudflare (" + code + "). Falling back to WebView.");
                        mainHandler.post(() -> loadWithCfBypass(server, url, activity, callback));
                    } else {
                        // Strict Fast Mode: Fail immediately so caller can queue it
                        Log.d(TAG, "Direct request hit Cloudflare (" + code + "). Reporting CLOUDFLARE_DETECTED.");
                        mainHandler.post(() -> callback.onError("CLOUDFLARE_DETECTED"));
                    }

                } else if (code >= 200 && code < 400 && !body.isEmpty()) {
                    // Success
                    Log.d(TAG, "Direct request success (" + code + ").");
                    checkAndHandleRedirect(server, response.request().url().toString());
                    mainHandler.post(() -> callback.onSuccess(body, cookies));
                } else {
                    if (!body.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(body, cookies));
                    } else {
                        mainHandler.post(() -> callback.onError("HTTP Error: " + code));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Direct request failed: " + e.getMessage());
                if (allowWebViewFallback) {
                    mainHandler.post(() -> loadWithCfBypass(server, url, activity, callback));
                } else {
                    mainHandler.post(() -> callback.onError("CONNECTION_ERROR"));
                }
            }
        }).start();
    }

    // ==================== VIDEO SNIFFING ====================

    /**
     * Load URL and sniff for video links (m3u8/mp4).
     */
    public void sniffVideo(String url, VideoSniffCallback callback) {
        if (!isWebViewReady) {
            initialize();
            mainHandler.postDelayed(() -> sniffVideo(url, callback), 500);
            return;
        }

        mainHandler.post(() -> {
            Log.d(TAG, "Sniffing video from: " + url);
            AtomicBoolean found = new AtomicBoolean(false);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onLoadResource(WebView view, String resourceUrl) {
                    if (found.get())
                        return;

                    // Basic sniffing logic
                    if (resourceUrl.contains(".m3u8") || resourceUrl.contains(".mp4")) {
                        Log.d(TAG, "Video found: " + resourceUrl);
                        if (!found.getAndSet(true)) {
                            view.stopLoading();
                            callback.onVideoFound(resourceUrl, new HashMap<>()); // Cookies?
                        }
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    // Inject JS to find video src objects if network sniff fails
                    if (!found.get()) {
                        view.evaluateJavascript(
                                "(function() { " +
                                        "   var videos = document.getElementsByTagName('video');" +
                                        "   if(videos.length > 0) return videos[0].src;" +
                                        "   return null;" +
                                        "})()",
                                result -> {
                                    if (result != null && !result.equals("null") && !found.get()) {
                                        String videoUrl = unescapeJsString(result);
                                        if (videoUrl != null
                                                && (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {
                                            if (!found.getAndSet(true)) {
                                                callback.onVideoFound(videoUrl, new HashMap<>());
                                            }
                                        }
                                    }
                                });
                    }
                }
            });

            // Timeout
            mainHandler.postDelayed(() -> {
                if (!found.get()) {
                    callback.onError("Sniffing timed out");
                }
            }, 30000);

            webView.loadUrl(url);
        });
    }

    public interface VideoSniffCallback {
        void onVideoFound(String videoUrl, Map<String, String> cookies);

        void onError(String message);
    }

    public interface ScraperCallback {
        void onSuccess(String html, Map<String, String> cookies);

        void onError(String message);
    }
}
