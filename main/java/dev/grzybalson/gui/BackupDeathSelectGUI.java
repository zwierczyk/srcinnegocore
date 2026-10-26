package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.backup.BackupDataManager;
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
import java.util.UUID;

public class BackupDeathSelectGUI implements InventoryHolder {

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
    private final UUID targetUuid;
    private final String targetName;
    private final List<BackupDataManager.DeathRecord> deaths;
    private final int page;
    private final int maxPage;
    private final Inventory inventory;

    public BackupDeathSelectGUI(HollyCore plugin, Player viewer, UUID targetUuid,
                                String targetName, List<BackupDataManager.DeathRecord> deaths) {
        this(plugin, viewer, targetUuid, targetName, deaths, 0);
    }

    public BackupDeathSelectGUI(HollyCore plugin, Player viewer, UUID targetUuid,
                                String targetName, List<BackupDataManager.DeathRecord> deaths, int page) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.deaths = deaths;
        this.maxPage = Math.max(0, (int) Math.ceil((double) Math.max(1, deaths.size()) / CONTENT_SLOTS.length) - 1);
        this.page = Math.max(0, Math.min(page, maxPage));
        this.inventory = Bukkit.createInventory(this, 54, "§8Backupy: " + targetName + " §7[" + (this.page + 1) + "/" + (maxPage + 1) + "]");
        setupItems();
    }

    private void setupItems() {
        fillBackground();

        int startIndex = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int deathIndex = startIndex + i;
            if (deathIndex >= deaths.size()) {
                break;
            }

            BackupDataManager.DeathRecord death = deaths.get(deathIndex);
            ItemStack item = new ItemStack(Material.SKELETON_SKULL);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§f" + death.publicId());

            List<String> lore = new ArrayList<>();
            lore.add("§8ID: §e" + death.publicId());
            lore.add("§8Data: §f" + FORMATTER.format(death.createdAt()));
            lore.add("§8Zabójca: §f" + (death.killerName() != null ? death.killerName() : "Brak"));
            lore.add("§8Przyczyna: §f" + formatDeathCause(death.deathCause()));
            lore.add("§8Pozycja: §f" + death.location());
            lore.add("");
            lore.add("§eKliknij, aby wybrać backup");
            meta.setLore(lore);
            item.setItemMeta(meta);

            NBTUtils.setString(item, "death_id", death.id());
            NBTUtils.setString(item, "target_uuid", targetUuid.toString());
            NBTUtils.setString(item, "target_name", targetName);
            inventory.setItem(CONTENT_SLOTS[i], item);
        }

        if (page > 0) {
            inventory.setItem(48, createNavItem(Material.ARROW, "§e⬅ Poprzednia strona §7(" + page + "/" + (maxPage + 1) + ")", "page_prev"));
        }

        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.setDisplayName("§6Strona §e" + (page + 1) + " §7/ §e" + (maxPage + 1));
        List<String> pageInfoLore = new ArrayList<>();
        pageInfoLore.add("§7Wszystkich backupów: §f" + deaths.size());
        pageInfoLore.add("§7Pokazywane zgony: §f#" + (startIndex + 1) + " - #" + Math.min(deaths.size(), startIndex + CONTENT_SLOTS.length));
        pageInfoLore.add("");
        pageInfoLore.add("§8Użyj strzałek, aby zmieniać strony");
        pageInfoMeta.setLore(pageInfoLore);
        pageInfo.setItemMeta(pageInfoMeta);
        inventory.setItem(49, pageInfo);

        if (page < maxPage) {
            inventory.setItem(50, createNavItem(Material.ARROW, "§aNastępna strona ➡ §7(" + (page + 2) + "/" + (maxPage + 1) + ")", "page_next"));
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
        meta.setDisplayName("§eŚmierci gracza §f" + targetName);
        meta.setLore(List.of(
            "§7Dostępne backupy: §f" + deaths.size(),
            "§7Maksymalnie przechowywane: §f" + plugin.getConfigManager().getBackupConfig().getMaxDeathsPerPlayer(),
            "§7Strona: §f" + (page + 1) + "§7/§f" + (maxPage + 1)
        ));
        info.setItemMeta(meta);
        inventory.setItem(4, info);
    }

    private String formatDeathCause(String cause) {
        if (cause == null || cause.isEmpty()) {
            return "Nieznana";
        }
        String normalized = cause.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
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

    public int getPage() {
        return page;
    }

    public List<BackupDataManager.DeathRecord> getDeaths() {
        return deaths;
    }
}
