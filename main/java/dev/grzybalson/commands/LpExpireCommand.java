package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LpExpireCommand implements CommandExecutor, TabCompleter {

    private static final Pattern DURATION_TOKEN = Pattern.compile(
        "(\\d+)(dni|days|day|godz|hours|hour|hrs|mins|minutes|minute|min|sek|secs|seconds|second|sec|tyg|weeks|week|[smhdw])",
        Pattern.CASE_INSENSITIVE
    );

    private final HollyCore plugin;

    public LpExpireCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.lpexpire")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.lpexpire"));
            return true;
        }

        LuckPerms luckPerms = resolveLuckPerms();
        if (luckPerms == null) {
            sender.sendMessage(ColorUtils.error("LuckPerms nie jest dostępny."));
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("add")) {
            sender.sendMessage(ColorUtils.usage("/" + label + " add <ranga> <czas> [nick]"));
            sender.sendMessage(ColorUtils.colorizeComponent("&7Przykład: &f/" + label + " add vip 12h"));
            sender.sendMessage(ColorUtils.colorizeComponent("&7Dolicza czas do aktualnego wygaśnięcia rangi (2d + 12h = 2d 12h)."));
            return true;
        }

        ParsedAdd parsed = parseAddArgs(args);
        if (parsed == null) {
            sender.sendMessage(ColorUtils.usage("/" + label + " add <ranga> <czas> [nick]"));
            sender.sendMessage(ColorUtils.colorizeComponent("&7Czas: &f12h&7, &f2d&7, &f7d12h&7, &f30m&7, &f1w"));
            return true;
        }

        long extraMillis = parseDurationMillis(parsed.durationRaw());
        if (extraMillis <= 0L) {
            sender.sendMessage(ColorUtils.error("Niepoprawny czas. Przykłady: &f12h&c, &f2d&c, &f7d12h&c, &f30m"));
            return true;
        }

        Group group = luckPerms.getGroupManager().getGroup(parsed.groupName());
        if (group == null) {
            sender.sendMessage(ColorUtils.error("Nie ma rangi &f" + parsed.groupName() + "&c w LuckPerms."));
            return true;
        }
        String groupName = group.getName();

        if (parsed.playerName() != null) {
            OfflinePlayer target = resolvePlayer(parsed.playerName());
            if (target == null || (target.getName() == null && !target.hasPlayedBefore() && Bukkit.getPlayer(target.getUniqueId()) == null)) {
                sender.sendMessage(ColorUtils.error("Nie znaleziono gracza &f" + parsed.playerName() + "&c."));
                return true;
            }
            extendOne(sender, luckPerms, target.getUniqueId(), displayName(target, parsed.playerName()), groupName, extraMillis);
            return true;
        }

        extendAll(sender, luckPerms, groupName, extraMillis);
        return true;
    }

    private void extendOne(CommandSender sender, LuckPerms luckPerms, UUID uuid, String name, String groupName, long extraMillis) {
        luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
            ExtendOutcome outcome = extendUser(user, groupName, extraMillis);
            CompletableFuture<Void> save = outcome.changed()
                ? luckPerms.getUserManager().saveUser(user)
                : CompletableFuture.completedFuture(null);
            save.whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    sender.sendMessage(ColorUtils.error("Nie udało się zapisać rangi: &f" + error.getMessage()));
                    return;
                }
                if (!outcome.changed()) {
                    if (outcome.wasPermanent()) {
                        sender.sendMessage(ColorUtils.error("&f" + name + " &cma rangę &f" + groupName + " &cna stałe — nie ma czego przedłużać."));
                    } else {
                        sender.sendMessage(ColorUtils.error("&f" + name + " &cnie ma tymczasowej rangi &f" + groupName + "&c."));
                    }
                    return;
                }
                sender.sendMessage(ColorUtils.success(
                    "Przedłużono &f" + groupName + " &adla &f" + name
                        + " &ao &f" + formatDuration(extraMillis)
                        + "&a. Zostało: &f" + formatDuration(outcome.remainingMillis())
                ));
                notifyPlayer(uuid, groupName, extraMillis, outcome.remainingMillis());
            }));
        }).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                sender.sendMessage(ColorUtils.error("Błąd LuckPerms: &f" + error.getMessage())));
            return null;
        });
    }

    private void extendAll(CommandSender sender, LuckPerms luckPerms, String groupName, long extraMillis) {
        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &fPrzedłużam rangę &b" + groupName + " &fo &b" + formatDuration(extraMillis) + "&f..."
        ));

        luckPerms.getUserManager().searchAll(NodeMatcher.key(InheritanceNode.builder(groupName).build()))
            .thenCompose(found -> {
                if (found == null || found.isEmpty()) {
                    return CompletableFuture.completedFuture(new BulkResult(0, 0, 0));
                }
                AtomicInteger extended = new AtomicInteger();
                AtomicInteger permanent = new AtomicInteger();
                AtomicInteger missing = new AtomicInteger();
                List<CompletableFuture<Void>> jobs = new ArrayList<>();
                List<Notify> notifies = new ArrayList<>();

                for (Map.Entry<UUID, ? extends Collection<InheritanceNode>> entry : found.entrySet()) {
                    UUID uuid = entry.getKey();
                    jobs.add(luckPerms.getUserManager().loadUser(uuid).thenCompose(user -> {
                        ExtendOutcome outcome = extendUser(user, groupName, extraMillis);
                        if (!outcome.changed()) {
                            if (outcome.wasPermanent()) {
                                permanent.incrementAndGet();
                            } else {
                                missing.incrementAndGet();
                            }
                            return CompletableFuture.completedFuture(null);
                        }
                        return luckPerms.getUserManager().saveUser(user).thenAccept(ignored -> {
                            extended.incrementAndGet();
                            synchronized (notifies) {
                                notifies.add(new Notify(uuid, outcome.remainingMillis()));
                            }
                        });
                    }));
                }

                return CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (Notify notify : notifies) {
                                notifyPlayer(notify.uuid(), groupName, extraMillis, notify.remainingMillis());
                            }
                        });
                        return new BulkResult(extended.get(), permanent.get(), found.size());
                    });
            })
            .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    sender.sendMessage(ColorUtils.error("Błąd LuckPerms: &f" + error.getMessage()));
                    return;
                }
                if (result == null || result.extended() == 0) {
                    sender.sendMessage(ColorUtils.error(
                        "Nikt nie ma tymczasowej rangi &f" + groupName + "&c."
                            + (result != null && result.permanent() > 0
                            ? " &7Na stałe: &f" + result.permanent()
                            : "")
                    ));
                    return;
                }
                String extra = result.permanent() > 0
                    ? " &7Pominięto na stałe: &f" + result.permanent()
                    : "";
                sender.sendMessage(ColorUtils.success(
                    "Dodano &f" + formatDuration(extraMillis)
                        + " &ado rangi &f" + groupName
                        + " &adla &f" + result.extended()
                        + " &a" + pluralPlayers(result.extended()) + "."
                        + extra
                ));
            }));
    }

    private ExtendOutcome extendUser(User user, String groupName, long extraMillis) {
        if (user == null) {
            return ExtendOutcome.missing();
        }
        List<InheritanceNode> nodes = user.getNodes(NodeType.INHERITANCE).stream()
            .filter(node -> node.getGroupName().equalsIgnoreCase(groupName))
            .filter(node -> !node.hasExpired())
            .toList();
        if (nodes.isEmpty()) {
            return ExtendOutcome.missing();
        }

        boolean permanent = false;
        boolean changed = false;
        long latestRemaining = 0L;
        Instant now = Instant.now();

        for (InheritanceNode node : nodes) {
            if (!node.hasExpiry() || node.getExpiry() == null) {
                permanent = true;
                continue;
            }
            Instant newExpiry = node.getExpiry().plusMillis(extraMillis);
            if (!newExpiry.isAfter(now)) {
                continue;
            }
            InheritanceNode updated = node.toBuilder().expiry(newExpiry).build();
            user.data().remove(node);
            user.data().add(updated);
            changed = true;
            latestRemaining = Math.max(latestRemaining, newExpiry.toEpochMilli() - now.toEpochMilli());
        }

        if (changed) {
            return ExtendOutcome.ok(latestRemaining);
        }
        return permanent ? ExtendOutcome.permanentRank() : ExtendOutcome.missing();
    }

    private void notifyPlayer(UUID uuid, String groupName, long extraMillis, long remainingMillis) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage(ColorUtils.success(
            "Twoja ranga &f" + groupName
                + " &azostała przedłużona o &f" + formatDuration(extraMillis)
                + "&a. Zostało: &f" + formatDuration(remainingMillis)
        ));
    }

    private ParsedAdd parseAddArgs(String[] args) {
        if (args.length == 3) {
            if (parseDurationMillis(args[2]) <= 0L) {
                return null;
            }
            return new ParsedAdd(args[1], args[2], null);
        }
        if (args.length == 4) {
            if (parseDurationMillis(args[2]) > 0L) {
                return new ParsedAdd(args[1], args[2], args[3]);
            }
            if (parseDurationMillis(args[3]) > 0L) {
                return new ParsedAdd(args[2], args[3], args[1]);
            }
        }
        return null;
    }

    static long parseDurationMillis(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        String compact = raw.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = DURATION_TOKEN.matcher(compact);
        long total = 0L;
        int index = 0;
        boolean found = false;
        while (matcher.find()) {
            if (matcher.start() != index) {
                return -1L;
            }
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            if (amount < 0L) {
                return -1L;
            }
            try {
                total = Math.addExact(total, unitMillis(matcher.group(2), amount));
            } catch (ArithmeticException exception) {
                return -1L;
            }
            index = matcher.end();
        }
        if (!found || index != compact.length() || total <= 0L) {
            return -1L;
        }
        return total;
    }

    private static long unitMillis(String unit, long amount) {
        String key = unit.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "s", "sek", "sec", "secs", "second", "seconds" -> amount * 1000L;
            case "m", "min", "mins", "minute", "minutes" -> amount * 60_000L;
            case "h", "godz", "hour", "hours", "hrs" -> amount * 3_600_000L;
            case "d", "dni", "day", "days" -> amount * 86_400_000L;
            case "w", "tyg", "week", "weeks" -> amount * 604_800_000L;
            default -> 0L;
        };
    }

    static String formatDuration(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long weeks = total / 604_800L;
        total %= 604_800L;
        long days = total / 86_400L;
        total %= 86_400L;
        long hours = total / 3_600L;
        total %= 3_600L;
        long minutes = total / 60L;
        long seconds = total % 60L;
        List<String> parts = new ArrayList<>();
        if (weeks > 0L) {
            parts.add(weeks + "w");
        }
        if (days > 0L) {
            parts.add(days + "d");
        }
        if (hours > 0L) {
            parts.add(hours + "h");
        }
        if (minutes > 0L && weeks == 0L) {
            parts.add(minutes + "m");
        }
        if (parts.isEmpty()) {
            parts.add(Math.max(1L, seconds) + "s");
        }
        return String.join(" ", parts);
    }

    private static String pluralPlayers(int count) {
        if (count == 1) {
            return "gracza";
        }
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return "graczy";
        }
        return "graczy";
    }

    private LuckPerms resolveLuckPerms() {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return null;
        }
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolvePlayer(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    private static String displayName(OfflinePlayer player, String fallback) {
        if (player.getName() != null && !player.getName().isBlank()) {
            return player.getName();
        }
        Player online = Bukkit.getPlayer(player.getUniqueId());
        if (online != null) {
            return online.getName();
        }
        return fallback;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.lpexpire")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("add"), args[0]);
        }
        if (!args[0].equalsIgnoreCase("add")) {
            return List.of();
        }
        if (args.length == 2) {
            List<String> groups = loadedGroupNames();
            List<String> names = new ArrayList<>(groups);
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3) {
            if (parseDurationMillis(args[2]) > 0L) {
                return filter(onlineNames(), args[2]);
            }
            List<String> suggestions = new ArrayList<>(List.of("1h", "12h", "1d", "2d", "7d", "7d12h", "30d"));
            suggestions.addAll(loadedGroupNames());
            return filter(suggestions, args[2]);
        }
        if (args.length == 4) {
            if (parseDurationMillis(args[2]) > 0L) {
                return filter(onlineNames(), args[3]);
            }
            return filter(List.of("1h", "12h", "1d", "2d", "7d", "7d12h", "30d"), args[3]);
        }
        return List.of();
    }

    private List<String> loadedGroupNames() {
        LuckPerms luckPerms = resolveLuckPerms();
        if (luckPerms == null) {
            return List.of();
        }
        return luckPerms.getGroupManager().getLoadedGroups().stream()
            .map(Group::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private record ParsedAdd(String groupName, String durationRaw, String playerName) {}

    private record BulkResult(int extended, int permanent, int found) {}

    private record Notify(UUID uuid, long remainingMillis) {}

    private record ExtendOutcome(boolean changed, boolean wasPermanent, long remainingMillis) {
        static ExtendOutcome ok(long remainingMillis) {
            return new ExtendOutcome(true, false, remainingMillis);
        }

        static ExtendOutcome missing() {
            return new ExtendOutcome(false, false, 0L);
        }

        static ExtendOutcome permanentRank() {
            return new ExtendOutcome(false, true, 0L);
        }
    }
}
