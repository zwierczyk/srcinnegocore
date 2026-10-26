package dev.grzybalson.anarchiagg.szafa.papugi;

import dev.grzybalson.anarchiagg.szafa.SzafkaConfig;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.ColorUtils;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PapugiHandler {
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
    public static final String PAPUGA_KEY_TAG = "szafa_papuga_key";
    public static final String PAPUGA_DURATION_TAG = "szafa_papuga_duration";
    public static final String PAPUGA_EXPIRATION_TAG = "szafa_papuga_expiration";
    public static final String PAPUGA_BONUS_TAG = "szafa_papuga_bonus";
    public static final String PAPUGA_ROLLS_TAG = "szafa_papuga_rerolls";
    public static final String PAPUGA_RANDOM_NAME_TAG = "szafa_papuga_random_name";
    public static final String PAPUGA_REAL_DISPLAY_TAG = "szafa_papuga_real_display";

    private final JavaPlugin plugin;
    private final PapugiDatabase database;
    private final Map<String, PapugaData> papugi = new LinkedHashMap<>();
    private final Map<String, BonusData> bonuses = new LinkedHashMap<>();
    private File configFile;
    private FileConfiguration papugiConfig;
    private double baseRollCost = 50.0;
    private double rollMultiplier = 2.0;
    private Economy economy;

    public PapugiHandler(JavaPlugin plugin, PapugiDatabase database) {
        this.plugin = plugin;
        this.database = database;
        this.setupFiles();
        this.hookEconomy();
        this.loadPapugi();
        this.startExpirationChecker();
    }

    private void setupFiles() {
        this.configFile = SzafkaConfig.getFile(this.plugin);
        this.papugiConfig = SzafkaConfig.getConfig(this.plugin);
    }

    private void hookEconomy() {
        try {
            RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (registration != null) {
                this.economy = registration.getProvider();
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Nie udało się podpiąć ekonomii dla papug: " + e.getMessage());
        }
    }

    public void reload() {
        this.papugiConfig = SzafkaConfig.getConfig(this.plugin);
        this.loadPapugi();
    }

    private void loadPapugi() {
        this.papugi.clear();
        this.bonuses.clear();
        String rollRoot = SzafkaConfig.firstSetPath(this.papugiConfig, SzafkaConfig.path("roll"), "roll");
        this.baseRollCost = this.papugiConfig.getDouble((rollRoot == null ? SzafkaConfig.path("roll") : rollRoot) + ".base-cost", 50.0);
        this.rollMultiplier = this.papugiConfig.getDouble((rollRoot == null ? SzafkaConfig.path("roll") : rollRoot) + ".multiplier", 2.0);

        String bonusesRoot = SzafkaConfig.firstSection(this.papugiConfig, SzafkaConfig.path("bonuses"), "bonuses");
        ConfigurationSection bonusSection = bonusesRoot == null ? null : this.papugiConfig.getConfigurationSection(bonusesRoot);
        if (bonusSection != null) {
            for (String key : bonusSection.getKeys(false)) {
                String path = bonusesRoot + "." + key;
                String displayName = this.papugiConfig.getString(path + ".displayname", key);
                List<String> lore = this.papugiConfig.getStringList(path + ".lore");
                double damageMultiplier = this.papugiConfig.getDouble(path + ".damage-multiplier", 0.0);
                double damageBonus = this.papugiConfig.getDouble(path + ".damage", 0.0);
                List<String> effects = this.papugiConfig.getStringList(path + ".effects");
                int weight = Math.max(1, this.papugiConfig.getInt(path + ".weight", 1));
                this.bonuses.put(key, new BonusData(key, displayName, lore, damageMultiplier, damageBonus, effects, weight));
            }
        }

        String papugiRoot = SzafkaConfig.firstSection(this.papugiConfig, SzafkaConfig.path("papugi"), "papugi");
        ConfigurationSection papugiSection = papugiRoot == null ? null : this.papugiConfig.getConfigurationSection(papugiRoot);
        if (papugiSection != null) {
            for (String key : papugiSection.getKeys(false)) {
                String path = papugiRoot + "." + key;
                if (!this.papugiConfig.getBoolean(path + ".enabled", true)) {
                    continue;
                }
                Material material = Material.matchMaterial(this.papugiConfig.getString(path + ".material", "PARROT_SPAWN_EGG"));
                if (material == null) {
                    material = Material.PARROT_SPAWN_EGG;
                }
                String displayName = this.papugiConfig.getString(path + ".displayname", "&fPapuga");
                List<String> lore = this.papugiConfig.getStringList(path + ".lore");
                String shoulder = this.papugiConfig.getString(path + ".shoulder", "right");
                String variantName = this.papugiConfig.getString(path + ".variant", "BLUE").toUpperCase(Locale.ROOT);
                Parrot.Variant variant;
                try {
                    variant = Parrot.Variant.valueOf(variantName);
                } catch (IllegalArgumentException ignored) {
                    variant = Parrot.Variant.BLUE;
                }
                List<String> allowedBonuses = this.papugiConfig.getStringList(path + ".allowed-bonuses");
                List<String> randomNames = this.papugiConfig.getStringList(path + ".random-names");
                String giveDisplayName = this.papugiConfig.getString(path + ".give-displayname", "&7???");
                this.papugi.put(key, new PapugaData(key, material, displayName, lore, shoulder, variant, allowedBonuses, randomNames, giveDisplayName));
            }
        }

        if (rollRoot == null || !this.papugiConfig.isSet(rollRoot + ".base-cost") || !this.papugiConfig.isSet(rollRoot + ".multiplier")) {
            this.saveConfigQuietly();
        }
    }

    private void saveConfigQuietly() {
        try {
            this.papugiConfig.save(this.configFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac papug w anarchiagg/anarchiagg.yml", e);
        }
    }

    private void startExpirationChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PapugiDatabase.PapugaRecord record = database.getActivePapugaData(player.getUniqueId());
                    if (!record.hasActivePapuga()) {
                        continue;
                    }
                    String expiration = record.expirationDate();
                    if (expiration == null || expiration.isBlank() || expiration.equalsIgnoreCase("NA ZAWSZE") || expiration.equalsIgnoreCase("Brak")) {
                        continue;
                    }
                    try {
                        LocalDateTime expirationTime = LocalDateTime.parse(expiration.trim(), DATE_FORMATTER);
                        if (!now.isAfter(expirationTime)) {
                            continue;
                        }
                        database.removeActivePapuga(player.getUniqueId());
                        player.sendMessage(ColorUtils.error("Twoja papuga (" + record.activePapuga() + ") wygasła i została zdjęta."));
                    } catch (DateTimeParseException e) {
                        plugin.getLogger().log(Level.WARNING, "Błąd parsowania daty papugi gracza " + player.getName() + ": " + expiration, e);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Nieznany błąd ExpirationChecker dla papugi gracza " + player.getName(), e);
                    }
                }
            }
        }.runTaskTimer(this.plugin, 40L, 200L);
    }

    public ItemStack createItemForGive(String papugaKey, String duration) {
        PapugaData data = this.papugi.get(papugaKey);
        if (data == null) {
            return new ItemStack(Material.STONE);
        }
        String randomName = this.rollRandomName(data);
        String actualDisplayName = this.resolvePapugaDisplayName(data, duration, randomName);
        String giveDisplayName = this.resolveGiveDisplayName(data, duration, randomName);
        ItemStack item = new ItemStack(data.material());
        return this.applyPapugaMeta(item, data, giveDisplayName, actualDisplayName, randomName, duration, null, null, 0);
    }

    public ItemStack createStoredItem(String papugaKey, String storedDisplayName, String expirationDate, String bonusKey, int rerolls) {
        PapugaData data = this.papugi.get(papugaKey);
        if (data == null) {
            return new ItemStack(Material.STONE);
        }
        String safeDisplayName = storedDisplayName != null ? storedDisplayName : ChatColor.translateAlternateColorCodes('&', data.displayName().replace(" na czas %czas%", ""));
        String duration = this.extractDurationFromDisplayName(safeDisplayName);
        ItemStack item = new ItemStack(data.material());
        return this.applyPapugaMeta(item, data, safeDisplayName, safeDisplayName, null, duration, expirationDate, bonusKey, rerolls);
    }

    public ItemStack createGuiItem(Player player) {
        PapugiDatabase.PapugaRecord record = this.database.getActivePapugaData(player.getUniqueId());
        if (!record.hasActivePapuga()) {
            return new ItemStack(Material.STONE);
        }
        return this.createStoredItem(record.activePapuga(), record.displayName(), record.expirationDate(), record.bonusKey(), record.rerolls());
    }

    private ItemStack applyPapugaMeta(ItemStack item, PapugaData data, String visibleDisplayName, String realDisplayName, String randomName, String duration, String expirationDate, String bonusKey, int rerolls) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', visibleDisplayName));
        List<String> lore = new ArrayList<>();
        for (String line : data.lore()) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("%czas%", duration == null ? "NA ZAWSZE" : duration).replace("%uzycie%", expirationDate == null ? "Brak" : expirationDate)));
        }
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&', "&fOtrzymasz następujące bonusy:"));
        BonusData bonusData = bonusKey != null ? this.bonuses.get(bonusKey) : null;
        if (bonusData == null) {
            lore.add(ChatColor.translateAlternateColorCodes('&', " &8» &7???"));
        } else if (bonusData.lore().isEmpty()) {
            lore.add(ChatColor.translateAlternateColorCodes('&', " &8» " + bonusData.displayName()));
        } else {
            for (String line : bonusData.lore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
        }
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7Przelosowań: &f" + rerolls));
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7Następny koszt: &a" + this.formatMoney(this.getRollCost(rerolls)) + "$"));
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7Wygaśnie: &f" + (expirationDate == null ? "Brak" : expirationDate)));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.values());
        this.hideAdditionalTooltip(meta);
        item.setItemMeta(meta);

        NBTUtils.setString(item, PAPUGA_KEY_TAG, data.key());
        NBTUtils.setInt(item, PAPUGA_ROLLS_TAG, rerolls);
        if (realDisplayName != null && !realDisplayName.isBlank()) {
            NBTUtils.setString(item, PAPUGA_REAL_DISPLAY_TAG, realDisplayName);
        }
        if (randomName != null && !randomName.isBlank()) {
            NBTUtils.setString(item, PAPUGA_RANDOM_NAME_TAG, randomName);
        }
        if (duration != null) {
            NBTUtils.setString(item, PAPUGA_DURATION_TAG, duration);
        }
        if (expirationDate != null) {
            NBTUtils.setString(item, PAPUGA_EXPIRATION_TAG, expirationDate);
        }
        if (bonusKey != null) {
            NBTUtils.setString(item, PAPUGA_BONUS_TAG, bonusKey);
        }
        return item;
    }

    public BonusData rollBonusForPapuga(PapugaData papugaData, String excludedKey) {
        List<BonusData> available = new ArrayList<>();
        Collection<String> requested = papugaData.allowedBonuses().isEmpty() ? this.bonuses.keySet() : papugaData.allowedBonuses();
        for (String key : requested) {
            BonusData data = this.bonuses.get(key);
            if (data != null) {
                available.add(data);
            }
        }
        if (available.isEmpty()) {
            return null;
        }
        int totalWeight = 0;
        for (BonusData data : available) {
            if (!available.isEmpty() && available.size() > 1 && data.key().equalsIgnoreCase(excludedKey)) {
                continue;
            }
            totalWeight += Math.max(1, data.weight());
        }
        if (totalWeight <= 0) {
            totalWeight = available.stream().mapToInt(BonusData::weight).sum();
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int current = 0;
        BonusData fallback = available.get(0);
        for (BonusData data : available) {
            if (available.size() > 1 && data.key().equalsIgnoreCase(excludedKey)) {
                continue;
            }
            current += Math.max(1, data.weight());
            if (roll <= current) {
                return data;
            }
            fallback = data;
        }
        return fallback;
    }



    public String resolveActivationDisplayName(ItemStack item, String papugaKey, String fallbackDisplayName) {
        String realDisplayName = NBTUtils.getString(item, PAPUGA_REAL_DISPLAY_TAG);
        if (realDisplayName != null && !realDisplayName.isBlank()) {
            return realDisplayName;
        }
        PapugaData data = this.papugi.get(papugaKey);
        if (data == null) {
            return fallbackDisplayName;
        }
        String duration = NBTUtils.getString(item, PAPUGA_DURATION_TAG);
        String randomName = NBTUtils.getString(item, PAPUGA_RANDOM_NAME_TAG);
        String resolved = this.resolvePapugaDisplayName(data, duration, randomName);
        return resolved != null ? resolved : fallbackDisplayName;
    }

    private String resolvePapugaDisplayName(PapugaData data, String duration, String randomName) {
        return ChatColor.translateAlternateColorCodes('&', data.displayName()
                .replace("%czas%", duration == null ? "NA ZAWSZE" : duration)
                .replace("%imie%", randomName == null ? "???" : randomName));
    }

    private String resolveGiveDisplayName(PapugaData data, String duration, String randomName) {
        return ChatColor.translateAlternateColorCodes('&', data.giveDisplayName()
                .replace("%czas%", duration == null ? "NA ZAWSZE" : duration)
                .replace("%imie%", randomName == null ? "???" : randomName));
    }

    private String rollRandomName(PapugaData data) {
        if (data.randomNames() == null || data.randomNames().isEmpty()) {
            return "???";
        }
        List<String> names = data.randomNames();
        return names.get(ThreadLocalRandom.current().nextInt(names.size()));
    }

    private void hideAdditionalTooltip(ItemMeta meta) {
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public double getRollCost(int rerolls) {
        double result = this.baseRollCost;
        for (int i = 0; i < rerolls; i++) {
            result *= this.rollMultiplier;
        }
        return result;
    }

    public String formatMoney(double value) {
        if (Math.floor(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    public boolean canAfford(Player player, double cost) {
        return this.economy != null && this.economy.has(player, cost);
    }

    public boolean withdraw(Player player, double cost) {
        return this.economy != null && this.economy.withdrawPlayer(player, cost).transactionSuccess();
    }

    public boolean hasEconomy() {
        return this.economy != null;
    }

    public String extractDurationFromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String stripped = ChatColor.stripColor(displayName);
        int index = stripped.toLowerCase(Locale.ROOT).lastIndexOf(" na czas ");
        if (index == -1) {
            return null;
        }
        return stripped.substring(index + 9).trim();
    }

    public String buildExpirationFromItem(ItemStack item) {
        String storedExpiration = NBTUtils.getString(item, PAPUGA_EXPIRATION_TAG);
        if (storedExpiration != null && !storedExpiration.isBlank()) {
            return storedExpiration;
        }
        String duration = NBTUtils.getString(item, PAPUGA_DURATION_TAG);
        if (duration == null || duration.isBlank()) {
            return null;
        }
        LocalDateTime end = this.calculateExpirationTime(LocalDateTime.now(), duration);
        return end == null ? null : end.format(DATE_FORMATTER);
    }

    public LocalDateTime calculateExpirationTime(LocalDateTime start, String duration) {
        try {
            String lower = duration.toLowerCase(Locale.ROOT);
            int amount = Integer.parseInt(lower.substring(0, lower.length() - 1));
            char suffix = lower.charAt(lower.length() - 1);
            return switch (suffix) {
                case 'd' -> start.plusDays(amount);
                case 'h' -> start.plusHours(amount);
                case 'm' -> start.plusMinutes(amount);
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    public Map<String, PapugaData> getPapugi() {
        return Collections.unmodifiableMap(this.papugi);
    }

    public Map<String, BonusData> getBonuses() {
        return Collections.unmodifiableMap(this.bonuses);
    }

    public PapugiDatabase getDatabase() {
        return this.database;
    }

    public BonusData getBonus(String key) {
        return this.bonuses.get(key);
    }

    public PapugaData getPapuga(String key) {
        return this.papugi.get(key);
    }

    public record PapugaData(String key, Material material, String displayName, List<String> lore, String shoulder, Parrot.Variant variant, List<String> allowedBonuses, List<String> randomNames, String giveDisplayName) {}
    public record BonusData(String key, String displayName, List<String> lore, double damageMultiplier, double damageBonus, List<String> effects, int weight) {}
}
