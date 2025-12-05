package com.omarflex5.data.repository;

import android.util.Log;

import com.omarflex5.data.remote.TmdbCacheService;
import com.omarflex5.data.source.remote.BaseServer;
import com.omarflex5.data.source.remote.TmdbApi;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for TMDB data with cache-first strategy.
 * 
 * Flow:
 * 1. Check Firestore cache
 * 2. If cache hit and not expired → return cached data
 * 3. If cache miss → fetch from TMDB API → cache result → return
 */
public class TmdbRepository {

    private static final String TAG = "TmdbRepository";
    private static volatile TmdbRepository INSTANCE;

    private final TmdbCacheService cacheService;
    private final TmdbApi tmdbApi;

    private TmdbRepository() {
        cacheService = TmdbCacheService.getInstance();
        tmdbApi = BaseServer.getClient().create(TmdbApi.class);
    }

    public static TmdbRepository getInstance() {
        if (INSTANCE == null) {
            synchronized (TmdbRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TmdbRepository();
                }
            }
        }
        return INSTANCE;
    }

    // ==================== MOVIE DETAILS ====================

    /**
     * Get movie details with cache-first strategy.
     */
    public void getMovieDetails(int tmdbId, TmdbCallback<Map<String, Object>> callback) {
        // First, check cache
        cacheService.getMovie(tmdbId, new TmdbCacheService.CacheCallback<Map<String, Object>>() {
            @Override
            public void onCacheHit(Map<String, Object> data) {
                callback.onSuccess(data);
            }

            @Override
            public void onCacheMiss() {
                // Fetch from API
                fetchMovieFromApi(tmdbId, callback);
            }
        });
    }

    private void fetchMovieFromApi(int tmdbId, TmdbCallback<Map<String, Object>> callback) {
        tmdbApi.getMovieDetails(tmdbId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> movieData = response.body();

                    // Cache the result
                    cacheService.cacheMovie(tmdbId, movieData);

                    callback.onSuccess(movieData);
                } else {
                    callback.onError("Failed to fetch movie: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "API error: " + t.getMessage());
                callback.onError(t.getMessage());
            }
        });
    }

    // ==================== TV SHOW DETAILS ====================

    /**
     * Get TV show details with cache-first strategy.
     */
    public void getTvShowDetails(int tmdbId, TmdbCallback<Map<String, Object>> callback) {
        // First, check cache
        cacheService.getTvShow(tmdbId, new TmdbCacheService.CacheCallback<Map<String, Object>>() {
            @Override
            public void onCacheHit(Map<String, Object> data) {
                callback.onSuccess(data);
            }

            @Override
            public void onCacheMiss() {
                // Fetch from API
                fetchTvShowFromApi(tmdbId, callback);
            }
        });
    }

    private void fetchTvShowFromApi(int tmdbId, TmdbCallback<Map<String, Object>> callback) {
        tmdbApi.getTvShowDetails(tmdbId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> tvData = response.body();

                    // Cache the result
                    cacheService.cacheTvShow(tmdbId, tvData);

                    callback.onSuccess(tvData);
                } else {
                    callback.onError("Failed to fetch TV show: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "API error: " + t.getMessage());
                callback.onError(t.getMessage());
            }
        });
    }

    // ==================== SEARCH ====================

    /**
     * Search TMDB with cache-first strategy.
     */
    public void searchMulti(String query, TmdbCallback<Map<String, Object>> callback) {
        // First, check cache
        cacheService.getSearchResults(query, new TmdbCacheService.CacheCallback<Map<String, Object>>() {
            @Override
            public void onCacheHit(Map<String, Object> data) {
                callback.onSuccess(data);
            }

            @Override
            public void onCacheMiss() {
                // Fetch from API
                searchFromApi(query, callback);
            }
        });
    }

    private void searchFromApi(String query, TmdbCallback<Map<String, Object>> callback) {
        tmdbApi.searchMulti(query).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> searchData = response.body();

                    // Cache the result (extract results list)
                    Object results = searchData.get("results");
                    if (results instanceof java.util.List) {
                        // noinspection unchecked
                        cacheService.cacheSearchResults(query, (java.util.List<Map<String, Object>>) results);
                    }

                    callback.onSuccess(searchData);
                } else {
                    callback.onError("Search failed: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "Search API error: " + t.getMessage());
                callback.onError(t.getMessage());
            }
        });
    }

    // ==================== ENRICH MEDIA ====================

    /**
     * Enrich local media entity with TMDB data.
     * Used to fill in missing metadata like posters, descriptions, etc.
     */
    public void enrichMediaByTitle(String title, int year, boolean isTvShow,
            TmdbCallback<Map<String, Object>> callback) {

        String searchQuery = title + (year > 0 ? " " + year : "");

        searchMulti(searchQuery, new TmdbCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                Object results = data.get("results");
                if (results instanceof java.util.List) {
                    // noinspection unchecked
                    java.util.List<Map<String, Object>> resultList = (java.util.List<Map<String, Object>>) results;

                    // Find best match
                    for (Map<String, Object> result : resultList) {
                        String mediaType = (String) result.get("media_type");
                        boolean isMatch = isTvShow ? "tv".equals(mediaType) : "movie".equals(mediaType);

                        if (isMatch) {
                            // Get full details
                            Number idNum = (Number) result.get("id");
                            int tmdbId = idNum != null ? idNum.intValue() : 0;

                            if (tmdbId > 0) {
                                if (isTvShow) {
                                    getTvShowDetails(tmdbId, callback);
                                } else {
                                    getMovieDetails(tmdbId, callback);
                                }
                                return;
                            }
                        }
                    }
                }
                callback.onError("No matching content found");
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    // ==================== CALLBACK INTERFACE ====================

    public interface TmdbCallback<T> {
        void onSuccess(T data);

        void onError(String message);
    }
}
