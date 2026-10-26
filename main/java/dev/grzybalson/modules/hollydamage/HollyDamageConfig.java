package dev.grzybalson.modules.hollydamage;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class HollyDamageConfig {

    private static final String RESOURCE = "addons/hollydamage/damage.yml";

    private final HollyCore plugin;
    private final File file;
    private YamlConfiguration yaml;

    private Set<Material> weapons = Set.of();
    private Set<Material> armor = Set.of();
    private Set<Material> dripstone = Set.of(Material.POINTED_DRIPSTONE);
    private Map<Material, Double> weaponMultipliers = Map.of();
    private NavigableMap<Integer, Double> multipliers = new TreeMap<>();
    private boolean weaponsMatchAll;
    private String activeSkypvpProfileId = "anarchiaboxpvp";

    private boolean enabled = true;
    private String mode = "advanced";
    private double simpleMultiplier = 1.0D;
    private double maxDamage = -1.0D;
    private double baseDamage = 16.0D;
    private double diamondSetPenalty = 0.12D;
    private double strengthPerLevel = 3.0D;
    private double weaknessPerLevel = 4.0D;
    private int minLevel = 2;
    private int maxLevel = 30;
    private boolean debugEnabled;
    private String debugPermission = "Holly.damage.debug";
    private boolean debugNotifyAttacker = true;
    private boolean debugNotifyVictim;
    private boolean armorDurabilityEnabled = true;
    private double armorDurabilityDivisor = 8.0D;
    private boolean dripstoneEnabled = true;
    private double dripstoneMultiplier = 1.0D;

    public HollyDamageConfig(HollyCore plugin) {
        this.plugin = plugin;
        this.file = HollyDataPaths.getRootFile(plugin, RESOURCE);
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE);
        yaml = YamlConfiguration.loadConfiguration(file);
        ensureDefaults();
        activeSkypvpProfileId = yaml.getString("skypvp.active-profile", "anarchiaboxpvp");
        applySkypvpProfile(activeSkypvpProfileId);
        saveQuietly();
    }

    private void ensureDefaults() {
        setIfMissing("enabled", true);
        setIfMissing("mode", "advanced");
        setIfMissing("simple.multiplier", 1.0);
        setIfMissing("advanced.weapons", List.of("DIAMOND_SWORD", "NETHERITE_SWORD"));
        setIfMissing("advanced.armor", List.of(
            "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
            "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS"
        ));
        if (!yaml.contains("advanced.weapon-multipliers")) {
            Map<String, Object> weaponDefaults = new LinkedHashMap<>();
            weaponDefaults.put("NETHERITE_SWORD", 1.0);
            weaponDefaults.put("DIAMOND_SWORD", 0.85);
            yaml.set("advanced.weapon-multipliers", weaponDefaults);
        }
        setIfMissing("advanced.sharpness.start", 3);
        setIfMissing("advanced.sharpness.step", 2);
        setIfMissing("advanced.protection.start", 2);
        setIfMissing("advanced.protection.step", 2);
        if (!yaml.contains("advanced.multipliers")) {
            Map<String, Object> defaults = new LinkedHashMap<>();
            defaults.put("-4", 0.5);
            defaults.put("-3", 0.6);
            defaults.put("-2", 0.7);
            defaults.put("-1", 0.8);
            defaults.put("0", 0.9);
            defaults.put("1", 1.0);
            defaults.put("2", 1.1);
            defaults.put("3", 1.2);
            defaults.put("4", 1.3);
            yaml.set("advanced.multipliers", defaults);
        }
        setIfMissing("max-damage", -1.0);
        setIfMissing("scale.base-damage", 16.0);
        setIfMissing("scale.min-level", 2);
        setIfMissing("scale.max-level", 30);
        setIfMissing("scale.diamond-set-penalty", 0.12);
        if (!yaml.getBoolean("scale.diamond-eq-netherite-prot5", false)) {
            yaml.set("scale.diamond-eq-netherite-prot5", true);
            yaml.set("scale.diamond-set-penalty", 0.12);
        }
        setIfMissing("effects.strength-per-level", 3.0);
        setIfMissing("effects.weakness-per-level", 4.0);
        setIfMissing("debug.enabled", false);
        setIfMissing("debug.permission", "Holly.damage.debug");
        setIfMissing("debug.notify-attacker", true);
        setIfMissing("debug.notify-victim", false);
        setIfMissing("armor-durability.enabled", true);
        setIfMissing("armor-durability.divisor", 8.0);
        setIfMissing("dripstone.enabled", true);
        setIfMissing("dripstone.multiplier", 1.0);
        setIfMissing("dripstone.materials", List.of("POINTED_DRIPSTONE"));
        setIfMissing("skypvp.active-profile", "anarchiaboxpvp");
    }

    private void setIfMissing(String path, Object value) {
        if (!yaml.contains(path)) {
            yaml.set(path, value);
        }
    }

    public void applySkypvpProfile(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            profileId = "anarchiaboxpvp";
        }
        activeSkypvpProfileId = profileId.trim().toLowerCase(Locale.ROOT);
        String base = "skypvp.profiles." + activeSkypvpProfileId + ".";
        if (!yaml.contains(base + "weapons") && !yaml.contains(base + "weapons-match-all")) {
            plugin.getLogger().warning("[HollyDamage] Profil skypvp '" + activeSkypvpProfileId
                + "' nie istnieje w damage.yml - zostaje poprzednia konfiguracja advanced.*");
            reloadCachesFromAdvanced();
            return;
        }
        yaml.set("skypvp.active-profile", activeSkypvpProfileId);
        yaml.set("advanced.weapons-match-all", yaml.getBoolean(base + "weapons-match-all", false));
        yaml.set("advanced.weapons", new ArrayList<>(yaml.getStringList(base + "weapons")));
        yaml.set("advanced.armor", new ArrayList<>(yaml.getStringList(base + "armor")));
        ConfigurationSection profileWeaponMultipliers = yaml.getConfigurationSection(base + "weapon-multipliers");
        if (profileWeaponMultipliers != null) {
            yaml.set("advanced.weapon-multipliers", profileWeaponMultipliers.getValues(false));
        }
        yaml.set("advanced.sharpness.start", yaml.getInt(base + "sharpness.start", 3));
        yaml.set("advanced.sharpness.step", yaml.getInt(base + "sharpness.step", 2));
        yaml.set("advanced.protection.start", yaml.getInt(base + "protection.start", 2));
        yaml.set("advanced.protection.step", yaml.getInt(base + "protection.step", 2));
        reloadCachesFromAdvanced();
    }

    private void reloadCachesFromAdvanced() {
        weaponsMatchAll = yaml.getBoolean("advanced.weapons-match-all", false);
        weapons = yaml.getStringList("advanced.weapons").stream()
            .map(this::matchMaterial)
            .filter(m -> m != null)
            .collect(Collectors.toSet());
        armor = yaml.getStringList("advanced.armor").stream()
            .map(this::matchMaterial)
            .filter(m -> m != null)
            .collect(Collectors.toSet());
        Set<Material> dripstoneMaterials = yaml.getStringList("dripstone.materials").stream()
            .map(this::matchMaterial)
            .filter(m -> m != null)
            .collect(Collectors.toSet());
        dripstone = dripstoneMaterials.isEmpty() ? Set.of(Material.POINTED_DRIPSTONE) : Set.copyOf(dripstoneMaterials);
        Map<Material, Double> parsedWeaponMultipliers = new LinkedHashMap<>();
        ConfigurationSection weaponMultiplierSection = yaml.getConfigurationSection("advanced.weapon-multipliers");
        if (weaponMultiplierSection != null) {
            for (String key : weaponMultiplierSection.getKeys(false)) {
                Material material = matchMaterial(key);
                if (material != null) {
                    parsedWeaponMultipliers.put(material, weaponMultiplierSection.getDouble(key, 1.0D));
                }
            }
        }
        weaponMultipliers = Map.copyOf(parsedWeaponMultipliers);
        multipliers = new TreeMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("advanced.multipliers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    multipliers.put(Integer.parseInt(key), section.getDouble(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        reloadScalarCache();
    }

    /** Skalary z yamla czytane raz na reload — w hicie to bylo ~15 lookupow po sciezkach na cios. */
    private void reloadScalarCache() {
        enabled = yaml.getBoolean("enabled", true);
        mode = yaml.getString("mode", "advanced");
        simpleMultiplier = yaml.getDouble("simple.multiplier", 1.0);
        maxDamage = yaml.getDouble("max-damage", -1.0);
        baseDamage = Math.max(0.5D, yaml.getDouble("scale.base-damage", 16.0D));
        diamondSetPenalty = Math.max(0.0D, Math.min(1.0D, yaml.getDouble("scale.diamond-set-penalty", 0.12D)));
        strengthPerLevel = yaml.getDouble("effects.strength-per-level", 3.0D);
        weaknessPerLevel = yaml.getDouble("effects.weakness-per-level", 4.0D);
        minLevel = yaml.getInt("scale.min-level", 2);
        maxLevel = Math.max(minLevel + 1, yaml.getInt("scale.max-level", 30));
        debugEnabled = yaml.getBoolean("debug.enabled", false);
        debugPermission = yaml.getString("debug.permission", "Holly.damage.debug");
        debugNotifyAttacker = yaml.getBoolean("debug.notify-attacker", true);
        debugNotifyVictim = yaml.getBoolean("debug.notify-victim", false);
        armorDurabilityEnabled = yaml.getBoolean("armor-durability.enabled", true);
        armorDurabilityDivisor = Math.max(1.0, yaml.getDouble("armor-durability.divisor", 8.0));
        dripstoneEnabled = yaml.getBoolean("dripstone.enabled", true);
        double dripstoneMul = yaml.getDouble("dripstone.multiplier", 1.0D);
        dripstoneMultiplier = (Double.isNaN(dripstoneMul) || Double.isInfinite(dripstoneMul))
            ? 1.0D
            : Math.max(0.0D, dripstoneMul);
    }

    private Material matchMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void saveQuietly() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("[HollyDamage] Nie udalo sie zapisac damage.yml: " + exception.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMode() {
        return mode;
    }

    public double getSimpleMultiplier() {
        return simpleMultiplier;
    }

    public int getSharpnessStart() {
        return yaml.getInt("advanced.sharpness.start", 3);
    }

    public int getSharpnessStep() {
        return yaml.getInt("advanced.sharpness.step", 2);
    }

    public int getProtectionStart() {
        return yaml.getInt("advanced.protection.start", 2);
    }

    public int getProtectionStep() {
        return yaml.getInt("advanced.protection.step", 2);
    }

    public Set<Material> getWeapons() {
        return weapons;
    }

    public Set<Material> getArmor() {
        return armor;
    }

    public boolean isWeaponsMatchAll() {
        return weaponsMatchAll;
    }

    public String getActiveSkypvpProfileId() {
        return activeSkypvpProfileId;
    }

    public double getMultiplier(int diff) {
        if (multipliers.isEmpty()) {
            return 1.0;
        }
        Double exact = multipliers.get(diff);
        if (exact != null) {
            return clampMultiplier(exact);
        }
        Map.Entry<Integer, Double> floor = multipliers.floorEntry(diff);
        Map.Entry<Integer, Double> ceil = multipliers.ceilingEntry(diff);
        if (floor != null && ceil != null && !floor.getKey().equals(ceil.getKey())) {
            double span = ceil.getKey() - floor.getKey();
            double t = (diff - floor.getKey()) / span;
            return clampMultiplier(floor.getValue() + t * (ceil.getValue() - floor.getValue()));
        }
        if (floor != null) {
            Map.Entry<Integer, Double> prev = multipliers.lowerEntry(floor.getKey());
            if (prev != null && !prev.getKey().equals(floor.getKey())) {
                double slope = (floor.getValue() - prev.getValue()) / (floor.getKey() - prev.getKey());
                return clampMultiplier(floor.getValue() + slope * (diff - floor.getKey()));
            }
            return clampMultiplier(floor.getValue());
        }
        if (ceil != null) {
            Map.Entry<Integer, Double> next = multipliers.higherEntry(ceil.getKey());
            if (next != null && !next.getKey().equals(ceil.getKey())) {
                double slope = (next.getValue() - ceil.getValue()) / (next.getKey() - ceil.getKey());
                return clampMultiplier(ceil.getValue() + slope * (diff - ceil.getKey()));
            }
            return clampMultiplier(ceil.getValue());
        }
        return 1.0;
    }

    private static double clampMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0;
        }
        return Math.max(0.20D, Math.min(2.50D, value));
    }

    public double getMaxDamage() {
        return maxDamage;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    /** Diax set bierze tyle razy wiecej dmg niz netherite prot 5 (0.12 = 12% gorszy). */
    public double getDiamondSetPenalty() {
        return diamondSetPenalty;
    }

    public double getStrengthPerLevel() {
        return strengthPerLevel;
    }

    public double getWeaknessPerLevel() {
        return weaknessPerLevel;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /** Perk min → -7, max → 7. */
    public int toSlot(int level) {
        int min = minLevel;
        int max = maxLevel;
        int clamped = Math.max(min, Math.min(max, level));
        double t = (clamped - min) / (double) (max - min);
        int slot = (int) Math.round(t * 14.0D - 7.0D);
        return Math.max(-7, Math.min(7, slot));
    }

    public boolean controlsMelee(ItemStack weapon) {
        if (!isEnabled() || !"advanced".equalsIgnoreCase(getMode())) {
            return false;
        }
        if (weapon == null || weapon.getType().isAir()) {
            return false;
        }
        return isWeaponsMatchAll() || getWeapons().contains(weapon.getType());
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public String getDebugPermission() {
        return debugPermission;
    }

    public boolean isDebugNotifyAttacker() {
        return debugNotifyAttacker;
    }

    public boolean isDebugNotifyVictim() {
        return debugNotifyVictim;
    }

    public boolean isArmorDurabilityEnabled() {
        return armorDurabilityEnabled;
    }

    public double getArmorDurabilityDivisor() {
        return armorDurabilityDivisor;
    }

    /** Mnoznik obrazen zalezny od materialu broni (np. diax 0.85 wzgledem netherite 1.0). */
    public double getWeaponMultiplier(Material material) {
        if (material == null) {
            return 1.0D;
        }
        Double value = weaponMultipliers.get(material);
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0D;
        }
        return Math.max(0.0D, value);
    }

    public boolean isDripstoneEnabled() {
        return dripstoneEnabled;
    }

    /** Mnoznik obrazen przy biciu dripstonem (1.0 = bez zmian). */
    public double getDripstoneMultiplier() {
        return dripstoneMultiplier;
    }

    public Set<Material> getDripstoneMaterials() {
        return dripstone;
    }
}
