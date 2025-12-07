package com.omarflex5.temp.omerflex.service.cloudflare;

import java.util.Arrays;
import java.util.List;

/**
 * Detects Cloudflare-related content, resources, and challenges.
 * 
 * This class provides utilities to identify Cloudflare protection
 * mechanisms including challenge pages, resource URLs, and cookies.
 * 
 * @author Your Name
 * @version 1.0
 */
public class CloudflareDetector {
    
    private static final String TAG = "CloudflareDetector";
    
    /**
     * Cloudflare-related domains and resources
     */
    private static final List<String> CLOUDFLARE_DOMAINS = Arrays.asList(
        "cloudflare.com",
        "challenges.cloudflare.com",
        "cloudflareinsights.com",
        "cf-assets.com"
    );
    
    /**
     * Cloudflare challenge page indicators in title
     */
    private static final List<String> CHALLENGE_TITLE_INDICATORS = Arrays.asList(
        "just a moment",
        "checking your browser",
        "please wait",
        "attention required"
    );
    
    /**
     * Cloudflare challenge page indicators in body content
     */
    private static final List<String> CHALLENGE_BODY_INDICATORS = Arrays.asList(
        "cloudflare",
        "ray id",
        "enable javascript and cookies",
        "ddos protection",
        "checking if the site connection is secure"
    );
    
    /**
     * Cloudflare cookie names
     */
    public static final String CF_CLEARANCE_COOKIE = "cf_clearance";
    public static final String CF_BM_COOKIE = "__cf_bm";
    public static final String CF_CFDUID_COOKIE = "__cfduid";
    
    /**
     * Check if a URL is a Cloudflare resource
     * 
     * @param url The URL to check
     * @return true if the URL is a Cloudflare resource
     */
    public boolean isCloudflareResource(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        
        for (String domain : CLOUDFLARE_DOMAINS) {
            if (lowerUrl.contains(domain)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if page title indicates Cloudflare challenge
     * 
     * @param title The page title
     * @return true if title indicates a challenge
     */
    public boolean isChallengeTitle(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }
        
        String lowerTitle = title.toLowerCase();
        
        for (String indicator : CHALLENGE_TITLE_INDICATORS) {
            if (lowerTitle.contains(indicator)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if page body content indicates Cloudflare challenge
     * 
     * @param bodyText The page body text
     * @return true if body indicates a challenge
     */
    public boolean isChallengeBody(String bodyText) {
        if (bodyText == null || bodyText.isEmpty()) {
            return false;
        }
        
        String lowerBody = bodyText.toLowerCase();
        int indicatorCount = 0;
        
        for (String indicator : CHALLENGE_BODY_INDICATORS) {
            if (lowerBody.contains(indicator)) {
                indicatorCount++;
            }
        }
        
        // Require at least 2 indicators to reduce false positives
        return indicatorCount >= 2;
    }
    
    /**
     * Check if a cookie is a Cloudflare cookie
     * 
     * @param cookieName The cookie name
     * @return true if it's a Cloudflare cookie
     */
    public boolean isCloudflareCookie(String cookieName) {
        if (cookieName == null || cookieName.isEmpty()) {
            return false;
        }
        
        return cookieName.equals(CF_CLEARANCE_COOKIE) ||
               cookieName.equals(CF_BM_COOKIE) ||
               cookieName.equals(CF_CFDUID_COOKIE) ||
               cookieName.startsWith("__cf");
    }
    
    /**
     * Check if a cookie string contains the clearance cookie
     * 
     * @param cookieString The cookie string
     * @return true if clearance cookie is present
     */
    public boolean hasClearanceCookie(String cookieString) {
        if (cookieString == null || cookieString.isEmpty()) {
            return false;
        }
        
        return cookieString.contains(CF_CLEARANCE_COOKIE + "=");
    }
    
    /**
     * Extract clearance cookie value from cookie string
     * 
     * @param cookieString The cookie string
     * @return The clearance cookie value or null if not found
     */
    public String extractClearanceCookie(String cookieString) {
        if (cookieString == null || cookieString.isEmpty()) {
            return null;
        }
        
        String[] cookies = cookieString.split(";");
        for (String cookie : cookies) {
            cookie = cookie.trim();
            if (cookie.startsWith(CF_CLEARANCE_COOKIE + "=")) {
                return cookie.substring((CF_CLEARANCE_COOKIE + "=").length());
            }
        }
        
        return null;
    }
    
    /**
     * Check if a URL requires Cloudflare bypass based on HTTP response
     * This can be expanded to check response headers, status codes, etc.
     * 
     * @param statusCode HTTP status code
     * @param contentType Response content type
     * @return true if bypass might be needed
     */
    public boolean mightRequireBypass(int statusCode, String contentType) {
        // Cloudflare challenges typically return 403 or 503
        if (statusCode == 403 || statusCode == 503) {
            return true;
        }
        
        // Challenge pages are HTML
        if (contentType != null && contentType.toLowerCase().contains("text/html")) {
            return true;
        }
        
        return false;
    }
}
