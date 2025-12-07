package com.omarflex5.temp.omerflex.service.database.contract;

import android.provider.BaseColumns;

public final class ServerConfigContract {

    private ServerConfigContract(){}
/*
    public static class CategoriesTable implements BaseColumns {
        public static final String TABLE_NAME = "quiz_categories";
        public static final String COLUMN_NAME = "name";
    }

 */

    public static class ConfigTable implements BaseColumns {
        public static final String TABLE_NAME = "configs";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_LABEL = "label";
        public static final String COLUMN_IS_ACTIVE = "is_active";
        public static final String COLUMN_URL = "url";
        public static final String COLUMN_REFERER = "referer";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_HEADER = "header";
        public static final String COLUMN_COOKIE = "cookie";
    }

}
