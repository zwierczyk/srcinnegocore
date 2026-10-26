package dev.grzybalson.modules.mission;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class MissionGuiHolder implements InventoryHolder {

    private Inventory inventory;

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new UnsupportedOperationException("Inventory is managed by MissionManager");
        }
        return inventory;
    }
}
