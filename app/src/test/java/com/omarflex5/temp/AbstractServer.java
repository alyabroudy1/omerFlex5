package com.omerflex.server;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.content.Context;
import android.content.Intent;

import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.entity.ServerConfig;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.view.VideoDetailsFragment;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractServer implements ServerInterface {

    private static final String TAG = "AbstractServer";

    @Override
    public ArrayList<Movie> search(String query, ActivityCallback<ArrayList<Movie>> activityCallback, boolean handleCookie) {
        Log.i(getLabel(), "search: " + query);
        String url = query;
        if (!query.contains("http")) {
            url = this.getSearchUrl(query);
        }

        Document doc = this.getSearchRequestDoc(url);
        if (doc == null) {
            return null;
        }
        return this.getSearchMovieList(doc);
    }

    @Override
    public MovieFetchProcess fetch(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        if (movie == null) {
            Log.d(TAG, "fetch: invalid link");
            activityCallback.onInvalidLink("invalid link");
            return null;
        }
        switch (action) {
            case Movie.GROUP_OF_GROUP_STATE:
            case Movie.GROUP_STATE:
                Log.d(TAG, "fetch: fetchSeriesAction");
                return fetchSeriesAction(movie, action, activityCallback);
//            case Movie.ITEM_STATE:
//                return fetchItem(movie, Movie.ITEM_STATE);
//            case Movie.BROWSER_STATE:
//                return fetchItem(movie, Movie.BROWSER_STATE);
//            case Movie.RESOLUTION_STATE:
//                if (movie.getFetch() == Movie.REQUEST_CODE_MOVIE_UPDATE){
//                    return movie;
//                }
//                return fetchResolutions(movie);
//            case Movie.VIDEO_STATE:
//                return fetchVideo(movie);
//            case Movie.COOKIE_STATE:
//                return fetchCookie(movie);
            default:
                Log.d(TAG, "fetch: default fetchItemAction: "+ action+ "m: "+ movie);
              return fetchItemAction(movie, action, activityCallback);
        }
    }
    protected ServerConfig getConfig(){
        return ServerConfigRepository.getInstance().getConfig(getServerId());
    }
    public boolean shouldOverrideUrlLoading(Movie movie, WebView view, WebResourceRequest request){
        boolean result = false;
        String url = request.getUrl().toString();
        String host = request.getUrl().getHost();
        String newUrl = request.getUrl().toString().length() > 25 ? request.getUrl().toString().substring(0, 25) : request.getUrl().toString();

        if (getConfig() != null) {
            if (newUrl.contains(getConfig().getUrl())) {
                return false;
            }
            Log.d(TAG, "shouldOverrideUrlLoading:0 false: s: " + getConfig().getUrl() + ", u: " + url);
        }

        String videoLink = movie.getVideoUrl();
        if (!videoLink.startsWith("http")){
            videoLink = getConfig().getUrl() + videoLink;
        }
        if (newUrl.contains(Util.extractDomain(videoLink, false, false))) {
//                Log.d(TAG, "shouldOverrideUrlLoading:0 false: domain: " + Util.extractDomain(url, false, false) + ", u: " + url);
            return false;
        }

        if (host != null && host.contains("game") && url.contains("post")) {
//                Log.d(TAG, "shouldOverrideUrlLoading:0 false: domain: " + Util.extractDomain(url, false, false) + ", u: " + url);
            return false;
        }

        if (url.contains("embed")) {
//                if (url.contains("embed") || sameSite) {
            view.loadUrl(url);
            Log.d(TAG, "shouldOverrideUrlLoading:1 false: " + url);
            return false;
        }


//        if (movie.getState() == Movie.COOKIE_STATE || !Util.shouldOverrideUrlLoading(newUrl)) {
//        if (movie.getState() == Movie.COOKIE_STATE || !Util.shouldOverrideUrlLoading(newUrl)) {
//            if (url.startsWith("##")) {
//                url = url.replace("##", "");
//            }
//            Log.d(TAG, "shouldOverrideUrlLoading:5 false: " + url);
//            view.loadUrl(url);
//            return true;
//            //  CURRENT_WEB_NAME = getWebName(url);
//        }


        return true;
    }


    protected Document getSearchRequestDoc(String url) {
        final int MAX_REDIRECTS = 5;
        ServerConfig config = getConfig();
        Document doc = null;
        int redirectCount = 0;
        String currentUrl = url;
        boolean isDomainUpdated = false;
        String initialHost = Uri.parse(url).getHost();

        try {
            while (redirectCount < MAX_REDIRECTS) {
                Log.d(TAG, "Processing URL: " + currentUrl + ", follow: "+ isDomainUpdated);

                Connection.Response response = Jsoup.connect(currentUrl)
                        .headers(config.getHeaders())
                        .cookies(config.getMappedCookies())
                        .followRedirects(false)
//                        .followRedirects(isDomainUpdated)
                        .ignoreHttpErrors(true)
                        .ignoreContentType(true)
                        .timeout(10000)
                        .execute();

                int statusCode = response.statusCode();
                String docTitle = "no title";
                Log.i(TAG, "HTTP Status: " + statusCode + " for " + currentUrl);

                if (statusCode == HttpURLConnection.HTTP_OK) {
                    doc = response.parse();
                    return doc;
                } else if (isRedirect(statusCode)) {
                    String newLocation = response.header("Location");
                    if (newLocation == null || newLocation.isEmpty()) {
                        Log.w(TAG, "Redirect without Location header: " + currentUrl);
                        doc = response.parse();
                        return doc;
                    }
                    currentUrl = resolveRedirectUrl(currentUrl, newLocation);
                    Log.d(TAG, "Redirecting to: " + currentUrl);
                    isDomainUpdated = checkForDomainUpdate(currentUrl, initialHost);
                    redirectCount++;
                } else {
//                    Log.e(TAG, "Unexpected status " + response.statusCode() + " for " + currentUrl);
//                    Log.e(TAG, response.body());
//                    return statusCode == HttpURLConnection.HTTP_NOT_FOUND ? null : response.parse();
                    return response.parse();
                }
            }
            Log.w(TAG, "Too many redirects (" + MAX_REDIRECTS + ") for: " + url);
        } catch (IOException e) {
            Log.e(TAG, "Network error for " + currentUrl + ": " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error processing " + currentUrl + ": " + e.getMessage());
        }
        Log.d(TAG, "getSearchRequestDoc: ");
        return null;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode >= HttpURLConnection.HTTP_MOVED_PERM
                && statusCode < HttpURLConnection.HTTP_BAD_REQUEST;
    }

    private String resolveRedirectUrl(String baseUrl, String location) throws MalformedURLException {
        if (location.startsWith("http")) {
            return location;
        }
        URL base = new URL(baseUrl);
        return new URL(base, location).toString();
    }

    private boolean checkForDomainUpdate(String finalUrl, String initialHost) {
        Log.d(TAG, "checkForDomainUpdate: "+ shouldUpdateDomainOnSearchResult() + ", "+ finalUrl);
        if (!shouldUpdateDomainOnSearchResult()) return true;

        Uri finalUri = Uri.parse(finalUrl);
        String finalHost = finalUri.getHost();

        if (!initialHost.equals(finalHost)) {
            String schemeAndHost = finalUri.getScheme() + "://" + finalHost;
            Log.i(TAG, "Updating domain from " + initialHost + " to " + finalHost);
            updateDomain(schemeAndHost);
            return true;
        }
        return false;
    }

    /**
     * @param url request link
     * @return Document or null if an exception occurs
     */
    protected Document getSearchRequestDoc_2(String url) {
        Document doc = null;
        ServerConfig config = getConfig();
        Log.d(TAG, "getSearchRequestDoc: " + url);

        try {
            Connection.Response response = Jsoup.connect(url)
                    .headers(config.getHeaders())
                    .cookies(config.getMappedCookies())
                    .followRedirects(false) // Don't automatically follow redirects
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute();

            int statusCode = response.statusCode();
            Log.i(TAG, "Response status code: " + statusCode);

            if (statusCode == HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "Website HTTP_OK " + url);
                doc = response.parse();
                return doc;
            } else if (statusCode >= 300 && statusCode < 400) { // Redirect detected
                String newUrl = response.header("Location");

                if (newUrl == null) {
                    Connection.Response redirectResponse = Jsoup.connect(url)
                            .headers(config.getHeaders())
                            .cookies(config.getMappedCookies())
                            .followRedirects(false) // Now follow redirects for the new URL
                            .execute();

                    Log.d(TAG, "getSearchRequestDoc: redirectResponse: " + redirectResponse.statusCode());
                    Log.d(TAG, "getSearchRequestDoc: redirectResponse.headers: " + redirectResponse.headers());
                    doc = redirectResponse.parse();
                    return doc;
                }

                if (url.equals(newUrl)){
                    Log.d(TAG, "getSearchRequestDoc: redirectURL same1: " + url + ", new: " + newUrl);
                    // If you want to follow the redirect and get the document:
                    Connection.Response redirectResponse = Jsoup.connect(newUrl)
                            .headers(config.getHeaders())
                            .cookies(config.getMappedCookies())
                            .followRedirects(false) // Now follow redirects for the new URL
                            .execute();
                    newUrl = redirectResponse.header("Location");
                    if (url.equals(newUrl)){
                        Log.d(TAG, "getSearchRequestDoc: redirectURL same2: " + url + ", new: " + newUrl);
                    }else {
                        URL redirectURL = new URL(new URL(url), newUrl); // Construct absolute URL from relative
                        Log.d(TAG, "getSearchRequestDoc: redirectURL: " + redirectURL.toString() + ", new: " + newUrl);
                        Log.d(TAG, "getSearchRequestDoc: headers: " + response.headers());
                        if (shouldUpdateDomainOnSearchResult()) {
                            String scheme = redirectURL.getProtocol();
                            String host = redirectURL.getHost();
                            String schemeAndHost = scheme + "://" + host;
                            updateDomain(schemeAndHost); // Update the DB with new host
                        }

                        doc = redirectResponse.parse();
                        return doc;
                    }
                }else {
                    Log.d(TAG, "getSearchRequestDoc: not same old: "+url + " new: "+newUrl);
                    URL redirectURL = new URL(new URL(url), newUrl); // Construct absolute URL from relative
                    Log.d(TAG, "getSearchRequestDoc: redirectURL: " + redirectURL.toString() + ", new: " + newUrl);
                    Log.d(TAG, "getSearchRequestDoc: headers: " + response.headers());
                    if (shouldUpdateDomainOnSearchResult()) {
                        String scheme = redirectURL.getProtocol();
                        String host = redirectURL.getHost();
                        String schemeAndHost = scheme + "://" + host;
                        updateDomain(schemeAndHost); // Update the DB with new host
                    }

                    doc = response.parse();
                    return doc;
                }

            } else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.i(TAG, "Website not found: " + url);
                return null;
            } else {
                doc = response.parse();
                Log.e(TAG, "Unexpected status code: " + statusCode + " for " + url);
                Log.d(TAG, "Unexpected status code: "+doc.title());
                return doc;
            }

        } catch (IOException e) {
            Log.e(TAG, "Error fetching URL " + url + ": " + e.getMessage());
            return null;
        }
        return null;
    }

    private void updateDomain(String newUrl) {
        Log.d(TAG, "updateDomain: "+ newUrl);
        getConfig().setUrl(newUrl);
        getConfig().setReferer(newUrl + "/");
        ServerConfigRepository.getInstance().updateConfig(getConfig());
    }

    protected Document getRequestDoc(String url, Context context, Map<String, String> customHeaders ) {
        Document doc = null;
        ServerConfig config = getConfig();
        Log.d(TAG, "getRequestDoc: "+url);
//        Map<String, String> existingHeaders = config.getHeaders();
//        existingHeaders.putAll(customHeaders);
        Log.d(TAG, "getRequestDoc: new headers" + customHeaders);
        try {
            doc = Jsoup.connect(url)
                    .headers(customHeaders)
                    .cookies(config.getMappedCookies())
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .timeout(10000) // 10 seconds timeout
                    .get();

            String docTitle = doc.title();
            Log.d(TAG, "getRequestDoc: " + docTitle);

            if (docTitle.contains("Just a moment") || docTitle.contains("Checking your browser") || docTitle.contains("لحظة…") || docTitle.contains("التحقق")) {
//            if (docTitle.contains("Just a moment") || docTitle.contains("Checking your browser")) {
                Log.d(TAG, "getRequestDoc: Cloudflare detected, launching GetDocActivity.");
                if (context == null) {
                    Log.e(TAG, "getRequestDoc: Context is null, cannot launch GetDocActivity for Cloudflare.");
                    return doc; // return the challenge page doc
                }

                // Use a CompletableFuture to get the result from the activity
                com.omerflex.view.GetDocActivity.resultFuture = new java.util.concurrent.CompletableFuture<>();

                Intent intent = new Intent(context, com.omerflex.view.GetDocActivity.class);
                intent.putExtra("url", url); // Pass the URL to BrowserActivity via GetDocActivity
                if (customHeaders.containsKey("referer")){
                    intent.putExtra("referer", customHeaders.get("referer")); // Pass the URL to BrowserActivity via GetDocActivity
                }
                intent.putExtra("studio", getServerId()); // Pass the URL to BrowserActivity via GetDocActivity
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

                try {
                    // Wait for the result from GetDocActivity
                    String html = com.omerflex.view.GetDocActivity.resultFuture.get(2, java.util.concurrent.TimeUnit.MINUTES); // 2 minutes timeout
                    if (html != null) {
                        doc = Jsoup.parse(html);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getRequestDoc: Failed to get document from GetDocActivity", e);
                }
            }

        } catch (IOException e) {
            Log.i(TAG, "error: " + e.getMessage() + ", url: "+ url);
        }
        return doc;
    }

    protected Document getRequestDoc(String url, Context context) {
        Document doc = null;
        ServerConfig config = getConfig();
        Log.d(TAG, "getRequestDoc: "+url);

        try {
            doc = Jsoup.connect(url)
                    .headers(config.getHeaders())
                    .cookies(config.getMappedCookies())
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .timeout(10000) // 10 seconds timeout
                    .get();

            String docTitle = doc.title();
            Log.d(TAG, "getRequestDoc: " + docTitle);

            // If Jsoup gets a challenge page, use the activity to get the real page
//            if (true) {
            if (docTitle.contains("Just a moment") || docTitle.contains("Checking your browser") || docTitle.contains("لحظة…") || docTitle.contains("التحقق")) {
                Log.d(TAG, "getRequestDoc: Cloudflare detected, launching GetDocActivity.");
                if (context == null) {
                    Log.e(TAG, "getRequestDoc: Context is null, cannot launch GetDocActivity for Cloudflare.");
                    return doc; // return the challenge page doc
                }

                // Use a CompletableFuture to get the result from the activity
                com.omerflex.view.GetDocActivity.resultFuture = new java.util.concurrent.CompletableFuture<>();

                Intent intent = new Intent(context, com.omerflex.view.GetDocActivity.class);
                intent.putExtra("url", url); // Pass the URL to BrowserActivity via GetDocActivity
                intent.putExtra("studio", getServerId()); // Pass the URL to BrowserActivity via GetDocActivity
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

                try {
                    // Wait for the result from GetDocActivity
                    String html = com.omerflex.view.GetDocActivity.resultFuture.get(2, java.util.concurrent.TimeUnit.MINUTES); // 2 minutes timeout
                    if (html != null) {
                        doc = Jsoup.parse(html);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getRequestDoc: Failed to get document from GetDocActivity", e);
                }
            }

        } catch (IOException e) {
            Log.i(TAG, "error: " + e.getMessage() + ", url: "+ url);
        }
        return doc;
    }

    protected Document getRequestDoc(String url) {
        Document doc = null;
        ServerConfig config = getConfig();
        Log.d(TAG, "getRequestDoc: "+url);
        Log.d(TAG, "getRequestDoc: "+config);
//        String testo = CookieManager.getInstance().getCookie(config.getUrl());
//        config.getHeaders().put("Cookie", testo);
//        config.getHeaders().put("sec-ch-ua-full-version", "131.0.6778.205");
//        config.getHeaders().put("sec-ch-ua-full-version-list", "Google Chrome\";v=\"131.0.6778.205, \"Chromium\";v=\"131.0.6778.205\", \"Not_A Brand\";v=\"24.0.0.0\"");
//        config.getHeaders().put("sec-ch-ua-mobile", "?0");
//        config.getHeaders().put("sec-ch-ua-platform", "Windows");
//        config.getHeaders().put("sec-fetch-dest", "document");
//        config.getHeaders().put("sec-fetch-mode", "navigate");
//        config.getHeaders().put("sec-fetch-site", "none");
//        config.getHeaders().put("sec-fetch-user", "?1");
//        config.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
//        config.getHeaders().put("sec-ch-ua-arch", "x86");
//        config.getHeaders().put("sec-ch-ua-bitness", "64");
//        config.getHeaders().put("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"");
//        config.getHeaders().put("upgrade-insecure-requests", "1");
//        config.getHeaders().put("sec-ch-ua-platform-version", "10.0.0");
//        config.getHeaders().put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

        try {
//            doc = Jsoup.connect("http://www.faselhds.center/most_recent")
            doc = Jsoup.connect(url)
//                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
//                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .headers(config.getHeaders())
                    .cookies(config.getMappedCookies())
//                    .userAgent("Android 7")
//                    .userAgent("Mozilla/5.0 (Linux; Android 8.1.0; Android SDK built for x86 Build/OSM1.180201.031; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/69.0.3497.100 Mobile Safari/537.36")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
//                    .timeout(16000)
                    .timeout(0)
                    .get();

            String docTitle = doc.title();
//            Log.d(TAG, "getRequestDoc: " + docTitle);
//            if (docTitle.contains("Just a moment")) {
//                return fetchDocUsingWebView(url);
//            }
            Log.d(TAG, "getRequestDoc: " + docTitle);

        } catch (IOException e) {
            //builder.append("Error : ").append(e.getMessage()).append("\n");
            Log.i(TAG, "error: " + e.getMessage() + ", url: "+ url);
//            String errorMessage = "error: " + getServerId() + ": " + e.getMessage();
        }
        return doc;
    }

    public int fetchNextAction(Movie movie) {
//        Log.d(TAG, "fetchNextAction: "+ (movie.getFetch() == Movie.REQUEST_CODE_MOVIE_UPDATE) );
        if (movie.getFetch() == Movie.REQUEST_CODE_MOVIE_UPDATE) {
            return VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY;
        }
        switch (movie.getState()) {
            case Movie.GROUP_OF_GROUP_STATE:
            case Movie.GROUP_STATE:
            case Movie.ITEM_STATE:
                return VideoDetailsFragment.ACTION_OPEN_DETAILS_ACTIVITY;
            case Movie.BROWSER_STATE:
                return VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY;
        }
        return VideoDetailsFragment.ACTION_OPEN_EXTERNAL_ACTIVITY;
    }

    public String determineRelatedMovieLabel(Movie movie) {
        switch (movie.getState()){
            case Movie.GROUP_OF_GROUP_STATE:
                return "المواسم";
            case Movie.GROUP_STATE:
                return "الحلقات";
            case Movie.ITEM_STATE:
                return "الجودة";
            default:
                return "الروابط";
        }
    }

    public boolean shouldInterceptRequest(WebView view, WebResourceRequest request, Movie movie){
        int state = movie.getState();
        return  state == Movie.RESOLUTION_STATE || state == Movie.BROWSER_STATE;
    }

    public boolean shouldCleanWebPage(String pageUrl, Movie movie){
        int state = movie.getState();
            return state == Movie.RESOLUTION_STATE ||
                    state == Movie.BROWSER_STATE;
    }

    protected abstract String getSearchUrl(String query);

    protected abstract ArrayList<Movie> getSearchMovieList(Document doc);

    protected abstract MovieFetchProcess fetchSeriesAction(Movie movie, int action, ActivityCallback<Movie> activityCallback);

    protected abstract MovieFetchProcess fetchItemAction(Movie movie, int action, ActivityCallback<Movie> activityCallback);
    public abstract Movie updateMovieState(Movie movie);

    public abstract String getWebScript(int mode, Movie movie);

    public String getCustomUserAgent(int state){
        String defaultUserAgent = "Android 6";
//        switch (state){
//            case Movie.COOKIE_STATE:
//                return defaultUserAgent;
//            default:
//                return null;
//        }
        return defaultUserAgent;
    }

    public MovieFetchProcess handleJSResult(String elementJson, List<Movie> movies, Movie movie){
        movie.setSubList(movies);
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_DETAILS_ACTIVITY_REQUIRE, movie);
    }

    public boolean shouldUpdateDomainOnSearchResult(){
        return true;
    }

    public String getLabel() {
        return getConfig().getLabel() != null ? getConfig().getLabel() : getConfig().getName();
    }
}
