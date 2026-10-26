package dev.grzybalson.utils;

import org.bukkit.entity.Player;

public final class CommandPlaceholderUtil {

    private CommandPlaceholderUtil() {
    }

    public static String applyPlayerPlaceholders(String text, Player player) {
        if (text == null) {
            return "";
        }
        String name = player.getName();
        String uuid = player.getUniqueId().toString();
        return text
            .replace("{player}", name)
            .replace("{PLAYER}", name)
            .replace("%player%", name)
            .replace("%PLAYER%", name)
            .replace("{nick}", name)
            .replace("{NICK}", name)
            .replace("{uuid}", uuid)
            .replace("{UUID}", uuid)
            .replace("%uuid%", uuid)
            .replace("%UUID%", uuid);
    }

    public static String normalizeConsoleCommand(String command, Player player) {
        return stripLeadingSlash(applyPlayerPlaceholders(command, player));
    }

    public static String stripLeadingSlash(String command) {
        if (command == null) {
            return "";
        }
        String parsed = command.trim();
        while (parsed.startsWith("/")) {
            parsed = parsed.substring(1).trim();
        }
        return parsed;
    }
}
