package dev.grzybalson.modules.szaman;

import java.util.Locale;

public enum PerkType {
    LIVE,
    STRENGTH,
    SLOWNESS,
    LIFESTEAL,
    TRAP,
    DROP,
    RANKING,
    BLOCK;

    public static PerkType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return PerkType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
