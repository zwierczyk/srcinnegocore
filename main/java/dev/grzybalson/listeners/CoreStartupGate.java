package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Blokuje wejscie graczy dopoki HollyCore nie zakonczy pelnego startu.
 * Kick tylko na AsyncPreLogin (mniej podwojnego spamu Paper/LuckPerms).
 */
public final class CoreStartupGate implements Listener {

    private static final String BYPASS_PERMISSION = "hollycore.startup.bypass";
    private static final String LOADING_MESSAGE = "&cSerwer sie jeszcze uruchamia.\n&7Sprobuj ponownie za chwile...";
    private static final String FAILED_MESSAGE = "&cSerwer jest niedostepny.\n&7HollyCore nie zostal poprawnie wczytany.";
    /** Max 1 log kicka na UUID / 3s — reszta i tak leci z Paper, ale bez naszego podwojenia. */
    private static final long KICK_LOG_COOLDOWN_MS = 3000L;

    private static final AtomicBoolean READY = new AtomicBoolean(false);
    private static final AtomicBoolean FAILED = new AtomicBoolean(false);
    private static final ConcurrentHashMap<UUID, AtomicLong> lastKickLog = new ConcurrentHashMap<>();

    public CoreStartupGate(HollyCore plugin) {
    }

    public static void reset() {
        READY.set(false);
        FAILED.set(false);
        lastKickLog.clear();
    }

    public static void markReady() {
        FAILED.set(false);
        READY.set(true);
        lastKickLog.clear();
    }

    public static void markFailed() {
        READY.set(false);
        FAILED.set(true);
    }

    public static void markNotReady() {
        READY.set(false);
    }

    public static boolean isReady() {
        return READY.get();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (shouldAllow(event.getUniqueId(), null)) {
            return;
        }
        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            ColorUtils.colorizeComponent(blockMessage())
        );
        maybeLogKick(event.getUniqueId(), event.getName());
    }

    /**
     * Backup — zwykle nie odpala się, gdy async już odrzucił.
     * Bez dodatkowego logowania.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (shouldAllow(event.getPlayer().getUniqueId(), event.getPlayer())) {
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, ColorUtils.colorizeComponent(blockMessage()));
    }

    private void maybeLogKick(UUID uuid, String name) {
        if (uuid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        AtomicLong last = lastKickLog.computeIfAbsent(uuid, key -> new AtomicLong(0L));
        long prev = last.get();
        if (now - prev < KICK_LOG_COOLDOWN_MS) {
            return;
        }
        if (!last.compareAndSet(prev, now)) {
            return;
        }
        // Jeden zwięzły wpis zamiast floodu — Paper i tak doda swoje linie.
        Bukkit.getLogger().info("[HollyCore] Start: odrzucono " + (name == null ? uuid.toString() : name) + " (serwer sie laduje)");
    }

    private boolean shouldAllow(java.util.UUID uuid, Player onlinePlayer) {
        if (READY.get()) {
            return true;
        }
        if (onlinePlayer != null && onlinePlayer.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }
        OfflinePlayer offlinePlayer = uuid == null ? null : Bukkit.getOfflinePlayer(uuid);
        return offlinePlayer != null && offlinePlayer.isOp();
    }

    private String blockMessage() {
        return FAILED.get() ? FAILED_MESSAGE : LOADING_MESSAGE;
    }
}
