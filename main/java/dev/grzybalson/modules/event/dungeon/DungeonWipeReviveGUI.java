package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Oferta odrodzenia calej drużyny po wipe (30s / solo cena VPLN). */
public final class DungeonWipeReviveGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final DungeonSession session;
    private final Player player;
    private Inventory inventory;

    public DungeonWipeReviveGUI(HollyCore plugin, DungeonSession session, Player player) {
        this.plugin = plugin;
        this.session = session;
        this.player = player;
    }

    public void open() {
        int cost = session.wipeReviveCost();
        int team = session.getPlayers().size();
        int left = session.wipeReviveSecondsLeft();
        inventory = Bukkit.createInventory(this, 27, ColorUtils.colorizeComponent("&8Odrodzenie drużyny"));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        ItemStack accept = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta acceptMeta = accept.getItemMeta();
        if (acceptMeta != null) {
            acceptMeta.displayName(ColorUtils.colorizeComponent("&a&lODRODZ CAŁĄ DRUŻYNĘ")
                .decoration(TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(ColorUtils.colorizeComponent("").decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8× &fKoszt: &e" + cost + " VPLN")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8× &f(cena jak solo)")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8× &fOdrodzi: &a" + team + " &fosób")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8× &fCzas: &e" + left + "s")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("").decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&8» &eKliknij, aby zapłacić!")
                .decoration(TextDecoration.ITALIC, false));
            acceptMeta.lore(lore);
            acceptMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            accept.setItemMeta(acceptMeta);
        }
        inventory.setItem(11, accept);

        ItemStack info = new ItemStack(Material.CLOCK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(ColorUtils.colorizeComponent("&e&lWIPE — DECYZJA")
                .decoration(TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(ColorUtils.colorizeComponent("").decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&7Cała drużyna padła.")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&7Jeden zakup odradza &fwszystkich&7.")
                .decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&7Po &e" + left + "s &7bez zakupu = przegrana.")
                .decoration(TextDecoration.ITALIC, false));
            infoMeta.lore(lore);
            infoMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(13, info);

        ItemStack decline = new ItemStack(Material.BARRIER);
        ItemMeta declineMeta = decline.getItemMeta();
        if (declineMeta != null) {
            declineMeta.displayName(ColorUtils.colorizeComponent("&c&lANULUJ / CZEKAJ")
                .decoration(TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(ColorUtils.colorizeComponent("").decoration(TextDecoration.ITALIC, false));
            lore.add(ColorUtils.colorizeComponent("&7Zamknij GUI — inni mogą zapłacić.")
                .decoration(TextDecoration.ITALIC, false));
            declineMeta.lore(lore);
            declineMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            decline.setItemMeta(declineMeta);
        }
        inventory.setItem(15, decline);

        player.openInventory(inventory);
    }

    public void handleClick(int rawSlot) {
        if (rawSlot == 11) {
            session.acceptWipeRevive(player);
            return;
        }
        if (rawSlot == 15) {
            session.declineWipeRevive(player);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
