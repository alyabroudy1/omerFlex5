package com.omarflex6.ui.home;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex6.R;
import com.omarflex6.controller.base.DataController;
import com.omarflex6.controller.home.HomeDataController;
import com.omarflex6.controller.home.HomeInteractionController;
import com.omarflex6.data.model.Category;
import com.omarflex6.data.model.Movie;
import com.omarflex6.ui.home.adapter.CategoryAdapter;
import com.omarflex6.ui.home.adapter.MovieCardAdapter;
import com.omarflex6.ui.navigation.ButtonRowLayer;
import com.omarflex6.ui.navigation.TvFocusController;
import com.omarflex6.ui.navigation.VerticalRecyclerLayer;
import com.omarflex6.util.SoundManager;

import java.util.Arrays;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private RecyclerView recyclerCategories;
    private RecyclerView recyclerMovies;
    private View sidebarWrapper;
    private View sidebarContainer;
    private Button btnWatchNow;
    private ImageButton btnMute;
    private ImageButton btnFullscreen;

    private TextView textHeroTitle;
    private TextView textHeroDescription;

    private CategoryAdapter categoryAdapter;
    private MovieCardAdapter movieCardAdapter;
    private TvFocusController focusController;
    private VerticalRecyclerLayer moviesLayer;
    private SoundManager soundManager;

    // Focus Memory
    private java.util.Map<String, Integer> categoryFocusMap = new java.util.HashMap<>();
    private String currentCategoryId = null;

    // Controllers
    private HomeDataController dataController;
    private HomeInteractionController interactionController;

    private boolean isSidebarVisible = true;
    private static final int SIDEBAR_ANIMATION_DURATION = 250;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        soundManager = SoundManager.getInstance(this);

        initControllers();
        initViews();
        setupAdapters();
        setupNavigation();

        // Attach controllers
        dataController.onAttach();
        interactionController.onAttach();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (soundManager != null)
            soundManager.cleanup();
        if (dataController != null)
            dataController.onDetach();
        if (interactionController != null)
            interactionController.onDetach();
    }

    private void initControllers() {
        // Data Controller impl
        dataController = new HomeDataController(new DataController.DataCallback<List<Category>>() {
            @Override
            public void onDataLoaded(List<Category> data) {
                categoryAdapter.setCategories(data);
                // Initial load
                if (!data.isEmpty()) {
                    movieCardAdapter.setMovies(data.get(0).getMovies());
                    if (!data.get(0).getMovies().isEmpty()) {
                        updateHero(data.get(0).getMovies().get(0));
                    }
                }

                // Hide loading
                View loading = findViewById(R.id.loading_container);
                if (loading != null)
                    loading.setVisibility(View.GONE);
            }

            @Override
            public void onMoreDataLoaded(List<Category> data) {
                // For simplicity, just adding movies from the first new category to current
                // list
                if (!data.isEmpty()) {
                    movieCardAdapter.addMovies(data.get(0).getMovies());
                }
            }

            @Override
            public void onDataError(String error) {
                Log.e("HomeActivity", "Data Error: " + error);
            }
        });

        // Interaction Controller impl
        interactionController = new HomeInteractionController(this,
                new HomeInteractionController.InteractionCallback() {
                    @Override
                    public void onCategorySelected(Category category) {
                        if (soundManager != null)
                            soundManager.playSelect(null);
                        if (category.getMovies() != null) {
                            // 1. Save focus for previous category
                            if (currentCategoryId != null && moviesLayer != null) {
                                int lastPos = moviesLayer.getSavedAdapterPosition();
                                categoryFocusMap.put(currentCategoryId, lastPos);
                                Log.d("HomeActivity", "Saved focus for " + currentCategoryId + ": " + lastPos);
                            }

                            // 2. Update current category
                            currentCategoryId = category.getId(); // Use ID or unique identifier

                            // 3. Update data
                            movieCardAdapter.setMovies(category.getMovies());

                            // 4. Restore focus for new category
                            int savedPos = 0;
                            if (currentCategoryId != null && categoryFocusMap.containsKey(currentCategoryId)) {
                                savedPos = categoryFocusMap.get(currentCategoryId);
                            }
                            Log.d("HomeActivity", "Restoring focus for " + currentCategoryId + ": " + savedPos);

                            if (moviesLayer != null) {
                                moviesLayer.setSavedAdapterPosition(savedPos);
                            }
                            recyclerMovies.scrollToPosition(savedPos);
                        }
                    }

                    @Override
                    public void onMovieFocused(Movie movie) {
                        updateHero(movie);
                    }

                    @Override
                    public void onLoadMoreRequested() {
                        dataController.loadMoreData();
                    }
                });
    }

    private void initViews() {
        recyclerCategories = findViewById(R.id.recycler_categories);
        recyclerMovies = findViewById(R.id.recycler_movies);

        sidebarWrapper = findViewById(R.id.sidebar_wrapper);
        sidebarContainer = findViewById(R.id.sidebar_container);

        btnWatchNow = findViewById(R.id.btn_watch_now);
        btnMute = findViewById(R.id.btn_mute);
        btnFullscreen = findViewById(R.id.btn_fullscreen);

        textHeroTitle = findViewById(R.id.text_hero_title);
        textHeroDescription = findViewById(R.id.text_hero_description);

        findViewById(R.id.loading_container).setVisibility(View.VISIBLE);
    }

    private void setupAdapters() {
        recyclerCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerMovies.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        categoryAdapter = new CategoryAdapter();
        // Bridge adapter listener to controller
        categoryAdapter.setListener(category -> interactionController.getCategoryClickHandler().onItemClick(category));
        recyclerCategories.setAdapter(categoryAdapter);

        movieCardAdapter = new MovieCardAdapter();
        // Bridge adapter listener to controller
        movieCardAdapter.setListener(new MovieCardAdapter.OnMovieListener() {
            @Override
            public void onMovieClicked(Movie movie) {
                if (soundManager != null)
                    soundManager.playSelect(null);
                interactionController.getMovieClickHandler().onItemClick(movie);
            }

            @Override
            public void onMovieFocused(Movie movie) {
                interactionController.getMovieClickHandler().onItemFocus(movie);
            }

            @Override
            public void onLoadMoreClicked() {
                interactionController.getLoadMoreClickHandler().run();
            }
        });
        recyclerMovies.setAdapter(movieCardAdapter);
    }

    private void updateHero(Movie movie) {
        textHeroTitle.setText(movie.getTitle());
        textHeroDescription.setText(movie.getDescription());
    }

    private void setupNavigation() {
        boolean isRtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        focusController = new TvFocusController(isRtl);
        focusController.setSoundManager(soundManager);
        focusController.setDebugEnabled(true);

        final String COL1_SIDEBAR = "column_1_sidebar";
        final String COL2_MOVIES = "column_2_movies";
        final String COL3_HERO = "column_3_hero";

        focusController.registerLayer(
                new VerticalRecyclerLayer(COL1_SIDEBAR, recyclerCategories, null, COL2_MOVIES));

        moviesLayer = new VerticalRecyclerLayer(COL2_MOVIES, recyclerMovies, COL1_SIDEBAR, COL3_HERO);
        focusController.registerLayer(moviesLayer);

        focusController.registerLayer(
                new ButtonRowLayer(COL3_HERO, Arrays.asList(btnWatchNow, btnMute, btnFullscreen), COL2_MOVIES));

        focusController.setCurrentLayer(COL1_SIDEBAR);

        focusController.setOnLayerChangeListener((oldLayer, newLayer) -> {
            if (COL1_SIDEBAR.equals(newLayer)) {
                showSidebar();
            } else if (COL1_SIDEBAR.equals(oldLayer)) {
                hideSidebar();
            }
        });

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus == null)
                return;
            // Optional: Keep this for non-dpad interaction only?
            // For now, relying on controller is safer for dpad.
        });
    }

    // ... helper methods ...

    private void hideSidebar() {
        if (!isSidebarVisible || sidebarContainer == null)
            return;
        isSidebarVisible = false;

        sidebarContainer.animate()
                .translationX(-sidebarContainer.getWidth())
                .alpha(0f)
                .setDuration(SIDEBAR_ANIMATION_DURATION)
                .withEndAction(() -> sidebarContainer.setVisibility(View.GONE))
                .start();

        if (sidebarWrapper != null) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) sidebarWrapper.getLayoutParams();
            params.weight = 0f;
            sidebarWrapper.setLayoutParams(params);
        }
    }

    private void showSidebar() {
        if (isSidebarVisible || sidebarContainer == null)
            return;
        isSidebarVisible = true;

        sidebarContainer.setVisibility(View.VISIBLE);
        sidebarContainer.animate()
                .translationX(0)
                .alpha(1f)
                .setDuration(SIDEBAR_ANIMATION_DURATION)
                .start();

        if (sidebarWrapper != null) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) sidebarWrapper.getLayoutParams();
            params.weight = 0.12f;
            sidebarWrapper.setLayoutParams(params);

            // Re-request focus after layout pass ensures width > 0
            sidebarWrapper.post(() -> {
                if (focusController != null &&
                        "column_1_sidebar".equals(focusController.getCurrentLayerName())) {
                    focusController.getCurrentLayer().requestFocus();
                }
            });
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (focusController != null && focusController.dispatchKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean isDescendantOf(View child, View parent) {
        if (child == parent)
            return true;
        if (child.getParent() == parent)
            return true;
        while (child.getParent() != null && child.getParent() instanceof View) {
            if (child.getParent() == parent)
                return true;
            child = (View) child.getParent();
        }
        return false;
    }
}
