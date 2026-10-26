package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.SpawnConfig;
import dev.grzybalson.config.modules.warps.WarpDataConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PortalListener implements Listener {

    private static final long MIN_PORTAL_COOLDOWN_MS = 2000L;
    private static final String PVP_WARP_NAME = "pvp";

    private final HollyCore plugin;
    private final Set<UUID> recentlyTeleported = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> teleportTime = new ConcurrentHashMap<>();
    private volatile String cachedPvpWorld = null;

    public PortalListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.error("Tworzenie portali do Netheru jest zablokowane na tym serwerze!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();

        if (event.getCause() == PlayerPortalEvent.TeleportCause.NETHER_PORTAL) {
            event.setCancelled(true);
            handleNetherPortal(player);
            return;
        }

        if (event.getCause() == PlayerPortalEvent.TeleportCause.END_PORTAL) {
            event.setCancelled(true);
            handleEndPortal(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        Location from = event.getFrom();
        // Ignoruj czyste obroty głową/kamerą
        if (from.getX() == to.getX() &&
            from.getY() == to.getY() &&
            from.getZ() == to.getZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Obsługa cooldownu teleportacji
        if (recentlyTeleported.contains(uuid)) {
            Long lastTime = teleportTime.get(uuid);
            long now = System.currentTimeMillis();
            if (lastTime != null && (now - lastTime) < MIN_PORTAL_COOLDOWN_MS) {
                return;
            }
            recentlyTeleported.remove(uuid);
            teleportTime.remove(uuid);
        }

        Material portalType = detectPortal(player, from, to);
        if (portalType == null) {
            return;
        }

        if (portalType == Material.NETHER_PORTAL) {
            handleNetherPortal(player);
        } else if (portalType == Material.END_PORTAL) {
            handleEndPortal(player);
        }
    }

    /**
     * Precyzyjne wykrywanie portalu:
     * 1. Sprawdza stopy oraz poziom oczu
     * 2. Sprawdza cały bounding box gracza w punkcie docelowym (kluczowe przy fly/elytrze,
     *    gdy gracz leci poziomo lub jego stopy są poniżej/powyżej klocka portalu)
     * 3. Obsługuje szybki przelot na elyttrze (próbkowanie trasy między FROM a TO,
     *    żeby gracz nie przeskoczył jednoklockowego portalu w 1 ticku)
     */
    private Material detectPortal(Player player, Location from, Location to) {
        World world = to.getWorld();
        if (world == null) return null;

        // 1. Szybkie sprawdzenie pod nogami i na poziomie oczu
        Material feet = to.getBlock().getType();
        if (isPortal(feet)) return feet;

        Location eyeLoc = to.clone().add(0, player.getEyeHeight(), 0);
        Material eye = eyeLoc.getBlock().getType();
        if (isPortal(eye)) return eye;

        // 2. BoundingBox gracza na pozycji docelowej
        double widthRadius = 0.3;
        double height = player.isGliding() ? 0.6 : Math.max(0.6, player.getHeight());

        int minX = (int) Math.floor(to.getX() - widthRadius);
        int maxX = (int) Math.floor(to.getX() + widthRadius);
        int minY = (int) Math.floor(to.getY());
        int maxY = (int) Math.floor(to.getY() + height);
        int minZ = (int) Math.floor(to.getZ() - widthRadius);
        int maxZ = (int) Math.floor(to.getZ() + widthRadius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (isPortal(type)) {
                        return type;
                    }
                }
            }
        }

        // 3. Sprawdzenie szybkiego przelotu (elytra/fly) - próbkowanie odcinka FROM -> TO
        if (from.getWorld() != null && from.getWorld().equals(world)) {
            double distSq = from.distanceSquared(to);
            if (distSq > 0.25) { // przemieszczenie powyżej 0.5 bloku w ticku
                double dist = Math.sqrt(distSq);
                if (dist <= 20.0) { // zabezpieczenie przed dalekimi teleportami
                    int steps = (int) Math.ceil(dist / 0.4);
                    double stepX = (to.getX() - from.getX()) / steps;
                    double stepY = (to.getY() - from.getY()) / steps;
                    double stepZ = (to.getZ() - from.getZ()) / steps;

                    for (int i = 1; i < steps; i++) {
                        double sx = from.getX() + stepX * i;
                        double sy = from.getY() + stepY * i;
                        double sz = from.getZ() + stepZ * i;

                        Material stepFeet = world.getBlockAt((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz)).getType();
                        if (isPortal(stepFeet)) return stepFeet;

                        Material stepTorso = world.getBlockAt((int) Math.floor(sx), (int) Math.floor(sy + 0.4), (int) Math.floor(sz)).getType();
                        if (isPortal(stepTorso)) return stepTorso;

                        if (height > 0.8) {
                            Material stepHead = world.getBlockAt((int) Math.floor(sx), (int) Math.floor(sy + height), (int) Math.floor(sz)).getType();
                            if (isPortal(stepHead)) return stepHead;
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean isPortal(Material material) {
        return material == Material.NETHER_PORTAL || material == Material.END_PORTAL;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        recentlyTeleported.remove(uuid);
        teleportTime.remove(uuid);
    }

    private void handleNetherPortal(Player player) {
        UUID uuid = player.getUniqueId();
        if (!recentlyTeleported.add(uuid)) return;
        teleportTime.put(uuid, System.currentTimeMillis());

        // Bezpieczne zatrzymanie pędu gracza (żeby po teleportacji nie zginął od uderzenia w ścianę)
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0.0F);
        if (player.isGliding()) {
            player.setGliding(false);
        }

        if (isInPvpWorld(player)) {
            teleportToSpawn(player);
        } else {
            teleportToPvp(player);
        }

        // Wyzerowanie prędkości po przejściu na nowy świat
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setVelocity(new Vector(0, 0, 0));
                player.setFallDistance(0.0F);
            }
        });
    }

    private void handleEndPortal(Player player) {
        UUID uuid = player.getUniqueId();
        if (!recentlyTeleported.add(uuid)) return;
        teleportTime.put(uuid, System.currentTimeMillis());

        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0.0F);
        if (player.isGliding()) {
            player.setGliding(false);
        }

        if (plugin.getWarpManager() != null) {
            plugin.getWarpManager().teleportToWarpSilent(player, "end");
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.setVelocity(new Vector(0, 0, 0));
                player.setFallDistance(0.0F);
            }
        });
    }

    private boolean isInPvpWorld(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }

        String currentWorld = player.getWorld().getName();
        if (currentWorld.equalsIgnoreCase("pvp") || currentWorld.toLowerCase(java.util.Locale.ROOT).contains("pvp")) {
            return true;
        }

        if (cachedPvpWorld == null && plugin.getWarpManager() != null) {
            WarpDataConfig.WarpEntry pvpWarp = plugin.getWarpManager().getWarp(PVP_WARP_NAME);
            if (pvpWarp != null && pvpWarp.getWorld() != null) {
                cachedPvpWorld = pvpWarp.getWorld();
            }
        }

        if (cachedPvpWorld != null && currentWorld.equalsIgnoreCase(cachedPvpWorld)) {
            return true;
        }

        return false;
    }

    private void teleportToPvp(Player player) {
        if (plugin.getWarpManager() != null && plugin.getWarpManager().getWarp(PVP_WARP_NAME) != null) {
            plugin.getWarpManager().teleportToWarpSilent(player, PVP_WARP_NAME);
            return;
        }

        World pvpWorld = Bukkit.getWorld("pvp");
        if (pvpWorld == null) {
            pvpWorld = Bukkit.getWorld("world_pvp");
        }
        if (pvpWorld != null) {
            player.teleport(pvpWorld.getSpawnLocation());
        }
    }

    private void teleportToSpawn(Player player) {
        SpawnConfig config = plugin.getConfigManager() != null ? plugin.getConfigManager().getSpawnConfig() : null;
        if (config != null && config.isSpawnSet()) {
            World world = Bukkit.getWorld(config.getWorld());
            if (world != null) {
                Location spawnLocation = new Location(
                    world,
                    config.getX(),
                    config.getY(),
                    config.getZ(),
                    config.getYaw(),
                    config.getPitch()
                );
                player.teleport(spawnLocation);
                return;
            }
        }

        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld != null) {
            player.teleport(mainWorld.getSpawnLocation());
        } else if (!Bukkit.getWorlds().isEmpty()) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }
}
