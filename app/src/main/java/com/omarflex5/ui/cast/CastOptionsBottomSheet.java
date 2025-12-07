package com.omarflex5.ui.cast;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.Intent;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.app.MediaRouteChooserDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.omarflex5.R;
import java.util.Map;
import java.util.List;
import com.omarflex5.util.NetworkUtils;

public class CastOptionsBottomSheet extends BottomSheetDialogFragment {

    private String videoUrl;
    private String title;
    private Map<String, String> headers;

    public void setMediaInfo(String videoUrl, String title, Map<String, String> headers) {
        this.videoUrl = videoUrl;
        this.title = title;
        this.headers = headers;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_cast_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = getContext();

        // Option 1: Google Cast
        view.findViewById(R.id.option_chromecast).setOnClickListener(v -> {
            dismiss();
            if (context != null) {
                try {
                    com.google.android.gms.cast.framework.CastContext castContext = com.google.android.gms.cast.framework.CastContext
                            .getSharedInstance(context);
                    com.google.android.gms.cast.framework.CastSession currentSession = castContext.getSessionManager()
                            .getCurrentCastSession();
                    if (currentSession != null && currentSession.isConnected()) {
                        new androidx.mediarouter.app.MediaRouteControllerDialog(context).show();
                    } else {
                        MediaRouteChooserDialog dialog = new MediaRouteChooserDialog(context);
                        dialog.setRouteSelector(castContext.getMergedSelector());
                        dialog.show();
                    }
                } catch (Exception e) {
                    Toast.makeText(context, "Cast not available: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Option 2: OmarFlex TV
        View omarFlexOption = view.findViewById(R.id.option_omarflex);
        android.widget.TextView omarFlexText = view.findViewById(R.id.text_omarflex);
        omarFlexOption.setEnabled(true);

        com.omarflex5.cast.receiver.OmarFlexSessionManager omarSession = com.omarflex5.cast.receiver.OmarFlexSessionManager
                .getInstance(context);

        if (omarSession.isConnected()) {
            omarFlexText.setText("Disconnect " + omarSession.getCurrentDevice().name);
            omarFlexOption.setOnClickListener(v -> {
                omarSession.disconnect();
                Toast.makeText(context, "Disconnected from TV", Toast.LENGTH_SHORT).show();
                dismiss();
            });
        } else {
            com.omarflex5.cast.receiver.NsdDiscovery.OmarFlexDevice lastOmarDevice = omarSession.getLastUsedDevice();
            if (lastOmarDevice != null) {
                omarFlexOption.setOnClickListener(v -> {
                    dismiss();
                    // Show options: Reconnect or New Search
                    new android.app.AlertDialog.Builder(context)
                            .setTitle("OmarFlex Cast")
                            .setItems(new String[] { "Reconnect to " + lastOmarDevice.name, "Search for new TV" },
                                    (dialog, which) -> {
                                        if (which == 0) {
                                            startOmarFlexCast(context, lastOmarDevice);
                                        } else {
                                            showOmarFlexDiscoveryDialog(context);
                                        }
                                    })
                            .show();
                });
            } else {
                omarFlexOption.setOnClickListener(v -> {
                    if (context == null)
                        return;
                    dismiss();
                    showOmarFlexDiscoveryDialog(context);
                });
            }
        }

        // Option 3: DLNA
        View dlnaOption = view.findViewById(R.id.option_dlna);
        android.widget.TextView dlnaText = view.findViewById(R.id.text_dlna);

        com.omarflex5.cast.dlna.DlnaSessionManager sessionManager = com.omarflex5.cast.dlna.DlnaSessionManager
                .getInstance(context);

        if (sessionManager.isConnected()) {
            dlnaText.setText("Disconnect " + sessionManager.getCurrentDevice().friendlyName);
            dlnaOption.setOnClickListener(v -> {
                sessionManager.disconnect();
                // Stop server if running
                com.omarflex5.cast.server.MediaServer.getInstance().stopServer();
                // Stop DLNA playback
                com.omarflex5.cast.dlna.DlnaCaster.stopCast(sessionManager.getCurrentDevice().location);
                Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show();
                dismiss();
            });
        } else {
            com.omarflex5.cast.dlna.SsdpDiscoverer.DlnaDevice lastDevice = sessionManager.getLastUsedDevice();
            if (lastDevice != null) {
                // Determine logic: Show 2 options? Or one that opens a choice dialog?
                // Let's modify the click listener to show a choice: "Reconnect to X" or "Scan
                // New"
                dlnaOption.setOnClickListener(v -> {
                    dismiss();
                    showReconnectOrScanDialog(context, lastDevice);
                });
            } else {
                dlnaOption.setOnClickListener(v -> {
                    if (context == null)
                        return;
                    dismiss();
                    showDlnaDiscoveryDialog(context);
                });
            }
        }

        // Option 4: External Player
        view.findViewById(R.id.option_external).setOnClickListener(v -> {
            startExternalPlayer(context);
        });
    }

    private void startExternalPlayer(Context context) {
        if (videoUrl == null) {
            Toast.makeText(context, "No video info available", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            android.net.Uri uri = android.net.Uri.parse(videoUrl);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "video/*");
            intent.putExtra("title", title != null ? title : "Video");

            // Try to pass headers if supported by players (e.g. MX Player)
            if (headers != null && !headers.isEmpty()) {
                String[] headersArray = new String[headers.size() * 2];
                int i = 0;
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    headersArray[i] = entry.getKey();
                    headersArray[i + 1] = entry.getValue();
                    i += 2;
                }
                intent.putExtra("headers", headersArray);
            }

            dismiss();
            context.startActivity(Intent.createChooser(intent, "Play with..."));
        } catch (Exception e) {
            Toast.makeText(context, "No external player found", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReconnectOrScanDialog(Context context,
            com.omarflex5.cast.dlna.SsdpDiscoverer.DlnaDevice lastDevice) {
        String[] options = { "Reconnect to " + lastDevice.friendlyName, "Scan for new devices" };
        new android.app.AlertDialog.Builder(context)
                .setTitle("DLNA Cast")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        startDlnaCast(context, lastDevice);
                    } else {
                        showDlnaDiscoveryDialog(context);
                    }
                })
                .show();
    }

    private void showDlnaDiscoveryDialog(Context context) {
        android.app.AlertDialog progressDialog = new android.app.AlertDialog.Builder(context)
                .setTitle("Scanning for Devices")
                .setMessage("Searching your Wi-Fi network...")
                .setCancelable(true)
                .show();

        com.omarflex5.cast.dlna.SsdpDiscoverer.discoverDevices(context,
                new com.omarflex5.cast.dlna.SsdpDiscoverer.DiscoveryListener() {
                    @Override
                    public void onDeviceFound(com.omarflex5.cast.dlna.SsdpDiscoverer.DlnaDevice device) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                progressDialog.setMessage("Found: " + device.friendlyName + "\nScanning...");
                            });
                        }
                    }

                    @Override
                    public void onDiscoveryComplete(List<com.omarflex5.cast.dlna.SsdpDiscoverer.DlnaDevice> devices) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                progressDialog.dismiss();
                                if (devices.isEmpty()) {
                                    Toast.makeText(context, "No DLNA devices found.", Toast.LENGTH_LONG).show();
                                } else {
                                    showDeviceSelectionDialog(context, devices);
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(context, "Discovery failed: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });
    }

    private void showDeviceSelectionDialog(Context context,
            List<com.omarflex5.cast.dlna.SsdpDiscoverer.DlnaDevice> devices) {
        String[] deviceNames = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            deviceNames[i] = devices.get(i).friendlyName;
        }

        new android.app.AlertDialog.Builder(context)
                .setTitle("Select Device")
                .setItems(deviceNames, (dialog, which) -> {
                    com.omarflex5.cast.dlna.SsdpDiscoverer.DlnaDevice selectedDevice = devices.get(which);
                    startDlnaCast(context, selectedDevice);
                })
                .show();
    }

    private void startDlnaCast(Context context, com.omarflex5.cast.dlna.SsdpDiscoverer.DlnaDevice device) {
        if (videoUrl == null) {
            Toast.makeText(context, "No video info available", Toast.LENGTH_SHORT).show();
            return;
        }

        String localIp = NetworkUtils.getLocalIpAddress();
        if (localIp == null || localIp.equals("0.0.0.0")) {
            Toast.makeText(context, "Cannot cast: Invalid Local IP. Connect to Wi-Fi.", Toast.LENGTH_LONG).show();
            return;
        }

        // 1. Mark as Connected (Session Logic)
        com.omarflex5.cast.dlna.DlnaSessionManager.getInstance(context).connect(device);

        // 2. Start Local Proxy
        String proxyUrl = com.omarflex5.cast.server.MediaServer.getInstance().startServer(videoUrl, headers);
        if (proxyUrl == null) {
            Toast.makeText(context, "Failed to start local server", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(context, "Casting to " + device.friendlyName + "...", Toast.LENGTH_SHORT).show();

        // 3. Send to Device
        com.omarflex5.cast.dlna.DlnaCaster.castToDevice(
                device.location,
                proxyUrl,
                title != null ? title : "OmarFlex Video",
                new com.omarflex5.cast.dlna.DlnaCaster.CastListener() {
                    @Override
                    public void onCastSuccess() {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> Toast
                                    .makeText(context, "Playback started on TV!", Toast.LENGTH_SHORT).show());
                        }
                    }

                    @Override
                    public void onCastError(String error) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                Toast.makeText(context, "Cast failed: " + error, Toast.LENGTH_LONG).show();
                                com.omarflex5.cast.server.MediaServer.getInstance().stopServer();
                            });
                        }
                    }
                });
    }

    private void showOmarFlexDiscoveryDialog(Context context) {
        android.app.AlertDialog progressDialog = new android.app.AlertDialog.Builder(context)
                .setTitle("Searching for OmarFlex TV")
                .setMessage("Make sure the app is open on your TV...")
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Logic to stop discovery
                })
                .show();

        com.omarflex5.cast.receiver.NsdDiscovery discovery = new com.omarflex5.cast.receiver.NsdDiscovery(context,
                new com.omarflex5.cast.receiver.NsdDiscovery.DiscoveryListener() {
                    @Override
                    public void onDeviceFound(com.omarflex5.cast.receiver.NsdDiscovery.OmarFlexDevice device) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                progressDialog.dismiss();
                                showOmarFlexDeviceSelection(context, device);
                            });
                        }
                    }

                    @Override
                    public void onDiscoveryStopped() {
                    }

                    @Override
                    public void onError(String error) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(context, "Search failed: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });

        progressDialog.setOnDismissListener(dialog -> discovery.stopDiscovery());
        discovery.startDiscovery();
    }

    private void showOmarFlexDeviceSelection(Context context,
            com.omarflex5.cast.receiver.NsdDiscovery.OmarFlexDevice device) {
        new android.app.AlertDialog.Builder(context)
                .setTitle("Found Device")
                .setMessage("Connect to " + device.name + "?")
                .setPositiveButton("Connect", (dialog, which) -> {
                    startOmarFlexCast(context, device);
                })
                .show();
    }

    private void startOmarFlexCast(Context context, com.omarflex5.cast.receiver.NsdDiscovery.OmarFlexDevice device) {
        if (videoUrl == null)
            return;

        Toast.makeText(context, "Sending to " + device.name + "...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                // Build JSON Payload
                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("url", videoUrl);
                payload.put("title", title != null ? title : "Casted Video");

                // Send POST
                java.net.URL url = new java.net.URL("http://" + device.host + ":" + device.port + "/cast");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);

                // Write body
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        if (code == 200) {
                            com.omarflex5.cast.receiver.OmarFlexSessionManager.getInstance(context).connect(device);
                            Toast.makeText(context, "Playing on TV!", Toast.LENGTH_SHORT).show();
                        } else {
                            // Read error response if any
                            String errorMsg = "";
                            try {
                                java.io.InputStream es = conn.getErrorStream();
                                if (es != null) {
                                    byte[] buffer = new byte[es.available()];
                                    es.read(buffer);
                                    errorMsg = new String(buffer);
                                }
                            } catch (Exception ex) {
                            }

                            Toast.makeText(context, "Failed: " + code + " " + errorMsg, Toast.LENGTH_LONG)
                                    .show();
                            android.util.Log.e("OmarFlexCast", "Server returned: " + code + " " + errorMsg);
                        }
                    });
                }

            } catch (Exception e) {
                android.util.Log.e("OmarFlexCast", "Cast Exception", e);
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        Toast.makeText(context, "Err: " + e.getClass().getSimpleName() + ": " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }
}
