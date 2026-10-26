package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ClickCooldown;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EventCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;
    private final EventManager manager;

    public EventCommand(HollyCore plugin, EventManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }
        if (!player.hasPermission("holly.event.use")) {
            player.sendMessage(ColorUtils.noPermission(label, "holly.event.use"));
            return true;
        }
        if (!ClickCooldown.tryUse(player, "open-event-gui", 500L)) {
            return true;
        }
        manager.openMain(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
