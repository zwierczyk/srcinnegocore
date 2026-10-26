package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.backup.BackupDataManager;
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

import java.util.List;

public class BackupPreviewGUI implements InventoryHolder {

    public enum PreviewMode {
        DIRECT,
        VERIFY
    }

    private final HollyCore plugin;
    private final Player viewer;
    private final BackupDataManager.DeathRecord death;
    private final PreviewMode mode;
    private final String requestId;
    private final int returnPage;
    private final Inventory inventory;

    public BackupPreviewGUI(HollyCore plugin, Player viewer, BackupDataManager.DeathRecord death,
                            PreviewMode mode, String requestId) {
        this(plugin, viewer, death, mode, requestId, 0);
    }

    public BackupPreviewGUI(HollyCore plugin, Player viewer, BackupDataManager.DeathRecord death,
                            PreviewMode mode, String requestId, int returnPage) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.death = death;
        this.mode = mode;
        this.requestId = requestId;
        this.returnPage = Math.max(0, returnPage);
        this.inventory = Bukkit.createInventory(this, 54, "§8" + death.publicId());
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

        ItemStack[] inv = death.inventory();
        if (inv != null) {
            for (int i = 9; i < 36 && i < inv.length; i++) {
                if (inv[i] != null) {
                    inventory.setItem(i - 9, ItemClientSanitizer.forClient(inv[i]));
                }
            }
            for (int i = 0; i < 9 && i < inv.length; i++) {
                if (inv[i] != null) {
                    inventory.setItem(27 + i, ItemClientSanitizer.forClient(inv[i]));
                }
            }
        }

        ItemStack[] armor = death.armor();
        if (armor != null) {
            if (armor.length > 3 && armor[3] != null) inventory.setItem(45, ItemClientSanitizer.forClient(armor[3]));
            if (armor.length > 2 && armor[2] != null) inventory.setItem(46, ItemClientSanitizer.forClient(armor[2]));
            if (armor.length > 1 && armor[1] != null) inventory.setItem(47, ItemClientSanitizer.forClient(armor[1]));
            if (armor.length > 0 && armor[0] != null) inventory.setItem(48, ItemClientSanitizer.forClient(armor[0]));
        }

        if (inv != null && inv.length > 40 && inv[40] != null) {
            inventory.setItem(49, ItemClientSanitizer.forClient(inv[40]));
        }

        inventory.setItem(50, createActionItem(Material.CHEST, "§aPrzywróć statystyki",
            List.of(
                "§7Przywraca ranking ELO",
                "§7oraz usuwa 1 śmierć.",
                "",
                "§eKliknij, aby przywrócić statystyki"
            ), "restore_stats"));

        inventory.setItem(51, createActionItem(Material.LIME_DYE, "§aPrzywróć EQ",
            List.of(
                "§7Nadaje backup do odbioru",
                "§7przez /odbierzbackup.",
                "",
                "§eKliknij, aby nadać backup"
            ), "restore_eq"));

        if (requestId != null) {
            inventory.setItem(52, createActionItem(Material.BARRIER, "§cOdrzuć zgłoszenie",
                List.of(
                    "§7Usuwa prośbę z kolejki",
                    "",
                    "§cKliknij, aby odrzucić"
                ), "deny"));
        }

        inventory.setItem(53, createActionItem(Material.ARROW, "§ePowrót",
            List.of("§7Kliknij, aby wrócić"), "back"));
    }

    private ItemStack createActionItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        NBTUtils.setString(item, "action", action);
        if (requestId != null) {
            NBTUtils.setString(item, "backup_id", requestId);
        }
        NBTUtils.setString(item, "death_id", death.id());
        return item;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public BackupDataManager.DeathRecord getDeath() {
        return death;
    }

    public PreviewMode getMode() {
        return mode;
    }

    public String getRequestId() {
        return requestId;
    }

    public int getReturnPage() {
        return returnPage;
    }
}
