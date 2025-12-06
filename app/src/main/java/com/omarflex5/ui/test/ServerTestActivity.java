package com.omarflex5.ui.test;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omarflex5.R;
import com.omarflex5.data.local.entity.ServerEntity;
import com.omarflex5.data.repository.ServerRepository;
import com.omarflex5.data.scraper.BaseHtmlParser;
import com.omarflex5.data.scraper.ParserFactory;
import com.omarflex5.data.scraper.WebViewScraperManager;
import com.omarflex5.data.sniffer.VideoSniffer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ServerTestActivity extends AppCompatActivity {

    private static final String TAG = "ServerTestActivity";

    private EditText inputQuery;
    private Spinner spinnerServers;
    private Button btnTest;
    private TextView textLog;
    private RecyclerView recyclerResults;

    private ServerRepository serverRepository;
    private WebViewScraperManager scraperManager;
    private StringBuilder logBuffer = new StringBuilder();

    private TestResultAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_test);

        inputQuery = findViewById(R.id.input_query);
        spinnerServers = findViewById(R.id.spinner_servers);
        btnTest = findViewById(R.id.btn_test);
        textLog = findViewById(R.id.text_log);
        recyclerResults = findViewById(R.id.recycler_results);

        serverRepository = ServerRepository.getInstance(this);
        scraperManager = WebViewScraperManager.getInstance(this);
        scraperManager.initialize();

        setupSpinner();

        recyclerResults.setLayoutManager(new GridLayoutManager(this, 4));
        adapter = new TestResultAdapter();
        recyclerResults.setAdapter(adapter);

        adapter.setListener(this::onItemClicked);

        btnTest.setOnClickListener(v -> runTest());

        log("Ready. Select a server and click TEST.");
    }

    private void setupSpinner() {
        String[] servers = { "faselhd", "arabseed", "mycima", "cimanow", "akwam", "koora" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, servers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerServers.setAdapter(adapter);
    }

    private void runTest() {
        String serverName = (String) spinnerServers.getSelectedItem();
        final String query = inputQuery.getText().toString().trim();

        if (query.isEmpty()) {
            inputQuery.setError("Enter query");
            return;
        }

        clearLog();
        adapter.clear();

        log("Setting up test for: " + serverName);
        log("Query: " + query);

        serverRepository.getServerByName(serverName, server -> {
            if (server == null) {
                log("Error: Server not found in DB!");
                return;
            }

            log("Server Base: " + server.getBaseUrl());
            startScraping(server, query);
        });
    }

    private void startScraping(ServerEntity server, String query) {
        log("--- SEARCHING ---");

        scraperManager.search(server, query, new WebViewScraperManager.ScraperCallback() {
            @Override
            public void onSuccess(String html, Map<String, String> cookies) {
                log("--- SEARCH SUCCESS ---");
                try {
                    BaseHtmlParser parser = ParserFactory.getParser(server.getName(), html, server.getBaseUrl());
                    List<BaseHtmlParser.ParsedItem> items = parser.parseSearchResults();

                    log("Found " + items.size() + " items.");

                    runOnUiThread(() -> {
                        adapter.setItems(items);
                        if (items.isEmpty())
                            log("No results found.");
                    });

                } catch (Exception e) {
                    log("PARSER ERROR: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(String message) {
                log("SEARCH ERROR: " + message);
            }
        });
    }

    // Generic Navigation Entry Point
    private void onItemClicked(BaseHtmlParser.ParsedItem item) {
        log("\n--- NAVIGATING: " + item.getTitle() + " ---");
        loadPageAndParse(item.getPageUrl());
    }

    private void loadPageAndParse(String url) {
        String serverName = (String) spinnerServers.getSelectedItem();

        serverRepository.getServerByName(serverName, server -> {
            // RESOLVE RELATIVE URLS
            String fullUrl = url;
            if (url != null && (url.startsWith("?") || url.startsWith("/"))) {
                String baseUrl = server.getBaseUrl();
                // Ensure no double slashes if both have them, or missing slash
                if (baseUrl.endsWith("/") && fullUrl.startsWith("/")) {
                    fullUrl = baseUrl + fullUrl.substring(1);
                } else if (!baseUrl.endsWith("/") && !fullUrl.startsWith("/") && !fullUrl.startsWith("?")) {
                    fullUrl = baseUrl + "/" + fullUrl;
                } else {
                    fullUrl = baseUrl + fullUrl;
                }
            }

            log("Loading: " + fullUrl);

            // Create final variable for lambda
            String finalUrl = fullUrl;

            // FIX: Run UI on Main Thread
            runOnUiThread(() -> {
                AlertDialog loadingDialog = new AlertDialog.Builder(this)
                        .setTitle("Loading")
                        .setMessage("Fetching & Parsing...")
                        .setCancelable(false)
                        .show();

                scraperManager.loadHybrid(server, finalUrl, new WebViewScraperManager.ScraperCallback() {
                    @Override
                    public void onSuccess(String html, Map<String, String> cookies) {
                        loadingDialog.dismiss();
                        log("--- PAGE LOADED ---");

                        try {
                            // Use Production Parser!
                            BaseHtmlParser parser = ParserFactory.getParser(server.getName(), html, finalUrl);
                            BaseHtmlParser.ParsedItem result = parser.parseDetailPage();

                            List<BaseHtmlParser.ParsedItem> subItems = result.getSubItems();
                            if (subItems == null || subItems.isEmpty()) {
                                if (result.getType() == com.omarflex5.data.local.entity.MediaType.FILM
                                        || result.getType() == com.omarflex5.data.local.entity.MediaType.EPISODE) {
                                    handleWebViewFallback(finalUrl);
                                    return;
                                }
                                log("No sub-items found on this page.");
                                return;
                            }

                            log("Found " + subItems.size() + " sub-items.");

                            // Determine Action based on what we found
                            boolean isResolution = false;
                            if (!subItems.isEmpty()) {
                                String firstQuality = subItems.get(0).getQuality();
                                String firstTitle = subItems.get(0).getTitle();
                                if (firstQuality != null || firstTitle.contains("Auto")
                                        || firstTitle.contains("Quality")) {
                                    isResolution = true;
                                }
                            }

                            if (isResolution) {
                                showResolutionDialog(subItems);
                            } else {
                                showNavigationDialog(subItems);
                            }

                        } catch (Exception e) {
                            log("Parser Error: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        loadingDialog.dismiss();
                        log("Load Error: " + message);
                    }
                });
            });
        });
    }

    private void showNavigationDialog(List<BaseHtmlParser.ParsedItem> items) {
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = items.get(i).getTitle();
        }

        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("Select Content")
                    .setItems(names, (dialog, which) -> {
                        BaseHtmlParser.ParsedItem selected = items.get(which);
                        log("Selected: " + selected.getTitle());
                        loadPageAndParse(selected.getPageUrl()); // RECURSIVE CALL
                    })
                    .setPositiveButton("Close", null)
                    .show();
        });
    }

    private void showResolutionDialog(List<BaseHtmlParser.ParsedItem> items) {
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = items.get(i).getTitle() + " (" + items.get(i).getQuality() + ")";
        }

        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("Select Resolution to PLAY")
                    .setItems(names, (dialog, which) -> {
                        BaseHtmlParser.ParsedItem selected = items.get(which);
                        String url = selected.getPageUrl();

                        log("Playing: " + url);
                        if (url != null && url.startsWith("http")) {
                            android.content.Intent intent = new android.content.Intent(this,
                                    com.omarflex5.ui.player.PlayerActivity.class);
                            intent.putExtra(com.omarflex5.ui.player.PlayerActivity.EXTRA_VIDEO_URL, url);
                            intent.putExtra(com.omarflex5.ui.player.PlayerActivity.EXTRA_VIDEO_TITLE,
                                    selected.getTitle());
                            startActivity(intent);
                        } else {
                            log("Invalid URL: " + url);
                        }
                    })
                    .setPositiveButton("Close", null)
                    .show();
        });
    }

    private void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String line = time + ": " + message + "\n";
        logBuffer.append(line);
        runOnUiThread(() -> textLog.setText(logBuffer.toString()));
        Log.d(TAG, message);
    }

    private void clearLog() {
        logBuffer.setLength(0);
        textLog.setText("");
    }

    private void handleWebViewFallback(String url) {
        log("No direct links found. Launching Visible Video Sniffer for: " + url);
        runOnUiThread(() -> {
            // 1. Create a Root Container (FrameLayout for layering)
            android.widget.FrameLayout root = new android.widget.FrameLayout(this);
            root.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            root.setBackgroundColor(0xFF000000); // Black background

            // 2. Create WebView Container
            android.widget.FrameLayout webContainer = new android.widget.FrameLayout(this);
            webContainer.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(webContainer);

            // 3. Create Close Level (Overlay Button)
            Button closeBtn = new Button(this);
            closeBtn.setText("CLOSE SNIFFER");
            closeBtn.setBackgroundColor(0xFFFF0000); // Red
            closeBtn.setTextColor(0xFFFFFFFF); // White

            android.widget.FrameLayout.LayoutParams btnParams = new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            btnParams.setMargins(20, 20, 20, 20);

            root.addView(closeBtn, btnParams);

            // 4. Build Dialog - Raw Dialog for TRUE Fullscreen
            android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            dialog.setContentView(root);
            dialog.setCancelable(false);

            // Close Action
            closeBtn.setOnClickListener(v -> {
                dialog.dismiss();
            });

            // 5. Initialize Sniffer
            VideoSniffer sniffer = new VideoSniffer(this, webContainer, new VideoSniffer.SniffCallback() {
                @Override
                public void onVideoFound(String videoUrl, Map<String, String> headers) {
                    log("SNIFF SUCCESS: " + videoUrl);
                    dialog.dismiss();
                    runOnUiThread(() -> {
                        android.content.Intent intent = new android.content.Intent(ServerTestActivity.this,
                                com.omarflex5.ui.player.PlayerActivity.class);
                        intent.putExtra(com.omarflex5.ui.player.PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
                        intent.putExtra(com.omarflex5.ui.player.PlayerActivity.EXTRA_VIDEO_TITLE, "Sniffed Video");

                        // Pass Headers if available
                        if (headers != null && !headers.isEmpty()) {
                            String userAgent = headers.get("User-Agent");
                            if (userAgent != null)
                                intent.putExtra("EXTRA_USER_AGENT", userAgent);

                            String referer = headers.get("Referer");
                            if (referer != null)
                                intent.putExtra("EXTRA_REFERER", referer);
                        }

                        startActivity(intent);
                    });
                }

                @Override
                public void onError(String message) {
                    log("SNIFF ERROR: " + message);
                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(ServerTestActivity.this, "Error: " + message,
                                android.widget.Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onProgress(String message) {
                    // Optional
                }
            });

            // 6. Handle Cleanup
            dialog.setOnDismissListener(d -> sniffer.destroy());

            dialog.show();

            // 7. Start!
            sniffer.startSniffing(url);
        });
    }
}
