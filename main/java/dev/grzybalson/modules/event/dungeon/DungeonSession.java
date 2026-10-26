package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import dev.grzybalson.managers.MultiActionBarService;
import dev.grzybalson.modules.event.DungeonParty;
import dev.grzybalson.modules.event.EventManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.PlayerHealthBase;
import dev.grzybalson.utils.PlayerInventories;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonSession {

    public enum Phase {
        TUTORIAL, PREPARE, WAVE, UPGRADE, COIN_SHOP, BOSS_CINEMATIC, BOSS, BOSS_REWARD, FROZEN_REJOIN, ENDED
    }

    public enum MiniEvent {
        DOUBLE_COINS, FRENZY, BOUNTY, SPEED_RUSH
    }

    private final HollyCore plugin;
    private final DungeonSessionManager manager;
    private final DungeonParty party;
    private final World world;
    private final DungeonLocations locations;
    private final YamlConfiguration settings;
    private final int ticketMultiplier;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> skipVotes = new HashSet<>();
    private final Set<UUID> deadSpectators = new HashSet<>();
    private final Set<UUID> aliveMobs = new HashSet<>();
    private final Set<UUID> focusedMobs = new HashSet<>();
    private final Set<UUID> idleMobs = new HashSet<>();
    private final Set<UUID> pendingUpgrades = new HashSet<>();
    private final Set<UUID> pendingCoinShop = new HashSet<>();
    private final Map<UUID, Long> pingCooldownUntil = new HashMap<>();
    private final Map<UUID, java.util.ArrayDeque<Long>> pingRecent = new HashMap<>();
    private final Map<UUID, List<org.bukkit.entity.Entity>> activePings = new HashMap<>();
    private final Map<UUID, BukkitTask> pingExpireTasks = new HashMap<>();
    private final Map<UUID, String> mobNames = new HashMap<>();
    private final Map<UUID, Integer> lastMobNameHp = new HashMap<>();
    private final Map<UUID, Integer> armorProt = new HashMap<>();
    private final Map<UUID, Integer> swordSharp = new HashMap<>();
    private final Map<UUID, Integer> dashCdReduce = new HashMap<>();
    private final Map<UUID, Double> bonusMaxHp = new HashMap<>();
    private final Map<UUID, Set<DungeonUpgradeGUI.UpgradeType>> lastUpgradePicks = new HashMap<>();
    private final Map<UUID, List<DungeonUpgradeGUI.UpgradeType>> offeredUpgrades = new HashMap<>();
    private final Map<UUID, List<DungeonUpgradeGUI.UpgradeType>> pendingUpgradePicks = new HashMap<>();
    private final Map<UUID, Set<DungeonUpgradeGUI.UpgradeType>> activeBuffUpgrades = new HashMap<>();
    private final Map<UUID, Location> logoutLocations = new HashMap<>();
    private final Map<UUID, Integer> bankedTickets = new HashMap<>();
    private final Map<UUID, Location> upgradeFreezeLoc = new HashMap<>();
    private final Map<UUID, DungeonUpgradeGUI.UpgradeType> upgradeChoices = new HashMap<>();
    private final Map<UUID, String> upgradeChoiceLabels = new HashMap<>();
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final Map<UUID, Integer> killCounts = new HashMap<>();
    private final Map<UUID, Integer> reviveCounts = new HashMap<>();
    private final Map<UUID, Integer> killStreak = new HashMap<>();
    private final Map<UUID, ArmorStand> deathMarkers = new HashMap<>();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, ItemStack> cachedHeads = new HashMap<>();
    private final Set<UUID> suppressUpgradeReopen = new HashSet<>();
    private boolean refreshWaitScheduled = false;
    private final Map<UUID, Integer> reviveProgress = new HashMap<>();
    private final Map<UUID, UUID> reviveTargets = new HashMap<>();
    private final Map<UUID, Integer> reviveProgressByTarget = new HashMap<>();
    private final Map<UUID, InvSnapshot> savedInventories = new HashMap<>();
    private final Map<UUID, Long> dashCooldownUntil = new HashMap<>();
    private final Map<UUID, BossBar> hudBars = new HashMap<>();
    private final Set<UUID> revivedThisWave = new HashSet<>();
    private final List<String> namePool;
    private boolean wave12RewardOpen;
    private boolean wave12RewardClaimed;
    private final Map<UUID, Boolean> wave12Votes = new HashMap<>(); // true=totem, false=mass revive
    private long sessionStartedAt;
    private long hazardTick;
    private final DungeonCoinSystem coinSystem = new DungeonCoinSystem(this);
    private final DungeonHazardSystem hazardSystem = new DungeonHazardSystem(this);
    private MiniEvent activeMiniEvent;
    private int coinMultiplier = 1;
    private long lastPaidReviveClickMs;

    private static final class InvSnapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;

        private InvSnapshot(PlayerInventory inv) {
            ItemStack[] c = inv.getContents();
            this.contents = new ItemStack[c.length];
            for (int i = 0; i < c.length; i++) {
                this.contents[i] = c[i] == null ? null : c[i].clone();
            }
            ItemStack[] a = inv.getArmorContents();
            this.armor = new ItemStack[a.length];
            for (int i = 0; i < a.length; i++) {
                this.armor[i] = a[i] == null ? null : a[i].clone();
            }
            ItemStack off = inv.getItemInOffHand();
            this.offhand = off == null || off.getType() == Material.AIR ? new ItemStack(Material.AIR) : off.clone();
        }

        private boolean isEmpty() {
            for (ItemStack stack : contents) {
                if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                    return false;
                }
            }
            for (ItemStack stack : armor) {
                if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                    return false;
                }
            }
            return offhand == null || offhand.getType() == Material.AIR || offhand.getAmount() <= 0;
        }

        private void apply(Player player) {
            // setContents na krótkiej tablicy zeruje armor/offhand — użyj bezpiecznego apply.
            PlayerInventories.apply(
                player,
                cloneArr(contents),
                cloneArr(armor),
                new ItemStack[]{offhand == null ? new ItemStack(Material.AIR) : offhand.clone()}
            );
            player.updateInventory();
        }

        private static ItemStack[] cloneArr(ItemStack[] src) {
            ItemStack[] out = new ItemStack[src.length];
            for (int i = 0; i < src.length; i++) {
                out[i] = src[i] == null ? null : src[i].clone();
            }
            return out;
        }
    }

    private Phase phase = Phase.TUTORIAL;
    private Phase phaseBeforeFreeze = Phase.WAVE;
    private int level = 1;
    private int wave = 1;
    private int waveTotal;
    private int waveLeft;
    private int wavesCleared;
    private boolean movementLocked = true;
    private boolean bossPhase2;
    private boolean musicFading;
    private long tickCounter;
    private BukkitTask tickTask;
    private BukkitTask abilityTask;
    private BukkitTask musicTask;
    private BukkitTask ambientTask;
    private BukkitTask tutorialSlideTask;
    private BukkitTask upgradeTimeoutTask;
    private BukkitTask coinShopTimeoutTask;
    private BukkitTask shopCountdownTask;
    private BukkitTask cinematicTask;
    private BukkitTask progressTask;
    private boolean adminSkipBusy;
    private boolean bossRewardAnimating;
    private final List<ItemStack> pendingBossRewards = new ArrayList<>();
    private boolean wipeReviveOpen;
    private boolean wipeReviveCharging;
    private BukkitTask wipeReviveTask;
    private long wipeReviveEndsAtMs;
    private final Set<UUID> wipeReviveDeclined = new HashSet<>();
    private long tutorialEndsAt;
    private UUID bossId;
    private int nameCursor;
    private int tutorialSlideIndex;
    private Location tutorialCameraLock;
    private Runnable afterUpgradeContinue;

    public DungeonSession(
        HollyCore plugin,
        DungeonSessionManager manager,
        DungeonParty party,
        World world,
        DungeonLocations locations,
        YamlConfiguration settings
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.party = party;
        this.world = world;
        this.locations = locations;
        this.settings = settings;
        this.ticketMultiplier = Math.max(1, party.getTicketCost());
        this.namePool = settings.getStringList("mob-names");
        this.sessionStartedAt = System.currentTimeMillis();
        for (DungeonParty.Member member : party.getMemberList()) {
            players.add(member.getUuid());
        }
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    public int getCoinBalance(UUID uuid) {
        return coinSystem.getCoins(uuid);
    }

    public int getCoinMultiplier() {
        return Math.max(1, coinMultiplier);
    }

    public World getWorld() {
        return world;
    }

    public DungeonParty getParty() {
        return party;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getLevel() {
        return level;
    }

    public int getWave() {
        return wave;
    }

    public int getTicketMultiplier() {
        return ticketMultiplier;
    }

    /** Mnoznik obrazen mobow/bossow vs gracze (0.7 = latwiej o 30%). */
    public double mobDamageMult() {
        return Math.max(0.05D, settings.getDouble("mob-damage-mult", 0.7D));
    }

    public double scaleMobDamage(double amount) {
        return Math.max(0.0D, amount * mobDamageMult());
    }

    public int getKillCount(UUID uuid) {
        return killCounts.getOrDefault(uuid, 0);
    }

    public double getDamageDealt(UUID uuid) {
        return damageDealt.getOrDefault(uuid, 0.0D);
    }

    public String formatDamage(UUID uuid) {
        return formatDmg(uuid);
    }

    public String getActiveMiniEventLabel() {
        if (activeMiniEvent == null) {
            return "";
        }
        return activeMiniEvent.name().replace('_', ' ');
    }

    public String tabProgressLine() {
        if (phase == Phase.WAVE) {
            return "Postep <gradient:#00FF5A:#9FFF00>" + Math.max(0, waveTotal - waveLeft)
                + "</gradient>&8/&f" + waveTotal;
        }
        if ((phase == Phase.BOSS || phase == Phase.BOSS_CINEMATIC) && bossId != null
            && Bukkit.getEntity(bossId) instanceof LivingEntity boss && !boss.isDead()) {
            return "Boss HP <gradient:#FF5C5C:#FF9B00>" + (int) Math.ceil(boss.getHealth())
                + "</gradient>&8/&f" + (int) Math.ceil(boss.getMaxHealth());
        }
        return "Faza " + phase.name();
    }

    public Set<UUID> getPlayers() {
        return players;
    }

    public boolean isParticipant(UUID uuid) {
        return players.contains(uuid);
    }

    public boolean isMovementLocked() {
        return movementLocked && (phase == Phase.PREPARE || phase == Phase.FROZEN_REJOIN);
    }

    public boolean isUpgradePending(UUID uuid) {
        return phase == Phase.UPGRADE && pendingUpgrades.contains(uuid);
    }

    public Map<UUID, String> getUpgradeChoiceLabels() {
        return upgradeChoiceLabels;
    }

    public List<DungeonUpgradeGUI.UpgradeType> getOfferedUpgrades(UUID uuid) {
        return offeredUpgrades.getOrDefault(uuid, List.of());
    }

    public List<DungeonUpgradeGUI.UpgradeType> getPendingUpgradePicks(UUID uuid) {
        return pendingUpgradePicks.getOrDefault(uuid, List.of());
    }

    public void reopenUpgradeIfNeeded(Player player) {
        if (phase != Phase.UPGRADE || !player.isOnline()) {
            return;
        }
        if (suppressUpgradeReopen.contains(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (phase != Phase.UPGRADE || !player.isOnline()) {
                return;
            }
            if (suppressUpgradeReopen.contains(player.getUniqueId())) {
                return;
            }
            // Nie otwieraj drugi raz tego samego wait GUI.
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof DungeonUpgradeGUI
                || player.getOpenInventory().getTopInventory().getHolder() instanceof DungeonUpgradeWaitGUI) {
                return;
            }
            if (pendingUpgrades.contains(player.getUniqueId())) {
                new DungeonUpgradeGUI(plugin, this, player).open();
            } else {
                new DungeonUpgradeWaitGUI(plugin, this, player).open();
            }
        });
    }

    /** Glowa gracza bez OfflinePlayer / sync Mojang lookup (lag). */
    public ItemStack createPlayerHead(UUID uuid, String fallbackName) {
        ItemStack cached = cachedHeads.get(uuid);
        if (cached != null) {
            return cached.clone();
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                meta.setOwningPlayer(online);
                fallbackName = online.getName();
            }
            // NIE wywoluj getOfflinePlayer — blokuje main thread / caly serwer.
            String name = fallbackName != null ? fallbackName : "Gracz";
            meta.displayName(ColorUtils.colorizeComponent("&f" + name).decoration(TextDecoration.ITALIC, false));
            head.setItemMeta(meta);
            if (online != null) {
                cachedHeads.put(uuid, head.clone());
            }
        }
        return head;
    }

    public int takeBankedTickets(UUID uuid) {
        return Math.max(0, bankedTickets.getOrDefault(uuid, 0));
    }

    public void start() {
        Location spawn = locations.getPlayerSpawn(world);
        for (UUID uuid : players) {
            manager.bindPlayer(uuid, this);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            player.closeInventory();
            // Bank leftover tickets BEFORE clear (cost already removed at start).
            int leftover = manager.getEventManager().countTickets(player);
            if (leftover > 0) {
                manager.getEventManager().removeTickets(player, leftover);
                bankedTickets.put(uuid, leftover);
            }
            if (spawn != null) {
                player.teleport(spawn);
            }
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setFlySpeed(0.1F);
            player.setWalkSpeed(0.2F);
            player.setGravity(true);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.getInventory().clear();
            sendDungeonTitle(player,
                settings.getString("tutorial.title", "&#2BB2FFTutorial Dungeon"),
                settings.getString("tutorial.subtitle", "&fAby pominac wpisz &#FFD900/skip"),
                10, 70, 10
            );
        }
        spawnPreviewMobs();
        tutorialEndsAt = System.currentTimeMillis() + settings.getInt("tutorial-seconds", 40) * 1000L;
        startTutorialCamera();
        startTutorialSlides();
        startMusicLoop();
        startAmbientLoop();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        abilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAbilities, 80L, 80L);
        // Force first educational slides immediately + shortly after (spectator can eat first title).
        Bukkit.getScheduler().runTaskLater(plugin, () -> showTutorialSlide(0), 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> showTutorialSlide(1), 45L);
        // Retry teleport dla osob ktore nie weszly w 1 tick (lag / chunk).
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureAllPlayersPresent, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensureAllPlayersPresent, 60L);
    }

    /** Adventure + zero fly — nigdy nie zostawiaj spectatora „wisiacego”. */
    public void forceFighterState(Player player) {
        if (player == null) {
            return;
        }
        if (deadSpectators.contains(player.getUniqueId())) {
            return;
        }
        // Podczas cinematic/tutorial kamera sama ustawia spectator.
        if (phase == Phase.BOSS_CINEMATIC || phase == Phase.TUTORIAL) {
            return;
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFlySpeed(0.1F);
        player.setWalkSpeed(0.2F);
        player.setGravity(true);
    }

    private void ensureAllPlayersPresent() {
        if (phase == Phase.ENDED) {
            return;
        }
        Location spawn = getRespawnLocation();
        for (UUID uuid : players) {
            manager.bindPlayer(uuid, this);
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!player.getWorld().equals(world)) {
                player.teleport(spawn);
                if (phase == Phase.TUTORIAL) {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.setFlySpeed(0.0F);
                } else if (deadSpectators.contains(uuid)) {
                    player.setGameMode(GameMode.SPECTATOR);
                } else {
                    forceFighterState(player);
                    if (phase != Phase.TUTORIAL && player.getInventory().isEmpty()) {
                        giveKits(player);
                    }
                }
                player.sendMessage(ColorUtils.colorizeComponent(
                    "<gradient:#00FFC1:#00FF5A>Przywrocono Cie</gradient> &fdo instancji dungeona.</shadow>"
                ));
            } else if (!deadSpectators.contains(uuid) && phase != Phase.TUTORIAL && phase != Phase.BOSS_CINEMATIC) {
                if (player.getGameMode() == GameMode.SPECTATOR || player.getAllowFlight() || player.isFlying()) {
                    forceFighterState(player);
                }
            }
        }
    }

    private void healPartyFull() {
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double hp = max != null ? max.getValue() : player.getMaxHealth();
            player.setHealth(Math.max(1.0D, hp));
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
            player.setFireTicks(0);
            player.setExhaustion(0.0F);
            player.removePotionEffect(PotionEffectType.POISON);
            player.removePotionEffect(PotionEffectType.WITHER);
            player.removePotionEffect(PotionEffectType.SLOW);
            player.removePotionEffect(PotionEffectType.WEAKNESS);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            world.spawnParticle(Particle.HEART, player.getLocation().add(0, 1.2D, 0), 6, 0.35, 0.35, 0.35, 0);
        }
        broadcastRaw("<gradient:#00FF5A:#9FFF00>Drużyna uleczona!</gradient> &7(pełne HP)</shadow>");
    }

    public boolean isDeadSpectator(UUID uuid) {
        return deadSpectators.contains(uuid);
    }

    public boolean isFocusedMob(UUID uuid) {
        return focusedMobs.contains(uuid);
    }

    /** Idle/freeze mob po uderzeniu przez gracza wchodzi do walki. */
    public void engageMob(LivingEntity entity) {
        if (phase != Phase.WAVE || !(entity instanceof Mob mob)) {
            return;
        }
        if (!entity.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        if (bossId != null && bossId.equals(entity.getUniqueId())) {
            return;
        }
        if (focusedMobs.contains(mob.getUniqueId())) {
            mob.setAI(true);
            mob.setAware(true);
            Player target = nearestPlayer(mob.getLocation());
            if (target != null) {
                mob.setTarget(target);
            }
            return;
        }
        setFocused(mob, true);
        mob.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, mob.getLocation().add(0, 1.2D, 0), 6, 0.3, 0.3, 0.3, 0);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 0.45F, 1.4F);
    }

    public void handleExplosiveArrow(org.bukkit.entity.AbstractArrow arrow, Location impact) {
        if (impact == null || impact.getWorld() == null) {
            if (arrow != null && !arrow.isDead()) {
                arrow.remove();
            }
            return;
        }
        // Pozycja zanim usuniemy entity — to jest punkt wybuchu.
        Location center = impact.clone();
        if (arrow != null && !arrow.isDead()) {
            center = arrow.getLocation().clone();
            try {
                arrow.setBounce(false);
            } catch (Throwable ignored) {
            }
            arrow.remove();
        }
        org.bukkit.World boomWorld = center.getWorld() != null ? center.getWorld() : world;
        boomWorld.spawnParticle(Particle.EXPLOSION_LARGE, center, 1);
        boomWorld.spawnParticle(Particle.CRIT, center, 28, 0.35, 0.25, 0.35, 0.08);
        boomWorld.spawnParticle(Particle.SMOKE_LARGE, center, 12, 0.25, 0.2, 0.25, 0.02);
        boomWorld.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.7F, 1.25F);
        boomWorld.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.55F, 1.1F);
        double radius = 3.2D;
        double dmg = 6.0D; // 3 serca — stałe, bez skalowania levelu
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(center) > radius * radius) {
                continue;
            }
            player.damage(dmg);
            Vector knock = player.getLocation().toVector().subtract(center.toVector());
            if (knock.lengthSquared() > 0.01D) {
                knock = knock.normalize().multiply(0.35D);
                knock.setY(0.22D);
                player.setVelocity(knock);
            }
        }
    }

    private void launchShuriken(LivingEntity living) {
        org.bukkit.entity.Snowball snowball = living.launchProjectile(org.bukkit.entity.Snowball.class);
        ItemStack visual = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = visual.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(1);
            meta.setDisplayName("Shuriken");
            visual.setItemMeta(meta);
        }
        snowball.setItem(visual);
        snowball.addScoreboardTag("holly_dungeon_shuriken");
    }

    public boolean isAliveFighter(Player player) {
        return players.contains(player.getUniqueId())
            && !deadSpectators.contains(player.getUniqueId())
            && player.getGameMode() != GameMode.SPECTATOR
            && phase != Phase.ENDED
            && phase != Phase.TUTORIAL
            && phase != Phase.BOSS_CINEMATIC;
    }

    private void tick() {
        if (phase == Phase.ENDED) {
            return;
        }
        // Stuck recovery: Freeze na scoreboardzie + gracz online = odblokuj (bug world-quit).
        if (phase == Phase.FROZEN_REJOIN && hasOnlineParticipants()) {
            Player first = null;
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    first = p;
                    break;
                }
            }
            if (first != null) {
                resumeAfterFrozenRejoin(first);
            }
            return;
        }
        if (phase == Phase.TUTORIAL) {
            int need = Math.max(1, (int) Math.ceil(players.size() * 0.5D));
            String bar = settings.getString("messages.skip-vote", "&fSkip: &#FFD900/skip &8| &fGlosy: &#FFD900{VOTES}&f/&#FFD900{NEED}")
                .replace("{VOTES}", String.valueOf(skipVotes.size()))
                .replace("{NEED}", String.valueOf(need));
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                MultiActionBarService.get().send(player, "dungeon", bar, 20L);
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.setFlySpeed(0.0F);
                    player.setWalkSpeed(0.0F);
                    if (tutorialCameraLock != null
                        && player.getLocation().distanceSquared(tutorialCameraLock) > 0.36D) {
                        player.teleport(tutorialCameraLock);
                    }
                }
            }
            if (System.currentTimeMillis() >= tutorialEndsAt || skipVotes.size() >= need) {
                if (skipVotes.size() >= need) {
                    broadcast("messages.skipped");
                }
                beginPrepare();
            }
            return;
        }
        tickCounter++;
        boolean combat = phase == Phase.WAVE || phase == Phase.BOSS;
        if (tickCounter % 40L == 0L) {
            ensureAllPlayersPresent();
        }
        if (combat) {
            // Ciezka robota rzadziej — Spark: tick() 4.5%+ przez mob names / gate / HUD co tick.
            if (tickCounter % 10L == 0L) {
                enforceGate();
                refreshMobNames();
            }
            tickHazards();
            if (tickCounter % 4L == 0L) {
                hazardSystem.tick(hazardTick);
            }
            tickRevive();
            if (tickCounter % 5L == 0L) {
                tickDeadSpectators();
            }
            if (tickCounter % 2L == 0L) {
                coinSystem.tick();
            }
            if (phase == Phase.BOSS && tickCounter % 10L == 0L) {
                ensureBossAlive();
                checkBossPhase2();
            }
            if (tickCounter % 10L == 0L) {
                if (phase == Phase.WAVE) {
                    if (isWave12Voting()) {
                        String bar = "<gradient:#FFD900:#C800FF>Glosowanie fali 12</gradient> &8|&f wybierz w GUI";
                        for (UUID uuid : players) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player == null) {
                                continue;
                            }
                            MultiActionBarService.get().send(player, "dungeon", bar, 25L);
                            reopenWave12IfNeeded(player);
                        }
                    } else {
                        String timer = formatElapsed();
                        String bar = settings.getString("messages.wave-actionbar",
                                "&fFala: &#2BD4FF{WAVE} &8|&f Zostalo: &#FFD900{LEFT}&f/&#FFD900{TOTAL} &8|&f Czas: &#FFD900{TIME}")
                            .replace("{WAVE}", String.valueOf(wave))
                            .replace("{LEFT}", String.valueOf(waveLeft))
                            .replace("{TOTAL}", String.valueOf(waveTotal))
                            .replace("{TIME}", timer);
                        for (UUID uuid : players) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && !deadSpectators.contains(uuid)) {
                                MultiActionBarService.get().send(player, "dungeon", bar, 25L);
                            }
                        }
                    }
                } else if (phase == Phase.BOSS) {
                    String timer = formatElapsed();
                    for (UUID uuid : players) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && !deadSpectators.contains(uuid)) {
                            MultiActionBarService.get().send(player, "dungeon",
                                "Boss &8|&f Czas: <gradient:#FFD900:#FF9B00>" + timer + "</gradient></shadow>", 25L);
                        }
                    }
                }
            }
        }
        // Coiny tickuja tez w UPGRADE / shop / po bossie (zbieranie z ziemi + Vault).
        // Podczas animacji losowania bossa nie ruszamy coinow — mniej roboty na main thread.
        if ((phase == Phase.UPGRADE || phase == Phase.COIN_SHOP || phase == Phase.BOSS_REWARD)
            && !bossRewardAnimating) {
            if (tickCounter % 2L == 0L) {
                coinSystem.tick();
            }
        }
        if (tickCounter % 10L == 0L) {
            updateHudBars();
        }
        if (tickCounter % 20L == 0L) {
            updateDungeonScoreboard();
        }
        if (tickCounter % 20L == 0L) {
            tickLowHpWarning();
            if (combat && !hasAliveFighters()) {
                lose();
            }
        }
        if ((phase == Phase.WAVE || phase == Phase.PREPARE) && movementLocked && tickCounter % 5L == 0L) {
            Location spawn = locations.getPlayerSpawn(world);
            if (spawn != null) {
                for (UUID uuid : players) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null) {
                        continue;
                    }
                    if (player.getLocation().distanceSquared(spawn) > 2.5D) {
                        player.teleport(spawn);
                        player.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.freeze", "&#FFAA00⚠ &8Ξ &eNie mozesz sie jeszcze ruszac...")));
                    }
                }
            }
        }
    }

    private void ensureBossAlive() {
        if (phase == Phase.ENDED) {
            return;
        }
        // Solo/wipe: nie respiuj bossa — koncz gre.
        if (!hasAliveFighters()) {
            lose();
            return;
        }
        if (bossId == null) {
            return;
        }
        org.bukkit.entity.Entity entity = Bukkit.getEntity(bossId);
        if (entity instanceof LivingEntity living && !living.isDead()) {
            living.setPersistent(true);
            living.setRemoveWhenFarAway(false);
            if (living instanceof org.bukkit.entity.PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            }
            return;
        }
        // Boss zniknal bez smierci (despawn/transform) — respawn w miejscu bossa.
        Location bossLoc = locations.getBoss(level, world);
        if (bossLoc == null) {
            return;
        }
        aliveMobs.remove(bossId);
        LivingEntity boss = spawnBoss(level, bossLoc);
        if (boss != null) {
            bossId = boss.getUniqueId();
            broadcastRaw("&#FFAA00⚠ &8Ξ &eBoss wrocil na arene!");
        }
    }

    private String formatElapsed() {
        long sec = Math.max(0L, (System.currentTimeMillis() - sessionStartedAt) / 1000L);
        long m = sec / 60L;
        long s = sec % 60L;
        return String.format("%d:%02d", m, s);
    }

    private int globalWaveNumber() {
        if (level <= 1) {
            return wave;
        }
        if (level == 2) {
            return 6 + wave;
        }
        return 12 + wave;
    }

    private void tickHazards() {
        hazardTick++;
        if (hazardTick % 20L != 0L) {
            return;
        }
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.isInWater() || player.getLocation().getBlock().isLiquid()) {
                if (hazardTick % 40L == 0L) {
                    player.damage(4.0D);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0, false, true, true));
                }
                ColorUtils.sendTitle(player,
                    "<gradient:#00C1FF:#FF3B3B><bold>TRUJACA WODA!</bold></gradient></shadow>",
                    "<gradient:#FFD900:#FF9B00>UCIEKAJ</gradient> &f— woda Cię bije!</shadow>",
                    0, 25, 5);
                MultiActionBarService.get().send(player, "dungeon-water",
                    "<gradient:#FF3B3B:#00C1FF>WODA BIJE!</gradient> &8— &fwynurz sie natychmiast</shadow>", 20L);
            }
        }
        // Moby dungeonowe tez dostaja dmg od wody (duzo slabiej niz gracze — nie maja zbroi).
        if (hazardTick % 40L == 0L) {
            for (UUID mobId : new HashSet<>(aliveMobs)) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(mobId);
                if (!(entity instanceof LivingEntity living) || living.isDead()) {
                    continue;
                }
                if (living.isInWater() || living.getLocation().getBlock().isLiquid()) {
                    living.damage(1.5D);
                    living.setFireTicks(0);
                    living.getWorld().spawnParticle(Particle.WATER_SPLASH, living.getLocation().add(0, 1, 0),
                        12, 0.3, 0.4, 0.3, 0.02);
                }
            }
        }
    }

    public void voteSkip(Player player) {
        if (phase != Phase.TUTORIAL || !players.contains(player.getUniqueId())) {
            return;
        }
        skipVotes.add(player.getUniqueId());
        manager.getEventManager().playClick(player);
    }

    public void handlePlayerDeath(Player player) {
        if (!players.contains(player.getUniqueId()) || phase == Phase.ENDED) {
            return;
        }
        if (phase == Phase.TUTORIAL || phase == Phase.PREPARE || phase == Phase.UPGRADE
            || phase == Phase.COIN_SHOP || phase == Phase.BOSS_CINEMATIC || phase == Phase.FROZEN_REJOIN) {
            Location hold = player.getLocation().clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || phase == Phase.ENDED) {
                    return;
                }
                if (player.isDead()) {
                    player.spigot().respawn();
                }
                player.teleport(hold);
                player.setGameMode(GameMode.ADVENTURE);
                player.setHealth(player.getMaxHealth());
                giveKits(player);
            }, 1L);
            return;
        }
        // Snapshot tylko jesli jeszcze nie ma — NIE nadpisuj pustym po smierci.
        if (!savedInventories.containsKey(player.getUniqueId())) {
            saveInventory(player);
        }
        deadSpectators.add(player.getUniqueId());
        killStreak.put(player.getUniqueId(), 0);
        Location deathLoc = player.getLocation().clone();
        spawnDeathMarker(player, deathLoc);
        if (!hasAliveFighters()) {
            Bukkit.getScheduler().runTask(plugin, this::offerWipeReviveOrLose);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || phase == Phase.ENDED) {
                return;
            }
            if (player.isDead()) {
                player.spigot().respawn();
            }
            player.teleport(deathLoc);
            player.setGameMode(GameMode.SPECTATOR);
            player.setFlySpeed(0.12F);
            try {
                player.setSpectatorTarget(null);
            } catch (Throwable ignored) {
            }
            // Przywroc ekwipunek po respawnie (keepInventory bywa zbijane przez inne pluginy).
            restoreInventory(player);
            player.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.spectator",
                "<gradient:#FF5C5C:#FF9B00>Zginales!</gradient> &7Teammate sneak = darmowe ozywienie</shadow>")));
            if (wipeReviveOpen) {
                openWipeReviveGui(player);
            } else {
                int cost = paidReviveCost();
                ColorUtils.sendTitle(player,
                    "<gradient:#FF3B3B:#FF9B00><bold>SPECTATOR</bold></gradient></shadow>",
                    "Sneak przy ciele &8| &ePPM = odrodzenie za " + cost + " VPLN</shadow>",
                    8, 70, 12);
                MultiActionBarService.get().send(player, "dungeon-paid-revive",
                    "<gradient:#FFD900:#FF9B00>PPM</gradient> &f= odrodz sie za &e" + cost + " VPLN</shadow>", 80L);
            }
            dipMusicOnDeath();
            if (!hasAliveFighters()) {
                offerWipeReviveOrLose();
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && deadSpectators.contains(player.getUniqueId())) {
                restoreInventory(player);
            }
        }, 5L);
    }

    public void saveInventory(Player player) {
        if (player == null) {
            return;
        }
        InvSnapshot snap = new InvSnapshot(player.getInventory());
        // Nie nadpisuj dobrego snapshota pustym (po smierci inv bywa juz pusty).
        InvSnapshot existing = savedInventories.get(player.getUniqueId());
        if (existing != null && !existing.isEmpty() && snap.isEmpty()) {
            return;
        }
        if (snap.isEmpty() && existing != null) {
            return;
        }
        savedInventories.put(player.getUniqueId(), snap);
    }

    public void restoreInventory(Player player) {
        if (player == null) {
            return;
        }
        InvSnapshot snap = savedInventories.get(player.getUniqueId());
        if (snap != null && !snap.isEmpty()) {
            snap.apply(player);
        }
    }

    private boolean hasAliveFighters() {
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player fighter = Bukkit.getPlayer(uuid);
            if (fighter == null || !fighter.isOnline()) {
                continue;
            }
            if (fighter.isDead()) {
                continue;
            }
            if (fighter.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** Spectator: latanie tylko za zywymi teammate, max 15 kratek. */
    public void tickDeadSpectators() {
        if (phase == Phase.ENDED || phase == Phase.TUTORIAL || phase == Phase.BOSS_CINEMATIC) {
            return;
        }
        // Wipe-offer: ADVENTURE + GUI (SPECTATOR nie otwiera inventory).
        if (wipeReviveOpen) {
            // Zabezpieczenie: gdy task zginie (reload / cancel), nie wisimy w freeze w nieskonczonosc.
            if (System.currentTimeMillis() >= wipeReviveEndsAtMs) {
                expireWipeReviveOffer("&#FF0000✘ &8Ξ &cCzas na odrodzenie minal — przegrana.");
                return;
            }
            int cost = wipeReviveCost();
            int left = wipeReviveSecondsLeft();
            for (UUID uuid : new HashSet<>(deadSpectators)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                prepareWipeReviveViewer(player);
                MultiActionBarService.get().send(player, "dungeon-wipe-revive",
                    "<gradient:#FFD900:#FF9B00>Odrodzenie</gradient> &e" + cost + " VPLN"
                        + " &8|&f cala druzyna &8|&e " + left + "s &8|&f /portfel", 25L);
            }
            return;
        }
        double maxDist = Math.max(8.0D, settings.getDouble("spectator-teammate-radius", 15.0D));
        double maxDistSq = maxDist * maxDist;
        for (UUID uuid : new HashSet<>(deadSpectators)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SPECTATOR);
            }
            try {
                if (player.getSpectatorTarget() != null) {
                    player.setSpectatorTarget(null);
                }
            } catch (Throwable ignored) {
            }
            player.setFlySpeed(0.15F);
            int cost = paidReviveCost();
            MultiActionBarService.get().send(player, "dungeon-paid-revive",
                "<gradient:#FFD900:#FF9B00>PPM</gradient> &f= odrodz sie za &e" + cost + " VPLN"
                    + " &8| &fTeammate sneak = za darmo</shadow>", 25L);
            Location mate = nearestLivingTeammate(player);
            if (mate != null) {
                if (!player.getWorld().equals(mate.getWorld())
                    || player.getLocation().distanceSquared(mate) > maxDistSq) {
                    // Cofnij w strone teammate (nie w srodek hitboxa).
                    Location pull = mate.clone().add(0, 1.4D, 0);
                    Vector away = player.getLocation().toVector().subtract(mate.toVector());
                    if (away.lengthSquared() > 0.01D) {
                        away = away.normalize().multiply(Math.min(maxDist * 0.85D, 12.0D));
                        pull = mate.clone().add(away.getX(), 1.4D, away.getZ());
                    }
                    player.teleport(pull);
                }
                continue;
            }
            // Brak zywych — trzymaj przy ciele.
            Location corpse = deathLocations.get(uuid);
            if (corpse != null && corpse.getWorld() != null
                && (!player.getWorld().equals(corpse.getWorld())
                || player.getLocation().distanceSquared(corpse) > 36.0D)) {
                player.teleport(corpse.clone().add(0, 1.5D, 0));
            }
        }
    }

    /** SPECTATOR nie otwiera GUI — adventure + flight lock na czas oferty wipe. */
    private void prepareWipeReviveViewer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.isDead()) {
            try {
                player.spigot().respawn();
            } catch (Throwable ignored) {
            }
        }
        if (player.getGameMode() != GameMode.ADVENTURE) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.0F);
        player.setWalkSpeed(0.0F);
        player.setGravity(false);
        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
    }

    /** Zdejmij freeze po wipe (walk/fly 0 + no gravity) — zeby nie zostawal „zfreezowany”. */
    private void clearWipeReviveViewerState(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setGravity(true);
        player.setFlySpeed(0.1F);
        player.setWalkSpeed(0.2F);
        if (deadSpectators.contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setFlySpeed(0.15F);
        } else {
            forceFighterState(player);
        }
    }

    private void clearAllWipeReviveViewerStates() {
        for (UUID uuid : players) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                clearWipeReviveViewerState(online);
            }
        }
    }

    private Location nearestLivingTeammate(Player spectator) {
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID uuid : players) {
            if (uuid.equals(spectator.getUniqueId()) || deadSpectators.contains(uuid)) {
                continue;
            }
            Player mate = Bukkit.getPlayer(uuid);
            if (mate == null || !mate.isOnline() || mate.getGameMode() == GameMode.SPECTATOR || mate.isDead()) {
                continue;
            }
            if (!mate.getWorld().equals(spectator.getWorld())) {
                continue;
            }
            double dist = spectator.getLocation().distanceSquared(mate.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = mate.getLocation();
            }
        }
        return best;
    }

    public double getSpectatorTeammateRadius() {
        return Math.max(8.0D, settings.getDouble("spectator-teammate-radius", 15.0D));
    }

    public Location getDeathLocation(UUID uuid) {
        return deathLocations.get(uuid);
    }

    private void beginPrepare() {
        if (tutorialSlideTask != null) {
            tutorialSlideTask.cancel();
            tutorialSlideTask = null;
        }
        phase = Phase.PREPARE;
        clearMobs();
        ensureAllPlayersPresent();
        giveKits();
        healPartyFull();
        movementLocked = true;
        Location spawn = locations.getPlayerSpawn(world);
        int prepare = settings.getInt("prepare-seconds", 5);
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                forceFighterState(player);
                if (spawn != null) {
                    player.teleport(spawn);
                }
                ColorUtils.sendTitle(player,
                    "<gradient:#00C1FF:#00FFC1><bold>PRZYGOTUJ SIE</bold></gradient></shadow>",
                    "Start za <gradient:#FFD900:#FF9B00>" + prepare + "s</gradient></shadow>",
                    8, 45, 8);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase != Phase.PREPARE) {
                return;
            }
            movementLocked = false;
            startWave(1, 1);
        }, prepare * 20L);
    }

    private void startWave(int level, int waveNumber) {
        boolean levelChanged = this.level != level;
        this.level = level;
        this.wave = waveNumber;
        this.bossPhase2 = false;
        phase = Phase.WAVE;
        revivedThisWave.clear();
        reviveProgressByTarget.clear();
        if (levelChanged) {
            startAmbientLoop();
        }
        clearMobs();
        ensureAllPlayersPresent();
        coinSystem.setCollectable(false);
        clearMiniEvent();
        Location loc = locations.getWave(level, waveNumber, world);
        if (loc == null) {
            startBossCinematic(level);
            return;
        }
        // Fala 12: najpierw glosowanie — moby dopiero po wyborze.
        if (globalWaveNumber() == 12 && !wave12RewardClaimed) {
            waveTotal = 0;
            waveLeft = 0;
            openWave12Reward();
            return;
        }
        spawnCurrentWaveMobs();
    }

    private void clearMiniEvent() {
        activeMiniEvent = null;
        coinMultiplier = 1;
    }

    private void rollMiniEvent() {
        MiniEvent[] pool = MiniEvent.values();
        activeMiniEvent = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        coinMultiplier = activeMiniEvent == MiniEvent.DOUBLE_COINS ? 2 : 1;
        String title;
        String sub;
        switch (activeMiniEvent) {
            case DOUBLE_COINS -> {
                title = "<gradient:#FFD900:#FF9B00><bold>MINI-EVENT</bold></gradient></shadow>";
                sub = "<gradient:#FFD900:#FFFFFF>Podwojne coiny</gradient> &8— &fta fala!</shadow>";
            }
            case FRENZY -> {
                title = "<gradient:#FF5C5C:#FF9B00><bold>MINI-EVENT</bold></gradient></shadow>";
                sub = "<gradient:#FF5C5C:#FFD900>Frenzy</gradient> &8— &fwiecej wrogow!</shadow>";
            }
            case BOUNTY -> {
                title = "<gradient:#C800FF:#FFD900><bold>MINI-EVENT</bold></gradient></shadow>";
                sub = "<gradient:#C800FF:#FFFFFF>Bounty</gradient> &8— &fzabij elite!</shadow>";
            }
            case SPEED_RUSH -> {
                title = "<gradient:#00C1FF:#00FF5A><bold>MINI-EVENT</bold></gradient></shadow>";
                sub = "<gradient:#00C1FF:#FFFFFF>Speed Rush</gradient> &8— &fSpeed II 60s!</shadow>";
            }
            default -> {
                title = "<gradient:#FFD900:#C800FF><bold>MINI-EVENT</bold></gradient></shadow>";
                sub = "Bonusowa fala!</shadow>";
            }
        }
        broadcastRaw("<gradient:#FFD900:#C800FF>Mini-event</gradient> &8— &f" + activeMiniEvent.name().replace('_', ' '));
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || deadSpectators.contains(uuid)) {
                continue;
            }
            sendDungeonTitle(player, title, sub, 8, 45, 12);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.85F, 1.25F);
            if (activeMiniEvent == MiniEvent.SPEED_RUSH) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 1, false, true, true));
            }
        }
    }

    private void spawnCurrentWaveMobs() {
        Location loc = locations.getWave(level, wave, world);
        if (loc == null) {
            startBossCinematic(level);
            return;
        }
        if (globalWaveNumber() > 0 && globalWaveNumber() % 3 == 0 && activeMiniEvent == null) {
            rollMiniEvent();
        }
        int partyBonus = Math.max(0, startingPartySize() - 1) * Math.max(0, settings.getInt("party-mobs-per-player", 1));
        int count = Math.max(4, settings.getInt("mobs-per-wave-base", 5) + level + (wave / 3)
            + Math.max(0, ticketMultiplier - 1) + partyBonus);
        if (activeMiniEvent == MiniEvent.FRENZY) {
            count = Math.max(count + 3, (int) Math.ceil(count * 1.4D));
        }
        waveTotal = count;
        waveLeft = count;
        List<LivingEntity> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location safe = findSafeSpawn(spread(loc, i, count));
            LivingEntity mob = spawnWaveMob(safe, false);
            if (mob != null) {
                spawned.add(mob);
            }
        }
        if (activeMiniEvent == MiniEvent.BOUNTY) {
            LivingEntity elite = spawnBountyElite(findSafeSpawn(spread(loc, count, count + 1)));
            if (elite != null) {
                spawned.add(elite);
                waveTotal++;
                waveLeft++;
            }
        }
        // 1-2 elite: tylko Berserk / Bombowiec (bez tarczy).
        int elites = 1 + (startingPartySize() >= 3 || ticketMultiplier >= 3 ? 1 : 0);
        for (int e = 0; e < elites; e++) {
            LivingEntity elite = spawnWaveElite(findSafeSpawn(spread(loc, count + 2 + e, count + 4)), e);
            if (elite != null) {
                spawned.add(elite);
                waveTotal++;
                waveLeft++;
            }
        }
        hazardSystem.spawnForWave(loc);
        assignFocusSlots(spawned);
        String eventHint = activeMiniEvent == null ? "" : " &8| &e" + activeMiniEvent.name().replace('_', ' ');
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendDungeonTitle(player,
                    "<gradient:#00C1FF:#2BD4FF><bold>FALA " + wave + "</bold></gradient></shadow>",
                    "Poziom <gradient:#FFD900:#FF9B00>" + level + "</gradient> &8| &fWrogowie: <gradient:#FF5C5C:#FF9B00>"
                        + waveTotal + "</gradient>" + eventHint + "</shadow>",
                    8, 40, 10);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.2F);
            }
        }
        if (wavesCleared == 4 || (level == 1 && wave == 4)) {
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    sendDungeonTitle(player,
                        "<gradient:#FFB800:#FF5C5C><bold>UWAGA</bold></gradient></shadow>",
                        "Uwazaj na <gradient:#FF5C5C:#FF9B00>upadki</gradient> i <gradient:#00C1FF:#2BD4FF>trujaca wode</gradient></shadow>",
                        10, 55, 12);
                }
            }
        }
    }

    private int maxFocusCount() {
        int alive = 0;
        for (UUID uuid : players) {
            if (!deadSpectators.contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                    alive++;
                }
            }
        }
        alive = Math.max(1, alive);
        // 1 gracz = 3 focus, kazdy kolejny +1 (5 graczy = 7)
        return Math.min(aliveMobs.size(), 3 + Math.max(0, alive - 1));
    }

    private void assignFocusSlots(List<LivingEntity> candidates) {
        focusedMobs.clear();
        idleMobs.clear();
        int max = maxFocusCount();
        int i = 0;
        for (LivingEntity entity : candidates) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            if (i < max) {
                setFocused(mob, true);
                i++;
            } else {
                setFocused(mob, false);
            }
        }
    }

    private void setFocused(Mob mob, boolean focused) {
        if (focused) {
            idleMobs.remove(mob.getUniqueId());
            focusedMobs.add(mob.getUniqueId());
            mob.setAI(true);
            mob.setAware(true);
            Player target = nearestPlayer(mob.getLocation());
            if (target != null) {
                mob.setTarget(target);
            }
        } else {
            focusedMobs.remove(mob.getUniqueId());
            idleMobs.add(mob.getUniqueId());
            mob.setAI(false);
            mob.setAware(false);
            mob.setTarget(null);
            mob.setVelocity(new Vector(0, 0, 0));
        }
    }

    private void promoteIdleToFocus() {
        if (phase != Phase.WAVE) {
            return;
        }
        while (focusedMobs.size() < maxFocusCount()) {
            UUID nextId = null;
            for (UUID id : idleMobs) {
                nextId = id;
                break;
            }
            if (nextId == null) {
                break;
            }
            org.bukkit.entity.Entity entity = Bukkit.getEntity(nextId);
            if (!(entity instanceof Mob mob) || mob.isDead()) {
                idleMobs.remove(nextId);
                continue;
            }
            setFocused(mob, true);
        }
    }

    private void startBossCinematic(int level) {
        phase = Phase.BOSS_CINEMATIC;
        clearMobs();
        Location cam = locations.getBossCamera(level, world);
        Location bossLoc = locations.getBoss(level, world);
        if (cam == null || bossLoc == null) {
            startBossFight(level);
            return;
        }
        LivingEntity boss = spawnBoss(level, bossLoc);
        if (boss != null) {
            bossId = boss.getUniqueId();
            boss.setAI(false);
            boss.setInvulnerable(true);
            boss.setPersistent(true);
            boss.setRemoveWhenFarAway(false);
        }
        // Smooth cam: ticki zalezne od dystansu + preload chunkow (bez 4-fps skokow).
        int configuredFly = Math.max(80, settings.getInt("cinematic-fly-ticks", 120));
        int holdTicks = Math.max(40, settings.getInt("boss-cinematic-seconds", 5) * 20);
        Map<UUID, Location> origins = new HashMap<>();
        Map<UUID, GameMode> modes = new HashMap<>();
        Map<UUID, Integer> flyTicksByPlayer = new HashMap<>();
        preloadChunk(cam);
        preloadChunk(bossLoc);
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || deadSpectators.contains(uuid)) {
                continue;
            }
            Location origin = player.getLocation().clone();
            origins.put(uuid, origin);
            modes.put(uuid, player.getGameMode());
            double dist = origin.distance(cam);
            // ~5 tickow na blok = plynnosc; min z configu.
            int fly = Math.max(configuredFly, (int) Math.ceil(dist * 5.0D));
            fly = Math.min(fly, 200);
            flyTicksByPlayer.put(uuid, fly);
            preloadChunk(origin);
            // Preload po drodze.
            for (int i = 1; i <= 4; i++) {
                double p = i / 5.0D;
                Location mid = origin.clone().add(
                    (cam.getX() - origin.getX()) * p,
                    (cam.getY() - origin.getY()) * p,
                    (cam.getZ() - origin.getZ()) * p
                );
                preloadChunk(mid);
            }
            player.setGameMode(GameMode.SPECTATOR);
            player.setFlySpeed(0.0F);
            player.setGravity(false);
        }
        if (origins.isEmpty()) {
            startBossFight(level);
            return;
        }

        final int maxFly = flyTicksByPlayer.values().stream().mapToInt(Integer::intValue).max().orElse(configuredFly);
        final int total = maxFly + holdTicks;
        final int[] step = {0};
        if (cinematicTask != null) {
            cinematicTask.cancel();
            cinematicTask = null;
        }
        cinematicTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (phase != Phase.BOSS_CINEMATIC) {
                if (cinematicTask != null) {
                    cinematicTask.cancel();
                    cinematicTask = null;
                }
                return;
            }
            int s = step[0]++;
            for (Map.Entry<UUID, Location> entry : origins.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) {
                    continue;
                }
                player.setGameMode(GameMode.SPECTATOR);
                Location origin = entry.getValue();
                int flyTicks = flyTicksByPlayer.getOrDefault(entry.getKey(), configuredFly);
                if (s <= flyTicks) {
                    double p = easeInOut(s / (double) Math.max(1, flyTicks));
                    Location frame = lerpLook(origin, cam, p, bossLoc);
                    // Velocity chase = client interpolates (plynnie). Teleport = 20fps chop.
                    EventManager.smoothSpectatorChase(player, frame, 0.62D);
                } else {
                    EventManager.smoothSpectatorChase(player, lerpLook(cam, cam, 1.0D, bossLoc), 0.75D);
                    if (s == maxFly + 5) {
                        sendDungeonTitle(
                            player,
                            settings.getString("messages.boss-title", "&#FF3B3B&lPokonaj bossa!"),
                            settings.getString("messages.boss-subtitle", "&fPokonaj straznika poziomu &#FFD900{LEVEL}&f!")
                                .replace("{LEVEL}", String.valueOf(level)),
                            10, 50, 10
                        );
                        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.35F, 1.4F);
                    }
                }
            }
            if (s >= total) {
                phase = Phase.BOSS;
                for (Map.Entry<UUID, Location> entry : origins.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null) {
                        continue;
                    }
                    player.setGravity(true);
                    player.teleport(entry.getValue());
                    player.setFlySpeed(0.1F);
                    if (deadSpectators.contains(entry.getKey())) {
                        player.setGameMode(GameMode.SPECTATOR);
                    } else {
                        // ZAWSZE adventure — nie przywracaj starego SPECTATOR (bug fly).
                        forceFighterState(player);
                    }
                }
                if (bossId != null) {
                    org.bukkit.entity.Entity entity = Bukkit.getEntity(bossId);
                    if (entity instanceof LivingEntity living) {
                        living.setAI(true);
                        living.setInvulnerable(false);
                        living.setPersistent(true);
                        living.setRemoveWhenFarAway(false);
                    }
                }
                ensureAllPlayersPresent();
                if (cinematicTask != null) {
                    cinematicTask.cancel();
                    cinematicTask = null;
                }
            }
        }, 1L, 1L);
    }

    private void preloadChunk(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().getChunkAtAsync(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private double easeInOut(double t) {
        t = Math.max(0.0D, Math.min(1.0D, t));
        // smootherstep — mniej "schodkow" niz klasyczny ease
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    private void startBossFight(int level) {
        phase = Phase.BOSS;
        bossPhase2 = false;
        Location bossLoc = locations.getBoss(level, world);
        if (bossLoc != null && (bossId == null || Bukkit.getEntity(bossId) == null)) {
            LivingEntity boss = spawnBoss(level, bossLoc);
            if (boss != null) {
                bossId = boss.getUniqueId();
            }
        }
        updateHudBars();
    }

    public void onMobDeath(LivingEntity entity) {
        boolean wasBoss = (bossId != null && bossId.equals(entity.getUniqueId()))
            || entity.getScoreboardTags().contains("holly_dungeon_boss");
        Player killer = entity.getKiller();
        if (killer != null && players.contains(killer.getUniqueId())) {
            killCounts.merge(killer.getUniqueId(), 1, Integer::sum);
            int streak = killStreak.merge(killer.getUniqueId(), 1, Integer::sum);
            if (activeBuffUpgrades.getOrDefault(killer.getUniqueId(), Set.of())
                .contains(DungeonUpgradeGUI.UpgradeType.LIFESTEAL)) {
                double nh = Math.min(killer.getMaxHealth(), killer.getHealth() + 2.0D);
                killer.setHealth(nh);
                killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0, 1.2D, 0), 3, 0.2, 0.2, 0.2, 0);
            }
            if (streak >= 3) {
                MultiActionBarService.get().send(killer, "dungeon-streak",
                    "<gradient:#FFD900:#FF9B00>Seria zabojstw</gradient> &8×&f" + streak + "</shadow>", 35L);
                if (streak == 5) {
                    killer.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 60, 0));
                    killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 1.6F);
                }
            }
        }
        aliveMobs.remove(entity.getUniqueId());
        focusedMobs.remove(entity.getUniqueId());
        idleMobs.remove(entity.getUniqueId());
        mobNames.remove(entity.getUniqueId());
        lastMobNameHp.remove(entity.getUniqueId());
        // Roblox-style coin burst (zbieralne po fali).
        if (phase == Phase.WAVE || phase == Phase.BOSS) {
            boolean bounty = entity.getScoreboardTags().contains("holly_dungeon_bounty");
            coinSystem.spawnFromKill(entity.getLocation(), wasBoss || bounty, killer);
            if (bounty && killer != null) {
                broadcastRaw("<gradient:#C800FF:#FFD900>Bounty zbity</gradient> &8— &f" + killer.getName() + "!</shadow>");
            }
        }
        if (wasBoss) {
            clearHudBars();
            onBossDeath(entity.getLocation());
            return;
        }
        if (phase != Phase.WAVE) {
            return;
        }
        waveLeft = Math.max(0, waveLeft - 1);
        promoteIdleToFocus();
        if (waveLeft > 0) {
            return;
        }
        completeWaveClear(false);
    }

    /**
     * Admin: zabija aktualna fale / bossa i przechodzi dalej (z blokada antyspam).
     */
    public boolean adminSkipFala() {
        if (adminSkipBusy || bossRewardAnimating || phase == Phase.ENDED || phase == Phase.TUTORIAL
            || phase == Phase.PREPARE || phase == Phase.UPGRADE || phase == Phase.COIN_SHOP
            || phase == Phase.FROZEN_REJOIN || phase == Phase.BOSS_REWARD) {
            return false;
        }
        if (isWave12Voting()) {
            adminSkipBusy = true;
            wave12RewardClaimed = true;
            wave12RewardOpen = false;
            for (UUID uuid : players) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.closeInventory();
                }
            }
            clearMobs();
            spawnCurrentWaveMobs();
            adminSkipBusy = false;
            return true;
        }
        if (phase == Phase.WAVE) {
            adminSkipBusy = true;
            cancelProgressTask();
            clearMobs();
            waveLeft = 0;
            broadcastRaw("&#FFD900⚠ &8Ξ &eAdmin pominął fale.");
            completeWaveClear(true);
            return true;
        }
        if (phase == Phase.BOSS || phase == Phase.BOSS_CINEMATIC) {
            adminSkipBusy = true;
            cancelCinematic();
            clearMobs();
            broadcastRaw("&#FFD900⚠ &8Ξ &eAdmin pominął bossa.");
            Location skipLoc = locations.getBoss(level, world);
            if (skipLoc == null) {
                skipLoc = locations.getPlayerSpawn(world);
            }
            onBossDeath(skipLoc);
            return true;
        }
        return false;
    }

    private void cancelCinematic() {
        if (cinematicTask != null) {
            cinematicTask.cancel();
            cinematicTask = null;
        }
        // Wyrwij z BOSS_CINEMATIC, zeby stary timer nie doszedl do startBossFight.
        if (phase == Phase.BOSS_CINEMATIC) {
            phase = Phase.BOSS;
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                player.setGravity(true);
                player.setFlySpeed(0.1F);
                if (!deadSpectators.contains(uuid)) {
                    forceFighterState(player);
                }
            }
        }
    }

    private void cancelProgressTask() {
        if (progressTask != null) {
            progressTask.cancel();
            progressTask = null;
        }
    }

    private void completeWaveClear(boolean adminFast) {
        broadcastRaw(settings.getString("messages.wave-clear", "&#00FF3B✔ &8Ξ &#4EFA76Fala pokonana! Podazaj dalej sciezka."));
        wavesCleared++;
        Location fx = locations.getWave(level, wave, world);
        if (fx == null) {
            fx = locations.getPlayerSpawn(world);
        }
        if (fx != null) {
            world.spawnParticle(Particle.TOTEM, fx.clone().add(0, 1.5D, 0), 40, 1.2, 0.8, 1.2, 0.15);
            world.playSound(fx, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.55F, 1.35F);
        }
        for (UUID uuid : players) {
            killStreak.put(uuid, 0);
        }
        // Co 2 fale: full heal.
        if (!adminFast && wavesCleared % 2 == 0) {
            healPartyFull();
        }
        Runnable next = () -> {
            progressTask = null;
            adminSkipBusy = false;
            if (phase == Phase.ENDED || phase == Phase.BOSS_REWARD || bossRewardAnimating) {
                return;
            }
            int max = locations.maxWaves(level);
            if (wave >= max) {
                startBossCinematic(level);
            } else {
                startWave(level, wave + 1);
            }
        };
        // Upgrade po kazdej fali.
        if (!adminFast) {
            openUpgradeChoice(next);
        } else {
            cancelProgressTask();
            progressTask = Bukkit.getScheduler().runTaskLater(plugin, next, 5L);
        }
    }

    private void onBossDeath(Location deathLocation) {
        if (bossRewardAnimating || phase == Phase.ENDED || phase == Phase.BOSS_REWARD) {
            return;
        }
        final int bossLevel = level;
        bossId = null;
        bossPhase2 = false;
        clearHudBars();
        cancelCinematic();
        // Po bossie nie zostawiaj falowych mobow — moga zabic podczas losowania / win.
        clearMobs();
        phase = Phase.BOSS_REWARD;
        bossRewardAnimating = true;
        broadcastRaw(settings.getString("messages.boss-down", "&#00FF3B✔ &8Ξ &#4EFA76Boss poziomu {LEVEL} pokonany!")
            .replace("{LEVEL}", String.valueOf(bossLevel)));

        grantBossCoinReward();

        Location animLoc = deathLocation != null ? deathLocation.clone() : null;
        if (animLoc == null || animLoc.getWorld() == null) {
            animLoc = locations.getBoss(bossLevel, world);
        }
        if (animLoc == null) {
            animLoc = locations.getPlayerSpawn(world);
        }
        int ticketCost = party != null ? party.getTicketCost() : 0;
        boolean premiumPool = ticketCost > 0;
        List<ItemStack> pool = manager.getBossRewards(bossLevel, premiumPool);
        int pickCount = DungeonSessionManager.bossItemCount(ticketCost);
        Runnable continueProgress = () -> {
            bossRewardAnimating = false;
            adminSkipBusy = false;
            if (phase == Phase.ENDED) {
                return;
            }
            if (bossLevel >= 3) {
                win();
                return;
            }
            cancelProgressTask();
            progressTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                progressTask = null;
                if (phase != Phase.ENDED) {
                    startWave(bossLevel + 1, 1);
                }
            }, 40L);
        };

        plugin.getLogger().info("[Dungeon] Boss reward pool size=" + pool.size()
            + " (premium=" + premiumPool + ", tickets=" + ticketCost + ", pick=" + pickCount + ")");
        if (pool.isEmpty() || animLoc == null || animLoc.getWorld() == null) {
            if (pool.isEmpty()) {
                broadcastRaw("&#FFD900⚠ &8Ξ &eBrak itemow w puli nagród bossa &f" + bossLevel
                    + " &e(/event → Nagrody). Ustaw &fdarmowe &ealbo &fpremium&e.");
                plugin.getLogger().warning("[Dungeon] Pusta pula boss-rewards lvl " + bossLevel
                    + " (premium=" + premiumPool + ", tickets=" + ticketCost + ")");
            }
            continueProgress.run();
            return;
        }

        List<Player> participants = new ArrayList<>();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                participants.add(player);
            }
        }
        // Jedno losowanie per gracz — animacja i /odbierz pokazuja TO SAMO.
        Map<UUID, List<ItemStack>> personalByPlayer = rollPersonalBossRewards(pool, pickCount);

        if (participants.isEmpty()) {
            grantPersonalBossRewards(personalByPlayer);
            continueProgress.run();
            return;
        }

        broadcastRaw("<gradient:#FFD900:#00FFC1>Losowanie nagród</gradient> &8— &fprzy ciele bossa...</shadow>");
        Bukkit.getScheduler().runTaskLater(plugin, () -> grantPersonalBossRewards(personalByPlayer), 8L);
        try {
            new DungeonBossRewardAnimator(
                plugin,
                animLoc,
                pool,
                pickCount,
                participants,
                ignored -> { },
                continueProgress
            ).start();
        } catch (Throwable exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Dungeon] Animacja nagrody bossa fail", exception);
            continueProgress.run();
        }
    }

    private Map<UUID, List<ItemStack>> rollPersonalBossRewards(List<ItemStack> pool, int pickCount) {
        Map<UUID, List<ItemStack>> out = new HashMap<>();
        if (pool == null || pool.isEmpty() || pickCount <= 0) {
            return out;
        }
        Set<UUID> targets = new HashSet<>(players);
        if (party != null) {
            for (DungeonParty.Member member : party.getMemberList()) {
                if (member != null && member.getUuid() != null) {
                    targets.add(member.getUuid());
                }
            }
        }
        for (UUID uuid : targets) {
            List<ItemStack> personal = pickBossRewardsOffline(pool, pickCount);
            if (!personal.isEmpty()) {
                out.put(uuid, personal);
            }
        }
        return out;
    }

    private void grantPersonalBossRewards(Map<UUID, List<ItemStack>> personalByPlayer) {
        if (personalByPlayer == null || personalByPlayer.isEmpty()) {
            return;
        }
        int granted = 0;
        for (Map.Entry<UUID, List<ItemStack>> entry : personalByPlayer.entrySet()) {
            List<ItemStack> personal = entry.getValue();
            if (personal == null || personal.isEmpty()) {
                continue;
            }
            giveBossRewardsToOdbierz(entry.getKey(), personal);
            granted++;
            if (plugin != null) {
                StringBuilder names = new StringBuilder();
                for (int i = 0; i < personal.size(); i++) {
                    if (i > 0) {
                        names.append(", ");
                    }
                    names.append(readableBossRewardName(personal.get(i)));
                }
                plugin.getLogger().info("[Dungeon] Boss reward personal uuid=" + entry.getKey()
                    + " pick=" + personal.size() + " -> " + names);
            }
        }
        if (granted > 0) {
            broadcastRaw("&#00FF3B✔ &8Ξ &#4EFA76Nagrody bossa w &e/odbierz"
                + " &7(kazdy gracz = wlasny drop).");
        }
    }

    private List<ItemStack> pickBossRewardsOffline(List<ItemStack> pool, int pickCount) {
        List<ItemStack> bag = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (ItemStack stack : pool) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                bag.add(stack);
                keys.add(lookKey(stack));
            }
        }
        List<ItemStack> picks = new ArrayList<>();
        if (bag.isEmpty()) {
            return picks;
        }
        int take = Math.max(1, Math.min(pickCount, bag.size()));
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < take && !bag.isEmpty(); i++) {
            int idx = rng.nextInt(bag.size());
            ItemStack picked = bag.remove(idx).clone();
            String key = keys.remove(idx);
            picks.add(picked);
            if (i + 1 < take) {
                for (int j = keys.size() - 1; j >= 0; j--) {
                    if (key.equals(keys.get(j))) {
                        bag.remove(j);
                        keys.remove(j);
                    }
                }
            }
        }
        return picks;
    }

    private static String lookKey(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        String cmd = "";
        try {
            if (stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasCustomModelData()) {
                cmd = Integer.toString(stack.getItemMeta().getCustomModelData());
            }
        } catch (Throwable ignored) {
        }
        return stack.getType().name() + ":" + cmd;
    }

    /** Legacy: osobne losowanie + grant (preferuj rollPersonal + grantPersonal). */
    private void queueBossItemRewardsPersonal(List<ItemStack> pool, int pickCount) {
        grantPersonalBossRewards(rollPersonalBossRewards(pool, pickCount));
    }

    private static String readableBossRewardName(ItemStack stack) {
        if (stack == null) {
            return "?";
        }
        try {
            if (stack.hasItemMeta() && stack.getItemMeta() != null && stack.getItemMeta().hasDisplayName()) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(stack.getItemMeta().displayName());
            }
        } catch (Throwable ignored) {
        }
        return stack.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    /** Nagrody bossow + win-rewards (win nadal po koncu; itemy bossa juz w /odbierz). */
    public void deliverRewardsAtServerSpawn(boolean won) {
        // pendingBossRewards celowo puste — itemy poszly do /odbierz przy losowaniu.
        if (won) {
            grantWinRewards();
        }
    }

    private void flushPendingBossRewards() {
        // Legacy no-op: nagrody ida do /odbierz w queueBossItemRewards.
        pendingBossRewards.clear();
    }

    private void giveBossRewardsToOdbierz(UUID uuid, List<ItemStack> personal) {
        if (uuid == null || personal == null || personal.isEmpty()) {
            return;
        }
        if (plugin.getPickupManager() == null) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                DungeonBossRewardAnimator.giveItems(online, personal);
                online.sendMessage(ColorUtils.colorizeComponent(
                    "&#FFD900⚠ &8Ξ &ePickup wylaczony — itemy wrzucone do EQ."
                ));
            }
            return;
        }
        for (int i = 0; i < personal.size(); i++) {
            ItemStack stack = personal.get(i);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            ItemStack copy = stack.clone();
            int amt = Math.max(1, copy.getAmount());
            // /odbierz: amount w PickupItem = ile sztuk po claim; szablon pakietu ma 1.
            ItemStack template = copy.clone();
            template.setAmount(1);
            try {
                plugin.getPickupManager().giveItemDirect(uuid, "dungeon-boss", template, amt);
            } catch (Throwable exception) {
                plugin.getLogger().warning("[Dungeon] Nie udalo sie dac nagrody do /odbierz: " + exception.getMessage());
            }
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < personal.size(); i++) {
                if (i > 0) {
                    names.append("&7, ");
                }
                names.append("&f").append(readableBossRewardName(personal.get(i)));
            }
            online.sendMessage(ColorUtils.colorizeComponent(
                "&#00FF3B✔ &8Ξ &#4EFA76Twoja nagroda z bossa w &e/odbierz&a: " + names
            ));
        }
    }

    private void giveBossRewardsOffline(UUID uuid, List<ItemStack> personal) {
        giveBossRewardsToOdbierz(uuid, personal);
    }

    private void grantBossCoinReward() {
        int base = Math.max(0, settings.getInt("coin-reward-amount", 0));
        if (base <= 0) {
            return;
        }
        int ticketCost = party != null ? party.getTicketCost() : 0;
        int mult = DungeonSessionManager.moneyMultiplier(ticketCost);
        int amount = base * mult;
        if (amount <= 0) {
            return;
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            grantVaultMoney(player, amount);
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#00FF3B✔ &8Ξ &#4EFA76Nagroda Vault: &e" + amount + " &amonet &7(baza &e" + base
                    + " &7× &e" + mult + "&7)"
            ));
        }
    }

    private void grantVaultMoney(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        try {
            if (plugin.getEconomy() != null) {
                plugin.getEconomy().addBalance(player.getUniqueId(), amount);
                return;
            }
        } catch (Throwable ignored) {
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
            "eco give " + player.getName() + " " + amount);
    }

    /** Vault $ za podniesiony dungeon coin (trwale, poza dungeonem — ta sama ekonomia co /ulepsz). */
    public int grantCoinPickupVault(Player player, int dungeonCoinValue) {
        if (player == null || dungeonCoinValue <= 0) {
            return 0;
        }
        int per = Math.max(0, settings.getInt("coin-pickup-vault", 1));
        int amount = dungeonCoinValue * per;
        if (amount <= 0) {
            return 0;
        }
        grantVaultMoney(player, amount);
        return amount;
    }

    private void win() {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        stopMusicLoop();
        stopAmbientLoop();
        clearHudBars();
        clearDungeonScoreboards();
        // Od razu zdejmij kit — /lobby w oknie 5s nie moze wyniesc itemow dungeona.
        stripAllDungeonInventories();
        Bukkit.getScheduler().runTaskLater(plugin, coinSystem::clear, 60L);
        broadcastRaw(settings.getString("messages.win", "&#00FF3B✔ &8Ξ &#4EFA76Dungeon ukonczony! Gratulacje."));
        announceServer(settings.getString("messages.announce-win",
            "&#00FF3B✔ &8Ξ &#4EFA76Druzyna &f{PLAYERS} &#4EFA76ukonczyla &fDungeon Ninja&a!")
            .replace("{PLAYERS}", playerNames()));
        showMvpScreen(true);
        broadcastRaw("<gradient:#FFD900:#00FFC1>Itemy z bossow</gradient> &fsa w &e/odbierz&f.");
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setGameMode(GameMode.ADVENTURE);
                ColorUtils.sendTitle(player,
                    "<gradient:#00FFC1:#00FF5A><bold>WYGRANA</bold></gradient></shadow>",
                    "<gradient:#FFFFFF:#B5CFD4>Dungeon Ninja ukonczony!</gradient></shadow>",
                    10, 65, 15);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> manager.endSession(this, true), 100L);
    }

    private void grantWinRewards() {
        boolean premium = party != null && party.isPremium();
        List<String> commands = premium
            ? settings.getStringList("win-rewards.premium")
            : settings.getStringList("win-rewards.free");
        int coinPrizeBase = Math.max(0, settings.getInt("coin-prize-amount", 0));
        int ticketCost = party != null ? party.getTicketCost() : 0;
        int moneyMult = DungeonSessionManager.moneyMultiplier(ticketCost);
        int coinPrize = coinPrizeBase > 0 ? coinPrizeBase * moneyMult : 0;
        String coinPrizeCmd = settings.getString("coin-prize-command", "eco give {player} {amount}");
        if ((commands == null || commands.isEmpty()) && coinPrize <= 0) {
            return;
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            if (commands != null) {
                for (String raw : commands) {
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    String command = raw.replace("{player}", player.getName())
                        .replace("{PLAYER}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
            if (coinPrize > 0) {
                // Prefer HollyEconomy; fall back to configured console command.
                try {
                    if (plugin.getEconomy() != null) {
                        plugin.getEconomy().addBalance(player.getUniqueId(), coinPrize);
                    } else if (coinPrizeCmd != null && !coinPrizeCmd.isBlank()) {
                        String command = coinPrizeCmd
                            .replace("{player}", player.getName())
                            .replace("{PLAYER}", player.getName())
                            .replace("{amount}", String.valueOf(coinPrize))
                            .replace("{AMOUNT}", String.valueOf(coinPrize))
                            .replace("{tickets}", String.valueOf(ticketCost))
                            .replace("{TICKETS}", String.valueOf(ticketCost));
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    }
                } catch (Throwable ex) {
                    if (coinPrizeCmd != null && !coinPrizeCmd.isBlank()) {
                        String command = coinPrizeCmd
                            .replace("{player}", player.getName())
                            .replace("{PLAYER}", player.getName())
                            .replace("{amount}", String.valueOf(coinPrize))
                            .replace("{AMOUNT}", String.valueOf(coinPrize))
                            .replace("{tickets}", String.valueOf(ticketCost))
                            .replace("{TICKETS}", String.valueOf(ticketCost));
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    }
                }
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Nagroda: &e" + coinPrize + " &amonet &7(x" + moneyMult + ")"
                ));
            }
        }
    }

    private void lose() {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        stopMusicLoop();
        stopAmbientLoop();
        clearMobs();
        coinSystem.clear();
        clearHudBars();
        clearDungeonScoreboards();
        stripAllDungeonInventories();
        broadcastRaw(settings.getString("messages.lose", "&#FF0000✘ &8Ξ &cPrzegrana! Cala druzyna padla."));
        announceServer(settings.getString("messages.announce-lose",
            "&#FF0000✘ &8Ξ &cDruzyna &f{PLAYERS} &cprzegrala &fDungeon Ninja&c.")
            .replace("{PLAYERS}", playerNames()));
        showMvpScreen(false);
        broadcastRaw("<gradient:#FFD900:#00FFC1>Itemy z bossow</gradient> &fsa w &e/odbierz&f.");
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                ColorUtils.sendTitle(
                    player,
                    settings.getString("messages.lose-title", "&#FF0000&lPRZEGRANA"),
                    settings.getString("messages.lose-subtitle", "&fNikt nie przezyl dungeona ninja."),
                    10, 60, 15
                );
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> manager.endSession(this, false), 100L);
    }

    private void stripAllDungeonInventories() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                manager.stripDungeonInventory(player);
            }
        }
    }

    public void handlePlayerQuit(Player player) {
        handlePlayerQuit(player, !player.isOnline());
    }

    /**
     * @param realDisconnect true = PlayerQuit (DC), false = wyjscie ze swiata bez DC
     */
    public void handlePlayerQuit(Player player, boolean realDisconnect) {
        if (!players.contains(player.getUniqueId()) || phase == Phase.ENDED) {
            return;
        }
        // /kill / smierc nie otwiera 10-min freeze — to jest spectator albo wipe.
        if (deadSpectators.contains(player.getUniqueId()) || player.isDead()) {
            if (!hasAliveFighters()) {
                offerWipeReviveOrLose();
            }
            return;
        }
        // Tutorial: wyjscie = glos /skip (nie buguj kamery).
        if (phase == Phase.TUTORIAL) {
            skipVotes.add(player.getUniqueId());
        }
        // World-change (gracz nadal online): NIE zamrazaj sesji — wciagnij z powrotem.
        // Inaczej FROZEN_REJOIN + ensureAllPlayersPresent = wieczny Freeze na scoreboardzie.
        if (!realDisconnect) {
            Location back = logoutLocations.getOrDefault(player.getUniqueId(), getRespawnLocation());
            if (back == null || back.getWorld() == null || !back.getWorld().equals(world)) {
                back = getRespawnLocation();
            }
            Location pull = back.clone();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || phase == Phase.ENDED || !players.contains(player.getUniqueId())) {
                    return;
                }
                if (!player.getWorld().equals(world)) {
                    player.teleport(pull);
                }
                if (deadSpectators.contains(player.getUniqueId())) {
                    player.setGameMode(GameMode.SPECTATOR);
                } else if (phase != Phase.TUTORIAL && phase != Phase.BOSS_CINEMATIC) {
                    forceFighterState(player);
                }
                player.sendMessage(ColorUtils.colorizeComponent(
                    "<gradient:#FFD900:#FF9B00>Nie mozesz opuszczac</gradient> &fdungeona w trakcie runu!</shadow>"
                ));
            });
            return;
        }
        manager.markRejoin(player.getUniqueId(), this);
        try {
            logoutLocations.put(player.getUniqueId(), player.getLocation().clone());
        } catch (Throwable ignored) {
            logoutLocations.put(player.getUniqueId(), getRespawnLocation());
        }
        // Solo / nikt online: zamroz instancje i daj czas na auto-rejoin.
        if (!hasOnlineParticipantsExcept(player.getUniqueId())) {
            if (phase != Phase.FROZEN_REJOIN) {
                phaseBeforeFreeze = phase;
            }
            // Zatrzymaj tutorial zanim zamrozimy — inaczej kamera/slide leci w tle i psuje sesje.
            if (phase == Phase.TUTORIAL || phaseBeforeFreeze == Phase.TUTORIAL) {
                if (tutorialSlideTask != null) {
                    tutorialSlideTask.cancel();
                    tutorialSlideTask = null;
                }
                tutorialCameraLock = null;
                clearMobs();
            }
            phase = Phase.FROZEN_REJOIN;
            movementLocked = true;
            if (phaseBeforeFreeze != Phase.TUTORIAL) {
                clearMobs();
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (phase == Phase.FROZEN_REJOIN && !hasOnlineParticipants()) {
                    announceServer(settings.getString("messages.announce-timeout",
                        "&#FF0000✘ &8Ξ &cDruzyna &f{PLAYERS} &cnie wrocila do dungeona na czas.")
                        .replace("{PLAYERS}", playerNames()));
                    for (UUID uuid : players) {
                        Player offlineTarget = Bukkit.getPlayer(uuid);
                        if (offlineTarget != null) {
                            offlineTarget.sendMessage(ColorUtils.colorizeComponent(
                                settings.getString("messages.quit-lost", "&#FF0000✘ &8Ξ &cNie wrociles na czas — tickety przepadly.")
                            ));
                        }
                    }
                    manager.endSession(this, false);
                }
            }, settings.getInt("rejoin-seconds", 600) * 20L);
        }
    }

    public boolean tryRejoin(Player player) {
        if (phase == Phase.ENDED || !players.contains(player.getUniqueId())) {
            return false;
        }
        boolean wasFrozen = phase == Phase.FROZEN_REJOIN;
        Location spawn = logoutLocations.get(player.getUniqueId());
        if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(world)) {
            spawn = getRespawnLocation();
        }
        player.teleport(spawn);
        player.getInventory().clear();
        player.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.rejoin",
            "&#00FF3B✔ &8Ξ &#4EFA76Auto-rejoin: przywrocono Cie do dungeona!")));

        if (deadSpectators.contains(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            if (phase != Phase.TUTORIAL && !(wasFrozen && phaseBeforeFreeze == Phase.TUTORIAL)) {
                giveKits(player);
                applyBonusMaxHp(player);
                reapplyBuffUpgrades(player);
            }
        }

        if (wasFrozen) {
            resumeAfterFrozenRejoin(player);
        }
        return true;
    }

    /** Wznawia gre po FROZEN_REJOIN (rejoin DC albo auto-fix gdy gracz online). */
    private void resumeAfterFrozenRejoin(Player player) {
        if (phase != Phase.FROZEN_REJOIN) {
            return;
        }
        Phase resume = phaseBeforeFreeze == Phase.FROZEN_REJOIN || phaseBeforeFreeze == Phase.ENDED
            ? Phase.WAVE
            : phaseBeforeFreeze;
        // Wyjscie w tutorialu = skip → po powrocie od razu prepare (nie restartuj zbugowanej kamery).
        if (resume == Phase.TUTORIAL) {
            skipVotes.add(player.getUniqueId());
            startMusicLoop();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (phase == Phase.FROZEN_REJOIN || phase == Phase.TUTORIAL) {
                    beginPrepare();
                }
            }, 15L);
            return;
        }
        if (resume == Phase.BOSS || resume == Phase.BOSS_CINEMATIC) {
            phase = Phase.PREPARE;
            movementLocked = true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (phase == Phase.PREPARE) {
                    movementLocked = false;
                    startBossFight(Math.max(1, level));
                }
            }, 40L);
        } else {
            phase = Phase.PREPARE;
            movementLocked = true;
            for (UUID uuid : players) {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline()) {
                    continue;
                }
                if (!online.getWorld().equals(world)) {
                    online.teleport(getRespawnLocation());
                }
                if (deadSpectators.contains(uuid)) {
                    online.setGameMode(GameMode.SPECTATOR);
                } else {
                    forceFighterState(online);
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (phase == Phase.PREPARE) {
                    movementLocked = false;
                    startWave(Math.max(1, level), Math.max(1, wave));
                }
            }, 40L);
        }
        startMusicLoop();
        broadcastRaw("<gradient:#00FFC1:#00FF5A>Sesja odmrozona</gradient> &8— &fkontynuujemy run!</shadow>");
    }

    private boolean hasOnlineParticipantsExcept(UUID excluded) {
        for (UUID uuid : players) {
            if (uuid.equals(excluded)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOnlineParticipants() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return true;
            }
        }
        return false;
    }

    private void startTutorialSlides() {
        if (tutorialSlideTask != null) {
            tutorialSlideTask.cancel();
        }
        tutorialSlideIndex = 0;
        showTutorialSlide(0);
        // ~40s tutorial, title co ~4s (2x wolniej)
        long period = Math.max(60L, settings.getInt("tutorial-slide-ticks", 80));
        tutorialSlideTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (phase != Phase.TUTORIAL) {
                return;
            }
            tutorialSlideIndex++;
            showTutorialSlide(tutorialSlideIndex);
        }, period, period);
    }

    private void showTutorialSlide(int index) {
        List<Map<?, ?>> slides = settings.getMapList("tutorial.slides");
        String title;
        String subtitle;
        String[][] fallback = {
            {"<gradient:#00C1FF:#2BD4FF><bold>TUTORIAL</bold></gradient></shadow>", "<gradient:#FFFFFF:#B5CFD4>Dungeon Ninja</gradient> &8— &fprzygotuj sie</shadow>"},
            {"<gradient:#00FFC1:#00FF5A><bold>CO TO JEST?</bold></gradient></shadow>", "<gradient:#FFD900:#FF9B00>3 poziomy</gradient>&f, fale i bossy</shadow>"},
            {"<gradient:#FFD900:#FF9B00><bold>FALE</bold></gradient></shadow>", "Pokonuj po kolei — sciezka zablokowana</shadow>"},
            {"<gradient:#2BD4FF:#00C1FF><bold>JAK GRAC?</bold></gradient></shadow>", "Bij ninje, idz dalej po czystej fali</shadow>"},
            {"<gradient:#FF3B3B:#FF5C5C><bold>BOSSY</bold></gradient></shadow>", "Kamera GM3, potem walka</shadow>"},
            {"<gradient:#00FF5A:#9FFF00><bold>ULEPSZENIA</bold></gradient></shadow>", "Co 2 fale wybierasz upgrade</shadow>"},
            {"<gradient:#9FFF00:#2AFF5B><bold>UPGRADE</bold></gradient></shadow>", "Protection / Sharpness / Gapple</shadow>"},
            {"<gradient:#FF5C5C:#FF9B00><bold>SMIERC</bold></gradient></shadow>", "Sneak 15s przy ciele = revive</shadow>"},
            {"<gradient:#00C1FF:#00D6FF><bold>DASH</bold></gradient></shadow>", "Shift + PPM katana</shadow>"},
            {"<gradient:#FFB800:#FF5C5C><bold>WYSOKOSC</bold></gradient></shadow>", "8+ kratek = insta kill</shadow>"},
            {"<gradient:#00FFC1:#2BD4FF><bold>WODA</bold></gradient></shadow>", "Trujaca woda — nie wchodz</shadow>"},
            {"<gradient:#FFD900:#FFFFFF><bold>/SKIP</bold></gradient></shadow>", "50% lobby: wpisz /skip</shadow>"}
        };
        if (slides == null || slides.isEmpty()) {
            String[] slide = fallback[Math.floorMod(index, fallback.length)];
            title = slide[0];
            subtitle = slide[1];
        } else {
            Map<?, ?> slide = slides.get(Math.floorMod(index, slides.size()));
            Object t = slide.get("title");
            Object s = slide.get("subtitle");
            title = t != null ? String.valueOf(t) : fallback[Math.floorMod(index, fallback.length)][0];
            subtitle = s != null ? String.valueOf(s) : fallback[Math.floorMod(index, fallback.length)][1];
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendDungeonTitle(player, title, subtitle, 8, 70, 10);
            }
        }
    }

    private void sendDungeonTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(net.kyori.adventure.title.Title.title(
            ColorUtils.colorizeComponent(title == null ? "" : title),
            ColorUtils.colorizeComponent(subtitle == null ? "" : subtitle),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(Math.max(0, fadeIn) * 50L),
                java.time.Duration.ofMillis(Math.max(1, stay) * 50L),
                java.time.Duration.ofMillis(Math.max(0, fadeOut) * 50L)
            )
        ));
    }

    private void startMusicLoop() {
        stopMusicLoop();
        musicFading = false;
        playMusicAtVolume(1.0F);
        int loopSeconds = Math.max(1, settings.getInt("music-loop-seconds", 50));
        musicTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!musicFading) {
                playMusicAtVolume(1.0F);
            }
        }, loopSeconds * 20L, loopSeconds * 20L);
    }

    private void playMusicOnce() {
        playMusicAtVolume(1.0F);
    }

    private void playMusicAtVolume(float volumeMul) {
        String sound = settings.getString("music-sound", "minecraft:eventmusic");
        float volume = (float) settings.getDouble("music-volume", 1.3D) * Math.max(0.05F, volumeMul);
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.stopSound(sound, SoundCategory.RECORDS);
            player.playSound(player.getLocation(), sound, SoundCategory.RECORDS, volume, 1.0F);
        }
    }

    private void dipMusicOnDeath() {
        if (phase == Phase.ENDED) {
            return;
        }
        musicFading = true;
        String main = settings.getString("music-sound", "minecraft:eventmusic");
        String fadeOut = settings.getString("music-fadeout-sound", "minecraft:eventmusic_fadeout");
        String fadeIn = settings.getString("music-fadein-sound", "minecraft:eventmusic_fadein");
        float volume = (float) settings.getDouble("music-volume", 1.3D);
        int fadeOutTicks = Math.max(60, settings.getInt("music-fadeout-ticks", 160)); // ~8s
        int quietTicks = Math.max(20, settings.getInt("music-quiet-ticks", 40)); // ~2s ciszy
        int fadeInTicks = Math.max(60, settings.getInt("music-fadein-ticks", 160)); // ~8s

        // Natychmiast: stop main + odpal clip ktory SAM sie zcisza (prawdziwy fade-out w ogg).
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.stopSound(main, SoundCategory.RECORDS);
            player.stopSound(fadeOut, SoundCategory.RECORDS);
            player.stopSound(fadeIn, SoundCategory.RECORDS);
            player.playSound(player.getLocation(), fadeOut, SoundCategory.RECORDS, volume, 1.0F);
        }

        // Po fade-out: krotka cisza, potem fade-in clip, potem znowu petla.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase == Phase.ENDED) {
                musicFading = false;
                return;
            }
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.stopSound(fadeOut, SoundCategory.RECORDS);
                }
            }
        }, fadeOutTicks);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase == Phase.ENDED) {
                musicFading = false;
                return;
            }
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                player.playSound(player.getLocation(), fadeIn, SoundCategory.RECORDS, volume, 1.0F);
            }
        }, fadeOutTicks + quietTicks);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase == Phase.ENDED) {
                musicFading = false;
                return;
            }
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.stopSound(fadeIn, SoundCategory.RECORDS);
                }
            }
            playMusicAtVolume(1.0F);
            musicFading = false;
        }, fadeOutTicks + quietTicks + fadeInTicks);
    }

    private void stopMusicLoop() {
        musicFading = false;
        if (musicTask != null) {
            musicTask.cancel();
            musicTask = null;
        }
        String main = settings.getString("music-sound", "minecraft:eventmusic");
        String fadeOut = settings.getString("music-fadeout-sound", "minecraft:eventmusic_fadeout");
        String fadeIn = settings.getString("music-fadein-sound", "minecraft:eventmusic_fadein");
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.stopSound(main, SoundCategory.RECORDS);
                player.stopSound(fadeOut, SoundCategory.RECORDS);
                player.stopSound(fadeIn, SoundCategory.RECORDS);
            }
        }
    }

    private void startAmbientLoop() {
        stopAmbientLoop();
        playAmbientOnce();
        int seconds = Math.max(20, settings.getInt("ambient-loop-seconds", 40));
        ambientTask = Bukkit.getScheduler().runTaskTimer(plugin, this::playAmbientOnce, seconds * 20L, seconds * 20L);
    }

    private void playAmbientOnce() {
        if (phase == Phase.ENDED || phase == Phase.TUTORIAL) {
            return;
        }
        String key = settings.getString("ambient.level-" + Math.max(1, Math.min(3, level)),
            level <= 1 ? "minecraft:event.dungeon_ambient_1"
                : level == 2 ? "minecraft:event.dungeon_ambient_2"
                : "minecraft:event.dungeon_ambient_3");
        float volume = (float) settings.getDouble("ambient-volume", 0.7D);
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.stopSound(key, SoundCategory.AMBIENT);
            player.playSound(player.getLocation(), key, SoundCategory.AMBIENT, volume, 1.0F);
            // Dopelnienie vanilla mood per poziom.
            if (level == 2) {
                player.playSound(player.getLocation(), Sound.AMBIENT_NETHER_WASTES_MOOD, SoundCategory.AMBIENT, 0.35F, 1.0F);
            } else if (level >= 3) {
                player.playSound(player.getLocation(), Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, SoundCategory.AMBIENT, 0.4F, 0.85F);
            }
        }
    }

    private void stopAmbientLoop() {
        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
        for (String key : List.of(
            "minecraft:event.dungeon_ambient_1",
            "minecraft:event.dungeon_ambient_2",
            "minecraft:event.dungeon_ambient_3"
        )) {
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    player.stopSound(key, SoundCategory.AMBIENT);
                }
            }
        }
    }

    public void recordDamage(UUID attacker, double amount) {
        if (attacker == null || amount <= 0.0D || !players.contains(attacker)) {
            return;
        }
        damageDealt.merge(attacker, amount, Double::sum);
    }

    public boolean tryDash(Player player) {
        if (phase != Phase.WAVE && phase != Phase.BOSS) {
            return false;
        }
        if (deadSpectators.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (isMovementLocked()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long readyAt = dashCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) {
            long left = (readyAt - now + 999L) / 1000L;
            MultiActionBarService.get().send(player, "dungeon-dash",
                "<gradient:#FF5C5C:#FF9B00>Dash niedostepny</gradient> &8— &fodnowa za <gradient:#FFD900:#FF9B00>"
                    + left + "s</gradient></shadow>", 25L);
            return false;
        }
        int cd = Math.max(2, settings.getInt("dash-cooldown-seconds", 5)
            - dashCdReduce.getOrDefault(player.getUniqueId(), 0));
        dashCooldownUntil.put(player.getUniqueId(), now + cd * 1000L);
        Vector dir = player.getLocation().getDirection().normalize().multiply(1.55D);
        dir.setY(Math.max(0.12D, Math.min(0.42D, dir.getY() + 0.18D)));
        player.setVelocity(dir);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.4D, 0), 18, 0.35, 0.2, 0.35, 0.02);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1.0D, 0), 2);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.55F, 1.55F);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7F, 1.2F);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 15, 0, false, false, false));
        return true;
    }

    private void spawnDeathMarker(Player dead, Location loc) {
        removeDeathMarker(dead.getUniqueId());
        Location at = loc.clone();
        at.setYaw(dead.getLocation().getYaw());
        deathLocations.put(dead.getUniqueId(), at.clone());
        ArmorStand stand = world.spawn(at, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setCollidable(false);
            as.setCustomNameVisible(true);
            as.customName(ColorUtils.colorizeComponent("&#FF5C5C☠ &f" + dead.getName() + " &7(sneak)")
                .decoration(TextDecoration.ITALIC, false));
            as.addScoreboardTag("holly_dungeon_corpse");
            as.addScoreboardTag("holly_corpse_" + dead.getUniqueId());
            try {
                for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
                    as.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                    as.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
                }
            } catch (Throwable ignored) {
            }
        });
        ItemStack head = createPlayerHead(dead.getUniqueId(), dead.getName());
        if (stand.getEquipment() != null) {
            stand.getEquipment().setHelmet(head);
            // SparklyPaper/Paper 1.21+: ArmorStand nie jest Mob — set*DropChance rzuca IAE.
            // Blokady equipmentu powyżej wystarczą, żeby hełm nie spadł.
            try {
                if (stand instanceof org.bukkit.entity.Mob) {
                    stand.getEquipment().setHelmetDropChance(0.0F);
                    stand.getEquipment().setChestplateDropChance(0.0F);
                    stand.getEquipment().setLeggingsDropChance(0.0F);
                    stand.getEquipment().setBootsDropChance(0.0F);
                    stand.getEquipment().setItemInMainHandDropChance(0.0F);
                    stand.getEquipment().setItemInOffHandDropChance(0.0F);
                }
            } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            }
        }
        deathMarkers.put(dead.getUniqueId(), stand);
    }

    private void removeDeathMarker(UUID uuid) {
        ArmorStand stand = deathMarkers.remove(uuid);
        Location loc = deathLocations.remove(uuid);
        if (stand != null) {
            try {
                if (stand.getEquipment() != null) {
                    stand.getEquipment().clear();
                }
                stand.setCustomNameVisible(false);
                stand.customName(null);
                stand.remove();
            } catch (Throwable ignored) {
            }
        }
        // Cleanup orfanow (chunk / duplikaty) — glowa nie moze zostac.
        Location scan = loc;
        if (scan == null && stand != null) {
            try {
                scan = stand.getLocation();
            } catch (Throwable ignored) {
            }
        }
        if (scan != null && scan.getWorld() != null) {
            String tag = "holly_corpse_" + uuid;
            for (org.bukkit.entity.Entity entity : scan.getWorld().getNearbyEntities(scan, 4.0D, 4.0D, 4.0D)) {
                if (!(entity instanceof ArmorStand as)) {
                    continue;
                }
                if (as.getScoreboardTags().contains(tag)) {
                    try {
                        if (as.getEquipment() != null) {
                            as.getEquipment().clear();
                        }
                        as.remove();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        // Fallback: szukaj po tagu w calym swiecie sesji.
        String tag = "holly_corpse_" + uuid;
        for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
            if (as.getScoreboardTags().contains(tag)) {
                try {
                    if (as.getEquipment() != null) {
                        as.getEquipment().clear();
                    }
                    as.remove();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void clearDeathMarkers() {
        for (UUID uuid : new HashSet<>(deathMarkers.keySet())) {
            removeDeathMarker(uuid);
        }
        reviveProgress.clear();
        reviveTargets.clear();
        reviveProgressByTarget.clear();
    }

    private void tickRevive() {
        int need = Math.max(30, settings.getInt("revive-seconds", 12) * 20);
        double radius = Math.max(3.0D, settings.getDouble("revive-radius", 5.0D));
        Map<UUID, List<Player>> helpersByTarget = new HashMap<>();
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player reviver = Bukkit.getPlayer(uuid);
            if (reviver == null || reviver.getGameMode() == GameMode.SPECTATOR || reviver.isDead()) {
                continue;
            }
            if (!reviver.isSneaking()) {
                continue;
            }
            UUID targetId = nearestCorpse(reviver.getLocation(), radius);
            if (targetId == null) {
                continue;
            }
            if (revivedThisWave.contains(targetId)) {
                MultiActionBarService.get().send(reviver, "dungeon-revive",
                    "<gradient:#FF5C5C:#FF9B00>Juz ozywiony na tej fali</gradient> &7— przejdz fale</shadow>", 20L);
                continue;
            }
            helpersByTarget.computeIfAbsent(targetId, k -> new ArrayList<>()).add(reviver);
        }

        Set<UUID> activeTargets = helpersByTarget.keySet();
        reviveProgressByTarget.keySet().removeIf(id -> !activeTargets.contains(id));

        for (Map.Entry<UUID, List<Player>> entry : helpersByTarget.entrySet()) {
            UUID targetId = entry.getKey();
            List<Player> helpers = entry.getValue();
            int team = Math.max(1, helpers.size());
            int progress = reviveProgressByTarget.getOrDefault(targetId, 0) + team;
            reviveProgressByTarget.put(targetId, progress);
            int pct = Math.min(100, (progress * 100) / need);
            String bar = reviveBar(pct);
            for (Player helper : helpers) {
                MultiActionBarService.get().send(helper, "dungeon-revive",
                    "<gradient:#00FF5A:#9FFF00>Ozywianie</gradient> &8×&f" + team
                        + " &8— <gradient:#FFD900:#FF9B00>" + pct + "%</gradient> "
                        + bar + "</shadow>", 15L);
            }
            Player dying = Bukkit.getPlayer(targetId);
            if (dying != null && dying.isOnline()) {
                ColorUtils.sendTitle(dying,
                    "<gradient:#00FFC1:#00FF5A><bold>ODRADZANIE</bold></gradient></shadow>",
                    "&f" + bar + " &8| &f" + pct + "% &8| &fx" + team + " osob",
                    0, 25, 5);
                MultiActionBarService.get().send(dying, "dungeon-revive-self",
                    "<gradient:#00FFC1:#00FF5A>Ktos Cie odradza</gradient> &8— &f"
                        + pct + "% &8(×" + team + ")</shadow>", 15L);
            }
            Location corpse = deathLocations.get(targetId);
            if (corpse != null) {
                world.spawnParticle(Particle.HEART, corpse.clone().add(0, 1.0D, 0),
                    Math.min(8, 1 + team), 0.3, 0.3, 0.3, 0);
            }
            if (progress >= need) {
                reviveProgressByTarget.remove(targetId);
                Player primary = helpers.get(0);
                performRevive(primary, targetId);
            }
        }
    }

    private String reviveBar(int pct) {
        int filled = Math.max(0, Math.min(10, pct / 10));
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < 10; i++) {
            if (i == filled) {
                sb.append("&7");
            }
            sb.append("|");
        }
        return sb.toString();
    }

    private UUID nearestCorpse(Location from, double radius) {
        UUID best = null;
        double bestDist = radius * radius;
        for (UUID deadId : deadSpectators) {
            Location corpse = deathLocations.get(deadId);
            if (corpse == null) {
                ArmorStand stand = deathMarkers.get(deadId);
                if (stand == null || stand.isDead()) {
                    continue;
                }
                corpse = stand.getLocation();
            }
            if (corpse.getWorld() == null || from.getWorld() == null || !corpse.getWorld().equals(from.getWorld())) {
                continue;
            }
            double dx = corpse.getX() - from.getX();
            double dz = corpse.getZ() - from.getZ();
            double dy = corpse.getY() - from.getY();
            double dist = dx * dx + dz * dz + Math.min(dy * dy, 4.0D);
            if (dist <= bestDist) {
                bestDist = dist;
                best = deadId;
            }
        }
        return best;
    }

    private void performRevive(Player reviver, UUID targetId) {
        performRevive(reviver, targetId, true);
    }

    private void performRevive(Player reviver, UUID targetId, boolean countWaveLimit) {
        Player target = Bukkit.getPlayer(targetId);
        Location at = deathLocations.get(targetId);
        if (at == null) {
            ArmorStand marker = deathMarkers.get(targetId);
            at = marker != null ? marker.getLocation().clone() : getRespawnLocation();
        } else {
            at = at.clone();
        }
        removeDeathMarker(targetId);
        deadSpectators.remove(targetId);
        if (countWaveLimit) {
            revivedThisWave.add(targetId);
        }
        if (reviver != null) {
            reviveCounts.merge(reviver.getUniqueId(), 1, Integer::sum);
        }
        if (target != null && target.isOnline()) {
            target.teleport(at);
            forceFighterState(target);
            target.setHealth(Math.min(8.0D, target.getMaxHealth()));
            target.setFoodLevel(20);
            restoreInventory(target);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target.isOnline() && !deadSpectators.contains(targetId)) {
                    restoreInventory(target);
                    forceFighterState(target);
                }
            });
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (target.isOnline() && !deadSpectators.contains(targetId)) {
                    restoreInventory(target);
                }
            }, 3L);
            target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60, 0));
            applyBonusMaxHp(target);
            reapplyBuffUpgrades(target);
            sendDungeonTitle(target,
                "<gradient:#00FF5A:#9FFF00><bold>OZYWIONY</bold></gradient></shadow>",
                reviver != null
                    ? "Dzieki <gradient:#00C1FF:#00D6FF>" + reviver.getName() + "</gradient></shadow>"
                    : "<gradient:#00FFC1:#00FF5A>Odrodzenie druzynowe</gradient></shadow>",
                8, 45, 10);
            target.sendMessage(ColorUtils.colorizeComponent(
                "&#00FF3B✔ &8Ξ &#4EFA76Zostales ozywiony! Ekwipunek przywrocony."
            ));
        }
        if (reviver != null) {
            reviver.sendMessage(ColorUtils.colorizeComponent(
                "&#00FF3B✔ &8Ξ &#4EFA76Ozywiles gracza!"
            ));
            broadcastRaw("&#4EFA76♥ &8Ξ &f" + reviver.getName() + " &#4EFA76ozywil &f"
                + (target != null ? target.getName() : "gracza") + "&a!");
        }
        world.playSound(at, Sound.ITEM_TOTEM_USE, 0.7F, 1.25F);
    }

    private void openWave12Reward() {
        if (wave12RewardClaimed || wave12RewardOpen) {
            return;
        }
        wave12RewardOpen = true;
        wave12Votes.clear();
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            sendDungeonTitle(player,
                "<gradient:#FFD900:#C800FF><bold>GLOSOWANIE FALI 12</bold></gradient></shadow>",
                "Totem &8vs &fOdrodzenie — glosuja zywi</shadow>",
                10, 50, 10);
            new DungeonWave12GUI(plugin, this, player).open();
        }
        if (countLivingVoters() <= 0) {
            wave12RewardOpen = false;
            wave12RewardClaimed = true;
            spawnCurrentWaveMobs();
        }
    }

    private int countLivingVoters() {
        int n = 0;
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getGameMode() != GameMode.SPECTATOR) {
                n++;
            }
        }
        return n;
    }

    public int wave12TotemVotes() {
        int n = 0;
        for (Boolean v : wave12Votes.values()) {
            if (Boolean.TRUE.equals(v)) {
                n++;
            }
        }
        return n;
    }

    public int wave12ReviveVotes() {
        int n = 0;
        for (Boolean v : wave12Votes.values()) {
            if (Boolean.FALSE.equals(v)) {
                n++;
            }
        }
        return n;
    }

    public Boolean getWave12Vote(UUID uuid) {
        return wave12Votes.get(uuid);
    }

    public boolean isWave12Voting() {
        return wave12RewardOpen && !wave12RewardClaimed;
    }

    /** true = totem, false = mass revive */
    public void voteWave12(Player player, boolean totem) {
        if (wave12RewardClaimed || !wave12RewardOpen || phase == Phase.ENDED) {
            return;
        }
        if (deadSpectators.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        wave12Votes.put(player.getUniqueId(), totem);
        player.sendMessage(ColorUtils.colorizeComponent(totem
            ? "&#00FF3B✔ &8Ξ &#4EFA76Zaglosowano na &fTOTEM"
            : "&#00FF3B✔ &8Ξ &#4EFA76Zaglosowano na &fODRODZENIE WSZYSTKICH"
        ));
        refreshWave12Guis();
        int need = countLivingVoters();
        if (need > 0 && wave12Votes.size() >= need) {
            resolveWave12Vote();
        } else {
            broadcastRaw("Glosy: <gradient:#FFD900:#FF9B00>Totem " + wave12TotemVotes()
                + "</gradient> &8| <gradient:#00C1FF:#00FFC1>Revive " + wave12ReviveVotes()
                + "</gradient> &8/&f" + need);
        }
    }

    private void refreshWave12Guis() {
        for (UUID uuid : players) {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                continue;
            }
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof DungeonWave12GUI gui) {
                gui.render();
            }
        }
    }

    private void resolveWave12Vote() {
        if (wave12RewardClaimed) {
            return;
        }
        int totem = wave12TotemVotes();
        int revive = wave12ReviveVotes();
        boolean pickTotem;
        if (totem > revive) {
            pickTotem = true;
        } else if (revive > totem) {
            pickTotem = false;
        } else {
            // Remis: jesli ktos martwy — revive, inaczej totem.
            pickTotem = deadSpectators.isEmpty();
        }
        if (pickTotem) {
            applyWave12Totem();
        } else {
            applyWave12MassRevive();
        }
    }

    private void applyWave12Totem() {
        wave12RewardClaimed = true;
        wave12RewardOpen = false;
        for (UUID uuid : players) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.closeInventory();
                if (!deadSpectators.contains(uuid) && online.getGameMode() != GameMode.SPECTATOR) {
                    online.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
                    online.sendMessage(ColorUtils.colorizeComponent(
                        "&#00FF3B✔ &8Ξ &#4EFA76Otrzymano &fTotem Nieumierania&a!"
                    ));
                }
            }
        }
        broadcastRaw("<gradient:#FFD900:#FF9B00>Fala 12:</gradient> &fwygrywa <gradient:#00FF5A:#9FFF00>TOTEM</gradient> &8("
            + wave12TotemVotes() + ":" + wave12ReviveVotes() + ")");
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnCurrentWaveMobs, 15L);
    }

    private void applyWave12MassRevive() {
        wave12RewardClaimed = true;
        wave12RewardOpen = false;
        for (UUID uuid : players) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.closeInventory();
            }
        }
        Location rally = locations.getWave(2, 5, world);
        if (rally == null) {
            rally = getRespawnLocation();
        }
        List<UUID> toRevive = new ArrayList<>(deadSpectators);
        for (UUID deadId : toRevive) {
            performRevive(null, deadId, false);
            Player revived = Bukkit.getPlayer(deadId);
            if (revived != null && revived.isOnline() && rally != null) {
                revived.teleport(rally.clone().add(0, 0.5D, 0));
            }
        }
        broadcastRaw("<gradient:#FFD900:#FF9B00>Fala 12:</gradient> &fwygrywa <gradient:#00C1FF:#00FFC1>ODRODZENIE</gradient> &8("
            + wave12ReviveVotes() + ":" + wave12TotemVotes() + ")");
        broadcastRaw("Ozywieni TP na <gradient:#00C1FF:#00FFC1>fale 5 poziomu 2</gradient>");
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnCurrentWaveMobs, 15L);
    }

    public void claimWave12Totem(Player player) {
        voteWave12(player, true);
    }

    public void claimWave12MassRevive(Player player) {
        voteWave12(player, false);
    }

    public void reopenWave12IfNeeded(Player player) {
        if (!isWave12Voting() || player == null || !player.isOnline()) {
            return;
        }
        if (deadSpectators.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isWave12Voting() || !player.isOnline()) {
                return;
            }
            if (deadSpectators.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof DungeonWave12GUI) {
                return;
            }
            new DungeonWave12GUI(plugin, this, player).open();
        });
    }

    private void checkBossPhase2() {
        if (bossPhase2 || bossId == null) {
            return;
        }
        org.bukkit.entity.Entity entity = Bukkit.getEntity(bossId);
        if (!(entity instanceof LivingEntity boss) || boss.isDead()) {
            return;
        }
        double max = boss.getMaxHealth();
        if (max <= 0.0D || boss.getHealth() / max > 0.5D) {
            return;
        }
        bossPhase2 = true;
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sendDungeonTitle(player,
                    "<gradient:#C800FF:#FF3B3B><bold>FAZA 2</bold></gradient></shadow>",
                    "Boss wscieka sie — <gradient:#FF5C5C:#FF9B00>uwazaj!</gradient></shadow>",
                    8, 50, 10);
            }
        }
        broadcastRaw("<gradient:#C800FF:#FF3B3B>Boss wszedl w faze 2!</gradient> &7Uwazajcie.</shadow>");
        boss.getWorld().strikeLightningEffect(boss.getLocation());
        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0, 1, 0), 40, 0.8, 0.8, 0.8, 0.02);
        AttributeInstance speed = boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 1.18D);
        }
        AttributeInstance dmg = boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() + (level >= 3 ? 2.0D : 1.0D));
        }
        Location base = boss.getLocation();
        for (int i = 0; i < 2 + level; i++) {
            LivingEntity add = spawnWaveMob(findSafeSpawn(spread(base, i, 4)), false);
            if (add instanceof Mob mob) {
                setFocused(mob, true);
            }
        }
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(boss.getLocation()) < 100.0D) {
                Vector knock = player.getLocation().toVector().subtract(boss.getLocation().toVector());
                if (knock.lengthSquared() > 0.01D) {
                    knock = knock.normalize().multiply(0.55D);
                    knock.setY(0.28D);
                    player.setVelocity(knock);
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0));
            }
        }
        updateHudBars();
    }

    private void updateHudBars() {
        if (phase == Phase.ENDED || phase == Phase.TUTORIAL) {
            clearHudBars();
            return;
        }
        String title;
        float progress;
        BossBar.Color color;
        BossBar.Overlay overlay = BossBar.Overlay.NOTCHED_10;
        if (phase == Phase.BOSS || phase == Phase.BOSS_CINEMATIC) {
            LivingEntity boss = null;
            if (bossId != null && Bukkit.getEntity(bossId) instanceof LivingEntity living) {
                boss = living;
            }
            String bossName = strip(settings.getString("bosses." + level + ".name", "Boss"));
            if (boss != null && !boss.isDead()) {
                int hp = (int) Math.ceil(boss.getHealth());
                int max = (int) Math.ceil(boss.getMaxHealth());
                title = (bossPhase2
                    ? "<gradient:#C800FF:#FC73F7>Faza 2</gradient> &8| "
                    : "<gradient:#FF3B3B:#FF5C5C>Boss</gradient> &8| ")
                    + bossName + " &8| <gradient:#FF5C5C:#FF9B00>" + hp + "</gradient>&8/&f" + max + "</shadow>";
                progress = (float) Math.max(0.0D, Math.min(1.0D, boss.getHealth() / Math.max(1.0D, boss.getMaxHealth())));
                color = bossPhase2 ? BossBar.Color.PURPLE : BossBar.Color.RED;
            } else {
                title = "<gradient:#FF3B3B:#FF5C5C>Boss</gradient> &8| " + bossName + "</shadow>";
                progress = 1.0F;
                color = BossBar.Color.RED;
            }
        } else if (phase == Phase.WAVE) {
            int killed = Math.max(0, waveTotal - waveLeft);
            title = "Poziom <gradient:#FFD900:#FF9B00>" + level
                + "</gradient> &8| &fFala <gradient:#00C1FF:#00D6FF>" + wave
                + "</gradient> &8| <gradient:#00FF5A:#9FFF00>" + killed + "</gradient>&8/&f" + waveTotal + "</shadow>";
            progress = waveTotal <= 0 ? 0.0F : (float) killed / (float) waveTotal;
            color = BossBar.Color.BLUE;
        } else if (phase == Phase.UPGRADE) {
            title = "<gradient:#FFD900:#FF9B00>Wybierz ulepszenie</gradient> &7w GUI</shadow>";
            progress = 1.0F;
            color = BossBar.Color.YELLOW;
        } else if (phase == Phase.COIN_SHOP) {
            title = "<gradient:#FFD900:#FFFFFF>Shop coinow</gradient> &7(opcjonalny)</shadow>";
            progress = 1.0F;
            color = BossBar.Color.YELLOW;
        } else if (phase == Phase.PREPARE) {
            title = "<gradient:#00FFC1:#00FF5A>Przygotowanie</gradient> &8— &fzaraz start</shadow>";
            progress = 1.0F;
            color = BossBar.Color.GREEN;
        } else {
            title = "<gradient:#00C1FF:#C800FF>Dungeon Ninja</gradient></shadow>";
            progress = 1.0F;
            color = BossBar.Color.WHITE;
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                BossBar stale = hudBars.remove(uuid);
                if (stale != null) {
                    // no player to hide
                }
                continue;
            }
            BossBar bar = hudBars.computeIfAbsent(uuid, id -> {
                BossBar created = BossBar.bossBar(
                    ColorUtils.colorizeComponent(title),
                    progress,
                    color,
                    overlay
                );
                player.showBossBar(created);
                return created;
            });
            bar.name(ColorUtils.colorizeComponent(title));
            bar.progress(Math.max(0.0F, Math.min(1.0F, progress)));
            bar.color(color);
            player.showBossBar(bar);
        }
        hudBars.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            return player == null || !player.isOnline() || !players.contains(entry.getKey());
        });
    }

    private void clearHudBars() {
        for (Map.Entry<UUID, BossBar> entry : hudBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        hudBars.clear();
    }

    private void updateDungeonScoreboard() {
        if (phase == Phase.ENDED) {
            return;
        }
        var sidebar = plugin.getSidebarManager();
        if (sidebar == null) {
            return;
        }
        String phaseLabel = switch (phase) {
            case TUTORIAL -> "<gradient:#00C1FF:#2BD4FF>Tutorial</gradient>";
            case PREPARE -> "<gradient:#00FFC1:#00FF5A>Prepare</gradient>";
            case WAVE -> "<gradient:#00C1FF:#00D6FF>Fala " + wave + "</gradient>";
            case UPGRADE -> "<gradient:#FFD900:#FF9B00>Upgrade</gradient>";
            case COIN_SHOP -> "<gradient:#FFD900:#FFFFFF>Shop</gradient>";
            case BOSS_CINEMATIC -> "<gradient:#FF5C5C:#FF9B00>Boss Cam</gradient>";
            case BOSS -> bossPhase2
                ? "<gradient:#C800FF:#FC73F7>Boss F2</gradient>"
                : "<gradient:#FF3B3B:#FF5C5C>Boss</gradient>";
            case FROZEN_REJOIN -> "<gradient:#FFB800:#FF9B00>Freeze</gradient>";
            default -> "&7...";
        };
        int alive = 0;
        for (UUID uuid : players) {
            if (!deadSpectators.contains(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.getGameMode() != GameMode.SPECTATOR) {
                    alive++;
                }
            }
        }
        String bossLine = "&7—";
        if ((phase == Phase.BOSS || phase == Phase.BOSS_CINEMATIC) && bossId != null
            && Bukkit.getEntity(bossId) instanceof LivingEntity boss && !boss.isDead()) {
            bossLine = "<gradient:#FF5C5C:#FF9B00>" + (int) Math.ceil(boss.getHealth())
                + "</gradient>&8/&f" + (int) Math.ceil(boss.getMaxHealth()) + "❤";
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            List<String> lines = List.of(
                "",
                "Tryb: <gradient:#00C1FF:#00FFC1>Dungeon Ninja</gradient></shadow>",
                "Faza: " + phaseLabel + "</shadow>",
                "Poziom: <gradient:#FFD900:#FF9B00>" + level + "</gradient></shadow>",
                phase == Phase.WAVE
                    ? "Postep: <gradient:#00FF5A:#9FFF00>" + Math.max(0, waveTotal - waveLeft)
                    + "</gradient>&8/&f" + waveTotal + "</shadow>"
                    : "Boss HP: " + bossLine + "</shadow>",
                "Druzyna: <gradient:#00FFC1:#2AFF5B>" + alive + "</gradient>&8/&f" + players.size() + "</shadow>",
                "Ticket: <gradient:#FFD900:#FF9B00>x" + ticketMultiplier + "</gradient></shadow>",
                "",
                "Twoje DMG: <gradient:#FFD900:#FF9B00>" + formatDmg(uuid) + "</gradient></shadow>",
                "Kille: <gradient:#00C1FF:#2BD4FF>" + killCounts.getOrDefault(uuid, 0) + "</gradient></shadow>",
                "Streak: <gradient:#FF9B00:#FF5C5C>" + killStreak.getOrDefault(uuid, 0) + "</gradient></shadow>",
                "Revive: <gradient:#00FF5A:#9FFF00>" + reviveCounts.getOrDefault(uuid, 0) + "</gradient></shadow>",
                "",
                "<gradient:#A7A7A7:#B5CFD4>Shift+PPM</gradient> &8= &fDash</shadow>"
            );
            sidebar.setCustomBoard(player,
                "&#C800FF&lN&#D926FC&lI&#EB4DFA&lN&#FC73F7&lJ&#E23AFB&lA &#C800FF&lD&#D926FC&lU&#EB4DFA&lN&#FC73F7&lG&#E23AFB&lE&#C800FF&lO&#D926FC&lN",
                lines);
        }
    }

    private void clearDungeonScoreboards() {
        var sidebar = plugin.getSidebarManager();
        if (sidebar == null) {
            return;
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sidebar.clearCustomBoard(player);
            }
        }
    }

    private void tickLowHpWarning() {
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getHealth() <= 8.0D && player.getHealth() > 0.0D) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.35F, 0.5F);
                MultiActionBarService.get().send(player, "dungeon-hp",
                    "<gradient:#FF5C5C:#FF9B00>Male HP!</gradient> &7Zjedz <gradient:#FFD900:#FF9B00>zlote jablko</gradient></shadow>", 30L);
            }
        }
    }

    private void showMvpScreen(boolean won) {
        UUID dmgMvp = topUuid(damageDealt);
        UUID killMvp = topUuidInt(killCounts);
        UUID clutchMvp = topUuidInt(reviveCounts);
        broadcastRaw("");
        broadcastRaw(won
            ? "<gradient:#00FFC1:#00FF5A><bold>MVP DUNGEONU</bold></gradient></shadow>"
            : "<gradient:#FF5C5C:#FF9B00><bold>STATYSTYKI RUNU</bold></gradient></shadow>");
        broadcastRaw("Najwiecej obrazen: <gradient:#FFD900:#FF9B00>" + nameOf(dmgMvp)
            + "</gradient> &8(&f" + formatDmg(dmgMvp) + "&8)</shadow>");
        broadcastRaw("Najwiecej zabojstw: <gradient:#00C1FF:#2BD4FF>" + nameOf(killMvp)
            + "</gradient> &8(&f" + killCounts.getOrDefault(killMvp, 0) + "&8)</shadow>");
        broadcastRaw("Najwiecej ozywnien: <gradient:#00FF5A:#9FFF00>" + nameOf(clutchMvp)
            + "</gradient> &8(&f" + reviveCounts.getOrDefault(clutchMvp, 0) + "&8)</shadow>");
        broadcastRaw("");
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            String sub = "Obrazenia <gradient:#FFD900:#FF9B00>" + formatDmg(uuid)
                + "</gradient> &8| &fZabojstwa <gradient:#00C1FF:#2BD4FF>" + killCounts.getOrDefault(uuid, 0)
                + "</gradient> &8| &fOzywienia <gradient:#00FF5A:#9FFF00>" + reviveCounts.getOrDefault(uuid, 0)
                + "</gradient></shadow>";
            sendDungeonTitle(player,
                won ? "<gradient:#FFD900:#C800FF><bold>MVP</bold></gradient></shadow>"
                    : "<gradient:#FFB800:#FF5C5C><bold>STATYSTYKI</bold></gradient></shadow>",
                sub, 10, 75, 15);
        }
    }

    private UUID topUuid(Map<UUID, Double> map) {
        UUID best = null;
        double bestVal = -1.0D;
        for (UUID uuid : players) {
            double v = map.getOrDefault(uuid, 0.0D);
            if (v > bestVal) {
                bestVal = v;
                best = uuid;
            }
        }
        return best;
    }

    private UUID topUuidInt(Map<UUID, Integer> map) {
        UUID best = null;
        int bestVal = -1;
        for (UUID uuid : players) {
            int v = map.getOrDefault(uuid, 0);
            if (v > bestVal) {
                bestVal = v;
                best = uuid;
            }
        }
        return best;
    }

    private String nameOf(UUID uuid) {
        if (uuid == null) {
            return "-";
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : "-";
    }

    private String formatDmg(UUID uuid) {
        if (uuid == null) {
            return "0";
        }
        return String.valueOf((int) Math.round(damageDealt.getOrDefault(uuid, 0.0D)));
    }

    private void announceServer(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(ColorUtils.colorizeComponent(message));
        }
    }

    private String playerNames() {
        List<String> names = new ArrayList<>();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                names.add(player.getName());
            } else {
                String offline = Bukkit.getOfflinePlayer(uuid).getName();
                names.add(offline != null ? offline : uuid.toString().substring(0, 8));
            }
        }
        return names.isEmpty() ? "?" : String.join(", ", names);
    }

    private void enforceGate() {
        Location current = phase == Phase.BOSS
            ? locations.getBoss(level, world)
            : locations.getWave(level, wave, world);
        if (current == null) {
            return;
        }
        Location next = null;
        int max = locations.maxWaves(level);
        if (phase == Phase.WAVE && wave < max) {
            next = locations.getWave(level, wave + 1, world);
        } else if (phase == Phase.WAVE) {
            next = locations.getBoss(level, world);
        } else if (phase == Phase.BOSS && level < 3) {
            next = locations.getWave(level + 1, 1, world);
        }
        if (next == null) {
            return;
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || deadSpectators.contains(uuid) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distanceSquared(next) < 36.0D && player.getLocation().distanceSquared(current) > 10.0D) {
                player.teleport(current);
                player.sendMessage(ColorUtils.colorizeComponent(
                    settings.getString("messages.gate-blocked", "&#FF0000✘ &8Ξ &cNajpierw pokonaj aktualna fale!")
                ));
            }
        }
    }

    private void spawnPreviewMobs() {
        // 1:1 jak prawdziwe fale — rozrzucone, nie stack na 1 bloku.
        for (int lvl = 1; lvl <= 3; lvl++) {
            int max = locations.maxWaves(lvl);
            for (int w = 1; w <= max; w++) {
                Location loc = locations.getWave(lvl, w, world);
                if (loc == null) {
                    continue;
                }
                int partyBonus = Math.max(0, startingPartySize() - 1)
                    * Math.max(0, settings.getInt("party-mobs-per-player", 1));
                int count = Math.max(4, settings.getInt("mobs-per-wave-base", 5) + lvl + (w / 3)
                    + Math.max(0, ticketMultiplier - 1) + partyBonus);
                for (int i = 0; i < count; i++) {
                    Location safe = findSafeSpawn(spread(loc, i, count));
                    LivingEntity mob = spawnWaveMob(safe, true);
                    if (mob != null) {
                        mob.setAI(false);
                        mob.setInvulnerable(true);
                        mob.setCollidable(false);
                        mob.addScoreboardTag("holly_dungeon_preview");
                    }
                }
            }
            Location bossLoc = locations.getBoss(lvl, world);
            if (bossLoc != null) {
                LivingEntity boss = spawnBoss(lvl, findSafeSpawn(bossLoc), true);
                if (boss != null) {
                    boss.setAI(false);
                    boss.setInvulnerable(true);
                    boss.setCollidable(false);
                    boss.addScoreboardTag("holly_dungeon_preview");
                }
            }
        }
    }

    private void startTutorialCamera() {
        List<Location> cams = locations.getTutorialCameras(world);
        if (cams.isEmpty()) {
            return;
        }
        Map<UUID, Location> origins = new HashMap<>();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            origins.put(uuid, player.getLocation().clone());
            player.setGameMode(GameMode.SPECTATOR);
            player.setFlySpeed(0.0F);
            tutorialCameraLock = player.getLocation().clone();
        }
        int segment = Math.max(20, (settings.getInt("tutorial-seconds", 24) * 20) / Math.max(1, cams.size()));
        for (int i = 0; i < cams.size(); i++) {
            Location cam = cams.get(i);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (phase != Phase.TUTORIAL) {
                    return;
                }
                for (UUID uuid : players) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) {
                        tutorialCameraLock = cam.clone();
                        player.setFlySpeed(0.0F);
                        player.teleport(cam);
                    }
                }
            }, (long) i * segment);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase != Phase.TUTORIAL) {
                return;
            }
            for (Map.Entry<UUID, Location> entry : origins.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    player.teleport(entry.getValue());
                    forceFighterState(player);
                }
            }
        }, (long) cams.size() * segment + 5L);
    }

    private LivingEntity spawnWaveMob(Location loc, boolean preview) {
        // Bez wither skeleton w falach — zostaw vanilla tylko jako boss 1.
        EntityType[] types = {EntityType.ZOMBIE, EntityType.HUSK, EntityType.SKELETON, EntityType.STRAY};
        EntityType type = types[ThreadLocalRandom.current().nextInt(types.length)];
        LivingEntity entity = (LivingEntity) world.spawnEntity(findSafeSpawn(loc), type);
        String name = nextName();
        double hp = (20.0D + level * 3.5D + wave * 0.7D)
            * (1.0D + (ticketMultiplier - 1) * 0.35D)
            * partyHpMultiplier(false);
        if (activeMiniEvent == MiniEvent.FRENZY) {
            hp *= 0.88D;
        }
        String display = preview
            ? "<gradient:#FF5C5C:#FF9B00>" + name + "</gradient></shadow>"
            : name;
        applyCombatStats(entity, hp, display, false);
        AttributeInstance dmg = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) {
            // Protection + netherite mocno tnie — wysoki base + tickety skalują dmg.
            double base = 9.0D + level * 1.25D + (ticketMultiplier - 1) * 1.1D;
            double waveBonus = (wave / 2) * 1.4D;
            dmg.setBaseValue(base + waveBonus);
        }
        if (entity instanceof org.bukkit.entity.AbstractSkeleton skeleton) {
            ItemStack bow = new ItemStack(Material.BOW);
            int power = 2 + Math.max(0, level - 1) + (wave / 2);
            bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, Math.min(6, power));
            bow.addUnsafeEnchantment(Enchantment.ARROW_KNOCKBACK, 1);
            skeleton.getEquipment().setItemInMainHand(bow);
            skeleton.getEquipment().setItemInMainHandDropChance(0.0F);
        }
        if (!preview) {
            aliveMobs.add(entity.getUniqueId());
            mobNames.put(entity.getUniqueId(), name);
        } else if (entity instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
        }
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        return entity;
    }

    private LivingEntity spawnBountyElite(Location loc) {
        if (loc == null) {
            return null;
        }
        LivingEntity entity = (LivingEntity) world.spawnEntity(findSafeSpawn(loc), EntityType.VINDICATOR);
        String name = "<gradient:#C800FF:#FFD900><bold>BOUNTY</bold></gradient> &fElite";
        double hp = (34.0D + level * 5.0D + wave * 1.5D)
            * (1.0D + (ticketMultiplier - 1) * 0.35D)
            * partyHpMultiplier(false);
        applyCombatStats(entity, hp, name, false);
        entity.addScoreboardTag("holly_dungeon_bounty");
        entity.setGlowing(true);
        AttributeInstance dmg = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(10.0D + level * 1.4D + (ticketMultiplier - 1) * 0.9D);
        }
        AttributeInstance speed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 1.12D);
        }
        aliveMobs.add(entity.getUniqueId());
        mobNames.put(entity.getUniqueId(), strip(name));
        if (entity instanceof Mob mob) {
            setFocused(mob, true);
        }
        return entity;
    }

    private LivingEntity spawnWaveElite(Location loc, int index) {
        if (loc == null) {
            return null;
        }
        return (index % 2 == 0) ? spawnBerserkElite(loc) : spawnBomberElite(loc);
    }

    private LivingEntity spawnBerserkElite(Location loc) {
        LivingEntity entity = (LivingEntity) world.spawnEntity(findSafeSpawn(loc), EntityType.WITHER_SKELETON);
        String name = "<gradient:#C800FF:#00C1FF><bold>BERSERK</bold></gradient> &fElite";
        double hp = (28.0D + level * 4.0D)
            * (1.0D + (ticketMultiplier - 1) * 0.25D)
            * partyHpMultiplier(false);
        applyCombatStats(entity, hp, name, false);
        entity.addScoreboardTag("holly_dungeon_elite_berserk");
        entity.setGlowing(true);
        AttributeInstance dmg = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(8.5D + level * 1.1D + (ticketMultiplier - 1) * 0.8D);
        }
        AttributeInstance speed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(Math.max(0.28D, speed.getBaseValue() * 1.15D));
        }
        aliveMobs.add(entity.getUniqueId());
        mobNames.put(entity.getUniqueId(), "BERSERK Elite");
        if (entity instanceof Mob mob) {
            setFocused(mob, true);
        }
        return entity;
    }

    private LivingEntity spawnBomberElite(Location loc) {
        // Bez CREEPER — fuse/wybuch psuje gre. Bombowiec = husk + custom explode w tickAbilities.
        LivingEntity entity = (LivingEntity) world.spawnEntity(findSafeSpawn(loc), EntityType.HUSK);
        String name = "<gradient:#FF5C5C:#FF9B00><bold>BOMBOWIEC</bold></gradient> &fElite";
        double hp = (24.0D + level * 3.5D)
            * (1.0D + (ticketMultiplier - 1) * 0.25D)
            * partyHpMultiplier(false);
        applyCombatStats(entity, hp, name, false);
        entity.addScoreboardTag("holly_dungeon_elite_bomber");
        entity.setGlowing(true);
        AttributeInstance dmg = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(7.5D + level * 1.0D + (ticketMultiplier - 1) * 0.7D);
        }
        aliveMobs.add(entity.getUniqueId());
        mobNames.put(entity.getUniqueId(), "BOMBOWIEC Elite");
        if (entity instanceof Mob mob) {
            setFocused(mob, true);
        }
        return entity;
    }

    public List<Player> getOnlineFighters() {
        List<Player> out = new ArrayList<>();
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                out.add(player);
            }
        }
        return out;
    }

    public int paidReviveCost() {
        return ticketMultiplier <= 2 ? 5 : 10;
    }

    public int wipeReviveCost() {
        return Math.max(1, settings.getInt("wipe-revive-cost", 10));
    }

    public int wipeReviveSecondsLeft() {
        if (!wipeReviveOpen) {
            return 0;
        }
        return (int) Math.max(0L, (wipeReviveEndsAtMs - System.currentTimeMillis() + 999L) / 1000L);
    }

    public boolean isWipeReviveOpen() {
        return wipeReviveOpen;
    }

    private void offerWipeReviveOrLose() {
        if (phase == Phase.ENDED || wipeReviveOpen || hasAliveFighters()) {
            return;
        }
        if (phase != Phase.WAVE && phase != Phase.BOSS) {
            lose();
            return;
        }
        startWipeReviveOffer();
    }

    private void startWipeReviveOffer() {
        if (wipeReviveOpen || phase == Phase.ENDED) {
            return;
        }
        wipeReviveOpen = true;
        wipeReviveCharging = false;
        wipeReviveDeclined.clear();
        int seconds = Math.max(5, settings.getInt("wipe-revive-seconds", 30));
        wipeReviveEndsAtMs = System.currentTimeMillis() + seconds * 1000L;
        int cost = wipeReviveCost();
        int team = players.size();
        broadcastRaw("<gradient:#FFD900:#FF9B00>WIPE</gradient> &8— &fodrodzenie calej druzyny za &e"
            + cost + " VPLN &7(/portfel, jak solo) &8| &f" + seconds + "s &7| &a" + team + " osob");
        for (UUID uuid : players) {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {
                continue;
            }
            ColorUtils.sendTitle(online,
                "<gradient:#FFD900:#FF9B00><bold>ODRODZENIE</bold></gradient></shadow>",
                "&e" + cost + " VPLN &f= cala druzyna &8(&e" + seconds + "s&8)</shadow>",
                8, 50, 10);
            // Po wipe wszyscy sa martwi — kazdy dostaje GUI kup/anuluj.
            openWipeReviveGui(online);
        }
        if (wipeReviveTask != null) {
            wipeReviveTask.cancel();
        }
        wipeReviveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            wipeReviveTask = null;
            expireWipeReviveOffer("&#FF0000✘ &8Ξ &cCzas na odrodzenie minal — przegrana.");
        }, seconds * 20L);
    }

    private void expireWipeReviveOffer(String message) {
        if (!wipeReviveOpen || phase == Phase.ENDED) {
            return;
        }
        wipeReviveOpen = false;
        wipeReviveCharging = false;
        if (wipeReviveTask != null) {
            wipeReviveTask.cancel();
            wipeReviveTask = null;
        }
        closeWipeReviveGuis();
        clearAllWipeReviveViewerStates();
        if (message != null && !message.isBlank()) {
            broadcastRaw(message);
        }
        lose();
    }

    private void openWipeReviveGui(Player player) {
        if (player == null || !player.isOnline() || !wipeReviveOpen || phase == Phase.ENDED) {
            return;
        }
        // Po smierci: respawn + adventure (spectator nie otwiera GUI), potem inventory.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !wipeReviveOpen || phase == Phase.ENDED) {
                return;
            }
            prepareWipeReviveViewer(player);
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof DungeonWipeReviveGUI)) {
                new DungeonWipeReviveGUI(plugin, this, player).open();
            }
        }, 8L);
    }

    /** Po zamknieciu GUI — otworz ponownie dopoki oferta zyje (chyba ze Anuluj). */
    public void reopenWipeReviveIfNeeded(Player player) {
        if (!wipeReviveOpen || phase == Phase.ENDED || player == null || !player.isOnline()) {
            return;
        }
        if (!players.contains(player.getUniqueId())) {
            return;
        }
        if (wipeReviveDeclined.contains(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!wipeReviveOpen || !player.isOnline() || phase == Phase.ENDED) {
                return;
            }
            if (wipeReviveDeclined.contains(player.getUniqueId())) {
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof DungeonWipeReviveGUI) {
                return;
            }
            prepareWipeReviveViewer(player);
            new DungeonWipeReviveGUI(plugin, this, player).open();
        }, 5L);
    }

    public void declineWipeRevive(Player player) {
        if (player == null) {
            return;
        }
        wipeReviveDeclined.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#FFD900⚠ &8Ξ &eAnulowano platnosc. Inni moga zaplacic &e"
                + wipeReviveCost() + " VPLN &ez /portfel — zostalo &e"
                + wipeReviveSecondsLeft() + "s&e."
        ));
    }

    private void closeWipeReviveGuis() {
        for (UUID uuid : players) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.getOpenInventory().getTopInventory().getHolder() instanceof DungeonWipeReviveGUI) {
                online.closeInventory();
            }
        }
    }

    public void acceptWipeRevive(Player payer) {
        if (payer == null || !wipeReviveOpen || phase == Phase.ENDED || wipeReviveCharging) {
            return;
        }
        if (!players.contains(payer.getUniqueId())) {
            return;
        }
        int cost = wipeReviveCost();
        var wallet = plugin.getWalletManager();
        if (wallet == null) {
            payer.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cPortfel jest wylaczony."));
            return;
        }
        wipeReviveCharging = true;
        UUID payerId = payer.getUniqueId();
        wallet.hasBalance(payerId, cost).thenAccept(has -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!wipeReviveOpen || phase == Phase.ENDED) {
                wipeReviveCharging = false;
                return;
            }
            if (!Boolean.TRUE.equals(has)) {
                wipeReviveCharging = false;
                payer.sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cPotrzebujesz &e" + cost + " VPLN &cw portfelu."));
                payer.playSound(payer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7F, 1.0F);
                return;
            }
            wallet.removeBalance(payerId, cost, "dungeon", payer.getName(), "wipe-revive",
                payerId.toString(), "Dungeon wipe revive x" + players.size()).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    wipeReviveCharging = false;
                    if (!wipeReviveOpen || phase == Phase.ENDED) {
                        return;
                    }
                    if (!Boolean.TRUE.equals(ok)) {
                        payer.sendMessage(ColorUtils.colorizeComponent(
                            "&#FF0000✘ &8Ξ &cNie udalo sie pobrac VPLN."));
                        return;
                    }
                    completeWipeRevive(payer, cost);
                }));
        }));
    }

    private void completeWipeRevive(Player payer, int cost) {
        wipeReviveOpen = false;
        wipeReviveCharging = false;
        if (wipeReviveTask != null) {
            wipeReviveTask.cancel();
            wipeReviveTask = null;
        }
        closeWipeReviveGuis();
        clearAllWipeReviveViewerStates();
        List<UUID> toRevive = new ArrayList<>(deadSpectators);
        Location rally = locations.getPlayerSpawn(world);
        if (rally == null) {
            rally = getRespawnLocation();
        }
        for (UUID deadId : toRevive) {
            deathLocations.put(deadId, rally.clone().add(
                (ThreadLocalRandom.current().nextDouble() - 0.5D) * 2.0D,
                0.5D,
                (ThreadLocalRandom.current().nextDouble() - 0.5D) * 2.0D
            ));
            performRevive(payer, deadId, false);
        }
        broadcastRaw("<gradient:#00FF5A:#9FFF00>" + payer.getName()
            + "</gradient> &fodrodzil &acala druzyne &fza &e" + cost + " VPLN &7("
            + toRevive.size() + " osob)!");
        for (UUID uuid : players) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                ColorUtils.sendTitle(online,
                    "<gradient:#00FF5A:#9FFF00><bold>DRUZYNA WRACA</bold></gradient></shadow>",
                    "Odrodzenie za &e" + cost + " VPLN</shadow>",
                    8, 40, 10);
            }
        }
    }

    public void tryPaidRevive(Player player) {
        if (player == null || !deadSpectators.contains(player.getUniqueId())) {
            return;
        }
        if (wipeReviveOpen) {
            acceptWipeRevive(player);
            return;
        }
        if (phase != Phase.WAVE && phase != Phase.BOSS) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPaidReviveClickMs < 800L) {
            return;
        }
        lastPaidReviveClickMs = now;
        int cost = paidReviveCost();
        var wallet = plugin.getWalletManager();
        if (wallet == null) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cPortfel jest wylaczony."));
            return;
        }
        UUID id = player.getUniqueId();
        wallet.hasBalance(id, cost).thenAccept(has -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !deadSpectators.contains(id) || wipeReviveOpen) {
                return;
            }
            if (!Boolean.TRUE.equals(has)) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cPotrzebujesz &e" + cost + " VPLN &cw portfelu."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7F, 1.0F);
                return;
            }
            wallet.removeBalance(id, cost, "dungeon", player.getName(), "paid-revive",
                id.toString(), "Dungeon revive x" + ticketMultiplier).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !deadSpectators.contains(id) || wipeReviveOpen) {
                        return;
                    }
                    if (!Boolean.TRUE.equals(ok)) {
                        player.sendMessage(ColorUtils.colorizeComponent(
                            "&#FF0000✘ &8Ξ &cNie udalo sie pobrac VPLN."));
                        return;
                    }
                    performRevive(null, id, false);
                    player.sendMessage(ColorUtils.colorizeComponent(
                        "&#00FF3B✔ &8Ξ &#4EFA76Odrodzenie kupione za &e" + cost + " VPLN&a!"));
                    broadcastRaw("<gradient:#FFD900:#FF9B00>" + player.getName()
                        + "</gradient> &fodrodzil sie za &e" + cost + " VPLN");
                }));
        }));
    }

    private LivingEntity spawnBoss(int level, Location loc) {
        return spawnBoss(level, loc, false);
    }

    private LivingEntity spawnBoss(int level, Location loc, boolean preview) {
        String path = "bosses." + level + ".";
        String typeName = settings.getString(path + "type", "WITHER_SKELETON");
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException exception) {
            type = EntityType.WITHER_SKELETON;
        }
        LivingEntity entity = (LivingEntity) world.spawnEntity(findSafeSpawn(loc), type);
        String name = settings.getString(path + "name", "&cBoss");
        // ~35+ hitow; boss 1 mocniejszy (Sensei)
        double defaultHp = level == 1 ? 260.0D : level == 2 ? 340.0D : 420.0D;
        double baseHp = settings.getDouble(path + "health", defaultHp);
        double hp = baseHp
            * (1.0D + (ticketMultiplier - 1) * 0.18D)
            * partyHpMultiplier(true);
        applyCombatStats(entity, hp, name, true);
        AttributeInstance dmg = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        double soloDmgScale = startingPartySize() <= 1 ? 0.85D : 1.0D;
        if (dmg != null) {
            // Surowsze uderzenia (Protection + netherite mocno tnie).
            if (type == EntityType.WITHER_SKELETON) {
                dmg.setBaseValue((9.0D + (ticketMultiplier - 1) * 1.0D) * soloDmgScale);
            } else if (type == EntityType.RAVAGER) {
                dmg.setBaseValue((15.0D + (ticketMultiplier - 1) * 1.3D) * soloDmgScale);
            } else if (type == EntityType.PIGLIN_BRUTE) {
                dmg.setBaseValue((12.0D + (ticketMultiplier - 1) * 1.1D) * soloDmgScale);
            } else {
                dmg.setBaseValue((10.0D + level * 1.2D + (ticketMultiplier - 1) * 1.0D) * soloDmgScale);
            }
        }
        if (type == EntityType.WITHER_SKELETON) {
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, false));
            AttributeInstance kb = entity.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.setBaseValue(0.95D);
            }
        }
        if (type == EntityType.RAVAGER) {
            AttributeInstance speed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null && ticketMultiplier <= 1) {
                speed.setBaseValue(speed.getBaseValue() * 0.92D);
            }
        }
        if (entity instanceof org.bukkit.entity.PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        if (preview) {
            if (entity instanceof Mob mob) {
                mob.setAI(false);
                mob.setAware(false);
            }
            return entity;
        }
        if (entity instanceof Mob mob) {
            mob.setAware(true);
            mob.setTarget(nearestPlayer(entity.getLocation()));
        }
        aliveMobs.add(entity.getUniqueId());
        bossId = entity.getUniqueId();
        return entity;
    }

    private int startingPartySize() {
        return Math.max(1, players.size());
    }

    /** 1 gracz = solo mult; od 2 graczy skromne skalowanie (5p nie ma byc 2.5x HP). */
    private double partyHpMultiplier(boolean boss) {
        int size = startingPartySize();
        double global = boss
            ? Math.max(0.2D, settings.getDouble("boss-hp-mult", 0.72D))
            : Math.max(0.2D, settings.getDouble("mob-hp-mult", 0.75D));
        if (size <= 1) {
            double solo = boss
                ? settings.getDouble("solo-boss-hp-mult", 0.55D)
                : settings.getDouble("solo-mob-hp-mult", 0.80D);
            return solo * global;
        }
        double per = boss
            ? settings.getDouble("party-boss-hp-per-player", 0.12D)
            : settings.getDouble("party-hp-per-player", 0.14D);
        per = Math.max(0.0D, Math.min(0.25D, per));
        return (1.0D + (size - 1) * per) * global;
    }

    private void applyCombatStats(LivingEntity entity, double health, String displayName, boolean boss) {
        AttributeInstance maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        entity.setHealth(Math.min(health, entity.getMaxHealth()));
        AttributeInstance kb = entity.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.setBaseValue(settings.getDouble("knockback-resistance", 0.9D));
        }
        AttributeInstance speed = entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null && boss && entity.getType() != EntityType.WITHER_SKELETON) {
            speed.setBaseValue(speed.getBaseValue() * (1.05D + level * 0.03D));
        }
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.setCanPickupItems(false);
        entity.customName(ColorUtils.colorizeComponent(displayName).decoration(TextDecoration.ITALIC, false));
        entity.setCustomNameVisible(true);
        entity.addScoreboardTag("holly_dungeon_mob");
        if (boss) {
            entity.addScoreboardTag("holly_dungeon_boss");
            entity.addScoreboardTag("holly_dungeon_boss_" + level);
        }
        entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, false));
    }

    private void refreshMobNames() {
        for (UUID id : aliveMobs) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                lastMobNameHp.remove(id);
                continue;
            }
            int hp = (int) Math.ceil(living.getHealth());
            Integer prev = lastMobNameHp.get(id);
            if (prev != null && prev == hp) {
                continue;
            }
            lastMobNameHp.put(id, hp);
            String base = mobNames.getOrDefault(id, living.getName());
            if (bossId != null && bossId.equals(id)) {
                base = strip(settings.getString("bosses." + level + ".name", "Boss"));
            }
            int max = (int) Math.ceil(living.getMaxHealth());
            living.customName(ColorUtils.colorizeComponent(base + " &8| &#FF5C5C❤ &f" + hp + "&7/&f" + max)
                .decoration(TextDecoration.ITALIC, false));
            living.setCustomNameVisible(true);
        }
    }

    private void tickAbilities() {
        if (phase != Phase.WAVE && phase != Phase.BOSS) {
            return;
        }
        for (UUID id : new HashSet<>(aliveMobs)) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof Mob living) || living.isDead()) {
                continue;
            }
            if (phase == Phase.WAVE && !focusedMobs.contains(id)) {
                living.setTarget(null);
                living.setAI(false);
                continue;
            }
            Player target = nearestPlayer(living.getLocation());
            if (target == null) {
                living.setTarget(null);
                continue;
            }
            living.setTarget(target);
            boolean isBoss = bossId != null && bossId.equals(id);
            if (isBoss) {
                tickBossAbility(living, target, level);
                continue;
            }
            if (living.getScoreboardTags().contains("holly_dungeon_elite_bomber")) {
                if (living.getLocation().distanceSquared(target.getLocation()) <= 6.25D) {
                    Location boom = living.getLocation().clone().add(0, 0.5D, 0);
                    living.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, boom, 1);
                    living.getWorld().spawnParticle(Particle.FLAME, boom, 30, 0.6, 0.4, 0.6, 0.02);
                    living.getWorld().playSound(boom, Sound.ENTITY_GENERIC_EXPLODE, 0.85F, 1.15F);
                    for (Player fighter : getOnlineFighters()) {
                        if (fighter.getLocation().distanceSquared(boom) <= 12.25D) {
                            fighter.damage(5.5D + level * 0.7D + (ticketMultiplier - 1) * 0.5D, living);
                            Vector knock = fighter.getLocation().toVector().subtract(boom.toVector());
                            if (knock.lengthSquared() > 0.01D) {
                                knock = knock.normalize().multiply(0.55D);
                                knock.setY(0.35D);
                                fighter.setVelocity(knock);
                            }
                        }
                    }
                    living.setHealth(0);
                }
                continue;
            }
            // Zwykle moby: rzadkie skille, glownie melee.
            if (ThreadLocalRandom.current().nextInt(8) > 1) {
                continue;
            }
            int roll = ThreadLocalRandom.current().nextInt(3);
            switch (roll) {
                case 0 -> {
                    Vector dash = target.getLocation().toVector().subtract(living.getLocation().toVector());
                    if (dash.lengthSquared() > 0.01D) {
                        dash = dash.normalize().multiply(0.8D);
                        dash.setY(0.15D);
                        living.setVelocity(dash);
                    }
                }
                case 1 -> launchShuriken(living);
                case 2 -> {
                    if (living.getLocation().distanceSquared(target.getLocation()) < 12.0D) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 15, 0));
                        target.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, living.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.01);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void tickBossAbility(Mob living, Player target, int bossLevel) {
        int roll = ThreadLocalRandom.current().nextInt(4 + bossLevel);
        switch (bossLevel) {
            case 1 -> {
                // Sensei: dash + smoke + lekki wither tick.
                if (roll <= 1) {
                    Vector dash = target.getLocation().toVector().subtract(living.getLocation().toVector());
                    if (dash.lengthSquared() > 0.01D) {
                        dash = dash.normalize().multiply(1.15D);
                        dash.setY(0.22D);
                        living.setVelocity(dash);
                    }
                } else if (roll == 2 && living.getLocation().distanceSquared(target.getLocation()) < 16.0D) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 20, 0));
                    target.getWorld().spawnParticle(Particle.SMOKE_LARGE, living.getLocation().add(0, 1, 0), 18, 0.35, 0.4, 0.35, 0.01);
                } else if (roll >= 3 && living.getLocation().distanceSquared(target.getLocation()) < 12.0D) {
                    target.damage(1.0D, living);
                    living.getWorld().playSound(living.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 0.8F, 0.7F);
                }
            }
            case 2 -> {
                // Oni: leap + double shuriken.
                if (roll <= 1) {
                    Vector leap = target.getLocation().toVector().subtract(living.getLocation().toVector());
                    if (leap.lengthSquared() > 0.01D) {
                        leap = leap.normalize().multiply(1.0D);
                        leap.setY(0.45D);
                        living.setVelocity(leap);
                    }
                } else if (roll == 2) {
                    launchShuriken(living);
                    launchShuriken(living);
                } else if (roll >= 3 && living.getLocation().distanceSquared(target.getLocation()) < 12.0D) {
                    target.damage(1.5D, living);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 0));
                }
            }
            default -> {
                // Ryujin: slam + charge + shuriken rain.
                if (roll <= 1) {
                    Vector charge = target.getLocation().toVector().subtract(living.getLocation().toVector());
                    if (charge.lengthSquared() > 0.01D) {
                        charge = charge.normalize().multiply(1.2D);
                        charge.setY(0.25D);
                        living.setVelocity(charge);
                    }
                } else if (roll == 2) {
                    launchShuriken(living);
                    launchShuriken(living);
                    launchShuriken(living);
                } else if (roll >= 3 && living.getLocation().distanceSquared(target.getLocation()) < 25.0D) {
                    Vector knock = target.getLocation().toVector().subtract(living.getLocation().toVector());
                    if (knock.lengthSquared() > 0.01D) {
                        knock = knock.normalize().multiply(0.75D);
                        knock.setY(0.32D);
                        target.setVelocity(knock);
                    }
                    target.damage(3.5D, living);
                    target.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_LARGE, living.getLocation(), 1);
                }
            }
        }
    }

    private Player nearestPlayer(Location loc) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.getWorld().equals(world) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(loc);
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private void giveKits() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                giveKits(player);
            }
        }
    }

    private void giveKits(Player player) {
        player.getInventory().clear();
        ItemStack helm = new ItemStack(Material.NETHERITE_HELMET);
        ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        int prot = Math.min(
            Math.max(1, settings.getInt("max-protection", 2)),
            armorProt.getOrDefault(player.getUniqueId(), 0)
        );
        if (prot > 0) {
            for (ItemStack piece : List.of(helm, chest, legs, boots)) {
                piece.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, prot);
            }
        }
        player.getInventory().setHelmet(helm);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(settings.getInt("kit.sword-custom-model-data", 1));
            meta.displayName(ColorUtils.colorizeComponent("&b&lKatana Ninja").decoration(TextDecoration.ITALIC, false));
            sword.setItemMeta(meta);
        }
        int sharp = swordSharp.getOrDefault(player.getUniqueId(), 0);
        if (sharp > 0) {
            sword.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, sharp);
        }
        player.getInventory().addItem(sword);
        player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, settings.getInt("kit.golden-apples", 12)));
        player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, settings.getInt("kit.steak", 32)));
        applyBonusMaxHp(player);
        reapplyBuffUpgrades(player);
    }

    private void applyBonusMaxHp(Player player) {
        double bonus = bonusMaxHp.getOrDefault(player.getUniqueId(), 0.0D);
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        double base = PlayerHealthBase.resolveBaseMaxHealth(plugin, player);
        if (!Double.isFinite(base) || base < 1.0D) {
            base = 20.0D;
        }
        maxHealth.setBaseValue(base + bonus);
        if (player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    private void reapplyBuffUpgrades(Player player) {
        Set<DungeonUpgradeGUI.UpgradeType> buffs = activeBuffUpgrades.get(player.getUniqueId());
        if (buffs == null || buffs.isEmpty()) {
            return;
        }
        // Tylko pasywne (lifesteal) — potion buffy trwaja 2 min i nie sa odnawiane w nieskonczonosc.
        // (Pozostawione puste celowo dla potionow; lifesteal jest flagą w activeBuffUpgrades.)
    }

    private static final int BUFF_TICKS = 20 * 120; // 2 minuty

    private void applyTimedBuff(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, BUFF_TICKS, amplifier, false, true, true));
    }

    private void openUpgradeChoice(Runnable continueRun) {
        afterUpgradeContinue = continueRun;
        pendingUpgrades.clear();
        upgradeFreezeLoc.clear();
        upgradeChoices.clear();
        upgradeChoiceLabels.clear();
        offeredUpgrades.clear();
        pendingUpgradePicks.clear();
        cancelShopTimers();
        phase = Phase.UPGRADE;
        movementLocked = false;
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            pendingUpgrades.add(uuid);
            offeredUpgrades.put(uuid, rollUpgradeOffer(uuid, 6));
            pendingUpgradePicks.put(uuid, new ArrayList<>());
            sendDungeonTitle(player,
                "<gradient:#FFD900:#C800FF><bold>ULEPSZENIE</bold></gradient></shadow>",
                "Wybierz &e2 &fbonusy &8— &fmasz &e15s</shadow>",
                8, 50, 10);
            new DungeonUpgradeGUI(plugin, this, player).open();
        }
        if (pendingUpgrades.isEmpty()) {
            finishUpgrades();
            return;
        }
        int timeout = Math.max(60, settings.getInt("upgrade-timeout-ticks", 300));
        startShopCountdown(timeout, "upgrade");
        upgradeTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::timeoutUpgrades, timeout);
    }

    private void timeoutUpgrades() {
        if (phase != Phase.UPGRADE) {
            return;
        }
        for (UUID uuid : new HashSet<>(pendingUpgrades)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                pendingUpgrades.remove(uuid);
                continue;
            }
            List<DungeonUpgradeGUI.UpgradeType> picks = new ArrayList<>(
                pendingUpgradePicks.getOrDefault(uuid, List.of()));
            List<DungeonUpgradeGUI.UpgradeType> offer = offeredUpgrades.getOrDefault(uuid, List.of());
            for (DungeonUpgradeGUI.UpgradeType type : offer) {
                if (picks.size() >= 2) {
                    break;
                }
                if (!picks.contains(type)) {
                    picks.add(type);
                }
            }
            if (picks.size() < 2) {
                for (DungeonUpgradeGUI.UpgradeType type : DungeonUpgradeGUI.UpgradeType.values()) {
                    if (type == DungeonUpgradeGUI.UpgradeType.TOTEM || picks.contains(type)) {
                        continue;
                    }
                    picks.add(type);
                    if (picks.size() >= 2) {
                        break;
                    }
                }
            }
            if (picks.size() >= 2) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#FFAA00⚠ &8Ξ &eCzas minal — auto-wybor upgrade'ow."));
                finalizeUpgradePicks(player, new ArrayList<>(picks.subList(0, 2)));
            } else {
                pendingUpgrades.remove(uuid);
            }
        }
        if (phase == Phase.UPGRADE && pendingUpgrades.isEmpty()) {
            finishUpgrades();
            for (UUID uuid : players) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.closeInventory();
                }
            }
        }
    }

    private List<DungeonUpgradeGUI.UpgradeType> rollUpgradeOffer(UUID uuid, int count) {
        Set<DungeonUpgradeGUI.UpgradeType> banned = lastUpgradePicks.getOrDefault(uuid, Set.of());
        List<DungeonUpgradeGUI.UpgradeType> pool = new ArrayList<>();
        for (DungeonUpgradeGUI.UpgradeType type : DungeonUpgradeGUI.UpgradeType.values()) {
            if (type == DungeonUpgradeGUI.UpgradeType.TOTEM) {
                continue; // Totem tylko z glosowania fali 12
            }
            if (banned.contains(type)) {
                continue;
            }
            if (type == DungeonUpgradeGUI.UpgradeType.ARMOR) {
                int maxProt = Math.max(1, settings.getInt("max-protection", 2));
                if (armorProt.getOrDefault(uuid, 0) >= maxProt) {
                    continue;
                }
            }
            pool.add(type);
        }
        if (pool.size() < count) {
            // Gdy za malo — doloz z banned (lepiej niz puste).
            for (DungeonUpgradeGUI.UpgradeType type : DungeonUpgradeGUI.UpgradeType.values()) {
                if (type == DungeonUpgradeGUI.UpgradeType.TOTEM) {
                    continue;
                }
                if (!pool.contains(type)) {
                    pool.add(type);
                }
            }
        }
        java.util.Collections.shuffle(pool, ThreadLocalRandom.current());
        List<DungeonUpgradeGUI.UpgradeType> offer = new ArrayList<>();
        for (DungeonUpgradeGUI.UpgradeType type : pool) {
            if (offer.size() >= count) {
                break;
            }
            offer.add(type);
        }
        return offer;
    }

    /** Klik w GUI: zbiera 2 wybory, potem finalizuje. */
    public void pickUpgrade(Player player, DungeonUpgradeGUI.UpgradeType type) {
        if (phase != Phase.UPGRADE || !pendingUpgrades.contains(player.getUniqueId())) {
            return;
        }
        List<DungeonUpgradeGUI.UpgradeType> picks = pendingUpgradePicks.computeIfAbsent(
            player.getUniqueId(), k -> new ArrayList<>());
        if (picks.contains(type)) {
            picks.remove(type);
            player.sendMessage(ColorUtils.colorizeComponent("&#FFAA00⚠ &8Ξ &eOdznaczono upgrade."));
            return;
        }
        if (picks.size() >= 2) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cMozesz wybrac tylko &f2 &cupgrade'y. Odznacz jeden."
            ));
            return;
        }
        picks.add(type);
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Wybrano &f" + picks.size() + "&a/2&a: " + type.shortLabel()
        ));
        if (picks.size() < 2) {
            return;
        }
        finalizeUpgradePicks(player, new ArrayList<>(picks));
    }

    private void finalizeUpgradePicks(Player player, List<DungeonUpgradeGUI.UpgradeType> picks) {
        if (!pendingUpgrades.remove(player.getUniqueId())) {
            return;
        }
        suppressUpgradeReopen.add(player.getUniqueId());
        for (DungeonUpgradeGUI.UpgradeType type : picks) {
            applyOneUpgrade(player, type);
        }
        lastUpgradePicks.put(player.getUniqueId(), new HashSet<>(picks));
        String label = picks.get(0).shortLabel() + " &8+ " + picks.get(1).shortLabel();
        upgradeChoiceLabels.put(player.getUniqueId(), label);
        if (!picks.isEmpty()) {
            upgradeChoices.put(player.getUniqueId(), picks.get(0));
        }
        pendingUpgradePicks.remove(player.getUniqueId());
        offeredUpgrades.remove(player.getUniqueId());

        scheduleRefreshWaitGuis();
        if (pendingUpgrades.isEmpty()) {
            finishUpgrades();
            for (UUID uuid : players) {
                suppressUpgradeReopen.add(uuid);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.closeInventory();
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, suppressUpgradeReopen::clear, 2L);
        } else {
            new DungeonUpgradeWaitGUI(plugin, this, player).open();
            MultiActionBarService.get().send(player, "dungeon",
                "Czekaj na druzyne: <gradient:#FFD900:#FF9B00>" + pendingUpgrades.size()
                    + "</gradient> &fosob</shadow>", 45L);
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> suppressUpgradeReopen.remove(player.getUniqueId()), 2L);
        }
    }

    private void applyOneUpgrade(Player player, DungeonUpgradeGUI.UpgradeType type) {
        UUID id = player.getUniqueId();
        switch (type) {
            case ARMOR -> {
                int maxProt = Math.max(1, settings.getInt("max-protection", 2));
                int next = Math.min(maxProt, armorProt.getOrDefault(id, 0) + 1);
                armorProt.put(id, next);
                PlayerInventory inv = player.getInventory();
                for (ItemStack piece : new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()}) {
                    if (piece != null && piece.getType() != Material.AIR) {
                        piece.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, next);
                    }
                }
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Zbroja: Protection &f" + next));
            }
            case SWORD -> {
                int next = swordSharp.getOrDefault(id, 0) + 1;
                swordSharp.put(id, next);
                for (ItemStack stack : player.getInventory().getContents()) {
                    if (stack != null && stack.getType() == Material.DIAMOND_SWORD) {
                        stack.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, next);
                    }
                }
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Miecz: Sharpness &f" + next));
            }
            case APPLES -> {
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 6));
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76+6 golden apple"));
            }
            case SPEED -> {
                applyTimedBuff(player, PotionEffectType.SPEED, 0);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Speed I &f(2 min)"));
            }
            case STRENGTH -> {
                applyTimedBuff(player, PotionEffectType.INCREASE_DAMAGE, 0);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Strength I &f(2 min)"));
            }
            case RESIST -> {
                applyTimedBuff(player, PotionEffectType.DAMAGE_RESISTANCE, 0);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Resistance I &f(2 min)"));
            }
            case HASTE -> {
                applyTimedBuff(player, PotionEffectType.FAST_DIGGING, 1);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Haste II &f(2 min)"));
            }
            case JUMP -> {
                applyTimedBuff(player, PotionEffectType.JUMP, 1);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Jump Boost II &f(2 min)"));
            }
            case LIFESTEAL -> {
                activeBuffUpgrades.computeIfAbsent(id, k -> new HashSet<>()).add(type);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Aktywowano: " + type.shortLabel()));
            }
            case MAX_HP -> {
                bonusMaxHp.merge(id, 2.0D, Double::sum);
                applyBonusMaxHp(player);
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2.0D));
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76+1 serce max HP"));
            }
            case DASH -> {
                dashCdReduce.merge(id, 2, Integer::sum);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Dash cooldown -2s"));
            }
            case HEAL -> {
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.setSaturation(20.0F);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Pelne leczenie"));
            }
            case STEAK -> {
                player.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 24));
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76+24 stekow"));
            }
            case ABSORB -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 90, 1, false, true, true));
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Absorption II (90s)"));
            }
        }
    }

    /** @deprecated uzywaj pickUpgrade — zostawione dla kompatybilnosci klikow */
    public void applyUpgrade(Player player, DungeonUpgradeGUI.UpgradeType choice) {
        pickUpgrade(player, choice);
    }

    private void scheduleRefreshWaitGuis() {
        if (refreshWaitScheduled) {
            return;
        }
        refreshWaitScheduled = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshWaitScheduled = false;
            if (phase != Phase.UPGRADE) {
                return;
            }
            refreshWaitGuis();
        });
    }

    private void refreshWaitGuis() {
        for (UUID uuid : players) {
            if (pendingUpgrades.contains(uuid) || deadSpectators.contains(uuid)) {
                continue;
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                continue;
            }
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof DungeonUpgradeWaitGUI wait) {
                wait.render();
            }
        }
    }

    private void finishUpgrades() {
        cancelShopTimers();
        pendingUpgrades.clear();
        upgradeFreezeLoc.clear();
        upgradeChoices.clear();
        upgradeChoiceLabels.clear();
        offeredUpgrades.clear();
        pendingUpgradePicks.clear();
        movementLocked = false;
        if (shouldOpenCoinShop()) {
            broadcastRaw("<gradient:#00FF5A:#9FFF00>Ulepszenia wybrane</gradient> &8— &fshop coinow &e(co 4 fale, 15s)</shadow>");
            openCoinShop();
        } else {
            continueAfterShops(false);
        }
    }

    private boolean shouldOpenCoinShop() {
        int every = Math.max(1, settings.getInt("coin-shop-every-waves", 4));
        return wavesCleared > 0 && wavesCleared % every == 0;
    }

    private void openCoinShop() {
        pendingCoinShop.clear();
        cancelShopTimers();
        phase = Phase.COIN_SHOP;
        for (UUID uuid : players) {
            if (deadSpectators.contains(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            pendingCoinShop.add(uuid);
            sendDungeonTitle(player,
                "<gradient:#FFD900:#FF9B00><bold>SHOP COINOW</bold></gradient></shadow>",
                "Kup buffy za coiny &8— &fmasz &e15s &falbo &epomin</shadow>",
                8, 40, 10);
            new DungeonCoinShopGUI(plugin, this, player).open();
        }
        if (pendingCoinShop.isEmpty()) {
            finishCoinShops();
            return;
        }
        int timeout = Math.max(60, settings.getInt("coin-shop-timeout-ticks", 300));
        startShopCountdown(timeout, "shop");
        coinShopTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::timeoutCoinShops, timeout);
    }

    private void timeoutCoinShops() {
        if (phase != Phase.COIN_SHOP) {
            return;
        }
        for (UUID uuid : new HashSet<>(pendingCoinShop)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#FFAA00⚠ &8Ξ &eCzas sklepu minal — lecimy dalej."));
                finishCoinShop(player);
            } else {
                pendingCoinShop.remove(uuid);
            }
        }
        if (phase == Phase.COIN_SHOP && pendingCoinShop.isEmpty()) {
            finishCoinShops();
        }
    }

    private void startShopCountdown(int totalTicks, String kind) {
        if (shopCountdownTask != null) {
            shopCountdownTask.cancel();
            shopCountdownTask = null;
        }
        final int[] left = {Math.max(1, totalTicks / 20)};
        shopCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if ((kind.equals("upgrade") && phase != Phase.UPGRADE)
                || (kind.equals("shop") && phase != Phase.COIN_SHOP)) {
                if (shopCountdownTask != null) {
                    shopCountdownTask.cancel();
                    shopCountdownTask = null;
                }
                return;
            }
            String label = kind.equals("upgrade") ? "Upgrade" : "Shop";
            for (UUID uuid : players) {
                if (deadSpectators.contains(uuid)) {
                    continue;
                }
                if (kind.equals("upgrade") && !pendingUpgrades.contains(uuid)) {
                    continue;
                }
                if (kind.equals("shop") && !pendingCoinShop.contains(uuid)) {
                    continue;
                }
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                MultiActionBarService.get().send(player, "dungeon",
                    "" + label + ": &e" + left[0] + "s &8— &fwybierz!</shadow>", 25L);
            }
            left[0]--;
            if (left[0] < 0 && shopCountdownTask != null) {
                shopCountdownTask.cancel();
                shopCountdownTask = null;
            }
        }, 0L, 20L);
    }

    private void cancelShopTimers() {
        if (upgradeTimeoutTask != null) {
            upgradeTimeoutTask.cancel();
            upgradeTimeoutTask = null;
        }
        if (coinShopTimeoutTask != null) {
            coinShopTimeoutTask.cancel();
            coinShopTimeoutTask = null;
        }
        if (shopCountdownTask != null) {
            shopCountdownTask.cancel();
            shopCountdownTask = null;
        }
    }

    public void buyCoinShopOffer(Player player, DungeonCoinShopGUI.Offer offer) {
        if (phase != Phase.COIN_SHOP || !pendingCoinShop.contains(player.getUniqueId()) || offer == null) {
            return;
        }
        if (!coinSystem.trySpend(player.getUniqueId(), offer.cost())) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cZa malo coinow."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1.0F);
            return;
        }
        switch (offer) {
            case HEAL -> {
                double next = Math.min(player.getMaxHealth(), player.getHealth() + 8.0D);
                player.setHealth(next);
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76+8 HP"));
            }
            case ABSORB -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 45, 0, false, true, true));
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Absorption I (45s)"));
            }
            case SPEED -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 40, 1, false, true, true));
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Speed II (40s)"));
            }
            case STRENGTH -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * 30, 0, false, true, true));
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Strength I (30s)"));
            }
            case DASH -> {
                dashCooldownUntil.remove(player.getUniqueId());
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Dash gotowy"));
            }
            case GAPPLES -> {
                player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 3));
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76+3 gapples"));
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.35F);
    }

    public void finishCoinShop(Player player) {
        if (phase != Phase.COIN_SHOP) {
            return;
        }
        if (!pendingCoinShop.remove(player.getUniqueId())) {
            return;
        }
        suppressUpgradeReopen.add(player.getUniqueId());
        player.closeInventory();
        if (pendingCoinShop.isEmpty()) {
            for (UUID uuid : players) {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    online.closeInventory();
                }
            }
            finishCoinShops();
            Bukkit.getScheduler().runTaskLater(plugin, suppressUpgradeReopen::clear, 2L);
        } else {
            MultiActionBarService.get().send(player, "dungeon",
                "Czekaj na druzyne (shop): <gradient:#FFD900:#FF9B00>" + pendingCoinShop.size()
                    + "</gradient></shadow>", 40L);
            Bukkit.getScheduler().runTaskLater(plugin,
                () -> suppressUpgradeReopen.remove(player.getUniqueId()), 2L);
        }
    }

    private void finishCoinShops() {
        continueAfterShops(true);
    }

    private void continueAfterShops(boolean fromCoinShop) {
        cancelShopTimers();
        pendingCoinShop.clear();
        movementLocked = false;
        phase = Phase.WAVE;
        Runnable next = afterUpgradeContinue;
        afterUpgradeContinue = null;
        if (fromCoinShop) {
            broadcastRaw("<gradient:#FFD900:#FF9B00>Shop zamkniety</gradient> &8— &fruszamy dalej!</shadow>");
        }
        if (next != null) {
            Bukkit.getScheduler().runTaskLater(plugin, next, 20L);
        }
    }

    /** Q z katana: party ping. Item z drop-eventu (main-hand bywa pusty). */
    public void tryPartyPing(Player player) {
        tryPartyPing(player, player != null ? player.getInventory().getItemInMainHand() : null);
    }

    public void tryPartyPing(Player player, ItemStack katana) {
        if (player == null || !players.contains(player.getUniqueId())) {
            return;
        }
        if (phase != Phase.WAVE && phase != Phase.BOSS) {
            return;
        }
        if (deadSpectators.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!isKatana(katana) && !isKatana(player.getInventory().getItemInMainHand())) {
            return;
        }
        long now = System.currentTimeMillis();
        long ready = pingCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            long left = Math.max(1L, (ready - now + 999L) / 1000L);
            MultiActionBarService.get().send(player, "dungeon-ping",
                "Ej, spokojniej! Zaczekaj jeszcze <gradient:#FFD900:#FF9B00>"
                    + left + "s</gradient></shadow>", 30L);
            return;
        }

        java.util.ArrayDeque<Long> recent = pingRecent.computeIfAbsent(
            player.getUniqueId(), k -> new java.util.ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst() > 1000L) {
            recent.pollFirst();
        }
        recent.addLast(now);
        if (recent.size() >= 5) {
            pingCooldownUntil.put(player.getUniqueId(), now + 3000L);
            recent.clear();
            MultiActionBarService.get().send(player, "dungeon-ping",
                "Za duzo pingow naraz! Odczekaj &e3 sekundy&f.</shadow>", 40L);
        }

        Location mark = resolvePingLocation(player);
        clearActivePing(player.getUniqueId());
        spawnPingMarker(player, mark);
        for (UUID uuid : players) {
            Player mate = Bukkit.getPlayer(uuid);
            if (mate == null) {
                continue;
            }
            mate.playSound(mate.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.85F, 1.55F);
            mate.playSound(mate.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.35F, 1.8F);
            MultiActionBarService.get().send(mate, "dungeon-ping",
                "<gradient:#7CFFCB:#00C1FF>" + player.getName()
                    + "</gradient> &fzaznaczyl miejsce</shadow>", 40L);
        }
    }

    public static boolean isKatana(ItemStack stack) {
        if (stack == null || stack.getType() != Material.DIAMOND_SWORD) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() >= 1;
    }

    private Location resolvePingLocation(Player player) {
        org.bukkit.block.Block target = player.getTargetBlockExact(48);
        if (target != null && target.getType() != Material.AIR) {
            return target.getLocation().add(0.5D, 1.45D, 0.5D);
        }
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize().multiply(10.0D);
        return eye.clone().add(dir);
    }

    private void clearActivePing(UUID ownerId) {
        BukkitTask oldTask = pingExpireTasks.remove(ownerId);
        if (oldTask != null) {
            oldTask.cancel();
        }
        List<org.bukkit.entity.Entity> old = activePings.remove(ownerId);
        if (old == null) {
            return;
        }
        for (org.bukkit.entity.Entity entity : old) {
            if (entity != null && entity.isValid()) {
                try {
                    entity.remove();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void spawnPingMarker(Player source, Location mark) {
        List<org.bukkit.entity.Entity> spawned = new ArrayList<>();
        // Czysty marker: duza strzalka + nick (przez sciany).
        org.bukkit.entity.TextDisplay arrow = world.spawn(mark.clone().add(0, 0.85D, 0),
            org.bukkit.entity.TextDisplay.class, d -> {
                d.text(ColorUtils.colorizeComponent("<gradient:#7CFFCB:#00C1FF><bold>▼</bold></gradient>")
                    .decoration(TextDecoration.ITALIC, false));
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setDefaultBackground(false);
                d.setShadowed(false);
                d.setViewRange(128.0F);
                d.setPersistent(false);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, 0, 0),
                    new org.joml.AxisAngle4f(0, 0, 1, 0),
                    new org.joml.Vector3f(2.4F, 2.4F, 2.4F),
                    new org.joml.AxisAngle4f(0, 0, 1, 0)
                ));
                d.addScoreboardTag("holly_dungeon_ping");
            });
        org.bukkit.entity.TextDisplay label = world.spawn(mark.clone().add(0, 0.35D, 0),
            org.bukkit.entity.TextDisplay.class, d -> {
                d.text(ColorUtils.colorizeComponent("&f" + source.getName())
                    .decoration(TextDecoration.ITALIC, false));
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setDefaultBackground(false);
                d.setShadowed(true);
                d.setViewRange(128.0F);
                d.setPersistent(false);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.addScoreboardTag("holly_dungeon_ping");
            });
        // Delikatny "beacon" — item display jako punkt.
        org.bukkit.entity.ItemDisplay tip = world.spawn(mark.clone().add(0, 0.05D, 0),
            org.bukkit.entity.ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.SEA_LANTERN));
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setPersistent(false);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0, 0, 0),
                    new org.joml.AxisAngle4f(0, 0, 1, 0),
                    new org.joml.Vector3f(0.35F, 0.35F, 0.35F),
                    new org.joml.AxisAngle4f(0, 0, 1, 0)
                ));
                d.setGlowing(true);
                d.addScoreboardTag("holly_dungeon_ping");
            });
        spawned.add(arrow);
        spawned.add(label);
        spawned.add(tip);
        activePings.put(source.getUniqueId(), spawned);

        // Kolumna czastek w gore (czytelna przez sciany jako glow tip + FX lokalnie).
        for (int i = 0; i < 10; i++) {
            Location p = mark.clone().add(0, i * 0.35D, 0);
            world.spawnParticle(Particle.END_ROD, p, 1, 0, 0, 0, 0);
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, mark.clone().add(0, 0.2D, 0), 12, 0.25, 0.15, 0.25, 0.01);

        BukkitTask expire = Bukkit.getScheduler().runTaskLater(plugin, () -> clearActivePing(source.getUniqueId()), 100L);
        pingExpireTasks.put(source.getUniqueId(), expire);
    }

    private void clearMobs() {
        hazardSystem.clear();
        for (UUID id : new HashSet<>(aliveMobs)) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        aliveMobs.clear();
        focusedMobs.clear();
        idleMobs.clear();
        mobNames.clear();
        lastMobNameHp.clear();
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity.getScoreboardTags().contains("holly_dungeon_mob")
                || entity.getScoreboardTags().contains("holly_dungeon_hazard")
                || entity.getScoreboardTags().contains("holly_dungeon_preview")) {
                entity.remove();
            }
        }
        bossId = null;
    }

    public void shutdown() {
        softStopTasks(true);
        clearDeathMarkers();
        clearMobs();
        for (UUID uuid : new HashSet<>(activePings.keySet())) {
            clearActivePing(uuid);
        }
        coinSystem.clear();
        phase = Phase.ENDED;
    }

    /**
     * Restart serwera: zamroz sesje, zostaw postep + swiat — NIE ENDED / NIE kasuj danych.
     */
    public void prepareForServerRestart() {
        if (phase == Phase.ENDED) {
            return;
        }
        if (phase != Phase.FROZEN_REJOIN) {
            phaseBeforeFreeze = phase;
        }
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                try {
                    logoutLocations.put(uuid, player.getLocation().clone());
                } catch (Throwable ignored) {
                    logoutLocations.put(uuid, getRespawnLocation());
                }
            }
        }
        softStopTasks(false);
        clearDeathMarkers();
        clearMobs();
        for (UUID uuid : new HashSet<>(activePings.keySet())) {
            clearActivePing(uuid);
        }
        wipeReviveOpen = false;
        wipeReviveCharging = false;
        bossRewardAnimating = false;
        adminSkipBusy = false;
        phase = Phase.FROZEN_REJOIN;
        movementLocked = true;
        bossId = null;
    }

    private void softStopTasks(boolean clearHud) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (abilityTask != null) {
            abilityTask.cancel();
            abilityTask = null;
        }
        if (tutorialSlideTask != null) {
            tutorialSlideTask.cancel();
            tutorialSlideTask = null;
        }
        if (wipeReviveTask != null) {
            wipeReviveTask.cancel();
            wipeReviveTask = null;
        }
        cancelShopTimers();
        cancelCinematic();
        cancelProgressTask();
        stopMusicLoop();
        stopAmbientLoop();
        if (clearHud) {
            clearHudBars();
            clearDungeonScoreboards();
        } else {
            clearHudBars();
            clearDungeonScoreboards();
        }
    }

    /** Po restarcie: tick + okno rejoin, gracze wroca tryRejoin. */
    public void startAfterServerRestart() {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.FROZEN_REJOIN;
        movementLocked = true;
        if (phaseBeforeFreeze == null || phaseBeforeFreeze == Phase.ENDED || phaseBeforeFreeze == Phase.FROZEN_REJOIN) {
            phaseBeforeFreeze = Phase.WAVE;
        }
        // BOSS_REWARD / UPGRADE / COIN_SHOP po restarcie = bezpiecznie fala / boss.
        if (phaseBeforeFreeze == Phase.BOSS_REWARD || phaseBeforeFreeze == Phase.UPGRADE
            || phaseBeforeFreeze == Phase.COIN_SHOP || phaseBeforeFreeze == Phase.PREPARE) {
            phaseBeforeFreeze = Phase.WAVE;
        }
        clearMobs();
        for (UUID uuid : players) {
            manager.bindPlayer(uuid, this);
            manager.markRejoin(uuid, this);
        }
        if (tickTask == null) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 1L);
        }
        if (abilityTask == null) {
            abilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAbilities, 80L, 80L);
        }
        int rejoinSec = Math.max(60, settings.getInt("rejoin-seconds", 600));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase == Phase.FROZEN_REJOIN && !hasOnlineParticipants()) {
                broadcastRaw(settings.getString("messages.quit-lost",
                    "&#FF0000✘ &8Ξ &cNie wrociles na czas — tickety przepadly."));
                manager.endSession(this, false);
            }
        }, rejoinSec * 20L);
        plugin.getLogger().info("[Dungeon] Przywrocono sesje " + world.getName()
            + " lvl=" + level + " wave=" + wave + " resume=" + phaseBeforeFreeze
            + " players=" + players.size());
    }

    public org.bukkit.configuration.file.YamlConfiguration toRestartSnapshot() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("world", world != null ? world.getName() : null);
        yaml.set("phase-before", phaseBeforeFreeze != null ? phaseBeforeFreeze.name() : Phase.WAVE.name());
        yaml.set("level", level);
        yaml.set("wave", Math.max(1, wave));
        yaml.set("waves-cleared", wavesCleared);
        yaml.set("ticket-cost", party != null ? party.getTicketCost() : ticketMultiplier);
        yaml.set("party-code", party != null ? party.getCode() : "RST");
        yaml.set("leader-id", party != null ? party.getLeaderId().toString() : null);
        yaml.set("leader-name", party != null ? party.getLeaderName() : "unknown");
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("coin-multiplier", coinMultiplier);
        List<String> playerIds = new ArrayList<>();
        List<String> playerNames = new ArrayList<>();
        for (UUID uuid : players) {
            playerIds.add(uuid.toString());
            String name = "player";
            if (party != null && party.getMembers().get(uuid) != null) {
                name = party.getMembers().get(uuid).getName();
            } else {
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    name = online.getName();
                }
            }
            playerNames.add(name);
        }
        yaml.set("players", playerIds);
        yaml.set("player-names", playerNames);
        yaml.set("dead-spectators", deadSpectators.stream().map(UUID::toString).collect(java.util.stream.Collectors.toList()));
        writeUuidIntMap(yaml, "armor-prot", armorProt);
        writeUuidIntMap(yaml, "sword-sharp", swordSharp);
        writeUuidIntMap(yaml, "dash-cd", dashCdReduce);
        writeUuidIntMap(yaml, "banked-tickets", bankedTickets);
        writeUuidIntMap(yaml, "kill-counts", killCounts);
        writeUuidIntMap(yaml, "revive-counts", reviveCounts);
        writeUuidDoubleMap(yaml, "bonus-max-hp", bonusMaxHp);
        writeUuidDoubleMap(yaml, "damage-dealt", damageDealt);
        writeUuidIntMap(yaml, "coins", coinSystem.getAllCollected());
        for (Map.Entry<UUID, Location> entry : logoutLocations.entrySet()) {
            Location loc = entry.getValue();
            if (loc == null) {
                continue;
            }
            String base = "logout." + entry.getKey();
            yaml.set(base + ".x", loc.getX());
            yaml.set(base + ".y", loc.getY());
            yaml.set(base + ".z", loc.getZ());
            yaml.set(base + ".yaw", loc.getYaw());
            yaml.set(base + ".pitch", loc.getPitch());
        }
        for (Map.Entry<UUID, Set<DungeonUpgradeGUI.UpgradeType>> entry : activeBuffUpgrades.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (DungeonUpgradeGUI.UpgradeType type : entry.getValue()) {
                names.add(type.name());
            }
            yaml.set("buffs." + entry.getKey(), names);
        }
        return yaml;
    }

    public void applyRestartSnapshot(org.bukkit.configuration.file.YamlConfiguration yaml) {
        if (yaml == null) {
            return;
        }
        try {
            phaseBeforeFreeze = Phase.valueOf(yaml.getString("phase-before", Phase.WAVE.name()));
        } catch (IllegalArgumentException ignored) {
            phaseBeforeFreeze = Phase.WAVE;
        }
        level = Math.max(1, yaml.getInt("level", 1));
        wave = Math.max(1, yaml.getInt("wave", 1));
        wavesCleared = Math.max(0, yaml.getInt("waves-cleared", 0));
        sessionStartedAt = yaml.getLong("session-started-at", System.currentTimeMillis());
        coinMultiplier = Math.max(1, yaml.getInt("coin-multiplier", 1));
        deadSpectators.clear();
        for (String raw : yaml.getStringList("dead-spectators")) {
            try {
                deadSpectators.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        readUuidIntMap(yaml, "armor-prot", armorProt);
        readUuidIntMap(yaml, "sword-sharp", swordSharp);
        readUuidIntMap(yaml, "dash-cd", dashCdReduce);
        readUuidIntMap(yaml, "banked-tickets", bankedTickets);
        readUuidIntMap(yaml, "kill-counts", killCounts);
        readUuidIntMap(yaml, "revive-counts", reviveCounts);
        readUuidDoubleMap(yaml, "bonus-max-hp", bonusMaxHp);
        readUuidDoubleMap(yaml, "damage-dealt", damageDealt);
        Map<UUID, Integer> coins = new HashMap<>();
        readUuidIntMap(yaml, "coins", coins);
        for (Map.Entry<UUID, Integer> entry : coins.entrySet()) {
            coinSystem.setCoins(entry.getKey(), entry.getValue());
        }
        if (yaml.isConfigurationSection("logout")) {
            for (String key : yaml.getConfigurationSection("logout").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double x = yaml.getDouble("logout." + key + ".x");
                    double y = yaml.getDouble("logout." + key + ".y");
                    double z = yaml.getDouble("logout." + key + ".z");
                    float yaw = (float) yaml.getDouble("logout." + key + ".yaw");
                    float pitch = (float) yaml.getDouble("logout." + key + ".pitch");
                    logoutLocations.put(uuid, new Location(world, x, y, z, yaw, pitch));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        activeBuffUpgrades.clear();
        if (yaml.isConfigurationSection("buffs")) {
            for (String key : yaml.getConfigurationSection("buffs").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    Set<DungeonUpgradeGUI.UpgradeType> set = new HashSet<>();
                    for (String name : yaml.getStringList("buffs." + key)) {
                        try {
                            set.add(DungeonUpgradeGUI.UpgradeType.valueOf(name));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (!set.isEmpty()) {
                        activeBuffUpgrades.put(uuid, set);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private static void writeUuidIntMap(org.bukkit.configuration.file.YamlConfiguration yaml, String path, Map<UUID, Integer> map) {
        for (Map.Entry<UUID, Integer> entry : map.entrySet()) {
            yaml.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    private static void writeUuidDoubleMap(org.bukkit.configuration.file.YamlConfiguration yaml, String path, Map<UUID, Double> map) {
        for (Map.Entry<UUID, Double> entry : map.entrySet()) {
            yaml.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    private static void readUuidIntMap(org.bukkit.configuration.file.YamlConfiguration yaml, String path, Map<UUID, Integer> target) {
        target.clear();
        if (!yaml.isConfigurationSection(path)) {
            return;
        }
        for (String key : yaml.getConfigurationSection(path).getKeys(false)) {
            try {
                target.put(UUID.fromString(key), yaml.getInt(path + "." + key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static void readUuidDoubleMap(org.bukkit.configuration.file.YamlConfiguration yaml, String path, Map<UUID, Double> target) {
        target.clear();
        if (!yaml.isConfigurationSection(path)) {
            return;
        }
        for (String key : yaml.getConfigurationSection(path).getKeys(false)) {
            try {
                target.put(UUID.fromString(key), yaml.getDouble(path + "." + key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /** Admin: natychmiast konczy sesje (odmraza zablokowanych graczy). */
    public void adminForceEnd() {
        if (phase == Phase.ENDED) {
            manager.endSession(this, false);
            return;
        }
        wipeReviveOpen = false;
        wipeReviveCharging = false;
        if (wipeReviveTask != null) {
            wipeReviveTask.cancel();
            wipeReviveTask = null;
        }
        closeWipeReviveGuis();
        clearAllWipeReviveViewerStates();
        cancelCinematic();
        cancelProgressTask();
        broadcastRaw("&#FFD900⚠ &8Ξ &eAdmin zakonczyl sesje dungeona.");
        phase = Phase.ENDED;
        stopMusicLoop();
        stopAmbientLoop();
        clearMobs();
        coinSystem.clear();
        clearHudBars();
        clearDungeonScoreboards();
        Bukkit.getScheduler().runTask(plugin, () -> manager.endSession(this, false));
    }

    private void broadcast(String path) {
        String message = settings.getString(path);
        if (message != null && !message.isBlank()) {
            broadcastRaw(message);
        }
    }

    public Location getRespawnLocation() {
        Location spawn = locations.getPlayerSpawn(world);
        return spawn != null ? spawn : world.getSpawnLocation();
    }

    private void broadcastRaw(String message) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(ColorUtils.colorizeComponent(message));
            }
        }
    }

    private Location jitter(Location loc) {
        double ox = ThreadLocalRandom.current().nextDouble(-1.4D, 1.4D);
        double oz = ThreadLocalRandom.current().nextDouble(-1.4D, 1.4D);
        return loc.clone().add(ox, 0, oz);
    }

    private Location spread(Location loc, int index, int total) {
        double angle = (Math.PI * 2.0D * index) / Math.max(1, total);
        double radius = 1.6D + (index % 3) * 0.85D;
        Location out = loc.clone().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
        out.setYaw(loc.getYaw());
        out.setPitch(loc.getPitch());
        return out;
    }

    private Location findSafeSpawn(Location base) {
        if (base == null || base.getWorld() == null) {
            return base;
        }
        Location best = base.clone();
        if (isSafeStand(best)) {
            return best.add(0.5D, 0.0D, 0.5D);
        }
        for (int y = 0; y <= 3; y++) {
            for (int r = 0; r <= 4; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        Location tryLoc = base.clone().add(dx, y, dz);
                        if (isSafeStand(tryLoc)) {
                            return tryLoc.add(0.5D, 0.0D, 0.5D);
                        }
                        Location down = base.clone().add(dx, -y, dz);
                        if (y > 0 && isSafeStand(down)) {
                            return down.add(0.5D, 0.0D, 0.5D);
                        }
                    }
                }
            }
        }
        Location fallback = base.clone();
        fallback.setY(base.getWorld().getHighestBlockYAt(base) + 1.0D);
        return fallback;
    }

    private boolean isSafeStand(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        org.bukkit.block.Block feet = loc.getBlock();
        org.bukkit.block.Block head = loc.clone().add(0, 1, 0).getBlock();
        org.bukkit.block.Block below = loc.clone().add(0, -1, 0).getBlock();
        if (!feet.getType().isAir() && feet.getType() != Material.LIGHT) {
            return false;
        }
        if (!head.getType().isAir() && head.getType() != Material.LIGHT) {
            return false;
        }
        if (feet.isLiquid() || head.isLiquid()) {
            return false;
        }
        return below.getType().isSolid() && !below.isLiquid();
    }

    private Location lerpLook(Location from, Location to, double p, Location lookAt) {
        double x = from.getX() + (to.getX() - from.getX()) * p;
        double y = from.getY() + (to.getY() - from.getY()) * p;
        double z = from.getZ() + (to.getZ() - from.getZ()) * p;
        Location out = new Location(from.getWorld(), x, y, z);
        Vector dir = lookAt.toVector().subtract(out.toVector());
        out.setDirection(dir);
        return out;
    }

    private String nextName() {
        if (namePool == null || namePool.isEmpty()) {
            return "Ninja";
        }
        String name = namePool.get(nameCursor % namePool.size());
        nameCursor++;
        return name;
    }

    private String strip(String input) {
        return input == null ? "" : input.replaceAll("&[0-9A-FK-OR]", "").replaceAll("&#[0-9A-Fa-f]{6}", "");
    }
}
