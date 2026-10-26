package dev.grzybalson.modules.upgrade;

import java.util.Locale;

public enum UpgradeStyle {
    STANDARD("standard", "Standard", "Netherite"),
    SKYPVP("skypvp", "SkyPVP", "SkyPVP");

    private final String configKey;
    private final String displayName;
    private final String shortLabel;

    UpgradeStyle(String configKey, String displayName, String shortLabel) {
        this.configKey = configKey;
        this.displayName = displayName;
        this.shortLabel = shortLabel;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public boolean isSkyPvp() {
        return this == SKYPVP;
    }

    public static UpgradeStyle fromArgument(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT)
            .replace("-", "_")
            .replace(" ", "");
        return switch (normalized) {
            case "SKYPVP", "SKY_PVP", "SKY", "PVP" -> SKYPVP;
            case "STANDARD", "NORMAL", "NETHERITE", "DEFAULT" -> STANDARD;
            default -> {
                for (UpgradeStyle style : values()) {
                    if (style.name().equals(normalized) || style.configKey.equalsIgnoreCase(value.trim())) {
                        yield style;
                    }
                }
                yield STANDARD;
            }
        };
    }
}
