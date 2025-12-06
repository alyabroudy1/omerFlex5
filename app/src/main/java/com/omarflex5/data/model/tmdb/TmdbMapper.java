package com.omarflex5.data.model.tmdb;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.model.MovieActionType;

import java.util.ArrayList;
import java.util.List;

public class TmdbMapper {
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w780";
    private static final String BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w1280";

    public static Movie mapToMovie(TmdbMovie tmdbMovie, boolean isTvShow) {
        return mapToMovie(tmdbMovie, null, isTvShow);
    }

    public static Movie mapToMovie(TmdbMovie tmdbMovie) {
        return mapToMovie(tmdbMovie, null, false);
    }

    public static Movie mapToMovie(TmdbMovie tmdbMovie, String trailerUrl, boolean isTvShow) {
        String id = String.valueOf(tmdbMovie.getId());
        String title = tmdbMovie.getTitle();
        String originalTitle = tmdbMovie.getOriginalTitle();
        String description = tmdbMovie.getOverview();
        if (description == null || description.isEmpty()) {
            description = "لا يتوفر وصف لهذا المحتوى حالياً.";
        }
        String posterUrl = IMAGE_BASE_URL + tmdbMovie.getPosterPath();
        String backgroundUrl = BACKDROP_BASE_URL + tmdbMovie.getBackdropPath();

        // Use provided trailer URL or fallback to dummy
        if (trailerUrl == null || trailerUrl.isEmpty()) {
            trailerUrl = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
        }
        String year = ""; // TMDB release date parsing omitted for brevity
        String rating = String.valueOf(tmdbMovie.getVoteAverage());

        return new Movie(id, title, originalTitle, description, backgroundUrl, posterUrl,
                trailerUrl, trailerUrl, year, rating, MovieActionType.EXOPLAYER, isTvShow);
    }

    public static List<Movie> mapToMovies(List<TmdbMovie> tmdbMovies) {
        return mapToMovies(tmdbMovies, false);
    }

    public static List<Movie> mapToMovies(List<TmdbMovie> tmdbMovies, boolean isTvShow) {
        List<Movie> movies = new ArrayList<>();
        if (tmdbMovies != null) {
            for (TmdbMovie tmdbMovie : tmdbMovies) {
                // Filter out movies without images
                if (tmdbMovie.getPosterPath() != null && tmdbMovie.getBackdropPath() != null) {
                    movies.add(mapToMovie(tmdbMovie, isTvShow));
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
