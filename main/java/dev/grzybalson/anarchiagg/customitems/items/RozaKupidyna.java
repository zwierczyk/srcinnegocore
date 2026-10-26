package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.event.Listener;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;

public class RozaKupidyna extends BukkitRunnable implements Listener {
    private final JavaPlugin plugin;
    private final String rozaName;
    public final Material rozaMaterial;

    public RozaKupidyna(JavaPlugin plugin) {
        this.plugin = plugin;

        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);

        String klataMeduzyItem = eventowkiConfig.getString("eventowki.rozakupidyna.item", "POPPY");
        rozaName = eventowkiConfig.getString("eventowki.rozakupidyna.name", "&c&lRóża kupidyna").replace("&", "§");
        rozaMaterial = Material.valueOf(klataMeduzyItem.toUpperCase());
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getItemInOffHand();
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (!EventItemRegistry.matches(plugin, "rozakupidyna", item)) {
                continue;
            }

            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 0, false, false));
        }
    }
}
