package dev.grzybalson.utils;

import dev.grzybalson.config.configs.WalletConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WalletItemFactory {

    private WalletItemFactory() {
    }

    public static ItemStack create(WalletConfig.DisplayItem config, Map<String, String> placeholders) {
        Material material;
        try {
            material = Material.valueOf(config.getMaterial().toUpperCase());
        } catch (IllegalArgumentException exception) {
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material, Math.max(1, config.getAmount()));
        if (material == Material.PLAYER_HEAD && config.getHeadValue() != null && !config.getHeadValue().isBlank()) {
            item = HeadUtils.setHeadTextureLikeShop(item, apply(config.getHeadValue(), placeholders));
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        WalletConfig.ItemMetaData metaData = config.getMeta();
        meta.displayName(ColorUtils.colorizeComponent(apply(metaData.getDisplay(), placeholders)).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : metaData.getLore()) {
            String applied = apply(line, placeholders);
            if (ColorUtils.isBlankLoreLine(applied)) {
                lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
                continue;
            }
            lore.addAll(ColorUtils.colorizeLore(applied));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        if (metaData.getCustomModelData() > 0) {
            meta.setCustomModelData(metaData.getCustomModelData());
        }
        meta.setUnbreakable(metaData.isUnbreakable());

        for (String flagName : metaData.getItemFlags()) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    public static String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
