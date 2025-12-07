package com.omarflex5.temp.omerflex.service.update;

import android.content.Context;
import android.util.Log;


import com.omerflex.service.utils.NetworkUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadManager {
    private static final String TAG = "DownloadManager";

    public interface DownloadListener {
        void onProgress(int progress);
        void onComplete(File apkFile);
        void onError(String error);
        void onCancelled();
    }

    private Context context;
    private DownloadListener listener;
    private OkHttpClient client;
    private volatile boolean isCancelled = false;
    private File downloadFile;
    private File tempFile;

    public DownloadManager(Context context, DownloadListener listener) {
        this.context = context;
        this.listener = listener;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Constants.DOWNLOAD_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(Constants.DOWNLOAD_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(Constants.DOWNLOAD_TIMEOUT, TimeUnit.MILLISECONDS)
                .build();
    }

    public void downloadUpdate(UpdateInfo updateInfo) {
        if (!NetworkUtils.isNetworkAvailable()) {
            listener.onError("No network connection available");
            return;
        }

        if (!StorageUtils.isEnoughSpace(updateInfo.getApkSize())) {
            listener.onError("Not enough storage space");
            return;
        }

        // Clean up old files
        StorageUtils.cleanupOldFiles(context);

        downloadFile = StorageUtils.getApkFile(context);
        tempFile = new File(downloadFile.getAbsolutePath() + ".tmp");

        new DownloadTask(updateInfo).start();
    }

    public void cancelDownload() {
        isCancelled = true;
        listener.onCancelled();
    }

    private class DownloadTask extends Thread {
        private UpdateInfo updateInfo;

        public DownloadTask(UpdateInfo updateInfo) {
            this.updateInfo = updateInfo;
        }

        @Override
        public void run() {
            try {
                // Check if we can resume previous download
                long downloadedBytes = 0;
                if (tempFile.exists()) {
                    downloadedBytes = tempFile.length();
                }

                Request request = new Request.Builder()
                        .url(updateInfo.getApkUrl())
                        .addHeader("Range", "bytes=" + downloadedBytes + "-")
                        .build();

                Response response = client.newCall(request).execute();

                if (!response.isSuccessful() && response.code() != 206) {
                    listener.onError("Download failed: HTTP " + response.code());
                    return;
                }

                long totalBytes = downloadedBytes + response.body().contentLength();

                InputStream inputStream = response.body().byteStream();
                RandomAccessFile outputFile = new RandomAccessFile(tempFile, "rw");
                outputFile.seek(downloadedBytes);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = downloadedBytes;

                while ((bytesRead = inputStream.read(buffer)) != -1 && !isCancelled) {
                    outputFile.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    int progress = (int) ((totalRead * 100) / totalBytes);
                    listener.onProgress(progress);
                }

                outputFile.close();
                inputStream.close();

                if (isCancelled) {
                    tempFile.delete();
                    return;
                }

                // Verify checksum
                if (!verifyChecksum(tempFile, updateInfo.getApkChecksum())) {
                    tempFile.delete();
                    listener.onError("File integrity check failed");
                    return;
                }

                // Move temp file to final location
                tempFile.renameTo(downloadFile);

                listener.onComplete(downloadFile);

            } catch (IOException e) {
                Log.e(TAG, "Download error", e);
                listener.onError("Download error: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
                listener.onError("Unexpected error: " + e.getMessage());
            }
        }

        private boolean verifyChecksum(File file, String expectedChecksum) {
            if (expectedChecksum == null || expectedChecksum.isEmpty()) {
                Log.w(TAG, "Checksum not provided, skipping verification.");
                return true;
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                InputStream fis = new java.io.FileInputStream(file);

                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }

                fis.close();

                byte[] hashBytes = digest.digest();
                StringBuilder sb = new StringBuilder();

                for (byte b : hashBytes) {
                    sb.append(String.format("%02x", b));
                }

                return sb.toString().equalsIgnoreCase(expectedChecksum);

            } catch (Exception e) {
                Log.e(TAG, "Checksum verification error", e);
                return false;
            }
        }
    }
}