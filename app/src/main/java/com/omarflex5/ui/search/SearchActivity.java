
package com.omarflex5.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex5.R;
import com.omarflex5.ui.details.DetailsActivity;

/**
 * Activity that displays multi-server search results.
 * Uses BrowserActivity for video sniffing when needed.
 */
public class SearchActivity extends com.omarflex5.ui.base.BaseActivity {

    private static final String TAG = "SearchActivity";
    private static final int REQUEST_VIDEO_BROWSER = 1001; // This constant is no longer used but kept for consistency
                                                           // if needed elsewhere.

    public static final String EXTRA_QUERY = "extra_query";
    public static final String EXTRA_MOVIE_TITLE = "extra_movie_title";

    // Inheritance Extras
    public static final String EXTRA_DESCRIPTION = "extra_description";
    public static final String EXTRA_RATING = "extra_rating";
    public static final String EXTRA_CATEGORIES = "extra_categories";
    public static final String EXTRA_TRAILER = "extra_trailer";
    public static final String EXTRA_YEAR = "extra_year";
    public static final String EXTRA_TMDB_ID = "extra_tmdb_id";

    private SearchViewModel viewModel;
    private SearchResultAdapter adapter;

    // Views
    private RecyclerView recyclerResults;
    private ProgressBar progressBar;
    private TextView textStatus;
    private TextView textTitle;
    private Button btnLoadMore;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        initViews();
        setupRecyclerView();
        setupViewModel();
        setupFocusController();

        // Get query and inheritance extras from intent
        String query = getIntent().getStringExtra(EXTRA_QUERY);
        String movieTitle = getIntent().getStringExtra(EXTRA_MOVIE_TITLE);

        com.omarflex5.data.search.UnifiedSearchService.MetadataContext context = null;
        if (getIntent().hasExtra(EXTRA_DESCRIPTION)) {
            context = new com.omarflex5.data.search.UnifiedSearchService.MetadataContext();
            context.description = getIntent().getStringExtra(EXTRA_DESCRIPTION);
            context.trailerUrl = getIntent().getStringExtra(EXTRA_TRAILER);
            context.year = getIntent().getIntExtra(EXTRA_YEAR, 0);
            if (context.year == 0)
                context.year = null;

            float rating = getIntent().getFloatExtra(EXTRA_RATING, -1f);
            if (rating >= 0)
                context.rating = rating;

            java.util.ArrayList<String> categories = getIntent().getStringArrayListExtra(EXTRA_CATEGORIES);
            if (categories != null) {
                context.categories = categories;
            }

            int tmdbId = getIntent().getIntExtra(EXTRA_TMDB_ID, -1);
            if (tmdbId != -1) {
                context.tmdbId = tmdbId;
            }
        }

        if (movieTitle != null && !movieTitle.isEmpty()) {
            textTitle.setText("البحث عن: " + movieTitle);
        }

        if (query != null && !query.isEmpty()) {
            Log.d(TAG, "Starting search for: " + query + (context != null ? " with TMDB context" : ""));
            viewModel.search(query, context);
        } else {
            textStatus.setText("لا يوجد استعلام بحث");
            textStatus.setVisibility(View.VISIBLE);
        }
    }

    private void initViews() {
        recyclerResults = findViewById(R.id.recycler_results);
        progressBar = findViewById(R.id.progress_bar);
        textStatus = findViewById(R.id.text_status);
        textTitle = findViewById(R.id.text_title);
        btnLoadMore = findViewById(R.id.btn_load_more);
        emptyView = findViewById(R.id.empty_view);

        btnLoadMore.setOnClickListener(v -> viewModel.loadMore());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new SearchResultAdapter();
        adapter.setOnResultClickListener(result -> {
            Log.d(TAG, "Result clicked: " + result.title + " from " + result.serverName);

            // Directly launch DetailsActivity. It will handle loading and parsing.
            com.omarflex5.data.local.entity.MediaType type = com.omarflex5.data.local.entity.MediaType.FILM;
            if (result.type != null) {
                try {
                    type = com.omarflex5.data.local.entity.MediaType.valueOf(result.type.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Default to FILM
                }
            }
            DetailsActivity.start(this, result.pageUrl, result.title, result.serverId, null, type, null, result.mediaId,
                    null, null);
        });

        int spanCount = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 4 : 2;
        recyclerResults.setLayoutManager(new GridLayoutManager(this, spanCount));
        recyclerResults.setAdapter(adapter);
    }

    @Override
    public void onBackPressed() {
        viewModel.clearSearch();
        super.onBackPressed();
    }

    private com.omarflex5.ui.navigation.TvFocusController focusController;

    private void setupFocusController() {
        // Initialize Controller
        boolean isRTL = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        focusController = new com.omarflex5.ui.navigation.TvFocusController(isRTL);
        focusController.setDebugEnabled(true);

        // Header Layer (Back Button + Search Bar)
        // UP: Block, DOWN: Results, LEFT/RIGHT: Block
        focusController.registerLayer(new com.omarflex5.ui.navigation.SingleViewLayer(
                "header",
                findViewById(R.id.btn_back),
                null, // UP
                "results", // DOWN
                null, // LEFT
                null // RIGHT
        ));

        // Results Layer (Grid)
        // UP: Header, DOWN: Footer (Load More)
        focusController.registerLayer(new com.omarflex5.ui.navigation.GridLayer(
                "results",
                recyclerResults,
                "header", // UP
                "footer" // DOWN
        ));

        // Footer Layer (Load More)
        // UP: Results, DOWN: Block
        focusController.registerLayer(new com.omarflex5.ui.navigation.SingleViewLayer(
                "footer",
                btnLoadMore,
                "results", // UP
                null, // DOWN
                null, // LEFT
                null // RIGHT
        ));

        // Initial Focus
        findViewById(R.id.btn_back).post(() -> {
            if (focusController != null) {
                focusController.setCurrentLayer("header");
            }
        });
    }

    /**
     * Handle D-pad navigation for TV remote with RTL-aware grid navigation.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (focusController != null) {
            // Let the controller handle D-pad navigation
            // We pass the current focus to help the controller detect layer changes logic
            View currentFocus = getCurrentFocus();
            if (focusController.handleKeyEvent(event, currentFocus)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // ==================== VIEW MODEL ====================

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        viewModel.getSearchState().observe(this, state -> {
            if (state == null)
                return;

            Log.d(TAG, "Search state: " + state.status + ", results: " +
                    (state.results != null ? state.results.size() : 0));

            switch (state.status) {
                case IDLE:
                    progressBar.setVisibility(View.GONE);
                    textStatus.setVisibility(View.GONE);
                    break;

                case LOADING:
                    progressBar.setVisibility(View.VISIBLE);
                    textStatus.setText("جاري البحث...");
                    textStatus.setVisibility(View.VISIBLE);
                    btnLoadMore.setVisibility(View.GONE);
                    emptyView.setVisibility(View.GONE);
                    break;

                case LOADING_MORE:
                    progressBar.setVisibility(View.VISIBLE);
                    textStatus.setText("جاري تحميل المزيد...");
                    textStatus.setVisibility(View.VISIBLE);
                    btnLoadMore.setVisibility(View.GONE);
                    break;

                case PARTIAL:
                    progressBar.setVisibility(View.GONE);
                    adapter.setResults(state.results);

                    if (state.results.isEmpty()) {
                        textStatus.setText("لا توجد نتائج حتى الآن");
                        textStatus.setVisibility(View.VISIBLE);
                    } else {
                        textStatus.setVisibility(View.GONE);
                    }

                    if (state.pendingServers > 0) {
                        btnLoadMore.setText("تحميل المزيد (" + state.pendingServers + ")");
                        btnLoadMore.setVisibility(View.VISIBLE);
                    } else {
                        btnLoadMore.setVisibility(View.GONE);
                    }
                    break;

                case COMPLETE:
                    progressBar.setVisibility(View.GONE);
                    adapter.setResults(state.results);

                    // Show Load More button if there are pending pagination or CF tasks
                    if (viewModel.hasPendingTasks()) {
                        btnLoadMore.setText("تحميل المزيد ›");
                        btnLoadMore.setVisibility(View.VISIBLE);
                        btnLoadMore.requestFocus(); // Auto-focus for TV remote
                    } else {
                        btnLoadMore.setVisibility(View.GONE);
                    }

                    if (state.results.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        textStatus.setVisibility(View.GONE);
                    } else {
                        emptyView.setVisibility(View.GONE);
                        textStatus.setText("تم العثور على " + state.results.size() + " نتيجة");
                        textStatus.setVisibility(View.VISIBLE);
                    }
                    break;

                case ERROR:
                    progressBar.setVisibility(View.GONE);
                    btnLoadMore.setVisibility(View.GONE);
                    textStatus.setText("خطأ: " + state.errorMessage);
                    textStatus.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }
}
