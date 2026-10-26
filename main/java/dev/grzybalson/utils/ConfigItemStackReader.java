package dev.grzybalson.utils;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigItemStackReader {
    private ConfigItemStackReader() {
    }

    public static ItemStack getItem(FileConfiguration config, String path) {
        if (config == null || path == null || path.isBlank()) {
            return null;
        }

        ItemStack serialized = null;
        try {
            serialized = config.getItemStack(path);
        } catch (RuntimeException ignored) {
        }

        ConfigurationSection section = config.getConfigurationSection(path);
        if (serialized != null) {
            applyTextOverrides(serialized, section);
            return serialized;
        }

        return buildSimpleItem(section);
    }

    public static ItemStack fromSection(ConfigurationSection section) {
        return buildSimpleItem(section);
    }

    private static ItemStack buildSimpleItem(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String materialName = firstString(section, "material", "type", "item");
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material == null) {
            return null;
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, amount);

        if (material == Material.PLAYER_HEAD) {
            String texture = firstString(section, "head-value", "headValue", "head-texture", "texture", "skull-texture");
            if (texture != null && !texture.isBlank()) {
                item = HeadUtils.setHeadTextureLikeShop(item, texture);
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String displayName = readDisplayName(section);
        if (displayName != null) {
            meta.displayName(ColorUtils.colorizeComponent(displayName));
        }

        List<String> lore = readLore(section);
        if (lore != null) {
            List<Component> colored = colorComponentList(lore);
            if (!colored.isEmpty()) {
                meta.lore(colored);
            }
        }

        Integer customModelData = firstInt(section, "custom-model-data", "customModelData", "model-data", "model");
        if (customModelData != null && customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        Boolean unbreakable = firstBoolean(section, "unbreakable", "Unbreakable");
        if (unbreakable != null) {
            meta.setUnbreakable(unbreakable);
        }

        Integer damage = firstInt(section, "damage", "Damage");
        if (damage != null && meta instanceof Damageable damageable) {
            damageable.setDamage(Math.max(0, damage));
        }

        applyFlags(meta, section);
        applyLeatherColor(meta, section);
        item.setItemMeta(meta);
        applyEnchants(item, section);
        return item;
    }

    private static void applyTextOverrides(ItemStack item, ConfigurationSection section) {
        if (item == null || section == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String displayName = readDisplayName(section);
        if (displayName != null) {
            meta.displayName(ColorUtils.colorizeComponent(displayName));
        }

        List<String> lore = readLore(section);
        if (lore != null) {
            List<Component> colored = colorComponentList(lore);
            if (!colored.isEmpty()) {
                meta.lore(colored);
            }
        }

        item.setItemMeta(meta);
    }

    private static String readDisplayName(ConfigurationSection section) {
        String rootValue = firstString(section, "displayName", "DisplayName", "display-name", "displayname", "display", "name");
        if (rootValue != null) {
            return rootValue;
        }

        ConfigurationSection meta = section.getConfigurationSection("meta");
        String metaValue = meta == null ? null : firstString(meta, "displayName", "DisplayName", "display-name", "displayname", "display", "name");
        return looksSerializedComponent(metaValue) ? null : metaValue;
    }

    private static List<String> readLore(ConfigurationSection section) {
        List<String> rootLore = firstStringList(section, "lore");
        if (rootLore != null) {
            return rootLore;
        }

        ConfigurationSection meta = section.getConfigurationSection("meta");
        List<String> metaLore = meta == null ? null : firstStringList(meta, "lore");
        if (metaLore == null || metaLore.stream().anyMatch(ConfigItemStackReader::looksSerializedComponent)) {
            return null;
        }
        return metaLore;
    }

    private static void applyFlags(ItemMeta meta, ConfigurationSection section) {
        List<String> flags = firstStringList(section, "item-flags", "itemFlags", "flags", "ItemFlags");
        if (flags == null) {
            ConfigurationSection metaSection = section.getConfigurationSection("meta");
            flags = metaSection == null ? null : firstStringList(metaSection, "item-flags", "itemFlags", "flags", "ItemFlags");
        }
        if (flags == null) {
            return;
        }

        for (String rawFlag : flags) {
            if (rawFlag == null || rawFlag.isBlank()) {
                continue;
            }
            try {
                meta.addItemFlags(ItemFlag.valueOf(rawFlag.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void applyLeatherColor(ItemMeta meta, ConfigurationSection section) {
        if (!(meta instanceof LeatherArmorMeta leatherArmorMeta)) {
            return;
        }

        String rawColor = firstString(section, "color", "leather-color", "leatherColor");
        Color color = parseColor(rawColor);
        if (color != null) {
            leatherArmorMeta.setColor(color);
        }
    }

    private static void applyEnchants(ItemStack item, ConfigurationSection section) {
        ConfigurationSection enchants = section.getConfigurationSection("enchants");
        if (enchants == null) {
            enchants = section.getConfigurationSection("enchantments");
        }
        if (enchants == null) {
            return;
        }

        for (String key : enchants.getKeys(false)) {
            Enchantment enchantment = resolveEnchantment(key);
            if (enchantment == null) {
                continue;
            }
            item.addUnsafeEnchantment(enchantment, enchants.getInt(key, 1));
        }
    }

    private static Enchantment resolveEnchantment(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }

        Enchantment byName = Enchantment.getByName(rawKey.trim().toUpperCase(Locale.ROOT));
        if (byName != null) {
            return byName;
        }

        String normalized = rawKey.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        NamespacedKey namespacedKey = normalized.contains(":")
            ? NamespacedKey.fromString(normalized)
            : NamespacedKey.minecraft(normalized);
        return namespacedKey == null ? null : Enchantment.getByKey(namespacedKey);
    }

    private static Color parseColor(String rawColor) {
        if (rawColor == null || rawColor.isBlank()) {
            return null;
        }

        String value = rawColor.trim();
        if (value.startsWith("#") && value.length() == 7) {
            try {
                return Color.fromRGB(
                    Integer.parseInt(value.substring(1, 3), 16),
                    Integer.parseInt(value.substring(3, 5), 16),
                    Integer.parseInt(value.substring(5, 7), 16)
                );
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String firstString(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (!section.contains(path)) {
                continue;
            }
            String value = section.getString(path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer firstInt(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (!section.contains(path)) {
                continue;
            }
            if (section.isInt(path)) {
                return section.getInt(path);
            }
            String value = section.getString(path);
            if (value == null) {
                continue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Boolean firstBoolean(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (!section.contains(path)) {
                continue;
            }
            if (section.isBoolean(path)) {
                return section.getBoolean(path);
            }
            String value = section.getString(path);
            if (value != null) {
                return Boolean.parseBoolean(value);
            }
        }
        return null;
    }

    private static List<String> firstStringList(ConfigurationSection section, String... paths) {
        for (String path : paths) {
            if (!section.contains(path)) {
                continue;
            }
            if (section.isList(path)) {
                return section.getStringList(path);
            }
            String value = section.getString(path);
            if (value != null) {
                return List.of(value.split("\\r?\\n", -1));
            }
        }
        return null;
    }

    private static List<String> colorList(List<String> lines) {
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(color(line == null ? "" : line));
        }
        return colored;
    }

    private static List<Component> colorComponentList(List<String> lines) {
        List<Component> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (ColorUtils.isBlankLoreLine(line)) {
                continue;
            }
            colored.addAll(ColorUtils.colorizeLore(line));
        }
        return colored;
    }

    private static String color(String text) {
        return ColorUtils.colorize(text);
    }

    private static boolean looksSerializedComponent(String value) {
        if (value == null) {
            return false;
        }

        String trimmed = value.trim();
        return trimmed.startsWith("{")
            || trimmed.startsWith("[")
            || trimmed.equals("\"\"")
            || trimmed.startsWith("{\"text\"")
            || trimmed.startsWith("{\"extra\"");
    }
}
