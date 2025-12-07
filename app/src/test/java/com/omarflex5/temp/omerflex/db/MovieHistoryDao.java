package com.omarflex5.temp.omerflex.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.omerflex.entity.MovieHistory;

import java.util.List;

@Dao
public interface MovieHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MovieHistory movieHistory);

    @Update
    void update(MovieHistory movieHistory);

    @Query("SELECT * FROM movie_history WHERE movieId = :movieId")
    LiveData<MovieHistory> getMovieHistoryByMovieIdLive(long movieId);

    @Query("SELECT * FROM movie_history WHERE movieId = :movieId")
    MovieHistory getMovieHistoryByMovieId(long movieId);

    @Query("SELECT * FROM movie_history WHERE movieId = :movieId")
    MovieHistory getMovieHistoryByMovieIdSync(long movieId);

    @Query("SELECT * FROM movie_history ORDER BY lastWatchedDate DESC")
    LiveData<List<MovieHistory>> getAllMovieHistories();
}