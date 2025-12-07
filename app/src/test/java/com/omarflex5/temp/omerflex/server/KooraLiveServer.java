package com.omarflex5.temp.omerflex.server;

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
import com.omerflex.view.BrowserActivity;
import com.omerflex.view.VideoDetailsFragment;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KooraLiveServer extends AbstractServer {

    static String TAG = "KooraLive";
    static String WEBSITE_NAME = "kooraLive";
    public static String WEBSITE_URL = "https://kooora.live-koora.live";
    static boolean START_BROWSER_CODE = false;
    static boolean STOP_BROWSER_CODE = false;
    static int RESULT_COUNTER = 0;

    public KooraLiveServer() {
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
            doc = getRequestDoc(url, OmerFlexApplication.getAppContext());
        }else {
            doc = getSearchRequestDoc(url);
        }
        if (doc == null) {
            activityCallback.onInvalidLink("Invalid link");
            return null;
        }

        Log.d(TAG, "result stop title: " + doc.title());
        if (doc.title().contains("moment")) {
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
            m.setStudio(Movie.SERVER_KOORA_LIVE);
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
// Select all match-like blocks: divs that contain at least two team logos and a time/result pattern
        Elements matchBlocks = doc.select("div:has(img[alt])"); // Start broad: containers with team logos

        int matchCounter = 0;
        for (Element block : matchBlocks) {

            // Extract all team images (logos)
            Elements teamImages = block.select("img[alt]");
            if (teamImages.size() < 2) continue; // Must have two teams

            // Extract team data
            Element team1Img = teamImages.get(0);
            Element team2Img = teamImages.get(1);

            String team1Name = team1Img.attr("alt").trim();
            String team1Logo = team1Img.attr("data-src").trim();
            String team2Name = team2Img.attr("alt").trim();
            String team2Logo = team2Img.attr("data-src").trim();

            // Search within this block for a time or result pattern
            Element timeElement = block.selectFirst(":matchesOwn(^\\d{1,2}:\\d{2}$)");
            String matchTime = (timeElement != null) ? timeElement.text().trim() : "N/A";

            // Search for score pattern like "1-0" or "0 - 0"
            Element resultElement = block.selectFirst(":matchesOwn(^\\d+\\s*-\\s*\\d+$)");
            String matchResult = (resultElement != null) ? resultElement.text().trim() : "N/A";

            // Search for match status keywords (in English or Arabic)
            Element statusElement = block.selectFirst(":matchesOwn((?i)(Running|Live|Ended|Finished|مباشر|انتهت|لم تبدأ))");
            String matchStatus = (statusElement != null) ? statusElement.text().trim() : "N/A";

            // Extract match link (if exists)
            Element linkElement = block.selectFirst("a[href]");
            String matchLink = (linkElement != null) ? linkElement.attr("href").trim() : null;

            if (matchLink == null || matchLink.isEmpty() || matchLink.contains("today") || matchLink.contains("yesterday")){
                continue;
            }
            if (!matchLink.startsWith("http")){
                matchLink = getConfig().getUrl() + "/" + matchLink;
            }
            matchLink = matchLink + "|referer="+ getConfig().getUrl() + "/";

            // Skip if this is not a real match (filter false positives)
            if (team1Name.isEmpty() || team2Name.isEmpty()) continue;

            // Create Movie object
            Movie m = new Movie();
            m.setTitle(team1Name + " - " + team2Name);
            m.setDescription(matchStatus + " | " + matchTime);
            m.setStudio(Movie.SERVER_KOORA_LIVE);
            m.setVideoUrl(matchLink);
            m.setRate(matchResult.equals("N/A") ? matchTime : matchResult);
            m.setCardImageUrl(!team1Logo.isEmpty() ? team1Logo : team2Logo);
            m.setSearchContext(searchContext);

            movieList.add(updateMovieState(m));

            // Debug logs
            System.out.println("=================================");
            System.out.println("Team 1: " + team1Name);
            System.out.println("Team 2: " + team2Name);
            System.out.println("Time: " + matchTime);
            System.out.println("logo: " + team1Logo);
            System.out.println("Result: " + matchResult);
            System.out.println("Status: " + matchStatus);
            System.out.println("Link: " + matchLink);
        }


//        Elements containers = doc.select("div.match-container");
//        Log.d(TAG, "search: containers: "+containers.size());
//        for (Element container : containers) {
//            Element link = container.selectFirst("a");
//            String videoUrl = link != null ? link.attr("href") : "";
//
//            String rightTeam = container.selectFirst(".right-team .team-name").text();
//            String leftTeam = container.selectFirst(".left-team .team-name").text();
//            String title = leftTeam + " - " + rightTeam;
//
//            Element cardImageElement = container.selectFirst(".left-team .team-logo img");
//            String cardImage = cardImageElement != null ? cardImageElement.attr("data-src") : "";
//
//            Element matchTimeElem = container.selectFirst("#match-time");
//            String matchTime = matchTimeElem != null ? matchTimeElem.text() : "";
//
//            Element resultDescElem = container.selectFirst(".date.end");
//            String resultDescription = resultDescElem != null ? resultDescElem.text() : "";
//            String description = resultDescription + " at " + matchTime;
//
//            Movie m = new Movie();
//            m.setTitle(title);
//            m.setDescription(description);
//            m.setStudio(Movie.SERVER_KOORA_LIVE);
//            m.setState(Movie.BROWSER_STATE);
//            m.setVideoUrl(videoUrl);
//            m.setRate(description);
//            m.setCardImageUrl(cardImage);
//            m.setBackgroundImageUrl(cardImage);
//            m.setBgImageUrl(cardImage);
//            m.setSearchContext(searchContext);
//            m.setMainMovie(m);
//            movieList.add(m);
//        }

        activityCallback.onSuccess(movieList, getLabel());
        return movieList;
    }

    @Override
    public Movie updateMovieState(Movie movie) {
//        String u = movie.getVideoUrl();
//        String n = movie.getTitle();
//        if (u.contains("/series") || u.contains("/movies") || n.contains("فلام")){
//            movie.setState(Movie.GROUP_STATE);
//            movie.setType(MovieType.SEASON);
//            return movie;
//        }
//        movie.setState(Movie.ITEM_STATE);
//        if (u.contains("/movie/") || n.contains("فلم") || n.contains("فيلم")){
//            movie.setType(MovieType.FILM);
//            return movie;
//        }
//        if (u.contains("/episode") || n.contains("حلقة") || n.contains("حلقه")){
//            movie.setType(MovieType.EPISODE);
//        }
        movie.setState(Movie.BROWSER_STATE);
        movie.setType(MovieType.BROWSER);
        return movie;
    }



    //    @Override
    public ArrayList<Movie> searchKoora(String query, ActivityCallback<ArrayList<Movie>> activityCallback) {
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

        Document doc = this.getSearchRequestDoc(url);
        if (doc == null) {
            activityCallback.onInvalidLink("Invalid link");
            return null;
        }

        Log.d(TAG, "result stop title: " + doc.title());
        if (doc.title().contains("moment")) {
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
            m.setStudio(Movie.SERVER_KOORA_LIVE);
            m.setVideoUrl(url);
            //  m.setVideoUrl("https://www.google.com/");
            m.setState(Movie.COOKIE_STATE);
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

        // Find all divs that contain match-related info (dynamically)
        Elements matches = doc.select("div:has(div:matches(\\d{1,2}:\\d{2} (AM|PM)))");
        // Select each match block (instead of relying on class names)
//        Elements matchBlocks = doc.select("div:has(div:matchesOwn(\\d{1,2}:\\d{2} (AM|PM)))");

        for (Element match : matches) {
            // Ensure the div has at least 3 direct child divs (to confirm it's a match container)
            Log.d(TAG, "match div size: " + match.select("> div").size());
            if (match.select("> div").size() != 3) {
                continue; // Skip if it has less than 3 divs
            }

            // Extract team names (avoid picking up status messages)
            Elements teamNames = match.select("div:has(img) + div:matchesOwn([\\p{InArabic}]+)");

            String team1 = (teamNames.size() > 0 && teamNames.get(0) != null) ? teamNames.get(0).text().trim() : "N/A";
            String team2 = (teamNames.size() > 1 && teamNames.get(1) != null) ? teamNames.get(1).text().trim() : "N/A";

            // Extract match time
            Element timeElement = match.selectFirst("span:matchesOwn(\\d{1,2}:\\d{2} (AM|PM))");
            String matchTime = (timeElement != null) ? timeElement.text().trim() : "N/A";

            // Extract match status (should be different from team names)
            Element statusElement = match.selectFirst("div:matchesOwn(لم تبدأ بعد|مباشر|منتهية|انتهت)");
            String matchStatus = (statusElement != null) ? statusElement.text().trim() : "N/A";

            // Extract the match link
            String matchLink = "N/A";
            Element parentDiv = match.parent();
            if (parentDiv != null) {
                Element linkElement = parentDiv.selectFirst("a[href]");
                if (linkElement != null) {
                    matchLink = linkElement.attr("href").trim();
                    String userAgent = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Mobile Safari/537.36";
                    matchLink = matchLink + "|User-Agent=" + userAgent;
                }
            }

            String title = team1 + "-" + team2;
            String cardImageUrl = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";

            Movie m = new Movie();
            m.setTitle(title);
            m.setDescription(matchTime);
            m.setStudio(Movie.SERVER_KOORA_LIVE);
            m.setType(MovieType.BROWSER);
            m.setVideoUrl(matchLink);
            m.setRate(matchTime);
            m.setCardImageUrl(cardImageUrl);
            m.setSearchContext(searchContext);
            movieList.add(m);

            // Print extracted match details
            System.out.println("=================================");
            System.out.println("Team 1: " + team1);
            System.out.println("Team 2: " + team2);
            System.out.println("Match Time: " + matchTime);
            System.out.println("Match Status: " + matchStatus);
            System.out.println("Match Link: " + matchLink);
        }


//        Elements containers = doc.select("div.match-container");
//        Log.d(TAG, "search: containers: "+containers.size());
//        for (Element container : containers) {
//            Element link = container.selectFirst("a");
//            String videoUrl = link != null ? link.attr("href") : "";
//
//            String rightTeam = container.selectFirst(".right-team .team-name").text();
//            String leftTeam = container.selectFirst(".left-team .team-name").text();
//            String title = leftTeam + " - " + rightTeam;
//
//            Element cardImageElement = container.selectFirst(".left-team .team-logo img");
//            String cardImage = cardImageElement != null ? cardImageElement.attr("data-src") : "";
//
//            Element matchTimeElem = container.selectFirst("#match-time");
//            String matchTime = matchTimeElem != null ? matchTimeElem.text() : "";
//
//            Element resultDescElem = container.selectFirst(".date.end");
//            String resultDescription = resultDescElem != null ? resultDescElem.text() : "";
//            String description = resultDescription + " at " + matchTime;
//
//            Movie m = new Movie();
//            m.setTitle(title);
//            m.setDescription(description);
//            m.setStudio(Movie.SERVER_KOORA_LIVE);
//            m.setState(Movie.BROWSER_STATE);
//            m.setVideoUrl(videoUrl);
//            m.setRate(description);
//            m.setCardImageUrl(cardImage);
//            m.setBackgroundImageUrl(cardImage);
//            m.setBgImageUrl(cardImage);
//            m.setSearchContext(searchContext);
//            m.setMainMovie(m);
//            movieList.add(m);
//        }

        activityCallback.onSuccess(movieList, getLabel());
        return movieList;
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
        nextPage.setMainMovieTitle(videoUrl);

        return nextPage;
    }

    @Override
    protected ArrayList<Movie> getSearchMovieList(Document doc) {
        return null;
    }

    @Override
    public String getServerId() {
        return Movie.SERVER_KOORA_LIVE;
    }

    @Override
    protected String getSearchUrl(String query) {
        String searchUrl = query;
        if (query.contains("http")) {
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
            searchUrl = webLink;
        } else {
            searchUrl = webLink + "/";
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

    public MovieFetchProcess fetchBrowseItem(Movie movie, ActivityCallback<Movie> activityCallback) {
        Movie clonedMovie = Movie.clone(movie);
        clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
        // to do nothing and wait till result returned to activity only the first fetch
        activityCallback.onInvalidCookie(clonedMovie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, clonedMovie);
    }

    private MovieFetchProcess fetchCookie(Movie movie, ActivityCallback<Movie> activityCallback) {
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, movie);
    }

    private MovieFetchProcess fetchWatchLocally(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.d(TAG, "fetchWatchLocally: " + movie);
        if (movie.getState() == Movie.BROWSER_STATE || movie.getState() == Movie.RESOLUTION_STATE) {
//            Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
//            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, clonedMovie);
            activityCallback.onInvalidCookie(movie, getLabel());
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, movie);
        }
        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_EXOPLAYER, movie);
    }

    @Override
    protected MovieFetchProcess fetchItemAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        Log.d(TAG, "fetchItemAction: ");
        switch (action) {
            case Movie.BROWSER_STATE:
                Log.d(TAG, "fetchItemAction: BROWSER_STATE");
                return fetchBrowseItem(movie, activityCallback);
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

        Document doc = this.getSearchRequestDoc(movie.getVideoUrl());
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

        Document doc = this.getSearchRequestDoc(movie.getVideoUrl());
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

    private MovieFetchProcess fetchItem(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchItem: " + movie.getVideoUrl());

        Document doc = this.getSearchRequestDoc(movie.getVideoUrl());
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

        Elements buttons = doc.select("button");
        Log.d(TAG, "fetchItem: buttons:" + buttons.size());
        Log.d(TAG, "fetchItem: html:" + doc.outerHtml());

        for (Element button : buttons) {

            String title = button.attr("data-title"); // Or button.text() if the title is in the button's text
            String link = button.attr("data-url");
            String link2 = button.attr("data-url2");

            System.out.println("Title: " + title);
            System.out.println("Link: " + link);
            System.out.println("Link2: " + link2);
            System.out.println("---");

        }

//        Element descriptionElement = doc.selectFirst("div#postsacs > p");
//        String description = descriptionElement != null ? descriptionElement.text() : "";
//        movie.setDescription(description);
//
//        // Select all elements with class containing '-link'
//        Elements allUl = doc.select("ul");
//        Log.d(TAG, "fetchItem: uls: " + allUl.size());
//        for (Element ul : allUl) {
//            String classAttribute = ul.attr("class");
//            Log.d(TAG, "fetchItem: ul: " + classAttribute);
//            if (!classAttribute.contains("-menu")) {
//                continue;
//            }
//            Elements allLinks = ul.select("a");
//            Log.d(TAG, "fetchItem: links: " + allLinks.size());
//            for (Element link : allLinks) {
//                String linkClass = link.attr("class");
//                if (!linkClass.contains("-link")) {
//                    continue;
//                }
//                String videoLink = link.attr("href");
//                String title = link.text();
//
//                Movie a = Movie.clone(movie);
//                a.setTitle(title);
//                a.setVideoUrl(videoLink);
//                a.setState(Movie.RESOLUTION_STATE);
//                if (movie.getSubList() == null) {
//                    movie.setSubList(new ArrayList<>());
//                }
//                movie.addSubList(a);
//            }
//        }

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
        if (movie.getState() == Movie.BROWSER_STATE || movie.getState() == Movie.RESOLUTION_STATE) {
            Movie clonedMovie = Movie.clone(movie);
            clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
//            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, clonedMovie);
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


    private boolean isSeries(Movie movie) {
        String u = movie.getVideoUrl();
        String n = movie.getTitle();
        return u.contains("/seasons") || n.contains("مسلسل");
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

    @Override
    public boolean shouldInterceptRequest(WebView view, WebResourceRequest request, Movie movie){
        return true;
    }

    public void fetchWebResult(Movie movie) {
//        WebView webView = activity.findViewById(R.id.webView);
//        webView.loadUrl(movie.getVideoUrl());
        WebView webView = getWebView();
        KooraLiveServer.RESULT_COUNTER = 0;
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
                            if (isSeries(mov)) {
                                movies.get(movies.indexOf(mov)).setState(Movie.GROUP_OF_GROUP_STATE);
                            } else {
                                movies.get(movies.indexOf(mov)).setState(Movie.ITEM_STATE);
                            }
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
                                    callBack.onCallback(value, KooraLiveServer.RESULT_COUNTER++);
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

    public String getCustomUserAgent(int state) {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }

    @Override
    public String getWebScript(int mode, Movie movie) {
        String script = "";
        if (mode == BrowserActivity.WEB_VIEW_MODE_ON_PAGE_STARTED) {
            if (movie.getState() == Movie.BROWSER_STATE) {
                script = "document.addEventListener('DOMContentLoaded', () => {" +
                        "// Using querySelector (Recommended)\n" +
                        "let firstIframe = document.querySelector(\"iframe\");\n" +
                        "if(firstIframe != null){" +
                        "firstIframe.scrollIntoView({ behavior: \"smooth\", block: \"center\" });" +

                        "   makeFullScreen(firstIframe);" +
                        "removeEmptyIframesInside(firstIframe);" +
                        "" +
                        "" +
                        "" +
                        "let iframes = document.querySelectorAll('iframe'); // Check initial iframes on the page\n" +
                        "    iframes.forEach(iframe => {\n" +
                        "        let src = iframe.getAttribute('src');\n" +
                        "        if (src == null || src === \"\" || src === \"about:blank\") {\n" +
                        "            iframe.remove();\n" +
                        "            console.log(\"Initial iframe removed:\", iframe);\n" +
                        "        }\n" +
                        "    });" +


                        "console.log(\"iframe found\");" +
                        "return;" +
                        "}" +
                        "var divs = document.querySelectorAll('div');\n" +
                        "var matches = [];\n" +
                        "\n" +
                        "// Convert NodeList to Array (for older browsers)\n" +
                        "for (var i = 0; i < divs.length; i++) {\n" +
                        "    var div = divs[i];\n" +
                        "    var childDivs = div.querySelectorAll('div');\n" +
                        "    var hasMatchingText = false;\n" +
                        "\n" +
                        "    // Check if any child div matches the regex\n" +
                        "    for (var j = 0; j < childDivs.length; j++) {\n" +
                        "        var text = childDivs[j].textContent.trim();\n" +
                        "        var regex = /\\d{1,2}:\\d{2} (م|ص)/;\n" +
                        "        if (regex.test(text)) {\n" +
                        "            hasMatchingText = true;\n" +
                        "            break;\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n " +
                        "    // Check if the div has exactly 3 direct child divs\n" +
                        "    var directChildDivs = [];\n" +
                        "    for (var k = 0; k < div.children.length; k++) {\n" +
                        "        if (div.children[k].tagName === 'DIV') {\n" +
                        "            directChildDivs.push(div.children[k]);\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "    if (directChildDivs.length === 3 && hasMatchingText) {\n" +
                        "        matches.push(div);\n" +
                        "    }\n" +
                        "}\n" +

                        "function decryptUrl(t, e=3) {\n" +
                        "    t = Uint8Array.from(atob(t), t => t.charCodeAt(0));\n" +
                        "    const n = (new TextDecoder).decode(t);\n" +
                        "    let o = \"\";\n" +
                        "    for (let t = 0; t < n.length; t++) {\n" +
                        "        var r = n.codePointAt(t);\n" +
                        "        o += String.fromCodePoint(r - e)\n" +
                        "    }\n" +
                        "    return o\n" +
                        "}" +
                        "" +
                        "function removeEmptyIframesInside(iframe) {\n" +
                        "  if (!iframe || iframe.tagName !== 'IFRAME') {\n" +
                        "    return; // Handle cases where the input isn't an iframe\n" +
                        "  }\n" +
                        "    // Example ad removal (highly site-specific!)" +
                        "    var ads = document.querySelectorAll('.ad-class, #ad-id, [data-ad-attribute]');" + // Replace with actual selectors
                        "    for (var i = 0; i < ads.length; i++) {" +
                        "        ads[i].remove();" +
                        "    }" +

                        // Another example: Removing elements based on their source URL (for image ads)
                        "    var adImages = document.querySelectorAll('img[src*=\"ad-provider.com\"]');" + // Replace with ad provider's domain
                        "    for (var i = 0; i < adImages.length; i++) {" +
                        "        adImages[i].remove();" +
                        "    }" +
                        "\n" +
                        "  iframe.addEventListener('load', function() { // Wait for the iframe to load\n" +
                        "    const innerIframes = iframe.contentDocument.querySelectorAll('iframe');\n" +
                        "\n" +
                        "    innerIframes.forEach(innerIframe => {\n" +
                        "      const src = innerIframe.getAttribute('src');\n" +
                        "      if (src === null || src === \"\" || src === \"about:blank\") {\n" +
                        "        innerIframe.remove();\n" +
                        "        console.log(\"Removed iframe inside iframe:\", innerIframe);\n" +
                        "      }\n" +
                        "    });\n" +
                        "\n" +
                        "\n" +
                        "    // Set up an observer *inside* the loaded iframe to watch for further changes within it.\n" +
                        "    const innerObserver = new MutationObserver(mutations => {\n" +
                        "        for (const mutation of mutations) {\n" +
                        "            if (mutation.type === 'childList') {\n" +
                        "                mutation.addedNodes.forEach(node => {\n" +
                        "                    if (node.nodeName === 'IFRAME') {\n" +
                        "                        const src = node.getAttribute('src');\n" +
                        "                        if (src === null || src === \"\" || src === \"about:blank\") {\n" +
                        "                            node.remove();\n" +
                        "                            console.log(\"Dynamically added iframe inside iframe removed:\", node);\n" +
                        "                        }\n" +
                        "                    }\n" +
                        "                });\n" +
                        "            }\n" +
                        "        }\n" +
                        "    });\n" +
                        "\n" +
                        "    innerObserver.observe(iframe.contentDocument.documentElement, { childList: true, subtree: true }); // Observe the inner iframe's document\n" +
                        "\n" +
                        "  });\n" +
                        "}" +

                        "" +
                        "function makeFullScreen(iframe) {\n" +
                        "    if (!iframe) return; // Null-safe check\n" +
                        "\n" +
                        "    // Apply full-screen styles\n" +
                        "    Object.assign(iframe.style, {\n" +
                        "        position: \"fixed\",\n" +
                        "        top: \"0\",\n" +
                        "        left: \"0\",\n" +
                        "        width: \"100vw\",\n" +
                        "        height: \"100vh\",\n" +
                        "        border: \"none\",\n" +
                        "        margin: \"0\",\n" +
                        "        padding: \"0\",\n" +
                        "        zIndex: \"9999\", // Bring iframe to the front\n" +
                        "    });\n" +
                        "\n" +
                        "    // Ensure the body does not interfere\n" +
                        "    document.body.style.overflow = \"hidden\";\n" +
                        "}\n" +
                        "" +


                        " });";
            }
//            if (movie.getState() == Movie.BROWSER_STATE) {
//
//                String referer = Util.extractDomain(movie.getVideoUrl(), true, true);
//                int hashIndex = movie.getVideoUrl().indexOf('#');
//
//                // Check if the hash symbol is found in the string
//                if (hashIndex != -1 && hashIndex < movie.getVideoUrl().length() - 1) {
//                    // Return the substring after the hash symbol
//                    String serverId = movie.getVideoUrl().substring(hashIndex + 1);
//
//                    // Find the index of the '|' symbol in the substring after the hash
//                    int pipeIndex = serverId.indexOf('|');
//
//                    // If '|' is found, return the substring up to the '|' symbol
//                    if (pipeIndex != -1) {
//                        serverId = serverId.substring(0, pipeIndex);
//                    }
//
//                    Log.d(TAG, "getWebScript: serverid: " + serverId);
//                    script = "document.addEventListener('DOMContentLoaded', () => {" +
//                            "// Using querySelector (Recommended)\n" +
//                            "let firstIframe = document.querySelector(\"iframe\");\n" +
//                            "if(firstIframe != null){" +
//                            "firstIframe.scrollIntoView({ behavior: \"smooth\", block: \"center\" });" +
//
//                            "   makeFullScreen(firstIframe);" +
//                            "removeEmptyIframesInside(firstIframe);" +
//                            "" +
//                            "" +
//                            "" +
//                            "let iframes = document.querySelectorAll('iframe'); // Check initial iframes on the page\n" +
//                            "    iframes.forEach(iframe => {\n" +
//                            "        let src = iframe.getAttribute('src');\n" +
//                            "        if (src == null || src === \"\" || src === \"about:blank\") {\n" +
//                            "            iframe.remove();\n" +
//                            "            console.log(\"Initial iframe removed:\", iframe);\n" +
//                            "        }\n" +
//                            "    });" +
//
//
//                            "console.log(\"iframe found\");" +
//                            "return;" +
//                            "}" +
//                            "var divs = document.querySelectorAll('div');\n" +
//                            "var matches = [];\n" +
//                            "\n" +
//                            "// Convert NodeList to Array (for older browsers)\n" +
//                            "for (var i = 0; i < divs.length; i++) {\n" +
//                            "    var div = divs[i];\n" +
//                            "    var childDivs = div.querySelectorAll('div');\n" +
//                            "    var hasMatchingText = false;\n" +
//                            "\n" +
//                            "    // Check if any child div matches the regex\n" +
//                            "    for (var j = 0; j < childDivs.length; j++) {\n" +
//                            "        var text = childDivs[j].textContent.trim();\n" +
//                            "        var regex = /\\d{1,2}:\\d{2} (م|ص)/;\n" +
//                            "        if (regex.test(text)) {\n" +
//                            "            hasMatchingText = true;\n" +
//                            "            break;\n" +
//                            "        }\n" +
//                            "    }\n" +
//                            "\n " +
//                            "    // Check if the div has exactly 3 direct child divs\n" +
//                            "    var directChildDivs = [];\n" +
//                            "    for (var k = 0; k < div.children.length; k++) {\n" +
//                            "        if (div.children[k].tagName === 'DIV') {\n" +
//                            "            directChildDivs.push(div.children[k]);\n" +
//                            "        }\n" +
//                            "    }\n" +
//                            "\n" +
//                            "    if (directChildDivs.length === 3 && hasMatchingText) {\n" +
//                            "        matches.push(div);\n" +
//                            "    }\n" +
//                            "}\n" +
//                            "\n" +
//                            "if (matches[" + serverId + "] != null) {\n" +
//                            " var closestAnchor = matches[" + serverId + "].closest('a'); // Find the closest <a> element\n" +
//                            "    if (closestAnchor) {\n" +
//                            " console.log(\"Closest <a> element found:\", closestAnchor);\n" +
//                            "        console.log(\"Closest <a> href:\", closestAnchor.title);\n" +
//                            "" +
////                                "closestAnchor.preventDefault();\n" +
//                            "             // Get the encrypted URL from the data-href attribute\n" +
//                            "        let encryptedUrl = closestAnchor.getAttribute(\"data-href\");\n" +
//                            "\n" +
//                            "        // Decrypt the URL\n" +
//                            "        let decryptedUrl = decryptUrl(encryptedUrl);\n" +
//                            "\n" +
//                            "        // Navigate to the decrypted URL\n" +
//                            "        console.log(\"Closest <a> location:\", decryptedUrl);\n" +
//                            "        window.location.href = decryptedUrl;" +
//                            "    } else {\n" +
//                            "        console.log(\"No <a> element found.\");\n" +
//                            "    }" +
//                            "    // Wait for the second element to appear after the click\n" +
//
//                            "}\n" +
//                            "\nconsole.log(matches[" + serverId + "]);" +
//                            "\nconsole.log('hiii');" +
//                            "\nconsole.log(matches.length);" +
//                            "" +
//                            "function decryptUrl(t, e=3) {\n" +
//                            "    t = Uint8Array.from(atob(t), t => t.charCodeAt(0));\n" +
//                            "    const n = (new TextDecoder).decode(t);\n" +
//                            "    let o = \"\";\n" +
//                            "    for (let t = 0; t < n.length; t++) {\n" +
//                            "        var r = n.codePointAt(t);\n" +
//                            "        o += String.fromCodePoint(r - e)\n" +
//                            "    }\n" +
//                            "    return o\n" +
//                            "}" +
//                            "" +
//                            "function removeEmptyIframesInside(iframe) {\n" +
//                            "  if (!iframe || iframe.tagName !== 'IFRAME') {\n" +
//                            "    return; // Handle cases where the input isn't an iframe\n" +
//                            "  }\n" +
//                            "    // Example ad removal (highly site-specific!)" +
//                            "    var ads = document.querySelectorAll('.ad-class, #ad-id, [data-ad-attribute]');" + // Replace with actual selectors
//                            "    for (var i = 0; i < ads.length; i++) {" +
//                            "        ads[i].remove();" +
//                            "    }" +
//
//                            // Another example: Removing elements based on their source URL (for image ads)
//                            "    var adImages = document.querySelectorAll('img[src*=\"ad-provider.com\"]');" + // Replace with ad provider's domain
//                            "    for (var i = 0; i < adImages.length; i++) {" +
//                            "        adImages[i].remove();" +
//                            "    }" +
//                            "\n" +
//                            "  iframe.addEventListener('load', function() { // Wait for the iframe to load\n" +
//                            "    const innerIframes = iframe.contentDocument.querySelectorAll('iframe');\n" +
//                            "\n" +
//                            "    innerIframes.forEach(innerIframe => {\n" +
//                            "      const src = innerIframe.getAttribute('src');\n" +
//                            "      if (src === null || src === \"\" || src === \"about:blank\") {\n" +
//                            "        innerIframe.remove();\n" +
//                            "        console.log(\"Removed iframe inside iframe:\", innerIframe);\n" +
//                            "      }\n" +
//                            "    });\n" +
//                            "\n" +
//                            "\n" +
//                            "    // Set up an observer *inside* the loaded iframe to watch for further changes within it.\n" +
//                            "    const innerObserver = new MutationObserver(mutations => {\n" +
//                            "        for (const mutation of mutations) {\n" +
//                            "            if (mutation.type === 'childList') {\n" +
//                            "                mutation.addedNodes.forEach(node => {\n" +
//                            "                    if (node.nodeName === 'IFRAME') {\n" +
//                            "                        const src = node.getAttribute('src');\n" +
//                            "                        if (src === null || src === \"\" || src === \"about:blank\") {\n" +
//                            "                            node.remove();\n" +
//                            "                            console.log(\"Dynamically added iframe inside iframe removed:\", node);\n" +
//                            "                        }\n" +
//                            "                    }\n" +
//                            "                });\n" +
//                            "            }\n" +
//                            "        }\n" +
//                            "    });\n" +
//                            "\n" +
//                            "    innerObserver.observe(iframe.contentDocument.documentElement, { childList: true, subtree: true }); // Observe the inner iframe's document\n" +
//                            "\n" +
//                            "  });\n" +
//                            "}" +
//
//                            "" +
//                            "function makeFullScreen(iframe) {\n" +
//                            "    if (!iframe) return; // Null-safe check\n" +
//                            "\n" +
//                            "    // Apply full-screen styles\n" +
//                            "    Object.assign(iframe.style, {\n" +
//                            "        position: \"fixed\",\n" +
//                            "        top: \"0\",\n" +
//                            "        left: \"0\",\n" +
//                            "        width: \"100vw\",\n" +
//                            "        height: \"100vh\",\n" +
//                            "        border: \"none\",\n" +
//                            "        margin: \"0\",\n" +
//                            "        padding: \"0\",\n" +
//                            "        zIndex: \"9999\", // Bring iframe to the front\n" +
//                            "    });\n" +
//                            "\n" +
//                            "    // Ensure the body does not interfere\n" +
//                            "    document.body.style.overflow = \"hidden\";\n" +
//                            "}\n" +
//                            "" +
//
//
//                            " });";
//                }
//
//
////                Log.d(TAG, "getScript:Fasel WEB_VIEW_MODE_ON_PAGE_STARTED RESOLUTION_STATE");
////                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
////                        "let firstIframe = document.querySelector(\"iframe\");\n" +
////                        "\n" +
////                        "// Check if the iframe exists\n" +
////                        "if (firstIframe) {\n" +
////                        "    // Scroll the page to the position of the first iframe\n" +
////                        "    firstIframe.scrollIntoView({\n" +
////                        "        behavior: 'smooth', // For smooth scrolling\n" +
////                        "        block: 'start', // Align the iframe to the top of the viewport\n" +
////                        "    });\n" +
////                        "}" +
////                        "});";
//            }
        }

        Log.d(TAG, "getWebScript: " + script);
        return script;
    }

    @Override
    public ArrayList<Movie> getHomepageMovies(boolean handleCookie, ActivityCallback<ArrayList<Movie>> activityCallback) {
//        return search(getConfig().getUrl() + "matches-today", activityCallback);
//        return search(getConfig().getUrl() + "/matches-today/", activityCallback);
        return search(getConfig().getUrl() + "/", activityCallback, handleCookie);
//        return search(getConfig().getUrl() + "/yesterday", activityCallback);
//        return search(getConfig().getUrl() + "/matches-yesterday/", activityCallback);
    }

    public boolean shouldUpdateDomainOnSearchResult() {
        return true;
    }
}
