package com.omarflex5.temp.omerflex.view.mobile.entity;

// Category.java

import com.omerflex.view.mobile.view.HorizontalMovieAdapter;

public class Category {
    private String categoryName;
    private HorizontalMovieAdapter movieListAdapter;

    // Constructor, getters, and setters
    public Category(String categoryName, HorizontalMovieAdapter movieList) {
        this.categoryName = categoryName;
        this.movieListAdapter = movieList;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public HorizontalMovieAdapter getMovieAdapter() {
        return movieListAdapter;
    }
}

