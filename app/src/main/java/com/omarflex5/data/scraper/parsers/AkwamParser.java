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

public class AkwamParser extends BaseHtmlParser {

    private static final String TAG = "AkwamParser";

    public AkwamParser(String html, String pageUrl) {
        super(html, pageUrl);
    }

    public AkwamParser(String html) {
        this(html, "");
    }

    @Override
    public List<ParsedItem> parseSearchResults() {
        List<ParsedItem> items = new ArrayList<>();
        try {
            Log.d(TAG, "Parsing Search Results. HTML Length: " + html.length());
            Document doc = Jsoup.parse(html);
            // From AkwamServer.java: elements defined by class "entry-box"
            Elements links = doc.getElementsByClass("entry-box");
            Log.d(TAG, "Found entry-box elements: " + links.size());

            for (Element link : links) {
                Elements linkUrlElements = link.getElementsByClass("box");
                if (linkUrlElements.isEmpty())
                    continue;

                String href = linkUrlElements.attr("href");
                // Log.d(TAG, "Found Link: " + href);

                // From reference: check if it's a valid media link
                if (!href.contains("/movie") && !href.contains("/series") && !href.contains("/episode")) {
                    Log.d(TAG, "Skipping invalid link type: " + href);
                    continue;
                }

                Elements titleElem = link.getElementsByAttribute("src");
                String title = "";
                String poster = "";
                if (!titleElem.isEmpty()) {
                    title = titleElem.attr("alt");
                    poster = titleElem.attr("data-src");
                    if (poster.isEmpty())
                        poster = titleElem.attr("src");
                }

                if (!href.isEmpty() && !title.isEmpty()) {
                    MediaType type = detectType(href, title);
                    Log.d(TAG, "Parsed Item: " + title + " | Type: " + type + " | URL: " + href);

                    ParsedItem item = new ParsedItem()
                            .setTitle(cleanTitle(title))
                            .setOriginalTitle(title)
                            .setPageUrl(href)
                            .setPosterUrl(poster)
                            .setType(type);

                    item.setYear(extractYear(title));

                    // Akwam Categories in Search (typically inside .box or siblings)
                    Elements genres = link.select(".genres a, .categories a");
                    for (Element genre : genres) {
                        item.addCategory(genre.text().trim());
                    }
                    items.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing search results", e);
        }
        Log.d(TAG, "Total items parsed: " + items.size());
        return items;
    }

    @Override
    public ParsedItem parseDetailPage() {
        ParsedItem result = new ParsedItem();
        try {
            Document doc = Jsoup.parse(html);
            String url = getPageUrl();
            result.setPageUrl(url); // CRITICAL: Preserve URL for DB Sync

            // 1. Basic Info - From AkwamServer.java logic
            // Attempt to find poster/background
            String poster = "";
            String bgImage = "";

            // Reference logic: iterates .row.py-4 images
            Elements imageDivs = doc.getElementsByClass("row py-4");
            for (Element imageDiv : imageDivs) {
                Elements imageLinks = imageDiv.getElementsByAttribute("href");
                for (Element imagelink : imageLinks) {
                    String linkHref = imagelink.attr("href");
                    if (linkHref.contains("/uploads/")) {
                        bgImage = linkHref;
                        if (poster.isEmpty())
                            poster = bgImage; // Use as poster if none
                    }
                }
            }
            // Fallback if strict loop didn't find it - try standard meta tags
            if (poster.isEmpty()) {
                poster = extractFirst("<meta property=\"og:image\" content=\"([^\"]+)\"", 1);
            }

            // Description: h2 -> p
            String desc = "";
            Elements decDivs = doc.select("h2");
            for (Element div : decDivs) {
                desc = div.getElementsByTag("p").text();
                if (!desc.isEmpty())
                    break;
            }
            if (desc.isEmpty()) {
                desc = doc.select(".story p").text();
            }

            // Title: usually in h1 or derived from valid element
            String rawTitle = doc.title();
            // Try to find specific title element if possible, typically h1.entry-title
            Element h1 = doc.selectFirst("h1.entry-title");
            if (h1 != null)
                rawTitle = h1.text();

            result.setPosterUrl(poster);
            result.setDescription(desc);
            result.setTitle(cleanTitle(rawTitle));
            result.setOriginalTitle(rawTitle);

            // Metadata: Rating, Year, Categories
            try {
                // Rating
                Element rateElem = doc.selectFirst(".rating, .imdb-rating, .rate");
                if (rateElem != null) {
                    String rStr = rateElem.text().replaceAll("[^0-9.]", "");
                    if (!rStr.isEmpty())
                        result.setRating(Float.parseFloat(rStr));
                }

                // Year
                Element yearElem = doc.selectFirst(".release-year, .year, a[href*='year']");
                if (yearElem != null) {
                    String yStr = yearElem.text().replaceAll("[^0-9]", "");
                    if (yStr.length() == 4)
                        result.setYear(Integer.parseInt(yStr));
                }

                // Categories
                Elements catLinks = doc.select(".genres a, .categories a, a[href*='genre']");
                for (Element cat : catLinks) {
                    result.addCategory(cat.text().trim());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting extra metadata", e);
            }

            // DETECT TYPE
            MediaType type = detectMediaType(url, rawTitle);

            // SPECIAL HANDLING FOR AKWAM STAGES
            // Stage 2: Link Page (.../link/...)
            if (url.contains("/link/")) {
                Log.d(TAG, "Parsing Link Page: " + url);
                parseLinkPage(doc, result);
                return result;
            }
            // Stage 3: Download Page (.../download/...)
            else if (url.contains("/download")) { // matches /download or /watch
                Log.d(TAG, "Parsing Download Page: " + url);
                parseDownloadPage(doc, result);
                return result;
            }

            // Normal Detail Page (Series/Movie/Episode)
            // Correction based on content
            if (url.contains("/series") || url.contains("/season")) {
                type = MediaType.SERIES;
            } else if (url.contains("/movie")) {
                type = MediaType.FILM;
            } else if (url.contains("/episode")) {
                type = MediaType.EPISODE;
            }

            result.setType(type);

            // PARSE SUB-ITEMS (Episodes or Resolutions)
            if (type == MediaType.SERIES || type == MediaType.SEASON) {
                parseEpisodes(doc, result);
                // If we found episodes, it's effectively a Season view in Akwam context
                if (!result.getSubItems().isEmpty()) {
                    result.setType(MediaType.SEASON);
                }
            } else {
                // Movie or Episode -> Fetch resolutions
                parseResolutions(doc, result);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing detail page", e);
        }
        return result;
    }

    private void parseLinkPage(Document doc, ParsedItem result) {
        // Look for the "Download" button usually class "download-link" or matching
        // regex
        // Reference: fetchResolutions -> regex pattern (?:a[kwamoc])?.*/[download]{1,6}

        List<ParsedItem> nextSteps = new ArrayList<>();

        // 1. Try class .download-link
        Elements buttons = doc.getElementsByClass("download-link");
        for (Element btn : buttons) {
            String href = btn.attr("href");
            if (!href.isEmpty()) {
                href = fixUrl(href);
                nextSteps.add(new ParsedItem()
                        .setTitle("Go to Download")
                        .setPageUrl(href)
                        .setType(MediaType.FILM));
            }
        }

        if (nextSteps.isEmpty()) {
            // 2. Try Regex on all links if class not found
            Elements links = doc.getElementsByTag("a");
            Pattern p = Pattern.compile("(?:a[kwamoc])?.*/[download]{1,6}"); // simplistic version of ref regex
            for (Element link : links) {
                String href = link.attr("href");
                Matcher m = p.matcher(href);
                if (m.find()) {
                    href = fixUrl(href);
                    nextSteps.add(new ParsedItem()
                            .setTitle("Go to Download")
                            .setPageUrl(href)
                            .setType(MediaType.FILM));
                    break; // Usually only one valid one
                }
            }
        }

        result.setSubItems(nextSteps);
    }

    private void parseDownloadPage(Document doc, ParsedItem result) {
        // Reference: The final page has the direct link.
        // It might be in a "Direct Download" button or requires some sniffing?
        // Reference says:
        // "akwam.download", "akwam.link", "/download/"
        // and checks for "btn-loader".

        List<ParsedItem> finals = new ArrayList<>();

        // Try getting all links and finding the one that looks like a file or
        // high-priority domain
        Elements links = doc.getElementsByTag("a");
        for (Element link : links) {
            String href = link.attr("href");
            if (href.contains("akwam.download") || href.contains("akwam.link") || href.endsWith(".mp4")
                    || href.endsWith(".mkv")) {
                String title = link.text();
                if (title.isEmpty())
                    title = "Direct Link";

                finals.add(new ParsedItem()
                        .setTitle(title)
                        .setQuality("Direct") // SET QUALITY HERE TO INDICATE PLAYABLE
                        .setPageUrl(href)
                        .setType(MediaType.FILM));
            }
        }

        result.setSubItems(finals);
    }

    private void parseEpisodes(Document doc, ParsedItem result) {
        // Reference: fetchGroup -> doc.select("a") check if contains "/episode"
        // Also check if img alt exists
        Elements links = doc.select("a");
        for (Element link : links) {
            String href = link.attr("href");
            if (href.contains("/episode") && link.getElementsByAttribute("src").hasAttr("alt")) {
                String title = link.getElementsByAttribute("src").attr("alt");
                String epPoster = link.getElementsByAttribute("src").attr("src");

                href = fixUrl(href);

                ParsedItem episode = new ParsedItem()
                        .setTitle(title)
                        .setPageUrl(href)
                        .setPosterUrl(epPoster)
                        .setType(MediaType.EPISODE);

                result.addSubItem(episode);
            }
        }

        // Sometimes Akwam lists episodes in a specific grid, usually handled by the
        // generic check above.
    }

    private void parseResolutions(Document doc, ParsedItem result) {
        // Reference: fetchItem -> .tab-content.quality -> a href
        // Search for direct download links or link pages

        // 1. Check .tab-content.quality
        Elements qualityDivs = doc.getElementsByClass("tab-content quality");
        for (Element div : qualityDivs) {
            Elements links = div.getElementsByAttribute("href");
            for (Element link : links) {
                String href = link.attr("href");
                String txt = link.text();

                if (href.contains("/link/") || href.contains("/download/")) {
                    href = fixUrl(href);
                    ParsedItem res = new ParsedItem()
                            .setTitle(txt) // e.g. "1080p"
                            .setPageUrl(href)
                            // .setQuality(txt) <--- REMOVED: Treat as Navigation Folder
                            .setType(MediaType.FILM);
                    result.addSubItem(res);
                }
            }
        }

        // No duplicate fetching of .download-link here, as those are usually on the
        // Link Page, not the Detail page.
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty())
            return "";
        if (url.startsWith("http"))
            return url;

        return com.omarflex5.util.UrlHelper.restore(getBaseUrl(), url);
    }

    private MediaType detectType(String url, String title) {
        if (url.contains("/series") || url.contains("/season"))
            return MediaType.SERIES;
        if (url.contains("/movie"))
            return MediaType.FILM;
        if (url.contains("/episode"))
            return MediaType.EPISODE;
        return detectMediaType(url, title); // Fallback to base method
    }
}
