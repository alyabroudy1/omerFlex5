package com.omarflex6.controller.base;

/**
 * Interface for controllers responsible for data management.
 * 
 * @param <T> The type of data being managed.
 */
public interface DataController<T> extends BaseController {
    void refreshData();

    void loadMoreData();

    interface DataCallback<T> {
        void onDataLoaded(T data);

        void onMoreDataLoaded(T data); // For pagination

        void onDataError(String error);
    }
}
