package dev.grzybalson.anarchiagg.szafa.papugi;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class PapugiVisualHandler implements Listener {
    private static final String PAPUGA_ENTITY_TAG = "holly_shoulder_papuga";
    private static final long MOVE_SYNC_COOLDOWN_MS = 5000L;

    private final JavaPlugin plugin;
    private final PapugiHandler papugiHandler;
    private final NamespacedKey papugaMarkerKey;
    private final Map<UUID, Long> lastMoveSync = new ConcurrentHashMap<>();
    private final Map<UUID, VisualState> visualStates = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> queuedSyncs = ConcurrentHashMap.newKeySet();

    public PapugiVisualHandler(JavaPlugin plugin, PapugiHandler papugiHandler) {
        this.plugin = plugin;
        this.papugiHandler = papugiHandler;
        this.papugaMarkerKey = new NamespacedKey(plugin, "shoulder_papuga_visual");
        this.startTask();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.scheduleSync(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        this.scheduleSync(event.getPlayer(), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.lastMoveSync.remove(uuid);
        this.visualStates.remove(uuid);
        this.queuedSyncs.remove(uuid);
        this.clearNearbyTaggedPapugi(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long previous = this.lastMoveSync.get(player.getUniqueId());
        if (previous != null && now - previous < MOVE_SYNC_COOLDOWN_MS) {
            return;
        }
        this.lastMoveSync.put(player.getUniqueId(), now);
        this.scheduleSync(player, 0L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        this.scheduleSync(event.getPlayer(), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            this.scheduleSync(player, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPapugaDamage(EntityDamageEvent event) {
        if (this.isVisualPapuga(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPapugaDamageByEntity(EntityDamageByEntityEvent event) {
        if (this.isVisualPapuga(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPapugaTarget(EntityTargetEvent event) {
        if (this.isVisualPapuga(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPapugaInteract(PlayerInteractEntityEvent event) {
        if (this.isVisualPapuga(event.getRightClicked())) {
            event.setCancelled(true);
            event.getRightClicked().remove();
            this.visualStates.remove(event.getPlayer().getUniqueId());
            this.syncPapuga(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPapugaInteractAt(PlayerInteractAtEntityEvent event) {
        if (this.isVisualPapuga(event.getRightClicked())) {
            event.setCancelled(true);
            event.getRightClicked().remove();
            this.visualStates.remove(event.getPlayer().getUniqueId());
            this.syncPapuga(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPapugaSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Parrot parrot)) {
            return;
        }
        if (!this.isVisualPapuga(parrot)) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            parrot.remove();
            Player owner = this.findOwningPlayer(parrot);
            if (owner != null) {
                this.syncPapuga(owner);
            }
        });
    }

    private void startTask() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.scheduleSync(player, 0L);
            }
        }, 40L, 600L);
    }

    private void scheduleSync(Player player, long delayTicks) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!this.queuedSyncs.add(uuid)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            try {
                if (player.isOnline()) {
                    this.syncPapuga(player);
                }
            } finally {
                this.queuedSyncs.remove(uuid);
            }
        }, Math.max(0L, delayTicks));
    }

    public void syncPapuga(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        PapugiDatabase.PapugaRecord record = this.papugiHandler.getDatabase().getActivePapugaData(player.getUniqueId());
        if (!record.hasActivePapuga()) {
            this.clearShoulderPapuga(player);
            return;
        }

        String expiration = record.expirationDate();
        if (expiration != null && !expiration.isBlank()) {
            try {
                LocalDateTime expirationTime = LocalDateTime.parse(expiration, PapugiHandler.DATE_FORMATTER);
                if (LocalDateTime.now().isAfter(expirationTime)) {
                    this.papugiHandler.getDatabase().removeActivePapuga(player.getUniqueId());
                    this.clearShoulderPapuga(player);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        PapugiHandler.PapugaData papugaData = this.papugiHandler.getPapuga(record.activePapuga());
        if (papugaData == null) {
            this.clearShoulderPapuga(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        if (hasCachedVisual(uuid, record.activePapuga(), papugaData)) {
            return;
        }

        if (this.hasMatchingPapuga(player, papugaData)) {
            this.visualStates.put(uuid, VisualState.from(record.activePapuga(), papugaData));
        } else {
            this.placePapuga(player, record.activePapuga(), papugaData, 1);
        }
    }

    public void clearShoulderPapuga(Player player) {
        this.visualStates.remove(player.getUniqueId());
        this.clearShoulder(player, true);
        this.clearShoulder(player, false);
        this.clearNearbyTaggedPapugi(player);
    }

    private boolean hasCachedVisual(UUID uuid, String activePapuga, PapugiHandler.PapugaData papugaData) {
        VisualState state = this.visualStates.get(uuid);
        return state != null && state.matches(activePapuga, papugaData);
    }

    private void clearShoulder(Player player, boolean left) {
        Entity current = left ? player.getShoulderEntityLeft() : player.getShoulderEntityRight();
        if (current == null) {
            return;
        }
        try {
            if (left) {
                player.setShoulderEntityLeft(null);
            } else {
                player.setShoulderEntityRight(null);
            }
        } catch (Exception ignored) {
            Entity released = left ? player.releaseLeftShoulderEntity() : player.releaseRightShoulderEntity();
            if (released != null) {
                released.remove();
            }
        }
        current.remove();
    }

    private boolean hasMatchingPapuga(Player player, PapugiHandler.PapugaData papugaData) {
        boolean left = papugaData.shoulder().equalsIgnoreCase("left");
        Entity current = left ? player.getShoulderEntityLeft() : player.getShoulderEntityRight();
        if (!(current instanceof Parrot parrot)) {
            return false;
        }
        return parrot.getVariant() == papugaData.variant();
    }

    private void placePapuga(Player player, String activePapuga, PapugiHandler.PapugaData papugaData, int retriesLeft) {
        boolean left = papugaData.shoulder().equalsIgnoreCase("left");
        this.clearShoulderPapuga(player);
        Location location = this.getHiddenSpawnLocation(player.getWorld(), player.getLocation());
        Entity spawned = player.getWorld().spawnEntity(location, EntityType.PARROT);
        if (!(spawned instanceof Parrot parrot)) {
            spawned.remove();
            return;
        }
        this.markAsVisualPapuga(parrot);
        parrot.setVariant(papugaData.variant());
        parrot.setSilent(true);
        parrot.setAI(false);
        parrot.setCollidable(false);
        parrot.setInvulnerable(true);
        parrot.setCanPickupItems(false);
        parrot.setGravity(false);
        parrot.setCustomName(null);
        parrot.setCustomNameVisible(false);
        parrot.setAdult();
        try {
            if (left) {
                player.setShoulderEntityLeft(parrot);
            } else {
                player.setShoulderEntityRight(parrot);
            }
            this.visualStates.put(player.getUniqueId(), VisualState.from(activePapuga, papugaData));
        } finally {
            spawned.remove();
        }
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.clearNearbyTaggedPapugi(player);
            if (retriesLeft > 0 && !this.hasMatchingPapuga(player, papugaData)) {
                this.placePapuga(player, activePapuga, papugaData, retriesLeft - 1);
            }
        }, 2L);
    }

    private Location getHiddenSpawnLocation(World world, Location playerLocation) {
        double y = Math.max(world.getMinHeight() + 1, playerLocation.getY() - 128.0);
        return new Location(world, playerLocation.getX(), y, playerLocation.getZ(), playerLocation.getYaw(), playerLocation.getPitch());
    }

    private void clearNearbyTaggedPapugi(Player player) {
        for (Entity entity : player.getNearbyEntities(12.0, 12.0, 12.0)) {
            if (!this.isVisualPapuga(entity)) {
                continue;
            }
            entity.remove();
        }
    }

    private boolean isVisualPapuga(Entity entity) {
        if (entity == null || entity.getType() != EntityType.PARROT) {
            return false;
        }
        if (entity.getScoreboardTags().contains(PAPUGA_ENTITY_TAG)) {
            return true;
        }
        PersistentDataContainer container = entity.getPersistentDataContainer();
        Byte marker = container.get(this.papugaMarkerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void markAsVisualPapuga(Parrot parrot) {
        parrot.addScoreboardTag(PAPUGA_ENTITY_TAG);
        parrot.getPersistentDataContainer().set(this.papugaMarkerKey, PersistentDataType.BYTE, (byte) 1);
    }

    private Player findOwningPlayer(Parrot parrot) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(parrot.getWorld())) {
                continue;
            }
            PapugiDatabase.PapugaRecord record = this.papugiHandler.getDatabase().getActivePapugaData(player.getUniqueId());
            if (!record.hasActivePapuga()) {
                continue;
            }
            PapugiHandler.PapugaData papugaData = this.papugiHandler.getPapuga(record.activePapuga());
            if (papugaData == null || papugaData.variant() != parrot.getVariant()) {
                continue;
            }
            if (player.getLocation().distanceSquared(parrot.getLocation()) <= 9.0) {
                return player;
            }
        }
        return null;
    }

    private record VisualState(String activePapuga, Parrot.Variant variant, boolean left) {
        private static VisualState from(String activePapuga, PapugiHandler.PapugaData papugaData) {
            return new VisualState(
                activePapuga,
                papugaData.variant(),
                papugaData.shoulder().equalsIgnoreCase("left")
            );
        }

        private boolean matches(String currentActivePapuga, PapugiHandler.PapugaData papugaData) {
            if (activePapuga != null && !activePapuga.equals(currentActivePapuga)) {
                return false;
            }
            return variant == papugaData.variant()
                && left == papugaData.shoulder().equalsIgnoreCase("left");
        }
    }
}
