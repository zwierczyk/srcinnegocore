package dev.grzybalson.anarchiagg.szafa;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.szafa.RegionHandler;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyArmorHandler;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import dev.grzybalson.anarchiagg.szafa.papugi.PapugiHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyHandler;
import dev.grzybalson.modules.itemsync.event.PlayerInventoryLoadedEvent;
import dev.grzybalson.utils.PlayerHealthBase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Goat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class BoostHandler
implements Listener {
    private final JavaPlugin plugin;
    /** Per-instance — static powodowalo, ze MSLF/oghub/anarchiagg nadpisywaly sobie nawzajem handler. */
    private final KostiumyHandler kostiumyHandler;
    private final PetyHandler petyHandler;
    private final PapugiHandler papugiHandler;
    private final RegionHandler regionHandler;
    private final Map<UUID, Double> extraHearts = new HashMap<UUID, Double>();
    public record BlockPos(UUID worldId, int x, int y, int z) {
        public static BlockPos of(Block block) {
            return new BlockPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
        public static BlockPos of(Location location) {
            return new BlockPos(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
    public static final Set<BlockPos> temporarySnow = ConcurrentHashMap.newKeySet();
    /** Stała nazwa modifiera — po UUID na nowszych Paper bywa zawodne (mnożenie serc). */
    public static final String HEALTH_MODIFIER_NAME = "ExtraHearts";
    private static final UUID HEALTH_MODIFIER_UUID;
    private static Economy economy;
    private static final List<Material> DOUBLE_DROP_BLOCKS;
    private final Map<UUID, Long> zakazny = new HashMap<UUID, Long>();
    private final Map<UUID, Long> sneakCooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Integer> sneakCounter = new HashMap<UUID, Integer>();
    private final Map<UUID, Long> reflectActiveUntil = new HashMap<UUID, Long>();
    private static final Map<UUID, BossBar> sneakBossBars;
    private static final Map<UUID, BukkitTask> sneakCountdownTasks;
    private static final Map<UUID, BukkitTask> reflectBossBarTasks;
    private static final Map<UUID, BukkitTask> cobwebClearTasks;
    private static final Map<UUID, BukkitTask> uczenCooldownTasks;
    private static final Map<UUID, Double> dropKeysMultipliers;
    private final Map<UUID, Long> hitEffectCooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Long> dolphinCooldown = new HashMap<UUID, Long>();
    private final Map<UUID, UUID> lastDamager = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> lastDamageTime = new HashMap<UUID, Long>();
    private final Map<UUID, PlayerCache> playerCache = new ConcurrentHashMap<UUID, PlayerCache>();
    private final Map<UUID, Long> ninjaFrozenUntil = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, AtomicInteger> goatWeaknessHits = new ConcurrentHashMap<UUID, AtomicInteger>();
    private final Map<UUID, Integer> goatWeaknessRequired = new ConcurrentHashMap<UUID, Integer>();
    private static final long KILL_ASSIST_TIME = 90000L;
    private static final long REFLECT_DURATION_MS = 10000L;
    private static final long COOLDOWN_DURATION_MS = 120000L;
    private static final long COBWEB_CLEAR_DURATION_TICKS = 60L;
    private static final int COBWEB_CLEAR_RADIUS = 5;

    public BoostHandler(JavaPlugin plugin, KostiumyHandler kostiumyHandler, PetyHandler petyHandler, PapugiHandler papugiHandler, RegionHandler regionHandler) {
        this.plugin = plugin;
        this.kostiumyHandler = kostiumyHandler;
        this.petyHandler = petyHandler;
        this.papugiHandler = papugiHandler;
        this.regionHandler = regionHandler;
        this.startEffectTasks();
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                economy = Objects.requireNonNull(Bukkit.getServer().getServicesManager().getRegistration(Economy.class)).getProvider();
            }
            catch (Exception e) {
                plugin.getLogger().warning("B\u0142\u0105d inicjalizacji Vault Economy: " + e.getMessage());
            }
        }
        this.startDataUpdater();
    }

    private void startDataUpdater() {
        new BukkitRunnable(){

            @Override
            public void run() {
                // Tylko online z już znanym cache / nowi — nie skanuj „pustych” 400 osób w kółko.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    BoostHandler.this.updatePlayerCache(player);
                }
            }
        }.runTaskTimer(this.plugin, 0L, 100L);
    }

    private void updatePlayerCache(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerCache cache = this.playerCache.computeIfAbsent(uuid, k -> new PlayerCache());
        double previousHearts = cache.totalHearts;

        cache.kostiumBoosts = this.fetchKostiumBoosts(uuid);
        cache.petBoosts = this.fetchPetBoosts(uuid);
        cache.papugaBonus = this.fetchPapugaBonus(uuid);
        cache.mikolaj = cache.kostiumBoosts != null && cache.kostiumBoosts.mikolaj();
        cache.nurek = cache.kostiumBoosts != null && cache.kostiumBoosts.nurek();
        cache.totalDamageBoost = (cache.kostiumBoosts != null ? cache.kostiumBoosts.damage() : 0.0) + (cache.petBoosts != null ? cache.petBoosts.damage() : 0.0) + (cache.papugaBonus != null ? cache.papugaBonus.damageBonus() : 0.0);
        cache.totalDamageMultiplier = 1.0 + (cache.papugaBonus != null ? cache.papugaBonus.damageMultiplier() : 0.0) + (cache.kostiumBoosts != null ? cache.kostiumBoosts.damagePercent() / 100.0 : 0.0);
        cache.totalReduction = (cache.kostiumBoosts != null ? cache.kostiumBoosts.damageReduction() : 0.0) + (cache.petBoosts != null ? cache.petBoosts.damageReduction() : 0.0);
        cache.totalHearts = (cache.kostiumBoosts != null ? (double)cache.kostiumBoosts.hearts() : 0.0)
            + (cache.petBoosts != null ? (double)cache.petBoosts.hearts() : 0.0)
            + (cache.kostiumBoosts != null ? cache.kostiumBoosts.extraHp() / 2.0 : 0.0);
        cache.negativeEffectsDisabled = cache.kostiumBoosts != null && cache.kostiumBoosts.negativeEffectsDisable() || cache.petBoosts != null && cache.petBoosts.negativeEffectsDisable();
        cache.noHunger = cache.kostiumBoosts != null && cache.kostiumBoosts.noHunger() || cache.petBoosts != null && cache.petBoosts.noHunger();
        if (cache.noHunger) {
            fillHunger(player);
        }

        ArrayList<String> effects = new ArrayList<String>();
        if (cache.kostiumBoosts != null) {
            effects.addAll(cache.kostiumBoosts.effects());
        }
        if (cache.petBoosts != null) {
            effects.addAll(cache.petBoosts.effects());
        }
        if (cache.papugaBonus != null) {
            effects.addAll(cache.papugaBonus.effects());
        }
        cache.combinedEffects = effects.isEmpty() ? List.of() : List.copyOf(effects);

        double dropKeysMultiplier = cache.kostiumBoosts != null ? cache.kostiumBoosts.dropKeysMultiplier() : 1.0;
        if (dropKeysMultiplier > 0.0 && Double.compare(dropKeysMultiplier, 1.0) != 0) {
            dropKeysMultipliers.put(uuid, dropKeysMultiplier);
        } else {
            dropKeysMultipliers.remove(uuid);
        }

        if (Double.compare(previousHearts, cache.totalHearts) != 0) {
            if (cache.totalHearts > 0.0) {
                this.applyHearts(player, cache.totalHearts);
            } else {
                this.removeHearts(player);
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void applyDamageBoost(EntityDamageByEntityEvent event) {
        Entity damagerEntity = event.getDamager();
        Player damagerPlayer = null;
        if (damagerEntity instanceof Player player) {
            damagerPlayer = player;
        } else if (damagerEntity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            damagerPlayer = shooter;
        }
        if (damagerPlayer == null) {
            return;
        }
        if (this.isLocationBlocked(damagerPlayer.getLocation())) {
            return;
        }
        if (event.getEntity() instanceof Player victim && this.isLocationBlocked(victim.getLocation())) {
            return;
        }
        PlayerCache cache = this.playerCache.get(damagerPlayer.getUniqueId());
        if (cache == null) {
            return;
        }
        double totalBoost = cache.totalDamageBoost;
        double damageMultiplier = cache.totalDamageMultiplier;
        double attackFast = cache.petBoosts != null ? cache.petBoosts.attackfast() : 0.0;
        double targetSpeed = attackFast > 0.0 ? 4.0 + attackFast * 4.0 : 4.0;
        AttributeInstance attr = damagerPlayer.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr != null && attr.getBaseValue() != targetSpeed) {
            attr.setBaseValue(targetSpeed);
        }
        if (!this.hollyDamageOwnsMelee(damagerPlayer, event.getEntity())) {
            double newDamage = event.getDamage() + totalBoost;
            if (cache.nurek && damagerPlayer.getLocation().getBlock().getType() == Material.WATER) {
                newDamage *= 1.2;
            }
            event.setDamage(newDamage * damageMultiplier);
        }
        if (event.getEntity() instanceof Player victim && !victim.equals(damagerPlayer)
            && cache.kostiumBoosts != null && cache.kostiumBoosts.hiddenWeakness()) {
            this.tryHiddenWeakness(damagerPlayer.getUniqueId(), victim);
        }
    }

    public double extraAttackDamage(Player player) {
        if (player == null) {
            return 0.0;
        }
        PlayerCache cache = this.playerCache.get(player.getUniqueId());
        return cache != null ? cache.totalDamageBoost : 0.0;
    }

    public double attackDamageMultiplier(Player player) {
        if (player == null) {
            return 1.0;
        }
        PlayerCache cache = this.playerCache.get(player.getUniqueId());
        return cache != null ? cache.totalDamageMultiplier : 1.0;
    }

    public boolean nurekWaterBoost(Player player) {
        if (player == null) {
            return false;
        }
        PlayerCache cache = this.playerCache.get(player.getUniqueId());
        return cache != null && cache.nurek && player.getLocation().getBlock().getType() == Material.WATER;
    }

    private boolean hollyDamageOwnsMelee(Player attacker, Entity victim) {
        if (!(victim instanceof Player) || attacker == null) {
            return false;
        }
        if (!(this.plugin instanceof HollyCore core)) {
            return false;
        }
        var service = core.getHollyDamageService();
        if (service == null || service.getConfig() == null) {
            return false;
        }
        return service.getConfig().controlsMelee(attacker.getInventory().getItemInMainHand());
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void applyDamageReduction(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (this.isLocationBlocked(victim.getLocation())) {
            return;
        }
        Entity damagerEntity = event.getDamager();
        if (damagerEntity instanceof Player damager && this.isLocationBlocked(damager.getLocation())) {
            return;
        }
        if (damagerEntity instanceof Projectile projectile
            && projectile.getShooter() instanceof Player shooter
            && this.isLocationBlocked(shooter.getLocation())) {
            return;
        }
        PlayerCache cache = this.playerCache.get(victim.getUniqueId());
        if (cache != null) {
            event.setDamage(Math.max(0.0, event.getDamage() - cache.totalReduction));
        }
    }

    private void startEffectTasks() {
        // Negative effects need a frequent guard, but hearts do not need to be rewritten every second.
        Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> {
            for (Map.Entry<UUID, PlayerCache> entry : this.playerCache.entrySet()) {
                PlayerCache cache = entry.getValue();
                if (cache == null || (!cache.negativeEffectsDisabled && !cache.noHunger)) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (cache.negativeEffectsDisabled) {
                    this.handleNegativeEffectsDisable(player, true);
                }
                if (cache.noHunger) {
                    fillHunger(player);
                }
            }
        }, 0L, 40L);

        // Slow self-healing check in case another plugin removed our health modifier.
        Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> {
            for (Map.Entry<UUID, PlayerCache> entry : this.playerCache.entrySet()) {
                PlayerCache cache = entry.getValue();
                if (cache == null) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (cache.totalHearts > 0.0) {
                    this.applyHearts(player, cache.totalHearts);
                } else {
                    this.removeHearts(player);
                }
            }
        }, 100L, 200L);

        Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> {
            for (Map.Entry<UUID, PlayerCache> entry : this.playerCache.entrySet()) {
                PlayerCache cache = entry.getValue();
                if (cache == null || cache.combinedEffects.isEmpty()) {
                    continue;
                }
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    this.applyEffects(player, cache.combinedEffects);
                }
            }
        }, 0L, 80L);
    }

    private KostiumyHandler.BoostsData fetchKostiumBoosts(UUID uuid) {
        KostiumyHandler.KostiumData data;
        if (kostiumyHandler == null || kostiumyHandler.getDatabase() == null) {
            return null;
        }
        String active = kostiumyHandler.getDatabase().getActiveKostium(uuid);
        if (active != null && (data = kostiumyHandler.getKostiumy().get(active)) != null) {
            return data.boosts();
        }
        return null;
    }

    private PetyHandler.BoostsData fetchPetBoosts(UUID uuid) {
        PetyHandler.PetData data;
        if (this.petyHandler == null || this.petyHandler.getDatabase() == null) {
            return null;
        }
        String active = this.petyHandler.getDatabase().getActivePet(uuid);
        if (active != null && (data = this.petyHandler.getPetData(active)) != null) {
            return data.boosts();
        }
        return null;
    }

    public KostiumyHandler.BoostsData getKostiumBoosts(UUID uuid) {
        PlayerCache cache = this.playerCache.get(uuid);
        return cache != null ? cache.kostiumBoosts : this.fetchKostiumBoosts(uuid);
    }

    public PetyHandler.BoostsData getPetBoosts(UUID uuid) {
        PlayerCache cache = this.playerCache.get(uuid);
        return cache != null ? cache.petBoosts : this.fetchPetBoosts(uuid);
    }

    private PapugiHandler.BonusData fetchPapugaBonus(UUID uuid) {
        if (this.papugiHandler == null || this.papugiHandler.getDatabase() == null) {
            return null;
        }
        String active = this.papugiHandler.getDatabase().getActivePapuga(uuid);
        if (active == null) {
            return null;
        }
        String bonusKey = this.papugiHandler.getDatabase().getActivePapugaBonus(uuid);
        return bonusKey != null ? this.papugiHandler.getBonus(bonusKey) : null;
    }

    /**
     * Mnoznik szansy na klucze z dropu pochodzacy z aktywnego kostiumu (1.0 = brak zmiany).
     */
    public static double getDropKeysMultiplier(UUID uuid) {
        Double multiplier = uuid == null ? null : dropKeysMultipliers.get(uuid);
        return multiplier == null ? 1.0 : multiplier;
    }

    private boolean isSneakDamageReflectActiveFor(Player player) {
        KostiumyHandler.BoostsData boosts = this.getKostiumBoosts(player.getUniqueId());
        return boosts != null && boosts.sneakBoost();
    }

    private boolean isCobwebClearActiveFor(Player player) {
        KostiumyHandler.BoostsData boosts = this.getKostiumBoosts(player.getUniqueId());
        return boosts != null && boosts.cobwebClear();
    }

    private boolean isNinjaFreezeActiveFor(Player player) {
        KostiumyHandler.BoostsData boosts = this.getKostiumBoosts(player.getUniqueId());
        return boosts != null && boosts.ninjaFreeze();
    }

    private boolean isGoatRamActiveFor(Player player) {
        KostiumyHandler.BoostsData boosts = this.getKostiumBoosts(player.getUniqueId());
        return boosts != null && boosts.goatRam();
    }

    private SneakAbility resolveSneakAbility(Player player) {
        if (this.isCobwebClearActiveFor(player)) {
            return SneakAbility.UCZEN;
        }
        if (this.isSneakDamageReflectActiveFor(player)) {
            return SneakAbility.MIM;
        }
        if (this.isNinjaFreezeActiveFor(player)) {
            return SneakAbility.NINJA;
        }
        if (this.isGoatRamActiveFor(player)) {
            return SneakAbility.GOAT;
        }
        return null;
    }

    private boolean isHitEffectActiveFor(Player player) {
        KostiumyHandler.BoostsData boosts = this.getKostiumBoosts(player.getUniqueId());
        return boosts != null && boosts.zakazny();
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player target;
        Player damager;
        block8: {
            block7: {
                damager = event.getPlayer();
                Entity targetEntity = event.getRightClicked();
                if (!(targetEntity instanceof Player)) break block7;
                target = (Player)targetEntity;
                if (!targetEntity.equals(damager)) break block8;
            }
            return;
        }
        if (!this.isHitEffectActiveFor(damager)) {
            return;
        }
        if (this.isLocationBlocked(damager.getLocation()) || this.isLocationBlocked(target.getLocation())) {
            return;
        }
        if (this.zakazny.containsKey(damager.getUniqueId())) {
            if (this.zakazny.get(damager.getUniqueId()) > System.currentTimeMillis()) {
                return;
            }
            this.zakazny.remove(damager.getUniqueId());
        }
        long durationTicks = 180L;
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int)durationTicks, 0, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, (int)durationTicks, 0, true, true));
        this.zakazny.put(damager.getUniqueId(), System.currentTimeMillis() + 60000L);
        dev.grzybalson.utils.ColorUtils.sendTitle(damager, "\u00a74Zainfekowa\u0142e\u015b przeciwnika!", "\u00a77Zainfekowa\u0142e\u015b gracza \u00a7f" + target.getName() + "\u00a77!", 0, 50, 10);
        dev.grzybalson.utils.ColorUtils.sendTitle(target, "\u00a74Zosta\u0142e\u015b zainfekowany!", "\u00a77Zosta\u0142e\u015b zainfekowany przez gracza \u00a7f" + damager.getName() + "\u00a77!", 0, 50, 10);
    }

    private boolean isLocationBlocked(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return true;
        }
        // Dungeon = osobna arena — nie odpalaj freeze/kostiumow PvP z hubu.
        if (dev.grzybalson.modules.event.dungeon.DungeonWorldService.isDungeonWorld(loc.getWorld().getName())) {
            return true;
        }
        if (this.regionHandler != null && this.regionHandler.isInAnyRegion(loc)) {
            return true;
        }
        // Fallback: bezpośrednio z runtime oghub-customitems (gdy manager żyje).
        if (this.plugin instanceof HollyCore holly) {
            var customItems = holly.getOgHubCustomItemsManager();
            if (customItems != null && customItems.isInBlockedRegion(loc)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCostumeHeartModifier(AttributeModifier modifier) {
        if (modifier == null) {
            return false;
        }
        if (HEALTH_MODIFIER_UUID.equals(modifier.getUniqueId())) {
            return true;
        }
        String name = modifier.getName();
        return name != null && (name.equals(HEALTH_MODIFIER_NAME) || name.equalsIgnoreCase("extrahearts"));
    }

    /** Usuwa wszystkie modifiery serc kostiumu (także zduplikowane po sektorach). */
    public static void stripCostumeHeartModifiers(AttributeInstance instance) {
        if (instance == null) {
            return;
        }
        for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
            if (isCostumeHeartModifier(modifier)) {
                instance.removeModifier(modifier);
            }
        }
    }

    private void applyHearts(Player player, double totalHearts) {
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return;
        }
        // Pilnuj bazy — inaczej rejoiny / sync pieczerują bonus w baseValue.
        if (plugin instanceof HollyCore holly) {
            double expectedBase = PlayerHealthBase.resolveBaseMaxHealth(holly, player);
            if (Math.abs(maxHealthAttribute.getBaseValue() - expectedBase) > 0.05D) {
                stripCostumeHeartModifiers(maxHealthAttribute);
                maxHealthAttribute.setBaseValue(expectedBase);
            }
        }
        double valueToAdd = totalHearts * 2.0;
        double existingAmount = 0.0D;
        int existingCount = 0;
        for (AttributeModifier modifier : maxHealthAttribute.getModifiers()) {
            if (isCostumeHeartModifier(modifier)) {
                existingAmount += modifier.getAmount();
                existingCount++;
            }
        }
        // Jeden poprawny modifier — nic nie ruszaj (anty-flicker / anty-mnożenie).
        if (existingCount == 1 && Double.compare(existingAmount, valueToAdd) == 0) {
            this.extraHearts.put(player.getUniqueId(), valueToAdd);
            return;
        }
        stripCostumeHeartModifiers(maxHealthAttribute);
        if (valueToAdd > 0.0D) {
            AttributeModifier modifier = new AttributeModifier(
                HEALTH_MODIFIER_UUID,
                HEALTH_MODIFIER_NAME,
                valueToAdd,
                AttributeModifier.Operation.ADD_NUMBER
            );
            maxHealthAttribute.addModifier(modifier);
            this.extraHearts.put(player.getUniqueId(), valueToAdd);
        } else {
            this.extraHearts.remove(player.getUniqueId());
        }
        if (player.getHealth() > maxHealthAttribute.getValue()) {
            player.setHealth(maxHealthAttribute.getValue());
        }
    }

    private void removeHearts(Player player) {
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return;
        }
        stripCostumeHeartModifiers(maxHealthAttribute);
        this.extraHearts.remove(player.getUniqueId());
        if (player.getHealth() > maxHealthAttribute.getValue()) {
            player.setHealth(maxHealthAttribute.getValue());
        }
    }

    /** Po ItemSync / sektorze / join — twardy reset bazy + jeden modifier kostiumu. */
    public void refreshHearts(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (plugin instanceof HollyCore holly) {
            PlayerHealthBase.resetBaseMaxHealth(holly, player, true);
        } else {
            stripCostumeHeartModifiers(player.getAttribute(Attribute.GENERIC_MAX_HEALTH));
        }
        this.updatePlayerCache(player);
        PlayerCache cache = this.playerCache.get(player.getUniqueId());
        if (cache != null && cache.totalHearts > 0.0) {
            this.applyHearts(player, cache.totalHearts);
        } else {
            this.removeHearts(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryLoaded(PlayerInventoryLoadedEvent event) {
        Player player = event.getPlayer();
        // setMaxHealth z syncu zdejmuje/psuje modifier — nałóż serca kostiumu ponownie.
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
                this.refreshHearts(player);
            }
        }, 2L);
    }

    private void handleNegativeEffectsDisable(Player player, boolean disabled) {
        if (disabled) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (!this.isNegativeEffect(effect.getType())) continue;
                player.removePotionEffect(effect.getType());
            }
        }
    }

    private boolean isNegativeEffect(PotionEffectType type) {
        if (type == null) {
            return false;
        }
        return matchesPotion(type, "WITHER")
            || matchesPotion(type, "POISON")
            || matchesPotion(type, "SLOW", "SLOWNESS")
            || matchesPotion(type, "BLINDNESS")
            || matchesPotion(type, "HUNGER")
            || matchesPotion(type, "WEAKNESS")
            || matchesPotion(type, "UNLUCK")
            || matchesPotion(type, "CONFUSION", "NAUSEA");
    }

    private static boolean matchesPotion(PotionEffectType type, String... names) {
        String key = type.getName();
        String keyed = null;
        try {
            keyed = type.getKey().getKey();
        } catch (Throwable ignored) {
        }
        for (String name : names) {
            if (name.equalsIgnoreCase(key) || (keyed != null && name.equalsIgnoreCase(keyed))) {
                return true;
            }
        }
        return false;
    }

    private void applyEffects(Player player, List<String> combinedEffects) {
        for (String effectString : combinedEffects) {
            try {
                String[] parts = effectString.split(":");
                PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
                int amplifier = Integer.parseInt(parts[1]);
                if (type == null) continue;
                PotionEffect existing = player.getPotionEffect(type);
                if (existing != null && existing.getAmplifier() == amplifier && existing.getDuration() > 20) {
                    continue;
                }
                player.addPotionEffect(new PotionEffect(type, 100, amplifier, true, false), true);
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("Nieprawid\u0142owy format efektu: " + effectString);
            }
        }
    }

    private void removeBossBar(Player player, Map<UUID, BukkitTask> taskMap) {
        BukkitTask task;
        UUID uuid = player.getUniqueId();
        BossBar bar = sneakBossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
        if ((task = taskMap.remove(uuid)) != null) {
            task.cancel();
        }
    }

    private void removeSneakBossBar(Player player) {
        this.removeBossBar(player, sneakCountdownTasks);
        this.sneakCounter.remove(player.getUniqueId());
    }

    private void removeReflectBossBar(Player player) {
        this.removeBossBar(player, reflectBossBarTasks);
    }

    private void activateDamageReflect(Player player) {
        if (this.isLocationBlocked(player.getLocation())) {
            return;
        }
        this.reflectActiveUntil.put(player.getUniqueId(), System.currentTimeMillis() + 10000L);
        this.sneakCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 120000L);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a75Mim Aktywny! \u00a77Masz \u00a7f10s \u00a77na odbijanie cios\u00f3w.", 0, 50, 10);
        this.removeSneakBossBar(player);
        this.startReflectBossBar(player);
    }

    private void startReflectBossBar(final Player player) {
        UUID uuid = player.getUniqueId();
        this.removeReflectBossBar(player);
        final BossBar bar = Bukkit.createBossBar("\u00a75Mim Aktywny! \u00a77Odbijasz ciosy jeszcze przez \u00a7f10s", BarColor.RED, BarStyle.SOLID, new BarFlag[0]);
        bar.addPlayer(player);
        bar.setProgress(1.0);
        sneakBossBars.put(uuid, bar);
        BukkitTask task = new BukkitRunnable(){
            int remainingSeconds = 10;

            @Override
            public void run() {
                if (!player.isOnline() || this.remainingSeconds <= 0) {
                    BoostHandler.this.removeBossBar(player, reflectBossBarTasks);
                    this.cancel();
                    return;
                }
                bar.setTitle(String.format("\u00a75Mim Aktywny! \u00a77Odbijasz ciosy jeszcze przez \u00a7f%ds", this.remainingSeconds));
                bar.setProgress((double)this.remainingSeconds / 10.0);
                --this.remainingSeconds;
            }
        }.runTaskTimer(this.plugin, 0L, 20L);
        reflectBossBarTasks.put(uuid, task);
    }

    private void removeCobwebClearBossBar(Player player) {
        this.removeBossBar(player, cobwebClearTasks);
    }

    private void activateCobwebClear(final Player player) {
        if (this.isLocationBlocked(player.getLocation())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        this.sneakCooldowns.put(uuid, System.currentTimeMillis() + COOLDOWN_DURATION_MS);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a79Ucze\u0144 Aktywny! \u00a77Pajeczyny znikaj\u0105 przez \u00a7f3s\u00a77.", 0, 50, 10);
        this.removeSneakBossBar(player);
        this.removeUczenCooldownBossBar(player);
        this.removeCobwebClearBossBar(player);
        final BossBar bar = Bukkit.createBossBar("\u00a79Czyszczenie paj\u0119czyn jeszcze przez \u00a7f3s", BarColor.BLUE, BarStyle.SOLID, new BarFlag[0]);
        bar.addPlayer(player);
        bar.setProgress(1.0);
        sneakBossBars.put(uuid, bar);
        BukkitTask task = new BukkitRunnable(){
            long remainingTicks = COBWEB_CLEAR_DURATION_TICKS;

            @Override
            public void run() {
                if (!player.isOnline() || this.remainingTicks <= 0L) {
                    BoostHandler.this.removeCobwebClearBossBar(player);
                    if (player.isOnline()) {
                        BoostHandler.this.startUczenCooldownBossBar(player);
                    }
                    this.cancel();
                    return;
                }
                BoostHandler.this.clearCobwebsAround(player);
                bar.setTitle(String.format("\u00a79Czyszczenie paj\u0119czyn jeszcze przez \u00a7f%ds", (int)Math.ceil((double)this.remainingTicks / 20.0)));
                bar.setProgress(Math.max(0.0, (double)this.remainingTicks / (double)COBWEB_CLEAR_DURATION_TICKS));
                --this.remainingTicks;
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
        cobwebClearTasks.put(uuid, task);
    }

    private void activateNinjaFreeze(Player player) {
        if (this.isLocationBlocked(player.getLocation())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        this.sneakCooldowns.put(uuid, System.currentTimeMillis() + COOLDOWN_DURATION_MS);
        this.removeSneakBossBar(player);
        long until = System.currentTimeMillis() + 2000L;
        for (Player target : player.getWorld().getPlayers()) {
            if (target.equals(player) || !target.isOnline()) {
                continue;
            }
            if (this.isLocationBlocked(target.getLocation())) {
                continue;
            }
            if (target.getLocation().distanceSquared(player.getLocation()) > 9.0) {
                continue;
            }
            this.ninjaFrozenUntil.put(target.getUniqueId(), until);
            target.setVelocity(new Vector(0, 0, 0));
            target.setFreezeTicks(40);
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0F, 0.6F);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a78Ninja \u00a77zamrozi\u0142 okolice na \u00a7f2s\u00a77.", 0, 40, 10);
        this.startSneakCooldownBossBar(player, SneakAbility.NINJA);
    }

    private void activateGoatRam(Player player) {
        if (this.isLocationBlocked(player.getLocation())) {
            return;
        }
        UUID uuid = player.getUniqueId();
        this.sneakCooldowns.put(uuid, System.currentTimeMillis() + COOLDOWN_DURATION_MS);
        this.removeSneakBossBar(player);
        Location origin = player.getLocation();
        for (int i = 0; i < 4; i++) {
            double angle = i * (Math.PI / 2.0);
            Location spawn = origin.clone().add(Math.cos(angle) * 1.4, 0.0, Math.sin(angle) * 1.4);
            Goat goat = player.getWorld().spawn(spawn, Goat.class, spawned -> {
                spawned.setInvulnerable(true);
                spawned.setSilent(true);
                spawned.setAI(false);
                spawned.setRemoveWhenFarAway(true);
            });
            goat.setVelocity(new Vector(Math.cos(angle), 0.25, Math.sin(angle)).multiply(0.85));
            Bukkit.getScheduler().runTaskLater(this.plugin, goat::remove, 25L);
        }
        for (Player target : player.getWorld().getPlayers()) {
            if (target.equals(player) || !target.isOnline()) {
                continue;
            }
            if (this.isLocationBlocked(target.getLocation())) {
                continue;
            }
            if (target.getLocation().distanceSquared(origin) > 9.0) {
                continue;
            }
            this.knockbackStepByStep(target, origin, 5);
        }
        player.getWorld().playSound(origin, Sound.ENTITY_GOAT_RAM_IMPACT, 1.0F, 0.8F);
        dev.grzybalson.utils.ColorUtils.sendTitle(player, "", "\u00a7cGoat \u00a77odpycha okolice!", 0, 40, 10);
        this.startSneakCooldownBossBar(player, SneakAbility.GOAT);
    }

    /** Odpychanie kratka po kratce zamiast jednego dużego velocity. */
    private void knockbackStepByStep(Player target, Location origin, int blocks) {
        Vector dir = target.getLocation().toVector().subtract(origin.toVector());
        dir.setY(0.0);
        if (dir.lengthSquared() < 0.01) {
            dir = origin.getDirection().setY(0);
        }
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(1, 0, 0);
        }
        final Vector step = dir.normalize();
        final int totalBlocks = Math.max(1, blocks);
        new BukkitRunnable() {
            int remaining = totalBlocks;

            @Override
            public void run() {
                if (this.remaining <= 0 || !target.isOnline() || target.isDead()) {
                    this.cancel();
                    return;
                }
                Location current = target.getLocation();
                Location next = current.clone().add(step.getX(), 0.0, step.getZ());
                if (next.getWorld() == null) {
                    this.cancel();
                    return;
                }
                if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) {
                    this.cancel();
                    return;
                }
                next.setYaw(current.getYaw());
                next.setPitch(current.getPitch());
                target.teleport(next);
                target.setVelocity(step.clone().multiply(0.18).setY(this.remaining == totalBlocks ? 0.22 : 0.05));
                target.getWorld().spawnParticle(Particle.CLOUD, next.clone().add(0, 0.2, 0), 4, 0.15, 0.05, 0.15, 0.01);
                target.getWorld().playSound(next, Sound.ENTITY_GOAT_STEP, 0.55F, 1.2F);
                --this.remaining;
            }
        }.runTaskTimer(this.plugin, 0L, 2L);
    }

    private void removeUczenCooldownBossBar(Player player) {
        this.removeBossBar(player, uczenCooldownTasks);
    }

    /** Pokazuje, za ile zdolność będzie znowu gotowa (po aktywacji). */
    private void startUczenCooldownBossBar(final Player player) {
        this.startSneakCooldownBossBar(player, SneakAbility.UCZEN);
    }

    private void startSneakCooldownBossBar(final Player player, final SneakAbility ability) {
        if (player == null || !player.isOnline() || ability == null) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        long until = this.sneakCooldowns.getOrDefault(uuid, 0L);
        long remainingMs = until - System.currentTimeMillis();
        if (remainingMs <= 0L) {
            return;
        }

        this.removeSneakBossBar(player);
        this.removeUczenCooldownBossBar(player);

        final String label = switch (ability) {
            case UCZEN -> "\u00a79Ucze\u0144";
            case NINJA -> "\u00a78Ninja";
            case GOAT -> "\u00a7cGoat";
            case MIM -> "\u00a75Mim";
        };

        final BossBar bar = Bukkit.createBossBar(
            String.format("%s \u00a77\u2014 mo\u017cesz u\u017cy\u0107 za \u00a7f%ds", label, (int) Math.ceil(remainingMs / 1000.0)),
            ability.barColor,
            BarStyle.SOLID,
            new BarFlag[0]
        );
        bar.addPlayer(player);
        bar.setProgress(Math.min(1.0, Math.max(0.0, (double) remainingMs / (double) COOLDOWN_DURATION_MS)));
        sneakBossBars.put(uuid, bar);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    BoostHandler.this.removeUczenCooldownBossBar(player);
                    this.cancel();
                    return;
                }
                long left = BoostHandler.this.sneakCooldowns.getOrDefault(uuid, 0L) - System.currentTimeMillis();
                if (left <= 0L) {
                    bar.setColor(BarColor.GREEN);
                    bar.setProgress(1.0);
                    bar.setTitle(String.format("%s \u00a7a\u2014 gotowy!", label));
                    BukkitTask self = uczenCooldownTasks.remove(uuid);
                    if (self != null) {
                        self.cancel();
                    }
                    Bukkit.getScheduler().runTaskLater(BoostHandler.this.plugin, () -> {
                        BossBar current = sneakBossBars.get(uuid);
                        if (current == bar) {
                            sneakBossBars.remove(uuid);
                            bar.removeAll();
                        }
                    }, 40L);
                    this.cancel();
                    return;
                }
                int secs = (int) Math.ceil(left / 1000.0);
                bar.setTitle(String.format("%s \u00a77\u2014 mo\u017cesz u\u017cy\u0107 za \u00a7f%ds", label, secs));
                bar.setProgress(Math.min(1.0, Math.max(0.0, (double) left / (double) COOLDOWN_DURATION_MS)));
            }
        }.runTaskTimer(this.plugin, 0L, 20L);
        uczenCooldownTasks.put(uuid, task);
    }

    private void clearCobwebsAround(Player player) {
        Location center = player.getLocation();
        if (center.getWorld() == null || this.isLocationBlocked(center)) {
            return;
        }
        Block origin = center.getBlock();
        int radiusSquared = COBWEB_CLEAR_RADIUS * COBWEB_CLEAR_RADIUS;
        for (int x = -COBWEB_CLEAR_RADIUS; x <= COBWEB_CLEAR_RADIUS; ++x) {
            for (int y = -COBWEB_CLEAR_RADIUS; y <= COBWEB_CLEAR_RADIUS; ++y) {
                for (int z = -COBWEB_CLEAR_RADIUS; z <= COBWEB_CLEAR_RADIUS; ++z) {
                    if (x * x + y * y + z * z > radiusSquared) continue;
                    Block block = origin.getRelative(x, y, z);
                    if (block.getType() != Material.COBWEB) continue;
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void startSneakCountdown(final Player player, final SneakAbility ability) {
        final UUID uuid = player.getUniqueId();
        final String abilityName = ability.displayName;
        final String barPrefix = ability.prefix;
        this.removeSneakBossBar(player);
        final BossBar bar = Bukkit.createBossBar(String.format("%sShiftuj jeszcze \u00a7f2 %srazy, aby uruchomi\u0107 %s%s! \u00a77(3s)", barPrefix, barPrefix, abilityName, barPrefix), ability.barColor, BarStyle.SOLID, new BarFlag[0]);
        bar.addPlayer(player);
        bar.setProgress(1.0);
        sneakBossBars.put(uuid, bar);
        this.sneakCounter.put(uuid, 1);
        BukkitTask task = new BukkitRunnable(){
            int remainingSeconds = 3;

            @Override
            public void run() {
                if (this.remainingSeconds <= 0) {
                    BoostHandler.this.removeSneakBossBar(player);
                    this.cancel();
                    return;
                }
                int sneaksNeeded = 3 - BoostHandler.this.sneakCounter.getOrDefault(uuid, 1);
                bar.setTitle(String.format("%sShiftuj jeszcze \u00a7f%d %srazy, aby uruchomi\u0107 %s%s! \u00a77(%ds)", barPrefix, sneaksNeeded, barPrefix, abilityName, barPrefix, this.remainingSeconds));
                bar.setProgress(Math.max(0.0, (double)(this.remainingSeconds - 1) / 3.0));
                --this.remainingSeconds;
            }
        }.runTaskTimer(this.plugin, 0L, 20L);
        sneakCountdownTasks.put(uuid, task);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!event.isSneaking()) {
            return;
        }
        if (this.isLocationBlocked(player.getLocation())) {
            return;
        }
        // Kostiumy maja jedna zdolnosc na 3x shift, wiec licznik jest wspoldzielony.
        SneakAbility ability = this.resolveSneakAbility(player);
        if (ability == null) {
            return;
        }
        if (this.reflectActiveUntil.getOrDefault(uuid, 0L) > System.currentTimeMillis()) {
            return;
        }
        if (cobwebClearTasks.containsKey(uuid)) {
            return;
        }
        if (this.sneakCooldowns.getOrDefault(uuid, 0L) > System.currentTimeMillis()) {
            return;
        }
        int currentSneaks = this.sneakCounter.getOrDefault(uuid, 0);
        if (currentSneaks == 0) {
            this.startSneakCountdown(player, ability);
        } else if (currentSneaks < 3 && sneakCountdownTasks.containsKey(uuid)) {
            this.sneakCounter.put(uuid, ++currentSneaks);
            if (currentSneaks == 3) {
                switch (ability) {
                    case UCZEN -> this.activateCobwebClear(player);
                    case MIM -> this.activateDamageReflect(player);
                    case NINJA -> this.activateNinjaFreeze(player);
                    case GOAT -> this.activateGoatRam(player);
                }
            } else if (sneakBossBars.containsKey(uuid)) {
                BossBar bar = sneakBossBars.get(uuid);
                bar.setTitle(String.format("%sShiftuj jeszcze \u00a7f%d %srazy, aby uruchomi\u0107 %s%s! \u00a77(%ds)", ability.prefix, 3 - currentSneaks, ability.prefix, ability.displayName, ability.prefix, (int)Math.ceil(bar.getProgress() * 3.0)));
            }
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity entity;
        if (event.isCancelled() || !((entity = event.getEntity()) instanceof Player)) {
            return;
        }
        Player damaged = (Player)entity;
        Player damager = null;
        Object object = event.getDamager();
        if (object instanceof Player) {
            Player p;
            damager = p = (Player)object;
        } else {
            Projectile pr;
            object = event.getDamager();
            if (object instanceof Projectile && (object = (pr = (Projectile)object).getShooter()) instanceof Player) {
                Player p;
                damager = p = (Player)object;
            }
        }
        if (damager != null && !damager.equals(damaged)) {
            this.lastDamager.put(damaged.getUniqueId(), damager.getUniqueId());
            this.lastDamageTime.put(damaged.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onDamageReflect(EntityDamageByEntityEvent event) {
        Player damaged;
        Entity entity;
        block6: {
            block5: {
                if (event.isCancelled() || !((entity = event.getEntity()) instanceof Player)) break block5;
                damaged = (Player)entity;
                entity = event.getDamager();
                if (entity instanceof Player) break block6;
            }
            return;
        }
        Player damager = (Player)entity;
        UUID damagedId = damaged.getUniqueId();
        if (this.reflectActiveUntil.getOrDefault(damagedId, 0L) > System.currentTimeMillis()) {
            damager.damage(event.getDamage() * 0.7);
            event.setCancelled(true);
            event.setDamage(0.0);
            dev.grzybalson.utils.ColorUtils.sendTitle(damaged, "\u00a75Odbito cios", "\u00a77Odbi\u0142e\u015b cios od \u00a7f" + damager.getName(), 0, 50, 10);
            dev.grzybalson.utils.ColorUtils.sendTitle(damager, "\u00a75Odbito cios", "\u00a7f" + damager.getName() + " \u00a77odbi\u0142 tw\u00f3j cios!", 0, 50, 10);
            damager.playSound(damager.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
            damaged.playSound(damaged.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
        } else {
            this.reflectActiveUntil.remove(damagedId);
        }
    }

    /** Tylko cleanup wygasłego Mim — cancel damage jest wyłącznie w {@link #onDamageReflect} (PvP melee). */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player damaged)) {
            return;
        }
        UUID damagedId = damaged.getUniqueId();
        Long until = this.reflectActiveUntil.get(damagedId);
        if (until != null && until <= System.currentTimeMillis()) {
            this.reflectActiveUntil.remove(damagedId);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        Entity entity;
        if (event.isCancelled() || event.getCause() != EntityDamageEvent.DamageCause.FALL || !((entity = event.getEntity()) instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (this.isLocationBlocked(player.getLocation())) {
            return;
        }
        PlayerCache cache = this.playerCache.get(player.getUniqueId());
        if (cache == null) {
            return;
        }
        double reductionFactor = (cache.kostiumBoosts != null ? cache.kostiumBoosts.fallDamageReduction() : 0.0) + (cache.petBoosts != null ? cache.petBoosts.fallDamageReduction() : 0.0);
        if (reductionFactor > 0.0) {
            double actualReduction = Math.min(1.0, reductionFactor);
            event.setDamage(Math.max(0.0, event.getDamage() * (1.0 - actualReduction)));
            if (actualReduction >= 1.0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onDripstoneDamage(EntityDamageEvent event) {
        Entity entity;
        if (event.isCancelled() || event.getCause() != EntityDamageEvent.DamageCause.FALLING_BLOCK || !((entity = event.getEntity()) instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        PlayerCache cache = this.playerCache.get(player.getUniqueId());
        if (cache == null) {
            return;
        }
        double reductionFactor = (cache.kostiumBoosts != null ? cache.kostiumBoosts.dripstoneDamageReduction() : 0.0) + (cache.petBoosts != null ? cache.petBoosts.dripstoneDamageReduction() : 0.0);
        if (reductionFactor > 0.0) {
            double actualReduction = Math.min(1.0, reductionFactor);
            event.setDamage(Math.max(0.0, event.getDamage() * (1.0 - actualReduction)));
            if (actualReduction >= 1.0) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHungerChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!hasNoHunger(player.getUniqueId())) {
            return;
        }
        int current = player.getFoodLevel();
        int next = event.getFoodLevel();
        if (next < current) {
            event.setCancelled(true);
            fillHunger(player);
            return;
        }
        event.setFoodLevel(20);
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                fillHunger(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExhaustion(EntityExhaustionEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!hasNoHunger(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.setExhaustion(0.0F);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEatWithNoHunger(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!hasNoHunger(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                fillHunger(player);
            }
        });
    }

    private boolean hasNoHunger(UUID uuid) {
        PlayerCache cache = this.playerCache.get(uuid);
        if (cache != null && cache.noHunger) {
            return true;
        }
        KostiumyHandler.BoostsData kostium = this.fetchKostiumBoosts(uuid);
        if (kostium != null && kostium.noHunger()) {
            return true;
        }
        PetyHandler.BoostsData pet = this.fetchPetBoosts(uuid);
        return pet != null && pet.noHunger();
    }

    private static void fillHunger(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getFoodLevel() != 20) {
            player.setFoodLevel(20);
        }
        if (player.getSaturation() < 20.0F) {
            player.setSaturation(20.0F);
        }
        if (player.getExhaustion() > 0.0F) {
            player.setExhaustion(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled() || !event.isDropItems() || !DOUBLE_DROP_BLOCKS.contains(event.getBlock().getType())) {
            return;
        }
        if (plugin instanceof dev.grzybalson.HollyCore hollyCore) {
            if (hollyCore.getZoneBoxManager() != null && hollyCore.getZoneBoxManager().isBlockEconomyBlock(event.getBlock().getType())) {
                return;
            }
        }
        PlayerCache cache = this.playerCache.get(event.getPlayer().getUniqueId());
        ItemStack mainDrop;
        if (cache != null && (cache.kostiumBoosts != null && cache.kostiumBoosts.doubleDrop() || cache.petBoosts != null && cache.petBoosts.doubleDrop()) && (mainDrop = (ItemStack)event.getBlock().getDrops(event.getPlayer().getInventory().getItemInMainHand()).stream().findFirst().orElse(null)) != null) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), mainDrop.clone());
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onStoneBreakForExperience(BlockBreakEvent event) {
        if (event.isCancelled() || event.getBlock().getType() != Material.STONE) {
            return;
        }
        PlayerCache cache = this.playerCache.get(event.getPlayer().getUniqueId());
        if (cache != null && cache.petBoosts != null && cache.petBoosts.stoneExperience() >= 0.0) {
            event.getPlayer().giveExp(2 + (int)Math.floor(cache.petBoosts.stoneExperience() * 2.0));
        }
    }

    private static boolean changedBlock(Location from, Location to) {
        return to != null && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || !from.getWorld().equals(to.getWorld()));
    }

    public static boolean isTemporarySnow(Location location) {
        return location != null && location.getWorld() != null && temporarySnow.contains(BlockPos.of(location));
    }

    public static boolean consumeTemporarySnow(Location location) {
        return location != null && location.getWorld() != null && temporarySnow.remove(BlockPos.of(location));
    }

    public static boolean isTemporarySnow(Block block) {
        return block != null && block.getWorld() != null && temporarySnow.contains(BlockPos.of(block));
    }

    public static boolean consumeTemporarySnow(Block block) {
        return block != null && block.getWorld() != null && temporarySnow.remove(BlockPos.of(block));
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onEndermanDeath(EntityDeathEvent event) {
        Enderman enderman;
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof Enderman) || (enderman = (Enderman)livingEntity).getKiller() == null) {
            return;
        }
        PlayerCache cache = this.playerCache.get(enderman.getKiller().getUniqueId());
        if (cache != null && cache.petBoosts != null && cache.petBoosts.doubleEnderPearlDrop()) {
            ArrayList<ItemStack> extras = new ArrayList<ItemStack>();
            for (ItemStack drop : event.getDrops()) {
                if (drop.getType() != Material.ENDER_PEARL) continue;
                extras.add(drop.clone());
            }
            event.getDrops().addAll(extras);
        }
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled = true)
    public void onEnterWater(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (!changedBlock(event.getFrom(), to)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerCache cache = this.playerCache.get(player.getUniqueId());
        if (cache == null || !cache.nurek) {
            return;
        }

        if (event.getFrom().getBlock().isLiquid() || !to.getBlock().isLiquid()) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        if (now - this.dolphinCooldown.getOrDefault(uuid, 0L) < 60000L) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, 1, false, false, true));
        this.dolphinCooldown.put(uuid, now);
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        boolean wampir;
        Entity entity = event.getDamager();
        if (!(entity instanceof Player)) {
            return;
        }
        Player attacker = (Player)entity;
        PlayerCache cache = this.playerCache.get(attacker.getUniqueId());
        if (cache == null) {
            return;
        }
        boolean bl = wampir = cache.kostiumBoosts != null && cache.kostiumBoosts.wampir() || cache.petBoosts != null && cache.petBoosts.wampir();
        if (wampir && ThreadLocalRandom.current().nextInt(100) < 3) {
            attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + 4.0));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location loc = event.getTo();
        if (!changedBlock(event.getFrom(), loc)) {
            return;
        }
        Player player = event.getPlayer();
        PlayerCache cache = this.playerCache.get(player.getUniqueId());
        if (cache == null) {
            return;
        }
        final Block currentBlock = loc.getBlock();
        BlockPos pos = BlockPos.of(currentBlock);
        if (!cache.mikolaj) {
            float targetSpeed = currentBlock.getType() == Material.SNOW && temporarySnow.contains(pos) ? 0.14f : 0.2f;
            if (Math.abs(player.getWalkSpeed() - targetSpeed) > 0.001f) {
                player.setWalkSpeed(targetSpeed);
            }
            return;
        }
        if (Math.abs(player.getWalkSpeed() - 0.2f) > 0.001f) {
            player.setWalkSpeed(0.2f);
        }
        if (this.isLocationBlocked(loc)) {
            return;
        }
        Block blockBelow = currentBlock.getRelative(BlockFace.DOWN);
        if (currentBlock.getType() == Material.AIR && blockBelow.getType().isSolid()) {
            currentBlock.setType(Material.SNOW);
            temporarySnow.add(pos);
            new BukkitRunnable(){

                @Override
                public void run() {
                    if (temporarySnow.remove(pos) && currentBlock.getType() == Material.SNOW) {
                        currentBlock.setType(Material.AIR);
                    }
                }
            }.runTaskLater(this.plugin, 100L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSnowBreak(BlockBreakEvent event) {
        if (temporarySnow.remove(BlockPos.of(event.getBlock()))) {
            event.setDropItems(false);
        }
    }

    private Economy getEconomy() {
        if (economy != null) {
            return economy;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                org.bukkit.plugin.RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
                if (rsp != null) {
                    economy = rsp.getProvider();
                }
            } catch (Exception ignored) {
            }
        }
        return economy;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player damaged = event.getEntity();
        UUID damagedId = damaged.getUniqueId();
        Player finalKiller = damaged.getKiller();
        if (finalKiller == null && this.lastDamager.containsKey(damagedId) && System.currentTimeMillis() - this.lastDamageTime.get(damagedId) <= 90000L) {
            finalKiller = Bukkit.getPlayer(this.lastDamager.get(damagedId));
        }
        this.lastDamager.remove(damagedId);
        this.lastDamageTime.remove(damagedId);

        Economy eco = getEconomy();
        if (finalKiller != null) {
            dev.grzybalson.utils.ColorUtils.sendTitle(damaged, "", "\u00a7cZosta\u0142e\u015b zabity przez \u00a74" + finalKiller.getName(), 0, 50, 10);
            finalKiller.playSound(finalKiller.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);
            damaged.getWorld().strikeLightningEffect(damaged.getLocation());
            PlayerCache killerCache = this.playerCache.get(finalKiller.getUniqueId());
            double robPerc = 0.0;
            if (killerCache != null) {
                robPerc = (killerCache.kostiumBoosts != null ? killerCache.kostiumBoosts.robMoney() : 0.0)
                        + (killerCache.petBoosts != null ? killerCache.petBoosts.robMoney() : 0.0);
            }
            if (eco != null && robPerc > 0.0) {
                double victimBalance = eco.getBalance((OfflinePlayer) damaged);
                double steal = victimBalance * (robPerc / 100.0);
                if (steal > 0.0) {
                    eco.withdrawPlayer((OfflinePlayer) damaged, steal);
                    eco.depositPlayer((OfflinePlayer) finalKiller, steal);
                    dev.grzybalson.utils.ColorUtils.sendTitle(finalKiller, "\u00a7a+" + String.format(java.util.Locale.US, "%.2f", steal) + "$", "\u00a77Zabi\u0142e\u015b gracza \u00a7f" + damaged.getName(), 0, 50, 10);
                    return;
                }
            }
            dev.grzybalson.utils.ColorUtils.sendTitle(finalKiller, "", "\u00a77Zabi\u0142e\u015b gracza \u00a7f" + damaged.getName(), 0, 50, 10);
        } else {
            dev.grzybalson.utils.ColorUtils.sendTitle(damaged, "", "\u00a7cZgin\u0105\u0142e\u015b w niewyja\u015bnionych okoliczno\u015bciach!", 0, 50, 10);
            damaged.getWorld().strikeLightningEffect(damaged.getLocation());
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onEntityResurrect(EntityResurrectEvent event) {
        LivingEntity livingEntity;
        if (!event.isCancelled() && (livingEntity = event.getEntity()) instanceof Player) {
            Player player = (Player)livingEntity;
            PlayerCache cache = this.playerCache.get(player.getUniqueId());
            if (cache == null) {
                return;
            }
            ArrayList<String> effects = new ArrayList<String>();
            if (cache.kostiumBoosts != null) {
                effects.addAll(cache.kostiumBoosts.totemPopEffects());
            }
            if (cache.petBoosts != null) {
                effects.addAll(cache.petBoosts.totemPopEffects());
            }
            if (!effects.isEmpty()) {
                Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
                    for (String s : effects) {
                        try {
                            String[] p = s.split(":");
                            PotionEffectType t = PotionEffectType.getByName(p[0].toUpperCase());
                            if (t == null) continue;
                            player.addPotionEffect(new PotionEffect(t, Integer.parseInt(p[2]) * 20, Integer.parseInt(p[1]), true, false), true);
                        }
                        catch (Exception exception) {}
                    }
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                CompletableFuture<?> f1 = (kostiumyHandler != null && kostiumyHandler.getDatabase() != null)
                        ? kostiumyHandler.getDatabase().loadRecordAsync(uuid)
                        : CompletableFuture.completedFuture(null);
                CompletableFuture<?> f2 = (petyHandler != null && petyHandler.getDatabase() != null)
                        ? petyHandler.getDatabase().loadRecordAsync(uuid)
                        : CompletableFuture.completedFuture(null);
                CompletableFuture<?> f3 = (papugiHandler != null && papugiHandler.getDatabase() != null)
                        ? papugiHandler.getDatabase().loadPapugaRecordAsync(uuid)
                        : CompletableFuture.completedFuture(null);

                CompletableFuture.allOf(f1, f2, f3).join();
            } catch (Exception ignored) {
            }
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (player.isOnline()) {
                    // Reset bazy (10/30/40) + jeden modifier kostiumu — bez stackowania po rejoine.
                    this.refreshHearts(player);
                    KostiumyArmorHandler armor = KostiumyArmorHandler.getInstance();
                    if (armor != null) {
                        armor.refreshPlayer(player);
                        // Ponownie po chwili — PacketEvents / tracking bywa opóźniony po joinie.
                        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                            if (player.isOnline()) {
                                armor.refreshPlayer(player);
                            }
                        }, 40L);
                    }
                }
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();
        this.removeHearts(p);
        this.removeSneakBossBar(p);
        this.removeReflectBossBar(p);
        this.removeCobwebClearBossBar(p);
        this.removeUczenCooldownBossBar(p);
        this.playerCache.remove(uuid);
        dropKeysMultipliers.remove(uuid);
        this.extraHearts.remove(uuid);
        this.zakazny.remove(uuid);
        this.sneakCooldowns.remove(uuid);
        this.sneakCounter.remove(uuid);
        this.reflectActiveUntil.remove(uuid);
        this.hitEffectCooldowns.remove(uuid);
        this.dolphinCooldown.remove(uuid);
        this.lastDamager.remove(uuid);
        this.lastDamageTime.remove(uuid);
        this.ninjaFrozenUntil.remove(uuid);
        this.goatWeaknessHits.remove(uuid);
        this.goatWeaknessRequired.remove(uuid);

        if (kostiumyHandler != null && kostiumyHandler.getDatabase() != null) {
            kostiumyHandler.getDatabase().invalidateCache(uuid);
        }
        if (petyHandler != null && petyHandler.getDatabase() != null) {
            petyHandler.getDatabase().invalidateCache(uuid);
        }
        if (papugiHandler != null && papugiHandler.getDatabase() != null) {
            papugiHandler.getDatabase().invalidate(uuid);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        this.removeSneakBossBar(p);
        this.removeReflectBossBar(p);
        this.removeCobwebClearBossBar(p);
        this.removeUczenCooldownBossBar(p);
        this.reflectActiveUntil.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            PlayerCache cache = this.playerCache.get(p.getUniqueId());
            if (cache != null && cache.totalHearts > 0.0) {
                this.applyHearts(p, cache.totalHearts);
            } else {
                this.removeHearts(p);
            }
        }, 5L);
    }

    public static void onDisable() {
        sneakBossBars.values().forEach(BossBar::removeAll);
        sneakBossBars.clear();
        sneakCountdownTasks.values().forEach(BukkitTask::cancel);
        sneakCountdownTasks.clear();
        reflectBossBarTasks.values().forEach(BukkitTask::cancel);
        reflectBossBarTasks.clear();
        cobwebClearTasks.values().forEach(BukkitTask::cancel);
        cobwebClearTasks.clear();
        uczenCooldownTasks.values().forEach(BukkitTask::cancel);
        uczenCooldownTasks.clear();
        dropKeysMultipliers.clear();
        for (BlockPos pos : new HashSet<>(temporarySnow)) {
            World w = Bukkit.getWorld(pos.worldId());
            if (w != null) {
                Block b = w.getBlockAt(pos.x(), pos.y(), pos.z());
                if (b.getType() == Material.SNOW) {
                    b.setType(Material.AIR);
                }
            }
        }
        temporarySnow.clear();
    }

    static {
        HEALTH_MODIFIER_UUID = UUID.fromString("f483b692-a1e4-42b7-a3f2-1a4c8f000001");
        economy = null;
        DOUBLE_DROP_BLOCKS = Arrays.asList(Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.EMERALD_ORE, Material.ANCIENT_DEBRIS, Material.NETHER_QUARTZ_ORE, Material.COPPER_ORE);
        sneakBossBars = new HashMap<UUID, BossBar>();
        sneakCountdownTasks = new HashMap<UUID, BukkitTask>();
        reflectBossBarTasks = new HashMap<UUID, BukkitTask>();
        cobwebClearTasks = new HashMap<UUID, BukkitTask>();
        uczenCooldownTasks = new HashMap<UUID, BukkitTask>();
        dropKeysMultipliers = new ConcurrentHashMap<UUID, Double>();
    }

    private static class PlayerCache {
        KostiumyHandler.BoostsData kostiumBoosts;
        PetyHandler.BoostsData petBoosts;
        PapugiHandler.BonusData papugaBonus;
        boolean mikolaj;
        double totalDamageBoost;
        double totalDamageMultiplier;
        double totalReduction;
        double totalHearts;
        boolean negativeEffectsDisabled;
        boolean noHunger;
        boolean nurek;
        List<String> combinedEffects = List.of();

        private PlayerCache() {
        }
    }

    private enum SneakAbility {
        UCZEN("\u00a79Ucznia", "\u00a7b", BarColor.BLUE),
        MIM("\u00a75Mima", "\u00a7d", BarColor.PURPLE),
        NINJA("\u00a78Ninja", "\u00a78", BarColor.WHITE),
        GOAT("\u00a7cGoata", "\u00a7c", BarColor.RED);

        private final String displayName;
        private final String prefix;
        private final BarColor barColor;

        SneakAbility(String displayName, String prefix, BarColor barColor) {
            this.displayName = displayName;
            this.prefix = prefix;
            this.barColor = barColor;
        }
    }

    private void tryHiddenWeakness(UUID attackerId, Player victim) {
        int required = goatWeaknessRequired.computeIfAbsent(attackerId, key -> nextWeaknessHits());
        int hits = goatWeaknessHits.computeIfAbsent(attackerId, key -> new AtomicInteger()).incrementAndGet();
        if (hits < required) {
            return;
        }
        goatWeaknessHits.get(attackerId).set(0);
        goatWeaknessRequired.put(attackerId, nextWeaknessHits());
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
    }

    private static int nextWeaknessHits() {
        int[] options = {10, 20, 50};
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCostumeMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Long frozenUntil = ninjaFrozenUntil.get(playerId);
        if (frozenUntil != null) {
            if (frozenUntil <= System.currentTimeMillis()) {
                ninjaFrozenUntil.remove(playerId);
            } else if (event.getTo() != null) {
                Location from = event.getFrom();
                Location to = event.getTo();
                if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                    to.setX(from.getX());
                    to.setY(from.getY());
                    to.setZ(from.getZ());
                    event.setTo(to);
                }
                return;
            }
        }

        Location to = event.getTo();
        if (to == null || !changedBlock(event.getFrom(), to)) {
            return;
        }
        KostiumyHandler.BoostsData boosts = this.getKostiumBoosts(playerId);
        if (boosts == null || boosts.cobwebSpeedPercent() <= 0.0) {
            return;
        }
        if (this.isLocationBlocked(to)) {
            return;
        }
        Material feet = to.getBlock().getType();
        if (feet != Material.COBWEB && player.getEyeLocation().getBlock().getType() != Material.COBWEB) {
            return;
        }
        Vector look = player.getLocation().getDirection();
        look.setY(0.0);
        if (look.lengthSquared() < 1.0E-4) {
            return;
        }
        double extra = 0.06 * (boosts.cobwebSpeedPercent() / 100.0);
        player.setVelocity(player.getVelocity().add(look.normalize().multiply(extra)));
    }
}
