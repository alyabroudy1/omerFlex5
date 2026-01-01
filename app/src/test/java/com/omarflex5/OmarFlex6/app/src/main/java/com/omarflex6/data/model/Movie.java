package com.omarflex6.data.model;

public class Movie {
    private String title;
    private String description;
    private String posterUrl;
    private String backdropUrl; // For hero
    private String year;
    private String rating;
    private String sourceBadge;
    private String videoUrl; // For player

    public Movie(String title, String description, String posterUrl, String backdropUrl, String year, String rating) {
        this.title = title;
        this.description = description;
        this.posterUrl = posterUrl;
        this.backdropUrl = backdropUrl;
        this.year = year;
        this.rating = rating;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public String getBackdropUrl() {
        return backdropUrl;
    }

    public String getYear() {
        return year;
    }

    public String getRating() {
        return rating;
    }

    public String getSourceBadge() {
        return sourceBadge;
    }

    public void setSourceBadge(String sourceBadge) {
        this.sourceBadge = sourceBadge;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }
}
