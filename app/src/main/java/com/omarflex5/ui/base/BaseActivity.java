package com.omarflex5.ui.base;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base Activity that handles double-back-to-exit logic for all activities.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private long backPressedTime;
    private Toast backToast;
    protected boolean doubleBackToExitPressedOnce = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Add default back press callback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleDoubleBack();
            }
        });
    }

    /**
     * Handles the double back press logic.
     * Can be overridden by child activities if they need custom logic BEFORE this
     * check.
     */
    protected void handleDoubleBack() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            if (backToast != null) {
                backToast.cancel();
            }
            // Actually exit/finish the activity
            finish();
        } else {
            backToast = Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT);
            backToast.show();
        }
        backPressedTime = System.currentTimeMillis();
    }
}
