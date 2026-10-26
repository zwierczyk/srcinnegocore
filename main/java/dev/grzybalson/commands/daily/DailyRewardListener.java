package dev.grzybalson.commands.daily;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DailyRewardListener implements Listener {

    private final DailyRewardManager manager;
    private final Map<UUID, Long> clickCooldowns = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MS = 1000L; // 1 sekunda cooldown na kliknięcia

    public DailyRewardListener(DailyRewardManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.loadPlayer(event.getPlayer().getUniqueId(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clickCooldowns.remove(event.getPlayer().getUniqueId());
        manager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getInventory().getHolder() instanceof DailyRewardManager.DailyMainHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return;
            }

            // Sprawdź cooldown przed przetworzeniem kliknięcia
            UUID playerId = player.getUniqueId();
            long now = System.currentTimeMillis();
            Long lastClick = clickCooldowns.get(playerId);
            if (lastClick != null && (now - lastClick) < CLICK_COOLDOWN_MS) {
                return; // Ignoruj kliknięcie, jeśli jest na cooldownie
            }
            clickCooldowns.put(playerId, now);

            for (int day : manager.getConfiguredDays()) {
                if (event.getRawSlot() == manager.getDaySlot(day)) {
                    if (!event.isLeftClick() && !event.isRightClick()) {
                        return;
                    }
                    manager.claim(player, day);
                    return;
                }
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof DailyRewardManager.DailyEditHolder) {
            if (event.getClickedInventory() == null) {
                return;
            }
            if (event.getRawSlot() >= event.getInventory().getSize()) {
                return;
            }
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof DailyRewardManager.DailyEditHolder holder)) {
            return;
        }

        manager.saveEditorInventory(holder.getDay(), event.getInventory());
        player.sendMessage(ColorUtils.colorizeComponent(manager.getMessage("editor-saved").replace("{day}", String.valueOf(holder.getDay()))));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }
}
