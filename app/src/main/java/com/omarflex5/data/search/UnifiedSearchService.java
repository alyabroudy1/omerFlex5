package com.omarflex5.data.search;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.omarflex5.data.local.AppDatabase;
import com.omarflex5.data.local.dao.MediaSourceDao;
import com.omarflex5.data.local.entity.MediaSourceEntity;
import com.omarflex5.data.local.entity.ServerEntity;
import com.omarflex5.data.repository.MediaRepository;
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
    private final MediaRepository mediaRepository;
    private final MediaSourceDao mediaSourceDao;
    private final WebViewScraperManager scraperManager;
    private final ExecutorService executor;

    // Search state
    private final MutableLiveData<SearchState> searchState = new MutableLiveData<>(SearchState.idle());
    private String currentQuery = null;
    private MetadataContext currentContext = null;
    private java.lang.ref.WeakReference<android.app.Activity> currentActivityRef = null;

    // Track tasks that failed Direct Search due to Cloudflare
    private final List<SearchTask> lastFailedTasks = new ArrayList<>();

    // Queue for pagination (next page URLs to fetch on "Load More")
    private final List<SearchTask> paginationQueue = new ArrayList<>();

    // Queue for low-priority servers (priority > HIGH_PRIORITY_THRESHOLD)
    private final List<SearchTask> lowPriorityQueue = new ArrayList<>();

    // Only servers with basePriority <= this threshold get direct HTTP search
    // Priority 1-3: MyCima, FaselHD, ArabSeed = Direct HTTP
    // Priority 4+: Queue from start
    private static final int HIGH_PRIORITY_THRESHOLD = 3;

    private static class SearchTask {
        final ServerEntity server;
        final String url;

        SearchTask(ServerEntity server, String url) {
            this.server = server;
            this.url = url;
        }
    }

    private UnifiedSearchService(Context context) {
        this.context = context.getApplicationContext();
        this.serverRepository = ServerRepository.getInstance(context);
        this.mediaRepository = MediaRepository.getInstance(context);
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

        // Fix ArabSeed search URL pattern (Migration for existing users)
        // Site uses /find/?word= instead of /?s= to avoid server redirect
        serverRepository.getServerByName("arabseed", server -> {
            if (server != null && server.getSearchUrlPattern() != null
                    && server.getSearchUrlPattern().contains("?s=")) {
                serverRepository.updateSearchUrlPattern("arabseed", "/find/?word={query}");
                Log.i(TAG, "Migrated ArabSeed to /find/?word= pattern");
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
        search(query, null, null);
    }

    public void search(String query, MetadataContext context) {
        search(query, context, null);
    }

    public void search(String query, MetadataContext context, android.app.Activity activity) {
        if (query == null || query.trim().isEmpty()) {
            searchState.postValue(SearchState.idle());
            return;
        }

        currentQuery = query.trim();
        currentContext = context;
        currentActivityRef = activity != null ? new java.lang.ref.WeakReference<>(activity) : null;
        searchState.postValue(SearchState.loading(currentQuery));

        // Clear all queues for new search session
        lastFailedTasks.clear();
        paginationQueue.clear();
        lowPriorityQueue.clear();

        Log.d(TAG, "Starting search: " + currentQuery + (context != null ? " with context" : ""));

        executor.execute(() -> {
            try {
                // Get all searchable servers
                serverRepository.getSearchableServers(servers -> {
                    if (servers == null || servers.isEmpty()) {
                        Log.e(TAG, "Search failed: No servers found in DB query.");
                        searchState.postValue(SearchState.error(currentQuery, "No servers available"));
                        return;
                    }

                    Log.d(TAG, "Found " + servers.size() + " searchable servers in DB.");

                    // Partition servers by priority
                    List<ServerEntity> highPriorityServers = new ArrayList<>();
                    List<ServerEntity> lowPriorityServers = new ArrayList<>();

                    for (ServerEntity server : servers) {
                        if (!server.isEnabled())
                            continue;

                        if (server.getBasePriority() <= HIGH_PRIORITY_THRESHOLD) {
                            // Priority 1-3: Direct HTTP (MyCima, FaselHD, ArabSeed)
                            highPriorityServers.add(server);
                        } else {
                            // Priority 4+: Queue from start
                            lowPriorityServers.add(server);
                        }
                    }

                    Log.d(TAG, "Partitioned: " + highPriorityServers.size() + " high-priority (direct), "
                            + lowPriorityServers.size() + " low-priority (queued)");

                    // Generate tasks for high-priority servers (Fast Mode)
                    List<SearchTask> fastTasks = new ArrayList<>();
                    for (ServerEntity server : highPriorityServers) {
                        List<String> urls = ParserFactory.getSearchUrls(server, currentQuery);
                        for (String url : urls) {
                            fastTasks.add(new SearchTask(server, url));
                            Log.d(TAG, "[FAST] Added: " + server.getName() + " (priority " + server.getBasePriority()
                                    + ") -> " + url);
                        }
                    }

                    // Add low-priority servers to queue immediately
                    synchronized (lowPriorityQueue) {
                        for (ServerEntity server : lowPriorityServers) {
                            List<String> urls = ParserFactory.getSearchUrls(server, currentQuery);
                            for (String url : urls) {
                                lowPriorityQueue.add(new SearchTask(server, url));
                                Log.d(TAG, "[QUEUE] Added to lowPriorityQueue: " + server.getName() + " (priority "
                                        + server.getBasePriority() + ") -> " + url);
                            }
                        }
                    }

                    Log.d(TAG,
                            "Fast Mode: " + fastTasks.size() + " tasks, Queue: " + lowPriorityQueue.size() + " tasks");

                    // Execute Fast Search (Strict Mode: allowFallback=false)
                    List<SearchResult> allResults = searchFastTasks(fastTasks, context);

                    // Deduplicate results
                    List<SearchResult> deduped = deduplicateResults(allResults);

                    // Calculate total remaining queue tasks
                    int totalQueuedTasks = 0;
                    synchronized (paginationQueue) {
                        totalQueuedTasks += paginationQueue.size();
                    }
                    synchronized (lastFailedTasks) {
                        totalQueuedTasks += lastFailedTasks.size();
                    }
                    synchronized (lowPriorityQueue) {
                        totalQueuedTasks += lowPriorityQueue.size();
                    }

                    // Decision Time
                    if (deduped.isEmpty() && totalQueuedTasks > 0) {
                        Log.i(TAG, "Fast search empty. Auto-triggering queue for " + totalQueuedTasks + " tasks.");

                        // Build prioritized queue: pagination > cfRetry > lowPriority
                        List<SearchTask> toQueue = buildPrioritizedQueue();

                        // Auto-queue logic with recursive stop-on-result
                        processNextQueuedTaskRecursive(toQueue, 0, deduped, new ArrayList<>(), context, 0);

                    } else if (totalQueuedTasks > 0) {
                        // We have results, but more tasks remain. Allow "Load More".
                        searchState.postValue(SearchState.partial(currentQuery, deduped, totalQueuedTasks));
                    } else {
                        // All done - no more queued tasks
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
    private List<SearchResult> searchFastTasks(List<SearchTask> tasks, MetadataContext context) {
        List<SearchResult> allResults = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(tasks.size());
        Object lock = new Object();

        for (SearchTask task : tasks) {
            executor.execute(() -> {
                try {
                    // Try to search FAST (allowFallback = false)
                    List<SearchResult> results = searchSingleTask(task, false, context);
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
    private List<SearchResult> searchSingleTask(SearchTask task, boolean allowFallback, MetadataContext context) {
        List<SearchResult> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        android.app.Activity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        scraperManager.search(task.server, task.url, allowFallback, activity,
                new WebViewScraperManager.ScraperCallback() {
                    @Override
                    public void onSuccess(String html, Map<String, String> cookies) {
                        try {
                            results.addAll(parseResults(task.server, html, context));
                            serverRepository.recordSuccess(task.server);
                        } catch (Exception e) {
                            Log.e(TAG, "Parsing error in searchSingleTask", e);
                        } finally {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if ("CLOUDFLARE_DETECTED".equals(message)) {
                            Log.w(TAG, "Capturing CF Failure for task: " + task.url);
                            synchronized (lastFailedTasks) {
                                lastFailedTasks.add(task);
                            }
                        } else {
                            Log.e(TAG, "Search failed on " + task.server.getName() + ": " + message);
                            serverRepository.recordFailure(task.server);
                        }
                        latch.countDown();
                    }
                });

        try {
            latch.await(PARALLEL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.w(TAG, "Search timeout for " + task.url);
        }

        return results;
    }

    /**
     * Build a prioritized queue from all pending tasks.
     * Priority order: 1) Pagination (next pages), 2) CF Retry, 3) Low Priority
     * servers
     */
    private List<SearchTask> buildPrioritizedQueue() {
        List<SearchTask> prioritized = new ArrayList<>();
        int paginationCount = 0, cfRetryCount = 0, lowPriorityCount = 0;

        // Priority 1: Pagination tasks (next pages from successful searches)
        synchronized (paginationQueue) {
            paginationCount = paginationQueue.size();
            for (SearchTask task : paginationQueue) {
                Log.d(TAG, "[BUILD_QUEUE] Priority 1 - Pagination: " + task.server.getName() + " -> " + task.url);
            }
            prioritized.addAll(paginationQueue);
            paginationQueue.clear();
        }

        // Priority 2: CF retry tasks (high-priority servers that failed with
        // Cloudflare)
        synchronized (lastFailedTasks) {
            cfRetryCount = lastFailedTasks.size();
            for (SearchTask task : lastFailedTasks) {
                Log.d(TAG, "[BUILD_QUEUE] Priority 2 - CF Retry: " + task.server.getName() + " -> " + task.url);
            }
            prioritized.addAll(lastFailedTasks);
            lastFailedTasks.clear();
        }

        // Priority 3: Low-priority servers (priority 4+)
        synchronized (lowPriorityQueue) {
            lowPriorityCount = lowPriorityQueue.size();
            for (SearchTask task : lowPriorityQueue) {
                Log.d(TAG, "[BUILD_QUEUE] Priority 3 - Low Priority: " + task.server.getName() + " (priority "
                        + task.server.getBasePriority() + ") -> " + task.url);
            }
            prioritized.addAll(lowPriorityQueue);
            lowPriorityQueue.clear();
        }

        Log.i(TAG, "Built prioritized queue: " + paginationCount + " pagination + " + cfRetryCount + " CF retry + "
                + lowPriorityCount + " low-priority = " + prioritized.size() + " total");
        return prioritized;
    }

    /**
     * Process queued servers (triggered by user clicking "Load More").
     * Uses recursive stop-on-result logic.
     */
    public void processQueuedServers() {
        if (currentQuery == null)
            return;

        // Snapshot current results (from Fast phase) to use as base
        List<SearchResult> baseResults = getCurrentResults();
        searchState.postValue(SearchState.loadingMore(currentQuery, baseResults));

        // Build prioritized queue
        List<SearchTask> toProcess = buildPrioritizedQueue();

        if (!toProcess.isEmpty()) {
            // Start recursive processing with stop-on-result
            processNextQueuedTaskRecursive(toProcess, 0, baseResults, new ArrayList<>(), currentContext, 0);
        } else {
            // No more tasks - mark as complete
            Log.d(TAG, "No more tasks to process");
            searchState.postValue(SearchState.complete(currentQuery, baseResults));
        }
    }

    /**
     * Check if there are pending tasks (pagination, CF retry, or low-priority).
     * Used by UI to show/hide "Load More" button.
     */
    public boolean hasPendingTasks() {
        synchronized (paginationQueue) {
            if (!paginationQueue.isEmpty())
                return true;
        }
        synchronized (lastFailedTasks) {
            if (!lastFailedTasks.isEmpty())
                return true;
        }
        synchronized (lowPriorityQueue) {
            return !lowPriorityQueue.isEmpty();
        }
    }

    /**
     * Get total count of pending tasks.
     */
    public int getPendingTaskCount() {
        int count = 0;
        synchronized (paginationQueue) {
            count += paginationQueue.size();
        }
        synchronized (lastFailedTasks) {
            count += lastFailedTasks.size();
        }
        synchronized (lowPriorityQueue) {
            count += lowPriorityQueue.size();
        }
        return count;
    }

    /**
     * Process queued tasks recursively.
     * STOPS as soon as newResultsThisRound >= 1 (user must click "Load More" to
     * continue).
     * 
     * @param tasks               List of tasks to process
     * @param index               Current task index
     * @param baseResults         Results from previous phases
     * @param accumulated         Results accumulated in this round
     * @param context             Metadata context
     * @param newResultsThisRound Count of new results found in this round
     */
    private void processNextQueuedTaskRecursive(List<SearchTask> tasks, int index,
            List<SearchResult> baseResults, List<SearchResult> accumulated,
            MetadataContext context, int newResultsThisRound) {

        // Check if we found results this round - STOP and wait for "Load More"
        if (newResultsThisRound > 0) {
            List<SearchResult> currentDisplay = new ArrayList<>(baseResults);
            currentDisplay.addAll(accumulated);
            List<SearchResult> deduped = deduplicateResults(currentDisplay);

            // Put remaining tasks back into lowPriorityQueue for "Load More"
            int tasksAddedBack = 0;
            if (index < tasks.size()) {
                synchronized (lowPriorityQueue) {
                    for (int i = index; i < tasks.size(); i++) {
                        lowPriorityQueue.add(tasks.get(i));
                        tasksAddedBack++;
                    }
                }
            }

            // Count remaining = tasks we just added back (now in getPendingTaskCount)
            int remaining = getPendingTaskCount();
            Log.i(TAG, "[STOP] Found " + newResultsThisRound + " results. Added " + tasksAddedBack
                    + " tasks back to queue. " + remaining + " total remaining.");
            searchState.postValue(SearchState.partial(currentQuery, deduped, remaining));
            return;
        }

        // All tasks processed without finding results
        if (index >= tasks.size()) {
            List<SearchResult> finalResults = new ArrayList<>(baseResults);
            finalResults.addAll(accumulated);
            List<SearchResult> deduped = deduplicateResults(finalResults);

            // Check if there are more tasks in other queues
            int remaining = getPendingTaskCount();
            if (remaining > 0) {
                searchState.postValue(SearchState.partial(currentQuery, deduped, remaining));
            } else {
                searchState.postValue(SearchState.complete(currentQuery, deduped));
            }
            return;
        }

        SearchTask task = tasks.get(index);
        Log.i(TAG, "[EXECUTE] Task [" + (index + 1) + "/" + tasks.size() + "] Server: " + task.server.getName()
                + " (priority " + task.server.getBasePriority() + ") URL: " + task.url);

        // IN THE QUEUE: Allow Fallback = TRUE (use WebView if needed)
        android.app.Activity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        scraperManager.search(task.server, task.url, true, activity, new WebViewScraperManager.ScraperCallback() {
            @Override
            public void onSuccess(String html, Map<String, String> cookies) {
                executor.execute(() -> {
                    try {
                        List<SearchResult> results = parseResults(task.server, html, context);
                        int newResults = results.size();
                        accumulated.addAll(results);

                        Log.d(TAG, "Task yielded " + newResults + " results from " + task.server.getName());

                        // Construct current display list for progress update
                        List<SearchResult> currentDisplay = new ArrayList<>(baseResults);
                        currentDisplay.addAll(accumulated);
                        int remaining = tasks.size() - index - 1;
                        searchState.postValue(SearchState.partial(currentQuery,
                                deduplicateResults(currentDisplay), remaining));

                        // Continue to next task with updated newResultsThisRound
                        processNextQueuedTaskRecursive(tasks, index + 1, baseResults, accumulated,
                                context, newResultsThisRound + newResults);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in background search processing", e);
                        // Continue to next task on error
                        processNextQueuedTaskRecursive(tasks, index + 1, baseResults, accumulated,
                                context, newResultsThisRound);
                    }
                });
            }

            @Override
            public void onError(String message) {
                executor.execute(() -> {
                    Log.e(TAG, "Queued task failed (" + task.url + "): " + message);
                    // Continue with next task on failure
                    processNextQueuedTaskRecursive(tasks, index + 1, baseResults, accumulated,
                            context, newResultsThisRound);
                });
            }
        });
    }

    // Keep the old method for backward compatibility (unused, can be removed later)
    @SuppressWarnings("unused")
    private void processNextQueuedTask(List<SearchTask> tasks, int index,
            List<SearchResult> baseResults, List<SearchResult> accumulated, MetadataContext context) {
        // Delegate to new recursive method with stop-on-result disabled (0 threshold
        // means never stop)
        processNextQueuedTaskRecursive(tasks, index, baseResults, accumulated, context, -1);
    }

    /**
     * Parse HTML results from a server.
     * Now uses parseSearchResultsWithPagination to extract and queue next page
     * URLs.
     */
    private List<SearchResult> parseResults(ServerEntity server, String html, MetadataContext context) {
        List<SearchResult> results = new ArrayList<>();

        try {
            BaseHtmlParser parser = ParserFactory.getParser(server.getName(), html, server.getBaseUrl());

            // Use pagination-aware parsing
            BaseHtmlParser.ParsedSearchResult parsedResult = parser.parseSearchResultsWithPagination();
            List<BaseHtmlParser.ParsedItem> items = parsedResult.items;

            // Queue next page URL if available
            if (parsedResult.hasNextPage()) {
                synchronized (paginationQueue) {
                    paginationQueue.add(new SearchTask(server, parsedResult.nextPageUrl));
                    Log.d(TAG, "Queued next page for " + server.getName() + ": " + parsedResult.nextPageUrl);
                }
            }

            // Enrich items with context if available
            if (context != null) {
                for (BaseHtmlParser.ParsedItem item : items) {
                    if (context.description != null)
                        item.setDescription(context.description);
                    if (context.rating != null)
                        item.setRating(context.rating);
                    if (context.year != null)
                        item.setYear(context.year);
                    if (context.trailerUrl != null)
                        item.setTrailerUrl(context.trailerUrl);
                    if (context.categories != null && !context.categories.isEmpty())
                        item.setCategories(context.categories);
                    if (context.tmdbId != null)
                        item.setTmdbId(context.tmdbId);
                }
            }

            // AGGRESSIVE SYNC: Save all items to DB immediately and link to watch progress
            mediaRepository.syncSearchResults(items, server.getId());

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
                result.categories = item.getCategories();
                result.mediaId = item.getMediaId(); // Propagate Media ID
                results.add(result);
            }

            Log.d(TAG, "Parsed " + results.size() + " results from " + server.getName() +
                    (parsedResult.hasNextPage() ? " (has more pages)" : " (last page)"));

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
        public List<String> categories;
        public List<SourceInfo> alternativeSources;
        public long mediaId = -1;
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

    public static class MetadataContext {
        public String description;
        public Float rating;
        public Integer year;
        public String trailerUrl;
        public List<String> categories;
        public Integer tmdbId;
    }
}
