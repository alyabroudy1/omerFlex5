package com.omarflex5.ui.search;

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
import com.omarflex5.data.search.UnifiedSearchService;

/**
 * Activity that displays multi-server search results.
 * Launched when a TMDB movie is clicked (selected and confirmed).
 */
public class SearchActivity extends AppCompatActivity {

    private static final String TAG = "SearchActivity";
    public static final String EXTRA_QUERY = "extra_query";
    public static final String EXTRA_MOVIE_TITLE = "extra_movie_title";

    private SearchViewModel viewModel;
    private SearchResultAdapter adapter;

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

        btnLoadMore.setOnClickListener(v -> {
            viewModel.loadMore();
        });

        // Back button
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new SearchResultAdapter();
        adapter.setOnResultClickListener(result -> {
            // TODO: Open result detail or play video
            Log.d(TAG, "Result clicked: " + result.title + " from " + result.serverName);

            // For now, show a toast with the source info
            String message = result.title + "\n" + result.serverLabel;
            if (result.alternativeSources != null && !result.alternativeSources.isEmpty()) {
                message += "\n+" + result.alternativeSources.size() + " مصادر أخرى";
            }
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
        });

        // Grid layout for TV-friendly display (2 columns for landscape)
        int spanCount = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 4 : 2;
        recyclerResults.setLayoutManager(new GridLayoutManager(this, spanCount));
        recyclerResults.setAdapter(adapter);
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        // Observe search state
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

    @Override
    public void onBackPressed() {
        viewModel.clearSearch();
        super.onBackPressed();
    }
}
