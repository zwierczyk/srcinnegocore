package dev.grzybalson.modules.patrons;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.modules.cases.GuiScrollAnimator;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.HeadUtils;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class PatronManager implements Listener {

    private final HollyCore plugin;
    private final File dataFile;
    private final File configFile;
    private final MongoCollection<Document> collection;
    private final Map<UUID, String> patrons = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextFreeRoll = new ConcurrentHashMap<>();
    private final Map<UUID, Long> patronExpires = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAbsorption = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fireworkCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cobwebHitCount = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> applyingLightningDamage = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final Map<UUID, Integer> cobwebHitTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> helmetHitCount = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> helmetHitTarget = new ConcurrentHashMap<>();
    private final Map<String, PatronDefinition> settings = new LinkedHashMap<>();
    private final Map<String, String> messages = new HashMap<>();
    private final Random random = new Random();

    private static final int PROC_HITS_MIN = 30;
    private static final int PROC_HITS_MAX = 70;
    private BossBar godBar;
    private String godHour;
    private long godHourEnd;
    private org.bukkit.scheduler.BukkitTask tickTask;
    private org.bukkit.scheduler.BukkitTask godTask;
    private final Map<UUID, AtomicBoolean> animations = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingReward = new ConcurrentHashMap<>();

    private int guiSize;
    private boolean guiUseTemplate;
    private String guiTitle;
    private String rollGuiTitle;
    private int rollGuiRows;
    private boolean rollGuiUseTemplate;
    private int selectedSlot;
    private int rollSlot;
    private int rollCooldownDays;
    private int paidRollShardCost;
    private int patronDurationDays;
    private int godHourMinutes;
    private String mongoCollectionName;
    private Material rollItemMaterial;
    private String rollItemName;
    private List<String> rollItemLore;
    private String selectedName;
    private List<String> selectedFooter;
    private String freeReadyText;
    private List<String> shardPackages;
    private boolean godHourTitleEnabled;
    private String godHourBossBarTitle;
    private BarColor godHourBossBarColor;
    private BarStyle godHourBossBarStyle;
    private boolean godHourBossBarEnabled;
    private boolean godHourBossBarProgress;
    private List<BarFlag> godHourBossBarFlags;
    private String godHourTitle;
    private String godHourSubtitle;
    private int godHourTitleFadeIn;
    private int godHourTitleStay;
    private int godHourTitleFadeOut;
    private String rollBroadcastMessage;
    private int rollFinalSlot;
    private int[] rollDelayTicks;
    private List<Integer> rollSlots;
    private List<Decoration> rollDecorations;
    private List<Decoration> guiDecorations;
    private final Map<String, Integer> patronGuiSlots = new LinkedHashMap<>();
    private int infoSlot = -1;
    private Material infoMaterial = Material.BOOK;
    private String infoName = "&c&lINFORMACJE";
    private List<String> infoLore = List.of();
    private ConfiguredSound soundTick;
    private ConfiguredSound soundFinish;
    private ConfiguredSound soundFail;
    private ConfiguredSound soundOpen;
    private boolean winTitleEnabled;
    private String winTitle;
    private String winSubtitle;
    private int winTitleFadeIn;
    private int winTitleStay;
    private int winTitleFadeOut;

    public PatronManager(HollyCore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "addons/patroni/data.yml");
        this.configFile = new File(plugin.getDataFolder(), "addons/patroni/patroni.yml");
        ensureConfig();
        loadConfig();
        MongoCollection<Document> mongo = null;
        try {
            if (plugin.getMongoManager() != null) {
                mongo = plugin.getMongoManager().getCollection(mongoCollectionName);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie polaczono zapisu patronow z MongoDB: " + exception.getMessage());
        }
        this.collection = mongo;
        if (collection == null) {
            plugin.getLogger().warning("[Patroni] MongoDB niedostępne — dane patronów nie będą zapisywane.");
        } else {
            migrateLegacyYamlIfPresent();
            loadMongoData();
        }
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (collection != null) {
            saveAllPlayersMongo();
        }
        if (tickTask != null) tickTask.cancel();
        if (godTask != null) godTask.cancel();
        if (godBar != null) godBar.removeAll();
        animations.values().forEach(flag -> flag.set(true));
    }

    public void reload() {
        loadConfig();
        refreshActiveGodBossBar();
        Bukkit.getOnlinePlayers().forEach(this::refreshGodEffects);
    }

    public void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(), guiSize, color(guiTitle));
        if (guiUseTemplate) {
            if (plugin.getGuiFactory() != null) {
                plugin.getGuiFactory().applyDefaultTemplate(inv);
            } else {
                applyGuiTemplate(inv);
            }
        }
        applyDecorations(inv, guiDecorations);

        for (PatronDefinition patron : settings.values()) {
            Integer slot = patronGuiSlots.get(patron.key);
            if (slot == null || slot < 0 || slot >= inv.getSize()) {
                continue;
            }
            inv.setItem(slot, patronItem(patron));
        }

        Map<String, String> placeholders = rollPlaceholders(player);
        if (rollSlot >= 0 && rollSlot < inv.getSize()) {
            inv.setItem(rollSlot, item(
                rollItemMaterial,
                color(formatMessage(rollItemName, null, player.getName(), 0L, placeholders)),
                rollItemLore.stream()
                    .map(line -> color(formatMessage(line, null, player.getName(), 0L, placeholders)))
                    .toList()
            ));
        }
        ItemStack selected = selectedPatronItem(player);
        if (selected != null && selectedSlot >= 0 && selectedSlot < inv.getSize()) {
            inv.setItem(selectedSlot, selected);
        }
        if (infoSlot >= 0 && infoSlot < inv.getSize()) {
            inv.setItem(infoSlot, item(
                infoMaterial,
                color(formatMessage(infoName, null, player.getName(), 0L, placeholders)),
                infoLore.stream()
                    .map(line -> color(formatMessage(line, null, player.getName(), 0L, placeholders)))
                    .toList()
            ));
        }
        play(player, soundOpen);
        player.openInventory(inv);
    }

    public void startRandomGodHour() {
        PatronDefinition patron = randomPatron();
        if (patron != null) {
            startGodHour(patron, Duration.ofMinutes(godHourMinutes));
        }
    }

    public boolean startGodHour(String name) {
        PatronDefinition patron = find(name);
        if (patron == null) return false;
        startGodHour(patron, Duration.ofMinutes(godHourMinutes));
        return true;
    }

    private void startGodHour(PatronDefinition patron, Duration duration) {
        if (godTask != null) godTask.cancel();
        if (godBar != null) godBar.removeAll();
        godHour = patron.key;
        godHourEnd = System.currentTimeMillis() + duration.toMillis();
        long totalMillis = Math.max(1L, duration.toMillis());
        if (godHourTitleEnabled) {
            Bukkit.getOnlinePlayers().forEach(p -> ColorUtils.sendTitle(p,
                color(formatMessage(godHourTitle, patron, p.getName(), duration.toMillis())),
                color(formatMessage(godHourSubtitle, patron, p.getName(), duration.toMillis())),
                godHourTitleFadeIn,
                godHourTitleStay,
                godHourTitleFadeOut
            ));
        }
        if (godHourBossBarEnabled) {
            godBar = Bukkit.createBossBar(godBarTitle(patron, duration.toMillis()), godHourBossBarColor, godHourBossBarStyle, godHourBossBarFlags.toArray(new BarFlag[0]));
            godBar.setProgress(1.0);
            Bukkit.getOnlinePlayers().forEach(godBar::addPlayer);
        } else {
            godBar = null;
        }
        Bukkit.getOnlinePlayers().forEach(this::refreshGodEffects);
        godTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long left = godHourEnd - System.currentTimeMillis();
            if (left <= 0) {
                endGodHour();
                return;
            }
            if (godBar != null) {
                if (godHourBossBarProgress) {
                    godBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) totalMillis)));
                }
                godBar.setTitle(godBarTitle(patron, left));
            }
        }, 0L, 20L);
    }

    public void stopGodHour() {
        endGodHour();
    }

    private void endGodHour() {
        if (godTask != null) godTask.cancel();
        godTask = null;
        if (godBar != null) godBar.removeAll();
        godBar = null;
        String ended = godHour;
        godHour = null;
        godHourEnd = 0L;
        Bukkit.getOnlinePlayers().forEach(p -> clearGodEffects(p, ended));
    }

    private void refreshActiveGodBossBar() {
        String active = godHour;
        if (active == null) {
            if (godBar != null) {
                godBar.removeAll();
                godBar = null;
            }
            return;
        }
        PatronDefinition patron = settings.get(active);
        long left = godHourEnd - System.currentTimeMillis();
        if (patron == null || left <= 0) {
            endGodHour();
            return;
        }
        if (godBar != null) {
            godBar.removeAll();
            godBar = null;
        }
        if (!godHourBossBarEnabled) {
            return;
        }
        long totalMillis = Math.max(1L, Duration.ofMinutes(godHourMinutes).toMillis());
        godBar = Bukkit.createBossBar(godBarTitle(patron, left), godHourBossBarColor, godHourBossBarStyle, godHourBossBarFlags.toArray(new BarFlag[0]));
        godBar.setProgress(godHourBossBarProgress ? Math.max(0.0, Math.min(1.0, left / (double) totalMillis)) : 1.0);
        Bukkit.getOnlinePlayers().forEach(godBar::addPlayer);
    }

    private String godBarTitle(PatronDefinition patron, long leftMillis) {
        return color(formatMessage(godHourBossBarTitle, patron, null, leftMillis));
    }

    private String formatClock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds);
    }

    private String formatDurationShort(long millis) {
        long safe = Math.max(0L, millis);
        if (safe <= 0L) {
            return freeReadyText;
        }
        long totalSec = (safe + 999L) / 1000L;
        long days = totalSec / 86400L;
        long hours = (totalSec % 86400L) / 3600L;
        long minutes = (totalSec % 3600L) / 60L;
        if (days > 0L) {
            return days + "d " + hours + "h";
        }
        if (hours > 0L) {
            return hours + "h" + (minutes > 0L ? " " + minutes + "m" : "");
        }
        if (minutes > 0L) {
            return minutes + "m";
        }
        return totalSec + "s";
    }

    private String formatMessage(String message, PatronDefinition patron, String playerName, long millis) {
        return formatMessage(message, patron, playerName, millis, Map.of());
    }

    private String formatMessage(String message, PatronDefinition patron, String playerName, long millis, Map<String, String> placeholders) {
        if (message == null) return "";
        String patronDisplay = patron == null ? "" : patron.displayName;
        String patronPlain = patron == null ? "" : stripColors(patronDisplay);
        String patronKey = patron == null ? "" : patron.key;
        String formatted = message
            .replace("{PATRON}", patronDisplay)
            .replace("{PATRON_NAME}", patronPlain)
            .replace("{PATRON_KEY}", patronKey)
            .replace("{PLAYER}", playerName == null ? "" : playerName)
            .replace("{TIME}", formatClock(millis))
            .replace("{czas}", placeholders.getOrDefault("czas", formatDurationShort(millis)))
            .replace("{COST}", String.valueOf(paidRollShardCost));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return formatted;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof GuiHolder) && !(e.getView().getTopInventory().getHolder() instanceof RollHolder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory().getHolder() instanceof GuiHolder && e.getRawSlot() == rollSlot) {
            roll(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GuiHolder || e.getView().getTopInventory().getHolder() instanceof RollHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof RollHolder)) return;
        AtomicBoolean cancelled = animations.remove(p.getUniqueId());
        if (cancelled != null) cancelled.set(true);
        String patron = pendingReward.remove(p.getUniqueId());
        if (patron != null) {
            finishRoll(p, patron, true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        loadPlayerMongo(p.getUniqueId());
        if (godBar != null) godBar.addPlayer(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) refreshGodEffects(p);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        savePlayerData(p.getUniqueId());
        if (godBar != null) godBar.removePlayer(p);
        lastAbsorption.remove(p.getUniqueId());
        fireworkCooldownUntil.remove(p.getUniqueId());
        AtomicBoolean cancelled = animations.remove(p.getUniqueId());
        if (cancelled != null) cancelled.set(true);
        pendingReward.remove(p.getUniqueId());
    }

    private void roll(Player player) {
        if (animations.containsKey(player.getUniqueId())) return;
        List<PatronDefinition> pool = configuredPatrons();
        if (pool.isEmpty()) return;
        boolean free = canFreeRoll(player);
        if (!free && !takeShard(player, paidRollShardCost)) {
            play(player, soundFail);
            player.sendMessage(color(formatMessage(messages.get("not-enough-shards"), null, player.getName(), 0L, rollPlaceholders(player))));
            return;
        }
        if (free) {
            nextFreeRoll.put(player.getUniqueId(), System.currentTimeMillis() + Duration.ofDays(rollCooldownDays).toMillis());
            savePlayerData(player.getUniqueId());
        }
        PatronDefinition result = randomPatron();
        if (result == null) return;
        pendingReward.put(player.getUniqueId(), result.key);
        int size = Math.max(9, rollGuiRows * 9);
        Inventory inv = Bukkit.createInventory(new RollHolder(), size, color(rollGuiTitle));
        if (rollGuiUseTemplate) {
            if (plugin.getGuiFactory() != null) {
                plugin.getGuiFactory().applyDefaultTemplate(inv);
            } else {
                applyGuiTemplate(inv);
            }
        }
        applyRollDecorations(inv);
        player.openInventory(inv);

        int[] delays = rollDelayTicks.length == 0 ? new int[]{2, 2, 2, 2, 4, 6, 10} : rollDelayTicks.clone();
        int steps = delays.length;
        int winnerIdx = Math.max(0, steps - 1 + 4);
        int len = steps + 9;
        ItemStack[] strip = new ItemStack[len];
        ItemStack landed = patronItem(result);
        for (int i = 0; i < len; i++) {
            strip[i] = i == winnerIdx ? landed.clone() : patronItem(pool.get(random.nextInt(pool.size())));
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        animations.put(player.getUniqueId(), cancelled);
        GuiScrollAnimator.run(plugin, inv, strip, delays, rollGuiRows, cancelled, () -> {
            animations.remove(player.getUniqueId());
            String awarded = pendingReward.remove(player.getUniqueId());
            if (awarded != null) {
                finishRoll(player, awarded, true);
            }
        });
    }

    private void finishRoll(Player player, String patronKey, boolean reopen) {
        PatronDefinition patron = settings.get(patronKey);
        if (patron == null) return;
        patrons.put(player.getUniqueId(), patron.key);
        patronExpires.put(player.getUniqueId(), System.currentTimeMillis() + Duration.ofDays(patronDurationDays).toMillis());
        savePlayerData(player.getUniqueId());
        String broadcast = color(formatMessage(rollBroadcastMessage, patron, player.getName(), 0L));
        if (!broadcast.isBlank()) Bukkit.broadcastMessage(broadcast);
        play(player, soundFinish);
        if (winTitleEnabled) {
            ColorUtils.sendTitle(player,
                color(formatMessage(winTitle, patron, player.getName(), 0L)),
                color(formatMessage(winSubtitle, patron, player.getName(), 0L)),
                winTitleFadeIn, winTitleStay, winTitleFadeOut);
        }
        refreshGodEffects(player);
        if (reopen) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) openGui(player);
            });
        }
    }

    private Map<String, String> rollPlaceholders(Player player) {
        boolean free = player != null && canFreeRoll(player);
        long left = player == null ? 0L : Math.max(0L, nextFreeRoll.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis());
        return Map.of(
            "COST", String.valueOf(paidRollShardCost),
            "COOLDOWN_DAYS", String.valueOf(rollCooldownDays),
            "FREE_DAYS", String.valueOf(rollCooldownDays),
            "DURATION", String.valueOf(patronDurationDays),
            "czas", free ? freeReadyText : formatDurationShort(left)
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (Boolean.TRUE.equals(applyingLightningDamage.get())) {
            return;
        }
        Player attacker = asPlayer(e.getDamager());
        if (attacker != null) {
            if (plugin.getCustomHitsService() == null) {
                double mul = getOutgoingDamageMultiplier(attacker);
                if (plugin.getSzamanManager() != null) {
                    mul = 1.0 + (mul - 1.0)
                        + plugin.getSzamanManager().getPerkValue(attacker.getUniqueId(), dev.grzybalson.modules.szaman.PerkType.STRENGTH) / 100.0;
                }
                if (mul != 1.0) e.setDamage(e.getDamage() * mul);
            }
            applyAttackEffects(attacker, e);
        }
        if (e.getEntity() instanceof Player victim) {
            applyDefendEffects(victim, attacker, e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (!hasPassive(player, def -> def.neverHungry)) return;
        if (e.getFoodLevel() < player.getFoodLevel()) {
            e.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        if (item.getType() != Material.GOLDEN_APPLE && item.getType() != Material.ENCHANTED_GOLDEN_APPLE) return;
        PatronDefinition god = godHourPatron();
        if (god == null || god.goldenAppleExtraRegenTicks <= 0) return;
        int extra = god.goldenAppleExtraRegenTicks;
        int amplifier = Math.max(0, god.goldenAppleRegenAmplifier);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (e.getPlayer().isOnline()) {
                e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, extra, amplifier, true, true, true));
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.COBWEB) return;
        PatronDefinition god = godHourPatron();
        if (god != null && god.disableCobweb) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFirework(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND && e.getHand() != EquipmentSlot.OFF_HAND) return;
        if (e.getItem() == null || e.getItem().getType() != Material.FIREWORK_ROCKET) return;
        PatronDefinition god = godHourPatron();
        if (god == null || god.fireworkCooldownSeconds <= 0) return;
        long now = System.currentTimeMillis();
        Long until = fireworkCooldownUntil.get(e.getPlayer().getUniqueId());
        if (until != null && until > now) {
            e.setCancelled(true);
            return;
        }
        fireworkCooldownUntil.put(e.getPlayer().getUniqueId(), now + god.fireworkCooldownSeconds * 1000L);
    }

    private void applyAttackEffects(Player attacker, EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        PatronDefinition owned = ownedPatron(attacker);
        PatronDefinition god = godHourPatron();

        if (owned != null && chance(owned.igniteChance)) {
            victim.setFireTicks(Math.max(victim.getFireTicks(), owned.igniteTicks));
        }
        if (owned != null && owned.cobwebChance > 0.0 && (!isCobwebDisabled())
            && hitIntervalProc(attacker.getUniqueId(), cobwebHitCount, cobwebHitTarget)) {
            Block block = victim.getLocation().getBlock();
            if (block.getType().isAir()) block.setType(Material.COBWEB);
        }
        if (owned != null && owned.dropHelmetChance > 0.0
            && victim instanceof Player target) {
            ItemStack helmet = target.getInventory().getHelmet();
            // Bez wolnego slotu w EQ patron nie działa — hełm zostaje, nic nie leci na ziemię.
            if (helmet != null && !helmet.getType().isAir()
                && target.getInventory().firstEmpty() >= 0
                && hitIntervalProc(attacker.getUniqueId(), helmetHitCount, helmetHitTarget)) {
                ItemStack removed = helmet.clone();
                target.getInventory().setHelmet(null);
                target.getInventory().addItem(removed);
            }
        }
        if (owned != null && chance(owned.lightningChance)) {
            if (owned.lightningReal) {
                victim.getWorld().strikeLightning(victim.getLocation());
            } else {
                victim.getWorld().strikeLightningEffect(victim.getLocation());
            }
            // 1 serce = 2 HP; np. lightning-hearts: 2.5 → 5.0 obrażeń.
            if (owned.lightningDamage > 0.0 && !victim.isDead()) {
                applyingLightningDamage.set(Boolean.TRUE);
                try {
                    victim.damage(owned.lightningDamage, attacker);
                } finally {
                    applyingLightningDamage.set(Boolean.FALSE);
                }
            }
        }
        if (owned != null && chance(owned.healOnHitChance)) {
            AttributeInstance attr = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double max = attr == null ? 20.0 : attr.getValue();
            attacker.setHealth(Math.min(max, attacker.getHealth() + owned.healAmount));
        }
        if (god != null && chance(god.poisonChance)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, god.poisonDurationTicks, god.poisonAmplifier, true, true, true));
        }
        if (god != null && chance(god.levitationChance)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, god.levitationDurationTicks, god.levitationAmplifier, true, true, true));
        }
        if (god != null && chance(god.weakenChance)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, god.weakenDurationTicks, god.weakenAmplifier, true, true, true));
        }
    }

    private void applyDefendEffects(Player victim, Player attacker, EntityDamageByEntityEvent e) {
        PatronDefinition owned = ownedPatron(victim);
        PatronDefinition god = godHourPatron();
        if (owned != null && owned.damageTakenMultiplier > 0.0 && owned.damageTakenMultiplier != 1.0) {
            e.setDamage(e.getDamage() * owned.damageTakenMultiplier);
        }
        if (owned != null && chance(owned.blockHitChance)) {
            e.setCancelled(true);
            victim.playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
            if (attacker != null) attacker.playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
            return;
        }
        if (god != null && god.regenWhenHitTicks > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, god.regenWhenHitTicks, god.regenWhenHitAmplifier, true, true, true));
        }
    }

    private void tick() {
        PatronDefinition god = godHourPatron();
        if (god == null && patrons.isEmpty()) {
            return;
        }
        if (god != null) {
            // God hour: efekty globalne — ale bez dodawania do bossbara co sekundę.
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshGodEffects(player);
            }
            return;
        }
        // Tylko gracze z aktywnym patronem (neverHungry itd.).
        for (UUID uuid : patrons.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                refreshGodEffects(player);
            }
        }
    }

    private void refreshGodEffects(Player p) {
        expireIfNeeded(p.getUniqueId());
        PatronDefinition owned = ownedPatron(p);
        PatronDefinition god = godHourPatron();
        if (owned != null && owned.neverHungry) {
            p.setFoodLevel(20);
            p.setSaturation(Math.max(p.getSaturation(), 20f));
        }
        if (god != null && god.speedAmplifier >= 0) {
            applyEffectIfNeeded(p, PotionEffectType.SPEED, god.speedAmplifier);
        }
        if (god != null && god.strengthAmplifier >= 0) {
            applyEffectIfNeeded(p, PotionEffectType.INCREASE_DAMAGE, god.strengthAmplifier);
        }
        if (god != null && god.absorptionEverySeconds > 0) {
            long now = System.currentTimeMillis();
            long last = lastAbsorption.getOrDefault(p.getUniqueId(), 0L);
            if (now - last >= god.absorptionEverySeconds * 1000L) {
                lastAbsorption.put(p.getUniqueId(), now);
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, god.absorptionDurationTicks, god.absorptionAmplifier, true, true, true));
            }
        }
    }

    private void applyEffectIfNeeded(Player p, PotionEffectType type, int amplifier) {
        PotionEffect current = p.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier && current.getDuration() > 40) {
            return;
        }
        p.addPotionEffect(new PotionEffect(type, 220, amplifier, true, false, true));
    }

    private void clearGodEffects(Player p, String endedKey) {
        PatronDefinition ended = settings.get(endedKey);
        if (ended == null) return;
        if (ended.speedAmplifier >= 0 && (ownedPatron(p) == null || !ownedPatron(p).neverHungry)) {
            p.removePotionEffect(PotionEffectType.SPEED);
        }
        if (ended.strengthAmplifier >= 0) {
            p.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        }
        refreshGodEffects(p);
    }

    public double getAresDamageMultiplier(Player player) {
        return getOutgoingDamageMultiplier(player);
    }

    public double getOutgoingDamageMultiplier(Player player) {
        if (player == null) return 1.0;
        double bonus = 0.0;
        PatronDefinition owned = ownedPatron(player);
        if (owned != null && owned.damageMultiplier > 0.0 && owned.damageMultiplier != 1.0) {
            bonus += scaleOutgoingBonus(owned.damageMultiplier) - 1.0;
        }
        if (owned != null && owned.lowHealthDamageMultiplier > 0.0 && owned.lowHealthHearts > 0.0) {
            if (player.getHealth() <= owned.lowHealthHearts * 2.0) {
                bonus += scaleOutgoingBonus(owned.lowHealthDamageMultiplier) - 1.0;
            }
        }
        double multiplier = 1.0 + bonus;
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier) || multiplier <= 0.0) return 1.0;
        return multiplier;
    }

    /** Skaluje tylko część bonusową: 1.20 → 1.10, 1.04 → 1.02. */
    private static double scaleOutgoingBonus(double multiplier) {
        if (multiplier <= 1.0) {
            return multiplier;
        }
        return 1.0 + (multiplier - 1.0) * 0.5;
    }

    private boolean hasPassive(Player player, java.util.function.Predicate<PatronDefinition> test) {
        PatronDefinition owned = ownedPatron(player);
        return owned != null && test.test(owned);
    }

    private boolean isCobwebDisabled() {
        PatronDefinition god = godHourPatron();
        return god != null && god.disableCobweb;
    }

    private boolean chance(double chance) {
        return chance > 0.0 && random.nextDouble() < chance;
    }

    /** Proc co 30–70 hitów (losowy próg, potem reset). */
    private boolean hitIntervalProc(UUID uuid, Map<UUID, Integer> counts, Map<UUID, Integer> targets) {
        int target = targets.computeIfAbsent(uuid, ignored -> nextProcHits());
        int hits = counts.merge(uuid, 1, Integer::sum);
        if (hits < target) {
            return false;
        }
        counts.put(uuid, 0);
        targets.put(uuid, nextProcHits());
        return true;
    }

    private int nextProcHits() {
        return PROC_HITS_MIN + random.nextInt(PROC_HITS_MAX - PROC_HITS_MIN + 1);
    }

    private Player asPlayer(Entity e) {
        return e instanceof Player p ? p : null;
    }

    private boolean canFreeRoll(Player p) {
        return System.currentTimeMillis() >= nextFreeRoll.getOrDefault(p.getUniqueId(), 0L);
    }

    private PatronDefinition ownedPatron(Player player) {
        if (player == null) return null;
        expireIfNeeded(player.getUniqueId());
        String key = patrons.get(player.getUniqueId());
        return key == null ? null : settings.get(key);
    }

    private PatronDefinition godHourPatron() {
        if (godHour == null) return null;
        if (System.currentTimeMillis() >= godHourEnd) return null;
        return settings.get(godHour);
    }

    private void expireIfNeeded(UUID uuid) {
        Long expires = patronExpires.get(uuid);
        if (expires != null && expires > 0L && System.currentTimeMillis() >= expires) {
            patrons.remove(uuid);
            patronExpires.remove(uuid);
            savePlayerData(uuid);
        }
    }

    private boolean takeShard(Player p, int amount) {
        if (amount <= 0) return true;
        ItemStack sample = shardSample();
        int count = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isShard(it, sample)) count += it.getAmount();
        }
        if (count < amount) return false;
        int left = amount;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && left > 0 && isShard(it, sample)) {
                int take = Math.min(left, it.getAmount());
                it.setAmount(it.getAmount() - take);
                left -= take;
            }
        }
        return true;
    }

    private boolean isShard(ItemStack item, ItemStack sample) {
        if (item == null || item.getType().isAir()) return false;
        if (sample != null) return item.isSimilar(sample);
        return item.getType() == Material.PRISMARINE_SHARD || item.getType() == Material.AMETHYST_SHARD;
    }

    private ItemStack shardSample() {
        if (plugin.getPickupManager() != null) {
            for (String name : shardPackages) {
                ItemStack item = plugin.getPickupManager().getPackage(name);
                if (item != null && !item.getType().isAir()) return item;
            }
        }
        return null;
    }

    private PatronDefinition randomPatron() {
        List<PatronDefinition> pool = configuredPatrons();
        if (pool.isEmpty()) return null;
        int total = 0;
        for (PatronDefinition def : pool) total += Math.max(1, def.weight);
        int roll = random.nextInt(Math.max(1, total));
        int cursor = 0;
        for (PatronDefinition def : pool) {
            cursor += Math.max(1, def.weight);
            if (roll < cursor) return def;
        }
        return pool.get(0);
    }

    public String parse(String s) {
        PatronDefinition def = find(s);
        return def == null ? null : def.key;
    }

    private PatronDefinition find(String s) {
        if (s == null) return null;
        String normalized = stripColors(s).replace("§", "").replace("&", "").trim().toLowerCase(Locale.ROOT);
        PatronDefinition exact = settings.get(normalized);
        if (exact != null) return exact;
        for (PatronDefinition def : settings.values()) {
            if (def.key.equalsIgnoreCase(s) || stripColors(def.displayName).equalsIgnoreCase(normalized)) {
                return def;
            }
        }
        return null;
    }

    public List<String> patronKeys() {
        return configuredPatrons().stream().map(p -> p.key).collect(Collectors.toList());
    }

    public String getPlayerPatronDisplayName(UUID uuid) {
        if (uuid == null) return "";
        expireIfNeeded(uuid);
        String key = patrons.get(uuid);
        PatronDefinition def = key == null ? null : settings.get(key);
        return def == null ? "" : color(def.displayName);
    }

    public boolean setPlayerPatron(UUID uuid, String patronName) {
        if (uuid == null) return false;
        PatronDefinition patron = find(patronName);
        if (patron == null) return false;
        patrons.put(uuid, patron.key);
        patronExpires.put(uuid, System.currentTimeMillis() + Duration.ofDays(patronDurationDays).toMillis());
        savePlayerData(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) refreshGodEffects(player);
        return true;
    }

    public String getPatronDisplayName(String patronName) {
        PatronDefinition patron = find(patronName);
        return patron == null ? patronName : color(patron.displayName);
    }

    public String formatConfiguredMessage(String key, String playerName, String patronName) {
        PatronDefinition patron = find(patronName);
        return color(formatMessage(messages.getOrDefault(key, ""), patron, playerName, 0L));
    }

    public boolean setPatronHead(String patronName, ItemStack item) {
        PatronDefinition patron = find(patronName);
        if (patron == null || item == null || item.getType().isAir()) return false;
        String value = HeadUtils.getBase64FromHead(item);
        if (value == null || value.isBlank()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(configFile);
        y.set("patrons." + patron.key + ".head-value", value);
        try {
            y.save(configFile);
            loadConfig();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie zapisano glowki patrona w config.yml: " + exception.getMessage());
            return false;
        }
    }

    private ItemStack selectedPatronItem(Player player) {
        PatronDefinition owned = ownedPatron(player);
        if (owned == null) return null;
        long left = Math.max(0L, patronExpires.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis());
        Map<String, String> placeholders = Map.of("czas", formatDurationShort(left));
        ItemStack it = patronItem(owned);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(formatMessage(selectedName, owned, player.getName(), left, placeholders)));
            List<String> lore = new ArrayList<>();
            for (String line : owned.lore) {
                String formatted = formatMessage(line, owned, player.getName(), left, placeholders);
                if (ColorUtils.isBlankLoreLine(formatted)) {
                    continue;
                }
                lore.add(color(formatted));
            }
            for (String line : selectedFooter) {
                String formatted = formatMessage(line, owned, player.getName(), left, placeholders);
                if (ColorUtils.isBlankLoreLine(formatted)) {
                    continue;
                }
                lore.add(color(formatted));
            }
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack patronItem(PatronDefinition p) {
        ItemStack it = (p.headValue == null || p.headValue.isBlank())
            ? new ItemStack(Material.PLAYER_HEAD)
            : HeadUtils.createHead(p.headValue);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(p.displayName));
            List<String> lore = p.lore.stream()
                .filter(line -> !ColorUtils.isBlankLoreLine(line))
                .map(this::color)
                .toList();
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private boolean applyGuiTemplate(Inventory inv) {
        if (inv == null) return false;
        File templateFile = new File(plugin.getDataFolder(), "guis/template.yml");
        if (!templateFile.exists()) {
            try {
                plugin.saveResource("guis/template.yml", false);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        YamlConfiguration guiTemplate = YamlConfiguration.loadConfiguration(templateFile);
        ConfigurationSection section = guiTemplate.getConfigurationSection("template" + inv.getSize());
        if (section == null) return false;
        String itemName = guiTemplate.getString("backgroundItemName", "&c");
        boolean hideAttributes = guiTemplate.getBoolean("hideAttributes", true);
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) continue;
            ItemStack decoration = new ItemStack(material);
            ItemMeta meta = decoration.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(itemName));
                if (hideAttributes) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                decoration.setItemMeta(meta);
            }
            for (int slot : section.getIntegerList(materialName)) {
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, decoration.clone());
            }
        }
        return true;
    }

    private void applyRollDecorations(Inventory inv) {
        applyDecorations(inv, rollDecorations);
    }

    private void applyDecorations(Inventory inv, List<Decoration> decorations) {
        if (inv == null || decorations == null) {
            return;
        }
        for (Decoration decoration : decorations) {
            ItemStack stack = item(decoration.material, color(decoration.name), List.of());
            for (int slot : decoration.slots) {
                if (slot >= 0 && slot < inv.getSize()) {
                    inv.setItem(slot, stack.clone());
                }
            }
        }
    }

    private ItemStack item(Material m, String n, List<String> l) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(n);
            meta.setLore(l);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void ensureConfig() {
        HollyDataPaths.ensureResource(plugin, "addons/patroni/patroni.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
    }

    private void loadConfig() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(configFile);
        int rows = y.getInt("gui.rows", -1);
        if (rows > 0) {
            guiSize = Math.max(1, Math.min(6, rows)) * 9;
        } else {
            guiSize = y.getInt("gui.size", 45);
            if (guiSize < 9 || guiSize % 9 != 0) guiSize = 45;
        }
        guiUseTemplate = y.getBoolean("gui.use-template", true);
        guiTitle = y.getString("gui.title", "&8PATRONI");
        selectedSlot = y.getInt("gui.selected-slot", 39);
        rollSlot = y.getInt("gui.roll-slot", 41);
        selectedName = y.getString("gui.selected-name", "&fWylosowany patron: {PATRON}");
        selectedFooter = y.getStringList("gui.selected-footer");
        if (selectedFooter.isEmpty()) selectedFooter = List.of("", "&fAKTYWNY PRZEZ: &e{czas}");
        rollItemMaterial = parseMaterial(y.getString("gui.roll-item.material", "NETHER_STAR"), Material.NETHER_STAR);
        rollItemName = y.getString("gui.roll-item.name", "&dLosowanie patrona");
        rollItemLore = y.getStringList("gui.roll-item.lore");
        guiDecorations = loadDecorations(y.getList("gui.decoration"));
        infoSlot = y.getInt("gui.info-slot", 40);
        infoMaterial = parseMaterial(y.getString("gui.info-item.material", "BOOK"), Material.BOOK);
        infoName = y.getString("gui.info-item.name", "&c&lINFORMACJE");
        infoLore = y.getStringList("gui.info-item.lore");
        patronGuiSlots.clear();
        ConfigurationSection patronSlotsSection = y.getConfigurationSection("gui.patron-slots");
        if (patronSlotsSection != null) {
            for (String key : patronSlotsSection.getKeys(false)) {
                patronGuiSlots.put(key.toLowerCase(Locale.ROOT), patronSlotsSection.getInt(key));
            }
        }
        rollGuiTitle = y.getString("roll-gui.title", y.getString("gui.roll-title", "&7Losowanko...."));
        rollGuiRows = Math.max(1, y.getInt("roll-gui.rows", 3));
        rollGuiUseTemplate = y.getBoolean("roll-gui.use-template", false);
        rollFinalSlot = y.getInt("roll-gui.final-slot", 13);
        rollSlots = y.getIntegerList("roll-gui.roll-slots");
        if (rollSlots.isEmpty()) rollSlots = List.of(10, 11, 12, 13, 14, 15, 16);
        rollDelayTicks = parseDelayTicks(y.getStringList("roll-gui.anim-delay-ticks"));
        rollDecorations = loadDecorations(y.getList("roll-gui.decoration"));
        rollCooldownDays = Math.max(0, y.getInt("roll.free-cooldown-days", 3));
        paidRollShardCost = Math.max(0, y.getInt("roll.paid-shard-cost", 50));
        patronDurationDays = Math.max(1, y.getInt("roll.patron-duration-days", 14));
        freeReadyText = y.getString("roll.free-ready-text", "TERAZ");
        shardPackages = y.getStringList("roll.shard-packages");
        if (shardPackages.isEmpty()) shardPackages = List.of("odlamek", "odlamkow", "shard");
        godHourMinutes = Math.max(1, y.getInt("god-hour.duration-minutes", 30));
        godHourTitleEnabled = y.getBoolean("god-hour.title-enabled", true);
        godHourTitle = y.getString("god-hour.title", "&eGodzina Boga");
        godHourSubtitle = y.getString("god-hour.subtitle", "{PATRON}");
        godHourTitleFadeIn = Math.max(0, y.getInt("god-hour.title-fade-in", 10));
        godHourTitleStay = Math.max(0, y.getInt("god-hour.title-stay", 60));
        godHourTitleFadeOut = Math.max(0, y.getInt("god-hour.title-fade-out", 10));
        godHourBossBarEnabled = y.getBoolean("god-hour.bossbar-enabled", true);
        godHourBossBarTitle = y.getString("god-hour.bossbar-title", "&eGodzina Boga: {PATRON} &8| &fKoniec za: &a{TIME}");
        godHourBossBarColor = parseBarColor(y.getString("god-hour.bossbar-color", "YELLOW"));
        godHourBossBarStyle = parseBarStyle(y.getString("god-hour.bossbar-style", "SOLID"));
        godHourBossBarProgress = y.getBoolean("god-hour.bossbar-progress", true);
        godHourBossBarFlags = parseBarFlags(y.getStringList("god-hour.bossbar-flags"));
        soundTick = ConfiguredSound.from(y.getConfigurationSection("sounds.roll-tick"), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
        soundFinish = ConfiguredSound.from(y.getConfigurationSection("sounds.roll-finish"), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        soundFail = ConfiguredSound.from(y.getConfigurationSection("sounds.roll-fail"), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        soundOpen = ConfiguredSound.from(y.getConfigurationSection("sounds.gui-open"), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        winTitleEnabled = y.getBoolean("titles.roll-win.enabled", true);
        winTitle = y.getString("titles.roll-win.title", "&a&lWYLOSOWANO");
        winSubtitle = y.getString("titles.roll-win.subtitle", "{PATRON}");
        winTitleFadeIn = Math.max(0, y.getInt("titles.roll-win.fade-in", 5));
        winTitleStay = Math.max(0, y.getInt("titles.roll-win.stay", 40));
        winTitleFadeOut = Math.max(0, y.getInt("titles.roll-win.fade-out", 10));
        mongoCollectionName = y.getString("mongodb.collection", "patroni_players");
        loadMessages(y);
        settings.clear();
        ConfigurationSection section = y.getConfigurationSection("patrons");
        if (section != null) {
            int fallbackIndex = 0;
            int[] fallbackSlots = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
            for (String key : section.getKeys(false)) {
                ConfigurationSection patronSection = section.getConfigurationSection(key);
                if (patronSection == null || !patronSection.getBoolean("enabled", true)) continue;
                String normalized = key.toLowerCase(Locale.ROOT);
                settings.put(normalized, PatronDefinition.from(normalized, patronSection));
                if (!patronGuiSlots.containsKey(normalized) && patronSection.contains("slot")) {
                    patronGuiSlots.put(normalized, patronSection.getInt("slot"));
                }
                if (!patronGuiSlots.containsKey(normalized) && fallbackIndex < fallbackSlots.length) {
                    patronGuiSlots.put(normalized, fallbackSlots[fallbackIndex++]);
                }
            }
        }
    }

    private void loadMessages(YamlConfiguration y) {
        messages.clear();
        putMessage(y, "roll-broadcast", "&8[&dPATRON&8] &e{PLAYER} &fwylosowal patrona {PATRON}&f!");
        putMessage(y, "addon-disabled", "&#FF0000✘ &8Ξ &cAddon patroni jest wylaczony.");
        putMessage(y, "no-permission", "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/patron &8(&4Holly.cmd.patron&8)");
        putMessage(y, "player-only", "&#FF0000✘ &8Ξ &cTa komenda jest tylko dla gracza.");
        putMessage(y, "not-enough-shards", "&#FF0000✘ &8Ξ &cPotrzebujesz &f{COST}x &codlamkow.");
        putMessage(y, "god-hour-random-started", "&8[&dPATRON&8] &aWylosowano Godzine Boga.");
        putMessage(y, "god-hour-stopped", "&8[&dPATRON&8] &cZatrzymano Godzine Boga.");
        putMessage(y, "god-hour-set", "&8[&dPATRON&8] &aUstawiono Godzine Boga: {PATRON}&a.");
        putMessage(y, "reload", "&8[&dPATRON&8] &aPrzeladowano konfiguracje patronow.");
        putMessage(y, "set-head-success", "&8[&dPATRON&8] &aUstawiono head-value patrona w addons/patroni/patroni.yml.");
        putMessage(y, "set-head-fail", "&8[&dPATRON&8] &cNieznany patron albo item w rece nie ma tekstury glowki.");
        putMessage(y, "set-patron-success", "&8[&dPATRON&8] &aUstawiono patrona {PATRON} &agraczowi &e{PLAYER}&a.");
        putMessage(y, "set-patron-target", "&8[&dPATRON&8] &aAdministrator ustawil Ci patrona {PATRON}&a.");
        putMessage(y, "set-patron-usage", "&#FF0000✘ &8Ξ &cPoprawne użycie: &#FF0000\n &8• &4/patron setpatron <patron> <nick>");
        putMessage(y, "unknown-patron", "&#FF0000✘ &8Ξ &cNieznany patron.");
        putMessage(y, "player-not-found", "&#FF0000✘ &8Ξ &cNie znaleziono gracza.");
        rollBroadcastMessage = messages.get("roll-broadcast");
    }

    private void putMessage(YamlConfiguration y, String key, String fallback) {
        messages.put(key, y.getString("messages." + key, fallback));
    }

    private List<Decoration> loadDecorations(List<?> raw) {
        List<Decoration> out = new ArrayList<>();
        if (raw == null) return out;
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            Object slotsRaw = map.get("slots");
            List<Integer> slots = new ArrayList<>();
            if (slotsRaw instanceof List<?> list) {
                for (Object value : list) {
                    if (value instanceof Number number) slots.add(number.intValue());
                    else try { slots.add(Integer.parseInt(String.valueOf(value))); } catch (NumberFormatException ignored) {}
                }
            }
            Material material = parseMaterial(String.valueOf(map.get("material")), Material.GRAY_STAINED_GLASS_PANE);
            String name = map.get("name") == null ? " " : String.valueOf(map.get("name"));
            out.add(new Decoration(slots, material, name));
        }
        return out;
    }

    private int[] parseDelayTicks(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new int[]{1, 1, 1, 2, 2, 3, 4, 6, 8, 12, 16, 24};
        }
        int[] out = new int[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            try {
                out[i] = Math.max(1, Integer.parseInt(raw.get(i).trim()));
            } catch (NumberFormatException ignored) {
                out[i] = 1;
            }
        }
        return out;
    }

    private BarColor parseBarColor(String value) {
        try {
            return BarColor.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle parseBarStyle(String value) {
        try {
            return BarStyle.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return BarStyle.SOLID;
        }
    }

    private List<BarFlag> parseBarFlags(List<String> values) {
        List<BarFlag> flags = new ArrayList<>();
        if (values == null) return flags;
        for (String value : values) {
            try {
                flags.add(BarFlag.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        return flags;
    }

    private Material parseMaterial(String value, Material fallback) {
        Material material = Material.matchMaterial(String.valueOf(value));
        return material != null && material.isItem() ? material : fallback;
    }

    private void play(Player player, ConfiguredSound sound) {
        if (player == null || sound == null || sound.sound == null) return;
        player.playSound(player.getLocation(), sound.sound, sound.volume, sound.pitch);
    }

    private void migrateLegacyYamlIfPresent() {
        if (collection == null || !dataFile.isFile()) {
            return;
        }
        loadLegacyData();
        Set<UUID> players = new HashSet<>();
        players.addAll(patrons.keySet());
        players.addAll(nextFreeRoll.keySet());
        players.addAll(patronExpires.keySet());
        if (players.isEmpty()) {
            if (!dataFile.delete()) {
                plugin.getLogger().warning("[Patroni] Nie udało się usunąć starego data.yml.");
            }
            return;
        }
        for (UUID uuid : players) {
            try {
                savePlayerMongo(uuid).join();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("[Patroni] Migracja gracza " + uuid + " nieudana: " + exception.getMessage());
            }
        }
        if (!dataFile.delete()) {
            plugin.getLogger().warning("[Patroni] Zmigrowano dane, ale nie udało się usunąć data.yml — skasuj ręcznie.");
        } else {
            plugin.getLogger().info("[Patroni] Zmigrowano " + players.size() + " graczy z data.yml → MongoDB.");
        }
    }

    private void loadLegacyData() {
        dataFile.getParentFile().mkdirs();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        if (y.isConfigurationSection("free")) {
            for (String k : y.getConfigurationSection("free").getKeys(false)) {
                try { nextFreeRoll.put(UUID.fromString(k), y.getLong("free." + k)); } catch (Exception ignored) {}
            }
        }
        if (y.isConfigurationSection("patrons")) {
            for (String k : y.getConfigurationSection("patrons").getKeys(false)) {
                try {
                    PatronDefinition p = find(y.getString("patrons." + k));
                    if (p != null) patrons.put(UUID.fromString(k), p.key);
                } catch (Exception ignored) {}
            }
        }
        if (y.isConfigurationSection("expires")) {
            for (String k : y.getConfigurationSection("expires").getKeys(false)) {
                try { patronExpires.put(UUID.fromString(k), y.getLong("expires." + k)); } catch (Exception ignored) {}
            }
        }
    }

    private CompletableFuture<?> savePlayerData(UUID uuid) {
        if (collection != null) {
            return savePlayerMongo(uuid);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void loadMongoData() {
        if (collection == null) return;
        plugin.getMongoManager().toListFuture(collection.find()).thenAccept(docs -> Bukkit.getScheduler().runTask(plugin, () -> {
            for (Document doc : docs) applyMongoDocument(doc);
            Bukkit.getOnlinePlayers().forEach(this::refreshGodEffects);
        })).exceptionally(error -> {
            plugin.getLogger().warning("Nie wczytano patronow z MongoDB: " + error.getMessage());
            return null;
        });
    }

    private void loadPlayerMongo(UUID uuid) {
        if (collection == null) return;
        plugin.getMongoManager().toFuture(collection.find(Filters.eq("uuid", uuid.toString())).first()).thenAccept(doc -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (doc != null) {
                applyMongoDocument(doc);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) refreshGodEffects(p);
            }
        })).exceptionally(error -> {
            plugin.getLogger().warning("Nie wczytano patrona gracza z MongoDB: " + error.getMessage());
            return null;
        });
    }

    private void applyMongoDocument(Document doc) {
        try {
            UUID uuid = UUID.fromString(doc.getString("uuid"));
            PatronDefinition patron = find(doc.getString("patron"));
            if (patron != null) patrons.put(uuid, patron.key);
            else patrons.remove(uuid);
            Number next = doc.get("nextFreeRoll", Number.class);
            if (next != null) nextFreeRoll.put(uuid, next.longValue());
            Number expires = doc.get("patronExpires", Number.class);
            if (expires != null) patronExpires.put(uuid, expires.longValue());
        } catch (Exception ignored) {
        }
    }

    private CompletableFuture<?> savePlayerMongo(UUID uuid) {
        if (collection == null) return CompletableFuture.completedFuture(null);
        String patron = patrons.get(uuid);
        Document doc = new Document("uuid", uuid.toString())
            .append("patron", patron)
            .append("nextFreeRoll", nextFreeRoll.getOrDefault(uuid, 0L))
            .append("patronExpires", patronExpires.getOrDefault(uuid, 0L))
            .append("updatedAt", System.currentTimeMillis());
        return plugin.getMongoManager().toFuture(collection.replaceOne(Filters.eq("uuid", uuid.toString()), doc, new ReplaceOptions().upsert(true)))
            .exceptionally(error -> {
                plugin.getLogger().warning("Nie zapisano patrona gracza w MongoDB: " + error.getMessage());
                return null;
            });
    }

    private void saveAllPlayersMongo() {
        Set<UUID> playersToSave = new HashSet<>();
        playersToSave.addAll(patrons.keySet());
        playersToSave.addAll(nextFreeRoll.keySet());
        playersToSave.addAll(patronExpires.keySet());
        CompletableFuture<?>[] saves = playersToSave.stream().map(this::savePlayerMongo).toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(saves).get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie zakonczono zapisu patronow do MongoDB przed wylaczeniem: " + exception.getMessage());
        }
    }

    private List<PatronDefinition> configuredPatrons() {
        return settings.values().stream().sorted(Comparator.comparing(p -> p.key)).toList();
    }

    private String color(String s) {
        return ColorUtils.colorize(s);
    }

    private String stripColors(String s) {
        return ColorUtils.colorize(s).replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Config always uses percent values: 0.3 = 0.3%, 2 = 2%, 10 = 10%.
     * (Previously values &lt;= 1 were treated as fractions, so 0.3 became 30%.)
     */
    private static double readPercentChance(ConfigurationSection y, String path, double fallback) {
        if (y == null || !y.contains(path)) return fallback;
        Object raw = y.get(path);
        double percent;
        if (raw instanceof Number number) {
            percent = number.doubleValue();
        } else if (raw instanceof String text) {
            try {
                percent = Double.parseDouble(text.trim().replace("%", "").replace(",", "."));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        } else {
            return fallback;
        }
        if (Double.isNaN(percent) || Double.isInfinite(percent)) return fallback;
        if (percent <= 0.0) return 0.0;
        if (percent >= 100.0) return 1.0;
        return percent / 100.0;
    }

    /** Preferuje {@code lightning-hearts}, inaczej {@code lightning-damage} (HP). 1 serce = 2 HP. */
    private static double readLightningDamage(ConfigurationSection effects) {
        if (effects == null) return 0.0;
        if (effects.contains("lightning-hearts")) {
            return Math.max(0.0, effects.getDouble("lightning-hearts") * 2.0);
        }
        return Math.max(0.0, effects.getDouble("lightning-damage", 0.0));
    }

    private static final class PatronDefinition {
        private final String key;
        private final String displayName;
        private final List<String> lore;
        private final String headValue;
        private final int weight;
        private final double damageMultiplier;
        private final double damageTakenMultiplier;
        private final double igniteChance;
        private final int igniteTicks;
        private final int goldenAppleExtraRegenTicks;
        private final int goldenAppleRegenAmplifier;
        private final boolean neverHungry;
        private final int speedAmplifier;
        private final double cobwebChance;
        private final boolean disableCobweb;
        private final double dropHelmetChance;
        private final int fireworkCooldownSeconds;
        private final double lowHealthHearts;
        private final double lowHealthDamageMultiplier;
        private final double poisonChance;
        private final int poisonDurationTicks;
        private final int poisonAmplifier;
        private final double lightningChance;
        private final boolean lightningReal;
        /** Obrażenia pioruna w HP (2 HP = 1 serce). */
        private final double lightningDamage;
        private final double levitationChance;
        private final int levitationDurationTicks;
        private final int levitationAmplifier;
        private final double blockHitChance;
        private final double weakenChance;
        private final int weakenDurationTicks;
        private final int weakenAmplifier;
        private final int strengthAmplifier;
        private final double healOnHitChance;
        private final double healAmount;
        private final int regenWhenHitTicks;
        private final int regenWhenHitAmplifier;
        private final int absorptionEverySeconds;
        private final int absorptionAmplifier;
        private final int absorptionDurationTicks;

        private PatronDefinition(String key, String displayName, List<String> lore, String headValue, int weight,
                                 double damageMultiplier, double damageTakenMultiplier, double igniteChance, int igniteTicks,
                                 int goldenAppleExtraRegenTicks, int goldenAppleRegenAmplifier, boolean neverHungry, int speedAmplifier,
                                 double cobwebChance, boolean disableCobweb, double dropHelmetChance, int fireworkCooldownSeconds,
                                 double lowHealthHearts, double lowHealthDamageMultiplier, double poisonChance, int poisonDurationTicks,
                                 int poisonAmplifier, double lightningChance, boolean lightningReal, double lightningDamage,
                                 double levitationChance, int levitationDurationTicks, int levitationAmplifier, double blockHitChance,
                                 double weakenChance, int weakenDurationTicks, int weakenAmplifier, int strengthAmplifier,
                                 double healOnHitChance, double healAmount, int regenWhenHitTicks, int regenWhenHitAmplifier,
                                 int absorptionEverySeconds, int absorptionAmplifier, int absorptionDurationTicks) {
            this.key = key;
            this.displayName = displayName;
            this.lore = lore;
            this.headValue = headValue;
            this.weight = weight;
            this.damageMultiplier = damageMultiplier;
            this.damageTakenMultiplier = damageTakenMultiplier;
            this.igniteChance = igniteChance;
            this.igniteTicks = igniteTicks;
            this.goldenAppleExtraRegenTicks = goldenAppleExtraRegenTicks;
            this.goldenAppleRegenAmplifier = goldenAppleRegenAmplifier;
            this.neverHungry = neverHungry;
            this.speedAmplifier = speedAmplifier;
            this.cobwebChance = cobwebChance;
            this.disableCobweb = disableCobweb;
            this.dropHelmetChance = dropHelmetChance;
            this.fireworkCooldownSeconds = fireworkCooldownSeconds;
            this.lowHealthHearts = lowHealthHearts;
            this.lowHealthDamageMultiplier = lowHealthDamageMultiplier;
            this.poisonChance = poisonChance;
            this.poisonDurationTicks = poisonDurationTicks;
            this.poisonAmplifier = poisonAmplifier;
            this.lightningChance = lightningChance;
            this.lightningReal = lightningReal;
            this.lightningDamage = lightningDamage;
            this.levitationChance = levitationChance;
            this.levitationDurationTicks = levitationDurationTicks;
            this.levitationAmplifier = levitationAmplifier;
            this.blockHitChance = blockHitChance;
            this.weakenChance = weakenChance;
            this.weakenDurationTicks = weakenDurationTicks;
            this.weakenAmplifier = weakenAmplifier;
            this.strengthAmplifier = strengthAmplifier;
            this.healOnHitChance = healOnHitChance;
            this.healAmount = healAmount;
            this.regenWhenHitTicks = regenWhenHitTicks;
            this.regenWhenHitAmplifier = regenWhenHitAmplifier;
            this.absorptionEverySeconds = absorptionEverySeconds;
            this.absorptionAmplifier = absorptionAmplifier;
            this.absorptionDurationTicks = absorptionDurationTicks;
        }

        static PatronDefinition from(String key, ConfigurationSection y) {
            ConfigurationSection effects = y.getConfigurationSection("effects");
            if (effects == null) effects = y;
            return new PatronDefinition(
                key,
                y.getString("name", "&f" + key.toUpperCase(Locale.ROOT)),
                y.getStringList("lore"),
                y.getString("head-value", ""),
                Math.max(1, y.getInt("weight", 1)),
                effects.getDouble("damage-multiplier", 1.0),
                effects.getDouble("damage-taken-multiplier", 1.0),
                readPercentChance(effects, "ignite-chance", 0.0),
                effects.getInt("ignite-ticks", 0),
                effects.getInt("golden-apple-extra-regen-ticks", 0),
                effects.getInt("golden-apple-regen-amplifier", 0),
                effects.getBoolean("never-hungry", false),
                effects.contains("speed-amplifier") ? effects.getInt("speed-amplifier") : -1,
                readPercentChance(effects, "cobweb-chance", 0.0),
                effects.getBoolean("disable-cobweb", false),
                readPercentChance(effects, "drop-helmet-chance", 0.0),
                effects.getInt("firework-cooldown-seconds", 0),
                effects.getDouble("low-health-hearts", 0.0),
                effects.getDouble("low-health-damage-multiplier", 1.0),
                readPercentChance(effects, "poison-chance", 0.0),
                effects.getInt("poison-duration-ticks", 0),
                effects.getInt("poison-amplifier", 0),
                readPercentChance(effects, "lightning-chance", 0.0),
                effects.getBoolean("lightning-real", false),
                readLightningDamage(effects),
                readPercentChance(effects, "levitation-chance", 0.0),
                effects.getInt("levitation-duration-ticks", 0),
                effects.getInt("levitation-amplifier", 0),
                readPercentChance(effects, "block-hit-chance", 0.0),
                readPercentChance(effects, "weaken-chance", 0.0),
                effects.getInt("weaken-duration-ticks", 0),
                effects.getInt("weaken-amplifier", 0),
                effects.contains("strength-amplifier") ? effects.getInt("strength-amplifier") : -1,
                readPercentChance(effects, "heal-on-hit-chance", 0.0),
                effects.getDouble("heal-amount", 0.0),
                effects.getInt("regen-when-hit-ticks", 0),
                effects.getInt("regen-when-hit-amplifier", 0),
                effects.getInt("absorption-every-seconds", 0),
                effects.getInt("absorption-amplifier", 0),
                effects.getInt("absorption-duration-ticks", 240)
            );
        }
    }

    private record Decoration(List<Integer> slots, Material material, String name) {
    }

    private record ConfiguredSound(Sound sound, float volume, float pitch) {
        static ConfiguredSound from(ConfigurationSection section, Sound fallback, float volume, float pitch) {
            Sound sound = fallback;
            float vol = volume;
            float pit = pitch;
            if (section != null) {
                try {
                    sound = Sound.valueOf(section.getString("sound", fallback.name()).trim().toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {
                }
                vol = (float) section.getDouble("volume", volume);
                pit = (float) section.getDouble("pitch", pitch);
            }
            return new ConfiguredSound(sound, vol, pit);
        }
    }

    private static final class GuiHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(null, 9);
        }
    }

    private static final class RollHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return Bukkit.createInventory(null, 9);
        }
    }
}
