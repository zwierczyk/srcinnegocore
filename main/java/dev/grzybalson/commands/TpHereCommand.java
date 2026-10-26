package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.managers.TeleportRequestManager;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TpHereCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public TpHereCommand(HollyCore plugin) {
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

        String permission = label.equalsIgnoreCase("s") ? "Holly.cmd.s" : "Holly.cmd.tphere";
        if (!player.hasPermission(permission)) {
            player.sendMessage(ColorUtils.noPermission(label, permission));
            return true;
        }

        if (args.length < 1) {
            ColorUtils.sendUsage(player, "/" + label + " <gracz>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(ColorUtils.error("Nie możesz wysłać prośby do siebie!"));
            return true;
        }

        if (player.hasPermission("Holly.cmd.tphere.instant")) {
            target.teleport(player.getLocation());
            player.sendMessage(ColorUtils.success("Teleportowano " + target.getName() + " do Ciebie!"));
            target.sendMessage(ColorUtils.info("Zostałeś teleportowany do " + player.getName() + "!"));
            return true;
        }

        if (plugin.getTeleportRequestManager().isOnCooldown(player.getUniqueId())) {
            long remaining = plugin.getTeleportRequestManager().getRemainingCooldown(player.getUniqueId());
            player.sendMessage(ColorUtils.error("Poczekaj jeszcze " + (remaining / 1000 + 1) + "s przed wysłaniem kolejnej prośby!"));
            return true;
        }

        plugin.getTeleportRequestManager().createRequest(player, target, TeleportRequestManager.RequestType.TPAHERE);

        player.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getTpaRequestSent()
                .replace("{player}", target.getName())
        ));

        Component acceptButton = ColorUtils.colorizeComponent("&a[AKCEPTUJ]")
            .clickEvent(ClickEvent.runCommand("/tpaccept"))
            .hoverEvent(HoverEvent.showText(ColorUtils.colorizeComponent("&7Kliknij, aby zaakceptować")));

        Component denyButton = ColorUtils.colorizeComponent("&c[ODRZUĆ]")
            .clickEvent(ClickEvent.runCommand("/tpdeny"))
            .hoverEvent(HoverEvent.showText(ColorUtils.colorizeComponent("&7Kliknij, aby odrzucić")));

        target.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &e" + player.getName() + " &7chce, żebyś się do niego teleportował!"
        ));
        target.sendMessage(acceptButton.append(Component.text(" ")).append(denyButton));

        return true;
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
        }
        return completions;
    }
}
