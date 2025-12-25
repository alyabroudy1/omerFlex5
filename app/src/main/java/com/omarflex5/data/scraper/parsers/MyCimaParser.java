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

public class MyCimaParser extends BaseHtmlParser {

    private static final String TAG = "MyCimaParser";

    public MyCimaParser(String html) {
        super(html);
    }

    public MyCimaParser(String html, String pageUrl) {
        super(html, pageUrl);
    }

    @Override
    public List<ParsedItem> parseSearchResults() {
        List<ParsedItem> items = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(html);
            Elements elements = doc.select(".GridItem");

            for (Element element : elements) {
                Element linkElement = element.selectFirst(".Thumb--GridItem > a");
                if (linkElement == null)
                    continue;

                String href = linkElement.attr("abs:href");
                if (href.isEmpty())
                    href = linkElement.attr("href");

                String title = linkElement.select("strong").text();
                // Remove year from title if present
                Element yearLink = linkElement.selectFirst("strong .year");
                String yearStr = "";
                if (yearLink != null) {
                    yearStr = yearLink.text().trim();
                    title = title.replace(yearStr, "").trim();
                }

                String posterUrl = "";
                Element bgElement = element.selectFirst(".BG--GridItem");
                if (bgElement != null) {
                    String style = bgElement.attr("style");
                    // Extract from var(--image)
                    if (style.contains("--image:url(")) {
                        posterUrl = extractBetween(style, "--image:url(", ")");
                    } else if (style.contains("background-image:url(")) {
                        posterUrl = extractBetween(style, "background-image:url(", ")");
                    }
                    if (posterUrl != null)
                        posterUrl = posterUrl.replace("'", "").replace("\"", "");
                }

                if (posterUrl == null || posterUrl.isEmpty()) {
                    Element img = element.selectFirst("img");
                    if (img != null) {
                        posterUrl = img.attr("data-src");
                        if (posterUrl.isEmpty())
                            posterUrl = img.attr("src");
                    }
                }

                MediaType type = detectMediaType(href, title);

                ParsedItem item = new ParsedItem()
                        .setTitle(title)
                        .setPageUrl(href)
                        .setPosterUrl(posterUrl)
                        .setType(type);

                if (!yearStr.isEmpty()) {
                    try {
                        item.setYear(Integer.parseInt(yearStr.replaceAll("[^0-9]", "")));
                    } catch (Exception ignored) {
                    }
                }

                items.add(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing search results", e);
        }
        return items;
    }

    @Override
    public BaseHtmlParser.ParsedSearchResult parseSearchResultsWithPagination() {
        List<ParsedItem> items = parseSearchResults();
        String nextPageUrl = null;
        try {
            Document doc = Jsoup.parse(html);
            Element nextLink = doc.selectFirst(
                    ".pagination a.next, .pagination ul.page-numbers li a:contains(›), .pagination ul.page-numbers li a[href*='/page/']");

            // Specifically look for the "Next" sibling of "current"
            if (nextLink == null) {
                Element current = doc.selectFirst(".pagination .current");
                if (current != null) {
                    Element parent = current.parent();
                    if (parent != null && parent.tagName().equals("li")) {
                        Element nextLi = parent.nextElementSibling();
                        if (nextLi != null) {
                            nextLink = nextLi.selectFirst("a");
                        }
                    }
                }
            }

            if (nextLink != null) {
                nextPageUrl = nextLink.attr("abs:href");
                if (nextPageUrl.isEmpty())
                    nextPageUrl = nextLink.attr("href");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing pagination", e);
        }
        return new ParsedSearchResult(items, nextPageUrl);
    }

    @Override
    public ParsedItem parseDetailPage() {
        ParsedItem result = new ParsedItem();
        try {
            Document doc = Jsoup.parse(html);
            result.setPageUrl(pageUrl);

            // Detailed page could be:
            // 1. Series page (has seasons list)
            // 2. Season page (has episodes list)
            // 3. Episode page (has server list)
            // 4. Movie page (has server list)

            Element seasonsList = doc.selectFirst(".SeasonsList");
            Element episodesList = doc.selectFirst(".EpisodesList");
            Element watchServers = doc.selectFirst(".WatchServers");

            if (seasonsList != null) {
                result.setType(MediaType.SERIES);
                parseSeries(doc, result);
            } else if (episodesList != null) {
                result.setType(MediaType.SEASON);
                parseSeason(doc, result);
            } else if (watchServers != null) {
                // Could be episode or movie
                if (pageUrl.contains("/episode/") || pageUrl.contains("حلقة")) {
                    result.setType(MediaType.EPISODE);
                } else {
                    result.setType(MediaType.FILM);
                }
                parsePlayableContent(doc, result);
            } else {
                // Fallback: try to detect from URL
                result.setType(detectMediaType(pageUrl, ""));
            }

            // Basic metadata
            Element titleHeader = doc.selectFirst(".Title--Content--Single-begin h1");
            if (titleHeader != null) {
                result.setTitle(titleHeader.text().trim());
            }

            Element poster = doc.selectFirst(".Poster--Single-begin a.Img--Poster--Single-begin");
            if (poster != null) {
                String style = poster.attr("style");
                if (style.contains("--img:url(")) {
                    String imgUrl = extractBetween(style, "--img:url(", ")");
                    if (imgUrl != null)
                        result.setPosterUrl(imgUrl.replace("'", "").replace("\"", ""));
                }
            }

            Element story = doc.selectFirst(".StoryMovieContent");
            if (story != null) {
                result.setDescription(story.text().trim());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing detail page", e);
        }
        return result;
    }

    private void parseSeries(Document doc, ParsedItem result) {
        Elements seasons = doc.select(".SeasonsList ul li a");
        for (Element season : seasons) {
            String title = season.text().trim();
            String href = season.attr("abs:href");
            if (href.isEmpty())
                href = season.attr("href");

            result.addSubItem(new ParsedItem()
                    .setTitle(title)
                    .setPageUrl(href)
                    .setType(MediaType.SEASON));
        }
    }

    private void parseSeason(Document doc, ParsedItem result) {
        Elements episodes = doc.select(".EpisodesList a");
        for (Element episode : episodes) {
            String title = episode.select("episodetitle").text().trim();
            if (title.isEmpty())
                title = episode.text().trim();

            String href = episode.attr("abs:href");
            if (href.isEmpty())
                href = episode.attr("href");

            result.addSubItem(new ParsedItem()
                    .setTitle(title)
                    .setPageUrl(href)
                    .setType(MediaType.EPISODE));
        }
    }

    private void parsePlayableContent(Document doc, ParsedItem result) {
        Elements servers = doc.select(".WatchServers ul li.server--item");
        if (servers.isEmpty()) {
            servers = doc.select(".WatchServers ul li");
        }

        for (Element server : servers) {
            String name = server.text().trim();
            // MyCima usually uses a data attribute or triggers a load
            // In our architecture, we often return the page URL and let Sniffer handle it,
            // or we return direct embed URLs if possible.
            // MyCima servers often have an ID or specific URL.

            // For now, let's just collect server names as subtitles if needed,
            // but the Sniffer should pick up the player.
            // If there's a specific "active" server or a list of server links:
            String serverUrl = server.attr("data-url");
            if (serverUrl.isEmpty())
                serverUrl = server.attr("data-link");

            if (!serverUrl.isEmpty()) {
                result.addSubItem(new ParsedItem()
                        .setTitle(name)
                        .setPageUrl(serverUrl)
                        .setType(MediaType.SERVER));
            }
        }
    }
}
