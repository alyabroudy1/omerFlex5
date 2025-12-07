package com.omarflex5.temp.omerflex.service.casting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Network State Manager for monitoring network connectivity changes
 * Provides real-time network status and WiFi information
 */
public class NetworkStateManager {
    private static final String TAG = "NetworkStateManager";
    
    private final Context context;
    private final ConnectivityManager connectivityManager;
    private final WifiManager wifiManager;
    private final Handler mainHandler;
    private final CopyOnWriteArrayList<NetworkStateListener> listeners;
    
    private NetworkChangeReceiver networkReceiver;
    private WifiStateReceiver wifiReceiver;
    private boolean isListening = false;
    
    public interface NetworkStateListener {
        void onNetworkConnected(NetworkInfo networkInfo);
        void onNetworkDisconnected();
        void onWifiConnected(WifiInfo wifiInfo);
        void onWifiDisconnected();
        void onNetworkTypeChanged(int networkType);
        void onSignalStrengthChanged(int signalStrength);
    }
    
    public NetworkStateManager(Context context) {
        this.context = context;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.listeners = new CopyOnWriteArrayList<>();
    }
    
    /**
     * Start listening for network state changes
     */
    public void startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening for network changes");
            return;
        }
        
        // Register network change receiver
        networkReceiver = new NetworkChangeReceiver();
        IntentFilter networkFilter = new IntentFilter();
        networkFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(networkReceiver, networkFilter);
        
        // Register WiFi state receiver
        wifiReceiver = new WifiStateReceiver();
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        context.registerReceiver(wifiReceiver, wifiFilter);
        
        isListening = true;
        Log.d(TAG, "Started listening for network state changes");
    }
    
    /**
     * Stop listening for network state changes
     */
    public void stopListening() {
        if (!isListening) {
            return;
        }
        
        try {
            if (networkReceiver != null) {
                context.unregisterReceiver(networkReceiver);
                networkReceiver = null;
            }
            
            if (wifiReceiver != null) {
                context.unregisterReceiver(wifiReceiver);
                wifiReceiver = null;
            }
            
            isListening = false;
            Log.d(TAG, "Stopped listening for network state changes");
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receivers", e);
        }
    }
    
    /**
     * Add a network state listener
     */
    public void addNetworkStateListener(NetworkStateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove a network state listener
     */
    public void removeNetworkStateListener(NetworkStateListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Check if network is currently connected
     */
    public boolean isNetworkConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && 
                   (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }
    
    /**
     * Check if WiFi is currently connected
     */
    public boolean isWifiConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } else {
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return networkInfo != null && networkInfo.isConnected();
        }
    }
    
    /**
     * Get current WiFi connection information
     */
    public WifiInfo getWifiConnectionInfo() {
        if (isWifiConnected()) {
            return wifiManager.getConnectionInfo();
        }
        return null;
    }
    
    /**
     * Get current network type
     */
    public int getCurrentNetworkType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return -1;
            
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null) return -1;
            
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return ConnectivityManager.TYPE_WIFI;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return ConnectivityManager.TYPE_MOBILE;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return ConnectivityManager.TYPE_ETHERNET;
            }
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                return networkInfo.getType();
            }
        }
        return -1;
    }
    
    /**
     * Get current IP address
     */
    public String getLocalIpAddress() {
        try {
            // For WiFi connection
            if (isWifiConnected()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                int ipAddress = wifiInfo.getIpAddress();
                return String.format("%d.%d.%d.%d",
                        (ipAddress & 0xff),
                        (ipAddress >> 8 & 0xff),
                        (ipAddress >> 16 & 0xff),
                        (ipAddress >> 24 & 0xff));
            }
            
            // For other network interfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address.getHostAddress().indexOf(':') == -1) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting IP address", e);
        }
        return null;
    }
    
    /**
     * Get WiFi signal strength
     */
    public int getWifiSignalStrength() {
        if (isWifiConnected()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo.getRssi(); // Returns dBm value
        }
        return Integer.MIN_VALUE;
    }
    
    /**
     * Get WiFi signal level (0-5)
     */
    public int getWifiSignalLevel() {
        int rssi = getWifiSignalStrength();
        if (rssi == Integer.MIN_VALUE) return 0;
        
        // Convert RSSI to signal level (0-5)
        return WifiManager.calculateSignalLevel(rssi, 6);
    }
    
    private class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network network = connectivityManager.getActiveNetwork();
                    NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    
                    boolean isConnected = capabilities != null && 
                                         (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                                          capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                    
                    if (isConnected) {
                        // Notify connected
                        mainHandler.post(() -> {
                            for (NetworkStateListener listener : listeners) {
                                listener.onNetworkConnected(null);
                            }
                        });
                    } else {
                        // Notify disconnected
                        mainHandler.post(() -> {
                            for (NetworkStateListener listener : listeners) {
                                listener.onNetworkDisconnected();
                            }
                        });
                    }
                } else {
                    NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    
                    if (networkInfo != null && networkInfo.isConnected()) {
                        mainHandler.post(() -> {
                            for (NetworkStateListener listener : listeners) {
                                listener.onNetworkConnected(networkInfo);
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            for (NetworkStateListener listener : listeners) {
                                listener.onNetworkDisconnected();
                            }
                        });
                    }
                }
            }
        }
    }
    
    private class WifiStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                
                if (networkInfo != null && networkInfo.isConnected()) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    mainHandler.post(() -> {
                        for (NetworkStateListener listener : listeners) {
                            listener.onWifiConnected(wifiInfo);
                        }
                    });
                } else if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.DISCONNECTED) {
                    mainHandler.post(() -> {
                        for (NetworkStateListener listener : listeners) {
                            listener.onWifiDisconnected();
                        }
                    });
                }
                
            } else if (WifiManager.RSSI_CHANGED_ACTION.equals(action)) {
                int signalStrength = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, 0);
                mainHandler.post(() -> {
                    for (NetworkStateListener listener : listeners) {
                        listener.onSignalStrengthChanged(signalStrength);
                    }
                });
            }
        }
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        stopListening();
        listeners.clear();
        Log.d(TAG, "NetworkStateManager cleaned up");
    }
}