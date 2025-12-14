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

        // Group extracted episodes into Seasons/Series
        return groupEpisodesIntoSeasons(results);
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

            String title = item.getTitle();
            // Regex to strip "Episode X" / "الحلقة X"
            // Patterns: "Episode \d+", "الحلقة \d+", "Ep \d+"
            String cleanTitle = title.replaceAll("(?i)\\s*(?:Episode|Ep|الحلقة|حلقة)\\s*\\d+.*", "").trim();

            // Remove trailing " - " or similar
            if (cleanTitle.endsWith("-"))
                cleanTitle = cleanTitle.substring(0, cleanTitle.length() - 1).trim();

            // If title becomes empty (rare), revert
            if (cleanTitle.isEmpty())
                cleanTitle = title;

            if (!seasonMap.containsKey(cleanTitle)) {
                ParsedItem folder = new ParsedItem();
                folder.setTitle("📂 " + cleanTitle); // Add folder icon for clarity
                folder.setPageUrl(item.getPageUrl()); // Use FIRST episode link as entry
                folder.setType(MediaType.SERIES); // Treat as Series Folder
                folder.setPosterUrl(item.getPosterUrl());
                folder.setYear(item.getYear());
                seasonMap.put(cleanTitle, folder);
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
            result.setTitle(doc.title());
            result.setPageUrl(getPageUrl());

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

            // 1. Check for Server List (Watch Page)
            Elements serverElems = doc.getElementsByClass("containerServers");
            if (serverElems.isEmpty())
                serverElems = doc.select("li[data-post]");

            // Fallback: Check for li containing "سيرفر" if standard classes missing
            if (serverElems.isEmpty()) {
                for (Element li : doc.select("li")) {
                    if (li.text().contains("سيرفر"))
                        serverElems.add(li);
                }
            }

            if (!serverElems.isEmpty()) {
                for (Element serverContainer : serverElems) {
                    // Sometimes the container itself is the item, sometimes it contains
                    // li[data-link]
                    Elements links = serverContainer.getElementsByAttribute("data-link");
                    if (links.isEmpty() && serverContainer.hasAttr("data-link")) {
                        links.add(serverContainer);
                    }

                    for (Element linkElem : links) {
                        String serverUrl = linkElem.attr("data-link");
                        String serverName = linkElem.text();
                        if (serverName.isEmpty())
                            serverName = "Server";

                        // Extract specific title from span if available
                        Element span = linkElem.selectFirst("span");
                        if (span != null)
                            serverName = span.text();

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
                            serverItem.setType(MediaType.FILM);
                            serverItem.setQuality("Server"); // Mark as Server for Sniffer
                            subItems.add(serverItem);
                        }
                    }
                }
            }

            // 2. Check for Watch Buttons (Main Page) -> Navigate to Watch Page
            if (subItems.isEmpty()) {
                Elements watchContainer = doc.getElementsByClass("WatchButtons");
                if (watchContainer.isEmpty())
                    watchContainer = doc.select(".btton.watch__btn");

                for (Element container : watchContainer) {
                    // Standard Links
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

            // 2b. Check for Parent Series Link (Series Discovery)
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

            // 3. Check for Episodes (Series)
            // Fix: If we are on a Series Page (/selary/ or /series/), we MUST look for
            // episodes
            // even if subItems (like parent link) are found.
            boolean isSeriesPage = getPageUrl().contains("/selary/") || getPageUrl().contains("/series/");

            if (subItems.isEmpty() || isSeriesPage) {
                Log.d(TAG, "Scanning for Episodes (IsSeriesPage: " + isSeriesPage + ")...");

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

            result.setSubItems(subItems);

            // Set type
            if (!subItems.isEmpty() && subItems.get(0).getType() == MediaType.EPISODE) {
                result.setType(MediaType.SERIES);
            } else {
                result.setType(MediaType.FILM);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing detail page", e);
        }
        return result;
    }
}
