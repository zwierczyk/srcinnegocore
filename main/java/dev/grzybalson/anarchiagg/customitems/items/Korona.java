package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.event.Listener;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;

public class Korona extends BukkitRunnable implements Listener {
    private final JavaPlugin plugin;
    private final String koronaName;
    public final Material koronaMaterial;

    public Korona(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);

        String klataMeduzyItem = eventowkiConfig.getString("eventowki.korona.item", "GOLDEN_HELMET");
        koronaName = eventowkiConfig.getString("eventowki.korona.name", "&6Korona ANARCHII").replace("&", "§");
        koronaMaterial = Material.valueOf(klataMeduzyItem.toUpperCase());
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getHelmet();

            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (!EventItemRegistry.matches(plugin, "korona", item)) {
                continue;
            }

            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 100, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 2, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 100, 0, false, false));
        }
    }
}
