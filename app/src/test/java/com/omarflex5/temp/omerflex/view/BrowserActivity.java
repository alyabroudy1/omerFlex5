package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.leanback.widget.ArrayObjectAdapter;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.entity.MovieFetchProcess;
import com.omerflex.entity.MovieType;
import com.omerflex.entity.ServerConfig;
import com.omerflex.entity.dto.LinkHeadersDTO;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.Util;
import com.omerflex.service.HtmlPageService;
import com.omerflex.service.LinkFilterService;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.database.MovieDbHelper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * start a webView browser
 */
public class BrowserActivity extends AppCompatActivity {

    public static final int WEB_VIEW_MODE_ON_PAGE_STARTED = 0;
    public static final int WEB_VIEW_MODE_ON_LOAD_RESOURCES = 1;
    public static final int WEB_VIEW_MODE_ON_PAGE_FINISHED = 2;
    //variable for back button confirmation
    private long backPressedTime;
    static String TAG = "BrowserActivity";
    AbstractServer server;

    Movie movie;
    public boolean openedForResult = false;
    public boolean isCookieFetch = false;
    ArrayObjectAdapter listRowAdapter;

    Gson gson;
    public static int RESULT_COUNTER = 0;
    public static boolean EXECUTE_JS = true;
    Activity activity;
    MovieDbHelper dbHelper;

    WebView webView;
    CursorLayout cursorLayout;

    //####################
    //notification bar
    private String redirectUrl; // Variable to store the full URL
    private LinearLayout urlNotificationBar;
    private TextView urlBar;
    private Handler autoHideHandler = new Handler();
    private Runnable autoHideRunnable;
    private boolean hideRedirectBar;
    ServerConfig config;

    static String currentVideoUrl = "";

    private int selectedRowIndex = -1;
    private int  selectedItemIndex = -1;

    private HashSet<String> playedVideoLinks;

    //#############
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        Log.d(TAG, "onCreate: BrowserActivity");

        initializeThings();
        if (savedInstanceState == null) {
            redirectUrl = movie.getVideoUrl();
            Log.d(TAG, "onCreate: linkHeaders original link: " + movie.getVideoUrl());
            LinkHeadersDTO linkHeadersDTO = prepareLoadingLink(movie);
//            Log.d(TAG, "onCreate: linkHeadersDTO: "+ linkHeadersDTO);
            Log.d(TAG, "onCreate: linkHeadersDTO url: " + linkHeadersDTO.url);
            Log.d(TAG, "onCreate: linkHeadersDTO headers: " + linkHeadersDTO.headers);
            movie.setVideoUrl(linkHeadersDTO.url);

//            CookieManager cookieManager = CookieManager.getInstance();
//            cookieManager.setAcceptCookie(true);
//            String sessionId = linkHeadersDTO.headers.get("Cookie")+" path=/; domain=rm.freex2line.online";
//
//            Log.d(TAG, "onCreate: cookie: "+ sessionId);
//            cookieManager.setCookie("rm.freex2line.online", sessionId);
//            cookieManager.setCookie(linkHeadersDTO.headers.get("Referer"), linkHeadersDTO.headers.get("Cookie"));
//            // Sync to WebView (important!)
//            cookieManager.flush();
            webView.loadUrl(linkHeadersDTO.url, linkHeadersDTO.headers);


//           String pageUrl = "https://m.gamehub.cam/?post=4e6a49314e6a5931&wpost=aHR0cHM6Ly9tMTUuYXNkLnJlc3Q=HhZ35wNXMr,,AcFjgjB4_-ThremFtZiwlVVk_6u-dVJO9YsfDDvm1_PMFsPOF_Fjpq5o0Hoesr8jZt6dfIjEWDp_6jDZTG5qnM1vurAfVnO5DLblW6_f50BqNpIu1QGloTOIk25EGQ2Fblov1Jc9o8Oz0s3eJGdZJgpYmCVlErqtS9STA1eIEK-Qx3LSLtNVbpz60s9GoDPyFXrDcQjFPQxmP4q8kpOGJaUeCX5tdS2eCX1Rf-oYUVxAFaUV3j1IgaNagpeWQ0OmY5zgfFZxI9mr6sBiXH8fCX18nZ3atLzmtuLrLdOT798tG_ESwR2CDRpFPMBC1-F_dhUAShS19jdqNe";
//            //        pageUrl = "https://www.google.com";
//            HashMap<String, String> headers = new HashMap<>();
//            headers.put("Referer", "https://m15.asd.rest/");
//        headers.put("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");


//            webView.loadUrl(pageUrl, headers);
        }
    }

    /**
     * initialize essential variables
     */
    private void initializeThings() {
        webView = findViewById(R.id.webView);
        cursorLayout = new CursorLayout(this);
        openedForResult = getIntent().getBooleanExtra("openedForResult", false);
        isCookieFetch = getIntent().getBooleanExtra(Movie.KEY_IS_COOKIE_FETCH, false);
        selectedRowIndex = getIntent().getIntExtra(Movie.KEY_CLICKED_ROW_ID, 0);
        selectedItemIndex = getIntent().getIntExtra(Movie.KEY_CLICKED_MOVIE_INDEX, 0);
//         Log.d(TAG, "initializeThings: isCookieFetch: "+ isCookieFetch);
         Log.d(TAG, "initializeThings: selectedRowIndex: "+ selectedRowIndex);
         Log.d(TAG, "initializeThings: selectedItemIndex: "+ selectedItemIndex);
        gson = new Gson();
        activity = this;

        //######
        getSupportActionBar().hide();
        initilizeNotificationBar();
        //######
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPressed();
            }
        };

        // Add the callback to the back pressed dispatcher
        getOnBackPressedDispatcher().addCallback(this, callback);

        // setContentView(webView);
        dbHelper = MovieDbHelper.getInstance(this);

        listRowAdapter = new ArrayObjectAdapter(new CardPresenter());

        movie = Util.recieveSelectedMovie(getIntent());

        server = ServerConfigRepository.getInstance().getServer(movie.getStudio());
        if (server == null) {
            Toast.makeText(activity, "undefined server", Toast.LENGTH_SHORT).show();
            return;
        }
        config = ServerConfigRepository.getInstance().getConfig(server.getServerId());

        playedVideoLinks = new HashSet<>();

        configureWebview(webView);
    }

    private LinkHeadersDTO prepareLoadingLink(Movie movie) {
        LinkHeadersDTO linkHeaders = new LinkHeadersDTO();
        String url = movie.getVideoUrl();
        if (url.contains("||")) {
            linkHeaders.headers = Util.parseParamsToMap(movie.getVideoUrl());
            linkHeaders.url = url.substring(0, url.indexOf("||"));

            Log.d(TAG, "browser: map:" + linkHeaders.toString() + ", url:" + url);
            return linkHeaders;
        }
        if (url.contains("|")) {
//                Map<String, String> map = parseParamsToMap(movie.getVideoUrl());
            String[] parts = url.split("\\|", 2);
            String cleanUrl = parts[0];
            Map<String, String> headers = new HashMap<>();

            if (parts.length == 2) {
                linkHeaders.headers = Util.extractHeaders(parts[1]);
                // Log.d("TAG", "buildMediaSource: h:" + parts[1]);
            }
            linkHeaders.url = cleanUrl;

            // Log.d(TAG, "browser: map:" + linkHeaders.toString() + ", url:" + cleanUrl);
            return linkHeaders;
        }
        // Log.d(TAG, "onCreate: url:" + url);
        linkHeaders.url = url;
        return linkHeaders;
    }

    /**
     * configure the web view before loading the url like setting the custom userAgent and cache
     *
     * @param webView
     */
    private void configureWebview(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

// Set User-Agent to a desktop browser (Chrome)
        webSettings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
//        webSettings.setUserAgentString("Android 6");

        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true); // Load the WebView with an overview mode
        webSettings.setUseWideViewPort(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.addJavascriptInterface(new MyJavaScriptInterface(), "MyJavaScriptInterface");
        // Set a custom User-Agent string
       String customUserAgent = server.getCustomUserAgent(movie.getState());

        if (customUserAgent != null) {
            webSettings.setUserAgentString(customUserAgent);
        }

        if (movie.getState() == Movie.ITEM_STATE ||
                movie.getState() == Movie.RESOLUTION_STATE ||
                movie.getFetch() == Movie.REQUEST_CODE_MOVIE_UPDATE
        ) {
            webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        }

        // Enable hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        // Allow autoplay without user gesture (critical for Android TV)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }

// Enable hardware acceleration for smoother playback (optional)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setFocusable(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.requestFocus();
/// ######
        webView.setWebViewClient(new Browser_Home());
//        webView.setWebViewClient(new WebViewClient());

//        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebChromeClient(new ChromeClient());

    }

    private void initilizeNotificationBar() {
        hideRedirectBar = false;
        urlNotificationBar = findViewById(R.id.urlNotificationBar);
        urlBar = findViewById(R.id.urlBar);
        Button buttonHide = findViewById(R.id.buttonHide);
        Button buttonYes = findViewById(R.id.buttonYes);
        Button buttonNo = findViewById(R.id.buttonNo);

        buttonHide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideRedirectBar = true;
                urlNotificationBar.setVisibility(View.GONE);
                autoHideHandler.removeCallbacks(autoHideRunnable);
            }
        });

        buttonYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.loadUrl(redirectUrl);
                urlNotificationBar.setVisibility(View.GONE);
                autoHideHandler.removeCallbacks(autoHideRunnable);
            }
        });

        buttonNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                urlNotificationBar.setVisibility(View.GONE);
                autoHideHandler.removeCallbacks(autoHideRunnable);
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pause the WebView
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
            webView.stopLoading(); // Stops loading any ongoing content
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume the WebView
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        // Clean up the WebView to prevent memory leaks
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
        }
        super.onDestroy();
    }

    //    @Override
//    public void onBackPressed() {
    private void handleBackPressed() {
        Log.d(TAG, "handleBackPressed: ");
        //check if waiting time between the second click of back button is greater less than 2 seconds so we finish the app
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            webView.stopLoading();
            finish();
        } else {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();// if there is previous page open it
                Toast.makeText(activity, "backPressed", Toast.LENGTH_SHORT).show();
            } else
                Toast.makeText(this, "Press back 2 time to exit", Toast.LENGTH_SHORT).show();
        }
        backPressedTime = System.currentTimeMillis();
    }

    class MyJavaScriptInterface {
        @JavascriptInterface
        public void myMethod(String elementJson) {
            Log.d(TAG, "myMethod: xxxx: "+ elementJson);
            Log.d(TAG, "movie.getFetch: "+ movie.getFetch());


            if (elementJson == null || elementJson.isEmpty() || elementJson.equals("[]")) {
                Toast.makeText(activity, "حدث خطأ...", Toast.LENGTH_LONG).show();
                return;
            }

            ServerConfig config = ServerConfigRepository.getInstance().getConfig(server.getServerId());
            // Parse the JSON string
            Type movieListType = new TypeToken<List<Movie>>() {
            }.getType();
            ArrayList<Movie> movies = gson.fromJson(elementJson, movieListType);

            // Check if elementJson is an HTML content or specific commands
            if (movie.getFetch() == Movie.REQUEST_CODE_MOVIE_UPDATE) {
                handleJSResultMovieUpdate(elementJson, movies);
//                Log.d(TAG, "myMethod: REQUEST_CODE_MOVIE_UPDATE ");
                return;
            }

            if (elementJson.startsWith("##")) {
                handleIframeRedirect(elementJson);
                return;
            }

            handleJSResultDefaultCase(elementJson, movies, config);
        }

        private void handleJSResultDefaultCase(String elementJson, ArrayList<Movie> movies, ServerConfig config) {
            Log.d(TAG, "handleDefault: processing elementJson");
            if (movie.getState() == Movie.COOKIE_STATE) {
                // mainMovie = null to set each movie as its a main movie
                processOnCookieState(
                        elementJson,
                        movies,
                        config
                );
                return;
            }
            processOnOtherMovieStates(
                    elementJson,
                    movies,
                    config
            );
        }

        private void processOnOtherMovieStates(String elementJson, List<Movie> movies, ServerConfig config) {
            MovieFetchProcess movieFetchProcess = server.handleJSResult(elementJson, (ArrayList<Movie>)movies, movie);
            Log.d(TAG, "processOnOtherMovieStates: movie: "+ movie);
            Log.d(TAG, "processOnOtherMovieStates: elementJson: "+ elementJson);
            Log.d(TAG, "processOnOtherMovieStates: sublist: "+ movies);
            switch (movieFetchProcess.stateCode) {
                case MovieFetchProcess.FETCH_PROCESS_UPDATE_CONFIG_AND_RETURN_RESULT:
                    // config being updated in the server and here saved to db
                    // this case is only for akwam and old_akwam servers
                    Log.d(TAG, "processOnOtherMovieStates: update config");
                    ServerConfigRepository.getInstance().updateConfig(config);
                    setResult(
                            Activity.RESULT_OK,
                            Util.generateIntentResult(movieFetchProcess.movie, selectedRowIndex, selectedItemIndex)
                    );
                    finish();
                    return;
                case MovieFetchProcess.FETCH_PROCESS_RETURN_RESULT:
                    // this case is arabseed
//                    Log.d(TAG, "processOnOtherMovieStates: FETCH_PROCESS_RETURN_RESULT");
                    setResult(
                            Activity.RESULT_OK,
                            Util.generateIntentResult(movieFetchProcess.movie, selectedRowIndex, selectedItemIndex)
                    );
                    finish();
                    return;
                default:
                    movieFetchProcess.movie.setFetch(Movie.NO_FETCH_MOVIE_AT_START); // Very important to change the fetch state not to go in an infinite loop
                    Log.d(TAG, "processOnOtherMovieStates: default");
                    Util.openVideoDetailsIntent(movieFetchProcess.movie, activity);
                    finish();
            }
        }

        private void processOnCookieState(String elementJson, ArrayList<Movie> movies, ServerConfig config) {
            Log.d(TAG, "processOnCookieState size: " + movies.size());
            if (movies.isEmpty()){
                return;
            }
            movie.setSubList(movies);
            setResult(Activity.RESULT_OK, Util.generateIntentResult(movie, selectedRowIndex, selectedItemIndex));
            finish();
        }

        private void handleIframeRedirect(String elementJson) {
//            Log.d(TAG, "handleIframeRedirect: "+elementJson);
            final String url = elementJson.replace("##", "");
            runOnUiThread(() -> webView.loadUrl(url));
        }

        private void handleJSResultMovieUpdate(String elementJson, ArrayList<Movie> movies) {
            //update main movie desc
            if (!movies.isEmpty()) {
                String sampleMovieDesc = movies.get(0).getDescription();
                movie.setDescription(sampleMovieDesc);
            }
            movie.setSubList(
                    movies
            );
//             Log.d(TAG, "handleJSResultMovieUpdate: mainMovie: "+ movie.getMainMovie());
            setResult(Activity.RESULT_OK, Util.generateIntentResult(movie, selectedRowIndex, selectedItemIndex));
            finish();
        }
    }

    private class ChromeClient extends WebChromeClient {
        private View mCustomView;
        private CustomViewCallback mCustomViewCallback;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;

        ChromeClient() {
        }

        private static final String TAG = "ChromeClient";

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
             Log.d(TAG, "onCreateWindow: xxx");
            return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                Log.d(TAG, "onJsAlert: " + message);
            result.cancel();
            //super.onJsAlert(view, url, null, result);
            return true;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
                Log.d(TAG, "onJsBeforeUnload: " + url + ", m:" + message + ", re:" + result);
            result.cancel();
            return true;
        }


        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
              Log.d(TAG, "onJsPrompt: " + message);
            result.cancel();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                Log.d(TAG, "onJsConfirm: " + message);
            result.cancel();
            return true;
        }


        public Bitmap getDefaultVideoPoster() {
                Log.d(TAG, "getDefaultVideoPoster: " + mCustomView);
            if (mCustomView == null) {
                return null;
            }
            //    Log.d(TAG, "getDefaultVideoPoster: " + BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573));
            return BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573);
        }

        public void onHideCustomView() {
                Log.d(TAG, "onHideCustomView: ");
            ((FrameLayout) getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
        }

        public void onShowCustomView(View paramView, CustomViewCallback paramCustomViewCallback) {
                Log.d(TAG, "onShowCustomView: ");
            if (this.mCustomView != null) {
                onHideCustomView();
                return;
            }
            this.mCustomView = paramView;
            this.mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            this.mOriginalOrientation = getRequestedOrientation();
            this.mCustomViewCallback = paramCustomViewCallback;
            //this.mOriginalMouseElevation = mouseArrow.getElevation();

            FrameLayout frameLayout = (FrameLayout) paramView;
            View focusedChild = frameLayout.getFocusedChild();

            // Set the mouse arrow's elevation to a higher value than the custom view's elevation
            if (focusedChild != null) {
                focusedChild.getElevation();
                //   mouseArrow.setElevation(focusedChild.getElevation() + 1);
            }

            FrameLayout.LayoutParams newFrame = new FrameLayout.LayoutParams(-1, -1);
            ((FrameLayout) getWindow().getDecorView()).addView(this.mCustomView, newFrame);
            getWindow().getDecorView().setSystemUiVisibility(3846 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            paramCustomViewCallback.onCustomViewHidden();
        }
    }


    private class Browser_Home extends WebViewClient {
        boolean cookieFound = false;
        Map<String, String> headers;
        CookieManager cookieManager = CookieManager.getInstance();

        Browser_Home() {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
            cookieManager.setAcceptCookie(true);
            headers = new HashMap<>();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            Log.d(TAG, "onReceivedError: eeee: "+error.toString());
            super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
              Log.d(TAG, "onReceivedHttpError: eeee:"+errorResponse.getEncoding()+", "+errorResponse.getStatusCode()+", "+errorResponse.getResponseHeaders().toString()+ "; "+ request.getUrl());
            super.onReceivedHttpError(view, request, errorResponse);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
               Log.d(TAG, "onReceivedSslError: eeee:"+error.toString());
//            super.onReceivedSslError(view, handler, error);
            handler.proceed(); // Bypass SSL errors (use cautiously!)
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
           Uri url = request.getUrl();
             Log.d(TAG, "shouldInterceptRequest: " + url.getHost());
             Log.d(TAG, "shouldInterceptRequest: " + request.getRequestHeaders());
            if (LinkFilterService.isAdDomain(url.getHost())){
                Log.d(TAG, "Blocking resource: " + url.getHost());
                return new WebResourceResponse("text/plain", "utf-8", null); // Return an empty response
            }

            headers = request.getRequestHeaders();

            if (
                    server.shouldInterceptRequest(view, request, movie) &&
                            LinkFilterService.isSupportedMedia(request, movie.getVideoUrl())
            ) {
//            if (isSupportedStateInInterceptRequest() && LinkFilterService.isSupportedMedia(request)) {
                    return handleSupportedMedia(view, request, headers);
//                Log.d(TAG, "shouldInterceptRequest:xxxx videoUrl: "+ request.getUrl().toString());
            }
            return super.shouldInterceptRequest(view, request);
        }

        private WebResourceResponse handleSupportedMedia(WebView view, WebResourceRequest request, Map<String, String> headers) {
            Log.d(TAG, "handleSupportedMedia: "+ request.getUrl().toString());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.stopLoading();
                }
            });
//                Intent returnIntent = new Intent(activity, ExoplayerMediaPlayer.class);
            Movie mm = Movie.clone(movie);
            mm.setState(Movie.VIDEO_STATE);
            mm.setType(MovieType.VIDEO);

//            String videoUrl = request.getUrl().toString().replace("&amp;amp;", "&");
            String videoUrl = request.getUrl().toString();
            mm.setVideoUrl(videoUrl + Util.generateHeadersForVideoUrl(request.getRequestHeaders()));
            Log.d(TAG, "handleSupportedMedia:xxxx videoUrl: "+ mm.getVideoUrl());

            if (!openedForResult) {
                Log.d(TAG, "handleSupportedMedia: if (!openedForResult) 1029");
                startExoplayer(mm);
                return null;
            }
            // Log.d(TAG, "shouldInterceptRequest: video shouldInterceptRequest. " + mm);

            setResult(Activity.RESULT_OK, Util.generateIntentResult(mm, selectedRowIndex, selectedItemIndex));
            activity.finish();

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    view.stopLoading();
                }
            });

            return null;
//                return super.shouldInterceptRequest(view, request);
//                activity.finish();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
//            webView.evaluateJavascript("document.cookie", value -> {
//                Log.d("WebView", "document.cookie = " + value);
//            });

            Log.d(TAG, "shouldOverrideUrlLoading: " + request.getUrl());
            if (request.getUrl().getScheme() == null || !request.getUrl().getScheme().contains("http")) {
                Log.d(TAG, "shouldOverrideUrlLoading 1 true: "+request.getUrl().toString());
                return true;
            }
//            if (true){
//                return super.shouldOverrideUrlLoading(view, request);
//            }
            String url = request.getUrl().toString();
            redirectUrl = url;



            String newUrl = request.getUrl().toString().length() > 25 ? request.getUrl().toString().substring(0, 25) : request.getUrl().toString();

            if (!server.shouldOverrideUrlLoading(movie, view, request)) {
                Log.d(TAG, "shouldOverrideUrlLoading 2 false: "+request.getUrl().toString());
                return false;
            }

//            if (movie.getState() == Movie.COOKIE_STATE ) {
            if (movie.getState() == Movie.COOKIE_STATE  || !Util.shouldOverrideUrlLoading(newUrl)) {
                if (url.startsWith("##")) {
                    url = url.replace("##", "");
//                    Log.d(TAG, "shouldOverrideUrlLoading: replace ##");
                }

                view.loadUrl(url);
                return true;
            }

            showRedirectBar(newUrl);

             Log.d(TAG, "shouldOverrideUrlLoading:6 true: " + url);
            return true;
        }

        private void showRedirectBar(String newUrl) {
            if (!hideRedirectBar && !movie.getStudio().equals(Movie.SERVER_AKWAM)) {
                urlBar.setText(newUrl);
                urlNotificationBar.setVisibility(View.VISIBLE);
                autoHideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        urlNotificationBar.setVisibility(View.GONE);
                    }
                };
                autoHideHandler.postDelayed(autoHideRunnable, 10000);
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.d(TAG, "onPageStarted: " + url);
            BrowserActivity.EXECUTE_JS = true;
            BrowserActivity.RESULT_COUNTER = 0;
            //executedJS = false;
            currentVideoUrl = "";
            // if (!executedJS){
            //  Log.d(TAG, "onPageStarted: xxxx executing");
            String script = server.getWebScript(WEB_VIEW_MODE_ON_PAGE_STARTED, movie);
            if (script != null) {
                view.evaluateJavascript(script, null);
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
//              Log.d(TAG, "onPageFinished: " + url);
            String script = server.getWebScript(WEB_VIEW_MODE_ON_PAGE_FINISHED, movie);
            if (script != null) {
                view.evaluateJavascript(script, null);
            }
            updateCookies();
        }

        private void updateCookies() {
//            CookieManager cookieManager = CookieManager.getInstance();
            String cookies = cookieManager.getCookie(config.getUrl());
//            url = "https://www.faselhds.care/account/login";
//            Log.d(TAG, "Cookies for " + config.getUrl() + ": " + cookies);
            Log.d(TAG, "updateCookies for " + movie.getVideoUrl() + ": " + cookies);
            if (cookies == null) {
                return;
            }
            config.setStringCookies(cookies);
            config.setHeaders(headers);
            new Thread(() -> ServerConfigRepository.getInstance().updateConfig(config)).start();
        }
        @Override
        public void onLoadResource(WebView view, String url) {
//            Log.d(TAG, "onLoadResource: "+url);
            if (shouldEvaluateJavascript()) {
                evaluateJavascript(view);
            }

            if (server.shouldCleanWebPage(url, movie)) {
                HtmlPageService.cleanWebPage(view, true);
            }

//            if (shouldProcessVideo(url)) {
//                processVideoResource(view, url);
//            }

            super.onLoadResource(view, url);
        }

        private void processVideoResource(WebView view, String url) {
            BrowserActivity.EXECUTE_JS = false;
            BrowserActivity.RESULT_COUNTER++;
            String newUrl = getProcessedVideoUrl(url);
            currentVideoUrl = url;
            Movie mov = Movie.clone(movie);
            mov.setVideoUrl(newUrl);
            mov.setState(Movie.VIDEO_STATE);
            mov.setType(MovieType.VIDEO);
            Log.d(TAG, "onLoadResource: isVideo: " + newUrl);
            if (!openedForResult) {
                Log.d(TAG, "processVideoResource: 1272");
                startExoplayer(mov);
                return;
            }
            setResult(Activity.RESULT_OK, Util.generateIntentResult(mov, selectedRowIndex, selectedItemIndex));
            runOnUiThread(view::stopLoading);
            activity.finish();
        }

        private String getProcessedVideoUrl(String url) {
            String newUrl = url;
            if (!movie.getStudio().equals(Movie.SERVER_FASELHD)) {
                if (movie.getStudio().equals(Movie.SERVER_CimaNow)) {
                    if (Util.extractDomain(newUrl, false, false).contains("cimanow")) {
                        url = url.replace("-360p.", "-720p.");
                    }
                }
                newUrl = Util.generateMaxPlayerHeaders(url, headers);
            }
            return newUrl;
        }

        private boolean shouldProcessVideo(String url) {
            return BrowserActivity.RESULT_COUNTER < 3 &&
                    !url.equals(currentVideoUrl) &&
                    LinkFilterService.isVideo(url, movie) &&
                    !(movie.getStudio().equals(Movie.SERVER_ARAB_SEED) &&
                            movie.getState() == Movie.BROWSER_STATE
                    );
        }

        private void evaluateJavascript(WebView view) {
            BrowserActivity.RESULT_COUNTER++;
            //hhhhhh view.evaluateJavascript(getScript(WEB_VIEW_MODE_ON_LOAD_RESOURCES), null);
            String script = server.getWebScript(WEB_VIEW_MODE_ON_LOAD_RESOURCES, movie);
            if (script != null) {
                view.evaluateJavascript(script, null);
            }

        }

        private boolean shouldEvaluateJavascript() {
            return BrowserActivity.RESULT_COUNTER < 2 && webView.getProgress() == 100;
        }
    }

    private void startExoplayer(Movie mov) {
        Log.d(TAG, "processVideoResource: openExoPlayer !openedForResult: "+ currentVideoUrl);

        String videoUrl = mov.getVideoUrl();
        if (playedVideoLinks.contains(videoUrl)) {
            Log.d(TAG, "Video link already played: " + videoUrl + ". Not opening ExoPlayer.");
            return;
        }

        playedVideoLinks.add(videoUrl);
        currentVideoUrl = videoUrl;
        Util.openExoPlayer(mov, activity, false);
    }

    private void testCookie(WebView view, WebResourceRequest request) {
//        String url = "https://shahid4uu.cam/login";
        String url = "https://wecima.show/watch/%d9%85%d8%b4%d8%a7%d9%87%d8%af%d8%a9-%d9%85%d8%b3%d9%84%d8%b3%d9%84-fox-spirit-matchmaker-love-in-pavilion-%d9%85%d9%88%d8%b3%d9%85-1-%d8%ad%d9%84%d9%82%d8%a9-10/";

        String cookieTest = CookieManager.getInstance().getCookie("https://wecima.show/");
        if (cookieTest != null) {
            //                String url = "https://www.faselhd.link/account/login";
            Connection connection = Jsoup.connect(url);//.sslSocketFactory(getSSLSocketFactory());
            connection.ignoreHttpErrors(true);
            connection.ignoreContentType(true);
            connection.headers(request.getRequestHeaders());
            // String cookie = CookieManager.getInstance().getCookie("https://shahid4uu.cam");
            connection.cookies(Util.getMapCookies(cookieTest));
            Document doc = null;
            try {
                doc = connection.get();
                String title = doc.title();
//                Log.d(TAG, "testCookie shouldInterceptRequest: cookie test title:" + title);
                if (!title.contains("moment")) {
                    // Log.d(TAG, "testCookie shouldInterceptRequest: success headers:" + request.getRequestHeaders().toString());
                    // Log.d(TAG, "testCookie shouldInterceptRequest: success cookies:" + cookieTest);
                }
            } catch (IOException e) {
//            throw new RuntimeException(e);
                //Document doc = Jsoup.parse(htmlContent);
                Log.d(TAG, "testCookie: error: " + e.getMessage());

            }
        }
    }

    private boolean isValidReferer(String referer) {
        boolean result = false;
        referer = Util.getValidReferer(referer);
        if (referer != null) {
            if (config != null && referer.contains(config.getUrl())) {
                return true;
            }
            Pattern pattern = Pattern.compile("fas[ei]|shah[ei]d|c[ie]{0,2}m|ak[waom]{0,2}");
            Matcher matcher = pattern.matcher(referer);
            result = matcher.find();
        }
        // Log.d(TAG, "isValidReferer: " + result + ", " + referer);
        return result;
    }
}