package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.MimeTypeMap;
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
import com.omerflex.entity.ServerConfig;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.service.database.MovieDbHelper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * start a webView browser
 */
public class BrowserActivity_old_2 extends AppCompatActivity {

    public static final int WEB_VIEW_MODE_ON_LOAD_RESOURCES = 1;
    public static final int WEB_VIEW_MODE_ON_PAGE_STARTED = 0;
    public static final int WEB_VIEW_MODE_ON_PAGE_FINISHED = 2;
    //variable for back button confirmation
    private long backPressedTime;
    static String TAG = "BrowserActivity";
    AbstractServer server;

    Intent receivedIntent;
    Movie movie;
    static boolean saved;
    public boolean openedForResult = false;
    ArrayObjectAdapter listRowAdapter;

    // Initialize Class
    private Handler handler = new Handler();
    private Timer timer = new Timer();

    // private ImageView mouseArrow;
    private float arrowUpX;
    private float arrowUpY;
    private int lastKeyCode = 0;
    private Timer arrowTimer = null;
    private float speed = 10;
    // Screen Size
    private int screenWidth;
    private int screenHeight;
    int webViewWidth;
    int webViewHeight;
    //hide mouse in 3 secod
    private Handler hideHandler;
    private Runnable hideRunnable;
    private boolean arrowVisible = true;
    private boolean allowArrowEdgeScrolling = true;
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

    //#############
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        webView = findViewById(R.id.webView);
        cursorLayout = new CursorLayout(this);
        openedForResult = getIntent().getBooleanExtra("openedForResult", false);

//######
        initilizeNotificationBar();
        //######

        gson = new Gson();
        activity = this;


        // Handle the back button press with the new onBackPressedDispatcher
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




        // webView = MyApplication.getWebView();
        // setContentView(webView);
        dbHelper = MovieDbHelper.getInstance(this);

        listRowAdapter = new ArrayObjectAdapter(new CardPresenter());

        getSupportActionBar().hide();

        movie = Util.recieveSelectedMovie(getIntent());

//        String movieJson = getIntent().getStringExtra("sub");
//        Gson gson = new Gson();
//        Type type = new TypeToken<List<Movie>>(){}.getType();
//        List<Movie> movieList = gson.fromJson(movieJson, type);


        Log.d(TAG, "onCreate: BrowserActivity: " + movie.getStudio());


        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
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
        //String customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36";
        String customUserAgent = "Android 7";
        // String customUserAgent = "Mozilla/5.0 (Linux; Android 8.1.0; Android SDK built for x86 Build/OSM1.180201.031; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/69.0.3497.100 Mobile Safari/537.36 Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:106.0) Gecko/20100101 Firefox/106.0";
        if (movie.getState() == Movie.COOKIE_STATE
                || movie.getStudio().equals(Movie.SERVER_ARAB_SEED)
                || movie.getStudio().equals(Movie.SERVER_FASELHD)
                || movie.getStudio().equals(Movie.SERVER_MyCima)
        ) {
            webSettings.setUserAgentString(customUserAgent);
        }

        if (
                movie.getState() == Movie.ITEM_STATE ||
                        movie.getState() == Movie.RESOLUTION_STATE
                        || movie.getFetch() == Movie.REQUEST_CODE_MOVIE_UPDATE
        ) {
            webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        }

        // Enable hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
/*        webSettings.setJavaScriptEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webSettings.setAllowFileAccess(true);
        webSettings.setLoadsImagesAutomatically(false);
       // webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        webSettings.setDomStorageEnabled(true);

//        String userAgent = "Mozilla/5.0 (Linux; Android 9; SM-G960F Build/PPR1.180610.011) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.149 Mobile Safari/537.36";
//        webSettings.setUserAgentString(userAgent);

        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setPluginState(WebSettings.PluginState.ON);
        webSettings.setMediaPlaybackRequiresUserGesture(false);*/


        ///// mouseArrow
//        mouseArrow = (ImageView) findViewById(R.id.mouse_arrow);
// Get Screen Size.
//        WindowManager wm = getWindowManager();
//        Display disp = wm.getDefaultDisplay();
//        Point size = new Point();
//        disp.getSize(size);

//###################
//        webViewWidth = webView.getWidth();
//        webViewHeight = webView.getHeight();
//        screenWidth = getResources().getDisplayMetrics().widthPixels;
//        screenHeight = getResources().getDisplayMetrics().heightPixels;
//        // Calculate the center of the screen
//        int centerX = screenWidth / 2;
//        int centerY = screenHeight / 2;
//
//// Set the position of the mouse arrow to the center of the screen
//        mouseArrow.setX(centerY);
//        mouseArrow.setY(centerY);

        // Initialize the handler and runnable
//        hideHandler = new Handler();
//        hideRunnable = new Runnable() {
//            @Override
//            public void run() {
//                // Hide the mouse arrow
//                mouseArrow.setVisibility(View.INVISIBLE);
//                arrowVisible = false;
//            }
//        };

/////////////////////////////////////////

        // webView.setFocusable(false);  // Set the WebView to not focusable
//        webView.setOnKeyListener(new View.OnKeyListener() {
//            @Override
//            public boolean onKey(View view, int i, KeyEvent keyEvent) {
//               String message = "key: "+keyEvent.getKeyCode() ;
//                Log.d(TAG, "onKey: "+message);
//                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK){
//                    onBackPressed();
//                    return true;
//                }
////                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
//                return false;
//            }
//        });


//        server = ServerManager.determineServer(movie, listRowAdapter, BrowserActivity.this, null);
        server = ServerConfigRepository.getInstance().getServer(movie.getStudio());
        if (server == null){
            Toast.makeText(activity, "undefined server", Toast.LENGTH_SHORT).show();
            return;
        }
        config = ServerConfigRepository.getInstance().getConfig(server.getServerId());
        //   simpleWebView.setWebViewClient(new CustomWebViewClient());
        //   webView.setWebChromeClient(new ChromeClient());
        //   webView.setWebViewClient(new CustomWebViewClient(this));
        // Create an OkHttpClient
        // OkHttpClient client = new OkHttpClient();

// Create an instance of OkHttpWebViewClient, passing in the OkHttpClient
        //   OkHttpWebViewClient webViewClient = new OkHttpWebViewClient(client);

// Set the OkHttpWebViewClient as the WebViewClient for your WebView
        //webView.setWebViewClient(webViewClient);
        webView.setWebViewClient(new Browser_Home());
//        webView.setWebViewClient(new WebViewClient());

//        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebChromeClient(new ChromeClient());
        if (savedInstanceState == null) {
            redirectUrl = movie.getVideoUrl();

            String url = movie.getVideoUrl();
            Log.d(TAG, "onCreate: url: "+url);
            if (url.contains("||")) {
                Map<String, String> map = parseParamsToMap(movie.getVideoUrl());
                url = url.substring(0, url.indexOf("||"));

                Log.d(TAG, "browser: map:" + map.toString() + ", url:" + url);
                webView.loadUrl(url, map);
            }else if (url.contains("|")) {
//                Map<String, String> map = parseParamsToMap(movie.getVideoUrl());
                String[] parts = url.split("\\|", 2);
                String cleanUrl = parts[0];
                Map<String, String> headers = new HashMap<>();

                if (parts.length == 2) {
                    headers = Util.extractHeaders(parts[1]);
                    Log.d("TAG", "buildMediaSource: h:" + parts[1]);
                }

                Log.d(TAG, "browser: map:" + headers.toString() + ", url:" + cleanUrl);
                if (!headers.isEmpty()){
                    webView.loadUrl(cleanUrl, headers);
                }else {
                    webView.loadUrl(cleanUrl);
                }
            } else {
                Log.d(TAG, "onCreate: url:" + url);
                webView.loadUrl(url);
            }

        }

    }

//    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            Log.d(TAG, "onKeyDown: "+keyCode);
//            handleBackPressed();
//        }
////            onBackPressed();
//////            if (webView.canGoBack()) {
//////                // If the WebView has a history, go back immediately
//////                webView.stopLoading(); // Stop any ongoing page loading
//////                webView.goBack();
//////                return true; // Indicate that we've handled the back press
//////            } else {
//////                // If there's no history, handle the default back press (exit the activity)
//////                finish();
//////            }
////        }
//        return super.onKeyDown(keyCode, event);
//    }

    public Map<String, String> parseParamsToMap(String params) {
        params = params.substring(params.indexOf("||") + 2);
        Map<String, String> map = new HashMap<>();
        String[] pairs;
        if (params.contains("&")) {
            pairs = params.split("&");
        } else {
            pairs = new String[]{params};
        }
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            map.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return map;
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

//    private void simulateMouseClick(WebView webView, float x, float y, int action) {
//        long uptimeMillis = SystemClock.uptimeMillis();
//        long uptimeMillis2 = SystemClock.uptimeMillis();
//        Log.d(TAG, "simulateMouseClick: "+action);
//        MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
//        pointerProperties.id = 0;
//        pointerProperties.toolType = 1;
//        MotionEvent.PointerProperties[] pointerPropertiesArr = {pointerProperties};
//        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
//        pointerCoords.x = x;
//        pointerCoords.y = y;
//        pointerCoords.pressure = 1.0f;
//        pointerCoords.size = 1.0f;
//        webView.dispatchTouchEvent(MotionEvent.obtain(uptimeMillis, uptimeMillis2, action, 1, pointerPropertiesArr, new MotionEvent.PointerCoords[]{pointerCoords}, 0, 0, 1.0f, 1.0f, 0, 0, 0, 0));
//    }
//
//    public boolean changePos(KeyEvent event) {
//        int action = event.getKeyCode();
//        //Log.d(TAG, "changePos: x:"+mouseArrow.getX()+", Y:"+mouseArrow.getY());
//        boolean shouldInterrept = true;
//
//        // Show the mouse arrow if it is hidden
//        if (!arrowVisible) {
//            mouseArrow.setVisibility(View.VISIBLE);
//            arrowVisible = true;
//        }
//
//// Cancel the delayed hide task
//        hideHandler.removeCallbacks(hideRunnable);
//
//// Schedule a new hide task to run after 3 seconds
//        hideHandler.postDelayed(hideRunnable, 3000);
//
//
//        // Check if the mouse arrow reaches the end of the screen
//        //Toast.makeText(this, "key: "+action, Toast.LENGTH_SHORT).show();
//        switch (action) {
//            case KeyEvent.KEYCODE_DPAD_LEFT:
//                // Move the mouse arrow speed pixels to the left
//                mouseArrow.setX(mouseArrow.getX() - speed);
//                // Limit the position of the mouse arrow to the left edge of the screen
//                if (mouseArrow.getX() < 0) {
//                    mouseArrow.setX(0);
//                }
//                break;
//            case KeyEvent.KEYCODE_DPAD_RIGHT:
//                // Move the mouse arrow speed pixels to the right
//                mouseArrow.setX(mouseArrow.getX() + speed);
//                // Limit the position of the mouse arrow to the right edge of the screen
//                if (mouseArrow.getX() + mouseArrow.getWidth() > screenWidth) {
//                    mouseArrow.setX(screenWidth - mouseArrow.getWidth());
//                }
//                break;
//            case KeyEvent.KEYCODE_DPAD_UP:
//                // Move the mouse arrow speed pixels up
//                mouseArrow.setY(mouseArrow.getY() - speed);
//                // Limit the position of the mouse arrow to the top edge of the screen
//                if (mouseArrow.getY() < 0) {
//                    mouseArrow.setY(0);
//                }
//                break;
//            case KeyEvent.KEYCODE_DPAD_DOWN:
//                // Move the mouse arrow speed pixels down
//                mouseArrow.setY(mouseArrow.getY() + speed);
//                // Limit the position of the mouse arrow to the bottom edge of the screen
//                if (mouseArrow.getY() + mouseArrow.getHeight() > screenHeight) {
//                    mouseArrow.setY(screenHeight - mouseArrow.getHeight());
//                }
//                break;
//            case KeyEvent.KEYCODE_ENTER:
//            case KeyEvent.KEYCODE_DPAD_CENTER:
//                if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) {
//                    simulateMouseClick(webView, mouseArrow.getX(), mouseArrow.getY(), event.getAction());
//                }
//            default:
//                shouldInterrept = false; // if to interrupt the key event or not
//                break;
//        }
//
//        // Check if the user is pressing the same key as before
//        if (action == lastKeyCode) {
//            // Check if the timer has started
//            if (timer == null) {
//                // Start the timer
//                timer = new Timer();
//                timer.scheduleAtFixedRate(new TimerTask() {
//                    @Override
//                    public void run() {
//                        // Increase the arrow's speed
//                        speed += 0.95;
//                    }
//                }, 0, 10); // Increase the speed every 100ms
//            }
//        } else {
//            // Reset the arrow's speed
//            speed = 10;
//
//            // Stop the timer
//            if (timer != null) {
//                timer.cancel();
//                timer = null;
//            }
//        }
//
//// Update the last key code
//        lastKeyCode = action;
//
//        if (allowArrowEdgeScrolling) {
//            // Scroll the screen horizontally when the mouse arrow reaches the left or right edge of the screen
//            if (mouseArrow.getX() == 0) {
//                if (webView.canScrollHorizontally(-1)) {
//                    // The WebView can be scrolled horizontally
//                    webView.scrollBy((int) -speed, 0);
//                }
//            } else if (mouseArrow.getX() + mouseArrow.getWidth() == screenWidth) {
//                if (webView.canScrollHorizontally(1)) {
//                    // The WebView can be scrolled horizontally
//                    webView.scrollBy((int) speed, 0);
//                }
//            }
//
//            // Scroll the screen vertically when the mouse arrow reaches the top or bottom edge of the screen
//            if (mouseArrow.getY() == 0) {
//                if (webView.canScrollVertically(-1)) {
//                    // The WebView can be scrolled vertically
//                    webView.scrollBy(0, (int) -speed);
//                }
//            } else if (mouseArrow.getY() + mouseArrow.getHeight() == screenHeight) {
//                if (webView.canScrollVertically(1)) {
//                    // The WebView can be scrolled vertically
//                    webView.scrollBy(0, (int) speed);
//                }
//            }
//        }
//        return shouldInterrept;
//    }

//    private void start() {
//        BrowserActivity.RESULT_COUNTER = 0;
//        switch (movie.getState()) {
//            case Movie.GROUP_OF_GROUP_STATE:
//                Log.i(TAG, "Browser GROUP_OF_GROUP_STATE" + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                server.fetchGroupOfGroup(movie);
//                //webView.loadUrl(movie.getVideoUrl());
//                break;
//            case Movie.GROUP_STATE:
//                Log.i(TAG, "Browser GROUP_STATE" + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                server.fetchGroup(movie);
//                //webView.loadUrl(movie.getVideoUrl());
//                break;
//            case Movie.ITEM_STATE:
//                Log.i(TAG, "Browser ITEM_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                server.fetchItem(movie);
//                break;
//            case Movie.RESOLUTION_STATE:
//                Log.i(TAG, "Browser RESOLUTION_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                server.fetchResolutions(movie);
//                break;
//            case Movie.VIDEO_STATE:
//                Log.i(TAG, "Browser. VIDEO_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                server.startVideo(movie.getVideoUrl());
//                break;
//            case Movie.BROWSER_STATE:
//                Log.i(TAG, "Browser. BROWSER_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                server.startBrowser(movie.getVideoUrl());
//                break;
//            case Movie.COOKIE_STATE:
//                Log.i(TAG, "Browser. COOKIE_STATE " + movie.getStudio() + ". url:" + movie.getVideoUrl());
//                //server.fetchCookie(movie);
//                fetchCookie(movie);
//                break;
//            case Movie.RESULT_STATE:
//                Log.d(TAG, "start: RESULT_STATE");
//                server.fetchWebResult(movie);
//                break;
//            default:
//                server.fetchGroup(movie);
//        }
//    }
////
//    private void fetchCookie(Movie movie) {
//        WebSettings webSettings = webView.getSettings();
//
//        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
//        webView.loadUrl(movie.getVideoUrl());
//    }

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
        super.onDestroy();
        // Clean up the WebView to prevent memory leaks
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
        }
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
            }
            else
                Toast.makeText(this, "Press back 2 time to exit", Toast.LENGTH_SHORT).show();
        }
        backPressedTime = System.currentTimeMillis();
    }

    /**
     * inject js in view to remove ads
     *
     * @param view
     */
    public static void cleanWebPage(WebView view) {
        //delete all iframes  and their sub iframes
        String delIframes = "function deleteIframes() {\n" +
                "  // Get all iframes on the page\n" +
                "  const iframes = document.querySelectorAll('iframe');\n" +
                "\n" +
                "  // Iterate through each iframe\n" +
                "  for (const iframe of iframes) {\n" +
                "    // If the iframe's src is not about:blank\n" +
                "    if (iframe.src !== 'about:blank') {\n" +
                "      // Search for iframes within the found iframe and delete them\n" +
                "      deleteIframesInIframe(iframe);\n" +
                "    } else {\n" +
                "      // If the iframe's src is about:blank, delete the iframe\n" +
                "      iframe.remove();\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "function deleteIframesInIframe(iframe) {\n" +
                "  // Get all iframes within the given iframe\n" +
                "  const iframesInIframe = iframe.contentDocument.querySelectorAll('iframe');\n" +
                "\n" +
                "  // Iterate through each iframe within the given iframe\n" +
                "  for (const iframeInIframe of iframesInIframe) {\n" +
                "    // Delete the iframe\n" +
                "    iframeInIframe.remove();\n" +
                "  }\n" +
                "}\n" +
                "\n" +
                "// Call the deleteIframes function to delete all iframes on the page\n" +
                "deleteIframes();";

//        view.loadUrl("javascript: " + delIframes + "; deleteIframes();");


// delete all elements out of the body
        String jsBodyRemoveCode = "javascript:(function() { " +
                "  // Get all elements in the document" +
                "  var allElements = document.querySelectorAll('*');" +
                "  " +
                "  // Iterate through all elements" +
                "  for (var i = 0; i < allElements.length; i++) {" +
                "    var element = allElements[i];" +
                "    " +
                "    // Check if the element is outside the body element" +
                "    if (!document.body.contains(element)) {" +
                "      // If the element is outside the body, remove it" +
                "      element.remove();" +
                "    }" +
                "  }" +
                "})();";

//        view.evaluateJavascript(jsBodyRemoveCode, null);


        //remove divs with display block
        view.evaluateJavascript(
                "(function() {\n" +
                        "    var elements = document.querySelectorAll('[style*=\"display: block\"], [style*=\"display: block !important\"]');\n" +
                        "    \n" +
                        "    elements.forEach(function(element) {\n" +
                        "        element.parentNode.removeChild(element);\n" +
                        "    });\n" +
                        "})();\n",
                null
        );

        //delete ads iframes
        String jsCode_old = "(function() {" +
                "var iframes = [];" +
                "var allIframes = document.querySelectorAll('iframe');" +
                "for (var i = 0; i < allIframes.length; i++) {" +
                "var iframe = allIframes[i];" +
                "iframe.click();" +
                "while (iframe.hasChildNodes()) {\n" +
                "    iframe.removeChild(iframe.firstChild);\n" +
                "}" +
                "if (iframe.getAttribute('src') !== 'about:blank' && iframe.getAttribute('src') !== null) {" +
                "iframes.push({" +
                "src: iframe.getAttribute('src')," +
                "height: iframe.getAttribute('height')," +
                "width: iframe.getAttribute('width')," +
                "});" +
                "}" +
                "else {" +
                "if (iframe.parentNode !== null) {" +
                "  iframe.parentNode.removeChild(iframe);" +
                "}else{" +
                "iframe.remove();" +
                "}" +
                "}" +
                "}" +
                "return iframes;" +
                "})();";

        String jsCode = "(function() {\n" +
                "    var iframes = [];\n" +
                "    var allIframes = document.querySelectorAll('iframe');\n" +
                "\n" +
                "    allIframes.forEach(function(iframe) {\n" +
                "        iframe.click();\n" +
                "\n" +
                "        while (iframe.hasChildNodes()) {\n" +
                "            iframe.removeChild(iframe.firstChild);\n" +
                "        }\n" +
                "\n" +
                "        var src = iframe.getAttribute('src');\n" +
                "        if (src !== 'about:blank' && src !== null) {\n" +
                "            iframes.push({\n" +
                "                src: src,\n" +
                "                height: iframe.getAttribute('height'),\n" +
                "                width: iframe.getAttribute('width')\n" +
                "            });\n" +
                "        } else {\n" +
                "            if (iframe.parentNode !== null) {\n" +
                "                iframe.parentNode.removeChild(iframe);\n" +
                "            } else {\n" +
                "                iframe.remove();\n" +
                "            }\n" +
                "        }\n" +
                "    });\n" +
                "\n" +
                "    return iframes;\n" +
                "})();\n";
        view.evaluateJavascript(jsCode, null);
    }

    public static boolean isBlackListedUrl(String url) {

        // List of substrings to check
        List<String> patterns = Arrays.asList(
                "click",
                "brand",
                "/patrik",
                "adserver",
                ".php",
                ".gif",
                "error",
                "null",
                "/stub",
                ".html"
        );

        // Check if the URL contains any of the substrings
        for (String pattern : patterns) {
            if (url.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVideo(String url, Movie movie) {

        if (isBlackListedUrl(url)){
            return false;
        }
        List<String> patternsMovieUrl = Arrays.asList(
                "vidmoly", ".html"
        );

        String movieUrl = movie.getVideoUrl();

        // Check if the URL contains any of the substrings
        for (String pattern : patternsMovieUrl) {
            if (movieUrl.contains(pattern)) {
                return false;
            }
        }

        if ((url.contains("token=") && (url.contains("inconsistencygasdifficult") || url.contains("video-delivery")))) {
//        if ((url.contains("token=") && (url.contains("inconsistencygasdifficult")))) {
            return false;
        }

        // Check the file extension
        if (url.endsWith(".mp4") || (url.contains("file_code=") && !(url.contains("embed") || url.contains("watch"))) ||
                (url.contains("token=") && !(url.contains("embed") || url.contains("watch"))) ||
                (url.endsWith(".mov") && !url.contains("_ads")) || url.endsWith(".avi") || url.endsWith(".wmv") || url.endsWith(".m3u") || url.endsWith(".m3u8") || url.endsWith(".mkv") || url.contains(".m3u8")) {
            // The URL is likely a video
            // Log.d(TAG, "isVideo: 1");
            if (url.endsWith(".mp4")) {
                //avoid audio only mp4
                String newUrl = url.substring(url.lastIndexOf("/"));
                //   Log.d(TAG, "isVideo: newUrl:" + newUrl);
                if (newUrl.contains("_a_") || url.contains("themes") || url.contains("/test") || url.contains("cloudfront") || url.length() < 50 || url.contains("//store") || url.contains("//rabsh")) {
                    return false;
                }
            }
            Log.d(TAG, "xxx: isVideo: " + url);
            return true;
        }
        // Log.d(TAG, "isVideo: no one 4:" + url);
        return false;
    }

    class MyJavaScriptInterface {
        @JavascriptInterface
        public void myMethod(String elementJson) {
            Log.d(TAG, "myMethod: xxxx");
            // parse the JSON string to get the element
            // Element element = Json.parse(elementJson);
//            if (elementJson.startsWith("<html>")){
            ServerConfig config = ServerConfigRepository.getInstance().getConfig(server.getServerId());
            if (movie.getFetch() == Movie.REQUEST_CODE_MOVIE_UPDATE) {
                Log.d(TAG, "myMethod: html contents: " + elementJson);
      //todo          server.handleJSWebResult(activity, movie, elementJson);
                Type movieListType = new TypeToken<List<Movie>>() {
                }.getType();
                List<Movie> movies = gson.fromJson(elementJson, movieListType);
                //todo handle movie state of movies as it comes from js call without state
                movie.setSubList(movies);
                setResult(Activity.RESULT_OK, Util.generateIntentResult(movie));
                finish();
                return;
            } else if (elementJson.equals("click")) {
//                mouseArrow.setX(screenHeight / 2);
//                mouseArrow.setY(screenWidth / 2);
//                simulateMouseClick(webView, screenHeight / 2, screenWidth / 2);
//                simulateMouseClick(webView, screenHeight / 2, screenWidth / 2);
//                simulateMouseClick(webView, screenHeight / 2, screenWidth / 2);
//                simulateMouseClick(webView, screenHeight / 2, screenWidth / 2);
            } else if (elementJson.startsWith("##")) {
                elementJson = elementJson.replace("##", "");
                final String url = elementJson;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //     Log.d(TAG, "run: xxx: iframe redirect: " + url);
                        webView.loadUrl(url);
                    }
                });

            }
            else {
                Log.d(TAG, "myMethod:else ");
                if (movie.getState() == Movie.COOKIE_STATE) {
//           hiiiiiieeeer         Intent intent = new Intent();
//           hiiiiiieeeer         intent.putExtra("result", elementJson);
//           hiiiiiieeeer setResult(Activity.RESULT_OK, intent);
                    //server.setCookies(CookieManager.getInstance().getCookie(movie.getVideoUrl()));
                    //  if (server.getReferer() == null){4
                    //String ref = getValidReferer(movie.)
                    //  }
                    Type movieListType = new TypeToken<List<Movie>>() {
                    }.getType();
                    List<Movie> movies = gson.fromJson(elementJson, movieListType);
                    //todo handle movie state of movies as it comes from js call without state
                    movie.setSubList(movies);
                    Intent intent = new Intent();
                    intent.putParcelableArrayListExtra(DetailsActivity.MOVIE_SUBLIST, (ArrayList<Movie>) movies);
                    setResult(Activity.RESULT_OK, intent);
//                    Log.d(TAG, "myMethod: xxx referer:" + config.getReferer());

//                    try {
//                        // Parse the JSON string to a JSONArray
//                        JSONArray jsonArray = new JSONArray(elementJson);
//
//                        // Get the first JSONObject from the JSONArray
//                        JSONObject jsonObject = jsonArray.getJSONObject(0);
//
//                        // Extract the videoUrl value
//                        String videoUrl = jsonObject.getString("videoUrl");
//                        String movieReferer = urlExtractor(videoUrl);
//                        if (null == movieReferer) {
//                            movieReferer = config.getReferer();
//                        }
//                        config.setReferer(movieReferer);
//                        config.setUrl(movieReferer);
//
//                        ServerConfigRepository.updateConfig(config);
//                        // Print the extracted URL
//                        Log.d(TAG, "Extracted URL: " + movieReferer);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//
//
//                    Log.d(TAG, "myMethod: xxx result:" + elementJson);
//                    ServerConfigRepository.updateConfig(config, dbHelper);
//                    dbHelper.saveHeadersAndCookies(server, movie.getStudio())
                    finish();
                    return;
                }
                else {
                    Movie resultMovie = null;
                    if (
                            movie.getStudio().equals(Movie.SERVER_AKWAM) ||
                                    movie.getStudio().equals(Movie.SERVER_OLD_AKWAM)
                    ) {
                        Type type = new TypeToken<List<Movie>>() {
                        }.getType();
                        List<Movie> movieSublist = gson.fromJson(elementJson, type);
                        if (!movieSublist.isEmpty()) {
                            resultMovie = movieSublist.get(0);
                        } else {
                            resultMovie = movie;
                        }

                        // If you want to send back data
//                        resultMovie.setMainMovie(movie.getMainMovie());
                        setResult(Activity.RESULT_OK, Util.generateIntentResult(resultMovie));
                        Log.d(TAG, "myMethod: akwam resultActivity finish ");

                        //this case only for akwam as the valid referer already fetched by search method
                        String movieReferer = Util.getValidReferer(movie.getVideoUrl());
//                        ServerConfig config = server.getConfig();
                        //todo optimize config update
                        if (config != null){
                            config.setReferer(movieReferer);
                            config.setUrl(movieReferer);

                            ServerConfigRepository.getInstance().updateConfig(config);
                        }
//                        dbHelper.saveHeadersAndCookies(server, server.getServerId());
                        finish();
                        return;
                    } else {
                        resultMovie = movie;
                    }

                    if (movie.getStudio().equals(Movie.SERVER_ARAB_SEED)) {
                        if (movie.getState() == Movie.BROWSER_STATE) {
//                            Intent intent = new Intent();
//                            intent.putExtra("result", elementJson);
//                            setResult(Activity.RESULT_OK, intent);
                            Type movieListType = new TypeToken<List<Movie>>() {
                            }.getType();
                            List<Movie> movies = gson.fromJson(elementJson, movieListType);
                            //todo handle movie state of movies as it comes from js call without state
                            movie.setSubList(movies);
                            setResult(Activity.RESULT_OK, Util.generateIntentResult(movie));
                            finish();
                            return;
                        }
                    }


                    Log.d(TAG, "myMethod: xxxx: resultMovie: " + resultMovie);

//                    Intent returnIntent = new Intent(activity, VideoDetailsFragment.class);
//                    resultMovie.setFetch(0); //tell next activity not to fetch movie on start
//                    returnIntent.putExtra(DetailsActivity.MOVIE, (Serializable) resultMovie);
//                    returnIntent.putExtra(DetailsActivity.MAIN_MOVIE, (Serializable) movie.getMainMovie()); //selected Movie mainMovie
//                    returnIntent.putExtra(DetailsActivity.MOVIE_SUBLIST, elementJson);
//                    startActivity(returnIntent);
                    Type movieListType = new TypeToken<List<Movie>>() {
                    }.getType();
                    List<Movie> movies = gson.fromJson(elementJson, movieListType);
                    //todo handle movie state of movies as it comes from js call without state
                    movie.setSubList(movies);
                    Util.openVideoDetailsIntent(movie, activity);
                    finish();
                    return;
                }
            }


//                        returnIntent.putExtra("result",(Serializable) jsonMovie);
//                        activity.setResult(Activity.RESULT_OK, returnIntent);
            // do something with the element in the iframe
        }
    }

    private String urlExtractor(String videoUrl) {
        String baseUrl = null;
        try {
            // Create a URI object from the videoUrl string
            URI uri = new URI(videoUrl);

            // Extract the scheme (e.g., "https") and authority (e.g., "www.faselhd.express") from the URI
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();

            // Combine scheme and authority to form the base URL
            baseUrl = scheme + "://" + authority;

            // Print the base URL
            System.out.println("Base URL: " + baseUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return baseUrl;
    }

    private class ChromeClient extends WebChromeClient {
        private View mCustomView;
        private CustomViewCallback mCustomViewCallback;
        protected FrameLayout mFullscreenContainer;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;
        private float mOriginalMouseElevation;


        ChromeClient() {
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            // Log.d(TAG, "onCreateWindow: xxx");
            return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            //    Log.d(TAG, "onJsAlert: " + message);
            result.cancel();
            //super.onJsAlert(view, url, null, result);
            return true;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            //    Log.d(TAG, "onJsBeforeUnload: " + url + ", m:" + message + ", re:" + result);
            result.cancel();
            return true;
        }


        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            //  Log.d(TAG, "onJsPrompt: " + message);
            result.cancel();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
            //    Log.d(TAG, "onJsConfirm: " + message);
            result.cancel();
            return true;
        }


        public Bitmap getDefaultVideoPoster() {
            //    Log.d(TAG, "getDefaultVideoPoster: " + mCustomView);
            if (mCustomView == null) {
                return null;
            }
            //    Log.d(TAG, "getDefaultVideoPoster: " + BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573));
            return BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573);
        }

        public void onHideCustomView() {
            //    Log.d(TAG, "onHideCustomView: ");
            ((FrameLayout) getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
//            mouseArrow.setElevation(this.mOriginalMouseElevation);
            allowArrowEdgeScrolling = true;
        }

        public void onShowCustomView(View paramView, CustomViewCallback paramCustomViewCallback) {
            //    Log.d(TAG, "onShowCustomView: ");
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


            //    Log.d(TAG, "onShowCustomView: custom:" + mCustomView);


            FrameLayout.LayoutParams newFrame = new FrameLayout.LayoutParams(-1, -1);
            ((FrameLayout) getWindow().getDecorView()).addView(this.mCustomView, newFrame);
            getWindow().getDecorView().setSystemUiVisibility(3846 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            //mouseArrow.bringToFront();
            allowArrowEdgeScrolling = false;
            paramCustomViewCallback.onCustomViewHidden();
        }
    }

//    interface Callback {
//        void onCallback(String value);
//    }

    private class Browser_Home extends WebViewClient {
        boolean executedJS = false;
        boolean cookieFound = false;
        Map<String, String> headers = new HashMap<>();
        private String currentVideoUrl = "";
        CookieManager cookieManager = CookieManager.getInstance();


        Browser_Home() {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
            cookieManager.setAcceptCookie(true);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
//            Log.d(TAG, "onReceivedError: eeee: "+error.toString());
            super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
//              Log.d(TAG, "onReceivedHttpError: eeee:"+errorResponse.getEncoding()+", "+errorResponse.getStatusCode()+", "+errorResponse.getResponseHeaders().toString()+ "; "+ request.getUrl());
            super.onReceivedHttpError(view, request, errorResponse);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
//               Log.d(TAG, "onReceivedSslError: eeee:"+error.toString());
            super.onReceivedSslError(view, handler, error);
        }

        private boolean isSupportedMedia(WebResourceRequest request) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(request.getUrl().toString()));
            String acceptEncoding = request.getRequestHeaders().get("Accept-Encoding");
            boolean isAcceptEncoding = acceptEncoding != null && acceptEncoding.contains("identity;q=1");
            if (isAcceptEncoding) {
                Log.d(TAG, "isSupportedMedia: isAcceptEncoding: " + acceptEncoding);
                if (isBlackListedUrl(request.getUrl().toString())){
                    return false;
                }
                return true;
            }
            if (mimeType != null) {
//                Log.d(TAG, "isSupportedMedia: mimeType: "+mimeType);
                if (mimeType.startsWith("video") || mimeType.startsWith("audio")) {
                    Log.d(TAG, "isSupportedMedia: mimeType: " + mimeType);
                    return true;
                }  // Check for video mimetypes
            }

            return false;
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Log.d(TAG, "shouldInterceptRequest: " + request.getUrl());
            headers = request.getRequestHeaders();
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                Log.d(TAG, "shouldInterceptRequest: isRedirect: "+request.isRedirect());
//            }
//            Map<String, String> headers = request.getRequestHeaders();
            Log.d(TAG, "shouldInterceptRequest: headers:" + request.getRequestHeaders().toString());
//            String oriReferer = headers.get("Referer");
//            if (oriReferer == null && request.getUrl().toString().contains("gamezone")){
//                Log.d(TAG, "shouldInterceptRequest: no referer");
//                headers.put("Referer", "https://m.asd.quest/");
//                request.getRequestHeaders().put("Referer", "https://m.asd.quest/");
//                Log.d(TAG, "shouldInterceptRequest: headers:" + request.getRequestHeaders().toString());
//
//            }

            boolean isSupportedState = movie.getState() == Movie.RESOLUTION_STATE || movie.getState() == Movie.BROWSER_STATE;
            if (movie.getStudio().equals(Movie.SERVER_ARAB_SEED)){
                isSupportedState = movie.getState() == Movie.RESOLUTION_STATE;
            }
            if (isSupportedState && isSupportedMedia(request)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webView.stopLoading();
                    }
                });
//                Intent returnIntent = new Intent(activity, ExoplayerMediaPlayer.class);
                Movie mm = Movie.clone(movie);
                mm.setState(Movie.VIDEO_STATE);

                if (movie.getStudio().equals(Movie.SERVER_CimaNow)) {
                    if (Util.extractDomain(request.getUrl().toString(), false, false).contains("cima")){
                       return super.shouldInterceptRequest(view, request);
                    }
                }

                mm.setVideoUrl(request.getUrl().toString() + Util.generateHeadersForVideoUrl(request.getRequestHeaders()));

                if (!openedForResult){
                    Util.openExoPlayer(mm, activity, false);
                    return null;
                }
//                Log.d(TAG, "shouldInterceptRequest: video: " + mm.getVideoUrl());
//                Log.d(TAG, "shouldInterceptRequest: video: headers: " + request.getRequestHeaders());

//                //movie.setFetch(0); //tell next activity not to fetch movie on start
//                returnIntent.putExtra(DetailsActivity.MOVIE, (Serializable) mm);
//                returnIntent.putExtra(DetailsActivity.MAIN_MOVIE, (Serializable) mm.getMainMovie());
//                activity.startActivity(returnIntent);
//                Intent intent = new Intent(activity, ExoplayerMediaPlayer.class);
//                intent.putExtra(DetailsActivity.MOVIE, (Serializable) mov);
//                Objects.requireNonNull(activity).startActivity(intent);
//  hiiiier             String type = "video/*"; // It works for all video application
//                Uri uri = Uri.parse(mm.getVideoUrl());
//                Intent in1 = new Intent(Intent.ACTION_VIEW, uri);
//                in1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                //  in1.setPackage("org.videolan.vlc");
//                in1.setDataAndType(uri, type);
//                // movie.getMainMovie().save(dbHelper);
//
//                activity.startActivity(in1);
                Log.d(TAG, "shouldInterceptRequest: video shouldInterceptRequest. "+ mm);
//                Intent returnIntent = new Intent();
//                returnIntent.putExtra(DetailsActivity.MOVIE,  mm);
//                returnIntent.putExtra(DetailsActivity.MAIN_MOVIE, mm.getMainMovie());
                setResult(Activity.RESULT_OK, Util.generateIntentResult(mm));
                activity.finish();
//                setResult(Activity.RESULT_OK, Util.generateIntentResult(mm));
//                finish();
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

            server.shouldInterceptRequest(view, request, movie);
//            testCookie(view, request);
            if (
                    movie.getStudio().equals(Movie.SERVER_ARAB_SEED) ||
                            movie.getStudio().equals(Movie.SERVER_OMAR) ||
                            movie.getStudio().equals(Movie.SERVER_KOORA_LIVE)
            ) {
                return super.shouldInterceptRequest(view, request);
            }
//            if (
//                    movie.getState() != Movie.COOKIE_STATE ||
//                    !movie.getStudio().equals(Movie.SERVER_AKWAM)
//            ){
//                  return super.shouldInterceptRequest(view, request);
//            }
            try {

                if (!cookieFound && request.getRequestHeaders() != null && request.getRequestHeaders().containsKey("Referer")) {
                    String referer = request.getRequestHeaders().get("Referer");
                    String validReferer = Util.getValidReferer(referer);
                    String cookie = CookieManager.getInstance().getCookie(validReferer);
                    if (cookie != null && isValidReferer(referer)) {

                        //  String validReferer = getValidReferer(referer);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            headers.replace("Referer", validReferer);
                        }

                        config.setReferer(validReferer);
                        config.setUrl(validReferer);
                        config.setHeaders(request.getRequestHeaders());
                        //todo handle setting StringCookies to manage mapped Cookies
                        config.setStringCookies(cookie);

                        ServerConfigRepository.getInstance().updateConfig(config);
                        cookieFound = true;
//                        Log.d(TAG, "shouldInterceptRequest: response: headers:" + request.getRequestHeaders().toString());
                        //  setResult(RESULT_OK);
                        //finish();
                        // }
                    }
                }
            } catch (Exception e) {
                // Handle the exception
                Log.d(TAG, "shouldInterceptRequest: error:" + e.getMessage());
                //  return null;
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
//            Log.d(TAG, "shouldOverrideUrlLoading: " + request.getUrl());
            boolean result = false;
            String url = request.getUrl().toString();
            redirectUrl = url;
            //   Log.d(TAG, "shouldOverrideUrlLoading: redirectUrl:"+redirectUrl);
//            if (BBrowserActivity.BLOCK_URL_SWITCH){
//                result = !url.contains(CURRENT_WEB_NAME);
//            }
//            if (!result){
//                CURRENT_WEB_NAME = getWebName(url);
//            }
//            Log.d(TAG, "shouldOverrideUrlLoading: "+result+"url: "+url);
//            return result;
            // if (BBrowserActivity.BLOCK_URL_SWITCH){
            //  result = !url.contains(CURRENT_WEB_NAME);
            String newUrl = request.getUrl().toString().length() > 25 ? request.getUrl().toString().substring(0, 25) : request.getUrl().toString();
            if (newUrl.contains("intent:/")) {
                return true;
            }
            if (!server.shouldOverrideUrlLoading(movie, view, request)){
                return false;
            }

            if (movie.getState() == Movie.COOKIE_STATE || !Util.shouldOverrideUrlLoading(newUrl)) {
                if (url.startsWith("##")) {
                    url = url.replace("##", "");
                }
                Log.d(TAG, "shouldOverrideUrlLoading:5 false: " + url);
                view.loadUrl(url);
                return true;
                //  CURRENT_WEB_NAME = getWebName(url);
            }



//            Log.d(TAG, "shouldOverrideUrlLoading: contains config url : "+ newUrl.contains(server.getConfig().url) + ", "+server.getConfig().url);


            if (movie.getStudio().equals(Movie.SERVER_ARAB_SEED)) {
                if (url.contains("/e/")) {
                    Log.d(TAG, "shouldOverrideUrlLoading:0b false: " + url);
                    return false;
                }
                if (newUrl.contains("gameland")) {
                    Log.d(TAG, "shouldOverrideUrlLoading:0c false: " + url);
                    return false;
                }
            }


//            if (movie.getStudio().equals(Movie.SERVER_MyCima)) {
////                boolean sameSite = server.getConfig() != null && server.getConfig().url != null && server.getConfig() != null && newUrl.contains(server.getConfig().url);
//
                if (!hideRedirectBar && !movie.getStudio().equals(Movie.SERVER_AKWAM)) {
                    urlBar.setText(newUrl);
                    urlNotificationBar.setVisibility(View.VISIBLE);
                    autoHideRunnable = new Runnable() {
                        @Override
                        public void run() {
                            urlNotificationBar.setVisibility(View.GONE);
                        }
                    };
                    autoHideHandler.postDelayed(autoHideRunnable, 7000);
                }
//                Log.d(TAG, "shouldOverrideUrlLoading:2 true: " + url);
//                return true;
//            }


            if (
                    (
                            movie.getStudio().equals(Movie.SERVER_AKWAM) ||
                                    movie.getStudio().equals(Movie.SERVER_OLD_AKWAM)) &&
                            isVideo(request.getUrl().toString(), movie)) {
                //hieeeer       Intent returnIntent = new Intent(activity, DetailsActivity.class);
                Movie mm = Movie.clone(movie);
                mm.setState(Movie.VIDEO_STATE);
                mm.setVideoUrl(request.getUrl().toString());
                //mm.addSubList(mm);

                List<Movie> movieList = new ArrayList<>();
                movieList.add(mm);
                mm.setSubList(movieList);
                if (!openedForResult){
                    Util.openExoPlayer(mm, activity, false);
                    return true;
                }

                setResult(Activity.RESULT_OK,  Util.generateIntentResult(mm));
//      //hieeeer          String jsonMovie = gson.toJson(movieList);
//
//   //hieeeer             //movie.setFetch(0); //tell next activity not to fetch movie on start
//   //hieeeer             returnIntent.putExtra(DetailsActivity.MOVIE, (Serializable) mm);
//   //hieeeer             returnIntent.putExtra(DetailsActivity.MAIN_MOVIE, (Serializable) mm.getMainMovie());
//   //hieeeer             returnIntent.putExtra(DetailsActivity.MOVIE_SUBLIST, jsonMovie);
//    //hieeeer            activity.startActivity(returnIntent);
                Log.d(TAG, "shouldOverrideUrlLoading:3 true: " + url);
                activity.finish();

                return true;
            }

//            if (newUrl.contains(Util.extractDomain(movie.getVideoUrl(), false, false))) {
//                Log.d(TAG, "shouldOverrideUrlLoading:4 false: " + url);
//                view.loadUrl(url);
//                return false;
//            }




            //########

            if (!hideRedirectBar && !movie.getStudio().equals(Movie.SERVER_AKWAM)) {
                urlBar.setText(newUrl);
                urlNotificationBar.setVisibility(View.VISIBLE);
                autoHideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        urlNotificationBar.setVisibility(View.GONE);
                    }
                };
                autoHideHandler.postDelayed(autoHideRunnable, 7000);
            }
            Log.d(TAG, "shouldOverrideUrlLoading:6 true: " + url);
            //########
            return true;
            // }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.d(TAG, "onPageStarted: " + url);
            BrowserActivity_old_2.EXECUTE_JS = true;
            BrowserActivity_old_2.RESULT_COUNTER = 0;
            //executedJS = false;
            currentVideoUrl = "";
            // if (!executedJS){
            //  Log.d(TAG, "onPageStarted: xxxx executing");
            String script = server.getWebScript(WEB_VIEW_MODE_ON_PAGE_STARTED, movie);
            if (script == null) {
                script = getScript(WEB_VIEW_MODE_ON_PAGE_STARTED);
            }
            view.evaluateJavascript(script, null);
//                executedJS = true;
//            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
//              Log.d(TAG, "onPageFinished: " + url);
            String script = server.getWebScript(WEB_VIEW_MODE_ON_PAGE_FINISHED, movie);
            if (script == null) {
                script = getScript(WEB_VIEW_MODE_ON_PAGE_FINISHED);
            }
            view.evaluateJavascript(script, null);
//            view.evaluateJavascript(getScript(WEB_VIEW_MODE_ON_PAGE_FINISHED), null);
//            if (movie.getStudio().equals(Movie.SERVER_CIMA_CLUB) && movie.getState() == Movie.ITEM_STATE){
//                simulateTouchCenter(view);
//                simulateTouchCenter(view);
//                simulateTouchCenter(view);
//                simulateTouchCenter(view);
//            }

            // progressBar.setVisibility(View.INVISIBLE);
        }

        private void simulateTouchCenter(View view) {
            int centerX = view.getWidth() / 2;
            int centerY = view.getHeight() / 2;
            //  Log.d(TAG, "xxx: simulateTouchCenter: width:" + centerX + ", height:" + centerY);

            long downTime = SystemClock.uptimeMillis();
            long eventTime = SystemClock.uptimeMillis() + 100;

            MotionEvent downEvent = MotionEvent.obtain(downTime, eventTime,
                    MotionEvent.ACTION_DOWN, centerX, centerY, 0);
            MotionEvent upEvent = MotionEvent.obtain(downTime, eventTime,
                    MotionEvent.ACTION_UP, centerX, centerY, 0);

            dispatchTouchEvent(downEvent);
            dispatchTouchEvent(upEvent);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            //if (webView.getProgress() == 100 && (movie.getState() == Movie.ITEM_STATE || movie.getState() == Movie.RESOLUTION_STATE )){
//            Log.d(TAG, "onLoadResource: " + webView.getProgress() + ", " + RESULT_COUNTER + ", " + url);
            if (BrowserActivity_old_2.RESULT_COUNTER < 2 && webView.getProgress() == 100) {
//                Log.d(TAG, "onLoadResource: loaded");
                BrowserActivity_old_2.RESULT_COUNTER++;
                //hhhhhh view.evaluateJavascript(getScript(WEB_VIEW_MODE_ON_LOAD_RESOURCES), null);
                String script = server.getWebScript(WEB_VIEW_MODE_ON_LOAD_RESOURCES, movie);
                if (script == null) {
                    script = getScript(WEB_VIEW_MODE_ON_LOAD_RESOURCES);
                }
                view.evaluateJavascript(script, null);
            }

            if (movie.getStudio().equals(Movie.SERVER_ARAB_SEED)) {
                if (!movie.getVideoUrl().contains("vidmoly") &&
                        movie.getState() == Movie.RESOLUTION_STATE
                ) {
                    //todo fix cleanweb
                    if (
                            movie.getVideoUrl().contains("voe.sx") ||
                                    movie.getVideoUrl().contains("brucevotewithin")
                    ) {

                    } else {
                        cleanWebPage(view);
                    }
                }
            } else {
                if (movie.getState() == Movie.RESOLUTION_STATE || movie.getState() == Movie.BROWSER_STATE) {
                    cleanWebPage(view);
                }
            }


//fetch video
            //    Log.d(TAG, "onLoadResource: isvideo: " + isVideo(url) + ": " + BrowserActivity.RESULT_COUNTER + !url.equals(currentVideoUrl) + ", " + url);
            // avoid detecting video if SERVER_ARAB_SEED on BROWSER_STATE
            if (movie.getStudio().equals(Movie.SERVER_ARAB_SEED)
                    && movie.getState() == Movie.BROWSER_STATE) {
                super.onLoadResource(view, url);
                return;
            }
            if (isVideo(url, movie) && BrowserActivity_old_2.RESULT_COUNTER < 3 && !url.equals(currentVideoUrl)) {
                BrowserActivity_old_2.EXECUTE_JS = false;
                BrowserActivity_old_2.RESULT_COUNTER++;
                String newUrl = url;
                //   if (url.endsWith("m3u8")){
                if (!movie.getStudio().equals(Movie.SERVER_FASELHD)) {
                    if (movie.getStudio().equals(Movie.SERVER_CimaNow)) {
                        if (Util.extractDomain(newUrl, false, false).contains("cimanow")){
                            url = url.replace("-360p.", "-720p.");
                        }
                    }


                    //convert headers to string
                    newUrl = Util.generateMaxPlayerHeaders(url, headers);
                    // newUrl =url+"|Referer=https://vidshar.org/";
                    //   }

                }


//                        //to play master.m3u8 not other versions
//                        if (currentVideoUrl.endsWith(".m3u8") && currentVideoUrl.contains("master")){
//                            uri = Uri.parse(currentVideoUrl);
//                        }else {
                currentVideoUrl = url;

                //hier hhhhhhhh

                //         Intent browse = new Intent(activity, DetailsActivity.class);
                Movie mov = Movie.clone(movie);
                mov.setVideoUrl(newUrl);
                mov.setState(Movie.VIDEO_STATE);


                Log.d(TAG, "onLoadResource: isVideo: yessss1: " + newUrl + "");

                if (!openedForResult){
                    Util.openExoPlayer(mov, activity, false);
                    return;
                }
//                Intent intent = new Intent(activity, ExoplayerMediaPlayer.class);
//                intent.putExtra(DetailsActivity.MOVIE, (Serializable) mov);
//                Objects.requireNonNull(activity).startActivity(intent);
//                String type = "video/*"; // It works for all video application
//                Uri uri = Uri.parse(newUrl);
//                Intent in1 = new Intent(Intent.ACTION_VIEW, uri);
//                in1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                //  in1.setPackage("org.videolan.vlc");
//                in1.setDataAndType(uri, type);
//                // movie.getMainMovie().save(dbHelper);
//
//                activity.startActivity(in1);


                //hier hhhhhhhh
                setResult(Activity.RESULT_OK, Util.generateIntentResult(mov));

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        view.stopLoading();
                    }
                });

                activity.finish();
                return;
                //hier hhhhhhhh

//                mov.setFetch(0);
//                ArrayList<Movie> mList = new ArrayList<>();
//                mList.add(mov);
//                //order is very important to avoid recursion
//                String jsonMovie = gson.toJson(mList);
//                mov.setSubList(mList);
//
//                browse.putExtra(DetailsActivity.MOVIE, (Serializable) mov);
//                browse.putExtra(DetailsActivity.MOVIE_SUBLIST, jsonMovie);
//                //   Log.d(TAG, "getResultFromWeb: activity:" + fragment.getClass().getName());
//                //activity.startActivity(browse);
//
//                webView.stopLoading();
//                startActivity(browse);
//                finish();
                //hier hhhhhhhh

                //  }

                //               Uri uri = Uri.parse(newUrl);
                //String newUrl=  "https://s49.vidsharcdn.com/hls/,pdomb2axiwm4f4kmlfccf2dfhe5pjdnzs2my25t67wekxuae4atqnrryu7kq,.urlset/master.m3u8|Referer=https://vidshar.org/";
//                String type = "video/*"; // It works for all video application
//
//                Log.d("yessss2", uri + "");
//                Intent in1 = new Intent(Intent.ACTION_VIEW, uri);
//                in1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                //  in1.setPackage("org.videolan.vlc");
//                in1.setDataAndType(uri, type);
//                // view.stopLoading();
//                activity.startActivity(in1);
            }
            super.onLoadResource(view, url);
        }
//hhhhhhhhhhh
//        @Override
//        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
////            Log.d(TAG, "shouldOverrideKeyEvent: " + event.toString());
////            mouseArrow.requestFocus();
////            return changePos(event);
//            //remote: right: 22, left: 21, up:19, down: 20, back:4, enter: 23
//            //gamepad: right: 22, left: 21/125, up:19/102, down: 20, back:97/4, enter: 96/23
////            Toast.makeText(activity, "code:" +event.getKeyCode() + KeyEvent.KEYCODE_BUTTON_10, Toast.LENGTH_SHORT).show();
////            return false; //changePos(event);
//        }

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
            connection.cookies(getMapCookies(cookieTest));
            Document doc = null;
            try {
                doc = connection.get();
                String title = doc.title();
//                Log.d(TAG, "testCookie shouldInterceptRequest: cookie test title:" + title);
                if (!title.contains("moment")) {
                    Log.d(TAG, "testCookie shouldInterceptRequest: success headers:" + request.getRequestHeaders().toString());
                    Log.d(TAG, "testCookie shouldInterceptRequest: success cookies:" + cookieTest);
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
        Log.d(TAG, "isValidReferer: " + result + ", " + referer);
        return result;
    }

    private String getScript(int mode) {
        String script = "";
        if (mode == 0) {
            if (movie.getStudio().equals(Movie.SERVER_SHAHID4U)) {
                if (movie.getState() == Movie.COOKIE_STATE) {
                    Log.d(TAG, "getScript: Shahid COOKIE_STATE");
                    script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                            "var postDivs = document.getElementsByClassName(\"content-box\");" +
                            "if (postDivs.length > 0){" +
                            "var postList = [];" +
                            "for (var i = 0; i < postDivs.length; i++) {" +
                            "    var post = {};" +
                            "    var postDiv = postDivs[i];" +
                            "    var imageDiv = postDiv.getElementsByClassName(\"image\")[0];" +
                            "    var rateEle = postDiv.getElementsByClassName(\"rate ti-star\");" +
                            "    if(rateEle.length > 0){" +
                            "    post.rate = rateEle[0].textContent;" +
                            "    }" +
                            "    post.title = postDiv.getElementsByTagName(\"h3\")[0].textContent;" +
                            "    post.videoUrl = imageDiv.href;" +
                            "    post.cardImageUrl = imageDiv.getAttribute('data-src');" +
                            "    post.bgImageUrl = post.cardImageUrl;" +
                            "    post.studio = \"" + Movie.SERVER_SHAHID4U + "\";" +
                            "    post.state = 0;" +
                            "    postList.push(post);" +
                            "}" +
                            "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                            "}" +
                            "});";

                } else if (movie.getState() == Movie.GROUP_OF_GROUP_STATE) {
                    Log.d(TAG, "getScript: Shahid GROUP_OF_GROUP_STATE");
                    script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                            "var postDivs = document.getElementsByClassName(\"content-box\");" +
                            "if (postDivs.length > 0){" +
                            "var postList = [];" +
                            "for (var i = 0; i < postDivs.length; i++) {" +
                            "    var post = {};" +
                            "    var postDiv = postDivs[i];" +
                            "    var imageDiv = postDiv.getElementsByClassName(\"image\")[0];" +
                            "    var rateEle = postDiv.getElementsByClassName(\"rate ti-star\");" +
                            "    if(rateEle.length > 0){" +
                            "    post.rate = rateEle[0].textContent;" +
                            "    }" +
                            "    post.title = postDiv.getElementsByTagName(\"h3\")[0].textContent;" +
                            "    post.videoUrl = imageDiv.href;" +
                            "    post.cardImageUrl = imageDiv.getAttribute('data-src');" +
                            "    post.bgImageUrl = post.cardImageUrl;" +
                            "    post.studio = \"" + Movie.SERVER_SHAHID4U + "\";" +
                            "    post.state = 1;" +
                            "    postList.push(post);" +
                            "}" +
                            "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                            "}" +
                            "});";
                } else if (movie.getState() == Movie.GROUP_STATE) {
                    Log.d(TAG, "getScript: Shahid GROUP_STATE");
                    script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                            "var postDivs = document.getElementsByClassName(\"content-box\");" +
                            "if (postDivs.length > 0){" +
                            "var postList = [];" +
                            "for (var i = 0; i < postDivs.length; i++) {" +
                            "    var post = {};" +
                            "    var postDiv = postDivs[i];" +
                            "    var imageDiv = postDiv.getElementsByClassName(\"image\")[0];" +
                            "    var rateEle = postDiv.getElementsByClassName(\"rate ti-star\");" +
                            "    if(rateEle.length > 0){" +
                            "    post.rate = rateEle[0].textContent;" +
                            "    }" +
                            "    post.title = postDiv.getElementsByClassName(\"episode-number\")[0].textContent;" +
                            "    post.videoUrl = imageDiv.href;" +
                            "    post.cardImageUrl = imageDiv.getAttribute('data-src');" +
                            "    post.bgImageUrl = post.cardImageUrl;" +
                            "    post.studio = \"" + Movie.SERVER_SHAHID4U + "\";" +
                            "    post.state = 2;" +
                            "    postList.push(post);" +
                            "}" +
                            "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                            "}" +
                            "});";
                } else if (movie.getState() == Movie.ITEM_STATE) {
                    Log.d(TAG, "getScript: Shahid ITEM_STATE");
                    //fetch servers
                /*    script ="document.addEventListener(\"DOMContentLoaded\", () => {" +
                            "var serversTab = document.getElementsByClassName('servers-list');" +
                            "if (serversTab.length == 0){" +
                            "serversTab = document.getElementsByClassName('server--item');" +
                            "}" +
                            "if (serversTab.length > 0){" +
                            "var postList = [];" +
                            "var postDivs = serversTab[0].getElementsByTagName('li');" +
                            "if (postDivs.length > 0){" +
                            "for (var i = 0; i < postDivs.length; i++) {" +
                            "    var post = {};" +
                            "    var postDiv = postDivs[i];" +
                            "    post.title = postDiv.textContent.replace(/\\n/g, \"\");" +
                            "    post.videoUrl =\""+ movie.getVideoUrl()+"\"+'?'+postDiv.getAttribute('data-embedd');" +
                            // "    post.videoUrl = postDiv.getAttribute('data-embedd');" +
                            "    post.cardImageUrl = postDiv.getElementsByTagName('img')[0].getAttribute('src');" +
                            //"    post.cardImageUrl = \""+movie.getCardImageUrl()+"\";" +
                            "    post.bgImageUrl = post.cardImageUrl;" +
                            "    post.studio = \""+Movie.SERVER_SHAHID4U+"\";" +
                            "    post.state = 5;" +
                            "    postList.push(post);" +
                            "}" +
                            "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                            "}" +
                            "}" +
                            "});";
                 */
//                    script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
//                            "var iframes = document.getElementsByTagName('iframe');\n" +
//                                    "if (iframes.length > 0) {\n" +
//                                    "                                var iframe = iframes[0];\n" +
//                                    "                                if(iframe.src.includes('java')) {\n" +
//                                    "                                    if(iframes[1] != null){\n" +
//                                    "                                        iframe = iframes[1];\n" +
//                                    "                                    } \n" +
//                                    "                                }\n" +
//                                    "     iframe.scrollIntoView({behavior: 'smooth'});\n" +
//                                    "}"+
//                            "});";
                }
                /*else if (movie.getState() == Movie.BROWSER_STATE)
                {
                    Log.d(TAG, "getScript:Shahid BROWSER_STATE");
                    String serverId = "#";
                    if (movie.getVideoUrl().contains("?")){
                        // newUrl = movie.getVideoUrl().substring(0, movie.getVideoUrl().indexOf("?"));
                        serverId = movie.getVideoUrl().substring(movie.getVideoUrl().indexOf("?")+1);
                        Log.d(TAG, "getScript: serverID:"+serverId);
                    }
                    script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                            "var intendedValue = '"+serverId+"';\n" +
                            "var serversTab = document.getElementsByClassName('servers-list');\n" +
                            "if (serversTab.length == 0){" +
                            "serversTab = document.getElementsByClassName('server--item');" +
                            "}" +
                            " if (serversTab.length > 0) {\n" +
                            "     var links = serversTab[0].getElementsByTagName('li');\n" +
                            "if (links[intendedValue] != null)" +
                            "     links[intendedValue].click();\n" +
                            "var iframes = document.getElementsByTagName('iframe');\n" +
                            "                            if (iframes.length > 0) {\n" +
                            "                                var iframe = iframes[0];\n" +
                            "                                if(iframe.src.includes('java')) {\n" +
                            "                                    if(iframes[1] != null){\n" +
                            "                                        iframe = iframes[1];\n" +
                            "                                    } \n" +
                            "                                }\n" +
                            "                                iframe.scrollIntoView({behavior: 'smooth'});\n" +
                            "                            } "+
                            " }"+
                            "});";
                }
                */
            } else if (movie.getStudio().equals(Movie.SERVER_ARAB_SEED)) {
                if (movie.getState() == Movie.COOKIE_STATE) {
                    Log.d(TAG, "getScript: SERVER_ARAB_SEED COOKIE_STATE");
                    script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                            "var postDivs = document.getElementsByClassName(\"MovieBlock\");\n" +
                            "if (postDivs.length > 0){\n" +
                            "var postList = [];\n" +
                            "for (var i = 0; i < postDivs.length; i++) {\n" +
                            "var post = {};\n" +
                            "var postDiv = postDivs[i];\n" +
                            "var category = postDiv.getElementsByClassName(\"category\")[0].textContent;" +
                            "if (category.match(/تطبيق|برامج|برنامج|موبايل|اغاني|العاب|لعبة|لعبه|صور/) != null){continue;}" +
                            "var poster = postDiv.getElementsByClassName(\"Poster\")[0];\n" +
                            "var imageDiv = poster.getElementsByTagName(\"img\")[0];\n" +
                            "post.title = imageDiv.getAttribute(\"alt\");\n" +
                            "post.videoUrl = postDiv.getElementsByTagName(\"a\")[0].getAttribute('href');\n" +
                            "rateDiv = postDiv.getElementsByClassName(\"RateNumber\");\n" +
                            "if(rateDiv.length > 0) {\n" +
                            "post.rate = rateDiv[0].textContent;\n" +
                            "}\n" +
                            "post.cardImageUrl = imageDiv.getAttribute('data-src');" +
                            "if (post.cardImageUrl == null ){ post.cardImageUrl = imageDiv.getAttribute('src'); }" +
                            "post.bgImageUrl = post.cardImageUrl;\n" +
                            "post.studio = \"" + Movie.SERVER_ARAB_SEED + "\";\n" +
                            "post.state = 2;\n" +
                            "postList.push(post);\n" +
                            "}" +
                            "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                            "}" +
                            "});";

                } else if (movie.getState() == Movie.ITEM_STATE) {
                    Log.d(TAG, "getScript: SERVER_ARAB_SEED ITEM_STATE");
//                    script ="document.addEventListener(\"DOMContentLoaded\", () => {" +
//                            "var watchBtn = document.getElementsByClassName('watchBTn');" +
//                            "if (watchBtn.length > 0){" +
//                                "watchBtn[0].click();"+
//                              //  "window.location= watchBtn[0].href;"+
//                            "}" +
//                            "});";
                }

            } else if (movie.getStudio().equals(Movie.SERVER_CIMA4U)) {
                if (movie.getState() == Movie.BROWSER_STATE) {
                    Log.d(TAG, "getScript:Shahid BROWSER_STATE");
                    String serverId = "#";
                    if (movie.getVideoUrl().contains("?")) {
                        // newUrl = movie.getVideoUrl().substring(0, movie.getVideoUrl().indexOf("?"));
                        serverId = movie.getVideoUrl().substring(movie.getVideoUrl().indexOf("?") + 1);
                        Log.d(TAG, "getScript: serverID:" + serverId);
                    }
                    script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                            "var iframes = document.getElementsByTagName('iframe');\n" +
                            "if (iframes.length > 0) {\n" +
                            "    var iframe = iframes[0];\n" +
                            "     if(iframe.src.includes('java')) {\n" +
                            "         if(iframes[1] != null){\n" +
                            "            iframe = iframes[1];\n" +
                            "          }\n" +
                            "     }\n" +
                            "     iframe.scrollIntoView({behavior: 'smooth'});\n" +
                            "}" +
                            "var intendedValue = '" + serverId + "';\n" +
                            " var serversWatchSideElement = document.querySelector('.serversWatchSide');\n" +
                            "var elementToClick = serversWatchSideElement.querySelector(\"[data-link='" + serverId + "']\");\n" +
                            "elementToClick.click();" +
                            "if (iframe.requestFullscreen) {\n" +
                            "  iframe.requestFullscreen();\n" +
                            "} else if (iframe.mozRequestFullScreen) { // Firefox\n" +
                            "  iframe.mozRequestFullScreen();\n" +
                            "} else if (iframe.webkitRequestFullscreen) { // Chrome, Safari and Opera\n" +
                            "  iframe.webkitRequestFullscreen();\n" +
                            "} else if (iframe.msRequestFullscreen) { // IE/Edge\n" +
                            "  iframe.msRequestFullscreen();\n" +
                            "}" +
                            "});";
                }
            }
        } else if (mode == 1) {
            if (movie.getState() == Movie.ITEM_STATE) {
                if (movie.getStudio().equals(Movie.SERVER_SHAHID4U)) {
                    script = "var iframes = document.getElementsByTagName('iframe');\n" +
                            "if (iframes.length > 0) {\n" +
                            "                                var iframe = iframes[0];\n" +
                            "                                if(iframe.src.includes('java')) {\n" +
                            "                                    if(iframes[1] != null){\n" +
                            "                                        iframe = iframes[1];\n" +
                            "                                    } \n" +
                            "                                }\n" +
                            "     iframe.scrollIntoView({behavior: 'smooth'});\n" +
                            "}";
                } else if (movie.getStudio().equals(Movie.SERVER_ARAB_SEED)) {//|| movie.getStudio().equals(Movie.SERVER_SHAHID4U)) {
                    Log.d(TAG, "getScript: SERVER_ARAB_SEED mode 1 ITEM_STATE");
//                    script ="var watchBtn = document.getElementsByClassName('watchBTn');" +
//                            "if (watchBtn.length > 0){" +
//                            "window.location= watchBtn[0].href;"+
//                            "}";
                }
            } else if (movie.getState() == Movie.BROWSER_STATE) {
                if (movie.getStudio().equals(Movie.SERVER_CIMA_CLUB)) {
                    //|| movie.getStudio().equals(Movie.SERVER_SHAHID4U)) {
//                Log.d(TAG, "getScript: cimaClub|Shahid mode 1 BROWSER_STATE");
//                String serverId = "#";
//                if (movie.getVideoUrl().contains("?")){
//                   // newUrl = movie.getVideoUrl().substring(0, movie.getVideoUrl().indexOf("?"));
//                    serverId = movie.getVideoUrl().substring(movie.getVideoUrl().indexOf("?")+1);
//                    Log.d(TAG, "getScript: serverID:"+serverId);
//                }
//                script = "var intendedValue = '"+serverId+"';\n" +
//                        "var serversTab = document.getElementsByClassName('servers-list');\n" +
//                        "if (serversTab.length == 0){" +
//                        "serversTab = document.getElementsByClassName('server--item');" +
//                        "}" +
//                        " if (serversTab.length > 0) {\n" +
//                        "     var links = serversTab[0].getElementsByTagName('li');\n" +
//                        "if (links[intendedValue] != null)" +
//                        "     links[intendedValue].click();\n" +
//                        " }";
                }
            }
        } else {
//            if (movie.getStudio().equals(Movie.SERVER_CIMA4U)) {
//                script = "var iframes = document.getElementsByTagName('iframe');\n" +
//                        "if (iframes.length > 0) {\n" +
//                        "    var iframe = iframes[0];\n" +
//                        "     if(iframe.src.includes('java')) {\n" +
//                        "         if(iframes[1] != null){\n" +
//                        "            iframe = iframes[1];\n" +
//                        "          }\n" +
//                        "     }\n" +
//                        "}" +
//                        "console.log('yess:'+iframe.src);" +
//                        "MyJavaScriptInterface.myMethod('##' + iframe.src);";
////                        "var iframe2 = document.createElement('iframe');" +
////                        "iframe2.src = iframe.src;" +
////                        "iframe2.style.width = '100vw';\n" +
////                        "iframe2.style.height = '100vh';" +
////                        "iframe2.setAttribute('allowfullscreen', 'true');" +
////                        "iframe2.setAttribute('autoplay', 'true');" +
////                        "document.body.appendChild(iframe2);\n" +
////                        "if (iframe2.requestFullscreen) {\n" +
////                        "  iframe2.requestFullscreen();\n" +
////                        "} else if (iframe2.mozRequestFullScreen) { " +
////                        "  iframe2.mozRequestFullScreen();\n" +
////                        "} else if (iframe2.webkitRequestFullscreen) {" +
////                        "  iframe2.webkitRequestFullscreen();\n" +
////                        "} else if (iframe2.msRequestFullscreen) {" +
////                        "  iframe2.msRequestFullscreen();\n" +
////                        "}" +
////                        "     iframe2.scrollIntoView({behavior: 'smooth'});\n";
//            }
        }
        Log.d(TAG, "getScript: mode:" + mode + " state:" + movie.getState() + ", " + script);
        return script;
    }

    public Map<String, String> getMapCookies(String cookies) {
        Map<String, String> cookiesHash = new HashMap<>();
        if (cookies != null) {
            //split the String by a comma
            String parts[] = cookies.split(";");

            //iterate the parts and add them to a map
            for (String part : parts) {

                //split the employee data by : to get id and name
                String empdata[] = part.split("=");

                String strId = empdata[0].trim();
                String strName = empdata[1].trim();

                //add to map
                cookiesHash.put(strId, strName);
            }

        }
        return cookiesHash;
    }

//    public static SSLSocketFactory getSSLSocketFactory() throws Exception {
//        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
//            public X509Certificate[] getAcceptedIssuers() {
//                return null;
//            }
//
//            public void checkClientTrusted(X509Certificate[] certs, String authType) {
//            }
//
//            public void checkServerTrusted(X509Certificate[] certs, String authType) {
//            }
//        }};
//
//        SSLContext sslContext = SSLContext.getInstance("SSL");
//        sslContext.init(null, trustAllCerts, new SecureRandom());
//
//        return sslContext.getSocketFactory();
//    }

}