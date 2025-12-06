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

public class DetailsActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_SERVER_ID = "extra_server_id";

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
    private String serverId;
    private ServerEntity currentServer;

    private WebViewScraperManager scraperManager;
    private ServerRepository serverRepository;

    public static void start(Context context, String url, String title, String serverId) {
        Intent intent = new Intent(context, DetailsActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SERVER_ID, serverId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        url = getIntent().getStringExtra(EXTRA_URL);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        serverId = getIntent().getStringExtra(EXTRA_SERVER_ID);

        scraperManager = WebViewScraperManager.getInstance(this);
        serverRepository = ServerRepository.getInstance(this);

        initViews();
        setupRecyclerView();

        if (serverId != null) {
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
            scraperManager.loadHybrid(currentServer, url, true, new WebViewScraperManager.ScraperCallback() {
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
                    // Launch browser sniffer directly? Or return result?
                    // Since we are in DetailsActivity, if we find no subItems but it is playable,
                    // it likely means we reached a dead end or it is a direct sniffable page.
                    // Let's assume we try to sniff it.
                    BrowserActivity.launch(DetailsActivity.this, url, REQUEST_VIDEO_BROWSER);
                    finish(); // Close this intermediate activity? Or keep it? keeping for now.
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

        // Check for direct video extensions or known patterns
        if (isDirectVideo(itemUrl)) {
            launchPlayer(itemUrl, itemTitle);
            return;
        }

        // Use heuristics to decide: is this a final playable item or a container?
        // If it's quality/resolution, play it (might need sniffing if not direct link).
        String quality = item.getQuality();
        if (quality != null && !quality.isEmpty()) {
            // It's a resolution. if url is not direct video, sniff it.
            if (isDirectVideo(itemUrl)) {
                launchPlayer(itemUrl, itemTitle);
            } else {
                BrowserActivity.launch(this, itemUrl, REQUEST_VIDEO_BROWSER);
            }
            return;
        }

        // If it seems to be a container (Season X, Episode Y, Server Name), open new
        // DetailsActivity
        // Exception: If we just clicked an Episode, the next level is Servers.
        // Exception: If we just clicked a Server, the next level is Resolutions.
        // All these are handled by the generic recursive call.
        DetailsActivity.start(this, itemUrl, itemTitle, serverId);
    }

    private boolean isDirectVideo(String url) {
        if (url == null)
            return false;
        return url.contains(".mp4") || url.contains(".m3u8") || url.contains("googlevideo")
                || url.contains("cdnstream") || url.contains("streamtape");
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
            if (videoUrl != null) {
                launchPlayer(videoUrl, "Sniffed Video");
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
}
