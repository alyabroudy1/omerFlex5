package com.omarflex5.temp.omerflex.view.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.SearchView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.server.Util;
import com.omerflex.service.ServerManager;
import com.omerflex.service.database.MovieDbHelper;
import com.omerflex.view.DetailsActivity;
import com.omerflex.view.SearchViewControl;
import com.omerflex.view.mobile.view.CategoryAdapter;
import com.omerflex.view.mobile.view.HorizontalMovieAdapter;
import com.omerflex.view.mobile.view.OnMovieClickListener;

import java.util.ArrayList;

public class MobileSearchResultActivity extends AppCompatActivity {

    private SearchView searchView;
    public static String TAG = "MobileSearchResultActivity";
    private RecyclerView recyclerView;
    private CategoryAdapter categoryAdapter;
    ServerManager serverManager;
    Activity activity;
    HorizontalMovieAdapter clickedHorizontalMovieAdapter;
    int clickedMovieIndex = 0;
    int defaultHeadersCounter = 0;
    private Handler handler = new Handler();
    public MovieDbHelper dbHelper;
    private String query;

    private SearchViewControl searchViewControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mobile_search_result);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
        activity = this;
        dbHelper = MovieDbHelper.getInstance(activity);
        // todo: serverManager.updateServers();
        searchView = findViewById(R.id.searchView);
        recyclerView = findViewById(R.id.recyclerView);
        query = getIntent().getStringExtra(DetailsActivity.QUERY);
        Log.d(TAG, "onCreate: " + query);

//        // Set up the vertical RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

//        categoryAdapter = new CategoryAdapter(this, categoryList, new MovieItemClickListener(this));
        categoryAdapter = new CategoryAdapter(this, new MovieItemClickListener(this));


        recyclerView.setAdapter(categoryAdapter);

        // Handle the submit button click
        searchView.setOnQueryTextListener(getSearchViewListener());

        // Load categories in the background
//        loadCategoriesInBackground();
        query = getIntent().getStringExtra(DetailsActivity.QUERY);
        if (query == null) {
            Log.d(TAG, "loadRows: fail to receive the query");
            return;
        }
        query = query.trim();
        searchViewControl = new SearchViewControl(activity, null, dbHelper) {
            @Override
            public <T> void handleMovieItemClick(Movie movie, int position, T rowsAdapter, T clickedRow, int defaultHeadersCounter) {
                super.handleMovieItemClick(movie, position, rowsAdapter, clickedRow, defaultHeadersCounter);
            }

            @Override
            protected void openDetailsActivity(Movie movie, Activity activity) {
                Util.openMobileDetailsIntent(movie, activity, true);
            }

            @Override
            protected <T> void removeRow(T rowsAdapter, int i) {
                if (rowsAdapter instanceof CategoryAdapter) {
                        CategoryAdapter adapter = ((CategoryAdapter) rowsAdapter);
                        try {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    if (i < adapter.getItemCount()){
                                        adapter.remove(i);
                                        adapter.notifyItemRemoved(i);
                                    }
                                }
                            });

                        } catch (Exception exception) {
                            Log.d(TAG, "handleItemClicked: error deleting iptv header on main fragment: " + exception.getMessage());
                        }
                }
            }

            @Override
            public void loadCategoriesInBackground(String query) {
                super.loadCategoriesInBackground(query);
            }

            @Override
            protected <T> void updateClickedMovieItem(T clickedAdapter, int clickedMovieIndex, Movie resultMovie) {
                // If you need to handle specific adapter types, use instanceof and cast
                if (clickedAdapter instanceof HorizontalMovieAdapter) {
                    HorizontalMovieAdapter adapter = (HorizontalMovieAdapter) clickedAdapter;
                    updateRelatedMovieItem(adapter, clickedMovieIndex, resultMovie);
                }
                // Handle other adapter types similarly
            }

            @Override
            protected void updateCurrentMovie(Movie movie) {
                Log.d(TAG, "updateCurrentMovie: MobileSearchResult");
            }


            protected <T> void updateMovieListOfMovieAdapter(ArrayList<Movie> movies, T clickedAdapter) {
//                updateMovieListOfHorizontalMovieAdapter(movies);
                if (clickedAdapter instanceof HorizontalMovieAdapter) {
                    HorizontalMovieAdapter adapter = (HorizontalMovieAdapter) clickedAdapter;
                    extendMovieListOfHorizontalMovieAdapter(movies, adapter);
                }
            }

            protected <T> T generateCategory(String title, ArrayList<Movie> movies, boolean isDefaultHeader) {
                return (T) generateCategoryView(title, movies, isDefaultHeader);
            }
        };

        searchViewControl.loadCategoriesInBackground(query);
        // todo: handle expired activity or device changed orientation
    }

    @NonNull
    private SearchView.OnQueryTextListener getSearchViewListener() {
        return new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                // Handle the search query here (e.g., filter the category list)
                // For simplicity, this example doesn't implement filtering
                Log.d(TAG, "onQueryTextSubmit: " + s);
                Util.openSearchResultActivity(s, activity);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        };
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent: ");
        setIntent(intent);
        super.onNewIntent(intent);
    }

//    private void loadIptvServerResult(String query, AbstractServer server) {
//        ArrayList<Movie> tvChannelList = dbHelper.findMovieBySearchContext(Movie.SERVER_IPTV, query);
//        if (tvChannelList == null || tvChannelList.isEmpty()) {
//            Log.d(TAG, "iptv channels search result is empty ");
//            return;
//        }
//        generateCategoryView(server.getLabel(), tvChannelList);
////        ArrayObjectAdapter adapter = addRowToMainAdapter(server.getLabel(), server.getServerId());
////        activity.runOnUiThread(new Runnable() {
////            @Override
////            public void run() {
////                // String newName = rowName + ("(" + finalMovieList.size() + ")");
////                adapter.addAll(0, tvChannelList);
////            }
////        });
//    }

//    private void loadOmarServerResult(String query, AbstractServer server) {
//
//
//        ExecutorService executor2 = Executors.newSingleThreadExecutor();
//        executor2.submit(() -> {
//            ArrayList<Movie> movies = server.search(query, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//                @Override
//                public void onSuccess(ArrayList<Movie> result, String title) {
////                            generateCategory(title, result);
////                            if (server instanceof IptvServer) {
////                        //load history rows first
////                        loadHomepageHistoryRaws();
////
////                        loadMoviesRow(server, addRowToMainAdapter(server.getLabel()), null);
//////                            //channel list
//////                            ArrayList<Movie> channels = dbHelper.getIptvHomepageChannels();
//////                            if (channels.size() > 0) {
//////                                loadMoviesRow("tv", channels);
//////                            }
////                    } else {
//                    if (result == null || result.isEmpty()) {
//                        Log.d(TAG, "onSuccess: search result is empty");
//                        return;
//                    }
//                    generateCategory(title, result);
////                    }
//                }
//
//                @Override
//                public void onInvalidCookie(ArrayList<Movie> result) {
//                    Log.d(TAG, "onInvalidCookie: " + result);
////                            loadMoviesRow(server, addRowToMainAdapter(server.getLabel()), result);
//
//                    if (result == null || result.isEmpty()) {
//                        Log.d(TAG, "onInvalidCookie: search result is empty");
//                        return;
//                    }
//                    generateCategory(server.getLabel(), result);
//                }
//
//                @Override
//                public void onInvalidLink(ArrayList<Movie> result) {
//
//                }
//
//                @Override
//                public void onInvalidLink(String message) {
//                    Log.d(TAG, "onInvalidLink: loadOmarServerResult: " + message);
//                }
//            });
//
//        });
//        executor2.shutdown();
//
////        ExecutorService executor = Executors.newSingleThreadExecutor();
////        executor.submit(() -> {
////            if (activity != null) {
////                ArrayList<Movie> movies = server.getHomepageMovies(new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
////                    @Override
////                    public void onSuccess(ArrayList<Movie> result, String title) {
////                        Map<String, List<Movie>> moviesByCategory = result.stream()
////                                .flatMap(movie -> movie.getCategories().stream()
////                                        .map(category -> new AbstractMap.SimpleEntry<>(category, movie)))
////                                .collect(Collectors.groupingBy(
////                                        Map.Entry::getKey,
////                                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
////                                ));
////
////                        // Assuming you already have the 'moviesByCategory' map
////                        for (Map.Entry<String, List<Movie>> entry : moviesByCategory.entrySet()) {
////                            String category = entry.getKey();
////                            List<Movie> moviesInCategory = entry.getValue();
////                            Log.d(TAG, "loadOmarServerHomepage: " + category + ", " + moviesInCategory.size());
////                            loadMoviesRow(server, addRowToMainAdapter(category), (ArrayList) moviesInCategory);
////                        }
////                    }
////
////                    @Override
////                    public void onInvalidCookie(ArrayList<Movie> result) {
////
////                    }
////
////                    @Override
////                    public void onInvalidLink(ArrayList<Movie> result) {
////
////                    }
////
////                    @Override
////                    public void onInvalidLink(String message) {
////
////                    }
////                });
////
////            }
////
////        });
//    }


//    private void loadCategoriesInBackground() {
//        ExecutorService executor = Executors.newSingleThreadExecutor();
//        executor.submit(() -> {
////            AbstractServer server = new MyCimaServer(activity, null);
////            ArrayList<Movie> movies = server.getHomepageMovies();
////
////            Category category = new Category(server.getLabel(), movies);
////            handler.post(new Runnable() {
////                @Override
////                public void run() {
////                    categoryList.add(category);
////                    categoryAdapter.notifyItemInserted(categoryList.size() - 1);
////                }
////            });
//
//            query = getIntent().getStringExtra(DetailsActivity.QUERY);
//            Log.d(TAG, "loadHomepageRaws a " + query);
//            for (AbstractServer server : ServerConfigRepository.getServers(dbHelper)) {
//                ServerConfig config = ServerConfigRepository.getConfig(server.getServerId());
//
//                Log.d(TAG, "loadHomepageRaws config: " + config);
////                if (config == null || !config.isActive()){
//                if (config == null) {
//                    return;
//                }
////                if (server == null || server.getConfig() == null|| !server.getConfig().isActive()){
////                    continue;
////                }
////                if (
////                        server instanceof OldAkwamServer ||
////                                server instanceof CimaClubServer ||
//////                                    server instanceof FaselHdController ||
////                                    server instanceof AkwamServer ||
////                                    server instanceof ArabSeedServer ||
////                                    server instanceof IptvServer ||
////                                    server instanceof MyCimaServer
////                ) {
////                    continue;
////                }
//
//                if (server instanceof OmarServer) {
//                    loadOmarServerResult(query, server);
//                    continue;
//                }
//
//                if (server instanceof IptvServer) {
//                    loadIptvServerResult(query, server);
//                    continue;
//                }
//
//                // Update the RecyclerView on the main thread
//                ArrayList<Movie> movies = server.search(query, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//                    @Override
//                    public void onSuccess(ArrayList<Movie> result, String title) {
//                        generateCategoryView(server.getLabel(), result);
//                    }
//
//                    @Override
//                    public void onInvalidCookie(ArrayList<Movie> result) {
//                        Log.d(TAG, "onInvalidCookie: ");
//                    }
//
//                    @Override
//                    public void onInvalidLink(ArrayList<Movie> result) {
//                        Log.d(TAG, "onInvalidLink: ");
//                    }
//
//                    @Override
//                    public void onInvalidLink(String message) {
//                        Log.d(TAG, "onInvalidLink: ");
//                    }
//                });
//
//            }
//        });
//        executor.shutdown();
//    }

    private HorizontalMovieAdapter generateCategoryView(String title, ArrayList<Movie> movies, boolean isDefaultHeader) {

        HorizontalMovieAdapter adapter = categoryAdapter.addCategory(title, movies);
        if (isDefaultHeader) {
            defaultHeadersCounter++;
        }
        if (isDefaultHeader) {
            defaultHeadersCounter++;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                categoryAdapter.notifyItemInserted(categoryAdapter.size() - 1);

            }
        });
        return adapter;
    }

    public class MovieItemClickListener implements OnMovieClickListener {
        Activity activity;

        CategoryAdapter categoryAdapter;

        public MovieItemClickListener(Activity activity) {
            this.activity = activity;
        }

        public void onMovieClick(Movie movie, int position, HorizontalMovieAdapter horizontalMovieAdapter) {
            // Check if the clicked movie matches the criteria to extend the category
            //            Log.d(TAG, "onMovieClick: "+ categoryAdapter.getItemCount());
            clickedHorizontalMovieAdapter = horizontalMovieAdapter;
            clickedMovieIndex = position;

            searchViewControl.handleMovieItemClick(movie, position, categoryAdapter, horizontalMovieAdapter, defaultHeadersCounter);
//            ExecutorService executor = Executors.newSingleThreadExecutor();
//            executor.submit(() -> {
////                AbstractServer server = ServerManager.determineServer(movie, null, activity, null);
//                AbstractServer server = ServerConfigRepository.getServer(movie.getStudio());
//
//                if (server == null){
//                    Toast.makeText(activity, "Unknown Server", Toast.LENGTH_SHORT).show();
//                }
//                if (movie.getState() == Movie.COOKIE_STATE){
//                    Log.d(TAG, "onMovieClick: COOKIE_STATE-0");
//                    MovieFetchProcess fetchProcess = server.fetch(
//                            movie,
//                            movie.getState(),
//                            new ServerInterface.ActivityCallback<Movie>() {
//                                @Override
//                                public void onSuccess(Movie result, String title) {
//                                    // todo: analyse case
//                                    Log.d(TAG, "onMovieClick: COOKIE_STATE onSuccess");
//                                    Util.openBrowserIntent(result, activity, false, true);
//                                }
//
//                                @Override
//                                public void onInvalidCookie(Movie result) {
//                                    Log.d(TAG, "onMovieClick: COOKIE_STATE onInvalidCookie: ");
//                                    result.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//                                    Util.openBrowserIntent(result, activity, true, true);
//                                }
//
//                                @Override
//                                public void onInvalidLink(Movie result) {
//                                    Log.d(TAG, "onMovieClick: COOKIE_STATE onInvalidLink: ");
//                                }
//
//                                @Override
//                                public void onInvalidLink(String message) {
//
//                                }
//                            }
//                    );
////                    Log.d(TAG, "onMovieClick: COOKIE_STATE");
////                    if (fetchProcess.stateCode == MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE) {
////                        Util.openBrowserIntent(movie, activity, false, true);
////                        return;
////                    }
//                }
//                else if (movie.getStudio().equals(Movie.SERVER_IPTV)){
//                    Log.d(TAG, "onMovieClick: Movie.SERVER_IPTV");
//                    handleIptvClickedItem(movie, position, horizontalMovieAdapter);
//                }
//                else {
//                    Log.d(TAG, "onMovieClick: openMobileDetailsIntent");
//                    // Handle normal click event (e.g., open detail activity)
//                    Util.openMobileDetailsIntent(movie, activity, true);
//                }
//
////                if (shouldExtendCategory(movie)) {
////                    Log.d(TAG, "onMovieClick: shouldExtendCategory");
////                    extendMovieListForCategory(movie, position, categoryAdapter, horizontalMovieAdapter);
////                } else {
////                    Log.d(TAG, "onMovieClick: openMobileDetailsIntent");
////                    // Handle normal click event (e.g., open detail activity)
////                    Util.openMobileDetailsIntent(movie, activity, true);
////                }
//            });
//            executor.shutdown();
        }

//        private void handleIptvClickedItem(Movie movie, int position, HorizontalMovieAdapter horizontalMovieAdapter) {
//            if (movie.getState() == Movie.PLAYLIST_STATE) {
//                // Todo: generateCategories for a specific iptv list
//            } else {
//                Util.openExoPlayer(movie, activity, true);
//            }
//        }

//        private void fetchCookie(Movie movie) {
//            AbstractServer server = ServerManager.determineServer(movie, null, activity, null);
//            if (server == null) {
//                return;
//            }
//            server.fetch(movie, movie.getState());
//        }

        public CategoryAdapter getCategoryAdapter() {
            return categoryAdapter;
        }

        public void setCategoryAdapter(CategoryAdapter categoryAdapter) {
            this.categoryAdapter = categoryAdapter;
        }

        private boolean shouldExtendCategory(Movie movie) {
            return movie.getState() == Movie.NEXT_PAGE_STATE;
//            return true;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Log.d(TAG, "onActivityResult: ");
        searchViewControl.onActivityResult(requestCode, resultCode, data, clickedHorizontalMovieAdapter, clickedMovieIndex);
        super.onActivityResult(requestCode, resultCode, data);
    }
//    protected void onActivityResult_old(int requestCode, int resultCode, @Nullable Intent data) {
//        Log.d("TAG", "onActivityResult: adapter item count ");
////        Log.d("TAG", "onActivityResult: adapter item count "+ clickedHorizontalMovieAdapter);
//
//        // cases:
//        // 5.Movie.REQUEST_CODE_MOVIE_LIST to extend the movie list in row
//        // should update:
//        // 1.movie list
//
//        // returned from Browser result:
//        // 1. in case it doesnt start with "##"
//        //  - in case its COOKIE_STATE
//        //  - and its not COOKIE_STATE
//
//
//        if (resultCode != Activity.RESULT_OK || data == null) {
//            Log.d(TAG, "onActivityResult:RESULT_NOT_OK ");
//            return;
//        }
//
//        if (requestCode == Movie.REQUEST_CODE_EXOPLAYER) {
//            Movie resultMovie = (Movie) data.getParcelableExtra(DetailsActivity.MOVIE);
//            if (resultMovie == null) {
//                return;
//            }
//            Util.openExoPlayer(resultMovie, activity, true);
//            // todo: handle dbHelper
//            updateRelatedMovieItem(clickedHorizontalMovieAdapter, clickedMovieIndex, resultMovie);
//            dbHelper.addMainMovieToHistory(clickedHorizontalMovieAdapter.getMovieList().get(clickedMovieIndex));
//            return;
//        }
//
//
//        ArrayList<Movie> resultMovieSublist = data.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);
//        if (resultMovieSublist != null && !resultMovieSublist.isEmpty()) {
//            updateMovieListOfHorizontalMovieAdapter(resultMovieSublist);
//        }
////        Gson gson = new Gson();
////
////        Movie resultMovie = (Movie) data.getSerializableExtra(DetailsActivity.MOVIE);
////        Type type = new TypeToken<List<Movie>>() {
////        }.getType();
////        String movieSublistString = data.getStringExtra(DetailsActivity.MOVIE_SUBLIST);
////
////        List<Movie> resultMovieSublist = gson.fromJson(movieSublistString, type);
////
////        String result = data.getStringExtra("result");
////
////
////        Log.d(TAG, "onActivityResult:RESULT_OK ");
////
////        //requestCode Movie.REQUEST_CODE_MOVIE_UPDATE is one movie object or 2 for a list of movies
////        //this result is only to update the clicked movie of the sublist only and in some cases to update the description of mSelectedMovie
////        //the id property of Movie object is used to identify the index of the clicked sublist movie
//////        switch (requestCode) {
//////            case Movie.REQUEST_CODE_MOVIE_UPDATE:
//////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_MOVIE_UPDATE");
//////            case Movie.REQUEST_CODE_MOVIE_LIST:
//////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_MOVIE_LIST");
//////                // returns null or movie
//////                mSelectedMovie = updateMovieOnActivityResult(resultMovie, resultMovieSublist);
//////                break;
//////            case Movie.REQUEST_CODE_FETCH_HTML:
//////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_FETCH_HTML");
//////                mSelectedMovie = (Movie) server.handleOnActivityResultHtml(result, mSelectedMovie);
//////                break;
//////            case Movie.REQUEST_CODE_EXOPLAYER:
//////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_EXOPLAYER");
//////                resultMovie.setSubList(resultMovieSublist);
//////                Util.openExoPlayer(resultMovie, activity);
//////                // todo: handle dbHelper
//////                // dbHelper.addMainMovieToHistory(mSelectedMovie);
//////                mSelectedMovie = resultMovie;
//////                break;
//////            case Movie.REQUEST_CODE_EXTERNAL_PLAYER:
//////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_EXTERNAL_PLAYER");
//////                Util.openExternalVideoPlayer(resultMovie, activity);
//////                // todo: handle dbHelper
//////                // dbHelper.addMainMovieToHistory(mSelectedMovie);
//////                mSelectedMovie = resultMovie;
//////                break;
//////            default:
//////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE unknown: "+ requestCode);
//////                mSelectedMovie = resultMovie;
//////        }
////
//////        updateRelatedMovieAdapter(mSelectedMovie);
//        super.onActivityResult(requestCode, resultCode, data);
//    }

    private void updateRelatedMovieItem(HorizontalMovieAdapter horizontalMovieAdapter, int clickedMovieIndex, Movie resultMovie) {
        horizontalMovieAdapter.getMovieList().set(clickedMovieIndex, resultMovie);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                horizontalMovieAdapter.notifyItemChanged(clickedMovieIndex);
            }
        });

    }

//    private void updateMovieListOfHorizontalMovieAdapter(ArrayList<Movie> resultMovieSublist) {
//        if (clickedHorizontalMovieAdapter != null) {
//            extendMovieListOfHorizontalMovieAdapter(resultMovieSublist, clickedHorizontalMovieAdapter);
//        }
//    }

    private void extendMovieListOfHorizontalMovieAdapter(ArrayList<Movie> resultMovieSublist, HorizontalMovieAdapter horizontalMovieAdapter) {
        Log.d(TAG, "extendMovieListOfHorizontalMovieAdapter: p:" + ", s:" + horizontalMovieAdapter.getItemCount());
        horizontalMovieAdapter.getMovieList().addAll(resultMovieSublist);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
//                    categoryList.add(category);
//                    categoryAdapter.notifyItemInserted(categoryList.size() - 1);
//                    horizontalMovieAdapter.notifyItemInserted(categoryList.size() - 1);
                horizontalMovieAdapter.notifyDataSetChanged();
            }
        });
    }

}