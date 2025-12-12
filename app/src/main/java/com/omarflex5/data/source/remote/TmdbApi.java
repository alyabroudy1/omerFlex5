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

        // ========== Films ==========
        @GET("trending/movie/week")
        Call<TmdbMovieResponse> getTrendingMovies();

        @GET("movie/popular")
        Call<TmdbMovieResponse> getPopularMovies();

        @GET("discover/movie")
        Call<TmdbMovieResponse> getMoviesByGenre(@Query("with_genres") String genreId);

        @GET("discover/movie")
        Call<TmdbMovieResponse> discoverMoviesByDateRange(
                        @Query("primary_release_date.gte") String minDate,
                        @Query("primary_release_date.lte") String maxDate,
                        @Query("sort_by") String sortBy,
                        @Query("vote_count.gte") int minVotes);

        @GET("genre/movie/list")
        Call<TmdbGenreResponse> getGenres();

        @GET("movie/{movie_id}/videos")
        Call<TmdbVideoResponse> getMovieVideos(
                        @Path("movie_id") int movieId,
                        @Query("language") String language);

        // ========== TV Series ==========
        @GET("trending/tv/week")
        Call<TmdbMovieResponse> getTrendingTVSeries();

        @GET("discover/tv")
        Call<TmdbMovieResponse> discoverTvByDateRange(
                        @Query("first_air_date.gte") String minDate,
                        @Query("first_air_date.lte") String maxDate,
                        @Query("sort_by") String sortBy,
                        @Query("vote_count.gte") int minVotes);

        @GET("tv/popular")
        Call<TmdbMovieResponse> getPopularTVSeries();

        @GET("tv/{tv_id}/videos")
        Call<TmdbVideoResponse> getTvVideos(
                        @Path("tv_id") int tvId,
                        @Query("language") String language);

        // ========== Details (For cache-first strategy) ==========
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
