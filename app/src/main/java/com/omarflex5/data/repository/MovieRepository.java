package com.omarflex5.data.repository;

import android.os.Handler;
import android.os.Looper;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.source.DataSourceCallback;
import com.omarflex5.data.source.MovieDataSource;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MovieRepository {

    private static volatile MovieRepository INSTANCE;
    private final MovieDataSource dataSource;
    private final ExecutorService executorService;
    private final Handler mainHandler;

    private MovieRepository(MovieDataSource dataSource) {
        this.dataSource = dataSource;
        this.executorService = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static MovieRepository getInstance(MovieDataSource dataSource) {
        if (INSTANCE == null) {
            synchronized (MovieRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MovieRepository(dataSource);
                }
            }
        }
        return INSTANCE;
    }

    public void getCategories(final DataSourceCallback<List<Category>> callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Category> categories = dataSource.getCategories();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(categories);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e);
                        }
                    });
                }
            }
        });
    }

    public void getMoviesByCategory(final String categoryId, final DataSourceCallback<List<Movie>> callback) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Movie> movies = dataSource.getMoviesByCategory(categoryId);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(movies);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e);
                        }
                    });
                }
            }
        });
    }
}
