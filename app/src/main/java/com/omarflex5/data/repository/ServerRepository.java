package com.omarflex5.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.omarflex5.data.local.AppDatabase;
import com.omarflex5.data.local.dao.ServerDao;
import com.omarflex5.data.local.entity.ServerEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository for server management.
 * Handles server priority, CF cookies, and availability tracking.
 */
public class ServerRepository {

    private static final String TAG = "ServerRepository";
    private static volatile ServerRepository INSTANCE;

    private final ServerDao serverDao;
    private final ExecutorService executor;

    private ServerRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        serverDao = db.serverDao();
        executor = Executors.newSingleThreadExecutor();
    }

    public static ServerRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ServerRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ServerRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // ==================== GETTERS ====================

    public LiveData<List<ServerEntity>> getAllServersLive() {
        return serverDao.getAllLive();
    }

    public LiveData<List<ServerEntity>> getEnabledServersLive() {
        return serverDao.getEnabledByPriorityLive();
    }

    public void getSearchableServers(OnResultCallback<List<ServerEntity>> callback) {
        executor.execute(() -> {
            List<ServerEntity> servers = serverDao.getSearchableByPriority();
            if (servers.isEmpty()) {
                Log.w(TAG, "No searchable servers found. Attempting to ensure defaults...");
                ensureDefaultServersSync();
                servers = serverDao.getSearchableByPriority();
            }
            callback.onResult(servers);
        });
    }

    /**
     * Ensures default servers are present in the database.
     */
    public void ensureDefaultServersSync() {
        List<ServerEntity> all = serverDao.getSearchableByPriority();
        if (all.isEmpty()) {
            Log.i(TAG, "Database empty. Injecting default servers...");
            long now = System.currentTimeMillis();

            // Re-using the logic from AppDatabase but here for safety
            try {
                // ArabSeed
                ServerEntity arabseed = new ServerEntity();
                arabseed.setName("arabseed");
                arabseed.setLabel("عرب سيد");
                arabseed.setBaseUrl("https://arabseed.show");
                arabseed.setBasePriority(3);
                arabseed.setCurrentPriority(3);
                arabseed.setEnabled(true);
                arabseed.setSearchable(true);
                arabseed.setRequiresWebView(true);
                arabseed.setSearchUrlPattern("/?s={query}");
                arabseed.setParseStrategy("HTML");
                arabseed.setCreatedAt(now);
                arabseed.setUpdatedAt(now);
                serverDao.insert(arabseed);

                // FaselHD
                ServerEntity faselhd = new ServerEntity();
                faselhd.setName("faselhd");
                faselhd.setLabel("فاصل");
                faselhd.setBaseUrl("https://www.faselhds.biz");
                faselhd.setBasePriority(2);
                faselhd.setCurrentPriority(2);
                faselhd.setEnabled(true);
                faselhd.setSearchable(true);
                faselhd.setRequiresWebView(true);
                faselhd.setSearchUrlPattern("/?s={query}");
                faselhd.setParseStrategy("HTML");
                faselhd.setCreatedAt(now);
                faselhd.setUpdatedAt(now);
                serverDao.insert(faselhd);

                // Akwam
                ServerEntity akwam = new ServerEntity();
                akwam.setName("akwam");
                akwam.setLabel("أكوام");
                akwam.setBaseUrl("https://ak.sv");
                akwam.setBasePriority(5);
                akwam.setCurrentPriority(5);
                akwam.setEnabled(true);
                akwam.setSearchable(true);
                akwam.setRequiresWebView(true);
                akwam.setSearchUrlPattern("/search?q={query}");
                akwam.setParseStrategy("HTML");
                akwam.setCreatedAt(now);
                akwam.setUpdatedAt(now);
                serverDao.insert(akwam);

                Log.d(TAG, "Default servers injected successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to inject default servers: " + e.getMessage());
            }
        }
    }

    public void getServerByName(String name, OnResultCallback<ServerEntity> callback) {
        executor.execute(() -> {
            ServerEntity server = serverDao.getByName(name);
            callback.onResult(server);
        });
    }

    public void getServerById(long id, OnResultCallback<ServerEntity> callback) {
        executor.execute(() -> {
            ServerEntity server = serverDao.getById(id);
            callback.onResult(server);
        });
    }

    public void getCfProtectedServers(OnResultCallback<List<ServerEntity>> callback) {
        executor.execute(() -> {
            List<ServerEntity> servers = serverDao.getCfProtectedServers();
            callback.onResult(servers);
        });
    }

    // ==================== PRIORITY MANAGEMENT ====================

    /**
     * Record a successful request to a server.
     * Improves priority and resets failure count.
     */
    public void recordSuccess(ServerEntity server) {
        executor.execute(() -> {
            server.onSuccess();
            serverDao.update(server);
            Log.d(TAG, "Server " + server.getName() + " success. Priority: " + server.getCurrentPriority());
        });
    }

    /**
     * Record a failed request to a server.
     * Worsens priority and tracks failure count.
     */
    public void recordFailure(ServerEntity server) {
        executor.execute(() -> {
            server.onFailure();
            serverDao.update(server);
            Log.w(TAG, "Server " + server.getName() + " failed. Priority: " + server.getCurrentPriority()
                    + ", Consecutive failures: " + server.getConsecutiveFailures());
        });
    }

    /**
     * Reset server priority to base value (e.g., after manual fix).
     */
    public void resetPriority(long serverId) {
        executor.execute(() -> {
            serverDao.resetPriority(serverId);
            Log.d(TAG, "Reset priority for server ID: " + serverId);
        });
    }

    // ==================== COOKIE MANAGEMENT ====================

    /**
     * Save Cloudflare cookies for a server.
     */
    public void saveCfCookies(long serverId, String cookiesJson, long expiresAt) {
        executor.execute(() -> {
            serverDao.updateCookies(serverId, cookiesJson, expiresAt, System.currentTimeMillis());
            Log.d(TAG, "Saved CF cookies for server ID: " + serverId);
        });
    }

    /**
     * Check if server needs cookie refresh.
     */
    public void needsCookieRefresh(ServerEntity server, OnResultCallback<Boolean> callback) {
        executor.execute(() -> {
            boolean needsRefresh = server.needsCookieRefresh();
            callback.onResult(needsRefresh);
        });
    }

    // ==================== HEADER MANAGEMENT ====================

    /**
     * Save important headers for a server (e.g., Referer).
     */
    public void saveHeaders(long serverId, java.util.Map<String, String> headers) {
        if (headers == null || headers.isEmpty())
            return;

        executor.execute(() -> {
            try {
                String json = new com.google.gson.Gson().toJson(headers);
                serverDao.updateHeaders(serverId, json, System.currentTimeMillis());
                Log.d(TAG, "Saved headers for server ID: " + serverId + " - " + headers.keySet());
            } catch (Exception e) {
                Log.e(TAG, "Failed to save headers: " + e.getMessage());
            }
        });
    }

    /**
     * Get saved headers for a server.
     */
    public java.util.Map<String, String> getSavedHeaders(ServerEntity server) {
        if (server == null || server.getHeadersJson() == null || server.getHeadersJson().isEmpty()) {
            return new java.util.HashMap<>();
        }

        try {
            return new com.google.gson.Gson().fromJson(
                    server.getHeadersJson(),
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, String>>() {
                    }.getType());
        } catch (Exception e) {
            Log.e(TAG, "Error parsing saved headers: " + e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    // ==================== SERVER CONFIGURATION ====================

    /**
     * Update server base URL (e.g., from Firebase remote config).
     */
    public void updateBaseUrl(String serverName, String newBaseUrl) {
        executor.execute(() -> {
            serverDao.updateBaseUrl(serverName, newBaseUrl, System.currentTimeMillis());
            Log.d(TAG, "Updated base URL for " + serverName + " to " + newBaseUrl);
        });
    }

    /**
     * Update search URL pattern (e.g., from generic /?s= to /search?s=).
     */
    public void updateSearchUrlPattern(String serverName, String pattern) {
        executor.execute(() -> {
            serverDao.updateSearchUrlPattern(serverName, pattern, System.currentTimeMillis());
            Log.d(TAG, "Updated search pattern for " + serverName + " to " + pattern);
        });
    }

    /**
     * Enable or disable a server.
     */
    public void setServerEnabled(long serverId, boolean enabled) {
        executor.execute(() -> {
            ServerEntity server = serverDao.getById(serverId);
            if (server != null) {
                server.setEnabled(enabled);
                server.setUpdatedAt(System.currentTimeMillis());
                serverDao.update(server);
            }
        });
    }

    /**
     * Update server from remote config.
     */
    public void updateServerFromRemote(ServerEntity remoteConfig) {
        executor.execute(() -> {
            ServerEntity local = serverDao.getByName(remoteConfig.getName());
            if (local != null) {
                // Only update configurable fields, preserve local state
                local.setBaseUrl(remoteConfig.getBaseUrl());

                String searchPattern = remoteConfig.getSearchUrlPattern();
                if (searchPattern != null && !searchPattern.isEmpty()) {
                    local.setSearchUrlPattern(searchPattern);
                }

                local.setRequiresWebView(remoteConfig.isRequiresWebView());

                // Only update user agent if provided
                if (remoteConfig.getUserAgent() != null && !remoteConfig.getUserAgent().isEmpty()) {
                    local.setUserAgent(remoteConfig.getUserAgent());
                }

                local.setLastSyncedAt(System.currentTimeMillis());
                local.setRemoteConfigVersion(remoteConfig.getRemoteConfigVersion());
                local.setUpdatedAt(System.currentTimeMillis());

                serverDao.update(local);
                Log.d(TAG, "Updated server config from remote: " + local.getName());
            }
        });
    }

    /**
     * Fetch and sync server configurations from Firebase Firestore.
     */
    public void fetchRemoteConfigs() {
        com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore
                .getInstance();
        db.collection("server_configs")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots == null)
                        return;

                    for (com.google.firebase.firestore.QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            // Map manually or use POJO if fields match exactly
                            String name = document.getId();
                            String baseUrl = document.getString("baseUrl");
                            String searchPattern = document.getString("searchUrlPattern");
                            Boolean requiresWebView = document.getBoolean("requiresWebView");
                            Long version = document.getLong("version");

                            if (baseUrl != null) {
                                ServerEntity config = new ServerEntity();
                                config.setName(name);
                                config.setBaseUrl(baseUrl);
                                config.setSearchUrlPattern(searchPattern);
                                config.setRequiresWebView(requiresWebView != null ? requiresWebView : true);
                                config.setRemoteConfigVersion(version != null ? String.valueOf(version) : "1");

                                updateServerFromRemote(config);
                            }
                        } catch (Exception e) {
                            Log.e(TAG,
                                    "Error parsing remote config for doc: " + document.getId() + ", " + e.getMessage());
                        }
                    }
                    Log.d(TAG, "Remote config sync completed");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch remote configs: " + e.getMessage()));
    }

    public void findServerByHost(String host, OnResultCallback<ServerEntity> callback) {
        executor.execute(() -> {
            ServerEntity server = serverDao.findByHost(host);
            callback.onResult(server);
        });
    }

    // ==================== CALLBACK INTERFACE ====================

    public interface OnResultCallback<T> {
        void onResult(T result);
    }
}
