package dev.grzybalson.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Paper {@code enable-player-collisions: false} jest nadpisywane, gdy jakikolwiek
 * team (nametag, TAB, inny plugin) wyśle {@code collisionRule=always}.
 * Tu gasimy kolizję po stronie encji i pakietów TEAMS — bez agresywnego
 * skanowania wszystkich Bukkit teamów (psujeło to sortowanie TAB).
 */
public final class PlayerCollisionGuard implements Listener {

    private static final long PERIODIC_REFRESH_TICKS = 40L;

    private final HollyCore plugin;
    private boolean packetListenerRegistered;
    private BukkitTask refreshTask;

    public PlayerCollisionGuard(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void install() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        tryRegisterTeamRewrite();
        Bukkit.getScheduler().runTaskLater(plugin, this::tryRegisterTeamRewrite, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, this::tryRegisterTeamRewrite, 40L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshCollidable, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::refreshCollidable, 60L);
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            plugin, this::refreshCollidable, PERIODIC_REFRESH_TICKS, PERIODIC_REFRESH_TICKS
        );
    }

    public void apply(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setCollidable(false);
    }

    private void refreshCollidable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isCollidable()) {
                player.setCollidable(false);
            }
        }
    }

    private void tryRegisterTeamRewrite() {
        if (packetListenerRegistered) {
            return;
        }
        try {
            if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) {
                return;
            }
            PacketEvents.getAPI().getEventManager().registerListener(
                new PacketListenerAbstract(PacketListenerPriority.HIGH) {
                    @Override
                    public void onPacketSend(PacketSendEvent event) {
                        if (event.getPacketType() != PacketType.Play.Server.TEAMS) {
                            return;
                        }
                        try {
                            rewriteTeamCollision(event);
                        } catch (RuntimeException | LinkageError ignored) {
                        }
                    }
                }
            );
            packetListenerRegistered = true;
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static void rewriteTeamCollision(PacketSendEvent event) {
        WrapperPlayServerTeams wrapper = new WrapperPlayServerTeams(event);
        WrapperPlayServerTeams.TeamMode mode = wrapper.getTeamMode();
        if (mode != WrapperPlayServerTeams.TeamMode.CREATE
            && mode != WrapperPlayServerTeams.TeamMode.UPDATE) {
            return;
        }
        WrapperPlayServerTeams.ScoreBoardTeamInfo info = wrapper.getTeamInfo().orElse(null);
        if (info == null) {
            return;
        }
        if (info.getCollisionRule() == WrapperPlayServerTeams.CollisionRule.NEVER) {
            return;
        }
        info.setCollisionRule(WrapperPlayServerTeams.CollisionRule.NEVER);
        wrapper.setTeamInfo(info);
        event.markForReEncode(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        apply(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(player), 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(player), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> apply(event.getPlayer()));
    }
}
