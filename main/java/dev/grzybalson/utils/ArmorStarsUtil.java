package dev.grzybalson.utils;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ArmorStarsConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ArmorStarsUtil {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final Map<String, CacheEntry> STARS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, String> ICON_DISPLAY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long STARS_CACHE_TTL_MS = 5_000L;

    private record CacheEntry(String value, long timestamp) {}

    private ArmorStarsUtil() {
    }

    public static String calculateStarsPlaceholder(Player player, String params) {
        return calculateStarsPlaceholder(null, player, params);
    }

    public static String calculateStarsPlaceholder(HollyCore plugin, Player player, String params) {
        if (plugin != null && !plugin.isAddonEnabled("armorstars")) {
            return "";
        }
        if (player == null) {
            return "";
        }
        long now = System.currentTimeMillis();
        String paramKey = params != null ? params : "";
        String cacheKey = player.getUniqueId().toString() + ":" + paramKey;
        CacheEntry cached = STARS_CACHE.get(cacheKey);
        if (cached != null && (now - cached.timestamp()) < STARS_CACHE_TTL_MS) {
            return cached.value();
        }

        String computed = computeStarsPlaceholder(plugin, player, paramKey);
        STARS_CACHE.put(cacheKey, new CacheEntry(computed, now));
        return computed;
    }

    public static void invalidateCache(UUID uuid) {
        if (uuid == null) {
            return;
        }
        String prefix = uuid.toString() + ":";
        STARS_CACHE.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String computeStarsPlaceholder(HollyCore plugin, Player player, String params) {
        PlayerInventory inventory = player.getInventory();
        return calculateStarsFromItems(
            plugin,
            inventory.getHelmet(),
            inventory.getChestplate(),
            inventory.getLeggings(),
            inventory.getBoots(),
            findBestWeapon(inventory),
            params
        );
    }

    public static ItemStack findBestWeapon(PlayerInventory inventory) {
        if (inventory == null) {
            return null;
        }
        ItemStack best = null;
        double bestLevel = -1.0D;
        int bestSharpness = -1;
        for (ItemStack item : inventory.getContents()) {
            if (!isWeaponItem(item)) {
                continue;
            }
            double level = getSwordLevel(item);
            int sharpness = getSharpnessLevel(item);
            if (level > bestLevel || (level == bestLevel && sharpness > bestSharpness)) {
                bestLevel = level;
                bestSharpness = sharpness;
                best = item;
            }
        }
        return best;
    }

    public static String calculateStarsFromItems(
            HollyCore plugin,
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack weapon,
            String params
    ) {
        if (plugin != null && !plugin.isAddonEnabled("armorstars")) {
            return "";
        }
        ArmorStarsConfig config = plugin != null && plugin.getConfigManager() != null
            ? plugin.getConfigManager().getArmorStarsConfig()
            : null;

        double swordLevel = getSwordLevel(weapon);
        double armorLevel = getArmorLevel(helmet, chestplate, leggings, boots, config);

        String paramKey = params != null ? params : "";
        String lower = paramKey.toLowerCase(Locale.ROOT);

        if (lower.contains("onlyarmor") || lower.contains("armoronly") || lower.endsWith("_armor") || lower.contains("zbroja")) {
            return formatLevel(armorLevel);
        }
        if (lower.contains("weapon") || lower.contains("sword") || lower.contains("miecz")) {
            return formatLevel(swordLevel);
        }
        if (lower.contains("symbol") || lower.contains("emoji")) {
            return ColorUtils.colorize(config != null ? config.getSwordSymbol() : "\uE004");
        }

        if (lower.contains("icon") || lower.contains("formatted")) {
            return buildIconDisplay(config, swordLevel, armorLevel);
        }

        return buildIconDisplay(config, swordLevel, armorLevel);
    }

    public static String buildIconDisplay(ArmorStarsConfig config, double swordLevel, double armorLevel) {
        String swordSymbol = config != null ? config.getSwordSymbol() : "\uE004";
        String armorSymbol = config != null ? config.getArmorSymbol() : "\uE012";
        String swordFormat = config != null ? config.getSwordFormat() : "&#6CC9FF{symbol}{level}";
        String armorFormat = config != null ? config.getArmorFormat() : "&#98FF6C{symbol}{level}";
        String iconFormat = config != null ? config.getIconFormat() : "{sword} {armor}";

        String cacheKey = swordSymbol + '|' + armorSymbol + '|' + swordFormat + '|' + armorFormat + '|'
            + iconFormat + '|' + formatLevel(swordLevel) + '|' + formatLevel(armorLevel);
        String cached = ICON_DISPLAY_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String swordPart = ColorUtils.colorize(swordFormat
            .replace("{symbol}", swordSymbol)
            .replace("{level}", formatLevel(swordLevel)));
        String armorPart = ColorUtils.colorize(armorFormat
            .replace("{symbol}", armorSymbol)
            .replace("{level}", formatLevel(armorLevel)));

        String rendered = ColorUtils.colorize(iconFormat
            .replace("{sword}", swordPart)
            .replace("{armor}", armorPart));
        if (ICON_DISPLAY_CACHE.size() > 4096) {
            ICON_DISPLAY_CACHE.clear();
        }
        ICON_DISPLAY_CACHE.put(cacheKey, rendered);
        return rendered;
    }

    public static double getSwordLevel(ItemStack item) {
        int sharpness = getSharpnessLevel(item);
        if (sharpness < 3) {
            return 0.0D;
        }
        return (sharpness - 1) / 2.0D;
    }

    public static double getArmorLevel(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots, ArmorStarsConfig config) {
        double factor = config != null ? config.getArmorPieceFactor() : 0.25D;
        return getPieceArmorContribution(helmet, factor)
            + getPieceArmorContribution(chestplate, factor)
            + getPieceArmorContribution(leggings, factor)
            + getPieceArmorContribution(boots, factor);
    }

    public static double getArmorLevel(PlayerInventory inventory) {
        if (inventory == null) {
            return 0.0D;
        }
        return getArmorLevel(
            inventory.getHelmet(),
            inventory.getChestplate(),
            inventory.getLeggings(),
            inventory.getBoots(),
            null
        );
    }

    private static double getPieceArmorContribution(ItemStack item, double factor) {
        int upgradeLevel = getArmorUpgradeLevel(item);
        if (upgradeLevel <= 0) {
            return 0.0D;
        }
        return upgradeLevel * factor;
    }

    /** Poziom zbroi z ItemUpgrader SkyPVP: protection / 2. */
    public static int getArmorUpgradeLevel(ItemStack item) {
        int protection = getProtectionLevel(item);
        if (protection <= 0) {
            return 0;
        }
        return protection / 2;
    }

    public static int getProtectionLevel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }
        int maxLevel = 0;
        try {
            int lvl = item.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
            if (lvl > maxLevel) {
                maxLevel = lvl;
            }
        } catch (Throwable ignored) {
        }

        if (maxLevel == 0 && item.hasItemMeta()) {
            try {
                Map<Enchantment, Integer> map = item.getEnchantments();
                for (Map.Entry<Enchantment, Integer> entry : map.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    NamespacedKey key = entry.getKey().getKey();
                    if (key != null && "protection".equalsIgnoreCase(key.getKey())) {
                        int lvl = entry.getValue() != null ? entry.getValue() : 0;
                        if (lvl > maxLevel) {
                            maxLevel = lvl;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return maxLevel;
    }

    public static int getSharpnessLevel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }
        int maxLevel = 0;
        try {
            int lvl = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
            if (lvl > maxLevel) {
                maxLevel = lvl;
            }
        } catch (Throwable ignored) {
        }

        if (maxLevel == 0 && item.hasItemMeta()) {
            try {
                Map<Enchantment, Integer> map = item.getEnchantments();
                for (Map.Entry<Enchantment, Integer> entry : map.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    NamespacedKey key = entry.getKey().getKey();
                    if (key != null && ("sharpness".equalsIgnoreCase(key.getKey()) || "damage_all".equalsIgnoreCase(key.getKey()))) {
                        int lvl = entry.getValue() != null ? entry.getValue() : 0;
                        if (lvl > maxLevel) {
                            maxLevel = lvl;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return maxLevel;
    }

    public static boolean isWeaponItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        Material type = item.getType();
        String typeName = type.name();
        if (typeName.endsWith("_SWORD") || typeName.endsWith("_AXE") || type == Material.TRIDENT) {
            return true;
        }
        return getSharpnessLevel(item) > 0;
    }

    public static String formatLevel(double value) {
        if (value <= 0.0D) {
            return "0";
        }
        return DECIMAL_FORMAT.format(value);
    }

    public static String formatNumber(double value) {
        return formatLevel(value);
    }
}
