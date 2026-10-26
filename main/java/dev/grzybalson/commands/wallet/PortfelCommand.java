package dev.grzybalson.commands.wallet;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.WalletGUI;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.LockedFeatureMessages;
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
import java.util.Locale;

public class PortfelCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public PortfelCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.aportfel")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.aportfel"));
                return true;
            }
            plugin.getConfigManager().reloadWalletConfig();
            if (plugin.getWalletProductStatsService() != null) {
                plugin.getWalletProductStatsService().invalidateCache();
            }
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getWalletConfig().getMessages().getReloaded()));
            return true;
        }

        if (!plugin.getConfigManager().getWalletConfig().isEnabled()) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getWalletConfig().getMessages().getDisabled()));
            return true;
        }

        if (args.length >= 1 && sender.hasPermission("Holly.cmd.aportfel") && !args[0].equalsIgnoreCase("reload")) {
            showTargetBalance(sender, args[0]);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getWalletConfig().getMessages().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.portfel")) {
            LockedFeatureMessages.send(player, "/portfel");
            return true;
        }

        plugin.getWalletManager().getBalance(player.getUniqueId()).thenAccept(balance ->
            plugin.getServer().getScheduler().runTask(plugin, () -> new WalletGUI(plugin, player).open())
        );
        return true;
    }

    private void showTargetBalance(CommandSender sender, String name) {
        WalletPlayerLookup.ResolvedTarget local = WalletPlayerLookup.resolveLocal(plugin, name);
        if (local != null) {
            sendBalance(sender, local);
            return;
        }
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &7Wyszukiwanie gracza &f" + name.trim() + "&7 (także offline)..."));
        WalletPlayerLookup.resolve(plugin, name).thenAccept(resolved -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (resolved == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getWalletConfig().getMessages().getPlayerNotFound().replace("{PLAYER}", name)));
                return;
            }
            sendBalance(sender, resolved);
        })).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getWalletConfig().getMessages().getPlayerNotFound().replace("{PLAYER}", name))));
            return null;
        });
    }

    private void sendBalance(CommandSender sender, WalletPlayerLookup.ResolvedTarget target) {
        plugin.getWalletManager().getBalance(target.uuid()).thenAccept(balance -> Bukkit.getScheduler().runTask(plugin, () ->
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getWalletConfig().getMessages().getBalanceTarget()
                    .replace("{PLAYER}", target.name() != null ? target.name() : target.uuid().toString())
                    .replace("{BALANCE}", String.format(Locale.US, "%.2f", balance))
                    .replace("{CURRENCY}", plugin.getConfigManager().getWalletConfig().getCurrencyName())
            ))
        ));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> completions = new ArrayList<>();
        String partial = args[0].toLowerCase(Locale.ROOT);
        if (sender.hasPermission("Holly.cmd.aportfel") && "reload".startsWith(partial)) {
            completions.add("reload");
        }
        if (sender.hasPermission("Holly.cmd.aportfel")) {
            completions.addAll(WalletPlayerLookup.suggestNames(plugin, args[0]));
        }
        return completions;
    }
}
