package com.omarflex5.temp.omerflex.providers;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class SsdpDiscoverer {
    private static final String TAG = "SsdpDiscoverer";
    private static final String SSDP_HOST = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int TIMEOUT = 5000;

    public interface DiscoveryListener {
        void onDeviceFound(DlnaDevice device);
        void onDiscoveryComplete(List<String> devices);
        void onError(String error);
    }

    public static class DlnaDevice {
        public String friendlyName;
        public String ipAddress;
        public String location;
        public String server;
        public String usn;

        public DlnaDevice(String friendlyName, String ipAddress, String location, String server, String usn) {
            this.friendlyName = friendlyName;
            this.ipAddress = ipAddress;
            this.location = location;
            this.server = server;
            this.usn = usn;
        }

        private String getServiceType() {
            if (usn == null) return "";
            String[] parts = usn.split("::");
            if (parts.length < 2) return "";
            String servicePart = parts[1];
            String[] serviceParts = servicePart.split(":");
            if (serviceParts.length >= 4) {
                return serviceParts[3]; // e.g., AVTransport
            }
            return "";
        }

        @Override
        public String toString() {
            String serviceType = getServiceType();
            String name = (friendlyName != null) ? friendlyName : "Unknown Device";
            
            if (serviceType != null && !serviceType.isEmpty()) {
                return name + " (" + serviceType + ")";
            }
            return name;
        }
    }

    private static List<DlnaDevice> deviceCache = new ArrayList<>();
    private static long lastCacheTime = 0;
    private static final long CACHE_DURATION_MS = 60 * 1000; // 60 seconds

    public static void discoverDevicesWithDetails(DiscoveryListener listener) {
        // Check cache first
        long now = System.currentTimeMillis();
        if (lastCacheTime > 0 && (now - lastCacheTime < CACHE_DURATION_MS) && !deviceCache.isEmpty()) {
            Log.d(TAG, "Returning " + deviceCache.size() + " cached DLNA devices.");
            // Use a new thread to avoid blocking the caller, mimicking the async behavior
            new Thread(() -> {
                for (DlnaDevice device : deviceCache) {
                    listener.onDeviceFound(device);
                }
                listener.onDiscoveryComplete(deviceCache.stream().map(DlnaDevice::toString).collect(Collectors.toList()));
            }).start();
            return; // Stop here, don't perform a new scan
        }

        // If cache is stale, perform network discovery
        new Thread(() -> {
            try {
                List<DlnaDevice> devices = new ArrayList<>();

                String searchMessage =
                        "M-SEARCH * HTTP/1.1\r\n" +
                                "HOST: " + SSDP_HOST + ":" + SSDP_PORT + "\r\n" +
                                "MAN: \"ssdp:discover\"\r\n" +
                                "MX: 3\r\n" +
                                "ST: ssdp:all\r\n" +
                                "\r\n";

                byte[] sendData = searchMessage.getBytes();

                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(TIMEOUT);

                    InetAddress group = InetAddress.getByName(SSDP_HOST);
                    DatagramPacket sendPacket = new DatagramPacket(
                            sendData, sendData.length, group, SSDP_PORT
                    );

                    socket.send(sendPacket);

                    byte[] receiveData = new byte[8192];
                    long endTime = System.currentTimeMillis() + TIMEOUT;

                    while (System.currentTimeMillis() < endTime) {
                        try {
                            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                            socket.receive(receivePacket);

                            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                            String deviceIp = receivePacket.getAddress().getHostAddress();

                            DlnaDevice device = parseDeviceDetails(response, deviceIp);
                            if (device != null && !containsDevice(devices, device.usn)) {
                                devices.add(device);
                                listener.onDeviceFound(device);
                            }

                        } catch (java.net.SocketTimeoutException e) {
                            break;
                        }
                    }
                }

                // Update the cache with the new findings
                Log.d(TAG, "Discovery finished. Found " + devices.size() + " devices. Updating cache.");
                deviceCache.clear();
                deviceCache.addAll(devices);
                lastCacheTime = System.currentTimeMillis();

                listener.onDiscoveryComplete(devices.stream().map(DlnaDevice::toString).collect(Collectors.toList()));

            } catch (Exception e) {
                Log.e(TAG, "SSDP discovery error", e);
                listener.onError("Discovery failed: " + e.getMessage());
            }
        }).start();
    }

    private static DlnaDevice parseDeviceDetails(String response, String ipAddress) {
        try {
            String[] lines = response.split("\r\n");
            String server = null;
            String location = null;
            String usn = null;

            for (String line : lines) {
                if (line.toUpperCase().startsWith("SERVER:")) {
                    server = line.substring(7).trim();
                } else if (line.toUpperCase().startsWith("LOCATION:")) {
                    location = line.substring(9).trim();
                } else if (line.toUpperCase().startsWith("USN:")) {
                    usn = line.substring(4).trim();
                }
            }

            // Filter devices based on USN, looking for MediaRenderer, AVTransport, or our own app
            if (usn != null && (usn.contains(":device:MediaRenderer:") || usn.contains(":service:AVTransport:") || usn.contains(":service:OmerFlex:"))) {
                if (location != null) {
                    String friendlyName = fetchFriendlyName(location);
                    if (friendlyName == null && server != null) {
                        friendlyName = extractFriendlyName(server);
                    } else if (friendlyName == null) {
                        friendlyName = "Unknown Device";
                    }
                    return new DlnaDevice(friendlyName, ipAddress, location, server, usn);
                }
            }

            return null; // Return null if not a media renderer or no location

        } catch (Exception e) {
            Log.e(TAG, "Error parsing device details", e);
            return null;
        }
    }

    private static String fetchFriendlyName(String locationUrl) {
        try {
            URL url = new URL(locationUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(2000);
            urlConnection.setReadTimeout(2000);
            try (InputStream in = urlConnection.getInputStream()) {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser xpp = factory.newPullParser();
                xpp.setInput(in, null);

                int eventType = xpp.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if ("friendlyName".equalsIgnoreCase(xpp.getName())) {
                            if (xpp.next() == XmlPullParser.TEXT) {
                                return xpp.getText().trim();
                            }
                        }
                    }
                    eventType = xpp.next();
                }
            } finally {
                urlConnection.disconnect();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch or parse device description from " + locationUrl, e);
        }
        return null;
    }

    private static boolean containsDevice(List<DlnaDevice> devices, String usn) {
        if (usn == null) {
            return false;
        }
        for (DlnaDevice device : devices) {
            if (device.usn != null && device.usn.equals(usn)) {
                return true;
            }
        }
        return false;
    }

        public static void discoverDevices(DiscoveryListener listener) {
            new Thread(() -> {
                try {
                    List<DlnaDevice> devices = new ArrayList<>();
    
                    // SSDP M-SEARCH message
                    String searchMessage =
                            "M-SEARCH * HTTP/1.1\r\n" +
                                    "HOST: " + SSDP_HOST + ":" + SSDP_PORT + "\r\n" +
                                    "MAN: \"ssdp:discover\"\r\n" +
                                    "MX: 3\r\n" +
                                    "ST: ssdp:all\r\n" +
                                    "\r\n";
    
                    byte[] sendData = searchMessage.getBytes();
    
                    // Get local IP address
                    String localIp = getLocalIpAddress();
                    if (localIp == null) {
                        listener.onError("Cannot get local IP address");
                        return;
                    }
    
                    Log.d(TAG, "Starting SSDP discovery from: " + localIp);
    
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.setSoTimeout(TIMEOUT);
    
                        InetAddress group = InetAddress.getByName(SSDP_HOST);
                        DatagramPacket sendPacket = new DatagramPacket(
                                sendData, sendData.length, group, SSDP_PORT
                        );
    
                        // Send discovery packet
                        socket.send(sendPacket);
                        Log.d(TAG, "SSDP discovery packet sent");
    
                        // Listen for responses
                        byte[] receiveData = new byte[8192];
                        long endTime = System.currentTimeMillis() + TIMEOUT;
    
                        while (System.currentTimeMillis() < endTime) {
                            try {
                                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                                socket.receive(receivePacket);
    
                                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                                String deviceIp = receivePacket.getAddress().getHostAddress();
    
                                Log.d(TAG, "Received response from: " + deviceIp);
                                Log.d(TAG, "Response: " + response);
    
                                // Parse device info from response
                                DlnaDevice device = parseDeviceInfo(response, deviceIp);
                                if (device != null && !containsDevice(devices, device.usn)) {
                                    devices.add(device);
                                    listener.onDeviceFound(device);
                                }
    
                            } catch (java.net.SocketTimeoutException e) {
                                // Timeout is expected, continue
                                break;
                            }
                        }
                    }
    
                    Log.d(TAG, "SSDP discovery completed. Found: " + devices.size() + " devices");
                    listener.onDiscoveryComplete(devices.stream().map(DlnaDevice::toString).collect(Collectors.toList()));
    
                } catch (Exception e) {
                    Log.e(TAG, "SSDP discovery error", e);
                    listener.onError("Discovery failed: " + e.getMessage());
                }
            }).start();
        }
    private static DlnaDevice parseDeviceInfo(String response, String ipAddress) {
        try {
            String[] lines = response.split("\r\n");
            String server = null;
            String location = null;
            String usn = null;

            for (String line : lines) {
                if (line.toUpperCase().startsWith("SERVER:")) {
                    server = line.substring(7).trim();
                } else if (line.toUpperCase().startsWith("LOCATION:")) {
                    location = line.substring(9).trim();
                } else if (line.toUpperCase().startsWith("USN:")) {
                    usn = line.substring(4).trim();
                }
            }

            if (server != null) {
                // Extract friendly name if available
                String friendlyName = extractFriendlyName(server);
                return new DlnaDevice(friendlyName, ipAddress, location, server, usn);
            }

            return new DlnaDevice("Unknown Device", ipAddress, location, server, usn);

        } catch (Exception e) {
            Log.e(TAG, "Error parsing device info", e);
            return new DlnaDevice("Device", ipAddress, null, null, null);
        }
    }

    private static String extractFriendlyName(String server) {
        // Try to extract a friendly name from the server string
        if (server.contains("/")) {
            String[] parts = server.split("/");
            if (parts.length > 1) {
                return parts[0].trim();
            }
        }
        return server;
    }

    private static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "Error getting local IP", e);
        }
        return null;
    }
}
