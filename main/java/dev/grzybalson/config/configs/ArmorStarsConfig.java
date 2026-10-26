package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class ArmorStarsConfig extends OkaeriConfig {

    private String swordSymbol = "\uE004";
    private String armorSymbol = "\uE012";
    private String swordFormat = "&#6CC9FF{symbol}{level}";
    private String armorFormat = "&#98FF6C{symbol}{level}";
    private String iconFormat = "{sword} {armor}";
    private double armorPieceFactor = 0.25D;

    public String getSwordSymbol() {
        return swordSymbol != null ? swordSymbol : "\uE004";
    }

    public String getArmorSymbol() {
        return armorSymbol != null ? armorSymbol : "\uE012";
    }

    public String getSwordFormat() {
        return swordFormat != null ? swordFormat : "&#6CC9FF{symbol}{level}";
    }

    public String getArmorFormat() {
        return armorFormat != null ? armorFormat : "&#98FF6C{symbol}{level}";
    }

    public String getIconFormat() {
        return iconFormat != null ? iconFormat : "{sword} {armor}";
    }

    public double getArmorPieceFactor() {
        return armorPieceFactor > 0.0D ? armorPieceFactor : 0.25D;
    }
}
