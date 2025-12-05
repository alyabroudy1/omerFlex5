package com.omarflex5.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.omarflex5.data.local.entity.SearchQueueEntity;
import com.omarflex5.data.local.entity.SearchQueueStatus;

import java.util.List;

@Dao
public interface SearchQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SearchQueueEntity queueItem);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<SearchQueueEntity> queueItems);

    @Update
    void update(SearchQueueEntity queueItem);

    @Delete
    void delete(SearchQueueEntity queueItem);

    @Query("SELECT * FROM search_queue WHERE id = :id")
    SearchQueueEntity getById(long id);

    @Query("SELECT * FROM search_queue WHERE query = :query AND status = :status ORDER BY createdAt ASC")
    List<SearchQueueEntity> getByQueryAndStatus(String query, SearchQueueStatus status);

    @Query("SELECT * FROM search_queue WHERE status = :status ORDER BY createdAt ASC")
    List<SearchQueueEntity> getByStatus(SearchQueueStatus status);

    @Query("SELECT * FROM search_queue WHERE status = :status ORDER BY createdAt ASC")
    LiveData<List<SearchQueueEntity>> getByStatusLive(SearchQueueStatus status);

    @Query("SELECT COUNT(*) FROM search_queue WHERE query = :query AND status = 'PENDING'")
    int getPendingCountForQuery(String query);

    @Query("SELECT COUNT(*) FROM search_queue WHERE query = :query AND status = 'PENDING'")
    LiveData<Integer> getPendingCountForQueryLive(String query);

    @Query("UPDATE search_queue SET status = :status, processedAt = :timestamp WHERE id = :id")
    void updateStatus(long id, SearchQueueStatus status, long timestamp);

    @Query("UPDATE search_queue SET status = :status, resultCount = :resultCount, processedAt = :timestamp WHERE id = :id")
    void markDone(long id, SearchQueueStatus status, int resultCount, long timestamp);

    @Query("UPDATE search_queue SET status = 'FAILED', errorMessage = :error, processedAt = :timestamp WHERE id = :id")
    void markFailed(long id, String error, long timestamp);

    @Query("DELETE FROM search_queue WHERE query = :query")
    void deleteByQuery(String query);

    @Query("DELETE FROM search_queue WHERE createdAt < :olderThan")
    void deleteOlderThan(long olderThan);
}
