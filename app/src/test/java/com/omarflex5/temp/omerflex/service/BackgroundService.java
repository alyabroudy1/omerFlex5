package com.omarflex5.temp.omerflex.service;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.omerflex.R;

import java.util.Timer;
import java.util.TimerTask;

public class BackgroundService {
    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private final Handler mHandler = new Handler(Looper.myLooper());
    private final BackgroundManager mBackgroundManager;
    private final Activity mActivity;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private String mBackgroundUri;

    public BackgroundService(Activity activity) {
        mActivity = activity;
        mBackgroundManager = BackgroundManager.getInstance(mActivity);
        if (!mBackgroundManager.isAttached()) {
            mBackgroundManager.attach(mActivity.getWindow());
        }
        mDefaultBackground = ContextCompat.getDrawable(mActivity, R.drawable.default_background);
        mMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    public void updateBackground(String uri) {
        mBackgroundUri = uri;
        startBackgroundTimer();
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    public void release() {
        if (mBackgroundTimer != null) {
            mBackgroundTimer.cancel();
            mBackgroundTimer = null;
        }
    }

    private class UpdateBackgroundTask extends TimerTask {
        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    performUpdateBackground(mBackgroundUri);
                }
            });
        }
    }

    private void performUpdateBackground(String uri) {
        try {
            if (mActivity.isDestroyed() || mActivity.isFinishing()) {
                return;
            }
            int width = mMetrics.widthPixels;
            int height = mMetrics.heightPixels;
            Glide.with(mActivity)
                    .load(uri)
                    .centerCrop()
                    .error(mDefaultBackground)
                    .into(new SimpleTarget<Drawable>(width, height) {
                        @Override
                        public void onResourceReady(@NonNull Drawable drawable,
                                                    @Nullable Transition<? super Drawable> transition) {
                            if (mActivity.isDestroyed() || mActivity.isFinishing()) {
                                return;
                            }
                            if (mBackgroundManager.isAttached()) {
                                mBackgroundManager.setDrawable(drawable);
                            }
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
            // Optionally, set a default background in case of an exception
            if (mBackgroundManager.isAttached()) {
                mBackgroundManager.setDrawable(mDefaultBackground);
            }
        }
    }
}
