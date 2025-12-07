package com.omarflex5.temp.omerflex.data.source.local;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.omerflex.db.AppDatabase;
import com.omerflex.db.MovieDao;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieRepository;

import java.util.ArrayList;
import java.util.List;

public class LocalDataSource {

    private static LocalDataSource instance;
    private MovieDao movieDao;

    private LocalDataSource(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.movieDao = db.movieDao();
    }

    public static synchronized LocalDataSource getInstance(Context context) {
        if (instance == null) {
            instance = new LocalDataSource(context.getApplicationContext());
        }
        return instance;
    }

    public void getMovieByUrl(String videoUrl, MovieRepository.MovieCallback callback) {
        new Thread(() -> {
            Movie movie = movieDao.getMovieByVideoUrlSync(videoUrl);
            callback.onMovieFetched(movie);
        }).start();
    }

    public void getHomepageMovies(MovieRepository.MovieListCallback callback) {
//        LiveData<List<Movie>> historyMovies = movieDao.getWatchedMovies(Movie.SERVER_IPTV);
//        historyMovies.observeForever(movies -> {
//            callback.onMovieListFetched("History", (ArrayList<Movie>) movies);
//        });
    }

    public void saveMovie(Movie movie) {
        new Thread(() -> movieDao.insert(movie)).start();
    }

    public void fetchMovieDetails(Movie mSelectedMovie, MovieRepository.MovieCallback callback) {
    }
}