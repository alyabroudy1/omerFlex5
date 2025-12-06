package com.omarflex5.temp.webtest;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Pattern;

public class SmartWebViewClient extends WebViewClient {
    private final MainActivity activity;
    private final String originalDomain;
    // Regex for video files: .m3u8, .mp4, .mkv, .webm
    // Exclude segments: .ts, .m4s
    private static final Pattern VIDEO_PATTERN = Pattern.compile(".*\\.(m3u8|mp4|mkv|webm|mpd)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SEGMENT_PATTERN = Pattern.compile(".*\\.(ts|m4s)(\\?.*)?$", Pattern.CASE_INSENSITIVE);

    private final String userAgent;

    public SmartWebViewClient(MainActivity activity, String originalDomain, String userAgent) {
        this.activity = activity;
        this.originalDomain = originalDomain;
        this.userAgent = userAgent;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        String host = request.getUrl().getHost();

        // 1. Block any non-HTTP/HTTPS URLs (market://, intent://, etc.)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            activity.logStatus("BLOCKED non-HTTP URL: " + url);
            return true; // Block the navigation
        }

        // 2. Check if this is the same domain (or subdomain) as the original
        if (host != null && host.contains(originalDomain)) {
            // Same domain - allow navigation
            return false;
        }

        // 3. External domain - ask for user confirmation
        activity.showRedirectionDialog(url);
        return true; // Block until user approves
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        activity.logStatus("Loading: " + url);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        // INTERCEPT AND PROXY VIDEO REQUESTS
        // Check if this is a video/playlist request
        if (url.contains(".m3u8") || url.contains(".mpd") || url.contains(".mp4") ||
                url.contains("/hls/") || url.contains("/dash/")) {

            activity.logStatus("Intercepting video request: " + url);

            // Capture the actual headers from this request
            java.util.Map<String, String> headers = request.getRequestHeaders();

            if (headers != null && !headers.isEmpty()) {
                // Store headers for this video URL
                activity.storeVideoHeaders(url, headers);
                activity.logStatus("Stored " + headers.size() + " headers for video");
            }

            // For WebView-backed DataSource, we MUST allow the request to proceed
            // so that our JS fetcher (XHR) can succeed.
            // We also notify the activity that a video was detected, but we don't block it.
            activity.onVideoDetected(url);
            return null;
        }

        // 2. Filter out common resources to avoid unnecessary network requests
        if (isCommonResource(url)) {
            return super.shouldInterceptRequest(view, request);
        }

        // 3. Slow Path: Check Content-Type via HEAD request
        // This handles video URLs that don't have standard extensions
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            String contentType = getContentType(url, cookies, userAgent);

            if (contentType != null) {
                String lowerType = contentType.toLowerCase();
                if (lowerType.startsWith("video/") ||
                        lowerType.equals("application/vnd.apple.mpegurl") ||
                        lowerType.equals("application/x-mpegurl") ||
                        lowerType.equals("application/dash+xml")) {

                    activity.logStatus("Detected video via Content-Type: " + contentType);
                    activity.onVideoDetected(url);
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
                }
            }
        } catch (Exception e) {
            // Ignore errors during detection
        }

        // Block beacon.min.js to prevent CORS errors
        if (url.contains("beacon.min.js")) {
            activity.logStatus("Blocking beacon.min.js");
            return new WebResourceResponse("text/javascript", "UTF-8", null);
        }

        // Let WebView handle everything else natively
        return super.shouldInterceptRequest(view, request);
    }

    // Helper to check for common non-video resources
    private boolean isCommonResource(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".js") || lowerUrl.endsWith(".css") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") ||
                lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".gif") ||
                lowerUrl.endsWith(".svg") || lowerUrl.endsWith(".ico") ||
                lowerUrl.endsWith(".woff") || lowerUrl.endsWith(".woff2") ||
                lowerUrl.endsWith(".ttf") || lowerUrl.endsWith(".json") ||
                lowerUrl.endsWith(".xml") || lowerUrl.endsWith(".html") ||
                lowerUrl.endsWith(".htm");
    }

    // Helper to perform HEAD request
    private String getContentType(String urlString, String cookies, String userAgent) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000); // Short timeout
            connection.setReadTimeout(3000);
            if (userAgent != null) {
                connection.setRequestProperty("User-Agent", userAgent);
            }
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

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        activity.logStatus("Page Finished: " + url);

        // Inject AndroidFetcher JavaScript
        view.postDelayed(() -> {
            String fetcherJs = "javascript:(function() {" +
                    "  window.AndroidFetcher = {" +
                    "    fetch: function(id, url) {" +
                    "      console.log('AndroidFetcher: Fetching', id, url);" +
                    "      var xhr = new XMLHttpRequest();" +
                    "      xhr.responseType = 'arraybuffer';" +
                    "      xhr.onreadystatechange = function() {" +
                    "        if (xhr.readyState === 4) {" +
                    "          if (xhr.status === 200) {" +
                    "            var bytes = new Uint8Array(xhr.response);" +
                    "            var binary = '';" +
                    "            var len = bytes.byteLength;" +
                    "            for (var i = 0; i < len; i++) {" +
                    "              binary += String.fromCharCode(bytes[i]);" +
                    "            }" +
                    "            /* Send in chunks to avoid memory limits */" +
                    "            var chunkSize = 1024 * 1024; /* 1MB chunks */" +
                    "            for (var i = 0; i < len; i += chunkSize) {" +
                    "              var chunk = binary.substring(i, Math.min(i + chunkSize, len));" +
                    "              var base64 = btoa(chunk);" +
                    "              AndroidFetcherBridge.onFetchData(id.toString(), base64);" +
                    "            }" +
                    "            AndroidFetcherBridge.onFetchComplete(id.toString(), len.toString());" +
                    "            console.log('AndroidFetcher: Complete', id, len);" +
                    "          } else {" +
                    "            AndroidFetcherBridge.onFetchError(id.toString(), 'HTTP ' + xhr.status);" +
                    "          }" +
                    "        }" +
                    "      };" +
                    "      xhr.onerror = function() {" +
                    "        AndroidFetcherBridge.onFetchError(id.toString(), 'Network error');" +
                    "      };" +
                    "      xhr.open('GET', url);" +
                    "      xhr.send();" +
                    "    }" +
                    "  };" +
                    "  console.log('AndroidFetcher initialized');" +
                    "})();";

            view.evaluateJavascript(fetcherJs, null);
        }, 500);

        // Wait 2 seconds for Cloudflare's JavaScript to execute and set cookies
        view.postDelayed(() -> {
            String js = "javascript:(function() {" +
                    "   try {" +
                    "       console.log('[IframeMonitor] Starting iframe monitor');" +
                    "" +
                    "       /* Cloudflare Detection */" +
                    "       var cfForm = document.getElementById('challenge-form');" +
                    "       var cfSpinner = document.getElementById('cf-spinner');" +
                    "       var cfError = document.getElementById('challenge-error-text');" +
                    "       var htmlContent = document.documentElement ? document.documentElement.innerHTML : '';" +
                    "       var hasErrorText = htmlContent.indexOf('Enable JavaScript and cookies to continue') !== -1;"
                    +
                    "       var isDetected = (cfForm != null || cfSpinner != null || cfError != null || hasErrorText);"
                    +
                    "       window.Android.onCloudflareDetected(isDetected);" +
                    "       if (!isDetected && document.documentElement) {" +
                    "           window.Android.onHtmlExtracted(document.documentElement.outerHTML, '');" +
                    "       }" +
                    "" +
                    "       /* Iframe Fullscreen System */" +
                    "       var fullscreenIframe = null;" +
                    "" +
                    "       function enterFullscreen(iframe) {" +
                    "           if (!iframe || fullscreenIframe) return;" +
                    "           console.log('[IframeMonitor] Entering fullscreen for:', iframe.src);" +
                    "           fullscreenIframe = iframe;" +
                    "           " +
                    "           iframe.dataset.origPos = iframe.style.position || '';" +
                    "           iframe.dataset.origTop = iframe.style.top || '';" +
                    "           iframe.dataset.origLeft = iframe.style.left || '';" +
                    "           iframe.dataset.origWidth = iframe.style.width || '';" +
                    "           iframe.dataset.origHeight = iframe.style.height || '';" +
                    "           iframe.dataset.origZIndex = iframe.style.zIndex || '';" +
                    "           " +
                    "           iframe.style.position = 'fixed';" +
                    "           iframe.style.top = '0px';" +
                    "           iframe.style.left = '0px';" +
                    "           iframe.style.width = '100vw';" +
                    "           iframe.style.height = '100vh';" +
                    "           iframe.style.zIndex = '999999';" +
                    "           iframe.style.border = 'none';" +
                    "           document.body.style.overflow = 'hidden';" +
                    "       }" +
                    "" +
                    "       function exitFullscreen() {" +
                    "           if (!fullscreenIframe) return;" +
                    "           console.log('[IframeMonitor] Exiting fullscreen');" +
                    "           " +
                    "           var iframe = fullscreenIframe;" +
                    "           iframe.style.position = iframe.dataset.origPos;" +
                    "           iframe.style.top = iframe.dataset.origTop;" +
                    "           iframe.style.left = iframe.dataset.origLeft;" +
                    "           iframe.style.width = iframe.dataset.origWidth;" +
                    "           iframe.style.height = iframe.dataset.origHeight;" +
                    "           iframe.style.zIndex = iframe.dataset.origZIndex;" +
                    "           document.body.style.overflow = '';" +
                    "           fullscreenIframe = null;" +
                    "       }" +
                    "" +
                    "       function isVideoIframe(iframe) {" +
                    "           var src = (iframe.src || '').toLowerCase();" +
                    "           return src.indexOf('player') >= 0 || src.indexOf('embed') >= 0 || " +
                    "                  src.indexOf('video') >= 0 || src.indexOf('dood') >= 0 || " +
                    "                  src.indexOf('stream') >= 0 || src.indexOf('/e/') >= 0 || " +
                    "                  src.indexOf('/v/') >= 0 || src.indexOf('/play') >= 0;" +
                    "       }" +
                    "" +
                    "       function setupIframe(iframe) {" +
                    "           if (iframe.dataset.fsSetup) return;" +
                    "           iframe.dataset.fsSetup = 'true';" +
                    "           " +
                    "           if (isVideoIframe(iframe)) {" +
                    "               console.log('[IframeMonitor] Found video iframe:', iframe.src);" +
                    "               " +
                    "               iframe.addEventListener('click', function() {" +
                    "                   setTimeout(function() { enterFullscreen(iframe); }, 100);" +
                    "               });" +
                    "               " +
                    "               try {" +
                    "                   var iframeDoc = iframe.contentDocument || iframe.contentWindow.document;" +
                    "                   if (iframeDoc) {" +
                    "                       var videos = iframeDoc.getElementsByTagName('video');" +
                    "                       console.log('[IframeMonitor] Found', videos.length, 'videos in iframe');" +
                    "                       for (var i = 0; i < videos.length; i++) {" +
                    "                           videos[i].addEventListener('play', function() {" +
                    "                               console.log('[IframeMonitor] Video playing');" +
                    "                               enterFullscreen(iframe);" +
                    "                           });" +
                    "                       }" +
                    "                   }" +
                    "               } catch (e) {" +
                    "                   console.log('[IframeMonitor] Cross-origin iframe, using click fallback');" +
                    "               }" +
                    "           }" +
                    "       }" +
                    "" +
                    "       function scanIframes() {" +
                    "           var iframes = document.getElementsByTagName('iframe');" +
                    "           console.log('[IframeMonitor] Scanning', iframes.length, 'iframes');" +
                    "           for (var i = 0; i < iframes.length; i++) {" +
                    "               setupIframe(iframes[i]);" +
                    "           }" +
                    "       }" +
                    "" +
                    "       scanIframes();" +
                    "" +
                    "       var observer = new MutationObserver(function(mutations) {" +
                    "           mutations.forEach(function(mutation) {" +
                    "               if (mutation.type === 'childList') {" +
                    "                   scanIframes();" +
                    "               } else if (mutation.type === 'attributes' && mutation.attributeName === 'src') {" +
                    "                   var iframe = mutation.target;" +
                    "                   if (iframe.tagName === 'IFRAME' && isVideoIframe(iframe)) {" +
                    "                       console.log('[IframeMonitor] Iframe src changed to:', iframe.src);" +
                    "                       setTimeout(function() { enterFullscreen(iframe); }, 500);" +
                    "                   }" +
                    "               }" +
                    "           });" +
                    "       });" +
                    "       observer.observe(document.body, { " +
                    "           childList: true, " +
                    "           subtree: true, " +
                    "           attributes: true, " +
                    "           attributeFilter: ['src']" +
                    "       });" +
                    "" +
                    "       window.addEventListener('popstate', exitFullscreen);" +
                    "       console.log('[IframeMonitor] Monitor initialized');" +
                    "" +
                    "   } catch (e) {" +
                    "       console.error('[IframeMonitor] Error:', e.message, e.stack);" +
                    "   }" +
                    "})();";

            android.util.Log.d("SmartWebViewClient", "Injecting IframeMonitor script for: " + url);
            view.evaluateJavascript(js, result -> {
                android.util.Log.d("SmartWebViewClient", "IframeMonitor script result: " + result);
            });
        }, 2000); // Wait 2 seconds for Cloudflare challenge to complete
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        activity.logStatus("Error: " + description + " (" + errorCode + ")");
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request,
            WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        activity.logStatus("HTTP Error: " + errorResponse.getStatusCode());
    }

    @Override
    public void onLoadResource(WebView view, String url) {
        super.onLoadResource(view, url);
        // Removed redundant detection to avoid double-firing
    }
}
