package com.omarflex6.controller.base;

import android.content.Intent;
import androidx.annotation.NonNull;

/**
 * Base definition for a Controller in the application.
 * Defines lifecycle hooks and activity result handling.
 */
public interface BaseController {
    /**
     * Called when the controller is attached to the view/activity.
     */
    void onAttach();

    /**
     * Called when the controller is detached/destroyed.
     */
    void onDetach();

    /**
     * Handle activity results.
     * 
     * @return true if consumed, false otherwise.
     */
    boolean handleActivityResult(int requestCode, int resultCode, Intent data);
}
