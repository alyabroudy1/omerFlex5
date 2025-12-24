package com.omarflex5.ui.sniffer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.omarflex5.R;
import com.omarflex5.data.sniffer.callback.SnifferCallback;
import com.omarflex5.data.sniffer.config.SnifferConfig;
import com.omarflex5.data.sniffer.strategy.CloudflareBypassStrategy;
import com.omarflex5.data.sniffer.strategy.SniffingStrategy;
import com.omarflex5.data.sniffer.strategy.VideoSniffingStrategy;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Call;
import okhttp3.Callback;
import java.io.IOException;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Activity-based WebView sniffer with modular strategy support.
 * Provides visual status bar and returns results via onActivityResult.
 */
public class SnifferActivity extends AppCompatActivity {

    private static final String TAG = "SnifferActivity";

    // Intent Extras
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_STRATEGY = "extra_strategy";
    public static final String EXTRA_CUSTOM_JS = "extra_custom_js";
    public static final String EXTRA_TIMEOUT = "extra_timeout";
    // EXTRA_HEADERS removed to simplify interface

    // Result Extras
    public static final String RESULT_VIDEO_URL = "result_video_url";
    public static final String RESULT_HTML = "result_html";
    public static final String RESULT_COOKIES = "result_cookies";
    public static final String RESULT_HEADERS = "result_headers";

    // Strategy Types
    public static final int STRATEGY_VIDEO = 1;
    public static final int STRATEGY_CLOUDFLARE = 2;

    private WebView webView;
    private TextView statusText;
    private ProgressBar statusProgress;
    private ImageButton btnClose;

    private Handler handler;
    private Runnable timeoutRunnable;

    private SniffingStrategy strategy;
    private boolean isDestroyed = false;
    private boolean resultDelivered = false;
    private boolean isCloudflareActive = false;

    private String targetUrl;
    private long timeout = 60000;

    // JS Interface for callbacks from WebView
    private final SnifferJsInterface jsInterface = new SnifferJsInterface();

    public static Intent createIntent(Context context, String url, int strategyType, Map<String, String> headers) {
        // Headers map ignored in intent to fix build issues.
        // Caller should encode headers in URL pipe or we rely on defaults.
        return createIntent(context, url, strategyType);
    }

    public static Intent createIntent(Context context, String url, int strategyType) {
        Intent intent = new Intent(context, SnifferActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_STRATEGY, strategyType);
        return intent;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sniffer);

        // Init views
        android.widget.FrameLayout container = findViewById(R.id.webview_container);
        statusText = findViewById(R.id.status_text);
        statusProgress = findViewById(R.id.status_progress);
        btnClose = findViewById(R.id.btn_close);

        handler = new Handler(Looper.getMainLooper());

        // Parse intent
        String rawUrl = getIntent().getStringExtra(EXTRA_URL);
        int strategyType = getIntent().getIntExtra(EXTRA_STRATEGY, STRATEGY_VIDEO);
        String customJs = getIntent().getStringExtra(EXTRA_CUSTOM_JS);
        timeout = getIntent().getLongExtra(EXTRA_TIMEOUT, 60000);

        // Parse User Agent
        String userAgent = getIntent().getStringExtra("EXTRA_USER_AGENT");

        if (rawUrl == null || rawUrl.isEmpty()) {
            updateStatus("❌ No URL provided");
            finishWithError("No URL provided");
            return;
        }

        // Parse Headers from URL (Format: url|Key=Value&Key2=Value2)
        Map<String, String> extraHeaders = new HashMap<>();
        if (rawUrl.contains("|")) {
            String[] parts = rawUrl.split("\\|", 2);
            targetUrl = parts[0];
            String params = parts[1];

            String[] pairs = params.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    extraHeaders.put(kv[0], kv[1]);
                }
            }
            Log.d(TAG, "Parsed headers from URL: " + extraHeaders);
        } else {
            targetUrl = rawUrl;
        }

        // ONE WEBVIEW ARCHITECTURE:
        // Borrow the persistent WebView from ScraperManager.
        // This ensures cookies/CF tokens are shared perfectly.
        com.omarflex5.data.scraper.WebViewScraperManager scraperManager = com.omarflex5.data.scraper.WebViewScraperManager
                .getInstance(this);
        webView = scraperManager.borrowWebView();

        if (webView.getParent() != null) {
            ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        }
        container.addView(webView);

        // Debug: Confirm User-Agent
        String finalUa = webView.getSettings().getUserAgentString();
        Log.d(TAG, "WebView Final User-Agent: " + finalUa);

        // Create strategy based on type
        strategy =

                createStrategy(strategyType, customJs);

        // Setup WebView (Apply clients but keep core settings)
        // CRITICAL: Use same dynamic UA as Scraper to avoid CF mismatch
        setupWebView(userAgent != null ? userAgent : com.omarflex5.util.WebConfig.getUserAgent(this));

        // Close button
        btnClose.setOnClickListener(v -> {
            finishWithError("Cancelled by user");
        });

        // Start timeout
        timeoutRunnable = () -> {
            if (!isDestroyed && !resultDelivered) {
                updateStatus("⏱️ Timeout");
                finishWithError("Timeout after " + (timeout / 1000) + "s");
            }
        };
        handler.postDelayed(timeoutRunnable, timeout);

        Log.d(TAG, "Loading URL: " + targetUrl);
        updateStatus("Loading...");

        // Check if we already have cookies (Native Persistence)
        // Note: We used to check CookieManager here, but native persistence proved
        // unreliable with Cloudflare.
        // We now enforce a DB restore + OkHttp Pre-fetch for maximum reliability.
        Log.d(TAG, "Enforcing DB restore for Cloudflare reliability...");
        scraperManager.restoreCookiesForUrl(targetUrl, () -> {
            // Debug: Check restored values
            String ua = webView.getSettings().getUserAgentString();
            String cookies = CookieManager.getInstance().getCookie(targetUrl);
            Log.d(TAG, "WebView User-Agent: " + ua);
            Log.d(TAG, "WebView Cookies: " + (cookies != null ? cookies : "null"));

            // Try to fetch via OkHttp first.
            // This bypasses the initial WebView navigation loop which triggers CF
            // challenges.
            fetchWithOkHttpAndLoad(targetUrl, extraHeaders);
        });

        // AUTO-CLICK FALLBACK (Native):
        // Disabled: This causes redirect loops on some sites (e.g. ArabSeed) by
        // clicking banners/home links.
        /*
         * handler.postDelayed(() -> {
         * if (!isDestroyed && !resultDelivered) {
         * if (!isCloudflareActive) {
         * simulateClick();
         * } else {
         * Log.d(TAG, "Native click blocked by Cloudflare detection");
         * }
         * }
         * }, 4000); // 4 seconds after load
         */
    }

    private void simulateClick() {
        if (webView == null || isCloudflareActive)
            return;

        long downTime = android.os.SystemClock.uptimeMillis();
        long eventTime = android.os.SystemClock.uptimeMillis() + 100;
        float x = webView.getWidth() / 2.0f;
        float y = webView.getHeight() / 2.0f;

        Log.d(TAG, "Simulating native click at " + x + "," + y);
        updateStatus("👆 Simulating Click...");

        android.view.MotionEvent downEvent = android.view.MotionEvent.obtain(
                downTime, eventTime, android.view.MotionEvent.ACTION_DOWN, x, y, 0);
        android.view.MotionEvent upEvent = android.view.MotionEvent.obtain(
                downTime, eventTime + 100, android.view.MotionEvent.ACTION_UP, x, y, 0);

        webView.dispatchTouchEvent(downEvent);
        webView.dispatchTouchEvent(upEvent);
    }

    private SniffingStrategy createStrategy(int type, String customJs) {
        SnifferCallback callback = new SnifferCallback() {
            @Override
            public void onVideoFound(String videoUrl, Map<String, String> headers) {
                deliverVideoResult(videoUrl, headers);
            }

            @Override
            public void onHtmlExtracted(String html, Map<String, String> cookies) {
                deliverHtmlResult(html, cookies);
            }

            @Override
            public void onCloudflareBypassComplete(Map<String, String> cookies) {
                Log.d(TAG, "CF bypass complete with " + cookies.size() + " cookies");
                isCloudflareActive = false; // Reset flag
            }

            @Override
            public void onCloudflareDetected() {
                if (!isCloudflareActive) {
                    isCloudflareActive = true;
                    Log.d(TAG, "Cloudflare protection detected. Pausing auto-clicks.");
                    updateStatus("⚠️ Cloudflare Detected - Please Verify");
                }
            }

            @Override
            public void onProgress(String message) {
                updateStatus(message);
            }

            @Override
            public void onError(String message) {
                finishWithError(message);
            }

            @Override
            public void onTimeout() {
                finishWithError("Strategy timeout");
            }
        };

        switch (type) {
            case STRATEGY_CLOUDFLARE:
                return new CloudflareBypassStrategy(callback);
            case STRATEGY_VIDEO:
            default:
                VideoSniffingStrategy videoStrategy = new VideoSniffingStrategy(callback);
                if (customJs != null) {
                    videoStrategy.setCustomScript(customJs);
                }
                return videoStrategy;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView(String userAgent) {
        // Unified Configuration
        com.omarflex5.data.scraper.config.WebConfig.configure(webView);

        // Ensure User Agent consistency if overridden by Intent
        if (userAgent != null && !userAgent.isEmpty()) {
            webView.getSettings().setUserAgentString(userAgent);
        }

        // Add JS interface (Unique to Sniffer)
        webView.addJavascriptInterface(jsInterface, "SnifferAndroid");

        // Set clients using Unified Architecture
        com.omarflex5.data.scraper.client.WebViewController controller = new com.omarflex5.data.scraper.client.WebViewController() {
            @Override
            public void updateStatus(String message) {
                SnifferActivity.this.updateStatus(message);
            }

            @Override
            public void updateProgress(int progress) {
                if (statusProgress != null) {
                    statusProgress.setProgress(progress);
                    statusProgress.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onCloudflareDetected() {
                // SnifferActivity mainly relies on VideoSniffingStrategy (JS) for this,
                // but if the Client detects it (e.g. if we used ScraperClient logic), handle it
                // here.
                if (strategy != null && strategy.getCallback() != null) {
                    strategy.getCallback().onCloudflareDetected();
                }
            }

            @Override
            public void onPageStarted(String url) {
                // Update status when page starts loading
                SnifferActivity.this.updateStatus("Loading: " + truncateUrl(url));
            }

            @Override
            public void onPageLoaded(String url) {
                // Standard page load
            }

            @Override
            public void onContentReady(String url) {
                // Not used in SnifferActivity (relies on JS strategy)
            }

            @Override
            public void onVideoDetected(String url, java.util.Map<String, String> headers) {
                // Native detection (fallback/augmentation to JS)
                SnifferActivity.this.updateStatus("Video Detected: " + url);

                // Immediately deliver result to calling activity
                runOnUiThread(() -> deliverVideoResult(url, headers));
            }
        };

        webView.setWebViewClient(new com.omarflex5.data.scraper.client.SnifferWebViewClient(this, controller));
        webView.setWebChromeClient(new com.omarflex5.data.scraper.client.CoreWebChromeClient(controller));
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            Log.d(TAG, "[Status] " + message);
        });
    }

    private void deliverVideoResult(String videoUrl, Map<String, String> headers) {
        if (resultDelivered)
            return;
        resultDelivered = true;

        // Inject session headers
        String currentUrl = webView.getUrl();
        String webViewUA = webView.getSettings().getUserAgentString();

        Map<String, String> finalHeaders = new HashMap<>();
        if (headers != null)
            finalHeaders.putAll(headers);

        finalHeaders.put("User-Agent", webViewUA);
        if (currentUrl != null) {
            finalHeaders.put("Referer", currentUrl);
            String cookies = CookieManager.getInstance().getCookie(currentUrl);
            if (cookies != null)
                finalHeaders.put("Cookie", cookies);
        }

        Intent result = new Intent();
        result.putExtra(RESULT_VIDEO_URL, videoUrl);
        result.putExtra(RESULT_HEADERS, (Serializable) finalHeaders);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void deliverHtmlResult(String html, Map<String, String> cookies) {
        if (resultDelivered)
            return;
        resultDelivered = true;

        Intent result = new Intent();
        result.putExtra(RESULT_HTML, html);
        result.putExtra(RESULT_COOKIES, (Serializable) cookies);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void finishWithError(String message) {
        if (resultDelivered)
            return;
        resultDelivered = true;

        Intent result = new Intent();
        result.putExtra("error", message);
        setResult(Activity.RESULT_CANCELED, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            // STOP loading and detach
            webView.stopLoading();

            // ONE WEBVIEW ARCHITECTURE:
            // Return to manager instead of destroying
            com.omarflex5.data.scraper.WebViewScraperManager.getInstance(this).returnWebView(webView);
            webView = null;
        }

        super.onDestroy();
    }

    // ==================== WebViewClient ====================

    private class SnifferWebClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (isDestroyed)
                return;
            updateStatus("Loading: " + truncateUrl(url));
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (isDestroyed)
                return;
            strategy.onPageFinished(view, url);

            // Inject custom script if available
            String customScript = strategy.getCustomScript();
            if (customScript != null) {
                view.evaluateJavascript("javascript:" + customScript, null);
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // SECURITY: Block non-HTTP schemes (intent://, market://, etc.)
            if (!url.startsWith("http")) {
                Log.w(TAG, "Blocked non-HTTP navigation: " + url);
                return true;
            }

            return strategy.shouldOverrideUrlLoading(url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (isDestroyed)
                return super.shouldInterceptRequest(view, request);

            String url = request.getUrl().toString();

            // Check if strategy wants to process this
            if (!strategy.shouldLoadResource(url, request)) {
                return new WebResourceResponse("text/plain", "UTF-8", null);
            }

            // Check for video URL patterns
            if (isVideoUrl(url)) {
                handler.post(() -> {
                    Map<String, String> headers = request.getRequestHeaders();
                    if (strategy.onPotentialVideoDetected(url, headers)) {
                        // Video found, result delivered
                    }
                });
            }

            return super.shouldInterceptRequest(view, request);
        }

        private boolean isVideoUrl(String url) {
            if (url == null)
                return false;
            String lower = url.toLowerCase();
            return lower.contains(".m3u8") || lower.contains(".mp4") ||
                    lower.contains(".mpd") || lower.contains("/hls/") ||
                    lower.contains("/dash/");
        }
    }

    // ==================== JS Interface ====================

    public class SnifferJsInterface {

        @JavascriptInterface
        public void onVideoDetected(String url) {
            if (resultDelivered)
                return;
            Log.d(TAG, "JS detected video: " + url);

            handler.post(() -> {
                strategy.onPotentialVideoDetected(url, null);
            });
        }

        @JavascriptInterface
        public void onCloudflareStatus(boolean isCloudflare) {
            if (strategy instanceof CloudflareBypassStrategy) {
                handler.post(() -> {
                    ((CloudflareBypassStrategy) strategy)
                            .onCloudflareCheckResult(webView, isCloudflare, webView.getUrl());
                });
            }
        }
    }

    private String truncateUrl(String url) {
        if (url == null)
            return "";
        if (url.length() > 50) {
            return url.substring(0, 50) + "...";
        }
        return url;
    }

    private void setCookies(String url, String cookieString) {
        if (cookieString == null)
            return;
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
        String[] parts = cookieString.split(";");
        for (String part : parts) {
            cookieManager.setCookie(url, part.trim());
        }
        cookieManager.flush();
        Log.d(TAG, "Injected manual cookies: " + cookieString);
    }

    /**
     * Attempts to fetch the page content using OkHttp (which shares/uses the same
     * cookies)
     * and load it directly into WebView. This bypasses the initial WebView
     * navigation loop.
     */
    /**
     * Attempts to fetch the page content using OkHttp (which shares/uses the same
     * cookies)
     * and load it directly into WebView. This bypasses the initial WebView
     * navigation loop.
     */
    private void fetchWithOkHttpAndLoad(String url, Map<String, String> extraHeaders) {
        updateStatus("Fetching via OkHttp...");

        // 1. Get Cookies from CookieManager (now restored)
        String cookies = CookieManager.getInstance().getCookie(url);
        String userAgent = webView.getSettings().getUserAgentString();

        Log.d(TAG, "OkHttp Fetch - Cookies: " + cookies);
        Log.d(TAG, "OkHttp Fetch - UA: " + userAgent);
        Log.d(TAG, "OkHttp Fetch - Extra Headers: " + extraHeaders);

        // 2. Build Request
        // Note: Using a new client or shared one. Ideally shared but for now new one is
        // fine as cookies are passed via header.
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent);

        if (cookies != null && !cookies.isEmpty()) {
            builder.header("Cookie", cookies);
        }

        // Apply extra headers (Referer, etc.)
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        client.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "OkHttp Fetch Failed: " + e.getMessage());
                // Fallback to standard load
                runOnUiThread(() -> {
                    Log.d(TAG, "Fallback to standard WebView loadUrl");
                    if (extraHeaders != null && !extraHeaders.isEmpty()) {
                        webView.loadUrl(url, extraHeaders);
                    } else {
                        webView.loadUrl(url);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String html = response.body() != null ? response.body().string() : "";
                response.close();

                // Check for Cloudflare using utility
                if (com.omarflex5.data.scraper.util.CfDetector.isCloudflareResponse(code, html)) {
                    Log.w(TAG, "OkHttp hit Cloudflare (" + code + "). Falling back to WebView.");
                    runOnUiThread(() -> {
                        updateStatus("⚠️ Cloudflare Detected - Loading via WebView...");
                        if (extraHeaders != null && !extraHeaders.isEmpty()) {
                            webView.loadUrl(url, extraHeaders);
                        } else {
                            webView.loadUrl(url);
                        }
                    });
                    return;
                }

                if (response.isSuccessful() || code >= 200 && code < 400) {
                    Log.d(TAG, "OkHttp Fetch Success! Size: " + html.length());

                    runOnUiThread(() -> {
                        updateStatus("Loading Content...");
                        // Load data with Base URL so relative links/scripts work
                        webView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url);
                    });
                } else {
                    Log.e(TAG, "OkHttp Fetch Error: " + code);
                    // Fallback
                    runOnUiThread(() -> {
                        if (extraHeaders != null && !extraHeaders.isEmpty()) {
                            webView.loadUrl(url, extraHeaders);
                        } else {
                            webView.loadUrl(url);
                        }
                    });
                }
            }
        });
    }
}
