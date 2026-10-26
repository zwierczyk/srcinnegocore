package dev.grzybalson.menus;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.StaticMenuGUI;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class StaticMenuManager {

    private static final String REWARD_MENU_TITLE = "&x&9&7&C&F&F&5&lɴ&x&8&E&C&C&F&6&lᴀ&x&8&5&C&9&F&8&lɢ&x&7&C&C&6&F&9&lʀ&x&7&3&C&4&F&B&lᴏ&x&6&A&C&1&F&C&lᴅ&x&6&1&B&E&F&E&lᴀ &x&6&1&B&E&F&E&lᴅ&x&6&A&C&1&F&C&lɪ&x&7&3&C&4&F&B&lꜱ&x&7&C&C&6&F&9&lᴄ&x&8&5&C&9&F&8&lᴏ&x&8&E&C&C&F&6&lʀ&x&9&7&C&F&F&5&lᴅ";
    private static final String REWARD_INFO_NAME = "&x&9&7&C&F&F&5ᴛ&x&8&D&C&C&F&7ᴡ&x&8&2&C&8&F&8ᴏ&x&7&8&C&5&F&Aᴊ&x&6&D&C&2&F&Cᴀ &x&5&8&B&B&F&Fɴ&x&6&3&B&E&F&Dᴀ&x&6&D&C&2&F&Cɢ&x&7&8&C&5&F&Aʀ&x&8&2&C&8&F&8ᴏ&x&8&D&C&C&F&7ᴅ&x&9&7&C&F&F&5ᴀ";

    private final HollyCore plugin;

    public StaticMenuManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // GUI nagrody jest budowane w kodzie, więc nie ma już pliku guis/nagroda.yml do przeładowania.
    }

    public void openReward(Player player) {
        StaticMenuGUI holder = new StaticMenuGUI("reward");
        Inventory inventory = Bukkit.createInventory(holder, 45, ColorUtils.colorize(REWARD_MENU_TITLE));
        holder.setInventory(inventory);

        setItem(inventory, holder, createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, 1, " ", List.of()), "", 0, 8, 36, 44);
        setItem(inventory, holder, createItem(Material.CYAN_STAINED_GLASS_PANE, 1, " ", List.of()), "", 1, 7, 9, 17, 27, 35, 37, 43);
        setItem(inventory, holder, createItem(Material.WHITE_STAINED_GLASS_PANE, 1, " ", List.of()), "", 2, 6, 18, 26, 38, 42);

        setItem(inventory, holder, createItem(
            Material.DIAMOND_AXE,
            1,
            REWARD_INFO_NAME,
            List.of(
                "&f",
                "&7⬇ Jak otrzymać &7nagrode? ⬇",
                "&f",
                "&8• &fɴᴀɢʀᴏᴅʏ:",
                "",
                "&fʀᴀɴɢᴀ: &x&9&7&C&F&F&5&lᴅ&x&8&A&C&B&F&7&lɪ&x&7&E&C&7&F&9&lꜱ&x&7&1&C&3&F&B&lᴄ&x&6&5&B&F&F&D&lᴏ&x&5&8&B&B&F&F&lʀ&x&6&8&C&0&F&D&lᴅ&x&7&8&C&5&F&A&lᴢ&x&8&7&C&A&F&8&lɪ&x&9&7&C&F&F&5&lᴋ &8(&73dni&8)",
                "",
                "&8• &fWejdź na nasz &eDiscord &8(&adc.hollyservice.pl&8)",
                "&8• &fOdszukaj kategori &e#Nagroda &foraz kanał &a#MSBOX",
                "&8• &fNapisz swój nick i ciesz się z &ddarmowej nagrody!",
                ""
            )
        ), "", 22);

        String state = plugin.getRewardNotificationManager().isReminderEnabled(player.getUniqueId()) ? "&aWŁ" : "&cWYŁ";
        setItem(inventory, holder, createItem(
            Material.BELL,
            1,
            "&bPowiadomienia o nagrodzie: " + state,
            List.of(
                "&7Kliknij, aby przełączyć przypomnienia",
                "&7o darmowej nagrodzie z AutoMessage.",
                "",
                "&8• &fAktualny stan: " + state
            )
        ), "player_command:nagroda toggle", 31);

        player.openInventory(inventory);
    }

    private void setItem(Inventory inventory, StaticMenuGUI holder, ItemStack item, String action, int... slots) {
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item.clone());
                holder.setAction(slot, action);
            }
        }
    }

    private ItemStack createItem(Material material, int amount, String displayName, List<String> rawLore) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtils.colorizeComponent(displayName));

        if (rawLore != null && !rawLore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(ColorUtils.colorizeComponent(line));
            }
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }
}
