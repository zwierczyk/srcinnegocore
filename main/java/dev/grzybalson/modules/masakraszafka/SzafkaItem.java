package dev.grzybalson.modules.masakraszafka;

import dev.grzybalson.utils.HeadUtils;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.List;
import java.util.Locale;

record SzafkaItem(Kind kind, String id, String name, String rarity, List<String> properties, Material icon, Color color,
                  String texture, int customModelData, long defaultDuration, String symbol, SzafkaPerks perks) {

    enum Kind {
        COSTUME("kostium", "costumes"),
        TALISMAN("talizman", "talismans");

        final String label;
        final String section;

        Kind(String label, String section) {
            this.label = label;
            this.section = section;
        }

        static Kind parse(String raw) {
            return switch (raw == null ? "" : raw.toLowerCase(Locale.ROOT)) {
                case "kostium", "kostiumy", "costume", "costumes" -> COSTUME;
                case "talizman", "talizmany", "talisman", "talismans" -> TALISMAN;
                default -> null;
            };
        }
    }

    ItemStack createIcon(Material material) {
        ItemStack item = new ItemStack(material);
        if (material == Material.PLAYER_HEAD && texture != null) HeadUtils.setHeadTexture(item, texture);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather && color != null) leather.setColor(color);
        if (customModelData > 0) meta.setCustomModelData(customModelData);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE,
            ItemFlag.HIDE_ARMOR_TRIM, ItemFlag.HIDE_ITEM_SPECIFICS);
        item.setItemMeta(meta);
        return item;
    }
}
