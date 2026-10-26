package dev.grzybalson.anarchiagg.adminitems.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AntiCobwebListener implements Listener {

    private final HollyCore plugin;
    private final AdminItemsConfig config;

    public AntiCobwebListener(HollyCore plugin, AdminItemsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public AntiCobwebListener(JavaPlugin plugin, AdminItemsConfig config) {
        this((HollyCore) plugin, config);
    }

    private static final List<int[]> SPHERE_OFFSETS = makeSphereOffsets(5);

    private static List<int[]> makeSphereOffsets(int radius) {
        List<int[]> list = new ArrayList<>();
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx*dx + dy*dy + dz*dz <= r2) {
                        list.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        return list;
    }

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final int BATCH_SIZE = 32;
    private static final long REMOVE_PERIOD_TICKS = 2L;

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        org.bukkit.event.block.Action action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.BLAZE_ROD || AdminItemsGUI.KEY_ANTY_COBWEB == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (!meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_ANTY_COBWEB, PersistentDataType.BYTE)) return;

        if (AdminItemsRegionUtil.isInBlockedRegion(player, this.plugin)) {
            event.setCancelled(true);
            AdminItemsRegionUtil.sendBlockedMessage(player, this.plugin);
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        AdminItemsConfig.AntiCobwebSettings settings = this.config.getAntiCobweb();
        long cooldownMillis = Math.max(0L, settings.getCooldown()) * 1000L;
        Long until = this.cooldowns.get(uuid);
        if (until != null && until > now) {
            event.setCancelled(true);
            AdminItemsCooldowns.refresh(player, Material.BLAZE_ROD, until);
            player.sendMessage(ChatColor.RED + "Przedmiot posiada cooldown!");
            return;
        }
        this.cooldowns.remove(uuid);

        Location center = player.getLocation();
        org.bukkit.World world = center.getWorld();

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        List<Block> targets = new ArrayList<>();
        for (int[] off : SPHERE_OFFSETS) {
            int x = cx + off[0];
            int y = cy + off[1];
            int z = cz + off[2];

            if (y < 0 || y > 255) continue;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

            Block b = world.getBlockAt(x, y, z);
            if (b.getType() == Material.COBWEB) {
                targets.add(b);
            }
        }

        if (targets.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Nie znaleziono pajęczyn w pobliżu.");
            return;
        }

        String subtitle = dev.grzybalson.utils.ColorUtils.colorize("&aPomyślnie użyto, antycobweba!");
        dev.grzybalson.anarchiagg.util.TextUtil.sendTitle(player, "", subtitle, 5, 40, 5);
        if (cooldownMillis > 0L) {
            this.cooldowns.put(uuid, now + cooldownMillis);
            AdminItemsCooldowns.apply(player, Material.BLAZE_ROD, cooldownMillis);
        }

        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }

        new BukkitRunnable() {
            int idx = 0;
            @Override
            public void run() {
                int removedThisTick = 0;
                while (removedThisTick < BATCH_SIZE && idx < targets.size()) {
                    Block b = targets.get(idx++);
                    if (b.getType() == Material.COBWEB) b.setType(Material.AIR);
                    removedThisTick++;
                }
                if (idx >= targets.size()) {
                    this.cancel();
                }
            }
        }.runTaskTimer(this.plugin, 0L, REMOVE_PERIOD_TICKS);
    }
}
