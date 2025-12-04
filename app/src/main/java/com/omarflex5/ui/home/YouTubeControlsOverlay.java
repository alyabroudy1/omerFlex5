package com.omarflex5.ui.home;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.omarflex5.R;

/**
 * Controller for the YouTube custom controls overlay.
 * Handles show/hide, button actions, and progress updates.
 */
public class YouTubeControlsOverlay {
    private static final String TAG = "YouTubeControlsOverlay";
    private static final long AUTO_HIDE_DELAY_MS = 5000;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = 1000;

    private final View rootView;
    private final WebView webView;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // UI Elements
    private final ImageButton btnPlayPause;
    private final ImageButton btnRewind;
    private final ImageButton btnForward;
    private final ImageButton btnQuality;
    private final ImageButton btnSpeed;
    private final ImageButton btnCaptions;
    private final ImageButton btnExitFullscreen;
    private final SeekBar seekBar;
    private final TextView textCurrentTime;
    private final TextView textDuration;

    // State
    private boolean isPlaying = true;
    private boolean isVisible = false;
    private boolean isSeeking = false;
    private boolean captionsEnabled = false;
    private double videoDuration = 0;
    private OnExitFullscreenListener exitListener;

    // Current settings (for highlighting in menus)
    private String currentQuality = "auto";
    private double currentSpeed = 1.0;

    // Quality options
    private static final String[][] QUALITY_OPTIONS = {
            { "auto", "Auto" },
            { "hd2160", "4K (2160p)" },
            { "hd1440", "1440p" },
            { "hd1080", "1080p" },
            { "hd720", "720p" },
            { "large", "480p" },
            { "medium", "360p" },
            { "small", "240p" },
            { "tiny", "144p" }
    };

    // Speed options
    private static final double[] SPEED_VALUES = { 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0 };
    private static final String[] SPEED_LABELS = { "0.25x", "0.5x", "0.75x", "Normal", "1.25x", "1.5x", "1.75x", "2x" };

    // Runnables
    private final Runnable autoHideRunnable = this::hide;
    private final Runnable progressUpdateRunnable = this::updateProgress;

    public interface OnExitFullscreenListener {
        void onExitFullscreen();
    }

    public YouTubeControlsOverlay(View rootView, WebView webView) {
        this.rootView = rootView;
        this.webView = webView;
        this.context = rootView.getContext();

        // Find views
        btnPlayPause = rootView.findViewById(R.id.btn_play_pause);
        btnRewind = rootView.findViewById(R.id.btn_rewind);
        btnForward = rootView.findViewById(R.id.btn_forward);
        btnQuality = rootView.findViewById(R.id.btn_quality);
        btnSpeed = rootView.findViewById(R.id.btn_speed);
        btnCaptions = rootView.findViewById(R.id.btn_captions);
        btnExitFullscreen = rootView.findViewById(R.id.btn_exit_fullscreen);
        seekBar = rootView.findViewById(R.id.seek_bar);
        textCurrentTime = rootView.findViewById(R.id.text_current_time);
        textDuration = rootView.findViewById(R.id.text_duration);

        setupListeners();
    }

    public void setOnExitFullscreenListener(OnExitFullscreenListener listener) {
        this.exitListener = listener;
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> seekRelative(-10));
        btnForward.setOnClickListener(v -> seekRelative(10));
        btnQuality.setOnClickListener(v -> showQualityMenu());
        btnSpeed.setOnClickListener(v -> showSpeedMenu());
        btnCaptions.setOnClickListener(v -> toggleCaptions());
        btnExitFullscreen.setOnClickListener(v -> {
            if (exitListener != null) {
                exitListener.onExitFullscreen();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && videoDuration > 0) {
                    double newTime = (progress / 100.0) * videoDuration;
                    textCurrentTime.setText(formatTime((int) newTime));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
                resetAutoHide();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeeking = false;
                if (videoDuration > 0) {
                    double newTime = (seekBar.getProgress() / 100.0) * videoDuration;
                    seekTo(newTime);
                    showSeekFeedback(newTime);
                }
            }
        });

        // Reset auto-hide on any focus change
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (hasFocus)
                resetAutoHide();
        };
        btnPlayPause.setOnFocusChangeListener(focusListener);
        btnRewind.setOnFocusChangeListener(focusListener);
        btnForward.setOnFocusChangeListener(focusListener);
        btnQuality.setOnFocusChangeListener(focusListener);
        btnSpeed.setOnFocusChangeListener(focusListener);
        btnCaptions.setOnFocusChangeListener(focusListener);
        btnExitFullscreen.setOnFocusChangeListener(focusListener);
        seekBar.setOnFocusChangeListener(focusListener);

        // Touch support: tap anywhere on WebView to show/hide controls
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                if (isVisible) {
                    hide();
                } else {
                    show();
                    btnPlayPause.requestFocus();
                }
            }
            // Return false to allow WebView to still handle touch events (scroll, etc.)
            return false;
        });

        // Also allow tapping on the overlay background to hide it
        rootView.setOnClickListener(v -> {
            // Only hide if click is on background, not on buttons
            // (buttons have their own click handlers)
            if (isVisible) {
                hide();
            }
        });
    }

    public void show() {
        if (!isVisible) {
            rootView.setVisibility(View.VISIBLE);
            AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(200);
            rootView.startAnimation(fadeIn);
            isVisible = true;
            btnPlayPause.requestFocus();
            startProgressUpdates();
            // Sync current settings from video
            syncCurrentSettings();
        }
        resetAutoHide();
    }

    public void hide() {
        if (isVisible) {
            AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
            fadeOut.setDuration(200);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    rootView.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            rootView.startAnimation(fadeOut);
            isVisible = false;
            stopProgressUpdates();
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void toggle() {
        if (isVisible)
            hide();
        else
            show();
    }

    private void resetAutoHide() {
        handler.removeCallbacks(autoHideRunnable);
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS);
    }

    // ==================== PLAYBACK CONTROLS ====================

    private void togglePlayPause() {
        resetAutoHide();
        isPlaying = !isPlaying;
        btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

        String js = "(function() { var v = document.querySelector('video'); if (v) { " +
                (isPlaying ? "v.play();" : "v.pause();") +
                " } })();";
        webView.evaluateJavascript(js, null);

        showToast(isPlaying ? "Playing" : "Paused");
    }

    private void seekRelative(int seconds) {
        resetAutoHide();
        String js = "(function() { " +
                "var v = document.querySelector('video'); " +
                "if (v) { " +
                "  var wasPlaying = !v.paused; " +
                "  v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + " + seconds + ")); " +
                "  if (wasPlaying) { v.pause(); setTimeout(function() { v.play(); }, 50); } " +
                "  return v.currentTime; " +
                "} " +
                "return -1; " +
                "})();";
        webView.evaluateJavascript(js, result -> {
            try {
                double newTime = Double.parseDouble(result);
                if (newTime >= 0) {
                    handler.post(() -> showSeekFeedback(newTime));
                }
            } catch (Exception e) {
                // Ignore parse errors
            }
        });
    }

    private void seekTo(double seconds) {
        String js = "(function() { " +
                "var v = document.querySelector('video'); " +
                "if (v) { " +
                "  var wasPlaying = !v.paused; " +
                "  v.currentTime = " + seconds + "; " +
                "  if (wasPlaying) { v.pause(); setTimeout(function() { v.play(); }, 50); } " +
                "} " +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void showSeekFeedback(double seconds) {
        String timeStr = formatTime((int) seconds);
        showToast("⏱ " + timeStr);
    }

    // ==================== QUALITY MENU ====================

    private void showQualityMenu() {
        resetAutoHide();
        PopupMenu popup = new PopupMenu(context, btnQuality, Gravity.END);

        for (int i = 0; i < QUALITY_OPTIONS.length; i++) {
            String qualityCode = QUALITY_OPTIONS[i][0];
            String qualityLabel = QUALITY_OPTIONS[i][1];

            // Add checkmark for current quality
            String displayLabel = qualityCode.equals(currentQuality)
                    ? "✓ " + qualityLabel
                    : "   " + qualityLabel;

            popup.getMenu().add(0, i, i, displayLabel);
        }

        popup.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            if (index >= 0 && index < QUALITY_OPTIONS.length) {
                String quality = QUALITY_OPTIONS[index][0];
                String label = QUALITY_OPTIONS[index][1];
                setQuality(quality, label);
            }
            return true;
        });
        popup.show();
    }

    private void setQuality(String quality, String label) {
        currentQuality = quality;

        // YouTube's quality labels: tiny, small, medium, large, hd720, hd1080, hd1440,
        // hd2160
        String js = "(function() { " +
                "var player = document.querySelector('#movie_player'); " +
                "if (player && player.setPlaybackQualityRange) { " +
                "  player.setPlaybackQualityRange('" + quality + "'); " +
                "  console.log('Quality set to: " + quality + "'); " +
                "  return true; " +
                "} " +
                "return false; " +
                "})();";
        webView.evaluateJavascript(js, result -> {
            handler.post(() -> showToast("Quality: " + label));
        });
        Log.d(TAG, "Set quality: " + quality);
    }

    // ==================== SPEED MENU ====================

    private void showSpeedMenu() {
        resetAutoHide();
        PopupMenu popup = new PopupMenu(context, btnSpeed, Gravity.END);

        for (int i = 0; i < SPEED_VALUES.length; i++) {
            double speed = SPEED_VALUES[i];
            String label = SPEED_LABELS[i];

            // Add checkmark for current speed
            String displayLabel = (Math.abs(speed - currentSpeed) < 0.01)
                    ? "✓ " + label
                    : "   " + label;

            popup.getMenu().add(0, i, i, displayLabel);
        }

        popup.setOnMenuItemClickListener(item -> {
            int index = item.getItemId();
            if (index >= 0 && index < SPEED_VALUES.length) {
                setPlaybackSpeed(SPEED_VALUES[index], SPEED_LABELS[index]);
            }
            return true;
        });
        popup.show();
    }

    private void setPlaybackSpeed(double rate, String label) {
        currentSpeed = rate;

        String js = "(function() { " +
                "var v = document.querySelector('video'); " +
                "if (v) { " +
                "  v.playbackRate = " + rate + "; " +
                "  console.log('Playback rate set to: " + rate + "'); " +
                "  return true; " +
                "} " +
                "return false; " +
                "})();";
        webView.evaluateJavascript(js, result -> {
            handler.post(() -> showToast("Speed: " + label));
        });
        Log.d(TAG, "Set playback speed: " + rate);
    }

    // ==================== CAPTIONS & TRANSLATION ====================

    private void toggleCaptions() {
        resetAutoHide();
        // Show caption/translation menu instead of simple toggle
        showCaptionMenu();
    }

    private void showCaptionMenu() {
        PopupMenu popup = new PopupMenu(context, btnCaptions, Gravity.END);

        // Add menu items
        popup.getMenu().add(0, 0, 0, "⏸ Off");
        popup.getMenu().add(0, 1, 1, "🇸🇦 Arabic");
        popup.getMenu().add(0, 2, 2, "🇺🇸 English");
        popup.getMenu().add(0, 3, 3, "🌐 Auto-translate to Arabic");
        popup.getMenu().add(0, 4, 4, "🌐 Auto-translate to English");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0:
                    setCaptions(false, null, null);
                    break;
                case 1:
                    setCaptions(true, "ar", null);
                    break;
                case 2:
                    setCaptions(true, "en", null);
                    break;
                case 3:
                    setCaptions(true, null, "ar");
                    break;
                case 4:
                    setCaptions(true, null, "en");
                    break;
            }
            return true;
        });
        popup.show();
    }

    private void setCaptions(boolean enabled, String languageCode, String translateTo) {
        // Try multiple methods to enable captions on YouTube mobile
        String js = "(function() { " +
                "var result = {method: 'none', success: false, tracks: []}; " +
                "try { " +
                // Method 1: Try YouTube player API (desktop)
                "  var player = document.querySelector('#movie_player'); " +
                "  if (player && typeof player.getOption === 'function') { " +
                "    var tracks = player.getOption('captions', 'tracklist'); " +
                "    result.tracks = tracks || []; " +
                "    console.log('YT Player tracks:', JSON.stringify(tracks)); " +
                "    if (tracks && tracks.length > 0) { " +
                (enabled ? (translateTo != null ?
                // Enable with translation
                        "      var track = tracks.find(function(t) { return t.kind === 'asr'; }) || tracks[0]; " +
                                "      player.setOption('captions', 'track', {languageCode: track.languageCode}); " +
                                "      player.setOption('captions', 'translationLanguage', {languageCode: '"
                                + translateTo + "'}); " +
                                "      result = {method: 'yt-api', success: true, lang: 'translate-" + translateTo
                                + "'}; "
                        :
                        // Enable specific language
                        "      var track = tracks.find(function(t) { return t.languageCode === '" + languageCode
                                + "'; }) || tracks[0]; " +
                                "      player.setOption('captions', 'track', track); " +
                                "      result = {method: 'yt-api', success: true, lang: track.languageCode}; ")
                        :
                        // Disable
                        "      player.setOption('captions', 'track', {}); " +
                                "      result = {method: 'yt-api', success: true, lang: 'off'}; ")
                +
                // Force visibility by clicking CC button
                (enabled ? "      var ccBtn = document.querySelector('.ytp-subtitles-button'); " +
                        "      if (ccBtn && ccBtn.getAttribute('aria-pressed') !== 'true') { ccBtn.click(); } " +
                        "      if (player.loadModule) { player.loadModule('captions'); } "
                        : "      var ccBtn = document.querySelector('.ytp-subtitles-button'); " +
                                "      if (ccBtn && ccBtn.getAttribute('aria-pressed') === 'true') { ccBtn.click(); } ")
                +
                "    } " +
                "  } " +
                // Method 2: Try video textTracks API
                "  if (!result.success) { " +
                "    var video = document.querySelector('video'); " +
                "    if (video && video.textTracks) { " +
                "      console.log('Video textTracks count:', video.textTracks.length); " +
                "      for (var i = 0; i < video.textTracks.length; i++) { " +
                "        var track = video.textTracks[i]; " +
                "        console.log('Track ' + i + ': ' + track.language + ' (' + track.kind + ') mode=' + track.mode); "
                +
                "        result.tracks.push({lang: track.language, kind: track.kind, mode: track.mode}); " +
                (enabled ? "        if ('" + (languageCode != null ? languageCode : "en")
                        + "' === track.language || i === 0) { " +
                        "          track.mode = 'showing'; " +
                        "          result = {method: 'textTracks', success: true, lang: track.language}; " +
                        "        } "
                        : "        track.mode = 'hidden'; ")
                +
                "      } " +
                "      if (!result.success && video.textTracks.length > 0) { " +
                "        video.textTracks[0].mode = 'showing'; " +
                "        result = {method: 'textTracks', success: true, lang: 'default'}; " +
                "      } " +
                "    } " +
                "  } " +
                // Method 3: Try clicking CC button
                "  if (!result.success) { " +
                "    var ccBtn = document.querySelector('.ytp-subtitles-button, [class*=\"subtitle\"], [class*=\"caption\"], [aria-label*=\"caption\"], [aria-label*=\"subtitle\"]'); "
                +
                "    console.log('CC button found:', ccBtn != null); " +
                "    if (ccBtn) { " +
                "      var isCurrentlyOn = ccBtn.getAttribute('aria-pressed') === 'true' || ccBtn.classList.contains('ytp-subtitles-button-on'); "
                +
                "      if (" + enabled + " !== isCurrentlyOn) { " +
                "        ccBtn.click(); " +
                "        result = {method: 'cc-click', success: true, lang: " + enabled + " ? 'on' : 'off'}; " +
                "      } else { " +
                "        result = {method: 'cc-click', success: true, lang: isCurrentlyOn ? 'already-on' : 'already-off'}; "
                +
                "      } " +
                "    } " +
                "  } " +
                "} catch(e) { " +
                "  result.error = e.message; " +
                "  console.log('Caption error:', e.message); " +
                "} " +
                "console.log('Caption result:', JSON.stringify(result)); " +
                "return JSON.stringify(result); " +
                "})();";

        webView.evaluateJavascript(js, result -> {
            handler.post(() -> {
                Log.d(TAG, "Caption result: " + result);
                if (result != null && !result.equals("null")) {
                    try {
                        String json = result.replace("\\\"", "\"");
                        json = json.substring(1, json.length() - 1);

                        if (json.contains("\"success\":true")) {
                            if (!enabled) {
                                showToast("Captions: OFF");
                                btnCaptions.setAlpha(0.5f);
                            } else if (translateTo != null) {
                                showToast("Translating to: " + translateTo.toUpperCase());
                                btnCaptions.setAlpha(1.0f);
                            } else {
                                showToast("Captions: " + (languageCode != null ? languageCode.toUpperCase() : "ON"));
                                btnCaptions.setAlpha(1.0f);
                            }
                        } else {
                            showToast("No captions available");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing caption result", e);
                        showToast("Caption error");
                    }
                } else {
                    showToast("No captions available");
                }
            });
        });
        Log.d(TAG, "Set captions: enabled=" + enabled + ", lang=" + languageCode + ", translate=" + translateTo);
    }

    // ==================== SYNC SETTINGS ====================

    private void syncCurrentSettings() {
        // Get current playback rate from video
        String js = "(function() { " +
                "var v = document.querySelector('video'); " +
                "if (v) { " +
                "  return JSON.stringify({playbackRate: v.playbackRate}); " +
                "} " +
                "return null; " +
                "})();";
        webView.evaluateJavascript(js, result -> {
            if (result != null && !result.equals("null")) {
                try {
                    double rate = parseJsonDouble(result.replace("\\\"", "\""), "playbackRate");
                    if (rate > 0) {
                        currentSpeed = rate;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error syncing settings", e);
                }
            }
        });
    }

    // ==================== PROGRESS UPDATES ====================

    private void startProgressUpdates() {
        handler.postDelayed(progressUpdateRunnable, PROGRESS_UPDATE_INTERVAL_MS);
    }

    private void stopProgressUpdates() {
        handler.removeCallbacks(progressUpdateRunnable);
    }

    private void updateProgress() {
        if (!isVisible || isSeeking)
            return;

        // Get current time, duration, and playback state from video
        String js = "(function() { " +
                "var v = document.querySelector('video'); " +
                "if (v) { " +
                "  return JSON.stringify({" +
                "    currentTime: v.currentTime, " +
                "    duration: v.duration, " +
                "    paused: v.paused, " +
                "    playbackRate: v.playbackRate" +
                "  }); " +
                "} " +
                "return null; " +
                "})();";

        webView.evaluateJavascript(js, result -> {
            if (result != null && !result.equals("null")) {
                try {
                    // Parse JSON result
                    String json = result.replace("\\\"", "\"");
                    json = json.substring(1, json.length() - 1); // Remove outer quotes

                    // Simple parsing
                    double currentTime = parseJsonDouble(json, "currentTime");
                    double duration = parseJsonDouble(json, "duration");
                    boolean paused = json.contains("\"paused\":true");
                    double playbackRate = parseJsonDouble(json, "playbackRate");

                    videoDuration = duration;
                    if (playbackRate > 0) {
                        currentSpeed = playbackRate;
                    }

                    // Update UI
                    handler.post(() -> {
                        if (duration > 0) {
                            int progress = (int) ((currentTime / duration) * 100);
                            seekBar.setProgress(progress);
                            textCurrentTime.setText(formatTime((int) currentTime));
                            textDuration.setText(formatTime((int) duration));
                        }

                        // Sync play/pause state
                        if (isPlaying == paused) {
                            isPlaying = !paused;
                            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing video state", e);
                }
            }
        });

        // Schedule next update
        if (isVisible) {
            handler.postDelayed(progressUpdateRunnable, PROGRESS_UPDATE_INTERVAL_MS);
        }
    }

    // ==================== UTILITY METHODS ====================

    private double parseJsonDouble(String json, String key) {
        try {
            int keyIndex = json.indexOf("\"" + key + "\":");
            if (keyIndex >= 0) {
                int start = keyIndex + key.length() + 3;
                int end = json.indexOf(",", start);
                if (end < 0)
                    end = json.indexOf("}", start);
                if (end < 0)
                    end = json.length();
                return Double.parseDouble(json.substring(start, end).trim());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing " + key, e);
        }
        return 0;
    }

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    private void showToast(String message) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 100);
        toast.show();
    }

    public void cleanup() {
        handler.removeCallbacks(autoHideRunnable);
        handler.removeCallbacks(progressUpdateRunnable);
    }
}
