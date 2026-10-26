package dev.grzybalson.modules.cases;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaseAnimationHolder implements InventoryHolder {

    private final String caseName;
    private Inventory inventory;
    private boolean finished;

    public CaseAnimationHolder(String caseName) {
        this.caseName = caseName;
    }

    public String caseName() {
        return caseName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isFinished() {
        return finished;
    }

    public void markFinished() {
        this.finished = true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
