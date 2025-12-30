package com.omarflex5.engine;

/**
 * Configuration settings for web engines.
 * Applied during engine initialization.
 */
public class WebEngineSettings {

    private String userAgent = null;
    private boolean javaScriptEnabled = true;
    private boolean domStorageEnabled = true;
    private boolean databaseEnabled = true;
    private boolean allowFileAccess = false;
    private boolean allowContentAccess = true;
    private boolean javaScriptCanOpenWindowsAutomatically = true;
    private boolean mediaPlaybackRequiresUserGesture = false;
    private boolean useWideViewPort = true;
    private boolean loadWithOverviewMode = true;
    private boolean supportZoom = true;
    private boolean builtInZoomControls = true;
    private boolean displayZoomControls = false;
    private String cacheMode = "default"; // "default", "no-cache", "cache-only", "cache-else-network"
    private boolean mixedContentAllowed = true;

    public static WebEngineSettings createDefault() {
        return new WebEngineSettings();
    }

    public static WebEngineSettings createForScraping() {
        WebEngineSettings settings = new WebEngineSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentAllowed(true);
        return settings;
    }

    public static WebEngineSettings builder() {
        return new WebEngineSettings();
    }

    public WebEngineSettings build() {
        return this;
    }

    // Getters and Setters

    public String getUserAgent() {
        return userAgent;
    }

    public WebEngineSettings setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public boolean isJavaScriptEnabled() {
        return javaScriptEnabled;
    }

    public WebEngineSettings setJavaScriptEnabled(boolean enabled) {
        this.javaScriptEnabled = enabled;
        return this;
    }

    public boolean isDomStorageEnabled() {
        return domStorageEnabled;
    }

    public WebEngineSettings setDomStorageEnabled(boolean enabled) {
        this.domStorageEnabled = enabled;
        return this;
    }

    public boolean isDatabaseEnabled() {
        return databaseEnabled;
    }

    public WebEngineSettings setDatabaseEnabled(boolean enabled) {
        this.databaseEnabled = enabled;
        return this;
    }

    public boolean isAllowFileAccess() {
        return allowFileAccess;
    }

    public WebEngineSettings setAllowFileAccess(boolean allowed) {
        this.allowFileAccess = allowed;
        return this;
    }

    public boolean isAllowContentAccess() {
        return allowContentAccess;
    }

    public WebEngineSettings setAllowContentAccess(boolean allowed) {
        this.allowContentAccess = allowed;
        return this;
    }

    public boolean isJavaScriptCanOpenWindowsAutomatically() {
        return javaScriptCanOpenWindowsAutomatically;
    }

    public WebEngineSettings setJavaScriptCanOpenWindowsAutomatically(boolean allowed) {
        this.javaScriptCanOpenWindowsAutomatically = allowed;
        return this;
    }

    public boolean isMediaPlaybackRequiresUserGesture() {
        return mediaPlaybackRequiresUserGesture;
    }

    public WebEngineSettings setMediaPlaybackRequiresUserGesture(boolean required) {
        this.mediaPlaybackRequiresUserGesture = required;
        return this;
    }

    public boolean isUseWideViewPort() {
        return useWideViewPort;
    }

    public WebEngineSettings setUseWideViewPort(boolean use) {
        this.useWideViewPort = use;
        return this;
    }

    public boolean isLoadWithOverviewMode() {
        return loadWithOverviewMode;
    }

    public WebEngineSettings setLoadWithOverviewMode(boolean load) {
        this.loadWithOverviewMode = load;
        return this;
    }

    public boolean isSupportZoom() {
        return supportZoom;
    }

    public WebEngineSettings setSupportZoom(boolean support) {
        this.supportZoom = support;
        return this;
    }

    public boolean isBuiltInZoomControls() {
        return builtInZoomControls;
    }

    public WebEngineSettings setBuiltInZoomControls(boolean show) {
        this.builtInZoomControls = show;
        return this;
    }

    public boolean isDisplayZoomControls() {
        return displayZoomControls;
    }

    public WebEngineSettings setDisplayZoomControls(boolean display) {
        this.displayZoomControls = display;
        return this;
    }

    public String getCacheMode() {
        return cacheMode;
    }

    public WebEngineSettings setCacheMode(String mode) {
        this.cacheMode = mode;
        return this;
    }

    public boolean isMixedContentAllowed() {
        return mixedContentAllowed;
    }

    public WebEngineSettings setMixedContentAllowed(boolean allowed) {
        this.mixedContentAllowed = allowed;
        return this;
    }
}
