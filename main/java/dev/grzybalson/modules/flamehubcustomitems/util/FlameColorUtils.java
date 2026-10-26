package dev.grzybalson.modules.flamehubcustomitems.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FlameColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private FlameColorUtils() {
    }

    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        String normalized = text.replace('§', '&');
        Matcher matcher = HEX_PATTERN.matcher(normalized);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);

        return SERIALIZER.deserialize(buffer.toString());
    }

    public static Component colorizeItem(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        Component component = colorize(text);
        if (!text.contains("&o") && !text.contains("§o")) {
            return component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }
        return component;
    }

    public static List<Component> colorizeList(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(colorizeItem(line));
        }
        return components;
    }
}
