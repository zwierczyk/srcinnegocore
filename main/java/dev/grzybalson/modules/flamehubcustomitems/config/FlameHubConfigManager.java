package dev.grzybalson.modules.flamehubcustomitems.config;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.modules.flamehubcustomitems.manager.CustomItemType;
import dev.grzybalson.modules.flamehubcustomitems.util.FlameColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public class FlameHubConfigManager {

    private static final String RESOURCE_PATH = "addons/flamehub-customitems/flamehub-customitems.yml";

    private final HollyCore plugin;
    private final File configFile;

    private Material yodaMaterial;
    private String yodaName;
    private List<String> yodaLore;
    private int yodaDurationSeconds;
    private double yodaDamageReductionPercent;
    private int yodaCooldownSeconds;

    private Material vaderMaterial;
    private String vaderName;
    private List<String> vaderLore;
    private int vaderDurationSeconds;
    private double vaderDamageIncreasePercent;
    private int vaderCooldownSeconds;

    private String guiTitle;
    private int guiSize;
    private boolean guiFillerEnabled;
    private Material guiFillerMaterial;
    private String guiFillerName;
    private int guiYodaSlot;
    private int guiVaderSlot;

    private String prefix;
    private final Map<String, String> messages = new HashMap<>();

    public FlameHubConfigManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "flamehub-customitems"), "flamehub-customitems.yml");
        loadConfig();
    }

    public void loadConfig() {
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        FileConfiguration config = configFile.exists()
                ? YamlConfiguration.loadConfiguration(configFile)
                : new YamlConfiguration();

        this.yodaMaterial = parseMaterial(config.getString("items.yoda.material", "NETHER_STAR"), Material.NETHER_STAR);
        this.yodaName = config.getString("items.yoda.name", "&#2ecc71&lMoc Yody");
        this.yodaLore = config.getStringList("items.yoda.lore");
        int yodaDur = config.getInt("items.yoda.duration-seconds", -1);
        if (yodaDur <= 0) yodaDur = config.getInt("items.yoda.duration", -1);
        if (yodaDur <= 0) yodaDur = config.getInt("items.yoda.time", 3);
        this.yodaDurationSeconds = Math.max(1, yodaDur);
        this.yodaDamageReductionPercent = config.getDouble("items.yoda.damage-reduction-percent", 30.0);
        int yodaCd = config.getInt("items.yoda.cooldown-seconds", -1);
        if (yodaCd <= 0) yodaCd = config.getInt("items.yoda.cooldown", 60);
        this.yodaCooldownSeconds = Math.max(1, yodaCd);

        this.vaderMaterial = parseMaterial(config.getString("items.vader.material", "BLAZE_ROD"), Material.BLAZE_ROD);
        this.vaderName = config.getString("items.vader.name", "&#e74c3c&lMoc Vadera");
        this.vaderLore = config.getStringList("items.vader.lore");
        int vaderDur = config.getInt("items.vader.duration-seconds", -1);
        if (vaderDur <= 0) vaderDur = config.getInt("items.vader.duration", -1);
        if (vaderDur <= 0) vaderDur = config.getInt("items.vader.time", 3);
        this.vaderDurationSeconds = Math.max(1, vaderDur);

        double vaderDmg = config.getDouble("items.vader.damage-increase-percent", -1.0);
        if (vaderDmg <= 0) vaderDmg = config.getDouble("items.vader.damage-increase", -1.0);
        if (vaderDmg <= 0) vaderDmg = config.getDouble("items.vader.extra-damage-percent", 25.0);
        this.vaderDamageIncreasePercent = Math.max(0.0, vaderDmg);

        int vaderCd = config.getInt("items.vader.cooldown-seconds", -1);
        if (vaderCd <= 0) vaderCd = config.getInt("items.vader.cooldown", 60);
        this.vaderCooldownSeconds = Math.max(1, vaderCd);

        this.guiTitle = config.getString("gui.title", "&#c0392b&lFLAME&f&lHUB &8| &7Wybierz przedmiot");
        int size = config.getInt("gui.size", 9);
        if (size <= 0 || size % 9 != 0 || size > 54) {
            plugin.getLogger().warning("[FlameHub] Nieprawidłowy rozmiar GUI: " + size + ". Przywrócono domyślny: 9.");
            size = 9;
        }
        this.guiSize = size;
        this.guiFillerEnabled = config.getBoolean("gui.filler.enabled", true);
        this.guiFillerMaterial = parseMaterial(config.getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        this.guiFillerName = config.getString("gui.filler.name", "&7");
        this.guiYodaSlot = config.getInt("gui.slots.yoda", 3);
        this.guiVaderSlot = config.getInt("gui.slots.vader", 5);

        this.prefix = config.getString("messages.prefix", "&#c0392b&lFLAME&f&lHUB &8» ");
        this.messages.clear();
        if (config.isConfigurationSection("messages")) {
            for (String key : Objects.requireNonNull(config.getConfigurationSection("messages")).getKeys(false)) {
                this.messages.put(key, config.getString("messages." + key, ""));
            }
        }
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.trim().isEmpty()) {
            return fallback;
        }
        Material mat = Material.matchMaterial(name.trim().toUpperCase());
        if (mat == null) {
            plugin.getLogger().log(Level.WARNING, "[FlameHub] Nieznany materiał: ''{0}''. Użyto domyślnego: {1}", new Object[]{name, fallback});
            return fallback;
        }
        return mat;
    }

    public Material getItemMaterial(CustomItemType type) {
        return switch (type) {
            case YODA -> yodaMaterial;
            case VADER -> vaderMaterial;
        };
    }

    public int getItemCooldown(CustomItemType type) {
        return switch (type) {
            case YODA -> yodaCooldownSeconds;
            case VADER -> vaderCooldownSeconds;
        };
    }

    public String getItemName(CustomItemType type) {
        return switch (type) {
            case YODA -> yodaName;
            case VADER -> vaderName;
        };
    }

    public List<String> getItemLore(CustomItemType type) {
        return switch (type) {
            case YODA -> yodaLore;
            case VADER -> vaderLore;
        };
    }

    public int getItemDuration(CustomItemType type) {
        return switch (type) {
            case YODA -> yodaDurationSeconds;
            case VADER -> vaderDurationSeconds;
        };
    }

    public String getRawMessage(String key) {
        return messages.getOrDefault(key, "&cBrak wiadomości w configu: messages." + key);
    }

    public Component getMessage(String key, String... placeholders) {
        String msg = getRawMessage(key);
        if (placeholders != null && placeholders.length > 0) {
            for (int i = 0; i < placeholders.length - 1; i += 2) {
                String target = placeholders[i];
                String replacement = placeholders[i + 1];
                msg = msg.replace(target, replacement != null ? replacement : "");
            }
        }
        msg = msg.replace("{PREFIX}", prefix);
        return FlameColorUtils.colorize(msg);
    }

    public HollyCore getPlugin() { return plugin; }
    public Material getYodaMaterial() { return yodaMaterial; }
    public String getYodaName() { return yodaName; }
    public List<String> getYodaLore() { return yodaLore; }
    public int getYodaDurationSeconds() { return yodaDurationSeconds; }
    public double getYodaDamageReductionPercent() { return yodaDamageReductionPercent; }
    public int getYodaCooldownSeconds() { return yodaCooldownSeconds; }

    public Material getVaderMaterial() { return vaderMaterial; }
    public String getVaderName() { return vaderName; }
    public List<String> getVaderLore() { return vaderLore; }
    public int getVaderDurationSeconds() { return vaderDurationSeconds; }
    public double getVaderDamageIncreasePercent() { return vaderDamageIncreasePercent; }
    public int getVaderCooldownSeconds() { return vaderCooldownSeconds; }

    public String getGuiTitle() { return guiTitle; }
    public int getGuiSize() { return guiSize; }
    public boolean isGuiFillerEnabled() { return guiFillerEnabled; }
    public Material getGuiFillerMaterial() { return guiFillerMaterial; }
    public String getGuiFillerName() { return guiFillerName; }
    public int getGuiYodaSlot() { return guiYodaSlot; }
    public int getGuiVaderSlot() { return guiVaderSlot; }
    public String getPrefix() { return prefix; }
}
