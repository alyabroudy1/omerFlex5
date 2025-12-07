package com.omarflex5.temp.omerflex.data.source.remote;

import android.util.Log;

import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieRepository;
import com.omerflex.entity.ServerConfig;
import com.omerflex.entity.dto.CategoryDTO;
import com.omerflex.entity.dto.MovieCategoryDTO;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.ServerFactory;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.config.ServerConfigRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RemoteDataSource {

    private static RemoteDataSource instance;
    public static String TAG = "RemoteDataSource";

    public static synchronized RemoteDataSource getInstance() {
        if (instance == null) {
            instance = new RemoteDataSource();
        }
        return instance;
    }

    public void fetchMovieByUrl(String videoUrl, MovieRepository.MovieCallback callback) {
        // For now, returning a dummy movie
//        callback.onMovieFetched(generateDummyMovies().get(0));
    }

    public interface AllMoviesCallback {
        void onAllMoviesFetched();
    }

    public void fetchHomepageMovies(boolean handleCookie, MovieRepository.MovieListCallback callback) {
        ServerConfigRepository repository = ServerConfigRepository.getInstance();
        Log.d(TAG, "fetchHomepageMovies: ");
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            repository.getIsInitialized().observeForever(new androidx.lifecycle.Observer<Boolean>() {
                @Override
                public void onChanged(Boolean isInitialized) {
                    if (isInitialized != null && isInitialized) {
                        repository.getIsInitialized().removeObserver(this);

                        ExecutorService executor = Executors.newCachedThreadPool();
                        executor.submit(() -> {
                            List<ServerConfig> configs = repository.getAllActiveConfigsList();
                            Log.d(TAG, "fetchHomepageMovies: Starting fetch for " + configs.size() + " servers.");

                            for (ServerConfig config : configs) {
                                if (
//                                        config.getName().equals(Movie.SERVER_OLD_AKWAM)
                                        !config.getName().equals(Movie.SERVER_KOORA_LIVE)
//                                        !config.getName().equals(Movie.SERVER_FASELHD)
//                                        !config.getName().equals(Movie.SERVER_MyCima)
//                                        !config.getName().equals(Movie.SERVER_ARAB_SEED)
                                ){
                                    // Latch is removed, just skip this server.
                                    continue;
                                }
                                ExecutorService executor2 = Executors.newCachedThreadPool();
                                executor2.submit(() -> {
                                    AbstractServer server = null;
                                    try {
                                        server = ServerFactory.createServer(config.getName());
                                    } catch (Exception e) {
                                        Log.e(TAG, "Failed to create server: " + config.getName(), e);
                                        return;
                                    }

                                    if (server != null) {
                                        final String serverName = server.getLabel();
                                        Log.d(TAG, "fetchHomepageMovies: config: " + config.getName() + ", " + serverName);
                                        server.getHomepageMovies(handleCookie, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
                                            @Override
                                            public void onSuccess(ArrayList<Movie> result, String title) {
                                                Log.d(TAG, "onSuccess from " + serverName);
                                                if (result.isEmpty()) {
                                                    Log.d(TAG, "onSuccess: " + title + " is empty");
                                                }
                                                callback.onMovieListFetched(title, result);
                                            }

                                            @Override
                                            public void onInvalidCookie(ArrayList<Movie> result, String title) {
                                                Log.d(TAG, "onInvalidCookie from " + serverName);
                                                callback.onMovieListFetched(title, result);
                                            }

                                            @Override
                                            public void onInvalidLink(ArrayList<Movie> result) {
                                                Log.d(TAG, "onInvalidLink from " + serverName);
                                            }

                                            @Override
                                            public void onInvalidLink(String message) {
                                                Log.d(TAG, "onInvalidLink from " + serverName + ": " + message);
                                            }
                                        });
                                    } else {
                                        Log.d(TAG, "Server is null for " + config.getName());
                                    }

                                    });
                                    executor2.shutdown();
                            }
                        });
                        executor.shutdown();
                    }
                }
            });
        });
    }

//    private List<Movie> generateDummyMovies() {
//        List<Movie> dummyMovies = new ArrayList<>();
//        String placeholderCardUrl = "https://placehold.co/500x750/4F46E5/FFFFFF?text=Placeholder";
//        dummyMovies.add(new Movie(
//                "series",
//                "Inception dream mind heist",
//                "Inception",
//                "Warner Bros.",
//                Movie.GROUP_OF_GROUP_STATE,
//                "A thief who steals corporate secrets through the use of dream-sharing technology...",
//                "https://image.tmdb.org/t/p/w500/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // bgImageUrl
//                placeholderCardUrl, // cardImageUrl is now a placeholder
//                "https://commondatastorage.googleapis.com/android-tv/Sample%20videos/Demo%20Slam/Inception/Inception.mp4", // videoUrl
//                "8.8",
//                "https://www.youtube.com/watch?v=YoV436yA778", // trailerUrl
//                0,
//                0,
//                "2010-07-16",
//                new Date(),
//                0,
//                "https://image.tmdb.org/t/p/original/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // backgroundImageUrl
//                "Science Fiction"
//        ));
//
//        dummyMovies.add(new Movie(
//                "series",
//                "Inception dream mind heist",
//                "Inception",
//                "Warner Bros.",
//                Movie.VIDEO_STATE,
//                "A thief who steals corporate secrets through the use of dream-sharing technology...",
//                "https://image.tmdb.org/t/p/w500/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // bgImageUrl
//                placeholderCardUrl, // cardImageUrl is now a placeholder
//                "https://commondatastorage.googleapis.com/android-tv/Sample%20videos/Demo%20Slam/Inception/Inception.mp4", // videoUrl
//                "8.8",
//                "https://www.youtube.com/watch?v=YoV436yA778", // trailerUrl
//                0,
//                0,
//                "2010-07-16",
//                new Date(),
//                0,
//                "https://image.tmdb.org/t/p/original/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // backgroundImageUrl
//                "Science Fiction"
//        ));
//
//        dummyMovies.add(new Movie(
//                "series",
//                "Inception dream mind heist",
//                "Inception",
//                "Warner Bros.",
//                Movie.COOKIE_STATE,
//                "A thief who steals corporate secrets through the use of dream-sharing technology...",
//                "https://image.tmdb.org/t/p/w500/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // bgImageUrl
//                placeholderCardUrl, // cardImageUrl is now a placeholder
//                "https://commondatastorage.googleapis.com/android-tv/Sample%20videos/Demo%20Slam/Inception/Inception.mp4", // videoUrl
//                "8.8",
//                "https://www.youtube.com/watch?v=YoV436yA778", // trailerUrl
//                0,
//                0,
//                "2010-07-16",
//                new Date(),
//                0,
//                "https://image.tmdb.org/t/p/original/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // backgroundImageUrl
//                "Science Fiction"
//        ));
//
//        dummyMovies.add(new Movie(
//                "series",
//                "Inception dream mind heist",
//                "Inception",
//                "Warner Bros.",
//                Movie.NEXT_PAGE_STATE,
//                "A thief who steals corporate secrets through the use of dream-sharing technology...",
//                "https://image.tmdb.org/t/p/w500/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // bgImageUrl
//                placeholderCardUrl, // cardImageUrl is now a placeholder
//                "https://commondatastorage.googleapis.com/android-tv/Sample%20videos/Demo%20Slam/Inception/Inception.mp4", // videoUrl
//                "8.8",
//                "https://www.youtube.com/watch?v=YoV436yA778", // trailerUrl
//                0,
//                0,
//                "2010-07-16",
//                new Date(),
//                0,
//                "https://image.tmdb.org/t/p/original/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // backgroundImageUrl
//                "Science Fiction"
//        ));
//
//        dummyMovies.add(new Movie(
//                "series",
//                "Inception dream mind heist",
//                "Inception",
//                "Warner Bros.",
//                Movie.BROWSER_STATE,
//                "A thief who steals corporate secrets through the use of dream-sharing technology...",
//                "https://image.tmdb.org/t/p/w500/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // bgImageUrl
//                placeholderCardUrl, // cardImageUrl is now a placeholder
//                "https://commondatastorage.googleapis.com/android-tv/Sample%20videos/Demo%20Slam/Inception/Inception.mp4", // videoUrl
//                "8.8",
//                "https://www.youtube.com/watch?v=YoV436yA778", // trailerUrl
//                0,
//                0,
//                "2010-07-16",
//                new Date(),
//                0,
//                "https://image.tmdb.org/t/p/original/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // backgroundImageUrl
//                "Science Fiction"
//        ));
//
//        dummyMovies.add(new Movie(
//                "series",
//                "Inception dream mind heist",
//                "Inception",
//                "Warner Bros.",
//                Movie.IPTV_PLAY_LIST_STATE,
//                "A thief who steals corporate secrets through the use of dream-sharing technology...",
//                "https://image.tmdb.org/t/p/w500/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // bgImageUrl
//                placeholderCardUrl, // cardImageUrl is now a placeholder
//                "https://commondatastorage.googleapis.com/android-tv/Sample%20videos/Demo%20Slam/Inception/Inception.mp4", // videoUrl
//                "8.8",
//                "https://www.youtube.com/watch?v=YoV436yA778", // trailerUrl
//                0,
//                0,
//                "2010-07-16",
//                new Date(),
//                0,
//                "https://image.tmdb.org/t/p/original/8c728eG8i7v0I1Xf9R9E5b1h7P4.jpg", // backgroundImageUrl
//                "Science Fiction"
//        ));
//        return dummyMovies;
//    }

    public void fetchMovieDetails(Movie mSelectedMovie, ServerInterface.ActivityCallback<Movie> callback) {
        Log.d(TAG, "fetchMovieDetails: ");
        ServerConfigRepository.getInstance().getServerAsync(mSelectedMovie.getStudio(), server -> {
            if (server != null) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> {
                     server.fetch(mSelectedMovie, mSelectedMovie.getState(), callback);
                });
                executor.shutdown();
            } else {
                callback.onInvalidLink("Server not found for " + mSelectedMovie.getStudio());
            }
        });
    }

    public void getSearchMovies(boolean handleCookie, String query, MovieRepository.MovieListCallback callback) {
        ServerConfigRepository repository = ServerConfigRepository.getInstance();
        Log.d(TAG, "getSearchMovies: ");
        // LiveData must be observed from the main thread.
        // We post the observer attachment to the main looper.
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            repository.getIsInitialized().observeForever(new androidx.lifecycle.Observer<Boolean>() {
                @Override
                public void onChanged(Boolean isInitialized) {
                    if (isInitialized != null && isInitialized) {
                        // The configuration is ready. We can now fetch the movies.
                        // It's important to remove the observer to avoid memory leaks.
                        repository.getIsInitialized().removeObserver(this);

                        ExecutorService executor = Executors.newSingleThreadExecutor();
                        executor.submit(() -> {
                            List<ServerConfig> configs = repository.getAllActiveConfigsList();
                            for (ServerConfig config : configs) {
                                AbstractServer server = ServerFactory.createServer(config.getName());
                                Log.d(TAG, "getSearchMovies: config: " + config.getName() + ", "+ server.getLabel());
                                    server.search(query, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
                                        @Override
                                        public void onSuccess(ArrayList<Movie> result, String title) {
                                            if (result.isEmpty()){
                                                Log.d(TAG, "onSuccess: "+ title + " is empty");
                                            }
                                            Log.d(TAG, "onSuccess: "+ title);
                                            callback.onMovieListFetched(title, result);
                                            return;
                                        }

                                        @Override
                                        public void onInvalidCookie(ArrayList<Movie> result, String title) {
                                            Log.d(TAG, "onInvalidCookie: ");
                                            callback.onMovieListFetched(title, result);
                                        }

                                        @Override
                                        public void onInvalidLink(ArrayList<Movie> result) {
                                            Log.d(TAG, "onInvalidLink: ");
                                        }

                                        @Override
                                        public void onInvalidLink(String message) {
                                            Log.d(TAG, "onInvalidLink: ");
                                        }
                                    },
                                            false);
                            }
                        });
                        executor.shutdown();
                    }
                }
            });
        });
    }
}
