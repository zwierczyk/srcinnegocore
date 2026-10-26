package dev.grzybalson.modules.masakraszafka;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class SzafkaEffects implements Listener {
    static final int EFFECT_TICKS = 70;

    private final MasakraSzafkaManager manager;

    SzafkaEffects(MasakraSzafkaManager manager) {
        this.manager = manager;
    }

    void tick(Player player, SzafkaPerks perks) {
        if (perks == SzafkaPerks.NONE || manager.effectsBlocked(player.getLocation())) return;
        for (Map.Entry<PotionEffectType, Integer> entry : perks.effects().entrySet()) {
            PotionEffect current = player.getPotionEffect(entry.getKey());
            if (current != null && (current.getAmplifier() > entry.getValue()
                || (current.getAmplifier() == entry.getValue() && (current.isInfinite() || current.getDuration() > EFFECT_TICKS - 25)))) continue;
            player.addPotionEffect(new PotionEffect(entry.getKey(), EFFECT_TICKS, entry.getValue(), true, false, true));
        }
        if (perks.noHunger() && player.getFoodLevel() < 20) {
            player.setFoodLevel(20);
            player.setSaturation(Math.max(player.getSaturation(), 5f));
        }
    }

    void clear(Player player, SzafkaPerks previous, SzafkaPerks current) {
        for (Map.Entry<PotionEffectType, Integer> entry : previous.effects().entrySet()) {
            Integer kept = current.effects().get(entry.getKey());
            if (kept != null && kept >= entry.getValue()) continue;
            PotionEffect active = player.getPotionEffect(entry.getKey());
            if (active != null && active.getAmplifier() == entry.getValue() && active.isAmbient() && !active.hasParticles()
                && !active.isInfinite() && active.getDuration() <= EFFECT_TICKS) {
                player.removePotionEffect(entry.getKey());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;
        Location location = event.getBlock().getLocation();
        double bonus = manager.miningBonus(player, location);
        if (bonus <= 0) return;
        Material mined = event.getBlockState().getType();
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            if (!manager.multipliesDrop(mined, stack, location)) continue;
            int extra = roll(stack.getAmount() * bonus);
            if (extra <= 0) continue;
            int merged = Math.min(extra, stack.getMaxStackSize() - stack.getAmount());
            if (merged > 0) {
                stack.setAmount(stack.getAmount() + merged);
                drop.setItemStack(stack);
            }
            for (int remaining = extra - merged; remaining > 0; ) {
                ItemStack spill = stack.asQuantity(Math.min(stack.getMaxStackSize(), remaining));
                drop.getWorld().dropItemNaturally(drop.getLocation(), spill);
                remaining -= spill.getAmount();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        double multiplier = 1;
        Player attacker = attacker(event);
        if (attacker != null && !attacker.equals(event.getEntity())) {
            double bonus = manager.perks(attacker).damagePercent();
            if (bonus > 0 && !manager.effectsBlocked(attacker.getLocation())) multiplier *= 1 + bonus / 100;
        }
        if (event.getEntity() instanceof Player victim && !victim.equals(attacker)) {
            double reduction = manager.perks(victim).reductionPercent();
            if (reduction > 0 && !manager.effectsBlocked(victim.getLocation())) multiplier *= 1 - reduction / 100;
        }
        if (multiplier != 1) setFinalBeforeAbsorption(event, beforeAbsorption(event) * multiplier);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLifesteal(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event);
        if (attacker == null || !(event.getEntity() instanceof Player victim) || attacker.equals(victim)
            || event.getFinalDamage() <= 0 || attacker.isDead()) return;
        SzafkaPerks perks = manager.perks(attacker);
        if (perks.lifestealChance() <= 0 || perks.lifestealHearts() <= 0) return;
        if (ThreadLocalRandom.current().nextDouble(100) >= perks.lifestealChance()) return;
        if (manager.effectsBlocked(attacker.getLocation()) || manager.effectsBlocked(victim.getLocation())) return;
        AttributeInstance maxHealth = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20;
        if (attacker.getHealth() >= max) return;
        attacker.setHealth(Math.min(max, attacker.getHealth() + perks.lifestealHearts() * 2));
        attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2.1, 0), 2, 0.25, 0.1, 0.25, 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFoodLevel() >= player.getFoodLevel()) return;
        if (manager.perks(player).noHunger() && !manager.effectsBlocked(player.getLocation())) event.setCancelled(true);
    }

    private static int roll(double expected) {
        int whole = (int) expected;
        return whole + (ThreadLocalRandom.current().nextDouble() < expected - whole ? 1 : 0);
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        return event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player ? player : null;
    }

    private static double beforeAbsorption(EntityDamageEvent event) {
        double value = event.getFinalDamage();
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)) value -= event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION);
        return Math.max(0, value);
    }

    private static void setFinalBeforeAbsorption(EntityDamageEvent event, double target) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double current = beforeAbsorption(event);
            if (Math.abs(current - target) < 0.0001) return;
            if (target <= 0 || current <= 0) {
                event.setDamage(0);
                return;
            }
            event.setDamage(Math.min(4096, event.getDamage() * target / current));
        }
    }
}
