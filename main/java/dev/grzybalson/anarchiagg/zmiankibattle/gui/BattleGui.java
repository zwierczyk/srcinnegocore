package dev.grzybalson.anarchiagg.zmiankibattle.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import dev.grzybalson.anarchiagg.zmiankibattle.ZmiankaBattle;
import dev.grzybalson.anarchiagg.zmiankibattle.battle.Battle;
import dev.grzybalson.anarchiagg.zmiankibattle.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BattleGui {

    private final ZmiankaBattle plugin;
    private final Battle battle;
    private final FileConfiguration cfg;

    private Inventory inv;

    private final int slotInviterHead;
    private final int slotInviterPercent;
    private final int slotInvitedPercent;
    private final int slotInvitedHead;

    public BattleGui(ZmiankaBattle plugin, Battle battle) {
        this.plugin = plugin;
        this.battle = battle;
        this.cfg = plugin.getConfig();

        this.slotInviterHead = cfg.getInt("battle.gui.slots.inviter-head", 10);
        this.slotInviterPercent = cfg.getInt("battle.gui.slots.inviter-percent", 11);
        this.slotInvitedPercent = cfg.getInt("battle.gui.slots.invited-percent", 15);
        this.slotInvitedHead = cfg.getInt("battle.gui.slots.invited-head", 16);
    }

    public Inventory createInventory() {
        int rows = cfg.getInt("battle.gui.rows", 3);
        int size = Math.max(1, rows) * 9;

        String title = cfg.getString("battle.gui.title", "&8Zmianki - {ilość}");
        String money = Text.formatMoney(battle.amount());
        title = Text.apply(title, Map.of("{ilość}", money, "{ilosc}", money));

        BattleGuiHolder holder = new BattleGuiHolder(battle);
        inv = Bukkit.createInventory(holder, size, Text.component(title));

        // wypełnienie tła
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(Text.color(" "));
        filler.setItemMeta(fm);
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        // itemy startowe
        inv.setItem(slotInviterHead, head(battle.inviterId()));
        inv.setItem(slotInvitedHead, head(battle.invitedId()));

        // początkowe procenty ustawiamy na MAX, żeby animacja była od max -> wylosowanej
        inv.setItem(slotInviterPercent, percentItem(battle.maxPercent()));
        inv.setItem(slotInvitedPercent, percentItem(battle.maxPercent()));

        return inv;
    }

    public void updateInviterPercent(int percent) {
        if (inv == null) return;
        inv.setItem(slotInviterPercent, percentItem(percent));
    }

    public void updateInvitedPercent(int percent) {
        if (inv == null) return;
        inv.setItem(slotInvitedPercent, percentItem(percent));
    }

    private ItemStack head(java.util.UUID uuid) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta im = it.getItemMeta();
        if (im instanceof SkullMeta sm) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            sm.setOwningPlayer(op);
            String name = op.getName() != null ? op.getName() : "Gracz";
            sm.setDisplayName(Text.color("&f" + name));
            it.setItemMeta(sm);
        }
        return it;
    }

    private ItemStack percentItem(int percent) {
        Material mat = glassFor(percent);
        int amount = Math.max(1, Math.min(64, Math.abs(percent)));
        ItemStack it = new ItemStack(mat, amount);

        String nameT = cfg.getString("battle.gui.percent-item.name", "&fSzansa: &e{percent}%");
        List<String> loreT = cfg.getStringList("battle.gui.percent-item.lore");

        Map<String, String> ph = new HashMap<>();
        ph.put("{percent}", String.valueOf(percent));

        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Text.color(Text.apply(nameT, ph)));

        if (loreT != null && !loreT.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String l : loreT) {
                lore.add(Text.color(Text.apply(l, ph)));
            }
            meta.setLore(lore);
        }
        it.setItemMeta(meta);
        return it;
    }

    private Material glassFor(int percent) {
        // orange: 52..46
        if (percent >= 46) return Material.ORANGE_STAINED_GLASS_PANE;
        // yellow: 45..30
        if (percent >= 30) return Material.YELLOW_STAINED_GLASS_PANE;
        // white: 29..15
        if (percent >= 15) return Material.WHITE_STAINED_GLASS_PANE;
        // gray: 14..0
        if (percent >= 0) return Material.GRAY_STAINED_GLASS_PANE;
        // red: -1..-24
        return Material.RED_STAINED_GLASS_PANE;
    }
}
