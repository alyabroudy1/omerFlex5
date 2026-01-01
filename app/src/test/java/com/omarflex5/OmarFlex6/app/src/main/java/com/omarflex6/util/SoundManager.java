package com.omarflex6.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.View;

import com.omarflex6.R;

/**
 * Manages UI sound effects for the application.
 * Currently uses system sounds as placeholders, but ready for custom assets.
 */
public class SoundManager {
    private static SoundManager instance;
    private SoundPool soundPool;
    private int soundNav;
    private int soundSelect;
    private int soundBack;
    private boolean useSystemSounds = true; // Set to false once raw assets are added

    private SoundManager(Context context) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build();

        // Placeholder: Attempt to load raw resources if they exist
        // Note: Since we don't have these files yet, we will catch errors or just flag
        // to use system sounds
        // soundNav = soundPool.load(context, R.raw.snd_nav, 1);
        // ...
    }

    public static synchronized SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context.getApplicationContext());
        }
        return instance;
    }

    public void playNav(View view) {
        if (useSystemSounds) {
            if (view != null)
                view.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_RIGHT);
        } else {
            // soundPool.play(soundNav, 1f, 1f, 0, 0, 1f);
        }
    }

    public void playSelect(View view) {
        if (useSystemSounds) {
            if (view != null)
                view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
        } else {
            // soundPool.play(soundSelect, 1f, 1f, 0, 0, 1f);
        }
    }

    public void cleanup() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
    }
}
