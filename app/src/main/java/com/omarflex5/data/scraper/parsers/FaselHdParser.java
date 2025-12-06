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

                if (!href.isEmpty() && !title.isEmpty()) {
                    MediaType type = detectType(href, title);

                    ParsedItem item = new ParsedItem()
                            .setTitle(title)
                            .setPageUrl(href)
                            .setPosterUrl(img)
                            .setType(type);
                    items.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing search results", e);
        }
        return items;
    }

    @Override
    public ParsedItem parseDetailPage() {
        ParsedItem result = new ParsedItem();
        try {
            Document doc = Jsoup.parse(html);
            String url = getPageUrl();

            // 1. Basic Info
            Element posterElem = doc.selectFirst(".posterImg img");
            String poster = posterElem != null
                    ? (posterElem.hasAttr("data-src") ? posterElem.attr("data-src") : posterElem.attr("src"))
                    : "";

            String desc = doc.select(".singleDesc p").text();
            if (desc.isEmpty())
                desc = doc.select(".singleDesc").text();

            result.setPosterUrl(poster);
            result.setDescription(desc);
            // Use Original Title for detection before cleaning?
            // The reference uses 'movie.getTitle()' which might be raw.
            // We'll use doc.title() as broad check.
            String rawTitle = doc.title();
            result.setTitle(cleanTitle(rawTitle));

            // STRICT REFERENCE LOGIC
            MediaType type = detectType(url, rawTitle);

            // STATELESS CORRECTION:
            // The reference relies on parent setting CHILD state. We don't have that.
            // If we detected FILM (Default), check if it's actually a SEASON (has
            // Episodes).
            // CRITICAL: DO NOT check for Series (SeasonDiv) here to avoid loop (Fasel has
            // SeasonDiv in sidebar).
            if (type == MediaType.FILM) {
                if (doc.selectFirst("#epAll") != null) {
                    type = MediaType.SEASON;
                }
            }

            result.setType(type);
            Log.d(TAG, "Parsing Detail for URL: " + url + " | Final Type: " + type);

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

        // 1. Series Case: URL contains "/seasons"
        boolean seriesCase = url.contains("/seasons");
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

                ParsedItem seasonItem = new ParsedItem()
                        .setTitle(sTitle)
                        .setPageUrl(url)
                        .setType(MediaType.SEASON);
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

    private void parseSeason(Document doc, ParsedItem result) {
        Element epContainer = doc.selectFirst("#epAll");
        if (epContainer != null) {
            Elements episodes = epContainer.select("a");
            for (Element ep : episodes) {
                String eTitle = ep.text();
                String eLink = ep.attr("href");

                ParsedItem epItem = new ParsedItem()
                        .setTitle(eTitle)
                        .setPageUrl(eLink)
                        .setType(MediaType.EPISODE);
                result.addSubItem(epItem);
            }
        }
    }

    private void parsePlayableContent(Document doc, ParsedItem result) {
        // Player/Resolutions
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

        // Servers
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

                if (isValidUrl(tLink)) {
                    ParsedItem serverItem = new ParsedItem()
                            .setTitle(tTitle)
                            .setPageUrl(tLink)
                            .setType(MediaType.FILM);
                    result.addSubItem(serverItem);
                }
            }
            Collections.reverse(result.getSubItems());
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
