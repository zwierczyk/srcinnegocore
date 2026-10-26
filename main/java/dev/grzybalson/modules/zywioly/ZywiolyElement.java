package dev.grzybalson.modules.zywioly;

import java.util.Locale;

enum ZywiolyElement {
    OGIEN,
    NATURA,
    WODA;

    static ZywiolyElement fromKey(String key) {
        if (key == null) {
            return null;
        }
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "ogien", "ogień", "fire" -> OGIEN;
            case "natura", "nature" -> NATURA;
            case "woda", "water" -> WODA;
            default -> null;
        };
    }

    String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
