package dev.grzybalson.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/** Most HollySpoofer / HalosPlayerSpoofer API przez refleksję (bez twardej zależności). */
public final class SpooferBridge {

    private static Object api;
    private static boolean checked;
    private static Method getFakePlayersMethod;
    private static Method getFakeBotHelmetMethod;
    private static Method getFakeBotChestplateMethod;
    private static Method getFakeBotLeggingsMethod;
    private static Method getFakeBotBootsMethod;
    private static Method getFakeBotWeaponMethod;
    private static Method isLoadTestModeMethod;
    private static Method getFakeCountMethod;
    private static Method getFakeBotUuidMethod;
    private static Method getFakeBotTabPriorityMethod;
    private static Method hideTabBotsMethod;
    private static Method showTabBotsMethod;

    private SpooferBridge() {
    }

    public static boolean isFakeBot(String name) {
        return resolveFakeName(name) != null;
    }

    public static String resolveFakeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (String fake : getFakeNames()) {
            if (fake.equalsIgnoreCase(name)) {
                return fake;
            }
        }
        return null;
    }

    public static java.util.UUID getFakeBotUuid(String name) {
        String resolved = resolveFakeName(name);
        if (resolved == null) {
            return null;
        }
        ensureApi();
        if (api == null || getFakeBotUuidMethod == null) {
            return java.util.UUID.nameUUIDFromBytes(("HollySpoofer:" + resolved.toLowerCase()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try {
            Object result = getFakeBotUuidMethod.invoke(api, resolved);
            if (result instanceof java.util.UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return java.util.UUID.nameUUIDFromBytes(("HollySpoofer:" + resolved.toLowerCase()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static int getFakeBotTabPriority(String name, int fallback) {
        String resolved = resolveFakeName(name);
        if (resolved == null) {
            return fallback;
        }
        ensureApi();
        if (api == null || getFakeBotTabPriorityMethod == null) {
            return fallback;
        }
        try {
            Object result = getFakeBotTabPriorityMethod.invoke(api, resolved);
            if (result instanceof Number number) {
                int value = number.intValue();
                if (value >= 0 && value <= 999) {
                    return value;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return fallback;
    }

    public static boolean isLoadTestMode() {
        ensureApi();
        if (api == null || isLoadTestModeMethod == null) {
            return false;
        }
        try {
            Object result = isLoadTestModeMethod.invoke(api);
            return result instanceof Boolean enabled && enabled;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static int getFakeCount() {
        ensureApi();
        if (api == null || getFakeCountMethod == null) {
            return 0;
        }
        try {
            Object result = getFakeCountMethod.invoke(api);
            if (result instanceof Number number) {
                return Math.max(0, number.intValue());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 0;
    }

    public static void hideTabBots(Player viewer) {
        ensureApi();
        if (api == null || hideTabBotsMethod == null || viewer == null) {
            return;
        }
        try {
            hideTabBotsMethod.invoke(api, viewer);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static void showTabBots(Player viewer) {
        ensureApi();
        if (api == null || showTabBotsMethod == null || viewer == null) {
            return;
        }
        try {
            showTabBotsMethod.invoke(api, viewer);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** Usuwa teamy ht… ze Spoofera — HollyCore sortuje boty pakietami h…. */
    public static void clearHtTeamForEntry(Player viewer, String name) {
        if (viewer == null || name == null || name.isBlank()) {
            return;
        }
        Scoreboard board = viewer.getScoreboard();
        if (board == null) {
            return;
        }
        Team entryTeam = board.getEntryTeam(name);
        if (entryTeam != null && entryTeam.getName().startsWith("ht")) {
            entryTeam.removeEntry(name);
            if (entryTeam.getEntries().isEmpty()) {
                entryTeam.unregister();
            }
        }
        for (Team team : new HashSet<>(board.getTeams())) {
            String teamName = team.getName();
            if (teamName.startsWith("ht") && team.hasEntry(name)) {
                team.removeEntry(name);
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        }
    }

    public static Collection<String> getFakeNames() {
        ensureApi();
        if (api == null || getFakePlayersMethod == null) {
            return List.of();
        }
        try {
            Object result = getFakePlayersMethod.invoke(api);
            if (!(result instanceof Collection<?> raw)) {
                return List.of();
            }
            List<String> names = new ArrayList<>(raw.size());
            for (Object entry : raw) {
                if (entry instanceof String fake && !fake.isBlank()) {
                    names.add(fake);
                }
            }
            return names;
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
    }

    public static ItemStack[] getFakeEquipment(String name) {
        ensureApi();
        if (api == null || name == null || name.isBlank()) {
            return null;
        }
        if (!isFakeBot(name)) {
            return null;
        }
        return new ItemStack[] {
            invokeItem(name, getFakeBotHelmetMethod),
            invokeItem(name, getFakeBotChestplateMethod),
            invokeItem(name, getFakeBotLeggingsMethod),
            invokeItem(name, getFakeBotBootsMethod),
            invokeItem(name, getFakeBotWeaponMethod)
        };
    }

    private static ItemStack invokeItem(String name, Method method) {
        if (method == null) {
            return null;
        }
        try {
            Object result = method.invoke(api, name);
            return result instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void ensureApi() {
        if (checked) {
            return;
        }
        checked = true;
        try {
            Object resolved = resolveApiInstance();
            if (resolved == null) {
                return;
            }
            Class<?> apiType = resolved.getClass();
            api = resolved;
            getFakePlayersMethod = findNoArgMethod(apiType, "getFakePlayers", "getOnlineFakePlayers");
            getFakeBotHelmetMethod = findMethod(apiType, "getFakeBotHelmet", String.class);
            getFakeBotChestplateMethod = findMethod(apiType, "getFakeBotChestplate", String.class);
            getFakeBotLeggingsMethod = findMethod(apiType, "getFakeBotLeggings", String.class);
            getFakeBotBootsMethod = findMethod(apiType, "getFakeBotBoots", String.class);
            getFakeBotWeaponMethod = findMethod(apiType, "getFakeBotWeapon", String.class);
            getFakeBotUuidMethod = findMethod(apiType, "getFakeBotUuid", String.class);
            getFakeBotTabPriorityMethod = findMethod(apiType, "getFakeBotTabPriority", String.class);
            isLoadTestModeMethod = findNoArgMethod(apiType, "isLoadTestMode");
            getFakeCountMethod = findNoArgMethod(apiType, "getFakePlayerCount", "getFakeCount", "getFakePlayersCount");
            hideTabBotsMethod = findMethod(apiType, "hideTabBots", Player.class);
            showTabBotsMethod = findMethod(apiType, "showTabBots", Player.class);
        } catch (RuntimeException | LinkageError ignored) {
            api = null;
        }
    }

    private static Object resolveApiInstance() {
        for (String className : List.of(
            "com.halos.spoofer.api.spigot.SpigotSpooferAPI",
            "dev.holly.spoofer.api.spigot.SpigotSpooferAPI"
        )) {
            try {
                Class<?> apiType = Class.forName(className);
                for (String getter : List.of("get", "getInstance", "api")) {
                    try {
                        Method method = apiType.getMethod(getter);
                        Object instance = method.invoke(null);
                        if (instance != null) {
                            return instance;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        for (String pluginName : List.of("HollySpoofer", "HalosPlayerSpoofer", "PlayerSpoofer")) {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
            if (plugin != null) {
                return plugin;
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String... names) {
        for (String name : names) {
            Method method = findMethod(type, name);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
