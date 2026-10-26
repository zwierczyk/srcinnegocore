package dev.grzybalson.modules.backup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.grzybalson.HollyCore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerSessionCache {

    private final HollyCore plugin;
    private final Cache<UUID, PlayerSession> sessionCache;

    public PlayerSessionCache(HollyCore plugin) {
        this.plugin = plugin;
        this.sessionCache = Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    public void loadSession(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerSession session = new PlayerSession(uuid, player.getName());
        session.setInventory(player.getInventory().getContents().clone());
        session.setArmor(player.getInventory().getArmorContents().clone());
        session.setEnderChest(player.getEnderChest().getContents().clone());
        sessionCache.put(uuid, session);
    }

    public void loadSession(UUID uuid, String name, ItemStack[] inv, ItemStack[] armor, ItemStack[] ec) {
        PlayerSession session = new PlayerSession(uuid, name);
        session.setInventory(inv);
        session.setArmor(armor);
        session.setEnderChest(ec);
        sessionCache.put(uuid, session);
    }

    public void updateSession(Player player) {
        PlayerSession session = sessionCache.getIfPresent(player.getUniqueId());
        if (session != null) {
            session.setInventory(player.getInventory().getContents().clone());
            session.setArmor(player.getInventory().getArmorContents().clone());
            session.setEnderChest(player.getEnderChest().getContents().clone());
            session.setLastUpdate(System.currentTimeMillis());
        }
    }

    public void updateSession(UUID uuid, ItemStack[] inv, ItemStack[] armor, ItemStack[] ec) {
        PlayerSession session = sessionCache.getIfPresent(uuid);
        if (session != null) {
            session.setInventory(inv);
            session.setArmor(armor);
            session.setEnderChest(ec);
            session.setLastUpdate(System.currentTimeMillis());
        }
    }

    public PlayerSession getSession(UUID uuid) {
        return sessionCache.getIfPresent(uuid);
    }

    public void removeSession(UUID uuid) {
        sessionCache.invalidate(uuid);
    }

    public void invalidateAll() {
        sessionCache.invalidateAll();
    }

    public Cache<UUID, PlayerSession> getCache() {
        return sessionCache;
    }

    public static class PlayerSession {
        private final UUID uuid;
        private final String playerName;
        // volatile: sesja jest zapisywana z wątków async, a czytana z main threada.
        private volatile ItemStack[] inventory;
        private volatile ItemStack[] armor;
        private volatile ItemStack[] enderChest;
        private volatile long lastUpdate;
        private volatile boolean hasPendingBackup;

        public PlayerSession(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.lastUpdate = System.currentTimeMillis();
            this.hasPendingBackup = false;
        }

        public UUID getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public ItemStack[] getInventory() { return inventory; }
        public void setInventory(ItemStack[] inventory) { this.inventory = inventory; }
        public ItemStack[] getArmor() { return armor; }
        public void setArmor(ItemStack[] armor) { this.armor = armor; }
        public ItemStack[] getEnderChest() { return enderChest; }
        public void setEnderChest(ItemStack[] enderChest) { this.enderChest = enderChest; }
        public long getLastUpdate() { return lastUpdate; }
        public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }
        public boolean hasPendingBackup() { return hasPendingBackup; }
        public void setHasPendingBackup(boolean hasPendingBackup) { this.hasPendingBackup = hasPendingBackup; }
    }
}
