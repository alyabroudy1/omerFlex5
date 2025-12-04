package com.omarflex5.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.repository.MovieRepository;
import com.omarflex5.data.source.DataSourceCallback;

import java.util.List;

public class HomeViewModel extends ViewModel {

    private final MovieRepository repository;
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Movie>> movies = new MutableLiveData<>();
    private final MutableLiveData<Movie> selectedMovie = new MutableLiveData<>();
    private final MutableLiveData<String> trailerUrl = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private boolean isInitialLoad = true;

    public HomeViewModel(MovieRepository repository) {
        this.repository = repository;
        loadCategories();
    }

    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    public LiveData<List<Movie>> getMovies() {
        return movies;
    }

    public LiveData<Movie> getSelectedMovie() {
        return selectedMovie;
    }

    public LiveData<String> getTrailerUrl() {
        return trailerUrl;
    }

    public LiveData<String> getError() {
        return error;
    }

    private void loadCategories() {
        repository.getCategories(new DataSourceCallback<List<Category>>() {
            @Override
            public void onSuccess(List<Category> result) {
                categories.setValue(result);
                if (!result.isEmpty()) {
                    selectCategory(result.get(0));
                }
            }

            @Override
            public void onError(Throwable t) {
                error.setValue(t.getMessage());
            }
        });
    }

    public void selectCategory(Category category) {
        repository.getMoviesByCategory(category.getId(), new DataSourceCallback<List<Movie>>() {
            @Override
            public void onSuccess(List<Movie> result) {
                movies.setValue(result);
                // Auto-select first movie only on initial app load
                if (isInitialLoad && !result.isEmpty()) {
                    selectMovie(result.get(0));
                    isInitialLoad = false;
                }
            }

            @Override
            public void onError(Throwable t) {
                error.setValue(t.getMessage());
            }
        });
    }

    public void selectMovie(Movie movie) {
        selectedMovie.setValue(movie);

        // Fetch trailer for selected movie
        try {
            int movieId = Integer.parseInt(movie.getId());
            repository.getMovieTrailer(movieId, new DataSourceCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    trailerUrl.setValue(result);
                }

                @Override
                public void onError(Throwable t) {
                    trailerUrl.setValue(null);
                }
            });
        } catch (NumberFormatException e) {
            trailerUrl.setValue(null);
        }
    }
}
