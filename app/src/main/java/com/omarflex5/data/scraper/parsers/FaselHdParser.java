package com.omarflex5.data.scraper.parsers;

import android.util.Log;

import com.omarflex5.data.local.entity.MediaType;
import com.omarflex5.data.scraper.BaseHtmlParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FaselHdParser extends BaseHtmlParser {

    private static final String TAG = "FaselHdParser";

    public FaselHdParser(String html) {
        super(html);
    }

    @Override
    public List<ParsedItem> parseSearchResults() {
        List<ParsedItem> items = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            Elements posts = doc.select("div.postDiv");

            for (Element post : posts) {
                String title = post.select(".h1").text();
                if (title.isEmpty())
                    title = post.select("img").attr("alt");

                String href = post.select("a").attr("href");
                String img = post.select("img").attr("data-src");
                if (img.isEmpty())
                    img = post.select("img").attr("src");
                if (img.isEmpty())
                    img = post.select("img").attr("data-lazy-src");

                if (img.isEmpty()) {
                    Element posterDiv = post.selectFirst(".postImg, .poster, .image");
                    if (posterDiv != null) {
                        img = posterDiv.select("img").attr("data-src");
                        if (img.isEmpty())
                            img = posterDiv.select("img").attr("src");
                    }
                }

                if (!href.isEmpty() && !title.isEmpty()) {
                    MediaType type = detectType(href, title);

                    ParsedItem item = new ParsedItem()
                            .setTitle(title)
                            .setPageUrl(href)
                            .setPosterUrl(img)
                            .setType(type);

                    // Extract categories (common in FaselHD: <div
                    // class="cat"><a...>Action</a>...</div>)
                    Elements catLinks = post
                            .select(".cat a, .genres a, .categories a, a[href*='genre'], a[href*='category']");
                    for (Element cat : catLinks) {
                        String catName = cat.text().trim();
                        if (!catName.isEmpty()) {
                            item.addCategory(catName);
                        }
                    }

                    // Year Badge
                    Element yearElem = post.selectFirst(".year, .date");
                    if (yearElem != null) {
                        String yStr = yearElem.text().replaceAll("[^0-9]", "");
                        if (yStr.length() == 4)
                            item.setYear(Integer.parseInt(yStr));
                    }
                    if (item.getYear() == null)
                        item.setYear(extractYear(title));

                    items.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing search results", e);
        }
        return items;
    }

    /**
     * Parse search results with pagination info.
     * Extracts the "next page" link from FaselHD pagination.
     * FaselHD uses: <a class="page-link" href="...">›</a> for next page
     */
    @Override
    public ParsedSearchResult parseSearchResultsWithPagination() {
        List<ParsedItem> items = parseSearchResults();
        String nextPageUrl = null;

        try {
            Document doc = Jsoup.parse(html);
            // FaselHD pagination: find the link containing "›" (next page symbol)
            Element nextLink = doc.selectFirst("ul.pagination a.page-link:contains(›)");
            if (nextLink != null) {
                nextPageUrl = nextLink.attr("abs:href");
                if (nextPageUrl.isEmpty()) {
                    nextPageUrl = nextLink.attr("href");
                }
                Log.d(TAG, "Found next page URL: " + nextPageUrl);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting next page URL", e);
        }

        return new ParsedSearchResult(items, nextPageUrl);
    }

    @Override
    public ParsedItem parseDetailPage() {
        ParsedItem result = new ParsedItem();
        try {
            Document doc = Jsoup.parse(html);
            String url = getPageUrl();
            result.setPageUrl(url); // CRITICAL: Preserve URL for DB Sync

            // 1. Basic Info
            Element posterElem = doc.selectFirst(".posterImg img, .poster img, .image img, img[class*='poster']");
            String poster = "";
            if (posterElem != null) {
                poster = posterElem.hasAttr("data-src") ? posterElem.attr("data-src")
                        : (posterElem.hasAttr("src") ? posterElem.attr("src") : posterElem.attr("data-lazy-src"));
            }

            String desc = doc.select(".singleDesc p").text();
            if (desc.isEmpty())
                desc = doc.select(".singleDesc").text();

            result.setPosterUrl(poster);
            result.setDescription(desc);

            // Metadata: Rating, Year, Categories
            try {
                // Rating (e.g. <span class="rating">8.5</span>)
                Element rateElem = doc.selectFirst(".rating, .imdb-rating, .rate, [class*='rate'], [class*='rating']");
                if (rateElem != null) {
                    String rStr = rateElem.text().replaceAll("[^0-9.]", "");
                    if (!rStr.isEmpty())
                        result.setRating(Float.parseFloat(rStr));
                }

                // Year
                Element yearLink = doc.selectFirst("a[href*='release-year'], .year, .date");
                if (yearLink != null) {
                    String yStr = yearLink.text().replaceAll("[^0-9]", "");
                    if (yStr.length() == 4)
                        result.setYear(Integer.parseInt(yStr));
                }

                // Categories
                Elements catLinks = doc.select(
                        ".cat a, .genres a, a[href*='genre'], .cat-links a, .categories a, a[href*='category']");
                for (Element cat : catLinks) {
                    result.addCategory(cat.text().trim());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting extra metadata", e);
            }

            // TITLE: Use H1 or .postTitle instead of doc.title() to avoid site suffixes
            String rawTitle = "";
            Element h1 = doc.selectFirst("h1.postTitle, h1, .postTitle, .title h1");
            if (h1 != null) {
                rawTitle = h1.text();
            }
            if (rawTitle.isEmpty()) {
                rawTitle = doc.title(); // Fallback to doc.title() if no H1 found
            }
            result.setTitle(cleanTitle(rawTitle));

            // STRICT REFERENCE LOGIC
            MediaType type = detectType(url, rawTitle);

            // STATELESS CORRECTION & CONTEXT AWARENESS:
            // 1. Check for "Seasons List" (Sidebar/Main) -> Indicates Series View
            // 2. Check for "Episodes List" (Main) -> Indicates Season View
            // 3. Respect Input Context (if user clicked a Season, show Episodes)

            boolean hasSeasonsList = !doc.select(".seasonDiv").isEmpty();
            boolean hasEpisodesList = doc.selectFirst("#epAll") != null;
            ParsedItem inputItem = getSourceItem();

            if (inputItem != null && inputItem.getType() == MediaType.SEASON) {
                // User explicitly clicked a [SEASON] item -> DRILL DOWN to Episodes
                // Ignore season list (sidebar) to avoid loop
                type = MediaType.SEASON;
            } else if (inputItem != null && inputItem.getType() == MediaType.EPISODE) {
                // User explicitly clicked an [EPISODE] item -> DRILL DOWN to Servers
                // Ignore season list (sidebar)
                type = MediaType.EPISODE;
            } else if (hasSeasonsList) {
                // Default: If multiple seasons are listed, show them (Series View)
                // This captures "Search -> Season Page" and treats it as "Series Page"
                type = MediaType.SERIES;
            } else if (type == MediaType.FILM && hasEpisodesList) {
                // Fallback: If marked as Film but has episodes, it's a Season (Single Season?)
                type = MediaType.SEASON;
            }

            result.setType(type);
            Log.d(TAG, "Parsing Detail for URL: " + url + " | Final Type: " + type + " (InputType: "
                    + (inputItem != null ? inputItem.getType() : "null") + ")");

            if (type == MediaType.SERIES) {
                parseSeries(doc, result);
            } else if (type == MediaType.SEASON) {
                parseSeason(doc, result);
            } else {
                parsePlayableContent(doc, result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing detail page", e);
        }
        return result;
    }

    /**
     * EXACT Logic from Reference 'updateMovieState'
     */
    private MediaType detectType(String url, String title) {
        if (url == null)
            url = "";
        if (title == null)
            title = "";

        // 1. Series/Season Case
        boolean seriesCase = url.contains("/seasons") || url.contains("/series") || title.contains("مسلسل")
                || title.contains("الموسم");
        if (seriesCase) {
            return MediaType.SERIES;
        }

        // 2. Episode Case
        if (url.contains("episodes/") || title.contains("حلقة") || title.contains("حلقه")) {
            return MediaType.EPISODE;
        }

        // 3. Film Case
        if (title.contains("فلم") || title.contains("فيلم")) {
            return MediaType.FILM;
        }

        // Default to FILM (Item State)
        return MediaType.FILM;
    }

    private void parseSeries(Document doc, ParsedItem result) {
        Elements seasonDivs = doc.select(".seasonDiv");
        if (!seasonDivs.isEmpty()) {
            for (Element season : seasonDivs) {
                String sTitle = season.select(".title").text();
                String sLink = season.attr("onclick");
                String url = "";
                if (sLink != null) {
                    Pattern p = Pattern.compile("href\\s*=\\s*['\"]([^'\"]+)['\"]");
                    Matcher m = p.matcher(sLink);
                    if (m.find())
                        url = m.group(1);
                    else if (sLink.contains("href = '")) {
                        int start = sLink.indexOf("href = '");
                        if (start != -1)
                            url = sLink.substring(start + 8, sLink.length() - 1);
                    }
                }

                url = fixUrl(url); // FIX: Resolve relative URL

                ParsedItem seasonItem = new ParsedItem()
                        .setTitle(sTitle)
                        .setPageUrl(url)
                        .setType(MediaType.SEASON);

                // EXTRACT SEASON NUMBER
                Integer sNum = extractNumber(sTitle);
                if (sNum != null)
                    seasonItem.setSeasonNumber(sNum);

                result.addSubItem(seasonItem);
            }
        } else {
            // Fallback: If marked as SERIES/Seasons but no SeasonDiv found, check for
            // episodes (Single Season)
            parseSeason(doc, result);
            if (!result.getSubItems().isEmpty()) {
                result.setType(MediaType.SEASON);
            }
        }
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty())
            return "";
        if (url.startsWith("http"))
            return url;

        return com.omarflex5.util.UrlHelper.restore(getBaseUrl(), url);
    }

    private void parseSeason(Document doc, ParsedItem result) {
        Element epContainer = doc.selectFirst("#epAll");
        if (epContainer != null) {
            Elements episodes = epContainer.select("a");
            for (Element ep : episodes) {
                String eTitle = ep.text();
                String eLink = ep.attr("href");

                eLink = fixUrl(eLink); // FIX: Resolve relative URL

                ParsedItem epItem = new ParsedItem()
                        .setTitle(eTitle)
                        .setPageUrl(eLink)
                        .setType(MediaType.EPISODE);

                // EXTRACT EPISODE NUMBER
                Integer eNum = extractNumber(eTitle);
                if (eNum != null)
                    epItem.setEpisodeNumber(eNum);

                result.addSubItem(epItem);
            }
        }
    }

    private Integer extractNumber(String text) {
        if (text == null)
            return null;
        // Matches "15", "Episode 15", "الحلقة 15"
        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void parsePlayableContent(Document doc, ParsedItem result) {
        // 1. Try Servers First (Requested Flow: Episode -> Servers -> Resolutions)
        Elements watchTabs = doc.select(".signleWatch li");
        if (!watchTabs.isEmpty()) {
            for (Element tab : watchTabs) {
                String tTitle = tab.text();
                String tOnclick = tab.attr("onclick");
                String tLink = "";
                if (tOnclick != null && tOnclick.contains("player_iframe")) {
                    tLink = tOnclick.replace("player_iframe.location.href =", "")
                            .replace("'", "")
                            .replace(";", "")
                            .trim();
                }

                tLink = fixUrl(tLink); // FIX: Resolve relative URL

                if (isValidUrl(tLink)) {
                    ParsedItem serverItem = new ParsedItem()
                            .setTitle(tTitle)
                            .setPageUrl(tLink)
                            .setType(MediaType.FILM);
                    result.addSubItem(serverItem);
                }
            }
            if (!result.getSubItems().isEmpty()) {
                Collections.reverse(result.getSubItems());
                return; // Return servers list
            }
        }

        // 2. Player/Resolutions (Fallback or Leaf Node)
        boolean isPlayer = false;
        Elements qualityButtons = doc.select("div.quality_change button");
        if (!qualityButtons.isEmpty())
            isPlayer = true;
        if (doc.html().contains("videoSrc"))
            isPlayer = true;

        if (isPlayer) {
            List<ParsedItem> resolutions = extractResolutions(doc);
            if (!resolutions.isEmpty()) {
                result.setSubItems(resolutions);
                return;
            }
        }
    }

    private List<ParsedItem> extractResolutions(Document doc) {
        List<ParsedItem> resolutions = new ArrayList<>();
        Elements buttons = doc.select("div.quality_change button");
        for (Element btn : buttons) {
            String txt = btn.text();
            String url = btn.attr("data-url");
            if (url.isEmpty())
                url = btn.attr("data-href");

            url = fixUrl(url); // FIX: Resolve relative URL

            if (isValidUrl(url)) {
                resolutions.add(new ParsedItem().setTitle(txt).setPageUrl(url).setQuality(txt));
            }
        }
        if (resolutions.isEmpty()) {
            Elements scripts = doc.select("script");
            for (Element script : scripts) {
                String data = script.data();
                if (data.contains("videoSrc")) {
                    Pattern p = Pattern.compile("videoSrc\\s*=\\s*['\"]([^'\"]+)['\"]");
                    Matcher m = p.matcher(data);
                    if (m.find()) {
                        String url = m.group(1);
                        if (isValidUrl(url)) {
                            resolutions.add(new ParsedItem().setTitle("Auto").setPageUrl(url).setQuality("Auto"));
                        }
                    }
                }
            }
        }
        if (resolutions.isEmpty()) {
            Elements iframes = doc.select("iframe[src]");
            for (Element iframe : iframes) {
                String src = iframe.attr("src");
                if (src.startsWith("//"))
                    src = "https:" + src;
                if (isValidUrl(src)) {
                    resolutions.add(new ParsedItem().setTitle("Embed").setPageUrl(src).setQuality("Embed"));
                }
            }
        }
        return resolutions;
    }

    private boolean isValidUrl(String url) {
        return url != null && url.startsWith("http") && url.length() > 10 && !url.contains(" ");
    }
}
