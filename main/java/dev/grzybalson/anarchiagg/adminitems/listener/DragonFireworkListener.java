package dev.grzybalson.anarchiagg.adminitems.listener;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.utils.ColorUtils;

public class DragonFireworkListener
implements Listener {
    private final HollyCore plugin;
    private final NamespacedKey key;

    public DragonFireworkListener(HollyCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey((Plugin)plugin, "admin_item_smocza_fajerwerka");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() == null) {
            return;
        }

        AdminItemsConfig.DragonFireworkSettings settings = this.plugin.getAdminItemsConfig().getDragonFirework();
        ItemStack item = event.getItem();
        if (!this.isDragonFirework(item, settings)) {
            return;
        }

        Player player = event.getPlayer();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);

            if (player.hasCooldown(Material.FIREWORK_ROCKET)) {
                return;
            }

            AdminItemsCooldowns.applySeconds(player, Material.FIREWORK_ROCKET, settings.getCooldown());
            this.useFirework(player, event, item);
            return;
        }

        if (player.isGliding()) {
            return;
        }

        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        AdminItemsConfig.DragonFireworkSettings settings = this.plugin.getAdminItemsConfig().getDragonFirework();
        ItemStack item = event.getItemStack();
        if (!this.isDragonFirework(item, settings)) {
            return;
        }
        event.setShouldConsume(false);
        Player player = event.getPlayer();
        if (player.hasCooldown(Material.FIREWORK_ROCKET)) {
            event.setCancelled(true);
            return;
        }
        AdminItemsCooldowns.applySeconds(player, Material.FIREWORK_ROCKET, settings.getCooldown());
        this.applyFireworkMeta(event.getFirework(), item);
    }

    private boolean isDragonFirework(ItemStack item, AdminItemsConfig.DragonFireworkSettings settings) {
        if (item == null || item.getType() != Material.FIREWORK_ROCKET || !item.hasItemMeta()) {
            return false;
        }
        if (!settings.isEnabled()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE)) {
            return true;
        }
        if (!(settings.getCustomModelData() == 0 || meta.hasCustomModelData() && meta.getCustomModelData() == settings.getCustomModelData())) {
            return false;
        }
        return meta.hasDisplayName() && meta.getDisplayName().equals(dev.grzybalson.utils.ColorUtils.colorize(settings.getName()));
    }

    private void useFirework(Player player, PlayerInteractEvent event, ItemStack item) {
        if (event.getClickedBlock() == null) {
            return;
        }
        BlockFace face = event.getBlockFace();
        Location location = event.getClickedBlock().getRelative(face).getLocation().add(0.5, 0.0, 0.5);
        Firework firework = (Firework)player.getWorld().spawn(location, Firework.class);
        firework.setShooter((ProjectileSource)player);
        this.applyFireworkMeta(firework, item);
    }

    private void applyFireworkMeta(Firework firework, ItemStack item) {
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta instanceof FireworkMeta) {
            firework.setFireworkMeta((FireworkMeta)itemMeta);
        }
    }
}
