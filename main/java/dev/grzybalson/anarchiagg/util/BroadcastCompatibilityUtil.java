package dev.grzybalson.anarchiagg.util;

import org.bukkit.Bukkit;

public final class BroadcastCompatibilityUtil {

    private BroadcastCompatibilityUtil() {
    }

    public static void broadcastMessage(String message) {
        Bukkit.broadcastMessage(message);
    }
}
