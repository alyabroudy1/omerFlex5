package com.omarflex5.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.omarflex5.data.local.entity.MediaSourceEntity;

import java.util.List;

@Dao
public interface MediaSourceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MediaSourceEntity source);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<MediaSourceEntity> sources);

    @Update
    void update(MediaSourceEntity source);

    @Delete
    void delete(MediaSourceEntity source);

    @Query("SELECT * FROM media_sources WHERE id = :id")
    MediaSourceEntity getById(long id);

    // Get sources for a film
    @Query("SELECT * FROM media_sources WHERE mediaId = :mediaId AND seasonId IS NULL AND episodeId IS NULL")
    List<MediaSourceEntity> getByMediaId(long mediaId);

    @Query("SELECT * FROM media_sources WHERE mediaId = :mediaId AND seasonId IS NULL AND episodeId IS NULL")
    LiveData<List<MediaSourceEntity>> getByMediaIdLive(long mediaId);

    // Get sources for a season
    @Query("SELECT * FROM media_sources WHERE seasonId = :seasonId AND episodeId IS NULL")
    List<MediaSourceEntity> getBySeasonId(long seasonId);

    // Get sources for an episode
    @Query("SELECT * FROM media_sources WHERE episodeId = :episodeId")
    List<MediaSourceEntity> getByEpisodeId(long episodeId);

    @Query("SELECT * FROM media_sources WHERE episodeId = :episodeId")
    LiveData<List<MediaSourceEntity>> getByEpisodeIdLive(long episodeId);

    // Find by match key for deduplication
    @Query("SELECT * FROM media_sources WHERE matchKey = :matchKey")
    List<MediaSourceEntity> getByMatchKey(String matchKey);

    // Get sources from a specific server
    @Query("SELECT * FROM media_sources WHERE serverId = :serverId")
    List<MediaSourceEntity> getByServerId(long serverId);

    // Get available sources for media
    @Query("SELECT * FROM media_sources WHERE mediaId = :mediaId AND isAvailable = 1")
    List<MediaSourceEntity> getAvailableByMediaId(long mediaId);

    // Count sources per server
    @Query("SELECT COUNT(*) FROM media_sources WHERE serverId = :serverId")
    int getCountByServerId(long serverId);

    @Query("DELETE FROM media_sources WHERE serverId = :serverId")
    void deleteByServerId(long serverId);

    // Find by External URL (Relative Path) and Server ID
    // Used for "Aggressive Search Sync" to link search results to existing DB items
    @Query("SELECT * FROM media_sources WHERE externalUrl = :url AND serverId = :serverId LIMIT 1")
    MediaSourceEntity findByExternalUrlAndServer(String url, long serverId);
}
