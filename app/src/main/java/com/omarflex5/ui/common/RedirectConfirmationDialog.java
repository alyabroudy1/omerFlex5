package com.omarflex5.ui.common;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Dialog for confirming cross-domain redirects.
 * Auto-rejects after timeout (default 8 seconds).
 */
public class RedirectConfirmationDialog {

    private static final String TAG = "RedirectConfirmDialog";
    private static final long DEFAULT_TIMEOUT_MS = 8000; // 8 seconds

    /**
     * Callback for redirect confirmation result.
     */
    public interface ConfirmationCallback {
        void onAllow();

        void onReject();
    }

    /**
     * Show redirect confirmation dialog with auto-reject timeout.
     *
     * @param activity  Parent activity
     * @param targetUrl The URL being redirected to
     * @param callback  Callback for user decision
     */
    public static void show(Activity activity, String targetUrl, ConfirmationCallback callback) {
        show(activity, targetUrl, DEFAULT_TIMEOUT_MS, callback);
    }

    /**
     * Show redirect confirmation dialog with custom timeout.
     *
     * @param activity  Parent activity
     * @param targetUrl The URL being redirected to
     * @param timeoutMs Timeout in milliseconds before auto-reject
     * @param callback  Callback for user decision
     */
    public static void show(Activity activity, String targetUrl, long timeoutMs, ConfirmationCallback callback) {
        if (activity == null || activity.isFinishing()) {
            Log.w(TAG, "Activity not available, auto-rejecting redirect");
            if (callback != null)
                callback.onReject();
            return;
        }

        String domain = extractDomain(targetUrl);
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] handled = { false };

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("⚠️ External Redirect")
                .setMessage("Navigate to external domain?\n\n" + domain +
                        "\n\nAuto-blocking in " + (timeoutMs / 1000) + " seconds...")
                .setCancelable(false)
                .setPositiveButton("Allow", (dialog, which) -> {
                    if (!handled[0]) {
                        handled[0] = true;
                        Log.d(TAG, "User ALLOWED redirect to: " + domain);
                        if (callback != null)
                            callback.onAllow();
                    }
                })
                .setNegativeButton("Block", (dialog, which) -> {
                    if (!handled[0]) {
                        handled[0] = true;
                        Log.d(TAG, "User BLOCKED redirect to: " + domain);
                        if (callback != null)
                            callback.onReject();
                    }
                });

        // Show dialog on main thread
        handler.post(() -> {
            try {
                AlertDialog dialog = builder.create();

                // Set up auto-reject timeout
                Runnable timeoutRunnable = () -> {
                    if (!handled[0] && dialog.isShowing()) {
                        if (activity.isFinishing()
                                || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                                        && activity.isDestroyed())) {
                            Log.d(TAG, "Activity destroyed, skipping dismiss");
                            return;
                        }
                        handled[0] = true;
                        Log.d(TAG, "TIMEOUT - auto-rejected redirect to: " + domain);
                        try {
                            dialog.dismiss();
                        } catch (Exception e) {
                            Log.w(TAG, "Error dismissing dialog", e);
                        }
                        if (callback != null)
                            callback.onReject();
                    }
                };

                dialog.setOnShowListener(d -> {
                    // Start timeout counter
                    handler.postDelayed(timeoutRunnable, timeoutMs);
                });

                dialog.setOnDismissListener(d -> {
                    // Clean up timeout
                    handler.removeCallbacks(timeoutRunnable);
                });

                dialog.show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing dialog: " + e.getMessage());
                if (!handled[0]) {
                    handled[0] = true;
                    if (callback != null)
                        callback.onReject();
                }
            }
        });
    }

    /**
     * Extract domain from URL for display.
     */
    private static String extractDomain(String url) {
        if (url == null)
            return "Unknown";
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url.length() > 50 ? url.substring(0, 50) + "..." : url;
        }
    }
}
