package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.combat.CombatManager;
import dev.grzybalson.modules.report.ReportStore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class FastReportCommand implements CommandExecutor {

    private final HollyCore plugin;
    private final ReportStore reportStore;

    public FastReportCommand(HollyCore plugin, ReportStore reportStore) {
        this.plugin = plugin;
        this.reportStore = reportStore;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.szybkiezgloszenie")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.szybkiezgloszenie"));
            return true;
        }

        CombatManager combatManager = plugin.getCombatManager();
        if (combatManager == null || !combatManager.isInCombat(player)) {
            player.sendMessage(ColorUtils.colorizeComponent("&cNie jesteś podczas &4walki!"));
            return true;
        }

        UUID targetUuid = combatManager.getLastAttacker(player);
        if (targetUuid == null) {
            player.sendMessage(ColorUtils.colorizeComponent("&cNie jesteś podczas &4walki!"));
            return true;
        }

        if (reportStore.hasReport(player.getUniqueId(), targetUuid)) {
            player.sendMessage(ColorUtils.colorizeComponent("&cZgłosiłeś już tego gracza!"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() != null ? target.getName() : targetUuid.toString();

        reportStore.insert(player.getUniqueId(), player.getName(), targetUuid, targetName);

        player.sendMessage(ColorUtils.colorizeComponent("&aZgłosiłeś gracza &2" + targetName));
        notifyStaff(player.getName(), targetName);
        return true;
    }

    private void notifyStaff(String reporterName, String targetName) {
        String message = "&8[&cSzybkie zgłoszenie&8] &f" + reporterName
            + " &7zgłosił gracza &c" + targetName + "&7 podczas walki.";
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("Holly.cmd.sprawdz")
                || online.hasPermission("Holly.cmd.helpop")
                || online.hasPermission("holly.cmd.helpop")) {
                online.sendMessage(ColorUtils.colorizeComponent(message));
            }
        }
    }
}
