package dev.grzybalson.modules.masakracustomitems;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Supplier;

/** The editable root config is authoritative; updates only add missing utility abilities. */
public final class MasakraConfigStore {
    private final File file;
    private final File legacyFile;
    private final Supplier<InputStream> defaults;

    public MasakraConfigStore(File dataFolder, Supplier<InputStream> defaults) {
        file = new File(dataFolder, "masakra-customitems.yml");
        legacyFile = new File(dataFolder, "addons/masakra-customitems/masakra-customitems.yml");
        this.defaults = defaults;
    }

    public File getFile() { return file; }

    public void ensureFile() throws IOException, InvalidConfigurationException {
        if (file.exists()) return;
        Files.createDirectories(file.toPath().getParent());
        if (legacyFile.isFile()) {
            // Check syntax before migrating. Never replace an unreadable file with defaults.
            YamlConfiguration legacy = new YamlConfiguration();
            legacy.load(legacyFile);
            Files.move(legacyFile.toPath(), file.toPath());
        } else {
            try (InputStream input = Objects.requireNonNull(defaults.get(), "Missing bundled Masakra config")) {
                Files.copy(input, file.toPath());
            }
        }
    }

    public YamlConfiguration load() throws IOException, InvalidConfigurationException {
        ensureFile();
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file);
        // Add only the three new abilities; preserve every existing item and edited value.
        boolean changed = false;
        try (var reader = new InputStreamReader(Objects.requireNonNull(defaults.get()), StandardCharsets.UTF_8)) {
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(reader);
            for (String id : List.of("usuwacz-pajeczyn", "torba-misako", "kwiat-lotosu")) {
                String path = "items." + id;
                if (!loaded.contains(path) && bundled.isConfigurationSection(path)) {
                    for (var entry : bundled.getConfigurationSection(path).getValues(true).entrySet()) {
                        if (!(entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection)) {
                            loaded.set(path + "." + entry.getKey(), entry.getValue());
                        }
                    }
                    changed = true;
                }
            }
            String bag = "items.torba-misako.";
            if (loaded.isConfigurationSection("items.torba-misako") && loaded.getInt(bag + "color-version", 0) < 1
                && bundled.isConfigurationSection("items.torba-misako")) {
                if (loaded.getString(bag + "material", "").equals("RABBIT_HIDE")) {
                    loaded.set(bag + "material", bundled.getString(bag + "material"));
                }
                String oldName = "&#E9B5FF&l\u1d1b&#E0A5FF&l\u1d0f&#D695FF&l\u0280&#CD84FF&l\u0299&#C374FF&l\u1d00 &#BB66FF&l\u1d0d&#C67AFF&l\u026a&#D18EFF&l\ua731&#DCA1FF&l\u1d00&#E7B5FF&l\u1d0b&#F2C9FF&l\u1d0f";
                String currentName = loaded.getString(bag + "name", "");
                if (currentName.equals(oldName) || currentName.equals("&#EAA2FF&lTorba Misako")) {
                    loaded.set(bag + "name", bundled.getString(bag + "name"));
                }
                loaded.set(bag + "lore", loaded.getStringList(bag + "lore").stream()
                    .map(line -> line.replace("&#E9B5FF", "&#72FFF0")).toList());
                loaded.set(bag + "gui-title", loaded.getString(bag + "gui-title", "&8Torba Misako")
                    .replace("&d", "&#72FFF0"));
                loaded.set(bag + "color-version", 1);
                changed = true;
            }
            String usage = loaded.getString("messages.usage", "");
            if (!usage.contains("resetcooldown") && usage.contains("give <nick> <id>")) {
                loaded.set("messages.usage", usage.replace("give <nick> <id>", "give <nick> <id>|resetcooldown <nick|all>"));
                changed = true;
            }
            var messages = bundled.getConfigurationSection("messages");
            if (messages != null) for (String key : messages.getKeys(false)) {
                if (!(key.startsWith("bag-") || key.startsWith("lotus-") || key.startsWith("web-") || key.startsWith("cooldown-reset-"))) continue;
                if (!loaded.contains("messages." + key)) {
                    loaded.set("messages." + key, bundled.get("messages." + key));
                    changed = true;
                }
            }
        }
        if (changed) save(loaded);
        return loaded;
    }

    public void save(YamlConfiguration config) throws IOException { config.save(file); }
}
