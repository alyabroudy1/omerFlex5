package com.omarflex5.data.model;

import java.util.Objects;

public class Movie {
    private final String id;
    private final String title;
    private final String description;
    private final String backgroundUrl;
    private final String posterUrl;
    private final String trailerUrl;
    private final String videoUrl; // Full video URL for playback
    private final String year;
    private final String rating;
    private final MovieActionType actionType;

    public Movie(String id, String title, String description, String backgroundUrl, String posterUrl,
            String trailerUrl, String videoUrl, String year, String rating, MovieActionType actionType) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.backgroundUrl = backgroundUrl;
        this.posterUrl = posterUrl;
        this.trailerUrl = trailerUrl;
        this.videoUrl = videoUrl;
        this.year = year;
        this.rating = rating;
        this.actionType = actionType;
    }

    // Legacy constructor for backward compatibility
    public Movie(String id, String title, String description, String backgroundUrl, String posterUrl,
            String trailerUrl, String year, String rating) {
        this(id, title, description, backgroundUrl, posterUrl, trailerUrl, trailerUrl, year, rating,
                MovieActionType.EXOPLAYER);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
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
