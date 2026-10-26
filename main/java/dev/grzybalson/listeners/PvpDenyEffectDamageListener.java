package dev.grzybalson.listeners;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trucizna (i wither) nie zabiera HP na regionach z flagą pvp deny —
 * efekt zostaje, ale obrażenia nie wchodzą.
 */
public final class PvpDenyEffectDamageListener implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> EFFECT_CAUSES = EnumSet.of(
        EntityDamageEvent.DamageCause.POISON,
        EntityDamageEvent.DamageCause.WITHER
    );
    private static final long CACHE_MS = 500L;

    private final Map<UUID, CachedPvpDeny> cache = new ConcurrentHashMap<>();
    private volatile RegionQuery cachedQuery;
    private long cachedQueryAt;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEffectDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!EFFECT_CAUSES.contains(event.getCause())) {
            return;
        }
        if (!isPvpDenied(player)) {
            return;
        }
        event.setCancelled(true);
        event.setDamage(0.0D);
    }

    private boolean isPvpDenied(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        CachedPvpDeny cached = cache.get(player.getUniqueId());
        if (cached != null && cached.matches(location) && now < cached.expiresAt) {
            return cached.denied;
        }

        boolean denied = queryPvpDeny(location);
        cache.put(player.getUniqueId(), new CachedPvpDeny(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            denied,
            now + CACHE_MS
        ));
        return denied;
    }

    private boolean queryPvpDeny(Location location) {
        try {
            RegionQuery query = getRegionQuery();
            if (query == null) {
                return false;
            }
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));
            return regions.queryState(null, Flags.PVP) == StateFlag.State.DENY;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private RegionQuery getRegionQuery() {
        long now = System.currentTimeMillis();
        if (cachedQuery != null && now - cachedQueryAt < 5000L) {
            return cachedQuery;
        }
        try {
            if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
                cachedQuery = null;
                return null;
            }
            if (WorldGuard.getInstance() != null && WorldGuard.getInstance().getPlatform() != null) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                if (container != null) {
                    cachedQuery = container.createQuery();
                    cachedQueryAt = now;
                    return cachedQuery;
                }
            }
        } catch (Throwable ignored) {
        }
        cachedQuery = null;
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    private record CachedPvpDeny(UUID worldId, int x, int y, int z, boolean denied, long expiresAt) {

        private boolean matches(Location location) {
            return location.getWorld() != null
                && worldId.equals(location.getWorld().getUID())
                && x == location.getBlockX()
                && y == location.getBlockY()
                && z == location.getBlockZ();
        }
    }
}
