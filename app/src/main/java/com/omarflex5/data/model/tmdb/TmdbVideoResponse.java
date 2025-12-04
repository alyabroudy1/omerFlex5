package com.omarflex5.data.model.tmdb;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TmdbVideoResponse {
    @SerializedName("results")
    private List<TmdbVideo> results;

    public List<TmdbVideo> getResults() {
        return results;
    }
}
