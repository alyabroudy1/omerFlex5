package com.omarflex5.data.model.tmdb;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TmdbGenreResponse {
    @SerializedName("genres")
    private List<TmdbGenre> genres;

    public List<TmdbGenre> getGenres() {
        return genres;
    }
}
