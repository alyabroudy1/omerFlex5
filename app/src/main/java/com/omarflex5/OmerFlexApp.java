package com.omarflex5;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.omarflex5.worker.ContentDiscoveryWorker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class OmerFlexApp extends Application {

    private static final String PREF_NAME = "omerflex_prefs";
    private static final String KEY_LAST_FEED_DATE = "last_feed_date";

    @Override
    public void onCreate() {
        super.onCreate();
        checkAndScheduleFeeder();
    }

    private void checkAndScheduleFeeder() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String lastFeedDate = prefs.getString(KEY_LAST_FEED_DATE, "");
        String today = LocalDate.now().toString();

        if (!today.equals(lastFeedDate)) {
            // Not run today yet
            scheduleFeeder();
            // Mark as run for today (optimistic, or move to Worker success)
            prefs.edit().putString(KEY_LAST_FEED_DATE, today).apply();
        }
    }

    private void scheduleFeeder() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Any network
                .build();

        OneTimeWorkRequest feedRequest = new OneTimeWorkRequest.Builder(ContentDiscoveryWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueue(feedRequest);
    }
}
