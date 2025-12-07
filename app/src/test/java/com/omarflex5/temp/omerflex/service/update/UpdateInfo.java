package com.omarflex5.temp.omerflex.service.update;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class UpdateInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    @SerializedName("versionCode")
    private int versionCode;

    @SerializedName("versionName")
    private String versionName;

    @SerializedName("changelog")
    private String changelog;

    @SerializedName("apkUrl")
    private String apkUrl;

    @SerializedName("apkSize")
    private long apkSize;

    @SerializedName("apkChecksum")
    private String apkChecksum;

    @SerializedName("mandatory")
    private boolean mandatory;

    @SerializedName("releaseDate")
    private String releaseDate;

    // Default constructor
    public UpdateInfo() {
    }

    // Getters and setters
    public int getVersionCode() { return versionCode; }
    public void setVersionCode(int versionCode) { this.versionCode = versionCode; }

    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }

    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }

    public String getApkUrl() { return apkUrl; }
    public void setApkUrl(String apkUrl) { this.apkUrl = apkUrl; }

    public long getApkSize() { return apkSize; }
    public void setApkSize(long apkSize) { this.apkSize = apkSize; }

    public String getApkChecksum() { return apkChecksum; }
    public void setApkChecksum(String apkChecksum) { this.apkChecksum = apkChecksum; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    @Override
    public String toString() {
        return "UpdateInfo{" +
                "versionCode=" + versionCode +
                ", versionName='" + versionName + '\'' +
                ", changelog='" + changelog + '\'' +
                ", apkUrl='" + apkUrl + '\'' +
                ", apkSize=" + apkSize +
                ", apkChecksum='" + apkChecksum + '\'' +
                ", mandatory=" + mandatory +
                ", releaseDate='" + releaseDate + '\'' +
                '}';
    }
}