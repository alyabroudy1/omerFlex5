package com.omarflex5.temp.omerflex.service.cloudflare;

import android.os.Build;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;

/**
 * Helper class for managing cookies, especially Cloudflare clearance cookies.
 * 
 * This class provides utilities for enabling cookies, extracting specific cookies,
 * and managing cookie persistence for Cloudflare bypass.
 * 
 * @author Your Name
 * @version 1.0
 */
public class CookieManagerHelper {
    
    private static final String TAG = "CookieManagerHelper";
    
    private final CookieManager cookieManager;
    private final CloudflareDetector cloudflareDetector;
    
    /**
     * Constructor
     */
    public CookieManagerHelper() {
        this.cookieManager = CookieManager.getInstance();
        this.cloudflareDetector = new CloudflareDetector();
    }
    
    /**
     * Enable cookies for the WebView
     * This is CRITICAL for Cloudflare bypass
     * 
     * @param webView The WebView instance
     */
    public void enableCookies(WebView webView) {
        cookieManager.setAcceptCookie(true);
        
        // Enable third-party cookies (required for some Cloudflare scenarios)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
        
        Log.d(TAG, "Cookies enabled for WebView");
    }
    
    /**
     * Get all cookies for a specific URL
     * 
     * @param url The URL to get cookies for
     * @return Cookie string or null if no cookies
     */
    public String getAllCookies(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        String cookies = cookieManager.getCookie(url);
        
        if (cookies != null && !cookies.isEmpty()) {
            Log.d(TAG, "Retrieved cookies for " + url + ": " + 
                  (cookies.length() > 50 ? cookies.substring(0, 50) + "..." : cookies));
        }
        
        return cookies;
    }
    
    /**
     * Get the Cloudflare clearance cookie for a specific URL
     * This is the most important cookie for maintaining bypass state
     * 
     * @param url The URL to get the clearance cookie for
     * @return The clearance cookie value or null if not found
     */
    public String getCloudflareClearanceCookie(String url) {
        String allCookies = getAllCookies(url);
        
        if (allCookies == null || allCookies.isEmpty()) {
            Log.d(TAG, "No cookies found for URL: " + url);
            return null;
        }
        
        String clearanceCookie = cloudflareDetector.extractClearanceCookie(allCookies);
        
        if (clearanceCookie != null) {
            Log.d(TAG, "Clearance cookie extracted: " + clearanceCookie.substring(0, 
                  Math.min(20, clearanceCookie.length())) + "...");
        } else {
            Log.d(TAG, "No clearance cookie found");
        }
        
        return clearanceCookie;
    }
    
    /**
     * Check if the clearance cookie exists for a URL
     * 
     * @param url The URL to check
     * @return true if clearance cookie exists
     */
    public boolean hasClearanceCookie(String url) {
        return getCloudflareClearanceCookie(url) != null;
    }
    
    /**
     * Set a specific cookie for a URL
     * Useful for restoring previously saved clearance cookies
     * 
     * @param url The URL to set the cookie for
     * @param cookieString The cookie string (e.g., "cf_clearance=value; Max-Age=...")
     */
    public void setCookie(String url, String cookieString) {
        if (url == null || url.isEmpty() || cookieString == null || cookieString.isEmpty()) {
            Log.e(TAG, "Cannot set cookie: invalid parameters");
            return;
        }
        
        cookieManager.setCookie(url, cookieString);
        
        // Flush cookies to persistent storage immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
        }
        
        Log.d(TAG, "Cookie set for " + url);
    }
    
    /**
     * Remove all cookies for a specific URL
     * 
     * @param url The URL to remove cookies for
     */
    public void removeCookies(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeSessionCookies(null);
        } else {
            cookieManager.removeSessionCookie();
        }
        
        Log.d(TAG, "Cookies removed for " + url);
    }
    
    /**
     * Remove all cookies globally
     */
    public void removeAllCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } else {
            cookieManager.removeAllCookie();
        }
        
        Log.d(TAG, "All cookies removed");
    }
    
    /**
     * Save clearance cookie to persistent storage
     * This allows reusing the cookie across app sessions
     * 
     * @param url The URL the cookie is for
     * @param clearanceCookie The clearance cookie value
     * @return Cookie string formatted for storage
     */
    public String formatClearanceCookieForStorage(String url, String clearanceCookie) {
        if (clearanceCookie == null || clearanceCookie.isEmpty()) {
            return null;
        }
        
        // Format: cf_clearance=value; Domain=.example.com; Path=/; Max-Age=31536000
        try {
            java.net.URL netUrl = new java.net.URL(url);
            String domain = netUrl.getHost();
            
            return String.format(
                "%s=%s; Domain=.%s; Path=/; Max-Age=31536000; Secure; HttpOnly",
                CloudflareDetector.CF_CLEARANCE_COOKIE,
                clearanceCookie,
                domain
            );
        } catch (Exception e) {
            Log.e(TAG, "Error formatting clearance cookie", e);
            return CloudflareDetector.CF_CLEARANCE_COOKIE + "=" + clearanceCookie;
        }
    }
    
    /**
     * Restore a previously saved clearance cookie
     * 
     * @param url The URL to restore the cookie for
     * @param clearanceCookieValue The clearance cookie value
     * @return true if cookie was restored successfully
     */
    public boolean restoreClearanceCookie(String url, String clearanceCookieValue) {
        if (url == null || clearanceCookieValue == null) {
            return false;
        }
        
        String cookieString = formatClearanceCookieForStorage(url, clearanceCookieValue);
        setCookie(url, cookieString);
        
        // Verify it was set
        boolean success = hasClearanceCookie(url);
        
        if (success) {
            Log.d(TAG, "Clearance cookie restored successfully");
        } else {
            Log.e(TAG, "Failed to restore clearance cookie");
        }
        
        return success;
    }
    
    /**
     * Get all Cloudflare-related cookies for a URL
     * 
     * @param url The URL to get cookies for
     * @return String containing all Cloudflare cookies
     */
    public String getAllCloudflareCookies(String url) {
        String allCookies = getAllCookies(url);
        
        if (allCookies == null || allCookies.isEmpty()) {
            return null;
        }
        
        StringBuilder cfCookies = new StringBuilder();
        String[] cookies = allCookies.split(";");
        
        for (String cookie : cookies) {
            cookie = cookie.trim();
            String cookieName = cookie.split("=")[0];
            
            if (cloudflareDetector.isCloudflareCookie(cookieName)) {
                if (cfCookies.length() > 0) {
                    cfCookies.append("; ");
                }
                cfCookies.append(cookie);
            }
        }
        
        return cfCookies.length() > 0 ? cfCookies.toString() : null;
    }
    
    /**
     * Flush cookies to persistent storage
     * Important to call this before app exits to ensure cookies are saved
     */
    public void flush() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.flush();
            Log.d(TAG, "Cookies flushed to storage");
        }
    }
}
