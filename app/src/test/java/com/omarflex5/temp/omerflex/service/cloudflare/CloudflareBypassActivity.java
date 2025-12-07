package com.omarflex5.temp.omerflex.service.cloudflare;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.omerflex.R;

/**
 * Activity for handling Cloudflare-protected content extraction.
 * 
 * Supports two modes:
 * 1. HTML_EXTRACTION: Extract page HTML after bypassing Cloudflare
 * 2. VIDEO_DETECTION: Detect video URLs from the loaded page
 * 
 * Usage:
 * Intent intent = new Intent(context, CloudflareBypassActivity.class);
 * intent.putExtra(EXTRA_URL, "https://example.com");
 * intent.putExtra(EXTRA_MODE, MODE_HTML_EXTRACTION);
 * startActivityForResult(intent, REQUEST_CODE);
 * 
 * Results:
 * - RESULT_OK: Success with data in extras
 * - RESULT_CANCELED: User cancelled or error occurred
 * 
 * @author Your Name
 * @version 1.0
 */
public class CloudflareBypassActivity extends AppCompatActivity {
    
    private static final String TAG = "CloudflareBypass";
    
    // Intent extras
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_USER_AGENT = "extra_user_agent";
    public static final String EXTRA_TIMEOUT_SECONDS = "extra_timeout_seconds";
    
    // Operation modes
    public static final int MODE_HTML_EXTRACTION = 1;
    public static final int MODE_VIDEO_DETECTION = 2;
    
    // Result extras
    public static final String RESULT_HTML_CONTENT = "result_html_content";
    public static final String RESULT_VIDEO_URL = "result_video_url";
    public static final String RESULT_CLEARANCE_COOKIE = "result_clearance_cookie";
    public static final String RESULT_ALL_COOKIES = "result_all_cookies";
    public static final String RESULT_ERROR_MESSAGE = "result_error_message";
    
    // Default timeout for operations (30 seconds)
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    // Redirection confirmation timeout (8 seconds)
    private static final int REDIRECT_CONFIRMATION_TIMEOUT = 8000;
    
    // UI Components
    private WebView webView;
    private ProgressBar progressBar;
    private TextView statusText;
    
    // Configuration
    private String targetUrl;
    private int operationMode;
    private String customUserAgent;
    private int timeoutSeconds;
    
    // State management
    private String initialDomain;
    private boolean cloudflareDetected = false;
    private boolean cloudflarePassed = false;
    private boolean operationCompleted = false;
    
    // Handlers
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    
    // Managers
    private CloudflareDetector cloudflareDetector;
    private CookieManagerHelper cookieManagerHelper;
    private VideoUrlDetector videoUrlDetector;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.omerflex.R.layout.activity_cloudflare_bypass);
        
        initializeComponents();
        parseIntent();
        setupWebView();
        startOperation();
    }
    
    /**
     * Initialize UI components and managers
     */
    private void initializeComponents() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        
        cookieManagerHelper = new CookieManagerHelper();
        cloudflareDetector = new CloudflareDetector();
        
        updateStatus("Initializing...");
    }
    
    /**
     * Parse intent extras and validate input
     */
    private void parseIntent() {
        Intent intent = getIntent();
        
        targetUrl = intent.getStringExtra(EXTRA_URL);
        operationMode = intent.getIntExtra(EXTRA_MODE, MODE_HTML_EXTRACTION);
        customUserAgent = intent.getStringExtra(EXTRA_USER_AGENT);
        timeoutSeconds = intent.getIntExtra(EXTRA_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
        
        if (targetUrl == null || targetUrl.isEmpty()) {
            finishWithError("No URL provided");
            return;
        }
        
        try {
            initialDomain = extractDomain(targetUrl);
        } catch (Exception e) {
            finishWithError("Invalid URL: " + e.getMessage());
        }
    }
    
    /**
     * Configure WebView with optimal settings for Cloudflare bypass
     * and old Android TV compatibility
     */
    private void setupWebView() {
        WebViewConfigurator configurator = new WebViewConfigurator(webView);
        
        // Apply optimal settings for old Android devices
        configurator.configureForCloudflareBypass(customUserAgent);
        
        // Set custom WebViewClient for monitoring and control
        CloudflareWebViewClient webViewClient = new CloudflareWebViewClient(
            initialDomain,
            cloudflareDetector,
            new CloudflareWebViewClient.Callbacks() {
                @Override
                public void onCloudflareDetected() {
                    handleCloudflareDetected();
                }
                
                @Override
                public void onCloudflarePassed() {
                    handleCloudflarePassed();
                }
                
                @Override
                public void onPageFullyLoaded() {
                    handlePageFullyLoaded();
                }
                
                @Override
                public void onRedirectRequested(String url, CloudflareWebViewClient.RedirectCallback callback) {
                    handleRedirectRequest(url, callback);
                }
                
                @Override
                public void onError(String message) {
                    handleError(message);
                }
            }
        );
        webView.setWebViewClient(webViewClient);
        
        // Set up video detection if needed
        if (operationMode == MODE_VIDEO_DETECTION) {
            videoUrlDetector = new VideoUrlDetector(new VideoUrlDetector.VideoDetectionCallback() {
                @Override
                public void onVideoUrlDetected(String videoUrl) {
                    handleVideoUrlDetected(videoUrl);
                }
            });
            
            CloudflareWebChromeClient chromeClient = new CloudflareWebChromeClient(videoUrlDetector);
            webView.setWebChromeClient(chromeClient);
        }
    }
    
    /**
     * Start the bypass operation with timeout protection
     */
    private void startOperation() {
        updateStatus("Loading page...");
        
        // Set up timeout
        timeoutRunnable = () -> {
            if (!operationCompleted) {
                finishWithError("Operation timed out after " + timeoutSeconds + " seconds");
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, timeoutSeconds * 1000);
        
        // Enable cookie management
        cookieManagerHelper.enableCookies(webView);
        
        // Load the target URL
        webView.loadUrl(targetUrl);
    }
    
    /**
     * Handle Cloudflare detection
     */
    private void handleCloudflareDetected() {
        if (!cloudflareDetected) {
            cloudflareDetected = true;
            updateStatus("Cloudflare detected, bypassing security check...");
            Log.d(TAG, "Cloudflare challenge detected on: " + targetUrl);
        }
    }
    
    /**
     * Handle successful Cloudflare bypass
     */
    private void handleCloudflarePassed() {
        if (!cloudflarePassed) {
            cloudflarePassed = true;
            updateStatus("Cloudflare bypassed successfully");
            Log.d(TAG, "Cloudflare challenge passed");
            
            // Extract clearance cookie
            String clearanceCookie = cookieManagerHelper.getCloudflareClearanceCookie(targetUrl);
            if (clearanceCookie != null) {
                Log.d(TAG, "Clearance cookie extracted: " + clearanceCookie.substring(0, 20) + "...");
            }
        }
    }
    
    /**
     * Handle page fully loaded event
     */
    private void handlePageFullyLoaded() {
        if (operationCompleted) return;
        
        updateStatus("Page loaded, processing...");
        
        // Give a small delay to ensure all resources are loaded
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (operationMode == MODE_HTML_EXTRACTION) {
                extractHtmlContent();
            } else if (operationMode == MODE_VIDEO_DETECTION) {
                // Video detection runs continuously, wait for detection
                updateStatus("Monitoring for video URLs...");
            }
        }, 1000);
    }
    
    /**
     * Handle redirection request with user confirmation
     */
    private void handleRedirectRequest(String url, CloudflareWebViewClient.RedirectCallback callback) {
        try {
            String redirectDomain = extractDomain(url);
            
            if (redirectDomain.equals(initialDomain)) {
                // Same domain, allow automatically
                callback.allow();
                return;
            }
            
            // Different domain, ask user
            runOnUiThread(() -> showRedirectConfirmationDialog(url, callback));
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling redirect", e);
            callback.deny();
        }
    }
    
    /**
     * Show redirect confirmation dialog with auto-timeout
     */
    private void showRedirectConfirmationDialog(String url, CloudflareWebViewClient.RedirectCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Redirect Requested");
        builder.setMessage("The page wants to redirect to:\n\n" + url + "\n\nAllow this redirect?");
        builder.setCancelable(false);
        
        final boolean[] responded = {false};
        
        builder.setPositiveButton("Allow", (dialog, which) -> {
            if (!responded[0]) {
                responded[0] = true;
                callback.allow();
            }
        });
        
        builder.setNegativeButton("Deny", (dialog, which) -> {
            if (!responded[0]) {
                responded[0] = true;
                callback.deny();
                finishWithError("Redirect denied by user");
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Auto-deny after 8 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!responded[0] && dialog.isShowing()) {
                responded[0] = true;
                dialog.dismiss();
                callback.deny();
                finishWithError("Redirect confirmation timed out");
            }
        }, REDIRECT_CONFIRMATION_TIMEOUT);
    }
    
    /**
     * Extract HTML content from the loaded page
     */
    private void extractHtmlContent() {
        updateStatus("Extracting HTML content...");
        
        webView.evaluateJavascript(
            "(function() { return document.documentElement.outerHTML; })();",
            html -> {
                if (html != null && !html.equals("null")) {
                    // Remove quotes from JavaScript string
                    html = html.substring(1, html.length() - 1);
                    // Unescape JavaScript string
                    html = unescapeJavaScriptString(html);
                    
                    finishWithHtmlContent(html);
                } else {
                    finishWithError("Failed to extract HTML content");
                }
            }
        );
    }
    
    /**
     * Handle video URL detection
     */
    private void handleVideoUrlDetected(String videoUrl) {
        if (operationCompleted) return;
        
        Log.d(TAG, "Video URL detected: " + videoUrl);
        updateStatus("Video URL detected!");
        
        finishWithVideoUrl(videoUrl);
    }
    
    /**
     * Handle errors gracefully
     */
    private void handleError(String message) {
        Log.e(TAG, "Error occurred: " + message);
        finishWithError(message);
    }
    
    /**
     * Finish activity with HTML content result
     */
    private void finishWithHtmlContent(String htmlContent) {
        if (operationCompleted) return;
        operationCompleted = true;
        
        timeoutHandler.removeCallbacks(timeoutRunnable);
        
        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_HTML_CONTENT, htmlContent);
        resultIntent.putExtra(RESULT_CLEARANCE_COOKIE, 
            cookieManagerHelper.getCloudflareClearanceCookie(targetUrl));
        resultIntent.putExtra(RESULT_ALL_COOKIES, 
            cookieManagerHelper.getAllCookies(targetUrl));
        
        setResult(RESULT_OK, resultIntent);
        finish();
    }
    
    /**
     * Finish activity with video URL result
     */
    private void finishWithVideoUrl(String videoUrl) {
        if (operationCompleted) return;
        operationCompleted = true;
        
        timeoutHandler.removeCallbacks(timeoutRunnable);
        
        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_VIDEO_URL, videoUrl);
        resultIntent.putExtra(RESULT_CLEARANCE_COOKIE, 
            cookieManagerHelper.getCloudflareClearanceCookie(targetUrl));
        resultIntent.putExtra(RESULT_ALL_COOKIES, 
            cookieManagerHelper.getAllCookies(targetUrl));
        
        setResult(RESULT_OK, resultIntent);
        finish();
    }
    
    /**
     * Finish activity with error
     */
    private void finishWithError(String errorMessage) {
        if (operationCompleted) return;
        operationCompleted = true;
        
        timeoutHandler.removeCallbacks(timeoutRunnable);
        
        Log.e(TAG, "Finishing with error: " + errorMessage);
        Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
        
        Intent resultIntent = new Intent();
        resultIntent.putExtra(RESULT_ERROR_MESSAGE, errorMessage);
        
        setResult(RESULT_CANCELED, resultIntent);
        finish();
    }
    
    /**
     * Update status text for user feedback
     */
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            statusText.setText(status);
            Log.d(TAG, "Status: " + status);
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
    
    /**
     * Unescape JavaScript string
     */
    private String unescapeJavaScriptString(String str) {
        return str.replace("\\u003C", "<")
                  .replace("\\u003E", ">")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\'", "'")
                  .replace("\\\"", "\"")
                  .replace("\\\\", "\\");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
        
        if (webView != null) {
            webView.destroy();
        }
    }
    
    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle("Cancel Operation")
            .setMessage("Are you sure you want to cancel?")
            .setPositiveButton("Yes", (dialog, which) -> {
                finishWithError("Operation cancelled by user");
            })
            .setNegativeButton("No", null)
            .show();
        super.onBackPressed();
    }
}
