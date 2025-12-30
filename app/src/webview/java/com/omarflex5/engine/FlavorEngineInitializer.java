package com.omarflex5.engine;

import android.content.Context;
import android.util.Log;

/**
 * WebView flavor initializer.
 * Initializes the WebViewEngineFactory at app startup.
 */
public class FlavorEngineInitializer implements EngineInitializer {

    private static final String TAG = "FlavorEngineInit";

    @Override
    public void initialize(Context context) {
        Log.d(TAG, "Initializing WebView engine...");
        WebViewEngineFactory.init();
        Log.d(TAG, "WebView engine initialized");
    }
}
