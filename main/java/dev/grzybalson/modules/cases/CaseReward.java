package dev.grzybalson.modules.cases;

import org.bukkit.inventory.ItemStack;

public final class CaseReward {

    private final ItemStack item;
    private final Integer editorSlot;
    private double chance;

    public CaseReward(ItemStack item, double chance) {
        this(item, chance, null);
    }

    public CaseReward(ItemStack item, double chance, Integer editorSlot) {
        this.item = item;
        this.chance = chance;
        this.editorSlot = editorSlot;
    }

    public ItemStack item() {
        return item;
    }

    public double chance() {
        return chance;
    }

    public Integer editorSlot() {
        return editorSlot;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }
}
