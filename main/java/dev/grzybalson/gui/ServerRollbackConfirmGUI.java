package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.backup.RollbackManager;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ServerRollbackConfirmGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player viewer;
    private final Date targetDate;
    private final RollbackManager.RollbackPreview preview;
    private final List<Date> allSnapshots;
    private final int returnPage;
    private final Inventory inventory;

    public ServerRollbackConfirmGUI(HollyCore plugin, Player viewer, Date targetDate,
                                    RollbackManager.RollbackPreview preview,
                                    List<Date> allSnapshots, int returnPage) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetDate = targetDate;
        this.preview = preview;
        this.allSnapshots = allSnapshots;
        this.returnPage = Math.max(0, returnPage);
        this.inventory = Bukkit.createInventory(this, 27, "§8Potwierdź cofkę serwera");
        setupItems();
    }

    private void setupItems() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        int onlineCount = 0;
        int offlineCount = 0;
        for (UUID uuid : preview.playerUuids()) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                onlineCount++;
            } else {
                offlineCount++;
            }
        }

        ItemStack info = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§6§lPodsumowanie cofki");
        List<String> lore = new ArrayList<>();
        lore.add("§8Punkt czasu: §f" + RollbackManager.DATE_FORMATTER.format(targetDate.toInstant()));
        lore.add("§8Snapshotów graczy: §f" + preview.playerUuids().size());
        lore.add("§8Online: §a" + onlineCount + " §8| §7Offline: §e" + offlineCount);
        lore.add("§8Ofert rynku: §f" + preview.marketItemsCount());
        lore.add("");
        lore.add("§7Zakres: §fEQ, EC, kasa, portfel, rynek");
        lore.add("§c§lRestart serwera po cofce!");
        infoMeta.setLore(lore);
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        inventory.setItem(11, createAction(Material.LIME_DYE, "§a§lTAK — Cofnij serwer",
            List.of("§7Przywraca stan do wybranej godziny", "§cWymaga restartu serwera"), "confirm"));
        inventory.setItem(15, createAction(Material.RED_DYE, "§c§lNIE — Anuluj",
            List.of("§7Wróć bez zmian"), "cancel"));
    }

    private ItemStack createAction(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBTUtils.setString(item, "action", action);
        return item;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getViewer() {
        return viewer;
    }

    public Date getTargetDate() {
        return targetDate;
    }

    public RollbackManager.RollbackPreview getPreview() {
        return preview;
    }

    public List<Date> getAllSnapshots() {
        return allSnapshots;
    }

    public int getReturnPage() {
        return returnPage;
    }
}
