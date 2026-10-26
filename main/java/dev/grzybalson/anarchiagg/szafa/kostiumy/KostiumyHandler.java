package dev.grzybalson.anarchiagg.szafa.kostiumy;

import dev.grzybalson.anarchiagg.szafa.SzafkaConfig;
import dev.grzybalson.utils.ConfigItemStackReader;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class KostiumyHandler {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");

    private final JavaPlugin plugin;
    private final KostiumyDatabase database;
    private final Map<String, KostiumData> kostiumy = new HashMap<>();
    private final Map<String, ItemStack> kostiumItems = new HashMap<>();
    private File configFile;
    private FileConfiguration config;
    private String kostiumyRoot;
    private String itemsRoot;
    private FileConfiguration customConfig;
    private final File customFile;

    public KostiumyHandler(JavaPlugin plugin, KostiumyDatabase database) {
        this(plugin, database, null, null);
    }

    /**
     * Wariant dla dodatkow (np. oghub-szafka), ktore trzymaja kostiumy w wlasnym pliku
     * i nie moga dotykac konfiguracji anarchiagg.
     */
    public KostiumyHandler(JavaPlugin plugin, KostiumyDatabase database, FileConfiguration customConfig, File customFile) {
        this.plugin = plugin;
        this.database = database;
        this.customConfig = customConfig;
        this.customFile = customFile;
        setupFiles();
        loadItems();
        loadKostiumy();
        startExpirationChecker();
    }

    public void reload() {
        if (this.customConfig != null && this.customFile != null && this.customFile.exists()) {
            // Wczytujemy w miejscu, zeby GUI i inne klasy trzymajace ten sam obiekt widzialy nowe wartosci.
            try {
                this.customConfig.load(this.customFile);
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
                this.plugin.getLogger().log(Level.WARNING, "Nie udalo sie przeladowac " + this.customFile.getName(), exception);
            }
        }
        setupFiles();
        loadItems();
        loadKostiumy();
    }

    private void setupFiles() {
        if (this.customConfig != null) {
            this.configFile = this.customFile;
            this.config = this.customConfig;
            this.kostiumyRoot = SzafkaConfig.firstSection(this.config, SzafkaConfig.path("kostiumy"), "kostiumy");
            this.itemsRoot = SzafkaConfig.firstSection(this.config, SzafkaConfig.path("kostium-items"), "kostium-items");
            return;
        }
        this.configFile = SzafkaConfig.getFile(this.plugin);
        this.config = SzafkaConfig.getConfig(this.plugin);
        this.kostiumyRoot = SzafkaConfig.firstSection(this.config, SzafkaConfig.path("kostiumy"), "kostiumy");
        this.itemsRoot = SzafkaConfig.firstSection(this.config, SzafkaConfig.path("kostium-items"), "items");
    }

    private void loadKostiumy() {
        this.kostiumy.clear();
        ConfigurationSection section = this.kostiumyRoot == null ? null : this.config.getConfigurationSection(this.kostiumyRoot);
        if (section == null) {
            return;
        }

        boolean configUpdated = false;
        for (String key : section.getKeys(false)) {
            String path = this.kostiumyRoot + "." + key;
            if (!this.config.getBoolean(path + ".enabled", true)) {
                continue;
            }

            ItemStack displayItem = ConfigItemStackReader.getItem(this.config, path + ".display-item");
            Material type = this.config.isSet(path + ".type") ? Material.matchMaterial(this.config.getString(path + ".type", "STONE")) : null;
            if (type == null && displayItem != null) {
                type = displayItem.getType();
            }
            if (type == null) {
                type = Material.STONE;
            }

            String headValue = firstString(path + ".display-item.head-value", path + ".display-item.headValue", path + ".head-value", path + ".headValue");
            if ((key.equalsIgnoreCase("grinch") || key.equalsIgnoreCase("grincz")) && (headValue == null || headValue.isBlank())) {
                headValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTgzOGQ2MzBkNDk2ZjIxMjg1ZDQ5MDk5YzNmZDhkOTIxNDQyMTcyYTUxNjBjMWI1ZDQyYTUwODBmOWNmODE4YSJ9fX0=";
            }
            if (type == Material.PLAYER_HEAD && headValue != null && !headValue.isBlank()) {
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                displayItem = dev.grzybalson.utils.HeadUtils.setHeadTextureLikeShop(skull, headValue);
            }

            String displayName = firstString(path + ".display-item.name", path + ".displayname", path + ".name");
            List<String> lore = firstStringList(path + ".display-item.lore", path + ".lore");
            String color = firstString(path + ".display-item.color", path + ".color");
            if (shouldForcePresidentColor(key, displayName)) {
                color = "#000000";
                if (!"#000000".equalsIgnoreCase(this.config.getString(path + ".color", ""))) {
                    this.config.set(path + ".color", color);
                    configUpdated = true;
                }
            }

            BoostsData boosts = readBoosts(path);
            Map<String, String> armor = readArmor(path);
            this.kostiumy.put(key, new KostiumData(type, displayName, lore, boosts, armor, color, displayItem));
        }

        if (this.kostiumy.containsKey("grinch") && !this.kostiumy.containsKey("grincz")) {
            this.kostiumy.put("grincz", this.kostiumy.get("grinch"));
        } else if (this.kostiumy.containsKey("grincz") && !this.kostiumy.containsKey("grinch")) {
            this.kostiumy.put("grinch", this.kostiumy.get("grincz"));
        }

        if (configUpdated) {
            try {
                this.config.save(this.configFile);
            } catch (IOException exception) {
                this.plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac zaktualizowanego koloru kostiumu prezydenta.", exception);
            }
        }

        String zwojRoot = SzafkaConfig.firstSetPath(this.config, SzafkaConfig.path("zwoj-kostium"), "zwoj-kostium");
        String zwojName = zwojRoot == null
                ? "&6&lZwoj przedluzenia"
                : this.config.getString(zwojRoot + ".name", "&6&lZwoj przedluzenia");
        Material zwojType = Material.matchMaterial(zwojRoot == null
                ? "FLOWER_BANNER_PATTERN"
                : this.config.getString(zwojRoot + ".type", "FLOWER_BANNER_PATTERN"));
        if (zwojType == null) {
            zwojType = Material.FLOWER_BANNER_PATTERN;
        }
        List<String> zwojLore = zwojRoot == null ? List.of() : this.config.getStringList(zwojRoot + ".lore");
        this.kostiumy.put("zwoj-kostium", new KostiumData(zwojType, zwojName, zwojLore, null, Map.of(), "#FFFFFF", new ItemStack(zwojType)));
    }

    private BoostsData readBoosts(String path) {
        ConfigurationSection boostsSection = firstConfigSection(path + ".boosts", path + ".bonuses");
        if (boostsSection == null) {
            return null;
        }
        return new BoostsData(
                firstDouble(boostsSection, 0.0D, "damage", "extra-damage"),
                firstDouble(boostsSection, 0.0D, "damage-reduction"),
                firstDouble(boostsSection, 0.0D, "fall-damage-reduction"),
                firstInt(boostsSection, 0, "hearts", "extra-hearts"),
                firstBoolean(boostsSection, false, "negative-effects-disable"),
                firstDouble(boostsSection, 0.0D, "rob-money"),
                firstStringList(boostsSection, "effects"),
                firstStringList(boostsSection, "totem-pop-effects"),
                firstDouble(boostsSection, 0.0D, "dripstone-damage-reduction"),
                firstBoolean(boostsSection, false, "no-hunger"),
                firstBoolean(boostsSection, false, "double-drop"),
                firstBoolean(boostsSection, false, "see-health"),
                firstBoolean(boostsSection, false, "mim"),
                firstBoolean(boostsSection, false, "zakazny"),
                firstBoolean(boostsSection, false, "nurek"),
                firstBoolean(boostsSection, false, "wampir"),
                firstBoolean(boostsSection, false, "mikolaj"),
                firstBoolean(boostsSection, false, "cobweb-clear", "uczen", "mim-cobweb"),
                firstDouble(boostsSection, 0.0D, "damage-percent"),
                firstDouble(boostsSection, 1.0D, "dropkeys-multiplier"),
                firstDouble(boostsSection, 0.0D, "extra-hp", "extraHp"),
                firstDouble(boostsSection, 0.0D, "cobweb-speed-percent", "cobwebSpeedPercent"),
                firstBoolean(boostsSection, false, "ninja-freeze", "ninja"),
                firstBoolean(boostsSection, false, "goat-ram", "goat"),
                firstBoolean(boostsSection, false, "hidden-weakness", "hiddenWeakness")
        );
    }

    private Map<String, String> readArmor(String path) {
        Map<String, String> armor = new HashMap<>();
        ConfigurationSection armorSection = this.config.getConfigurationSection(path + ".armor");
        if (armorSection != null) {
            armor.put("helmet", armorSection.getString("helmet"));
            armor.put("chestplate", armorSection.getString("chestplate"));
            armor.put("leggings", armorSection.getString("leggings"));
            armor.put("boots", armorSection.getString("boots"));
        }
        return armor;
    }

    private void loadItems() {
        this.kostiumItems.clear();
        ConfigurationSection section = this.itemsRoot == null ? null : this.config.getConfigurationSection(this.itemsRoot);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ItemStack item = ConfigItemStackReader.getItem(this.config, this.itemsRoot + "." + key);
            if (item != null) {
                this.kostiumItems.put(key, item);
            }
        }
    }

    public void saveItem(String name, ItemStack item) {
        String root = this.itemsRoot != null ? this.itemsRoot : SzafkaConfig.path("kostium-items");
        this.config.set(root + "." + name, item);
        try {
            this.config.save(this.configFile);
            this.itemsRoot = root;
            loadItems();
            KostiumyArmorHandler armorHandler = KostiumyArmorHandler.getInstance();
            if (armorHandler != null) {
                armorHandler.clearVisualCache();
            }
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Nie udalo sie zapisac kostium-items do anarchiagg/anarchiagg.yml", exception);
        }
    }

    private void startExpirationChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    String activeKostiumKey = database.getActiveKostium(player.getUniqueId());
                    if (activeKostiumKey == null) {
                        continue;
                    }
                    String expirationDateString = database.getKostiumExpiration(player.getUniqueId());
                    if (expirationDateString == null 
                            || expirationDateString.isBlank() 
                            || expirationDateString.equalsIgnoreCase("NA ZAWSZE") 
                            || expirationDateString.equalsIgnoreCase("Brak")
                            || expirationDateString.equalsIgnoreCase("Nieokreślony")
                            || expirationDateString.equalsIgnoreCase("null")
                            || expirationDateString.startsWith("00:00:00")) {
                        continue;
                    }
                    try {
                        LocalDateTime expirationTime = LocalDateTime.parse(expirationDateString.trim(), DATE_FORMATTER);
                        if (!now.isAfter(expirationTime)) {
                            continue;
                        }
                        database.removeActiveKostium(player.getUniqueId());
                        KostiumyArmorHandler armorHandler = KostiumyArmorHandler.getInstance();
                        if (armorHandler != null) {
                            armorHandler.removeArmor(player);
                        }
                        player.sendMessage(ColorUtils.error("Twój aktywny kostium (" + activeKostiumKey + ") wygasł i został zdjęty."));
                    } catch (DateTimeParseException exception) {
                        plugin.getLogger().log(Level.WARNING, "Blad parsowania daty wygasniecia dla gracza " + player.getName() + ": " + expirationDateString, exception);
                    } catch (Exception exception) {
                        plugin.getLogger().log(Level.SEVERE, "Nieznany blad w ExpirationChecker dla gracza " + player.getName(), exception);
                    }
                }
            }
        }.runTaskTimer(this.plugin, 40L, 200L);
    }

    public ItemStack getKostiumItem(String itemKey) {
        ItemStack item = this.kostiumItems.get(itemKey);
        return item != null ? item.clone() : null;
    }

    public static boolean shouldForcePresidentColor(String key, String displayName) {
        return containsPresidentKeyword(key) || containsPresidentKeyword(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', displayName == null ? "" : displayName)));
    }

    public static String getEffectiveColor(String key, String displayName, String color) {
        return shouldForcePresidentColor(key, displayName) ? "#000000" : color;
    }

    private static boolean containsPresidentKeyword(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.contains("prezydent") || normalized.contains("president");
    }

    private ConfigurationSection firstConfigSection(String... paths) {
        for (String path : paths) {
            ConfigurationSection section = this.config.getConfigurationSection(path);
            if (section != null) {
                return section;
            }
        }
        return null;
    }

    private String firstString(String... paths) {
        for (String path : paths) {
            if (!this.config.isSet(path)) {
                continue;
            }
            String value = this.config.getString(path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<String> firstStringList(String... paths) {
        for (String path : paths) {
            if (this.config.isList(path)) {
                return this.config.getStringList(path);
            }
        }
        return List.of();
    }

    private static double firstDouble(ConfigurationSection section, double fallback, String... keys) {
        for (String key : keys) {
            if (section.isSet(key)) {
                return section.getDouble(key, fallback);
            }
        }
        return fallback;
    }

    private static int firstInt(ConfigurationSection section, int fallback, String... keys) {
        for (String key : keys) {
            if (section.isSet(key)) {
                return section.getInt(key, fallback);
            }
        }
        return fallback;
    }

    private static boolean firstBoolean(ConfigurationSection section, boolean fallback, String... keys) {
        for (String key : keys) {
            if (section.isSet(key)) {
                return section.getBoolean(key, fallback);
            }
        }
        return fallback;
    }

    private static List<String> firstStringList(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.isList(key)) {
                return section.getStringList(key);
            }
        }
        return List.of();
    }

    public Map<String, KostiumData> getKostiumy() {
        return this.kostiumy;
    }

    public KostiumData getKostiumData(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        KostiumData data = this.kostiumy.get(rawKey);
        if (data != null) {
            return data;
        }
        for (Map.Entry<String, KostiumData> entry : this.kostiumy.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(rawKey)) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, KostiumData> entry : this.kostiumy.entrySet()) {
            if (entry.getValue().displayName() != null && entry.getValue().displayName().equalsIgnoreCase(rawKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public KostiumyDatabase getDatabase() {
        return this.database;
    }

    public record BoostsData(double damage, double damageReduction, double fallDamageReduction, int hearts,
                             boolean negativeEffectsDisable, double robMoney, List<String> effects,
                             List<String> totemPopEffects, double dripstoneDamageReduction, boolean noHunger,
                             boolean doubleDrop, boolean seeHealth, boolean sneakBoost, boolean zakazny,
                             boolean nurek, boolean wampir, boolean mikolaj, boolean cobwebClear,
                             double damagePercent, double dropKeysMultiplier, double extraHp,
                             double cobwebSpeedPercent, boolean ninjaFreeze, boolean goatRam,
                             boolean hiddenWeakness) {
    }

    public record KostiumData(Material type, String displayName, List<String> lore, BoostsData boosts,
                              Map<String, String> armor, String color, ItemStack displayItem) {
    }
}
