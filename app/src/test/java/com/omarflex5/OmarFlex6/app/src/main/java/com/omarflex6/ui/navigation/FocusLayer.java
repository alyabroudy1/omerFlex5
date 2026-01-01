package com.omarflex6.ui.navigation;

import android.view.View;

public interface FocusLayer {
    enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    String getName();

    boolean containsView(View view);

    boolean canNavigateWithin(Direction direction);

    String getNextLayerName(Direction direction);

    boolean handleNavigation(Direction direction);

    void requestFocus();

    void saveFocusState();

    View getCurrentFocusedView();
}
