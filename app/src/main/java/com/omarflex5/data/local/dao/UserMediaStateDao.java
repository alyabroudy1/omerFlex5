package com.omarflex5.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import com.omarflex5.data.local.entity.UserMediaStateEntity;

@Dao
public interface UserMediaStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(UserMediaStateEntity state);

    // Get Series/Movie State (episodeId IS NULL)
    @Query("SELECT * FROM user_media_state WHERE mediaId = :mediaId AND episodeId IS NULL")
    LiveData<UserMediaStateEntity> getStateForMedia(long mediaId);

    @Query("SELECT * FROM user_media_state WHERE mediaId = :mediaId AND episodeId IS NULL")
    UserMediaStateEntity getStateForMediaSync(long mediaId);

    // Get Specific Episode State
    @Query("SELECT * FROM user_media_state WHERE episodeId = :episodeId")
    UserMediaStateEntity getStateForEpisodeSync(long episodeId);

    // Get Specific Season State
    @Query("SELECT * FROM user_media_state WHERE seasonId = :seasonId AND episodeId IS NULL")
    UserMediaStateEntity getStateForSeasonSync(long seasonId);

    // Get Last Watched Episode for a Series (for Auto-Focus)
    @Query("SELECT * FROM user_media_state WHERE mediaId = :mediaId AND episodeId IS NOT NULL ORDER BY lastWatchedAt DESC LIMIT 1")
    UserMediaStateEntity getLastWatchedEpisodeSync(long mediaId);

    @Query("SELECT * FROM user_media_state WHERE isFavorite = 1 AND episodeId IS NULL ORDER BY updatedAt DESC")
    LiveData<java.util.List<UserMediaStateEntity>> getFavorites();

    // Count watched episodes in a season
    @Query("SELECT COUNT(*) FROM user_media_state WHERE seasonId = :seasonId AND episodeId IS NOT NULL AND isWatched = 1")
    int getWatchedCountForSeasonSync(long seasonId);

    // Count watched episodes in a series
    @Query("SELECT COUNT(*) FROM user_media_state WHERE mediaId = :mediaId AND episodeId IS NOT NULL AND isWatched = 1")
    int getWatchedCountForMediaSync(long mediaId);

}
