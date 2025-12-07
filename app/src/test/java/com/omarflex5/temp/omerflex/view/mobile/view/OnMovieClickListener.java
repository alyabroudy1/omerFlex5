package com.omarflex5.temp.omerflex.view.mobile.view;

import com.omerflex.entity.Movie;

public interface OnMovieClickListener {

    void onMovieClick(Movie movie, int moviePosition, HorizontalMovieAdapter horizontalMovieAdapter);  // Callback method
}
