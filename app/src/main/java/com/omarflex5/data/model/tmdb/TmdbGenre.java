package com.omarflex5.data.model.tmdb;

import com.google.gson.annotations.SerializedName;

public class TmdbGenre {
    @SerializedName("id")
    private int id;

    @SerializedName("name")
    private String name;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
