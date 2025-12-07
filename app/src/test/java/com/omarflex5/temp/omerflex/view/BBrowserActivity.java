package com.omarflex5.temp.omerflex.view;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.util.Patterns;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.server.AbstractServer;
import com.omerflex.server.config.ServerConfigRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class BBrowserActivity extends AppCompatActivity {

    EditText urlInput;
    ImageView clearUrl;
    ImageView linkImage;
    WebView webView;
    Movie movie;
    ProgressBar progressBar;
    ImageView webBack,webForward,webRefresh,webShare;
    public static boolean BLOCK_URL_SWITCH = false;
    public static String CURRENT_WEB_NAME = "google";
    public static String TAG = "browser: ";


    // Initialize Class
    private Handler handler = new Handler();
    private Timer timer = new Timer();
    //variable for back button confirmation
    private long backPressedTime;

    private ImageView mouseArrow;
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
    AbstractServer server;
    public static int RESULT_COUNTER=0;




    Activity activity;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        movie = (Movie) getIntent().getSerializableExtra(DetailsActivity.MOVIE);
        activity = this;

//Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Objects.requireNonNull(getSupportActionBar()).hide(); //hide the title bar
//set content view AFTER ABOVE sequence (to avoid crash)

        setContentView(com.omerflex.R.layout.activity_bbrowser);

        urlInput = findViewById(R.id.url_input);
        clearUrl = findViewById(R.id.clear_icon);
        linkImage = findViewById(R.id.link_icon);
        progressBar = findViewById(R.id.progress_bar);
        webView = findViewById(R.id.web_view);

        webBack = findViewById(R.id.web_back);
        webForward = findViewById(R.id.web_forward);
        webRefresh = findViewById(R.id.web_refresh);
        webShare = findViewById(R.id.web_share);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.addJavascriptInterface(new MyJavaScriptInterface(), "MyJavaScriptInterface");

        if (movie.getState() == Movie.ITEM_STATE ){
            webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        }

        // Enable hardware acceleration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        ///// mouseArrow
        mouseArrow = (ImageView) findViewById(R.id.mouse_arrow);
// Get Screen Size.
        WindowManager wm = getWindowManager();
        Display disp = wm.getDefaultDisplay();
        Point size = new Point();
        disp.getSize(size);

//###################
        webViewWidth = webView.getWidth();
        webViewHeight = webView.getHeight();
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        // Initialize the handler and runnable
        hideHandler = new Handler();
        hideRunnable = new Runnable() {
            @Override
            public void run() {
                // Hide the mouse arrow
                mouseArrow.setVisibility(View.INVISIBLE);
                arrowVisible = false;
            }
        };

        server = ServerConfigRepository.getInstance().getServer(movie.getStudio());



//        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
//            WebViewCompat.setForceDark(webView, WebViewCompat.FORCE_DARK_ON);
//        }
//
//        if (WebViewCompat.getCurrentWebViewPackage(this) != null) {
//            WebViewCompat.setDataDirectorySuffix(webView, "myWebView");
//            WebViewCompat.setWebContentsDebuggingEnabled(true);
//        }
//
//        WebViewCompat.setWebViewRenderProcessClient(webView, new WebViewRenderProcessClient() {
//            @Override
//            public void onRenderProcessUnresponsive(@NonNull WebView view, @Nullable WebViewRenderProcess renderer) {
//
//            }
//
//            @Override
//            public void onRenderProcessResponsive(@NonNull WebView view, @Nullable WebViewRenderProcess renderer) {
//
//            }
//        });
//        WebViewCompat.setWebViewClient(webView, new WebViewClient());
//        WebViewCompat.setWebChromeClient(webView, new WebChromeClient());
//        WebViewCompat.setWebContentsDebuggingEnabled(true);

     //   webView.setWebViewClient(new MyWebViewClient());
        webView.setWebViewClient(new Browser_Home() );

        webView.setWebChromeClient(new ChromeClient());
     /*   webView.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);
            }
        });

      */

     //   loadMyUrl("google.com");

        urlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if(i == EditorInfo.IME_ACTION_GO || i == EditorInfo.IME_ACTION_DONE){
                    InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(urlInput.getWindowToken(),0);
                    loadMyUrl(urlInput.getText().toString());
                    return true;
                }
                return false;
            }
        });

        clearUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                urlInput.setText("");
            }
        });

        linkImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (BLOCK_URL_SWITCH){
                    linkImage.setBackgroundColor(Color.RED);
                    BBrowserActivity.BLOCK_URL_SWITCH = false;
                    Toast.makeText(getApplicationContext(),"block is off",Toast.LENGTH_SHORT).show();
                }else {
                    linkImage.setBackgroundColor(Color.GREEN);
                    BBrowserActivity.BLOCK_URL_SWITCH = true;
                    Toast.makeText(getApplicationContext(),"block is active",Toast.LENGTH_SHORT).show();
                }

            }
        });

        webBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(webView.canGoBack()){
                    webView.goBack();
                }
            }
        });

        webForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(webView.canGoForward()){
                    webView.goForward();
                }
            }
        });

        webRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                webView.reload();
            }
        });

        webShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setAction(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, webView.getUrl());
                intent.setType("text/plain");
                startActivity(intent);
            }
        });


        if (savedInstanceState == null) {
            //loadMyUrl("google.com");
           // loadMyUrl("https://www.faselhd.ac/?s=sonic");
            loadMyUrl(movie.getVideoUrl());
        }

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


    void loadMyUrl(String url){
        boolean matchUrl = Patterns.WEB_URL.matcher(url).matches();
        if(matchUrl){
            webView.loadUrl(url);
        }else{
            webView.loadUrl("google.com/search?q="+url);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        //check if waiting time between the second click of back button is greater less than 2 seconds so we finish the app
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            finish();
        } else {
            if (webView != null && webView.canGoBack())
                webView.goBack();// if there is previous page open it
            else
                Toast.makeText(this, "Press back 2 time to exit", Toast.LENGTH_SHORT).show();
        }
        backPressedTime = System.currentTimeMillis();

    }

    class MyWebViewClient extends WebViewClient{
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
          boolean result = false;
            String url = request.getUrl().toString();
            if (BBrowserActivity.BLOCK_URL_SWITCH){
              //  result = !url.contains(CURRENT_WEB_NAME);
                String newUrl = request.getUrl().toString().length() > 30 ? request.getUrl().toString().substring(0, 30) : request.getUrl().toString();
                result = shouldOverride(newUrl);
                if (!result){
                    view.loadUrl(request.getUrl().toString());
                    CURRENT_WEB_NAME = getWebName(url);
                }

            }
            Log.d(TAG, "shouldOverrideUrlLoading: "+result+"url: "+url);
            return result;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            urlInput.setText(webView.getUrl());
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        webView.pauseTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        webView.destroy();
    }

    private String getWebName(String url) {
        URL newUrl = null;
        try {
             newUrl = new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        String host = newUrl.getHost();
        String domainName = host.startsWith("www.") ? host.substring(4) : host;
        String name = domainName.substring(0, domainName.lastIndexOf("."));
        Log.d("TAG", "getWebName: "+name+": url: "+ url);
        return name;
    }

    private class Browser_Home extends WebViewClient {
        Browser_Home() {
        }
       boolean executedJS = false;
        Map<String, String> headers = new HashMap<>();
        private String currentVideoUrl = "";

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            headers = request.getRequestHeaders();
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            boolean result = false;
            String url = request.getUrl().toString();
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
                if (!shouldOverride(newUrl)){
                    view.loadUrl(request.getUrl().toString());
                  //  CURRENT_WEB_NAME = getWebName(url);
                }
            return true;
           // }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.d(TAG, "onPageStarted: "+url);
            //executedJS = false;
            currentVideoUrl = "";
            if (!executedJS){
                Log.d(TAG, "onPageStarted: xxxx executing");
                view.evaluateJavascript(getScript(), null);
                executedJS = true;
            }

            urlInput.setText(webView.getUrl());
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            Log.d(TAG, "onLoadResource: "+webView.getProgress()+", "+url);
//fetch video
            if (isVideo(url) && !url.equals(currentVideoUrl)) {
                String newUrl = url;
                //   if (url.endsWith("m3u8")){
                //convert headers to string
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
//                                if (entry.getKey().equals("User-Agent")){
//                                    continue;
//                                }
                    sb.append(entry.getKey());
                    sb.append("=");
                    sb.append(entry.getValue());
                    sb.append("&");
                }
                // Remove the last "&" character
                sb.deleteCharAt(sb.length() - 1);
                String headerString = sb.toString();
                newUrl = url + "|" + headerString;
                // newUrl =url+"|Referer=https://vidshar.org/";
                //   }
                Log.d("yessss1", newUrl + "");

                Uri uri = Uri.parse(newUrl);
//                        //to play master.m3u8 not other versions
//                        if (currentVideoUrl.endsWith(".m3u8") && currentVideoUrl.contains("master")){
//                            uri = Uri.parse(currentVideoUrl);
//                        }else {
                currentVideoUrl = url;
                //  }

                //String newUrl=  "https://s49.vidsharcdn.com/hls/,pdomb2axiwm4f4kmlfccf2dfhe5pjdnzs2my25t67wekxuae4atqnrryu7kq,.urlset/master.m3u8|Referer=https://vidshar.org/";
                String type = "video/*"; // It works for all video application

                Log.d("yessss2", uri + "");
                Intent in1 = new Intent(Intent.ACTION_VIEW, uri);
                in1.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                //  in1.setPackage("org.videolan.vlc");
                in1.setDataAndType(uri, type);
                // view.stopLoading();
                activity.startActivity(in1);
            }
            super.onLoadResource(view, url);
        }

        @Override
        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
            Log.d(TAG, "shouldOverrideKeyEvent: "+ event.toString());
            mouseArrow.requestFocus();
            return changePos(event);
            //return false; //changePos(event);
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown: "+event.toString());
        webView.requestFocus();
        return true;// changePos(event);
    }

    private String getScript() {
        String script = "";
        if (movie.getStudio().equals(Movie.SERVER_FASELHD)){
            if (movie.getState() == Movie.COOKIE_STATE){
                Log.d(TAG, "getScript:Fasel COOKIE_STATE");
                script = "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        // "alert(\"DOM ready!\");" +
                        "var postDivs = document.getElementsByClassName(\"postDiv\");" +
                        "if (postDivs.length > 0){" +
                        "var postList = [];" +
                        "for (var i = 0; i < postDivs.length; i++) {" +
                        "    var post = {};" +
                        "    var postDiv = postDivs[i];" +
                        "    post.title = postDiv.getElementsByTagName(\"img\")[0].alt;" +
                        "    post.videoUrl = postDiv.getElementsByTagName(\"a\")[0].href;" +
                        "    post.cardImageUrl = postDiv.getElementsByTagName(\"img\")[0].getAttribute('data-src');" +
                        "    post.bgImageUrl = post.cardImageUrl;" +
                        "    post.studio = 'FaselHd';" +
                        "    post.state = 0;" +
                        "    postList.push(post);" +
                        "}" +
                        //"postList;"+
                        "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                        "}" +
                        "});";
            }else if (movie.getState() == Movie.GROUP_OF_GROUP_STATE){
                Log.d(TAG, "getScript:Fasel GROUP_OF_GROUP_STATE");
                script ="document.addEventListener(\"DOMContentLoaded\", () => {" +
                                "var seasons = document.querySelectorAll('.seasonDiv');" +
                        "if (seasons.length > 0){" +
                        "var postList = [];" +
                        "var description = document.getElementsByClassName(\"singleDesc\")[0].innerHTML.replace(/<.*?>/g, \"\").replace(/(\\r\\n|\\n|\\r)/gm,\"\");" +
                                "for (var i = 0; i < seasons.length; i++) {" +
                                "    var post = {};" +
                                "    var season = seasons[i];" +
                                "    post.videoUrl = 'https://www.faselhd.club/?p='+ season.getAttribute('data-href');" +
                                "    post.title = season.querySelector('.title').textContent;" +
                                "    post.cardImageUrl = season.querySelector('[data-src]').getAttribute('data-src');" +
                                "    post.bgImageUrl = post.cardImageUrl;" +
                                "    post.description = description;" +
                                "    var spans = season.querySelectorAll('.seasonMeta');" +
                                "    for (var j = 0; j < spans.length; j++) {" +
                                "        post.rate = spans[j].querySelector('*').textContent;" +
                                "        break;" +
                                "    }" +
                                "    post.state = 1;" +
                                "    post.studio = 'FaselHd';" +
                                "    postList.push(post);" +
                                "}" +
                        "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                        "}" +
                        "});";
            }else if (movie.getState() == Movie.GROUP_STATE){
                Log.d(TAG, "getScript:Fasel GROUP_STATE");
                script ="document.addEventListener(\"DOMContentLoaded\", () => {" +
                "//fetch description" +
                        "        //fetch session" +
                        "        var boxs = document.getElementsByClassName(\"seasonDiv active\");" +
                        "        var description = document.getElementsByClassName(\"singleDesc\")[0].innerHTML.replace(/<.*?>/g, \"\").replace(/(\\r\\n|\\n|\\r)/gm,\"\");" +
                        "        var postList = [];" +
                        "        if (boxs.length == 0){" +
                        "            var title = document.getElementsByClassName(\"h1 title\")[0].text;" +
                        "            var cardImageUrl = document.getElementsByClassName(\"img-fluid\")[0].getAttribute(\"src\");" +
                        "            var divs = document.getElementById(\"epAll\").querySelectorAll(\"[href]\");" +
                        "            for (const div of divs) {" +
                        "                var post = {};" +
                        "                post.cardImageUrl = cardImageUrl;" +
                        "                post.bgImageUrl = cardImageUrl;" +
                        "                post.backgroundImageUrl = cardImageUrl;" +
                        "                post.videoUrl = div.getAttribute(\"href\");" +
                        "                post.title = div.innerHTML.replace(/<.*?>/g, \"\").replace(/(\\r\\n|\\n|\\r)/gm,\"\").trim();" +
                        "                post.description = description;" +
                        "                post.state = 2;" +
                        "                post.rate = '';" +
                        "                post.studio = 'FaselHd';" +
                        "                postList.push(post);" +
                        "            }" +
                        "        }else{" +
                        "        for (var i = 0; i < boxs.length; i++) {" +
                        "            var title = boxs[i].getElementsByClassName(\"title\")[0].textContent;" +
                        "            var cardImageUrl = boxs[i].querySelectorAll(\"[data-src]\")[0].getAttribute(\"data-src\");" +
                        "                var divs = document.getElementById(\"epAll\").getElementsByTagName(\"a\");" +
                        "                for (const div of divs) {" +
                        "                var post = {};" +
                        "                post.cardImageUrl = cardImageUrl;" +
                        "                post.bgImageUrl = cardImageUrl;" +
                        "                post.backgroundImageUrl = cardImageUrl;" +
                        "                post.videoUrl = div.getAttribute(\"href\");" +
                        "                post.title = div.innerHTML.replace(/<.*?>/g, \"\").replace(/(\\r\\n|\\n|\\r)/gm,\"\").trim();" +
                        "                post.description = description;" +
                        "                post.state = 2;" +
                        "                post.rate = '';" +
                        "                post.studio = 'FaselHd';" +
                        "                postList.push(post);" +
                        "               }" +
                        "         }" +
                        "      }" +
                        "if(postList.length > 0){" +
                        "MyJavaScriptInterface.myMethod(JSON.stringify(postList));" +
                        "}" +
                        "});";
            }else if (movie.getState() == Movie.ITEM_STATE){
                Log.d(TAG, "getScript:Fasel ITEM_STATE");
                script =  "document.addEventListener(\"DOMContentLoaded\", () => {" +
                        "var iframe = document.querySelector('iframe[src]:not([src=\"about:blank\"])');" +
                        "  // Check if the iframe element was found" +
                        "  if (iframe) {" +
                        "    iframe.scrollIntoView({behavior: 'smooth'});" +
                        "  } " +
                        "var singeWatch = document.getElementsByClassName(\"signleWatch\");" +
                        "if(singeWatch.length > 0){\n" +
                        "    singeWatch[0].getElementsByTagName(\"li\")[0].click();" +
                        "    singeWatch[0].getElementsByTagName(\"li\")[1].click();" +
                        "}" +
//                        "window.frames['player_iframe'].document.querySelectorAll('#player')[0].click();" +
//                        "window.frames['player_iframe'].document.querySelectorAll('.hd_btn')[0].click();" +
//                        "window.frames['player_iframe'].document.querySelectorAll('.hd_btn selected')[0].click();" +
                        "var playerIframe = document.getElementById('player_iframe');" +
                        "if (playerIframe != null) {" +
                        "playerIframe.click();" +
                        "}" +
                        "});";
            }
        }

        return script;
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
            Log.d(TAG, "getDefaultVideoPoster: " + BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573));
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
            mouseArrow.setElevation(this.mOriginalMouseElevation);
            allowArrowEdgeScrolling = true;
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
            this.mOriginalMouseElevation = mouseArrow.getElevation();

            FrameLayout frameLayout = (FrameLayout) paramView;
            View focusedChild = frameLayout.getFocusedChild();

//            Log.d(TAG, "onShowCustomView: elevm:" + mouseArrow.getElevation()
//                    + ", elevV:" + paramView.getElevation() + ", elevFu:" + focusedChild.getElevation());
//            // Set the mouse arrow's elevation to a higher value than the custom view's elevation
            mouseArrow.setElevation(focusedChild.getElevation() + 1);


            Log.d(TAG, "onShowCustomView: custom:" + mCustomView);


            FrameLayout.LayoutParams newFrame = new FrameLayout.LayoutParams(-1, -1);
            ((FrameLayout) getWindow().getDecorView()).addView(this.mCustomView, newFrame);
            getWindow().getDecorView().setSystemUiVisibility(3846 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            mouseArrow.bringToFront();
            allowArrowEdgeScrolling = false;
            paramCustomViewCallback.onCustomViewHidden();
        }
    }


    private class ChromeClient__ extends WebChromeClient {
        private View mCustomView;
        private CustomViewCallback mCustomViewCallback;
        protected FrameLayout mFullscreenContainer;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;

        ChromeClient__() {
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            progressBar.setProgress(newProgress);
        }

        public Bitmap getDefaultVideoPoster() {
            if (mCustomView == null) {
                return null;
            }
            return BitmapFactory.decodeResource(getApplicationContext().getResources(), 2130837573);
        }

        public void onHideCustomView() {
            ((FrameLayout) getWindow().getDecorView()).removeView(this.mCustomView);
            this.mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
            setRequestedOrientation(this.mOriginalOrientation);
            this.mCustomViewCallback.onCustomViewHidden();
            this.mCustomViewCallback = null;
        }

        public void onShowCustomView(View paramView, CustomViewCallback paramCustomViewCallback) {
            if (this.mCustomView != null) {
                onHideCustomView();
                return;
            }
            this.mCustomView = paramView;
            this.mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            this.mOriginalOrientation = getRequestedOrientation();
            this.mCustomViewCallback = paramCustomViewCallback;
            ((FrameLayout) getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
            getWindow().getDecorView().setSystemUiVisibility(3846 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    class MyJavaScriptInterface {
        @JavascriptInterface
        public void myMethod(String elementJson) {
            // parse the JSON string to get the element
            // Element element = Json.parse(elementJson);

            // do something with the element
            //  element.innerHTML = "Hello, World!";
            Log.d(TAG, "myMethod: xxxx: " + elementJson);
            Intent intent = new Intent();
            intent.putExtra("result", elementJson);
            setResult(Activity.RESULT_OK, intent);
            finish();


//                        returnIntent.putExtra("result",(Serializable) jsonMovie);
//                        activity.setResult(Activity.RESULT_OK, returnIntent);
            // do something with the element in the iframe
        }
    }


    public static boolean isVideo(String url) {
        if (url.contains("click") || url.contains("brand") || url.contains(".php") || url.contains(".gif") || url.contains("error") || url.endsWith("null")) {
            return false;
        }

        // Check the file extension
        if (url.endsWith(".mp4") || (url.contains("file_code=") && !(url.contains("embed") || url.contains("watch"))) || (url.contains("token=") && !(url.contains("embed") || url.contains("watch"))) || url.endsWith(".mov") || url.endsWith(".avi") || url.endsWith(".wmv") || url.endsWith(".m3u") || url.endsWith(".m3u8") || url.endsWith(".mkv") || url.contains(".m3u8")) {
            // The URL is likely a video
            // ////log.d(TAG, "isVideo: 1");
            if (url.endsWith(".mp4")) {
                //avoid audio only mp4
                String newUrl = url.substring(url.lastIndexOf("/"));
                //   ////log.d(TAG, "isVideo: newUrl:" + newUrl);
                if (newUrl.contains("_a_")) {
                    return false;
                }
            }
            return true;
        }
        // ////log.d(TAG, "isVideo: no one 4:" + url);
        return false;
    }

    public static boolean shouldOverride(String url) {
        boolean result =
                url.contains("cima")
                        || url.contains("faselhd")
                        || url.contains("akwam")
                        || url.contains("club")
                        || url.contains("clup")
                        || url.contains("ciima")
                        || url.contains("challenge")
                        || url.contains("akoam")
                        || url.contains("shahed")
                        || url.contains("shahid");
        return !result;
    }

    public boolean changePos(KeyEvent event) {
        int action = event.getKeyCode();
        Log.d(TAG, "changePos: x:"+mouseArrow.getX()+", Y:"+mouseArrow.getY());
        boolean shouldInterrept = true;

        // Show the mouse arrow if it is hidden
        if (!arrowVisible) {
            mouseArrow.setVisibility(View.VISIBLE);
            arrowVisible = true;
        }

// Cancel the delayed hide task
        hideHandler.removeCallbacks(hideRunnable);

// Schedule a new hide task to run after 3 seconds
        hideHandler.postDelayed(hideRunnable, 3000);



        // Check if the mouse arrow reaches the end of the screen
        //Toast.makeText(this, "key: "+action, Toast.LENGTH_SHORT).show();
        switch (action) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // Move the mouse arrow speed pixels to the left
                mouseArrow.setX(mouseArrow.getX() - speed);
                // Limit the position of the mouse arrow to the left edge of the screen
                if (mouseArrow.getX() < 0) {
                    mouseArrow.setX(0);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Move the mouse arrow speed pixels to the right
                mouseArrow.setX(mouseArrow.getX() + speed);
                // Limit the position of the mouse arrow to the right edge of the screen
                if (mouseArrow.getX() + mouseArrow.getWidth() > screenWidth) {
                    mouseArrow.setX(screenWidth - mouseArrow.getWidth());
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                // Move the mouse arrow speed pixels up
                mouseArrow.setY(mouseArrow.getY() - speed);
                // Limit the position of the mouse arrow to the top edge of the screen
                if (mouseArrow.getY() < 0) {
                    mouseArrow.setY(0);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // Move the mouse arrow speed pixels down
                mouseArrow.setY(mouseArrow.getY() + speed);
                // Limit the position of the mouse arrow to the bottom edge of the screen
                if (mouseArrow.getY() + mouseArrow.getHeight() > screenHeight) {
                    mouseArrow.setY(screenHeight - mouseArrow.getHeight());
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    simulateMouseClick(webView, mouseArrow.getX(), mouseArrow.getY());
                }
            default:
                shouldInterrept = false; // if to interrupt the key event or not
                break;
        }

        // Check if the user is pressing the same key as before
        if (action == lastKeyCode) {
            // Check if the timer has started
            if (timer == null) {
                // Start the timer
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        // Increase the arrow's speed
                        speed += 0.95;
                    }
                }, 0, 10); // Increase the speed every 100ms
            }
        } else {
            // Reset the arrow's speed
            speed = 10;

            // Stop the timer
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
        }

// Update the last key code
        lastKeyCode = action;

        if (allowArrowEdgeScrolling) {
            // Scroll the screen horizontally when the mouse arrow reaches the left or right edge of the screen
            if (mouseArrow.getX() == 0) {
                if (webView.canScrollHorizontally(-1)) {
                    // The WebView can be scrolled horizontally
                    webView.scrollBy((int) -speed, 0);
                }
            } else if (mouseArrow.getX() + mouseArrow.getWidth() == screenWidth) {
                if (webView.canScrollHorizontally(1)) {
                    // The WebView can be scrolled horizontally
                    webView.scrollBy((int) speed, 0);
                }
            }

            // Scroll the screen vertically when the mouse arrow reaches the top or bottom edge of the screen
            if (mouseArrow.getY() == 0) {
                if (webView.canScrollVertically(-1)) {
                    // The WebView can be scrolled vertically
                    webView.scrollBy(0, (int) -speed);
                }
            } else if (mouseArrow.getY() + mouseArrow.getHeight() == screenHeight) {
                if (webView.canScrollVertically(1)) {
                    // The WebView can be scrolled vertically
                    webView.scrollBy(0, (int) speed);
                }
            }
        }
        return shouldInterrept;
    }

    private void simulateMouseClick(WebView webView, float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis() + 100;
        float xScale = webView.getScaleX();
        float yScale = webView.getScaleY();
        float xTouch = x / xScale;
        float yTouch = y / yScale;
        MotionEvent downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, xTouch, yTouch, 0);
        MotionEvent upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, xTouch, yTouch, 0);
        // webView.dispatchTouchEvent(downEvent);
        Log.d(TAG, "simulateMouseClick: " + webView.dispatchTouchEvent(downEvent));
        webView.dispatchTouchEvent(upEvent);
//        MotionEvent motionEvent = MotionEvent.obtain(
//                SystemClock.uptimeMillis(),  // downTime
//                SystemClock.uptimeMillis() + 100,  // eventTime
//                MotionEvent.ACTION_DOWN,   // action
//                mouseArrow.getLeft() + 10,   // x
//                mouseArrow.getTop() + 10,    // y
//                0                          // metaState
//        );
//        Log.d(TAG, "simulateMouseClick: "+webView.dispatchTouchEvent(motionEvent));
    }



}