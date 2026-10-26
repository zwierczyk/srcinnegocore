package dev.grzybalson.modules.event;

import dev.grzybalson.modules.event.dungeon.DungeonSession;
import dev.grzybalson.modules.event.dungeon.DungeonSessionManager;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DungeonSkipCommand implements CommandExecutor {

    private final DungeonSessionManager dungeon;

    public DungeonSkipCommand(DungeonSessionManager dungeon) {
        this.dungeon = dungeon;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTylko gracz!"));
            return true;
        }
        DungeonSession session = dungeon.getByPlayer(player.getUniqueId());
        if (session == null || session.getPhase() != DungeonSession.Phase.TUTORIAL) {
            player.sendMessage(ColorUtils.colorizeComponent(
                dungeon.getSettings().getString("messages.skip-need-tutorial",
                    "&#FF0000✘ &8Ξ &cMozesz uzyc &e/skip &ctylko podczas tutoriala.")
            ));
            return true;
        }
        session.voteSkip(player);
        player.sendMessage(ColorUtils.colorizeComponent(
            dungeon.getSettings().getString("messages.skip-ok",
                "&#00FF3B✔ &8Ξ &#4EFA76Zaglosowano na skip tutoriala.")
        ));
        return true;
    }
}
