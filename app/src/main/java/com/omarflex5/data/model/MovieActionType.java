package com.omarflex5.data.model;

/**
 * Defines the action type for movie click behavior.
 * The action type determines what happens when a user clicks on a movie.
 */
public enum MovieActionType {
    /**
     * Opens the movie in a browser (web player)
     */
    BROWSER,

    /**
     * Opens the movie in ExoPlayer activity for native playback
     */
    EXOPLAYER,

    /**
     * Opens movie details page
     */
    DETAILS,

    /**
     * Extensible action for future implementations
     */
    EXTEND
}
