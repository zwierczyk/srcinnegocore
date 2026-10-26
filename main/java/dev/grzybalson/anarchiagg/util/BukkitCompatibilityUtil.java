package dev.grzybalson.anarchiagg.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BukkitCompatibilityUtil {
    private BukkitCompatibilityUtil() {
    }

    public static void broadcastMessage(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            dev.grzybalson.anarchiagg.util.TextUtil.sendMessage(player, message);
        }
        Bukkit.getConsoleSender().sendMessage(dev.grzybalson.anarchiagg.util.TextUtil.fixColor(message));
    }
}
