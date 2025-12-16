package com.omarflex5.data.sniffer.strategy;

import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import com.omarflex5.data.sniffer.callback.SnifferCallback;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Strategy for detecting video URLs in WebView.
 * Uses URL patterns (NOT MIME type) for detection.
 */
public class VideoSniffingStrategy implements SniffingStrategy {

    private static final String TAG = "VideoSniffingStrategy";

    // Video URL patterns
    private static final Pattern VIDEO_PATTERN = Pattern.compile(
            ".*\\.(m3u8|mp4|mkv|webm|mpd)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    // Ad URL patterns to filter out
    private static final String[] AD_PATTERNS = {
            "beacon.min.js", "googleads", "doubleclick",
            "analytics", "/ads.mp4", "/ad.mp4"
    };

    private final SnifferCallback callback;
    private String customScript;
    private boolean videoFound = false;

    public VideoSniffingStrategy(SnifferCallback callback) {
        this.callback = callback;
    }

    public void setCustomScript(String script) {
        this.customScript = script;
    }

    @Override
    public SnifferCallback getCallback() {
        return callback;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (videoFound)
            return;
        callback.onProgress("Looking for video...");

        // Inject monitor script
        String monitorScript = buildMonitorScript();
        view.evaluateJavascript(monitorScript, null);
    }

    @Override
    public boolean shouldLoadResource(String url, WebResourceRequest request) {
        if (videoFound)
            return true;

        // Check URL pattern for video
        if (isVideoUrl(url)) {
            if (!isAdUrl(url)) {
                Log.d(TAG, "Video detected via URL pattern: " + url);
                return true; // Allow load, will be captured
            } else {
                Log.d(TAG, "Filtered ad video: " + url);
            }
        }

        // Block common ad resources
        if (isAdUrl(url)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean shouldOverrideUrlLoading(String url) {
        // Block non-HTTP schemes
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.d(TAG, "Blocked non-HTTP URL: " + url);
            return true;
        }
        return false;
    }

    @Override
    public String getCustomScript() {
        // We inject the custom script internally inside buildMonitorScript()
        // so we don't want the SnifferActivity to inject it separately.
        return null;
    }

    @Override
    public boolean onPotentialVideoDetected(String url, Map<String, String> headers) {
        if (videoFound || isAdUrl(url))
            return false;

        // Validate URL pattern
        if (isVideoUrl(url) || url.contains("/hls/") || url.contains("/dash/")) {
            videoFound = true;
            callback.onProgress("🎬 Video found!");
            callback.onVideoFound(url, headers);
            return true;
        }
        return false;
    }

    @Override
    public void onWafDetected(String type) {
        callback.onProgress("🛡️ Security check detected: " + type);
    }

    @Override
    public String getName() {
        return "VideoSniffing";
    }

    private boolean isVideoUrl(String url) {
        if (url == null)
            return false;
        return VIDEO_PATTERN.matcher(url).matches() ||
                url.contains("/hls/") ||
                url.contains("/dash/") ||
                url.contains(".m3u8") ||
                url.contains(".mpd");
    }

    private boolean isAdUrl(String url) {
        if (url == null)
            return false;
        String lower = url.toLowerCase();
        for (String pattern : AD_PATTERNS) {
            if (lower.contains(pattern))
                return true;
        }
        // Short URLs are usually ads/trackers
        if (lower.length() < 50 && (lower.endsWith(".mp4") || lower.endsWith(".m3u8"))) {
            return true;
        }
        return false;
    }

    private String buildMonitorScript() {
        StringBuilder script = new StringBuilder();
        script.append("javascript:(function() {");
        script.append("   try {");
        script.append("       console.log('[VideoSniffer] Starting monitor v2.0');");
        script.append("       var foundVideo = false;");
        script.append("       var clickAttempts = 0;");
        script.append("       var hasClicked = false;");
        script.append("       ");
        script.append("       function checkVideo(src) {");
        script.append("           if (!src || foundVideo) return;");
        script.append("           if (src.match(/\\.(m3u8|mp4|mkv|webm|mpd)($|\\?)/i)) {");
        script.append("               window.SnifferAndroid.onVideoDetected(src);");
        script.append("               foundVideo = true;");
        script.append("           }");
        script.append("       }");
        script.append("       ");
        script.append("       function checkCloudflare() {");
        script.append("           try {");
        script.append("               if (document.querySelector('input[name=\"cf_challenge_response\"]') ||");
        script.append("                   document.querySelector('div#challenge-stage') ||");
        script.append("                   document.title.includes('Just a moment') ||");
        script.append("                   document.querySelector('iframe[title*=\"Cloudflare\"]')) {");
        script.append("                   console.log('[VideoSniffer] Cloudflare detected!');");
        script.append("                   if (window.SnifferAndroid && window.SnifferAndroid.onCloudflareDetected) {");
        script.append("                       window.SnifferAndroid.onCloudflareDetected();");
        script.append("                   }");
        script.append("                   return true;");
        script.append("               }");
        script.append("           } catch(e) {}");
        script.append("           return false;");
        script.append("       }");
        script.append("       ");
        script.append("       function attemptClick() {");
        script.append("           if (foundVideo || hasClicked || clickAttempts > 5 || checkCloudflare()) return;");
        script.append("           ");
        script.append("           var targets = [");
        script.append("               '.vjs-big-play-button',");
        script.append("               '.jw-display-icon-container',");
        script.append("               '.plyr__control--overlaid',");
        script.append("               '#play-button',");
        script.append("               '.play-button',");
        script.append("               'button[class*=\"play\"]',");
        script.append("               'div[class*=\"play\"]',");
        script.append("               'div[id*=\"player\"] div[class*=\"overlay\"]'"); // Generic overlay
        script.append("           ];");
        script.append("           ");
        script.append("           for (var i = 0; i < targets.length; i++) {");
        script.append("               var el = document.querySelector(targets[i]);");
        script.append("               if (el && el.offsetParent !== null) {"); // Visible
        script.append("                   console.log('[VideoSniffer] Clicking: ' + targets[i]);");
        script.append("                   el.click();");
        script.append("                   hasClicked = true;");
        script.append("                   return;");
        script.append("               }");
        script.append("           }");
        script.append("           clickAttempts++;");
        script.append("       }");
        script.append("       ");
        script.append("       function scan() {");
        script.append("           if (foundVideo) return;");
        script.append("           if (checkCloudflare()) return;");
        script.append("           ");

        // Inject custom script if provided
        if (customScript != null && !customScript.isEmpty()) {
            script.append(customScript);
        }

        script.append("           var videos = document.getElementsByTagName('video');");
        script.append("           for(var i=0; i<videos.length; i++) {");
        script.append("               checkVideo(videos[i].src);");
        script.append("               checkVideo(videos[i].currentSrc);");
        script.append("           }");
        script.append("           var iframes = document.getElementsByTagName('iframe');");
        script.append("           for(var i=0; i<iframes.length; i++) {");
        script.append("               checkVideo(iframes[i].src);");
        script.append("               try {"); // Try to peer into same-origin iframes
        script.append(
                "                   var innerDoc = iframes[i].contentDocument || iframes[i].contentWindow.document;");
        script.append("                   if (innerDoc) {");
        script.append("                       var innerVideos = innerDoc.getElementsByTagName('video');");
        script.append("                       for(var j=0; j<innerVideos.length; j++) {");
        script.append("                           checkVideo(innerVideos[j].src);");
        script.append("                           checkVideo(innerVideos[j].currentSrc);");
        script.append("                       }");
        script.append("                   }");
        script.append("               } catch(e) {}");
        script.append("           }");
        script.append("           ");
        script.append("           attemptClick();");
        script.append("       }");
        script.append("       setInterval(scan, 1000);");
        script.append("       scan();"); // Initial scan
        script.append("   } catch(e) { console.error('[VideoSniffer] Error:', e); }");
        script.append("})();");

        return script.toString();
    }
}
