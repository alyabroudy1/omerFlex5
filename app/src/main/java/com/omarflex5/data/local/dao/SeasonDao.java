package com.omarflex5.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.omarflex5.data.local.entity.SeasonEntity;

import java.util.List;

@Dao
public interface SeasonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SeasonEntity season);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<SeasonEntity> seasons);

    @Update
    void update(SeasonEntity season);

    @Delete
    void delete(SeasonEntity season);

    @Query("SELECT * FROM seasons WHERE id = :id")
    SeasonEntity getById(long id);

    @Query("SELECT * FROM seasons WHERE mediaId = :mediaId ORDER BY seasonNumber ASC")
    List<SeasonEntity> getByMediaId(long mediaId);

    @Query("SELECT * FROM seasons WHERE mediaId = :mediaId ORDER BY seasonNumber ASC")
    LiveData<List<SeasonEntity>> getByMediaIdLive(long mediaId);

    @Query("SELECT * FROM seasons WHERE mediaId = :mediaId AND seasonNumber = :seasonNumber")
    SeasonEntity getByMediaIdAndNumber(long mediaId, int seasonNumber);

    @Query("DELETE FROM seasons WHERE mediaId = :mediaId")
    void deleteByMediaId(long mediaId);
}
