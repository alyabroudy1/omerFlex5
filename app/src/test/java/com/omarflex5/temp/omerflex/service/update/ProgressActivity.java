package com.omarflex5.temp.omerflex.service.update;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.omerflex.R;

import java.io.File;

public class ProgressActivity extends Activity {
    private static final String TAG = "ProgressActivity";

    private ProgressBar progressBar;
    private TextView statusText;
    private View cancelButton;

    private DownloadManager downloadManager;
    private InstallationManager installationManager;
    private UpdateInfo updateInfo;

    private BroadcastReceiver installReceiver;
    private boolean hasInstallPermission = false;
    private boolean permissionCheckRequested = false;

    public static void start(Activity activity, UpdateInfo updateInfo) {
        Intent intent = new Intent(activity, ProgressActivity.class);
        intent.putExtra("update_info", updateInfo);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        updateInfo = (UpdateInfo) getIntent().getSerializableExtra("update_info");

        initViews();
        setupInstallReceiver();
        // The update process will now be started from onResume or after the permission check
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check permission every time the activity is resumed.
        // This handles the case where the user grants permission and returns to the app.
        checkAndStartUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (installReceiver != null) {
            unregisterReceiver(installReceiver);
        }
        if (downloadManager != null) {
            downloadManager.cancelDownload();
        }
    }

    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        cancelButton = findViewById(R.id.cancel_button);

        cancelButton.setOnClickListener(v -> {
            if (downloadManager != null) {
                downloadManager.cancelDownload();
            }
            finish();
        });
    }

    private void checkAndStartUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hasInstallPermission = getPackageManager().canRequestPackageInstalls();
        } else {
            hasInstallPermission = true;
        }

        if (hasInstallPermission) {
            // If we have permission and download isn't already running/complete
            if (downloadManager == null) {
                startDownload();
            }
        } else {
            // If we haven't already sent the user to settings
            if (!permissionCheckRequested) {
                statusText.setText("Please enable installation from unknown sources");
                progressBar.setVisibility(View.GONE);
                requestInstallPermission();
            }
        }
    }

    private void requestInstallPermission() {
        permissionCheckRequested = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }


    private void setupInstallReceiver() {
        installReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.androidtv.updateapp.INSTALL_SUCCESS".equals(action)) {
                    runOnUiThread(() -> {
                        statusText.setText("Installation complete!");
                        finish();
                    });
                } else if ("com.androidtv.updateapp.INSTALL_FAILED".equals(action)) {
                    String message = intent.getStringExtra("message");
                    runOnUiThread(() -> {
                        statusText.setText("Installation failed: " + message);
                    });
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.androidtv.updateapp.INSTALL_SUCCESS");
        filter.addAction("com.androidtv.updateapp.INSTALL_FAILED");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(installReceiver, filter);
        }
    }

    private void startDownload() {
        permissionCheckRequested = false; // Reset flag
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Downloading update...");

        downloadManager = new DownloadManager(this, new DownloadManager.DownloadListener() {
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> {
                    progressBar.setProgress(progress);
                    statusText.setText("Downloading: " + progress + "%");
                });
            }

            @Override
            public void onComplete(File apkFile) {
                runOnUiThread(() -> {
                    statusText.setText("Download complete. Installing...");
                    installUpdate(apkFile);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    statusText.setText("Download failed: " + error);
                    Log.e(TAG, "Download error: " + error);
                });
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> {
                    statusText.setText("Download cancelled");
                });
            }
        });

        downloadManager.downloadUpdate(updateInfo);
    }

    private void installUpdate(File apkFile) {
        // Validate APK before installation
        if (!UpdateValidator.validateAPK(this, apkFile)) {
            statusText.setText("APK validation failed");
            return;
        }

        installationManager = new InstallationManager(this, new InstallationManager.InstallationListener() {
            @Override
            public void onInstallationStarted() {
                runOnUiThread(() -> {
                    statusText.setText("Installing update...");
                });
            }

            @Override
            public void onInstallationComplete() {
                // Installation complete will be handled by InstallReceiver
                runOnUiThread(() -> {
                    statusText.setText("Installation initiated...");
                });
            }

            @Override
            public void onInstallationFailed(String error) {
                runOnUiThread(() -> {
                    statusText.setText("Installation failed: " + error);
                    Log.e(TAG, "Installation error: " + error);
                });
            }

            @Override
            public void onPermissionRequired() {
                // This case should ideally not be hit now due to the new flow, but kept for safety.
                runOnUiThread(() -> {
                    statusText.setText("Please enable installation from unknown sources");
                    checkAndStartUpdate(); // Re-trigger the permission check
                });
            }
        });

        installationManager.installUpdate(apkFile);
    }
}