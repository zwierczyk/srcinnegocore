package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ChatAdFilter;
import dev.grzybalson.utils.ChatInputSanitizer;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.SpooferBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ReplyCommand implements CommandExecutor {

    private final HollyCore plugin;

    public ReplyCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.reply")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.reply"));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/r <wiadomość>");
            return true;
        }

        String message = joinArgs(args);
        if (ChatInputSanitizer.rejectIfBlocked(player, message)) {
            return true;
        }
        if (ChatAdFilter.rejectIfBlocked(plugin, player, message)) {
            return true;
        }

        String fakeTarget = MsgCommand.getLastFakeMessaged(player.getUniqueId());
        if (fakeTarget != null && SpooferBridge.isFakeBot(fakeTarget)) {
            MsgCommand.deliverMessage(plugin, player, fakeTarget, message, null);
            MsgCommand.setLastFakeMessaged(player.getUniqueId(), fakeTarget);
            return true;
        }

        UUID lastUUID = MsgCommand.getLastMessaged(player.getUniqueId());
        if (lastUUID == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getNoReply()
            ));
            return true;
        }

        Player target = Bukkit.getPlayer(lastUUID);
        if (target == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return true;
        }

        if (plugin.getUserSettingsManager() != null && !plugin.getUserSettingsManager().isPrivateMessagesEnabled(target.getUniqueId())) {
            player.sendMessage(ColorUtils.error("Ten gracz ma wyłączone wiadomości prywatne."));
            return true;
        }

        if (plugin.getIgnoreManager() != null
            && plugin.getIgnoreManager().isIgnored(target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getIgnoreConfig().getPlayerIgnored()
            ));
            return true;
        }

        MsgCommand.deliverMessage(plugin, player, target.getName(), message, target);
        MsgCommand.setLastMessaged(player.getUniqueId(), target.getUniqueId());

        return true;
    }

    private static String joinArgs(String[] args) {
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            messageBuilder.append(args[i]);
            if (i < args.length - 1) {
                messageBuilder.append(" ");
            }
        }
        return messageBuilder.toString();
    }
}
