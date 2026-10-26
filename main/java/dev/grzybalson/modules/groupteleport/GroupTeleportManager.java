package dev.grzybalson.modules.groupteleport;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.SpawnConfig;
import dev.grzybalson.managers.MultiActionBarService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class GroupTeleportManager {

    private final HollyCore plugin;
    private final GroupTeleportConfig config;
    private final Map<UUID, GroupTeleportSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRemoveKill = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingVictimActionBarKiller = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastElytraNotify = new ConcurrentHashMap<>();
    private static final long REMOVE_KILL_COOLDOWN = 1000L;
    private static final long ELYTRA_NOTIFY_COOLDOWN = 1500L;
    private static final int ELYTRA_BLOCK_TICKS = 20 * 60 * 5;

    public GroupTeleportManager(HollyCore plugin) {
        this.plugin = plugin;
        this.config = new GroupTeleportConfig(plugin);
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    public java.util.Collection<GroupTeleportSession> snapshotSessions() {
        return new ArrayList<>(sessions.values());
    }

    public GroupTeleportConfig getConfig() {
        return config;
    }

    public void reload() {
        config.reload();
    }

    public void shutdown() {
        for (GroupTeleportSession session : new ArrayList<>(sessions.values())) {
            Player first = null;
            Player second = null;
            if (!session.getPlayers().isEmpty()) {
                first = Bukkit.getPlayer(session.getPlayers().get(0));
            }
            if (session.getPlayers().size() > 1) {
                second = Bukkit.getPlayer(session.getPlayers().get(1));
            }
            if (first != null || second != null) {
                endSession(first, second, true);
            }
        }
        sessions.clear();
        restoreAllVisibility();
    }

    public GroupTeleportSession getByPlayer(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        for (GroupTeleportSession session : sessions.values()) {
            if (session.getPlayers().contains(uuid)) {
                return session;
            }
        }
        return null;
    }

    public void startTeleport(Player first, Player second) {
        if (first == null || second == null) {
            return;
        }
        if (getByPlayer(first) != null || getByPlayer(second) != null) {
            return;
        }
        Location loc = config.randomLocation();
        if (loc == null || loc.getWorld() == null) {
            first.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeComponent(
                "&#FF5C5C✖ Brak lokalizacji tepark. Uzyj /gtp addlocation"));
            return;
        }

        first.setGliding(false);
        second.setGliding(false);
        first.teleport(loc);
        second.teleport(loc);

        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        int countdownSeconds = config.getPvpCountdownSeconds();
        GroupTeleportSession session = new GroupTeleportSession(
            id,
            List.of(first.getUniqueId(), second.getUniqueId()),
            loc.clone(),
            now,
            now + config.getEndMillis()
        );
        if (countdownSeconds > 0) {
            session.setPvpUnlockAt(now + TimeUnit.SECONDS.toMillis(countdownSeconds));
        }
        sessions.put(id, session);

        if (plugin.getCombatManager() != null) {
            plugin.getCombatManager().tagPlayer(first, second);
        }

        applyElytraBlock(first);
        applyElytraBlock(second);
        Bukkit.getScheduler().runTask(plugin, () -> {
            applyElytraBlock(first);
            applyElytraBlock(second);
        });
        runPvpCountdown(first, second);
        clearCobwebs(loc);
        setupBorder(first, loc, config.getBorderRadius());
        setupBorder(second, loc, config.getBorderRadius());
        isolatePair(first, second);
        hideBotsOnTab(first);
        hideBotsOnTab(second);

        BossBar barFirst = session.createBar(
            config.color(config.getTeleportationTitle().replace("{TIME}", GroupTeleportConfig.formatTime(config.getEndMillis()))),
            config.getTeleportationColor()
        );
        BossBar barSecond = session.createBar(
            config.color(config.getTeleportationTitle().replace("{TIME}", GroupTeleportConfig.formatTime(config.getEndMillis()))),
            config.getTeleportationColor()
        );
        barFirst.addPlayer(first);
        barSecond.addPlayer(second);
        session.setFirstBar(barFirst);
        session.setSecondBar(barSecond);

        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long remaining = session.getEndTime() - System.currentTimeMillis();
            long total = session.getEndTime() - session.getStartTime();
            float progress = total <= 0 ? 0F : Math.max(0F, Math.min(1F, remaining / (float) total));

            if (remaining <= 0) {
                endSession(first, second, true);
                return;
            }

            String title = config.color(config.getTeleportationTitle()
                .replace("{TIME}", GroupTeleportConfig.formatTime(remaining)));
            if (session.getFirstBar() != null) {
                session.getFirstBar().setTitle(title);
                session.getFirstBar().setProgress(progress);
            }
            if (session.getSecondBar() != null) {
                session.getSecondBar().setTitle(title);
                session.getSecondBar().setProgress(progress);
            }
        }, 0L, 20L);
        session.setTaskId(taskId);
    }

    public void removeKill(Player attacker, Player victim) {
        removeKill(attacker, victim, true);
    }

    public void leaveToSpawn(Player player) {
        if (player == null) {
            return;
        }
        prepareLeave(player);
        GroupTeleportSession session = getByPlayer(player);
        if (session == null) {
            return;
        }
        Player other = otherPlayer(session, player);
        if (other != null) {
            removeKill(other, player, false);
            return;
        }
        endSession(player, null, false);
    }

    public void removeKill(Player attacker, Player victim, boolean teleportVictim) {
        if (attacker == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastRemoveKill.get(attacker.getUniqueId());
        if (last != null && now - last < REMOVE_KILL_COOLDOWN) {
            return;
        }
        lastRemoveKill.put(attacker.getUniqueId(), now);

        GroupTeleportSession session = getByPlayer(attacker);
        if (session == null || session.getEndTaskId() != 0) {
            return;
        }

        if (victim != null) {
            prepareLeave(victim);
            if (teleportVictim) {
                teleportToSpawn(victim);
                sendMapTeleportActionBar(victim, config.getVictimTeleportActionBar(), attacker, victim);
                pendingVictimActionBarKiller.put(victim.getUniqueId(), attacker.getName());
                Bukkit.getScheduler().runTaskLater(plugin,
                    () -> pendingVictimActionBarKiller.remove(victim.getUniqueId()), 20L * 30);
            }
            removeBar(session.getSecondBar(), victim);
            showToEveryone(victim);
        }

        session.getPlayers().removeIf(uuid -> victim != null && uuid.equals(victim.getUniqueId()));
        hideFromOtherArenas(attacker, victim);

        session.setBarrierEndTime(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(20));
        long barrierStart = System.currentTimeMillis();

        if (session.getEndTaskId() != 0) {
            return;
        }

        int endTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long remaining = session.getBarrierEndTime() - System.currentTimeMillis();
            long total = session.getBarrierEndTime() - barrierStart;
            float progress = total <= 0 ? 0F : Math.max(0F, Math.min(1F, remaining / (float) total));

            if (remaining <= 0) {
                if (attacker.isOnline()) {
                    showToEveryone(attacker);
                    prepareLeave(attacker);
                    teleportToSpawn(attacker);
                    sendMapTeleportActionBar(attacker, config.getKillerTeleportActionBar(), attacker, victim);
                    removeBar(session.getFirstBar(), attacker);
                }
                if (session.getTaskId() != 0) {
                    Bukkit.getScheduler().cancelTask(session.getTaskId());
                }
                Bukkit.getScheduler().cancelTask(session.getEndTaskId());
                sessions.remove(session.getId());
                return;
            }

            if (session.getFirstBar() != null && attacker.isOnline()) {
                session.getFirstBar().setColor(config.getEndColor());
                session.getFirstBar().setTitle(config.color(config.getEndTitle()
                    .replace("{TIME}", GroupTeleportConfig.formatTime(remaining))));
                session.getFirstBar().setProgress(progress);
            }
            if (attacker.isOnline()) {
                sendMapTeleportActionBar(attacker, config.getKillerWaitActionBar(), attacker, victim, remaining);
            }
        }, 0L, 20L);
        session.setEndTaskId(endTask);
    }

    public void endSession(Player first, Player second, boolean teleportOut) {
        GroupTeleportSession session = first != null ? getByPlayer(first) : (second != null ? getByPlayer(second) : null);
        if (session == null) {
            return;
        }
        if (session.getTaskId() != 0) {
            Bukkit.getScheduler().cancelTask(session.getTaskId());
        }
        if (session.getEndTaskId() != 0) {
            Bukkit.getScheduler().cancelTask(session.getEndTaskId());
        }
        sessions.remove(session.getId());

        if (first != null) {
            prepareLeave(first);
            removeBar(session.getFirstBar(), first);
            if (teleportOut) {
                teleportToSpawn(first);
            }
            showToEveryone(first);
        }
        if (second != null) {
            prepareLeave(second);
            removeBar(session.getSecondBar(), second);
            if (teleportOut) {
                teleportToSpawn(second);
            }
            showToEveryone(second);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (first != null && second != null && first.isOnline() && second.isOnline()) {
                first.showPlayer(plugin, second);
                second.showPlayer(plugin, first);
            }
        }, 5L);
    }

    private void runPvpCountdown(Player first, Player second) {
        int seconds = config.getPvpCountdownSeconds();
        for (int i = seconds; i >= 1; i--) {
            int left = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String msg = "&#FF5C5CPvP &#C1C1C1zostanie wlaczone za &#FF5C5C" + left + "s&#C1C1C1.";
                title(first, msg);
                title(second, msg);
            }, (seconds - i) * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            title(first, "&#60FF8BPvP zostalo wlaczone!");
            title(second, "&#60FF8BPvP zostalo wlaczone!");
        }, seconds * 20L);
    }

    private void title(Player player, String subtitle) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendTitle(" ", config.color(subtitle), 5, 25, 5);
    }

    private void isolatePair(Player first, Player second) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(first) || online.equals(second)) {
                continue;
            }
            first.hidePlayer(plugin, online);
            second.hidePlayer(plugin, online);
            online.hidePlayer(plugin, first);
            online.hidePlayer(plugin, second);
        }
        first.showPlayer(plugin, second);
        second.showPlayer(plugin, first);
    }

    private void hideFromOtherArenas(Player attacker, Player victim) {
        for (GroupTeleportSession other : sessions.values()) {
            for (UUID targetId : other.getPlayers()) {
                Player target = Bukkit.getPlayer(targetId);
                if (target == null) {
                    continue;
                }
                if (attacker != null && !target.equals(attacker)) {
                    target.hidePlayer(plugin, attacker);
                    attacker.hidePlayer(plugin, target);
                }
                if (victim != null && !target.equals(victim)) {
                    target.hidePlayer(plugin, victim);
                    victim.hidePlayer(plugin, target);
                }
            }
        }
    }

    private void showToEveryone(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            // nie pokazuj ludzi z aktywnych tepark
            if (getByPlayer(online) != null) {
                continue;
            }
            player.showPlayer(plugin, online);
            if (plugin.getVanishManager() == null || !plugin.getVanishManager().shouldHideFrom(online, player)) {
                online.showPlayer(plugin, player);
            }
        }
    }

    private void restoreAllVisibility() {
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (Player a : all) {
            for (Player b : all) {
                if (!a.equals(b)) {
                    if (plugin.getVanishManager() != null && plugin.getVanishManager().shouldHideFrom(a, b)) {
                        a.hidePlayer(plugin, b);
                    } else {
                        a.showPlayer(plugin, b);
                    }
                }
            }
        }
    }

    private void setupBorder(Player player, Location center, double radius) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(center);
        border.setSize(radius * 2.0D);
        border.setDamageBuffer(0);
        border.setDamageAmount(1);
        player.setWorldBorder(border);
    }

    public void resetBorder(Player player) {
        if (player != null) {
            player.setWorldBorder(null);
        }
    }

    public void resendVictimMapActionBar(Player player) {
        if (player == null) {
            return;
        }
        String killerName = pendingVictimActionBarKiller.remove(player.getUniqueId());
        if (killerName == null) {
            return;
        }
        String template = config.getVictimTeleportActionBar();
        if (!config.isActionbarEnabled() || template == null || template.isBlank()) {
            return;
        }
        String message = template
            .replace("{PLAYER}", player.getName())
            .replace("{player}", player.getName())
            .replace("{KILLER}", killerName)
            .replace("{killer}", killerName)
            .replace("{VICTIM}", player.getName())
            .replace("{victim}", player.getName());
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.send(player, "teparka", message, config.getActionbarDurationTicks());
            return;
        }
        player.sendActionBar(dev.grzybalson.utils.ColorUtils.colorizeComponent(message));
    }

    private void sendMapTeleportActionBar(Player player, String template, Player killer, Player victim) {
        sendMapTeleportActionBar(player, template, killer, victim, -1L);
    }

    private void sendMapTeleportActionBar(Player player, String template, Player killer, Player victim, long remainingMillis) {
        if (player == null || !player.isOnline() || !config.isActionbarEnabled()) {
            return;
        }
        if (template == null || template.isBlank()) {
            return;
        }
        String killerName = killer != null ? killer.getName() : "";
        String victimName = victim != null ? victim.getName() : "";
        String timeText = remainingMillis >= 0L ? GroupTeleportConfig.formatTime(remainingMillis) : "";
        String message = template
            .replace("{PLAYER}", player.getName())
            .replace("{player}", player.getName())
            .replace("{KILLER}", killerName)
            .replace("{killer}", killerName)
            .replace("{VICTIM}", victimName)
            .replace("{victim}", victimName)
            .replace("{TIME}", timeText)
            .replace("{time}", timeText);
        long durationTicks = remainingMillis >= 0L ? 25L : config.getActionbarDurationTicks();
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.send(player, "teparka", message, durationTicks);
            return;
        }
        player.sendActionBar(dev.grzybalson.utils.ColorUtils.colorizeComponent(message));
    }

    public void teleportToPvpSameCoords(Player player) {
        teleportToSpawn(player);
    }

    public void teleportToSpawn(Player player) {
        if (player == null) {
            return;
        }
        prepareLeave(player);
        Location spawn = resolveSpawnLocation();
        if (spawn == null) {
            return;
        }
        player.teleport(spawn);
        player.setFallDistance(0.0F);
    }

    private void prepareLeave(Player player) {
        if (player == null) {
            return;
        }
        restoreBotsOnTab(player);
        resetBorder(player);
        clearElytraBlock(player);
        if (plugin.getCombatManager() != null) {
            plugin.getCombatManager().clearCombat(player);
        }
    }

    private Player otherPlayer(GroupTeleportSession session, Player player) {
        if (session == null || player == null) {
            return null;
        }
        for (UUID id : session.getPlayers()) {
            if (id != null && !id.equals(player.getUniqueId())) {
                return Bukkit.getPlayer(id);
            }
        }
        return null;
    }

    private Location resolveSpawnLocation() {
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getSpawnConfig() != null) {
            SpawnConfig spawnConfig = plugin.getConfigManager().getSpawnConfig();
            if (spawnConfig.isSpawnSet()) {
                World world = Bukkit.getWorld(spawnConfig.getWorld());
                if (world != null) {
                    return new Location(
                        world,
                        spawnConfig.getX(),
                        spawnConfig.getY(),
                        spawnConfig.getZ(),
                        spawnConfig.getYaw(),
                        spawnConfig.getPitch()
                    );
                }
            }
        }
        World pvp = Bukkit.getWorld(config.getPvpWorld());
        if (pvp != null) {
            return pvp.getSpawnLocation();
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private void clearCobwebs(Location center) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        int y = center.getBlockY() - 1;
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                Block block = center.getWorld().getBlockAt(center.getBlockX() + x, y, center.getBlockZ() + z);
                if (block.getType() == Material.COBWEB) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void removeBar(BossBar bar, Player player) {
        if (bar != null) {
            if (player != null) {
                bar.removePlayer(player);
            }
            bar.removeAll();
        }
    }

    public boolean isPvpLocked(Player player) {
        GroupTeleportSession session = getByPlayer(player);
        return session != null && session.isPvpLocked();
    }

    public boolean shouldCancelPvp(Player attacker, Player victim) {
        if (attacker == null || victim == null || attacker.equals(victim)) {
            return false;
        }
        GroupTeleportSession attackerSession = getByPlayer(attacker);
        if (attackerSession != null && attackerSession.isPvpLocked()) {
            return true;
        }
        GroupTeleportSession victimSession = getByPlayer(victim);
        return victimSession != null && victimSession.isPvpLocked();
    }

    public Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        if (damager instanceof org.bukkit.entity.AreaEffectCloud cloud
            && cloud.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    public boolean isElytraBlocked(Player player) {
        if (player == null || !config.isBlockElytra()) {
            return false;
        }
        return isInTeleportWorld(player) || getByPlayer(player) != null;
    }

    public void applyElytraBlock(Player player) {
        if (player == null || !player.isOnline() || !config.isBlockElytra()) {
            return;
        }
        player.setGliding(false);
        player.setCooldown(Material.ELYTRA, ELYTRA_BLOCK_TICKS);
        player.setCooldown(Material.FIREWORK_ROCKET, ELYTRA_BLOCK_TICKS);
    }

    public void clearElytraBlock(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        lastElytraNotify.remove(player.getUniqueId());
        if (player.getCooldown(Material.ELYTRA) > 0) {
            player.setCooldown(Material.ELYTRA, 0);
        }
        if (player.getCooldown(Material.FIREWORK_ROCKET) > 0) {
            player.setCooldown(Material.FIREWORK_ROCKET, 0);
        }
    }

    public void denyElytra(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setGliding(false);
        applyElytraBlock(player);
        long now = System.currentTimeMillis();
        Long last = lastElytraNotify.get(player.getUniqueId());
        if (last != null && now - last < ELYTRA_NOTIFY_COOLDOWN) {
            return;
        }
        lastElytraNotify.put(player.getUniqueId(), now);
        String message = config.getElytraBlockedMessage();
        if (message != null && !message.isBlank()) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeComponent(message));
        }
    }

    public boolean isInActiveSession(Player player) {
        return player != null && getByPlayer(player) != null;
    }

    public boolean isSpawnBlocked(Player player) {
        return isInActiveSession(player);
    }

    public void sendSpawnBlockedMessage(Player player) {
        if (player == null) {
            return;
        }
        String message = config.getSpawnBlockedMessage();
        if (message != null && !message.isBlank()) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeComponent(message));
        }
    }

    public void sendSpawnBlockedOtherMessage(org.bukkit.command.CommandSender sender, Player target) {
        if (sender == null || target == null) {
            return;
        }
        String message = config.getSpawnBlockedOtherMessage();
        if (message != null && !message.isBlank()) {
            sender.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeComponent(
                message.replace("{player}", target.getName()).replace("{PLAYER}", target.getName())
            ));
        }
    }

    public boolean isInTeleportWorld(Player player) {
        return player != null
            && player.getWorld() != null
            && player.getWorld().getName().equalsIgnoreCase(config.getTeleportWorld());
    }

    private void hideBotsOnTab(Player player) {
        if (player == null) {
            return;
        }
        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().setSpooferBotsHidden(player, true);
        } else {
            dev.grzybalson.utils.SpooferBridge.hideTabBots(player);
        }
    }

    private void restoreBotsOnTab(Player player) {
        if (player == null) {
            return;
        }
        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().setSpooferBotsHidden(player, false);
        } else {
            dev.grzybalson.utils.SpooferBridge.showTabBots(player);
        }
    }
}
