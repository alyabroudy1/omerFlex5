package com.omarflex5.data.model.tmdb;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;

import java.util.ArrayList;
import java.util.List;

public class TmdbMapper {
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w780";
    private static final String BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w1280";

    public static Movie mapToMovie(TmdbMovie tmdbMovie) {
        String id = String.valueOf(tmdbMovie.getId());
        String title = tmdbMovie.getTitle();
        String description = tmdbMovie.getOverview();
        String posterUrl = IMAGE_BASE_URL + tmdbMovie.getPosterPath();
        String backgroundUrl = BACKDROP_BASE_URL + tmdbMovie.getBackdropPath();
        // For now, use a dummy trailer since the main list endpoint doesn't return
        // videos
        String trailerUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        String year = ""; // TMDB release date parsing omitted for brevity
        String rating = String.valueOf(tmdbMovie.getVoteAverage());

        return new Movie(id, title, description, backgroundUrl, posterUrl, trailerUrl, year, rating);
    }

    public static List<Movie> mapToMovies(List<TmdbMovie> tmdbMovies) {
        List<Movie> movies = new ArrayList<>();
        if (tmdbMovies != null) {
            for (TmdbMovie tmdbMovie : tmdbMovies) {
                // Filter out movies without images
                if (tmdbMovie.getPosterPath() != null && tmdbMovie.getBackdropPath() != null) {
                    movies.add(mapToMovie(tmdbMovie));
                }
            }
        }
        return movies;
    }

    public static Category mapToCategory(TmdbGenre tmdbGenre) {
        String id = String.valueOf(tmdbGenre.getId());
        String name = tmdbGenre.getName();
        return new Category(id, name, new ArrayList<>());
    }

    public static List<Category> mapToCategories(List<TmdbGenre> tmdbGenres) {
        List<Category> categories = new ArrayList<>();
        if (tmdbGenres != null) {
            for (TmdbGenre tmdbGenre : tmdbGenres) {
                categories.add(mapToCategory(tmdbGenre));
            }
        }
        return categories;
    }
}
