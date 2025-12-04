package com.omarflex5.util;

import android.net.Uri;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for media-related operations.
 * This class will grow as the project develops.
 */
public class MediaUtils {

    private static final String HEADER_SEPARATOR = "|";
    private static final String HEADER_PAIR_SEPARATOR = "&";
    private static final String HEADER_VALUE_SEPARATOR = "=";

    /**
     * Result class containing parsed URL and headers.
     */
    public static class ParsedMedia {
        private final String url;
        private final Map<String, String> headers;

        public ParsedMedia(String url, Map<String, String> headers) {
            this.url = url;
            this.headers = headers != null ? headers : new HashMap<>();
        }

        public String getUrl() {
            return url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public boolean hasHeaders() {
            return !headers.isEmpty();
        }
    }

    /**
     * Parses a URL string that may contain headers.
     * Format:
     * https://example.com/video.m3u8|User-Agent=Android&Referer=https://site.com
     * 
     * @param urlWithHeaders The URL string potentially containing headers after |
     * @return ParsedMedia object with separated URL and headers
     */
    public static ParsedMedia parseUrlWithHeaders(String urlWithHeaders) {
        if (TextUtils.isEmpty(urlWithHeaders)) {
            return new ParsedMedia("", new HashMap<>());
        }

        String url;
        Map<String, String> headers = new HashMap<>();

        int separatorIndex = urlWithHeaders.indexOf(HEADER_SEPARATOR);
        if (separatorIndex > 0) {
            url = urlWithHeaders.substring(0, separatorIndex);
            String headerString = urlWithHeaders.substring(separatorIndex + 1);
            headers = parseHeaders(headerString);
        } else {
            url = urlWithHeaders;
        }

        return new ParsedMedia(url, headers);
    }

    /**
     * Parses a header string into key-value pairs.
     * Format: User-Agent=Android&Referer=https://site.com
     * 
     * @param headerString The header string with & separated pairs
     * @return Map of header name to value
     */
    public static Map<String, String> parseHeaders(String headerString) {
        Map<String, String> headers = new HashMap<>();

        if (TextUtils.isEmpty(headerString)) {
            return headers;
        }

        String[] pairs = headerString.split(HEADER_PAIR_SEPARATOR);
        for (String pair : pairs) {
            int eqIndex = pair.indexOf(HEADER_VALUE_SEPARATOR);
            if (eqIndex > 0) {
                String key = pair.substring(0, eqIndex).trim();
                String value = pair.substring(eqIndex + 1).trim();
                // Remove quotes if present
                value = removeQuotes(value);
                if (!TextUtils.isEmpty(key)) {
                    headers.put(key, value);
                }
            }
        }

        return headers;
    }

    /**
     * Removes surrounding quotes from a string.
     */
    public static String removeQuotes(String value) {
        if (value == null)
            return null;
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * Determines the media type from URL.
     */
    public static MediaType getMediaType(String url) {
        if (TextUtils.isEmpty(url)) {
            return MediaType.OTHER;
        }

        String lowerUrl = url.toLowerCase();

        if (lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/")) {
            return MediaType.HLS;
        } else if (lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/")) {
            return MediaType.DASH;
        } else if (lowerUrl.contains(".mp4") || lowerUrl.contains(".mkv") ||
                lowerUrl.contains(".webm") || lowerUrl.contains(".avi")) {
            return MediaType.PROGRESSIVE;
        } else if (lowerUrl.contains("rtmp://") || lowerUrl.contains("rtsp://")) {
            return MediaType.RTMP;
        }

        return MediaType.OTHER;
    }

    /**
     * Checks if URL is a streaming format (HLS/DASH).
     */
    public static boolean isStreamingUrl(String url) {
        MediaType type = getMediaType(url);
        return type == MediaType.HLS || type == MediaType.DASH;
    }

    /**
     * Media type enumeration.
     */
    public enum MediaType {
        HLS, // HTTP Live Streaming (.m3u8)
        DASH, // MPEG-DASH (.mpd)
        PROGRESSIVE, // Progressive download (mp4, mkv, etc.)
        RTMP, // Real-Time Messaging Protocol
        OTHER
    }

    /**
     * Builds a URL with headers string.
     * 
     * @param url     The base URL
     * @param headers Map of headers to append
     * @return Combined URL string with headers
     */
    public static String buildUrlWithHeaders(String url, Map<String, String> headers) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }

        if (headers == null || headers.isEmpty()) {
            return url;
        }

        StringBuilder sb = new StringBuilder(url);
        sb.append(HEADER_SEPARATOR);

        boolean first = true;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!first) {
                sb.append(HEADER_PAIR_SEPARATOR);
            }
            sb.append(entry.getKey())
                    .append(HEADER_VALUE_SEPARATOR)
                    .append(entry.getValue());
            first = false;
        }

        return sb.toString();
    }
}
