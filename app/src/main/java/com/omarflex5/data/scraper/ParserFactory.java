package com.omarflex5.data.scraper;

import com.omarflex5.data.scraper.parsers.MyCimaParser;

/**
 * Factory for creating server-specific HTML parsers.
 */
public class ParserFactory {

    /**
     * Get the appropriate parser for a server.
     */
    public static BaseHtmlParser getParser(String serverName, String html, String url) {
        BaseHtmlParser parser;
        if (serverName == null) {
            parser = new GenericParser(html);
            parser.setPageUrl(url);
            return parser;
        }

        switch (serverName.toLowerCase()) {
            case "mycima":
                parser = new MyCimaParser(html);
                break;
            case "faselhd":
                parser = new com.omarflex5.data.scraper.parsers.FaselHdParser(html);
                break;
            case "arabseed":
                parser = new com.omarflex5.data.scraper.parsers.ArabSeedParser(html);
                break;
            case "akwam":
                parser = new com.omarflex5.data.scraper.parsers.AkwamParser(html, url);
                break;
            case "oldakwam":
                parser = new com.omarflex5.data.scraper.parsers.OldAkwamParser(html, url);
                break;
            default:
                parser = new GenericParser(html);
                break;
        }

        parser.setPageUrl(url);
        return parser;
    }

    /**
     * Get the search URLs for a server based on its specific strategy.
     */
    public static java.util.List<String> getSearchUrls(com.omarflex5.data.local.entity.ServerEntity server,
            String query) {
        String pattern = server.getSearchUrlPattern();
        if (pattern == null || pattern.isEmpty()) {
            pattern = "/?s={query}";
        }

        String encodedQuery;
        try {
            encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            encodedQuery = query.replace(" ", "+");
        }

        String baseSearchUrl = server.getBaseUrl() + pattern.replace("{query}", encodedQuery);
        java.util.List<String> urls = new java.util.ArrayList<>();

        if ("arabseed".equalsIgnoreCase(server.getName())) {
            // ArabSeed specific: Split into Movies and Series for better accuracy
            urls.add(baseSearchUrl + "&type=movies");
            urls.add(baseSearchUrl + "&type=series");
        } else {
            urls.add(baseSearchUrl);
        }

        return urls;
    }

    /**
     * Generic parser that works with common HTML structures.
     */
    private static class GenericParser extends BaseHtmlParser {
        public GenericParser(String html) {
            super(html);
        }

        @Override
        public java.util.List<ParsedItem> parseSearchResults() {
            java.util.List<ParsedItem> results = new java.util.ArrayList<>();

            // Generic: Look for common card patterns
            java.util.regex.Pattern cardPattern = java.util.regex.Pattern.compile(
                    "<a[^>]+href=\"([^\"]+)\"[^>]*>.*?" +
                            "<img[^>]+src=\"([^\"]+)\"[^>]*>.*?" +
                            "(?:<(?:h\\d|strong|span)[^>]*>([^<]+)</(?:h\\d|strong|span)>)?",
                    java.util.regex.Pattern.DOTALL);

            java.util.regex.Matcher m = cardPattern.matcher(html);
            while (m.find()) {
                String url = m.group(1);
                String poster = m.group(2);
                String title = m.group(3);

                // Filter media URLs only
                if (!url.contains("/watch") &&
                        !url.contains("/film") &&
                        !url.contains("/movie") &&
                        !url.contains("/series") &&
                        !url.contains("/فيلم") &&
                        !url.contains("/مسلسل")) {
                    continue;
                }

                if (title != null) {
                    title = stripHtml(title);
                }

                if (title == null || title.length() < 2)
                    continue;

                Integer year = extractYear(title);
                String cleanedTitle = cleanTitle(title);

                results.add(new ParsedItem()
                        .setTitle(cleanedTitle)
                        .setOriginalTitle(title)
                        .setPosterUrl(poster)
                        .setPageUrl(url)
                        .setYear(year)
                        .setType(detectMediaType(url, title))
                        .setMatchKey(createMatchKey(cleanedTitle, year, null, null)));
            }

            return results;
        }

        @Override
        public ParsedItem parseDetailPage() {
            ParsedItem item = new ParsedItem();

            // Title from h1 or og:title
            String title = extractFirst("<h1[^>]*>([^<]+)</h1>", 1);
            if (title == null) {
                title = extractFirst("<meta property=\"og:title\" content=\"([^\"]+)\"", 1);
            }
            if (title != null) {
                title = stripHtml(title);
                item.setTitle(cleanTitle(title));
                item.setOriginalTitle(title);
                item.setYear(extractYear(title));
            }

            // Poster from og:image
            String poster = extractFirst("<meta property=\"og:image\" content=\"([^\"]+)\"", 1);
            item.setPosterUrl(poster);

            // Description from og:description
            String desc = extractFirst("<meta property=\"og:description\" content=\"([^\"]+)\"", 1);
            if (desc == null) {
                desc = extractFirst("<meta name=\"description\" content=\"([^\"]+)\"", 1);
            }
            item.setDescription(stripHtml(desc));

            item.setType(detectMediaType(html, title != null ? title : ""));

            return item;
        }
    }
}
