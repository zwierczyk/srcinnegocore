package dev.grzybalson.internal;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.LicenseConfig;
import dev.grzybalson.license.LicenseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class HollyRuntime {

    private static final AtomicReference<State> STATE = new AtomicReference<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int WATCHDOG_DELAY_TICKS = 20 * 45;
    private static final int WATCHDOG_PERIOD_TICKS = 20 * 180;
    private static final long QUICK_CHECK_CACHE_MILLIS = 5_000L;
    private static final long STRONG_CHECK_CACHE_MILLIS = 60_000L;
    private static final int MAGIC = 0x48_43_5A_19;

    private static volatile int watchdogTaskId = -1;

    private HollyRuntime() {
    }

    public static boolean verifyAndBind(HollyCore plugin, LicenseConfig licenseConfig) {
        STATE.set(null);
        try {
            ensureServiceModulePresent();

            LicenseManager licenseManager = new LicenseManager(plugin);
            boolean valid = licenseManager.validate(licenseConfig);
            if (!valid) {
                return false;
            }

            if (!isBound(plugin, "service-result")) {
                plugin.getLogger().severe("Modul uslugowy zwrocil OK, ale runtime nie zostal powiazany.");
                return false;
            }

            startWatchdog(plugin);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE, "Nie mozna uruchomic modulu runtime: " + throwable.getMessage(), throwable);
            return false;
        }
    }

    public static void bind(HollyCore plugin, String licenseKey, String challenge, String challengeResponse, String responseBody) {
        if (plugin == null || licenseKey == null || challenge == null || challengeResponse == null) {
            STATE.set(null);
            return;
        }

        try {
            JarIdentity jarIdentity = JarIdentity.read(plugin, true);
            long nonce = RANDOM.nextLong() ^ System.nanoTime() ^ Thread.currentThread().getId();
            byte[] proof = sha256(join(
                    "HC-RUNTIME-V2",
                    plugin.getClass().getName(),
                    LicenseManager.class.getName(),
                    String.valueOf(System.identityHashCode(plugin)),
                    licenseKey,
                    challenge,
                    challengeResponse,
                    responseBody == null ? "" : responseBody,
                    jarIdentity.strongHash,
                    String.valueOf(nonce)
            ));

            long now = System.currentTimeMillis();
            int guard = guardValue(plugin.getClass().getName(), jarIdentity, proof, nonce);
            State newState = new State(
                    plugin.getClass().getName(),
                    System.identityHashCode(plugin),
                    jarIdentity,
                    proof,
                    nonce,
                    guard,
                    now
            );
            newState.quickCheckValidUntilMillis = now + QUICK_CHECK_CACHE_MILLIS;
            newState.strongCheckValidUntilMillis = now + STRONG_CHECK_CACHE_MILLIS;
            STATE.set(newState);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.SEVERE, "Nie mozna powiazac modulu runtime: " + throwable.getMessage(), throwable);
            STATE.set(null);
        }
    }

    public static void require(HollyCore plugin, String area) {
        if (isBound(plugin, area)) {
            return;
        }
        fail(plugin, area);
    }

    public static void requireStrong(HollyCore plugin, String area) {
        if (isBound(plugin, area, true)) {
            return;
        }
        fail(plugin, area);
    }

    public static boolean isBound(HollyCore plugin, String area) {
        return isBound(plugin, area, false);
    }

    public static boolean hasBoundState(HollyCore plugin) {
        if (plugin == null) {
            return false;
        }

        State state = STATE.get();
        return state != null
                && state.pluginClassName.equals(plugin.getClass().getName())
                && state.pluginIdentity == System.identityHashCode(plugin);
    }

    private static volatile long lastIntegrityCheckMillis = 0L;
    private static volatile boolean lastIntegrityCheckResult = true;

    private static boolean isBound(HollyCore plugin, String area, boolean strong) {
        if (plugin == null) {
            return false;
        }

        State state = STATE.get();
        if (state == null) {
            return false;
        }

        if (!state.pluginClassName.equals(plugin.getClass().getName())) {
            return false;
        }

        if (state.pluginIdentity != System.identityHashCode(plugin)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (!strong && state.quickCheckValidUntilMillis > now) {
            return true;
        }
        if (strong && state.strongCheckValidUntilMillis > now) {
            return true;
        }

        if (!checkCallerIntegrity()) {
            return false;
        }

        if (!ensureServiceModulePresent()) {
            return false;
        }

        JarIdentity current = JarIdentity.read(plugin, strong);
        if (!state.jarIdentity.sameFile(current, strong)) {
            return false;
        }

        int expectedGuard = guardValue(state.pluginClassName, state.jarIdentity, state.proof, state.nonce);
        if (expectedGuard != state.guard) {
            return false;
        }

        boolean valid = state.createdAt > 0L && state.proof.length == 32;
        if (valid) {
            if (strong) {
                state.strongCheckValidUntilMillis = now + STRONG_CHECK_CACHE_MILLIS;
                state.quickCheckValidUntilMillis = Math.max(state.quickCheckValidUntilMillis, now + QUICK_CHECK_CACHE_MILLIS);
            } else {
                state.quickCheckValidUntilMillis = now + QUICK_CHECK_CACHE_MILLIS;
            }
        }
        return valid;
    }

    public static int featureSeed(HollyCore plugin, String feature) {
        require(plugin, feature);
        State state = STATE.get();
        byte[] bytes = sha256(join(
                "HC-FEATURE",
                feature == null ? "" : feature,
                state.jarIdentity.strongHash,
                Base64.getEncoder().encodeToString(state.proof),
                String.valueOf(state.nonce)
        ));
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    private static void startWatchdog(HollyCore plugin) {
        if (watchdogTaskId != -1) {
            Bukkit.getScheduler().cancelTask(watchdogTaskId);
            watchdogTaskId = -1;
        }

        watchdogTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (isBound(plugin, "heartbeat", true)) {
                return;
            }
            plugin.getLogger().severe("[HollyCore] Naruszono integralnosc modulu runtime. Plugin zostanie wylaczony.");
            Bukkit.getPluginManager().disablePlugin(plugin);
        }, WATCHDOG_DELAY_TICKS, WATCHDOG_PERIOD_TICKS);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!isBound(plugin, "async-heartbeat", true)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().severe("[HollyCore] Naruszono integralnosc modulu runtime (async). Plugin zostanie wylaczony.");
                    if (plugin.isEnabled()) {
                        Bukkit.getPluginManager().disablePlugin(plugin);
                    }
                });
            }
        }, WATCHDOG_DELAY_TICKS + 100L, WATCHDOG_PERIOD_TICKS);
    }

    private static void fail(HollyCore plugin, String area) {
        if (plugin != null && !plugin.isEnabled()) {
            return;
        }

        String where = area == null ? "unknown" : area;
        try {
            plugin.getLogger().severe("[HollyCore] Modul runtime jest nieaktywny albo zmodyfikowany: " + where);
            if (plugin.isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(plugin);
            }
        } catch (Throwable ignored) {
        }
        throw new IllegalStateException("HollyCore module state mismatch: " + where);
    }

    private static boolean checkCallerIntegrity() {
        return true;
    }

    private static boolean ensureServiceModulePresent() {
        try {
            Class<?> type = LicenseManager.class;
            if (type == null) {
                return false;
            }
            type.getDeclaredConstructor(HollyCore.class);
            if (type.getDeclaredFields().length == 0) {
                return false;
            }
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static int guardValue(String pluginClassName, JarIdentity jarIdentity, byte[] proof, long nonce) {
        int result = MAGIC;
        result = 31 * result + pluginClassName.hashCode();
        result = 31 * result + jarIdentity.pathHash;
        result = 31 * result + jarIdentity.quickHash;
        result = 31 * result + jarIdentity.strongHash.hashCode();
        result = 31 * result + Arrays.hashCode(proof);
        result = 31 * result + Long.hashCode(nonce);
        return result;
    }

    private static byte[] sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append('\u001D');
            }
            builder.append(part == null ? "" : part);
        }
        return builder.toString();
    }

    private static final class State {
        private final String pluginClassName;
        private final int pluginIdentity;
        private final JarIdentity jarIdentity;
        private final byte[] proof;
        private final long nonce;
        private final int guard;
        private final long createdAt;
        private volatile long quickCheckValidUntilMillis;
        private volatile long strongCheckValidUntilMillis;

        private State(String pluginClassName, int pluginIdentity, JarIdentity jarIdentity, byte[] proof, long nonce, int guard, long createdAt) {
            this.pluginClassName = pluginClassName;
            this.pluginIdentity = pluginIdentity;
            this.jarIdentity = jarIdentity;
            this.proof = proof;
            this.nonce = nonce;
            this.guard = guard;
            this.createdAt = createdAt;
            this.quickCheckValidUntilMillis = 0L;
            this.strongCheckValidUntilMillis = 0L;
        }
    }

    private static final class JarIdentity {
        private final String path;
        private final int pathHash;
        private final int quickHash;
        private final String strongHash;

        private JarIdentity(String path, int pathHash, int quickHash, String strongHash) {
            this.path = path;
            this.pathHash = pathHash;
            this.quickHash = quickHash;
            this.strongHash = strongHash;
        }

        private boolean sameFile(JarIdentity other, boolean strong) {
            return other != null
                    && this.pathHash == other.pathHash
                    && this.quickHash == other.quickHash
                    && this.path.equals(other.path)
                    && (!strong || this.strongHash.equals(other.strongHash));
        }

        private static JarIdentity read(Plugin plugin, boolean strong) {
            try {
                URL location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
                File file = new File(location.toURI());
                String path = file.getAbsolutePath();
                long length = file.isFile() ? file.length() : 0L;
                long modified = file.exists() ? file.lastModified() : 0L;
                int quick = (path + ":" + length + ":" + modified).hashCode();
                String strongHash = strong ? strongHash(location) : "quick:" + quick;
                return new JarIdentity(path, path.hashCode(), quick, strongHash);
            } catch (Throwable throwable) {
                String fallback = plugin.getClass().getName() + ":" + System.identityHashCode(plugin);
                return new JarIdentity(fallback, fallback.hashCode(), fallback.hashCode(), "fallback:" + fallback.hashCode());
            }
        }

        private static final java.util.Map<String, String> STRONG_HASH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

        private static String strongHash(URL location) {
            if (location == null) {
                return "unavailable:null";
            }
            try {
                File file = new File(location.toURI());
                String cacheKey = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
                String cached = STRONG_HASH_CACHE.get(cacheKey);
                if (cached != null) {
                    return cached;
                }

                try (InputStream inputStream = location.openStream()) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                    }
                    String hash = Base64.getEncoder().encodeToString(digest.digest());
                    STRONG_HASH_CACHE.put(cacheKey, hash);
                    return hash;
                }
            } catch (Throwable throwable) {
                return "unavailable:" + location.toExternalForm().hashCode();
            }
        }
    }
}
