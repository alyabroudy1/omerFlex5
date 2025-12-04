package com.omarflex5.data.model.tmdb;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TmdbMovieResponse {
    @SerializedName("results")
    private List<TmdbMovie> results;

    public List<TmdbMovie> getResults() {
        return results;
    }
}
