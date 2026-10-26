package dev.grzybalson.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Bezpieczny auto-update:
 * - tworzy brakujące pliki z JAR
 * - dopisuje tylko brakujące klucze (nigdy nie nadpisuje istniejących wartości)
 * - nic nie zapisuje, jeśli nie ma realnych zmian
 */
public final class ConfigAutoUpdater {

    private static final String STATE_FILE = "config-update.yml";

    private static final String[] ENSURE_RESOURCES = {
        "addons.yml",
        "addons/afk/afk.yml",
        "addons/combat/combat.yml",
        "addons/kit/kit.yml",
        "addons/market/market.yml",
        "addons/messages/messages.yml",
        "addons/upgrade/upgrade.yml",
        "addons/szaman/szaman.yml",
        "addons/patroni/patroni.yml",
        "addons/lockedfeatures/lockedfeatures.yml",
        "addons/tablist/tablist.yml",
        "addons/boss/boss.yml",
        "addons/itemstorage/items.yml",
        "addons/itemshop/itemshop.yml",
        "addons/trade/trade.yml",
        "addons/media/media.yml",
        "addons/commands/odbierz.yml",
        "addons/flamehub-customitems/flamehub-customitems.yml",
        "addons/killeffect/killeffect.yml"
    };

    private ConfigAutoUpdater() {
    }

    public static void run(JavaPlugin plugin) {
        try {
            int stripped = ConfigYamlCommentStripper.stripAll(plugin);
            if (stripped > 0) {
                plugin.getLogger().info("Config cleanup: usunięto banery/komentarze z " + stripped + " plików YAML.");
            }

            int filesTouched = 0;
            for (String resource : ENSURE_RESOURCES) {
                if (ensureMissingOnly(plugin, resource)) {
                    filesTouched++;
                }
            }
            if (ensureAddonToggles(plugin)) {
                filesTouched++;
            }
            if (unlockFarmMechanics(plugin)) {
                filesTouched++;
            }
            if (disableDeathKeep(plugin)) {
                filesTouched++;
            }
            if (ensureHomeEnabled(plugin)) {
                filesTouched++;
            }
            if (allowDrowning(plugin)) {
                filesTouched++;
            }
            if (preferMslfEnderchest(plugin)) {
                filesTouched++;
            }
            if (applyEntityLimits(plugin)) {
                filesTouched++;
            }
            if (balanceDiamondSword(plugin)) {
                filesTouched++;
            }
            if (patchFlameHubYoda30(plugin)) {
                filesTouched++;
            }
            if (ensureUpgradeCustomModelToggle(plugin)) {
                filesTouched++;
            }
            if (patchFancyNpcPushSoft(plugin)) {
                filesTouched++;
            }
            if (fasterScoreboardRefresh(plugin)) {
                filesTouched++;
            }

            if (filesTouched > 0) {
                File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
                YamlConfiguration state = stateFile.exists()
                    ? YamlConfiguration.loadConfiguration(stateFile)
                    : new YamlConfiguration();
                state.set("last-update-millis", System.currentTimeMillis());
                state.set("last-files-touched", filesTouched);
                state.save(stateFile);
                plugin.getLogger().info("Config auto-update: dopisano brakujace pliki/klucze (" + filesTouched + ")");
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Config auto-update nieudany: " + exception.getMessage(), exception);
        }
    }

    /**
     * Tworzy plik jeśli go nie ma. Jeśli istnieje — dopisuje wyłącznie brakujące klucze.
     * Nigdy nie nadpisuje istniejących wartości.
     */
    public static boolean updateResource(JavaPlugin plugin, String resourcePath, boolean ignoredForce) {
        return ensureMissingOnly(plugin, resourcePath);
    }

    private static boolean ensureMissingOnly(JavaPlugin plugin, String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        if (plugin.getResource(normalized) == null) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), normalized);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!file.exists()) {
            try {
                plugin.saveResource(normalized, false);
                return true;
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Nie udalo sie skopiowac " + normalized + ": " + exception.getMessage());
                return false;
            }
        }

        YamlConfiguration jar = loadJarYaml(plugin, normalized);
        if (jar == null) {
            return false;
        }

        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        boolean changed = mergeMissing(live, jar, "");

        // Itemstorage: tylko brakujace klucze itemow
        if (normalized.equals("addons/itemstorage/items.yml")) {
            ConfigurationSection jarItems = jar.getConfigurationSection("serializedItems");
            if (jarItems != null) {
                for (String key : jarItems.getKeys(false)) {
                    String path = "serializedItems." + key;
                    if (!live.contains(path) || isBlank(live.getString(path))) {
                        live.set(path, jar.get(path));
                        changed = true;
                    }
                }
            }
        }

        if (!changed) {
            return false;
        }

        try {
            live.save(file);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac auto-update " + normalized + ": " + exception.getMessage());
            return false;
        }
    }

    private static boolean unlockFarmMechanics(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("farm-mechanics-unlocked", false)) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), "addons/antilag/antilag.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            live.set("disable-redstone", false);
            live.set("disable-pistons", false);
            live.set("disable-dispensers-and-droppers", false);
            live.set("disable-hoppers", false);
            live.set("blocked-place-materials", new java.util.ArrayList<String>());
            try {
                live.save(file);
                changed = true;
            } catch (Exception exception) {
                plugin.getLogger().warning("Nie udalo sie odblokowac farm w antilag.yml: " + exception.getMessage());
                return false;
            }
        }

        try {
            state.set("farm-mechanics-unlocked", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po odblokowaniu farm: " + exception.getMessage());
        }
        return changed;
    }

    private static boolean disableDeathKeep(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("death-keep-disabled-v1", false)) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), "addons/backup/backup.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            live.set("deathKeepEnabled", false);
            live.set("deathKeepPercent", 0.0);
            live.set("deathKeepBoostPercent", 0.0);
            try {
                live.save(file);
                changed = true;
            } catch (Exception exception) {
                plugin.getLogger().warning("Nie udalo sie wylaczyc deathKeep: " + exception.getMessage());
                return false;
            }
        }

        try {
            state.set("death-keep-disabled-v1", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po deathKeep: " + exception.getMessage());
        }
        return changed;
    }

    /** FancyNPC push: miękka kolizja zamiast ściany (jednorazowy patch żywego configu). */
    private static boolean patchFancyNpcPushSoft(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("fancynpc-push-soft-v1", false)) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), "addons/fancynpc-push/fancynpc-push.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            live.set("radius", 1.0D);
            live.set("min-distance", 0.72D);
            live.set("push-strength", 0.48D);
            live.set("push-y", 0.12D);
            live.set("push-cooldown-ms", 350L);
            // Stary komunikat „Nie możesz wchodzić” sugerował ścianę — wyciszamy.
            String msg = live.getString("message", "");
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("nie możesz")) {
                live.set("message", "");
            }
            try {
                live.save(file);
                changed = true;
                plugin.getLogger().info("FancyNPC-push: włączono miękkie odpychanie (bez ściany).");
            } catch (Exception exception) {
                plugin.getLogger().warning("Nie udalo sie zpatchowac fancynpc-push.yml: " + exception.getMessage());
                return false;
            }
        }

        try {
            state.set("fancynpc-push-soft-v1", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po FancyNPC push: " + exception.getMessage());
        }
        return changed;
    }

    /** FlameHub: Moc Yody 50% → 30% (jednorazowy force-patch żywego configu). */
    private static boolean patchFlameHubYoda30(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("flamehub-yoda-30-v1", false)) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), "addons/flamehub-customitems/flamehub-customitems.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            live.set("items.yoda.damage-reduction-percent", 30.0);
            List<String> lore = live.getStringList("items.yoda.lore");
            if (!lore.isEmpty()) {
                List<String> patched = new java.util.ArrayList<>(lore.size());
                for (String line : lore) {
                    if (line == null) {
                        patched.add(null);
                        continue;
                    }
                    patched.add(line
                        .replace("50%", "30%")
                        .replace("&#2ecc7150%", "&#2ecc7130%"));
                }
                live.set("items.yoda.lore", patched);
            }
            String activated = live.getString("messages.yoda-activated", "");
            if (activated != null && activated.contains("50%")) {
                live.set("messages.yoda-activated", activated.replace("50%", "30%"));
            }
            try {
                live.save(file);
                changed = true;
                plugin.getLogger().info("FlameHub: zaktualizowano Moc Yody do 30% redukcji.");
            } catch (Exception exception) {
                plugin.getLogger().warning("Nie udalo sie zpatchowac flamehub Yody: " + exception.getMessage());
                return false;
            }
        }

        try {
            state.set("flamehub-yoda-30-v1", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po FlameHub Yoda 30%: " + exception.getMessage());
        }
        return changed;
    }

    /** Dopisz klucz apply-sword-custom-model-data do upgrade.yml (domyślnie true). */
    private static boolean ensureUpgradeCustomModelToggle(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "addons/upgrade/upgrade.yml");
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        if (live.contains("apply-sword-custom-model-data")) {
            return false;
        }
        live.set("apply-sword-custom-model-data", true);
        try {
            live.save(file);
            plugin.getLogger().info("Upgrade: dopisano apply-sword-custom-model-data: true");
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie dopisac apply-sword-custom-model-data: " + exception.getMessage());
            return false;
        }
    }

    /** Diax wchodzi pod HollyDamage i bije 15% slabiej od netherite. */
    private static boolean balanceDiamondSword(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("diamond-sword-balance-v1", false)) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), "addons/hollydamage/damage.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            changed = addDiamondSword(live, "advanced");
            ConfigurationSection profiles = live.getConfigurationSection("skypvp.profiles");
            if (profiles != null) {
                for (String profile : profiles.getKeys(false)) {
                    if (addDiamondSword(live, "skypvp.profiles." + profile)) {
                        changed = true;
                    }
                }
            }
            if (changed) {
                try {
                    live.save(file);
                } catch (Exception exception) {
                    plugin.getLogger().warning("Nie udalo sie zbalansowac diaxa w damage.yml: " + exception.getMessage());
                    return false;
                }
            }
        }

        try {
            state.set("diamond-sword-balance-v1", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po balansie diaxa: " + exception.getMessage());
        }
        return changed;
    }

    /** Dokłada DIAMOND_SWORD do listy broni i mnozniki materialow — tylko tam, gdzie jest juz netherite. */
    private static boolean addDiamondSword(YamlConfiguration live, String section) {
        if (live.getBoolean(section + ".weapons-match-all", false)) {
            return false;
        }
        java.util.List<String> weapons = new java.util.ArrayList<>(live.getStringList(section + ".weapons"));
        if (!weapons.contains("NETHERITE_SWORD")) {
            return false;
        }
        boolean changed = false;
        if (!weapons.contains("DIAMOND_SWORD")) {
            weapons.add("DIAMOND_SWORD");
            live.set(section + ".weapons", weapons);
            changed = true;
        }
        if (!live.contains(section + ".weapon-multipliers")) {
            live.set(section + ".weapon-multipliers.NETHERITE_SWORD", 1.0);
            live.set(section + ".weapon-multipliers.DIAMOND_SWORD", 0.85);
            changed = true;
        }
        return changed;
    }

    private static boolean applyEntityLimits(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("entity-limits-v1", false)) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), "addons/antilag/antilag.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            live.set("max-armor-stands-per-chunk", 4);
            live.set("max-minecarts-per-chunk", 2);
            try {
                live.save(file);
                changed = true;
            } catch (Exception exception) {
                plugin.getLogger().warning("Nie udalo sie ustawic limitow entity w antilag.yml: " + exception.getMessage());
                return false;
            }
        }

        try {
            state.set("entity-limits-v1", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po limitach entity: " + exception.getMessage());
        }
        return changed;
    }

    private static boolean ensureHomeEnabled(JavaPlugin plugin) {
        File file = HollyDataPaths.getRootFile(plugin, "addons.yml");
        if (!file.exists()) {
            return false;
        }
        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        if (!live.getBoolean("addons.home", false)) {
            live.set("addons.home", true);
            changed = true;
        }
        if (!changed) {
            return false;
        }
        try {
            live.save(file);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie wlaczyc home w addons.yml: " + exception.getMessage());
            return false;
        }
    }

    private static boolean allowDrowning(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("drowning-allowed-v1", false)) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), "addons/core/core.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            java.util.List<String> causes = new java.util.ArrayList<>(live.getStringList("no-damage.causes"));
            if (causes.removeIf(c -> c != null && c.equalsIgnoreCase("DROWNING"))) {
                live.set("no-damage.causes", causes);
                try {
                    live.save(file);
                    changed = true;
                } catch (Exception exception) {
                    plugin.getLogger().warning("Nie udalo sie odblokowac topienia w core.yml: " + exception.getMessage());
                    return false;
                }
            }
        }

        try {
            state.set("drowning-allowed-v1", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po topieniu: " + exception.getMessage());
        }
        return changed;
    }

    /** Przy MSLF /ec ma byc lekkim EC — wylacz ciezki addon enderchest na live. */
    private static boolean preferMslfEnderchest(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("mslf-owns-ec-v1", false)) {
            return false;
        }

        File file = HollyDataPaths.getRootFile(plugin, "addons.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            boolean mslf = live.getBoolean("addons.mslf", false);
            if (mslf && live.getBoolean("addons.enderchest", true)) {
                live.set("addons.enderchest", false);
                try {
                    live.save(file);
                    changed = true;
                    plugin.getLogger().info("MSLF: wylaczono addon enderchest — /ec = lekki EC MSLF.");
                } catch (Exception exception) {
                    plugin.getLogger().warning("Nie udalo sie wylaczyc enderchest dla MSLF: " + exception.getMessage());
                    return false;
                }
            }
        }

        try {
            state.set("mslf-owns-ec-v1", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po MSLF EC: " + exception.getMessage());
        }
        return changed;
    }

    private static boolean ensureAddonToggles(JavaPlugin plugin) {
        File file = HollyDataPaths.getRootFile(plugin, "addons.yml");
        if (!file.exists()) {
            HollyDataPaths.ensureResource(plugin, "addons.yml");
            return file.exists();
        }

        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration jar = loadJarYaml(plugin, "addons.yml");
        boolean changed = false;

        Map<String, Boolean> required = new LinkedHashMap<>();
        required.put("boss", true);
        required.put("report", true);
        required.put("itemstorage", true);
        required.put("itemshop", true);
        required.put("lockedfeatures", true);
        required.put("patroni", true);
        required.put("armorstars", true);
        required.put("home", true);
        required.put("elytra", true);
        required.put("czek", true);
        required.put("event", true);
        required.put("titles", true);
        required.put("unique", true);
        required.put("wallet", true);
        required.put("spawn", true);
        required.put("antilag", true);
        required.put("flamehub-customitems", true);
        required.put("killeffect", true);

        for (Map.Entry<String, Boolean> entry : required.entrySet()) {
            String path = "addons." + entry.getKey();
            if (!live.contains(path)) {
                boolean value = jar != null && jar.contains(path) ? jar.getBoolean(path) : entry.getValue();
                live.set(path, value);
                changed = true;
            }
        }

        if (!changed) {
            return false;
        }
        try {
            live.save(file);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac addons.yml auto-update: " + exception.getMessage());
            return false;
        }
    }

    private static boolean mergeMissing(YamlConfiguration live, YamlConfiguration jar, String prefix) {
        boolean changed = false;
        for (String key : jar.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object jarValue = jar.get(key);

            if (jarValue instanceof ConfigurationSection jarSection) {
                if (!live.isConfigurationSection(path)) {
                    if (!live.contains(path)) {
                        live.createSection(path);
                        changed |= copySection(live, path, jarSection);
                    }
                } else {
                    ConfigurationSection liveSection = live.getConfigurationSection(path);
                    if (liveSection != null) {
                        changed |= mergeMissingSection(live, liveSection, jarSection, path);
                    }
                }
                continue;
            }

            if (!live.contains(path)) {
                live.set(path, jarValue);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean mergeMissingSection(
        YamlConfiguration root,
        ConfigurationSection live,
        ConfigurationSection jar,
        String prefix
    ) {
        boolean changed = false;
        for (String key : jar.getKeys(false)) {
            String path = prefix + "." + key;
            Object jarValue = jar.get(key);

            if (jarValue instanceof ConfigurationSection jarSection) {
                if (!live.isConfigurationSection(key)) {
                    if (!live.contains(key)) {
                        live.createSection(key);
                        changed |= copySection(root, path, jarSection);
                    }
                } else {
                    ConfigurationSection liveSection = live.getConfigurationSection(key);
                    if (liveSection != null) {
                        changed |= mergeMissingSection(root, liveSection, jarSection, path);
                    }
                }
                continue;
            }

            if (!live.contains(key)) {
                root.set(path, jarValue);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean copySection(YamlConfiguration root, String path, ConfigurationSection section) {
        boolean changed = false;
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) {
                continue;
            }
            root.set(path + "." + key, section.get(key));
            changed = true;
        }
        return changed;
    }

    /**
     * Scoreboard miał periodic-refresh:false + update-seconds:8 — placeholdery stały długo.
     * Jednorazowo wymuszamy odświeżanie co 3s.
     */
    private static boolean fasterScoreboardRefresh(JavaPlugin plugin) {
        File stateFile = HollyDataPaths.getRootFile(plugin, STATE_FILE);
        YamlConfiguration state = stateFile.exists()
            ? YamlConfiguration.loadConfiguration(stateFile)
            : new YamlConfiguration();
        if (state.getBoolean("scoreboard-refresh-3s-v1", false)) {
            return false;
        }

        File file = HollyDataPaths.getRootFile(plugin, "addons/tablist/tablist.yml");
        boolean changed = false;
        if (file.exists()) {
            YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
            String root = live.isConfigurationSection("scoreboard") ? "scoreboard." : "";
            live.set(root + "update-seconds", 3);
            live.set(root + "update-batch-size", Math.max(24, live.getInt(root + "update-batch-size", 8)));
            live.set(root + "periodic-refresh", true);
            try {
                live.save(file);
                changed = true;
                plugin.getLogger().info("Scoreboard: update-seconds=3, periodic-refresh=true.");
            } catch (Exception exception) {
                plugin.getLogger().warning("Nie udalo sie przyspieszyc scoreboardu: " + exception.getMessage());
                return false;
            }
        }

        try {
            state.set("scoreboard-refresh-3s-v1", true);
            state.save(stateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac config-update.yml po scoreboard refresh: " + exception.getMessage());
        }
        return changed;
    }

    private static YamlConfiguration loadJarYaml(JavaPlugin plugin, String resourcePath) {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie odczytac JAR resource " + resourcePath + ": " + exception.getMessage());
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
