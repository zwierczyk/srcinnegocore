package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.config.configs.AntiLagConfig;
import dev.grzybalson.utils.ChunkAccess;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EntityLimitListener implements Listener {

    private static final long CONFIG_REFRESH_TICKS = 20L;
    private static final int IDX_REDSTONE = 0;
    private static final int IDX_PISTON = 1;
    private static final int IDX_HOPPER = 2;

    private final HollyCore plugin;
    private final ArrayDeque<ChunkRef> queuedChunks = new ArrayDeque<>();
    private final Set<ChunkRef> queuedChunkKeys = new HashSet<>();
    private final ArrayDeque<ChunkRef> periodicChunks = new ArrayDeque<>();
    private final Set<ChunkRef> periodicChunkKeys = new HashSet<>();
    private final Set<ChunkRef> knownLoadedChunks = new HashSet<>();
    private final Map<World, Map<Long, int[]>> mechanicTicks = new HashMap<>();
    private BukkitTask cleanupTask;
    private AntiLagSettings settings;
    private long nextConfigRefreshTick;
    private long nextPeriodicCleanupTick;
    private long mechanicTick = Long.MIN_VALUE;
    private boolean periodicScanActive;
    private volatile boolean active = false;

    public EntityLimitListener(HollyCore plugin) {
        this.plugin = plugin;
        refreshCachedSettings();
        startCleanupTask();
    }

    private AntiLagSettings settings() {
        long currentTick = Bukkit.getCurrentTick();
        if (currentTick >= nextConfigRefreshTick) {
            refreshCachedSettings();
            nextConfigRefreshTick = currentTick + CONFIG_REFRESH_TICKS;
        }
        return settings;
    }

    private void refreshCachedSettings() {
        settings = AntiLagSettings.from(plugin.getConfigManager().getAntiLagConfig());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isCudownaLatarniaItem(event)) {
            return;
        }

        AntiLagSettings config = settings();
        Material type = event.getBlockPlaced().getType();
        if (config.blockedPlaceMaterials().contains(type.name())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtils.colorizeComponent(config.blockedBlockMessage()));
            return;
        }
        if (type == Material.HOPPER) {
            int limit = config.maxHoppersPerChunk();
            if (limit > 0 && countHoppers(event.getBlockPlaced().getChunk()) > limit) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ColorUtils.colorizeComponent(format(config.hopperLimitMessage(), limit)));
            }
        }
    }

    private boolean isCudownaLatarniaItem(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        return inHand != null
            && inHand.getType() == Material.BEACON
            && plugin.isAddonEnabled("anarchiagg")
            && EventItemRegistry.matches(plugin, "cudownalatarnia", inHand);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) {
            return;
        }

        AntiLagSettings config = settings();
        Chunk chunk = event.getEntity().getLocation().getChunk();
        int limit = config.maxArmorStandsPerChunk();
        if (limit <= 0) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().sendMessage(ColorUtils.colorizeComponent(format(config.armorStandLimitMessage(), 0)));
            }
            queueChunk(chunk, config);
            return;
        }
        if (countEntities(chunk, config, ArmorStand.class) >= limit) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().sendMessage(ColorUtils.colorizeComponent(format(config.armorStandLimitMessage(), limit)));
            }
            queueChunk(chunk, config);
            return;
        }
        queueChunk(chunk, config);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        AntiLagSettings config = settings();
        Chunk chunk = event.getEntity().getLocation().getChunk();

        if (event.getEntity() instanceof ItemFrame || event.getEntity() instanceof GlowItemFrame) {
            int limit = config.maxItemFramesPerChunk();
            if (limit <= 0 || countEntities(chunk, config, ItemFrame.class, GlowItemFrame.class) >= limit) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(ColorUtils.colorizeComponent(format(config.itemFrameLimitMessage(), Math.max(limit, 0))));
                }
                queueChunk(chunk, config);
                return;
            }
            queueChunk(chunk, config);
            return;
        }

        if (event.getEntity() instanceof Painting) {
            int limit = config.maxPaintingsPerChunk();
            if (limit <= 0 || countEntities(chunk, config, Painting.class) >= limit) {
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(ColorUtils.colorizeComponent(format(config.paintingLimitMessage(), Math.max(limit, 0))));
                }
                queueChunk(chunk, config);
                return;
            }
            queueChunk(chunk, config);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Material type = event.getItem().getType();
        if (!isMinecartItem(type) && !isBoatItem(type) && type != Material.END_CRYSTAL) {
            return;
        }

        AntiLagSettings config = settings();
        Chunk chunk = event.getClickedBlock() != null ? event.getClickedBlock().getChunk() : event.getPlayer().getLocation().getChunk();

        if (isMinecartItem(type)) {
            int limit = config.maxMinecartsPerChunk();
            if (limit <= 0 || countEntities(chunk, config, Minecart.class) >= limit) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ColorUtils.colorizeComponent(format(config.minecartLimitMessage(), Math.max(limit, 0))));
                queueChunk(chunk, config);
            }
            return;
        }

        if (isBoatItem(type)) {
            int limit = config.maxBoatsPerChunk();
            if (limit <= 0 || countEntities(chunk, config, Boat.class) >= limit) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ColorUtils.colorizeComponent(format(config.boatLimitMessage(), Math.max(limit, 0))));
                queueChunk(chunk, config);
            }
            return;
        }

        if (type == Material.END_CRYSTAL) {
            int limit = config.maxEndCrystalsPerChunk();
            if (limit <= 0 || countEntities(chunk, config, EnderCrystal.class) >= limit) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ColorUtils.colorizeComponent(format(config.endCrystalLimitMessage(), Math.max(limit, 0))));
                queueChunk(chunk, config);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        registerLoadedChunk(event.getChunk());
        if (active) {
            queueChunk(event.getChunk(), settings());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkRef ref = new ChunkRef(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        knownLoadedChunks.remove(ref);
        queuedChunkKeys.remove(ref);
        periodicChunkKeys.remove(ref);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        AntiLagSettings config = settings();
        if (config.disableRedstone()) {
            event.setNewCurrent(0);
            return;
        }
        int max = config.maxRedstoneUpdatesPerChunkTick();
        if (max > 0 && bumpMechanic(event.getBlock().getChunk(), IDX_REDSTONE) > max) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (limitPiston(event.getBlock().getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (limitPiston(event.getBlock().getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (settings().disableDispensersAndDroppers()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        AntiLagSettings config = settings();
        if (config.disableHoppers()) {
            event.setCancelled(true);
            return;
        }
        int max = config.maxHopperMovesPerChunkTick();
        if (max <= 0 || !isHopperTransfer(event)) {
            return;
        }
        Location location = event.getSource().getLocation();
        if (location == null) {
            location = event.getDestination().getLocation();
        }
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (bumpMechanic(location.getChunk(), IDX_HOPPER) > max) {
            event.setCancelled(true);
        }
    }

    private boolean limitPiston(Chunk chunk) {
        AntiLagSettings config = settings();
        if (config.disablePistons()) {
            return true;
        }
        int max = config.maxPistonEventsPerChunkTick();
        return max > 0 && bumpMechanic(chunk, IDX_PISTON) > max;
    }

    private int bumpMechanic(Chunk chunk, int index) {
        long tick = Bukkit.getCurrentTick();
        if (tick != mechanicTick) {
            mechanicTicks.clear();
            mechanicTick = tick;
        }
        int[] counters = mechanicTicks
            .computeIfAbsent(chunk.getWorld(), world -> new HashMap<>())
            .computeIfAbsent(chunk.getChunkKey(), chunkKey -> new int[3]);
        return ++counters[index];
    }

    private static boolean isHopperTransfer(InventoryMoveItemEvent event) {
        return isHopperHolder(event.getSource().getHolder(false)) || isHopperHolder(event.getDestination().getHolder(false));
    }

    private static boolean isHopperHolder(InventoryHolder holder) {
        return holder instanceof Hopper || holder instanceof HopperMinecart;
    }

    private static int countHoppers(Chunk chunk) {
        int count = 0;
        for (BlockState state : chunk.getTileEntities(false)) {
            if (state.getType() == Material.HOPPER) {
                count++;
            }
        }
        return count;
    }

    @SafeVarargs
    private final long countEntities(Chunk chunk, AntiLagSettings config, Class<? extends Entity>... classes) {
        long count = 0;
        for (Entity entity : chunk.getEntities()) {
            for (Class<? extends Entity> clazz : classes) {
                if (clazz.isInstance(entity)) {
                    if (!isIgnoredByLimit(entity, config)) {
                        count++;
                    }
                    break;
                }
            }
        }
        return count;
    }

    private String format(String message, int limit) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message.replace("{limit}", String.valueOf(limit));
    }

    private boolean isMinecartItem(Material material) {
        return material.name().endsWith("MINECART");
    }

    private boolean isBoatItem(Material material) {
        String name = material.name().toUpperCase(Locale.ROOT);
        return name.endsWith("_BOAT") || name.endsWith("_CHEST_BOAT") || name.equals("BOAT");
    }

    private void startCleanupTask() {
        cleanupTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runCleanupTick, 20L, 20L);
    }

    private void runCleanupTick() {
        if (!active) {
            return;
        }

        AntiLagSettings config = settings();
        if (!config.cleanupExcessEntities()) {
            queuedChunks.clear();
            queuedChunkKeys.clear();
            periodicChunks.clear();
            periodicChunkKeys.clear();
            periodicScanActive = false;
            return;
        }

        int chunksPerRun = Math.max(1, config.entityCleanupChunksPerRun());
        int processed = cleanupQueuedChunks(chunksPerRun, config);

        long currentTick = Bukkit.getCurrentTick();
        if (!periodicScanActive && currentTick >= nextPeriodicCleanupTick) {
            refillPeriodicQueue();
            periodicScanActive = !periodicChunks.isEmpty();
            nextPeriodicCleanupTick = currentTick + Math.max(20L, config.entityCleanupIntervalTicks());
        }
        if (periodicScanActive && processed < chunksPerRun) {
            cleanupPeriodicChunks(chunksPerRun - processed, config);
        }
    }

    private int cleanupQueuedChunks(int limit, AntiLagSettings config) {
        int processed = 0;
        while (processed < limit && !queuedChunks.isEmpty()) {
            ChunkRef ref = queuedChunks.pollFirst();
            queuedChunkKeys.remove(ref);

            if (cleanupChunk(ref, config)) {
                processed++;
            }
        }
        return processed;
    }

    private int cleanupPeriodicChunks(int limit, AntiLagSettings config) {
        int processed = 0;
        while (processed < limit && !periodicChunks.isEmpty()) {
            ChunkRef ref = periodicChunks.pollFirst();
            periodicChunkKeys.remove(ref);

            if (cleanupChunk(ref, config)) {
                processed++;
            }
        }

        if (periodicChunks.isEmpty()) {
            periodicScanActive = false;
        }
        return processed;
    }

    private boolean cleanupChunk(ChunkRef ref, AntiLagSettings config) {
        World world = plugin.getServer().getWorld(ref.worldName());
        Chunk chunk = ChunkAccess.getLoadedChunk(world, ref.x(), ref.z());
        if (chunk == null) {
            knownLoadedChunks.remove(ref);
            return false;
        }

        cleanupChunk(chunk, config);
        return true;
    }

    private void cleanupChunk(Chunk chunk, AntiLagSettings config) {
        if (isChunkProtectedForCreativeBuild(chunk)) {
            return;
        }

        int removed = 0;
        removed += removeExcess(chunk, config.maxArmorStandsPerChunk(), config, ArmorStand.class);
        removed += removeExcess(chunk, config.maxItemFramesPerChunk(), config, ItemFrame.class, GlowItemFrame.class);
        removed += removeExcess(chunk, config.maxPaintingsPerChunk(), config, Painting.class);
        removed += removeExcess(chunk, config.maxMinecartsPerChunk(), config, Minecart.class);
        removed += removeExcess(chunk, config.maxBoatsPerChunk(), config, Boat.class);
        removed += removeExcess(chunk, config.maxEndCrystalsPerChunk(), config, EnderCrystal.class);

        if (removed > 0 && config.logEntityCleanup()) {
            plugin.getLogger().info("[EntityLimit] Usunieto " + removed + " nadmiarowych entity w "
                + chunk.getWorld().getName() + " chunk " + chunk.getX() + "," + chunk.getZ() + ".");
        }
    }

    private boolean isChunkProtectedForCreativeBuild(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return false;
        }
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        World world = chunk.getWorld();
        for (Player player : world.getPlayers()) {
            GameMode mode = player.getGameMode();
            if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR) {
                continue;
            }
            Chunk playerChunk = player.getLocation().getChunk();
            if (Math.abs(playerChunk.getX() - chunkX) <= 2 && Math.abs(playerChunk.getZ() - chunkZ) <= 2) {
                return true;
            }
        }
        return false;
    }

    @SafeVarargs
    private final int removeExcess(Chunk chunk, int limit, AntiLagSettings config, Class<? extends Entity>... classes) {
        int effectiveLimit = Math.max(0, limit);
        List<Entity> matchingEntities = new ArrayList<>();

        for (Entity entity : chunk.getEntities()) {
            if (isIgnoredByLimit(entity, config)) {
                continue;
            }
            for (Class<? extends Entity> clazz : classes) {
                if (clazz.isInstance(entity)) {
                    matchingEntities.add(entity);
                    break;
                }
            }
        }

        if (matchingEntities.size() <= effectiveLimit) {
            return 0;
        }

        matchingEntities.sort(
            Comparator.comparingInt(Entity::getTicksLived).reversed()
                .thenComparing(entity -> entity.getUniqueId().toString())
        );

        int removed = 0;
        for (int i = effectiveLimit; i < matchingEntities.size(); i++) {
            Entity entity = matchingEntities.get(i);
            if (!entity.isDead()) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private void seedLoadedChunksAtStartup() {
        AntiLagSettings config = settings();
        if (!config.cleanupExcessEntities()) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                registerLoadedChunk(chunk);
                queueChunk(chunk, config);
            }
        }
    }

    private void refillPeriodicQueue() {
        periodicChunks.clear();
        periodicChunkKeys.clear();
        for (ChunkRef ref : knownLoadedChunks) {
            if (periodicChunkKeys.add(ref)) {
                periodicChunks.addLast(ref);
            }
        }
    }

    private void queueChunk(Chunk chunk, AntiLagSettings config) {
        if (chunk == null || !active || !config.cleanupExcessEntities()) {
            return;
        }

        ChunkRef ref = registerLoadedChunk(chunk);
        if (queuedChunkKeys.add(ref)) {
            queuedChunks.addLast(ref);
        }
    }

    private ChunkRef registerLoadedChunk(Chunk chunk) {
        ChunkRef ref = new ChunkRef(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        knownLoadedChunks.add(ref);
        return ref;
    }

    private boolean isIgnoredByLimit(Entity entity, AntiLagSettings config) {
        if (entity == null || config.entityLimitIgnoredMetadata().isEmpty()) {
            return false;
        }
        for (String metadataKey : config.entityLimitIgnoredMetadata()) {
            if (entity.hasMetadata(metadataKey)) {
                return true;
            }
        }
        return false;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (!active) {
            queuedChunks.clear();
            queuedChunkKeys.clear();
            periodicChunks.clear();
            periodicChunkKeys.clear();
            periodicScanActive = false;
        } else {
            seedLoadedChunksAtStartup();
        }
    }

    public record ManualCleanupResult(int chunksProcessed, int entitiesRemoved) {}

    public ManualCleanupResult runManualCleanup() {
        AntiLagSettings config = settings();
        int chunksProcessed = 0;
        int totalRemoved = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                chunksProcessed++;
                totalRemoved += cleanupChunkAndCount(chunk, config);
            }
        }
        return new ManualCleanupResult(chunksProcessed, totalRemoved);
    }

    private int cleanupChunkAndCount(Chunk chunk, AntiLagSettings config) {
        int removed = 0;
        removed += removeExcess(chunk, config.maxArmorStandsPerChunk(), config, ArmorStand.class);
        removed += removeExcess(chunk, config.maxItemFramesPerChunk(), config, ItemFrame.class, GlowItemFrame.class);
        removed += removeExcess(chunk, config.maxPaintingsPerChunk(), config, Painting.class);
        removed += removeExcess(chunk, config.maxMinecartsPerChunk(), config, Minecart.class);
        removed += removeExcess(chunk, config.maxBoatsPerChunk(), config, Boat.class);
        removed += removeExcess(chunk, config.maxEndCrystalsPerChunk(), config, EnderCrystal.class);
        return removed;
    }

    private record ChunkRef(String worldName, int x, int z) {
    }

    private record AntiLagSettings(
        boolean disableRedstone,
        boolean disablePistons,
        boolean disableDispensersAndDroppers,
        boolean disableHoppers,
        int maxRedstoneUpdatesPerChunkTick,
        int maxPistonEventsPerChunkTick,
        int maxHopperMovesPerChunkTick,
        int maxHoppersPerChunk,
        int maxArmorStandsPerChunk,
        int maxMinecartsPerChunk,
        int maxBoatsPerChunk,
        int maxItemFramesPerChunk,
        int maxPaintingsPerChunk,
        int maxEndCrystalsPerChunk,
        boolean cleanupExcessEntities,
        int entityCleanupIntervalTicks,
        int entityCleanupChunksPerRun,
        boolean logEntityCleanup,
        String blockedBlockMessage,
        String armorStandLimitMessage,
        String minecartLimitMessage,
        String boatLimitMessage,
        String itemFrameLimitMessage,
        String paintingLimitMessage,
        String endCrystalLimitMessage,
        String hopperLimitMessage,
        Set<String> entityLimitIgnoredMetadata,
        Set<String> blockedPlaceMaterials
    ) {
        private static AntiLagSettings from(AntiLagConfig config) {
            Set<String> ignoredMetadata = new HashSet<>();
            for (String key : config.getEntityLimitIgnoredMetadata()) {
                if (key != null && !key.isBlank()) {
                    ignoredMetadata.add(key.trim());
                }
            }

            Set<String> blockedMaterials = new HashSet<>();
            for (String material : config.getBlockedPlaceMaterials()) {
                if (material != null && !material.isBlank()) {
                    blockedMaterials.add(material.trim().toUpperCase(Locale.ROOT));
                }
            }

            return new AntiLagSettings(
                config.isDisableRedstone(),
                config.isDisablePistons(),
                config.isDisableDispensersAndDroppers(),
                config.isDisableHoppers(),
                Math.max(0, config.getMaxRedstoneUpdatesPerChunkTick()),
                Math.max(0, config.getMaxPistonEventsPerChunkTick()),
                Math.max(0, config.getMaxHopperMovesPerChunkTick()),
                Math.max(0, config.getMaxHoppersPerChunk()),
                config.getMaxArmorStandsPerChunk(),
                config.getMaxMinecartsPerChunk(),
                config.getMaxBoatsPerChunk(),
                config.getMaxItemFramesPerChunk(),
                config.getMaxPaintingsPerChunk(),
                config.getMaxEndCrystalsPerChunk(),
                config.isCleanupExcessEntities(),
                Math.max(20, config.getEntityCleanupIntervalTicks()),
                Math.max(1, config.getEntityCleanupChunksPerRun()),
                config.isLogEntityCleanup(),
                config.getBlockedBlockMessage(),
                config.getArmorStandLimitMessage(),
                config.getMinecartLimitMessage(),
                config.getBoatLimitMessage(),
                config.getItemFrameLimitMessage(),
                config.getPaintingLimitMessage(),
                config.getEndCrystalLimitMessage(),
                config.getHopperLimitMessage(),
                Set.copyOf(ignoredMetadata),
                Set.copyOf(blockedMaterials)
            );
        }
    }
}
