package com.omarflex5.temp.omerflex.view;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SimpleExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.leanback.LeanbackPlayerAdapter;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouter;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.omerflex.R;
import com.omerflex.db.AppDatabase;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieHistory;
import com.omerflex.entity.MovieRepository;
import com.omerflex.entity.MovieType;
import com.omerflex.providers.DlnaCaster;
import com.omerflex.providers.MediaServer;
import com.omerflex.providers.SsdpDiscoverer;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@androidx.media3.common.util.UnstableApi
public class ExoplayerMediaPlayer_old22 extends AppCompatActivity implements SessionAvailabilityListener {

    @Nullable private ExoPlayer player;
    private static final String TAG = "Exoplayer";

    MovieRepository movieRepository;
    PlayerView playerView;
    private long backPressedTime;
    Movie movie;

    // url of video which we are loading.

    LeanbackPlayerAdapter leanbackPlayerAdapter;

    //ontouch
    public long MAX_SEEK_DURATION_MS = 2000; // 20 seconds
    private static final long MAX_VIDEO_DURATION = 7200; // 2 hours
    public long videoDuration; // The duration of the video in milliseconds
    public static long SEEK_DURATION_MS = 1000;
    public static long CONNECTION_TIMEOUT = 5000;
    private static final float MIN_SEEK_DISTANCE = 100; //  pixels
    public static float initialX = 0;
    private Map<String, String> headers;
    private boolean hasSeekedToWatchedPosition = false;

    private SimpleExoPlayer exoPlayer;
    private CastPlayer castPlayer;
    private Player currentPlayer;

    private CastContext mCastContext;
    private CastStateListener mCastStateListener;
    private MenuItem mediaRouteMenuItem;
    private Map<String, SsdpDiscoverer.DlnaDevice> deviceLocations = new HashMap<>();




    private MediaRouter mMediaRouter;
    private MediaRouter.Callback mMediaRouterCallback;
    private boolean mCastDevicesAvailable = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // remove title
        // Hide the status bar.
        // Hide status bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        this.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_exoplayer);
        getSupportActionBar().hide();

        playerView = findViewById(R.id.player_view);
        AppDatabase db = AppDatabase.getDatabase(this);
        movieRepository = MovieRepository.getInstance(this, db.movieDao());
        movie = com.omerflex.server.Util.recieveSelectedMovie(getIntent());

        // It's crucial to initialize the CastContext. This is a heavy operation,
        // and the framework does it lazily in a background thread.
        // It uses the OptionsProvider we defined in the Manifest.
        try {
            mCastContext = CastContext.getSharedInstance(this);
        } catch (RuntimeException e) {
            // This can happen if the Google Play services are out of date or missing.
            Log.e(TAG, "Failed to get CastContext instance. Is Google Play Services available?");
            // You could show an error dialog to the user here.
            return;
        }

        mMediaRouter = MediaRouter.getInstance(this);
        mMediaRouterCallback = new MediaRouter.Callback() {
            @Override
            public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo route) {
                updateCastDeviceAvailability();
            }
            @Override
            public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
                updateCastDeviceAvailability();
            }
            @Override
            public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
                updateCastDeviceAvailability();
            }
        };

        // The Cast button will automatically become visible or hidden based on this.
        mCastStateListener = newState -> {
            if (newState == CastState.NO_DEVICES_AVAILABLE) {
                Log.d(TAG, "CastState: No devices available.");
            }
            else if (newState == CastState.NOT_CONNECTED) {
                Log.d(TAG, "CastState: Devices available, but not connected.");
            }
            else if (newState == CastState.CONNECTING) {
                Log.d(TAG, "CastState: Connecting to a device.");
            }
            else if (newState == CastState.CONNECTED) {
                Log.d(TAG, "CastState: Connected to a device.");
            }
        };

        //  setContentView(R.layout.activity_main);
        TextView customTitle = playerView.findViewById(R.id.custom_exo_title);
        if (customTitle != null) {
            if (movie != null && movie.getTitle() != null) {
                customTitle.setText(movie.getTitle());
            } else {
                customTitle.setText("No Title");
            }
        }

        // The title will be set dynamically by a player listener.

        // Manually handle the cast button to show a unified picker
//        MediaRouteButton mediaRouteButton = playerView.findViewById(R.id.media_route_button);
//        if (mediaRouteButton != null) {
//            mediaRouteButton.setOnClickListener(v -> showCastOrDlnaDialog());
//        }

        ImageView shareButton = playerView.findViewById(R.id.share_button);
        if (shareButton != null) {
            shareButton.setOnClickListener(v -> showCastOrDlnaDialog());
        }


//        player = new ExoPlayer.Builder(getApplicationContext()).build();
        // player.setPlayWhenReady(true);
        //leanbackPlayerAdapter = new LeanbackPlayerAdapter(this, player, 0);

        leanbackPlayerAdapter = new LeanbackPlayerAdapter(this.getApplicationContext(), player, 16);

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
//                if (webView.canGoBack()) {
//                    webView.stopLoading(); // Stop loading the page
//                    webView.goBack();      // Navigate back in WebView's history
//                } else {
//                    // If there's no history, close the activity
//                    finish();
//                }
                handleBackPressed();
            }
        };

        // Add the callback to the back pressed dispatcher
        getOnBackPressedDispatcher().addCallback(this, callback);


        // increase audio buffer
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                .build();


        player = new ExoPlayer.Builder(getApplicationContext())
                .setLoadControl(loadControl)
                .build();

        castPlayer = new CastPlayer(mCastContext);
        castPlayer.setSessionAvailabilityListener(this);
        castPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "CastPlayer error: " + error.getMessage(), error);
                runOnUiThread(() -> {
                    Toast.makeText(ExoplayerMediaPlayer_old22.this,
                            "Cast playback failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d(TAG, "CastPlayer state changed: " + playbackState);
            }
        });


        // give audio focus for iptv live videos
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(), true);



        //Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {

                    public X509Certificate[] getAcceptedIssuers()
                    {
                        Log.d(TAG, "getAcceptedIssuers: ");
                        return new X509Certificate[0];
//                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType)
                    {
                        Log.d(TAG, "checkClientTrusted: ");
                        //
                    }
                    public void checkServerTrusted(X509Certificate[] certs, String authType)
                    {
                        Log.d(TAG, "checkServerTrusted: "+ movie.getVideoUrl());
                        //
                    }
                }
        };

//Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            Log.d(TAG, "onCreate: error: "+e.getMessage());
            e.printStackTrace();
        }

        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
        player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        playerView.setPlayer(player);

        // CAST INTEGRATION: Determine which player to use initially and start playback
        setCurrentPlayer(castPlayer.isCastSessionAvailable() ? castPlayer : player);


// Set the media source to be played.
     //   player.setMediaSource(createMediaSource(movie));

//        DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory();
//
//        DataSource.Factory dataSourceFactory = () -> {
//            HttpDataSource dataSource = httpDataSourceFactory.createDataSource();
//            // Set a custom authentication request header.
//            String[] parts = movie.getVideoUrl().split("\\|");
//            for (String part : parts) {
//                if (part.contains("=")) {
//                    String[] keyValue = part.split("=");
//                    String key = keyValue[0];
//                    String value = keyValue[1];
//                    dataSource.setRequestProperty(key, value);
//                }
//            }
//            movie.setVideoUrl(movie.getVideoUrl().substring(0, movie.getVideoUrl().indexOf('|')));
//            Log.d("TAG", "dataSource:exoplayer "+movie.getVideoUrl());
//            return dataSource;
//        };
//        MediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
//                .createMediaSource(Uri.parse(movie.getVideoUrl()));

//
//        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
//                .createMediaSource(Uri.parse(movie.getVideoUrl()));
//        player.setMediaSource(mediaSource);


//hier        player.setMediaItem(createMediaSource(movie));
//
//// Prepare the player.
//        player.prepare();
        MediaSource mediaSource = buildMediaSource(movie);

        player.prepare(mediaSource);
        Log.d(TAG, "onCreate: player.prepare(mediaSource) ");

        playerView.setControllerAutoShow(false);


        // Set the touch listener for the view that displays the ExoPlayer
        playerView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Log.d(TAG, "onTouch: ");
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // Save the initial x position of the touch event
                    ExoplayerMediaPlayer_old22.initialX = event.getX();
                    Log.d("player", "onTouch:"+initialX+" ACTION_DOWN");

                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    Log.d("player", "onTouch:"+initialX+" ACTION_MOVE "+event.getX());

                    // Update the current x position of the touch event
                    float currentX = event.getX();

                    // Do something with the deltaX value, such as updating a visual indication of the seek position
                    if (Math.abs(event.getX()) > initialX + MIN_SEEK_DISTANCE) {
                        // Seek forward or backward by 15 seconds if the touch moved a certain distance
                        player.seekTo(player.getCurrentPosition() + SEEK_DURATION_MS);
                    }
                    if (Math.abs(event.getX()) < initialX - MIN_SEEK_DISTANCE) {
                        // Seek forward or backward by 15 seconds if the touch moved a certain distance
                        player.seekTo(player.getCurrentPosition() - SEEK_DURATION_MS);
                    }
                    return true;
                }else if (event.getAction() == MotionEvent.ACTION_UP) {
                    // Calculate the difference between the initial and final x position of the touch event
                    Log.d("player", "onTouch:"+event.getX()+" ACTION_UP "+initialX);
                    ExoplayerMediaPlayer_old22.initialX = 0;
                    if (!playerView.isControllerFullyVisible()){
                        playerView.showController();
                    }
                    return true;
                }
                return true;
            }
        });

        player.addAnalyticsListener(new AnalyticsListener(){
            @Override
            public void onAudioSinkError(EventTime eventTime, Exception audioSinkError) {
                Log.d(TAG, "onAudioSinkError: "+audioSinkError.getMessage());
//                AnalyticsListener.super.onAudioSinkError(eventTime, audioSinkError);
                MediaSource mediaSource = buildMediaSource(movie);
                player.prepare(mediaSource);
                player.play();
            }
        });
        player.addListener(new Player.Listener() {
            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                Log.d(TAG, "onTimelineChanged: " + reason);
                if (player.getDuration() != C.TIME_UNSET) {
                    updateHistoryPlayback();
                    if (!hasSeekedToWatchedPosition && movie.getParentId() != null) {
                        long movieLength = player.getDuration();
                        hasSeekedToWatchedPosition = true; // Set it here to only try once.
                        new Thread(() -> {
                            movieRepository.updateMovieLength(movie.getParentId(), movieLength);

                            // --- Start Gemini Debug Logging ---
                            Object parentIdObj = movie.getParentId();
                            Log.d(TAG, "DEBUG: parentId object is: " + parentIdObj);
                            if (parentIdObj != null) {
                                Log.d(TAG, "DEBUG: parentId class is: " + parentIdObj.getClass().getName());
                                Log.d(TAG, "DEBUG: parentId value is: '" + parentIdObj + "'");
                            }
                            // --- End Gemini Debug Logging ---

                            MovieHistory history = movieRepository.getMovieHistoryByMovieIdSync(movie.getParentId());
                            Log.d(TAG, "onTimelineChanged: parentId: "+ movie.getParentId() );
                            Log.d(TAG, "onTimelineChanged: movieId: "+ movie.getId() );
                            Log.d(TAG, "onTimelineChanged: history: "+ history );
                            if (history != null) {
                                runOnUiThread(() -> {
                                    long watchedPosition = history.getWatchedPosition();
                                    Log.d(TAG, "onTimelineChanged: watchedPosition: "+ watchedPosition + ", length: "+ movieLength);
                                    if (watchedPosition > 0 && movieLength > 0) {
                                        long percentage = (watchedPosition * 100) / movieLength;
                                        if (percentage < 95) {
                                            player.seekTo(watchedPosition);
                                            Log.d(TAG, "onTimelineChanged: seeked to: "+ watchedPosition);

                                        }
                                    }
                                });
                            }
                        }).start();
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
//            public void onPlayerError(ExoPlaybackException error) {
                Log.d("TAG", "onPlayerError: xxxx:"+error.getMessage()+", "+error.errorCode+", "+ error.toString()+ ", "+movie.getVideoUrl());

                int c = error.errorCode;
                if (c == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ){
                    String studio = movie.getStudio();
                    if (studio.equals(Movie.SERVER_OMAR) || studio.equals(Movie.SERVER_IPTV)){
                        MediaSource mediaSource = buildMediaSource(movie);
                        player.prepare(mediaSource);
                        player.play();
                        return;
                    }
                }


                boolean deleteCond = c == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                        || c == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                        || c == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        || c == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                        || c == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
                       // || c == PlaybackException.;
                if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED){
                    deleteCond = error.getCause() != null && error.getCause().getMessage() != null && error.getCause().getMessage().contains("verified");
                }
                if (deleteCond){
                    Log.d("TAG", "onPlayerError: movie deleted: "+movie.toString());
                    movieRepository.deleteMovie(movie);
                }
                Toast.makeText(ExoplayerMediaPlayer_old22.this, "فشل في تشغيل الرابط", Toast.LENGTH_SHORT).show();
                Player.Listener.super.onPlayerError(error);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d(TAG, "onPlaybackStateChanged: xxxx:"+playbackState);
               String studio = movie.getStudio();
                if (!studio.equals(Movie.SERVER_IPTV)
                && !studio.equals(Movie.SERVER_OMAR)){
                    return;
                }
                if (playbackState == Player.STATE_READY){
                    Log.d(TAG, "onPlaybackStateChanged: xxxx: STATE_READY");
//                    player.play();
                    return;
//                    dbHelper.addMovieToHistory(movie, false);
//                    if (player != null && player.getCurrentPosition() == 0){
//                      //  player.seekTo(movie.getPlayedTime());
//                    }
                }
                if (playbackState == Player.STATE_ENDED){
                    Log.d(TAG, "onPlaybackStateChanged: xxxx: STATE_ENDED");
                    MediaSource mediaSource = buildMediaSource(movie);
                    player.prepare(mediaSource);
                    player.play();
                    return;
//                    dbHelper.addMovieToHistory(movie, false);
//                    if (player != null && player.getCurrentPosition() == 0){
//                      //  player.seekTo(movie.getPlayedTime());
//                    }
                }
                Player.Listener.super.onPlaybackStateChanged(playbackState);
            }
        });

        //  player.play()
        playerView.setKeepScreenOn(true);
        player.play();
        //   player.play();

    }



    private void showDiscoveredDevices() {
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
                .setTitle("Discovering DLNA Devices...")
                .setMessage("Searching for devices on your network...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        deviceLocations.clear(); // Clear previous devices

//        SsdpDiscoverer.discoverDevicesWithDetails(new SsdpDiscoverer.DiscoveryListener() {
//            @Override
//            public void onDeviceFound(SsdpDiscoverer.DlnaDevice device) {
//                Log.d(TAG, "Discovered device: " + device.friendlyName);
//                // Store device info for later use
//                deviceLocations.put(device.toString(), device);
//            }
//
//            @Override
//            public void onDiscoveryComplete(List<String> devices) {
//                runOnUiThread(() -> {
//                    loadingDialog.dismiss();
//                    showDiscoveredDevicesDialog(devices);
//                });
//            }
//
//            @Override
//            public void onError(String error) {
//                runOnUiThread(() -> {
//                    loadingDialog.dismiss();
//                    showDiscoveryError(error);
//                });
//            }
//        });
    }

    private void onDlnaDeviceSelected(String deviceInfo) {
        SsdpDiscoverer.DlnaDevice selectedDevice = deviceLocations.get(deviceInfo);
        if (selectedDevice == null || selectedDevice.location == null) {
            Toast.makeText(this, "Device info not found", Toast.LENGTH_SHORT).show();
            return;
        }
        castToDlnaLocation(deviceInfo, selectedDevice.location);
    }

    private void castToDlnaLocation(String deviceName, String locationUrl) {
        AlertDialog castingDialog = new AlertDialog.Builder(this)
                .setTitle("Casting to " + deviceName)
                .setMessage("Setting up media server...")
                .setCancelable(false)
                .create();
        castingDialog.show();

        // Extract headers from video URL
        String url = movie.getVideoUrl();
        String[] parts = url.split("\\|", 2);
        Map<String, String> headers = new HashMap<>();
        if (parts.length == 2) {
            headers = com.omerflex.server.Util.extractHeaders(parts[1]);
        }

        // Start media server
        MediaServer mediaServer = MediaServer.getInstance();
        String localServerUrl = mediaServer.startServer(movie, headers);

        if (localServerUrl == null) {
            castingDialog.dismiss();
            Toast.makeText(this, "Failed to start media server", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Media server URL: " + localServerUrl);
        Log.d(TAG, "Trying to cast to: " + locationUrl);

        DlnaCaster.castToDevice(locationUrl, localServerUrl, movie.getTitle(), new DlnaCaster.CastListener() {
            @Override
            public void onCastSuccess() {
                runOnUiThread(() -> {
                    castingDialog.dismiss();
                    Toast.makeText(ExoplayerMediaPlayer_old22.this,
                            "Casting started successfully to " + deviceName,
                            Toast.LENGTH_LONG).show();

                    // Save the device for next time
                    android.content.SharedPreferences prefs = getSharedPreferences("dlna_prefs", MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("last_dlna_name", deviceName);
                    editor.putString("last_dlna_location", locationUrl);
                    editor.apply();

                    // Pause local playback
                    if (player != null && player.isPlaying()) {
                        player.pause();
                    }
                });
            }

            @Override
            public void onCastError(String error) {
                runOnUiThread(() -> {
                    castingDialog.dismiss();
                    Toast.makeText(ExoplayerMediaPlayer_old22.this, "Cast failed: " + error, Toast.LENGTH_LONG).show();
                    MediaServer.getInstance().stopServer();
                });
            }
        });
    }



    private void showDiscoveredDevicesDialog(List<String> devices) {
        if (devices.isEmpty()) {
            showNoDevicesFound();
            return;
        }

        String[] deviceArray = devices.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Discovered DLNA Devices (" + devices.size() + ")")
                .setItems(deviceArray, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selectedDevice = devices.get(which);
                        onDlnaDeviceSelected(selectedDevice);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDiscoveryError(String error) {
        new AlertDialog.Builder(this)
                .setTitle("Discovery Error")
                .setMessage("Failed to discover devices: " + error + "\n\nMake sure you're connected to Wi-Fi.")
                .setPositiveButton("OK", null)
                .show();
    }


    // Helper methods for debugging
    private String getConnectionState(MediaRouter.RouteInfo route) {
        try {
            int state = route.getConnectionState();
            switch (state) {
                case MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED: return "CONNECTED";
                case MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING: return "CONNECTING";
                case MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED: return "DISCONNECTED";
                default: return "UNKNOWN(" + state + ")";
            }
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private String getDeviceType(MediaRouter.RouteInfo route) {
        try {
            int type = route.getDeviceType();
            switch (type) {
                case MediaRouter.RouteInfo.DEVICE_TYPE_TV: return "TV";
                case MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER: return "SPEAKER";
//                case MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH: return "BLUETOOTH";
                case MediaRouter.RouteInfo.DEVICE_TYPE_UNKNOWN: return "UNKNOWN";
                default: return "OTHER(" + type + ")";
            }
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private void showDevicesDialog(List<MediaRouter.RouteInfo> devices) {
        String[] deviceNames = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            MediaRouter.RouteInfo device = devices.get(i);
            String deviceInfo = device.getName();

            // Add type information
            String type = getDeviceType(device);
            String state = getConnectionState(device);
            deviceInfo += " [" + type + " - " + state + "]";

            if (device.getDescription() != null && !device.getDescription().isEmpty()) {
                deviceInfo += "\n" + device.getDescription();
            }

            deviceNames[i] = deviceInfo;
        }

        new AlertDialog.Builder(this)
                .setTitle("All Discovered Routes (" + devices.size() + ")")
                .setItems(deviceNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        MediaRouter.RouteInfo selectedDevice = devices.get(which);
                        onDeviceSelected(selectedDevice);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNoDevicesFound() {
        new AlertDialog.Builder(this)
                .setTitle("No Devices Found")
                .setMessage("No casting devices found on your network.\n\n" +
                        "Make sure:\n" +
                        "• Your TV/Cast device is on the same Wi-Fi\n" +
                        "• Your TV is turned on\n" +
                        "• Your device supports casting (Chromecast, Smart TV, etc.)")
                .setPositiveButton("OK", null)
                .show();
    }

    private void onDeviceSelected(MediaRouter.RouteInfo device) {
        Log.d(TAG, "Device selected: " + device.getName());
        Toast.makeText(this, "Selected: " + device.getName(), Toast.LENGTH_SHORT).show();

        // For now, just show a toast - we'll handle actual casting later
        // You can add your casting logic here when ready
    }

    private void startBuiltInCasting() {
        try {
            // Get the clean video URL without headers
            String url = movie.getVideoUrl();
            String[] parts = url.split("\\|", 2);
            String cleanUrl = parts[0];

            Log.d(TAG, "Starting DLNA/UPnP casting with URL: " + cleanUrl);

            // Try multiple intents for different casting apps

            // Intent 1: Generic video view (may show DLNA apps)
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(cleanUrl), "video/*");
            intent.putExtra(Intent.EXTRA_TITLE, movie.getTitle());
            intent.putExtra("title", movie.getTitle());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Intent 2: Specific to popular casting apps
            Intent allCastingIntent = new Intent(Intent.ACTION_SEND);
            allCastingIntent.setType("video/*");
            allCastingIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(cleanUrl));
            allCastingIntent.putExtra("title", movie.getTitle());
            allCastingIntent.putExtra("subject", movie.getTitle());

            // Create chooser to show all available casting apps
            Intent chooserIntent = Intent.createChooser(intent, "Cast to TV");
            // Add the SEND intent as an option
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {allCastingIntent});

            // Check if any app can handle this
            if (chooserIntent.resolveActivity(getPackageManager()) != null) {
                // Pause local playback
                if (player != null && player.isPlaying()) {
                    player.pause();
                }

                startActivity(chooserIntent);
                Toast.makeText(this, "Select a casting app...", Toast.LENGTH_SHORT).show();

            } else {
                // No casting app found - prompt to install Web Video Caster
                showNoCastingAppDialog();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error starting casting", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showNoCastingAppDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Casting App Found")
                .setMessage("To cast to your TV, please install a DLNA casting app like 'Web Video Caster' from Play Store")
                .setPositiveButton("Install", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openPlayStoreForCastingApps();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openPlayStoreForCastingApps() {
        try {
            Intent playStoreIntent = new Intent(Intent.ACTION_VIEW);
            playStoreIntent.setData(Uri.parse("market://details?id=com.instantbits.cast.webvideo"));
            startActivity(playStoreIntent);
        } catch (Exception e) {
            // Fallback to web browser
            Intent browserIntent = new Intent(Intent.ACTION_VIEW);
            browserIntent.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.instantbits.cast.webvideo"));
            startActivity(browserIntent);
        }
    }



    /**
     * Start built-in Android casting using Intent
     */
    private void startBuiltInCastingGoogle() {
        try {
            // Get the clean video URL without headers
            String url = movie.getVideoUrl();
            String[] parts = url.split("\\|", 2);
            String cleanUrl = parts[0];

            Log.d(TAG, "Starting built-in casting with URL: " + cleanUrl);

            // Create the intent for viewing the video
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(cleanUrl), "video/*");

            // Add extra information for better casting experience
            intent.putExtra(Intent.EXTRA_TITLE, movie.getTitle());
            intent.putExtra("title", movie.getTitle());

            // Add flags to suggest casting
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Check if there's an app that can handle this intent (including casting apps)
            if (intent.resolveActivity(getPackageManager()) != null) {
                // Pause local playback before starting cast
                if (player != null && player.isPlaying()) {
                    player.pause();
                }

                // Start the casting intent
                startActivity(intent);

                Toast.makeText(this, "Opening casting dialog...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Built-in casting intent started successfully");

            } else {
                // No app available to handle casting
                Toast.makeText(this, "No casting app available on your device", Toast.LENGTH_LONG).show();
                Log.w(TAG, "No activity found to handle casting intent");

                // Resume playback if no casting app available
                if (player != null) {
                    player.play();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error starting built-in casting", e);
            Toast.makeText(this, "Error starting casting: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // Resume playback on error
            if (player != null) {
                player.play();
            }
        }
    }

    private void updateCastDeviceAvailability() {
        mCastDevicesAvailable = mMediaRouter.isRouteAvailable(
                new androidx.mediarouter.media.MediaRouteSelector.Builder()
                        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                        .build(),
                0);
    }

    @Override
    public void onCastSessionAvailable() {
        Log.d(TAG, "onCastSessionAvailable: Cast session is available");
        runOnUiThread(() -> {
            try {
                setCurrentPlayer(castPlayer);
            } catch (Exception e) {
                Log.e(TAG, "Error switching to CastPlayer", e);
                Toast.makeText(this, "Failed to start casting", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onCastSessionUnavailable() {
        Log.d(TAG, "onCastSessionUnavailable: Cast session is unavailable");
        runOnUiThread(() -> {
            try {
                setCurrentPlayer(player);
            } catch (Exception e) {
                Log.e(TAG, "Error switching back to local player", e);
            }
        });
    }
    private void setCurrentPlayer(Player newPlayer) {
        if (currentPlayer == newPlayer) {
            return;
        }

        // Save state from current player
        long playbackPositionMs = C.TIME_UNSET;
        boolean playWhenReady = false;
        MediaItem currentMediaItem = null;

        if (currentPlayer != null) {
            playbackPositionMs = currentPlayer.getCurrentPosition();
            playWhenReady = currentPlayer.getPlayWhenReady();
            if (currentPlayer.getCurrentMediaItem() != null) {
                currentMediaItem = currentPlayer.getCurrentMediaItem();
            }
            currentPlayer.stop();
        }

        currentPlayer = newPlayer;
        playerView.setPlayer(currentPlayer);

        Log.d(TAG, "setCurrentPlayer: Switching to " + (newPlayer == castPlayer ? "CastPlayer" : "LocalPlayer"));

        if (currentPlayer == castPlayer) {
            // Cast Player
            if (currentMediaItem != null) {
                currentPlayer.setMediaItem(currentMediaItem);
            } else {
                MediaSource mediaSource = buildMediaSource(movie);
                currentPlayer.setMediaItem(mediaSource.getMediaItem());
            }
            currentPlayer.prepare();
            currentPlayer.setPlayWhenReady(true);
        } else {
            // Local Player (your existing player)
            if (currentMediaItem != null) {
                currentPlayer.setMediaItem(currentMediaItem);
            } else {
                MediaSource mediaSource = buildMediaSource(movie);
                ((ExoPlayer) currentPlayer).setMediaSource(mediaSource);
            }
            currentPlayer.prepare();
            if (playbackPositionMs != C.TIME_UNSET) {
                currentPlayer.seekTo(playbackPositionMs);
            }
            currentPlayer.setPlayWhenReady(playWhenReady);
        }
    }

    private void debugMediaItem(MediaItem mediaItem) {
        Log.d(TAG, "MediaItem Debug:");
        Log.d(TAG, "  - URI: " + mediaItem.localConfiguration.uri);
        Log.d(TAG, "  - MIME Type: " + mediaItem.localConfiguration.mimeType);
        Log.d(TAG, "  - Title: " + mediaItem.mediaMetadata.title);
    }

    private MediaItem buildMediaItemForCast(Movie movie) {
        updateMovieUrlToHttps(movie);

        String url = movie.getVideoUrl();
        String[] parts = url.split("\\|", 2);
        String cleanUrl = parts[0];
        Map<String, String> headers = new HashMap<>();

        if (parts.length == 2) {
            headers = com.omerflex.server.Util.extractHeaders(parts[1]);
            Log.d(TAG, "buildMediaItemForCast: Headers found: " + headers.size());
        }

        String mimeType = determineMimeType(cleanUrl);

        // For HLS streams with headers, we need to use a different approach
        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(Uri.parse(cleanUrl))
                .setMimeType(mimeType);

        // Create a custom media item that includes headers in the media ID
        // This is a workaround since CastPlayer doesn't directly support headers in MediaItem
        if (!headers.isEmpty()) {
            String mediaIdWithHeaders = cleanUrl + "|headers|" + parts[1];
            builder.setMediaId(mediaIdWithHeaders);
        }

        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(movie.getTitle())
                .setDisplayTitle(movie.getTitle())
                .build();

        builder.setMediaMetadata(metadata);

        return builder.build();
    }

    private String determineMimeType(String url) {
        Uri uri = Uri.parse(url);
        @C.ContentType int contentType = Util.inferContentType(uri);

        switch (contentType) {
            case C.CONTENT_TYPE_HLS:
                return MimeTypes.APPLICATION_M3U8;
            case C.CONTENT_TYPE_DASH:
                return MimeTypes.APPLICATION_MPD;
            case C.CONTENT_TYPE_SS:
                return MimeTypes.APPLICATION_SS;
            default:
                // For progressive streams, try to detect from file extension
                if (url.contains(".m3u8")) {
                    return MimeTypes.APPLICATION_M3U8;
                } else if (url.contains(".mpd")) {
                    return MimeTypes.APPLICATION_MPD;
                } else if (url.contains(".ism")) {
                    return MimeTypes.APPLICATION_SS;
                } else if (url.contains(".mp4")) {
                    return MimeTypes.VIDEO_MP4;
                } else if (url.contains(".webm")) {
                    return MimeTypes.VIDEO_WEBM;
                } else if (url.contains(".mkv")) {
                    return "video/x-matroska";
                } else {
                    // Default to MP4
                    return MimeTypes.VIDEO_MP4;
                }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mCastContext != null) {
            mCastContext.addCastStateListener(mCastStateListener);
        }
        mMediaRouter.addCallback(new androidx.mediarouter.media.MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build(), mMediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
        updateCastDeviceAvailability();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCastContext != null) {
            mCastContext.removeCastStateListener(mCastStateListener);
        }
    }


    private void updateHistoryPlayback() {
        new Thread(() -> {
            if (movie != null) {
                Log.d(TAG, "updateHistoryPlayback: "+movie.getStudio()+ " "+movie.getId());
                if (movie.getStudio().equals(Movie.SERVER_IPTV)){
                    movieRepository.setMovieIsHistory(movie.getId());
                    return;
                }
                if (movie.getParentId() == null){
                    return;
                }
                Movie parentMovie = movieRepository.getMovieByIdSync(movie.getParentId());
                if (parentMovie != null) {
                    if (parentMovie.getType() == MovieType.FILM) {
                        movieRepository.setMovieIsHistory(parentMovie.getId());
                    } else if (parentMovie.getType() == MovieType.EPISODE) {
                        if (parentMovie.getParentId() == null)
                        {
                            movieRepository.setMovieIsHistory(parentMovie.getId());
                            return;
                        }
                        Movie season = movieRepository.getMovieByIdSync(parentMovie.getParentId());
                        if (season != null) {
                            if (season.getParentId() != null) {
                                movieRepository.setMovieIsHistory(season.getParentId());
                            } else {
                                movieRepository.setMovieIsHistory(season.getId());
                            }
                        }
                    }
                }
            }
        }).start();
    }


    public void handleBackPressed() {
        //super.onBackPressed();
        Log.d("TAG", "onBackPressed: 1");
        //check if waiting time between the second click of back button is greater less than 2 seconds so we finish the app
        if (backPressedTime + 1500 > System.currentTimeMillis()) {
            Log.d("TAG", "onBackPressed: 2 +");
            if (player != null){
                Log.d("TAG", "onBackPressed: 2 player released");
                //    movie.setPlayedTime(String.valueOf(player.getCurrentPosition()));
               // movie.save(dbHelper);
                player.stop();
                player.release();
            }
            finish();
        } else {
            Log.d("TAG", "onBackPressed: 3");
            if (playerView.isControllerFullyVisible())
                playerView.hideController();
            else
                Toast.makeText(this, "Press back 2 time to exit", Toast.LENGTH_SHORT).show();
        }
        backPressedTime = System.currentTimeMillis();
//        super.onBackPressed();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        assert player != null;
       // movie.setPlayedTime(String.valueOf(player.getCurrentPosition()));
       // movie.save(dbHelper);
        player.pause();
        playerView.setKeepScreenOn(false);
    }

    public  List<String> splitString(String headers) {
        List<String> headerList = new ArrayList<>();
        String[] headerParts = headers.split("&");
        for (String headerPart : headerParts) {
            String[] keyValuePair = headerPart.split("=");
            if (keyValuePair.length == 2) {
                String key = keyValuePair[0];
                String value = keyValuePair[1];
            }
        }
        return headerList;
    }

    private MediaSource buildMediaSource_old(Movie movie) {
        if (Objects.equals(movie.getStudio(), Movie.SERVER_OLD_AKWAM) && !movie.getVideoUrl().contains("https")){
            movie.setVideoUrl(movie.getVideoUrl().replace("http", "https"));
        }
        String url = movie.getVideoUrl();
        DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        if (url.contains("||")){
            String[] splitString = url.split("\\|\\|");
            url = splitString[0];
            dataSourceFactory = () -> {
                DataSource.Factory  httpDataSourceFactory = new DefaultHttpDataSource.Factory();
                // HttpDataSource dataSource = httpDataSourceFactory.createDataSource();
                DataSource dataSource = httpDataSourceFactory.createDataSource();
                if (splitString.length == 2){
                    Log.d("TAG", "buildMediaSource: extracted headers ssss: "+splitString[1]);
                    String[] headerParts = splitString[1].split("&");
                    for (String headerPart : headerParts) {
                        String[] keyValuePair = headerPart.split("=");
                        if (keyValuePair.length == 2) {
                            String key = keyValuePair[0];
                            String value = keyValuePair[1];
                            Log.d(TAG, "buildMediaSource: "+ key+", "+value);
                            // Set a custom authentication request header.
                            ((HttpDataSource) dataSource).setRequestProperty(key, value);
                        }
                    }
                }

                return dataSource;
            };
        }
        else if (url.contains("|")){
            String[] splitString = url.split("\\|");
            url = splitString[0];
            dataSourceFactory = () -> {
                DataSource.Factory  httpDataSourceFactory = new DefaultHttpDataSource.Factory();
                // HttpDataSource dataSource = httpDataSourceFactory.createDataSource();
                DataSource dataSource = httpDataSourceFactory.createDataSource();
                if (splitString.length == 2){
                    Log.d("TAG", "buildMediaSource: extracted headers ssss: "+splitString[1]);
                    String[] headerParts = splitString[1].split("&");
                    for (String headerPart : headerParts) {
                        String[] keyValuePair = headerPart.split("=");
                        if (keyValuePair.length == 2) {
                            String key = keyValuePair[0];
                            String value = keyValuePair[1];
                            // Set a custom authentication request header.

                            ((HttpDataSource) dataSource).setRequestProperty(key, value);
                        }
                    }
                }

                return dataSource;
            };
        }
        Log.d(TAG, "buildMediaSource: "+url);
        Uri uri = Uri.parse(url);


        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(uri));

//        acceptAllSSLCertificate(dataSourceFactory);
        int type = Util.inferContentType(uri);

        Log.d("TAG", "buildMediaSource: play: "+type+", "+ uri);
        if (movie.getVideoUrl().contains("m3u")){
            return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
        }
        switch (type) {
            case C.CONTENT_TYPE_SS:
// Create a SmoothStreaming media source pointing to a manifest uri.
                mediaSource = new SsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
                return mediaSource;
//            case C.CONTENT_TYPE_DASH:
//// Create a dash media source pointing to a dash manifest uri.
//                mediaSource = new DashMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
//                return mediaSource;
            case C.CONTENT_TYPE_HLS:
// Create a HLS media source pointing to a playlist uri.
                HlsMediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
                return hlsMediaSource;
        }

        return mediaSource;
//        if (movie.getVideoUrl().contains("m3u")){
//            HlsMediaSource.Factory factory = new HlsMediaSource.Factory(
//                    new DefaultHttpDataSourceFactory(Util.getUserAgent(this, "exoplayer-codelab")));
//            HlsMediaSource mediaSource = factory.createMediaSource(uri);
//            return mediaSource;
//        }


//      DataSource.Factory dataSourceFactory =
//                new DefaultDataSourceFactory(this, "exoplayer-codelab");
//        return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
    }

//    private void acceptAllSSLCertificate(DataSource.Factory dataSourceFactory) {
//        try {
//            // Set up SSL context to trust all certificates
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            TrustManager[] trustManagers = new TrustManager[]{
//                    new X509TrustManager() {
//                        @Override
//                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
//                            // Do nothing
//                        }
//
//                        @Override
//                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
//                            // Do nothing
//                        }
//
//                        @Override
//                        public X509Certificate[] getAcceptedIssuers() {
//                            return new X509Certificate[0];
//                        }
//                    }
//            };
//
//            sslContext.init(null, trustManagers, new java.security.SecureRandom());
//
//            // Use the custom SSL context
//            dataSourceFactory.setSslSocketFactory(sslContext.getSocketFactory());
//        } catch (NoSuchAlgorithmException | KeyManagementException e) {
//            e.printStackTrace();
//        }
//    }

    private MediaSource buildMediaSource(Movie movie) {
        updateMovieUrlToHttps(movie);

        String url = movie.getVideoUrl();
        // Split the URL to get the clean URL and headers part
        String[] parts = url.split("\\|", 2);
        String cleanUrl = parts[0];
        Map<String, String> headers = new HashMap<>();

        if (parts.length == 2) {
            headers = com.omerflex.server.Util.extractHeaders(parts[1]);
            Log.d("TAG", "buildMediaSource: h:" + parts[1]);
        }
        DataSource.Factory dataSourceFactory = createDataSourceFactory(cleanUrl, headers);

        Log.d("TAG", "buildMediaSource: cleanUrl:" + cleanUrl);
        Uri uri = Uri.parse(cleanUrl);

        MediaSource mediaSource = createMediaSource(dataSourceFactory, uri, movie);
        Log.d(TAG, "buildMediaSource: mediaSource: "+mediaSource.toString());
        Log.d("TAG", "buildMediaSource: done: " + Util.inferContentType(uri) + ", " + uri);
        return mediaSource;
    }

    private void updateMovieUrlToHttps(Movie movie) {
        if (Objects.equals(movie.getStudio(), Movie.SERVER_OLD_AKWAM) && !movie.getVideoUrl().contains("https")) {
            movie.setVideoUrl(movie.getVideoUrl().replace("http", "https"));
        }
    }

    private DataSource.Factory createDataSourceFactory(String url, Map<String, String> headers) {
        Log.d(TAG, "createDataSourceFactory: h:"+headers);
        if (headers.isEmpty()) {
            return new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(60000)
                    .setReadTimeoutMs(60000);
        }
            return () -> {
                DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(60000)
                        .setReadTimeoutMs(60000);
                DataSource dataSource = httpDataSourceFactory.createDataSource();
                Log.d(TAG, "createDataSourceFactory:setRequestHeaders: "+dataSource.toString());
                    setRequestHeaders(dataSource, headers);

                return dataSource;
            };
    }

    private void setRequestHeaders(DataSource dataSource, Map<String, String> headers) {
        Log.d("TAG", "buildMediaSource: extracted headers: " + headers);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            ((HttpDataSource) dataSource).setRequestProperty(entry.getKey(), entry.getValue());
        }
        Log.d(TAG, "buildMediaSource: extracted headers: done");
    }

    private MediaSource createMediaSource(DataSource.Factory dataSourceFactory, Uri uri, Movie movie) {
        int type = Util.inferContentType(uri);
        String mimeType = determineMimeType(uri.toString());

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(uri)
                .setMimeType(mimeType)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(movie.getTitle())
                        .build())
                .build();

        Log.d(TAG, "createMediaSource: type: " + type + ", mimeType: " + mimeType);

        if (movie.getVideoUrl().contains("m3u")) {
            return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        }

        switch (type) {
            case C.CONTENT_TYPE_SS:
                return new SsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
            case C.CONTENT_TYPE_DASH:
                return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
            case C.CONTENT_TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
            default:
                return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        }
    }

    private MediaSource createMediaSource_old(DataSource.Factory dataSourceFactory, Uri uri, Movie movie) {
        int type = Util.inferContentType(uri);
        Log.d(TAG, "createMediaSource: type: "+type);

        if (movie.getVideoUrl().contains("m3u")) {
            return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
        }
        Log.d(TAG, "createMediaSource: type "+ type);
        switch (type) {
            case C.CONTENT_TYPE_SS:
                return new SsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
            case C.CONTENT_TYPE_DASH:
//                // Configure DRM for PlayReady
//                MediaItem.DrmConfiguration drmConfig = new MediaItem.DrmConfiguration.Builder(C.PLAYREADY_UUID)
////                        .setLicenseUri("YOUR_PLAYREADY_LICENSE_SERVER_URL") // Replace with actual license URL
////                        .setForceDefaultLicenseUri(true)
//                        .setMultiSession(true)
//                        .build();
//                MediaItem mediaItem = new MediaItem.Builder()
//                        .setUri(uri)
//                        .setDrmConfiguration(drmConfig)
//                        .build();
//                return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
            case C.CONTENT_TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
            default:
                return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri));
        }
    }

    private MediaItem createMediaSource_old(Movie movie) {
        // Uri videoUri = Uri.parse("https://media.geeksforgeeks.org/wp-content/uploads/20201217163353/Screenrecorder-2020-12-17-16-32-03-350.mp4");
        // Build the media item.
        if (Objects.equals(movie.getStudio(), Movie.SERVER_OLD_AKWAM) && !movie.getVideoUrl().contains("https")){
            movie.setVideoUrl(movie.getVideoUrl().replace("http", "https"));
        }
        MediaItem mediaItem = MediaItem.fromUri(movie.getVideoUrl());
        Log.d("Exoplayer", "createMediaSource: "+movie.getVideoUrl()+ "[ "+Uri.parse(movie.getVideoUrl())+" ]");
// Set the media item to be played.
    /*    player.setMediaItem(mediaItem);
// Prepare the player.
        player.prepare();
// Start the playback.
        player.play();

     */
/*
        // Create a data source factory.
        DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory();
// Create a SmoothStreaming media source pointing to a manifest uri.
        MediaSource mediaSource =
                new SsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(videoUri));

 */
        return mediaItem;

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // See whether the player view wants to handle media or DPAD keys events.
        Log.d("Exoplayer", "dispatchKeyEvent: "+event.toString());

        if(playerView == null){
            return super.dispatchKeyEvent(event);
        }

        if (!playerView.isControllerFullyVisible()) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
            {
                playerView.showController();
            }
            else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)
            {
                playerView.hideController();

                Objects.requireNonNull(player).seekTo(player.getCurrentPosition() + 15000);
            }else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT){
                Objects.requireNonNull(player).seekTo(player.getCurrentPosition() - 15000);
            }
            else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK){
                if (playerView.isControllerFullyVisible()){
                    playerView.hideController();
                }
            }
        }

        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }


    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("TAG", "onDestroy: yess ");
        if (isFinishing()){
            if (player != null) {
                // movie.setPlayedTime(String.valueOf(player.getCurrentPosition()));
                //movie.save(dbHelper);
                player.release();
                player = null;
            }
        }
        // Stop media server
        MediaServer.getInstance().stopServer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mMediaRouter.removeCallback(mMediaRouterCallback);

        Log.d("TAG", "onStop: yess ");
        if (player != null && movie.getParentId() != null) {
            long watchedPosition = player.getCurrentPosition();
            long movieLength = player.getDuration();

            if (movieLength > 0) {
                long playedPercentage = (watchedPosition * 100) / movieLength;
                movie.setPlayedTime(playedPercentage);
            }

            MovieHistory movieHistory = movie.getMovieHistory();
            if (movieHistory == null) {
                // use parent movie
                movieHistory = new MovieHistory(movie.getParentId(), watchedPosition, new Date());
            } else {
                movieHistory.setWatchedPosition(watchedPosition);
                movieHistory.setLastWatchedDate(new Date());
            }
            movie.setMovieHistory(movieHistory);

            movieRepository.updateWatchedTime(movie.getParentId(), watchedPosition);

            // Check if the activity is still running
            if (!isFinishing()) {
                player.pause();
                playerView.setKeepScreenOn(false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playerView != null) {
            playerView.setKeepScreenOn(true);
        }
        if (mCastContext != null) {
            mCastContext.addCastStateListener(mCastStateListener);
        }
        Log.d("TAG", "onResume: yess");
    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        super.onCreateOptionsMenu(menu);
//        // This line inflates your menu layout into the activity's app bar
//        getMenuInflater().inflate(R.menu.main_menu, menu);
//        // This line finds your button within the menu and makes it a fully functional Cast button
//        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
//        return true;
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Set up the Cast button, but we will hide it to avoid confusion with the layout button
        mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.media_route_menu_item);
        if (mediaRouteMenuItem != null) {
            mediaRouteMenuItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void showCastOrDlnaDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences("dlna_prefs", MODE_PRIVATE);
        String lastDeviceName = prefs.getString("last_dlna_name", null);
        String lastDeviceLocation = prefs.getString("last_dlna_location", null);

        final List<String> items = new ArrayList<>();

        if (lastDeviceName != null && lastDeviceLocation != null) {
            items.add("Reconnect to " + lastDeviceName);
        }
        if (mCastDevicesAvailable) {
            items.add("Google Cast Device");
        }
        items.add("Scan for DLNA Devices");
        items.add("Share to another app");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cast to...");
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            String selectedItem = items.get(which);
            if (selectedItem.startsWith("Reconnect")) {
                castToDlnaLocation(lastDeviceName, lastDeviceLocation);
            } else if (selectedItem.equals("Google Cast Device")) {
                // Show the standard Google Cast chooser dialog
                androidx.mediarouter.app.MediaRouteChooserDialogFragment chooser = new androidx.mediarouter.app.MediaRouteChooserDialogFragment();
                chooser.setRouteSelector(new androidx.mediarouter.media.MediaRouteSelector.Builder()
                        .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                        .build());
                chooser.show(getSupportFragmentManager(), "MediaRouteChooser");
            } else if (selectedItem.equals("Scan for DLNA Devices")) {
                showDiscoveredDevices();
            } else if (selectedItem.equals("Share to another app")) {
                shareVideoUrl();
            }
        });
        builder.create().show();
    }

    private void shareVideoUrl() {
        if (movie == null || movie.getVideoUrl() == null) {
            Toast.makeText(this, "Video URL not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get the clean video URL without headers
        String url = movie.getVideoUrl();
        String[] parts = url.split("\\|", 2);
        String cleanUrl = parts[0];

        Intent shareIntent = new Intent(Intent.ACTION_VIEW);
        shareIntent.setDataAndType(Uri.parse(cleanUrl), "video/*");
        shareIntent.putExtra(Intent.EXTRA_TITLE, movie.getTitle());

        // Create a chooser to show only video players
        Intent chooserIntent = Intent.createChooser(shareIntent, "Open with");

        if (chooserIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(chooserIntent);
        } else {
            Toast.makeText(this, "No app found to play this video", Toast.LENGTH_SHORT).show();
        }
    }

    // A helper to guess the MIME type for the MediaItem
    private String getMimeType(String url) {
        int type = Util.inferContentType(Uri.parse(url));
        switch (type) {
            case C.CONTENT_TYPE_DASH: return "application/dash+xml";
            case C.CONTENT_TYPE_HLS: return "application/x-mpegURL";
            case C.CONTENT_TYPE_SS: return "application/vnd.ms-sstr+xml";
            default: return null; // Let ExoPlayer infer it
        }
    }
}
