package com.omarflex5.ui.layout;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * ScreenDimensionManager - Singleton for dynamic dimension calculations.
 * 
 * Detects device type (PHONE/TABLET/TV) based on screen width and calculates
 * appropriate layout dimensions. Also handles RTL detection.
 */
public class ScreenDimensionManager {

    private static final String TAG = "ScreenDimensionManager";
    private static ScreenDimensionManager instance;

    public enum DeviceType {
        PHONE, // < 600dp width
        TABLET, // 600-960dp width
        TV // > 960dp width
    }

    // Screen metrics
    private int screenWidthPx;
    private int screenHeightPx;
    private int screenWidthDp;
    private int screenHeightDp;
    private float density;
    private boolean isRtl;
    private DeviceType deviceType;

    // Calculated dimensions in DP
    private int sidebarWidthDp;
    private int cardWidthDp;
    private int cardHeightDp;
    private int heroWidthPercent;
    private int moviesWidthPercent;
    private int sidebarWidthPercent;

    private ScreenDimensionManager() {
        // Private constructor
    }

    public static synchronized ScreenDimensionManager getInstance() {
        if (instance == null) {
            instance = new ScreenDimensionManager();
        }
        return instance;
    }

    /**
     * Initialize with context. Call this in onCreate of main activity.
     */
    public void initialize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        this.density = metrics.density;
        this.screenWidthPx = metrics.widthPixels;
        this.screenHeightPx = metrics.heightPixels;
        this.screenWidthDp = (int) (screenWidthPx / density);
        this.screenHeightDp = (int) (screenHeightPx / density);

        // Detect RTL
        this.isRtl = context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        // Detect device type and calculate dimensions
        detectDeviceType();
        calculateDimensions();
        logAllDimensions();
    }

    private void detectDeviceType() {
        if (screenWidthDp < 600) {
            deviceType = DeviceType.PHONE;
        } else if (screenWidthDp < 960) {
            deviceType = DeviceType.TABLET;
        } else {
            deviceType = DeviceType.TV;
        }
    }

    private void calculateDimensions() {
        switch (deviceType) {
            case PHONE:
                sidebarWidthPercent = 15;
                moviesWidthPercent = 30;
                heroWidthPercent = 55;
                sidebarWidthDp = 80;
                cardWidthDp = (int) (screenWidthDp * 0.25);
                cardHeightDp = (int) (cardWidthDp * 1.4); // 5:7 aspect ratio
                break;

            case TABLET:
                sidebarWidthPercent = 12;
                moviesWidthPercent = 28;
                heroWidthPercent = 60;
                sidebarWidthDp = 100;
                cardWidthDp = (int) (screenWidthDp * 0.22);
                cardHeightDp = (int) (cardWidthDp * 1.4);
                break;

            case TV:
            default:
                sidebarWidthPercent = 12;
                moviesWidthPercent = 25;
                heroWidthPercent = 63;
                sidebarWidthDp = 120;
                cardWidthDp = (int) (screenWidthDp * 0.20);
                cardHeightDp = (int) (cardWidthDp * 1.4);
                break;
        }
    }

    private void logAllDimensions() {
        Log.d(TAG, "╔══════════════════════════════════════════════════════════════╗");
        Log.d(TAG, "║              SCREEN DIMENSION MANAGER                        ║");
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════╣");
        Log.d(TAG, "║ Screen: " + screenWidthPx + "×" + screenHeightPx + " px (" + screenWidthDp + "×" + screenHeightDp
                + " dp)");
        Log.d(TAG, "║ Density: " + density);
        Log.d(TAG, "║ Device Type: " + deviceType.name());
        Log.d(TAG, "║ RTL Mode: " + isRtl);
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════╣");
        Log.d(TAG, "║ Layout Weights:");
        Log.d(TAG, "║   Sidebar: " + sidebarWidthPercent + "%");
        Log.d(TAG, "║   Movies:  " + moviesWidthPercent + "%");
        Log.d(TAG, "║   Hero:    " + heroWidthPercent + "%");
        Log.d(TAG, "╠══════════════════════════════════════════════════════════════╣");
        Log.d(TAG, "║ Card Size: " + cardWidthDp + "dp × " + cardHeightDp + "dp");
        Log.d(TAG, "╚══════════════════════════════════════════════════════════════╝");
    }

    // Getters
    public int getScreenWidthPx() {
        return screenWidthPx;
    }

    public int getScreenHeightPx() {
        return screenHeightPx;
    }

    public int getScreenWidthDp() {
        return screenWidthDp;
    }

    public int getScreenHeightDp() {
        return screenHeightDp;
    }

    public float getDensity() {
        return density;
    }

    public boolean isRtl() {
        return isRtl;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public int getSidebarWidthDp() {
        return sidebarWidthDp;
    }

    public int getCardWidthDp() {
        return cardWidthDp;
    }

    public int getCardHeightDp() {
        return cardHeightDp;
    }

    public int getCardWidthPx() {
        return (int) (cardWidthDp * density);
    }

    public int getCardHeightPx() {
        return (int) (cardHeightDp * density);
    }

    public float getSidebarWeight() {
        return sidebarWidthPercent / 100f;
    }

    public float getMoviesWeight() {
        return moviesWidthPercent / 100f;
    }

    public float getHeroWeight() {
        return heroWidthPercent / 100f;
    }
}
