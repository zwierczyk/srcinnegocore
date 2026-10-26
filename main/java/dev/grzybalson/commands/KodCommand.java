package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.CodeConfig;
import dev.grzybalson.modules.discordreward.DiscordRewardManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KodCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final Set<UUID> pendingDiscordChecks = ConcurrentHashMap.newKeySet();

    public KodCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        CodeConfig config = plugin.getConfigManager().getCodeConfig();

        if (args.length == 0) {
            ColorUtils.sendUsage(player, "/kod <kod>");
            return true;
        }

        if (isRankGiveawayCode(args)) {
            if (plugin.getRankGiveawayManager().tryJoin(player)) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return true;
        }

        String codeName = args[0];
        if (!config.isRequireDiscordReward()) {
            redeem(player, codeName);
            return true;
        }

        DiscordRewardManager rewardManager = plugin.getDiscordRewardManager();
        if (rewardManager == null) {
            denyDiscordRequired(player, config);
            return true;
        }

        if (!pendingDiscordChecks.add(player.getUniqueId())) {
            return true;
        }

        rewardManager.hasMinecraftClaim(player.getUniqueId(), player.getName()).whenComplete((claimed, throwable) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingDiscordChecks.remove(player.getUniqueId());
                if (!player.isOnline()) {
                    return;
                }
                if (throwable != null) {
                    plugin.getLogger().warning("Nie udalo sie sprawdzic nagrody Discord przed /kod dla "
                        + player.getName() + ": " + throwable.getMessage());
                    denyDiscordRequired(player, plugin.getConfigManager().getCodeConfig());
                    return;
                }
                if (!Boolean.TRUE.equals(claimed)) {
                    denyDiscordRequired(player, plugin.getConfigManager().getCodeConfig());
                    return;
                }
                redeem(player, codeName);
            });
        });

        return true;
    }

    private void redeem(Player player, String codeName) {
        if (plugin.getCodeManager().redeemCode(player, codeName)) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            plugin.getCodeManager().hideBossBarForPlayer(player, codeName);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void denyDiscordRequired(Player player, CodeConfig config) {
        player.sendMessage(ColorUtils.colorizeComponent(config.getCodeDiscordRequired()));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            CodeConfig config = plugin.getConfigManager().getCodeConfig();
            if ("rozdanie".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                completions.add("rozdanie rang");
            }
            for (String code : config.getCodes().keySet()) {
                CodeConfig.CodeEntry entry = config.getCode(code);
                if (entry != null && entry.isActive()) {
                    if (code.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(code);
                    }
                }
            }
        }

        return completions;
    }

    private boolean isRankGiveawayCode(String[] args) {
        if (args.length < 2) {
            return false;
        }
        return args[0].equalsIgnoreCase("rozdanie") && args[1].equalsIgnoreCase("rang");
    }
}
