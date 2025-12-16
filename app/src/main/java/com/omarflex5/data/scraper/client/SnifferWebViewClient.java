package com.omarflex5.data.scraper.client;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

/**
 * Specialized WebViewClient for Sniffing (Interactive & Browser modes).
 * Handles external intent redirection (Market, Mail, Tel, WhatsApp).
 */
public class SnifferWebViewClient extends CoreWebViewClient {

    private static final String TAG = "SnifferWebViewClient";
    private final Context context;

    public SnifferWebViewClient(Context context, WebViewController controller) {
        super(controller);
        this.context = context;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        // Handle External Protocols (Market, Mail, Tel, WhatsApp)
        if (url.startsWith("market://") || url.startsWith("mailto:") ||
                url.startsWith("tel:") || url.contains("whatsapp.com") || url.contains("tg:")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(intent);
                return true; // Handled external link
            } catch (Exception e) {
                Log.e(TAG, "Could not handle external link: " + url, e);
                return true; // Consume error to prevent WebView error page
            }
        }

        // Block Ads/Unknown schemes if necessary
        return super.shouldOverrideUrlLoading(view, request);
    }

    @Override
    public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();

        // Check for video URLs (Basic check, can be expanded)
        if (isVideoUrl(url)) {
            Log.d(TAG, "Intercepted video request: " + url);
            if (controller != null) {
                controller.onVideoDetected(url, request.getRequestHeaders());
            }
            // Allow request to proceed (don't block)
            return null;
        }

        return super.shouldInterceptRequest(view, request);
    }

    @Override
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Log.d(TAG, "onPageStarted: " + url);
        String cookies = android.webkit.CookieManager.getInstance().getCookie(url);
        Log.d(TAG, "COOKIES at onPageStarted: " + (cookies != null ? cookies : "null"));
    }

    private boolean isVideoUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.contains(".mp4?") || lower.contains(".m3u8?");
    }

}
