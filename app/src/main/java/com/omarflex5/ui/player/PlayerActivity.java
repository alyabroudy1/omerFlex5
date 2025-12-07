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
    private ExoPlayer player;
    private String videoUrl;
    private String videoTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full screen
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_player);

        // Get video info from intent
        videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        videoTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        initPlayer();
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        loadingIndicator = findViewById(R.id.loading_indicator);

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
