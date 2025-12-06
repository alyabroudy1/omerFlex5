package com.omarflex5.temp.webtest;

import android.webkit.JavascriptInterface;

public class WebAppInterface {
    private final MainActivity mContext;

    public WebAppInterface(MainActivity c) {
        mContext = c;
    }

    @JavascriptInterface
    public void onHtmlExtracted(String html, String cookies) {
        mContext.onHtmlExtracted(html, cookies);
    }

    @JavascriptInterface
    public void onCloudflareDetected(boolean isDetected) {
        mContext.onCloudflareDetected(isDetected);
    }

    @JavascriptInterface
    public void onVideoElementDetected(String url) {
        mContext.onVideoDetected(url);
    }
}
