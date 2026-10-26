package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.MessagesConfig;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatCooldownListener implements Listener {

    private final HollyCore plugin;
    private final Map<UUID, Long> chatCooldowns = new ConcurrentHashMap<>();

    public ChatCooldownListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        MessagesConfig config = plugin.getConfigManager().getMessagesConfig();

        if (player.isOp() || player.hasPermission(config.getChatCooldownBypassPermission())) {
            return;
        }

        int cooldownSeconds = config.getChatCooldownSeconds();
        if (cooldownSeconds <= 0) {
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = cooldownSeconds * 1000L;

        Long lastChat = chatCooldowns.get(uuid);
        if (lastChat != null && now - lastChat < cooldownMs) {
            long remaining = (cooldownMs - (now - lastChat)) / 1000;
            if (remaining < 1) remaining = 1;

            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(
                config.getChatCooldownMessage().replace("{time}", String.valueOf(remaining))
            ));
            return;
        }

        chatCooldowns.put(uuid, now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        chatCooldowns.remove(event.getPlayer().getUniqueId());
    }
}
