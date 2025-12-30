package com.omarflex5.engine;

import android.content.Context;
import android.util.Log;

/**
 * GeckoView flavor initializer.
 * Initializes the GeckoViewEngineFactory and GeckoRuntime at app startup.
 */
public class FlavorEngineInitializer implements EngineInitializer {

    private static final String TAG = "FlavorEngineInit";

    @Override
    public void initialize(Context context) {
        Log.d(TAG, "Initializing GeckoView engine...");
        GeckoViewEngineFactory.init(context);
        Log.d(TAG, "GeckoView engine initialized");
    }
}
