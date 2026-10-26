package dev.grzybalson.modules.flamehubcustomitems.manager;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EffectManager {

    private final Map<UUID, Long> activeYoda = new ConcurrentHashMap<>();
    private final Map<UUID, Long> activeVader = new ConcurrentHashMap<>();

    public void activateYoda(Player player, int durationSeconds) {
        long expireAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        activeYoda.put(player.getUniqueId(), expireAt);
    }

    public boolean isYodaActive(Player player) {
        return isActive(activeYoda, player.getUniqueId());
    }

    public void activateVader(Player player, int durationSeconds) {
        long expireAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        activeVader.put(player.getUniqueId(), expireAt);
    }

    public boolean isVaderActive(Player player) {
        return isActive(activeVader, player.getUniqueId());
    }

    public void clearEffects(Player player) {
        activeYoda.remove(player.getUniqueId());
        activeVader.remove(player.getUniqueId());
    }

    public void cleanupPlayer(UUID uuid) {
        activeYoda.remove(uuid);
        activeVader.remove(uuid);
    }

    private static boolean isActive(Map<UUID, Long> map, UUID uuid) {
        Long expireAt = map.get(uuid);
        if (expireAt == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expireAt) {
            map.remove(uuid);
            return false;
        }
        return true;
    }
}
