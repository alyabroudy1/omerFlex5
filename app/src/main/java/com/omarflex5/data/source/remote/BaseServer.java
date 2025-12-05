package com.omarflex5.data.source.remote;

import android.util.Log;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BaseServer {
    private static final String TAG = "BaseServer";
    private static final String BASE_URL = "https://api.themoviedb.org/3/";
    // TODO: Replace with your actual TMDB API Key
    private static final String API_KEY = "0ba8a205453bbcae968348bcb9a2976b";

    private static Retrofit retrofit;

    public static Retrofit getClient() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .addInterceptor(chain -> {
                        okhttp3.Request original = chain.request();
                        okhttp3.HttpUrl originalHttpUrl = original.url();

                        okhttp3.HttpUrl.Builder urlBuilder = originalHttpUrl.newBuilder()
                                .addQueryParameter("api_key", API_KEY);

                        // Only add Arabic language if not already specified in the request
                        if (originalHttpUrl.queryParameter("language") == null) {
                            urlBuilder.addQueryParameter("language", "ar-SA");
                        }

                        okhttp3.Request.Builder requestBuilder = original.newBuilder()
                                .url(urlBuilder.build());

                        okhttp3.Request request = requestBuilder.build();
                        return chain.proceed(request);
                    });

            // For debug builds on emulator with outdated certs, bypass SSL verification
            // WARNING: This should NEVER be used in production!
            try {
                // Create a trust manager that accepts all certificates
                final TrustManager[] trustAllCerts = new TrustManager[] {
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType)
                                    throws CertificateException {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType)
                                    throws CertificateException {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[] {};
                            }
                        }
                };

                // Install the trust manager
                final SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

                // Create SSL socket factory
                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                clientBuilder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
                clientBuilder.hostnameVerifier((hostname, session) -> true);

                Log.w(TAG, "SSL verification disabled for debug build - DO NOT use in production!");
            } catch (Exception e) {
                Log.e(TAG, "Error setting up SSL bypass: " + e.getMessage());
            }

            OkHttpClient client = clientBuilder.build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
