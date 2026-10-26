package dev.grzybalson.modules.generator;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Odpowiednik pl.hollyservice.engine.config.YamlConfig oparty o bukkitowe YamlConfiguration,
 * dzieki czemu logika generatorow moze byc przeniesiona 1:1 z hollyservice.
 */
public class GeneratorConfig {

    private final File file;
    private final Logger logger;
    private YamlConfiguration data;

    public GeneratorConfig(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.data = new YamlConfiguration();
    }

    public void load() {
        if (!this.file.exists()) {
            File parent = this.file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            this.data = new YamlConfiguration();
            this.save();
            return;
        }
        this.data = YamlConfiguration.loadConfiguration(this.file);
    }

    public void save() {
        try {
            File parent = this.file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            this.data.save(this.file);
        } catch (IOException exception) {
            this.logger.warning("Nie udalo sie zapisac " + this.file.getName() + ": " + exception.getMessage());
        }
    }

    public Object get(String path) {
        return this.data.get(path);
    }

    public String getString(String path) {
        return this.getString(path, null);
    }

    public String getString(String path, String def) {
        Object value = this.data.get(path);
        return value != null ? value.toString() : def;
    }

    public int getInt(String path, int def) {
        Object value = this.data.get(path);
        return value instanceof Number number ? number.intValue() : def;
    }

    public double getDouble(String path, double def) {
        Object value = this.data.get(path);
        return value instanceof Number number ? number.doubleValue() : def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = this.data.get(path);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(text.trim())) {
                return false;
            }
        }
        return def;
    }

    public List<String> getStringList(String path) {
        Object value = this.data.get(path);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object entry : list) {
                if (entry != null) {
                    result.add(entry.toString());
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    public Map<String, Object> getSection(String path) {
        ConfigurationSection section = this.data.getConfigurationSection(path);
        if (section != null) {
            return new LinkedHashMap<>(section.getValues(false));
        }
        Object value = this.data.get(path);
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }
        return new LinkedHashMap<>();
    }

    public void set(String path, Object value) {
        this.data.set(path, value);
    }

    public void setDefault(String path, Object value) {
        if (this.data.get(path) == null) {
            this.data.set(path, value);
        }
    }

    public boolean contains(String path) {
        return this.data.get(path) != null;
    }

    public ConfigurationSection getConfigurationSection(String path) {
        return this.data.getConfigurationSection(path);
    }

    public File getFile() {
        return this.file;
    }
}
