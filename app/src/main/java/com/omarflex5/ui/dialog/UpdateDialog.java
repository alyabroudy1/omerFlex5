package com.omarflex5.ui.dialog;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.omarflex5.R;
import com.omarflex5.data.model.UpdateInfo;
import com.omarflex5.service.DownloadService;
import com.omarflex5.util.UpdateManager;
import java.io.File;

public class UpdateDialog extends DialogFragment {

    private UpdateInfo updateInfo;
    private TextView titleView;
    private TextView versionView;
    private TextView notesView;
    private ProgressBar progressBar;
    private TextView statusView;
    private Button btnUpdate;
    private Button btnCancel;
    private File downloadedFile;

    private BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null)
                return;

            switch (intent.getAction()) {
                case DownloadService.ACTION_DOWNLOAD_PROGRESS:
                    int progress = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0);
                    updateProgress(progress);
                    break;
                case DownloadService.ACTION_DOWNLOAD_COMPLETE:
                    String path = intent.getStringExtra(DownloadService.EXTRA_FILE_PATH);
                    downloadedFile = new File(path);
                    onDownloadComplete();
                    break;
                case DownloadService.ACTION_DOWNLOAD_ERROR:
                    String error = intent.getStringExtra(DownloadService.EXTRA_ERROR);
                    Toast.makeText(context, "Download failed: " + error, Toast.LENGTH_SHORT).show();
                    dismiss();
                    break;
            }
        }
    };

    public static UpdateDialog newInstance(UpdateInfo info) {
        UpdateDialog dialog = new UpdateDialog();
        dialog.updateInfo = info;
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View view = inflater.inflate(R.layout.dialog_update, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }

        initViews(view);
        return dialog;
    }

    private void initViews(View view) {
        titleView = view.findViewById(R.id.text_update_title);
        versionView = view.findViewById(R.id.text_version_name);
        notesView = view.findViewById(R.id.text_release_notes);
        progressBar = view.findViewById(R.id.progress_update);
        statusView = view.findViewById(R.id.text_progress_status);
        btnUpdate = view.findViewById(R.id.btn_update);
        btnCancel = view.findViewById(R.id.btn_cancel);

        if (updateInfo != null) {
            versionView.setText("Version " + updateInfo.getVersionName());
            notesView.setText(updateInfo.getReleaseNotes());
        }

        btnCancel.setOnClickListener(v -> dismiss());

        btnUpdate.setOnClickListener(v -> {
            if (downloadedFile != null) {
                installUpdate();
            } else {
                startDownload();
            }
        });
    }

    private void startDownload() {
        if (updateInfo == null)
            return;

        btnUpdate.setEnabled(false);
        btnUpdate.setText("Downloading...");
        progressBar.setVisibility(View.VISIBLE);
        statusView.setVisibility(View.VISIBLE);

        UpdateManager.getInstance().startUpdate(requireContext(), updateInfo.getApkUrl());
    }

    private void updateProgress(int progress) {
        progressBar.setProgress(progress);
        statusView.setText(progress + "%");
    }

    private void onDownloadComplete() {
        progressBar.setVisibility(View.GONE);
        statusView.setVisibility(View.GONE);
        btnUpdate.setEnabled(true);
        btnUpdate.setText("Install Now");
        btnUpdate.requestFocus(); // Focus for D-pad users

        // Auto trigger install if permission granted, otherwise show button
        installUpdate();
    }

    private void installUpdate() {
        Context context = requireContext();
        if (UpdateManager.getInstance().checkInstallPermission(context)) {
            UpdateManager.getInstance().installUpdate(context, downloadedFile);
        } else {
            Toast.makeText(context, "Please grant permission to install unknown apps", Toast.LENGTH_LONG).show();
            UpdateManager.getInstance().requestInstallPermission(context);
            // We remain in the dialog. User comes back, clicks "Install Now" again, logic
            // repeats.
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_DOWNLOAD_PROGRESS);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(DownloadService.ACTION_DOWNLOAD_ERROR);
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(downloadReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(downloadReceiver);
    }
}
