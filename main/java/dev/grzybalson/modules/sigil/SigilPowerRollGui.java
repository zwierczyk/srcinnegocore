package dev.grzybalson.modules.sigil;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Animacja losowania procentu mocy sigila. */
public class SigilPowerRollGui implements InventoryHolder {

    private final UUID playerUuid;
    private final SigilType type;
    private final double oldPower;
    private final double newPower;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private Inventory inventory;

    public SigilPowerRollGui(UUID playerUuid, SigilType type, double oldPower, double newPower) {
        this.playerUuid = playerUuid;
        this.type = type;
        this.oldPower = oldPower;
        this.newPower = newPower;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public SigilType getType() {
        return type;
    }

    public double getOldPower() {
        return oldPower;
    }

    public double getNewPower() {
        return newPower;
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
