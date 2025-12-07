package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRow;

import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieRepository;
import com.omerflex.server.AbstractServer;
//import com.omerflex.server.AkwamServer;
//import com.omerflex.server.ArabSeedServer;
//import com.omerflex.server.CimaNowServer;
//import com.omerflex.server.FaselHdServer;
//import com.omerflex.server.IptvServer;
//import com.omerflex.server.KooraServer;
//import com.omerflex.server.MyCimaServer;
//import com.omerflex.server.OldAkwamServer;
//import com.omerflex.server.OmarServer;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;
import com.omerflex.service.M3U8ContentFetcher;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.database.MovieDbHelper;
import com.omerflex.view.mobile.view.CategoryAdapter;
import com.omerflex.view.mobile.view.HorizontalMovieAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SearchViewControl {

    public static String TAG = "SearchViewControl";

    protected final Activity activity;
    protected final Fragment fragment;
    protected MovieDbHelper dbHelper;
    protected MovieRepository movieRepository;
    Handler mainHandler = new Handler(Looper.getMainLooper());

    public SearchViewControl(Activity activity, Fragment fragment, MovieDbHelper dbHelper) {
        this.activity = activity;
        this.fragment = fragment;
        this.dbHelper = dbHelper;
        movieRepository = MovieRepository.getInstance(activity, ((OmerFlexApplication) activity.getApplication()).getDatabase().movieDao());
//        Looper.prepare(); // to run Toasts
    }

    //    protected abstract void updateCurrentMovie(Movie movie);
    protected abstract <T> void updateMovieListOfMovieAdapter(ArrayList<Movie> movies, T clickedAdapter);

    protected abstract <T> T generateCategory(String title, ArrayList<Movie> movies, boolean isDefaultHeader);

    protected abstract <T> void updateClickedMovieItem(T clickedAdapter, int clickedMovieIndex, Movie resultMovie);

    protected abstract void updateCurrentMovie(Movie movie);

    public <T> void handleMovieItemClick(Movie movie, int position, T rowsAdapter, T clickedRow, int defaultHeadersCounter) {
        Log.d(TAG, "handleMovieItemClick: super");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
//                AbstractServer server = ServerManager.determineServer(movie, null, activity, null);
            if (movie == null) {
                Log.d(TAG, "handleMovieItemClick: Unknown movie");
//                Toast.makeText(activity, "Unknown Server", Toast.LENGTH_SHORT).show();
                return;
            }
            AbstractServer server = ServerConfigRepository.getInstance().getServer(movie.getStudio());
            Log.d(TAG, "handleMovieItemClick: movie: " + movie);
            if (server == null) {
                Log.d(TAG, "handleMovieItemClick: Unknown Server: "+ movie.getStudio());
//                Toast.makeText(activity, "Unknown Server", Toast.LENGTH_SHORT).show();
                return;
            }
            if (movie.getState() == Movie.COOKIE_STATE) {
//                Toast.makeText(activity, "renewing Cookie", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "handleMovieItemClick: renewCookie");

                renewCookie(movie);
                return;
            }
//            if (movie.getStudio().equals(Movie.SERVER_IPTV)) {
            if (movie.getState() == Movie.VIDEO_STATE) {
                Util.openExoPlayer(movie, activity, true);
                dbHelper.addMainMovieToHistory(movie);
                //exist method after handling
                return;
            }

            if (movie.getStudio().equals(Movie.SERVER_IPTV)) {
                Log.d(TAG, "handleMovieItemClick: SERVER_IPTV");
                //todo: add info to say if next already clicked, and handle the rest
                handleIptvClickedItem(movie, position, rowsAdapter, defaultHeadersCounter);
                return;
            }


            if (movie.getState() == Movie.NEXT_PAGE_STATE) {
                //todo: add info to say if next already clicked, and handle the rest
                handleNextPageMovieClick(clickedRow, movie);
                return;
            }

            if (movie.getState() == Movie.BROWSER_STATE) {
                Log.d(TAG, "handleMovieItemClick: BROWSER_STATE");
                //todo: add info to say if next already clicked, and handle the rest
                Util.openBrowserIntent(movie, activity, false, false, false);
                return;
            }

            openDetailsActivity(movie, activity);


//                if (shouldExtendCategory(movie)) {
//                    Log.d(TAG, "onMovieClick: shouldExtendCategory");
//                    extendMovieListForCategory(movie, position, categoryAdapter, horizontalMovieAdapter);
//                } else {
//                    Log.d(TAG, "onMovieClick: openMobileDetailsIntent");
//                    // Handle normal click event (e.g., open detail activity)
//                    Util.openMobileDetailsIntent(movie, activity, true);
//                }
        });
        executor.shutdown();

    }

    private <T> void handleNextPageMovieClick(T clickedRow, Movie movie) {
        if (movie.getDescription().equals("0")) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
//                            AbstractServer server = ServerManager.determineServer(movie, null, getActivity(), fragment);
                AbstractServer server = ServerConfigRepository.getInstance().getServer(movie.getStudio());
                if (server == null) {
                    Log.d(TAG, "handleItemClicked NEXT_PAGE_STATE run: unknown server:" + movie.getStudio());
                    return;
                }
                //server

//                            Log.d(TAG, "onItemClicked: adapter :" + adapter.toString());

                List<Movie> nextList = server.search(movie.getVideoUrl(), new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
                    @Override
                    public void onSuccess(ArrayList<Movie> result, String title) {
                        Log.d(TAG, "handleItemClicked NEXT_PAGE_STATE onSuccess");
                        if (result.isEmpty()) {
                            Log.d(TAG, "onSuccess: empty result");
                            return;
                        }
                        //  todo : refactor logic in view class to add or remove
//                        activity.runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
                        new Handler(Looper.getMainLooper()).post(() -> {
//                            Log.d(TAG, "runOnUiThread run: " + clickedRow);
                            if (clickedRow instanceof ListRow) {
                                ListRow adapterRow = (ListRow) clickedRow;
                                ArrayObjectAdapter adapter = (ArrayObjectAdapter) adapterRow.getAdapter();
                                adapter.addAll(adapter.size(), result);
                                //flag that its already clicked
                            } else if (clickedRow instanceof HorizontalMovieAdapter) {
                                HorizontalMovieAdapter adapterRow = (HorizontalMovieAdapter) clickedRow;
                                adapterRow.addAll(result);
                            }
                            movie.setDescription("1");
                        });
                    }

                    @Override
                    public void onInvalidCookie(ArrayList<Movie> result, String title) {
                        Log.d(TAG, "handleItemClicked NEXT_PAGE_STATE onInvalidCookie");
                        if (result.isEmpty()) {
                            Log.d(TAG, "onInvalidCookie: empty result");
                            return;
                        }
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (clickedRow instanceof ListRow) {
                                ListRow adapterRow = (ListRow) clickedRow;
                                ArrayObjectAdapter adapter = (ArrayObjectAdapter) adapterRow.getAdapter();

                                adapter.addAll(adapter.size(), result);
                                //flag that its already clicked
                            } else if (clickedRow instanceof HorizontalMovieAdapter) {
                                HorizontalMovieAdapter adapterRow = (HorizontalMovieAdapter) clickedRow;
                                adapterRow.addAll(result);
                            }
                            movie.setDescription("1");
                        });
                    }

                    @Override
                    public void onInvalidLink(ArrayList<Movie> result) {
                        Log.d(TAG, "onInvalidLink handleNextPageMovieClick");
                    }

                    @Override
                    public void onInvalidLink(String message) {
                        Log.d(TAG, "onInvalidLink: handleNextPageMovieClick: " + message);
                    }
                }, false);
//                            Log.d(TAG, "handleItemClicked: nextPage:" + nextList.toString());

            });

            executor.shutdown();

        }
    }


    private void renewCookie(Movie movie) {
        Log.d(TAG, "renewCookie: ");
        movie.setFetch(Movie.REQUEST_CODE_EXTEND_MOVIE_SUB_LIST);
        if (fragment != null) {
            Util.openBrowserIntent(movie, fragment, true, true, true);
            return;
        }
        Util.openBrowserIntent(movie, activity, true, true, true);
    }

    protected abstract void openDetailsActivity(Movie movie, Activity activity);

    protected void loadIptvServerResult(String query, AbstractServer server) {
        ArrayList<Movie> tvChannelList = dbHelper.findMovieBySearchContext(Movie.SERVER_IPTV, query);
        if (tvChannelList == null || tvChannelList.isEmpty()) {
            Log.d(TAG, "iptv channels search result is empty ");
            return;
        }
        generateCategory(server.getLabel(), tvChannelList, true);
//        ArrayObjectAdapter adapter = addRowToMainAdapter(server.getLabel(), server.getServerId());
//        activity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                // String newName = rowName + ("(" + finalMovieList.size() + ")");
//                adapter.addAll(0, tvChannelList);
//            }
//        });
    }

    private <T> void handleIptvClickedItem(Movie movie, int position, T rowsAdapter, int defaultHeadersCounter) {
        if (movie.getState() != Movie.IPTV_PLAY_LIST_STATE) {
            Log.d(TAG, "handleIptvClickedItem: not PLAYLIST_STATE but: " + movie.getState());
            Util.openExoPlayer(movie, activity, true);
            return;
        }


//        int defaultHeaders = rowSize - IPTV_HEADER_ROWS_COUNTER;
        Log.d(TAG, "handleItemClicked: defaultHeaders:" + defaultHeadersCounter);

//                        Log.d(TAG, "handleItemClicked: iptvStartIndex: "+iptvStartIndex);
        // clean iptv rows first if they have been generated
        cleanIptvRows(rowsAdapter, defaultHeadersCounter);

        //generate iptv rows
        Log.d(TAG, "handleIptvClickedItem: generateIptvRows");
        generateIptvRows(movie);
    }

    private <T> void cleanIptvRows(T rowsAdapter, int defaultHeadersCounter) {
        Log.d(TAG, "cleanIptvRows: ");
        if (rowsAdapter instanceof CategoryAdapter) {
            CategoryAdapter adapter = (CategoryAdapter) rowsAdapter;
            int rowSize = adapter.size() - 1;
            while (rowSize >= defaultHeadersCounter) {
//                                Log.d(TAG, "onItemClicked: remove row:" + iptvLastIndex);
                try {
                    removeRow(adapter, rowSize--);
                } catch (Exception e) {
                    Log.d(TAG, "loadOmarServerResult: error: " + e.getMessage());
                }

            }
            Log.d(TAG, "cleanIptvRows: done");
            return;
        }
        if (rowsAdapter instanceof ArrayObjectAdapter) {
            ArrayObjectAdapter adapter = (ArrayObjectAdapter) rowsAdapter;
            int rowSize = adapter.size() - 1;
            while (rowSize >= defaultHeadersCounter) {
//                                Log.d(TAG, "onItemClicked: remove row:" + iptvLastIndex);
                try {
//                    adapter.remove(
//                            adapter.get((rowSize--)
//                            ));
//                    removeRow(adapter, row_size--);
                } catch (Exception exception) {
                    Log.d(TAG, "handleItemClicked: error deleting iptv header on main fragment: " + exception.getMessage());
                }

            }
            Log.d(TAG, "cleanIptvRows: done");
        }
    }

    protected abstract <T> void removeRow(T rowsAdapter, int i);

    private void generateIptvRows(Movie movie) {
        Log.d(TAG, "generateIptvRows: " + movie.getVideoUrl());
        try {
//           showProgressDialog();
//        todo
//            IptvServer iptvServer = (IptvServer) ServerConfigRepository.getServer(Movie.SERVER_IPTV);
//            if (iptvServer == null) {
//                return;
//            }
//            CompletableFuture<Map<String, List<Movie>>> futureGroupedMovies = iptvServer.fetchAndGroupM3U8ContentAsync(movie, dbHelper);





            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
//                HashMap<String, ArrayList<Movie>> futureGroupedMovies = iptvServer.fetchAndGroupM3U8ContentAsync(movie, dbHelper);
//                Log.d(TAG, "generateIptvRows: " + futureGroupedMovies.size());

                M3U8ContentFetcher.fetchAndStoreM3U8Content(movie, result -> {
                    // Update UI with the result
//                updateChannelGroups(result);
                    // Print grouped movies


                    result.entrySet().stream()
                            .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                            .forEach(
                                    entry -> mainHandler.post(()
                                            -> generateCategory(entry.getKey(), entry.getValue(), false)
                                    )
                            );

                    Log.d(TAG, "movies: "+ result.size());
                });

            });
            executor.shutdown();

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, "الرجاء الانتظار...", Toast.LENGTH_LONG).show();
                }
            });

//            futureGroupedMovies.thenAcceptAsync(groupedMovies -> {
//                if (groupedMovies == null){
//                    return;
//                }
//                for (Map.Entry<String, List<Movie>> entry : groupedMovies.entrySet()) {
//                    String group = entry.getKey();
//                    List<Movie> groupMovies = entry.getValue();
//                    try {
//                        generateCategory(group, (ArrayList<Movie>) groupMovies, false);
//                    }catch (Exception e){
//                        Log.d(TAG, "loadOmarServerResult: error: generateCategory: "+e.getMessage());
//                    }
//
//
////                        }
////                    });
//                }
//            }).exceptionally(e -> {
//                // Handle any exceptions with grace (and maybe a touch of humor!)
//                Log.e(TAG, "Something went wrong: " + e.getMessage());
//                return null;
//            });


// This line waits for the completion of the future
//          hideProgressDialog();
        } catch (Exception e) {
            Log.d(TAG, "handleIptvClickedItem: " + e.getMessage());
        }
    }
//    void handleWatchButtonClick();
//    void handleOnActivityResult();

    public void loadCategoriesInBackground(String query) {

        if (query == null) {
            Log.d(TAG, "loadCategoriesInBackground: empty query");
            return;
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String finalQuery = query;
        executor.submit(() -> {
//            AbstractServer server = new MyCimaServer(activity, null);
//            ArrayList<Movie> movies = server.getHomepageMovies();
//
//            Category category = new Category(server.getLabel(), movies);
//            handler.post(new Runnable() {
//                @Override
//                public void run() {
//                    categoryList.add(category);
//                    categoryAdapter.notifyItemInserted(categoryList.size() - 1);
//                }
//            });


            Log.d(TAG, "loadHomepageRaws a " + finalQuery);
//            for (Map.Entry<String, AbstractServer> entry : ServerConfigRepository.getServers(dbHelper).entrySet()) {
//                AbstractServer server = entry.getValue();
//                ServerConfig config = ServerConfigRepository.getConfig(entry.getKey());
//
//                Log.d(TAG, "loadHomepageRaws config: " + config);
////                if (config == null || !config.isActive()){
//                if (config == null) {
//                    return;
//                }
////                if (server == null || server.getConfig() == null|| !server.getConfig().isActive()){
////                    continue;
////                }
//                Log.d(TAG, "loadCategoriesInBackground: " + server.getServerId());
//                if (
//                        server instanceof OldAkwamServer ||
//                                server instanceof AkwamServer ||
//                                server instanceof ArabSeedServer ||
//                                server instanceof CimaNowServer ||
//                                server instanceof LarozaServer ||
//                                server instanceof FaselHdServer ||
//                                server instanceof OmarServer ||
//                                server instanceof IptvServer ||
//                                server instanceof MyCimaServer ||
//                                server instanceof KooraServer
////                        (server instanceof KooraServer && !query.isEmpty())
//                ) {
//                    continue;
//                }
//
//                loadServerRow(server, finalQuery);
//            }

            loadHomepageHistoryRows();
        });
        executor.shutdown();
    }

    protected void loadHomepageHistoryRows() {
        return;
    }

    protected <T> void loadServerRow(AbstractServer server, String finalQuery) {
//        if (server instanceof OmarServer) {
//            loadOmarServerResult(finalQuery, server);
//            return;
//        }
//
//        if (server instanceof IptvServer) {
//            loadIptvServerResult(finalQuery, server);
//            return;
//        }

        // Update the RecyclerView on the main thread
        ArrayList<Movie> movies = server.search(finalQuery, new SearchCallback(), false);
    }

    protected void loadOmarServerResult(String query, AbstractServer server) {


        ExecutorService executor2 = Executors.newSingleThreadExecutor();
        executor2.submit(() -> {
            ArrayList<Movie> movies = server.search(query, new SearchCallback(), false);
        });
        executor2.shutdown();

//        ExecutorService executor = Executors.newSingleThreadExecutor();
//        executor.submit(() -> {
//            if (activity != null) {
//                ArrayList<Movie> movies = server.getHomepageMovies(new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//                    @Override
//                    public void onSuccess(ArrayList<Movie> result, String title) {
//                        Map<String, List<Movie>> moviesByCategory = result.stream()
//                                .flatMap(movie -> movie.getCategories().stream()
//                                        .map(category -> new AbstractMap.SimpleEntry<>(category, movie)))
//                                .collect(Collectors.groupingBy(
//                                        Map.Entry::getKey,
//                                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
//                                ));
//
//                        // Assuming you already have the 'moviesByCategory' map
//                        for (Map.Entry<String, List<Movie>> entry : moviesByCategory.entrySet()) {
//                            String category = entry.getKey();
//                            List<Movie> moviesInCategory = entry.getValue();
//                            Log.d(TAG, "loadOmarServerHomepage: " + category + ", " + moviesInCategory.size());
//                            loadMoviesRow(server, addRowToMainAdapter(category), (ArrayList) moviesInCategory);
//                        }
//                    }
//
//                    @Override
//                    public void onInvalidCookie(ArrayList<Movie> result) {
//
//                    }
//
//                    @Override
//                    public void onInvalidLink(ArrayList<Movie> result) {
//
//                    }
//
//                    @Override
//                    public void onInvalidLink(String message) {
//
//                    }
//                });
//
//            }
//
//        });
    }

    public <T> void onActivityResult(int requestCode, int resultCode, @Nullable Intent data, T clickedAdapter, int clickedMovieIndex) {
        Log.d(TAG, "onActivityResult: adapter item count ");
//        Log.d("TAG", "onActivityResult: adapter item count "+ clickedHorizontalMovieAdapter);

        // cases:
        // 5.Movie.REQUEST_CODE_MOVIE_LIST to extend the movie list in row
        // should pdate:
        // 1.movie list

        // returned from Browser result:
        // 1. in case it doesnt start with "##"
        //  - in case its COOKIE_STATE
        //  - and its not COOKIE_STATE


        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.d(TAG, "onActivityResult:RESULT_NOT_OK ");
            return;
        }

        Movie resultMovie = Util.recieveSelectedMovie(data);
//        Movie resultMovie = (Movie) data.getParcelableExtra(DetailsActivity.MOVIE);
//        Movie mainMovie = (Movie) data.getParcelableExtra(DetailsActivity.MAIN_MOVIE);
//        if (resultMovie != null && mainMovie != null){
//            resultMovie.setMainMovie(mainMovie);
//        }
        if (requestCode == Movie.REQUEST_CODE_EXOPLAYER) {
//            if (resultMovie == null) {
//                Log.d(TAG, "onActivityResult: REQUEST_CODE_EXOPLAYER but empty result movie");
//                return;
//            }
            Log.d(TAG, "onActivityResult: REQUEST_CODE_EXOPLAYER");
            Util.openExoPlayer(resultMovie, activity, true);
            // todo: handle dbHelper
//            updateRelatedMovieItem(clickedHorizontalMovieAdapter, clickedMovieIndex, resultMovie);
            updateClickedMovieItem(clickedAdapter, clickedMovieIndex, resultMovie);
//            dbHelper.addMainMovieToHistory(clickedAdapter.getMovieList().get(clickedMovieIndex));
            return;
        }

        if (requestCode == Movie.REQUEST_CODE_EXTERNAL_PLAYER) {
//            if (resultMovie == null) {
//                Log.d(TAG, "onActivityResult: REQUEST_CODE_EXTERNAL_PLAYER but empty result movie");
//                return;
//            }

            // todo: handle dbHelper
//            updateRelatedMovieItem(clickedHorizontalMovieAdapter, clickedMovieIndex, resultMovie);
            updateClickedMovieItem(clickedAdapter, clickedMovieIndex, resultMovie);
//            dbHelper.addMainMovieToHistory(clickedAdapter.getMovieList().get(clickedMovieIndex));
            return;
        }


//        ArrayList<Movie> resultMovieSublist = data.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);

        if (resultMovie.getSubList() != null && !resultMovie.getSubList().isEmpty() && clickedAdapter != null) {
//            updateMovieListOfHorizontalMovieAdapter(resultMovieSublist);
//            Log.d(TAG, "onActivityResult: updateMovieListOfMovieAdapter: "+ resultMovie);
            updateCurrentMovie(resultMovie);
//            Log.d(TAG, "onActivityResult: updateMovieListOfMovieAdapter: mainMovie: "+ resultMovie.getMainMovie());

//            updateMainMovieOnJsResult(resultMovie.getMainMovie(), resultMovie.getSubList());

            updateMovieListOfMovieAdapter((ArrayList<Movie>) resultMovie.getSubList(), clickedAdapter);
        }
//        Log.d(TAG, "onActivityResult: end: "+ resultMovie.getSubList());
//        Log.d(TAG, "onActivityResult: end: movie: "+ resultMovie);
//        Log.d(TAG, "onActivityResult: end: clickedAdapter: "+ clickedAdapter);
//        updateClickedMovieItem(clickedAdapter, clickedMovieIndex, resultMovie);
//        updateMovieListOfMovieAdapter(resultMovieSublist, clickedAdapter);
//        Gson gson = new Gson();
//
//        Movie resultMovie = (Movie) data.getSerializableExtra(DetailsActivity.MOVIE);
//        Type type = new TypeToken<List<Movie>>() {
//        }.getType();
//        String movieSublistString = data.getStringExtra(DetailsActivity.MOVIE_SUBLIST);
//
//        List<Movie> resultMovieSublist = gson.fromJson(movieSublistString, type);
//
//        String result = data.getStringExtra("result");
//
//
//        Log.d(TAG, "onActivityResult:RESULT_OK ");
//
//        //requestCode Movie.REQUEST_CODE_MOVIE_UPDATE is one movie object or 2 for a list of movies
//        //this result is only to update the clicked movie of the sublist only and in some cases to update the description of mSelectedMovie
//        //the id property of Movie object is used to identify the index of the clicked sublist movie
////        switch (requestCode) {
////            case Movie.REQUEST_CODE_MOVIE_UPDATE:
////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_MOVIE_UPDATE");
////            case Movie.REQUEST_CODE_MOVIE_LIST:
////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_MOVIE_LIST");
////                // returns null or movie
////                mSelectedMovie = updateMovieOnActivityResult(resultMovie, resultMovieSublist);
////                break;
////            case Movie.REQUEST_CODE_FETCH_HTML:
////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_FETCH_HTML");
////                mSelectedMovie = (Movie) server.handleOnActivityResultHtml(result, mSelectedMovie);
////                break;
////            case Movie.REQUEST_CODE_EXOPLAYER:
////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_EXOPLAYER");
////                resultMovie.setSubList(resultMovieSublist);
////                Util.openExoPlayer(resultMovie, activity);
////                // todo: handle dbHelper
////                // dbHelper.addMainMovieToHistory(mSelectedMovie);
////                mSelectedMovie = resultMovie;
////                break;
////            case Movie.REQUEST_CODE_EXTERNAL_PLAYER:
////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_EXTERNAL_PLAYER");
////                Util.openExternalVideoPlayer(resultMovie, activity);
////                // todo: handle dbHelper
////                // dbHelper.addMainMovieToHistory(mSelectedMovie);
////                mSelectedMovie = resultMovie;
////                break;
////            default:
////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE unknown: "+ requestCode);
////                mSelectedMovie = resultMovie;
////        }
//
////        updateRelatedMovieAdapter(mSelectedMovie);
    }

    private void updateMainMovieOnJsResult(Movie mainMovie, List<Movie> subList) {
        if (subList.isEmpty()) {
            return;
        }
        for (Movie mov : subList) {
            if (mainMovie == null) {
                mainMovie = mov;
            }
//            mov.setMainMovie(mainMovie);
            subList.set(subList.indexOf(mov), mov);
//            Log.d(TAG, "onActivityResult: mov main: 44: "+ mov.getMainMovie());
        }
    }

    public class SearchCallback implements ServerInterface.ActivityCallback<ArrayList<Movie>> {
        @Override
        public void onSuccess(ArrayList<Movie> result, String title) {
            Log.d(TAG, "onSuccess: " + title);
            if (result == null || result.isEmpty()) {
                Log.d(TAG, "onSuccess: search result is empty");
                return;
            }

            generateCategory(title, result, true);
        }

        @Override
        public void onInvalidCookie(ArrayList<Movie> result, String title) {
            Log.d(TAG, "onInvalidCookie: " + result);
            if (result == null || result.isEmpty()) {
                Log.d(TAG, "onInvalidCookie: search result is empty");
                return;
            }

            Log.d(TAG, "onInvalidCookie: generateCategory");
            generateCategory(title, result, true);
        }

        @Override
        public void onInvalidLink(ArrayList<Movie> result) {
            Log.d(TAG, "onInvalidLink ");
        }

        @Override
        public void onInvalidLink(String message) {
            Log.d(TAG, "onInvalidLink: loadOmarServerResult: " + message);
        }
    }
}