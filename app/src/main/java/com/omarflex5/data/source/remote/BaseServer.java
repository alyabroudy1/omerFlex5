package com.omarflex5.data.source.remote;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BaseServer {
    private static final String BASE_URL = "https://api.themoviedb.org/3/";
    // TODO: Replace with your actual TMDB API Key
    private static final String API_KEY = "0ba8a205453bbcae968348bcb9a2976b";

    private static Retrofit retrofit;

    public static Retrofit getClient() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor(chain -> {
                        okhttp3.Request original = chain.request();
                        okhttp3.HttpUrl originalHttpUrl = original.url();

                        okhttp3.HttpUrl url = originalHttpUrl.newBuilder()
                                .addQueryParameter("api_key", API_KEY)
                                .addQueryParameter("language", "ar-SA")
                                .build();

                        okhttp3.Request.Builder requestBuilder = original.newBuilder()
                                .url(url);

                        okhttp3.Request request = requestBuilder.build();
                        return chain.proceed(request);
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
