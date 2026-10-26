package dev.grzybalson.modules.oghubzmianki;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiCommand;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiDamageListener;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiGUIHandler;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiManager;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiUseListener;
import dev.grzybalson.anarchiagg.zmiankibattle.ZmiankaBattle;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

/**
 * Samodzielny addon zmianek OGHub (/zmianki, /azmianki).
 * Nie zalezy od modulu anarchiagg.zmianki.
 */
public class OgHubZmiankiManager {
    public static final String ADDON_NAME = "oghubzmianki";
    private static final String CONFIG_RESOURCE = "addons/oghubzmianki/oghubzmianki.yml";

    private final HollyCore plugin;
    private final File configFile;
    private final ZmiankiManager zmiankiManager;
    private final ZmiankaBattle zmiankaBattle;

    public OgHubZmiankiManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, ADDON_NAME), "oghubzmianki.yml");
        ensureResources();

        this.zmiankiManager = new ZmiankiManager(plugin, configFile);
        ZmiankiGUIHandler guiHandler = new ZmiankiGUIHandler(zmiankiManager);

        this.zmiankaBattle = new ZmiankaBattle(plugin);
        this.zmiankaBattle.onEnable();

        ZmiankiCommand command = new ZmiankiCommand(zmiankiManager, guiHandler, zmiankaBattle.getCommandHandler());
        registerCommand("zmianki", command, command);
        registerCommand("azmianki", command, command);
        if (zmiankaBattle.getCommandHandler() != null && plugin.getCommand("zmiankibattle") != null) {
            plugin.getCommand("zmiankibattle").setExecutor(zmiankaBattle.getCommandHandler());
            plugin.getCommand("zmiankibattle").setTabCompleter(zmiankaBattle.getCommandHandler());
        }

        plugin.getServer().getPluginManager().registerEvents(guiHandler, plugin);
        plugin.getServer().getPluginManager().registerEvents(new ZmiankiUseListener(zmiankiManager, guiHandler), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ZmiankiDamageListener(zmiankiManager), plugin);
        plugin.getLogger().info("[oghubzmianki] Addon zmianek wlaczony (/zmianki, /azmianki).");
    }

    public void reload() {
        ensureResources();
        zmiankiManager.reload();
        if (zmiankaBattle != null) {
            zmiankaBattle.loadConfig();
        }
    }

    public void disable() {
        if (zmiankaBattle != null) {
            zmiankaBattle.onDisable();
        }
    }

    public ZmiankiManager getZmiankiManager() {
        return zmiankiManager;
    }

    private void ensureResources() {
        File folder = configFile.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("[oghubzmianki] Nie udalo sie utworzyc folderu: " + folder.getAbsolutePath());
        }
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource(CONFIG_RESOURCE)) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                } else {
                    configFile.createNewFile();
                }
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[oghubzmianki] Nie udalo sie skopiowac configu", exception);
            }
        }
        mergeMissingDefaults();
    }

    private void mergeMissingDefaults() {
        try (InputStream in = plugin.getResource(CONFIG_RESOURCE)) {
            if (in == null || !configFile.exists()) {
                return;
            }
            org.bukkit.configuration.file.YamlConfiguration disk =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
            org.bukkit.configuration.file.YamlConfiguration defaults =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!disk.contains(key) && !defaults.isConfigurationSection(key)) {
                    disk.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                disk.save(configFile);
            }
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "[oghubzmianki] Merge defaults failed", exception);
        }
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("[oghubzmianki] Brak komendy " + name + " w plugin.yml");
            return;
        }
        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
