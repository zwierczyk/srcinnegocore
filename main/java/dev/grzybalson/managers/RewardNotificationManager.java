package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RewardNotificationManager {

    private static final String DISABLED_PLAYERS_PATH = "disabled-players";

    private final HollyCore plugin;
    private final File configFile;
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    public RewardNotificationManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "reward-notifications.yml");
        deleteLegacyPlayerSettingsFolder();
        load();
    }

    public boolean isReminderEnabled(UUID playerUuid) {
        return !disabledPlayers.contains(playerUuid);
    }

    public boolean toggle(UUID playerUuid) {
        boolean enabled;
        if (disabledPlayers.remove(playerUuid)) {
            enabled = true;
        } else {
            disabledPlayers.add(playerUuid);
            enabled = false;
        }

        save();
        return enabled;
    }

    public void reload() {
        load();
    }

    private void load() {
        disabledPlayers.clear();

        if (!configFile.exists()) {
            save();
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
        for (String rawUuid : configuration.getStringList(DISABLED_PLAYERS_PATH)) {
            try {
                disabledPlayers.add(UUID.fromString(rawUuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        List<String> serialized = new ArrayList<>();
        for (UUID uuid : disabledPlayers) {
            serialized.add(uuid.toString());
        }
        serialized.sort(String::compareTo);
        configuration.set(DISABLED_PLAYERS_PATH, serialized);

        try {
            configuration.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się zapisać reward-notifications.yml: " + exception.getMessage());
        }
    }

    private void deleteLegacyPlayerSettingsFolder() {
        File legacyFolder = new File(plugin.getDataFolder(), "player-" + "settings");
        if (!legacyFolder.exists()) {
            return;
        }
        deleteRecursively(legacyFolder);
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }

        if (!file.delete() && file.exists()) {
            plugin.getLogger().warning("Nie udało się usunąć starego pliku/folderu ustawień: " + file.getPath());
        }
    }
}
