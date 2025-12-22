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
    private final com.omarflex5.data.local.dao.MediaSourceDao mediaSourceDao;
    private final com.omarflex5.data.local.dao.SeasonDao seasonDao; // Added
    private final com.omarflex5.data.local.dao.EpisodeDao episodeDao; // Added
    private final FirestoreSyncManager firestoreSyncManager;
    private final ExecutorService executorService;

    // Last sync timestamp (in-memory for now, should be in Prefs)
    private long lastSyncedTimestamp = 0;

    private MediaRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.mediaDao = db.mediaDao();
        this.userMediaStateDao = db.userMediaStateDao();
        this.mediaSourceDao = db.mediaSourceDao();
        this.seasonDao = db.seasonDao();
        this.episodeDao = db.episodeDao();
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

    public void getMediaById(long mediaId, com.omarflex5.data.source.DataSourceCallback<MediaEntity> callback) {
        executorService.execute(() -> {
            MediaEntity media = mediaDao.getById(mediaId);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (media != null) {
                    callback.onSuccess(media);
                } else {
                    callback.onError(new Exception("Media not found"));
                }
            });
        });
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

    public void setFavorite(long mediaId, boolean favorite) {
        new Thread(() -> {
            // Guard: Skip if mediaId is invalid
            if (mediaId <= 0) {
                android.util.Log.w("MediaRepository", "Skipping setFavorite - invalid mediaId: " + mediaId);
                return;
            }

            UserMediaStateEntity state = userMediaStateDao.getStateForMediaSync(mediaId);
            if (state == null) {
                state = new UserMediaStateEntity();
                state.setMediaId(mediaId);
            }
            state.setFavorite(favorite);
            state.setLastWatchedAt(System.currentTimeMillis());
            state.setUpdatedAt(System.currentTimeMillis());
            userMediaStateDao.insertOrUpdate(state);
        }).start();
    }

    /**
     * Retrieves media items that the user has started watching, ordered by recency.
     * Filtered to only include top-level media (MediaEntity).
     */
    public LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getContinueWatching(int limit) {
        return mediaDao.getContinueWatchingLiveData(limit);
    }

    /**
     * Gets the last watched episode for a specific series.
     */
    public UserMediaStateEntity getLastWatchedEpisodeSync(long mediaId) {
        return userMediaStateDao.getLastWatchedEpisodeSync(mediaId);
    }

    /**
     * Resolves watch state based on hierarchy.
     */
    public UserMediaStateEntity getWatchStateSync(long mediaId, Long seasonId, Long episodeId) {
        if (episodeId != null && episodeId != -1) {
            return userMediaStateDao.getStateForEpisodeSync(episodeId);
        } else if (seasonId != null && seasonId != -1) {
            return userMediaStateDao.getStateForSeasonSync(seasonId);
        } else {
            return userMediaStateDao.getStateForMediaSync(mediaId);
        }
    }

    public com.omarflex5.data.local.entity.MediaSourceEntity getSourceForMediaAndServerSync(long mediaId,
            long serverId) {
        return mediaSourceDao.findByMediaAndServer(mediaId, serverId);
    }

    public void updateMedia(MediaEntity media) {
        new Thread(() -> mediaDao.update(media)).start();
    }

    /**
     * Attaches watch history to a list of parsed items.
     */
    public void attachWatchHistory(List<com.omarflex5.data.scraper.BaseHtmlParser.ParsedItem> items,
            long seriesId, Long currentSeasonId, Long episodeId) {
        if (items == null || items.isEmpty())
            return;

        for (com.omarflex5.data.scraper.BaseHtmlParser.ParsedItem item : items) {
            item.setMediaId(seriesId);
            UserMediaStateEntity state = null;

            if (item.getType() == com.omarflex5.data.local.entity.MediaType.EPISODE) {
                // 1. Try by Episode number if we have the season
                if (currentSeasonId != null && item.getEpisodeNumber() != null) {
                    com.omarflex5.data.local.entity.EpisodeEntity ep = episodeDao
                            .getBySeasonIdAndNumber(currentSeasonId, item.getEpisodeNumber());
                    if (ep != null) {
                        item.setEpisodeId(ep.getId());
                        item.setSeasonId(currentSeasonId);
                        state = userMediaStateDao.getStateForEpisodeSync(ep.getId());
                    }
                }
            } else if (item.getType() == com.omarflex5.data.local.entity.MediaType.SEASON) {
                // 2. Try by Season number
                if (item.getSeasonNumber() != null) {
                    com.omarflex5.data.local.entity.SeasonEntity s = seasonDao.getByMediaIdAndNumber(seriesId,
                            item.getSeasonNumber());
                    if (s != null) {
                        item.setSeasonId(s.getId());
                        state = userMediaStateDao.getStateForSeasonSync(s.getId());
                    }
                }
            } else if (item.getType() == com.omarflex5.data.local.entity.MediaType.FILM ||
                    item.getType() == com.omarflex5.data.local.entity.MediaType.SERIES) {

                // CHECK: Is this actually a SERVER? (parsed as FILM but might be child of
                // Episode/Movie)
                boolean isServer = (episodeId != null)
                        || (items.size() > 1 && items.get(0).getTitle().contains("Server"));

                if (isServer && episodeId != null) {
                    // CASE: Server for an Episode -> Inherit Episode Progress
                    state = userMediaStateDao.getStateForEpisodeSync(episodeId);
                } else if (isServer) {
                    // CASE: Server for a Movie -> Inherit Movie Progress
                    state = userMediaStateDao.getStateForMediaSync(seriesId);
                } else {
                    // CASE: Actual Movie or Series Item
                    state = userMediaStateDao.getStateForMediaSync(seriesId);
                }
            }

            if (state != null) {
                item.setWatchProgress(state.getWatchProgress());
                item.setDuration(state.getDuration());
                item.setWatched(state.isWatched());
                item.setLastWatchedAt(state.getLastWatchedAt());

                // Ensure IDs are propagated
                if (state.getSeasonId() != null)
                    item.setSeasonId(state.getSeasonId());
                if (state.getEpisodeId() != null)
                    item.setEpisodeId(state.getEpisodeId());
            }
        }
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

            if (localMedia == null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onError(new Exception("Media not found")));
                return;
            }

            if (localMedia.getTmdbId() == null) {
                // If it was previously inherited but not saved correctly yet, it might still
                // have trailerUrl
                if (localMedia.getTrailerUrl() != null && !localMedia.getTrailerUrl().isEmpty()) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onSuccess(localMedia.getTrailerUrl()));
                    return;
                }
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onSuccess(null)); // SILENT FAIL
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

    /**
     * Updates watch progress for an episode or film and propagates updates upward.
     * 
     * @param mediaId   The ID of the MediaEntity (Series or Film)
     * @param seasonId  Null for films, actual ID for episodes
     * @param episodeId Null for films, actual ID for episodes
     * @param progress  Current watched time in ms
     * @param duration  Total duration in ms
     */
    public void updateWatchProgress(long mediaId, Long seasonId, Long episodeId, long progress, long duration,
            Long serverId, String sourceUrl) {
        executorService.execute(() -> {
            try {
                // Guard: Skip if mediaId is invalid (item wasn't properly synced to DB)
                if (mediaId <= 0) {
                    android.util.Log.w("MediaRepository", "Skipping updateWatchProgress - invalid mediaId: " + mediaId);
                    return;
                }

                long now = System.currentTimeMillis();
                boolean isFinished = progress > (duration * 0.9); // Assume finished if > 90%

                android.util.Log.d("WATCH_PROGRESS", "=== updateWatchProgress called ===");
                android.util.Log.d("WATCH_PROGRESS", "  mediaId=" + mediaId + ", serverId=" + serverId);
                android.util.Log.d("WATCH_PROGRESS", "  progress=" + progress + ", duration=" + duration);
                android.util.Log.d("WATCH_PROGRESS", "  seasonId=" + seasonId + ", episodeId=" + episodeId);

                // 1. Update Episode or Film State
                UserMediaStateEntity itemState = null;
                if (episodeId != null) {
                    itemState = userMediaStateDao.getStateForEpisodeSync(episodeId);
                    if (itemState == null) {
                        itemState = new UserMediaStateEntity();
                        itemState.setMediaId(mediaId);
                        itemState.setSeasonId(seasonId);
                        itemState.setEpisodeId(episodeId);
                    }
                } else {
                    itemState = userMediaStateDao.getStateForMediaSync(mediaId);
                    if (itemState == null) {
                        itemState = new UserMediaStateEntity();
                        itemState.setMediaId(mediaId);
                    }
                }

                itemState.setWatchProgress(progress);
                itemState.setDuration(duration);
                itemState.setWatched(isFinished);
                itemState.setLastWatchedAt(now);
                itemState.setUpdatedAt(now);

                // Save routing info
                if (serverId != null)
                    itemState.setLastSourceServerId(serverId);
                if (sourceUrl != null && !sourceUrl.isEmpty()) {
                    // Normalize URL if needed, but usually we want to keep what worked
                    itemState.setLastSourceUrl(sourceUrl);
                }

                userMediaStateDao.insertOrUpdate(itemState);

                // 2. Propagate Upwards if Series
                if (episodeId != null && seasonId != null) {
                    // --- Update Season Progress ---
                    int totalEpInSeason = episodeDao.getEpisodeCountForSeasonSync(seasonId);
                    int watchedEpInSeason = userMediaStateDao.getWatchedCountForSeasonSync(seasonId);

                    UserMediaStateEntity seasonState = userMediaStateDao.getStateForSeasonSync(seasonId);
                    if (seasonState == null) {
                        seasonState = new UserMediaStateEntity();
                        seasonState.setMediaId(mediaId);
                        seasonState.setSeasonId(seasonId);
                    }

                    // Set season percentage
                    if (totalEpInSeason > 0) {
                        long percent = (watchedEpInSeason * 100L) / totalEpInSeason;
                        // Hack: Ensure at least 1% if any episode is in progress/watched
                        if (percent == 0)
                            percent = 1;
                        seasonState.setWatchProgress(percent);
                        seasonState.setDuration(100);
                    }
                    seasonState.setLastWatchedAt(now);
                    seasonState.setUpdatedAt(now);
                    userMediaStateDao.insertOrUpdate(seasonState);

                    // --- Update Series Progress ---
                    int totalEpInSeries = episodeDao.getEpisodeCountForMediaSync(mediaId);
                    int watchedEpInSeries = userMediaStateDao.getWatchedCountForMediaSync(mediaId);

                    UserMediaStateEntity seriesState = userMediaStateDao.getStateForMediaSync(mediaId);
                    if (seriesState == null) {
                        seriesState = new UserMediaStateEntity();
                        seriesState.setMediaId(mediaId);
                    }

                    // Set series percentage
                    if (totalEpInSeries > 0) {
                        long percent = (watchedEpInSeries * 100L) / totalEpInSeries;
                        // Hack: Ensure at least 1% if any episode is in progress/watched
                        if (percent == 0)
                            percent = 1;
                        seriesState.setWatchProgress(percent);
                        seriesState.setDuration(100);
                    }
                    seriesState.setLastWatchedAt(now);
                    seriesState.setUpdatedAt(now);

                    // Propagate routing info to Series
                    if (serverId != null)
                        seriesState.setLastSourceServerId(serverId);
                    if (sourceUrl != null && !sourceUrl.isEmpty()) {
                        seriesState.setLastSourceUrl(sourceUrl);
                    }

                    userMediaStateDao.insertOrUpdate(seriesState);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ========== AGGRESSIVE SEARCH SYNC ==========

    /**
     * Synchronizes ParsedItems from search results with the Local DB.
     * Strategy:
     * 1. Normalize items' URL (strip domain).
     * 2. Check overlap with MediaSourceEntity.
     * 3. If Exists -> Populate transient watch progress fields on ParsedItem.
     * 4. If New -> Create MediaEntity + MediaSourceEntity (Aggressive Persist).
     * 
     * @param items    List of parsed items (modified in-place).
     * @param serverId ID of the server these results came from.
     */
    public void syncSearchResults(List<com.omarflex5.data.scraper.BaseHtmlParser.ParsedItem> items, long serverId) {
        if (items == null || items.isEmpty())
            return;

        // This should run on background, but we assume caller handles threading since
        // it modifies list in-place
        // before UI submission.

        // Ideally pass DAO in constructor, but for now we follow pattern.
        // Wait, getInstance needs context. The singleton INSTANCE already has context.
        // We can just use member fields if we add them.

        for (com.omarflex5.data.scraper.BaseHtmlParser.ParsedItem item : items) {
            try {
                String fullUrl = item.getPageUrl();
                String normalizedUrl = com.omarflex5.util.UrlHelper.normalize(fullUrl);

                if (normalizedUrl == null || normalizedUrl.isEmpty()) {
                    android.util.Log.e("SYNC", "Skipping source for " + item.getTitle() + " - Empty URL");
                    continue;
                }

                // 1. Check DB for this source
                com.omarflex5.data.local.entity.MediaSourceEntity existingSource = mediaSourceDao
                        .findByExternalUrlAndServer(normalizedUrl, serverId);

                if (existingSource != null) {
                    // Match found! Get watch history.
                    Long mediaId = existingSource.getMediaId();

                    android.util.Log.d("SYNC", "EXISTING source found for " + item.getTitle() +
                            " - mediaId=" + mediaId + " (URL=" + normalizedUrl + ")");

                    if (mediaId != null) {
                        item.setMediaId(mediaId);

                        // ENRICH: If record exists, update it with CURRENT scraper metadata
                        // BUT: Skip enrichment for TMDB entities to keep them pristine
                        MediaEntity existingMedia = mediaDao.getById(mediaId);
                        if (existingMedia != null) {
                            boolean isTmdbEntity = existingMedia.getTmdbId() != null && existingMedia.getTmdbId() > 0;

                            if (!isTmdbEntity) {
                                // Only update source-specific entities, not TMDB
                                existingMedia.setPrimaryServerId(serverId);

                                if (enrichMediaFromItem(existingMedia, item)) {
                                    try {
                                        mediaDao.update(existingMedia);
                                    } catch (android.database.sqlite.SQLiteConstraintException e) {
                                        android.util.Log.w("SYNC",
                                                "Skipping update due to tmdbId conflict: " + e.getMessage());
                                    }
                                }
                            } else {
                                android.util.Log.d("SYNC",
                                        "Skipping enrichment for TMDB entity: " + existingMedia.getTitle());
                            }
                        }

                        UserMediaStateEntity state = userMediaStateDao.getStateForMediaSync(mediaId);
                        if (state != null) {
                            item.setWatched(state.isWatched());
                            item.setWatchProgress(state.getWatchProgress());
                            item.setDuration(state.getDuration());
                        }
                    }

                } else {
                    // Not found by URL -> Always create SOURCE-SPECIFIC MediaEntity
                    MediaEntity newMedia = new MediaEntity();
                    newMedia.setTitle(item.getTitle());
                    newMedia.setOriginalTitle(item.getOriginalTitle());
                    newMedia.setPosterUrl(item.getPosterUrl());
                    newMedia.setType(item.getType());
                    newMedia.setYear(item.getYear());
                    newMedia.setCreatedAt(System.currentTimeMillis());
                    newMedia.setUpdatedAt(System.currentTimeMillis());
                    newMedia.setCategoriesJson(new org.json.JSONArray(item.getCategories()).toString());
                    newMedia.setPrimaryServerId(serverId);

                    if (item.getTmdbId() != null && item.getTmdbId() > 0) {
                        MediaEntity tmdbEntity = mediaDao.getByTmdbId(item.getTmdbId());
                        if (tmdbEntity != null && tmdbEntity.getType() == item.getType()) {
                            inheritTmdbMetadata(newMedia, tmdbEntity);
                        }
                    }

                    long newId = mediaDao.insert(newMedia);
                    item.setMediaId(newId);

                    // Link Source
                    com.omarflex5.data.local.entity.MediaSourceEntity newSource = new com.omarflex5.data.local.entity.MediaSourceEntity();
                    newSource.setMediaId(newId);
                    newSource.setServerId(serverId);
                    newSource.setExternalUrl(normalizedUrl);
                    newSource.setCreatedAt(System.currentTimeMillis());
                    newSource.setUpdatedAt(System.currentTimeMillis());
                    mediaSourceDao.insert(newSource);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Synchronizes Sub-Items (Seasons/Episodes) to DB to ensure they have IDs
     * BEFORE attaching watch history.
     */
    public void syncSubItems(long mediaId, Long seasonId,
            List<com.omarflex5.data.scraper.BaseHtmlParser.ParsedItem> items) {
        if (items == null || items.isEmpty())
            return;

        for (com.omarflex5.data.scraper.BaseHtmlParser.ParsedItem item : items) {
            try {
                if (item.getType() == com.omarflex5.data.local.entity.MediaType.SEASON) {
                    if (item.getSeasonNumber() != null) {
                        com.omarflex5.data.local.entity.SeasonEntity s = seasonDao.getByMediaIdAndNumber(mediaId,
                                item.getSeasonNumber());
                        if (s == null) {
                            s = new com.omarflex5.data.local.entity.SeasonEntity();
                            s.setMediaId(mediaId);
                            s.setSeasonNumber(item.getSeasonNumber());
                            s.setTitle(item.getTitle());
                            s.setPosterUrl(item.getPosterUrl());
                            s.setId(seasonDao.insert(s));
                        }
                        item.setSeasonId(s.getId());
                        item.setMediaId(mediaId);
                    }
                } else if (item.getType() == com.omarflex5.data.local.entity.MediaType.EPISODE) {
                    if (seasonId != null && item.getEpisodeNumber() != null) {
                        com.omarflex5.data.local.entity.EpisodeEntity ep = episodeDao.getBySeasonIdAndNumber(seasonId,
                                item.getEpisodeNumber());
                        if (ep == null) {
                            ep = new com.omarflex5.data.local.entity.EpisodeEntity();
                            ep.setSeasonId(seasonId);
                            ep.setEpisodeNumber(item.getEpisodeNumber());
                            ep.setTitle(item.getTitle());
                            ep.setStillUrl(item.getPosterUrl()); // Use poster if available
                            ep.setId(episodeDao.insert(ep));
                        }
                        item.setEpisodeId(ep.getId());
                        item.setSeasonId(seasonId);
                        item.setMediaId(mediaId);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper to populate MediaEntity from ParsedItem metadata if missing.
     * Returns true if any field was changed.
     */
    private boolean enrichMediaFromItem(MediaEntity entity, com.omarflex5.data.scraper.BaseHtmlParser.ParsedItem item) {
        boolean changed = false;

        // TMDB PROTECTION: If entity has TMDB ID, it has "premium" metadata. Don't
        // overwrite unless current is empty.
        boolean hasTmdbData = entity.getTmdbId() != null && entity.getTmdbId() > 0;
        boolean isTmdbEntity = hasTmdbData;

        // TITLE: Only update if new title is cleaner (shorter, no site suffixes)
        if (item.getTitle() != null && !item.getTitle().isEmpty()) {
            String currentTitle = entity.getTitle();
            String newTitle = item.getTitle();

            // Skip if new title has common site suffixes (messy)
            boolean newIsMessy = newTitle.contains(" - ") || newTitle.contains("فاصل") ||
                    newTitle.contains("ArabSeed") || newTitle.contains("أكوام");

            if (currentTitle == null || currentTitle.isEmpty()) {
                entity.setTitle(newTitle);
                changed = true;
            } else if (!newIsMessy && newTitle.length() < currentTitle.length() && !newTitle.equals(currentTitle)) {
                // Only update if new title is cleaner (shorter and not messy)
                entity.setTitle(newTitle);
                changed = true;
            }
        }

        if (item.getPosterUrl() != null && !item.getPosterUrl().isEmpty()) {
            if (!item.getPosterUrl().equals(entity.getPosterUrl())) {
                entity.setPosterUrl(item.getPosterUrl());
                changed = true;
            }
        }

        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            if (entity.getDescription() == null || entity.getDescription().length() < item.getDescription().length()) {
                entity.setDescription(item.getDescription());
                changed = true;
            }
        }

        // RATING: If TMDB data exists, only update if current is empty/zero
        if (item.getRating() != null && item.getRating() > 0) {
            if (entity.getRating() == null || entity.getRating() == 0) {
                entity.setRating(item.getRating());
                changed = true;
            } else if (!hasTmdbData && Math.abs(entity.getRating() - item.getRating()) > 0.1) {
                // Only overwrite non-TMDB ratings
                entity.setRating(item.getRating());
                changed = true;
            }
        }

        // YEAR: If TMDB data exists, only update if current is empty/zero
        if (item.getYear() != null && item.getYear() > 0) {
            if (entity.getYear() == null || entity.getYear() == 0) {
                entity.setYear(item.getYear());
                changed = true;
            } else if (!hasTmdbData && !entity.getYear().equals(item.getYear())) {
                entity.setYear(item.getYear());
                changed = true;
            }
        }

        if (entity.getBackdropUrl() == null || entity.getBackdropUrl().isEmpty()) {
            if (item.getBackdropUrl() != null && !item.getBackdropUrl().isEmpty()) {
                entity.setBackdropUrl(item.getBackdropUrl());
                changed = true;
            }
        }

        // CATEGORIES: If TMDB data exists, only update if current is empty
        if (item.getCategories() != null && !item.getCategories().isEmpty()) {
            String newCats = new org.json.JSONArray(item.getCategories()).toString();
            boolean currentCategoriesEmpty = entity.getCategoriesJson() == null
                    || entity.getCategoriesJson().length() <= 2;

            if (currentCategoriesEmpty) {
                entity.setCategoriesJson(newCats);
                changed = true;
            } else if (!hasTmdbData && !entity.getCategoriesJson().contains(item.getCategories().get(0))) {
                // Only overwrite non-TMDB categories if new ones are different
                entity.setCategoriesJson(newCats);
                changed = true;
            }
        }

        if (item.getTrailerUrl() != null && !item.getTrailerUrl().isEmpty()) {
            if (entity.getTrailerUrl() == null || !entity.getTrailerUrl().equals(item.getTrailerUrl())) {
                entity.setTrailerUrl(item.getTrailerUrl());
                changed = true;
            }
        }

        // AUTO-MARK ENRICHED: If we have a significant description, it's enriched
        if (entity.getDescription() != null && entity.getDescription().length() > 20 && !entity.isEnriched()) {
            entity.setEnriched(true);
            entity.setEnrichedAt(System.currentTimeMillis());
            changed = true;
        }

        // DO NOT copy TMDB ID here if we are enriching a source-specific entity
        // Source-specific entities must keep tmdbId = NULL to be distinguished from
        // TMDB entities
        // Only valid if we are upgrading a TMDB entity itself (which we shouldn't do
        // here anyway)
        if (isTmdbEntity && item.getTmdbId() != null) {
            // Already a TMDB entity, so it's fine
        } else if (!isTmdbEntity && item.getTmdbId() != null) {
            // SKIP setting tmdbId for source entities!
            // They inherit metadata but NOT identity
        }

        if (changed) {
            entity.setUpdatedAt(System.currentTimeMillis());
        }

        return changed;
    }

    /**
     * Copies display metadata from a TMDB entity to a source-specific entity.
     * This ensures rich display without modifying the original TMDB data.
     * 
     * @param target     The source-specific entity being created
     * @param tmdbSource The original TMDB entity to inherit from
     */
    private void inheritTmdbMetadata(MediaEntity target, MediaEntity tmdbSource) {
        // Copy poster and backdrop (prioritize TMDB quality)
        if (target.getPosterUrl() == null || target.getPosterUrl().isEmpty()) {
            target.setPosterUrl(tmdbSource.getPosterUrl());
        }
        if (target.getBackdropUrl() == null || target.getBackdropUrl().isEmpty()) {
            target.setBackdropUrl(tmdbSource.getBackdropUrl());
        }

        // Copy description if richer
        if (tmdbSource.getDescription() != null && !tmdbSource.getDescription().isEmpty()) {
            if (target.getDescription() == null ||
                    target.getDescription().length() < tmdbSource.getDescription().length()) {
                target.setDescription(tmdbSource.getDescription());
            }
        }

        // Copy categories
        if (target.getCategoriesJson() == null || target.getCategoriesJson().length() <= 2) {
            target.setCategoriesJson(tmdbSource.getCategoriesJson());
        }

        // Copy rating (TMDB is more reliable)
        if (tmdbSource.getRating() != null && tmdbSource.getRating() > 0) {
            target.setRating(tmdbSource.getRating());
        }

        // Copy year if missing
        if (target.getYear() == null || target.getYear() == 0) {
            target.setYear(tmdbSource.getYear());
        }

        // Copy release date
        if (target.getReleaseDate() == null || target.getReleaseDate().isEmpty()) {
            target.setReleaseDate(tmdbSource.getReleaseDate());
        }

        // Copy trailer URL
        if (tmdbSource.getTrailerUrl() != null && !tmdbSource.getTrailerUrl().isEmpty()) {
            target.setTrailerUrl(tmdbSource.getTrailerUrl());
        }

        // Copy language
        if (target.getOriginalLanguage() == null || target.getOriginalLanguage().isEmpty()) {
            target.setOriginalLanguage(tmdbSource.getOriginalLanguage());
        }

        // Keep original scraper title (do NOT copy title from TMDB)
        // This allows Continue Watching to show the source-specific title
    }
}
