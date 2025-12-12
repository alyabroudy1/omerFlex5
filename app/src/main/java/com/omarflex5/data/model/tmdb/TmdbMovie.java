package com.omarflex5.data.model.tmdb;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TmdbMovie {
    @SerializedName("id")
    private int id;

    @SerializedName("title")
    private String title;

    @SerializedName("name")
    private String name;

    @SerializedName("original_name")
    private String originalName;

    @SerializedName("original_title")
    private String originalTitle;

    @SerializedName("overview")
    private String overview;

    @SerializedName("poster_path")
    private String posterPath;

    @SerializedName("backdrop_path")
    private String backdropPath;

    @SerializedName("vote_average")
    private double voteAverage;

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title != null ? title : name;
    }

    public String getOriginalTitle() {
        String original = originalTitle != null ? originalTitle : originalName;
        return original != null ? original : getTitle();
    }

    public String getOverview() {
        return overview;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public String getBackdropPath() {
        return backdropPath;
    }

    @SerializedName("release_date")
    private String releaseDate;

    @SerializedName("first_air_date")
    private String firstAirDate;

    @SerializedName("genre_ids")
    private List<Integer> genreIds;

    @SerializedName("original_language")
    private String originalLanguage;

    public String getReleaseDate() {
        return releaseDate;
    }

    public String getFirstAirDate() {
        return firstAirDate;
    }

    public List<Integer> getGenreIds() {
        return genreIds;
    }

    public double getVoteAverage() {
        return voteAverage;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }
}
