package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DungeonUpgradeWaitGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final DungeonSession session;
    private final Player viewer;
    private Inventory inventory;

    public DungeonUpgradeWaitGUI(HollyCore plugin, DungeonSession session, Player viewer) {
        this.plugin = plugin;
        this.session = session;
        this.viewer = viewer;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 27, ColorUtils.colorizeComponent(
            "<gradient:#FFD900:#FF9B00><bold>OCZEKIWANIE...</bold></gradient></shadow>"));
        render();
        viewer.openInventory(inventory);
    }

    public void render() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        int slot = 10;
        Map<UUID, String> labels = session.getUpgradeChoiceLabels();
        for (UUID uuid : session.getPlayers()) {
            if (session.isDeadSpectator(uuid)) {
                continue;
            }
            if (slot > 16) {
                break;
            }
            Player online = Bukkit.getPlayer(uuid);
            String name = online != null ? online.getName() : "Gracz";
            ItemStack head = session.createPlayerHead(uuid, name);
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.colorizeComponent("&f" + name).decoration(TextDecoration.ITALIC, false));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                String label = labels.get(uuid);
                if (label == null || label.isBlank()) {
                    lore.add(ColorUtils.colorizeComponent("<gradient:#FF5C5C:#FF9B00>Jeszcze nie wybrano</gradient></shadow>")
                        .decoration(TextDecoration.ITALIC, false));
                    lore.add(ColorUtils.colorizeComponent("&7Wybierz 2 upgrade'y</shadow>")
                        .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(ColorUtils.colorizeComponent("<gradient:#00FF5A:#9FFF00>Wybral:</gradient></shadow>")
                        .decoration(TextDecoration.ITALIC, false));
                    lore.add(ColorUtils.colorizeComponent(label).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                head.setItemMeta(meta);
            }
            inventory.setItem(slot++, head);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
