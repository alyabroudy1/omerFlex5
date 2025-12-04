package com.omarflex5.ui.home;

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.omarflex5.R;
import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.repository.MovieRepository;
import com.omarflex5.ui.controller.DefaultMovieClickController;
import com.omarflex5.ui.controller.MovieClickController;
import com.omarflex5.ui.home.adapter.CategoryAdapter;
import com.omarflex5.ui.home.adapter.MovieCardAdapter;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private HomeViewModel viewModel;
    private ImageView heroBackground;
    private TextView heroTitle;
    private TextView heroDescription;
    private RecyclerView recyclerCategories;
    private RecyclerView recyclerMovies;
    private CategoryAdapter categoryAdapter;
    private MovieCardAdapter movieCardAdapter;

    private PlayerView playerView;
    private android.webkit.WebView youtubeWebView;
    private ExoPlayer player;
    private Movie lastSelectedMovie;

    // Fullscreen toggle
    private View heroContainer;
    private View gradientOverlay;
    private ImageButton btnFullscreen;
    private boolean isFullscreen = false;

    // Mute toggle
    private ImageButton btnMute;
    private boolean isMuted = false;
    private ContentObserver volumeObserver;

    // Focus memory for each layer (to restore focus when navigating back)
    private View lastFocusedHero = null;
    private View lastFocusedCategory = null;
    private View lastFocusedMovie = null;

    // Track last focused movie position per category
    private java.util.Map<String, Integer> categoryToMoviePosition = new java.util.HashMap<>();
    private String currentCategoryId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide Action Bar if it exists
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Enable full-screen immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_home);

        initViews();
        initViewModel();
        setupAdapters();
        setupBackPressCallback();
        observeViewModel();
    }

    private void initViews() {
        heroBackground = findViewById(R.id.image_hero_background);
        heroTitle = findViewById(R.id.text_hero_title);
        heroDescription = findViewById(R.id.text_hero_description);
        recyclerCategories = findViewById(R.id.recycler_categories);
        recyclerMovies = findViewById(R.id.recycler_movies);
        playerView = findViewById(R.id.player_view);

        // Fullscreen components
        heroContainer = findViewById(R.id.hero_container);
        gradientOverlay = findViewById(R.id.gradient_overlay);
        youtubeWebView = findViewById(R.id.youtube_webview);
        btnFullscreen = findViewById(R.id.btn_fullscreen);
        btnFullscreen.setOnClickListener(v -> toggleFullscreen());

        // Setup WebView for YouTube
        android.webkit.WebSettings webSettings = youtubeWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false);
        webSettings.setAllowContentAccess(false);
        youtubeWebView.setWebChromeClient(new android.webkit.WebChromeClient());
        youtubeWebView.setBackgroundColor(0x00000000); // Transparent background

        // Mute button
        btnMute = findViewById(R.id.btn_mute);
        btnMute.setOnClickListener(v -> toggleMute());

        // Setup volume observer
        setupVolumeObserver();
    }

    private void setupVolumeObserver() {
        volumeObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                // When user changes volume, unmute if currently muted
                if (isMuted) {
                    isMuted = false;
                    if (player != null) {
                        player.setVolume(1f);
                    }
                    btnMute.setImageResource(R.drawable.ic_volume_on);
                }
            }
        };

        getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI, true, volumeObserver);
    }

    private void toggleMute() {
        isMuted = !isMuted;
        updateMuteButton();

        // Update ExoPlayer volume if active
        if (player != null) {
            player.setVolume(isMuted ? 0f : 1f);
        }

        // Update YouTube video mute state if WebView is visible
        if (youtubeWebView != null && youtubeWebView.getVisibility() == View.VISIBLE) {
            toggleYouTubeMute();
        }
    }

    private void toggleYouTubeMute() {
        String js = "(function() {" +
                "var video = document.querySelector('video');" +
                "if (video) {" +
                "  video.muted = " + isMuted + ";" +
                "  console.log('YouTube video muted: ' + " + isMuted + ");" +
                "}" +
                "})();";

        youtubeWebView.evaluateJavascript(js, null);
        Log.d(TAG, "YouTube mute toggled: " + isMuted);
    }

    private void updateMuteButton() {
        btnMute.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_on);
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;

        if (isFullscreen) {
            // Enter fullscreen
            recyclerCategories.setVisibility(View.GONE);
            recyclerMovies.setVisibility(View.GONE);
            heroTitle.setVisibility(View.GONE);
            heroDescription.setVisibility(View.GONE);
            gradientOverlay.setVisibility(View.GONE);

            // Expand hero container to full screen
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) heroContainer.getLayoutParams();
            params.weight = 10; // Take full weight
            heroContainer.setLayoutParams(params);

            // Show ExoPlayer controller
            playerView.setUseController(true);
            playerView.showController();

            // Hide our buttons to avoid overlap with ExoPlayer's controls
            btnFullscreen.setVisibility(View.GONE);
            btnMute.setVisibility(View.GONE);
        } else {
            // Exit fullscreen
            recyclerCategories.setVisibility(View.VISIBLE);
            recyclerMovies.setVisibility(View.VISIBLE);
            heroTitle.setVisibility(View.VISIBLE);
            heroDescription.setVisibility(View.VISIBLE);
            gradientOverlay.setVisibility(View.VISIBLE);

            // Restore hero container weight
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) heroContainer.getLayoutParams();
            params.weight = 6;
            heroContainer.setLayoutParams(params);

            // Hide ExoPlayer controller
            playerView.setUseController(false);
            playerView.hideController();

            // Show our buttons again
            btnFullscreen.setVisibility(View.VISIBLE);
            btnMute.setVisibility(View.VISIBLE);
        }
    }

    private void setupBackPressCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    toggleFullscreen();
                } else {
                    // Disable this callback and let the system handle it
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void initViewModel() {
        HomeViewModelFactory factory = new HomeViewModelFactory(MovieRepository.getInstance());
        viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
    }

    private void setupAdapters() {
        categoryAdapter = new CategoryAdapter();
        categoryAdapter.setListener(category -> {
            // Save current movie position for the old category before switching
            if (currentCategoryId != null && lastFocusedMovie != null) {
                int moviePosition = recyclerMovies.getChildLayoutPosition(lastFocusedMovie);
                if (moviePosition != RecyclerView.NO_POSITION) {
                    categoryToMoviePosition.put(currentCategoryId, moviePosition);
                }
            }

            // Update current category
            currentCategoryId = category.getId();

            // Tell ViewModel to load movies for this category
            viewModel.selectCategory(category);

            // Auto-select last focused movie for this category (done in observer after
            // movies load)
        });
        recyclerCategories.setAdapter(categoryAdapter);

        // Create click controller for movie actions
        MovieClickController clickController = new DefaultMovieClickController();

        movieCardAdapter = new MovieCardAdapter();
        movieCardAdapter.setListener(new MovieCardAdapter.OnMovieListener() {
            @Override
            public void onMovieSelected(Movie movie) {
                viewModel.selectMovie(movie);
            }

            @Override
            public void onMovieClicked(Movie movie) {
                // This is called on second click (when already selected)
                clickController.handleClick(HomeActivity.this, movie);
            }
        });
        recyclerMovies.setAdapter(movieCardAdapter);
    }

    /**
     * Override dispatchKeyEvent to intercept D-pad navigation BEFORE the focus
     * system.
     * Implements focus memory - remembers last focused element in each layer.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();

            if (focused != null) {
                int keyCode = event.getKeyCode();

                // Check if focus is in categories row
                if (isDescendantOf(focused, recyclerCategories)) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        // Save current focus before leaving
                        lastFocusedCategory = focused;
                        // Restore to last focused movie, or first if none
                        View target = (lastFocusedMovie != null && lastFocusedMovie.getParent() == recyclerMovies)
                                ? lastFocusedMovie
                                : (recyclerMovies.getChildCount() > 0 ? recyclerMovies.getChildAt(0) : null);
                        if (target != null) {
                            target.requestFocus();
                            return true;
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                        // Save current focus before leaving
                        lastFocusedCategory = focused;
                        // Restore to last focused hero button, or mute if none
                        View target = (lastFocusedHero != null) ? lastFocusedHero : btnMute;
                        target.requestFocus();
                        return true;
                    }
                }
                // Check if focus is in movies row
                else if (isDescendantOf(focused, recyclerMovies)) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                        // Save current focus before leaving
                        lastFocusedMovie = focused;
                        // Restore to last focused category, or first if none
                        View target = (lastFocusedCategory != null
                                && lastFocusedCategory.getParent() == recyclerCategories)
                                        ? lastFocusedCategory
                                        : (recyclerCategories.getChildCount() > 0 ? recyclerCategories.getChildAt(0)
                                                : null);
                        if (target != null) {
                            target.requestFocus();
                            return true;
                        }
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        // Block movement - stay in movies row
                        return true;
                    }
                }
                // Check if focus is on hero buttons
                else if (focused == btnMute || focused == btnFullscreen) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                        // Block movement - stay in hero
                        return true;
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        // Save current focus before leaving
                        lastFocusedHero = focused;
                        // Restore to last focused category, or first if none
                        View target = (lastFocusedCategory != null
                                && lastFocusedCategory.getParent() == recyclerCategories)
                                        ? lastFocusedCategory
                                        : (recyclerCategories.getChildCount() > 0 ? recyclerCategories.getChildAt(0)
                                                : null);
                        if (target != null) {
                            target.requestFocus();
                            return true;
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Check if a view is a descendant of a parent view.
     */
    private boolean isDescendantOf(View child, View parent) {
        if (child == null || parent == null)
            return false;
        if (child == parent)
            return true;

        android.view.ViewParent viewParent = child.getParent();
        while (viewParent != null) {
            if (viewParent == parent) {
                return true;
            }
            viewParent = viewParent.getParent();
        }
        return false;
    }

    /**
     * Focus navigation is now handled by adapter-level key listeners on each item.
     * - CategoryAdapter.OnNavigationListener handles UP/DOWN from categories
     * - MovieCardAdapter.OnNavigationListener handles UP from movies
     */
    private void setupLayerFocusNavigation() {
        // Navigation is handled in adapters via OnNavigationListener interfaces
    }

    private void observeViewModel() {
        viewModel.getCategories().observe(this, categories -> {
            categoryAdapter.setCategories(categories);
        });

        viewModel.getMovies().observe(this, movies -> {
            movieCardAdapter.setMovies(movies);

            if (!movies.isEmpty()) {
                recyclerMovies.scrollToPosition(0);

                // Restore selected movie if it exists in this category
                if (lastSelectedMovie != null) {
                    for (int i = 0; i < movies.size(); i++) {
                        if (movies.get(i).getId().equals(lastSelectedMovie.getId())) {
                            // Found the selected movie in this category - restore selection
                            final int position = i;
                            recyclerMovies.post(() -> movieCardAdapter.selectMovie(position));
                            break;
                        }
                    }
                }
            }
        });

        viewModel.getSelectedMovie().observe(this, movie -> {
            if (movie != null) {
                updateHeroView(movie);
                lastSelectedMovie = movie;
            }
        });

        // Observe trailer URL - this is fetched asynchronously when a movie is selected
        viewModel.getTrailerUrl().observe(this, trailerUrl -> {
            if (trailerUrl != null && !trailerUrl.isEmpty()) {
                playTrailer(trailerUrl);
            }
        });

        viewModel.getError().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String error) {
                Toast.makeText(HomeActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateHeroView(Movie movie) {
        if (movie == null)
            return;

        // Save current focus before updating hero - we'll restore it after
        View currentFocus = getCurrentFocus();

        // TEMPORARILY disable focus on ALL hero elements to prevent focus stealing
        btnMute.setFocusable(false);
        btnFullscreen.setFocusable(false);
        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);

        heroTitle.setText(movie.getTitle());
        // Limit description to 300 characters
        String description = movie.getDescription();
        if (description != null && description.length() > 300) {
            description = description.substring(0, 300) + "...";
        }
        heroDescription.setText(description);

        // Load image immediately as placeholder
        Glide.with(this)
                .load(movie.getBackgroundUrl())
                .centerCrop()
                .into(heroBackground);

        heroBackground.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.GONE);

        // Trailer will be played when trailerUrl LiveData is updated
        // (fetched asynchronously from TMDB via ViewModel)

        // Restore focus and re-enable hero buttons after a delay
        if (currentFocus != null) {
            currentFocus.postDelayed(() -> {
                currentFocus.requestFocus();
                // Re-enable hero buttons after focus is restored
                btnMute.setFocusable(true);
                btnFullscreen.setFocusable(true);
            }, 150);
        } else {
            // No current focus, just re-enable buttons
            btnMute.postDelayed(() -> {
                btnMute.setFocusable(true);
                btnFullscreen.setFocusable(true);
            }, 150);
        }
    }

    private boolean isViewInRecyclerView(View view, RecyclerView recyclerView) {
        View parent = (View) view.getParent();
        while (parent != null) {
            if (parent == recyclerView) {
                return true;
            }
            if (parent.getParent() instanceof View) {
                parent = (View) parent.getParent();
            } else {
                break;
            }
        }
        return false;
    }

    private void playTrailer(String videoUrl) {
        releasePlayer();
        if (videoUrl != null && !videoUrl.isEmpty()) {
            // Check if it's a YouTube URL
            if (isYouTubeUrl(videoUrl)) {
                // Extract YouTube video ID and play in WebView
                String videoId = extractYouTubeId(videoUrl);
                if (videoId != null) {
                    playYouTubeInWebView(videoId);
                } else {
                    // Fallback to showing image only
                    heroBackground.setVisibility(View.VISIBLE);
                    playerView.setVisibility(View.GONE);
                    youtubeWebView.setVisibility(View.GONE);
                }
            } else {
                // Direct URL, play in ExoPlayer
                playDirectUrl(videoUrl);
            }
        }
    }

    private boolean isYouTubeUrl(String url) {
        return url.contains("youtube.com") || url.contains("youtu.be");
    }

    private String extractYouTubeId(String url) {
        // Extract video ID from various YouTube URL formats
        String[] patterns = {
                "(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})",
                "youtube\\.com/embed/([a-zA-Z0-9_-]{11})",
                "youtube\\.com/v/([a-zA-Z0-9_-]{11})"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern compiledPattern = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher matcher = compiledPattern.matcher(url);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private void playYouTubeInWebView(String videoId) {
        Log.d(TAG, "Playing YouTube video: " + videoId);

        // Load YouTube watch URL
        String youtubeUrl = "https://www.youtube.com/watch?v=" + videoId;
        youtubeWebView.loadUrl(youtubeUrl);

        // Show WebView, hide others
        runOnUiThread(() -> {
            heroBackground.setVisibility(View.GONE);
            playerView.setVisibility(View.GONE);
            youtubeWebView.setVisibility(View.VISIBLE);
        });

        // Apply fullscreen CSS after page loads
        youtubeWebView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                Log.d(TAG, "YouTube page loaded, applying fullscreen");
                applyYouTubeFullscreen(2000);
                applyYouTubeFullscreen(3000);
            }
        });
    }

    private void applyYouTubeFullscreen(int delayMs) {
        youtubeWebView.postDelayed(() -> {
            String js = "(function() {" +
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

            // Auto-play video (muted)
                    "var video = document.querySelector('video');" +
                    "if (video) {" +
                    "  video.muted = " + (isMuted ? "true" : "false") + ";" +
                    "  video.play();" +
                    "}" +
                    "})();";

            youtubeWebView.evaluateJavascript(js, null);
        }, delayMs);
    }

    private void playDirectUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return;
        }

        // Hide WebView, show ExoPlayer
        youtubeWebView.setVisibility(View.GONE);
        youtubeWebView.loadUrl("about:blank");

        // Disable focus on playerView BEFORE attaching player
        playerView.setFocusable(false);
        playerView.setFocusableInTouchMode(false);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem mediaItem = MediaItem.fromUri(videoUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);

        // Apply current mute state
        player.setVolume(isMuted ? 0f : 1f);

        // Listen for when video is ready to render to hide the image
        player.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                runOnUiThread(() -> {
                    heroBackground.setVisibility(View.GONE);
                    playerView.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    // Loop for background effect
                    player.seekTo(0);
                    player.play();
                }
            }
        });
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        // Clear WebView
        if (youtubeWebView != null) {
            youtubeWebView.loadUrl("about:blank");
            youtubeWebView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Resume playing the trailer if we have one cached
        String trailerUrl = viewModel.getTrailerUrl().getValue();
        if (trailerUrl != null && !trailerUrl.isEmpty()) {
            playTrailer(trailerUrl);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (volumeObserver != null) {
            getContentResolver().unregisterContentObserver(volumeObserver);
        }
    }
}
