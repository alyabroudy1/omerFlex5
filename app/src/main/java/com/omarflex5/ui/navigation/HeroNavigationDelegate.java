package com.omarflex5.ui.navigation;

import android.view.View;

/**
 * Navigation delegate for Hero layer (mute/fullscreen buttons).
 * Handles focus switching between buttons and vertical navigation to
 * categories.
 */
public class HeroNavigationDelegate implements NavigationDelegate {

    private final View btnMute;
    private final View btnFullscreen;
    private View lastFocusedButton;

    private NavigationCallback onNavigateDownCallback;

    public interface NavigationCallback {
        boolean onNavigate();
    }

    public HeroNavigationDelegate(View btnMute, View btnFullscreen) {
        this.btnMute = btnMute;
        this.btnFullscreen = btnFullscreen;
        this.lastFocusedButton = btnMute; // Default to mute button
    }

    public void setOnNavigateDown(NavigationCallback callback) {
        this.onNavigateDownCallback = callback;
    }

    @Override
    public boolean onNavigateUp() {
        // Block - can't go up from hero layer
        return true;
    }

    @Override
    public boolean onNavigateDown() {
        saveFocusState();
        if (onNavigateDownCallback != null) {
            return onNavigateDownCallback.onNavigate();
        }
        return false;
    }

    @Override
    public boolean onNavigateLeft() {
        // RTL: Left = next button (mute → fullscreen)
        View currentFocus = getCurrentFocus();
        if (currentFocus == btnMute) {
            btnFullscreen.requestFocus();
            return true;
        }
        return false; // At edge
    }

    @Override
    public boolean onNavigateRight() {
        // RTL: Right = previous button (fullscreen → mute)
        View currentFocus = getCurrentFocus();
        if (currentFocus == btnFullscreen) {
            btnMute.requestFocus();
            return true;
        }
        return false; // At edge
    }

    @Override
    public void requestFocus() {
        if (lastFocusedButton != null && lastFocusedButton.isAttachedToWindow()) {
            lastFocusedButton.requestFocus();
        } else {
            btnMute.requestFocus();
        }
    }

    @Override
    public void saveFocusState() {
        View currentFocus = getCurrentFocus();
        if (currentFocus == btnMute || currentFocus == btnFullscreen) {
            lastFocusedButton = currentFocus;
        }
    }

    @Override
    public void resetToPosition(int position) {
        // Hero doesn't use positions, reset to mute button
        lastFocusedButton = btnMute;
        requestFocus();
    }

    private View getCurrentFocus() {
        if (btnMute.hasFocus())
            return btnMute;
        if (btnFullscreen.hasFocus())
            return btnFullscreen;
        return lastFocusedButton;
    }
}
