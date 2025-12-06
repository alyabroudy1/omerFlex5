package com.omarflex5.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.repository.MovieRepository;
import com.omarflex5.data.source.DataSourceCallback;

import java.net.UnknownHostException;
import java.util.List;

public class HomeViewModel extends ViewModel {

    private final MovieRepository repository;
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Movie>> movies = new MutableLiveData<>();
    private final MutableLiveData<Movie> selectedMovie = new MutableLiveData<>();
    private final MutableLiveData<String> trailerUrl = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.loading());
    private boolean isInitialLoad = true;

    public HomeViewModel(MovieRepository repository) {
        this.repository = repository;
        loadCategories();
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    /**
     * Retry loading data after an error.
     */
    public void retry() {
        isInitialLoad = true;
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
        uiState.setValue(UiState.loading());
        repository.getCategories(new DataSourceCallback<List<Category>>() {
            @Override
            public void onSuccess(List<Category> result) {
                categories.setValue(result);
                if (!result.isEmpty()) {
                    selectCategory(result.get(0));
                } else {
                    uiState.setValue(UiState.success());
                }
            }

            @Override
            public void onError(Throwable t) {
                error.setValue(t.getMessage());
                // Check error type for user-friendly message
                if (t instanceof UnknownHostException ||
                        t.getMessage() != null && t.getMessage().contains("Unable to resolve host")) {
                    uiState.setValue(UiState.networkError());
                } else {
                    uiState.setValue(UiState.serverError(t.getMessage()));
                }
            }
        });
    }

    public void selectCategory(Category category) {
        repository.getMoviesByCategory(category.getId(), new DataSourceCallback<List<Movie>>() {
            @Override
            public void onSuccess(List<Movie> result) {
                movies.setValue(result);
                // Set success state when movies are loaded
                uiState.setValue(UiState.success());
                // Auto-select first movie only on initial app load
                if (isInitialLoad && !result.isEmpty()) {
                    selectMovie(result.get(0));
                    isInitialLoad = false;
                }
            }

            @Override
            public void onError(Throwable t) {
                error.setValue(t.getMessage());
                if (t instanceof UnknownHostException ||
                        t.getMessage() != null && t.getMessage().contains("Unable to resolve host")) {
                    uiState.setValue(UiState.networkError());
                } else {
                    uiState.setValue(UiState.serverError(t.getMessage()));
                }
            }
        });
    }

    public void selectMovie(Movie movie) {
        selectedMovie.setValue(movie);

        // Fetch trailer for selected movie
        try {
            int movieId = Integer.parseInt(movie.getId());
            repository.getMovieTrailer(movieId, movie.isTvShow(), new DataSourceCallback<String>() {
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
