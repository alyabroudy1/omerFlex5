package com.omarflex5.temp.omerflex.service.cloudflare;

import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * Custom WebChromeClient for enhanced video detection and debugging.
 * 
 * This client monitors console messages, JavaScript dialogs, and page
 * loading progress to assist with video detection and troubleshooting.
 * 
 * @author Your Name
 * @version 1.0
 */
public class CloudflareWebChromeClient extends WebChromeClient {
    
    private static final String TAG = "CloudflareWebChrome";
    
    private final VideoUrlDetector videoUrlDetector;
    
    /**
     * Constructor
     * 
     * @param videoUrlDetector Video URL detector instance (may be null)
     */
    public CloudflareWebChromeClient(VideoUrlDetector videoUrlDetector) {
        this.videoUrlDetector = videoUrlDetector;
    }
    
    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        
        // Log page loading progress
        if (newProgress % 25 == 0) {
            Log.d(TAG, "Page loading progress: " + newProgress + "%");
        }
        
        // When page is substantially loaded, inject video detection script
        if (newProgress >= 70 && videoUrlDetector != null) {
            injectVideoDetectionScript(view);
        }
    }
    
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        // Log console messages for debugging
        String message = consoleMessage.message();
        String source = consoleMessage.sourceId();
        int line = consoleMessage.lineNumber();
        
        Log.d(TAG, "Console [" + consoleMessage.messageLevel() + "] " + 
              source + ":" + line + " - " + message);
        
        // Detect video-related console messages
        if (videoUrlDetector != null) {
            detectVideoFromConsoleMessage(message);
        }
        
        return true;
    }
    
    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        Log.d(TAG, "JavaScript Alert: " + message);
        
        // Allow the alert to proceed
        result.confirm();
        return true;
    }
    
    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        Log.d(TAG, "JavaScript Confirm: " + message);
        
        // Auto-confirm for automated operation
        result.confirm();
        return true;
    }
    
    @Override
    public void onReceivedTitle(WebView view, String title) {
        super.onReceivedTitle(view, title);
        Log.d(TAG, "Page title: " + title);
    }
    
    /**
     * Inject JavaScript to detect video elements and sources
     */
    private void injectVideoDetectionScript(WebView view) {
        String script = 
            "(function() {" +
            "  try {" +
            "    var videos = [];" +
            "    " +
            "    // Find all video elements" +
            "    document.querySelectorAll('video').forEach(function(video) {" +
            "      if (video.src) videos.push(video.src);" +
            "      if (video.currentSrc) videos.push(video.currentSrc);" +
            "      " +
            "      // Check source elements" +
            "      video.querySelectorAll('source').forEach(function(source) {" +
            "        if (source.src) videos.push(source.src);" +
            "      });" +
            "    });" +
            "    " +
            "    // Find iframe video embeds" +
            "    document.querySelectorAll('iframe').forEach(function(iframe) {" +
            "      if (iframe.src && (iframe.src.includes('video') || " +
            "          iframe.src.includes('player') || " +
            "          iframe.src.includes('embed'))) {" +
            "        videos.push(iframe.src);" +
            "      }" +
            "    });" +
            "    " +
            "    // Find object/embed elements" +
            "    document.querySelectorAll('object, embed').forEach(function(el) {" +
            "      if (el.data) videos.push(el.data);" +
            "      if (el.src) videos.push(el.src);" +
            "    });" +
            "    " +
            "    // Look for video URLs in data attributes" +
            "    document.querySelectorAll('[data-src], [data-video], [data-url]').forEach(function(el) {" +
            "      var dataSrc = el.getAttribute('data-src') || " +
            "                    el.getAttribute('data-video') || " +
            "                    el.getAttribute('data-url');" +
            "      if (dataSrc && (dataSrc.includes('.mp4') || " +
            "                      dataSrc.includes('.m3u8') || " +
            "                      dataSrc.includes('video'))) {" +
            "        videos.push(dataSrc);" +
            "      }" +
            "    });" +
            "    " +
            "    return JSON.stringify({videos: videos, count: videos.length});" +
            "  } catch(e) {" +
            "    return JSON.stringify({error: e.message});" +
            "  }" +
            "})();";
        
        view.evaluateJavascript(script, result -> {
            if (result != null && !result.equals("null")) {
                try {
                    // Remove quotes and parse
                    result = result.substring(1, result.length() - 1);
                    result = result.replace("\\\"", "\"");
                    
                    Log.d(TAG, "Video detection result: " + result);
                    
                    // Parse and extract video URLs
                    parseVideoDetectionResult(result);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing video detection result", e);
                }
            }
        });
    }
    
    /**
     * Parse video detection result from injected JavaScript
     */
    private void parseVideoDetectionResult(String result) {
        if (result == null || result.isEmpty() || videoUrlDetector == null) {
            return;
        }
        
        // Simple parsing (you could use a JSON library for more robust parsing)
        if (result.contains("\"videos\":[")) {
            int start = result.indexOf("[");
            int end = result.lastIndexOf("]");
            
            if (start != -1 && end != -1) {
                String videosArray = result.substring(start + 1, end);
                String[] urls = videosArray.split(",");
                
                for (String url : urls) {
                    url = url.trim().replace("\"", "");
                    if (!url.isEmpty() && url.startsWith("http")) {
                        Log.d(TAG, "Video element found: " + url);
                        // Analyze through the detector
                        videoUrlDetector.analyzeUrl(url, null, null);
                    }
                }
            }
        }
    }
    
    /**
     * Detect video URLs from console messages
     * Some video players log URLs to console
     */
    private void detectVideoFromConsoleMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        // Look for URLs in console messages
        if (message.contains("http") && 
            (message.contains(".mp4") || 
             message.contains(".m3u8") || 
             message.contains("video") ||
             message.contains("stream"))) {
            
            // Extract URL from message
            String[] parts = message.split("\\s+");
            for (String part : parts) {
                if (part.startsWith("http")) {
                    // Remove trailing punctuation
                    part = part.replaceAll("[,;:'\"]$", "");
                    Log.d(TAG, "Potential video URL from console: " + part);
                    videoUrlDetector.analyzeUrl(part, null, null);
                }
            }
        }
    }
}
