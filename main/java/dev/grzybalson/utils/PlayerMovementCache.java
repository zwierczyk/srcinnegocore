package dev.grzybalson.utils;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Globalny cache dla optymalizacji PlayerMoveEvent.
 * Przechowuje ostatnią pozycję bloku gracza, aby uniknąć niepotrzebnych obliczeń.
 */
public final class PlayerMovementCache {

    private static final Map<UUID, CachedPosition> CACHE = new ConcurrentHashMap<>();
    private static final long CLEANUP_INTERVAL_MS = 300000L; // 5 minut
    private static long lastCleanup = System.currentTimeMillis();

    private PlayerMovementCache() {
    }

    /**
     * Sprawdza czy gracz zmienił pozycję bloku.
     * @return true jeśli gracz zmienił blok, false jeśli tylko obrócił głowę
     */
    public static boolean hasBlockChanged(UUID playerId, Location from, Location to) {
        if (playerId == null || from == null || to == null) {
            return true;
        }

        // Sprawdź światy
        if (from.getWorld() != to.getWorld()) {
            CACHE.remove(playerId);
            return true;
        }

        int toBlockX = to.getBlockX();
        int toBlockY = to.getBlockY();
        int toBlockZ = to.getBlockZ();

        CachedPosition cached = CACHE.get(playerId);
        if (cached != null) {
            // Porównaj z cache
            if (cached.blockX == toBlockX &&
                cached.blockY == toBlockY &&
                cached.blockZ == toBlockZ &&
                cached.worldId.equals(to.getWorld().getUID())) {
                return false; // Pozycja bloku nie zmieniła się
            }
        }

        // Zaktualizuj cache
        CACHE.put(playerId, new CachedPosition(
            to.getWorld().getUID(),
            toBlockX,
            toBlockY,
            toBlockZ
        ));

        // Okresowe czyszczenie
        periodicCleanup();

        return true;
    }

    /**
     * Usuwa gracza z cache (używane przy quit).
     */
    public static void remove(UUID playerId) {
        if (playerId != null) {
            CACHE.remove(playerId);
        }
    }

    /**
     * Czyści cache co jakiś czas, aby uniknąć wycieku pamięci.
     */
    private static void periodicCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            lastCleanup = now;
            // Cache jest automatycznie czyszczony przy quit, więc nie musimy nic robić
        }
    }

    /**
     * Czyści cały cache (używane przy reload).
     */
    public static void clear() {
        CACHE.clear();
    }

    /**
     * Zwraca rozmiar cache (do debugowania).
     */
    public static int size() {
        return CACHE.size();
    }

    private static final class CachedPosition {
        final UUID worldId;
        final int blockX;
        final int blockY;
        final int blockZ;

        CachedPosition(UUID worldId, int blockX, int blockY, int blockZ) {
            this.worldId = worldId;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
        }
    }
}
