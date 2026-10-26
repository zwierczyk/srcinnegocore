package dev.grzybalson.anarchiagg.customitems.items;

import dev.grzybalson.anarchiagg.AnarchiaGGSettings;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;

public class TotemUlaskawienia implements Listener {
    public static String totemulaskawieniaName = "§5§lTotem ułaskawienia";
    private final Set<UUID> playersToClearTotem = new HashSet<UUID>();
    private final JavaPlugin plugin;

    public TotemUlaskawienia(JavaPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration eventowkiConfig = AnarchiaGGSettings.getConfig(plugin);

        totemulaskawieniaName = eventowkiConfig
                .getString("eventowki.totemulaskawienia.name", "§5§lTotem ułaskawienia")
                .replace("&", "§");
    }

    @EventHandler
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack main = victim.getInventory().getItemInMainHand();
        ItemStack off = victim.getInventory().getItemInOffHand();

        boolean mainTotem = EventItemRegistry.matches(plugin, "totemulaskawienia", main);
        boolean offTotem = EventItemRegistry.matches(plugin, "totemulaskawienia", off);

        if (mainTotem || offTotem) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        ItemStack main = victim.getInventory().getItemInMainHand();
        ItemStack off = victim.getInventory().getItemInOffHand();

        boolean mainTotem = EventItemRegistry.matches(plugin, "totemulaskawienia", main);
        boolean offTotem = EventItemRegistry.matches(plugin, "totemulaskawienia", off);

        if (event.getKeepInventory()) return;

        if (mainTotem || offTotem) {
            event.getDrops().clear();
            event.setKeepInventory(true);
            playersToClearTotem.add(victim.getUniqueId());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        removeTotemFromPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeTotemFromPlayer(event.getPlayer());
    }

    private void removeTotemFromPlayer(Player player) {
        if (playersToClearTotem.contains(player.getUniqueId())) {
            ItemStack mainNow = player.getInventory().getItemInMainHand();
            ItemStack offNow = player.getInventory().getItemInOffHand();

            if (EventItemRegistry.matches(plugin, "totemulaskawienia", mainNow)) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else if (EventItemRegistry.matches(plugin, "totemulaskawienia", offNow)) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }
            playersToClearTotem.remove(player.getUniqueId());
        }
    }
}
