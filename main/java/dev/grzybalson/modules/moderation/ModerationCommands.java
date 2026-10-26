package dev.grzybalson.modules.moderation;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ModerationCommands implements CommandExecutor, TabCompleter {

    private final ModerationManager manager;

    public ModerationCommands(ModerationManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "ban" -> handleBan(sender, label, args, false);
            case "tempban" -> handleBan(sender, label, args, true);
            case "banip" -> handleBanIp(sender, label, args, false);
            case "tempbanip" -> handleBanIp(sender, label, args, true);
            case "mute" -> handleMute(sender, label, args, false);
            case "tempmute" -> handleMute(sender, label, args, true);
            case "unban" -> handleUnban(sender, label, args);
            case "unmute" -> handleUnmute(sender, label, args);
            case "kick" -> handleKick(sender, label, args);
            case "alts" -> handleAlts(sender, label, args);
            case "wyczysclista" -> handleClearList(sender, label);
            case "zglos" -> handleZglos(sender, label, args);
            default -> false;
        };
    }

    private boolean canUse(CommandSender sender, String permission) {
        // Konsola / RCON zawsze może — inaczej LuckPerms potrafi zablokować console.
        if (!(sender instanceof Player)) {
            return true;
        }
        return sender.hasPermission(permission) || sender.hasPermission("Holly.mod.*");
    }

    private boolean handleBan(CommandSender sender, String label, String[] args, boolean forceTemp) {
        String permission = forceTemp ? "Holly.mod.tempban" : "Holly.mod.ban";
        if (!canUse(sender, permission)) {
            sender.sendMessage(ColorUtils.noPermission(label, permission));
            return true;
        }

        if (args.length < 1 || (forceTemp && args.length < 2)) {
            sender.sendMessage(ColorUtils.usage(forceTemp
                ? "/" + label + " <nick> <czas> [powód...] [-s]"
                : "/" + label + " <nick> [czas] [powód...] [-s]"));
            return true;
        }

        boolean silent = ModerationManager.hasSilentFlag(args);
        String target = args[0];
        long duration = 0L;
        int reasonFrom = 1;

        if (forceTemp) {
            duration = ModerationManager.parseDurationMillis(args[1]);
            if (duration <= 0L) {
                sender.sendMessage(ColorUtils.error("Niepoprawny czas. Przykłady: &f1h&c, &f30m&c, &f1d&c, &f7d"));
                return true;
            }
            reasonFrom = 2;
        } else if (args.length >= 2 && ModerationManager.looksLikeDuration(args[1])) {
            // Kompatybilność z CheckCommand: ban {player} 7d Powód
            duration = ModerationManager.parseDurationMillis(args[1]);
            reasonFrom = 2;
        }

        String reason = ModerationManager.joinReason(args, reasonFrom);
        ModerationManager.BanResult result = manager.ban(sender, target, duration, reason, silent);
        if (result.isMissing()) {
            sender.sendMessage(ColorUtils.error("Nie znaleziono gracza &f" + target + "&c."));
            return true;
        }

        ModerationPunishment ban = result.punishment();
        String silentTag = silent ? " &8[&7silent&8]" : "";
        if (ban.isPermanent()) {
            sender.sendMessage(ColorUtils.success(
                "Zbanowano &f" + ban.name() + " &ana zawsze. &7Powód: &f" + ban.reason() + silentTag
            ));
        } else {
            sender.sendMessage(ColorUtils.success(
                "Zbanowano &f" + ban.name() + " &ana &e" + ModerationManager.formatDuration(ban.remainingMillis(System.currentTimeMillis()))
                    + "&a. &7Powód: &f" + ban.reason() + silentTag
            ));
        }
        return true;
    }

    private boolean handleBanIp(CommandSender sender, String label, String[] args, boolean forceTemp) {
        String permission = forceTemp ? "Holly.mod.tempbanip" : "Holly.mod.banip";
        String fallback = forceTemp ? "Holly.mod.tempban" : "Holly.mod.ban";
        if (!canUse(sender, permission) && !canUse(sender, fallback)) {
            sender.sendMessage(ColorUtils.noPermission(label, permission));
            return true;
        }

        if (args.length < 1 || (forceTemp && args.length < 2)) {
            sender.sendMessage(ColorUtils.usage(forceTemp
                ? "/" + label + " <nick|ip> <czas> [powód...] [-s]"
                : "/" + label + " <nick|ip> [powód...] [-s]"));
            return true;
        }

        boolean silent = ModerationManager.hasSilentFlag(args);
        String target = args[0];
        long duration = 0L;
        int reasonFrom = 1;

        if (forceTemp) {
            duration = ModerationManager.parseDurationMillis(args[1]);
            if (duration <= 0L) {
                sender.sendMessage(ColorUtils.error("Niepoprawny czas. Przykłady: &f1h&c, &f30m&c, &f1d&c, &f7d"));
                return true;
            }
            reasonFrom = 2;
        } else if (args.length >= 2 && ModerationManager.looksLikeDuration(args[1])) {
            duration = ModerationManager.parseDurationMillis(args[1]);
            reasonFrom = 2;
        }

        String reason = ModerationManager.joinReason(args, reasonFrom);
        ModerationManager.BanResult result = manager.banIp(sender, target, duration, reason, silent);
        if (result.isMissing()) {
            sender.sendMessage(ColorUtils.error("Nie znaleziono gracza &f" + target + "&c."));
            return true;
        }
        if (result.isMissingIp()) {
            sender.sendMessage(ColorUtils.error("Brak zapisanego IP dla &f" + target + "&c. Gracz musi się chociaż raz połączyć."));
            return true;
        }
        if (result.isImmune()) {
            sender.sendMessage(ColorUtils.error("Nie można zbanować IP tego gracza."));
            return true;
        }

        ModerationPunishment ban = result.punishment();
        String silentTag = silent ? " &8[&7silent&8]" : "";
        String targetLabel = ban.name() == null || ban.name().isBlank() ? "cel" : ban.name();
        if (ModerationManager.looksLikeIp(targetLabel)) {
            targetLabel = "cel";
        }
        if (ban.isPermanent()) {
            sender.sendMessage(ColorUtils.success(
                "Zbanowano IP gracza &f" + targetLabel + " &ana zawsze. &7Powód: &f" + ban.reason() + silentTag
            ));
        } else {
            sender.sendMessage(ColorUtils.success(
                "Zbanowano IP gracza &f" + targetLabel + " &ana &e"
                    + ModerationManager.formatDuration(ban.remainingMillis(System.currentTimeMillis()))
                    + "&a. &7Powód: &f" + ban.reason() + silentTag
            ));
        }
        return true;
    }

    private boolean handleMute(CommandSender sender, String label, String[] args, boolean forceTemp) {
        String permission = forceTemp ? "Holly.mod.tempmute" : "Holly.mod.mute";
        if (!canUse(sender, permission)) {
            sender.sendMessage(ColorUtils.noPermission(label, permission));
            return true;
        }

        if (args.length < 1 || (forceTemp && args.length < 2)) {
            sender.sendMessage(ColorUtils.usage(forceTemp
                ? "/" + label + " <nick> <czas> [powód...]"
                : "/" + label + " <nick> [czas] [powód...]"));
            return true;
        }

        String target = args[0];
        long duration = 0L;
        int reasonFrom = 1;

        if (forceTemp) {
            duration = ModerationManager.parseDurationMillis(args[1]);
            if (duration <= 0L) {
                sender.sendMessage(ColorUtils.error("Niepoprawny czas. Przykłady: &f1h&c, &f30m&c, &f1d&c, &f7d"));
                return true;
            }
            reasonFrom = 2;
        } else if (args.length >= 2 && ModerationManager.looksLikeDuration(args[1])) {
            duration = ModerationManager.parseDurationMillis(args[1]);
            reasonFrom = 2;
        }

        String reason = ModerationManager.joinReason(args, reasonFrom);
        ModerationManager.MuteResult result = manager.mute(sender, target, duration, reason);
        if (result.isMissing()) {
            sender.sendMessage(ColorUtils.error("Nie znaleziono gracza &f" + target + "&c."));
            return true;
        }

        ModerationPunishment mute = result.punishment();
        if (mute.isPermanent()) {
            sender.sendMessage(ColorUtils.success(
                "Wyciszono &f" + mute.name() + " &ana zawsze. &7Powód: &f" + mute.reason()
            ));
        } else {
            sender.sendMessage(ColorUtils.success(
                "Wyciszono &f" + mute.name() + " &ana &e" + ModerationManager.formatDuration(mute.remainingMillis(System.currentTimeMillis()))
                    + "&a. &7Powód: &f" + mute.reason()
            ));
        }
        return true;
    }

    private boolean handleUnban(CommandSender sender, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("all")) {
            if (!(sender instanceof Player) || sender.isOp() || sender.hasPermission("Holly.mod.unban.all") || sender.hasPermission("Holly.mod.*")) {
                int cleared = manager.unbanAll();
                sender.sendMessage(ColorUtils.success("Usunięto wszystkie aktywne bany &8(&f" + cleared + "&8)&a."));
                return true;
            }
            sender.sendMessage(ColorUtils.noPermission(label + " all", "Holly.mod.unban.all"));
            return true;
        }

        if (!canUse(sender, "Holly.mod.unban")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.mod.unban"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ColorUtils.usage("/" + label + " <nick|ip|all>"));
            return true;
        }

        if (!manager.unban(args[0])) {
            sender.sendMessage(ColorUtils.error(ModerationManager.looksLikeIp(args[0])
                ? "IP &f" + args[0] + " &cnie jest zbanowane."
                : "Gracz &f" + args[0] + " &cnie jest zbanowany."));
            return true;
        }
        sender.sendMessage(ColorUtils.success(ModerationManager.looksLikeIp(args[0])
            ? "Zdjęto bana IP &f" + args[0] + "&a."
            : "Odbanowano gracza &f" + args[0] + "&a."));
        return true;
    }

    private boolean handleUnmute(CommandSender sender, String label, String[] args) {
        if (!canUse(sender, "Holly.mod.unmute")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.mod.unmute"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtils.usage("/" + label + " <nick>"));
            return true;
        }
        if (!manager.unmute(args[0])) {
            sender.sendMessage(ColorUtils.error("Gracz &f" + args[0] + " &cnie jest wyciszony."));
            return true;
        }
        sender.sendMessage(ColorUtils.success("Odciszono gracza &f" + args[0] + "&a."));
        return true;
    }

    private boolean handleKick(CommandSender sender, String label, String[] args) {
        if (!canUse(sender, "Holly.mod.kick")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.mod.kick"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtils.usage("/" + label + " <nick> [powód...]"));
            return true;
        }
        String reason = ModerationManager.joinReason(args, 1);
        if (!manager.kick(sender, args[0], reason)) {
            sender.sendMessage(ColorUtils.error("Gracz &f" + args[0] + " &cnie jest online."));
            return true;
        }
        sender.sendMessage(ColorUtils.success("Wyrzucono gracza &f" + args[0] + "&a. &7Powód: &f" + reason));
        return true;
    }

    private boolean handleAlts(CommandSender sender, String label, String[] args) {
        if (!canUse(sender, "Holly.mod.alts")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.mod.alts"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ColorUtils.usage("/" + label + " <nick>"));
            return true;
        }

        List<ModerationManager.AltInfo> alts = manager.getAlts(args[0]);
        if (alts.isEmpty()) {
            sender.sendMessage(ColorUtils.info("Brak powiązanych kont dla &f" + args[0] + "&f (lub nieznane IP)."));
            return true;
        }

        sender.sendMessage(ColorUtils.colorizeComponent("&8&m------------------------------------------"));
        sender.sendMessage(ColorUtils.colorizeComponent("&#00E0FF&lALTS &8Ξ &7Konta powiązane z &f" + args[0]));
        for (ModerationManager.AltInfo alt : alts) {
            String color;
            String status;
            if (alt.banned()) {
                color = "&#FF3D3D";
                status = "zbanowany";
            } else if (alt.online()) {
                color = "&#4EFA76";
                status = "online";
            } else {
                color = "&7";
                status = "offline";
            }
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&8» " + color + alt.name() + " &8(&7" + status + "&8)"
            ));
        }
        sender.sendMessage(ColorUtils.colorizeComponent("&8&m------------------------------------------"));
        return true;
    }

    private boolean handleClearList(CommandSender sender, String label) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy."));
            return true;
        }
        if (!player.hasPermission("Holly.cmd.wyczysclista")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.wyczysclista"));
            return true;
        }
        manager.clearReportList(player);
        return true;
    }

    private boolean handleZglos(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla graczy."));
            return true;
        }
        if (!player.hasPermission("Holly.cmd.zglos")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.zglos"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            player.sendMessage(ColorUtils.colorizeComponent("&8&m------------------------------------------"));
            player.sendMessage(ColorUtils.colorizeComponent("&#00E0FF&lZGŁOS &8Ξ &7Pomoc"));
            player.sendMessage(ColorUtils.colorizeComponent("&8» &f/" + label + " lista &8- &7lista zbanowanych, którzy Cię zabili"));
            player.sendMessage(ColorUtils.colorizeComponent("&8» &f/wyczysclista &8- &7czyści bossbar i listę"));
            player.sendMessage(ColorUtils.colorizeComponent("&8&m------------------------------------------"));
            return true;
        }

        if (!args[0].equalsIgnoreCase("lista")) {
            player.sendMessage(ColorUtils.usage("/" + label + " lista"));
            return true;
        }

        List<ModerationStorage.ReportEntry> list = manager.getReportList(player.getUniqueId());
        if (list.isEmpty()) {
            player.sendMessage(ColorUtils.info("Twoja lista zbanowanych zabójców jest pusta."));
            return true;
        }

        player.sendMessage(ColorUtils.colorizeComponent("&8&m------------------------------------------"));
        player.sendMessage(ColorUtils.colorizeComponent("&#FF3D3D&lZGŁOS LISTA &8Ξ &7Zbanowani, którzy Cię zabili"));
        for (ModerationStorage.ReportEntry entry : list) {
            player.sendMessage(ColorUtils.colorizeComponent("&8» &#FF3D3D" + entry.name()));
        }
        player.sendMessage(ColorUtils.colorizeComponent("&8&m------------------------------------------"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            if (name.equals("zglos")) {
                return filter(List.of("lista", "help"), args[0]);
            }
            if (name.equals("unban")) {
                List<String> suggestions = onlineNames();
                suggestions.add("all");
                return filter(suggestions, args[0]);
            }
            if (name.equals("wyczysclista")) {
                return Collections.emptyList();
            }
            return filter(onlineNames(), args[0]);
        }
        if (args.length == 2) {
            if (name.equals("tempban") || name.equals("tempbanip") || name.equals("tempmute")
                || name.equals("ban") || name.equals("banip") || name.equals("mute")) {
                return filter(List.of("30m", "1h", "6h", "12h", "1d", "3d", "7d", "30d", "-s"), args[1]);
            }
        }
        if (args.length >= 3 && (name.equals("ban") || name.equals("tempban")
            || name.equals("banip") || name.equals("tempbanip"))) {
            return filter(List.of("-s"), args[args.length - 1]);
        }
        return Collections.emptyList();
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> filter(List<String> input, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : input) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }
}
