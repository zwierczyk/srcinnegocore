package dev.grzybalson.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Locale;

/** Liczniki botów HollySpoofer bez twardej zależności od JAR-a spoofera. */
public final class SpooferCountHelper {

    private static final String[] PLUGIN_NAMES = {"HollySpoofer", "HalosPlayerSpoofer", "PlayerSpoofer"};
    private static final String[] SERVICE_TYPES = {
        "dev.holly.spoofer.api.HollySpooferAPI"
    };
    private static final String[] STATIC_API_TYPES = {
        "com.halos.spoofer.api.spigot.SpigotSpooferAPI",
        "dev.holly.spoofer.api.spigot.SpigotSpooferAPI"
    };

    private static final long COUNT_CACHE_MS = 1000L;
    /** Po nieudanym bindzie nie wal Class.forName co 2s — Paper remapper robi z tego spike. */
    private static final long BIND_RETRY_MS = 30_000L;

    private static Object api;
    private static long lastBindAttemptMillis;
    private static int failedBindAttempts;
    private static Method getFakeCountMethod;
    private static Method getNetworkFakeCountMethod;
    private static Method getNetworkOnlineCountMethod;
    private static Method getNetworkFakeCountServerMethod;
    private static Method getVisibleOnlineMethod;
    private static Method getServerIdMethod;
    private static Method getLocalServerNameMethod;
    private static Method getFakePlayerCountServerMethod;
    private static Method getServerOnlineCountMethod;

    private static int cachedNetworkOnline = Integer.MIN_VALUE;
    private static long cachedNetworkOnlineAt;
    private static int cachedNetworkFake = Integer.MIN_VALUE;
    private static long cachedNetworkFakeAt;
    private static int cachedVisibleOnline = Integer.MIN_VALUE;
    private static long cachedVisibleOnlineAt;

    private SpooferCountHelper() {
    }

    public static String getLocalServerId() {
        ensureApi();
        try {
            if (getServerIdMethod != null) {
                Object value = getServerIdMethod.invoke(api);
                if (value instanceof String server && !server.isBlank()) {
                    return server.toLowerCase(Locale.ROOT);
                }
            }
            if (getLocalServerNameMethod != null) {
                Object value = getLocalServerNameMethod.invoke(api);
                if (value instanceof String server && !server.isBlank()) {
                    return server.toLowerCase(Locale.ROOT);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    public static boolean isLocalServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return false;
        }
        String local = getLocalServerId();
        return local != null && local.equalsIgnoreCase(serverId);
    }

    public static int getLocalFakeCount() {
        ensureApi();
        return invokeBound(getFakeCountMethod, 0);
    }

    public static int getNetworkFakeCount() {
        long now = System.currentTimeMillis();
        if (cachedNetworkFake != Integer.MIN_VALUE && now - cachedNetworkFakeAt < COUNT_CACHE_MS) {
            return cachedNetworkFake;
        }
        ensureApi();
        int network = invokeBound(getNetworkFakeCountMethod, Integer.MIN_VALUE);
        int value = network != Integer.MIN_VALUE ? network : getLocalFakeCount();
        cachedNetworkFake = value;
        cachedNetworkFakeAt = now;
        return value;
    }

    /** Prawdziwi + boty ze wszystkich trybów (Redis). -1 gdy brak API. */
    public static int getNetworkOnlineCount() {
        long now = System.currentTimeMillis();
        if (cachedNetworkOnline != Integer.MIN_VALUE && now - cachedNetworkOnlineAt < COUNT_CACHE_MS) {
            return cachedNetworkOnline;
        }
        ensureApi();
        int network = invokeBound(getNetworkOnlineCountMethod, Integer.MIN_VALUE);
        int value;
        if (network != Integer.MIN_VALUE) {
            value = network;
        } else {
            int fake = invokeBound(getNetworkFakeCountMethod, Integer.MIN_VALUE);
            value = fake != Integer.MIN_VALUE ? Bukkit.getOnlinePlayers().size() + fake : -1;
        }
        cachedNetworkOnline = value;
        cachedNetworkOnlineAt = now;
        return value;
    }

    public static int getServerVisibleCount(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return -1;
        }
        ensureApi();
        if (isLocalServer(serverId)) {
            return getVisibleOnlineCount();
        }
        if (getServerOnlineCountMethod != null && api != null) {
            try {
                Object result = getServerOnlineCountMethod.invoke(api, serverId);
                if (result instanceof Number number) {
                    int value = number.intValue();
                    if (value >= 0) {
                        return value;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return -1;
    }

    public static int getFakeCountForServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return 0;
        }
        ensureApi();
        if (isLocalServer(serverId)) {
            return getLocalFakeCount();
        }
        if (getNetworkFakeCountServerMethod != null && api != null) {
            try {
                Object result = getNetworkFakeCountServerMethod.invoke(api, serverId);
                if (result instanceof Number number) {
                    return Math.max(0, number.intValue());
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (getFakePlayerCountServerMethod != null && api != null) {
            try {
                Object result = getFakePlayerCountServerMethod.invoke(api, serverId);
                if (result instanceof Number number) {
                    return Math.max(0, number.intValue());
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return 0;
    }

    public static int getVisibleOnlineCount() {
        long now = System.currentTimeMillis();
        if (cachedVisibleOnline != Integer.MIN_VALUE && now - cachedVisibleOnlineAt < COUNT_CACHE_MS) {
            return cachedVisibleOnline;
        }
        ensureApi();
        int visible = invokeBound(getVisibleOnlineMethod, -1);
        int value = visible >= 0 ? visible : Bukkit.getOnlinePlayers().size() + getLocalFakeCount();
        cachedVisibleOnline = value;
        cachedVisibleOnlineAt = now;
        return value;
    }

    private static int invokeBound(Method method, int fallback) {
        if (api == null || method == null) {
            return fallback;
        }
        try {
            Object result = method.invoke(api);
            if (result instanceof Number number) {
                return Math.max(0, number.intValue());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return fallback;
    }

    private static void ensureApi() {
        if (api != null) {
            return;
        }
        long now = System.currentTimeMillis();
        long backoff = failedBindAttempts <= 0 ? 2000L : Math.min(120_000L, BIND_RETRY_MS * failedBindAttempts);
        if (lastBindAttemptMillis > 0L && now - lastBindAttemptMillis < backoff) {
            return;
        }
        lastBindAttemptMillis = now;

        Plugin plugin = findSpooferPlugin();
        ClassLoader loader = plugin != null ? plugin.getClass().getClassLoader() : SpooferCountHelper.class.getClassLoader();

        for (String typeName : SERVICE_TYPES) {
            Class<?> serviceType = loadClass(typeName, loader);
            if (serviceType == null) {
                continue;
            }
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(serviceType);
            if (provider != null && provider.getProvider() != null) {
                bind(provider.getProvider());
                failedBindAttempts = 0;
                return;
            }
            Object loaded = Bukkit.getServicesManager().load(serviceType);
            if (loaded != null) {
                bind(loaded);
                failedBindAttempts = 0;
                return;
            }
        }

        for (String typeName : STATIC_API_TYPES) {
            Object instance = resolveStaticApi(typeName, loader);
            if (instance != null) {
                bind(instance);
                failedBindAttempts = 0;
                return;
            }
        }
        failedBindAttempts = Math.min(8, failedBindAttempts + 1);
    }

    private static Plugin findSpooferPlugin() {
        for (String name : PLUGIN_NAMES) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null && plugin.isEnabled()) {
                return plugin;
            }
        }
        return null;
    }

    private static void bind(Object instance) {
        api = instance;
        bindMethods(instance.getClass());
    }

    private static Class<?> loadClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, true, loader);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static Object resolveStaticApi(String className, ClassLoader loader) {
        Class<?> apiType = loadClass(className, loader);
        if (apiType == null) {
            return null;
        }
        for (String getter : new String[] {"get", "getInstance", "api"}) {
            try {
                Method method = apiType.getMethod(getter);
                Object instance = method.invoke(null);
                if (instance != null) {
                    return instance;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static void bindMethods(Class<?> apiType) {
        getFakeCountMethod = findNoArg(apiType, "getFakeCount", "getFakePlayerCount", "getFakePlayersCount", "getOnlineFakePlayersCount");
        getNetworkFakeCountMethod = findNoArg(apiType, "getNetworkFakeCount");
        getNetworkOnlineCountMethod = findNoArg(apiType, "getNetworkOnlineCount");
        getNetworkFakeCountServerMethod = findOneArg(apiType, "getNetworkFakeCount", String.class);
        getVisibleOnlineMethod = findNoArg(apiType, "getVisibleOnline", "getVisibleOnlineCount");
        getServerIdMethod = findNoArg(apiType, "getServerId");
        getLocalServerNameMethod = findNoArg(apiType, "getLocalServerName", "getServerName");
        getFakePlayerCountServerMethod = findOneArg(apiType, "getFakePlayerCount", String.class);
        if (getFakePlayerCountServerMethod == null) {
            getFakePlayerCountServerMethod = findOneArg(apiType, "getFakePlayersCount", String.class);
        }
        getServerOnlineCountMethod = findOneArg(apiType, "getServerOnlineCount", String.class);
    }

    private static Method findNoArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Method findOneArg(Class<?> type, String name, Class<?> parameterType) {
        try {
            return type.getMethod(name, parameterType);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
