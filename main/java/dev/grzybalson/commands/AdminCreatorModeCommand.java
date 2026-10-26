package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CreatorModeConfig;
import dev.grzybalson.managers.CreatorModeManager;
import dev.grzybalson.modules.moderation.ModerationManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class AdminCreatorModeCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "Holly.cmd.atryb-tworca";

    private final HollyCore plugin;

    public AdminCreatorModeCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (sender instanceof Player
            && !sender.hasPermission(PERMISSION)
            && !sender.hasPermission("Holly.mod.*")
            && !sender.isOp()) {
            sender.sendMessage(ColorUtils.noPermission(label, PERMISSION));
            return true;
        }

        CreatorModeManager manager = plugin.getCreatorModeManager();
        if (manager == null) {
            sender.sendMessage(ColorUtils.error("Tryb twórcy jest wyłączony."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.usage("/" + label + " <nick> <czas|off>"));
            return true;
        }

        ResolvedTarget target = resolveTarget(args[0]);
        if (target == null) {
            sender.sendMessage(ColorUtils.error("Nie znaleziono gracza &f" + args[0] + "&c."));
            return true;
        }

        CreatorModeConfig config = plugin.getConfigManager().getCreatorModeConfig();
        if (isOffToken(args[1])) {
            if (!manager.revoke(target.uuid())) {
                sender.sendMessage(ColorUtils.error("Gracz &f" + target.name() + " &cnie ma nadanego trybu twórcy."));
                return true;
            }
            sender.sendMessage(ColorUtils.colorizeComponent(
                config.getRevokedStaffMessage().replace("{player}", target.name())
            ));
            if (target.online() != null) {
                target.online().sendMessage(ColorUtils.colorizeComponent(config.getRevokedPlayerMessage()));
            }
            return true;
        }

        long duration = ModerationManager.parseDurationMillis(args[1]);
        if (duration <= 0L) {
            sender.sendMessage(ColorUtils.error("Niepoprawny czas. Przykłady: &f30m&c, &f1h&c, &f6h&c, &f1d"));
            return true;
        }

        manager.grant(target.uuid(), target.name(), duration);
        String formatted = ModerationManager.formatDuration(duration);
        sender.sendMessage(ColorUtils.colorizeComponent(
            config.getGrantedStaffMessage()
                .replace("{player}", target.name())
                .replace("{time}", formatted)
        ));
        if (target.online() != null) {
            target.online().sendMessage(ColorUtils.colorizeComponent(
                config.getGrantedPlayerMessage().replace("{time}", formatted)
            ));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (sender instanceof Player player
            && !player.hasPermission(PERMISSION)
            && !player.hasPermission("Holly.mod.*")
            && !player.isOp()) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(onlineNames(), args[0]);
        }
        if (args.length == 2) {
            return filter(List.of("30m", "1h", "6h", "12h", "1d", "3d", "7d", "off"), args[1]);
        }
        return List.of();
    }

    private static boolean isOffToken(String raw) {
        if (raw == null) {
            return false;
        }
        String value = raw.toLowerCase(Locale.ROOT);
        return value.equals("off") || value.equals("offa") || value.equals("zabierz")
            || value.equals("remove") || value.equals("0");
    }

    private static ResolvedTarget resolveTarget(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(rawName);
        if (online == null) {
            online = Bukkit.getPlayer(rawName);
        }
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName(), online);
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(rawName);
        if (offline.getUniqueId() != null && (offline.hasPlayedBefore() || offline.getName() != null)) {
            String name = offline.getName() != null ? offline.getName() : rawName;
            return new ResolvedTarget(offline.getUniqueId(), name, null);
        }
        return null;
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<String> filter(List<String> input, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : input) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }

    private record ResolvedTarget(UUID uuid, String name, Player online) {}
}
