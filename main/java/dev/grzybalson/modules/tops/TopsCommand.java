package dev.grzybalson.modules.tops;

import dev.grzybalson.utils.ColorUtils;
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
import java.util.Locale;
import java.util.stream.Collectors;

public class TopsCommand implements CommandExecutor, TabCompleter {

    private final TopsManager manager;

    public TopsCommand(TopsManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTa komenda jest tylko dla graczy!"));
            return true;
        }

        if (!player.hasPermission("Holly.tops.manage")) {
            player.sendMessage(ColorUtils.colorizeComponent(ColorUtils.formatNoPermission("/tops", "Holly.tops.manage")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ColorUtils.colorizeComponent("&#DD5FFF&lᴛᴏᴘꜱ &8— &7Komendy:"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &f/tops create <typ> <pozycja>"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &f/tops delete <id>"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &f/tops list"));
            player.sendMessage(ColorUtils.colorizeComponent("&#DD5FFF&lᴛᴏᴘꜱ &8— &7Typy:"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fPOINTS &8- &7Ranking punktów (ELO)"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fCLAN &8- &7Ranking klanów"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fKILLS &8- &7Top zabójstw"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fDEATHS &8- &7Top śmierci"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fTIME &8- &7Top czasu gry"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fCRATES &8- &7Top otwartych skrzynek"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fBLOCKS &8- &7Top wykopanych bloków"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fMONEY &8- &7Top kasy"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fSWORD &8- &7Top poziomu miecza"));
            player.sendMessage(ColorUtils.colorizeComponent("  &8× &fZBROJA &8- &7Top poziomu zbroi"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    ColorUtils.sendUsage(player, "/" + label + " create <typ> <pozycja>");
                    return true;
                }
                String upper = EquipmentTopTracker.normalizeType(args[1]);
                if (!this.manager.getTypes().contains(upper)) {
                    player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C&l✖ &#C1C1C1Nieznany typ. Dostępne: &f"
                        + String.join("&7, &f", this.manager.getTypes())));
                    return true;
                }
                int position;
                try {
                    position = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C&l✖ &#C1C1C1Podaj poprawną pozycję (liczba)."));
                    return true;
                }
                if (position < 1 || position > 100) {
                    player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C&l✖ &#C1C1C1Pozycja musi być od 1 do 100."));
                    return true;
                }
                this.manager.createTop(upper, position, player.getLocation());
                player.sendMessage(ColorUtils.colorizeComponent("&#60FF8B&l✔ &#C1C1C1Stworzono topkę &f" + upper
                    + " #" + position + " &#C1C1C1na Twojej pozycji."));
            }
            case "delete" -> {
                if (args.length < 2) {
                    ColorUtils.sendUsage(player, "/" + label + " delete <id>");
                    return true;
                }
                String id = args[1];
                if (!this.manager.getTopIds().contains(id)) {
                    player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C&l✖ &#C1C1C1Topka &f" + id + " &#C1C1C1nie istnieje."));
                    return true;
                }
                this.manager.deleteTop(id);
                player.sendMessage(ColorUtils.colorizeComponent("&#60FF8B&l✔ &#C1C1C1Usunięto topkę &f" + id));
            }
            case "list" -> {
                List<String> ids = this.manager.getTopIds();
                if (ids.isEmpty()) {
                    player.sendMessage(ColorUtils.colorizeComponent("&#C1C1C1Brak aktywnych topek."));
                    return true;
                }
                player.sendMessage(ColorUtils.colorizeComponent("&#DD5FFF&lᴛᴏᴘꜱ &8— &7Aktywne topki:"));
                for (String id : ids) {
                    player.sendMessage(ColorUtils.colorizeComponent("  &8× &f" + id));
                }
            }
            default -> player.sendMessage(ColorUtils.colorizeComponent("&#FF5C5C&l✖ &#C1C1C1Nieznana podkomenda. &f/tops"));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("Holly.tops.manage")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(List.of("create", "delete", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filter(List.of("POINTS", "CLAN", "KILLS", "DEATHS", "TIME", "CRATES", "BLOCKS", "MONEY", "SWORD", "ZBROJA", "sword", "zbroja"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return filter(List.of("1", "2", "3", "4", "5", "10"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            return filter(this.manager.getTopIds(), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream()
            .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower))
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
