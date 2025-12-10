package com.omarflex5.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.omarflex5.data.model.UpdateInfo;
import com.omarflex5.service.DownloadService;
import com.google.gson.Gson;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static UpdateManager instance;
    private static final String UPDATE_JSON_URL = "https://raw.githubusercontent.com/alyabroudy1/omerFlex5/refs/heads/main/app/src/main/java/com/omarflex5/data/update.json"; // TODO:
                                                                                                                                                                              // Replace
                                                                                                                                                                              // with
                                                                                                                                                                              // actual
    // URL
    private OkHttpClient client;
    private Gson gson;

    private UpdateManager() {
        client = new OkHttpClient();
        gson = new Gson();
    }

    public static synchronized UpdateManager getInstance() {
        if (instance == null) {
            instance = new UpdateManager();
        }
        return instance;
    }

    public interface UpdateCheckCallback {
        void onUpdateAvailable(UpdateInfo updateInfo);

        void onNoUpdate();

        void onError(String error);
    }

    public void checkForUpdate(Context context, UpdateCheckCallback callback) {
        Log.d(TAG, "checkForUpdate: Checking for updates from " + UPDATE_JSON_URL);
        Request request = new Request.Builder()
                .url(UPDATE_JSON_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "checkForUpdate: Request failed", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "checkForUpdate: Response received, code=" + response.code());
                if (!response.isSuccessful()) {
                    Log.e(TAG, "checkForUpdate: Unsuccessful response: " + response.code());
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("Failed to fetch update info"));
                    return;
                }

                try {
                    String json = response.body().string();
                    Log.d(TAG, "checkForUpdate: JSON received: " + json);
                    UpdateInfo updateInfo = gson.fromJson(json, UpdateInfo.class);

                    if (isUpdateAvailable(context, updateInfo)) {
                        Log.d(TAG, "checkForUpdate: Update available");
                        new Handler(Looper.getMainLooper()).post(() -> callback.onUpdateAvailable(updateInfo));
                    } else {
                        Log.d(TAG, "checkForUpdate: No update available");
                        new Handler(Looper.getMainLooper()).post(callback::onNoUpdate);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "checkForUpdate: JSON parsing or processing error", e);
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("Invalid update data"));
                }
            }
        });
    }

    private boolean isUpdateAvailable(Context context, UpdateInfo updateInfo) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            int currentVersionCode = pInfo.versionCode;
            Log.d(TAG, "isUpdateAvailable: Local=" + currentVersionCode + ", Remote=" + updateInfo.getVersionCode());
            return updateInfo.getVersionCode() > currentVersionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void startUpdate(Context context, String url) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(DownloadService.EXTRA_URL, url);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public boolean checkInstallPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    public void requestInstallPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public void installUpdate(Context context, File apkFile) {
        if (!apkFile.exists())
            return;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getApplicationContext().getPackageName() + ".fileprovider",
                apkFile);

        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }
}
