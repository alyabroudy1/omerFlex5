package com.omarflex5.temp.omerflex.service;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.ui.PlayerView;

import androidx.annotation.Nullable;

import com.google.android.gms.cast.framework.CastContext;
import com.omerflex.providers.MediaServer;
import com.omerflex.server.Util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;

@androidx.media3.common.util.UnstableApi
public class PlayerManager implements SessionAvailabilityListener {

    private static final String TAG = "PlayerManager";

    private final Context context;
    private final PlayerView playerView;
    private final com.omerflex.entity.Movie movie;

    private ExoPlayer localPlayer;
    @Nullable private CastPlayer castPlayer;
    private Player currentPlayer;

    public PlayerManager(Context context, @Nullable CastContext castContext, PlayerView playerView, com.omerflex.entity.Movie movie) {
        this.context = context;
        this.playerView = playerView;
        this.movie = movie;

        // 1. Always create local ExoPlayer
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder().build();
        localPlayer = new ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build();

        localPlayer.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
                Log.d(TAG, "ExoPlayer Load Started: URI=" + loadEventInfo.uri);
            }

            @Override
            public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
                if (mediaLoadData.trackFormat != null) {
                    Log.d(TAG, "ExoPlayer downstream format changed: Bitrate=" + mediaLoadData.trackFormat.bitrate + ", mimeType=" + mediaLoadData.trackFormat.sampleMimeType);
                }
            }
        });

        localPlayer.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (mediaItem != null && mediaItem.localConfiguration != null) {
                    Log.d(TAG, "LocalPlayer media item transition: URI=" + mediaItem.localConfiguration.uri + ", Reason=" + reason);
                } else {
                    Log.d(TAG, "LocalPlayer media item transition: mediaItem is null, Reason=" + reason);
                }
            }
        });

        // 2. Only create CastPlayer if context is available
        if (castContext != null) {
            castPlayer = new CastPlayer(castContext);
            castPlayer.setSessionAvailabilityListener(this);
            castPlayer.addListener(new Player.Listener() {
                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    if (mediaItem != null && mediaItem.localConfiguration != null) {
                        Log.d(TAG, "CastPlayer media item transition: URI=" + mediaItem.localConfiguration.uri + ", Reason=" + reason);
                    } else {
                        Log.d(TAG, "CastPlayer media item transition: mediaItem is null, Reason=" + reason);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "CastPlayer error: " + error.getMessage(), error);
                    Toast.makeText(context, "Cast playback failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        // 3. Set initial player (defaults to local player)
        if (castPlayer != null && castPlayer.isCastSessionAvailable()) {
            this.currentPlayer = castPlayer;
        } else {
            this.currentPlayer = localPlayer;
        }
        playerView.setPlayer(this.currentPlayer);
    }

    public ExoPlayer getLocalPlayer() {
        return localPlayer;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }
    // Fetches the first playable variant (.m3u8) from a master manifest
    public static String getPlayableHlsUrl(String masterUrl) {
        try {
            URL url = new URL(masterUrl);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("http") && line.endsWith(".m3u8")) {
                    reader.close();
                    return line;
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // fallback — return original if no variant found
        return masterUrl;
    }

    public void prepare(MediaSource mediaSource) {
        MediaItem mediaItem = mediaSource.getMediaItem();
        Log.d(TAG, "Preparing media with MIME type: " + mediaItem.localConfiguration.mimeType);
        Log.d(TAG, "Full MediaItem URI: " + mediaItem.localConfiguration.uri);
        Log.d(TAG, "Full MediaItem MIME Type: " + mediaItem.localConfiguration.mimeType);

        if (currentPlayer instanceof ExoPlayer) {
            ((ExoPlayer) currentPlayer).setPlayWhenReady(true);
            ((ExoPlayer) currentPlayer).prepare(mediaSource);
        } else if (currentPlayer instanceof CastPlayer) {
            // Rebuild the MediaItem explicitly for Cast compatibility
            MediaItem castItem = new MediaItem.Builder()
//                    .setUri(Uri.parse(getPlayableHlsUrl(mediaItem.localConfiguration.uri.toString())))
                    .setUri(mediaItem.localConfiguration.uri)
                    .setMimeType("application/vnd.apple.mpegurl") // HLS MIME type
                    .setMediaMetadata(mediaItem.mediaMetadata)
                    .build();

            Log.d(TAG, "Preparing CastPlayer with HLS item: " + castItem.localConfiguration.uri);
            ((CastPlayer) currentPlayer).setMediaItem(castItem);
        } else if (currentPlayer != null) {
            currentPlayer.setMediaItem(mediaItem);
        }


    }

    private void switchPlayer() {
        // Cannot switch if cast player was never created
        if (castPlayer == null) return;

        Player newPlayer = castPlayer.isCastSessionAvailable() ? castPlayer : localPlayer;
        if (currentPlayer == newPlayer) {
            return; // No switch needed
        }

        long playbackPositionMs = C.TIME_UNSET;
        boolean playWhenReady = false;
        MediaItem currentMediaItem = null;

        if (currentPlayer != null) {
            playbackPositionMs = currentPlayer.getCurrentPosition();
            playWhenReady = currentPlayer.getPlayWhenReady();
            currentMediaItem = currentPlayer.getCurrentMediaItem();
            currentPlayer.stop();
        }

        currentPlayer = newPlayer;
        playerView.setPlayer(currentPlayer);

        Log.d(TAG, "Switched to " + (newPlayer == castPlayer ? "CastPlayer" : "LocalPlayer"));

        if (newPlayer == castPlayer) {
            // Check if the original video URL has custom headers
            String originalVideoUrl = movie.getVideoUrl();
            String[] parts = originalVideoUrl.split("\\|", 2);
            if (parts.length == 2) {
                // Video URL has custom headers, start MediaServer to proxy it
                Map<String, String> headers = com.omerflex.server.Util.extractHeaders(parts[1]);
                MediaServer mediaServer = MediaServer.getInstance();
                String localServerUrl = mediaServer.startServer(movie, headers);
                if (localServerUrl != null) {
                    // Update the MediaItem to use the local server URL
                    currentMediaItem = currentMediaItem.buildUpon().setUri(localServerUrl).build();
                    Log.d(TAG, "Using MediaServer for CastPlayer: " + localServerUrl);
                } else {
                    Log.e(TAG, "Failed to start MediaServer for CastPlayer. Using original URL.");
                }
            }
        } else if (currentPlayer == castPlayer && newPlayer == localPlayer) {
            // Switching from CastPlayer to LocalPlayer, stop MediaServer if it was running
            MediaServer.getInstance().stopServer();
            Log.d(TAG, "MediaServer stopped due to Cast session ending.");
        }

        if (currentMediaItem != null) {
            currentPlayer.setMediaItem(currentMediaItem, playbackPositionMs);
        }
        currentPlayer.setPlayWhenReady(playWhenReady);
        currentPlayer.prepare();
    }

    @Override
    public void onCastSessionAvailable() {
        if (castPlayer == null) return;
        Log.d(TAG, "Cast session is available");
        switchPlayer();
    }

    @Override
    public void onCastSessionUnavailable() {
        if (castPlayer == null) return;
        Log.d(TAG, "Cast session is unavailable");
        switchPlayer();
    }

    public void release() {
        if (localPlayer != null) {
            localPlayer.release();
            localPlayer = null;
        }
        if (castPlayer != null) {
            castPlayer.setSessionAvailabilityListener(null);
            castPlayer.release();
            castPlayer = null;
        }
        currentPlayer = null;
    }
}
