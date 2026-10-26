package dev.grzybalson.modules.mslf;

import org.bukkit.Material;

/**
 * Identyfikatory itemow LF Minestar (PDC + GUI).
 */
public enum MslfItemType {
    BOMBA("bomba", Material.FIRE_CHARGE, 1),
    DYNAMIT("dynamit", Material.LIGHT_GRAY_DYE, 1),
    KONICZYNKA("koniczynka", Material.AMETHYST_SHARD, 1),
    ENDERCHEST("enderchest", Material.SOUL_LANTERN, 1),
    MIECZ_PIEKIELNY("miecz_piekielny", Material.NETHERITE_SWORD, 1),
    ESSENCE_2_CRITICAL_DAMAGE("essence_2_critical_damage", Material.PAPER, 1),
    ESSENCE_2_ENCHANTED("essence_2_enchanted", Material.PAPER, 2),
    ESSENCE_2_REGENERATION("essence_2_regeneration", Material.PAPER, 3),
    ESSENCE_2_RESISTANCE("essence_2_resistance", Material.PAPER, 4),
    ESSENCE_2_SPEED("essence_2_speed", Material.PAPER, 5),
    ESSENCE_2_STRENGTH("essence_2_strength", Material.PAPER, 6),
    ESSENCE_2_TRIDENT("essence_2_trident", Material.PAPER, 7),
    KOKOS("kokos", Material.ENCHANTED_GOLDEN_APPLE, 1),
    ZAMEK("zamek", Material.HONEYCOMB, 1),
    KNOCK("knock", Material.GHAST_TEAR, 1),
    PILKA("pilka", Material.SNOWBALL, 1),
    LOD_NA_PATYKU("lod_na_patyku", Material.STICK, 1),
    SMOCZY_TOTEM("smoczy_totem", Material.TOTEM_OF_UNDYING, 1),
    POLE_SMIERCI("pole_smierci", Material.FERMENTED_SPIDER_EYE, 1);

    private final String id;
    private final Material defaultMaterial;
    private final int defaultCmd;

    MslfItemType(String id, Material defaultMaterial, int defaultCmd) {
        this.id = id;
        this.defaultMaterial = defaultMaterial;
        this.defaultCmd = defaultCmd;
    }

    public String id() {
        return id;
    }

    public Material defaultMaterial() {
        return defaultMaterial;
    }

    public int defaultCmd() {
        return defaultCmd;
    }

    public static MslfItemType byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (MslfItemType type : values()) {
            if (type.id.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }

    public boolean consumableUse() {
        return this == BOMBA
            || this == DYNAMIT
            || this == ENDERCHEST
            || this == KOKOS
            || this == PILKA;
    }

    public boolean hasUseCooldown() {
        return switch (this) {
            case BOMBA, DYNAMIT, ENDERCHEST, KOKOS, PILKA,
                 ZAMEK, POLE_SMIERCI, LOD_NA_PATYKU, MIECZ_PIEKIELNY, KONICZYNKA -> true;
            default -> false;
        };
    }
}
