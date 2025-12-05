package com.omarflex5.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.omarflex5.data.local.AppDatabase;
import com.omarflex5.data.local.dao.EpisodeDao;
import com.omarflex5.data.local.dao.MediaDao;
import com.omarflex5.data.local.dao.MediaSourceDao;
import com.omarflex5.data.local.dao.SeasonDao;
import com.omarflex5.data.local.entity.EpisodeEntity;
import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.local.entity.MediaSourceEntity;
import com.omarflex5.data.local.entity.MediaType;
import com.omarflex5.data.local.entity.ProcessingState;
import com.omarflex5.data.local.entity.SeasonEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for media content (films, series, seasons, episodes).
 * Handles local database operations and coordinates with remote sources.
 */
public class MediaLocalRepository {

    private static volatile MediaLocalRepository INSTANCE;

    private final MediaDao mediaDao;
    private final SeasonDao seasonDao;
    private final EpisodeDao episodeDao;
    private final MediaSourceDao mediaSourceDao;
    private final ExecutorService executor;

    private MediaLocalRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        mediaDao = db.mediaDao();
        seasonDao = db.seasonDao();
        episodeDao = db.episodeDao();
        mediaSourceDao = db.mediaSourceDao();
        executor = Executors.newFixedThreadPool(4);
    }

    public static MediaLocalRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MediaLocalRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MediaLocalRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // ==================== MEDIA ====================

    public void insertMedia(MediaEntity media, OnResultCallback<Long> callback) {
        executor.execute(() -> {
            media.setCreatedAt(System.currentTimeMillis());
            media.setUpdatedAt(System.currentTimeMillis());
            if (media.getState() == null) {
                media.setState(ProcessingState.NEW);
            }
            long id = mediaDao.insert(media);
            if (callback != null) {
                callback.onResult(id);
            }
        });
    }

    public void updateMedia(MediaEntity media) {
        executor.execute(() -> {
            media.setUpdatedAt(System.currentTimeMillis());
            mediaDao.update(media);
        });
    }

    public void getMediaById(long id, OnResultCallback<MediaEntity> callback) {
        executor.execute(() -> {
            MediaEntity media = mediaDao.getById(id);
            callback.onResult(media);
        });
    }

    public LiveData<MediaEntity> getMediaByIdLive(long id) {
        return mediaDao.getByIdLive(id);
    }

    public void getMediaByTmdbId(int tmdbId, OnResultCallback<MediaEntity> callback) {
        executor.execute(() -> {
            MediaEntity media = mediaDao.getByTmdbId(tmdbId);
            callback.onResult(media);
        });
    }

    public LiveData<List<MediaEntity>> getAllFilms() {
        return mediaDao.getAllByType(MediaType.FILM);
    }

    public LiveData<List<MediaEntity>> getAllSeries() {
        return mediaDao.getAllByType(MediaType.SERIES);
    }

    public LiveData<List<MediaEntity>> getRecentMedia(int limit) {
        return mediaDao.getRecent(limit);
    }

    public void searchMedia(String query, OnResultCallback<List<MediaEntity>> callback) {
        executor.execute(() -> {
            List<MediaEntity> results = mediaDao.search(query);
            callback.onResult(results);
        });
    }

    public void markMediaEnriched(long mediaId) {
        executor.execute(() -> {
            mediaDao.markEnriched(mediaId, System.currentTimeMillis());
        });
    }

    public void getUnenrichedMedia(int limit, OnResultCallback<List<MediaEntity>> callback) {
        executor.execute(() -> {
            List<MediaEntity> results = mediaDao.getUnenriched(limit);
            callback.onResult(results);
        });
    }

    // ==================== SEASONS ====================

    public void insertSeason(SeasonEntity season, OnResultCallback<Long> callback) {
        executor.execute(() -> {
            season.setCreatedAt(System.currentTimeMillis());
            season.setUpdatedAt(System.currentTimeMillis());
            if (season.getState() == null) {
                season.setState(ProcessingState.NEW);
            }
            long id = seasonDao.insert(season);
            if (callback != null) {
                callback.onResult(id);
            }
        });
    }

    public void insertSeasons(List<SeasonEntity> seasons) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            for (SeasonEntity season : seasons) {
                season.setCreatedAt(now);
                season.setUpdatedAt(now);
                if (season.getState() == null) {
                    season.setState(ProcessingState.NEW);
                }
            }
            seasonDao.insertAll(seasons);
        });
    }

    public LiveData<List<SeasonEntity>> getSeasonsByMediaId(long mediaId) {
        return seasonDao.getByMediaIdLive(mediaId);
    }

    public void getSeasonByNumber(long mediaId, int seasonNumber, OnResultCallback<SeasonEntity> callback) {
        executor.execute(() -> {
            SeasonEntity season = seasonDao.getByMediaIdAndNumber(mediaId, seasonNumber);
            callback.onResult(season);
        });
    }

    // ==================== EPISODES ====================

    public void insertEpisode(EpisodeEntity episode, OnResultCallback<Long> callback) {
        executor.execute(() -> {
            episode.setCreatedAt(System.currentTimeMillis());
            episode.setUpdatedAt(System.currentTimeMillis());
            if (episode.getState() == null) {
                episode.setState(ProcessingState.NEW);
            }
            long id = episodeDao.insert(episode);
            if (callback != null) {
                callback.onResult(id);
            }
        });
    }

    public void insertEpisodes(List<EpisodeEntity> episodes) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            for (EpisodeEntity episode : episodes) {
                episode.setCreatedAt(now);
                episode.setUpdatedAt(now);
                if (episode.getState() == null) {
                    episode.setState(ProcessingState.NEW);
                }
            }
            episodeDao.insertAll(episodes);
        });
    }

    public LiveData<List<EpisodeEntity>> getEpisodesBySeasonId(long seasonId) {
        return episodeDao.getBySeasonIdLive(seasonId);
    }

    public void getEpisodeByNumber(long seasonId, int episodeNumber, OnResultCallback<EpisodeEntity> callback) {
        executor.execute(() -> {
            EpisodeEntity episode = episodeDao.getBySeasonIdAndNumber(seasonId, episodeNumber);
            callback.onResult(episode);
        });
    }

    // ==================== MEDIA SOURCES ====================

    public void insertMediaSource(MediaSourceEntity source, OnResultCallback<Long> callback) {
        executor.execute(() -> {
            source.setCreatedAt(System.currentTimeMillis());
            source.setUpdatedAt(System.currentTimeMillis());
            long id = mediaSourceDao.insert(source);
            if (callback != null) {
                callback.onResult(id);
            }
        });
    }

    public LiveData<List<MediaSourceEntity>> getSourcesForMedia(long mediaId) {
        return mediaSourceDao.getByMediaIdLive(mediaId);
    }

    public LiveData<List<MediaSourceEntity>> getSourcesForEpisode(long episodeId) {
        return mediaSourceDao.getByEpisodeIdLive(episodeId);
    }

    public void getSourcesByMatchKey(String matchKey, OnResultCallback<List<MediaSourceEntity>> callback) {
        executor.execute(() -> {
            List<MediaSourceEntity> sources = mediaSourceDao.getByMatchKey(matchKey);
            callback.onResult(sources);
        });
    }

    // ==================== CALLBACK INTERFACE ====================

    public interface OnResultCallback<T> {
        void onResult(T result);
    }
}
