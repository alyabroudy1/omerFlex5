package com.omarflex5.data.source.remote;

import com.omarflex5.data.model.tmdb.TmdbGenreResponse;
import com.omarflex5.data.model.tmdb.TmdbMovieResponse;
import com.omarflex5.data.model.tmdb.TmdbVideoResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TmdbApi {
    @GET("trending/movie/week")
    Call<TmdbMovieResponse> getTrendingMovies();

    @GET("movie/popular")
    Call<TmdbMovieResponse> getPopularMovies();

    @GET("discover/movie")
    Call<TmdbMovieResponse> getMoviesByGenre(@Query("with_genres") String genreId);

    @GET("genre/movie/list")
    Call<TmdbGenreResponse> getGenres();

    @GET("movie/{movie_id}/videos")
    Call<TmdbVideoResponse> getMovieVideos(
            @Path("movie_id") int movieId,
            @Query("language") String language);

    // ========== NEW: For cache-first strategy ==========

    @GET("movie/{movie_id}")
    Call<Map<String, Object>> getMovieDetails(@Path("movie_id") int movieId);

    @GET("tv/{tv_id}")
    Call<Map<String, Object>> getTvShowDetails(@Path("tv_id") int tvId);

    @GET("search/multi")
    Call<Map<String, Object>> searchMulti(@Query("query") String query);

    @GET("tv/{tv_id}/season/{season_number}")
    Call<Map<String, Object>> getTvSeasonDetails(
            @Path("tv_id") int tvId,
            @Path("season_number") int seasonNumber);
}
