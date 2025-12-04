package com.omarflex5.ui.controller;

import android.content.Context;
import com.omarflex5.data.model.Movie;

/**
 * Base interface for handling movie click actions.
 * Implementations can extend this to provide custom click behavior.
 */
public interface MovieClickController {

    /**
     * Handle the click action for a movie.
     * 
     * @param context The context for starting activities or showing toasts
     * @param movie   The movie that was clicked
     */
    void handleClick(Context context, Movie movie);
}
