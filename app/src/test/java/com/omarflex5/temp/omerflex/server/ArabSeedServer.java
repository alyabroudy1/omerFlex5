package com.omarflex5.temp.omerflex.server;

import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.entity.MovieType;
import com.omerflex.entity.ServerConfig;
import com.omerflex.entity.dto.LinkHeadersDTO;
import com.omerflex.view.BrowserActivity;
import com.omerflex.view.VideoDetailsFragment;
import com.omerflex.view.mobile.entity.Category;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * from SearchActivity or MainActivity -> item -> resolutions
 * Or -> GroupOfItem -> item -> resolutions
 * -> if security check -> web browser intent
 * -> else to video intent
 * group + item -> resolution
 */
public class ArabSeedServer extends AbstractServer {

    static String TAG = "arabseed";
    public static String WEBSITE_URL = "https://arabseed.show";

    static boolean START_BROWSER_CODE = false;

    public ArabSeedServer() {
    }

    /**
     * produce movie from search result if isSeries than Group_State else Item_state
     *
     * @param query name to search for
     * @return
     */
    @Override
    public ArrayList<Movie> search(String query, ActivityCallback<ArrayList<Movie>> activityCallback, boolean handleCookie) {
        Log.i(TAG, "search: " + query);
        String searchContext = query;
//        String url = WEBSITE_URL + "/find/?find=" + query;
        ArrayList<Movie> movieList = new ArrayList<>();


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
//        if (true) {
//            setCookieRefreshed(false);

            //we need to update the url as domain might be updated in getSearchRequestDoc
//            Log.d(TAG, "search: new Url: "+ doc.baseUri() + ", location:"+doc.location());

            //**** default
            // String title = "ابحث في موقع فاصل ..";
//            String title = query;
            //int imageResourceId = R.drawable.default_image;
            // String cardImageUrl = "android.resource://" + activity.getPackageName() + "/" + imageResourceId;
            String cardImageUrl = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
            String backgroundImageUrl = "http://commondatastorage.googleapis.com/android-tv/Sample%20videos/April%20Fool's%202013/Introducing%20Google%20Nose/bg.jpg";
            Movie m = new Movie();
            m.setTitle(query);
            m.setDescription("نتائج البحث في الاسفل...");
            m.setStudio(Movie.SERVER_ARAB_SEED);
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
        Elements lis = doc.getElementsByClass("MovieBlock");
        if (lis.size() == 0) {
            lis = doc.getElementsByClass("postDiv");
            if (lis.isEmpty()){
                lis = doc.select("li.box__xs__2"); // select all relevant <li>
            }
            if (lis.isEmpty()){
                lis = doc.select("li"); // get all li first
                Elements filteredLis = new Elements();

                for (Element li : lis) {
                    String classAttr = li.className();
                    if (classAttr != null) {
                        String lower = classAttr.toLowerCase();
                        if (lower.contains("box") || lower.contains("item") || lower.contains("episode")) {
                            filteredLis.add(li);
                        }
                    }
                }
                lis = filteredLis;
            }
        }
        Log.i(TAG, "arab element found: " + lis.size());
        for (Element li : lis) {
            Element videoUrlElem = li.getElementsByAttribute("href").first();
            if (videoUrlElem != null) {
                String videoUrl = videoUrlElem.attr("href");

//                Log.d(TAG, "search: Url: "+ videoUrl);
                Element imageElem = li.getElementsByAttribute("data-src").first();
                String image = "";
                String title = "";
                if (imageElem == null) {
                    continue;
                }
                image = imageElem.attr("data-src");
                title = imageElem.attr("alt");
                if (title == null || title.contains("تحميل") || title.equals("")) {
                    continue;
                }

                    title = title.replace("مسلسل", "");

                String category = "";
                ArrayList<String> movieCategory = new ArrayList<>();
                String genre = "";
                Elements cateElems = videoUrlElem.getElementsByClass("category");
                if (cateElems.isEmpty()){
                    // Categories (post__category and __genre)
                    Element categoryElm = li.selectFirst(".post__category");
                    if (categoryElm == null){
                        // Find first element whose class contains the word "category"
                        for (Element el : li.getAllElements()) {
                            String classAttr = el.className(); // get class string
                            if (classAttr != null && classAttr.toLowerCase().contains("category")) {
                                categoryElm = el;
                                break; // stop at the first match
                            }
                        }
                    }


                    if (categoryElm != null) {
                        category = categoryElm.text();
                        movieCategory.add(category);
//                        Log.d(TAG, "search: category: "+ category);
                    }
                    Element genreElm = li.selectFirst(".__genre");
                    if (genreElm != null) {
                        genre = genreElm.text();
                        movieCategory.add(genre);
//                        Log.d(TAG, "search: genre: "+ genre);
                    }
                }else {
                    for (Element catElem : cateElems) {
//                        Log.d(TAG, "search: catElem: "+ catElem.text());
                        category = catElem.text();
                        break;
                    }
                }
                if (
                        category.contains("اغاني") ||
                                category.contains("كمبيوتر") ||
                                category.contains("موبايلات")
                ) {
                    continue;
                }
//                        String rate = "";
//                        Elements spans = li.getElementsByTag("span");
//                        for (Element span : spans) {
//                            if (!span.hasAttr("class")) {
//                                rate = span.text();
//                                break;
//                            }
//                        }
//                        if (rate.equals("")) {
//                            Element rateElem = li.getElementsByClass("pImdb").first();
//                            if (rateElem != null){
//                                rate = rateElem.text();
//                            }
//                        }

                // Rate
                Element rate = li.selectFirst(".post__ratings");

                Movie m = new Movie();
                if (rate != null) {
//                    Log.d(TAG, "search: rate: "+rate.text());
                    m.setRate(rate.text());
                }
                m.setTitle(title);
                m.setCategories(movieCategory);
                m.setDescription("");
                m.setStudio(Movie.SERVER_ARAB_SEED);
                m.setVideoUrl(videoUrl);
                m.setCardImageUrl(image);
                m.setBackgroundImageUrl(image);
                m.setBgImageUrl(image);
                m.setSearchContext(searchContext);
                m = updateMovieState(m);
                movieList.add(m);
//                       Log.d(TAG, "search: movie: " + m);
            }
            //hhhhhhhhhhhhhhhhh
//                    break;
        }

        //nextpage
        Movie nextPage = generateNextPage(doc);

        if (nextPage != null) {
            movieList.add(nextPage);
        }
        activityCallback.onSuccess(movieList, getLabel());
        return movieList;
    }

    @Override
    public Movie updateMovieState(Movie movie) {
        String u = movie.getVideoUrl();
        String n = movie.getTitle();
        boolean seriesCase = u.contains("/selary") ||u.contains("series") || u.contains("/movies")
                 || n.contains("فلام");

        if (seriesCase){
            movie.setState(Movie.GROUP_OF_GROUP_STATE);
            movie.setType(MovieType.SERIES);
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

    private Movie generateNextPage(Document doc) {

        Elements footerUls = doc.getElementsByClass("next page-numbers");
        Log.d(TAG, "search: footerUls:" + footerUls.size());
        if (footerUls.isEmpty()) {
            return null;
        }
        String nextPageLink = "";
        for (Element ul : footerUls) {
            nextPageLink = ul.attr("href");
            if (nextPageLink == null) {
                return null;
            }
            break;
        }
        nextPageLink = getConfig().getUrl() + nextPageLink;
        Movie nextPage = new Movie();
        nextPage.setTitle("التالي");
        nextPage.setDescription("0");
        nextPage.setStudio(Movie.SERVER_ARAB_SEED);
        nextPage.setVideoUrl(nextPageLink);
        nextPage.setCardImageUrl("https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png");
        nextPage.setBackgroundImageUrl("https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png");
        nextPage.setState(Movie.NEXT_PAGE_STATE);
        nextPage.setType(MovieType.NEXT_PAGE);
        nextPage.setRate("");
        Log.d(TAG, "search: nextPage:" + nextPageLink);
        return nextPage;
    }

    @Override
    public boolean shouldInterceptRequest(WebView view, WebResourceRequest request, Movie movie){
        return movie.getState() == Movie.RESOLUTION_STATE;
    }

    @Override
    public boolean shouldCleanWebPage(String pageUrl, Movie movie){
        int state = movie.getState();
//        String videoUrl = movie.getVideoUrl();
//
//        return state == Movie.RESOLUTION_STATE &&
//                !(videoUrl.contains("vidmoly")  ||
//         movie.getVideoUrl().contains("voe.sx") ||
//                movie.getVideoUrl().contains("brucevotewithin")
//                );
        return false;
    }

    @Override
    protected ArrayList<Movie> getSearchMovieList(Document doc) {
        return null;
    }

    @Override
    protected String getSearchUrl(String query) {
        if (query.contains("http")) {
            return query;
        }

        String searchPart = "/find/?word=";
        ServerConfig config = getConfig();
        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            return WEBSITE_URL + searchPart + query;
        }


        if (query.startsWith("/")) {
            return config.getUrl() + query;
        }
        return config.getUrl() + searchPart + query;
    }

    public MovieFetchProcess fetchBrowseItem(Movie movie, ActivityCallback<Movie> activityCallback) {
        Movie clonedMovie = Movie.clone(movie);
        clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
        // to do nothing and wait till result returned to activity only the first fetch
//        return startWebForResultActivity(clonedMovie);
        activityCallback.onInvalidCookie(clonedMovie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, clonedMovie);
    }

    protected MovieFetchProcess fetchSeriesAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        if (action == Movie.GROUP_OF_GROUP_STATE) {
            return fetchGroupOfGroup(movie, activityCallback);
        }
        return fetchGroup(movie, activityCallback);
    }

    protected MovieFetchProcess fetchItemAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        Log.d(TAG, "fetchItemAction: "+ action);
        switch (action) {
            case Movie.BROWSER_STATE:
                return fetchBrowseItem(movie, activityCallback);
            case Movie.COOKIE_STATE:
                return fetchCookie(movie);
            case Movie.ACTION_WATCH_LOCALLY:
                return fetchWatchLocally(movie, activityCallback);
            case Movie.RESOLUTION_STATE:
                return fetchResolutions(movie, activityCallback);
//            case Movie.RESOLUTION_STATE:
//                return fetchResolutions(movie);
            case Movie.VIDEO_STATE:
                activityCallback.onSuccess(movie, getLabel());
                return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
            default:
                return fetchItem(movie, activityCallback);
        }
    }

    private MovieFetchProcess fetchWatchLocally(Movie movie, ActivityCallback<Movie> activityCallback) {
        if (movie.getState() == Movie.BROWSER_STATE) {
//            Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
//            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, clonedMovie);
            activityCallback.onInvalidCookie(movie, getLabel());
        }
        if (movie.getState() == Movie.RESOLUTION_STATE) {
            return fetchResolutions(movie, activityCallback);
        }
        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_EXOPLAYER, movie);
    }


//    @Override
//    public Movie fetch(Movie movie) {
//        Log.i(TAG, "fetch: " + movie.getVideoUrl());
//        switch (movie.getState()) {
//            case Movie.GROUP_STATE:
//                Log.i(TAG, "onItemClick. GROUP_STATE" + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                return fetchGroup(movie);
//            case Movie.ITEM_STATE:
//                Log.i(TAG, "onItemClick. ITEM_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                return fetchItem(movie);
////                return startWebForResultActivity(movie);
//            case Movie.RESOLUTION_STATE:
//                Log.i(TAG, "onItemClick. RESOLUTION_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                return fetchResolutions(movie);
//            case Movie.BROWSER_STATE:
//                Log.i(TAG, "onItemClick. BROWSER_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                startBrowser(movie.getVideoUrl());
//                break;
//            case Movie.VIDEO_STATE:
//                Log.i(TAG, "onItemClick. VIDEO_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                startVideo(movie.getVideoUrl());
//                break;
//            default:
//                return fetchResolutions(movie);
//        }
//        return movie;
//    }

    @Override
    public int fetchNextAction(Movie movie) {
        switch (movie.getState()) {
            case Movie.GROUP_OF_GROUP_STATE:
            case Movie.ITEM_STATE:
                return VideoDetailsFragment.ACTION_OPEN_DETAILS_ACTIVITY;
        }
        return VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY;
    }

    private MovieFetchProcess fetchGroupOfGroup(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchGroupOfGroup: " + movie.getVideoUrl());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    private MovieFetchProcess fetchGroup(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchGroup: " + movie.getVideoUrl());
        final String url = movie.getVideoUrl();
       /* Thread t = new Thread(new Runnable() {
            @Override
            public void run() {

        */
        //     Movie series = dbHelper.findMovieByLink(movie.getVideoUrl());
        //   movie.setSubList(dbHelper.findSubListByMainMovieLink(movie.getStudio(), movie.getVideoUrl()));

        //  if (movie.getSubList().size() == 0) {
        //  if (series == null) {
        Log.d(TAG, "fetchGroup: source network");

        Log.i(TAG, "FetchSeriesLink url:" + url);
        //descriptionTextView = activity.findViewById(R.id.textViewDesc);

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


//            Document doc = Jsoup.connect(url)
//                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
//                    .userAgent("Mozilla/5.0 (Linux; Android 8.1.0; Android SDK built for x86 Build/OSM1.180201.031; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/69.0.3497.100 Mobile Safari/537.36")
//                    .followRedirects(true)
//                    .ignoreHttpErrors(true)
//                    .timeout(0)
//                    .ignoreContentType(true)
//                    .get();

        //  Document doc = Jsoup.connect(url).header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8").header("User-Agent", "Mozilla/5.0 (Linux; Android 8.1.0; Android SDK built for x86 Build/OSM1.180201.031; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/69.0.3497.100 Mobile Safari/537.36").timeout(0).get();
        //description

        Elements decDivs = doc.select("h2");
        String description = "";
        for (Element div : decDivs) {
            String desc = div.getElementsByTag("p").html();
            description = desc;
            Log.i("description", "found:" + description);

            if (null != description && !description.equals("")) {
                break;
            }
        }

        if (!description.equals("")) {
            Log.d(TAG, "fetchGroup: desooo" + description);
            movie.setDescription(description);
        }
        //hier             VideoDetailsFragment.MOVIE_DESCRIPTION = description;
        // VideoDetailsFragment.updateDescription(description);
        //      movie.setDescription(description);
                 /*   activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            descriptionPresenter.updateDescription(desc+" ");
                        }
                    });

                  */


        //backgroundImage and trailer
        Elements imageDivs = doc.getElementsByClass("row py-4");

        String bgImage = "";
        String ytLink = "";
        for (Element imageDiv : imageDivs) {
            Elements imageLinks = imageDiv.getElementsByAttribute("href");
            for (Element imagelink : imageLinks) {
                Log.d(TAG, "run: bgimage divs : " + imagelink.attr("href"));
                if (imagelink.attr("href").contains("/uploads/")) {
                    bgImage = imagelink.attr("href");
                    Log.d(TAG, "run: bgimage found : " + bgImage);
                    //break;
                }
                if (imagelink.attr("href").contains("youtube")) {
                    ytLink = imagelink.attr("href");
                    Log.d(TAG, "run: youtube found : " + ytLink);
                    break;
                }
            }
            if (!bgImage.equals("")) {
                break;
            }
        }
        //hier   VideoDetailsFragment.MOVIE_BACKGROUND_IMAGE = bgImage;
        //hier VideoDetailsFragment.MOVIE_TRAILER_URL = ytLink;
        movie.setBackgroundImageUrl(bgImage);
        movie.setTrailerUrl(ytLink);
        if (movie.getSubList() == null) {
            movie.setSubList(new ArrayList<>());
        }

        Elements links = doc.select("a");
        for (Element link : links) {
            // TODO: find better way to get the link
            if (
                    link.attr("href").contains("/episode") &&
                            link.getElementsByAttribute("src").hasAttr("alt")
            ) {
                Movie episode = Movie.clone(movie);
                episode.setParentId(movie.getId());
                Log.i(TAG, "linkFound:" + link.attr("href") + "");


                String title = link.getElementsByAttribute("src").attr("alt");
                String cardImageUrl = link.getElementsByAttribute("src").attr("src");

                String videoUrl = link.attr("href");

                episode.setTitle(title);
                episode.setDescription(description);
                episode.setVideoUrl(videoUrl);
                episode.setCardImageUrl(cardImageUrl);
                episode.setState(Movie.ITEM_STATE);
                episode.setType(MovieType.EPISODE);

                movie.addSubList(episode);
                //    episode.save(dbHelper);
                // movie.addSeries(m);
                       /* Episode finalEpisode = episode;
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                listRowAdapter.add(finalEpisode);
                            }
                        });
                        */
            }
        }
        activityCallback.onSuccess(movie, getLabel());
    /*        movie.save(dbHelper);
        } else {
            Log.d(TAG, "fetchGroup: source db");
        }

     */
            /*}
        });

        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

             */

        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    private MovieFetchProcess fetchItem(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchItem: " + movie.getVideoUrl());
        Movie m = null;
        Log.d(TAG, "fetchItem: source Network");

        String url = movie.getVideoUrl();
        Document doc = getRequestDoc(url, OmerFlexApplication.getAppContext());
//            Document doc = Jsoup.connect(url)
//                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
//                    .userAgent("Mozilla/5.0 (Linux; Android 8.1.0; Android SDK built for x86 Build/OSM1.180201.031; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/69.0.3497.100 Mobile Safari/537.36")
//                    .followRedirects(true)
//                    .ignoreHttpErrors(true)
//                    .timeout(0)
//                    .ignoreContentType(true)
//                    .get();

        if (doc == null) {
            activityCallback.onInvalidLink(movie);
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, movie);
        }

        if (doc.title().contains("Just a moment")) {
//            Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//            return startWebForResultActivity(clonedMovie);
            activityCallback.onInvalidCookie(movie, getLabel());
        }

        Elements storyElems = doc.getElementsByClass("StoryLine");
        String dec = "";
        for (Element storyElem : storyElems) {
            Elements pElems = storyElem.getElementsByClass("descrip");
            for (Element pElem : pElems) {
                if (pElem.hasAttr("style")) {
                    continue;
                }
                dec = pElem.text();
                break;
            }
            break;
        }

        if (dec.isEmpty()) {
//            Element storyDiv = doc.selectFirst("div.post__story");
            Element storyDiv  = doc.select("[class*=post][class*=story]").first();
            if (storyDiv != null){
                dec = storyDiv.text(); // Gets the full text content, concatenated
            }
        }

        Elements watchElems = doc.getElementsByClass("WatchButtons");
        if (watchElems.isEmpty()){
            watchElems = doc.getElementsByClass(".btton watch__btn");

            if (watchElems.isEmpty()){
                Elements lis = doc.select("a"); // get all li first
                for (Element li : lis) {
                    String classAttr = li.className();
                    if (classAttr != null) {
                        String lower = classAttr.toLowerCase();
                        if (lower.contains("tton") && lower.contains("watch")) {
                            watchElems.add(li);
                        }
                    }
                }
            }
        }
        String link = "";
        Log.d(TAG, "fetchItem: watchElems:" + watchElems.size());
        for (Element watchElem : watchElems) {
            Elements linkElems = watchElem.getElementsByTag("a");
            Log.d(TAG, "fetchItem: watchElems-link: " + linkElems.size());
            String domain = Util.extractDomain(movie.getVideoUrl(), true, true);
            Log.d(TAG, "fetchItem: domain: "+ domain);
            movie.setDescription(dec);
            if (!linkElems.isEmpty()) {
                for (Element linkElem : linkElems) {
                    link = linkElem.attr("href");
                    if (link != null) {
                        Movie resolution = Movie.clone(movie);
                        resolution.setParentId(movie.getId());
                        resolution.setVideoUrl(link);
                        Log.d(TAG, "fetchItem: link:" + link);
                        resolution.setState(Movie.BROWSER_STATE);
                        resolution.setType(MovieType.BROWSER);

                        return fetchServers(null, resolution, domain, activityCallback);
                    }
                    break;
                }
                break;
            }
            //new style
            try {
            // Find the form dynamically (by checking if it contains a hidden input and a button)
            Element targetForm = null;
            Elements forms = watchElem.select("form");
            for (Element form : forms) {
                if (form.selectFirst("input[type='hidden']") != null && form.selectFirst("button") != null) {
                    targetForm = form;
                    break;
                }
            }

            if (targetForm == null) {
                System.out.println("error: Watch form not found.");
                break;
            }

// Extract the POST URL from the form's action attribute
                String postUrl = targetForm.attr("action");
                if (postUrl.isEmpty()) {
                    System.out.println("Post URL not found in form's action attribute.");
                    break;
                }

                Log.d(TAG, "fetchItem: postUrl:"+ postUrl);


                // Find the hidden input
            Element hiddenInput = targetForm.selectFirst("input[type='hidden']");
            if (hiddenInput == null) {
                System.out.println("Error Hidden input not found.");
                break;
            }

            // Extract the hidden input name dynamically
            String hiddenInputName = hiddenInput.attr("name");
            if (hiddenInputName.isEmpty()) {
                System.out.println("Error Hidden input name not found.");
                break;
            }
                Log.d(TAG, "fetchItem: hiddenInputName:"+ hiddenInputName);

            // Extract the hidden input value
            String hiddenInputValue = hiddenInput.val();

                Log.d(TAG, "fetchItem: hiddenInputValue:"+ hiddenInputValue);

            // Prepare the POST data
            Map<String, String> postData = new HashMap<>();
            postData.put(hiddenInputName, hiddenInputValue);

            // Make the POST request
            Connection connection = Jsoup.connect(postUrl)
                    .method(Connection.Method.POST)
                    .data(postData)
                    .followRedirects(true);

                // Add headers
                connection.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
                connection.header("Content-Type", "application/x-www-form-urlencoded");
                connection.header("Accept-Language", "en-US,en;q=0.9");
                connection.header("Referer", domain);

                Connection.Response response = connection.execute();

                if (response.statusCode() == 200) {

                    // Print the response status code and body
                    System.out.println("Status Code: " + response.statusCode());
//                    Log.d(TAG, "Response Body: " + response.body());

                    return fetchServers(response.parse(), movie, domain, activityCallback);
                }

        } catch (IOException e) {
                System.out.println("Error fetching watch button: " + e.getMessage());
        }


        }
        activityCallback.onInvalidLink(movie);
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, movie);
    }

    private MovieFetchProcess fetchServers(Document doc, Movie movie, String referer, ActivityCallback<Movie> activityCallback) {
//        Log.d(TAG, "fetchServers run-1: " + movie.getVideoUrl());
//        Log.d(TAG, "fetchServers run-1 referer: " + referer);

//        Document doc = getRequestDoc(movie.getVideoUrl());
        try {
                String host = Util.extractDomain(movie.getVideoUrl(), true, true);
                String newCookie = CookieManager.getInstance().getCookie(host);
            if (doc == null){
                doc = Jsoup.connect(movie.getVideoUrl())
//                        .userAgent("Android 8")
                        .userAgent(getCustomUserAgent(movie.getState()))
                        .headers(getConfig().getHeaders())
                        .cookies(getConfig().getMappedCookies())
                        .header("referer", referer)
                        .header("Cookie", newCookie)
                        .followRedirects(false) // in this case dont follow redirect
                        .ignoreHttpErrors(true)
                        .timeout(0)
                        .ignoreContentType(true)
                        .get();
//                Log.d(TAG, "fetchServers: headers "+ getConfig().getHeaders().toString());
//                doc = getRequestDoc(movie.getVideoUrl());
                Log.d(TAG, "fetchServers: new request-1 title:"+ doc.title());
                if (doc.title().contains("Just a moment")) {
//                if (true) {
                    Movie clonedMovie = Movie.clone(movie);
//            clonedMovie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
//            return startWebForResultActivity(clonedMovie);
                    clonedMovie.setVideoUrl(movie.getVideoUrl() + "|Referer="+referer);
                    Log.d(TAG, "fetchServers: new request title:"+ doc.title());
                    activityCallback.onInvalidCookie(clonedMovie, getLabel());

                    return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, clonedMovie);
                }

            }

            Log.d(TAG, "fetchServers: title:"+ doc.title());
            Elements serverElems = doc.getElementsByClass("containerServers");

            if (serverElems.isEmpty()){
                // First: select all li with data-post attribute
                serverElems = doc.select("li[data-post]");

// Fallback: if nothing found, search for "سيرفر" in child text
                if (serverElems.isEmpty()) {
                    Elements allLis = doc.select("li");
                    for (Element li : allLis) {
                        if (li.text().contains("سيرفر")) { // Arabic word for "server"
                            serverElems.add(li);
                        }
                    }
                }

            }

            if (movie.getSubList() == null) {
                movie.setSubList(new ArrayList<>());
            }


            Log.d(TAG, "fetchServers:serverElems: "+serverElems.size());
            if (serverElems.isEmpty()) {
                Movie clonedMovie = Movie.clone(movie);
                clonedMovie.setFetch(Movie.REQUEST_CODE_EXTEND_MOVIE_SUB_LIST);
//                String userAgent = "Mozilla/5.0 (Linux; Android 9; SM-G960F Build/PPR1.180610.011) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Mobile Safari/537.36";
                String userAgent = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Mobile Safari/537.36";
                String newUrl = movie.getVideoUrl() + "|referer=" + referer + "&User-Agent=" + userAgent;
                Log.d(TAG, "fetchServers: newUrl:"+ newUrl);
                clonedMovie.setVideoUrl(newUrl);
//                startWebForResultActivity(clonedMovie);
                activityCallback.onInvalidCookie(clonedMovie, getLabel());
                return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, movie);//very important to return the original movie
            }
            Log.d(TAG, "fetchServers: serverElems:" + serverElems.size());
            String domain = Util.extractDomain(movie.getVideoUrl(), true, true);
            for (Element serverElem : serverElems) {
                Elements listElems = serverElem.getElementsByAttribute("data-link");
                if (listElems.isEmpty()){
                    listElems = serverElems;
                }
                int counter = 0;
                for (Element listElem : listElems) {
                    Movie server = Movie.clone(movie);
                    server.setParentId(movie.getId());
                    server.setState(Movie.RESOLUTION_STATE);
                    server.setType(MovieType.RESOLUTION);
//                    String link = movie.getVideoUrl() + "??"+(counter++)+"||referer="+ referer;
                    String link = listElem.attr("data-link");
                    Log.d(TAG, "fetchServers: data-link:"+link);
                    if (link.isEmpty()) {
//                            link = movie.getVideoUrl() + "||Referer=" + domain;
                        link = "#"+listElem.attr("data-server");
                        if (listElem.attr("data-server").isEmpty()) {
                            link = "#"+counter++;
                        }
                    }
//                        if (link.contains(".wiki")){
//                            continue;
//                        }
                    //              "if (title.includes(\"عرب سيد\")) {\n" +
                    //                        "        post.videoUrl = \""+movie.getVideoUrl()+"\"; \n" +
                    //                        "    }\n" +

                    link = link + "|Referer=" + referer;


//                        "&Accept-Encoding=\"gzip, deflate, br\""+
//                        "&Accept=\"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7\""+
//                        "&Accept-Language=\"de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7\"";
//                        link = link + "||referer=https://arabseed.show/";
                    if (!link.startsWith("http")){
//                        link = movie.getVideoUrl() + link;
                        link = movie.getVideoUrl() + link;
                    }
                    server.setVideoUrl(link);
                    Log.d(TAG, "fetchServers:link:  "+link);
                    Elements titleElems = listElem.getElementsByTag("span");
                    if (!titleElems.isEmpty()) {
                        if (titleElems.first() != null) {
                            String titleText = Objects.requireNonNull(titleElems.first()).text();
                            if (titleText.contains("عرب سيد")) {
                                server.setVideoUrl(movie.getVideoUrl()+ "|Referer=" + referer+"&User-Agent="+getCustomUserAgent(movie.getState()));
                            }

                            server.setTitle(titleText);
                        }
                    } else {
                        server.setTitle(listElem.text());
                    }
                    Log.d(TAG, "fetchServers:title:  "+titleElems);
                    Log.d(TAG, "fetchServers: servers: " + server);

                    movie.addSubList(server);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    /**
     * fetch movie resolutions and start an external video intent
     *
     * @param movie Movie object to fetch its url
     * @return
     */
    private MovieFetchProcess fetchResolutions(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchResolutions: " + movie.getVideoUrl());
        Movie clonedMovie = Movie.clone(movie);
        clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);

        activityCallback.onInvalidCookie(clonedMovie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, clonedMovie);
    }

    private MovieFetchProcess fetchCookie(Movie movie) {
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, movie);
    }

    /**
     * exactly same as fetchResolutions() fetch movie resolutions and start Exoplayer video intent
     *
     * @param movie
     * @return
     */
//    @Override
//    public Movie fetchToWatchLocally(final Movie movie) {
//        Log.i(TAG, "fetchToWatchLocally: " + movie.getVideoUrl());
//        if (movie.getState() == Movie.VIDEO_STATE) {
//            return movie;
//        }
//        Movie clonedMovie = Movie.clone(movie);
//        clonedMovie.setFetch(Movie.REQUEST_CODE_EXOPLAYER);
//
//        startWebForResultActivity(clonedMovie);
//        return null;
//    }

//    @Override
//    public void startVideo(final String link) {
//        Log.i(TAG, "startVideo: " + link);
//        ArabSeedServer.START_BROWSER_CODE = true;
//
//        WebView simpleWebView = activity.findViewById(R.id.webView);
//        simpleWebView.clearCache(true);
//        simpleWebView.clearFormData();
//        simpleWebView.clearHistory();
//
//
//        WebSettings webSettings = simpleWebView.getSettings();
//
//        webSettings.setJavaScriptEnabled(true);
//        webSettings.setAllowFileAccess(true);
//        //webSettings.setAppCacheEnabled(true);
//
//        simpleWebView.loadUrl(link);
//    }

//    @Override
//    public void startBrowser(String url) {
//        Log.i(TAG, "startBrowser: " + url);
//        if (url.contains("yout")) {
//            url = fixTrailerUrl(url);
//        }
//        WebView webView = activity.findViewById(R.id.webView);
//
//        webView.loadData("<html><body><iframe width=\"100%\" height=\"100%\" src=\"" + url + "\" frameborder=\"0\" allowfullscreen></iframe></body></html>", "text/html", "utf-8");
//    }

//    public String fixTrailerUrl(String url) {
//        Log.i(TAG, "browseTrailer: " + url);
//        String newUrl = url;
//        if (url.contains("v=")) {
//            newUrl = "https://www.youtube.com/embed/" +
//                    url.substring(url.indexOf("v=") + 2)
//                    + "?autoplay=1&fs=1";
//            Log.d(TAG, "browseTrailer: newUrl=" + newUrl);
//        }
////        Movie movie = new Movie();
////        movie.setVideoUrl(newUrl);
////        movie.setStudio(Movie.SERVER_AKWAM);
////        movie.setState(Movie.BROWSER_STATE);
////
////        Intent intent = new Intent(activity, BrowserActivity.class); //start a browser to fetch item
////        intent.putExtra(DetailsActivity.MOVIE, (Serializable) movie);
////        activity.startActivity(intent);
//
//        //      WebView simpleWebView = activity.findViewById(R.id.webView);
//
////        simpleWebView.clearCache(true);
////        simpleWebView.clearFormData();
////        simpleWebView.clearHistory();
////
////
////        simpleWebView.setWebViewClient(new Browser_Home() {
////            // !url.contains("youtube") || !url.contains(WEBSITE_NAME);
////
////            @Override
////            public void onPageFinished(WebView view, String url) {
////                super.onPageFinished(view, url);
////                Log.d("WEBCLIENT", "onPageFinished");
////            }
////
////            @Override
////            public void onLoadResource(final WebView view, String url) {
////                Log.d("WEBCLIENT", "onLoadResource :url" + url);
////                super.onLoadResource(view, url);
////            }
////        });
////        simpleWebView.setWebChromeClient(new ChromeClient());
////        WebSettings webSettings = simpleWebView.getSettings();
////
////        webSettings.setJavaScriptEnabled(true);
////        webSettings.setAllowFileAccess(true);
////        webSettings.setAppCacheEnabled(true);
////        webSettings.setDomStorageEnabled(true);
////        webSettings.setLoadsImagesAutomatically(true);
////        webSettings.setBlockNetworkImage(false);
////
////        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
////        webSettings.setPluginState(WebSettings.PluginState.ON);
////        webSettings.setMediaPlaybackRequiresUserGesture(false);
////
////
////        simpleWebView.loadUrl(newUrl);
//        return newUrl;
//    }
    private boolean isSeries(Movie movie) {
        String u = movie.getVideoUrl();
        return u.contains("/series") || u.contains("/movies");
    }

//    public String getStudioText(String serverName) {
//
//        switch (serverName) {
//            case Movie.SERVER_SHAHID4U:
//                return "https://shahid4u";
//            case Movie.SERVER_FASELHD:
//                return "www.faselhd";
//            case Movie.SERVER_CIMA4U:
//                return "cima4u.io/";
//            case Movie.SERVER_AKWAM:
//                return "akwam.";
//
//        }
//
//        return "akwam.";
//    }
//
//    @Override
//    public boolean onLoadResource(Activity activity, WebView view, String url, Movie movie) {
//        return false;
//    }

//    private Movie startWebForResultActivity(Movie movie) {
//        Intent browse = new Intent(activity, BrowserActivity.class);
//        browse.putExtra(DetailsActivity.MOVIE, (Serializable) movie);
//        browse.putExtra(DetailsActivity.MAIN_MOVIE, (Serializable) movie.getMainMovie());
//        //   Log.d(TAG, "getResultFromWeb: activity:" + fragment.getClass().getName());
//        //activity.startActivity(browse);
//        fragment.startActivityForResult(browse, movie.getFetch());
//
//        return movie;
//    }

    @Override
    public String getWebScript(int mode, Movie movie) {
        String script = null;
        String referer = Util.extractDomain(movie.getVideoUrl(), true, true);
        if (mode == BrowserActivity.WEB_VIEW_MODE_ON_PAGE_STARTED) {

            if (movie.getState() == Movie.BROWSER_STATE) {
                Log.d(TAG, "getScript:cimaClub BROWSER_STATE");

                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        "var serverElems = document.getElementsByClassName('containerServers');\n" +
                        "var postList = [];\n" +
                        "console.log(serverElems.length);" +
                        "if(serverElems.length > 0){\n" +
                        " var serverElem = serverElems[0];\n" +
                        "var listElems = serverElem.querySelectorAll('[data-link]');\n" +
                        "\n" +
                        "if(listElems.length > 0){\n" +
                        "for (let b = 0; b < listElems.length; b++) {\n" +
                        "    var listElem = listElems[b];\n" +
                        "        // Clone 'movie' object\n" +
                        "        var post = {};\n" +
                        "        post.state = "+Movie.RESOLUTION_STATE+";\n" +
                        "        post.studio = \"" + movie.getStudio() + "\";\n" +
                        "        post.fetch = \"" + movie.getFetch() + "\";\n" +
                        "        post.cardImageUrl = \"" + movie.getCardImageUrl() + "\";\n" +
                        "        post.backgroundImageUrl = \"" + movie.getBackgroundImageUrl() + "\";\n" +
                        "        post.getMainMovieTitle = \"" + movie.getMainMovieTitle() + "\";\n" +
                        "        var link = listElem.getAttribute('data-link');\n" +
                        "\n" +
                        "        // Modify 'link' if it exists\n" +
                        "        if (link) {\n" +
                        "            post.videoUrl = link;\n" +
                        "        }\n" +
                        "\n" +
                        "var titleElems = listElem.getElementsByTagName(\"span\");\n" +
                        "                    if (titleElems.length > 0){\n" +
                        "                 \n" +
                        "                            var title = titleElems[0].textContent;\n" +
                        "if (title.includes(\"عرب سيد\")) {\n" +
                        "        continue;" +
                        "    }\n" +
                        "                            post.title = title;\n" +
                        "                        \n" +
                        "                    }else {\n" +
                        "                        post.title = listElem.textContent;\n" +
                        "                    }\n" +
                        "\n" +
                        "\n" +
                        "        // Log the server object\n" +
                        "        console.log(\"fetchServers: servers: \", post.videoUrl);\n" +
                        "\t\t\t\tpostList.push(post);\n" +
                        "    }\n" +
                        "\n" +
//                        "postList;\n" +
                        "MyJavaScriptInterface.myMethod(JSON.stringify(postList));\n" +
                        "}\n" +
                        "}" +
                        "});";


//                String serverId = "#";
//                String clickServer = "";
//                if (movie.getVideoUrl().contains("??")) {
//                    // newUrl = movie.getVideoUrl().substring(0, movie.getVideoUrl().indexOf("?"));
//                    int idIndex = movie.getVideoUrl().indexOf("??") + 2;
//                    serverId = movie.getVideoUrl().substring(idIndex, idIndex + 1);
//                    clickServer = "var intendedValue = '" + serverId + "';\n" +
//                            " var serversWatchSideElement = document.querySelector('.containerServers');\n" +
//                            "var elementList = serversWatchSideElement.querySelectorAll('[data-link]');" +
//                            "elementList['"+serverId+"'].click();";
//                    Log.d(TAG, "getScript: serverID:" + serverId);
//                }
//
            }
            else if (movie.getState() == Movie.RESOLUTION_STATE) {
                Log.d(TAG, "getScript: SERVER_ARAB_SEED RESOLUTION_STATE");


                String id = Util.getIdFromUrl(movie.getVideoUrl());
                if (id != null) {
//                    serverId = movie.getVideoUrl().substring(idIndex, idIndex + 1);
//                    clickServer = "var intendedValue = '" + serverId + "';\n" +
//                            " var serversWatchSideElement = document.querySelector('.containerServers');\n" +
//                            "var elementList = serversWatchSideElement.querySelectorAll('[data-link]');" +
//                            "elementList['"+serverId+"'].click();";
                    String clickServer =
                            "let element = document.querySelector(`li[data-server=\""+id+"\"]`);\n" +
                            "\n" +
                            "if (element) {\n" +
                            "  element.click();\n" +
                            "} else {\n" +
                            "  console.log(\"No element found with data-server=\" + "+id+");\n" +
                            "}"
                        ;

                    script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        clickServer +
                        "var iframe = document.querySelector('iframe[src]:not([src=\"about:blank\"])');" +
                        "  // Check if the iframe element was found" +
                        "  if (iframe) {" +
                        "    iframe.scrollIntoView({behavior: 'smooth'});" +
                        "  } " +
                        "// Find the first iframe with src attribute not equal to \"about:blank\"\n" +
                        "var originalIframe = document.querySelector('iframe[src]:not([src=\"about:blank\"])');\n" +
//                        "\n" +
//                        "if (originalIframe) {\n" +
//                        "  // Clone the iframe\n" +
//                        "  var clonedIframe = originalIframe.cloneNode(true);\n" +
//                        "\n" +
//                        "  // Remove all elements from the body\n" +
//                        "  document.body.innerHTML = '';\n" +
//                        "\n" +
//                        "  // Apply CSS to make the cloned iframe take up the entire screen\n" +
//                        "  clonedIframe.style.position = 'fixed';\n" +
//                        "  clonedIframe.style.top = '0';\n" +
//                        "  clonedIframe.style.left = '0';\n" +
//                        "  clonedIframe.style.width = '100%';\n" +
//                        "  clonedIframe.style.height = '100%';\n" +
//                        "\n" +
//                        "  // Add the cloned iframe back to the page\n" +
//                        "  document.body.appendChild(clonedIframe);\n" +
//                        "\n" +
                        "  // Request fullscreen for the cloned iframe\n" +
                        "  if (originalIframe.requestFullscreen) {\n" +
                        "    originalIframe.requestFullscreen();\n" +
                        "  } else if (originalIframe.mozRequestFullScreen) {\n" +
                        "    originalIframe.mozRequestFullScreen();\n" +
                        "  } else if (originalIframe.webkitRequestFullscreen) {\n" +
                        "    originalIframe.webkitRequestFullscreen();\n" +
                        "  } else if (originalIframe.msRequestFullscreen) {\n" +
                        "    originalIframe.msRequestFullscreen();\n" +
                        "  }\n" +
//                        "\n" +
//                        "  // Disable the ability to create new elements\n" +
//                        "  document.addEventListener('DOMNodeInserted', function (e) {\n" +
//                        "    e.preventDefault();\n" +
//                        "    e.stopPropagation();\n" +
//                        "    return false;\n" +
//                        "  });\n" +
                        "} else {\n" +
                        "  // Handle the case when no iframe with src other than \"about:blank\" is found\n" +
                        "  console.error('No eligible iframe found.');\n" +
                        "}\n" +
                        "});";

                    script =
                            "document.addEventListener('DOMContentLoaded', () => {" +
                                    "  let element = document.querySelector('li[data-server=\"" + id + "\"]');" +
                                    "  if (element) {" +
                                    "    element.click();" +
                                    "  } else {" +
                                    "    console.warn('No element found with data-server=" + id + "');" +
                                    "  }" +

                                    "  let iframe = document.querySelector('iframe[src]:not([src=\"about:blank\"])');" +
                                    "  if (iframe) {" +
                                    "    iframe.scrollIntoView({behavior: 'smooth'});" +
                                    "    if (iframe.requestFullscreen) {" +
                                    "      iframe.requestFullscreen();" +
                                    "    } else if (iframe.mozRequestFullScreen) {" +
                                    "      iframe.mozRequestFullScreen();" +
                                    "    } else if (iframe.webkitRequestFullscreen) {" +
                                    "      iframe.webkitRequestFullscreen();" +
                                    "    } else if (iframe.msRequestFullscreen) {" +
                                    "      iframe.msRequestFullscreen();" +
                                    "    }" +
                                    "  } else {" +
                                    "    console.error('No eligible iframe found.');" +
                                    "  }" +
                                    "});";


                }
                Log.d(TAG, "getWebScript: script :" + script);
            }
            else if (movie.getState() == Movie.COOKIE_STATE) {
                Log.d(TAG, "getScript: SERVER_ARAB_SEED COOKIE_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        "var postDivs = document.getElementsByClassName(\"MovieBlock\");\n" +
                        "if (postDivs.length > 0){\n" +
                        "var postList = [];\n" +
                        "for (var i = 0; i < postDivs.length; i++) {\n" +
                        "var post = {};\n" +
                        "var postDiv = postDivs[i];\n" +
                        "var category = postDiv.getElementsByClassName(\"category\")[0].textContent;" +
                        "if (category.match(/تطبيق|برامج|برنامج|موبايل|اغاني|العاب|لعبة|لعبه|صور/) != null){continue;}" +
                        "var poster = postDiv.getElementsByClassName(\"Poster\")[0];\n" +
                        "var imageDiv = poster.querySelector(\"[data-src]\");\n" +
                        "if (imageDiv == null) {\n" +
                        "                    continue;\n" +
                        "                }" +
                        "let title = imageDiv.getAttribute(\"alt\");\n" +
                        "post.title = title.replace(\"مسلسل\", \"\");" +
                        "post.videoUrl = postDiv.getElementsByTagName(\"a\")[0].getAttribute('href');\n" +
                        "rateDiv = postDiv.getElementsByClassName(\"RateNumber\");\n" +
                        "if(rateDiv.length > 0) {\n" +
                        "post.rate = rateDiv[0].textContent;\n" +
                        "}\n" +
                        "post.cardImageUrl = imageDiv.getAttribute('data-src');" +
                        "if (post.cardImageUrl == null ){ post.cardImageUrl = imageDiv.getAttribute('src'); }" +
                        "post.bgImageUrl = post.cardImageUrl;\n" +
                        "post.studio = \"" + Movie.SERVER_ARAB_SEED + "\";\n" +
                        "post.state = 2;\n" +
                        "postList.push(post);\n" +
                        "}" +
                        "    if (postList.length > 0) {\n" +
                        "        MyJavaScriptInterface.myMethod(JSON.stringify(postList));\n" +
                        "    }" +
                        "}" +
                        "});";

            }
            else if (movie.getState() == Movie.ITEM_STATE) {
                Log.d(TAG, "getScript: SERVER_ARAB_SEED ITEM_STATE");
                    script ="document.addEventListener(\"DOMContentLoaded\", () => {\n" +
                            "    fetchServers(\""+referer+"\");\n" +
                            "    fetchResolutionLink(true);\n" +
                            "});\n" +
                            "\n" +
//                            "window.onload = function () {\n" +

//                            "};\n" +
                            "\n" +
                            "function fetchResolutionLink(click) {\n" +
                            "    const watchElems = document.querySelectorAll(\".WatchButtons\");\n" +
                            "    console.log(\"fetchItem: watchElems:\", watchElems.length);\n" +
                            "\n" +
                            "    for (const watchElem of watchElems) {\n" +
                            "        const linkElems = watchElem.querySelectorAll(\"a\");\n" +
                            "        console.log(\"fetchItem: watchElems-link:\", linkElems.length);\n" +
                            "\n" +
                            "        if (linkElems.length > 0) {\n" +
                            "            for (const linkElem of linkElems) {\n" +
                            "                const link = linkElem.getAttribute(\"href\");\n" +
                            "                if (link) {\n" +
                            "                    console.log(\"fetchItem: old-style link:\", link);\n" +
                            "                    if (click) {\n" +
                            "                        linkElem.click();\n" +
                            "                    }\n" +
                            "                    return link;\n" +
                            "                }\n" +
                            "            }\n" +
                            "        }\n" +
                            "\n" +
                            "        // New style form handling\n" +
                            "        try {\n" +
                            "            const forms = watchElem.querySelectorAll(\"form\");\n" +
                            "            let targetForm = null;\n" +
                            "\n" +
                            "            for (const form of forms) {\n" +
                            "                if (form.querySelector(\"input[type='hidden']\") && form.querySelector(\"button\")) {\n" +
                            "                    targetForm = form;\n" +
                            "                    break;\n" +
                            "                }\n" +
                            "            }\n" +
                            "\n" +
                            "            if (!targetForm) {\n" +
                            "                console.error(\"Error: Watch form not found.\");\n" +
                            "                return null;\n" +
                            "            }\n" +
                            "\n" +
                            "            const postUrl = targetForm.getAttribute(\"action\") || \"\";\n" +
                            "            if (!postUrl) {\n" +
                            "                console.error(\"Error: Post URL not found in form's action attribute.\");\n" +
                            "                return null;\n" +
                            "            }\n" +
                            "\n" +
                            "            if (click) {\n" +
                            "                targetForm.submit();\n" +
                            "            }\n" +
                            "\n" +
                            "            console.log(\"fetchItem: postUrl:\", postUrl);\n" +
                            "\n" +
                            "            const hiddenInput = targetForm.querySelector(\"input[type='hidden']\");\n" +
                            "            if (!hiddenInput) {\n" +
                            "                console.error(\"Error: Hidden input not found.\");\n" +
                            "                return null;\n" +
                            "            }\n" +
                            "\n" +
                            "            const hiddenInputName = hiddenInput.getAttribute(\"name\") || \"\";\n" +
                            "            const hiddenInputValue = hiddenInput.value;\n" +
                            "            console.log(\"fetchItem: hiddenInputName:\", hiddenInputName);\n" +
                            "            console.log(\"fetchItem: hiddenInputValue:\", hiddenInputValue);\n" +
                            "\n" +
                            "            return `${postUrl}|${hiddenInputName}=${hiddenInputValue}`;\n" +
                            "        } catch (error) {\n" +
                            "            console.error(\"Error processing form:\", error);\n" +
                            "        }\n" +
                            "    }\n" +
                            "\n" +
                            "    return null;\n" +
                            "}\n" +
                            "\n" +
                            "function fetchServers(referer) {\n" +
                            "    const serverElems = document.querySelectorAll('.containerServers [data-link]');\n" +
                            "\n" +
                            "    if (!serverElems.length) {\n" +
                            "        console.error(\"Error: No server elements found.\");\n" +
                            "        return;\n" +
                            "    }\n" +
                            "\n" +
                            "    const movieList = [];\n" +
                            "\n" +
                            "    serverElems.forEach(elem => {\n" +
                            "        let link = elem.getAttribute(\"data-link\");\n" +
                            "        if (!link || link.includes(\"عرب سيد\")) return;\n" +
                            "\n" +
                            "        link += `|Referer=${referer}`;\n" +
                            "\n" +
                            "        const server = {\n" +
                            "            videoUrl: link,\n" +
                            "            cardImageUrl: \""+movie.getCardImageUrl()+"\"," +
                            "            backgroundImageUrl: \""+movie.getBackgroundImageUrl()+"\"," +
                            "            state: "+Movie.RESOLUTION_STATE+","+
                            "            studio: \""+Movie.SERVER_ARAB_SEED+"\""+
                            "        };\n" +
                            "\n" +
                            "        const title = elem.querySelector(\"span\")?.textContent.trim() || elem.textContent.trim();\n" +
                            "        if (title && title.includes(\"عرب سيد\")) {\n" +
                            "           return;" +
                            "        }\n" +
                            "            server.title = title;\n" +
                            "\n" +
                            "        movieList.push(server);\n" +
                            "    });\n" +
                            "\n" +
                            "    if (movieList.length > 0) {\n" +
                            "        console.log(`Success: ${movieList.length} servers found.`);\n" +
                            "        MyJavaScriptInterface.myMethod(JSON.stringify(movieList));\n" +
                            "    }\n" +
                            "}\n";
            }
        }
        Log.d(TAG, "getWebScript: " + script);
        return script;
    }

    @Override
    public ArrayList<Movie> getHomepageMovies(boolean handleCookie, ActivityCallback<ArrayList<Movie>> activityCallback) {
//        return search("la casa", activityCallback, handleCookie);
//        return search("sonic", activityCallback, handleCookie);
                return search(
////                config.url +"/category/netfilx/");
//                getConfig().getUrl() + "/latest1/", activityCallback, handleCookie);
                getConfig().getUrl() + "/category/netfilx/افلام-netfilx/", activityCallback, handleCookie);
    }

    private void filterSearchResultMovies(Movie movie, ArrayList<Movie> movieList) {
        if (movie == null) {
            return;
        }

        if (movie.getType() == MovieType.EPISODE) {
            String cleanedTitle = movie.getTitle()
                    .replace("مشاهدة", "")
                    .replace("مسلسل", "")
                    .replaceAll("موسم\\s*\\d+", "")
                    .replaceAll("حلقة\\s*\\d+", "")
                    .replace("والاخيرة", "")
                    .trim();
            Log.d(TAG, "filterSearchResultMovies: cleaned title "+cleanedTitle);
            boolean seriesExists = false;
            for (Movie m : movieList) {
                if (m.getTitle().equals(cleanedTitle)) {
                    seriesExists = true;
                    break;
                }
            }

            if (!seriesExists) {
//                movie.setVideoUrl(getConfig().getUrl() + "/series/"+cleanedTitle);
                String url = movie.getVideoUrl();
                if (!url.startsWith("http")){
                    url = getConfig().getUrl() + url;
                }
//                String newUrl = getConfig().getUrl() + "/series/"+cleanedTitle;
                String newUrl = null;
                Document doc = this.getRequestDoc(url, OmerFlexApplication.getAppContext());
                if (doc != null){
                    //fetch session
                    Elements boxs = doc.getElementsByClass("List--Seasons--Episodes");
                    if (boxs.isEmpty()) {
                        // Select elements whose class attribute contains both "seasons" and "list"
                        boxs = doc.select("[class*=seasons][class*=list]");
                    }
                    Log.d(TAG, "generateGroupOfGroupMovie: boxes size: "+boxs.size());

                    for (Element box : boxs) {
                        Elements lis = box.getElementsByTag("a");
                        for (Element li : lis) {
                            newUrl = li.attr("href");
                            Log.d(TAG, "filterSearchResultMovies: series url found: "+ newUrl);
                            break;
                        }
                        if (newUrl != null){
                            break;
                        }
                    }
                }
                if (newUrl == null){
                    newUrl = getConfig().getUrl() + "/series/"+cleanedTitle;
                }
                movie.setVideoUrl(newUrl);
                movie.setTitle(cleanedTitle);
                movie.setType(MovieType.SERIES);
                movie.setState(Movie.GROUP_OF_GROUP_STATE);
                movieList.add(movie);
            }
        } else {
            movieList.add(movie);
        }
        Log.d(TAG, "filterSearchResultMovies: movielist:"+movieList);
    }


    @Override
    public String getServerId() {
        return Movie.SERVER_ARAB_SEED;
    }

    public String getCustomUserAgent(int state) {
        return "Android 7";
    }

    public MovieFetchProcess handleJSResult(String elementJson, List<Movie> movies, Movie movie) {
        // just return the result if its browse state
        if (movie.getState() == Movie.BROWSER_STATE) {
            movie.setSubList(movies);
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_RETURN_RESULT, movie);
        }
        return super.handleJSResult(elementJson, movies, movie);
    }

    public boolean shouldOverrideUrlLoading(Movie movie, WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        String newUrl = request.getUrl().toString().length() > 25 ? request.getUrl().toString().substring(0, 25) : request.getUrl().toString();
        if (url.contains("/e/")) {
            Log.d(TAG, "shouldOverrideUrlLoading:0b false: " + url);
            return false;
        }
        if (newUrl.contains("gameland")) {
            Log.d(TAG, "shouldOverrideUrlLoading:0c false: " + url);
            return false;
        }
        return super.shouldOverrideUrlLoading(movie, view, request);
    }
}
