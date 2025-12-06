package com.omarflex5.data.scraper;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.Gson;
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

    /**
     * Load a URL and extract CF cookies + page HTML.
     */
    public void loadWithCfBypass(ServerEntity server, String url, ScraperCallback callback) {
        if (!isWebViewReady) {
            initialize();
            // Retry after a short delay
            mainHandler.postDelayed(() -> loadWithCfBypass(server, url, callback), 500);
            return;
        }

        mainHandler.post(() -> {
            Log.d(TAG, "Loading URL: " + url);

            AtomicBoolean completed = new AtomicBoolean(false);
            AtomicBoolean cfDetected = new AtomicBoolean(false);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String finishedUrl) {
                    Log.d(TAG, "Page loaded: " + finishedUrl);

                    // Check if still on CF challenge page
                    view.evaluateJavascript("document.title", title -> {
                        String pageTitle = title != null ? title.replace("\"", "") : "";

                        if (pageTitle.contains("Cloudflare") ||
                                pageTitle.contains("Just a moment") ||
                                pageTitle.contains("Checking your browser")) {
                            // Still on CF challenge
                            cfDetected.set(true);
                            Log.d(TAG, "CF challenge detected, waiting...");
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

    /**
     * Extract cookies and HTML from loaded page.
     */
    private void extractAndSave(WebView view, ServerEntity server, ScraperCallback callback) {
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
    public void search(ServerEntity server, String query, ScraperCallback callback) {
        String searchUrl = buildSearchUrl(server, query);
        loadHybrid(server, searchUrl, callback);
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

    /**
     * Configure WebView for scraping.
     */
    private void configureWebView(WebView webView) {
        WebSettings settings = webView.getSettings();

        // Essential settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Act like a real browser
        settings.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Performance
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(false); // Faster loading
        settings.setBlockNetworkImage(true);

        // Cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
    }

    /**
     * Parse cookie string into map.
     */
    private Map<String, String> parseCookies(String cookieString) {
        Map<String, String> cookies = new HashMap<>();
        if (cookieString != null && !cookieString.isEmpty()) {
            String[] pairs = cookieString.split("; ");
            for (String pair : pairs) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    String name = pair.substring(0, idx);
                    String value = idx < pair.length() - 1 ? pair.substring(idx + 1) : "";
                    cookies.put(name, value);
                }
            }
        }
        return cookies;
    }

    /**
     * Unescape JavaScript string.
     */
    private String unescapeJsString(String js) {
        if (js == null)
            return null;

        // Remove surrounding quotes
        if (js.startsWith("\"") && js.endsWith("\"")) {
            js = js.substring(1, js.length() - 1);
        }

        // Unescape common sequences
        return js.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\/", "/")
                .replace("\\\\", "\\")
                .replace("\\u003C", "<")
                .replace("\\u003c", "<")
                .replace("\\u003E", ">")
                .replace("\\u003e", ">");
    }

    /**
     * Get saved cookies for OkHttp requests.
     */
    public Map<String, String> getSavedCookies(ServerEntity server) {
        String cookiesJson = server.getCfCookiesJson();
        if (cookiesJson != null && !cookiesJson.isEmpty()) {
            try {
                // noinspection unchecked
                return gson.fromJson(cookiesJson, Map.class);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse cookies: " + e.getMessage());
            }
        }
        return new HashMap<>();
    }

    /**
     * Release WebView resources.
     */
    public void release() {
        mainHandler.post(() -> {
            if (webView != null) {
                webView.stopLoading();
                webView.destroy();
                webView = null;
                isWebViewReady = false;
                Log.d(TAG, "WebView released");
            }
        });
    }

    /**
     * Check if the final URL is different from the server's base URL (redirect).
     * If so, update the database with the new base URL.
     */
    private void checkAndHandleRedirect(ServerEntity server, String currentUrl) {
        try {
            Uri currentUri = Uri.parse(currentUrl);
            Uri baseUri = Uri.parse(server.getBaseUrl());

            String currentHost = currentUri.getHost();
            String baseHost = baseUri.getHost();

            // Simple check: if hosts differ
            if (currentHost != null && baseHost != null && !currentHost.equalsIgnoreCase(baseHost)) {
                // Construct new Base URL (scheme + host)
                String newBaseUrl = currentUri.getScheme() + "://" + currentHost;

                Log.i(TAG,
                        "Detected redirect for " + server.getName() + ": " + server.getBaseUrl() + " -> " + newBaseUrl);

                // Update Base URL
                serverRepository.updateBaseUrl(server.getName(), newBaseUrl);

                // Special handling for FaselHD: Update search pattern if moving to .biz (or
                // similar)
                // The pattern /?s={query} works on .biz, while .care used /search?s={query} (or
                // similar)
                if (server.getName().equalsIgnoreCase("faselhd")) {
                    // Start of robust logic: if using .biz, force the known working pattern
                    if (currentHost.contains("faselhds.biz")) {
                        serverRepository.updateSearchUrlPattern(server.getName(), "/?s={query}");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling redirect check: " + e.getMessage());
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
    public void loadHybrid(ServerEntity server, String url, ScraperCallback callback) {
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
                    // Failover to WebView
                    Log.d(TAG, "Direct request hit Cloudflare (" + code + "). Falling back to WebView.");
                    mainHandler.post(() -> loadWithCfBypass(server, url, callback));
                } else if (code >= 200 && code < 400 && !body.isEmpty()) {
                    // Success
                    Log.d(TAG, "Direct request success (" + code + ").");
                    checkAndHandleRedirect(server, response.request().url().toString());
                    mainHandler.post(() -> callback.onSuccess(body, cookies));
                } else {
                    // Other error (404, etc) - generic callback error or successful parse of error
                    // page?
                    // Usually treat as success if body exists so parser can decide, unless it's
                    // empty
                    if (!body.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(body, cookies));
                    } else {
                        mainHandler.post(() -> callback.onError("HTTP Error: " + code));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Direct request failed: " + e.getMessage());
                // Fallback to WebView on connection error too?
                // User said "if it fails for cloudflare", but connection error might be solved
                // by WebView if it's protocol related?
                // Let's fallback to WebView to be safe.
                mainHandler.post(() -> loadWithCfBypass(server, url, callback));
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
