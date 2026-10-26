package dev.grzybalson.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Map<String, Map<UUID, Long>> cooldowns;

    public CooldownManager() {
        this.cooldowns = new ConcurrentHashMap<>();
    }

    public void setCooldown(UUID player, String command, int seconds) {
        cooldowns.computeIfAbsent(command, k -> new ConcurrentHashMap<>())
                 .put(player, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean isOnCooldown(UUID player, String command) {
        Map<UUID, Long> commandCooldowns = cooldowns.get(command);
        if (commandCooldowns == null) {
            return false;
        }

        Long expiry = commandCooldowns.get(player);
        if (expiry == null) {
            return false;
        }

        if (System.currentTimeMillis() >= expiry) {
            commandCooldowns.remove(player);
            return false;
        }

        return true;
    }

    public int getRemainingCooldown(UUID player, String command) {
        Map<UUID, Long> commandCooldowns = cooldowns.get(command);
        if (commandCooldowns == null) {
            return 0;
        }

        Long expiry = commandCooldowns.get(player);
        if (expiry == null) {
            return 0;
        }

        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            commandCooldowns.remove(player);
            return 0;
        }

        return (int) Math.ceil(remaining / 1000.0);
    }

    public void removeCooldown(UUID player, String command) {
        Map<UUID, Long> commandCooldowns = cooldowns.get(command);
        if (commandCooldowns != null) {
            commandCooldowns.remove(player);
        }
    }

    public void clearCooldowns(UUID player) {
        for (Map<UUID, Long> commandCooldowns : cooldowns.values()) {
            commandCooldowns.remove(player);
        }
    }

    public void clearAllCooldowns() {
        cooldowns.clear();
    }

    public void clearCooldownsWithPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }
        for (String key : new java.util.ArrayList<>(cooldowns.keySet())) {
            if (key != null && key.startsWith(prefix)) {
                cooldowns.remove(key);
            }
        }
    }

    public void clearCooldownsWithPrefix(UUID player, String prefix) {
        if (player == null || prefix == null || prefix.isBlank()) {
            return;
        }
        for (Map.Entry<String, Map<UUID, Long>> entry : cooldowns.entrySet()) {
            if (entry.getKey() != null && entry.getKey().startsWith(prefix) && entry.getValue() != null) {
                entry.getValue().remove(player);
            }
        }
    }
}
