package com.omarflex5.ui.navigation;

import android.view.View;

/**
 * Interface for a focusable layer in the TV navigation system.
 * Each layer manages its own focus and navigation within its bounds.
 */
public interface FocusLayer {

    /**
     * Get the unique name of this layer (e.g., "hero", "categories", "movies").
     */
    String getName();

    /**
     * Check if this layer contains the given view.
     */
    boolean containsView(View view);

    /**
     * Check if navigation in the given direction should stay within this layer
     * or transition to another layer.
     * 
     * @return true if navigation should be handled within this layer
     */
    boolean canNavigateWithin(Direction direction);

    /**
     * Get the name of the next layer to transition to when navigating in the
     * given direction. Returns null if no transition should occur (edge blocking).
     */
    String getNextLayerName(Direction direction);

    /**
     * Handle navigation within this layer.
     * 
     * @return true if the navigation was handled, false to allow default behavior
     */
    boolean handleNavigation(Direction direction);

    /**
     * Request focus on this layer, restoring to the last remembered position.
     */
    void requestFocus();

    /**
     * Save the current focus state (position) for later restoration.
     */
    void saveFocusState();

    /**
     * Get the currently focused view within this layer, or null if none.
     */
    View getCurrentFocusedView();
}
