package dev.grzybalson.anarchiagg.szafa.pety;

import dev.grzybalson.anarchiagg.szafa.SzafkaConfig;
import dev.grzybalson.utils.ConfigItemStackReader;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class PetyHandler {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");

    private final JavaPlugin plugin;
    private final PetyDatabase database;
    private final Map<String, PetData> pety = new HashMap<>();
    private final Map<String, ItemStack> petItems = new HashMap<>();
    private File configFile;
    private FileConfiguration config;
    private String petyRoot;
    private String itemsRoot;
    private double defaultMaxHealth = 20.0;
    private final FileConfiguration customConfig;
    private final File customFile;

    public PetyHandler(JavaPlugin plugin, PetyDatabase database) {
        this(plugin, database, null, null);
    }

    public PetyHandler(JavaPlugin plugin, PetyDatabase database, FileConfiguration customConfig, File customFile) {
        this.plugin = plugin;
        this.database = database;
        this.customConfig = customConfig;
        this.customFile = customFile;
        setupFiles();
        loadItems();
        loadPety();
        loadSettings();
        startExpirationChecker();
    }

    public void reload() {
        if (this.customConfig != null && this.customFile != null && this.customFile.exists()) {
            try {
                this.customConfig.load(this.customFile);
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
                this.plugin.getLogger().log(Level.WARNING, "Nie udalo sie przeladowac " + this.customFile.getName(), exception);
            }
        }
        setupFiles();
        loadItems();
        loadPety();
        loadSettings();
    }

    private void setupFiles() {
        if (this.customConfig != null) {
            this.configFile = this.customFile;
            this.config = this.customConfig;
            this.petyRoot = SzafkaConfig.firstSection(this.config, SzafkaConfig.path("pety"), "pety");
            this.itemsRoot = SzafkaConfig.firstSection(this.config, SzafkaConfig.path("pet-items"), "pet-items", "items");
            return;
        }
        this.configFile = SzafkaConfig.getFile(this.plugin);
        this.config = SzafkaConfig.getConfig(this.plugin);
        this.petyRoot = SzafkaConfig.firstSection(this.config, SzafkaConfig.path("pety"), "pety");
        this.itemsRoot = SzafkaConfig.firstSection(this.config, SzafkaConfig.path("pet-items"), "items");
    }

    private void loadSettings() {
        String defaultHpPath = SzafkaConfig.firstSetPath(this.config, SzafkaConfig.path("default-hp"), "default-hp");
        if (defaultHpPath == null) {
            defaultHpPath = SzafkaConfig.path("default-hp");
            this.config.set(defaultHpPath, 20.0D);
            try {
                this.config.save(this.configFile);
            } catch (IOException exception) {
                this.plugin.getLogger().log(Level.SEVERE, "Nie udalo sie zapisac default-hp do anarchiagg/anarchiagg.yml", exception);
            }
        }
        this.defaultMaxHealth = this.config.getDouble(defaultHpPath, 20.0D);
    }

    private void loadPety() {
        this.pety.clear();
        ConfigurationSection section = this.petyRoot == null ? null : this.config.getConfigurationSection(this.petyRoot);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String path = this.petyRoot + "." + key;
            if (!this.config.getBoolean(path + ".enabled", true)) {
                continue;
            }

            String itemKey = this.config.getString(path + ".type", key);
            ItemStack displayItem = ConfigItemStackReader.getItem(this.config, path + ".display-item");
            if (displayItem != null) {
                itemKey = key;
                this.petItems.put(itemKey, displayItem);
            }

            String displayName = firstString(path + ".display-item.name", path + ".displayname", path + ".name");
            List<String> lore = firstStringList(path + ".display-item.lore", path + ".lore");
            String color = firstString(path + ".display-item.color", path + ".color");
            BoostsData boosts = readBoosts(path);
            Map<String, String> armor = readArmor(path);
            this.pety.put(key, new PetData(itemKey, displayName, lore, boosts, armor, color));
        }

        String zwojRoot = SzafkaConfig.firstSetPath(this.config, SzafkaConfig.path("zwoj-pet"), "zwoj-pet");
        String zwojName = zwojRoot == null
                ? "&6&lZwoj przedluzenia"
                : this.config.getString(zwojRoot + ".name", "&6&lZwoj przedluzenia");
        String zwojItemKey = zwojRoot == null
                ? "FLOWER_BANNER_PATTERN"
                : this.config.getString(zwojRoot + ".type", "FLOWER_BANNER_PATTERN");
        List<String> zwojLore = zwojRoot == null ? List.of() : this.config.getStringList(zwojRoot + ".lore");
        this.pety.put("zwoj-pet", new PetData(zwojItemKey, zwojName, zwojLore, null, Map.of(), "#FFFFFF"));
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
                firstBoolean(boostsSection, false, "double-ender-pearl-drop"),
                firstBoolean(boostsSection, false, "see-health"),
                firstDouble(boostsSection, 0.0D, "stone-experience"),
                firstBoolean(boostsSection, false, "wampir"),
                firstDouble(boostsSection, 0.0D, "attack-faster")
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
        this.petItems.clear();
        ConfigurationSection section = this.itemsRoot == null ? null : this.config.getConfigurationSection(this.itemsRoot);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ItemStack item = ConfigItemStackReader.getItem(this.config, this.itemsRoot + "." + key);
            if (item != null) {
                this.petItems.put(key, item);
            }
        }
    }

    public void saveItem(String name, ItemStack item) {
        String root = this.itemsRoot != null ? this.itemsRoot : SzafkaConfig.path("pet-items");
        this.config.set(root + "." + name, item);
        try {
            this.config.save(this.configFile);
            this.itemsRoot = root;
            loadItems();
            loadPety();
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Nie udalo sie zapisac pet-items do anarchiagg/anarchiagg.yml", exception);
        }
    }

    private void startExpirationChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PetyDatabase.PetRecord record = database.getActivePetData(player.getUniqueId());
                    if (!record.hasActivePet()) {
                        continue;
                    }
                    String activePetKey = record.activePet();
                    String expirationDateString = record.expirationDate();
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
                        database.removeActivePet(player.getUniqueId());
                        player.sendMessage(ColorUtils.error("Twój aktywny pet (" + activePetKey + ") wygasł i został zdjęty."));
                    } catch (DateTimeParseException exception) {
                        plugin.getLogger().log(Level.WARNING, "Blad parsowania daty wygasniecia dla gracza " + player.getName() + ": " + expirationDateString, exception);
                    } catch (Exception exception) {
                        plugin.getLogger().log(Level.SEVERE, "Nieznany blad w ExpirationChecker dla gracza " + player.getName(), exception);
                    }
                }
            }
        }.runTaskTimer(this.plugin, 40L, 200L);
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

    public ItemStack getPetItem(String itemKey) {
        ItemStack item = this.petItems.get(itemKey);
        return item != null ? item.clone() : null;
    }

    public Map<String, PetData> getPety() {
        return this.pety;
    }

    public PetData getPetData(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        PetData data = this.pety.get(rawKey);
        if (data != null) {
            return data;
        }
        for (Map.Entry<String, PetData> entry : this.pety.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(rawKey)) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, PetData> entry : this.pety.entrySet()) {
            if (entry.getValue().displayName() != null && entry.getValue().displayName().equalsIgnoreCase(rawKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public PetyDatabase getDatabase() {
        return this.database;
    }

    public double getDefaultMaxHealth() {
        return this.defaultMaxHealth;
    }

    public record BoostsData(double damage, double damageReduction, double fallDamageReduction, int hearts,
                             boolean negativeEffectsDisable, double robMoney, List<String> effects,
                             List<String> totemPopEffects, double dripstoneDamageReduction, boolean noHunger,
                             boolean doubleDrop, boolean doubleEnderPearlDrop, boolean seeHealth,
                             double stoneExperience, boolean wampir, double attackfast) {
    }

    public record PetData(String itemKey, String displayName, List<String> lore, BoostsData boosts,
                          Map<String, String> armor, String color) {
    }
}
