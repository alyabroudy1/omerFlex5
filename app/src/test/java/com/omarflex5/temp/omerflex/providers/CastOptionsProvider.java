package com.omarflex5.temp.omerflex.providers;

import android.content.Context;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;
import com.google.android.gms.cast.framework.media.NotificationOptions;
import com.omerflex.view.ExoplayerMediaPlayer;

import java.util.List;

public class CastOptionsProvider implements OptionsProvider {

    @Override
    public CastOptions getCastOptions(Context context) {
        // The NotificationOptions allows the Cast framework to create a notification
        // to control media playback.
        NotificationOptions notificationOptions = new NotificationOptions.Builder()
                .setTargetActivityClassName(ExoplayerMediaPlayer.class.getName()) // Replace with your player activity
                .build();

        CastMediaOptions mediaOptions = new CastMediaOptions.Builder()
                .setNotificationOptions(notificationOptions)
                .build();

        // This is the most important part.
        // It tells the Cast SDK what app to launch on the receiver device.
        // For now, we use the Default Media Receiver, which can handle basic
        // video and audio playback without any extra development on the receiver side.
        return new CastOptions.Builder()
                .setReceiverApplicationId("CC1AD845") // This is the ID for the Default Media Receiver
                .setCastMediaOptions(mediaOptions)
                .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}