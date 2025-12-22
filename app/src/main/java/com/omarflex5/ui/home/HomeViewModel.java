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
    private final MutableLiveData<String> selectedCategoryTrigger = new MutableLiveData<>("all");
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

    // Server cache for resolving IDs to Names
    private final java.util.Map<Long, com.omarflex5.data.local.entity.ServerEntity> serverCache = new java.util.HashMap<>();
    private final LiveData<List<com.omarflex5.data.local.entity.ServerEntity>> allServers;

    // Use Factory to pass Context
    public HomeViewModel(android.app.Application application) {
        super(application);
        this.repository = MediaRepository.getInstance(application);
        com.omarflex5.data.repository.ServerRepository serverRepo = com.omarflex5.data.repository.ServerRepository
                .getInstance(application);

        // Trigger Background Sync
        repository.syncFromGlobal();

        // Load servers for resolving source labels
        allServers = serverRepo.getAllServersLive();

        // Observe Local DB with Pagination using SwitchMap
        allMedia = androidx.lifecycle.Transformations.switchMap(selectedCategoryTrigger, id -> {
            Integer pageSize = categoryPageSizes.get(id);
            if (pageSize == null)
                pageSize = 30;

            if ("continue".equals(id)) {
                return repository.getContinueWatching(pageSize);
            } else if ("all".equals(id)) {
                return repository.getPagedMedia(currentPageSize);
            } else if ("arabic".equals(id)) {
                return repository.getMediaByLanguage("ar", currentPageSize);
            } else {
                return repository.getMediaByGenre(id, currentPageSize);
            }
        });

        // Mediator to combine Media and Servers
        // We need servers loaded to correctly map the source labels
        androidx.lifecycle.MediatorLiveData<List<com.omarflex5.data.local.model.MediaWithUserState>> combinedData = new androidx.lifecycle.MediatorLiveData<>();

        combinedData.addSource(allServers, servers -> {
            if (servers != null) {
                serverCache.clear();
                for (com.omarflex5.data.local.entity.ServerEntity s : servers) {
                    serverCache.put(s.getId(), s);
                }
                // Trigger re-mapping if we already have media
                if (allMedia.getValue() != null) {
                    combinedData.setValue(allMedia.getValue());
                }
            }
        });

        combinedData.addSource(allMedia, mediaItems -> {
            combinedData.setValue(mediaItems);
        });

        // Transform Entity -> Movie Model
        combinedData.observeForever(mediaItems -> {
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

        // DEBUG: Log entity details
        // android.util.Log.d("HOME_DEBUG", "=== mapToMovie for " + entity.getTitle() +
        // " ===");
        // android.util.Log.d("HOME_DEBUG", " entityId=" + entity.getId() +
        // ", tmdbId=" + entity.getTmdbId() +
        // ", primaryServerId=" + entity.getPrimaryServerId());
        // if (item.userState != null) {
        // android.util.Log.d("HOME_DEBUG", " userState: watchProgress=" +
        // item.userState.getWatchProgress() +
        // ", duration=" + item.userState.getDuration() +
        // ", lastSourceServerId=" + item.userState.getLastSourceServerId());
        // } else {
        // android.util.Log.d("HOME_DEBUG", " userState: NULL");
        // }

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

        String yearStr = (entity.getYear() != null && entity.getYear() > 0) ? String.valueOf(entity.getYear()) : "";
        String ratingStr = (entity.getRating() != null && entity.getRating() > 0)
                ? String.format("%.1f", entity.getRating())
                : "";

        // ROUTING LOGIC: Determine Source URL, Server Label, and Display Title
        String sourceUrl = null;
        String sourceLabel = "TMDB";
        Long serverId = null;
        String displayTitle = entity.getTitle(); // Default to TMDB title

        // 1. Try to route based on User History (Last Used Source)
        if (item.userState != null && item.userState.getLastSourceServerId() != null) {
            Long lastServerId = item.userState.getLastSourceServerId();

            // Resolve Label from Cache
            com.omarflex5.data.local.entity.ServerEntity server = serverCache.get(lastServerId);
            if (server != null) {
                sourceLabel = server.getLabel();
                serverId = lastServerId;
            }

            // Resolve URL and Title
            if (item.userState.getLastSourceUrl() != null) {
                sourceUrl = item.userState.getLastSourceUrl();
                // Try to find the title from matching source even if we have URL history
                if (item.sources != null) {
                    for (com.omarflex5.data.local.entity.MediaSourceEntity source : item.sources) {
                        if (source.getServerId() == lastServerId) {
                            if (source.getTitle() != null && !source.getTitle().isEmpty()) {
                                displayTitle = source.getTitle();
                            }
                            break;
                        }
                    }
                }
            } else {
                // Fallback: Find source for this server in sources list
                if (item.sources != null) {
                    for (com.omarflex5.data.local.entity.MediaSourceEntity source : item.sources) {
                        if (source.getServerId() == lastServerId) {
                            sourceUrl = source.getExternalUrl();
                            if (source.getTitle() != null && !source.getTitle().isEmpty()) {
                                displayTitle = source.getTitle();
                            }
                            break;
                        }
                    }
                }
            }
        }

        // 2. Legacy Fallback: Use Primary Server ID from MediaEntity (if History
        // missing)
        if (serverId == null && entity.getPrimaryServerId() != null) {
            Long primServerId = entity.getPrimaryServerId();
            com.omarflex5.data.local.entity.ServerEntity server = serverCache.get(primServerId);
            if (server != null) {
                sourceLabel = server.getLabel();
                serverId = primServerId;
            } else {
                // Try manual relation (if Room populated it)
                if (item.server != null) {
                    sourceLabel = item.server.getLabel();
                    serverId = item.server.getId();
                }
            }

            // Resolve URL and Title for primary server
            if (item.sources != null) {
                for (com.omarflex5.data.local.entity.MediaSourceEntity source : item.sources) {
                    if (source.getServerId() == (serverId != null ? serverId : -1)) {
                        sourceUrl = source.getExternalUrl();
                        if (source.getTitle() != null && !source.getTitle().isEmpty()) {
                            displayTitle = source.getTitle();
                        }
                        break;
                    }
                }
            }
        }

        // 3. Fallback for Scraper Items (if no history and no primary set, but has
        // source)
        if (serverId == null && item.server != null) {
            sourceLabel = item.server.getLabel();
            serverId = item.server.getId();
            // Try to find title
            if (item.sources != null) {
                for (com.omarflex5.data.local.entity.MediaSourceEntity source : item.sources) {
                    if (source.getServerId() == serverId) {
                        if (source.getTitle() != null && !source.getTitle().isEmpty()) {
                            displayTitle = source.getTitle();
                        }
                        break;
                    }
                }
            }
        }

        return new Movie(
                String.valueOf(entity.getId()), // ID
                displayTitle, // Title (Source preferred)
                entity.getOriginalTitle(), // Original Title (English for search)
                entity.getDescription(), // Desc
                entity.getBackdropUrl() != null ? entity.getBackdropUrl() : entity.getPosterUrl(), // Background
                entity.getPosterUrl(), // Poster
                null, // Trailer (fetched on demand)
                sourceUrl, // Video URL (Now populated to simulate search flow)
                yearStr, // Year
                ratingStr, // Rating
                com.omarflex5.data.model.MovieActionType.DETAILS,
                entity.getType() == com.omarflex5.data.local.entity.MediaType.SERIES,
                categories, // Categories (Parsed)
                sourceLabel, // Source (Dynamic based on history)
                isFav, // Favorite
                isWatched, // Watched
                progress,
                duration,
                item.userState != null ? item.userState.getSeasonId() : null, // seasonId
                item.userState != null ? item.userState.getEpisodeId() : null, // episodeId
                entity.getTmdbId(),
                serverId); // ID for routing
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

        // Update page size reference for this category if needed
        Integer pageSize = categoryPageSizes.get(selectedGenre);
        if (pageSize == null) {
            pageSize = 30;
            categoryPageSizes.put(selectedGenre, pageSize);
        }
        currentPageSize.setValue(pageSize);

        // Trigger the switchMap in constructor
        selectedCategoryTrigger.setValue(selectedGenre);
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
