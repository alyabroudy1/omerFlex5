package com.omarflex6.ui.navigation;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.util.Log;
import com.omarflex6.util.SoundManager;
import java.util.HashMap;
import java.util.Map;

public class TvFocusController {
    private final Map<String, FocusLayer> layers = new HashMap<>();
    private FocusLayer currentLayer;
    private final boolean isRtl;
    private boolean debugEnabled = false;

    public interface OnLayerChangeListener {
        void onLayerChanged(String oldLayer, String newLayer);
    }

    private OnLayerChangeListener layerChangeListener;

    private SoundManager soundManager;
    private Handler handler;

    public TvFocusController(boolean isRtl) {
        this.isRtl = isRtl;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void setSoundManager(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    public void setOnLayerChangeListener(OnLayerChangeListener listener) {
        this.layerChangeListener = listener;
    }

    public void registerLayer(FocusLayer layer) {
        layers.put(layer.getName(), layer);
    }

    public void setCurrentLayer(String layerName) {
        FocusLayer layer = layers.get(layerName);
        if (layer != null) {
            String oldLayerName = currentLayer != null ? currentLayer.getName() : null;
            if (currentLayer != null) {
                currentLayer.saveFocusState();
            }
            currentLayer = layer;

            if (layerChangeListener != null && !layerName.equals(oldLayerName)) {
                layerChangeListener.onLayerChanged(oldLayerName, layerName);
            }
        }
    }

    public String getCurrentLayerName() {
        return currentLayer != null ? currentLayer.getName() : null;
    }

    public FocusLayer getCurrentLayer() {
        return currentLayer;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN)
            return false;
        if (currentLayer == null)
            return false;

        if (debugEnabled)
            Log.d("TvFocus", "Dispatching Key: " + event.getKeyCode() + " Action: " + event.getAction());

        FocusLayer.Direction direction = null;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                direction = FocusLayer.Direction.UP;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                direction = FocusLayer.Direction.DOWN;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                direction = FocusLayer.Direction.LEFT;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                direction = FocusLayer.Direction.RIGHT;
                break;
        }

        if (direction == null)
            return false;

        // Handle RTL if needed (swap Left/Right)
        if (isRtl) {
            if (direction == FocusLayer.Direction.LEFT)
                direction = FocusLayer.Direction.RIGHT;
            else if (direction == FocusLayer.Direction.RIGHT)
                direction = FocusLayer.Direction.LEFT;
        }

        if (debugEnabled)
            Log.d("TvFocus", "Direction: " + direction + " CurrentLayer: "
                    + (currentLayer != null ? currentLayer.getName() : "null"));

        if (currentLayer.canNavigateWithin(direction)) {
            if (currentLayer.handleNavigation(direction)) {
                if (soundManager != null)
                    soundManager.playNav(currentLayer.getCurrentFocusedView());
                if (debugEnabled)
                    Log.d("TvFocus", "Handled within layer: " + currentLayer.getName());
                return true;
            }
        }

        // Try switching layers
        String nextLayerName = currentLayer.getNextLayerName(direction);
        if (debugEnabled)
            Log.d("TvFocus", "Next Layer Name: " + nextLayerName);

        if (nextLayerName != null) {
            FocusLayer nextLayer = layers.get(nextLayerName);
            if (nextLayer != null) {
                if (debugEnabled)
                    Log.d("TvFocus", "Switching to layer: " + nextLayerName);

                String oldLayerName = currentLayer.getName();
                if (currentLayer != null)
                    currentLayer.saveFocusState();
                currentLayer = nextLayer;

                if (layerChangeListener != null) {
                    layerChangeListener.onLayerChanged(oldLayerName, nextLayerName);
                }

                currentLayer.requestFocus();
                if (soundManager != null)
                    soundManager.playNav(currentLayer.getCurrentFocusedView());
                return true;
            } else {
                if (debugEnabled)
                    Log.e("TvFocus", "Target layer not found: " + nextLayerName);
            }
        }

        if (debugEnabled)
            Log.d("TvFocus", "Unhandled event");
        return false;
    }
}
