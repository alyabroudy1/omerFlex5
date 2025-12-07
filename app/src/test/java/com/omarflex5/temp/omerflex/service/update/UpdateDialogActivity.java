package com.omarflex5.temp.omerflex.service.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.omerflex.R;

public class UpdateDialogActivity extends Activity {
    private static final String TAG = "UpdateDialogActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "UpdateDialogActivity created");

        // Make the activity appear as a dialog
        setFinishOnTouchOutside(false);

        // Get update info from intent
        UpdateInfo updateInfo = (UpdateInfo) getIntent().getSerializableExtra("update_info");

        if (updateInfo != null) {
            showUpdateDialog(updateInfo);
        } else {
            Log.e(TAG, "No update info provided");
            finish();
        }
    }

    private void showUpdateDialog(UpdateInfo updateInfo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, com.omerflex.R.style.TVDialogTheme);

        builder.setTitle("Update Available")
                .setMessage("Version " + updateInfo.getVersionName() + " is available.\n\n" +
                        "Changelog:\n" + updateInfo.getChangelog() + "\n\n" +
                        "Size: " + formatFileSize(updateInfo.getApkSize()))
                .setPositiveButton("Update Now", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startUpdate(updateInfo);
                    }
                })
                .setNegativeButton("Later", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        finish();
                    }
                });

        if (updateInfo.isMandatory()) {
            builder.setCancelable(false);
        }

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });

        dialog.show();
    }

    private void startUpdate(UpdateInfo updateInfo) {
        Log.d(TAG, "Starting update to version " + updateInfo.getVersionName());

        // Start the progress activity to handle download and installation
        Intent intent = new Intent(this, ProgressActivity.class);
        intent.putExtra("update_info", updateInfo);
        startActivity(intent);

        finish();
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    @Override
    public void onBackPressed() {
        // Prevent dismissal with back button if update is mandatory
        UpdateInfo updateInfo = (UpdateInfo) getIntent().getSerializableExtra("update_info");
        if (updateInfo != null && updateInfo.isMandatory()) {
            // Don't allow back button to dismiss mandatory updates
            return;
        }
        super.onBackPressed();
    }
}