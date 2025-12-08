package com.omarflex5.data.model.tmdb;

import com.omarflex5.data.model.Category;
import com.omarflex5.data.model.Movie;
import com.omarflex5.data.model.MovieActionType;

import java.util.ArrayList;
import java.util.List;

public class TmdbMapper {
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w780";
    private static final String BACKDROP_BASE_URL = "https://image.tmdb.org/t/p/w1280";

    public static final java.util.Map<Integer, String> GENRE_MAP = new java.util.HashMap<>();
    static {
        GENRE_MAP.put(28, "Action");
        GENRE_MAP.put(12, "Adventure");
        GENRE_MAP.put(16, "Animation");
        GENRE_MAP.put(35, "Comedy");
        GENRE_MAP.put(80, "Crime");
        GENRE_MAP.put(99, "Documentary");
        GENRE_MAP.put(18, "Drama");
        GENRE_MAP.put(10751, "Family");
        GENRE_MAP.put(14, "Fantasy");
        GENRE_MAP.put(36, "History");
        GENRE_MAP.put(27, "Horror");
        GENRE_MAP.put(10402, "Music");
        GENRE_MAP.put(9648, "Mystery");
        GENRE_MAP.put(10749, "Romance");
        GENRE_MAP.put(878, "Sci-Fi");
        GENRE_MAP.put(10770, "TV Movie");
        GENRE_MAP.put(53, "Thriller");
        GENRE_MAP.put(10752, "War");
        GENRE_MAP.put(37, "Western");
        GENRE_MAP.put(10759, "Action & Adventure");
        GENRE_MAP.put(10762, "Kids");
        GENRE_MAP.put(10763, "News");
        GENRE_MAP.put(10764, "Reality");
        GENRE_MAP.put(10765, "Sci-Fi & Fantasy");
        GENRE_MAP.put(10766, "Soap");
        GENRE_MAP.put(10767, "Talk");
        GENRE_MAP.put(10768, "War & Politics");
    }

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

        // Parse Year
        String dateStr = isTvShow ? tmdbMovie.getFirstAirDate() : tmdbMovie.getReleaseDate();
        String year = "";
        if (dateStr != null && dateStr.length() >= 4) {
            year = dateStr.substring(0, 4);
        }

        String rating = String.format("%.1f", tmdbMovie.getVoteAverage());

        // Map Genres
        List<String> categories = new ArrayList<>();
        if (tmdbMovie.getGenreIds() != null) {
            for (Integer gId : tmdbMovie.getGenreIds()) {
                String gName = GENRE_MAP.get(gId);
                if (gName != null) {
                    categories.add(gName);
                }
            }
        }

        android.util.Log.e("TmdbMapper",
                "DEBUG_DATA: " + title + " | YearRaw: "
                        + (isTvShow ? tmdbMovie.getFirstAirDate() : tmdbMovie.getReleaseDate()) + " ->Parsed: " + year
                        + " | Genres: " + tmdbMovie.getGenreIds() + " -> " + categories);

        return new Movie(id, title, originalTitle, description, backgroundUrl, posterUrl,
                trailerUrl, trailerUrl, year, rating, MovieActionType.EXOPLAYER, isTvShow, categories, "TMDB");
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
