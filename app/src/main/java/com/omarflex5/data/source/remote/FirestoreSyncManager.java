package com.omarflex5.data.source.remote;

import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.omarflex5.data.local.entity.MediaEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Manages synchronization between Local Room DB and Global Firestore DB.
 * Implements "Delta Sync" and "Crowdsourced Feeder" patterns.
 */
public class FirestoreSyncManager {

    private static final String TAG = "FirestoreSyncManager";
    private static final String COLLECTION_MEDIA = "media";

    private final FirebaseFirestore db;
    private final CollectionReference mediaCollection;

    public FirestoreSyncManager() {
        this.db = FirebaseFirestore.getInstance();
        this.mediaCollection = db.collection(COLLECTION_MEDIA);
    }

    /**
     * DELTA SYNC: Fetch all media items updated since the given timestamp.
     *
     * @param lastSyncedTimestamp The timestamp of the last successful sync.
     * @return List of MediaEntity objects from Firestore.
     */
    public List<MediaEntity> syncDown(long lastSyncedTimestamp) throws ExecutionException, InterruptedException {
        Log.d(TAG, "syncDown: Fetching updates since " + lastSyncedTimestamp);

        Query query = mediaCollection
                .whereGreaterThan("updatedAt", lastSyncedTimestamp)
                .orderBy("updatedAt")
                .limit(500); // Batch limit to avoid memory issues

        // Synchronous wait for Worker context
        Task<QuerySnapshot> task = query.get();
        QuerySnapshot snapshot = Tasks.await(task);

        List<MediaEntity> updates = new ArrayList<>();
        if (snapshot != null) {
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                MediaEntity entity = doc.toObject(MediaEntity.class);
                if (entity != null) {
                    updates.add(entity);
                }
            }
        }
        Log.d(TAG, "syncDown: Fetched " + updates.size() + " updates.");
        return updates;
    }

    /**
     * FEEDER: Push a batch of new media items to Firestore.
     * Used by the daily "Crowd-Crawl" worker.
     *
     * @param mediaList List of new movies/series found from TMDB.
     */
    public void pushBatch(List<MediaEntity> mediaList) throws ExecutionException, InterruptedException {
        if (mediaList == null || mediaList.isEmpty())
            return;

        Log.d(TAG, "pushBatch: Pushing " + mediaList.size() + " items to Global DB.");

        // Firestore batches are limited to 500 ops
        final int BATCH_SIZE = 450;
        for (int i = 0; i < mediaList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, mediaList.size());
            List<MediaEntity> batchList = mediaList.subList(i, end);

            WriteBatch batch = db.batch();
            for (MediaEntity media : batchList) {
                // Use tmdbId as document ID for easy deduplication
                String docId = "tmdb_" + media.getTmdbId();
                if (media.getTmdbId() == null)
                    continue; // Skip invalid

                // We merge to avoid overwriting newer data if exists
                batch.set(mediaCollection.document(docId), media, SetOptions.merge());
            }

            Tasks.await(batch.commit());
            Log.d(TAG, "pushBatch: Committed batch " + (i / BATCH_SIZE + 1));
        }
    }

    /**
     * Get the latest TMDB ID in the global database.
     * Used to know where to start scraping for new content.
     */
    public Integer getLatestTmdbId() throws ExecutionException, InterruptedException {
        Query query = mediaCollection
                .orderBy("tmdbId", Query.Direction.DESCENDING)
                .limit(1);

        QuerySnapshot snapshot = Tasks.await(query.get());
        if (!snapshot.isEmpty()) {
            MediaEntity latest = snapshot.getDocuments().get(0).toObject(MediaEntity.class);
            return latest != null ? latest.getTmdbId() : 0;
        }
        return 0;
    }

    public String getEarliestReleaseDate() throws ExecutionException, InterruptedException {
        Query query = mediaCollection
                .whereNotEqualTo("releaseDate", null) // Filter out nulls
                .orderBy("releaseDate", Query.Direction.ASCENDING)
                .limit(1);

        QuerySnapshot snapshot = Tasks.await(query.get());
        if (!snapshot.isEmpty()) {
            MediaEntity item = snapshot.getDocuments().get(0).toObject(MediaEntity.class);
            return item != null ? item.getReleaseDate() : null;
        }
        return null;
    }

    public String getLatestReleaseDate() throws ExecutionException, InterruptedException {
        Query query = mediaCollection
                .orderBy("releaseDate", Query.Direction.DESCENDING)
                .limit(1);

        QuerySnapshot snapshot = Tasks.await(query.get());
        if (!snapshot.isEmpty()) {
            MediaEntity item = snapshot.getDocuments().get(0).toObject(MediaEntity.class);
            return item != null ? item.getReleaseDate() : null;
        }
        return null;
    }
}
