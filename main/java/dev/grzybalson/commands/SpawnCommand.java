package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MessagesConfig;
import dev.grzybalson.config.configs.SpawnConfig;
import dev.grzybalson.modules.teleport.TeleportAnimator;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SpawnCommand implements CommandExecutor {

    private final HollyCore plugin;

    public SpawnCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        MessagesConfig messages = plugin.getConfigManager().getMessagesConfig();
        TeleportAnimator animator = plugin.getTeleportAnimator();

        if (args.length >= 1) {
            if (!sender.hasPermission("Holly.cmd.spawn.other")) {
                if (sender instanceof Player player) {
                    player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.spawn.other"));
                } else {
                    sender.sendMessage(ColorUtils.colorizeComponent(messages.getNoPermission()));
                }
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(messages.getPlayerNotFound()));
                return true;
            }

            Location spawnLocation = resolveSpawnLocation(messages, sender);
            if (spawnLocation == null) {
                return true;
            }

            if (plugin.getGroupTeleportManager() != null && plugin.getGroupTeleportManager().isSpawnBlocked(target)) {
                plugin.getGroupTeleportManager().sendSpawnBlockedOtherMessage(sender, target);
                return true;
            }

            animator.completeInstant(target, spawnLocation, spawnOptions(messages));
            if (!sender.equals(target)) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    messages.getSpawnTeleportOther().replace("{player}", target.getName())
                ));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(messages.getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.spawn")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.spawn"));
            return true;
        }

        Location spawnLocation = resolveSpawnLocation(messages, player);
        if (spawnLocation == null) {
            return true;
        }

        if (plugin.getGroupTeleportManager() != null && plugin.getGroupTeleportManager().isSpawnBlocked(player)) {
            plugin.getGroupTeleportManager().sendSpawnBlockedMessage(player);
            return true;
        }

        if (plugin.getEventManager() != null && plugin.getEventManager().getDungeonSessions() != null
            && plugin.getEventManager().getDungeonSessions().isInActiveDungeon(player.getUniqueId())
            && !player.isOp() && !player.hasPermission("holly.adungeon.admin")) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "<gradient:#FF3B3B:#FF5C5C>/spawn zablokowany</gradient> &fpodczas dungeona!</shadow>"
            ));
            return true;
        }

        if (animator.isTeleporting(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent(messages.getSpawnTeleportCancelled()));
            return true;
        }

        SpawnConfig config = plugin.getConfigManager().getSpawnConfig();
        int delay = config.getTeleportDelay();
        TeleportAnimator.Options options = spawnOptions(messages);
        options.delaySeconds = delay;
        options.cancelOnMove = true;

        if (delay <= 0 || player.hasPermission("Holly.cmd.spawn.instant") || player.isOp()) {
            animator.completeInstant(player, spawnLocation, options);
            return true;
        }

        animator.start(player, spawnLocation, options);
        return true;
    }

    private TeleportAnimator.Options spawnOptions(MessagesConfig messages) {
        TeleportAnimator.Options options = new TeleportAnimator.Options();
        options.countdownSubtitle = messages.getSpawnTeleportingSubtitle();
        options.cancelledMessage = messages.getSpawnTeleportCancelled();
        options.successTitle = messages.getSpawnTeleportSuccessTitle();
        options.successSubtitle = messages.getSpawnTeleportSuccessSubtitle();
        options.successChat = messages.getSpawnTeleported();
        return options;
    }

    private Location resolveSpawnLocation(MessagesConfig messages, CommandSender sender) {
        SpawnConfig config = plugin.getConfigManager().getSpawnConfig();
        if (!config.isSpawnSet()) {
            sender.sendMessage(ColorUtils.colorizeComponent(messages.getSpawnNotSet()));
            return null;
        }

        World world = Bukkit.getWorld(config.getWorld());
        if (world == null) {
            sender.sendMessage(ColorUtils.error("Świat spawnu nie istnieje!"));
            return null;
        }

        return new Location(
            world,
            config.getX(),
            config.getY(),
            config.getZ(),
            config.getYaw(),
            config.getPitch()
        );
    }

    public void cancelTeleport(UUID uuid) {
        plugin.getTeleportAnimator().cancel(uuid, true);
    }
}
