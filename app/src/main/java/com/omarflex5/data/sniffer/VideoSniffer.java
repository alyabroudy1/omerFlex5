package com.omarflex5.data.sniffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enhanced Video Sniffer that uses a visible WebView to detect video URLs.
 * Features:
 * - Smart Request Interception (Regex + Headers).
 * - Content-Type Sniffing (HEAD requests).
 * - JavaScript Injection (Iframe Monitor, Cloudflare Bypass).
 * - UI Visibility (attached to provided container).
 * - Ad Filtering (ignores v.mp4 and blocks common ads).
 */
public class VideoSniffer {

    private static final String TAG = "VideoSniffer";
    private static final long SNIFF_TIMEOUT_MS = 60000; // 60 seconds
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";

    // Video Pattern from SmartWebViewClient
    private static final Pattern VIDEO_PATTERN = Pattern.compile(".*\\.(m3u8|mp4|mkv|webm|mpd)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    // Base Monitor Script Builder
    private String getMonitorScript() {
        return "javascript:(function() {" +
                "   try {" +
                "       console.log('[Sniffer] Starting monitor');" +
                "       var foundVideo = false;" +
                "       function checkVideo(src) {" +
                "           if (!src) return;" +
                "           if (src.match(/\\.(m3u8|mp4|mkv|webm|mpd)($|\\?)/i)) {" +
                "               window.SnifferAndroid.onVideoDetected(src);" +
                "               foundVideo = true;" +
                "           }" +
                "       }" +
                "       function scan() {" +
                "           /* Custom Script Injection */" +
                "           " + (customScript != null ? customScript : "") +
                "           " +
                "           var videos = document.getElementsByTagName('video');" +
                "           for(var i=0; i<videos.length; i++) {" +
                "               checkVideo(videos[i].src);" +
                "               checkVideo(videos[i].currentSrc);" +
                "           }" +
                "           var iframes = document.getElementsByTagName('iframe');" +
                "           for(var i=0; i<iframes.length; i++) {" +
                "               checkVideo(iframes[i].src);" +
                "               try {" +
                "                   var doc = iframes[i].contentDocument || iframes[i].contentWindow.document;" +
                "                   if(doc) {" +
                "                       var innerVideos = doc.getElementsByTagName('video');" +
                "                       for(var j=0; j<innerVideos.length; j++){" +
                "                           checkVideo(innerVideos[j].src);" +
                "                       }" +
                "                   }" +
                "               } catch(e){}" +
                "           }" +
                "       }" +
                "       setInterval(scan, 1000);" + // Scan every second
                "       /* Auto-Click Helper */" +
                "       setTimeout(function() {" +
                "           if(!foundVideo) {" +
                "               var poster = document.querySelector('.poster, .play-button, [aria-label=\"Play\"], .download_button, a[class*=\"download\"], a[href*=\"download\"]');"
                +
                "               if(poster) poster.click();" +
                "           }" +
                "       }, 2000);" +
                "   } catch(e) {}" +
                "})();";
    }

    private String customScript = null;

    public void setCustomScript(String script) {
        this.customScript = script;
    }

    private Context context;
    private ViewGroup container;
    private SniffCallback callback;
    private WebView webView;
    private Handler handler;
    private Runnable timeoutRunnable;
    private boolean isDestroyed = false;
    private boolean videoFound = false;

    public interface SniffCallback {
        void onVideoFound(String videoUrl, Map<String, String> headers);

        void onError(String message);

        void onProgress(String message);
    }

    public VideoSniffer(Context context, ViewGroup container, SniffCallback callback) {
        this.context = context;
        this.container = container;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void startSniffing(String pageUrl) {
        if (isDestroyed)
            return;

        Log.d(TAG, "startSniffing called for: " + pageUrl);
        callback.onProgress("Initializing...");

        // Defer all WebView operations to prevent blocking
        handler.post(() -> {
            if (isDestroyed)
                return;

            try {
                Log.d(TAG, "Creating WebView...");
                webView = new WebView(context);
                webView.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

                // Configure Settings
                Log.d(TAG, "Configuring WebView settings...");
                WebSettings settings = webView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setMediaPlaybackRequiresUserGesture(false);
                settings.setJavaScriptCanOpenWindowsAutomatically(true);
                settings.setSupportMultipleWindows(true);
                settings.setUserAgentString(USER_AGENT);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

                // Viewport Settings
                settings.setUseWideViewPort(true);
                settings.setLoadWithOverviewMode(true);
                settings.setBuiltInZoomControls(true);
                settings.setDisplayZoomControls(false);

                // Add JS Interface
                webView.addJavascriptInterface(new SnifferWebAppInterface(), "SnifferAndroid");

                // Set Client
                webView.setWebViewClient(new SmartClient());
                webView.setWebChromeClient(new WebChromeClient());

                // Attach to UI
                Log.d(TAG, "Attaching WebView to container...");
                container.removeAllViews();
                container.addView(webView);

                // Start Load
                Log.d(TAG, "Loading URL: " + pageUrl);
                callback.onProgress("Loading page...");
                webView.loadUrl(pageUrl);

                // Timeout
                timeoutRunnable = () -> {
                    if (!isDestroyed && !videoFound) {
                        callback.onError("Sniffing timed out after " + (SNIFF_TIMEOUT_MS / 1000) + "s");
                        destroy();
                    }
                };
                handler.postDelayed(timeoutRunnable, SNIFF_TIMEOUT_MS);

            } catch (Exception e) {
                Log.e(TAG, "Error in startSniffing", e);
                callback.onError("WebView Error: " + e.getMessage());
            }
        });
    }

    public void destroy() {
        if (isDestroyed)
            return; // Prevent double destroy
        isDestroyed = true;
        videoFound = true; // Stop callbacks
        handler.removeCallbacksAndMessages(null);

        // Run WebView cleanup on main thread but without blocking operations
        handler.post(() -> {
            try {
                if (webView != null) {
                    webView.stopLoading();
                    webView.setWebViewClient(null);
                    webView.setWebChromeClient(null);
                    container.removeView(webView);
                    webView.destroy();
                    webView = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error destroying WebView", e);
            }
        });
    }

    private class SmartClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (isDestroyed || videoFound)
                return;
            callback.onProgress("Loading: " + url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // STRICT: Block any non-HTTP/HTTPS redirect (e.g. intent://, market://,
            // mailto:)
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Log.d(TAG, "BLOCKED Non-HTTP Redirect: " + url);
                return true; // Block it
            }

            return super.shouldOverrideUrlLoading(view, request);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (isDestroyed || videoFound)
                return;
            callback.onProgress("Looking for video...");

            // Inject Monitor Script
            view.evaluateJavascript(getMonitorScript(), null);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (isDestroyed || videoFound)
                return super.shouldInterceptRequest(view, request);

            String url = request.getUrl().toString();

            // 1. Block Junk
            if (isCommonResource(url)) {
                return super.shouldInterceptRequest(view, request);
            }

            // 2. Check Regex
            if (VIDEO_PATTERN.matcher(url).matches() || url.contains("/hls/") || url.contains("/dash/")) {
                if (!isAdUrl(url)) {
                    notifyVideoFound(url, request.getRequestHeaders());
                } else {
                    Log.d(TAG, "Filtered Ad URL: " + url);
                }
                return null;
            }

            // 3. Head Request for Content-Type (Slow Path)
            if (!isCommonResource(url) && (url.contains("video") || url.contains("play") || url.contains("stream"))) {
                checkContentType(url, request.getRequestHeaders());
            }

            return super.shouldInterceptRequest(view, request);
        }
    }

    private boolean isAdUrl(String url) {
        String lower = url.toLowerCase();
        // Custom Blocklist
        if (lower.contains("beacon.min.js"))
            return true;
        if (lower.contains("googleads"))
            return true;
        if (lower.contains("doubleclick"))
            return true;
        if (lower.contains("analytics"))
            return true;
        // Generic low-value video names
        if (lower.endsWith("/ads.mp4") || lower.endsWith("/ad.mp4"))
            return true;
        // Length check - strict but safe for absolute URLs
        // https://a.com/v.mp4 is ~21 chars.
        if (lower.length() < 50)
            return true;
        return false;
    }

    private void checkContentType(String urlString, Map<String, String> originalHeaders) {
        new Thread(() -> {
            try {
                if (isAdUrl(urlString))
                    return;

                String cookies = CookieManager.getInstance().getCookie(urlString);
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", USER_AGENT);
                if (cookies != null)
                    conn.setRequestProperty("Cookie", cookies);

                String contentType = conn.getContentType();
                conn.disconnect();

                if (contentType != null) {
                    String lower = contentType.toLowerCase();
                    boolean isVideo = lower.startsWith("video/") || lower.equals("application/vnd.apple.mpegurl")
                            || lower.equals("application/x-mpegurl");

                    if (isVideo && !isAdUrl(urlString)) {
                        notifyVideoFound(urlString, originalHeaders);
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }).start();
    }

    private boolean isCommonResource(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".css")
                || lower.endsWith(".js") || lower.endsWith(".svg") || lower.endsWith(".woff");
    }

    private void notifyVideoFound(String url, Map<String, String> headers) {
        if (videoFound || isDestroyed)
            return;

        handler.post(() -> {
            if (videoFound || isDestroyed)
                return; // Double check on main thread

            // Auto-Inject Referer from WebView if missing
            String currentUrl = null;
            if (webView != null) {
                currentUrl = webView.getUrl();
            }

            Map<String, String> finalHeaders = new HashMap<>();
            if (headers != null)
                finalHeaders.putAll(headers);

            if (!finalHeaders.containsKey("User-Agent"))
                finalHeaders.put("User-Agent", USER_AGENT);

            if (!finalHeaders.containsKey("Referer") && currentUrl != null) {
                finalHeaders.put("Referer", currentUrl);
                Log.d(TAG, "Auto-Injected Referer: " + currentUrl);
            }

            videoFound = true;
            callback.onVideoFound(url, finalHeaders);
        });
    }

    public class SnifferWebAppInterface {
        @JavascriptInterface
        public void onVideoDetected(String url) {
            // JS detected videos also need to be filtered
            if (!isAdUrl(url)) {
                notifyVideoFound(url, null);
            } else {
                Log.d(TAG, "JS Filtered Ad URL: " + url);
            }
        }
    }
}
