package com.omarflex5.temp.omerflex.service.cloudflare;

import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Advanced video URL detector that uses multiple detection strategies.
 * 
 * This detector goes beyond simple MIME type checking to identify video URLs
 * using URL patterns, file extensions, content-type headers, content-length
 * analysis, and streaming protocol detection.
 * 
 * Designed to detect full video URLs, not segments (HLS/DASH fragments).
 * 
 * @author Your Name
 * @version 1.0
 */
public class VideoUrlDetector {
    
    private static final String TAG = "VideoUrlDetector";
    
    /**
     * Video file extensions
     */
    private static final List<String> VIDEO_EXTENSIONS = Arrays.asList(
        ".mp4", ".m4v", ".mov", ".avi", ".mkv", ".flv", ".wmv", ".webm",
        ".3gp", ".3g2", ".ogv", ".mpg", ".mpeg", ".m2v", ".m4p"
    );
    
    /**
     * Streaming manifest extensions (these are NOT segment files)
     */
    private static final List<String> MANIFEST_EXTENSIONS = Arrays.asList(
        ".m3u8", ".mpd", ".ism/manifest"
    );
    
    /**
     * Video segment patterns to EXCLUDE (we want main videos, not segments)
     */
    private static final List<Pattern> SEGMENT_PATTERNS = Arrays.asList(
        Pattern.compile(".*segment[0-9]+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*chunk[0-9]+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*frag[0-9]+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/[0-9]+\\.ts$", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/[0-9]+\\.m4s$", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*init\\.mp4$", Pattern.CASE_INSENSITIVE)
    );
    
    /**
     * Video MIME types
     */
    private static final List<String> VIDEO_MIME_TYPES = Arrays.asList(
        "video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo",
        "video/x-flv", "video/webm", "video/x-matroska", "video/3gpp",
        "video/ogg", "application/x-mpegURL", "application/vnd.apple.mpegurl",
        "application/dash+xml"
    );
    
    /**
     * Minimum size for video files (1MB)
     * Helps distinguish full videos from thumbnails/previews
     */
    private static final long MIN_VIDEO_SIZE = 1024 * 1024;
    
    /**
     * Maximum size for segments (typically < 10MB)
     * Full videos are usually larger
     */
    private static final long MAX_SEGMENT_SIZE = 10 * 1024 * 1024;
    
    /**
     * Domains known for video CDN/streaming
     */
    private static final List<String> VIDEO_CDN_PATTERNS = Arrays.asList(
        "cloudfront.net", "akamaihd.net", "fastly.net",
        "googleapis.com", "googlevideo.com",
        "vimeocdn.com", "cloudflarestream.com",
        "jwplatform.com", "jwpcdn.com", "brightcove.net"
    );
    
    private final VideoDetectionCallback callback;
    private final Set<String> detectedUrls = new HashSet<>();
    
    /**
     * Callback interface for video detection
     */
    public interface VideoDetectionCallback {
        void onVideoUrlDetected(String videoUrl);
    }
    
    /**
     * Constructor
     * 
     * @param callback Callback for when video URLs are detected
     */
    public VideoUrlDetector(VideoDetectionCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Analyze a web resource request to detect video URLs
     * 
     * @param request The web resource request
     */
    public void analyzeRequest(WebResourceRequest request) {
        if (request == null) return;
        
        String url = request.getUrl().toString();
        Map<String, String> headers = request.getRequestHeaders();
        
        analyzeUrl(url, headers, null);
    }
    
    /**
     * Analyze a web resource response to detect video URLs
     * 
     * @param url The resource URL
     * @param response The web resource response
     */
    public void analyzeResponse(String url, WebResourceResponse response) {
        if (url == null || response == null) return;
        
        String mimeType = response.getMimeType();
        Map<String, String> headers = response.getResponseHeaders();
        
        analyzeUrl(url, null, mimeType);
        
        // Check response headers for content-type and content-length
        if (headers != null) {
            analyzeResponseHeaders(url, headers);
        }
    }
    
    /**
     * Main URL analysis method using multiple detection strategies
     * 
     * @param url The URL to analyze
     * @param requestHeaders Request headers (may be null)
     * @param responseMimeType Response MIME type (may be null)
     */
    public void analyzeUrl(String url, Map<String, String> requestHeaders, String responseMimeType) {
        if (url == null || url.isEmpty()) return;
        
        // Skip if already detected
        if (detectedUrls.contains(url)) return;
        
        // Skip if it's a segment
        if (isSegment(url)) {
            Log.d(TAG, "Skipping segment: " + url);
            return;
        }
        
        int score = 0;
        
        // Strategy 1: Check file extension
        if (hasVideoExtension(url)) {
            score += 30;
            Log.d(TAG, "Video extension detected (+30): " + url);
        }
        
        // Strategy 2: Check manifest extension
        if (hasManifestExtension(url)) {
            score += 40;
            Log.d(TAG, "Manifest extension detected (+40): " + url);
        }
        
        // Strategy 3: Check MIME type
        if (responseMimeType != null && isVideoMimeType(responseMimeType)) {
            score += 35;
            Log.d(TAG, "Video MIME type detected (+35): " + responseMimeType);
        }
        
        // Strategy 4: Check URL patterns
        if (containsVideoKeywords(url)) {
            score += 15;
            Log.d(TAG, "Video keywords detected (+15): " + url);
        }
        
        // Strategy 5: Check if from video CDN
        if (isVideoCdnUrl(url)) {
            score += 20;
            Log.d(TAG, "Video CDN detected (+20): " + url);
        }
        
        // Strategy 6: Check URL structure
        if (hasVideoUrlStructure(url)) {
            score += 10;
            Log.d(TAG, "Video URL structure detected (+10): " + url);
        }
        
        // Threshold: 50 points = high confidence video URL
        if (score >= 50) {
            reportVideoUrl(url, score);
        } else if (score >= 30) {
            Log.d(TAG, "Potential video URL (score: " + score + "): " + url);
        }
    }
    
    /**
     * Analyze response headers for video indicators
     */
    private void analyzeResponseHeaders(String url, Map<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue().toLowerCase();
            
            // Check Content-Type header
            if (key.equals("content-type") && isVideoMimeType(value)) {
                Log.d(TAG, "Video Content-Type header detected: " + value);
            }
            
            // Check Content-Length for video size
            if (key.equals("content-length")) {
                try {
                    long size = Long.parseLong(value);
                    if (size >= MIN_VIDEO_SIZE && size <= MAX_SEGMENT_SIZE * 100) {
                        Log.d(TAG, "Video-sized content detected: " + formatSize(size));
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * Check if URL is a segment file (to be excluded)
     */
    private boolean isSegment(String url) {
        for (Pattern pattern : SEGMENT_PATTERNS) {
            if (pattern.matcher(url).matches()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if URL has video file extension
     */
    private boolean hasVideoExtension(String url) {
        String lowerUrl = url.toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (lowerUrl.contains(ext)) {
                // Make sure it's at the end or followed by query params
                int index = lowerUrl.indexOf(ext);
                int afterExt = index + ext.length();
                if (afterExt >= lowerUrl.length() || 
                    lowerUrl.charAt(afterExt) == '?' || 
                    lowerUrl.charAt(afterExt) == '#') {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Check if URL has streaming manifest extension
     */
    private boolean hasManifestExtension(String url) {
        String lowerUrl = url.toLowerCase();
        for (String ext : MANIFEST_EXTENSIONS) {
            if (lowerUrl.contains(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if MIME type is a video type
     */
    private boolean isVideoMimeType(String mimeType) {
        if (mimeType == null) return false;
        
        String lowerMime = mimeType.toLowerCase();
        for (String videoMime : VIDEO_MIME_TYPES) {
            if (lowerMime.contains(videoMime)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if URL contains video-related keywords
     */
    private boolean containsVideoKeywords(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("/video/") || 
               lowerUrl.contains("/videos/") ||
               lowerUrl.contains("/media/") ||
               lowerUrl.contains("/stream/") ||
               lowerUrl.contains("/play/") ||
               lowerUrl.contains("videoplayback") ||
               lowerUrl.contains("/playlist/");
    }
    
    /**
     * Check if URL is from a known video CDN
     */
    private boolean isVideoCdnUrl(String url) {
        String lowerUrl = url.toLowerCase();
        for (String cdn : VIDEO_CDN_PATTERNS) {
            if (lowerUrl.contains(cdn)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if URL has typical video URL structure
     */
    private boolean hasVideoUrlStructure(String url) {
        // Look for patterns like: /v/xxx, /watch?v=xxx, /embed/xxx
        return url.matches(".*/(v|watch|embed|player)/.*") ||
               url.contains("?v=") ||
               url.contains("&v=");
    }
    
    /**
     * Report detected video URL
     */
    private void reportVideoUrl(String url, int score) {
        if (detectedUrls.add(url)) {
            Log.i(TAG, "VIDEO URL DETECTED (score: " + score + "): " + url);
            
            if (callback != null) {
                callback.onVideoUrlDetected(url);
            }
        }
    }
    
    /**
     * Format file size for logging
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024 * 1024 * 1024)) + " GB";
    }
    
    /**
     * Reset detected URLs (useful when starting a new page)
     */
    public void reset() {
        detectedUrls.clear();
        Log.d(TAG, "Video detector reset");
    }
}
