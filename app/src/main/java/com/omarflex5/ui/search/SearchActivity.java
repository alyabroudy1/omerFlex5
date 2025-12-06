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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex5.R;
import com.omarflex5.data.local.entity.ServerEntity;
import com.omarflex5.data.repository.ServerRepository;
import com.omarflex5.data.scraper.BaseHtmlParser;
import com.omarflex5.data.scraper.WebViewScraperManager;
import com.omarflex5.ui.browser.BrowserActivity;
import com.omarflex5.ui.player.PlayerActivity;

import java.util.List;
import java.util.Map;

/**
 * Activity that displays multi-server search results.
 * Uses BrowserActivity for video sniffing when needed.
 */
public class SearchActivity extends AppCompatActivity {

    private static final String TAG = "SearchActivity";
    private static final int REQUEST_VIDEO_BROWSER = 1001;

    public static final String EXTRA_QUERY = "extra_query";
    public static final String EXTRA_MOVIE_TITLE = "extra_movie_title";

    private SearchViewModel viewModel;
    private SearchResultAdapter adapter;
    private WebViewScraperManager scraperManager;
    private ServerRepository serverRepository;

    // Views
    private RecyclerView recyclerResults;
    private ProgressBar progressBar;
    private TextView textStatus;
    private TextView textTitle;
    private Button btnLoadMore;
    private View emptyView;

    // State for resolution/navigation
    private List<BaseHtmlParser.ParsedItem> pendingResolutionItems;
    private ServerEntity currentServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        scraperManager = WebViewScraperManager.getInstance(this);
        serverRepository = ServerRepository.getInstance(this);

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

            // Lookup server by ID and load the detail page
            serverRepository.getServerById(result.serverId, server -> {
                if (server != null) {
                    runOnUiThread(() -> {
                        showOverlayLoading("Fetching content...");
                        loadPageAndParse(server, result.pageUrl);
                    });
                } else {
                    runOnUiThread(() -> android.widget.Toast.makeText(this, "Server not found",
                            android.widget.Toast.LENGTH_SHORT).show());
                }
            });
        });

        int spanCount = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 4 : 2;
        recyclerResults.setLayoutManager(new GridLayoutManager(this, spanCount));
        recyclerResults.setAdapter(adapter);
    }

    // ==================== PAGE LOADING ====================

    private void loadPageAndParse(ServerEntity server, String url) {
        Log.d(TAG, "Loading: " + url);

        new Thread(() -> {
            scraperManager.loadHybrid(server, url, true,
                    new WebViewScraperManager.ScraperCallback() {
                        @Override
                        public void onSuccess(String html, Map<String, String> cookies) {
                            Log.d(TAG, "--- PAGE LOADED ---");
                            runOnUiThread(() -> handlePageLoaded(server, url, html));
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                hideOverlayLoading();
                                android.widget.Toast.makeText(SearchActivity.this,
                                        "Load Error: " + message, android.widget.Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
        }).start();
    }

    private void handlePageLoaded(ServerEntity server, String url, String html) {
        try {
            BaseHtmlParser parser = com.omarflex5.data.scraper.ParserFactory
                    .getParser(server.getName(), html, url);
            BaseHtmlParser.ParsedItem result = parser.parseDetailPage();
            List<BaseHtmlParser.ParsedItem> subItems = result.getSubItems();

            // Check if we need to launch browser for video sniffing
            if (subItems == null || subItems.isEmpty()) {
                if (result.getType() == com.omarflex5.data.local.entity.MediaType.FILM
                        || result.getType() == com.omarflex5.data.local.entity.MediaType.EPISODE) {
                    // Launch browser to sniff video
                    hideOverlayLoading();
                    BrowserActivity.launch(this, url, REQUEST_VIDEO_BROWSER);
                    return;
                }
                hideOverlayLoading();
                android.widget.Toast.makeText(this, "No content found.",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            // Determine if these are resolution options or navigation links
            boolean isResolution = false;
            if (!subItems.isEmpty()) {
                String firstQuality = subItems.get(0).getQuality();
                String firstTitle = subItems.get(0).getTitle();
                if (firstQuality != null || (firstTitle != null &&
                        (firstTitle.contains("Auto") || firstTitle.contains("Quality")))) {
                    isResolution = true;
                }
            }

            hideOverlayLoading();
            if (isResolution) {
                pendingResolutionItems = subItems;
                showResolutionDialog(subItems);
            } else {
                currentServer = server;
                pendingResolutionItems = subItems;
                showNavigationDialog(server, subItems);
            }

        } catch (Exception e) {
            Log.e(TAG, "Parser Error", e);
            hideOverlayLoading();
            android.widget.Toast.makeText(this, "Error: " + e.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== OVERLAY LOADING ====================

    private void showOverlayLoading(String message) {
        View overlay = findViewById(R.id.resolution_overlay);
        View loadingContainer = findViewById(R.id.overlay_loading_container);
        RecyclerView recycler = findViewById(R.id.recycler_resolutions_overlay);
        TextView loadingText = findViewById(R.id.text_loading_status);
        TextView title = findViewById(R.id.text_overlay_title);
        TextView subtitle = findViewById(R.id.text_overlay_subtitle);

        if (overlay != null && loadingContainer != null) {
            title.setText("Loading");
            subtitle.setText("Please wait...");
            loadingText.setText(message);
            recycler.setVisibility(View.GONE);
            loadingContainer.setVisibility(View.VISIBLE);

            if (overlay.getVisibility() != View.VISIBLE) {
                overlay.setAlpha(0f);
                overlay.setVisibility(View.VISIBLE);
                overlay.animate().alpha(1f).setDuration(200).start();
            }
        }
    }

    private void hideOverlayLoading() {
        View loadingContainer = findViewById(R.id.overlay_loading_container);
        RecyclerView recycler = findViewById(R.id.recycler_resolutions_overlay);
        if (loadingContainer != null) {
            loadingContainer.setVisibility(View.GONE);
            if (recycler != null)
                recycler.setVisibility(View.VISIBLE);
        }
    }

    // ==================== NAVIGATION DIALOG ====================

    private void showNavigationDialog(ServerEntity server, List<BaseHtmlParser.ParsedItem> items) {
        currentServer = server;
        pendingResolutionItems = items;

        runOnUiThread(() -> {
            View overlay = findViewById(R.id.resolution_overlay);
            RecyclerView recycler = findViewById(R.id.recycler_resolutions_overlay);
            Button btnClose = findViewById(R.id.btn_close_overlay);
            TextView title = findViewById(R.id.text_overlay_title);
            TextView subtitle = findViewById(R.id.text_overlay_subtitle);

            if (overlay == null || recycler == null) {
                Log.e(TAG, "Resolution overlay not found!");
                return;
            }

            title.setText("Select Watch Server");
            subtitle.setText("Choose a streaming source");
            hideOverlayLoading();

            recycler.setLayoutManager(new LinearLayoutManager(this));
            NavigationCardAdapter adapter = new NavigationCardAdapter(items, item -> {
                hideResolutionOverlay();
                showOverlayLoading("Loading server...");
                loadPageAndParse(server, item.getPageUrl());
            });
            recycler.setAdapter(adapter);

            btnClose.setOnClickListener(v -> {
                pendingResolutionItems = null;
                hideResolutionOverlay();
            });

            overlay.setOnClickListener(v -> {
                pendingResolutionItems = null;
                hideResolutionOverlay();
            });

            overlay.setAlpha(0f);
            overlay.setVisibility(View.VISIBLE);
            overlay.animate().alpha(1f).setDuration(200).start();
        });
    }

    // ==================== RESOLUTION DIALOG ====================

    private void showResolutionDialog(List<BaseHtmlParser.ParsedItem> items) {
        runOnUiThread(() -> {
            View overlay = findViewById(R.id.resolution_overlay);
            RecyclerView recycler = findViewById(R.id.recycler_resolutions_overlay);
            Button btnClose = findViewById(R.id.btn_close_overlay);
            TextView title = findViewById(R.id.text_overlay_title);
            TextView subtitle = findViewById(R.id.text_overlay_subtitle);

            if (overlay == null || recycler == null) {
                Log.e(TAG, "Resolution overlay not found!");
                return;
            }

            title.setText("Select Quality");
            subtitle.setText("Choose video quality");
            hideOverlayLoading();

            recycler.setLayoutManager(new LinearLayoutManager(this));
            ResolutionCardAdapter adapter = new ResolutionCardAdapter(items, item -> {
                hideResolutionOverlay();
                playItem(item);
            });
            recycler.setAdapter(adapter);

            btnClose.setOnClickListener(v -> {
                pendingResolutionItems = null;
                hideResolutionOverlay();
            });

            overlay.setOnClickListener(v -> {
                pendingResolutionItems = null;
                hideResolutionOverlay();
            });

            overlay.setAlpha(0f);
            overlay.setVisibility(View.VISIBLE);
            overlay.animate().alpha(1f).setDuration(200).start();
        });
    }

    private void hideResolutionOverlay() {
        View overlay = findViewById(R.id.resolution_overlay);
        if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
            overlay.animate().alpha(0f).setDuration(150).withEndAction(() -> overlay.setVisibility(View.GONE)).start();
        }
    }

    // ==================== PLAY ITEM ====================

    private void playItem(BaseHtmlParser.ParsedItem item) {
        String url = item.getPageUrl();
        if (url != null && url.startsWith("http")) {
            // Check if it's a direct video URL
            if (url.contains(".mp4") || url.contains(".m3u8") || url.contains("googlevideo")
                    || url.contains("cdnstream") || url.contains("streamtape")) {
                launchPlayer(url, item.getTitle());
            } else {
                // Page URL - launch browser to sniff
                BrowserActivity.launch(this, url, REQUEST_VIDEO_BROWSER);
            }
        } else {
            android.widget.Toast.makeText(this, "Invalid URL", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void launchPlayer(String videoUrl, String title) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, title != null ? title : "Video");
        startActivity(intent);
    }

    // ==================== ACTIVITY RESULT ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VIDEO_BROWSER && resultCode == RESULT_OK && data != null) {
            String videoUrl = data.getStringExtra(BrowserActivity.EXTRA_VIDEO_URL);
            String cookies = data.getStringExtra(BrowserActivity.EXTRA_COOKIES);
            String referer = data.getStringExtra(BrowserActivity.EXTRA_REFERER);
            String userAgent = data.getStringExtra(BrowserActivity.EXTRA_USER_AGENT);

            Log.d(TAG, "Video found from browser: " + videoUrl);

            if (videoUrl != null) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
                intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, "Sniffed Video");
                if (cookies != null)
                    intent.putExtra("EXTRA_COOKIES", cookies);
                if (referer != null)
                    intent.putExtra("EXTRA_REFERER", referer);
                if (userAgent != null)
                    intent.putExtra("EXTRA_USER_AGENT", userAgent);
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-show the resolution/navigation dialog if we have pending items
        if (pendingResolutionItems != null && !pendingResolutionItems.isEmpty()) {
            View overlay = findViewById(R.id.resolution_overlay);
            if (overlay != null && overlay.getVisibility() != View.VISIBLE) {
                if (currentServer != null) {
                    showNavigationDialog(currentServer, pendingResolutionItems);
                } else {
                    showResolutionDialog(pendingResolutionItems);
                }
            }
        }
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

    @Override
    public void onBackPressed() {
        View overlay = findViewById(R.id.resolution_overlay);
        if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
            hideResolutionOverlay();
            pendingResolutionItems = null;
            return;
        }
        viewModel.clearSearch();
        super.onBackPressed();
    }

    // ==================== ADAPTERS ====================

    private static class NavigationCardAdapter extends RecyclerView.Adapter<NavigationCardAdapter.ViewHolder> {
        private final List<BaseHtmlParser.ParsedItem> items;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onClick(BaseHtmlParser.ParsedItem item);
        }

        NavigationCardAdapter(List<BaseHtmlParser.ParsedItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_resolution_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            BaseHtmlParser.ParsedItem item = items.get(position);
            holder.textQualityBadge.setText("▶");
            holder.textServerName.setText(item.getTitle() != null ? item.getTitle() : "Server " + (position + 1));
            holder.textServerInfo.setText("Tap to play • Option " + (position + 1));
            holder.itemView.setOnClickListener(v -> listener.onClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textQualityBadge, textServerName, textServerInfo;

            ViewHolder(View itemView) {
                super(itemView);
                textQualityBadge = itemView.findViewById(R.id.text_quality_badge);
                textServerName = itemView.findViewById(R.id.text_server_name);
                textServerInfo = itemView.findViewById(R.id.text_server_info);
            }
        }
    }

    private static class ResolutionCardAdapter extends RecyclerView.Adapter<ResolutionCardAdapter.ViewHolder> {
        private final List<BaseHtmlParser.ParsedItem> items;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onClick(BaseHtmlParser.ParsedItem item);
        }

        ResolutionCardAdapter(List<BaseHtmlParser.ParsedItem> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_resolution_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            BaseHtmlParser.ParsedItem item = items.get(position);
            String quality = item.getQuality();
            holder.textQualityBadge.setText(quality != null && !quality.isEmpty() ? quality : "Auto");
            holder.textServerName.setText(item.getTitle() != null ? item.getTitle() : "Server " + (position + 1));
            holder.textServerInfo.setText("FaselHD • Option " + (position + 1));
            holder.itemView.setOnClickListener(v -> listener.onClick(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textQualityBadge, textServerName, textServerInfo;

            ViewHolder(View itemView) {
                super(itemView);
                textQualityBadge = itemView.findViewById(R.id.text_quality_badge);
                textServerName = itemView.findViewById(R.id.text_server_name);
                textServerInfo = itemView.findViewById(R.id.text_server_info);
            }
        }
    }
}
