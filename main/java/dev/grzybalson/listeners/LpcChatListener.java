package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.commands.ChatCommand;
import dev.grzybalson.config.configs.LpcChatConfig;
import dev.grzybalson.managers.ClanManager;
import dev.grzybalson.managers.IgnoreManager;
import dev.grzybalson.managers.IncognitoManager;
import dev.grzybalson.utils.ChatAdFilter;
import dev.grzybalson.utils.ChatInputSanitizer;
import dev.grzybalson.utils.ChatLinkifier;
import dev.grzybalson.utils.ChatPlayerHover;
import dev.grzybalson.utils.ColorUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LpcChatListener implements Listener {

    private final HollyCore plugin;
    private final Map<UUID, Long> lastMessages = new ConcurrentHashMap<>();
    private final boolean papiEnabled;

    public LpcChatListener(HollyCore plugin) {
        this.plugin = plugin;
        this.papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        LpcChatConfig config = plugin.getConfigManager().getLpcChatConfig();
        if (!config.isEnableChat()) {
            return;
        }

        Player player = event.getPlayer();
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
        if (!ChatCommand.isChatEnabled() && !player.hasPermission(plugin.getConfigManager().getChatConfig().getChatPermission())) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(config.getChatDisabledMessage()));
            return;
        }

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (message == null) {
            message = "";
        }
        if (ChatInputSanitizer.rejectIfBlocked(player, message)) {
            event.setCancelled(true);
            return;
        }
        if (ChatAdFilter.rejectIfBlocked(plugin, player, message)) {
            event.setCancelled(true);
            return;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);

        if (!player.hasPermission(config.getBypassBlockedWordsPermission())) {
            for (String blocked : config.getBlockedWorld()) {
                if (blocked == null || blocked.isBlank()) {
                    continue;
                }
                if (normalizedMessage.contains(blocked.toLowerCase(Locale.ROOT))) {
                    event.setCancelled(true);
                    player.sendMessage(ColorUtils.colorizeComponent(config.getBlockedMessage()));
                    return;
                }
            }
        }

        User user;
        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
            user = lp.getUserManager().getUser(player.getUniqueId());
        } catch (Exception exception) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(config.getGroupNotConfiguredMessage()));
            return;
        }

        if (user == null) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(config.getGroupNotConfiguredMessage()));
            return;
        }

        java.util.Set<String> ownedGroups = dev.grzybalson.utils.LuckPermsGroups.collect(lp, user);
        String rankGroup = dev.grzybalson.utils.LuckPermsGroups.bestGroup(ownedGroups, user.getPrimaryGroup());
        LpcChatConfig.ChatGroup chatGroup = config.findBestGroup(ownedGroups);
        if (chatGroup == null) {
            chatGroup = config.findGroup(rankGroup);
        }
        if (chatGroup == null) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(config.getGroupNotConfiguredMessage()));
            return;
        }

        if (!player.isOp() && !player.hasPermission(config.getBypassCooldownPermission())) {
            long now = System.currentTimeMillis();
            long last = lastMessages.getOrDefault(player.getUniqueId(), 0L);
            long cooldown = Math.max(0L, chatGroup.getCooldown());
            if (now - last < cooldown) {
                long remaining = cooldown - (now - last);
                event.setCancelled(true);
                String formattedRemaining = formatCooldown(remaining);
                player.sendMessage(ColorUtils.colorizeComponent(
                    config.getCooldownMessage()
                        .replace("{TIME}ms", formattedRemaining)
                        .replace("{TIME}", formattedRemaining)
                ));
                return;
            }
            lastMessages.put(player.getUniqueId(), now);
        }

        String prefix = dev.grzybalson.utils.LuckPermsGroups.bestPrefix(lp, user);
        String nickColor = "&f";
        if (plugin.getTablistManager() != null) {
            nickColor = plugin.getTablistManager().getRankNameColor(player);
        }

        String chatNick = player.getName();

        String clanTag = "";
        if (chatGroup.getMessageFormat().contains("{CLAN}")) {
            ClanManager clanManager = plugin.getClanManager();
            if (clanManager != null) {
                String clan = clanManager.getCachedPlayerClan(player.getUniqueId());
                if (clan != null && !clan.isBlank()) {
                    clanTag = clan;
                } else if (!clanManager.hasCachedPlayerClanLookup(player.getUniqueId())) {
                    clanManager.preloadPlayerClan(player.getUniqueId());
                }
            }
        }

        String formatted = buildFormattedChat(player, chatGroup, rankGroup, prefix, nickColor, chatNick, clanTag, message);
        Component chatMessage = buildChatComponent(player, formatted, message);
        chatMessage = ChatPlayerHover.apply(plugin, player, chatMessage, lp, user, prefix == null ? "" : prefix, rankGroup);
        chatMessage = ChatLinkifier.apply(chatMessage);

        event.setCancelled(true);
        // Incognito players use normal chat format (no disguised name in chat)
        IgnoreManager ignore = plugin.getIgnoreManager();
        if (ignore != null) {
            ignore.sendVisibleChat(player, chatMessage);
        } else {
            Bukkit.broadcast(chatMessage);
        }
    }

    private String buildFormattedChat(Player player, LpcChatConfig.ChatGroup chatGroup, String rankGroup,
                                      String prefix, String nickColor, String chatNick, String clanTag,
                                      String message) {
        String formatted = chatGroup.getMessageFormat()
            .replace("{TAG}", prefix)
            .replace("{RANK}", rankGroup)
            .replace("{NICK}", nickColor + chatNick)
            .replace("{CLAN}", clanTag);

        if (formatted.contains("%holly_ranking%") || formatted.contains("%core_ranking%") || formatted.contains("%holly_elo%") || formatted.contains("%core_elo%") || formatted.contains("%core_points%")) {
            String rankingVal = "0";
            if (plugin.getEloManager() != null) {
                rankingVal = String.valueOf(plugin.getEloManager().getElo(player.getUniqueId()));
            }
            formatted = formatted.replace("%holly_ranking%", rankingVal)
                                 .replace("%core_ranking%", rankingVal)
                                 .replace("%holly_elo%", rankingVal)
                                 .replace("%core_elo%", rankingVal)
                                 .replace("%core_points%", rankingVal);
        }

        final String starsToken = "⟦HOLLY_ARMORSTARS⟧";
        boolean hasStarsPlaceholder = formatted.contains("%holly_armorstars_icon%")
            || formatted.contains("%holly_armorstars%")
            || formatted.contains("%holly_armor_stars_icon%")
            || formatted.contains("%holly_armor_stars%")
            || formatted.contains("%holly_stars_icon%")
            || formatted.contains("%holly_eqstars%");
        if (hasStarsPlaceholder) {
            formatted = formatted
                .replace("%holly_armorstars_icon%", starsToken)
                .replace("%holly_armor_stars_icon%", starsToken)
                .replace("%holly_stars_icon%", starsToken)
                .replace("%holly_armorstars%", starsToken)
                .replace("%holly_armor_stars%", starsToken)
                .replace("%holly_eqstars%", starsToken);
        }

        if (papiEnabled && formatted.contains("%")) {
            try {
                formatted = PlaceholderAPI.setPlaceholders(player, formatted);
            } catch (Exception ignored) {
            }
            if (formatted.contains("%holly_armorstars_icon%")
                || formatted.contains("%holly_armorstars%")
                || formatted.contains("%holly_armor_stars_icon%")
                || formatted.contains("%holly_armor_stars%")
                || formatted.contains("%holly_stars_icon%")
                || formatted.contains("%holly_eqstars%")) {
                formatted = formatted
                    .replace("%holly_armorstars_icon%", starsToken)
                    .replace("%holly_armor_stars_icon%", starsToken)
                    .replace("%holly_stars_icon%", starsToken)
                    .replace("%holly_armorstars%", starsToken)
                    .replace("%holly_armor_stars%", starsToken)
                    .replace("%holly_eqstars%", starsToken);
            }
        }
        return formatted;
    }

    private Component buildChatComponent(Player player, String formatted, String message) {
        final String starsToken = "⟦HOLLY_ARMORSTARS⟧";
        boolean hasStarsPlaceholder = formatted.contains(starsToken);

        Component chatMessage = ColorUtils.colorizeWithPlainPlaceholder(formatted, "{MESSAGE}", message);

        if (hasStarsPlaceholder) {
            String starsDisplay = dev.grzybalson.utils.ArmorStarsUtil.calculateStarsPlaceholder(plugin, player, "armorstars_icon");
            if (starsDisplay == null || starsDisplay.isBlank()) {
                starsDisplay = "";
            }
            net.kyori.adventure.text.Component starComponent = ColorUtils.colorizeComponent(starsDisplay)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/eqsee " + player.getName()))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(ColorUtils.colorizeComponent(
                    "&6&lPodgląd Ekwipunku &8(&e" + player.getName() + "&8)\n" +
                    "&eKliknij, aby podglądnąć zbroję i broń!"
                )));

            chatMessage = chatMessage.replaceText(net.kyori.adventure.text.TextReplacementConfig.builder()
                .matchLiteral(starsToken)
                .replacement(starComponent)
                .build());
        }
        return chatMessage;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessages.remove(event.getPlayer().getUniqueId());
    }

    private String formatCooldown(long millis) {
        long remaining = Math.max(0L, millis);
        if (remaining < 1000L) {
            return String.format(Locale.US, "%.1fs", remaining / 1000.0D);
        }

        long totalSeconds = (remaining + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0L) {
            return String.format(Locale.US, "%dm %02ds", minutes, seconds);
        }
        return seconds + "s";
    }
}
