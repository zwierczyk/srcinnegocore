package dev.grzybalson.modules.zywioly;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class ZywiolyHolder implements InventoryHolder {

    enum Type {
        MAIN,
        PICK,
        ROLL
    }

    private final Type type;
    private final String gameId;
    private final boolean joining;
    private Inventory inventory;
    private Map<Integer, String> gamesBySlot = Map.of();

    ZywiolyHolder(Type type, String gameId, boolean joining) {
        this.type = type;
        this.gameId = gameId;
        this.joining = joining;
    }

    Type type() {
        return type;
    }

    String gameId() {
        return gameId;
    }

    boolean joining() {
        return joining;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    void setGamesBySlot(Map<Integer, String> gamesBySlot) {
        this.gamesBySlot = gamesBySlot == null ? Map.of() : Map.copyOf(gamesBySlot);
    }

    Map<Integer, String> gamesBySlot() {
        return gamesBySlot;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
