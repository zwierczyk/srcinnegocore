package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ChatInputSanitizer;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class ClanChatListener implements Listener {

    private final HollyCore plugin;

    public ClanChatListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        String prefix = plugin.getConfigManager().getClanConfig().getClanChatPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "!";
        }

        if (!message.startsWith(prefix)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String clanMessage = message.substring(prefix.length()).trim();

        if (clanMessage.isEmpty()) {
            return;
        }
        if (ChatInputSanitizer.rejectIfBlocked(player, clanMessage)) {
            return;
        }

        plugin.getClanManager().getPlayerClan(player.getUniqueId()).thenAccept(clanTag -> {
            if (clanTag == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ColorUtils.colorizeComponent(
                        plugin.getConfigManager().getClanConfig().getClanNotInClanMessage()
                    ));
                });
                return;
            }

            plugin.getClanManager().getClan(clanTag).thenAccept(clan -> {
                if (clan == null) return;

                String formattedMessage = plugin.getConfigManager().getClanConfig().getClanChatFormat()
                    .replace("{player}", player.getName())
                    .replace("{PLAYER}", player.getName())
                    .replace("{clan}", clanTag)
                    .replace("{CLAN}", clanTag)
                    .replace("{MESSAGE}", clanMessage)
                    .replace("{message}", clanMessage);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Component chatMessage = ColorUtils.colorizeWithPlainPlaceholder(formattedMessage, "{message}", clanMessage);
                    for (UUID memberUuid : clan.getMembers()) {
                        Player member = Bukkit.getPlayer(memberUuid);
                        if (member != null && member.isOnline()) {
                            var ignore = plugin.getIgnoreManager();
                            if (ignore != null && ignore.shouldHideChat(member, player)) {
                                continue;
                            }
                            member.sendMessage(chatMessage);
                        }
                    }
                });
            });
        });
    }
}
