package dev.grzybalson.modules.customhit;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class CustomHitCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public CustomHitCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("Holly.cmd.customhit")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.customhit"));
            if (sender instanceof Player player) {
                plugin.getSoundManager().playError(player);
            }
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("Holly.cmd.customhit.reload")) {
                sender.sendMessage(ColorUtils.noPermission(label + " reload", "Holly.cmd.customhit.reload"));
                if (sender instanceof Player player) {
                    plugin.getSoundManager().playError(player);
                }
                return true;
            }

            String system = plugin.resolveDamageSystem();
            if ("hollyservice".equals(system)) {
                if (plugin.getHollyDamageService() == null) {
                    sender.sendMessage(ColorUtils.warning(
                        "System hollyservice jest ustawiony w addons.yml, ale nie udało się go uruchomić. "
                            + "Ustaw damage-system: hollyservice i zrób restart serwera."
                    ));
                } else {
                    plugin.getHollyDamageService().reload();
                    sender.sendMessage(ColorUtils.success("Przeładowano system obrażeń hollyservice (hollydamage)!"));
                }
            } else if ("customhit".equals(system)) {
                if (plugin.getCustomHitsService() == null) {
                    sender.sendMessage(ColorUtils.warning(
                        "System customhit jest ustawiony w addons.yml, ale nie udało się go uruchomić. Zrób restart serwera."
                    ));
                } else {
                    plugin.getConfigManager().getCustomHitConfig().load(true);
                    plugin.getCustomHitsService().reload();
                    sender.sendMessage(ColorUtils.success("Przeładowano system obrażeń customhit!"));
                }
            } else {
                sender.sendMessage(ColorUtils.warning(
                    "System obrażeń jest wyłączony (addons.yml → damage-system: none)."
                ));
            }

            if (sender instanceof Player player) {
                plugin.getSoundManager().playSuccess(player);
            }
            return true;
        }

        sendUsage(sender, label, plugin.resolveDamageSystem());
        if (sender instanceof Player player) {
            plugin.getSoundManager().playError(player);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label, String activeSystem) {
        ColorUtils.sendUsage(sender, "/" + label + " reload");
        sender.sendMessage(ColorUtils.info("Aktywny system: &f" + activeSystem));
        sender.sendMessage(ColorUtils.info("Zmiana systemu: &faddons.yml &7→ &fdamage-system: customhit &8| &fhollyservice"));
        sender.sendMessage(ColorUtils.info("Po zmianie damage-system wymagany jest &frestart &7serwera."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }
}
