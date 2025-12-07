package com.omarflex5.temp.omerflex.view.listener;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.entity.MovieRepository;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.M3U8ContentFetcher;
import com.omerflex.view.CardPresenter;
import com.omerflex.view.VideoDetailsFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MovieItemViewClickedListener implements OnItemViewClickedListener{

    private static final String TAG = "MovieClickedListener";

    public static final int ACTION_OPEN_DETAILS_ACTIVITY = 1;
    public static final int ACTION_OPEN_EXTERNAL_ACTIVITY = 2;
    public static final int ACTION_OPEN_NO_ACTIVITY = 3;

    private static final int ACTION_WATCH = 1;
    private static final int ACTION_WATCH_TRAILER = 2;

    private Integer selectedItemIndex = -1;
    private Integer selectedRowIndex = -1;
    Fragment mFragment;
    ArrayObjectAdapter mRowsAdapter;
    private final MovieRepository movieRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // A single, shared executor service for all background tasks
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public MovieItemViewClickedListener(Fragment fragment, ArrayObjectAdapter mRowsAdapter) {
        this.mFragment = fragment;
        this.mRowsAdapter = mRowsAdapter;
        this.movieRepository = MovieRepository.getInstance(fragment.getActivity(), ((OmerFlexApplication) fragment.getActivity().getApplication()).getDatabase().movieDao());
    }

    @Override
    public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        Log.d(TAG, "onItemClicked: item:"+item);
        if (!(item instanceof Movie)) {
            // return to delegate handling
            Log.d(TAG, "onItemClicked: return item not instance of movie");
            return;
        }
        selectedRowIndex = mRowsAdapter.indexOf(row);
        if (row instanceof ListRow){
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) ((ListRow)  row).getAdapter();
            selectedItemIndex = adapter.indexOf(item);
        }
        else{
            Log.d(TAG, "onItemClicked: row is not instanceof ListRow");
            return;
        }
        Movie movie = (Movie) item;
        if (movie.getStudio() == null) {
            Log.d(TAG, "handleMovieItemClick: Unknown movie or studio, returning.");
            Toast.makeText(mFragment.getActivity(), "Unknown movie or server: " + movie.getStudio(), Toast.LENGTH_SHORT).show();
            return;
        }
        // Submit the heavy lifting to a background thread
        executorService.submit(() -> processMovieClick(movie, (ListRow) row));
    }

    /**
     * Handles the actual click processing logic on a background thread.
     */
    private void processMovieClick(Movie movie, ListRow clickedRow) {
//        if (movieRepository.getSelectedMovie().getValue() == null){
//            Log.d(TAG, "processMovieClick: ");
//            // set mSelectedMovie in the repository
//            // very important to monitor the selected movie in the repository
//            movieRepository.setSelectedMovie(movie);
//        }
        Log.d(TAG, "processMovieClick: state: " + movie.getState());
        ArrayObjectAdapter adapter =(ArrayObjectAdapter) clickedRow.getAdapter();
        selectedItemIndex = adapter.indexOf(movie);
//        Log.d(TAG, "processMovieClick 2: "+ movieRepository.getSelectedMovie().getValue());
        Log.d(TAG, "processMovieClick: selectedRowIndex: " + selectedRowIndex);
        Log.d(TAG, "processMovieClick: selectedItemIndex: " + selectedItemIndex);
        // Use a switch statement for cleaner state handling
        switch (movie.getState()) {
            case Movie.COOKIE_STATE:
                // Fetch cookie through browser
                handleCookieState(movie, clickedRow);
                break;
            case Movie.VIDEO_STATE:
                // play video
                handleVideoState(movie);
                break;
            case Movie.BROWSER_STATE:
                // open browser. todo analyze use case
                handleBrowserState(movie);
                break;
            case Movie.NEXT_PAGE_STATE:
                // fetch next page movies
                handleNextPageState(movie, clickedRow);
                break;
            case Movie.IPTV_PLAY_LIST_STATE:
                // fetch iptv categories
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    handleIptvPlayListState(movie);
                }
                break;
            default:
                // The default case handles all other states,
                // including opening the details activity or next page states
                handleServerFetchByItemClickCase(movie, clickedRow);
//                Util.openVideoDetailsIntent(movie, mFragment);
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleIptvPlayListState(Movie movie) {
        Log.d(TAG, "handleIptvPlayListState: list size: " + mRowsAdapter.size());
        // fetch iptv movies
        try {
            // Use a new executor to isolate the IPTV fetch and prevent it from blocking the shared executor.
            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    // remove iptv created rows first
                    mainHandler.post(this::removeIpTvRows);
                    M3U8ContentFetcher.fetchIpTvPlayList(movie, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
                        @Override
                        public void onSuccess(ArrayList<Movie> result, String title) {
                            Log.d(TAG, "onSuccess: " + title + ", " + result.size());
                            generateCategory(title, result);
                        }

                        @Override
                        public void onInvalidCookie(ArrayList<Movie> result, String title) {
                            Log.d(TAG, "onInvalidCookie: ");
                        }

                        @Override
                        public void onInvalidLink(ArrayList<Movie> result) {
                            Log.d(TAG, "onInvalidLink: ");
                        }

                        @Override
                        public void onInvalidLink(String message) {
                            Log.d(TAG, "onInvalidLink: " + message);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in handleIptvPlayListState for movie: " + movie.getTitle(), e);
                    mainHandler.post(() -> {
                        if (mFragment != null && mFragment.getContext() != null) {
                            Toast.makeText(mFragment.getContext(), "Error loading IPTV list.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

//            executorService.submit(() -> {
//                M3U8ContentFetcher.fetchAndStoreM3U8Content(movie, result -> {
////                    Log.d(TAG, "handleIptvPlayListState: movies: "+ result.size());
////                    Log.d(TAG, "handleIptvPlayListState: movies: "+ result.toString());
////                    movieRepository.saveIptvMovies(result, movies -> {
////                        movies.entrySet().stream()
////                                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
////                                .forEach(
////                                        entry -> mainHandler.post(()
////                                                -> generateCategory(entry.getKey(), entry.getValue(), false)
////                                        )
////                                );
////                        Log.d(TAG, "movies: " + movies.size());
////                    });
////                });
////            });

            mainHandler.post(() -> {
                if (mFragment.getActivity() != null) {
                    Toast.makeText(mFragment.getActivity(), "الرجاء الانتظار...", Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            Log.d(TAG, "handleIptvClickedItem: " + e.getMessage());
        }
    }

    private void removeIpTvRows() {
        // loop over listRows of the mRowsAdapter and delete rows of header id = Movie.IPTV_PLAY_LIST_STATE
        for (int i = 0; i < mRowsAdapter.size(); i++) {
            ListRow row = (ListRow) mRowsAdapter.get(i);
            Log.d(TAG, "removeIpTvRows: " + row.getHeaderItem().getId() );
            if (row.getHeaderItem().getId() == Movie.IPTV_PLAY_LIST_STATE) {
                Log.d(TAG, "removeIpTvRows: removing row: " + row.getHeaderItem().getName());
                mainHandler.post(() ->mRowsAdapter.remove(row));
            }
        }
    }

    public void addMovieRow(HeaderItem header, List<Movie> movies) {
        ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new CardPresenter());
        listRowAdapter.addAll(0, movies);
        mRowsAdapter.add(new ListRow(header, listRowAdapter));
    }

    private void generateCategory(String group, ArrayList<Movie> groupMovies) {
        HeaderItem header = new HeaderItem(Movie.IPTV_PLAY_LIST_STATE, group);
        addMovieRow(header, groupMovies);
    }


    private void handleCookieState(Movie movie, ListRow clickedRow) {
        // to update the row with new result after fetching the cookie, setFetch to REQUEST_CODE_MOVIE_LIST
        Log.d(TAG, "handleCookieState: ");
        if (movie.getFetch() == Movie.NO_FETCH_MOVIE_AT_START) {
            Log.d(TAG, "handleCookieState: This handleCookieState is already fetched...");
            return;
        }
            try {
                ServerConfigRepository.getInstance().getConfigAsync(movie.getStudio(), config -> {
                    if (config == null) {
                        mainHandler.post(() -> {
                            if (mFragment != null && mFragment.getContext() != null) {
                                Toast.makeText(mFragment.getContext(), "Server config not found.", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                    ExecutorService executor = Executors.newSingleThreadExecutor();

                    executor.submit(() -> {
                        movieRepository.getSearchMoviesOfServer(true, config, movie.getVideoUrl(), (category, movieList) -> {
                            if (movieList != null) {
    //                            Log.d("Movie", "handleCookieState movie33: " + movieList.toString());
                                updateAdapterOnMainThread(movieList, clickedRow, movie);
                                movie.setFetch(Movie.NO_FETCH_MOVIE_AT_START);
                            } else {
                                Log.d("Movie", "handleCookieState movieList not found.");
                            }
                        });
                    });
                    executor.shutdown();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in handleCookieState for movie: " + movie.getTitle(), e);
                mainHandler.post(() -> {
                    if (mFragment != null && mFragment.getContext() != null) {
                        Toast.makeText(mFragment.getContext(), "Error processing cookie state.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
    }

    private void handleVideoState(Movie movie) {
        Log.d(TAG, "handleMovieItemClick: VIDEO_STATE");
        Util.openExoPlayer(movie, mFragment.getActivity(), true);
    }

    private void handleBrowserState(Movie movie) {
        Log.d(TAG, "handleMovieItemClick: BROWSER_STATE");
        Util.openBrowserIntent(movie, mFragment, false, false, false);
    }

    protected void handleServerFetchByItemClickCase(Movie movie, ListRow clickedRow) {
        Log.d(TAG, "handleServerFetchByItemClickCase");

        // Asynchronously get the server object first.
        ServerConfigRepository.getInstance().getServerAsync(movie.getStudio(), server -> {
            if (server == null) {
                mainHandler.post(() -> {
                    if (mFragment != null && mFragment.getContext() != null) {
                        Toast.makeText(mFragment.getContext(), "Server not found.", Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }

            // https://wecima.video/2f7jimjworum/The.Walking.Dead.Daryl.Dixon.S03E03.WeCima.Tube.720p.mp4.html?Key=JiIyfK74LdKioPfIxlLHaA&Expires=1758550449
            // https://tgb4.top15top.shop/2f7jimjworum/The.Walking.Dead.Daryl.Dixon.S03E03.WeCima.Tube.720p.mp4.html?Key=jWlEFsYxN_xzOSanB58AoA&Expires=1758550373

            // Once we have the server, execute the potentially blocking call on a new, separate thread.
            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    // This is the blocking call
                    int nextAction = server.fetchNextAction(movie);

                    // Once the action is fetched, post the result back to the main thread
                    // to perform UI actions like opening an activity.
                    mainHandler.post(() -> {
                        switch (nextAction) {
                            case VideoDetailsFragment.ACTION_OPEN_DETAILS_ACTIVITY:
                                Log.d(TAG, "handleServerFetchByItemClickCase: nextAction: ACTION_OPEN_DETAILS_ACTIVITY");
                                Util.openVideoDetailsIntent(movie, mFragment);
                                break;
                            case VideoDetailsFragment.ACTION_OPEN_EXTERNAL_ACTIVITY:
//                dbHelper.addMainMovieToHistory(movie);  todo
                                Log.d(TAG, "handleServerFetchByItemClickCase: nextAction: ACTION_OPEN_EXTERNAL_ACTIVITY");
                                Util.openExternalVideoPlayer(movie, mFragment.getActivity());
                                break;
                            case VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY:
//                dbHelper.addMainMovieToHistory(movie); todo
                                Log.d(TAG, "handleServerFetchByItemClickCase: nextAction: ACTION_OPEN_NO_ACTIVITY");
                                handleFetchMovieFromServer(movie, clickedRow, server);
                                break;
                            default:
                                Log.d(TAG, "handleServerFetchByItemClickCase: unknown case");
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching next action for movie: " + movie.getTitle(), e);
                    // Optionally, post a toast or error message to the user on the main thread
                    mainHandler.post(() -> {
                        if (mFragment != null && mFragment.getContext() != null) {
                            Toast.makeText(mFragment.getContext(), "Error processing movie.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        });
    }

    private void handleFetchMovieFromServer(Movie movie, ListRow clickedRow, AbstractServer server) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> {
            ArrayObjectAdapter adapter =(ArrayObjectAdapter) clickedRow.getAdapter();
            int position = adapter.indexOf(movie);
            MovieFetchProcess process = server.fetch(movie, movie.getState(), new ServerInterface.ActivityCallback<Movie>() {
                @Override
                public void onSuccess(Movie result, String title) {
                    Util.openExternalVideoPlayer(result, mFragment.getActivity());
                    adapter.replace(position, result);
                }

                @Override
                public void onInvalidCookie(Movie result, String title) {
                    Log.d(TAG, "onInvalidCookie: " + result);
                    result.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
                    if (mFragment != null) {
                        Util.openBrowserIntent(result, mFragment, true, true, true, selectedRowIndex, selectedItemIndex);
                        return;
                    }
                    Util.openBrowserIntent(result, mFragment.getActivity(), false, true, true, selectedRowIndex, selectedItemIndex);
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

    /**
     * Refactored method to handle the next page state for a movie click.
     * This method now uses the class's shared ExecutorService and consolidates callback logic.
     * @param movie The movie that was clicked.
     * @param clickedRow The row that contains the clicked movie.
     */
    private void handleNextPageState(Movie movie, ListRow clickedRow) {
        // Use a more robust check to ensure we don't fetch multiple times.
        if (movie.getFetch() == Movie.NO_FETCH_MOVIE_AT_START) {
            Log.d(TAG, "handleNextPageState: This Next page is already fetched...");
            return;
        }

        // Isolate the repository call in a new thread to avoid blocking the shared executor
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.submit(() -> {
            try {
                ServerConfigRepository.getInstance().getConfigAsync(movie.getStudio(), config -> {
                    if (config == null) {
                        mainHandler.post(() -> {
                            if (mFragment != null && mFragment.getContext() != null) {
                                Toast.makeText(mFragment.getContext(), "Server config not found.", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }

                    Log.d(TAG, "handleNextPageState: ClickListener");
                    movieRepository.getSearchMoviesOfServer(true, config, movie.getVideoUrl(), (category, movieList) -> {
                        if (movieList != null) {
                            Log.d("Movie", "fetchNextPage movie33: " + movieList.toString());
                            updateAdapterOnMainThread(movieList, clickedRow, movie);
                            movie.setFetch(Movie.NO_FETCH_MOVIE_AT_START);
                        } else {
                            Log.d("Movie", "fetchNextPage movieList not found.");
                        }
                    });
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in handleNextPageState for movie: " + movie.getTitle(), e);
                mainHandler.post(() -> {
                    if (mFragment != null && mFragment.getContext() != null) {
                        Toast.makeText(mFragment.getContext(), "Error loading next page.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        executor.shutdown();
    }

    /**
     * Helper method to update the adapter on the main thread and set a flag on the movie.
     * This avoids code duplication in the callback methods.
     * @param result The list of new movies to add.
     * @param clickedRow The row adapter to update.
     * @param movie The movie to update with a "clicked" flag.
     */
    private void updateAdapterOnMainThread(List<Movie> result, ListRow clickedRow, Movie movie) {
        if (result.isEmpty()) {
            Log.d(TAG, "onSuccess: empty result");
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) clickedRow.getAdapter();
            adapter.addAll(adapter.size(), result);
//            movie.setFetch(Movie.NO_FETCH_MOVIE_AT_START); // Flag that it's already clicked
        });
    }

    public void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "handleActivityResult: " + requestCode + ", " + resultCode + ", " + data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.d(TAG, "onActivityResult:RESULT_NOT_OK ");
            return;
        }
        //int clickedRowId = data.getIntExtra(Movie.KEY_CLICKED_ROW_ID, -1);
        int clickedRowId = selectedRowIndex;

        if (clickedRowId == -1){
            Log.d(TAG, "handleActivityResult: not ListRow");
            Toast.makeText(mFragment.getActivity(), "حدث خطأ...", Toast.LENGTH_SHORT).show();
            return;
        }
        Movie resultMovie = Util.recieveSelectedMovie(data);

        //int clickedMovieIndex = data.getIntExtra(Movie.KEY_CLICKED_MOVIE_INDEX, -1);
        int clickedMovieIndex = selectedItemIndex;
        Log.d(TAG, "handleActivityResult: clickedMovieIndex: "+ clickedMovieIndex);
        Log.d(TAG, "handleActivityResult: clickedRowId: "+ clickedRowId);
        if (!(mRowsAdapter.get(clickedRowId) instanceof ListRow)){
            Log.d(TAG, "handleResult: error: fail to find the clicked row");
        }

        ListRow listRow = (ListRow) mRowsAdapter.get(clickedRowId);
        ArrayObjectAdapter clickedAdapter = (ArrayObjectAdapter) listRow.getAdapter();
        if (clickedMovieIndex != -1) {
            // we have to updated the clicked movie
            updateClickedMovieItem(clickedAdapter, clickedMovieIndex, resultMovie);
        }

        switch (requestCode) {
            case Movie.REQUEST_CODE_EXOPLAYER:
                Log.d(TAG, "onActivityResult: REQUEST_CODE_EXOPLAYER");
                Util.openExoPlayer(resultMovie, mFragment.getActivity(), true);
                break;
            case Movie.REQUEST_CODE_EXTERNAL_PLAYER:
                Log.d(TAG, "onActivityResult: REQUEST_CODE_EXTERNAL_PLAYER: " + resultMovie);
                Util.openExoPlayer(resultMovie, mFragment.getActivity(), true);
                break;
            default:
                Log.d(TAG, "handleResult: default");
                if (mFragment instanceof VideoDetailsFragment){
                    VideoDetailsFragment videoDetailsFragment = (VideoDetailsFragment) mFragment;
                    videoDetailsFragment.updateOverviewUI(resultMovie);
                }
                extendClickedRow(clickedAdapter, (ArrayList<Movie>) (resultMovie.getSubList()));
                break;
        }
    }


    private void extendClickedRow(ArrayObjectAdapter clickedAdapter, ArrayList<Movie> movieList) {
        if (movieList == null || movieList.isEmpty()) {
            Log.d(TAG, "extendClickedRow: empty list");
            return;
        }
        Log.d(TAG, "extendClickedRow: movieList:" +movieList);
        clickedAdapter.addAll(clickedAdapter.size(), movieList);
    }

    private void updateClickedMovieItem(ArrayObjectAdapter clickedAdapter, int clickedMovieIndex, Movie resultMovie) {
        Log.d(TAG, "updateClickedMovieItem: clickedMovieIndex "+ clickedMovieIndex + ", size: "+ clickedAdapter.size());
        if (resultMovie != null && clickedAdapter.size() > clickedMovieIndex) {
            clickedAdapter.replace(clickedMovieIndex, resultMovie);
        }
    }
}