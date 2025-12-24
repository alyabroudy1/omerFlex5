package com.omarflex5.ui.navigation;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * FocusLayer implementation for a horizontal RecyclerView.
 * Used for Categories and Movies rows.
 * 
 * Navigation rules:
 * - LEFT/RIGHT: Scroll and navigate between items
 * - UP: Transition to previous layer
 * - DOWN: Transition to next layer
 * 
 * Features:
 * - Remembers adapter position when layer loses focus
 * - Smooth scrolling to target position
 * - Edge blocking (stops at first/last item)
 */
public class RecyclerLayer implements FocusLayer {

    private final String name;
    private final RecyclerView recyclerView;
    private final String nextLayerUp;
    private final String nextLayerDown;
    private int savedAdapterPosition = 0;

    /**
     * @param name          Unique layer name (e.g., "categories", "movies")
     * @param recyclerView  The RecyclerView for this layer
     * @param nextLayerUp   Layer to transition to on UP (null to block)
     * @param nextLayerDown Layer to transition to on DOWN (null to block)
     */
    public RecyclerLayer(String name, RecyclerView recyclerView,
            String nextLayerUp, String nextLayerDown) {
        this.name = name;
        this.recyclerView = recyclerView;
        this.nextLayerUp = nextLayerUp;
        this.nextLayerDown = nextLayerDown;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean containsView(View view) {
        if (view == null)
            return false;

        // Check if view is the RecyclerView itself
        if (view == recyclerView)
            return true;

        // Check if view is a descendant of the RecyclerView
        return isDescendantOf(view, recyclerView);
    }

    @Override
    public boolean canNavigateWithin(Direction direction) {
        // LEFT/RIGHT navigates within, UP/DOWN transitions
        return direction == Direction.LEFT || direction == Direction.RIGHT;
    }

    @Override
    public String getNextLayerName(Direction direction) {
        switch (direction) {
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
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return true;

        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0)
            return true;

        int itemCount = adapter.getItemCount();
        int currentPosition = getCurrentFocusedPosition();

        if (currentPosition == RecyclerView.NO_POSITION) {
            // No current focus, focus first item
            currentPosition = 0;
        }

        // Calculate next position based on direction
        // Note: RTL is already handled by TvFocusController
        int nextPosition = currentPosition;

        switch (direction) {
            case LEFT:
                nextPosition = currentPosition - 1;
                break;
            case RIGHT:
                nextPosition = currentPosition + 1;
                break;
            default:
                return true;
        }

        // Check bounds - block at edges
        if (nextPosition < 0 || nextPosition >= itemCount) {
            return true; // Consume event but don't move
        }

        // Navigate to next position
        savedAdapterPosition = nextPosition;
        navigateToPosition(nextPosition, layoutManager);
        return true;
    }

    @Override
    public void requestFocus() {
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0)
            return;

        // Ensure saved position is valid
        int itemCount = adapter.getItemCount();
        if (savedAdapterPosition >= itemCount) {
            savedAdapterPosition = 0;
        }

        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return;

        navigateToPosition(savedAdapterPosition, layoutManager);
    }

    @Override
    public void saveFocusState() {
        int position = getCurrentFocusedPosition();
        if (position != RecyclerView.NO_POSITION) {
            savedAdapterPosition = position;
        }
    }

    @Override
    public View getCurrentFocusedView() {
        View focused = recyclerView.getFocusedChild();
        if (focused != null)
            return focused;

        // Check if any child has focus
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child.hasFocus()) {
                return child;
            }
        }
        return null;
    }

    /**
     * Get the adapter position of the currently focused item.
     */
    private int getCurrentFocusedPosition() {
        View focused = recyclerView.getFocusedChild();
        if (focused != null) {
            int pos = recyclerView.getChildAdapterPosition(focused);
            if (pos != RecyclerView.NO_POSITION) {
                return pos;
            }
        }

        // Fallback: check all children
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child.hasFocus()) {
                int pos = recyclerView.getChildAdapterPosition(child);
                if (pos != RecyclerView.NO_POSITION) {
                    return pos;
                }
            }
        }

        return savedAdapterPosition;
    }

    /**
     * Navigate to a specific adapter position with smooth scroll.
     */
    private void navigateToPosition(int position, LinearLayoutManager layoutManager) {
        // Try to find view by position (already visible)
        View targetView = layoutManager.findViewByPosition(position);

        if (targetView != null) {
            // View already visible, focus it directly
            targetView.requestFocus();
        } else {
            // View not visible, scroll to it then focus
            recyclerView.smoothScrollToPosition(position);

            // Post focus request after scroll
            recyclerView.postDelayed(() -> {
                View view = layoutManager.findViewByPosition(position);
                if (view != null) {
                    view.requestFocus();
                }
            }, 100);
        }
    }

    /**
     * Get the saved adapter position.
     */
    public int getSavedAdapterPosition() {
        return savedAdapterPosition;
    }

    /**
     * Set the saved adapter position (for external reset).
     */
    public void setSavedAdapterPosition(int position) {
        this.savedAdapterPosition = position;
    }

    private boolean isDescendantOf(View child, View parent) {
        if (child == parent)
            return true;
        if (child.getParent() == parent)
            return true;

        // Walk up the hierarchy
        View current = child;
        while (current.getParent() != null) {
            if (current.getParent() == parent)
                return true;
            if (!(current.getParent() instanceof View))
                break;
            current = (View) current.getParent();
        }
        return false;
    }
}
