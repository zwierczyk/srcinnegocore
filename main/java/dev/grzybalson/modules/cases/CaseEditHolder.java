package dev.grzybalson.modules.cases;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaseEditHolder implements InventoryHolder {

    private final String caseName;
    private Inventory inventory;

    public CaseEditHolder(String caseName) {
        this.caseName = caseName;
    }

    public String caseName() {
        return caseName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
