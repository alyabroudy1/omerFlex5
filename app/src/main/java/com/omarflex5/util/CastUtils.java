package com.omarflex5.util;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

public class CastUtils {

    /**
     * Checks if the current device is a TV.
     * Use this to hide Cast button on TV devices.
     */
    public static boolean isTv(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }

        PackageManager pm = context.getPackageManager();
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            return true;
        }

        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
            return true;
        }

        return false;
    }
}
