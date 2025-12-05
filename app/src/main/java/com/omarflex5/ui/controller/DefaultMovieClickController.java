package com.omarflex5.ui.controller;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.omarflex5.data.model.Movie;
import com.omarflex5.data.model.MovieActionType;
import com.omarflex5.ui.player.PlayerActivity;
import com.omarflex5.ui.search.SearchActivity;

/**
 * Default implementation of MovieClickController.
 * Routes clicks to appropriate handlers based on movie action type.
 */
public class DefaultMovieClickController implements MovieClickController {

    @Override
    public void handleClick(Context context, Movie movie) {
        if (movie == null) {
            return;
        }

        MovieActionType actionType = movie.getActionType();
        if (actionType == null) {
            actionType = MovieActionType.EXOPLAYER; // Default
        }

        switch (actionType) {
            case BROWSER:
                handleBrowserAction(context, movie);
                break;
            case EXOPLAYER:
                // Now triggers multi-server search instead of playing directly
                handleSearchAction(context, movie);
                break;
            case DETAILS:
                handleDetailsAction(context, movie);
                break;
            case EXTEND:
                handleExtendAction(context, movie);
                break;
        }
    }

    /**
     * Handle search action - opens SearchActivity to find sources across servers
     * Uses original title (English) for better search results on servers
     */
    protected void handleSearchAction(Context context, Movie movie) {
        Intent intent = new Intent(context, SearchActivity.class);
        // Use original title for search (English), display localized title
        intent.putExtra(SearchActivity.EXTRA_QUERY, movie.getSearchTitle());
        intent.putExtra(SearchActivity.EXTRA_MOVIE_TITLE, movie.getTitle());
        context.startActivity(intent);
    }

    /**
     * Handle browser action - opens movie in web browser
     * To be implemented later
     */
    protected void handleBrowserAction(Context context, Movie movie) {
        Toast.makeText(context,
                "Browser: " + movie.getTitle() + " - Coming soon!",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Handle ExoPlayer action - opens native video player
     * Used when a specific source is selected from search results
     */
    protected void handleExoPlayerAction(Context context, Movie movie) {
        String videoUrl = movie.getVideoUrl();
        if (videoUrl == null || videoUrl.isEmpty()) {
            videoUrl = movie.getTrailerUrl();
        }

        if (videoUrl != null && !videoUrl.isEmpty()) {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
            intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, movie.getTitle());
            context.startActivity(intent);
        } else {
            Toast.makeText(context,
                    "No video available for " + movie.getTitle(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle details action - opens movie details page
     * To be implemented later
     */
    protected void handleDetailsAction(Context context, Movie movie) {
        Toast.makeText(context,
                "Details: " + movie.getTitle() + " - Coming soon!",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Handle extend action - extensible for future use
     * To be implemented later
     */
    protected void handleExtendAction(Context context, Movie movie) {
        Toast.makeText(context,
                "Extend: " + movie.getTitle() + " - Coming soon!",
                Toast.LENGTH_SHORT).show();
    }
}
