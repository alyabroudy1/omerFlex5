package com.omarflex5.temp.omerflex.entity;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.omerflex.data.source.local.LocalDataSource;
import com.omerflex.data.source.remote.RemoteDataSource;
import com.omerflex.db.AppDatabase;
import com.omerflex.db.MovieDao;
import com.omerflex.db.MovieHistoryDao;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.IptvServer;
import com.omerflex.server.ServerFactory;
import com.omerflex.server.ServerInterface;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MovieRepository {
    private static final String TAG = "MovieRepository";
    private static MovieRepository instance;
    private final LocalDataSource localDataSource;
    private final RemoteDataSource remoteDataSource;
    private final MutableLiveData<Movie> selectedMovie = new MutableLiveData<>();
    private final MovieDao movieDao;
    private final MovieHistoryDao movieHistoryDao;

    // Centralized executor for all database operations
    private static final int DATABASE_THREAD_POOL_SIZE = 4;
    private static final ExecutorService databaseExecutor = Executors.newFixedThreadPool(DATABASE_THREAD_POOL_SIZE);


    private MovieRepository(Context context, MovieDao movieDao) {
        localDataSource = LocalDataSource.getInstance(context);
        remoteDataSource = RemoteDataSource.getInstance();
        this.movieDao = movieDao;
        this.movieHistoryDao = AppDatabase.getDatabase(context).movieHistoryDao();
    }

    public static synchronized MovieRepository getInstance(Context context, MovieDao movieDao) {
        if (instance == null) {
            instance = new MovieRepository(context.getApplicationContext(), movieDao);
        }
        return instance;
    }

    private String removeDomain(String url) {
        if (url == null || !url.startsWith("http")) {
            return url;
        }
        int doubleSlash = url.indexOf("//");
        if (doubleSlash == -1) {
            return url;
        }
        int nextSlash = url.indexOf('/', doubleSlash + 2);
        if (nextSlash == -1) {
            return "/"; // URL is just the domain
        }
        return url.substring(nextSlash);
    }

    private void reAddDomainToMovie(Movie movie) {
        if (movie != null && movie.getVideoUrl() != null && !movie.getVideoUrl().startsWith("http")) {
            ServerConfigRepository.getInstance().getConfigAsync(movie.getStudio(), serverConfig -> {
                if (serverConfig != null) {
                    movie.setVideoUrl(serverConfig.getUrl() + movie.getVideoUrl());
                }
            });
        }
    }

    private void reAddDomainToMovies(List<Movie> movies) {
        if (movies != null) {
            for (Movie movie : movies) {
                reAddDomainToMovie(movie);
            }
        }
    }

    public LiveData<Movie> getSelectedMovie() {
        return selectedMovie;
    }

    public void setSelectedMovie(Movie movie) {
        selectedMovie.postValue(movie);
    }

    public void saveMovie(Movie movie) {
        databaseExecutor.execute(() -> {
            movie.setVideoUrl(removeDomain(movie.getVideoUrl()));
            long id = movieDao.insert(movie);
            movie.setId(id);
            if (movie.getMovieHistory() != null) {
                movie.getMovieHistory().setMovieId(movie.getId());
                movieHistoryDao.insert(movie.getMovieHistory());
            }
        });
    }

    public void getMovieByUrl(String videoUrl, MovieCallback callback) {
        localDataSource.getMovieByUrl(videoUrl, movie -> {
            if (movie != null) {
                reAddDomainToMovie(movie);
                callback.onMovieFetched(movie);
            } else {
                remoteDataSource.fetchMovieByUrl(videoUrl, remoteMovie -> {
                    if (remoteMovie != null) {
                        saveMovie(remoteMovie);
                        reAddDomainToMovie(remoteMovie);
                        callback.onMovieFetched(remoteMovie);
                    } else {
                        callback.onMovieFetched(null);
                    }
                });
            }
        });
    }

    public void getHomepageMovies(boolean handleCookie, final MovieListCallback callback) {
        Log.d(TAG, "getHomepageMovies: ");
        remoteDataSource.fetchHomepageMovies(handleCookie, (remoteCategory, remoteMovies) -> {
            Log.d(TAG, "getHomepageMovies: remote: " + remoteMovies.size());
            if (remoteMovies != null && !remoteMovies.isEmpty()) {
                databaseExecutor.execute(() -> {
                    boolean isIpTv = remoteMovies.get(0).getStudio().equals(Movie.SERVER_IPTV);
                    boolean isKoora = remoteMovies.get(0).getStudio().equals(Movie.SERVER_KOORA_LIVE);
                    for (int i = 0; i < remoteMovies.size(); i++) {
                        Movie movie = remoteMovies.get(i);
                        String videoUrl = (isIpTv || isKoora) ? movie.getVideoUrl() : removeDomain(movie.getVideoUrl());
                        movie.setVideoUrl(videoUrl);
                        Movie existingMovie = null;
                        if (movie.getType() == MovieType.COLLECTION) {
                            existingMovie = movieDao.getMovieByTitleAndStudio(movie.getTitle(), movie.getStudio());
                        }else {
                            existingMovie = movieDao.getMovieByVideoUrlSync(movie.getVideoUrl());
                        }
                        if (existingMovie == null) {
                            if (
                                    movie.getType() == MovieType.SEASON ||
                                    movie.getType() == MovieType.SERIES ||
                                    movie.getType() == MovieType.EPISODE ||
                                    movie.getType() == MovieType.FILM
                            ) {
                                long newId = movieDao.insert(movie);
                                movie.setId(newId);
                            }
                        } else {
                            existingMovie.setTitle(movie.getTitle());
                            existingMovie.setVideoUrl(movie.getVideoUrl());
                            existingMovie.setState(movie.getState());
                            existingMovie.setCardImageUrl(movie.getCardImageUrl());
                            existingMovie.setType(movie.getType());
                            remoteMovies.set(i, existingMovie);
                        }
                    }
                    reAddDomainToMovies(remoteMovies);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onMovieListFetched(remoteCategory, remoteMovies);
                    });
                });
            } else {
                new Handler(Looper.getMainLooper()).post(() -> {
                    callback.onMovieListFetched(remoteCategory, remoteMovies);
                });
            }
        });
    }

    public ArrayList<String> getHomepageCategories() {
        // You should get this list from your ServerConfigRepository or another central place
        // where you define which servers are active.
        // For example:
        return ServerConfigRepository.getInstance().getActiveServerNames();
    }

    public void saveIptvMovies(HashMap<String, ArrayList<Movie>> iptvMovies, final IptvMovieListCallback callback) {
        databaseExecutor.execute(() -> {
            for (Map.Entry<String, ArrayList<Movie>> entry : iptvMovies.entrySet()) {
                ArrayList<Movie> movies = entry.getValue();
                for (int i = 0; i < movies.size(); i++) {
                    Movie movie = movies.get(i);
                    Movie existingMovie = movieDao.getMovieByVideoUrlSync(movie.getVideoUrl());
                    if (existingMovie == null) {
                        if (
                                movie.getType() == MovieType.SEASON ||
                                        movie.getType() == MovieType.SERIES ||
                                        movie.getType() == MovieType.EPISODE ||
                                        movie.getType() == MovieType.FILM
                        ) {
                            long newId = movieDao.insert(movie);
                            movie.setId(newId);
                        }
                    } else {
                        movies.set(i, existingMovie);
                    }
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                callback.onIptvMovieListFetched(iptvMovies);
            });
        });
    }

    public void fetchMovieDetails(Movie mSelectedMovie, ServerInterface.ActivityCallback<Movie> callback) {
        databaseExecutor.execute(() -> {
            boolean isCollection = mSelectedMovie.getType() == MovieType.COLLECTION;


            Log.d(TAG, "fetchMovieDetails: mSelectedMovie type: "+ mSelectedMovie.getType());
            Movie localMselectedMovie = null;
            if (mSelectedMovie.getId() == 0){
                if (isCollection) {
                    localMselectedMovie = movieDao.getMovieByTitleAndStudio(mSelectedMovie.getTitle(), mSelectedMovie.getStudio());
                }else {
                    localMselectedMovie = movieDao.getMovieByVideoUrlSync(removeDomain(mSelectedMovie.getVideoUrl()));
                }
            }
            if (localMselectedMovie == null){
                localMselectedMovie = mSelectedMovie;
            }
            Log.d(TAG, "fetchMovieDetails: localMselectedMovie: "+localMselectedMovie);
//            List<Movie> localSubList = new ArrayList<>();
//            if (localMselectedMovie != null) {
//                reAddDomainToMovie(localMselectedMovie);
//                localSubList = movieDao.getMoviesByParentIdSync(localMselectedMovie.getId());
//                if (!localSubList.isEmpty()){
//                    reAddDomainToMovies(localSubList);
//                    localMselectedMovie.setSubList(localSubList);
//                    Movie finalLocalMselectedMovie = localMselectedMovie;
//                    new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(finalLocalMselectedMovie, finalLocalMselectedMovie.getTitle()));
//                    return;
////                    if (localSubList != null && !localSubList.isEmpty()) {
////                        Log.d(TAG, "fetchMovieDetails: found " + localSubList.size() + " sub-movies in local DB");
//////                    Log.d(TAG, "fetchMovieDetails: localMselectedMovie " + localMselectedMovie);
//////                    reAddDomainToMovies(localSubList);
//////                    if (localMselectedMovie != null) {
//////                         reAddDomainToMovie(localMselectedMovie);
//////                        localMselectedMovie.setSubList(localSubList);
////                        Movie finalLocalMselectedMovie = localMselectedMovie;
////                        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(finalLocalMselectedMovie, finalLocalMselectedMovie.getTitle()));
//////                    } else {
//////                        mSelectedMovie.setSubList(localSubList);
//////                        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(mSelectedMovie, mSelectedMovie.getTitle()));
//////                    }
////                        return;
////                    }
//                }
//
//            }
//            Log.d(TAG, "fetchMovieDetails: localSubList: "+localSubList);
//            if (localMselectedMovie == null){
//                localMselectedMovie = mSelectedMovie;
//            }
//            reAddDomainToMovie(localMselectedMovie);


            // Commenting out the local data check in fetchMovieDetails created a loop for the following reason:
            //
            //   1. UI Requests Data: The details screen (VideoDetailsFragmentController) requests movie details from the MovieRepository to display them.
            //
            //   2. Local Cache Skipped: With the local check commented out, the MovieRepository no longer looks in the local database first. It jumps directly to fetching the data from the remote server every single time
            //      fetchMovieDetails is called.
            //
            //   3. Remote Fetch and DB Save: The app successfully fetches the data from the server, and as part of the success callback, it saves or updates that data in the local database.
            //
            //   4. UI Update: After saving, it notifies the UI (the VideoDetailsFragmentController) that the data has been fetched. The controller then updates the SharedViewModel with this new data.
            //
            //   5. The Loop: The UI is designed to be reactive. When the SharedViewModel is updated with new movie details, the UI observes this change and automatically refreshes itself to display the new data. This
            //      refresh, in turn, triggers the initial data request (step 1) all over again.


            Log.d(TAG, "fetchMovieDetails: no local sub-movies found, fetching from remote");
            remoteDataSource.fetchMovieDetails(localMselectedMovie, new ServerInterface.ActivityCallback<Movie>() {
                @Override
                public void onSuccess(Movie remoteMovie, String title) {
                    if (remoteMovie != null) {
                        databaseExecutor.execute(() -> {

                            remoteMovie.setVideoUrl(removeDomain(remoteMovie.getVideoUrl()));
                            Log.d(TAG, "onSuccess: mSelectedMovie:"+remoteMovie);
                            boolean saveCond = remoteMovie.getType() == MovieType.SERIES ||
                                    remoteMovie.getType() == MovieType.SEASON;

                            if (saveCond && remoteMovie.getId() == 0){
                                long newMovieId = movieDao.insert(remoteMovie);
                                remoteMovie.setId(newMovieId);
                                Log.d(TAG, "onSuccess: saving collection movie: "+remoteMovie);
                                for (Movie subMovie : remoteMovie.getSubList()) {
                                    subMovie.setParentId(newMovieId);
                                }
                            } else {
                                Movie conflictingMovie = movieDao.getMovieByVideoUrlSync(remoteMovie.getVideoUrl());
                                Log.d(TAG, "onSuccess: conflictingMovie: "+ remoteMovie.getVideoUrl());
                                if (conflictingMovie != null && conflictingMovie.getId() != remoteMovie.getId()) {
                                    // Conflict detected. Merge data into conflictingMovie and update it.
                                    conflictingMovie.setTitle(remoteMovie.getTitle());
                                    conflictingMovie.setState(remoteMovie.getState());
                                    conflictingMovie.setType(remoteMovie.getType());
                                    conflictingMovie.setCardImageUrl(remoteMovie.getCardImageUrl());
                                    conflictingMovie.setBackgroundImageUrl(remoteMovie.getBackgroundImageUrl());
                                    conflictingMovie.setSubList(remoteMovie.getSubList());
                                    movieDao.update(conflictingMovie);
                                    // Update remoteMovie's ID to match the one we just updated.
                                    remoteMovie.setId(conflictingMovie.getId());
                                } else {
                                    // No conflict, or conflict is with the same movie, so update is safe.
                                    movieDao.update(remoteMovie);
                                }
                            }

                            if (saveCond){
                                for (int i = 0; i < remoteMovie.getSubList().size(); i++) {
                                    Movie movie = remoteMovie.getSubList().get(i);
                                    movie.setVideoUrl(removeDomain(movie.getVideoUrl()));
                                    Movie existingMovie = movieDao.getMovieByVideoUrlSync(movie.getVideoUrl());
                                    if (existingMovie == null) {
                                       long newId = movieDao.insert(movie);
                                       movie.setId(newId);
                                    } else {
                                        existingMovie.setTitle(movie.getTitle());
                                        existingMovie.setVideoUrl(movie.getVideoUrl());
                                        existingMovie.setState(movie.getState());
                                        existingMovie.setType(movie.getType());
                                        remoteMovie.getSubList().set(i, existingMovie);
                                    }
                                }
                                reAddDomainToMovie(remoteMovie);
                                reAddDomainToMovies(remoteMovie.getSubList());
                            }
                            new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(remoteMovie, title));
                        });
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(mSelectedMovie, mSelectedMovie.getTitle()));
                    }
                }

                @Override
                public void onInvalidCookie(Movie result, String title) {
                    Log.d(TAG, "onInvalidCookie: MovieRepo fetchMovieDetails");
                    new Handler(Looper.getMainLooper()).post(() -> callback.onInvalidCookie(result, title));
                }

                @Override
                public void onInvalidLink(Movie result) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onInvalidLink(result));
                }

                @Override
                public void onInvalidLink(String message) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onInvalidLink(message));
                }
            });
        });
    }

    public LiveData<List<Movie>> getMoviesByType(MovieType type) {
        return Transformations.map(movieDao.getMoviesByType(type), movies -> {
            reAddDomainToMovies(movies);
            return movies;
        });
    }

    public LiveData<List<Movie>> getMoviesByParentId(long parentId) {
        return Transformations.map(movieDao.getMoviesByParentId(parentId), movies -> {
            reAddDomainToMovies(movies);
            return movies;
        });
    }

    public void updateWatchedTime(Long movieId, long watchedTime) {
        databaseExecutor.execute(() -> {
            if (movieId == null || movieId == 0) {
                Log.d(TAG, "updateWatchedTime: Error invalid movie with id: " + movieId);
                return;
            }
            Movie movie = movieDao.getMovieById(movieId);
            if (movie == null) {
                Log.d(TAG, "updateWatchedTime: Error movie with id: " + movieId + " doesn't exist");
                return;
            }

            updateMovieHistory(movie, watchedTime);

            if (movie.getType() != MovieType.EPISODE) {
                return;
            }

            Movie episode = movie;
            long episodeLength = episode.getMovieLength();
            if (episodeLength > 0) {
                long episodePlayedTime = (watchedTime * 100) / episodeLength;
                episode.setPlayedTime(episodePlayedTime);
                episode.setUpdatedAt(new Date());
                movieDao.update(episode);
            }

            Long seasonId = episode.getParentId();
            if (seasonId != null && seasonId != 0) {
                Movie season = movieDao.getMovieById(seasonId);
                if (season != null && season.getType() == MovieType.SEASON) {
                    List<Movie> episodes = movieDao.getMoviesByParentIdSync(seasonId);
                    if (episodes != null && !episodes.isEmpty()) {
                        long totalPlayedTime = 0;
                        for (Movie ep : episodes) {
                            totalPlayedTime += ep.getPlayedTime();
                        }
                        long seasonPlayedTime = totalPlayedTime / episodes.size();
                        season.setPlayedTime(seasonPlayedTime);
                        season.setUpdatedAt(new Date());
                        movieDao.update(season);

                        Long seriesId = season.getParentId();
                        if (seriesId != null && seriesId != 0) {
                            Movie series = movieDao.getMovieById(seriesId);
                            if (series != null && series.getType() == MovieType.SERIES) {
                                List<Movie> seasons = movieDao.getMoviesByParentIdSync(seriesId);
                                if (seasons != null && !seasons.isEmpty()) {
                                    long totalSeasonPlayedTime = 0;
                                    for (Movie s : seasons) {
                                        totalSeasonPlayedTime += s.getPlayedTime();
                                    }
                                    long seriesPlayedTime = totalSeasonPlayedTime / seasons.size();
                                    series.setPlayedTime(seriesPlayedTime);
                                    series.setUpdatedAt(new Date());
                                    movieDao.update(series);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private void updateMovieHistory(Movie movie, long watchedTime) {
        MovieHistory movieHistory = movieHistoryDao.getMovieHistoryByMovieIdSync(movie.getId());
        if (movieHistory == null) {
            movieHistory = new MovieHistory(movie.getId(), watchedTime, new Date());
        } else {
            movieHistory.setWatchedPosition(watchedTime);
            movieHistory.setLastWatchedDate(new Date());
        }
        movieHistoryDao.insert(movieHistory);
    }

    public void saveMovieHistory(MovieHistory movieHistory) {
        databaseExecutor.execute(() -> movieHistoryDao.insert(movieHistory));
    }

    public LiveData<MovieHistory> getMovieHistoryByMovieIdLive(long movieId) {
        return movieHistoryDao.getMovieHistoryByMovieIdLive(movieId);
    }

    public MovieHistory getMovieHistoryByMovieIdSync(long movieId) {
        return movieHistoryDao.getMovieHistoryByMovieIdSync(movieId);
    }

    public Movie getMovieByIdSync(long id) {
        return movieDao.getMovieById(id);
    }

    public void setMovieIsHistory(long movieId) {
        databaseExecutor.execute(() -> {
            movieDao.setMovieIsHistory(movieId);
        });
    }

    public void getWatchedMovies(MovieListCallback callback) {
        ArrayList<Movie> movies = (ArrayList<Movie>)movieDao.getWatchedMovies(Movie.SERVER_IPTV);
        reAddDomainToMovies(movies);
        callback.onMovieListFetched(
                "المحفوظات",
                movies
        );
    }

    public void getWatchedChannels(MovieListCallback callback) {
        ArrayList<Movie> movies = (ArrayList<Movie>)movieDao.getWatchedChannels(Movie.SERVER_IPTV);
        callback.onMovieListFetched(
                "محفوظات القنوات",
                movies
        );
    }

    public void deleteMovie(Movie movie) {
        databaseExecutor.execute(() -> movieDao.delete(movie));
    }

    public void updateMovieLength(long id, long movieLength) {
        databaseExecutor.execute(() -> {
            movieDao.updateMovieLength(id, movieLength);
            Movie movie = movieDao.getMovieById(id);
            if (movie != null && movie.getType() == MovieType.RESOLUTION) {
                Long parentId = movie.getParentId();
                if (parentId != null && parentId != 0) {
                    movieDao.updateMovieLength(parentId, movieLength);
                }
            }
        });
    }

    public void getHomepageChannels(MovieListCallback callback) {
        Log.d(TAG, "getHomepageChannels: Started.");
        databaseExecutor.execute(() -> {
            List<String> groups = new ArrayList<>();
            groups.add("shahid");
            groups.add("أم بي سي");
            groups.add("Syria");
            groups.add("الاخبار");
            groups.add("اسلامية");
            groups.add("Germany");
            groups.add("المجد");
//            groups.add("doc");
            groups.add("Children");

            List<Movie> movies = movieDao.getMoviesByStudioAndGroups(Movie.SERVER_IPTV, groups);
            Log.d(TAG, "getHomepageChannels: Found " + (movies == null ? "null" : movies.size()) + " movies in DB.");

            if (movies == null || movies.isEmpty()) {
//            if (true) {
                Log.d(TAG, "getHomepageChannels: DB is empty, calling loadDefaultChannels.");
                loadDefaultChannels((category, fetchedMovies) -> {
                    if (fetchedMovies == null || fetchedMovies.isEmpty()) {
                        return; // Nothing to do
                    }
                    Log.d(TAG, "getHomepageChannels: movies fetched, next is to save to db");
                    // Save movies to DB
                    for (int i = 0; i < fetchedMovies.size(); i++) {
                        Movie movie = fetchedMovies.get(i);
                        Movie existingMovie = movieDao.getMovieByVideoUrlSync(movie.getVideoUrl());
                        if (existingMovie == null) {
                            long newId = movieDao.insert(movie);
                            movie.setId(newId);
                        } else {
                            fetchedMovies.set(i, existingMovie);
                        }
                    }

                    Log.d(TAG, "getHomepageChannels: saved to db next update ui");

                    // Group and post to UI
                    Map<String, List<Movie>> moviesByGroup = new HashMap<>();
                    for (Movie movie : fetchedMovies) {
                        if (movie.getGroup() != null && groups.contains(movie.getGroup())) {
                            if (!moviesByGroup.containsKey(movie.getGroup())) {
                                moviesByGroup.put(movie.getGroup(), new ArrayList<>());
                            }
                            moviesByGroup.get(movie.getGroup()).add(movie);
                        }
                    }

                    new Handler(Looper.getMainLooper()).post(() -> {
                        for (Map.Entry<String, List<Movie>> entry : moviesByGroup.entrySet()) {
                            callback.onMovieListFetched(entry.getKey(), new ArrayList<>(entry.getValue()));
                        }
                    });
                });
            }
            else {
                // DB has movies, just group and post
                Log.d(TAG, "getHomepageChannels: grouping movies ");
                Map<String, List<Movie>> moviesByGroup = new HashMap<>();
                for (Movie movie : movies) {
                    if (movie.getGroup() != null) {
                        if (!moviesByGroup.containsKey(movie.getGroup())) {
                            moviesByGroup.put(movie.getGroup(), new ArrayList<>());
                        }
                        moviesByGroup.get(movie.getGroup()).add(movie);
                    }
                }
                Log.d(TAG, "getHomepageChannels: movies are grouped in :"+ moviesByGroup.size());
                new Handler(Looper.getMainLooper()).post(() -> {
                    for (Map.Entry<String, List<Movie>> entry : moviesByGroup.entrySet()) {
                        Log.d(TAG, "getHomepageChannels: updating ui: "+ entry.getKey());
                        callback.onMovieListFetched(entry.getKey(), new ArrayList<>(entry.getValue()));
                    }
                });
            }
        });
    }

    public void getSearchMovies(String query, MovieListCallback callback) {
        Log.d(TAG, "getSearchMovies: ");
        ServerConfigRepository repository = ServerConfigRepository.getInstance();
        new Handler(Looper.getMainLooper()).post(() -> {
            repository.getIsInitialized().observeForever(new androidx.lifecycle.Observer<Boolean>() {
                @Override
                public void onChanged(Boolean isInitialized) {
                    if (isInitialized != null && isInitialized) {
                        repository.getIsInitialized().removeObserver(this);

                        ExecutorService executor = Executors.newCachedThreadPool();
                        executor.submit(() -> {
                            List<ServerConfig> configs = repository.getAllActiveConfigsList();
                            for (ServerConfig config : configs) {
//                                if (
////                                        config.getName().equals(Movie.SERVER_OLD_AKWAM)
////                                        !config.getName().equals(Movie.SERVER_FASELHD)
//                                        !config.getName().equals(Movie.SERVER_MyCima)
////                                        !config.getName().equals(Movie.SERVER_ARAB_SEED)
//                                ){
//                                    // Latch is removed, just skip this server.
//                                    continue;
//                                }

                                ExecutorService executor2 = Executors.newCachedThreadPool();
                                executor2.submit(() -> {
                                    getSearchMoviesOfServer(false, config, query, callback);
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

    public void getSearchMoviesOfServer(boolean handleCookie, ServerConfig config, String query, MovieListCallback callback) {
        ExecutorService executor = Executors.newCachedThreadPool();
        executor.submit(() -> {
                AbstractServer server = ServerFactory.createServer(config.getName());
                if (server == null) {
                    return;
                }
                Log.d(TAG, "getSearchMoviesOfServer: config: " + config.getName() + ", " + server.getLabel());
                server.search(query, new ServerInterface.ActivityCallback<ArrayList<Movie>>() {
            @Override
            public void onSuccess(ArrayList<Movie> remoteMovies, String remoteCategory) {
                Log.d(TAG, "onSuccess: " + remoteCategory);
                if (remoteMovies != null && !remoteMovies.isEmpty()) {
                    databaseExecutor.execute(() -> {
                        boolean isIpTv = remoteMovies.get(0).getStudio().equals(Movie.SERVER_IPTV);
                        for (int i = 0; i < remoteMovies.size(); i++) {
                            Movie movie = remoteMovies.get(i);
                            String videoUrl = isIpTv ? movie.getVideoUrl() : removeDomain(movie.getVideoUrl());
                            movie.setVideoUrl(videoUrl);
                            Movie existingMovie = movieDao.getMovieByVideoUrlSync(movie.getVideoUrl());

                            if (existingMovie == null) {
                                if (
                                        movie.getType() == MovieType.SEASON ||
                                                movie.getType() == MovieType.SERIES ||
                                                movie.getType() == MovieType.EPISODE ||
                                                movie.getType() == MovieType.FILM
                                ) {
                                    long newId = movieDao.insert(movie);
                                    movie.setId(newId);
                                }
                            } else {
                                remoteMovies.set(i, existingMovie);
                            }
                        }
                        reAddDomainToMovies(remoteMovies);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            callback.onMovieListFetched(remoteCategory, remoteMovies);
                        });
                    });
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onMovieListFetched(remoteCategory, remoteMovies);
                    });
                }
            }

            @Override
            public void onInvalidCookie(ArrayList<Movie> result, String title) {
                Log.d(TAG, "onInvalidCookie: from " + config.getName());
                callback.onMovieListFetched(title, result);
            }

            @Override
            public void onInvalidLink(ArrayList<Movie> result) {
                Log.d(TAG, "onInvalidLink: from " + config.getName());
            }

            @Override
            public void onInvalidLink(String message) {
                Log.d(TAG, "onInvalidLink from " + config.getName() + ": " + message);
            }
        }, handleCookie);

            });
            executor.shutdown();
    }

    public void getSearchIptvMovies(String query, MovieListCallback callback) {
        ArrayList<Movie> movies = (ArrayList<Movie>)movieDao.getSearchIptvMovies(query);
        callback.onMovieListFetched(
                "القنوات",
                movies
        );
    }

    private interface OnMoviesFetchedCallback {
        void onFetched(List<Movie> movies);
    }

    private void loadDefaultChannels(MovieListCallback callback) {
        Log.d(TAG, "loadDefaultChannels: Started.");
        AbstractServer server = ServerFactory.createServer(Movie.SERVER_IPTV);

        IptvServer iptvServer = (IptvServer) server;
        iptvServer.loadDefaultChannels(callback);
    }


    public interface MovieCallback {
        void onMovieFetched(Movie movie);
    }

    public interface MovieListCallback {
        void onMovieListFetched(String category, ArrayList<Movie> movies);
    }

    public interface IptvMovieListCallback {
        void onIptvMovieListFetched(HashMap<String, ArrayList<Movie>> movies);
    }
}
