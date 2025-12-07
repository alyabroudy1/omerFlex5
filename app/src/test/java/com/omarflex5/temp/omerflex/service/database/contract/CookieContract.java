package com.omarflex5.temp.omerflex.service.database.contract;

import android.provider.BaseColumns;

public final class CookieContract {

    private CookieContract(){}
/*
    public static class CategoriesTable implements BaseColumns {
        public static final String TABLE_NAME = "quiz_categories";
        public static final String COLUMN_NAME = "name";
    }

 */

    public static class CookieTable implements BaseColumns {
        public static final String TABLE_NAME = "cookies";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_REFERRER = "referrer";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_HEADER = "header";
        public static final String COLUMN_COOKIE = "cookie";
    }

}
