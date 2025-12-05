package com.omarflex5.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Season entity - child of SERIES MediaEntity.
 */
@Entity(tableName = "seasons", foreignKeys = @ForeignKey(entity = MediaEntity.class, parentColumns = "id", childColumns = "mediaId", onDelete = ForeignKey.CASCADE), indices = {
        @Index(value = "mediaId"),
        @Index(value = "tmdbSeasonId")
})
public class SeasonEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // Parent series
    private long mediaId;

    // TMDB ID for this season
    private Integer tmdbSeasonId;

    // Season info
    private int seasonNumber;
    private String title;
    private String description;
    private String posterUrl;
    private Integer episodeCount;
    private String airDate;

    // Processing state
    private ProcessingState state;

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

    public long getMediaId() {
        return mediaId;
    }

    public void setMediaId(long mediaId) {
        this.mediaId = mediaId;
    }

    public Integer getTmdbSeasonId() {
        return tmdbSeasonId;
    }

    public void setTmdbSeasonId(Integer tmdbSeasonId) {
        this.tmdbSeasonId = tmdbSeasonId;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public Integer getEpisodeCount() {
        return episodeCount;
    }

    public void setEpisodeCount(Integer episodeCount) {
        this.episodeCount = episodeCount;
    }

    public String getAirDate() {
        return airDate;
    }

    public void setAirDate(String airDate) {
        this.airDate = airDate;
    }

    public ProcessingState getState() {
        return state;
    }

    public void setState(ProcessingState state) {
        this.state = state;
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
}
