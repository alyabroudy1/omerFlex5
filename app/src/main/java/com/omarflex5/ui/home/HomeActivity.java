package com.omarflex5.ui.home;

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
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
import com.omarflex5.data.source.DummyDataProvider;
import com.omarflex5.ui.controller.DefaultMovieClickController;
import com.omarflex5.ui.controller.MovieClickController;
import com.omarflex5.ui.home.adapter.CategoryAdapter;
import com.omarflex5.ui.home.adapter.MovieCardAdapter;

import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private HomeViewModel viewModel;
    private ImageView heroBackground;
    private TextView heroTitle;
    private TextView heroDescription;
    private RecyclerView recyclerCategories;
    private RecyclerView recyclerMovies;
    private CategoryAdapter categoryAdapter;
    private MovieCardAdapter movieCardAdapter;

    private PlayerView playerView;
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
        btnFullscreen = findViewById(R.id.btn_fullscreen);
        btnFullscreen.setOnClickListener(v -> toggleFullscreen());

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

        if (player != null) {
            player.setVolume(isMuted ? 0f : 1f);
        }

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
        DummyDataProvider dataProvider = new DummyDataProvider();
        MovieRepository repository = MovieRepository.getInstance(dataProvider);
        HomeViewModelFactory factory = new HomeViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(HomeViewModel.class);
    }

    private void setupAdapters() {
        categoryAdapter = new CategoryAdapter();
        categoryAdapter.setListener(category -> viewModel.selectCategory(category));
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
                if (movie.equals(viewModel.getSelectedMovie().getValue())) {
                    // Movie is already selected, handle the click action
                    clickController.handleClick(HomeActivity.this, movie);
                } else {
                    viewModel.selectMovie(movie);
                }
            }
        });
        recyclerMovies.setAdapter(movieCardAdapter);
    }

    private void observeViewModel() {
        viewModel.getCategories().observe(this, categories -> {
            categoryAdapter.setCategories(categories);
        });

        viewModel.getMovies().observe(this, movies -> {
            movieCardAdapter.setMovies(movies);
            // Reset focus to first item when category changes
            if (!movies.isEmpty()) {
                recyclerMovies.scrollToPosition(0);
            }
        });

        viewModel.getSelectedMovie().observe(this, movie -> {
            if (movie != null) {
                updateHeroView(movie);
                lastSelectedMovie = movie;
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

        // Prepare and play video
        playTrailer(movie.getTrailerUrl());
    }

    private void playTrailer(String videoUrl) {
        releasePlayer();
        if (videoUrl != null && !videoUrl.isEmpty()) {
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
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
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
        Movie currentMovie = viewModel.getSelectedMovie().getValue();
        if (currentMovie != null && currentMovie.getTrailerUrl() != null && !currentMovie.getTrailerUrl().isEmpty()) {
            playTrailer(currentMovie.getTrailerUrl());
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
