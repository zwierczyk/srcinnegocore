package dev.grzybalson.pvp.kowal;

import dev.grzybalson.pvp.kowal.KowalUpgradeHandler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class KowalGetMethod {
    public double getTotalUpgradeValue(Player player, KowalUpgradeHandler handler) {
        ItemStack[] armorContents;
        double totalValue = 0.0;
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : armorContents = inventory.getArmorContents()) {
            if (item == null || !handler.canUpgrade(item)) continue;
            totalValue += handler.getItemUpgradeValue(item);
        }
        return totalValue;
    }
}

