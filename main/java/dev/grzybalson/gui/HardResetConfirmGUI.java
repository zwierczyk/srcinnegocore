package dev.grzybalson.gui;

import dev.grzybalson.HollyCore;
import dev.grzybalson.core.HardResetService;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class HardResetConfirmGUI implements InventoryHolder {

    private final HollyCore plugin;
    private final HardResetService service;
    private final Player player;
    private final String prefix;
    private final int step; // 1 = first confirm, 2 = final confirm
    private Inventory inventory;

    public HardResetConfirmGUI(HollyCore plugin, HardResetService service, Player player, String prefix, int step) {
        this.plugin = plugin;
        this.service = service;
        this.player = player;
        this.prefix = prefix;
        this.step = Math.max(1, Math.min(2, step));
    }

    public void open() {
        String title = step == 1
            ? ColorUtils.colorize("&8Hardreset #1 — " + prefix)
            : ColorUtils.colorize("&4Hardreset #2 — " + prefix);
        inventory = Bukkit.createInventory(this, 27, title);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(ColorUtils.colorizeComponent("&ePrefix: &f" + prefix));
        boolean current = service.isCurrentPrefix(prefix);
        infoMeta.lore(List.of(
            ColorUtils.colorizeComponent("&7Usunie TYLKO kolekcje w bazie &f" + prefix),
            ColorUtils.colorizeComponent("&7Nie rusza innych prefixow / baz."),
            ColorUtils.colorizeComponent(current
                ? "&cTo aktualny prefix tego serwera — wyczysci tez online EQ."
                : "&aTo obcy prefix — tylko wipe Mongo tej bazy."),
            ColorUtils.colorizeComponent("&8Krok: &f" + step + "/2")
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(13, info);

        ItemStack confirm = new ItemStack(step == 1 ? Material.ORANGE_CONCRETE : Material.RED_CONCRETE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(ColorUtils.colorizeComponent(step == 1
            ? "&6Potwierdzam — dalej (#1)"
            : "&cUSUN WSZYSTKO (#2)"));
        confirmMeta.lore(List.of(
            ColorUtils.colorizeComponent(step == 1
                ? "&7Kliknij, aby przejsc do ostatecznego potwierdzenia."
                : "&4NIEODWRACALNE. Kliknij, aby usunac dane prefixu.")
        ));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(ColorUtils.colorizeComponent("&aAnuluj"));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(15, cancel);

        player.openInventory(inventory);
    }

    public void handleClick(int slot) {
        if (slot == 15) {
            player.closeInventory();
            player.sendMessage(ColorUtils.colorizeComponent("&#4EFA76✔ &8• &#4EFA76Hardreset anulowany."));
            return;
        }
        if (slot != 11) {
            return;
        }

        if (step == 1) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                new HardResetConfirmGUI(plugin, service, player, prefix, 2).open()
            );
            return;
        }

        player.closeInventory();
        if (service.isRunning()) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C✘ &8• &#FF5C5CHardreset juz trwa."));
            return;
        }

        player.sendMessage(ColorUtils.colorizeComponent(
            "&#FFBA00⚠ &8• &#FFBA00Uruchamiam hardreset prefixu &f" + prefix + "&e..."
        ));
        plugin.getLogger().warning("[Core] Hardreset prefix='" + prefix + "' by " + player.getName());

        service.hardReset(prefix).whenComplete((count, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        "&#FF5C5C✘ &8• &#FF5C5CHardreset nieudany: &f" + error.getMessage()
                    ));
                    return;
                }
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8• &#4EFA76Usunieto &f" + count + " &#4EFA76kolekcji z bazy &f" + prefix
                        + "&#4EFA76. Zapis zaczyna sie od nowa (bez restartu)."
                ));
            })
        );
    }

    public String getPrefix() {
        return prefix;
    }

    public int getStep() {
        return step;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
