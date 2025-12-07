package com.omarflex5.temp.omerflex.service.cloudflare;

import android.os.Build;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * Configures WebView with optimal settings for Cloudflare bypass
 * and compatibility with old Android devices (Android 7+).
 * 
 * This class encapsulates all WebView configuration logic following
 * best practices for security, performance, and compatibility.
 * 
 * @author Your Name
 * @version 1.0
 */
public class WebViewConfigurator {
    
    private static final String TAG = "WebViewConfigurator";
    
    /**
     * Default modern User-Agent for old Android devices
     * Mimics Chrome 114 on Android 10 to avoid "outdated browser" blocks
     */
    private static final String DEFAULT_MODERN_USER_AGENT = 
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";
    
    private final WebView webView;
    private final WebSettings settings;
    
    /**
     * Constructor
     * 
     * @param webView The WebView instance to configure
     */
    public WebViewConfigurator(WebView webView) {
        this.webView = webView;
        this.settings = webView.getSettings();
    }
    
    /**
     * Apply comprehensive configuration for Cloudflare bypass
     * 
     * @param customUserAgent Optional custom User-Agent string (null for default)
     */
    public void configureForCloudflareBypass(String customUserAgent) {
        configureJavaScript();
        configureStorage();
        configureCaching();
        configureUserAgent(customUserAgent);
        configureMiscellaneousSettings();
        configurePerformanceOptimizations();
    }
    
    /**
     * Configure JavaScript settings
     * JavaScript is REQUIRED for Cloudflare challenges to work
     */
    private void configureJavaScript() {
        // Enable JavaScript execution (CRITICAL for Cloudflare)
        settings.setJavaScriptEnabled(true);
        
        // Allow JavaScript to open windows automatically
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        // Enable support for multiple windows
        settings.setSupportMultipleWindows(false); // Keep false for security
    }
    
    /**
     * Configure storage APIs
     * DOM storage is REQUIRED for Cloudflare challenges
     */
    private void configureStorage() {
        // Enable DOM Storage API (CRITICAL for Cloudflare)
        settings.setDomStorageEnabled(true);
        
        // Enable database storage
        settings.setDatabaseEnabled(true);
        
        // Enable application cache (deprecated but still useful for old devices)
//        settings.setAppCacheEnabled(true);
        
        // Set cache path for app cache
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
////            settings.setAppCachePath(webView.getContext().getCacheDir().getAbsolutePath());
////            settings.setAppCacheMaxSize(10 * 1024 * 1024); // 10MB
//        }
    }
    
    /**
     * Configure caching behavior
     */
    private void configureCaching() {
        // Use cache with network validation
        // This balances performance with freshness
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Allow loading from any source (network or file)
        settings.setAllowFileAccess(false); // Security: disable file access
        settings.setAllowContentAccess(true);
    }
    
    /**
     * Configure User-Agent string
     * 
     * @param customUserAgent Optional custom User-Agent (null for default)
     */
    private void configureUserAgent(String customUserAgent) {
        if (customUserAgent != null && !customUserAgent.isEmpty()) {
            settings.setUserAgentString(customUserAgent);
        } else {
            // For Android 7 and older devices, use a modern User-Agent
            // to avoid "outdated browser" warnings from Cloudflare
            settings.setUserAgentString(DEFAULT_MODERN_USER_AGENT);
        }
    }
    
    /**
     * Configure miscellaneous settings for compatibility and security
     */
    private void configureMiscellaneousSettings() {
        // Allow mixed content (HTTP and HTTPS)
        // Some Cloudflare-protected sites may have mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // Enable zoom controls (useful for Android TV)
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        
        // Configure viewport and layout
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        
        // Enable media playback
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        // Enable geolocation if needed
        settings.setGeolocationEnabled(false); // Disable for privacy
        
        // Configure safe browsing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false); // Disable to avoid interference
        }
    }
    
    /**
     * Configure performance optimizations for old Android TV devices
     */
    private void configurePerformanceOptimizations() {
        // Disable image loading initially for faster page load
        // Images can be enabled after Cloudflare bypass if needed
        settings.setBlockNetworkImage(false); // Keep enabled for full functionality
        
        // Enable hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        }
        
        // Optimize rendering
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        
        // Disable unnecessary features for performance
        settings.setNeedInitialFocus(false);
        settings.setSaveFormData(false);
        settings.setSavePassword(false);
        
        // Enable automatic loading
        settings.setLoadsImagesAutomatically(true);
    }
    
    /**
     * Get the configured User-Agent string
     * 
     * @return Current User-Agent string
     */
    public String getUserAgent() {
        return settings.getUserAgentString();
    }
    
    /**
     * Update User-Agent at runtime (use with caution)
     * Note: Changing User-Agent after Cloudflare challenge starts will cause failure
     * 
     * @param userAgent New User-Agent string
     */
    public void updateUserAgent(String userAgent) {
        settings.setUserAgentString(userAgent);
    }
    
    /**
     * Enable remote debugging (ONLY for development, never in production)
     * 
     * @param enabled Whether to enable debugging
     */
    public void setDebuggingEnabled(boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(enabled);
        }
    }
}
