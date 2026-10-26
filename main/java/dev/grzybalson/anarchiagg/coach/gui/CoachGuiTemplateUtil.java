package dev.grzybalson.anarchiagg.coach.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CoachGuiTemplateUtil {

    public static boolean applyGuiTemplate(Inventory inv) {
        if (inv == null) return false;
        HollyCore plugin = HollyCore.getInstance();
        if (plugin == null) return false;

        HollyDataPaths.ensureResource(plugin, "guis/trener.yml");
        HollyDataPaths.ensureResource(plugin, "guis/template.yml");

        File guiFile = new File(plugin.getDataFolder(), "guis/trener.yml");
        YamlConfiguration guiConfig = null;

        if (guiFile.exists() && guiFile.length() > 0) {
            guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        } else {
            InputStream stream = plugin.getResource("guis/trener.yml");
            if (stream != null) {
                guiConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        }

        ConfigurationSection section = null;
        if (guiConfig != null) {
            int size = inv.getSize();
            section = guiConfig.getConfigurationSection("template" + size);
            if (section == null) {
                section = guiConfig.getConfigurationSection("template");
            }
            if (section == null) {
                section = guiConfig.getConfigurationSection("template" + (size >= 54 ? "54" : (size >= 45 ? "45" : "27")));
            }
            if (section == null && hasMaterialKeys(guiConfig)) {
                section = guiConfig;
            }
        }

        if (section == null || section.getKeys(false).isEmpty()) {
            File templateFile = new File(plugin.getDataFolder(), "guis/template.yml");
            if (templateFile.exists() && templateFile.length() > 0) {
                guiConfig = YamlConfiguration.loadConfiguration(templateFile);
            } else {
                InputStream stream = plugin.getResource("guis/template.yml");
                if (stream != null) {
                    guiConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                }
            }
            if (guiConfig != null) {
                int size = inv.getSize();
                section = guiConfig.getConfigurationSection("template" + size);
                if (section == null) {
                    section = guiConfig.getConfigurationSection(size >= 54 ? "template54" : (size >= 45 ? "template45" : "template27"));
                }
            }
        }

        if (section == null) return false;

        boolean applied = false;
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) continue;

            ItemStack decoration = plugin.getGuiFactory() != null
                    ? plugin.getGuiFactory().createBackgroundItem(material)
                    : new ItemStack(material);

            for (int slot : section.getIntegerList(materialName)) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, decoration.clone());
                    applied = true;
                }
            }
        }
        return applied;
    }

    private static boolean hasMaterialKeys(ConfigurationSection section) {
        if (section == null) return false;
        for (String key : section.getKeys(false)) {
            Material m = Material.matchMaterial(key);
            if (m != null && m.isItem()) {
                return true;
            }
        }
        return false;
    }
}
