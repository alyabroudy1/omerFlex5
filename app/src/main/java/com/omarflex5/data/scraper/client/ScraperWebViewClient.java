package com.omarflex5.data.scraper.client;

import android.webkit.WebView;
import android.util.Log;

public class ScraperWebViewClient extends CoreWebViewClient {

    private static final String TAG = "ScraperWebViewClient";

    public ScraperWebViewClient(WebViewController controller) {
        super(controller);
    }
}
