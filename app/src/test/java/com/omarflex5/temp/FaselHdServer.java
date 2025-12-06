package com.omerflex.server;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.entity.MovieType;
import com.omerflex.entity.dto.LinkHeadersDTO;
import com.omerflex.view.BrowserActivity;
import com.omerflex.view.VideoDetailsFragment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FaselHdServer extends AbstractServer {

    static String TAG = "FaselHd";
    static String WEBSITE_NAME = ".faselhd.";
    public static String WEBSITE_URL = "https://www.faselhd.center";
    static boolean START_BROWSER_CODE = false;
    static boolean STOP_BROWSER_CODE = false;
    static int RESULT_COUNTER = 0;

    public FaselHdServer() {
    }

    @Override
    public ArrayList<Movie> search(String query, ActivityCallback<ArrayList<Movie>> activityCallback, boolean handleCookie) {
        Log.i(TAG, "search: " + query);
        String searchContext = query;
        String queryName = query;
        ArrayList<Movie> movieList = new ArrayList<>();
        // if (!query.contains("faselhd")) {
//        if (headers.containsKey("Referer")){
//            if (headers.get("Referer").contains("?s=")){
//                query = headers.get("Referer");
//            }else {
//                query = headers.get("Referer")+ "/?s=" + query;
//            }
//        }else {
//            query = WEBSITE_URL + "/?s=" + query;
//        }
//        if (referer != null && !referer.isEmpty()){
//            if (referer.endsWith("/")){
//                query = referer + "?s=" + query;
//            }else {
//                query = referer + "/?s=" + query;
//            }
//        }else {
//            query = WEBSITE_URL + "/?s=" + query;
//        }
        String url = query;
        if (!query.contains("http")) {
            url = this.getSearchUrl(query);
        }
        Log.i(getLabel(), "search: " + url);

        Document doc = null;
        if (handleCookie){
            doc = this.getRequestDoc(url, OmerFlexApplication.getAppContext());
        }else {
            doc = getSearchRequestDoc(url);
        }
        if (doc == null) {
            activityCallback.onInvalidLink("Invalid link");
            return null;
        }

        Log.d(TAG, "result stop title: " + doc.title());
        if (doc.title().contains("moment")) {
//        if (!handleCookie) {
//            setCookieRefreshed(false);
            //**** default
            // String title = "ابحث في موقع فاصل ..";
            String title = searchContext;
            //int imageResourceId = R.drawable.default_image;
            // String cardImageUrl = "android.resource://" + activity.getPackageName() + "/" + imageResourceId;
            String cardImageUrl = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
            String backgroundImageUrl = "http://commondatastorage.googleapis.com/android-tv/Sample%20videos/April%20Fool's%202013/Introducing%20Google%20Nose/bg.jpg";
            Movie m = new Movie();
            m.setTitle(title);
            m.setDescription("نتائج البحث في الاسفل...");
            m.setStudio(Movie.SERVER_FASELHD);
            m.setVideoUrl(url);
            //  m.setVideoUrl("https://www.google.com/");
            m.setState(Movie.COOKIE_STATE);
            m.setType(MovieType.COOKIE);
            // m.setState(Movie.RESULT_STATE);
            m.setCardImageUrl(cardImageUrl);
            m.setBackgroundImageUrl(backgroundImageUrl);
            m.setRate("");
            m.setSearchContext(searchContext);
            m.setCreatedAt(Calendar.getInstance().getTime().toString());
            movieList.add(m);

            activityCallback.onInvalidCookie(movieList, getLabel());
            return movieList;
        }


        //Elements links = doc.select("a[href]");
        Elements lis = doc.getElementsByClass("postDiv");
        for (Element li : lis) {
            Log.i(TAG, "Fasel element found: ");

            Movie a = new Movie();
            a.setStudio(Movie.SERVER_FASELHD);
            Element videoUrlElem = li.getElementsByAttribute("href").first();
            if (videoUrlElem != null) {
                String videoUrl = videoUrlElem.attr("href");

                Element titleElem = li.getElementsByAttribute("alt").first();
                String title = "";
                if (titleElem != null) {
                    title = titleElem.attr("alt");
                }

                Element imageElem = li.getElementsByAttribute("data-src").first();
                String image = "";
                if (imageElem != null) {
                    image = imageElem.attr("data-src");
                }
                String rate = "";
                Elements spans = li.getElementsByTag("span");
                for (Element span : spans) {
                    if (!span.hasAttr("class")) {
                        rate = span.text();
                        break;
                    }
                }
                if (rate.equals("")) {
                    Element rateElem = li.getElementsByClass("pImdb").first();
                    if (rateElem != null) {
                        rate = rateElem.text();
                    }
                }

                a.setTitle(title);
                a.setVideoUrl(videoUrl);
                a.setCardImageUrl(image);

                Movie m = new Movie();
                m.setTitle(title);
                m.setDescription("");
                m.setStudio(Movie.SERVER_FASELHD);
                m.setVideoUrl(videoUrl);
                m.setMainMovieTitle(videoUrl);
                m.setCardImageUrl(image);
                m.setBackgroundImageUrl(image);
                m.setBgImageUrl(image);
                m.setRate(rate);
                m.setSearchContext(searchContext);
                m.setCreatedAt(Calendar.getInstance().getTime().toString());
                m = updateMovieState(m);
                movieList.add(m);
            }

        }

        Movie nextPage = getNextPage(doc);
        if (nextPage != null){
            movieList.add(nextPage);
        }
        activityCallback.onSuccess(movieList, getLabel());
        return movieList;
    }

    @Override
    public Movie updateMovieState(Movie movie) {
        String u = movie.getVideoUrl();
        String n = movie.getTitle();
        boolean seriesCase = u.contains("/seasons");

        if (seriesCase){
            movie.setState(Movie.GROUP_OF_GROUP_STATE);
            movie.setType(MovieType.SERIES);
            return movie;
        }
        movie.setState(Movie.ITEM_STATE);
        if (u.contains("episodes/") || n.contains("حلقة") || n.contains("حلقه")){
            movie.setType(MovieType.EPISODE);
        }
        if (n.contains("فلم") || n.contains("فيلم")){
            movie.setType(MovieType.FILM);
        }
        return movie;
    }


    private Movie getNextPage(Document doc) {

        //nextpage
        // Find the anchor element with the text "›"
        Elements elements = doc.select("a.page-link:contains(›)");

        if (elements.isEmpty()) {
            return null;
        }
        Movie nextPage = new Movie();
            // Get the href attribute of the first matching element
        String videoUrl = elements.first().attr("href");
        nextPage.setTitle("التالي");
        nextPage.setDescription("0");
        nextPage.setStudio(Movie.SERVER_FASELHD);
        nextPage.setVideoUrl(videoUrl);
        nextPage.setCardImageUrl("https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png");
        nextPage.setBackgroundImageUrl("https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png");
        nextPage.setState(Movie.NEXT_PAGE_STATE);
        nextPage.setType(MovieType.NEXT_PAGE);
        nextPage.setMainMovieTitle(videoUrl);

        return nextPage;
    }

    @Override
    protected ArrayList<Movie> getSearchMovieList(Document doc) {
        return null;
    }

    @Override
    public String getServerId() {
        return Movie.SERVER_FASELHD;
    }

    @Override
    protected String getSearchUrl(String query) {
        String searchUrl = query;
        if (query.contains("http") || query.contains("www")) {
            return query;
        }
        String webLink = getConfig().getUrl();
        if (webLink == null || webLink.isEmpty()) {
            webLink = getConfig().getReferer();
            if (webLink == null || webLink.isEmpty()) {
                webLink = WEBSITE_URL;
            }
        }
        if (webLink.endsWith("/")) {
            searchUrl = webLink + "?s=" + query;
        } else {
            searchUrl = webLink + "/?s=" + query;
        }
        Log.d(TAG, "getSearchUrl: " + searchUrl);
        return searchUrl;
    }

//    public Movie fetch(Movie movie) {
//        Log.d(TAG, "fetch: " + movie.getVideoUrl());
//        switch (movie.getState()) {
//            case Movie.GROUP_OF_GROUP_STATE:
//                Log.i(TAG, "onItemClick. GROUP_OF_GROUP_STATE" + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                return fetchGroupOfGroup(movie);
//            //return startWebForResultActivity(movie);
//            //return movie;
//            case Movie.GROUP_STATE:
//                Log.i(TAG, "onItemClick. GROUP_STATE" + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                return fetchGroup(movie);
//            //return startWebForResultActivity(movie);
//            //return movie;
//            case Movie.ITEM_STATE:
//                Log.i(TAG, "onItemClick. ITEM_STATE" + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                //fetchItem(movie);
//                // movie.setVideoUrl(movie.getVideoUrl() + "#vihtml");
//                //return startWebForResultActivity(movie);
//                return fetchItem(movie);
//            case Movie.RESOLUTION_STATE:
//                Log.i(TAG, "onItemClick. RESOLUTION_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                Movie clonedMovie = Movie.clone(movie);
//                clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
//                return fetchResolutions(clonedMovie);
//            case Movie.BROWSER_STATE:
//                Log.i(TAG, "onItemClick. BROWSER_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                //startBrowser(movie.getVideoUrl());
//                break;
//            case Movie.COOKIE_STATE:
//                Log.i(TAG, "onItemClick. COOKIE_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                Movie clonedMovie2 = Movie.clone(movie);
//                clonedMovie2.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//                startWebForResultActivity(clonedMovie2);
//                activity.finish();
//                return movie;
//            case Movie.RESULT_STATE:
//                Log.i(TAG, "onItemClick. RESULT_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                startWebForResultActivity(movie);
//                activity.finish();
//                return movie;
//            case Movie.VIDEO_STATE:
//                Log.i(TAG, "onItemClick. VIDEO_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                return movie;
//            default:
//                return fetchResolutions(movie);
//        }
//        return movie;
//    }

    protected MovieFetchProcess fetchSeriesAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        if (action == Movie.GROUP_OF_GROUP_STATE) {
            return fetchGroupOfGroup(movie, activityCallback);
        }
        return fetchGroup(movie, activityCallback);
    }

    public MovieFetchProcess fetchBrowseItem(Movie movie) {
        Movie clonedMovie = Movie.clone(movie);
        clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
        // to do nothing and wait till result returned to activity only the first fetch
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, clonedMovie);
    }

    private MovieFetchProcess fetchCookie(Movie movie, ActivityCallback<Movie> activityCallback) {
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, movie);
    }

    private MovieFetchProcess fetchWatchLocally(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.d(TAG, "fetchWatchLocally: "+movie);
        return fetchResolutions(movie, activityCallback);
//        if (movie.getState() == Movie.BROWSER_STATE || movie.getState() == Movie.RESOLUTION_STATE) {
////            Movie clonedMovie = Movie.clone(movie);
////            clonedMovie.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
////            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, clonedMovie);
//            activityCallback.onInvalidCookie(movie, getLabel());
//            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, movie);
//        }
//        activityCallback.onSuccess(movie, getLabel());
//        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_EXOPLAYER, movie);
    }

    @Override
    protected MovieFetchProcess fetchItemAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        Log.d(TAG, "fetchItemAction: ");
        switch (action) {
            case Movie.BROWSER_STATE:
                Log.d(TAG, "fetchItemAction: BROWSER_STATE");
                return fetchBrowseItem(movie);
            case Movie.COOKIE_STATE:
                Log.d(TAG, "fetchItemAction: COOKIE_STATE");
                activityCallback.onInvalidCookie(movie, getLabel());
                return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, movie);
            case Movie.ACTION_WATCH_LOCALLY:
                Log.d(TAG, "fetchItemAction: ACTION_WATCH_LOCALLY");
                return fetchWatchLocally(movie, activityCallback);
            case Movie.RESOLUTION_STATE:
                return fetchResolutions(movie, activityCallback);
//            case Movie.VIDEO_STATE:
//                return fetchVideo(movie);
            default:
                Log.d(TAG, "fetchItemAction: default fetchItem");
                return fetchItem(movie, activityCallback);
        }
    }

    private Movie startWebForResultActivity(Movie movie) {
//        fragment.getActivity().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                Intent browse = new Intent(fragment.getActivity(), BrowserActivity.class);
//                browse.putExtra(DetailsActivity.MOVIE, (Serializable) movie);
//                //   Log.d(TAG, "getResultFromWeb: activity:" + fragment.getClass().getName());
//                //activity.startActivity(browse);
//                fragment.startActivityForResult(browse, movie.getFetch());
//            }
//        });
//
//        return movie;
//        Intent browse = new Intent(activity, BrowserActivity.class);
//        browse.putExtra(DetailsActivity.MOVIE, (Serializable) movie);
//        browse.putExtra(DetailsActivity.MAIN_MOVIE, (Serializable) movie.getMainMovie());
//        Log.d(TAG, "startWebForResultActivity: fragment: " + movie);
//        if (fragment == null) {
//            activity.startActivityForResult(browse, movie.getFetch());
//        } else {
//            fragment.startActivityForResult(browse, movie.getFetch());
//        }
//        return movie;
        return null;
    }

    @Override
    public int fetchNextAction(Movie movie) {
        Log.d(TAG, "fetchNextAction: " + movie);
        switch (movie.getState()) {
            case Movie.GROUP_OF_GROUP_STATE:
            case Movie.GROUP_STATE:
            case Movie.ITEM_STATE:
                return VideoDetailsFragment.ACTION_OPEN_DETAILS_ACTIVITY; // to open VideoDetailsActivityreturn true;
            case Movie.VIDEO_STATE:
                return VideoDetailsFragment.ACTION_OPEN_EXTERNAL_ACTIVITY;
        }
        return VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY; // not to open any activity;
    }

//    public Movie fetchToWatchLocally(Movie movie) {
//        Log.d(TAG, "fetchToWatchLocally: " + movie.getVideoUrl());
//        if (movie.getState() == Movie.VIDEO_STATE) {
//            return movie;
//        }
//        Movie clonedMovie = Movie.clone(movie);
//        clonedMovie.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
//        fetchResolutions(clonedMovie);
//        return null; // to do nothing till result returned to the fragment/activity
//    }

    private MovieFetchProcess fetchGroupOfGroup(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchGroupOfGroup: " + movie.getVideoUrl());
//            Document doc = Jsoup.connect(movie.getVideoUrl())
//                    .cookies(getMapCookies())
//                    .headers(headers)
//                    .followRedirects(true)
//                    .ignoreHttpErrors(true)
//                    .timeout(0)
//                    .get();

        Document doc = this.getRequestDoc(movie.getVideoUrl(), OmerFlexApplication.getAppContext());
        if (doc == null) {
            activityCallback.onInvalidLink(movie);
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, movie);

        }
        Log.i(TAG, "result stop title: " + doc.title());
        if (doc.title().contains("moment")) {
//            setCookieRefreshed(false);
//            Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//            return startWebForResultActivity(clonedMovie);
            activityCallback.onInvalidCookie(movie, getLabel());
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, movie);
        }

        Element posterImg = doc.selectFirst(".posterImg");
        String backgroundImage = "";
        String trailer = "";
        String description = "";
        if (posterImg != null) {
            Element bgImage = posterImg.selectFirst("img");
            if (bgImage != null) {
                backgroundImage = bgImage.attr("src");
            }

            Element trailerElement = posterImg.selectFirst("a");
            if (trailerElement != null) {
                trailer = trailerElement.attr("href");
            }
        }

        Element singleDesc = doc.selectFirst(".singleDesc");
        if (singleDesc != null) {
            Element desElement = singleDesc.selectFirst("p");
            if (desElement != null) {
                description = desElement.text();
            } else {
                description = singleDesc.text();
            }
        }

        movie.setDescription(description);
        movie.setTrailerUrl(trailer);
        movie.setBackgroundImageUrl(backgroundImage);

        Elements lis = doc.getElementsByClass("seasonDiv");
        if (lis.isEmpty()) {
            return fetchGroup(movie, activityCallback);
        }

        for (Element seasonDiv : lis) {
            Log.i(TAG, "Fasel element found: " + description);

            Movie a = Movie.clone(movie);
            a.setParentId(movie.getId());
//                if (headers.containsKey("Referer") && !headers.get("Referer").contains("/s")){
//                    WEBSITE_URL =  headers.get("Referer");
//                }

            String title = "";
            String rate = "";
            String image = "";
            String link = "";
            if (seasonDiv.selectFirst(".title") != null) {
                title = seasonDiv.selectFirst(".title").text();
            }
            if (seasonDiv.selectFirst(".fa-star") != null) {
                rate = seasonDiv.selectFirst(".fa-star").parent().ownText();
            }
            if (seasonDiv.selectFirst("img") != null) {
                image = seasonDiv.selectFirst("img").attr("data-src");
                if (image == null || image.equals("")) {
                    image = seasonDiv.selectFirst("img").attr("src");
                }
            }
            if (seasonDiv.attr("onclick") != null) {
                link = Util.extractDomain(movie.getVideoUrl(), true, false) + seasonDiv.attr("onclick").replace("window.location.href = '", "").replace("'", "");
            }

            Log.d(TAG, "fetchGroupOfGroup: image:" + image);
            Log.d(TAG, "fetchGroupOfGroup: link:" + link);
            a.setTitle(title);
            a.setVideoUrl(link);
            a.setCardImageUrl(image);
            a.setRate(rate);
            a.setState(Movie.GROUP_STATE);
            a.setType(MovieType.SEASON);
            a.setTrailerUrl(trailer);
            a.setDescription(description);
            a.setStudio(Movie.SERVER_FASELHD);
            a.setBackgroundImageUrl(backgroundImage);
            if (movie.getSubList() == null) {
                movie.setSubList(new ArrayList<>());
            }
            movie.addSubList(a);
        }

        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    private MovieFetchProcess fetchGroup(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchGroup: " + movie.getVideoUrl());

        Document doc = this.getRequestDoc(movie.getVideoUrl(), OmerFlexApplication.getAppContext());
        if (doc == null) {
            activityCallback.onInvalidLink(movie);

            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, movie);
        }

//            Document doc = Jsoup.connect(movie.getVideoUrl())
//                    .cookies(getMapCookies())
//                    .headers(headers)
//                    .followRedirects(true)
//                    .ignoreHttpErrors(true)
//                    .timeout(0)
//                    .get();
        Log.i(TAG, doc.title());

        if (doc.title().contains("moment")) {
//            Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
            activityCallback.onInvalidCookie(movie, getLabel());
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, movie);
//            return startWebForResultActivity(clonedMovie);
        }

        Element posterImg = doc.selectFirst(".posterImg");
        String backgroundImage = "";

        String description = "";
        if (posterImg != null) {
            Element bgImage = posterImg.selectFirst("img");
            if (bgImage != null) {
                backgroundImage = bgImage.attr("src");
            }

        }

        Element singleDesc = doc.selectFirst(".singleDesc");
        if (singleDesc != null) {
            Element desElement = singleDesc.selectFirst("p");
            if (desElement != null) {
                description = desElement.text();
            } else {
                description = singleDesc.text();
            }
        }

        movie.setDescription(description);
        movie.setBackgroundImageUrl(backgroundImage);

        Element episodeContainer = doc.selectFirst(".epAll");
        Log.d(TAG, "fetchGroup: xxx epAll ");
//                Log.d(TAG+"xxx", "fetchGroup: xxx epAll2 " + episodeContainer);
        if (episodeContainer == null) {
            return fetchItem(movie, activityCallback);
        }

        Elements episodeList = episodeContainer.getElementsByTag("a");
//                    Log.d(TAG+"xxx", "fetchGroup: xxx epAll3 " + episodeList.size());
//                    Log.d(TAG, "fetchGroup: xxx episode " + episodeList.size());
        for (Element episodeDiv : episodeList) {
            Log.i(TAG, "Fasel episode element found: ");

            Movie a = Movie.clone(movie);
            a.setParentId(movie.getId());
            String title = episodeDiv.text();
            String link = episodeDiv.attr("href");

            a.setTitle(title);
            a.setVideoUrl(link);
            a.setCardImageUrl(movie.getCardImageUrl());
            a.setRate(movie.getRate());
            a.setState(Movie.ITEM_STATE);
            a.setType(MovieType.EPISODE);
            a.setTrailerUrl(movie.getTrailerUrl());
            a.setDescription(description);
            a.setStudio(Movie.SERVER_FASELHD);
            a.setBackgroundImageUrl(backgroundImage);
            if (movie.getSubList() == null) {
                movie.setSubList(new ArrayList<>());
            }
            movie.addSubList(a);
        }

        Log.d(TAG, "fetchGroup: result movie: " + movie.getSubList());
        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    @NonNull
    private WebView getWebView() {
//        WebView webView = activity.findViewById(R.id.webView);
//        WebSettings webSettings = webView.getSettings();
//
//        webSettings.setJavaScriptEnabled(true);
//        //webSettings.setAppCacheEnabled(true);
//        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
//        webSettings.setAllowFileAccess(true);
//        webSettings.setLoadsImagesAutomatically(true);
//        // webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
//        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
//        webSettings.setDomStorageEnabled(true);
//
//        webView.setInitialScale(1);
//        webSettings.setLoadWithOverviewMode(true);
//        webSettings.setUseWideViewPort(true);
//
//        // Enable hardware acceleration
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//        } else {
//            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
//        }
//
//        webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
//        webSettings.setBlockNetworkImage(false);
//        webSettings.setPluginState(WebSettings.PluginState.OFF);
//        webSettings.setMediaPlaybackRequiresUserGesture(false);
//        webView.setWebChromeClient(new ChromeClient());
//        return webView;
        return null;
    }

    private ArrayList<Movie> extractVideos(Movie movie) {
        ArrayList<Movie> videoList = new ArrayList<>();
            // Step 1: Try to find div with class "quality_change"
        LinkHeadersDTO LinkDTO = Util.splitLinkAndHeaders(movie.getVideoUrl());
            Document doc = this.getRequestDoc(LinkDTO.url, OmerFlexApplication.getAppContext());
        if (doc == null) {
            Log.d(TAG, "extractVideos: onInvalidLink ");
            return videoList;
        }
        Log.i(TAG, "result stop title: " + doc.title());

        Element qualityDiv = doc.selectFirst("div.quality_change");

            // Step 2: If not found, search fallback div
            if (qualityDiv == null) {
                for (Element div : doc.select("div")) {
                    String text = div.text();
                    if (text.contains("Auto") &&
                            (text.contains("1080p") || text.contains("720p") || text.contains("360p"))) {
                        qualityDiv = div;
                        break;
                    }
                }
            }
        if (qualityDiv != null) {
//            extractButtons(qualityDiv.select("button"), movie, videoList);
            Log.d(TAG, "extractVideos:qualityDiv- "+qualityDiv.outerHtml());
        }
            // Step 3: Look for document.write inside <script>
            Elements scripts = doc.select("script");
            for (Element script : scripts) {
                String scriptData = script.data();

                if (scriptData.contains("document.write")) {
                    // Extract HTML inside document.write(' ... ')
                    Matcher m = Pattern.compile("document\\.write\\('(.*?)'\\);")
                            .matcher(scriptData);

                    if (m.find()) {
                        String innerHtml = m.group(1)
                                .replace("\\\"", "\"") // unescape quotes if needed
                                .replace("\\/", "/");

                        Document innerDoc = Jsoup.parse(innerHtml);
                        Log.d(TAG, "extractVideos: innerDoc: "+ innerDoc.outerHtml());
                        Elements buttons = innerDoc.select("button");
                        extractButtons(buttons, movie, videoList);
                        break;
                    }
                }
            }
            // Print results
            if (videoList.isEmpty()) {
                // --- Strategy 3: fallback, look for var videoSrc = '...m3u8' ---
                Pattern pattern = Pattern.compile("videoSrc\\s*=\\s*['\"]([^'\"]+)['\"]");
                for (Element script : doc.select("script")) {
                    String data = script.data();
                    if (data != null && !data.isEmpty()) {
                        Matcher matcher = pattern.matcher(data);
                        if (matcher.find()) {
                            String url = matcher.group(1);

                            Movie videoMap = Movie.clone(movie);
                            videoMap.setVideoUrl(url);
                            videoMap.setTitle("Auto");
                            videoMap.setState(Movie.VIDEO_STATE);
                            videoMap.setType(MovieType.VIDEO);

                            videoList.add(videoMap);
                            Log.d(TAG, "extractButtons: url: " + url);
                            break;
                        }
                    }
                }

                if (videoList.isEmpty()) {
                System.out.println(doc.outerHtml());
                System.out.println("No quality buttons or URLs found.");
                }
            } else {
                for (Movie entry : videoList) {
                    System.out.println("title = " + entry.getTitle());
                    System.out.println("videoUrl = " + entry.getVideoUrl());
                    System.out.println("-------------");
                }
            }
            return videoList;
    }

    private void extractButtons(Elements buttons, Movie movie, ArrayList<Movie> videoList) {
        Log.d(TAG, "extractButtons: "+ buttons.size());
        for (Element button : buttons) {
            String title = button.text().trim();
            Log.d(TAG, "extractButtons:button.text(): "+ title);
            if (title.isEmpty()) continue;

            String videoUrl = null;

            if (button.hasAttr("data-url")) {
                videoUrl = button.attr("data-url");
            } else if (button.hasAttr("data-href")) {
                videoUrl = button.attr("data-href");
            } else if (button.hasAttr("onclick")) {
                String onclick = button.attr("onclick");
                int start = onclick.indexOf("http");
                int end = onclick.indexOf("m3u8");
                if (start != -1 && end != -1) {
                    videoUrl = onclick.substring(start, end + 4);
                }
            } else {
                Element link = button.selectFirst("a[href]");
                if (link != null) {
                    videoUrl = link.attr("href");
                }
            }

            if (videoUrl != null && videoUrl.startsWith("http")) {
                Movie videoMap = Movie.clone(movie);
                videoMap.setVideoUrl(videoUrl);
                videoMap.setTitle(title);
                videoMap.setState(Movie.VIDEO_STATE);
                videoMap.setType(MovieType.VIDEO);
                videoList.add(videoMap);
                Log.d(TAG, "extractButtons: title: "+ title);
            }
        }
    }

    private MovieFetchProcess fetchItem(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchItem: " + movie.getVideoUrl());

        Document doc = this.getRequestDoc(movie.getVideoUrl(), OmerFlexApplication.getAppContext());
        if (doc == null) {
            Log.d(TAG, "fetchItem: onInvalidLink ");
            activityCallback.onInvalidLink(movie);
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, movie);
        }
        Log.i(TAG, "result stop title: " + doc.title());

        if (doc.title().contains("moment")) {
            Movie clonedMovie = Movie.clone(movie);
            clonedMovie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//            return startWebForResultActivity(clonedMovie);
            Log.d(TAG, "fetchItem: moment");
            activityCallback.onInvalidCookie(clonedMovie, getLabel());
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, clonedMovie);
        }

        Element posterImg = doc.selectFirst(".posterImg");
        String backgroundImage = "";

        String description = "";
        if (posterImg != null) {
            Element bgImage = posterImg.selectFirst("img");
            if (bgImage != null) {
                backgroundImage = bgImage.attr("src");
            }

        }

        Element singleDesc = doc.selectFirst(".singleDesc");
        if (singleDesc != null) {
            Element desElement = singleDesc.selectFirst("p");
            if (desElement != null) {
                description = desElement.text();
            } else {
                description = singleDesc.text();
            }
        }

        movie.setDescription(description);
        movie.setBackgroundImageUrl(backgroundImage);

        if (movie.getSubList() == null) {
            movie.setSubList(new ArrayList<>());
        }

        Element resolutionsTab = doc.selectFirst(".signleWatch");
        if (resolutionsTab != null) {
            Elements episodeList = resolutionsTab.getElementsByTag("li");
            for (Element episodeDiv : episodeList) {

                Movie a = Movie.clone(movie);
                a.setParentId(movie.getId());

                String title = "";
                Element titleElem = episodeDiv.selectFirst("a");
                if (titleElem != null) {
                    title = titleElem.text();
                }

                String link = "";
                String linkElem = episodeDiv.attr("onclick");
                if (linkElem != null) {
                    link = linkElem.replace("player_iframe.location.href = ", "").replace("'", "");
                    link = link + Util.generateHeadersForVideoUrl(getConfig().getHeaders());
                }

                Log.i(TAG, "Fasel server element found: " + link);

                a.setTitle(title);
                a.setVideoUrl(link);
                a.setCardImageUrl(movie.getCardImageUrl());
                a.setRate(movie.getRate());
                a.setState(Movie.RESOLUTION_STATE);
                a.setType(MovieType.RESOLUTION);
                a.setTrailerUrl(movie.getTrailerUrl());
                a.setDescription(description);
                a.setStudio(Movie.SERVER_FASELHD);
                a.setBackgroundImageUrl(backgroundImage);

                movie.addSubList(a);
            }
        }
        Collections.reverse(movie.getSubList());
        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    /**
     * should fetch video link from the video page but doesnot work coz of security check
     *
     * @param movie Movie object to fetch its url
     * @return
     */
    private MovieFetchProcess fetchResolutions(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchResolutions: " + movie.getVideoUrl());
        Log.i(TAG, "fetchResolutions: " + movie.getState());
        if (movie.getState() == Movie.BROWSER_STATE || movie.getState() == Movie.RESOLUTION_STATE) {
           ArrayList<Movie> videos = extractVideos(movie);
           // Todo: attach to exoPlayer as quality
           if (!videos.isEmpty()){
               videos.get(0).setFetch(Movie.REQUEST_CODE_EXOPLAYER);
               activityCallback.onSuccess(videos.get(0), getLabel());
               return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, videos.get(0));
           }
            Log.d(TAG, "fetchResolutions: empty video list");
            Movie clonedMovie = Movie.clone(movie);
            clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
            activityCallback.onInvalidCookie(clonedMovie, getLabel());
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, clonedMovie);
        }
        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    public void startVideo(String url) {
        Log.i(TAG, "startVideo: " + url);
        //for now as the web site require security check.
        String type = "video/*"; // It works for all video application
        Uri uri = Uri.parse(url);
        Intent in1 = new Intent(Intent.ACTION_VIEW, uri);
        in1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        //  in1.setPackage("org.videolan.vlc");
        in1.setDataAndType(uri, type);
//        fragment.startActivity(in1);
    }


    public void startBrowser(String url) {
//        Log.i(TAG, "startBrowser: " + url);
//        //   FaselHdController.CURRENT_VIDEO_URL = "";
//        FaselHdServer.START_BROWSER_CODE = false;
//        WebView simpleWebView = fragment.getActivity().findViewById(R.id.webView);
//        simpleWebView.loadUrl(url);
    }

    //load it from browser activity but doesnt work now cuz of security check
    public void loadFaselVideo(Movie movie) {

        Log.d(TAG, "loadFaselVideo: " + movie.getVideoUrl());

   /*     final Movie m1 = Movie.buildMovieInfo("Auto", "", Movie.SERVER_FASELHD, movie.getVideoUrl(), movie.getCardImageUrl(), movie.getBackgroundImageUrl(), Movie.VIDEO_STATE, "", movie.getHistoryUrl(), movie.getHistoryTitle(), movie.getHistoryState(),
              //  movie.getHistoryCardImageUrl(),
                movie.getCreatedAt(),"");

        String url2 = movie.getVideoUrl().replaceAll("/master.m3u8", "/index-f1-v1-a1.m3u8");
        final Movie m2 = Movie.buildMovieInfo("1080", "", Movie.SERVER_FASELHD, url2, movie.getCardImageUrl(), movie.getBackgroundImageUrl(), Movie.VIDEO_STATE, "", movie.getHistoryUrl(), movie.getHistoryTitle(),movie.getHistoryState(),
             //   movie.getHistoryCardImageUrl(),
                movie.getCreatedAt(),"");

        String url3 = movie.getVideoUrl().replaceAll("/master.m3u8", "/index-f2-v1-a1.m3u8");
        final Movie m3 = Movie.buildMovieInfo("720", "", Movie.SERVER_FASELHD, url3, movie.getCardImageUrl(), movie.getBackgroundImageUrl(), Movie.VIDEO_STATE, "", movie.getHistoryUrl(), movie.getHistoryTitle(),movie.getHistoryState(),
             //   movie.getHistoryCardImageUrl(),
                movie.getCreatedAt(),"");

        String url4 = movie.getVideoUrl().replaceAll("/master.m3u8", "/index-f3-v1-a1.m3u8");
        final Movie m4 = Movie.buildMovieInfo("480", "", Movie.SERVER_FASELHD, url4, movie.getCardImageUrl(), movie.getBackgroundImageUrl(), Movie.VIDEO_STATE, "", movie.getHistoryUrl(), movie.getHistoryTitle(),movie.getHistoryState(),
             //   movie.getHistoryCardImageUrl(),
            //    movie.getCreatedAt(),"");

        String url5 = movie.getVideoUrl().replaceAll("/master.m3u8", "/index-f4-v1-a1.m3u8");
        //final Movie m5 = Movie.buildMovieInfo("320", "", Movie.SERVER_FASELHD, url5, movie.getCardImageUrl(), movie.getBackgroundImageUrl(), Movie.VIDEO_STATE, "", movie.getHistoryUrl(), movie.getHistoryTitle(),movie.getHistoryState(),
        final Movie m5 = new Movie();
                //  movie.getHistoryCardImageUrl(),
              //  movie.getCreatedAt(),"");

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Collections.reverse(LinkSeriesActivity.seriesMovieList);
                listRowAdapter.add(m1);
                listRowAdapter.add(m2);
                listRowAdapter.add(m3);
                listRowAdapter.add(m4);
                listRowAdapter.add(m5);
            }
        });


    */
        //addToHistory(movie);

    }

    public boolean onLoadResource(Activity activity, WebView webView, String url, Movie movie) {
    /*    CookieManager cookieManager = CookieManager.getInstance();
        Log.d(TAG, "onLoadResource: Fasel:" + url + ", movie:" + movie.getVideoUrl());
        String extractMovies =
                "let postList = [];\n" +
                        "let postDivs = document.getElementsByClassName(\"postDiv\");\n" +
                        "for (let i = 0; i < postDivs.length; i++) {\n" +
                        "    let post = {};\n" +
                        "    let postDiv = postDivs[i];\n" +
                        "    post.title = postDiv.getElementsByTagName(\"img\")[0].alt;\n" +
                        "    post.videoUrl = postDiv.getElementsByTagName(\"a\")[0].href;\n" +
                        "    post.cardImageUrl = postDiv.getElementsByTagName(\"img\")[0].getAttribute('data-src');\n" +
                        "    post.bgImageUrl = post.cardImageUrl;" +
                        "    post.studio = 'FaselHd';" +
                        "    post.state = 0;" +
                        "    postList.push(post);\n" +
                        "}\n" +
                        "postList;\n";

        // String web = FaselHdController.WEBSITE_URL + "/?p=";
        String fetchGroupOfGroup =
                "let postList = [];\n" +
                        "let seasons = document.querySelectorAll('.seasonDiv');\n" +
                        "let description = document.getElementsByClassName(\"singleDesc\")[0].innerHTML.replace(/<.*?>/g, \"\").replace(/(\\r\\n|\\n|\\r)/gm,\"\");" +
                        "for (let i = 0; i < seasons.length; i++) {\n" +
                        "    let post = {};\n" +
                        "    let season = seasons[i];\n" +
                        "    post.videoUrl = 'https://www.faselhd.club/?p='+ season.getAttribute('data-href');\n" +
                        "    post.title = season.querySelector('.title').textContent;\n" +
                        "    post.cardImageUrl = season.querySelector('[data-src]').getAttribute('data-src');\n" +
                        "    post.bgImageUrl = post.cardImageUrl;\n" +
                        "    post.description = description;\n" +
                        "    let spans = season.querySelectorAll('.seasonMeta');\n" +
                        "    for (let j = 0; j < spans.length; j++) {\n" +
                        "        post.rate = spans[j].querySelector('*').textContent;\n" +
                        "        break;\n" +
                        "    }\n" +
                        "    post.state = 1;\n" +
                        "    post.studio = 'FaselHd';\n" +
                        "    postList.push(post);\n" +
                        "}\n" +
                        "postList;\n";

        String fetchGroup =
                "//fetch description\n" +
                        "        let description = document.getElementsByClassName(\"singleDesc\")[0].innerHTML.replace(/<.*?>/g, \"\").replace(/(\\r\\n|\\n|\\r)/gm,\"\");" +
                        "        //fetch session\n" +
                        "        let boxs = document.getElementsByClassName(\"seasonDiv active\");\n" +
                        "        let postList = [];\n" +
                        "        if (boxs.length == 0){\n" +
                        "            let title = document.getElementsByClassName(\"h1 title\")[0].text;\n" +
                        "            let cardImageUrl = document.getElementsByClassName(\"img-fluid\")[0].getAttribute(\"src\");\n" +
                        "            let divs = document.getElementById(\"epAll\").querySelectorAll(\"[href]\");\n" +
                        "            for (const div of divs) {\n" +
                        "                let post = {};\n" +
                        "                post.cardImageUrl = cardImageUrl;\n" +
                        "                post.bgImageUrl = cardImageUrl;\n" +
                        "                post.backgroundImageUrl = cardImageUrl;\n" +
                        "                post.videoUrl = div.getAttribute(\"href\");\n" +
                        "                post.title = div.innerHTML.replace(/<.*?>/g, \"\").replace(/(\\r\\n|\\n|\\r)/gm,\"\").trim();\n" +
                        "                post.description = description;\n" +
                        "                post.state = 2;\n" +
                        "                post.rate = '';\n" +
                        "                post.studio = 'FaselHd';\n" +
                        "                postList.push(post);\n" +
                        "               console.log('box0: '+post.toString());" +
                        "            }\n" +
                        "        }else{" +
                        "        for (let i = 0; i < boxs.length; i++) {\n" +
                        "            let title = boxs[i].getElementsByClassName(\"title\")[0].textContent;\n" +
                        "            let cardImageUrl = boxs[i].querySelectorAll(\"[data-src]\")[0].getAttribute(\"data-src\");\n" +
                        "                let divs = document.getElementById(\"epAll\").getElementsByTagName(\"a\");\n" +
                        "                for (const div of divs) {\n" +
                        "                let post = {};\n" +
                        "                post.cardImageUrl = cardImageUrl;\n" +
                        "                post.bgImageUrl = cardImageUrl;\n" +
                        "                post.backgroundImageUrl = cardImageUrl;\n" +
                        "                post.videoUrl = div.getAttribute(\"href\");\n" +
                        "                post.title = div.innerHTML.replace(/<.*?>/g, \"\").replace(/(\\r\\n|\\n|\\r)/gm,\"\").trim();\n" +
                        "                post.description = description;\n" +
                        "                post.state = 2;\n" +
                        "                post.rate = '';\n" +
                        "                post.studio = 'FaselHd';\n" +
                        "                postList.push(post);\n" +
                        "            console.log('box2: '+post.title+', '+post.videoUrl);" +
                        "               }\n" +
                        "         }\n" +
                        "      }" +
                        "postList;";
        Callback callBack = new Callback() {
            @Override
            public void onCallback(String value, int counter) {
                Log.d(TAG, "onCallback: " +counter + ", "+ url + ", " + value);
                webView.stopLoading();
                // webView.destroy();
                // Remove the WebView from its parent view
//                ViewGroup parent = (ViewGroup) webView.getParent();
//                if (parent != null) {
//                    parent.removeView(webView);
//                }
                // Remove any child views from the WebView
                // webView.removeAllViews();
                // Destroy the WebView
                //   webView.destroy();
                setCookies(cookieManager.getCookie(movie.getVideoUrl()));
                setHeaders(headers);
                Intent returnIntent = new Intent();
                returnIntent.putExtra("result", value);
                activity.setResult(Activity.RESULT_OK, returnIntent);

                activity.finish();
                return; // to stop loading resources
            }
        };

        String faselJsCode = "";
        if (movie.getState() == Movie.RESULT_STATE) {
            if (url.equals(movie.getVideoUrl())) {
                faselJsCode = extractMovies;
            }
        } else if (movie.getState() == Movie.GROUP_OF_GROUP_STATE) {
            if (url.equals(movie.getVideoUrl())) {
                faselJsCode = fetchGroupOfGroup;
            }
        } else if (movie.getState() == Movie.GROUP_STATE) {
            faselJsCode = fetchGroup;
        } else if (movie.getState() == Movie.ITEM_STATE) {
            // view.loadUrl("javascript:window.confirm = function() { return false; }");
            // view.loadUrl("javascript:window.addEventListener('beforeunload', function (e) { e.preventDefault(); });");
            //  view.loadUrl("javascript:window.frames['player_iframe'].click()");
            String jsClickScript = "(function() {" +
                    "let playerIframe = document.getElementById('player_iframe');" +
                    "if (playerIframe) {" +
                    "playerIframe.click();" +
                    "}" +
                    "})();";
            webView.evaluateJavascript(jsClickScript, null);
        }

        webView.evaluateJavascript(faselJsCode, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (value.length() > 4) {
                            Log.d(TAG, "onReceiveValue:tempValue1: " + value.length() + ", " + value);
                            webView.stopLoading();
                           // callBack.onCallback(value);
                            setCookies(cookieManager.getCookie(movie.getVideoUrl()));
                            setHeaders(headers);
                            Intent returnIntent = new Intent();
                            returnIntent.putExtra("result", value);
                            activity.setResult(Activity.RESULT_OK, returnIntent);

                            activity.finish();
                        }
                    }
                }
        );
*/
        return true;
    }

    public void fetchWebResult(Movie movie) {
//        WebView webView = activity.findViewById(R.id.webView);
//        webView.loadUrl(movie.getVideoUrl());
        WebView webView = getWebView();
        FaselHdServer.RESULT_COUNTER = 0;
        //WebView webView = MyApplication.getWebView();
        int counter = 0;

        webView.setWebViewClient(new CustomWebViewClient(movie) {
            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                CookieManager cookieManager = CookieManager.getInstance();
                Log.d(TAG, "onLoadResource: Fasel:" + url + ", movie:" + movie.getVideoUrl());
                String extractMovies =
                        "let postList = [];\n" +
                                "let postDivs = document.getElementsByClassName(\"postDiv\");\n" +
                                "for (let i = 0; i < postDivs.length; i++) {\n" +
                                "    let post = {};\n" +
                                "    let postDiv = postDivs[i];\n" +
                                "    post.title = postDiv.getElementsByTagName(\"img\")[0].alt;\n" +
                                "    post.videoUrl = postDiv.getElementsByTagName(\"a\")[0].href;\n" +
                                "    post.cardImageUrl = postDiv.getElementsByTagName(\"img\")[0].getAttribute('data-src');\n" +
                                "    post.bgImageUrl = post.cardImageUrl;" +
                                "    post.studio = 'FaselHd';" +
                                "    post.state = 0;" +
                                "    postList.push(post);\n" +
                                "}\n" +
                                "postList;\n";
                Callback callBack = new Callback() {
                    @Override
                    public void onCallback(String value, int counter) {
                        Log.d(TAG, "onCallback: " + counter + ", " + url + ", " + value);
                        if (counter != 0) {
                            return;
                        }
                        webView.stopLoading();
                        // webView.destroy();
                        // Remove the WebView from its parent view
//                ViewGroup parent = (ViewGroup) webView.getParent();
//                if (parent != null) {
//                    parent.removeView(webView);
//                }
                        // Remove any child views from the WebView
                        // webView.removeAllViews();
                        // Destroy the WebView
                        //   webView.destroy();
//          Hiiir              setCookies(cookieManager.getCookie(movie.getVideoUrl()));
//               Hiiir         setHeaders(headers);
//                        Intent returnIntent = new Intent(activity, DetailsActivity.class);
//                        movie.setFetch(0); //tell next activity not to fetch movie on start
                        Gson gson = new Gson();
                        Type movieListType = new TypeToken<List<Movie>>() {
                        }.getType();
                        List<Movie> movies = gson.fromJson(value, movieListType);

                        for (Movie mov : movies) {
                                updateMovieState(movies.get(movies.indexOf(mov)));
                        }

                        String jsonMovies = gson.toJson(movies);
//                        returnIntent.putExtra(DetailsActivity.MOVIE, (Serializable) movie);
//                        returnIntent.putExtra(DetailsActivity.MOVIE_SUBLIST, jsonMovies);
//                        activity.startActivity(returnIntent);
//
//
//                        //returnIntent.putExtra("result", value);
//                        // activity.setResult(Activity.RESULT_OK, returnIntent);
//
//                        activity.finish();
//                        return; // to stop loading resources
                    }
                };

                //     if (url.equals(movie.getVideoUrl())) {
                webView.evaluateJavascript(extractMovies, new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                if (value.length() > 4) {
                                    Log.d(TAG, "onReceiveValue:tempValue1: " + value.length() + ", " + value);
                                    view.stopLoading();
                                    webView.stopLoading();
                                    callBack.onCallback(value, FaselHdServer.RESULT_COUNTER++);
//                                        setCookies(cookieManager.getCookie(movie.getVideoUrl()));
//                                        setHeaders(headers);
//                                        Intent returnIntent = new Intent();
//                                        returnIntent.putExtra("result", value);
//                                        activity.setResult(Activity.RESULT_OK, returnIntent);
//
//                                        activity.finish();
                                }
                            }
                        }
                );
                // }

            }
        });


        webView.loadUrl(movie.getVideoUrl());
    }

    interface Callback {
        void onCallback(String value, int counter);
    }

    private class ChromeClient extends WebChromeClient {
        private View mCustomView;
        private CustomViewCallback mCustomViewCallback;
        protected FrameLayout mFullscreenContainer;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;
        private float mOriginalMouseElevation;

        ChromeClient() {
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Log.d(TAG, "onJsAlert: " + message);
            result.cancel();
            //super.onJsAlert(view, url, null, result);
            return true;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            Log.d(TAG, "onJsBeforeUnload: " + url + ", m:" + message + ", re:" + result);
            result.cancel();
            return true;
        }


        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            Log.d(TAG, "onJsPrompt: " + message);
            result.cancel();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            Log.d(TAG, "onJsConfirm: " + message);
            result.cancel();
            return true;
        }


//        public Bitmap getDefaultVideoPoster() {
//            Log.d(TAG, "getDefaultVideoPoster: " + mCustomView);
//            if (mCustomView == null) {
//                return null;
//            }
//            Log.d(TAG, "getDefaultVideoPoster: " + BitmapFactory.decodeResource(activity.getApplicationContext().getResources(), 2130837573));
//            return BitmapFactory.decodeResource(activity.getApplicationContext().getResources(), 2130837573);
//        }

        public void onHideCustomView() {
            Log.d(TAG, "onHideCustomView: ");
//            ((FrameLayout) activity.getWindow().getDecorView()).removeView(this.mCustomView);
//            this.mCustomView = null;
//            activity.getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
//            activity.setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
            //mouseArrow.setElevation(this.mOriginalMouseElevation);
            //allowArrowEdgeScrolling = true;
        }

        public void onShowCustomView(View paramView, CustomViewCallback paramCustomViewCallback) {
            Log.d(TAG, "onShowCustomView: ");
            if (this.mCustomView != null) {
                onHideCustomView();
                return;
            }
            this.mCustomView = paramView;
//            this.mOriginalSystemUiVisibility = activity.getWindow().getDecorView().getSystemUiVisibility();
//            this.mOriginalOrientation = activity.getRequestedOrientation();
            this.mCustomViewCallback = paramCustomViewCallback;
            //this.mOriginalMouseElevation = mouseArrow.getElevation();

            FrameLayout frameLayout = (FrameLayout) paramView;
            View focusedChild = frameLayout.getFocusedChild();

            //   Log.d(TAG, "onShowCustomView: elevm:" + mouseArrow.getElevation()
            //         + ", elevV:" + paramView.getElevation() + ", elevFu:" + focusedChild.getElevation());
            // Set the mouse arrow's elevation to a higher value than the custom view's elevation
            //mouseArrow.setElevation(focusedChild.getElevation() + 1);


            Log.d(TAG, "onShowCustomView: custom:" + mCustomView);


            FrameLayout.LayoutParams newFrame = new FrameLayout.LayoutParams(-1, -1);
//            ((FrameLayout) activity.getWindow().getDecorView()).addView(this.mCustomView, newFrame);
//            activity.getWindow().getDecorView().setSystemUiVisibility(3846 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            // mouseArrow.bringToFront();
            //allowArrowEdgeScrolling = false;
            paramCustomViewCallback.onCustomViewHidden();
        }
    }

    private class CustomWebViewClient extends WebViewClient {

        private String currentVideoUrl = "";
        boolean scrolled = false;
        List<Movie> movieList = new ArrayList<>();
        Map<String, String> headers = new HashMap<>();
        CookieManager cookieManager = CookieManager.getInstance();
        boolean stopCode = false;
        int counter = 0;
        Movie movie;

        CustomWebViewClient(Movie movie) {
            //cookieManager.setAcceptCookie(true);
            this.movie = movie;
//            cookieManager.setAcceptThirdPartyCookies(webView, true);
//            CookieSyncManager.createInstance(activity);
            CookieSyncManager.getInstance().startSync();
//         Hiiiir   setCookies(cookieManager.getCookie(FaselHdController.WEBSITE_URL));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            //return super.shouldOverrideUrlLoading(view, request);
            String newUrl = request.getUrl().toString().length() > 25 ? request.getUrl().toString().substring(0, 25) : request.getUrl().toString();
            Log.d(TAG, "shouldOverrideUrlLoading: " + newUrl);
//  Hiir          if (!BrowserActivity.shouldOverride(newUrl)) {
//                view.loadUrl(request.getUrl().toString());
//            }
            return true;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            currentVideoUrl = "";
            movieList = new ArrayList<>();
            scrolled = false;
            stopCode = false;
            counter = 0;
            super.onPageStarted(view, url, favicon);
        }


        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            headers = request.getRequestHeaders();
            Log.d(TAG, "shouldInterceptRequest: headers:" + headers.toString());
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            //super.onReceivedSslError(view, handler, error);
            Log.d(TAG, "onReceivedSslError: " + error.toString());
            // Ignore SSL certificate errors
            handler.proceed();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            String cookies = cookieManager.getCookie(url);
            Log.d(TAG, "onPageFinished: " + url + ", Cookies:" + cookies);
            if (cookies == null) {
//         Hiiir       cookieManager.setCookie(url, getCookies());
            } else {
//    Hiiir            if (getCookies() == null) {
//                    setCookies(cookies);
//                } else {
//                    if (!getCookies().contains(cookies)) {
//                        // Add the cookie to the string
//                        setCookies(getCookies() + "; " + cookies);
//                    }
//                }
            }
            Log.d(TAG, "onPageFinished: cookie:" + cookies);
        }

    }

    @Override
    public String getWebScript(int mode, Movie movie) {
        String script = "";
        if (mode == BrowserActivity.WEB_VIEW_MODE_ON_PAGE_STARTED) {
            if (movie.getState() == Movie.COOKIE_STATE) {
                Log.d(TAG, "getScript:WEB_VIEW_MODE_ON_PAGE_STARTED COOKIE_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        // "alert(\"DOM ready!\");" +
                        "let postDivs = document.getElementsByClassName(\"postDiv\");" +
                        "if (postDivs.length > 0){" +
                        "let postList = [];" +
                        "for (let i = 0; i < postDivs.length; i++) {" +
                        "    let post = {};" +
                        "    let postDiv = postDivs[i];" +
                        "    post.title = postDiv.getElementsByTagName(\"img\")[0].alt;" +
                        "    let link = postDiv.getElementsByTagName(\"a\")[0].href;" +
                        "    post.videoUrl = link;" +
                        "    post.mainMovieTitle = link;" +
                        "    post.cardImageUrl = postDiv.getElementsByTagName(\"img\")[0].getAttribute('data-src');" +
                        "    post.bgImageUrl = post.cardImageUrl;" +
                        "    post.backgroundImageUrl = post.cardImageUrl;" +
                        "    post.studio = '" + Movie.SERVER_FASELHD + "';" +
                        "    post.mainMovieTitle = '" + movie.getMainMovieTitle() + "';" +
                        "    let u = post.videoUrl;" +
                        "    let n = post.title;" +
                        "    let series = u.includes(\"/seasons\") || n.includes(\"مسلسل\");\n" +
                        "    let item = u.includes(\"/episodes\") || n.includes(\"حلقة\") || n.includes(\"فيلم\") || n.includes(\"فلم\");\n" +
                        "    if (item) {\n" +
                        "       post.state = " + Movie.ITEM_STATE + ";\n" +
                        "    } else if (series) {" +
                        "       post.state = " + Movie.GROUP_OF_GROUP_STATE + ";" +
                        "    } else {" +
                        "       post.state = " + Movie.ITEM_STATE + ";\n" +
                        "    }" +
                        "    postList.push(post);" +
                        "}" +
                        //"postList;"+
                        "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                        "}" +
                        "});";
            }
            else if (movie.getState() == Movie.GROUP_OF_GROUP_STATE) {
                Log.d(TAG, "getScript:Fasel WEB_VIEW_MODE_ON_PAGE_STARTED GROUP_OF_GROUP_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", function () {\n" +
                        "let posterImg = document.querySelector(\".posterImg\");\n" +
                        "let backgroundImage = posterImg?.querySelector(\"img\")?.getAttribute(\"src\") || \"\";\n" +
                        "let trailer = posterImg?.querySelector(\"a\")?.getAttribute(\"href\") || \"\";\n" +
                        "\n" +
                        "let singleDesc = document.querySelector(\".singleDesc\");\n" +
                        "let description = singleDesc?.querySelector(\"p\")?.textContent.trim() || singleDesc?.textContent.trim() || \"\";\n" +
                        "\n" +
                        "let seasons = document.querySelectorAll(\".seasonDiv\");\n" +
                        "let postList = [];\n" +
                        "\n" +
                        "if (seasons.length === 0) {\n" +
                        "    console.log(\"No seasons found.\"); // Handle empty seasons gracefully\n" +
                        "} else {\n" +
                        "    seasons.forEach(function (season) {\n" +
                        "        let post = {};\n" +
                        "        let title = season.querySelector(\".title\")?.textContent || \"\";\n" +
                        "        let imgElement = season.querySelector(\"img\");\n" +
                        "        let cardImageUrl = imgElement?.getAttribute(\"data-src\") || imgElement?.getAttribute(\"src\") || \"\";\n" +
                        "        let onclickAttr = season.getAttribute(\"onclick\");\n" +
                        "        let spans = season.querySelectorAll(\".seasonMeta\");\n" +
                        "\n" +
                        "        let link = onclickAttr?.match(/\\?p=.[^']*/)?.[0]\n" +
                        "            ? `"+getConfig().getUrl()+"/${onclickAttr.match(/\\?p=.[^']*/)[0]}`\n" +
                        "            : \"\";\n" +
                        "    post.videoUrl = link;" +
                        "    post.mainMovieTitle = link;" +
                        "        post.title = title;\n" +
                        "        post.cardImageUrl = cardImageUrl;\n" +
                        "        post.bgImageUrl = cardImageUrl;\n" +
                        "        post.description = description;\n" +
                        "\n" +
                        "        post.rate = \"\";\n" +
                        "        spans.forEach(function (span) {\n" +
                        "            let rateElement = span.querySelector(\"*\");\n" +
                        "            if (rateElement) {\n" +
                        "                post.rate = rateElement.textContent;\n" +
                        "            }\n" +
                        "        });\n" +
                        "\n" +
                        "        post.state = 1;\n" +
                        "        post.studio = \""+Movie.SERVER_FASELHD+"\";\n" +
                        "\n" +
                        "        postList.push(post);\n" +
                        "    });\n" +
                        "\n" +
                        "    console.log(postList.length); // Use or process postList as needed\n" +
                        "    MyJavaScriptInterface.myMethod(JSON.stringify(postList));\n" +
                        "}\n" +
                        "});";
            }
            else if (movie.getState() == Movie.GROUP_STATE) {
                Log.d(TAG, "getScript:Fasel WEB_VIEW_MODE_ON_PAGE_STARTED GROUP_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        "// Parsing the background image\n" +
                        "let posterImg = document.querySelector(\".posterImg\");\n" +
                        "let backgroundImage = posterImg?.querySelector(\"img\")?.getAttribute(\"src\") || \"\";\n" +
                        "\n" +
                        "// Parsing the description\n" +
                        "let singleDesc = document.querySelector(\".singleDesc\");\n" +
                        "let description = singleDesc?.querySelector(\"p\")?.textContent.trim() || singleDesc?.textContent.trim() || \"\";\n" +
                        "\n" +
                        "// Parsing the episodes or seasons\n" +
                        "let episodeContainer = document.getElementById(\"epAll\");\n" +
                        "let episodeList = episodeContainer ? episodeContainer.querySelectorAll(\"a\") : null;\n" +
                        "\n" +
                        "let postList = [];\n" +
                        "\n" +
                        "// Handle episodes if they exist\n" +
                        "if (episodeList) {\n" +
                        "    episodeList.forEach(function (episodeDiv) {\n" +
                        "        let post = {};\n" +
                        "        post.title = episodeDiv.textContent.trim();\n" +
                        "        let link = episodeDiv.getAttribute(\"href\") || \"\";\n" +
                        "    post.videoUrl = link;" +
                        "    post.mainMovieTitle = link;" +
                        "        post.cardImageUrl = backgroundImage;\n" +
                        "        post.bgImageUrl = backgroundImage;\n" +
                        "        post.backgroundImageUrl = backgroundImage;\n" +
                        "        post.description = description;\n" +
                        "        post.state = 2; // ITEM_STATE equivalent\n" +
                        "        post.rate = \"\"; // Placeholder for rate\n" +
                        "        post.studio = \""+Movie.SERVER_FASELHD+"\"; // Example studio name\n" +
                        "        postList.push(post);\n" +
                        "    });\n" +
                        "} else {\n" +
                        "    console.log(\"No episodes found.\");\n" +
                        "}\n" +
                        "\n" +
                        "// Send the processed data back to the native interface\n" +
                        "    MyJavaScriptInterface.myMethod(JSON.stringify(postList));\n" +
                        "});";
            }
            else if (movie.getState() == Movie.ITEM_STATE) {
                Log.d(TAG, "getScript:Fasel WEB_VIEW_MODE_ON_PAGE_STARTED ITEM_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        "let iframe = document.querySelector('iframe[src]:not([src=\"about:blank\"])');" +
                        "  // Check if the iframe element was found" +
                        "  if (iframe) {" +
                        "    iframe.scrollIntoView({behavior: 'smooth'});" +
                        "  } " +
                        "});";

                script = "document.addEventListener(\"DOMContentLoaded\", () => {"
                        + "let postList = [];\n" +
                        "\n" +
                        "// Parsing the poster image\n" +
                        "let posterImg = document.querySelector(\".posterImg\");\n" +
                        "let backgroundImage = posterImg?.querySelector(\"img\")?.getAttribute(\"src\") || \"\";\n" +
                        "\n" +
                        "// Parsing the description\n" +
                        "let singleDesc = document.querySelector(\".singleDesc\");\n" +
                        "let description = singleDesc?.querySelector(\"p\")?.textContent.trim() || singleDesc?.textContent.trim() || \"\";\n" +
                        "\n" +
                        "// Parsing the resolutions tab\n" +
                        "let resolutionsTab = document.querySelector(\".signleWatch\");\n" +
                        "if (resolutionsTab) {\n" +
//                        "let headers = \""+ Util.generateHeadersForVideoUrl(getConfig().getHeaders()) +"\";" +
                        "    let episodeList = resolutionsTab.querySelectorAll(\"li\");\n" +
                        "\n" +
                        "    episodeList.forEach(function (episodeDiv) {\n" +
                        "        let post = {}; // Create a new post object for each episode\n" +
                        "\n" +
                        "        // Parsing title\n" +
                        "        let titleElement = episodeDiv.querySelector(\"a\");\n" +
                        "        post.title = titleElement?.textContent.trim() || \"\";\n" +
                        "\n" +
                        "        // Parsing video link\n" +
                        "        let onclickAttr = episodeDiv.getAttribute(\"onclick\");\n" +
                        "        let link = onclickAttr\n" +
                        "            ? onclickAttr.replace(\"player_iframe.location.href = \", \"\").replace(/'/g, \"\").trim()\n" +
                        "            : \"\";\n" +
                        "        // Append headers or additional parameters if required\n" +
                        "        post.videoUrl = link;"+
                        "    post.mainMovieTitle = link;" +
                        "        // Assign other properties\n" +
                        "        post.cardImageUrl = backgroundImage;\n" +
                        "        post.bgImageUrl = backgroundImage;\n" +
                        "        post.backgroundImageUrl = backgroundImage;\n" +
                        "        post.description = description;\n" +
                        "        post.rate = \"\";\n" +
                        "        post.state = "+Movie.RESOLUTION_STATE+";"+ // Assuming RESOLUTION_STATE equivalent\n" +
                        "        post.trailerUrl = \"\";\n" +
                        "        post.studio = \""+Movie.SERVER_FASELHD+"\"; // Example studio\n" +
                        "\n" +
                        "        // Add the post to the postList\n" +
                        "        postList.push(post);\n" +
                        "    });\n" +
                        "}\n" +
                        "\n" +
                        "    MyJavaScriptInterface.myMethod(JSON.stringify(postList));\n" +
                        "});";


            } else if (movie.getState() == Movie.RESOLUTION_STATE) {
                Log.d(TAG, "getScript:Fasel WEB_VIEW_MODE_ON_PAGE_STARTED RESOLUTION_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        "let buttons = document.getElementsByClassName('hd_btn');" +
                        " if (buttons.length > 0){" +
                        "     buttons[0].click();" +
                        "}" +
                        "});";
            }
        } else if (mode == BrowserActivity.WEB_VIEW_MODE_ON_LOAD_RESOURCES) {
            if (movie.getState() == Movie.ITEM_STATE) {
                Log.d(TAG, "getScript: fasel WEB_VIEW_MODE_ON_LOAD_RESOURCES ITEM_STATE");
                script =
//                            "let singeWatch = document.getElementsByClassName(\"signleWatch\");" +
//                                    "if(singeWatch.length > 0){" +
//                                    "singeWatch[0].getElementsByTagName('li')[1].click();" +
//                                    "singeWatch[0].getElementsByTagName('li')[0].click();" +
//                                    "}" +
                        "let singeWatch = document.getElementsByClassName('signleWatch');\n" +
                                "            if (singeWatch.length > 0) {\n" +
                                "                singeWatch[0].getElementsByTagName('li')[1].click();\n" +
                                "                //singeWatch[0].getElementsByTagName('li')[0].click();\n" +
                                "            }\n" +
                                "            let video = document.getElementsByTagName('video');\n" +
                                "            if (video.length > 0){\n" +
                                "                video[0].click();\n" +
                                "            }\n" +
                                "            let button = document.getElementsByClassName('hd_btn');\n" +
                                "            if (button.length > 0){\n" +
                                "                button[0].click();\n" +
                                "                window.frames['player_iframe'].document.querySelectorAll('.hd_btn selected')[0].click();" +
                                "MyJavaScriptInterface.myMethod('clicked')" +
                                "            }";
            }
        }

        Log.d(TAG, "getWebScript: " + script);
        return script;
    }

    @Override
    public ArrayList<Movie> getHomepageMovies(boolean handleCookie, ActivityCallback<ArrayList<Movie>> activityCallback) {
//        return search(getConfig().getUrl() + "/most_recent", activityCallback, handleCookie);
        return search(getConfig().getUrl() + "/movies", activityCallback, handleCookie);
//        return search("la casa", activityCallback, handleCookie);
    }

    public String getCustomUserAgent(int state) {
        return "Android 7";
    }
}
