package dev.grzybalson.modules.combat;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CombatConfig;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {

    private final HollyCore plugin;
    private final Map<UUID, Long> combatExpiry;
    private final Map<UUID, UUID> lastAttacker;
    private final Map<UUID, BossBar> bossBars;
    private final Map<UUID, Long> regionBounceThrottle;
    private final Map<UUID, Long> regionMessageThrottle;
    private final Map<UUID, Long> enderchestMessageThrottle;
    private final Map<UUID, CachedRegionBlock> regionBlockCache;
    private volatile RegionQuery cachedRegionQuery;
    private long cachedRegionQueryAt;
    private BukkitTask sharedCombatTask;
    private boolean enabled = true;

    public CombatManager(HollyCore plugin) {
        this.plugin = plugin;
        dev.grzybalson.internal.HollyIntegrityGuard.requireBound(plugin, "timer:start");
        this.combatExpiry = new ConcurrentHashMap<>();
        this.lastAttacker = new ConcurrentHashMap<>();
        this.bossBars = new ConcurrentHashMap<>();
        this.regionBounceThrottle = new ConcurrentHashMap<>();
        this.regionMessageThrottle = new ConcurrentHashMap<>();
        this.enderchestMessageThrottle = new ConcurrentHashMap<>();
        this.regionBlockCache = new ConcurrentHashMap<>();
        startSharedTimer();
    }

    /**
     * Single shared timer that handles ALL combat players instead of one BukkitRunnable per player.
     * Dramatically reduces scheduler overhead at high player counts.
     */
    private void startSharedTimer() {
        sharedCombatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (combatExpiry.isEmpty()) return;

            CombatConfig config = plugin.getConfigManager().getCombatConfig();
            int combatTime = config.getCombatTime();
            String notificationType = config.getNotificationType();
            long now = System.currentTimeMillis();

            Iterator<Map.Entry<UUID, Long>> it = combatExpiry.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                UUID uuid = entry.getKey();
                long expiry = entry.getValue();

                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    it.remove();
                    lastAttacker.remove(uuid);
                    bossBars.remove(uuid);
                    regionBounceThrottle.remove(uuid);
                    regionMessageThrottle.remove(uuid);
                    enderchestMessageThrottle.remove(uuid);
                    regionBlockCache.remove(uuid);
                    continue;
                }

                if (now >= expiry) {
                    // Combat ended
                    it.remove();
                    lastAttacker.remove(uuid);
                    BossBar bossBar = bossBars.remove(uuid);
                    if (bossBar != null) {
                        player.hideBossBar(bossBar);
                    }
                    regionBounceThrottle.remove(uuid);
                    regionMessageThrottle.remove(uuid);
                    enderchestMessageThrottle.remove(uuid);
                    regionBlockCache.remove(uuid);

                    player.sendMessage(ColorUtils.colorizeComponent(config.getCombatEndMessage()));
                    plugin.getActionBarManager().refreshActionbar(player);
                    continue;
                }

                // Update bossbar
                if (notificationType.equalsIgnoreCase("BOSSBAR")) {
                    int timeLeft = (int) Math.ceil((expiry - now) / 1000.0);
                    String message = config.getCombatMessage().replace("{time}", String.valueOf(timeLeft));
                    BossBar bossBar = bossBars.get(uuid);
                    if (bossBar != null) {
                        bossBar.name(ColorUtils.colorizeComponent(message));
                        bossBar.progress(Math.max(0, Math.min(1, (float) timeLeft / combatTime)));
                    }
                }
            }
        }, 0L, 20L);
    }

    public void tagPlayer(Player player, Player attacker) {
        if (!enabled) return;
        if (hasCombatBypass(player) || hasCombatBypass(attacker)) return;

        if (player != null && attacker != null) {
            lastAttacker.put(player.getUniqueId(), attacker.getUniqueId());
            lastAttacker.put(attacker.getUniqueId(), player.getUniqueId());
        }

        tagPlayer(player);
        tagPlayer(attacker);
    }

    public UUID getLastAttacker(Player player) {
        if (player == null) return null;
        return lastAttacker.get(player.getUniqueId());
    }

    public void tagPlayer(Player player) {
        if (!enabled) return;
        if (hasCombatBypass(player)) {
            cleanup(player.getUniqueId());
            return;
        }

        UUID uuid = player.getUniqueId();
        CombatConfig config = plugin.getConfigManager().getCombatConfig();
        int combatTime = config.getCombatTime();

        combatExpiry.put(uuid, System.currentTimeMillis() + (combatTime * 1000L));

        // Create bossbar on first tag
        if (config.getNotificationType().equalsIgnoreCase("BOSSBAR")) {
            bossBars.computeIfAbsent(uuid, k -> {
                BossBar bar = createBossBar(config);
                player.showBossBar(bar);
                return bar;
            });
            if (plugin.getPickupManager() != null) {
                plugin.getPickupManager().schedulePinToBottom(player);
            }
        }

        plugin.getActionBarManager().refreshActionbar(player);
    }

    private BossBar createBossBar(CombatConfig config) {
        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(config.getBossBarColor().toUpperCase());
        } catch (IllegalArgumentException e) {
            color = BossBar.Color.RED;
        }

        BossBar.Overlay overlay;
        try {
            overlay = BossBar.Overlay.valueOf(config.getBossBarStyle().toUpperCase());
        } catch (IllegalArgumentException e) {
            overlay = BossBar.Overlay.PROGRESS;
        }

        return BossBar.bossBar(
            Component.empty(),
            1.0f,
            color,
            overlay
        );
    }

    public void endCombat(Player player) {
        UUID uuid = player.getUniqueId();
        CombatConfig config = plugin.getConfigManager().getCombatConfig();

        cleanup(uuid);

        if (player.isOnline()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getCombatEndMessage()));
            plugin.getActionBarManager().refreshActionbar(player);
        }
    }

    public void clearCombat(Player player) {
        if (player != null) {
            cleanup(player.getUniqueId());
        }
    }

    private void cleanup(UUID uuid) {
        combatExpiry.remove(uuid);
        lastAttacker.remove(uuid);
        regionBounceThrottle.remove(uuid);
        regionMessageThrottle.remove(uuid);
        enderchestMessageThrottle.remove(uuid);
        regionBlockCache.remove(uuid);

        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.hideBossBar(bossBar);
            }
        }
    }

    public boolean isInCombat(Player player) {
        if (!enabled) return false;

        UUID uuid = player.getUniqueId();
        Long expiry = combatExpiry.get(uuid);
        if (expiry == null) return false;

        if (System.currentTimeMillis() >= expiry) {
            cleanup(uuid);
            return false;
        }

        return true;
    }

    /** true = otwarcie enderchesta ma byc zablokowane (gracz w walce). Sam wysyla komunikat. */
    public boolean blockEnderchest(Player player) {
        if (player == null || !isInCombat(player)) {
            return false;
        }
        CombatConfig config = plugin.getConfigManager().getCombatConfig();
        if (!config.isBlockEnderchestInCombat()) {
            return false;
        }

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        if (now - enderchestMessageThrottle.getOrDefault(uuid, 0L) >= 750L) {
            enderchestMessageThrottle.put(uuid, now);
            player.sendMessage(ColorUtils.colorizeComponent(config.getEnderchestBlockedMessage()));
        }
        return true;
    }

    public int getRemainingCombatTime(Player player) {
        Long expiry = combatExpiry.get(player.getUniqueId());
        if (expiry == null) return 0;

        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? (int) Math.ceil(remaining / 1000.0) : 0;
    }

    public void handleCombatLogout(Player player) {
        if (!isInCombat(player)) return;

        CombatConfig config = plugin.getConfigManager().getCombatConfig();
        String message = config.getCombatLogoutMessage().replace("{player}", player.getName());

        Bukkit.broadcast(ColorUtils.colorizeComponent(message));

        player.setHealth(0);

        cleanup(player.getUniqueId());
    }

    public void handlePlayerDeath(Player player) {
        if (isInCombat(player)) {
            cleanup(player.getUniqueId());
        }
    }

    public boolean tryEnterSafeRegion(Player player, Location from, Location to) {
        if (!isInCombat(player)) return true;

        CombatConfig config = plugin.getConfigManager().getCombatConfig();
        if (!isBlockedCombatRegion(player, to, config)) {
            return true;
        }

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        if (now - regionMessageThrottle.getOrDefault(uuid, 0L) >= 750L) {
            regionMessageThrottle.put(uuid, now);
            player.sendMessage(ColorUtils.colorizeComponent(config.getRegionBlockedMessage()));
        }

        if (now - regionBounceThrottle.getOrDefault(uuid, 0L) >= 250L) {
            regionBounceThrottle.put(uuid, now);
            Vector direction = from.toVector().subtract(to.toVector());
            if (direction.lengthSquared() <= 0.0001D) {
                direction = player.getLocation().getDirection().multiply(-1);
            }
            Vector knockback = direction.normalize().multiply(Math.max(0.1D, config.getKnockbackStrength() * 0.35D));
            knockback.setY(Math.max(0.18D, knockback.getY() + 0.18D));
            Vector finalKnockback = knockback;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.setVelocity(finalKnockback);
            });
        }

        return false;
    }

    public boolean hasCombatBypass(Player player) {
        return player != null && (player.isOp() || player.hasPermission("Holly.combat.bypass"));
    }

    public boolean isInSafeRegionOrPvpDeny(Location location) {
        CombatConfig config = plugin.getConfigManager().getCombatConfig();
        return isBlockedCombatRegion(null, location, config);
    }

    private RegionQuery getRegionQuery() {
        long now = System.currentTimeMillis();
        if (cachedRegionQuery != null && now - cachedRegionQueryAt < 5000L) {
            return cachedRegionQuery;
        }
        try {
            if (WorldGuard.getInstance() != null && WorldGuard.getInstance().getPlatform() != null) {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                if (container != null) {
                    cachedRegionQuery = container.createQuery();
                    cachedRegionQueryAt = now;
                    return cachedRegionQuery;
                }
            }
        } catch (Throwable ignored) {
        }
        cachedRegionQuery = null;
        return null;
    }

    private boolean isBlockedCombatRegion(Player player, Location location, CombatConfig config) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (player != null) {
            CachedRegionBlock cached = regionBlockCache.get(player.getUniqueId());
            if (cached != null && cached.matches(location) && now < cached.expiresAt) {
                return cached.blocked;
            }
        }
        boolean blocked = queryBlockedCombatRegion(location, config);
        if (player != null) {
            regionBlockCache.put(player.getUniqueId(), new CachedRegionBlock(
                location.getWorld().getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                blocked,
                now + 250L
            ));
        }
        return blocked;
    }

    private boolean queryBlockedCombatRegion(Location location, CombatConfig config) {
        try {
            RegionQuery query = getRegionQuery();
            if (query == null) return false;
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));
            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regions.getRegions()) {
                for (String regionName : config.getSafeRegions()) {
                    if (region.getId().equalsIgnoreCase(regionName)) {
                        return true;
                    }
                }
            }

            StateFlag.State pvpState = regions.queryState(null, Flags.PVP);
            return pvpState == StateFlag.State.DENY;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isInSafeRegion(Location location, CombatConfig config) {
        try {
            RegionQuery query = getRegionQuery();
            if (query == null) return false;
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));
            for (com.sk89q.worldguard.protection.regions.ProtectedRegion region : regions.getRegions()) {
                for (String regionName : config.getSafeRegions()) {
                    if (region.getId().equalsIgnoreCase(regionName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLocationInRegion(Location location, String regionName) {
        try {
            RegionQuery query = getRegionQuery();
            if (query == null) return false;
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

            return regions.getRegions().stream()
                .anyMatch(region -> region.getId().equalsIgnoreCase(regionName));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPvpDenyRegion(Location location) {
        try {
            RegionQuery query = getRegionQuery();
            if (query == null) return false;
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

            StateFlag.State pvpState = regions.queryState(null, Flags.PVP);
            return pvpState == StateFlag.State.DENY;
        } catch (Exception e) {
            return false;
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        if (!enabled) {
            for (UUID uuid : combatExpiry.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    endCombat(player);
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        if (sharedCombatTask != null) {
            sharedCombatTask.cancel();
        }
        for (UUID uuid : combatExpiry.keySet()) {
            cleanup(uuid);
        }
    }

    private record CachedRegionBlock(UUID worldId, int x, int y, int z, boolean blocked, long expiresAt) {
        private boolean matches(Location location) {
            return location.getWorld() != null
                && worldId.equals(location.getWorld().getUID())
                && x == location.getBlockX()
                && y == location.getBlockY()
                && z == location.getBlockZ();
        }
    }
}
