package dev.grzybalson.modules.oghubszafka;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.szafa.BoostHandler;
import dev.grzybalson.anarchiagg.szafa.ItemCommand;
import dev.grzybalson.anarchiagg.szafa.RegionHandler;
import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIHandler;
import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIListener;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyArmorHandler;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyDatabase;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Samodzielna szafka OGHub - tylko kostiumy, bez petow i papug.
 * Korzysta z klas szafki anarchiagg, ale trzyma wlasny config, wlasna kolekcje w Mongo
 * i wlasny wyglad GUI.
 */
public class OgHubSzafkaManager {
    public static final String ADDON_NAME = "oghub-szafka";
    private static final String CONFIG_RESOURCE = "addons/oghub-szafka/oghub-szafka.yml";
    private static final String GUI_RESOURCE = "addons/oghub-szafka/szafka.yml";
    private static final String COLLECTION_NAME = "oghub_szafka_kostiumy";

    private final HollyCore plugin;
    private final File configFile;

    private final YamlConfiguration config;
    private final KostiumyDatabase kostiumyDatabase;
    private final KostiumyHandler kostiumyHandler;
    private final KostiumyArmorHandler armorHandler;
    private final SzafaGUIHandler guiHandler;
    private final BoostHandler boostHandler;
    private final RegionHandler regionHandler;

    public OgHubSzafkaManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, ADDON_NAME), ADDON_NAME + ".yml");
        ensureResources();
        this.config = YamlConfiguration.loadConfiguration(this.configFile);

        this.kostiumyDatabase = new KostiumyDatabase(plugin, COLLECTION_NAME);
        this.kostiumyHandler = new KostiumyHandler(plugin, this.kostiumyDatabase, this.config, this.configFile);

        this.armorHandler = new KostiumyArmorHandler(plugin, this.kostiumyHandler);
        plugin.getServer().getPluginManager().registerEvents(this.armorHandler, plugin);

        this.guiHandler = new SzafaGUIHandler(this.kostiumyHandler, null, null, plugin, this.config, GUI_RESOURCE, true);
        plugin.getServer().getPluginManager().registerEvents(new SzafaGUIListener(this.guiHandler, this.armorHandler, null, null), plugin);

        this.regionHandler = new RegionHandler(plugin, this.config);
        this.boostHandler = new BoostHandler(plugin, this.kostiumyHandler, null, null, this.regionHandler);
        plugin.getServer().getPluginManager().registerEvents(this.boostHandler, plugin);

        ItemCommand itemCommand = new ItemCommand(plugin, this.kostiumyHandler, null, null, this.guiHandler);
        registerCommand("aszafka", itemCommand, itemCommand);
        registerCommand("szafka", (sender, command, label, args) -> {
            if (sender instanceof Player player) {
                this.guiHandler.openGUI(player);
                return true;
            }
            return false;
        }, null);
    }

    private void ensureResources() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        HollyDataPaths.ensureResource(plugin, GUI_RESOURCE);
        mergeMissingCostumeKeys();
    }

    /**
     * Dopisuje brakujące kostiumy/itemy z JAR (np. ninja, goat) do istniejącego runtime YAML,
     * bez nadpisywania lokalnych zmian. Ścieżki są pod rootem {@code szafka.*}.
     */
    private void mergeMissingCostumeKeys() {
        if (!configFile.exists()) {
            return;
        }
        YamlConfiguration runtime = YamlConfiguration.loadConfiguration(configFile);
        YamlConfiguration resource;
        try (InputStream stream = plugin.getResource(CONFIG_RESOURCE)) {
            if (stream == null) {
                return;
            }
            resource = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[oghub-szafka] Nie udalo sie wczytac szablonu do merge.", exception);
            return;
        }

        boolean changed = false;
        // Kostiumy siedzą pod szafka.kostiumy / szafka.kostium-items (nie na rootcie YAML).
        String[][] sections = {
            {dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("kostiumy"), "szafka.kostiumy", "kostiumy"},
            {dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("kostium-items"), "szafka.kostium-items", "kostium-items"}
        };
        for (String[] candidates : sections) {
            String resourcePath = null;
            ConfigurationSection resourceSection = null;
            for (String candidate : candidates) {
                resourceSection = resource.getConfigurationSection(candidate);
                if (resourceSection != null) {
                    resourcePath = candidate;
                    break;
                }
            }
            if (resourceSection == null || resourcePath == null) {
                continue;
            }

            String runtimeRoot = null;
            for (String candidate : candidates) {
                if (runtime.getConfigurationSection(candidate) != null || runtime.contains(candidate)) {
                    runtimeRoot = candidate;
                    break;
                }
            }
            if (runtimeRoot == null) {
                runtimeRoot = candidates[0];
            }

            for (String key : resourceSection.getKeys(false)) {
                String path = runtimeRoot + "." + key;
                if (runtime.contains(path)) {
                    continue;
                }
                ConfigurationSection from = resourceSection.getConfigurationSection(key);
                if (from == null) {
                    runtime.set(path, resource.get(resourcePath + "." + key));
                } else {
                    for (String deepKey : from.getKeys(true)) {
                        if (!from.isConfigurationSection(deepKey)) {
                            runtime.set(path + "." + deepKey, from.get(deepKey));
                        }
                    }
                }
                changed = true;
                plugin.getLogger().info("[oghub-szafka] Dopisano brakujacy klucz: " + path);
            }
        }

        if (!changed) {
            // nadal odswiez lore ninja/goat (stare "w stylu ucznia")
        } else {
            try {
                runtime.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[oghub-szafka] Nie udalo sie zapisac merge kostiumow.", exception);
            }
            runtime = YamlConfiguration.loadConfiguration(configFile);
        }

        if (refreshCostumeDisplay(runtime, resource, "ninja") | refreshCostumeDisplay(runtime, resource, "goat")) {
            try {
                runtime.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[oghub-szafka] Nie udalo sie zapisac lore kostiumow.", exception);
            }
        }
    }

    /** Nadpisuje displayname/lore z JAR dla wskazanego kostiumu (pod szafka.kostiumy). */
    private boolean refreshCostumeDisplay(YamlConfiguration runtime, YamlConfiguration resource, String key) {
        String[] roots = {
            dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("kostiumy"),
            "szafka.kostiumy",
            "kostiumy"
        };
        String runtimePath = null;
        String resourcePath = null;
        for (String root : roots) {
            if (runtime.contains(root + "." + key)) {
                runtimePath = root + "." + key;
            }
            if (resource.contains(root + "." + key)) {
                resourcePath = root + "." + key;
            }
        }
        if (runtimePath == null || resourcePath == null) {
            return false;
        }
        List<String> newLore = resource.getStringList(resourcePath + ".lore");
        String newName = resource.getString(resourcePath + ".displayname");
        List<String> oldLore = runtime.getStringList(runtimePath + ".lore");
        boolean needsUpdate = oldLore.stream().anyMatch(line -> line != null && line.toLowerCase().contains("stylu"))
            || !oldLore.equals(newLore);
        if (!needsUpdate && (newName == null || newName.equals(runtime.getString(runtimePath + ".displayname")))) {
            return false;
        }
        if (newName != null) {
            runtime.set(runtimePath + ".displayname", newName);
        }
        if (!newLore.isEmpty()) {
            runtime.set(runtimePath + ".lore", newLore);
        }
        plugin.getLogger().info("[oghub-szafka] Odswiezono lore/displayname: " + runtimePath);
        return true;
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("[oghub-szafka] Nie mozna zarejestrowac komendy /" + name + ", bo nie ma jej w plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
    }

    public void reload() {
        ensureResources();
        // KostiumyHandler wczytuje plik w miejscu, wiec wspoldzielony obiekt config zostaje aktualny.
        if (this.kostiumyHandler != null) {
            this.kostiumyHandler.reload();
        }
        if (this.regionHandler != null) {
            this.regionHandler.reload();
        }
        if (this.armorHandler != null) {
            this.armorHandler.clearVisualCache();
        }
        if (this.guiHandler != null) {
            this.guiHandler.reload();
        }
    }

    public void disable() {
        BoostHandler.onDisable();
        KostiumyArmorHandler.cleanupArmorOnDisable();
        if (this.kostiumyDatabase != null) {
            this.kostiumyDatabase.close();
        }
    }

    public KostiumyHandler getKostiumyHandler() {
        return this.kostiumyHandler;
    }

    public SzafaGUIHandler getGuiHandler() {
        return this.guiHandler;
    }

    public BoostHandler getBoostHandler() {
        return this.boostHandler;
    }
}
