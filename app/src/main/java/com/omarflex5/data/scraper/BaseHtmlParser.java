package com.omarflex5.data.scraper;

import com.omarflex5.data.local.entity.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base HTML parser with common extraction utilities.
 * Server-specific parsers extend this class.
 */
public abstract class BaseHtmlParser {

    protected final String html;
    protected String pageUrl; // The URL of the page being parsed

    public BaseHtmlParser(String html) {
        this.html = html;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    /**
     * Parse search results from the HTML.
     */
    public abstract List<ParsedItem> parseSearchResults();

    /**
     * Parse detail page (movie/series info).
     */
    public abstract ParsedItem parseDetailPage();

    // ==================== COMMON UTILITIES ====================

    /**
     * Extract text between two markers.
     */
    protected String extractBetween(String content, String start, String end) {
        int startIdx = content.indexOf(start);
        if (startIdx < 0)
            return null;
        startIdx += start.length();

        int endIdx = content.indexOf(end, startIdx);
        if (endIdx < 0)
            return null;

        return content.substring(startIdx, endIdx).trim();
    }

    /**
     * Extract all matches of a pattern.
     */
    protected List<String> extractAll(String pattern, int group) {
        List<String> results = new ArrayList<>();
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(html);
        while (m.find()) {
            if (group <= m.groupCount()) {
                results.add(m.group(group));
            }
        }
        return results;
    }

    /**
     * Extract first match of a pattern.
     */
    protected String extractFirst(String pattern, int group) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(html);
        if (m.find() && group <= m.groupCount()) {
            return m.group(group);
        }
        return null;
    }

    /**
     * Clean HTML tags from text.
     */
    protected String stripHtml(String html) {
        if (html == null)
            return null;
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&#\\d+;", "")
                .trim();
    }

    /**
     * Extract year from title like "Movie Name (2023)".
     */
    protected Integer extractYear(String title) {
        Pattern p = Pattern.compile("\\((\\d{4})\\)");
        Matcher m = p.matcher(title);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Clean title by removing year and common suffixes.
     */
    protected String cleanTitle(String title) {
        if (title == null)
            return null;
        return title
                .replaceAll("\\(\\d{4}\\)", "") // Remove year
                .replaceAll("(?i)\\s*مترجم\\s*", "") // Remove "مترجم" (translated)
                .replaceAll("(?i)\\s*مدبلج\\s*", "") // Remove "مدبلج" (dubbed)
                .replaceAll("(?i)\\s*HD\\s*", "")
                .replaceAll("(?i)\\s*BluRay\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Determine media type from URL or title.
     */
    protected MediaType detectMediaType(String url, String title) {
        String combined = (url + " " + title).toLowerCase();

        if (combined.contains("series") ||
                combined.contains("مسلسل") ||
                combined.contains("season") ||
                combined.contains("موسم") ||
                combined.contains("episode") ||
                combined.contains("حلقة")) {
            return MediaType.SERIES;
        }

        return MediaType.FILM;
    }

    /**
     * Create a match key for deduplication.
     * Format: normalized_title|year|season|episode
     */
    protected String createMatchKey(String title, Integer year, Integer season, Integer episode) {
        String normalized = title != null ? title.toLowerCase()
                .replaceAll("[^a-z0-9\\u0600-\\u06FF]", "") // Keep Arabic
                .trim() : "";

        StringBuilder key = new StringBuilder(normalized);
        key.append("|").append(year != null ? year : "");
        key.append("|").append(season != null ? season : "");
        key.append("|").append(episode != null ? episode : "");

        return key.toString();
    }

    // ==================== PARSED ITEM ====================

    /**
     * Represents a parsed search result or detail.
     */
    public static class ParsedItem {
        private String title;
        private String originalTitle;
        private String posterUrl;
        private String pageUrl;
        private String description;
        private Integer year;
        private MediaType type;
        private Integer seasonNumber;
        private Integer episodeNumber;
        private String quality;
        private String matchKey;
        private List<String> categories = new ArrayList<>();

        private List<ParsedItem> subItems = new ArrayList<>();

        // Builder pattern
        public ParsedItem setTitle(String title) {
            this.title = title;
            return this;
        }

        public ParsedItem setCategories(List<String> categories) {
            this.categories = categories != null ? categories : new ArrayList<>();
            return this;
        }

        public ParsedItem addCategory(String category) {
            if (this.categories == null)
                this.categories = new ArrayList<>();
            this.categories.add(category);
            return this;
        }

        public ParsedItem setOriginalTitle(String originalTitle) {
            this.originalTitle = originalTitle;
            return this;
        }

        public ParsedItem setPosterUrl(String posterUrl) {
            this.posterUrl = posterUrl;
            return this;
        }

        public ParsedItem setPageUrl(String pageUrl) {
            this.pageUrl = pageUrl;
            return this;
        }

        public ParsedItem setDescription(String description) {
            this.description = description;
            return this;
        }

        public ParsedItem setYear(Integer year) {
            this.year = year;
            return this;
        }

        public ParsedItem setType(MediaType type) {
            this.type = type;
            return this;
        }

        public ParsedItem setSeasonNumber(Integer seasonNumber) {
            this.seasonNumber = seasonNumber;
            return this;
        }

        public ParsedItem setEpisodeNumber(Integer episodeNumber) {
            this.episodeNumber = episodeNumber;
            return this;
        }

        public ParsedItem setQuality(String quality) {
            this.quality = quality;
            return this;
        }

        public ParsedItem setMatchKey(String matchKey) {
            this.matchKey = matchKey;
            return this;
        }

        public ParsedItem setSubItems(List<ParsedItem> subItems) {
            this.subItems = subItems;
            return this;
        }

        public ParsedItem addSubItem(ParsedItem item) {
            if (this.subItems == null)
                this.subItems = new ArrayList<>();
            this.subItems.add(item);
            return this;
        }

        // Getters
        public String getTitle() {
            return title;
        }

        public List<String> getCategories() {
            return categories;
        }

        public String getOriginalTitle() {
            return originalTitle;
        }

        public String getPosterUrl() {
            return posterUrl;
        }

        public String getPageUrl() {
            return pageUrl;
        }

        public String getDescription() {
            return description;
        }

        public Integer getYear() {
            return year;
        }

        public MediaType getType() {
            return type;
        }

        public Integer getSeasonNumber() {
            return seasonNumber;
        }

        public Integer getEpisodeNumber() {
            return episodeNumber;
        }

        public String getQuality() {
            return quality;
        }

        public String getMatchKey() {
            return matchKey;
        }

        public List<ParsedItem> getSubItems() {
            return subItems;
        }
    }
}
