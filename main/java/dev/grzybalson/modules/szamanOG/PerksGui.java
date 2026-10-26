package dev.grzybalson.modules.szaman;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PerksGui implements InventoryHolder {

    private final PerksManager manager;
    private final UUID playerUuid;
    private Inventory inventory;

    public PerksGui(PerksManager manager, Player player, PerkPlayerData data) {
        this.manager = manager;
        this.playerUuid = player.getUniqueId();
        this.inventory = manager.buildInventory(this, player, data);
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void open() {
        Player player = org.bukkit.Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.openInventory(inventory);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
