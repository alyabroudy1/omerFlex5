package com.omarflex5.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.local.entity.MediaType;

import java.util.List;

@Dao
public interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MediaEntity media);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<MediaEntity> mediaList);

    @Update
    void update(MediaEntity media);

    @Delete
    void delete(MediaEntity media);

    @Query("SELECT * FROM media WHERE id = :id")
    MediaEntity getById(long id);

    @Query("SELECT * FROM media WHERE id = :id")
    LiveData<MediaEntity> getByIdLive(long id);

    @Query("SELECT * FROM media WHERE tmdbId = :tmdbId")
    MediaEntity getByTmdbId(int tmdbId);

    @Query("SELECT * FROM media WHERE tmdbId = :tmdbId")
    LiveData<MediaEntity> getMediaByTmdbId(int tmdbId);

    @androidx.room.Transaction
    @Query("SELECT * FROM media")
    List<com.omarflex5.data.local.model.MediaWithUserState> getAllMediaWithState();

    @androidx.room.Transaction
    @Query("SELECT * FROM media ORDER BY CASE WHEN releaseDate IS NULL THEN 1 ELSE 0 END, releaseDate DESC, id DESC LIMIT :limit")
    LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getAllMediaWithStateLiveData(int limit);

    @androidx.room.Transaction
    @Query("SELECT * FROM media ORDER BY CASE WHEN releaseDate IS NULL THEN 1 ELSE 0 END, releaseDate DESC, id DESC")
    LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getAllMediaWithStateLiveData();

    @Query("SELECT MAX(tmdbId) FROM media")
    Integer getMaxTmdbId();

    @Query("SELECT * FROM media ORDER BY updatedAt DESC")
    LiveData<List<MediaEntity>> getAllMedia();

    @Query("SELECT * FROM media WHERE type = :type ORDER BY updatedAt DESC")
    LiveData<List<MediaEntity>> getAllByType(MediaType type);

    @Query("SELECT * FROM media WHERE title LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%'")
    List<MediaEntity> search(String query);

    @Query("SELECT * FROM media WHERE isEnriched = 0 LIMIT :limit")
    List<MediaEntity> getUnenriched(int limit);

    @Query("SELECT * FROM media ORDER BY updatedAt DESC LIMIT :limit")
    LiveData<List<MediaEntity>> getRecent(int limit);

    @Query("DELETE FROM media WHERE id = :id")
    void deleteById(long id);

    @Query("UPDATE media SET isEnriched = 1, enrichedAt = :timestamp WHERE id = :id")
    void markEnriched(long id, long timestamp);

    // Genre-based filtering for categories
    @androidx.room.Transaction
    @Query("SELECT * FROM media WHERE categoriesJson LIKE '%' || :genre || '%' ORDER BY CASE WHEN releaseDate IS NULL THEN 1 ELSE 0 END, releaseDate DESC, id DESC LIMIT :limit")
    LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getMediaByGenreLiveData(String genre, int limit);

    // Language-based filtering for Arabic category
    @androidx.room.Transaction
    @Query("SELECT * FROM media WHERE originalLanguage = :language ORDER BY CASE WHEN releaseDate IS NULL THEN 1 ELSE 0 END, releaseDate DESC, id DESC LIMIT :limit")
    LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> getMediaByLanguageLiveData(String language,
            int limit);
}
