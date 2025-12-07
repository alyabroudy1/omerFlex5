package com.omarflex5.ui.player;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackSelectionOverride;
import android.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.omarflex5.R;
import com.omarflex5.util.MediaUtils;

import java.util.Map;

/**
 * Full-screen video player activity using ExoPlayer.
 * Supports HLS, DASH, and progressive media with custom HTTP headers.
 */
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";

    private PlayerView playerView;
    private ProgressBar loadingIndicator;
    private TextView titleView;
    private TextView castDeviceIndicator;
    private ExoPlayer player;
    private String videoUrl;
    private String videoTitle;

    // Cast Components
    private com.google.android.gms.cast.framework.CastContext castContext;
    private com.google.android.gms.cast.framework.CastSession castSession;
    private androidx.media3.cast.CastPlayer castPlayer;
    private com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession> sessionManagerListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_player);

        videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        videoTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        initPlayer();
        setupCastListener();

        // Enable hardware volume keys to control media/cast volume
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
    }

    private void setupCastListener() {
        if (com.omarflex5.util.CastUtils.isTv(this))
            return;

        try {
            castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(this);
            // Initialize CastPlayer for UI control
            castPlayer = new androidx.media3.cast.CastPlayer(castContext);

            // Add Debug Listener
            castPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    String stateString;
                    switch (playbackState) {
                        case Player.STATE_IDLE:
                            stateString = "IDLE";
                            break;
                        case Player.STATE_BUFFERING:
                            stateString = "BUFFERING";
                            break;
                        case Player.STATE_READY:
                            stateString = "READY";
                            break;
                        case Player.STATE_ENDED:
                            stateString = "ENDED";
                            break;
                        default:
                            stateString = "UNKNOWN";
                            break;
                    }
                    android.util.Log.d("CastDebug", "CastPlayer State: " + stateString);
                    if (playbackState == Player.STATE_READY) {
                        Toast.makeText(PlayerActivity.this, "Cast Ready!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    android.util.Log.e("CastDebug", "CastPlayer Error: " + error.getMessage(), error);
                    Toast.makeText(PlayerActivity.this, "Cast Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            // CRITICAL: SessionAvailabilityListener is how we know CastPlayer is actually
            // ready!
            // This is the pattern used in the reference implementation
            castPlayer.setSessionAvailabilityListener(new androidx.media3.cast.SessionAvailabilityListener() {
                @Override
                public void onCastSessionAvailable() {
                    android.util.Log.d("CastDebug", "CastPlayer Session Available!");
                    // NOW the CastPlayer is actually ready to load media
                    loadMediaToCastSession();

                    // Show cast device indicator
                    runOnUiThread(() -> {
                        com.google.android.gms.cast.framework.CastSession session = castContext.getSessionManager()
                                .getCurrentCastSession();
                        if (session != null && session.getCastDevice() != null) {
                            String deviceName = session.getCastDevice().getFriendlyName();
                            castDeviceIndicator.setText("Casting to " + deviceName);
                            castDeviceIndicator.setVisibility(View.VISIBLE);
                        }
                    });
                }

                @Override
                public void onCastSessionUnavailable() {
                    android.util.Log.d("CastDebug", "CastPlayer Session Unavailable");
                    stopLocalProxy();

                    // Hide cast device indicator
                    runOnUiThread(() -> castDeviceIndicator.setVisibility(View.GONE));
                }
            });

            sessionManagerListener = new com.google.android.gms.cast.framework.SessionManagerListener<com.google.android.gms.cast.framework.CastSession>() {
                @Override
                public void onSessionStarting(com.google.android.gms.cast.framework.CastSession session) {
                }

                @Override
                public void onSessionStarted(com.google.android.gms.cast.framework.CastSession session,
                        String sessionId) {
                    castSession = session;
                    android.util.Log.d("CastDebug", "Cast session started, updating UI");
                    // Switch UI to control Cast
                    playerView.setPlayer(castPlayer);
                    // Don't load media here! Wait for onCastSessionAvailable callback
                }

                @Override
                public void onSessionStartFailed(com.google.android.gms.cast.framework.CastSession session, int error) {
                }

                @Override
                public void onSessionEnding(com.google.android.gms.cast.framework.CastSession session) {
                }

                @Override
                public void onSessionEnded(com.google.android.gms.cast.framework.CastSession session, int error) {
                    castSession = null;
                    stopLocalProxy();
                    // Switch UI back to local player
                    if (player != null) {
                        playerView.setPlayer(player);
                    }
                }

                @Override
                public void onSessionResuming(com.google.android.gms.cast.framework.CastSession session,
                        String sessionId) {
                }

                @Override
                public void onSessionResumed(com.google.android.gms.cast.framework.CastSession session,
                        boolean wasSuspended) {
                    castSession = session;
                    playerView.setPlayer(castPlayer);
                }

                @Override
                public void onSessionResumeFailed(com.google.android.gms.cast.framework.CastSession session,
                        int error) {
                }

                @Override
                public void onSessionSuspended(com.google.android.gms.cast.framework.CastSession session, int reason) {
                }
            };
        } catch (

        Exception e) {
            // Cast not available
        }
    }

    private void loadMediaToCastSession() {
        // Get session from CastContext (instance variable may not be set yet due to
        // callback timing)
        com.google.android.gms.cast.framework.CastSession session = castContext != null
                ? castContext.getSessionManager().getCurrentCastSession()
                : null;

        if (session == null || !session.isConnected())
            return;

        // 1. Prepare Headers
        MediaUtils.ParsedMedia parsed = MediaUtils.parseUrlWithHeaders(videoUrl);
        Map<String, String> currentHeaders = parsed.getHeaders();
        if (getIntent().hasExtra("EXTRA_USER_AGENT")) {
            currentHeaders.put("User-Agent", getIntent().getStringExtra("EXTRA_USER_AGENT"));
        }
        if (getIntent().hasExtra("EXTRA_REFERER")) {
            currentHeaders.put("Referer", getIntent().getStringExtra("EXTRA_REFERER"));
        }

        // 2. Start Local Proxy
        String localIp = com.omarflex5.util.NetworkUtils.getLocalIpAddress();
        if (localIp == null || localIp.equals("0.0.0.0")) {
            Toast.makeText(this, "Connect to Wi-Fi to Cast", Toast.LENGTH_SHORT).show();
            return;
        }

        String proxyUrl = com.omarflex5.cast.server.MediaServer.getInstance().startServer(videoUrl, currentHeaders);
        if (proxyUrl == null) {
            Toast.makeText(this, "Failed to start proxy", Toast.LENGTH_SHORT).show();
            return;
        }

        // Self-test: verify server is reachable from the phone itself
        testServerReachability(proxyUrl);

        // 3. Create Media3 MediaItem - matches reference implementation
        androidx.media3.common.MediaItem.Builder mediaItemBuilder = new androidx.media3.common.MediaItem.Builder()
                .setUri(proxyUrl);

        // CRITICAL: Set MIME type based on ORIGINAL video URL, not proxy URL
        String mimeType = androidx.media3.common.MimeTypes.VIDEO_MP4; // default
        if (videoUrl.contains(".m3u8")) {
            mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8;
        } else if (videoUrl.contains(".mpd")) {
            mimeType = androidx.media3.common.MimeTypes.APPLICATION_MPD;
        }

        android.util.Log.d("CastDebug", "Setting MediaItem with MIME: " + mimeType + " for URL: " + proxyUrl);
        mediaItemBuilder.setMimeType(mimeType);

        // Add metadata
        androidx.media3.common.MediaMetadata metadata = new androidx.media3.common.MediaMetadata.Builder()
                .setTitle(videoTitle != null ? videoTitle : "Video")
                .build();
        mediaItemBuilder.setMediaMetadata(metadata);

        androidx.media3.common.MediaItem mediaItem = mediaItemBuilder.build();

        // 4. Load using CastPlayer (Media3 API only - NO RemoteMediaClient!)
        if (castPlayer != null) {
            android.util.Log.d("CastDebug", "Loading MediaItem to CastPlayer...");
            long currentPosition = player != null ? player.getCurrentPosition() : 0;
            castPlayer.setMediaItem(mediaItem, currentPosition);
            castPlayer.prepare();
            castPlayer.setPlayWhenReady(true);

            Toast.makeText(this, "Casting: " + mimeType, Toast.LENGTH_SHORT).show();

            if (player != null)
                player.pause();
        } else {
            android.util.Log.e("CastDebug", "CastPlayer is NULL!");
        }
    }

    private void stopLocalProxy() {
        com.omarflex5.cast.server.MediaServer.getInstance().stopServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (castContext != null && sessionManagerListener != null) {
            castContext.getSessionManager().addSessionManagerListener(sessionManagerListener,
                    com.google.android.gms.cast.framework.CastSession.class);
        }
        if (player != null && (castSession == null || !castSession.isConnected())) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (castContext != null && sessionManagerListener != null) {
            castContext.getSessionManager().removeSessionManagerListener(sessionManagerListener,
                    com.google.android.gms.cast.framework.CastSession.class);
        }
        // Do not pause if resuming cast
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocalProxy();
        if (castPlayer != null) {
            castPlayer.release();
            castPlayer = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        loadingIndicator = findViewById(R.id.loading_indicator);
        castDeviceIndicator = findViewById(R.id.cast_device_indicator);

        // Stretch video to fill screen (removes black bars)
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);

        // Wait for layout to complete, then setup focus animations and title
        playerView.post(() -> {
            setupControllerFocusAnimations();
            injectTitleIntoController();
        });
    }

    /**
     * Adds scale animations to default controller buttons for better TV remote
     * visibility.
     */
    private void setupControllerFocusAnimations() {
        // Find all focusable buttons in the default ExoPlayer controller
        int[] buttonIds = {
                androidx.media3.ui.R.id.exo_play_pause,
                androidx.media3.ui.R.id.exo_rew,
                androidx.media3.ui.R.id.exo_ffwd,
                androidx.media3.ui.R.id.exo_prev,
                androidx.media3.ui.R.id.exo_next,
                androidx.media3.ui.R.id.exo_settings,
                androidx.media3.ui.R.id.exo_subtitle,
                androidx.media3.ui.R.id.exo_fullscreen,
                androidx.media3.ui.R.id.exo_vr
        };

        for (int buttonId : buttonIds) {
            View button = playerView.findViewById(buttonId);
            if (button != null) {
                button.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.animate()
                                .scaleX(1.4f)
                                .scaleY(1.4f)
                                .setDuration(200)
                                .start();
                    } else {
                        v.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150)
                                .start();
                    }
                });
            }
        }

        // Add focus animation to progress bar (make it thicker)
        View progressBar = playerView.findViewById(R.id.exo_progress);
        if (progressBar != null) {
            progressBar.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.animate().scaleY(2.0f).setDuration(200).start();
                } else {
                    v.animate().scaleY(1.0f).setDuration(150).start();
                }
            });
        }

        // Setup custom buttons (back, settings)
        setupCustomButtons();
    }

    /**
     * Sets up click handlers for custom buttons in the controller.
     */
    private void setupCustomButtons() {
        // Back button
        View btnBack = playerView.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Settings button (opens settings dialog)
        View btnSettings = playerView.findViewById(R.id.exo_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsDialog());
        }

        // Cast button
        View btnCast = playerView.findViewById(R.id.btn_cast);
        if (btnCast != null) {
            // Hide on TV devices
            if (com.omarflex5.util.CastUtils.isTv(this)) {
                btnCast.setVisibility(View.GONE);
            } else {
                btnCast.setVisibility(View.VISIBLE);
                btnCast.setOnClickListener(v -> {
                    com.omarflex5.ui.cast.CastOptionsBottomSheet bottomSheet = new com.omarflex5.ui.cast.CastOptionsBottomSheet();

                    // Prepare media info
                    MediaUtils.ParsedMedia parsed = MediaUtils.parseUrlWithHeaders(videoUrl);
                    Map<String, String> currentHeaders = parsed.getHeaders();

                    // Add extra headers from intent if needed (to match initPlayer logic)
                    if (getIntent().hasExtra("EXTRA_USER_AGENT")) {
                        currentHeaders.put("User-Agent", getIntent().getStringExtra("EXTRA_USER_AGENT"));
                    }
                    if (getIntent().hasExtra("EXTRA_REFERER")) {
                        currentHeaders.put("Referer", getIntent().getStringExtra("EXTRA_REFERER"));
                    }

                    bottomSheet.setMediaInfo(videoUrl, videoTitle, currentHeaders);
                    bottomSheet.show(getSupportFragmentManager(), "CastOptions");
                });
            }
        }
    }

    private void showSettingsDialog() {
        String[] options = { "Audio Tracks", "Playback Speed" };
        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showAudioSelectionDialog();
                    } else if (which == 1) {
                        showSpeedSelectionDialog();
                    }
                })
                .show();
    }

    private void showSpeedSelectionDialog() {
        String[] speeds = { "0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x" };
        float[] speedValues = { 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f };

        int currentSpeedIndex = 2; // Default 1.0x
        if (player != null) {
            float currentSpeed = player.getPlaybackParameters().speed;
            for (int i = 0; i < speedValues.length; i++) {
                if (Math.abs(currentSpeed - speedValues[i]) < 0.1f) {
                    currentSpeedIndex = i;
                    break;
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setSingleChoiceItems(speeds, currentSpeedIndex, (dialog, which) -> {
                    if (player != null) {
                        player.setPlaybackSpeed(speedValues[which]);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void showAudioSelectionDialog() {
        if (player == null)
            return;

        Tracks tracks = player.getCurrentTracks();
        List<String> audioTitles = new ArrayList<>();
        List<TrackSelectionOverride> overrides = new ArrayList<>();

        int selectedIndex = -1;
        int trackCount = 0;

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSupported(i)) {
                        String label = group.getTrackFormat(i).label;
                        if (label == null || label.isEmpty()) {
                            label = group.getTrackFormat(i).language;
                        }
                        if (label == null || label.isEmpty()) {
                            label = "Audio Track " + (trackCount + 1);
                        }

                        audioTitles.add(label);
                        overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(), i));

                        if (group.isTrackSelected(i)) {
                            selectedIndex = trackCount;
                        }
                        trackCount++;
                    }
                }
            }
        }

        if (audioTitles.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Audio Tracks")
                .setSingleChoiceItems(audioTitles.toArray(new String[0]), selectedIndex, (dialog, which) -> {
                    TrackSelectionOverride override = overrides.get(which);
                    player.setTrackSelectionParameters(
                            player.getTrackSelectionParameters()
                                    .buildUpon()
                                    .setOverrideForType(override)
                                    .build());
                    dialog.dismiss();
                    Toast.makeText(this, "Selected: " + audioTitles.get(which), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /**
     * Sets the title in the custom controller layout.
     */
    private void injectTitleIntoController() {
        if (videoTitle == null || videoTitle.isEmpty()) {
            return;
        }

        // Find title view in custom controller layout
        titleView = playerView.findViewById(R.id.exo_title);
        if (titleView != null) {
            titleView.setText(videoTitle);
            titleView.setSelected(true); // Required for marquee to work
        }
    }

    private void initPlayer() {
        // Parse URL and headers
        MediaUtils.ParsedMedia parsedMedia = MediaUtils.parseUrlWithHeaders(videoUrl);
        String mediaUrl = parsedMedia.getUrl();
        Map<String, String> headers = parsedMedia.getHeaders();

        // Add headers from Intent if present
        if (getIntent().hasExtra("EXTRA_USER_AGENT")) {
            headers.put("User-Agent", getIntent().getStringExtra("EXTRA_USER_AGENT"));
        }
        if (getIntent().hasExtra("EXTRA_REFERER")) {
            headers.put("Referer", getIntent().getStringExtra("EXTRA_REFERER"));
        }

        // Create data source factory with headers
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        if (!headers.isEmpty()) {
            dataSourceFactory.setDefaultRequestProperties(headers);
        }

        // Create appropriate media source based on URL type
        MediaSource mediaSource = createMediaSource(mediaUrl, dataSourceFactory);

        // Build player
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // Show loading indicator while buffering
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    loadingIndicator.setVisibility(View.VISIBLE);
                } else {
                    loadingIndicator.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this,
                        "Playback error: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                loadingIndicator.setVisibility(View.GONE);
            }
        });

        // Load and play video
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /**
     * Creates the appropriate MediaSource based on the URL type.
     */
    private MediaSource createMediaSource(String url, DefaultHttpDataSource.Factory dataSourceFactory) {
        MediaUtils.MediaType mediaType = MediaUtils.getMediaType(url);
        MediaItem mediaItem = MediaItem.fromUri(url);

        switch (mediaType) {
            case HLS:
                return new HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem);
            case DASH:
                return new DashMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem);
            default:
                return new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    /**
     * Tests if the media server is reachable from this device.
     * Inspired by reference implementation's self-check.
     */
    private void testServerReachability(String serverUrl) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(serverUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestMethod("HEAD");

                int responseCode = connection.getResponseCode();
                android.util.Log.d("ServerTest",
                        "✅ Server self-test: HTTP " + responseCode + " - Server IS reachable from phone");

                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(this, "✅ Server running and accessible!", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                android.util.Log.e("ServerTest",
                        "❌ Server self-test FAILED - Server NOT reachable from phone: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "⚠️ Warning: Server may not be accessible on network", Toast.LENGTH_LONG)
                            .show();
                });
            }
        }).start();
    }

}
