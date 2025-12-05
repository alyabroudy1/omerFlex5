package com.omarflex5.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Media entity representing a SERIES or FILM.
 * 
 * For SERIES: Has child seasons and episodes
 * For FILM: Has direct MediaSource links
 */
@Entity(tableName = "media", indices = {
        @Index(value = "tmdbId", unique = true),
        @Index(value = "type"),
        @Index(value = "title")
})
public class MediaEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // Media type
    private MediaType type;

    // External IDs (nullable if not matched)
    private Integer tmdbId;
    private String imdbId;

    // Core metadata
    private String title;
    private String originalTitle;
    private String description;
    private String posterUrl;
    private String backdropUrl;
    private Float rating;
    private Integer year;
    private String releaseDate;

    // Categories as JSON array: ["Action", "Drama"]
    private String categoriesJson;

    // Enrichment status
    private boolean isEnriched;
    private Long enrichedAt;

    // Primary server that provided this content
    private Long primaryServerId;

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

    public MediaType getType() {
        return type;
    }

    public void setType(MediaType type) {
        this.type = type;
    }

    public Integer getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(Integer tmdbId) {
        this.tmdbId = tmdbId;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
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

    public String getBackdropUrl() {
        return backdropUrl;
    }

    public void setBackdropUrl(String backdropUrl) {
        this.backdropUrl = backdropUrl;
    }

    public Float getRating() {
        return rating;
    }

    public void setRating(Float rating) {
        this.rating = rating;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getCategoriesJson() {
        return categoriesJson;
    }

    public void setCategoriesJson(String categoriesJson) {
        this.categoriesJson = categoriesJson;
    }

    public boolean isEnriched() {
        return isEnriched;
    }

    public void setEnriched(boolean enriched) {
        isEnriched = enriched;
    }

    public Long getEnrichedAt() {
        return enrichedAt;
    }

    public void setEnrichedAt(Long enrichedAt) {
        this.enrichedAt = enrichedAt;
    }

    public Long getPrimaryServerId() {
        return primaryServerId;
    }

    public void setPrimaryServerId(Long primaryServerId) {
        this.primaryServerId = primaryServerId;
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
