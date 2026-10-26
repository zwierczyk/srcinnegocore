package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public class GammaCommand implements CommandExecutor {

    public GammaCommand(HollyCore plugin) {}

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Komenda tylko dla graczy!"));
            return true;
        }

        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.sendMessage(ColorUtils.success("Widzenie w ciemności (Gamma) zostało &cwyłączone&a."));
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            player.sendMessage(ColorUtils.success("Widzenie w ciemności (Gamma) zostało &awłączone&a."));
        }

        return true;
    }
}
