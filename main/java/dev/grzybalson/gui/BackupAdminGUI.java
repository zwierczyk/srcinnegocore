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
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BackupAdminGUI implements InventoryHolder {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final HollyCore plugin;
    private final Player viewer;
    private final List<BackupDataManager.PendingBackup> pendingBackups;
    private final int page;
    private final int maxPage;
    private final Inventory inventory;

    public BackupAdminGUI(HollyCore plugin, Player viewer, List<BackupDataManager.PendingBackup> pendingBackups) {
        this(plugin, viewer, pendingBackups, 0);
    }

    public BackupAdminGUI(HollyCore plugin, Player viewer, List<BackupDataManager.PendingBackup> pendingBackups, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.pendingBackups = pendingBackups;
        this.maxPage = Math.max(0, (int) Math.ceil((double) Math.max(1, pendingBackups.size()) / CONTENT_SLOTS.length) - 1);
        this.page = Math.max(0, Math.min(page, maxPage));
        // 6 rzędów + dekoracje z systemu guis (GuiFactory template54).
        if (plugin.getGuiFactory() != null) {
            this.inventory = plugin.getGuiFactory().createWithDefaultTemplate(
                this,
                54,
                "&8Weryfikacja backupów &7[" + (this.page + 1) + "/" + (maxPage + 1) + "]"
            );
        } else {
            this.inventory = Bukkit.createInventory(this, 54,
                ColorUtils.colorizeComponent("&8Weryfikacja backupów &7[" + (this.page + 1) + "/" + (maxPage + 1) + "]"));
            BackgroundProvider.fillBackground(this.inventory);
        }
        setupItems();
    }

    private void setupItems() {
        int startIndex = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int backupIndex = startIndex + i;
            if (backupIndex >= pendingBackups.size()) {
                break;
            }

            BackupDataManager.PendingBackup backup = pendingBackups.get(backupIndex);
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(backup.playerUuid()));
            meta.displayName(ColorUtils.colorizeComponent("&e&l" + backup.playerName())
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(ColorUtils.colorizeComponent("&8━━━━━━━━━━━━━━━━━━")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8» &7Data: &f" + FORMATTER.format(backup.createdAt()))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8» &7Zgłaszający: &f" + safeName(backup.requestedBy()))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8» &7Statystyki: " + (backup.statsRestored() ? "&aPrzywrócone" : "&cNieprzywrócone"))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8━━━━━━━━━━━━━━━━━━")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&eLPM &8» &7Otwórz podgląd backupu")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);

            NBTUtils.setString(item, "backup_id", backup.id());
            NBTUtils.setString(item, "death_id", backup.deathId());
            inventory.setItem(CONTENT_SLOTS[i], item);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(ColorUtils.colorizeComponent("&a&lOczekujące prośby")
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(
            ColorUtils.colorizeComponent("&8» &7Liczba: &f" + pendingBackups.size())
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
            ColorUtils.colorizeComponent("&8» &7Strona: &f" + (page + 1) + "&7/&f" + (maxPage + 1))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
            ColorUtils.colorizeComponent("&7Kliknij główkę, aby przejrzeć backup")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(4, info);

        if (page > 0) {
            inventory.setItem(48, createNavItem(Material.ARROW, "&e⬅ Poprzednia strona", "page_prev"));
        }
        if (page < maxPage) {
            inventory.setItem(50, createNavItem(Material.ARROW, "&eNastępna strona ➡", "page_next"));
        }
    }

    private static String safeName(java.util.UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : "?";
    }

    private ItemStack createNavItem(Material material, String name, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtils.colorizeComponent(name)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
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

    public int getPage() {
        return page;
    }

    public List<BackupDataManager.PendingBackup> getPendingBackups() {
        return pendingBackups;
    }
}
