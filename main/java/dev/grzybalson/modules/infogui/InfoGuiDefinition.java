package dev.grzybalson.modules.infogui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hollyservice-style info GUI config ({@code gui.*} + {@code ranks.*}).
 */
public final class InfoGuiDefinition {

    private final File file;
    private int rows = 5;
    private String title = "&8Info";
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public InfoGuiDefinition(File file) {
        this.file = file;
    }

    public void reload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        this.rows = Math.max(1, Math.min(6, yaml.getInt("gui.rows", 5)));
        this.title = yaml.getString("gui.title", "&8Info");
        this.entries.clear();

        ConfigurationSection section = yaml.getConfigurationSection("ranks");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            String name = entry.getString("name", id);
            List<String> aliases = entry.getStringList("aliases");
            int slot = entry.getInt("slot", -1);
            String icon = entry.getString("icon", "CHEST");
            List<String> lore = entry.getStringList("lore");
            this.entries.put(id, new Entry(id, name, aliases, slot, icon, lore == null ? List.of() : List.copyOf(lore)));
        }
    }

    public static InfoGuiDefinition load(JavaPlugin plugin, String resourcePath, File file) {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
        InfoGuiDefinition definition = new InfoGuiDefinition(file);
        definition.reload();
        return definition;
    }

    public int getRows() {
        return rows;
    }

    public String getTitle() {
        return title;
    }

    public Map<String, Entry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public List<Entry> getEntriesList() {
        return new ArrayList<>(entries.values());
    }

    public record Entry(String id, String name, List<String> aliases, int slot, String icon, List<String> lore) {
    }
}
