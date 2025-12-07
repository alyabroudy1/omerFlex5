package com.omarflex5.temp.omerflex;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.multidex.MultiDex;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;
import com.omerflex.db.AppDatabase;
import com.omerflex.entity.Movie;
import com.omerflex.providers.SsdpResponder;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.casting.CommunicationManager;
import com.omerflex.service.casting.Device;
import com.omerflex.service.casting.Message;
import com.omerflex.service.casting.NetworkServiceDiscovery;
import com.omerflex.view.ExoplayerMediaPlayer;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom Application class for OmerFlex.
 * Handles initialization of app-wide components and provides access to them.
 */
public class OmerFlexApplication extends Application {
    private static final String TAG = "OmerFlexApplication";

    private static volatile OmerFlexApplication instance;

    // WeakReference to prevent context leaks
    private static WeakReference<Context> contextReference;

    // Initialization flags
    private final AtomicBoolean isServerConfigInitialized = new AtomicBoolean(false);
    private AppDatabase database;
    private SsdpResponder ssdpResponder;

    private CommunicationManager communicationManager;
    private NetworkServiceDiscovery networkDiscovery;


    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Initialize Firebase
        Log.d(TAG, "onCreate: initializing FirebaseApp");
        FirebaseApp app = FirebaseApp.initializeApp(this);
        if (app == null) {
            Log.e(TAG, "onCreate: FirebaseApp.initializeApp returned null!");
        } else {
            Log.d(TAG, "onCreate: FirebaseApp initialized");
        }




        int permissionStatus = ContextCompat.checkSelfPermission(
                getApplicationContext(),
                android.Manifest.permission.PROCESS_OUTGOING_CALLS
        );

        // Compare the status with PERMISSION_GRANTED
        Log.d(TAG, "onCreate: Permission xxx :" + (permissionStatus == PackageManager.PERMISSION_GRANTED));



        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                Log.e(TAG, "No Firebase apps found!");
            } else {
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                Log.d(TAG, "FirebaseDatabase instance retrieved successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting FirebaseDatabase instance", e);
        }


        contextReference = new WeakReference<>(getApplicationContext());

        // One-time migration from SQLiteOpenHelper to Room.
        // Deletes the old database.
        android.content.SharedPreferences migrationPrefs = getSharedPreferences("migration_to_room", MODE_PRIVATE);
        if (!migrationPrefs.getBoolean("is_migrated", false)) {
            deleteDatabase("MoviesHistory.db");
            migrationPrefs.edit().putBoolean("is_migrated", true).apply();
            Log.i(TAG, "Old database 'MoviesHistory.db' has been removed for Room migration.");
        }

        database = AppDatabase.getDatabase(this);
        Log.i(TAG, "Database initialized");

        initializeServerConfigRepository();
        initializeNetworkServices();

//        // Enable strict mode for debug builds
//        if (BuildConfig.DEBUG) {
//            enableStrictMode();
//        }

        ssdpResponder = new SsdpResponder(getApplicationContext());
        ssdpResponder.start();

        Log.i(TAG, "Application initialized. Other components will be lazily initialized on demand.");
    }

    private void initializeNetworkServices() {
        Log.d(TAG, "initializeNetworkServices: ");
        WifiP2pManager wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            Log.e(TAG, "initializeNetworkServices: WifiP2pManager is not available.");
            return;
        }
        WifiP2pManager.Channel channel = wifiP2pManager.initialize(this, getMainLooper(), null);
        String deviceId = "OmerFlex_" + Build.MODEL;

        communicationManager = new CommunicationManager(deviceId);
        networkDiscovery = new NetworkServiceDiscovery(this, wifiP2pManager, channel);

        communicationManager.startServer();
        networkDiscovery.registerService(CommunicationManager.SERVER_PORT);

        communicationManager.addMessageListener(new CommunicationManager.MessageListener() {
            @Override
            public void onMessageReceived(Message message, Device sender) {
                Log.d(TAG, "onMessageReceived: " + message.getContent());
                if (message.getMessageType() == Message.MessageType.DATA) {
                    Gson gson = new Gson();
                    Movie movie = gson.fromJson(message.getContent(), Movie.class);
                    if (movie != null) {
                        Intent intent = new Intent(getApplicationContext(), ExoplayerMediaPlayer.class);
                        Util.generateIntent(movie, intent, false);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                }
            }

            @Override
            public void onMessageSent(Message message, Device recipient) {
                Log.d(TAG, "onMessageSent: to " + recipient.getDeviceName());
            }

            @Override
            public void onMessageFailed(Message message, Device recipient, String error) {
                Log.e(TAG, "onMessageFailed: to " + recipient.getDeviceName() + " error: " + error);
            }

            @Override
            public void onDeviceConnected(Device device) {
                Log.d(TAG, "onDeviceConnected: " + device.getDeviceName());
            }

            @Override
            public void onDeviceDisconnected(Device device) {
                Log.d(TAG, "onDeviceDisconnected: " + device.getDeviceName());
            }
        });
    }
    private void initializeServerConfigRepository() {
        if (isServerConfigInitialized.compareAndSet(false, true)) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    ServerConfigRepository.initialize(getApplicationContext());
                    ServerConfigRepository.getInstance().initializeDbWithDefaults();
//                    ServerConfigRepository.getInstance().checkForRemoteUpdates(null);
                    Log.i(TAG, "ServerConfigRepository initialized");
                } catch (Exception e) {
                    Log.e(TAG, "Error initializing ServerConfigRepository", e);
                    isServerConfigInitialized.set(false);
                }
            });
        }
    }

    private boolean isDefaultProcess() {
        int pid = android.os.Process.myPid();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            for (ActivityManager.RunningAppProcessInfo processInfo : am.getRunningAppProcesses()) {
                if (processInfo.pid == pid) {
                    return getPackageName().equals(processInfo.processName);
                }
            }
        }
        return false;
    }

    /**
     * Enable StrictMode for detecting potential issues in debug builds
     */
    private void enableStrictMode() {
        StrictMode.ThreadPolicy.Builder threadPolicyBuilder = new StrictMode.ThreadPolicy.Builder()
                //.detectDiskReads() // Disabled due to framework-level violations from IdsController
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog();

        // API 23+ features
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            threadPolicyBuilder.detectResourceMismatches();
        }

        StrictMode.setThreadPolicy(threadPolicyBuilder.build());

        StrictMode.VmPolicy.Builder vmPolicyBuilder = new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog();

        // API 23+ features
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            vmPolicyBuilder.detectCleartextNetwork();
        }

        // API 28+ features
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            vmPolicyBuilder.detectNonSdkApiUsage();
        }

        StrictMode.setVmPolicy(vmPolicyBuilder.build());

        Log.i(TAG, "StrictMode enabled for debug build");
    }

    /**
     * Get the application instance
     * @return OmerFlexApplication instance
     */
    @NonNull
    public static OmerFlexApplication getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Application instance is not yet created");
        }
        return instance;
    }

    public static CommunicationManager getCommunicationManager() {
        return getInstance().communicationManager;
    }

    public static NetworkServiceDiscovery getNetworkDiscovery() {
        return getInstance().networkDiscovery;
    }

    /**
     * Get application context safely
     * @return The application context
     */
    @NonNull
    public static Context getAppContext() {
        Context context = contextReference != null ? contextReference.get() : null;
        if (context == null) {
            throw new IllegalStateException("Application context is not available");
        }
        return context;
    }

    public AppDatabase getDatabase() {
        return database;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // Enable multidex support
        MultiDex.install(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Release non-essential resources based on memory level
        if (level >= TRIM_MEMORY_MODERATE) {
            Log.i(TAG, "Memory pressure detected (level " + level + "), releasing non-essential resources");
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory warning, releasing as many resources as possible");
    }

    @Override
    public void onTerminate() {
        if (ssdpResponder != null) {
            ssdpResponder.stop();
        }
        if (communicationManager != null) {
            communicationManager.cleanup();
        }
        if (networkDiscovery != null) {
            networkDiscovery.cleanup();
        }
        // Clear references
        contextReference.clear();
        instance = null;

        super.onTerminate();
    }
}