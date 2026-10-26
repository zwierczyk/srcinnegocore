package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;
import java.util.UUID;

public final class DungeonGameplayListener implements Listener {

    private final HollyCore plugin;
    private final DungeonSessionManager manager;

    public DungeonGameplayListener(HollyCore plugin, DungeonSessionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
            && event.getFrom().getBlockY() == event.getTo().getBlockY()
            && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        DungeonSession session = manager.getByPlayer(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        if (session.isMovementLocked()) {
            event.setTo(event.getFrom());
            return;
        }
        if (session.getPhase() == DungeonSession.Phase.TUTORIAL
            && event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        DungeonSession session = manager.getByPlayer(event.getPlayer().getUniqueId());
        if (session != null && session.getPhase() == DungeonSession.Phase.TUTORIAL) {
            event.setCancelled(true);
            event.getPlayer().setFlying(false);
            event.getPlayer().setFlySpeed(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onNinjaDash(PlayerInteractEvent event) {
        // AIR czasem ma hand=null; OFF_HAND tez bywa jedynym eventem — akceptuj main hand item.
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
            && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != org.bukkit.Material.DIAMOND_SWORD) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 1) {
            return;
        }
        DungeonSession session = manager.getByPlayer(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.tryDash(player)) {
            event.setCancelled(true);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onNinjaDashAirFallback(PlayerInteractEvent event) {
        // Fallback gdy inny plugin canceluje RIGHT_CLICK_AIR zanim dojdziemy.
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR) {
            return;
        }
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != org.bukkit.Material.DIAMOND_SWORD) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != 1) {
            return;
        }
        DungeonSession session = manager.getByPlayer(player.getUniqueId());
        if (session != null) {
            session.tryDash(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDungeonDamageStats(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (!victim.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        DungeonSession session = manager.getByPlayer(attacker.getUniqueId());
        if (session == null) {
            return;
        }
        session.recordDamage(attacker.getUniqueId(), event.getFinalDamage());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        DungeonSession session = manager.getByPlayer(victim.getUniqueId());
        if (session != null && session.isParticipant(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Bossy dungeonowe — mocniejsze uderzenia (Protection mocno tnie). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBossHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity living) {
            attacker = living;
        } else if (event.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof LivingEntity living) {
            attacker = living;
        }
        if (attacker == null || !attacker.getScoreboardTags().contains("holly_dungeon_boss")) {
            return;
        }
        DungeonSession session = manager.getByPlayer(victim.getUniqueId());
        if (session == null || session.isDeadSpectator(victim.getUniqueId())) {
            return;
        }
        int lvl = session.getLevel();
        double mult = lvl >= 3 ? 1.75D : lvl == 2 ? 1.45D : 1.25D;
        double minBase = lvl >= 3 ? 10.0D : lvl == 2 ? 7.5D : 5.5D;
        event.setDamage(session.scaleMobDamage(Math.max(event.getDamage() * mult, minBase)));
    }

    /** Fale / elite — floor dmg, zeby Protection nie zerowal uderzen. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onWaveMobHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity living) {
            attacker = living;
        } else if (event.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof LivingEntity living) {
            attacker = living;
        }
        if (attacker == null
            || !attacker.getScoreboardTags().contains("holly_dungeon_mob")
            || attacker.getScoreboardTags().contains("holly_dungeon_boss")) {
            return;
        }
        DungeonSession session = manager.getByPlayer(victim.getUniqueId());
        if (session == null || session.isDeadSpectator(victim.getUniqueId())) {
            return;
        }
        int lvl = session.getLevel();
        int tickets = Math.max(1, session.getTicketMultiplier());
        boolean elite = attacker.getScoreboardTags().stream()
            .anyMatch(t -> t.contains("elite") || t.contains("bounty"));
        double mult = elite
            ? (lvl >= 3 ? 1.55D : lvl == 2 ? 1.40D : 1.30D)
            : (lvl >= 3 ? 1.45D : lvl == 2 ? 1.30D : 1.20D);
        double minBase = elite
            ? (lvl >= 3 ? 7.0D : lvl == 2 ? 5.5D : 4.5D)
            : (lvl >= 3 ? 5.5D : lvl == 2 ? 4.5D : 3.5D);
        minBase += Math.max(0, tickets - 1) * 0.6D;
        event.setDamage(session.scaleMobDamage(Math.max(event.getDamage() * mult, minBase)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCorpseArmorStand(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains("holly_dungeon_corpse")
            || event.getRightClicked().getScoreboardTags().stream().anyMatch(t -> t.startsWith("holly_corpse_"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFall(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DungeonSession session = manager.getByPlayer(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        // fallDistance bywa juz zresetowany w evencie — szacuj tez z vanilla dmg (dmg ≈ fall-3).
        double estimatedBlocks = Math.max(player.getFallDistance(), event.getDamage() + 3.0D);
        if (estimatedBlocks >= 8.0D) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                DungeonSession current = manager.getByPlayer(player.getUniqueId());
                if (current == null || current.isDeadSpectator(player.getUniqueId())) {
                    return;
                }
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
                player.setHealth(0.0D);
            });
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFallTrackLand(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        DungeonSession session = manager.getByPlayer(player.getUniqueId());
        if (session == null || session.isDeadSpectator(player.getUniqueId())) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.isDead() || player.isFlying()) {
            return;
        }
        if (!player.isOnGround() || player.getFallDistance() < 8.0F) {
            return;
        }
        player.setFallDistance(0.0F);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            DungeonSession current = manager.getByPlayer(player.getUniqueId());
            if (current == null || current.isDeadSpectator(player.getUniqueId())) {
                return;
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            player.setHealth(0.0D);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBossTransform(EntityTransformEvent event) {
        if (event.getEntity().getScoreboardTags().contains("holly_dungeon_boss")
            || event.getEntity().getScoreboardTags().contains("holly_dungeon_mob")) {
            event.setCancelled(true);
        }
    }

    /** Zero creeper/TNT grief w swiecie dungeona. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDungeonExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (manager.getByWorld(event.getEntity().getWorld().getName()) == null) {
            return;
        }
        event.setCancelled(true);
        event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDungeonExplosionPrime(org.bukkit.event.entity.ExplosionPrimeEvent event) {
        if (manager.getByWorld(event.getEntity().getWorld().getName()) == null) {
            return;
        }
        if (event.getEntity() instanceof org.bukkit.entity.Creeper
            || event.getEntity().getScoreboardTags().contains("holly_dungeon_mob")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMobFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (!victim.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity living) {
            attacker = living;
        } else if (event.getDamager() instanceof Projectile projectile
            && projectile.getShooter() instanceof LivingEntity living) {
            attacker = living;
        }
        if (attacker != null && attacker.getScoreboardTags().contains("holly_dungeon_mob")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onIdleMobHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        // Tylko melee idle — strzaly/shurikeny zawsze maja bil.
        if (!(event.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        if (!attacker.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        DungeonSession session = manager.getByWorld(attacker.getWorld().getName());
        if (session != null
            && session.getPhase() == DungeonSession.Phase.WAVE
            && !session.isFocusedMob(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDungeonArrowLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof LivingEntity shooter)) {
            return;
        }
        if (!shooter.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        if (manager.getByWorld(shooter.getWorld().getName()) == null) {
            return;
        }
        arrow.addScoreboardTag("holly_dungeon_arrow");
        try {
            arrow.setBounce(false);
            arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDungeonArrow(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof LivingEntity shooter)) {
            return;
        }
        if (!shooter.getScoreboardTags().contains("holly_dungeon_mob")
            && !arrow.getScoreboardTags().contains("holly_dungeon_arrow")) {
            return;
        }
        DungeonSession session = manager.getByWorld(shooter.getWorld().getName());
        if (session == null) {
            return;
        }
        // Bez dmg przy trafieniu w entity — wybuch dopiero o ziemie.
        event.setCancelled(true);
        arrow.addScoreboardTag("holly_dungeon_arrow");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDungeonArrowHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.AbstractArrow arrow)) {
            return;
        }
        boolean tagged = arrow.getScoreboardTags().contains("holly_dungeon_arrow");
        boolean fromDungeon = arrow.getShooter() instanceof LivingEntity living
            && living.getScoreboardTags().contains("holly_dungeon_mob");
        if (!tagged && !fromDungeon) {
            return;
        }
        if (arrow.getScoreboardTags().contains("holly_dungeon_arrow_done")) {
            arrow.remove();
            return;
        }
        arrow.addScoreboardTag("holly_dungeon_arrow_done");
        arrow.addScoreboardTag("holly_dungeon_arrow");

        // Trafienie w moba/gracza — bez wybuchu, tylko usun strzale.
        if (event.getHitBlock() == null) {
            if (!arrow.isDead()) {
                arrow.remove();
            }
            return;
        }

        // Wybuch DOKLADNIE tam, gdzie stoi entity strzaly w momencie walniecia w ziemie.
        Location impact = arrow.getLocation().clone();

        DungeonSession session = manager.getByWorld(impact.getWorld().getName());
        if (session == null) {
            arrow.remove();
            return;
        }
        session.handleExplosiveArrow(arrow, impact);
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onSpectatorPaidRevive(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
            && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        DungeonSession session = manager.getByPlayer(player.getUniqueId());
        if (session == null || !session.isDeadSpectator(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        session.tryPaidRevive(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerHitIdleMob(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (!victim.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        DungeonSession session = manager.getByPlayer(attacker.getUniqueId());
        if (session == null) {
            return;
        }
        session.engageMob(victim);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !mob.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        DungeonSession session = manager.getByWorld(mob.getWorld().getName());
        if (session == null) {
            return;
        }
        LivingEntity target = event.getTarget();
        if (target != null && target.getScoreboardTags().contains("holly_dungeon_mob")) {
            Player player = nearestAlive(session, mob);
            if (player != null) {
                event.setTarget(player);
            } else {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getTarget() instanceof Player player) {
            if (!session.isParticipant(player.getUniqueId())
                || session.isDeadSpectator(player.getUniqueId())
                || player.getGameMode() == GameMode.SPECTATOR) {
                Player next = nearestAlive(session, mob);
                if (next != null) {
                    event.setTarget(next);
                } else {
                    event.setCancelled(true);
                }
            }
        }
        if (phaseWaveIdle(session, mob)) {
            event.setCancelled(true);
            mob.setTarget(null);
        }
    }

    private boolean phaseWaveIdle(DungeonSession session, Mob mob) {
        return session.getPhase() == DungeonSession.Phase.WAVE && !session.isFocusedMob(mob.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onShuriken(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Snowball snowball)) {
            return;
        }
        if (!(snowball.getShooter() instanceof LivingEntity shooter)) {
            return;
        }
        if (!shooter.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity living
            && living.getScoreboardTags().contains("holly_dungeon_mob")) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        DungeonSession session = manager.getByWorld(shooter.getWorld().getName());
        if (session == null) {
            return;
        }
        event.setDamage(session.scaleMobDamage(4.0D + session.getPhase().ordinal()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.getScoreboardTags().contains("holly_dungeon_mob")) {
            return;
        }
        DungeonSession session = manager.getByWorld(entity.getWorld().getName());
        if (session == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        session.onMobDeath(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFatalDamageSaveInv(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DungeonSession session = manager.getByPlayer(player.getUniqueId());
        if (session == null || session.isDeadSpectator(player.getUniqueId())) {
            return;
        }
        double remaining = player.getHealth() - event.getFinalDamage();
        if (remaining > 0.0D) {
            return;
        }
        // Przed smiercia — pewny snapshot ekwipunku.
        session.saveInventory(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerDeathEarly(PlayerDeathEvent event) {
        DungeonSession session = manager.getByPlayer(event.getEntity().getUniqueId());
        if (session == null) {
            return;
        }
        session.saveInventory(event.getEntity());
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDeath(PlayerDeathEvent event) {
        DungeonSession session = manager.getByPlayer(event.getEntity().getUniqueId());
        if (session == null) {
            return;
        }
        Player dead = event.getEntity();
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDeathMessage(null);
        manager.markIgnoreWorldQuit(dead.getUniqueId(), 40L);
        Location deathLoc = dead.getLocation().clone();
        session.handlePlayerDeath(dead);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (deathLoc.getWorld() == null) {
                return;
            }
            for (org.bukkit.entity.Entity entity : deathLoc.getWorld().getNearbyEntities(deathLoc, 3.5D, 3.5D, 3.5D)) {
                if (entity instanceof org.bukkit.entity.Item item) {
                    item.remove();
                }
            }
            if (dead.isOnline()) {
                session.restoreInventory(dead);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonItemDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        DungeonSession session = manager.getByPlayer(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        // W trakcie smierci / spectatora nic nie wypadnie na ziemie.
        if (event.getPlayer().isDead() || session.isDeadSpectator(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        // Q z katana = ping. Po dropie main-hand bywa pusty — bierz item z eventu.
        ItemStack dropped = event.getItemDrop().getItemStack();
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (DungeonSession.isKatana(dropped) || DungeonSession.isKatana(hand)) {
            event.setCancelled(true);
            session.tryPartyPing(event.getPlayer(), DungeonSession.isKatana(dropped) ? dropped : hand);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        DungeonSession session = manager.getByPlayer(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        Location respawn = session.getRespawnLocation();
        if (respawn != null) {
            event.setRespawnLocation(respawn);
        }
        manager.markIgnoreWorldQuit(event.getPlayer().getUniqueId(), 40L);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            DungeonSession current = manager.getByPlayer(player.getUniqueId());
            if (current == null || !current.isDeadSpectator(player.getUniqueId())) {
                return;
            }
            // Wipe GUI wymaga ADVENTURE — nie wpychaj SPECTATOR w trakcie oferty.
            if (current.isWipeReviveOpen()) {
                return;
            }
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        DungeonSession session = manager.getByPlayer(player.getUniqueId());
        if (session != null) {
            // Zanim Bungee / DC wyniesie EQ — zdejmij kit dungeona.
            manager.stripDungeonInventory(player);
            session.handlePlayerQuit(player, true);
        }
        // Zawsze zdejmij GM3 przy wyjściu jesli nie w aktywnej sesji jako zywego.
        if (session == null || session.getPhase() == DungeonSession.Phase.ENDED
            || !session.isDeadSpectator(player.getUniqueId())) {
            // nie ruszaj w trakcie aktywnego spectator death — handle w endSession
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (manager.tryRejoin(player)) {
                return;
            }
            // Po rejoin fail: ZERO tolerancji dla GM3 poza dungeonem.
            if (!manager.isInActiveDungeon(player.getUniqueId())) {
                if (player.getGameMode() == GameMode.SPECTATOR
                    || player.getAllowFlight()
                    || player.isFlying()
                    || DungeonWorldService.isDungeonWorld(player.getWorld().getName())) {
                    manager.forceSurvivalNoFlight(player);
                    if (DungeonWorldService.isDungeonWorld(player.getWorld().getName())) {
                        manager.teleportToServerSpawn(player);
                        player.getInventory().clear();
                    }
                }
            }
        }, 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || manager.isInActiveDungeon(player.getUniqueId())) {
                return;
            }
            if (player.getGameMode() == GameMode.SPECTATOR || player.isFlying()) {
                manager.forceSurvivalNoFlight(player);
            }
        }, 25L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDungeonCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInActiveDungeon(player.getUniqueId())) {
            return;
        }
        // Admin: wszystkie komendy.
        if (player.isOp() || player.hasPermission("holly.adungeon.admin")) {
            return;
        }
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2) {
            return;
        }
        String cmd = raw.substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int slash = cmd.indexOf(':');
        if (slash >= 0) {
            cmd = cmd.substring(slash + 1);
        }
        // Tylko /helpop i /skip.
        if (cmd.equals("helpop") || cmd.equals("skip")) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeComponent(
            "<gradient:#FF3B3B:#FF5C5C>Komendy zablokowane</gradient> &fw dungeonie! &7(/helpop, /skip)</shadow>"
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        DungeonSession session = manager.getByPlayer(player.getUniqueId());

        // Poza dungeonem: GM3 = bug → fix.
        if (session == null || session.getPhase() == DungeonSession.Phase.ENDED) {
            if (player.getGameMode() == GameMode.SPECTATOR
                && !DungeonWorldService.isDungeonWorld(player.getWorld().getName())
                && event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
                event.setCancelled(true);
                manager.forceSurvivalNoFlight(player);
            }
            return;
        }

        // W dungeonie: blokuj spectator menu TP do graczy (scroll / double-click).
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE
            || (player.getGameMode() == GameMode.SPECTATOR
            && (session.isDeadSpectator(player.getUniqueId())
            || session.getPhase() == DungeonSession.Phase.BOSS_CINEMATIC
            || session.getPhase() == DungeonSession.Phase.TUTORIAL))) {
            Location to = event.getTo();
            if (to == null) {
                event.setCancelled(true);
                return;
            }
            // SPECTATE cause = zawsze blokuj.
            if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
                event.setCancelled(true);
                try {
                    player.setSpectatorTarget(null);
                } catch (Throwable ignored) {
                }
                player.sendMessage(dev.grzybalson.utils.ColorUtils.colorizeComponent(
                    "<gradient:#FF3B3B:#FF5C5C>Nie mozesz sie tepac</gradient> &fdo graczy w spectatorze!</shadow>"
                ));
                Location corpse = session.getDeathLocation(player.getUniqueId());
                if (corpse != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && session.isDeadSpectator(player.getUniqueId())) {
                            player.teleport(corpse.clone().add(0, 1.5D, 0));
                        }
                    });
                }
                return;
            }
            // Martwy spectator: max 15 od zywego teammate.
            if (session.isDeadSpectator(player.getUniqueId())) {
                double max = session.getSpectatorTeammateRadius();
                double maxSq = max * max;
                Location bestMate = null;
                double best = Double.MAX_VALUE;
                for (java.util.UUID mateId : session.getPlayers()) {
                    if (mateId.equals(player.getUniqueId()) || session.isDeadSpectator(mateId)) {
                        continue;
                    }
                    Player mate = Bukkit.getPlayer(mateId);
                    if (mate == null || !mate.isOnline() || mate.getGameMode() == GameMode.SPECTATOR || mate.isDead()) {
                        continue;
                    }
                    if (!mate.getWorld().equals(to.getWorld())) {
                        continue;
                    }
                    double d = to.distanceSquared(mate.getLocation());
                    if (d < best) {
                        best = d;
                        bestMate = mate.getLocation();
                    }
                }
                if (bestMate != null && best > maxSq) {
                    event.setCancelled(true);
                    Location pull = bestMate.clone().add(0, 1.4D, 0);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && session.isDeadSpectator(player.getUniqueId())) {
                            player.teleport(pull);
                        }
                    });
                }
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (DungeonWorldService.isDungeonWorld(event.getFrom().getName())
            && !DungeonWorldService.isDungeonWorld(player.getWorld().getName())) {
            // Wyszedl z dungeon world — zawsze zdejmij GM3 jesli sesja sie skonczyla / nie jest w party.
            DungeonSession session = manager.getByWorld(event.getFrom().getName());
            if (session == null || session.getPhase() == DungeonSession.Phase.ENDED
                || !session.isParticipant(player.getUniqueId())) {
                manager.forceSurvivalNoFlight(player);
            }
        }
        if (!DungeonWorldService.isDungeonWorld(event.getFrom().getName())) {
            return;
        }
        if (manager.shouldIgnoreWorldQuit(player.getUniqueId())) {
            return;
        }
        DungeonSession session = manager.getByWorld(event.getFrom().getName());
        if (session == null || !session.isParticipant(player.getUniqueId())) {
            return;
        }
        if (DungeonWorldService.isDungeonWorld(player.getWorld().getName())) {
            return;
        }
        // Smierc /kill / respawn nie jest wyjsciem z runu.
        if (player.isDead() || session.isDeadSpectator(player.getUniqueId()) || session.getPhase() == DungeonSession.Phase.ENDED) {
            if (session.getPhase() == DungeonSession.Phase.ENDED) {
                manager.stripDungeonInventory(player);
                manager.forceSurvivalNoFlight(player);
            }
            return;
        }
        manager.stripDungeonInventory(player);
        session.handlePlayerQuit(player, false);
    }

    private Player nearestAlive(DungeonSession session, Mob mob) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID uuid : session.getPlayers()) {
            if (session.isDeadSpectator(uuid)) {
                continue;
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(mob.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = player;
            }
        }
        return best;
    }

    private Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
