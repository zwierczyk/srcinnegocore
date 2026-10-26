package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MainConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.Locale;

public class NoEnvironmentalDamageListener implements Listener {

    private static final EnumSet<EntityDamageEvent.DamageCause> BUILDING_CAUSES = EnumSet.of(
        EntityDamageEvent.DamageCause.SUFFOCATION,
        EntityDamageEvent.DamageCause.CRAMMING
    );

    private final HollyCore plugin;
    private BukkitTask airTask;

    public NoEnvironmentalDamageListener(HollyCore plugin) {
        this.plugin = plugin;
        startAirTask();
    }

    public void stop() {
        if (airTask != null) {
            airTask.cancel();
            airTask = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        if (!isEnabled()) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (!shouldBlockCause(cause)) {
            return;
        }

        event.setCancelled(true);
        event.setDamage(0.0D);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGravityPhysics(BlockPhysicsEvent event) {
        if (!isNoFallingBlocks()) {
            return;
        }
        // Tylko sand/żwir/kowadło — dripstone i inne zawsze lecą.
        if (!isBlockedFallingMaterial(event.getBlock().getType())
            && !isBlockedFallingMaterial(event.getSourceBlock().getType())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingBlockSpawn(EntitySpawnEvent event) {
        if (!isNoFallingBlocks() || !(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        if (!isBlockedFallingMaterial(falling.getBlockData().getMaterial())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingBlockChange(EntityChangeBlockEvent event) {
        if (!isNoFallingBlocks() || !(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        if (!isBlockedFallingMaterial(falling.getBlockData().getMaterial())
            && !isBlockedFallingMaterial(event.getTo())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isEnabled()) {
            refillAir(event.getPlayer());
        }
    }

    private void startAirTask() {
        airTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isEnabled()) {
                return;
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (player.getRemainingAir() >= player.getMaximumAir()
                    && !player.isInWater()
                    && !player.isInBubbleColumn()) {
                    continue;
                }
                if (shouldRefillAir(player)) {
                    refillAir(player);
                }
            }
        }, 40L, 40L);
    }

    private boolean shouldRefillAir(Player player) {
        // Nie uzupełniaj w wodzie — topienie ma działać.
        if (player.isInWater() || player.isInBubbleColumn()) {
            return false;
        }

        Block eyeBlock = player.getEyeLocation().getBlock();
        if (!eyeBlock.isPassable()) {
            return true;
        }

        Block headBlock = player.getLocation().getBlock().getRelative(0, 1, 0);
        return !headBlock.isPassable();
    }

    private void refillAir(Player player) {
        int maxAir = player.getMaximumAir();
        if (player.getRemainingAir() < maxAir) {
            player.setRemainingAir(maxAir);
        }
    }

    private boolean isEnabled() {
        MainConfig.NoDamageSettings settings = plugin.getConfigManager().getMainConfig().getNoDamage();
        return settings != null && settings.isEnabled();
    }

    private boolean isNoFallingBlocks() {
        MainConfig.NoDamageSettings settings = plugin.getConfigManager().getMainConfig().getNoDamage();
        return settings != null && settings.isEnabled() && settings.isNoFallingBlocks();
    }

    private static boolean isBlockedFallingMaterial(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case SAND, RED_SAND, GRAVEL, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> true;
            default -> {
                String name = material.name();
                yield name.endsWith("_CONCRETE_POWDER")
                    || name.equals("SUSPICIOUS_SAND")
                    || name.equals("SUSPICIOUS_GRAVEL");
            }
        };
    }

    private boolean shouldBlockCause(EntityDamageEvent.DamageCause cause) {
        if (BUILDING_CAUSES.contains(cause)) {
            return true;
        }

        MainConfig.NoDamageSettings settings = plugin.getConfigManager().getMainConfig().getNoDamage();
        if (settings == null) {
            return false;
        }

        for (String rawCause : settings.getCauses()) {
            if (rawCause == null || rawCause.isBlank()) {
                continue;
            }
            try {
                if (EntityDamageEvent.DamageCause.valueOf(rawCause.trim().toUpperCase(Locale.ROOT)) == cause) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }
}
