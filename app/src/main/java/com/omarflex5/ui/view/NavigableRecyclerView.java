package com.omarflex5.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Custom RecyclerView that allows blocking Android's automatic focus search.
 * This prevents focus from jumping to other layers when navigating at edges.
 * 
 * Used in HomeActivity for categories and movies RecyclerViews to maintain
 * focus within each layer during D-pad navigation.
 */
public class NavigableRecyclerView extends RecyclerView {

    private boolean blockFocusSearch = false;

    public NavigableRecyclerView(@NonNull Context context) {
        super(context);
    }

    public NavigableRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public NavigableRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Override focusSearch to block Android's automatic focus search when needed.
     * This is the key method that prevents focus from jumping between layers.
     * 
     * @param focused   The currently focused view
     * @param direction The direction to search (FOCUS_LEFT, FOCUS_RIGHT, etc.)
     * @return The next view to focus, or the current view if blocked
     */
    @Override
    public View focusSearch(View focused, int direction) {
        if (blockFocusSearch) {
            // Block focus search - return focused view to prevent navigation
            return focused;
        }
        // Allow normal focus search
        return super.focusSearch(focused, direction);
    }

    /**
     * Enable or disable focus search blocking.
     * Should be called by NavigationDelegate when at edges.
     * 
     * @param block true to block focus search, false to allow
     */
    public void setBlockFocusSearch(boolean block) {
        this.blockFocusSearch = block;
    }

    /**
     * Check if focus search is currently blocked.
     * 
     * @return true if blocking, false otherwise
     */
    public boolean isBlockingFocusSearch() {
        return blockFocusSearch;
    }
}
