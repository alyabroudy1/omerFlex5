package com.omarflex5.temp.omerflex.service.database.contract;

import android.provider.BaseColumns;

public final class MovieContract {

    private MovieContract(){}
/*
    public static class CategoriesTable implements BaseColumns {
        public static final String TABLE_NAME = "quiz_categories";
        public static final String COLUMN_NAME = "name";
    }

 */

    public static class MoviesTable implements BaseColumns {
        public static final String TABLE_NAME = "movie_movies";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_GROUP = "group_name";
        public static final String COLUMN_BACKGROUND_IMAGE_URL = "background_image_url";
        public static final String COLUMN_CARD_IMAGE_URL = "card_image_url";
        public static final String COLUMN_VIDEO_URL = "video_url";
        public static final String COLUMN_STUDIO = "studio";
        public static final String COLUMN_RATE = "rate";
        public static final String COLUMN_TRAILER_URL = "trailer_url";
        public static final String COLUMN_MAIN_MOVIE_TITLE = "main_movie_title";
        public static final String COLUMN_STATE = "state";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_SEARCH_CONTEXT = "search_context";
        public static final String COLUMN_IS_HISTORY = "is_history";
        public static final String COLUMN_UPDATED_AT = "updated_at";
        public static final String COLUMN_PLAYED_TIME = "played_time";
        public static final String COLUMN_MOVIE_HISTORY_ID = "movie_history_id";
    }

}
