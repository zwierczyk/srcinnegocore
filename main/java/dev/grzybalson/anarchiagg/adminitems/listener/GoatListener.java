package dev.grzybalson.anarchiagg.adminitems.listener;

import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsInventoryUtil;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;

import dev.grzybalson.utils.ColorUtils;

public class GoatListener
implements Listener {
    private static final int MATCH_MESSAGE_INTERVAL_TICKS = 200;
    private static final int ELYTRA_BLOCK_REFRESH_TICKS = 20;
    private static final int ELYTRA_BLOCK_COOLDOWN_TICKS = 40;
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final Set<UUID> vanishedPlayers;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> targetCooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> activeMatches = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, UUID> matchOpponents = new ConcurrentHashMap<UUID, UUID>();
    private final List<MatchSession> currentSessions = Collections.synchronizedList(new ArrayList());
    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<UUID, BukkitTask>();

    public GoatListener(HollyCore plugin) {
        this(plugin, Collections.emptySet());
    }

    public GoatListener(HollyCore plugin, Set<UUID> vanishedPlayers) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
        this.vanishedPlayers = vanishedPlayers == null ? Collections.emptySet() : vanishedPlayers;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        if (entity instanceof Player) {
            Player target = (Player)entity;
            if (!this.isKoza(event.getPlayer().getInventory().getItemInMainHand())) {
                return;
            }
            this.handleKoza(event.getPlayer(), target);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player)humanEntity;
        if (!this.isActiveMatchParticipant(player.getUniqueId())) {
            return;
        }
        this.cleanupExpiredSessions();
        if (!this.isActiveMatchParticipant(player.getUniqueId())) {
            return;
        }
        if (this.isElytraClick(event, player) || this.hasElytraEquipped(player)) {
            event.setCancelled(true);
            this.blockElytraEquip(player, true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onInventoryDrag(InventoryDragEvent event) {
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player)humanEntity;
        if (!this.isActiveMatchParticipant(player.getUniqueId())) {
            return;
        }
        this.cleanupExpiredSessions();
        if (!this.isActiveMatchParticipant(player.getUniqueId())) {
            return;
        }
        if (this.isElytra(event.getOldCursor()) || this.hasElytraEquipped(player)) {
            event.setCancelled(true);
            this.blockElytraEquip(player, true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!this.isActiveMatchParticipant(player.getUniqueId()) || !this.isElytra(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        this.blockElytraEquip(player, true);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onThirdPartyBlockCanBuild(BlockCanBuildEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        this.cleanupExpiredSessions();
        if (this.isActiveMatchParticipant(player.getUniqueId())) {
            event.setBuildable(false);
            return;
        }
        if (event.isBuildable()) {
            return;
        }
        if (this.overlapsNonMatchPlayer(event.getBlock())) {
            return;
        }
        if (this.findActiveSessionAt(event.getBlock().getLocation()) != null) {
            event.setBuildable(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMatchAreaBlockPlace(BlockPlaceEvent event) {
        this.cleanupExpiredSessions();
        if (this.isActiveMatchParticipant(event.getPlayer().getUniqueId())) {
            event.setBuild(false);
            event.setCancelled(true);
            return;
        }
        if (this.findActiveSessionAt(event.getBlockPlaced().getLocation()) == null) {
            return;
        }
        if (!this.overlapsNonMatchPlayer(event.getBlockPlaced())) {
            event.setBuild(true);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=false)
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        Player player;
        block5: {
            block4: {
                Entity entity = event.getEntity();
                if (!(entity instanceof Player)) break block4;
                player = (Player)entity;
                if (event.isGliding()) break block5;
            }
            return;
        }
        if (!this.isActiveMatchParticipant(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        this.blockElytraEquip(player, true);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onMatchAreaDamage(EntityDamageEvent event) {
        this.cancelInvalidMatchAreaDamage(event);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onMatchAreaDamageFinal(EntityDamageEvent event) {
        this.cancelInvalidMatchAreaDamage(event);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onMatchDamage(EntityDamageByEntityEvent event) {
        this.cancelInvalidMatchDamage(event);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onMatchDamageFinal(EntityDamageByEntityEvent event) {
        this.cancelInvalidMatchDamage(event);
    }

    private void cancelInvalidMatchDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player)entity;
        this.cleanupExpiredSessions();
        Player attacker = this.getAttackingPlayer(event);
        if (attacker == null) {
            if (this.findSessionByPlayer(victim.getUniqueId()) != null) {
                event.setCancelled(true);
            }
            return;
        }
        if (attacker.equals((Object)victim)) {
            return;
        }
        if (!this.isActiveMatchParticipant(attacker.getUniqueId()) && !this.isActiveMatchParticipant(victim.getUniqueId())) {
            return;
        }
        if (this.shouldCancelMatchDamage(attacker, victim)) {
            // PvpCancelTracker.mark(attacker, victim, "koza - gracz w pojedynku");
            event.setCancelled(true);
        }
    }

    private void cancelInvalidMatchAreaDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player)entity;
        this.cleanupExpiredSessions();
        MatchSession session = this.findActiveSessionAt(victim.getLocation());
        if (session != null && !session.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        this.cleanupExpiredSessions();
        // Bez vanisha i bez trwajacych pojedynkow nikt nikogo nie ukrywa, wiec pelny przebieg
        // O(online) na kazdy join bylby no-opem — przy fali wejsc robilo sie z tego O(n^2).
        if (this.vanishedPlayers.isEmpty() && this.activeMatches.isEmpty()) {
            return;
        }
        this.refreshVisibilityFor(joined);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        BukkitTask task = this.activeTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        this.findAndEndMatch(uuid);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        MatchSession session;
        Player deadPlayer = event.getEntity();
        UUID deadUUID = deadPlayer.getUniqueId();
        if (this.activeMatches.containsKey(deadUUID) && (session = this.findSessionByPlayer(deadUUID)) != null) {
            UUID opponentUUID = session.getOpponent(deadUUID);
            Player opponent = Bukkit.getPlayer((UUID)opponentUUID);
            if (opponent != null && opponent.isOnline()) {
                String winMsg = dev.grzybalson.utils.ColorUtils.colorize("&#FF2D00&l\u1d0b&#F63700&l\u1d0f&#EE4000&l\u1d22&#E54A00&l\u1d00 &8:: &aWygra\u0142e\u015b pojedynek!");
                opponent.sendMessage(winMsg);
            }
            String lossMsg = dev.grzybalson.utils.ColorUtils.colorize("&#FF2D00&l\u1d0b&#F63700&l\u1d0f&#EE4000&l\u1d22&#E54A00&l\u1d00 &8:: &cPrzegra\u0142e\u015b pojedynek!");
            deadPlayer.sendMessage(lossMsg);
            this.cleanupMatch(session.attacker, session.target, session);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private MatchSession findSessionByPlayer(UUID uuid) {
        List<MatchSession> list = this.currentSessions;
        synchronized (list) {
            for (MatchSession session : this.currentSessions) {
                if (!session.contains(uuid)) continue;
                return session;
            }
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private MatchSession findActiveSessionAt(Location location) {
        List<MatchSession> list = this.currentSessions;
        synchronized (list) {
            for (MatchSession session : this.currentSessions) {
                if (session.isExpired() || !session.contains(location)) continue;
                return session;
            }
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void findAndEndMatch(UUID uuid) {
        MatchSession endedSession = null;
        List<MatchSession> list = this.currentSessions;
        synchronized (list) {
            Iterator<MatchSession> iterator = this.currentSessions.iterator();
            while (iterator.hasNext()) {
                Player opponent;
                MatchSession session = iterator.next();
                if (!session.contains(uuid)) continue;
                UUID opponentUUID = session.getOpponent(uuid);
                if (opponentUUID != null && (opponent = Bukkit.getPlayer((UUID)opponentUUID)) != null && opponent.isOnline()) {
                    String msg = dev.grzybalson.utils.ColorUtils.colorize("&#FF2D00&l\u1d0b&#F63700&l\u1d0f&#EE4000&l\u1d22&#E54A00&l\u1d00 &8:: &ePojedynek zosta\u0142 zako\u0144czony, poniewa\u017c przeciwnik opu\u015bci\u0142 serwer!");
                    opponent.sendMessage(msg);
                }
                endedSession = session;
                iterator.remove();
                break;
            }
        }
        if (endedSession != null) {
            Player attacker = Bukkit.getPlayer((UUID)endedSession.attacker);
            Player target = Bukkit.getPlayer((UUID)endedSession.target);
            this.applyTargetCooldown(endedSession.target);
            this.clearMatchState(endedSession.attacker, endedSession.target);
            this.cancelTasks(endedSession.attacker, endedSession.target);
            this.endMatch(attacker, target);
        }
    }

    private void cancelTasks(UUID ... uuids) {
        for (UUID uuid : uuids) {
            BukkitTask task = this.activeTasks.remove(uuid);
            if (task == null || task.isCancelled()) continue;
            task.cancel();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void cleanupExpiredSessions() {
        ArrayList<MatchSession> expiredSessions = new ArrayList<MatchSession>();
        boolean changed = false;
        List<MatchSession> list = this.currentSessions;
        synchronized (list) {
            Iterator<MatchSession> iterator = this.currentSessions.iterator();
            while (iterator.hasNext()) {
                MatchSession session = iterator.next();
                if (!session.isExpired()) continue;
                expiredSessions.add(session);
                iterator.remove();
                changed = true;
            }
        }
        for (MatchSession session : expiredSessions) {
            Player attacker = Bukkit.getPlayer((UUID)session.attacker);
            Player target = Bukkit.getPlayer((UUID)session.target);
            this.applyTargetCooldown(session.target);
            this.clearMatchState(session.attacker, session.target);
            this.cancelTasks(session.attacker, session.target);
            this.endMatch(attacker, target);
        }
        if (this.cleanupOrphanMatchState()) {
            changed = true;
        }
        if (changed) {
            this.refreshAllVisibility();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean cleanupOrphanMatchState() {
        long now = System.currentTimeMillis();
        HashSet<UUID> sessionPlayers = new HashSet<UUID>();
        boolean changed = false;
        synchronized (this.currentSessions) {
            for (MatchSession session : this.currentSessions) {
                sessionPlayers.add(session.attacker);
                sessionPlayers.add(session.target);
            }
        }
        for (UUID uuid : new ArrayList<UUID>(this.activeMatches.keySet())) {
            Long endTimestamp = this.activeMatches.get(uuid);
            if (endTimestamp != null && endTimestamp > now && sessionPlayers.contains(uuid)) continue;
            this.clearSingleMatchState(uuid);
            changed = true;
        }
        for (UUID uuid : new ArrayList<UUID>(this.matchOpponents.keySet())) {
            if (this.activeMatches.containsKey(uuid)) continue;
            this.clearSingleMatchState(uuid);
            changed = true;
        }
        return changed;
    }

    private void clearSingleMatchState(UUID uuid) {
        UUID opponent = this.matchOpponents.remove(uuid);
        this.activeMatches.remove(uuid);
        BukkitTask task = this.activeTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        this.resetPlayerAfterMatch(uuid);
        if (opponent == null) {
            return;
        }
        this.matchOpponents.remove(opponent);
        this.activeMatches.remove(opponent);
        BukkitTask opponentTask = this.activeTasks.remove(opponent);
        if (opponentTask != null && !opponentTask.isCancelled()) {
            opponentTask.cancel();
        }
        this.resetPlayerAfterMatch(opponent);
    }

    private void resetPlayerAfterMatch(UUID uuid) {
        Player player = Bukkit.getPlayer((UUID)uuid);
        if (player != null && player.isOnline()) {
            player.setWorldBorder(null);
        }
    }

    private void handleKoza(Player attacker, Player target) {
        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!this.isKoza(item)) {
            return;
        }
        this.cleanupExpiredSessions();
        AdminItemsConfig.GoatSettings settings = this.config.getGoat();
        if (settings == null) {
            dev.grzybalson.anarchiagg.util.TextUtil.sendConsole("&eKoza settings not found in config!");
            return;
        }
        if (!settings.isEnabled()) {
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cTen przedmiot jest wylaczony!"));
            return;
        }
        if (attacker.equals((Object)target)) {
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cNie mo\u017cesz u\u017cy\u0107 tego przedmiotu na sobie!"));
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(attacker, this.plugin) || AdminItemsRegionUtil.isInBlockedRegion(target, this.plugin)) {
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cNie mo\u017cesz u\u017cywa\u0107 tego przedmiotu na spawnie!"));
            return;
        }
        UUID attackerUUID = attacker.getUniqueId();
        UUID targetUUID = target.getUniqueId();
        if (this.isActiveMatchParticipant(attackerUUID) || this.isActiveMatchParticipant(targetUUID)) {
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cJeden z graczy jest ju\u017c w pojedynku!"));
            return;
        }
        long now = System.currentTimeMillis();
        this.clearExpiredTargetCooldowns(now);
        long targetCooldown = this.targetCooldowns.getOrDefault(targetUUID, 0L);
        if (targetCooldown > now) {
            long secondsLeft = Math.max(1L, (targetCooldown - now + 999L) / 1000L);
            attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cTen gracz byl juz na kozie! Poczekaj jeszcze &4" + secondsLeft + "s"));
            return;
        }
        long cooldownMillis = AdminItemsCooldowns.secondsToMillis(settings.getCooldown());
        Long cooldownUntil = this.cooldowns.get(attackerUUID);
        if (cooldownUntil != null) {
            long timeLeft = cooldownUntil - now;
            if (timeLeft > 0L) {
                AdminItemsCooldowns.refresh(attacker, item.getType(), cooldownUntil);
                attacker.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(settings.getName() + " &8:: &cMusisz poczeka\u0107 jeszcze &4" + timeLeft / 1000L + "s"));
                return;
            }
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.cooldowns, attackerUUID, cooldownUntil);
        }
        this.cooldowns.put(attackerUUID, now + cooldownMillis);
        AdminItemsCooldowns.apply(attacker, item.getType(), cooldownMillis);
        this.stripElytraAndHandleInventory(attacker);
        this.stripElytraAndHandleInventory(target);
        Location rawCenter = attacker.getLocation().add(target.getLocation()).multiply(0.5);
        Location blockAlignedCenter = new Location(rawCenter.getWorld(), Math.floor(rawCenter.getX()) + 0.5, rawCenter.getY(), Math.floor(rawCenter.getZ()) + 0.5);
        int borderSize = (int)settings.getBorderSize();
        if (borderSize % 2 != 0) {
            ++borderSize;
        }
        double maxRadius = (double)borderSize / 2.0;
        int duration = settings.getDuration();
        long durationMillis = (long)duration * 1000L;
        long endTimestamp = now + durationMillis;
        WorldBorder worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(blockAlignedCenter);
        worldBorder.setSize((double)borderSize);
        worldBorder.setSize((double)borderSize - 0.1, (long)duration);
        worldBorder.setDamageAmount(0.0);
        worldBorder.setDamageBuffer(0.0);
        worldBorder.setWarningDistance(0);
        worldBorder.setWarningTime(0);
        attacker.setWorldBorder(worldBorder);
        target.setWorldBorder(worldBorder);
        MatchSession session = new MatchSession(attackerUUID, targetUUID, blockAlignedCenter, maxRadius, endTimestamp);
        this.currentSessions.add(session);
        this.activeMatches.put(attackerUUID, endTimestamp);
        this.activeMatches.put(targetUUID, endTimestamp);
        this.matchOpponents.put(attackerUUID, targetUUID);
        this.matchOpponents.put(targetUUID, attackerUUID);
        this.refreshAllVisibility();
        BukkitTask matchTask = this.startMatchTask(attacker, target, session);
        this.activeTasks.put(attackerUUID, matchTask);
        this.activeTasks.put(targetUUID, matchTask);
    }

    private BukkitTask startMatchTask(final Player attacker, final Player target, final MatchSession session) {
        final UUID attackerUUID = attacker.getUniqueId();
        final UUID targetUUID = target.getUniqueId();
        return new BukkitRunnable(){
            int ticksLeft;
            {
                this.ticksLeft = GoatListener.this.config.getGoat().getDuration() * 20;
            }

            public void run() {
                if (!GoatListener.this.activeMatches.containsKey(attackerUUID) || !GoatListener.this.activeMatches.containsKey(targetUUID)) {
                    GoatListener.this.cleanupMatch(attackerUUID, targetUUID, session);
                    this.cancel();
                    return;
                }
                if (!attacker.isOnline() || !target.isOnline() || this.ticksLeft <= 0) {
                    GoatListener.this.cleanupMatch(attackerUUID, targetUUID, session);
                    this.cancel();
                    return;
                }
                if (session.isExpired()) {
                    GoatListener.this.cleanupMatch(attackerUUID, targetUUID, session);
                    this.cancel();
                    return;
                }
                GoatListener.this.refreshFlightCooldown(attacker);
                GoatListener.this.refreshFlightCooldown(target);
                GoatListener.this.refreshVisibilityForMatch(attacker, target);
                // Poza personal borderem client nie wysyla melee — wracaj do srodka.
                GoatListener.this.clampInsideMatchBorder(attacker);
                GoatListener.this.clampInsideMatchBorder(target);
                if (this.ticksLeft % 200 == 0 && this.ticksLeft > 0) {
                    int secondsLeft = this.ticksLeft / 20;
                    String timeMsg = dev.grzybalson.utils.ColorUtils.colorize("&#FF2D00&l\u1d0b&#F63700&l\u1d0f&#EE4000&l\u1d22&#E54A00&l\u1d00 &8:: &cDo ko\u0144ca pojedynku pozosta\u0142o: &4" + secondsLeft + "s");
                    attacker.sendMessage(timeMsg);
                    target.sendMessage(timeMsg);
                }
                this.ticksLeft -= 20;
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 20L);
    }

    private void refreshFlightCooldown(Player player) {
        player.setCooldown(Material.ELYTRA, 40);
        player.setCooldown(Material.FIREWORK_ROCKET, 40);
    }

    private void clampInsideMatchBorder(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        WorldBorder border = player.getWorldBorder();
        if (border == null) {
            return;
        }
        Location loc = player.getLocation();
        boolean outside;
        try {
            outside = !border.isInside(loc);
        } catch (Throwable ignored) {
            Location center = border.getCenter();
            double half = border.getSize() * 0.5D;
            outside = Math.abs(loc.getX() - center.getX()) > half || Math.abs(loc.getZ() - center.getZ()) > half;
        }
        if (!outside) {
            return;
        }
        Location center = border.getCenter();
        double maxOffset = Math.max(0.0D, border.getSize() * 0.5D - 0.75D);
        double x = Math.max(center.getX() - maxOffset, Math.min(center.getX() + maxOffset, loc.getX()));
        double z = Math.max(center.getZ() - maxOffset, Math.min(center.getZ() + maxOffset, loc.getZ()));
        Location safe = new Location(loc.getWorld(), x, loc.getY(), z, loc.getYaw(), loc.getPitch());
        player.teleport(safe);
        player.setNoDamageTicks(0);
    }

    private void blockElytraEquip(Player player, boolean notify) {
        player.setGliding(false);
        this.refreshFlightCooldown(player);
        this.stripElytraAndHandleInventory(player);
        player.updateInventory();
        if (notify) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.config.getGoat().getName() + " &8:: &cNie mozesz zakladac elytry podczas kozy!"));
        }
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (!this.isActiveMatchParticipant(player.getUniqueId())) {
                return;
            }
            player.setGliding(false);
            this.refreshFlightCooldown(player);
            this.stripElytraAndHandleInventory(player);
            player.updateInventory();
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void cleanupMatch(UUID attackerUUID, UUID targetUUID, MatchSession session) {
        List<MatchSession> list = this.currentSessions;
        synchronized (list) {
            this.currentSessions.remove(session);
        }
        this.applyTargetCooldown(targetUUID);
        this.clearMatchState(attackerUUID, targetUUID);
        this.cancelTasks(attackerUUID, targetUUID);
        Player attacker = Bukkit.getPlayer((UUID)attackerUUID);
        Player target = Bukkit.getPlayer((UUID)targetUUID);
        this.endMatch(attacker, target);
    }

    private void stripElytraAndHandleInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack chestplate = inv.getChestplate();
        if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
            return;
        }
        boolean hasFreeSlot = false;
        for (int i = 0; i < 36; ++i) {
            if (inv.getItem(i) != null && inv.getItem(i).getType() != Material.AIR) continue;
            hasFreeSlot = true;
            break;
        }
        inv.setChestplate(null);
        if (!hasFreeSlot) {
            if (!AdminItemsInventoryUtil.moveItemToStorageTotemSlot(player, chestplate)) {
                ItemCompatibilityUtil.dropItemNaturally(player, chestplate);
            }
        } else {
            HashMap<Integer, ItemStack> leftOver = inv.addItem(new ItemStack[]{chestplate});
            if (!leftOver.isEmpty()) {
                for (ItemStack drop : leftOver.values()) {
                    ItemCompatibilityUtil.dropItemNaturally(player, drop);
                }
            }
        }
    }

    private boolean hasElytraEquipped(Player player) {
        return player != null && this.isElytra(player.getInventory().getChestplate());
    }

    private boolean isElytraClick(InventoryClickEvent event, Player player) {
        if (this.isElytra(event.getCursor()) || this.isElytra(event.getCurrentItem())) {
            return true;
        }
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0 && this.isElytra(player.getInventory().getItem(hotbarButton))) {
            return true;
        }
        return event.getClick() == ClickType.SWAP_OFFHAND && this.isElytra(player.getInventory().getItemInOffHand());
    }

    private boolean isElytra(ItemStack item) {
        return item != null && item.getType() == Material.ELYTRA;
    }

    private void endMatch(Player attacker, Player target) {
        if (attacker != null && attacker.isOnline()) {
            attacker.setWorldBorder(null);
        }
        if (target != null && target.isOnline()) {
            target.setWorldBorder(null);
        }
        this.refreshAllVisibility();
        if (attacker != null && attacker.isOnline() && target != null && target.isOnline()) {
            String endMsg = dev.grzybalson.utils.ColorUtils.colorize("&#FF2D00&l\u1d0b&#F63700&l\u1d0f&#EE4000&l\u1d22&#E54A00&l\u1d00 &8:: &cPojedynek zako\u0144czony!");
            attacker.sendMessage(endMsg);
            target.sendMessage(endMsg);
        }
    }

    private void clearMatchState(UUID attackerUUID, UUID targetUUID) {
        this.activeMatches.remove(attackerUUID);
        this.activeMatches.remove(targetUUID);
        this.matchOpponents.remove(attackerUUID);
        this.matchOpponents.remove(targetUUID);
    }

    private void applyTargetCooldown(UUID targetUUID) {
        this.targetCooldowns.put(targetUUID, System.currentTimeMillis() + AdminItemsCooldowns.secondsToMillis(Math.max(0, this.config.getGoat().getTargetCooldown())));
    }

    private void clearExpiredTargetCooldowns(long now) {
        this.targetCooldowns.entrySet().removeIf(entry -> (Long)entry.getValue() <= now);
    }

    private void refreshVisibilityForMatch(Player first, Player second) {
        if (first != null && first.isOnline()) {
            this.refreshVisibilityFor(first);
        }
        if (second != null && second.isOnline()) {
            this.refreshVisibilityFor(second);
        }
    }

    private void refreshVisibilityFor(Player player) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals((Object)player)) continue;
            this.updateVisibility(player, onlinePlayer);
            this.updateVisibility(onlinePlayer, player);
        }
    }

    private void refreshAllVisibility() {
        ArrayList<Player> players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        for (Player viewer : players) {
            for (Player subject : players) {
                if (viewer.equals((Object)subject)) continue;
                this.updateVisibility(viewer, subject);
            }
        }
    }

    private void updateVisibility(Player viewer, Player subject) {
        if (this.shouldHidePlayer(viewer, subject)) {
            if (viewer.canSee(subject)) {
                viewer.hidePlayer((Plugin)this.plugin, subject);
            }
            return;
        }
        if (!viewer.canSee(subject)) {
            viewer.showPlayer((Plugin)this.plugin, subject);
        }
    }

    private boolean shouldHidePlayer(Player viewer, Player subject) {
        UUID subjectUUID = subject.getUniqueId();
        if (this.vanishedPlayers.contains(subjectUUID) && !viewer.hasPermission("Holly.cmd.vanish") && !viewer.hasPermission("Holly.vanish.see")) {
            return true;
        }
        return this.shouldHideForMatch(viewer, subject);
    }

    private boolean shouldHideForMatch(Player viewer, Player subject) {
        UUID viewerUUID = viewer.getUniqueId();
        UUID subjectUUID = subject.getUniqueId();
        boolean viewerInMatch = this.isActiveMatchParticipant(viewerUUID);
        boolean subjectInMatch = this.isActiveMatchParticipant(subjectUUID);
        if (!viewerInMatch && !subjectInMatch) {
            return false;
        }
        if (viewerInMatch) {
            return !subjectUUID.equals(this.matchOpponents.get(viewerUUID));
        }
        return true;
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

    private boolean overlapsNonMatchPlayer(Block block) {
        if (block == null || block.getWorld() == null) {
            return false;
        }
        BoundingBox blockBox = new BoundingBox((double)block.getX(), (double)block.getY(), (double)block.getZ(), (double)block.getX() + 1.0, (double)block.getY() + 1.0, (double)block.getZ() + 1.0);
        for (Player player : block.getWorld().getPlayers()) {
            if (player.isDead() || this.isActiveMatchParticipant(player.getUniqueId()) || !player.getBoundingBox().overlaps(blockBox)) continue;
            return true;
        }
        return false;
    }

    private boolean shouldCancelMatchDamage(Player attacker, Player victim) {
        UUID attackerUUID = attacker.getUniqueId();
        UUID victimUUID = victim.getUniqueId();
        boolean attackerInMatch = this.isActiveMatchParticipant(attackerUUID);
        boolean victimInMatch = this.isActiveMatchParticipant(victimUUID);
        if (!attackerInMatch && !victimInMatch) {
            return false;
        }
        UUID attackerOpponent = this.matchOpponents.get(attackerUUID);
        UUID victimOpponent = this.matchOpponents.get(victimUUID);
        return !attackerInMatch || !victimInMatch || !victimUUID.equals(attackerOpponent) || !attackerUUID.equals(victimOpponent);
    }

    public boolean isActiveMatchParticipant(UUID uuid) {
        Long endTimestamp = this.activeMatches.get(uuid);
        return endTimestamp != null && endTimestamp > System.currentTimeMillis() && this.matchOpponents.containsKey(uuid);
    }

    public boolean canDamage(Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return true;
        }
        this.cleanupExpiredSessions();
        return !this.shouldCancelMatchDamage(attacker, victim);
    }

    public boolean canDamageCached(Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return true;
        }
        return !this.shouldCancelMatchDamage(attacker, victim);
    }

    private boolean isKoza(ItemStack item) {
        if (item == null || item.getType() != Material.SHULKER_SHELL || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && AdminItemsGUI.KEY_KOZA != null && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_KOZA, PersistentDataType.BYTE);
    }

    public Map<UUID, Long> getActiveMatches() {
        this.cleanupExpiredSessions();
        return Collections.unmodifiableMap(this.activeMatches);
    }

    public boolean hasActiveMatches() {
        return !this.activeMatches.isEmpty();
    }

    public Long getActiveMatchEndTimestamp(UUID uuid) {
        return this.activeMatches.get(uuid);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void cleanup() {
        ArrayList<MatchSession> sessionsToEnd;
        synchronized (this.currentSessions) {
            sessionsToEnd = new ArrayList<MatchSession>(this.currentSessions);
            this.currentSessions.clear();
        }
        for (BukkitTask task : this.activeTasks.values()) {
            if (task == null || task.isCancelled()) continue;
            task.cancel();
        }
        this.activeTasks.clear();
        this.activeMatches.clear();
        this.matchOpponents.clear();
        this.cooldowns.clear();
        this.targetCooldowns.clear();
        for (MatchSession session : sessionsToEnd) {
            Player attacker = Bukkit.getPlayer((UUID)session.attacker);
            Player target = Bukkit.getPlayer((UUID)session.target);
            this.endMatch(attacker, target);
        }
    }

    private static class MatchSession {
        final UUID attacker;
        final UUID target;
        final Location center;
        final double maxRadius;
        final long endTimestamp;

        MatchSession(UUID attacker, UUID target, Location center, double maxRadius, long endTimestamp) {
            this.attacker = attacker;
            this.target = target;
            this.center = center.clone();
            this.maxRadius = maxRadius;
            this.endTimestamp = endTimestamp;
        }

        boolean contains(UUID uuid) {
            return this.attacker.equals(uuid) || this.target.equals(uuid);
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= this.endTimestamp;
        }

        UUID getOpponent(UUID uuid) {
            if (this.attacker.equals(uuid)) {
                return this.target;
            }
            if (this.target.equals(uuid)) {
                return this.attacker;
            }
            return null;
        }

        boolean contains(Location location) {
            if (location == null || location.getWorld() == null || this.center.getWorld() == null) {
                return false;
            }
            if (!location.getWorld().equals((Object)this.center.getWorld())) {
                return false;
            }
            double blockCenterX = (double)location.getBlockX() + 0.5;
            double blockCenterZ = (double)location.getBlockZ() + 0.5;
            return Math.abs(blockCenterX - this.center.getX()) <= this.maxRadius && Math.abs(blockCenterZ - this.center.getZ()) <= this.maxRadius;
        }
    }
}
