package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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

public class DungeonCreateGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final EventManager manager;
    private Inventory inventory;
    private int selectedSize = 1;
    private int selectedTickets = 1;
    private Boolean publicParty;

    public DungeonCreateGUI(HollyCore plugin, Player player, EventManager manager) {
        this.plugin = plugin;
        this.player = player;
        this.manager = manager;
    }

    public void open() {
        YamlConfiguration config = manager.getConfig();
        int rows = Math.max(1, Math.min(6, config.getInt("create.rows", config.getInt("rows", 5))));
        if (rows < 5) {
            rows = 5;
        }
        inventory = Bukkit.createInventory(this, rows * 9, ColorUtils.colorizeComponent(config.getString("create-title", "&8Stworz party")));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);
        render();
        player.openInventory(inventory);
    }

    public void handleClick(int rawSlot) {
        ConfigurationSection create = manager.getConfig().getConfigurationSection("create");
        if (create == null) {
            return;
        }

        List<Integer> sizeSlots = create.getIntegerList("size-slots");
        List<Integer> ticketSlots = create.getIntegerList("ticket-slots");
        int privateSlot = create.getInt("visibility-private-slot", 29);
        int publicSlot = create.getInt("visibility-public-slot", 33);
        int freeSlot = create.getInt("free-slot", 37);
        int modeSlot = create.getInt("mode-slot", 21);
        int confirmSlot = create.getInt("confirm-slot", 23);
        int backSlot = create.getInt("back-slot", 36);

        int sizeIndex = sizeSlots.indexOf(rawSlot);
        if (sizeIndex >= 0) {
            selectedSize = sizeIndex + 1;
            render();
            return;
        }

        int ticketIndex = ticketSlots.indexOf(rawSlot);
        if (ticketIndex >= 0) {
            selectedTickets = ticketIndex + 1;
            render();
            return;
        }
        if (rawSlot == freeSlot) {
            selectedTickets = 0;
            render();
            return;
        }
        if (rawSlot == modeSlot) {
            if (selectedTickets <= 0) {
                selectedTickets = 1;
            } else {
                selectedTickets = 0;
            }
            render();
            return;
        }

        if (rawSlot == privateSlot) {
            publicParty = false;
            render();
            return;
        }
        if (rawSlot == publicSlot) {
            publicParty = true;
            render();
            return;
        }
        if (rawSlot == backSlot) {
            manager.openMain(player);
            return;
        }
        if (rawSlot == confirmSlot) {
            if (publicParty == null) {
                manager.send(player, "create-need-select");
                return;
            }
            manager.createParty(player, selectedSize, publicParty, selectedTickets);
        }
    }

    private void render() {
        YamlConfiguration config = manager.getConfig();
        ConfigurationSection create = config.getConfigurationSection("create");
        if (create == null) {
            return;
        }

        List<Integer> sizeSlots = create.getIntegerList("size-slots");
        for (int i = 0; i < sizeSlots.size(); i++) {
            int size = i + 1;
            boolean selected = selectedSize == size;
            Material material = Material.matchMaterial(create.getString(selected ? "size-selected-material" : "size-unselected-material", selected ? "LIME_DYE" : "GRAY_DYE"));
            if (material == null) {
                material = selected ? Material.LIME_DYE : Material.GRAY_DYE;
            }
            ItemStack item = named(
                material,
                create.getString("size-name", "&a&l{SIZE} OSOB").replace("{SIZE}", String.valueOf(size)),
                replaceList(create.getStringList("size-lore"), size, selectedTickets, publicParty)
            );
            NBTUtils.setString(item, "event_action", "size");
            inventory.setItem(sizeSlots.get(i), item);
        }

        boolean privateSelected = publicParty != null && !publicParty;
        boolean publicSelected = publicParty != null && publicParty;
        inventory.setItem(create.getInt("visibility-private-slot", 29), named(
            match(create.getString("private-material", "IRON_DOOR"), Material.IRON_DOOR),
            create.getString("private-name", "&7&lPRYWATNE"),
            replaceStatus(create.getStringList("private-lore"), privateSelected)
        ));
        inventory.setItem(create.getInt("visibility-public-slot", 33), named(
            match(create.getString("public-material", "OAK_DOOR"), Material.OAK_DOOR),
            create.getString("public-name", "&b&lPUBLICZNE"),
            replaceStatus(create.getStringList("public-lore"), publicSelected)
        ));

        boolean freeSelected = selectedTickets == 0;
        inventory.setItem(create.getInt("mode-slot", 21), named(
            match(
                create.getString(freeSelected ? "mode-free-material" : "mode-premium-material",
                    freeSelected ? "EMERALD" : "NETHER_STAR"),
                freeSelected ? Material.EMERALD : Material.NETHER_STAR
            ),
            create.getString("mode-name", "&e&lTYP ROZGRYWKI"),
            replaceList(create.getStringList(freeSelected ? "mode-free-lore" : "mode-premium-lore"), selectedSize, selectedTickets, publicParty)
        ));

        inventory.setItem(create.getInt("free-slot", 37), named(
            match(
                create.getString(freeSelected ? "free-selected-material" : "free-unselected-material",
                    freeSelected ? "LIME_DYE" : "GRAY_DYE"),
                freeSelected ? Material.LIME_DYE : Material.GRAY_DYE
            ),
            create.getString("free-name", "&a&lDARMOWE"),
            replaceList(create.getStringList("free-lore"), selectedSize, 0, publicParty)
        ));

        List<Integer> ticketSlots = create.getIntegerList("ticket-slots");
        for (int i = 0; i < ticketSlots.size(); i++) {
            int tickets = i + 1;
            boolean selected = selectedTickets == tickets;
            Material material = match(
                create.getString(selected ? "ticket-selected-material" : "ticket-unselected-material", selected ? "MAGENTA_DYE" : "GRAY_DYE"),
                selected ? Material.MAGENTA_DYE : Material.GRAY_DYE
            );
            ItemStack item = named(
                material,
                create.getString("ticket-name", "&d&l{TICKETS} TICKETOW").replace("{TICKETS}", String.valueOf(tickets)),
                replaceList(create.getStringList("ticket-lore"), selectedSize, tickets, publicParty)
            );
            inventory.setItem(ticketSlots.get(i), item);
        }

        inventory.setItem(create.getInt("confirm-slot", 23), named(
            match(create.getString("confirm-material", "EMERALD"), Material.EMERALD),
            create.getString("confirm-name", "&a&lPOTWIERDZ PARTY"),
            replaceList(create.getStringList("confirm-lore"), selectedSize, selectedTickets, publicParty)
        ));
        inventory.setItem(create.getInt("back-slot", 36), named(
            match(create.getString("back-material", "ARROW"), Material.ARROW),
            create.getString("back-name", "&c&lPOWROT"),
            create.getStringList("back-lore")
        ));
    }

    private List<String> replaceList(List<String> lore, int size, int tickets, Boolean visibility) {
        String visibilityText = visibility == null ? "&cNie wybrano" : (visibility ? "Publiczne" : "Prywatne");
        String ticketText = tickets > 0 ? String.valueOf(tickets) : "Darmowe";
        String modeText = tickets > 0 ? "Premium" : "Darmowe";
        int moneyMult = tickets <= 0 ? 1 : tickets + 2;
        int itemCount = tickets <= 0 ? 1 : tickets;
        List<String> out = new ArrayList<>();
        for (String line : lore) {
            out.add(line
                .replace("{SIZE}", String.valueOf(size))
                .replace("{TICKETS}", ticketText)
                .replace("{MODE}", modeText)
                .replace("{MONEY_MULT}", "x" + moneyMult)
                .replace("{ITEM_COUNT}", String.valueOf(itemCount))
                .replace("{VISIBILITY}", visibilityText));
        }
        return out;
    }

    private List<String> replaceStatus(List<String> lore, boolean selected) {
        String status = selected
            ? manager.getConfig().getString("create.selected", "&aWybrane")
            : manager.getConfig().getString("create.unselected", "&cNie wybrano");
        List<String> out = new ArrayList<>();
        for (String line : lore) {
            out.add(line.replace("{STATUS}", status));
        }
        return out;
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
