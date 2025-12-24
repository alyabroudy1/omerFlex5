package com.omarflex5.ui.navigation;

import android.view.View;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * FocusLayer implementation for a grid RecyclerView.
 * Used for Search Results.
 * 
 * Navigation rules:
 * - LEFT/RIGHT: Navigate between columns (RTL aware)
 * - UP/DOWN: Navigate between rows
 * - Transitions: Block/Transition at edges
 */
public class GridLayer implements FocusLayer {

    private final String name;
    private final RecyclerView recyclerView;
    private final String nextLayerUp;
    private final String nextLayerDown;
    private int savedAdapterPosition = 0;

    public GridLayer(String name, RecyclerView recyclerView, String nextLayerUp, String nextLayerDown) {
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
        if (view == recyclerView)
            return true;

        // Check ancestry
        View current = view;
        while (current.getParent() != null) {
            if (current.getParent() == recyclerView)
                return true;
            if (!(current.getParent() instanceof View))
                break;
            current = (View) current.getParent();
        }
        return false;
    }

    @Override
    public boolean canNavigateWithin(Direction direction) {
        // Always attempt internal navigation first, fall back to layer transition if at
        // edge
        return true;
    }

    @Override
    public String getNextLayerName(Direction direction) {
        // NOTE: This is only called if handleNavigation returns false
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
        GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return false;

        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0)
            return false;

        int itemCount = adapter.getItemCount();
        int spanCount = layoutManager.getSpanCount();
        int currentPosition = getCurrentFocusedPosition();

        if (currentPosition == RecyclerView.NO_POSITION) {
            currentPosition = 0;
        }

        int nextPosition = currentPosition;

        switch (direction) {
            case LEFT:
                // RTL aware: LEFT decreases index
                nextPosition = currentPosition - 1;
                break;
            case RIGHT:
                // RTL aware: RIGHT increases index
                nextPosition = currentPosition + 1;
                break;
            case UP:
                nextPosition = currentPosition - spanCount;
                break;
            case DOWN:
                nextPosition = currentPosition + spanCount;
                break;
        }

        // Validity checks
        if (nextPosition < 0) {
            // Cannot go up/left (or beyond start)
            return false; // Delegate to transition (e.g. UP -> header)
        }

        if (nextPosition >= itemCount) {
            // Cannot go down/right (or beyond end)
            return false; // Delegate to transition (e.g. DOWN -> footer)
        }

        // Specific Edge Case for Grid:
        // If LEFT at start of row (pos % span == 0), should we wrap? No, usually block.
        // If RIGHT at end of row, should we wrap? Yes, usually standard behavior moves
        // to next row.

        // HOWEVER, we want predictable D-pad navigation.
        // Let's stick to simple index math for now.
        // If user presses UP at index 0, next is -spanCount (-ve), so we return false
        // -> Header.
        // If user presses DOWN at last index, next is +spanCount (>= count), so we
        // return false -> Footer.

        navigateToPosition(nextPosition, layoutManager);
        savedAdapterPosition = nextPosition;
        return true;
    }

    @Override
    public void requestFocus() {
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0)
            return;

        if (savedAdapterPosition >= adapter.getItemCount()) {
            savedAdapterPosition = 0;
        }

        navigateToPosition(savedAdapterPosition, (GridLayoutManager) recyclerView.getLayoutManager());
    }

    @Override
    public void saveFocusState() {
        int pos = getCurrentFocusedPosition();
        if (pos != RecyclerView.NO_POSITION) {
            savedAdapterPosition = pos;
        }
    }

    @Override
    public View getCurrentFocusedView() {
        return recyclerView.getFocusedChild(); // Simplified
    }

    private int getCurrentFocusedPosition() {
        View focused = recyclerView.getFocusedChild();
        if (focused != null) {
            return recyclerView.getChildAdapterPosition(focused);
        }
        return savedAdapterPosition;
    }

    private void navigateToPosition(int position, GridLayoutManager layoutManager) {
        View targetView = layoutManager.findViewByPosition(position);
        if (targetView != null) {
            targetView.requestFocus();
        } else {
            recyclerView.smoothScrollToPosition(position);
            recyclerView.postDelayed(() -> {
                View view = layoutManager.findViewByPosition(position);
                if (view != null) {
                    view.requestFocus();
                }
            }, 100);
        }
    }
}
