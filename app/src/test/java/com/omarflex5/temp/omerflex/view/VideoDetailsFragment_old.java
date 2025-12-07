package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.app.DetailsSupportFragmentBackgroundController;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieHistory;
import com.omerflex.entity.ServerConfig;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.database.MovieDbHelper;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/*
 * LeanbackDetailsFragment extends DetailsFragment, a Wrapper fragment for leanback details screens.
 * It shows a detailed view of video and its meta plus related videos.
 */
public class VideoDetailsFragment_old extends DetailsSupportFragment {
    private static final String TAG = "VideoDetailsFragment";
    public static String TRANSITION_NAME = "poster_transition";

    public static final int ACTION_OPEN_DETAILS_ACTIVITY = 1;
    public static final int ACTION_OPEN_EXTERNAL_ACTIVITY = 2;
    public static final int ACTION_OPEN_NO_ACTIVITY = 3;
    private static final int ACTION_OPEN_BROWSER_FOR_RESULT = 4;

    private static final int ACTION_WATCH = 1;
    private static final int ACTION_WATCH_TRAILER = 2;
    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private static final int NUM_COLS = 10;
    int defaultHeadersCounter = 0;
    int clickedMovieIndex = 0;
    private Movie mSelectedMovie;

    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter clickedMovieAdapter;
    private ClassPresenterSelector mPresenterSelector;

    private DetailsSupportFragmentBackgroundController mDetailsBackground;

    // ********
    private ProgressDialog mProgressDialog;

    public MovieDbHelper dbHelper;
    Handler movieHandler;


    AbstractServer server;
    DetailsDescriptionPresenter detailsDescriptionPresenter;
    ArrayObjectAdapter listRowAdapter;
    FullWidthDetailsOverviewRowPresenter detailsPresenter;


    DetailsOverviewRow row;
    Fragment fragment = this;
    Activity activity;
    private boolean isInitialized = false;

    ServerConfig config;
    DetailsViewControl detailsViewControl;

    // ********

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate DetailsFragment");
        start();
        super.onCreate(savedInstanceState);
    }

    public void start() {
        if (isInitialized) {
            return;
        }
        setRetainInstance(true);
        activity = getActivity();
        if (activity == null) {
            return;
        }
        Intent currentIntent = activity.getIntent();

        if (currentIntent == null) {
            return;
        }

        mSelectedMovie = Util.recieveSelectedMovie(getActivity().getIntent());
//        Log.d(TAG, "start: mainMovie:"+ mSelectedMovie.getMainMovie());
        listRowAdapter = new ArrayObjectAdapter(new CardPresenter());

        // default value in case of activity result
        clickedMovieAdapter = listRowAdapter;
//            mSelectedMovie.setMainMovie(mSelectedMovieMainMovie);

        //very important to initialize all required
        initializeThings();

        // todo clarify
//        if (mSelectedMovie.getMovieHistory() == null && mSelectedMovie.getMainMovie() != null) {
//            mSelectedMovie.setMovieHistory(dbHelper.getMovieHistoryByMainMovie(Util.getUrlPathOnly(mSelectedMovie.getMainMovie().getVideoUrl())));
//        }
        setupRowsAndServer();

        isInitialized = true;
    }


    @Override
    public void onStart() {
        //   Log.d(TAG, "onStart::: " + mSelectedMovie.getStudio());
        //fetch server and load description and bg image
//        server = ServerManager.determineServer(mSelectedMovie, listRowAdapter, getActivity(), this);
        start();
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        //Objects.requireNonNull(getView()).requestFocus();
    }

    private void initializeThings() {
        dbHelper = MovieDbHelper.getInstance(getActivity());
        movieHandler = new Handler();

        mDetailsBackground = new DetailsSupportFragmentBackgroundController(this);
        detailsDescriptionPresenter = new DetailsDescriptionPresenter();

        Log.d(TAG, "initializeThings: Movie :");

//        String movieJson = Objects.requireNonNull(getActivity()).getIntent().getStringExtra(DetailsActivity.MOVIE_SUBLIST);
//        Gson gson = new Gson();
//        Type type = new TypeToken<List<Movie>>() {
//        }.getType();
//        List<Movie> movieSublist = gson.fromJson(movieJson, type);
//        Log.d(TAG, "onCreate: subList:" + movieSublist);
//        if (movieSublist != null) {
//            mSelectedMovie.setSubList(movieSublist);
//        }

//        https://www.laroza.now/play.php?vid=4109d062a
//        https://www.laroza.now/video.php?vid=4109d062a

        mProgressDialog = new ProgressDialog(getContext());
        mProgressDialog.setTitle("جاري التحميل...");
        mProgressDialog.setMessage("الرجاء الانتظار...");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(true);

//        server = ServerManager.determineServer(mSelectedMovie, listRowAdapter, getActivity(), fragment);
        server = ServerConfigRepository.getInstance().getServer(mSelectedMovie.getStudio());
        if (server == null) {
            Log.d(TAG, "initializeThings: unknown server: " + mSelectedMovie.getStudio() + ", state: " + mSelectedMovie.getState());
            return;
        }

        mPresenterSelector = new ClassPresenterSelector();
        mAdapter = new ArrayObjectAdapter(mPresenterSelector);

        setupDetailsOverviewRow();
        setupDetailsOverviewRowPresenter();
        setupRelatedMovieListRow();
        setAdapter(mAdapter);
        initializeBackground(mSelectedMovie);

        detailsViewControl = new DetailsViewControl(getActivity(), fragment, dbHelper) {
            @Override
            protected void updateCurrentMovie(Movie movie) {
                updateCurrentMovieView(movie);
            }

            @Override
            protected <T> void updateMovieListOfMovieAdapter(ArrayList<Movie> movies, T clickedAdapter) {
                if (clickedAdapter instanceof ArrayObjectAdapter) {
                    ArrayObjectAdapter adapter = (ArrayObjectAdapter) clickedAdapter;
                    extendMovieListOfHorizontalMovieAdapter(movies, adapter);
                }
            }

            @Override
            protected <T> T generateCategory(String title, ArrayList<Movie> movies, boolean isDefaultHeader) {
                return null;
            }

            @Override
            protected <T> void updateClickedMovieItem(T clickedAdapter, int clickedMovieIndex, Movie resultMovie) {
                Log.d(TAG, "updateClickedMovieItem: "+ clickedAdapter);
                if (clickedAdapter instanceof ArrayObjectAdapter) {
                    ArrayObjectAdapter adapter = (ArrayObjectAdapter) clickedAdapter;
                    Log.d(TAG, "updateClickedMovieItem: "+ adapter.size()+ ", index: "+clickedMovieIndex);
                    updateRelatedMovieItem(adapter, clickedMovieIndex, resultMovie);
                }
            }

            @Override
            protected void openDetailsActivity(Movie movie, Activity activity) {
                Util.openVideoDetailsIntent(movie, activity);
            }

            @Override
            protected <T> void removeRow(T rowsAdapter, int i) {
                if (rowsAdapter instanceof ArrayObjectAdapter){
                    try {
                        ((ArrayObjectAdapter) rowsAdapter).remove(i);
                    } catch (Exception exception) {
                        Log.d(TAG, "handleItemClicked: error deleting iptv header on main fragment: " + exception.getMessage());
                    }
                }
            }
        };
        setOnItemViewClickedListener(new ItemViewClickedListener());
    }
    private void updateRelatedMovieItem(ArrayObjectAdapter adapter, int clickedMovieIndex, Movie resultMovie) {
        if (adapter == null || resultMovie == null) {
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
        if (adapter == null) {
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

    //fetch and update current movie
    private void setupRowsAndServer() {
        if (mSelectedMovie.getFetch() == Movie.NO_FETCH_MOVIE_AT_START) {
            Log.d(TAG, "setupRowsAndServer:NO_FETCH_MOVIE_AT_START ");
            return;
        }

        //todo: 1. at start init and fetch current movie and update the current movie accordingly
        ExecutorService executor = Executors.newSingleThreadExecutor();
         showProgressDialog(false);
        executor.submit(() -> {
            mSelectedMovie = (Movie) server.fetch(
                    mSelectedMovie,
                    mSelectedMovie.getState(),
                    new ServerInterface.ActivityCallback<Movie>() {
                        @Override
                        public void onSuccess(Movie result, String title) {
                            Log.d(TAG, "setupRowsAndServer: mSelectedMovie after first fetch:" + mSelectedMovie);
                            if (result == null || result.getSubList() == null || result.getSubList().isEmpty()) {
                                Log.d(TAG, "setupRowsAndServer: onSuccess after first fetch: empty movie");
                                hideProgressDialog(true, "حدث خطأ...");
                                return;
                            }
//                            if (mSelectedMovie.getSubList() != null) {
                            listRowAdapter.addAll(0, result.getSubList());
                            evaluateWatchAction(result);

                            //todo test
//                            removeInvalidLinks(mSelectedMovie.getSubList());
                                hideProgressDialog(true, null);

                            mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
                            mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(listRowAdapter), mAdapter.size());
                            setSelectedPosition(getAdapter().size() - 1, true);
//                            Log.d(TAG, "setupRowsAndServer: mSelectedMovie sublist after first fetch:" + mSelectedMovie.getSubList().toString());
                        }

                        @Override
                        public void onInvalidCookie(Movie result, String title) {
                            Log.d(TAG, "onInvalidCookie: 338: "+ result);
                            result.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
                            hideProgressDialog(true, null);
                            Util.openBrowserIntent(result, fragment, false, true, true);
                        }

                        @Override
                        public void onInvalidLink(Movie result) {
                            hideProgressDialog(true, "حدث خطأ...");
                        }

                        @Override
                        public void onInvalidLink(String message) {
                            Log.d(TAG, "onInvalidLink: " + message);
                            hideProgressDialog(true, "حدث خطأ...");
                        }
                    }
            ).movie;

        });

        executor.shutdown();

//        movieHandler = new Handler() {
//            @Override
//            public void handleMessage(@NonNull Message msg) {
//                loadActionRow();
//                Log.d(TAG, "handle what: " + msg.what);
//                if (mSelectedMovie.getSubList() != null) {
//                    listRowAdapter.addAll(0, mSelectedMovie.getSubList());
//                    Log.d(TAG, "handleMessage: sublist Added: " + mSelectedMovie.getSubList());
//                }
//                mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
//                mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(listRowAdapter), mAdapter.size());
//                super.handleMessage(msg);
//            }
//        };


//        Log.d(TAG, "setupRowsAndServer: " + mSelectedMovie.toString());
//
//
//        if (mSelectedMovie.getFetch() != 0) {
//            Log.d(TAG, "setupRowsAndServer: getSubList:" + mSelectedMovie.getSubList());
//            ExecutorService executor = Executors.newSingleThreadExecutor();
//
//            executor.submit(() -> {
//                Log.d(TAG, "run: save movie 1");
//                if (mSelectedMovie.getStudio().equals(Movie.SERVER_SHAHID4U)
//                        || mSelectedMovie.getStudio().equals(Movie.SERVER_CIMA4U)
//                        || mSelectedMovie.getStudio().equals(Movie.SERVER_CIMA_CLUB)
//                        || (mSelectedMovie.getStudio().equals(Movie.SERVER_FASELHD)
//                        && mSelectedMovie.getState() == Movie.RESULT_STATE)
//                ) {
//                    mSelectedMovie.setIsHistory(1);
//                }
//                mSelectedMovie.save(dbHelper);
//                Movie fetchedMovie = (Movie) Objects.requireNonNull(server).fetch(mSelectedMovie);
//                if (fetchedMovie != null) {
//                    mSelectedMovie = fetchedMovie;
//                }
//
//                hideProgressDialog();
//                movieHandler.sendEmptyMessage(0);
//            });
//
//            executor.shutdown();
//        } else {
//            if (mSelectedMovie.getSubList() != null) {
//                listRowAdapter.addAll(0, mSelectedMovie.getSubList());
//            }
//            loadActionRow();
//            hideProgressDialog();
//        }

    }

    private void evaluateWatchAction(Movie movie) {
        Movie firstSubMovie = movie;
        if (!movie.getSubList().isEmpty()){
            firstSubMovie = movie.getSubList().get(0);
        }

        boolean watchCond = firstSubMovie.getState() == Movie.RESOLUTION_STATE || firstSubMovie.getState() == Movie.VIDEO_STATE;
        boolean watchOmarCond = firstSubMovie.getStudio().equals(Movie.SERVER_OMAR) && firstSubMovie.getState() > Movie.ITEM_STATE;
//        boolean watchOmarCond = result.getStudio().equals(Movie.SERVER_OMAR) && firstSubMovie.getState() > Movie.ITEM_STATE;
        Log.d(TAG, "evaluateWatchAction: " + watchCond + ", "+ firstSubMovie);
        if (watchCond || watchOmarCond) {
            Log.d(TAG, "onSuccess: watchCond");
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadActionRow();
                }
            });
        }
    }

    private void removeInvalidLinks(List<Movie> subList) {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        // showProgressDialog(false);
        executor.submit(() -> {
            //            mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
            //            mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(listRowAdapter), mAdapter.size());
            for (Movie movie : subList) {
                if (movie.getState() == Movie.BROWSER_STATE || movie.getState() == Movie.RESOLUTION_STATE || movie.getState() == Movie.VIDEO_STATE) {
                    boolean invalidLink = !isValidLink(movie);
                    Log.d(TAG, "removeInvalidLinks: " + !invalidLink + ", " + movie.getTitle() + ", " + movie.getVideoUrl());
                    if (invalidLink) {
                        int movieIndexInRow = listRowAdapter.indexOf(movie);
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                listRowAdapter.remove(listRowAdapter.get(movieIndexInRow));
                                listRowAdapter.notifyArrayItemRangeChanged(0, listRowAdapter.size());

                                mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
                                mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(listRowAdapter), mAdapter.size());
                                mSelectedMovie.getSubList().remove(movie);
                                Log.d(TAG, "removeInvalidLinks: removed index " + movieIndexInRow + "title: " + movie.getTitle());
                            }
                        });

                    }
                }
            }
        });
        executor.shutdown();
    }

    public Map<String, String> parseParamsToMap(String params) {
        params = params.substring(params.indexOf("||") + 2);
        Map<String, String> map = new HashMap<>();
        String[] pairs;
        if (params.contains("&")) {
            pairs = params.split("&");
        } else {
            pairs = new String[]{params};
        }
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            map.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return map;
    }


    private boolean isValidLink(Movie movie) {
        String url = movie.getVideoUrl();
        Map<String, String> headers = new HashMap<>();
        if (url.contains("||")) {
            headers = parseParamsToMap(movie.getVideoUrl());
            url = url.substring(0, url.indexOf("||"));
        } else if (url.contains("|")) {
            headers = parseParamsToMap(movie.getVideoUrl());
            url = url.substring(0, url.indexOf("|"));
        }

        Log.d(TAG, "isValidLink: url: " + url + "headers: " + headers.toString());
        OkHttpClient client = new OkHttpClient();

        // Convert HashMap to Headers
        Headers.Builder headersBuilder = new Headers.Builder();


        for (Map.Entry<String, String> entry : headers.entrySet()) {
            try {
                headersBuilder.add(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                Log.d(TAG, "isValidLink: error headers: " + e.getMessage());
            }
        }


        Headers headersMap = headersBuilder.build();


        Request request = new Request.Builder()
                .url(url)
                .headers(headersMap)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                // Read response.body().string() to get the response content
                ResponseBody requestBody = response.body();
                Log.d(TAG, "isValidLink: " + response.toString());
                if (requestBody == null) {
                    return false;
                }
                String body = requestBody.string();
//                Log.d(TAG, "isValidLink: body: " + body);
                if (body.contains("deleted") ||
                        body.contains("has been blocked") ||
                        body.contains("was deleted") ||
                        body.contains("not found") ||
                        body.contains("has been deleted")
                ) {
                    return false;
                }
                return true;
                // System.out.print(body);
                // Log.d("TAG", "xxxX: getMovieUrl: "+ body);

            }
        } catch (IOException e) {
            Log.d(TAG, "isValidLink: error: " + e.getMessage());
            if (e.getMessage().contains("PROTOCOL_ERROR")) {
                return true;
            }
        }
        return false;
    }


    private void loadActionRow() {
        ArrayObjectAdapter actionAdapter = new ArrayObjectAdapter();
        //boolean watchActionCond = mSelectedMovie.getVideoUrl() != null && ( !(mSelectedMovie.getStudio().equals(Movie.SERVER_CIMA_CLUB) || mSelectedMovie.getStudio().equals(Movie.SERVER_SHAHID4U) || mSelectedMovie.getStudio().equals(Movie.SERVER_CIMA4U)) && (mSelectedMovie.getState() == Movie.ITEM_STATE || mSelectedMovie.getState() == Movie.RESOLUTION_STATE || mSelectedMovie.getState() == Movie.VIDEO_STATE));
        int state = mSelectedMovie.getState();
        String studio = mSelectedMovie.getStudio();
        boolean watchFaselCond = state == Movie.VIDEO_STATE;
        boolean watchCond = state == Movie.ITEM_STATE && (studio.equals(Movie.SERVER_AKWAM) || studio.equals(Movie.SERVER_MyCima));
        //   Log.d(TAG, "loadActionRow: watchCond:"+watchActionCond);
//        if (watchFaselCond || watchCond) {
//            // if (watchActionCond) {
//            actionAdapter.add(
//                    new Action(
//                            ACTION_WATCH,
//                            getResources().getString(R.string.watch)));
//            setSelectedPosition(0);
//        } else {
//            setSelectedPosition(1);
//        }
        actionAdapter.add(
                new Action(
                        ACTION_WATCH,
                        getResources().getString(R.string.watch)));
        actionAdapter.notifyArrayItemRangeChanged(0, actionAdapter.size() - 1);
        setSelectedPosition(0, true);
        if (mSelectedMovie.getTrailerUrl() != null) {
            if (mSelectedMovie.getTrailerUrl().length() > 2) {
                actionAdapter.add(
                        new Action(
                                ACTION_WATCH_TRAILER,
                                getResources().getString(R.string.watch_trailer_1)));
            }
        }
        row.setActionsAdapter(actionAdapter);
    }


    private void initializeBackground(Movie data) {
        mDetailsBackground.enableParallax();

        config = ServerConfigRepository.getInstance().getConfig(data.getStudio());
        boolean noHeaderCond = config == null ||
                config.getHeaders() == null ||
                config.getHeaders().isEmpty();
        if (noHeaderCond) {
            Glide.with(getActivity())
                    .asBitmap()
                    .centerCrop()
                    .error(R.drawable.default_background_2)
                    .fallback(R.drawable.default_background_2)
                    .placeholder(R.drawable.default_background_2)
                    .load(data.getBackgroundImageUrl())
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap,
                                                    @Nullable Transition<? super Bitmap> transition) {
                            mDetailsBackground.setCoverBitmap(bitmap);
                            mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                        }
                    });
            return;
        }


        String cookies = config.getStringCookies();
        if (cookies == null) {
            cookies = "";
        }

        LazyHeaders.Builder builder = new LazyHeaders.Builder()
                .addHeader("Cookie", cookies);

        for (Map.Entry<String, String> entry : config.getHeaders().entrySet()) {
            builder.addHeader(entry.getKey(), entry.getValue());
        }
//            GlideUrl glideUrl = new GlideUrl(data.getBackgroundImageUrl(), builder.build());
//            if (mSelectedMovie.getStudio().equals(Movie.SERVER_FASELHD)){
//                glideUrl = new GlideUrl("https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png", builder.build());
//            }

        if (data.getBackgroundImageUrl() == null || data.getBackgroundImageUrl().equals("")) {
            data.setBackgroundImageUrl(data.getCardImageUrl());
        }

        Glide.with(getActivity())
                .asBitmap()
                //.fitCenter()
                .centerCrop()
                //  .load(glideUrl)
                .load(data.getBackgroundImageUrl())
                .error(R.drawable.default_background_2)
                .fallback(R.drawable.default_background_2)
                .placeholder(R.drawable.default_background_2)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap,
                                                @Nullable Transition<? super Bitmap> transition) {
                        mDetailsBackground.setCoverBitmap(bitmap);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }
                });
    }

    private void setupDetailsOverviewRow() {
        Log.d(TAG, "doInBackground: " + mSelectedMovie.toString());
        row = new DetailsOverviewRow(mSelectedMovie);
        row.setImageDrawable(
                ContextCompat.getDrawable(getContext(), R.drawable.default_background_2));
        int width = convertDpToPixel(getActivity().getApplicationContext(), DETAIL_THUMB_WIDTH);
        int height = convertDpToPixel(getActivity().getApplicationContext(), DETAIL_THUMB_HEIGHT);
        Glide.with(getActivity())
                .load(mSelectedMovie.getCardImageUrl())
//                .centerCrop()
                .fitCenter()
                .error(R.drawable.default_background_2)
                .fallback(R.drawable.default_background_2)
                .placeholder(R.drawable.default_background_2)
                .into(new SimpleTarget<Drawable>(width, height) {
                    @Override
                    public void onResourceReady(@NonNull Drawable drawable,
                                                @Nullable Transition<? super Drawable> transition) {
                        Log.d(TAG, "details overview card image url ready: " + drawable);
                        row.setImageDrawable(drawable);
                        mAdapter.notifyArrayItemRangeChanged(0, mAdapter.size());
                    }
                });

        mAdapter.add(row);
    }

    private void setupDetailsOverviewRowPresenter() {
        Handler movieResHandler = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                Log.d(TAG, "handle what: " + msg.what);
                mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
                super.handleMessage(msg);
            }
        };

        // Set detail background.
        detailsPresenter =
                new FullWidthDetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
        detailsPresenter.setBackgroundColor(
                ContextCompat.getColor(getContext(), R.color.selected_background));

        // Hook up transition element.
        FullWidthDetailsOverviewSharedElementHelper sharedElementHelper =
                new FullWidthDetailsOverviewSharedElementHelper();
        sharedElementHelper.setSharedElementEnterTransition(
                getActivity(), DetailsActivity.SHARED_ELEMENT_NAME);
        detailsPresenter.setListener(sharedElementHelper);
        detailsPresenter.setParticipatingEntranceTransition(true);

        detailsPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                Log.d(TAG, "onActionClicked: "+listRowAdapter.size());
                clickedMovieAdapter = listRowAdapter;
                detailsViewControl.handleActionClick(mSelectedMovie, action.getId(), clickedMovieAdapter);
            }
        });
        mPresenterSelector.addClassPresenter(DetailsOverviewRow.class, detailsPresenter);
    }

    private void handleActionClick(Action action) {
        Intent intent;
        boolean condTrailer = action.getId() == ACTION_WATCH_TRAILER;
        boolean condWatch1 = action.getId() == ACTION_WATCH;

        if (condTrailer) {
            intent = new Intent(getActivity(), BrowserActivity.class);
            Movie trailer = new Movie();
            trailer.setVideoUrl(mSelectedMovie.getTrailerUrl());
            trailer.setState(Movie.BROWSER_STATE);
            trailer.setTitle(mSelectedMovie.getTitle());
            trailer.setStudio(mSelectedMovie.getStudio());
            intent.putExtra(DetailsActivity.MOVIE, (Serializable) trailer);
            Log.d(TAG, "onActionClicked: Trailer: " + trailer.getTitle());
            startActivity(intent);
        } else if (condWatch1) {
            boolean condWatch2 = listRowAdapter.size() > 0;
            if (condWatch2) {

                showProgressDialog(false);
                ExecutorService executor = Executors.newSingleThreadExecutor();

                Toast.makeText(getActivity(), "الرجاء الانتظار...", Toast.LENGTH_SHORT).show();
                executor.submit(() -> {
                    // mSelectedMovie = Objects.requireNonNull(server).fetchItem(mSelectedMovie);
                    Movie movie = (Movie) listRowAdapter.get(0);
                    movie.setRowIndex(0); // very important to update the correct item of the row
                    Log.d(TAG, "run: condWatch2 " + movie);
                    //todo : handle local watch
//                    dbHelper.addMainMovieToHistory(mSelectedMovie);
//                    Movie res = server.fetchToWatchLocally(movie);
                    Movie res = server.fetch(
                            movie,
                            Movie.ACTION_WATCH_LOCALLY,
                            new ServerInterface.ActivityCallback<Movie>() {
                                @Override
                                public void onSuccess(Movie result, String title) {
                                    if (result != null && result.getVideoUrl() != null) {
//                                        String type = "video/*";
                                        // Uri uri = Uri.parse(res.getSubList().get(0).getVideoUrl());
                                        Log.d(TAG, "onActionClicked: Resolutions " + result);
                                        getActivity().runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Util.openExoPlayer(result, getActivity(), true);
                                                //dbHelper.addMainMovieToHistory(res.getMainMovieTitle(), null);
                                                updateItemFromActivityResult(result);

                                                hideProgressDialog(false, null);
                                            }
                                        });

                                    }
                                }

                                @Override
                                public void onInvalidCookie(Movie result, String title) {
                                    Log.d(TAG, "onInvalidCookie: 752"+ result);
                                    hideProgressDialog(false, null);
                                    result.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
                                    Util.openBrowserIntent(result, fragment, false, true, true);
                                }

                                @Override
                                public void onInvalidLink(Movie result) {
                                    hideProgressDialog(true, "حدث خطأ...");
                                }

                                @Override
                                public void onInvalidLink(String message) {
                                    hideProgressDialog(true, "حدث خطأ...");
                                }
                            }
                    ).movie;
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
            } else {
                Toast.makeText(getActivity(), getResources().getString(R.string.watch_trailer_2), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getActivity(), getResources().getString(R.string.watch_trailer_2), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRelatedMovieListRow() {
        String headerTitle = getResources().getString(R.string.related_movie_row_header_resolutions);
        if (mSelectedMovie.getState() == Movie.GROUP_OF_GROUP_STATE) {
            headerTitle = getResources().getString(R.string.related_movie_row_header_session);
        } else if (mSelectedMovie.getState() == Movie.GROUP_STATE) {
            headerTitle = getResources().getString(R.string.related_movie_row_header_series);
        }

        HeaderItem header = new HeaderItem(0, headerTitle);
        mAdapter.add(new ListRow(header, listRowAdapter));
        mPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
    }

    private int convertDpToPixel(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {
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

            clickedMovieAdapter = (ArrayObjectAdapter) ((ListRow) row).getAdapter();
//            clickedMovie = movie;
            clickedMovieIndex = clickedMovieAdapter.indexOf(movie);
//            generateMovieHistory(mSelectedMovie, movie);
//            handleItemClick(itemViewHolder, item, rowViewHolder, row);
            detailsViewControl.handleMovieItemClick(movie, clickedMovieIndex, mAdapter,clickedMovieAdapter, defaultHeadersCounter);
//            detailsViewControl.handleMovieItemClick(movie, clickedMovieIndex, mAdapter,(ListRow) row, defaultHeadersCounter);
        }

    }

//    private void handleItemClick(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder,
//                                 Row row) {
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
//            Toast.makeText(getActivity(), "handleItemClicked clicked item not is instanceof Movie ", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        Movie movie = (Movie) item;
//
//        Log.d(TAG, "Item: " + item.toString());
//        int nextAction = server.fetchNextAction(movie);
//
//        generateMovieHistory(mSelectedMovie, movie);
//
//        //find the adapter to get the item index in order to identify and update the correct clicked item on returned result
//        ArrayObjectAdapter adapter = (ArrayObjectAdapter) ((ListRow) row).getAdapter();
//        int itemIndexInRow = adapter.indexOf(movie);
//        Log.d(TAG, "handleItemClick: itemIndex:" + itemIndexInRow);
//        movie.setRowIndex(itemIndexInRow);
//        Log.d(TAG, "handleItemClick: id: " + movie.getRowIndex());
//
//        if (nextAction == ACTION_OPEN_DETAILS_ACTIVITY) {
//            Log.d(TAG, "onItemClicked: new Activity");
//            Intent intent = Util.generateIntent(movie, new Intent(getActivity(), DetailsActivity.class), true);
//
//            Bundle bundle =
//                    ActivityOptionsCompat.makeSceneTransitionAnimation(
//                                    getActivity(),
//                                    ((ImageCardView) itemViewHolder.view).getMainImageView(),
//                                    DetailsActivity.SHARED_ELEMENT_NAME)
//                            .toBundle();
//            getActivity().startActivity(intent, bundle);
//            return;
//        }
//        dbHelper.addMainMovieToHistory(mSelectedMovie);
//
//        if (nextAction == ACTION_OPEN_EXTERNAL_ACTIVITY) { // means to run movie with external video player usually when movie in Video state is
//            Log.d(TAG, "onItemClicked: fetch only");
//            openExternalActivityOnItemClicked(movie);
//            return;
//        }
//
//        if (nextAction == ACTION_OPEN_NO_ACTIVITY) {
//            MovieFetchProcess process = server.fetch(movie, movie.getState(), new ServerInterface.ActivityCallback<Movie>() {
//                @Override
//                public void onSuccess(Movie result, String title) {
//                    Log.d(TAG, "onSuccess: ");
//                    Util.openExternalVideoPlayer(movie, getActivity());
//                    updateItemFromActivityResult(result);
//                }
//
//                @Override
//                public void onInvalidCookie(Movie result, String title) {
//                    Log.d(TAG, "onInvalidCookie: ");
//                    movie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
//                    Util.openBrowserIntent(movie, fragment, false, true);
//                }
//
//                @Override
//                public void onInvalidLink(Movie result) {
//
//                }
//
//                @Override
//                public void onInvalidLink(String message) {
//                    Log.d(TAG, "onInvalidLink: "+message);
//                }
//            });
//        }
//
//
////        if (!((Movie) item).getVideoUrl().equals("")) {
////            Toast.makeText(getActivity(), "الرجاء الانتظار...", Toast.LENGTH_LONG).show();
////        }
//
//        // case ACTION_OPEN_NO_ACTIVITY
////        ExecutorService executor = Executors.newSingleThreadExecutor();
////
////        executor.submit(() -> {
////            //dbHelper.addMainMovieToHistory(((Movie) item).getMainMovieTitle(), null);
////            //fetch movie and the server is responsible for playing the video internal or external
////            server.fetch(
////                    movie,
////                    movie.getState(),
////                    new ServerInterface.ActivityCallback<Movie>() {
////                        @Override
////                        public void onSuccess(Movie result, String title) {
////                            Log.d(TAG, "handleItemClick: open No Activity: " + result);
////                            if (result == null) {
////                                Log.d(TAG, "handleItemClick: open No Activity onSuccess: empty result");
////                            }
////                                movieHandler.post(() -> {
//////                                    String type = "video/*"; // It works for all video application
//////                                    Uri uri = Uri.parse(result.getVideoUrl());
//////                                    Intent in1 = new Intent(Intent.ACTION_VIEW, uri);
//////                                    in1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//////                                    //  in1.setPackage("org.videolan.vlc");
//////                                    in1.setDataAndType(uri, type);
////                                    hideProgressDialog(true);
////                                    Util.openExternalVideoPlayer(result, getActivity());
////
//////                                    getActivity().startActivity(in1);
////                                    updateItemFromActivityResult(result);
////                                    dbHelper.addMainMovieToHistory(mSelectedMovie);
////
////                                });
////                        }
////
////                        @Override
////                        public void onInvalidCookie(Movie result) {
////                            result.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
////                            Util.openBrowserIntent(result, getActivity(), false, true);
////                        }
////
////                        @Override
////                        public void onInvalidLink(Movie result) {
////
////                        }
////
////
////                        @Override
////                        public void onInvalidLink(String message) {
////                            Log.d(TAG, "onInvalidLink: "+ message);
////                        }
////                    }
////            );
////
////        });
////
////        executor.shutdown();
//    }

//    private void openExternalActivityOnItemClicked(Movie item) {
//        showProgressDialog(false);
//        ExecutorService executor = Executors.newSingleThreadExecutor();
//
//        executor.submit(() -> {
//            Movie movie = server.fetch(
//                    item,
//                    item.getState(),
//                    new ServerInterface.ActivityCallback<Movie>() {
//                        @Override
//                        public void onSuccess(Movie result, String title) {
//                            Log.d(TAG, "run: fetch only save movie 2");
//
//                            if (result == null || result.getVideoUrl() == null) {
//                                Log.d(TAG, "openExternalActivityOnItemClicked: onSuccess: empty result");
//                                return;
//                            }
//                            //dbHelper.addMainMovieToHistory(movie.getMainMovieTitle(), null);
////                            movieHandler.post(() -> {
////                                    String type = "video/*"; // It works for all video application
////                                    Uri uri = Uri.parse(result.getVideoUrl());
////                                    Intent in1 = new Intent(Intent.ACTION_VIEW, uri);
////                                    in1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
////                                    //  in1.setPackage("org.videolan.vlc");
////                                    in1.setDataAndType(uri, type);
//                                // movie.getMainMovie().save(dbHelper);
//                                hideProgressDialog(true);
//                                Util.openExternalVideoPlayer(result, getActivity());
//
////                                    getActivity().startActivity(in1);
//                                updateItemFromActivityResult(result);
////                                dbHelper.addMainMovieToHistory(mSelectedMovie);
////                            });
//
//                        }
//
//                        @Override
//                        public void onInvalidCookie(Movie result, String title) {
//                            result.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
//                            Util.openBrowserIntent(result, getActivity(), false, true);
//                        }
//
//                        @Override
//                        public void onInvalidLink(Movie result) {
//
//                        }
//
//
//                        @Override
//                        public void onInvalidLink(String message) {
//                            Log.d(TAG, "onInvalidLink: " + message);
//                        }
//                    }
//            ).movie;
//        });
//
//        executor.shutdown();
//    }

    private void updateCurrentMovieView(Movie movie){
//        movieHandler.post(new Runnable() {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                mSelectedMovie = movie;
//                if (movie.getSubList() != null) {
//                    listRowAdapter.addAll(listRowAdapter.size(), movie.getSubList());
//                }
                mSelectedMovie.setDescription(movie.getDescription());
//                mSelectedMovie.setTitle("hhhhhh");

//                Log.d(TAG, "run: updateCurrentMovieView: "+ mSelectedMovie.getDescription());
//                mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
//                mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(listRowAdapter), mAdapter.size());
                evaluateWatchAction(movie);
                try {
                    initializeBackground(movie);
                }catch (Exception e){
                    Log.d(TAG, "run: error updating background: "+e.getMessage());
                }
//            }
//        }).start();


    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: " + requestCode + ", " + data);

        detailsViewControl.onActivityResult(requestCode, resultCode, data, clickedMovieAdapter, clickedMovieIndex);
        super.onActivityResult(requestCode, resultCode, data);
//
//        if (resultCode != Activity.RESULT_OK || data == null) {
//            Log.d(TAG, "onActivityResult:RESULT_NOT_OK ");
//            return;
//        }
//            Log.d(TAG, "onActivityResult:RESULT_OK ");
//
////                Gson gson = new Gson();
//
//        Movie resultMovie = (Movie) data.getParcelableExtra(DetailsActivity.MOVIE);
////        Type type = new TypeToken<List<Movie>>() {
////        }.getType();
////        ArrayList<Movie> movieSublistString = data.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);
//        ArrayList<Movie> resultMovieSublist = data.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);
//
////        List<Movie> resultMovieSublist = gson.fromJson(movieSublistString, type);
//
//        String result = data.getStringExtra("result");
//
//        //requestCode Movie.REQUEST_CODE_MOVIE_UPDATE is one movie object or 2 for a list of movies
//                //this result is only to update the clicked movie of the sublist only and in some cases to update the description of mSelectedMovie
//                //the id property of Movie object is used to identify the index of the clicked sublist movie
//
//        switch (requestCode) {
//            case Movie.REQUEST_CODE_EXOPLAYER:
//                // open exoplayer after a web activity to fetch the real video link
//                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_EXOPLAYER");
//                if (resultMovie != null) {
//                    resultMovie.setSubList(resultMovieSublist);
//                }
//                Util.openExoPlayer(resultMovie, getActivity(), true);
//                // todo: handle dbHelper
//                updateItemFromActivityResult(resultMovie);
//                dbHelper.addMainMovieToHistory(mSelectedMovie);
//                break;
//            case Movie.REQUEST_CODE_EXTERNAL_PLAYER:
//                // open external activity after a web activity to fetch the real video link
//                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_EXTERNAL_PLAYER");
//                Util.openExternalVideoPlayer(resultMovie, getActivity());
//                // todo: handle dbHelper
////                updateRelatedMovieItem(clickedMovieIndex, resultMovie);
////                mSelectedMovie = resultMovie;
//
//                Log.d(TAG, "onActivityResult: REQUEST_CODE_EXTERNAL_PLAYER: " + resultMovie);
//                updateItemFromActivityResult(resultMovie);
//                dbHelper.addMainMovieToHistory(mSelectedMovie);
//                break;
//            default: //case Movie.REQUEST_CODE_MOVIE_UPDATE
//                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE unknown: "+ requestCode);
//                updateCurrentMovieView(resultMovie);
//        }
//
//
//        if (requestCode == Movie.REQUEST_CODE_MOVIE_UPDATE) {
//                    Movie resultMovie = (Movie) data.getSerializableExtra(DetailsActivity.MOVIE);
//                    if (resultMovie != null) {
//                        Log.d(TAG, "onActivityResult: REQUEST_CODE_MOVIE_UPDATE: " + resultMovie);
//
//                        String movieSublistString = data.getStringExtra(DetailsActivity.MOVIE_SUBLIST);
////                    ArrayList<Movie> movieSublist = (ArrayList<Movie>) data.getSerializableExtra(DetailsActivity.MOVIE_SUBLIST);
//                        Type type = new TypeToken<List<Movie>>() {
//                        }.getType();
//                        List<Movie> movieSublist = gson.fromJson(movieSublistString, type);
//
//                        Log.d(TAG, "onActivityResult: subList:" + movieSublist);
//                        if (movieSublist != null && !movieSublist.isEmpty()) {
//                            String desc = movieSublist.get(0).getDescription();
//                            Log.d(TAG, "onActivityResult: desc: " + desc);
//                            resultMovie.setDescription(desc);
//                            mSelectedMovie.setDescription(desc);
//                            resultMovie.setSubList(movieSublist);
//
//                        }
//                        mSelectedMovie = resultMovie;
//                        if (resultMovie.getSubList() != null) {
//                            listRowAdapter.addAll(listRowAdapter.size(), resultMovie.getSubList());
//                        }
//                        mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
//                        mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(listRowAdapter), mAdapter.size());
//                    }
//                }
//         else if (requestCode == Movie.REQUEST_CODE_FETCH_HTML) {
//                    String result = data.getStringExtra("result");
//                    //todo handle handleOnActivityResultHtml
////                    Movie resultMovie = (Movie) server.handleOnActivityResultHtml(result, mSelectedMovie);
////                    Log.d(TAG, "onActivityResult: REQUEST_CODE_MOVIE_UPDATE: " + resultMovie);
////                    updateItemFromActivityResult(resultMovie);
//                }
//         else if (requestCode == Movie.REQUEST_CODE_EXTERNAL_PLAYER)
//         {
//
//
//                } else if (requestCode == Movie.REQUEST_CODE_MOVIE_LIST) {
//                    //list of movies
//                    Log.d(TAG, "onActivityResult: resultString:" + result);
//                    if (result == null) {
//                        return;
//                    }
//                        Type movieListType = new TypeToken<List<Movie>>() {
//                        }.getType();
//                        List<Movie> movies = gson.fromJson(result, movieListType);
//                        if (movies != null) {
//                            if (mSelectedMovie.getState() == Movie.RESULT_STATE) {
//                                for (Movie mov : movies) {
//                                    // todo; optimize
////                                    if (server.isSeries(mov)) {
////                                        movies.get(movies.indexOf(mov)).setState(Movie.GROUP_OF_GROUP_STATE);
////                                    } else {
////                                        movies.get(movies.indexOf(mov)).setState(Movie.ITEM_STATE);
////                                    }
//                                }
//                            }
//                            Log.d(TAG, "onActivityResult: movie before:" + mSelectedMovie.getSubList());
//
//                            if (mSelectedMovie.getSubList() == null) {
//                                mSelectedMovie.setSubList(new ArrayList<>());
//                            }
//                            mSelectedMovie.setSubList(movies);
//
////                            mSelectedMovie = Movie.clone(mSelectedMovie);
////                            mSelectedMovie.setSubList(movies);
//                            getActivity().runOnUiThread(new Runnable() {
//                                @Override
//                                public void run() {
//                                    for (Movie movie : movies) {
//                                        Log.d(TAG, "run: movie: " + movie);
//                                        listRowAdapter.add(movie);
//                                    }
//                                    mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
//                                    mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(listRowAdapter), mAdapter.size());
//
//                                    if (movies.size() > 0 && movies.get(0) != null) {
//                                        int state = movies.get(0).getState();
//                                        if (state == Movie.RESOLUTION_STATE || state == Movie.VIDEO_STATE) {
//                                            loadActionRow();
//                                        }
//                                    }
//                                }
//                            });
//
////                            Movie mm = new Movie();
////                            mm.setStudio(mSelectedMovie.getStudio());
////                            mm.setState(Movie.GROUP_OF_GROUP_STATE);
////                            mm.setDescription(mSelectedMovie.getDescription());
////                            Intent intent = new Intent(getActivity(), DetailsActivity.class);
////                            if (movies != null){
////                                //Convert the movie object to a JSON string
////                                String movieJson = gson.toJson(movies);
////                                intent.putExtra(DetailsActivity.MOVIE_SUBLIST, (Serializable) movieJson);
////                            }
////                            intent.putExtra(getResources().getString(R.string.movie), (Serializable) (Movie) mm);
////                            getActivity().startActivity(intent);
//
//
//                            Log.d(TAG, "onActivityResult: listRowAdapter:" + listRowAdapter.size());
//                            Log.d(TAG, "onActivityResult: movies:" + mSelectedMovie.getSubList().toString());
//                        }
//
//                    }
//


//        super.onActivityResult(requestCode, resultCode, data);
        // Objects.requireNonNull(getView()).requestFocus();
    }

    private void updateItemFromActivityResult(Movie resultMovie) {
        if (resultMovie == null || resultMovie.getVideoUrl() == null) {
            return;
        }
            //find the correct clicked movie to be updated
            Movie targetMovie = mSelectedMovie.getSubList().get(resultMovie.getRowIndex());
            resultMovie.setTitle(mSelectedMovie.getTitle() + " | " + targetMovie.getTitle());
            targetMovie = resultMovie;

            mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(row), mAdapter.size());
            mAdapter.notifyArrayItemRangeChanged(mAdapter.indexOf(listRowAdapter), mAdapter.size());
            listRowAdapter.replace(resultMovie.getRowIndex(), resultMovie);
    }

//    private void generateMovieHistory(Movie movie, Movie subMovie) {
//        MovieHistory history = new MovieHistory();
////        String mainMovieUrl = movie.getVideoUrl();
////        String title = movie.getTitle();
////        Movie mainMovie = movie.getMainMovie();
////        Log.d(TAG, "generateMovieHistory: main0:"+movie.getTitle());
////        switch (movie.getState()){
////            case Movie.GROUP_OF_GROUP_STATE:
////                mainMovieUrl = movie.getVideoUrl();
////            case Movie.GROUP_STATE:
////                mainMovie = movie.getMainMovie();
////                if (mainMovie != null){
////                    mainMovieUrl = mainMovie.getVideoUrl();
////                    title = title + " | "+ mainMovie.getTitle();
////                }
////
////                mainMovieUrl = (mainMovie != null) ? mainMovie.getMainMovie() : mainMovie.getVideoUrl();
////            case Movie.GROUP_OF_GROUP_STATE:
////                mainMovie = movie.getVideoUrl();
////        }
////        if(mainMovie != null ) {
////            Log.d(TAG, "generateMovieHistory: main1:"+mainMovie.getTitle());
////            mainMovieUrl = mainMovie.getVideoUrl();
////            title = mainMovie.getTitle();
////            Movie mainMovie2 = mainMovie.getMainMovie();
////            if(mainMovie2 != null ) {
////                Log.d(TAG, "generateMovieHistory: main2:"+mainMovie2.getTitle());
////                mainMovieUrl = mainMovie2.getVideoUrl();
////                title = title + " | " + mainMovie2.getTitle();
////                Movie mainMovie3 = mainMovie2.getMainMovie();
////                if(mainMovie3.getMainMovie() != null ) {
////                    Log.d(TAG, "generateMovieHistory: main3:"+mainMovie3.getTitle());
////                    mainMovieUrl = mainMovie3.getVideoUrl();
////                    title = title + " | " + mainMovie3.getTitle();
////                }
////            }
////        }
//
//        if (movie.getState() > Movie.ITEM_STATE) {
//            return;
//        }
////        Movie mainMovie = movie.getMainMovie();
//
//        if (movie.getState() == Movie.GROUP_STATE) {
//            history.setSeason(movie.getTitle());
//        }
//
//        if (movie.getState() == Movie.ITEM_STATE) {
//            history.setEpisode(movie.getTitle());
//        }
//
//        if (subMovie.getState() == Movie.GROUP_STATE) {
//            history.setSeason(subMovie.getTitle());
//        }
//
//        if (subMovie.getState() == Movie.ITEM_STATE) {
//            history.setEpisode(subMovie.getTitle());
//        }
//
////        history.setSeason(subMovie.getTitle() + " | " + mainMovie.getTitle());
////        history.setEpisode(removeDomain(movie.getVideoUrl()));
//
//        String historyMainMovieUrl = movie.getMainMovieTitle();
////        if (mainMovie != null && mainMovie.getVideoUrl() != null) {
////            historyMainMovieUrl = mainMovie.getVideoUrl();
////        }
//        history.setMainMovieUrl(Util.getUrlPathOnly(historyMainMovieUrl));
//        Log.d(TAG, "generateMovieHistory: " + historyMainMovieUrl);
////        subMovie.setMovieHistory(history);
//        dbHelper.saveMovieHistory(history);
//    }


    // Show the ProgressDialog
    private void showProgressDialog(boolean uiThread) {
        if (uiThread) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mProgressDialog != null) {
                        mProgressDialog.show();
                    }
                }
            });
        } else {
            if (mProgressDialog != null) {
                mProgressDialog.show();
            }
        }
    }

    // Hide the ProgressDialog
    private void hideProgressDialog(boolean uiThread, String message) {
        if (uiThread) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                    }
                    if (message != null){
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            if (message != null){
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        }

    }
}