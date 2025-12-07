package com.omarflex5.temp.omerflex.server;

import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;

import java.util.ArrayList;

public interface ServerInterface {
    public ArrayList<Movie> getHomepageMovies(boolean handleCookie, ActivityCallback<ArrayList<Movie>> activityCallback);
    public ArrayList<Movie> search(String query, ActivityCallback<ArrayList<Movie>> activityCallback, boolean handleCookie);
    public MovieFetchProcess fetch(Movie movie, int action, ActivityCallback<Movie> activityCallback);
    public int fetchNextAction(Movie movie);
    public String getLabel();
    public String getServerId();

    // 1. success movie fetch
    // 2. failure:
    //      -invalid cookie
    //      -invalid movie link

    interface ActivityCallback<T> {
        void onSuccess(T result, String title);
        void onInvalidCookie(T result, String title);
        void onInvalidLink(T result);
        void onInvalidLink(String message);
    }

}


