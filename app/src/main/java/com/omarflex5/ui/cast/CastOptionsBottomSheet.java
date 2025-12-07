package com.omarflex5.ui.cast;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

        // Option 2: OmarFlex TV (Placeholder)
        view.findViewById(R.id.option_omarflex).setOnClickListener(v -> {
            Toast.makeText(context, "Start OmarFlex on your TV to cast (Coming Soon)", Toast.LENGTH_SHORT).show();
        });

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
            Toast.makeText(context, "External Player Support Coming Soon", Toast.LENGTH_SHORT).show();
        });
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
}
