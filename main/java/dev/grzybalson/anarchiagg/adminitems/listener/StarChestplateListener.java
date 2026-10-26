package dev.grzybalson.anarchiagg.adminitems.listener;

import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;

import dev.grzybalson.utils.ColorUtils;

public class StarChestplateListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Random random = new Random();

    public StarChestplateListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
        this.startEffectsTask();
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player defender = (Player)entity;
        ItemStack chestplate = defender.getInventory().getChestplate();
        if (!this.isStarChestplate(chestplate)) {
            return;
        }
        this.protectStarChestplate(chestplate);
        AdminItemsConfig.StarChestplateSettings settings = this.config.getStarChestplate();
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        double chance = Math.max(0.0, Math.min(100.0, settings.getBlockChance()));
        // Stary default 15% → +50% odporności (22.5%)
        if (Math.abs(chance - 15.0D) < 0.05D) {
            chance = 22.5D;
            settings.setBlockChance(22.5D);
        }
        if (this.random.nextDouble() * 100.0 <= chance) {
            // PvpCancelTracker.mark(this.getAttackingPlayer(event), defender, "klata gwiazdy");
            event.setCancelled(true);
            defender.getWorld().playSound(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.8f);
            defender.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getMessageBlocked()));
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onStarChestplateDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player defender = (Player)entity;
        ItemStack chestplate = defender.getInventory().getChestplate();
        if (!this.isStarChestplate(chestplate)) {
            return;
        }
        AdminItemsConfig.StarChestplateSettings settings = this.config.getStarChestplate();
        if (settings == null || !settings.isEnabled()) {
            return;
        }
        this.protectStarChestplate(chestplate);
        this.applyNetheriteChestplateProtection(event, defender);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (this.isStarChestplate(item)) {
            event.setCancelled(true);
            this.protectStarChestplate(item);
        }
    }

    private void startEffectsTask() {
        new BukkitRunnable(){

            public void run() {
                AdminItemsConfig.StarChestplateSettings settings = StarChestplateListener.this.config.getStarChestplate();
                if (settings == null || !settings.isEnabled()) {
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PotionEffect activeEffect;
                    ItemStack chestplate = player.getInventory().getChestplate();
                    if (StarChestplateListener.this.isStarChestplate(chestplate)) {
                        StarChestplateListener.this.protectStarChestplate(chestplate);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 80, 1, false, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 0, false, false, true));
                        continue;
                    }
                    if (player.hasPotionEffect(PotionEffectType.INCREASE_DAMAGE) && (activeEffect = player.getPotionEffect(PotionEffectType.INCREASE_DAMAGE)) != null && activeEffect.getDuration() <= 80) {
                        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
                    }
                    if (!player.hasPotionEffect(PotionEffectType.SPEED) || (activeEffect = player.getPotionEffect(PotionEffectType.SPEED)) == null || activeEffect.getDuration() > 80) continue;
                    player.removePotionEffect(PotionEffectType.SPEED);
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 20L, 20L);
    }

    private Player getAttackingPlayer(EntityDamageByEntityEvent event) {
        Projectile projectile;
        Entity entity = event.getDamager();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            return player;
        }
        Entity entity2 = event.getDamager();
        if (entity2 instanceof Projectile && (projectile = (Projectile)entity2).getShooter() instanceof Player) {
            Player player = (Player)projectile.getShooter();
            return player;
        }
        return null;
    }

    private void applyNetheriteChestplateProtection(EntityDamageEvent event, Player defender) {
        double rawDamage = event.getDamage();
        if (rawDamage <= 0.0D) {
            return;
        }
        ArmorStats currentStats = this.armorStats(defender, false);
        ArmorStats netheriteStats = this.armorStats(defender, true);
        double currentAfterArmor = this.damageAfterArmor(rawDamage, currentStats);
        double netheriteAfterArmor = this.damageAfterArmor(rawDamage, netheriteStats);
        if (netheriteAfterArmor >= currentAfterArmor) {
            return;
        }
        double adjustedRaw = this.rawDamageForArmorResult(netheriteAfterArmor, rawDamage, currentStats);
        if (adjustedRaw < rawDamage) {
            event.setDamage(Math.max(0.0D, adjustedRaw));
        }
    }

    private ArmorStats armorStats(Player defender, boolean starChestplateAsNetherite) {
        double armor = 0.0D;
        double toughness = 0.0D;
        for (ItemStack item : defender.getInventory().getArmorContents()) {
            if (starChestplateAsNetherite && this.isStarChestplate(item)) {
                armor += 8.0D;
                toughness += 3.0D;
                continue;
            }
            armor += this.armorPoints(item);
            toughness += this.armorToughness(item);
        }
        return new ArmorStats(armor, toughness);
    }

    private double rawDamageForArmorResult(double targetAfterArmor, double maxRawDamage, ArmorStats currentStats) {
        double low = 0.0D;
        double high = maxRawDamage;
        for (int i = 0; i < 24; i++) {
            double middle = (low + high) / 2.0D;
            double result = this.damageAfterArmor(middle, currentStats);
            if (result > targetAfterArmor) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    private double damageAfterArmor(double damage, ArmorStats stats) {
        double armorReduction = Math.min(20.0D, Math.max(stats.armor / 5.0D, stats.armor - damage / (2.0D + stats.toughness / 4.0D)));
        return damage * (1.0D - armorReduction / 25.0D);
    }

    private double armorPoints(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0.0D;
        }
        return switch (item.getType()) {
            case TURTLE_HELMET, GOLDEN_HELMET, CHAINMAIL_HELMET, IRON_HELMET -> 2.0D;
            case LEATHER_HELMET -> 1.0D;
            case DIAMOND_HELMET, NETHERITE_HELMET -> 3.0D;
            case LEATHER_CHESTPLATE -> 3.0D;
            case GOLDEN_CHESTPLATE, CHAINMAIL_CHESTPLATE -> 5.0D;
            case IRON_CHESTPLATE -> 6.0D;
            case DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE -> 8.0D;
            case LEATHER_LEGGINGS -> 2.0D;
            case GOLDEN_LEGGINGS -> 3.0D;
            case CHAINMAIL_LEGGINGS -> 4.0D;
            case IRON_LEGGINGS -> 5.0D;
            case DIAMOND_LEGGINGS, NETHERITE_LEGGINGS -> 6.0D;
            case LEATHER_BOOTS, GOLDEN_BOOTS, CHAINMAIL_BOOTS -> 1.0D;
            case IRON_BOOTS -> 2.0D;
            case DIAMOND_BOOTS, NETHERITE_BOOTS -> 3.0D;
            default -> 0.0D;
        };
    }

    private double armorToughness(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return 0.0D;
        }
        String materialName = item.getType().name();
        if (materialName.startsWith("NETHERITE_")) {
            return 3.0D;
        }
        if (materialName.startsWith("DIAMOND_")) {
            return 2.0D;
        }
        return 0.0D;
    }

    private void protectStarChestplate(ItemStack item) {
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

    private static final class ArmorStats {
        private final double armor;
        private final double toughness;

        private ArmorStats(double armor, double toughness) {
            this.armor = armor;
            this.toughness = toughness;
        }
    }
}
