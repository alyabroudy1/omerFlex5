package com.omarflex5.engine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Map;

/**
 * WebView implementation of IWebEngine.
 * Wraps Android's standard WebView for the webview flavor.
 */
public class WebViewEngine implements IWebEngine {

    private final WebView webView;
    private IWebEngineClient client;
    private final Context context;

    @SuppressLint("SetJavaScriptEnabled")
    public WebViewEngine(Context context) {
        this.context = context;
        this.webView = new WebView(context);

        // Apply default settings
        applySettings(WebEngineSettings.createDefault());

        // Set up internal clients
        setupInternalClients();
    }

    private void setupInternalClients() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (client != null) {
                    client.onPageStarted(WebViewEngine.this, url, favicon);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (client != null) {
                    client.onPageFinished(WebViewEngine.this, url);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (client != null) {
                    WebResourceResponse response = client.shouldInterceptRequest(WebViewEngine.this, request);
                    if (response != null) {
                        return response;
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (client != null) {
                    return client.shouldOverrideUrlLoading(WebViewEngine.this, request.getUrl().toString());
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (client != null) {
                    client.onError(WebViewEngine.this, errorCode, description, failingUrl);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (client != null) {
                    client.onProgressChanged(WebViewEngine.this, newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (client != null) {
                    client.onTitleChanged(WebViewEngine.this, title);
                }
            }
        });
    }

    @Override
    public View getView() {
        return webView;
    }

    @Override
    public void loadUrl(String url) {
        webView.loadUrl(url);
    }

    @Override
    public void loadUrl(String url, Map<String, String> headers) {
        webView.loadUrl(url, headers);
    }

    @Override
    public void loadHtml(String html, String baseUrl) {
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
    }

    @Override
    public void evaluateJavascript(String script, ValueCallback<String> callback) {
        webView.evaluateJavascript(script, callback);
    }

    @SuppressLint("JavascriptInterface")
    @Override
    public void addJavascriptInterface(Object object, String name) {
        webView.addJavascriptInterface(object, name);
    }

    @Override
    public void setEngineClient(IWebEngineClient client) {
        this.client = client;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void applySettings(WebEngineSettings settings) {
        WebSettings webSettings = webView.getSettings();

        webSettings.setJavaScriptEnabled(settings.isJavaScriptEnabled());
        webSettings.setDomStorageEnabled(settings.isDomStorageEnabled());
        webSettings.setDatabaseEnabled(settings.isDatabaseEnabled());
        webSettings.setAllowFileAccess(settings.isAllowFileAccess());
        webSettings.setAllowContentAccess(settings.isAllowContentAccess());
        webSettings.setJavaScriptCanOpenWindowsAutomatically(settings.isJavaScriptCanOpenWindowsAutomatically());
        webSettings.setMediaPlaybackRequiresUserGesture(settings.isMediaPlaybackRequiresUserGesture());
        webSettings.setUseWideViewPort(settings.isUseWideViewPort());
        webSettings.setLoadWithOverviewMode(settings.isLoadWithOverviewMode());
        webSettings.setSupportZoom(settings.isSupportZoom());
        webSettings.setBuiltInZoomControls(settings.isBuiltInZoomControls());
        webSettings.setDisplayZoomControls(settings.isDisplayZoomControls());

        if (settings.getUserAgent() != null) {
            webSettings.setUserAgentString(settings.getUserAgent());
        }

        // Cache mode
        switch (settings.getCacheMode()) {
            case "no-cache":
                webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
                break;
            case "cache-only":
                webSettings.setCacheMode(WebSettings.LOAD_CACHE_ONLY);
                break;
            case "cache-else-network":
                webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                break;
            default:
                webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        }

        // Mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (settings.isMixedContentAllowed()) {
                webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            } else {
                webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            }
        }

        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
    }

    @Override
    public String getUserAgent() {
        return webView.getSettings().getUserAgentString();
    }

    @Override
    public String getCurrentUrl() {
        return webView.getUrl();
    }

    @Override
    public boolean canGoBack() {
        return webView.canGoBack();
    }

    @Override
    public void goBack() {
        webView.goBack();
    }

    @Override
    public void stopLoading() {
        webView.stopLoading();
    }

    @Override
    public void reload() {
        webView.reload();
    }

    @Override
    public void destroy() {
        webView.stopLoading();
        webView.clearHistory();
        webView.clearCache(true);
        webView.loadUrl("about:blank");
        webView.onPause();
        webView.removeAllViews();
        webView.destroyDrawingCache();
        webView.destroy();
    }

    /**
     * Get the underlying WebView for advanced usage.
     * Use sparingly - prefer the IWebEngine interface.
     */
    public WebView getWebView() {
        return webView;
    }
}
