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
            callback.onResult(servers);
        });
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

    // ==================== CALLBACK INTERFACE ====================

    public interface OnResultCallback<T> {
        void onResult(T result);
    }
}
