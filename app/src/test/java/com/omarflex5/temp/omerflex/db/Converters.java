package com.omarflex5.temp.omerflex.db;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieHistory;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Converters {
    private static Gson gson = new Gson();

    @TypeConverter
    public static List<String> fromStringList(String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        Type listType = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(value, listType);
    }

    @TypeConverter
    public static String fromStringList(List<String> list) {
        return gson.toJson(list);
    }

    @TypeConverter
    public static Movie fromStringToMovie(String value) {
        return gson.fromJson(value, Movie.class);
    }

    @TypeConverter
    public static String fromMovieToString(Movie movie) {
        return gson.toJson(movie);
    }

    @TypeConverter
    public static MovieHistory fromStringToMovieHistory(String value) {
        return gson.fromJson(value, MovieHistory.class);
    }

    @TypeConverter
    public static String fromMovieHistoryToString(MovieHistory movieHistory) {
        return gson.toJson(movieHistory);
    }

    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }
}
