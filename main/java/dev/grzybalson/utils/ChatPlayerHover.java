package dev.grzybalson.utils;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ChatConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class ChatPlayerHover {

    private ChatPlayerHover() {
    }

    public static Component build(HollyCore plugin, Player player) {
        return build(plugin, player, null, null, null, null);
    }

    /** Wariant z gotowym LuckPerms/prefixem/rangą — bez ponownego odpytywania LuckPerms. */
    public static Component build(HollyCore plugin, Player player, LuckPerms lp, User user,
                                  String prefixValue, String rankGroup) {
        ChatConfig config = plugin.getConfigManager().getChatConfig();
        if (!config.isHoverEnabled()) {
            return Component.empty();
        }

        List<String> lines = config.getHoverLines();
        boolean needPrefix = false;
        boolean needRank = false;
        boolean needMoney = false;
        boolean needKills = false;
        boolean needDeaths = false;
        boolean needPlaytime = false;
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            needPrefix |= raw.contains("{prefix}");
            needRank |= raw.contains("{rank}");
            needMoney |= raw.contains("{money}");
            needKills |= raw.contains("{kills}");
            needDeaths |= raw.contains("{deaths}");
            needPlaytime |= raw.contains("{playtime}");
        }

        String prefix = needPrefix
            ? (prefixValue != null ? prefixValue : resolvePrefix(lp, user, player))
            : "";
        String rank = needRank
            ? (rankGroup != null ? formatRank(rankGroup) : resolveRank(lp, user, player))
            : "Gracz";
        String money = needMoney ? formatMoney(plugin, player) : "";
        int kills = needKills ? resolveKills(plugin, player) : 0;
        int deaths = needDeaths ? resolveDeaths(plugin, player) : 0;
        String playtime = needPlaytime ? formatPlaytime(resolvePlaytimeMs(plugin, player)) : "";

        Component out = Component.empty();
        boolean first = true;
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = applyPlaceholders(raw, player.getName(), prefix, rank, money, kills, deaths, playtime);
            if (!first) {
                out = out.append(Component.newline());
            }
            first = false;
            if (line.isBlank()) {
                out = out.append(Component.text(" "));
            } else {
                out = out.append(ColorUtils.colorizeComponent(line));
            }
        }
        return out;
    }

    public static Component apply(HollyCore plugin, Player player, Component component) {
        return apply(plugin, player, component, null, null, null, null);
    }

    /** Wariant z gotowym LuckPerms/prefixem/rangą — bez ponownego odpytywania LuckPerms. */
    public static Component apply(HollyCore plugin, Player player, Component component,
                                  LuckPerms lp, User user, String prefixValue, String rankGroup) {
        if (component == null || player == null) {
            return component;
        }
        ChatConfig config = plugin.getConfigManager().getChatConfig();
        if (!config.isHoverEnabled()) {
            return component;
        }
        Component hover = build(plugin, player, lp, user, prefixValue, rankGroup);
        String command = config.getHoverClickCommand()
            .replace("{player}", player.getName())
            .replace("{nick}", player.getName());
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        return component
            .hoverEvent(HoverEvent.showText(hover))
            .clickEvent(ClickEvent.runCommand("/" + command));
    }

    private static String applyPlaceholders(
        String template,
        String playerName,
        String prefix,
        String rank,
        String money,
        int kills,
        int deaths,
        String playtime
    ) {
        return template
            .replace("{prefix}", prefix == null ? "" : prefix)
            .replace("{player}", playerName)
            .replace("{nick}", playerName)
            .replace("{rank}", rank == null ? "Gracz" : rank)
            .replace("{money}", money)
            .replace("{kills}", String.valueOf(kills))
            .replace("{deaths}", String.valueOf(deaths))
            .replace("{playtime}", playtime);
    }

    private static String resolvePrefix(LuckPerms lp, User user, Player player) {
        try {
            LuckPerms api = lp != null ? lp : LuckPermsProvider.get();
            User target = user != null ? user : api.getUserManager().getUser(player.getUniqueId());
            String prefix = LuckPermsGroups.bestPrefix(api, target);
            return prefix == null ? "" : prefix;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String resolveRank(LuckPerms lp, User user, Player player) {
        try {
            LuckPerms api = lp != null ? lp : LuckPermsProvider.get();
            User target = user != null ? user : api.getUserManager().getUser(player.getUniqueId());
            if (target != null) {
                return formatRank(LuckPermsGroups.bestGroup(api, target));
            }
        } catch (Throwable ignored) {
        }
        return "Gracz";
    }

    private static String formatRank(String group) {
        if (group == null || group.isBlank()) {
            return "Gracz";
        }
        return group.substring(0, 1).toUpperCase(Locale.ROOT) + group.substring(1);
    }

    private static String formatMoney(HollyCore plugin, Player player) {
        double balance = 0.0D;
        if (plugin.getEconomy() != null) {
            balance = plugin.getEconomy().getBalance(player);
        }
        String compact = MoneyAmountUtil.formatCompact(balance);
        if (compact.startsWith("$") || compact.endsWith("$")) {
            return compact;
        }
        return "$" + compact;
    }

    private static int resolveKills(HollyCore plugin, Player player) {
        if (plugin.getAchievementManager() != null) {
            var data = plugin.getAchievementManager().getCachedData(player.getUniqueId());
            if (data != null) {
                return Math.max(0, data.getKills());
            }
        }
        try {
            return Math.max(0, player.getStatistic(Statistic.PLAYER_KILLS));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int resolveDeaths(HollyCore plugin, Player player) {
        if (plugin.getAchievementManager() != null) {
            var data = plugin.getAchievementManager().getCachedData(player.getUniqueId());
            if (data != null) {
                return Math.max(0, data.getDeaths());
            }
        }
        try {
            return Math.max(0, player.getStatistic(Statistic.DEATHS));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long resolvePlaytimeMs(HollyCore plugin, Player player) {
        if (plugin.getStatsDataManager() != null) {
            var stats = plugin.getStatsDataManager().getCachedStats(player.getUniqueId());
            if (stats != null) {
                return Math.max(0L, stats.getTotalPlaytime());
            }
        }
        try {
            return player.getStatistic(Statistic.PLAY_ONE_MINUTE) * 50L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String formatPlaytime(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;
        hours %= 24L;
        minutes %= 60L;
        if (days > 0L) {
            return days + "d, " + hours + "h, " + minutes + "min";
        }
        if (hours > 0L) {
            return hours + "h, " + minutes + "min";
        }
        if (minutes > 0L) {
            return minutes + "min";
        }
        return seconds + "s";
    }
}
