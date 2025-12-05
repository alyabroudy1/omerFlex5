package com.omarflex5.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.omarflex5.data.local.AppDatabase;
import com.omarflex5.data.local.dao.SearchQueueDao;
import com.omarflex5.data.local.dao.ServerDao;
import com.omarflex5.data.local.entity.SearchQueueEntity;
import com.omarflex5.data.local.entity.SearchQueueStatus;
import com.omarflex5.data.local.entity.ServerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for search operations with queue management.
 * 
 * Handles:
 * - Fast server searches (direct HTTP)
 * - CF-protected server queueing
 * - Search state management
 */
public class SearchRepository {

    private static final String TAG = "SearchRepository";
    private static volatile SearchRepository INSTANCE;

    private final ServerDao serverDao;
    private final SearchQueueDao searchQueueDao;
    private final ServerRepository serverRepository;
    private final ExecutorService executor;

    // Current search state
    private final MutableLiveData<SearchState> searchState = new MutableLiveData<>();

    private SearchRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        serverDao = db.serverDao();
        searchQueueDao = db.searchQueueDao();
        serverRepository = ServerRepository.getInstance(context);
        executor = Executors.newFixedThreadPool(4);
    }

    public static SearchRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SearchRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SearchRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Start a search across all searchable servers.
     * Fast servers are queried immediately.
     * CF-protected servers with expired cookies are queued.
     */
    public void search(String query, SearchCallback callback) {
        executor.execute(() -> {
            try {
                searchState.postValue(SearchState.loading(query));

                List<ServerEntity> servers = serverDao.getSearchableByPriority();
                List<ServerEntity> fastServers = new ArrayList<>();
                List<ServerEntity> queuedServers = new ArrayList<>();

                // Partition servers
                for (ServerEntity server : servers) {
                    if (!server.isRequiresWebView()) {
                        // No CF protection - can query directly
                        fastServers.add(server);
                    } else if (!server.needsCookieRefresh()) {
                        // Has valid CF cookies - can query with cookies
                        fastServers.add(server);
                    } else {
                        // Needs WebView - queue for later
                        queuedServers.add(server);
                        addToQueue(query, server.getId());
                    }
                }

                Log.d(TAG, "Search '" + query + "': " + fastServers.size() + " fast servers, "
                        + queuedServers.size() + " queued");

                // Update state with pending count
                int pendingCount = queuedServers.size();
                searchState.postValue(SearchState.partial(query, new ArrayList<>(), pendingCount));

                // Query fast servers
                for (ServerEntity server : fastServers) {
                    // TODO: Implement actual server search in Phase 4
                    // For now, just log
                    Log.d(TAG, "Would search: " + server.getName() + " for '" + query + "'");
                }

                callback.onSearchStarted(fastServers.size(), queuedServers.size());

            } catch (Exception e) {
                Log.e(TAG, "Search error: " + e.getMessage());
                searchState.postValue(SearchState.error(query, e.getMessage()));
                callback.onSearchError(e.getMessage());
            }
        });
    }

    /**
     * Process queued servers for a query (triggered by user clicking "Load More").
     */
    public void processQueue(String query, QueueProcessCallback callback) {
        executor.execute(() -> {
            List<SearchQueueEntity> pending = searchQueueDao.getByQueryAndStatus(query, SearchQueueStatus.PENDING);

            Log.d(TAG, "Processing " + pending.size() + " queued searches for '" + query + "'");

            for (SearchQueueEntity item : pending) {
                try {
                    // Mark as in progress
                    searchQueueDao.updateStatus(item.getId(), SearchQueueStatus.IN_PROGRESS,
                            System.currentTimeMillis());

                    ServerEntity server = serverDao.getById(item.getServerId());
                    if (server == null) {
                        searchQueueDao.markFailed(item.getId(), "Server not found",
                                System.currentTimeMillis());
                        continue;
                    }

                    // TODO: In Phase 4, this will trigger WebView scraping
                    // For now, just mark as done with 0 results
                    Log.d(TAG, "Would WebView search: " + server.getName() + " for '" + query + "'");

                    callback.onServerProcessing(server);

                    // Simulate processing (will be replaced with actual WebView scraping)
                    searchQueueDao.markDone(item.getId(), SearchQueueStatus.DONE, 0,
                            System.currentTimeMillis());

                    callback.onServerCompleted(server, 0);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing queue item: " + e.getMessage());
                    searchQueueDao.markFailed(item.getId(), e.getMessage(),
                            System.currentTimeMillis());
                    callback.onServerFailed(item.getServerId(), e.getMessage());
                }
            }

            callback.onQueueCompleted();
        });
    }

    /**
     * Get pending queue count for a query.
     */
    public LiveData<Integer> getPendingCount(String query) {
        return searchQueueDao.getPendingCountForQueryLive(query);
    }

    /**
     * Get current search state.
     */
    public LiveData<SearchState> getSearchState() {
        return searchState;
    }

    /**
     * Clear old queue entries.
     */
    public void clearOldQueue(long olderThanMillis) {
        executor.execute(() -> {
            long threshold = System.currentTimeMillis() - olderThanMillis;
            searchQueueDao.deleteOlderThan(threshold);
        });
    }

    // ==================== PRIVATE METHODS ====================

    private void addToQueue(String query, long serverId) {
        SearchQueueEntity item = new SearchQueueEntity();
        item.setQuery(query);
        item.setServerId(serverId);
        item.setStatus(SearchQueueStatus.PENDING);
        item.setCreatedAt(System.currentTimeMillis());
        searchQueueDao.insert(item);
    }

    // ==================== STATE CLASSES ====================

    /**
     * Represents the current state of a search operation.
     */
    public static class SearchState {
        public enum State {
            IDLE, LOADING, PARTIAL, COMPLETE, ERROR
        }

        private final State state;
        private final String query;
        private final List<SearchResult> results;
        private final int pendingServersCount;
        private final String errorMessage;

        private SearchState(State state, String query, List<SearchResult> results,
                int pendingServersCount, String errorMessage) {
            this.state = state;
            this.query = query;
            this.results = results;
            this.pendingServersCount = pendingServersCount;
            this.errorMessage = errorMessage;
        }

        public static SearchState idle() {
            return new SearchState(State.IDLE, null, new ArrayList<>(), 0, null);
        }

        public static SearchState loading(String query) {
            return new SearchState(State.LOADING, query, new ArrayList<>(), 0, null);
        }

        public static SearchState partial(String query, List<SearchResult> results, int pendingCount) {
            return new SearchState(State.PARTIAL, query, results, pendingCount, null);
        }

        public static SearchState complete(String query, List<SearchResult> results) {
            return new SearchState(State.COMPLETE, query, results, 0, null);
        }

        public static SearchState error(String query, String errorMessage) {
            return new SearchState(State.ERROR, query, new ArrayList<>(), 0, errorMessage);
        }

        public State getState() {
            return state;
        }

        public String getQuery() {
            return query;
        }

        public List<SearchResult> getResults() {
            return results;
        }

        public int getPendingServersCount() {
            return pendingServersCount;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Represents a single search result item.
     */
    public static class SearchResult {
        private final long mediaId; // Local DB ID (if exists)
        private final String title;
        private final String posterUrl;
        private final String type; // "SERIES", "FILM", "SEASON", "EPISODE"
        private final long serverId;
        private final String externalUrl;

        public SearchResult(long mediaId, String title, String posterUrl, String type,
                long serverId, String externalUrl) {
            this.mediaId = mediaId;
            this.title = title;
            this.posterUrl = posterUrl;
            this.type = type;
            this.serverId = serverId;
            this.externalUrl = externalUrl;
        }

        public long getMediaId() {
            return mediaId;
        }

        public String getTitle() {
            return title;
        }

        public String getPosterUrl() {
            return posterUrl;
        }

        public String getType() {
            return type;
        }

        public long getServerId() {
            return serverId;
        }

        public String getExternalUrl() {
            return externalUrl;
        }
    }

    // ==================== CALLBACKS ====================

    public interface SearchCallback {
        void onSearchStarted(int fastServerCount, int queuedServerCount);

        void onSearchError(String message);
    }

    public interface QueueProcessCallback {
        void onServerProcessing(ServerEntity server);

        void onServerCompleted(ServerEntity server, int resultCount);

        void onServerFailed(long serverId, String error);

        void onQueueCompleted();
    }
}
