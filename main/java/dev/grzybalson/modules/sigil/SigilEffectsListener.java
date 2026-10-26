package dev.grzybalson.modules.sigil;

import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import dev.grzybalson.utils.ColorUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class SigilEffectsListener implements Listener {

    private static final int JAD_LEVITATION_TICKS = 40; // 2s
    private static final int GNIEW_HITS_MIN = 10;
    private static final int GNIEW_HITS_MAX = 20;

    private final SigilManager manager;
    /** Łączne hity atakującego z Jadem (wszyscy cele razem, nie per ofiara). */
    private final Map<UUID, AtomicInteger> jadHitCounters = new ConcurrentHashMap<>();
    /** Łączne hity Gniewu — próg 10–20 losowany po każdym procie (ukryty). */
    private final Map<UUID, AtomicInteger> gniewHitCounters = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> gniewHitsRequired = new ConcurrentHashMap<>();
    private final Map<UUID, Long> boundUntil = new ConcurrentHashMap<>();

    public SigilEffectsListener(SigilManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event);
        Player victim = event.getEntity() instanceof Player player ? player : null;

        if (attacker != null && victim != null && !attacker.equals(victim)) {
            UUID victimId = victim.getUniqueId();

            double blockChance = manager.getActivePower(victimId, SigilType.TARCZA);
            if (rollChance(blockChance)) {
                event.setCancelled(true);
                playShieldBreak(attacker, victim);
                manager.notifyPerk(attacker, victim, SigilType.TARCZA, blockChance, true);
                return;
            }

            double resist = manager.getActivePower(victimId, SigilType.WYTRZYMALOSC);
            if (resist > 0.0D) {
                event.setDamage(Math.max(0.0D, event.getDamage() * (1.0D - resist / 100.0D)));
            }
        }

        if (attacker == null) {
            return;
        }

        UUID attackerId = attacker.getUniqueId();
        double damage = event.getDamage();
        double dmgScale = manager.getDamageBonusMultiplier();

        double furia = manager.getActivePower(attackerId, SigilType.FURIA);
        if (furia > 0.0D) {
            damage *= 1.0D + (furia / 100.0D) * dmgScale;
        }

        // Gniew: +½ serca co 10–20 hitów (losowo, łącznie, nie per ofiara).
        double gniew = manager.getEquippedPower(attackerId, SigilType.GNIEW);
        boolean gniewCrit = tryProcGniew(attacker, attackerId, event.getEntity(), gniew);
        if (gniewCrit) {
            damage += 1.0D;
        }

        double przebicie = manager.getActivePower(attackerId, SigilType.PRZEBICIE);
        if (przebicie > 0.0D && victim != null) {
            double armorPoints = victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR) == null
                ? 0.0D
                : victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR).getValue();
            if (armorPoints > 0.0D) {
                double ignoredFraction = Math.min(1.0D, (przebicie / 100.0D) * dmgScale);
                damage *= 1.0D + (ignoredFraction * Math.min(0.8D, armorPoints / 20.0D));
            }
        }

        if (damage != event.getDamage()) {
            event.setDamage(Math.max(0.0D, damage));
        }

        if (gniewCrit && victim != null && !attacker.equals(victim)) {
            manager.notifyPerk(attacker, victim, SigilType.GNIEW, gniew, false);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 1.15F);
        }

        tryProcJad(attacker, attackerId, victim, event.getEntity());

        if (victim == null || attacker.equals(victim)) {
            return;
        }

        double klatwa = manager.getActivePower(attackerId, SigilType.KLATWA);
        if (rollChance(klatwa)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
            manager.notifyPerk(attacker, victim, SigilType.KLATWA, klatwa, false);
        }

        double drenaz = manager.getActivePower(attackerId, SigilType.DRENAZ);
        if (drenaz > 0.0D) {
            // Lifesteal też -50% wraz z damage-bonus-multiplier
            double heal = event.getFinalDamage() * ((drenaz / 100.0D) * dmgScale);
            if (heal > 0.0D) {
                double maxHealth = attacker.getMaxHealth();
                attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + heal));
            }
        }

        double spetania = manager.getActivePower(attackerId, SigilType.SPETANIA);
        if (rollChance(spetania * 0.2D)) {
            bindPlayer(victim, attacker, 4000L);
            manager.notifyPerk(attacker, victim, SigilType.SPETANIA, spetania, false);
        }

        double podmuchu = manager.getActivePower(victim.getUniqueId(), SigilType.PODMUCHU);
        if (podmuchu > 0.0D
            && victim.hasPotionEffect(PotionEffectType.LEVITATION)
            && rollChance(podmuchu)) {
            knockAway(attacker, victim);
            manager.notifyPerk(victim, attacker, SigilType.PODMUCHU, podmuchu, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        jadHitCounters.remove(event.getPlayer().getUniqueId());
        gniewHitCounters.remove(event.getPlayer().getUniqueId());
        gniewHitsRequired.remove(event.getPlayer().getUniqueId());
        boundUntil.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        double chance = manager.getActivePower(killer.getUniqueId(), SigilType.LOWCY);
        if (!rollChance(chance)) {
            return;
        }
        killer.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 600, 0, false, true, true));
        manager.notifyPerk(killer, victim, SigilType.LOWCY, chance, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoundMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isBound(player.getUniqueId())) {
            return;
        }
        if (player.isGliding()) {
            player.setGliding(false);
        }
        if (event.getTo() == null) {
            return;
        }
        if (event.getTo().getY() > event.getFrom().getY()) {
            event.getTo().setY(event.getFrom().getY());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoundGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isGliding() && isBound(player.getUniqueId())) {
            event.setCancelled(true);
            player.setGliding(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoundFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying() && isBound(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().setFlying(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
            && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }

        PotionEffect effect = event.getNewEffect();
        if (effect == null || isPositive(effect.getType())) {
            return;
        }

        double chance = manager.getActivePower(player.getUniqueId(), SigilType.ODPORNOSC);
        if (rollChance(chance)) {
            event.setCancelled(true);
            manager.notifyPerk(null, player, SigilType.ODPORNOSC, chance, true);
        }
    }

    private boolean isPositive(PotionEffectType type) {
        if (type == null) {
            return true;
        }
        return type.equals(PotionEffectType.SPEED)
            || type.equals(PotionEffectType.FAST_DIGGING)
            || type.equals(PotionEffectType.INCREASE_DAMAGE)
            || type.equals(PotionEffectType.HEAL)
            || type.equals(PotionEffectType.JUMP)
            || type.equals(PotionEffectType.REGENERATION)
            || type.equals(PotionEffectType.DAMAGE_RESISTANCE)
            || type.equals(PotionEffectType.FIRE_RESISTANCE)
            || type.equals(PotionEffectType.WATER_BREATHING)
            || type.equals(PotionEffectType.INVISIBILITY)
            || type.equals(PotionEffectType.NIGHT_VISION)
            || type.equals(PotionEffectType.HEALTH_BOOST)
            || type.equals(PotionEffectType.ABSORPTION)
            || type.equals(PotionEffectType.SATURATION)
            || type.equals(PotionEffectType.LUCK)
            || type.equals(PotionEffectType.SLOW_FALLING)
            || type.equals(PotionEffectType.CONDUIT_POWER)
            || type.equals(PotionEffectType.DOLPHINS_GRACE)
            || type.equals(PotionEffectType.HERO_OF_THE_VILLAGE);
    }

    private void playShieldBreak(Player attacker, Player victim) {
        if (attacker != null && attacker.isOnline()) {
            attacker.playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0F, 1.0F);
        }
        if (victim != null && victim.isOnline()) {
            victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0F, 1.0F);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    /** Hity ze wszystkich celów razem; co 10–20 trafień (losowo). */
    private boolean tryProcGniew(Player attacker, UUID attackerId, org.bukkit.entity.Entity damaged, double gniew) {
        if (gniew <= 0.0D || !(damaged instanceof LivingEntity) || attacker.equals(damaged)) {
            return false;
        }
        int required = gniewHitsRequired.computeIfAbsent(attackerId, key -> nextGniewHitsRequired());
        int hits = gniewHitCounters
            .computeIfAbsent(attackerId, key -> new AtomicInteger())
            .incrementAndGet();
        if (hits < required) {
            return false;
        }
        gniewHitCounters.get(attackerId).set(0);
        gniewHitsRequired.put(attackerId, nextGniewHitsRequired());
        return true;
    }

    private static int nextGniewHitsRequired() {
        return ThreadLocalRandom.current().nextInt(GNIEW_HITS_MIN, GNIEW_HITS_MAX + 1);
    }

    /** Hity ze wszystkich celów razem; lewitacja wpada na gracza, którego aktualnie trafisz. */
    private void tryProcJad(Player attacker, UUID attackerId, Player victim, org.bukkit.entity.Entity damaged) {
        if (!(damaged instanceof LivingEntity) || attacker.equals(damaged)) {
            return;
        }

        double jad = manager.getEquippedPower(attackerId, SigilType.JAD);
        if (jad <= 0.0D) {
            return;
        }

        int required = jadHitsRequired(jad);
        int hits = jadHitCounters
            .computeIfAbsent(attackerId, key -> new AtomicInteger())
            .incrementAndGet();
        if (hits < required || victim == null) {
            return;
        }

        jadHitCounters.get(attackerId).set(0);
        victim.addPotionEffect(new PotionEffect(
            PotionEffectType.LEVITATION,
            JAD_LEVITATION_TICKS,
            0,
            false,
            true,
            true
        ));
        manager.notifyPerk(attacker, victim, SigilType.JAD, jad, false);
    }

    /** 5% → 30, 10% → 27, 15% → 24, 20% → 20, 25% → 17 (progi ukryte, nie w lore). */
    private static int jadHitsRequired(double power) {
        if (power >= 25.0D) {
            return 17;
        }
        if (power >= 20.0D) {
            return 20;
        }
        if (power >= 15.0D) {
            return 24;
        }
        if (power >= 10.0D) {
            return 27;
        }
        return 30;
    }

    private boolean rollChance(double percent) {
        if (percent <= 0.0D) {
            return false;
        }
        if (percent >= 100.0D) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble(100.0D) < percent;
    }

    private void bindPlayer(Player victim, Player attacker, long durationMs) {
        boundUntil.put(victim.getUniqueId(), System.currentTimeMillis() + durationMs);
        victim.setGliding(false);
        victim.setVelocity(new Vector(0, 0, 0));

        playSpetaniaSpiderFx(victim);
        if (attacker != null && attacker.isOnline() && !attacker.equals(victim)) {
            playSpetaniaSpiderFx(attacker);
        }

        // Title tylko dla osoby, która dostaje spętanie.
        int seconds = Math.max(1, (int) Math.round(durationMs / 1000.0D));
        ColorUtils.sendTitle(
            victim,
            "<gradient:#8B5A2B:#C4A484><bold>SPĘTANIE</bold></gradient></shadow>",
            "&7Sieć pająka &8— &cbez skoku / elytry &8(&e" + seconds + "s&8)",
            5, 45, 10
        );
    }

    private void playSpetaniaSpiderFx(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        var loc = player.getLocation();
        player.playSound(loc, Sound.ENTITY_SPIDER_AMBIENT, 1.0F, 0.75F);
        player.playSound(loc, Sound.ENTITY_SPIDER_HURT, 0.55F, 0.55F);
        player.playSound(loc, Sound.ENTITY_SPIDER_STEP, 0.85F, 0.7F);
        player.playSound(loc, Sound.BLOCK_HONEY_BLOCK_PLACE, 0.7F, 0.85F);
    }

    private boolean isBound(UUID uuid) {
        Long until = boundUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            boundUntil.remove(uuid);
            return false;
        }
        return true;
    }

    private void knockAway(Player attacker, Player victim) {
        Vector dir = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        if (dir.lengthSquared() < 0.01D) {
            dir = attacker.getLocation().getDirection();
        }
        dir.setY(0.0D);
        if (dir.lengthSquared() < 0.01D) {
            dir = new Vector(1, 0, 0);
        }
        attacker.setVelocity(dir.normalize().multiply(1.35D).setY(0.35D));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
    }
}
