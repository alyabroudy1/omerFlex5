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

    @Query("SELECT * FROM user_media_state WHERE mediaId = :mediaId")
    LiveData<UserMediaStateEntity> getStateForMedia(long mediaId);

    @Query("SELECT * FROM user_media_state WHERE mediaId = :mediaId")
    UserMediaStateEntity getStateForMediaSync(long mediaId);

    @Query("SELECT * FROM user_media_state WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    LiveData<java.util.List<UserMediaStateEntity>> getFavorites();

    @Query("SELECT * FROM user_media_state WHERE watchProgress > 0 ORDER BY lastWatchedAt DESC")
    LiveData<java.util.List<UserMediaStateEntity>> getContinueWatching();
}
