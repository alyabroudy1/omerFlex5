package com.omarflex5.temp.omerflex.service.database.contract;

import android.provider.BaseColumns;

public final class MovieHistoryContract {

    private MovieHistoryContract(){}
/*
    public static class CategoriesTable implements BaseColumns {
        public static final String TABLE_NAME = "quiz_categories";
        public static final String COLUMN_NAME = "name";
    }

 */

    public static class MovieHistoryTable implements BaseColumns {
        public static final String TABLE_NAME = "movie_movie_history";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_MAIN_MOVIE_URL = "main_movie_url";
        public static final String COLUMN_SEASON = "season";
        public static final String COLUMN_EPISODE = "episode";
        public static final String COLUMN_PLAYED_TIME = "played_time";
        public static final String COLUMN_PLAYED_AT = "played_at";
    }

}
