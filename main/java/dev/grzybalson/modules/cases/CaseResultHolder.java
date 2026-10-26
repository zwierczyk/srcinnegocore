package dev.grzybalson.modules.cases;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaseResultHolder implements InventoryHolder {

    private final String caseName;
    private final CaseOpenType previousOpenType;
    private Inventory inventory;

    public CaseResultHolder(String caseName, CaseOpenType previousOpenType) {
        this.caseName = caseName;
        this.previousOpenType = previousOpenType;
    }

    public String caseName() {
        return caseName;
    }

    public CaseOpenType previousOpenType() {
        return previousOpenType;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
