package com.omarflex5.temp.omerflex.view.viewConroller;

import android.content.Intent;
import android.graphics.drawable.Drawable;

import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.omerflex.entity.Movie;
import com.omerflex.service.BackgroundService;
import com.omerflex.view.CardPresenter;
import com.omerflex.view.listener.MovieItemViewClickedListener;

import com.omerflex.view.handler.ActivityResultHandler;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BaseFragmentController {

    private static final String TAG = "BaseFragmentController";

    protected BrowseSupportFragment mFragment;
    protected ArrayObjectAdapter mRowsAdapter;
    private ActivityResultHandler activityResultHandler;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private BackgroundService mBackgroundService;
    private MovieItemViewClickedListener movieItemViewClickedListener;


    public BaseFragmentController(BrowseSupportFragment fragment, ArrayObjectAdapter rowsAdapter, Drawable defaultBackground) {
        this.mFragment = fragment;
        this.mRowsAdapter = rowsAdapter;
        this.mBackgroundService = new BackgroundService(mFragment.getActivity());
        this.activityResultHandler = new ActivityResultHandler(fragment.getActivity());
        this.movieItemViewClickedListener = new MovieItemViewClickedListener(mFragment, mRowsAdapter);
        //fragment.getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        setupEventListeners();
    }

    protected void setupEventListeners() {
        mFragment.setOnItemViewClickedListener(movieItemViewClickedListener);
        mFragment.setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    // Abstract method to be implemented by subclasses for specific data loading.
    public abstract void loadData();

    // Method to add a new row of movies to the adapter.
    public void addMovieRow(HeaderItem header, List<Movie> movies) {
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());
        listRowAdapter.addAll(0, movies);
        mRowsAdapter.add(new ListRow(header, listRowAdapter));
    }

    // Method to update the background image.
    protected void updateBackground(String uri) {
        mBackgroundService.updateBackground(uri);
    }

    public void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        //activityResultHandler.handleResult(requestCode, resultCode, data, mRowsAdapter, null);
        movieItemViewClickedListener.handleOnActivityResult(requestCode, resultCode, data);
    }

    // A new Runnable object to perform the background update

    public void handleOnDetach(){
        // Clear the final Glide request
        if (mBackgroundService != null) {
            mBackgroundService.release();
        }
        executorService.shutdown();
        mFragment = null; // Nullify the reference
    }



    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Movie) {
                updateBackground(((Movie) item).getBackgroundImageUrl());
            }
        }
    }
}