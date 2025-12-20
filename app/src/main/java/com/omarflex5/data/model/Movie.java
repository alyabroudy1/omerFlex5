package com.omarflex5.data.model;

import java.util.Objects;

public class Movie {
    private final String id;
    private final String title; // Localized title (Arabic)
    private final String originalTitle; // Original title (English) for search
    private final String description;
    private final String backgroundUrl;
    private final String posterUrl;
    private final String trailerUrl;
    private final String videoUrl;
    private final String year;
    private final String rating;
    private final MovieActionType actionType;
    private final boolean isTvShow;
    private final java.util.List<String> categories;
    private final String sourceName;
    private final boolean isFavorite;
    private final boolean isWatched;
    private final long watchProgress;
    private final long duration;
    private final Long seasonId;
    private final Long episodeId;
    private final Integer tmdbId;
    private final Long serverId;

    public Movie(String id, String title, String originalTitle, String description,
            String backgroundUrl, String posterUrl, String trailerUrl, String videoUrl,
            String year, String rating, MovieActionType actionType, boolean isTvShow,
            java.util.List<String> categories, String sourceName, boolean isFavorite, boolean isWatched,
            long watchProgress, long duration, Long seasonId, Long episodeId, Integer tmdbId, Long serverId) {
        this.id = id;
        this.title = title;
        this.originalTitle = originalTitle != null ? originalTitle : title;
        this.description = description;
        this.backgroundUrl = backgroundUrl;
        this.posterUrl = posterUrl;
        this.trailerUrl = trailerUrl;
        this.videoUrl = videoUrl;
        this.year = year;
        this.rating = rating;
        this.actionType = actionType;
        this.isTvShow = isTvShow;
        this.categories = categories != null ? categories : new java.util.ArrayList<>();
        this.sourceName = sourceName != null ? sourceName : "TMDB";
        this.isFavorite = isFavorite;
        this.isWatched = isWatched;
        this.watchProgress = watchProgress;
        this.duration = duration;
        this.seasonId = seasonId;
        this.episodeId = episodeId;
        this.tmdbId = tmdbId;
        this.serverId = serverId;
    }

    public Movie(String id, String title, String originalTitle, String description,
            String backgroundUrl, String posterUrl, String trailerUrl, String videoUrl,
            String year, String rating, MovieActionType actionType, boolean isTvShow,
            java.util.List<String> categories, String sourceName, boolean isFavorite, boolean isWatched,
            long watchProgress, long duration) {
        this(id, title, originalTitle, description, backgroundUrl, posterUrl, trailerUrl, videoUrl,
                year, rating, actionType, isTvShow, categories, sourceName, isFavorite, isWatched,
                watchProgress, duration, null, null, null, null);
    }

    public Movie(String id, String title, String originalTitle, String description,
            String backgroundUrl, String posterUrl, String trailerUrl, String videoUrl,
            String year, String rating, MovieActionType actionType, boolean isTvShow,
            java.util.List<String> categories, String sourceName) {
        this(id, title, originalTitle, description, backgroundUrl, posterUrl, trailerUrl, videoUrl,
                year, rating, actionType, isTvShow, categories, sourceName, false, false, 0, 0);
    }

    public Movie(String id, String title, String originalTitle, String description,
            String backgroundUrl, String posterUrl, String trailerUrl, String videoUrl,
            String year, String rating, MovieActionType actionType, boolean isTvShow,
            java.util.List<String> categories) {
        this(id, title, originalTitle, description, backgroundUrl, posterUrl, trailerUrl, videoUrl,
                year, rating, actionType, isTvShow, categories, "TMDB");
    }

    public Movie(String id, String title, String originalTitle, String description,
            String backgroundUrl, String posterUrl, String trailerUrl, String videoUrl,
            String year, String rating, MovieActionType actionType, boolean isTvShow) {
        this(id, title, originalTitle, description, backgroundUrl, posterUrl, trailerUrl, videoUrl,
                year, rating, actionType, isTvShow, new java.util.ArrayList<>(), "TMDB");
    }

    // Legacy constructor for backward compatibility
    public Movie(String id, String title, String description, String backgroundUrl, String posterUrl,
            String trailerUrl, String year, String rating) {
        this(id, title, title, description, backgroundUrl, posterUrl, trailerUrl, trailerUrl,
                year, rating, MovieActionType.EXOPLAYER, false, new java.util.ArrayList<>(), "TMDB",
                false, false, 0, 0);
    }

    // Legacy 10-arg constructor
    public Movie(String id, String title, String description, String backgroundUrl, String posterUrl,
            String trailerUrl, String videoUrl, String year, String rating, MovieActionType actionType) {
        this(id, title, title, description, backgroundUrl, posterUrl, trailerUrl, videoUrl,
                year, rating, actionType, false, new java.util.ArrayList<>(), "TMDB",
                false, false, 0, 0);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Get original title (English) for searching on servers
     */
    public String getOriginalTitle() {
        return originalTitle;
    }

    /**
     * Get the best title for search - prefers original title
     */
    public String getSearchTitle() {
        return originalTitle != null && !originalTitle.isEmpty() ? originalTitle : title;
    }

    public String getDescription() {
        return description;
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public String getTrailerUrl() {
        return trailerUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getYear() {
        return year;
    }

    public String getRating() {
        return rating;
    }

    public MovieActionType getActionType() {
        return actionType;
    }

    public boolean isTvShow() {
        return isTvShow;
    }

    public java.util.List<String> getCategories() {
        return categories;
    }

    public String getSourceName() {
        return sourceName;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public boolean isWatched() {
        return isWatched;
    }

    public long getWatchProgress() {
        return watchProgress;
    }

    public long getDuration() {
        return duration;
    }

    public Long getSeasonId() {
        return seasonId;
    }

    public Long getEpisodeId() {
        return episodeId;
    }

    public Integer getTmdbId() {
        return tmdbId;
    }

    public Long getServerId() {
        return serverId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Movie movie = (Movie) o;
        return Objects.equals(id, movie.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
