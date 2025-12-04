package com.omarflex5.data.repository;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.source.DataSourceCallback;
import com.omarflex5.data.source.remote.MovieDBServer;

import java.util.List;

public class MovieRepository {

    private static volatile MovieRepository INSTANCE;
    private final MovieDBServer remoteDataSource;

    private MovieRepository() {
        this.remoteDataSource = new MovieDBServer();
    }

    public static MovieRepository getInstance() {
        if (INSTANCE == null) {
            synchronized (MovieRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MovieRepository();
                }
            }
        }
        return INSTANCE;
    }

    public void getCategories(DataSourceCallback<List<Category>> callback) {
        remoteDataSource.getCategories(callback);
    }

    public void getMoviesByCategory(String categoryId, DataSourceCallback<List<Movie>> callback) {
        remoteDataSource.getMoviesByCategory(categoryId, callback);
    }
}
