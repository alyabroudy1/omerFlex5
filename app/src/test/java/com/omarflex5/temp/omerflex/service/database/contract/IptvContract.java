package com.omarflex5.temp.omerflex.service.database.contract;

import android.provider.BaseColumns;

public final class IptvContract {

    private IptvContract(){}
/*
    public static class CategoriesTable implements BaseColumns {
        public static final String TABLE_NAME = "quiz_categories";
        public static final String COLUMN_NAME = "name";
    }

 */

    public static class IptvTable implements BaseColumns {
        public static final String TABLE_NAME = "iptv_playlist";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_URL = "url";
        public static final String COLUMN_HASH = "hash";
    }

}
