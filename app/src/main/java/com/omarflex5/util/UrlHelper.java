package com.omarflex5.util;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlHelper {

    /**
     * Normalizes a URL by stripping the scheme and domain.
     * Example: "https://a.asd.homes/selary/xyz" -> "/selary/xyz"
     * Example: "/selary/xyz" -> "/selary/xyz"
     */
    public static String normalize(String url) {
        if (url == null || url.isEmpty())
            return "";

        // Simple manual check if it starts with http/https to avoid URI strictness
        // issues
        if (url.startsWith("http")) {
            try {
                // Remove protocol and domain
                int doubleSlash = url.indexOf("//");
                if (doubleSlash != -1) {
                    int nextSlash = url.indexOf("/", doubleSlash + 2);
                    if (nextSlash != -1) {
                        return url.substring(nextSlash);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Return as is (assume it's already relative or malformed enough to use as ID)
        return url;
    }

    /**
     * Appends a relative path to the current domain.
     * Handles missing slashes.
     */
    public static String restore(String domain, String relativePath) {
        if (domain == null || domain.isEmpty())
            return relativePath;
        if (relativePath == null || relativePath.isEmpty())
            return domain;

        // Check if relativePath is actually absolute (sometimes happens)
        if (relativePath.startsWith("http"))
            return relativePath;

        String cleanDomain = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        String cleanPath = relativePath.startsWith("/") ? relativePath : "/" + relativePath;

        return cleanDomain + cleanPath;
    }
}
