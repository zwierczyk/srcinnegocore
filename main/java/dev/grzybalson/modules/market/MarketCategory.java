package dev.grzybalson.modules.market;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MarketConfig;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;

public enum MarketCategory {
    ARMOR,
    TOOLS,
    EVENT_ITEMS,
    PREMIUM,
    OTHER;

    public static MarketCategory fromItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return OTHER;

        MarketConfig config = HollyCore.getInstance().getConfigManager().getMarketConfig();
        if (config == null) return OTHER;

        if (matchesCategory(item, config.getCategoryArmorItems())) {
            return ARMOR;
        }

        if (matchesCategory(item, config.getCategoryToolsItems())) {
            return TOOLS;
        }

        if (matchesCategory(item, config.getCategoryEventItems())) {
            return EVENT_ITEMS;
        }

        if (matchesCategory(item, config.getCategoryPremiumItems())) {
            return PREMIUM;
        }

        if (config.isPremiumIncludesEnchanted() && item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            return PREMIUM;
        }

        return OTHER;
    }

    private static boolean matchesCategory(ItemStack item, List<String> configuredItems) {
        if (item == null || configuredItems == null || configuredItems.isEmpty()) {
            return false;
        }

        String materialName = item.getType().name();
        String displayName = "";
        String customModelDataStr = "";

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    displayName = ChatColor.stripColor(meta.getDisplayName());
                }
                if (meta.hasCustomModelData()) {
                    customModelDataStr = String.valueOf(meta.getCustomModelData());
                }
            }
        }

        for (String entry : configuredItems) {
            if (entry == null || entry.isBlank()) continue;
            String trimmed = entry.trim();

            if (materialName.equalsIgnoreCase(trimmed)) {
                return true;
            }

            if (!customModelDataStr.isEmpty() && customModelDataStr.equals(trimmed)) {
                return true;
            }

            if (!displayName.isEmpty() && displayName.toLowerCase(Locale.ROOT).contains(trimmed.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
