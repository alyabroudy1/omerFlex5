package com.omarflex5.temp.omerflex.service.cloudflare;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.omerflex.service.cloudflare.CloudflareBypassActivity;

/**
 * Example usage of CloudflareBypassActivity
 * 
 * Demonstrates both HTML extraction and video detection modes.
 * 
 * @author Your Name
 * @version 1.0
 */
public class UsageExample extends AppCompatActivity {
    
    private static final String TAG = "UsageExample";
    private static final int REQUEST_HTML_EXTRACTION = 1001;
    private static final int REQUEST_VIDEO_DETECTION = 1002;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Example 1: HTML Extraction
        extractHtmlFromCloudflareProtectedSite();
        
        // Example 2: Video Detection
        // detectVideoFromCloudflareProtectedSite();
    }
    
    /**
     * Example 1: Extract HTML content from a Cloudflare-protected site
     */
    private void extractHtmlFromCloudflareProtectedSite() {
        String url = "https://cima4u.info/";
        
        Intent intent = new Intent(this, CloudflareBypassActivity.class);
        intent.putExtra(CloudflareBypassActivity.EXTRA_URL, url);
        intent.putExtra(CloudflareBypassActivity.EXTRA_MODE, 
                       CloudflareBypassActivity.MODE_HTML_EXTRACTION);
        
        // Optional: Custom User-Agent
        // intent.putExtra(CloudflareBypassActivity.EXTRA_USER_AGENT, 
        //                "Your-Custom-User-Agent");
        
        // Optional: Custom timeout (default is 30 seconds)
        intent.putExtra(CloudflareBypassActivity.EXTRA_TIMEOUT_SECONDS, 60);
        
        startActivityForResult(intent, REQUEST_HTML_EXTRACTION);
    }
    
    /**
     * Example 2: Detect video URL from a Cloudflare-protected site
     */
    private void detectVideoFromCloudflareProtectedSite() {
        String url = "https://example.com/video-page";
        
        Intent intent = new Intent(this, CloudflareBypassActivity.class);
        intent.putExtra(CloudflareBypassActivity.EXTRA_URL, url);
        intent.putExtra(CloudflareBypassActivity.EXTRA_MODE, 
                       CloudflareBypassActivity.MODE_VIDEO_DETECTION);
        intent.putExtra(CloudflareBypassActivity.EXTRA_TIMEOUT_SECONDS, 45);
        
        startActivityForResult(intent, REQUEST_VIDEO_DETECTION);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_HTML_EXTRACTION) {
            handleHtmlExtractionResult(resultCode, data);
        } else if (requestCode == REQUEST_VIDEO_DETECTION) {
            handleVideoDetectionResult(resultCode, data);
        }
    }
    
    /**
     * Handle HTML extraction result
     */
    private void handleHtmlExtractionResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            String htmlContent = data.getStringExtra(
                CloudflareBypassActivity.RESULT_HTML_CONTENT);
            String clearanceCookie = data.getStringExtra(
                CloudflareBypassActivity.RESULT_CLEARANCE_COOKIE);
            String allCookies = data.getStringExtra(
                CloudflareBypassActivity.RESULT_ALL_COOKIES);
            
            Log.d(TAG, "HTML Content Length: " + 
                  (htmlContent != null ? htmlContent.length() : 0));
            Log.d(TAG, "Clearance Cookie: " + clearanceCookie);
            Log.d(TAG, "All Cookies: " + allCookies);
            
            // Process the HTML content
            processHtmlContent(htmlContent);
            
            // Save the clearance cookie for future use
            saveClearanceCookie(clearanceCookie);
            
            Toast.makeText(this, "HTML extracted successfully!", 
                          Toast.LENGTH_SHORT).show();
            
        } else if (resultCode == RESULT_CANCELED) {
            String errorMessage = data != null ? 
                data.getStringExtra(CloudflareBypassActivity.RESULT_ERROR_MESSAGE) : 
                "Unknown error";
            
            Log.e(TAG, "HTML Extraction failed: " + errorMessage);
            Toast.makeText(this, "Error: " + errorMessage, 
                          Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Handle video detection result
     */
    private void handleVideoDetectionResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            String videoUrl = data.getStringExtra(
                CloudflareBypassActivity.RESULT_VIDEO_URL);
            String clearanceCookie = data.getStringExtra(
                CloudflareBypassActivity.RESULT_CLEARANCE_COOKIE);
            String allCookies = data.getStringExtra(
                CloudflareBypassActivity.RESULT_ALL_COOKIES);
            
            Log.d(TAG, "Video URL: " + videoUrl);
            Log.d(TAG, "Clearance Cookie: " + clearanceCookie);
            
            // Process the video URL
            processVideoUrl(videoUrl);
            
            // Save the clearance cookie for future use
            saveClearanceCookie(clearanceCookie);
            
            Toast.makeText(this, "Video URL detected: " + videoUrl, 
                          Toast.LENGTH_LONG).show();
            
        } else if (resultCode == RESULT_CANCELED) {
            String errorMessage = data != null ? 
                data.getStringExtra(CloudflareBypassActivity.RESULT_ERROR_MESSAGE) : 
                "Unknown error";
            
            Log.e(TAG, "Video Detection failed: " + errorMessage);
            Toast.makeText(this, "Error: " + errorMessage, 
                          Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Process extracted HTML content
     * Implement your logic here
     */
    private void processHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return;
        }
        
        // Example: Parse HTML to extract specific data
        // Example: Save to database
        // Example: Display in UI
        
        Log.d(TAG, "Processing HTML content: " + 
              htmlContent.substring(0, Math.min(100, htmlContent.length())) + "...");
    }
    
    /**
     * Process detected video URL
     * Implement your logic here
     */
    private void processVideoUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return;
        }
        
        // Example: Play video using video player
        // Example: Download video
        // Example: Extract more video info
        
        Log.d(TAG, "Processing video URL: " + videoUrl);
    }
    
    /**
     * Save clearance cookie for future requests
     * This allows you to bypass Cloudflare without WebView in subsequent requests
     */
    private void saveClearanceCookie(String clearanceCookie) {
        if (clearanceCookie == null || clearanceCookie.isEmpty()) {
            return;
        }
        
        // Example: Save to SharedPreferences
        getSharedPreferences("cloudflare", MODE_PRIVATE)
            .edit()
            .putString("clearance_cookie", clearanceCookie)
            .apply();
        
        Log.d(TAG, "Clearance cookie saved");
        
        // You can now use this cookie in HTTP requests with OkHttp, Retrofit, etc.
        // Example with OkHttp:
        // Request request = new Request.Builder()
        //     .url(url)
        //     .addHeader("Cookie", "cf_clearance=" + clearanceCookie)
        //     .build();
    }
    
    /**
     * Retrieve saved clearance cookie
     * 
     * @return Saved clearance cookie or null
     */
    private String getSavedClearanceCookie() {
        return getSharedPreferences("cloudflare", MODE_PRIVATE)
            .getString("clearance_cookie", null);
    }
}
