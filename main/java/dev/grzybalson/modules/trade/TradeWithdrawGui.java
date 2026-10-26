package dev.grzybalson.modules.trade;

import dev.grzybalson.HollyCore;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TradeWithdrawGui implements InventoryHolder {

    private final HollyCore plugin;
    private final UUID ownerId;
    private final List<ItemStack> items;
    private final Inventory inventory;

    public TradeWithdrawGui(HollyCore plugin, Player owner, List<ItemStack> items) {
        this.plugin = plugin;
        this.ownerId = owner.getUniqueId();
        this.items = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                this.items.add(item.clone());
            }
        }

        int rows = Math.min(6, Math.max(1, (int) Math.ceil(this.items.size() / 7.0)) + 2);
        rows = plugin.getGuiFactory().normalizeRows(rows);
        TradeConfig config = plugin.getTradeManager().getConfig();
        this.inventory = plugin.getGuiFactory().createInventory(this, rows * 9, config.withdrawTitle());
        build();
    }

    private void build() {
        plugin.getGuiFactory().applyDefaultTemplate(inventory);
        int[] slots = plugin.getGuiFactory().getContentSlots(inventory);
        for (int i = 0; i < items.size() && i < slots.length; i++) {
            inventory.setItem(slots[i], items.get(i).clone());
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
        plugin.getTradeManager().playSound(player, "open");
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
