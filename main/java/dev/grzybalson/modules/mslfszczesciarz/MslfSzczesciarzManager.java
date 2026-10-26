package dev.grzybalson.modules.mslfszczesciarz;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiCommand;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiDamageListener;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiGUIHandler;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiUseListener;
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
 * Addon mslf-szczesciarz — kopia zmianek pod LF (koniczynka = roll %).
 */
public final class MslfSzczesciarzManager {

    public static final String ADDON_NAME = "mslf-szczesciarz";
    private static final String CONFIG_RESOURCE = "addons/mslf-szczesciarz/mslf-szczesciarz.yml";

    private final HollyCore plugin;
    private final File configFile;
    private final SzczesciarzManager zmiankiManager;
    private final ZmiankiGUIHandler guiHandler;

    public MslfSzczesciarzManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, ADDON_NAME), "mslf-szczesciarz.yml");
        ensureResources();

        this.zmiankiManager = new SzczesciarzManager(plugin, configFile);
        this.guiHandler = new ZmiankiGUIHandler(zmiankiManager);

        ZmiankiCommand command = new ZmiankiCommand(zmiankiManager, guiHandler, null);
        registerCommand("szczesciarz", command, command);
        registerCommand("aszczesciarz", command, command);

        plugin.getServer().getPluginManager().registerEvents(guiHandler, plugin);
        plugin.getServer().getPluginManager().registerEvents(new ZmiankiUseListener(zmiankiManager, guiHandler), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ZmiankiDamageListener(zmiankiManager), plugin);
        plugin.getLogger().info("[mslf-szczesciarz] Addon szczesciarza wlaczony (/szczesciarz, /aszczesciarz).");
    }

    public void reload() {
        ensureResources();
        zmiankiManager.reload();
    }

    public void openGui(org.bukkit.entity.Player player) {
        guiHandler.openGUI(player);
    }

    public SzczesciarzManager getZmiankiManager() {
        return zmiankiManager;
    }

    public ZmiankiGUIHandler getGuiHandler() {
        return guiHandler;
    }

    private void ensureResources() {
        File folder = configFile.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("[mslf-szczesciarz] Nie udalo sie utworzyc folderu: " + folder.getAbsolutePath());
        }
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource(CONFIG_RESOURCE)) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    configFile.createNewFile();
                }
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "[mslf-szczesciarz] Nie udalo sie skopiowac configu", exception);
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
            plugin.getLogger().log(Level.WARNING, "[mslf-szczesciarz] Merge defaults failed", exception);
        }
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("[mslf-szczesciarz] Brak komendy " + name + " w plugin.yml");
            return;
        }
        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
