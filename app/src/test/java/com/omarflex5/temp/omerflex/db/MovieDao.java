package com.omarflex5.temp.omerflex.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.omerflex.entity.Movie;

import com.omerflex.entity.MovieType;

import java.util.ArrayList;
import java.util.List;

@Dao
public interface MovieDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Movie movie);

    @Update
    void update(Movie movie);

    @Delete
    void delete(Movie movie);

    @Query("DELETE FROM movies")
    void deleteAllMovies();

    @Query("DELETE FROM movies WHERE studio = :studio")
    void deleteByStudio(String studio);

    @Query("SELECT * FROM movies ORDER BY updatedAt DESC")
    LiveData<List<Movie>> getAllMovies();

    @Query("SELECT * FROM movies WHERE isHistory = 1 AND studio != :iptvStudioName ORDER BY updatedAt DESC")
    List<Movie> getWatchedMovies(String iptvStudioName);

    @Query("SELECT * FROM movies WHERE isHistory = 1 AND studio = :iptvStudioName ORDER BY updatedAt DESC")
    List<Movie> getWatchedChannels(String iptvStudioName);

    @Query("SELECT * FROM movies WHERE videoUrl = :videoUrl")
    LiveData<Movie> getMovieByVideoUrl(String videoUrl);

    @Query("SELECT * FROM movies WHERE videoUrl = :videoUrl")
    Movie getMovieByVideoUrlSync(String videoUrl);

    @Query("SELECT * FROM movies WHERE type = :type")
    LiveData<List<Movie>> getMoviesByType(MovieType type);

    @Query("SELECT * FROM movies WHERE parentId = :parentId")
    LiveData<List<Movie>> getMoviesByParentId(long parentId);

    @Query("SELECT * FROM movies WHERE parentId = :parentId")
    List<Movie> getMoviesByParentIdSync(long parentId);

    @Query("SELECT * FROM movies WHERE id = :id")
    Movie getMovieById(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Movie> movies);

    @Query("UPDATE movies SET playedTime = :playedTime WHERE id = :movieId")
    void updateWatchedTime(long movieId, long playedTime);

    @Query("UPDATE movies SET movieLength = :movieLength WHERE id = :id")
    void updateMovieLength(long id, long movieLength);

    @Query("UPDATE movies SET isHistory = 1 WHERE id = :id")
    void setMovieIsHistory(long id);

    @RawQuery
    List<Movie> getMoviesByStudioAndGroupsRaw(SupportSQLiteQuery query);

    default List<Movie> getMoviesByStudioAndGroups(String studio, List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return new ArrayList<>();
        }

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT * FROM movies WHERE studio = ? AND (");

        Object[] args = new Object[groups.size() + 1];
        args[0] = studio;

        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) {
                queryBuilder.append(" OR ");
            }
            queryBuilder.append("LOWER(`group`) LIKE ?");
            args[i + 1] = "%" + groups.get(i).toLowerCase() + "%";
        }
        queryBuilder.append(")");

        SimpleSQLiteQuery simpleSQLiteQuery = new SimpleSQLiteQuery(queryBuilder.toString(), args);
        return getMoviesByStudioAndGroupsRaw(simpleSQLiteQuery);
    }

    default List<Movie> getSearchIptvMovies(String query) {
        String q = "%" + query.toLowerCase() + "%";
        SimpleSQLiteQuery simpleSQLiteQuery = new SimpleSQLiteQuery(
                "SELECT * FROM movies WHERE studio = '"+Movie.SERVER_IPTV+"' AND (LOWER(title) LIKE ? OR LOWER(`group`) LIKE ?)",
                new Object[]{q, q}
        );
        return getMoviesByStudioAndGroupsRaw(simpleSQLiteQuery);
    }

    @Query("SELECT * FROM movies WHERE title = :title AND studio = :studio")
    Movie getMovieByTitleAndStudio(String title, String studio);
}