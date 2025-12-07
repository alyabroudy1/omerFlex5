package com.omarflex5.temp.omerflex.view.viewConroller;

import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Log;
import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieRepository;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.UpdateService;
import com.omerflex.view.CardPresenter;

import java.util.ArrayList;

public class MainFragmentController extends BaseFragmentController {

    private MovieRepository movieRepository;
    UpdateService updateService;
    public static String TAG = "MainFragmentController";
    

    public MainFragmentController(BrowseSupportFragment fragment, ArrayObjectAdapter rowsAdapter, Drawable defaultBackground) {
        super(fragment, rowsAdapter, defaultBackground);
        this.movieRepository = MovieRepository.getInstance(fragment.getActivity(), ((OmerFlexApplication) fragment.getActivity().getApplication()).getDatabase().movieDao());
        updateService = new UpdateService(mFragment);
    }

    @Override
    public void loadData() {
        Log.d(TAG, "loadData: starting");
        try {
            Log.d(TAG, "loadData: try to check for update");
            ServerConfigRepository.getInstance().checkForRemoteUpdates(updateService);
        } catch (Exception e) {
            Log.e(TAG, "checkForRemoteUpdates: " + e.getMessage());
        }

        ArrayList<String> categories = movieRepository.getHomepageCategories();
        categories.add("المحفوظات");
        categories.add("محفوظات القنوات");
        for (String category : categories) {
            HeaderItem header = new HeaderItem(categories.indexOf(category) + 1, category);
            addEmptyMovieRow(header);
        }

        // Phase 2: Fetch all content asynchronously and in parallel
        movieRepository.getHomepageMovies(false, this::onHomepageMoviesLoaded);
//        movieRepository.getWatchedMovies(this::onMoviesLoaded);
//        movieRepository.getWatchedChannels(this::onMoviesLoaded);
//        movieRepository.getHomepageChannels(this::onMoviesLoaded);


        // The rest of this is test code, you may want to remove it.
//        Movie movie = new Movie();
//        movie.setStudio(Movie.SERVER_FASELHD);
//        movie.setTitle("test title");
//        movie.setVideoUrl("https://www.w3schools.com/html/mov_bbb.mp4");
//        Util.openExoPlayer(movie, mFragment.getActivity(),false);
    }

    private void addEmptyMovieRow(HeaderItem header) {
        CardPresenter cardPresenter = new CardPresenter();
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
        ListRow listRow = new ListRow(header, listRowAdapter);
        mRowsAdapter.add(listRow);
    }

    private void onHomepageMoviesLoaded(String category, ArrayList<Movie> movieList) {
        if (movieList == null || movieList.isEmpty()) {
            Log.d(TAG, "Movie list for category '" + category + "' is empty or null. The row will remain empty.");
            return;
        }

        // Search for the pre-existing row to populate
        for (int i = 0; i < mRowsAdapter.size(); i++) {
            Object item = mRowsAdapter.get(i);
            if (item instanceof ListRow) {
                ListRow row = (ListRow) item;
                if (row.getHeaderItem() != null && row.getHeaderItem().getName().equals(category)) {
                    // Found the row, add the movies to its adapter
                    ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getAdapter();
                    adapter.addAll(adapter.size(), movieList);
                    Log.d(TAG, "Populated " + movieList.size() + " movies to existing category: " + category);
                    return; // Exit after populating
                }
            }
        }

        // Fallback: If no pre-existing row was found, create a new one.
        Log.w(TAG, "No pre-existing row found for category '" + category + "'. Creating a new one.");
        // Using the category hashcode as an ID for the header
        HeaderItem header = new HeaderItem(category.hashCode(), category);
        addMovieRow(header, movieList);
    }

    private void onMoviesLoaded(String category, ArrayList<Movie> movieList) {
        if (movieList == null) { // Watched list can be empty, so we still update to clear the row
            movieList = new ArrayList<>();
        }

        // Search for the pre-existing row to populate
        for (int i = 0; i < mRowsAdapter.size(); i++) {
            Object item = mRowsAdapter.get(i);
            if (item instanceof ListRow) {
                ListRow row = (ListRow) item;
                if (row.getHeaderItem() != null && row.getHeaderItem().getName().equals(category)) {
                    // Found the row, clear old items and add the new list
                    ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getAdapter();
                    adapter.clear();
                    adapter.addAll(0, movieList);
                    Log.d(TAG, "Populated " + movieList.size() + " movies to existing category: " + category);
                    return; // Exit after populating
                }
            }
        }

        // Fallback: If no pre-existing row was found, create a new one.
        Log.w(TAG, "No pre-existing row found for category '" + category + "'. Creating a new one.");
        HeaderItem header = new HeaderItem(category.hashCode(), category);
        addMovieRow(header, movieList);
    }

    private void onHomepageChannelsLoaded(String category, ArrayList<Movie> movieList) {
        if (movieList != null) {
            Log.d(TAG, "Fetched getHomepageChannels: " + movieList.size());
            HeaderItem header = new HeaderItem(1, category);
            addMovieRow(header, movieList);
        } else {
            Log.d(TAG, "getHomepageChannels: movieList not found.");
        }
    }


    @Override
    public void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        updateService.handleOnActivityResult(requestCode, resultCode, data);
    }

    public void handleOnRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        updateService.handleOnRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void handleOnDestroy() {
        updateService.handleOnDestroy();
    }

    public void loadSearchData(String query) {
        Log.d(TAG, "loadSearchData: " + query);
        movieRepository.getSearchMovies(query, this::onMoviesLoaded);
    }
}