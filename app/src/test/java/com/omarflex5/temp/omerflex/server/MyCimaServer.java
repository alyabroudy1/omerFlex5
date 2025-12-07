package com.omarflex5.temp.omerflex.server;

import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.MovieType;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.entity.ServerConfig;
import com.omerflex.view.BrowserActivity;
import com.omerflex.view.DetailsActivity;
import com.omerflex.view.VideoDetailsFragment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

public class MyCimaServer extends AbstractServer {
    static String TAG = "MyCima";
    public static String WEBSITE_URL = "https://mycima.io";

    public MyCimaServer() {
    }

    @Override
    public ArrayList<Movie> search(String query, ActivityCallback<ArrayList<Movie>> activityCallback, boolean handleCookie) {
        Log.i(getLabel(), "search: " + query);
        String searchContext = query;
        String url = query;
        boolean multiSearch = false;
        if (!query.contains("http")) {
            url = this.getSearchUrl(query);
            multiSearch = true;
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
            String title = searchContext;
            //int imageResourceId = R.drawable.default_image;
            // String cardImageUrl = "android.resource://" + activity.getPackageName() + "/" + imageResourceId;
            String cardImageUrl = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
            String backgroundImageUrl = "http://commondatastorage.googleapis.com/android-tv/Sample%20videos/April%20Fool's%202013/Introducing%20Google%20Nose/bg.jpg";
            Movie m = new Movie();
            m.setTitle(title);
            m.setDescription("نتائج البحث في الاسفل...");
            m.setStudio(Movie.SERVER_MyCima);
            m.setVideoUrl(url);
//            m.setVideoUrl(doc.location());
            //  m.setVideoUrl("https://www.google.com/");
            m.setState(Movie.COOKIE_STATE);
            m.setType(MovieType.COOKIE);
            // m.setState(Movie.RESULT_STATE);
            m.setCardImageUrl(cardImageUrl);
            m.setBackgroundImageUrl(backgroundImageUrl);
            m.setRate("");
            m.setSearchContext(searchContext);
            movieList.add(m);

            activityCallback.onInvalidCookie(movieList, getLabel());
            return movieList;
        }

//        if (multiSearch) {
////            String extraUrl = query.startsWith("http") ? query : (doc.baseUri().isEmpty() ? url : doc.baseUri());
//            Log.d(TAG, "search: extraUrl: "+url);
//            this.getExtraSearchSeriesMovieList(url, movieList);
//            this.getExtraSearchAnimeMovieList(url, movieList);
//        }


        Elements lis = null;
        if (query.contains("http")) {
            Elements box = doc.getElementsByClass("Grid--WecimaPosts");
            if (!box.isEmpty()) {
                Elements boxItems = Objects.requireNonNull(box.first()).getElementsByClass("GridItem");
                if (!boxItems.isEmpty()) {
                    lis = boxItems;
                }
            }
        }
        if (lis == null) {
            lis = doc.getElementsByClass("GridItem");

            if (lis.isEmpty()) {
                // Select elements with class containing "media-card"
                lis = doc.select("[class*=media-card]");
            }
        }
//        Log.d(TAG, "search: lis size: "+lis.size());
        for (Element li : lis) {
            Movie movie = this.generateMovieFromDocElement(li);
            filterSearchResultMovies(movie, movieList);
        }

        if (!movieList.isEmpty()){
            Movie nextPage = this.generateNextPageMovie(doc);

            Log.d(TAG, "search: nextPage: " + nextPage);

            if (nextPage != null) {
                movieList.add(nextPage);
            }
        }

        activityCallback.onSuccess(movieList, getLabel());
        return movieList;
    }

    private void filterSearchResultMovies(Movie movie, ArrayList<Movie> movieList) {
        if (movie == null) {
            return;
        }

        if (movie.getType() == MovieType.EPISODE) {
//            String cleanedTitle = cleanMovieTitle(movie.getTitle());
//            Log.d(TAG, "filterSearchResultMovies: cleaned title "+cleanedTitle);
            boolean seriesExists = false;
            for (Movie m : movieList) {
                if (m.getTitle().equals(movie.getTitle())) {
                    seriesExists = true;
                    break;
                }
            }

            if (!seriesExists) {
//                movie.setVideoUrl(getConfig().getUrl() + "/series/"+cleanedTitle);
//                String url = movie.getVideoUrl();
//                if (!url.startsWith("http")){
//                    url = getConfig().getUrl() + url;
//                }
////                String newUrl = getConfig().getUrl() + "/series/"+cleanedTitle;
//                String newUrl = null;
////                Document doc = this.getRequestDoc(url, OmerFlexApplication.getAppContext());
//                Document doc = getSearchRequestDoc(url);
//                if (doc != null &&  !(doc.title().contains("Just a moment...") || doc.title().contains("Checking your browser") || doc.title().contains("لحظة…"))){
//                    //fetch session
//                    Elements boxs = doc.getElementsByClass("List--Seasons--Episodes");
//                    if (boxs.isEmpty()) {
//                        // Select elements whose class attribute contains both "seasons" and "list"
//                        boxs = doc.select("[class*=seasons][class*=list]");
//                    }
////                    Log.d(TAG, "generateGroupOfGroupMovie: boxes size: "+boxs.size());
//
//                    for (Element box : boxs) {
//                        Elements lis = box.getElementsByTag("a");
//                        for (Element li : lis) {
//                            newUrl = li.attr("href");
//                            Log.d(TAG, "filterSearchResultMovies: series url found: "+ newUrl);
//                            break;
//                        }
//                        if (newUrl != null){
//                            break;
//                        }
//                    }
//                }
//                if (newUrl == null){
//                    newUrl = getConfig().getUrl() + "/series/"+cleanedTitle;
//                }
//                movie.setVideoUrl(newUrl);
//                movieList.add(movie);
//                Movie collection = Movie.clone(movie);
//                movie.setTitle(cleanedTitle);
                //   change type to collection to avoid being saved to db
                movie.setType(MovieType.COLLECTION);
                // let state be GROUP_OF_GROUP_STATE to fetch it correctly
                movie.setState(Movie.GROUP_OF_GROUP_STATE);
                movieList.add(movie); // add the collection movie
            }
        } else {
            boolean movieExists = false;
            for (Movie m : movieList) {
                if (m.getTitle().equals(movie.getTitle())) {
                    movieExists = true;
                    break;
                }
            }
            if (!movieExists) {
                movieList.add(movie);
            }
        }
//        Log.d(TAG, "filterSearchResultMovies: movielist:"+movieList);
    }

    @NonNull
    private static String cleanMovieTitle(String title) {
       return title
                .replace("وتحميل", "")
                .replace("تحميل", "")
                .replace("ومشاهدة", "")
                .replace("ومشاهده", "")
                .replace("مشاهدة", "")
                .replace("مشاهده", "")
                .replace("مسلسل", "")
                .replaceAll("موسم\\s*\\d+", "")
                .replaceAll("حلقة\\s*\\d+", "")
                .replaceAll("حلقه\\s*\\d+", "")
                .replaceAll("الحلقة\\s*\\d+", "")
                .replaceAll("الحلقه\\s*\\d+", "")
                .replace("والاخيرة", "")
                .replace("والاخيره", "")
                .replace("والأخيرة", "")
                .replace("والأخيره", "")
                .replace("الاخيرة", "")
                .replace("الاخيره", "")
                .replace("الأخيرة", "")
                .replace("الأخيره", "")
                .replace("انمي", "")
                .replace("أنمي", "")
                .replace("فيلم", "")
                .replace("فلم", "")
                .trim();
    }

    protected String getSearchUrl(String query) {
//        String searchUrl = query;
        if (query.contains("http")) {
            return query;
        }
        String searchPart = "/search/";
        ServerConfig config = getConfig();
        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            return WEBSITE_URL + searchPart + query;
        }

        if (query.startsWith("/")) {
            return config.getUrl() + query;
        }

        return config.getUrl() + searchPart + query;
    }

    private void getExtraSearchSeriesMovieList(String url, ArrayList<Movie> movies) {
        Log.d(TAG, "getExtraSearchMovieList: ");
        //search series
        String seriesSearch = url + "/list/series/";

        Document doc = getRequestDoc(seriesSearch);
        Log.d(TAG, "getExtraSearchMovieList: doc: " + doc.title());
        Elements lis2 = doc.getElementsByClass("GridItem");
        if (lis2.isEmpty()){
            lis2 =  doc.select("[class*=media][class*=card]");
        }
        Log.d(TAG, "getExtraSearchMovieList: lis2: " + lis2.size());
        for (Element li : lis2) {
            Movie movie = generateMovieFromDocElement(li);
            if (movie != null) {
                movies.add(movie);
            }
        }
    }

    private boolean movieExists(Movie movie, ArrayList<Movie> movies) {
        for (Movie mov: movies) {
            if (mov.getTitle().equals(movie.getTitle())){
                return true;
            }
        }
        return false;
    }

    private void getExtraSearchAnimeMovieList(String url, ArrayList<Movie> movies) {
        Log.d(TAG, "getExtraSearchMovieList: ");
        //search anime
        String animeSearch = url + "/list/anime/";
        Document doc = getRequestDoc(animeSearch);
        Log.d(TAG, "getExtraSearchMovieList: doc anime: " + doc.title());
        Elements lis3 = doc.getElementsByClass("GridItem");
        if (lis3.isEmpty()){
            lis3 =  doc.select("[class*=media][class*=card]");
        }
        Log.d(TAG, "getExtraSearchMovieList: lis3 anime: " + lis3.size());
        for (Element li : lis3) {
            //              Log.i(TAG, "element found: ");
            Movie movie = generateMovieFromDocElement(li);
            if (movie != null) {
                movies.add(movie);
            }
        }
    }

    protected ArrayList<Movie> getSearchMovieList(Document doc) {
        return new ArrayList<>();
    }

    @Override
    public String getServerId() {
        return Movie.SERVER_MyCima;
    }

    private Movie generateNextPageMovie(Document doc) {
        Movie nextPage = null;
        //nextpage
//        Elements nextLinkNaviElements = doc.getElementsByClass("next");
        Elements nextLinkNaviElements = doc.select("[class*=next][class*=page]");
        //        Log.d(TAG, "search: nextpage1 found :"+nextLinkNaviElements.size());
        if (!nextLinkNaviElements.isEmpty()) {
            Element nextLinkNaviElement = nextLinkNaviElements.first();
            if (nextLinkNaviElement != null) {
                String videoUrl = nextLinkNaviElement.attr("href");
                nextPage = new Movie();
                nextPage.setTitle("التالي");
                nextPage.setDescription("0");
                nextPage.setStudio(Movie.SERVER_MyCima);
                nextPage.setVideoUrl(videoUrl);
                nextPage.setCardImageUrl("https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png");
                nextPage.setBackgroundImageUrl("https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png");
                nextPage.setState(Movie.NEXT_PAGE_STATE);
//                nextPage.setMainMovie(nextPage);
                nextPage.setMainMovieTitle(videoUrl);
            }
        }
        return nextPage;
    }

    private Movie generateMovieFromDocElement(Element li) {
        Movie movie = null;
        try {
            Element linkElem = li.getElementsByAttribute("href").first();
            if (linkElem != null) {
                movie = new Movie();
                movie.setStudio(Movie.SERVER_MyCima);

                String videoUrl = linkElem.attr("href");

                Element nameElem = li.getElementsByAttribute("title").first();
                String title = "";
                Element imageElem = li.getElementsByAttribute("style").first();
                if (nameElem != null) {
                    title = nameElem.attr("title");
                    imageElem = nameElem.getElementsByAttribute("style").first();

                }

//                Element imageElem = li.getElementsByAttribute("style").first();
                String image = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
                if (imageElem == null){
                    imageElem = li.select("[class*=media][class*=card]").first();
                }
                if (imageElem != null) {
//                    image = imageElem.attr("style");
                    image = imageElem.attr("data-src");
//                    Log.d(TAG, "generateMovieFromDocElement: title: "+title);
//                    Log.d(TAG, "generateMovieFromDocElement: imageElem-1: "+imageElem.outerHtml());
                }

                if (!image.contains("http")) {
                    // image = li.getElementsByClass("BG--GridItem").attr("style");
                    Element image2Elem = li.getElementsByAttribute("data-lazy-style").first();
                    if (image2Elem != null) {
                        image = image2Elem.attr("data-lazy-style");
                    }
                }

                if (image.isEmpty()) {
                    Element image3Elem = li.getElementsByClass("BG--GridItem").first();
                    if (image3Elem != null) {
                        image = image3Elem.attr("data-owl-img");
                    }
                }

                if (image.isEmpty()) {
                    Element image4Elem = li.select("[class*=media][class*=card][class*=thumb]").first();
                    if (image4Elem != null) {
                        Element image5Elem = image4Elem.getElementsByAttribute("style").first();
                        if (image4Elem != null) {
                            image = image5Elem.attr("style");
                        }
                    }
                }

                if (image.contains("(") && image.contains(")")) {
                    image = image.substring(image.indexOf('(') + 1, image.indexOf(')'));
                }
//                Log.d(TAG, "generateMovieFromDocElement: image: "+image);
//                Log.d(TAG, "generateMovieFromDocElement: title3: "+title);
//                Log.d(TAG, "generateMovieFromDocElement: image3: "+image);

                movie.setTitle(title);
                movie.setVideoUrl(Util.getUrlPathOnly(videoUrl));
//                if (isSeries(movie)) {
//                    movie.setState(Movie.GROUP_OF_GROUP_STATE);
//                } else {
//                    movie.setState(Movie.ITEM_STATE);
//                }
                // detectMovieState important after setting the title and videoUrl
//                movie.setState(detectMovieState(movie));
                movie.setStudio(Movie.SERVER_MyCima);
                movie.setDescription("");
                Log.d(TAG, "generateMovieFromDocElement: ardImageUrl(image): "+image);
                movie.setCardImageUrl(image);
                movie.setBackgroundImageUrl(image);
                movie.setBgImageUrl(image);

                movie = updateMovieState(movie);
                // titles should be cleand only after state update
                if (!title.isEmpty()){
                    title = cleanMovieTitle(title);
                }
                movie.setTitle(title);
//                movie.setMainMovie(movie);
            }

        } catch (Exception e) {
            Log.i(getLabel(), "error: " + e.getMessage());
        }
        return movie;
    }

    public MovieFetchProcess fetchBrowseItem(Movie movie, ActivityCallback<Movie> activityCallback) {
        Movie clonedMovie = Movie.clone(movie);
        clonedMovie.setFetch(Movie.REQUEST_CODE_EXTERNAL_PLAYER);
        // to do nothing and wait till result returned to activity only the first fetch
//        return startWebForResultActivity(clonedMovie);
        activityCallback.onInvalidCookie(clonedMovie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, clonedMovie);
    }

    private void addMovieToHistory(Movie movie) {

        final Movie m = new Movie();
        /* final Movie m = Movie.buildMovieInfo(
                movie.getHistoryTitle(),
                movie.getDescription(),
                movie.getStudio(),
                movie.getHistoryUrl(),
                // movie.getHistoryUrl(),
                movie.getCardImageUrl(),
                movie.getBackgroundImageUrl(),
                movie.getHistoryState(),
                movie.getRate(),
                movie.getHistoryUrl(),
                movie.getHistoryTitle(),
                movie.getHistoryState(),
                movie.getHistoryCardImageUrl(),
                movie.getCreatedAt(),
                movie.getHistoryDescription()
        );
        */

    }

    protected MovieFetchProcess fetchSeriesAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        if (action == Movie.GROUP_OF_GROUP_STATE) {
            return fetchGroupOfGroup(movie, activityCallback);
        }
        return fetchGroup(movie, activityCallback);
    }

    protected MovieFetchProcess fetchItemAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
//        Log.d(TAG, "fetchItemAction: 55");
        switch (action) {
            case Movie.BROWSER_STATE:
                return fetchBrowseItem(movie, activityCallback);
            case Movie.COOKIE_STATE:
                return fetchCookie(movie);
            case Movie.ACTION_WATCH_LOCALLY:
                return fetchWatchLocally(movie, activityCallback);
//            case Movie.RESOLUTION_STATE:
//                return fetchResolutions(movie);
//            case Movie.VIDEO_STATE:
//                return fetchVideo(movie);
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
        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_EXOPLAYER, movie);
    }

    @Override
    public Movie updateMovieState(Movie movie) {
        String u = movie.getVideoUrl();
        String n = movie.getTitle();

        boolean seriesCase = u.contains("/series") || (!u.contains("/watch") &&
        (!(n.contains("حلقة") || n.contains("حلقه")) &&
                        n.contains("انمي") || n.contains("برنامج") || n.contains("مسلسل")
                ));
//        boolean seriesCase = n.contains("انمي") || n.contains("برنامج") || n.contains("مسلسل")
//                || u.contains("series") || n.contains("فلام");
//        Log.d(TAG, "updateMovieState: is series :"+seriesCase+ ", "+u);
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


    public String determineRelatedMovieLabel(Movie movie) {
        switch (movie.getState()) {
            case Movie.GROUP_OF_GROUP_STATE:
                return "المواسم/الحلقات";
            case Movie.GROUP_STATE:
                return "الحلقات";
            case Movie.ITEM_STATE:
                return "الجودة";
            default:
                return "الروابط";
        }
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
            movie.setFetch(Movie.REQUEST_CODE_MOVIE_UPDATE);
            activityCallback.onInvalidCookie(movie, getLabel());
        }

        return generateGroupOfGroupMovie(doc, movie, activityCallback);
    }

    private MovieFetchProcess startWebForResultActivity(Movie movie) {
        Log.d(TAG, "startWebForResultActivity: " + movie);
//        Intent browse = new Intent(activity, BrowserActivity.class);
//        browse.putExtra(DetailsActivity.MOVIE, (Serializable) movie);
//        browse.putExtra(DetailsActivity.MAIN_MOVIE, (Serializable) movie.getMainMovie());
//
//        fragment.startActivityForResult(browse, movie.getFetch());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_BROWSER_ACTIVITY_REQUIRE, movie);
    }

    private MovieFetchProcess generateGroupOfGroupMovie(Document doc, Movie movie, ActivityCallback<Movie> activityCallback) {
        //get link of episodes page
        Log.d(TAG, "generateGroupOfGroupMovie: " + doc.title());
        Element descElem = doc.getElementsByClass("PostItemContent").first();
        if (descElem == null) {
            descElem = doc.select("[class*=story][class*=content]").first();
        }
        String desc = "";
        if (descElem != null) {
            desc = descElem.text();
            movie.setDescription(desc);
        }

        //fetch session
        Elements boxs = doc.getElementsByClass("List--Seasons--Episodes");
        if (boxs.isEmpty()) {
            // Select elements whose class attribute contains both "seasons" and "list"
            boxs = doc.select("[class*=seasons][class*=list]");
        }
        Log.d(TAG, "generateGroupOfGroupMovie: boxes size: "+boxs.size());

        boolean isCollection = movie.getType() == MovieType.COLLECTION;
        boolean seriesUrlFound = false;
        if (isCollection){
            // Step 1: Find <singlesection> tag
//            Element singleSection = doc.selectFirst("singlesection");
            Element singleSection = doc.select("[class*=page][class*=section][class*=series]").first();

            if (singleSection != null) {
                Element linkElement = singleSection.select("a").first();
                if (linkElement != null) {
                    String seriesUrl = linkElement.attr("href");
                    System.out.println("Series URL: " + seriesUrl);
                    movie.setVideoUrl(seriesUrl);
                    movie.setType(MovieType.SERIES);
                    seriesUrlFound = true;
                } else {
                    System.out.println("Series link not found.");
                }
            }


        }
        if (movie.getSubList() == null) {
            movie.setSubList(new ArrayList<>());
        }

        if (boxs.isEmpty()) {
            if (!isCollection){
                movie.setState(Movie.GROUP_STATE);
                movie.setType(MovieType.SEASON);
            }
            if (movie.getVideoUrl() == null) {
                return null;
            }
            return fetchGroup(movie, activityCallback);
        }

        for (Element box : boxs) {
            Elements lis = box.getElementsByTag("a");
            for (Element li : lis) {
                String title = li.text();
                String videoUrl = li.attr("href");
                String cardImageUrl = movie.getCardImageUrl();
                String backgroundImageUrl = movie.getCardImageUrl();
                Movie season = Movie.clone(movie);
                if (!isCollection){
                    season.setParentId(movie.getId());
                }
                season.setTitle(title);
                season.setDescription(desc);
                season.setVideoUrl(Util.getUrlPathOnly(videoUrl));
                season.setState(Movie.GROUP_STATE);
                season.setType(MovieType.SEASON);

                movie.addSubList(season);
            }
        }
        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    private MovieFetchProcess fetchGroup(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchGroup: " + movie.getVideoUrl());

        String url = movie.getVideoUrl();
        if (!url.startsWith("http")){
            url = getConfig().getUrl() + url;
        }
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

        return generateGroupMovie(doc, movie, activityCallback);
    }

    private MovieFetchProcess generateGroupMovie(Document doc, Movie movie, ActivityCallback<Movie> activityCallback) {
        boolean isCollection = movie.getType() == MovieType.COLLECTION;
        //get link of episodes page
        Element descElem = doc.getElementsByClass("PostItemContent").first();
        if (descElem == null) {
            descElem = doc.select("[class*=story][class*=content]").first();
        }
        String desc = "";
        if (descElem != null) {
            desc = descElem.text();
            movie.setDescription(desc);
        }

        if (movie.getSubList() == null) {
            movie.setSubList(new ArrayList<>());
        }

        //fetch session
        Elements boxs = doc.getElementsByClass("Episodes--Seasons--Episodes");
        if (boxs.isEmpty()){
            boxs = doc.select("[class*=episodes][class*=list]");
        }
        Log.d(TAG, "generateGroupMovie: episodes list: "+boxs.size());
        for (Element box : boxs) {

            Elements lis = box.getElementsByTag("a");

            //            Log.i("Count", "boxs:" + boxs.size());
            for (Element li : lis) {
                String title = li.text();
                String videoUrl = li.attr("href");

                Movie a = new Movie();
                a.setStudio(Movie.SERVER_MyCima);

                Movie episode = Movie.clone(movie);
                if (!isCollection){
                    episode.setParentId(movie.getId());
                }
                episode.setTitle(title);
                episode.setVideoUrl(Util.getUrlPathOnly(videoUrl));
                episode.setState(Movie.ITEM_STATE);
                episode.setType(MovieType.EPISODE);

                movie.addSubList(episode);
            }
        }

        Element moreEp = doc.select(".MoreEpisodes--Button").first();
        if (moreEp != null) {
            String name = moreEp.attr("data-term");
            if (name == null || name.isEmpty()) {
                name = movie.getTitle();
            }

            String domain;
            String baseUri = doc.baseUri();
            if (baseUri != null && baseUri.startsWith("http")) {
                int doubleSlash = baseUri.indexOf("//");
                if (doubleSlash != -1) {
                    int nextSlash = baseUri.indexOf('/', doubleSlash + 2);
                    if (nextSlash != -1) {
                        domain = baseUri.substring(0, nextSlash);
                    } else {
                        domain = baseUri;
                    }
                } else {
                    domain = getConfig().getUrl();
                }
            } else {
                domain = getConfig().getUrl();
            }

            int episodesCount = 0;
            if (movie.getSubList() != null) {
                episodesCount = movie.getSubList().size();
            }

            String moreUrl = domain + "/AjaxCenter/MoreEpisodes/" + name + "/" + episodesCount + "/";
            Log.d(TAG, "Fetching hidden episodes from: " + moreUrl);

            getConfig().getHeaders().put("Accept", "application/json, text/javascript, */*");
            getConfig().getHeaders().put("X-Requested-With", "XMLHttpRequest");
            Document doc2 = getRequestDoc(moreUrl, OmerFlexApplication.getAppContext());
            getConfig().getHeaders().remove("Accept");
            getConfig().getHeaders().remove("X-Requested-With");

            if (doc2 != null) {
                // Select all elements that have an href attribute
                Elements links2 = doc2.select("[href]");

                for (Element link : links2) {
                    // Get the title element inside the link or elsewhere
                    Element titleElm = link.selectFirst("episodetitle"); // search inside link if needed
                    String title = "";
                    if (titleElm == null) {
                        continue;
                    }
                        // Get raw text
                        title = titleElm.text();

                        // Remove literal substrings
                        title = title.replace("<\\/episodeTitle><\\/episodeArea><\\/a>", "")
                                .replace("</episodeTitle></episodeArea></a>", "")
                                .replace("\\", "")
                                .replace("\"", "")
                                .replace("}", "")
                                .trim();

                        // Decode Unicode like u0627 -> Arabic
                        title = decodeUnicodeEscapes(title);

                    // Clean the link URL
                    String linkUrl = link.attr("href")
                            .replace("\\", "")  // remove backslashes
                            .replace("\"", ""); // remove quotes
                    // Decode percent-encoded URL (e.g., %d9%85 -> Arabic)
                    try {
                        linkUrl = URLDecoder.decode(linkUrl, "UTF-8").trim();
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }

                    Movie episode = Movie.clone(movie);
                    episode.setParentId(movie.getId());
                    episode.setTitle(title);
                    episode.setVideoUrl(Util.getUrlPathOnly(linkUrl));
                    episode.setState(Movie.ITEM_STATE);
                    episode.setType(MovieType.EPISODE);
                    movie.addSubList(episode);
                }
            }
        }

        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    private static String decodeUnicodeEscapes(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length();) {
            if (i + 5 < input.length() && input.charAt(i) == 'u') {
                // handle u0627 style
                try {
                    String hex = input.substring(i + 1, i + 5);
                    int codePoint = Integer.parseInt(hex, 16);
                    sb.append((char) codePoint);
                    i += 5;
                    continue;
                } catch (NumberFormatException e) {
                    // fall through if not valid
                }
            }
            sb.append(input.charAt(i));
            i++;
        }
        return sb.toString();
    }


    private MovieFetchProcess fetchItem(final Movie movie, ActivityCallback<Movie> activityCallback) {
        Log.i(TAG, "fetchItem: " + movie.getVideoUrl());
        String url = movie.getVideoUrl();
        if (!url.startsWith("http")){
            url = getConfig().getUrl() + url;
        }
//            Document doc = Jsoup.connect(url).header(
//                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8").header(
//                    "User-Agent", " Mozilla/5.0 (Linux; Android 8.1.0; Android SDK built for x86 Build/OSM1.180201.031; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/69.0.3497.100 Mobile Safari/537.36").header(
//                    "accept-encoding", "gzip, deflate").header(
//                    "accept-language", "en,en-US;q=0.9").header(
//                    "x-requested-with", "pc1"
//            ).timeout(0).get();
        //Elements links = doc.select("a[href]");

        Document doc = getRequestDoc(url, OmerFlexApplication.getAppContext());
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

        return generateItemMovie(doc, movie, activityCallback);
    }

    private MovieFetchProcess fetchCookie(Movie movie) {
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_COOKE_REQUIRE, movie);
    }

    private boolean isSeries(Movie movie) {
        String u = movie.getVideoUrl();
        String n = movie.getTitle();
        // Log.d(TAG, "isSeries: title:" + n + ", url=" + u);
        return n.contains("انمي") || n.contains("برنامج") || n.contains("مسلسل")
                || u.contains("series");
    }

//    public int fetchNextAction(Movie movie) {
////        Log.d(TAG, "fetchNextAction: "+ (movie.getFetch() == Movie.REQUEST_CODE_MOVIE_UPDATE) );
//        switch (movie.getState()) {
//            case Movie.GROUP_OF_GROUP_STATE:
//            case Movie.GROUP_STATE:
//            case Movie.ITEM_STATE:
//                return VideoDetailsFragment.ACTION_OPEN_DETAILS_ACTIVITY;
//            case Movie.BROWSER_STATE:
//                return VideoDetailsFragment.ACTION_OPEN_NO_ACTIVITY;
//        }
//        return VideoDetailsFragment.ACTION_OPEN_EXTERNAL_ACTIVITY;
//    }

    @Override
    public String getWebScript(int mode, Movie movie) {
        String script = null;

        // let postList = [];
        //    document.querySelectorAll('.GridItem').forEach(item => {
        //        let post = {};
        //
        //        // Extract title
        //        let titleElement = item.querySelector('a[title]');
        //        post.title = titleElement ? titleElement.getAttribute('title').replace('مشاهدة ', '') : '';
        //
        //        // Extract video URL
        //        post.videoUrl = titleElement ? titleElement.getAttribute('href') : '';
        //
        //        // Extract card image safely
        //        let imageElement = item.querySelector('.BG--GridItem');
        //        if (imageElement) {
        //            let style = imageElement.getAttribute('style') || '';
        //            let match = style.match(/url\((.*?)\)/);
        //            post.cardImage = match ? match[1] : '';
        //        } else {
        //            post.cardImage = ''; // Default value if no image found
        //        }
        //
        //        // Add post to the list only if all properties exist
        //        if (post.title && post.videoUrl && post.cardImage) {
        //            postList.push(post);
        //        }
        //    });
        //
        //    // Extract next page if available
        //    let nextPageElement = document.querySelector('.next');
        //    if (nextPageElement) {
        //          let nextPage = {};
        //        let videoUrl = nextPageElement.getAttribute('href');
        //        if (videoUrl) {
        // nextPage.title = "التالي";
        //            nextPage.description = "0";
        //            nextPage.studio = Movie.SERVER_MyCima;
        //            nextPage.videoUrl = videoUrl;
        //            nextPage.cardImageUrl = "https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png";
        //            nextPage.backgroundImageUrl = "https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png";
        //            nextPage.state = "https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png";
        //            postList.push(nextPage);
        //        }
        //    }

        Log.d(TAG, "getWebScript: m:" + mode + ", f:" + movie.getFetch());
        if (mode == BrowserActivity.WEB_VIEW_MODE_ON_PAGE_STARTED) {
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
            if (movie.getState() == Movie.COOKIE_STATE) {
                Log.d(TAG, "getScript:WEB_VIEW_MODE_ON_PAGE_STARTED COOKIE_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        "let postList = [];\n" +
                    "    document.querySelectorAll('.GridItem').forEach(item => {\n" +
                    "        let post = {};\n" +
                    "\n" +
                    "        // Extract title\n" +
                    "        let titleElement = item.querySelector('a[title]');\n" +
                    "        post.title = titleElement ? titleElement.getAttribute('title').replace('مشاهدة ', '') : '';\n" +
                    "\n" +
                    "        // Extract video URL\n" +
                    "        post.videoUrl = titleElement ? titleElement.getAttribute('href') : '';\n" +
                    "\n" +
                    "        // Extract card image safely\n" +
                    "        post.cardImageUrl = extractImageUrl(item);\n" +
                        "    post.backgroundImageUrl = post.cardImageUrl; " +
                    "\n" +
                    "        // Add post to the list only if all properties exist\n" +
                    "        if (post.videoUrl) {\n" +
                        "           post.studio = \""+Movie.SERVER_MyCima+"\";" +
                        "           post.state = detectMovieState(post);" +
                        "console.log(post.cardImageUrl);" +
                    "            postList.push(post);\n" +
                    "        }\n" +
                    "    });\n" +
                    "\n" +
                    "    // Extract next page if available\n" +
                    "    let nextPageElement = document.querySelector('.next');\n" +
                    "    if (nextPageElement) {\n" +
                    "          let nextPage = {};\n" +
                    "        let videoUrl = nextPageElement.getAttribute('href');\n" +
                    "        if (videoUrl) {\n" +
                    " nextPage.title = \"التالي\";\n" +
                    "            nextPage.description = \"0\";\n" +
                    "            nextPage.studio = \""+Movie.SERVER_MyCima+"\";\n" +
                    "            nextPage.videoUrl = videoUrl;\n" +
                    "            nextPage.cardImageUrl = \"https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png\";\n" +
                    "            nextPage.backgroundImageUrl = \"https://colorslab.com/blog/wp-content/uploads/2012/03/next-button-usability.png\";\n" +
                    "            nextPage.state = "+Movie.NEXT_PAGE_STATE+ ";" +
                    "            postList.push(nextPage);\n" +
                    "        }\n" +
                    "    }" +
                        "if (postList && postList.length > 0) {" +
                        "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                        "}" +
                        "" +
                        "function extractImageUrl(item) {\n" +
                        "    let defaultImage = \"https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png\";\n" +
                        "    let image = defaultImage;\n" +
                        "\n" +
                        "    // Try to get the image from \"style\" attribute\n" +
                        "    let imageElem = item.querySelector(\"[style]\");\n" +
                        "    if (imageElem) {\n" +
                        "        image = imageElem.getAttribute(\"style\") || \"\";\n" +
                        "    }\n" +
                        "\n" +
                        "    // Check if the extracted style contains a valid URL\n" +
                        "    if (!image.includes(\"http\")) {\n" +
                        "        let image2Elem = item.querySelector(\"[data-lazy-style]\");\n" +
                        "        if (image2Elem) {\n" +
                        "            image = image2Elem.getAttribute(\"data-lazy-style\") || \"\";\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "    // If still empty, check \"data-owl-img\" attribute\n" +
                        "    if (!image) {\n" +
                        "        let image3Elem = item.querySelector(\".BG--GridItem\");\n" +
                        "        if (image3Elem) {\n" +
                        "            image = image3Elem.getAttribute(\"data-owl-img\") || \"\";\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "    // Extract the URL inside `url(...)`\n" +
                        "    let match = image.match(/url\\([\"']?(.*?)[\"']?\\)/);\n" +
                        "    if (match) {\n" +
                        "        image = match[1];\n" +
                        "    }\n" +
                        "\n" +
                        "    return image || defaultImage; // Return extracted image or default\n" +
                        "}" +
                        "" +
                        "function detectMovieState(movie) {\n" +
                        "    let u = movie.videoUrl || \"\";\n" +
                        "    let n = movie.title || \"\";\n" +
                        "\n" +
                        "    let seriesCase = n.includes(\"انمي\") || n.includes(\"برنامج\") || n.includes(\"مسلسل\") || u.includes(\"series\");\n" +
                        "    let itemCase = u.includes(\"/watch\") || n.includes(\"حلقة\") || n.includes(\"حلقه\");\n" +
                        "\n" +
                        "    if (itemCase) {\n" +
                        "        return "+Movie.ITEM_STATE+ ";" +
                        "    } else if (seriesCase) {\n" +
                        "        return "+Movie.GROUP_OF_GROUP_STATE+ ";" +
                        "    }\n" +
                        "    return "+Movie.ITEM_STATE+ ";" +
                        "}\n" +
                        "});";
            }
            else if (
                    movie.getState() == Movie.GROUP_OF_GROUP_STATE ||
                            movie.getState() == Movie.GROUP_STATE
            ) {
                Log.d(TAG, "getScript:mycima GROUP_OF_GROUP_STATE");
                String itemBoxName = "List--Seasons--Episodes";

                int state = Movie.GROUP_STATE;
                if (movie.getState() == Movie.GROUP_STATE) {
                    Log.d(TAG, "getScript:mycima GROUP_STATE");
                    itemBoxName = "Episodes--Seasons--Episodes";
                    state = Movie.ITEM_STATE;
                }
                script =
                        "document.addEventListener('DOMContentLoaded', () => {\n" +
                                "  if (document.title.includes('Just a moment')) {\n" +
                                "    console.log('[DEBUG] Skipping page due to title: ' + document.title);\n" +
                                "    return;\n" +
                                "  }\n" +
                                "  console.log('[DEBUG] Page title: ' + document.title);\n" +

                                "  let desc = '';\n" +
                                "  let descElems = document.getElementsByClassName('StoryMovieContent');\n" +
                                "  if (descElems.length > 0) {\n" +
                                "    desc = descElems[0].textContent;\n" +
                                "    console.log('[DEBUG] Found description in StoryMovieContent');\n" +
                                "  } else {\n" +
                                "    descElems = document.getElementsByClassName('PostItemContent');\n" +
                                "    if (descElems.length > 0) {\n" +
                                "      desc = descElems[0].textContent;\n" +
                                "      console.log('[DEBUG] Found description in PostItemContent');\n" +
                                "    } else {\n" +
                                "      console.log('[DEBUG] No description element found');\n" +
                                "    }\n" +
                                "  }\n" +

                                "  let boxs = document.getElementsByClassName('" + itemBoxName + "');\n" +
                                "  if (boxs.length === 0) {\n" +
                                "    boxs = document.getElementsByClassName('Episodes--Seasons--Episodes');\n" +
                                "  }\n" +
                                "  console.log('[DEBUG] Number of episode boxes: ' + boxs.length);\n" +

                                "  if (boxs.length > 0) {\n" +
                                "    let box = boxs[0];\n" +
                                "    let links = box.getElementsByTagName('a');\n" +
                                "    let postList = [];\n" +
                                "    console.log('[DEBUG] Found ' + links.length + ' links in episode box');\n" +

                                "    for (let link of links) {\n" +
                                "      let post = {};\n" +
                                "      post.title = link.textContent.trim();\n" +
                                "      post.videoUrl = link.getAttribute('href');\n" +
                                "      post.description = desc;\n" +
                                "      post.state = '" + state + "';\n" +
                                "      post.type = '" + (state == Movie.ITEM_STATE ? MovieType.EPISODE : MovieType.SEASON) + "';\n" +
                                "      post.studio = '" + movie.getStudio() + "';\n" +
                                "      post.fetch = '" + movie.getFetch() + "';\n" +
                                "      post.studio = '" + Movie.SERVER_MyCima + "';\n" +
                                "      post.parentId = '" + movie.getId() + "';\n" +
                                "      post.cardImageUrl = '" + movie.getCardImageUrl() + "';\n" +
                                "      post.backgroundImageUrl = '" + movie.getBackgroundImageUrl() + "';\n" +

                                "      postList.push(post);\n" +
                                "      console.log('[DEBUG] Added post: ' + JSON.stringify(post));\n" +
                                "    }\n" +

                                "    if (typeof MyJavaScriptInterface !== 'undefined') {\n" +
                                "      console.log('[DEBUG] Sending postList to Android: ' + postList.length + ' items');\n" +
                                "      MyJavaScriptInterface.myMethod(JSON.stringify(postList));\n" +
                                "    } else {\n" +
                                "      console.log('[DEBUG] MyJavaScriptInterface not available');\n" +
                                "    }\n" +
                                "  }\n" +
                                "});";

            }
            else if (movie.getState() == Movie.ITEM_STATE) {
                Log.d(TAG, "getScript:mycima ITEM_STATE");
                String videoLink = movie.getVideoUrl();
                if (!videoLink.startsWith("http")){
                    videoLink = getConfig().getUrl() + videoLink;
                }
                String referer = Util.extractDomain(videoLink, true, true);
                script = "document.addEventListener('DOMContentLoaded', () => {\n" +
                        "if (!document.title.includes('Just a moment')){" +
                        "var descElems = document.getElementsByClassName('StoryMovieContent');\n" +
                        "                        var postList = [];\n" +
                        "                        var desc = \"\";\n" +
                        "                        console.log(descElems.length);\n" +
                        "                        if(descElems.length > 0){\n" +
                        "                        desc = descElems[0].textContent;\n" +
                        "                        }else{\n" +
                        "                                descElems = document.getElementsByClassName('PostItemContent');\n" +
                        "                                if(descElems.length > 0){\n" +
                        "                        desc = descElems[0].textContent;\n" +
                        "                            }\n" +
                        "                        }\n" +
                        "                        // download links\n" +
                        "var uls = document.getElementsByClassName('List--Download--Wecima--Single');\n" +
                        "if(uls.length > 0){\n" +
                        "   var box = uls[0];\n" +
                        "       var lis = box.getElementsByTagName('li');\n" +
                        "        if(lis.length > 0){\n" +
                        "                                for (let l = 0; l < lis.length; l++) {\n" +
                        "                                   var li = lis[l];\n" +
                        "                                   var post = {};\n" +
                        "\n" +
                        "                                   var videoLinkElems = li.getElementsByTagName('a');\n" +
                        "                                   if(videoLinkElems.length > 0){\n" +
                        "                                    var videoLinkElem = videoLinkElems[0];\n" +
                        "                                    var delimiter  = '||'; " +
                        "                                    var videoUrl = videoLinkElem.getAttribute('href');\n" +
                        "                                    post.videoUrl = videoUrl + delimiter + 'referer=' + '" + referer + "' ;\n" +
                        "                                post.parentId = '" + movie.getId() + "';\n" +
                        "                                    var titleElems = li.getElementsByTagName('resolution');\n" +
                        "                                     if(titleElems.length > 0){\n" +
                        "                                        var titleElem = titleElems[0];\n" +
                        "                                         post.title = titleElem.textContent;\n" +
                        "                                      }\n" +
                        "\n" +
                        "                                      post.description = desc;\n" +
                        "                                                                         post.state = '" + Movie.RESOLUTION_STATE + "' ;\n" +
                        "\n" +
                        "                                                                         // Clone 'movie' object\n" +
                        "                                                                         post.studio = '" + movie.getStudio() + "' ;\n" +
                        "                                                                         post.fetch = '" + movie.getFetch() + "' ;\n" +
                        "                                                                           post.parentId = '" + movie.getId() + "';\n" +
                        "                                                                         post.cardImageUrl = '" + movie.getStudio() + "' ;\n" +
                        "                                                                         post.backgroundImageUrl = '" + movie.getBackgroundImageUrl() + "' ;\n" +
                        "                                                                         post.cardImageUrl = '" + movie.getCardImageUrl() + "' ;\n" +
                        "                                                                         post.getMainMovieTitle = '" + movie.getMainMovieTitle() + "' ;\n" +
                        "                                                                         postList.push(post);\n" +
                        "                                 }\n" +
                        "                                }\n" +
                        "                             }\n" +
                        "}\n" +
                        "\n" +
                        "                        // watch links\n" +
                        "var ulsWatch = document.getElementsByClassName('WatchServersList');\n" +
                        "console.log('ulsWatch: '+ulsWatch.length);" +
                        "console.log('title: '+ document.title);" +
                        "if(ulsWatch.length > 0){\n" +
                        "   var boxWatch = ulsWatch[0];\n" +
                        "       var lisWatch = boxWatch.getElementsByTagName('btn');\n" +
                        "        if(lisWatch.length > 0){\n" +
                        "                                for (let l = 0; l < lisWatch.length; l++) {\n" +
                        "                                   var li = lisWatch[l];\n" +
                        "                                   var post = {};\n" +
                        "                                    var videoUrl = li.getAttribute('data-url');\n" +
                        "\n" +
                        "                                    if(videoUrl === \"\"){\n" +
                        "                                    continue;\n" +
                        "                                    }\n" +
//                        "                                    // todo: handle if the link already has headers like ?key\n" +

                        "                                    var delimiter  = '||'; " +
                        "                                    post.videoUrl = videoUrl + delimiter + 'referer=' + '" + referer + "' ;\n" +
                        "                                    var titleElems = li.getElementsByTagName('strong');\n" +
                        "                                     if(titleElems.length > 0){\n" +
                        "                                        var titleElem = titleElems[0];\n" +
                        "                                         post.title = titleElem.textContent;\n" +
                        "                                      }\n" +
                        "\n" +
                        "                                      post.description = desc;\n" +
                        "                                                                         post.state = '" + Movie.BROWSER_STATE + "' ;\n" +
                        "\n" +
                        "                                                                         // Clone 'movie' object\n" +
                        "                                                                         post.studio = '" + movie.getStudio() + "' ;\n" +
                        "                                                                         post.fetch = '" + movie.getFetch() + "' ;\n" +
                        "                                                                           post.parentId = '" + movie.getId() + "';\n" +
                        "                                                                         post.cardImageUrl = '" + movie.getCardImageUrl() + "' ;\n" +
                        "                                                                         post.backgroundImageUrl = '" + movie.getBackgroundImageUrl() + "' ;\n" +
                        "                                                                         post.cardImageUrl = '" + movie.getCardImageUrl() + "' ;\n" +
                        "                                                                         post.getMainMovieTitle = '" + movie.getMainMovieTitle() + "' ;\n" +
                        "                                                                         postList.push(post);\n" +
                        "                                }\n" +
                        "                             }\n" +
                        "}\n" +
                        "MyJavaScriptInterface.myMethod(JSON.stringify(postList));\n" +
                        "}\n" +
                        " });";
            }


        }
        Log.d(TAG, "getWebScript: "+script);
        return script;
    }


    private void handleJSWebResult__(Activity activity, Movie movie, String jsResult, ActivityCallback<Movie> activityCallback) {
        Log.d(TAG, "handleJSWebResult - " + movie);
        if (jsResult != null && jsResult.startsWith("<html>")) {

            Document doc = Jsoup.parse(jsResult);

            Movie newMovie = generateGroupOfGroupMovie(doc, movie, activityCallback).movie;

            Log.d(TAG, "handleJSWebResult: sublist; " + movie.getSubList().size());
            Intent intent = new Intent();
            intent.putExtra(DetailsActivity.MOVIE, (Serializable) newMovie);
            if (newMovie != null && newMovie.getSubList() != null) {
//                Gson gson = new Gson();
//                String movieJson = gson.toJson(newMovie.getSubList());
                intent.putExtra(DetailsActivity.MOVIE_SUBLIST, (Serializable) newMovie.getSubList());
            }
//                                intent.putExtra(DetailsActivity.MOVIE_SUBLIST, (Serializable) movieJson);


            activity.setResult(Activity.RESULT_OK, intent);
            Log.d(TAG, "handleJSWebResult - 2");
            activity.finish();
        }
    }

    public MovieFetchProcess handleOnActivityResultHtml(String html, Movie m, ActivityCallback<Movie> activityCallback) {
        if (html == null) {
            return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, m);
        }
        Document doc = Jsoup.parse(html);
        Log.d(TAG, "handleOnActivityResultHtml: s: ");
        Log.d(TAG, "handleJSWebResult: sublistm ; " + m.getSubList().size());
        if (m == null) {
            m = new Movie();
            m.setSubList(new ArrayList<>());
        }
        if (m.getState() == Movie.GROUP_OF_GROUP_STATE) {
            return generateGroupOfGroupMovie(doc, m, activityCallback);
        } else if (m.getState() == Movie.GROUP_STATE) {
            return generateGroupMovie(doc, m, activityCallback);
        } else if (m.getState() == Movie.ITEM_STATE) {
            return generateItemMovie(doc, m, activityCallback);
        }
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, m);
    }

    private MovieFetchProcess generateItemMovie(Document doc, Movie movie, ActivityCallback<Movie> activityCallback) {

        String url = movie.getVideoUrl();
        if (!url.startsWith("http")){
            url = getConfig().getUrl() + url;
        }
        //get link of episodes page
        Element descElem = doc.getElementsByClass("StoryMovieContent").first();
        if (descElem == null) {
            descElem = doc.select("[class*=story][class*=content]").first();
        }
        if (descElem == null) {
            descElem = doc.select("[class*=description][class*=content]").first();
        }
        String desc = "";
        if (descElem != null) {
            desc = descElem.text();
            movie.setDescription(desc);
        }
        String referer = Util.extractDomain(url, true, true);
        Elements uls = doc.getElementsByClass("List--Download--Wecima--Single");

//        Log.d(TAG, "generateItemMovie: description: "+desc);
        if (uls.isEmpty()) {
            uls = doc.select("[class*=downloads][class*=list][class*=Download][class*=List]");
        }
//        Log.d(TAG, "generateItemMovie: html: " + doc.html());
//        Log.d(TAG, "generateItemMovie: title: " + doc.title());
//        Log.d(TAG, "generateItemMovie: download uls: " + uls.size());
        for (Element ul : uls) {
            Elements lis = ul.getElementsByTag("li");
//            Log.d(TAG, "generateItemMovie: download lis: " + lis.size());

            for (Element li : lis) {
                Element videoUrlElem = li.getElementsByAttribute("href").first();
                if (videoUrlElem == null){
                    videoUrlElem = li.getElementsByAttribute("data-href").first();
                }
                if (videoUrlElem != null) {
                    String videoUrl = videoUrlElem.attr("href");
                    if (videoUrl.isEmpty()){
                        videoUrl = videoUrlElem.attr("data-href");
                    }
                    if (videoUrl.isEmpty()){
                        Log.d(TAG, "generateItemMovie: fail finding download url ");
                        continue;
                    }
//                    Log.d(TAG, "generateItemMovie:videoUrl: "+videoUrl);
                        if (!videoUrl.startsWith("http")){
                            // Your encoded string from the data-url attribute
// Decode the Base64 string into a byte array
// The NO_WRAP flag ensures no extra line breaks are added
                            try {
                                // Step 1: Remove all plus '+' characters from the string.
//                                String cleanedUrl = videoUrl.replace("+", "");
//
//                                // Step 2: Prepend the Base64 string for "http", which is "aHR0c".
//                                String finalUrlString = "aHR0c" + cleanedUrl;

                                // Step 3: Perform the standard Base64 decoding on the corrected string.
                                // Using Base64.DEFAULT is robust and handles padding.
                                byte[] decodedBytes = Base64.decode(videoUrl, Base64.DEFAULT);

                                // Step 4: Convert the byte array to a UTF-8 string to get the final URL.
                                videoUrl = new String(decodedBytes, StandardCharsets.UTF_8);

                                // Now, the 'videoUrl' variable holds the clean, usable URL.
                                // You can log it to verify:
//                                Log.d(TAG, "Successfully decoded URL: " + videoUrl);

                            } catch (Exception e) {
                                // This will catch any errors if the string is still malformed.
                                Log.e(TAG, "generateItemMovie: Error decoding URL: " + e.getMessage());
                            }
                            // Now 'decodedUrl' holds the final, readable URL
//                     System.out.println(videoUrl);
//                    videoUrl = Util.extractDomain(movie.getVideoUrl(), true, true) + videoUrl;
                        }
//                    Log.d(TAG, "generateItemMovie:videoUrl: "+videoUrl);
                    Element titleElem = li.getElementsByTag("resolution").first();
                    String title = movie.getTitle();
                    if (titleElem != null) {
                        title = titleElem.text();
                    }

                    Movie episode = Movie.clone(movie);
                    episode.setTitle(title);
                    episode.setParentId(movie.getId());
                    episode.setDescription(desc);
                    if (!getConfig().getHeaders().containsKey("referer")) {
                        getConfig().getHeaders().put("referer", referer);
                    }
                    episode.setVideoUrl(videoUrl + Util.generateHeadersForVideoUrl(getConfig().getHeaders()));
                    episode.setState(Movie.RESOLUTION_STATE);
                    episode.setType(MovieType.RESOLUTION);
                    if (movie.getSubList() == null) {
                        movie.setSubList(new ArrayList<>());
                    }
                    movie.addSubList(episode);
                }
            }
            break;
        }

        uls = doc.getElementsByClass("WatchServersList");
        if (uls.isEmpty()) {
            uls = doc.select("[class*=watch][class*=list]");
        }
        for (Element ul : uls) {
            Elements lis = ul.getElementsByAttribute("data-url");
            for (Element li : lis) {
                String videoUrl = li.attr("data-url");

                if (videoUrl == null || videoUrl.equals("")) {
                    continue;
                }
                if (!videoUrl.startsWith("http")){
                    // Your encoded string from the data-url attribute
// Decode the Base64 string into a byte array
// The NO_WRAP flag ensures no extra line breaks are added
                    try {
                        // Step 1: Remove all plus '+' characters from the string.
//                        String cleanedUrl = videoUrl.replace("+", "");
//
//                        // Step 2: Prepend the Base64 string for "http", which is "aHR0c".
//                        String finalUrlString = "aHR0c" + cleanedUrl;

                        // Step 3: Perform the standard Base64 decoding on the corrected string.
                        // Using Base64.DEFAULT is robust and handles padding.
                        byte[] decodedBytes = Base64.decode(videoUrl, Base64.DEFAULT);

                        // Step 4: Convert the byte array to a UTF-8 string to get the final URL.
                        videoUrl = new String(decodedBytes, StandardCharsets.UTF_8);

                        // Now, the 'videoUrl' variable holds the clean, usable URL.
                        // You can log it to verify:
//                        Log.d(TAG, "Successfully watch decoded URL: " + videoUrl);

                    } catch (Exception e) {
                        // This will catch any errors if the string is still malformed.
                        Log.e(TAG, "generateItemMovie: Error decoding URL: " + e.getMessage());
                    }
                    // Now 'decodedUrl' holds the final, readable URL
//                     System.out.println(videoUrl);
//                    videoUrl = Util.extractDomain(movie.getVideoUrl(), true, true) + videoUrl;
                }
                videoUrl = videoUrl + "|referer=" + referer;
//                Log.d(TAG, "generateItemMovie: "+ videoUrl);
//                videoUrl = videoUrl + Util.generateHeadersForVideoUrl(headers);

                Element titleElem = li.getElementsByTag("strong").first();
                String title = movie.getTitle();
                if (titleElem != null) {
                    title = titleElem.text();
                }

//                        Movie a = new Movie();
//
//                        a.setState(Movie.BROWSER_STATE);
//
//                        a.setStudio(Movie.SERVER_MyCima);

                Movie episode = Movie.clone(movie);
                episode.setTitle(title);
                episode.setParentId(movie.getId());
                episode.setDescription(desc);
                episode.setVideoUrl(videoUrl);
                episode.setState(Movie.BROWSER_STATE);
                episode.setType(MovieType.BROWSER);
                if (movie.getSubList() == null) {
                    movie.setSubList(new ArrayList<>());
                }
                movie.addSubList(episode);
            }
//            Log.d(TAG, "generateItemMovie: " + movie);
            break;
        }
        activityCallback.onSuccess(movie, getLabel());
        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_SUCCESS, movie);
    }

    @Override
    public ArrayList<Movie> getHomepageMovies(boolean handleCookie, ActivityCallback<ArrayList<Movie>> activityCallback) {
//        return search("la casa", activityCallback, handleCookie);
//        return search("اسر", activityCallback,handleCookie);
//        return search("ratched");
        return search(getConfig().getUrl() + "/movies/", activityCallback, handleCookie);
//        return search(getConfig().getUrl() + "/category/أفلام/", activityCallback, handleCookie);
//   hhhhh     return search(getConfig().getUrl() + "/", activityCallback);
//        return search(config.url + "/category/%d9%85%d8%b3%d9%84%d8%b3%d9%84%d8%a7%d8%aa/%d9%85%d8%b3%d9%84%d8%b3%d9%84%d8%a7%d8%aa-%d8%b1%d9%85%d8%b6%d8%a7%d9%86-2024/list/");
//        return search(config.url);
    }

    public String getCustomUserAgent(int state) {
        return "Android 7";
    }
}
