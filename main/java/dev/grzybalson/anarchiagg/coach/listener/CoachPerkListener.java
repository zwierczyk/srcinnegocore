package dev.grzybalson.anarchiagg.coach.listener;

import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.coach.CoachManager;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CoachPerkListener implements Listener {

    private final HollyCore plugin;
    private final CoachManager manager;
    private final Map<UUID, LastHit> lastHits = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingRespawnItems = new HashMap<>();

    public CoachPerkListener(HollyCore plugin, CoachManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMysticHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        // Fast perk check: if target doesn't roll intelligence mystic block, exit immediately
        if (!manager.rollIntelligenceMysticBlock(target)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(target)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR || !manager.isMysticItem(item)) {
            item = attacker.getInventory().getItemInOffHand();
        }
        if (manager.isMysticItem(item)) {
            event.setCancelled(true);
            manager.sendMysticBlocked(target, attacker);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        lastHits.put(victim.getUniqueId(), new LastHit(attacker.getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombatHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        if (plugin.isAddonEnabled("customhit") && plugin.getCustomHitsService() != null) {
            return;
        }

        if (event.getDamage() <= 0.0 || victim.getNoDamageTicks() > 10) {
            return;
        }

        if (manager.rollDefenseBlock(victim)) {
            manager.sendDefenseBlock(victim, attacker);
            event.setCancelled(true);
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) {
            return;
        }

        if (attacker.getAttackCooldown() < 0.8f) {
            return;
        }

        double damage = event.getDamage();
        double strengthBonus = manager.getStrengthDamageBonusPercent(attacker);
        if (strengthBonus > 0.0) {
            damage *= (1.0 + (strengthBonus / 100.0));
        }

        double extraHit = manager.rollStrengthExtraHitDamage(attacker);
        if (extraHit > 0.0) {
            damage += extraHit;
            manager.sendStrengthExtraHitSubtitle(attacker, extraHit);
        }

        event.setDamage(damage);

        if (manager.tryApplyStrengthHeal(attacker)) {
            manager.sendStrengthHealSubtitle(attacker);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (plugin.getCreatorModeManager() != null
            && plugin.getCreatorModeManager().shouldPreventDrops(victim, killer)) return;
        if (hasGoldenSkull(victim)) return;
        if (event.getKeepInventory() || event.getDrops().isEmpty()) return;

        List<ItemStack> kept = new ArrayList<>();

        if (manager.rollDefenseKeepArmor(victim)) {
            keepArmorPieces(event, victim, kept);
        }
        if (manager.rollLuckKeepSword(victim)) {
            keepSword(event, victim, kept);
        }
        if (manager.rollIntelligenceKeepMystics(victim)) {
            keepMystics(event, kept);
        }

        if (!kept.isEmpty()) {
            pendingRespawnItems.computeIfAbsent(victim.getUniqueId(), uuid -> new ArrayList<>()).addAll(kept);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> kept = pendingRespawnItems.remove(event.getPlayer().getUniqueId());
        if (kept == null || kept.isEmpty()) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            for (ItemStack item : kept) {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                leftovers.values().forEach(left -> ItemCompatibilityUtil.dropItemNaturally(player, left));
            }
            manager.sendKeptItems(player, kept.size());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        LastHit lastHit = lastHits.get(victim.getUniqueId());
        if (lastHit == null || System.currentTimeMillis() - lastHit.timeMillis > 15000L) return;

        Player attacker = plugin.getServer().getPlayer(lastHit.attacker);
        if (attacker == null || !attacker.isOnline()) return;

        if (manager.rollDexterityTotemStrength(attacker)) {
            int duration = Math.max(1, manager.getConfig().getDexterity().getTotemStrengthDurationSeconds()) * 20;
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, duration, 0));
        }
        if (manager.rollDexterityFireworkBlock(attacker)) {
            int seconds = manager.getDexterityLevel(manager.getData(attacker.getUniqueId()).getLevel(dev.grzybalson.anarchiagg.coach.CoachSkill.DEXTERITY)).getFireworkCooldownSeconds();
            if (seconds <= 0) seconds = manager.getConfig().getDexterity().getFireworkCooldownSeconds();
            seconds = Math.max(1, seconds);
            manager.blockFireworks(victim, seconds);
            manager.sendFireworkBlocked(attacker, victim, seconds);
        }
        if (manager.rollIntelligenceBreakThreeTotems(attacker)) {
            manager.breakThreeTotemsLater(attacker, victim);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMysticUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (!manager.rollIntelligenceMysticBlock(target)) return;
        Player attacker = event.getPlayer();
        if (attacker.equals(target)) return;
        ItemStack item = itemFromHand(attacker, event.getHand());
        if (!manager.isMysticItem(item)) return;
        event.setCancelled(true);
        manager.sendMysticBlocked(target, attacker);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMysticUseAt(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;
        if (!manager.rollIntelligenceMysticBlock(target)) return;
        Player attacker = event.getPlayer();
        if (attacker.equals(target)) return;
        ItemStack item = itemFromHand(attacker, event.getHand());
        if (!manager.isMysticItem(item)) return;
        event.setCancelled(true);
        manager.sendMysticBlocked(target, attacker);
    }

    private void handleMysticBlock(Player attacker, Player target, ItemStack item, org.bukkit.event.Cancellable event) {
        if (attacker.equals(target)) return;
        if (!manager.rollIntelligenceMysticBlock(target)) return;
        if (!manager.isMysticItem(item)) return;
        event.setCancelled(true);
        manager.sendMysticBlocked(target, attacker);
    }

    private ItemStack itemFromHand(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) return player.getInventory().getItemInOffHand();
        return player.getInventory().getItemInMainHand();
    }

    private void keepArmorPieces(PlayerDeathEvent event, Player victim, List<ItemStack> kept) {
        int keptCount = 0;
        for (ItemStack armor : victim.getInventory().getArmorContents()) {
            if (keptCount >= 3) break;
            if (armor == null || armor.getType() == Material.AIR) continue;
            ItemStack clone = armor.clone();
            clone.setAmount(1);
            if (removeSimilarDrop(event, clone)) {
                kept.add(clone);
                keptCount++;
            }
        }
    }

    private void keepSword(PlayerDeathEvent event, Player victim, List<ItemStack> kept) {
        ItemStack sword = findSword(victim);
        if (sword == null) return;
        ItemStack clone = sword.clone();
        clone.setAmount(1);
        if (removeSimilarDrop(event, clone)) {
            kept.add(clone);
        }
    }

    private ItemStack findSword(Player victim) {
        ItemStack hand = victim.getInventory().getItemInMainHand();
        if (isSword(hand)) return hand;
        for (ItemStack item : victim.getInventory().getContents()) {
            if (isSword(item)) return item;
        }
        return null;
    }

    private void keepMystics(PlayerDeathEvent event, List<ItemStack> kept) {
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (!manager.isMysticItem(drop)) continue;
            kept.add(drop.clone());
            iterator.remove();
        }
    }

    private boolean removeSimilarDrop(PlayerDeathEvent event, ItemStack item) {
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop == null || !drop.isSimilar(item)) continue;
            if (drop.getAmount() <= item.getAmount()) {
                iterator.remove();
            } else {
                drop.setAmount(drop.getAmount() - item.getAmount());
            }
            return true;
        }
        return false;
    }

    private boolean isSword(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getType().name().endsWith("_SWORD");
    }

    private boolean hasGoldenSkull(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (!manager.isGoldenSkull(item)) continue;
            return true;
        }
        return false;
    }

    private record LastHit(UUID attacker, long timeMillis) {}
}
