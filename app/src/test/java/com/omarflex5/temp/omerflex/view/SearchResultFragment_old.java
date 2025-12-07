package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.Util;
import com.omerflex.service.ServerManager;
import com.omerflex.service.database.MovieDbHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class SearchResultFragment_old extends BrowseSupportFragment {
    private static final String TAG = "SearchResultFragment";
    private List<AbstractServer> servers = new ArrayList<>();
    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private static final int GRID_ITEM_WIDTH = 200;
    private static final int GRID_ITEM_HEIGHT = 200;
    private static final int NUM_ROWS = 6;
    private static final int NUM_COLS = 15;

    private final Handler mHandler = new Handler();
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private String mBackgroundUri;
    private BackgroundManager mBackgroundManager;

    ArrayObjectAdapter rowsAdapter;
    private int ROWS_COUNTER = 0;
    WebView webView;
    Movie clickedMovie;
    int clickedMovieIndex = 0;
    int defaultHeadersCounter = 0;
    ArrayObjectAdapter clickedMovieAdapter;
    Fragment fragment = this;
    Activity activity;
    ArrayObjectAdapter faselAdapter;
    ArrayObjectAdapter cimaClubAdapter;
    ArrayObjectAdapter cima4uAdapter;
    ArrayObjectAdapter shahidAdapter;
    ArrayObjectAdapter arabSeedAdapter;
    ArrayObjectAdapter tvAdapter;

    String query;
    private SearchViewControl searchViewControl;
    int requestCounter = 0;
    //    private GeckoBroadcastReceiver geckoReceiver;
//
//    private GeckoService geckoService;
    private boolean isBound = false;
    ServerManager serverManager;
    public MovieDbHelper dbHelper;
    private boolean isInitialized = false;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        start();
        super.onActivityCreated(savedInstanceState);
    }

    public void start() {
        if (isInitialized) {
            return;
        }

        activity = getActivity();
        if (activity == null) {
            Log.d(TAG, "start: fail to identify the activity of SearchResultFragment");
            return;
        }
        dbHelper = MovieDbHelper.getInstance(activity);
        serverManager = new ServerManager(activity, fragment, null);

        rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
//        webView = getActivity().findViewById(com.omerflex.R.id.webView_main);

//// Register the broadcast receiver
//        geckoReceiver = new GeckoBroadcastReceiver();
//        IntentFilter filter = new IntentFilter("com.example.gecko.broadcast");
//        getActivity().registerReceiver(geckoReceiver, filter);

        prepareBackgroundManager();

        setupUIElements();

        loadRows();

        setupEventListeners();
        isInitialized = true;
    }

    @Override
    public void onStart() {
        start();
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mBackgroundTimer) {
            Log.d(TAG, "onDestroy: " + mBackgroundTimer.toString());
            mBackgroundTimer.cancel();
//            webView.stopLoading();
            // webView.destroy();
        }
        // Unregister the broadcast receiver
        //  getActivity().unregisterReceiver(geckoReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
//        webView.stopLoading();
        //webView.destroy();
    }

//    private ArrayObjectAdapter addRowToMainAdapter(String label, String serverId) {
//        ArrayObjectAdapter adapter = getServerAdapter(serverId);
//
//        HeaderItem header = new HeaderItem(ROWS_COUNTER++, label);
//        new Handler(Looper.getMainLooper()).post(() -> {
//            rowsAdapter.add(new ListRow(header, adapter));
//        });
//
//        return adapter;
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
//                    if (result == null || result.isEmpty()){
//                        Log.d(TAG, "onSuccess: search result is empty");
//                        return;
//                    }
//                    ArrayObjectAdapter adapter = addRowToMainAdapter(title, server.getServerId());
//                    activity.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            // String newName = rowName + ("(" + finalMovieList.size() + ")");
//                            adapter.addAll(0, result);
//                        }
//                    });
////                    }
//                }
//
//                @Override
//                public void onInvalidCookie(ArrayList<Movie> result) {
//                    Log.d(TAG, "onInvalidCookie: " + result);
////                            loadMoviesRow(server, addRowToMainAdapter(server.getLabel()), result);
//
//                    if (result == null || result.isEmpty()){
//                        Log.d(TAG, "onInvalidCookie: search result is empty");
//                        return;
//                    }
//                    ArrayObjectAdapter adapter = addRowToMainAdapter(server.getLabel(), server.getServerId());
//                    activity.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            // String newName = rowName + ("(" + finalMovieList.size() + ")");
//                            adapter.addAll(0, result);
//                        }
//                    });
//                }
//
//                @Override
//                public void onInvalidLink(ArrayList<Movie> result) {
//
//                }
//
//                @Override
//                public void onInvalidLink(String message) {
//                    Log.d(TAG, "onInvalidLink: loadOmarServerResult: "+ message);
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


//    private void loadSearchResultRaws() {
//        Log.d(TAG, "loadSearchResultRaws ");
//        ExecutorService executor = Executors.newSingleThreadExecutor();
//        executor.submit(() -> {
//            for (AbstractServer server : ServerConfigRepository.getServers(dbHelper)) {
//                if (server == null) {
//                    Log.d(TAG, "loadSearchResultRaws: unknown server");
//                    continue;
//                }
//
//                try {
//
//                    if (server instanceof OmarServer) {
//                        loadOmarServerResult(query, server);
//                        continue;
//                    }
//
//                    if (server instanceof IptvServer) {
//                        loadIptvServerResult(query, server);
//                        continue;
//                    }
//                    ArrayObjectAdapter adapter = addRowToMainAdapter(server.getLabel(), server.getServerId());
//
//                    ExecutorService executor2 = Executors.newSingleThreadExecutor();
//                    executor2.submit(() -> {
//                        try {
//                            ArrayList<Movie> movies = server.search(query, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//                                @Override
//                                public void onSuccess(ArrayList<Movie> result, String title) {
//                                    if (result == null || result.isEmpty()){
//                                        Log.d(TAG, "onSuccess: search result is empty");
//                                        return;
//                                    }
//                                    activity.runOnUiThread(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            // String newName = rowName + ("(" + finalMovieList.size() + ")");
//                                            adapter.addAll(0, result);
//                                        }
//                                    });
//                                }
//
//                                @Override
//                                public void onInvalidCookie(ArrayList<Movie> result) {
//                                    Log.d(TAG, "onInvalidCookie: ");
//                                    activity.runOnUiThread(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            // String newName = rowName + ("(" + finalMovieList.size() + ")");
//                                            adapter.addAll(0, result);
//                                        }
//                                    });
//                                }
//
//                                @Override
//                                public void onInvalidLink(ArrayList<Movie> result) {
//
//                                }
//
//                                @Override
//                                public void onInvalidLink(String message) {
//                                    Log.d(TAG, "onInvalidLink: "+ message);
//                                }
//                            });
//                            // it done in new thread
//                        } catch (Exception exception) {
//                            Log.d(TAG, "loadHomepageRaws: error: " + server.getServerId() + ", " + exception.getMessage());
//                        }
//
//                    });
//                    executor2.shutdown();
//                } catch (Exception exception) {
//                    Log.d(TAG, "loadHomepageRaws: error: " + exception.getMessage());
//                    exception.printStackTrace();
//                }
//            }
//
//        });
//        executor.shutdown();
//
////        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new CardPresenter());
////        AbstractServer iptvServer = IptvServer.getInstance(activity, fragment);
////        HeaderItem header = new HeaderItem(ROWS_COUNTER++, iptvServer.getLabel());
////        rowsAdapter.add(new ListRow(header, adapter));
////
////
////        ExecutorService executor = Executors.newSingleThreadExecutor();
////        executor.submit(() -> {
////            try {
////                ArrayList<Movie> movies = iptvServer.search(query);
////                // it done in new thread
////                if (movies.size() > 0) {
////                    if (activity != null) {
////                        activity.runOnUiThread(new Runnable() {
////                            @Override
////                            public void run() {
////                                // String newName = rowName + ("(" + finalMovieList.size() + ")");
////                                adapter.addAll(0, movies);
////                            }
////                        });
////                    }
////                }
////            } catch (Exception exception) {
////                Log.d(TAG, "loadHomepageRaws: error: " + exception.getMessage());
////            }
////        });
////        executor.shutdown();
////
////        ExecutorService executor2 = Executors.newSingleThreadExecutor();
////        //load iptv m3u8 file list from google drive
////        executor2.submit(this::loadIptvGDriveFileList);
////        executor2.shutdown();
//    }

//    private void loadIptvServerResult(String query, AbstractServer server) {
//        ArrayList<Movie> tvChannelList = dbHelper.findMovieBySearchContext(Movie.SERVER_IPTV, query);
//        if (tvChannelList == null || tvChannelList.isEmpty()){
//            Log.d(TAG, "iptv channels search result is empty ");
//            return;
//        }
//        ArrayObjectAdapter adapter = addRowToMainAdapter(server.getLabel(), server.getServerId());
//        activity.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                // String newName = rowName + ("(" + finalMovieList.size() + ")");
//                adapter.addAll(0, tvChannelList);
//            }
//        });
//
//    }


//    private void loadMoviesRow(String label, ArrayList<Movie> movies) {
//        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new CardPresenter());
//        HeaderItem header = new HeaderItem(ROWS_COUNTER++, label);
//        rowsAdapter.add(new ListRow(header, adapter));
//
//        ExecutorService executor = Executors.newSingleThreadExecutor();
//        executor.submit(() -> {
//            if (activity != null) {
//                activity.runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        // String newName = rowName + ("(" + finalMovieList.size() + ")");
//                        adapter.addAll(0, movies);
//                    }
//                });
//            }
//
//        });
//
//        executor.shutdown();
//    }

    private void loadRows() {
        query = activity.getIntent().getStringExtra("query");
        if (query == null) {
            Log.d(TAG, "loadRows: fail to receive the query");
            return;
        }
        query = query.trim();

//        faselAdapter = new ArrayObjectAdapter(new CardPresenter());
//        cimaClubAdapter = new ArrayObjectAdapter(new CardPresenter());
//        cima4uAdapter = new ArrayObjectAdapter(new CardPresenter());
//        shahidAdapter = new ArrayObjectAdapter(new CardPresenter());
//        arabSeedAdapter = new ArrayObjectAdapter(new CardPresenter());
//        tvAdapter = new ArrayObjectAdapter(new CardPresenter());
//        loadSearchResultRaws();
//        hier
//
//
//
////        AbstractServer omerServer = OmarServer.getInstance(getActivity(), fragment);
////        if (omerServer.getConfig() == null || omerServer.getConfig().isActive){
////            loadServerRow("عمر", omerServer, new ArrayObjectAdapter(new CardPresenter()), query);
////        }
//
//        AbstractServer myCimaController = MyCimaController.getInstance(getActivity(), fragment);
//        if (myCimaController.getConfig() == null || myCimaController.getConfig().isActive){
//            loadServerRow("ماي سيما", myCimaController, new ArrayObjectAdapter(new CardPresenter()), query);
//        }
//
//        AbstractServer arabseedController = ArabSeedController.getInstance(fragment, getActivity());
//        if (arabseedController.getConfig() == null || arabseedController.getConfig().isActive){
//            loadServerRow(arabseedController.getLabel(), arabseedController,  new ArrayObjectAdapter(new CardPresenter()), query);
//        }
//
//        AbstractServer oldAkwamController = OldAkwamController.getInstance(getActivity(), fragment);
//        if (oldAkwamController.getConfig() == null || oldAkwamController.getConfig().isActive){
//            loadServerRow("اكوام القديم", oldAkwamController,  new ArrayObjectAdapter(new CardPresenter()), query);
//        }
//
//
//        AbstractServer akwamController = AkwamController.getInstance(getActivity(), fragment);
//        if (akwamController.getConfig() == null || akwamController.getConfig().isActive){
//            loadServerRow("اكوام", akwamController,  new ArrayObjectAdapter(new CardPresenter()), query);
//        }
//
//        AbstractServer watanFlixController = WatanFlixController.getInstance(fragment, getActivity());
//        if (watanFlixController.getConfig() == null || watanFlixController.getConfig().isActive){
//            loadServerRow("watan", watanFlixController, new ArrayObjectAdapter(new CardPresenter()), query);
//        }
//
//        AbstractServer faselHdController = FaselHdController.getInstance(this, getActivity());
//        if (faselHdController.getConfig() == null || faselHdController.getConfig().isActive){
//            loadServerRow("فاصل", faselHdController, faselAdapter, query);
//        }
//
//        AbstractServer cimaClubController = CimaClubController.getInstance(this, getActivity());
//        if (cimaClubController.getConfig() == null || cimaClubController.getConfig().isActive){
//            loadServerRow("سيماكلوب", cimaClubController, cimaClubAdapter, query);
//        }


        //     loadServerRow("اكوام القديم", OldAkwamController.getInstance(getActivity()),null, query);
        //       loadServerRow("سيمافوريو", Cima4uController.getInstance(fragment, getActivity()), cima4uAdapter, query);

//zzz        loadServerRow("شاهد فوريو", Shahid4uController.getInstance(this, getActivity()), shahidAdapter, query);
        //      loadServerRow("arabseed", ArabSeedController.getInstance(this, getActivity()), arabSeedAdapter, query);
//        CookieManager cookieManager = CookieManager.getInstance();

//        Log.d(TAG, "loadRows: cookies:"+cookieManager.getCookie("https://www.faselhd.club"));
//        if (cookieManager.getCookie("https://www.faselhd.club/?s=sonic") != null){
//            FaselHdController.getInstance(getActivity()).setCookies(cookieManager.getCookie("https://www.faselhd.club/?s=sonic"));
//
//            loadServerRow("فاصل", FaselHdController.getInstance(this, getActivity()), query);
//        }
// hhhhhhhhhhhhhh       loadServerRow("قنوات", IptvController.getInstance(getActivity(), this), tvAdapter, query);
//        loadServerRow("مسلسلات",new SeriesTimeController(new ArrayObjectAdapter(new CardPresenter()), getActivity()), query);
//

//        loadServerRow("جوجل",null, query);

//        Movie mm = new Movie();
//        mm.setStudio(Movie.SERVER_FASELHD);
//        mm.setState(Movie.RESULT_STATE);
//        //  mm.setVideoUrl("https://ciima-clup.quest/c135");
//        mm.setVideoUrl("https://www.faselhd.club/?s=sonic");
//
//        Log.d(TAG, "loadRows: cookie:"+CookieManager.getInstance().getCookie(mm.getVideoUrl()));
//
//        Intent browse = new Intent(getActivity(), BrowserActivity.class);
//        browse.putExtra(DetailsActivity.MOVIE, (Serializable) mm);
//        startActivityForResult(browse, 0);

        setAdapter(rowsAdapter);


        searchViewControl = new SearchViewControl(activity, fragment, dbHelper) {
            @Override
            public <T> void handleMovieItemClick(Movie movie, int position, T rowsAdapter, T clickedRow, int defaultHeadersCounter) {
                super.handleMovieItemClick(movie, position, rowsAdapter, clickedRow, defaultHeadersCounter);
            }

            @Override
            protected void openDetailsActivity(Movie movie, Activity activity) {
                Log.d(TAG, "openDetailsActivity: SearchResult");
                Util.openVideoDetailsIntent(movie, activity);
            }

            @Override
            protected <T> void removeRow(T rowsAdapter, int i) {
                if (rowsAdapter instanceof ArrayObjectAdapter){
                    try {
                        ArrayObjectAdapter adapter = ((ArrayObjectAdapter) rowsAdapter);
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (adapter.size() > i ){
                                    adapter.remove(adapter.get(i));
                                    adapter.notifyItemRangeChanged(0, adapter.size());
                                    ROWS_COUNTER--;
                                    Log.d(TAG, "run: item "+i+ " removed");
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
                if (clickedAdapter instanceof ArrayObjectAdapter) {
                    ArrayObjectAdapter adapter = (ArrayObjectAdapter) clickedAdapter;
                    updateRelatedMovieItem(adapter, clickedMovieIndex, resultMovie);
                }
                // Handle other adapter types similarly
            }

            @Override
            protected void updateCurrentMovie(Movie movie) {
                Log.d(TAG, "updateCurrentMovie: SearchResultFragment");
            }


            protected <T> void updateMovieListOfMovieAdapter(ArrayList<Movie> movies, T clickedAdapter) {
//                updateMovieListOfHorizontalMovieAdapter(movies);
                if (clickedAdapter instanceof ArrayObjectAdapter) {
                    ArrayObjectAdapter adapter = (ArrayObjectAdapter) clickedAdapter;
                    extendMovieListOfHorizontalMovieAdapter(movies, adapter);
                }
            }

            protected <T> T generateCategory(String title, ArrayList<Movie> movies, boolean isDefaultHeader){
                return (T) generateCategoryView(title, movies, isDefaultHeader);
            }
        };

        searchViewControl.loadCategoriesInBackground(query);
    }

    private ArrayObjectAdapter generateCategoryView(String title, ArrayList<Movie> movies, boolean isDefaultHeader) {
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new CardPresenter());

        HeaderItem header = new HeaderItem(ROWS_COUNTER++, title);

        new Handler(Looper.getMainLooper()).post(() -> {
            if (!movies.isEmpty()){
                adapter.addAll(0, movies);
            }
            rowsAdapter.add(new ListRow(header, adapter));
            if (isDefaultHeader) {
                defaultHeadersCounter++;
            }
        });
        return adapter;
    }

    private void updateRelatedMovieItem(ArrayObjectAdapter adapter, int clickedMovieIndex, Movie resultMovie) {
        if (adapter == null || resultMovie == null){
            Log.d(TAG, "updateRelatedMovieItem: undefined adapter or movie");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                Log.d(TAG, "run: adapter> " + objectAdapter.size());
                adapter.replace(clickedMovieIndex, resultMovie);
            }
        });
    }

    private void extendMovieListOfHorizontalMovieAdapter(ArrayList<Movie> movies, ArrayObjectAdapter adapter) {
       if (adapter == null){
           Log.d(TAG, "extendMovieListOfHorizontalMovieAdapter: undefined adapter");
           return;
       }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                Log.d(TAG, "run: adapter> " + objectAdapter.size());
                adapter.addAll(adapter.size(), movies);
            }
        });
    }


//    private void loadServerRow(String rowName, AbstractServer server, ArrayObjectAdapter adapter, String query) {
//
//
//        if (server == null) {
//            Movie movie = new Movie();
//            movie.setTitle("ابحث في جوجل");
//            movie.setStudio(Movie.SERVER_GOOGLE);
//            movie.setVideoUrl(query);
//            movie.setCardImageUrl(String.valueOf(mDefaultBackground));
//            // adapter.add(0, movie);
//        } else {
////            if (server instanceof FaselHdController){
////                server.setListRowAdapter(adapter);
////            }
//            Activity activity = getActivity();
//
//
//            HeaderItem header = new HeaderItem(ROWS_COUNTER++, rowName);
//            rowsAdapter.add(new ListRow(header, adapter));
//
//            ExecutorService executor = Executors.newSingleThreadExecutor();
//            executor.submit(() -> {
//                List<Movie> finalMovieList = server.search(query, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//                    @Override
//                    public void onSuccess(ArrayList<Movie> result, String title) {
//
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
//                activity.runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        adapter.addAll(0, finalMovieList);
//                    }
//                });
//            });
//
//            executor.shutdown();
//        }
//    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
//        private ArrayObjectAdapter objectAdapter;
//
//        public ItemViewClickedListener(ArrayObjectAdapter adapter) {
//            objectAdapter = adapter;
//        }

        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

//            handleItemClicked(itemViewHolder, item, row);
            if (item instanceof String) {
                if (((String) item).contains(getString(R.string.error_fragment))) {
                    Intent intent = new Intent(getActivity(), BrowseErrorActivity.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), ((String) item), Toast.LENGTH_SHORT).show();
                }
                return;
            }
            if (!(item instanceof Movie)) {
//                Toast.makeText(getActivity(), "handleItemClicked clicked item not is instanceof Movie ", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "onItemClicked: handleItemClicked clicked item not is instanceof Movie ");
                return;
            }

            Movie movie = (Movie) item;

            clickedMovieAdapter =(ArrayObjectAdapter) ((ListRow) row).getAdapter();
//            clickedMovie = movie;
            clickedMovieIndex = clickedMovieAdapter.indexOf(movie);

            searchViewControl.handleMovieItemClick(movie, clickedMovieIndex, rowsAdapter, (ListRow) row, defaultHeadersCounter);
        }
    }


//    private void handleNextPageMovieClick(ListRow row, Movie movie) {
//        if (movie.getDescription().equals("0")) {
//            ExecutorService executor = Executors.newSingleThreadExecutor();
//            executor.submit(() -> {
////                            AbstractServer server = ServerManager.determineServer(movie, null, getActivity(), fragment);
//                AbstractServer server = ServerConfigRepository.getServer(movie.getStudio());
//                if (server == null) {
//                    Log.d(TAG, "handleItemClicked NEXT_PAGE_STATE run: unknown server:" + movie.getStudio());
//                    return;
//                }
//                //server
//                ArrayObjectAdapter adapter = (ArrayObjectAdapter) row.getAdapter();
////                            Log.d(TAG, "onItemClicked: adapter :" + adapter.toString());
//
//                List<Movie> nextList = server.search(movie.getVideoUrl(), new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//                    @Override
//                    public void onSuccess(ArrayList<Movie> result, String title) {
//                        Log.d(TAG, "handleItemClicked NEXT_PAGE_STATE onSuccess");
//                        if (result.isEmpty()) {
//                            return;
//                        }
//                        mHandler.post(() -> {
//                            adapter.addAll(adapter.size(), result);
//                            //flag that its already clicked
//                            movie.setDescription("1");
//                        });
//                    }
//
//                    @Override
//                    public void onInvalidCookie(ArrayList<Movie> result) {
//                        Log.d(TAG, "handleItemClicked NEXT_PAGE_STATE onInvalidCookie");
//                        if (result.isEmpty()) {
//                            return;
//                        }
//                        mHandler.post(() -> {
//                            adapter.addAll(adapter.size(), result);
//                            //flag that its already clicked
//                            movie.setDescription("1");
//                        });
//                    }
//
//                    @Override
//                    public void onInvalidLink(ArrayList<Movie> result) {
//
//                    }
//
//                    @Override
//                    public void onInvalidLink(String message) {
//                        Log.d(TAG, "onInvalidLink: handleNextPageMovieClick: "+ message);
//                    }
//                });
////                            Log.d(TAG, "handleItemClicked: nextPage:" + nextList.toString());
//
//            });
//
//            executor.shutdown();
//
//        }
//    }


//    private void handleItemClicked(Presenter.ViewHolder itemViewHolder, Object item, Row row) {
//        //######
//        if (item instanceof String) {
//            if (((String) item).contains(getString(R.string.error_fragment))) {
//                Intent intent = new Intent(getActivity(), BrowseErrorActivity.class);
//                startActivity(intent);
//            } else {
//                Toast.makeText(getActivity(), ((String) item), Toast.LENGTH_SHORT).show();
//            }
//            return;
//        }
//        if (!(item instanceof Movie)) {
////            Toast.makeText(getActivity(), "handleItemClicked clicked item not is instanceof Movie ", Toast.LENGTH_SHORT).show();
//            Log.d(TAG, "handleItemClicked: handleItemClicked clicked item not is instanceof Movie ");
//            return;
//        }
//
//        Movie movie = (Movie) item;
//        //  if (movie.getStudio().equals(Movie.SERVER_GOOGLE)){
//                /*if (movie.getStudio().equals(Movie.SERVER_FASELHD)){
//                    FaselHdController server = FaselHdController.getInstance(getActivity());
//
////                    Intent intent = new Intent(getActivity(), BBrowserActivity.class);
////                    intent.putExtra(DetailsActivity.MOVIE, (Serializable) movie);
////                    startActivity(intent);
//                    List<Movie> mList= server.searchWebView(query);
//                    Log.d(TAG, "onItemClicked: MovieListWeb:"+ mList);
//
//
//                }else { */
//        clickedMovie = movie;
//
//        Log.d(TAG, "onItemClicked: " + item.toString());
//        if (movie.getState() == Movie.COOKIE_STATE) {
//            Toast.makeText(getContext(), "renewing Cookie", Toast.LENGTH_SHORT).show();
//
//            renewCookie(movie);
//            return;
//        }
////            if (movie.getStudio().equals(Movie.SERVER_IPTV)) {
//        if (movie.getState() == Movie.VIDEO_STATE) {
//            Util.openExoPlayer(movie, activity, true);
//            //exist method after handling
//            return;
//        }
//
//
//        if (movie.getState() == Movie.NEXT_PAGE_STATE) {
//            //todo: add info to say if next already clicked, and handle the rest
//            handleNextPageMovieClick((ListRow) row, movie);
//            return;
//        }
//
//
//        Intent intent = Util.generateIntent(movie, new Intent(activity, DetailsActivity.class), true);
//
//        Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
//                        getActivity(),
//                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
//                        DetailsActivity.SHARED_ELEMENT_NAME)
//                .toBundle();
//        getActivity().startActivity(intent, bundle);
//
////                else if (movie.getStudio().equals(Movie.SERVER_IPTV) && movie.getState() == Movie.VIDEO_STATE) {
////                    Intent intent = new Intent(getActivity(), ExoplayerMediaPlayer.class);
////                    intent.putExtra(DetailsActivity.MOVIE, (Serializable) movie);
////                    intent.putExtra(DetailsActivity.MAIN_MOVIE, (Serializable) movie.getMainMovie());
////                    Objects.requireNonNull(getActivity()).startActivity(intent);
////
////                }
////                else {
////
//////                    if (movie.getState() == Movie.NEXT_PAGE_STATE) {
//////                        //todo: add info to say if next already clicked, and handle the rest
//////                        if (movie.getDescription().equals("0")) {
//////                            ExecutorService executor = Executors.newSingleThreadExecutor();
//////
//////                            executor.submit(() -> {
////////                                AbstractServer server = ServerManager.determineServer(movie, null, getActivity(), fragment);
//////                                AbstractServer server = ServerConfigRepository.getServer(movie.getStudio());
//////                                if (server == null) {
//////                                    Log.d(TAG, "onItemClicked: NEXT_PAGE_STATE unknown server: " + movie.getStudio());
//////                                    return;
//////                                }
//////                                //server
//////                                List<Movie> nextList = server.search(movie.getVideoUrl(), new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//////                                    @Override
//////                                    public void onSuccess(ArrayList<Movie> result, String title) {
//////
//////                                    }
//////
//////                                    @Override
//////                                    public void onInvalidCookie(ArrayList<Movie> result) {
//////
//////                                    }
//////
//////                                    @Override
//////                                    public void onInvalidLink(ArrayList<Movie> result) {
//////
//////                                    }
//////
//////                                    @Override
//////                                    public void onInvalidLink(String message) {
//////
//////                                    }
//////                                });
//////                                Log.d(TAG, "handleItemClicked: nextPage:" + nextList.toString());
//////
//////                                ArrayObjectAdapter adapter = (ArrayObjectAdapter) ((ListRow) row).getAdapter();
//////                                Log.d(TAG, "onItemClicked: adapter :" + adapter.toString());
//////                                getActivity().runOnUiThread(new Runnable() {
//////                                    @Override
//////                                    public void run() {
//////                                        adapter.addAll(adapter.size(), nextList);
//////                                    }
//////                                });
//////
//////                                //flag that its already clicked
//////                                movie.setDescription("1");
//////                            });
//////
//////                            executor.shutdown();
//////                        }
//////
//////                    }
//////
////                    else {
//////                        AbstractServer server = ServerManager.determineServer(movie, null, getActivity(), fragment);
////                        AbstractServer server = ServerConfigRepository.getServer(movie.getStudio());
////                        if (server == null) {
////                            Log.d(TAG, "onItemClicked: unknown server: " + movie.getStudio() + ", state: " + movie.getState());
////                            return;
////                        }
////                        int nextAction = server.fetchNextAction((Movie) item);
////                        if (nextAction == VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY) {
////                            Toast.makeText(getActivity(), "الرجاء الانتظار...", Toast.LENGTH_LONG).show();
////                            Thread t = new Thread(new Runnable() {
////                                @Override
////                                public void run() {
//////                                    Movie movie = server.fetch((Movie) item, ((Movie) item).getState()).movie;
////                                }
////                            });
////                            t.start();
////                        } else {
////
////
////                        }
////                    }
////                }
//    }


//    private void renewCookie(Movie movie) {
//        movie.setFetch(Movie.REQUEST_CODE_MOVIE_LIST);
//        Util.openBrowserIntent(movie, fragment, true, true);
//    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {
            if (item instanceof Movie) {
                mBackgroundUri = ((Movie) item).getBackgroundImageUrl();
                startBackgroundTimer();
            }
        }
    }

    private void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;
        Glide.with(getActivity())
                .load(uri)
                .centerCrop()
                .error(mDefaultBackground)
                .fallback(mDefaultBackground)
                .placeholder(mDefaultBackground)
                .into(new SimpleTarget<Drawable>(width, height) {
                    @Override
                    public void onResourceReady(@NonNull Drawable drawable,
                                                @Nullable Transition<? super Drawable> transition) {
                        mBackgroundManager.setDrawable(drawable);
                    }
                });
        mBackgroundTimer.cancel();
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    private class UpdateBackgroundTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateBackground(mBackgroundUri);
                }
            });
        }
    }

    private class GridItemPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT));
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setBackgroundColor(
                    ContextCompat.getColor(getActivity(), R.color.default_background));
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ((TextView) viewHolder.view).setText((String) item);
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
        }
    }

    private void prepareBackgroundManager() {

        Activity currentActivity = getActivity();
        Context context = getContext();
        if (currentActivity == null || context == null) {
            return;
        }
        mBackgroundManager = BackgroundManager.getInstance(currentActivity);
        mBackgroundManager.attach(currentActivity.getWindow());

        mDefaultBackground = ContextCompat.getDrawable(context, R.drawable.default_background);
        mMetrics = new DisplayMetrics();
        currentActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
        // setBadgeDrawable(getActivity().getResources().getDrawable(
        // R.drawable.videos_by_google_banner));
        //setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent
        // over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getActivity(), R.color.fastlane_background));
        // set search icon color
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.search_opaque));
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), GetSearchQueryActivity.class);
                startActivityForResult(intent, 1);
            }
        });

        setOnItemViewSelectedListener(new ItemViewSelectedListener());
        setOnItemViewClickedListener(new ItemViewClickedListener());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode + ", " + resultCode + ", " + data);

        searchViewControl.onActivityResult(requestCode, resultCode, data, clickedMovieAdapter, clickedMovieIndex);
        super.onActivityResult(requestCode, resultCode, data);
//
//        if (resultCode != Activity.RESULT_OK || data == null) {
//            Log.d(TAG, "onActivityResult:RESULT_NOT_OK ");
//            return;
//        }
//        if (clickedMovie == null) {
//            Log.d(TAG, "onActivityResult: clickedMovie is not identified");
//            return;
//        }
//        ArrayObjectAdapter objectAdapter = getServerAdapter(clickedMovie.getStudio());
////        AbstractServer server = ServerManager.determineServer(clickedMovie, objectAdapter, getActivity(), fragment);
//        AbstractServer server = ServerConfigRepository.getServer(clickedMovie.getStudio());
//        if (server == null) {
//            Log.d(TAG, "onActivityResult: unknown server: " + clickedMovie.getStudio() + ", state: " + clickedMovie.getState());
//            return;
//        }
//        String result = data.getStringExtra("result");
//        ArrayList<Movie> resultMovieSublist = data.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);
//
//        if (requestCode == 0 || requestCode == Movie.REQUEST_CODE_MOVIE_LIST) {
//                    Thread t = new Thread(new Runnable() {
//                        @Override
//                        public void run() {
////                            Movie movie = new Movie();
////                            movie.setTitle(query);
////                            movie.setStudio(result);
//
////                            Log.d(TAG, "run: after renewing the cookie:" + server.getHeaders());
//                            //List<Movie> movies = server.search(query);
////                            Gson gson = new Gson();
////                            Type movieListType = new TypeToken<List<Movie>>() {
////                            }.getType();
////                            List<Movie> movies = gson.fromJson(result, movieListType);
////                            if (movies == null){
////                                Log.d(TAG, "onActivityResult run: fail converting result to movies object ");
////                                return;
////                            }
//
//                            if (clickedMovie.getState() == Movie.COOKIE_STATE) {
//                                for (Movie mov : resultMovieSublist) {
//                                    //todo: check if still needed to redetect the movie state
//                                    mov.setState(server.detectMovieState(mov));
//                                    //sets main movie to it self same as search method as renewing cookie only search for movies
//                                    mov.setMainMovie(mov);
//                                    mov.setMainMovieTitle(mov.getTitle());
////                                    if (server.isSeries(mov)){
////                                        movies.get(movies.indexOf(mov)).setState(Movie.GROUP_OF_GROUP_STATE);
////                                    }else {
////                                        movies.get(movies.indexOf(mov)).setState(Movie.ITEM_STATE);
////                                    }
//                                }
//                            }
//
//                            if (!resultMovieSublist.isEmpty()) {
//                                getActivity().runOnUiThread(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        Log.d(TAG, "run: adapter> " + objectAdapter.size());
//                                        objectAdapter.addAll(1, resultMovieSublist);
//                                    }
//                                });
//                            } else {
//                                getActivity().runOnUiThread(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        Toast.makeText(getContext(), "لايوجد نتائج", Toast.LENGTH_SHORT).show();
//                                    }
//                                });
//                            }
//
//                        }
//                    });
//                    t.start();
//                    Log.d(TAG, "onActivityResult: cookies:");
////
////                    Gson gson = new Gson();
////                    Type movieListType = new TypeToken<List<Movie>>(){}.getType();
////                    List<Movie> movies = gson.fromJson(result, movieListType);
////                    Log.d(TAG, "onActivityResult: movie:"+movies.toString());
//
//            return;
//
//        }
//            Thread t = new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    List<Movie> movies = server.search(clickedMovie.getTitle(), new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
//                        @Override
//                        public void onSuccess(ArrayList<Movie> result, String title) {
//                            Log.d(TAG, "onSuccess: ");
//                            if (result == null || result.isEmpty()){
//                                Log.d(TAG, "onSuccess: onActivityResult empty result");
//                            return;
//                            }
//                            getActivity().runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    Log.d(TAG, "run: adapter> " + objectAdapter.size());
//                                    objectAdapter.addAll(1, result);
//                                }
//                            });
//                        }
//
//                        @Override
//                        public void onInvalidCookie(ArrayList<Movie> result) {
//                            Log.d(TAG, "onInvalidCookie: ");
//                            if (result == null || result.isEmpty()){
//                                Log.d(TAG, "onInvalidCookie: onActivityResult empty result");
//                                return;
//                            }
//                            getActivity().runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    Log.d(TAG, "run: adapter> " + objectAdapter.size());
//                                    objectAdapter.addAll(1, result);
//                                }
//                            });
//                        }
//
//                        @Override
//                        public void onInvalidLink(ArrayList<Movie> result) {
//
//                        }
//
//                        @Override
//                        public void onInvalidLink(String message) {
//                            Log.d(TAG, "onInvalidLink: "+message);
//                        }
//                    });
//                }
//            });
//            t.start();
//
            //hier



//        getActivity().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                Toast.makeText(getContext(), "لايوجد نتائج", Toast.LENGTH_SHORT).show();
//            }
//        });

    }

//    private ArrayObjectAdapter getServerAdapter(String serverId) {
//        switch (serverId) {
//            case Movie.SERVER_CIMA_CLUB:
//                return cimaClubAdapter;
//            case Movie.SERVER_CIMA4U:
//                return cima4uAdapter;
//            case Movie.SERVER_SHAHID4U:
//                return shahidAdapter;
//            case Movie.SERVER_ARAB_SEED:
//                return arabSeedAdapter;
//            case Movie.SERVER_FASELHD:
//                return faselAdapter;
//            case Movie.SERVER_IPTV:
//                return tvAdapter;
//            default:
//                return new ArrayObjectAdapter(new CardPresenter());
//        }
//    }
}