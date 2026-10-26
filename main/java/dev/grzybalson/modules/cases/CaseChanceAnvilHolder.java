package dev.grzybalson.modules.cases;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaseChanceAnvilHolder implements InventoryHolder {

    private final String caseName;
    private final int rewardIndex;
    private String lastChanceInput;
    private Inventory inventory;

    public CaseChanceAnvilHolder(String caseName, int rewardIndex, String initialChanceInput) {
        this.caseName = caseName;
        this.rewardIndex = rewardIndex;
        this.lastChanceInput = initialChanceInput;
    }

    public String caseName() {
        return caseName;
    }

    public int rewardIndex() {
        return rewardIndex;
    }

    public String lastChanceInput() {
        return lastChanceInput;
    }

    public void setLastChanceInput(String lastChanceInput) {
        if (lastChanceInput != null && !lastChanceInput.isBlank()) {
            this.lastChanceInput = lastChanceInput;
        }
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
