package com.omarflex5.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.omarflex5.data.local.dao.EpisodeDao;
import com.omarflex5.data.local.dao.MediaDao;
import com.omarflex5.data.local.dao.MediaSourceDao;
import com.omarflex5.data.local.dao.SearchQueueDao;
import com.omarflex5.data.local.dao.SeasonDao;
import com.omarflex5.data.local.dao.ServerDao;
import com.omarflex5.data.local.dao.UserMediaStateDao;
import com.omarflex5.data.local.entity.EpisodeEntity;
import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.local.entity.MediaSourceEntity;
import com.omarflex5.data.local.entity.SearchQueueEntity;
import com.omarflex5.data.local.entity.SeasonEntity;
import com.omarflex5.data.local.entity.ServerEntity;
import com.omarflex5.data.local.entity.UserMediaStateEntity;

import java.util.concurrent.Executors;

@Database(entities = {
        MediaEntity.class,
        SeasonEntity.class,
        EpisodeEntity.class,
        ServerEntity.class,
        MediaSourceEntity.class,
        SearchQueueEntity.class,
        UserMediaStateEntity.class
}, version = 10, exportSchema = true)
@TypeConverters({ Converters.class })
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "omarflex_db";
    private static volatile AppDatabase INSTANCE;

    // DAOs
    public abstract MediaDao mediaDao();

    public abstract SeasonDao seasonDao();

    public abstract EpisodeDao episodeDao();

    public abstract ServerDao serverDao();

    public abstract MediaSourceDao mediaSourceDao();

    public abstract SearchQueueDao searchQueueDao();

    public abstract UserMediaStateDao userMediaStateDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME)
                            .addCallback(new PrepopulateCallback())
                            .fallbackToDestructiveMigration() // For development changes
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Callback to prepopulate database with default servers.
     */
    private static class PrepopulateCallback extends RoomDatabase.Callback {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            android.util.Log.d("AppDatabase", "onCreate: Database created. Prepopulating...");

            android.util.Log.d("AppDatabase", "onCreate: Database created. Prepopulating...");
            // Run synchronously to ensure data is ready for the first query
            if (INSTANCE != null) {
                insertDefaultServers(INSTANCE.serverDao());
                android.util.Log.d("AppDatabase", "Prepopulation completed.");
            } else {
                android.util.Log.e("AppDatabase", "Prepopulation failed: INSTANCE is null");
            }
        }

        @Override
        public void onDestructiveMigration(@NonNull SupportSQLiteDatabase db) {
            super.onDestructiveMigration(db);
            android.util.Log.w("AppDatabase", "onDestructiveMigration: Database reset. Prepopulating...");
            // Same as onCreate for destructive migration - Run synchronously
            if (INSTANCE != null) {
                insertDefaultServers(INSTANCE.serverDao());
                android.util.Log.d("AppDatabase", "Prepopulation after migration completed.");
            }
        }
    }

    /**
     * Insert the 9 default servers with their configurations.
     */
    private static void insertDefaultServers(ServerDao serverDao) {
        android.util.Log.d("AppDatabase", "Starting insertDefaultServers...");
        long now = System.currentTimeMillis();
        // Server 1: MyCima
        try {
            ServerEntity mycima = new ServerEntity();
            mycima.setName("mycima");
            mycima.setLabel("ماي سيما");
            mycima.setBaseUrl("https://my-cima.me");
            mycima.setBasePriority(1);
            mycima.setCurrentPriority(1);
            mycima.setEnabled(false); // Disabled for Production
            mycima.setSearchable(false);
            mycima.setRequiresWebView(true); // CF protected
            mycima.setSearchUrlPattern("/search/{query}");
            mycima.setParseStrategy("HTML");
            mycima.setCreatedAt(now);
            mycima.setUpdatedAt(now);
            serverDao.insert(mycima);
            android.util.Log.d("AppDatabase", "Inserted MyCima");

            // Server 2: FaselHD
            ServerEntity faselhd = new ServerEntity();
            faselhd.setName("faselhd");
            faselhd.setLabel("فاصل");
            faselhd.setBaseUrl("https://www.faselhds.biz");
            faselhd.setBasePriority(2);
            faselhd.setCurrentPriority(2);
            faselhd.setEnabled(true); // Enabled for Production
            faselhd.setSearchable(true);
            faselhd.setRequiresWebView(true); // CF protected
            faselhd.setSearchUrlPattern("/?s={query}");
            faselhd.setParseStrategy("HTML");
            faselhd.setCreatedAt(now);
            faselhd.setUpdatedAt(now);
            serverDao.insert(faselhd);
            android.util.Log.d("AppDatabase", "Inserted FaselHD");

            // Server 3: ArabSeed
            ServerEntity arabseed = new ServerEntity();
            arabseed.setName("arabseed");
            arabseed.setLabel("عرب سيد");
            arabseed.setBaseUrl("https://arabseed.show");
            arabseed.setBasePriority(3);
            arabseed.setCurrentPriority(3);
            arabseed.setEnabled(true); // Enabled for Production
            arabseed.setSearchable(true);
            arabseed.setRequiresWebView(true); // Sometimes CF
            arabseed.setSearchUrlPattern("/?s={query}");
            arabseed.setParseStrategy("HTML");
            arabseed.setCreatedAt(now);
            arabseed.setUpdatedAt(now);
            serverDao.insert(arabseed);
            android.util.Log.d("AppDatabase", "Inserted ArabSeed");

            // Server 4: CimaNow
            ServerEntity cimanow = new ServerEntity();
            cimanow.setName("cimanow");
            cimanow.setLabel("سيماناو");
            cimanow.setBaseUrl("https://cimanow.cc");
            cimanow.setBasePriority(4);
            cimanow.setCurrentPriority(4);
            cimanow.setEnabled(false); // Enabled for Production
            cimanow.setSearchable(false);
            cimanow.setRequiresWebView(true); // CF protected
            cimanow.setSearchUrlPattern("/?s={query}");
            cimanow.setParseStrategy("HTML");
            cimanow.setCreatedAt(now);
            cimanow.setUpdatedAt(now);
            serverDao.insert(cimanow);
            android.util.Log.d("AppDatabase", "Inserted CimaNow");

            // Server 5: Koora
            ServerEntity koora = new ServerEntity();
            koora.setName("koora");
            koora.setLabel("كورة");
            koora.setBaseUrl("https://www.koraa-live.com");
            koora.setBasePriority(99);
            koora.setCurrentPriority(99);
            koora.setEnabled(false);
            koora.setSearchable(false);
            koora.setRequiresWebView(true);
            koora.setParseStrategy("HTML");
            koora.setCreatedAt(now);
            koora.setUpdatedAt(now);
            serverDao.insert(koora);
            android.util.Log.d("AppDatabase", "Inserted Koora");

            // Server 6: IPTV
            ServerEntity iptv = new ServerEntity();
            iptv.setName("iptv");
            iptv.setLabel("قنوات");
            iptv.setBaseUrl("local://iptv");
            iptv.setBasePriority(3);
            iptv.setCurrentPriority(3);
            iptv.setEnabled(false);
            iptv.setSearchable(false);
            iptv.setRequiresWebView(false);
            iptv.setParseStrategy("LOCAL");
            iptv.setCreatedAt(now);
            iptv.setUpdatedAt(now);
            serverDao.insert(iptv);
            android.util.Log.d("AppDatabase", "Inserted IPTV");

            // Server 7: Akwam
            ServerEntity akwam = new ServerEntity();
            akwam.setName("akwam");
            akwam.setLabel("أكوام");
            akwam.setBaseUrl("https://ak.sv");
            akwam.setBasePriority(5);
            akwam.setCurrentPriority(5);
            akwam.setEnabled(true); // Enabled for Production
            akwam.setSearchable(true);
            akwam.setRequiresWebView(true);
            akwam.setSearchUrlPattern("/search?q={query}");
            akwam.setParseStrategy("HTML");
            akwam.setCreatedAt(now);
            akwam.setUpdatedAt(now);
            serverDao.insert(akwam);
            android.util.Log.d("AppDatabase", "Inserted Akwam");

            // Server 8: Old Akwam
            ServerEntity oldAkwam = new ServerEntity();
            oldAkwam.setName("oldakwam");
            oldAkwam.setLabel("اكوام القديم");
            oldAkwam.setBaseUrl("https://ak.sv");
            oldAkwam.setBasePriority(6);
            oldAkwam.setCurrentPriority(6);
            oldAkwam.setEnabled(false);
            oldAkwam.setSearchable(false);
            oldAkwam.setRequiresWebView(true);
            oldAkwam.setSearchUrlPattern("/old/advanced-search/{query}");
            oldAkwam.setParseStrategy("HTML");
            oldAkwam.setCreatedAt(now);
            oldAkwam.setUpdatedAt(now);
            serverDao.insert(oldAkwam);
            android.util.Log.d("AppDatabase", "Inserted OldAkwam");

            // Server 9: WatanFlix
            ServerEntity watanflix = new ServerEntity();
            watanflix.setName("watanflix");
            watanflix.setLabel("watan");
            watanflix.setBaseUrl("https://watanflix.com");
            watanflix.setBasePriority(7);
            watanflix.setCurrentPriority(7);
            watanflix.setEnabled(false);
            watanflix.setSearchable(false);
            watanflix.setRequiresWebView(false);
            watanflix.setSearchUrlPattern("/?s={query}");
            watanflix.setParseStrategy("HTML");
            watanflix.setCreatedAt(now);
            watanflix.setUpdatedAt(now);
            serverDao.insert(watanflix);
            android.util.Log.d("AppDatabase", "Inserted WatanFlix");

        } catch (Exception e) {
            android.util.Log.e("AppDatabase", "Error during prepopulation: " + e.getMessage());
        }
    }
}
