package dev.grzybalson.anarchiagg.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SchedulerUtil {

    private static final int ASYNC_THREADS = Math.max(8, Math.min(16, Runtime.getRuntime().availableProcessors() * 2));
    private static final int ASYNC_QUEUE_CAPACITY = 4096;
    private static final Object ASYNC_EXECUTOR_LOCK = new Object();
    private static final AtomicInteger ASYNC_THREAD_COUNTER = new AtomicInteger(1);

    private static ThreadPoolExecutor asyncExecutor;

    private SchedulerUtil() {
    }

    public static boolean runSync(Plugin plugin, Runnable runnable) {
        if (!canSchedule(plugin)) return false;

        if (Bukkit.isPrimaryThread()) {
            runIfEnabled(plugin, runnable);
            return true;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, () -> runIfEnabled(plugin, runnable));
            return true;
        } catch (IllegalPluginAccessException ignored) {
            return false;
        }
    }

    public static boolean runSyncLater(Plugin plugin, Runnable runnable, long delay) {
        if (!canSchedule(plugin)) return false;

        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> runIfEnabled(plugin, runnable), delay);
            return true;
        } catch (IllegalPluginAccessException ignored) {
            return false;
        }
    }

    public static boolean runAsync(Plugin plugin, Runnable runnable) {
        if (!canSchedule(plugin)) return false;

        try {
            executor().execute(() -> runIfEnabled(plugin, () -> runAsyncSafely(runnable)));
            return true;
        } catch (RejectedExecutionException exception) {
            dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&e" + "Kolejka zadan async jest pelna - pomijam zadanie, zeby nie zabic serwera watkami.");
            return false;
        }
    }

    public static boolean runAsyncOrNow(Plugin plugin, Runnable runnable) {
        if (!canSchedule(plugin)) return false;

        if (!Bukkit.isPrimaryThread()) {
            runIfEnabled(plugin, () -> runAsyncSafely(runnable));
            return true;
        }

        return runAsync(plugin, runnable);
    }

    private static boolean canSchedule(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    private static void runIfEnabled(Plugin plugin, Runnable runnable) {
        if (canSchedule(plugin)) {
            runnable.run();
        }
    }

    public static void runAsyncSafely(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            if (false) {
                
                return;
            }

            dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&e" + "Blad podczas zadania async: " + exception.getMessage());
            dev.grzybalson.anarchiagg.util.TextUtil.printStackTrace(exception);
        }
    }

    public static void shutdownAsyncExecutor() {
        ThreadPoolExecutor executor;
        synchronized (ASYNC_EXECUTOR_LOCK) {
            executor = asyncExecutor;
            asyncExecutor = null;
        }

        if (executor == null) {
            return;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadPoolExecutor executor() {
        synchronized (ASYNC_EXECUTOR_LOCK) {
            if (asyncExecutor == null || asyncExecutor.isShutdown() || asyncExecutor.isTerminated()) {
                asyncExecutor = new ThreadPoolExecutor(
                        ASYNC_THREADS,
                        ASYNC_THREADS,
                        60L,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(ASYNC_QUEUE_CAPACITY),
                        runnable -> {
                            Thread thread = new Thread(runnable, "hollycore-io-" + ASYNC_THREAD_COUNTER.getAndIncrement());
                            thread.setDaemon(true);
                            return thread;
                        }
                );
            }
            return asyncExecutor;
        }
    }
}
