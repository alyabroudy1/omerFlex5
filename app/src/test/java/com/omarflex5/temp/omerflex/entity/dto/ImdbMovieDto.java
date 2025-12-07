package com.omarflex5.temp.omerflex.entity.dto;

import com.omerflex.entity.Movie;

import java.util.ArrayList;

public class ImdbMovieDto {

    public int id;
    public String title;
    public String poster_path;
    public String overview;
    public String release_date;

    public boolean isAdult;
    public String backdrop_path;
    public ArrayList<Integer> genre_ids;
    public String original_language;
    public String original_title;
    public double popularity;
    public boolean video;
    public double vote_average;
    public int vote_count;

    @Override
    public String toString() {
        return "ImdbMovieDto{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", poster_path='" + poster_path + '\'' +
                ", overview='" + overview + '\'' +
                ", release_date='" + release_date + '\'' +
                ", isAdult=" + isAdult +
                ", backdrop_path='" + backdrop_path + '\'' +
                ", genre_ids=" + genre_ids +
                ", original_language='" + original_language + '\'' +
                ", original_title='" + original_title + '\'' +
                ", popularity=" + popularity +
                ", video=" + video +
                ", vote_average=" + vote_average +
                ", vote_count=" + vote_count +
                '}';
    }
}
