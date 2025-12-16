package com.omarflex5.data.scraper.client;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Base WebViewClient with standard policies for SSL, Redirects, and Logging.
 */
public class CoreWebViewClient extends WebViewClient {

    private static final String TAG = "CoreWebViewClient";
    protected final WebViewController controller;

    public CoreWebViewClient(WebViewController controller) {
        this.controller = controller;
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

        // Automatic Cloudflare Detection (Moved from ScraperWebViewClient)
        // Check for Cloudflare Challenge using Page Title
        view.evaluateJavascript("document.title", title -> {
            String pageTitle = title != null ? title.replace("\"", "") : "";

            if (controller != null) {
                // Update status with title
                controller.updateStatus("Status: " + (pageTitle.isEmpty() ? "Loading..." : pageTitle));
            }

            if (pageTitle.contains("Cloudflare") ||
                    pageTitle.contains("Just a moment") ||
                    pageTitle.contains("Checking your browser")) {

                // Still on CF challenge
                Log.d(TAG, "CF challenge detected via Title: " + pageTitle);
                if (controller != null) {
                    controller.onCloudflareDetected();
                }
            } else if (!pageTitle.isEmpty()) {
                // CF passed or normal page loaded
                // Notify controller that content is safe to process
                if (controller != null) {
                    controller.onContentReady(url);
                }
            }
        });
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // Default: Allow WebView to handle all internal redirects (essential for
        // Cloudflare)
        return false;
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        Log.w(TAG, "SSL Error ignored: " + error.toString());
        handler.proceed(); // Standard for scrapers, though risky for banking apps (not our use case)
    }
}
