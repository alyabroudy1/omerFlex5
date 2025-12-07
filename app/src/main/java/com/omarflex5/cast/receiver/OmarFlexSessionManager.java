package com.omarflex5.cast.receiver;

import android.content.Context;
import android.content.SharedPreferences;

public class OmarFlexSessionManager {
    private static final String PREF_NAME = "omarflex_cast_session";
    private static final String KEY_IP = "last_ip";
    private static final String KEY_PORT = "last_port";
    private static final String KEY_NAME = "last_name";
    private static final String KEY_IS_CONNECTED = "is_connected";

    private static OmarFlexSessionManager instance;
    private final SharedPreferences prefs;

    private OmarFlexSessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized OmarFlexSessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new OmarFlexSessionManager(context);
        }
        return instance;
    }

    public void connect(NsdDiscovery.OmarFlexDevice device) {
        prefs.edit()
                .putString(KEY_IP, device.host)
                .putInt(KEY_PORT, device.port)
                .putString(KEY_NAME, device.name)
                .putBoolean(KEY_IS_CONNECTED, true)
                .apply();
    }

    public void disconnect() {
        prefs.edit().putBoolean(KEY_IS_CONNECTED, false).apply();
    }

    public boolean isConnected() {
        return prefs.getBoolean(KEY_IS_CONNECTED, false);
    }

    public NsdDiscovery.OmarFlexDevice getCurrentDevice() {
        if (!isConnected())
            return null;
        return getLastUsedDevice();
    }

    public NsdDiscovery.OmarFlexDevice getLastUsedDevice() {
        String ip = prefs.getString(KEY_IP, null);
        String name = prefs.getString(KEY_NAME, null);
        int port = prefs.getInt(KEY_PORT, 0);

        if (ip != null && name != null && port != 0) {
            return new NsdDiscovery.OmarFlexDevice(name, ip, port);
        }
        return null;
    }
}
