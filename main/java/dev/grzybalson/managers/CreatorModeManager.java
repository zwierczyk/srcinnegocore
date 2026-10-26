package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CreatorModeManager {

    private final HollyCore plugin;
    private final CreatorHitProtection hitProtection;
    private final Set<UUID> creatorModePlayers;
    private final Map<UUID, Long> grantedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> expireTasks = new ConcurrentHashMap<>();
    private final File grantsFile;

    public CreatorModeManager(HollyCore plugin) {
        this.plugin = plugin;
        this.hitProtection = new CreatorHitProtection(new NamespacedKey(plugin, "creator-hit-protected-until"));
        this.creatorModePlayers = ConcurrentHashMap.newKeySet();
        this.grantsFile = new File(HollyDataPaths.getAddonFolder(plugin, "creatormode"), "grants.yml");
        loadGrants();
    }

    public void setCreatorMode(Player player, boolean enabled) {
        if (player == null) {
            return;
        }
        if (enabled) {
            creatorModePlayers.add(player.getUniqueId());
        } else {
            creatorModePlayers.remove(player.getUniqueId());
        }
        if (plugin.getActionBarManager() != null) {
            plugin.getActionBarManager().refreshActionbar(player);
        }
    }

    public boolean isInCreatorMode(Player player) {
        return player != null && creatorModePlayers.contains(player.getUniqueId());
    }

    public boolean isInCreatorMode(UUID uuid) {
        return uuid != null && creatorModePlayers.contains(uuid);
    }

    public void protectAfterCreatorHit(Player victim) {
        hitProtection.protect(victim);
    }

    public boolean shouldPreventDrops(Player victim, Player killer) {
        return hitProtection.protectsDeath(victim, isInCreatorMode(victim), isInCreatorMode(killer));
    }

    public boolean toggleCreatorMode(Player player) {
        if (hasActiveGrant(player.getUniqueId())) {
            if (!isInCreatorMode(player)) {
                setCreatorMode(player, true);
            }
            return true;
        }
        boolean newState = !isInCreatorMode(player);
        setCreatorMode(player, newState);
        return newState;
    }

    public void grant(UUID uuid, String name, long durationMillis) {
        if (uuid == null || durationMillis <= 0L) {
            return;
        }
        long until = System.currentTimeMillis() + durationMillis;
        grantedUntil.put(uuid, until);
        scheduleExpire(uuid, durationMillis);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            setCreatorMode(online, true);
        }
        saveGrants();
    }

    public boolean revoke(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        boolean hadGrant = grantedUntil.remove(uuid) != null;
        boolean wasActive = creatorModePlayers.remove(uuid);
        cancelExpire(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            setCreatorMode(online, false);
        }
        if (hadGrant) {
            saveGrants();
        }
        return hadGrant || wasActive;
    }

    public boolean hasActiveGrant(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        Long until = grantedUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            expireGrant(uuid, false);
            return false;
        }
        return true;
    }

    public long remainingMillis(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        Long until = grantedUntil.get(uuid);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void handlePlayerJoin(Player player) {
        if (player == null) {
            return;
        }
        if (hasActiveGrant(player.getUniqueId())) {
            setCreatorMode(player, true);
        }
    }

    public void handlePlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        creatorModePlayers.remove(player.getUniqueId());
    }

    public void shutdown() {
        for (BukkitTask task : expireTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        expireTasks.clear();
        saveGrants();
        creatorModePlayers.clear();
    }

    public Set<UUID> getCreatorModePlayers() {
        return creatorModePlayers;
    }

    private void scheduleExpire(UUID uuid, long delayMillis) {
        cancelExpire(uuid);
        long ticks = Math.max(1L, delayMillis / 50L);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> expireGrant(uuid, true), ticks);
        expireTasks.put(uuid, task);
    }

    private void cancelExpire(UUID uuid) {
        BukkitTask existing = expireTasks.remove(uuid);
        if (existing != null) {
            existing.cancel();
        }
    }

    private void expireGrant(UUID uuid, boolean notify) {
        grantedUntil.remove(uuid);
        cancelExpire(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            setCreatorMode(online, false);
            if (notify) {
                String message = plugin.getConfigManager().getCreatorModeConfig().getExpiredMessage();
                online.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeComponent(message));
            }
        } else {
            creatorModePlayers.remove(uuid);
        }
        saveGrants();
    }

    private void loadGrants() {
        if (!grantsFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(grantsFile);
        ConfigurationSection section = yaml.getConfigurationSection("grants");
        if (section == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            long until = section.getLong(key + ".until", 0L);
            if (until <= now) {
                continue;
            }
            grantedUntil.put(uuid, until);
            scheduleExpire(uuid, until - now);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                setCreatorMode(online, true);
            }
        }
    }

    private void saveGrants() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : grantedUntil.entrySet()) {
            String path = "grants." + entry.getKey();
            yaml.set(path + ".until", entry.getValue());
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null) {
                yaml.set(path + ".name", online.getName());
            }
        }
        try {
            File parent = grantsFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(grantsFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać grantów trybu twórcy: " + exception.getMessage());
        }
    }
}
