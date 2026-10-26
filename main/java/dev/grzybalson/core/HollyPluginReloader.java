package dev.grzybalson.core;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hot-reload HollyCore JAR bez restartu calego serwera.
 * Fazowy reload (disable → unload → load → enable) rozlozony na ticki + warmup po enable.
 */
public final class HollyPluginReloader {

    public enum Mode {
        NATIVE,
        EXTERNAL,
        SOFT_ONLY,
        FAILED
    }

    public record Result(Mode mode, String message, String detail) {
    }

    private static final String PLUGIN_NAME = "HollyCore";
    private static final long PHASE_GAP_TICKS = 3L;
    private static volatile boolean reloadScheduled;
    private static final AtomicReference<Plugin> pendingLoadedPlugin = new AtomicReference<>();

    private HollyPluginReloader() {
    }

    public static Result loadNew(HollyCore plugin, CommandSender sender) {
        if (reloadScheduled) {
            return new Result(Mode.FAILED, "Reload HollyCore jest juz zaplanowany.", null);
        }

        File currentJar = plugin.getPluginJarFile();
        if (currentJar == null || !currentJar.isFile()) {
            return new Result(Mode.FAILED, "Nie mozna znalezc pliku JAR HollyCore.", null);
        }

        String swapInfo = applyPendingJarSwap(plugin, currentJar);
        Logger logger = plugin.getLogger();

        if (tryExternalReload(logger)) {
            reloadScheduled = true;
            HollyLiveReload.begin();
            scheduleResetFlag(plugin);
            String msg = "Zlecono hot-reload HollyCore przez PlugMan/ServerUtils.";
            if (swapInfo != null) {
                msg += " " + swapInfo;
            }
            return new Result(Mode.EXTERNAL, msg, null);
        }

        reloadScheduled = true;
        schedulePhasedNativeReload(plugin, currentJar, swapInfo, sender);
        return new Result(
            Mode.NATIVE,
            "Fazowy hot-reload HollyCore zaplanowany (~" + (PHASE_GAP_TICKS * 4) + " tickow, bez freeza).",
            swapInfo
        );
    }

    private static void schedulePhasedNativeReload(HollyCore plugin, File jarFile, String swapInfo, CommandSender sender) {
        Plugin host = resolveSchedulerHost(plugin);
        HollyLiveReload.begin();
        plugin.getLogger().info("[LoadNew] Start fazowego reloadu (host scheduler: " + host.getName() + ")");

        Bukkit.getScheduler().runTaskLater(host, () -> runPhase(plugin, jarFile, swapInfo, sender, host, 1), 1L);
    }

    private static void runPhase(HollyCore plugin, File jarFile, String swapInfo, CommandSender sender, Plugin host, int phase) {
        try {
            switch (phase) {
                case 1 -> {
                    plugin.getLogger().info("[LoadNew] Faza 1/4: disable...");
                    Bukkit.getPluginManager().disablePlugin(plugin);
                    scheduleNext(plugin, jarFile, swapInfo, sender, host, 2);
                }
                case 2 -> {
                    plugin.getLogger().info("[LoadNew] Faza 2/4: unload classloader...");
                    unloadPlugin(Bukkit.getPluginManager(), plugin);
                    scheduleNext(plugin, jarFile, swapInfo, sender, host, 3);
                }
                case 3 -> {
                    plugin.getLogger().info("[LoadNew] Faza 3/4: load JAR...");
                    Plugin loaded = Bukkit.getPluginManager().loadPlugin(jarFile);
                    if (loaded == null) {
                        throw new InvalidPluginException("PluginManager.loadPlugin zwrocil null");
                    }
                    pendingLoadedPlugin.set(loaded);
                    scheduleNext(plugin, jarFile, swapInfo, sender, host, 4);
                }
                case 4 -> {
                    plugin.getLogger().info("[LoadNew] Faza 4/4: enable...");
                    Plugin loaded = pendingLoadedPlugin.getAndSet(null);
                    if (loaded == null) {
                        throw new InvalidPluginException("Brak zaladowanej instancji pluginu");
                    }
                    Bukkit.getPluginManager().enablePlugin(loaded);
                    broadcastConsole(sender, plugin.getLogger(),
                        "&#00FF3B✔ &8Ξ &#4EFA76HollyCore przeladowany fazowo (nowy JAR aktywny)."
                            + (swapInfo != null ? " &7(" + swapInfo + ")" : ""));
                    reloadScheduled = false;
                }
                default -> throw new IllegalStateException("Nieznana faza reloadu: " + phase);
            }
        } catch (Exception exception) {
            reloadScheduled = false;
            HollyLiveReload.finish();
            plugin.getLogger().log(Level.SEVERE, "Native loadnew failed at phase " + phase, exception);
            try {
                new CoreReloadService(plugin).reload("all");
                broadcastConsole(sender, plugin.getLogger(),
                    "&#FFB800⚠ &8Ξ &eFazowy reload nieudany — przeladowano tylko configi.");
            } catch (Exception softReloadError) {
                broadcastConsole(sender, plugin.getLogger(),
                    "&#FF0000✘ &8Ξ &cLoadnew nieudany: &f" + rootMessage(exception));
            }
        }
    }

    private static void scheduleNext(HollyCore plugin, File jarFile, String swapInfo, CommandSender sender, Plugin host, int nextPhase) {
        Bukkit.getScheduler().runTaskLater(host, () -> runPhase(plugin, jarFile, swapInfo, sender, host, nextPhase), PHASE_GAP_TICKS);
    }

    private static Plugin resolveSchedulerHost(HollyCore holly) {
        for (String name : List.of("PlugManX", "PlugMan", "ServerUtils", "LuckPerms", "Vault")) {
            Plugin candidate = Bukkit.getPluginManager().getPlugin(name);
            if (candidate != null && candidate.isEnabled()) {
                return candidate;
            }
        }
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (candidate.isEnabled() && candidate != holly) {
                return candidate;
            }
        }
        return holly;
    }

    private static void unloadPlugin(PluginManager manager, Plugin plugin) throws Exception {
        List<Plugin> plugins = getField(manager, "plugins");
        Map<String, Plugin> lookupNames = getField(manager, "lookupNames");

        plugins.remove(plugin);
        lookupNames.values().removeIf(plugin::equals);
        lookupNames.remove(plugin.getName().toLowerCase(Locale.ROOT));

        try {
            Field classLoaderField = plugin.getClass().getDeclaredField("classLoader");
            classLoaderField.setAccessible(true);
            Object loader = classLoaderField.get(plugin);
            if (loader instanceof URLClassLoader urlClassLoader) {
                urlClassLoader.close();
            }
        } catch (NoSuchFieldException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static boolean tryExternalReload(Logger logger) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlugManX")) {
            logger.info("[LoadNew] PlugManX reload HollyCore");
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman reload " + PLUGIN_NAME);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlugMan")) {
            logger.info("[LoadNew] PlugMan reload HollyCore");
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman reload " + PLUGIN_NAME);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("ServerUtils")) {
            logger.info("[LoadNew] ServerUtils reload HollyCore");
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "su reload " + PLUGIN_NAME)
                || Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "serverutils reload " + PLUGIN_NAME);
        }
        return false;
    }

    static String applyPendingJarSwap(HollyCore plugin, File currentJar) {
        File pluginsDir = currentJar.getParentFile();
        if (pluginsDir == null) {
            return null;
        }

        File[] candidates = {
            new File(currentJar.getPath() + ".new"),
            new File(currentJar.getPath() + ".pending"),
            new File(pluginsDir, "HollyCore/update.jar"),
            new File(pluginsDir, "HollyCore/update/" + currentJar.getName()),
            new File(plugin.getDataFolder(), "update/" + currentJar.getName()),
            new File(plugin.getDataFolder(), "update.jar")
        };

        for (File candidate : candidates) {
            if (candidate == null || !candidate.isFile() || candidate.length() <= 0L) {
                continue;
            }
            if (candidate.getAbsolutePath().equals(currentJar.getAbsolutePath())) {
                continue;
            }
            try {
                File backup = new File(currentJar.getPath() + ".bak");
                if (currentJar.isFile()) {
                    Files.copy(currentJar.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.copy(candidate.toPath(), currentJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(candidate.toPath());
                return "Podmieniono JAR z &f" + candidate.getName() + "&a (backup: &f" + backup.getName() + "&a).";
            } catch (IOException exception) {
                plugin.getLogger().warning("[LoadNew] Nie udalo sie podmienic JAR z " + candidate.getName()
                    + ": " + exception.getMessage());
            }
        }
        return null;
    }

    private static void scheduleResetFlag(HollyCore plugin) {
        Plugin host = resolveSchedulerHost(plugin);
        Bukkit.getScheduler().runTaskLater(host, () -> {
            reloadScheduled = false;
            if (HollyLiveReload.isActive() && Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)) {
                HollyCore holly = (HollyCore) Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
                if (holly != null) {
                    HollyLiveReload.schedulePostEnableWarmup(holly);
                }
            }
        }, 60L);
    }

    private static void broadcastConsole(CommandSender sender, Logger logger, String legacyMessage) {
        String plain = legacyMessage.replaceAll("(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]", "");
        logger.info("[LoadNew] " + plain);
        if (sender != null) {
            try {
                sender.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeComponent(legacyMessage));
            } catch (Exception ignored) {
                sender.sendMessage(plain);
            }
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
