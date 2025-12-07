package com.omarflex5.temp.omerflex.server;

import android.util.Log;

import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.entity.MovieType;
import com.omerflex.entity.ServerConfig;
import com.omerflex.view.BrowserActivity;
import com.omerflex.view.VideoDetailsFragment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LarozaServer extends AbstractServer{

    static String TAG = "Laroza";
    static String WEBSITE_URL = "https://www.laroza.now";

    @Override
    protected String getSearchUrl(String query) {
        if (query.contains("http")) {
            return query;
        }
        String searchPart = "/search.php?keywords=";
        ServerConfig config = getConfig();
        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            return WEBSITE_URL + searchPart + query;
        }

        if (query.startsWith("/")) {
            return config.getUrl() + query;
        }

        return config.getUrl() + searchPart + query;
    }

    public ArrayList<Movie> search(String query, ActivityCallback<ArrayList<Movie>> activityCallback, boolean handleCookie) {
        Log.i(getLabel(), "search: " + query);
        String url = query;
        if (!query.contains("http")) {
            url = this.getSearchUrl(query);
        }
        Log.d(TAG, "search: url: " + url);
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
        ArrayList<Movie> movieList = new ArrayList<>();

        if (doc.title().contains("moment")) {
//        if (true) {
//            setCookieRefreshed(false);
            //**** default
            // String title = "ابحث في موقع فاصل ..";
            String title = query;
            //int imageResourceId = R.drawable.default_image;
            // String cardImageUrl = "android.resource://" + activity.getPackageName() + "/" + imageResourceId;
            String cardImageUrl = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
            String backgroundImageUrl = "http://commondatastorage.googleapis.com/android-tv/Sample%20videos/April%20Fool's%202013/Introducing%20Google%20Nose/bg.jpg";
            Movie m = new Movie();
            m.setTitle(title);
            m.setDescription("نتائج البحث في الاسفل...");
            m.setStudio(Movie.SERVER_LAROZA);
            m.setVideoUrl(doc.location());
            //  m.setVideoUrl("https://www.google.com/");
            m.setState(Movie.COOKIE_STATE);
            m.setType(MovieType.COOKIE);
            // m.setState(Movie.RESULT_STATE);
            m.setCardImageUrl(cardImageUrl);
            m.setBackgroundImageUrl(backgroundImageUrl);
            m.setBgImageUrl(backgroundImageUrl);
            m.setRate("");
            m.setSearchContext(query);
            movieList.add(m);

            activityCallback.onInvalidCookie(movieList, getLabel());
            return movieList;
        }

        movieList = generateSearchResultFromDoc(doc, url);
        activityCallback.onSuccess(movieList, getLabel());
        return movieList;
    }

    private ArrayList<Movie> generateSearchResultFromDoc(Document doc, String searchUrl) {

        // Get all thumbnail elements
        Elements thumbnails = doc.select(".thumbnail");
        ArrayList<Movie> movieList = new ArrayList<>();
        // Iterate through each thumbnail
        for (Element thumbnail : thumbnails) {
            Movie movie = new Movie();
            // Extract title
            Element titleElement = thumbnail.selectFirst(".caption");
            String title = titleElement != null ? titleElement.text().trim() : null;

            // Extract video URL
            Element videoUrlElement = thumbnail.selectFirst(".pm-video-thumb a:not(.pm-watch-later-add)");
            String videoUrl = videoUrlElement != null ? videoUrlElement.attr("href") : null;
            if (videoUrl == null || title == null){continue;}

            title = title.replace("مسلسل", "");

            if (!videoUrl.startsWith("http")){
                videoUrl = getConfig().getUrl() + "/" + videoUrl;
            }
//            Log.d(TAG, "search result url: " + videoUrl);

            // Extract card image URL
            Element cardImageElement = thumbnail.selectFirst(".pm-video-thumb img");
// Check for common attributes where the real image URL might be stored
            String cardImage = null;
            if (cardImageElement != null) {

                if (cardImageElement.hasAttr("data-echo")) {
                    cardImage = cardImageElement.attr("data-echo"); // Lazy-loaded image
                } else
                if (cardImageElement.hasAttr("data-src")) {
                    cardImage = cardImageElement.attr("data-src"); // Lazy-loaded image
                } else if (cardImageElement.hasAttr("data-original")) {
                    cardImage = cardImageElement.attr("data-original"); // Alternative attribute
                } else {
                    cardImage = cardImageElement.attr("src"); // Default src attribute
                }
            }

//            Log.d(TAG, "generateSearchResultFromDoc: image:"+cardImage);

            movie.setTitle(title);
            movie.setDescription(title);
            movie.setCardImageUrl(cardImage);
            movie.setBackgroundImageUrl(cardImage);
            movie.setBgImageUrl(cardImage);
            movie.setVideoUrl(videoUrl);
            movie.setStudio(Movie.SERVER_LAROZA);
            movie = updateMovieState(movie);
            movieList.add(movie);
        }
        Log.d(TAG, "generateSearchResultFromDoc: movielist: "+ movieList.size());
        if (searchUrl != null){
            Movie nextPage = generateNextPageMovie(doc, searchUrl);
            if (!movieList.isEmpty()) {
                movieList.add(nextPage);
            }
        }
        return movieList;
    }


    @Override
    public Movie updateMovieState(Movie movie) {
        String u = movie.getVideoUrl();
        String n = movie.getTitle();
        boolean seriesCase = u.contains("-serie");

        if (seriesCase){
            movie.setState(Movie.GROUP_STATE);
            movie.setType(MovieType.SEASON);
            return movie;
        }
        movie.setState(Movie.ITEM_STATE);
        if (n.contains("حلقة") || n.contains("حلقه")){
            movie.setType(MovieType.EPISODE);
        }
        if (n.contains("فلم") || n.contains("فيلم")){
            movie.setType(MovieType.FILM);
        }
        return movie;
    }

    private Movie generateNextPageMovie(Document doc, String searchUrl) {
        // Find the pagination <ul>
//        Movie nextPage = null;
        // Find the <i> element with class "fa fa-arrow-left"
//        Element iElement = doc.selectFirst("i.fa.fa-arrow-left");
//        Element aElement = doc.selectFirst("a:has(i.fa.fa-arrow-left)");
//        if (aElement == null) {
//            Log.d(TAG, "generateNextPageMovie: aElement: not found");
//            Log.d(TAG, "generateNextPageMovie: "+ doc.body());
//            return null; // or throw an exception
//        }

//        // Find the closest ancestor 'a' element
//        Element aElement = iElement.closest("a");
//
//        if (aElement == null) {
//            Log.d(TAG, "generateNextPageMovie: iElement link: not found");
//            return null; // or throw an exception if no 'a' is found
//        }

//        String nextPageUrl  = aElement.attr("href");
        Log.d(TAG, "generateNextPageMovie: ");

        // Regular expression to capture only the number after "&page="
        Pattern pattern = Pattern.compile("&page=(\\d+)");
        Matcher matcher = pattern.matcher(searchUrl);
        String nextPageUrl = null;
        if (matcher.find()) {
//            String pageValue = matcher.group(); // Extracts "&page=33"
            String pageNumber = matcher.group(1); // Extracts only "33"
            if (pageNumber == null){return null;}
            int newPageNumber = Integer.parseInt(pageNumber) + 1; // Increment the number
            System.out.println("Page Number: " + pageNumber);

            // Replace the old page number with the new one
            nextPageUrl = searchUrl.replaceFirst("&page=" + pageNumber, "&page=" + newPageNumber);

            System.out.println("Updated URL: " + nextPageUrl);
        }else {
            nextPageUrl = searchUrl + "&page=2";
        }
        System.out.println("nextPageUrl: " + nextPageUrl);
        Movie nextPage = new Movie();
        nextPage.setTitle("التالي");
        nextPage.setDescription("0");
        nextPage.setStudio(Movie.SERVER_LAROZA);
        nextPage.setVideoUrl(nextPageUrl);
        nextPage.setCardImageUrl("https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png");
        nextPage.setBackgroundImageUrl("https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png");
        nextPage.setState(Movie.NEXT_PAGE_STATE);
        nextPage.setType(MovieType.NEXT_PAGE);
        return nextPage;
    }

    @Override
    protected ArrayList<Movie> getSearchMovieList(Document doc) {
        return null;
    }

    @Override
    protected MovieFetchProcess fetchSeriesAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        if (action == Movie.GROUP_OF_GROUP_STATE) {
            return fetchGroupOfGroup(movie, activityCallback);
        }
        return fetchGroup(movie, activityCallback);
    }

    private MovieFetchProcess fetchGroupOfGroup(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchGroupOfGroup: " + movie.getVideoUrl());
        String url = movie.getVideoUrl();
        Log.i(TAG, "ur:" + url);
        Document doc = getRequestDoc(url, OmerFlexApplication.getAppContext());
        if (doc == null) {
            activityCallback.onInvalidLink(movie);
            return null;
        }
        if (doc.title().contains("Just a moment")) {
//            Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//            return startWebForResultActivity(clonedMovie);
            activityCallback.onInvalidCookie(movie, getLabel());
        }

        // Find the seasons <section> by its aria-label attribute
        Element seasonsSection = doc.selectFirst("section[aria-label=seasons]");
        if (seasonsSection != null) {
            // Select all <li> elements within the <ul>
            Elements seasonItems = seasonsSection.select("ul li");

            // Iterate over each <li> element
            for (Element li : seasonItems) {
                Movie season = Movie.clone(movie);
                season.setParentId(movie.getParentId());
                // Extract the link and the title from the <a> tag
                Element link = li.selectFirst("a");
                if (link != null) {
                    String videoUrl = link.attr("href").trim();
                    String title = link.text().trim();

                    // Set the movie properties
                    season.setVideoUrl(videoUrl);
                    season.setTitle(title);
                    season.setState(Movie.GROUP_STATE);
                    season.setType(MovieType.SEASON);

                    // Output the movie object
                    System.out.println(season);
                    movie.addSubList(season);
                }
            }
            activityCallback.onSuccess(movie, getLabel());
        } else {
            System.out.println("Seasons section not found.");
        }

//        return generateGroupOfGroupMovie(doc, movie, activityCallback);
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    private MovieFetchProcess fetchGroup(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchGroup: " + movie.getVideoUrl());

        String url = movie.getVideoUrl();
        //         Log.i(TAG, "ur:" + url);
//            Document doc = Jsoup.connect(url).header(
//                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8").header(
//                    "User-Agent", " Mozilla/5.0 (Linux; Android 8.1.0; Android SDK built for x86 Build/OSM1.180201.031; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/69.0.3497.100 Mobile Safari/537.36").header(
//                    "accept-encoding", "gzip, deflate").header(
//                    "accept-language", "en,en-US;q=0.9").header(
//                    "x-requested-with", "pc1"
//            ).timeout(6000).get();
        //Elements links = doc.select("a[href]");

        Document doc = getRequestDoc(url, OmerFlexApplication.getAppContext());
        if (doc == null) {
            Log.d(TAG, "fetchGroup: error doc is null ");
            activityCallback.onInvalidLink(movie);
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, movie);
        }

        if (doc.title().contains("Just a moment")) {
//            Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//            return startWebForResultActivity(clonedMovie);
            activityCallback.onInvalidCookie(movie, getLabel());
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, movie);
        }

        ArrayList<Movie> movieList = generateSearchResultFromDoc(doc, null);
        movie.setSubList(movieList);
        activityCallback.onSuccess(movie, getLabel());
        return  new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }


    @Override
    protected MovieFetchProcess fetchItemAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
//        Log.d(TAG, "fetchItemAction: 55");
        switch (action) {
            case Movie.BROWSER_STATE:
//            case Movie.RESOLUTION_STATE:
                return fetchBrowseItem(movie, activityCallback);
//            case Movie.COOKIE_STATE:
//                return fetchCookie(movie);
            case Movie.ACTION_WATCH_LOCALLY:
                return fetchWatchLocally(movie, activityCallback);
            case Movie.RESOLUTION_STATE:
                return fetchResolutions(movie, activityCallback);
//            case Movie.VIDEO_STATE:
//                return fetchVideo(movie);
            default:
                return fetchItem(movie, activityCallback);
        }
    }
    public MovieFetchProcess fetchResolutions(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.d(TAG, "fetchResolutions: ");
        Movie clonedMovie = Movie.clone(movie);
        clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
        // to do nothing and wait till result returned to activity only the first fetch
//        return startWebForResultActivity(clonedMovie);
        activityCallback.onInvalidCookie(clonedMovie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, clonedMovie);
    }
    public MovieFetchProcess fetchBrowseItem(Movie movie, ActivityCallback<Movie> activityCallback) {
        Movie clonedMovie = Movie.clone(movie);
        clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
        // to do nothing and wait till result returned to activity only the first fetch
//        return startWebForResultActivity(clonedMovie);
        activityCallback.onInvalidCookie(clonedMovie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, clonedMovie);
    }

    private MovieFetchProcess fetchWatchLocally(Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.d(TAG, "fetchWatchLocally: ");
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
    public int fetchNextAction(Movie movie) {
        Log.d(TAG, "fetchNextAction: " + movie);
        switch (movie.getState()) {
            case Movie.GROUP_STATE:
            case Movie.ITEM_STATE:
                return VideoDetailsFragment.ACTION_OPEN_DETAILS_ACTIVITY;
            case Movie.VIDEO_STATE:
                return VideoDetailsFragment.ACTION_OPEN_EXTERNAL_ACTIVITY;
//           case Movie.RESOLUTION_STATE:
//               if (movie.getFetch() == 1 || movie.getFetch() == 0){
//                   return VideoDetailsFragment.ACTION_OPEN_EXTERNAL_ACTIVITY;
//               }
//                break;
        }
        return VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY;
    }

    private MovieFetchProcess fetchItem(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchItem: " + movie.getVideoUrl());

        String url = movie.getVideoUrl();
        if (url.contains("video.php")){
            url = movie.getVideoUrl().replace("video.php", "play.php");
        }

        // get watch link referer
        Document doc = getRequestDoc(url, OmerFlexApplication.getAppContext());
        if (doc == null) {
            activityCallback.onInvalidLink(movie);
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, movie);
        }
        Log.d(TAG, "fetchItem: title: "+ doc.title()+", "+doc.title().contains("Just a moment"));
//        if (true) {
        if (doc.title().contains("Just a moment")) {
//            Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//            return startWebForResultActivity(clonedMovie);
            activityCallback.onInvalidCookie(movie, getLabel());
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, movie);
        }


        // Select the .description element
        Element descriptionDiv = doc.selectFirst(".description");

        if (descriptionDiv != null) {
            // Select all p elements within the .description element
            Elements pElements = descriptionDiv.select("p");

            // Iterate through the p elements and extract the text
            for (Element pElement : pElements) {
                String text = pElement.text();
                if (!text.trim().isEmpty()) {
                    System.out.println("Extracted Text: " + text);
                    movie.setDescription(text);
                }
            }
        } else {
            System.out.println("Could not find the .description element.");
        }

        Elements servers = doc.select("ul.WatchList li");

        if (servers.isEmpty()){
            activityCallback.onInvalidCookie(movie, getLabel());
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, movie);
        }
        String referer = Util.extractDomain(movie.getVideoUrl(), true, true);
        // Extract and print server details
        for (Element server : servers) {
            String title = server.select("strong").text();
            String dataIndex = server.attr("data-embed-url");
//            String dataIndex = server.attr("data-embed-id");
            Movie resolution = Movie.clone(movie);
            resolution.setParentId(movie.getId());
            resolution.setState(Movie.RESOLUTION_STATE);
            resolution.setType(MovieType.RESOLUTION);
            resolution.setTitle(title);

            resolution.setVideoUrl(dataIndex + "|Referer="+referer);
//            resolution.setVideoUrl(url + "#"+dataIndex);

            movie.addSubList(resolution);
        }

        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    @Override
    public String getWebScript(int mode, Movie movie) {
        String script = null;

        Log.d(TAG, "getWebScript: m:" + mode + ", f:" + movie.getFetch());
        if (mode == BrowserActivity.WEB_VIEW_MODE_ON_PAGE_STARTED) {
            if (movie.getState() == Movie.COOKIE_STATE) {
                Log.d(TAG, "getScript:WEB_VIEW_MODE_ON_PAGE_STARTED COOKIE_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {\n" +
                        "    let thumbnails = document.querySelectorAll(\".thumbnail\");\n" +
                        "    let movieList = [];\n" +
                        "\n" +
                        "    thumbnails.forEach(thumbnail => {\n" +
                        "        let movie = {};\n" +
                        "\n" +
                        "        // Extract title\n" +
                        "        let titleElement = thumbnail.querySelector(\".caption\");\n" +
                        "        let title = titleElement?.textContent.trim() || null;\n" +
                        "\n" +
                        "        // Extract video URL\n" +
                        "        let videoUrlElement = thumbnail.querySelector(\".pm-video-thumb a:not(.pm-watch-later-add)\");\n" +
                        "        let videoUrl = videoUrlElement?.getAttribute(\"href\") || null;\n" +
                        "\n" +
                        "        if (!videoUrl) return;\n" +
                        "\n" +
                        "        if (!videoUrl.startsWith(\"http\")) {\n" +
                        "            videoUrl = \""+getConfig().getUrl() + "\"+ \"/\" + videoUrl;\n" +
                        "        }\n" +
                        "\n" +
                        "        console.log(\"Search result URL:\", videoUrl);\n" +
                        "\n" +
                        "        // Extract card image URL\n" +
                        "        let cardImageElement = thumbnail.querySelector(\".pm-video-thumb img\");\n" +
                        "        let cardImage = cardImageElement?.getAttribute(\"data-echo\") ||\n" +
                        "                        cardImageElement?.getAttribute(\"data-src\") ||\n" +
                        "                        cardImageElement?.getAttribute(\"data-original\") ||\n" +
                        "                        cardImageElement?.getAttribute(\"src\") || null;\n" +
                        "\n" +
                        "        console.log(\"Generated search result image:\", cardImage);\n" +
                        "\n" +
                        "        movie.title = title;\n" +
                        "        movie.description = title;\n" +
                        "        movie.cardImageUrl = cardImage;\n" +
                        "        movie.backgroundImageUrl = cardImage;\n" +
                        "        movie.bgImageUrl = cardImage;\n" +
                        "        movie.videoUrl = videoUrl;\n" +
                        "        movie.studio = \""+Movie.SERVER_LAROZA+"\";\n" +
                        "        movie.state = videoUrl.includes(\"-serie\") ? "+Movie.GROUP_STATE +" : "+ Movie.ITEM_STATE+";\n" +
                        "\n" +
                        "        movieList.push(movie);\n" +
                        "    });\n" +
                        "\n" +
                        "    // Extract next page information\n" +
                        "    let nextPageMovie = null;\n" +
                        "    let iElement = document.querySelector(\"i.fa.fa-arrow-left\");\n" +
                        "    \n" +
                        "    if (iElement) {\n" +
                        "        let aElement = iElement.closest(\"a\");\n" +
                        "        let nextPageUrl = aElement?.getAttribute(\"href\") || null;\n" +
                        "\n" +
                        "        if (nextPageUrl) {\n" +
                        "            nextPageMovie = {\n" +
                        "                title: \"التالي\",\n" +
                        "                description: \"0\",\n" +
                        "                studio: \""+Movie.SERVER_LAROZA+"\",\n" +
                        "                videoUrl: nextPageUrl,\n" +
                        "                cardImageUrl: \"https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png\",\n" +
                        "                backgroundImageUrl: \"https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png\",\n" +
                        "                state: "+Movie.NEXT_PAGE_STATE+",\n" +
                        "                 mainMovieTitle: nextPageUrl\n" +
                        "            };\n" +
                        "\n" +
                        "            console.log(\"Next page URL:\", nextPageUrl);\n" +
                        "        } else {\n" +
                        "            console.log(\"No next page found.\");\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "    if (movieList.length > 0) {\n" +
                        "        MyJavaScriptInterface.myMethod(JSON.stringify(movieList));\n" +
                        "    }" +
                        "});";
            }
////            if (movie.getState() == Movie.GROUP_OF_GROUP_STATE){
//                Log.d(TAG, "getScript:mycima REQUEST_CODE_FETCH_HTML");
//                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
////                        "var serverElems = document.getElementsByClassName('PostItemContent');\n" +
////                        "console.log(serverElems.length);" +
////                        "if(serverElems.length > 0){" +
//                        "var html = ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');" +
//                        "MyJavaScriptInterface.myMethod(html);" +
////                        "}" +
//                        "});";
////            }
//
            else if (movie.getState() == Movie.ITEM_STATE) {
                Log.d(TAG, "getScript:WEB_VIEW_MODE_ON_PAGE_STARTED COOKIE_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {\n" +
                        "let movieList = []; // Assuming 'movie' is already defined\n" +
                        "\n" +
                        "    let url = \"https://www.laroza.now/video.php?vid=4109d062a\";\n" +
                        "\n" +
                        "    // Replace \"video.php\" with \"play.php\" in URL if needed\n" +
                        "    if (url.includes(\"video.php\")) {\n" +
                        "        url = url.replace(\"video.php\", \"play.php\");\n" +
                        "    }\n" +
                        "\n" +
                        "    fetch(url)\n" +
                        "        .then(response => response.text())\n" +
                        "        .then(html => {\n" +
                        "            let parser = new DOMParser();\n" +
                        "            let doc = parser.parseFromString(html, \"text/html\");\n" +
                        "\n" +
                        "            // Extract description\n" +
                        "            let descriptionDiv = doc.querySelector(\".description\");\n" +
                        "            if (descriptionDiv) {\n" +
                        "                let pElements = descriptionDiv.querySelectorAll(\"p\");\n" +
                        "                for (let p of pElements) {\n" +
                        "                    let text = p.textContent.trim();\n" +
                        "                    if (text) {\n" +
                        "                        console.log(\"Extracted Text:\", text);\n" +
                        "                        //movie.description = text;\n" +
                        "                    }\n" +
                        "                }\n" +
                        "            } else {\n" +
                        "                console.log(\"Could not find the .description element.\");\n" +
                        "            }\n" +
                        "\n" +
                        "            // Extract servers\n" +
                        "            let servers = doc.querySelectorAll(\"ul.WatchList li\");\n" +
                        "            if (servers.length === 0) {\n" +
                        "                return;\n" +
                        "            }\n" +
                        "\n" +
                        "            let referer = extractDomain(url, true, true);\n" +
                        "\n" +
                        "            // Process servers\n" +
                        "            servers.forEach(server => {\n" +
                        "                let title = server.querySelector(\"strong\")?.textContent.trim() || \"Unknown Server\";\n" +
                        "                let dataIndex = server.getAttribute(\"data-embed-url\") || null;\n" +
                        "\n" +
                        "                if (dataIndex) {\n" +
                        "                    let resolution = {}; // Clone movie object\n" +
                        "                    resolution.state = "+Movie.RESOLUTION_STATE+";\n" +
                        "                    resolution.title = title;\n" +
                        "                    resolution.cardImageUrl = \""+movie.getCardImageUrl()+"\";\n" +
                        "                    resolution.backgroundImageUrl = \""+movie.getBackgroundImageUrl()+"\";\n" +
                        "                    resolution.studio = \""+Movie.SERVER_LAROZA+"\";\n" +
                        "                    resolution.videoUrl = `${dataIndex}|Referer=${referer}`;\n" +
                        "\n" +
                        "                    movieList.push(resolution);\n" +
                        "                }\n" +
                        "            });\n" +
                        "\n" +
                        "    if (movieList.length > 0) {\n" +
                        "        MyJavaScriptInterface.myMethod(JSON.stringify(movieList));\n" +
                        "    }" +
                        "        })\n" +
                        "        .catch(error => console.error(\"Error fetching movie details:\", error));\n" +
                        "\n" +
                        "function extractDomain(url, includeProtocol = true, addTrailingSlash = true) {\n" +
                        "    try {\n" +
                        "        let parsedUrl = new URL(url);\n" +
                        "        let domain = includeProtocol ? `${parsedUrl.protocol}//${parsedUrl.host}` : parsedUrl.host;\n" +
                        "        return addTrailingSlash ? domain + \"/\" : domain;\n" +
                        "    } catch (error) {\n" +
                        "        console.error(\"Invalid URL:\", url, error);\n" +
                        "        return null; // Handle invalid URLs gracefully\n" +
                        "    }\n" +
                        "}" +

                        "});";
            }
            if (movie.getState() == Movie.RESOLUTION_STATE) {
                String referer = Util.extractDomain(movie.getVideoUrl(), true, true);
                int hashIndex = movie.getVideoUrl().indexOf('#');

                // Check if the hash symbol is found in the string
                if (hashIndex != -1 && hashIndex < movie.getVideoUrl().length() - 1) {
                    // Return the substring after the hash symbol
                  String  serverId = movie.getVideoUrl().substring(hashIndex + 1);

                    // Find the index of the '|' symbol in the substring after the hash
                    int pipeIndex = serverId.indexOf('|');

                    // If '|' is found, return the substring up to the '|' symbol
                    if (pipeIndex != -1) {
                        serverId = serverId.substring(0, pipeIndex);
                    }

                    script =
//                            "document.addEventListener('DOMContentLoaded', () => {\n" +
                            "" +
                            "let elements = document.querySelectorAll('li[data-embed-id]');\n" +
                            "elements.forEach(function (element) {\n" +
                            "    if (element.getAttribute('data-embed-id') == " + serverId+ ") {\n" +
                            "        // Simulate a click on the matched element\n" +
                            "        element.click();\n" +
                            "console.log('Valid iframe found with src:', element.getAttribute('data-embed-url'));" +
                            "\n" +
//                            "        // Wait for the second element to appear after the click\n" +
                            "        waitForElement('iframe', function (secondElement) {\n" +
                            "    console.log('xxxx: Second element appeared:', secondElement);\n" +
                            "\n" +
                            "    // Get the iframe src attribute\n" +
                            "    let iframeSrc = secondElement.getAttribute('src');\n" +
                            "\n" +
                            "    // Check if src is valid (not null, not 'about:blank')\n" +
                            "    if (iframeSrc && iframeSrc.trim() !== '' && iframeSrc !== 'about:blank') {\n" +
                            "        console.log('Valid iframe found with src:', iframeSrc);\n" +
                            "\n" +
                            "        // Scroll to the iframe\n" +
                            "        secondElement.scrollIntoView({ behavior: 'smooth', block: 'center' });\n" +
                            "\n" +
                            "        // Optional: Click the iframe if needed\n" +
                            "        secondElement.click();\n" +
                            "    } else {\n" +
                            "        console.log('Skipping iframe with invalid src:', iframeSrc);\n" +
                            "    }\n" +
                            "});" +

                            "return;" +

                            "    }\n" +
                            "});" +

                            "function makeFullScreen(iframe) {" +
                            "if (iframe) {\n" +
                            "  // Clone the iframe element\n" +
                            "        var clonedIframe = iframe.cloneNode(true);\n" +
                            "        \n" +
                            "        // Clear the entire body content\n" +
//                            "        document.body.innerHTML = '';\n" +
                            "        iframe.innerHTML = '';\n" +
                            "\n" +
                            "        // Append the cloned iframe to the body\n" +
                            "        document.body.appendChild(clonedIframe);\n" +
                            " // Modify the cloned iframe to make it fullscreen\n" +
                            "        clonedIframe.style.position = \"fixed\";\n" +
                            "        clonedIframe.style.top = \"0\";\n" +
                            "        clonedIframe.style.left = \"0\";\n" +
                            "        clonedIframe.style.width = \"100%\";\n" +
                            "        clonedIframe.style.height = \"100%\";\n" +
                            "        clonedIframe.style.zIndex = \"9999\";\n" +
                            "        clonedIframe.style.border = \"none\"; // Remove any borders\n" +
                            "        clonedIframe.setAttribute(\"allowfullscreen\", \"true\");\n" +
                            "        clonedIframe.setAttribute(\"scrolling\", \"yes\");\n" +
                            "\n" +
                            "        // Optional: if you want to apply fullscreen mode programmatically (only works if triggered by a user gesture)\n" +
                            "        if (iframe.requestFullscreen) {\n" +
                            "            iframe.requestFullscreen();\n" +
                            "        } else if (iframe.webkitRequestFullscreen) { // Safari\n" +
                            "            iframe.webkitRequestFullscreen();\n" +
                            "        } else if (iframe.mozRequestFullScreen) { // Firefox\n" +
                            "            iframe.mozRequestFullScreen();\n" +
                            "        } else if (iframe.msRequestFullscreen) { // IE/Edge\n" +
                            "            iframe.msRequestFullscreen();\n" +
                            "        }\n" +
                            "    } else {\n" +
                            "        console.log(\"Iframe not found.\");\n" +
                            "    }" +
                            "\n}" +
                            "// Function to wait for an element to appear\n" +
                            "    function waitForElement(selector, callback) {\n" +
                            "        const observer = new MutationObserver(function(mutations) {\n" +
                            "            mutations.forEach(function(mutation) {\n" +
                            "                const element = document.querySelector(selector);\n" +
                            "                if (element) {\n" +
                            "                    callback(element);\n" +
                            "                    observer.disconnect(); // Stop observing after the element appears\n" +
                            "                }\n" +
                            "            });\n" +
                            "        });\n" +
                            "\n" +
                            "        // Observe changes in the DOM\n" +
                            "        observer.observe(document.body, {\n" +
                            "            childList: true,\n" +
                            "            subtree: true\n" +
                            "        });\n" +
                            "    }" +
                            "" +
                            ""
//                            " });"
                    ;

                    script = "document.addEventListener('DOMContentLoaded', () => {\n" +
                            "    setTimeout(() => {\n" +
                            "        let elements = document.querySelectorAll('li[data-embed-id]');\n" +
                            "        elements.forEach(function (element) {\n" +
                            "            if (element.getAttribute('data-embed-id') == serverId) {\n" +
                            "                console.log('Matching element found:', element.getAttribute('data-embed-url'));\n" +
                            "\n" +
                            "                // Trigger click\n" +
                            "                element.click();\n" +
                            "            }\n" +
                            "        });\n" +
                            "    }, 1000); // Wait for 1 second before executing\n" +
                            "});\n";
                }

            }


        }
        Log.d(TAG, "getWebScript: "+script);
        return script;
    }

    @Override
    public ArrayList<Movie> getHomepageMovies(boolean handleCookie, ActivityCallback<ArrayList<Movie>> activityCallback) {
//        return search(getSearchUrl("البطل"), activityCallback);
//        return search(getConfig().getUrl() + "/newvideos.php", activityCallback, handleCookie);
        return search(getConfig().getUrl() + "/category.php?cat=arabic-movies28", activityCallback, handleCookie);
//        return search(getConfig().getUrl() + "/moslslat1.php", activityCallback);
//        return search(getConfig().getUrl() + "/category/افلام-اجنبية/", activityCallback);
//        return search(getConfig().getUrl() + "/category/المسلسلات", activityCallback);
//        return search("sonic", activityCallback);
    }

    @Override
    public String getServerId() {
        return Movie.SERVER_LAROZA;
    }
}
