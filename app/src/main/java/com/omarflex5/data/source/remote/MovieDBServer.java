package com.omarflex5.data.source.remote;

import android.util.Log;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.model.MovieActionType;
import com.omarflex5.data.model.tmdb.TmdbGenreResponse;
import com.omarflex5.data.model.tmdb.TmdbMapper;
import com.omarflex5.data.model.tmdb.TmdbMovie;
import com.omarflex5.data.model.tmdb.TmdbMovieResponse;
import com.omarflex5.data.model.tmdb.TmdbVideo;
import com.omarflex5.data.model.tmdb.TmdbVideoResponse;
import com.omarflex5.data.remote.TmdbCacheService;
import com.omarflex5.data.source.DataSourceCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MovieDBServer {

    private static final String TAG = "MovieDBServer";
    private final TmdbApi api;
    private final TmdbCacheService cacheService;

    public MovieDBServer() {
        this.api = BaseServer.getClient().create(TmdbApi.class);
        this.cacheService = TmdbCacheService.getInstance();
    }

    public void getCategories(DataSourceCallback<List<Category>> callback) {
        // Return just 2 categories: Films and TV Series
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("films", "أفلام", new ArrayList<>())); // Films - Arabic
        categories.add(new Category("tv", "مسلسلات", new ArrayList<>())); // TV Series - Arabic

        callback.onSuccess(categories);
    }

    public void getMoviesByCategory(String categoryId, DataSourceCallback<List<Movie>> callback) {
        // Try cache first for main categories
        if ("films".equals(categoryId) || "tv".equals(categoryId) ||
                "trending".equals(categoryId) || "popular".equals(categoryId)) {
            String cacheKey = "category_" + categoryId;

            cacheService.getSearchResults(cacheKey, new TmdbCacheService.CacheCallback<Map<String, Object>>() {
                @Override
                public void onCacheHit(Map<String, Object> data) {
                    Log.d(TAG, "📦 CACHE HIT for category: " + categoryId);
                    try {
                        Object results = data.get("results");
                        if (results instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> movieList = (List<Map<String, Object>>) results;
                            List<Movie> movies = parseMoviesFromCache(movieList, "tv".equals(categoryId));
                            callback.onSuccess(movies);
                            return;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Cache parse error, fetching fresh: " + e.getMessage());
                    }
                    fetchMoviesFromApi(categoryId, callback);
                }

                @Override
                public void onCacheMiss() {
                    Log.d(TAG, "🌐 CACHE MISS for category: " + categoryId + " - fetching from API");
                    fetchMoviesFromApi(categoryId, callback);
                }
            });
        } else {
            fetchMoviesFromApi(categoryId, callback);
        }
    }

    private void fetchMoviesFromApi(String categoryId, DataSourceCallback<List<Movie>> callback) {
        Call<TmdbMovieResponse> call;
        boolean isTvShow = "tv".equals(categoryId);

        if ("films".equals(categoryId) || "trending".equals(categoryId)) {
            call = api.getTrendingMovies();
        } else if ("tv".equals(categoryId)) {
            call = api.getTrendingTVSeries();
        } else if ("popular".equals(categoryId)) {
            call = api.getPopularMovies();
        } else {
            call = api.getMoviesByGenre(categoryId);
        }

        call.enqueue(new Callback<TmdbMovieResponse>() {
            @Override
            public void onResponse(Call<TmdbMovieResponse> call, Response<TmdbMovieResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Movie> movies = TmdbMapper.mapToMovies(response.body().getResults(), isTvShow);

                    // Cache trending/popular results
                    if ("trending".equals(categoryId) || "popular".equals(categoryId) ||
                            "films".equals(categoryId) || "tv".equals(categoryId)) {
                        cacheMovieResults(categoryId, response.body());
                    }

                    callback.onSuccess(movies);
                } else {
                    callback.onError(new Exception("Failed to fetch movies: " + response.message()));
                }
            }

            @Override
            public void onFailure(Call<TmdbMovieResponse> call, Throwable t) {
                callback.onError(t);
            }
        });
    }

    private void cacheMovieResults(String categoryId, TmdbMovieResponse response) {
        try {
            List<Map<String, Object>> movieMaps = new ArrayList<>();
            for (TmdbMovie movie : response.getResults()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", movie.getId());
                map.put("title", movie.getTitle());
                map.put("original_title", movie.getOriginalTitle());
                map.put("poster_path", movie.getPosterPath());
                map.put("backdrop_path", movie.getBackdropPath());
                map.put("overview", movie.getOverview());
                map.put("vote_average", movie.getVoteAverage());
                map.put("release_date", movie.getReleaseDate());
                map.put("first_air_date", movie.getFirstAirDate());
                map.put("genre_ids", movie.getGenreIds());
                movieMaps.add(map);
            }

            String cacheKey = "category_" + categoryId;
            cacheService.cacheSearchResults(cacheKey, movieMaps);
            Log.d(TAG, "💾 Cached " + movieMaps.size() + " movies for: " + categoryId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache: " + e.getMessage());
        }
    }

    private List<Movie> parseMoviesFromCache(List<Map<String, Object>> movieList, boolean isTvShow) {
        List<Movie> movies = new ArrayList<>();
        for (Map<String, Object> map : movieList) {
            Number id = (Number) map.get("id");
            String movieId = id != null ? String.valueOf(id.intValue()) : "0";
            String title = (String) map.get("title");
            String originalTitle = (String) map.get("original_title");
            String description = (String) map.get("overview");

            String posterPath = (String) map.get("poster_path");
            String posterUrl = posterPath != null ? "https://image.tmdb.org/t/p/w500" + posterPath : null;

            String backdropPath = (String) map.get("backdrop_path");
            String backdropUrl = backdropPath != null ? "https://image.tmdb.org/t/p/original" + backdropPath : null;

            Number ratingNum = (Number) map.get("vote_average");
            String rating = ratingNum != null ? String.format("%.1f", ratingNum.doubleValue()) : "";

            // Parse Year from Cache
            String dateStr = isTvShow ? (String) map.get("first_air_date") : (String) map.get("release_date");
            String year = "";
            if (dateStr != null && dateStr.length() >= 4) {
                year = dateStr.substring(0, 4);
            }

            // Parse Genres from Cache
            List<String> categories = new ArrayList<>();
            Object genreIdsObj = map.get("genre_ids");
            if (genreIdsObj instanceof List) {
                List<Double> genreIds = (List<Double>) genreIdsObj; // Gson often decodes numbers as Double
                for (Number gIdNum : genreIds) {
                    int gId = gIdNum.intValue();
                    String gName = TmdbMapper.GENRE_MAP.get(gId);
                    if (gName != null) {
                        categories.add(gName);
                    }
                }
            }

            Movie movie = new Movie(movieId, title, originalTitle, description, backdropUrl, posterUrl,
                    null, null, year, rating, MovieActionType.EXOPLAYER, isTvShow, categories, "TMDB");
            movies.add(movie);
        }
        return movies;
    }

    /**
     * Fetch trailer URL using isTvShow flag to select correct endpoint
     */
    public void getMovieTrailer(int movieId, boolean isTvShow, DataSourceCallback<String> callback) {
        Call<TmdbVideoResponse> call;
        if (isTvShow) {
            call = api.getTvVideos(movieId, "en-US");
        } else {
            call = api.getMovieVideos(movieId, "en-US");
        }

        call.enqueue(new Callback<TmdbVideoResponse>() {
            @Override
            public void onResponse(Call<TmdbVideoResponse> call, Response<TmdbVideoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String trailerUrl = findBestTrailer(response.body().getResults());
                    callback.onSuccess(trailerUrl);
                } else {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onFailure(Call<TmdbVideoResponse> call, Throwable t) {
                callback.onSuccess(null);
            }
        });
    }

    /**
     * Legacy method for backward compatibility - assumes Movie
     */
    public void getMovieTrailer(int movieId, DataSourceCallback<String> callback) {
        getMovieTrailer(movieId, false, callback);
    }

    private String findBestTrailer(List<TmdbVideo> videos) {
        if (videos == null || videos.isEmpty()) {
            return null;
        }

        TmdbVideo best = null;
        for (TmdbVideo video : videos) {
            if (!"YouTube".equalsIgnoreCase(video.getSite())) {
                continue;
            }

            if (video.isOfficial() && "Trailer".equalsIgnoreCase(video.getType())) {
                best = video;
                break;
            } else if ("Trailer".equalsIgnoreCase(video.getType()) && best == null) {
                best = video;
            } else if ("Teaser".equalsIgnoreCase(video.getType()) && best == null) {
                best = video;
            } else if (best == null) {
                best = video;
            }
        }

        return best != null ? best.getVideoUrl() : null;
    }
}
