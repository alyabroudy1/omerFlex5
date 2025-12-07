package com.omarflex5.cast.dlna;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages the "Connected" state for DLNA devices.
 * Persists the last used device for quick reconnection.
 */
public class DlnaSessionManager {

    private static final String PREF_NAME = "dlna_prefs";
    private static final String KEY_LAST_DEVICE_LOCATION = "last_device_location";
    private static final String KEY_LAST_DEVICE_NAME = "last_device_name";
    private static final String KEY_LAST_DEVICE_SERVER = "last_device_server";

    private static DlnaSessionManager instance;
    private SsdpDiscoverer.DlnaDevice currentDevice;
    private final SharedPreferences prefs;

    private DlnaSessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized DlnaSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new DlnaSessionManager(context);
        }
        return instance;
    }

    public void connect(SsdpDiscoverer.DlnaDevice device) {
        this.currentDevice = device;
        // Save as last used
        prefs.edit()
                .putString(KEY_LAST_DEVICE_LOCATION, device.location)
                .putString(KEY_LAST_DEVICE_NAME, device.friendlyName)
                .putString(KEY_LAST_DEVICE_SERVER, device.server)
                .apply();
    }

    public void disconnect() {
        this.currentDevice = null;
    }

    public boolean isConnected() {
        return currentDevice != null;
    }

    public SsdpDiscoverer.DlnaDevice getCurrentDevice() {
        return currentDevice;
    }

    public SsdpDiscoverer.DlnaDevice getLastUsedDevice() {
        String location = prefs.getString(KEY_LAST_DEVICE_LOCATION, null);
        if (location == null)
            return null;

        SsdpDiscoverer.DlnaDevice device = new SsdpDiscoverer.DlnaDevice();
        device.location = location;
        device.friendlyName = prefs.getString(KEY_LAST_DEVICE_NAME, "Unknown Device");
        device.server = prefs.getString(KEY_LAST_DEVICE_SERVER, "");
        return device;
    }
}
