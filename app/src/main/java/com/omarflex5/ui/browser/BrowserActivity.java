package com.omarflex5.ui.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.omarflex5.R;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * BrowserActivity - A reusable browser component for video detection.
 * 
 * Usage:
 * BrowserActivity.launch(context, pageUrl, REQUEST_CODE);
 * 
 * In onActivityResult:
 * if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
 * String videoUrl = data.getStringExtra(EXTRA_VIDEO_URL);
 * String cookies = data.getStringExtra(EXTRA_COOKIES);
 * String referer = data.getStringExtra(EXTRA_REFERER);
 * String userAgent = data.getStringExtra(EXTRA_USER_AGENT);
 * }
 */
public class BrowserActivity extends com.omarflex5.ui.base.BaseActivity {

    private static final String TAG = "BrowserActivity";

    // Intent extras
    public static final String EXTRA_PAGE_URL = "page_url";
    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_COOKIES = "cookies";
    public static final String EXTRA_REFERER = "referer";
    public static final String EXTRA_USER_AGENT = "user_agent";
    public static final String EXTRA_HEADERS = "headers";

    // Video detection patterns
    private static final Pattern VIDEO_PATTERN = Pattern.compile(
            ".*\\.(m3u8|mp4|mkv|webm|mpd)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    // Common video content in URL paths (be specific to avoid false positives)
    private static final String[] VIDEO_INDICATORS = {
            ".m3u8", ".mpd", ".mp4", "/hls/", "/dash/", "googlevideo", "cdnstream", "streamtape", "master.m3u8"
    };

    // URLs to ignore (analytics, tracking, ads)
    private static final String[] TRACKING_BLACKLIST = {
            "jwpltx.com", "ping.gif", "analytics", "tracking", "telemetry",
            "beacon", "stats.", "log.", "pixel", "impression", "adserver"
    };

    // User agent (matching Scraper for compatibility)
    // private static final String USER_AGENT =
    // com.omarflex5.util.WebConfig.COMMON_USER_AGENT; // Deprecated
    private String userAgent;

    // Views
    private WebView webView;
    private View loadingOverlay;
    private TextView textStatus;
    private TextView textTitle;

    // State
    private String pageUrl;
    private boolean videoFound = false;
    private Map<String, Map<String, String>> videoHeadersMap = new HashMap<>();

    /**
     * Launch the browser activity to detect video from a page URL.
     */
    public static void launch(Activity context, String pageUrl, int requestCode) {
        Intent intent = new Intent(context, BrowserActivity.class);
        intent.putExtra(EXTRA_PAGE_URL, pageUrl);
        context.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        // Get page URL from intent
        pageUrl = getIntent().getStringExtra(EXTRA_PAGE_URL);
        if (pageUrl == null || pageUrl.isEmpty()) {
            Toast.makeText(this, "No URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();

        // ONE WEBVIEW ARCHITECTURE:
        // Borrow the persistent WebView from ScraperManager.
        com.omarflex5.data.scraper.WebViewScraperManager scraperManager = com.omarflex5.data.scraper.WebViewScraperManager
                .getInstance(this);
        webView = scraperManager.borrowWebView();

        // Attach to layout
        android.widget.FrameLayout container = findViewById(R.id.webview_container);
        if (webView.getParent() != null) {
            ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        }
        container.addView(webView);

        setupWebView();

        // Defer loading to allow activity to fully render (prevents ANR)
        Log.d(TAG, "Loading page: " + pageUrl);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            updateStatus("Loading page...");
            webView.loadUrl(pageUrl);
        }, 100); // Small delay to let UI render
    }

    private void initViews() {
        // webView is initialized via borrowWebView()
        loadingOverlay = findViewById(R.id.loading_overlay);
        textStatus = findViewById(R.id.text_status);
        textTitle = findViewById(R.id.text_title);
        Button btnClose = findViewById(R.id.btn_close);

        btnClose.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        textTitle.setText(pageUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        // Unified Configuration
        com.omarflex5.data.scraper.config.WebConfig.configure(webView);

        // Ensure User Agent consistency in local field
        userAgent = webView.getSettings().getUserAgentString();

        // Enable debug
        WebView.setWebContentsDebuggingEnabled(true);

        // Set clients using Unified Architecture
        com.omarflex5.data.scraper.client.WebViewController controller = new com.omarflex5.data.scraper.client.WebViewController() {
            @Override
            public void updateStatus(String message) {
                BrowserActivity.this.updateStatus(message);
            }

            @Override
            public void updateProgress(int progress) {
                // No ProgressBar in specific BrowserActivity, status handled via overlay
            }

            @Override
            public void onCloudflareDetected() {
                // Browser mode handles CF naturally by user interaction
                updateStatus("⚠️ Cloudflare Detected");
            }

            @Override
            public void onPageStarted(String url) {
                // Optional: Update title or status when a new page load begins
                BrowserActivity.this.updateStatus("Loading...");
                textTitle.setText(url);
            }

            @Override
            public void onPageLoaded(String url) {
                textTitle.setText(url);
                // Inject auto-click script if needed (preserved logic)
                webView.postDelayed(() -> injectAutoClickScript(webView), 1000);

                // Hide loading after a delay to let video elements appear
                webView.postDelayed(() -> {
                    if (!videoFound && loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                }, 5000);
            }

            @Override
            public void onContentReady(String url) {
                // Not used in Browser
            }

            @Override
            public void onVideoDetected(String url, Map<String, String> headers) {
                // Add headers to map if needed
                if (headers != null) {
                    videoHeadersMap.put(url, new HashMap<>(headers));
                }
                BrowserActivity.this.onVideoDetected(url);
            }
        };

        webView.setWebViewClient(new com.omarflex5.data.scraper.client.SnifferWebViewClient(this, controller));
        webView.setWebChromeClient(new com.omarflex5.data.scraper.client.CoreWebChromeClient(controller));
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> textStatus.setText(status));
        Log.d(TAG, status);
    }

    private void onVideoDetected(String videoUrl) {
        if (videoFound)
            return; // Prevent duplicates

        // Filter out segment files and short URLs (likely ads)
        if (videoUrl.contains(".ts") || videoUrl.contains(".m4s") || videoUrl.length() < 50) {
            Log.d(TAG, "Ignoring segment/short URL: " + videoUrl);
            return;
        }

        videoFound = true;
        Log.d(TAG, "VIDEO DETECTED: " + videoUrl);
        updateStatus("Video found!");

        runOnUiThread(() -> {
            // Capture context
            String cookies = CookieManager.getInstance().getCookie(videoUrl);
            String referer = webView.getUrl();
            String userAgent = webView.getSettings().getUserAgentString();

            // Create result intent
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_VIDEO_URL, videoUrl);
            resultIntent.putExtra(EXTRA_COOKIES, cookies);
            resultIntent.putExtra(EXTRA_REFERER, referer);
            resultIntent.putExtra(EXTRA_USER_AGENT, userAgent);

            // Add captured headers if available
            Map<String, String> headers = videoHeadersMap.get(videoUrl);
            if (headers != null && !headers.isEmpty()) {
                Bundle headersBundle = new Bundle();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    headersBundle.putString(entry.getKey(), entry.getValue());
                }
                resultIntent.putExtra(EXTRA_HEADERS, headersBundle);
            }

            setResult(RESULT_OK, resultIntent);
            Toast.makeText(this, "Video Found!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.stopLoading();

            // ONE WEBVIEW ARCHITECTURE:
            // Return to manager instead of destroying
            com.omarflex5.data.scraper.WebViewScraperManager.getInstance(this).returnWebView(webView);
            webView = null;
        }
    }

    private void injectAutoClickScript(WebView view) {
        String autoClickJs = "javascript:(function() {" +
                "console.log('[AutoClick] Starting...');" +
                // Look for video elements and click them
                "var videos = document.querySelectorAll('video');" +
                "for (var i = 0; i < videos.length; i++) {" +
                "  videos[i].play();" +
                "  console.log('[AutoClick] Playing video ' + i);" +
                "}" +
                // Click on iframes that look like video players
                "var iframes = document.querySelectorAll('iframe');" +
                "for (var i = 0; i < iframes.length; i++) {" +
                "  var src = iframes[i].src || '';" +
                "  if (src.indexOf('player') >= 0 || src.indexOf('embed') >= 0 || src.indexOf('video') >= 0) {" +
                "    console.log('[AutoClick] Found player iframe: ' + src);" +
                "    iframes[i].click();" +
                "  }" +
                "}" +
                // Click on any element with 'play' in its class/id
                "var playButtons = document.querySelectorAll('[class*=\"play\"],[id*=\"play\"],[class*=\"Play\"],[id*=\"Play\"]');"
                +
                "for (var i = 0; i < playButtons.length; i++) {" +
                "  playButtons[i].click();" +
                "  console.log('[AutoClick] Clicked play button ' + i);" +
                "}" +
                "console.log('[AutoClick] Done');" +
                "})();";

        view.evaluateJavascript(autoClickJs, null);
        Log.d(TAG, "Injected auto-click script");
    }
}
