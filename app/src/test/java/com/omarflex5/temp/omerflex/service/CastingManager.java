package com.omarflex5.temp.omerflex.service;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.mediarouter.app.MediaRouteChooserDialogFragment;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouter;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.providers.DlnaCaster;
import com.omerflex.providers.MediaServer;
import com.omerflex.providers.SsdpDiscoverer;
import com.omerflex.service.utils.NetworkUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CastingManager {

    private static final String TAG = "CastingManager";

    private final AppCompatActivity activity;
    private final Movie movie;
    private PlayerManager playerManager;

    private CastContext castContext;
    private CastStateListener castStateListener;
    private MediaRouter mediaRouter;
    private MediaRouter.Callback mediaRouterCallback;

    private boolean isGoogleCastAvailable = false;
    private boolean googleCastDevicesAvailable = false;
    private final Map<String, SsdpDiscoverer.DlnaDevice> deviceLocations = new HashMap<>();

    public CastingManager(AppCompatActivity activity, Movie movie) {
        this.activity = activity;
        this.movie = movie;
    }

    public void setPlayerManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public void init() {
        try {
            castContext = CastContext.getSharedInstance(activity);
            isGoogleCastAvailable = true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Google Cast SDK not available on this device.", e);
            isGoogleCastAvailable = false;
            return; // Stop further Google Cast initialization
        }

        mediaRouter = MediaRouter.getInstance(activity);

        mediaRouterCallback = new MediaRouter.Callback() {
            @Override
            public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
                updateCastDeviceAvailability();
            }
            @Override
            public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
                updateCastDeviceAvailability();
            }
            @Override
            public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
                updateCastDeviceAvailability();
            }
        };

        castStateListener = newState -> {
            googleCastDevicesAvailable = (newState != CastState.NO_DEVICES_AVAILABLE);
            Log.d(TAG, "CastState changed: " + newState);
        };

        updateCastDeviceAvailability();
    }

    public CastContext getCastContext() {
        return castContext;
    }

    public void onStart() {
        if (!isGoogleCastAvailable) return;
        castContext.addCastStateListener(castStateListener);
        mediaRouter.addCallback(new androidx.mediarouter.media.MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build(), mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    public void onStop() {
        if (!isGoogleCastAvailable) return;
        mediaRouter.removeCallback(mediaRouterCallback);
    }

    public void onPause() {
        if (!isGoogleCastAvailable) return;
        castContext.removeCastStateListener(castStateListener);
    }

    public void setUpMediaRouteButton(androidx.mediarouter.app.MediaRouteButton mediaRouteButton) {
        if (isGoogleCastAvailable) {
            CastButtonFactory.setUpMediaRouteButton(activity.getApplicationContext(), mediaRouteButton);
        }
    }

    private void updateCastDeviceAvailability() {
        if (!isGoogleCastAvailable || mediaRouter == null) return;
        googleCastDevicesAvailable = mediaRouter.isRouteAvailable(
                new androidx.mediarouter.media.MediaRouteSelector.Builder()
                        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                        .build(),
                0);
    }

    public void showCastOrDlnaDialog() {
        SharedPreferences prefs = activity.getSharedPreferences("dlna_prefs", AppCompatActivity.MODE_PRIVATE);
        String lastDeviceName = prefs.getString("last_dlna_name", null);
        String lastDeviceLocation = prefs.getString("last_dlna_location", null);

        final List<String> items = new ArrayList<>();

        if (lastDeviceName != null && lastDeviceLocation != null) {
            items.add("Reconnect to " + lastDeviceName);
        }
        // "Google Cast Device" is now handled by the MediaRouteButton in the player controls.
        items.add("Scan for DLNA Devices");
        items.add("Share to another app");

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Cast to...");
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            String selectedItem = items.get(which);
            if (selectedItem.startsWith("Reconnect")) {
                castToDlnaLocation(lastDeviceName, lastDeviceLocation);
            } else if (selectedItem.equals("Google Cast Device")) {
                MediaRouteChooserDialogFragment chooser = new MediaRouteChooserDialogFragment();
                chooser.setRouteSelector(new androidx.mediarouter.media.MediaRouteSelector.Builder()
                        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                        .build());
                chooser.show(activity.getSupportFragmentManager(), "MediaRouteChooser");
            } else if (selectedItem.equals("Scan for DLNA Devices")) {
                showDiscoveredDevices();
            } else if (selectedItem.equals("Share to another app")) {
                shareVideoUrl();
            }
        });
        builder.create().show();
    }

    private void shareVideoUrl() {
        if (movie == null || movie.getVideoUrl() == null) {
            Toast.makeText(activity, "Video URL not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = movie.getVideoUrl();
        String[] parts = url.split("\\|", 2);
        String cleanUrl = parts[0];

        Intent shareIntent = new Intent(Intent.ACTION_VIEW);
        shareIntent.setDataAndType(Uri.parse(cleanUrl), "video/*");
        shareIntent.putExtra(Intent.EXTRA_TITLE, movie.getTitle());

        Intent chooserIntent = Intent.createChooser(shareIntent, "Open with");

        if (chooserIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivity(chooserIntent);
        } else {
            Toast.makeText(activity, "No app found to play this video", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDiscoveredDevices() {
        AlertDialog loadingDialog = new AlertDialog.Builder(activity)
                .setTitle("Discovering DLNA Devices...")
                .setMessage("Searching for devices on your network...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        deviceLocations.clear();

        SsdpDiscoverer.discoverDevicesWithDetails(new SsdpDiscoverer.DiscoveryListener() {
            @Override
            public void onDeviceFound(SsdpDiscoverer.DlnaDevice device) {
                Log.d(TAG, "Discovered device: " + device.friendlyName);
                deviceLocations.put(device.toString(), device);
            }

            @Override
            public void onDiscoveryComplete(List<String> devices) {
                activity.runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    showDiscoveredDevicesDialog(devices);
                });
            }

            @Override
            public void onError(String error) {
                activity.runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    showDiscoveryError(error);
                });
            }
        });
    }

    private void onDlnaDeviceSelected(String deviceInfo) {
        SsdpDiscoverer.DlnaDevice selectedDevice = deviceLocations.get(deviceInfo);
        if (selectedDevice == null || selectedDevice.location == null) {
            Toast.makeText(activity, "Device info not found", Toast.LENGTH_SHORT).show();
            return;
        }
        castToDlnaLocation(deviceInfo, selectedDevice.location);
    }

    private void castToDlnaLocation(String deviceName, String locationUrl) {
        // First, check if we're on the right network
        String localIp = NetworkUtils.getLocalIpAddress();
        if (localIp.startsWith("10.") || localIp.startsWith("192.168.") || localIp.startsWith("172.")) {
            // Good - private IP range
            Log.d(TAG, "Using private IP: " + localIp);
        } else {
            // Bad - likely mobile data or unreachable IP
            activity.runOnUiThread(() -> {
                new AlertDialog.Builder(activity)
                        .setTitle("Network Issue")
                        .setMessage("Your phone appears to be on mobile data. Both devices must be on the same Wi-Fi network for casting to work.")
                        .setPositiveButton("OK", null)
                        .show();
            });
            return;
        }

        AlertDialog castingDialog = new AlertDialog.Builder(activity)
                .setTitle("Casting to " + deviceName)
                .setMessage("Setting up media server...")
                .setCancelable(false)
                .create();
        castingDialog.show();

        String url = movie.getVideoUrl();
        String[] parts = url.split("\\|", 2);
        Map<String, String> headers = new HashMap<>();
        if (parts.length == 2) {
            headers = com.omerflex.server.Util.extractHeaders(parts[1]);
        }

        MediaServer mediaServer = MediaServer.getInstance();
        String localServerUrl = mediaServer.startServer(movie, headers);

        if (localServerUrl == null) {
            castingDialog.dismiss();
            Toast.makeText(activity, "Failed to start media server", Toast.LENGTH_LONG).show();
            return;
        }

        // Test if the server is actually reachable
        testServerReachability(localServerUrl, success -> {
            if (!success) {
                activity.runOnUiThread(() -> {
                    castingDialog.dismiss();
                    new AlertDialog.Builder(activity)
                            .setTitle("Server Not Reachable")
                            .setMessage("Media server started but may not be accessible from the TV. Check that both devices are on the same Wi-Fi.")
                            .setPositiveButton("OK", null)
                            .show();
                });
                return;
            }

            // Continue with casting...
            DlnaCaster.castToDevice(locationUrl, localServerUrl, movie.getTitle(), new DlnaCaster.CastListener() {
                @Override
                public void onCastSuccess() {
                    activity.runOnUiThread(() -> {
                        castingDialog.dismiss();
                        Toast.makeText(activity, "Casting started successfully to " + deviceName, Toast.LENGTH_LONG).show();

                        SharedPreferences prefs = activity.getSharedPreferences("dlna_prefs", AppCompatActivity.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("last_dlna_name", deviceName);
                        editor.putString("last_dlna_location", locationUrl);
                        editor.apply();

                        if (playerManager != null && playerManager.getCurrentPlayer() != null && playerManager.getCurrentPlayer().isPlaying()) {
                            playerManager.getCurrentPlayer().pause();
                        }
                    });
                }

                @Override
                public void onCastError(String error) {
                    activity.runOnUiThread(() -> {
                        castingDialog.dismiss();
                        Toast.makeText(activity, "Cast failed: " + error, Toast.LENGTH_LONG).show();
                        MediaServer.getInstance().stopServer();
                    });
                }
            });
        });
    }

    private void testServerReachability(String serverUrl, Consumer<Boolean> callback) {
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("HEAD");

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Server reachability test: " + responseCode);
                callback.accept(responseCode == 200);

            } catch (Exception e) {
                Log.e(TAG, "Server not reachable", e);
                callback.accept(false);
            }
        }).start();
    }

    private void showDiscoveredDevicesDialog(List<String> devices) {
        if (devices.isEmpty()) {
            showNoDevicesFound();
            return;
        }

        String[] deviceArray = devices.toArray(new String[0]);

        new AlertDialog.Builder(activity)
                .setTitle("Discovered DLNA Devices (" + devices.size() + ")")
                .setItems(deviceArray, (dialog, which) -> {
                    String selectedDevice = devices.get(which);
                    onDlnaDeviceSelected(selectedDevice);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDiscoveryError(String error) {
        new AlertDialog.Builder(activity)
                .setTitle("Discovery Error")
                .setMessage("Failed to discover devices: " + error + "\n\nMake sure you're connected to Wi-Fi.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showNoDevicesFound() {
        new AlertDialog.Builder(activity)
                .setTitle("No Devices Found")
                .setMessage("No casting devices found on your network.\n\n" +
                        "Make sure:\n" +
                        "• Your TV/Cast device is on the same Wi-Fi\n" +
                        "• Your TV is turned on\n" +
                        "• Your device supports casting (Chromecast, Smart TV, etc.)")
                .setPositiveButton("OK", null)
                .show();
    }
}
