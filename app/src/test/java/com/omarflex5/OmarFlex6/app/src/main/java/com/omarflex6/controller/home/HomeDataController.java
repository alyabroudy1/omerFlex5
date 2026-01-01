package com.omarflex6.controller.home;

import com.omarflex6.controller.base.AbstractBaseController;
import com.omarflex6.controller.base.DataController;
import com.omarflex6.data.model.Category;
import com.omarflex6.data.model.Movie;

import java.util.ArrayList;
import java.util.List;

public class HomeDataController extends AbstractBaseController implements DataController<List<Category>> {

    private final DataCallback<List<Category>> callback;

    public HomeDataController(DataCallback<List<Category>> callback) {
        this.callback = callback;
    }

    @Override
    public void onAttach() {
        super.onAttach();
        refreshData();
    }

    @Override
    public void refreshData() {
        // Simulate async data loading
        loadMockData(false);
    }

    @Override
    public void loadMoreData() {
        // Simulate async incremental loading
        loadMockData(true);
    }

    private void loadMockData(boolean isAppend) {
        List<Category> cats = new ArrayList<>();
        int startId = isAppend ? 5 : 0;
        for (int i = startId; i < startId + 3; i++) { // Load 3 more categories
            List<Movie> movies = new ArrayList<>();
            for (int j = 0; j < 10; j++) {
                movies.add(new Movie(
                        "Movie " + (j + 1) + " Cat " + (i + 1),
                        "This is a description for the movie. It is very interesting.",
                        "", "", "2024", "8.5"));
            }
            cats.add(new Category(String.valueOf(i), "Category " + (i + 1) + (isAppend ? " (More)" : ""), movies));
        }

        if (callback != null) {
            if (isAppend) {
                callback.onMoreDataLoaded(cats);
            } else {
                callback.onDataLoaded(cats);
            }
        }
    }
}
