package dev.grzybalson.pvp;

import dev.grzybalson.pvp.kowal.KowalGUIHandler;
import dev.grzybalson.pvp.kowal.KowalUpgradeHandler;
import java.io.File;
import java.io.IOException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class PvpEnable {

    public static void pvpEnable(JavaPlugin plugin) {
        File kowalFile;
        File kowalFolder = dev.grzybalson.anarchiagg.AnarchiaGGPaths.getFolder(plugin, "kowal");
        if (!kowalFolder.exists()) {
            kowalFolder.mkdirs();
        }
        if (!(kowalFile = new File(kowalFolder, "kowal.yml")).exists()) {
            try {
                dev.grzybalson.anarchiagg.AnarchiaGGPaths.ensureResource(plugin, "kowal/kowal.yml");
            }
            catch (IllegalArgumentException e) {
                try {
                    kowalFile.createNewFile();
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        YamlConfiguration kowalConfig = YamlConfiguration.loadConfiguration(kowalFile);
        KowalUpgradeHandler kowalUpgradeHandler = new KowalUpgradeHandler(kowalConfig, kowalFile);
        KowalGUIHandler kowalGUIHandler = new KowalGUIHandler(plugin, kowalUpgradeHandler);
        plugin.getServer().getPluginManager().registerEvents(kowalGUIHandler, plugin);
        PluginCommand command = plugin.getCommand("kowal");
        if (command != null) {
            command.setExecutor(kowalGUIHandler);
            command.setTabCompleter(null);
        } else {
            plugin.getLogger().warning("Nie mozna zarejestrowac komendy /kowal, bo nie ma jej w plugin.yml.");
        }
        // Nie przejmuj /ulepsz — to komenda addonu upgrade (UpgradeCommand).

        PluginCommand aCommand = plugin.getCommand("akowal");
        if (aCommand != null) {
            dev.grzybalson.commands.AKowalCommand aKowalCommand = new dev.grzybalson.commands.AKowalCommand(kowalUpgradeHandler);
            aCommand.setExecutor(aKowalCommand);
            aCommand.setTabCompleter(aKowalCommand);
        } else {
            plugin.getLogger().warning("Nie mozna zarejestrowac komendy /akowal, bo nie ma jej w plugin.yml.");
        }
    }

    public static void pvpDisable(JavaPlugin plugin) {
    }
}
