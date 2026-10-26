package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.backup.BackupDataManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI odbioru backupów — kilka zatwierdzonych naraz.
 */
public class ClaimBackupGUI implements InventoryHolder {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault());

    private final HollyCore plugin;
    private final Player viewer;
    private final List<BackupDataManager.PendingBackup> backups;
    private final int page;
    private final int maxPage;
    private final Inventory inventory;

    public ClaimBackupGUI(HollyCore plugin, Player viewer, List<BackupDataManager.PendingBackup> backups) {
        this(plugin, viewer, backups, 0);
    }

    public ClaimBackupGUI(HollyCore plugin, Player viewer, List<BackupDataManager.PendingBackup> backups, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.backups = backups != null ? backups : List.of();
        this.maxPage = Math.max(0, (int) Math.ceil((double) Math.max(1, this.backups.size()) / CONTENT_SLOTS.length) - 1);
        this.page = Math.max(0, Math.min(page, maxPage));
        this.inventory = Bukkit.createInventory(this, 54,
            ColorUtils.colorize("&8Odbierz backup &7[" + (this.page + 1) + "/" + (maxPage + 1) + "]"));
        setupItems();
    }

    private void setupItems() {
        plugin.getGuiFactory().applyDefaultTemplate(inventory);
        for (int slot : CONTENT_SLOTS) {
            inventory.setItem(slot, null);
        }

        if (backups.isEmpty()) {
            ItemStack empty = named(Material.BARRIER, ColorUtils.colorize("&cBrak dostępnych backupów"));
            ItemMeta meta = empty.getItemMeta();
            meta.setLore(List.of(
                ColorUtils.colorize("&7Nie masz zatwierdzonego backupu."),
                ColorUtils.colorize("&7Poczekaj na administrację.")
            ));
            empty.setItemMeta(meta);
            inventory.setItem(22, empty);
        } else {
            int start = page * CONTENT_SLOTS.length;
            for (int i = 0; i < CONTENT_SLOTS.length; i++) {
                int idx = start + i;
                if (idx >= backups.size()) {
                    break;
                }
                BackupDataManager.PendingBackup backup = backups.get(idx);
                inventory.setItem(CONTENT_SLOTS[i], createBackupItem(backup, idx + 1));
            }
        }

        ItemStack info = named(Material.BOOK, ColorUtils.colorize("&6&lTwoje backupy"));
        ItemMeta infoMeta = info.getItemMeta();
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ColorUtils.colorize("&8» &7Dostępnych: &f" + backups.size()));
        infoLore.add(ColorUtils.colorize("&8» &7EQ musi być &cpuste&7, aby odebrać"));
        infoLore.add("");
        infoLore.add(ColorUtils.colorize("&eKliknij backup, aby go odebrać"));
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        if (page > 0) {
            inventory.setItem(48, named(Material.ARROW, ColorUtils.colorize("&e⬅ Poprzednia strona")));
            NBTUtils.setString(inventory.getItem(48), "claim_nav", "prev");
        }
        if (page < maxPage) {
            inventory.setItem(50, named(Material.ARROW, ColorUtils.colorize("&eNastępna strona ➡")));
            NBTUtils.setString(inventory.getItem(50), "claim_nav", "next");
        }

        ItemStack close = named(Material.RED_STAINED_GLASS_PANE, ColorUtils.colorize("&cZamknij"));
        NBTUtils.setString(close, "claim_nav", "close");
        inventory.setItem(49, close);
    }

    private ItemStack createBackupItem(BackupDataManager.PendingBackup backup, int index) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorUtils.colorize("&a&lBackup #" + index));
        List<String> lore = new ArrayList<>();
        lore.add(ColorUtils.colorize("&8━━━━━━━━━━━━━━━━━━"));
        lore.add(ColorUtils.colorize("&8» &7Gracz: &f" + backup.playerName()));
        if (backup.approvedAt() != null) {
            lore.add(ColorUtils.colorize("&8» &7Zatwierdzono: &f" + FORMATTER.format(backup.approvedAt())));
        } else if (backup.createdAt() != null) {
            lore.add(ColorUtils.colorize("&8» &7Utworzono: &f" + FORMATTER.format(backup.createdAt())));
        }
        // Wyświetl kto nadał backup
        if (backup.approvedBy() != null) {
            String approverName = Bukkit.getOfflinePlayer(backup.approvedBy()).getName();
            if (approverName != null && !approverName.isEmpty()) {
                lore.add(ColorUtils.colorize("&8» &7Nadał: &e" + approverName));
            }
        }
        lore.add(ColorUtils.colorize("&8» &7Death ID: &e" + shortId(backup.deathId())));
        lore.add(ColorUtils.colorize("&8━━━━━━━━━━━━━━━━━━"));
        lore.add("");
        lore.add(ColorUtils.colorize("&aLPM &8» &7Odbierz ekwipunek"));
        lore.add(ColorUtils.colorize("&7Wymagane: &cpuste EQ + zbroja + offhand"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBTUtils.setString(item, "claim_backup_id", backup.id());
        return item;
    }

    private static String shortId(String id) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        return id.length() <= 10 ? id : id.substring(0, 8) + "…";
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    public int getPage() {
        return page;
    }

    public List<BackupDataManager.PendingBackup> getBackups() {
        return backups;
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
