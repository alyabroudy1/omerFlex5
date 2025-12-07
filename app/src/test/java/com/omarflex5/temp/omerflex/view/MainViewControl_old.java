package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.fragment.app.Fragment;

import com.omerflex.entity.Movie;
import com.omerflex.entity.ServerConfig;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.database.MovieDbHelper;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class MainViewControl_old extends SearchViewControl {
    public MainViewControl_old(Activity activity, Fragment fragment, MovieDbHelper dbHelper) {
        super(activity, fragment, dbHelper);
    }

    protected <T> void loadServerRow(AbstractServer server, String finalQuery) {
        //initialize homepage
        Log.d(TAG, "loadHomepageRows homepage "+ server.getLabel());

//            ExecutorService executor2 = Executors.newSingleThreadExecutor();
//            executor2.submit(() -> {
        try {

//            if (server instanceof IptvServer) {
//                // return as it is loaded with loadOmarServerResult to manage orders
//                return;
//            }

//            if (
//                    server instanceof KooraServer //||
////                    server instanceof OldAkwamServer //||
////                    server instanceof AkwamServer ||
////                    server instanceof ArabSeedServer ||
////                    server instanceof CimaNowServer ||
////                    server instanceof FaselHdServer ||
////                    server instanceof OmarServer ||
////                    server instanceof MyCimaServer
//            ) {
//                return;
//            }
//
//            if (server instanceof OmarServer) {
//                loadOmarServerResult(finalQuery, server);
//                return;
//            }
            T serverAdapter = generateCategory(server.getLabel(), new ArrayList<>(), true);

            ExecutorService executor2 = Executors.newSingleThreadExecutor();
            executor2.submit(() -> {
                try {
                    ArrayList<Movie> movies = server.getHomepageMovies(false, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
                        @Override
                        public void onSuccess(ArrayList<Movie> result, String title) {
                            if (result.isEmpty()) {
                                Log.d(TAG, "onSuccess: empty result");
                                return;
                            }
                            updateMovieListOfMovieAdapter(result, serverAdapter);
//                                    loadMoviesRow(server, serverAdapter, result);
                            if (server.shouldUpdateDomainOnSearchResult()){
                                ServerConfig config = ServerConfigRepository.getInstance().getConfig(server.getServerId());
                                ServerConfigRepository.getInstance().updateConfig(config);
                                Log.d(TAG, "onSuccess: updateDomain on search result");
//                                Movie sampleMovie = result.get(0);
//                                if (sampleMovie != null && sampleMovie.getVideoUrl() != null) {
//                                    ServerConfig config = ServerConfigRepository.getConfig(server.getServerId());
//                                    if (null != config) {
//                                        updateDomain(sampleMovie.getVideoUrl(), config, dbHelper);
//                                    }
//                                }
                            }
                        }

                        @Override
                        public void onInvalidCookie(ArrayList<Movie> result, String title) {
                            Log.d(TAG, "onInvalidCookie: MainViewController LoadRows: " + result);
//                                    loadMoviesRow(server, serverAdapter, result);
                            updateMovieListOfMovieAdapter(result, serverAdapter);
                            if (server.shouldUpdateDomainOnSearchResult()){
                                ServerConfig config = ServerConfigRepository.getInstance().getConfig(server.getServerId());
                                ServerConfigRepository.getInstance().updateConfig(config);
                                Log.d(TAG, "onInvalidCookie: updateDomain on search result");
                            }
                        }

                        @Override
                        public void onInvalidLink(ArrayList<Movie> result) {
                            Log.d(TAG, "onInvalidLink: ");
                        }

                        @Override
                        public void onInvalidLink(String message) {
                            Log.d(TAG, "onInvalidLink: "+message);
                        }
                    });
                } catch (Exception exception) {
                    Log.d(TAG, "loadHomepageRaws: error: " + server.getLabel() + ", " + exception.getMessage());
                }
            });
            executor2.shutdown();
        } catch (Exception exception) {
            Log.d(TAG, "loadHomepageRaws: error: " + exception.getMessage());
        }
    }

    protected void loadOmarServerResult(String query, AbstractServer server) {
        ExecutorService executor2 = Executors.newSingleThreadExecutor();
        executor2.submit(() -> {
//            Log.d(TAG, "loadOmarServerResult: before");
           ArrayList<Movie> movies = server.getHomepageMovies(false, new SearchCallback());
//            Log.d(TAG, "loadOmarServerResult: after");
            try {
                loadIptvServerHomepageResult();
            }catch (Exception e){
                Log.d(TAG, "loadOmarServerResult: error: "+e.getMessage());
            }
//            loadHomepageHistoryRows();
        });
        executor2.shutdown();
    }

    private void loadIptvServerHomepageResult() {
        Log.d(TAG, "loadIptvServerHomepageResult: ");
        AbstractServer server = ServerConfigRepository.getInstance().getServer(Movie.SERVER_IPTV);
        if (server == null){
            Log.d(TAG, "loadIptvServerHomepageResult: undefined iptv server");
            return;
        }
        ArrayList<Movie> movies = server.getHomepageMovies(false, new SearchCallback());
    }

    private void updateDomain(String movieLink, ServerConfig config, MovieDbHelper dbHelper) {
        String newDomain = Util.extractDomain(movieLink, true, false);
        boolean equal = config.getUrl().contains(newDomain);
        Log.d(TAG, "updateDomain: old: " + config.getUrl() + ", new: " + newDomain + ", = " + (equal));
        if (!equal) {
            config.setUrl(newDomain);
            config.setReferer(newDomain + "/");
//            ServerConfigRepository.updateConfig(config);

//            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
//            Log.d(TAG, "addServerConfigsToDB: ");
//            Date date = null;
//            try {
//                date = format.parse("2024-02-22T12:30:00");
//            } catch (ParseException e) {
//                date = new Date();
//            }
//            dbHelper.saveServerConfigAsCookieDTO(config, date);
            ServerConfigRepository.getInstance().updateConfig(config);
        }
    }

    protected void loadHomepageHistoryRows() {
        try {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    ArrayList<Movie> historyMovies = dbHelper.getAllHistoryMovies(false);
                    generateCategory("المحفوظات", historyMovies, true);

                    ArrayList<Movie> iptvHistoryMovies = dbHelper.getAllHistoryMovies(true);
                    generateCategory("محفوظات القنوات", iptvHistoryMovies, true);
                }
            });
        } catch (Exception e) {
            Log.d(TAG, "loadHomepageRaws: error loading historyRows: " + e.getMessage());
        }
    }
}
