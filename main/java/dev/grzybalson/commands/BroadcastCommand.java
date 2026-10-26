package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BroadcastCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final File configFile;
    private YamlConfiguration config;

    public BroadcastCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "commands"), "broadcast-title.yml");
        reloadConfig();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.bc.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.bc.reload"));
                return true;
            }
            reloadConfig();
            sender.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.reload", "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano broadcast-title.yml.")));
            return true;
        }

        if (!sender.hasPermission("Holly.cmd.bc")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.bc"));
            return true;
        }

        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/bc title <tekst>", "/bc chat <tekst>", "/bc <nick> <tekst>", "/bc reload");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (message.isBlank()) {
            ColorUtils.sendUsage(sender, "/bc title <tekst>", "/bc chat <tekst>", "/bc <nick> <tekst>", "/bc reload");
            return true;
        }

        if (sub.equals("chat")) {
            sendChat(Bukkit.getOnlinePlayers(), message);
            sender.sendMessage(ColorUtils.colorizeComponent(config.getString(
                "messages.success-chat",
                "&#00FF3B✔ &8Ξ &#4EFA76Wysłano ogłoszenie na chat do wszystkich graczy!"
            )));
            return true;
        }

        if (sub.equals("title")) {
            sendTitle(Bukkit.getOnlinePlayers(), message);
            sender.sendMessage(ColorUtils.colorizeComponent(config.getString(
                "messages.success",
                "&#00FF3B✔ &8Ξ &#4EFA76Wysłano ogłoszenie do wszystkich graczy!"
            )));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            target = Bukkit.getPlayer(args[0]);
        }
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ColorUtils.error("Gracz &f" + args[0] + " &cnie jest online."));
            return true;
        }

        sendPlayerTitle(target, sender, message);
        sender.sendMessage(ColorUtils.colorizeComponent(config.getString(
            "messages.success-player",
            "&#00FF3B✔ &8Ξ &#4EFA76Wysłano ogłoszenie do gracza &f{player}&a."
        ).replace("{player}", target.getName())));
        return true;
    }

    private void sendTitle(Iterable<? extends Player> players, String message) {
        sendTitle(players, config.getString("title.main", "&c&lOGŁOSZENIE"), message);
    }

    private void sendPlayerTitle(Player target, CommandSender from, String message) {
        String fromName = from instanceof Player player ? player.getName() : from.getName();
        if (fromName == null || fromName.isBlank()) {
            fromName = "Konsola";
        }
        String titleText = config.getString("title.player-main", "&eWiadomość od: &f{from}")
            .replace("{from}", fromName)
            .replace("{nick}", fromName)
            .replace("{player}", fromName);
        sendTitle(List.of(target), titleText, message);
    }

    private void sendTitle(Iterable<? extends Player> players, String titleText, String subtitle) {
        Title.Times times = Title.Times.times(
            Duration.ofMillis(Math.max(0, config.getInt("title.fade-in-ms", 500))),
            Duration.ofMillis(Math.max(0, config.getInt("title.stay-ms", 3000))),
            Duration.ofMillis(Math.max(0, config.getInt("title.fade-out-ms", 500)))
        );
        for (Player player : players) {
            String resolvedTitle = applyViewerPlaceholders(titleText, player);
            String resolvedSubtitle = applyViewerPlaceholders(subtitle, player);
            player.showTitle(Title.title(
                ColorUtils.colorizeComponent(resolvedTitle),
                ColorUtils.colorizeComponent(resolvedSubtitle),
                times
            ));
            playSounds(player);
        }
    }

    private void sendChat(Iterable<? extends Player> players, String message) {
        String format = config.getString("chat.format", "&#FF3D3D&lOGŁOSZENIE &8» &f{message}");
        for (Player player : players) {
            String resolvedMessage = applyViewerPlaceholders(message, player);
            player.sendMessage(ColorUtils.colorizeComponent(
                format.replace("{message}", resolvedMessage)
            ));
            playSounds(player);
        }
    }

    /** %player% = nick gracza, który widzi title/chat. */
    private static String applyViewerPlaceholders(String text, Player viewer) {
        if (text == null || text.isEmpty() || viewer == null) {
            return text == null ? "" : text;
        }
        String name = viewer.getName();
        return text.replace("%player%", name);
    }

    private void playSounds(Player player) {
        playConfiguredSound(
            player,
            resolveSoundName("sound", "sound.name", "BLOCK_NOTE_BLOCK_CHIME"),
            resolveDouble("sound-volume", "sound.volume", 0.35D),
            resolveDouble("sound-pitch", "sound.pitch", 1.15D),
            "sound"
        );
        playConfiguredSound(
            player,
            resolveSoundName("sound-attention", "sound.attention.name", "BLOCK_NOTE_BLOCK_PLING"),
            resolveDouble("sound-attention-volume", "sound.attention.volume", 0.2D),
            resolveDouble("sound-attention-pitch", "sound.attention.pitch", 1.45D),
            "sound-attention"
        );
    }

    private void playConfiguredSound(Player player, String soundName, float volume, float pitch, String configKey) {
        if (soundName == null || soundName.isBlank() || soundName.equalsIgnoreCase("none") || soundName.equalsIgnoreCase("off")) {
            return;
        }

        try {
            Sound sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("[Broadcast] Nieprawidlowy " + configKey + ": " + soundName);
        }
    }

    private String resolveSoundName(String primaryPath, String legacyPath, String fallback) {
        String value = config.getString(primaryPath);
        if (value != null && !value.isBlank() && !(config.isConfigurationSection(primaryPath))) {
            return value;
        }

        value = config.getString(legacyPath);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback;
    }

    private float resolveDouble(String primaryPath, String legacyPath, double fallback) {
        if (config.contains(primaryPath) && !config.isConfigurationSection(primaryPath)) {
            return (float) config.getDouble(primaryPath, fallback);
        }
        if (config.contains(legacyPath)) {
            return (float) config.getDouble(legacyPath, fallback);
        }
        return (float) fallback;
    }

    private void reloadConfig() {
        ensureConfig();
        config = YamlConfiguration.loadConfiguration(configFile);
        boolean changed = false;
        changed |= setDefault("title.main", "&c&lOGŁOSZENIE");
        changed |= setDefault("title.player-main", "&eWiadomość od: &f{from}");
        changed |= setDefault("title.fade-in-ms", 250);
        changed |= setDefault("title.stay-ms", 3500);
        changed |= setDefault("title.fade-out-ms", 600);

        boolean hasLegacySoundSection = config.isConfigurationSection("sound");
        if (!hasLegacySoundSection) {
            changed |= setDefault("sound", "BLOCK_NOTE_BLOCK_CHIME");
            changed |= setDefault("sound-volume", 0.35D);
            changed |= setDefault("sound-pitch", 1.15D);
        }

        changed |= setDefault("sound-attention", "BLOCK_NOTE_BLOCK_PLING");
        changed |= setDefault("sound-attention-volume", 0.2D);
        changed |= setDefault("sound-attention-pitch", 1.45D);
        changed |= setDefault("chat.format", "&#FF3D3D&lOGŁOSZENIE &8» &f{message}");
        changed |= setDefault("messages.success", "&#00FF3B✔ &8Ξ &#4EFA76Wysłano ogłoszenie do wszystkich graczy!");
        changed |= setDefault("messages.success-chat", "&#00FF3B✔ &8Ξ &#4EFA76Wysłano ogłoszenie na chat do wszystkich graczy!");
        changed |= setDefault("messages.success-player", "&#00FF3B✔ &8Ξ &#4EFA76Wysłano ogłoszenie do gracza &f{player}&a.");
        changed |= setDefault("messages.reload", "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano broadcast-title.yml.");
        if (changed) {
            saveConfig();
        }
    }

    private void ensureConfig() {
        HollyDataPaths.ensureResource(plugin, "addons/commands/broadcast-title.yml");
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udało się utworzyć broadcast-title.yml: " + exception.getMessage());
            }
        }
    }

    private boolean setDefault(String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się zapisać broadcast-title.yml: " + exception.getMessage());
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String sub : List.of("title", "chat", "reload")) {
                if (sub.startsWith(partial)) {
                    completions.add(sub);
                }
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }
}
