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

public class ServerRollbackSelectGUI implements InventoryHolder {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final HollyCore plugin;
    private final Player viewer;
    private final List<Date> snapshots;
    private final int page;
    private final int maxPage;
    private final Inventory inventory;

    public ServerRollbackSelectGUI(HollyCore plugin, Player viewer, List<Date> snapshots) {
        this(plugin, viewer, snapshots, 0);
    }

    public ServerRollbackSelectGUI(HollyCore plugin, Player viewer, List<Date> snapshots, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.snapshots = snapshots;
        this.maxPage = Math.max(0, (int) Math.ceil((double) Math.max(1, snapshots.size()) / CONTENT_SLOTS.length) - 1);
        this.page = Math.max(0, Math.min(page, maxPage));
        this.inventory = Bukkit.createInventory(this, 54, "§8Cofka serwera §7[" + (this.page + 1) + "/" + (maxPage + 1) + "]");
        setupItems();
    }

    private void setupItems() {
        fillBackground();

        int startIndex = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int snapshotIndex = startIndex + i;
            if (snapshotIndex >= snapshots.size()) {
                break;
            }

            Date snapshotDate = snapshots.get(snapshotIndex);
            ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
            ItemMeta meta = item.getItemMeta();
            String when = RollbackManager.DATE_FORMATTER.format(snapshotDate.toInstant());
            meta.setDisplayName("§f" + when);
            meta.setLore(List.of(
                "",
                "§7Przywraca cały serwer:",
                "§8▪ §fEQ, zbroja, enderchesty",
                "§8▪ §fKasa, portfele, rynek",
                "",
                "§eKliknij, aby potwierdzić cofkę"
            ));
            item.setItemMeta(meta);
            NBTUtils.setString(item, "snapshot_index", String.valueOf(snapshotIndex));
            inventory.setItem(CONTENT_SLOTS[i], item);
        }

        if (page > 0) {
            inventory.setItem(48, createNavItem(Material.ARROW, "§e⬅ Poprzednia strona", "page_prev"));
        }

        ItemStack pageInfo = new ItemStack(Material.CLOCK);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.setDisplayName("§6Cofka całego serwera");
        pageInfoMeta.setLore(List.of(
            "§7Snapshotów: §f" + snapshots.size(),
            "§7Strona: §f" + (page + 1) + "§7/§f" + (maxPage + 1),
            "",
            "§cUwaga: §7wymaga restartu serwera"
        ));
        pageInfo.setItemMeta(pageInfoMeta);
        inventory.setItem(49, pageInfo);

        if (page < maxPage) {
            inventory.setItem(50, createNavItem(Material.ARROW, "§aNastępna strona ➡", "page_next"));
        }
    }

    private ItemStack createNavItem(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        NBTUtils.setString(item, "action", action);
        return item;
    }

    private void fillBackground() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§c§lCofka serwera");
        List<String> lore = new ArrayList<>();
        lore.add("§7Wybierz punkt czasu snapshotu");
        lore.add("§7co §f" + plugin.getConfigManager().getBackupConfig().getSnapshotIntervalMinutes() + " min§7.");
        lore.add("");
        lore.add("§7Przywraca stan §ewszystkich graczy§7,");
        lore.add("§7rynku i oczekujących backupów.");
        meta.setLore(lore);
        info.setItemMeta(meta);
        inventory.setItem(4, info);
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

    public List<Date> getSnapshots() {
        return snapshots;
    }

    public int getPage() {
        return page;
    }

    public Date getSnapshotDate(int index) {
        if (index < 0 || index >= snapshots.size()) {
            return null;
        }
        return snapshots.get(index);
    }
}
