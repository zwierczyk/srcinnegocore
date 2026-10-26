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

public final class DungeonWave12GUI implements InventoryHolder {

    private final HollyCore plugin;
    private final DungeonSession session;
    private final Player player;
    private Inventory inventory;

    public DungeonWave12GUI(HollyCore plugin, DungeonSession session, Player player) {
        this.plugin = plugin;
        this.session = session;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 27, ColorUtils.colorizeComponent(
            "<gradient:#FFD900:#C800FF><bold>GLOSOWANIE FALI 12</bold></gradient></shadow>"));
        render();
        player.openInventory(inventory);
    }

    public void render() {
        if (inventory == null) {
            return;
        }
        int totemVotes = session.wave12TotemVotes();
        int reviveVotes = session.wave12ReviveVotes();
        Boolean my = session.getWave12Vote(player.getUniqueId());
        inventory.setItem(11, item(Material.TOTEM_OF_UNDYING,
            "<gradient:#FFD900:#00FF5A><bold>TOTEM</bold></gradient></shadow>",
            List.of(
                "&8▪ Kazdy zywy dostaje totem",
                "&7Nieumierania (1 szt).",
                "",
                "&fGlosy: &#FFD900" + totemVotes,
                my != null && my ? "&a✔ Twoj glos" : "&7Kliknij aby zaglosowac"
            )));
        inventory.setItem(13, item(Material.PAPER,
            "<gradient:#FFFFFF:#B5CFD4><bold>STATUS</bold></gradient></shadow>",
            List.of(
                "&fTotem: &#FFD900" + totemVotes,
                "&fOdrodzenie: &#00C1FF" + reviveVotes,
                "",
                "&7Glosuja tylko zywi.",
                "&7Po wszystkich glosach — wynik."
            )));
        inventory.setItem(15, item(Material.END_CRYSTAL,
            "<gradient:#00C1FF:#00FFC1><bold>ODRODZENIE WSZYSTKICH</bold></gradient></shadow>",
            List.of(
                "&8▪ Ozywia cala druzyne",
                "&7i TP na fale 5 poziomu 2.",
                "",
                "&fGlosy: &#00C1FF" + reviveVotes,
                my != null && !my ? "&a✔ Twoj glos" : "&7Kliknij aby zaglosowac"
            )));
    }

    public void handleClick(int slot) {
        if (slot == 11) {
            session.voteWave12(player, true);
            render();
        } else if (slot == 15) {
            session.voteWave12(player, false);
            render();
        }
    }

    private ItemStack item(Material material, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent(name).decoration(TextDecoration.ITALIC, false));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
