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
                startForeground(NOTIFICATION_ID, createNotification(0));
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
            if (outputFile.exists()) {
                downloadedBytes = outputFile.length();
            }

            Request.Builder requestBuilder = new Request.Builder().url(url);
            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=" + downloadedBytes + "-");
            }

            try {
                Response response = client.newCall(requestBuilder.build()).execute();
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

                long totalBytes = response.body().contentLength();
                if (totalBytes == -1)
                    totalBytes = 0;
                // If we are resuming, the total is the remaining bytes + already downloaded
                // If server returns 206, content-length is the chunk size.
                // We need to handle total size carefully.
                // For simplicity, if constructing from scratch, total is content-length.
                // If resuming, total to show in UI is chunk + existing.

                long totalFileSize = totalBytes + downloadedBytes;

                InputStream is = response.body().byteStream();
                FileOutputStream fos = new FileOutputStream(outputFile, true); // Append mode

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
                        int progress = (int) ((currentBytes * 100) / totalFileSize);
                        updateNotification(progress);
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

    private Notification createNotification(int progress) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading Update")
                .setContentText(progress + "%")
                .setSmallIcon(R.drawable.ic_settings) // Placeholder icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, progress, false)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(int progress) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createNotification(progress));
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
