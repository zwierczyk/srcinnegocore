package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
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

import java.util.List;
import java.util.UUID;

public class TpaConfirmGUI implements InventoryHolder {

    public static final String ACTION_KEY = "tpa_confirm_action";
    public static final String ACTION_ACCEPT = "accept";
    public static final String ACTION_CANCEL = "cancel";

    private final UUID targetUuid;
    private final String targetName;
    private final Inventory inventory;

    public TpaConfirmGUI(HollyCore plugin, Player target, int kills, int deaths, int ranking, String money) {
        this.targetUuid = target.getUniqueId();
        this.targetName = target.getName();
        this.inventory = plugin.getGuiFactory().createInventory(this, 27, "&#00C1FF&lPotwierdź teleportację");
        setupItems(plugin, target, kills, deaths, ranking, money);
    }

    private void setupItems(HollyCore plugin, Player target, int kills, int deaths, int ranking, String money) {
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        inventory.setItem(11, actionItem(
            Material.LIME_DYE,
            "&#32F55F&lPOTWIERDŹ",
            List.of(
                "&7Wyślij prośbę o teleportację",
                "&7do gracza &#00E0FF" + target.getName() + "&7.",
                "",
                "&a✔ Kliknij, aby wysłać prośbę"
            ),
            ACTION_ACCEPT
        ));

        inventory.setItem(13, targetHead(target, kills, deaths, ranking, money));

        inventory.setItem(15, actionItem(
            Material.RED_DYE,
            "&#FF0000&lANULUJ",
            List.of(
                "&7Zamknij to okno bez",
                "&7wysyłania prośby.",
                "",
                "&c✘ Kliknij, aby anulować"
            ),
            ACTION_CANCEL
        ));
    }

    private ItemStack targetHead(Player target, int kills, int deaths, int ranking, String money) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target);
            meta = skullMeta;
        }
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize("&#00E0FF&lPROFIL: &f" + target.getName()));
            meta.setLore(List.of(
                ColorUtils.colorize("&8&m---------------------"),
                ColorUtils.colorize(" &7Statystyki gracza:"),
                ColorUtils.colorize(" &8● &7Zabójstwa: &#00E0FF" + kills),
                ColorUtils.colorize(" &8● &7Śmierci: &#00E0FF" + deaths),
                ColorUtils.colorize(" &8● &7Ranking: &#00E0FF#" + ranking),
                ColorUtils.colorize(" &8● &7Pieniądze: &#00FF4F" + money + "$"),
                ColorUtils.colorize("&8&m---------------------")
            ));
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, "tpa_target_uuid", targetUuid.toString());
        return item;
    }

    private ItemStack actionItem(Material material, String name, List<String> lore, String action) {
        ItemStack item = createItem(material, name, lore);
        NBTUtils.setString(item, ACTION_KEY, action);
        return item;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            meta.setLore(lore.stream().map(ColorUtils::colorize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }
}
