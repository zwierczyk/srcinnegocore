package dev.grzybalson.commands;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RemoveLoreCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
            return true;
        }
        if (!player.hasPermission("Holly.cmd.usunlore")) {
            player.sendMessage(ColorUtils.error("Brak permisji."));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ColorUtils.usage("/usunlore <ilość>"));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException ignored) {
            player.sendMessage(ColorUtils.error("Ilość musi być liczbą."));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(ColorUtils.error("Ilość musi być większa od 0."));
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) {
            player.sendMessage(ColorUtils.error("Musisz trzymać przedmiot z lore w ręce."));
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null || meta.getLore().isEmpty()) {
            player.sendMessage(ColorUtils.error("Ten przedmiot nie ma lore."));
            return true;
        }
        List<String> lore = new ArrayList<>(meta.getLore());
        int remove = Math.min(amount, lore.size());
        for (int i = 0; i < remove; i++) lore.remove(lore.size() - 1);
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
        player.sendMessage(ColorUtils.success("Usunięto &e" + remove + " &aostatnich linijek lore."));
        return true;
    }
}
