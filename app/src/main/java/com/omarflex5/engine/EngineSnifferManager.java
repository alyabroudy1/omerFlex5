package com.omarflex5.engine;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.webkit.ValueCallback;

/**
 * Manager for creating and managing IWebEngine instances for video sniffing.
 * Works with both WebView and GeckoView flavors.
 */
public class EngineSnifferManager {

    private static final String TAG = "EngineSnifferManager";
    private static volatile EngineSnifferManager INSTANCE;

    private final Context context;
    private IWebEngine currentEngine;

    private EngineSnifferManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static EngineSnifferManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (EngineSnifferManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new EngineSnifferManager(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Get or create a web engine for video sniffing.
     * Creates a new engine on demand, caller is responsible for lifecycle.
     */
    public IWebEngine createEngine() {
        return createEngine(context);
    }

    /**
     * Get or create a web engine for video sniffing.
     * Creates a new engine on demand, caller is responsible for lifecycle.
     * 
     * @param context Activity context (required for proper UI/Window access)
     */
    public IWebEngine createEngine(Context context) {
        if (!WebEngineFactory.isInitialized()) {
            Log.e(TAG, "WebEngineFactory not initialized!");
            return null;
        }

        IWebEngine engine = WebEngineFactory.getInstance().create(context);
        Log.d(TAG, "Created engine: " + WebEngineFactory.getInstance().getEngineName());
        return engine;
    }

    /**
     * Get a shared engine instance for sniffing.
     * Creates if not exists, reuses if available.
     */
    public IWebEngine getSharedEngine() {
        if (currentEngine == null) {
            currentEngine = createEngine();
        }
        return currentEngine;
    }

    /**
     * Detach the shared engine view from its parent (for reattaching elsewhere).
     */
    public void detachEngineView() {
        if (currentEngine != null) {
            View view = currentEngine.getView();
            if (view.getParent() != null) {
                ((android.view.ViewGroup) view.getParent()).removeView(view);
            }
        }
    }

    /**
     * Release the shared engine and clean up resources.
     */
    public void releaseSharedEngine() {
        if (currentEngine != null) {
            detachEngineView();
            currentEngine.loadUrl("about:blank");
            // Don't destroy - keep for reuse
        }
    }

    /**
     * Destroy the shared engine completely.
     */
    public void destroyEngine() {
        if (currentEngine != null) {
            currentEngine.destroy();
            currentEngine = null;
        }
    }

    /**
     * Check if using GeckoView flavor.
     */
    public boolean isGeckoView() {
        return WebEngineFactory.isInitialized() && WebEngineFactory.getInstance().isGeckoView();
    }

    /**
     * Get the engine name for display purposes.
     */
    public String getEngineName() {
        if (WebEngineFactory.isInitialized()) {
            return WebEngineFactory.getInstance().getEngineName();
        }
        return "Unknown";
    }

    /**
     * Apply default sniffer settings to an engine.
     */
    public void applySnifferSettings(IWebEngine engine) {
        WebEngineSettings settings = WebEngineSettings.builder()
                .setJavaScriptEnabled(true)
                .setDomStorageEnabled(true)
                .setMediaPlaybackRequiresUserGesture(false)
                .setAllowContentAccess(true)
                .build();
        engine.applySettings(settings);
    }
}
