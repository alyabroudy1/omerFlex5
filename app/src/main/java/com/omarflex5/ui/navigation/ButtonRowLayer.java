package com.omarflex5.ui.navigation;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * FocusLayer implementation for a horizontal row of buttons.
 * Used for the Hero layer (mute/fullscreen buttons).
 * 
 * Navigation rules:
 * - LEFT/RIGHT: Navigate between buttons
 * - UP: Blocked
 * - DOWN: Transition to next layer
 */
public class ButtonRowLayer implements FocusLayer {

    private final String name;
    private final List<View> buttons;
    private final String nextLayerDown;
    private int currentIndex = 0;

    /**
     * @param name          Unique layer name (e.g., "hero")
     * @param buttons       List of buttons in this row, in visual order (LTR for
     *                      calculations)
     * @param nextLayerDown Name of layer to transition to when pressing DOWN
     */
    public ButtonRowLayer(String name, List<View> buttons, String nextLayerDown) {
        this.name = name;
        this.buttons = buttons;
        this.nextLayerDown = nextLayerDown;

        // Default to last button (rightmost in LTR, which is leftmost visually in RTL)
        this.currentIndex = buttons.size() > 0 ? buttons.size() - 1 : 0;
    }

    /**
     * Convenience constructor for two buttons.
     */
    public ButtonRowLayer(String name, View button1, View button2, String nextLayerDown) {
        this(name, createList(button1, button2), nextLayerDown);
    }

    private static List<View> createList(View... views) {
        List<View> list = new ArrayList<>();
        for (View v : views) {
            if (v != null)
                list.add(v);
        }
        return list;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean containsView(View view) {
        if (view == null)
            return false;
        for (View button : buttons) {
            if (button == view || isDescendantOf(view, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canNavigateWithin(Direction direction) {
        // LEFT/RIGHT navigates within, UP/DOWN transitions
        return direction == Direction.LEFT || direction == Direction.RIGHT;
    }

    @Override
    public String getNextLayerName(Direction direction) {
        switch (direction) {
            case DOWN:
                return nextLayerDown;
            case UP:
                return null; // Blocked - no layer above
            default:
                return null;
        }
    }

    @Override
    public boolean handleNavigation(Direction direction) {
        if (buttons.isEmpty())
            return true;

        // Calculate next index based on direction
        // Note: RTL is already handled by TvFocusController before this
        int nextIndex = currentIndex;

        switch (direction) {
            case LEFT:
                nextIndex = currentIndex - 1;
                break;
            case RIGHT:
                nextIndex = currentIndex + 1;
                break;
            default:
                return true;
        }

        // Check bounds
        if (nextIndex < 0 || nextIndex >= buttons.size()) {
            // At edge, consume but don't move
            return true;
        }

        // Move to next button
        currentIndex = nextIndex;
        buttons.get(currentIndex).requestFocus();
        return true;
    }

    @Override
    public void requestFocus() {
        if (buttons.isEmpty())
            return;

        // Ensure index is valid
        if (currentIndex < 0 || currentIndex >= buttons.size()) {
            currentIndex = buttons.size() - 1;
        }

        buttons.get(currentIndex).requestFocus();
    }

    @Override
    public void saveFocusState() {
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).hasFocus()) {
                currentIndex = i;
                break;
            }
        }
    }

    @Override
    public View getCurrentFocusedView() {
        for (View button : buttons) {
            if (button.hasFocus()) {
                return button;
            }
        }
        return null;
    }

    private boolean isDescendantOf(View child, View parent) {
        if (child == parent)
            return true;
        if (parent instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) parent;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (isDescendantOf(child, vg.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}
