package com.omarflex5.temp.omerflex.service;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.omerflex.entity.Movie;
import com.omerflex.entity.dto.GoogleFile;
import com.omerflex.service.database.MovieDbHelper;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.ByteString;

public class M3U8ContentFetcher_old {

    MovieDbHelper dbHelper;
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void main(String[] args) {
       // String m3u8Url = "https://github.com/Free-TV/IPTV/blob/master/playlist.m3u8";
        String m3u8Url = "https://iptv-list.live/download/421/12-16-22-iptv-list-1.m3u";
        Movie iptvList = new Movie();
        iptvList.setTitle("iptvList");
        iptvList.setVideoUrl(m3u8Url);

        CompletableFuture<List<Movie>> futureMovieList = fetchM3U8ContentAsync(iptvList, null);

        futureMovieList.thenAccept(movieList -> {
            System.out.println("Movie List Size: " + movieList.size());
            for (Movie movie : movieList) {
                Log.d("TAG", "main: " + movie.toString());
            }
        });
        // Wait for the asynchronous task to complete
        try {
            futureMovieList.get(); // This blocks until the task is complete
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static CompletableFuture<List<Movie>> fetchM3U8ContentAsync(Movie iptvList, MovieDbHelper dbHelper) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Movie> tempList = fetchM3U8Content(iptvList, dbHelper);
                return tempList;
            } catch (IOException e) {
                e.printStackTrace();
                return new ArrayList<>();
            }
        }, executor);
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

    private static List<Movie> fetchM3U8Content(Movie iptvList, MovieDbHelper dbHelper) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(iptvList.getVideoUrl())
                .build();
        List<Movie> movieList = new ArrayList<>();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                // Read response.body().string() to get the response content
                String body = response.body().string();

                byte[] bytes = body.getBytes();

                // Calculate the hash value (SHA-256 in this example)
                String hashValue = calculateHash(bytes, "SHA-256");
                iptvList.setDescription(hashValue);
                Log.d("TAG", "fetchM3U8Content: hash:"+hashValue);

              Movie existingList = dbHelper.findIptvListByHash(iptvList.getDescription());
              if (existingList != null){
                  Log.d("TAG", "fetchM3U8Content: xxx: database");
//                  movieList = dbHelper.getMovieListByHash(hashValue);
              }else {
                  Log.d("TAG", "fetchM3U8Content: xxx: new fetch");
                  //  Log.d("TAG", "fetchM3U8Content: body:"+body);
                  dbHelper.saveIptvList(iptvList);
                  movieList = parseGroupNames(body, hashValue);
                  final List<Movie> movieListFinal = new ArrayList<>(movieList);
                  // Save movieList in a background thread
                  new Thread(new Runnable() {
                      @Override
                      public void run() {
                          dbHelper.saveMovieList(movieListFinal);
                      }
                  }).start();

              }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return movieList;
    }

    private List<Movie> fetchIpv(MovieDbHelper dbHelper) {
        String m3u8Url = "https://github.com/Free-TV/IPTV/blob/master/playlist.m3u8"; // Replace with the actual URL

        Queue<Movie> movieQueue = new ConcurrentLinkedQueue<>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Movie iptvList = new Movie();
                    iptvList.setTitle("iptvList");
                    iptvList.setVideoUrl(m3u8Url);
                    List<Movie> tempList = fetchM3U8Content(iptvList, dbHelper);
                    movieQueue.addAll(tempList);

                } catch (IOException e) {
                    Log.d("TAG", "run: xxx error:" + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();

      //  Log.d("TAG", "fetchIpv: soosos:" + movieQueue.size());
        return new ArrayList<>();
    }

    private static List<Movie> parseGroupNames(String m3u8Content, String hash) {
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
