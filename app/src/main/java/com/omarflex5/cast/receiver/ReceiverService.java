package com.omarflex5.cast.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.omarflex5.R;
import com.omarflex5.ui.player.PlayerActivity;

import org.json.JSONObject;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Service running on TV devices to:
 * 1. Advertise existence via NSD (_omarflex._tcp).
 * 2. Listen for Play commands via HTTP server.
 */
public class ReceiverService extends Service {

    private static final String TAG = "ReceiverService";
    private static final String SERVICE_TYPE = "_omarflex._tcp.";
    private static final String SERVICE_NAME_PREFIX = "OmarFlex TV";

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private CommandServer commandServer;
    private String serviceName;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Starting ReceiverService...");
        startForegroundService();
        initializeNsd();
    }

    private void startForegroundService() {
        String channelId = "receiver_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Cast Receiver", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null)
                manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("OmarFlex Receiver")
                .setContentText("Ready to receive cast requests")
                .setSmallIcon(R.drawable.ic_cast) // Ensure this icon exists or use generic
                .build();
        startForeground(101, notification);
    }

    private void initializeNsd() {
        try {
            // Try fixed port 8090 first for session persistence
            int localPort = 8090;
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new java.net.InetSocketAddress(localPort));
                socket.close();
            } catch (IOException e) {
                // If 8090 is truly occupied by another app, fallback to random
                ServerSocket socket = new ServerSocket(0);
                localPort = socket.getLocalPort();
                socket.close();
            }

            // Start HTTP Server
            commandServer = new CommandServer(localPort);
            commandServer.start();
            Log.d(TAG, "Command Server started on port " + localPort);

            // Register NSD
            nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
            registerService(localPort);

        } catch (IOException e) {
            Log.e(TAG, "Failed to start server", e);
        }
    }

    private void registerService(int port) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME_PREFIX + " (" + Build.MODEL + ")");
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        // Explicitly broadcast our preferred IP (Wi-Fi) to avoid 10.x.x.x mobile IP
        // issues
        String preferredIp = com.omarflex5.util.NetworkUtils.getLocalIpAddress();
        if (preferredIp != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                serviceInfo.setAttribute("preferred_ip", preferredIp);
            }
        }

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                serviceName = NsdServiceInfo.getServiceName();
                Log.d(TAG, "Service registered: " + serviceName);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    @Override
    public void onDestroy() {
        if (nsdManager != null && registrationListener != null) {
            nsdManager.unregisterService(registrationListener);
        }
        if (commandServer != null) {
            commandServer.stop();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --- HTTP Server ---

    private class CommandServer extends NanoHTTPD {

        public CommandServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (Method.POST.equals(session.getMethod()) && "/cast".equals(session.getUri())) {
                try {
                    String contentLengthStr = session.getHeaders().get("content-length");
                    int contentLength = 0;
                    if (contentLengthStr != null) {
                        try {
                            contentLength = Integer.parseInt(contentLengthStr);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }

                    String jsonBody = null;
                    if (contentLength > 0) {
                        byte[] buffer = new byte[contentLength];
                        session.getInputStream().read(buffer, 0, contentLength);
                        jsonBody = new String(buffer);
                    } else {
                        // Fallback to reading everything if no content length (unlikely for
                        // well-behaved client)
                        // Or try parseBody as fallback
                        Map<String, String> files = new HashMap<>();
                        session.parseBody(files);
                        jsonBody = files.get("postData");
                    }

                    if (jsonBody == null || jsonBody.isEmpty()) {
                        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing Body");
                    }

                    JSONObject json = new JSONObject(jsonBody);
                    String url = json.optString("url");
                    String title = json.optString("title");

                    // Extract headers if present
                    JSONObject headersJson = json.optJSONObject("headers");

                    Log.d(TAG, "Received Cast Request: " + title);

                    // Launch Player
                    Intent intent = new Intent(ReceiverService.this, PlayerActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, url);
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, title);

                    // Pass headers to PlayerActivity via intent extras
                    if (headersJson != null) {
                        if (headersJson.has("Referer")) {
                            intent.putExtra("EXTRA_REFERER", headersJson.optString("Referer"));
                        }
                        if (headersJson.has("User-Agent")) {
                            intent.putExtra("EXTRA_USER_AGENT", headersJson.optString("User-Agent"));
                        }
                        if (headersJson.has("Cookie")) {
                            intent.putExtra("EXTRA_COOKIE", headersJson.optString("Cookie"));
                        }
                    }

                    startActivity(intent);

                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK");

                } catch (Exception e) {
                    Log.e(TAG, "Error handling cast request", e);
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.getMessage());
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
        }
    }
}
