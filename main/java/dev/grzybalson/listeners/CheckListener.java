package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CheckConfig;
import dev.grzybalson.gui.CheckMenuGUI;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CheckListener implements Listener {

    private final HollyCore plugin;

    public CheckListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        var checks = plugin.getCheckManager();
        if (checks == null || !checks.isBeingChecked(player)) {
            return;
        }

        String command = event.getMessage().toLowerCase();

        if (checks.isCommandAllowed(command)) {
            return;
        }

        event.setCancelled(true);
        CheckConfig config = plugin.getConfigManager().getCheckConfig();
        player.sendMessage(ColorUtils.colorizeComponent(config.getMessageBeingChecked()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var checks = plugin.getCheckManager();
        if (checks != null) {
            checks.handlePlayerQuit(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof CheckMenuGUI gui)) {
            return;
        }

        event.setCancelled(true);

        if (event.getCurrentItem() == null) {
            return;
        }

        gui.handleClick(event.getRawSlot());
    }
}
