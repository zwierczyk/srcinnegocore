package dev.grzybalson.modules.boss;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public record BossRewards(
    List<String> commands,
    List<ItemDrop> items,
    boolean dropToInventory,
    String broadcast,
    Map<Integer, PlaceReward> top
) {
    public record ItemDrop(ItemStack item, double chance) {
    }

    public record PlaceReward(List<String> commands, List<ItemDrop> items) {
    }
}
