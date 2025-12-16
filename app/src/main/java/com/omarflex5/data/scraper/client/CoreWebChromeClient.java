package com.omarflex5.data.scraper.client;

import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

/**
 * Base WebChromeClient with unified logging and progress updates.
 */
public class CoreWebChromeClient extends WebChromeClient {

    private static final String TAG = "CoreWebChromeClient";
    private final WebViewController controller;

    public CoreWebChromeClient(WebViewController controller) {
        this.controller = controller;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (controller != null) {
            controller.updateProgress(newProgress);
        }
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        // Standardized logging for debugging JS issues (like Turnstile or Video
        // Players)
        Log.d(TAG, "[" + consoleMessage.messageLevel() + "] " + consoleMessage.message()
                + " -- From line " + consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
        return true;
    }

    // NOTE: CustomView methods (FullScreen) can be overridden here or in a subclass
    // if specifically needed for Player/Browser activities.
}
