package dev.grzybalson.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Zamienia http/https/www w komponencie czatu na klikalne {@code openUrl}.
 * Musi iść PO hoverze na całą wiadomość — własne clickEvent na URL nadpisuje runCommand nicka.
 */
public final class ChatLinkifier {

    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?i)(?:https?://|www\\.)[-a-z0-9+&@#/%?=~_!:,.;]*[-a-z0-9+&@#/%=~_]"
    );

    private ChatLinkifier() {
    }

    public static Component apply(Component component) {
        if (component == null) {
            return null;
        }
        return component.replaceText(TextReplacementConfig.builder()
            .match(URL_PATTERN)
            .replacement((matchResult, builder) -> {
                String openable = toOpenableUrl(matchResult.group());
                if (openable == null) {
                    return builder;
                }
                return builder
                    .clickEvent(ClickEvent.openUrl(openable))
                    .decoration(TextDecoration.UNDERLINED, true)
                    .color(NamedTextColor.AQUA)
                    .hoverEvent(HoverEvent.showText(Component.text("Kliknij, aby otworzyć link", NamedTextColor.GRAY)));
            })
            .build());
    }

    private static String toOpenableUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String url = raw.trim();
        if (url.regionMatches(true, 0, "www.", 0, 4)) {
            url = "https://" + url;
        }
        if (!url.regionMatches(true, 0, "http://", 0, 7)
            && !url.regionMatches(true, 0, "https://", 0, 8)) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return null;
            }
            return url;
        } catch (Exception ignored) {
            return null;
        }
    }
}
