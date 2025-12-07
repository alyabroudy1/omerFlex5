package com.omarflex5.temp.omerflex.view.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.database.MovieDbHelper;
import com.omerflex.view.DetailsActivity;
import com.omerflex.view.VideoDetailsFragment;
import com.omerflex.view.mobile.view.CategoryAdapter;
import com.omerflex.view.mobile.view.HorizontalMovieAdapter;
import com.omerflex.view.mobile.view.OnMovieClickListener;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MobileMovieDetailActivity extends AppCompatActivity {
    public static final String TAG = "MobileDetailActivity";
    public static final String EXTRA_MOVIE_TITLE = "extra_movie_title";
    public static final String EXTRA_MOVIE_IMAGE_URL = "extra_movie_image_url";
    public static final String EXTRA_MOVIE_CATEGORY = "extra_movie_category";
    public static final String EXTRA_MOVIE_RATING = "extra_movie_rating";
    public static final String EXTRA_MOVIE_STUDIO = "extra_movie_studio";

    private TextView titleTextView;
    private TextView categoryTextView;
    private ImageView imageView;
    private RecyclerView relatedMoviesRecyclerView;
    private HorizontalMovieAdapter relatedMoviesAdapter;
    private TextView ratingTextView;
    private TextView studioTextView;
    private TextView descriptionTextView;
    private TextView historyTextView;
    private Button watchButtonView;
    private Movie mSelectedMovie;
    private Handler handler = new Handler();
    Activity activity;
    static AbstractServer server;
    public MovieDbHelper dbHelper;
    private static int clickedMovieIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_mobile_movie_detail);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
        activity = this;
        dbHelper = MovieDbHelper.getInstance(activity);

        mSelectedMovie = Util.recieveSelectedMovie(getIntent());
//        server = ServerManager.determineServer(mSelectedMovie, null, activity, null);
        server = ServerConfigRepository.getInstance().getServer(mSelectedMovie.getStudio());

        initializeView(mSelectedMovie);

        // Set up the related movies RecyclerView
        fetchRelatedMovies(mSelectedMovie);
    }

    private void initializeView(Movie mSelectedMovie) {
        titleTextView = findViewById(R.id.titleTextView);
        categoryTextView = findViewById(R.id.categoryTextView);
        imageView = findViewById(R.id.imageView);
        relatedMoviesRecyclerView = findViewById(R.id.relatedMoviesRecyclerView);

        // Set the movie details
        titleTextView.setText(mSelectedMovie.getTitle());
        Glide.with(this).load(mSelectedMovie.getCardImageUrl()).into(imageView);

        ratingTextView = findViewById(R.id.ratingTextView);
        studioTextView = findViewById(R.id.studioTextView);
        descriptionTextView = findViewById(R.id.descriptionTextView);
        historyTextView = findViewById(R.id.historyTextView);
        watchButtonView = findViewById(R.id.watch_button);


        watchButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: watch button");
                Movie sampleMovie = relatedMoviesAdapter.getMovieList().get(0);
                clickedMovieIndex = 0;
//                dbHelper.addMainMovieToHistory(mSelectedMovie);
                MovieFetchProcess movieFetchProcess= server.fetch(
                        sampleMovie,
                        Movie.ACTION_WATCH_LOCALLY,
                        new ServerInterface.ActivityCallback<Movie>() {
                            @Override
                            public void onSuccess(Movie result, String title) {
                                Util.openExoPlayer(result, activity, true);
                            }

                            @Override
                            public void onInvalidCookie(Movie result, String title) {
                                fetchCookie(result);
                            }

                            @Override
                            public void onInvalidLink(Movie result) {

                            }


                            @Override
                            public void onInvalidLink(String message) {

                            }
                        }
                );
//                if (movieFetchProcess.stateCode == MovieFetchProcess.FETCH_PROCESS_EXOPLAYER){
//                    Util.openExoPlayer(movieFetchProcess.movie, activity, true);
//                }
            }
        });


        // Set the rating and studio name
        ratingTextView.setText(mSelectedMovie.getRate());
        studioTextView.setText(mSelectedMovie.getStudio());
        descriptionTextView.setText(mSelectedMovie.getDescription());

        String history = "";
        if (mSelectedMovie.getMovieHistory() != null &&
                (
                        mSelectedMovie.getState() == Movie.GROUP_OF_GROUP_STATE ||
                                mSelectedMovie.getState() == Movie.GROUP_STATE
                )
        ) {
//            history = ((mSelectedMovie.getMovieHistory().getEpisode() != null) ?
//                    (" | " + mSelectedMovie.getMovieHistory().getEpisode()) : "") +
//                    ((mSelectedMovie.getMovieHistory().getSeason() != null) ? (" | " + mSelectedMovie.getMovieHistory().getSeason()) : "");
        }


        historyTextView.setText(history);

        categoryTextView.setText(server.determineRelatedMovieLabel(mSelectedMovie));
        relatedMoviesAdapter = new HorizontalMovieAdapter(this, new ArrayList<>(), new RelatedMovieItemClickListener(this, dbHelper));
        relatedMoviesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        relatedMoviesRecyclerView.setAdapter(relatedMoviesAdapter);
    }

    private void fetchCookie(Movie result) {
        result.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
        Util.openBrowserIntent(result, activity, true, true, true);
    }

    private void evaluateWatchButton() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!relatedMoviesAdapter.getMovieList().isEmpty()){
                    Movie sampleMovie = relatedMoviesAdapter.getMovieList().get(0);
                    boolean watchButtonCond = sampleMovie.getState() == Movie.RESOLUTION_STATE ||
                            sampleMovie.getState() == Movie.VIDEO_STATE;

                    if (watchButtonCond){
                        watchButtonView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }

    private void fetchRelatedMovies(Movie movie) {

        if (movie.getFetch() == Movie.NO_FETCH_MOVIE_AT_START && movie.getSubList() != null) {
            Log.d(TAG, "fetchRelatedMovies: NO_FETCH_MOVIE_AT_START");
            updateRelatedMovieAdapter((ArrayList<Movie>) movie.getSubList());
            evaluateWatchButton();
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            MovieFetchProcess movieFetchProcess = server.fetch(
                    mSelectedMovie,
                    mSelectedMovie.getState(),
                    new ServerInterface.ActivityCallback<Movie>() {
                        @Override
                        public void onSuccess(Movie result, String title) {
                            updateCurrentMovieView(result);
                            updateRelatedMovieAdapter((ArrayList<Movie>) result.getSubList());
                            evaluateWatchButton();
                        }

                        @Override
                        public void onInvalidCookie(Movie result, String title) {
                            fetchCookie(result);
                        }

                        @Override
                        public void onInvalidLink(Movie result) {
                            return;
                        }

                        @Override
                        public void onInvalidLink(String message) {

                        }
                    }
            );
//            if (
//                    movieFetchProcess.stateCode == MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN ||
//                    movieFetchProcess.movie.getSubList() == null
//            ) {
//                // todo: Handle error
//                return;
//            }

//            updateMovieView(movieFetchProcess.movie);
//            updateRelatedMovieAdapter(movieFetchProcess.movie);
//            evaluateWatchButton();
        });

        executor.shutdown();
    }

//    private Movie updateMovieOnActivityResult(Movie resultMovie, List<Movie> resultMovieSublist) {
//        if (resultMovie == null) {
//            resultMovie = mSelectedMovie;
//        }
//        if (resultMovieSublist == null) {
//            resultMovieSublist = mSelectedMovie.getSubList();
//        }
//        Log.d(TAG, "onActivityResult: REQUEST_CODE_MOVIE_UPDATE: " + resultMovie);
//
//
////                    ArrayList<Movie> movieSublist = (ArrayList<Movie>) data.getSerializableExtra(DetailsActivity.MOVIE_SUBLIST);
//
//
//        Log.d(TAG, "onActivityResult: subList:" + resultMovieSublist);
//        if (resultMovieSublist != null && !resultMovieSublist.isEmpty()) {
//            String desc = resultMovieSublist.get(0).getDescription();
//            Log.d(TAG, "onActivityResult: desc: " + desc);
//            resultMovie.setDescription(desc);
//            resultMovie.setSubList(resultMovieSublist);
//
//        }
//        // update subMovies state
//        if (resultMovie.getState() == Movie.RESULT_STATE && server != null && resultMovieSublist != null) {
//            for (Movie mov : resultMovieSublist) {
//                //todo optimize
////                if (server.isSeries(mov)) {
////                    resultMovieSublist.get(resultMovieSublist.indexOf(mov)).setState(Movie.GROUP_OF_GROUP_STATE);
////                } else {
////                    resultMovieSublist.get(resultMovieSublist.indexOf(mov)).setState(Movie.ITEM_STATE);
////                }
//                mov.setState(server.detectMovieState(mov));
//            }
//        }
//        updateCurrentMovieView(resultMovie);
//        return resultMovie;
//    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Log.d("TAG", "onActivityResult: ");


        // cases:
        // 1.Movie.REQUEST_CODE_MOVIE_UPDATE
        // 2.Movie.REQUEST_CODE_EXOPLAYER
        // 3.Movie.REQUEST_CODE_FETCH_HTML
        // 4.Movie.REQUEST_CODE_EXTERNAL_PLAYER
        // 5.Movie.REQUEST_CODE_MOVIE_LIST
        // should update:
        // 1.mSelectedMovie
        // 2.relatedMovieAdapter

        // real cases:
        // 1. fetch movie details using web browser as the website contains only js
        //      - suggestion: Movie.REQUEST_CODE_MOVIE_UPDATE
        // 2. fetch a real movie link using the browser
        //      - suggestion: Movie.REQUEST_CODE_MOVIE_UPDATE
        // 3. fetch cookie and movie details
        //      - suggestion: Movie.REQUEST_CODE_MOVIE_UPDATE
        //      - to be aware of the movie state and how to handle the situation
        // requests to be :
        // Movie.REQUEST_CODE_MOVIE_UPDATE
        // Movie.REQUEST_CODE_EXTERNAL_PLAYER
        // Movie.REQUEST_CODE_EXOPLAYER

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.d(TAG, "onActivityResult:RESULT_NOT_OK ");
            return;
        }
//        Gson gson = new Gson();

        Movie resultMovie = (Movie) data.getParcelableExtra(DetailsActivity.MOVIE);
//        Type type = new TypeToken<List<Movie>>() {
//        }.getType();
//        ArrayList<Movie> movieSublistString = data.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);
        ArrayList<Movie> resultMovieSublist = data.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);

//        List<Movie> resultMovieSublist = gson.fromJson(movieSublistString, type);

        String result = data.getStringExtra("result");


        Log.d(TAG, "onActivityResult:RESULT_OK ");

        //requestCode Movie.REQUEST_CODE_MOVIE_UPDATE is one movie object or 2 for a list of movies
        //this result is only to update the clicked movie of the sublist only and in some cases to update the description of mSelectedMovie
        //the id property of Movie object is used to identify the index of the clicked sublist movie
        switch (requestCode) {
//            case Movie.REQUEST_CODE_MOVIE_UPDATE:
//                // update current movie data
//                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_MOVIE_UPDATE");
////            case Movie.REQUEST_CODE_MOVIE_LIST:
////                // fetch a list of movie like search result
////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_MOVIE_LIST");
////                // returns null or movie
////                mSelectedMovie = updateMovieOnActivityResult(resultMovie, resultMovieSublist);
////                break;
////            case Movie.REQUEST_CODE_FETCH_HTML:
////                // unknown ?
////                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_FETCH_HTML");
//////                mSelectedMovie = (Movie) server.handleOnActivityResultHtml(result, mSelectedMovie);
////                break;
            case Movie.REQUEST_CODE_EXOPLAYER:
                // open exoplayer after a web activity to fetch the real video link
                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_EXOPLAYER");
               if (resultMovie != null){
                   resultMovie.setSubList(resultMovieSublist);
               }
                Util.openExoPlayer(resultMovie, activity, true);
                // todo: handle dbHelper
                updateRelatedMovieItem(clickedMovieIndex, resultMovie);
//                 dbHelper.addMainMovieToHistory(mSelectedMovie);
//                mSelectedMovie = resultMovie;
                break;
            case Movie.REQUEST_CODE_EXTERNAL_PLAYER:
                // open external activity after a web activity to fetch the real video link
                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE_EXTERNAL_PLAYER");
                Util.openExternalVideoPlayer(resultMovie, activity);
                // todo: handle dbHelper
//                 dbHelper.addMainMovieToHistory(mSelectedMovie);
                updateRelatedMovieItem(clickedMovieIndex, resultMovie);
//                mSelectedMovie = resultMovie;
                break;
            default: //case Movie.REQUEST_CODE_MOVIE_UPDATE
                Log.d(TAG, "handleOnDetailsActivityResult: REQUEST_CODE unknown: "+ requestCode);
                updateCurrentMovieView(resultMovie);
                if (resultMovieSublist != null && !resultMovieSublist.isEmpty()){
                    updateRelatedMovieAdapter(resultMovieSublist);
                }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateRelatedMovieItem(int clickedMovieIndex, Movie resultMovie) {
        resultMovie.setTitle(mSelectedMovie.getTitle());
        relatedMoviesAdapter.getMovieList().set(clickedMovieIndex, resultMovie);
        relatedMoviesAdapter.notifyItemChanged(clickedMovieIndex);
    }

    private void updateRelatedMovieAdapter(ArrayList<Movie> movieList) {
//        Log.d("TAG", "updateRelatedMovieAdapter: "+ movieList);
        if (movieList.isEmpty()){
            return;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                relatedMoviesAdapter.getMovieList().addAll(movieList);
                relatedMoviesAdapter.notifyDataSetChanged();
            }
        });
    }

    private void updateCurrentMovieView(Movie movie) {
        Log.d("TAG", "updateRelatedMovieAdapter: "+ movie);
        handler.post(new Runnable() {
            @Override
            public void run() {
                titleTextView.setText(movie.getTitle());
                descriptionTextView.setText(movie.getDescription());
                Glide.with(activity).load(movie.getCardImageUrl()).into(imageView);
                relatedMoviesAdapter.notifyDataSetChanged();
            }
        });
    }
    public static class RelatedMovieItemClickListener implements OnMovieClickListener {
        Activity activity;
        public String TAG = "RelatedMovieItemClickListener";

        CategoryAdapter categoryAdapter;
        MovieDbHelper dbHelper;

        public RelatedMovieItemClickListener(Activity activity, MovieDbHelper dbHelper) {
            this.activity = activity;
            this.dbHelper = dbHelper;
        }

        public void onMovieClick(Movie movie, int position, HorizontalMovieAdapter horizontalMovieAdapter) {
// Check if the clicked movie matches the criteria to extend the category
//            Log.d(TAG, "onMovieClick: "+ categoryAdapter.getItemCount());

            clickedMovieIndex = position;
            // 1. case: open external movie player
            // 2. case open web browser
            // 3. case open details activity
//            boolean externalPlayerCond = movie.getState() == Movie.VIDEO_STATE || movie.getState() == Movie.RESOLUTION_STATE;
//            if (externalPlayerCond) {
//                Util.openExternalVideoPlayer(movie, activity);
//            } else if (movie.getState() == Movie.BROWSER_STATE || movie.getState() == Movie.COOKIE_STATE) {
//                movie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
//                Util.openBrowserIntent(movie, activity, false, true);
//            } else {
//                Util.openMobileDetailsIntent(movie, activity, false);
//            }
            int nextAction = server.fetchNextAction(movie);
            switch (nextAction){
                case VideoDetailsFragment.ACTION_OPEN_DETAILS_ACTIVITY:
                    Util.openMobileDetailsIntent(movie, activity, false);
                    break;
                case VideoDetailsFragment.ACTION_OPEN_EXTERNAL_ACTIVITY:
//                    dbHelper.addMainMovieToHistory(movie);
                    Util.openExternalVideoPlayer(movie, activity);
                    break;
                case VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY:
//                    dbHelper.addMainMovieToHistory(movie);
                    MovieFetchProcess process = server.fetch(movie, movie.getState(), new ServerInterface.ActivityCallback<Movie>() {
                        @Override
                        public void onSuccess(Movie result, String title) {
                            Util.openExternalVideoPlayer(movie, activity);
                        }

                        @Override
                        public void onInvalidCookie(Movie result, String title) {
                            movie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
                            Util.openBrowserIntent(movie, activity, false, true, true);
                        }

                        @Override
                        public void onInvalidLink(Movie result) {

                        }

                        @Override
                        public void onInvalidLink(String message) {

                        }
                    });
                    break;

            }

        }
    }


}


