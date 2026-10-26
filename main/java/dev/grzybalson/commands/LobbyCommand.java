package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.event.dungeon.DungeonSessionManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class LobbyCommand implements CommandExecutor {

    private final HollyCore plugin;

    public LobbyCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
            return true;
        }

        if (!plugin.getCombatManager().isInSafeRegionOrPvpDeny(player.getLocation())) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cKomendy /lobby możesz użyć tylko na strefie no-pvp."));
            return true;
        }

        // Dungeon: nigdy nie wynos EQ (szczegolnie po win, zanim endSession).
        try {
            var events = plugin.getEventManager();
            if (events != null && events.getDungeonSessions() != null) {
                DungeonSessionManager dungeon = events.getDungeonSessions();
                if (dungeon.isBoundToDungeon(player.getUniqueId())) {
                    dungeon.stripDungeonInventory(player);
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);
            out.writeUTF("Connect");
            out.writeUTF("lobby");
            player.sendPluginMessage(plugin, "BungeeCord", byteStream.toByteArray());
            player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Łączenie z serwerem lobby..."));
        } catch (IOException exception) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie udało się połączyć z lobby."));
        }
        return true;
    }
}
