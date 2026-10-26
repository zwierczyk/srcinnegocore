package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.upgrade.UpgradeStyle;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ArmorCreateCommand implements CommandExecutor, TabCompleter {

    private final HollyCore plugin;

    public ArmorCreateCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (plugin.getUpgradeManager() == null) {
            sender.sendMessage(ColorUtils.error("Addon ulepsz jest wyłączony."));
            return true;
        }
        if (!sender.hasPermission("Holly.cmd.armorcreate")) {
            sender.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.armorcreate"));
            return true;
        }

        UpgradeStyle style = plugin.getUpgradeManager().getActiveStyle();
        boolean sky = style.isSkyPvp();

        if (args.length >= 1 && args[0].equalsIgnoreCase("style")) {
            if (args.length < 2) {
                sender.sendMessage(ColorUtils.usage("/armorcreate style <standard|skypvp>"));
                sender.sendMessage(ColorUtils.colorizeComponent("&7Aktualny styl: &e" + style.getDisplayName()));
                return true;
            }
            UpgradeStyle next = UpgradeStyle.fromArgument(args[1]);
            plugin.getUpgradeManager().setActiveStyle(next);
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getUpgradeManager().getStyleSwitchedMessage(next)));
            return true;
        }

        int minArgs = sky ? 8 : 6;
        if (args.length < minArgs || !args[0].equalsIgnoreCase("create")) {
            if (sky) {
                sender.sendMessage(ColorUtils.usage(
                    "/armorcreate create <poziom> <cena> <startHex> <endHex> <hexarmor> <headvalue> <nazwa>"
                ));
            } else {
                sender.sendMessage(ColorUtils.usage(
                    "/armorcreate create <poziom> <cena> <startHex> <endHex> [headvalue|none] <nazwa>"
                ));
            }
            sender.sendMessage(ColorUtils.colorizeComponent("&7Aktualny styl: &e" + style.getDisplayName()
                + " &8(&7przełącz: &f/armorcreate style <standard|skypvp>&8)"));
            return true;
        }

        Integer level = parseInt(args[1]);
        Double price = parseDouble(args[2]);
        if (level == null || level < 1 || price == null || price < 0.0) {
            sender.sendMessage(ColorUtils.error("Poziom i cena muszą być poprawnymi liczbami."));
            return true;
        }

        String startHex = args[3];
        String endHex = args[4];
        int created;
        if (sky) {
            String hexArmor = args[5];
            String headValue = args[6];
            String armorName = normalizeArmorName(String.join(" ", Arrays.copyOfRange(args, 7, args.length)));
            created = plugin.getUpgradeManager().createGeneratedLevel(
                level, price, startHex, endHex, hexArmor, headValue, armorName
            );
        } else {
            String headValue = null;
            String armorName;
            if (args.length >= 7) {
                String candidate = args[5];
                if (isExplicitNoHead(candidate)) {
                    headValue = null;
                    armorName = normalizeArmorName(String.join(" ", Arrays.copyOfRange(args, 6, args.length)));
                } else if (isExplicitHeadPrefix(candidate)) {
                    headValue = candidate.substring(candidate.indexOf(':') + 1).trim();
                    if (isExplicitNoHead(headValue)) {
                        headValue = null;
                    }
                    armorName = normalizeArmorName(String.join(" ", Arrays.copyOfRange(args, 6, args.length)));
                } else if (looksLikeHeadValue(candidate)) {
                    headValue = candidate;
                    armorName = normalizeArmorName(String.join(" ", Arrays.copyOfRange(args, 6, args.length)));
                } else {
                    headValue = null;
                    armorName = normalizeArmorName(String.join(" ", Arrays.copyOfRange(args, 5, args.length)));
                }
            } else {
                String candidate = args[5];
                if (looksLikeHeadValue(candidate) || isExplicitHeadPrefix(candidate)) {
                    sender.sendMessage(ColorUtils.error("Podano wartość główki, ale brakuje nazwy zbroi! Użycie: /armorcreate create <poziom> <cena> <startHex> <endHex> [headvalue|none] <nazwa>"));
                    return true;
                }
                headValue = null;
                armorName = normalizeArmorName(candidate);
            }

            if (armorName.isEmpty()) {
                sender.sendMessage(ColorUtils.error("Podaj poprawną nazwę zbroi."));
                return true;
            }

            created = plugin.getUpgradeManager().createGeneratedLevel(level, price, startHex, endHex, headValue, armorName);
        }

        String message = "&#00FF3B✔ &8Ξ &#4EFA76Wygenerowano poziom &e" + level + " &adla &e" + created
            + " &atypów &8(&e" + style.getDisplayName() + "&8)&a.";
        sender.sendMessage(ColorUtils.colorizeComponent(message));
        return true;
    }

    /**
     * W komendzie ">" traktujemy jak spację, np. Gelerta>japidi -> Gelerta japidi.
     */
    private String normalizeArmorName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace('>', ' ').replaceAll(" +", " ").trim();
    }

    private boolean looksLikeHeadValue(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String trimmed = token.trim();
        if (isExplicitNoHead(trimmed)) {
            return false;
        }
        if (trimmed.startsWith("eyJ")) {
            return true;
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return true;
        }
        if (trimmed.length() >= 32 && trimmed.length() <= 64 && trimmed.matches("^[a-fA-F0-9]+$")) {
            return true;
        }
        if (dev.grzybalson.utils.HeadUtils.normalizeToTextureUrl(trimmed) != null) {
            return true;
        }
        return false;
    }

    private boolean isExplicitNoHead(String token) {
        if (token == null) {
            return false;
        }
        String lower = token.trim().toLowerCase(Locale.ROOT);
        return lower.equals("none")
            || lower.equals("brak")
            || lower.equals("-")
            || lower.equals("netherite")
            || lower.equals("default")
            || lower.equals("null");
    }

    private boolean isExplicitHeadPrefix(String token) {
        if (token == null) {
            return false;
        }
        String lower = token.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("head:") || lower.startsWith("texture:") || lower.startsWith("value:");
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        boolean sky = plugin.getUpgradeManager() != null && plugin.getUpgradeManager().getActiveStyle().isSkyPvp();

        if (args.length == 1) {
            return filter(List.of("create", "style"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("style")) {
            return filter(List.of("standard", "skypvp"), args[1]);
        }
        if (!args[0].equalsIgnoreCase("create")) {
            return List.of();
        }
        if (args.length == 2) {
            return filter(Arrays.asList("1", "2", "3", "8", "10", "20"), args[1]);
        }
        if (args.length == 3) {
            return filter(Arrays.asList("2", "1000", "1000000"), args[2]);
        }
        if (args.length == 4) {
            return List.of("#50FF7F");
        }
        if (args.length == 5) {
            return List.of("#00FF44");
        }
        if (sky) {
            if (args.length == 6) {
                return List.of("#2B2B2B");
            }
            if (args.length == 7) {
                return filter(List.of(
                    "none",
                    "54ac1c2a8a1284a9f3d6abf1cad9ebf8e2dca43cb931a8da6cc1176fe8ae5093"
                ), args[6]);
            }
            if (args.length == 8) {
                return List.of("Zeus");
            }
        } else {
            if (args.length == 6) {
                return filter(List.of(
                    "none",
                    "54ac1c2a8a1284a9f3d6abf1cad9ebf8e2dca43cb931a8da6cc1176fe8ae5093",
                    "Zeus"
                ), args[5]);
            }
            if (args.length == 7) {
                return List.of("Zeus");
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String partial) {
        String normalized = partial == null ? "" : partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                out.add(value);
            }
        }
        return out;
    }
}
