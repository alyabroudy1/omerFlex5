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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OldAkwamParser extends BaseHtmlParser {

    private static final String TAG = "OldAkwamParser";

    public OldAkwamParser(String html, String pageUrl) {
        super(html, pageUrl);
    }

    @Override
    public List<ParsedItem> parseSearchResults() {
        List<ParsedItem> results = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            // From OldAkwamServer.java: elements defined by class "tags_box"
            Elements boxes = doc.getElementsByClass("tags_box");

            for (Element box : boxes) {
                try {
                    Element linkTag = box.getElementsByTag("a").first();
                    if (linkTag == null)
                        continue;

                    String href = linkTag.attr("href");

                    // Filter out unwanted types
                    if (href.contains("لعبة") || href.contains("كورس") || href.contains("تحديث")) {
                        continue;
                    }

                    // Image is in style attribute: style="background-image:url(http...)"
                    String posterUrl = "";
                    Element imgDiv = box.getElementsByTag("div").first();
                    if (imgDiv != null) {
                        String style = imgDiv.attr("style");
                        if (style.contains("url(")) {
                            int start = style.indexOf("url(") + 4;
                            int end = style.indexOf(")", start);
                            if (end > start) {
                                posterUrl = style.substring(start, end).replace("\"", "").replace("'", "");
                            }
                        }
                    }

                    Element h1 = box.getElementsByTag("h1").first();
                    String title = (h1 != null) ? h1.text() : "";

                    ParsedItem item = new ParsedItem();
                    item.setTitle(title);
                    item.setPageUrl(fixUrl(href));
                    item.setPosterUrl(posterUrl);

                    // Determine type based on URL or Title
                    if (href.contains("/series") || href.contains("مسلسل") || title.contains("مسلسل")) {
                        item.setType(MediaType.SERIES);
                    } else if (href.contains("/movie") || href.contains("فيلم") || title.contains("فيلم")) {
                        item.setType(MediaType.FILM);
                    } else {
                        item.setType(MediaType.FILM); // Default
                    }

                    results.add(item);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing item", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing search results", e);
        }
        return results;
    }

    @Override
    public ParsedItem parseDetailPage() {
        ParsedItem result = new ParsedItem();
        try {
            Document doc = Jsoup.parse(html);

            // Basic Info
            String rawTitle = doc.title();
            // Try to find specific description
            Element descEl = doc.getElementsByClass("sub_desc").first();
            String description = (descEl != null) ? descEl.text() : "";

            result.setTitle(rawTitle);
            result.setDescription(description);
            result.setPageUrl(getPageUrl());

            // Determine Type
            String url = getPageUrl();
            if (url.contains("/series") || url.contains("مسلسل")) {
                result.setType(MediaType.SERIES);
            } else if (url.contains("/movie") || url.contains("فيلم")) {
                result.setType(MediaType.FILM);
            } else if (url.contains("/episode") || url.contains("حلقة")) {
                result.setType(MediaType.EPISODE);
            }

            // Sub-items list
            List<ParsedItem> subItems = new ArrayList<>();

            // 1. Check for Episodes (sub_episode_links)
            Elements episodeDivs = doc.getElementsByClass("sub_episode_links");
            for (Element div : episodeDivs) {
                Element link = div.getElementsByTag("a").first();
                Element titleTag = div.getElementsByTag("h2").first();

                if (link != null && titleTag != null) {
                    String epTitle = titleTag.text();
                    String epUrl = link.attr("href");

                    if (epUrl == null || epUrl.length() < 5)
                        continue;

                    ParsedItem ep = new ParsedItem();
                    ep.setTitle(epTitle);
                    ep.setPageUrl(fixUrl(epUrl));
                    ep.setType(MediaType.EPISODE);
                    subItems.add(ep);
                }
            }

            // 2. Check for Direct/Resolution Links (sub_direct_links)
            Elements directDivs = doc.getElementsByClass("sub_direct_links");
            for (Element div : directDivs) {
                Element link = div.getElementsByTag("a").first();
                // Element nameDiv = div.getElementsByClass("sub_file_title").first(); // Often
                // cleaner
                String name = div.text(); // Fallback

                if (div.getElementsByClass("sub_file_title").first() != null) {
                    name = div.getElementsByClass("sub_file_title").first().text();
                }

                if (link != null) {
                    String resUrl = link.attr("ng-href");
                    if (resUrl == null || resUrl.isEmpty()) {
                        resUrl = link.attr("href");
                    }

                    if (name.contains("للتصميم الجديد")) {
                        // Skip "New Design" links if irrelevant or broken
                        continue;
                    }

                    ParsedItem res = new ParsedItem();
                    res.setTitle(name);
                    res.setPageUrl(fixUrl(resUrl));
                    res.setType(MediaType.FILM); // Resolutions look like files

                    // Extract Quality from name if possible
                    if (name.contains("1080"))
                        res.setQuality("1080p");
                    else if (name.contains("720"))
                        res.setQuality("720p");
                    else if (name.contains("480"))
                        res.setQuality("480p");
                    else
                        res.setQuality("Unknown");

                    // Check if it's a direct download link immediately
                    if (resUrl.endsWith(".mp4") || resUrl.endsWith(".mkv")) {
                        res.setQuality("Direct");
                    } else if (resUrl.contains("/download/")) {
                        // Likely needs 1 more step
                        res.setTitle("Go to Download (" + res.getQuality() + ")");
                    }

                    subItems.add(res);
                }
            }

            // 3. Fallback: Check for standard Akwam download buttons if structure matches
            // new Akwam
            if (subItems.isEmpty()) {
                // Try parsing as "Link Page" or "Download Page" similar to AkwamParser
                // Check for "Go to Download" button usually in 'a.link_button' or similar
                ParsedItem nextStep = parseLinkPage(doc);
                if (nextStep != null) {
                    subItems.add(nextStep);
                } else {
                    ParsedItem direct = parseDownloadPage(doc);
                    if (direct != null) {
                        subItems.add(direct);
                    }
                }
            }

            result.setSubItems(subItems);

        } catch (Exception e) {
            Log.e(TAG, "Error parsing detail page", e);
        }
        return result;
    }

    // Helper to handle intermediate "Link" pages
    private ParsedItem parseLinkPage(Document doc) {
        // Look for the "Go to Download" button
        // In "Old Akwam", this might be different.
        // Based on reference: unauth_capsule clearfix -> a[ng-href]
        // Or standard Jsoup:

        Elements capsules = doc.getElementsByClass("unauth_capsule");
        for (Element cap : capsules) {
            Element link = cap.getElementsByTag("a").first();
            if (link != null) {
                // Check ng-href or href
                String href = link.attr("href");
                if (href.isEmpty())
                    href = link.attr("ng-href");

                if (href != null && href.contains("download")) {
                    ParsedItem item = new ParsedItem();
                    item.setTitle("Go to Download");
                    item.setPageUrl(fixUrl(href));
                    item.setType(MediaType.FILM);
                    return item;
                }
            }
        }
        return null;
    }

    // Helper to handle final "Download" pages
    private ParsedItem parseDownloadPage(Document doc) {
        // Look for direct link
        // Reference: class="download_button"
        Element downloadBtn = doc.getElementsByClass("download_button").first();
        if (downloadBtn != null) {
            String href = downloadBtn.attr("href");
            if (href != null && (href.contains("download") || href.contains(".link") || href.endsWith(".mp4"))) {
                ParsedItem item = new ParsedItem();
                item.setTitle("Direct Link");
                item.setPageUrl(fixUrl(href));
                item.setQuality("Direct");
                item.setType(MediaType.FILM);
                return item;
            }
        }
        return null;
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty())
            return "";
        if (url.startsWith("http"))
            return url;

        // Old Akwam typically shares base with Akwam
        String domain = "https://akwam.cc";
        if (getPageUrl() != null && getPageUrl().startsWith("http")) {
            try {
                java.net.URL u = new java.net.URL(getPageUrl());
                domain = u.getProtocol() + "://" + u.getHost();
            } catch (Exception e) {
            }
        }

        if (!url.startsWith("/"))
            return domain + "/" + url;
        return domain + url;
    }
}
