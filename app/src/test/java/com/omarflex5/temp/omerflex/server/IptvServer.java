package com.omarflex5.temp.omerflex.server;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.omerflex.OmerFlexApplication;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.entity.MovieRepository;
import com.omerflex.entity.MovieType;
import com.omerflex.service.M3U8ContentFetcher;
import com.omerflex.service.database.MovieDbHelper;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class IptvServer extends AbstractServer {

    static String TAG = "iptv";
    public static String MAIN_IPTV_PLAYLIST_URL = "https://drive.google.com/u/0/uc?id=1a7kHxCHOxW6TH8OnM_kO3SjjQvt38oCx&export=download";

    private MovieRepository movieRepository;
    M3U8ContentFetcher contentFetcher;

    public IptvServer() {

        this.contentFetcher = new M3U8ContentFetcher();
        this.movieRepository = MovieRepository.getInstance(OmerFlexApplication.getAppContext(), OmerFlexApplication.getInstance().getDatabase().movieDao());
    }

//    @Override
//    public ArrayList<Movie> search(String query, ActivityCallback<ArrayList<Movie>> activityCallback) {
//        ArrayList<Movie> tvChannelList = new ArrayList<>();
////        if (MainFragment.iptvList != null){
////            for (Movie movie : MainFragment.iptvList) {
////                boolean titleCond = movie != null && movie.getTitle() != null && movie.getTitle().toLowerCase().contains(query.toLowerCase());
////                boolean groupCond = movie != null && movie.getGroup() != null && movie.getGroup().toLowerCase().contains(query.toLowerCase());
////                if (titleCond || groupCond ) {
////                    tvChannelList.add(movie);
////                }
////            }
////        }
//
////        if (tvChannelList.size() == 0){
//        return dbHelper.findMovieBySearchContext(Movie.SERVER_IPTV, query);
////        }
////        return tvChannelList;
//    }

    @Override
    protected ArrayList<Movie> getSearchMovieList(Document doc) {
        return null;
    }

    @Override
    protected MovieFetchProcess fetchSeriesAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        return null;
    }

    @Override
    protected MovieFetchProcess fetchItemAction(Movie movie, int action, ActivityCallback<Movie> activityCallback) {
        return null;
    }

    @Override
    public String getServerId() {
        return Movie.SERVER_IPTV;
    }

    @Override
    protected String getSearchUrl(String query) {
        return null;
    }

//    @Override
//    public Movie fetch(Movie movie) {
//        return null;
//    }
public ArrayList<Movie> search(String query, ActivityCallback<ArrayList<Movie>> activityCallback, boolean handleCookie) {
        movieRepository.getSearchIptvMovies(query, (category, movies) -> {
            activityCallback.onSuccess(movies, category);
        });
        return null;
}

    @Override
    public Movie updateMovieState(Movie movie) {
        movie.setState(Movie.ITEM_STATE);
        movie.setType(MovieType.FILM);
        return movie;
    }


    @Override
    public ArrayList<Movie> getHomepageMovies(boolean handleCookie, ActivityCallback<ArrayList<Movie>> activityCallback) {
        ArrayList<Movie> iptvList = new ArrayList<>();
        activityCallback.onInvalidLink("nothing to show");

        // fetch google drive iptv playlists
//        try {
//            Log.d(TAG, "getHomepageMovies: a");
//            String m3u8Url = "https://drive.google.com/drive/folders/1lHoE-WD43FGr9kHAYoo-11HrPHgUOQMa?usp=sharing";
//            fetchDriveFiles(m3u8Url, activityCallback);
//        } catch (Exception exception) {
//            Log.d(TAG, "getHomepageMovies: error c: "+exception.getMessage());
//            activityCallback.onInvalidLink(exception.getMessage());
//        }
        return iptvList;
    }

    @Override
    public int fetchNextAction(Movie movie) {
        return 0;
    }

//    @RequiresApi(api = Build.VERSION_CODES.N)
//    public CompletableFuture<List<Movie>> fetchM3U8ContentAsync(Movie movie) {
//        CompletableFuture<List<Movie>> futureMovieList = contentFetcher.fetchM3U8ContentAsync(movie.getVideoUrl());
//       return futureMovieList.thenAccept(movieList -> {
//            Map<String, List<Movie>> groupedMovies = groupMoviesByGroup(movieList);
//            return groupedMovies;
//            // Print groups and their movies
//            for (Map.Entry<String, List<Movie>> entry : groupedMovies.entrySet()) {
//                String group = entry.getKey();
//                List<Movie> groupMovies = entry.getValue();
//                ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new ErrorFragment.CardPresenter());
//                android.util.Log.d(TAG, "loadRows: "+group+", "+groupMovies.size());
//                listRowAdapter.addAll(0, groupMovies);
//                HeaderItem header = new HeaderItem(MainFragment.HEADER_ROWS_COUNTER++, group);
//                rowsAdapter.add(new ListRow(header, listRowAdapter));
//            }
//        });
//    }

//    @RequiresApi(api = Build.VERSION_CODES.N)
//    public CompletableFuture<Map<String, List<Movie>>> fetchAndGroupM3U8ContentAsync(Movie movie, MovieDbHelper dbHelper) {
//        CompletableFuture<List<Movie>> futureMovieList = contentFetcher.fetchM3U8ContentAsync(movie, dbHelper);
//
//        return futureMovieList.thenApplyAsync(IptvServer::groupMoviesByGroup);
//    }
    public HashMap<String, ArrayList<Movie>> fetchAndGroupM3U8ContentAsync(Movie movie, MovieDbHelper dbHelper) {
//        return M3U8ContentFetcher.fetchM3U8ContentAsync(movie, dbHelper);
//        try {
//            return M3U8ContentFetcher.fetchAndProcessContent(movie, dbHelper);
            return  new HashMap<>();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    public CompletableFuture<List<Movie>> fetchDriveFilesAsync(String folderUrl) throws IOException {
        CompletableFuture<List<Movie>> futureMovieList = contentFetcher.fetchDriveFilesAsync(folderUrl);
        return futureMovieList;
    }

    public static Map<String, List<Movie>> groupMoviesByGroup(List<Movie> movies) {
        Map<String, List<Movie>> groupedMovies = new HashMap<>();

        for (Movie movie : movies) {
            String groupName = movie.getGroup();
            if (!groupedMovies.containsKey(groupName)) {
                groupedMovies.put(groupName, new ArrayList<>());
            }
            groupedMovies.get(groupName).add(movie);
        }

        return groupedMovies;
    }

    @Override
    public String getWebScript(int mode, Movie movie) {
        return "";
    }

    public void fetchDriveFiles(String m3u8Url, ActivityCallback<ArrayList<Movie>> activityCallback) throws IOException {
         contentFetcher.fetchDriveFiles(m3u8Url, activityCallback);
    }

    public boolean shouldUpdateDomainOnSearchResult(){
        return false;
    }

    public void loadDefaultChannels(MovieRepository.MovieListCallback callback) {
        contentFetcher.fetchDefaultChannels(MAIN_IPTV_PLAYLIST_URL, callback);
    }

    public void loadChannelsFromUrl(String url, MovieRepository.MovieListCallback callback) {
        contentFetcher.fetchDefaultChannels(url, callback);
    }
}
