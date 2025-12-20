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
import com.omarflex5.data.repository.MediaRepository;
import com.omarflex5.data.repository.ServerRepository;
import com.omarflex5.data.scraper.BaseHtmlParser;
import com.omarflex5.data.scraper.ParserFactory;
import com.omarflex5.data.scraper.WebViewScraperManager;
import com.omarflex5.ui.browser.BrowserActivity;
import com.omarflex5.ui.player.PlayerActivity;
import com.omarflex5.ui.sniffer.SnifferActivity;

import java.util.List;
import java.util.Map;

public class DetailsActivity extends com.omarflex5.ui.base.BaseActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SERVER_ID = "extra_server_id";
    public static final String EXTRA_BREADCRUMB = "extra_breadcrumb";
    public static final String EXTRA_POST_DATA = "extra_post_data"; // NEW: Support for POST requests

    public static final String EXTRA_TYPE = "extra_type"; // NEW
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_SEASON_ID = "extra_season_id";
    public static final String EXTRA_EPISODE_ID = "extra_episode_id";

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
    private String postData; // NEW: Support for POST requests (e.g. seasons)
    private MediaType mediaType; // NEW: Context Type
    private long serverId;
    private ServerEntity currentServer;
    private long mediaId = -1;
    private Long seasonId = null;
    private Long episodeId = null;

    private WebViewScraperManager scraperManager;
    private ServerRepository serverRepository;
    private MediaRepository mediaRepository;

    public static void start(Context context, String url, String title, long serverId) {
        start(context, url, title, serverId, null, null, null);
    }

    public static void start(Context context, String url, String title, long serverId, String breadcrumb,
            MediaType type) {
        start(context, url, title, serverId, breadcrumb, type, null);
    }

    public static void start(Context context, String url, String title, long serverId, String breadcrumb,
            MediaType type, String postData) {
        start(context, url, title, serverId, breadcrumb, type, postData, -1, null, null);
    }

    public static void start(Context context, String url, String title, long serverId, String breadcrumb,
            MediaType type, String postData, long mediaId, Long seasonId, Long episodeId) {
        Intent intent = new Intent(context, DetailsActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SERVER_ID, serverId);
        intent.putExtra(EXTRA_BREADCRUMB, breadcrumb);
        intent.putExtra(EXTRA_POST_DATA, postData);
        intent.putExtra(EXTRA_MEDIA_ID, mediaId);
        if (seasonId != null)
            intent.putExtra(EXTRA_SEASON_ID, (long) seasonId);
        if (episodeId != null)
            intent.putExtra(EXTRA_EPISODE_ID, (long) episodeId);
        if (type != null) {
            intent.putExtra(EXTRA_TYPE, type.name());
        }
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
        postData = getIntent().getStringExtra(EXTRA_POST_DATA);

        String typeStr = getIntent().getStringExtra(EXTRA_TYPE);
        if (typeStr != null) {
            try {
                mediaType = MediaType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                mediaType = MediaType.FILM; // Default
            }
        } else {
            mediaType = MediaType.FILM;
        }

        mediaId = getIntent().getLongExtra(EXTRA_MEDIA_ID, -1);
        if (getIntent().hasExtra(EXTRA_SEASON_ID)) {
            seasonId = getIntent().getLongExtra(EXTRA_SEASON_ID, -1);
        }
        if (getIntent().hasExtra(EXTRA_EPISODE_ID)) {
            episodeId = getIntent().getLongExtra(EXTRA_EPISODE_ID, -1);
        }

        // Initialize breadcrumb with first title if not set
        if (breadcrumb == null || breadcrumb.isEmpty()) {
            breadcrumb = title;
        }

        scraperManager = WebViewScraperManager.getInstance(this);
        serverRepository = ServerRepository.getInstance(this);
        mediaRepository = MediaRepository.getInstance(this);

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
            scraperManager.loadHybrid(currentServer, url, postData, true, this,
                    new WebViewScraperManager.ScraperCallback() {
                        @Override
                        public void onSuccess(String html, Map<String, String> cookies) {
                            new Thread(() -> parseContent(html)).start();
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

            // Pass the clicked item context to the parser
            BaseHtmlParser.ParsedItem sourceItem = new BaseHtmlParser.ParsedItem();
            sourceItem.setPageUrl(url);
            sourceItem.setTitle(title);
            sourceItem.setType(mediaType); // CRITICAL FIX: Pass the type!
            sourceItem.setMediaId(mediaId);
            sourceItem.setSeasonId(seasonId);
            sourceItem.setEpisodeId(episodeId);
            parser.setSourceItem(sourceItem);

            BaseHtmlParser.ParsedItem result = parser.parseDetailPage();

            List<BaseHtmlParser.ParsedItem> subItems = result.getSubItems();
            if (subItems != null && !subItems.isEmpty()) {
                // Attach watch history from DB (Background)
                mediaRepository.attachWatchHistory(subItems, mediaId, seasonId);
            }

            runOnUiThread(() -> {
                switch (result.getStatus()) {
                    case EMPTY_ERROR:
                        android.widget.Toast.makeText(this, result.getStatusMessage(), android.widget.Toast.LENGTH_LONG)
                                .show();
                        showError(result.getStatusMessage());
                        break;

                    case REDIRECT:
                        android.util.Log.d("DetailsActivity",
                                "Parser requested REDIRECT to: " + result.getStatusMessage());
                        this.url = result.getStatusMessage();
                        loadContent();
                        break;

                    case SUCCESS:
                    default:
                        if (subItems != null && !subItems.isEmpty()) {
                            showContent();
                            adapter.setItems(subItems);

                            // Auto-scroll/Focus logic
                            handleAutoScroll(subItems);

                            // Optimization: Auto-trigger if it's a server list
                            BaseHtmlParser.ParsedItem firstItem = subItems.get(0);
                            boolean isServer = "Server".equals(firstItem.getQuality());
                            if (isServer || needsVideoSniffing(firstItem.getPageUrl())) {
                                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                    onItemClicked(firstItem);
                                }, 300);
                            }
                        } else {
                            showError("No Content Found (Unknown Error)");
                        }
                        break;
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> showError("Parsing Error: " + e.getMessage()));
        }
    }

    /**
     * Finds the last watched item and scrolls to it.
     */
    private void handleAutoScroll(List<BaseHtmlParser.ParsedItem> items) {
        if (items == null || items.isEmpty())
            return;

        int targetIndex = -1;
        long latestWatchTime = -1;

        for (int i = 0; i < items.size(); i++) {
            BaseHtmlParser.ParsedItem item = items.get(i);
            if (item.getWatchProgress() > 0) {
                // If we found an item with progress, it's a candidate
                // For now, let's just pick the last one in the list that has progress
                // (usually the most recently watched episode if they are in order)
                targetIndex = i;
            }
        }

        if (targetIndex != -1) {
            final int index = targetIndex;
            recyclerView.post(() -> {
                recyclerView.scrollToPosition(index);
                // Also try to give it focus if we are on TV
                android.view.View view = recyclerView.getLayoutManager().findViewByPosition(index);
                if (view != null) {
                    view.requestFocus();
                } else {
                    // If view is not yet laid out, we might need a small delay or a scroll listener
                    recyclerView.smoothScrollToPosition(index);
                }
            });
        }
    }

    private void onItemClicked(BaseHtmlParser.ParsedItem item) {
        // Navigation Logic
        // 1. If it has a video URL (direct), play it.
        // 2. If it is a resolution option, play it.
        // 3. Otherwise, drill down (Series -> Seasons -> Episodes -> Servers).

        String itemUrl = item.getPageUrl();
        String itemTitle = item.getTitle();

        android.util.Log.d("DetailsDebug", "Clicked Item: " + itemTitle + " | URL: " + itemUrl);
        android.util.Log.d("DetailsDebug", "Current Activity URL: " + this.url);

        // Prevent Infinite Loop: User clicked the item representing the CURRENT page
        if (itemUrl != null && this.url != null) {
            String normItem = normalizeUrl(itemUrl);
            String normCurrent = normalizeUrl(this.url);
            android.util.Log.d("DetailsDebug", "Normalized Item: " + normItem);
            android.util.Log.d("DetailsDebug", "Normalized Current: " + normCurrent);

            if (normItem.equals(normCurrent)) {
                android.util.Log.d("DetailsDebug", "Loop detected! Blocking navigation.");
                Toast.makeText(this, "You are already viewing this content", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Build full title for player (ignore server names and resolutions)
        String fullTitle = buildPlayerTitle(item);

        // Check for direct video extensions or known patterns
        if (isDirectVideo(itemUrl)) {
            android.util.Log.d("DetailsDebug", "Direct video detected, launching player.");
            launchPlayer(itemUrl, fullTitle, null, null, null,
                    item.getMediaId(), item.getSeasonId(), item.getEpisodeId());
            return;
        }

        // Check if this URL needs video sniffing (e.g., video_player pages)
        if (needsVideoSniffing(itemUrl)) {
            // Store fullTitle for onActivityResult
            getIntent().putExtra("pending_title", fullTitle);
            startSniffer(itemUrl);
            return;
        }

        // Use heuristics to decide: is this a final playable item or a container?
        // If it's quality/resolution, play it (might need sniffing if not direct link).
        String quality = item.getQuality();
        if (quality != null && !quality.isEmpty()) {
            // SPECIAL HANDLERS:
            // "parent_series" -> Treat as Folder (Fall through)
            // "Navigation" -> Treat as Folder/Redirect (Fall through or handled
            // specifically)
            if ("parent_series".equals(quality) || "Navigation".equals(quality)) {
                // Do nothing here, let it fall through to DetailActivity.start
            } else {
                // It's a resolution (e.g. "720p"). if url is not direct video, sniff it.
                if (isDirectVideo(itemUrl)) {
                    launchPlayer(itemUrl, fullTitle, null, null, null,
                            item.getMediaId(), item.getSeasonId(), item.getEpisodeId());
                } else {
                    getIntent().putExtra("pending_title", fullTitle);
                    startSniffer(itemUrl);
                }
                return;
            }
        }

        // Build new breadcrumb for next level (append current item title)
        String newBreadcrumb = buildBreadcrumb(itemTitle);

        // If it seems to be a container (Season X, Episode Y, Server Name), open new
        // DetailsActivity
        DetailsActivity.start(this, itemUrl, itemTitle, serverId, newBreadcrumb,
                item.getType(), item.getPostData(),
                item.getMediaId(), item.getSeasonId(), item.getEpisodeId());
    }

    private void startSniffer(String url) {
        Intent intent = SnifferActivity.createIntent(this, url, SnifferActivity.STRATEGY_VIDEO);
        startActivityForResult(intent, REQUEST_VIDEO_BROWSER);
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

    private void launchPlayer(String videoUrl, String title, String referer, String userAgent, String cookie,
            long mediaId, Long seasonId, Long episodeId) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, title != null ? title : "Video");

        intent.putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId);
        if (seasonId != null)
            intent.putExtra(PlayerActivity.EXTRA_SEASON_ID, (long) seasonId);
        if (episodeId != null)
            intent.putExtra(PlayerActivity.EXTRA_EPISODE_ID, (long) episodeId);

        if (referer != null)
            intent.putExtra("EXTRA_REFERER", referer);
        if (userAgent != null)
            intent.putExtra("EXTRA_USER_AGENT", userAgent);
        if (cookie != null)
            intent.putExtra("EXTRA_COOKIE", cookie);

        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_BROWSER && resultCode == RESULT_OK && data != null) {
            String videoUrl = data.getStringExtra(SnifferActivity.RESULT_VIDEO_URL);

            // Fetch headers (SnifferActivity returns headers in a Map)
            String cookies = data.getStringExtra(SnifferActivity.RESULT_COOKIES);
            Map<String, String> headers = (Map<String, String>) data
                    .getSerializableExtra(SnifferActivity.RESULT_HEADERS);

            String referer = null;
            String userAgent = null;

            if (headers != null) {
                referer = headers.get("Referer");
                userAgent = headers.get("User-Agent");
                // Fallback for Cookie if not in separate extra (though Sniffer provides it
                // separately)
                if (cookies == null) {
                    cookies = headers.get("Cookie");
                }
            }

            // SAVE COOKIES (Fixes Re-Challenge Loop)
            if (currentServer != null && cookies != null) {
                scraperManager.saveCookies(currentServer, cookies);
            }

            if (videoUrl != null) {
                // Use stored pending title, or fallback to breadcrumb
                String pendingTitle = getIntent().getStringExtra("pending_title");
                String playerTitle = (pendingTitle != null && !pendingTitle.isEmpty())
                        ? pendingTitle
                        : (breadcrumb != null ? breadcrumb : title);
                launchPlayer(videoUrl, playerTitle, referer, userAgent, cookies,
                        mediaId, seasonId, episodeId);
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

    /**
     * Normalize URL for comparison (remove trailing slash, etc.)
     */
    private String normalizeUrl(String url) {
        if (url == null)
            return "";
        url = url.trim();
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
