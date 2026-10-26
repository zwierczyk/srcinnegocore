package dev.grzybalson.anarchiagg.adminitems;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.customitems.items.HydroTrojzab;
import dev.grzybalson.anarchiagg.customitems.items.RozdzkaIluzjonisty;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminItemsCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final AdminItemsInfoGUI infoGui;

    public AdminItemsCommand(HollyCore plugin, AdminItemsInfoGUI infoGui) {
        this.plugin = plugin;
        this.infoGui = infoGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("resetcooldown") || args[0].equalsIgnoreCase("resetujcooldown"))) {
            return handleResetCooldown(sender, label, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cTa komenda jest dostepna tylko dla graczy!");
            return true;
        }

        if (!hasPermission(sender)) {
            player.sendMessage("§cBrak uprawnien!");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            if (this.infoGui != null) {
                this.infoGui.openItems(player);
            }
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("itemy") || args[0].equalsIgnoreCase("storage") || args[0].equalsIgnoreCase("itemstorage"))) {
            if (plugin.getItemStorageManager() == null) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CItemStorage jest wylaczony."));
                return true;
            }
            new dev.grzybalson.modules.itemstorage.ItemStorageGUI(plugin).open(player);
            return true;
        }

        new AdminItemsGUI(this.plugin).open(player);
        return true;
    }

    private boolean handleResetCooldown(CommandSender sender, String label, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(ColorUtils.noPermission(label + " resetcooldown", "Holly.cmd.aitems"));
            return true;
        }

        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/" + label + " resetcooldown <nick|all|me>");
            return true;
        }

        String targetArg = args[1];
        if (targetArg.equalsIgnoreCase("all") || targetArg.equalsIgnoreCase("*")) {
            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                resetPlayer(online);
                count++;
            }
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8• &#4EFA76Zresetowano cooldown eventówek dla &f" + count + " &agraczy."));
            return true;
        }

        if (targetArg.equalsIgnoreCase("me")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CTylko gracz może użyć &fme&c."));
                return true;
            }
            resetPlayer(player);
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8• &#4EFA76Zresetowano Twoje cooldowny eventówek."));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CGracz &f" + targetArg + " &cnie jest online."));
            return true;
        }

        resetPlayer(target);
        sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8• &#4EFA76Zresetowano cooldown eventówek dla &f" + target.getName() + "&a."));
        if (!sender.equals(target)) {
            target.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8• &#4EFA76Admin zresetował Twoje cooldowny eventówek."));
        }
        return true;
    }

    private void resetPlayer(Player player) {
        AdminItemsCooldownResetter.reset(player, plugin);
        EventItemUseGuard.clear(player);
        HydroTrojzab.resetCooldowns(player.getUniqueId());
        RozdzkaIluzjonisty.resetCooldowns(player.getUniqueId());
        for (Material material : Material.values()) {
            if (material.isItem() && player.hasCooldown(material)) {
                player.setCooldown(material, 0);
            }
        }
    }

    private boolean hasPermission(CommandSender sender) {
        return sender.hasPermission("Holly.cmd.aitems")
            || sender.hasPermission("hollycore.adminitems")
            || sender.isOp();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasPermission(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("info", "itemy", "storage", "resetcooldown", "resetujcooldown"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("resetcooldown") || args[0].equalsIgnoreCase("resetujcooldown"))) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("me");
            suggestions.add("all");
            for (Player online : Bukkit.getOnlinePlayers()) {
                suggestions.add(online.getName());
            }
            return filter(suggestions, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String needle = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(needle)) {
                result.add(value);
            }
        }
        return result;
    }
}
