package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
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

/** Wybor puli nagród (darmowe/premium) i bossa 1–3. */
public class DungeonBossRewardsMenuGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final Player player;
    private final EventManager manager;
    private Inventory inventory;
    private boolean premium;

    public DungeonBossRewardsMenuGUI(HollyCore plugin, Player player, EventManager manager) {
        this(plugin, player, manager, false);
    }

    public DungeonBossRewardsMenuGUI(HollyCore plugin, Player player, EventManager manager, boolean premium) {
        this.plugin = plugin;
        this.player = player;
        this.manager = manager;
        this.premium = premium;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 45, ColorUtils.colorizeComponent("&8Nagrody bossow"));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);
        render();
        player.openInventory(inventory);
    }

    public void handleClick(int rawSlot) {
        ItemStack clicked = inventory.getItem(rawSlot);
        String action = NBTUtils.getString(clicked, "event_action");
        if (action == null || action.isBlank()) {
            return;
        }
        switch (action) {
            case "back" -> manager.openMain(player);
            case "mode" -> {
                premium = !premium;
                render();
            }
            case "boss1" -> openBoss(1);
            case "boss2" -> openBoss(2);
            case "boss3" -> openBoss(3);
            default -> {
            }
        }
    }

    private void openBoss(int level) {
        boolean canEdit = player.hasPermission("holly.adungeon.admin");
        new DungeonBossRewardEditGUI(plugin, player, manager, level, premium, canEdit).open();
    }

    private void render() {
        inventory.setItem(21, button(
            premium ? Material.NETHER_STAR : Material.EMERALD,
            "&e&lTYP NAGROD",
            List.of(
                "",
                "&8× &fAktualnie: " + (premium ? "&dPremium" : "&aDarmowe"),
                "",
                "&8× &fDarmowe — party bez ticketow",
                "&8× &fPremium — party z ticketami",
                "",
                "&8» &eKliknij, aby zmienic!"
            ),
            "mode"
        ));

        inventory.setItem(29, bossButton(1, Material.IRON_SWORD));
        inventory.setItem(31, bossButton(2, Material.DIAMOND_SWORD));
        inventory.setItem(33, bossButton(3, Material.NETHERITE_SWORD));

        inventory.setItem(36, button(
            Material.ARROW,
            "&c&lPOWROT",
            List.of("", "&8» &eKliknij, aby wrocic!"),
            "back"
        ));
    }

    private ItemStack bossButton(int level, Material material) {
        int count = 0;
        if (manager.getDungeonSessions() != null) {
            count = manager.getDungeonSessions().getBossRewards(level, premium).size();
        }
        return button(
            material,
            (premium ? "&d&l" : "&a&l") + "BOSS " + level,
            List.of(
                "",
                "&8× &fPula: " + (premium ? "&dPremium" : "&aDarmowe"),
                "&8× &fPrzedmiotow w puli: &e" + count,
                "",
                premium
                    ? "&8× &f1 ticket = 1 item, 2 = 2..."
                    : "&8× &fDarmowa gra = 1 item z tej puli",
                "",
                player.hasPermission("holly.adungeon.admin")
                    ? "&8» &eKliknij, aby edytowac!"
                    : "&8» &eKliknij, aby podgladac!"
            ),
            "boss" + level
        );
    }

    private ItemStack button(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
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
        NBTUtils.setString(item, "event_action", action);
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
