package dev.grzybalson.modules.event;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DungeonJoinAnvilHolder implements InventoryHolder {

    private String lastCode = "";
    private Inventory inventory;

    public String lastCode() {
        return lastCode;
    }

    public void setLastCode(String lastCode) {
        this.lastCode = lastCode == null ? "" : lastCode.trim().toUpperCase();
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
