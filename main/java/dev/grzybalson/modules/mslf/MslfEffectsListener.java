package dev.grzybalson.modules.mslf;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MslfEffectsListener implements Listener {

    private static final String META_BOMB = "mslf_bomb";
    private static final String META_PILKA = "mslf_pilka";
    /** Ile kratek luzu musi zostac miedzy obszarami dwoch zamkow. */
    private static final int CASTLE_GAP = 1;

    private final HollyCore plugin;
    private final MslfManager manager;
    private final Set<UUID> elytraBlocked = new HashSet<>();
    private final List<ActiveCastle> activeCastles = new ArrayList<>();
    private final Map<UUID, BukkitTask> hellFire = new HashMap<>();
    private final Map<UUID, Long> iceFrozenUntil = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDragonTotem = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> deathFieldLastHit = new ConcurrentHashMap<>();
    /** Bloki lasera: klucz world:x:y:z → stan z refcount (overlap wielu laserów). */
    private final Map<String, LaserBlockClaim> laserBlocks = new ConcurrentHashMap<>();

    public MslfEffectsListener(HollyCore plugin, MslfManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private MslfItems items() {
        return manager.items();
    }

    private MslfSettings cfg() {
        return manager.settings();
    }

    private boolean guard(Player player, MslfItemType type) {
        if (MslfRegionUtil.isBlocked(player, cfg(), type)) {
            player.sendMessage(ColorUtils.colorizeComponent(cfg().blockedRegionMessage()));
            return false;
        }
        return true;
    }

    private static String cooldownKey(MslfItemType type) {
        return "mslf:" + type.id();
    }

    private boolean tryCooldown(Player player, MslfItemType type) {
        if (player == null || type == null || !type.hasUseCooldown()) {
            return true;
        }
        int seconds = cfg().cooldownSeconds(type);
        if (seconds <= 0) {
            return true;
        }
        String key = cooldownKey(type);
        UUID id = player.getUniqueId();
        if (plugin.getCooldownManager().isOnCooldown(id, key)) {
            int left = Math.max(1, plugin.getCooldownManager().getRemainingCooldown(id, key));
            String message = cfg().cooldownMessage()
                .replace("%seconds%", String.valueOf(left))
                .replace("%time%", String.valueOf(left));
            player.sendMessage(ColorUtils.colorizeComponent(message));
            return false;
        }
        plugin.getCooldownManager().setCooldown(id, key, seconds);
        applyVanillaCooldown(player, type, seconds);
        return true;
    }

    private void clearCooldown(Player player, MslfItemType type) {
        if (player == null || type == null) {
            return;
        }
        plugin.getCooldownManager().removeCooldown(player.getUniqueId(), cooldownKey(type));
        if (type == MslfItemType.KOKOS) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        if (items().is(main, type)) {
            player.setCooldown(main.getType(), 0);
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (items().is(off, type)) {
            player.setCooldown(off.getType(), 0);
        }
    }

    private void applyVanillaCooldown(Player player, MslfItemType type, int seconds) {
        if (type == MslfItemType.KOKOS) {
            return;
        }
        int ticks = Math.max(1, seconds * 20);
        ItemStack main = player.getInventory().getItemInMainHand();
        if (items().is(main, type)) {
            player.setCooldown(main.getType(), ticks);
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (items().is(off, type)) {
            player.setCooldown(off.getType(), ticks);
        }
    }

    private void consumeOne(Player player, ItemStack stack, EquipmentSlot slot) {
        if (stack == null) {
            return;
        }
        int amount = stack.getAmount();
        if (amount <= 1) {
            if (slot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            stack.setAmount(amount - 1);
        }
    }

    private ItemStack hand(Player player, EquipmentSlot slot) {
        if (slot == EquipmentSlot.OFF_HAND) {
            return player.getInventory().getItemInOffHand();
        }
        return player.getInventory().getItemInMainHand();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        Action action = event.getAction();
        // PPM w powietrze bywa cancelled; PPM w blok ma useItemInHand=DENY dla custom itemów.
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = hand(player, event.getHand());
        MslfItemType type = items().typeOf(stack);
        if (type == null) {
            return;
        }

        if (type == MslfItemType.DYNAMIT && event.getClickedBlock() != null) {
            event.setCancelled(true);
            if (!guard(player, type)) {
                return;
            }
            Block block = event.getClickedBlock();
            if (block.getType() != Material.BEDROCK) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTylko bedrock."));
                return;
            }
            if (MslfRegionUtil.isBlocked(block.getLocation(), cfg(), type)) {
                player.sendMessage(ColorUtils.colorizeComponent(cfg().blockedRegionMessage()));
                return;
            }
            if (isLastBedrockAboveVoid(block)) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie mozesz zniszczyc ostatniej kratki nad void."));
                return;
            }
            if (!tryCooldown(player, type)) {
                return;
            }
            block.setType(Material.AIR, false);
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.4F);
            block.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, block.getLocation().add(0.5, 0.5, 0.5), 1);
            consumeOne(player, stack, event.getHand());
            return;
        }

        if (type == MslfItemType.BOMBA) {
            cancelUse(event);
            if (!guard(player, type)) {
                return;
            }
            if (!tryCooldown(player, type)) {
                return;
            }
            Fireball ball = player.launchProjectile(Fireball.class);
            ball.setYield(0F);
            ball.setIsIncendiary(false);
            ball.setShooter(player);
            ball.setMetadata(META_BOMB, new FixedMetadataValue(plugin, true));
            ball.setVelocity(player.getLocation().getDirection().multiply(1.15D));
            consumeOne(player, stack, event.getHand());
            return;
        }

        if (type == MslfItemType.ZAMEK) {
            cancelUse(event);
            if (!guard(player, type)) {
                return;
            }
            startSandCastle(player);
            return;
        }

        if (type == MslfItemType.POLE_SMIERCI) {
            cancelUse(event);
            if (!guard(player, type)) {
                return;
            }
            startDeathField(player);
            return;
        }

        if (type == MslfItemType.KONICZYNKA) {
            cancelUse(event);
            if (!guard(player, type)) {
                return;
            }
            var szczesciarz = plugin.getMslfSzczesciarzManager();
            if (szczesciarz == null) {
                player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cAddon &fmslf-szczesciarz &cjest wylaczony."));
                return;
            }
            if (!tryCooldown(player, type)) {
                return;
            }
            szczesciarz.openGui(player);
            return;
        }

        if (type == MslfItemType.ENDERCHEST) {
            cancelUse(event);
            if (!guard(player, type)) {
                return;
            }
            MslfEnderChestService ec = manager.enderChestService();
            if (ec == null) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#FF0000✘ &8Ξ &cRozszerzanie EC niedostepne — MSLF enderchest nie dziala."
                ));
                return;
            }
            if (!tryCooldown(player, type)) {
                return;
            }
            if (ec.expand(player)) {
                consumeOne(player, stack, event.getHand());
            } else {
                clearCooldown(player, type);
            }
        }
    }

    private static void cancelUse(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        ProjectileSource source = snowball.getShooter();
        if (!(source instanceof Player player)) {
            return;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!items().is(stack, MslfItemType.PILKA)) {
            stack = player.getInventory().getItemInOffHand();
            if (!items().is(stack, MslfItemType.PILKA)) {
                return;
            }
        }
        if (!guard(player, MslfItemType.PILKA)) {
            event.setCancelled(true);
            return;
        }
        if (!tryCooldown(player, MslfItemType.PILKA)) {
            event.setCancelled(true);
            return;
        }
        snowball.setMetadata(META_PILKA, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPearlInCastleInteract(PlayerInteractEvent event) {
        if (!cfg().sandCastleBlockPearls() || activeCastles.isEmpty()) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (!isInsideCastle(player.getLocation())) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        player.sendMessage(ColorUtils.colorizeComponent(cfg().sandCastlePearlMessage()));
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlInCastleLaunch(ProjectileLaunchEvent event) {
        if (!cfg().sandCastleBlockPearls() || activeCastles.isEmpty()) {
            return;
        }
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player player)) {
            return;
        }
        if (!isInsideCastle(player.getLocation())) {
            return;
        }
        event.setCancelled(true);
        pearl.remove();
        refundPearl(player);
        player.sendMessage(ColorUtils.colorizeComponent(cfg().sandCastlePearlMessage()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearlInCastleTeleport(PlayerTeleportEvent event) {
        if (!cfg().sandCastleBlockPearls() || activeCastles.isEmpty()) {
            return;
        }
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean trappedNow = isInsideCastle(player.getLocation());
        boolean fromInside = isInsideCastle(from);
        boolean toInside = isInsideCastle(to);
        if (!trappedNow && !fromInside && !toInside) {
            return;
        }
        event.setCancelled(true);
        if (!trappedNow && fromInside) {
            refundPearl(player);
        }
        player.sendMessage(ColorUtils.colorizeComponent(cfg().sandCastlePearlMessage()));
    }

    private void refundPearl(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof EnderPearl pearl
            && cfg().sandCastleBlockPearls()
            && !activeCastles.isEmpty()) {
            Location pearlLoc = pearl.getLocation();
            Player shooter = pearl.getShooter() instanceof Player player ? player : null;
            if (isInsideCastle(pearlLoc) || (shooter != null && isInsideCastle(shooter.getLocation()))) {
                event.setCancelled(true);
                pearl.remove();
                if (shooter != null && shooter.isOnline()) {
                    shooter.sendMessage(ColorUtils.colorizeComponent(cfg().sandCastlePearlMessage()));
                }
                return;
            }
        }
        if (projectile.hasMetadata(META_BOMB)) {
            Location at = projectile.getLocation();
            if (MslfRegionUtil.isBlocked(at, cfg(), MslfItemType.BOMBA)) {
                projectile.remove();
                if (projectile.getShooter() instanceof Player shooter) {
                    shooter.sendMessage(ColorUtils.colorizeComponent(cfg().blockedRegionMessage()));
                }
                return;
            }
            explodeBlocks(at, cfg().bombRadius());
            projectile.remove();
            return;
        }
        if (!(projectile instanceof Snowball) || !projectile.hasMetadata(META_PILKA)) {
            return;
        }
        if (!(event.getHitEntity() instanceof Player victim)) {
            return;
        }
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        if (victim.getUniqueId().equals(shooter.getUniqueId())) {
            return;
        }
        Location a = shooter.getLocation().clone();
        Location b = victim.getLocation().clone();
        if (MslfRegionUtil.isBlocked(a, cfg(), MslfItemType.PILKA)
            || MslfRegionUtil.isBlocked(b, cfg(), MslfItemType.PILKA)) {
            projectile.remove();
            shooter.sendMessage(ColorUtils.colorizeComponent(cfg().blockedRegionMessage()));
            return;
        }
        shooter.teleport(b);
        victim.teleport(a);
        shooter.getWorld().playSound(a, Sound.ENTITY_ENDERMAN_TELEPORT, 1F, 1.2F);
        projectile.remove();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity().hasMetadata(META_BOMB)) {
            event.blockList().clear();
            event.setCancelled(true);
            return;
        }
        if (activeCastles.isEmpty() && laserBlocks.isEmpty()) {
            return;
        }
        event.blockList().removeIf(this::isMslfProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) {
            return;
        }
        ItemStack stack = attacker.getInventory().getItemInMainHand();
        MslfItemType held = items().typeOf(stack);
        if (held == null) {
            return;
        }
        if (held == MslfItemType.LOD_NA_PATYKU) {
            Location attackerLoc = attacker.getLocation();
            Location victimLoc = victim.getLocation();
            double range = cfg().iceStickRange();
            // Strzelec moze byc juz w innym swiecie — distance() by wtedy rzucilo wyjatkiem.
            if (attackerLoc.getWorld() == null
                || !attackerLoc.getWorld().equals(victimLoc.getWorld())
                || attackerLoc.distanceSquared(victimLoc) > range * range) {
                return;
            }
            if (MslfRegionUtil.isBlocked(attacker, cfg(), MslfItemType.LOD_NA_PATYKU)
                || MslfRegionUtil.isBlocked(victimLoc, cfg(), MslfItemType.LOD_NA_PATYKU)) {
                event.setCancelled(true);
                attacker.sendMessage(ColorUtils.colorizeComponent(cfg().blockedRegionMessage()));
                return;
            }
            if (!tryCooldown(attacker, MslfItemType.LOD_NA_PATYKU)) {
                return;
            }
            freezePlayer(victim);
            return;
        }
        if (held != MslfItemType.MIECZ_PIEKIELNY) {
            return;
        }
        if (MslfRegionUtil.isBlocked(attacker, cfg(), MslfItemType.MIECZ_PIEKIELNY)
            || MslfRegionUtil.isBlocked(victim.getLocation(), cfg(), MslfItemType.MIECZ_PIEKIELNY)) {
            event.setCancelled(true);
            attacker.sendMessage(ColorUtils.colorizeComponent(cfg().blockedRegionMessage()));
            return;
        }
        if (!tryCooldown(attacker, MslfItemType.MIECZ_PIEKIELNY)) {
            return;
        }
        startHellFire(victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        if (!items().is(stack, MslfItemType.KOKOS)) {
            return;
        }
        Player player = event.getPlayer();
        if (!guard(player, MslfItemType.KOKOS)) {
            event.setCancelled(true);
            return;
        }
        if (!tryCooldown(player, MslfItemType.KOKOS)) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Nerf -50%: czasy / absorption vs poprzedni kokos.
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 10 * 20, 1, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60 * 20, 2, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 5 * 20, 1, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 150 * 20, 0, false, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 150 * 20, 0, false, true, true));
            player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Kokos aktywowany!"));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() > 0.0D) {
            pendingDragonTotem.remove(player.getUniqueId());
            return;
        }
        if (hasDragonTotem(player)) {
            pendingDragonTotem.add(player.getUniqueId());
        } else {
            pendingDragonTotem.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        boolean ours = pendingDragonTotem.remove(player.getUniqueId()) || hasDragonTotem(player);
        if (!ours && event.getHand() != null) {
            ours = isDragonTotem(player.getInventory().getItem(event.getHand()));
        }
        if (!ours) {
            return;
        }
        healDragonTotem(player, false);
        Bukkit.getScheduler().runTask(plugin, () -> healDragonTotem(player, true));
        Bukkit.getScheduler().runTaskLater(plugin, () -> healDragonTotem(player, false), 3L);
    }

    private boolean hasDragonTotem(Player player) {
        return isDragonTotem(player.getInventory().getItemInMainHand())
            || isDragonTotem(player.getInventory().getItemInOffHand());
    }

    private boolean isDragonTotem(ItemStack stack) {
        if (items().is(stack, MslfItemType.SMOCZY_TOTEM)) {
            return true;
        }
        if (stack == null || stack.getType() != Material.TOTEM_OF_UNDYING || !stack.hasItemMeta()) {
            return false;
        }
        var meta = stack.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) {
            return false;
        }
        MslfSettings.ItemDef def = cfg().item(MslfItemType.SMOCZY_TOTEM);
        int expected = def != null && def.customModelData() > 0
            ? def.customModelData()
            : MslfItemType.SMOCZY_TOTEM.defaultCmd();
        return expected > 0 && meta.getCustomModelData() == expected;
    }

    private void healDragonTotem(Player player, boolean announce) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        var attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double max = attribute != null ? attribute.getValue() : player.getMaxHealth();
        if (max < 1.0D) {
            max = 20.0D;
        }
        player.setHealth(Math.min(max, Math.max(1.0D, max)));
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setExhaustion(0F);
        if (announce) {
            player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Smoczy totem &8— &apelne HP!"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isGliding() && elytraBlocked.contains(player.getUniqueId())) {
            event.setCancelled(true);
            player.setGliding(false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        elytraBlocked.remove(id);
        iceFrozenUntil.remove(id);
        pendingDragonTotem.remove(id);
        stopHellFire(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        iceFrozenUntil.remove(event.getEntity().getUniqueId());
        pendingDragonTotem.remove(event.getEntity().getUniqueId());
        Location death = event.getEntity().getLocation();
        if (!isInsideCastle(death)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> pinCastleDrops(death), 2L);
    }

    private void pinCastleDrops(Location death) {
        if (death == null || death.getWorld() == null) {
            return;
        }
        for (Entity entity : death.getWorld().getNearbyEntities(death, 3.5D, 3.5D, 3.5D)) {
            if (!(entity instanceof Item item) || !item.isValid()) {
                continue;
            }
            if (!isInsideCastle(item.getLocation())) {
                continue;
            }
            item.setGravity(false);
            item.setVelocity(new Vector(0, 0, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIceMove(PlayerMoveEvent event) {
        if (!isIceFrozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            Location locked = from.clone();
            locked.setY(to.getY());
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIceFreezeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE && isIceFrozen(player)) {
            event.setCancelled(true);
        }
    }

    private void freezePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        int seconds = Math.max(1, cfg().iceStickSeconds());
        iceFrozenUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFreezeTicks(Math.min(80, seconds * 20));
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 0.5F);
    }

    private boolean isIceFrozen(Player player) {
        if (player == null) {
            return false;
        }
        Long until = iceFrozenUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            iceFrozenUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void startHellFire(Player victim) {
        UUID id = victim.getUniqueId();
        stopHellFire(id);
        victim.setFireTicks(Math.max(victim.getFireTicks(), 6 * 20));
        final int[] hits = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!victim.isOnline() || victim.isDead() || hits[0] >= 6) {
                stopHellFire(id);
                return;
            }
            hits[0]++;
            Material standing = victim.getLocation().getBlock().getType();
            if (victim.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)
                || standing == Material.WATER
                || standing == Material.BUBBLE_COLUMN) {
                victim.damage(1.0);
                victim.setNoDamageTicks(0);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.0f, 1.0f);
            }
        }, 20L, 20L);
        hellFire.put(id, task);
    }

    private void stopHellFire(UUID id) {
        BukkitTask task = hellFire.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean isLastBedrockAboveVoid(Block bedrock) {
        World world = bedrock.getWorld();
        int x = bedrock.getX();
        int z = bedrock.getZ();
        int y = bedrock.getY();
        int minY = world.getMinHeight();
        for (int checkY = y - 1; checkY >= minY; checkY--) {
            Material type = world.getBlockAt(x, checkY, z).getType();
            if (type == Material.BEDROCK) {
                return false;
            }
            if (!type.isAir() && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
                return false;
            }
        }
        return true;
    }

    private void explodeBlocks(Location center, int radius) {
        if (center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1F, 0.9F);
        world.spawnParticle(Particle.EXPLOSION_LARGE, center, 1);
        int r = Math.max(1, radius);
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        // Lista zablokowanych regionow jest stala w obrebie jednego wybuchu — liczymy ja raz.
        Set<String> bombBlocked = MslfRegionUtil.blockedNames(cfg(), MslfItemType.BOMBA);
        boolean checkRegions = !bombBlocked.isEmpty();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > r * r + 1) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    Material type = block.getType();
                    if (type.isAir() || isMslfProtectedBlock(block) || MslfSchematicStrip.isUnbreakable(type)) {
                        continue;
                    }
                    if (checkRegions && MslfRegionUtil.isBlocked(block.getLocation(), bombBlocked)) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void startSandCastle(Player caster) {
        Location center = caster.getLocation().getBlock().getLocation();
        if (MslfRegionUtil.isNearBlocked(center, cfg(), 5.0D, MslfItemType.ZAMEK)) {
            caster.sendMessage(ColorUtils.colorizeComponent(cfg().blockedRegionMessage()));
            return;
        }
        // Obszar liczony przed wklejeniem — inaczej drugi zamek wszedlby w pierwszy i przejal jego bloki.
        MslfSchematicStrip.CastleBox plannedBox = MslfSchematicStrip.planBox(plugin, center, cfg().sandCastleSchematic());
        if (isNearAnotherCastle(center, cfg().sandCastleMinDistance()) || overlapsAnotherCastle(plannedBox)) {
            caster.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cZamek jest za blisko innego zamku!"));
            return;
        }
        if (!tryCooldown(caster, MslfItemType.ZAMEK)) {
            return;
        }

        Map<Location, BlockData> originalBlocks = new HashMap<>();
        MslfSchematicStrip.pasteOrFallback(plugin, center, cfg().sandCastleSchematic(), originalBlocks, this::isCastleBlock);

        Location trapCenter = findCastleStand(center);
        double radius = cfg().sandCastleRadius();
        if (center.getWorld() != null) {
            for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (entity instanceof Player nearbyPlayer) {
                    nearbyPlayer.teleport(trapCenter);
                }
            }
        }

        int durationSeconds = Math.max(1, cfg().sandCastleSeconds());
        BossBar bossBar = null;
        BukkitTask updateTask = null;
        if (cfg().sandCastleBossbarEnabled()) {
            BarColor barColor;
            try {
                barColor = BarColor.valueOf(cfg().sandCastleBossbarColor().toUpperCase(Locale.ROOT));
            } catch (Exception exception) {
                barColor = BarColor.YELLOW;
            }
            BarStyle barStyle;
            try {
                barStyle = BarStyle.valueOf(cfg().sandCastleBossbarStyle().toUpperCase(Locale.ROOT));
            } catch (Exception exception) {
                barStyle = BarStyle.SOLID;
            }
            String titleTemplate = cfg().sandCastleBossbarTitle();
            String initialTitle = ColorUtils.colorize(titleTemplate
                .replace("%czas%", String.valueOf(durationSeconds))
                .replace("%time%", String.valueOf(durationSeconds)));
            bossBar = Bukkit.createBossBar(initialTitle, barColor, barStyle);
            bossBar.setProgress(1.0);
            final BossBar finalBossBar = bossBar;
            final int totalTicks = durationSeconds * 20;
            final int[] ticksLeft = new int[] { totalTicks };
            updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                ticksLeft[0] -= 2;
                if (ticksLeft[0] <= 0) {
                    return;
                }
                double progress = Math.max(0.0, (double) ticksLeft[0] / (double) totalTicks);
                int secs = (int) Math.ceil((double) ticksLeft[0] / 20.0);
                finalBossBar.setProgress(progress);
                finalBossBar.setTitle(ColorUtils.colorize(titleTemplate
                    .replace("%czas%", String.valueOf(secs))
                    .replace("%time%", String.valueOf(secs))));
                Set<Player> insidePlayers = new HashSet<>();
                World castleWorld = center.getWorld();
                if (castleWorld != null) {
                    double maxDist = (radius + 0.5) * (radius + 0.5);
                    for (Player nearbyPlayer : castleWorld.getPlayers()) {
                        if (nearbyPlayer.getLocation().distanceSquared(center) <= maxDist) {
                            insidePlayers.add(nearbyPlayer);
                        }
                    }
                }
                for (Player player : new ArrayList<>(finalBossBar.getPlayers())) {
                    if (!insidePlayers.contains(player)) {
                        finalBossBar.removePlayer(player);
                    }
                }
                for (Player player : insidePlayers) {
                    finalBossBar.addPlayer(player);
                }
            }, 2L, 2L);
        }

        ActiveCastle activeCastle = new ActiveCastle(plugin, center, originalBlocks, bossBar, updateTask, plannedBox);
        activeCastles.add(activeCastle);
        cancelInFlightPearls(activeCastle);
        Bukkit.getScheduler().runTask(plugin, () -> cancelInFlightPearls(activeCastle));
        BukkitTask expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            activeCastle.restore(true, () -> activeCastles.remove(activeCastle));
        }, durationSeconds * 20L);
        activeCastle.setExpiryTask(expiryTask);
    }

    /** Wolane tylko przy wylaczaniu pluginu (MslfManager#disable) — nie uzywac przy reloadzie. */
    void restoreCastles() {
        for (ActiveCastle castle : new ArrayList<>(activeCastles)) {
            castle.restore(false, null);
        }
        activeCastles.clear();
        for (UUID id : new ArrayList<>(hellFire.keySet())) {
            stopHellFire(id);
        }
        // Przy wylaczeniu zadanie zdejmujace beton nie odpali — oddaj bloki pola smierci tutaj.
        try {
            for (LaserBlockClaim claim : new ArrayList<>(laserBlocks.values())) {
                try {
                    Location loc = claim.location;
                    World world = loc == null ? null : loc.getWorld();
                    if (world != null && world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                        loc.getBlock().setBlockData(claim.original, false);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        laserBlocks.clear();
    }

    private static Location findCastleStand(Location center) {
        if (center == null || center.getWorld() == null) {
            return center;
        }
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dy = 0; dy <= 4; dy++) {
            Material feet = world.getBlockAt(cx, cy + dy, cz).getType();
            Material head = world.getBlockAt(cx, cy + dy + 1, cz).getType();
            if (MslfSchematicStrip.isReplaceable(feet) && MslfSchematicStrip.isReplaceable(head)) {
                return new Location(world, cx + 0.5D, cy + dy, cz + 0.5D);
            }
        }
        return new Location(world, cx + 0.5D, cy, cz + 0.5D);
    }

    private static void unstuckCastlePlayers(Location center, Iterable<Location> restored) {
        if (center == null || center.getWorld() == null || restored == null) {
            return;
        }
        World world = center.getWorld();
        Location stand = findCastleStand(center);
        List<Player> players = world.getPlayers();
        for (Location loc : restored) {
            if (loc.getWorld() == null || !world.equals(loc.getWorld())) {
                continue;
            }
            for (Player player : players) {
                Location feet = player.getLocation();
                if (Math.abs(feet.getBlockX() - loc.getBlockX()) > 1
                    || Math.abs(feet.getBlockZ() - loc.getBlockZ()) > 1
                    || Math.abs(feet.getBlockY() - loc.getBlockY()) > 2) {
                    continue;
                }
                Block atFeetBlock = feet.getBlock();
                Material atFeet = atFeetBlock.getType();
                Material atHead = atFeetBlock.getRelative(BlockFace.UP).getType();
                if (!atFeet.isSolid() && !atHead.isSolid()) {
                    continue;
                }
                player.setFallDistance(0.0F);
                player.teleport(stand);
                player.setFallDistance(0.0F);
            }
        }
    }

    private boolean isNearAnotherCastle(Location center, double minDistance) {
        if (center == null || center.getWorld() == null || minDistance <= 0.0D) {
            return false;
        }
        for (ActiveCastle castle : activeCastles) {
            if (castle.center.getWorld() != null
                && castle.center.getWorld().equals(center.getWorld())
                && castle.center.distance(center) < minDistance) {
                return true;
            }
        }
        return false;
    }

    /** Blokuje postawienie zamku, ktory wszedlby w obszar juz stojacego zamku (albo go dotykal). */
    private boolean overlapsAnotherCastle(MslfSchematicStrip.CastleBox box) {
        if (box == null || activeCastles.isEmpty()) {
            return false;
        }
        for (ActiveCastle castle : activeCastles) {
            if (box.intersects(castle.box, CASTLE_GAP)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCastleBlock(Location location) {
        if (location == null || activeCastles.isEmpty()) {
            return false;
        }
        Location key = MslfSchematicStrip.blockLocation(location);
        for (ActiveCastle castle : activeCastles) {
            if (castle.originalBlocks.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInsideCastle(Location location) {
        if (location == null || location.getWorld() == null || activeCastles.isEmpty()) {
            return false;
        }
        for (ActiveCastle castle : activeCastles) {
            if (castle.contains(location)) {
                return true;
            }
        }
        return false;
    }

    private void cancelInFlightPearls(ActiveCastle castle) {
        if (!cfg().sandCastleBlockPearls() || castle == null || castle.center.getWorld() == null) {
            return;
        }
        World world = castle.center.getWorld();
        for (EnderPearl pearl : world.getEntitiesByClass(EnderPearl.class)) {
            if (castle.contains(pearl.getLocation())) {
                pearl.remove();
                continue;
            }
            if (pearl.getShooter() instanceof Player shooter
                && shooter.isOnline()
                && castle.contains(shooter.getLocation())) {
                pearl.remove();
            }
        }
    }

    private void hurtDeathField(Player player, double amount) {
        if (player == null || amount <= 0.0D || !player.isOnline() || player.isDead()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        double remaining = amount;
        double absorption = player.getAbsorptionAmount();
        if (absorption > 0.0D) {
            double take = Math.min(absorption, remaining);
            player.setAbsorptionAmount(Math.max(0.0D, absorption - take));
            remaining -= take;
        }
        if (remaining <= 0.0D) {
            return;
        }
        double next = player.getHealth() - remaining;
        if (next <= 0.0D) {
            player.setHealth(0.0D);
            return;
        }
        player.setHealth(next);
    }

    private void startDeathField(Player caster) {
        Location center = caster.getLocation().getBlock().getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        if (!tryCooldown(caster, MslfItemType.POLE_SMIERCI)) {
            return;
        }
        int radius = cfg().deathFieldRadius();
        int seconds = cfg().deathFieldSeconds();
        Set<UUID> victims = new HashSet<>();
        for (Player nearby : world.getPlayers()) {
            if (nearby.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }
            if (nearby.getLocation().distanceSquared(center) <= (radius + 0.75) * (radius + 0.75)) {
                if (MslfRegionUtil.isBlocked(nearby.getLocation(), cfg(), MslfItemType.POLE_SMIERCI)) {
                    continue;
                }
                victims.add(nearby.getUniqueId());
                elytraBlocked.add(nearby.getUniqueId());
                nearby.setGliding(false);
                nearby.sendMessage(ColorUtils.colorizeComponent(
                    "<gradient:#FF3B3B:#FF9B00>Pole smierci</gradient> &8— &celytra zablokowana</shadow>"
                ));
            }
        }

        // Instant: środek pomarańczowy, reszta żółta — bez animacji, z refcount przy overlap.
        List<String> claimedKeys = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) {
                    continue;
                }
                Location floor = findFloor(center.clone().add(x, 0, z));
                if (floor == null) {
                    continue;
                }
                Material paint = (x == 0 && z == 0) ? Material.ORANGE_CONCRETE : Material.YELLOW_CONCRETE;
                String key = claimLaserBlock(floor, paint);
                if (key != null) {
                    claimedKeys.add(key);
                }
            }
        }

        long minGapMillis = Math.max(50L, cfg().deathFieldTickInterval() * 50L);
        BukkitTask damage = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID uuid : victims) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (p.getWorld() != world) {
                    continue;
                }
                if (p.getLocation().distanceSquared(center) > (radius + 0.75) * (radius + 0.75)) {
                    continue;
                }
                if (MslfRegionUtil.isBlocked(p.getLocation(), cfg(), MslfItemType.POLE_SMIERCI)) {
                    continue;
                }
                Long last = deathFieldLastHit.get(uuid);
                if (last != null && now - last < minGapMillis) {
                    continue;
                }
                deathFieldLastHit.put(uuid, now);
                hurtDeathField(p, cfg().deathFieldDamage());
                p.setGliding(false);
            }
        }, cfg().deathFieldTickInterval(), cfg().deathFieldTickInterval());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            damage.cancel();
            for (String key : claimedKeys) {
                releaseLaserBlock(key);
            }
            for (UUID uuid : victims) {
                elytraBlocked.remove(uuid);
                deathFieldLastHit.remove(uuid);
            }
        }, seconds * 20L);

        caster.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Pole smierci aktywowane!"));
    }

    private String claimLaserBlock(Location floor, Material paint) {
        if (floor.getWorld() == null) {
            return null;
        }
        String key = blockKey(floor);
        laserBlocks.compute(key, (ignored, existing) -> {
            if (existing != null) {
                existing.refs++;
                return existing;
            }
            BlockData original = floor.getBlock().getBlockData();
            floor.getBlock().setType(paint, false);
            return new LaserBlockClaim(floor.clone(), original, 1);
        });
        return key;
    }

    private void releaseLaserBlock(String key) {
        laserBlocks.computeIfPresent(key, (ignored, claim) -> {
            claim.refs--;
            if (claim.refs > 0) {
                return claim;
            }
            if (claim.location.getWorld() != null) {
                claim.location.getBlock().setBlockData(claim.original, false);
            }
            return null;
        });
    }

    private static String blockKey(Location loc) {
        World world = loc.getWorld();
        String w = world == null ? "?" : world.getUID().toString();
        return w + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private boolean isMslfProtectedBlock(Block block) {
        if (block == null) {
            return false;
        }
        if (activeCastles.isEmpty() && laserBlocks.isEmpty()) {
            return false;
        }
        if (isCastleBlock(block.getLocation())) {
            return true;
        }
        if (laserBlocks.isEmpty()) {
            return false;
        }
        return laserBlocks.containsKey(blockKey(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isMslfProtectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (activeCastles.isEmpty()) {
            return;
        }
        Block placed = event.getBlockPlaced();
        if (placed == null) {
            return;
        }
        if (!isInsideCastle(placed.getLocation()) && !isCastleBlock(placed.getLocation())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ColorUtils.colorizeComponent(
            "&#FF0000✘ &8Ξ &cNie mozesz stawiac blokow w zamku."
        ));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmptyInCastle(PlayerBucketEmptyEvent event) {
        if (activeCastles.isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        if (block == null) {
            return;
        }
        if (!isInsideCastle(block.getLocation()) && !isCastleBlock(block.getLocation())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ColorUtils.colorizeComponent(
            "&#FF0000✘ &8Ξ &cNie mozesz stawiac blokow w zamku."
        ));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (activeCastles.isEmpty() && laserBlocks.isEmpty()) {
            return;
        }
        event.blockList().removeIf(this::isMslfProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (activeCastles.isEmpty()) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (isCastleBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (activeCastles.isEmpty()) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (isCastleBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private Location findFloor(Location start) {
        World world = start.getWorld();
        if (world == null) {
            return null;
        }
        int x = start.getBlockX();
        int z = start.getBlockZ();
        int y = start.getBlockY();
        for (int dy = 0; dy < 6; dy++) {
            Block at = world.getBlockAt(x, y - dy, z);
            Block below = at.getRelative(BlockFace.DOWN);
            if (at.getType().isAir() && below.getType().isSolid()) {
                return below.getLocation();
            }
            if (at.getType().isSolid()) {
                return at.getLocation();
            }
        }
        return world.getBlockAt(x, y - 1, z).getLocation();
    }

    private record HeldCastleDrop(ItemStack stack, Location origin) {
    }

    private static final class ActiveCastle {
        private final HollyCore plugin;
        private final Location center;
        private final Map<Location, BlockData> originalBlocks;
        private final BossBar bossBar;
        private final BukkitTask updateTask;
        private BukkitTask expiryTask;
        private BukkitTask undoTask;
        private boolean restoring;

        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        /** Zajety obszar — trzymany osobno, bo {@link #originalBlocks} pustoszeje przy zwijaniu zamku. */
        private final MslfSchematicStrip.CastleBox box;

        private ActiveCastle(HollyCore plugin, Location center, Map<Location, BlockData> originalBlocks,
                             BossBar bossBar, BukkitTask updateTask, MslfSchematicStrip.CastleBox plannedBox) {
            this.plugin = plugin;
            this.center = center;
            this.originalBlocks = originalBlocks;
            this.bossBar = bossBar;
            this.updateTask = updateTask;
            int minx = center.getBlockX();
            int maxx = minx;
            int miny = center.getBlockY();
            int maxy = miny + 4;
            int minz = center.getBlockZ();
            int maxz = minz;
            if (plannedBox != null && plannedBox.world() != null && plannedBox.world().equals(center.getWorld())) {
                minx = Math.min(minx, plannedBox.minX());
                maxx = Math.max(maxx, plannedBox.maxX());
                miny = Math.min(miny, plannedBox.minY());
                maxy = Math.max(maxy, plannedBox.maxY());
                minz = Math.min(minz, plannedBox.minZ());
                maxz = Math.max(maxz, plannedBox.maxZ());
            }
            for (Location loc : originalBlocks.keySet()) {
                minx = Math.min(minx, loc.getBlockX());
                maxx = Math.max(maxx, loc.getBlockX());
                miny = Math.min(miny, loc.getBlockY());
                maxy = Math.max(maxy, loc.getBlockY());
                minz = Math.min(minz, loc.getBlockZ());
                maxz = Math.max(maxz, loc.getBlockZ());
            }
            this.minX = minx;
            this.maxX = maxx;
            this.minY = miny;
            this.maxY = maxy;
            this.minZ = minz;
            this.maxZ = maxz;
            this.box = new MslfSchematicStrip.CastleBox(center.getWorld(), minx, miny, minz, maxx, maxy, maxz);
        }

        private boolean contains(Location location) {
            if (location == null || location.getWorld() == null || center.getWorld() == null) {
                return false;
            }
            if (!center.getWorld().equals(location.getWorld())) {
                return false;
            }
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            return x >= minX - 1 && x <= maxX + 1
                && y >= minY - 1 && y <= maxY + 2
                && z >= minZ - 1 && z <= maxZ + 1;
        }

        private void setExpiryTask(BukkitTask expiryTask) {
            this.expiryTask = expiryTask;
        }

        private void restore(boolean animated, Runnable onDone) {
            if (restoring && animated) {
                return;
            }
            restoring = true;
            if (expiryTask != null) {
                expiryTask.cancel();
            }
            if (updateTask != null) {
                updateTask.cancel();
            }
            if (bossBar != null) {
                bossBar.removeAll();
            }
            List<HeldCastleDrop> heldDrops = new ArrayList<>();
            scoopCastleDrops(heldDrops);
            if (!animated || originalBlocks.isEmpty()) {
                if (undoTask != null) {
                    undoTask.cancel();
                    undoTask = null;
                }
                restoreRemaining();
                scoopCastleDrops(heldDrops);
                releaseCastleDrops(heldDrops);
                if (onDone != null) {
                    onDone.run();
                }
                return;
            }
            TreeMap<Integer, List<Location>> columns = new TreeMap<>();
            for (Location loc : originalBlocks.keySet()) {
                columns.computeIfAbsent(loc.getBlockX(), key -> new ArrayList<>()).add(loc);
            }
            List<Integer> xs = new ArrayList<>(columns.descendingKeySet());
            final int[] index = {0};
            undoTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (index[0] >= xs.size()) {
                    if (undoTask != null) {
                        undoTask.cancel();
                        undoTask = null;
                    }
                    restoreRemaining();
                    scoopCastleDrops(heldDrops);
                    releaseCastleDrops(heldDrops);
                    if (onDone != null) {
                        onDone.run();
                    }
                    return;
                }
                scoopCastleDrops(heldDrops);
                int x = xs.get(index[0]++);
                List<Location> column = columns.get(x);
                if (column == null) {
                    return;
                }
                List<Location> restored = new ArrayList<>(column.size());
                for (Location loc : column) {
                    BlockData prev = originalBlocks.remove(loc);
                    if (loc.getWorld() != null && prev != null) {
                        loc.getBlock().setBlockData(prev, false);
                        restored.add(loc);
                    }
                }
                unstuckCastlePlayers(center, restored);
            }, 0L, 2L);
        }

        private void scoopCastleDrops(List<HeldCastleDrop> held) {
            World world = center.getWorld();
            if (world == null || held == null) {
                return;
            }
            double cx = (minX + maxX) * 0.5D + 0.5D;
            double cy = (minY + maxY) * 0.5D;
            double cz = (minZ + maxZ) * 0.5D;
            double hx = (maxX - minX) * 0.5D + 2.5D;
            double hy = (maxY - minY) * 0.5D + 5.0D;
            double hz = (maxZ - minZ) * 0.5D + 2.5D;
            for (Entity entity : world.getNearbyEntities(new Location(world, cx, cy, cz), hx, hy, hz)) {
                if (!(entity instanceof Item item) || !item.isValid()) {
                    continue;
                }
                Location at = item.getLocation();
                if (at.getY() > maxY + 8.0D) {
                    continue;
                }
                ItemStack stack = item.getItemStack();
                if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                    item.remove();
                    continue;
                }
                held.add(new HeldCastleDrop(stack.clone(), at.clone()));
                item.remove();
            }
        }

        private void releaseCastleDrops(List<HeldCastleDrop> held) {
            World world = center.getWorld();
            if (world == null || held == null || held.isEmpty()) {
                return;
            }
            Location preferred = center.clone();
            double ySum = 0.0D;
            for (HeldCastleDrop drop : held) {
                ySum += drop.origin().getY();
            }
            preferred.setY(ySum / held.size());
            Location spot = findSafeDropSpot(preferred);
            for (HeldCastleDrop drop : held) {
                Item spawned = world.dropItem(spot, drop.stack());
                spawned.setVelocity(new Vector(0, 0, 0));
                spawned.setGravity(true);
                spawned.setPickupDelay(10);
            }
            held.clear();
        }

        private Location findSafeDropSpot(Location preferred) {
            World world = center.getWorld();
            if (world == null) {
                return preferred;
            }
            int prefY = preferred != null ? preferred.getBlockY() : center.getBlockY();
            if (isDropSafe(world, preferred.getBlockX(), prefY, preferred.getBlockZ())) {
                return new Location(world, preferred.getBlockX() + 0.5D, prefY + 0.15D, preferred.getBlockZ() + 0.5D);
            }
            for (int dist = 0; dist <= 8; dist++) {
                int yMin = Math.max(minY - 1, prefY - dist);
                int yMax = Math.min(maxY + 6, prefY + dist);
                for (int y = yMin; y <= yMax; y++) {
                    for (int x = minX - 1; x <= maxX + 1; x++) {
                        for (int z = minZ - 1; z <= maxZ + 1; z++) {
                            if (isDropSafe(world, x, y, z)) {
                                return new Location(world, x + 0.5D, y + 0.15D, z + 0.5D);
                            }
                        }
                    }
                }
            }
            Location stand = findCastleStand(center);
            if (stand != null && stand.getWorld() != null) {
                return stand.clone().add(0.0D, 0.15D, 0.0D);
            }
            return new Location(world, center.getX(), prefY + 0.15D, center.getZ());
        }

        private static boolean isDropSafe(World world, int x, int y, int z) {
            if (world == null || y < world.getMinHeight() || y >= world.getMaxHeight() - 1) {
                return false;
            }
            Material feet = world.getBlockAt(x, y, z).getType();
            Material head = world.getBlockAt(x, y + 1, z).getType();
            return !feet.isSolid() && !head.isSolid();
        }

        private void restoreRemaining() {
            List<Location> restored = new ArrayList<>(originalBlocks.keySet());
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                Location loc = entry.getKey();
                BlockData data = entry.getValue();
                if (loc.getWorld() != null && data != null) {
                    loc.getBlock().setBlockData(data, false);
                }
            }
            originalBlocks.clear();
            unstuckCastlePlayers(center, restored);
        }
    }

    private static final class LaserBlockClaim {
        private final Location location;
        private final BlockData original;
        private int refs;

        private LaserBlockClaim(Location location, BlockData original, int refs) {
            this.location = location;
            this.original = original;
            this.refs = refs;
        }
    }
}
