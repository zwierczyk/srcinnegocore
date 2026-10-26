package dev.grzybalson.modules.anticheat;

import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;

public final class FireworkCheck implements Listener {

    private final AnticheatManager manager;

    public FireworkCheck(AnticheatManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!manager.getConfig().isElytraFirework()) {
            return;
        }
        if (!(event.getEntity() instanceof Firework) || !(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (!player.isGliding() || player.getInventory().getItemInMainHand().getAmount() <= 2) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> {
            if (!player.isOnline() || player.getInventory().getItemInMainHand().getType() == Material.FIREWORK_ROCKET) {
                return;
            }
            CheatPlayerData data = manager.data(player);
            int vl = data.addViolation(CheatType.ELYTRA_TARGET);
            data.setBlockHitUntil(System.currentTimeMillis());
            if (vl >= 3) {
                manager.swapHands(player);
                manager.scrambleLook(player, 10);
                manager.randomLook(player);
            }
            if (vl >= 10) {
                manager.notifyStaff(player, CheatType.ELYTRA_TARGET, vl,
                    manager.getConfig().getPrefix() + " &7Gracz &f{player} &7elytra-target &8(&cx{vl}&8)", Map.of());
            }
            String ban = manager.getConfig().getElytraBanCommand();
            if (vl >= 20 && ban != null && !ban.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ban.replace("{player}", player.getName()));
            }
        }, 1L);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (!manager.getConfig().isElytraFirework()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isGliding() && player.getInventory().getItemInOffHand().getType() == Material.FIREWORK_ROCKET) {
            event.setCancelled(true);
            SoundsUtil.error(player);
            player.sendMessage(ColorUtils.colorizeComponent(manager.getConfig().getFireworkOffhand()));
        }
    }
}