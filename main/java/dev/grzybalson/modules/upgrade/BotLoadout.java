package dev.grzybalson.modules.upgrade;

import org.bukkit.inventory.ItemStack;

public record BotLoadout(
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots,
        ItemStack weapon
) {
    public static BotLoadout empty() {
        return new BotLoadout(null, null, null, null, null);
    }
}
