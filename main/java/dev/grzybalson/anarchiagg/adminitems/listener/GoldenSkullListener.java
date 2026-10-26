package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;

import dev.grzybalson.utils.ColorUtils;

public class GoldenSkullListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;

    public GoldenSkullListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler(priority=EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity();
        Player killer = killed.getKiller();
        if (event.getKeepInventory() || (this.plugin.getCreatorModeManager() != null
            && this.plugin.getCreatorModeManager().shouldPreventDrops(killed, killer))) {
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(killed, this.plugin)) {
            return;
        }
        AdminItemsConfig.GoldenSkullSettings settings = this.config.getGoldenSkull();
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        PlayerInventory inv = killed.getInventory();
        int skullSlot = -1;
        for (int i = 0; i < inv.getSize(); ++i) {
            ItemStack item = inv.getItem(i);
            if (!this.isGoldenSkull(item)) continue;
            skullSlot = i;
            break;
        }
        if (skullSlot != -1) {
            if (event.getDrops() != null) {
                event.getDrops().clear();
            }
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            ItemStack skullItem = inv.getItem(skullSlot);
            if (skullItem != null) {
                if (skullItem.getAmount() > 1) {
                    skullItem.setAmount(skullItem.getAmount() - 1);
                } else {
                    inv.setItem(skullSlot, null);
                }
            }
            killed.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageUsed()));
            if (killer != null) {
                killer.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageKillerInfo().replace("{PLAYER}", killed.getName())));
            }
        }
    }

    private boolean isGoldenSkull(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return false;
        }
        SkullMeta meta = (SkullMeta)item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == this.config.getGoldenSkull().getCustomModelData();
    }
}
