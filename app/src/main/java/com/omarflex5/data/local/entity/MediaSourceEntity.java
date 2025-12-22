package com.omarflex5.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Links media content to server sources.
 * 
 * Polymorphic: Can link to Media (film), Season, or Episode.
 * Only ONE of mediaId, seasonId, episodeId should be set.
 */
@Entity(tableName = "media_sources", foreignKeys = {
        @ForeignKey(entity = ServerEntity.class, parentColumns = "id", childColumns = "serverId", onDelete = ForeignKey.CASCADE)
}, indices = {
        @Index(value = "serverId"),
        @Index(value = "mediaId"),
        @Index(value = "seasonId"),
        @Index(value = "episodeId"),
        @Index(value = "matchKey")
})
public class MediaSourceEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // Server this source is from
    private long serverId;

    // Polymorphic link - only ONE should be non-null
    private Long mediaId; // For films or full series page
    private Long seasonId; // For specific season
    private Long episodeId; // For specific episode

    // Server's identifier for this content
    private String externalId;
    private String externalUrl;

    // Normalized key for deduplication: "title|year|season|episode"
    private String matchKey;

    // Quality info (if known)
    private String quality; // "1080p", "720p", "480p", etc.

    // Original scraper title (for display preference)
    private String title;

    // Availability tracking
    private boolean isAvailable;
    private Long lastCheckedAt;
    private int checkFailures; // Consecutive failures

    // Timestamps
    private long createdAt;
    private long updatedAt;

    // ========== Getters and Setters ==========

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public void setMediaId(Long mediaId) {
        this.mediaId = mediaId;
    }

    public Long getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(Long seasonId) {
        this.seasonId = seasonId;
    }

    public Long getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(Long episodeId) {
        this.episodeId = episodeId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getMatchKey() {
        return matchKey;
    }

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public Long getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Long lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public int getCheckFailures() {
        return checkFailures;
    }

    public void setCheckFailures(int checkFailures) {
        this.checkFailures = checkFailures;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
