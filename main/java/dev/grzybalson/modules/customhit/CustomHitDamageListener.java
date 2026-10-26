package dev.grzybalson.modules.customhit;

import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.modules.patrons.PatronManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Globalne mnożniki dmg/ochrony.
 *
 * multiplierdmg: mnoży obrażenia
 * multiplierarmor: dzieli obrażenia (większe = większa ochrona)
 */
public final class CustomHitDamageListener implements Listener {

    private static final double EPS = 1.0E-6;
    private static final String UPGRADE_TYPE_KEY = "holly_upgrade_type";
    private static final Enchantment CACHED_SHARPNESS = resolveSharpness();
    private static final Enchantment CACHED_PROTECTION = resolveProtection();

    // OPTYMALIZACJA: Lazy init zamiast static init - unika Class.forName() przy każdym loaderze
    private static volatile java.lang.reflect.Method DAMAGE_SOURCE_METHOD;
    private static volatile java.lang.reflect.Method DAMAGE_TYPE_METHOD;
    private static volatile java.lang.reflect.Method DAMAGE_TYPE_KEY_METHOD;
    private static volatile java.lang.reflect.Method CAUSING_ENTITY_METHOD;
    private static volatile java.lang.reflect.Method DIRECT_ENTITY_METHOD;
    private static volatile Class<?> DAMAGE_SOURCE_CLASS;
    private static volatile Class<?> DAMAGE_TYPE_CLASS;
    private static volatile boolean REFLECTION_INITIALIZED = false;

    private static final Material CACHED_MACE = Material.matchMaterial("MACE");

    // OPTYMALIZACJA + FIX: Anti-stackowanie hitów - cooldown 500ms na uderzenie tego samego victima (zwiększono z 400ms)
    private static final long HIT_COOLDOWN_MS = 500L;
    private final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();

    // OPTYMALIZACJA: Cleanup co 30s zamiast rosnącej mapy w nieskończoność
    private long lastCleanup = System.currentTimeMillis();
    private static final long CLEANUP_INTERVAL_MS = 30_000L;

    private final CustomHitsService service;

    public CustomHitDamageListener(CustomHitsService service) {
        this.service = service;
        // OPTYMALIZACJA: Initialize reflection raz przy konstruktorze zamiast przy każdym static load
        ensureReflectionInitialized();
    }

    private static void ensureReflectionInitialized() {
        if (REFLECTION_INITIALIZED) {
            return;
        }
        synchronized (CustomHitDamageListener.class) {
            if (REFLECTION_INITIALIZED) {
                return;
            }
            try {
                // Cache classes raz
                DAMAGE_SOURCE_CLASS = Class.forName("org.bukkit.damage.DamageSource");
                DAMAGE_TYPE_CLASS = Class.forName("org.bukkit.damage.DamageType");

                // Cache methods
                DAMAGE_SOURCE_METHOD = EntityDamageEvent.class.getMethod("getDamageSource");
                DAMAGE_TYPE_METHOD = DAMAGE_SOURCE_CLASS.getMethod("getDamageType");
                DAMAGE_TYPE_KEY_METHOD = DAMAGE_TYPE_CLASS.getMethod("getKey");
                CAUSING_ENTITY_METHOD = DAMAGE_SOURCE_CLASS.getMethod("getCausingEntity");
                DIRECT_ENTITY_METHOD = DAMAGE_SOURCE_CLASS.getMethod("getDirectEntity");
            } catch (Throwable ignored) {
                // Starsza wersja bez damage API - methods pozostaną null
            }
            REFLECTION_INITIALIZED = true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent ev && ev.getDamager() instanceof Player attacker
                && ev.getEntity() instanceof Player victim) {

            // OPTYMALIZACJA: Anti-stackowanie hitów - ignoruj hity w cooldownie
            long now = System.currentTimeMillis();
            UUID victimId = victim.getUniqueId();
            Long lastHit = lastHitTime.get(victimId);
            if (lastHit != null && (now - lastHit) < HIT_COOLDOWN_MS) {
                e.setCancelled(true);
                return;
            }
            lastHitTime.put(victimId, now);

            // OPTYMALIZACJA: Cleanup mapy co 30s
            if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
                lastCleanup = now;
                lastHitTime.entrySet().removeIf(entry -> now - entry.getValue() > HIT_COOLDOWN_MS * 3);
            }

            dev.grzybalson.anarchiagg.coach.CoachManager coachManager = service.getPlugin().getCoachManager();
            if (coachManager != null && coachManager.rollDefenseBlock(victim)) {
                coachManager.sendDefenseBlock(victim, attacker);
                e.setCancelled(true);
                e.setDamage(0.0);
                return;
            }
            if (hollyDamageControls(attacker)) {
                return;
            }
        }

        double dmgMul = service.getDamageMultiplier();
        double armMul = service.getArmorMultiplier();
        boolean isMace = isMaceAttack(e);
        boolean isMaceSmash = isMaceSmash(e);
        double maceMul = isMace ? service.getMaceDamageMultiplier() : 1.0;
        double maceSmashMul = isMaceSmash ? service.getMaceSmashDamageMultiplier() : 1.0;
        double dripMul = isDripstoneDamage(e) ? service.getDripstoneDamageMultiplier() : 1.0;
        double crystalMul = isEndCrystalDamage(e) ? service.getCrystalDamageMultiplier() : 1.0;
        double voidMul = isVoidDamage(e) ? service.getVoidDamageMultiplier() : 1.0;
        double sharpMul = service.getSharpnessMultiplier();
        double sharpLvlBonus = service.getSharpnessLevelBonus();
        double protectionLvlBonus = service.getProtectionLevelBonus();

        double absorptionMul = service.getAbsorptionMultiplier();
        double criticalMul = service.getCriticalMultiplier();
        double noCritMul = service.getNoCritMultiplier();
        boolean normalizedCustomSharpWeapon = normalizeCustomSharpWeaponDamage(e);
        double bonusDamageMul = getAdditiveBonusDamageMultiplier(e);
        boolean hasCoachManager = service.getPlugin().getCoachManager() != null;
        boolean hasFlameHub = service.getPlugin().getFlameHubCustomItemsManager() != null;

        // OPTYMALIZACJA: Early return jeśli wszystkie multipliers są default
        if (dmgMul == 1.0 && armMul == 1.0 && maceMul == 1.0 && maceSmashMul == 1.0 && dripMul == 1.0
                && crystalMul == 1.0 && voidMul == 1.0 && sharpMul == 1.0 && sharpLvlBonus == 0.0
                && protectionLvlBonus == 0.0 && absorptionMul == 1.0 && criticalMul == 1.0 && noCritMul == 1.0
                && bonusDamageMul == 1.0 && !normalizedCustomSharpWeapon && !hasConfiguredEffectModifiers()
                && !hasCoachManager && !hasFlameHub) {
            return;
        }

        double finBeforeAbs = getDamageBeforeAbsorption(e);
        if (finBeforeAbs <= EPS)
            return;

        double targetFinal = finBeforeAbs;
        if (sharpMul != 1.0 || sharpLvlBonus > 0.0) {
            double sharpnessBonusDelta = getSharpnessBonusDelta(e, finBeforeAbs, sharpMul, sharpLvlBonus);
            if (sharpnessBonusDelta > 0.0) {
                targetFinal += sharpnessBonusDelta;
            }
        }

        targetFinal *= dmgMul;
        if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ev
                && ev.getDamager() instanceof Player attacker && ev.getEntity() instanceof Player victim) {
            targetFinal += getEffectModifierDelta(attacker, "strength", service.getEffectModifier("strength"));
            targetFinal += getEffectModifierDelta(attacker, "weakness", service.getEffectModifier("weakness"));
            double resMod = service.getEffectModifier("resistance");
            if (resMod != 0.0) {
                PotionEffect resEffect = victim.getPotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                if (resEffect != null) {
                    int level = resEffect.getAmplifier() + 1;
                    double resFactor = Math.max(0.2D, 1.0D - (0.20D * level));
                    targetFinal *= resFactor;
                }
            }

            if (victim.hasMetadata("bear_fur_active") || victim.hasMetadata("bear_fur")) {
                targetFinal *= 0.67D;
            }

            if (protectionLvlBonus > 0.0) {
                double protectionReduction = getAverageProtectionLevel(victim) * protectionLvlBonus;
                if (protectionReduction > 0.0) {
                    targetFinal -= Math.min(protectionReduction, targetFinal * 0.45D);
                }
            }

            // Diax Sharp 5 lekko gorszy od neth Sharp 6; diax set bierze troche wiecej.
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            if (hand != null && hand.getType().name().startsWith("DIAMOND_")) {
                double swordMul = service.getDiamondSwordMultiplier();
                if (swordMul != 1.0) {
                    targetFinal *= swordMul;
                }
            }
            double armorTakenMul = diamondArmorTakenFactor(victim, service.getDiamondArmorTakenMultiplier());
            if (armorTakenMul != 1.0) {
                targetFinal *= armorTakenMul;
            }

            boolean criticalAttack = isCriticalAttack(attacker);
            if (criticalAttack && criticalMul != 1.0) {
                targetFinal *= criticalMul;
            } else if (!criticalAttack && noCritMul != 1.0) {
                targetFinal *= noCritMul;
            }

            if (usesElytraDamageProfile(victim)) {
                targetFinal *= service.getElytraDamageMultiplier();
                double elytraMax = service.getElytraMaxDamage();
                if (elytraMax >= 0.0 && targetFinal > elytraMax) {
                    targetFinal = elytraMax;
                }
            }

            if (hasAbsorption(victim) && absorptionMul != 1.0) {
                targetFinal *= absorptionMul;
            }

            dev.grzybalson.anarchiagg.coach.CoachManager coachManager = service.getPlugin().getCoachManager();
            if (coachManager != null && attacker.getAttackCooldown() >= 0.8f) {
                ItemStack mainHand = attacker.getInventory().getItemInMainHand();
                if (mainHand != null && mainHand.getType() != Material.AIR) {
                    double coachStrengthBonus = coachManager.getStrengthDamageBonusPercent(attacker);
                    if (coachStrengthBonus > 0.0) {
                        targetFinal *= (1.0 + (coachStrengthBonus / 100.0));
                    }

                    double coachExtraHit = coachManager.rollStrengthExtraHitDamage(attacker);
                    if (coachExtraHit > 0.0) {
                        targetFinal += coachExtraHit;
                        coachManager.sendStrengthExtraHitSubtitle(attacker, coachExtraHit);
                    }

                    if (coachManager.tryApplyStrengthHeal(attacker)) {
                        coachManager.sendStrengthHealSubtitle(attacker);
                    }
                }
            }
        }
        if (maceMul != 1.0)
            targetFinal *= maceMul;
        if (maceSmashMul != 1.0)
            targetFinal *= maceSmashMul;
        if (dripMul != 1.0)
            targetFinal *= dripMul;
        if (crystalMul != 1.0)
            targetFinal *= crystalMul;
        if (voidMul != 1.0)
            targetFinal *= voidMul;
        if (armMul != 1.0)
            targetFinal = targetFinal / armMul;
        if (bonusDamageMul != 1.0)
            targetFinal *= bonusDamageMul;

        // FlameHub CustomItems (Yoda/Vader) — aplikowane w customhit, nie w HIGH listenerze.
        var flameHub = service.getPlugin().getFlameHubCustomItemsManager();
        if (flameHub != null) {
            if (e.getEntity() instanceof Player victim && flameHub.isYodaActive(victim)) {
                double percent = Math.max(0.0, Math.min(100.0, flameHub.getYodaDamageReductionPercent()));
                targetFinal *= (1.0 - (percent / 100.0));
                flameHub.playYodaHitFx(victim);
            }
            Player flameAttacker = resolveFlameHubAttacker(e);
            if (flameAttacker != null && flameHub.isVaderActive(flameAttacker)) {
                double bonusPercent = Math.max(0.0, flameHub.getVaderDamageIncreasePercent());
                targetFinal *= (1.0 + (bonusPercent / 100.0));
                flameHub.playVaderHitFx(flameAttacker, e.getEntity());
            }
        }

        if (targetFinal < 0.0)
            targetFinal = 0.0;

        // OPTYMALIZACJA + FEATURE: Randomizacja dmg 5-8% - każde uderzenie różni się
        if (targetFinal > 0.0 && e instanceof EntityDamageByEntityEvent) {
            double randomBonus = 1.0 + (java.util.concurrent.ThreadLocalRandom.current().nextDouble(0.05, 0.08));
            targetFinal *= randomBonus;
        }

        setDamageToMatchFinalBeforeAbsorption(e, targetFinal);
    }

    private static Player resolveFlameHubAttacker(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        if (byEntity.getDamager() instanceof Player player) {
            return player;
        }
        if (byEntity.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private double getAdditiveBonusDamageMultiplier(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent byEntity))
            return 1.0;
        if (!(byEntity.getDamager() instanceof Player attacker))
            return 1.0;

        double bonus = 0.0;
        PatronManager patronManager = service.getPlugin().getPatronManager();
        if (patronManager != null) {
            bonus += patronManager.getAresDamageMultiplier(attacker) - 1.0;
        }
        var szaman = service.getPlugin().getSzamanManager();
        if (szaman != null) {
            bonus += szaman.getPerkValue(attacker.getUniqueId(), dev.grzybalson.modules.szaman.PerkType.STRENGTH) / 100.0;
        }
        double multiplier = 1.0 + bonus;
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier) || multiplier <= 0.0)
            return 1.0;
        return multiplier;
    }

    private static boolean normalizeCustomSharpWeaponDamage(EntityDamageEvent e) {
        if (!(e instanceof EntityDamageByEntityEvent byEntity))
            return false;
        if (!(byEntity.getDamager() instanceof Player attacker))
            return false;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (!isCustomSharpWeapon(hand))
            return false;

        int sharpnessLevel = getSharpnessLevel(hand);
        double cooldown = clamp(attacker.getAttackCooldown(), 0.0, 1.0);
        double cooldownMultiplier = 0.2D + (0.8D * cooldown);
        double critMultiplier = isCriticalAttack(attacker) ? 1.5D : 1.0D;
        double targetRawDamage = (8.0D * cooldownMultiplier * critMultiplier)
                + computeVanillaSharpnessBonus(sharpnessLevel, cooldown);
        if (e.getDamage() >= targetRawDamage - EPS) {
            return false;
        }

        e.setDamage(targetRawDamage);
        return true;
    }

    private static boolean isCustomSharpWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (getSharpnessLevel(item) <= 0) {
            return false;
        }
        if (isUpgradeSword(item)) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null
                && (meta.hasDisplayName()
                        || meta.hasLore()
                        || meta.hasCustomModelData()
                        || !meta.getPersistentDataContainer().getKeys().isEmpty());
    }

    private static boolean isUpgradeSword(ItemStack item) {
        return "SWORD".equalsIgnoreCase(NBTUtils.getString(item, UPGRADE_TYPE_KEY));
    }

    private boolean hasConfiguredEffectModifiers() {
        return service.getEffectModifier("strength") != 0.0
                || service.getEffectModifier("weakness") != 0.0
                || service.getEffectModifier("resistance") != 0.0;
    }

    private static double getEffectModifierDelta(Player player, String effectName, double modifier) {
        if (modifier == 0.0 || player == null)
            return 0.0;

        PotionEffectType type = getPotionEffectType(effectName);
        if (type == null)
            return 0.0;

        PotionEffect effect = player.getPotionEffect(type);
        if (effect == null)
            return 0.0;

        return modifier * (effect.getAmplifier() + 1);
    }

    private static PotionEffectType getPotionEffectType(String effectName) {
        try {
            return switch (effectName.toLowerCase(Locale.ROOT)) {
                case "strength" -> PotionEffectType.INCREASE_DAMAGE;
                case "weakness" -> PotionEffectType.WEAKNESS;
                case "resistance" -> PotionEffectType.DAMAGE_RESISTANCE;
                default -> null;
            };
        } catch (Throwable ignored) {
            try {
                return PotionEffectType.getByName(effectName.toUpperCase(Locale.ROOT));
            } catch (Throwable ignoredAgain) {
                return null;
            }
        }
    }

    private static boolean hasAbsorption(Player player) {
        try {
            return player != null && player.getAbsorptionAmount() > EPS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasElytra(Player player) {
        if (player == null)
            return false;
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null)
            return false;
        ItemStack chestplate = equipment.getChestplate();
        return chestplate != null && chestplate.getType() == Material.ELYTRA;
    }

    private static boolean usesElytraDamageProfile(Player player) {
        return player != null && hasElytra(player);
    }

    private static boolean isCriticalAttack(Player attacker) {
        if (attacker == null)
            return false;
        if (attacker.isOnGround())
            return false;
        if (attacker.getFallDistance() <= 0.0F)
            return false;
        if (attacker.isInsideVehicle())
            return false;
        if (attacker.isClimbing())
            return false;
        if (attacker.isInWater())
            return false;
        if (attacker.isGliding())
            return false;
        return !attacker.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    private static double getDamageBeforeAbsorption(EntityDamageEvent e) {
        if (e == null)
            return 0.0;
        double raw = e.getDamage();
        if (Double.isNaN(raw) || Double.isInfinite(raw))
            return 0.0;
        return Math.max(0.0, raw);
    }

    private static boolean isEndCrystalDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof EnderCrystal) {
            return true;
        }

        Entity causing = getCausingEntity(e);
        if (causing instanceof EnderCrystal) {
            return true;
        }

        Entity direct = getDirectEntity(e);
        return direct instanceof EnderCrystal;
    }

    private static boolean isVoidDamage(EntityDamageEvent e) {
        try {
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID)
                return true;
        } catch (Throwable ignored) {
        }
        String k = getDamageTypeKey(e);
        if (k != null) {
            String key = k.toLowerCase(Locale.ROOT);
            return key.contains("out_of_world") || key.contains("void");
        }
        return false;
    }

    private static boolean isDripstoneDamage(EntityDamageEvent e) {
        String k = getDamageTypeKey(e);
        if (k != null) {
            String key = k.toLowerCase(Locale.ROOT);
            if (key.contains("stalagmite") || key.contains("stalactite") || key.contains("dripstone"))
                return true;
        }

        if (e instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof FallingBlock fb) {
                Material m = fb.getBlockData() != null ? fb.getBlockData().getMaterial() : null;
                if (m != null && m == Material.POINTED_DRIPSTONE)
                    return true;
            }
        }

        if (e.getCause() == EntityDamageEvent.DamageCause.CONTACT) {
            try {
                Location loc = e.getEntity().getLocation();
                if (loc == null || loc.getWorld() == null)
                    return false;

                if (isPointedDripstone(loc))
                    return true;
                if (isPointedDripstone(loc.clone().add(0, -1, 0)))
                    return true;
                if (isPointedDripstone(loc.clone().add(0, 1, 0)))
                    return true;
                if (isPointedDripstone(loc.clone().add(1, 0, 0)))
                    return true;
                if (isPointedDripstone(loc.clone().add(-1, 0, 0)))
                    return true;
                if (isPointedDripstone(loc.clone().add(0, 0, 1)))
                    return true;
                if (isPointedDripstone(loc.clone().add(0, 0, -1)))
                    return true;
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private static boolean isPointedDripstone(Location loc) {
        try {
            return loc.getBlock() != null && loc.getBlock().getType() == Material.POINTED_DRIPSTONE;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object getDamageSource(EntityDamageEvent e) {
        if (DAMAGE_SOURCE_METHOD == null) return null;
        try {
            return DAMAGE_SOURCE_METHOD.invoke(e);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Entity getCausingEntity(EntityDamageEvent e) {
        try {
            Object damageSource = getDamageSource(e);
            if (damageSource == null || CAUSING_ENTITY_METHOD == null)
                return null;
            Object causingEntity = CAUSING_ENTITY_METHOD.invoke(damageSource);
            if (causingEntity instanceof Entity entity)
                return entity;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Entity getDirectEntity(EntityDamageEvent e) {
        try {
            Object damageSource = getDamageSource(e);
            if (damageSource == null || DIRECT_ENTITY_METHOD == null)
                return null;
            Object directEntity = DIRECT_ENTITY_METHOD.invoke(damageSource);
            if (directEntity instanceof Entity entity)
                return entity;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String getDamageTypeKey(EntityDamageEvent e) {
        try {
            Object damageSource = getDamageSource(e);
            if (damageSource == null || DAMAGE_TYPE_METHOD == null)
                return null;
            Object damageType = DAMAGE_TYPE_METHOD.invoke(damageSource);
            if (damageType == null || DAMAGE_TYPE_KEY_METHOD == null)
                return null;
            Object key = DAMAGE_TYPE_KEY_METHOD.invoke(damageType);
            if (key == null)
                return null;
            return String.valueOf(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Średni Protection na kawałku — suma 40×4=160 * 0.05 zjadała cały hit (~1 serce). */
    private static double getAverageProtectionLevel(Player player) {
        if (CACHED_PROTECTION == null || player == null)
            return 0.0;
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null)
            return 0.0;
        int total = 0;
        int pieces = 0;
        int helmet = getProtectionLevel(equipment.getHelmet());
        int chest = getProtectionLevel(equipment.getChestplate());
        int legs = getProtectionLevel(equipment.getLeggings());
        int boots = getProtectionLevel(equipment.getBoots());
        if (isWorn(equipment.getHelmet())) {
            total += helmet;
            pieces++;
        }
        if (isWorn(equipment.getChestplate())) {
            total += chest;
            pieces++;
        }
        if (isWorn(equipment.getLeggings())) {
            total += legs;
            pieces++;
        }
        if (isWorn(equipment.getBoots())) {
            total += boots;
            pieces++;
        }
        if (pieces <= 0) {
            return 0.0;
        }
        return total / (double) pieces;
    }

    /** 0–1 udział diax w zbroi × (takenMul − 1) + 1 → pełny diax bierze takenMul. */
    private static double diamondArmorTakenFactor(Player victim, double takenMul) {
        if (victim == null || takenMul == 1.0) {
            return 1.0;
        }
        EntityEquipment equipment = victim.getEquipment();
        if (equipment == null) {
            return 1.0;
        }
        int diamond = 0;
        int ranked = 0;
        for (ItemStack piece : new ItemStack[]{
            equipment.getHelmet(), equipment.getChestplate(), equipment.getLeggings(), equipment.getBoots()
        }) {
            if (piece == null || piece.getType().isAir()) {
                continue;
            }
            String name = piece.getType().name();
            if (name.startsWith("DIAMOND_")) {
                diamond++;
                ranked++;
            } else if (name.startsWith("NETHERITE_")) {
                ranked++;
            }
        }
        if (ranked <= 0 || diamond <= 0) {
            return 1.0;
        }
        double ratio = diamond / (double) ranked;
        return 1.0 + (takenMul - 1.0) * ratio;
    }

    private static boolean isWorn(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    private static int getProtectionLevel(ItemStack item) {
        if (CACHED_PROTECTION == null || item == null || item.getType() == Material.AIR)
            return 0;
        try {
            return Math.max(0, item.getEnchantmentLevel(CACHED_PROTECTION));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Enchantment resolveProtection() {
        try {
            Object value = Enchantment.class.getField("PROTECTION").get(null);
            if (value instanceof Enchantment protection)
                return protection;
        } catch (Throwable ignored) {
        }
        try {
            Object value = Enchantment.class.getField("PROTECTION_ENVIRONMENTAL").get(null);
            if (value instanceof Enchantment protection)
                return protection;
        } catch (Throwable ignored) {
        }
        try {
            return Enchantment.getByKey(NamespacedKey.minecraft("protection"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double getSharpnessBonusDelta(EntityDamageEvent e, double finBeforeAbs, double sharpMul,
            double sharpLvlBonus) {
        if (!(e instanceof EntityDamageByEntityEvent byEntity))
            return 0.0;
        if (!(byEntity.getDamager() instanceof Player attacker))
            return 0.0;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR)
            return 0.0;

        int sharpnessLevel = getSharpnessLevel(hand);
        if (sharpnessLevel <= 0)
            return 0.0;

        double cooldown = clamp(attacker.getAttackCooldown(), 0.0, 1.0);
        double vanillaRawBonus = computeVanillaSharpnessBonus(sharpnessLevel, cooldown);
        double extraRawFromMultiplier = Math.max(0.0, (vanillaRawBonus * sharpMul) - vanillaRawBonus);

        double rawDamage = e.getDamage();
        double reductionRatio = rawDamage > EPS ? (finBeforeAbs / rawDamage) : 1.0;
        reductionRatio = clamp(reductionRatio, 0.0, 1.0);

        double extraFinalFromMultiplier = extraRawFromMultiplier * reductionRatio;
        double extraFinalFromLevels = Math.max(0.0, sharpnessLevel - 1) * sharpLvlBonus * (0.2D + (0.8D * cooldown));

        return Math.max(0.0, extraFinalFromMultiplier + extraFinalFromLevels);
    }

    private static double computeVanillaSharpnessBonus(int sharpnessLevel, double cooldown) {
        double vanillaBonus = 0.5D * sharpnessLevel + 0.5D;
        double cooldownMultiplier = 0.2D + (0.8D * cooldown);
        return vanillaBonus * cooldownMultiplier;
    }

    private static int getSharpnessLevel(ItemStack item) {
        if (CACHED_SHARPNESS == null || item == null)
            return 0;
        try {
            return Math.max(0, item.getEnchantmentLevel(CACHED_SHARPNESS));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Enchantment resolveSharpness() {
        try {
            Object value = Enchantment.class.getField("SHARPNESS").get(null);
            if (value instanceof Enchantment sharpness)
                return sharpness;
        } catch (Throwable ignored) {
        }
        try {
            Object value = Enchantment.class.getField("DAMAGE_ALL").get(null);
            if (value instanceof Enchantment sharpness)
                return sharpness;
        } catch (Throwable ignored) {
        }
        try {
            return Enchantment.getByKey(NamespacedKey.minecraft("sharpness"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void setDamageToMatchFinalBeforeAbsorption(EntityDamageEvent e, double targetFinalBeforeAbs) {
        if (targetFinalBeforeAbs <= 0.0) {
            e.setDamage(0.0);
            return;
        }

        double cur = getDamageBeforeAbsorption(e);
        if (cur <= EPS) {
            return;
        }

        if (Math.abs(cur - targetFinalBeforeAbs) <= 1.0E-3)
            return;

        double ratio = targetFinalBeforeAbs / cur;
        if (Double.isNaN(ratio) || Double.isInfinite(ratio))
            return;

        double newBase = e.getDamage() * ratio;
        if (Double.isNaN(newBase) || Double.isInfinite(newBase) || newBase < 0.0)
            newBase = 0.0;
        if (newBase > 4096.0)
            newBase = 4096.0;
        e.setDamage(newBase);
    }

    private boolean hollyDamageControls(Player attacker) {
        if (attacker == null) {
            return false;
        }
        var serviceHd = this.service.getPlugin().getHollyDamageService();
        if (serviceHd == null || serviceHd.getConfig() == null) {
            return false;
        }
        return serviceHd.getConfig().controlsMelee(attacker.getInventory().getItemInMainHand());
    }

    private static boolean isMaceAttack(EntityDamageEvent e) {
        if (CACHED_MACE == null)
            return false;
        if (!(e instanceof EntityDamageByEntityEvent byEntity))
            return false;
        if (!(byEntity.getDamager() instanceof Player attacker))
            return false;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        return hand != null && hand.getType() == CACHED_MACE;
    }

    private static boolean isMaceSmash(EntityDamageEvent e) {
        if (CACHED_MACE == null)
            return false;
        String k = getDamageTypeKey(e);
        if (k == null)
            return false;
        String lower = k.toLowerCase(Locale.ROOT);
        return lower.contains("mace_smash");
    }

    private static java.lang.reflect.Method resolveDamageSourceMethod() {
        ensureReflectionInitialized();
        return DAMAGE_SOURCE_METHOD;
    }

    private static java.lang.reflect.Method resolveDamageTypeMethod() {
        ensureReflectionInitialized();
        return DAMAGE_TYPE_METHOD;
    }

    private static java.lang.reflect.Method resolveDamageTypeKeyMethod() {
        ensureReflectionInitialized();
        return DAMAGE_TYPE_KEY_METHOD;
    }

    private static java.lang.reflect.Method resolveCausingEntityMethod() {
        ensureReflectionInitialized();
        return CAUSING_ENTITY_METHOD;
    }

    private static java.lang.reflect.Method resolveDirectEntityMethod() {
        ensureReflectionInitialized();
        return DIRECT_ENTITY_METHOD;
    }
}
