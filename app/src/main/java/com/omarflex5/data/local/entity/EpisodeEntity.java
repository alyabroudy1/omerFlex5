package com.omarflex5.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Episode entity - child of SeasonEntity.
 * Contains video sources via MediaSource links.
 */
@Entity(tableName = "episodes", foreignKeys = @ForeignKey(entity = SeasonEntity.class, parentColumns = "id", childColumns = "seasonId", onDelete = ForeignKey.CASCADE), indices = {
        @Index(value = "seasonId"),
        @Index(value = "tmdbEpisodeId")
})
public class EpisodeEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // Parent season
    private long seasonId;

    // TMDB ID for this episode
    private Integer tmdbEpisodeId;

    // Episode info
    private int episodeNumber;
    private String title;
    private String description;
    private String stillUrl; // Episode thumbnail
    private Integer runtime; // Duration in minutes
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

    public long getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(long seasonId) {
        this.seasonId = seasonId;
    }

    public Integer getTmdbEpisodeId() {
        return tmdbEpisodeId;
    }

    public void setTmdbEpisodeId(Integer tmdbEpisodeId) {
        this.tmdbEpisodeId = tmdbEpisodeId;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = episodeNumber;
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

    public String getStillUrl() {
        return stillUrl;
    }

    public void setStillUrl(String stillUrl) {
        this.stillUrl = stillUrl;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public void setRuntime(Integer runtime) {
        this.runtime = runtime;
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
