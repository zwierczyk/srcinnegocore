package dev.grzybalson.modules.zywioly;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class ZywiolySignPrompt {

    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private boolean listenerRegistered;

    ZywiolySignPrompt(Plugin plugin) {
        this.plugin = plugin;
    }

    void register() {
        if (listenerRegistered || !ready()) {
            return;
        }
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.HIGH) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.UPDATE_SIGN) {
                    return;
                }
                Object playerObj = event.getPlayer();
                if (!(playerObj instanceof Player player)) {
                    return;
                }
                Session session = sessions.remove(player.getUniqueId());
                if (session == null) {
                    return;
                }
                event.setCancelled(true);
                WrapperPlayClientUpdateSign wrapper = new WrapperPlayClientUpdateSign(event);
                String[] lines = wrapper.getTextLines();
                String raw = lines != null && lines.length > 0 ? lines[0] : "";
                Bukkit.getScheduler().runTask(plugin, () -> {
                    restore(player, session.location);
                    if (session.taskId != -1) {
                        Bukkit.getScheduler().cancelTask(session.taskId);
                    }
                    session.callback.accept(raw == null ? "" : raw.trim());
                });
            }
        });
        listenerRegistered = true;
    }

    void open(Player player, String[] hintLines, Consumer<String> callback) {
        if (player == null || callback == null) {
            return;
        }
        register();
        Location location = findSignLocation(player);
        cancel(player.getUniqueId(), false);
        sessions.put(player.getUniqueId(), new Session(location, callback, -1));
        player.closeInventory();

        player.sendBlockChange(location, Material.OAK_SIGN.createBlockData());
        try {
            String[] lines = new String[] {
                hintLines.length > 0 ? hintLines[0] : "",
                hintLines.length > 1 ? hintLines[1] : "",
                hintLines.length > 2 ? hintLines[2] : "",
                hintLines.length > 3 ? hintLines[3] : ""
            };
            player.sendSignChange(location, lines);
        } catch (Throwable ignored) {
        }

        if (ready()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                player,
                new WrapperPlayServerOpenSignEditor(
                    new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                    true
                )
            );
        }

        Session current = sessions.get(player.getUniqueId());
        if (current != null) {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Session expired = sessions.remove(player.getUniqueId());
                if (expired != null) {
                    restore(player, expired.location);
                    expired.callback.accept("");
                }
            }, 20L * 30L).getTaskId();
            sessions.put(player.getUniqueId(), new Session(location, callback, taskId));
        }
    }

    void cancel(UUID uuid, boolean restoreBlock) {
        Session session = sessions.remove(uuid);
        if (session == null) {
            return;
        }
        if (session.taskId != -1) {
            Bukkit.getScheduler().cancelTask(session.taskId);
        }
        if (restoreBlock) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restore(player, session.location);
            }
        }
    }

    private void restore(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        Block block = location.getBlock();
        player.sendBlockChange(block.getLocation(), block.getBlockData());
    }

    private static Location findSignLocation(Player player) {
        Location loc = player.getLocation().clone();
        loc.setY(Math.max(loc.getWorld().getMinHeight() + 1, loc.getBlockY() - 2));
        return loc.getBlock().getLocation();
    }

    private static boolean ready() {
        try {
            return PacketEvents.getAPI() != null && PacketEvents.getAPI().isLoaded();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private record Session(Location location, Consumer<String> callback, int taskId) {
    }
}
