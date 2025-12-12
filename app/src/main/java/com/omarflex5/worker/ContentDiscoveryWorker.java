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

            TmdbApi api = com.omarflex5.data.source.remote.BaseServer.getClient().create(TmdbApi.class);
            com.omarflex5.data.local.dao.MediaDao mediaDao = AppDatabase.getInstance(getApplicationContext())
                    .mediaDao();

            // STRATEGY: Bi-Directional Frontier Expansion (Movies & TV)
            // 1. Get Global Boundaries
            String minDate = firestoreManager.getEarliestReleaseDate();
            String maxDate = firestoreManager.getLatestReleaseDate();

            // Defaults if DB is empty
            if (minDate == null)
                minDate = "2023-01-01";
            if (maxDate == null)
                maxDate = "2023-12-01";

            Log.d(TAG, "Global Range: " + minDate + " to " + maxDate);

            List<MediaEntity> newContent = new java.util.ArrayList<>();

            // 2. Fetch 4 Batches (30 Future, 30 Past for Movies & TV)

            // --- Movies Future (30) ---
            fetchAndSave(api.discoverMoviesByDateRange(maxDate, null, "primary_release_date.asc", 50),
                    mediaDao, newContent, com.omarflex5.data.local.entity.MediaType.FILM, "Movie (Future)");

            // --- Movies Past (30) ---
            fetchAndSave(api.discoverMoviesByDateRange(null, minDate, "primary_release_date.desc", 100),
                    mediaDao, newContent, com.omarflex5.data.local.entity.MediaType.FILM, "Movie (Past)");

            // --- TV Future (30) ---
            fetchAndSave(api.discoverTvByDateRange(maxDate, null, "first_air_date.asc", 50),
                    mediaDao, newContent, com.omarflex5.data.local.entity.MediaType.SERIES, "TV (Future)");

            // --- TV Past (30) ---
            fetchAndSave(api.discoverTvByDateRange(null, minDate, "first_air_date.desc", 50),
                    mediaDao, newContent, com.omarflex5.data.local.entity.MediaType.SERIES, "TV (Past)");

            if (newContent.isEmpty()) {
                Log.d(TAG, "doWork: No new content found in this batch.");
                return Result.success();
            }

            // 3. Push to Firestore (Crowdsource)
            try {
                firestoreManager.pushBatch(newContent);
            } catch (Exception e) {
                Log.e(TAG, "doWork: Failed to push to Firestore (skipping)", e);
            }

            Log.d(TAG, "doWork: Expansion Completed. Added " + newContent.size() + " items.");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "doWork: Feeder failed", e);
            return Result.retry();
        }
    }

    private void fetchAndSave(retrofit2.Call<com.omarflex5.data.model.tmdb.TmdbMovieResponse> call,
            com.omarflex5.data.local.dao.MediaDao mediaDao,
            List<MediaEntity> newContent,
            com.omarflex5.data.local.entity.MediaType type,
            String label) {
        try {
            retrofit2.Response<com.omarflex5.data.model.tmdb.TmdbMovieResponse> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                List<com.omarflex5.data.model.tmdb.TmdbMovie> results = response.body().getResults();
                if (results != null) {
                    int count = 0;
                    for (com.omarflex5.data.model.tmdb.TmdbMovie item : results) {
                        if (count >= 30)
                            break; // Limit to 30 per batch

                        MediaEntity entity = com.omarflex5.util.TmdbMapper.mapFromPojo(item, type);
                        if (entity != null) {
                            mediaDao.insert(entity);
                            newContent.add(entity);
                            count++;
                        }
                    }
                    Log.d(TAG, "Saved " + count + " " + label + " items.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching " + label, e);
        }
    }
}
