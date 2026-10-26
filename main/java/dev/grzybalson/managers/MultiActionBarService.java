package dev.grzybalson.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Multi-channel actionbar (1:1 z HollyService MultiActionBarService).
 * Kanały łączone separatorem {@code &8| }.
 */
public final class MultiActionBarService {

    private static MultiActionBarService instance;

    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Entry>> entries = new ConcurrentHashMap<>();
    private final String separator;
    private BukkitTask task;
    private volatile Consumer<UUID> onChange;

    private MultiActionBarService(JavaPlugin plugin, String separator) {
        this.plugin = plugin;
        this.separator = separator;
    }

    public static synchronized void initialize(JavaPlugin plugin) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new MultiActionBarService(plugin, " &8| ");
        instance.start();
    }

    public static MultiActionBarService get() {
        return instance;
    }

    public static synchronized void shutdownGlobal() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public void setOnChange(Consumer<UUID> onChange) {
        this.onChange = onChange;
    }

    public void send(Player player, String channel, String message, long durationTicks) {
        if (player == null || channel == null || channel.isBlank()) {
            return;
        }
        this.send(player.getUniqueId(), channel, message, durationTicks);
    }

    public void send(UUID playerId, String channel, String message, long durationTicks) {
        if (playerId == null || channel == null || channel.isBlank()) {
            return;
        }

        if (message == null || message.isBlank() || durationTicks <= 0L) {
            this.remove(playerId, channel);
            notifyChange(playerId);
            return;
        }

        long expireAt = System.currentTimeMillis() + (durationTicks * 50L);
        this.entries
            .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
            .put(channel, new Entry(message, expireAt));
        notifyChange(playerId);
    }

    public void remove(UUID playerId, String channel) {
        Map<String, Entry> playerEntries = this.entries.get(playerId);
        if (playerEntries == null) {
            return;
        }
        playerEntries.remove(channel);
        if (playerEntries.isEmpty()) {
            this.entries.remove(playerId);
        }
        notifyChange(playerId);
    }

    public boolean hasEntries(UUID playerId) {
        Map<String, Entry> playerEntries = this.entries.get(playerId);
        return playerEntries != null && !playerEntries.isEmpty();
    }

    public Set<UUID> activePlayerIds() {
        return Set.copyOf(this.entries.keySet());
    }

    /**
     * Aktualny tekst kanałów (po wygaśnięciu starych) — bez wysyłania.
     */
    public String peekCombined(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        Map<String, Entry> channels = this.entries.get(playerId);
        if (channels == null || channels.isEmpty()) {
            return "";
        }
        long now = System.currentTimeMillis();
        channels.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
        if (channels.isEmpty()) {
            this.entries.remove(playerId);
            return "";
        }
        return channels.values().stream()
            .map(entry -> entry.message)
            .filter(text -> text != null && !text.isBlank())
            .reduce((left, right) -> left + this.separator + right)
            .orElse("");
    }

    private void notifyChange(UUID playerId) {
        Consumer<UUID> listener = this.onChange;
        if (listener != null && playerId != null) {
            listener.accept(playerId);
        }
    }

    private void start() {
        // Cleanup rzadko – ActionBarManager i tak czyści przy peek.
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::cleanup, 100L, 100L);
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Map<String, Entry>>> iterator = this.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Map<String, Entry>> playerEntry = iterator.next();
            Player player = Bukkit.getPlayer(playerEntry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            Map<String, Entry> channels = playerEntry.getValue();
            channels.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
            if (channels.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void shutdown() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.entries.clear();
        this.onChange = null;
    }

    private record Entry(String message, long expireAt) {
    }
}
