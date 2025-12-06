package com.omarflex5.temp.webtest;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import java.util.HashMap;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";
    private ExoPlayer player;
    private PlayerView playerView;
    private String videoUrl;
    private String cookies;
    private String referer;
    private String userAgent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);

        // Get data from Intent
        videoUrl = getIntent().getStringExtra("VIDEO_URL");
        cookies = getIntent().getStringExtra("COOKIES");
        referer = getIntent().getStringExtra("REFERER");
        userAgent = getIntent().getStringExtra("USER_AGENT");

        // Fallback User-Agent if not provided
        if (userAgent == null || userAgent.isEmpty()) {
            userAgent = "Mozilla/5.0 (Linux; Android 8.0.0; Pixel 2 XL Build/OPD1.170816.004) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.109 Mobile Safari/537.36";
        }

        if (videoUrl == null) {
            Toast.makeText(this, "No video URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Log what we received
        Log.d(TAG, "Video URL: " + videoUrl);
        Log.d(TAG, "Cookies received: " + (cookies != null ? cookies : "null"));
        Log.d(TAG, "Referer: " + (referer != null ? referer : "null"));
        Log.d(TAG, "User-Agent: " + userAgent);

        // Check if we have captured headers from WebView
        Bundle capturedHeadersBundle = getIntent().getBundleExtra("CAPTURED_HEADERS");
        if (capturedHeadersBundle != null) {
            Log.d(TAG, "Using " + capturedHeadersBundle.size() + " captured headers from WebView");
        } else {
            Log.d(TAG, "No captured headers available, will use default headers");
        }

        // Initialize player immediately
        initializePlayer();
    }

    private void initializePlayer() {
        if (player == null) {
            // Get the WebViewFetcher from the manager
            WebViewFetcher fetcher = FetcherManager.getInstance().getFetcher();

            if (fetcher == null) {
                Toast.makeText(this, "Error: WebView fetcher not available", Toast.LENGTH_LONG).show();
                Log.e(TAG, "WebViewFetcher is null");
                return;
            }

            Log.d(TAG, "Initializing player with WebViewDataSource");

            // Create WebViewDataSource Factory
            com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory = new WebViewDataSource.Factory(
                    fetcher, userAgent);

            // Create Media Source using the custom factory
            // We use HlsMediaSource explicitly since we know it's HLS and we want to ensure
            // our DataSource is used for everything (manifest and segments)
            MediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(videoUrl)));

            player = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(player);
            player.setMediaSource(mediaSource);

            // Add Error Listener
            player.addListener(new com.google.android.exoplayer2.Player.Listener() {
                @Override
                public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                    String errorMsg = "Playback Error: " + error.getMessage();
                    if (error
                            .getCause() instanceof com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException) {
                        com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException cause = (com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException) error
                                .getCause();
                        errorMsg = "HTTP Error " + cause.responseCode + ": " + cause.responseMessage;
                    }
                    Log.e(TAG, errorMsg, error);
                    Toast.makeText(PlayerActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            });

            player.prepare();
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    private void hideSystemUi() {
        playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}
