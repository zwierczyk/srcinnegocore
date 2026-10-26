package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.EqPreviewGUI;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.SpooferBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EqSeeCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public EqSeeCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorize("&cTa komenda jest dostępna tylko dla graczy!"));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.eqsee") && !player.isOp()) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.eqsee"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ColorUtils.usage("/" + label + " <nick>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target != null && target.isOnline()) {
            new EqPreviewGUI(plugin, player, target).open();
            return true;
        }

        ItemStack[] fakeEquipment = SpooferBridge.getFakeEquipment(args[0]);
        if (fakeEquipment != null) {
            new EqPreviewGUI(plugin, player, null).openFake(
                args[0],
                fakeEquipment[0],
                fakeEquipment[1],
                fakeEquipment[2],
                fakeEquipment[3],
                fakeEquipment[4]
            );
            return true;
        }

        player.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
        ));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(partial)) {
                    completions.add(online.getName());
                }
            }
            for (String fake : SpooferBridge.getFakeNames()) {
                if (fake.toLowerCase().startsWith(partial)) {
                    completions.add(fake);
                }
            }
        }
        return completions;
    }
}
