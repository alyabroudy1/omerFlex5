package com.omarflex5.util;

import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.local.entity.MediaType;

import java.util.Map;

public class TmdbMapper {

    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    // TMDB Genre ID to Name mapping
    private static final Map<Integer, String> GENRE_MAP = new java.util.HashMap<Integer, String>() {
        {
            // Movie genres
            put(28, "أكشن");
            put(12, "مغامرة");
            put(16, "رسوم متحركة");
            put(35, "كوميديا");
            put(80, "جريمة");
            put(99, "وثائقي");
            put(18, "دراما");
            put(10751, "عائلي");
            put(14, "فانتازيا");
            put(36, "تاريخي");
            put(27, "رعب");
            put(10402, "موسيقى");
            put(9648, "غموض");
            put(10749, "رومانسي");
            put(878, "خيال علمي");
            put(10770, "تلفاز");
            put(53, "إثارة");
            put(10752, "حرب");
            put(37, "غربي");
            // TV genres
            put(10759, "أكشن ومغامرة");
            put(10762, "أطفال");
            put(10763, "أخبار");
            put(10764, "واقعي");
            put(10765, "خيال علمي وفانتازيا");
            put(10766, "مسلسل درامي");
            put(10767, "حديث");
            put(10768, "حرب وسياسة");
        }
    };

    public static MediaEntity mapFromPojo(com.omarflex5.data.model.tmdb.TmdbMovie movie, MediaType type) {
        if (movie == null)
            return null;

        MediaEntity entity = new MediaEntity();
        entity.setTmdbId(movie.getId());
        entity.setType(type);
        entity.setTitle(movie.getTitle());
        entity.setOriginalTitle(movie.getOriginalTitle());
        entity.setDescription(movie.getOverview());

        if (movie.getPosterPath() != null) {
            entity.setPosterUrl(IMAGE_BASE_URL + movie.getPosterPath());
        }
        if (movie.getBackdropPath() != null) {
            entity.setBackdropUrl(IMAGE_BASE_URL + movie.getBackdropPath());
        }

        entity.setRating((float) movie.getVoteAverage());

        // Year
        String date = (type == MediaType.SERIES) ? movie.getFirstAirDate() : movie.getReleaseDate();
        entity.setReleaseDate(date);
        if (date != null && date.length() >= 4) {
            try {
                entity.setYear(Integer.parseInt(date.substring(0, 4)));
            } catch (NumberFormatException ignored) {
            }
        }

        // Map genre IDs to genre names
        if (movie.getGenreIds() != null && !movie.getGenreIds().isEmpty()) {
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (Integer genreId : movie.getGenreIds()) {
                String genreName = GENRE_MAP.get(genreId);
                if (genreName != null) {
                    jsonArray.put(genreName);
                }
            }
            if (jsonArray.length() > 0) {
                entity.setCategoriesJson(jsonArray.toString());
            }
        }

        // Store original language
        entity.setOriginalLanguage(movie.getOriginalLanguage());

        long now = System.currentTimeMillis();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return entity;
    }

    public static MediaEntity mapToEntity(Map<String, Object> data, MediaType type) {
        if (data == null)
            return null;

        try {
            MediaEntity entity = new MediaEntity();

            // IDs
            Object idObj = data.get("id");
            if (idObj instanceof Number) {
                entity.setTmdbId(((Number) idObj).intValue());
            } else {
                return null; // Invalid ID
            }

            entity.setImdbId((String) data.get("imdb_id"));

            // Type
            entity.setType(type);

            // Title
            if (type == MediaType.SERIES) {
                entity.setTitle((String) data.get("name"));
                entity.setOriginalTitle((String) data.get("original_name"));
                entity.setReleaseDate((String) data.get("first_air_date"));
            } else {
                entity.setTitle((String) data.get("title"));
                entity.setOriginalTitle((String) data.get("original_title"));
                entity.setReleaseDate((String) data.get("release_date"));
            }

            // Overview
            entity.setDescription((String) data.get("overview"));

            // Images
            String posterPath = (String) data.get("poster_path");
            if (posterPath != null) {
                entity.setPosterUrl(IMAGE_BASE_URL + posterPath);
            }

            String backdropPath = (String) data.get("backdrop_path");
            if (backdropPath != null) {
                entity.setBackdropUrl(IMAGE_BASE_URL + backdropPath);
            }

            // Rating
            Object voteAverage = data.get("vote_average");
            if (voteAverage instanceof Number) {
                entity.setRating(((Number) voteAverage).floatValue());
            }

            // Categories (Genres)
            Object genresObj = data.get("genres");
            if (genresObj instanceof java.util.List) {
                java.util.List<?> genresList = (java.util.List<?>) genresObj;
                org.json.JSONArray jsonArray = new org.json.JSONArray();
                for (Object item : genresList) {
                    if (item instanceof java.util.Map) {
                        java.util.Map<?, ?> genreMap = (java.util.Map<?, ?>) item;
                        Object name = genreMap.get("name");
                        if (name instanceof String) {
                            jsonArray.put(name);
                        }
                    }
                }
                entity.setCategoriesJson(jsonArray.toString());
            }

            // Timestamps
            long now = System.currentTimeMillis();
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);

            return entity;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
