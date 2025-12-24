package com.omarflex5.ui.home;

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

public class HomeActivity extends com.omarflex5.ui.base.BaseActivity {

    private static final String TAG = "HomeActivity";
    private HomeViewModel viewModel;
    private ImageView heroBackground;
    private TextView heroTitle;
    private TextView heroDescription;
    private com.omarflex5.ui.view.NavigableRecyclerView recyclerCategories;
    private com.omarflex5.ui.view.NavigableRecyclerView recyclerMovies;
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

    // YouTube controls overlay
    private YouTubeControlsOverlay youtubeControlsOverlay;

    // Loading and Error state views
    private View loadingContainer;
    private View errorContainer;
    private TextView errorTitle;
    private TextView errorMessage;
    private Button btnRetry;

    // New TV Focus Navigation Controller
    private com.omarflex5.ui.navigation.TvFocusController focusController;

    // Track current category for data loading
    private String currentCategoryId = null;

    // Navigation flag to prevent pagination during active navigation
    private boolean isNavigating = false;

    private final android.os.Handler navigationHandler = new android.os.Handler();

    // Pagination state tracking
    private boolean isLoadingMore = false;
    private int previousMovieCount = 0;

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

        // Initialize OmarFlex Receiver Service if this is a TV
        if (com.omarflex5.util.CastUtils.isTv(this)) {
            android.content.Intent serviceIntent = new android.content.Intent(this,
                    com.omarflex5.cast.receiver.ReceiverService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }

        initViews();
        initViewModel();
        setupAdapters();
        setupNavigation();
        setupBackPressCallback();
        observeViewModel();
        // TEMP: Start OldAkwam Test
        // startAkwamTest();

        checkForUpdates();
    }

    public void startAkwamTest() {
        android.content.Intent intent = new android.content.Intent(this,
                com.omarflex5.ui.test.ServerTestActivity.class);
        intent.putExtra("EXTRA_AUTO_TEST", true);
        intent.putExtra("EXTRA_QUERY", "ratched"); // Default query
        intent.putExtra("EXTRA_SERVER_NAME", "arabseed"); // Target generic or oldakwam
        startActivity(intent);
    }

    private void checkForUpdates() {
        Log.d(TAG, "checkForUpdates: Starting update check...");
        com.omarflex5.util.UpdateManager.getInstance().checkForUpdate(this,
                new com.omarflex5.util.UpdateManager.UpdateCheckCallback() {
                    @Override
                    public void onUpdateAvailable(com.omarflex5.data.model.UpdateInfo updateInfo) {
                        Log.d(TAG, "checkForUpdates: Update available (" + updateInfo.getVersionName()
                                + "). Showing dialog.");
                        try {
                            com.omarflex5.ui.dialog.UpdateDialog dialog = com.omarflex5.ui.dialog.UpdateDialog
                                    .newInstance(updateInfo);
                            dialog.show(getSupportFragmentManager(), "UpdateDialog");
                        } catch (Exception e) {
                            Log.e(TAG, "checkForUpdates: Error showing dialog", e);
                        }
                    }

                    @Override
                    public void onNoUpdate() {
                        Log.d(TAG, "checkForUpdates: No update available callback received.");
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "checkForUpdates: Update check failed with error: " + error);
                    }
                });
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
        // Setup WebView for YouTube
        try {
            android.webkit.WebSettings webSettings = youtubeWebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setMediaPlaybackRequiresUserGesture(false);
            webSettings.setDomStorageEnabled(true);
            webSettings.setAllowFileAccess(false);
            webSettings.setAllowContentAccess(false);
            youtubeWebView.setWebChromeClient(new android.webkit.WebChromeClient());
            youtubeWebView.setBackgroundColor(0x00000000); // Transparent background
        } catch (Exception e) {
            Log.e(TAG, "Error initializing WebView: ", e);
            // Hide WebView if it fails to initialize (e.g. on emulators without camera)
            youtubeWebView.setVisibility(View.GONE);
        }

        // Mute button
        btnMute = findViewById(R.id.btn_mute);
        btnMute.setOnClickListener(v -> toggleMute());

        // Setup volume observer
        setupVolumeObserver();

        // Setup YouTube controls overlay
        View overlayRoot = findViewById(R.id.youtube_controls_root);
        youtubeControlsOverlay = new YouTubeControlsOverlay(overlayRoot, youtubeWebView);
        youtubeControlsOverlay.setOnExitFullscreenListener(() -> toggleFullscreen());

        // Loading and Error state views
        loadingContainer = findViewById(R.id.loading_container);
        errorContainer = findViewById(R.id.error_container);
        errorTitle = findViewById(R.id.text_error_title);
        errorMessage = findViewById(R.id.text_error_message);
        btnRetry = findViewById(R.id.btn_retry);
        btnRetry.setOnClickListener(v -> {
            viewModel.retry();
        });
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
            // Exit fullscreen - hide the overlay first
            if (youtubeControlsOverlay != null) {
                youtubeControlsOverlay.hide();
            }

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

            // Restore focus to categories row using TvFocusController
            recyclerCategories.postDelayed(() -> {
                focusController.setCurrentLayer("categories");
            }, 100);
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
        HomeViewModelFactory factory = new HomeViewModelFactory(getApplication());
        viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
    }

    private void setupAdapters() {
        // CRITICAL: Set horizontal layout managers explicitly (XML orientation is
        // ignored)
        recyclerCategories.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerMovies.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        categoryAdapter = new CategoryAdapter();
        categoryAdapter.setListener(new CategoryAdapter.OnCategoryListener() {
            @Override
            public void onCategorySelected(Category category) {
                // Lock focus to prevent stealing during category switch
                if (focusController != null) {
                    focusController.lockFocus();
                    focusController.getCurrentLayer().saveFocusState();
                }

                // Update current category
                currentCategoryId = category.getId();

                // Tell ViewModel to load movies for this category
                viewModel.selectCategory(category);

                // Reset movie scroll to top for new category
                recyclerMovies.scrollToPosition(0);

                // Auto-select last focused movie for this category (done in observer after
                // movies load)
            }

            @Override
            public void onSearchSubmitted(String query) {
                Log.d(TAG, "Search submitted: " + query);
                android.content.Intent intent = new android.content.Intent(HomeActivity.this,
                        com.omarflex5.ui.search.SearchActivity.class);
                intent.putExtra(com.omarflex5.ui.search.SearchActivity.EXTRA_QUERY, query);
                startActivity(intent);
            }
        });
        recyclerCategories.setAdapter(categoryAdapter);

        // Create click controller for movie actions
        MovieClickController clickController = new DefaultMovieClickController();

        movieCardAdapter = new MovieCardAdapter();
        movieCardAdapter.setListener(new MovieCardAdapter.OnMovieListener() {
            @Override
            public void onMovieSelected(Movie movie) {
                // Lock focus on movies layer to prevent jumping to categories/hero when hero
                // updates
                if (focusController != null) {
                    focusController.lockFocus();
                }
                viewModel.selectMovie(movie);
            }

            @Override
            public void onMovieClicked(Movie movie) {
                // This is called on second click (when already selected)
                clickController.handleClick(HomeActivity.this, movie);
            }
        });
        recyclerMovies.setAdapter(movieCardAdapter);

        // Automatic pagination removed - now using manual "Load More" button
        // This prevents DiffUtil updates during navigation and eliminates focus jumping
        movieCardAdapter.setLoadMoreListener(() -> {
            if (!isNavigating) {
                // Track state before loading
                isLoadingMore = true;
                // getItemCount includes the button, so subtract 1 if needed, or just rely on
                // movies.size() from adapter if exposed
                // But easier to use current list size from viewModel if possible, or just count
                // from adapter.
                // Adapter.getItemCount() = movies.size() + 1.
                // So movies count = getItemCount() - 1 (if load more shown).
                previousMovieCount = movieCardAdapter.getItemCount() > 0 ? movieCardAdapter.getItemCount() - 1 : 0;

                viewModel.loadNextPage();
            }
        });

        // Handle focus restoration after Load More
        movieCardAdapter.setLoadMoreFocusCallback(position -> {
            if (recyclerMovies != null) {
                // Scroll to position first to ensure it's laid out
                recyclerMovies.scrollToPosition(position);

                // Then post a request to focus specifically on this item
                recyclerMovies.post(() -> {
                    RecyclerView.ViewHolder holder = recyclerMovies.findViewHolderForAdapterPosition(position);
                    if (holder != null && holder.itemView != null) {
                        holder.itemView.requestFocus();
                    }
                });
            }
        });
    }

    /**
     * Setup the TV Focus Navigation Controller with layer-based navigation.
     * This replaces the old delegate-based system with a cleaner, centralized
     * approach.
     */
    private void setupNavigation() {
        // Create controller (RTL = true for Arabic)
        focusController = new com.omarflex5.ui.navigation.TvFocusController(true);
        focusController.setDebugEnabled(true); // Enable for debugging

        // Hero layer: mute/fullscreen buttons
        // UP = blocked, DOWN = categories, LEFT/RIGHT = between buttons
        focusController.registerLayer(
                new com.omarflex5.ui.navigation.ButtonRowLayer(
                        "hero", btnMute, btnFullscreen, "categories"));

        // Categories layer: horizontal RecyclerView
        // UP = hero, DOWN = movies, LEFT/RIGHT = scroll
        focusController.registerLayer(
                new com.omarflex5.ui.navigation.RecyclerLayer(
                        "categories", recyclerCategories, "hero", "movies"));

        // Movies layer: horizontal RecyclerView
        // UP = categories, DOWN = blocked, LEFT/RIGHT = scroll
        focusController.registerLayer(
                new com.omarflex5.ui.navigation.RecyclerLayer(
                        "movies", recyclerMovies, "categories", null));

        // Set initial layer to categories
        focusController.setCurrentLayer("categories");
    }

    /**
     * Override dispatchKeyEvent to intercept D-pad navigation.
     * Delegates to TvFocusController for clean, layer-based navigation.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int keyCode = event.getKeyCode();

        // Handle YouTube fullscreen controls first
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            if (isFullscreen && youtubeWebView != null && youtubeWebView.getVisibility() == View.VISIBLE) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    if (youtubeControlsOverlay != null && youtubeControlsOverlay.isVisible()) {
                        youtubeControlsOverlay.hide();
                    } else {
                        toggleFullscreen();
                    }
                    return true;
                }

                if (youtubeControlsOverlay != null && !youtubeControlsOverlay.isVisible()) {
                    youtubeControlsOverlay.show();
                    return true;
                }

                if (youtubeControlsOverlay != null && youtubeControlsOverlay.isVisible()) {
                    return super.dispatchKeyEvent(event);
                }
            }
        }

        // Set navigating flag on D-pad events to prevent pagination during navigation
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                isNavigating = true;
            }
        }

        // Clear navigating flag on D-pad UP events with delay
        if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                navigationHandler.removeCallbacksAndMessages(null);
                navigationHandler.postDelayed(() -> isNavigating = false, 150);
            }
        }

        // Delegate D-pad navigation to TvFocusController
        if (focusController != null) {
            View currentFocus = getCurrentFocus();
            if (focusController.handleKeyEvent(event, currentFocus)) {
                return true;
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
        // Observe UI state for loading/error handling
        viewModel.getUiState().observe(this, uiState -> {
            switch (uiState.getState()) {
                case LOADING:
                    showLoading();
                    break;
                case SUCCESS:
                    showContent();
                    break;
                case ERROR:
                    showError(uiState.getErrorMessage(), uiState.getErrorType());
                    break;
            }
        });

        viewModel.getCategories().observe(this, categories -> {
            categoryAdapter.setCategories(categories);
        });

        viewModel.getMovies().observe(this, movies -> {
            // Check for "No More Items" scenario
            if (isLoadingMore) {
                isLoadingMore = false;
                if (movies != null && movies.size() <= previousMovieCount) {
                    android.widget.Toast.makeText(this, "No more items to load", android.widget.Toast.LENGTH_SHORT)
                            .show();
                    // Early exit is crucial here!
                    // If we update the adapter with the same list, it might cause a re-layout or
                    // focus search
                    // that resets focus. By returning, we ensure NOTHING changes in the UI.
                    return;
                }
            }

            movieCardAdapter.setMovies(movies);

            if (!movies.isEmpty()) {
                // Remove unconditional scrollToPosition(0) which breaks pagination

                // Restore selected movie if it exists in this category
                if (lastSelectedMovie != null) {
                    for (int i = 0; i < movies.size(); i++) {
                        if (movies.get(i).getId().equals(lastSelectedMovie.getId())) {
                            // Found the selected movie in this category - restore selection
                            final int position = i;
                            recyclerMovies.post(() -> movieCardAdapter.selectMovie(position));
                            break;
                            // Do NOT break focus here, let the adapter handle diff updates
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
            } else {
                // Stop playing any previous trailer
                releasePlayer();
                // Ensure background is visible
                heroBackground.setVisibility(View.VISIBLE);
                if (playerView != null)
                    playerView.setVisibility(View.GONE);
                if (youtubeWebView != null)
                    youtubeWebView.setVisibility(View.GONE);
            }
        });

        viewModel.getError().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String error) {
                // Error is now handled by UiState observer, just log here
                Log.w(TAG, "Error: " + error);
            }
        });
    }

    private void showLoading() {
        loadingContainer.setVisibility(View.VISIBLE);
        errorContainer.setVisibility(View.GONE);
        heroContainer.setVisibility(View.GONE);
        recyclerCategories.setVisibility(View.GONE);
        recyclerMovies.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
        heroContainer.setVisibility(View.VISIBLE);
        recyclerCategories.setVisibility(View.VISIBLE);
        recyclerMovies.setVisibility(View.VISIBLE);

        // Set focus to first category when content loads
        recyclerCategories.postDelayed(() -> {
            if (recyclerCategories.getChildCount() > 0) {
                recyclerCategories.getChildAt(0).requestFocus();
            }
        }, 100);
    }

    private void showError(String message, UiState.ErrorType errorType) {
        loadingContainer.setVisibility(View.GONE);
        errorContainer.setVisibility(View.VISIBLE);
        heroContainer.setVisibility(View.GONE);
        recyclerCategories.setVisibility(View.GONE);
        recyclerMovies.setVisibility(View.GONE);

        // Update error message based on type
        if (errorType == UiState.ErrorType.NETWORK) {
            errorTitle.setText("لا يوجد اتصال بالإنترنت");
            errorMessage.setText("تحقق من اتصالك بالإنترنت وحاول مرة أخرى");
        } else {
            errorTitle.setText("حدث خطأ");
            errorMessage.setText(message != null ? message : "يرجى المحاولة مرة أخرى");
        }

        // Focus retry button for D-pad navigation
        btnRetry.requestFocus();
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

        // Load YouTube with caption parameters:
        // cc_load_policy=1 forces captions on
        // cc_lang_pref=ar prefers Arabic captions/translation
        // hl=ar sets interface language to Arabic
        String youtubeUrl = "https://www.youtube.com/watch?v=" + videoId
                + "&cc_load_policy=1&cc_lang_pref=ar&hl=ar";
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
                // Try to enable captions multiple times - YouTube loads caption data slowly
                applyYouTubeFullscreen(5000);
                applyYouTubeFullscreen(7000);
                applyYouTubeFullscreen(10000);
                applyYouTubeFullscreen(15000);
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
            // Hide ALL mobile YouTube UI elements
                    "  .mobile-topbar-header, " +
                    "  .player-controls-top, " +
                    "  .watch-below-the-player, " +
                    "  ytm-app-header-layout header, " +
                    "  .ytm-autonav-bar, " +
                    "  .related-chips-slot-wrapper, " +
                    "  .slim-video-metadata-header, " +
                    "  .slim-video-information-renderer, " +
                    "  ytm-item-section-renderer, " +
                    "  ytm-comments-entry-point-header-renderer, " +
                    "  ytm-comments-simplebox-renderer, " +
                    "  .ytp-chrome-top, " +
                    "  .ytp-chrome-bottom { display: none !important; visibility: hidden !important; }" +
                    "  ytm-watch, ytm-app { background: #000 !important; padding: 0 !important; margin: 0 !important; }"
                    +
            // Force ALL containers to fill screen
                    "  #player-container-id, " +
                    "  .player-container, " +
                    "  #player, " +
                    "  ytm-player, " +
                    "  #movie_player, " +
                    "  .html5-video-container, " +
                    "  .html5-video-player, " +
                    "  .html5-main-video { " +
                    "    position: fixed !important;" +
                    "    top: 0 !important;" +
                    "    left: 0 !important;" +
                    "    right: 0 !important;" +
                    "    bottom: 0 !important;" +
                    "    width: 100vw !important;" +
                    "    height: 100vh !important;" +
                    "    max-width: 100vw !important;" +
                    "    max-height: 100vh !important;" +
                    "    margin: 0 !important;" +
                    "    padding: 0 !important;" +
                    "    z-index: 9999 !important;" +
                    "  }" +
            // Force video element to fill entire screen
                    "  video { " +
                    "    position: fixed !important;" +
                    "    top: 0 !important;" +
                    "    left: 0 !important;" +
                    "    width: 100vw !important;" +
                    "    height: 100vh !important;" +
                    "    object-fit: cover !important;" +
                    "    z-index: 9998 !important;" +
                    "  }" +
            // ENSURE CAPTION CONTAINERS ARE VISIBLE
                    "  .ytp-caption-window-container, " +
                    "  .caption-window, " +
                    "  .captions-text, " +
                    "  .ytp-caption-segment { " +
                    "    display: block !important;" +
                    "    visibility: visible !important;" +
                    "    opacity: 1 !important;" +
                    "    z-index: 99999 !important;" +
                    "  }" +
                    "';" +
                    "document.head.appendChild(style);" +

            // Auto-play video (muted)
                    "var video = document.querySelector('video');" +
                    "if (video) {" +
                    "  video.muted = " + (isMuted ? "true" : "false") + ";" +
                    "  video.play();" +
                    "}" +

            // Enable Arabic captions by default using YouTube player API
                    "try {" +
                    "  var player = document.querySelector('#movie_player');" +
                    "  if (player && player.getOption) {" +
            // Get available caption tracks
                    "    var tracks = player.getOption('captions', 'tracklist');" +
                    "    console.log('Available tracks:', JSON.stringify(tracks));" +
                    "    if (tracks && tracks.length > 0) {" +
            // Look for Arabic track first
                    "      var arabicTrack = tracks.find(function(t) { return t.languageCode === 'ar'; });" +
                    "      if (arabicTrack) {" +
                    "        player.setOption('captions', 'track', arabicTrack);" +
                    "        console.log('Set Arabic captions');" +
                    "      } else {" +
            // If no Arabic, try to use auto-translate to Arabic
                    "        var autoTrack = tracks.find(function(t) { return t.kind === 'asr' || t.is_default; }) || tracks[0];"
                    +
                    "        if (autoTrack) {" +
                    "          player.setOption('captions', 'track', {languageCode: autoTrack.languageCode});" +
                    "          player.setOption('captions', 'translationLanguage', {languageCode: 'ar', languageName: 'Arabic'});"
                    +
                    "          console.log('Set captions with Arabic translation');" +
                    "        }" +
                    "      }" +
            // IMPORTANT: Force captions to be visible
                    "      if (player.loadModule) { player.loadModule('captions'); }" +
                    "      if (player.toggleSubtitles) { " +
                    "        if (!player.isSubtitlesOn || !player.isSubtitlesOn()) { player.toggleSubtitles(); }" +
                    "      }" +
            // Alternative: directly click the CC button to ensure visibility
                    "      var ccBtn = document.querySelector('.ytp-subtitles-button');" +
                    "      if (ccBtn && ccBtn.getAttribute('aria-pressed') !== 'true') { ccBtn.click(); }" +
                    "      console.log('Captions visibility enabled');" +
                    "    }" +
                    "  }" +
                    "} catch(e) { console.log('Caption setup error: ' + e.message); }" +

            // AD SKIPPER - Auto-skip YouTube ads
                    "if (!window.adSkipperRunning) {" +
                    "  window.adSkipperRunning = true;" +
                    "  setInterval(function() {" +
                    "    try {" +
            // Method 1: Click skip button if available
                    "      var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, [class*=\"skip\"][class*=\"button\"]');"
                    +
                    "      if (skipBtn && skipBtn.style.display !== 'none') {" +
                    "        skipBtn.click();" +
                    "        console.log('Ad skipped via button');" +
                    "      }" +
            // Method 2: Click 'Skip Ad' text links
                    "      var skipLinks = document.querySelectorAll('.ytp-ad-skip-button-container button, .ytp-ad-skip-button-slot button');"
                    +
                    "      skipLinks.forEach(function(btn) { btn.click(); });" +
            // Method 3: Fast-forward through unskippable ads
                    "      var player = document.querySelector('#movie_player');" +
                    "      if (player && player.classList.contains('ad-showing')) {" +
                    "        var video = document.querySelector('video');" +
                    "        if (video && video.duration && video.duration > 0 && video.duration < 120) {" +
                    "          video.currentTime = video.duration;" +
                    "          console.log('Ad fast-forwarded');" +
                    "        }" +
                    "      }" +
            // Method 4: Hide ad overlays
                    "      var adOverlays = document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-text-overlay, .video-ads');"
                    +
                    "      adOverlays.forEach(function(ad) { ad.style.display = 'none'; });" +
                    "    } catch(e) {}" +
                    "  }, 500);" +
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
