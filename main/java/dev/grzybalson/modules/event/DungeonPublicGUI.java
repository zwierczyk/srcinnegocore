package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DungeonPublicGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final EventManager manager;
    private Inventory inventory;

    public DungeonPublicGUI(HollyCore plugin, Player player, EventManager manager) {
        this.plugin = plugin;
        this.player = player;
        this.manager = manager;
    }

    public void open() {
        YamlConfiguration config = manager.getConfig();
        int rows = Math.max(1, Math.min(6, config.getInt("public.rows", 5)));
        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(config.getString("public-title", "&8Publiczne party")));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);
        render();
        player.openInventory(inventory);
    }

    public void handleClick(int rawSlot) {
        ConfigurationSection section = manager.getConfig().getConfigurationSection("public");
        if (section == null) {
            return;
        }
        if (rawSlot == section.getInt("back-slot", 36)) {
            manager.openMain(player);
            return;
        }
        ItemStack clicked = inventory.getItem(rawSlot);
        String code = NBTUtils.getString(clicked, "dungeon_party_code");
        if (code == null || code.isBlank()) {
            return;
        }
        manager.joinPartyByCode(player, code);
    }

    private void render() {
        ConfigurationSection section = manager.getConfig().getConfigurationSection("public");
        if (section == null) {
            return;
        }
        Collection<DungeonParty> parties = manager.getPublicParties();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int index = 0;
        for (DungeonParty party : parties) {
            if (index >= slots.length) {
                break;
            }
            inventory.setItem(slots[index++], partyHead(party, section));
        }
        if (index == 0) {
            inventory.setItem(22, named(
                match(section.getString("empty-material", "BARRIER"), Material.BARRIER),
                section.getString("empty-name", "&cBrak publicznych party"),
                section.getStringList("empty-lore")
            ));
        }
        inventory.setItem(section.getInt("back-slot", 36), named(
            match(section.getString("back-material", "ARROW"), Material.ARROW),
            section.getString("back-name", "&c&lPOWROT"),
            section.getStringList("back-lore")
        ));
    }

    private ItemStack partyHead(DungeonParty party, ConfigurationSection section) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(party.getLeaderId());
            meta.setOwningPlayer(offline);
            meta.displayName(noItalic(ColorUtils.colorizeComponent(
                section.getString("party-name", "&b&lParty &f{LEADER}").replace("{LEADER}", party.getLeaderName())
            )));
            List<Component> lore = new ArrayList<>();
            for (String line : section.getStringList("party-lore")) {
                lore.add(noItalic(ColorUtils.colorizeComponent(line
                    .replace("{LEADER}", party.getLeaderName())
                    .replace("{CODE}", party.getCode())
                    .replace("{CURRENT}", String.valueOf(party.getMemberCount()))
                    .replace("{SIZE}", String.valueOf(party.getMaxSize()))
                    .replace("{TICKETS}", party.getTicketLabel())
                    .replace("{MODE}", party.getModeLabel())
                )));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        NBTUtils.setString(item, "dungeon_party_code", party.getCode());
        return item;
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(ColorUtils.colorizeComponent(name)));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(noItalic(ColorUtils.colorizeComponent(line)));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material match(String name, Material fallback) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        return material == null ? fallback : material;
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
