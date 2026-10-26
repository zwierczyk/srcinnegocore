package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.zonebox.ZoneBoxManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DrwalCommand implements CommandExecutor, Listener {

    private static final int GUI_ROWS = 5;
    private static final String GUI_TITLE = "&8Drwal";

    private final HollyCore plugin;
    private final ZoneBoxManager zoneBoxManager;

    public DrwalCommand(HollyCore plugin, ZoneBoxManager zoneBoxManager) {
        this.plugin = plugin;
        this.zoneBoxManager = zoneBoxManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.drwal")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.drwal"));
            return true;
        }

        openGui(player);
        return true;
    }

    private void openGui(Player player) {
        Map<Material, Double> prices = zoneBoxManager.getBlockEconomyPrices();
        if (prices.isEmpty()) {
            player.sendMessage(ColorUtils.error("Brak blokow w blockseconomy."));
            return;
        }

        Inventory inventory = plugin.getGuiFactory().createWithDefaultTemplate(
            new DrwalHolder(),
            plugin.getGuiFactory().getInventorySize(GUI_ROWS),
            GUI_TITLE
        );

        int[] slots = plugin.getGuiFactory().getContentSlots(inventory);
        int[] index = {0};
        prices.entrySet().stream()
            .sorted(Comparator
                .comparingInt((Map.Entry<Material, Double> entry) -> woodSortRank(entry.getKey()))
                .thenComparing(entry -> entry.getKey().name()))
            .forEach(entry -> {
                if (index[0] >= slots.length) return;
                inventory.setItem(slots[index[0]++], createBlockItem(entry.getKey(), entry.getValue()));
            });

        player.openInventory(inventory);
    }

    private ItemStack createBlockItem(Material material, double price) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&a" + displayName(material)).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                ColorUtils.colorizeComponent("&8» &7Cena: &#92FF74" + MoneyAmountUtil.formatCompact(price) + "$").decoration(TextDecoration.ITALIC, false),
                ColorUtils.colorizeComponent("&8» &7Na minutę: &#92FF74" + MoneyAmountUtil.formatCompact(price * 120.0D) + "$").decoration(TextDecoration.ITALIC, false),
                ColorUtils.colorizeComponent("").decoration(TextDecoration.ITALIC, false),
                ColorUtils.colorizeComponent("&8» &7Tyle dostajesz za wykopanie").decoration(TextDecoration.ITALIC, false),
                ColorUtils.colorizeComponent("&7tego bloku na tym trybie.").decoration(TextDecoration.ITALIC, false)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static int woodSortRank(Material material) {
        String name = material.name();
        if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) return 0;
        if (name.endsWith("_PLANKS")) return 1;
        if (name.endsWith("_LEAVES")) return 2;
        return 3;
    }

    private static String displayName(Material material) {
        String name = material.name();
        boolean stripped = name.startsWith("STRIPPED_");
        String normalized = stripped ? name.substring("STRIPPED_".length()) : name;

        if (normalized.endsWith("_LOG") || normalized.endsWith("_WOOD") || normalized.endsWith("_STEM") || normalized.endsWith("_HYPHAE")) {
            return (stripped ? "Okorowane drewno " : "Drewno ") + woodType(normalized);
        }
        if (normalized.endsWith("_PLANKS")) {
            return "Deski " + woodType(normalized);
        }
        if (normalized.endsWith("_LEAVES")) {
            return "Liscie " + woodType(normalized);
        }

        return titleCase(name);
    }

    private static String woodType(String materialName) {
        String prefix = materialName
            .replace("_LOG", "")
            .replace("_WOOD", "")
            .replace("_STEM", "")
            .replace("_HYPHAE", "")
            .replace("_PLANKS", "")
            .replace("_LEAVES", "");

        return switch (prefix) {
            case "OAK" -> "debowe";
            case "SPRUCE" -> "swierkowe";
            case "BIRCH" -> "brzozowe";
            case "JUNGLE" -> "tropikalne";
            case "ACACIA" -> "akacjowe";
            case "DARK_OAK" -> "ciemno-debowe";
            case "MANGROVE" -> "namorzynowe";
            case "CHERRY" -> "wisniowe";
            case "PALE_OAK" -> "blade debowe";
            case "CRIMSON" -> "szkarletne";
            case "WARPED" -> "spaczone";
            case "BAMBOO" -> "bambusowe";
            default -> titleCase(prefix);
        };
    }

    private static String titleCase(String materialName) {
        String[] parts = materialName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DrwalHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DrwalHolder) {
            event.setCancelled(true);
        }
    }

    private static final class DrwalHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(null, 9);
        }
    }
}
