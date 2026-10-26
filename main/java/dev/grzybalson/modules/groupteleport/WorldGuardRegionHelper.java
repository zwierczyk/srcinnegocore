package dev.grzybalson.modules.groupteleport;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;

/** Soft WorldGuard hook — bez twardej zaleznosci. */
public final class WorldGuardRegionHelper {

    private WorldGuardRegionHelper() {
    }

    public static boolean isInRegion(Player player, String regionName) {
        if (player == null || regionName == null || regionName.isBlank()) {
            return true;
        }
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) {
            return true;
        }
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);

            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weLoc = bukkitAdapter.getMethod("adapt", org.bukkit.Location.class).invoke(null, player.getLocation());

            Method getApplicable = null;
            for (Method method : query.getClass().getMethods()) {
                if (method.getName().equals("getApplicableRegions") && method.getParameterCount() == 1) {
                    getApplicable = method;
                    break;
                }
            }
            if (getApplicable == null) {
                return true;
            }
            Object regionSet = getApplicable.invoke(query, weLoc);
            for (Object region : (Iterable<?>) regionSet) {
                Method getId = region.getClass().getMethod("getId");
                Object id = getId.invoke(region);
                if (id != null && id.toString().equalsIgnoreCase(regionName)) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return true;
        }
    }
}
