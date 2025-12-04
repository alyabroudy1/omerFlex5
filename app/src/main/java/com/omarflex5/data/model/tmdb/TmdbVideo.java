package com.omarflex5.data.model.tmdb;

import com.google.gson.annotations.SerializedName;

public class TmdbVideo {
    @SerializedName("key")
    private String key;

    @SerializedName("site")
    private String site; // "YouTube" or "Vimeo"

    @SerializedName("type")
    private String type; // "Trailer", "Teaser", "Clip", etc.

    @SerializedName("official")
    private boolean official;

    public String getKey() {
        return key;
    }

    public String getSite() {
        return site;
    }

    public String getType() {
        return type;
    }

    public boolean isOfficial() {
        return official;
    }

    /**
     * Get the direct video URL.
     * Note: YouTube doesn't provide direct MP4 URLs. This returns an embed URL.
     * For direct playback, you would need a YouTube player or extraction service.
     */
    public String getVideoUrl() {
        if ("YouTube".equalsIgnoreCase(site)) {
            // YouTube embed URL - works in WebView but not in ExoPlayer directly
            return "https://www.youtube.com/watch?v=" + key;
        } else if ("Vimeo".equalsIgnoreCase(site)) {
            return "https://vimeo.com/" + key;
        }
        return null;
    }
}
