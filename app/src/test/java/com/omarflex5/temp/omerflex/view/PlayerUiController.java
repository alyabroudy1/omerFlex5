package com.omarflex5.temp.omerflex.view;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;


import com.omerflex.service.PlayerManager;

public class PlayerUiController {

    private final AppCompatActivity activity;
    private final PlayerView playerView;
    private final PlayerManager playerManager;

    private long backPressedTime;
    private float initialX = 0;

    private static final long SEEK_DURATION_MS = 1000;
    private static final float MIN_SEEK_DISTANCE = 100;

    public PlayerUiController(AppCompatActivity activity, PlayerView playerView, PlayerManager playerManager) {
        this.activity = activity;
        this.playerView = playerView;
        this.playerManager = playerManager;
    }

    public View.OnTouchListener getOnTouchListener() {
        return (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = event.getX();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    handleTouchMove(event);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!playerView.isControllerFullyVisible()) {
                        playerView.showController();
                    }
                    return true;
            }
            return false;
        };
    }

    private void handleTouchMove(MotionEvent event) {
        Player player = playerManager.getCurrentPlayer();
        if (player == null) return;

        float deltaX = event.getX() - initialX;
        if (Math.abs(deltaX) > MIN_SEEK_DISTANCE) {
            if (deltaX > 0) {
                player.seekTo(player.getCurrentPosition() + SEEK_DURATION_MS);
            } else {
                player.seekTo(player.getCurrentPosition() - SEEK_DURATION_MS);
            }
            initialX = event.getX();
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!playerView.isControllerFullyVisible()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                // Controller is hidden, and back is pressed.
                // Let the activity handle the back press, which should trigger handleBackPressed().
                // Returning false allows the event to propagate.
                return false;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                playerView.showController();
            }
            // Let the default PlayerView behavior handle key events when controller is hidden.
            return playerView.dispatchKeyEvent(event);
        }

        // Controller is visible, implement custom logic
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View focusedView = activity.getCurrentFocus();
            View bottomBar = playerView.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);
            View centerControls = playerView.findViewById(androidx.media3.ui.R.id.exo_center_controls);

            boolean isButtonFocused = false;
            if (focusedView != null && focusedView != playerView) {
                 // Check if the focused view is a descendant of the main control layouts
                if ((bottomBar != null && isDescendant(focusedView, bottomBar)) ||
                    (centerControls != null && isDescendant(focusedView, centerControls))) {
                    isButtonFocused = true;
                }
            }


            Player player = playerManager.getCurrentPlayer();
            if (player == null) return false;

            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (isButtonFocused) {
                        return false; // Let the framework handle navigation
                    }
                    // No button is focused, so seek
                    player.seekTo(player.getCurrentPosition() + 15000);
                    return true;

                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (isButtonFocused) {
                        return false; // Let the framework handle navigation
                    }
                    // No button is focused, so seek
                    player.seekTo(player.getCurrentPosition() - 15000);
                    return true;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (!isButtonFocused) {
                        // If no button is focused, treat as play/pause
                        if (player.isPlaying()) {
                            player.pause();
                        } else {
                            player.play();
                        }
                        return true;
                    }
                    // A button is focused, let the framework handle the click
                    return false;
            }
        }
        // For other keys or ACTION_UP, let the framework handle it.
        return false;
    }

    private boolean isDescendant(View view, View ancestor) {
        if (view == null || ancestor == null) {
            return false;
        }
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent == ancestor) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }


    public void handleBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            activity.finish();
        } else {
            backPressedTime = System.currentTimeMillis();
        }
    }
}
