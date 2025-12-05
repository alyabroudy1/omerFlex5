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

    private UnifiedSearchService(Context context) {
        this.context = context.getApplicationContext();
        this.serverRepository = ServerRepository.getInstance(context);
        this.mediaRepository = MediaLocalRepository.getInstance(context);
        this.mediaSourceDao = AppDatabase.getInstance(context).mediaSourceDao();
        this.scraperManager = WebViewScraperManager.getInstance(context);
        this.executor = Executors.newFixedThreadPool(4);

        // Initialize WebView
        scraperManager.initialize();
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
     * Fast Phase: Query servers with valid cookies in parallel
     * Queued Phase: CF servers needing WebView are queued for user action
     */
    public void search(String query) {
        if (query == null || query.trim().isEmpty()) {
            searchState.postValue(SearchState.idle());
            return;
        }

        currentQuery = query.trim();
        searchState.postValue(SearchState.loading(currentQuery));

        Log.d(TAG, "Starting search: " + currentQuery);

        executor.execute(() -> {
            try {
                // Get all searchable servers
                serverRepository.getSearchableServers(servers -> {
                    if (servers == null || servers.isEmpty()) {
                        searchState.postValue(SearchState.error(currentQuery, "No servers available"));
                        return;
                    }

                    // Partition servers
                    List<ServerEntity> fastServers = new ArrayList<>();
                    List<ServerEntity> queuedServers = new ArrayList<>();

                    for (ServerEntity server : servers) {
                        if (!server.isRequiresWebView() || !server.needsCookieRefresh()) {
                            // Can query directly (no CF or has valid cookies)
                            fastServers.add(server);
                        } else {
                            // Needs WebView for CF bypass
                            queuedServers.add(server);
                        }
                    }

                    Log.d(TAG, "Fast servers: " + fastServers.size() +
                            ", Queued servers: " + queuedServers.size());

                    // Execute fast search
                    List<SearchResult> allResults = new ArrayList<>();

                    if (!fastServers.isEmpty()) {
                        List<SearchResult> fastResults = searchFastServers(fastServers, currentQuery);
                        allResults.addAll(fastResults);
                    }

                    // Deduplicate results
                    List<SearchResult> deduped = deduplicateResults(allResults);

                    // Post results
                    if (queuedServers.isEmpty()) {
                        searchState.postValue(SearchState.complete(currentQuery, deduped));
                    } else {
                        searchState.postValue(SearchState.partial(currentQuery, deduped, queuedServers.size()));
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                searchState.postValue(SearchState.error(currentQuery, e.getMessage()));
            }
        });
    }

    /**
     * Process queued CF servers (triggered by user clicking "Load More").
     */
    public void processQueuedServers() {
        if (currentQuery == null)
            return;

        searchState.postValue(SearchState.loadingMore(currentQuery, getCurrentResults()));

        serverRepository.getSearchableServers(servers -> {
            List<ServerEntity> queuedServers = new ArrayList<>();
            for (ServerEntity server : servers) {
                if (server.isRequiresWebView() && server.needsCookieRefresh()) {
                    queuedServers.add(server);
                }
            }

            if (queuedServers.isEmpty()) {
                return;
            }

            // Process each queued server sequentially via WebView
            processNextQueuedServer(queuedServers, 0, new ArrayList<>());
        });
    }

    private void processNextQueuedServer(List<ServerEntity> servers, int index,
            List<SearchResult> accumulated) {
        if (index >= servers.size()) {
            // All done - merge with existing results
            List<SearchResult> current = getCurrentResults();
            current.addAll(accumulated);
            List<SearchResult> deduped = deduplicateResults(current);
            searchState.postValue(SearchState.complete(currentQuery, deduped));
            return;
        }

        ServerEntity server = servers.get(index);
        Log.d(TAG, "Processing queued server: " + server.getName());

        scraperManager.search(server, currentQuery, new WebViewScraperManager.ScraperCallback() {
            @Override
            public void onSuccess(String html, Map<String, String> cookies) {
                List<SearchResult> results = parseResults(server, html);
                accumulated.addAll(results);

                // Update progress
                int remaining = servers.size() - index - 1;
                List<SearchResult> current = getCurrentResults();
                current.addAll(accumulated);
                searchState.postValue(SearchState.partial(currentQuery,
                        deduplicateResults(current), remaining));

                // Process next
                processNextQueuedServer(servers, index + 1, accumulated);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Server " + server.getName() + " failed: " + message);
                serverRepository.recordFailure(server);
                // Continue with next server
                processNextQueuedServer(servers, index + 1, accumulated);
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
                    List<SearchResult> results = searchSingleServer(server, query);
                    synchronized (lock) {
                        allResults.addAll(results);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error searching " + server.getName() + ": " + e.getMessage());
                    serverRepository.recordFailure(server);
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
    private List<SearchResult> searchSingleServer(ServerEntity server, String query) {
        List<SearchResult> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        scraperManager.search(server, query, new WebViewScraperManager.ScraperCallback() {
            @Override
            public void onSuccess(String html, Map<String, String> cookies) {
                results.addAll(parseResults(server, html));
                serverRepository.recordSuccess(server);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Search failed on " + server.getName() + ": " + message);
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
     * Parse HTML results from a server.
     */
    private List<SearchResult> parseResults(ServerEntity server, String html) {
        List<SearchResult> results = new ArrayList<>();

        try {
            BaseHtmlParser parser = ParserFactory.getParser(server.getName(), html);
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
