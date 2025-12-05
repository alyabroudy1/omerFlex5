package com.omarflex5.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Server entity with dynamic priority system.
 * 
 * Priority adjusts based on success/failure:
 * - On failure: currentPriority increases (worse)
 * - On success: currentPriority decreases toward basePriority
 * - currentPriority never goes below basePriority
 */
@Entity(tableName = "servers", indices = {
        @Index(value = "name", unique = true),
        @Index(value = "currentPriority")
})
public class ServerEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // Server identification
    private String name; // e.g., "mycima"
    private String label; // e.g., "ماي سيما"
    private String baseUrl; // e.g., "https://my-cima.me"

    // Priority system
    private int basePriority; // Original priority (1 = highest)
    private int currentPriority; // Dynamic priority (adjusted on success/failure)
    private int consecutiveFailures;
    private int consecutiveSuccesses;

    // Status
    private boolean isEnabled;
    private boolean isSearchable; // false for koora (home-only)

    // Cloudflare handling
    private boolean requiresWebView;
    private String cfCookiesJson;
    private Long cfCookiesExpireAt;
    private String userAgent;

    // Scraping configuration
    private String searchUrlPattern; // e.g., "/search?q={query}"
    private String parseStrategy; // "HTML", "JSON", "CUSTOM"

    // Sync with Firebase
    private Long lastSyncedAt;
    private String remoteConfigVersion;

    // Stats
    private long totalSuccesses;
    private long totalFailures;
    private Long lastSuccessAt;
    private Long lastFailureAt;

    // Timestamps
    private long createdAt;
    private long updatedAt;

    // ========== Priority Logic ==========

    /**
     * Call when server request fails.
     * Increases currentPriority (lower priority).
     */
    public void onFailure() {
        consecutiveFailures++;
        consecutiveSuccesses = 0;
        totalFailures++;
        lastFailureAt = System.currentTimeMillis();

        // Increase priority number (worse priority)
        // Max penalty: basePriority + 10
        currentPriority = Math.min(basePriority + consecutiveFailures, basePriority + 10);
        updatedAt = System.currentTimeMillis();
    }

    /**
     * Call when server request succeeds.
     * Decreases currentPriority toward basePriority.
     */
    public void onSuccess() {
        consecutiveSuccesses++;
        consecutiveFailures = 0;
        totalSuccesses++;
        lastSuccessAt = System.currentTimeMillis();

        // Gradually restore priority (never below basePriority)
        if (currentPriority > basePriority) {
            currentPriority = Math.max(basePriority, currentPriority - 1);
        }
        updatedAt = System.currentTimeMillis();
    }

    /**
     * Reset priority to base (e.g., after manual server fix).
     */
    public void resetPriority() {
        currentPriority = basePriority;
        consecutiveFailures = 0;
        consecutiveSuccesses = 0;
        updatedAt = System.currentTimeMillis();
    }

    // ========== Cookie Logic ==========

    public boolean needsCookieRefresh() {
        if (!requiresWebView)
            return false;
        if (cfCookiesJson == null || cfCookiesJson.isEmpty())
            return true;
        if (cfCookiesExpireAt == null)
            return true;
        return System.currentTimeMillis() > cfCookiesExpireAt;
    }

    // ========== Getters and Setters ==========

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getBasePriority() {
        return basePriority;
    }

    public void setBasePriority(int basePriority) {
        this.basePriority = basePriority;
    }

    public int getCurrentPriority() {
        return currentPriority;
    }

    public void setCurrentPriority(int currentPriority) {
        this.currentPriority = currentPriority;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public int getConsecutiveSuccesses() {
        return consecutiveSuccesses;
    }

    public void setConsecutiveSuccesses(int consecutiveSuccesses) {
        this.consecutiveSuccesses = consecutiveSuccesses;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public boolean isSearchable() {
        return isSearchable;
    }

    public void setSearchable(boolean searchable) {
        isSearchable = searchable;
    }

    public boolean isRequiresWebView() {
        return requiresWebView;
    }

    public void setRequiresWebView(boolean requiresWebView) {
        this.requiresWebView = requiresWebView;
    }

    public String getCfCookiesJson() {
        return cfCookiesJson;
    }

    public void setCfCookiesJson(String cfCookiesJson) {
        this.cfCookiesJson = cfCookiesJson;
    }

    public Long getCfCookiesExpireAt() {
        return cfCookiesExpireAt;
    }

    public void setCfCookiesExpireAt(Long cfCookiesExpireAt) {
        this.cfCookiesExpireAt = cfCookiesExpireAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getSearchUrlPattern() {
        return searchUrlPattern;
    }

    public void setSearchUrlPattern(String searchUrlPattern) {
        this.searchUrlPattern = searchUrlPattern;
    }

    public String getParseStrategy() {
        return parseStrategy;
    }

    public void setParseStrategy(String parseStrategy) {
        this.parseStrategy = parseStrategy;
    }

    public Long getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Long lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getRemoteConfigVersion() {
        return remoteConfigVersion;
    }

    public void setRemoteConfigVersion(String remoteConfigVersion) {
        this.remoteConfigVersion = remoteConfigVersion;
    }

    public long getTotalSuccesses() {
        return totalSuccesses;
    }

    public void setTotalSuccesses(long totalSuccesses) {
        this.totalSuccesses = totalSuccesses;
    }

    public long getTotalFailures() {
        return totalFailures;
    }

    public void setTotalFailures(long totalFailures) {
        this.totalFailures = totalFailures;
    }

    public Long getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Long lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public Long getLastFailureAt() {
        return lastFailureAt;
    }

    public void setLastFailureAt(Long lastFailureAt) {
        this.lastFailureAt = lastFailureAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
