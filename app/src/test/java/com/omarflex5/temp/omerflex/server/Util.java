package com.omarflex5.temp.omerflex.server;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;
import com.omerflex.entity.Movie;
import com.omerflex.entity.dto.LinkHeadersDTO;
import com.omerflex.view.BrowserActivity;
import com.omerflex.view.DetailsActivity;
import com.omerflex.view.ExoplayerMediaPlayer;
import com.omerflex.view.mobile.MobileMovieDetailActivity;
import com.omerflex.view.mobile.MobileSearchResultActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {
    private static final String TAG = "Util";
    private static final Gson gson = new Gson();

    public static String extractDomain(String videoUrl, boolean withSchema, boolean endSlash) {
        String fullDomain = "";
        try {
            URL url = new URL(videoUrl);
            String protocol = url.getProtocol();
            String host = url.getHost();
            if (!withSchema) {
                return host;
            }
            String endPart = "";
            if (endSlash) {
                endPart = "/";
            }
            fullDomain = protocol + "://" + host + endPart;

        } catch (Exception e) {
            Log.d(TAG, "error: extractDomain: " + e.getMessage());
        }
        Log.d(TAG, "extractDomain: " + fullDomain);
        return fullDomain;
    }

    public static String getUrlPathOnly(String url) {
        try {
            URI uri = new URI(url);
            // Get the path part of the URL without the domain
            String path = uri.getPath();
            return path;
        } catch (URISyntaxException e) {
            // Handle invalid URI syntax
            e.printStackTrace();
            return null;
        }
    }

    public static String urlExtractor(String videoUrl) {
        String baseUrl = null;
        try {
            // Create a URI object from the videoUrl string
            URI uri = new URI(videoUrl);

            // Extract the scheme (e.g., "https") and authority (e.g., "www.faselhd.express") from the URI
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();

            // Combine scheme and authority to form the base URL
            baseUrl = scheme + "://" + authority;

            // Print the base URL
            System.out.println("Base URL: " + baseUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return baseUrl;
    }

    public static Map<String, String> parseParamsToMap(String params) {
        params = params.substring(params.indexOf("||") + 2);
        Map<String, String> map = new HashMap<>();
        String[] pairs;
        if (params.contains("&")) {
            pairs = params.split("&");
        } else {
            pairs = new String[]{params};
        }
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            map.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return map;
    }

    public static String getValidReferer(String referer) {
        String result = referer;
        if (referer != null) {
            Pattern pattern = Pattern.compile("(https?://[^/]+)");
            Matcher matcher = pattern.matcher(referer);
            if (matcher.find()) {
                result = matcher.group(1);
            }
        }
        Log.d(TAG, "getValidReferer: " + result + ", " + referer);
        return result;
    }

    public static Map<String, String> getMapCookies(String cookies) {
        Map<String, String> cookiesHash = new HashMap<>();
        if (cookies != null) {
            //split the String by a comma
            String parts[] = cookies.split(";");

            //iterate the parts and add them to a map
            for (String part : parts) {

                //split the employee data by : to get id and name
                String empdata[] = part.split("=");

                String strId = empdata[0].trim();
                String strName = empdata[1].trim();
                Log.d(TAG, "getMapCookies: "+strId);
                //add to map
                cookiesHash.put(strId, strName);
            }

        }
        return cookiesHash;
    }

    public static String generateHeadersForVideoUrl(Map<String, String> headers) {
        String headerString = "";
        if (headers != null && !headers.isEmpty()) {
            try {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
//                                if (entry.getKey().equals("User-Agent")){
//                                    continue;
//                                }
                    sb.append(entry.getKey());
                    sb.append("=");
                    sb.append(entry.getValue());
                    sb.append("&");
                }
                // Remove the last "&" character
                sb.deleteCharAt(sb.length() - 1);
                headerString = sb.toString();
            } catch (Exception e) {
                Log.d(TAG, "onLoadResource: error building headers for the video: " + e.getMessage());
                return "";
            }

            if (!headerString.isEmpty()) {
                return "|" + headerString;
            }
        }
        return "";
    }

    public static Map<String, String> extractHeaders(String url) {
        Map<String, String> headersMap = new HashMap<>();
                String[] headers = url.split("&");
                for (String header : headers) {
                    String[] keyValue = header.split("=", 2);
                    if (keyValue.length == 2) {
                        Log.d(TAG, "extractHeaders: k: "+keyValue[0]+", v: "+ keyValue[1]);
                        headersMap.put(keyValue[0], keyValue[1]);
                    }
                }
        return headersMap;
    }

    public static LinkHeadersDTO splitLinkAndHeaders(String url) {
        LinkHeadersDTO linkHeaders = new LinkHeadersDTO();
        if (url.contains("||")) {
            linkHeaders.headers = Util.parseParamsToMap(url);
            linkHeaders.url = url.substring(0, url.indexOf("||"));

            Log.d(TAG, "browser: map:" + linkHeaders.toString() + ", url:" + url);
            return linkHeaders;
        }
        if (url.contains("|")) {
//                Map<String, String> map = parseParamsToMap(movie.getVideoUrl());
            String[] parts = url.split("\\|", 2);
            String cleanUrl = parts[0];
            Map<String, String> headers = new HashMap<>();

            if (parts.length == 2) {
                linkHeaders.headers = Util.extractHeaders(parts[1]);
                // Log.d("TAG", "buildMediaSource: h:" + parts[1]);
            }
            linkHeaders.url = cleanUrl;

            // Log.d(TAG, "browser: map:" + linkHeaders.toString() + ", url:" + cleanUrl);
            return linkHeaders;
        }
        // Log.d(TAG, "onCreate: url:" + url);
        linkHeaders.url = url;
        return linkHeaders;
    }

    public static String getIdFromUrl(String url) {
        if (url == null) return null;

        // Regex: look for '#' then capture consecutive digits
        Pattern pattern = Pattern.compile("#(\\d+)");
        Matcher matcher = pattern.matcher(url);

        if (matcher.find()) {
            return matcher.group(1); // the number after #
        }
        return null;
    }

                public static boolean shouldOverrideUrlLoading(String url) {

        boolean result =
//                url.contains("game") ||
                (url.contains("gamehub") && url.contains("post")) ||
                url.contains("gamezone") ||
                        url.contains("cim")
                        || url.contains("faselhd")
                        || url.contains("/sharon")
                        || url.contains("/d000")
                        || url.contains("/dooo")
                        || url.contains("fas")
                        || url.contains("cem")
                        || url.contains("akwam")
                        || url.contains("club")
                        || url.contains("clup")
                        || url.contains("ciima")
                        || url.contains("challenge")
                        || url.contains("akoam")
                        || url.contains("shahed")
                        || url.contains("arab")
                        || url.contains("seed")
                        || url.contains("review")
                        || url.contains("tech")
                        || url.contains("youtube.com")
                        || url.startsWith("##")
                        || url.contains("shahid");
        return !result;
    }

    public static void openMobileDetailsIntent(Movie movie, Activity activity, boolean withSubList) {
        Intent exoIntent = generateIntent(movie, new Intent(activity, MobileMovieDetailActivity.class), withSubList);
        Objects.requireNonNull(activity).startActivity(exoIntent);
    }

    public static void openMobileDetailsIntent(Movie movie, Fragment fragment, boolean withSubList) {
        Intent exoIntent = generateIntent(movie, new Intent(fragment.getActivity(), MobileMovieDetailActivity.class), withSubList);
        Objects.requireNonNull(fragment).startActivity(exoIntent);
    }

    public static void openBrowserIntent(Movie movie, Activity activity, boolean withSubList, boolean openForResult, boolean isCookieFetch) {
        Intent exoIntent = generateIntent(movie, new Intent(activity, BrowserActivity.class), withSubList);
        exoIntent.putExtra(Movie.KEY_IS_COOKIE_FETCH, isCookieFetch);
        if (openForResult) {
            exoIntent.putExtra("openedForResult", true);
            Objects.requireNonNull(activity).startActivityForResult(exoIntent, movie.getFetch());
            return;
        }
        exoIntent.putExtra("openedForResult", false);
        Objects.requireNonNull(activity).startActivity(exoIntent);
    }

    public static void openBrowserIntent(Movie movie, Activity activity, boolean withSubList, boolean openForResult, boolean isCookieFetch, int clickedRowId, int clickedMovieIndex) {
        Intent exoIntent = generateIntent(movie, new Intent(activity, BrowserActivity.class), withSubList);
        exoIntent.putExtra(Movie.KEY_IS_COOKIE_FETCH, isCookieFetch);
        exoIntent.putExtra(Movie.KEY_CLICKED_ROW_ID, clickedRowId);
        exoIntent.putExtra(Movie.KEY_CLICKED_MOVIE_INDEX, clickedMovieIndex);
        if (openForResult) {
            exoIntent.putExtra("openedForResult", true);
            Objects.requireNonNull(activity).startActivityForResult(exoIntent, movie.getFetch());
            return;
        }
        exoIntent.putExtra("openedForResult", false);
        Objects.requireNonNull(activity).startActivity(exoIntent);
    }

    public static void openBrowserIntent(Movie movie, Fragment fragment, boolean withSubList, boolean openForResult, boolean isCookieFetch, int clickedRowId, int clickedMovieIndex) {
        //todo to implement clickedRowId
        Intent exoIntent = generateIntent(movie, new Intent(fragment.getActivity(), BrowserActivity.class), withSubList);
        exoIntent.putExtra(Movie.KEY_IS_COOKIE_FETCH, isCookieFetch);
        exoIntent.putExtra(Movie.KEY_CLICKED_ROW_ID, clickedRowId);
        exoIntent.putExtra(Movie.KEY_CLICKED_MOVIE_INDEX, clickedMovieIndex);
        Log.d(TAG, "openBrowserIntent: clickedRowId:"+clickedRowId);
        Log.d(TAG, "openBrowserIntent: clickedMovieIndex:"+clickedMovieIndex);
        if (openForResult) {
            exoIntent.putExtra("openedForResult", true);
            fragment.startActivityForResult(exoIntent, movie.getFetch());
            return;
        }
        exoIntent.putExtra("openedForResult", false);
        fragment.startActivity(exoIntent);
    }

    public static void openBrowserIntent(Movie movie, Fragment fragment, boolean withSubList, boolean openForResult, boolean isCookieFetch) {
        //todo to implement clickedRowId
        Log.d(TAG, "openBrowserIntent: ");
        Intent exoIntent = generateIntent(movie, new Intent(fragment.getActivity(), BrowserActivity.class), withSubList);
        exoIntent.putExtra(Movie.KEY_IS_COOKIE_FETCH, isCookieFetch);
        if (openForResult) {
            exoIntent.putExtra("openedForResult", true);
            fragment.startActivityForResult(exoIntent, movie.getFetch());
            return;
        }
        exoIntent.putExtra("openedForResult", false);
        fragment.startActivity(exoIntent);
    }

    @NonNull
    public static Intent generateIntent(Movie movie, Intent intent, boolean withSubList) {
        intent.putExtra(DetailsActivity.MOVIE, (Parcelable) movie);
//        intent.putExtra(DetailsActivity.MAIN_MOVIE, (Parcelable) movie.getMainMovie());
        if (withSubList && movie.getSubList() != null) {
            intent.putParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST, (ArrayList<Movie>) movie.getSubList());  // Pass sublist as Parcelable ArrayList
        }
        return intent;
    }

    @NonNull
    public static String generateMaxPlayerHeaders(String url, Map<String, String> headers) {
        String headerString = "";
        if (headers != null && !headers.isEmpty()) {
            try {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
//                                if (entry.getKey().equals("User-Agent")){
//                                    continue;
//                                }
                    sb.append(entry.getKey());
                    sb.append("=");
                    sb.append(entry.getValue());
                    sb.append("&");
                }
                // Remove the last "&" character
                sb.deleteCharAt(sb.length() - 1);
                headerString = sb.toString();
            } catch (Exception e) {
                Log.d(TAG, "onLoadResource: error building headers for the video: " + e.getMessage());
            }
        }

        if (!headerString.isEmpty()) {
            return url + "|" + headerString;
        }
        return url;
    }

    public static void openExoPlayer(Movie movie, Activity activity, boolean withSubList) {
        Intent exoIntent = generateIntent(movie, new Intent(activity, ExoplayerMediaPlayer.class), withSubList);
        Objects.requireNonNull(activity).startActivity(exoIntent);
    }

    public static void showToastMessage(String message, Activity activity) {
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }


    public static Movie recieveSelectedMovie(Intent intent) {
        Movie movie = (Movie) intent.getParcelableExtra(DetailsActivity.MOVIE);

        if (movie == null) {
            movie = new Movie();
            movie.setTitle("حدث خطأ...");
            return movie;
        }


        Movie mSelectedMovieMainMovie = (Movie) intent.getParcelableExtra(DetailsActivity.MAIN_MOVIE);
        ArrayList<Movie> movieSublist = intent.getParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST);

//        String movieJson = intent.getStringExtra(DetailsActivity.MOVIE_SUBLIST);
////        Gson gson = new Gson();
//        Type type = new TypeToken<List<Movie>>() {
//        }.getType();
//        List<Movie> movieSublist = gson.fromJson(movieJson, type);
//        Log.d(TAG, "onCreate: subList:" + movieSublist);


        if (movieSublist != null) {
            movie.setSubList(movieSublist);
        }
        if (movie.getSubList() == null) {
            movie.setSubList(new ArrayList<>());
        }
//        movie.setMainMovie(mSelectedMovieMainMovie);
        return movie;
    }

//    private static MovieFetchProcess fetchMovieAtStart(Movie movie, Activity activity) {
//        AbstractServer server = ServerManager.determineServer(movie, null, activity, null);
//        if (server != null) {
//            return server.fetch(movie, movie.getState());
//        }
//        return new MovieFetchProcess(MovieFetchProcess.FETCH_PROCESS_ERROR_UNKNOWN, movie);
//    }

    public static void openExternalVideoPlayer(Movie movie, Activity activity) {
        if (movie != null && movie.getVideoUrl() != null) {
            // Uri uri = Uri.parse(res.getSubList().get(0).getVideoUrl());
//            Log.d(TAG, "onActionClicked: Resolutions " + res);

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String type = "video/*"; // It works for all video application
                    Log.d(TAG, "run: playing: "+ movie.getVideoUrl());
                    Uri uri = Uri.parse(movie.getVideoUrl());
                    Intent in1 = new Intent(Intent.ACTION_VIEW, uri);
                    in1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    //  in1.setPackage("org.videolan.vlc");
                    in1.setDataAndType(uri, type);

                    activity.startActivity(in1);
//                    updateItemFromActivityResult(movie);
//                    dbHelper.addMainMovieToHistory(mSelectedMovie);
                }
            });

        }
    }


    public static Intent generateIntentResult(Movie movie) {
        return generateIntent(movie, new Intent(), true);
    }

    public static Intent generateIntentResult(Movie movie, int clickedRowId, int clickedMovieIndex) {
        Intent exoIntent = new Intent();
        exoIntent.putExtra(Movie.KEY_CLICKED_ROW_ID, clickedRowId);
        exoIntent.putExtra(Movie.KEY_CLICKED_MOVIE_INDEX, clickedMovieIndex);
        return generateIntent(movie, exoIntent, true);
    }

    public static void openVideoDetailsIntent(Movie movie, Activity activity) {
        Log.d(TAG, "openVideoDetailsIntent: Util");
        Intent exoIntent = generateIntent(movie, new Intent(activity, DetailsActivity.class), false);
        Objects.requireNonNull(activity).startActivity(exoIntent);
    }

    public static void openVideoDetailsIntent(Movie movie, Fragment fragment) {
        Log.d(TAG, "openVideoDetailsIntent: Util");
        Intent exoIntent = generateIntent(movie, new Intent(fragment.getActivity(), DetailsActivity.class), false);
        Objects.requireNonNull(fragment).startActivity(exoIntent);
    }

    public static HashMap<String, String> convertJsonToHashMap(String jsonString) throws JSONException {
        HashMap<String, String> map = new HashMap<>();
        if (jsonString == null){
            return map;
        }
        JSONObject jsonObject = new JSONObject(jsonString);

        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, jsonObject.getString(key));
        }

        return map;  // Return the HashMap
    }

    public static void openSearchResultActivity(String query, Activity activity) {
        Intent intent = new Intent(activity, MobileSearchResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(DetailsActivity.QUERY, query);
        Objects.requireNonNull(activity).startActivity(intent);
    }
}
