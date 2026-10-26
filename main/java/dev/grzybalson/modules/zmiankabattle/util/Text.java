package dev.grzybalson.modules.zmiankabattle.util;

import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

public final class Text {
    private static final DecimalFormat NO_DECIMALS = new DecimalFormat("#,##0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat TWO_DECIMALS = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.US));

    private Text() {}

    public static String color(String s) {
        if (s == null) return "";
        return ColorUtils.colorize(s);
    }

    public static Component component(String legacy) {
        return ColorUtils.colorizeComponent(legacy);
    }

    public static String apply(String s, Map<String, String> placeholders) {
        if (s == null) return "";
        String out = s;
        for (var e : placeholders.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    public static Component applyComponent(String s, Map<String, String> placeholders) {
        return component(apply(s, placeholders));
    }

    public static String formatMoney(double amount) {
        // 100 -> 100, 100.5 -> 100.50
        if (Math.abs(amount - Math.rint(amount)) < 1.0E-9) {
            return NO_DECIMALS.format(amount);
        }
        return TWO_DECIMALS.format(amount);
    }
}
