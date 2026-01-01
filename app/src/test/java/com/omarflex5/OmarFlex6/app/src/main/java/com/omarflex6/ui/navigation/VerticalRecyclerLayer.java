package com.omarflex6.ui.navigation;

import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * FocusLayer implementation for a VERTICAL RecyclerView.
 * Used for PS4-style sidebar and movie column navigation.
 * 
 * Navigation rules:
 * - UP/DOWN: Scroll and navigate between items within column
 * - LEFT: Transition to previous layer (column to the left)
 * - RIGHT: Transition to next layer (column to the right)
 * 
 * Features:
 * - Remembers adapter position when layer loses focus
 * - Smooth scrolling to target position
 * - Edge blocking (stops at first/last item)
 */
public class VerticalRecyclerLayer implements FocusLayer {

    private final String name;
    private final RecyclerView recyclerView;
    private final String nextLayerLeft;
    private final String nextLayerRight;
    private int savedAdapterPosition = 0;

    /**
     * @param name           Unique layer name (e.g., "sidebar", "movies")
     * @param recyclerView   The RecyclerView for this layer
     * @param nextLayerLeft  Layer to transition to on LEFT (null to block)
     * @param nextLayerRight Layer to transition to on RIGHT (null to block)
     */
    public VerticalRecyclerLayer(String name, RecyclerView recyclerView,
            String nextLayerLeft, String nextLayerRight) {
        this.name = name;
        this.recyclerView = recyclerView;
        this.nextLayerLeft = nextLayerLeft;
        this.nextLayerRight = nextLayerRight;

        // Passively track focus changes to keep savedAdapterPosition in sync with
        // physical reality
        // without overwriting logical "future" state during animations.
        recyclerView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null) {
                View directChild = findDirectChild(newFocus);
                if (directChild != null) {
                    int pos = recyclerView.getChildAdapterPosition(directChild);
                    if (pos != RecyclerView.NO_POSITION) {
                        savedAdapterPosition = pos;
                    }
                }
            }
        });
    }

    private View findDirectChild(View descendant) {
        if (descendant == null)
            return null;
        if (descendant.getParent() == recyclerView)
            return descendant;

        View current = descendant;
        while (current.getParent() != null && current.getParent() instanceof View) {
            if (current.getParent() == recyclerView) {
                return current;
            }
            current = (View) current.getParent();
        }
        return null;
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
        // UP/DOWN navigates within (vertical scrolling), LEFT/RIGHT transitions
        return direction == Direction.UP || direction == Direction.DOWN;
    }

    @Override
    public String getNextLayerName(Direction direction) {
        switch (direction) {
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
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return true;

        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0)
            return true;

        int itemCount = adapter.getItemCount();

        // Use savedAdapterPosition as the source of truth for navigation to allow rapid
        // key presses
        // independent of physical focus lag.
        int currentPosition = savedAdapterPosition;

        // Safety check: if physical focus is wildly different (e.g. touch interaction),
        // maybe sync?
        // For now, prioritize D-pad responsiveness.

        if (currentPosition < 0 || currentPosition >= itemCount) {
            currentPosition = 0;
        }

        // Calculate next position based on direction
        int nextPosition = currentPosition;

        switch (direction) {
            case UP:
                nextPosition = currentPosition - 1;
                break;
            case DOWN:
                nextPosition = currentPosition + 1;
                break;
            default:
                return true;
        }

        // Check bounds - return false to signal edge reached (allow layer transition)
        // Note: We check against 0 and itemCount-1
        if (nextPosition < 0 || nextPosition >= itemCount) {
            return false; // Allow transition
        }

        // Navigate to next position
        savedAdapterPosition = nextPosition;
        navigateToPosition(nextPosition, layoutManager, true); // Smooth scroll for navigation
        return true;
    }

    @Override
    public void requestFocus() {
        Log.d("TvFocus", "VerticalRecyclerLayer requestFocus: " + name + " savedPos: " + savedAdapterPosition);
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0)
            return;

        int itemCount = adapter.getItemCount();
        if (savedAdapterPosition >= itemCount)
            savedAdapterPosition = 0;
        if (savedAdapterPosition < 0)
            savedAdapterPosition = 0;

        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null)
            return;

        // Check if view is already visible
        View existingView = layoutManager.findViewByPosition(savedAdapterPosition);
        if (existingView != null) {
            // View is visible! Just focus it. No scrolling.
            Log.d("TvFocus", "VerticalRecyclerLayer View visible, immediate focus: " + savedAdapterPosition);
            existingView.requestFocus();
        } else {
            // View is hidden/recycled. Needs restoration.
            Log.d("TvFocus", "VerticalRecyclerLayer View hidden, instant scroll: " + savedAdapterPosition);
            navigateToPosition(savedAdapterPosition, layoutManager, false);
        }
    }

    @Override
    public void saveFocusState() {
        // Do NOT overwrite savedAdapterPosition with current physical focus here.
        // Physical focus might be lagging behind logical navigation (animations).
        // relying on handleNavigation + focus listener is safer.
        Log.d("TvFocus", "VerticalRecyclerLayer saveFocusState: " + name + " keeping saved: " + savedAdapterPosition);
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

        // Fallback: check all children for focus
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child.hasFocus()) {
                int pos = recyclerView.getChildAdapterPosition(child);
                if (pos != RecyclerView.NO_POSITION) {
                    return pos;
                }
            }
        }

        Log.d("TvFocus", "VerticalRecyclerLayer logical focus fallback to saved: " + savedAdapterPosition);
        return savedAdapterPosition;
    }

    /**
     * Navigate to a specific adapter position.
     * 
     * @param position Target position
     * @param smooth   If true, use smooth scrolling (navigation). If false, jump
     *                 instantly (restoration).
     */
    private void navigateToPosition(int position, LinearLayoutManager layoutManager, boolean smooth) {
        if (smooth) {
            // Always smooth scroll to center the item
            androidx.recyclerview.widget.LinearSmoothScroller smoothScroller = new androidx.recyclerview.widget.LinearSmoothScroller(
                    recyclerView.getContext()) {
                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
                }
            };
            smoothScroller.setTargetPosition(position);
            layoutManager.startSmoothScroll(smoothScroller);

            // Request focus after delay (standard navigation behavior)
            // Ideally we'd listener for scroll end, but delay is standard for d-pad
            // smoothing
            recyclerView.postDelayed(() -> {
                View view = layoutManager.findViewByPosition(position);
                if (view != null) {
                    view.requestFocus();
                }
            }, 100);
        } else {
            // Instant jump (Restoration)
            layoutManager.scrollToPositionWithOffset(position, 0); // 0 offset = top, calculate center if needed?
            // Better: scrollToPosition might put it at top.
            // But robustness is key.

            // Wait for view to be attached/laid out
            View view = layoutManager.findViewByPosition(position);
            if (view != null) {
                view.requestFocus();
            } else {
                // View not ready yet, wait for it
                waitForViewAndFocus(position);
            }
        }
    }

    private void waitForViewAndFocus(final int position) {
        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                if (recyclerView.getChildAdapterPosition(view) == position) {
                    view.requestFocus();
                    recyclerView.removeOnChildAttachStateChangeListener(this);
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
            }
        });

        // Failsafe: if layout happens but attach listener doesn't fire (sometimes
        // happens if view was recycled but re-bound instantly?)
        recyclerView.post(() -> {
            View v = recyclerView.getLayoutManager().findViewByPosition(position);
            if (v != null)
                v.requestFocus();
        });
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
