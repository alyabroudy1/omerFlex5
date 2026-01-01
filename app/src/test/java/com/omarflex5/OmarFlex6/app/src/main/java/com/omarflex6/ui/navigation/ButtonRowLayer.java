package com.omarflex6.ui.navigation;

import android.view.View;
import java.util.Collections;
import java.util.List;

public class ButtonRowLayer implements FocusLayer {

    private final String name;
    private final List<View> buttons;
    private String nextLayerUp;
    private String nextLayerDown;
    private String nextLayerLeft;
    private String nextLayerRight;
    private int currentIndex = 0;

    /**
     * @param name          Unique layer name (e.g., "hero")
     * @param buttons       List of buttons in this row
     * @param nextLayerLeft Name of layer to transition to when pressing LEFT at
     *                      start
     */
    public ButtonRowLayer(String name, List<View> buttons, String nextLayerLeft) {
        this.name = name;
        this.buttons = buttons;
        this.nextLayerLeft = nextLayerLeft;

        // Default to first button
        this.currentIndex = 0;
    }

    /**
     * Convenience constructor.
     */
    public ButtonRowLayer(String name, View button1, View button2, String nextLayerLeft) {
        this(name, createList(button1, button2), nextLayerLeft);
    }

    public void setNextLayerDown(String nextLayerDown) {
        this.nextLayerDown = nextLayerDown;
    }

    private static List<View> createList(View... views) {
        if (views == null)
            return Collections.emptyList();
        java.util.ArrayList<View> list = new java.util.ArrayList<>();
        Collections.addAll(list, views);
        return list;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean containsView(View view) {
        return buttons.contains(view);
    }

    @Override
    public boolean canNavigateWithin(Direction direction) {
        if (buttons.isEmpty())
            return false;

        // Only navigate within if we are NOT at an edge for that direction
        if (direction == Direction.LEFT) {
            return currentIndex > 0;
        } else if (direction == Direction.RIGHT) {
            return currentIndex < buttons.size() - 1;
        }
        return false;
    }

    @Override
    public String getNextLayerName(Direction direction) {
        switch (direction) {
            case LEFT:
                return nextLayerLeft;
            case RIGHT:
                return nextLayerRight;
            case UP:
                return nextLayerUp;
            case DOWN:
                return nextLayerDown;
            default:
                return null;
        }
    }

    @Override
    public boolean handleNavigation(Direction direction) {
        if (buttons.isEmpty())
            return false;

        int nextIndex = currentIndex;
        switch (direction) {
            case LEFT:
                nextIndex = currentIndex - 1;
                break;
            case RIGHT:
                nextIndex = currentIndex + 1;
                break;
            default:
                return false;
        }

        // Check bounds - if out of bounds, return false to allow layer transition
        if (nextIndex < 0 || nextIndex >= buttons.size()) {
            return false;
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
            currentIndex = 0; // Default to first button
        }

        buttons.get(currentIndex).requestFocus();
    }

    @Override
    public void saveFocusState() {
        // Current index is already state
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).hasFocus()) {
                currentIndex = i;
                break;
            }
        }
    }

    @Override
    public View getCurrentFocusedView() {
        for (View v : buttons) {
            if (v.hasFocus())
                return v;
        }
        return null;
    }
}
