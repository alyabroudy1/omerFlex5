package com.omarflex5.data.scraper.parsers;

import android.util.Log;

import com.omarflex5.data.local.entity.MediaType;
import com.omarflex5.data.scraper.BaseHtmlParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for ArabSeed server.
 */
public class ArabSeedParser extends BaseHtmlParser {

    private static final String TAG = "ArabSeedParser";

    public ArabSeedParser(String html) {
        super(html);
    }

    @Override
    public List<ParsedItem> parseSearchResults() {
        List<ParsedItem> results = new ArrayList<>();

        // Debug logging to find structure
        Log.d(TAG, "HTML start: " + html.substring(0, Math.min(1000, html.length())));

        // Common ArabSeed structure: MovieBlock or BlockItem or similar
        // <div class="MovieBlock"> <a href="..."> <img ...> <div class="Name">...</div>
        // </a> </div>

        // Pattern 1: MovieBlock (Common)
        Pattern pattern1 = Pattern.compile(
                "<div[^>]*class=\"[^\"]*MovieBlock[^\"]*\"[^>]*>\\s*" +
                        "<a[^>]+href=\"([^\"]+)\"[^>]*>.*?" +
                        "<img[^>]+(?:data-src|src)=\"([^\"]+)\"[^>]*>.*?" +
                        "<div[^>]*class=\"[^\"]*Name[^\"]*\"[^>]*>([^<]+)</div>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        Matcher m = pattern1.matcher(html);
        while (m.find()) {
            String url = m.group(1);
            String poster = m.group(2);
            String title = stripHtml(m.group(3));
            addItem(results, title, url, poster);
        }

        if (results.isEmpty()) {
            // Fallback: try searching for 'post-div' or 'entry' just in case
            // Log specific container if found
            if (html.contains("MovieBlock")) {
                Log.d(TAG, "Found MovieBlock but regex failed");
            } else {
                Log.d(TAG, "No MovieBlock found in HTML");
            }
        }

        return results;
    }

    private void addItem(List<ParsedItem> results, String title, String url, String poster) {
        if (title == null || title.isEmpty())
            return;

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

    @Override
    public ParsedItem parseDetailPage() {
        return new ParsedItem(); // TODO
    }
}
