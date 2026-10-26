package dev.grzybalson.modules.piniata;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.util.TimeUtil;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.CommandPlaceholderUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class PiniataManager implements Listener {

    private static final String RESOURCE_PATH = "addons/piniata/piniata.yml";

    private final HollyCore plugin;
    private final File configFile;
    private final Random random = new Random();
    private YamlConfiguration config;

    private BukkitTask tickerTask;
    private BukkitTask piniataTask;
    private BossBar bossBar;
    private Long scheduledStartMillis;
    private Long scheduledDelayMillis;
    private Long activeEndMillis;
    private boolean active;
    private UUID piniataEntityUuid;
    private double piniataCurrentHealth;
    private boolean piniataSmall;
    private long nextPiniataSizeChangeMillis;
    private int lastCountdownSecond = -1;
    private final Map<UUID, PlayerScore> scores = new HashMap<>();
    private final Map<UUID, Long> hitRewardCooldowns = new HashMap<>();
    private final Map<UUID, Long> knockbackCooldowns = new HashMap<>();

    public PiniataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "piniata"), "piniata.yml");
        reload();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void shutdown() {
        stopInternal();
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    public boolean isBusy() {
        return active || scheduledStartMillis != null;
    }

    public void start(CommandSender sender, long delayMillis) {
        if (!isEnabled()) {
            send(sender, message("disabled"));
            return;
        }
        if (isBusy()) {
            send(sender, message("already-running"));
            return;
        }

        long safeDelay = Math.max(0L, delayMillis);
        if (safeDelay <= 0L) {
            startActive();
            return;
        }

        scheduledDelayMillis = safeDelay;
        scheduledStartMillis = System.currentTimeMillis() + safeDelay;
        lastCountdownSecond = -1;
        startTicker();
        updateBossBar();
        send(sender, message("scheduled").replace("{time}", TimeUtil.convert(safeDelay)));
    }

    public void stop(CommandSender sender) {
        if (!isBusy()) {
            send(sender, message("not-running"));
            return;
        }
        stopInternal();
        send(sender, message("stopped"));
    }

    public void setSpawn(Location location) {
        config.set("spawn.world", location.getWorld() == null ? "world" : location.getWorld().getName());
        config.set("spawn.x", location.getX());
        config.set("spawn.y", location.getY());
        config.set("spawn.z", location.getZ());
        config.set("spawn.yaw", location.getYaw());
        config.set("spawn.pitch", location.getPitch());
        save();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPiniataHit(EntityDamageByEntityEvent event) {
        handlePiniataDamage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiniataAttack(EntityDamageByEntityEvent event) {
        if (isActivePiniata(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvalidPiniataDamage(EntityDamageByEntityEvent event) {
        if (isActivePiniata(event.getEntity()) && playerDamager(event.getDamager()) == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiniataDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (isActivePiniata(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPiniataDeath(EntityDeathEvent event) {
        if (!isActivePiniata(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        finishActive();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (bossBar != null) {
            bossBar.removePlayer(event.getPlayer());
        }
    }

    /** Sam joiner wystarczy — pelny updateBossBar() to O(online) na kazde wejscie. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bossBar == null) {
            return;
        }
        Player player = event.getPlayer();
        boolean onlyEventWorld = config.getBoolean("bossbar.only-event-world", false);
        String worldName = config.getString("spawn.world", "spawn");
        if (!onlyEventWorld || player.getWorld().getName().equalsIgnoreCase(worldName)) {
            bossBar.addPlayer(player);
        } else {
            bossBar.removePlayer(player);
        }
    }

    private void startActive() {
        World world = Bukkit.getWorld(config.getString("spawn.world", "spawn"));
        if (world == null) {
            plugin.getLogger().warning("Nie mozna wystartowac piniaty - swiat "
                + config.getString("spawn.world", "spawn") + " nie istnieje.");
            scheduledStartMillis = null;
            scheduledDelayMillis = null;
            stopTickerIfIdle();
            return;
        }

        cleanupPiniata();
        scores.clear();
        Location location = new Location(
            world,
            config.getDouble("spawn.x"),
            config.getDouble("spawn.y"),
            config.getDouble("spawn.z"),
            (float) config.getDouble("spawn.yaw"),
            (float) config.getDouble("spawn.pitch")
        );
        Llama llama = (Llama) world.spawnEntity(location, EntityType.LLAMA);
        double maxHealth = Math.max(1.0, config.getDouble("max-health", 2000.0));
        piniataCurrentHealth = maxHealth;
        AttributeInstance maxHealthAttribute = llama.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(Math.min(maxHealth, 2048.0));
        }
        llama.setHealth(Math.min(llama.getMaxHealth(), maxHealthAttribute == null ? llama.getHealth() : maxHealthAttribute.getValue()));
        llama.setAdult();
        llama.setAgeLock(true);
        llama.setAI(true);
        llama.setCustomNameVisible(true);
        llama.setGlowing(config.getBoolean("glowing", true));
        llama.setRemoveWhenFarAway(false);
        llama.setPersistent(false);
        piniataEntityUuid = llama.getUniqueId();
        piniataSmall = false;
        nextPiniataSizeChangeMillis = System.currentTimeMillis() + Math.max(1, config.getInt("size-change-seconds", 5)) * 1000L;
        active = true;
        long durationMillis = Math.max(1, config.getInt("duration-seconds", 300)) * 1000L;
        activeEndMillis = System.currentTimeMillis() + durationMillis;
        scheduledStartMillis = null;
        scheduledDelayMillis = null;
        updatePiniataName();
        long period = Math.max(1L, config.getLong("effect-interval-ticks", 20L));
        piniataTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPiniata, period, period);
        startTicker();
        updateBossBar();
        if (config.getBoolean("chat-announcements", true)) {
            broadcast(message("started")
                .replace("{duration}", TimeUtil.convert(durationMillis))
                .replace("{event}", displayName()));
        }
    }

    private void finishActive() {
        if (!active) {
            return;
        }
        List<PlayerScore> top = topScores(5);
        broadcastEnd(top);
        runRewards(top);
        stopInternal();
    }

    private void stopInternal() {
        stopPiniataTask();
        cleanupPiniata();
        cleanupBossBar();
        active = false;
        activeEndMillis = null;
        scheduledStartMillis = null;
        scheduledDelayMillis = null;
        lastCountdownSecond = -1;
        scores.clear();
        stopTickerIfIdle();
    }

    private void tickPiniata() {
        if (!active) {
            stopPiniataTask();
            return;
        }
        Llama llama = piniataEntity();
        if (llama == null || llama.isDead()) {
            finishActive();
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= nextPiniataSizeChangeMillis) {
            piniataSmall = !piniataSmall;
            setPiniataSize(llama, piniataSmall);
            nextPiniataSizeChangeMillis = now + Math.max(1, config.getInt("size-change-seconds", 5)) * 1000L;
        }
        applyPiniataEffects(llama);
        updatePiniataName();
    }

    private void setPiniataSize(Llama llama, boolean small) {
        if (small) {
            llama.setBaby();
        } else {
            llama.setAdult();
        }
        llama.setAgeLock(true);
    }

    private void applyPiniataEffects(Llama llama) {
        double slowRadius = config.getDouble("slow-radius", 8.0);
        double knockbackRadius = config.getDouble("knockback-radius", 6.0);
        double radius = Math.max(slowRadius, knockbackRadius);
        if (radius <= 0.0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Entity nearby : llama.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof Player player)) {
                continue;
            }
            if (slowRadius > 0.0 && player.getLocation().distanceSquared(llama.getLocation()) <= slowRadius * slowRadius) {
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW,
                    Math.max(1, config.getInt("slow-duration-ticks", 60)),
                    Math.max(0, config.getInt("slow-amplifier", 1)),
                    false,
                    true,
                    true
                ));
            }
            if (knockbackRadius <= 0.0 || player.getLocation().distanceSquared(llama.getLocation()) > knockbackRadius * knockbackRadius) {
                continue;
            }
            long cooldownUntil = knockbackCooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (now < cooldownUntil) {
                continue;
            }
            Vector direction = player.getLocation().toVector().subtract(llama.getLocation().toVector());
            if (direction.lengthSquared() <= 1.0E-4) {
                direction = new Vector(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5);
            }
            direction.normalize().multiply(Math.max(0.0, config.getDouble("knockback-strength", 1.2)));
            direction.setY(Math.max(0.0, config.getDouble("knockback-y", 0.35)));
            player.setVelocity(direction);
            knockbackCooldowns.put(player.getUniqueId(), now + Math.max(0L, config.getLong("knockback-cooldown-millis", 5000L)));
        }
    }

    private boolean handlePiniataDamage(EntityDamageByEntityEvent event) {
        if (!isActivePiniata(event.getEntity())) {
            return false;
        }
        event.setCancelled(true);
        Player damager = playerDamager(event.getDamager());
        if (damager == null) {
            return true;
        }
        double damagePerHit = Math.max(0.0, config.getDouble("damage-per-hit", 8.0));
        double damage = Math.max(0.0, Math.min(damagePerHit, piniataCurrentHealth));
        piniataCurrentHealth = Math.max(0.0, piniataCurrentHealth - damage);
        addScore(damager, damage);
        runHitReward(damager, damage);
        updatePiniataName();
        updateBossBar();
        if (piniataCurrentHealth <= 0.0) {
            finishActive();
        }
        return true;
    }

    private void addScore(Player player, double amount) {
        if (amount <= 0.0) {
            return;
        }
        PlayerScore score = scores.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerScore(uuid, player.getName()));
        score.add(amount);
    }

    private void runHitReward(Player player, double damage) {
        List<Map<?, ?>> rewards = config.getMapList("hit-rewards");
        if (rewards == null || rewards.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, config.getLong("hit-reward-cooldown-millis", 1000L));
        Long lastReward = hitRewardCooldowns.get(player.getUniqueId());
        if (lastReward != null && now - lastReward < cooldown) {
            return;
        }
        boolean rewarded = false;
        for (Map<?, ?> reward : rewards) {
            double chance = toDouble(reward.get("chance"));
            Object commandsRaw = reward.get("commands");
            if (!(commandsRaw instanceof List<?> commands) || commands.isEmpty() || chance <= 0.0) {
                continue;
            }
            if (random.nextDouble() * 100.0 > Math.min(100.0, chance)) {
                continue;
            }
            rewarded = true;
            for (Object commandObj : commands) {
                if (commandObj == null) {
                    continue;
                }
                String command = CommandPlaceholderUtil.stripLeadingSlash(commandObj.toString()
                    .replace("{player}", player.getName())
                    .replace("{damage}", formatHp(damage))
                    .replace("{hp}", formatHp(piniataHealth()))
                    .replace("{max_hp}", formatHp(piniataMaxHealth()))
                    .replace("{event}", displayName()));
                if (!command.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
        }
        if (rewarded) {
            hitRewardCooldowns.put(player.getUniqueId(), now);
        }
    }

    private void runRewards(List<PlayerScore> top) {
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        if (rewards == null) {
            return;
        }
        for (int index = 0; index < Math.min(5, top.size()); index++) {
            PlayerScore score = top.get(index);
            int place = index + 1;
            List<String> commands = rewards.getStringList("top-" + place);
            if (commands == null || commands.isEmpty()) {
                continue;
            }
            for (String command : commands) {
                if (command == null || command.isBlank()) {
                    continue;
                }
                String parsed = CommandPlaceholderUtil.stripLeadingSlash(applyPlaceholders(command, null, top, place)
                    .replace("{player}", score.name)
                    .replace("{score}", formatHp(score.score))
                    .replace("{place}", String.valueOf(place)));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
    }

    private void startTicker() {
        if (tickerTask != null) {
            return;
        }
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (active) {
            if (activeEndMillis != null && now >= activeEndMillis) {
                finishActive();
                return;
            }
            updateBossBar();
            return;
        }
        if (scheduledStartMillis != null) {
            long remainingMillis = scheduledStartMillis - now;
            if (remainingMillis <= 0L) {
                scheduledStartMillis = null;
                scheduledDelayMillis = null;
                startActive();
                return;
            }
            announceCountdown(remainingMillis);
            updateBossBar();
            return;
        }
        stopTickerIfIdle();
    }

    private void announceCountdown(long remainingMillis) {
        if (!config.getBoolean("chat-announcements", true)) {
            return;
        }
        int seconds = (int) Math.ceil(remainingMillis / 1000.0);
        if (seconds == lastCountdownSecond) {
            return;
        }
        lastCountdownSecond = seconds;
        if (!config.getIntegerList("countdown-seconds").contains(seconds)) {
            return;
        }
        for (String line : config.getStringList("messages.countdown")) {
            broadcast(applySimple(line).replace("{time}", TimeUtil.convert(remainingMillis)).replace("{event}", displayName()));
        }
    }

    private void updateBossBar() {
        if (!config.getBoolean("bossbar.enabled", true)) {
            cleanupBossBar();
            return;
        }
        BarStyle style = parseBarStyle(config.getString("bossbar.style", "SOLID"));
        String title;
        BarColor color;
        double progress;
        if (active) {
            title = applyPlaceholders(config.getString("bossbar.hp-title", "&fPINIATA HP: &d{piniata_hp}&7/&f{piniata_max_hp}"), null, topScores(5), 0);
            progress = piniataHealth() / Math.max(1.0, piniataMaxHealth());
            color = parseBarColor(config.getString("bossbar.end-color", "RED"), BarColor.RED);
        } else if (scheduledStartMillis != null) {
            long remainingMillis = Math.max(0L, scheduledStartMillis - System.currentTimeMillis());
            long totalMillis = Math.max(1L, scheduledDelayMillis == null ? remainingMillis : scheduledDelayMillis);
            progress = (double) remainingMillis / (double) totalMillis;
            title = applySimple(config.getString("bossbar.start-title", "&fDO ROZPOCZECIA PINIATY ZOSTALO: &b{time}"))
                .replace("{time}", TimeUtil.convert(remainingMillis))
                .replace("{event}", displayName());
            color = parseBarColor(config.getString("bossbar.start-color", "YELLOW"), BarColor.YELLOW);
        } else {
            cleanupBossBar();
            return;
        }

        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(ColorUtils.colorize(title), color, style);
        } else {
            bossBar.setTitle(ColorUtils.colorize(title));
            bossBar.setColor(color);
            bossBar.setStyle(style);
        }
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        boolean onlyEventWorld = config.getBoolean("bossbar.only-event-world", false);
        String worldName = config.getString("spawn.world", "spawn");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!onlyEventWorld || player.getWorld().getName().equalsIgnoreCase(worldName)) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    private void updatePiniataName() {
        Llama llama = piniataEntity();
        if (llama == null) {
            return;
        }
        String name = config.getString("entity-name", "&d&lPINIATA &8- &f{hp}&7/&f{max_hp} HP")
            .replace("{hp}", formatHp(piniataHealth()))
            .replace("{max_hp}", formatHp(piniataMaxHealth()));
        llama.customName(ColorUtils.colorizeComponent(name));
        llama.setCustomNameVisible(true);
    }

    private void stopPiniataTask() {
        if (piniataTask != null) {
            piniataTask.cancel();
            piniataTask = null;
        }
    }

    private void cleanupPiniata() {
        Llama llama = piniataEntity();
        if (llama != null && !llama.isDead()) {
            llama.remove();
        }
        piniataEntityUuid = null;
        hitRewardCooldowns.clear();
        knockbackCooldowns.clear();
        piniataCurrentHealth = 0.0;
        piniataSmall = false;
        nextPiniataSizeChangeMillis = 0L;
    }

    private void cleanupBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    private void stopTickerIfIdle() {
        if (active || scheduledStartMillis != null) {
            return;
        }
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private Llama piniataEntity() {
        if (piniataEntityUuid == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(piniataEntityUuid);
        return entity instanceof Llama llama ? llama : null;
    }

    private boolean isActivePiniata(Entity entity) {
        return active && entity != null && piniataEntityUuid != null && entity.getUniqueId().equals(piniataEntityUuid);
    }

    private Player playerDamager(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private List<PlayerScore> topScores(int limit) {
        return scores.values().stream()
            .filter(score -> score.score > 0.0)
            .sorted(Comparator.comparingDouble((PlayerScore score) -> score.score).reversed()
                .thenComparing(score -> score.name, String.CASE_INSENSITIVE_ORDER))
            .limit(Math.max(0, limit))
            .toList();
    }

    private double piniataHealth() {
        return Math.max(0.0, piniataCurrentHealth);
    }

    private double piniataMaxHealth() {
        return Math.max(1.0, config.getDouble("max-health", 2000.0));
    }

    private String displayName() {
        return config.getString("display-name", "Piniata");
    }

    private String formatHp(double value) {
        long tenths = Math.round(value * 10.0);
        boolean negative = tenths < 0L;
        if (negative) {
            tenths = Math.abs(tenths);
        }
        return (negative ? "-" : "") + tenths / 10L + "." + tenths % 10L;
    }

    private String applyPlaceholders(String text, Player player, List<PlayerScore> top, int rewardPlace) {
        String value = text == null ? "" : text;
        String hp = formatHp(piniataHealth());
        String maxHp = formatHp(piniataMaxHealth());
        long remainingMillis = active && activeEndMillis != null
            ? Math.max(0L, activeEndMillis - System.currentTimeMillis())
            : 0L;
        value = applySimple(value)
            .replace("{event}", displayName())
            .replace("{event_name}", displayName())
            .replace("{piniata_hp}", hp)
            .replace("{piniata_max_hp}", maxHp)
            .replace("{hp}", hp)
            .replace("{max_hp}", maxHp)
            .replace("{time}", TimeUtil.convert(remainingMillis))
            .replace("{duration}", TimeUtil.convert(Math.max(1, config.getInt("duration-seconds", 300)) * 1000L))
            .replace("{place}", rewardPlace > 0 ? String.valueOf(rewardPlace) : "");
        if (player != null) {
            PlayerScore playerScore = scores.get(player.getUniqueId());
            value = value.replace("{player}", player.getName())
                .replace("{player_score}", formatHp(playerScore == null ? 0.0 : playerScore.score));
        }
        String noWinners = config.getString("messages.no-winners", "brak");
        for (int place = 1; place <= 5; place++) {
            PlayerScore score = place <= top.size() ? top.get(place - 1) : null;
            String topValue = score == null ? noWinners : score.name + " - " + formatHp(score.score);
            value = value.replace("{top_" + place + "}", topValue)
                .replace("{top_" + place + "_player}", score == null ? noWinners : score.name)
                .replace("{top_" + place + "_score}", score == null ? "0" : formatHp(score.score));
        }
        return value;
    }

    private String applySimple(String message) {
        return (message == null ? "" : message).replace("{prefix}", config.getString("messages.prefix", ""));
    }

    private String message(String key) {
        return applySimple(config.getString("messages." + key, ""));
    }

    private void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isBlank()) {
            return;
        }
        sender.sendMessage(ColorUtils.colorizeComponent(message));
    }

    private void broadcast(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Bukkit.getConsoleSender().sendMessage(ColorUtils.colorizeComponent(message));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ColorUtils.colorizeComponent(message));
        }
    }

    private void broadcastEnd(List<PlayerScore> top) {
        if (!config.getBoolean("chat-announcements", true)) {
            return;
        }
        for (String line : config.getStringList("messages.ended")) {
            broadcast(applyPlaceholders(line, null, top, 0));
        }
    }

    private BarColor parseBarColor(String raw, BarColor fallback) {
        try {
            return BarColor.valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private BarStyle parseBarStyle(String raw) {
        try {
            return BarStyle.valueOf(raw == null ? "SOLID" : raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BarStyle.SOLID;
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private void save() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac addons/piniata/piniata.yml: " + exception.getMessage());
        }
    }

    private static final class PlayerScore {
        private final String name;
        private double score;

        private PlayerScore(UUID uuid, String name) {
            this.name = name;
        }

        private void add(double amount) {
            this.score += amount;
        }
    }
}
