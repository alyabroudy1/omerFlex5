package com.omarflex5.ui.navigation;

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
    private final String nextLayerUp;
    private final String nextLayerDown;
    private int savedAdapterPosition = 0;

    /**
     * @param name           Unique layer name (e.g., "sidebar", "movies")
     * @param recyclerView   The RecyclerView for this layer
     * @param nextLayerLeft  Layer to transition to on LEFT (null to block)
     * @param nextLayerRight Layer to transition to on RIGHT (null to block)
     * @param nextLayerUp    Layer to transition to on UP (null to block)
     * @param nextLayerDown  Layer to transition to on DOWN (null to block)
     */
    public VerticalRecyclerLayer(String name, RecyclerView recyclerView,
            String nextLayerLeft, String nextLayerRight, String nextLayerUp, String nextLayerDown) {
        this.name = name;
        this.recyclerView = recyclerView;
        this.nextLayerLeft = nextLayerLeft;
        this.nextLayerRight = nextLayerRight;
        this.nextLayerUp = nextLayerUp;
        this.nextLayerDown = nextLayerDown;

        // Passively track focus changes to keep savedAdapterPosition in sync
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

        if (view == recyclerView)
            return true;

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
        int currentPosition = savedAdapterPosition;

        if (currentPosition < 0 || currentPosition >= itemCount) {
            currentPosition = 0;
        }

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

        // Check bounds - return false to signal edge reached
        if (nextPosition < 0 || nextPosition >= itemCount) {
            return false;
        }

        // Navigate to next position
        savedAdapterPosition = nextPosition;
        navigateToPosition(nextPosition, layoutManager, true);
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

        View existingView = layoutManager.findViewByPosition(savedAdapterPosition);
        if (existingView != null) {
            Log.d("TvFocus", "VerticalRecyclerLayer View visible, immediate focus: " + savedAdapterPosition);
            existingView.requestFocus();
        } else {
            Log.d("TvFocus", "VerticalRecyclerLayer View hidden, instant scroll: " + savedAdapterPosition);
            navigateToPosition(savedAdapterPosition, layoutManager, false);
        }
    }

    @Override
    public void saveFocusState() {
        Log.d("TvFocus", "VerticalRecyclerLayer saveFocusState: " + name + " keeping saved: " + savedAdapterPosition);
    }

    @Override
    public View getCurrentFocusedView() {
        View focused = recyclerView.getFocusedChild();
        if (focused != null)
            return focused;

        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child.hasFocus()) {
                return child;
            }
        }
        return null;
    }

    private void navigateToPosition(int position, LinearLayoutManager layoutManager, boolean smooth) {
        if (smooth) {
            androidx.recyclerview.widget.LinearSmoothScroller smoothScroller = new androidx.recyclerview.widget.LinearSmoothScroller(
                    recyclerView.getContext()) {
                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
                }
            };
            smoothScroller.setTargetPosition(position);
            layoutManager.startSmoothScroll(smoothScroller);

            recyclerView.postDelayed(() -> {
                View view = layoutManager.findViewByPosition(position);
                if (view != null) {
                    view.requestFocus();
                }
            }, 100);
        } else {
            layoutManager.scrollToPositionWithOffset(position, 0);

            View view = layoutManager.findViewByPosition(position);
            if (view != null) {
                view.requestFocus();
            } else {
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

        recyclerView.post(() -> {
            View v = recyclerView.getLayoutManager().findViewByPosition(position);
            if (v != null)
                v.requestFocus();
        });
    }

    public int getSavedAdapterPosition() {
        return savedAdapterPosition;
    }

    public void setSavedAdapterPosition(int position) {
        this.savedAdapterPosition = position;
    }

    private boolean isDescendantOf(View child, View parent) {
        if (child == parent)
            return true;
        if (child.getParent() == parent)
            return true;

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
