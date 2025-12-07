// MainFragment.java
package com.omarflex5.temp.omerflex.view;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRowPresenter;

import com.omerflex.R;
import com.omerflex.entity.Movie;
import com.omerflex.server.Util;
import com.omerflex.service.casting.NetworkDiscoveryActivity;
import com.omerflex.service.cloudflare.UsageExample;
import com.omerflex.service.receiver.TvLinkReceiver;
import com.omerflex.service.receiver.TvServiceRegistrar;
import com.omerflex.view.viewConroller.MainFragmentController;
import com.omerflex.viewmodel.SharedViewModel;

import androidx.lifecycle.ViewModelProvider;

/**
 * The MainFragment is a lean view that sets up the UI and delegates
 * all business and UI logic to its controller.
 */
public class MainFragment extends BrowseSupportFragment {
    private static final String TAG = "MainFragment";


    private ArrayObjectAdapter mRowsAdapter;
    private MainFragmentController mController;
    private SharedViewModel sharedViewModel;


    // todo think about initialize and re initialize the activity

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);

        setupUIElements();
        prepareController();
        setupEventListeners();


//
//        try {
//
//                    Movie movie = new Movie();
//        movie.setState(Movie.BROWSER_STATE);
//        movie.setStudio(Movie.SERVER_CimaNow);
//            String url = " https://cimanow.cc/%d9%85%d8%b3%d9%84%d8%b3%d9%84-dexter-resurrection-%d8%a7%d9%84%d8%ad%d9%84%d9%82%d8%a9-10-%d8%a7%d9%84%d8%b9%d8%a7%d8%b4%d8%b1%d8%a9-%d9%85%d8%aa%d8%b1%d8%ac%d9%85%d8%a9/watching/";
//            url = "https://rm.freex2line.online/loadon/?link=aHR0cHM6Ly9jaW1hbm93LmNjLyVkOSU4NSVkOCViMyVkOSU4NCVkOCViMyVkOSU4NC1kZXh0ZXItcmVzdXJyZWN0aW9uLSVkOCVhNyVkOSU4NCVkOCVhZCVkOSU4NCVkOSU4MiVkOCVhOS0xMC0lZDglYTclZDklODQlZDglYjklZDglYTclZDglYjQlZDglYjElZDglYTktJWQ5JTg1JWQ4JWFhJWQ4JWIxJWQ4JWFjJWQ5JTg1JWQ4JWE5L3dhdGNoaW5nLw==";
//            String referer = "https://www.freex2line.online/2020/02/blog-post.html/";
////         referer = "https://cimanow.cc/";
//        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36";
//         userAgent = "Android 6";
//        String accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";
//        String acceptEncoding = "zip, deflate, br, zstd";
//        String acceptLanguage = "en-US,en;q=0.9";
//        String priority = "u=0";
////        String secChUa = "Not(A:Brand\";v=\"99\", \"Android WebView\";v=\"133\", \"Chromium\";v=\"133\"";
//        String secChUaMobile = "?1";
//        String secChUaPlatform = "Android";
//        String secFetchDest = "document";
//        String secFetchMode = "navigate";
//        String secFetchSite = "cross-site";
//        String secFetchUser = "?1";
//        String secGpc = "1";
//        String upgradeInsecureRequests = "1";
//
//
//
//        movie.setVideoUrl(url + "|Referer=" + referer + "&User-Agent=" + userAgent + "&Accept=" + accept + "&Accept-Encoding=" + acceptEncoding + "&Accept-Language=" + acceptLanguage + "&priority=" + priority + "&sec-ch-ua-mobile=" + secChUaMobile + "&sec-ch-ua-platform=" + secChUaPlatform + "&sec-fetch-dest=" + secFetchDest + "&sec-fetch-mode=" + secFetchMode + "&sec-fetch-site=" + secFetchSite + "&sec-fetch-user=" + secFetchUser + "&sec-gpc=" + secGpc + "&upgrade-insecure-requests=" + upgradeInsecureRequests);
//        Util.openBrowserIntent(movie, getActivity(), false, false,false);
            // Make a request and keep the response
//            Connection.Response response = Jsoup.connect(url)
//                    .method(Connection.Method.GET)
//                    .execute();
//
//            // Get the document if needed
//            Document doc = response.parse();
//            Log.d(TAG, "onActivityCreated: doc: "+ doc.title());
//
//            // Extract cookies from the response
//            Map<String, String> cookies = response.cookies();
//String cookieString = "";
//            for (Map.Entry<String, String> entry : cookies.entrySet()) {
//                Log.d("JSOUP", "Cookie: " + entry.getKey() + " = " + entry.getValue());
//                cookieString += entry.getKey() + "=" + entry.getValue() + ";";
//            }
//            CookieManager cookieManager = CookieManager.getInstance();
//            cookieManager.setAcceptCookie(true);
//            cookieManager.setCookie("https://www.freex2line.online/", cookieString);
//            url = " https://cimanow.cc/%d9%85%d8%b3%d9%84%d8%b3%d9%84-dexter-resurrection-%d8%a7%d9%84%d8%ad%d9%84%d9%82%d8%a9-10-%d8%a7%d9%84%d8%b9%d8%a7%d8%b4%d8%b1%d8%a9-%d9%85%d8%aa%d8%b1%d8%ac%d9%85%d8%a9/watching/";
//            movie.setVideoUrl(url + "|Referer=" + referer + "&Cookie=" + cookieString);
//            Util.openBrowserIntent(movie, getActivity(), false, false,false);
            // If you want to reuse cookies for another request:
//            Document doc2 = Jsoup.connect(url)
//                    .header("Referer", referer)
//                    .cookies(cookies)
//                    .get();

//            Log.d(TAG, "onActivityCreated: doc2: "+ doc2.outerHtml());
//            Log.d(TAG, "onActivityCreated: doc2: "+ doc2.title());

//        } catch (IOException e) {
//            Log.d(TAG, "onActivityCreated: error:"+ e.getMessage());
//            e.printStackTrace();
//        }

//        String iptvContents = "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 1\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221028428011214video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 2\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221029156447666video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 3\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221029774417256video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 4\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221030235980459video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 5\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221030732387163video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 6\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221031752858904video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 7\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221032284440635video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 8\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221032854988105video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 9\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221033332742077video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 10\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221034082239145video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 11\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221034632309962video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 12\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221035300843886video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 13\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221035818855909video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 14\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221036383632554video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 15\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221036874307057video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 16\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221037394019645video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 17\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221037850480752video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 18\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221038328802864video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 19\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221038825848845video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 20\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221039410396424video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 21\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221040689689875video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 22\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221041105635110video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 23\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221041606824897video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 24\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221042008755900video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 25\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221042974598774video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 26\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221043997819691video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 27\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221044410706023video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 28\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221044751013351video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 29\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221045408874445video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 30\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221045949026833video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 31\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221046412305279video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 32\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221046901681887video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 33\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221047294647163video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 34\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221047769021667video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 35\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221048165585008video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 36\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221048790741789video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 37\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221049610465519video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 38\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221050092281177video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 39\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221051264534699video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 40\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221051666255207video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 41\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221052358063874video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 42\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221052807621276video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 43\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221053721328336video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 44\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221056220055440video.mp4\n" +
//                "#EXTINF:-1 tvg-id=\"\" tvg-logo=\"https://github.com/6j9/mych/blob/master/img/ysf.jpg?raw=true\" group-title=\"مسلسل يوسف الصديق\",يوسف الصديق 45\n" +
//                "https://video.alkawthartv.ir/original/2023/06/11/638221057628550200video.mp4\n" +
//                "\n" +
//                "\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 1\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep1/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 2\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep2/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 3\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep3/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 4\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep4/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 5\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep5/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 6\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep6/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 7\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep7/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 8\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep8/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 9\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep9/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 10\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep10/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 11\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep11/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 12\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep12/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 13\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep13/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 14\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep14/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 15\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep15/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 16\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep16/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 17\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep17/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 18\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep18/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 19\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep19/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 20\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep20/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 21\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep21/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 22\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep22/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 23\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep23/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 24\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep24/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 25\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep25/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 26\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep26/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 27\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep27/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 28\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep28/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 29\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep29/720p/index.m3u8?//\n" +
//                "#EXTINF:-1 tvg-id=\"ext\"tvg-logo=\"https://bit.ly/3bVgIx1\" group-title=\"مسلسل قصص الأنبياء\",محمد الحلقة 30 و الأخيرة\n" +
//                "https://shoofmax.b-cdn.net/habib-allah/ep30/720p/index.m3u8?//";
//
//        ArrayList<Movie> movies = M3U8ContentFetcher.parseContentWithStreamingToList(iptvContents, "dd");
//        Log.d(TAG, "onActivityCreated: movies: "+ movies.size());
//        Log.d(TAG, "onActivityCreated: movies: "+ movies);

        // The controller is now responsible for loading data.
        mController.loadData();
//        Intent intent = new Intent(getActivity(), NetworkDiscoveryActivity.class);
//        intent.putExtra(CloudflareBypassActivity.EXTRA_URL, url);
//        intent.putExtra(CloudflareBypassActivity.EXTRA_MODE,
//                CloudflareBypassActivity.MODE_HTML_EXTRACTION);
//        startActivity(intent);
//        Movie movie = new Movie();
//        movie.setTitle("testo");
//        movie.setVideoUrl("https://www.w3schools.com/html/mov_bbb.mp4");
//        movie.setStudio(Movie.SERVER_FASELHD);
//        Util.openExoPlayer(movie, getActivity(), false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mController != null) {
            // Delegate the activity result to the controller.
            mController.handleOnActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mController != null) {
            // Delegate the activity result to the controller.
            mController.handleOnRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mController != null) {
            // Delegate the activity result to the controller.
            mController.handleOnDestroy();
        }
    }

    private void prepareController() {
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        Drawable defaultBackground = ContextCompat.getDrawable(getContext(), R.drawable.default_background);
        mController = new MainFragmentController(this, mRowsAdapter, defaultBackground);
        setAdapter(mRowsAdapter);
    }

    private void setupUIElements() {
//        setTitle(getString(R.string.browse_title));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setBrandColor(ContextCompat.getColor(getContext(), R.color.fastlane_background));
        setSearchAffordanceColor(ContextCompat.getColor(getContext(), R.color.search_opaque));
    }

    private void setupEventListeners() {
        // The fragment only handles simple UI events, delegating complex logic.
        setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent getQueryIntent = new Intent(getActivity(), GetSearchQueryActivity.class);
                startActivity(getQueryIntent);
            }
        });
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // The controller is now responsible for loading data.
        mController.handleOnDetach();
    }
}
