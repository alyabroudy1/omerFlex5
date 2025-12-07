package com.omarflex5.temp.omerflex.entity;

public class MovieFetchProcess {


    public final static int FETCH_PROCESS_SUCCESS = 0;
    public final static int FETCH_PROCESS_COOKE_REQUIRE = 2;
    public final static int FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE = 3;
    public final static int FETCH_PROCESS_DETAILS_ACTIVITY_REQUIRE = 4;
    public final static int FETCH_PROCESS_ERROR_UNKNOWN = 5;
    public static final int FETCH_PROCESS_EXOPLAYER = 6;
    public static final int FETCH_PROCESS_UPDATE_CONFIG_AND_RETURN_RESULT = 7;
    public static final int FETCH_PROCESS_RETURN_RESULT = 8;

    public int stateCode;
    public Movie movie;

    public MovieFetchProcess(int stateCode, Movie movie){
        this.stateCode = stateCode;
        this.movie = movie;
    }

}
