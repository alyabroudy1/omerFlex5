package com.omarflex5.data.scraper.parsers;

import android.util.Log;

import com.omarflex5.data.local.entity.MediaType;
import com.omarflex5.data.scraper.BaseHtmlParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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
                    Element linkElem = li.getElementsByAttribute("href").first();
                    if (linkElem == null)
                        continue;

                    String url = linkElem.attr("href");

                    Element imgElem = li.getElementsByAttribute("data-src").first();
                    if (imgElem == null)
                        continue;

                    String poster = imgElem.attr("data-src");
                    String title = imgElem.attr("alt");

                    if (title == null || title.isEmpty() || title.contains("تحميل")) {
                        continue;
                    }

                    title = title.replace("مسلسل", "").trim();

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
                        // Adopted from ArabSeedServer.java: Filter out non-video content
                        if (catText.contains("اغاني") ||
                                catText.contains("كمبيوتر") ||
                                catText.contains("موبايلات")) {
                            continue;
                        }

                        if (catText.contains("مسلسل"))
                            isSeries = true;
                    }

                    // Also check URL/Title
                    MediaType type = MediaType.FILM;
                    if (isSeries || url.contains("/series") || url.contains("مسلسل") || title.contains("مسلسل")) {
                        type = MediaType.SERIES;
                    } else if (url.contains("/episode") || title.contains("حلقة")) {
                        type = MediaType.EPISODE;
                    }

                    ParsedItem item = new ParsedItem();
                    item.setTitle(cleanTitle(title));
                    item.setOriginalTitle(title);
                    item.setPageUrl(url);
                    item.setPosterUrl(poster);
                    item.setType(type);
                    item.setYear(extractYear(title));

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

            // Use sourceItem title if available (passed from clicked item)
            // This is often cleaner and matches the user's intent better.
            if (sourceItem != null && sourceItem.getTitle() != null && !sourceItem.getTitle().isEmpty()) {
                // Prefer source title, but keep original if needed?
                // Actually, let's use the source title for strict type checking as it likely
                // contains "Episode X".
                result.setTitle(sourceItem.getTitle());
                result.setOriginalTitle(sourceItem.getTitle());
            } else {
                result.setTitle(doc.title());
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

            List<ParsedItem> subItems = new ArrayList<>();

            // 1. Check for Server List (Watch Page) & Watch Buttons
            extractServersAndWatchButtons(doc, subItems);

            // --- USER FLOW IMPLEMENTATION: "Search -> Episode -> Redirect to Series" ---
            Log.d(TAG, "Source Item Context: " + (sourceItem != null ? sourceItem.getType() : "null"));

            if (sourceItem != null && sourceItem.getType() == MediaType.SEASON) {
                Log.d(TAG, "Entered via SEASON context. Checking for Parent Series Link to redirect...");
                List<ParsedItem> seriesLinks = new ArrayList<>();
                extractParentSeriesLink(doc, seriesLinks);

                if (!seriesLinks.isEmpty()) {
                    ParsedItem parent = seriesLinks.get(0);
                    Log.d(TAG, "Found Parent Series Link: " + parent.getPageUrl() + ". Redirecting...");

                    // Force Redirect
                    result.setStatus(ParsedItem.ProcessStatus.REDIRECT);
                    result.setStatusMessage(parent.getPageUrl());
                    return result;
                } else {
                    Log.d(TAG,
                            "No Parent Series Link found in breadcrumbs/header. Falling back to local Episode extraction.");
                    // Debug: Dump breadcrumbs to see why we missed it
                    Elements crumbs = doc.select("div[class*='bread'][class*='rumbs']");
                    Log.d(TAG, "DEBUG: Breadcrumbs HTML: " + crumbs.outerHtml());
                }
            } else {
                Log.d(TAG, "Not a SEASON context (or null). Skipping Redirect check.");
            }

            // 2b. Check for Parent Series Link (Series Discovery) - Keep disabled for
            // normal flow
            // extractParentSeriesLink(doc, subItems);

            // 3. Check for Episodes (Series)

            // 3. Check for Episodes (Series)
            extractEpisodes(doc, subItems);

            // Logic to determine Page Type & List Content
            boolean hasServers = false;
            boolean hasEpisodes = false;

            for (ParsedItem item : subItems) {
                if (item.getQuality() != null && item.getQuality().equals("Server")) {
                    hasServers = true;
                }
                if (item.getType() == MediaType.EPISODE) {
                    hasEpisodes = true;
                }
            }

            // Streamline: Remove "Watch Page" if explicit SERVERS exist.
            // If we only found Episodes (siblings) but no servers, we MUST keep the
            // "Navigation" item
            // (the Watch Button) because that's likely the link to the actual watch page.
            if (hasServers) {
                List<ParsedItem> toRemove = new ArrayList<>();
                for (ParsedItem item : subItems) {
                    if (item.getQuality() != null && item.getQuality().equals("Navigation")) {
                        toRemove.add(item);
                    }
                }
                subItems.removeAll(toRemove);
            }

            // Fix: If we are on an Episode Page (hasServers), but we also found siblings
            // (hasEpisodes),
            // we MUST include the CURRENT page as an episode in the list so the user can
            // see/click it.
            // User reported "First episode is missing".
            if (hasServers && hasEpisodes) {
                boolean selfFound = false;
                String currentUrl = getPageUrl();
                // Check if current URL is in the list
                for (ParsedItem item : subItems) {
                    if (item.getType() == MediaType.EPISODE && item.getPageUrl().equals(currentUrl)) {
                        selfFound = true;
                        break;
                    }
                }

                if (!selfFound) {
                    // Create "Self" item
                    ParsedItem self = new ParsedItem();
                    String pageTitle = result.getTitle();

                    // Cleanup Title: Extract "Episode X" from full title
                    // e.g. "Series Name Season 1 Episode 4 Translated" -> "Episode 4"
                    java.util.regex.Pattern p = java.util.regex.Pattern
                            .compile("(?i)(?:Episode|Ep|E|الحلقة|حلقة)\\s*(\\d+)");
                    java.util.regex.Matcher m = p.matcher(pageTitle);
                    if (m.find()) {
                        self.setTitle("الحلقة " + m.group(1)); // "Episode 4"
                    } else {
                        self.setTitle(pageTitle);
                    }

                    self.setPageUrl(currentUrl);
                    self.setType(MediaType.EPISODE);
                    self.setPosterUrl(result.getPosterUrl());
                    subItems.add(0, self); // Add to top
                }
            }

            // DO NOT group episodes in Detail Page. We want a flat list of playable
            // episodes.
            result.setSubItems(subItems);

            // Determine Result Type based on Page Title (Strict Workflow)
            // If the title contains "Episode" or "Ring", it is an EPISODE page.
            // This ensures we report "No Servers Found" instead of causing a loop by
            // treating it as a Series.
            String strictTitle = result.getTitle();
            boolean isEpisodePage = false;
            if (strictTitle != null) {
                java.util.regex.Pattern pType = java.util.regex.Pattern
                        .compile("(?i)(?:Episode|Ep|E|الحلقة|حلقة)\\s*(\\d+)");
                if (pType.matcher(strictTitle).find()) {
                    isEpisodePage = true;
                }
            }

            if (isEpisodePage || hasServers) {
                result.setType(MediaType.EPISODE);

                // STRICT WORKFLOW: If it is an EPISODE, the list MUST NOT contain other
                // episodes (siblings).
                // It must only contain SERVERS.
                // Filter out any items that are likely sibling episodes to prevent "Looping" or
                // confusion.
                // We keep "Self" (if it exists) or Servers/Navigation items.
                if (result.getSubItems() != null) {
                    java.util.Iterator<ParsedItem> it = result.getSubItems().iterator();
                    while (it.hasNext()) {
                        ParsedItem item = it.next();
                        // Remove sibling episodes to enforce strict "Server Only" list
                        // If no servers are found, this results in an empty list -> Toast.
                        if (item.getType() == MediaType.EPISODE) {
                            it.remove();
                        }
                    }
                }

                // CRITICAL DEBUG: If we are on an Episode Page but found NO servers (and thus
                // cleared the list),
                // the user wants to see the HTML to know WHY.
                if (!hasServers) {
                    Log.e(TAG,
                            "CRITICAL FAILURE: Identified as Episode Page but found NO SERVERS. Dumping HTML for debugging...");
                    String html = doc.html();
                    // Split into 4000 char chunks to avoid Logcat truncation
                    for (int i = 0; i < html.length(); i += 4000) {
                        int end = Math.min(i + 4000, html.length());
                        Log.e("HTML_DUMP", html.substring(i, end));
                    }
                }
            } else if (hasEpisodes) {
                result.setType(MediaType.SERIES);
            } else {
                result.setType(MediaType.FILM);
            }

            Log.d(TAG, "Final Page Type: " + result.getType() + " | HasServers: " + hasServers + " | HasEpisodes: "
                    + hasEpisodes + " | IsEpisodePage: " + isEpisodePage);

            // --- LOGIC REFINEMENT FOR USER REQUESTS ---

            // 1. Fix "Season/Series" Page: Remove "Watch Page" button if we have a list of
            // episodes.
            // valid only if we are NOT on an episode page (where episodes are just
            // siblings).
            if (hasEpisodes && !isEpisodePage && !hasServers) {
                List<ParsedItem> toRemove = new ArrayList<>();
                for (ParsedItem item : subItems) {
                    if ("Navigation".equals(item.getQuality())) {
                        toRemove.add(item);
                    }
                }
                subItems.removeAll(toRemove);
            }

            // 2. Fix "Episode" Page: Auto-Redirect to Watch Page if found (and no servers).
            // This replaces the "Show Watch Button" behavior with "Go Fetch Servers".
            if (isEpisodePage && !hasServers) {
                // Find first Navigation item
                for (ParsedItem item : subItems) {
                    if ("Navigation".equals(item.getQuality())) {
                        result.setStatus(ParsedItem.ProcessStatus.REDIRECT);
                        result.setStatusMessage(item.getPageUrl());
                        return result; // Force Redirect immediately
                    }
                }

                // If we are here, we are on an episode page, no servers, no navigation link.
                // We likely have the sibling list still (if we didn't filter it yet).
                // Let's filter siblings now to ensure we show the user a clean specific error.
                if (result.getSubItems() != null) {
                    java.util.Iterator<ParsedItem> it = result.getSubItems().iterator();
                    while (it.hasNext()) {
                        ParsedItem item = it.next();
                        if (item.getType() == MediaType.EPISODE) {
                            it.remove();
                        }
                    }
                }
            }

            // --- SORTING LOGIC: Ascending Order (Ep 1, 2, 3...) ---
            // Fix UI mismatch where Index 1 != Episode 4.
            if (hasEpisodes && subItems != null && subItems.size() > 1 && !isEpisodePage) {
                try {
                    final java.util.regex.Pattern pSort = java.util.regex.Pattern
                            .compile("(?i)(?:Episode|Ep|E|الحلقة|حلقة)\\s*(\\d+)");
                    java.util.Collections.sort(subItems, (o1, o2) -> {
                        if (o1.getType() == MediaType.EPISODE && o2.getType() == MediaType.EPISODE) {
                            java.util.regex.Matcher m1 = pSort.matcher(o1.getTitle() != null ? o1.getTitle() : "");
                            java.util.regex.Matcher m2 = pSort.matcher(o2.getTitle() != null ? o2.getTitle() : "");

                            int n1 = m1.find() ? Integer.parseInt(m1.group(1)) : 9999;
                            int n2 = m2.find() ? Integer.parseInt(m2.group(1)) : 9999;
                            return Integer.compare(n1, n2);
                        }
                        return 0;
                    });
                    Log.d(TAG, "Sorted episodes in ascending order.");
                } catch (Exception e) {
                    Log.e(TAG, "Error sorting episodes: " + e.getMessage());
                }
            }
            // --- SEPARATION OF CONCERNS LOGIC ---

            // 1. Check for REDIRECT (Single Navigation Item)
            if (result.getStatus() == ParsedItem.ProcessStatus.SUCCESS && result.getSubItems() != null
                    && result.getSubItems().size() == 1) {
                ParsedItem single = result.getSubItems().get(0);
                if ("Navigation".equals(single.getQuality())) {
                    result.setStatus(ParsedItem.ProcessStatus.REDIRECT);
                    result.setStatusMessage(single.getPageUrl());
                    return result; // Return immediately
                }
            }

            // 2. Check for EMPTY_ERROR
            if (result.getSubItems() == null || result.getSubItems().isEmpty()) {
                if (result.getType() == MediaType.EPISODE || result.getType() == MediaType.FILM) {
                    result.setStatus(ParsedItem.ProcessStatus.EMPTY_ERROR);
                    result.setStatusMessage("No Watch Servers Found");
                } else {
                    result.setStatus(ParsedItem.ProcessStatus.EMPTY_ERROR);
                    result.setStatusMessage("No Episodes Found");
                }
            }

            for (ParsedItem sub : result.getSubItems()) {
                Log.d(TAG, "SubItem: " + sub.getTitle() + " | Type: " + sub.getType() + " | URL: " + sub.getPageUrl());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing detail page", e);
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
                    subItems.add(ep);
                    episodeCount++;
                }
            }
        }
        Log.d(TAG, "Found " + episodeCount + " episodes.");
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
}
