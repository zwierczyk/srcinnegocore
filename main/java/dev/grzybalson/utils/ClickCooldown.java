package dev.grzybalson.utils;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClickCooldown {

    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private ClickCooldown() {
    }

    public static boolean tryUse(Player player, String actionKey, long cooldownMillis) {
        if (player == null || cooldownMillis <= 0L) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        String key = uuid + ":" + actionKey;
        long now = System.currentTimeMillis();
        Long previous = COOLDOWNS.get(key);
        if (previous != null && now - previous < cooldownMillis) {
            return false;
        }
        COOLDOWNS.put(key, now);
        return true;
    }

    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        String prefix = player.getUniqueId() + ":";
        COOLDOWNS.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
