package com.omarflex5.temp.omerflex.service.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieHistory;
import com.omerflex.entity.MovieRepository;
import com.omerflex.entity.ServerConfig;
import com.omerflex.entity.dto.CookieDTO;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.database.contract.CookieContract.CookieTable;
import com.omerflex.service.database.contract.IptvContract.IptvTable;
import com.omerflex.service.database.contract.MovieContract.MoviesTable;
import com.omerflex.service.database.contract.MovieHistoryContract.MovieHistoryTable;
import com.omerflex.service.database.contract.ServerConfigContract.ConfigTable;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MovieDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "MoviesHistory.db";
    static String TAG = "MovieDbHelper";
    private static final int DATABASE_VERSION = 5; // should be increased if changes applied to database structure or reinstall the app

    //to make this class singleton which means to be created only one time in the app
    private static MovieDbHelper instance;

    //create instance of Sqlite database object
    private SQLiteDatabase db;

    private MovieDbHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    //to make this class singleton which means to be created only one time in the app
    public static synchronized MovieDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new MovieDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        this.db = db;



        final String SQL_CREATE_COOKIE_TABLE = "CREATE TABLE " + CookieTable.TABLE_NAME + "("
                + CookieTable.COLUMN_ID + " TEXT PRIMARY KEY, "
                + CookieTable.COLUMN_REFERRER + " TEXT, "
                + CookieTable.COLUMN_CREATED_AT + " INTEGER, "
                + CookieTable.COLUMN_HEADER + " TEXT, "
                + CookieTable.COLUMN_COOKIE + " TEXT"
                + ")";






        createServerConfigTable(db);
        createMoviesTable(db);
        createMovieHistoryTable(db);
        createIptvTable(db);


//        db.execSQL(SQL_CREATE_COOKIE_TABLE);



    }

    private void createIptvTable(SQLiteDatabase db) {
        final String SQL_CREATE_IPTV_TABLE = "CREATE TABLE " + IptvTable.TABLE_NAME + "("
                + IptvTable.COLUMN_ID + " TEXT, "
                + IptvTable.COLUMN_URL + " TEXT, "
                + IptvTable.COLUMN_HASH + " TEXT PRIMARY KEY"
                + ")";
        db.execSQL(SQL_CREATE_IPTV_TABLE);
    }

    private void createServerConfigTable(SQLiteDatabase db) {
        final String SQL_CREATE_SERVER_CONFIGS_TABLE = "CREATE TABLE " + ConfigTable.TABLE_NAME + "("
                + ConfigTable.COLUMN_ID + " TEXT PRIMARY KEY, "
                + ConfigTable.COLUMN_NAME + " TEXT, "
                + ConfigTable.COLUMN_LABEL + " TEXT, "
                + ConfigTable.COLUMN_IS_ACTIVE + " INTEGER, "
                + ConfigTable.COLUMN_URL + " TEXT, "
                + ConfigTable.COLUMN_REFERER + " TEXT, "
                + ConfigTable.COLUMN_CREATED_AT + " INTEGER, "
                + ConfigTable.COLUMN_HEADER + " TEXT, "
                + ConfigTable.COLUMN_COOKIE + " TEXT"
                + ")";

        db.execSQL(SQL_CREATE_SERVER_CONFIGS_TABLE);
    }

    private void createMoviesTable(SQLiteDatabase db) {
        final String SQL_CREATE_MOVIES_TABLE = "CREATE TABLE " +
                MoviesTable.TABLE_NAME + " ( " +
                MoviesTable._ID + " INTEGER, " +
                MoviesTable.COLUMN_TITLE + " TEXT, " +
                MoviesTable.COLUMN_SEARCH_CONTEXT + " TEXT, " +
                MoviesTable.COLUMN_GROUP + " TEXT, " +
                MoviesTable.COLUMN_MAIN_MOVIE_TITLE + " TEXT, " +
                MoviesTable.COLUMN_STUDIO + " TEXT, " +
                MoviesTable.COLUMN_STATE + " INTEGER, " +
                MoviesTable.COLUMN_DESCRIPTION + " TEXT, " +
                MoviesTable.COLUMN_BACKGROUND_IMAGE_URL + " TEXT, " +
                MoviesTable.COLUMN_CARD_IMAGE_URL + " TEXT, " +
                MoviesTable.COLUMN_VIDEO_URL + " TEXT PRIMARY KEY, " +
                MoviesTable.COLUMN_RATE + " TEXT, " +
                MoviesTable.COLUMN_TRAILER_URL + " TEXT, " +
                MoviesTable.COLUMN_CREATED_AT + " DATETIME, " +
                MoviesTable.COLUMN_IS_HISTORY + " INTEGER, " +
                MoviesTable.COLUMN_PLAYED_TIME + " INTEGER, " +
                MoviesTable.COLUMN_MOVIE_HISTORY_ID + " INTEGER, " +
                MoviesTable.COLUMN_UPDATED_AT + " INTEGER " +
                //  MoviesTable.COLUMN_STATE + " INTEGER, "+
                //  "FOREIGN KEY (" + MoviesTable.COLUMN_CATEGORY_ID + " ) REFERENCES "+
                //   CategoriesTable.TABLE_NAME + "(" + CategoriesTable._ID + ")" + "ON DELETE CASCADE" +
                ")";
        db.execSQL(SQL_CREATE_MOVIES_TABLE);
    }

    private void createMovieHistoryTable(SQLiteDatabase db) {
        final String SQL_CREATE_MOVIE_HISTORY_TABLE = "CREATE TABLE " + MovieHistoryTable.TABLE_NAME + "("
                + MovieHistoryTable.COLUMN_ID + " INTEGER, "
                + MovieHistoryTable.COLUMN_MAIN_MOVIE_URL + " TEXT PRIMARY KEY, "
                + MovieHistoryTable.COLUMN_SEASON + " TEXT, "
                + MovieHistoryTable.COLUMN_EPISODE + " TEXT, "
                + MovieHistoryTable.COLUMN_PLAYED_AT + " INTEGER,"
                + MovieHistoryTable.COLUMN_PLAYED_TIME + " INTEGER"
                + ")";
        db.execSQL(SQL_CREATE_MOVIE_HISTORY_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "onUpgrade: old:"+oldVersion+ ", new: "+ newVersion);
        if (newVersion > oldVersion) {
//            db.execSQL("DROP TABLE IF EXISTS " + MoviesTable.TABLE_NAME);
//            db.execSQL("DROP TABLE IF EXISTS " + CookieTable.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + ConfigTable.TABLE_NAME);
//            db.execSQL("DROP TABLE IF EXISTS " + IptvTable.TABLE_NAME);
//            db.execSQL("DROP TABLE IF EXISTS " + MovieHistoryTable.TABLE_NAME);
//            onCreate(db);
            createServerConfigTable(db);
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        //enable category foreign key
        db.setForeignKeyConstraintsEnabled(true);
    }

    // In your SQLiteOpenHelper class or database manager
    public void clearTable(String tableName) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + tableName);
        db.close();
    }

//    public void saveMovieHistory(MovieHistory history) {
//        ContentValues values = new ContentValues();
//
//
//        SQLiteDatabase db = this.getReadableDatabase();
//
//        Log.d(TAG, "saveMovieHistory: " + history);
//
//        if (history.getMainMovieUrl() == null) {
//            Log.d(TAG, "saveMovieHistory: fail history.getMainMovieUrl() == null");
//            return;
//        }
//        try (Cursor cursor = db.query(
//                MovieHistoryTable.TABLE_NAME, null,
//                MovieHistoryTable.COLUMN_MAIN_MOVIE_URL + " = ?", new String[]{history.getMainMovieUrl()}, null, null, null)) {
//
//            Log.d(TAG, "saveMovieHistory: cursor:" + cursor.getCount());
//            if (history.getEpisode() != null) {
//                values.put(MovieHistoryTable.COLUMN_EPISODE, history.getEpisode());
//            }
//
//            if (history.getSeason() != null) {
//                values.put(MovieHistoryTable.COLUMN_SEASON, history.getSeason());
//            }
//
//            values.put(MovieHistoryTable.COLUMN_PLAYED_TIME, history.getPlayedTime());
//            values.put(MovieHistoryTable.COLUMN_PLAYED_AT, history.getPlayedAt().getTime());
//            db = this.getWritableDatabase();
//            if (cursor.getCount() > 0) {
//                // Server entry already exists, update the headers and cookies
//
//                db.update(MovieHistoryTable.TABLE_NAME, values, MovieHistoryTable.COLUMN_MAIN_MOVIE_URL + " = ?", new String[]{history.getMainMovieUrl()});
//            } else {
//                values.put(MovieHistoryTable.COLUMN_MAIN_MOVIE_URL, history.getMainMovieUrl());
//
//                // Server entry does not exist, insert a new row
//                db.insert(MovieHistoryTable.TABLE_NAME, null, values);
//            }
//        }
//    }

    public void saveHeadersAndCookies(AbstractServer server, String serverName) {
        //.getHeaders(), server.getCookies(), movie.getStudio()
        ServerConfig config = ServerConfigRepository.getInstance().getConfig(server.getServerId());
        if (config == null) {
            return;
        }

        Map<String, String> headers = config.getHeaders();
        String cookies = config.getStringCookies();

        SQLiteDatabase db = this.getWritableDatabase();
//
        if (headers.containsKey("Referer")) {
            String referer = headers.get("Referer");
            if (referer != null) {
                headers.put("Referer", Util.extractDomain(referer, true, true));
            }
        }
//        else if (headers.containsKey("Origin")){
//            server.setReferer(headers.get("Origin"));
//        }
        Log.d(TAG, "saveHeadersAndCookies: " + config.getReferer());
        // Check if the server entry exists in the database
        Cursor cursor = db.query(CookieTable.TABLE_NAME, null, CookieTable.COLUMN_ID + " = ?", new String[]{serverName}, null, null, null);

        if (cursor.getCount() > 0) {
            // Server entry already exists, update the headers and cookies
            ContentValues values = new ContentValues();
            values.put(CookieTable.COLUMN_REFERRER, config.getReferer());
            values.put(CookieTable.COLUMN_HEADER, headers.toString());
            values.put(CookieTable.COLUMN_CREATED_AT, new Date().getTime());
            values.put(CookieTable.COLUMN_COOKIE, cookies);
            db.update(CookieTable.TABLE_NAME, values, CookieTable.COLUMN_ID + " = ?", new String[]{serverName});
        } else {
            // Server entry does not exist, insert a new row
            ContentValues values = new ContentValues();
            values.put(CookieTable.COLUMN_ID, serverName);
            values.put(CookieTable.COLUMN_REFERRER, config.getReferer());
            values.put(CookieTable.COLUMN_HEADER, headers.toString());
            values.put(CookieTable.COLUMN_COOKIE, cookies);
            db.insert(CookieTable.TABLE_NAME, null, values);
        }
        //cursor.close();
        //db.close();
    }


//    public MovieHistory getMovieHistoryByMainMovie(String mainMovieUrl) {
//        SQLiteDatabase db = this.getReadableDatabase();
//        String selection = MovieHistoryTable.COLUMN_MAIN_MOVIE_URL + " LIKE '%" + mainMovieUrl + "%'";
//        Cursor cursor = null;
//        MovieHistory history = new MovieHistory();
//        try {
//            cursor = db.query(MovieHistoryTable.TABLE_NAME, null, selection, null, null, null, null);
//            if (cursor != null && cursor.moveToFirst()) {
//                int columnId = cursor.getColumnIndex(MovieHistoryTable.COLUMN_ID);
//                if (columnId >= 0) {
//                    history.setId(cursor.getInt(columnId));
//                }
//
//                int columnMainMovie = cursor.getColumnIndex(MovieHistoryTable.COLUMN_MAIN_MOVIE_URL);
//                if (columnMainMovie >= 0) {
//                    history.setMainMovieUrl(cursor.getString(columnMainMovie));
//                }
//
//                int columnPlayedMovie = cursor.getColumnIndex(MovieHistoryTable.COLUMN_EPISODE);
//                if (columnPlayedMovie >= 0) {
//                    history.setEpisode(cursor.getString(columnPlayedMovie));
//                }
//
//                int columnTitle = cursor.getColumnIndex(MovieHistoryTable.COLUMN_SEASON);
//                if (columnTitle >= 0) {
//                    history.setSeason(cursor.getString(columnTitle));
//                }
//
//                int columnTime = cursor.getColumnIndex(MovieHistoryTable.COLUMN_PLAYED_TIME);
//                if (columnTime >= 0) {
//                    history.setPlayedTime(cursor.getLong(columnTime));
//                }
//
//                int columnPlayedAt = cursor.getColumnIndex(MovieHistoryTable.COLUMN_PLAYED_AT);
//                if (columnPlayedAt >= 0) {
//                    history.setPlayedAt(new Date(cursor.getLong(columnPlayedAt)));
//                }
//            }
//        } finally {
//            if (cursor != null) {
//                cursor.close();
//            }
//        }
//        return history;
//    }

    private ServerConfig generateServerConfigObject(Cursor cursor) {
        ServerConfig serverConfig = new ServerConfig();

        int nameIndex = cursor.getColumnIndex(ConfigTable.COLUMN_ID);
        if (nameIndex >= 0) {
            serverConfig.setName(cursor.getString(nameIndex));
        }

        int labelIndex = cursor.getColumnIndex(ConfigTable.COLUMN_LABEL);
        if (labelIndex >= 0) {
            serverConfig.setLabel(cursor.getString(labelIndex));
        }

        int isActiveIndex = cursor.getColumnIndex(ConfigTable.COLUMN_IS_ACTIVE);
        if (isActiveIndex >= 0) {
            serverConfig.setActive(cursor.getInt(isActiveIndex) == 1);
        }

        int urlIndex = cursor.getColumnIndex(ConfigTable.COLUMN_URL);
        if (urlIndex >= 0) {
            serverConfig.setUrl(cursor.getString(urlIndex));
        }

        int refererIndex = cursor.getColumnIndex(ConfigTable.COLUMN_REFERER);
        if (refererIndex >= 0) {
            serverConfig.setReferer(cursor.getString(refererIndex));
        }

        int dateIndex = cursor.getColumnIndex(ConfigTable.COLUMN_CREATED_AT);
        if (dateIndex >= 0) {
            try {
                serverConfig.setCreatedAt(new Date(cursor.getLong(dateIndex)));
            } catch (Exception exception) {
                Log.d(TAG, "generateCookieDto: error date: " + exception.getMessage());
            }
        }

        int headersIndex = cursor.getColumnIndex(ConfigTable.COLUMN_HEADER);
        if (headersIndex >= 0) {
            try {
                serverConfig.setHeaders(Util.convertJsonToHashMap(cursor.getString(headersIndex)));
            } catch (JSONException e) {
                Log.d(TAG, "generateServerConfigObject: error retrieving headers as hash: " + e.getMessage());
            }
        }

        int cookieIndex = cursor.getColumnIndex(ConfigTable.COLUMN_COOKIE);
        if (cookieIndex >= 0) {
            serverConfig.setStringCookies(cursor.getString(cookieIndex));
        }
//        Log.d(TAG, "generateCookieDto: "+cookieDTO);
        return serverConfig;
    }


    private CookieDTO generateCookieDto(Cursor cursor) {
        CookieDTO cookieDTO = new CookieDTO();
        int headersIndex = cursor.getColumnIndex(CookieTable.COLUMN_HEADER);
        if (headersIndex >= 0) {
            cookieDTO.headers = cursor.getString(headersIndex);
        }

        int nameIndex = cursor.getColumnIndex(CookieTable.COLUMN_ID);
        if (nameIndex >= 0) {
            cookieDTO.name = cursor.getString(nameIndex);
        }

        int refererIndex = cursor.getColumnIndex(CookieTable.COLUMN_REFERRER);
        if (refererIndex >= 0) {
            cookieDTO.referer = cursor.getString(refererIndex);
        }

        int dateIndex = cursor.getColumnIndex(CookieTable.COLUMN_CREATED_AT);
        if (dateIndex >= 0) {
            try {
                cookieDTO.date = new Date(cursor.getLong(dateIndex));
            } catch (Exception exception) {
                Log.d(TAG, "generateCookieDto: error date: " + exception.getMessage());
            }
        }

        int cookieIndex = cursor.getColumnIndex(CookieTable.COLUMN_COOKIE);
        if (cookieIndex >= 0) {
            cookieDTO.cookie = cursor.getString(cookieIndex);
        }
//        Log.d(TAG, "generateCookieDto: "+cookieDTO);
        return cookieDTO;
    }

    public ServerConfig getServerConfig(String serverName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = ConfigTable.COLUMN_ID + " LIKE '%" + serverName + "%'";
        Cursor cursor = null;
        ServerConfig serverConfig = null;
        try {
            cursor = db.query(ConfigTable.TABLE_NAME, null, selection, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                serverConfig = generateServerConfigObject(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return serverConfig;
    }

    public CookieDTO getCookieDto(String serverName) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = CookieTable.COLUMN_ID + " LIKE '%" + serverName + "%'";
        Cursor cursor = null;
        CookieDTO cookieDTO = null;
        try {
            cursor = db.query(CookieTable.TABLE_NAME, null, selection, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                cookieDTO = generateCookieDto(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return cookieDTO;
    }

    public Map<String, String> getHeadersByServer(String serverName) {
        String headers = "";
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {CookieTable.COLUMN_HEADER};
        String selection = CookieTable.COLUMN_ID + " LIKE '%" + serverName + "%'";
        Cursor cursor = null;
        try {
            cursor = db.query(CookieTable.TABLE_NAME, columns, selection, null, null, null, null);
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndex(CookieTable.COLUMN_HEADER);
                if (columnIndex >= 0) {
                    if (cursor.moveToFirst()) {
                        headers = cursor.getString(columnIndex);
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        Map<String, String> headersMap = new HashMap<>();
        if (headers != null && !headers.equals("")) {
            // Remove the curly braces from the string
            headers = headers.substring(1, headers.length() - 1);

            // Split the string into key-value pairs
            String[] headerPairs = headers.split(", ");

            // Iterate through the header pairs and populate the HashMap
            for (String pair : headerPairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();
                    headersMap.put(key, value);
                }
            }
        }
        return headersMap;
    }

    public String getCookiesByServer(String serverName) {
        String cookies = "";
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {CookieTable.COLUMN_COOKIE};
        String selection = CookieTable.COLUMN_ID + " LIKE '%" + serverName + "%'";
        Cursor cursor = null;
        try {
            cursor = db.query(CookieTable.TABLE_NAME, columns, selection, null, null, null, null);
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndex(CookieTable.COLUMN_COOKIE);
                if (columnIndex >= 0) {
                    if (cursor.moveToFirst()) {
                        cookies = cursor.getString(columnIndex);
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return cookies;
    }

    public String getReferrerByServer(String serverName) {
        String referrer = "";
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {CookieTable.COLUMN_REFERRER};
        String selection = CookieTable.COLUMN_ID + " LIKE '%" + serverName + "%'";
        Cursor cursor = null;
        try {
            cursor = db.query(CookieTable.TABLE_NAME, columns, selection, null, null, null, null);
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndex(CookieTable.COLUMN_REFERRER);
                if (columnIndex >= 0) {
                    if (cursor.moveToFirst()) {
                        referrer = cursor.getString(columnIndex);
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return referrer;
    }

    public void saveMovieList(List<Movie> movieList) {
        for (Movie movie : movieList) {
            //if(findMovieByLink(movie.getVideoUrl()) == null){
            saveMovie(movie, false);
            //  }
        }
    }

    public boolean saveIptvList(Movie movie) {
        db = getWritableDatabase();
        // db.delete(MoviesTable.TABLE_NAME, MoviesTable.COLUMN_VIDEO_URL + " = ?", new String[]{movie.getVideoUrl()});
        ContentValues cv = new ContentValues();
        cv.put(IptvTable.COLUMN_ID, movie.getTitle());
        cv.put(IptvTable.COLUMN_URL, movie.getVideoUrl());
        cv.put(IptvTable.COLUMN_HASH, movie.getDescription());

        long insertResult = db.insert(IptvTable.TABLE_NAME, null, cv);

//        Log.d("TAG", "saveMovie Result: " + insertResult);
        return true;
    }

    public boolean saveMovie(Movie movie, boolean onlyTime) {

        Movie mov = findMovieByLink(movie.getVideoUrl());

        db = getWritableDatabase();
        if (mov != null && mov.getVideoUrl() != null) {
            updateMovie(movie, null, onlyTime);
            //Log.d("MovieDbHelper: " + movie.getSearchContext(), "updateMovie: " + movie.getTitle());
        } else {
            //    Log.d("MovieDbHelper33: " + movie.getSearchContext(), "saveMovie: " + movie.getTitle());

            // db.delete(MoviesTable.TABLE_NAME, MoviesTable.COLUMN_VIDEO_URL + " = ?", new String[]{movie.getVideoUrl()});
            ContentValues cv = new ContentValues();
            cv = getContentValueMovie(cv, movie);
       /* if (movie.getSubList().size() > 0){
            for (Movie series : movie.getSubList()) {
                saveMovie(series);
            }
        }

        */

            long insertResult = db.insert(MoviesTable.TABLE_NAME, null, cv);

//            Log.d("TAG", "saveMovie Result: " + insertResult);
            //    arrayObjectAdapter.add(movie);
            //    arrayObjectAdapter.notifyArrayItemRangeChanged(0, arrayObjectAdapter.size());
            //Log.d("TAG", "addMovieToHistory: "+ historyDesc);
        }
        return true;
    }

    public boolean updateMovie(Movie movie, String oldLink, boolean onlyTime) {
        SQLiteDatabase db = getWritableDatabase();
//        Log.d("TAG", "search: dbHe" + db.toString());

        ContentValues cv = new ContentValues();
        if (movie.getStudio().equals(Movie.SERVER_IPTV)) {
            cv.put(MoviesTable.COLUMN_DESCRIPTION, movie.getDescription());
        } else if (onlyTime) {
            cv.put(MoviesTable.COLUMN_UPDATED_AT, new Date().toString());
        } else {
            cv = getContentValueMovie(cv, movie);
        }

       /* if (movie.getSubList().size() > 0){
            for (Movie series : movie.getSubList()) {
                saveMovie(series);
            }
        }

        */
        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs = new String[]{movie.getVideoUrl()};
        if (oldLink != null) {
            selectionArgs = new String[]{oldLink};
            // db.delete(MoviesTable.TABLE_NAME, MoviesTable.COLUMN_VIDEO_URL + " = ?", selectionArgs);
        }


        long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection, selectionArgs);
//        Log.d("TAG", "updateMovie result:" + updateResult);
        //    arrayObjectAdapter.add(movie);
        //    arrayObjectAdapter.notifyArrayItemRangeChanged(0, arrayObjectAdapter.size());
        //Log.d("TAG", "addMovieToHistory: "+ historyDesc);

        return true;
    }

    private static ContentValues getContentValueMovie(ContentValues cv, Movie movie) {
        cv.put(MoviesTable._ID, movie.getId());
        cv.put(MoviesTable.COLUMN_TITLE, movie.getTitle());
        cv.put(MoviesTable.COLUMN_SEARCH_CONTEXT, movie.getSearchContext());
        cv.put(MoviesTable.COLUMN_MAIN_MOVIE_TITLE, movie.getMainMovieTitle());
        cv.put(MoviesTable.COLUMN_STUDIO, movie.getStudio());
        cv.put(MoviesTable.COLUMN_STATE, movie.getState());
        cv.put(MoviesTable.COLUMN_DESCRIPTION, movie.getDescription());
        cv.put(MoviesTable.COLUMN_BACKGROUND_IMAGE_URL, movie.getBackgroundImageUrl());
        cv.put(MoviesTable.COLUMN_CARD_IMAGE_URL, movie.getCardImageUrl());
        cv.put(MoviesTable.COLUMN_VIDEO_URL, movie.getVideoUrl());
        cv.put(MoviesTable.COLUMN_RATE, movie.getRate());
        cv.put(MoviesTable.COLUMN_TRAILER_URL, movie.getTrailerUrl());
        cv.put(MoviesTable.COLUMN_CREATED_AT, movie.getCreatedAt());
        cv.put(MoviesTable.COLUMN_IS_HISTORY, movie.isHistory());
        cv.put(MoviesTable.COLUMN_PLAYED_TIME, movie.getPlayedTime());
        cv.put(MoviesTable.COLUMN_UPDATED_AT, new Date().getTime());
        cv.put(MoviesTable.COLUMN_GROUP, movie.getGroup());
        return cv;
    }

    public void addMainMovieToHistory2(String movieLink) {
        // String historyDesc =  " لقد شاهدت: \n"+ movie.getTitle() + movie.getHistoryDescription();
        Log.d("TAG", "addMainMovieToHistory:ss " + movieLink);

        db = getReadableDatabase();

        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs = new String[]{movieLink};


        Cursor c = null;
        try {
            c = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );
            int columnIndex = c.getColumnIndex(MoviesTable.COLUMN_MAIN_MOVIE_TITLE);
            if (columnIndex >= 0) {
                if (c.moveToFirst()) {
                    do {
                        String mainMovieLink = c.getString(columnIndex);
                        Log.d("TAG", "addMovieToHistory: link: " + mainMovieLink);
                        if (mainMovieLink == null) {
                            Log.d("TAG", "addMainMovieToHistory: found");
                            db = getWritableDatabase();

                            ContentValues cv = new ContentValues();
                            cv.put(MoviesTable.COLUMN_IS_HISTORY, 1);
                            String selection2 = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

                            //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
                            String[] selectionArgs2 = new String[]{movieLink};

                            long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection2, selectionArgs2);
                            Log.d("TAG", "addMainMovieToHistory: updated");
                            return;
                        } else {
                            //addMainMovieToHistory(mainMovieLink);
                        }
                        break;
                    } while (c.moveToNext());
                }
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public void addMovieToHistory(Movie movie, boolean iptv) {
        db = getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put(MoviesTable.COLUMN_IS_HISTORY, 1);
        cv.put(MoviesTable.COLUMN_UPDATED_AT, new Date().getTime());

        String selection2 = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs2 = new String[]{movie.getVideoUrl()};

        long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection2, selectionArgs2);

    }

    public void updateMoviePlayTime(Movie movie, long playedTime) {
        db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(MoviesTable.COLUMN_PLAYED_TIME, playedTime);
        //cv.put(MoviesTable.T, new Date().getTime());

        String videoUrl = movie.getVideoUrl();
//        if (movie.getMainMovie() != null && movie.getMainMovie().getVideoUrl() != null) {
//            videoUrl = movie.getMainMovie().getVideoUrl();
//        }

        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? ";
        String[] selectionArgs = new String[]{videoUrl};
        // Check if the movie exists in the database
        Cursor cursor = null;
        try {
            cursor = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.getCount() > 0) {
                long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection, selectionArgs);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void addMainMovieToHistory_old(Movie movie) {
        db = getWritableDatabase();

        String mainMovieTitle = movie.getMainMovieTitle();
        if (mainMovieTitle == null) {
            mainMovieTitle = movie.getVideoUrl();
        }
        String mainMovieTitlePath = mainMovieTitle;
        try {
            Uri uri = Uri.parse(mainMovieTitle);
            mainMovieTitlePath = uri.getPath();
        } catch (Exception e) {
            Log.d(TAG, "search: fail parsing url: " + e.getMessage());
        }


//        if (mainMovie.getIsHistory() == 1) {
//            return;
//        }
        Log.d(TAG, "addMainMovieToHistory:ss " + mainMovieTitlePath);
//        Log.d(TAG, "tag:ss " + movie.getMainMovie());

        ContentValues cv = new ContentValues();
        cv.put(MoviesTable.COLUMN_IS_HISTORY, 1);
        cv.put(MoviesTable.COLUMN_UPDATED_AT, new Date().getTime());

        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? ";
        String[] selectionArgs = new String[]{mainMovieTitlePath};
        // Check if the movie exists in the database
        Cursor cursor = null;
        try {
            cursor = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.getCount() > 0) {
                // The movie is already in the database, update it
                long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection, selectionArgs);
                //cursor.close();
            } else {
                // The movie is not found, insert it as a new record
                movie.setIsHistory(1);
                cv = getContentValueMovie(cv, movie);
                long insertResult = db.insert(MoviesTable.TABLE_NAME, null, cv);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void addMainMovieToHistory_old_3(Movie movie) {
        db = getWritableDatabase();

//try {
//                    Uri uri = Uri.parse(url);
//                    m.setMainMovieTitle(uri.getPath());
//                }catch (Exception e){
//                    Log.d(TAG, "search: fail parsing url: "+ e.getMessage());
//                }
        String mainMovieTitle = movie.getMainMovieTitle();
        if (mainMovieTitle == null) {
            mainMovieTitle = movie.getVideoUrl();
        }


//        if (mainMovie.getIsHistory() == 1) {
//            return;
//        }
        Log.d(TAG, "addMainMovieToHistory:ss " + mainMovieTitle);
//        Log.d(TAG, "tag:ss " + movie.getMainMovie());

        ContentValues cv = new ContentValues();
        cv.put(MoviesTable.COLUMN_IS_HISTORY, 1);
        cv.put(MoviesTable.COLUMN_UPDATED_AT, new Date().getTime());

        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? ";
        String[] selectionArgs = new String[]{mainMovieTitle};
        // Check if the movie exists in the database
        Cursor cursor = null;
        try {
            cursor = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.getCount() > 0) {
                // The movie is already in the database, update it
                long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection, selectionArgs);
                //cursor.close();
            } else {
                // The movie is not found, insert it as a new record
                movie.setIsHistory(1);
                cv = getContentValueMovie(cv, movie);
                long insertResult = db.insert(MoviesTable.TABLE_NAME, null, cv);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void addMainMovieToHistory(Movie movie) {
        db = getWritableDatabase();

//try {
//                    Uri uri = Uri.parse(url);
//                    m.setMainMovieTitle(uri.getPath());
//                }catch (Exception e){
//                    Log.d(TAG, "search: fail parsing url: "+ e.getMessage());
//                }
//        Movie mainMovie = movie.getMainMovie();
//        if (mainMovie == null) {
//            mainMovie = movie;
//        }


//        if (mainMovie.getIsHistory() == 1) {
//            return;
//        }
//        Log.d(TAG, "addMainMovieToHistory:ss " + mainMovie);
//        Log.d(TAG, "tag:ss " + movie.getMainMovie());

        ContentValues cv = new ContentValues();
        cv.put(MoviesTable.COLUMN_IS_HISTORY, 1);
        cv.put(MoviesTable.COLUMN_UPDATED_AT, new Date().getTime());

        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? ";
        String[] selectionArgs = new String[]{
//                mainMovie.getVideoUrl()
        };
        // Check if the movie exists in the database
        Cursor cursor = null;
        try {
            cursor = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.getCount() > 0) {
                // The movie is already in the database, update it
                long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection, selectionArgs);
                //cursor.close();
            } else {
                // The movie is not found, insert it as a new record
//                mainMovie.setIsHistory(1);
//                cv = getContentValueMovie(cv, mainMovie);
//                long insertResult = db.insert(MoviesTable.TABLE_NAME, null, cv);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void addMainMovieToHistory(String mainMovieLink, String playtime) {
        // String historyDesc =  " لقد شاهدت: \n"+ movie.getTitle() + movie.getHistoryDescription();
        Log.d("TAG", "addMainMovieToHistory:ss " + mainMovieLink);
      /*  String newDes = null;
        if (episodeTitle != null){
            db = getReadableDatabase();
            String sel = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

            //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
            String[] selArgs = new String[]{mainMovieLink};
            Cursor c = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    sel,
                    selArgs,
                    null,
                    null,
                    MoviesTable.COLUMN_UPDATED_AT
            );
            if (c.moveToFirst()) {
                newDes = c.getString(c.getColumnIndex(MoviesTable.COLUMN_DESCRIPTION)) +
                        "\n" +
                        "تمت مشاهدة:" +
                        "\n" +
                        episodeTitle;
            }
        }
*/
        db = getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put(MoviesTable.COLUMN_IS_HISTORY, 1);
        cv.put(MoviesTable.COLUMN_UPDATED_AT, new Date().getTime());

    /*    if (newDes != null){
            cv.put(MoviesTable.COLUMN_DESCRIPTION, newDes);
        }
*/
        if (playtime != null) {
            cv.put(MoviesTable.COLUMN_PLAYED_TIME, playtime);
        }

        String selection2 = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs2 = new String[]{mainMovieLink};

        long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection2, selectionArgs2);
    }


    public void addMainMovieToHistory_2(String movieLink) {
        // String historyDesc =  " لقد شاهدت: \n"+ movie.getTitle() + movie.getHistoryDescription();
        Log.d("TAG", "addMainMovieToHistory:ss " + movieLink);

        SQLiteDatabase db = getReadableDatabase();

        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs = new String[]{movieLink};

        try (Cursor c = db.query(
                MoviesTable.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        )) {
            int columnIndex = c.getColumnIndex(MoviesTable.COLUMN_MAIN_MOVIE_TITLE);
            if (columnIndex >= 0) {
                if (c.moveToFirst()) {
                    do {
                        String mainMovieLink = c.getString(columnIndex);
                        Log.d("TAG", "addMovieToHistory: link: " + mainMovieLink);
                        if (mainMovieLink == null) {
                            Log.d("TAG", "addMainMovieToHistory: found");
                            db = getWritableDatabase();

                            ContentValues cv = new ContentValues();
                            cv.put(MoviesTable.COLUMN_IS_HISTORY, 1);
                            String selection2 = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

                            //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
                            String[] selectionArgs2 = new String[]{movieLink};

                            long updateResult = db.update(MoviesTable.TABLE_NAME, cv, selection2, selectionArgs2);
                            Log.d("TAG", "addMainMovieToHistory: updated");
                            return;
                        } else {
                            //    addMainMovieToHistory(mainMovieLink);
                        }
                        break;
                    } while (c.moveToNext());
                }
            }
        }
    }

    public void getAllHistoryMovies(boolean tv, MovieRepository.MovieListCallback callback) {
        Observable.fromCallable(() -> {
                    ArrayList<Movie> movieArrayList = new ArrayList<>();
                    db = getReadableDatabase();
                    String selection = MoviesTable.COLUMN_IS_HISTORY + " = ? AND " + MoviesTable.COLUMN_STUDIO + " != ? AND " + MoviesTable.COLUMN_GROUP + " IS NULL";
                    String[] selectionArgs = {"1", Movie.SERVER_IPTV};

                    if (tv) {
                        selection = MoviesTable.COLUMN_IS_HISTORY + " = ? AND (" + MoviesTable.COLUMN_STUDIO + " = ? OR " + MoviesTable.COLUMN_GROUP + " = ? )";
                    }

                    String sortOrder = MoviesTable.COLUMN_UPDATED_AT + " DESC";
                    Cursor c = null;
                    try {
                        c = db.query(
                                MoviesTable.TABLE_NAME,
                                null,
                                selection,
                                selectionArgs,
                                null,
                                null,
                                sortOrder
                        );
                        Log.d(TAG, "getAllHistoryMovies: " + c.getCount());
                        movieArrayList = fetchMovieListFromDB(c);

                    } catch (Exception e) {
                        Log.d(TAG, "getAllHistoryMovies: error: " + e.getMessage());
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                    return movieArrayList;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(movies -> callback.onMovieListFetched("history", movies));
    }

    public ArrayList<Movie> getAllHistoryMovies(boolean tv) {
        // This is a blocking call, should be used only when Rx is not possible
        return Observable.fromCallable(() -> {
                    ArrayList<Movie> movieArrayList = new ArrayList<>();
                    db = getReadableDatabase();
                    String selection = MoviesTable.COLUMN_IS_HISTORY + " = ? AND " + MoviesTable.COLUMN_STUDIO + " != ? AND " + MoviesTable.COLUMN_GROUP + " IS NULL";
                    String[] selectionArgs = {"1", Movie.SERVER_IPTV};

                    if (tv) {
                        selection = MoviesTable.COLUMN_IS_HISTORY + " = ? AND (" + MoviesTable.COLUMN_STUDIO + " = ? OR " + MoviesTable.COLUMN_GROUP + " = ? )";
                    }

                    String sortOrder = MoviesTable.COLUMN_UPDATED_AT + " DESC";
                    Cursor c = null;
                    try {
                        c = db.query(
                                MoviesTable.TABLE_NAME,
                                null,
                                selection,
                                selectionArgs,
                                null,
                                null,
                                sortOrder
                        );
                        Log.d(TAG, "getAllHistoryMovies: " + c.getCount());
                        movieArrayList = fetchMovieListFromDB(c);

                    } catch (Exception e) {
                        Log.d(TAG, "getAllHistoryMovies: error: " + e.getMessage());
                    } finally {
                        if (c != null) {
                            c.close();
                        }
                    }
                    return movieArrayList;
                })
                .blockingFirst();
    }

    private ArrayList<Movie> fetchMovieListFromDB(Cursor c) {
        ArrayList<Movie> movieArrayList = new ArrayList<>();
        if (c.moveToFirst()) {
            do {
                Movie movie = readMovieFromDB(c);
//                movie.setMainMovie(movie);
                movieArrayList.add(movie);
            } while (c.moveToNext());
        }
        return movieArrayList;
    }

    @NonNull
    private static Movie readMovieFromDB(Cursor c) {
        Movie movie = new Movie();
        int idIndex = c.getColumnIndex(MoviesTable._ID);
        if (idIndex >= 0) {
            movie.setId(c.getInt(idIndex));
        }

        int titleIndex = c.getColumnIndex(MoviesTable.COLUMN_TITLE);
        if (titleIndex >= 0) {
            movie.setTitle(c.getString(titleIndex));
        }

        int descriptionIndex = c.getColumnIndex(MoviesTable.COLUMN_DESCRIPTION);
        if (descriptionIndex >= 0) {
            movie.setDescription(c.getString(descriptionIndex));
        }

        int backgroundImageUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_BACKGROUND_IMAGE_URL);
        if (backgroundImageUrlIndex >= 0) {
            movie.setBackgroundImageUrl(c.getString(backgroundImageUrlIndex));
        }

        int cardImageUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_CARD_IMAGE_URL);
        if (cardImageUrlIndex >= 0) {
            movie.setCardImageUrl(c.getString(cardImageUrlIndex));
        }

        int videoUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_VIDEO_URL);
        if (videoUrlIndex >= 0) {
            movie.setVideoUrl(c.getString(videoUrlIndex));
        }

        int studioIndex = c.getColumnIndex(MoviesTable.COLUMN_STUDIO);
        if (studioIndex >= 0) {
            movie.setStudio(c.getString(studioIndex));
        }

        int rateIndex = c.getColumnIndex(MoviesTable.COLUMN_RATE);
        if (rateIndex >= 0) {
            movie.setRate(c.getString(rateIndex));
        }

        int stateIndex = c.getColumnIndex(MoviesTable.COLUMN_STATE);
        if (stateIndex >= 0) {
            movie.setState(c.getInt(stateIndex));
        }

        int createdAtIndex = c.getColumnIndex(MoviesTable.COLUMN_CREATED_AT);
        if (createdAtIndex >= 0) {
            movie.setCreatedAt(c.getString(createdAtIndex));
        }

        int updatedAtIndex = c.getColumnIndex(MoviesTable.COLUMN_UPDATED_AT);
        if (updatedAtIndex >= 0) {
            movie.setUpdatedAt(new Date(c.getLong(updatedAtIndex)));
        }

        int searchContextIndex = c.getColumnIndex(MoviesTable.COLUMN_SEARCH_CONTEXT);
        if (searchContextIndex >= 0) {
            movie.setSearchContext(c.getString(searchContextIndex));
        }

        int trailerUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_TRAILER_URL);
        if (trailerUrlIndex >= 0) {
            movie.setTrailerUrl(c.getString(trailerUrlIndex));
        }

        int mainMovieTitleIndex = c.getColumnIndex(MoviesTable.COLUMN_MAIN_MOVIE_TITLE);
        if (mainMovieTitleIndex >= 0) {
            movie.setMainMovieTitle(c.getString(mainMovieTitleIndex));
        }

        int isHistoryIndex = c.getColumnIndex(MoviesTable.COLUMN_IS_HISTORY);
        if (isHistoryIndex >= 0) {
            movie.setIsHistory(c.getInt(isHistoryIndex));
        }

        int playedTimeIndex = c.getColumnIndex(MoviesTable.COLUMN_PLAYED_TIME);
        if (playedTimeIndex >= 0) {
            movie.setPlayedTime(c.getLong(playedTimeIndex));
        }

        int groupIndex = c.getColumnIndex(MoviesTable.COLUMN_GROUP);
        if (groupIndex >= 0) {
            movie.setGroup(c.getString(groupIndex));
        }
        return movie;
    }

  /*  public ArrayList<Question> getQuestions(int categoryID, String difficulty){
        ArrayList<Question> questionList = new ArrayList<>();
        db = getReadableDatabase();

        String selection = QuestionsTable.COLUMN_CATEGORY_ID + " = ? " +
                " AND " + QuestionsTable.COLUMN_DIFFICULTY + " = ? ";

        String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };

        Cursor c = db.query(
                QuestionsTable.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
                );
        if (c.moveToFirst()){
            do {
                Question question = new Question();
                question.setId(c.getColumnIndex(QuestionsTable._ID)); //get question id from database
                question.setQuestion(c.getString(c.getColumnIndex(QuestionsTable.COLUMN_QUESTION)));
                question.setOption1(c.getString(c.getColumnIndex(QuestionsTable.COLUMN_OPTION1)));
                question.setOption2(c.getString(c.getColumnIndex(QuestionsTable.COLUMN_OPTION2)));
                question.setOption3(c.getString(c.getColumnIndex(QuestionsTable.COLUMN_OPTION3)));
                question.setOption4(c.getString(c.getColumnIndex(QuestionsTable.COLUMN_OPTION4)));
                question.setAnswerNr(c.getInt(c.getColumnIndex(QuestionsTable.COLUMN_ANSWER_NR)));
                question.setAnswerDescription(c.getString(c.getColumnIndex(QuestionsTable.COLUMN_ANSWER_DESCRIPTION)));
                question.setDifficulty(c.getString(c.getColumnIndex(QuestionsTable.COLUMN_DIFFICULTY)));
                question.setCategoryID(c.getInt(c.getColumnIndex(QuestionsTable.COLUMN_CATEGORY_ID)));
                questionList.add(question);
            }while (c.moveToNext());
        }
        //c.close();
        return questionList;
    }

   */

    public ArrayList<Movie> findMoviesByStudio(String studio) {
        db = getReadableDatabase();
        Log.d("dbHelper", "findMovieByStudio: start:" + studio);

        ArrayList<Movie> movieArrayList = new ArrayList<>();

      /*  String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? " +
                " AND " + QuestionsTable.COLUMN_DIFFICULTY + " = ? ";   */

        String selection = MoviesTable.COLUMN_STUDIO + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs = new String[]{studio};
        Movie movie = new Movie();
        Cursor c = null;
        try {
            c = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );
            if (c != null && c.moveToFirst()) {
                do {
                    movie = readMovieFromDB(c);
                    movieArrayList.add(movie);
                    Log.d("dbHelper", "findMovieByStudio: " + movie.getTitle());
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        //c.close();
        ////db.close();
        Log.d("dbHelper", "findMovieByStudio: return:" + movieArrayList.size());
        return movieArrayList;
    }

    public ArrayList<Movie> findMovieBySearchContext(String studio, String searchContext) {
        Log.d(TAG, "findMovieBySearchContext: " + studio);
        db = getReadableDatabase();
        Log.d(TAG, "findMovieBySearchContext2: " + studio);

        String selection = MoviesTable.COLUMN_STUDIO + " = '" + studio + "' AND "
                + MoviesTable.COLUMN_TITLE + " LIKE '%" + searchContext + "%' OR "
                + MoviesTable.COLUMN_GROUP + " LIKE '%" + searchContext + "%' ";
        // + MoviesTable.COLUMN_SEARCH_CONTEXT + " = ? AND "
        // + MoviesTable.COLUMN_IS_HISTORY + " = ? ";
//        String[] selectionArgs = new String[]{studio, searchContext};
        // String[] selectionArgs = new String[]{studio, searchContext, "0"};


        Cursor c = null;
        ArrayList<Movie> movies;
        try {
            c = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    selection,
                    null,
                    null,
                    null,
                    null
            );
            Log.d(TAG, "findMovieBySearchContext: " + c.getCount());
            movies = fetchMovieListFromDB(c);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return movies;
    }

    public int cleanMovieList() {
        Calendar cj = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d HH:mm");
        //String getCurrentDateTime = sdf.format(new Date(cj.getTimeInMillis()+3600  ));

        cj.add(Calendar.HOUR, -2);
        // cj.add(Calendar.MINUTE, -1);
        String getCurrentDateTime = sdf.format(cj.getTime());
        //String getMyTime=movie.getCreatedAt();

        String selection = MoviesTable.COLUMN_CREATED_AT + " < ? AND " + MoviesTable.COLUMN_IS_HISTORY + " = ?";
        String[] selectionArgs = new String[]{getCurrentDateTime, "0"};

       /* db= getReadableDatabase();
       Cursor c = db.query(
                MoviesTable.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        );
        if (c.moveToFirst()) {
            do {
                String date = c.getString(c.getColumnIndex(MoviesTable.COLUMN_CREATED_AT)); //get question id from database


                Log.d("TAG", "cleanMovieList: date: "+ date+ ", Now: "+ getCurrentDateTime);
            } while (c.moveToNext());
        }
        //c.close();
                Log.d("TAG", "cleanMovieList: "+c.getCount()+", smaller als : "+getCurrentDateTime);
*/
        db = getWritableDatabase();

////
      /*  if (getCurrentDateTime.compareTo(getMyTime) > 0)
        {
            Log.d("TAG", "getCurrentDateTime: clean, "+getCurrentDateTime+"// "+getMyTime);
        }

       */
        Log.d("TAG", "clean Movie list");
        return db.delete(MoviesTable.TABLE_NAME, selection, selectionArgs);
    }

    public HashMap<String, ArrayList<Movie>> getMovieListByHash(String hash) {
        db = getReadableDatabase();
      /*  String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? " +
                " AND " + QuestionsTable.COLUMN_DIFFICULTY + " = ? ";   */

        String selection = MoviesTable.COLUMN_MAIN_MOVIE_TITLE + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs = new String[]{hash};


        Cursor c = null;
        HashMap<String, ArrayList<Movie>> groupedMovies = new HashMap<>();
        try {
            c = db.query(
                    MoviesTable.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );
            Log.d(TAG, "getMovieListByHash: " + c.getCount());
            for (Movie movie : fetchMovieListFromDB(c)) {

                if (!groupedMovies.containsKey(movie.getGroup())) {
                    groupedMovies.put(movie.getGroup(), new ArrayList<>());
                }

                // Add the movie to the corresponding group
                groupedMovies.get(movie.getGroup()).add(movie);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        return groupedMovies;
    }

    public List<Movie> findSubListByMainMovieLink(String studio, String mainMovieLink) {
        List<Movie> seriesList = new ArrayList<>();
        if (studio == null || mainMovieLink == null) {
            return seriesList;
        }
        db = getReadableDatabase();


        String selection = MoviesTable.COLUMN_STUDIO + " = ? AND " + MoviesTable.COLUMN_MAIN_MOVIE_TITLE + " = ? ";
        String[] selectionArgs = new String[]{studio, mainMovieLink};


        Cursor c = db.query(
                MoviesTable.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        );
        //Log.d("TAG", "fetchGroup: sublisto: " + c.toString());
        if (c.moveToFirst()) {
            do {
                Movie movie = readMovieFromDB(c);

                seriesList.add(movie);
            } while (c.moveToNext());
        }

        // Log.d("dbHelper", "findSubListByMainMovieLink: sublist: " + seriesList.size());
        //c.close();
        return seriesList;
    }

    public Movie findMovieByUrl(String videoUrl) {
        db = getReadableDatabase();

        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ?";
        String[] selectionArgs = new String[]{videoUrl};


        Cursor c = db.query(
                MoviesTable.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        );
        Log.d("TAG", "fetchGroup: sublisto: " + c.toString());
        Movie movie = new Movie();
        if (c.moveToFirst()) {
            //  Log.d("TAG", "findSeasonsBySearchContext: "+c.getString(c.getColumnIndex(MoviesTable.COLUMN_SEARCH_CONTEXT)));
            movie.setId(c.getColumnIndex(MoviesTable._ID)); //get question id from database
            int titleIndex = c.getColumnIndex(MoviesTable.COLUMN_TITLE);
            if (titleIndex >= 0) {
                movie.setTitle(c.getString(titleIndex));
            }

            int descriptionIndex = c.getColumnIndex(MoviesTable.COLUMN_DESCRIPTION);
            if (descriptionIndex >= 0) {
                movie.setDescription(c.getString(descriptionIndex));
            }

            int backgroundImageUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_BACKGROUND_IMAGE_URL);
            if (backgroundImageUrlIndex >= 0) {
                movie.setBackgroundImageUrl(c.getString(backgroundImageUrlIndex));
            }

            int cardImageUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_CARD_IMAGE_URL);
            if (cardImageUrlIndex >= 0) {
                movie.setCardImageUrl(c.getString(cardImageUrlIndex));
            }

            int videoUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_VIDEO_URL);
            if (videoUrlIndex >= 0) {
                movie.setVideoUrl(c.getString(videoUrlIndex));
            }

            int studioIndex = c.getColumnIndex(MoviesTable.COLUMN_STUDIO);
            if (studioIndex >= 0) {
                movie.setStudio(c.getString(studioIndex));
            }

            int rateIndex = c.getColumnIndex(MoviesTable.COLUMN_RATE);
            if (rateIndex >= 0) {
                movie.setRate(c.getString(rateIndex));
            }

            int stateIndex = c.getColumnIndex(MoviesTable.COLUMN_STATE);
            if (stateIndex >= 0) {
                movie.setState(c.getInt(stateIndex));
            }

            int createdAtIndex = c.getColumnIndex(MoviesTable.COLUMN_CREATED_AT);
            if (createdAtIndex >= 0) {
                movie.setCreatedAt(c.getString(createdAtIndex));
            }

            int updatedAtIndex = c.getColumnIndex(MoviesTable.COLUMN_UPDATED_AT);
            if (updatedAtIndex >= 0) {
                movie.setUpdatedAt(new Date(c.getLong(updatedAtIndex)));
            }

            int searchContextIndex = c.getColumnIndex(MoviesTable.COLUMN_SEARCH_CONTEXT);
            if (searchContextIndex >= 0) {
                movie.setSearchContext(c.getString(searchContextIndex));
            }

            int trailerUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_TRAILER_URL);
            if (trailerUrlIndex >= 0) {
                movie.setTrailerUrl(c.getString(trailerUrlIndex));
            }

            int mainMovieTitleIndex = c.getColumnIndex(MoviesTable.COLUMN_MAIN_MOVIE_TITLE);
            if (mainMovieTitleIndex >= 0) {
                movie.setMainMovieTitle(c.getString(mainMovieTitleIndex));
            }

            int isHistoryIndex = c.getColumnIndex(MoviesTable.COLUMN_IS_HISTORY);
            if (isHistoryIndex >= 0) {
                movie.setIsHistory(c.getInt(isHistoryIndex));
            }

            int playedTimeIndex = c.getColumnIndex(MoviesTable.COLUMN_PLAYED_TIME);
            if (playedTimeIndex >= 0) {
                movie.setPlayedTime(c.getLong(playedTimeIndex));
            }

        }

        //c.close();
        return movie;
    }

    public Movie findIptvListByHash(String hash) {
        db = getReadableDatabase();
      /*  String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? " +
                " AND " + QuestionsTable.COLUMN_DIFFICULTY + " = ? ";   */

        String selection = IptvTable.COLUMN_HASH + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs = new String[]{hash};


        Cursor c = db.query(
                IptvTable.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        );
        Movie movie = null;
        //if (c.moveToFirst()){
        if (c.moveToFirst()) {
            movie = new Movie();
            int titleIndex = c.getColumnIndex(IptvTable.COLUMN_ID);
            if (titleIndex >= 0) {
                movie.setTitle(c.getString(titleIndex));
            }

            int urlIndex = c.getColumnIndex(IptvTable.COLUMN_URL);
            if (urlIndex >= 0) {
                movie.setVideoUrl(c.getString(urlIndex));
            }

            int hashIndex = c.getColumnIndex(IptvTable.COLUMN_HASH);
            if (hashIndex >= 0) {
                movie.setDescription(c.getString(hashIndex));
            }
        }
        Log.d(TAG, "findIptvListByHash: " + movie);
        return movie;
    }

    public Movie findMovieByLink(String movieLink) {
        db = getReadableDatabase();
      /*  String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? " +
                " AND " + QuestionsTable.COLUMN_DIFFICULTY + " = ? ";   */

        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ? ";

        //  String[] selectionArgs = new String[]{ String.valueOf(categoryID), difficulty };
        String[] selectionArgs = new String[]{movieLink};
        Movie movie = null;

        Cursor c = db.query(
                MoviesTable.TABLE_NAME,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        );
        //if (c.moveToFirst()){
        if (c.moveToFirst()) {
            movie = new Movie();
            // do {
            movie.setId(c.getColumnIndex(MoviesTable._ID)); //get question id from database
            int titleIndex = c.getColumnIndex(MoviesTable.COLUMN_TITLE);
            if (titleIndex >= 0) {
                movie.setTitle(c.getString(titleIndex));
            }

            int descriptionIndex = c.getColumnIndex(MoviesTable.COLUMN_DESCRIPTION);
            if (descriptionIndex >= 0) {
                movie.setDescription(c.getString(descriptionIndex));
            }

            int backgroundImageUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_BACKGROUND_IMAGE_URL);
            if (backgroundImageUrlIndex >= 0) {
                movie.setBackgroundImageUrl(c.getString(backgroundImageUrlIndex));
            }

            int cardImageUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_CARD_IMAGE_URL);
            if (cardImageUrlIndex >= 0) {
                movie.setCardImageUrl(c.getString(cardImageUrlIndex));
            }

            int videoUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_VIDEO_URL);
            if (videoUrlIndex >= 0) {
                movie.setVideoUrl(c.getString(videoUrlIndex));
            }

            int studioIndex = c.getColumnIndex(MoviesTable.COLUMN_STUDIO);
            if (studioIndex >= 0) {
                movie.setStudio(c.getString(studioIndex));
            }

            int rateIndex = c.getColumnIndex(MoviesTable.COLUMN_RATE);
            if (rateIndex >= 0) {
                movie.setRate(c.getString(rateIndex));
            }

            int stateIndex = c.getColumnIndex(MoviesTable.COLUMN_STATE);
            if (stateIndex >= 0) {
                movie.setState(c.getInt(stateIndex));
            }

            int createdAtIndex = c.getColumnIndex(MoviesTable.COLUMN_CREATED_AT);
            if (createdAtIndex >= 0) {
                movie.setCreatedAt(c.getString(createdAtIndex));
            }

            int updatedAtIndex = c.getColumnIndex(MoviesTable.COLUMN_UPDATED_AT);
            if (updatedAtIndex >= 0) {
                movie.setUpdatedAt(new Date(c.getLong(updatedAtIndex)));
            }

            int searchContextIndex = c.getColumnIndex(MoviesTable.COLUMN_SEARCH_CONTEXT);
            if (searchContextIndex >= 0) {
                movie.setSearchContext(c.getString(searchContextIndex));
            }

            int trailerUrlIndex = c.getColumnIndex(MoviesTable.COLUMN_TRAILER_URL);
            if (trailerUrlIndex >= 0) {
                movie.setTrailerUrl(c.getString(trailerUrlIndex));
            }

            int mainMovieTitleIndex = c.getColumnIndex(MoviesTable.COLUMN_MAIN_MOVIE_TITLE);
            if (mainMovieTitleIndex >= 0) {
                movie.setMainMovieTitle(c.getString(mainMovieTitleIndex));
            }

            int isHistoryIndex = c.getColumnIndex(MoviesTable.COLUMN_IS_HISTORY);
            if (isHistoryIndex >= 0) {
                movie.setIsHistory(c.getInt(isHistoryIndex));
            }

            int playedTimeIndex = c.getColumnIndex(MoviesTable.COLUMN_PLAYED_TIME);
            if (playedTimeIndex >= 0) {
                movie.setPlayedTime(c.getLong(playedTimeIndex));
            }


            List<Movie> seriesList = findSubListByMainMovieLink(movie.getStudio(), movie.getVideoUrl());
            movie.setSubList(seriesList);

            //arrayObjectAdapter.add(movie);
            //   movieArrayList.add(movie);
            // }while (c.moveToNext());
        }

        //c.close();
        ////db.close();
        //Log.d("dbHelper", "findMovieByLink: return:" + movie.getTitle());
        return movie;
    }

    public int deleteMovie(Movie movie) {
        String selection = MoviesTable.COLUMN_VIDEO_URL + " = ?";
        String[] selectionArgs = new String[]{movie.getVideoUrl()};
        if (db == null) {
            return 0;
        }
        return db.delete(MoviesTable.TABLE_NAME, selection, selectionArgs);
    }

    public ArrayList<ServerConfig> getAllServerConfigs() {
        db = getReadableDatabase();
        ArrayList<ServerConfig> servers = new ArrayList<>();
        Cursor c = null;
        try {
            c = db.query(
                    ConfigTable.TABLE_NAME,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            if (c.moveToFirst()) {
                do {
                    try {
                        ServerConfig server = generateServerConfigObject(c);
                        servers.add(server);
                    } catch (Exception exception) {
                        Log.d(TAG, "generateCookieDto: error date: " + exception.getMessage());
                    }
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return servers;
    }

    public ArrayList<CookieDTO> getAllCookieDto() {
        db = getReadableDatabase();

        ArrayList<CookieDTO> servers = new ArrayList<>();
//           String selection = "SELECT * FROM "+CookieTable.TABLE_NAME;

        Cursor c = db.query(
                CookieTable.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                null
        );
        //if (c.moveToFirst()){
//        Log.d(TAG, "getAllCookieDto: count:" + c.getCount());
        if (c.moveToFirst()) {
            do {
                try {
                    CookieDTO server = generateCookieDto(c);
//                        Log.d(TAG, "getAllCookieDto: server__:" + server);
                    servers.add(server);
                } catch (Exception exception) {
                    Log.d(TAG, "generateCookieDto: error date: " + exception.getMessage());
                }
            } while (c.moveToNext());
        }
        return servers;
    }

    public void saveServerConfig(ServerConfig serverConfig) {
        ContentValues values = new ContentValues();


        SQLiteDatabase db = this.getReadableDatabase();

//        Log.d(TAG, "saveServerConfigAsCookieDTO: " + serverConfig);
        String selection = ConfigTable.COLUMN_ID + " = ?";
        String[] selectionArgs = new String[]{serverConfig.getName()};
        try (Cursor cursor = db.query(
                ConfigTable.TABLE_NAME, null,
                selection, selectionArgs, null, null, null)) {

//            Log.d(TAG, "saveServerConfigAsCookieDTO: cursor:" + cursor.getCount());

            if (serverConfig.getName() != null) {
                values.put(ConfigTable.COLUMN_ID, serverConfig.getName());
                values.put(ConfigTable.COLUMN_NAME, serverConfig.getName());
            }

            if (serverConfig.getLabel() != null) {
                values.put(ConfigTable.COLUMN_LABEL, serverConfig.getLabel());
            }

            values.put(ConfigTable.COLUMN_IS_ACTIVE, serverConfig.isActive() ? 1 : 0);


            if (serverConfig.getUrl() != null) {
                values.put(ConfigTable.COLUMN_URL, serverConfig.getUrl());
            }

            if (serverConfig.getReferer() != null) {
                values.put(ConfigTable.COLUMN_REFERER, serverConfig.getUrl());
            }

            values.put(ConfigTable.COLUMN_CREATED_AT, System.currentTimeMillis());

            if (!serverConfig.getHeaders().isEmpty()) {
                values.put(ConfigTable.COLUMN_HEADER, new JSONObject(serverConfig.getHeaders()).toString());
            }

            if (serverConfig.getStringCookies() != null) {
                values.put(ConfigTable.COLUMN_COOKIE, serverConfig.getStringCookies());
            }

            db = this.getWritableDatabase();
            if (cursor.getCount() > 0) {
                Log.d(TAG, "saveServerConfig: update: " + values);
                // Server entry already exists, update the headers and cookies

                db.update(ConfigTable.TABLE_NAME, values, ConfigTable.COLUMN_ID + " = ?", new String[]{serverConfig.getName()});
            } else {
                // Server entry does not exist, insert a new row
                Log.d(TAG, "saveServerConfig: insert: " + values);
                db.insert(ConfigTable.TABLE_NAME, null, values);
            }
        }
    }


    public void saveServerConfig_old(ServerConfig serverConfig) {
        ContentValues values = new ContentValues();


        SQLiteDatabase db = this.getReadableDatabase();

//        Log.d(TAG, "saveServerConfigAsCookieDTO: " + serverConfig);
        String selection = CookieTable.COLUMN_ID + " = ?";
        String[] selectionArgs = new String[]{serverConfig.getName()};
        try (Cursor cursor = db.query(
                CookieTable.TABLE_NAME, null,
                selection, selectionArgs, null, null, null)) {

//            Log.d(TAG, "saveServerConfigAsCookieDTO: cursor:" + cursor.getCount());

            if (serverConfig.getName() != null) {
                values.put(CookieTable.COLUMN_ID, serverConfig.getName());
            }

            if (serverConfig.getUrl() != null) {
                values.put(CookieTable.COLUMN_REFERRER, serverConfig.getUrl());
            }

            values.put(CookieTable.COLUMN_CREATED_AT, System.currentTimeMillis());

            if (!serverConfig.getHeaders().isEmpty()) {
                values.put(CookieTable.COLUMN_HEADER, new JSONObject(serverConfig.getHeaders()).toString());
            }

            if (serverConfig.getStringCookies() != null) {
                values.put(CookieTable.COLUMN_COOKIE, serverConfig.getStringCookies());
            }

            db = this.getWritableDatabase();
            if (cursor.getCount() > 0) {
                Log.d(TAG, "saveServerConfigAsCookieDTO: update: " + values);
                // Server entry already exists, update the headers and cookies

                db.update(CookieTable.TABLE_NAME, values, CookieTable.COLUMN_ID + " = ?", new String[]{serverConfig.getName()});
            } else {
                // Server entry does not exist, insert a new row
                Log.d(TAG, "saveServerConfigAsCookieDTO: insert: " + values);
                db.insert(CookieTable.TABLE_NAME, null, values);
            }
        }
    }

    public void saveServerConfigAsCookieDTO(ServerConfig serverConfig, Date date) {
        ContentValues values = new ContentValues();


        SQLiteDatabase db = this.getReadableDatabase();

//        Log.d(TAG, "saveServerConfigAsCookieDTO: " + serverConfig);
        String selection = CookieTable.COLUMN_ID + " = ?";
        String[] selectionArgs = new String[]{serverConfig.getName()};
        try (Cursor cursor = db.query(
                CookieTable.TABLE_NAME, null,
                selection, selectionArgs, null, null, null)) {

//            Log.d(TAG, "saveServerConfigAsCookieDTO: cursor:" + cursor.getCount());


            values.put(CookieTable.COLUMN_ID, serverConfig.getName());
            values.put(CookieTable.COLUMN_REFERRER, serverConfig.getUrl());
            values.put(CookieTable.COLUMN_CREATED_AT, date.getTime());

            db = this.getWritableDatabase();
            if (cursor.getCount() > 0) {
                Log.d(TAG, "saveServerConfigAsCookieDTO: update: " + values);
                // Server entry already exists, update the headers and cookies

                db.update(CookieTable.TABLE_NAME, values, CookieTable.COLUMN_ID + " = ?", new String[]{serverConfig.getName()});
            } else {
                // Server entry does not exist, insert a new row
                Log.d(TAG, "saveServerConfigAsCookieDTO: insert: " + values);
                db.insert(CookieTable.TABLE_NAME, null, values);
            }
        }
    }

//    public ArrayList<Movie> getIptvHomepageChannels() {
//        return MovieDbExecutor.getInstance(this).getIptvHomepageChannels();
//    }
//
//    public ArrayList<Movie> getHomepageMovies() {
//        return MovieDbExecutor.getInstance(this).getAllHistoryMovies(false);
//    }

    public boolean isLocalUpdateNeeded(int localUpdatePeriod) {
        // todo: implement
        return true;
    }
}
