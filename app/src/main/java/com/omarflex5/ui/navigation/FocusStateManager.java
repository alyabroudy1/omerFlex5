package com.omarflex5.ui.navigation;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized manager for focus state across all navigation layers.
 * Coordinates focus preservation during data changes and layer transitions.
 */
public class FocusStateManager {

    private final Map<String, NavigationDelegate> layers = new HashMap<>();
    private String currentLayer = null;

    /**
     * Register a navigation layer.
     * 
     * @param name     unique layer identifier (e.g., "hero", "categories",
     *                 "movies")
     * @param delegate the navigation delegate for this layer
     */
    public void registerLayer(String name, NavigationDelegate delegate) {
        layers.put(name, delegate);
    }

    /**
     * Get a registered layer delegate.
     * 
     * @param name layer identifier
     * @return the delegate, or null if not found
     */
    public NavigationDelegate getLayer(String name) {
        return layers.get(name);
    }

    /**
     * Save focus state for all registered layers.
     */
    public void saveAllStates() {
        for (NavigationDelegate delegate : layers.values()) {
            delegate.saveFocusState();
        }
    }

    /**
     * Restore focus to a specific layer.
     * 
     * @param layerName the layer to focus
     */
    public void restoreLayerFocus(String layerName) {
        NavigationDelegate delegate = layers.get(layerName);
        if (delegate != null) {
            currentLayer = layerName;
            delegate.requestFocus();
        }
    }

    /**
     * Set the current active layer.
     * 
     * @param layerName the active layer
     */
    public void setCurrentLayer(String layerName) {
        this.currentLayer = layerName;
    }

    /**
     * Get the current active layer name.
     * 
     * @return current layer name, or null if none set
     */
    public String getCurrentLayer() {
        return currentLayer;
    }
}
