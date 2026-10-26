package dev.grzybalson.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class TimeUtil {

    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

    private TimeUtil() {
    }

    public static String convert(long millis) {
        if (millis < 1L) {
            return "<1ms";
        }

        long totalDays = TimeUnit.MILLISECONDS.toDays(millis);
        long months = totalDays / 30;
        long days = totalDays % 30;
        long hours = TimeUnit.MILLISECONDS.toHours(millis) - TimeUnit.DAYS.toHours(totalDays);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
            - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
            - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis));

        List<String> parts = new ArrayList<>();
        if (months > 0) parts.add(months + "msc");
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "min");
        if (seconds > 0 || parts.isEmpty()) parts.add(seconds + "s");

        return String.join(", ", parts);
    }

    public static long fromDate(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            Date date = FORMAT.parse(value);
            return date != null ? date.getTime() : 0L;
        } catch (ParseException exception) {
            return 0L;
        }
    }

    public static boolean isEnableDateActive(long enableDateMillis) {
        return enableDateMillis > 0 && System.currentTimeMillis() < enableDateMillis;
    }
}
