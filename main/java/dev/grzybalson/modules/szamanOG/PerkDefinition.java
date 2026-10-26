package dev.grzybalson.modules.szaman;

import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public class PerkDefinition {

    private final PerkType type;
    private final String displayName;
    private final int maxLevel;
    private final double chance;
    private final ItemStack item;
    private final Map<Integer, Double> values;
    private final boolean enabled;

    public PerkDefinition(PerkType type, String displayName, int maxLevel, double chance, ItemStack item, Map<Integer, Double> values, boolean enabled) {
        this.type = type;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.chance = chance;
        this.item = item;
        this.values = new LinkedHashMap<>(values);
        this.enabled = enabled;
    }

    public PerkType getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public double getChance() {
        return chance;
    }

    public ItemStack getItem() {
        return item == null ? null : item.clone();
    }

    public double getValue(int level) {
        if (level <= 0) {
            return 0.0D;
        }

        Double direct = values.get(level);
        if (direct != null) {
            return direct;
        }

        double last = 0.0D;
        for (Map.Entry<Integer, Double> entry : values.entrySet()) {
            if (entry.getKey() <= level) {
                last = entry.getValue();
            }
        }
        return last;
    }

    public Map<Integer, Double> getValues() {
        return Map.copyOf(values);
    }
}
