package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.gui.TpaConfirmGUI;
import dev.grzybalson.managers.TeleportRequestManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class TpaConfirmListener implements Listener {

    private final HollyCore plugin;

    public TpaConfirmListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof TpaConfirmGUI gui)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        String action = NBTUtils.getString(clickedItem, TpaConfirmGUI.ACTION_KEY);
        if (action == null) {
            return;
        }

        if (TpaConfirmGUI.ACTION_CANCEL.equals(action)) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            player.sendMessage(ColorUtils.info("Anulowano prosbe o teleportacje."));
            return;
        }

        if (TpaConfirmGUI.ACTION_ACCEPT.equals(action)) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            sendRequest(player, gui);
        }
    }

    private void sendRequest(Player sender, TpaConfirmGUI gui) {
        Player target = Bukkit.getPlayer(gui.getTargetUuid());
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return;
        }

        if (target.equals(sender)) {
            sender.sendMessage(ColorUtils.error("Nie mozesz wyslac prosby do siebie!"));
            return;
        }

        if (plugin.getTeleportRequestManager().isOnCooldown(sender.getUniqueId())) {
            long remaining = plugin.getTeleportRequestManager().getRemainingCooldown(sender.getUniqueId());
            sender.sendMessage(ColorUtils.error("Poczekaj jeszcze " + (remaining / 1000 + 1) + "s przed wyslaniem kolejnej prosby!"));
            return;
        }

        plugin.getTeleportRequestManager().createRequest(sender, target, TeleportRequestManager.RequestType.TPA);

        sender.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getTpaRequestSent()
                .replace("{player}", target.getName())
        ));

        Component acceptButton = ColorUtils.colorizeComponent("&a[AKCEPTUJ]")
            .clickEvent(ClickEvent.runCommand("/tpaccept"))
            .hoverEvent(HoverEvent.showText(ColorUtils.colorizeComponent("&7Kliknij, aby zaakceptowac")));

        Component denyButton = ColorUtils.colorizeComponent("&c[ODRZUC]")
            .clickEvent(ClickEvent.runCommand("/tpdeny"))
            .hoverEvent(HoverEvent.showText(ColorUtils.colorizeComponent("&7Kliknij, aby odrzucic")));

        target.sendMessage(ColorUtils.colorizeComponent(
            plugin.getConfigManager().getMessagesConfig().getTpaRequestReceived()
                .replace("{player}", sender.getName())
        ));
        target.sendMessage(acceptButton.append(Component.text(" ")).append(denyButton));
    }
}
