package dev.grzybalson.modules.flamehubcustomitems.commands;

import dev.grzybalson.modules.flamehubcustomitems.config.FlameHubConfigManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.CooldownManager;
import dev.grzybalson.modules.flamehubcustomitems.manager.EffectManager;
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

public class FlameResetCommand implements CommandExecutor, TabCompleter {

    private final FlameHubConfigManager configManager;
    private final CooldownManager cooldownManager;
    private final EffectManager effectManager;

    public FlameResetCommand(FlameHubConfigManager configManager, CooldownManager cooldownManager, EffectManager effectManager) {
        this.configManager = configManager;
        this.cooldownManager = cooldownManager;
        this.effectManager = effectManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.flamehub.reset") && !sender.hasPermission("flame.reset")) {
            sender.sendMessage(configManager.getMessage("no-permission"));
            return true;
        }

        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player playerSender)) {
                sender.sendMessage(configManager.getMessage("player-not-found", "{PLAYER}", "Brak"));
                return true;
            }
            target = playerSender;
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(configManager.getMessage("player-not-found", "{PLAYER}", args[0]));
                return true;
            }
        }

        cooldownManager.resetCooldowns(target);
        effectManager.clearEffects(target);

        if (sender.equals(target)) {
            sender.sendMessage(configManager.getMessage("cooldown-reset-self"));
        } else {
            sender.sendMessage(configManager.getMessage("cooldown-reset-other", "{PLAYER}", target.getName()));
            target.sendMessage(configManager.getMessage("cooldown-reset-notify"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("Holly.flamehub.reset") && !sender.hasPermission("flame.reset")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(player.getName());
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}
