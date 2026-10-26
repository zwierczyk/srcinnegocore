package dev.grzybalson.modules.renderdistance;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.renderdistance.DefaultDistanceSettings;
import dev.grzybalson.config.modules.renderdistance.RegionDistanceSettings;
import dev.grzybalson.config.modules.renderdistance.RenderDistanceConfig;
import dev.grzybalson.config.modules.renderdistance.RenderDistanceMessages;
import dev.grzybalson.modules.renderdistance.cache.PlayerRenderDistanceCache;
import dev.grzybalson.modules.renderdistance.model.ConfiguredRenderRegion;
import dev.grzybalson.modules.renderdistance.model.RenderDistanceSelection;
import dev.grzybalson.modules.renderdistance.model.RenderDistanceStatus;
import dev.grzybalson.modules.renderdistance.model.WorldGuardRegionSnapshot;
import dev.grzybalson.modules.renderdistance.worldguard.WorldGuardRegionProvider;
import dev.grzybalson.modules.renderdistance.worldguard.WorldGuardRegionProviderImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class RenderDistanceManager {
    private static final String LOG_PREFIX = "[RenderDistance] ";
    private static final int MIN_DISTANCE = 0;
    private static final int MAX_DISTANCE = 32;
    private static final int BLOCKS_PER_CHUNK = 16;

    private final HollyCore plugin;
    private final Map<UUID, PlayerRenderDistanceCache> playerCache = new HashMap<>();
    private final Set<UUID> dirtyPlayers = new LinkedHashSet<>();

    private RenderDistanceConfig config;
    private WorldGuardRegionProvider regionProvider;
    private Map<String, Map<String, ConfiguredRenderRegion>> regionsByWorld = Map.of();
    private BukkitTask updateTask;
    private boolean worldGuardAvailable;
    private int defaultRenderDistance = -1;

    public RenderDistanceManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ensurePrimaryThread();
        this.config = plugin.getConfigManager().getRenderDistanceConfig();
        rebuildRuntimeState();
        restartScheduler();
        queueOnlinePlayersNow();
    }

    public void reload() {
        ensurePrimaryThread();
        plugin.getConfigManager().reloadRenderDistanceConfig();
        this.config = plugin.getConfigManager().getRenderDistanceConfig();
        cancelScheduler();
        showAllHiddenPlayers();
        rebuildRuntimeState();
        restartScheduler();
        dirtyPlayers.clear();
        playerCache.clear();
        queueOnlinePlayersNow();
    }

    public void shutdown() {
        ensurePrimaryThread();
        cancelScheduler();
        showAllHiddenPlayers();
        dirtyPlayers.clear();
        playerCache.clear();
        regionsByWorld = Map.of();
        regionProvider = null;
        worldGuardAvailable = false;
    }

    public void handleMove(Player player, Location from, Location to) {
        if (!isOperational() || !hasVisibilityRules() || player == null || to == null || to.getWorld() == null) {
            return;
        }
        if (!hasConfiguredRegionsInWorld(to.getWorld()) && defaultRenderDistance == -1) {
            PlayerRenderDistanceCache cache = playerCache.get(player.getUniqueId());
            if (cache != null && cache.isUsingDefaults() && Objects.equals(cache.getLastWorldName(), to.getWorld().getName())) {
                return;
            }
        }
        if (from != null && from.getWorld() != null && Objects.equals(from.getWorld(), to.getWorld())) {
            if (config.isCheckOnlyOnBlockChange()) {
                if (from.getBlockX() == to.getBlockX()
                    && from.getBlockY() == to.getBlockY()
                    && from.getBlockZ() == to.getBlockZ()) {
                    return;
                }
            } else if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
                return;
            }
        }
        queueUpdate(player, false);
    }

    public void queueUpdate(Player player, boolean flushNow) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> queueUpdate(player, flushNow));
            return;
        }
        PlayerRenderDistanceCache cache = playerCache.computeIfAbsent(player.getUniqueId(), PlayerRenderDistanceCache::new);
        long currentTick = Bukkit.getCurrentTick();
        if (cache.getLastQueuedTick() != currentTick) {
            cache.setLastQueuedTick(currentTick);
            dirtyPlayers.add(player.getUniqueId());
        }
        if (flushNow) {
            flushDirtyPlayers();
        }
    }

    public void removePlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }
        PlayerRenderDistanceCache cache = playerCache.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && cache != null) {
            showAllForViewer(player, cache);
        }
        for (PlayerRenderDistanceCache otherCache : playerCache.values()) {
            otherCache.getHiddenPlayers().remove(uuid);
        }
        dirtyPlayers.remove(uuid);
    }

    public RenderDistanceStatus getStatus(Player player) {
        ensurePrimaryThread();
        if (player == null) {
            return null;
        }
        if (isOperational()) {
            updatePlayer(player, true);
        }

        PlayerRenderDistanceCache cache = playerCache.get(player.getUniqueId());
        String activeEntry = cache == null || cache.getLastActiveEntryName() == null
            ? "defaults"
            : cache.getLastActiveEntryName();
        String worldGuardRegion = cache == null || cache.getLastWorldGuardRegionName() == null
            ? "-"
            : cache.getLastWorldGuardRegionName();
        int configuredRenderDistance = cache == null || cache.getLastRenderDistance() == Integer.MIN_VALUE
            ? defaultRenderDistance
            : cache.getLastRenderDistance();
        int hiddenPlayers = cache == null ? 0 : cache.getHiddenPlayers().size();
        boolean defaults = cache == null || cache.isUsingDefaults();

        return new RenderDistanceStatus(
            player.getName(),
            player.getWorld().getName(),
            activeEntry,
            worldGuardRegion,
            configuredRenderDistance,
            hiddenPlayers,
            defaults
        );
    }

    public RenderDistanceMessages getMessages() {
        if (config == null) {
            return new RenderDistanceMessages();
        }
        return config.getMessages();
    }

    public boolean isConfigEnabled() {
        return config != null && config.isEnabled();
    }

    public boolean isWorldGuardAvailable() {
        return worldGuardAvailable;
    }

    public boolean isOperational() {
        return isConfigEnabled() && worldGuardAvailable && regionProvider != null;
    }

    public int getConfiguredRegionCount() {
        int count = 0;
        for (Map<String, ConfiguredRenderRegion> byRegion : regionsByWorld.values()) {
            count += byRegion.size();
        }
        return count;
    }

    private void rebuildRuntimeState() {
        this.regionProvider = null;
        this.worldGuardAvailable = false;
        this.regionsByWorld = Map.of();
        validateDefaults();

        if (config == null || !config.isEnabled()) {
            info("Modul jest wylaczony w konfiguracji.");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            warning("WorldGuard nie jest wlaczony. Wylaczono tylko modul widocznosci graczy.");
            return;
        }

        try {
            this.regionProvider = new WorldGuardRegionProviderImpl();
            this.worldGuardAvailable = true;
        } catch (LinkageError | RuntimeException exception) {
            this.regionProvider = null;
            this.worldGuardAvailable = false;
            warning("Nie udalo sie podpiac WorldGuarda: " + exception.getMessage());
            return;
        }

        this.regionsByWorld = validateRegions(config.getRegions());
        info("Zaladowano poprawne wpisy regionow: " + getConfiguredRegionCount() + ".");
    }

    private Map<String, Map<String, ConfiguredRenderRegion>> validateRegions(Map<String, RegionDistanceSettings> configuredRegions) {
        if (configuredRegions == null || configuredRegions.isEmpty()) {
            return Map.of();
        }

        Map<String, Map<String, ConfiguredRenderRegion>> validated = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();

        for (Map.Entry<String, RegionDistanceSettings> entry : configuredRegions.entrySet()) {
            String configKey = entry.getKey();
            RegionDistanceSettings settings = entry.getValue();
            ConfiguredRenderRegion region = validateRegion(configKey, settings, duplicates);
            if (region == null) {
                continue;
            }
            validated
                .computeIfAbsent(normalize(region.worldName()), ignored -> new LinkedHashMap<>())
                .put(region.normalizedRegionId(), region);
        }

        Map<String, Map<String, ConfiguredRenderRegion>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ConfiguredRenderRegion>> entry : validated.entrySet()) {
            immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private ConfiguredRenderRegion validateRegion(String configKey, RegionDistanceSettings settings, Set<String> duplicates) {
        if (configKey == null || configKey.isBlank()) {
            warning("Pominieto wpis bez nazwy: nazwa wpisu nie moze byc pusta.");
            return null;
        }
        if (settings == null) {
            warning("Pominieto wpis " + configKey + ": brak sekcji ustawien.");
            return null;
        }
        if (isBlank(settings.getWorld())) {
            warning("Pominieto wpis " + configKey + ": brakuje pola world.");
            return null;
        }
        if (isBlank(settings.getRegion())) {
            warning("Pominieto wpis " + configKey + ": brakuje pola region.");
            return null;
        }
        World world = findWorld(settings.getWorld());
        if (world == null) {
            warning("Pominieto wpis " + configKey + ": swiat " + settings.getWorld() + " nie istnieje.");
            return null;
        }

        String actualRegionId = regionProvider.findRegionId(world, settings.getRegion());
        if (actualRegionId == null) {
            warning("Pominieto wpis " + configKey + ": region " + settings.getRegion() + " nie istnieje w swiecie " + world.getName() + ".");
            return null;
        }

        String duplicateKey = normalize(world.getName()) + ":" + normalize(actualRegionId);
        if (!duplicates.add(duplicateKey)) {
            warning("Pominieto wpis " + configKey + ": region " + actualRegionId + " w swiecie " + world.getName() + " zostal wpisany wiecej niz raz.");
            return null;
        }

        Integer renderDistance = validateRegionDistance(configKey, settings.getRenderDistance());
        if (renderDistance == null) {
            return null;
        }

        return new ConfiguredRenderRegion(
            configKey,
            world.getName(),
            actualRegionId,
            normalize(actualRegionId),
            settings.getPriority(),
            renderDistance
        );
    }

    private Integer validateRegionDistance(String configKey, Integer value) {
        if (value == null) {
            warning("Pominieto wpis " + configKey + ": brakuje pola render-distance.");
            return null;
        }
        if (value == -1) {
            return -1;
        }
        if (value >= MIN_DISTANCE && value <= MAX_DISTANCE) {
            return value;
        }
        if (value > MAX_DISTANCE) {
            warning("Wpis " + configKey + ": render-distance " + value + " przekracza limit " + MAX_DISTANCE + ", ograniczono do " + MAX_DISTANCE + ".");
            return MAX_DISTANCE;
        }
        warning("Pominieto wpis " + configKey + ": render-distance musi miec wartosc -1 albo zakres " + MIN_DISTANCE + "-" + MAX_DISTANCE + " chunkow.");
        return null;
    }

    private void validateDefaults() {
        DefaultDistanceSettings defaults = config == null ? null : config.getDefaults();
        if (defaults == null) {
            this.defaultRenderDistance = -1;
            return;
        }
        this.defaultRenderDistance = validateDefaultDistance(defaults.getRenderDistance());
    }

    private int validateDefaultDistance(int value) {
        if (value == -1) {
            return -1;
        }
        if (value >= MIN_DISTANCE && value <= MAX_DISTANCE) {
            return value;
        }
        if (value > MAX_DISTANCE) {
            warning("defaults.render-distance " + value + " przekracza limit " + MAX_DISTANCE + ", ograniczono do " + MAX_DISTANCE + ".");
            return MAX_DISTANCE;
        }
        warning("defaults.render-distance ma niepoprawna wartosc " + value + ", ustawiono -1.");
        return -1;
    }

    private void restartScheduler() {
        cancelScheduler();
        if (!isOperational() || !hasVisibilityRules()) {
            return;
        }
        long interval = Math.max(1L, config.getCheckIntervalTicks());
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushDirtyPlayers, interval, interval);
    }

    private void cancelScheduler() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void queueOnlinePlayersNow() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (!isOperational() || !hasVisibilityRules()) {
            showAllHiddenPlayers();
            return;
        }
        for (Player player : players) {
            updatePlayer(player, true);
        }
    }

    private void flushDirtyPlayers() {
        ensurePrimaryThread();
        if (dirtyPlayers.isEmpty()) {
            return;
        }

        List<UUID> queuedPlayers = new ArrayList<>(dirtyPlayers);
        dirtyPlayers.clear();
        for (UUID uuid : queuedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                removePlayer(uuid);
                continue;
            }
            updatePlayer(player, false);
        }
    }

    private void updatePlayer(Player player, boolean force) {
        if (player == null || !player.isOnline()) {
            return;
        }

        PlayerRenderDistanceCache cache = playerCache.computeIfAbsent(player.getUniqueId(), PlayerRenderDistanceCache::new);
        long currentTick = Bukkit.getCurrentTick();
        if (!force && cache.getLastUpdatedTick() == currentTick) {
            return;
        }

        Location location = player.getLocation();
        if (!force && cache.matchesLocation(location, config.isCheckOnlyOnBlockChange())) {
            return;
        }

        if (!isOperational() || !hasVisibilityRules()) {
            applyDefaultVisibility(player, cache);
            cache.updateLocation(location);
            cache.setLastUpdatedTick(currentTick);
            return;
        }

        RenderDistanceSelection selection = selectRegion(location);
        applySelection(player, cache, selection);
        cache.updateLocation(location);
        cache.setLastUpdatedTick(currentTick);
        refreshViewersForTarget(player);
    }

    private RenderDistanceSelection selectRegion(Location location) {
        if (location == null || location.getWorld() == null || regionProvider == null) {
            return createDefaultSelection();
        }

        Map<String, ConfiguredRenderRegion> configuredRegions = regionsByWorld.get(normalize(location.getWorld().getName()));
        if (configuredRegions == null || configuredRegions.isEmpty()) {
            return createDefaultSelection();
        }

        ConfiguredRenderRegion bestRegion = null;
        int bestWorldGuardPriority = Integer.MIN_VALUE;
        for (WorldGuardRegionSnapshot snapshot : regionProvider.getApplicableRegions(location)) {
            ConfiguredRenderRegion configuredRegion = configuredRegions.get(normalize(snapshot.id()));
            if (configuredRegion == null) {
                continue;
            }
            if (isBetter(configuredRegion, snapshot.priority(), bestRegion, bestWorldGuardPriority)) {
                bestRegion = configuredRegion;
                bestWorldGuardPriority = snapshot.priority();
            }
        }

        if (bestRegion == null) {
            return createDefaultSelection();
        }

        int renderDistance = resolveConfiguredDistance(bestRegion.renderDistance(), defaultRenderDistance);
        return new RenderDistanceSelection(bestRegion, renderDistance, false);
    }

    private boolean isBetter(ConfiguredRenderRegion candidate, int candidateWorldGuardPriority, ConfiguredRenderRegion current, int currentWorldGuardPriority) {
        if (current == null) {
            return true;
        }
        if (candidate.priority() != current.priority()) {
            return candidate.priority() > current.priority();
        }
        if (candidateWorldGuardPriority != currentWorldGuardPriority) {
            return candidateWorldGuardPriority > currentWorldGuardPriority;
        }
        int ignoreCase = candidate.configKey().compareToIgnoreCase(current.configKey());
        if (ignoreCase != 0) {
            return ignoreCase < 0;
        }
        return candidate.configKey().compareTo(current.configKey()) < 0;
    }

    private RenderDistanceSelection createDefaultSelection() {
        return new RenderDistanceSelection(null, defaultRenderDistance, true);
    }

    private int resolveConfiguredDistance(int configuredDistance, int defaultDistance) {
        return configuredDistance == -1 ? defaultDistance : configuredDistance;
    }

    private void applySelection(Player player, PlayerRenderDistanceCache cache, RenderDistanceSelection selection) {
        int renderDistance = selection.renderDistance();

        cache.setLastActiveEntryName(selection.defaults() ? "defaults" : selection.entryName());
        cache.setLastWorldGuardRegionName(selection.defaults() ? "-" : selection.worldGuardRegionName());
        cache.setLastRenderDistance(renderDistance);
        cache.setUsingDefaults(selection.defaults());

        refreshViewerVisibility(player, cache);
        debug("Ustawiono " + player.getName() + ": entry=" + cache.getLastActiveEntryName()
            + ", player-visibility=" + formatDistance(renderDistance) + ".");
    }

    private void applyDefaultVisibility(Player player, PlayerRenderDistanceCache cache) {
        cache.setLastActiveEntryName("defaults");
        cache.setLastWorldGuardRegionName("-");
        cache.setLastRenderDistance(defaultRenderDistance);
        cache.setUsingDefaults(true);
        refreshViewerVisibility(player, cache);
    }

    private void refreshViewerVisibility(Player viewer, PlayerRenderDistanceCache cache) {
        int renderDistance = cache.getLastRenderDistance();
        if (renderDistance == -1) {
            showAllForViewer(viewer, cache);
            return;
        }

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.getUniqueId().equals(viewer.getUniqueId())) {
                updateTargetVisibility(viewer, cache, target, renderDistance);
            }
        }
    }

    private void refreshViewersForTarget(Player target) {
        for (Map.Entry<UUID, PlayerRenderDistanceCache> entry : playerCache.entrySet()) {
            if (entry.getKey().equals(target.getUniqueId())) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            PlayerRenderDistanceCache viewerCache = entry.getValue();
            int renderDistance = viewerCache.getLastRenderDistance();
            if (renderDistance == Integer.MIN_VALUE) {
                continue;
            }
            updateTargetVisibility(viewer, viewerCache, target, renderDistance);
        }
    }

    private void updateTargetVisibility(Player viewer, PlayerRenderDistanceCache cache, Player target, int renderDistance) {
        if (shouldSeeTarget(viewer, target, renderDistance)) {
            showTarget(viewer, cache, target);
            return;
        }
        hideTarget(viewer, cache, target);
    }

    private boolean shouldSeeTarget(Player viewer, Player target, int renderDistance) {
        if (renderDistance == -1) {
            return true;
        }
        if (viewer == null || target == null || !viewer.isOnline() || !target.isOnline()) {
            return true;
        }
        if (!Objects.equals(viewer.getWorld(), target.getWorld())) {
            return true;
        }
        if (renderDistance == 0) {
            return false;
        }

        double maxDistance = (double) renderDistance * BLOCKS_PER_CHUNK;
        return viewer.getLocation().distanceSquared(target.getLocation()) <= maxDistance * maxDistance;
    }

    private void hideTarget(Player viewer, PlayerRenderDistanceCache cache, Player target) {
        if (viewer.equals(target)) {
            return;
        }
        if (cache.getHiddenPlayers().add(target.getUniqueId())) {
            viewer.hideEntity(plugin, target);
        }
    }

    private void showTarget(Player viewer, PlayerRenderDistanceCache cache, Player target) {
        if (plugin.getVanishManager() != null && plugin.getVanishManager().shouldHideFrom(viewer, target)) {
            hideTarget(viewer, cache, target);
            return;
        }
        if (cache.getHiddenPlayers().remove(target.getUniqueId())) {
            viewer.showEntity(plugin, target);
        }
    }

    private void showAllHiddenPlayers() {
        for (Map.Entry<UUID, PlayerRenderDistanceCache> entry : playerCache.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer == null || !viewer.isOnline()) {
                entry.getValue().getHiddenPlayers().clear();
                continue;
            }
            showAllForViewer(viewer, entry.getValue());
        }
    }

    private void showAllForViewer(Player viewer, PlayerRenderDistanceCache cache) {
        List<UUID> hiddenPlayers = new ArrayList<>(cache.getHiddenPlayers());
        for (UUID hiddenUuid : hiddenPlayers) {
            Player hiddenPlayer = Bukkit.getPlayer(hiddenUuid);
            if (hiddenPlayer != null && hiddenPlayer.isOnline()) {
                if (plugin.getVanishManager() != null && plugin.getVanishManager().shouldHideFrom(viewer, hiddenPlayer)) {
                    continue;
                }
                viewer.showEntity(plugin, hiddenPlayer);
            }
        }
        cache.getHiddenPlayers().clear();
    }

    private World findWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getName().equalsIgnoreCase(worldName)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasConfiguredRegions() {
        return !regionsByWorld.isEmpty();
    }

    private boolean hasVisibilityRules() {
        return defaultRenderDistance != -1 || hasConfiguredRegions();
    }

    private boolean hasConfiguredRegionsInWorld(World world) {
        return world != null && regionsByWorld.containsKey(normalize(world.getName()));
    }

    private String formatDistance(int renderDistance) {
        if (renderDistance == -1) {
            return "bez limitu";
        }
        if (renderDistance == 0) {
            return "ukrywa wszystkich graczy";
        }
        return renderDistance + " chunkow";
    }

    private void ensurePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("RenderDistance operations must run on the main thread");
        }
    }

    private void warning(String message) {
        plugin.getLogger().warning(LOG_PREFIX + message);
    }

    private void info(String message) {
        plugin.getLogger().info(LOG_PREFIX + message);
    }

    private void debug(String message) {
        if (config != null && config.isDebug()) {
            plugin.getLogger().info(LOG_PREFIX + message);
        }
    }
}
