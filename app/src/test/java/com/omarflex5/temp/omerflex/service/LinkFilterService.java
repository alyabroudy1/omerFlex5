package com.omarflex5.temp.omerflex.service;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;

import com.omerflex.entity.Movie;
import com.omerflex.server.Util;
//import com.omerflex.server.LarozaServer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class LinkFilterService {

    static String TAG = "LinkFilterService";

    private static final Set<String> MEDIA_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "avi", "mov", "wmv", "mp3", "m4a", "aac", "m3u8", "ts", "flv", "webm"// , "mpd"
    ));

    private static final Pattern MEDIA_PATTERN = Pattern.compile(
//            "(\\.m3u8|\\.mpd|\\.ts|seg-|chunk-|/quality/|/bitrate/)",
            "(\\.m3u8|\\.ts|seg-|chunk-|/quality/|/bitrate/)",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> PATTERNS_MOVIE_URL = Arrays.asList(
            "vidmoly",
            ".html"
//            "media200"
    );

    private static final List<String> PATTERNS_URL = Arrays.asList(
            "vidmoly",
//            "mbcvod-enc",
//            "click",
//            "brand",
            "/patrik",
            "adserver",
            ".php",
            ".gif",
            "error",
            "null",
            "/stub",
            "/library/",
            "afcdn.net/library/",
            ".html"
    );

    private static final Set<String> blockedDomains = new HashSet<>(
            Arrays.asList(
            "doubleclick.net",
            "googleadservices.com",
            "ads.pubmatic.com",
            "admob.com",
            "amazon-adsystem.com",
            "facebook.com", // Be careful with this one
            "criteo.com",
            "taboola.com",
            "outbrain.com",
            "yieldmo.com",
            "scorecardresearch.com",
            "quantserve.com",
            "roagrofoogrobo.com",
            "onmanectrictor.com",
            "2mdn.net",
            "adalliance.io",
            "adform.net",
            "adgrx.com",
            "adhigh.net",
            "admeld.com",
            "adnxs.com",
            "adroll.com",
            "adsafeprotected.com",
            "adsrvr.org",
            "adservice.google.com",
            "adtechus.com",
            "advertising.com",
            "adthor.com",
            "adyoulike.com",
            "amazon-adsystem.com",
            "aniview.com",
            "aps.amazon.com",
            "bidswitch.net",
            "bluekai.com",
            "brightroll.com",
            "casalemedia.com",
            "cdn.stickyadstv.com",
            "chartbeat.net",
            "clickaine.com",
            "cloudflareinsights.com",
            "criteo.com",
            "crwdcntrl.net",
            "demdex.net",
            "doubleclick.net",
            "doubleverify.com",
            "dpm.demdex.net",
            "emxdgt.com",
            "everesttech.net",
            "exelator.com",
            "eyeota.net",
            "facebook.net",
            "fbcdn.net",
            "gemius.pl",
            "google-analytics.com",
            "googleadservices.com",
            "googlesyndication.com",
            "googletagmanager.com",
            "hotjar.com",
            "hubspot.com",
            "imrworldwide.com",
            "innovid.com",
            "kruxdigital.com",
            "lijit.com",
            "loopme.com",
            "lotame.com",
            "media.net",
            "mediavoice.com",
            "mgid.com",
            "moatads.com",
            "mookie1.com",
            "mouseflow.com",
            "nativo.com",
            "nexac.com",
            "openx.net",
            "outbrain.com",
            "permuitive.com",
            "pinterest.com",
            "polarbyte.com",
            "prebid.org",
            "primis.tech",
            "propellorads.com",
            "pubmatic.com",
            "pushnative.com",
            "quantcount.com",
            "quantserve.com",
            "revcontent.com",
            "rfihub.com",
            "rhyhthmone.com",
            "richaudience.com",
            "rtbidder.net",
            "rubiconproject.com",
            "sascdn.com",
            "scorecardresearch.com",
            "semantic.com",
            "serving-sys.com",
            "sharethis.com",
            "sharethrough.com",
            "simpli.fi",
            "smartadserver.com",
            "sonobi.com",
            "sovrn.com",
            "spot.im",
            "spotxchange.com",
            "taboola.com",
            "tapad.com",
            "teads.tv",
            "themediagrid.com",
            "themidiagrid.com",
            "tidaltv.com",
            "tr.snapchat.com",
            "trafficfactory.biz",
            "tremorhub.com",
            "tribalfusion.com",
            "tru.am",
            "turn.com",
            "twitter.com",
            "ueili.com",
            "undertone.com",
            "uniconsent.com",
            "verizonmedia.com",
            "vidoomy.com",
            "vungle.com",
            "xaxis.com",
            "yandex.ru",
            "yieldlab.net",
            "yieldlove.com",
            "yieldmo.com",
            "zeal.com",
            "zedo.com",
            "zemanta.com",
            "33across.com",
            "360yield.com",
            "eehassoosostoa.com",
            "glempirteechacm.com"
            // ... Add more domains here!
    ));

    public static boolean isAdDomain(String domain) {
        return domain != null && blockedDomains.contains(domain);
    }

    public static boolean isSupportedMedia(WebResourceRequest request, String movieUrl)  {
//        Log.d(TAG, "isSupportedMedia: url: " + request.getUrl().toString());
        // Check if the URL contains any of the substrings
//        for (String pattern : PATTERNS_MOVIE_URL) {
//            if (movieUrl.contains(pattern)) {
////                Log.d(TAG, "isSupportedMedia: false PATTERNS_MOVIE_URL, "+ movieUrl);
//                return false;
//            }
//        }

        final Uri uri = request.getUrl();
        final String url = uri.toString().toLowerCase();
        final Map<String, String> headers = request.getRequestHeaders();

        // 1. Check for byte range requests

        if (LinkFilterService.isBlackListedUrl(url)){
          return false;
        }

        if (isMediaByHeaders(headers, url)) {
//            if (url.endsWith(".mp4")){return false;}
            return true;
        }

        // 2. Check for explicit media extensions
        if (isMediaByMimeType(url)) {
//            Log.d(TAG, "isSupportedMedia: true isMediaByMimeType, "+ url);
            return true;
        }
        // 3. Check URL patterns
        if (isMediaByUrlPattern(url)){
            Log.d(TAG, "isSupportedMedia: true isMediaByUrlPattern, "+ url);
            return true;
        }

        // 4. Additional checks for streaming protocols
//        if (url.contains("m3u8") || url.contains("mpd")) return true;

        return false;
    }

    public static boolean isSupportedMedia_3(WebResourceRequest request) {
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(request.getUrl().toString()));
        String acceptEncoding = request.getRequestHeaders().get("Accept-Encoding");
//        String accept = request.getRequestHeaders().get("Accept");
        boolean isAcceptEncoding = acceptEncoding != null && acceptEncoding.contains("identity;q=1");
        if (isAcceptEncoding) {
            Log.d(TAG, "isSupportedMedia: isAcceptEncoding: " + acceptEncoding);
            if (LinkFilterService.isBlackListedUrl(request.getUrl().toString())) {
                return false;
            }
            return true;
        }
//        Log.d(TAG, "isSupportedMedia: accept: "+ accept);
//        Log.d(TAG, "isSupportedMedia: acceptEncoding: "+ acceptEncoding);
        if (mimeType != null) {
            Log.d(TAG, "isSupportedMedia: mimeType: " + mimeType);
            if (mimeType.startsWith("video") || mimeType.startsWith("audio")) {
                Log.d(TAG, "isSupportedMedia: mimeType: " + mimeType + ", accept: " + acceptEncoding);
                if (request.getUrl().getPath().contains("index_")) {
//                    Log.d(TAG, "isSupportedMedia: path: "+request.getUrl().getPath());
//                    Log.d(TAG, "isSupportedMedia: headers: "+request.getRequestHeaders());
                    return false;
                }
                return true;
            }  // Check for video mimetypes
        }

        return false;
    }

    public static boolean isSupportedMedia_2(WebResourceRequest request) {
        // Cache frequently accessed objects
        final Uri uri = request.getUrl();
        final String url = uri.toString();
        final Map<String, String> headers = request.getRequestHeaders();

        // 1. Check Accept-Encoding condition first (cheapest check)
        final String acceptEncoding = headers.get("Accept-Encoding");
        if (acceptEncoding != null && acceptEncoding.contains("identity;q=1")) {
            Log.d(TAG, "isSupportedMedia: acceptEncoding: " + url);
            return !LinkFilterService.isBlackListedUrl(url);
        }

        // 2. Check MIME type only if needed
        final String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        final String mimeType = extension != null
                ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                : null;
        Log.d(TAG, "isSupportedMedia: mimeType: "+mimeType+ ", url: "+ url);
        if (mimeType == null) return false;

        // 3. Check for media types
        final boolean isMedia = mimeType.startsWith("video") || mimeType.startsWith("audio");
        if (!isMedia) return false;

        // 4. Final path check
        final String path = uri.getPath();
        return path == null || !path.contains("index_");
    }

    public static boolean isMediaByMimeType(String url) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(url).toLowerCase();
//        if (!extension.isEmpty()){
        Log.d(TAG, "isMediaByMimeType:extension: "+ extension);
//        }
        boolean isMedia = MEDIA_EXTENSIONS.contains(extension);

        return isMedia && !url.contains("index_");
    }

    public static boolean isMediaByHeaders(Map<String, String> headers, String url) {
        final String acceptEncoding = headers.get("Accept-Encoding");
        if (acceptEncoding != null){
            Log.d(TAG, "isMediaByHeaders: acceptEncoding: "+ acceptEncoding);
        }
        if (acceptEncoding != null && acceptEncoding.contains("identity;q=1")) {
            return !LinkFilterService.isBlackListedUrl(url);
        }
        final String range = headers.get("Range");
        if (range != null && range.startsWith("bytes=")){
            return true;
        }
        if (range != null){
            Log.d(TAG, "isMediaByHeaders: range: "+ range);
        }
        // 2. Check Content-Type header
        String contentType = headers.get("Content-Type");
        if (contentType != null && isMediaByContentType(contentType)) return true;

        return false;
    }

    public static boolean isMediaByUrlPattern(String url) {
        return MEDIA_PATTERN.matcher(url).find();
    }

    public static boolean isMediaByContentType(String mimeType) {
        return mimeType != null &&
                (mimeType.startsWith("video/") ||
                        mimeType.startsWith("audio/") ||
                        mimeType.equals("application/vnd.apple.mpegurl") ||
                        mimeType.equals("application/dash+xml"));
    }

    public static boolean isMediaRequest(WebResourceRequest request) {
        final String url = request.getUrl().toString().toLowerCase();

        // Fast path: Check common extensions first
        if (url.endsWith(".mp4") || url.endsWith(".m3u8")) return true;

        // Check for HLS/DASH patterns
        if (url.contains(".ts?") || url.contains("/seg-") || url.contains(".m4s")) return true;

        // Header analysis
        String rangeHeader = request.getRequestHeaders().get("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) return true;

        // MIME type fallback
        String mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url));
        return mime != null && (mime.startsWith("video") || mime.startsWith("audio"));
    }

    public static boolean isBlackListedUrl(String url) {
        // Check if the URL contains any of the substrings
        for (String pattern : PATTERNS_URL) {
            if (url.contains(pattern)) {
                return true;
            }
        }
        if (url.length() < 50){
            Log.d(TAG, "isBlackListedUrl: video url.length() < 50: "+ url);
            return true;
        }
        return false;
    }

    public static boolean isVideo(String url, Movie movie) {
        Log.d(TAG, "isVideo: "+url);
        // check movie url not the request url
        String movieUrl = movie.getVideoUrl();
        // Check if the URL contains any of the substrings
        for (String pattern : PATTERNS_MOVIE_URL) {
            if (movieUrl.contains(pattern)) {
                return false;
            }
        }

        // check request url
        if (LinkFilterService.isBlackListedUrl(url)) {
            return false;
        }


        // Token-based exclusion check
        if (url.contains("token=") &&
                (url.contains("inconsistencygasdifficult") || url.contains("video-delivery"))) {
            return false;
        }

        // Video extension and pattern checks
         return checkVideoExtensionsAndPatterns(url);
    }

    private static boolean checkVideoExtensionsAndPatterns(String url) {
        // Check .mp4 with additional conditions
        if (url.endsWith(".mp4")) {
            return isGenuineMp4(url);
        }

        // Check other video extensions
        if (isSimpleVideoExtension(url) ||
                (url.endsWith(".mov") && !url.contains("_ads"))) {
            return true;
        }

        // Check for file_code/token parameters without embed/watch
        if ((url.contains("file_code=") || url.contains("token=")) &&
                !(url.contains("embed") || url.contains("watch"))) {
            return true;
        }

        // Check for m3u8 anywhere in URL
        return url.contains(".m3u8");
    }

    private static boolean isGenuineMp4(String url) {
        // Check for "_a_" in filename part
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash != -1 && url.indexOf("_a_", lastSlash) != -1) {
            return false;
        }

        // Check other exclusion patterns
        return !(url.contains("themes") &&
                !url.contains("/test") &&
                !url.contains("cloudfront") &&
                url.length() >= 50 &&
                !url.contains("//store") &&
                !url.contains("//rabsh"));
    }

    private static boolean isSimpleVideoExtension(String url) {
        return url.endsWith(".avi") ||
                url.endsWith(".wmv") ||
                url.endsWith(".m3u") ||
                url.endsWith(".m3u8") ||
                url.endsWith(".mkv");
    }



    public static String decryptUrl(String encodedString, int shift) {
        // Step 1: Base64 decode the input string
        byte[] decodedBytes = Base64.decode(encodedString, Base64.DEFAULT);

        // Step 2: Convert bytes to UTF-8 string
        String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);

        // Step 3: Shift each character's code point
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < decodedString.length(); i++) {
            int codePoint = decodedString.codePointAt(i);
            codePoint -= shift;  // Subtract the shift value
            result.appendCodePoint(codePoint);
        }

        return result.toString();
    }

    // Overload with default shift value (e=3)
    public static String decryptUrl(String encodedString) {
        return decryptUrl(encodedString, 3);
    }
}
