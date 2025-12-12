package com.omarflex5.ui.navigation;

import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Navigation delegate for RecyclerView-based layers (Categories, Movies).
 * Handles RTL-aware horizontal scrolling and focus preservation using adapter
 * positions.
 */
public class RecyclerNavigationDelegate implements NavigationDelegate {

    private final com.omarflex5.ui.view.NavigableRecyclerView recyclerView;
    private final boolean isRTL;
    private int savedAdapterPosition = 0;

    // Navigation callbacks
    private NavigationCallback onNavigateUpCallback;
    private NavigationCallback onNavigateDownCallback;

    public interface NavigationCallback {
        boolean onNavigate();
    }

    public RecyclerNavigationDelegate(com.omarflex5.ui.view.NavigableRecyclerView recyclerView, boolean isRTL) {
        this.recyclerView = recyclerView;
        this.isRTL = isRTL;

        // Attach listener to restore focus when views are attached
        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                int position = recyclerView.getChildAdapterPosition(view);
                if (position == savedAdapterPosition && !view.hasFocus()) {
                    view.post(() -> view.requestFocus());
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
            }
        });
    }

    public void setOnNavigateUp(NavigationCallback callback) {
        this.onNavigateUpCallback = callback;
    }

    public void setOnNavigateDown(NavigationCallback callback) {
        this.onNavigateDownCallback = callback;
    }

    @Override
    public boolean onNavigateUp() {
        // Ensure focus search is allowed for vertical navigation
        recyclerView.setBlockFocusSearch(false);

        if (onNavigateUpCallback != null) {
            return onNavigateUpCallback.onNavigate();
        }
        return false;
    }

    @Override
    public boolean onNavigateDown() {
        // Ensure focus search is allowed for vertical navigation
        recyclerView.setBlockFocusSearch(false);

        if (onNavigateDownCallback != null) {
            return onNavigateDownCallback.onNavigate();
        }
        return false;
    }

    @Override
    public boolean onNavigateLeft() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return false;

        View focusedChild = recyclerView.getFocusedChild();
        if (focusedChild == null)
            return false;

        int currentPosition = recyclerView.getChildAdapterPosition(focusedChild);
        if (currentPosition == RecyclerView.NO_POSITION)
            return false;

        // RTL: LEFT = next item (increment position)
        int nextPosition = isRTL ? currentPosition + 1 : currentPosition - 1;

        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null)
            return false;

        // Check if at edge
        boolean atEdge = (isRTL && nextPosition >= adapter.getItemCount()) ||
                (!isRTL && nextPosition < 0);

        if (atEdge) {
            // At edge - block focus search
            recyclerView.setBlockFocusSearch(true);
            return true;
        }

        // Not at edge - allow focus search
        recyclerView.setBlockFocusSearch(false);

        if (nextPosition >= 0 && nextPosition < adapter.getItemCount()) {
            savedAdapterPosition = nextPosition;
            recyclerView.smoothScrollToPosition(nextPosition);

            // Try immediate focus if view is already attached
            View targetView = layoutManager.findViewByPosition(nextPosition);
            if (targetView != null) {
                targetView.requestFocus();
            }
            return true;
        }

        // At edge - consume event to prevent focus escape
        return true;
    }

    @Override
    public boolean onNavigateRight() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return false;

        View focusedChild = recyclerView.getFocusedChild();
        if (focusedChild == null)
            return false;

        int currentPosition = recyclerView.getChildAdapterPosition(focusedChild);
        if (currentPosition == RecyclerView.NO_POSITION)
            return false;

        // RTL: RIGHT = previous item (decrement position)
        int nextPosition = isRTL ? currentPosition - 1 : currentPosition + 1;

        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null)
            return false;

        // Check if at edge
        boolean atEdge = (isRTL && nextPosition < 0) ||
                (!isRTL && nextPosition >= adapter.getItemCount());

        if (atEdge) {
            // At edge - block focus search
            recyclerView.setBlockFocusSearch(true);
            return true;
        }

        // Not at edge - allow focus search
        recyclerView.setBlockFocusSearch(false);

        if (nextPosition >= 0 && nextPosition < adapter.getItemCount()) {
            savedAdapterPosition = nextPosition;
            recyclerView.smoothScrollToPosition(nextPosition);

            // Try immediate focus if view is already attached
            View targetView = layoutManager.findViewByPosition(nextPosition);
            if (targetView != null) {
                targetView.requestFocus();
            }
            return true;
        }

        // At edge - consume event to prevent focus escape
        return true;
    }

    @Override
    public void requestFocus() {
        if (recyclerView.getAdapter() == null || recyclerView.getAdapter().getItemCount() == 0) {
            return;
        }

        // Ensure position is valid
        int itemCount = recyclerView.getAdapter().getItemCount();
        if (savedAdapterPosition >= itemCount) {
            savedAdapterPosition = 0;
        }

        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return;

        // Try to find view by position
        View targetView = layoutManager.findViewByPosition(savedAdapterPosition);
        if (targetView != null) {
            targetView.requestFocus();
        } else {
            // View not attached yet, scroll to position and post focus request
            recyclerView.scrollToPosition(savedAdapterPosition);
            recyclerView.post(() -> {
                View view = layoutManager.findViewByPosition(savedAdapterPosition);
                if (view != null) {
                    view.requestFocus();
                }
            });
        }
    }

    @Override
    public void saveFocusState() {
        View focusedChild = recyclerView.getFocusedChild();
        if (focusedChild != null) {
            int position = recyclerView.getChildAdapterPosition(focusedChild);
            if (position != RecyclerView.NO_POSITION) {
                savedAdapterPosition = position;
            }
        }
    }

    @Override
    public void resetToPosition(int position) {
        savedAdapterPosition = position;
        requestFocus();
    }

    public int getSavedAdapterPosition() {
        return savedAdapterPosition;
    }
}
