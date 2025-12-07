package com.omarflex5.temp.omerflex.view;

import android.annotation.SuppressLint;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.entity.ServerConfig;
import com.omerflex.server.Util;
import com.omerflex.server.config.ServerConfigRepository;
import com.omerflex.view.CursorLayout;

import org.json.JSONArray;
import org.json.JSONException;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class GetDocActivity extends AppCompatActivity {

    private static final String TAG = "GetDocActivity";
    public static CompletableFuture<String> resultFuture;
    private WebView webView;
    private CursorLayout cursorLayout;
    private String url;
    private String studio;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Movie movie;
    private ServerConfig config;
    private Map<String, String> headers;

    @SuppressLint({"SetJavaScriptEnabled", "WebViewApiAvailability"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_get_doc);
        cursorLayout = new CursorLayout(this);

        url = getIntent().getStringExtra("url");
        studio = getIntent().getStringExtra("studio");
        movie = Util.recieveSelectedMovie(getIntent());
        Log.d(TAG, "onCreate: studio: "+ studio);
        Log.d(TAG, "onCreate: movie: "+ movie);
        if (movie != null) {
            config = ServerConfigRepository.getInstance().getConfig(studio);
            Log.d(TAG, "onCreate: config: "+ config);
        }

        if (url == null || url.isEmpty()) {
            completeExceptionally(new IllegalArgumentException("URL is missing from intent"));
            return;
        }

        if (resultFuture == null || resultFuture.isDone()) {
            Log.w(TAG, "ResultFuture is not ready. Finishing activity.");
            finish();
            return;
        }

        webView = findViewById(R.id.webView);
        webView.clearCache(true);
        configureWebView();

        Log.d(TAG, "Loading URL in WebView: " + url);
        webView.loadUrl(url);

        timeoutHandler.postDelayed(this::timeout, 60000); // 60-second timeout
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString("Android 7");
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setAllowContentAccess(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webSettings.setMediaPlaybackRequiresUserGesture(false);
        }

        webView.setWebViewClient(new WebViewClient() {
            private boolean challengeResolved = false;

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "onPageFinished for URL: " + url);

                try {
                    URI originalUri = new URI(GetDocActivity.this.url);
                    URI finalUri = new URI(url);
                    URI configUri = new URI(config.getUrl());

                    String originalHost = originalUri.getHost();
                    String finalHost = finalUri.getHost();
                    String configHost = configUri.getHost();
                    Log.d(TAG, "onPageFinished: originalHost:"+originalHost+", config: "+configHost);

                    if (config != null &&
                            originalHost != null &&
                            originalHost.equals(configHost) && // avoid updating when url is not from the same server
                            finalHost != null && !originalHost.equalsIgnoreCase(finalHost)) {
                        // Heuristic: if original path was not root, and final path is root, it might be a generic redirect.
                        if (!"/".equals(originalUri.getPath()) && "/".equals(finalUri.getPath())) {
                            Log.w(TAG, "Domain changed, but redirected to root of new domain. Not updating server URL to be safe.");
                        } else {
                            String newBaseUrl = finalUri.getScheme() + "://" + finalUri.getHost();
                            if (finalUri.getPort() != -1 && finalUri.getPort() != 80 && finalUri.getPort() != 443) {
                                newBaseUrl += ":" + finalUri.getPort();
                            }
                            Log.i(TAG, "Domain change detected for " + config.getName() + ". Updating URL from " + config.getUrl() + " to " + newBaseUrl);
                            config.setUrl(newBaseUrl);
                            config.setReferer(newBaseUrl+"/");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during domain change detection.", e);
                }

                updateCookies(url);
                view.evaluateJavascript("document.title", title -> {
                    Log.d(TAG, "Page title: " + title);
                    if (title != null && (title.contains("Just a moment...") || title.contains("Checking your browser") || title.contains("لحظة…") || title.contains("التحقق"))) {
                        Log.d(TAG, "Challenge page detected. WebView will continue loading...");
                        challengeResolved = false;
                    } else {
                        if (!challengeResolved) {
                            challengeResolved = true;
                            Log.d(TAG, "Challenge resolved or not present. Extracting HTML.");
                            extractHtml(view);
                        }
                    }
                });
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.w(TAG, "SSL Error: " + error.toString() + " Proceeding anyway.");
                handler.proceed(); // Bypass SSL errors
            }

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (request.isForMainFrame()) {
                        headers = request.getRequestHeaders();
                        Log.d(TAG, "Captured main frame headers: " + headers.toString());
                    }
                } else {
                    // For older APIs, we can't reliably check isForMainFrame(),
                    // so we'll continue to capture headers from any request.
                    headers = request.getRequestHeaders();
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                Log.w(TAG, "JS Alert suppressed: " + message);
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                Log.w(TAG, "JS Confirm suppressed: " + message);
                result.confirm();
                return true;
            }
        });
    }


    private void extractHtml(WebView view) {
        view.evaluateJavascript("document.documentElement.outerHTML",
                html -> {
                    if (html != null && !html.equals("null") && html.length() > 2) {
                        try {
                            JSONArray jsonArray = new JSONArray("[" + html + "]");
                            String unescapedHtml = jsonArray.getString(0);
                            completeSuccessfully(unescapedHtml);
                        } catch (JSONException e) {
                            completeExceptionally(new Exception("Failed to parse HTML from WebView", e));
                        }
                    } else {
                        completeExceptionally(new Exception("Failed to extract HTML from WebView"));
                    }
                });
    }

    private void timeout() {
        completeExceptionally(new TimeoutException("WebView timed out after 90 seconds"));
    }

    private void completeSuccessfully(String html) {
        if (resultFuture != null && !resultFuture.isDone()) {
            Log.d(TAG, "Completing future successfully.");
            resultFuture.complete(html);
            cleanup();
//            new Handler(Looper.getMainLooper()).post(this::cleanup);
        }
    }

    private void completeExceptionally(Exception e) {
        if (resultFuture != null && !resultFuture.isDone()) {
            Log.e(TAG, "Completing future exceptionally", e);
            resultFuture.completeExceptionally(e);
            cleanup();
//            new Handler(Looper.getMainLooper()).post(this::cleanup);
        }
    }

    private void updateCookies(String currentUrl) {
        if (config == null) {
            Log.w(TAG, "ServerConfig is null, cannot update cookies.");
            return;
        }
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(currentUrl);
        Log.d(TAG, "updateCookies for " + currentUrl + ": " + cookies);
        boolean configChanged = false;
        if (cookies != null) {
            config.setStringCookies(cookies);
            configChanged = true;
        }
        if (headers != null) {
            config.setHeaders(headers);
            configChanged = true;
        }

        if (configChanged) {
            new Thread(() -> ServerConfigRepository.getInstance().updateConfig(config)).start();
        }
    }

    private void cleanup() {
        timeoutHandler.removeCallbacksAndMessages(null);
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.setWebChromeClient(null);
            webView.destroy();
            webView = null;
        }
        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}