package com.omarflex5.ui.navigation;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Central TV Focus Navigation Controller.
 * 
 * Manages all D-pad navigation by routing events to appropriate FocusLayer
 * implementations. Completely overrides Android's default focus system.
 * 
 * Usage:
 * 1. Create controller: TvFocusController controller = new
 * TvFocusController(isRTL);
 * 2. Register layers: controller.registerLayer(new ButtonRowLayer(...));
 * 3. Handle events: if (controller.handleKeyEvent(event)) return true;
 */
public class TvFocusController {

    private static final String TAG = "TvFocusController";

    private final Map<String, FocusLayer> layers = new HashMap<>();
    private final boolean isRTL;
    private String currentLayerName;
    private boolean debugEnabled = false;

    // Focus lock to prevent focus stealing during updates
    private boolean focusLocked = false;
    private String lockedLayerName = null;

    public TvFocusController(boolean isRTL) {
        this.isRTL = isRTL;
    }

    /**
     * Enable/disable debug logging.
     */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    /**
     * Register a focus layer with the controller.
     */
    public void registerLayer(FocusLayer layer) {
        layers.put(layer.getName(), layer);
        if (currentLayerName == null) {
            currentLayerName = layer.getName();
        }
        log("Registered layer: " + layer.getName());
    }

    /**
     * Handle a key event. Call this from dispatchKeyEvent.
     * 
     * @return true if the event was consumed, false to allow default handling
     */
    public boolean handleKeyEvent(KeyEvent event) {
        // Only handle D-pad events
        int keyCode = event.getKeyCode();
        if (!isDpadKey(keyCode)) {
            return false;
        }

        // Only process ACTION_DOWN, but consume both DOWN and UP to block Android focus
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return true; // Consume UP to prevent focus search
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        Direction direction = keyCodeToDirection(keyCode);
        if (direction == null) {
            return false;
        }

        return handleNavigation(direction);
    }

    /**
     * Handle a key event with a specific focused view context.
     */
    public boolean handleKeyEvent(KeyEvent event, View focusedView) {
        // If focus is locked, don't update layer based on focused view
        // This prevents external focus changes from affecting navigation
        if (!focusLocked) {
            // Find which layer owns this view
            FocusLayer owningLayer = findLayerForView(focusedView);
            if (owningLayer != null) {
                currentLayerName = owningLayer.getName();
                owningLayer.saveFocusState();
            }
        }

        return handleKeyEvent(event);
    }

    /**
     * Core navigation logic.
     */
    private boolean handleNavigation(Direction direction) {
        FocusLayer currentLayer = getCurrentLayer();
        if (currentLayer == null) {
            log("No current layer, cannot navigate");
            return false;
        }

        log("Navigate " + direction + " in layer: " + currentLayerName);

        // Apply RTL swap for horizontal navigation
        Direction effectiveDirection = applyRTL(direction);

        // Check if navigation stays within current layer or transitions
        boolean handledInternal = false;
        if (currentLayer.canNavigateWithin(effectiveDirection)) {
            // Handle within layer (e.g., scrolling in RecyclerView)
            // If it returns false, it means we hit an edge and should try to transition
            handledInternal = currentLayer.handleNavigation(effectiveDirection);
            log("Within-layer navigation: " + (handledInternal ? "handled" : "reached edge"));
        }

        if (handledInternal) {
            return true;
        }

        // If not handled internally (either canNavigateWithin was false, or
        // handleNavigation returned false)
        // Transition to next layer
        String nextLayerName = currentLayer.getNextLayerName(effectiveDirection);
        if (nextLayerName == null) {
            // Edge blocking - consume event but don't navigate
            log("Edge blocked at " + currentLayerName + " going " + effectiveDirection);
            return true;
        }

        // Save current focus before transitioning
        currentLayer.saveFocusState();

        // Transition to new layer
        FocusLayer nextLayer = layers.get(nextLayerName);
        if (nextLayer == null) {
            log("Next layer not found: " + nextLayerName);
            return true;
        }

        currentLayerName = nextLayerName;
        nextLayer.requestFocus();
        log("Transitioned to layer: " + nextLayerName);
        return true;
    }

    /**
     * Find which layer contains the given view.
     */
    private FocusLayer findLayerForView(View view) {
        if (view == null)
            return null;

        for (FocusLayer layer : layers.values()) {
            if (layer.containsView(view)) {
                return layer;
            }
        }
        return null;
    }

    /**
     * Get the current active layer.
     */
    public FocusLayer getCurrentLayer() {
        if (currentLayerName == null)
            return null;
        return layers.get(currentLayerName);
    }

    /**
     * Set the current layer by name.
     */
    public void setCurrentLayer(String layerName) {
        if (layers.containsKey(layerName)) {
            FocusLayer current = getCurrentLayer();
            if (current != null) {
                current.saveFocusState();
            }
            currentLayerName = layerName;
            FocusLayer next = layers.get(layerName);
            if (next != null) {
                next.requestFocus();
            }
        }
    }

    /**
     * Lock focus to prevent focus stealing.
     * Call this before operations that might steal focus (e.g., adapter updates).
     */
    public void lockFocus() {
        focusLocked = true;
        lockedLayerName = currentLayerName;
        log("Focus locked on layer: " + lockedLayerName);
    }

    /**
     * Unlock focus and optionally restore to locked layer.
     * 
     * @param restore if true, restore focus to the locked layer
     */
    public void unlockFocus(boolean restore) {
        focusLocked = false;
        if (restore && lockedLayerName != null) {
            log("Restoring focus to: " + lockedLayerName);
            currentLayerName = lockedLayerName;
            FocusLayer layer = layers.get(lockedLayerName);
            if (layer != null) {
                layer.requestFocus();
            }
        }
        lockedLayerName = null;
    }

    /**
     * Check if focus is currently locked.
     */
    public boolean isFocusLocked() {
        return focusLocked;
    }

    /**
     * Save current focus state and return it for later restoration.
     */
    public String saveAndGetCurrentLayer() {
        if (currentLayerName != null) {
            FocusLayer layer = layers.get(currentLayerName);
            if (layer != null) {
                layer.saveFocusState();
            }
        }
        return currentLayerName;
    }

    /**
     * Restore focus to a previously saved layer.
     */
    public void restoreToLayer(String layerName) {
        if (layerName != null && layers.containsKey(layerName)) {
            currentLayerName = layerName;
            FocusLayer layer = layers.get(layerName);
            if (layer != null) {
                layer.requestFocus();
            }
        }
    }

    /**
     * Request initial focus on the default layer.
     */
    public void requestInitialFocus() {
        FocusLayer layer = getCurrentLayer();
        if (layer != null) {
            layer.requestFocus();
        }
    }

    /**
     * Apply RTL swap for horizontal directions.
     */
    private Direction applyRTL(Direction direction) {
        if (!isRTL)
            return direction;

        switch (direction) {
            case LEFT:
                return Direction.RIGHT;
            case RIGHT:
                return Direction.LEFT;
            default:
                return direction;
        }
    }

    /**
     * Convert key code to Direction.
     */
    private Direction keyCodeToDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return Direction.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return Direction.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return Direction.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return Direction.RIGHT;
            default:
                return null;
        }
    }

    /**
     * Check if key code is a D-pad navigation key.
     */
    private boolean isDpadKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private void log(String message) {
        if (debugEnabled) {
            Log.d(TAG, message);
        }
    }
}
