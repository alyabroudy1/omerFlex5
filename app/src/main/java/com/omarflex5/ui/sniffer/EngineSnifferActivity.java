package com.omarflex5.ui.sniffer;

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
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.omarflex5.R;
import com.omarflex5.engine.EngineSnifferManager;
import com.omarflex5.engine.IWebEngine;
import com.omarflex5.engine.IWebEngineClient;
import com.omarflex5.engine.WebEngineFactory;
import com.omarflex5.engine.WebEngineSettings;
import com.omarflex5.data.scraper.WebViewScraperManager;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Engine-agnostic video sniffer Activity.
 * Works with both WebView and GeckoView via IWebEngine interface.
 * Use this for GeckoView-based video sniffing with better Cloudflare bypass.
 */
public class EngineSnifferActivity extends AppCompatActivity {

    private static final String TAG = "EngineSnifferActivity";

    // Intent Extras (compatible with SnifferActivity)
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TIMEOUT = "extra_timeout";
    public static final String EXTRA_USER_AGENT = "extra_user_agent";

    // Result Extras
    public static final String RESULT_VIDEO_URL = "result_video_url";
    public static final String RESULT_HEADERS = "result_headers";

    private IWebEngine engine;
    private TextView statusText;
    private ProgressBar statusProgress;
    private ImageButton btnClose;
    private FrameLayout container;

    private Handler handler;
    private Runnable timeoutRunnable;

    private boolean isDestroyed = false;
    private boolean resultDelivered = false;
    private String targetUrl;
    private long timeout = 60000;
    private WebViewScraperManager scraperManager;

    public static Intent createIntent(Context context, String url) {
        Intent intent = new Intent(context, EngineSnifferActivity.class);
        intent.putExtra(EXTRA_URL, url);
        return intent;
    }

    public static Intent createIntent(Context context, String url, long timeoutMs) {
        Intent intent = new Intent(context, EngineSnifferActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_TIMEOUT, timeoutMs);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sniffer);

        // Init views
        container = findViewById(R.id.webview_container);
        statusText = findViewById(R.id.status_text);
        statusProgress = findViewById(R.id.status_progress);
        btnClose = findViewById(R.id.btn_close);

        handler = new Handler(Looper.getMainLooper());

        // Parse intent
        targetUrl = getIntent().getStringExtra(EXTRA_URL);
        timeout = getIntent().getLongExtra(EXTRA_TIMEOUT, 60000);
        String userAgent = getIntent().getStringExtra(EXTRA_USER_AGENT);

        if (targetUrl == null || targetUrl.isEmpty()) {
            updateStatus("❌ No URL provided");
            finishWithError("No URL provided");
            return;
        }

        // Check if engine factory is initialized
        if (!WebEngineFactory.isInitialized()) {
            updateStatus("❌ Engine not initialized");
            finishWithError("Web engine not initialized");
            return;
        }

        // Create engine
        updateStatus("🔧 Creating " + WebEngineFactory.getInstance().getEngineName() + " engine...");
        engine = EngineSnifferManager.getInstance(this).createEngine(this);

        if (engine == null) {
            finishWithError("Failed to create engine");
            return;
        }

        // Determine User Agent
        // CRITICAL: For GeckoView, we MUST use the same User-Agent that was used during
        // CF bypass
        // Cloudflare validates cf_clearance cookie against the User-Agent used to
        // acquire it
        String finalUserAgent = userAgent;
        if (finalUserAgent == null) {
            if (WebEngineFactory.getInstance().isGeckoView()) {
                // For GeckoView: Look up the User-Agent stored during CF bypass
                // It's saved in SharedPreferences (glide_ua) with key "ua_<host>"
                try {
                    java.net.URI uri = new java.net.URI(
                            targetUrl.contains("|") ? targetUrl.substring(0, targetUrl.indexOf("|")) : targetUrl);
                    String host = uri.getHost();
                    if (host != null) {
                        android.content.SharedPreferences prefs = getSharedPreferences("glide_ua", MODE_PRIVATE);
                        String storedUa = prefs.getString("ua_" + host, null);
                        if (storedUa != null) {
                            finalUserAgent = storedUa;
                            Log.d(TAG, "Using stored CF bypass User-Agent for " + host + ": " + storedUa);
                        } else {
                            // Fall back to WebConfig (Chrome UA) which is what GeckoCfBypassActivity uses
                            finalUserAgent = com.omarflex5.util.WebConfig.getUserAgent(this);
                            Log.d(TAG, "No stored UA for " + host + ", using WebConfig UA: " + finalUserAgent);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse URL for UA lookup: " + e.getMessage());
                    finalUserAgent = com.omarflex5.util.WebConfig.getUserAgent(this);
                }
            } else {
                // For WebView: Use system Chrome/WebView User-Agent
                finalUserAgent = com.omarflex5.util.WebConfig.getUserAgent(this);
            }
        }

        // Apply settings
        WebEngineSettings settings = WebEngineSettings.builder()
                .setJavaScriptEnabled(true)
                .setDomStorageEnabled(true)
                .setMediaPlaybackRequiresUserGesture(false)
                .setUserAgent(finalUserAgent)
                .build();
        engine.applySettings(settings);

        // Add engine view to container
        View engineView = engine.getView();
        container.addView(engineView);

        // Set up client for video detection
        engine.setEngineClient(new VideoSnifferClient());

        // Close button
        btnClose.setOnClickListener(v -> finishWithError("Cancelled by user"));

        // Start timeout
        timeoutRunnable = () -> {
            if (!isDestroyed && !resultDelivered) {
                updateStatus("⏱️ Timeout");
                finishWithError("Timeout after " + (timeout / 1000) + "s");
            }
        };
        handler.postDelayed(timeoutRunnable, timeout);

        // Initialize scraper manager for cookie restoration
        scraperManager = WebViewScraperManager.getInstance(this);

        // Load URL - check if it contains embedded Referer (|Referer=...)
        Log.d(TAG, "=== URL LOADING DEBUG ===");
        Log.d(TAG, "Original URL: " + targetUrl);
        Log.d(TAG, "Engine: " + WebEngineFactory.getInstance().getEngineName());
        Log.d(TAG, "Contains |Referer=: " + targetUrl.contains("|Referer="));
        updateStatus("Loading...");

        // Parse embedded Referer from URL (format: url|Referer=referer_url)
        String cleanUrl = targetUrl;
        Map<String, String> headers = new HashMap<>();

        // Add Standard Headers (Client Hints, Accept, etc.)
        headers.putAll(getStandardHeaders());

        // CRITICAL: Add User-Agent to headers for OkHttp
        if (finalUserAgent != null) {
            headers.put("User-Agent", finalUserAgent);
            Log.d(TAG, "Added User-Agent to headers: " + finalUserAgent);

            // Cleanup Chrome-specific headers if using Firefox/Gecko UA to avoid detection
            if (finalUserAgent.contains("Firefox") || finalUserAgent.contains("Gecko")) {
                headers.remove("Sec-Ch-Ua-Mobile");
                headers.remove("Sec-Ch-Ua-Platform");
                // We keep Upgrade-Insecure-Requests here for OkHttp,
                // but we'll remove it for GeckoView.loadUrl below
            }
        }

        // 1. Handle Referer from Intent (Priority)
        String intentReferer = getIntent().getStringExtra("EXTRA_REFERER");
        if (intentReferer != null && !intentReferer.isEmpty()) {
            headers.put("Referer", intentReferer);
            Log.d(TAG, "Added Referer from Intent: " + intentReferer);
        }

        // 2. Handle Referer from URL Pipe (Fallback/Override)
        if (targetUrl.contains("|Referer=")) {
            int refererIdx = targetUrl.indexOf("|Referer=");
            cleanUrl = targetUrl.substring(0, refererIdx);
            String refererPart = targetUrl.substring(refererIdx + 9);
            // Handle additional query params after referer (e.g., &__cf_chl_tk=...)
            int ampIdx = refererPart.indexOf("&");
            String referer = ampIdx > 0 ? refererPart.substring(0, ampIdx) : refererPart;
            headers.put("Referer", referer);
            Log.d(TAG, "Extracted Referer from URL: " + referer);
        } else {
            Log.d(TAG, "No embedded Referer found in URL");
        }

        // 3. Handle Cookies from Intent (Critical for Hybrid Fetch)
        String intentCookies = getIntent().getStringExtra("EXTRA_COOKIES");
        if (intentCookies != null && !intentCookies.isEmpty()) {
            Log.d(TAG, "Injecting Intent Cookies into CookieManager: " + intentCookies);
            // Inject into CookieManager so OkHttp and WebView share them
            String[] cookieParts = intentCookies.split(";");
            for (String cookie : cookieParts) {
                if (!cookie.trim().isEmpty()) {
                    CookieManager.getInstance().setCookie(cleanUrl, cookie.trim());
                }
            }
            CookieManager.getInstance().flush();
            headers.put("Cookie", intentCookies);
        }

        // Pass cookies from CookieManager (Double check)
        String cookies = CookieManager.getInstance().getCookie(cleanUrl);
        if (cookies != null) {
            headers.put("Cookie", cookies);
            Log.d(TAG, "Verified Cookies in CookieManager: " + cookies);
        }

        // Create final copies for lambda capture
        final String finalCleanUrl = cleanUrl;
        final Map<String, String> finalHeaders = headers;

        // CRITICAL: Restore cookies from DB before loading
        Log.d(TAG, "Restoring cookies from DB before loading...");
        scraperManager.restoreCookiesForUrl(finalCleanUrl, () -> {
            // Verify restored cookies
            String restoredCookies = CookieManager.getInstance().getCookie(finalCleanUrl);
            Log.d(TAG, "Restored cookies for " + finalCleanUrl + ": " + restoredCookies);

            // Update headers with potentially new cookies after restore
            String updatedCookies = CookieManager.getInstance().getCookie(finalCleanUrl);
            if (updatedCookies != null) {
                finalHeaders.put("Cookie", updatedCookies);
                Log.d(TAG, "Updated cookies in headers: " + updatedCookies);
            }

            // Create a specific headers map for GeckoView usage
            Map<String, String> geckoHeaders = new HashMap<>(finalHeaders);

            // Clean specific headers that cause issues with GeckoView.loadUrl
            if (WebEngineFactory.getInstance().isGeckoView()) {
                geckoHeaders.remove("Upgrade-Insecure-Requests");
                // Sec-Ch-Ua already removed in base headers if UA is Gecko
            }

            // Hybrid Fetch for GeckoView
            if (WebEngineFactory.getInstance().isGeckoView()) {
                Log.d(TAG, "=== GECKOVIEW HYBRID FETCH ===");
                // Use finalHeaders (with Upgrade-Insecure-Requests) for OkHttp
                fetchWithOkHttpAndLoad(finalCleanUrl, finalHeaders);
            } else if (!geckoHeaders.isEmpty()) {
                Log.d(TAG, "=== LOADING WITH HEADERS ===");
                Log.d(TAG, "Calling engine.loadUrl(finalCleanUrl, geckoHeaders)");
                engine.loadUrl(finalCleanUrl, geckoHeaders);
            } else {
                Log.d(TAG, "=== LOADING WITHOUT HEADERS ===");
                engine.loadUrl(finalCleanUrl);
            }
        });
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(message);
            }
            Log.d(TAG, "[Status] " + message);
        });
    }

    private void deliverVideoResult(String videoUrl, Map<String, String> headers) {
        if (resultDelivered)
            return;
        resultDelivered = true;

        handler.removeCallbacks(timeoutRunnable);

        // Build headers with session info
        Map<String, String> finalHeaders = new HashMap<>();
        // Add Standard Headers first
        finalHeaders.putAll(getStandardHeaders());

        if (headers != null)
            finalHeaders.putAll(headers);

        // Add User-Agent
        finalHeaders.put("User-Agent", engine.getUserAgent());

        // Add Referer (Robust Fallback)
        String currentUrl = engine.getCurrentUrl();
        if (currentUrl == null || currentUrl.isEmpty() || currentUrl.equals("about:blank")) {
            // Fallback to initial clean target URL
            currentUrl = targetUrl;
            if (currentUrl != null && currentUrl.contains("|Referer=")) {
                currentUrl = currentUrl.substring(0, currentUrl.indexOf("|Referer="));
            }
        }
        if (currentUrl != null) {
            finalHeaders.put("Referer", currentUrl);

            // Try to get cookies (WebView-based only for now)
            try {
                String cookies = CookieManager.getInstance().getCookie(currentUrl);
                if (cookies != null) {
                    finalHeaders.put("Cookie", cookies);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get cookies: " + e.getMessage());
            }
        }

        Log.d(TAG, "Video found: " + videoUrl);
        updateStatus("✅ Video Found!");

        Intent result = new Intent();
        result.putExtra(RESULT_VIDEO_URL, videoUrl);
        result.putExtra(RESULT_HEADERS, (Serializable) finalHeaders);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private void finishWithError(String message) {
        if (resultDelivered)
            return;
        resultDelivered = true;

        handler.removeCallbacks(timeoutRunnable);

        Intent result = new Intent();
        result.putExtra("error", message);
        setResult(Activity.RESULT_CANCELED, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        handler.removeCallbacksAndMessages(null);

        if (engine != null) {
            engine.stopLoading();
            // Remove from container
            View engineView = engine.getView();
            if (engineView.getParent() != null) {
                container.removeView(engineView);
            }
            engine.destroy();
            engine = null;
        }

        super.onDestroy();
    }

    /**
     * Hybrid Fetch Strategy for GeckoView to bypass Cloudflare.
     * Fetches HTML via OkHttp (which shares CookieManager) and loads it into
     * engine.
     * This avoids the initial "No Cookie" request from GeckoView.
     */
    private void fetchWithOkHttpAndLoad(String url, Map<String, String> headers) {
        updateStatus("Fetching content (Hybrid)...");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        Request.Builder builder = new Request.Builder().url(url);

        // Add headers
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (!"Cookie".equalsIgnoreCase(entry.getKey())) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
        }

        // Add Cookies from CookieManager
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) {
            builder.header("Cookie", cookies);
            Log.d(TAG, "Hybrid Fetch: Added cookies: " + cookies);
        }

        client.newCall(builder.build()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Hybrid Fetch failed: " + e.getMessage());
                runOnUiThread(() -> {
                    updateStatus("Fetch failed. Loading directly...");
                    engine.loadUrl(url, headers); // Fallback
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Hybrid Fetch HTTP error: " + response.code());
                    runOnUiThread(() -> {
                        updateStatus("Fetch error " + response.code() + ". Loading directly...");
                        engine.loadUrl(url, headers); // Fallback
                    });
                    response.close();
                    return;
                }

                String html = response.body().string();
                String finalUrl = response.request().url().toString(); // Handle redirects

                runOnUiThread(() -> {
                    Log.d(TAG, "Hybrid Fetch success. Loading HTML (" + html.length() + " chars)");
                    updateStatus("Content fetched. Rendering...");
                    // Use loadHtml with baseUrl
                    engine.loadHtml(html, finalUrl);
                });
            }
        });
    }

    /**
     * Client implementation that detects video URLs.
     */
    private class VideoSnifferClient implements IWebEngineClient {

        @Override
        public void onPageStarted(IWebEngine engine, String url, Bitmap favicon) {
            updateStatus("Loading: " + truncateUrl(url));
        }

        @Override
        public void onPageFinished(IWebEngine engine, String url) {
            updateStatus("Page loaded. Sniffing for videos...");

            // Inject robust auto-play and ad-skip script (ported from SnifferActivity)
            injectAdSkipAndAutoPlayScript();

            // Try JS-based video detection
            injectVideoDetectionScript();
        }

        private void injectAdSkipAndAutoPlayScript() {
            if (engine == null || resultDelivered)
                return;

            String script = "(function() {" +
                    "console.log('[AdSkip] Initializing...');" +
                    // JWPlayer ad skip
                    "function skipJWAd() {" +
                    "  try {" +
                    "    if (typeof jwplayer !== 'undefined') {" +
                    "      var player = jwplayer();" +
                    "      if (player && player.getState) {" +
                    "        console.log('[AdSkip] JWPlayer found, state: ' + player.getState());" +
                    // Try to seek to end of ad
                    "        var duration = player.getDuration();" +
                    "        if (duration > 0 && duration < 30) {" + // Short video = ad
                    "          console.log('[AdSkip] Short video detected (' + duration + 's), seeking to end...');" +
                    "          player.seek(duration - 0.5);" +
                    "          player.setMute(true);" +
                    "        }" +
                    "      }" +
                    "    }" +
                    "  } catch(e) { console.log('[AdSkip] JWPlayer error: ' + e); }" +
                    "}" +
                    // HTML5 video speed up
                    "function speedUpVideos() {" +
                    "  var videos = document.getElementsByTagName('video');" +
                    "  for (var i = 0; i < videos.length; i++) {" +
                    "    var v = videos[i];" +
                    "    if (v.duration > 0 && v.duration < 30) {" + // Short video = ad
                    "      console.log('[AdSkip] Speeding up ad video: ' + v.duration + 's');" +
                    "      v.playbackRate = 16;" + // Max speed
                    "      v.muted = true;" +
                    "      v.currentTime = v.duration - 0.5;" + // Seek to near end
                    "    }" +
                    "  }" +
                    "}" +
                    // Click skip button if visible
                    "function clickSkipButton() {" +
                    "  var skipSelectors = [" +
                    "    '.jw-skip', '.skip-button', '.skip-ad', '[class*=\"skip\"]'," +
                    "    '.vast-skip-button', '.videoAdUiSkipButton', '[aria-label*=\"Skip\"]'" +
                    "  ];" +
                    "  for (var i = 0; i < skipSelectors.length; i++) {" +
                    "    var btn = document.querySelector(skipSelectors[i]);" +
                    "    if (btn && btn.offsetParent !== null) {" +
                    "      console.log('[AdSkip] Found skip button, clicking...');" +
                    "      btn.click();" +
                    "      return true;" +
                    "    }" +
                    "  }" +
                    "  return false;" +
                    "}" +
                    // Auto-start video by clicking on player elements
                    "function autoStartVideo() {" +
                    "  try {" +
                    // Try JWPlayer play()
                    "    if (typeof jwplayer !== 'undefined') {" +
                    "      var player = jwplayer();;" +
                    "      if (player && player.play) {" +
                    "        console.log('[AdSkip] Calling jwplayer.play() (muted)...');" +
                    "        player.setMute(true);" +
                    "        player.play();" +
                    "      }" +
                    "    }" +
                    // Click on video element
                    "    var videos = document.getElementsByTagName('video');" +
                    "    for (var i = 0; i < videos.length; i++) {" +
                    "      if (videos[i].paused) {" +
                    "        console.log('[AdSkip] Video paused, starting muted...');" +
                    "        videos[i].muted = true;" +
                    "        videos[i].click();" +
                    "        videos[i].play().catch(function(e){ console.log('[AdSkip] Play error: ' + e); });" +
                    "      }" +
                    "    }" +
                    // Click on common play button selectors
                    "    var playSelectors = [" +
                    "      '.jw-icon-playback', '.jw-display-icon-container', '.vjs-big-play-button'," +
                    "      '.play-button', '.poster', '[aria-label*=\"Play\"]', '.plyr__control--overlaid'," +
                    "      '.ytp-large-play-button', 'button[data-plyr=\"play\"]'" +
                    "    ];" +
                    "    for (var i = 0; i < playSelectors.length; i++) {" +
                    "      var btn = document.querySelector(playSelectors[i]);" +
                    "      if (btn && btn.offsetParent !== null) {" +
                    "        console.log('[AdSkip] Found play button: ' + playSelectors[i]);" +
                    "        btn.click();" +
                    "        break;" +
                    "      }" +
                    "    }" +
                    // Click on iframe to focus and potentially trigger play
                    "    var iframes = document.getElementsByTagName('iframe');" +
                    "    for (var i = 0; i < iframes.length; i++) {" +
                    "      var src = iframes[i].src || '';" +
                    "      if (src.indexOf('player') > -1 || src.indexOf('embed') > -1) {" +
                    "        console.log('[AdSkip] Found player iframe, focusing...');" +
                    "        iframes[i].focus();" +
                    "      }" +
                    "    }" +
                    "  } catch(e) { console.log('[AdSkip] autoStart error: ' + e); }" +
                    "}" +
                    // Run all methods
                    "autoStartVideo();" +
                    "skipJWAd();" +
                    "speedUpVideos();" +
                    "clickSkipButton();" +
                    // Repeat every second
                    "setInterval(function() {" +
                    "  autoStartVideo();" +
                    "  skipJWAd();" +
                    "  speedUpVideos();" +
                    "  clickSkipButton();" +
                    "}, 1000);" +
                    "})();";

            engine.evaluateJavascript(script, null);
        }

        @Override
        public void onProgressChanged(IWebEngine engine, int progress) {
            if (statusProgress != null) {
                statusProgress.setProgress(progress);
                statusProgress.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(IWebEngine engine, WebResourceRequest request) {
            if (request != null) {
                String url = request.getUrl().toString();
                checkForVideoUrl(url, request.getRequestHeaders());
            }
            return null; // Allow all requests
        }

        @Override
        public boolean shouldOverrideUrlLoading(IWebEngine engine, String url) {
            // Intercept videosniff:// scheme - our custom communication from JS
            // Format: videosniff://VIDEO_URL|REFERER_URL
            if (url.startsWith("videosniff://")) {
                String payload = url.substring("videosniff://".length());
                try {
                    // Parse VIDEO_URL|REFERER_URL format
                    String videoUrl;
                    String refererUrl = null;
                    int separatorIndex = payload.indexOf('|');
                    if (separatorIndex > 0) {
                        videoUrl = java.net.URLDecoder.decode(payload.substring(0, separatorIndex), "UTF-8");
                        refererUrl = java.net.URLDecoder.decode(payload.substring(separatorIndex + 1), "UTF-8");
                    } else {
                        videoUrl = java.net.URLDecoder.decode(payload, "UTF-8");
                    }

                    Log.d(TAG, "VideoSniffer intercepted: " + videoUrl + " (referer: " + refererUrl + ")");

                    if (videoUrl.startsWith("iframe:")) {
                        // Found player iframe, navigate to it with Referer header
                        String iframeSrc = videoUrl.substring(7);
                        Log.d(TAG, "Found player iframe: " + iframeSrc);
                        updateStatus("Found player iframe...");

                        // Pass Referer header when loading iframe
                        Map<String, String> iframeHeaders = new HashMap<>();
                        if (refererUrl != null && !refererUrl.isEmpty()) {
                            iframeHeaders.put("Referer", refererUrl);
                        } else {
                            // Fall back to current URL as referer
                            String currentUrl = engine.getCurrentUrl();
                            if (currentUrl != null) {
                                iframeHeaders.put("Referer", currentUrl);
                            }
                        }
                        // Add cookies to iframe headers
                        String cookies = CookieManager.getInstance().getCookie(iframeSrc);
                        if (cookies != null) {
                            iframeHeaders.put("Cookie", cookies);
                            Log.d(TAG, "Added cookies to iframe headers: " + cookies);
                        }
                        engine.loadUrl(iframeSrc, iframeHeaders);
                    } else if (isValidVideoUrl(videoUrl)) {
                        // Build headers with referer
                        Map<String, String> headers = new HashMap<>();
                        if (refererUrl != null && !refererUrl.isEmpty()) {
                            headers.put("Referer", refererUrl);
                        }
                        deliverVideoResult(videoUrl, headers);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to decode videosniff URL: " + e.getMessage());
                }
                return true; // Block navigation
            }

            // Block non-HTTP schemes, but allow javascript: and about:
            if (url.startsWith("javascript:") || url.startsWith("about:")) {
                return false;
            }

            if (!url.startsWith("http")) {
                Log.w(TAG, "Blocked non-HTTP: " + url);
                return true;
            }
            return false;
        }

        @Override
        public void onError(IWebEngine engine, int errorCode, String description, String failingUrl) {
            Log.e(TAG, "Load error: " + errorCode + " - " + description);
            updateStatus("⚠️ Error: " + description);
        }

        @Override
        public void onTitleChanged(IWebEngine engine, String title) {
            // Not used
        }

        @Override
        public void onResourceLoaded(IWebEngine engine, String url) {
            checkForVideoUrl(url, null);
        }

        private void checkForVideoUrl(String url, Map<String, String> headers) {
            if (url == null || resultDelivered)
                return;

            String lower = url.toLowerCase();
            if (lower.contains(".m3u8") || lower.contains(".mp4") ||
                    lower.contains(".mpd") || lower.contains("/hls/") ||
                    lower.contains("/dash/") || lower.contains("manifest")) {

                // Filter out tracking/analytics
                if (lower.contains("analytics") || lower.contains("tracking") ||
                        lower.contains("ads.") || lower.contains("/ads/")) {
                    return;
                }

                Log.d(TAG, "Potential video URL detected: " + url);
                runOnUiThread(() -> deliverVideoResult(url, headers != null ? new HashMap<>(headers) : null));
            }
        }
    }

    /**
     * Inject JavaScript to find video sources in the DOM.
     * Uses videosniff:// custom scheme to communicate back since GeckoView's
     * evaluateJavascript doesn't return results.
     */
    private void injectVideoDetectionScript() {
        if (engine == null || resultDelivered)
            return;

        // Script that finds video URLs and navigates to videosniff:// scheme
        // which we intercept in shouldOverrideUrlLoading
        // Format: videosniff://VIDEO_URL|REFERER_URL
        String script = "(function() {" +
                "  if (window._videoSnifferValid) return;" +
                "  window._videoSnifferValid = true;" +
                "  console.log('[VideoSniffer] Starting detection loop...');" +
                "  function reportVideo(url) {" +
                "    if (url && !window._videoSniffed) {" +
                "      window._videoSniffed = true;" +
                "      var referer = document.referrer || window.location.href;" +
                "      console.log('[VideoSniffer] Found: ' + url + ' (referer: ' + referer + ')');" +
                "      window.location.href = 'videosniff://' + encodeURIComponent(url) + '|' + encodeURIComponent(referer);"
                +
                "    }" +
                "  }" +
                "  function check() {" +
                "    if (window._videoSniffed) return;" +
                "    var videos = document.querySelectorAll('video, source');" +
                "    for (var i = 0; i < videos.length; i++) {" +
                "      var src = videos[i].src || videos[i].getAttribute('data-src');" +
                "      if (src && (src.includes('.m3u8') || src.includes('.mp4') || src.includes('.mpd'))) {" +
                "        reportVideo(src);" +
                "        return;" +
                "      }" +
                "    }" +
                "    if (typeof jwplayer !== 'undefined') {" +
                "      try {" +
                "        var player = jwplayer();" +
                "        if (player && player.getPlaylistItem) {" +
                "          var item = player.getPlaylistItem();" +
                "          if (item && item.file) {" +
                "            reportVideo(item.file);" +
                "            return;" +
                "          }" +
                "        }" +
                "      } catch(e) { console.log('[VideoSniffer] JWPlayer error: ' + e); }" +
                "    }" +
                "    var iframes = document.querySelectorAll('iframe');" +
                "    for (var i = 0; i < iframes.length; i++) {" +
                "      var src = iframes[i].src;" +
                "      if (src && (src.includes('player') || src.includes('embed') || src.includes('video')) && !src.includes('unknown')) {"
                +
                "         if (src.includes('youtube') || src.includes('google')) return;" +
                "         window.location.href = 'videosniff://iframe:' + encodeURIComponent(src);" +
                "         window._videoSniffed = true;" +
                "         return;" +
                "      }" +
                "    }" +
                "  }" +
                "  check();" +
                "  setInterval(check, 1000);" +
                "})();";

        engine.evaluateJavascript(script, null);
    }

    private boolean isValidVideoUrl(String url) {
        if (url == null)
            return false;
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".mp4") ||
                lower.contains(".mpd") || lower.contains("/hls/");
    }

    private Map<String, String> getStandardHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.9,ar;q=0.8");
        headers.put("Sec-Ch-Ua-Mobile", "?1");
        headers.put("Sec-Ch-Ua-Platform", "\"Android\"");
        headers.put("Upgrade-Insecure-Requests", "1");
        return headers;
    }

    private String truncateUrl(String url) {
        if (url == null)
            return "";
        return url.length() > 50 ? url.substring(0, 50) + "..." : url;
    }
}
