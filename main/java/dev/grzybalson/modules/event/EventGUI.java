package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EventGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final EventManager manager;
    private Inventory inventory;

    public EventGUI(HollyCore plugin, Player player, EventManager manager) {
        this.plugin = plugin;
        this.player = player;
        this.manager = manager;
    }

    public void open() {
        YamlConfiguration config = manager.getConfig();
        int rows = Math.max(1, Math.min(6, config.getInt("rows", 5)));
        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(config.getString("title", "&8Dungeon")));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        setActionItem("public-parties", "public", 19);
        setActionItem("create-party", "create", 21);
        setActionItem("join-code", "join", 23);
        setActionItem("boss-rewards", "rewards", 25);

        player.openInventory(inventory);
    }

    public void handleClick(int rawSlot) {
        ItemStack clicked = inventory.getItem(rawSlot);
        String action = NBTUtils.getString(clicked, "event_action");
        if (action == null || action.isBlank()) {
            return;
        }
        switch (action) {
            case "public" -> manager.openPublic(player);
            case "create" -> manager.openCreate(player);
            case "join" -> manager.openJoinAnvil(player);
            case "rewards" -> manager.openBossRewards(player);
            default -> {
            }
        }
    }

    private void setActionItem(String key, String action, int fallbackSlot) {
        YamlConfiguration config = manager.getConfig();
        String path = "items." + key + ".";
        int slot = config.getInt(path + "slot", fallbackSlot);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        ItemStack item = createItem(path, key);
        NBTUtils.setString(item, "event_action", action);
        inventory.setItem(slot, item);
    }

    private ItemStack createItem(String path, String key) {
        YamlConfiguration config = manager.getConfig();
        String defaultMaterial = "PAPER";
        String defaultName = "&eEvent";
        List<String> defaultLore = List.of();
        if ("boss-rewards".equals(key)) {
            defaultMaterial = "CHEST";
            defaultName = "&6&lNAGRODY BOSSOW";
            defaultLore = List.of(
                "",
                "&8× &fZobacz itemy z bossow",
                "&8× &fdla trybu darmowego i premium.",
                "",
                "&8» &eKliknij, aby otworzyc!"
            );
        }
        Material material = Material.matchMaterial(config.getString(path + "material", defaultMaterial));
        if (material == null) {
            material = Material.matchMaterial(defaultMaterial);
            if (material == null) {
                material = Material.PAPER;
            }
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(ColorUtils.colorizeComponent(config.getString(path + "name", defaultName))));
            List<Component> lore = new ArrayList<>();
            List<String> loreLines = config.getStringList(path + "lore");
            if (loreLines.isEmpty()) {
                loreLines = defaultLore;
            }
            for (String line : loreLines) {
                lore.add(noItalic(ColorUtils.colorizeComponent(line)));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
