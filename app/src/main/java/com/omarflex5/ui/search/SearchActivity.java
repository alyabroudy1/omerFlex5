
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

        // Get query from intent
        String query = getIntent().getStringExtra(EXTRA_QUERY);
        String movieTitle = getIntent().getStringExtra(EXTRA_MOVIE_TITLE);

        if (movieTitle != null && !movieTitle.isEmpty()) {
            textTitle.setText("البحث عن: " + movieTitle);
        }

        if (query != null && !query.isEmpty()) {
            Log.d(TAG, "Starting search for: " + query);
            viewModel.search(query);
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
            DetailsActivity.start(this, result.pageUrl, result.title, result.serverId);
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

    /**
     * Handle D-pad navigation for TV remote with RTL-aware grid navigation.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            View focused = getCurrentFocus();

            if (focused != null && recyclerResults != null) {
                // Check if focus is in the RecyclerView
                android.view.ViewParent parent = focused.getParent();
                while (parent != null && parent != recyclerResults) {
                    if (parent instanceof View) {
                        parent = ((View) parent).getParent();
                    } else {
                        break;
                    }
                }

                if (parent == recyclerResults) {
                    // Find direct child of RecyclerView
                    View focusedItem = focused;
                    while (focusedItem.getParent() != recyclerResults) {
                        focusedItem = (View) focusedItem.getParent();
                    }

                    int currentIndex = recyclerResults.indexOfChild(focusedItem);
                    GridLayoutManager layoutManager = (GridLayoutManager) recyclerResults.getLayoutManager();
                    int spanCount = layoutManager != null ? layoutManager.getSpanCount() : 4;
                    int totalChildren = recyclerResults.getChildCount();

                    int targetIndex = -1;

                    // RTL-aware navigation:
                    // LEFT = move to next item (higher index in RTL)
                    // RIGHT = move to previous item (lower index in RTL)
                    // UP/DOWN = move between rows
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        targetIndex = currentIndex + 1; // RTL: LEFT goes to next
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                        targetIndex = currentIndex - 1; // RTL: RIGHT goes to previous
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        targetIndex = currentIndex + spanCount;
                    } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                        targetIndex = currentIndex - spanCount;
                        if (targetIndex < 0) {
                            // Move focus to back button
                            findViewById(R.id.btn_back).requestFocus();
                            return true;
                        }
                    }

                    if (targetIndex >= 0 && targetIndex < totalChildren) {
                        View target = recyclerResults.getChildAt(targetIndex);
                        if (target != null) {
                            target.requestFocus();
                            recyclerResults.smoothScrollToPosition(
                                    recyclerResults.getChildAdapterPosition(target));
                            return true;
                        }
                    }

                    // Block navigation at edges
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                            keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ||
                            keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        return true;
                    }
                }
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
                    btnLoadMore.setVisibility(View.GONE);
                    adapter.setResults(state.results);

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
