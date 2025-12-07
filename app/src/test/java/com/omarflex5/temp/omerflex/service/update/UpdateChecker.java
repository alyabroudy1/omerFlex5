package com.omarflex5.temp.omerflex.service.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.omerflex.service.utils.NetworkUtils;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";

    public interface UpdateCheckListener {
        void onUpdateAvailable(UpdateInfo updateInfo);
        void onNoUpdateAvailable();
        void onError(String error);
    }

    private Context context;
    private UpdateCheckListener listener;
    private SharedPreferences prefs;

    public UpdateChecker(Context context, UpdateCheckListener listener) {
        this.context = context;
        this.listener = listener;
        this.prefs = context.getSharedPreferences(Constants.UPDATE_PREFS, Context.MODE_PRIVATE);
    }

    public void checkForUpdate() {
        Log.d(TAG, "checkForUpdate: ");
        if (true) {
            new UpdateCheckTask().execute();
            return;
        }
        if (!NetworkUtils.isNetworkAvailable()) {
            listener.onError("No network connection available");
            return;
        }

        // Check if we should check for update (respect interval)
        long lastCheck = prefs.getLong("last_update_check", 0);
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastCheck < Constants.UPDATE_CHECK_INTERVAL) {
            Log.d(TAG, "Update check skipped - too soon");
            return;
        }

        new UpdateCheckTask().execute();
    }

    private class UpdateCheckTask extends AsyncTask<Void, Void, UpdateInfo> {
        private String error;

        @Override
        protected UpdateInfo doInBackground(Void... voids) {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                Request request = new Request.Builder()
                        .url(Constants.UPDATE_SERVER_URL)
                        .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    error = "Server error: " + response.code();
                    return null;
                }

                String responseBody = response.body().string();
                Gson gson = new Gson();
                UpdateInfo updateInfo = gson.fromJson(responseBody, UpdateInfo.class);

                // Save check time
                prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply();

                return updateInfo;

            } catch (IOException e) {
                error = "Network error: " + e.getMessage();
                return null;
            } catch (Exception e) {
                error = "Parse error: " + e.getMessage();
                return null;
            }
        }

        @Override
        protected void onPostExecute(UpdateInfo updateInfo) {
            if (error != null) {
                listener.onError(error);
                return;
            }

            if (updateInfo == null) {
                listener.onError("Invalid update information");
                return;
            }

            try {
                PackageInfo packageInfo = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                int currentVersionCode = packageInfo.versionCode;

                if (updateInfo.getVersionCode() > currentVersionCode) {
                    listener.onUpdateAvailable(updateInfo);
                } else {
                    listener.onNoUpdateAvailable();
                }

            } catch (PackageManager.NameNotFoundException e) {
                listener.onError("Could not get current version");
            }
        }
    }
}