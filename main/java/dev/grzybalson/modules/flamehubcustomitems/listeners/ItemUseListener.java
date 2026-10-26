package dev.grzybalson.modules.flamehubcustomitems.listeners;

import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.CooldownManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.CustomItemType;
import dev.grzybalson.modules.flamehubcustomitems.manager.EffectManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.ItemManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ItemUseListener implements Listener {

    private final FlameHubConfigManager configManager;
    private final ItemManager itemManager;
    private final CooldownManager cooldownManager;
    private final EffectManager effectManager;

    public ItemUseListener(FlameHubConfigManager configManager, ItemManager itemManager,
                           CooldownManager cooldownManager, EffectManager effectManager) {
        this.configManager = configManager;
        this.itemManager = itemManager;
        this.cooldownManager = cooldownManager;
        this.effectManager = effectManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        CustomItemType type = itemManager.getCustomItemType(item);
        if (type == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (cooldownManager.isOnCooldown(player, type)) {
            String remainingTime = cooldownManager.getRemainingSecondsFormatted(player, type);
            player.sendMessage(configManager.getMessage("cooldown-active", "{TIME}", remainingTime));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }

        cooldownManager.applyCooldown(player, type);

        switch (type) {
            case YODA -> {
                int duration = configManager.getYodaDurationSeconds();
                effectManager.activateYoda(player, duration);

                player.sendMessage(configManager.getMessage("yoda-activated"));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);
                player.getWorld().spawnParticle(
                        Particle.TOTEM,
                        player.getLocation().add(0, 1.0, 0),
                        25, 0.4, 0.6, 0.4, 0.1
                );
            }
            case VADER -> {
                int durationSeconds = configManager.getVaderDurationSeconds();
                effectManager.activateVader(player, durationSeconds);

                String percentStr = String.valueOf((int) configManager.getVaderDamageIncreasePercent());
                player.sendMessage(configManager.getMessage(
                        "vader-activated",
                        "{PERCENT}", percentStr,
                        "{DURATION}", String.valueOf(durationSeconds)
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.9f, 0.8f);
                player.getWorld().spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        player.getLocation().add(0, 1.0, 0),
                        30, 0.3, 0.6, 0.3, 0.05
                );
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cooldownManager.cleanupPlayer(player.getUniqueId());
        effectManager.cleanupPlayer(player.getUniqueId());
    }
}
