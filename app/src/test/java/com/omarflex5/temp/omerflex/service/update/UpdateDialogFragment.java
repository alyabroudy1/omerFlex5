package com.omarflex5.temp.omerflex.service.update;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;


public class UpdateDialogFragment extends DialogFragment {
    private static final String ARG_UPDATE_INFO = "update_info";

    public static UpdateDialogFragment newInstance(UpdateInfo updateInfo) {
        UpdateDialogFragment fragment = new UpdateDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_UPDATE_INFO, updateInfo);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        UpdateInfo updateInfo = (UpdateInfo) getArguments().getSerializable(ARG_UPDATE_INFO);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
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
                        // User cancelled the dialog
                    }
                });

        if (updateInfo.isMandatory()) {
            builder.setCancelable(false);
        }

        return builder.create();
    }

    private void startUpdate(UpdateInfo updateInfo) {
        ProgressActivity.start(getActivity(), updateInfo);
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}