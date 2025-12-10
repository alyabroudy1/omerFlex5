package com.omarflex5.util;

import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.local.entity.MediaType;

import java.util.Map;

public class TmdbMapper {

    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

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
