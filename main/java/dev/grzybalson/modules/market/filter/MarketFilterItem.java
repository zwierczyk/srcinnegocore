package dev.grzybalson.modules.market.filter;

import org.bukkit.Material;

public final class MarketFilterItem {

    private final Material material;
    private final int customModelData;

    public MarketFilterItem(Material material, int customModelData) {
        this.material = material;
        this.customModelData = customModelData;
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }
}
