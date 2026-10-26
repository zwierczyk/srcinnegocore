package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ChatAdFilter;
import dev.grzybalson.utils.ChatInputSanitizer;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.SpooferBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MsgCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private static final Map<UUID, UUID> lastMessaged = new HashMap<>();
    private static final Map<UUID, String> lastFakeMessaged = new HashMap<>();

    public MsgCommand(HollyCore plugin) {
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

        if (!player.hasPermission("Holly.cmd.msg")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.msg"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ColorUtils.usage("/msg <gracz> <wiadomosc>"));
            return true;
        }

        String message = joinArgs(args, 1);
        if (ChatInputSanitizer.rejectIfBlocked(player, message)) {
            return true;
        }
        if (ChatAdFilter.rejectIfBlocked(plugin, player, message)) {
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target != null) {
            if (target.equals(player)) {
                player.sendMessage(ColorUtils.error("Nie mozesz wyslac wiadomosci do siebie!"));
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

            deliverMessage(plugin, player, target.getName(), message, target);
            lastMessaged.put(player.getUniqueId(), target.getUniqueId());
            lastMessaged.put(target.getUniqueId(), player.getUniqueId());
            lastFakeMessaged.remove(player.getUniqueId());
            return true;
        }

        String fakeTarget = SpooferBridge.resolveFakeName(args[0]);
        if (fakeTarget != null) {
            if (fakeTarget.equalsIgnoreCase(player.getName())) {
                player.sendMessage(ColorUtils.error("Nie mozesz wyslac wiadomosci do siebie!"));
                return true;
            }

            deliverMessage(plugin, player, fakeTarget, message, null);
            lastFakeMessaged.put(player.getUniqueId(), fakeTarget);
            lastMessaged.remove(player.getUniqueId());
            return true;
        }

        player.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
        ));
        return true;
    }

    static void deliverMessage(HollyCore plugin, Player sender, String targetName, String message, @Nullable Player onlineTarget) {
        String format = plugin.getConfigManager().getMessagesConfig().getMsgFormat()
            .replace("{sender}", sender.getName())
            .replace("{receiver}", targetName);

        Component formattedMessage = ColorUtils.colorizeWithPlainPlaceholder(format, "{message}", message);
        sender.sendMessage(formattedMessage);
        if (onlineTarget != null) {
            onlineTarget.sendMessage(formattedMessage);
        }
    }

    public static UUID getLastMessaged(UUID player) {
        return lastMessaged.get(player);
    }

    public static String getLastFakeMessaged(UUID player) {
        return lastFakeMessaged.get(player);
    }

    public static void setLastMessaged(UUID sender, UUID receiver) {
        lastMessaged.put(sender, receiver);
        lastMessaged.put(receiver, sender);
        lastFakeMessaged.remove(sender);
        lastFakeMessaged.remove(receiver);
    }

    public static void setLastFakeMessaged(UUID sender, String receiverName) {
        lastFakeMessaged.put(sender, receiverName);
        lastMessaged.remove(sender);
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder messageBuilder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            messageBuilder.append(args[i]);
            if (i < args.length - 1) {
                messageBuilder.append(" ");
            }
        }
        return messageBuilder.toString();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial) && !player.equals(sender)) {
                    completions.add(player.getName());
                }
            }
            for (String fake : SpooferBridge.getFakeNames()) {
                if (fake.toLowerCase().startsWith(partial) && (sender instanceof Player self && !fake.equalsIgnoreCase(self.getName()))) {
                    completions.add(fake);
                }
            }
        }
        return completions;
    }
}
