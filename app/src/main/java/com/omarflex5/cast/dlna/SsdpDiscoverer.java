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

    public static void discoverDevices(DiscoveryListener listener) {
        new Thread(() -> {
            List<DlnaDevice> devices = new ArrayList<>();
            Set<String> foundLocations = new HashSet<>();
            DatagramSocket socket = null;

            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(TIMEOUT_MS);

                // SSDP M-SEARCH Packet
                String searchMessage = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: " + SSDP_IP + ":" + SSDP_PORT + "\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: urn:schemas-upnp-org:service:AVTransport:1\r\n" + // Look for Media Renderers
                        "\r\n";

                byte[] sendData = searchMessage.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
                        InetAddress.getByName(SSDP_IP), SSDP_PORT);

                // Send multiple times to ensure delivery over UDP
                for (int i = 0; i < 3; i++) {
                    socket.send(sendPacket);
                    Thread.sleep(100);
                }

                long endTime = System.currentTimeMillis() + 4000; // Listen for 4 seconds
                byte[] receiveData = new byte[2048];

                while (System.currentTimeMillis() < endTime) {
                    try {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        socket.receive(receivePacket);
                        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

                        String location = parseHeaderValue(response, "LOCATION");
                        if (location != null && !foundLocations.contains(location)) {
                            foundLocations.add(location);
                            DlnaDevice device = new DlnaDevice();
                            device.location = location;
                            device.server = parseHeaderValue(response, "SERVER");
                            device.usn = parseHeaderValue(response, "USN");

                            // Fetch friendly name from XML description (could be slow, maybe do async?)
                            // For this MVP, let's keep it simple: if server name exists, use it, else
                            // generic
                            device.friendlyName = device.server != null ? device.server : "DLNA Device";

                            // Better: Fetch the XML to get the real FriendlyName
                            String name = fetchFriendlyName(location);
                            if (name != null)
                                device.friendlyName = name;

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
            }
        }).start();
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
