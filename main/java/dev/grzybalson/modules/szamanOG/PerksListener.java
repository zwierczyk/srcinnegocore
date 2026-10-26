package dev.grzybalson.modules.szaman;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class PerksListener implements Listener {

    private final PerksManager manager;

    public PerksListener(PerksManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        manager.removeLiveHealth(event.getPlayer());
        manager.unloadPlayer(event.getPlayer().getUniqueId());
        manager.clearFireworkBlock(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(dev.grzybalson.HollyCore.getInstance(), () -> {
            if (event.getPlayer().isOnline()) {
                manager.applyLiveHealth(event.getPlayer());
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof PerksRollGui) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof PerksGui)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            String action = manager.getAction(clicked);
            if ("roll".equals(action)) {
                manager.rollPerk(player);
            } else if ("mystic_key".equals(action)) {
                manager.craftMysticKey(player);
            } else if ("turbo_drop".equals(action)) {
                manager.rollTurbo(player, true);
            } else if ("turbo_ranking".equals(action)) {
                manager.rollTurbo(player, false);
            } else if (manager.identifyPerkItem(clicked) != null) {
                manager.upgradeFromItem(player, clicked);
            }
            return;
        }

        manager.upgradeFromItem(player, clicked);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PerksGui
            || event.getView().getTopInventory().getHolder() instanceof PerksRollGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof PerksRollGui holder)) {
            return;
        }

        Bukkit.getScheduler().runTask(dev.grzybalson.HollyCore.getInstance(), () -> {
            if (player.isOnline()) {
                manager.finishRollImmediately(player, holder);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPerkUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (manager.identifyPerkItem(item) == null) {
            return;
        }

        event.setCancelled(true);
        manager.upgradeFromItem(event.getPlayer(), item);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        double blockChance = manager.getPerkValue(victimId, PerkType.BLOCK);
        if (rollChance(blockChance)) {
            event.setCancelled(true);
            victim.playSound(victim.getLocation(), manager.getBlockSound(), 0.8F, 1.0F);
            attacker.playSound(attacker.getLocation(), manager.getBlockSound(), 0.5F, 1.1F);
            return;
        }

        HollyCore plugin = HollyCore.getInstance();
        if (plugin.getCustomHitsService() == null && plugin.getPatronManager() == null) {
            double strengthPercent = manager.getPerkValue(attackerId, PerkType.STRENGTH);
            if (strengthPercent > 0.0D) {
                event.setDamage(Math.max(0.0D, event.getDamage() * (1.0D + strengthPercent / 100.0D)));
            }
        }

        double lifestealChance = manager.getPerkValue(attackerId, PerkType.LIFESTEAL);
        if (rollChance(lifestealChance)) {
            double maxHealth = attacker.getMaxHealth();
            double heal = Math.max(0.0D, manager.getLifestealHealAmount());
            if (heal > 0.0D && attacker.getHealth() < maxHealth) {
                attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + heal));
                attacker.playSound(attacker.getLocation(), manager.getLifestealSound(), 0.8F, 1.0F);
            }
        }

        double slownessChance = manager.getPerkValue(attackerId, PerkType.SLOWNESS);
        if (rollChance(slownessChance)) {
            victim.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW,
                manager.getSlownessDurationTicks(),
                manager.getSlownessAmplifier(),
                false,
                true,
                true
            ));
        }

        if (manager.getLevel(attackerId, PerkType.TRAP) > 0 && rollChance(manager.getTrapChancePercent())) {
            long until = System.currentTimeMillis() + (manager.getTrapDurationTicks(attackerId) * 50L);
            manager.blockFireworks(victim.getUniqueId(), until);
            victim.sendMessage(ColorUtils.colorizeComponent("&#00E0FFℹ &8Ξ &cTwoje fajerwerki zostaly chwilowo zablokowane."));
            victim.playSound(victim.getLocation(), manager.getTrapSound(), 0.8F, 1.0F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        long remainingMillis = manager.getFireworkBlockRemainingMillis(player.getUniqueId());
        if (remainingMillis <= 0L) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null && event.getHand() != null) {
            item = event.getHand() == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        }

        if (item == null || item.getType() != Material.FIREWORK_ROCKET) {
            return;
        }

        event.setCancelled(true);
        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie mozesz uzywac fajerwerek jeszcze &f" + seconds + "s&c."));
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }

        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }

    private boolean rollChance(double percent) {
        if (percent <= 0.0D) {
            return false;
        }
        if (percent >= 100.0D) {
            return true;
        }
        return Math.random() * 100.0D < percent;
    }
}
