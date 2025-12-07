package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ArrayObjectAdapter;

import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.database.MovieDbHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class DetailsViewControl extends SearchViewControl {
    public static String TAG = "DetailsViewControl";

    private static final int ACTION_WATCH = 1;
    private static final int ACTION_WATCH_TRAILER = 2;

    public DetailsViewControl(Activity activity, Fragment fragment, MovieDbHelper dbHelper) {
        super(activity, fragment, dbHelper);
    }

    protected abstract void updateCurrentMovie(Movie movie);

    public <T> void handleActionClick(Movie movie, long actionId, T clickedAdapter) {
        boolean condTrailer = actionId == ACTION_WATCH_TRAILER;
        boolean condWatch1 = actionId == ACTION_WATCH;
//        Log.d(TAG, "handleActionClick: "+clickedAdapter);
        if (condTrailer) {
            Log.d(TAG, "onActionClicked: Trailer: " + movie.getTitle());

            Util.openBrowserIntent(movie, activity, true, false, false, -1, -1);
            return;
        }
        if (!condWatch1) {
            return;
        }
//        Log.d(TAG, "handleActionClick: before addMainMovieToHistory: "+movie);
        dbHelper.addMainMovieToHistory(movie);
//        Log.d(TAG, "handleActionClick: after addMainMovieToHistory: "+movie);
        Movie watchMovie;
        if (clickedAdapter instanceof ArrayObjectAdapter){
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) clickedAdapter;
            if (adapter.size() > 0){
                watchMovie = (Movie) adapter.get(0);
            } else {
                watchMovie = null;
            }
        } else {
            watchMovie = null;
        }

        if (watchMovie == null) {
            Log.d(TAG, "handleActionClick: error: empty sublist");
            return;
        }
        watchMovie.setRowIndex(0); // very important to update the correct item of the row

//                showProgressDialog(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(activity, "الرجاء الانتظار...", Toast.LENGTH_SHORT).show();
        });

        executor.submit(() -> {
            // mSelectedMovie = Objects.requireNonNull(server).fetchItem(mSelectedMovie);

           Log.d(TAG, "run: condWatch2 " + watchMovie);
//           Log.d(TAG, "run: condWatch2 sub: " + movie.getSubList());
            //todo : handle local watch
//            dbHelper.addMainMovieToHistory(movie);
            AbstractServer server = ServerConfigRepository.getInstance().getServer(movie.getStudio());

            if (server == null){
                Log.d(TAG, "handleActionClick: undefined server");
                return;
            }
//                    Movie res = server.fetchToWatchLocally(movie);
            MovieFetchProcess movieFetchProcess= server.fetch(
                    watchMovie,
                    Movie.ACTION_WATCH_LOCALLY,
                    new ServerInterface.ActivityCallback<Movie>() {
                        @Override
                        public void onSuccess(Movie result, String title) {
                            if (result == null || result.getVideoUrl() == null) {
                                Log.d(TAG, "onSuccess: empty result");
                                return;
                            }
//                                        String type = "video/*";
                                // Uri uri = Uri.parse(res.getSubList().get(0).getVideoUrl());
                                Log.d(TAG, "onActionClicked: Resolutions " + result);
                                Util.openExoPlayer(result, activity, true);
                                //dbHelper.addMainMovieToHistory(res.getMainMovieTitle(), null);
                                updateClickedMovieItem(clickedAdapter, 0, result);
                                updateCurrentMovie(result);
//                                updateItemFromActivityResult(result);
//                                getActivity().runOnUiThread(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        Util.openExoPlayer(result, getActivity(), true);
//                                        //dbHelper.addMainMovieToHistory(res.getMainMovieTitle(), null);
//                                        updateItemFromActivityResult(result);
//
//                                        hideProgressDialog(false);
//                                    }
//                                });

                        }

                        @Override
                        public void onInvalidCookie(Movie result, String title) {
                            Log.d(TAG, "onInvalidCookie: "+result.getVideoUrl());
//                            hideProgressDialog(false);
                            result.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
                            if (fragment != null){
                                Util.openBrowserIntent(result, fragment, false, true, true);
                                return;
                            }
                            Util.openBrowserIntent(result, activity, false, true, true);
                        }

                        @Override
                        public void onInvalidLink(Movie result) {
                            Log.d(TAG, "onInvalidLink: "+result.getVideoUrl());
                        }

                        @Override
                        public void onInvalidLink(String message) {
                            Log.d(TAG, "onInvalidLink: "+message);
                        }
                    }
            );
//                    if (res != null && res.getVideoUrl() != null) {
//                        String type = "video/*";
//                        // Uri uri = Uri.parse(res.getSubList().get(0).getVideoUrl());
//                        Log.d(TAG, "onActionClicked: Resolutions " + res);
//                        Objects.requireNonNull(getActivity()).runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                //Intent intent = new Intent(activity, PlaybackActivity.class);
//                                Intent exoIntent = new Intent(getActivity(), ExoplayerMediaPlayer.class);
//                                exoIntent.putExtra(DetailsActivity.MOVIE, (Serializable) res);
//                                exoIntent.putExtra(DetailsActivity.MAIN_MOVIE, (Serializable) res.getMainMovie());
//                                //intent.putExtra(DetailsActivity.MOVIE, (Serializable) res.getSubList().get(0));
//                                Objects.requireNonNull(getActivity()).startActivity(exoIntent);
//                                //dbHelper.addMainMovieToHistory(res.getMainMovieTitle(), null);
//                                updateItemFromActivityResult(res);
//                                dbHelper.addMainMovieToHistory(mSelectedMovie);
//                            }
//                        });
//
//                    } else {
//                        Objects.requireNonNull(getActivity()).runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                Toast.makeText(getActivity(), "الرجاء الانتظار...", Toast.LENGTH_SHORT).show();
//                            }
//                        });
//                    }
//                    hideProgressDialog(true);
        });

        executor.shutdown();
    }

    public <T> void handleMovieItemClick(Movie movie, int position, T rowsAdapter, T clickedRow, int defaultHeadersCounter) {
        Log.d(TAG, "handleMovieItemClick: ");
        AbstractServer server = ServerConfigRepository.getInstance().getServer(movie.getStudio());
        if (server == null) {
            Log.d(TAG, "handleMovieItemClick: undefined server");
            return;
        }

        int nextAction = server.fetchNextAction(movie);
        switch (nextAction) {
            case VideoDetailsFragment.ACTION_OPEN_DETAILS_ACTIVITY:
                Util.openVideoDetailsIntent(movie, activity);
                break;
            case VideoDetailsFragment.ACTION_OPEN_EXTERNAL_ACTIVITY:
                dbHelper.addMainMovieToHistory(movie);
                Util.openExternalVideoPlayer(movie, activity);
                break;
            case VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY:
                dbHelper.addMainMovieToHistory(movie);
                handleFetchMovieFromServer(movie, position, clickedRow, server);
                break;

        }
    }

    private <T> void handleFetchMovieFromServer(Movie movie, int position, T clickedRow, AbstractServer server) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(activity, "الرجاء الانتظار...", Toast.LENGTH_SHORT).show();
        });

        executor.submit(() -> {
        MovieFetchProcess process = server.fetch(movie, movie.getState(), new ServerInterface.ActivityCallback<Movie>() {
            @Override
            public void onSuccess(Movie result, String title) {
                Util.openExternalVideoPlayer(result, activity);
                updateClickedMovieItem(clickedRow, position, result);
            }

            @Override
            public void onInvalidCookie(Movie result, String title) {
                Log.d(TAG, "onInvalidCookie: " + result);
                result.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
                if (fragment != null) {
                    Util.openBrowserIntent(result, fragment, true, true, true);
                    return;
                }
                Util.openBrowserIntent(result, activity, false, true, true);
            }

            @Override
            public void onInvalidLink(Movie result) {

            }

            @Override
            public void onInvalidLink(String message) {

            }
        });
    });

        executor.shutdown();
    }
}
