package com.omarflex5.temp.omerflex.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.omerflex.entity.ServerConfig;

import java.util.List;

@Dao
public interface ServerConfigDao {

    @Insert
    void insert(ServerConfig serverConfig);

    @Update
    void update(ServerConfig serverConfig);

    @Query("DELETE FROM server_config WHERE id = :id")
    void delete(int id);

    @Query("SELECT * FROM server_config")
    LiveData<List<ServerConfig>> getAll();

    @Query("SELECT * FROM server_config")
    List<ServerConfig> getAllList();

    @Query("SELECT * FROM server_config WHERE id = :id")
    ServerConfig findById(int id);

    @Query("SELECT * FROM server_config WHERE name = :name")
    ServerConfig findByName(String name);

    @Query("SELECT * FROM server_config WHERE isActive = 1")
    List<ServerConfig> getActiveServers();

    @Query("DELETE FROM server_config")
    void deleteAll();
}
