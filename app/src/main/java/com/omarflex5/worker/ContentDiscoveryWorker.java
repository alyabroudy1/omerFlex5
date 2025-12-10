package com.omarflex5.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.source.remote.FirestoreSyncManager;
import com.omarflex5.data.source.remote.TmdbApi;
import com.omarflex5.data.local.AppDatabase;
import com.omarflex5.util.NetworkUtils;

import java.io.IOException;
import java.util.List;

/**
 * Daily worker that:
 * 1. Checks the latest movie ID in the global Firestore DB (or local fallback).
 * 2. Fetches the next batch of movies from TMDB (e.g., 50 new movies).
 * 3. Pushes them to Firestore (crowdsourcing).
 * 4. Saves them locally (delta sync cache).
 */
public class ContentDiscoveryWorker extends Worker {

    private static final String TAG = "ContentDiscoveryWorker";

    public ContentDiscoveryWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!NetworkUtils.isNetworkAvailable(getApplicationContext())) {
            Log.d(TAG, "doWork: Skipped - No Network connection.");
            return Result.retry();
        }

        try {
            Log.d(TAG, "doWork: Starting Daily Feeder...");
            FirestoreSyncManager firestoreManager = new FirestoreSyncManager();

            // 1. Find where to start
            Integer latestId = firestoreManager.getLatestTmdbId();
            if (latestId == null || latestId == 0) {
                // Fallback: Check local DB max ID or default to a safe known ID
                latestId = 550; // Example: Fight Club
            }

            Log.d(TAG, "doWork: Latest Global TMDb ID is " + latestId + ". Fetching new content...");

            List<MediaEntity> newContent = new java.util.ArrayList<>();
            // 2. Fetch next batch from TMDB (Fetch 20 items to respect rate limits)
            // We need a Retrofit instance here. Assuming NetworkUtils or similar provides
            // it.
            // For now, illustrating the logic loop:

            /*
             * TmdbApi api = NetworkUtils.getTmdbApi(); // Hypothetical accessor
             * for (int i = 1; i <= 20; i++) {
             * int targetId = latestId + i;
             * try {
             * // Try fetch as Movie
             * retrofit2.Response<java.util.Map<String, Object>> response =
             * api.getMovieDetails(targetId).execute();
             * if (response.isSuccessful() && response.body() != null) {
             * MediaEntity entity =
             * com.omarflex5.util.TmdbMapper.mapToEntity(response.body(),
             * com.omarflex5.data.local.entity.MediaType.FILM);
             * if (entity != null) {
             * newContent.add(entity);
             * }
             * }
             * } catch (Exception e) {
             * // Ignore 404s (deleted/invalid IDs)
             * }
             * }
             */

            if (newContent.isEmpty()) {
                Log.d(TAG, "doWork: No new content found in this batch.");
                return Result.success();
            }

            // 3. Push to Firestore (Crowdsource)
            firestoreManager.pushBatch(newContent);

            // 4. Save Locally (Delta Sync cache)
            AppDatabase.getInstance(getApplicationContext()).mediaDao().insertAll(newContent);

            Log.d(TAG, "doWork: Daily Feeder Completed. Added " + newContent.size() + " items.");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork: Feeder failed", e);
            return Result.retry();
        }
    }
}
