package dev.grzybalson.modules.cases;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CaseCommand implements CommandExecutor, TabCompleter {

    private static final String[] USAGE = {
        "/case create <nazwa>",
        "/case setlocation <nazwa>",
        "/case removelocation <nazwa>",
        "/case setkey <nazwa>",
        "/case edit <nazwa>",
        "/case enable <nazwa>",
        "/case disable <nazwa>",
        "/case message <nazwa> [on/off/default]",
        "/case individualgui <nazwa> <on/off>",
        "/case setdecoration <nazwa> <preview/animation/result>",
        "/case give <nick> <nazwa> <ilość>",
        "/case giveall <nazwa> <ilość>",
        "/case rotacja [nazwa]",
        "/case reload"
    };

    private final CaseManager manager;

    public CaseCommand(CaseManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("reload")) {
            manager.reload();
            sender.sendMessage(manager.colorMessage("messages.reloaded", Map.of()));
            return true;
        }

        if (subCommand.equals("give")) {
            handleGive(sender, args);
            return true;
        }

        if (subCommand.equals("giveall")) {
            handleGiveAll(sender, args);
            return true;
        }

        if (subCommand.equals("rotacja")) {
            handleRotation(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(manager.colorMessage("messages.only-player", Map.of()));
            return true;
        }

        if (args.length < 2) {
            sendUsage(player, usageFor(subCommand));
            return true;
        }

        String caseName = args[1];
        if (!isValidCaseName(caseName)) {
            player.sendMessage(ColorUtils.error("Nazwa skrzynki może zawierać tylko litery, cyfry, _ i -."));
            return true;
        }

        switch (subCommand) {
            case "create" -> handleCreate(player, caseName);
            case "setlocation" -> handleSetLocation(player, caseName);
            case "removelocation" -> handleRemoveLocation(player, caseName);
            case "setkey" -> handleSetKey(player, caseName);
            case "edit" -> handleEdit(player, caseName);
            case "enable" -> handleEnable(player, caseName, true);
            case "disable" -> handleEnable(player, caseName, false);
            case "message" -> handleMessage(player, caseName, args);
            case "individualgui" -> handleIndividualGui(player, caseName, args);
            case "setdecoration" -> handleSetDecoration(player, caseName, args);
            default -> sendUsage(player);
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sendUsage(sender, "/case give <nick> <nazwa> <ilość>");
            return;
        }

        String playerName = args[1];
        String caseName = args[2];
        if (!isValidCaseName(caseName)) {
            sender.sendMessage(ColorUtils.error("Nazwa skrzynki może zawierać tylko litery, cyfry, _ i -."));
            return;
        }

        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            sender.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }
        if (!manager.hasConfiguredKey(definition)) {
            sender.sendMessage(manager.colorMessage("messages.key-not-configured", Map.of("case", definition.name())));
            return;
        }

        Integer amount = parseAmount(args[3]);
        if (amount == null) {
            sender.sendMessage(manager.colorMessage("messages.invalid-amount", Map.of("case", definition.name())));
            return;
        }

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(manager.colorMessage("messages.player-not-found", Map.of("player", playerName)));
            return;
        }

        manager.giveKey(target, definition, amount);
        sender.sendMessage(manager.colorMessage("messages.key-given", Map.of(
            "case", definition.name(),
            "player", target.getName(),
            "amount", String.valueOf(amount)
        )));
    }

    private void handleGiveAll(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendUsage(sender, "/case giveall <nazwa> <ilość>");
            return;
        }

        String caseName = args[1];
        if (!isValidCaseName(caseName)) {
            sender.sendMessage(ColorUtils.error("Nazwa skrzynki może zawierać tylko litery, cyfry, _ i -."));
            return;
        }

        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            sender.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }
        if (!manager.hasConfiguredKey(definition)) {
            sender.sendMessage(manager.colorMessage("messages.key-not-configured", Map.of("case", definition.name())));
            return;
        }

        Integer amount = parseAmount(args[2]);
        if (amount == null) {
            sender.sendMessage(manager.colorMessage("messages.invalid-amount", Map.of("case", definition.name())));
            return;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            manager.giveKey(onlinePlayer, definition, amount);
        }
        sender.sendMessage(manager.colorMessage("messages.key-given-all", Map.of(
            "case", definition.name(),
            "amount", String.valueOf(amount)
        )));
    }

    private Integer parseAmount(String raw) {
        try {
            int amount = Integer.parseInt(raw);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void handleRotation(CommandSender sender, String[] args) {
        String caseName = args.length >= 2 ? args[1] : "luckyblock";
        if (!isValidCaseName(caseName)) {
            sender.sendMessage(ColorUtils.error("Nazwa skrzynki może zawierać tylko litery, cyfry, _ i -."));
            return;
        }

        CaseManager.RotationResult result = manager.forceRewardRotation(caseName);
        if (!result.success()) {
            sender.sendMessage(manager.colorMessage("messages.rotation-not-available", Map.of("case", caseName)));
            return;
        }

        sender.sendMessage(manager.colorMessage("messages.rotation-forced", Map.of(
            "case", result.caseName(),
            "item", result.itemId(),
            "time", result.time()
        )));
    }

    private void handleCreate(Player player, String caseName) {
        if (!manager.createCase(caseName)) {
            player.sendMessage(manager.colorMessage("messages.case-exists", Map.of("case", caseName)));
            return;
        }
        player.sendMessage(manager.colorMessage("messages.case-created", Map.of("case", caseName)));
    }

    private void handleSetLocation(Player player, String caseName) {
        if (manager.getCase(caseName) == null) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }

        Block targetBlock = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        if (targetBlock == null) {
            player.sendMessage(manager.colorMessage("messages.no-target-block", Map.of("case", caseName)));
            return;
        }

        manager.setLocation(caseName, CaseBlockLocation.fromBlock(targetBlock));
        player.sendMessage(manager.colorMessage("messages.location-set", Map.of("case", caseName)));
    }

    private void handleRemoveLocation(Player player, String caseName) {
        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }

        Block targetBlock = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
        boolean removed = targetBlock == null
            ? manager.removeAllLocations(definition.name())
            : manager.removeLocation(definition.name(), CaseBlockLocation.fromBlock(targetBlock));

        if (!removed) {
            player.sendMessage(manager.colorMessage("messages.location-not-set", Map.of("case", definition.name())));
            return;
        }

        player.sendMessage(manager.colorMessage("messages.location-removed", Map.of("case", definition.name())));
    }

    private void handleSetKey(Player player, String caseName) {
        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(manager.colorMessage("messages.no-key-in-hand", Map.of("case", definition.name())));
            return;
        }

        manager.setKey(definition.name(), item.clone());
        player.sendMessage(manager.colorMessage("messages.key-set", Map.of("case", definition.name())));
    }

    private void handleEdit(Player player, String caseName) {
        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }

        player.openInventory(manager.createEditorInventory(definition));
    }

    private void handleEnable(Player player, String caseName, boolean enabled) {
        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }

        manager.setEnabled(definition.name(), enabled);
        player.sendMessage(manager.colorMessage(enabled ? "messages.enabled" : "messages.disabled", Map.of("case", definition.name())));
    }

    private void handleMessage(Player player, String caseName, String[] args) {
        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }

        Boolean newState;
        if (args.length >= 3) {
            String val = args[2].toLowerCase(Locale.ROOT);
            if (val.equals("on") || val.equals("true") || val.equals("wlacz") || val.equals("włącz")) {
                newState = Boolean.TRUE;
            } else if (val.equals("off") || val.equals("false") || val.equals("wylacz") || val.equals("wyłącz")) {
                newState = Boolean.FALSE;
            } else if (val.equals("default") || val.equals("domyslne") || val.equals("domyślne") || val.equals("reset")) {
                newState = null;
            } else {
                sendUsage(player, "/case message <nazwa> [on/off/default]");
                return;
            }
        } else {
            boolean current = manager.isMessageEnabled(definition);
            newState = !current;
        }

        manager.setMessageEnabled(definition.name(), newState);
        boolean effectiveNow = manager.isMessageEnabled(definition);
        player.sendMessage(manager.colorMessage(effectiveNow ? "messages.message-enabled" : "messages.message-disabled", Map.of("case", definition.name())));
    }

    private void handleIndividualGui(Player player, String caseName, String[] args) {
        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }

        if (args.length < 3) {
            sendUsage(player, "/case individualgui <nazwa> <on/off>");
            return;
        }

        String val = args[2].toLowerCase(Locale.ROOT);
        boolean enable;
        if (val.equals("on") || val.equals("true") || val.equals("wlacz") || val.equals("włącz")) {
            enable = true;
        } else if (val.equals("off") || val.equals("false") || val.equals("wylacz") || val.equals("wyłącz")) {
            enable = false;
        } else {
            sendUsage(player, "/case individualgui <nazwa> <on/off>");
            return;
        }

        manager.setIndividualGui(definition.name(), enable);
        String statusMsg = enable ? "&aWłączono" : "&cWyłączono";
        player.sendMessage(ColorUtils.colorize(statusMsg + " &7osobne GUI dla skrzynki &f" + definition.name() + "&7."));
    }

    private void handleSetDecoration(Player player, String caseName, String[] args) {
        CaseDefinition definition = manager.getCase(caseName);
        if (definition == null) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", caseName)));
            return;
        }

        if (!definition.individualGui()) {
            player.sendMessage(ColorUtils.error("Ta skrzynka nie ma włączonego osobnego GUI. Użyj /case individualgui " + caseName + " on"));
            return;
        }

        if (args.length < 3) {
            sendUsage(player, "/case setdecoration <nazwa> <preview/animation/result>");
            return;
        }

        String type = args[2].toLowerCase(Locale.ROOT);
        if (!type.equals("preview") && !type.equals("animation") && !type.equals("result")) {
            sendUsage(player, "/case setdecoration <nazwa> <preview/animation/result>");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage(ColorUtils.error("Musisz trzymać item dekoracyjny w głównej ręce."));
            return;
        }

        player.sendMessage(ColorUtils.colorize("&7Kliknij na sloty w inventory aby ustawić dekorację dla &f" + type + "&7."));
        player.sendMessage(ColorUtils.colorize("&7Item: &f" + (item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
            item.getItemMeta().getDisplayName() : item.getType().name())));
        player.sendMessage(ColorUtils.colorize("&cFunkcja w przygotowaniu - użyj edycji pliku konfiguracyjnego."));
    }

    private boolean isValidCaseName(String caseName) {
        return caseName != null && caseName.matches("[A-Za-z0-9_-]{1,32}");
    }

    private String usageFor(String subCommand) {
        return switch (subCommand.toLowerCase(Locale.ROOT)) {
            case "create" -> "/case create <nazwa>";
            case "setlocation" -> "/case setlocation <nazwa>";
            case "removelocation" -> "/case removelocation <nazwa>";
            case "setkey" -> "/case setkey <nazwa>";
            case "edit" -> "/case edit <nazwa>";
            case "enable" -> "/case enable <nazwa>";
            case "disable" -> "/case disable <nazwa>";
            case "message" -> "/case message <nazwa> [on/off/default]";
            case "individualgui" -> "/case individualgui <nazwa> <on/off>";
            case "setdecoration" -> "/case setdecoration <nazwa> <preview/animation/result>";
            case "give" -> "/case give <nick> <nazwa> <ilość>";
            case "giveall" -> "/case giveall <nazwa> <ilość>";
            case "rotacja" -> "/case rotacja [nazwa]";
            case "reload" -> "/case reload";
            default -> "/case <subkomenda>";
        };
    }

    private void sendUsage(CommandSender sender, String... commands) {
        ColorUtils.sendUsage(sender, commands.length == 0 ? USAGE : commands);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            addIfStartsWith(suggestions, "create", args[0]);
            addIfStartsWith(suggestions, "setlocation", args[0]);
            addIfStartsWith(suggestions, "removelocation", args[0]);
            addIfStartsWith(suggestions, "setkey", args[0]);
            addIfStartsWith(suggestions, "edit", args[0]);
            addIfStartsWith(suggestions, "enable", args[0]);
            addIfStartsWith(suggestions, "disable", args[0]);
            addIfStartsWith(suggestions, "message", args[0]);
            addIfStartsWith(suggestions, "individualgui", args[0]);
            addIfStartsWith(suggestions, "setdecoration", args[0]);
            addIfStartsWith(suggestions, "give", args[0]);
            addIfStartsWith(suggestions, "giveall", args[0]);
            addIfStartsWith(suggestions, "rotacja", args[0]);
            addIfStartsWith(suggestions, "reload", args[0]);
            return suggestions;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length == 2) {
                addIfStartsWith(suggestions, "nowa_skrzynka", args[1]);
            }
            return suggestions;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                String input = args[1].toLowerCase(Locale.ROOT);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                        suggestions.add(player.getName());
                    }
                }
            } else if (args.length == 3) {
                addCaseNameSuggestions(suggestions, args[2]);
            } else if (args.length == 4) {
                addIfStartsWith(suggestions, "1", args[3]);
                addIfStartsWith(suggestions, "16", args[3]);
                addIfStartsWith(suggestions, "64", args[3]);
            }
            return suggestions;
        }

        if (args[0].equalsIgnoreCase("giveall")) {
            if (args.length == 2) {
                addCaseNameSuggestions(suggestions, args[1]);
            } else if (args.length == 3) {
                addIfStartsWith(suggestions, "1", args[2]);
                addIfStartsWith(suggestions, "16", args[2]);
                addIfStartsWith(suggestions, "64", args[2]);
            }
            return suggestions;
        }

        if (args[0].equalsIgnoreCase("rotacja")) {
            if (args.length == 2) {
                addCaseNameSuggestions(suggestions, args[1]);
            }
            return suggestions;
        }

        if (args[0].equalsIgnoreCase("message")) {
            if (args.length == 2) {
                addCaseNameSuggestions(suggestions, args[1]);
            } else if (args.length == 3) {
                addIfStartsWith(suggestions, "on", args[2]);
                addIfStartsWith(suggestions, "off", args[2]);
                addIfStartsWith(suggestions, "default", args[2]);
            }
            return suggestions;
        }

        if (args[0].equalsIgnoreCase("individualgui")) {
            if (args.length == 2) {
                addCaseNameSuggestions(suggestions, args[1]);
            } else if (args.length == 3) {
                addIfStartsWith(suggestions, "on", args[2]);
                addIfStartsWith(suggestions, "off", args[2]);
            }
            return suggestions;
        }

        if (args[0].equalsIgnoreCase("setdecoration")) {
            if (args.length == 2) {
                addCaseNameSuggestions(suggestions, args[1]);
            } else if (args.length == 3) {
                addIfStartsWith(suggestions, "preview", args[2]);
                addIfStartsWith(suggestions, "animation", args[2]);
                addIfStartsWith(suggestions, "result", args[2]);
            }
            return suggestions;
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("reload")) {
            addCaseNameSuggestions(suggestions, args[1]);
        }
        return suggestions;
    }

    private void addCaseNameSuggestions(List<String> suggestions, String rawInput) {
        String input = rawInput.toLowerCase(Locale.ROOT);
        for (String name : manager.getCaseNames()) {
            if (name.toLowerCase(Locale.ROOT).startsWith(input)) {
                suggestions.add(name);
            }
        }
    }

    private void addIfStartsWith(List<String> suggestions, String value, String input) {
        if (value.startsWith(input.toLowerCase(Locale.ROOT))) {
            suggestions.add(value);
        }
    }
}
