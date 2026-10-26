package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandBlockerListener implements Listener {

    private final HollyCore plugin;
    private final Map<String, Command> resolvedCommands = new ConcurrentHashMap<>();
    private CommandMap commandMap;
    private static final String ADMIN_PERMISSION = "hollycore.admin";

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
        "help", "?",
        "pl", "plugins",
        "ver", "version", "about",
        "bukkit:help", "bukkit:?",
        "bukkit:pl", "bukkit:plugins",
        "bukkit:ver", "bukkit:version", "bukkit:about",
        "minecraft:help",
        "spigot:ver", "spigot:version",
        "paper:ver", "paper:version",
        "icanhasbukkit"
    );

    private static final String HOLLYCORE = "&#FF9100&lʜ&#FFA800&lᴏ&#FFC000&lʟ&#FFD700&lʟ&#FFEE00&lʏ&#FFD700&lᴄ&#FFC000&lᴏ&#FFA800&lʀ&#FF9100&lᴇ";
    private static final String BLOCKED_MSG = "&#FF0000✘ &8Ξ &cTa komenda jest zablokowana";
    private static final String AUTHOR = "&#FF0000&lɢ&#FF1F1F&lʀ&#FF3F3F&lᴢ&#FF5E5E&lʏ&#FF7E7E&lʙ&#FF9D9D&lᴀ&#FF7676&lʟ&#FF4F4F&lꜱ&#FF2727&lᴏ&#FF0000&lɴ";
    private static final String DISCORD = "&#BE00FF&lᴅ&#C312FF&lᴄ&#C723FF&l.&#CC35FF&lʜ&#D047FF&lᴏ&#D558FF&lʟ&#D96AFF&lʟ&#DE7CFF&lʏ&#E28DFF&lꜱ&#E79FFF&lᴇ&#E28BFF&lʀ&#DD77FF&lᴠ&#D863FF&lɪ&#D350FF&lᴄ&#CD3CFF&lᴇ&#C828FF&l.&#C314FF&lᴘ&#BE00FF&lʟ";
    private static final String LINE = "&8&m                                                  ";

    public CommandBlockerListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (plugin.getRestartManager().isRestarting()) {
            if (player.hasPermission(ADMIN_PERMISSION)) return;
            event.setCancelled(true);
            player.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getCommandsBlockedDuringRestart()
            ));
            return;
        }

        String message = event.getMessage();
        int end = message.indexOf(' ');
        String command = message.substring(1, end < 0 ? message.length() : end).toLowerCase(Locale.ROOT);

        if (BLOCKED_COMMANDS.contains(command)) {
            if (player.hasPermission(ADMIN_PERMISSION)) return;
            event.setCancelled(true);
            sendBlockMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        boolean admin = player.hasPermission(ADMIN_PERMISSION);
        event.getCommands().removeIf(command -> shouldHideCommand(player, command, admin));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (!event.isCommand()) {
            return;
        }

        String buffer = event.getBuffer();
        if (buffer == null || buffer.isBlank()) {
            return;
        }

        String commandBuffer = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        int firstSpace = commandBuffer.indexOf(' ');
        if (firstSpace < 0) {
            boolean admin = event.getSender().hasPermission(ADMIN_PERMISSION);
            event.getCompletions().removeIf(completion -> shouldHideCommand(event.getSender(), completion, admin));
            return;
        }

        String command = commandBuffer.substring(0, firstSpace);
        if (shouldHideCommand(event.getSender(), command, event.getSender().hasPermission(ADMIN_PERMISSION))) {
            event.getCompletions().clear();
        }
    }

    private boolean shouldHideCommand(CommandSender sender, String commandLabel, boolean admin) {
        if (commandLabel == null || commandLabel.isEmpty()) {
            return false;
        }
        // Fast check: hide all namespace:command patterns for non-admins
        if (commandLabel.indexOf(':') >= 0) {
            return !admin;
        }
        if (admin) {
            return false;
        }
        String command = normalizeCommandLabel(commandLabel);
        if (command.isEmpty()) {
            return false;
        }
        if (BLOCKED_COMMANDS.contains(command)) {
            return true;
        }

        Command bukkitCommand = resolveCommand(command);
        return bukkitCommand != null && !bukkitCommand.testPermissionSilent(sender);
    }

    private String normalizeCommandLabel(String commandLabel) {
        if (commandLabel == null) {
            return "";
        }

        int start = 0;
        int end = commandLabel.length();
        while (start < end && (commandLabel.charAt(start) == '/' || Character.isWhitespace(commandLabel.charAt(start)))) {
            start++;
        }
        while (end > start && Character.isWhitespace(commandLabel.charAt(end - 1))) {
            end--;
        }
        return start >= end ? "" : commandLabel.substring(start, end).toLowerCase(Locale.ROOT);
    }

    private Command resolveCommand(String commandLabel) {
        Command cached = resolvedCommands.get(commandLabel);
        if (cached != null) {
            return cached;
        }

        CommandMap map = getCommandMap();
        Command command = map != null ? map.getCommand(commandLabel) : null;
        if (command != null) {
            resolvedCommands.put(commandLabel, command);
        }
        return command;
    }

    private CommandMap getCommandMap() {
        if (commandMap != null) {
            return commandMap;
        }

        try {
            Method getCommandMapMethod = plugin.getServer().getClass().getMethod("getCommandMap");
            Object resolved = getCommandMapMethod.invoke(plugin.getServer());
            if (resolved instanceof CommandMap resolvedCommandMap) {
                commandMap = resolvedCommandMap;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return commandMap;
    }

    private void sendBlockMessage(Player player) {
        player.sendMessage(ColorUtils.colorizeComponent(""));
        player.sendMessage(ColorUtils.colorizeComponent(LINE));
        player.sendMessage(ColorUtils.colorizeComponent(""));
        player.sendMessage(ColorUtils.colorizeComponent("                " + HOLLYCORE));
        player.sendMessage(ColorUtils.colorizeComponent(""));
        player.sendMessage(ColorUtils.colorizeComponent("    " + BLOCKED_MSG));
        player.sendMessage(ColorUtils.colorizeComponent(""));
        player.sendMessage(ColorUtils.colorizeComponent("    &8Ξ &fSerwer zasilany przez " + HOLLYCORE));
        player.sendMessage(ColorUtils.colorizeComponent("    &8Ξ &fAutor: " + AUTHOR));
        player.sendMessage(ColorUtils.colorizeComponent("    &8Ξ &fDiscord: " + DISCORD));
        player.sendMessage(ColorUtils.colorizeComponent(""));
        player.sendMessage(ColorUtils.colorizeComponent(LINE));
        player.sendMessage(ColorUtils.colorizeComponent(""));
    }
}
