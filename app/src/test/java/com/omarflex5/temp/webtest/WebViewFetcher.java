package com.omarflex5.temp.webtest;

public interface WebViewFetcher {

    /**
     * Request WebView to fetch a URL and stream the response back
     */
    void fetchUrl(String url, FetchCallback callback);

    /**
     * Callback interface for receiving fetch results
     */
    interface FetchCallback {
        /**
         * Called when data chunk is received
         */
        void onDataReceived(byte[] data, int length);

        /**
         * Called when fetch is complete
         */
        void onComplete(long totalBytes);

        /**
         * Called if fetch fails
         */
        void onError(String error);
    }
}
