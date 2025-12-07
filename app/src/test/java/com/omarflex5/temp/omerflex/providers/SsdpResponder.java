package com.omarflex5.temp.omerflex.providers;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

public class SsdpResponder {
    private static final String TAG = "SsdpResponder";
    private static final String SSDP_HOST = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String SERVICE_TYPE = "urn:schemas-omerflex-com:service:OmerFlex:1";
    private static final String DEVICE_TYPE = "urn:schemas-omerflex-com:device:OmerFlexDevice:1";
    private static final int HTTP_PORT = 8080; // Make sure this port is available

    private Context context;
    private Thread ssdpListenerThread;
    private WebServer webServer;
    private volatile boolean isRunning = false;
    private String deviceUuid = "uuid:" + UUID.randomUUID().toString();

    public SsdpResponder(Context context) {
        this.context = context;
    }

    public void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;

        // Start the web server
        try {
            webServer = new WebServer();
            webServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.d(TAG, "Web server started on port " + webServer.getListeningPort());
        } catch (IOException e) {
            Log.e(TAG, "Failed to start web server", e);
            isRunning = false;
            return;
        }

        // Start the SSDP listener
        ssdpListenerThread = new Thread(this::listenForSsdp);
        ssdpListenerThread.start();
        Log.d(TAG, "SSDP Responder started");
    }

    public void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;

        if (ssdpListenerThread != null) {
            ssdpListenerThread.interrupt();
            ssdpListenerThread = null;
        }

        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        Log.d(TAG, "SSDP Responder stopped");
    }

    private void listenForSsdp() {
        try (MulticastSocket socket = new MulticastSocket(SSDP_PORT)) {
            InetAddress group = InetAddress.getByName(SSDP_HOST);
            socket.joinGroup(group);

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (isRunning && !Thread.currentThread().isInterrupted()) {
                try {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());

                    if (message.startsWith("M-SEARCH")) {
                        String st = getHeaderValue(message, "ST");
                        if ("ssdp:all".equals(st) || SERVICE_TYPE.equals(st)) {
                            Log.d(TAG, "Received M-SEARCH, sending response");
                            sendResponse(packet.getAddress(), packet.getPort());
                        }
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "Error receiving SSDP packet", e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create or join multicast socket", e);
        } finally {
            Log.d(TAG, "SSDP listener thread finished.");
        }
    }

    private void sendResponse(InetAddress clientAddress, int clientPort) {
        String localIp = getLocalIpAddress();
        if (localIp == null) {
            Log.e(TAG, "Could not get local IP address");
            return;
        }

        String location = "http://" + localIp + ":" + webServer.getListeningPort() + "/description.xml";
        String serverName = Build.MANUFACTURER + " " + Build.MODEL;

        String response = "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "ST: " + SERVICE_TYPE + "\r\n" +
                "USN: " + deviceUuid + "::" + SERVICE_TYPE + "\r\n" +
                "EXT:\r\n" +
                "SERVER: " + serverName + "/OmerFlex\r\n" +
                "LOCATION: " + location + "\r\n" +
                "\r\n";

        try {
            byte[] responseData = response.getBytes();
            DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
            try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                socket.send(responsePacket);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to send SSDP response", e);
        }
    }

    private String getHeaderValue(String message, String header) {
        String[] lines = message.split("\r\n");
        for (String line : lines) {
            if (line.toUpperCase().startsWith(header.toUpperCase() + ":")) {
                return line.substring(header.length() + 1).trim();
            }
        }
        return null;
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) { // IPv4
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "IP Address retrieval error", ex);
        }
        return null;
    }

    private class WebServer extends NanoHTTPD {
        public WebServer() {
            super(HTTP_PORT);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if ("/description.xml".equals(session.getUri())) {
                String friendlyName = "OmerFlex on " + Build.MODEL;
                String xml = "<?xml version=\"1.0\"?>\n" +
                        "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\n" +
                        "    <specVersion>\n" +
                        "        <major>1</major>\n" +
                        "        <minor>0</minor>\n" +
                        "    </specVersion>\n" +
                        "    <device>\n" +
                        "        <deviceType>" + DEVICE_TYPE + "</deviceType>\n" +
                        "        <friendlyName>" + friendlyName + "</friendlyName>\n" +
                        "        <manufacturer>" + Build.MANUFACTURER + "</manufacturer>\n" +
                        "        <modelName>" + Build.MODEL + "</modelName>\n" +
                        "        <UDN>" + deviceUuid + "</UDN>\n" +
                        "    </device>\n" +
                        "</root>";
                return newFixedLengthResponse(Response.Status.OK, "text/xml", xml);
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
        }
    }
}