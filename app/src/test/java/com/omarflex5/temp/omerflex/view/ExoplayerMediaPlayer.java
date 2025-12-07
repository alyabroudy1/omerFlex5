package com.omarflex5.temp.omerflex.view;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.gson.Gson;
import com.omerflex.OmerFlexApplication;
import com.omerflex.R;
import com.omerflex.db.AppDatabase;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieRepository;
import com.omerflex.providers.MediaServer;
import com.omerflex.service.CastingManager;
import com.omerflex.service.MediaSourceFactory;
import com.omerflex.service.PlayerManager;
import com.omerflex.service.PlaybackHistoryManager;
import com.omerflex.service.casting.CommunicationManager;
import com.omerflex.service.casting.Device;
import com.omerflex.service.casting.Message;
import com.omerflex.service.casting.NetworkServiceDiscovery;
import com.omerflex.view.PlayerUiController;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@androidx.media3.common.util.UnstableApi
public class ExoplayerMediaPlayer extends AppCompatActivity {

    private static final String TAG = "Exoplayer";

    private PlayerManager playerManager;
    private MediaSourceFactory mediaSourceFactory;
    private CastingManager castingManager;
    private PlaybackHistoryManager playbackHistoryManager;
    private PlayerUiController uiController;

    private MovieRepository movieRepository;
    private PlayerView playerView;
    private Movie movie;

    private CommunicationManager communicationManager;
    private NetworkServiceDiscovery networkDiscovery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Basic window setup
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_exoplayer);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize core components
        playerView = findViewById(R.id.player_view);
        AppDatabase db = AppDatabase.getDatabase(this);
        movieRepository = MovieRepository.getInstance(this, db.movieDao());
        movie = com.omerflex.server.Util.recieveSelectedMovie(getIntent());

        if (movie == null) {
            Toast.makeText(this, "Error: No movie data found.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Asynchronously fetch the full title and then initialize the player and UI
        fetchTitleAndInitialize();
    }

    private void fetchTitleAndInitialize() {
        if (movie.getParentId() != null) {
            new Thread(() -> {
                Movie parentMovie = movieRepository.getMovieByIdSync(movie.getParentId());
                if (parentMovie != null) {
                    String displayTitle = parentMovie.getTitle(); // Default title

                    // If parent is an EPISODE, find the season and series titles
                    if (parentMovie.getType() == com.omerflex.entity.MovieType.EPISODE && parentMovie.getParentId() != null) {
                        Movie season = movieRepository.getMovieByIdSync(parentMovie.getParentId());
                        if (season != null) {
                            // Check if the season also has a parent (the series)
                            if (season.getParentId() != null) {
                                Movie series = movieRepository.getMovieByIdSync(season.getParentId());
                                if (series != null) {
                                    displayTitle = series.getTitle() + " - " + season.getTitle() + " - " + parentMovie.getTitle();
                                } else {
                                    // Fallback to Season - Episode
                                    displayTitle = season.getTitle() + " - " + parentMovie.getTitle();
                                }
                            } else {
                                // Fallback to Season - Episode
                                displayTitle = season.getTitle() + " - " + parentMovie.getTitle();
                            }
                        }
                    }
                    movie.setTitle(displayTitle);
                }
                // After fetching, initialize on the main thread
                runOnUiThread(this::initializePlayerAndUi);
            }).start();
        } else {
            // No parent, initialize immediately
            initializePlayerAndUi();
        }
    }

    private void initializePlayerAndUi() {
        // Initialize managers
        mediaSourceFactory = new MediaSourceFactory();
        castingManager = new CastingManager(this, movie);
        castingManager.init();
        playerManager = new PlayerManager(this, castingManager.getCastContext(), playerView, movie);
        castingManager.setPlayerManager(playerManager);
        playbackHistoryManager = new PlaybackHistoryManager(movieRepository, movie);
        uiController = new PlayerUiController(this, playerView, playerManager);

        communicationManager = OmerFlexApplication.getCommunicationManager();
        networkDiscovery = OmerFlexApplication.getNetworkDiscovery();

        // Setup UI elements
        setupUI();

        // Configure and prepare the player
        configurePlayer();
        preparePlayback();
    }

    private void setupUI() {
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);

        TextView customTitle = playerView.findViewById(R.id.custom_exo_title);
        if (customTitle != null) {
            customTitle.setText(movie != null ? movie.getTitle() : "No Title");
        }

        ImageView shareButton = playerView.findViewById(R.id.share_button);
        if (shareButton != null) {
            shareButton.setOnClickListener(v -> showShareOptionsDialog());
        }

        androidx.mediarouter.app.MediaRouteButton mediaRouteButton = playerView.findViewById(R.id.media_route_button);
        if (mediaRouteButton != null) {
            castingManager.setUpMediaRouteButton(mediaRouteButton);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (uiController != null) {
                    uiController.handleBackPressed();
                }
            }
        });

        playerView.setOnTouchListener(uiController.getOnTouchListener());
    }

    private void showShareOptionsDialog() {
        final CharSequence[] items = {"Cast (Chromecast, DLNA)", "Share to another device"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Share Movie");
        builder.setItems(items, (dialog, item) -> {
            if (items[item].equals("Cast (Chromecast, DLNA)")) {
                castingManager.showCastOrDlnaDialog();
            } else if (items[item].equals("Share to another device")) {
                discoverAndShareToDevice();
            }
        });
        builder.show();
    }

    private void discoverAndShareToDevice() {
        if (networkDiscovery == null) {
            Toast.makeText(this, "Device discovery not available", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<Device> discoveredDevices = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a Device");

        NetworkServiceDiscovery.DiscoveryListener discoveryListener = new NetworkServiceDiscovery.DiscoveryListener() {
            @Override
            public void onDeviceFound(Device device) {
                runOnUiThread(() -> {
                    if (!discoveredDevices.contains(device)) {
                        discoveredDevices.add(device);
                        adapter.add(device.getDeviceName());
                        adapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onDeviceLost(Device device) {
                runOnUiThread(() -> {
                    discoveredDevices.remove(device);
                    adapter.remove(device.getDeviceName());
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onDiscoveryStarted() {
                runOnUiThread(() -> Toast.makeText(ExoplayerMediaPlayer.this, "Searching for devices...", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onDiscoveryStopped() {
                runOnUiThread(() -> {
                    Toast.makeText(ExoplayerMediaPlayer.this, "Discovery finished.", Toast.LENGTH_SHORT).show();
                    networkDiscovery.removeDiscoveryListener(this);
                });
            }

            @Override
            public void onServiceRegistered(String serviceName) {}

            @Override
            public void onRegistrationFailed(int errorCode) {}
        };

        networkDiscovery.addDiscoveryListener(discoveryListener);
        networkDiscovery.startDiscovery();

        builder.setAdapter(adapter, (dialog, which) -> {
            Device selectedDevice = discoveredDevices.get(which);
            Toast.makeText(ExoplayerMediaPlayer.this, "Sharing with " + selectedDevice.getDeviceName(), Toast.LENGTH_SHORT).show();

            communicationManager.connectToDevice(selectedDevice);

            Gson gson = new Gson();
            String movieJson = gson.toJson(movie);
            Message message = new Message(OmerFlexApplication.getInstance().getPackageName(), Message.MessageType.DATA, movieJson);
            communicationManager.sendMessage(message, selectedDevice);
            networkDiscovery.stopDiscovery();
        });

        builder.setOnDismissListener(dialog -> networkDiscovery.stopDiscovery());
        builder.show();
    }


    private void configurePlayer() {
        ExoPlayer localPlayer = playerManager.getLocalPlayer();

        localPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(), true);
        localPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);

        addPlayerListeners(localPlayer);
        trustAllCertificates();
    }

    private void preparePlayback() {
        playerManager.prepare(mediaSourceFactory.buildMediaSource(movie));
        playerView.setControllerAutoShow(false);
        playerView.setKeepScreenOn(true);
        playerManager.getCurrentPlayer().play();
    }

    private void addPlayerListeners(ExoPlayer localPlayer) {
        localPlayer.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onAudioSinkError(EventTime eventTime, Exception audioSinkError) {
                Log.d(TAG, "onAudioSinkError: " + audioSinkError.getMessage());
                playerManager.prepare(mediaSourceFactory.buildMediaSource(movie));
                playerManager.getCurrentPlayer().play();
            }
        });

        localPlayer.addListener(new Player.Listener() {
            @Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                Player player = playerManager.getCurrentPlayer();
                if (player.getDuration() != C.TIME_UNSET) {
                    playbackHistoryManager.markAsWatched();
                    playbackHistoryManager.restorePlaybackPosition(player);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.d("TAG", "onPlayerError: " + error.getMessage());

                // Retry logic for certain servers and error codes
                if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                    String studio = movie.getStudio();
                    if (Movie.SERVER_OMAR.equals(studio) || Movie.SERVER_IPTV.equals(studio)) {
                        MediaSource mediaSource = mediaSourceFactory.buildMediaSource(movie);
                        playerManager.getLocalPlayer().prepare(mediaSource);
                        playerManager.getLocalPlayer().play();
                        return;
                    }
                }

                boolean deleteCond = error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                        || error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                        || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                        || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED;
                if (deleteCond) {
                    movieRepository.deleteMovie(movie);
                }
                Toast.makeText(ExoplayerMediaPlayer.this, "Failed to play the link", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void trustAllCertificates() {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to install trust all certificates manager", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (castingManager != null) {
            castingManager.onStart();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playerManager != null) {
            playerManager.getCurrentPlayer().pause();
        }
        if (castingManager != null) {
            castingManager.onPause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (castingManager != null) {
            castingManager.onStop();
        }
        if (playerManager != null && playbackHistoryManager != null) {
            playbackHistoryManager.savePlaybackPosition(playerManager.getCurrentPlayer());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playerView != null) {
            playerView.setKeepScreenOn(true);
        }
        if (playerManager != null) {
            playerManager.getCurrentPlayer().play();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            if (playerManager != null) {
                playerManager.release();
            }
            MediaServer.getInstance().stopServer();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (playerManager != null) {
            playerManager.getCurrentPlayer().pause();
        }
        if (playerView != null) {
            playerView.setKeepScreenOn(false);
        }
    }

    // onCreateOptionsMenu is no longer needed as the cast button is in the player view.

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (uiController != null && uiController.dispatchKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
