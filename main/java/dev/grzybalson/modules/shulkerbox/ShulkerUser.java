package dev.grzybalson.modules.shulkerbox;

import org.bukkit.Material;

public class ShulkerUser {
    private final Material type;
    private final int slot;

    public ShulkerUser(Material type, int slot) {
        this.type = type;
        this.slot = slot;
    }

    public int getSlot() {
        return slot;
    }

    public Material getType() {
        return type;
    }
}
