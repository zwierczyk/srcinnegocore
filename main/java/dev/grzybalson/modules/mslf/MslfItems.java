package dev.grzybalson.modules.mslf;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class MslfItems {

    public static final String PDC_KEY = "mslf_item";

    private final HollyCore plugin;
    private final MslfSettings settings;
    private final NamespacedKey typeKey;

    public MslfItems(HollyCore plugin, MslfSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.typeKey = new NamespacedKey(plugin, PDC_KEY);
    }

    public NamespacedKey typeKey() {
        return typeKey;
    }

    public ItemStack create(MslfItemType type) {
        if (type == null) {
            return null;
        }
        MslfSettings.ItemDef def = settings.item(type);
        if (def == null || !def.enabled()) {
            return null;
        }
        ItemStack stack = null;
        if (def.serialized() != null && !def.serialized().isBlank()) {
            stack = dev.grzybalson.utils.ItemSerializer.deserializeItem(def.serialized());
        }
        if (stack == null || stack.getType() == Material.AIR) {
            stack = new ItemStack(def.material() != null ? def.material() : type.defaultMaterial());
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        applyAppearance(meta, def, type);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
        if (type == MslfItemType.KONICZYNKA) {
            var szczesciarz = plugin.getMslfSzczesciarzManager();
            if (szczesciarz != null) {
                meta.getPersistentDataContainer().set(
                    szczesciarz.getZmiankiManager().itemKey(),
                    PersistentDataType.BYTE,
                    (byte) 1
                );
            }
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        if (type == MslfItemType.KNOCK) {
            meta.addEnchant(Enchantment.KNOCKBACK, 4, true);
        }
        if (type == MslfItemType.MIECZ_PIEKIELNY) {
            meta.addEnchant(Enchantment.DAMAGE_ALL, 5, true);
            meta.addEnchant(Enchantment.DURABILITY, 5, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
        }
        stack.setItemMeta(meta);
        stack.setAmount(1);
        return stack;
    }

    private static void applyAppearance(ItemMeta meta, MslfSettings.ItemDef def, MslfItemType type) {
        if (def.name() != null && !def.name().isBlank()) {
            meta.displayName(ColorUtils.colorizeComponent(def.name()).decoration(TextDecoration.ITALIC, false));
        }
        List<String> defLore = def.lore();
        if (defLore != null && !defLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(defLore.size());
            for (String line : defLore) {
                if (line == null || line.isBlank()) {
                    lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
                }
            }
            meta.lore(lore);
        }
        int cmd = def.customModelData() > 0 ? def.customModelData() : type.defaultCmd();
        if (cmd > 0) {
            meta.setCustomModelData(cmd);
        }
    }

    public MslfItemType typeOf(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return MslfItemType.byId(id);
    }

    public boolean is(ItemStack stack, MslfItemType type) {
        return type != null && typeOf(stack) == type;
    }
}
