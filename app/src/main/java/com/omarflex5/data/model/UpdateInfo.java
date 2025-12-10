package com.omarflex5.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class UpdateInfo implements Serializable {
    @SerializedName("versionCode")
    private int versionCode;

    @SerializedName("versionName")
    private String versionName;

    @SerializedName("releaseNotes")
    private String releaseNotes;

    @SerializedName("apkUrl")
    private String apkUrl;

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public String getApkUrl() {
        return apkUrl;
    }
}
