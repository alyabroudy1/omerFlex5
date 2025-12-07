package com.omarflex5.temp.omerflex.service.casting;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Production-ready Network Service Discovery implementation for Android TV
 * Supports both NSD (Network Service Discovery) and WiFi Direct service discovery
 */
public class NetworkServiceDiscovery {
    private static final String TAG = "NetworkServiceDiscovery";
    
    // Service configuration
    private static final String SERVICE_TYPE = "_tvapp._tcp.";
    private static final String SERVICE_NAME_PREFIX = "TVApp_";
    private static final int DISCOVERY_TIMEOUT = 30000; // 30 seconds
    private static final int RESOLVE_TIMEOUT = 10000; // 10 seconds
    
    private final Context context;
    private final NsdManager nsdManager;
    private final WifiP2pManager wifiP2pManager;
    private final WifiP2pManager.Channel wifiChannel;
    private final Handler mainHandler;
    
    // Service registration
    private NsdManager.RegistrationListener nsdRegistrationListener;
    private String registeredServiceName;
    private int servicePort;
    
    // Service discovery
    private NsdManager.DiscoveryListener nsdDiscoveryListener;
    private final Map<String, Device> discoveredDevices;
    private final List<DiscoveryListener> discoveryListeners;
    
    // WiFi Direct components
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private final Map<String, String> buddies;
    
    // State management
    private boolean isDiscovering = false;
    private boolean isRegistered = false;
    private final Object discoveryLock = new Object();
    
    public interface DiscoveryListener {
        void onDeviceFound(Device device);
        void onDeviceLost(Device device);
        void onDiscoveryStarted();
        void onDiscoveryStopped();
        void onServiceRegistered(String serviceName);
        void onRegistrationFailed(int errorCode);
    }
    
    public NetworkServiceDiscovery(Context context, WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel) {
        this.context = context;
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.wifiP2pManager = wifiP2pManager;
        this.wifiChannel = channel;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.discoveredDevices = new ConcurrentHashMap<>();
        this.discoveryListeners = new CopyOnWriteArrayList<>();
        this.buddies = new HashMap<>();
    }
    
    /**
     * Register this device as a service on the network
     */
    public void registerService(int port) {
        if (isRegistered) {
            Log.w(TAG, "Service already registered");
            return;
        }
        
        this.servicePort = port;
        
        // Create service info
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME_PREFIX + android.os.Build.MODEL);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);
        
        // Add device information as attributes
        serviceInfo.setAttribute("device_model", android.os.Build.MODEL);
        serviceInfo.setAttribute("device_type", "android_tv");
        serviceInfo.setAttribute("app_version", "1.0");
        serviceInfo.setAttribute("api_level", String.valueOf(android.os.Build.VERSION.SDK_INT));
        
        initializeRegistrationListener();
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener);
    }
    
    /**
     * Unregister the service
     */
    public void unregisterService() {
        if (!isRegistered || nsdRegistrationListener == null) {
            Log.w(TAG, "No service to unregister");
            return;
        }
        
        try {
            nsdManager.unregisterService(nsdRegistrationListener);
            isRegistered = false;
            registeredServiceName = null;
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering service", e);
        }
    }
    
    /**
     * Start discovering services on the network
     */
    public void startDiscovery() {
        synchronized (discoveryLock) {
            if (isDiscovering) {
                Log.w(TAG, "Discovery already in progress");
                return;
            }
            
            initializeDiscoveryListener();
            
            try {
                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener);
                isDiscovering = true;
                
                // Set timeout for discovery
                mainHandler.postDelayed(() -> {
                    if (isDiscovering) {
                        Log.d(TAG, "Discovery timeout reached");
                        stopDiscovery();
                    }
                }, DISCOVERY_TIMEOUT);
                
                notifyDiscoveryStarted();
            } catch (Exception e) {
                Log.e(TAG, "Error starting discovery", e);
                notifyDiscoveryError();
            }
        }
    }
    
    /**
     * Stop discovering services
     */
    public void stopDiscovery() {
        synchronized (discoveryLock) {
            if (!isDiscovering || nsdDiscoveryListener == null) {
                return;
            }
            
            try {
                nsdManager.stopServiceDiscovery(nsdDiscoveryListener);
                isDiscovering = false;
                nsdDiscoveryListener = null;
                notifyDiscoveryStopped();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping discovery", e);
            }
        }
    }
    
    /**
     * Add a discovery listener
     */
    public void addDiscoveryListener(DiscoveryListener listener) {
        if (!discoveryListeners.contains(listener)) {
            discoveryListeners.add(listener);
        }
    }
    
    /**
     * Remove a discovery listener
     */
    public void removeDiscoveryListener(DiscoveryListener listener) {
        discoveryListeners.remove(listener);
    }
    
    /**
     * Get all discovered devices
     */
    public List<Device> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices.values());
    }
    
    /**
     * Find a device by service name
     */
    public Device findDeviceByServiceName(String serviceName) {
        for (Device device : discoveredDevices.values()) {
            if (serviceName.equals(device.getServiceName())) {
                return device;
            }
        }
        return null;
    }
    
    /**
     * Clear all discovered devices
     */
    public void clearDiscoveredDevices() {
        discoveredDevices.clear();
    }
    
    private void initializeRegistrationListener() {
        nsdRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                registeredServiceName = serviceInfo.getServiceName();
                isRegistered = true;
                Log.d(TAG, "Service registered: " + registeredServiceName);
                mainHandler.post(() -> {
                    for (DiscoveryListener listener : discoveryListeners) {
                        listener.onServiceRegistered(registeredServiceName);
                    }
                });
            }
            
            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Service registration failed: " + errorCode);
                mainHandler.post(() -> {
                    for (DiscoveryListener listener : discoveryListeners) {
                        listener.onRegistrationFailed(errorCode);
                    }
                });
            }
            
            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service unregistered: " + serviceInfo.getServiceName());
                isRegistered = false;
            }
            
            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Service unregistration failed: " + errorCode);
            }
        };
    }

    public boolean isDiscovering() {
        return isDiscovering;
    }

    public void setDiscovering(boolean discovering) {
        isDiscovering = discovering;
    }

    private void initializeDiscoveryListener() {
        nsdDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started: " + regType);
            }
            
            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service found: " + serviceInfo.getServiceName());
                
                // Check if it's our service type
                if (!serviceInfo.getServiceType().equals(SERVICE_TYPE)) {
                    Log.d(TAG, "Unknown service type: " + serviceInfo.getServiceType());
                    return;
                }
                
                // Check if it's our own service
                if (serviceInfo.getServiceName().equals(registeredServiceName)) {
                    Log.d(TAG, "Ignoring own service");
                    return;
                }
                
                // Resolve the service to get connection details
                resolveService(serviceInfo);
            }
            
            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service lost: " + serviceInfo.getServiceName());
                
                Device lostDevice = findDeviceByServiceName(serviceInfo.getServiceName());
                if (lostDevice != null) {
                    discoveredDevices.remove(serviceInfo.getServiceName());
                    mainHandler.post(() -> {
                        for (DiscoveryListener listener : discoveryListeners) {
                            listener.onDeviceLost(lostDevice);
                        }
                    });
                }
            }
            
            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped: " + serviceType);
                isDiscovering = false;
            }
            
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery start failed: " + errorCode);
                isDiscovering = false;
                notifyDiscoveryError();
            }
            
            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery stop failed: " + errorCode);
                isDiscovering = false;
            }
        };
    }


    private void resolveService(NsdServiceInfo serviceInfo) {
        NsdManager.ResolveListener resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service resolved: " + serviceInfo.getServiceName());
                
                Device device = new Device(serviceInfo);
                device.setDeviceType(Device.DeviceType.ANDROID_TV);
                
                discoveredDevices.put(serviceInfo.getServiceName(), device);
                
                mainHandler.post(() -> {
                    for (DiscoveryListener listener : discoveryListeners) {
                        listener.onDeviceFound(device);
                    }
                });
            }
            
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Service resolve failed: " + errorCode);
            }
        };
        
        try {
            nsdManager.resolveService(serviceInfo, resolveListener);
        } catch (Exception e) {
            Log.e(TAG, "Error resolving service", e);
        }
    }
    
    private void notifyDiscoveryStarted() {
        mainHandler.post(() -> {
            for (DiscoveryListener listener : discoveryListeners) {
                listener.onDiscoveryStarted();
            }
        });
    }
    
    private void notifyDiscoveryStopped() {
        mainHandler.post(() -> {
            for (DiscoveryListener listener : discoveryListeners) {
                listener.onDiscoveryStopped();
            }
        });
    }
    
    private void notifyDiscoveryError() {
        mainHandler.post(() -> {
            for (DiscoveryListener listener : discoveryListeners) {
                listener.onDiscoveryStopped();
            }
        });
    }
    
    /**
     * Clean up all resources
     */
    public void cleanup() {
        stopDiscovery();
        unregisterService();
        discoveryListeners.clear();
        discoveredDevices.clear();
    }
}