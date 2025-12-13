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
    public static final String EXTRA_USER_AGENT = "extra_user_agent";

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

    private String targetUrl;
    private long timeout = 60000;

    // JS Interface for callbacks from WebView
    private final SnifferJsInterface jsInterface = new SnifferJsInterface();

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
        webView = findViewById(R.id.sniffer_webview);
        statusText = findViewById(R.id.status_text);
        statusProgress = findViewById(R.id.status_progress);
        btnClose = findViewById(R.id.btn_close);

        handler = new Handler(Looper.getMainLooper());

        // Parse intent
        targetUrl = getIntent().getStringExtra(EXTRA_URL);
        int strategyType = getIntent().getIntExtra(EXTRA_STRATEGY, STRATEGY_VIDEO);
        String customJs = getIntent().getStringExtra(EXTRA_CUSTOM_JS);
        timeout = getIntent().getLongExtra(EXTRA_TIMEOUT, 60000);
        String userAgent = getIntent().getStringExtra(EXTRA_USER_AGENT);

        if (targetUrl == null || targetUrl.isEmpty()) {
            updateStatus("❌ No URL provided");
            finishWithError("No URL provided");
            return;
        }

        // Create strategy based on type
        strategy = createStrategy(strategyType, customJs);

        // Setup WebView
        setupWebView(userAgent != null ? userAgent : SnifferConfig.DEFAULT_USER_AGENT);

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

        // Load URL
        updateStatus("Loading: " + targetUrl);
        webView.loadUrl(targetUrl);
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
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setUserAgentString(userAgent);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Cookie support
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Add JS interface
        webView.addJavascriptInterface(jsInterface, "SnifferAndroid");

        // Set clients
        webView.setWebViewClient(new SnifferWebClient());
        webView.setWebChromeClient(new WebChromeClient());
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
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.setWebChromeClient(null);
            webView.destroy();
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
}
