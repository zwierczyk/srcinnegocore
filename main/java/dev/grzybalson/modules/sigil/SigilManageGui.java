package dev.grzybalson.modules.sigil;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Menu zarządzania jednym sigilem (PPM z katalogu) — przycisk przełosowania %. */
public class SigilManageGui implements InventoryHolder {

    private final UUID playerUuid;
    private final SigilType type;
    private Inventory inventory;

    public SigilManageGui(UUID playerUuid, SigilType type) {
        this.playerUuid = playerUuid;
        this.type = type;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public SigilType getType() {
        return type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
