package com.omarflex5.temp.omerflex.service.cloudflare;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Custom WebViewClient for handling Cloudflare challenges and page navigation.
 * 
 * This client monitors page loading, detects Cloudflare challenges, tracks
 * when challenges are passed, handles redirects with user confirmation,
 * and manages errors gracefully.
 * 
 * @author Your Name
 * @version 1.0
 */
public class CloudflareWebViewClient extends WebViewClient {
    
    private static final String TAG = "CloudflareWebViewClient";
    
    private final String initialDomain;
    private final CloudflareDetector cloudflareDetector;
    private final Callbacks callbacks;
    
    private boolean isPageLoading = false;
    private boolean cloudflareDetected = false;
    private boolean cloudflarePassed = false;
    private int redirectCount = 0;
    private static final int MAX_REDIRECT_COUNT = 10;
    
    /**
     * Callback interface for communication with parent activity
     */
    public interface Callbacks {
        void onCloudflareDetected();
        void onCloudflarePassed();
        void onPageFullyLoaded();
        void onRedirectRequested(String url, RedirectCallback callback);
        void onError(String message);
    }
    
    /**
     * Callback interface for redirect decisions
     */
    public interface RedirectCallback {
        void allow();
        void deny();
    }
    
    /**
     * Constructor
     * 
     * @param initialDomain The domain of the initial URL
     * @param cloudflareDetector Detector for Cloudflare challenges
     * @param callbacks Callbacks for communication with parent
     */
    public CloudflareWebViewClient(String initialDomain, 
                                   CloudflareDetector cloudflareDetector,
                                   Callbacks callbacks) {
        this.initialDomain = initialDomain;
        this.cloudflareDetector = cloudflareDetector;
        this.callbacks = callbacks;
    }
    
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        isPageLoading = true;
        
        Log.d(TAG, "Page started loading: " + url);
        
        // Check if this is a redirect to a different domain
        checkForDomainRedirect(url);
    }
    
    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        isPageLoading = false;
        
        Log.d(TAG, "Page finished loading: " + url);
        
        // Check for Cloudflare challenge in the loaded page
        checkForCloudflareChallenge(view);
    }
    
    @Override
    public void onLoadResource(WebView view, String url) {
        super.onLoadResource(view, url);
        
        // Monitor for Cloudflare-related resources
        if (cloudflareDetector.isCloudflareResource(url)) {
            if (!cloudflareDetected) {
                cloudflareDetected = true;
                callbacks.onCloudflareDetected();
            }
        }
    }
    
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        Log.d(TAG, "URL loading requested: " + url);
        
        // Check for too many redirects (possible loop)
        redirectCount++;
        if (redirectCount > MAX_REDIRECT_COUNT) {
            callbacks.onError("Too many redirects detected");
            return true;
        }
        
        // Check if redirect is to a different domain
        try {
            String newDomain = extractDomain(url);
            if (!newDomain.equals(initialDomain)) {
                // Different domain - request user confirmation
                handleDomainRedirect(view, url);
                return true; // Block until user responds
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking domain redirect", e);
        }
        
        // Same domain, allow
        return false;
    }
    
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Log.e(TAG, "Page error: " + error.getDescription());
            
            // Only report main frame errors
            if (request.isForMainFrame()) {
                callbacks.onError("Page load error: " + error.getDescription());
            }
        }
    }
    
    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Log.e(TAG, "SSL Error: " + error.toString());
        
        // For old Android devices, SSL errors with Cloudflare may occur
        // In production, you should validate the certificate properly
        // For now, we'll proceed to allow bypass (USE WITH CAUTION)
        
        // SECURITY WARNING: This accepts all SSL certificates
        // Only use this for testing or if you understand the risks
        handler.proceed(); // Proceed despite SSL error
        
        // In production, use:
        // handler.cancel();
        // callbacks.onError("SSL Certificate validation failed");
    }
    
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // This method can be used for advanced request interception
        // Currently just logging for debugging
        String url = request.getUrl().toString();
        
        // Log Cloudflare-related requests
        if (cloudflareDetector.isCloudflareResource(url)) {
            Log.d(TAG, "Intercepted Cloudflare resource: " + url);
        }
        
        return super.shouldInterceptRequest(view, request);
    }
    
    /**
     * Check if the loaded page contains a Cloudflare challenge
     */
    private void checkForCloudflareChallenge(WebView view) {
        // Inject JavaScript to detect Cloudflare challenge
        view.evaluateJavascript(
            "(function() {" +
            "  var title = document.title.toLowerCase();" +
            "  var body = document.body ? document.body.innerText.toLowerCase() : '';" +
            "  " +
            "  // Check for Cloudflare challenge indicators" +
            "  var isChallenge = title.includes('just a moment') || " +
            "                    title.includes('checking your browser') || " +
            "                    body.includes('cloudflare') && body.includes('checking');" +
            "  " +
            "  // Check if challenge has passed (clearance cookie present)" +
            "  var hasClearance = document.cookie.includes('cf_clearance');" +
            "  " +
            "  return JSON.stringify({" +
            "    isChallenge: isChallenge," +
            "    hasClearance: hasClearance," +
            "    title: document.title" +
            "  });" +
            "})();",
            result -> {
                if (result != null && !result.equals("null")) {
                    try {
                        // Parse the JSON result
                        result = result.substring(1, result.length() - 1); // Remove quotes
                        result = result.replace("\\\"", "\""); // Unescape
                        
                        Log.d(TAG, "Challenge check result: " + result);
                        
                        if (result.contains("\"isChallenge\":true")) {
                            if (!cloudflareDetected) {
                                cloudflareDetected = true;
                                callbacks.onCloudflareDetected();
                            }
                        }
                        
                        if (result.contains("\"hasClearance\":true")) {
                            if (!cloudflarePassed && cloudflareDetected) {
                                cloudflarePassed = true;
                                callbacks.onCloudflarePassed();
                            }
                            
                            // Page is ready for content extraction
                            callbacks.onPageFullyLoaded();
                        } else if (!cloudflareDetected) {
                            // No challenge detected, page is ready
                            callbacks.onPageFullyLoaded();
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing challenge check result", e);
                    }
                }
            }
        );
    }
    
    /**
     * Check if URL is redirecting to a different domain
     */
    private void checkForDomainRedirect(String url) {
        try {
            String domain = extractDomain(url);
            if (!domain.equals(initialDomain)) {
                Log.d(TAG, "Redirect detected: " + initialDomain + " -> " + domain);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking domain", e);
        }
    }
    
    /**
     * Handle redirect to a different domain
     */
    private void handleDomainRedirect(WebView view, String url) {
        callbacks.onRedirectRequested(url, new RedirectCallback() {
            @Override
            public void allow() {
                Log.d(TAG, "Redirect allowed: " + url);
                view.loadUrl(url);
            }
            
            @Override
            public void deny() {
                Log.d(TAG, "Redirect denied: " + url);
                // Stop loading and stay on current page
                view.stopLoading();
            }
        });
    }
    
    /**
     * Extract domain from URL
     */
    private String extractDomain(String url) {
        try {
            java.net.URL netUrl = new java.net.URL(url);
            return netUrl.getHost();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
    }
}
