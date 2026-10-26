package dev.grzybalson.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class StaticMenuGUI implements InventoryHolder {

    private final String key;
    private final Map<Integer, String> slotActions = new HashMap<>();
    private Inventory inventory;

    public StaticMenuGUI(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setAction(int slot, String action) {
        if (action == null || action.isBlank()) {
            slotActions.remove(slot);
            return;
        }
        slotActions.put(slot, action);
    }

    public String getAction(int slot) {
        return slotActions.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
