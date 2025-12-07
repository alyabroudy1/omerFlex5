package com.omarflex5.temp.omerflex.service.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

public class InstallReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -99); // Use a unique default
        String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        Log.d(TAG, "Installation status received: " + status + " for package: " + packageName);

        // Using if/else block to handle non-standard constant values on some devices.
        // We will trust the official Android documentation where:
        // STATUS_PENDING_USER_ACTION = 1
        // STATUS_SUCCESS = 0
        if (status == 1) {
            Log.d(TAG, "Handling status 1 as PENDING_USER_ACTION.");
            handleUserActionRequired(context, intent);
        } else if (status == 0) {
            Log.d(TAG, "Handling status 0 as SUCCESS.");
            handleInstallationSuccess(context, packageName);
        } else { // All other codes are treated as failures
            Log.e(TAG, "Handling status " + status + " as FAILURE.");
            handleInstallationFailure(context, status, statusMessage);
        }
    }

    private void handleUserActionRequired(Context context, Intent intent) {
        Log.d(TAG, "User action required for installation");

        // Get the confirmation intent from the extras
        Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirmationIntent != null) {
            // Start the confirmation activity
            confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(confirmationIntent);
        } else {
            Log.e(TAG, "No confirmation intent provided");
            // Fallback: show a notification or start our own confirmation activity
            showInstallationPrompt(context);
        }
    }

    private void handleInstallationSuccess(Context context, String packageName) {
        Log.d(TAG, "Installation successful for package: " + packageName);

        // Clean up downloaded APK file
        cleanupDownloadedFiles(context);

        // You can send a broadcast or notification here if needed
        Intent successIntent = new Intent("com.androidtv.updateapp.INSTALL_SUCCESS");
        successIntent.putExtra("package_name", packageName);
        context.sendBroadcast(successIntent);
    }

    private void handleInstallationFailure(Context context, int status, String message) {
        Log.e(TAG, "Installation failed with status: " + status + ", message: " + message);

        // Clean up downloaded APK file
        cleanupDownloadedFiles(context);

        // Send failure broadcast
        Intent failureIntent = new Intent("com.androidtv.updateapp.INSTALL_FAILED");
        failureIntent.putExtra("status", status);
        failureIntent.putExtra("message", message);
        context.sendBroadcast(failureIntent);
    }

    private void showInstallationPrompt(Context context) {
        // Fallback method to show installation prompt
        // This could start an activity or show a notification
        Log.d(TAG, "Showing installation prompt");

        Intent promptIntent = new Intent(context, UpdateDialogActivity.class);
        promptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        promptIntent.putExtra("show_install_prompt", true);
        context.startActivity(promptIntent);
    }

    private void cleanupDownloadedFiles(Context context) {
        try {
            // Clean up the downloaded APK file
            java.io.File updateDir = new java.io.File(context.getExternalFilesDir(null), "updates");
            if (updateDir.exists() && updateDir.isDirectory()) {
                java.io.File[] files = updateDir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        if (file.getName().endsWith(".apk") || file.getName().endsWith(".tmp")) {
                            file.delete();
                            Log.d(TAG, "Deleted file: " + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up files", e);
        }
    }
}