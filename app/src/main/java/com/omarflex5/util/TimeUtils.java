package com.omarflex5.util;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TimeUtils {

    /**
     * Formats milliseconds to HH:MM:SS or MM:SS
     */
    public static String formatTime(long millis) {
        if (millis <= 0)
            return "00:00";

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }
}
