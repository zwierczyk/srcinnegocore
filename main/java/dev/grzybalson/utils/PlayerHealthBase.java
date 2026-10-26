package dev.grzybalson.utils;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.customitems.items.AnarchiczneSerce;
import dev.grzybalson.anarchiagg.szafa.BoostHandler;
import dev.grzybalson.config.configs.HeartsConfig;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * Jedna reguła bazy HP:
 * - bez addonu hearts → 10 serc (20 HP)
 * - addon hearts → 30 serc (60 HP) + Anarchiczne serce (max +10 → 40 serc)
 * Bonus kostiumu (ExtraHearts) jest osobnym modifierem — zawsze najpierw reset bazy.
 */
public final class PlayerHealthBase {

    private PlayerHealthBase() {
    }

    public static boolean isHeartsAddonActive(HollyCore plugin) {
        if (plugin == null || !plugin.isAddonEnabled("hearts")) {
            return false;
        }
        HeartsConfig config = plugin.getConfigManager() != null
            ? plugin.getConfigManager().getHeartsConfig()
            : null;
        return config != null && config.isEnabled();
    }

    /** Bazowe max HP w punktach życia (2 HP = 1 serce). */
    public static double resolveBaseMaxHealth(HollyCore plugin, Player player) {
        if (isHeartsAddonActive(plugin)) {
            HeartsConfig config = plugin.getConfigManager().getHeartsConfig();
            double base = config.getMaxHealth() > 0.0D ? config.getMaxHealth() : 60.0D;
            int extra = AnarchiczneSerce.getExtraHearts(plugin, player);
            return base + (extra * 2.0D);
        }
        return 20.0D;
    }

    /**
     * Resetuje bazę HP i zdejmuje modifiery kostiumu.
     * Kostium trzeba potem nałożyć osobno ({@link BoostHandler#refreshHearts}).
     */
    public static void resetBaseMaxHealth(HollyCore plugin, Player player, boolean clampCurrentHealth) {
        if (player == null || !player.isOnline()) {
            return;
        }
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) {
            return;
        }
        BoostHandler.stripCostumeHeartModifiers(attr);
        double base = resolveBaseMaxHealth(plugin, player);
        if (!Double.isFinite(base) || base < 1.0D) {
            base = 20.0D;
        }
        attr.setBaseValue(base);
        if (clampCurrentHealth && player.getHealth() > attr.getValue()) {
            player.setHealth(Math.max(0.1D, attr.getValue()));
        }
    }

    public static void resetBaseAndHeal(HollyCore plugin, Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        resetBaseMaxHealth(plugin, player, false);
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            player.setHealth(Math.max(0.1D, attr.getValue()));
        }
    }
}
