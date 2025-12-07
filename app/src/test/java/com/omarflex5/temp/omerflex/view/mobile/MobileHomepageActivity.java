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
import com.omerflex.view.BrowseErrorActivity;
import com.omerflex.view.mobile.entity.Category;
import com.omerflex.view.mobile.view.CategoryAdapter;
import com.omerflex.view.mobile.view.HorizontalMovieAdapter;
import com.omerflex.view.mobile.view.OnMovieClickListener;

import java.util.ArrayList;

public class MobileHomepageActivity extends AppCompatActivity {

    private SearchView searchView;
    public static String TAG = "MobileHomepageActivity";
    private RecyclerView recyclerView;
    private CategoryAdapter categoryAdapter;
//    private List<HorizontalMovieAdapter> categoryList;
    ServerManager serverManager;
    Activity activity;
    HorizontalMovieAdapter clickedHorizontalMovieAdapter;
    int clickedMovieIndex = 0;

    int defaultHeadersCounter = 0;
    int totalHeadersCounter = 0;
    Category clickedMovieAdapter;

    private Handler handler = new Handler();
    public MovieDbHelper dbHelper;
    private BrowseErrorActivity.MainViewControl mainViewControl;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mobile_homepage);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
        activity = this;
        dbHelper = MovieDbHelper.getInstance(activity);
        serverManager = new ServerManager(activity, null, null);

        // todo: serverManager.updateServers();
        searchView = findViewById(R.id.searchView);
        recyclerView = findViewById(R.id.recyclerView);

//        // Set up the vertical RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
//        categoryList = new ArrayList<>();
        categoryAdapter = new CategoryAdapter(this, new MovieItemClickListener(this));


        mainViewControl = new BrowseErrorActivity.MainViewControl(activity, null, dbHelper) {
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
            public <T> void handleMovieItemClick(Movie movie, int position, T rowsAdapter, T clickedRow, int defaultHeadersCounter) {
                super.handleMovieItemClick(movie, position, rowsAdapter, clickedRow, defaultHeadersCounter);
            }

            @Override
            protected void openDetailsActivity(Movie movie, Activity activity) {
                Log.d(TAG, "openDetailsActivity: SearchResult");
                Util.openMobileDetailsIntent(movie, activity, true);
            }

//            @Override
//            public void loadCategoriesInBackground(String query) {
//                super.loadCategoriesInBackground(query);
//            }

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
                Log.d(TAG, "updateCurrentMovie: MobileHomepage");
            }


            protected <T> void updateMovieListOfMovieAdapter(ArrayList<Movie> movies, T clickedAdapter) {
//                updateMovieListOfHorizontalMovieAdapter(movies);
                Log.d(TAG, "updateMovieListOfMovieAdapter: " + clickedAdapter);
                if (clickedAdapter instanceof HorizontalMovieAdapter) {
                    HorizontalMovieAdapter adapter = (HorizontalMovieAdapter) clickedAdapter;
                    extendMovieListOfHorizontalMovieAdapter(movies, adapter);
                    return;
                }

//                if (clickedAdapter instanceof Category) {
//                    Category adapter = (Category) clickedAdapter;
//                    Log.d(TAG, "updateMovieListOfMovieAdapter: "+adapter);
//                    extendMovieListOfHorizontalMovieAdapter(movies, adapter);
//                }
            }

            protected <T> T generateCategory(String title, ArrayList<Movie> movies, boolean isDefaultHeader) {
                return (T) generateCategoryView(title, movies, isDefaultHeader);
            }
        };

        mainViewControl.loadCategoriesInBackground("");


        recyclerView.setAdapter(categoryAdapter);

        // Handle the submit button click
        searchView.setOnQueryTextListener(getSearchViewListener());

        // Load categories in the background
//        loadCategoriesInBackground();
//        String url = "https://airmax.boats/airmaxtv963/airmaxtv369/309.ts|User-Agent=airmaxtv&Accept-Encoding=identity&Host=airmax.boats&Connection=Keep-Alive&Icy-MetaData=1";
//        Movie mov = new Movie();
//        mov.setVideoUrl(url);
//        Util.openExoPlayer(mov, activity, false);

        // todo: handle expired activity or device changed orientation
    }

    private void generateCategoryViewAsync(String title, ArrayList<Movie> movies, boolean isDefaultHeader) {
//        Category category = new Category(title, movies);
        Log.d(TAG, "generateCategoryViewAsync: " + movies);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                categoryAdapter.addCategory(title, movies);
//        adapter.notifyItemInserted(categoryAdapter.size() - 1);
                categoryAdapter.notifyItemInserted(categoryAdapter.size() - 1);
                if (isDefaultHeader) {
                    defaultHeadersCounter++;
                }
            }
        });
    }

    private HorizontalMovieAdapter generateCategoryView(String title, ArrayList<Movie> movies, boolean isDefaultHeader) {
//        Category category = new Category(title, movies);
        if (title == null){
            title = "default";
        }
        Log.d(TAG, "generateCategoryView: " + title+", movies: "+movies);
        HorizontalMovieAdapter adapter = null;
        try {
            adapter = categoryAdapter.addCategory(title, movies);
        }catch (Exception e){
            Log.d(TAG, "run: error notifying adapter: "+e.getMessage());
            return null;
        }

        if (isDefaultHeader) {
            defaultHeadersCounter++;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    categoryAdapter.notifyItemInserted(categoryAdapter.getItemCount());
                }catch (Exception e){
                    Log.d(TAG, "run: error notifying adapter: "+e.getMessage());
                }
            }
        });
//        adapter.notifyItemInserted(categoryAdapter.size() - 1);
//        new Handler(Looper.getMainLooper()).post(new Runnable() {
//            @Override
//            public void run() {
//                categoryAdapter.notifyItemInserted(categoryAdapter.size() - 1);
//                if (isDefaultHeader) {
//                    defaultHeadersCounter++;
//                }
//            }
//        });
        return adapter;
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
//
//            ArrayList<AbstractServer> serversList= ServerConfigRepository.getServers(dbHelper);
////            ArrayList<AbstractServer> serversList= new ArrayList<>();
//
//            Log.d(TAG, "loadHomepageRaws a "+serversList);
//            for (AbstractServer server : serversList) {
//
//                Log.d(TAG, "loadHomepageRaws server: " + server.getServerId());
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
//                // Update the RecyclerView on the main thread
//                ArrayList<Movie> movies = server.getHomepageMovies(new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//                    @Override
//                    public void onSuccess(ArrayList<Movie> result, String title) {
//                        generateCategory(title, result);
//                    }
//
//                    @Override
//                    public void onInvalidCookie(ArrayList<Movie> result, String title) {
//                        generateCategory(server.getLabel(), result);
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
////                if (movies == null || movies.isEmpty()) {
////                    continue;
////                }
//
//
//            }
//        });
//        executor.shutdown();
//    }

//    private void generateCategory(String title, ArrayList<Movie> movies) {
////        Category category = new Category(title, movies);
//        HorizontalMovieAdapter adapter = categoryAdapter.addCategory(title, movies);
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
////                categoryList.add(category);
//                categoryAdapter.notifyItemInserted(categoryList.size() - 1);
//            }
//        });
//        return adapter;
//    }

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

            mainViewControl.handleMovieItemClick(movie, position, categoryAdapter, horizontalMovieAdapter, defaultHeadersCounter);
//            ExecutorService executor = Executors.newSingleThreadExecutor();
//            executor.submit(() -> {
////                AbstractServer server = ServerManager.determineServer(movie, null, activity, null);
////                AbstractServer server = ServerConfigRepository.getServer(movie.getStudio());
////
////                if (movie.getState() == Movie.COOKIE_STATE){
////                    Log.d(TAG, "onMovieClick: COOKIE_STATE-0");
////                    MovieFetchProcess fetchProcess = server.fetch(
////                            movie,
////                            movie.getState(),
////                            new ServerInterface.ActivityCallback<Movie>() {
////                                @Override
////                                public void onSuccess(Movie result, String title) {
////                                    // todo: analyse case
////                                    Log.d(TAG, "onMovieClick: COOKIE_STATE onSuccess");
////                                    Util.openBrowserIntent(result, activity, false, true);
////                                }
////
////                                @Override
////                                public void onInvalidCookie(Movie result, String title) {
////                                    Log.d(TAG, "onMovieClick: COOKIE_STATE onInvalidCookie: ");
////                                    result.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
////                                    Util.openBrowserIntent(result, activity, true, true);
////                                }
////
////                                @Override
////                                public void onInvalidLink(Movie result) {
////                                    Log.d(TAG, "onMovieClick: COOKIE_STATE onInvalidLink: ");
////                                }
////
////                                @Override
////                                public void onInvalidLink(String message) {
////
////                                }
////                            }
////                    );
//////                    Log.d(TAG, "onMovieClick: COOKIE_STATE");
//////                    if (fetchProcess.stateCode == MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE) {
//////                        Util.openBrowserIntent(movie, activity, false, true);
//////                        return;
//////                    }
////                }
////                else if (movie.getStudio().equals(Movie.SERVER_IPTV)){
////                    Log.d(TAG, "onMovieClick: Movie.SERVER_IPTV");
////                    handleIptvClickedItem(movie, position, horizontalMovieAdapter);
////                }else if (movie.getState() == Movie.VIDEO_STATE){
////                    Log.d(TAG, "onMovieClick: Movie.SERVER_IPTV");
////                    Util.openExoPlayer(movie, activity, false);
////                }
////                else {
////                    Log.d(TAG, "onMovieClick: openMobileDetailsIntent");
////                    // Handle normal click event (e.g., open detail activity)
////                    Util.openMobileDetailsIntent(movie, activity, true);
////                }
////hiier
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

        private void handleIptvClickedItem(Movie movie, int position, HorizontalMovieAdapter horizontalMovieAdapter) {
            if (movie.getState() == Movie.IPTV_PLAY_LIST_STATE) {
                // Todo: generateCategories for a specific iptv list
            } else {
                Util.openExoPlayer(movie, activity, true);
            }
        }

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
        Log.d("TAG", "onActivityResult: adapter item count ");
//        Log.d("TAG", "onActivityResult: adapter item count "+ clickedHorizontalMovieAdapter);

        // cases:
        // 5.Movie.REQUEST_CODE_MOVIE_LIST to extend the movie list in row
        // should update:
        // 1.movie list

        // returned from Browser result:
        // 1. in case it doesnt start with "##"
        //  - in case its COOKIE_STATE
        //  - and its not COOKIE_STATE

        mainViewControl.onActivityResult(requestCode, resultCode, data, clickedHorizontalMovieAdapter, clickedMovieIndex);
        super.onActivityResult(requestCode, resultCode, data);


//        if (resultCode != Activity.RESULT_OK || data == null) {
//            Log.d(TAG, "onActivityResult:RESULT_NOT_OK ");
//            return;
//        }
//
//        if (requestCode == Movie.REQUEST_CODE_EXOPLAYER){
//            Movie resultMovie = (Movie) data.getParcelableExtra(DetailsActivity.MOVIE);
//            if (resultMovie == null){
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
//
//
//        ArrayList<Movie> resultMovieSublist = data.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);
//        if (resultMovieSublist != null && !resultMovieSublist.isEmpty()){
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
    }

    private void updateRelatedMovieItem(HorizontalMovieAdapter horizontalMovieAdapter, int clickedMovieIndex, Movie resultMovie) {
        horizontalMovieAdapter.getMovieList().set(clickedMovieIndex, resultMovie);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                horizontalMovieAdapter.notifyItemChanged(clickedMovieIndex);
            }
        });
    }

    private void updateMovieListOfHorizontalMovieAdapter(ArrayList<Movie> resultMovieSublist) {
        if (clickedHorizontalMovieAdapter != null) {
            extendMovieListOfHorizontalMovieAdapter(resultMovieSublist, clickedHorizontalMovieAdapter);
        }
    }

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

    private void extendMovieListOfHorizontalMovieAdapter(ArrayList<Movie> resultMovieSublist, Category category) {
        Log.d(TAG, "Category: p:" + ", s:" + category.getMovieAdapter());
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
//                    categoryList.add(category);
//                    categoryAdapter.notifyItemInserted(categoryList.size() - 1);
//                    horizontalMovieAdapter.notifyItemInserted(categoryList.size() - 1);
//                if (categoryList.contains(category)) {
//                    int cateIndex = categoryList.indexOf(category);
//                    categoryList.get(cateIndex).getMovieList().addAll(resultMovieSublist);
//                    categoryAdapter.notifyItemInserted(categoryList.size() - 1);
//                }

            }
        });
    }
}