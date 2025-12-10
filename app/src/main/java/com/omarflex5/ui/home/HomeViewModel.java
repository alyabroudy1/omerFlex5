package com.omarflex5.ui.home;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.repository.MediaRepository;
import com.omarflex5.data.source.DataSourceCallback;

import java.net.UnknownHostException;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {

    private final MediaRepository repository;
    // Categories are now static or fetched differently, for MVP assuming fixed or
    // type-based
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();

    // We observe the DB directly now
    private LiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> allMedia;

    // UI mapping for legacy support (until we refactor generic UI)
    private final MutableLiveData<List<Movie>> movies = new MutableLiveData<>();

    private final MutableLiveData<Movie> selectedMovie = new MutableLiveData<>();
    private final MutableLiveData<String> trailerUrl = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.loading());
    private boolean isInitialLoad = true;

    // Use Factory to pass Context
    public HomeViewModel(android.app.Application application) {
        super(application);
        this.repository = MediaRepository.getInstance(application);

        // Trigger Background Sync
        repository.syncFromGlobal();

        // Observe Local DB
        allMedia = repository.getAllMedia();

        // Transform Entity -> Legacy Movie Model for UI
        allMedia.observeForever(mediaItems -> {
            if (mediaItems != null) {
                java.util.List<Movie> mappedMovies = new java.util.ArrayList<>();

                for (com.omarflex5.data.local.model.MediaWithUserState item : mediaItems) {
                    boolean isFav = item.userState != null && item.userState.isFavorite();
                    boolean isWatched = item.userState != null && item.userState.isWatched();

                    MediaEntity entity = item.media;
                    if (entity == null)
                        continue;

                    // Parse categories from JSON
                    java.util.List<String> categories = new java.util.ArrayList<>();
                    if (entity.getCategoriesJson() != null) {
                        try {
                            org.json.JSONArray jsonArray = new org.json.JSONArray(entity.getCategoriesJson());
                            for (int i = 0; i < jsonArray.length(); i++) {
                                categories.add(jsonArray.getString(i));
                            }
                        } catch (Exception e) {
                            // ignore parse error
                        }
                    }

                    // Map Entity to Movie Model
                    Movie movie = new Movie(
                            String.valueOf(entity.getId()), // ID
                            entity.getTitle(), // Title
                            entity.getTitle(), // Original Title (fallback)
                            entity.getDescription(), // Desc
                            entity.getBackdropUrl(), // Background
                            entity.getPosterUrl(), // Poster
                            null, // Trailer (fetched on demand)
                            null, // Video URL
                            String.valueOf(entity.getYear()), // Year
                            String.valueOf(entity.getRating()), // Rating
                            com.omarflex5.data.model.MovieActionType.EXOPLAYER,
                            entity.getType() == com.omarflex5.data.local.entity.MediaType.SERIES,
                            categories, // Categories (Parsed)
                            "TMDB", // Source
                            isFav, // Favorite
                            isWatched // Watched
                    );
                    mappedMovies.add(movie);
                }

                movies.setValue(mappedMovies);
                uiState.setValue(UiState.success());
            } else {
                uiState.setValue(UiState.success()); // Empty but successful
            }
        });

        loadCategories();
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public void retry() {
        isInitialLoad = true;
        repository.syncFromGlobal();
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

    // User Action: Toggle Favorite
    public void toggleFavorite(Movie movie) {
        // Find entity ID by mapping back or parsing ID
        if (movie == null)
            return;
        try {
            long mediaId = Long.parseLong(movie.getId());
            boolean currentFav = movie.isFavorite();
            repository.setFavorite(mediaId, !currentFav);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private void loadCategories() {
        // Mock categories for now as the DB structure changed
        // In the future, Category can be an Entity too
        java.util.List<Category> catList = new java.util.ArrayList<>();
        catList.add(new Category("1", "Movies", new java.util.ArrayList<>()));
        catList.add(new Category("2", "Series", new java.util.ArrayList<>()));
        categories.setValue(catList);
    }

    public void selectCategory(Category category) {
        // Filter logic should be applied here or in DAO
        // For MVP, we rely on getAllMedia observing
    }

    // Called by UI
    public void selectMovie(Movie movie) {
        selectedMovie.setValue(movie);
        // Fetch trailer logic here if needed
    }
}
