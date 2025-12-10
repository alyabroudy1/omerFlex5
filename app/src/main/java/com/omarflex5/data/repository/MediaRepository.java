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
}
