package com.omarflex5.ui.details;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex5.R;
import com.omarflex5.data.local.entity.MediaType;
import com.omarflex5.data.local.entity.ServerEntity;
import com.omarflex5.data.repository.ServerRepository;
import com.omarflex5.data.scraper.BaseHtmlParser;
import com.omarflex5.data.scraper.ParserFactory;
import com.omarflex5.data.scraper.WebViewScraperManager;
import com.omarflex5.ui.browser.BrowserActivity;
import com.omarflex5.ui.player.PlayerActivity;

import java.util.List;
import java.util.Map;

public class DetailsActivity extends com.omarflex5.ui.base.BaseActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SERVER_ID = "extra_server_id";
    public static final String EXTRA_BREADCRUMB = "extra_breadcrumb";

    private static final int REQUEST_VIDEO_BROWSER = 1002;

    private RecyclerView recyclerView;
    private DetailsAdapter adapter;
    private View loadingLayout;
    private View errorLayout;
    private TextView errorText;
    private Button btnRetry;
    private Toolbar toolbar;

    private String url;
    private String title;
    private String breadcrumb; // Accumulated title hierarchy: "Series - Season - Episode"
    private long serverId;
    private ServerEntity currentServer;

    private WebViewScraperManager scraperManager;
    private ServerRepository serverRepository;

    public static void start(Context context, String url, String title, long serverId) {
        start(context, url, title, serverId, null);
    }

    public static void start(Context context, String url, String title, long serverId, String breadcrumb) {
        Intent intent = new Intent(context, DetailsActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SERVER_ID, serverId);
        intent.putExtra(EXTRA_BREADCRUMB, breadcrumb);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        url = getIntent().getStringExtra(EXTRA_URL);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        serverId = getIntent().getLongExtra(EXTRA_SERVER_ID, -1);
        breadcrumb = getIntent().getStringExtra(EXTRA_BREADCRUMB);

        // Initialize breadcrumb with first title if not set
        if (breadcrumb == null || breadcrumb.isEmpty()) {
            breadcrumb = title;
        }

        scraperManager = WebViewScraperManager.getInstance(this);
        serverRepository = ServerRepository.getInstance(this);

        initViews();
        setupRecyclerView();

        if (serverId != -1) {
            loadServerAndContent();
        } else {
            showError("Missing Server ID");
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(title != null ? title : "Loading...");
        }

        recyclerView = findViewById(R.id.recycler_details);
        loadingLayout = findViewById(R.id.layout_loading);
        errorLayout = findViewById(R.id.layout_error);
        errorText = findViewById(R.id.text_error);
        btnRetry = findViewById(R.id.btn_retry);

        btnRetry.setOnClickListener(v -> loadContent());
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DetailsAdapter(this::onItemClicked);
        recyclerView.setAdapter(adapter);
    }

    private void loadServerAndContent() {
        showLoading();
        serverRepository.getServerById(serverId, server -> {
            if (server != null) {
                currentServer = server;
                loadContent();
            } else {
                runOnUiThread(() -> showError("Server not found"));
            }
        });
    }

    private void loadContent() {
        if (currentServer == null || url == null)
            return;

        showLoading();
        new Thread(() -> {
            scraperManager.loadHybrid(currentServer, url, true, this, new WebViewScraperManager.ScraperCallback() {
                @Override
                public void onSuccess(String html, Map<String, String> cookies) {
                    runOnUiThread(() -> parseContent(html));
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> showError(message));
                }
            });
        }).start();
    }

    private void parseContent(String html) {
        try {
            BaseHtmlParser parser = ParserFactory.getParser(currentServer.getName(), html, url);
            BaseHtmlParser.ParsedItem result = parser.parseDetailPage();
            List<BaseHtmlParser.ParsedItem> subItems = result.getSubItems();

            if (subItems == null || subItems.isEmpty()) {
                // If it's a leaf node with no subItems (e.g. direct link or needs sniffing)
                // Check if it is playable content
                if (result.getType() == MediaType.FILM || result.getType() == MediaType.EPISODE) {
                    // Launch browser sniffer directly
                    // Keep activity alive to receive onActivityResult with video URL
                    BrowserActivity.launch(DetailsActivity.this, url, REQUEST_VIDEO_BROWSER);
                    return;
                }
                showError("No content found");
            } else {
                showContent();
                adapter.setItems(subItems);

                // Update title if parsed title is available?
                if (result.getTitle() != null && !result.getTitle().isEmpty()) {
                    // toolbar.setTitle(result.getTitle());
                }

                // Auto-trigger first sniffable item (skip extra click for servers)
                if (!subItems.isEmpty()) {
                    BaseHtmlParser.ParsedItem firstItem = subItems.get(0);
                    String firstUrl = firstItem.getPageUrl();
                    if (needsVideoSniffing(firstUrl)) {
                        // Delay slightly to let UI update
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            onItemClicked(firstItem);
                        }, 300);
                    }
                }
            }
        } catch (Exception e) {
            showError("Parsing Error: " + e.getMessage());
        }
    }

    private void onItemClicked(BaseHtmlParser.ParsedItem item) {
        // Navigation Logic
        // 1. If it has a video URL (direct), play it.
        // 2. If it is a resolution option, play it.
        // 3. Otherwise, drill down (Series -> Seasons -> Episodes -> Servers).

        String itemUrl = item.getPageUrl();
        String itemTitle = item.getTitle();

        // Build full title for player (ignore server names and resolutions)
        String fullTitle = buildPlayerTitle(item);

        // Check for direct video extensions or known patterns
        if (isDirectVideo(itemUrl)) {
            launchPlayer(itemUrl, fullTitle);
            return;
        }

        // Check if this URL needs video sniffing (e.g., video_player pages)
        // Launch BrowserActivity directly instead of opening intermediate
        // DetailsActivity
        if (needsVideoSniffing(itemUrl)) {
            // Store fullTitle for onActivityResult
            getIntent().putExtra("pending_title", fullTitle);
            BrowserActivity.launch(this, itemUrl, REQUEST_VIDEO_BROWSER);
            return;
        }

        // Use heuristics to decide: is this a final playable item or a container?
        // If it's quality/resolution, play it (might need sniffing if not direct link).
        String quality = item.getQuality();
        if (quality != null && !quality.isEmpty()) {
            // It's a resolution. if url is not direct video, sniff it.
            if (isDirectVideo(itemUrl)) {
                launchPlayer(itemUrl, fullTitle);
            } else {
                getIntent().putExtra("pending_title", fullTitle);
                BrowserActivity.launch(this, itemUrl, REQUEST_VIDEO_BROWSER);
            }
            return;
        }

        // Build new breadcrumb for next level (append current item title)
        String newBreadcrumb = buildBreadcrumb(itemTitle);

        // If it seems to be a container (Season X, Episode Y, Server Name), open new
        // DetailsActivity
        DetailsActivity.start(this, itemUrl, itemTitle, serverId, newBreadcrumb);
    }

    /**
     * Builds the title to show in player based on current breadcrumb.
     * Ignores server/resolution titles, uses the meaningful content hierarchy.
     */
    private String buildPlayerTitle(BaseHtmlParser.ParsedItem item) {
        // If breadcrumb has meaningful content, use it
        if (breadcrumb != null && !breadcrumb.isEmpty()) {
            // For episodes, append episode title if not already in breadcrumb
            String itemTitle = item.getTitle();
            if (item.getType() == MediaType.EPISODE && itemTitle != null) {
                if (!breadcrumb.contains(itemTitle)) {
                    return breadcrumb + " - " + itemTitle;
                }
            }
            return breadcrumb;
        }
        // Fallback to current title
        return title != null ? title : "Video";
    }

    /**
     * Builds breadcrumb for next navigation level.
     */
    private String buildBreadcrumb(String itemTitle) {
        if (breadcrumb == null || breadcrumb.isEmpty()) {
            return itemTitle;
        }
        if (itemTitle == null || itemTitle.isEmpty()) {
            return breadcrumb;
        }
        return breadcrumb + " - " + itemTitle;
    }

    private boolean isDirectVideo(String url) {
        if (url == null)
            return false;
        return url.contains(".mp4") || url.contains(".m3u8") || url.contains("googlevideo")
                || url.contains("cdnstream") || url.contains("streamtape");
    }

    /**
     * Checks if this URL likely needs WebView video sniffing.
     * These are player/embed pages that need BrowserActivity directly.
     */
    private boolean needsVideoSniffing(String url) {
        if (url == null)
            return false;
        return url.contains("video_player") || url.contains("player_iframe")
                || url.contains("/embed/") || url.contains("player_token");
    }

    private void launchPlayer(String videoUrl, String title) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, title != null ? title : "Video");
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_BROWSER && resultCode == RESULT_OK && data != null) {
            String videoUrl = data.getStringExtra(BrowserActivity.EXTRA_VIDEO_URL);

            // SAVE COOKIES (Fixes Re-Challenge Loop)
            String cookies = data.getStringExtra(BrowserActivity.EXTRA_COOKIES);
            if (currentServer != null && cookies != null) {
                scraperManager.saveCookies(currentServer, cookies);
            }

            if (videoUrl != null) {
                // Use stored pending title, or fallback to breadcrumb
                String pendingTitle = getIntent().getStringExtra("pending_title");
                String playerTitle = (pendingTitle != null && !pendingTitle.isEmpty())
                        ? pendingTitle
                        : (breadcrumb != null ? breadcrumb : title);
                launchPlayer(videoUrl, playerTitle);
            }
        }
    }

    private void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        errorLayout.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
        errorText.setText(message);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // DEBUG: Log all key events for NVIDIA Shield TV remote debugging
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        String keyName = android.view.KeyEvent.keyCodeToString(keyCode);
        android.util.Log.d("DetailsActivity_KEY", "KEY DOWN: " + keyName + " (code=" + keyCode + ")");

        // Log focus state
        android.view.View focused = getCurrentFocus();
        if (focused != null) {
            android.util.Log.d("DetailsActivity_KEY", "Current focus: " + focused.getClass().getSimpleName()
                    + " id="
                    + (focused.getId() != android.view.View.NO_ID ? getResources().getResourceEntryName(focused.getId())
                            : "NO_ID"));
        } else {
            android.util.Log.d("DetailsActivity_KEY", "Current focus: NONE");
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        String keyName = android.view.KeyEvent.keyCodeToString(keyCode);
        android.util.Log.d("DetailsActivity_KEY", "KEY UP: " + keyName + " (code=" + keyCode + ")");
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        String keyName = android.view.KeyEvent.keyCodeToString(event.getKeyCode());
        String action = event.getAction() == android.view.KeyEvent.ACTION_DOWN ? "DOWN"
                : event.getAction() == android.view.KeyEvent.ACTION_UP ? "UP" : "OTHER";
        android.util.Log.d("DetailsActivity_KEY", "DISPATCH: " + keyName + " action=" + action);

        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            android.view.View focused = getCurrentFocus();

            if (focused != null && recyclerView != null) {
                // Check if focus is in the RecyclerView
                android.view.ViewParent parent = focused.getParent();
                while (parent != null && parent != recyclerView) {
                    if (parent instanceof android.view.View) {
                        parent = ((android.view.View) parent).getParent();
                    } else {
                        break;
                    }
                }

                if (parent == recyclerView) {
                    // Vertical RecyclerView - handle UP/DOWN navigation
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                            || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        android.view.View focusedItem = focused;
                        // Find the direct child of RecyclerView
                        while (focusedItem.getParent() != recyclerView) {
                            focusedItem = (android.view.View) focusedItem.getParent();
                        }
                        int currentIndex = recyclerView.indexOfChild(focusedItem);
                        int targetIndex = keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP
                                ? currentIndex - 1
                                : currentIndex + 1;
                        if (targetIndex >= 0 && targetIndex < recyclerView.getChildCount()) {
                            android.view.View target = recyclerView.getChildAt(targetIndex);
                            if (target != null) {
                                target.requestFocus();
                                recyclerView.smoothScrollToPosition(
                                        recyclerView.getChildAdapterPosition(target));
                                return true;
                            }
                        }
                        return true; // At edge - block navigation
                    }
                    // Block LEFT/RIGHT in vertical list (or let it go to back button)
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
