//
package com.omarflex5.temp.omerflex.service;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.omerflex.entity.ServerConfig;
import com.omerflex.entity.dto.ServerConfigDTO;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class UpdateService {

    private static final String TAG = "UpdateActivity";

    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final int REQUEST_CODE_INSTALL_PERMISSION = 100;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 101;
    private boolean permissionRequested = false;
    private static String APK_URL = "https://github.com/alyabroudy1/omerFlex_3/raw/refs/heads/mobile-4/app/omerFlex.apk";
    private static String APK_NAME = "omerFlex";

    private long downloadId;
    private BroadcastReceiver downloadCompleteReceiver;
    Fragment fragment;
    Activity activity;

    public UpdateService(Fragment fragment) {
        this.fragment = fragment;
        this.activity = fragment.getActivity();
    }

    public void checkForUpdates(ServerConfigDTO githubServerConfigDTO) {
        String version = githubServerConfigDTO.description;
        try {
            int number = Integer.parseInt(version);
            // Use the number
            Log.d(TAG, "checkForUpdates: " + version + ", n: " + number);
            if (toBeUpdated(number)) {
                showUpdateDialog(githubServerConfigDTO.url);
            }
        } catch (NumberFormatException e) {
            // Handle the case where the string is not a valid integer
            Log.d(TAG, "checkForUpdates: fail reading int version number: " + version);
        }

    }

    public boolean toBeUpdated(int newVersionCode) {
        // Get the package manager
        PackageManager pm = activity.getPackageManager();
// Get the package info
        PackageInfo packageInfo = null;
        try {
            packageInfo = pm.getPackageInfo(activity.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
// Get the current version code
        int currentVersion = packageInfo.versionCode;
        APK_NAME = APK_NAME + "_v"+ newVersionCode;
// Get the current version name
        String currentVersionName = packageInfo.versionName;

//        Log.d(TAG, "toBeUpdated: version: " + currentVersion + ", name: " + currentVersionName);
//        Log.d(TAG, "toBeUpdated: new version: " + newVersionCode);
//        Log.d(TAG, "toBeUpdated: new APK_NAME: " + APK_NAME);
        return newVersionCode > currentVersion ;
//        return true;
    }

    private void showUpdateDialog(String url) {
        new AlertDialog.Builder(activity)
                .setTitle("تحديث")
                .setMessage("إصدار جديد من التطبيق متاح. هل تريد التحديث الآن؟")
                .setPositiveButton("تحديث", (dialog, which) -> startDownload(url))
                .setNegativeButton("إلغاء", (dialog, which) -> Toast.makeText(activity, "إلغاء التحديث...", Toast.LENGTH_SHORT).show())
                .setCancelable(false)
                .show();
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE_STORAGE_PERMISSION);
    }

    private void startDownload(String url) {

        // Check and request permissions first
        if (!hasStoragePermission()) {
            requestStoragePermission();
            return;
        }
        // Create a download request
        Log.d(TAG, "startDownload: ");
        Log.d(TAG, "startDownload called, permission: " + hasStoragePermission());
        Log.d(TAG, "External storage state: " + Environment.getExternalStorageState());
        Log.d(TAG, "External storage directory: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        // Enqueue the download
        DownloadManager downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager service is null");
            Toast.makeText(activity, "Download service not available", Toast.LENGTH_LONG).show();
            return;
        }


        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("App Update");
        request.setDescription("Downloading the latest version...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_NAME+".apk");

        if (downloadManager != null) {
            downloadId = downloadManager.enqueue(request);
            Log.d(TAG, "startDownload: downloadId: " + downloadId);
        }

        // Register a BroadcastReceiver to listen for download completion
        downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                Log.d(TAG, "onReceive: " + id);
                if (id == downloadId) {
                    installUpdate();
                }
            }
        };
        // Register the receiver with the appropriate flag for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(
                    downloadCompleteReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
            );
        } else {
            activity.registerReceiver(
                    downloadCompleteReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            );
        }
    }

    private void installUpdate() {
        Uri apkUri = getDownloadedApkUri();
        if (apkUri == null) return; // Exit if no files match the criteria
        Log.d(TAG, "installUpdate: uri: " + apkUri.toString());

        // Check if the app has permission to install packages
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                // Request permission to install packages
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                // note: the fragment not the activity
                fragment.startActivityForResult(intent, REQUEST_CODE_INSTALL_PERMISSION);
                return;
            }
        }

        // If permission is already granted, proceed with installation
        proceedWithInstallation(apkUri);
    }

    @Nullable
    private Uri getDownloadedApkUri() {
        // Get the download directory
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        // Filter files to only those with the name pattern
        File[] apkFiles = downloadDir.listFiles((dir, name) -> name.startsWith(APK_NAME) && name.endsWith(".apk"));

        if (apkFiles == null || apkFiles.length == 0) {
            Toast.makeText(activity, "APK file not found.", Toast.LENGTH_SHORT).show();
            return null;
        }

        // Find the most recent file
        File latestApkFile = Arrays.stream(apkFiles)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);

        if (latestApkFile == null) {
            Toast.makeText(activity, "No valid APK file found.", Toast.LENGTH_SHORT).show();
            return null;
        }

        Log.d(TAG, "installUpdate: Latest APK file: " + latestApkFile.getName());

        // Create a content URI using FileProvider for the latest APK file
        Uri apkUri = FileProvider.getUriForFile(activity, activity.getApplicationContext().getPackageName() + ".provider", latestApkFile);
        return apkUri;
    }

    private void proceedWithInstallation(Uri apkUri) {
        // Start the installation
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(installIntent);
//    dbHelper.saveServerConfig(ServerConfigRepository.getConfig(Movie.SERVER_APP));
    }

    public void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: " + requestCode);
        if (requestCode != REQUEST_CODE_INSTALL_PERMISSION) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity.getPackageManager().canRequestPackageInstalls()) {
            Log.d(TAG, "onActivityResult: installUpdate");
            Uri apkUri = getDownloadedApkUri();
            proceedWithInstallation(apkUri);
        } else {
            Toast.makeText(activity, "Install permission denied.", Toast.LENGTH_SHORT).show();
        }

    }

    public void handleOnDestroy() {
        if (downloadCompleteReceiver != null) {
            activity.unregisterReceiver(downloadCompleteReceiver); // Unregister the receiver
        }
    }

    ///------------

    private void checkAndRequestPermissions() {
        // List of permissions to request
        List<String> permissionsToRequest = new ArrayList<>();

        // Check and add permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires media-specific permissions
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            // Android 12 and below require legacy storage permissions
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        // Request permissions if needed
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
        } else {
            // All permissions are already granted
            Toast.makeText(activity, "All permissions granted!", Toast.LENGTH_SHORT).show();
        }
    }

    public void handleOnRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "handleOnRequestPermissionsResult: " + requestCode);

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(activity, "Storage permission granted", Toast.LENGTH_SHORT).show();
                // You might want to restart the download here if you stored the URL
            } else {
                Toast.makeText(activity, "Storage permission denied - cannot download update", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showPermissionExplanationDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("Permissions Required")
                .setMessage("This app needs permissions to access storage to function properly. Please grant the permissions in the app settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> openAppSettings())
                .setNegativeButton("Cancel", (dialog, which) -> Toast.makeText(activity, "Permissions denied. Some features may not work.", Toast.LENGTH_SHORT).show())
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }

}