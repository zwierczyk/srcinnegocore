package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.backup.BackupDataManager;
import dev.grzybalson.modules.backup.RollbackManager;
import dev.grzybalson.utils.ItemClientSanitizer;
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

public class PlayerRollbackPreviewGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player viewer;
    private final UUID targetUuid;
    private final String targetName;
    private final BackupDataManager.PlayerSnapshotData snapshot;
    private final List<BackupDataManager.PlayerSnapshotData> allSnapshots;
    private final int returnPage;
    private final Inventory inventory;

    public PlayerRollbackPreviewGUI(HollyCore plugin, Player viewer, UUID targetUuid, String targetName,
                                    BackupDataManager.PlayerSnapshotData snapshot,
                                    List<BackupDataManager.PlayerSnapshotData> allSnapshots, int returnPage) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.snapshot = snapshot;
        this.allSnapshots = allSnapshots;
        this.returnPage = Math.max(0, returnPage);
        String when = snapshot.createdAt() != null
            ? RollbackManager.DATE_FORMATTER.format(snapshot.createdAt())
            : targetName;
        this.inventory = Bukkit.createInventory(this, 54, "§8Cofka: " + when);
        setupItems();
    }

    private void setupItems() {
        ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        sepMeta.setDisplayName(" ");
        separator.setItemMeta(sepMeta);

        for (int i = 36; i < 45; i++) {
            inventory.setItem(i, separator);
        }

        ItemStack[] inv = snapshot.inventory();
        if (inv != null) {
            for (int i = 9; i < 36 && i < inv.length; i++) {
                if (inv[i] != null && !inv[i].getType().isAir()) {
                    inventory.setItem(i - 9, ItemClientSanitizer.forClient(inv[i]));
                }
            }
            for (int i = 0; i < 9 && i < inv.length; i++) {
                if (inv[i] != null && !inv[i].getType().isAir()) {
                    inventory.setItem(27 + i, ItemClientSanitizer.forClient(inv[i]));
                }
            }
        }

        ItemStack[] ec = snapshot.enderChest();
        if (ec != null) {
            for (int i = 0; i < 9 && i < ec.length; i++) {
                if (ec[i] != null && !ec[i].getType().isAir()) {
                    inventory.setItem(36 + i, ItemClientSanitizer.forClient(ec[i]));
                }
            }
        }

        ItemStack[] armor = snapshot.armor();
        if (armor != null) {
            if (armor.length > 3 && armor[3] != null) {
                inventory.setItem(45, ItemClientSanitizer.forClient(armor[3]));
            }
            if (armor.length > 2 && armor[2] != null) {
                inventory.setItem(46, ItemClientSanitizer.forClient(armor[2]));
            }
            if (armor.length > 1 && armor[1] != null) {
                inventory.setItem(47, ItemClientSanitizer.forClient(armor[1]));
            }
            if (armor.length > 0 && armor[0] != null) {
                inventory.setItem(48, ItemClientSanitizer.forClient(armor[0]));
            }
        }

        if (inv != null && inv.length > 40 && inv[40] != null) {
            inventory.setItem(49, ItemClientSanitizer.forClient(inv[40]));
        }

        inventory.setItem(50, createActionItem(Material.LIME_DYE, "§a§lPrzywróć ten stan",
            buildRestoreLore(), "restore"));

        inventory.setItem(51, createActionItem(Material.CLOCK, "§6Informacje",
            buildInfoLore(), "info"));

        inventory.setItem(53, createActionItem(Material.ARROW, "§ePowrót",
            List.of("§7Wróć do listy godzin"), "back"));
    }

    private List<String> buildRestoreLore() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Gracz: §f" + targetName);
        lore.add("§7Bez restartu serwera");
        if (snapshot.hasInventory()) {
            lore.add("§7Przywraca: §fEQ, zbroję, EC");
        } else {
            lore.add("§cBrak EQ/EC — tylko kasa/portfel");
        }
        lore.add("");
        lore.add("§eKliknij, aby cofnąć gracza");
        return lore;
    }

    private List<String> buildInfoLore() {
        List<String> lore = new ArrayList<>();
        lore.add("§8Gracz: §f" + targetName);
        if (snapshot.createdAt() != null) {
            lore.add("§8Czas: §f" + RollbackManager.DATE_FORMATTER.format(snapshot.createdAt()));
        }
        if (snapshot.economyBalance() != null) {
            lore.add("§8Kasa: §f" + snapshot.economyBalance());
        }
        if (snapshot.walletBalance() != null) {
            lore.add("§8Portfel: §f" + snapshot.walletBalance());
        }
        lore.add("§8Rząd 4: §7podgląd enderchesta");
        return lore;
    }

    private ItemStack createActionItem(Material material, String name, List<String> lore, String action) {
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

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public BackupDataManager.PlayerSnapshotData getSnapshot() {
        return snapshot;
    }

    public List<BackupDataManager.PlayerSnapshotData> getAllSnapshots() {
        return allSnapshots;
    }

    public int getReturnPage() {
        return returnPage;
    }
}
