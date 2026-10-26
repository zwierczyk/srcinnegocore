package dev.grzybalson.modules.sigil;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SigilRollGui implements InventoryHolder {

    public enum RollKind {
        TYPE,
        POWER
    }

    private final UUID playerUuid;
    private final RollKind kind;
    private final SigilType type;
    private final double power;
    private final double oldPower;
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private Inventory inventory;

    public SigilRollGui(UUID playerUuid, RollKind kind, SigilType type, double power, double oldPower) {
        this.playerUuid = playerUuid;
        this.kind = kind;
        this.type = type;
        this.power = power;
        this.oldPower = oldPower;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public RollKind getKind() {
        return kind;
    }

    public SigilType getType() {
        return type;
    }

    public double getPower() {
        return power;
    }

    public double getOldPower() {
        return oldPower;
    }

    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    public boolean isFinished() {
        return finished.get();
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
