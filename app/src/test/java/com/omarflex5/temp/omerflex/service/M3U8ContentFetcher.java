package com.omarflex5.temp.omerflex.service;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieRepository;
import com.omerflex.entity.dto.GoogleFile;
import com.omerflex.entity.dto.IptvSegmentDTO;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class M3U8ContentFetcher {

    static String TAG = "M3U8ContentFetcher";

    private static final String DEFAULT_CHARSET = "UTF-8";
    private static final int MAX_CONCURRENT_DB_THREADS = 2;

    // Singleton OkHttpClient
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ExecutorService dbExecutor =
            Executors.newFixedThreadPool(MAX_CONCURRENT_DB_THREADS);

    public static void fetchAndStoreM3U8Content(Movie iptvList,
                                                Consumer<HashMap<String, ArrayList<Movie>>> callback) {
        dbExecutor.execute(() -> {
            try {
                HashMap<String, ArrayList<Movie>> result = fetchAndProcessContent(iptvList);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(result));
                }
            } catch (Exception e) {
                Log.e(TAG, "Fetch failed", e);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.accept(new HashMap<>()));
                }
            }
        });
    }

    public static void fetchIpTvPlayList(Movie iptvList, ServerInterface.ActivityCallback<ArrayList<Movie>> callback) {
        dbExecutor.execute(() -> {
            try {
                Log.d(TAG, "fetchIpTvPlayList: ");
                HashMap<String, ArrayList<Movie>> parsedContent;
                String contents = fetchLinkContent(iptvList.getVideoUrl());
                if (contents == null) {
                    Log.d(TAG, "fetchIpTvPlayList: contents is null");
                    callback.onInvalidLink("contents is null: " + iptvList.getVideoUrl());
                    return;
                }
                String hash = String.valueOf(contents.hashCode());  // Convert long to String;
                // todo find a way to delete ole iptv movies if the movie is updated
                parsedContent = parseContentWithStreaming(contents, hash);
                Log.d(TAG, "fetchAndProcessContent: parsedContent: "+ parsedContent);
                Log.d(TAG, "fetchAndProcessContent: parsedContent: "+ parsedContent.size());
                // for key and value in parsedContent
                for (String key : parsedContent.keySet()) {
                    Log.d(TAG, "fetchAndProcessContent: key: "+ key);
                    callback.onSuccess(parsedContent.get(key), key);
                }
                return;
//                return parsedContent;

            } catch (Exception e) {
                Log.e(TAG, "Fetch failed", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onInvalidLink(e.getMessage()));
            }
        });
    }

    public static String fetchLinkContent(String link)
            throws IOException {
        Log.d(TAG, "fetchLinkContent: "+ link);
        Request request = new Request.Builder()
                .url(link)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "Empty response or unsuccessful request");
                return null;
            }

            //            String hash = calculateHash(body.getBytes(DEFAULT_CHARSET), "SHA-256");
//            String hash = String.valueOf(body.hashCode());  // Convert long to String;
            Log.d(TAG, "fetchLinkContent: success fetching the contents");
            return response.body().string();
        }
    }


    public static HashMap<String, ArrayList<Movie>> fetchAndProcessContent(Movie iptvList)
            throws IOException {
        Log.d(TAG, "fetchAndProcessContent: "+ iptvList.getVideoUrl());
        Request request = new Request.Builder()
                .url(iptvList.getVideoUrl())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "Empty response or unsuccessful request");
                return new HashMap<>();
            }

            String body = response.body().string();
//            String hash = calculateHash(body.getBytes(DEFAULT_CHARSET), "SHA-256");
            String hash = String.valueOf(body.hashCode());  // Convert long to String;

            HashMap<String, ArrayList<Movie>> parsedContent = parseContentWithStreaming(body, hash);
            Log.d(TAG, "fetchAndProcessContent: parsedContent: "+ parsedContent.size());
            return parsedContent;
        }
    }


//    private static void persistContent(HashMap<String, ArrayList<Movie>> content, MovieDbHelper dbHelper) {
//        ArrayList<Movie> allMovies = new ArrayList<>();
//        for (ArrayList<Movie> group : content.values()) {
//            allMovies.addAll(group);
//        }
//
////        dbExecutor.execute(() -> {
////            try {
////                dbHelper.beginTransaction();
////                dbHelper.bulkInsertMovies(allMovies);
////                dbHelper.setTransactionSuccessful();
////            } finally {
////                dbHelper.endTransaction();
////            }
////        });
//    }

    public static HashMap<String, ArrayList<Movie>> parseContentWithStreaming(String content, String hash) {
        HashMap<String, ArrayList<Movie>> groupedMovies = new LinkedHashMap<>();
        IptvSegmentDTO currentSegment = null;

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // ignore empty lines
                    continue;
                }
                line = line.trim();
                try {
                    if (line.startsWith("#EXTINF:")) {
                        // save old segment and

                        if (currentSegment != null && currentSegment.url != null) {
                            // save the current segmentDto
                            saveCurrentIptvSegment(groupedMovies, currentSegment, hash);
//                        addToGroup(groupedMovies, currentGroupTitle, currentChannel);
                        }
                        // manage new segment
//                        currentSegment = new IptvSegmentDTO();
                        currentSegment = parseCurrentIptvSegment(line);
                    }

                    // continue if now data filled in EXTINF
                    if (currentSegment == null){
                        continue;
                    }

                    currentSegment = parseCurrentIptvSegmentExtraInfo(line, currentSegment);
                }catch (Exception e){
                    System.out.println("Error: " +e.getMessage());
                }



//                else if (line.startsWith("#EXTGRP:")) {
//                    currentGroupTitle = parseGroupTitle(line);
//                }
//                else if (
//                        !line.startsWith("#") &&
//                         currentChannel != null &&
//                         line.startsWith("http")
//                ) {
//                    currentChannel.setVideoUrl(line);
//                    addToGroup(groupedMovies, currentGroupTitle, currentChannel);
//                    currentChannel = null;
//                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Stream parsing failed", e);
        }

        return groupedMovies;
    }

    public static ArrayList<Movie> parseContentWithStreamingToList(String content, String hash) {
        ArrayList<Movie> movies = new ArrayList<>();
        IptvSegmentDTO currentSegment = null;

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // ignore empty lines
                    continue;
                }
                line = line.trim();
                try {
                    if (line.startsWith("#EXTINF:")) {
                        // save old segment and

                        if (currentSegment != null && currentSegment.url != null) {
                            // save the current segmentDto
                            movies.add(saveCurrentIptvSegmentToList(currentSegment, hash));
//                        addToGroup(groupedMovies, currentGroupTitle, currentChannel);
                        }
                        // manage new segment
//                        currentSegment = new IptvSegmentDTO();
                        currentSegment = parseCurrentIptvSegment(line);
                    }

                    // continue if now data filled in EXTINF
                    if (currentSegment == null){
                        continue;
                    }

                    currentSegment = parseCurrentIptvSegmentExtraInfo(line, currentSegment);
                }catch (Exception e){
                    System.out.println("Error: " +e.getMessage());
                }



//                else if (line.startsWith("#EXTGRP:")) {
//                    currentGroupTitle = parseGroupTitle(line);
//                }
//                else if (
//                        !line.startsWith("#") &&
//                         currentChannel != null &&
//                         line.startsWith("http")
//                ) {
//                    currentChannel.setVideoUrl(line);
//                    addToGroup(groupedMovies, currentGroupTitle, currentChannel);
//                    currentChannel = null;
//                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Stream parsing failed", e);
        }

        return movies;
    }

    private static Movie saveCurrentIptvSegmentToList(IptvSegmentDTO currentSegment, String hash) {
        String groupTitle = currentSegment.groupTitle;
        if (groupTitle == null || groupTitle.isEmpty()) {
            groupTitle = currentSegment.id;
        }
        if (groupTitle == null) {
            groupTitle = "default";
        }

        Movie channel = new Movie();
        channel.setTitle(currentSegment.name);
        channel.setVideoUrl(currentSegment.url);
//            channel.setTvgName(segmentDTO.getTvgName());
        channel.setCardImageUrl(currentSegment.tvgLogo);
//            channel.setFileName(segmentDTO.getFileName());
//            channel.setCredentialUrl(segmentDTO.getCredentialUrl());


//            if(groupTitle == null) {
//                movieGroupName = "undefined";
//                channel.setTitle(movieGroupName +"-"+ movieNameCounter++);
//            }

        channel.setGroup(groupTitle);
        channel.setStudio(Movie.SERVER_IPTV);
        channel.setMainMovieTitle(hash);
        channel.setState(Movie.VIDEO_STATE);

//        Log.d(TAG, "saveCurrentIptvSegment: " + groupTitle + ": " + currentSegment);
//        System.out.println("group: " + groupTitle);
//        System.out.println("id: " + currentSegment.id);
//        System.out.println("name: " + currentSegment.name);
//        System.out.println("tvgName: " + currentSegment.tvgName);
//        System.out.println("gTitle: " + currentSegment.groupTitle);
//        System.out.println("tvgLogo: " + currentSegment.tvgLogo);
//        System.out.println("url: " + currentSegment.url);
//        System.out.println("headers: " + currentSegment.httpHeaders.toString());
//        System.out.println("=================================");
        return channel;
    }
    private static void saveCurrentIptvSegment(HashMap<String, ArrayList<Movie>> groupedMovies, IptvSegmentDTO currentSegment, String hash) {
        String groupTitle = currentSegment.groupTitle;
        if (groupTitle == null || groupTitle.isEmpty()) {
            groupTitle = currentSegment.id;
        }
        if (groupTitle == null) {
            groupTitle = "default";
        }

        if (!groupedMovies.containsKey(groupTitle)) {
            groupedMovies.put(groupTitle, new ArrayList<>());
        }

        Movie channel = new Movie();
        channel.setTitle(currentSegment.name);
        channel.setVideoUrl(currentSegment.url);
//            channel.setTvgName(segmentDTO.getTvgName());
        channel.setCardImageUrl(currentSegment.tvgLogo);
//            channel.setFileName(segmentDTO.getFileName());
//            channel.setCredentialUrl(segmentDTO.getCredentialUrl());


//            if(groupTitle == null) {
//                movieGroupName = "undefined";
//                channel.setTitle(movieGroupName +"-"+ movieNameCounter++);
//            }

        channel.setGroup(groupTitle);
        channel.setStudio(Movie.SERVER_IPTV);
        channel.setMainMovieTitle(hash);
        channel.setState(Movie.VIDEO_STATE);

//        Log.d(TAG, "saveCurrentIptvSegment: " + groupTitle + ": " + currentSegment);
//        System.out.println("group: " + groupTitle);
//        System.out.println("id: " + currentSegment.id);
//        System.out.println("name: " + currentSegment.name);
//        System.out.println("tvgName: " + currentSegment.tvgName);
//        System.out.println("gTitle: " + currentSegment.groupTitle);
//        System.out.println("tvgLogo: " + currentSegment.tvgLogo);
//        System.out.println("url: " + currentSegment.url);
//        System.out.println("headers: " + currentSegment.httpHeaders.toString());
//        System.out.println("=================================");
        ArrayList<Movie> segmentGroup = groupedMovies.get(groupTitle);
        if (segmentGroup != null) {
            segmentGroup.add(channel);
        }
    }

    private static IptvSegmentDTO parseCurrentIptvSegmentExtraInfo(String line, IptvSegmentDTO currentSegment) {

        // Check for VLC options
        if (line.startsWith("#EXTVLCOPT:")) {
            String referrer = null;
            String userAgent = "airmaxtv"; // Default user-agent
            if (line.contains("http-user-agent=")) {
                userAgent = extractVlcOpt(line, "http-user-agent");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    currentSegment.httpHeaders.replace("user-agent", userAgent);
                }
            } else if (line.contains("http-referrer=")) {
                referrer = extractVlcOpt(line, "http-referrer");
                currentSegment.httpHeaders.put("referrer", referrer);
            }
        }

        // extract url
        if (line.startsWith("http")) {
                currentSegment.url = Util.generateMaxPlayerHeaders(line, currentSegment.httpHeaders);
        }

        return currentSegment;
    }

    private static IptvSegmentDTO parseCurrentIptvSegment(String infoLine) {
        // Extract attributes from the infoLine

        String tvgId = extractAttribute(infoLine, "tvg-id");
        String tvgName = extractAttribute(infoLine, "tvg-name");
        String tvgLogo = extractAttribute(infoLine, "tvg-logo");
        String groupTitle = extractAttribute(infoLine, "group-title");
        String name = infoLine != null ? infoLine.substring(infoLine.lastIndexOf(",") + 1).trim() : "";

        // Create the DTO and populate it
        IptvSegmentDTO segmentDTO = new IptvSegmentDTO();
//        segmentDTO.httpHeaders = new HashMap<>();
        segmentDTO.httpHeaders.put("user-agent", "airmaxtv");

        segmentDTO.id = (tvgId);
        segmentDTO.tvgName = tvgName;
        segmentDTO.tvgLogo = tvgLogo;
        segmentDTO.groupTitle = groupTitle;
        segmentDTO.name = name;
//        segmentDTO.url = url;
//            segmentDTO.setFileName(fileName);
//            segmentDTO.setCredentialUrl(credentialUrl);

        return segmentDTO;
    }


    private static Movie parseExtInfLine(String extInfLine, String hash) {
        // Existing parsing logic from generateSegmentDTO
        // Return Movie object with parsed metadata

        return new Movie();
    }


    private static String parseGroupTitle(String groupLine) {
        return groupLine.replaceFirst("#EXTGRP:", "").trim();
    }

    private static void addToGroup(HashMap<String, ArrayList<Movie>> groups,
                                   String groupTitle, Movie channel) {
        if (!groups.containsKey(groupTitle)) {
            groups.put(groupTitle, new ArrayList<>());
        }
        groups.get(groupTitle).add(channel);
    }


    private static String calculateHash(byte[] data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unsupported hash algorithm", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }


//77777777777777777777

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static CompletableFuture<List<Movie>> fetchDriveFilesAsync(String m3u8Url) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        return CompletableFuture.supplyAsync(() -> {
//            try {
//                List<Movie> tempList = fetchDriveFiles(m3u8Url);
                List<Movie> tempList = new ArrayList<>();
                return tempList;
//            } catch (IOException e) {
//                e.printStackTrace();
//                return new ArrayList<>();
//            }
        }, executor);
    }

    public static void fetchDriveFiles(String folderUrl, ServerInterface.ActivityCallback<ArrayList<Movie>> activityCallback) throws IOException {
        Log.d(TAG, "fetchDriveFiles: "+folderUrl);
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(folderUrl)
                .build();
        ArrayList<Movie> movieList = new ArrayList<>();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                // Read response.body().string() to get the response content
                String body = response.body().string();
                //  Log.d("TAG", "fetchM3U8Content: body:"+body);
                //System.out.print(body);
                String regex = "\\\\x22(.[^\\\\]+)\\\\x22,\\\\";
                Pattern mainPattern = Pattern.compile(regex, Pattern.MULTILINE);
                // Pattern mainPattern = Pattern.compile("#EXT(?:INF)?(?::-1)?(?:,)?([^#]+)");
                Matcher mainMatcher = mainPattern.matcher(body);

                List<GoogleFile> playlist = new ArrayList<>();

                while (mainMatcher.find()) {
                    String element = mainMatcher.group(0);
                    if (element.contains("\\x22,\\")) {
                        element = element.replace("\\x22,\\", "");
                        if (element.contains("\\x22")) {
                            element = element.replace("\\x22", "");
                        }
                    }
                    // Log.d("TAG", "fetchM3U8Content: list json:xxx: "+element);
                    if (playlist.isEmpty()) {
                        GoogleFile file = new GoogleFile();
                        file.id = element;
                        file.name = null;
                        playlist.add(file);
                    } else {
                        int lastFileKey = playlist.size() - 1;
                        GoogleFile lastFile = playlist.get(lastFileKey);
                        if (lastFile.name == null) {
                            lastFile.name = element;
                            lastFile.link = "https://drive.google.com/u/0/uc?id=" + lastFile.id + "&export=download";
                            // lastFile.link = "https://docs.google.com/document/d/"+lastFile.id+"/edit";
                        } else {
                            GoogleFile file = new GoogleFile();
                            file.id = element;
                            file.name = null;
                            playlist.add(file);
                        }
                    }
                }
                // Log.d("TAG", "fetchDriveFiles: list files: "+playlist.toString());
                String movieLogo = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";

                for (GoogleFile file : playlist) {
                    Movie movie = new Movie();
                    movie.setTitle(file.name);
                    movie.setVideoUrl(file.link);
                    movie.setCardImageUrl(movieLogo);
                    movie.setGroup("google");
                    movie.setStudio(Movie.SERVER_IPTV);
                    movie.setState(Movie.IPTV_PLAY_LIST_STATE);
                    movieList.add(movie);
                }
                activityCallback.onSuccess(movieList, "google");
                return;
            }else {
                Log.d(TAG, "fetchDriveFiles: error:"+ response.body());
            }
        } catch (IOException e) {
            Log.d(TAG, "fetchDriveFiles: error: "+ e.getMessage());
            e.printStackTrace();
        }
        Log.d("TAG", "fetchDriveFiles: " + movieList);
        activityCallback.onInvalidLink("empty list");
    }


    public static HashMap<String, ArrayList<Movie>> parseGroupNames(String m3u8Content, String hash) {
//        Log.d(TAG, "parseGroupNames: ");
        // HashMap to store movies grouped by their group names
        HashMap<String, ArrayList<Movie>> groupedMovies = new HashMap<>();
        int movieNameCounter = 0;

        // Split content into segments using the delimiter "#EXTINF:"
        String[] segments = m3u8Content.split("#EXTINF:");
        // Remove the first empty element
        List<String> segmentList = new ArrayList<>(Arrays.asList(segments));
        segmentList.remove(0);
        ArrayList<String> urlList = new ArrayList<>();

        // Loop through the segments
        for (int index = 0; index < segmentList.size(); index++) {
            String segment = segmentList.get(index);

            IptvSegmentDTO segmentDTO = generateSegmentDTO(segment); // Generate the DTO object

            if (segmentDTO == null) {
                Log.e("parseAndSave", "Error parsing segment: " + segment);
                continue;
            }

            // Skip if the logo is a specific one
            if ("https://bit.ly/3JQfa8u".equals(segmentDTO.tvgLogo)) {
                continue;
            }
            // skip duplicated link
            if (urlList.contains(segmentDTO.url)) {
                Log.d(TAG, "parseGroupNames: duplicated link: " + segmentDTO.url);
                continue;
            }
            urlList.add(segmentDTO.url);
//            Log.d(TAG, "parseGroupNames: "+segmentDTO);

            Movie channel = new Movie();
            channel.setTitle(segmentDTO.name);
            channel.setVideoUrl(segmentDTO.url);
//            channel.setTvgName(segmentDTO.getTvgName());
            channel.setCardImageUrl(segmentDTO.tvgLogo);
//            channel.setFileName(segmentDTO.getFileName());
//            channel.setCredentialUrl(segmentDTO.getCredentialUrl());

            String groupTitle = segmentDTO.groupTitle;
            if (groupTitle == null || groupTitle.isEmpty()) {
                Log.e("parseAndSave", "GroupTitle is missing for channel: " + segment);
                groupTitle = segmentDTO.id;
            }

//            if(groupTitle == null) {
//                movieGroupName = "undefined";
//                channel.setTitle(movieGroupName +"-"+ movieNameCounter++);
//            }

            channel.setGroup(groupTitle);
            channel.setStudio(Movie.SERVER_IPTV);
            channel.setMainMovieTitle(hash);
            channel.setState(Movie.VIDEO_STATE);
//            Log.d(TAG, "parseGroupNames: "+channel);
            // Check if the group already exists in the map
            if (!groupedMovies.containsKey(groupTitle)) {
                groupedMovies.put(groupTitle, new ArrayList<>());
            }

            // Add the movie to the corresponding group
            groupedMovies.get(groupTitle).add(channel);

//            try {
//                if (channel.getUrl().length() > 1500) {
//                    throw new IllegalArgumentException("URL length exceeds limit");
//                }
//
//                // Persist channel in your database (SQLite, Room, etc.)
//                saveChannel(channel); // This should be your custom method for saving the channel
//
//            } catch (Exception e) {
//                outputCallback.onOutput(false, channel);
//            }

        }
//
//        try {
//            // Save all changes (if using transactions or bulk inserts)
//            // commitTransaction(); // Implement this as per your persistence logic
//        } catch (Exception e) {
//            Log.e("parseAndSave", "Error saving to database", e);
//        }

        return groupedMovies;
    }

    // Method to extract a VLC option from a line in Android Java
    private static String extractVlcOpt(String line, String option) {
        // Create the regex pattern to find the option and everything after "="
        String pattern = option + "=(.*)";

        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(line);

        // Return the matched group if found, otherwise return null
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static IptvSegmentDTO generateSegmentDTO(String segment) {
        try {
            List<String> processedUrls = new ArrayList<>();

            // Split the segment by new lines
            String[] linesArray = segment.split("\n");
            List<String> lines = Arrays.asList(linesArray);

            // Step 2: Trim each line and remove any empty ones
            List<String> cleanedLines = new ArrayList<>();
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    cleanedLines.add(line);
                }
            }

            // Pop the first element of the lines
            String infoLine = cleanedLines.isEmpty() ? null : cleanedLines.remove(0);

            String url = null;
//            Log.d(TAG, "generateSegmentDTO:cleanedLines: "+cleanedLines);
            if (!cleanedLines.isEmpty()) {
                // Extract the URL
                url = extractUrl(cleanedLines);
            }
//            Log.d(TAG, "generateSegmentDTO: url: "+url);
            if (url == null) {
                return null;  // Exit if no URL is found
            }

//            String fileName = null;
//            String credentialUrl = null;
//
////            try {
////                URL parsedUrl = new URL(url);
////                String host = parsedUrl.getHost();
////
////                if (host != null && host.contains("airmax")) {
////                    String[] pathParts = parsedUrl.getPath().split("/");
////                    String domain = parsedUrl.getProtocol() + "://" + host + (parsedUrl.getPort() != -1 ? ":" + parsedUrl.getPort() : "") + "/";
////                    String username = pathParts.length > 0 ? pathParts[0] : "";
////                    String password = pathParts.length > 1 ? pathParts[1] : "";
////                    fileName = pathParts.length > 0 ? pathParts[pathParts.length - 1] : "";
////
////                    // Extract part after the last occurrence of '|'
////                    if (fileName.contains("|")) {
////                        fileName = fileName.split("\\|")[0];
////                    }
////                    credentialUrl = domain + username + "/" + password + "/";
////                }
////            } catch (MalformedURLException e) {
////                e.printStackTrace();
////                return null;  // Return null in case of URL parsing error
////            }

            // Extract attributes from the infoLine

            String tvgId = extractAttribute(infoLine, "tvg-id");
            String tvgName = extractAttribute(infoLine, "tvg-name");
            String tvgLogo = extractAttribute(infoLine, "tvg-logo");
            String groupTitle = extractAttribute(infoLine, "group-title");
            String name = infoLine != null ? infoLine.substring(infoLine.lastIndexOf(",") + 1).trim() : "";

            // Create the DTO and populate it
            IptvSegmentDTO segmentDTO = new IptvSegmentDTO();
            segmentDTO.id = (tvgId);
            segmentDTO.tvgName = tvgName;
            segmentDTO.tvgLogo = tvgLogo;
            segmentDTO.groupTitle = groupTitle;
            segmentDTO.name = name;
            segmentDTO.url = url;
//            segmentDTO.setFileName(fileName);
//            segmentDTO.setCredentialUrl(credentialUrl);

            return segmentDTO;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String extractUrl(List<String> lines) {
        String referrer = null;
        String userAgent = "airmaxtv"; // Default user-agent
        String url = null;

        // Iterate through each line in the list
        for (String line : lines) {
            line = line.trim(); // Trim the line

            // If the line starts with "http", consider it as the URL
            if (line.startsWith("http")) {
                url = line;
                continue;
            }

            // Check for VLC options
            if (line.startsWith("#EXTVLCOPT:")) {
                if (line.contains("http-user-agent=")) {
                    userAgent = extractVlcOpt(line, "http-user-agent");
                } else if (line.contains("http-referrer=")) {
                    referrer = extractVlcOpt(line, "http-referrer");
                }
            }
        }

        // If no URL was found, use the last line as a fallback
        if (url == null && !lines.isEmpty()) {
            url = lines.get(lines.size() - 1).trim(); // Get the last line

            if (!url.startsWith("#http")) {
                return null;
            }

            // Replace "#http" with "http" in the URL
            url = url.replace("#http", "http");
        }

        // Append user-agent to the URL
        if (url != null) {
            url += "|user-agent=" + userAgent;

            // If a referrer is found, append it to the URL as well
            if (referrer != null) {
                url += "&referrer=" + referrer;
            }
        }

        return url;
    }

    // Method to extract an attribute from a line in Android Java
    private static String extractAttribute(String line, String attribute) {
        // Modify the regular expression to stop at " or ,
        String pattern = attribute + "=\"([^\"]*)\"";

        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(line);

        // Return the matched group if found, otherwise return null
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public void fetchDefaultChannels(String mainIptvPlaylistUrl, MovieRepository.MovieListCallback callback) {
        Log.d(TAG, "fetchDefaultChannels: " + mainIptvPlaylistUrl);
        dbExecutor.execute(() -> {
            try {
                Log.d(TAG, "fetchIpTvPlayList: ");
                ArrayList<Movie> parsedContent;
                String contents = fetchLinkContent(mainIptvPlaylistUrl);
                if (contents == null) {
                    Log.d(TAG, "fetchIpTvPlayList: error: contents is null");
                    callback.onMovieListFetched("Default", new ArrayList<>());
                    return;
                }
                String hash = String.valueOf(contents.hashCode());  // Convert long to String;
                // todo find a way to delete ole iptv movies if the movie is updated
                parsedContent = parseContentWithStreamingToList(contents, hash);
                Log.d(TAG, "fetchAndProcessContent: parsedContent: "+ parsedContent.size());
                // for key and value in parsedContent

                callback.onMovieListFetched("Default", parsedContent);

                return;
//                return parsedContent;

            } catch (Exception e) {
                Log.e(TAG, "error: Fetch failed", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onMovieListFetched(e.getMessage(), new ArrayList<>()));
            }
        });
    }
}