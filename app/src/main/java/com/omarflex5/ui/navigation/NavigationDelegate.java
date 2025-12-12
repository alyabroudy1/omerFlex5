package com.omarflex5.ui.navigation;

/**
 * Interface for layer-based navigation management.
 * Each UI layer (Hero, Categories, Movies) implements this to handle D-pad
 * navigation.
 */
public interface NavigationDelegate {

    /**
     * Handle UP navigation from this layer.
     * 
     * @return true if navigation was handled, false to allow default behavior
     */
    boolean onNavigateUp();

    /**
     * Handle DOWN navigation from this layer.
     * 
     * @return true if navigation was handled, false to allow default behavior
     */
    boolean onNavigateDown();

    /**
     * Handle LEFT navigation within this layer.
     * 
     * @return true if navigation was handled, false if at edge
     */
    boolean onNavigateLeft();

    /**
     * Handle RIGHT navigation within this layer.
     * 
     * @return true if navigation was handled, false if at edge
     */
    boolean onNavigateRight();

    /**
     * Request focus on this layer's saved position.
     */
    void requestFocus();

    /**
     * Save current focus state for later restoration.
     */
    void saveFocusState();

    /**
     * Reset focus to a specific position (e.g., first item).
     * 
     * @param position adapter position to focus
     */
    void resetToPosition(int position);
}
