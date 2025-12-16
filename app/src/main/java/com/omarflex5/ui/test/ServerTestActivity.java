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
import com.omarflex5.ui.sniffer.SnifferActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ServerTestActivity extends AppCompatActivity {

    private static final String TAG = "ServerTestActivity";
    private static final int REQUEST_SNIFFER = 1001;

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

        handleAutoTest();
    }

    private void handleAutoTest() {
        if (getIntent().getBooleanExtra("EXTRA_AUTO_TEST", false)) {
            String query = getIntent().getStringExtra("EXTRA_QUERY");
            if (query == null)
                query = "Matrix";

            inputQuery.setText(query);

            String targetServer = getIntent().getStringExtra("EXTRA_SERVER_NAME");
            if (targetServer == null)
                targetServer = "akwam";

            // Select Target Server
            for (int i = 0; i < spinnerServers.getAdapter().getCount(); i++) {
                if (targetServer.equals(spinnerServers.getAdapter().getItem(i))) {
                    spinnerServers.setSelection(i);
                    break;
                }
            }

            // Auto run after a short delay
            new android.os.Handler().postDelayed(this::runTest, 1000);
        }
    }

    private void setupSpinner() {
        String[] servers = { "faselhd", "arabseed", "mycima", "cimanow", "akwam", "oldakwam", "koora" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, servers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerServers.setAdapter(adapter);
        // Default to ArabSeed for testing
        spinnerServers.setSelection(1);
        inputQuery.setText("la casa");
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

        scraperManager.search(server, query, true, this, new WebViewScraperManager.ScraperCallback() {
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
        // Entry from Search: Enable Auto-Redirect for Series Discovery
        loadPageAndParse(item.getPageUrl(), true);
    }

    private void loadPageAndParse(String url) {
        loadPageAndParse(url, false); // Default: No redirect
    }

    private void loadPageAndParse(String url, boolean allowAutoRedirect) {
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

                scraperManager.loadHybrid(server, finalUrl, true, this, new WebViewScraperManager.ScraperCallback() {
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

                            // SERIES DISCOVERY: Check for Parent Link and Auto-Redirect
                            if (allowAutoRedirect) {
                                for (BaseHtmlParser.ParsedItem item : subItems) {
                                    if ("parent_series".equals(item.getQuality())) {
                                        log("Series Discovery: Auto-Redirecting to Parent Series -> "
                                                + item.getTitle());
                                        loadPageAndParse(item.getPageUrl(), false); // Disable redirect for next page
                                        return;
                                    }
                                }
                            }

                            // AUTO-NAVIGATION LOGIC (Refactored to List Options)
                            java.util.List<BaseHtmlParser.ParsedItem> validOptions = new java.util.ArrayList<>();
                            for (BaseHtmlParser.ParsedItem item : subItems) {
                                boolean isNav = item.getTitle().equalsIgnoreCase("Go to Download") ||
                                        item.getTitle().equalsIgnoreCase("Direct Link") ||
                                        "Direct".equalsIgnoreCase(item.getQuality()) ||
                                        "Navigation".equalsIgnoreCase(item.getQuality()) ||
                                        "Server".equalsIgnoreCase(item.getQuality()) ||
                                // Include Episodes and Series for navigation
                                        item.getType() == com.omarflex5.data.local.entity.MediaType.EPISODE ||
                                        item.getType() == com.omarflex5.data.local.entity.MediaType.SERIES;

                                if (isNav) {
                                    validOptions.add(item);
                                }
                            }

                            if (!validOptions.isEmpty()) {
                                log("Found " + validOptions.size() + " valid server/destination options.");
                                showServerSelectionDialog(validOptions);
                                return; // Stop and wait for user selection
                            }

                            // Determine Action based on what we found (Fallback if no specific servers
                            // found)
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

    private void showServerSelectionDialog(List<BaseHtmlParser.ParsedItem> items) {
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = items.get(i).getTitle();
        }

        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("Select Server / Action")
                    .setItems(names, (dialog, which) -> {
                        BaseHtmlParser.ParsedItem item = items.get(which);
                        String url = item.getPageUrl();
                        log("User Selected: " + item.getTitle());

                        boolean isDownloadPage = url.contains("/download/")
                                || url.contains("/old/download/");

                        if (isDownloadPage || "Server".equalsIgnoreCase(item.getQuality())) {
                            // Use Sniffer for download pages or Server embeds
                            handleWebViewFallback(url);
                        } else if ("Direct".equalsIgnoreCase(item.getQuality())
                                || url.endsWith(".mp4")
                                || url.endsWith(".mkv")) {
                            // Final step: Play valid media files
                            playVideo(url, item.getTitle());
                        } else {
                            // Intermediate step: Load next page
                            // Handle POST params if present (basic stripping for now to prevent crash)
                            if (url.contains("|postParams=")) {
                                log("Warning: POST params detected but not fully supported in Test Activity: " + url);
                                url = url.split("\\|")[0];
                            }
                            loadPageAndParse(url);
                        }
                    })
                    .setPositiveButton("Close", null)
                    .show();
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

                        playVideo(url, selected.getTitle());
                    })
                    .setPositiveButton("Close", null)
                    .show();
        });
    }

    private void playVideo(String url, String title) {
        if (url == null)
            return;

        // INTERCEPT: If this is a download page, send to Sniffer instead of Player
        if (url.contains("/download/") || url.contains("/old/download/") || url.contains(".link")) {
            log("Redirecting to Sniffer (Download Page): " + url);
            handleWebViewFallback(url);
            return;
        }

        log("Playing: " + url);
        if (url.startsWith("http")) {
            android.content.Intent intent = new android.content.Intent(this,
                    com.omarflex5.ui.player.PlayerActivity.class);
            intent.putExtra(com.omarflex5.ui.player.PlayerActivity.EXTRA_VIDEO_URL, url);
            intent.putExtra(com.omarflex5.ui.player.PlayerActivity.EXTRA_VIDEO_TITLE,
                    title);
            startActivity(intent);
        } else {
            log("Invalid URL: " + url);
        }
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
        log("Fallback to WebView for: " + url);
        log("URL Hash Fragment: " + (url.contains("#") ? url.substring(url.indexOf("#")) : "MISSING"));

        runOnUiThread(() -> {
            // Determine strategy based on URL
            int strategy = SnifferActivity.STRATEGY_VIDEO;
            String customJs = null;

            // If Old Akwam, use the specialized strategy
            if (url.contains("ak.sv") || url.contains("akwam")) {
                log("Using OldAkwam strategy");
                // Custom JS for hash restoration + button polling
                customJs = new com.omarflex5.data.sniffer.strategy.OldAkwamStrategy(null).getCustomScript();
            }

            // Create intent for SnifferActivity
            // URL might contain pipes e.g. "http://...|Referer=..." which SnifferActivity
            // now handles internally
            android.content.Intent intent = SnifferActivity.createIntent(this, url, strategy);

            if (customJs != null) {
                intent.putExtra(SnifferActivity.EXTRA_CUSTOM_JS, customJs);
            }

            // Launch with result
            startActivityForResult(intent, REQUEST_SNIFFER);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SNIFFER) {
            if (resultCode == RESULT_OK && data != null) {
                String videoUrl = data.getStringExtra(SnifferActivity.RESULT_VIDEO_URL);

                if (videoUrl != null) {
                    log("SNIFF SUCCESS: " + videoUrl);

                    // Get headers from sniffer
                    @SuppressWarnings("unchecked")
                    Map<String, String> headers = (Map<String, String>) data.getSerializableExtra(
                            SnifferActivity.RESULT_HEADERS);

                    // Launch player
                    android.content.Intent playerIntent = new android.content.Intent(this,
                            com.omarflex5.ui.player.PlayerActivity.class);
                    playerIntent.putExtra(com.omarflex5.ui.player.PlayerActivity.EXTRA_VIDEO_URL, videoUrl);
                    playerIntent.putExtra(com.omarflex5.ui.player.PlayerActivity.EXTRA_VIDEO_TITLE, "Sniffed Video");

                    if (headers != null && !headers.isEmpty()) {
                        String userAgent = headers.get("User-Agent");
                        if (userAgent != null)
                            playerIntent.putExtra("EXTRA_USER_AGENT", userAgent);

                        String referer = headers.get("Referer");
                        if (referer != null)
                            playerIntent.putExtra("EXTRA_REFERER", referer);

                        String cookie = headers.get("Cookie");
                        if (cookie != null) {
                            playerIntent.putExtra("EXTRA_COOKIE", cookie);
                            log("Passing Cookie to Player: " + cookie.substring(0, Math.min(20, cookie.length()))
                                    + "...");

                            // SAVE COOKIES TO REPO FOR FUTURE REQUESTS (Fixes Re-Challenge Loop)
                            String currentServerName = (String) spinnerServers.getSelectedItem();
                            if (currentServerName != null) {
                                serverRepository.getServerByName(currentServerName, server -> {
                                    if (server != null) {
                                        scraperManager.saveCookies(server, cookie);
                                        log("Updated saved cookies for " + server.getName());
                                    }
                                });
                            }
                        }
                    }

                    startActivity(playerIntent);
                } else {
                    // Check for HTML result (CF bypass mode)
                    String html = data.getStringExtra(SnifferActivity.RESULT_HTML);
                    if (html != null) {
                        log("HTML extracted (" + html.length() + " chars)");
                    }
                }
            } else {
                String error = data != null ? data.getStringExtra("error") : "Unknown error";
                log("SNIFF ERROR: " + error);
            }
        }
    }
}
