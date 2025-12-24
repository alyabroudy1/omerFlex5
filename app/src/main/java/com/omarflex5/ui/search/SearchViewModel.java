package com.omarflex5.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.omarflex5.data.search.UnifiedSearchService;

import java.util.List;

/**
 * ViewModel for search UI.
 * Exposes search state and handles user actions.
 */
public class SearchViewModel extends AndroidViewModel {

    private final UnifiedSearchService searchService;
    private final MutableLiveData<String> queryInput = new MutableLiveData<>("");

    public SearchViewModel(@NonNull Application application) {
        super(application);
        searchService = UnifiedSearchService.getInstance(application);
    }

    /**
     * Get the current search state.
     */
    public LiveData<UnifiedSearchService.SearchState> getSearchState() {
        return searchService.getSearchState();
    }

    /**
     * Get search results from current state.
     */
    public LiveData<List<UnifiedSearchService.SearchResult>> getResults() {
        return Transformations.map(searchService.getSearchState(), state -> {
            if (state != null) {
                return state.results;
            }
            return null;
        });
    }

    /**
     * Check if search is in progress.
     */
    public LiveData<Boolean> isLoading() {
        return Transformations.map(searchService.getSearchState(), state -> {
            if (state == null)
                return false;
            return state.status == UnifiedSearchService.SearchState.Status.LOADING ||
                    state.status == UnifiedSearchService.SearchState.Status.LOADING_MORE;
        });
    }

    /**
     * Check if there are pending CF servers.
     */
    public LiveData<Integer> getPendingServerCount() {
        return Transformations.map(searchService.getSearchState(), state -> {
            if (state == null)
                return 0;
            return state.pendingServers;
        });
    }

    /**
     * Get error message if any.
     */
    public LiveData<String> getError() {
        return Transformations.map(searchService.getSearchState(), state -> {
            if (state != null && state.status == UnifiedSearchService.SearchState.Status.ERROR) {
                return state.errorMessage;
            }
            return null;
        });
    }

    /**
     * Perform search.
     */
    public void search(String query) {
        search(query, null);
    }

    public void search(String query, UnifiedSearchService.MetadataContext context) {
        queryInput.setValue(query);
        searchService.search(query, context);
    }

    /**
     * Load more results (pagination or CF-protected servers).
     */
    public void loadMore() {
        searchService.processQueuedServers();
    }

    /**
     * Check if there are pending tasks (pagination or CF retry).
     * Used to show/hide "Load More" button.
     */
    public boolean hasPendingTasks() {
        return searchService.hasPendingTasks();
    }

    /**
     * Clear current search.
     */
    public void clearSearch() {
        queryInput.setValue("");
        searchService.clearSearch();
    }

    /**
     * Get current query.
     */
    public LiveData<String> getQuery() {
        return queryInput;
    }
}
