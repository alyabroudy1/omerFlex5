package com.omarflex5.temp.webtest;

public class FetcherManager {
    private static FetcherManager instance;
    private WebViewFetcher fetcher;

    private FetcherManager() {
    }

    public static synchronized FetcherManager getInstance() {
        if (instance == null) {
            instance = new FetcherManager();
        }
        return instance;
    }

    public void setFetcher(WebViewFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public WebViewFetcher getFetcher() {
        return fetcher;
    }
}
