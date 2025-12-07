package com.omarflex5.cast.dlna;

import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers DLNA/UPnP devices using SSDP (Simple Service Discovery Protocol).
 */
public class SsdpDiscoverer {
    private static final String TAG = "SsdpDiscoverer";
    private static final String SSDP_IP = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int TIMEOUT_MS = 1000;

    /**
     * Represents a discovered DLNA device.
     */
    public static class DlnaDevice {
        public String location;
        public String server;
        public String usn;
        public String friendlyName = "Unknown Device";

        @Override
        public String toString() {
            return friendlyName;
        }
    }

    public interface DiscoveryListener {
        void onDeviceFound(DlnaDevice device);

        void onDiscoveryComplete(List<DlnaDevice> devices);

        void onError(String error);
    }

    public static void discoverDevices(android.content.Context context, DiscoveryListener listener) {
        new Thread(() -> {
            List<DlnaDevice> devices = new ArrayList<>();
            Set<String> foundLocations = new HashSet<>();
            DatagramSocket socket = null;
            android.net.wifi.WifiManager.MulticastLock multicastLock = null;

            try {
                // 1. Acquire Multicast Lock (CRITICAL for receiving UDP packets)
                android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) context
                        .getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    multicastLock = wifiManager.createMulticastLock("SsdpDiscoveryLock");
                    multicastLock.setReferenceCounted(true);
                    multicastLock.acquire();
                }

                // 2. Find correct local IP (using same logic as MediaServer)
                java.net.InetAddress localAddress = null;
                String localIp = com.omarflex5.util.NetworkUtils.getLocalIpAddress();
                if (localIp != null) {
                    localAddress = java.net.InetAddress.getByName(localIp);
                }

                if (localAddress == null) {
                    listener.onError("No valid Wi-Fi connection found");
                    return;
                }

                // 3. Use MulticastSocket for robust discovery
                // Bind to the specific local IP to ensure we use certain interface
                socket = new java.net.MulticastSocket(new java.net.InetSocketAddress(localAddress, 0));
                // Optional: Join the group to ensure routing is correct (though we send TO it)
                // ((java.net.MulticastSocket)socket).setNetworkInterface(java.net.NetworkInterface.getByInetAddress(localAddress));

                socket.setSoTimeout(TIMEOUT_MS);

                Log.d(TAG, "Starting SSDP Search from " + localAddress + " port " + socket.getLocalPort());

                // SSDP M-SEARCH Packet
                // Search for ssdp:all to debug visibility
                String searchMessage = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: " + SSDP_IP + ":" + SSDP_PORT + "\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: ssdp:all\r\n" +
                        "\r\n";

                byte[] sendData = searchMessage.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                        InetAddress.getByName(SSDP_IP), SSDP_PORT);

                // Send 3 times
                for (int i = 0; i < 3; i++) {
                    Log.d(TAG, "Sending M-SEARCH packet " + (i + 1));
                    socket.send(sendPacket);
                    Thread.sleep(200);
                }

                long endTime = System.currentTimeMillis() + 5000;
                byte[] receiveData = new byte[4096]; // Larger buffer

                while (System.currentTimeMillis() < endTime) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);
                        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                        Log.d(TAG, "Received SSDP Response from " + receivePacket.getAddress() + ":\n" + response);

                        String location = parseHeaderValue(response, "LOCATION");
                        if (location != null && !foundLocations.contains(location)) {
                            foundLocations.add(location);

                            // Basic filter: must be some kind of media device or service
                            // Since we scan "ssdp:all", we might get routers/gateways.
                            // Let's indiscriminately show all but prefer "AVTransport" ones.

                            DlnaDevice device = new DlnaDevice();
                            device.location = location;
                            device.server = parseHeaderValue(response, "SERVER");
                            device.usn = parseHeaderValue(response, "USN");

                            // 4. Determine Name immediately (Don't block for XML fetch!)
                            device.friendlyName = "Unknown Device";
                            if (device.server != null && !device.server.isEmpty()) {
                                // Clean up server string (e.g., "Linux/..., UPnP/1.0, Chromecast/..." ->
                                // "Chromecast")
                                device.friendlyName = simplifyServerName(device.server);
                            } else {
                                device.friendlyName = "Device (" + receivePacket.getAddress().getHostAddress() + ")";
                            }

                            // Optional: If we really want the fancy name, we should fire a background task
                            // here
                            // But for now, speed is priority.

                            Log.d(TAG, "Found Device: " + device.friendlyName + " at " + location);
                            devices.add(device);
                            listener.onDeviceFound(device); // Notify immediately
                        }
                    } catch (SocketTimeoutException e) {
                        // socket timed out, continue
                    }
                }

                listener.onDiscoveryComplete(devices);

            } catch (Exception e) {
                Log.e(TAG, "Discovery failed", e);
                listener.onError(e.getMessage());
            } finally {
                if (socket != null)
                    socket.close();
                if (multicastLock != null && multicastLock.isHeld()) {
                    multicastLock.release();
                }
            }
        }).start();
    }

    // Helper to make "Linux/.., UPnP/.., Model/.." look human readable
    private static String simplifyServerName(String server) {
        if (server.contains("Chromecast"))
            return "Chromecast";
        if (server.contains("EShare"))
            return "Smart TV (EShare)";
        if (server.contains("Samsung"))
            return "Samsung TV";
        if (server.contains("LG"))
            return "LG TV";
        if (server.contains("FRITZ!Box"))
            return "FRITZ!Box Media";
        return server; // Fallback to full string
    }

    private static String parseHeaderValue(String content, String headerName) {
        Pattern pattern = Pattern.compile("^" + headerName + ":\\s*(.*)$",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    // Tiny helper to fetch the description XML and grab <friendlyName>
    private static String fetchFriendlyName(String locationUrl) {
        try {
            java.net.URL url = new java.net.URL(locationUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);

            java.util.Scanner s = new java.util.Scanner(conn.getInputStream()).useDelimiter("\\A");
            String xml = s.hasNext() ? s.next() : "";

            int start = xml.indexOf("<friendlyName>");
            int end = xml.indexOf("</friendlyName>");
            if (start != -1 && end != -1) {
                return xml.substring(start + 14, end);
            }
        } catch (Exception e) {
            /* ignore */ }
        return null;
    }
}
