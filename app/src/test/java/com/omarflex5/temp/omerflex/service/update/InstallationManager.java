package com.omarflex5.temp.omerflex.service.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InstallationManager {
    private static final String TAG = "InstallationManager";

    public interface InstallationListener {
        void onInstallationStarted();
        void onInstallationComplete();
        void onInstallationFailed(String error);
        void onPermissionRequired();
    }

    private Context context;
    private InstallationListener listener;

    public InstallationManager(Context context, InstallationListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void installUpdate(File apkFile) {
        if (!apkFile.exists()) {
            listener.onInstallationFailed("APK file not found");
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                installUsingPackageInstaller(apkFile);
            } else {
                installUsingIntent(apkFile);
            }
        } catch (Exception e) {
            Log.e(TAG, "Installation error", e);
            listener.onInstallationFailed("Installation failed: " + e.getMessage());
        }
    }

    private void installUsingPackageInstaller(File apkFile) throws IOException {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);

        try {
            OutputStream out = session.openWrite("package", 0, apkFile.length());
            FileInputStream in = new FileInputStream(apkFile);

            byte[] buffer = new byte[65536];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            session.fsync(out);
            out.close();
            in.close();

            // Create intent for InstallReceiver
            Intent intent = new Intent(context, InstallReceiver.class);
            intent.setAction("android.content.pm.action.SESSION_UPDATED");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

            session.commit(pendingIntent.getIntentSender());
            listener.onInstallationStarted();

        } finally {
            session.close();
        }
    }

    private void installUsingIntent(File apkFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(getApkUri(apkFile), "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(intent);
        listener.onInstallationStarted();
    }

    private android.net.Uri getApkUri(File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return androidx.core.content.FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", apkFile);
        } else {
            return android.net.Uri.fromFile(apkFile);
        }
    }
}