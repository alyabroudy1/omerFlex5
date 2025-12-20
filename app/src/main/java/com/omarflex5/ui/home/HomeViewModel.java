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
    private final MutableLiveData<Integer> currentPageSize = new MutableLiveData<>(30);

    // Predefined major genres matching TmdbMapper.GENRE_MAP
    private static final java.util.List<String> PREDEFINED_GENRES = java.util.Arrays.asList(
            "أكشن", "دراما", "كوميديا", "رومانسي", "إثارة", "رعب", "خيال علمي", "فانتازيا");

    // Current selected category and pagination state per category
    private String selectedGenre = "all";
    private final java.util.Map<String, Integer> categoryPageSizes = new java.util.HashMap<>();

    public void loadNextPage() {
        Integer current = currentPageSize.getValue();
        if (current == null)
            current = 30;
        int newSize = current + 30;
        currentPageSize.setValue(newSize);

        // Save to category-specific state
        categoryPageSizes.put(selectedGenre, newSize);
    }

    // Use Factory to pass Context
    public HomeViewModel(android.app.Application application) {
        super(application);
        this.repository = MediaRepository.getInstance(application);

        // Trigger Background Sync
        repository.syncFromGlobal();

        // Limit controller
        currentPageSize.setValue(30);

        // Observe Local DB with Pagination
        allMedia = repository.getPagedMedia(currentPageSize);

        // Transform Entity -> Legacy Movie Model for UI
        allMedia.observeForever(mediaItems -> {
            if (mediaItems != null) {
                java.util.List<Movie> mappedMovies = new java.util.ArrayList<>();
                for (com.omarflex5.data.local.model.MediaWithUserState item : mediaItems) {
                    Movie movie = mapToMovie(item);
                    if (movie != null)
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

    private Movie mapToMovie(com.omarflex5.data.local.model.MediaWithUserState item) {
        MediaEntity entity = item.media;
        if (entity == null)
            return null;

        boolean isFav = item.userState != null && item.userState.isFavorite();
        boolean isWatched = item.userState != null && item.userState.isWatched();
        long progress = item.userState != null ? item.userState.getWatchProgress() : 0;
        long duration = item.userState != null ? item.userState.getDuration() : 0;

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

        return new Movie(
                String.valueOf(entity.getId()), // ID
                entity.getTitle(), // Title (localized Arabic)
                entity.getOriginalTitle(), // Original Title (English for search)
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
                isWatched, // Watched
                progress,
                duration);
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
        java.util.List<Category> catList = new java.util.ArrayList<>();

        // Add "Continue Watching" category at the very top
        catList.add(new Category("continue", "متابعة المشاهدة", new java.util.ArrayList<>()));

        // Add "All" category
        catList.add(new Category("all", "الكل", new java.util.ArrayList<>()));

        // Add Arabic category (language-based)
        catList.add(new Category("arabic", "عربي", new java.util.ArrayList<>()));

        // Add predefined genre categories
        for (String genre : PREDEFINED_GENRES) {
            catList.add(new Category(genre, genre, new java.util.ArrayList<>()));
        }

        categories.setValue(catList);

        // Initialize pagination state for each category
        categoryPageSizes.put("continue", 15); // Smaller limit for continue watching
        categoryPageSizes.put("all", 30);
        categoryPageSizes.put("arabic", 30);
        for (String genre : PREDEFINED_GENRES) {
            categoryPageSizes.put(genre, 30);
        }
    }

    public void selectCategory(Category category) {
        selectedGenre = category.getId();

        // Get or initialize page size for this category
        Integer pageSize = categoryPageSizes.get(selectedGenre);
        if (pageSize == null) {
            pageSize = 30;
            categoryPageSizes.put(selectedGenre, pageSize);
        }
        currentPageSize.setValue(pageSize);

        // Switch data source based on category
        if ("continue".equals(selectedGenre)) {
            allMedia = repository.getContinueWatching(pageSize);
        } else if ("all".equals(selectedGenre)) {
            allMedia = repository.getPagedMedia(currentPageSize);
        } else if ("arabic".equals(selectedGenre)) {
            allMedia = repository.getMediaByLanguage("ar", currentPageSize);
        } else {
            allMedia = repository.getMediaByGenre(selectedGenre, currentPageSize);
        }

        // Re-observe to update UI
        allMedia.observeForever(mediaItems -> {
            if (mediaItems != null) {
                java.util.List<Movie> mappedMovies = new java.util.ArrayList<>();
                for (com.omarflex5.data.local.model.MediaWithUserState item : mediaItems) {
                    Movie movie = mapToMovie(item);
                    if (movie != null)
                        mappedMovies.add(movie);
                }
                movies.setValue(mappedMovies);
                uiState.setValue(UiState.success());
            } else {
                uiState.setValue(UiState.success()); // Empty but successful
            }
        });
    }

    // Called by UI
    public void selectMovie(Movie movie) {
        selectedMovie.setValue(movie);
        trailerUrl.setValue(null); // Reset prev

        if (movie == null)
            return;

        try {
            long mediaId = Long.parseLong(movie.getId());
            repository.getTrailerUrl(getApplication(), mediaId, new DataSourceCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    trailerUrl.setValue(result);
                }

                @Override
                public void onError(Throwable t) {
                    // Log error but don't disrupt UI
                    t.printStackTrace();
                }
            });
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

}
