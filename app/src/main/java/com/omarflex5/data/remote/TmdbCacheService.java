package com.omarflex5.data.remote;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Firebase Firestore cache for TMDB data.
 * 
 * Reduces TMDB API calls by caching:
 * - Movie details
 * - TV show details
 * - Search results
 * 
 * Cache is shared across all app users.
 */
public class TmdbCacheService {

    private static final String TAG = "TmdbCacheService";
    private static volatile TmdbCacheService INSTANCE;

    // Firestore collections
    private static final String COLLECTION_MOVIES = "tmdb_movies";
    private static final String COLLECTION_TV = "tmdb_tv";
    private static final String COLLECTION_SEARCH = "tmdb_search";

    // Cache expiration (30 days in milliseconds)
    private static final long CACHE_EXPIRY_MS = TimeUnit.DAYS.toMillis(30);
    private static final long SEARCH_CACHE_EXPIRY_MS = TimeUnit.DAYS.toMillis(7);

    /**
     * Cache version - bump this to invalidate all cached data.
     * v1 = initial cache
     * v2 = added original_title for English search
     */
    private static final int CACHE_VERSION = 2;
    private static final String CACHE_VERSION_KEY = "cache_version";

    private final FirebaseFirestore db;

    private TmdbCacheService() {
        db = FirebaseFirestore.getInstance();
    }

    public static TmdbCacheService getInstance() {
        if (INSTANCE == null) {
            synchronized (TmdbCacheService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TmdbCacheService();
                }
            }
        }
        return INSTANCE;
    }

    // ==================== MOVIE CACHE ====================

    /**
     * Get cached movie data by TMDB ID.
     */
    public void getMovie(int tmdbId, CacheCallback<Map<String, Object>> callback) {
        String docId = String.valueOf(tmdbId);

        db.collection(COLLECTION_MOVIES)
                .document(docId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && !isExpired(document)) {
                        Log.d(TAG, "Movie cache hit: " + tmdbId);
                        callback.onCacheHit(document.getData());
                    } else {
                        Log.d(TAG, "Movie cache miss: " + tmdbId);
                        callback.onCacheMiss();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Movie cache error: " + e.getMessage());
                    callback.onCacheMiss(); // Treat errors as cache miss
                });
    }

    /**
     * Cache movie data.
     */
    public void cacheMovie(int tmdbId, Map<String, Object> movieData) {
        String docId = String.valueOf(tmdbId);

        Map<String, Object> cacheData = new HashMap<>(movieData);
        cacheData.put("cached_at", System.currentTimeMillis());
        cacheData.put("tmdb_id", tmdbId);

        db.collection(COLLECTION_MOVIES)
                .document(docId)
                .set(cacheData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Movie cached: " + tmdbId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to cache movie: " + e.getMessage()));
    }

    // ==================== TV SHOW CACHE ====================

    /**
     * Get cached TV show data by TMDB ID.
     */
    public void getTvShow(int tmdbId, CacheCallback<Map<String, Object>> callback) {
        String docId = String.valueOf(tmdbId);

        db.collection(COLLECTION_TV)
                .document(docId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && !isExpired(document)) {
                        Log.d(TAG, "TV cache hit: " + tmdbId);
                        callback.onCacheHit(document.getData());
                    } else {
                        Log.d(TAG, "TV cache miss: " + tmdbId);
                        callback.onCacheMiss();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "TV cache error: " + e.getMessage());
                    callback.onCacheMiss();
                });
    }

    /**
     * Cache TV show data.
     */
    public void cacheTvShow(int tmdbId, Map<String, Object> tvData) {
        String docId = String.valueOf(tmdbId);

        Map<String, Object> cacheData = new HashMap<>(tvData);
        cacheData.put("cached_at", System.currentTimeMillis());
        cacheData.put("tmdb_id", tmdbId);

        db.collection(COLLECTION_TV)
                .document(docId)
                .set(cacheData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "TV show cached: " + tmdbId))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to cache TV show: " + e.getMessage()));
    }

    /**
     * Get cached search results.
     */
    public void getSearchResults(String query, CacheCallback<Map<String, Object>> callback) {
        String docId = normalizeQuery(query);

        db.collection(COLLECTION_SEARCH)
                .document(docId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists() && !isSearchExpired(document) && isValidCacheVersion(document)) {
                        Log.d(TAG, "Search cache hit: " + query);
                        callback.onCacheHit(document.getData());
                    } else {
                        if (document.exists() && !isValidCacheVersion(document)) {
                            Log.d(TAG, "Search cache version mismatch (v" + document.getLong(CACHE_VERSION_KEY)
                                    + " != v" + CACHE_VERSION + "): " + query);
                        }
                        Log.d(TAG, "Search cache miss: " + query);
                        callback.onCacheMiss();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Search cache error: " + e.getMessage());
                    callback.onCacheMiss();
                });
    }

    /**
     * Cache search results.
     */
    public void cacheSearchResults(String query, List<Map<String, Object>> results) {
        String docId = normalizeQuery(query);

        Map<String, Object> cacheData = new HashMap<>();
        cacheData.put("query", query);
        cacheData.put("results", results);
        cacheData.put("result_count", results.size());
        cacheData.put("cached_at", System.currentTimeMillis());
        cacheData.put(CACHE_VERSION_KEY, CACHE_VERSION); // Store version for future invalidation

        db.collection(COLLECTION_SEARCH)
                .document(docId)
                .set(cacheData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Search cached (v" + CACHE_VERSION + "): " + query))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to cache search: " + e.getMessage()));
    }

    // ==================== SERVER CONFIG CACHE ====================

    /**
     * Sync server configurations from Firestore.
     * This allows updating server URLs remotely.
     */
    public void getServerConfigs(CacheCallback<List<Map<String, Object>>> callback) {
        db.collection("server_configs")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        java.util.ArrayList<Map<String, Object>> configs = new java.util.ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Map<String, Object> config = doc.getData();
                            if (config != null) {
                                config.put("name", doc.getId());
                                configs.add(config);
                            }
                        }
                        callback.onCacheHit(configs);
                    } else {
                        callback.onCacheMiss();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get server configs: " + e.getMessage());
                    callback.onCacheMiss();
                });
    }

    /**
     * Update a server config in Firestore.
     */
    public void updateServerConfig(String serverName, Map<String, Object> config) {
        config.put("updated_at", System.currentTimeMillis());

        db.collection("server_configs")
                .document(serverName)
                .set(config, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Server config updated: " + serverName))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update server config: " + e.getMessage()));
    }

    // ==================== HELPER METHODS ====================

    private boolean isExpired(DocumentSnapshot document) {
        Long cachedAt = document.getLong("cached_at");
        if (cachedAt == null)
            return true;
        return System.currentTimeMillis() - cachedAt > CACHE_EXPIRY_MS;
    }

    private boolean isSearchExpired(DocumentSnapshot document) {
        Long cachedAt = document.getLong("cached_at");
        if (cachedAt == null)
            return true;
        return System.currentTimeMillis() - cachedAt > SEARCH_CACHE_EXPIRY_MS;
    }

    /**
     * Check if cached document has valid version.
     * Old cache entries without version or with older version are invalid.
     */
    private boolean isValidCacheVersion(DocumentSnapshot document) {
        Long version = document.getLong(CACHE_VERSION_KEY);
        if (version == null) {
            return false; // Old cache without version
        }
        return version.intValue() >= CACHE_VERSION;
    }

    private String normalizeQuery(String query) {
        // Create a valid Firestore document ID from query
        return query.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\u0600-\\u06FF]", "_") // Keep Arabic chars
                .replaceAll("_+", "_");
    }

    // ==================== CALLBACK INTERFACE ====================

    public interface CacheCallback<T> {
        void onCacheHit(T data);

        void onCacheMiss();
    }
}
