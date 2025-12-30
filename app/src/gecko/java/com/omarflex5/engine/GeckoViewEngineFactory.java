package com.omarflex5.engine;

import android.content.Context;
import android.util.Log;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

/**
 * Factory for creating GeckoView-based IWebEngine instances.
 * Used in the gecko flavor.
 */
public class GeckoViewEngineFactory extends WebEngineFactory {

    private static final String TAG = "GeckoViewEngineFactory";
    private static boolean initialized = false;
    private static GeckoRuntime runtime;

    /**
     * Initialize the factory and GeckoRuntime. Should be called at app startup.
     * GeckoRuntime is expensive to create - we maintain a single instance.
     * IMPORTANT: Only initialize in the main process, not in GeckoView child
     * processes.
     */
    public static void init(Context context) {
        // Skip initialization for GeckoView child processes (e.g., :tab35, :gpu,
        // :socket)
        String processName = getProcessName(context);
        if (processName != null && processName.contains(":")) {
            Log.d(TAG, "Skipping GeckoView init for child process: " + processName);
            return;
        }

        if (!initialized) {
            Log.d(TAG, "Initializing GeckoView runtime in main process...");

            GeckoRuntimeSettings.Builder settingsBuilder = new GeckoRuntimeSettings.Builder();
            settingsBuilder.javaScriptEnabled(true);
            settingsBuilder.consoleOutput(true);
            settingsBuilder.forceUserScalableEnabled(true);

            // Set preferences directly without config file (avoids YAML parse error)
            // Note: Auto-play is enabled by default in GeckoSessionSettings
            // Performance and other settings are set via GeckoRuntimeSettings extras

            runtime = GeckoRuntime.create(context.getApplicationContext(), settingsBuilder.build());

            WebEngineFactory.setInstance(new GeckoViewEngineFactory());
            initialized = true;

            Log.d(TAG, "GeckoView runtime initialized successfully with autoplay enabled");
        }
    }

    /**
     * Get the current process name.
     */
    private static String getProcessName(Context context) {
        int pid = android.os.Process.myPid();
        android.app.ActivityManager am = (android.app.ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            for (android.app.ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
                if (info.pid == pid) {
                    return info.processName;
                }
            }
        }
        return null;
    }

    /**
     * Get the shared GeckoRuntime instance.
     */
    public static GeckoRuntime getRuntime() {
        if (runtime == null) {
            throw new IllegalStateException("GeckoRuntime not initialized. Call init() first.");
        }
        return runtime;
    }

    @Override
    public IWebEngine create(Context context) {
        if (runtime == null) {
            throw new IllegalStateException("GeckoRuntime not initialized. Call init() at app startup.");
        }
        return new GeckoViewEngine(context, runtime);
    }

    @Override
    public String getEngineName() {
        return "GeckoView";
    }

    @Override
    public boolean isGeckoView() {
        return true;
    }

    /**
     * Shutdown the GeckoRuntime. Call this when the app is terminating.
     */
    public static void shutdown() {
        if (runtime != null) {
            runtime.shutdown();
            runtime = null;
            initialized = false;
            Log.d(TAG, "GeckoView runtime shutdown");
        }
    }
}
