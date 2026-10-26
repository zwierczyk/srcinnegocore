package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.commands.ChatCommand;
import dev.grzybalson.config.configs.ChatConfig;
import dev.grzybalson.managers.IgnoreManager;
import dev.grzybalson.utils.ChatInputSanitizer;
import dev.grzybalson.utils.ChatLinkifier;
import dev.grzybalson.utils.ChatPlayerHover;
import dev.grzybalson.utils.ColorUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class ChatFormatListener implements Listener {

    private final HollyCore plugin;

    public ChatFormatListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (ChatInputSanitizer.rejectIfBlocked(player, PlainTextComponentSerializer.plainText().serialize(event.message()))) {
            event.setCancelled(true);
            return;
        }

        // Incognito mode - use normal chat format (without incognito prefix)
        var incognitoManager = plugin.getIncognitoManager();
        if (incognitoManager != null && incognitoManager.isIncognito(player)) {
            // Let the chat continue normally as if they don't have incognito
            // Don't use broadcastDisguisedChat - just continue with normal formatting below
        }

        var checks = plugin.getCheckManager();
        if (checks != null && checks.isParticipant(player)) {
            Player admin = checks.isBeingChecked(player)
                ? checks.getCheckingAdmin(player)
                : player;
            Player target = checks.isChecking(player)
                ? checks.getCheckedPlayer(player)
                : player;
            event.setCancelled(true);
            net.kyori.adventure.text.Component isolated = net.kyori.adventure.text.Component.text(player.getName() + ": ")
                .append(event.message());
            player.sendMessage(isolated);
            if (admin != null && admin != player) {
                admin.sendMessage(isolated);
            }
            if (target != null && target != player && target != admin) {
                target.sendMessage(isolated);
            }
            return;
        }

        // Chat on/off check
        if (!ChatCommand.isChatEnabled()) {
            ChatConfig chatConfig = plugin.getConfigManager().getChatConfig();
            if (!player.hasPermission(chatConfig.getChatPermission())) {
                event.setCancelled(true);
                player.sendMessage(ColorUtils.colorizeComponent(chatConfig.getChatIsDisabledMessage()));
                return;
            }
        }

        Component message = event.message();

        Component hoverText = ChatPlayerHover.build(plugin, player);

        Component playerName = Component.text(player.getName())
            .color(NamedTextColor.WHITE)
            .hoverEvent(HoverEvent.showText(hoverText))
            .clickEvent(ClickEvent.runCommand("/hollyplayeraction " + player.getName()));

        Component parsedMessage = message
            .hoverEvent(HoverEvent.showText(hoverText))
            .clickEvent(ClickEvent.runCommand("/hollyplayeraction " + player.getName()));

        parsedMessage = ChatLinkifier.apply(parsedMessage);

        Component formattedMessage = Component.empty()
            .append(playerName)
            .append(Component.text(": ", NamedTextColor.GRAY))
            .append(parsedMessage);

        event.setCancelled(true);
        IgnoreManager ignore = plugin.getIgnoreManager();
        if (ignore != null) {
            ignore.sendVisibleChat(player, formattedMessage);
        } else {
            Bukkit.broadcast(formattedMessage);
        }
    }
}
