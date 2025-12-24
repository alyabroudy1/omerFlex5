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
            controller.onPageStarted(url);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Log.d(TAG, "Page Finished: " + url);

        if (controller != null) {
            controller.onPageLoaded(url);
        }

        // Automatic Cloudflare Detection using HTML Content (Language Agnostic)
        // We verify content instead of Title because localized challenges (e.g. Arabic)
        // vary in title but contain standard technical tokens in HTML.
        view.evaluateJavascript(
                "(function() { return document.documentElement.outerHTML; })();",
                htmlRaw -> {
                    // Proper unescaping of JS string result
                    String html = unescapeJsString(htmlRaw);

                    // Debug: Log first 500 chars to help diagnose detection issues
                    if (html != null && html.length() > 0) {
                        String preview = html.length() > 500 ? html.substring(0, 500) : html;
                        Log.d(TAG, "HTML Preview: " + preview.replaceAll("\\s+", " "));
                    }

                    if (controller != null) {
                        controller.updateStatus("Status: Verifying content...");
                    }

                    if (html != null && com.omarflex5.data.scraper.util.CfDetector.isCloudflareContent(html)) {
                        // Still on CF challenge
                        Log.d(TAG, "CF challenge detected via Content");
                        if (controller != null) {
                            controller.onCloudflareDetected();
                        }
                    } else if (html != null && !html.isEmpty()) {
                        // CF passed or normal page loaded
                        if (controller != null) {
                            controller.onContentReady(url);
                        }
                    }
                });
    }

    /**
     * Properly unescape a JavaScript string returned from evaluateJavascript.
     */
    private String unescapeJsString(String jsString) {
        if (jsString == null)
            return null;
        // Remove surrounding quotes if present
        if (jsString.startsWith("\"") && jsString.endsWith("\"") && jsString.length() >= 2) {
            jsString = jsString.substring(1, jsString.length() - 1);
        }
        return jsString
                .replace("\\u003C", "<")
                .replace("\\u003c", "<")
                .replace("\\u003E", ">")
                .replace("\\u003e", ">")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
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
