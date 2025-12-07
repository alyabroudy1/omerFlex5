package com.omarflex5.temp.omerflex.entity.dto;

import com.omerflex.entity.Movie;

import java.util.ArrayList;

public class ImdbMovieResponse {

    private int page;
    private ArrayList<ImdbMovieDto> results;
    private int total_pages;
    private int total_results;

    public ArrayList<ImdbMovieDto> getResults() { return results; }
}
