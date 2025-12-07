package com.omarflex5.temp.omerflex.service.casting;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.omerflex.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Example Activity demonstrating network discovery and communication
 * This activity shows how to integrate all network components for Android TV
 */
public class NetworkDiscoveryActivity extends AppCompatActivity {
    private static final String TAG = "NetworkDiscoveryActivity";

    // UI Components
    private TextView statusTextView;
    private TextView networkInfoTextView;
    private ListView devicesListView;
    private Button startDiscoveryButton;
    private Button stopDiscoveryButton;
    private Button sendMessageButton;

    // Network components
    private NetworkServiceDiscovery networkDiscovery;
    private CommunicationManager communicationManager;
    private NetworkStateManager networkStateManager;
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel wifiChannel;

    // Device Status
    private enum DeviceStatus {
        NOT_CONNECTED, CONNECTED, UNREACHABLE
    }

    // Adapters and data
    private DeviceAdapter devicesAdapter;
    private List<Device> discoveredDevices;
    private Map<String, DeviceStatus> deviceStatusMap;
    private String deviceId;
    private Device targetDeviceForMessage;
    private Set<String> connectionAttemptedAddresses;
    private boolean shouldSendMessageAfterConnection = false;

    // Permission launcher
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.omerflex.R.layout.activity_network_discovery);

        // Initialize device ID
        deviceId = "TVApp_" + Build.MODEL + "_" + System.currentTimeMillis();

        // Initialize collections
        connectionAttemptedAddresses = new HashSet<>();
        deviceStatusMap = new HashMap<>();

        // Initialize UI
        initializeUI();

        // Initialize permission launcher
        initializePermissionLauncher();

        // Check and request permissions
        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            initializeNetworkComponents();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cleanupNetworkComponents();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupNetworkComponents();
    }

    private void initializeUI() {
        statusTextView = findViewById(R.id.statusTextView);
        networkInfoTextView = findViewById(R.id.networkInfoTextView);
        devicesListView = findViewById(R.id.devicesListView);
        startDiscoveryButton = findViewById(R.id.startDiscoveryButton);
        stopDiscoveryButton = findViewById(R.id.stopDiscoveryButton);
        sendMessageButton = findViewById(R.id.sendMessageButton);

        // Initialize device list and adapter
        discoveredDevices = new ArrayList<>();
        devicesAdapter = new DeviceAdapter(this, discoveredDevices, deviceStatusMap);
        devicesListView.setAdapter(devicesAdapter);

        // Style buttons to ensure visibility
        startDiscoveryButton.setBackgroundColor(Color.DKGRAY);
        startDiscoveryButton.setTextColor(Color.WHITE);
        stopDiscoveryButton.setBackgroundColor(Color.DKGRAY);
        stopDiscoveryButton.setTextColor(Color.WHITE);
        sendMessageButton.setBackgroundColor(Color.DKGRAY);
        sendMessageButton.setTextColor(Color.WHITE);

        // Set button click listeners
        startDiscoveryButton.setText("Scan");
        startDiscoveryButton.setOnClickListener(v -> {
            Log.d(TAG, "Scan button clicked.");
            // Clear previous results
            discoveredDevices.clear();
            deviceStatusMap.clear();
            devicesAdapter.notifyDataSetChanged();
            connectionAttemptedAddresses.clear();
            targetDeviceForMessage = null;
            updateUI(); // Ensure button state is updated after clearing list

            // Restart discovery
            startDiscovery();
        });
        stopDiscoveryButton.setOnClickListener(v -> stopDiscovery());
        sendMessageButton.setOnClickListener(v -> {
            Log.d(TAG, "Send Test Message button CLICKED.");
            sendTestMessage();
        });

        // Set list item click listener
        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < discoveredDevices.size()) {
                Device selectedDevice = discoveredDevices.get(position);
                Log.d(TAG, "Device selected: " + selectedDevice.getDeviceName());
                targetDeviceForMessage = selectedDevice;
                Toast.makeText(NetworkDiscoveryActivity.this, "Selected device: " + selectedDevice.getDeviceName(), Toast.LENGTH_SHORT).show();

                if (connectionAttemptedAddresses.add(selectedDevice.getDeviceAddress())) {
                    Log.d(TAG, "First click on device, attempting connection to " + selectedDevice.getDeviceName());
                    connectToDevice(selectedDevice);
                } else {
                    Log.d(TAG, "Already attempted connection to " + selectedDevice.getDeviceName());
                }
            }
        });

        updateUI();
    }

    private void initializePermissionLauncher() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        initializeNetworkComponents();
                    } else {
                        Toast.makeText(this, "All permissions are required for network discovery",
                                Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
        );
    }

    private void checkPermissions() {
        if (!allPermissionsGranted()) {
            String[] permissions = getRequiredPermissions();
            permissionLauncher.launch(permissions);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();

        // Basic network permissions
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
        permissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
        permissions.add(Manifest.permission.CHANGE_NETWORK_STATE);
        permissions.add(Manifest.permission.INTERNET);

        // Location permission for WiFi Direct (required for Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // Nearby WiFi devices permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        return permissions.toArray(new String[0]);
    }

    private void initializeNetworkComponents() {
        try {
            // Initialize WiFi P2P Manager
            wifiP2pManager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
            if (wifiP2pManager != null) {
                wifiChannel = wifiP2pManager.initialize(this, getMainLooper(), null);
            }

            // Initialize network state manager
            networkStateManager = new NetworkStateManager(this);
            networkStateManager.addNetworkStateListener(new NetworkStateManager.NetworkStateListener() {
                @Override
                public void onNetworkConnected(android.net.NetworkInfo networkInfo) {
                    runOnUiThread(() -> {
                        statusTextView.setText("Network Connected");
                        updateNetworkInfo();
                    });
                }

                @Override
                public void onNetworkDisconnected() {
                    runOnUiThread(() -> {
                        statusTextView.setText("Network Disconnected");
                        updateNetworkInfo();
                    });
                }

                @Override
                public void onWifiConnected(android.net.wifi.WifiInfo wifiInfo) {
                    runOnUiThread(() -> {
                        statusTextView.setText("WiFi Connected");
                        updateNetworkInfo();
                    });
                }

                @Override
                public void onWifiDisconnected() {
                    runOnUiThread(() -> {
                        statusTextView.setText("WiFi Disconnected");
                        updateNetworkInfo();
                    });
                }

                @Override
                public void onNetworkTypeChanged(int networkType) {
                    updateNetworkInfo();
                }

                @Override
                public void onSignalStrengthChanged(int signalStrength) {
                    updateNetworkInfo();
                }
            });
            networkStateManager.startListening();

            // Initialize network discovery
            networkDiscovery = new NetworkServiceDiscovery(this, wifiP2pManager, wifiChannel);
            networkDiscovery.addDiscoveryListener(new NetworkServiceDiscovery.DiscoveryListener() {
                                @Override
                                public void onDeviceFound(Device device) {
                                    Log.d(TAG, "onDeviceFound: " + device.getDeviceName());
                                    runOnUiThread(() -> {
                                        if (!deviceStatusMap.containsKey(device.getDeviceAddress())) {
                                            discoveredDevices.add(device);
                                            deviceStatusMap.put(device.getDeviceAddress(), DeviceStatus.NOT_CONNECTED);
                                            devicesAdapter.notifyDataSetChanged();
                                            updateUI(); // Update button state
                
                                            String message = "Found device: " + device.getDeviceName() +
                                                    " at " + device.getDeviceAddress();
                                            Toast.makeText(NetworkDiscoveryActivity.this, message, Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                
                                @Override
                                public void onDeviceLost(Device device) {
                                    Log.d(TAG, "onDeviceLost: " + device.getDeviceName());
                                    runOnUiThread(() -> {
                                        deviceStatusMap.remove(device.getDeviceAddress());
                                        // Safe removal from list
                                        for (Iterator<Device> iterator = discoveredDevices.iterator(); iterator.hasNext(); ) {
                                            Device d = iterator.next();
                                            if (d.getDeviceAddress().equals(device.getDeviceAddress())) {
                                                iterator.remove();
                                                break;
                                            }
                                        }
                                        devicesAdapter.notifyDataSetChanged();
                                        updateUI(); // Update button state
                
                                        Toast.makeText(NetworkDiscoveryActivity.this,
                                                "Device lost: " + device.getDeviceName(), Toast.LENGTH_SHORT).show();
                                    });
                                }

                @Override
                public void onDiscoveryStarted() {
                    Log.d(TAG, "onDiscoveryStarted");
                    runOnUiThread(() -> {
                        statusTextView.setText("Discovery Started");
                        startDiscoveryButton.setEnabled(false);
                        stopDiscoveryButton.setEnabled(true);
                    });
                }

                @Override
                public void onDiscoveryStopped() {
                    Log.d(TAG, "onDiscoveryStopped");
                    runOnUiThread(() -> {
                        statusTextView.setText("Discovery Stopped");
                        startDiscoveryButton.setEnabled(true);
                        stopDiscoveryButton.setEnabled(false);
                    });
                }

                @Override
                public void onServiceRegistered(String serviceName) {
                    Log.d(TAG, "onServiceRegistered: " + serviceName);
                    runOnUiThread(() -> {
                        Toast.makeText(NetworkDiscoveryActivity.this,
                                "Service registered: " + serviceName, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onRegistrationFailed(int errorCode) {
                    Log.d(TAG, "onRegistrationFailed: " + errorCode);
                    runOnUiThread(() -> {
                        Toast.makeText(NetworkDiscoveryActivity.this,
                                "Service registration failed: " + errorCode, Toast.LENGTH_SHORT).show();
                    });
                }
            });

            // Initialize communication manager
            communicationManager = new CommunicationManager(deviceId);
            communicationManager.addMessageListener(new CommunicationManager.MessageListener() {
                @Override
                public void onMessageReceived(Message message, Device sender) {
                    Log.d(TAG, "onMessageReceived from: " + sender.getDeviceName() + ", content: " + message.getContent());
                    runOnUiThread(() -> {
                        String messageText = "Message from " + sender.getDeviceName() +
                                ": " + message.getContent();
                        Toast.makeText(NetworkDiscoveryActivity.this, messageText, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onMessageSent(Message message, Device recipient) {
                    Log.d(TAG, "onMessageSent to: " + recipient.getDeviceName());
                    runOnUiThread(() -> {
                        Toast.makeText(NetworkDiscoveryActivity.this,
                                "Message sent to " + recipient.getDeviceName() , Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onMessageFailed(Message message, Device recipient, String error) {
                    Log.e(TAG, "onMessageFailed to: " + recipient.getDeviceName() + ", error: " + error);
                    runOnUiThread(() -> {
                        if (recipient != null) {
                            deviceStatusMap.put(recipient.getDeviceAddress(), DeviceStatus.UNREACHABLE);
                            devicesAdapter.notifyDataSetChanged();
                        }
                        Toast.makeText(NetworkDiscoveryActivity.this,
                                "Message failed to " + recipient.getDeviceName() + ": " + error,
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onDeviceConnected(Device device) {
                    Log.d(TAG, "onDeviceConnected: " + device.getDeviceName());
                    runOnUiThread(() -> {
                        deviceStatusMap.put(device.getDeviceAddress(), DeviceStatus.CONNECTED);
                        devicesAdapter.notifyDataSetChanged();
                        Toast.makeText(NetworkDiscoveryActivity.this,
                                "Connected to " + device.getDeviceName(), Toast.LENGTH_SHORT).show();

                        if (shouldSendMessageAfterConnection && device.equals(targetDeviceForMessage)) {
                            Log.d(TAG, "Sending pending message after reconnection.");
                            shouldSendMessageAfterConnection = false; // Reset flag

                            Message message = new Message(deviceId, targetDeviceForMessage.getDeviceAddress(),
                                    Message.MessageType.DATA, "Hello from Android TV! "+ ", url: "+"https://www.w3schools.com/html/mov_bbb.mp4");
                            communicationManager.sendMessage(message, targetDeviceForMessage);
                        }
                    });
                }

                @Override
                public void onDeviceDisconnected(Device device) {
                    Log.d(TAG, "onDeviceDisconnected: " + device.getDeviceName());
                    runOnUiThread(() -> {
                        deviceStatusMap.put(device.getDeviceAddress(), DeviceStatus.UNREACHABLE);
                        devicesAdapter.notifyDataSetChanged();
                        Toast.makeText(NetworkDiscoveryActivity.this,
                                "Disconnected from " + device.getDeviceName(), Toast.LENGTH_SHORT).show();
                    });
                }
            });

            // Start server for incoming connections
            communicationManager.startServer();

            // Register service for discovery
            networkDiscovery.registerService(CommunicationManager.SERVER_PORT);

            updateUI();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing network components", e);
            Toast.makeText(this, "Error initializing network: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void cleanupNetworkComponents() {
        if (networkStateManager != null) {
            networkStateManager.stopListening();
            networkStateManager.cleanup();
        }

        if (networkDiscovery != null) {
            networkDiscovery.stopDiscovery();
            networkDiscovery.unregisterService();
            networkDiscovery.cleanup();
        }

        if (communicationManager != null) {
            communicationManager.stopServer();
            communicationManager.cleanup();
        }
    }

    private void startDiscovery() {
        Log.d(TAG, "startDiscovery called.");
        if (networkDiscovery != null) {
            Log.d(TAG, "Calling networkDiscovery.startDiscovery()");
            networkDiscovery.startDiscovery();
        } else {
            Log.w(TAG, "networkDiscovery is null, cannot start discovery.");
        }
    }

    private void stopDiscovery() {
        Log.d(TAG, "stopDiscovery called.");
        if (networkDiscovery != null) {
            networkDiscovery.stopDiscovery();
        }
    }

    private void connectToDevice(Device device) {
        Log.d(TAG, "connectToDevice called for: " + device.getDeviceName());
        if (communicationManager != null) {
            Log.d(TAG, "Calling communicationManager.connectToDevice for: " + device.getDeviceName());
            communicationManager.connectToDevice(device);
        } else {
            Log.w(TAG, "communicationManager is null, cannot connect.");
        }
    }

    private void sendTestMessage() {
        Log.d(TAG, "sendTestMessage called.");
        if (targetDeviceForMessage == null) {
            Toast.makeText(this, "No device selected to send message", Toast.LENGTH_SHORT).show();
            return;
        }

        DeviceStatus status = deviceStatusMap.get(targetDeviceForMessage.getDeviceAddress());

        if (communicationManager != null && status == DeviceStatus.CONNECTED) {
            Log.d(TAG, "Sending message to connected device: " + targetDeviceForMessage.getDeviceName());
            Message message = new Message(deviceId, targetDeviceForMessage.getDeviceAddress(),
                    Message.MessageType.DATA, "https://www.w3schools.com/html/mov_bbb.mp4");
            communicationManager.sendMessage(message, targetDeviceForMessage);
        } else if (communicationManager != null) {
            Log.w(TAG, "Target device not connected. Reconnecting to send message. Status: " + status);
            Toast.makeText(this, "Reconnecting to send message...", Toast.LENGTH_SHORT).show();
            shouldSendMessageAfterConnection = true;
            connectToDevice(targetDeviceForMessage);
        }
    }

    private void updateUI() {
        if (networkStateManager != null) {
            updateNetworkInfo();
        }

        startDiscoveryButton.setEnabled(networkDiscovery != null && !networkDiscovery.isDiscovering());
        stopDiscoveryButton.setEnabled(networkDiscovery != null && networkDiscovery.isDiscovering());
        sendMessageButton.setEnabled(!discoveredDevices.isEmpty());
    }

    private void updateNetworkInfo() {
        if (networkStateManager == null) return;

        StringBuilder info = new StringBuilder();
        info.append("Network Status:\n");
        info.append("Connected: ").append(networkStateManager.isNetworkConnected()).append("\n");
        info.append("WiFi Connected: ").append(networkStateManager.isWifiConnected()).append("\n");

        if (networkStateManager.isWifiConnected()) {
            android.net.wifi.WifiInfo wifiInfo = networkStateManager.getWifiConnectionInfo();
            if (wifiInfo != null) {
                info.append("WiFi SSID: ").append(wifiInfo.getSSID()).append("\n");
                info.append("Signal Strength: ").append(networkStateManager.getWifiSignalLevel()).append("/5\n");
            }
        }

        String ipAddress = networkStateManager.getLocalIpAddress();
        if (ipAddress != null) {
            info.append("IP Address: ").append(ipAddress).append("\n");
        }

        info.append("Device ID: ").append(deviceId).append("\n");
        info.append("Discovered Devices: ").append(discoveredDevices.size());

        networkInfoTextView.setText(info.toString());
    }

    /**
     * Custom ArrayAdapter for displaying devices with a status indicator.
     */
    private class DeviceAdapter extends ArrayAdapter<Device> {
        private Map<String, DeviceStatus> statusMap;

        public DeviceAdapter(Context context, List<Device> devices, Map<String, DeviceStatus> statusMap) {
            super(context, 0, devices);
            this.statusMap = statusMap;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item_device, parent, false);
            }

            Device device = getItem(position);
            if (device != null) {
                TextView deviceNameTextView = convertView.findViewById(R.id.device_name);
                View statusIndicator = convertView.findViewById(R.id.status_indicator);

                deviceNameTextView.setText(device.getDeviceName() + " (" + device.getDeviceType() + ")");

                DeviceStatus status = statusMap.get(device.getDeviceAddress());
                if (status == null) {
                    status = DeviceStatus.NOT_CONNECTED;
                }

                // The indicator background is a shape drawable. We can mutate it.
                GradientDrawable background = (GradientDrawable) statusIndicator.getBackground().mutate();

                int color = Color.GRAY;
                switch (status) {
                    case CONNECTED:
                        color = Color.GREEN;
                        break;
                    case UNREACHABLE:
                        color = Color.RED;
                        break;
                    case NOT_CONNECTED:
                        color = Color.GRAY;
                        break;
                }
                background.setColor(color);
            }

            return convertView;
        }
    }
}