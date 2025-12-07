package com.omarflex5.temp.omerflex.service;

import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.Player;

import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieHistory;
import com.omerflex.entity.MovieRepository;
import com.omerflex.entity.MovieType;

public class PlaybackHistoryManager {

    private final MovieRepository movieRepository;
    private final Movie movie;
    private boolean hasSeekedToWatchedPosition = false;

    public PlaybackHistoryManager(MovieRepository movieRepository, Movie movie) {
        this.movieRepository = movieRepository;
        this.movie = movie;
    }

    public void savePlaybackPosition(Player player) {
        if (player == null || movie == null || movie.getParentId() == null) {
            return;
        }
        long watchedPosition = player.getCurrentPosition();
        movieRepository.updateWatchedTime(movie.getParentId(), watchedPosition);
    }

    public void restorePlaybackPosition(Player player) {
        if (hasSeekedToWatchedPosition || player == null || movie == null || movie.getParentId() == null) {
            return;
        }

        long movieLength = player.getDuration();
        if (movieLength <= 0) {
            return;
        }

        hasSeekedToWatchedPosition = true;
        new Thread(() -> {
            movieRepository.updateMovieLength(movie.getParentId(), movieLength);
            MovieHistory history = movieRepository.getMovieHistoryByMovieIdSync(movie.getParentId());

            if (history != null) {
                long watchedPosition = history.getWatchedPosition();
                // Post the seek operation to the main thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (watchedPosition > 0 && (watchedPosition * 100) / movieLength < 95) {
                        player.seekTo(watchedPosition);
                    }
                });
            }
        }).start();
    }

    public void markAsWatched() {
        if (movie == null) {
            return;
        }
        new Thread(() -> {
            if (movie.getStudio().equals(Movie.SERVER_IPTV)) {
                movieRepository.setMovieIsHistory(movie.getId());
                return;
            }
            if (movie.getParentId() == null) {
                return;
            }
            Movie parentMovie = movieRepository.getMovieByIdSync(movie.getParentId());
            if (parentMovie != null) {
                if (parentMovie.getType() == MovieType.FILM) {
                    movieRepository.setMovieIsHistory(parentMovie.getId());
                } else if (parentMovie.getType() == MovieType.EPISODE) {
                    Movie season = movieRepository.getMovieByIdSync(parentMovie.getParentId());
                    if (season != null) {
                        movieRepository.setMovieIsHistory(season.getParentId() != null ? season.getParentId() : season.getId());
                    }
                }
            }
        }).start();
    }
}
