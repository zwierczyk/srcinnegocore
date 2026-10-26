package dev.grzybalson.modules.itemsync;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventorySyncListener implements Listener {

    private final HollyCore plugin;
    private final InventorySyncManager manager;
    private final Map<UUID, Long> lastQuitAt = new ConcurrentHashMap<>();

    public InventorySyncListener(HollyCore plugin, InventorySyncManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        long cooldown = manager.getRejoinCooldownMillis();
        if (cooldown <= 0L) {
            return;
        }

        Long lastQuit = lastQuitAt.get(event.getPlayer().getUniqueId());
        if (lastQuit != null && lastQuit + cooldown >= System.currentTimeMillis()) {
            event.disallow(
                PlayerLoginEvent.Result.KICK_OTHER,
                ColorUtils.colorizeComponent(manager.getRejoinCooldownMessage())
            );
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // Login juz przeszedl — wpis cooldownu nic nie blokuje, a inaczej mapa rosnie bez konca.
        lastQuitAt.remove(uuid);

        if (!manager.configLoadOnJoin()) {
            return;
        }

        long loadStartedAt = System.currentTimeMillis();

        if (plugin.getBackupManager() != null) {
            plugin.getBackupManager().getDataManager().getQueuedRollbackRestore(uuid).thenAccept(queued -> {
                if (queued != null && queued.hasInventory()) {
                    return;
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> loadPlayerData(player, loadStartedAt), 5L);
            });
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> loadPlayerData(player, loadStartedAt), 5L);
    }

    private void loadPlayerData(Player player, long loadStartedAt) {
        if (!player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (manager.consumeSectorRestored(uuid)) {
            manager.savePlayer(player, false);
            return;
        }

        PlayerInventorySnapshot cached = manager.pollCachedJoin(uuid);
        if (cached != null) {
            manager.apply(player, cached.withRedirecting(false), loadStartedAt);
            return;
        }

        manager.loadSnapshot(uuid).thenAccept(snapshot -> {
            if (snapshot == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (manager.consumeSectorRestored(uuid)) {
                    return;
                }
                manager.apply(player, snapshot.withRedirecting(false), loadStartedAt);
            });
        }).exceptionally(error -> {
            plugin.getLogger().warning("Nie udalo sie wczytac danych gracza " + uuid + ": " + error.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!manager.isEnabled()) {
            return;
        }

        UUID uuid = event.getPlayer().getUniqueId();
        lastQuitAt.put(uuid, System.currentTimeMillis());

        if (manager.configSaveOnQuit()) {
            // Force: deathkeep suppress nie może pominąć quit-save (dupe: trade→kill→quit→rejoin).
            manager.forceSavePlayer(event.getPlayer());
        }
        manager.clearSaveFingerprint(uuid);
        manager.clearSectorRestored(uuid);
    }
}
