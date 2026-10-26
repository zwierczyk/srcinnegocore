package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemRepair;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RepairCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public RepairCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy!"));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.repair")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.repair"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            if (!player.hasPermission("Holly.cmd.repair.all")) {
                player.sendMessage(ColorUtils.noPermission(label + " all", "Holly.cmd.repair.all"));
                return true;
            }
            repairAll(player);
        } else {
            repairHand(player);
        }

        return true;
    }

    private void repairHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cMusisz trzymać przedmiot w ręce!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!(item.getItemMeta() instanceof Damageable)) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTen przedmiot nie może być naprawiony!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!ItemRepair.repair(item)) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FFB800⚠ &8Ξ &7Ten przedmiot nie wymaga naprawy!"));
            return;
        }

        player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Naprawiono przedmiot w ręce!"));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    private void repairAll(Player player) {
        int repairedCount = 0;

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (ItemRepair.repair(item)) {
                repairedCount++;
            }
        }

        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (ItemRepair.repair(item)) {
                repairedCount++;
            }
        }

        if (ItemRepair.repair(player.getInventory().getItemInOffHand())) {
            repairedCount++;
        }

        if (repairedCount == 0) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FFB800⚠ &8Ξ &7Nie masz przedmiotów wymagających naprawy!"));
        } else {
            player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Naprawiono &e" + repairedCount + " &aprzedmiotów!"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("Holly.cmd.repair.all")) {
            if ("all".startsWith(args[0].toLowerCase())) {
                completions.add("all");
            }
        }

        return completions;
    }
}
