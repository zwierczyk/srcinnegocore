package dev.grzybalson.anarchiagg.zmiankibattle.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import dev.grzybalson.anarchiagg.zmiankibattle.ZmiankaBattle;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Lekki checker regionu WorldGuard bez twardej zależności w kompilacji (działa przez reflection).
 */
public final class WorldGuardRegionGate {

    public enum Action {
        CHALLENGE,
        ACCEPT
    }

    public enum FailReason {
        NONE,
        DISABLED,
        WORLDGUARD_UNAVAILABLE,
        REGION_NOT_FOUND,
        ACTOR_NOT_IN_REGION,
        OTHER_NOT_IN_REGION
    }

    public static final class CheckResult {
        private final boolean ok;
        private final FailReason reason;
        private final String region;
        private final String otherPlayerName;

        public CheckResult(boolean ok, FailReason reason, String region, String otherPlayerName) {
            this.ok = ok;
            this.reason = reason;
            this.region = region;
            this.otherPlayerName = otherPlayerName;
        }

        public boolean ok() { return ok; }
        public FailReason reason() { return reason; }
        public String region() { return region; }
        public String otherPlayerName() { return otherPlayerName; }
    }

    private final ZmiankaBattle plugin;
    private boolean loggedReflectionError = false;

    public WorldGuardRegionGate(ZmiankaBattle plugin) {
        this.plugin = plugin;
    }

    /**
     * Sprawdza, czy dana lokacja jest we wskazanym regionie WG.
     * Jeśli WorldGuard jest niedostępny lub region nie istnieje (np. w innym świecie),
     * zwraca true (skip), żeby nie psuć rozgrywki w trakcie (np. podczas bitwy).
     */
    public boolean isLocationInsideRegionOrSkip(Location location, String regionId) {
        boolean inside = isLocationInsideRegion(location, regionId);
        if (lastLowLevelFail == LowLevelFail.WORLDGUARD_UNAVAILABLE || lastLowLevelFail == LowLevelFail.REGION_NOT_FOUND) {
            return true;
        }
        return inside;
    }

    public CheckResult check(Action action, Player actor, Player other) {
        if (actor == null) {
            return new CheckResult(true, FailReason.NONE, configuredRegion(), null);
        }

        if (!isEnabledFor(action)) {
            return new CheckResult(true, FailReason.DISABLED, configuredRegion(), other == null ? null : other.getName());
        }

        String region = configuredRegion();
        if (region == null || region.isBlank()) {
            region = "spawn";
        }

        if (!isPlayerInsideRegion(actor, region)) {
            FailReason fail = lastLowLevelFail == LowLevelFail.REGION_NOT_FOUND
                    ? FailReason.REGION_NOT_FOUND
                    : (lastLowLevelFail == LowLevelFail.WORLDGUARD_UNAVAILABLE ? FailReason.WORLDGUARD_UNAVAILABLE : FailReason.ACTOR_NOT_IN_REGION);
            return new CheckResult(false, fail, region, other == null ? null : other.getName());
        }

        if (other != null && !isPlayerInsideRegion(other, region)) {
            FailReason fail = lastLowLevelFail == LowLevelFail.REGION_NOT_FOUND
                    ? FailReason.REGION_NOT_FOUND
                    : (lastLowLevelFail == LowLevelFail.WORLDGUARD_UNAVAILABLE ? FailReason.WORLDGUARD_UNAVAILABLE : FailReason.OTHER_NOT_IN_REGION);
            return new CheckResult(false, fail, region, other.getName());
        }

        return new CheckResult(true, FailReason.NONE, region, other == null ? null : other.getName());
    }

    private boolean isEnabledFor(Action action) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("settings.worldguard.enabled", true)) {
            return false;
        }
        return switch (action) {
            case CHALLENGE -> cfg.getBoolean("settings.worldguard.restrict-challenge", true);
            case ACCEPT -> cfg.getBoolean("settings.worldguard.restrict-accept", true);
        };
    }

    private String configuredRegion() {
        return plugin.getConfig().getString("settings.worldguard.region", "spawn");
    }

    private enum LowLevelFail {
        NONE,
        WORLDGUARD_UNAVAILABLE,
        REGION_NOT_FOUND,
        OUTSIDE_REGION
    }

    private LowLevelFail lastLowLevelFail = LowLevelFail.NONE;

    private boolean isPlayerInsideRegion(Player player, String regionId) {
        if (player == null) {
            lastLowLevelFail = LowLevelFail.NONE;
            return true;
        }
        return isLocationInsideRegion(player.getLocation(), regionId);
    }

    private static final class ReflectionCache {
        private static final Method GET_INSTANCE_METHOD;
        private static final Method GET_PLATFORM_METHOD;
        private static final Method GET_REGION_CONTAINER_METHOD;
        private static final Method ADAPT_WORLD_METHOD;
        private static final Method GET_REGION_MANAGER_METHOD;
        private static final Method GET_REGION_METHOD;
        private static final Method CONTAINS_METHOD;
        private static final boolean INITIALIZED;

        static {
            Method getInstance = null;
            Method getPlatform = null;
            Method getContainer = null;
            Method adapt = null;
            Method getRM = null;
            Method getRegion = null;
            Method contains = null;
            boolean init = false;

            try {
                Class<?> wg = Class.forName("com.sk89q.worldguard.WorldGuard");
                Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                Class<?> world = Class.forName("com.sk89q.worldedit.world.World");
                Class<?> rm = Class.forName("com.sk89q.worldguard.protection.managers.RegionManager");
                Class<?> pr = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");

                getInstance = wg.getMethod("getInstance");
                getPlatform = wg.getMethod("getPlatform");
                adapt = adapter.getMethod("adapt", org.bukkit.World.class);

                Object wgInst = getInstance.invoke(null);
                Object plat = getPlatform.invoke(wgInst);
                getContainer = plat.getClass().getMethod("getRegionContainer");
                Object container = getContainer.invoke(plat);

                getRM = container.getClass().getMethod("get", world);
                getRegion = rm.getMethod("getRegion", String.class);
                contains = pr.getMethod("contains", int.class, int.class, int.class);

                init = true;
            } catch (Throwable ignored) {
            }

            GET_INSTANCE_METHOD = getInstance;
            GET_PLATFORM_METHOD = getPlatform;
            GET_REGION_CONTAINER_METHOD = getContainer;
            ADAPT_WORLD_METHOD = adapt;
            GET_REGION_MANAGER_METHOD = getRM;
            GET_REGION_METHOD = getRegion;
            CONTAINS_METHOD = contains;
            INITIALIZED = init;
        }
    }

    private boolean isLocationInsideRegion(Location location, String regionId) {
        lastLowLevelFail = LowLevelFail.NONE;

        if (location == null || location.getWorld() == null) {
            lastLowLevelFail = LowLevelFail.OUTSIDE_REGION;
            return false;
        }

        Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wgPlugin == null || !wgPlugin.isEnabled() || !ReflectionCache.INITIALIZED) {
            lastLowLevelFail = LowLevelFail.WORLDGUARD_UNAVAILABLE;
            return false;
        }

        try {
            Object wg = ReflectionCache.GET_INSTANCE_METHOD.invoke(null);
            Object platform = ReflectionCache.GET_PLATFORM_METHOD.invoke(wg);
            Object regionContainer = ReflectionCache.GET_REGION_CONTAINER_METHOD.invoke(platform);

            Object adaptedWorld = ReflectionCache.ADAPT_WORLD_METHOD.invoke(null, location.getWorld());
            Object regionManager = ReflectionCache.GET_REGION_MANAGER_METHOD.invoke(regionContainer, adaptedWorld);
            if (regionManager == null) {
                lastLowLevelFail = LowLevelFail.REGION_NOT_FOUND;
                return false;
            }

            Object region = ReflectionCache.GET_REGION_METHOD.invoke(regionManager, regionId);
            if (region == null) {
                String lowered = regionId.toLowerCase(Locale.ROOT);
                region = ReflectionCache.GET_REGION_METHOD.invoke(regionManager, lowered);
            }
            if (region == null) {
                lastLowLevelFail = LowLevelFail.REGION_NOT_FOUND;
                return false;
            }

            boolean contains = (boolean) ReflectionCache.CONTAINS_METHOD.invoke(region, location.getBlockX(), location.getBlockY(), location.getBlockZ());

            if (!contains) {
                lastLowLevelFail = LowLevelFail.OUTSIDE_REGION;
            }
            return contains;
        } catch (Throwable ex) {
            lastLowLevelFail = LowLevelFail.WORLDGUARD_UNAVAILABLE;
            if (!loggedReflectionError) {
                loggedReflectionError = true;
                plugin.getLogger().warning("Nie udało się sprawdzić regionu WorldGuard (reflection): " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
            return false;
        }
    }
}
