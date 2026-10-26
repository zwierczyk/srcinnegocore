package dev.grzybalson.modules.szaman;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PerksRollGui implements InventoryHolder {

    private final UUID playerUuid;
    private final PerkDefinition reward;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private Inventory inventory;

    public PerksRollGui(UUID playerUuid, PerkDefinition reward) {
        this.playerUuid = playerUuid;
        this.reward = reward;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public PerkDefinition getReward() {
        return reward;
    }

    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    public boolean isFinished() {
        return finished.get();
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
