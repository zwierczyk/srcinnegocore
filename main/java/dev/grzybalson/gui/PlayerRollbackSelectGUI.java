package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.backup.BackupDataManager;
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
import java.util.List;
import java.util.UUID;

public class PlayerRollbackSelectGUI implements InventoryHolder {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final HollyCore plugin;
    private final Player viewer;
    private final UUID targetUuid;
    private final String targetName;
    private final List<BackupDataManager.PlayerSnapshotData> snapshots;
    private final int page;
    private final int maxPage;
    private final Inventory inventory;

    public PlayerRollbackSelectGUI(HollyCore plugin, Player viewer, UUID targetUuid,
                                   String targetName, List<BackupDataManager.PlayerSnapshotData> snapshots) {
        this(plugin, viewer, targetUuid, targetName, snapshots, 0);
    }

    public PlayerRollbackSelectGUI(HollyCore plugin, Player viewer, UUID targetUuid,
                                   String targetName, List<BackupDataManager.PlayerSnapshotData> snapshots, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.snapshots = snapshots;
        this.maxPage = Math.max(0, (int) Math.ceil((double) Math.max(1, snapshots.size()) / CONTENT_SLOTS.length) - 1);
        this.page = Math.max(0, Math.min(page, maxPage));
        this.inventory = Bukkit.createInventory(this, 54, "§8Cofka: " + targetName + " §7[" + (this.page + 1) + "/" + (maxPage + 1) + "]");
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

            BackupDataManager.PlayerSnapshotData snapshot = snapshots.get(snapshotIndex);
            ItemStack item = new ItemStack(snapshot.hasInventory() ? Material.CHEST : Material.GOLD_INGOT);
            ItemMeta meta = item.getItemMeta();
            String when = snapshot.createdAt() != null
                ? RollbackManager.DATE_FORMATTER.format(snapshot.createdAt())
                : "Nieznana data";
            meta.setDisplayName("§f" + when);

            List<String> lore = new ArrayList<>();
            if (snapshot.hasInventory()) {
                lore.add("§8EQ: §f" + countItems(snapshot.inventory()) + " slotów");
                lore.add("§8Zbroja: §f" + countItems(snapshot.armor()) + " slotów");
                lore.add("§8Enderchest: §f" + countItems(snapshot.enderChest()) + " slotów");
            } else {
                lore.add("§cBrak zapisu EQ/EC");
                lore.add("§7(tylko kasa / portfel)");
            }
            if (snapshot.economyBalance() != null) {
                lore.add("§8Kasa: §f" + formatBalance(snapshot.economyBalance()));
            }
            if (snapshot.walletBalance() != null) {
                lore.add("§8Portfel: §f" + formatBalance(snapshot.walletBalance()));
            }
            lore.add("");
            lore.add("§eKliknij, aby zobaczyć podgląd");
            meta.setLore(lore);
            item.setItemMeta(meta);

            NBTUtils.setString(item, "snapshot_index", String.valueOf(snapshotIndex));
            inventory.setItem(CONTENT_SLOTS[i], item);
        }

        if (page > 0) {
            inventory.setItem(48, createNavItem(Material.ARROW, "§e⬅ Poprzednia strona", "page_prev"));
        }

        ItemStack pageInfo = new ItemStack(Material.CLOCK);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.setDisplayName("§6Snapshoty §f" + targetName);
        pageInfoMeta.setLore(List.of(
            "§7Punktów czasu: §f" + snapshots.size(),
            "§7Strona: §f" + (page + 1) + "§7/§f" + (maxPage + 1),
            "",
            "§7Cofka gracza §ebez restartu §7serwera"
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

        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§eCofka gracza §f" + targetName);
        meta.setLore(List.of(
            "§7Globalne snapshoty co §f"
                + plugin.getConfigManager().getBackupConfig().getSnapshotIntervalMinutes() + " min",
            "§7Wybierz godzinę, aby przywrócić stan"
        ));
        info.setItemMeta(meta);
        inventory.setItem(4, info);
    }

    private static int countItems(ItemStack[] items) {
        if (items == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                count++;
            }
        }
        return count;
    }

    private static String formatBalance(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
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

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public List<BackupDataManager.PlayerSnapshotData> getSnapshots() {
        return snapshots;
    }

    public int getPage() {
        return page;
    }
}
