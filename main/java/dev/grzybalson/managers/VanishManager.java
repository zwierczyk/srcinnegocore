package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VanishManager implements Listener {

    private static final String BAT_META = "holly_vanish_bat";
    private static final int BAT_COUNT = 10;
    private static final double BAT_RADIUS = 3.4;
    private static final long BAT_DURATION_TICKS = 60L; // 3s po wyłączeniu

    private final HollyCore plugin;
    private final Set<UUID> vanishedPlayers;
    private final Map<UUID, List<UUID>> revealBats = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> revealCleanupTasks = new ConcurrentHashMap<>();

    public VanishManager(HollyCore plugin) {
        this.plugin = plugin;
        this.vanishedPlayers = ConcurrentHashMap.newKeySet();
    }

    public void setVanished(Player player, boolean vanished) {
        if (vanished) {
            vanishedPlayers.add(player.getUniqueId());
            hideFromAll(player);
            strikeLightning(player);
            clearRevealBats(player.getUniqueId());
        } else {
            vanishedPlayers.remove(player.getUniqueId());
            showToAll(player);
            strikeLightning(player);
            playRevealBats(player);
        }
        if (plugin.getActionBarManager() != null) {
            plugin.getActionBarManager().refreshActionbar(player);
        }
    }

    public boolean isVanished(Player player) {
        return player != null && vanishedPlayers.contains(player.getUniqueId());
    }

    public boolean isVanished(UUID uuid) {
        return uuid != null && vanishedPlayers.contains(uuid);
    }

    public boolean toggleVanish(Player player) {
        boolean newState = !isVanished(player);
        setVanished(player, newState);
        return newState;
    }

    public boolean canSeeVanished(Player viewer) {
        return viewer != null && viewer.hasPermission("Holly.vanish.see");
    }

    public boolean shouldHideFrom(Player viewer, Player target) {
        if (viewer == null || target == null || viewer.equals(target)) {
            return false;
        }
        return isVanished(target) && !canSeeVanished(viewer);
    }

    public void showRespectingVanish(Player viewer, Player target) {
        if (viewer == null || target == null || viewer.equals(target)) {
            return;
        }
        if (shouldHideFrom(viewer, target)) {
            hideFromViewer(viewer, target);
            return;
        }
        viewer.showEntity(plugin, target);
        viewer.showPlayer(plugin, target);
    }

    public void shutdown() {
        for (UUID uuid : new ArrayList<>(revealBats.keySet())) {
            clearRevealBats(uuid);
        }
    }

    private void strikeLightning(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world != null) {
            world.strikeLightningEffect(center);
        }
    }

    /** Nietoperze w miejscu wyłączenia vanisha — widzi każdy, nie lecą za graczem. */
    private void playRevealBats(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Location center = player.getLocation().clone();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        clearRevealBats(playerId);

        List<UUID> spawned = new ArrayList<>(BAT_COUNT);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < BAT_COUNT; i++) {
            double angle = (Math.PI * 2.0D * i) / BAT_COUNT + random.nextDouble(-0.35D, 0.35D);
            double radius = BAT_RADIUS + random.nextDouble(-0.8D, 1.1D);
            Location spawnAt = center.clone().add(
                Math.cos(angle) * radius,
                0.6D + random.nextDouble(0.0D, 1.8D),
                Math.sin(angle) * radius
            );
            Entity entity = world.spawnEntity(spawnAt, EntityType.BAT);
            if (!(entity instanceof Bat bat)) {
                entity.remove();
                continue;
            }
            bat.setAwake(true);
            bat.setSilent(true);
            bat.setInvulnerable(true);
            bat.setCollidable(false);
            bat.setAI(true);
            bat.setRemoveWhenFarAway(true);
            bat.setPersistent(false);
            bat.setMetadata(BAT_META, new FixedMetadataValue(plugin, Boolean.TRUE));
            spawned.add(bat.getUniqueId());
        }
        if (spawned.isEmpty()) {
            return;
        }

        revealBats.put(playerId, spawned);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> clearRevealBats(playerId), BAT_DURATION_TICKS);
        revealCleanupTasks.put(playerId, task);
    }

    private void clearRevealBats(UUID playerId) {
        BukkitTask previous = revealCleanupTasks.remove(playerId);
        if (previous != null) {
            previous.cancel();
        }
        List<UUID> bats = revealBats.remove(playerId);
        if (bats == null) {
            return;
        }
        for (UUID batId : bats) {
            Entity entity = Bukkit.getEntity(batId);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
    }

    private boolean isVanishBat(Entity entity) {
        return entity != null && entity.hasMetadata(BAT_META);
    }

    private void hideFromAll(Player vanishedPlayer) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(vanishedPlayer)) {
                continue;
            }
            if (canSeeVanished(online)) {
                online.showEntity(plugin, vanishedPlayer);
                online.showPlayer(plugin, vanishedPlayer);
            } else {
                hideFromViewer(online, vanishedPlayer);
            }
        }
    }

    private void showToAll(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            if (shouldHideFrom(online, player)) {
                hideFromViewer(online, player);
                continue;
            }
            online.showEntity(plugin, player);
            online.showPlayer(plugin, player);
        }
    }

    private void hideFromViewer(Player viewer, Player target) {
        viewer.hideEntity(plugin, target);
        viewer.hidePlayer(plugin, target);
    }

    public void handlePlayerJoin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // Zwykły join: wystarczy ukryć aktualnie vanishniętych przed joinerem (O(vanished)).
        // Pełne hideFromAll tylko gdy wchodzi osoba w vanishu.
        if (isVanished(player)) {
            reapply(player);
            return;
        }
        for (UUID vanishedUuid : vanishedPlayers) {
            if (vanishedUuid.equals(player.getUniqueId())) {
                continue;
            }
            Player vanished = Bukkit.getPlayer(vanishedUuid);
            if (vanished == null || !vanished.isOnline()) {
                continue;
            }
            if (canSeeVanished(player)) {
                player.showEntity(plugin, vanished);
                player.showPlayer(plugin, vanished);
            } else {
                hideFromViewer(player, vanished);
            }
        }
    }

    public void handlePlayerQuit(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        clearRevealBats(player.getUniqueId());
    }

    public void handleWorldChange(Player player) {
        reapply(player);
        Bukkit.getScheduler().runTask(plugin, () -> reapply(player));
        Bukkit.getScheduler().runTaskLater(plugin, () -> reapply(player), 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> reapply(player), 20L);
    }

    private void reapply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isVanished(player)) {
            hideFromAll(player);
            if (plugin.getActionBarManager() != null) {
                plugin.getActionBarManager().refreshActionbar(player);
            }
        }
        for (UUID vanishedUuid : vanishedPlayers) {
            if (vanishedUuid.equals(player.getUniqueId())) {
                continue;
            }
            Player vanished = Bukkit.getPlayer(vanishedUuid);
            if (vanished == null || !vanished.isOnline()) {
                continue;
            }
            if (canSeeVanished(player)) {
                player.showEntity(plugin, vanished);
                player.showPlayer(plugin, vanished);
            } else {
                hideFromViewer(player, vanished);
            }
        }
    }

    public Set<UUID> getVanishedPlayers() {
        return vanishedPlayers;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        handleWorldChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getFrom().getWorld() == event.getTo().getWorld()) {
            return;
        }
        handleWorldChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (isVanishBat(event.getEntity()) || isVanishBat(event.getDamager())) {
            event.setCancelled(true);
            return;
        }
        Player victim = event.getEntity() instanceof Player player ? player : null;
        Player attacker = resolvePlayer(event.getDamager());
        if ((victim != null && isVanished(victim)) || (attacker != null && isVanished(attacker))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBatDamage(EntityDamageEvent event) {
        if (isVanishBat(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isVanishBat(event.getRightClicked())) {
            event.setCancelled(true);
            return;
        }
        if (event.getRightClicked() instanceof Player other && (isVanished(event.getPlayer()) || isVanished(other))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (isVanished(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
