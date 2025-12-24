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
 * Filters short URLs as pre-roll ad proxies.
 */
public class SnifferWebViewClient extends CoreWebViewClient {

    private static final String TAG = "SnifferWebViewClient";
    private static final int MIN_VIDEO_URL_LENGTH = 50; // Short URLs are likely ad proxies (v.mp4, ad.mp4)

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
            Log.d(TAG, "Intercepted video request: " + url + " (Len: " + url.length() + ")");

            // Filter short URLs - they are likely pre-roll ad proxies like /v.mp4
            if (url.length() < MIN_VIDEO_URL_LENGTH) {
                Log.d(TAG, "  -> REJECTED (Short URL < " + MIN_VIDEO_URL_LENGTH + " chars - likely ad proxy)");
                // Don't notify - continue sniffing for real video
                return null;
            }

            Log.d(TAG, "  -> ACCEPTED (URL length >= " + MIN_VIDEO_URL_LENGTH + ")");
            if (controller != null) {
                controller.onVideoDetected(url, request.getRequestHeaders());
            }
            // Allow request to proceed (don't block)
            return null;
        }

        return super.shouldInterceptRequest(view, request);
    }

    private boolean isVideoUrl(String url) {
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.contains(".mp4?") || lower.contains(".m3u8?");
    }
}
