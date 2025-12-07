package com.omarflex5.temp.omerflex.service.receiver;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.omerflex.entity.Movie;
import com.omerflex.server.Util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class TvLinkReceiver implements Runnable {
    private static final int PORT = 8080;
    private Context context;  // Pass this to trigger playback

    public TvLinkReceiver(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String videoLink = in.readLine();  // Read the link sent by phone
                in.close();
                clientSocket.close();

                // Handle playback on UI thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    // Assuming you have a method to play video
                    playVideo(videoLink);
                });
            }
        } catch (Exception e) {
            Log.e("Server", "Error: " + e.getMessage());
        }
    }

    private void playVideo(String link) {
        // Implement your video playback logic here, e.g., using ExoPlayer
        // For example: Intent to start a VideoActivity with the link as extra
        Movie movie = new Movie();
        movie.setStudio(Movie.SERVER_FASELHD);
        movie.setTitle("test receiver");
        movie.setVideoUrl(link);
        Util.openExoPlayer(movie, (Activity) context, false);
    }
}
