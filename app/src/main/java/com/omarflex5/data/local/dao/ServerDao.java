package com.omarflex5.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.omarflex5.data.local.entity.ServerEntity;

import java.util.List;

@Dao
public interface ServerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ServerEntity server);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<ServerEntity> servers);

    @Update
    void update(ServerEntity server);

    @Delete
    void delete(ServerEntity server);

    @Query("SELECT * FROM servers WHERE id = :id")
    ServerEntity getById(long id);

    @Query("SELECT * FROM servers WHERE name = :name")
    ServerEntity getByName(String name);

    @Query("SELECT * FROM servers WHERE isEnabled = 1 ORDER BY currentPriority ASC")
    List<ServerEntity> getEnabledByPriority();

    @Query("SELECT * FROM servers WHERE isEnabled = 1 ORDER BY currentPriority ASC")
    LiveData<List<ServerEntity>> getEnabledByPriorityLive();

    @Query("SELECT * FROM servers WHERE isEnabled = 1 AND isSearchable = 1 ORDER BY currentPriority ASC")
    List<ServerEntity> getSearchableByPriority();

    @Query("SELECT * FROM servers WHERE requiresWebView = 1 AND isEnabled = 1")
    List<ServerEntity> getCfProtectedServers();

    @Query("SELECT * FROM servers ORDER BY basePriority ASC")
    List<ServerEntity> getAll();

    @Query("SELECT * FROM servers ORDER BY basePriority ASC")
    LiveData<List<ServerEntity>> getAllLive();

    @Query("UPDATE servers SET baseUrl = :baseUrl, updatedAt = :timestamp WHERE name = :name")
    void updateBaseUrl(String name, String baseUrl, long timestamp);

    @Query("UPDATE servers SET searchUrlPattern = :pattern, updatedAt = :timestamp WHERE name = :name")
    void updateSearchUrlPattern(String name, String pattern, long timestamp);

    @Query("UPDATE servers SET cfCookiesJson = :cookies, cfCookiesExpireAt = :expireAt, updatedAt = :timestamp WHERE id = :id")
    void updateCookies(long id, String cookies, long expireAt, long timestamp);

    @Query("UPDATE servers SET currentPriority = basePriority, consecutiveFailures = 0, consecutiveSuccesses = 0 WHERE id = :id")
    void resetPriority(long id);

    @Query("SELECT * FROM servers WHERE baseUrl LIKE '%' || :host || '%' LIMIT 1")
    ServerEntity findByHost(String host);
}
