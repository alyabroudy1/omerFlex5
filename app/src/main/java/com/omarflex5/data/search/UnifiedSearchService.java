package com.omarflex5.data.search;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.omarflex5.data.local.AppDatabase;
import com.omarflex5.data.local.dao.MediaSourceDao;
import com.omarflex5.data.local.entity.MediaSourceEntity;
import com.omarflex5.data.local.entity.ServerEntity;
import com.omarflex5.data.repository.MediaLocalRepository;
import com.omarflex5.data.repository.ServerRepository;
import com.omarflex5.data.scraper.BaseHtmlParser;
import com.omarflex5.data.scraper.ParserFactory;
import com.omarflex5.data.scraper.WebViewScraperManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified search service that coordinates all search operations.
 * 
 * Combines:
 * - Fast servers (direct HTTP with saved cookies)
 * - CF-protected servers (WebView scraping)
 * - Result deduplication
 * - Local DB storage
 */
public class UnifiedSearchService {

    private static final String TAG = "UnifiedSearch";
    private static final int PARALLEL_TIMEOUT_SECONDS = 15;

    private static volatile UnifiedSearchService INSTANCE;

    private final Context context;
    private final ServerRepository serverRepository;
    private final MediaLocalRepository mediaRepository;
    private final MediaSourceDao mediaSourceDao;
    private final WebViewScraperManager scraperManager;
    private final ExecutorService executor;

    // Search state
    private final MutableLiveData<SearchState> searchState = new MutableLiveData<>(SearchState.idle());
    private String currentQuery = null;

    // Track servers that failed Direct Search due to Cloudflare
    private final List<ServerEntity> lastFailedCfServers = new ArrayList<>();

    private UnifiedSearchService(Context context) {
        this.context = context.getApplicationContext();
        this.serverRepository = ServerRepository.getInstance(context);
        this.mediaRepository = MediaLocalRepository.getInstance(context);
        this.mediaSourceDao = AppDatabase.getInstance(context).mediaSourceDao();
        this.scraperManager = WebViewScraperManager.getInstance(context);
        this.executor = Executors.newFixedThreadPool(4);

        // Initialize WebView
        scraperManager.initialize();

        // Fix FaselHD URL and Pattern if needed (Migration for existing users)
        serverRepository.getServerByName("faselhd", server -> {
            if (server != null && server.getBaseUrl().contains("faselhds.care")) {
                serverRepository.updateBaseUrl("faselhd", "https://www.faselhds.biz");
                serverRepository.updateSearchUrlPattern("faselhd", "/?s={query}");
                Log.i(TAG, "Migrated FaselHD to .biz domain");
            }
        });

        // Sync with Firebase
        serverRepository.fetchRemoteConfigs();
    }

    public static UnifiedSearchService getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (UnifiedSearchService.class) {
                if (INSTANCE == null) {
                    INSTANCE = new UnifiedSearchService(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Perform a unified search across all enabled servers.
     * 
     * Hybrid Strategy:
     * 1. Try ALL servers via Direct HTTP (Fast Mode).
     * 2. If any fail with Cloudflare, mark them for Queue.
     * 3. If Fast Mode yields 0 results, Auto-Trigger Queue for failed servers.
     */
    public void search(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchState.postValue(SearchState.idle());
            return;
        }

        currentQuery = query.trim();
        searchState.postValue(SearchState.loading(currentQuery));
        lastFailedCfServers.clear(); // Clear previous session failures

        Log.d(TAG, "Starting search: " + currentQuery);

        executor.execute(() -> {
            try {
                // Get all searchable servers
                serverRepository.getSearchableServers(servers -> {
                    if (servers == null || servers.isEmpty()) {
                        searchState.postValue(SearchState.error(currentQuery, "No servers available"));
                        return;
                    }

                    // Strict Production Filter (Fasel Only) & Active Servers Check
                    List<ServerEntity> activeServers = new ArrayList<>();
                    for (ServerEntity server : servers) {
                        // Strict Production Filter
                        if (!server.getName().equals("faselhd")) {
                            if (server.isEnabled()) {
                                Log.i(TAG, "Skipping and Disabling non-production server: " + server.getName());
                                serverRepository.setServerEnabled(server.getId(), false);
                            }
                            continue; // Skip this server for current search
                        }

                        // Double check enabled state
                        if (server.isEnabled()) {
                            activeServers.add(server);
                        }
                    }

                    Log.d(TAG, "Starting Hybrid Search on " + activeServers.size() + " servers.");

                    // Execute Fast Search (Strict Mode: allowFallback=false)
                    // This puts all active servers into the "Fast" pool first.
                    List<SearchResult> allResults = searchFastServers(activeServers, currentQuery);

                    // Deduplicate results
                    List<SearchResult> deduped = deduplicateResults(allResults);

                    // Decision Time
                    if (deduped.isEmpty() && !lastFailedCfServers.isEmpty()) {
                        Log.i(TAG, "Fast search empty. Auto-triggering queue for " + lastFailedCfServers.size()
                                + " servers.");

                        // Copy list to avoid concurrent modification issues
                        List<ServerEntity> toQueue = new ArrayList<>();
                        synchronized (lastFailedCfServers) {
                            toQueue.addAll(lastFailedCfServers);
                        }

                        // Auto-queue logic
                        processNextQueuedServer(toQueue, 0, deduped, new ArrayList<>());

                    } else if (!lastFailedCfServers.isEmpty()) {
                        // We have results, but some servers failed. Allow "Load More".
                        searchState.postValue(SearchState.partial(currentQuery, deduped, lastFailedCfServers.size()));
                    } else {
                        // All good (or all failed with non-CF errors)
                        searchState.postValue(SearchState.complete(currentQuery, deduped));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                searchState.postValue(SearchState.error(currentQuery, e.getMessage()));
            }
        });
    }

    /**
     * Search fast servers in parallel.
     */
    private List<SearchResult> searchFastServers(List<ServerEntity> servers, String query) {
        List<SearchResult> allResults = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(servers.size());
        Object lock = new Object();

        for (ServerEntity server : servers) {
            executor.execute(() -> {
                try {
                    // Try to search FAST (allowFallback = false)
                    // This will fail with CLOUDFLARE_DETECTED if needed
                    List<SearchResult> results = searchSingleServer(server, query, false);
                    synchronized (lock) {
                        allResults.addAll(results);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Fast Search Ex: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "Parallel search interrupted");
        }

        return allResults;
    }

    /**
     * Search a single server using WebView scraper.
     */
    private List<SearchResult> searchSingleServer(ServerEntity server, String query, boolean allowFallback) {
        List<SearchResult> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        scraperManager.search(server, query, allowFallback, new WebViewScraperManager.ScraperCallback() {
            @Override
            public void onSuccess(String html, Map<String, String> cookies) {
                results.addAll(parseResults(server, html));
                serverRepository.recordSuccess(server);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                if ("CLOUDFLARE_DETECTED".equals(message)) {
                    Log.w(TAG, "Capturing CF Failure for: " + server.getName());
                    synchronized (lastFailedCfServers) {
                        lastFailedCfServers.add(server);
                    }
                } else {
                    Log.e(TAG, "Search failed on " + server.getName() + ": " + message);
                    serverRepository.recordFailure(server);
                }
                latch.countDown();
            }
        });

        try {
            latch.await(PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "Search timeout for " + server.getName());
        }

        return results;
    }

    /**
     * Process queued CF servers (triggered by user clicking "Load More").
     */
    public void processQueuedServers() {
        if (currentQuery == null)
            return;

        // Snapshot current results (from Fast phase) to use as base
        List<SearchResult> baseResults = getCurrentResults();
        searchState.postValue(SearchState.loadingMore(currentQuery, baseResults));

        // Use cached failed servers if available
        List<ServerEntity> toProcess = new ArrayList<>();
        synchronized (lastFailedCfServers) {
            if (!lastFailedCfServers.isEmpty()) {
                toProcess.addAll(lastFailedCfServers);
            }
        }

        if (!toProcess.isEmpty()) {
            Log.d(TAG, "Processing cached failed servers: " + toProcess.size());
            processNextQueuedServer(toProcess, 0, baseResults, new ArrayList<>());
        } else {
            // Fallback (edge case)
            serverRepository.getSearchableServers(servers -> {
                List<ServerEntity> fallbackQueue = new ArrayList<>();
                for (ServerEntity server : servers) {
                    if (server.isEnabled() && server.isRequiresWebView()) {
                        fallbackQueue.add(server);
                    }
                }
                if (fallbackQueue.isEmpty()) {
                    searchState.postValue(SearchState.complete(currentQuery, baseResults));
                } else {
                    processNextQueuedServer(fallbackQueue, 0, baseResults, new ArrayList<>());
                }
            });
        }
    }

    private void processNextQueuedServer(List<ServerEntity> servers, int index,
            List<SearchResult> baseResults, List<SearchResult> accumulated) {

        if (index >= servers.size()) {
            // All done - merge final results
            List<SearchResult> finalResults = new ArrayList<>(baseResults);
            finalResults.addAll(accumulated);
            List<SearchResult> deduped = deduplicateResults(finalResults);
            searchState.postValue(SearchState.complete(currentQuery, deduped));
            return;
        }

        ServerEntity server = servers.get(index);
        Log.d(TAG, "Processing QUEUED server: " + server.getName());

        // IN THE QUEUE: Allow Fallback = TRUE
        scraperManager.search(server, currentQuery, true, new WebViewScraperManager.ScraperCallback() {
            @Override
            public void onSuccess(String html, Map<String, String> cookies) {
                List<SearchResult> results = parseResults(server, html);
                accumulated.addAll(results);

                // Update progress
                int remaining = servers.size() - index - 1;

                // Construct current display list: Base + Accumulated So Far
                List<SearchResult> currentDisplay = new ArrayList<>(baseResults);
                currentDisplay.addAll(accumulated);

                searchState.postValue(SearchState.partial(currentQuery,
                        deduplicateResults(currentDisplay), remaining));

                // Process next
                processNextQueuedServer(servers, index + 1, baseResults, accumulated);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Queued Server " + server.getName() + " failed: " + message);
                // Continue with next server
                processNextQueuedServer(servers, index + 1, baseResults, accumulated);
            }
        });
    }

    /**
     * Parse HTML results from a server.
     */
    private List<SearchResult> parseResults(ServerEntity server, String html) {
        List<SearchResult> results = new ArrayList<>();

        try {
            BaseHtmlParser parser = ParserFactory.getParser(server.getName(), html, server.getBaseUrl());
            List<BaseHtmlParser.ParsedItem> items = parser.parseSearchResults();

            for (BaseHtmlParser.ParsedItem item : items) {
                SearchResult result = new SearchResult();
                result.title = item.getTitle();
                result.posterUrl = item.getPosterUrl();
                result.pageUrl = item.getPageUrl();
                result.type = item.getType() != null ? item.getType().name() : "FILM";
                result.year = item.getYear();
                result.matchKey = item.getMatchKey();
                result.serverId = server.getId();
                result.serverName = server.getName();
                result.serverLabel = server.getLabel();
                results.add(result);
            }

            Log.d(TAG, "Parsed " + results.size() + " results from " + server.getName());

        } catch (Exception e) {
            Log.e(TAG, "Parse error for " + server.getName() + ": " + e.getMessage());
        }

        return results;
    }

    /**
     * Deduplicate results by match key.
     * Keeps the first occurrence (from higher priority server).
     */
    private List<SearchResult> deduplicateResults(List<SearchResult> results) {
        Map<String, SearchResult> seen = new HashMap<>();
        List<SearchResult> deduped = new ArrayList<>();

        for (SearchResult result : results) {
            String key = result.matchKey;
            if (key == null || key.isEmpty()) {
                // No key - can't dedupe, include as-is
                deduped.add(result);
            } else if (!seen.containsKey(key)) {
                seen.put(key, result);
                deduped.add(result);
            } else {
                // Duplicate - add as alternative source
                SearchResult existing = seen.get(key);
                if (existing.alternativeSources == null) {
                    existing.alternativeSources = new ArrayList<>();
                }
                existing.alternativeSources.add(new SourceInfo(
                        result.serverId, result.serverName, result.serverLabel, result.pageUrl));
            }
        }

        return deduped;
    }

    private List<SearchResult> getCurrentResults() {
        SearchState state = searchState.getValue();
        if (state != null && state.results != null) {
            return new ArrayList<>(state.results);
        }
        return new ArrayList<>();
    }

    /**
     * Get current search state.
     */
    public LiveData<SearchState> getSearchState() {
        return searchState;
    }

    /**
     * Clear search.
     */
    public void clearSearch() {
        currentQuery = null;
        searchState.postValue(SearchState.idle());
    }

    // ==================== STATE & RESULT CLASSES ====================

    public static class SearchState {
        public enum Status {
            IDLE, LOADING, PARTIAL, LOADING_MORE, COMPLETE, ERROR
        }

        public final Status status;
        public final String query;
        public final List<SearchResult> results;
        public final int pendingServers;
        public final String errorMessage;

        private SearchState(Status status, String query, List<SearchResult> results,
                int pendingServers, String errorMessage) {
            this.status = status;
            this.query = query;
            this.results = results;
            this.pendingServers = pendingServers;
            this.errorMessage = errorMessage;
        }

        public static SearchState idle() {
            return new SearchState(Status.IDLE, null, new ArrayList<>(), 0, null);
        }

        public static SearchState loading(String query) {
            return new SearchState(Status.LOADING, query, new ArrayList<>(), 0, null);
        }

        public static SearchState partial(String query, List<SearchResult> results, int pending) {
            return new SearchState(Status.PARTIAL, query, results, pending, null);
        }

        public static SearchState loadingMore(String query, List<SearchResult> results) {
            return new SearchState(Status.LOADING_MORE, query, results, 0, null);
        }

        public static SearchState complete(String query, List<SearchResult> results) {
            return new SearchState(Status.COMPLETE, query, results, 0, null);
        }

        public static SearchState error(String query, String message) {
            return new SearchState(Status.ERROR, query, new ArrayList<>(), 0, message);
        }
    }

    public static class SearchResult {
        public String title;
        public String posterUrl;
        public String pageUrl;
        public String type;
        public Integer year;
        public String matchKey;
        public long serverId;
        public String serverName;
        public String serverLabel;
        public List<SourceInfo> alternativeSources;
    }

    public static class SourceInfo {
        public final long serverId;
        public final String serverName;
        public final String serverLabel;
        public final String pageUrl;

        public SourceInfo(long serverId, String serverName, String serverLabel, String pageUrl) {
            this.serverId = serverId;
            this.serverName = serverName;
            this.serverLabel = serverLabel;
            this.pageUrl = pageUrl;
        }
    }
}
