package com.omarflex5.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * User-specific state for media items.
 * This data is PRIVATE and NOT synced to the global Firestore database.
 */
@Entity(tableName = "user_media_state", foreignKeys = @ForeignKey(entity = MediaEntity.class, parentColumns = "id", childColumns = "mediaId", onDelete = ForeignKey.CASCADE), indices = {
        @Index(value = "mediaId", unique = true) })
public class UserMediaStateEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private long mediaId; // FK to MediaEntity

    private boolean isFavorite;
    private boolean isWatched;
    private long watchProgress; // in milliseconds
    private long duration; // in milliseconds
    private Long lastWatchedAt;

    private long updatedAt;

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getMediaId() {
        return mediaId;
    }

    public void setMediaId(long mediaId) {
        this.mediaId = mediaId;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public boolean isWatched() {
        return isWatched;
    }

    public void setWatched(boolean watched) {
        isWatched = watched;
    }

    public long getWatchProgress() {
        return watchProgress;
    }

    public void setWatchProgress(long watchProgress) {
        this.watchProgress = watchProgress;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public Long getLastWatchedAt() {
        return lastWatchedAt;
    }

    public void setLastWatchedAt(Long lastWatchedAt) {
        this.lastWatchedAt = lastWatchedAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
