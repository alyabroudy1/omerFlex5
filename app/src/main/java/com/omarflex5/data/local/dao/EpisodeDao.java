package com.omarflex5.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.omarflex5.data.local.entity.EpisodeEntity;

import java.util.List;

@Dao
public interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(EpisodeEntity episode);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<EpisodeEntity> episodes);

    @Update
    void update(EpisodeEntity episode);

    @Delete
    void delete(EpisodeEntity episode);

    @Query("SELECT * FROM episodes WHERE id = :id")
    EpisodeEntity getById(long id);

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    List<EpisodeEntity> getBySeasonId(long seasonId);

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY episodeNumber ASC")
    LiveData<List<EpisodeEntity>> getBySeasonIdLive(long seasonId);

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId AND episodeNumber = :episodeNumber")
    EpisodeEntity getBySeasonIdAndNumber(long seasonId, int episodeNumber);

    @Query("DELETE FROM episodes WHERE seasonId = :seasonId")
    void deleteBySeasonId(long seasonId);
}
