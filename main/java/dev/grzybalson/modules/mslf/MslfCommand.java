package dev.grzybalson.modules.mslf;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MslfCommand implements CommandExecutor, TabCompleter {

    private final MslfManager manager;

    public MslfCommand(MslfManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("Holly.cmd.mslf")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.mslf"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            manager.reload();
            sender.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przeladowano MSLF."));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("resetcooldown")) {
            handleResetCooldown(sender, label, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tylko dla gracza.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("setitem")) {
            if (args.length < 2) {
                ColorUtils.sendUsage(player, "/" + label + " setitem <id>");
                return true;
            }
            MslfItemType type = MslfItemType.byId(args[1]);
            if (type == null) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNieznany item MSLF: &f" + args[1]));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTrzymaj item w rece."));
                return true;
            }
            if (!manager.settings().setItemFromHand(type, hand)) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie udalo sie zapisac itemu."));
                return true;
            }
            manager.reload();
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono wyglad MSLF &f" + type.id() + " &az reki (material, CMD, nazwa, lore)."
            ));
            return true;
        }
        manager.openGui(player);
        return true;
    }

    private void handleResetCooldown(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/" + label + " resetcooldown <nick|*|all>");
            return;
        }
        String targetArg = args[1];
        if (targetArg.equals("*") || targetArg.equalsIgnoreCase("all")) {
            int players = manager.resetAllCooldowns();
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#00FF3B✔ &8Ξ &#4EFA76Zresetowano cooldowny MSLF dla &f" + players + " &aonline graczy."
            ));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetArg);
        if (target == null) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cNie znaleziono gracza &4" + targetArg + "&c."
            ));
            return;
        }
        manager.resetCooldowns(target);
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Zresetowano cooldowny MSLF gracza &f" + target.getName() + "&a."
        ));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("Holly.cmd.mslf")) {
            return out;
        }
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : List.of("gui", "reload", "setitem", "resetcooldown")) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setitem")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            for (MslfItemType type : MslfItemType.values()) {
                if (type.id().startsWith(p)) {
                    out.add(type.id());
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetcooldown")) {
            String p = args[1].toLowerCase(Locale.ROOT);
            if ("*".startsWith(p)) {
                out.add("*");
            }
            if ("all".startsWith(p)) {
                out.add("all");
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }
}
