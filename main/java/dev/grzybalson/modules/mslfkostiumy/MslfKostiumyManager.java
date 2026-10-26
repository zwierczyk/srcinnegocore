package dev.grzybalson.modules.mslfkostiumy;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.szafa.BoostHandler;
import dev.grzybalson.anarchiagg.szafa.ItemCommand;
import dev.grzybalson.anarchiagg.szafa.RegionHandler;
import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIHandler;
import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIListener;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyArmorHandler;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyDatabase;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyDatabase;
import dev.grzybalson.anarchiagg.szafa.pety.PetyHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyVisualHandler;
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
import java.util.logging.Level;

/**
 * Szafka MSLF — kostiumy + pety; wlasny config, Mongo i GUI.
 */
public class MslfKostiumyManager {
    public static final String ADDON_NAME = "mslf-kostiumy";
    private static final String CONFIG_RESOURCE = "addons/mslf-kostiumy/mslf-kostiumy.yml";
    private static final String GUI_RESOURCE = "guis/szafka.yml";
    private static final String KOSTIUMY_COLLECTION = "mslf_kostiumy";
    private static final String PETY_COLLECTION = "mslf_pety";

    private final HollyCore plugin;
    private final File configFile;

    private final YamlConfiguration config;
    private final KostiumyDatabase kostiumyDatabase;
    private final KostiumyHandler kostiumyHandler;
    private final KostiumyArmorHandler armorHandler;
    private final PetyDatabase petyDatabase;
    private final PetyHandler petyHandler;
    private final PetyVisualHandler petyVisualHandler;
    private final SzafaGUIHandler guiHandler;
    private final BoostHandler boostHandler;
    private final RegionHandler regionHandler;
    private final MslfPetsEffectsListener petsEffectsListener;

    public MslfKostiumyManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, ADDON_NAME), ADDON_NAME + ".yml");
        ensureResources();
        this.config = YamlConfiguration.loadConfiguration(this.configFile);

        this.kostiumyDatabase = new KostiumyDatabase(plugin, KOSTIUMY_COLLECTION);
        this.kostiumyHandler = new KostiumyHandler(plugin, this.kostiumyDatabase, this.config, this.configFile);

        this.petyDatabase = new PetyDatabase(plugin, PETY_COLLECTION);
        this.petyHandler = new PetyHandler(plugin, this.petyDatabase, this.config, this.configFile);

        if (plugin.isAddonEnabled("oghub-szafka")) {
            plugin.getLogger().warning(
                "[mslf-kostiumy] Addon oghub-szafka jest tez wlaczony — "
                    + "oba share'uja KostiumyArmorHandler (singleton)."
            );
        }

        this.armorHandler = new KostiumyArmorHandler(plugin, this.kostiumyHandler);
        plugin.getServer().getPluginManager().registerEvents(this.armorHandler, plugin);

        this.guiHandler = new SzafaGUIHandler(
            this.kostiumyHandler, this.petyHandler, null, plugin, this.config, GUI_RESOURCE, false
        );
        this.petyVisualHandler = new PetyVisualHandler(plugin, this.petyHandler, this.guiHandler);
        plugin.getServer().getPluginManager().registerEvents(this.petyVisualHandler, plugin);
        plugin.getServer().getPluginManager().registerEvents(
            new SzafaGUIListener(this.guiHandler, this.armorHandler, this.petyVisualHandler, null),
            plugin
        );

        this.regionHandler = new RegionHandler(plugin, this.config);
        this.boostHandler = new BoostHandler(plugin, this.kostiumyHandler, this.petyHandler, null, this.regionHandler);
        plugin.getServer().getPluginManager().registerEvents(this.boostHandler, plugin);

        plugin.getServer().getPluginManager().registerEvents(
            new MslfKostiumyEffectsListener(plugin, this.kostiumyHandler),
            plugin
        );
        this.petsEffectsListener = new MslfPetsEffectsListener(plugin, this.petyHandler);
        plugin.getServer().getPluginManager().registerEvents(this.petsEffectsListener, plugin);

        ItemCommand itemCommand = new ItemCommand(plugin, this.kostiumyHandler, this.petyHandler, null, this.guiHandler);
        registerCommand("amkostiumy", itemCommand, itemCommand);
        registerCommand("aszafka", itemCommand, itemCommand);
        CommandExecutor openGui = (sender, command, label, args) -> {
            if (sender instanceof Player player) {
                this.guiHandler.openGUI(player);
                return true;
            }
            return false;
        };
        registerCommand("mkostiumy", openGui, null);
        registerCommand("szafka", openGui, null);
        // AnarchiaGG.szafka=false zdejmuje /szafka z CommandMap przed startem addonu — przywroc.
        plugin.restorePluginCommands(java.util.List.of("szafka", "aszafka", "mkostiumy", "amkostiumy"));

        plugin.getLogger().info("[mslf-kostiumy] Addon kostiumow+petow wlaczony (/szafka, /kostium, /amkostiumy).");
    }

    private void ensureResources() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        // Layout GUI z guis/szafka.yml — zawsze nadpisujemy z JAR (jak inne GUIs).
        forceCopyResource(GUI_RESOURCE);
        // Legacy path (stary addon) — usun zeby nie mylil.
        File legacy = new File(plugin.getDataFolder(), "addons/mslf-kostiumy/szafka.yml");
        if (legacy.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            legacy.delete();
        }
        mergeMissingKeys();
    }

    private void forceCopyResource(String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath.replace('\\', '/'));
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (InputStream stream = plugin.getResource(resourcePath.replace('\\', '/'))) {
            if (stream == null) {
                return;
            }
            java.nio.file.Files.copy(stream, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[mslf-kostiumy] Nie udalo sie nadpisac " + resourcePath, exception);
        }
    }

    private void mergeMissingKeys() {
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
            plugin.getLogger().log(Level.WARNING, "[mslf-kostiumy] Nie udalo sie wczytac szablonu do merge.", exception);
            return;
        }

        boolean changed = false;
        String[][] sections = {
            {dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("kostiumy"), "szafka.kostiumy", "kostiumy"},
            {dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("kostium-items"), "szafka.kostium-items", "kostium-items"},
            {dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("pety"), "szafka.pety", "pety"},
            {dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("pet-items"), "szafka.pet-items", "pet-items"}
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
                plugin.getLogger().info("[mslf-kostiumy] Dopisano brakujacy klucz: " + path);
            }
        }

        if (changed) {
            try {
                runtime.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[mslf-kostiumy] Nie udalo sie zapisac merge.", exception);
            }
        }

        // Aktualizuj head-value / displayname petow i kostiumow z JAR (layout glow).
        refreshPetVisualsFromResource(runtime, resource);
        refreshKostiumVisualsFromResource(runtime, resource);
    }

    private void refreshPetVisualsFromResource(YamlConfiguration runtime, YamlConfiguration resource) {
        String[] petRoots = {
            dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("pety"), "szafka.pety", "pety"
        };
        String[] itemRoots = {
            dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("pet-items"), "szafka.pet-items", "pet-items"
        };
        String resourcePety = null;
        for (String candidate : petRoots) {
            if (resource.getConfigurationSection(candidate) != null) {
                resourcePety = candidate;
                break;
            }
        }
        String runtimePety = null;
        for (String candidate : petRoots) {
            if (runtime.getConfigurationSection(candidate) != null) {
                runtimePety = candidate;
                break;
            }
        }
        String resourceItems = null;
        for (String candidate : itemRoots) {
            if (resource.getConfigurationSection(candidate) != null) {
                resourceItems = candidate;
                break;
            }
        }
        String runtimeItems = null;
        for (String candidate : itemRoots) {
            if (runtime.getConfigurationSection(candidate) != null) {
                runtimeItems = candidate;
                break;
            }
        }
        if (resourcePety == null || runtimePety == null) {
            return;
        }

        boolean changed = false;
        ConfigurationSection resourceSection = resource.getConfigurationSection(resourcePety);
        for (String key : resourceSection.getKeys(false)) {
            String dn = resource.getString(resourcePety + "." + key + ".displayname");
            if (dn != null) {
                runtime.set(runtimePety + "." + key + ".displayname", dn);
                changed = true;
            }
            if (resource.isList(resourcePety + "." + key + ".lore")) {
                runtime.set(runtimePety + "." + key + ".lore", resource.getStringList(resourcePety + "." + key + ".lore"));
                changed = true;
            }
            String head = resource.getString(resourcePety + "." + key + ".display-item.head-value");
            String mat = resource.getString(resourcePety + "." + key + ".display-item.material");
            String name = resource.getString(resourcePety + "." + key + ".display-item.name");
            if (mat != null) {
                runtime.set(runtimePety + "." + key + ".display-item.material", mat);
                changed = true;
            }
            if (head != null) {
                runtime.set(runtimePety + "." + key + ".display-item.head-value", head);
                changed = true;
            }
            if (name != null) {
                runtime.set(runtimePety + "." + key + ".display-item.name", name);
                changed = true;
            }
            if (resource.isList(resourcePety + "." + key + ".display-item.lore")) {
                runtime.set(
                    runtimePety + "." + key + ".display-item.lore",
                    resource.getStringList(resourcePety + "." + key + ".display-item.lore")
                );
                changed = true;
            }
            if (runtimeItems != null && resourceItems != null) {
                String itemHead = resource.getString(resourceItems + "." + key + ".head-value");
                String itemMat = resource.getString(resourceItems + "." + key + ".material");
                String itemName = resource.getString(resourceItems + "." + key + ".name");
                if (itemMat != null) {
                    runtime.set(runtimeItems + "." + key + ".material", itemMat);
                    changed = true;
                }
                if (itemHead != null) {
                    runtime.set(runtimeItems + "." + key + ".head-value", itemHead);
                    changed = true;
                }
                if (itemName != null) {
                    runtime.set(runtimeItems + "." + key + ".name", itemName);
                    changed = true;
                }
            }
        }
        if (changed) {
            try {
                runtime.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[mslf-kostiumy] Nie udalo sie zapisac refresh pet visuals.", exception);
            }
        }
    }

    private void refreshKostiumVisualsFromResource(YamlConfiguration runtime, YamlConfiguration resource) {
        String[] roots = {
            dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("kostiumy"), "szafka.kostiumy", "kostiumy"
        };
        String resourceRoot = null;
        String runtimeRoot = null;
        for (String candidate : roots) {
            if (resourceRoot == null && resource.getConfigurationSection(candidate) != null) {
                resourceRoot = candidate;
            }
            if (runtimeRoot == null && runtime.getConfigurationSection(candidate) != null) {
                runtimeRoot = candidate;
            }
        }
        if (resourceRoot == null || runtimeRoot == null) {
            return;
        }
        boolean changed = false;
        ConfigurationSection resourceSection = resource.getConfigurationSection(resourceRoot);
        for (String key : resourceSection.getKeys(false)) {
            String dn = resource.getString(resourceRoot + "." + key + ".displayname");
            if (dn != null) {
                runtime.set(runtimeRoot + "." + key + ".displayname", dn);
                changed = true;
            }
            if (resource.isList(resourceRoot + "." + key + ".lore")) {
                runtime.set(runtimeRoot + "." + key + ".lore", resource.getStringList(resourceRoot + "." + key + ".lore"));
                changed = true;
            }
            String color = resource.getString(resourceRoot + "." + key + ".color");
            if (color != null) {
                runtime.set(runtimeRoot + "." + key + ".color", color);
                changed = true;
            }
            String head = resource.getString(resourceRoot + "." + key + ".head-value");
            if (head != null && !head.isBlank()) {
                runtime.set(runtimeRoot + "." + key + ".head-value", head);
                changed = true;
            }
        }

        String[] itemRoots = {
            dev.grzybalson.anarchiagg.szafa.SzafkaConfig.path("kostium-items"), "szafka.kostium-items", "kostium-items"
        };
        String resourceItems = null;
        String runtimeItems = null;
        for (String candidate : itemRoots) {
            if (resourceItems == null && resource.getConfigurationSection(candidate) != null) {
                resourceItems = candidate;
            }
            if (runtimeItems == null && runtime.getConfigurationSection(candidate) != null) {
                runtimeItems = candidate;
            }
        }
        if (resourceItems != null && runtimeItems != null) {
            ConfigurationSection resourceItemSection = resource.getConfigurationSection(resourceItems);
            for (String itemKey : resourceItemSection.getKeys(false)) {
                String itemHead = resource.getString(resourceItems + "." + itemKey + ".head-value");
                if (itemHead != null && !itemHead.isBlank()) {
                    runtime.set(runtimeItems + "." + itemKey + ".head-value", itemHead);
                    changed = true;
                }
            }
        }
        if (changed) {
            try {
                runtime.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[mslf-kostiumy] Nie udalo sie zapisac refresh kostium visuals.", exception);
            }
        }
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("[mslf-kostiumy] Nie mozna zarejestrowac komendy /" + name + ", bo nie ma jej w plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
    }

    public void reload() {
        ensureResources();
        if (this.kostiumyHandler != null) {
            this.kostiumyHandler.reload();
        }
        if (this.petyHandler != null) {
            this.petyHandler.reload();
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
        if (this.petyDatabase != null) {
            this.petyDatabase.close();
        }
    }

    public KostiumyHandler getKostiumyHandler() {
        return this.kostiumyHandler;
    }

    public PetyHandler getPetyHandler() {
        return this.petyHandler;
    }

    public MslfPetsEffectsListener getPetsEffectsListener() {
        return this.petsEffectsListener;
    }

    public SzafaGUIHandler getGuiHandler() {
        return this.guiHandler;
    }

    public BoostHandler getBoostHandler() {
        return this.boostHandler;
    }
}
