package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.IgnoreDataManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IgnoreManager {

    private final HollyCore plugin;
    private final IgnoreDataManager dataManager;

    public IgnoreManager(HollyCore plugin) {
        this.plugin = plugin;
        this.dataManager = new IgnoreDataManager(plugin);
    }

    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return dataManager.loadPlayer(uuid);
    }

    public void unloadPlayer(UUID uuid) {
        dataManager.unloadPlayer(uuid);
    }

    public boolean toggleIgnore(UUID player, UUID target) {
        return dataManager.toggleIgnore(player, target);
    }

    public void setIgnoreAll(UUID player, boolean ignoreAll) {
        dataManager.setIgnoreAll(player, ignoreAll);
    }

    public boolean isIgnored(UUID player, UUID target) {
        return dataManager.isIgnored(player, target);
    }

    public boolean isIgnoringAll(UUID player) {
        return dataManager.isIgnoringAll(player);
    }

    public boolean shouldHideChat(Player viewer, Player speaker) {
        if (viewer == null || speaker == null) {
            return false;
        }
        if (viewer.getUniqueId().equals(speaker.getUniqueId())) {
            return false;
        }
        return isIgnored(viewer.getUniqueId(), speaker.getUniqueId());
    }

    public void sendVisibleChat(Player speaker, Component message) {
        if (message == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (shouldHideChat(viewer, speaker)) {
                continue;
            }
            viewer.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public boolean toggleIgnoreAll(UUID player) {
        boolean current = dataManager.isIgnoringAll(player);
        dataManager.setIgnoreAll(player, !current);
        return !current;
    }

    public IgnoreDataManager getDataManager() {
        return dataManager;
    }
}
