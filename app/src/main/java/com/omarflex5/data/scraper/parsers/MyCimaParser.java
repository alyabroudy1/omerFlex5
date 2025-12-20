package com.omarflex5.data.scraper.parsers;

import com.omarflex5.data.local.entity.MediaType;
import com.omarflex5.data.scraper.BaseHtmlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for MyCima server (my-cima.me).
 * 
 * Search results: Grid of cards with poster, title, year
 * Detail pages: Movie/Series info with video sources
 */
public class MyCimaParser extends BaseHtmlParser {

    public MyCimaParser(String html) {
        super(html);
    }

    @Override
    public List<ParsedItem> parseSearchResults() {
        List<ParsedItem> results = new ArrayList<>();

        // MyCima uses article.GridItem or similar for search results
        // Pattern: Look for movie/series cards
        Pattern cardPattern = Pattern.compile(
                "<a[^>]+href=\"([^\"]+)\"[^>]*class=\"[^\"]*BlockItem[^\"]*\"[^>]*>.*?" +
                        "<img[^>]+src=\"([^\"]+)\"[^>]*>.*?" +
                        "<strong>([^<]+)</strong>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher m = cardPattern.matcher(html);
        while (m.find()) {
            String url = m.group(1);
            String poster = m.group(2);
            String title = stripHtml(m.group(3));

            if (title == null || title.isEmpty())
                continue;

            Integer year = extractYear(title);
            String cleanedTitle = cleanTitle(title);
            MediaType type = detectMediaType(url, title);

            results.add(new ParsedItem()
                    .setTitle(cleanedTitle)
                    .setOriginalTitle(title)
                    .setPosterUrl(poster)
                    .setPageUrl(url)
                    .setYear(year)
                    .setType(type)
                    .setMatchKey(createMatchKey(cleanedTitle, year, null, null)));
        }

        // Fallback: Try simpler pattern if no results
        if (results.isEmpty()) {
            results = parseSimpleCards();
        }

        return results;
    }

    /**
     * Fallback parser for simpler card structures.
     */
    private List<ParsedItem> parseSimpleCards() {
        List<ParsedItem> results = new ArrayList<>();

        // Find all links with images
        Pattern linkPattern = Pattern.compile(
                "<a[^>]+href=\"(https?://[^\"]+)\"[^>]*>.*?" +
                        "<img[^>]+src=\"([^\"]+)\"[^>]*(?:alt=\"([^\"]+)\")?[^>]*>",
                Pattern.DOTALL);

        Matcher m = linkPattern.matcher(html);
        while (m.find()) {
            String url = m.group(1);
            String poster = m.group(2);
            String title = m.group(3);

            // Filter: Only keep movie/series links
            if (!url.contains("/film/") &&
                    !url.contains("/movie/") &&
                    !url.contains("/series/") &&
                    !url.contains("/مسلسل/") &&
                    !url.contains("/فيلم/")) {
                continue;
            }

            if (title == null || title.isEmpty()) {
                title = extractTitleFromUrl(url);
            }

            if (title == null || title.isEmpty())
                continue;

            Integer year = extractYear(title);
            String cleanedTitle = cleanTitle(title);
            MediaType type = detectMediaType(url, title);

            results.add(new ParsedItem()
                    .setTitle(cleanedTitle)
                    .setOriginalTitle(title)
                    .setPosterUrl(poster)
                    .setPageUrl(url)
                    .setYear(year)
                    .setType(type)
                    .setMatchKey(createMatchKey(cleanedTitle, year, null, null)));
        }

        return results;
    }

    @Override
    public ParsedItem parseDetailPage() {
        ParsedItem item = new ParsedItem();

        // Title: Usually in h1 or specific class
        String title = extractFirst("<h1[^>]*>([^<]+)</h1>", 1);
        if (title == null) {
            title = extractFirst("<title>([^<]+)</title>", 1);
        }

        if (title != null) {
            title = stripHtml(title);
            item.setTitle(cleanTitle(title));
            item.setOriginalTitle(title);
            item.setYear(extractYear(title));
        }

        // Poster
        String poster = extractFirst(
                "<img[^>]+class=\"[^\"]*poster[^\"]*\"[^>]+src=\"([^\"]+)\"", 1);
        if (poster == null) {
            poster = extractFirst("<meta property=\"og:image\" content=\"([^\"]+)\"", 1);
        }
        item.setPosterUrl(poster);

        // Description
        String desc = extractFirst(
                "<div[^>]+class=\"[^\"]*StoryLine[^\"]*\"[^>]*>([^<]+)", 1);
        if (desc == null) {
            desc = extractFirst("<meta name=\"description\" content=\"([^\"]+)\"", 1);
        }
        item.setDescription(stripHtml(desc));

        // Additional Metadata using Jsoup for robustness
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);

            // Rating
            org.jsoup.nodes.Element rateElem = doc.selectFirst(".rating, .imdb-rating, .rate");
            if (rateElem != null) {
                String rStr = rateElem.text().replaceAll("[^0-9.]", "");
                if (!rStr.isEmpty())
                    item.setRating(Float.parseFloat(rStr));
            }

            // Categories
            org.jsoup.select.Elements catLinks = doc.select(".cats a, a[href*='genre']");
            for (org.jsoup.nodes.Element cat : catLinks) {
                item.addCategory(cat.text().trim());
            }

            // Year (if not already extracted from title)
            if (item.getYear() == null || item.getYear() == 0) {
                org.jsoup.nodes.Element yearElem = doc.selectFirst(".year, a[href*='release-year']");
                if (yearElem != null) {
                    String yStr = yearElem.text().replaceAll("[^0-9]", "");
                    if (yStr.length() == 4)
                        item.setYear(Integer.parseInt(yStr));
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MyCimaParser", "Error extracting metadata", e);
        }

        // Type detection
        item.setType(detectMediaType(html, title != null ? title : ""));

        // Quality (if available)
        String quality = extractFirst("(?i)(\\d{3,4}p|HD|BluRay|CAM|WEB-DL)", 1);
        item.setQuality(quality);

        return item;
    }

    /**
     * Extract title from URL slug.
     */
    private String extractTitleFromUrl(String url) {
        // Get last path segment
        String[] parts = url.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (!part.isEmpty() && !part.matches("\\d+")) {
                // Replace dashes/underscores with spaces
                return part.replace("-", " ").replace("_", " ");
            }
        }
        return null;
    }
}
