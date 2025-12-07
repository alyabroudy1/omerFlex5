package com.omarflex5.temp.omerflex.service.update;

public class Constants {
    public static final String UPDATE_SERVER_URL = "https://your-update-server.com/api/version.json";
    public static final String APK_DOWNLOAD_URL = "https://your-update-server.com/releases/app-release.apk";

    public static final String UPDATE_PREFS = "update_prefs";
    public static final String PREF_CURRENT_VERSION = "current_version";
    public static final String PREF_DOWNLOAD_ID = "download_id";
    public static final String PREF_DOWNLOAD_PROGRESS = "download_progress";

    public static final int UPDATE_CHECK_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours
    public static final int DOWNLOAD_TIMEOUT = 30000; // 30 seconds
    public static final int MAX_DOWNLOAD_RETRIES = 3;

    public static final String APK_FILE_NAME = "update.apk";
    public static final String UPDATE_DIR = "updates";
}