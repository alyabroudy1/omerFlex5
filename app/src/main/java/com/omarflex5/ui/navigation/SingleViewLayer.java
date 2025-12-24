package com.omarflex5.ui.navigation;

import android.view.View;
import java.util.Collections;
import java.util.List;

/**
 * FocusLayer implementation for a single View (e.g., a button).
 * Wraps a standard View into the TvFocusController system.
 */
public class SingleViewLayer implements FocusLayer {

    private final String name;
    private final View view;
    private final String nextLayerUp;
    private final String nextLayerDown;
    private final String nextLayerLeft;
    private final String nextLayerRight;

    public SingleViewLayer(String name, View view, String nextLayerUp, String nextLayerDown) {
        this(name, view, nextLayerUp, nextLayerDown, null, null);
    }

    public SingleViewLayer(String name, View view, String nextLayerUp, String nextLayerDown,
            String nextLayerLeft, String nextLayerRight) {
        this.name = name;
        this.view = view;
        this.nextLayerUp = nextLayerUp;
        this.nextLayerDown = nextLayerDown;
        this.nextLayerLeft = nextLayerLeft;
        this.nextLayerRight = nextLayerRight;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean containsView(View target) {
        return view == target;
    }

    @Override
    public boolean canNavigateWithin(Direction direction) {
        // Single view has no internal navigation
        return false;
    }

    @Override
    public String getNextLayerName(Direction direction) {
        switch (direction) {
            case UP:
                return nextLayerUp;
            case DOWN:
                return nextLayerDown;
            case LEFT:
                return nextLayerLeft;
            case RIGHT:
                return nextLayerRight;
            default:
                return null;
        }
    }

    @Override
    public boolean handleNavigation(Direction direction) {
        // Should not be called if canNavigateWithin returns false
        return false;
    }

    @Override
    public void requestFocus() {
        if (view != null) {
            view.requestFocus();
        }
    }

    @Override
    public void saveFocusState() {
        // Nothing to save for single view
    }

    @Override
    public View getCurrentFocusedView() {
        return view.hasFocus() ? view : null;
    }
}
