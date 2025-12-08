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

    // User agent (matching Cronet for compatibility)
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.7432.0 Mobile Safari/537.36";

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
        setupWebView();

        // Defer loading to allow activity to fully render (prevents ANR)
        Log.d(TAG, "Loading page: " + pageUrl);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            updateStatus("Loading page...");
            webView.loadUrl(pageUrl);
        }, 100); // Small delay to let UI render
    }

    private void initViews() {
        webView = findViewById(R.id.webview_browser);
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
        WebSettings settings = webView.getSettings();

        // Enable JavaScript (required for video players)
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(USER_AGENT);

        // Disable cache for fresh content
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Cookie management
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // WebView debugging
        WebView.setWebContentsDebuggingEnabled(true);

        // Set clients
        webView.setWebViewClient(new VideoDetectorClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                Log.d(TAG + "_Console", consoleMessage.message());
                return true;
            }
        });
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
            webView.destroy();
        }
    }

    /**
     * WebViewClient that detects video URLs in network requests.
     */
    private class VideoDetectorClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            updateStatus("Loading: " + url);
            loadingOverlay.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            updateStatus("Page loaded, searching for video...");

            // Inject JavaScript to auto-click video player
            view.postDelayed(() -> injectAutoClickScript(view), 1000);

            // Hide loading after a delay to let video elements appear
            view.postDelayed(() -> {
                if (!videoFound) {
                    loadingOverlay.setVisibility(View.GONE);
                }
            }, 5000); // Increased to 5 seconds for video to load
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

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Check for video URLs
            if (isVideoUrl(url)) {
                Log.d(TAG, "Intercepted video request: " + url);

                // Capture headers
                Map<String, String> headers = request.getRequestHeaders();
                if (headers != null && !headers.isEmpty()) {
                    videoHeadersMap.put(url, new HashMap<>(headers));
                }

                // Notify video detected
                onVideoDetected(url);

                // Allow request to proceed (don't block)
                return null;
            }

            // Content-Type detection for non-standard URLs
            if (!isCommonResource(url)) {
                try {
                    String contentType = getContentType(url);
                    if (contentType != null && isVideoContentType(contentType)) {
                        Log.d(TAG, "Detected video via Content-Type: " + contentType);
                        onVideoDetected(url);
                        return new WebResourceResponse("text/plain", "utf-8",
                                new ByteArrayInputStream("".getBytes()));
                    }
                } catch (Exception e) {
                    // Ignore detection errors
                }
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            updateStatus("Error: " + description);
        }

        private boolean isVideoUrl(String url) {
            // First check if it's a tracking/analytics URL
            if (isTrackingUrl(url)) {
                return false;
            }

            String lowerUrl = url.toLowerCase();
            for (String indicator : VIDEO_INDICATORS) {
                if (lowerUrl.contains(indicator)) {
                    return true;
                }
            }
            return VIDEO_PATTERN.matcher(url).matches();
        }

        private boolean isTrackingUrl(String url) {
            String lowerUrl = url.toLowerCase();
            for (String blacklisted : TRACKING_BLACKLIST) {
                if (lowerUrl.contains(blacklisted)) {
                    Log.d(TAG, "Ignoring tracking URL: " + url.substring(0, Math.min(80, url.length())));
                    return true;
                }
            }
            return false;
        }

        private boolean isCommonResource(String url) {
            String lowerUrl = url.toLowerCase();
            return lowerUrl.endsWith(".js") || lowerUrl.endsWith(".css") ||
                    lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") ||
                    lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".gif") ||
                    lowerUrl.endsWith(".svg") || lowerUrl.endsWith(".ico") ||
                    lowerUrl.endsWith(".woff") || lowerUrl.endsWith(".woff2") ||
                    lowerUrl.endsWith(".ttf") || lowerUrl.endsWith(".json");
        }

        private boolean isVideoContentType(String contentType) {
            String lowerType = contentType.toLowerCase();
            return lowerType.startsWith("video/") ||
                    lowerType.equals("application/vnd.apple.mpegurl") ||
                    lowerType.equals("application/x-mpegurl") ||
                    lowerType.equals("application/dash+xml");
        }

        private String getContentType(String urlString) {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setRequestProperty("User-Agent", USER_AGENT);

                String cookies = CookieManager.getInstance().getCookie(urlString);
                if (cookies != null) {
                    connection.setRequestProperty("Cookie", cookies);
                }

                connection.connect();
                String contentType = connection.getContentType();
                connection.disconnect();
                return contentType;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
