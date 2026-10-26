package dev.grzybalson.modules.sigil;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SigilGui implements InventoryHolder {

    private final SigilManager manager;
    private final UUID playerUuid;
    private Inventory inventory;

    public SigilGui(SigilManager manager, Player player, SigilPlayerData data) {
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
