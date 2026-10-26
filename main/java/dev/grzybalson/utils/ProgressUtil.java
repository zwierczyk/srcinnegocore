package dev.grzybalson.utils;

public final class ProgressUtil {

    private ProgressUtil() {
    }

    public static String bar(long current, long required, int blocks) {
        if (required <= 0) {
            return "";
        }
        long clamped = Math.min(current, required);
        double percentage = (double) clamped / required;
        int filled = (int) Math.round(percentage * blocks);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < filled; i++) {
            builder.append("&a⏹");
        }
        for (int i = filled; i < blocks; i++) {
            builder.append("&c⏹");
        }
        return ColorUtils.colorize(builder.toString());
    }

    public static String percent(long current, long required) {
        if (required <= 0) {
            return "0%";
        }
        long clamped = Math.min(current, required);
        int rounded = (int) Math.round((double) clamped / required * 100.0);
        return Math.min(100, rounded) + "%";
    }
}
