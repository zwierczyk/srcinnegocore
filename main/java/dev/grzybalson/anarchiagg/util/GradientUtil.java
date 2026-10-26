package dev.grzybalson.anarchiagg.util;

import net.md_5.bungee.api.ChatColor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradientUtil {

    private static final Pattern GRADIENT_RANGE_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})&#([A-Fa-f0-9]{6})([^&#]*)");

    public static String applyGradient(String text) {
        Matcher matcher = GRADIENT_RANGE_PATTERN.matcher(text);
        StringBuilder output = null;

        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String content = matcher.group(3);

            String gradient = createGradient(startHex, endHex, content);
            if (output == null) {
                output = new StringBuilder(text.length() + gradient.length());
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(gradient));
        }

        if (output == null) {
            return dev.grzybalson.anarchiagg.util.TextUtil.fixColor(text);
        }

        matcher.appendTail(output);
        return dev.grzybalson.anarchiagg.util.TextUtil.fixColor(output.toString());
    }

    public static String createGradient(String startHex, String endHex, String text) {
        Color start = Color.decode("#" + startHex);
        Color end = Color.decode("#" + endHex);

        StringBuilder result = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++) {
            double ratio = (double) i / length;
            int red = (int) (start.getRed() + (end.getRed() - start.getRed()) * ratio);
            int green = (int) (start.getGreen() + (end.getGreen() - start.getGreen()) * ratio);
            int blue = (int) (start.getBlue() + (end.getBlue() - start.getBlue()) * ratio);

            String hex = String.format("#%02x%02x%02x", red, green, blue);
            result.append(ChatColor.of(hex)).append(text.charAt(i));
        }

        return result.toString();
    }

    public static String colorize(String text) {
        return dev.grzybalson.anarchiagg.util.TextUtil.fixColor(text);
    }

    public static List<String> colorizeList(List<String> list) {
        List<String> colored = new ArrayList<>();
        for (String line : list) {
            colored.add(colorize(line));
        }
        return colored;
    }
}
