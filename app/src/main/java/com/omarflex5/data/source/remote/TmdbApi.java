package com.omarflex5.data.source.remote;

import com.omarflex5.data.model.tmdb.TmdbGenreResponse;
import com.omarflex5.data.model.tmdb.TmdbMovieResponse;
import com.omarflex5.data.model.tmdb.TmdbVideoResponse;

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
}
