package com.omarflex5.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.omarflex5.R;
import com.omarflex5.util.UpdateManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadService extends Service {

    public static final String ACTION_DOWNLOAD_PROGRESS = "action_download_progress";
    public static final String ACTION_DOWNLOAD_COMPLETE = "action_download_complete";
    public static final String ACTION_DOWNLOAD_ERROR = "action_download_error";
    public static final String EXTRA_PROGRESS = "extra_progress";
    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_ERROR = "extra_error";
    public static final String EXTRA_URL = "extra_url";

    private static final String CHANNEL_ID = "download_channel";
    private static final int NOTIFICATION_ID = 100;
    private ExecutorService executorService;
    private OkHttpClient client;
    private boolean isDownloading = false;

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        client = new OkHttpClient();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String url = intent.getStringExtra(EXTRA_URL);
            if (url != null && !isDownloading) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, createNotification(0, true),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(0, true));
                }
                downloadFile(url);
            }
        }
        return START_NOT_STICKY;
    }

    private void downloadFile(String url) {
        isDownloading = true;
        executorService.execute(() -> {
            File outputFile = new File(getExternalFilesDir(null), "update.apk");
            long downloadedBytes = 0;
            // Force delete to avoid corruption issues for now - disabling resume
            // optimization
            if (outputFile.exists()) {
                outputFile.delete();
            }

            Request.Builder requestBuilder = new Request.Builder().url(url);
            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=" + downloadedBytes + "-");
            }

            try {
                Response response = client.newCall(requestBuilder.build()).execute();
                // LOGGING FOR DEBUGGING
                android.util.Log.d("DownloadService", "Response URL: " + response.request().url());
                android.util.Log.d("DownloadService", "Content-Type: " + response.header("Content-Type"));
                android.util.Log.d("DownloadService", "Content-Length: " + response.header("Content-Length"));
                android.util.Log.d("DownloadService", "Response Code: " + response.code());

                if (!response.isSuccessful()) {
                    // If Range not satisfiable (e.g. file changed or completed), retry from scratch
                    if (response.code() == 416) {
                        outputFile.delete();
                        downloadedBytes = 0;
                        response = client.newCall(new Request.Builder().url(url).build()).execute();
                    }
                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);
                }

                // Check if server actually honored the range request
                boolean isResume = response.code() == 206;
                if (!isResume) {
                    // Server returned 200 OK (full file), so we must overwrite, not append
                    downloadedBytes = 0;
                }

                long totalBytes = response.body().contentLength();
                if (totalBytes == -1)
                    totalBytes = 0;

                // If isResume (206), content-length is just the chunk. Total = chunk +
                // existing.
                // If not isResume (200), content-length is full file. Total = full file.
                long totalFileSize = totalBytes + downloadedBytes;

                InputStream is = response.body().byteStream();
                FileOutputStream fos = new FileOutputStream(outputFile, isResume); // Append only if HTTP 206

                byte[] buffer = new byte[8192];
                int read;
                long currentBytes = downloadedBytes;

                // Throttle notification updates
                long lastUpdateTime = 0;

                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    currentBytes += read;

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime > 500 || currentBytes == totalFileSize) {
                        int progress = 0;
                        if (totalFileSize > 0) {
                            progress = (int) ((currentBytes * 100) / totalFileSize);
                        }
                        updateNotification(progress, totalFileSize <= 0);
                        broadcastProgress(progress);
                        lastUpdateTime = currentTime;
                    }
                }

                fos.close();
                is.close();
                response.close();

                broadcastComplete(outputFile.getAbsolutePath());
                // Trigger install immediately if possible or notify user
                // For now, stop service, activity will handle install via broadcast
                stopSelf();

            } catch (Exception e) {
                e.printStackTrace();
                broadcastError(e.getMessage());
                stopSelf();
            } finally {
                isDownloading = false;
            }
        });
    }

    private void broadcastProgress(int progress) {
        Intent intent = new Intent(ACTION_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS, progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastComplete(String path) {
        Intent intent = new Intent(ACTION_DOWNLOAD_COMPLETE);
        intent.putExtra(EXTRA_FILE_PATH, path);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastError(String error) {
        Intent intent = new Intent(ACTION_DOWNLOAD_ERROR);
        intent.putExtra(EXTRA_ERROR, error);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void updateNotification(int progress, boolean indeterminate) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification(progress, indeterminate));
    }

    private Notification createNotification(int progress, boolean indeterminate) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading Update")
                .setContentText(indeterminate ? "Downloading..." : progress + "%")
                .setSmallIcon(R.drawable.ic_settings) // Placeholder icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, progress, indeterminate)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
