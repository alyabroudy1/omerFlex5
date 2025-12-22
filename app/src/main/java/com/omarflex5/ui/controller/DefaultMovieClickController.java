package com.omarflex5.ui.controller;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.omarflex5.data.model.Movie;
import com.omarflex5.data.model.MovieActionType;
import com.omarflex5.ui.player.PlayerActivity;
import com.omarflex5.ui.search.SearchActivity;
import com.omarflex5.ui.details.DetailsActivity;

/**
 * Default implementation of MovieClickController.
 * Routes clicks to appropriate handlers based on movie action type.
 */
public class DefaultMovieClickController implements MovieClickController {

    private static final String TAG = "DefaultMovieClickController";

    @Override
    public void handleClick(Context context, Movie movie) {
        Log.d(TAG, "handleClick called");
        if (movie == null) {
            Log.d(TAG, "handleClick: movie is null");
            return;
        }

        // Determine action type
        MovieActionType actionType = movie.getActionType();
        if (actionType == null) {
            actionType = MovieActionType.DETAILS; // Default to details
        }

        // TMDB Override: Always force SEARCH action for TMDB items
        // This ensures users search for actual playable sources instead of dummy
        // trailers
        if ("TMDB".equalsIgnoreCase(movie.getSourceName())) {
            Log.d(TAG, "handleClick: TMDB source detected, forcing SEARCH action");
            actionType = MovieActionType.SEARCH;
        }

        Log.d(TAG, "handleClick: actionType = " + actionType + ", movie = " + movie.getTitle());

        switch (actionType) {
            case SEARCH:
                Log.d(TAG, "handleClick: -> SEARCH");
                handleSearchAction(context, movie);
                break;
            case BROWSER:
                Log.d(TAG, "handleClick: -> BROWSER");
                handleBrowserAction(context, movie);
                break;
            case EXOPLAYER:
                Log.d(TAG, "handleClick: -> EXOPLAYER");
                // Smart Action: Play if video URL exists, otherwise go to details
                if (movie.getVideoUrl() != null && !movie.getVideoUrl().isEmpty()) {
                    handleExoPlayerAction(context, movie);
                } else {
                    handleDetailsAction(context, movie);
                }
                break;
            case DETAILS:
            default:
                Log.d(TAG, "handleClick: -> DETAILS (default)");
                handleDetailsAction(context, movie);
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

        // Inheritance: Pass rich metadata to SearchActivity
        if (movie.getDescription() != null)
            intent.putExtra(SearchActivity.EXTRA_DESCRIPTION, movie.getDescription());
        if (movie.getRating() != null) {
            try {
                intent.putExtra(SearchActivity.EXTRA_RATING, Float.parseFloat(movie.getRating()));
            } catch (Exception e) {
            }
        }
        if (movie.getYear() != null) {
            try {
                intent.putExtra(SearchActivity.EXTRA_YEAR, Integer.parseInt(movie.getYear()));
            } catch (Exception e) {
            }
        }
        if (movie.getTrailerUrl() != null)
            intent.putExtra(SearchActivity.EXTRA_TRAILER, movie.getTrailerUrl());
        if (movie.getCategories() != null && !movie.getCategories().isEmpty())
            intent.putStringArrayListExtra(SearchActivity.EXTRA_CATEGORIES,
                    new java.util.ArrayList<>(movie.getCategories()));

        // Pass TMDB ID if available (either item is TMDB or it was previously synced)
        Integer tmdbId = movie.getTmdbId();
        if (tmdbId == null && "TMDB".equalsIgnoreCase(movie.getSourceName())) {
            try {
                tmdbId = Integer.parseInt(movie.getId());
            } catch (Exception e) {
            }
        }
        if (tmdbId != null) {
            intent.putExtra(SearchActivity.EXTRA_TMDB_ID, tmdbId);
        }

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

            try {
                long mediaId = Long.parseLong(movie.getId());
                intent.putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId);
            } catch (NumberFormatException e) {
                // Ignore if ID is not a long
            }

            if (movie.getSeasonId() != null) {
                intent.putExtra(PlayerActivity.EXTRA_SEASON_ID, (long) movie.getSeasonId());
            }
            if (movie.getEpisodeId() != null) {
                intent.putExtra(PlayerActivity.EXTRA_EPISODE_ID, (long) movie.getEpisodeId());
            }

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
        // Find server ID if possible
        Long serverId = null;
        // SearchActivity results don't have serverId in 'Movie' yet, but saved items do
        // via
        // MediaEntity join in ViewModel
        // For now, let's assume if it came from DetailsActivity before, we have what we
        // need.
        // If it's a direct result with a VIDEO URL, maybe we should play?
        // User said: "directly fetch the next step like if its series directly go to
        // seasons and so on."

        com.omarflex5.data.local.entity.MediaType type = movie.isTvShow()
                ? com.omarflex5.data.local.entity.MediaType.SERIES
                : com.omarflex5.data.local.entity.MediaType.FILM;

        long mediaId = -1;
        try {
            mediaId = Long.parseLong(movie.getId());
        } catch (Exception e) {
        }

        // Launch DetailsActivity. It handles both fresh URLs and existing Media IDs.
        DetailsActivity.start(
                context,
                movie.getVideoUrl(), // Search Results use videoUrl for their page URL
                movie.getTitle(),
                movie.getServerId() != null ? movie.getServerId() : -1,
                null, // backdrop
                type,
                null, // poster
                mediaId,
                movie.getSeasonId(),
                movie.getEpisodeId());
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
