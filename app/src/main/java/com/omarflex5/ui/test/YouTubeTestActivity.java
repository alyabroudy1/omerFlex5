package com.omarflex5.ui.test;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.omarflex5.R;

/**
 * Test activity - Force YouTube iframe to fill entire screen
 */
public class YouTubeTestActivity extends AppCompatActivity {

    private static final String TAG = "YouTubeTestActivity";
    private static final String YOUTUBE_URL = "https://www.youtube.com/watch?v=5AwtptT8X8k";

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_test);

        webView = findViewById(R.id.test_webview);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        enableImmersiveMode();
        setupWebView();
        loadYouTube();

        showToast("Loading YouTube in fullscreen mode...");
    }

    private void enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(
                    WindowInsets.Type.statusBars() |
                            WindowInsets.Type.navigationBars());
            getWindow().getInsetsController().setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        Log.d(TAG, "Immersive mode enabled");
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "JS: " + consoleMessage.message());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page loaded: " + url);

                // Force iframe to fullscreen after page loads
                forcePlayerFullscreen(2000);
                forcePlayerFullscreen(3000);
                forcePlayerFullscreen(4000);
            }
        });

        webView.setBackgroundColor(0x00000000);
    }

    private void loadYouTube() {
        Log.d(TAG, "Loading YouTube: " + YOUTUBE_URL);
        webView.loadUrl(YOUTUBE_URL);
    }

    private void forcePlayerFullscreen(int delayMs) {
        webView.postDelayed(() -> {
            Log.d(TAG, "Forcing player fullscreen after " + delayMs + "ms");

            String js = "(function() {" +
                    "console.log('=== FORCING MOBILE YOUTUBE FULLSCREEN ===');" +

            // Force page and body to be fullscreen
                    "var style = document.createElement('style');" +
                    "style.textContent = '" +
            // Page-level fullscreen
                    "  html, body { " +
                    "    margin: 0 !important;" +
                    "    padding: 0 !important;" +
                    "    width: 100vw !important;" +
                    "    height: 100vh !important;" +
                    "    overflow: hidden !important;" +
                    "    background: #000 !important;" +
                    "  }" +
            // Hide all mobile YouTube UI
                    "  .mobile-topbar-header { display: none !important; }" +
                    "  .player-controls-top { display: none !important; }" +
                    "  .watch-below-the-player { display: none !important; }" +
                    "  ytm-watch { background: #000 !important; }" +
            // Force player container fullscreen
                    "  #player-container-id, " +
                    "  .player-container, " +
                    "  #movie_player, " +
                    "  .html5-video-container, " +
                    "  .html5-video-player { " +
                    "    position: fixed !important;" +
                    "    top: 0 !important;" +
                    "    left: 0 !important;" +
                    "    width: 100vw !important;" +
                    "    height: 100vh !important;" +
                    "    z-index: 9999 !important;" +
                    "  }" +
            // Force video element fullscreen
                    "  video { " +
                    "    width: 100vw !important;" +
                    "    height: 100vh !important;" +
                    "    object-fit: cover !important;" +
                    "  }" +
                    "';" +
                    "document.head.appendChild(style);" +
                    "console.log('Applied mobile fullscreen CSS');" +

            // Auto-play video
                    "var video = document.querySelector('video');" +
                    "if (video) {" +
                    "  video.muted = true;" +
                    "  video.play().then(function() {" +
                    "    console.log('✅ Video playing');" +
                    "  }).catch(function(e) {" +
                    "    console.log('❌ Play error:', e);" +
                    "  });" +
                    "  console.log('Video found and playing');" +
                    "} else {" +
                    "  console.log('❌ Video not found yet');" +
                    "}" +

                    "console.log('=== FULLSCREEN APPLIED ===');" +
                    "})();";

            webView.evaluateJavascript(js, result -> {
                Log.d(TAG, "Fullscreen JS executed");
            });
        }, delayMs);
    }

    private void showToast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
    }
}
