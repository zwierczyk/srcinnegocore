package dev.grzybalson.modules.upgrade;

import org.bukkit.Material;

import java.util.Locale;

public enum UpgradeType {
    // Mnożniki jak w HollyService /armorcreate: zbroja×1, miecz×4, kilof×4, nożyce×8
    SWORD("Broń", Material.NETHERITE_SWORD, 30, 4.0D, 1),
    HOE("Motyka", Material.NETHERITE_HOE, 31, 2.0D, 1),
    HELMET("Hełm", Material.NETHERITE_HELMET, 20, 1.0D, 1),
    CHEST_PLATE("Napierśnik", Material.NETHERITE_CHESTPLATE, 21, 1.0D, 1),
    LEGGINGS("Spodnie", Material.NETHERITE_LEGGINGS, 22, 1.0D, 1),
    ELYTRA("Elytra", Material.ELYTRA, 24, 1.0D, 1),
    BOOTS("Buty", Material.NETHERITE_BOOTS, 23, 1.0D, 1),
    PICKAXE("Kilof", Material.NETHERITE_PICKAXE, 32, 4.0D, 1),
    SHEARS("Nożyce", Material.SHEARS, 29, 8.0D, 1),
    AXE("Siekiera", Material.NETHERITE_AXE, 33, 2.0D, 1);

    private final String defaultPrefix;
    private final Material defaultMaterial;
    private final int defaultSlot;
    private final double defaultPriceMultiplier;
    private final int minimumLevel;

    UpgradeType(String defaultPrefix, Material defaultMaterial, int defaultSlot, double defaultPriceMultiplier, int minimumLevel) {
        this.defaultPrefix = defaultPrefix;
        this.defaultMaterial = defaultMaterial;
        this.defaultSlot = defaultSlot;
        this.defaultPriceMultiplier = defaultPriceMultiplier;
        this.minimumLevel = minimumLevel;
    }

    public String getDefaultPrefix() {
        return defaultPrefix;
    }

    public Material getDefaultMaterial() {
        return defaultMaterial;
    }

    public int getDefaultSlot() {
        return defaultSlot;
    }

    public double getDefaultPriceMultiplier() {
        return defaultPriceMultiplier;
    }

    /** @deprecated użyj {@link #getDefaultPriceMultiplier()} */
    @Deprecated
    public boolean isPriceDoubled() {
        return defaultPriceMultiplier >= 2.0D;
    }

    public int getMinimumLevel() {
        return minimumLevel;
    }

    public String getConfigKey() {
        return name();
    }

    public static UpgradeType fromArgument(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.toUpperCase(Locale.ROOT)
            .replace("-", "_")
            .replace("CHESTPLATE", "CHEST_PLATE")
            .replace("CHEST_PLATE", "CHEST_PLATE");

        for (UpgradeType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return switch (normalized) {
            case "MIECZ", "BRON", "BROŃ" -> SWORD;
            case "HELM" -> HELMET;
            case "KLATA", "ZBROJA", "NAPIERSNIK", "NAPIERŚNIK" -> CHEST_PLATE;
            case "SPODNIE" -> LEGGINGS;
            case "BUTY" -> BOOTS;
            case "KILOF" -> PICKAXE;
            case "HOE", "MOTYKA", "MOTYKI" -> HOE;
            case "NOZYCE", "NOŻYCE", "NOZYCZKI", "NOŻYCZKI", "LOPATA", "ŁOPATA", "SHOVEL" -> SHEARS;
            case "AXE", "SIEKIERA", "SIEKIERY", "TOPOR", "TOPÓR" -> AXE;
            default -> null;
        };
    }
}
