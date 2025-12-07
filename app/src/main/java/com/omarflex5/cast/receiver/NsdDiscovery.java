package com.omarflex5.cast.receiver;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers OmarFlex TV instances on the local network using NSD.
 */
public class NsdDiscovery {

    private static final String TAG = "NsdDiscovery";
    private static final String SERVICE_TYPE = "_omarflex._tcp.";

    public static class OmarFlexDevice {
        public String name;
        public String host;
        public int port;

        public OmarFlexDevice(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }
    }

    public interface DiscoveryListener {
        void onDeviceFound(OmarFlexDevice device);

        void onDiscoveryStopped();

        void onError(String error);
    }

    private final NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private final DiscoveryListener clientListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDiscovering = false;

    public NsdDiscovery(Context context, DiscoveryListener listener) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.clientListener = listener;
    }

    public void startDiscovery() {
        if (isDiscovering)
            return;

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "NSD Discovery Started");
                isDiscovering = true;
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                Log.d(TAG, "Service found: " + serviceInfo);
                if (serviceInfo.getServiceType().contains("_omarflex")) {
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.e(TAG, "Resolve failed: " + errorCode);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            Log.d(TAG, "Service resolved: " + serviceInfo);

                            String finalHost = null;
                            int finalPort = serviceInfo.getPort();
                            String name = serviceInfo.getServiceName();

                            // 1. Try to get preferred_ip from TXT record
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                java.util.Map<String, byte[]> attributes = serviceInfo.getAttributes();
                                if (attributes != null && attributes.containsKey("preferred_ip")) {
                                    byte[] ipBytes = attributes.get("preferred_ip");
                                    if (ipBytes != null) {
                                        finalHost = new String(ipBytes, java.nio.charset.StandardCharsets.UTF_8);
                                        Log.d(TAG, "Using preferred_ip from TXT record: " + finalHost);
                                    }
                                }
                            }

                            // 2. Fallback to OS resolved host
                            if (finalHost == null) {
                                InetAddress host = serviceInfo.getHost();
                                if (host != null) {
                                    finalHost = host.getHostAddress();
                                }
                            }

                            // 3. Validation
                            if (finalHost != null) {
                                OmarFlexDevice device = new OmarFlexDevice(name, finalHost, finalPort);
                                mainHandler.post(() -> clientListener.onDeviceFound(device));
                            }
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                Log.e(TAG, "service lost: " + serviceInfo);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
                isDiscovering = false;
                mainHandler.post(clientListener::onDiscoveryStopped);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
                mainHandler.post(() -> clientListener.onError("Start Failed: " + errorCode));
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    public void stopDiscovery() {
        if (isDiscovering && nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                // Ignore "already stopped" errors
            }
        }
    }
}
