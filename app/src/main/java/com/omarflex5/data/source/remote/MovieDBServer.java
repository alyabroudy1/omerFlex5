package com.omarflex5.data.source.remote;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.model.tmdb.TmdbGenreResponse;
import com.omarflex5.data.model.tmdb.TmdbMapper;
import com.omarflex5.data.model.tmdb.TmdbMovieResponse;
import com.omarflex5.data.model.tmdb.TmdbVideo;
import com.omarflex5.data.model.tmdb.TmdbVideoResponse;
import com.omarflex5.data.source.DataSourceCallback;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MovieDBServer {

    private final TmdbApi api;

    public MovieDBServer() {
        this.api = BaseServer.getClient().create(TmdbApi.class);
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
                    callback.onSuccess(null); // No trailer found
                }
            }

            @Override
            public void onFailure(Call<TmdbVideoResponse> call, Throwable t) {
                callback.onSuccess(null); // Fallback to default
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
