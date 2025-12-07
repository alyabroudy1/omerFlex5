package com.omarflex5.temp.omerflex.view.viewConroller;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;

import com.omerflex.OmerFlexApplication;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieRepository;
import com.omerflex.entity.MovieType;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.view.VideoDetailsFragment;
import com.omerflex.view.handler.ActivityResultHandler;
import com.omerflex.viewmodel.SharedViewModel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoDetailsFragmentController {
    private static final String TAG = "VideoDetailsController";

    private static final int ACTION_WATCH = 1;
    private static final int ACTION_WATCH_TRAILER = 2;
    private VideoDetailsFragment fragment;
    private ArrayObjectAdapter mAdapter;
    private DetailsOverviewRow row;
    private Movie mSelectedMovie;
    private MovieRepository movieRepository;
    private ListRow listRow;
    private ActivityResultHandler activityResultHandler;
    private SharedViewModel sharedViewModel;
    private Integer selectedItemIndex = -1;
    private Integer selectedRowIndex = -1;

    public VideoDetailsFragmentController(VideoDetailsFragment fragment, ArrayObjectAdapter adapter, DetailsOverviewRow row, Movie movie, ListRow listRow, SharedViewModel sharedViewModel) {
        this.fragment = fragment;
        this.mAdapter = adapter;
        this.row = row;
        this.mSelectedMovie = movie;
        this.movieRepository = MovieRepository.getInstance(fragment.getActivity(), ((OmerFlexApplication) fragment.getActivity().getApplication()).getDatabase().movieDao());
        this.listRow = listRow;
        this.activityResultHandler = new ActivityResultHandler(fragment.getActivity());
        this.sharedViewModel = sharedViewModel;
    }

    public void fetchDetails() {
        fragment.showProgressDialog(true);
        selectedRowIndex = mAdapter.indexOf(listRow); // track sublist adapter
        Log.d(TAG, "fetchDetails: "+ mSelectedMovie.getFetch());
        Log.d(TAG, "fetchDetails: selectedRowIndex: "+ selectedRowIndex);
        Log.d(TAG, "fetchDetails: mAdapter size: "+ mAdapter.size());
        Log.d(TAG, "fetchDetails: mAdapter size: "+ mAdapter.get(0).getClass());
        Log.d(TAG, "fetchDetails: mAdapter size: "+ mAdapter.get(1).getClass());

        if (mSelectedMovie.getFetch() == Movie.NO_FETCH_MOVIE_AT_START){
            Log.d(TAG, "fetchDetails: NO_FETCH_MOVIE_AT_START "+ mSelectedMovie.getFetch());
            updateUI(mSelectedMovie);
            return;
        }
        movieRepository.fetchMovieDetails(mSelectedMovie, new ServerInterface.ActivityCallback<Movie>() {
            @Override
            public void onSuccess(Movie fetchedMovie, String title) {
//                sharedViewModel.updateMovie(fetchedMovie);
                Log.d(TAG, "onSuccess: fetchedMovie: "+ fetchedMovie);
//                Log.d(TAG, "onSuccess: sublist: "+ fetchedMovie.getSubList());
                updateUI(fetchedMovie);
            }

            @Override
            public void onInvalidCookie(Movie result, String title) {
                Log.d(TAG, "onInvalidCookie: VideoDetailsFragmentController: " + title);
                // Handle error, maybe show a toast
                fragment.hideProgressDialog(true, "جاري التحديث٠٠٠");
//                result.setFetch(Movie.REQUEST_CODE_EXOPLAYER); server should decide the fetch state
                Util.openBrowserIntent(result, fragment, false, true, true, selectedRowIndex, selectedItemIndex);
            }

            @Override
            public void onInvalidLink(Movie result) {
                // Handle error
                fragment.hideProgressDialog(true, "حدث خطأ...");
            }

            @Override
            public void onInvalidLink(String message) {
                // Handle error
                fragment.hideProgressDialog(true, message);
            }
        });
    }

    private void updateUI(Movie fetchedMovie) {
//        listRowAdapter.clear();
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) listRow.getAdapter();
        if (fetchedMovie.getSubList() != null) {
            adapter.addAll(adapter.size(), fetchedMovie.getSubList());
        }

//        Log.d(TAG, "updateUI: fetchedMovie sub:"+fetchedMovie.getSubList().size());
//        Log.d(TAG, "updateUI: fetchedMovie sub:"+fetchedMovie.getSubList());
        // Resume watching logic
        resumeWatching(fetchedMovie);

        fragment.hideProgressDialog(true, null);
        evaluateWatchAction(fetchedMovie);
    }

    private void resumeWatching(Movie fetchedMovie) {
        if (fetchedMovie.getType() == MovieType.SERIES || fetchedMovie.getType() == MovieType.SEASON) {
            List<Movie> subList = fetchedMovie.getSubList();
            if (subList != null && !subList.isEmpty()) {
                Movie lastWatchedItem = subList.get(0);
                java.util.Date latestDate = null;

                for (Movie item : subList) {
                    if (item.getPlayedTime() > 0 && item.getUpdatedAt() != null) {
                        if (latestDate == null || item.getUpdatedAt().after(latestDate)) {
                            latestDate = item.getUpdatedAt();
                            lastWatchedItem = item;
                        }
                    }
                }

                if (lastWatchedItem != null) {
                    final int itemIndex = subList.indexOf(lastWatchedItem);
                    if (itemIndex != -1) {
                        Log.d(TAG, "Last watched item found: " + lastWatchedItem.getTitle() + " at index " + itemIndex);

//                        int rowIndex = -1;
//                        ArrayObjectAdapter adapter = (ArrayObjectAdapter) listRow.getAdapter();
//                        for (int i = 0; i < adapter.size(); i++) {
//                            Object rowObject = adapter.get(i);
//                            if (rowObject instanceof ListRow) {
//                                ListRow row = (ListRow) rowObject;
//                                if (row.getAdapter() == adapter) {
//                                    rowIndex = i;
//                                    break;
//                                }
//                            }
//                        }

                        selectItemOnRowListAdapter(selectedRowIndex, itemIndex);
                    }
                }
            }
        }
    }

    private void selectItemOnRowListAdapter(int rowIndex, int itemIndex) {
        Log.d(TAG, "selectItemOnRowListAdapter: rowIndex: "+rowIndex + ", "+ itemIndex);
        if (rowIndex != -1) {
            selectedItemIndex = itemIndex;
            selectedRowIndex = rowIndex;

            final int finalRowIndex = rowIndex;
            RowsSupportFragment rowsFrag = fragment.getRowsSupportFragment();

            // Request focus on the RowsSupportFragment's view
            rowsFrag.getView().requestFocus();

            rowsFrag.getView().post(() -> {
                Log.d(TAG, "Step1: select row " + finalRowIndex);
                rowsFrag.setSelectedPosition(finalRowIndex, true);

                rowsFrag.getView().postDelayed(() -> {
                    Log.d(TAG, "Step2: select item via SelectItemViewHolderTask, itemIndex=" + itemIndex);
                    rowsFrag.setSelectedPosition(
                            finalRowIndex,
                            true,
                            new ListRowPresenter.SelectItemViewHolderTask(itemIndex) {
                                @Override
                                public void run(Presenter.ViewHolder holder) {
                                    Log.d(TAG, "Task.run(): holder=" + (holder != null ? holder.getClass().getSimpleName() : "null"));
                                    if (holder instanceof ListRowPresenter.ViewHolder) {
                                        ListRowPresenter.ViewHolder lvh = (ListRowPresenter.ViewHolder) holder;
                                        HorizontalGridView grid = lvh.getGridView();
                                        Log.d(TAG, "Before grid select: attached=" + grid.isAttachedToWindow()
                                                + " childCount=" + grid.getChildCount()
                                                + " adapterCount=" + (grid.getAdapter() != null ? grid.getAdapter().getItemCount() : -1)
                                                + " hasFocus=" + grid.hasFocus());

                                        // Set the selected position
                                        grid.setSelectedPositionSmooth(itemIndex);
                                        boolean focusResult = grid.requestFocus();
                                        Log.d(TAG, "Selection applied: requestFocus()=" + focusResult);
                                    } else {
                                        Log.w(TAG, "Task.run(): not a ListRow ViewHolder; cannot select item.");
                                    }
                                }
                            }
                    );
                }, 200); // Delay for UI to settle
            });
        } else {
            Log.w(TAG, "Could not find ListRow to select last watched item.");
        }
    }

    private void updateOverview(Movie movie) {
        mSelectedMovie.setTitle(movie.getTitle());
        mSelectedMovie.setDescription(movie.getDescription());
        mSelectedMovie.setBackgroundImageUrl(movie.getBackgroundImageUrl());

        if (fragment != null) {
            fragment.updateOverviewUI(mSelectedMovie);
        }
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        activityResultHandler.handleResult(requestCode, resultCode, data, mAdapter, fragment);
    }

    public void evaluateWatchAction(Movie movie) {
        if (movie == null) return;
        ArrayObjectAdapter actionAdapter = new ArrayObjectAdapter();

        Movie firstSubMovie = movie;
        if (!movie.getSubList().isEmpty()){
            firstSubMovie = movie.getSubList().get(0);
        }

        boolean watchCond = firstSubMovie.getState() == Movie.RESOLUTION_STATE || firstSubMovie.getState() == Movie.VIDEO_STATE;
//        boolean watchOmarCond = firstSubMovie.getStudio().equals(Movie.SERVER_OMAR) && firstSubMovie.getState() > Movie.ITEM_STATE;
//        boolean watchOmarCond = result.getStudio().equals(Movie.SERVER_OMAR) && firstSubMovie.getState() > Movie.ITEM_STATE;
        Log.d(TAG, "evaluateWatchAction: " + watchCond + ", "+ firstSubMovie);
//        if (watchCond || watchOmarCond) {
        if (watchCond) {
            Log.d(TAG, "onSuccess: watchCond");
            actionAdapter.add(new Action(ACTION_WATCH, fragment.getResources().getString(R.string.watch)));
        }
        if (movie.getTrailerUrl() != null && movie.getTrailerUrl().length() > 2) {
            actionAdapter.add(new Action(ACTION_WATCH_TRAILER, fragment.getResources().getString(R.string.watch_trailer_1)));
        }

        row.setActionsAdapter(actionAdapter);
        int overviewRowIndex = mAdapter.indexOf(row);
        if (overviewRowIndex >= 0) {
            mAdapter.notifyArrayItemRangeChanged(overviewRowIndex, 1);
        }
    }
    public void handleActionClick(Movie movie, Action action, ArrayObjectAdapter clickedAdapter) {
        if (movie == null || action == null || clickedAdapter == null) {
            Log.e(TAG, "handleActionClick: Invalid input parameters");
//            Log.e(TAG, "movie: " + movie);
//            Log.e(TAG, "action: "+ action);
//            Log.e(TAG, "clickedAdapter: "+ clickedAdapter);
            return;
        }

        long actionId = action.getId();
        if (actionId == ACTION_WATCH_TRAILER) {
            Log.d(TAG, "onActionClicked: Trailer: " + movie.getTitle());
            Util.openBrowserIntent(movie, fragment, true, false, false);
            return;
        }

        if (actionId != ACTION_WATCH) {
            return;
        }

        Movie watchMovie = (clickedAdapter.size() > 0) ? (Movie) clickedAdapter.get(0) : null;
        if (watchMovie == null) {
            Log.e(TAG, "handleActionClick: Empty sublist");
            return;
        }
        watchMovie.setRowIndex(0); // Update the correct item of the row
                if (watchMovie.getState() == Movie.VIDEO_STATE) {
                    Log.d(TAG, "handleActionClick: Already video");
                    Util.openExoPlayer(watchMovie, fragment.getActivity(), true);
                    return;
                }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> Toast.makeText(fragment.getActivity(), "الرجاء الانتظار...", Toast.LENGTH_SHORT).show());

        executor.submit(() -> {
            try {
                AbstractServer server = ServerConfigRepository.getInstance().getServer(movie.getStudio());
                if (server == null) {
                    Log.e(TAG, "handleActionClick: Undefined server");
                    return;
                }


                server.fetch(watchMovie, Movie.ACTION_WATCH_LOCALLY, new ServerInterface.ActivityCallback<Movie>() {
                    @Override
                    public void onSuccess(Movie result, String title) {
                        if (result == null || result.getVideoUrl() == null) {
                            Log.e(TAG, "onSuccess: Empty result");
                            return;
                        }
                        Log.d(TAG, "onActionClicked: Resolutions " + result);
                        mainHandler.post(() -> {
                            Util.openExoPlayer(result, fragment.getActivity(), true);
                            clickedAdapter.replace(0, result);
                            updateOverview(result);
                        });
                    }

                    @Override
                    public void onInvalidCookie(Movie result, String title) {
                        Log.e(TAG, "onInvalidCookie: " + (result != null ? result.getVideoUrl() : "null"));
                        if (result == null){
                            Log.d(TAG, "onInvalidCookie: empty result");
                            return;
                        }
                        result.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
                        if (fragment != null){
                            Util.openBrowserIntent(result, fragment, false, true, true,selectedRowIndex, 0);
                            return;
                        }
                        Util.openBrowserIntent(result, fragment.getActivity(), false, true, true,selectedRowIndex,0);
                    }

                    @Override
                    public void onInvalidLink(Movie result) {
                        Log.e(TAG, "onInvalidLink: " + (result != null ? result.getVideoUrl() : "null"));
                    }

                    @Override
                    public void onInvalidLink(String message) {
                        Log.e(TAG, "onInvalidLink: " + message);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "handleActionClick: Error processing request", e);
            } finally {
                executor.shutdown();
            }
        });
    }

    public void onResume() {
        if (selectedRowIndex != null && selectedItemIndex != null && selectedRowIndex != -1 && selectedItemIndex != -1) {
            selectItemOnRowListAdapter(selectedRowIndex, selectedItemIndex);
        }
    }

    public void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState: ");
        outState.putInt("selectedRowIndex", selectedRowIndex);
        outState.putInt("selectedItemIndex", selectedItemIndex);
    }

    public void onDetach() {
        this.fragment = null;
    }
}
