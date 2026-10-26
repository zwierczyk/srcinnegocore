package dev.grzybalson.anarchiagg.util;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;


public final class SystemUtil {
    private static final long CPU_CACHE_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final int AVAILABLE_PROCESSORS = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final OperatingSystemMXBean OS_BEAN = resolveOperatingSystemMXBean();
    private static volatile long lastCpuLoadRefreshNanos;
    private static volatile long lastProcessCpuTimeNanos = -1L;
    private static volatile double cachedCpuLoad;

    private SystemUtil() {
    }

    public static double getProcessCpuLoad() {
        long now = System.nanoTime();
        if (now - lastCpuLoadRefreshNanos < CPU_CACHE_NANOS) {
            return cachedCpuLoad;
        }

        synchronized (SystemUtil.class) {
            now = System.nanoTime();
            if (now - lastCpuLoadRefreshNanos < CPU_CACHE_NANOS) {
                return cachedCpuLoad;
            }

            cachedCpuLoad = readProcessCpuLoad(now);
            return cachedCpuLoad;
        }
    }

    private static double readProcessCpuLoad(long now) {
        try {
            if (OS_BEAN == null) {
                lastCpuLoadRefreshNanos = now;
                return 0.0;
            }

            long processCpuTime = OS_BEAN.getProcessCpuTime();
            if (processCpuTime < 0L) {
                lastCpuLoadRefreshNanos = now;
                return cachedCpuLoad;
            }

            long previousRefresh = lastCpuLoadRefreshNanos;
            long previousProcessCpuTime = lastProcessCpuTimeNanos;
            lastCpuLoadRefreshNanos = now;
            lastProcessCpuTimeNanos = processCpuTime;

            if (previousRefresh <= 0L || previousProcessCpuTime < 0L) {
                return cachedCpuLoad;
            }

            long elapsedNanos = now - previousRefresh;
            long processCpuNanos = processCpuTime - previousProcessCpuTime;
            if (elapsedNanos <= 0L || processCpuNanos < 0L) {
                return cachedCpuLoad;
            }

            double load = processCpuNanos * 100.0D / elapsedNanos / AVAILABLE_PROCESSORS;
            return roundOneDecimal(clamp(load));
        } catch (Exception e) {
            lastCpuLoadRefreshNanos = now;
            return cachedCpuLoad;
        }
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(100.0D, value));
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    private static OperatingSystemMXBean resolveOperatingSystemMXBean() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean operatingSystemMXBean) {
            return operatingSystemMXBean;
        }

        return null;
    }
}
