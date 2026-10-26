package dev.grzybalson.modules.flamehubcustomitems.manager;

import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final FlameHubConfigManager configManager;
    private final Map<UUID, Map<CustomItemType, Long>> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(FlameHubConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean isOnCooldown(Player player, CustomItemType type) {
        Map<CustomItemType, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return false;
        }

        Long expireAt = playerCooldowns.get(type);
        if (expireAt == null) {
            return false;
        }

        if (System.currentTimeMillis() >= expireAt) {
            playerCooldowns.remove(type);
            return false;
        }

        return true;
    }

    public long getRemainingMillis(Player player, CustomItemType type) {
        Map<CustomItemType, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0L;
        }
        Long expireAt = playerCooldowns.get(type);
        if (expireAt == null) {
            return 0L;
        }
        return Math.max(0L, expireAt - System.currentTimeMillis());
    }

    public String getRemainingSecondsFormatted(Player player, CustomItemType type) {
        double seconds = getRemainingMillis(player, type) / 1000.0;
        return String.format(Locale.US, "%.1f", Math.max(0.1, seconds));
    }

    public void applyCooldown(Player player, CustomItemType type) {
        int seconds = configManager.getItemCooldown(type);
        long expireAt = System.currentTimeMillis() + (seconds * 1000L);

        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(type, expireAt);

        Material material = configManager.getItemMaterial(type);
        if (material != null) {
            player.setCooldown(material, seconds * 20);
        }
    }

    public void resetCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());

        Material yodaMat = configManager.getYodaMaterial();
        if (yodaMat != null) {
            player.setCooldown(yodaMat, 0);
        }

        Material vaderMat = configManager.getVaderMaterial();
        if (vaderMat != null) {
            player.setCooldown(vaderMat, 0);
        }
    }

    public void cleanupPlayer(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
