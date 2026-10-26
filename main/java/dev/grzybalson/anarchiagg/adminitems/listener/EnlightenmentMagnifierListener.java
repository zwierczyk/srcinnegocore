package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

public class EnlightenmentMagnifierListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();

    public EnlightenmentMagnifierListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        Player attacker;
        Entity entity;
        block14: {
            block13: {
                entity = event.getDamager();
                if (!(entity instanceof Player)) break block13;
                attacker = (Player)entity;
                entity = event.getEntity();
                if (entity instanceof Player) break block14;
            }
            return;
        }
        Player target = (Player)entity;
        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!this.isEnlightenmentMagnifier(item)) {
            return;
        }
        AdminItemsConfig.EnlightenmentMagnifierSettings settings = this.config.getEnlightenmentMagnifier();
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(attacker, this.plugin)) {
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageSpawn()));
            event.setCancelled(true);
            return;
        }
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Long cooldownUntil = this.cooldowns.get(uuid);
        if (cooldownUntil != null) {
            if (cooldownUntil > now) {
                AdminItemsCooldowns.refresh(attacker, Material.SOUL_TORCH, cooldownUntil);
                event.setCancelled(true);
                return;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldowns, uuid, cooldownUntil);
        }
        StringBuilder armorStatus = new StringBuilder();
        armorStatus.append(dev.grzybalson.utils.ColorUtils.colorize(settings.getHeaderMessage().replace("{PLAYER}", target.getName())));
        ItemStack[] armorContents = target.getInventory().getArmorContents();
        boolean hasArmor = false;
        for (int i = armorContents.length - 1; i >= 0; --i) {
            ItemMeta meta;
            ItemStack armorItem = armorContents[i];
            if (armorItem == null || armorItem.getType().isAir() || !((meta = armorItem.getItemMeta()) instanceof Damageable)) continue;
            Damageable damageable = (Damageable)meta;
            hasArmor = true;
            short maxDurability = armorItem.getType().getMaxDurability();
            int currentDurability = maxDurability - damageable.getDamage();
            String formatName = this.formatItemName(armorItem.getType());
            String line = settings.getArmorLineMessage().replace("{ITEM}", formatName).replace("{DURABILITY}", String.valueOf(currentDurability)).replace("{MAX_DURABILITY}", String.valueOf(maxDurability));
            armorStatus.append("\n").append(dev.grzybalson.utils.ColorUtils.colorize(line));
        }
        if (!hasArmor) {
            armorStatus.append("\n").append(dev.grzybalson.utils.ColorUtils.colorize("&#F6FFC5&l\u029f&#E4FFC9&l\u1d1c&#D3FFCD&l\u1d18&#C1FFD1&l\u1d00 &#9EFFDA&l\u1d0f&#8DFFDE&l\u015b&#7BFFE2&l\u1d21&#69FFE6&l\u026a&#58FFEA&l\u1d07&#46FFEE&l\u1d04&#35FFF3&l\u1d07&#23FFF7&l\u0274&#12FFFB&l\u026a&#00FFFF&l\u1d00 &8:: &cBrak za\u0142o\u017conej zbroi."));
        }
        attacker.sendMessage(armorStatus.toString());
        int totemCount = 0;
        for (ItemStack invItem : target.getInventory().getContents()) {
            if (invItem == null || invItem.getType() != Material.TOTEM_OF_UNDYING) continue;
            totemCount += invItem.getAmount();
        }
        ItemStack offHand = target.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == Material.TOTEM_OF_UNDYING) {
            totemCount += offHand.getAmount();
        }
        attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getTotemMessage().replace("{COUNT}", String.valueOf(totemCount))));
        long cooldownMillis = AdminItemsCooldowns.secondsToMillis(settings.getCooldown());
        if (cooldownMillis > 0L) {
            this.cooldowns.put(uuid, now + cooldownMillis);
            AdminItemsCooldowns.apply(attacker, Material.SOUL_TORCH, cooldownMillis);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            ItemStack item = event.getItem();
            if (this.isEnlightenmentMagnifier(item)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isEnlightenmentMagnifier(ItemStack item) {
        if (item == null || item.getType() != Material.SOUL_TORCH || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == this.config.getEnlightenmentMagnifier().getCustomModelData();
    }

    private String formatItemName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
