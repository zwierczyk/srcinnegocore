package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 1:1 z hollyservices-engine RenderCommand:
 * /render &lt;world&gt; &lt;simulation&gt; &lt;view&gt;
 */
public final class RenderCommand implements CommandExecutor, TabCompleter {

    private static final String CONFIG_RESOURCE = "addons/commands/render.yml";

    private final HollyCore plugin;
    private final File configFile;
    private YamlConfiguration config;

    public RenderCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = HollyDataPaths.getRootFile(plugin, CONFIG_RESOURCE);
        reloadConfig();
        applyAll(plugin.getLogger());
    }

    public void reloadConfig() {
        HollyDataPaths.ensureResource(plugin, CONFIG_RESOURCE);
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.render")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.render"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            applyAll(plugin.getLogger());
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#00FF3B✔ &8Ξ &#4EFA76Przeladowano &frender.yml &ai zastosowano dystanse."));
            return true;
        }

        if (args.length < 3) {
            ColorUtils.sendUsage(sender,
                "/" + label + " <swiat> <simulation> <view>",
                "/" + label + " reload"
            );
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#FF5C5C&l✖ &#C1C1C1Swiat &f" + args[0] + " &#C1C1C1nie istnieje."));
            return true;
        }

        int sim;
        int view;
        try {
            sim = Integer.parseInt(args[1]);
            view = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#FF5C5C&l✖ &#C1C1C1Podaj poprawne liczby."));
            return true;
        }

        sim = clamp(sim);
        view = clamp(view);

        world.setSimulationDistance(sim);
        world.setViewDistance(view);

        config.set("worlds." + world.getName() + ".simulation-distance", sim);
        config.set("worlds." + world.getName() + ".view-distance", view);
        saveConfig();

        sender.sendMessage(ColorUtils.colorizeComponent(
            "&#60FF8B&l✔ &#C1C1C1Swiat &f" + world.getName()
                + " &#C1C1C1→ simulation: &f" + sim
                + "&#C1C1C1, view: &f" + view
                + " &#C1C1C1(zapisano)"));
        return true;
    }

    public void applyAll(Logger logger) {
        ConfigurationSection worlds = config.getConfigurationSection("worlds");
        if (worlds == null) {
            return;
        }

        for (String worldName : worlds.getKeys(false)) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.info("[Render] Pominieto swiat (nie istnieje): " + worldName);
                continue;
            }

            int sim = worlds.getInt(worldName + ".simulation-distance", -1);
            int view = worlds.getInt(worldName + ".view-distance", -1);

            if (sim > 0) {
                world.setSimulationDistance(clamp(sim));
            }
            if (view > 0) {
                world.setViewDistance(clamp(view));
            }

            logger.info("[Render] Zastosowano dla " + worldName
                + ": simulation=" + sim + ", view=" + view);
        }
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("[Render] Nie udalo sie zapisac render.yml: " + exception.getMessage());
        }
    }

    private static int clamp(int value) {
        return Math.max(2, Math.min(32, value));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.cmd.render")) {
            return List.of();
        }

        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                    suggestions.add(world.getName());
                }
            }
            if ("reload".startsWith(input)) {
                suggestions.add("reload");
            }
            return suggestions;
        }
        if (args.length == 2) {
            for (String value : List.of("4", "5", "6", "8", "10", "12")) {
                if (value.startsWith(input)) {
                    suggestions.add(value);
                }
            }
            return suggestions;
        }
        if (args.length == 3) {
            for (String value : List.of("6", "8", "10", "12", "16")) {
                if (value.startsWith(input)) {
                    suggestions.add(value);
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }
}
