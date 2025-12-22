package com.omarflex5.data.scraper.parsers;

import android.util.Log;

import com.omarflex5.data.local.entity.MediaType;
import com.omarflex5.data.scraper.BaseHtmlParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArabSeedParser extends BaseHtmlParser {

    private static final String TAG = "ArabSeedParser";

    public ArabSeedParser(String html) {
        super(html);
    }

    @Override
    public List<ParsedItem> parseSearchResults() {
        List<ParsedItem> results = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            String currentUrl = getPageUrl();
            boolean isSeriesSearch = currentUrl != null && currentUrl.contains("type=series");
            boolean isMovieSearch = currentUrl != null && currentUrl.contains("type=movies");

            // Adapted from ArabSeedServer.search
            Elements lis = doc.getElementsByClass("MovieBlock");
            if (lis.isEmpty()) {
                lis = doc.getElementsByClass("postDiv");
            }
            if (lis.isEmpty()) {
                lis = doc.select("li.box__xs__2");
            }
            if (lis.isEmpty()) {
                // Fallback: Check standard list items with specific classes
                Elements allLis = doc.select("li");
                lis = new Elements();
                for (Element li : allLis) {
                    String cls = li.className().toLowerCase();
                    if (cls.contains("box") || cls.contains("item") || cls.contains("episode")) {
                        lis.add(li);
                    }
                }
            }

            for (Element li : lis) {
                try {
                    List<String> itemCategories = new ArrayList<>();
                    Element linkElem = li.getElementsByAttribute("href").first();
                    if (linkElem == null)
                        continue;

                    String url = linkElem.attr("href");

                    Element imgElem = li.selectFirst("img");
                    String posterUrl = "";
                    String movieTitle = "";

                    if (imgElem != null) {
                        posterUrl = imgElem.hasAttr("data-src") ? imgElem.attr("data-src")
                                : (imgElem.hasAttr("src") ? imgElem.attr("src") : imgElem.attr("data-lazy-src"));
                        movieTitle = imgElem.attr("alt");
                    }

                    if (movieTitle == null || movieTitle.isEmpty()) {
                        movieTitle = li.select(".Title, .h1, .post-title").text();
                    }

                    if (movieTitle == null || movieTitle.isEmpty())
                        continue; // Still need a title

                    // Category detection for Type
                    boolean isSeries = false;
                    Element catElem = li.selectFirst(".post__category");
                    if (catElem == null) {
                        Elements cats = linkElem.getElementsByClass("category");
                        if (!cats.isEmpty())
                            catElem = cats.first();
                    }

                    if (catElem != null) {
                        String catText = catElem.text();
                        if (catText.contains("اغاني") || catText.contains("كمبيوتر") || catText.contains("موبايلات")) {
                            continue;
                        }
                        if (catText.contains("مسلسل"))
                            isSeries = true;

                        // Add to item categories
                        itemCategories.add(catText);
                    }

                    // Detection First
                    MediaType type = MediaType.FILM;
                    String lowerUrl = url.toLowerCase();
                    boolean hasSeriesKeywords = lowerUrl.contains("/series") || lowerUrl.contains("/selary/")
                            || lowerUrl.contains("%d9%85%d8%b3%d9%84%d8%b3%d9%84");

                    if (isSeriesSearch || isSeries || hasSeriesKeywords || movieTitle.contains("مسلسل")) {
                        type = MediaType.SERIES;
                    } else if (isMovieSearch) {
                        type = MediaType.FILM;
                    } else if (lowerUrl.contains("/episode") || movieTitle.contains("حلقة")) {
                        type = MediaType.EPISODE;
                    }

                    // Display Logic Afterwards
                    movieTitle = movieTitle.replace("مسلسل", "").trim();

                    ParsedItem item = new ParsedItem();
                    item.setTitle(cleanTitle(movieTitle));
                    item.setOriginalTitle(movieTitle);
                    item.setPageUrl(url);
                    item.setPosterUrl(posterUrl);
                    item.setType(type);
                    item.setYear(extractYear(movieTitle));
                    item.setCategories(itemCategories); // Add categories

                    // Extract rating if present (e.g. <span class="imdb">8.5</span>)
                    Element ratingElem = li.selectFirst(".imdb, .rating, .rate, [class*='rate'], [class*='rating']");
                    if (ratingElem != null) {
                        String rText = ratingElem.text().replaceAll("[^0-9.]", "");
                        if (!rText.isEmpty()) {
                            try {
                                item.setRating(Float.parseFloat(rText));
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    results.add(item);

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing item", e);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing search results", e);
        }

        // Return raw results - grouping is no longer needed with dual search strategy
        return results;
    }

    /**
     * Groups flat episode results into "Seasons" or "Series" folders.
     * Example: "Ratched S1 Ep 1", "Ratched S1 Ep 2" -> "Ratched S1"
     */
    private List<ParsedItem> groupEpisodesIntoSeasons(List<ParsedItem> rawItems) {
        if (rawItems.isEmpty())
            return rawItems;

        List<ParsedItem> grouped = new ArrayList<>();
        Map<String, ParsedItem> seasonMap = new java.util.LinkedHashMap<>(); // Preserve order
        List<ParsedItem> others = new ArrayList<>();

        for (ParsedItem item : rawItems) {
            // Keep "Next Page" separate
            if (item.getTitle().contains("Next Page")) {
                others.add(item);
                continue;
            }

            String title = item.getOriginalTitle(); // Use raw title for safer regex matching
            if (title == null)
                title = item.getTitle();

            // Regex to strip "Episode X" / "الحلقة X"
            // Improved Regex:
            // 1. (?i) Case insensitive
            // 2. \b Word boundary (Prevents matching "Exception" as "E")
            // 3. (?:...) Non-capturing group for keywords (Episode, Ep, E, الحلقه, حلقة)
            // 4. \s* Optional whitespace
            // 5. \d+ Must be followed by a number
            String cleanTitle = title.replaceAll("(?i)\\b(?:Episode|Ep|E|الحلقة|حلقة)\\s*\\d+.*", "").trim();

            // Remove trailing " - " or similar
            if (cleanTitle.endsWith("-"))
                cleanTitle = cleanTitle.substring(0, cleanTitle.length() - 1).trim();

            // If title becomes empty (rare), revert
            if (cleanTitle.isEmpty())
                cleanTitle = title;

            String itemKey = cleanTitle.toLowerCase(); // Lowercase key for grouping

            if (!seasonMap.containsKey(itemKey)) {
                ParsedItem folder = new ParsedItem();
                folder.setTitle("📂 " + cleanTitle); // Add folder icon for clarity (keep original casing for display)
                folder.setPageUrl(item.getPageUrl()); // Use FIRST episode link as entry
                folder.setType(MediaType.SEASON); // Treat as Season (Collection of Episodes)
                folder.setPosterUrl(item.getPosterUrl());
                folder.setYear(item.getYear());
                seasonMap.put(itemKey, folder);
            }
        }

        grouped.addAll(seasonMap.values());
        grouped.addAll(others);

        Log.d(TAG, "Grouped " + rawItems.size() + " items into " + grouped.size() + " seasons/groups.");
        return grouped;
    }

    @Override
    public ParsedItem parseDetailPage() {
        ParsedItem result = new ParsedItem();
        try {
            Document doc = Jsoup.parse(html);
            result.setPageUrl(getPageUrl());

            Log.d("FLOW", "ArabSeedParser.parseDetailPage TRACE:");
            Log.d("FLOW", "  - Page URL: " + getPageUrl());
            Log.d("FLOW", "  - Base URL resolved to: " + getBaseUrl());
            Log.d("FLOW", "  - HTML Length: " + (html != null ? html.length() : 0));
            Log.d("FLOW", "  - SourceItem Type: " + (sourceItem != null ? sourceItem.getType() : "NULL"));

            // Use sourceItem title if available
            if (sourceItem != null && sourceItem.getTitle() != null && !sourceItem.getTitle().isEmpty()) {
                result.setTitle(sourceItem.getTitle());
                result.setOriginalTitle(sourceItem.getTitle());
            } else {
                result.setTitle(doc.title());
            }

            // Poster Fallback
            Element posterElem = doc.selectFirst(".poster img, .image img, img[class*='poster']");
            if (posterElem != null) {
                String poster = posterElem.hasAttr("data-src") ? posterElem.attr("data-src")
                        : (posterElem.hasAttr("src") ? posterElem.attr("src") : posterElem.attr("data-lazy-src"));
                result.setPosterUrl(poster);
            } else if (sourceItem != null) {
                result.setPosterUrl(sourceItem.getPosterUrl());
            }

            // Description
            String description = "";
            Elements storyElems = doc.getElementsByClass("StoryLine");
            for (Element story : storyElems) {
                Elements p = story.getElementsByClass("descrip");
                if (!p.isEmpty()) {
                    description = p.text();
                    break;
                }
            }
            if (description.isEmpty()) {
                Element postStory = doc.selectFirst("[class*=post][class*=story]");
                if (postStory != null)
                    description = postStory.text();
            }
            result.setDescription(description);

            // Extract Rating, Year, Categories from Detail Page
            try {
                // Year
                Element yearLink = doc.selectFirst("a[href*='release-year'], .year, .date");
                if (yearLink != null) {
                    String yStr = yearLink.text().replaceAll("[^0-9]", "");
                    if (yStr.length() == 4)
                        result.setYear(Integer.parseInt(yStr));
                }

                // Rating
                Element rateElem = doc.selectFirst(".imdb, .rating, .rate, [class*='rate'], [class*='rating']");
                if (rateElem != null) {
                    String rStr = rateElem.text().replaceAll("[^0-9.]", "");
                    if (!rStr.isEmpty())
                        result.setRating(Float.parseFloat(rStr));
                }

                // Categories
                Elements catLinks = doc.select(
                        ".post__category a, a[href*='category'], a[href*='genre'], .cat-links a, .categories a");
                for (Element cat : catLinks) {
                    result.addCategory(cat.text().trim());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting extra metadata", e);
            }

            List<ParsedItem> subItems = new ArrayList<>();

            // 1. AJAX / JSON Support (Highest Priority)
            String rawJson = (html != null) ? html.trim() : "";
            if (rawJson.startsWith("{") && rawJson.contains("\"type\"")) {
                Log.d(TAG, "FLOW: AJAX/JSON Response. Parsing episodes.");
                parseJsonEpisodes(rawJson, subItems);
                result.setSubItems(subItems);
                result.setStatus(ParsedItem.ProcessStatus.SUCCESS);
                result.setType(MediaType.SERIES);
                return result;
            }

            // 2. Strict Type-Based Branching (Zero Detection)
            MediaType contextType = (sourceItem != null) ? sourceItem.getType() : null;
            String url = getPageUrl();

            if (contextType == MediaType.SERIES) {
                // Series Click -> Show Seasons
                Log.d(TAG, "FLOW: [SERIES] Clicked. Extracting Seasons.");
                extractSeasons(doc, subItems);
                if (subItems.isEmpty()) {
                    Log.d(TAG, "FLOW: No seasons found. Fallback to Episodes.");
                    extractEpisodes(doc, subItems);
                }
                result.setType(MediaType.SERIES);
            } else if (contextType == MediaType.SEASON) {
                // Season Click -> Show Episodes
                Log.d(TAG, "FLOW: [SEASON] Clicked. Extracting Episodes.");
                extractEpisodes(doc, subItems);
                result.setType(MediaType.SEASON);
            } else if (contextType == MediaType.EPISODE) {
                // Episode Click -> Show Servers
                Log.d(TAG, "FLOW: [EPISODE] Clicked. Extracting Servers.");
                extractServersAndWatchButtons(doc, subItems);

                // AGGRESSIVE REDIRECT: If no servers found but a Watch Page button exists,
                // follow it.
                boolean hasServers = false;
                ParsedItem navItem = null;
                for (ParsedItem item : subItems) {
                    if ("Server".equals(item.getQuality()))
                        hasServers = true;
                    if ("Navigation".equals(item.getQuality()))
                        navItem = item;
                }

                if (!hasServers && navItem != null) {
                    Log.d(TAG, "FLOW: [EPISODE] No servers found, but Watch Page exists. Redirecting...");
                    result.setStatus(ParsedItem.ProcessStatus.REDIRECT);
                    result.setStatusMessage(navItem.getPageUrl());
                    return result;
                }
                result.setType(MediaType.EPISODE);
            } else if (contextType == MediaType.FILM) {
                // Movie/Watch Page Click -> Show Servers
                Log.d(TAG, "FLOW: [FILM] Clicked. Extracting Servers.");
                extractServersAndWatchButtons(doc, subItems);

                // AGGRESSIVE REDIRECT: If no servers found but a Watch Page button exists,
                // follow it.
                boolean hasServers = false;
                ParsedItem navItem = null;
                for (ParsedItem item : subItems) {
                    if ("Server".equals(item.getQuality()))
                        hasServers = true;
                    if ("Navigation".equals(item.getQuality()))
                        navItem = item;
                }

                if (!hasServers && navItem != null) {
                    Log.d(TAG, "FLOW: [FILM] No servers found, but Watch Page exists. Redirecting...");
                    result.setStatus(ParsedItem.ProcessStatus.REDIRECT);
                    result.setStatusMessage(navItem.getPageUrl());
                    return result;
                }
                result.setType(MediaType.FILM);
            } else {
                // FALLBACK for Direct Links or Unknown Search Types
                Log.d(TAG, "FLOW: [UNKNOWN] Context. Performing Smart Scan.");
                extractServersAndWatchButtons(doc, subItems);
                extractEpisodes(doc, subItems);
                extractSeasons(doc, subItems);

                // Guess type for result based on content
                if (!subItems.isEmpty()) {
                    ParsedItem first = subItems.get(0);
                    if ("Server".equals(first.getQuality()))
                        result.setType(MediaType.EPISODE);
                    else if (first.getType() == MediaType.SEASON)
                        result.setType(MediaType.SERIES);
                    else
                        result.setType(MediaType.SERIES);
                }
            }

            // 3. Post-Processing: Redirects & Cleanup

            // Auto-Redirect if only one "Watch Page" button found (User convenience)
            if (subItems.size() == 1 && "Navigation".equals(subItems.get(0).getQuality())) {
                ParsedItem nav = subItems.get(0);
                Log.d(TAG, "FLOW: Single navigation button found. Redirecting to " + nav.getPageUrl());
                result.setStatus(ParsedItem.ProcessStatus.REDIRECT);
                result.setStatusMessage(nav.getPageUrl());
                return result;
            }

            // Remove redundant Watch Button if servers/episodes/seasons exist
            boolean hasContent = false;
            for (ParsedItem item : subItems) {
                if ("Server".equals(item.getQuality()) || item.getType() == MediaType.EPISODE
                        || item.getType() == MediaType.SEASON) {
                    hasContent = true;
                    break;
                }
            }

            if (hasContent) {
                java.util.Iterator<ParsedItem> it = subItems.iterator();
                while (it.hasNext()) {
                    ParsedItem item = it.next();
                    if ("Navigation".equals(item.getQuality())) {
                        it.remove();
                    }
                }
            }

            result.setSubItems(subItems);
            result.setStatus(
                    subItems.isEmpty() ? ParsedItem.ProcessStatus.EMPTY_ERROR : ParsedItem.ProcessStatus.SUCCESS);

            if (subItems.isEmpty()) {
                Log.e("FLOW", "ArabSeedParser TRACE RESULT: No sub-items found!");
                result.setStatusMessage("No content found for this link.");
            } else {
                Log.d("FLOW", "ArabSeedParser TRACE RESULT: Successfully found " + subItems.size() + " sub-items.");
            }

        } catch (Exception e) {
            Log.e("FLOW", "ArabSeedParser TRACE ERROR: Exception during parsing", e);
            result.setStatus(ParsedItem.ProcessStatus.EMPTY_ERROR);
            result.setStatusMessage("Parser Error: " + e.getMessage());
        }
        return result;
    }

    private void extractEpisodes(Document doc, List<ParsedItem> subItems) {
        // Fix: Always scan for episodes.
        // Whether we are on a Series Page, or an Episode Page (which has servers),
        // we always want to populate the episode list (siblings or children).
        Log.d(TAG, "Scanning for Episodes...");

        // Fix: Scope search to "Episodes" section to avoid "Recommended" or sidebars
        Elements episodeLinks = new Elements();

        // --- STRATEGY A: Specific Container (User Suggestion: [episode][section]) ---
        // Try specific classes that usually hold episodes
        Elements containers = doc.select(
                "div[class*='pisode'], div[class*='ontainerEpisodes'], .ContainerEpisodesList, .list--episodes");
        for (Element c : containers) {
            // CRITICAL: Skip Sidebars
            if (c.hasClass("Sidebar") || c.hasClass("sidebar") || c.parents().hasClass("Sidebar"))
                continue;

            Elements links = c.select("a");
            if (!links.isEmpty()) {
                episodeLinks = links;
                Log.d(TAG, "Strategy A: Found Episodes in container: " + c.className());
                break;
            }
        }

        // --- STRATEGY B: Header + Smart Sibling Scan (Refined) ---
        if (episodeLinks.isEmpty()) {
            Elements headers = doc.select("h2, h3, div.SectionTitle");
            for (Element header : headers) {
                if (header.text().contains("الحلقات") || header.text().contains("Seasons")) {
                    Log.d(TAG, "Strategy B: Found Header '" + header.text() + "'");
                    Log.d(TAG, "DEBUG HTML - Header: " + header.outerHtml());

                    // Check Direct Sibling
                    Element startNode = header.nextElementSibling();
                    if (startNode != null)
                        Log.d(TAG, "DEBUG HTML - Direct Sibling: " + startNode.outerHtml().substring(0,
                                Math.min(100, startNode.outerHtml().length())));

                    // If direct sibling is null or empty, check Parent's sibling (Wrapped Header
                    // case)
                    if (startNode == null || startNode.text().trim().isEmpty()) {
                        if (header.parent() != null) {
                            Log.d(TAG, "Strategy B: Direct sibling empty, checking parent's sibling...");
                            Log.d(TAG, "DEBUG HTML - Header Parent: " + header.parent().tagName() + " class="
                                    + header.parent().className());
                            startNode = header.parent().nextElementSibling();
                            if (startNode != null)
                                Log.d(TAG, "DEBUG HTML - Parent Sibling: " + startNode.outerHtml().substring(0,
                                        Math.min(100, startNode.outerHtml().length())));

                            // Fix: If Parent Sibling is just the "Left Header" (filters), go to GRANDPARENT
                            // sibling - Dynamic Check
                            if (startNode != null) {
                                String c = startNode.className();
                                if (c.contains("head") && c.contains("left")) {
                                    Log.d(TAG,
                                            "Strategy B: Parent Sibling is Header-Left (Filters). Checking Grandparent Sibling...");
                                    if (header.parent().parent() != null) {
                                        Log.d(TAG,
                                                "DEBUG HTML - Header Grandparent: "
                                                        + header.parent().parent().tagName() + " class="
                                                        + header.parent().parent().className());
                                        startNode = header.parent().parent().nextElementSibling();
                                        if (startNode != null)
                                            Log.d(TAG, "DEBUG HTML - Grandparent Sibling: " + startNode
                                                    .outerHtml()
                                                    .substring(0,
                                                            Math.min(100, startNode.outerHtml().length())));
                                    }
                                }
                            }
                        }
                    }

                    Element sibling = startNode;

                    // Scan siblings until we hit a "Stop" section
                    while (sibling != null) {
                        // Stop Conditions
                        if (sibling.tagName().matches("h[1-6]") || sibling.hasClass("SectionTitle")
                                || sibling.hasClass("Footer")) {
                            String hText = sibling.text();
                            if (hText.contains("اخري") || hText.contains("related")
                                    || hText.contains("التعليقات"))
                                break;
                        }
                        if (sibling.text().contains("مسلسلات اخري")
                                || sibling.text().contains("Related Stories"))
                            break;

                        Elements links = sibling.select("a");
                        episodeLinks.addAll(links);

                        sibling = sibling.nextElementSibling();
                        // Safety break to avoid infinite loops or massive pages
                        if (episodeLinks.size() > 500)
                            break;
                    }

                    if (!episodeLinks.isEmpty())
                        break;
                }
            }
        }

        // --- STRATEGY C: Breadcrumb Anchor (Last Resort - Scoped) ---
        if (episodeLinks.isEmpty()) {
            Elements crumbs = doc.select(".bread__crumbs, .Breadcrumbs");
            if (!crumbs.isEmpty()) {
                Log.d(TAG, "Strategy C: Using Breadcrumbs as Anchor...");
                Log.d(TAG, "DEBUG HTML - Crumbs: " + crumbs.first().outerHtml());

                Element sibling = crumbs.first().nextElementSibling();
                while (sibling != null) {
                    if (sibling.hasClass("Sidebar") || sibling.hasClass("Footer"))
                        break;
                    if (sibling.text().contains("مسلسلات اخري") || sibling.text().contains("التعليقات"))
                        break;

                    Elements links = sibling.select("a");
                    // Filter valid episode links only to avoid grabbing social buttons in main area
                    for (Element l : links) {
                        String t = l.text();
                        String h = l.attr("href");
                        if (h.contains("/episode") || t.contains("الحلقة")
                                || h.contains("%d8%ad%d9%84%d9%82%d8%a9")) {
                            episodeLinks.add(l);
                        }
                    }
                    sibling = sibling.nextElementSibling();
                }
            }
        }

        // --- STRATEGY D: Global Item Search (Broad) ---
        if (episodeLinks.isEmpty()) {
            Log.d(TAG, "Strategy D: Broad Search for Item Boxes...");
            Elements boxes = doc.select(".box__xs__2, [class*='box'], [class*='item'], [class*='post']");
            for (Element box : boxes) {
                Elements links = box.select("a");
                episodeLinks.addAll(links);
            }
        }

        // --- STRATEGY E: Total Fallback (Global Link Search) ---
        if (episodeLinks.isEmpty()) {
            Log.d(TAG, "Strategy E: Global Link Search...");
            episodeLinks = doc.select("a");
        }

        // --- DEBUG: FULL HTML DUMP IF FAILED ---
        if (episodeLinks.isEmpty()) {
            Log.e(TAG, "CRITICAL: NO EPISODES FOUND. DUMPING HTML...");
            String html = doc.outerHtml();
            int chunkSize = 3000;
            for (int i = 0; i < html.length(); i += chunkSize) {
                Log.d(TAG, "HTML_DUMP: " + html.substring(i, Math.min(html.length(), i + chunkSize)));
            }
        }

        int episodeCount = 0;

        for (Element link : episodeLinks) {
            String href = link.attr("href");
            String text = link.text();

            if (href == null || href.isEmpty())
                continue;
            if (href.equals(getPageUrl()))
                continue; // Skip self

            // Improved Episode Detection:
            // Check text for "الحلقة" (Episode) OR URL for "/episode" OR Encoded Arabic
            // "Halqa"
            boolean isEpisode = href.contains("/episode") ||
                    text.contains("الحلقة") ||
                    href.contains("%d8%ad%d9%84%d9%82%d8%a9");

            if (isEpisode) {
                Element img = link.selectFirst("img");
                String epTitle = text;
                if (img != null && img.hasAttr("alt")) {
                    epTitle = img.attr("alt");
                }
                if (epTitle.isEmpty())
                    epTitle = "Episode";

                // Clean title
                epTitle = epTitle.replace("مشاهدة", "").replace("تحميل", "").trim();

                ParsedItem ep = new ParsedItem();
                ep.setTitle(epTitle);
                ep.setPageUrl(href);
                ep.setType(MediaType.EPISODE);
                if (img != null)
                    ep.setPosterUrl(img.attr("src"));

                // Avoid duplicates
                boolean exists = false;
                for (ParsedItem existing : subItems) {
                    if (existing.getPageUrl().equals(href)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    ep.setTitle(cleanHtmlText(epTitle));
                    ep.setPageUrl(unquoteUrl(href));
                    subItems.add(ep);
                    episodeCount++;
                }
            }
        }
        Log.d(TAG, "Found " + episodeCount + " episodes.");

        // --- Universal Sorting Fix ---
        sortEpisodes(subItems);
    }

    /**
     * Universal helper to sort episodes numerically in ascending order.
     */
    private void sortEpisodes(List<ParsedItem> subItems) {
        if (subItems == null || subItems.size() < 2)
            return;

        boolean hasAnyEpisodes = false;
        for (ParsedItem item : subItems) {
            if (item.getType() == MediaType.EPISODE) {
                hasAnyEpisodes = true;
                break;
            }
        }

        if (hasAnyEpisodes) {
            try {
                final java.util.regex.Pattern pSort = java.util.regex.Pattern
                        .compile("(?i)(?:Episode|Ep|E|الحلقة|حلقة)\\s*(\\d+)");
                java.util.Collections.sort(subItems, (o1, o2) -> {
                    // Only sort if BOTH are episodes. Keep other items (like Full Series Page) at
                    // top.
                    if (o1.getType() == MediaType.EPISODE && o2.getType() == MediaType.EPISODE) {
                        java.util.regex.Matcher m1 = pSort.matcher(o1.getTitle() != null ? o1.getTitle() : "");
                        java.util.regex.Matcher m2 = pSort.matcher(o2.getTitle() != null ? o2.getTitle() : "");

                        int n1 = m1.find() ? Integer.parseInt(m1.group(1)) : 9999;
                        int n2 = m2.find() ? Integer.parseInt(m2.group(1)) : 9999;
                        return Integer.compare(n1, n2);
                    }
                    return 0;
                });
                Log.d(TAG, "Universal Sort: episodes arranged in ascending order.");
            } catch (Exception e) {
                Log.e(TAG, "Error sorting episodes: " + e.getMessage());
            }
        }
    }

    private void extractSeasons(Document doc, List<ParsedItem> subItems) {
        Log.d(TAG, "Scanning for Seasons...");
        Element seasonsList = doc.getElementById("seasons__list");
        if (seasonsList == null)
            return;

        String csrfToken = extractCsrfToken(doc);
        Log.d(TAG, "Found CSRF Token: " + csrfToken);

        Elements seasons = seasonsList.select("li[data-term]");
        for (Element season : seasons) {
            String seasonId = season.attr("data-term");
            String seasonName = season.text();
            if (seasonName.isEmpty())
                seasonName = "Season " + seasonId;

            ParsedItem item = new ParsedItem();
            item.setTitle("📁 " + seasonName);
            item.setType(MediaType.SEASON);
            // The AJAX endpoint for episodes - Dynamic resolution
            String baseUrl = getBaseUrl();
            item.setPageUrl(baseUrl + "/season__episodes/");
            item.setPostData("season_id=" + seasonId + "&csrf_token=" + csrfToken);
            item.setPosterUrl(sourceItem != null ? sourceItem.getPosterUrl() : null);

            // Avoid adding current season twice if it's already in the list
            // (Wait, seasons are distinct from episodes)
            subItems.add(item);
        }
    }

    private String extractCsrfToken(Document doc) {
        // Try meta tags
        Element meta = doc.selectFirst("meta[name=csrf_token]");
        if (meta == null)
            meta = doc.selectFirst("meta[name=csrf-token]");
        if (meta == null)
            meta = doc.selectFirst("meta[name=csrf-nonce]");
        if (meta != null)
            return meta.attr("content");

        // Try hidden inputs
        Element input = doc.selectFirst("input[name=csrf_token]");
        if (input == null)
            input = doc.selectFirst("input[name=csrf-token]");
        if (input == null)
            input = doc.selectFirst("input[name=_wpnonce]");
        if (input == null)
            input = doc.selectFirst("input[id=nonce]");
        if (input != null)
            return input.attr("value");

        // Try scripts (regex)
        String htmlStr = doc.html();
        // Standard csrf_token or nonce patterns
        String[] patterns = {
                "['\"]?csrf__token['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]",
                "['\"]?csrf_token['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]",
                "['\"]?csrf-token['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]",
                "['\"]?nonce['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]",
                "['\"]?_wpnonce['\"]?\\s*[:=]\\s*['\"]([^'\"]+)['\"]"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(htmlStr);
            if (m.find()) {
                String token = m.group(1);
                Log.d(TAG, "Regex matched pattern [" + pattern + "]. Found token: " + token);
                return token;
            }
        }

        Log.e(TAG, "CRITICAL: FAILED to find CSRF token in HTML scripts.");
        return "";
    }

    private void extractServersAndWatchButtons(Document doc, List<ParsedItem> subItems) {
        // 1. Check for Server List (Watch Page)
        Log.d(TAG, "Extracting Servers...");
        Log.d(TAG, "Extracting Servers...");
        Elements serverElems = doc.getElementsByClass("containerServers");
        if (serverElems.isEmpty())
            serverElems = doc.getElementsByClass("servers__list");
        Log.d(TAG, "Found " + serverElems.size() + " elements with class 'containerServers' or 'servers__list'");

        if (serverElems.isEmpty()) {
            serverElems = doc.select("li[data-post]");
            Log.d(TAG, "Found " + serverElems.size() + " elements with selector 'li[data-post]'");
        }

        // Fallback: Check for li containing "سيرفر" if standard classes missing
        if (serverElems.isEmpty()) {
            Log.d(TAG, "Standard selectors empty, checking for 'li' containing 'سيرفر'...");
            for (Element li : doc.select("li")) {
                if (li.text().contains("سيرفر"))
                    serverElems.add(li);
            }
            Log.d(TAG, "Found " + serverElems.size() + " fallback elements.");
        }

        if (!serverElems.isEmpty()) {
            for (Element serverContainer : serverElems) {
                // Sometimes the container itself is the item, sometimes it contains
                // li[data-link]
                Elements links = serverContainer.getElementsByAttribute("data-link");
                if (links.isEmpty() && serverContainer.hasAttr("data-link")) {
                    links.add(serverContainer);
                }

                // Fallback: If no data-link found, check for standard anchor tags
                if (links.isEmpty()) {
                    links = serverContainer.getElementsByTag("a");
                    Log.d(TAG, "No data-link found, checking for <a> tags. Found: " + links.size());
                }

                if (links.isEmpty()) {
                    Log.d(TAG, "Skipping server container (no links found): " + serverContainer.outerHtml());
                }

                for (Element linkElem : links) {
                    String serverUrl = linkElem.attr("data-link");
                    if (serverUrl.isEmpty()) {
                        serverUrl = linkElem.attr("href");
                    }
                    String serverName = linkElem.text();
                    if (serverName.isEmpty())
                        serverName = "Server";

                    // Extract specific title from span if available
                    Element span = linkElem.selectFirst("span");
                    if (span != null)
                        serverName = span.text();

                    // FILTER SPAM / ADS
                    if (serverName.contains("IPTV") || serverName.contains("اشترك") || serverName.contains("قناة")) {
                        Log.d(TAG, "Skipping spam item: " + serverName);
                        continue;
                    }

                    if (serverUrl != null && !serverUrl.isEmpty() && !serverUrl.contains(".wiki")) {
                        // Fix Relative URLs
                        if (!serverUrl.startsWith("http")) {
                            try {
                                // Simple concatenation if just starting with /
                                if (serverUrl.startsWith("/")) {
                                    java.net.URI base = new java.net.URI(getPageUrl());
                                    serverUrl = base.getScheme() + "://" + base.getHost() + serverUrl;
                                } else {
                                    serverUrl = getPageUrl() + serverUrl;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error resolving url: " + serverUrl, e);
                                // Fallback
                                if (getPageUrl().endsWith("/"))
                                    serverUrl = getPageUrl() + serverUrl;
                                else
                                    serverUrl = getPageUrl() + "/" + serverUrl;
                            }
                        }

                        // Append Referer (Fix for Access Denied)
                        // Use the current page as the referer, as ArabSeed servers check this
                        serverUrl = serverUrl + "|Referer=" + getPageUrl();

                        ParsedItem serverItem = new ParsedItem();
                        serverItem.setTitle(serverName);
                        serverItem.setPageUrl(serverUrl);
                        serverItem.setType(MediaType.SERVER);
                        serverItem.setQuality("Server"); // Mark as Server for Sniffer
                        subItems.add(serverItem);
                    }
                }
            }
        }

        // 2. Check for Watch Buttons (Main Page) -> Navigate to Watch Page
        if (subItems.isEmpty()) {
            Log.d(TAG, "No servers found yet. Checking for Watch Buttons...");
            Elements watchContainer = doc.getElementsByClass("WatchButtons");
            if (watchContainer.isEmpty())
                watchContainer = doc.select(".btton.watch__btn");

            Log.d(TAG, "Found " + watchContainer.size() + " watch button containers.");

            for (Element container : watchContainer) {
                // If the container matches the button selector, it MIGHT BE the link itself.
                if (container.tagName().equalsIgnoreCase("a")) {
                    // Handle direct link
                    String href = container.attr("href");
                    if (href != null && !href.isEmpty() && !href.equals("#")) {
                        ParsedItem watchItem = new ParsedItem();
                        watchItem.setTitle("Watch Page");
                        watchItem.setPageUrl(href);
                        watchItem.setType(MediaType.FILM);
                        watchItem.setQuality("Navigation");
                        subItems.add(watchItem);
                    }
                }

                // Standard Links (Children)
                Elements links = container.getElementsByTag("a");
                for (Element link : links) {
                    String href = link.attr("href");
                    if (href != null && !href.isEmpty() && !href.equals("#")) {
                        ParsedItem watchItem = new ParsedItem();
                        watchItem.setTitle("Watch Page");
                        watchItem.setPageUrl(href);
                        watchItem.setType(MediaType.FILM);
                        watchItem.setQuality("Navigation");
                        subItems.add(watchItem);
                    }
                }

                // Form POSTs (Reference: "new style")
                Elements forms = container.select("form");
                for (Element form : forms) {
                    Element hiddenInput = form.selectFirst("input[type='hidden']");
                    Element button = form.selectFirst("button");

                    if (hiddenInput != null && button != null) {
                        String action = form.attr("action");
                        String name = hiddenInput.attr("name");
                        String value = hiddenInput.val();

                        if (!action.isEmpty() && !name.isEmpty()) {
                            // Encode POST data into URL for the fetcher to handle
                            // Format: url|postParams=key:value
                            String postUrl = action + "|postParams=" + name + ":" + value;

                            ParsedItem watchItem = new ParsedItem();
                            watchItem.setTitle("Watch Page (POST)");
                            watchItem.setPageUrl(postUrl);
                            watchItem.setType(MediaType.FILM);
                            watchItem.setQuality("Navigation");
                            subItems.add(watchItem);
                        }
                    }
                }
            }
        }
    }

    private void extractParentSeriesLink(Document doc, List<ParsedItem> subItems) {
        // Fix: Use dynamic selector for breadcrumbs to avoid social links and handle
        // class variations
        // User instruction: div class contains [bread][crumbs] and url contains /selary
        Elements parentLinks = doc.select("div[class*='bread'][class*='rumbs'] a[href*='/selary/']");

        // Fallback for older pages or if /series/ is used instead of /selary/
        if (parentLinks.isEmpty()) {
            parentLinks = doc.select("div[class*='bread'][class*='rumbs'] a[href*='/series/']");
        }

        // Ultimate fallback (broad scan) ONLY if specific breadcrumb search fails
        if (parentLinks.isEmpty()) {
            parentLinks = doc.select("a[href*='/selary/']");
        }

        for (Element link : parentLinks) {
            String href = link.attr("href");
            if (href == null || href.isEmpty())
                continue;

            // SKIP generic or unrelated links
            if (href.equals(getPageUrl()))
                continue;

            // SKIP Social/Share links (Extra Safety)
            if (href.startsWith("whatsapp:") || href.startsWith("tg:") || href.startsWith("mailto:") ||
                    href.contains("facebook.com") || href.contains("twitter.com") ||
                    href.contains("t.me") || href.contains("telegram.me") ||
                    href.contains("api.whatsapp.com") || href.contains("wa.me") ||
                    href.contains("pinterest") || href.contains("linkedin") ||
                    href.contains("reddit.com") || href.contains("share"))
                continue;

            if (href.endsWith("/series/") || href.endsWith("/series"))
                continue; // Generic list
            if (href.contains("/category/"))
                continue;
            if (href.contains("/page/"))
                continue;
            if (href.length() < "https://a.asd.homes/series/x".length())
                continue; // Too short to be a slug

            // If it passes filters, it's likely the parent
            ParsedItem parent = new ParsedItem();
            String text = link.text();
            // Clean text
            if (text.isEmpty()) {
                // Try finding text in spans (breadcrumbs often use icons/spans)
                text = link.select("span").text();
            }

            // Fallback to H1 extraction if link text is still empty (Common in ArabSeed
            // breadcrumbs)
            if (text.isEmpty()) {
                Element h1 = doc.selectFirst("h1.post__name");
                if (h1 == null)
                    h1 = doc.selectFirst("h1");

                if (h1 != null) {
                    String h1Text = h1.text();
                    // Remove "Episode X" parts to get just the Series Name
                    // Patterns: "Name Season X Episode Y", "Name Episode Y"
                    // We want "Name Season X" usually

                    // Remove "Episode ..."
                    String[] splitEp = h1Text.split("(?i)Episode|الحلقة");
                    if (splitEp.length > 0) {
                        text = splitEp[0].trim();
                    } else {
                        text = h1Text;
                    }
                }
            }

            if (text.isEmpty())
                text = "Full Series Page"; // Ultimate fallback

            parent.setTitle("📂 FULL SERIES: " + text);
            parent.setPageUrl(href);
            parent.setType(MediaType.SERIES);
            parent.setQuality("parent_series"); // Flag for Activity
            subItems.add(0, parent); // Add to TOP

            // If we found a /selary/ link, it's definitely the one. Stop.
            if (href.contains("/selary/"))
                break;

            // If /series/, we keep looking just in case a better /selary/ one exists later,
            // UNLESS we are sure this is a breadcrumb.
            // For safety, let's break on the first good match to avoid clutter.
            break;
        }
    }

    private void parseJsonEpisodes(String json, List<ParsedItem> subItems) {
        try {
            Log.d(TAG, "Raw JSON (first 200 chars): " + json.substring(0, Math.min(200, json.length())));
            JSONObject obj = new JSONObject(json);
            String htmlData = "";
            if (obj.has("html")) {
                htmlData = obj.getString("html");
            } else if (obj.has("content")) {
                htmlData = obj.getString("content");
            }

            if (!htmlData.isEmpty()) {
                Log.d(TAG, "Extracted htmlData (first 200 chars): "
                        + htmlData.substring(0, Math.min(200, htmlData.length())));

                // ArabSeed sometimes sends entity-encoded HTML inside JSON (e.g. &lt;a
                // href=...)
                // We must unescape it before parsing with Jsoup.
                if (htmlData.contains("&lt;") || htmlData.contains("&gt;")) {
                    Log.d(TAG, "Unescaping entity-encoded HTML fragment...");
                    htmlData = org.jsoup.parser.Parser.unescapeEntities(htmlData, true);
                    Log.d(TAG, "Unescaped htmlData (first 200 chars): "
                            + htmlData.substring(0, Math.min(200, htmlData.length())));
                }

                // Remove literal double-escaped slashes if they persist (Legacy check)
                htmlData = htmlData.replace("\\/", "/");

                Log.d(TAG, "Parsing JSON Episode HTML fragment (Length: " + htmlData.length() + ")");
                // Use parseBodyFragment for fragments to avoid <html><head><body> wrap issues
                Document fragmentDoc = Jsoup.parseBodyFragment(htmlData);

                // Log if we found any <a> tags at all in the fragment
                Elements allLinks = fragmentDoc.select("a");
                Log.d(TAG, "Total <a> tags found in fragment before filtering: " + allLinks.size());
                if (allLinks.size() > 0) {
                    Log.d(TAG, "First <a> tag: " + allLinks.first().outerHtml());
                } else {
                    Log.w(TAG, "NO <a> TAGS FOUND IN FRAGMENT. BODY HTML: " + fragmentDoc.body().html());
                }

                extractEpisodes(fragmentDoc, subItems);
            } else {
                Log.w(TAG, "JSON response received but no 'html' or 'content' field found. Full JSON: " + json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse JSON episodes: " + e.getMessage());
            Log.e(TAG, "JSON suspected of error: " + json);
        }
    }

    private String cleanHtmlText(String text) {
        if (text == null)
            return "";
        // 1. Parse as HTML to strip all tags (SVG, b, span, etc.) and unescape entities
        String cleaned = Jsoup.parse(text).text();
        // 2. Remove non-breaking spaces and other junk
        cleaned = cleaned.replace("\u00A0", " ")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", "")
                .replace("\\r", "")
                .replace("\\n", "")
                .replace("\\t", "");
        return cleaned.trim();
    }

    private String unquoteUrl(String url) {
        if (url == null)
            return "";
        String cleaned = url.trim();

        // 1. Unescape JSON-style backslashes
        cleaned = cleaned.replace("\\\"", "\"").replace("\\/", "/").replace("\\", "");

        // 2. Remove literal quotes if they surround the URL
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        } else if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }
}
