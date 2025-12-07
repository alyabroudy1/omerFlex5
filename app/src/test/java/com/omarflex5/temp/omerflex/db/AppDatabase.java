package com.omarflex5.temp.omerflex.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.omerflex.dao.ServerConfigDao;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieHistory;
import com.omerflex.entity.ServerConfig;

@Database(entities = {Movie.class, MovieHistory.class, ServerConfig.class}, version = 8, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract MovieDao movieDao();
    public abstract MovieHistoryDao movieHistoryDao();
    public abstract ServerConfigDao serverConfigDao();

    private static volatile AppDatabase INSTANCE;

    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Clear the server_config table
            database.execSQL("DELETE FROM server_config");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "omerflex_database")
                            .addMigrations(MIGRATION_7_8)
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
