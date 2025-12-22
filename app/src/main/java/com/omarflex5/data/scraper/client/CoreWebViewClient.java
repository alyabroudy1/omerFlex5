package com.omarflex5.data.scraper.client;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.omarflex5.data.scraper.policy.RedirectPolicy;
import com.omarflex5.ui.common.RedirectConfirmationDialog;

/**
 * Base WebViewClient with standard policies for SSL, Redirects, and Logging.
 * Supports unified redirect policy with domain validation.
 */
public class CoreWebViewClient extends WebViewClient {

    private static final String TAG = "CoreWebViewClient";
    protected final WebViewController controller;
    protected RedirectPolicy redirectPolicy;
    protected Activity activityContext;

    public CoreWebViewClient(WebViewController controller) {
        this.controller = controller;
    }

    /**
     * Set redirect policy for domain-based validation.
     */
    public void setRedirectPolicy(RedirectPolicy policy) {
        this.redirectPolicy = policy;
    }

    /**
     * Set activity context for showing confirmation dialogs.
     */
    public void setActivityContext(Activity activity) {
        this.activityContext = activity;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Log.d(TAG, "Page Started: " + url);
        if (controller != null) {
            controller.updateStatus("Loading...");
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Log.d(TAG, "Page Finished: " + url);

        if (controller != null) {
            controller.onPageLoaded(url);
        }

        // Automatic Cloudflare Detection using utility
        view.evaluateJavascript("document.title", title -> {
            String pageTitle = title != null ? title.replace("\"", "") : "";

            if (controller != null) {
                controller.updateStatus("Status: " + (pageTitle.isEmpty() ? "Loading..." : pageTitle));
            }

            if (com.omarflex5.data.scraper.util.CfDetector.isCloudflareTitleIndicator(pageTitle)) {
                // Still on CF challenge
                Log.d(TAG, "CF challenge detected via Title: " + pageTitle);
                if (controller != null) {
                    controller.onCloudflareDetected();
                }
            } else if (!pageTitle.isEmpty()) {
                // CF passed or normal page loaded
                if (controller != null) {
                    controller.onContentReady(url);
                }
            }
        });
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        // 1. Always block non-HTTP schemes (intent://, market://, javascript:, etc.)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.w(TAG, "BLOCKED non-HTTP scheme: " + truncateUrl(url));
            return true; // Block
        }

        // 2. If no policy set, allow all HTTP redirects (default behavior)
        if (redirectPolicy == null) {
            return false; // Allow WebView to handle
        }

        // 3. Apply redirect policy
        String currentUrl = view.getUrl();
        RedirectPolicy.Decision decision = redirectPolicy.shouldAllowRedirect(currentUrl, url);

        switch (decision) {
            case BLOCK:
                Log.w(TAG, "BLOCKED by policy: " + truncateUrl(url));
                return true;

            case ASK_USER:
                // Show confirmation dialog if activity available
                if (activityContext != null && !activityContext.isFinishing()) {
                    Log.d(TAG, "Requesting user confirmation for: " + truncateUrl(url));

                    // Cache URL for loading after confirmation
                    final String targetUrl = url;

                    RedirectConfirmationDialog.show(activityContext, targetUrl,
                            new RedirectConfirmationDialog.ConfirmationCallback() {
                                @Override
                                public void onAllow() {
                                    // Load the URL after user allows
                                    view.post(() -> view.loadUrl(targetUrl));
                                }

                                @Override
                                public void onReject() {
                                    Log.d(TAG, "User rejected redirect to: " + truncateUrl(targetUrl));
                                    // Do nothing, stay on current page
                                }
                            });

                    return true; // Block until confirmed
                } else {
                    // No activity context, block by default for safety
                    Log.w(TAG, "No activity context for confirmation, blocking: " + truncateUrl(url));
                    return true;
                }

            case ALLOW:
            default:
                return false; // Allow WebView to handle
        }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Log.w(TAG, "SSL Error ignored: " + error.toString());
        handler.proceed(); // Standard for scrapers
    }

    /**
     * Truncate URL for logging.
     */
    protected String truncateUrl(String url) {
        if (url == null)
            return "";
        return url.length() > 80 ? url.substring(0, 80) + "..." : url;
    }
}
