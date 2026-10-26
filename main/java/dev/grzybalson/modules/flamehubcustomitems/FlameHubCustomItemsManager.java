package dev.grzybalson.modules.flamehubcustomitems;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import dev.grzybalson.modules.flamehubcustomitems.listeners.CombatListener;
import dev.grzybalson.modules.flamehubcustomitems.listeners.GuiListener;
import dev.grzybalson.modules.flamehubcustomitems.listeners.ItemUseListener;
import dev.grzybalson.modules.flamehubcustomitems.manager.CooldownManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.EffectManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class FlameHubCustomItemsManager {

    private final FlameHubConfigManager configManager;
    private final ItemManager itemManager;
    private final CooldownManager cooldownManager;
    private final EffectManager effectManager;

    public FlameHubCustomItemsManager(HollyCore plugin) {
        this.configManager = new FlameHubConfigManager(plugin);
        this.itemManager = new ItemManager(plugin, configManager);
        this.cooldownManager = new CooldownManager(configManager);
        this.effectManager = new EffectManager();

        Bukkit.getPluginManager().registerEvents(
                new ItemUseListener(configManager, itemManager, cooldownManager, effectManager), plugin);
        Bukkit.getPluginManager().registerEvents(
                new CombatListener(plugin, configManager, effectManager), plugin);
        Bukkit.getPluginManager().registerEvents(
                new GuiListener(configManager, itemManager), plugin);
    }

    public void reload() {
        configManager.loadConfig();
    }

    public void shutdown() {
        // no persistent state
    }

    public boolean isYodaActive(Player player) {
        return effectManager.isYodaActive(player);
    }

    public boolean isVaderActive(Player player) {
        return effectManager.isVaderActive(player);
    }

    public double getYodaDamageReductionPercent() {
        return configManager.getYodaDamageReductionPercent();
    }

    public double getVaderDamageIncreasePercent() {
        return configManager.getVaderDamageIncreasePercent();
    }

    public void playYodaHitFx(Player victim) {
        victim.getWorld().spawnParticle(
                Particle.ENCHANTMENT_TABLE,
                victim.getLocation().add(0, 1.0, 0),
                15, 0.3, 0.5, 0.3, 0.1
        );
        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.45f, 1.85f);
    }

    public void playVaderHitFx(Player attacker, Entity target) {
        target.getWorld().spawnParticle(
                Particle.CRIT,
                target.getLocation().add(0, 1.0, 0),
                15, 0.3, 0.4, 0.3, 0.15
        );
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.2f);
    }

    public FlameHubConfigManager getConfigManager() {
        return configManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }
}
