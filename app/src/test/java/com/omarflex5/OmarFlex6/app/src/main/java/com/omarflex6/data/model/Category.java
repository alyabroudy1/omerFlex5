package com.omarflex6.data.model;

import java.util.List;

public class Category {
    private String id;
    private String name;
    private List<Movie> movies;

    public Category(String id, String name, List<Movie> movies) {
        this.id = id;
        this.name = name;
        this.movies = movies;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Movie> getMovies() {
        return movies;
    }
}
