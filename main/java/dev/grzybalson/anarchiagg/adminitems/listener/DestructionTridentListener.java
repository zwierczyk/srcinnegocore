package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.utils.ColorUtils;

public class DestructionTridentListener
implements Listener {
    private static final int COOLDOWN_SECONDS = 30;
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> targetCooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Random random = new Random();

    public DestructionTridentListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        long targetCooldown;
        Player attacker;
        Entity entity;
        block10: {
            block9: {
                entity = event.getDamager();
                if (!(entity instanceof Player)) break block9;
                attacker = (Player)entity;
                entity = event.getEntity();
                if (entity instanceof Player) break block10;
            }
            return;
        }
        Player target = (Player)entity;
        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!this.isDestructionTrident(item)) {
            return;
        }
        this.protectDestructionTrident(item);
        AdminItemsConfig.DestructionTridentSettings settings = this.config.getDestructionTrident();
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(attacker, this.plugin)) {
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageSpawn()));
            event.setCancelled(true);
            return;
        }
        UUID uuid = attacker.getUniqueId();
        UUID targetUuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = AdminItemsCooldowns.secondsToMillis(30);
        this.clearExpiredTargetCooldowns(now);
        if (this.cooldowns.containsKey(uuid)) {
            long cooldownUntil = this.cooldowns.get(uuid);
            long timeLeft = cooldownUntil - now;
            if (timeLeft > 0L) {
                AdminItemsCooldowns.refresh(attacker, Material.TRIDENT, cooldownUntil);
                attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cMusisz poczeka\u0107 jeszcze &4" + timeLeft / 1000L + "s"));
                return;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldowns, uuid, cooldownUntil);
        }
        if ((targetCooldown = this.targetCooldowns.getOrDefault(targetUuid, 0L).longValue()) > now) {
            long secondsLeft = Math.max(1L, (targetCooldown - now + 999L) / 1000L);
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cTen gracz byl juz trafiony trojzebem! Poczekaj jeszcze &4" + secondsLeft + "s"));
            return;
        }
        this.cooldowns.put(uuid, now + cooldownMillis);
        this.targetCooldowns.put(targetUuid, now + cooldownMillis);
        AdminItemsCooldowns.apply(attacker, Material.TRIDENT, cooldownMillis);
        this.damageArmorSecurely(target, settings.getMinArmorDamage(), settings.getMaxArmorDamage());
        this.strikeLightningEffects(target.getLocation(), settings.getLightningStrikes());
        attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageUse()));
    }

    private void clearExpiredTargetCooldowns(long now) {
        this.targetCooldowns.entrySet().removeIf(entry -> (Long)entry.getValue() <= now);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onTridentThrow(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (this.isDestructionTrident(item)) {
            this.protectDestructionTrident(item);
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (this.isDestructionTrident(item)) {
            event.setCancelled(true);
            this.protectDestructionTrident(item);
        }
    }

    private void damageArmorSecurely(Player target, int minDamage, int maxDamage) {
        int lowerDamage = Math.min(minDamage, maxDamage);
        int upperDamage = Math.max(minDamage, maxDamage);
        for (ItemStack armorPiece : target.getInventory().getArmorContents()) {
            ItemMeta meta;
            if (armorPiece == null || armorPiece.getType().isAir() || this.isStarChestplate(armorPiece) || !((meta = armorPiece.getItemMeta()) instanceof Damageable)) continue;
            Damageable damageable = (Damageable)meta;
            short maxDurability = armorPiece.getType().getMaxDurability();
            if (maxDurability <= 0) continue;
            int extraDamage = lowerDamage + this.random.nextInt(upperDamage - lowerDamage + 1);
            int currentDamage = damageable.getDamage();
            damageable.setDamage(Math.min(currentDamage + extraDamage, maxDurability));
            armorPiece.setItemMeta((ItemMeta)damageable);
        }
    }

    private void strikeLightningEffects(Location loc, int strikes) {
        if (loc.getWorld() == null) {
            return;
        }
        for (int i = 0; i < strikes; ++i) {
            loc.getWorld().strikeLightningEffect(loc);
        }
    }

    private void protectDestructionTrident(ItemStack item) {
        Damageable damageable;
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        if (!meta.isUnbreakable()) {
            meta.setUnbreakable(true);
            changed = true;
        }
        if (meta instanceof Damageable && (damageable = (Damageable)meta).getDamage() != 0) {
            damageable.setDamage(0);
            changed = true;
        }
        if (changed) {
            item.setItemMeta(meta);
        }
    }

    private boolean isDestructionTrident(ItemStack item) {
        if (item == null || item.getType() != Material.TRIDENT || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (AdminItemsGUI.KEY_TROJZAB_ZNISZCZENIA != null && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_TROJZAB_ZNISZCZENIA, PersistentDataType.BYTE)) {
            return true;
        }
        int requiredCustomModelData = this.config.getDestructionTrident().getCustomModelData();
        return requiredCustomModelData != 0 && meta.hasCustomModelData() && meta.getCustomModelData() == requiredCustomModelData;
    }

    private boolean isStarChestplate(ItemStack item) {
        if (item == null || item.getType() != Material.CHAINMAIL_CHESTPLATE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (AdminItemsGUI.KEY_GWIEZDNA_KLATA != null && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_GWIEZDNA_KLATA, PersistentDataType.BYTE)) {
            return true;
        }
        AdminItemsConfig.StarChestplateSettings settings = this.config.getStarChestplate();
        return settings != null && settings.getCustomModelData() != 0 && meta.hasCustomModelData() && meta.getCustomModelData() == settings.getCustomModelData();
    }
}
