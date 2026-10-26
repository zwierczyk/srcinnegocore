package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class WampirzeJablko implements Listener {
    private final JavaPlugin plugin;
    private final String jablkoName;
    private final Material jablkoMaterial;
    private final int time;
    private final int cooldownSeconds;

    public WampirzeJablko(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);

        String parawanItem = eventowkiConfig.getString("eventowki.jablko.item", "ENCHANTED_GOLDEN_APPLE");
        jablkoName = eventowkiConfig.getString("eventowki.jablko.name", "§c§lWampirze jabłko");
        time = eventowkiConfig.getInt("eventowki.jablko.time", 6);
        cooldownSeconds = eventowkiConfig.getInt("eventowki.jablko.cooldown", 0);
        jablkoMaterial = Material.valueOf(parawanItem.toUpperCase());
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack jablko = event.getItem();
        if (EventItemRegistry.matches(plugin, "jablko", jablko)) {
            if (!EventItemUseGuard.tryUse(plugin, player)) {
                event.setCancelled(true);
                return;
            }
            if (EventItemUseGuard.hasItemCooldown(plugin, player, "jablko", jablko, cooldownSeconds)) {
                event.setCancelled(true);
                return;
            }
            EventItemUseGuard.applyItemCooldown(plugin, player, "jablko", jablko, cooldownSeconds);
            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 20 * time, 1));
        }
    }
}
