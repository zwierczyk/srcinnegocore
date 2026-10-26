package dev.grzybalson.modules.home;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.home.HomeConfig;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {

    private final HollyCore plugin;
    private final HomeDataManager dataManager;
    private final Map<UUID, BukkitTask> pendingTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, Location> teleportStartLocations = new ConcurrentHashMap<>();

    public HomeManager(HollyCore plugin) {
        this.plugin = plugin;
        dev.grzybalson.internal.HollyIntegrityGuard.requireBound(plugin, "location:resolve");
        this.dataManager = new HomeDataManager(plugin);
    }

    public HomeDataManager getDataManager() {
        return dataManager;
    }

    public HomeConfig getConfig() {
        return plugin.getModuleConfigManager().getHomeConfig();
    }

    public PlayerHomesData getPlayerHomes(Player player) {
        return dataManager.getCachedHomes(player.getUniqueId());
    }

    public boolean canAccessSlot(Player player, int homeNumber) {
        HomeConfig.HomeSlotSection slotSection = getConfig().getHomeSlots().get("slot" + homeNumber);
        if (slotSection == null) return false;
        String perm = slotSection.getPermission();
        return perm == null || perm.isBlank() || player.hasPermission(perm);
    }

    public boolean setHome(Player player, int slotNumber, String name) {
        if (!canAccessSlot(player, slotNumber)) {
            player.sendMessage(ColorUtils.error("Nie posiadasz wystarczającej rangi do tego domku!"));
            playSound(player, getConfig().getSounds().getNoPermission());
            return false;
        }

        Location loc = player.getLocation();
        HomeConfig.SecuritySection sec = getConfig().getSecurity();

        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "";
        if (dev.grzybalson.modules.event.dungeon.DungeonWorldService.isDungeonWorld(worldName)
            || "ninjaevent".equalsIgnoreCase(worldName)
            || getConfig().getHomes().getBlockedWorlds().stream().anyMatch(w -> w != null && (
                w.equalsIgnoreCase(worldName)
                    || (w.endsWith("_") && worldName.toLowerCase(java.util.Locale.ROOT).startsWith(w.toLowerCase(java.util.Locale.ROOT)))
            ))) {
            player.sendMessage(ColorUtils.error("Nie możesz ustawić domu w tym świecie!"));
            playSound(player, getConfig().getSounds().getError());
            return false;
        }

        if (loc.getY() < sec.getMinHeight() || loc.getY() > sec.getMaxHeight()) {
            player.sendMessage(ColorUtils.error("Nie możesz zapisać domku na tej wysokości!"));
            playSound(player, getConfig().getSounds().getError());
            return false;
        }

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block below = loc.clone().subtract(0, 1, 0).getBlock();

        if (sec.isBlockLavaHomes() && (feet.getType() == Material.LAVA || head.getType() == Material.LAVA)) {
            player.sendMessage(ColorUtils.error("Nie możesz zapisać domku w lawie!"));
            playSound(player, getConfig().getSounds().getError());
            return false;
        }

        if (sec.isBlockFireHomes() && (feet.getType() == Material.FIRE || feet.getType() == Material.SOUL_FIRE)) {
            player.sendMessage(ColorUtils.error("Nie możesz zapisać domku w ogniu!"));
            playSound(player, getConfig().getSounds().getError());
            return false;
        }

        if (sec.isBlockAirHomes() && (below.getType() == Material.AIR || below.getType() == Material.CAVE_AIR || below.getType() == Material.VOID_AIR)) {
            player.sendMessage(ColorUtils.error("Nie możesz zapisać domku w powietrzu!"));
            playSound(player, getConfig().getSounds().getError());
            return false;
        }

        HomeConfig.HomeSlotSection slotConfig = getConfig().getHomeSlots().get("slot" + slotNumber);
        String defaultName = slotConfig != null ? slotConfig.getDefaultName() : "Domek #" + slotNumber;
        String defaultIcon = slotConfig != null ? slotConfig.getDefaultIcon() : "WHITE_BED";
        String homeName = (name != null && !name.isBlank()) ? name : defaultName;

        Home home = new Home(
            slotNumber,
            homeName,
            loc.getWorld().getName(),
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            loc.getYaw(),
            loc.getPitch(),
            defaultIcon,
            getConfig().getHomes().isDefaultShowCoordinates(),
            System.currentTimeMillis()
        );

        PlayerHomesData data = getPlayerHomes(player);
        data.setHome(home);
        dataManager.savePlayerHomes(player.getUniqueId());

        player.sendMessage(ColorUtils.success("Pomyślnie zapisano pozycję domku #" + slotNumber + "!"));
        playSound(player, getConfig().getSounds().getSetHome());
        spawnParticles(player, loc, getConfig().getParticles().getSetHome());
        return true;
    }

    public boolean deleteHome(Player player, int slotNumber) {
        PlayerHomesData data = getPlayerHomes(player);
        Home removed = data.removeHome(slotNumber);
        if (removed == null) {
            player.sendMessage(ColorUtils.error("Ten domek nie jest zapisany!"));
            playSound(player, getConfig().getSounds().getError());
            return false;
        }

        dataManager.savePlayerHomes(player.getUniqueId());
        player.sendMessage(ColorUtils.success("Pomyślnie usunięto domek #" + slotNumber + "!"));
        playSound(player, getConfig().getSounds().getDeleteHome());
        spawnParticles(player, player.getLocation(), getConfig().getParticles().getDeleteHome());
        return true;
    }

    public void updateHomeIcon(Player player, int slotNumber, String iconMaterial) {
        PlayerHomesData data = getPlayerHomes(player);
        Home home = data.getHome(slotNumber);
        if (home != null) {
            home.setIconMaterial(iconMaterial);
            dataManager.savePlayerHomes(player.getUniqueId());
            playSound(player, getConfig().getSounds().getChangeIcon());
        }
    }

    public void toggleCoordinates(Player player, int slotNumber) {
        PlayerHomesData data = getPlayerHomes(player);
        Home home = data.getHome(slotNumber);
        if (home != null) {
            home.setShowCoordinates(!home.isShowCoordinates());
            dataManager.savePlayerHomes(player.getUniqueId());
            playSound(player, getConfig().getSounds().getGuiClick());
        }
    }

    public void teleportToHome(Player player, int slotNumber) {
        PlayerHomesData data = getPlayerHomes(player);
        Home home = data.getHome(slotNumber);
        if (home == null) {
            player.sendMessage(ColorUtils.error("Ten domek nie został jeszcze zapisany!"));
            playSound(player, getConfig().getSounds().getError());
            return;
        }

        Location destination = home.getLocation();
        if (destination == null) {
            player.sendMessage(ColorUtils.error("Świat domku nie jest dostępny!"));
            playSound(player, getConfig().getSounds().getError());
            return;
        }

        int countdown = getConfig().getTeleport().getCountdown();
        if (countdown <= 0 || player.hasPermission("holly.home.bypass.countdown")) {
            performTeleport(player, destination, home.getName());
            return;
        }

        cancelPendingTeleport(player);
        teleportStartLocations.put(player.getUniqueId(), player.getLocation().clone());

        player.sendMessage(ColorUtils.info("Teleportacja nastąpi za " + countdown + " sekund... Nie ruszaj się!"));
        playSound(player, getConfig().getSounds().getTeleportStart());

        BukkitTask task = new BukkitRunnable() {
            int remaining = countdown;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    cleanup(player);
                    return;
                }

                if (getConfig().getTeleport().isCancelOnMove() && hasMoved(player)) {
                    player.sendMessage(ColorUtils.error("Teleportacja anulowana z powodu ruchu!"));
                    playSound(player, getConfig().getSounds().getTeleportCancel());
                    cancel();
                    cleanup(player);
                    return;
                }

                if (remaining > 0) {
                    playSound(player, getConfig().getTeleport().getCountdownSound());
                    if (getConfig().getParticles().isEnabled()) {
                        spawnParticles(player, player.getLocation(), getConfig().getParticles().getTeleportCountdown());
                    }
                    remaining--;
                } else {
                    cancel();
                    cleanup(player);
                    performTeleport(player, destination, home.getName());
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        pendingTeleports.put(player.getUniqueId(), task);
    }

    private void performTeleport(Player player, Location destination, String homeName) {
        player.teleport(destination);
        player.sendMessage(ColorUtils.success("Przeteleportowano do domku &e" + homeName + "&a!"));
        playSound(player, getConfig().getSounds().getTeleportSuccess());
        if (getConfig().getTeleport().isShowParticles()) {
            spawnParticles(player, destination, getConfig().getTeleport().getTeleportParticles());
        }
    }

    public void cancelPendingTeleport(Player player) {
        BukkitTask task = pendingTeleports.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        teleportStartLocations.remove(player.getUniqueId());
    }

    public void handleDamage(Player player) {
        if (!getConfig().getTeleport().isCancelOnDamage()) return;
        if (pendingTeleports.containsKey(player.getUniqueId())) {
            cancelPendingTeleport(player);
            player.sendMessage(ColorUtils.error("Teleportacja anulowana z powodu otrzymanych obrażeń!"));
            playSound(player, getConfig().getSounds().getTeleportCancel());
        }
    }

    private boolean hasMoved(Player player) {
        Location start = teleportStartLocations.get(player.getUniqueId());
        if (start == null) return false;
        Location current = player.getLocation();
        if (start.getWorld() != current.getWorld()) return true;
        return start.distance(current) > getConfig().getTeleport().getMaxMoveDistance();
    }

    private void cleanup(Player player) {
        pendingTeleports.remove(player.getUniqueId());
        teleportStartLocations.remove(player.getUniqueId());
    }

    public void playSound(Player player, HomeConfig.SoundConfigItem soundConfig) {
        if (player == null || soundConfig == null || !soundConfig.isEnabled()) return;
        try {
            Sound sound = Sound.valueOf(soundConfig.getSound().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, soundConfig.getVolume(), soundConfig.getPitch());
        } catch (IllegalArgumentException ignored) {}
    }

    public void spawnParticles(Player player, Location location, HomeConfig.ParticleConfigItem particleConfig) {
        if (player == null || location == null || particleConfig == null || !particleConfig.isEnabled()) return;
        try {
            Particle particle = Particle.valueOf(particleConfig.getType().toUpperCase(Locale.ROOT));
            location.getWorld().spawnParticle(particle, location, particleConfig.getAmount(), particleConfig.getOffsetX(), particleConfig.getOffsetY(), particleConfig.getOffsetZ(), particleConfig.getSpeed());
        } catch (IllegalArgumentException ignored) {}
    }

    public void shutdown() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        teleportStartLocations.clear();
        dataManager.saveAll();
    }
}
