package com.omarflex5.image;

import android.content.Context;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;
import java.net.CookieHandler;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * Custom Glide module that uses OkHttp with WebView's CookieManager.
 * This ensures CF cookies are sent when loading images from protected sites.
 */
@GlideModule
public final class MyAppGlideModule extends AppGlideModule {

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // Create OkHttpClient that uses WebView's CookieManager
        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(new WebViewCookieJar())
                .build();

        // Register the OkHttp client with Glide
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(client));
    }

    /**
     * CookieJar that bridges OkHttp and Android's CookieManager (WebView cookies).
     */
    private static class WebViewCookieJar implements CookieJar {

        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            CookieManager cookieManager = CookieManager.getInstance();
            if (cookieManager != null) {
                String urlStr = url.toString();
                for (Cookie cookie : cookies) {
                    cookieManager.setCookie(urlStr, cookie.toString());
                }
            }
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            CookieManager cookieManager = CookieManager.getInstance();
            if (cookieManager == null) {
                android.util.Log.d("GlideCookies", "CookieManager is null");
                return Collections.emptyList();
            }

            // Try with exactly the URL first, then fall back to base domain
            String urlStr = url.toString();
            String cookieHeader = cookieManager.getCookie(urlStr);

            // If no cookies, try with just the base domain (important for subdomain
            // matching)
            if (cookieHeader == null || cookieHeader.isEmpty()) {
                String baseDomain = "https://" + url.host();
                cookieHeader = cookieManager.getCookie(baseDomain);
                android.util.Log.d("GlideCookies", "Using base domain for cookies: " + baseDomain);
            }

            if (cookieHeader == null || cookieHeader.isEmpty()) {
                android.util.Log.d("GlideCookies", "No cookies found for: " + url.host());
                return Collections.emptyList();
            }

            android.util.Log.d("GlideCookies", "Found cookies for " + url.host() + ": " +
                    (cookieHeader.length() > 100 ? cookieHeader.substring(0, 100) + "..." : cookieHeader));

            // Parse cookies from header
            List<Cookie> cookies = new ArrayList<>();
            String[] cookiePairs = cookieHeader.split(";");
            for (String cookiePair : cookiePairs) {
                cookiePair = cookiePair.trim();
                int eqIdx = cookiePair.indexOf('=');
                if (eqIdx > 0) {
                    String name = cookiePair.substring(0, eqIdx);
                    String value = cookiePair.substring(eqIdx + 1);
                    Cookie cookie = new Cookie.Builder()
                            .domain(url.host())
                            .path("/")
                            .name(name)
                            .value(value)
                            .build();
                    cookies.add(cookie);
                }
            }
            return cookies;
        }
    }
}
