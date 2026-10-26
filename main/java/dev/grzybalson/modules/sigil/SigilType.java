package dev.grzybalson.modules.sigil;

import org.bukkit.Material;

import java.util.Locale;

public enum SigilType {
    FURIA(0, SigilCategory.FURIA, "Furia", "+{VALUE}% damage on every hit"),
    GNIEW(1, SigilCategory.FURIA, "Gniew", "applies +0.5 heart extra damage"),
    PRZEBICIE(2, SigilCategory.FURIA, "Przebicie", "ignore {VALUE}% armor"),
    WYTRZYMALOSC(3, SigilCategory.OBRONA, "Wytrzymalosc", "{VALUE}% less damage taken"),
    TARCZA(4, SigilCategory.OBRONA, "Tarcza", "{VALUE}% chance to block attack"),
    ODPORNOSC(5, SigilCategory.OBRONA, "Odpornosc", "{VALUE}% chance to ignore negative effects"),
    JAD(6, SigilCategory.MAGIA, "Jad", "applies Levitation I for 2s"),
    KLATWA(7, SigilCategory.MAGIA, "Klatwa", "{VALUE}% chance to weaken"),
    DRENAZ(8, SigilCategory.MAGIA, "Drenaz", "steal {VALUE}% damage as life"),
    SKARB(9, SigilCategory.FORTUNA, "Skarb", "{VALUE}% chance for double drop payout"),
    SZCZESCIE(10, SigilCategory.FORTUNA, "Szczescie", "drop payout increased by {VALUE}%"),
    DOBROBYT(11, SigilCategory.FORTUNA, "Dobrobyt", "{VALUE}% chance to keep sword on death"),
    SPETANIA(12, SigilCategory.CIEN, "Spetania", "very small chance to bind jump and elytra for 4s"),
    LOWCY(13, SigilCategory.CIEN, "Lowcy", "{VALUE}% chance for Strength I 30s after a player kill"),
    PODMUCHU(14, SigilCategory.CIEN, "Podmuchu", "{VALUE}% chance to knock back while levitating");

    private final int id;
    private final SigilCategory category;
    private final String displayName;
    private final String descriptionTemplate;

    SigilType(int id, SigilCategory category, String displayName, String descriptionTemplate) {
        this.id = id;
        this.category = category;
        this.displayName = displayName;
        this.descriptionTemplate = descriptionTemplate;
    }

    public int getId() {
        return id;
    }

    public SigilCategory getCategory() {
        return category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescriptionTemplate() {
        return descriptionTemplate;
    }

    public Material getDye() {
        return category.getDye();
    }

    public String formatDescription(double value) {
        return descriptionTemplate.replace("{VALUE}", formatValue(value));
    }

    public static String formatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05D) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    public static SigilType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SigilType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static SigilType fromId(int id) {
        for (SigilType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return null;
    }
}
