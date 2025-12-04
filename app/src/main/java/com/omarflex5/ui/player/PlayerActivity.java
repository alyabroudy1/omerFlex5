package com.omarflex5.ui.player;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
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

        // Inject title into ExoPlayer's default bottom bar
        injectTitleIntoController();
    }

    /**
     * Injects a title TextView into ExoPlayer's default controller bottom bar.
     * This extends the default controller without replacing it.
     */
    private void injectTitleIntoController() {
        if (videoTitle == null || videoTitle.isEmpty()) {
            return;
        }

        // Find the bottom bar in the default ExoPlayer controller
        View bottomBar = playerView.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);
        if (bottomBar instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout bottomBarLayout = (android.widget.FrameLayout) bottomBar;

            // Create title TextView
            titleView = new TextView(this);
            titleView.setText(videoTitle);
            titleView.setTextColor(getResources().getColor(android.R.color.white));
            titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            titleView.setSingleLine(true);
            titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            titleView.setMaxWidth(400);

            // Center it in the bottom bar
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = android.view.Gravity.CENTER;

            bottomBarLayout.addView(titleView, params);
        }
    }

    private void initPlayer() {
        // Parse URL and headers
        MediaUtils.ParsedMedia parsedMedia = MediaUtils.parseUrlWithHeaders(videoUrl);
        String mediaUrl = parsedMedia.getUrl();
        Map<String, String> headers = parsedMedia.getHeaders();

        // Create data source factory with headers
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        if (parsedMedia.hasHeaders()) {
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
