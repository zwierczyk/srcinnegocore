package dev.grzybalson.modules.trash;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class TrashGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final Inventory inventory;

    public TrashGUI(HollyCore plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54, "§8Kosz");
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }
}
