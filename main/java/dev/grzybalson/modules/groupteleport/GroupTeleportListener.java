package dev.grzybalson.modules.groupteleport;

import dev.grzybalson.managers.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GroupTeleportListener implements Listener {

    private final GroupTeleportManager manager;
    private final Set<UUID> pendingLogoutRestore = new HashSet<>();

    public GroupTeleportListener(GroupTeleportManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> {
            if (!pendingLogoutRestore.remove(player.getUniqueId())) {
                return;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) {
                    continue;
                }
                VanishManager vanish = manager.getPlugin().getVanishManager();
                if (vanish != null) {
                    vanish.showRespectingVanish(online, player);
                    vanish.showRespectingVanish(player, online);
                } else {
                    online.showPlayer(manager.getPlugin(), player);
                    player.showPlayer(manager.getPlugin(), online);
                }
            }
            manager.teleportToPvpSameCoords(player);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        GroupTeleportSession session = manager.getByPlayer(player);
        if (session == null) {
            return;
        }
        List<UUID> others = new ArrayList<>(session.getPlayers());
        others.remove(player.getUniqueId());
        if (others.isEmpty()) {
            return;
        }
        Player attacker = Bukkit.getPlayer(others.get(0));
        if (attacker != null) {
            manager.removeKill(attacker, player);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        manager.resendVictimMapActionBar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = manager.resolveAttacker(event.getDamager());
        if (manager.shouldCancelPvp(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvpProjectile(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter) || !manager.isPvpLocked(shooter)) {
            return;
        }
        if (event.getEntity() instanceof EnderPearl || event.getEntity() instanceof ThrownPotion) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvpSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) {
            return;
        }
        for (org.bukkit.entity.LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player other && manager.shouldCancelPvp(thrower, other)) {
                event.setIntensity(affected, 0.0D);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvpFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Player hooked && manager.shouldCancelPvp(event.getPlayer(), hooked)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElytraGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) {
            return;
        }
        if (!manager.isElytraBlocked(player)) {
            return;
        }
        event.setCancelled(true);
        manager.denyElytra(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElytraFirework(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!manager.isElytraBlocked(player)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREWORK_ROCKET) {
            return;
        }
        event.setCancelled(true);
        manager.denyElytra(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onElytraMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isGliding() || !manager.isElytraBlocked(player)) {
            return;
        }
        player.setGliding(false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String teleportWorld = manager.getConfig().getTeleportWorld();
        if (event.getFrom().getName().equalsIgnoreCase(teleportWorld)) {
            GroupTeleportSession session = manager.getByPlayer(player);
            if (session != null) {
                List<UUID> others = new ArrayList<>(session.getPlayers());
                others.remove(player.getUniqueId());
                if (!others.isEmpty()) {
                    Player attacker = Bukkit.getPlayer(others.get(0));
                    if (attacker != null) {
                        manager.removeKill(attacker, player);
                    }
                }
            }
            if (!manager.isInTeleportWorld(player)) {
                manager.clearElytraBlock(player);
            }
        }
        Bukkit.getScheduler().runTaskLater(manager.getPlugin(), () -> hideFromActiveSessions(player), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInTeleportWorld(player)) {
            return;
        }
        GroupTeleportSession session = manager.getByPlayer(player);
        if (session == null) {
            return;
        }
        List<UUID> others = new ArrayList<>(session.getPlayers());
        others.remove(player.getUniqueId());
        if (others.isEmpty()) {
            return;
        }
        Player attacker = Bukkit.getPlayer(others.get(0));
        if (attacker != null) {
            pendingLogoutRestore.add(player.getUniqueId());
            manager.removeKill(attacker, player);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTaskLater(manager.getPlugin(),
            () -> hideFromActiveSessions(event.getPlayer()), 5L);
    }

    private void hideFromActiveSessions(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (GroupTeleportSession session : manager.snapshotSessions()) {
            if (session.getPlayers().contains(player.getUniqueId())) {
                continue;
            }
            for (UUID memberId : session.getPlayers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member == null) {
                    continue;
                }
                member.hidePlayer(manager.getPlugin(), player);
                player.hidePlayer(manager.getPlugin(), member);
            }
        }
    }
}
