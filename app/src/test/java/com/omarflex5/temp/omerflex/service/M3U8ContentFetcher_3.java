package com.omarflex5.temp.omerflex.service;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.omerflex.entity.Movie;
import com.omerflex.entity.dto.GoogleFile;
import com.omerflex.entity.dto.IptvSegmentDTO;
import com.omerflex.service.database.MovieDbHelper;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.ByteString;

public class M3U8ContentFetcher_3 {

    static String TAG = "M3U8ContentFetcher";
    MovieDbHelper dbHelper;
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void main(String[] args) {
       // String m3u8Url = "https://github.com/Free-TV/IPTV/blob/master/playlist.m3u8";
        String m3u8Url = "https://iptv-list.live/download/421/12-16-22-iptv-list-1.m3u";
        Movie iptvList = new Movie();
        iptvList.setTitle("iptvList");
        iptvList.setVideoUrl(m3u8Url);
//
//        HashMap<String, List<Movie>> futureMovieList = fetchM3U8ContentAsync(iptvList, null);
//if (futureMovieList.isEmpty()){
//    return;
//}
//            System.out.println("Movie List Size: " + futureMovieList.size());
//            for (Movie movie : futureMovieList) {
//                Log.d("TAG", "main: " + movie.toString());
//            }
    }

    public static HashMap<String, ArrayList<Movie>> fetchM3U8ContentAsync(Movie iptvList, MovieDbHelper dbHelper) {
            try {
                return fetchM3U8Content(iptvList, dbHelper);
            } catch (IOException e) {
                Log.d(TAG, "fetchM3U8ContentAsync: error: "+ e.getMessage());
                return new HashMap<String, ArrayList<Movie>>();
            }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static CompletableFuture<List<Movie>> fetchDriveFilesAsync(String m3u8Url) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Movie> tempList = fetchDriveFiles(m3u8Url);
                return tempList;
            } catch (IOException e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        }, executor);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static CompletableFuture<List<Movie>> fetchJsonFilesAsync(String m3u8Url) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Movie> tempList = fetchJsonFiles(m3u8Url);
                return tempList;
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("TAG", "fetchJsonFiles: error:"+e.getMessage());
                return new ArrayList<>();
            }
        }, executor);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static CompletableFuture<String> getMovieUrl(Movie movie) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        return CompletableFuture.supplyAsync(() -> {
            String url = movie.getVideoUrl();
            if (url.contains(".m3u") ||
                    url.contains(".mp4") ||
                    url.contains(".avi") ||
                    url.contains(".mkv") ||
                    url.contains(".ts")
            ){
                return url;
            }
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    // Read response.body().string() to get the response content
                    String body = response.request().url().toString();
                    url = body;
                    // System.out.print(body);
                   // Log.d("TAG", "xxxX: getMovieUrl: "+ body);

                }
            } catch (IOException e) {
                e.printStackTrace();
                return movie.getVideoUrl();
            }
            Log.d("TAG", "getMovieUrl: xxxXX "+url);
            return url;
        }, executor);
    }

    public static List<Movie> fetchJsonFiles(String folderUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(folderUrl)
                .build();
        List<Movie> movieList = new ArrayList<>();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                // Read response.body().string() to get the response content
                String body = response.body().string();
                Log.d("TAG", "fetchJsonFiles: "+body);
                //  Log.d("TAG", "fetchM3U8Content: body:"+body);
                //System.out.print(body);
//                String regex = "\\\\x22(.[^\\\\]+)\\\\x22,\\\\";
//                Pattern mainPattern = Pattern.compile(regex, Pattern.MULTILINE);
//                // Pattern mainPattern = Pattern.compile("#EXT(?:INF)?(?::-1)?(?:,)?([^#]+)");
//                Matcher mainMatcher = mainPattern.matcher(body);
//
//                List<GoogleFile> playlist = new ArrayList<>();
//
//                while (mainMatcher.find()){
//                    String element = mainMatcher.group(0);
//                    if (element.contains("\\x22,\\")){
//                        element = element.replace("\\x22,\\", "");
//                        if (element.contains("\\x22")){
//                            element = element.replace("\\x22", "");
//                        }
//                    }
//                    // Log.d("TAG", "fetchM3U8Content: list json:xxx: "+element);
//                    if (playlist.isEmpty()){
//                        GoogleFile file = new GoogleFile();
//                        file.id = element;
//                        file.name = null;
//                        playlist.add(file);
//                    }else {
//                        int lastFileKey = playlist.size() -1;
//                        GoogleFile lastFile = playlist.get(lastFileKey);
//                        if (lastFile.name == null){
//                            lastFile.name = element;
//                            lastFile.link = "https://drive.google.com/u/0/uc?id="+lastFile.id+"&export=download";
//                            // lastFile.link = "https://docs.google.com/document/d/"+lastFile.id+"/edit";
//                        }else {
//                            GoogleFile file = new GoogleFile();
//                            file.id = element;
//                            file.name = null;
//                            playlist.add(file);
//                        }
//                    }
//                }
//                Log.d("TAG", "fetchDriveFiles: list files: "+playlist.toString());
//                String movieLogo = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
//
//                for (GoogleFile file: playlist) {
//                    Movie movie = new Movie();
//                    movie.setTitle(file.name);
//                    movie.setVideoUrl(file.link);
//                    movie.setCardImageUrl(movieLogo);
//                    movie.setStudio("google");
//                    movie.setGroup("google");
//                    movieList.add(movie);
//                }
            }
        } catch (IOException e) {
            Log.d("TAG", "fetchJsonFiles: error:"+e.getMessage());
            e.printStackTrace();
        }
        return movieList;
    }

    public List<Movie> fetchDriveFiles_2(String folderUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(folderUrl)
                .build();
        List<Movie> movieList = new ArrayList<>();

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

                while (mainMatcher.find()){
                    String element = mainMatcher.group(0);
                    if (element.contains("\\x22,\\")){
                        element = element.replace("\\x22,\\", "");
                        if (element.contains("\\x22")){
                            element = element.replace("\\x22", "");
                        }
                    }
                    // Log.d("TAG", "fetchM3U8Content: list json:xxx: "+element);
                    if (playlist.isEmpty()){
                        GoogleFile file = new GoogleFile();
                        file.id = element;
                        file.name = null;
                        playlist.add(file);
                    }else {
                        int lastFileKey = playlist.size() -1;
                        GoogleFile lastFile = playlist.get(lastFileKey);
                        if (lastFile.name == null){
                            lastFile.name = element;
                            lastFile.link = "https://drive.google.com/u/0/uc?id="+lastFile.id+"&export=download";
                            // lastFile.link = "https://docs.google.com/document/d/"+lastFile.id+"/edit";
                        }else {
                            GoogleFile file = new GoogleFile();
                            file.id = element;
                            file.name = null;
                            playlist.add(file);
                        }
                    }
                }
                // Log.d("TAG", "fetchDriveFiles: list files: "+playlist.toString());
                String movieLogo = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";

                for (GoogleFile file: playlist) {
                    Movie movie = new Movie();
                    movie.setTitle(file.name);
                    movie.setVideoUrl(file.link);
                    movie.setCardImageUrl(movieLogo);
                    movie.setGroup("google");
                    movie.setStudio(Movie.SERVER_IPTV);
                    movie.setState(Movie.IPTV_PLAY_LIST_STATE);
                    movieList.add(movie);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("TAG", "fetchDriveFiles: "+movieList);
        return movieList;
    }
    public static List<Movie> fetchDriveFiles(String folderUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(folderUrl)
                .build();
        List<Movie> movieList = new ArrayList<>();

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

                while (mainMatcher.find()){
                    String element = mainMatcher.group(0);
                    if (element.contains("\\x22,\\")){
                        element = element.replace("\\x22,\\", "");
                        if (element.contains("\\x22")){
                            element = element.replace("\\x22", "");
                        }
                    }
                    // Log.d("TAG", "fetchM3U8Content: list json:xxx: "+element);
                    if (playlist.isEmpty()){
                        GoogleFile file = new GoogleFile();
                        file.id = element;
                        file.name = null;
                        playlist.add(file);
                    }else {
                        int lastFileKey = playlist.size() -1;
                        GoogleFile lastFile = playlist.get(lastFileKey);
                        if (lastFile.name == null){
                            lastFile.name = element;
                            lastFile.link = "https://drive.google.com/u/0/uc?id="+lastFile.id+"&export=download";
                           // lastFile.link = "https://docs.google.com/document/d/"+lastFile.id+"/edit";
                        }else {
                            GoogleFile file = new GoogleFile();
                            file.id = element;
                            file.name = null;
                            playlist.add(file);
                        }
                    }
                }
               // Log.d("TAG", "fetchDriveFiles: list files: "+playlist.toString());
                String movieLogo = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";

                for (GoogleFile file: playlist) {
                    Movie movie = new Movie();
                    movie.setTitle(file.name);
                    movie.setVideoUrl(file.link);
                    movie.setCardImageUrl(movieLogo);
                    movie.setGroup("google");
                    movie.setStudio(Movie.SERVER_IPTV);
                    movie.setState(Movie.IPTV_PLAY_LIST_STATE);
                    movieList.add(movie);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("TAG", "fetchDriveFiles: "+movieList);
        return movieList;
    }

    private static String calculateHash(byte[] data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(data);
            return ByteString.of(hash).hex();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static HashMap<String, ArrayList<Movie>> fetchM3U8Content(Movie iptvList, MovieDbHelper dbHelper) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(iptvList.getVideoUrl())
                .build();
        HashMap<String, ArrayList<Movie>> movieList = new HashMap<String, ArrayList<Movie>>();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                // Read response.body().string() to get the response content
                if (response.body() == null){
                    Log.d(TAG, "fetchM3U8Content: empty response body");
                    return movieList;
                }
                String body = response.body().string();

                byte[] bytes = body.getBytes();

                // Calculate the hash value (SHA-256 in this example)
                String hashValue = calculateHash(bytes, "SHA-256");
//                iptvList.setDescription(hashValue);
//                Log.d("TAG", "fetchM3U8Content: hash:"+hashValue);

//              Movie existingList = dbHelper.findIptvListByHash(iptvList.getDescription());
                movieList = dbHelper.getMovieListByHash(hashValue);
//              if (existingList != null){
                  Log.d("TAG", "fetchM3U8Content: db " +
                          " . "+ movieList.size());
//              }else {
                if (movieList.isEmpty()){
                    Log.d("TAG", "fetchM3U8Content: xxx: new fetch");
                    movieList = parseGroupNames(body, hashValue);
                }

                  //  Log.d("TAG", "fetchM3U8Content: body:"+body);

                Log.d(TAG, "fetchM3U8Content: size: "+ movieList.size());
//                  final HashMap<String, ArrayList<Movie>> movieListFinal = movieList;
//                  // Save movieList in a background thread
//                  if (!movieList.isEmpty()){
                      for (String group : movieList.keySet()) {
//                    Log.d(TAG, "generateIptvRows: group: "+group);
//                    Log.d(TAG, "generateIptvRows: list: "+futureGroupedMovies.get(group));
                          if (movieList.get(group) == null || movieList.get(group).isEmpty()) {
                              continue;
                          }
                         ArrayList<Movie> finalMovieList = movieList.get(group);
                          new Thread(new Runnable() {
                              @Override
                              public void run() {
                                  dbHelper.saveMovieList(finalMovieList);
                              }
                          }).start();
                      }
//                  }
//              }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return movieList;
    }

//    private List<Movie> fetchIpv(MovieDbHelper dbHelper) {
//        String m3u8Url = "https://github.com/Free-TV/IPTV/blob/master/playlist.m3u8"; // Replace with the actual URL
//
//        Queue<Movie> movieQueue = new ConcurrentLinkedQueue<>();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Movie iptvList = new Movie();
//                    iptvList.setTitle("iptvList");
//                    iptvList.setVideoUrl(m3u8Url);
//                    HashMap<String, List<Movie>>tempList = fetchM3U8Content(iptvList, dbHelper);
//                    movieQueue.addAll(tempList);
//
//                } catch (IOException e) {
//                    Log.d("TAG", "run: xxx error:" + e.getMessage());
//                    e.printStackTrace();
//                }
//            }
//        }).start();
//
//      //  Log.d("TAG", "fetchIpv: soosos:" + movieQueue.size());
//        return new ArrayList<>();
//    }

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
            if (urlList.contains(segmentDTO.url)){
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
        // Create the regex pattern to find the option and everything after '='
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
//            try {
//                URL parsedUrl = new URL(url);
//                String host = parsedUrl.getHost();
//
//                if (host != null && host.contains("airmax")) {
//                    String[] pathParts = parsedUrl.getPath().split("/");
//                    String domain = parsedUrl.getProtocol() + "://" + host + (parsedUrl.getPort() != -1 ? ":" + parsedUrl.getPort() : "") + "/";
//                    String username = pathParts.length > 0 ? pathParts[0] : "";
//                    String password = pathParts.length > 1 ? pathParts[1] : "";
//                    fileName = pathParts.length > 0 ? pathParts[pathParts.length - 1] : "";
//
//                    // Extract part after the last occurrence of '|'
//                    if (fileName.contains("|")) {
//                        fileName = fileName.split("\\|")[0];
//                    }
//                    credentialUrl = domain + username + "/" + password + "/";
//                }
//            } catch (MalformedURLException e) {
//                e.printStackTrace();
//                return null;  // Return null in case of URL parsing error
//            }

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

            // Replace '#http' with 'http' in the URL
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



    public static List<Movie> parseGroupNames_2(String m3u8Content, String hash) {
        List<Movie> groupNames = new ArrayList<>();
       // Pattern mainPattern = Pattern.compile("#EXT(?:INF)?([^#]+)");
        Pattern mainPattern = Pattern.compile("#EXT(?:INF(?::-1,)?)?([^#]+)");
       // Pattern mainPattern = Pattern.compile("#EXT(?:INF)?(?::-1)?(?:,)?([^#]+)");
        Matcher mainMatcher = mainPattern.matcher(m3u8Content);
        int movieNameCounter = 0;

        //Log.d("TAG", "parseGroupNames:main "+m3u8Content);
        while (mainMatcher.find()) {
           String mainMatcherGroup = mainMatcher.group(1);
            String movieLogo = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
              //Log.d("TAG", "parseGroupNames:main "+mainMatcherGroup);
            Log.d(TAG, "parseGroupNames: mainMatcher: "+mainMatcherGroup);
            if (mainMatcherGroup != null){
              String tempMainGroup = mainMatcherGroup;
              if (tempMainGroup.contains("\\")){
                  tempMainGroup = tempMainGroup.replace("\\","");
              }
               Movie movie = new Movie();
               Pattern logoPattern = Pattern.compile("logo=(?:\\\\)?(?:\")?([^\\\\\r\n\"]+)");
               Matcher logoMatcher = logoPattern.matcher(tempMainGroup);

                //Log.d("TAG", "parseGroupNames: image:"+logoMatcher.find()+", "+mainMatcherGroup);
                while (logoMatcher.find()) {
                   movieLogo = logoMatcher.group(1);
                  // Log.d("TAG", "parseGroupNames: image:"+movieLogo);
                   break;
               }

               Pattern groupPattern = Pattern.compile("group-title=([^#]+)");
                Matcher groupMatcher = groupPattern.matcher(mainMatcherGroup);

                String movieGroupName = null;
                String movieUrl = null;
                String  movieGroupNameTitle  = null;
                while (groupMatcher.find()) {
                   String movieGroup = groupMatcher.group(1);
                    //Log.d("TAG", "parseGroupNames: ssss: "+movieGroup);
                   if (movieGroup != null){
                       Pattern groupNamePattern = Pattern.compile("\"([^\"]+)\"", Pattern.MULTILINE);
                       Matcher groupNameMatcher = groupNamePattern.matcher(movieGroup);
                       while (groupNameMatcher.find()) {
                           movieGroupName = groupNameMatcher.group(1);
                           if (movieGroupName !=null){
                               if (movieGroupName.contains("\\")){
                                   movieGroupName=  movieGroupName.replace("\\","");
                               }
                           }
                         //  Log.d("TAG", "parseGroupNames: name:"+groupNameMatcher.groupCount()+", "+movieGroupName);
                           break;
                       }

                       Pattern urlPattern = Pattern.compile("(http[^\\\\\"\r\n]+)", Pattern.MULTILINE);
                       Matcher urlMatcher = urlPattern.matcher(movieGroup);

                       while (urlMatcher.find()) {
                           movieUrl = urlMatcher.group(1);
                          // Log.d("TAG", "parseGroupNames: url:"+urlMatcher.groupCount()+", "+movieUrl);
                           // Log.d("TAG", "parseGroupNames: url:"+urlMatcher.groupCount()+", "+movieUrl);
                           break;
                       }
                    //   Log.d("TAG", "groupMatcher:"+movieGroup);

                       //Pattern groupNameTitlePattern = Pattern.compile("(.*?)\",\".*");
                       //Pattern groupNameTitlePattern = Pattern.compile("\",(.[^,]+)\"");
                       Pattern groupNameTitlePattern = Pattern.compile("\",(.[^,:\n\r]+)(?:\")?");
                       Matcher groupNameTitleMatcher = groupNameTitlePattern.matcher(movieGroup);
                        // Log.d("TAG", "parseGroupNames: name:"+groupNameTitleMatcher.find()+", "+movieGroup);
                       while (groupNameTitleMatcher.find()) {
                           movieGroupNameTitle = groupNameTitleMatcher.group(1);
                           movie.setTitle(movieGroupNameTitle);
                           //Log.d("TAG", "parseGroupNames: title:"+movieGroupNameTitle);
                           break;
                       }
                   }
                    break;
                }

                if (movieGroupNameTitle == null || movieGroupNameTitle.length() < 2){
                    Pattern namePattern = Pattern.compile("(?:tvg-)?name=\"([^\"]+)\"", Pattern.MULTILINE);
                    Matcher nameMatcher = namePattern.matcher(mainMatcherGroup);

                    String movieName = null;
                    while (nameMatcher.find()) {
                        movieName = nameMatcher.group(1);
                        //Log.d("TAG", "parseGroupNames: name:"+movieName);
                        break;
                    }
                    // Log.d("TAG", "parseGroupNames: name:"+nameMatcher.groupCount()+", "+nameMatcher.find()+ ", "+ movieName);
                    if (movieName != null && movieName.length() > 1){
                        movie.setTitle(movieName);
                    }
                    else {
                        Pattern idPattern = Pattern.compile("(?:tvg-)?id=\"([^\"]+)\"", Pattern.MULTILINE);
                        Matcher idMatcher = idPattern.matcher(mainMatcherGroup);

                        String movieId = null;
                        while (idMatcher.find()) {
                            movieId = idMatcher.group(1);
                            //Log.d("TAG", "parseGroupNames: image:"+logoMatcher.group(1));
                            break;
                        }
                        movie.setTitle(movieId);
                        // Log.d("TAG", "parseGroupNames: name:"+idMatcher.groupCount()+", "+idMatcher.find()+ ", "+ movieId);
                    }
                  //  Log.d("TAG", "parseGroupNames: name:"+movie.getTitle());

                }

                if (movieUrl == null){
                    Pattern secondTitlePattern = Pattern.compile("([^#\\r\\n\",]+)");
                    Matcher secondTitleMatcher = secondTitlePattern.matcher(mainMatcherGroup);
                    while (secondTitleMatcher.find()) {
                        String secondTitle = secondTitleMatcher.group(1);
                        movie.setTitle(secondTitle);
                        //Log.d("TAG", "parseGroupNames: title:"+movieGroupNameTitle);
                        break;
                    }

                    Pattern secondUrlPattern = Pattern.compile("(http[^#\\r\\n\",]+)");
                    Matcher secondUrlMatcher = secondUrlPattern.matcher(mainMatcherGroup);
                    while (secondUrlMatcher.find()) {
                        String secondUrl = secondUrlMatcher.group(1);
                        movieUrl = secondUrl;
                        //Log.d("TAG", "parseGroupNames: title:"+movieGroupNameTitle);
                       // Log.d("TAG", "parseGroupNames:url "+ secondUrl);
                        break;
                    }
                }

                if (movieUrl != null){
                    movie.setCardImageUrl(movieLogo);
                    if (movieGroupName == null || movieGroupName.trim().equals("")){
                        if (movie.getTitle() != null){
                            String[] movieGroupNameArray = null;
                           // Log.d("TAG", "parseGroupNames: name:"+movieGroupName+", title:"+movie.getTitle());
                            if (movie.getTitle().contains("-")){
                                movieGroupNameArray = movie.getTitle().split("-");
                            }else if (movie.getTitle().contains(":")){
                                movieGroupNameArray = movie.getTitle().split(":");
                            }
                            if (movieGroupNameArray != null  && movieGroupNameArray.length >1){
                                movie.setTitle(movieGroupNameArray[1]);
                                movieGroupName = movieGroupNameArray[0];
                            }
                        }
                        if(movieGroupName == null) {
                            movieGroupName = "undefined";
                            movie.setTitle(movieGroupName +"-"+ movieNameCounter++);
                        }
                    }
                    movie.setGroup(movieGroupName);
                    movie.setVideoUrl(movieUrl);
                    movie.setMainMovieTitle(hash);
                    movie.setStudio(Movie.SERVER_IPTV);
                    movie.setState(Movie.VIDEO_STATE);
                //    Log.d("TAG", "parseGroupNames:xxx "+movie.toString());
                    groupNames.add(movie);
                }
//                while (matcher.find()) {
//                   String groupTitle = matcher.group(1);
//                   if (groupTitle != null){
//                      // Log.d("TAG", "parseGroupNames:main "+groupTitle);
//                       if (groupTitle.contains("\"")){
//                           groupTitle = groupTitle.replace("\"", "");
//                       }
//                       if (groupTitle.contains("\\")){
//                           groupTitle = groupTitle.replace("\\", "");
//                       }
//                       String[] groupArray = groupTitle.split(",");
//                     //  Log.d("TAG", "parseGroupNames: Movie: "+groupTitle);
//                       if (groupArray.length > 0){
//                           if (groupArray[0] != null) {
//                               movie.setGroup(groupArray[0]);
//                           }
//                           if (groupArray.length > 1) {
//                               String title = groupArray[1];
//
//                               //if (title.contains("http")){
//                                   Pattern titlePattern = Pattern.compile("name=(?:\\\\\\\\)?(?:\\\")?([^\\\\\\\\\\\"]+)\"");
//                                   Matcher titleMatcher = titlePattern.matcher(title);
//                                   while (titleMatcher.find()) {
//                                       movie.setVideoUrl(titleMatcher.group(1));
//                                       break;
//                                   }
//                             //  }
//                             //  Log.d("TAG", "parseGroupNames: title:"+title);
//                               movie.setTitle(title);
//                           }
//                           if (groupArray.length > 3) {
//                               movie.setVideoUrl(groupArray[2]);
//                           }else {
//                               Pattern urlPattern = Pattern.compile("(http.+)$");
//                               Matcher urlMatcher = urlPattern.matcher(groupTitle);
//                               while (urlMatcher.find()) {
//                                   movie.setVideoUrl(urlMatcher.group(1));
//                                   break;
//                               }
//                           }
//                          // Log.d("TAG", "parseGroupNames: Movie: "+movie.toString());
//                           groupNames.add(movie);
//                       }
//                   }
//               }
           }
        }

        Log.d("TAG", "parseGroupNames: " + groupNames.size());
        return groupNames;
    }

    private static void parseAndPrintUrls(String m3u8Content) {
        String[] lines = m3u8Content.split("\n");
        for (String line : lines) {
            if (line.startsWith("#EXTINF")) {
                int urlStartIndex = line.indexOf(',') + 1;
                if (urlStartIndex > 0 && urlStartIndex < line.length()) {
                    String url = lines[lines.length - 1];
                 //   Log.d("TAG", "parseAndPrintUrls: " + url);
                }
            }
        }
    }

}
