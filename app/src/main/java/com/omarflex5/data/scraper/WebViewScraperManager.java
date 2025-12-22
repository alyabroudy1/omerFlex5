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
        // Unified Configuration
        com.omarflex5.data.scraper.config.WebConfig.configure(webView);

        // Add console logging
        webView.setWebChromeClient(new com.omarflex5.data.scraper.client.CoreWebChromeClient(null)); // No UI controller
                                                                                                     // needed for
                                                                                                     // ChromeClient
                                                                                                     // here yet
    }

    /**
     * Borrow the shared WebView for use in an Activity (e.g. SnifferActivity).
     * This ensures we use the EXACT SAME session/cookies/storage as the scraper.
     * The caller is responsible for attaching it to their view hierarchy.
     */
    public WebView borrowWebView() {
        if (webView == null) {
            initialize(); // Try to init if null, though it should be ready
            // If initialized on this thread, webView might still be null if posted.
            // But usually scraper runs first.
            if (webView == null) {
                // Force sync init if absolutely needed (rare fallback)
                webView = new WebView(context);
                configureWebView(webView);
            }
        }

        // Detach from any existing parent
        if (webView.getParent() != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }

        // Hide global dialog if showing
        if (visibleDialog != null && visibleDialog.isShowing()) {
            visibleDialog.dismiss();
            visibleDialog = null;
        }

        return webView;
    }

    /**
     * Return the WebView after use.
     * The borrower should detach it before calling this, but we check anyway.
     */
    public void returnWebView(WebView view) {
        if (view == this.webView) {
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            // Reset clients/interfaces to avoid leaks or unintended behavior
            view.setWebViewClient(null);
            view.setWebChromeClient(null);
            view.loadUrl("about:blank"); // Clear page content
        }
    }

    public void loadWithCfBypass(ServerEntity server, String url, Activity activity, ScraperCallback callback) {
        loadWithCfBypass(server, url, null, activity, callback);
    }

    /**
     * Load a URL and extract CF cookies + page HTML.
     */
    public void loadWithCfBypass(ServerEntity server, String url, String postData, Activity activity,
            ScraperCallback callback) {
        if (!isWebViewReady) {
            initialize();
            // Retry after a short delay
            mainHandler.postDelayed(() -> loadWithCfBypass(server, url, activity, callback), 500);
            return;
        }

        mainHandler.post(() -> {
            String resolvedUrl = com.omarflex5.util.UrlHelper.restore(server.getBaseUrl(), url);
            Log.d(TAG, "Loading URL with CF Bypass: " + resolvedUrl + " (Original: " + url + ")");

            // Show Dialog if Activity provided
            if (activity != null && !activity.isFinishing()) {
                showWebViewDialog(activity);
            }

            AtomicBoolean completed = new AtomicBoolean(false);

            // Implement Controller to link Client events to Manager logic
            com.omarflex5.data.scraper.client.WebViewController controller = new com.omarflex5.data.scraper.client.WebViewController() {
                @Override
                public void updateStatus(String message) {
                    updateDialogStatus(message);
                }

                @Override
                public void updateProgress(int progress) {
                    // Optional: Update progress bar if added to dialog
                }

                @Override
                public void onCloudflareDetected() {
                    Log.d(TAG, "CF challenge detected, waiting...");
                    updateDialogStatus("⚠️ Cloudflare Detected. Please verify.");
                }

                @Override
                public void onPageLoaded(String url) {
                    // Base loaded, waiting for content check
                }

                @Override
                public void onContentReady(String url) {
                    // Safe content detected (No CF or CF passed)
                    if (!completed.getAndSet(true)) {
                        checkAndHandleRedirect(server, url);
                        extractAndSave(webView, server, callback);
                    }
                }

                @Override
                public void onVideoDetected(String url, java.util.Map<String, String> headers) {
                    // Not used in Scraper
                }
            };

            // Set Clients
            webView.setWebViewClient(new com.omarflex5.data.scraper.client.ScraperWebViewClient(controller));
            webView.setWebChromeClient(new com.omarflex5.data.scraper.client.CoreWebChromeClient(controller));

            // Timeout handler
            mainHandler.postDelayed(() -> {
                if (!completed.getAndSet(true)) {
                    Log.w(TAG, "Timeout waiting for page");
                    extractAndSave(webView, server, callback);
                }
            }, CF_WAIT_TIMEOUT_MS);

            // Clear cookies for clean start if they're expired
            // Clear cookies for clean start if they're expired
            if (server.needsCookieRefresh()) {
                Log.d(TAG, "Cookies expired or missing, clearing WebView cookies.");
                CookieManager.getInstance().removeAllCookies(v -> {
                    // Safe to load after clear
                    if (postData != null) {
                        webView.postUrl(resolvedUrl, postData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        webView.loadUrl(resolvedUrl);
                    }
                });
            } else {
                // Restore valid cookies from DB to ensure WebView has them (Fixes restart
                // issue)
                // Now Async: Must wait for completion
                restoreCookiesToWebView(server, () -> {
                    if (postData != null) {
                        webView.postUrl(resolvedUrl, postData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        webView.loadUrl(resolvedUrl);
                    }
                });
            }
        });
    }

    private void updateDialogStatus(String message) {
        if (webView != null && webView.getTag() instanceof android.widget.TextView) {
            android.widget.TextView st = (android.widget.TextView) webView.getTag();
            st.post(() -> st.setText(message));
        }
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
    /**
     * EXTRACT and SAVE cookies from a WebView instance.
     */
    private void extractAndSave(WebView view, ServerEntity server, ScraperCallback callback) {
        // Dismiss UI
        dismissDialog();

        // Get cookies
        String cookieString = CookieManager.getInstance().getCookie(server.getBaseUrl());

        // Save using centralized method
        saveCookies(server, cookieString);

        // Return success with cookies map
        Map<String, String> cookies = parseCookies(cookieString);

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
     * Public method to save cookies from external sources (e.g. SnifferActivity).
     */
    public void saveCookies(ServerEntity server, String cookieString) {
        if (server == null || cookieString == null)
            return;

        Log.d(TAG, ">>> SAVING COOKIES for " + server.getName() + ": " + cookieString);

        Map<String, String> cookies = parseCookies(cookieString);

        saveCookies(server, cookies);
    }

    public void saveCookies(ServerEntity server, Map<String, String> cookies) {
        if (server == null || cookies == null || cookies.isEmpty())
            return;

        String cookiesJson = gson.toJson(cookies);
        long expiresAt = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24);

        // 1. Save to DB (Async)
        serverRepository.saveCfCookies(server.getId(), cookiesJson, expiresAt);

        // 2. Update In-Memory Object (Sync) - CRITICAL for next request
        server.setCfCookiesJson(cookiesJson);
        server.setCfCookiesExpireAt(expiresAt);

        // 3. Flush to disk to ensure WebView persistence
        CookieManager.getInstance().flush();

        Log.d(TAG, "External Save: Persisted " + cookies.size() + " cookies for " + server.getName());
    }

    public void search(ServerEntity server, String url, boolean allowWebViewFallback, Activity activity,
            ScraperCallback callback) {
        loadHybrid(server, url, allowWebViewFallback, activity, callback);
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
                // Fix: Update repo with new base URL so subsequent requests use correct
                // domain/cookies
                String newBase = currentUri.getScheme() + "://" + currentUri.getHost();
                if (!newBase.equals(base)) {
                    // 1. Update DB (Async)
                    serverRepository.updateBaseUrl(server.getName(), newBase);

                    // 2. Update In-Memory Object (Sync) - CRITICAL for next request
                    server.setBaseUrl(newBase);

                    Log.i(TAG, "Updated Server Base URL to: " + newBase);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking redirect: " + e.getMessage());
        }
    }

    private Map<String, String> getSavedCookies(ServerEntity server) {
        if (server.getCfCookiesJson() != null && !server.getCfCookiesJson().isEmpty()) {
            try {
                return gson.fromJson(server.getCfCookiesJson(), new TypeToken<Map<String, String>>() {
                }.getType());
            } catch (Exception e) {
                Log.e(TAG, "Error parsing saved cookies: " + e.getMessage());
            }
        }
        return new HashMap<>();
    }

    private void restoreCookiesToWebView(ServerEntity server, Runnable onComplete) {
        Map<String, String> cookies = getSavedCookies(server);
        if (!cookies.isEmpty()) {
            CookieManager cm = CookieManager.getInstance();

            // CRITICAL: Clear existing cookies to prevent duplicates (migrating from Native
            // to DB session)
            // Note: removeAllCookies is ASYNC. We must wait for callback.
            cm.removeAllCookies(success -> {
                Log.d(TAG, "Cleared all cookies (Async success=" + success + "). Now restoring...");

                String url = server.getBaseUrl();
                for (Map.Entry<String, String> entry : cookies.entrySet()) {
                    // Construct cookie string: name=value
                    // CRITICAL: Force Path=/ to ensure cookie is valid for entire site.
                    String cookieValue = entry.getKey() + "=" + entry.getValue() + "; Path=/";

                    // CRITICAL: Restore Attributes for sensitive cookies
                    if (entry.getKey().equals("cf_clearance") || entry.getKey().equals("__cf_bm")) {
                        cookieValue += "; HttpOnly; Secure";
                        // Removed SameSite=None
                    }

                    cm.setCookie(url, cookieValue);
                    Log.d(TAG, "Restoring Cookie: " + cookieValue + " for URL: " + url);
                }
                cm.flush();
                Log.d(TAG, "<<< RESTORED " + cookies.size() + " cookies to WebView for " + url);

                // Verify what actually stuck
                String verify = cm.getCookie(url);
                Log.d(TAG, "VERIFY WebView Cookies after restore: " + verify);

                if (onComplete != null)
                    onComplete.run();
            });
        } else {
            Log.d(TAG, "No cookies to restore for " + server.getName());
            if (onComplete != null)
                onComplete.run();
        }
    }

    // ==================== COOKIE RESTORATION ====================

    /**
     * Async restore cookies for a specific URL's domain.
     * Useful for SnifferActivity to ensure session before loading.
     */
    public void restoreCookiesForUrl(String url, Runnable onComplete) {
        if (url == null) {
            onComplete.run();
            return;
        }

        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) {
                onComplete.run();
                return;
            }

            // Remove www. prefix for better matching? Or let DAO handle partials?
            // Assuming DAO handles strict matching on stored Base URL host or Name
            // For now, let's try strict host match first.

            serverRepository.findServerByHost(host, server -> {
                mainHandler.post(() -> {
                    if (server != null) {
                        restoreCookiesToWebView(server, onComplete);
                    } else {
                        // Fallback: Check if we have any server that *contains* this host?
                        // Or just log warning.
                        Log.d(TAG, "No matching server entity found for host: " + host);
                        onComplete.run();
                    }
                });
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to restore cookies for URL: " + url, e);
            onComplete.run();
        }
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
    public void loadHybrid(ServerEntity server, String url, String postData, boolean allowWebViewFallback,
            Activity activity,
            ScraperCallback callback) {

        new Thread(() -> {
            try {
                // 1. Resolve URL
                String resolvedUrl = com.omarflex5.util.UrlHelper.restore(server.getBaseUrl(), url);

                // 2. Prepare Direct Request
                String ua = com.omarflex5.util.WebConfig.getUserAgent(context);
                Log.d(TAG, "OkHttp User-Agent: " + ua + " | Targeting: " + resolvedUrl);

                okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                        .url(resolvedUrl)
                        .header("User-Agent", ua)
                        .header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8");

                // AJAX Headers for POST
                if (postData != null) {
                    builder.header("X-Requested-With", "XMLHttpRequest");
                    // Use base URL or series URL as referer if possible
                    String referer = server.getBaseUrl();
                    if (url.contains("season__episodes")) {
                        // For ArabSeed, the referer helps bypass "unauthorized"
                        builder.header("Referer", server.getBaseUrl());
                    }

                    okhttp3.RequestBody body = okhttp3.RequestBody.create(
                            postData,
                            okhttp3.MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8"));
                    builder.post(body);
                    Log.d(TAG, "Preparing OkHttp POST request with body: " + postData);
                }

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

                // Attach saved headers if available
                Map<String, String> savedHeaders = serverRepository.getSavedHeaders(server);
                if (!savedHeaders.isEmpty()) {
                    for (Map.Entry<String, String> entry : savedHeaders.entrySet()) {
                        builder.header(entry.getKey(), entry.getValue());
                        Log.d(TAG, "Attached saved header: " + entry.getKey());
                    }
                }

                // 2. Execute
                okhttp3.Response response = okHttpClient.newCall(builder.build()).execute();
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "";
                response.close();

                // 3. Check for Cloudflare using utility
                if (com.omarflex5.data.scraper.util.CfDetector.isCloudflareResponse(code, body)) {

                    if (allowWebViewFallback) {
                        // Failover to WebView
                        Log.d(TAG, "Direct request hit Cloudflare (" + code + "). Falling back to WebView.");
                        mainHandler.post(() -> loadWithCfBypass(server, url, postData, activity, callback));
                    } else {
                        // Strict Fast Mode: Fail immediately so caller can queue it
                        Log.d(TAG, "Direct request hit Cloudflare (" + code + "). Reporting CLOUDFLARE_DETECTED.");
                        callback.onError("CLOUDFLARE_DETECTED");
                    }

                } else if (code >= 200 && code < 400 && !body.isEmpty()) {
                    // Success
                    Log.d(TAG, "Direct request success (" + code + ").");
                    checkAndHandleRedirect(server, response.request().url().toString());

                    // Save Referer header for future requests
                    Map<String, String> headersToSave = new HashMap<>();
                    headersToSave.put("Referer", resolvedUrl);
                    serverRepository.saveHeaders(server.getId(), headersToSave);

                    callback.onSuccess(body, cookies);
                } else {
                    if (!body.isEmpty()) {
                        callback.onSuccess(body, cookies);
                    } else {
                        callback.onError("HTTP Error: " + code);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Direct request failed: " + e.getMessage());
                if (allowWebViewFallback) {
                    mainHandler.post(() -> loadWithCfBypass(server, url, postData, activity, callback));
                } else {
                    callback.onError("CONNECTION_ERROR");
                }
            }
        }).start();
    }

    public void loadHybrid(ServerEntity server, String url, boolean allowWebViewFallback, Activity activity,
            ScraperCallback callback) {
        loadHybrid(server, url, null, allowWebViewFallback, activity, callback);
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
