package dev.grzybalson.modules.sigil;

import org.bukkit.Material;

public enum SigilCategory {
    FURIA("Furia", "&c", "FF1A1A", "FF6B00", Material.RED_DYE),
    OBRONA("Obrona", "&#00C2FF", "00C2FF", "39FFD6", Material.LIGHT_BLUE_DYE),
    MAGIA("Magia", "&#B266FF", "B266FF", "FF66E5", Material.PURPLE_DYE),
    FORTUNA("Fortuna", "&#FFAA00", "FFAA00", "FFE566", Material.ORANGE_DYE),
    CIEN("Cien", "&#C8C8C8", "C0C0C0", "F0F0F0", Material.LIGHT_GRAY_DYE);

    private final String displayName;
    private final String color;
    private final String gradientStart;
    private final String gradientEnd;
    private final Material dye;

    SigilCategory(String displayName, String color, String gradientStart, String gradientEnd, Material dye) {
        this.displayName = displayName;
        this.color = color;
        this.gradientStart = gradientStart;
        this.gradientEnd = gradientEnd;
        this.dye = dye;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getGradientStart() {
        return gradientStart;
    }

    public String getGradientEnd() {
        return gradientEnd;
    }

    public Material getDye() {
        return dye;
    }
}
