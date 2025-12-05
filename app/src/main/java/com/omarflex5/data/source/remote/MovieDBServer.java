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
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("trending", "Trending Now", new ArrayList<>()));
        categories.add(new Category("popular", "Popular", new ArrayList<>()));

        api.getGenres().enqueue(new Callback<TmdbGenreResponse>() {
            @Override
            public void onResponse(Call<TmdbGenreResponse> call, Response<TmdbGenreResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    categories.addAll(TmdbMapper.mapToCategories(response.body().getGenres()));
                    callback.onSuccess(categories);
                } else {
                    callback.onError(new Exception("Failed to fetch genres: " + response.message()));
                }
            }

            @Override
            public void onFailure(Call<TmdbGenreResponse> call, Throwable t) {
                if (!categories.isEmpty()) {
                    callback.onSuccess(categories);
                } else {
                    callback.onError(t);
                }
            }
        });
    }

    public void getMoviesByCategory(String categoryId, DataSourceCallback<List<Movie>> callback) {
        // Try cache first for trending/popular (most common)
        if ("trending".equals(categoryId) || "popular".equals(categoryId)) {
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
                            List<Movie> movies = parseMoviesFromCache(movieList);
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

        if ("trending".equals(categoryId)) {
            call = api.getTrendingMovies();
        } else if ("popular".equals(categoryId)) {
            call = api.getPopularMovies();
        } else {
            call = api.getMoviesByGenre(categoryId);
        }

        call.enqueue(new Callback<TmdbMovieResponse>() {
            @Override
            public void onResponse(Call<TmdbMovieResponse> call, Response<TmdbMovieResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Movie> movies = TmdbMapper.mapToMovies(response.body().getResults());

                    // Cache trending/popular results
                    if ("trending".equals(categoryId) || "popular".equals(categoryId)) {
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
                map.put("poster_path", movie.getPosterPath());
                map.put("backdrop_path", movie.getBackdropPath());
                map.put("overview", movie.getOverview());
                map.put("vote_average", movie.getVoteAverage());
                movieMaps.add(map);
            }

            String cacheKey = "category_" + categoryId;
            cacheService.cacheSearchResults(cacheKey, movieMaps);
            Log.d(TAG, "💾 Cached " + movieMaps.size() + " movies for: " + categoryId);
        } catch (Exception e) {
            Log.w(TAG, "Failed to cache: " + e.getMessage());
        }
    }

    private List<Movie> parseMoviesFromCache(List<Map<String, Object>> movieList) {
        List<Movie> movies = new ArrayList<>();
        for (Map<String, Object> map : movieList) {
            Number id = (Number) map.get("id");
            String movieId = id != null ? String.valueOf(id.intValue()) : "0";
            String title = (String) map.get("title");
            String description = (String) map.get("overview");

            String posterPath = (String) map.get("poster_path");
            String posterUrl = posterPath != null ? "https://image.tmdb.org/t/p/w500" + posterPath : null;

            String backdropPath = (String) map.get("backdrop_path");
            String backdropUrl = backdropPath != null ? "https://image.tmdb.org/t/p/original" + backdropPath : null;

            Number ratingNum = (Number) map.get("vote_average");
            String rating = ratingNum != null ? String.format("%.1f", ratingNum.doubleValue()) : "";

            Movie movie = new Movie(movieId, title, description, backdropUrl, posterUrl,
                    null, null, "", rating, MovieActionType.EXOPLAYER);
            movies.add(movie);
        }
        return movies;
    }

    /**
     * Fetch trailer URL for a specific movie (called on selection)
     * Note: We use en-US for videos since Arabic trailers are rarely available
     */
    public void getMovieTrailer(int movieId, DataSourceCallback<String> callback) {
        api.getMovieVideos(movieId, "en-US").enqueue(new Callback<TmdbVideoResponse>() {
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
