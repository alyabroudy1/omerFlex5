package com.omarflex5.data.repository;

import android.content.Context;
import androidx.lifecycle.LiveData;

import com.omarflex5.data.local.AppDatabase;
import com.omarflex5.data.local.dao.MediaDao;
import com.omarflex5.data.local.dao.UserMediaStateDao;
import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.local.entity.UserMediaStateEntity;
import com.omarflex5.data.source.remote.FirestoreSyncManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single source of truth for Media data.
 * Adheres to "Clone & Feed" strategy:
 * 1. Reads primarily from Local Room DB.
 * 2. Background Syncs with Firestore.
 */
public class MediaRepository {

    private static volatile MediaRepository INSTANCE;

    private final MediaDao mediaDao;
    private final UserMediaStateDao userMediaStateDao;
    private final FirestoreSyncManager firestoreSyncManager;
    private final ExecutorService executorService;

    // Last sync timestamp (in-memory for now, should be in Prefs)
    private long lastSyncedTimestamp = 0;

    private MediaRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.mediaDao = db.mediaDao();
        this.userMediaStateDao = db.userMediaStateDao();
        this.firestoreSyncManager = new FirestoreSyncManager();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public static MediaRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MediaRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MediaRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // ========== READ LOCAL (UI) ==========

    public LiveData<MediaEntity> getMediaById(int tmdbId) {
        return mediaDao.getMediaByTmdbId(tmdbId);
    }

    public LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getAllMedia() {
        return mediaDao.getAllMediaWithStateLiveData();
    }

    public LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getPagedMedia(
            androidx.lifecycle.MutableLiveData<Integer> limit) {
        return androidx.lifecycle.Transformations.switchMap(limit, mediaDao::getAllMediaWithStateLiveData);
    }

    // Get media filtered by genre with pagination
    public LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getMediaByGenre(
            String genre, androidx.lifecycle.MutableLiveData<Integer> limit) {
        return androidx.lifecycle.Transformations.switchMap(limit, l -> mediaDao.getMediaByGenreLiveData(genre, l));
    }

    // Get media filtered by language with pagination
    public LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getMediaByLanguage(
            String language, androidx.lifecycle.MutableLiveData<Integer> limit) {
        return androidx.lifecycle.Transformations.switchMap(limit,
                l -> mediaDao.getMediaByLanguageLiveData(language, l));
    }

    public LiveData<UserMediaStateEntity> getUserState(long mediaId) {
        return userMediaStateDao.getStateForMedia(mediaId);
    }

    // ========== WRITE (User Actions) ==========

    public void setFavorite(long mediaId, boolean isFavorite) {
        executorService.execute(() -> {
            UserMediaStateEntity state = userMediaStateDao.getStateForMediaSync(mediaId);
            if (state == null) {
                state = new UserMediaStateEntity();
                state.setMediaId(mediaId);
            }
            state.setFavorite(isFavorite);
            state.setUpdatedAt(System.currentTimeMillis());
            userMediaStateDao.insertOrUpdate(state);
        });
    }

    // ========== SYNC (Background) ==========

    /**
     * Triggers a Delta Sync from Firestore.
     * Should be called on app startup or periodically.
     */
    /**
     * Triggers a Delta Sync from Firestore.
     * Should be called on app startup or periodically.
     */
    public void syncFromGlobal() {
        executorService.execute(() -> {
            try {
                // Fetch updates
                List<MediaEntity> updates = firestoreSyncManager.syncDown(lastSyncedTimestamp);

                if (!updates.isEmpty()) {
                    // Update Local DB
                    mediaDao.insertAll(updates);

                    // Update timestamp
                    // In real app, save max(updatedAt) to SharedPreferences
                    lastSyncedTimestamp = System.currentTimeMillis();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Gets Trailer URL.
     * Strategy:
     * 1. Check Local DB.
     * 2. If missing, Fetch from API.
     * 3. Cache result locally and globally.
     */
    public void getTrailerUrl(Context context, long mediaId,
            com.omarflex5.data.source.DataSourceCallback<String> callback) {
        executorService.execute(() -> {
            // 1. Check Local Cache
            MediaEntity localMedia = mediaDao.getById(mediaId);
            if (localMedia != null && localMedia.getTrailerUrl() != null && !localMedia.getTrailerUrl().isEmpty()) {
                // HIT: Return cached
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onSuccess(localMedia.getTrailerUrl()));
                return;
            }

            // 2. Fetch from API (TMDB)
            // Using a temporary direct lookup or relying on existing infrastructure?
            // The previous HomeViewModel implementation used TmdbRepository directly.
            // Ideally we should route through a unified path, but for now we'll mimic the
            // fetch logic
            // inside the repository or delegate.
            // Since we don't have TmdbRepository instance here, we might need to pass the
            // logic or refactor.
            // However, to keep it simple as requested, let's assume the caller uses a
            // fetcher and we provide the "getOrFetch" logic.

            // To properly fix this based on user request "save the call... make fetch only
            // if no trailers",
            // the ViewModel should call THIS method.
            // So we need to perform the API call HERE if cache misses.

            String tmdbKey = "15d2ea6d0dc1d476efbca3eba2b9bbfb"; // Ideally from config
            // Need to make network call.
            // Simplest way is using OkHttp directly or existing service.
            // We can use the existing TmdbService if available or just raw json parsing to
            // save time/complexity.

            // Let's use the TmdbApiService if we can find it, or manual fetch for
            // robustness.
            // Actually, `HomeViewModel` was likely doing this.
            // Let's implement the fetch using a helper or assume a callback from UI?
            // No, Repos should handle data.

            if (localMedia == null || localMedia.getTmdbId() == null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onError(new Exception("Invalid Media")));
                return;
            }

            int tmdbId = localMedia.getTmdbId();
            String type = localMedia.getType() == com.omarflex5.data.local.entity.MediaType.SERIES ? "tv" : "movie";

            // Quick fetch implementation using basic HttpURLConnection to avoid dependency
            // complexities
            try {
                java.net.URL url = new java.net.URL("https://api.themoviedb.org/3/" + type + "/" + tmdbId
                        + "/videos?api_key=" + tmdbKey + "&language=en-US");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        response.append(line);
                    reader.close();

                    org.json.JSONObject json = new org.json.JSONObject(response.toString());
                    org.json.JSONArray results = json.getJSONArray("results");
                    String trailerKey = null;

                    for (int i = 0; i < results.length(); i++) {
                        org.json.JSONObject video = results.getJSONObject(i);
                        if ("Trailer".equals(video.getString("type")) && "YouTube".equals(video.getString("site"))) {
                            trailerKey = video.getString("key");
                            break;
                        }
                    }

                    if (trailerKey != null) {
                        String trailerUrl = "https://www.youtube.com/watch?v=" + trailerKey;

                        // 3. Update Cache (Local & Global)
                        mediaDao.updateTrailerUrl(mediaId, trailerUrl);
                        firestoreSyncManager.updateTrailer(tmdbId, trailerUrl);

                        // Return result
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> callback.onSuccess(trailerUrl));
                    } else {
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> callback.onError(new Exception("No trailer found")));
                    }
                } else {
                    final int code = conn.getResponseCode();
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onError(new Exception("API Error: " + code)));
                }

            } catch (Exception e) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(e));
            }
        });
    }
}
