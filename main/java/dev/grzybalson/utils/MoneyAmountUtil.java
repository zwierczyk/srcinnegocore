package dev.grzybalson.utils;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class MoneyAmountUtil {

    private MoneyAmountUtil() {
    }

    public static @Nullable Double parse(String input) {
        if (input == null) {
            return null;
        }

        String raw = input.trim().replace(',', '.').replace(" ", "").toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return null;
        }

        double multiplier = 1.0D;
        if (raw.endsWith("mil")) {
            multiplier = 1_000_000D;
            raw = raw.substring(0, raw.length() - 3);
        } else if (raw.endsWith("kk")) {
            multiplier = 1_000_000D;
            raw = raw.substring(0, raw.length() - 2);
        } else if (raw.endsWith("k")) {
            multiplier = 1_000D;
            raw = raw.substring(0, raw.length() - 1);
        } else if (raw.endsWith("m")) {
            multiplier = 1_000_000D;
            raw = raw.substring(0, raw.length() - 1);
        } else if (raw.endsWith("b")) {
            multiplier = 1_000_000_000D;
            raw = raw.substring(0, raw.length() - 1);
        } else if (raw.endsWith("t")) {
            multiplier = 1_000_000_000_000D;
            raw = raw.substring(0, raw.length() - 1);
        }

        if (raw.isEmpty()) {
            return null;
        }

        try {
            double value = Double.parseDouble(raw) * multiplier;
            return isFinite(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static boolean isPositiveFinite(double value) {
        return isFinite(value) && value > 0.0D;
    }

    public static boolean isNonNegativeFinite(double value) {
        return isFinite(value) && value >= 0.0D;
    }

    public static double sanitizeStoredBalance(@Nullable Double value, double fallback) {
        if (value == null || !isNonNegativeFinite(value)) {
            return fallback;
        }
        return value;
    }

    public static String formatCompact(double value) {
        if (!isFinite(value)) {
            return "0";
        }

        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000D) {
            return formatSuffix(value / 1_000_000_000_000D, "t");
        }
        if (abs >= 1_000_000_000D) {
            return formatSuffix(value / 1_000_000_000D, "b");
        }
        if (abs >= 1_000_000D) {
            return formatSuffix(value / 1_000_000D, "m");
        }
        if (abs >= 1_000D) {
            return formatSuffix(value / 1_000D, "k");
        }
        return trimTrailingZeros(String.format(Locale.US, "%.2f", value));
    }

    private static String formatSuffix(double value, String suffix) {
        return trimTrailingZeros(String.format(Locale.US, "%.2f", value)) + suffix;
    }

    private static String trimTrailingZeros(String value) {
        if (value.indexOf('.') < 0) {
            return value;
        }
        return value.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
