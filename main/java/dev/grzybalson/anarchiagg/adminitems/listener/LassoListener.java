package dev.grzybalson.anarchiagg.adminitems.listener;

import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsInventoryUtil;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;
import dev.grzybalson.anarchiagg.adminitems.listener.GoatListener;
import dev.grzybalson.anarchiagg.util.LocationUtil;
import dev.grzybalson.utils.ColorUtils;

public class LassoListener
implements Listener {
    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final AdminItemsConfig.LassoSettings lassoConfig;
    private final GoatListener goatListener;
    private final Map<UUID, UUID> lassoedPlayers = new HashMap<UUID, UUID>();
    private final Map<UUID, UUID> lassoedBy = new HashMap<UUID, UUID>();
    private final Map<UUID, BukkitTask> lassoTasks = new HashMap<UUID, BukkitTask>();
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Long> targetCooldowns = new HashMap<UUID, Long>();
    private final Map<UUID, Integer> lassoTimeLeft = new HashMap<UUID, Integer>();

    public LassoListener(HollyCore plugin) {
        this(plugin, null);
    }

    public LassoListener(HollyCore plugin, GoatListener goatListener) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
        this.lassoConfig = this.config.getLasso();
        this.goatListener = goatListener;
    }

    private boolean isLassoItem(ItemStack item) {
        if (item == null || item.getType() != Material.LEAD) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return false;
        }
        return meta.getDisplayName().equals(dev.grzybalson.utils.ColorUtils.colorize(this.lassoConfig.getName()));
    }

    private void forceRemoveElytra(Player player) {
        ItemStack totemStack;
        ItemStack elytra = player.getInventory().getChestplate();
        if (elytra == null || elytra.getType() != Material.ELYTRA) {
            return;
        }
        int totemSlot = AdminItemsInventoryUtil.findStorageTotemSlot(player.getInventory());
        if (totemSlot != -1 && (totemStack = player.getInventory().getItem(totemSlot)) != null) {
            player.getInventory().setChestplate(null);
            if (totemStack.getAmount() > 1) {
                totemStack.setAmount(totemStack.getAmount() - 1);
            } else {
                player.getInventory().setItem(totemSlot, null);
            }
            player.getInventory().setItem(totemSlot, elytra);
            return;
        }
        player.getInventory().setChestplate(null);
        HashMap left = player.getInventory().addItem(new ItemStack[]{elytra});
        if (!left.isEmpty()) {
            ItemCompatibilityUtil.dropItemNaturally(player, elytra);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }
        final Player target = (Player)event.getRightClicked();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!this.isLassoItem(itemInHand)) {
            return;
        }
        if (!this.lassoConfig.isEnabled()) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cTo narz\u0119dzie jest wy\u0142\u0105czone!"));
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(player, this.plugin) || AdminItemsRegionUtil.isInBlockedRegion(target, this.plugin)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cNie mo\u017cesz u\u017cy\u0107 lassa na chronionym terenie!"));
            return;
        }
        if (this.isInActiveGoatMatch(player) || this.isInActiveGoatMatch(target)) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.lassoConfig.getName() + " &8:: &cLasso zostalo zerwane, bo gracz jest w pojedynku kozy!"));
            return;
        }
        if (this.lassoedPlayers.containsKey(player.getUniqueId()) || this.lassoedBy.containsKey(player.getUniqueId())) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cJu\u017c u\u017cywasz lassa!"));
            return;
        }
        if (this.lassoedPlayers.containsKey(target.getUniqueId()) || this.lassoedBy.containsKey(target.getUniqueId())) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cTen gracz jest ju\u017c w trakcie interakcji z lassem!"));
            return;
        }
        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUniqueId();
        UUID targetUuid = target.getUniqueId();
        this.clearExpiredTargetCooldowns(now);
        long targetCooldown = this.targetCooldowns.getOrDefault(targetUuid, 0L);
        if (targetCooldown > now) {
            long secondsLeft = Math.max(1L, (targetCooldown - now + 999L) / 1000L);
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(this.lassoConfig.getName() + " &8:: &cTen gracz byl juz na lassie! Poczekaj jeszcze &4" + secondsLeft + "s"));
            return;
        }
        long cooldownTime = (long)this.lassoConfig.getCooldown() * 1000L;
        Long cooldownUntil = this.cooldowns.get(playerUuid);
        if (cooldownUntil != null && now < cooldownUntil) {
            AdminItemsCooldowns.refresh(player, itemInHand.getType(), cooldownUntil);
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cPoczekaj przed ponownym u\u017cyciem lassa!"));
            return;
        }
        this.cooldowns.remove(playerUuid);
        if (player.isFlying() || player.isGliding() || target.isFlying() || target.isGliding()) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cNie mo\u017cesz teraz z\u0142apa\u0107 tego gracza"));
            return;
        }
        this.lassoedPlayers.put(playerUuid, targetUuid);
        this.lassoedBy.put(targetUuid, playerUuid);
        player.setGliding(false);
        target.setGliding(false);
        this.forceRemoveElytra(player);
        this.forceRemoveElytra(target);
        player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &aZ\u0142apa\u0142e\u015b gracza, kliknij ponownie prawym aby go przyci\u0105gn\u0105\u0107 do siebie"));
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
        target.playSound(target.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
        this.cooldowns.put(playerUuid, now + cooldownTime);
        AdminItemsCooldowns.apply(player, itemInHand.getType(), cooldownTime);
        player.setAllowFlight(false);
        player.setCooldown(Material.ELYTRA, 100);
        player.setCooldown(Material.FIREWORK_ROCKET, 100);
        player.setCooldown(Material.FISHING_ROD, 100);
        target.setCooldown(Material.ELYTRA, 100);
        target.setCooldown(Material.FIREWORK_ROCKET, 100);
        target.setCooldown(Material.FISHING_ROD, 100);
        new BukkitRunnable(){

            public void run() {
                player.setAllowFlight(false);
            }
        }.runTaskLater((Plugin)this.plugin, 100L);
        final int duration = this.lassoConfig.getDuration();
        BukkitTask task = new BukkitRunnable(){
            int timeLeft;
            {
                this.timeLeft = duration;
            }

            public void run() {
                if (!LassoListener.this.lassoedPlayers.containsKey(player.getUniqueId())) {
                    this.cancel();
                    return;
                }
                player.setGliding(false);
                target.setGliding(false);
                LassoListener.this.forceRemoveElytra(player);
                LassoListener.this.forceRemoveElytra(target);
                LassoListener.this.lassoTimeLeft.put(player.getUniqueId(), this.timeLeft);
                --this.timeLeft;
                if (this.timeLeft <= 0) {
                    LassoListener.this.endLasso(player, target, true);
                    LassoListener.this.lassoTimeLeft.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }
                if (LassoListener.this.isInActiveGoatMatch(player) || LassoListener.this.isInActiveGoatMatch(target)) {
                    LassoListener.this.endLasso(player, target, false);
                    LassoListener.this.lassoTimeLeft.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }
                if (AdminItemsRegionUtil.isInBlockedRegion(player, LassoListener.this.plugin) || AdminItemsRegionUtil.isInBlockedRegion(target, LassoListener.this.plugin)) {
                    LassoListener.this.endLasso(player, target, false);
                    LassoListener.this.lassoTimeLeft.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }
                int maxDistance = LassoListener.this.lassoConfig.getMaxDistance();
                if (player.getLocation().distance(target.getLocation()) > (double)maxDistance) {
                    target.teleport(LocationUtil.preservingPlayerRotation(target, LassoListener.this.inFrontOf(player)));
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 20L);
        this.lassoTasks.put(playerUuid, task);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player target;
        Player player = event.getPlayer();
        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!this.isLassoItem(itemInHand)) {
            return;
        }
        long cooldownTime = (long)this.lassoConfig.getCooldown() * 1000L;
        if ((!this.cooldowns.containsKey(player.getUniqueId()) || System.currentTimeMillis() >= this.cooldowns.get(player.getUniqueId()) - (cooldownTime - 500L)) && this.lassoedPlayers.containsKey(player.getUniqueId()) && (target = Bukkit.getPlayer((UUID)this.lassoedPlayers.get(player.getUniqueId()))) != null) {
            this.endLasso(player, target, true);
        }
    }

    @EventHandler
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        if (this.lassoedBy.containsKey(player.getUniqueId()) || this.lassoedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.setGliding(false);
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cNie mo\u017cesz u\u017cywa\u0107 elytry podczas dzia\u0142ania lassa!"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID catcherUuid;
        UUID uuid = event.getPlayer().getUniqueId();
        UUID targetUuid = this.lassoedPlayers.get(uuid);
        if (targetUuid != null) {
            this.clearLassoState(uuid, targetUuid);
        }
        if ((catcherUuid = this.lassoedBy.get(uuid)) != null) {
            this.clearLassoState(catcherUuid, uuid);
        }
        this.cooldowns.remove(uuid);
    }

    private void endLasso(Player player, Player target, boolean teleport) {
        if (teleport && (AdminItemsRegionUtil.isInBlockedRegion(player, this.plugin) || AdminItemsRegionUtil.isInBlockedRegion(target, this.plugin))) {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cNie mo\u017cesz przyci\u0105gn\u0105\u0107 gracza z chronionego terenu!"));
            teleport = false;
        }
        if (teleport) {
            target.teleport(LocationUtil.preservingPlayerRotation(target, this.inFrontOf(player)));
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &aZerwano! Przyci\u0105gni\u0119to gracza do siebie..."));
        } else {
            player.sendMessage(dev.grzybalson.utils.ColorUtils.colorize("&6&l\u029f\u1d00\ua731\ua731\u1d0f &8:: &cLasso zosta\u0142o zerwane."));
        }
        this.clearLassoState(player.getUniqueId(), target.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
        target.playSound(target.getLocation(), Sound.ENTITY_HORSE_SADDLE, 1.0f, 1.0f);
    }

    private Location inFrontOf(Player player) {
        Location origin = player.getLocation();
        return origin.clone();
    }

    private void clearLassoState(UUID playerUuid, UUID targetUuid) {
        this.lassoedPlayers.remove(playerUuid);
        if (targetUuid != null) {
            this.lassoedBy.remove(targetUuid);
            this.applyTargetCooldown(targetUuid);
        }
        this.lassoTimeLeft.remove(playerUuid);
        BukkitTask task = this.lassoTasks.remove(playerUuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private boolean isInActiveGoatMatch(Player player) {
        return player != null && this.goatListener != null && this.goatListener.isActiveMatchParticipant(player.getUniqueId());
    }

    private void applyTargetCooldown(UUID targetUuid) {
        this.targetCooldowns.put(targetUuid, System.currentTimeMillis() + AdminItemsCooldowns.secondsToMillis(Math.max(0, this.lassoConfig.getTargetCooldown())));
    }

    private void clearExpiredTargetCooldowns(long now) {
        this.targetCooldowns.entrySet().removeIf(entry -> (Long)entry.getValue() <= now);
    }

    public Map<UUID, Integer> getLassoTimeLeft() {
        return this.lassoTimeLeft;
    }

    public Map<UUID, UUID> getLassoedPlayers() {
        return this.lassoedPlayers;
    }

    public Map<UUID, UUID> getLassoedBy() {
        return this.lassoedBy;
    }
}
