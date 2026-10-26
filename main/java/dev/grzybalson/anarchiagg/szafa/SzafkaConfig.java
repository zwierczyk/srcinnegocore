package dev.grzybalson.anarchiagg.szafa;

import dev.grzybalson.anarchiagg.AnarchiaGGPaths;
import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.utils.ConfigItemStackReader;
import dev.grzybalson.utils.HeadUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class SzafkaConfig {
    public static final String ROOT = "szafka";

    private SzafkaConfig() {
    }

    public static YamlConfiguration getConfig(JavaPlugin plugin) {
        ensureDefaults(plugin);
        return AnarchiaGGSettings.getConfig(plugin);
    }

    public static File getFile(JavaPlugin plugin) {
        return AnarchiaGGSettings.getFile(plugin);
    }

    public static String path(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return ROOT;
        }
        return ROOT + "." + relativePath;
    }

    public static String firstSection(FileConfiguration config, String... paths) {
        for (String path : paths) {
            if (config.getConfigurationSection(path) != null) {
                return path;
            }
        }
        return null;
    }

    public static String firstSetPath(FileConfiguration config, String... paths) {
        for (String path : paths) {
            if (config.isSet(path)) {
                return path;
            }
        }
        return null;
    }

    public static void ensureDefaults(JavaPlugin plugin) {
        AnarchiaGGPaths.ensureResource(plugin, "anarchiagg.yml");
        YamlConfiguration config = AnarchiaGGSettings.getConfig(plugin);
        boolean changed = false;

        YamlConfiguration gui = loadLegacyOrResource(plugin, "szafa/szafa.yml");
        changed |= copyMissing(config, path("blocked-regions"), gui, "blocked-regions");
        copyValue(config, path("gui"), gui, "gui");
        changed = true;

        YamlConfiguration pety = loadLegacyOrResource(plugin, "szafa/pety/pety.yml");
        changed |= copyMissing(config, path("default-hp"), pety, "default-hp");
        changed |= copyMissing(config, path("pety"), pety, "pety");
        changed |= copyMissing(config, path("zwoj-pet"), pety, "zwoj-pet");
        if (!config.isSet(path("default-hp"))) {
            config.set(path("default-hp"), 20.0D);
            changed = true;
        }

        YamlConfiguration petItems = loadLegacyOrResource(plugin, "szafa/pety/petyitems.yml");
        boolean copiedPetItems = false;
        if (hasMissingDisplayItems(config, path("pety"))) {
            copiedPetItems = copyMissing(config, path("pet-items"), petItems, "items");
            changed |= copiedPetItems;
        }

        YamlConfiguration kostiumy = loadLegacyOrResource(plugin, "szafa/kostiumy/kostiumy.yml");
        changed |= copyMissing(config, path("kostiumy"), kostiumy, "kostiumy");
        changed |= copyMissing(config, path("zwoj-kostium"), kostiumy, "zwoj-kostium");

        YamlConfiguration kostiumItems = loadLegacyOrResource(plugin, "szafa/kostiumy/kostiumyitems.yml");
        changed |= copyMissing(config, path("kostium-items"), kostiumItems, "items");

        YamlConfiguration papugi = loadLegacyOrResource(plugin, "szafa/papugi/papugi.yml");
        changed |= copyMissing(config, path("roll"), papugi, "roll");
        changed |= copyMissing(config, path("papugi"), papugi, "papugi");
        changed |= copyMissing(config, path("bonuses"), papugi, "bonuses");
        changed |= inlinePetDisplayItems(config);
        changed |= inlineKostiumDisplayItems(config);
        changed |= simplifyDisplayItems(config, path("pety"));
        changed |= simplifyDisplayItems(config, path("kostiumy"));
        changed |= simplifyStoredItems(config, path("kostium-items"));
        if (copiedPetItems && !hasMissingDisplayItems(config, path("pety"))) {
            config.set(path("pet-items"), null);
            changed = true;
        }

        if (changed) {
            AnarchiaGGSettings.save(plugin, config);
        }
    }

    private static boolean copyMissing(YamlConfiguration target, String targetPath, YamlConfiguration source, String sourcePath) {
        if (source == null || !source.isSet(sourcePath)) {
            return false;
        }
        if (!target.isSet(targetPath)) {
            copyValue(target, targetPath, source, sourcePath);
            return true;
        }
        ConfigurationSection sourceSection = source.getConfigurationSection(sourcePath);
        if (sourceSection == null) {
            return false;
        }
        boolean changed = false;
        for (String key : sourceSection.getKeys(false)) {
            String childTargetPath = targetPath + "." + key;
            String childSourcePath = sourcePath + "." + key;
            if (!target.isSet(childTargetPath)) {
                copyValue(target, childTargetPath, source, childSourcePath);
                changed = true;
            }
        }
        return changed;
    }

    private static void copyValue(YamlConfiguration target, String targetPath, YamlConfiguration source, String sourcePath) {
        ConfigurationSection section = source.getConfigurationSection(sourcePath);
        if (section == null) {
            target.set(targetPath, source.get(sourcePath));
            return;
        }
        for (String key : section.getKeys(false)) {
            copyValue(target, targetPath + "." + key, source, sourcePath + "." + key);
        }
    }

    private static boolean copyValue(FileConfiguration target, String targetPath, FileConfiguration source, String sourcePath) {
        if (!source.isSet(sourcePath)) {
            return false;
        }
        ConfigurationSection section = source.getConfigurationSection(sourcePath);
        if (section == null) {
            target.set(targetPath, source.get(sourcePath));
            return true;
        }
        for (String key : section.getKeys(false)) {
            copyValue(target, targetPath + "." + key, source, sourcePath + "." + key);
        }
        return true;
    }

    private static boolean inlinePetDisplayItems(YamlConfiguration config) {
        ConfigurationSection petSection = config.getConfigurationSection(path("pety"));
        ConfigurationSection itemSection = config.getConfigurationSection(path("pet-items"));
        if (petSection == null) {
            return false;
        }

        boolean changed = false;
        for (String key : petSection.getKeys(false)) {
            String petPath = path("pety." + key);
            if (!config.isSet(petPath + ".enabled")) {
                config.set(petPath + ".enabled", true);
                changed = true;
            }

            String displayPath = petPath + ".display-item";
            if (config.isSet(displayPath)) {
                continue;
            }
            if (itemSection == null) {
                continue;
            }
            String itemKey = config.getString(petPath + ".type");
            if (itemKey == null || itemKey.isBlank()) {
                continue;
            }

            ItemStack item = ConfigItemStackReader.getItem(config, path("pet-items." + itemKey));
            Material material = item != null ? item.getType() : Material.matchMaterial(itemKey);
            if (material == null) {
                continue;
            }

            config.set(displayPath + ".material", material.name());
            if (material == Material.PLAYER_HEAD && item != null) {
                String headValue = HeadUtils.getBase64FromHead(item);
                if (headValue == null || headValue.isBlank()) {
                    headValue = findTextureValue(itemSection.getConfigurationSection(itemKey));
                }
                if (headValue != null && !headValue.isBlank()) {
                    config.set(displayPath + ".head-value", headValue);
                }
            }

            String displayName = config.getString(petPath + ".displayname");
            if (displayName != null) {
                config.set(displayPath + ".name", displayName);
            }
            if (config.isList(petPath + ".lore")) {
                List<String> lore = config.getStringList(petPath + ".lore");
                config.set(displayPath + ".lore", lore);
            }
            changed = true;
        }
        return changed;
    }

    private static boolean simplifyDisplayItems(YamlConfiguration config, String sectionPath) {
        ConfigurationSection section = config.getConfigurationSection(sectionPath);
        if (section == null) {
            return false;
        }

        boolean changed = false;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            String entryPath = sectionPath + "." + key;
            String displayPath = entryPath + ".display-item";
            if (!isLegacySerializedItem(config, displayPath)) {
                continue;
            }

            ConfigurationSection legacySection = config.getConfigurationSection(displayPath);
            ItemStack item = ConfigItemStackReader.getItem(config, displayPath);
            if (item == null) {
                continue;
            }

            String headValue = item.getType() == Material.PLAYER_HEAD ? HeadUtils.getBase64FromHead(item) : null;
            if ((headValue == null || headValue.isBlank()) && legacySection != null) {
                headValue = findTextureValue(legacySection);
            }
            String displayName = firstString(config, displayPath + ".name", entryPath + ".displayname", entryPath + ".name");
            List<String> lore = config.isList(displayPath + ".lore")
                ? config.getStringList(displayPath + ".lore")
                : (config.isList(entryPath + ".lore") ? config.getStringList(entryPath + ".lore") : null);
            String color = firstString(config, displayPath + ".color", entryPath + ".color");

            config.set(displayPath, null);
            config.set(displayPath + ".material", item.getType().name());
            if (item.getType() == Material.PLAYER_HEAD && headValue != null && !headValue.isBlank()) {
                config.set(displayPath + ".head-value", headValue);
            }
            if (color != null) {
                config.set(displayPath + ".color", color);
            }
            if (displayName != null) {
                config.set(displayPath + ".name", displayName);
            }
            if (lore != null) {
                config.set(displayPath + ".lore", lore);
            }
            changed = true;
        }
        return changed;
    }

    private static boolean simplifyStoredItems(YamlConfiguration config, String rootPath) {
        ConfigurationSection itemSection = config.getConfigurationSection(rootPath);
        if (itemSection == null) {
            return false;
        }

        boolean changed = false;
        for (String key : new ArrayList<>(itemSection.getKeys(false))) {
            String itemPath = rootPath + "." + key;
            if (!isLegacySerializedItem(config, itemPath)) {
                continue;
            }

            ConfigurationSection legacySection = config.getConfigurationSection(itemPath);
            ItemStack item = ConfigItemStackReader.getItem(config, itemPath);
            if (item == null) {
                continue;
            }

            String headValue = item.getType() == Material.PLAYER_HEAD ? HeadUtils.getBase64FromHead(item) : null;
            if ((headValue == null || headValue.isBlank()) && legacySection != null) {
                headValue = findTextureValue(legacySection);
            }

            config.set(itemPath, null);
            config.set(itemPath + ".material", item.getType().name());
            if (item.getType() == Material.PLAYER_HEAD && headValue != null && !headValue.isBlank()) {
                config.set(itemPath + ".head-value", headValue);
            }
            changed = true;
        }
        return changed;
    }

    private static boolean isLegacySerializedItem(YamlConfiguration config, String itemPath) {
        return config.isSet(itemPath + ".==")
            || config.isSet(itemPath + ".v")
            || config.isSet(itemPath + ".meta")
            || config.isSet(itemPath + ".skull-owner");
    }

    private static boolean hasMissingDisplayItems(YamlConfiguration config, String sectionPath) {
        ConfigurationSection section = config.getConfigurationSection(sectionPath);
        if (section == null) {
            return false;
        }

        for (String key : section.getKeys(false)) {
            if (!config.isSet(sectionPath + "." + key + ".display-item")) {
                return true;
            }
        }
        return false;
    }

    private static String findTextureValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                String found = findTextureValue(section.get(key));
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        if (value instanceof Map<?, ?> map) {
            for (Object nestedValue : map.values()) {
                String found = findTextureValue(nestedValue);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        if (value instanceof Iterable<?> iterable) {
            for (Object nestedValue : iterable) {
                String found = findTextureValue(nestedValue);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        if (!(value instanceof String text)) {
            return null;
        }
        return looksLikeTextureValue(text) ? text : null;
    }

    private static String firstString(YamlConfiguration config, String... paths) {
        for (String path : paths) {
            String value = config.getString(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean looksLikeTextureValue(String value) {
        if (value == null || value.length() < 40 || "13.0.0".equals(value)) {
            return false;
        }
        try {
            String paddedValue = value + "=".repeat((4 - value.length() % 4) % 4);
            String decoded = new String(Base64.getDecoder().decode(paddedValue), StandardCharsets.UTF_8);
            return decoded.contains("textures.minecraft.net") || decoded.contains("\"textures\"");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean inlineKostiumDisplayItems(YamlConfiguration config) {
        ConfigurationSection kostiumSection = config.getConfigurationSection(path("kostiumy"));
        if (kostiumSection == null) {
            return false;
        }

        boolean changed = false;
        for (String key : kostiumSection.getKeys(false)) {
            String kostiumPath = path("kostiumy." + key);
            if (!config.isSet(kostiumPath + ".enabled")) {
                config.set(kostiumPath + ".enabled", true);
                changed = true;
            }

            String displayPath = kostiumPath + ".display-item";
            if (config.isSet(displayPath)) {
                continue;
            }
            String material = config.getString(kostiumPath + ".type");
            if (material == null || material.isBlank()) {
                continue;
            }
            config.set(displayPath + ".material", material);
            String displayName = config.getString(kostiumPath + ".displayname");
            if (displayName != null) {
                config.set(displayPath + ".name", displayName);
            }
            if (config.isList(kostiumPath + ".lore")) {
                config.set(displayPath + ".lore", config.getStringList(kostiumPath + ".lore"));
            }
            String color = config.getString(kostiumPath + ".color");
            if (color != null) {
                config.set(displayPath + ".color", color);
            }
            changed = true;
        }
        return changed;
    }

    private static YamlConfiguration loadLegacyOrResource(JavaPlugin plugin, String relativePath) {
        File file = new File(AnarchiaGGPaths.getRootFolder(plugin), relativePath);
        if (file.exists() && file.length() > 0L) {
            return YamlConfiguration.loadConfiguration(file);
        }

        String resourcePath = AnarchiaGGPaths.ROOT_FOLDER + "/" + relativePath.replace('\\', '/');
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie wczytac domyslnej konfiguracji " + resourcePath + ": " + exception.getMessage());
            return new YamlConfiguration();
        }
    }
}
