package dev.grzybalson.modules.cases;

import java.util.Locale;

public enum CaseKeyType {
    KEY,
    LUCKYBLOCK;

    public static CaseKeyType fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return KEY;
        }

        String normalized = raw.trim()
            .toUpperCase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "_");

        if (normalized.equals("LUCKY_BLOCK") || normalized.equals("BLOCK")) {
            return LUCKYBLOCK;
        }

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return KEY;
        }
    }
}
