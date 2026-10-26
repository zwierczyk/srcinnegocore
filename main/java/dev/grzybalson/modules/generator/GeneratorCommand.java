package dev.grzybalson.modules.generator;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class GeneratorCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "Holly.cmd.generator";

    private final GeneratorManager manager;

    public GeneratorCommand(GeneratorManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(ColorUtils.noPermission("/" + label, PERMISSION));
            return true;
        }
        if (args.length == 0) {
            this.sendHelp(player, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> this.create(player, label, args);
            case "clearblocks" -> this.clearBlocks(player, label, args);
            case "addblock" -> this.addBlock(player, label, args);
            case "move" -> this.move(player, label, args);
            case "clone" -> this.cloneGenerator(player, label, args);
            case "renamehologram" -> this.renameHologram(player, label, args);
            case "tp" -> this.teleport(player, label, args);
            case "height" -> this.height(player, label, args);
            case "radius" -> this.radius(player, label, args);
            case "settime" -> this.setTime(player, label, args);
            case "setblock" -> this.setBlock(player, label, args);
            case "setpermission", "setperm" -> this.setPermission(player, label, args);
            case "delete", "remove" -> this.delete(player, label, args);
            case "list" -> this.list(player);
            case "reload" -> this.reload(player);
            case "reset" -> this.reset(player, label, args);
            case "honeyregenrate", "honeyregenerate" -> this.blockRegen(player, args, this.manager.getHoneyRegenerate(), "HONEY_BLOCK", "honeyregenerate");
            case "cyanregenrate", "cyanregenerate", "cyanpowderregenerate" -> this.blockRegen(player, args, this.manager.getCyanConcretePowderRegenerate(), "CYAN_CONCRETE_POWDER", "cyanpowderregenerate");
            default -> this.sendHelp(player, label);
        }
        return true;
    }

    private void create(Player player, String label, String[] args) {
        if (args.length < 6) {
            player.sendMessage(ColorUtils.usage("/" + label + " create <nazwa> <radius> <height> <blok> <czas> [permisja]"));
            return;
        }
        Material material = this.parseMaterial(args[4]);
        if (material == null || material.isAir()) {
            this.send(player, this.message("invalid-material"));
            return;
        }
        int radius = this.parseInt(args[2], -1);
        int height = this.parseInt(args[3], -1);
        if (!this.validSize(radius) || !this.validSize(height)) {
            this.send(player, this.message("invalid-size"));
            return;
        }
        long time = this.manager.parseDurationMillis(args[5]);
        if (time < 1000L) {
            this.send(player, this.message("invalid-time"));
            return;
        }
        String permission = args.length >= 7 ? args[6] : "";
        if (!this.manager.create(args[1], player.getLocation(), radius, height, material, time, permission)) {
            this.send(player, this.message("exists"));
            return;
        }
        this.ok(player, "created");
    }

    private void clearBlocks(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.usage("/" + label + " clearblocks <generator>"));
            return;
        }
        if (!this.manager.clearBlocks(args[1])) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void addBlock(Player player, String label, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ColorUtils.usage("/" + label + " addblock <generator> <blok> <szansa>"));
            return;
        }
        Material material = this.parseMaterial(args[2]);
        if (material == null || material.isAir()) {
            this.send(player, this.message("invalid-material"));
            return;
        }
        double chance = this.parseDouble(args[3], -1.0D);
        if (chance <= 0.0D) {
            this.send(player, this.message("invalid-size"));
            return;
        }
        if (!this.manager.addBlock(args[1], material, chance)) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void move(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.usage("/" + label + " move <generator>"));
            return;
        }
        if (!this.manager.move(args[1], player.getLocation())) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void cloneGenerator(Player player, String label, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.usage("/" + label + " clone <generator> <nazwa>"));
            return;
        }
        if (!this.manager.cloneGenerator(args[1], args[2], player.getLocation())) {
            this.send(player, this.manager.exists(args[2]) ? this.message("exists") : this.message("not-found"));
            return;
        }
        this.ok(player, "created");
    }

    private void renameHologram(Player player, String label, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.usage("/" + label + " renamehologram <generator> <treść>"));
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (!this.manager.renameHologram(args[1], name)) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void teleport(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.usage("/" + label + " tp <generator>"));
            return;
        }
        Location location = this.manager.getTeleportLocation(args[1]);
        if (location == null) {
            this.send(player, this.message("not-found"));
            return;
        }
        player.teleport(location);
        this.send(player, this.message("teleported"));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F);
    }

    private void height(Player player, String label, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.usage("/" + label + " height <generator> <wysokość>"));
            return;
        }
        int height = this.parseInt(args[2], -1);
        if (!this.validSize(height)) {
            this.send(player, this.message("invalid-size"));
            return;
        }
        if (!this.manager.setHeight(args[1], height)) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void radius(Player player, String label, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.usage("/" + label + " radius <generator> <wielkość>"));
            return;
        }
        int radius = this.parseInt(args[2], -1);
        if (!this.validSize(radius)) {
            this.send(player, this.message("invalid-size"));
            return;
        }
        if (!this.manager.setRadius(args[1], radius)) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void setTime(Player player, String label, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.usage("/" + label + " settime <generator> <czas>"));
            return;
        }
        long time = this.manager.parseDurationMillis(args[2]);
        if (time < 1000L) {
            this.send(player, this.message("invalid-time"));
            return;
        }
        if (!this.manager.setTime(args[1], time)) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void setBlock(Player player, String label, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.usage("/" + label + " setblock <generator> <blok>"));
            return;
        }
        Material material = this.parseMaterial(args[2]);
        if (material == null || material.isAir()) {
            this.send(player, this.message("invalid-material"));
            return;
        }
        if (!this.manager.setDefaultBlock(args[1], material)) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void setPermission(Player player, String label, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ColorUtils.usage("/" + label + " setpermission <generator> <permisja|none>"));
            return;
        }
        String permission = "none".equalsIgnoreCase(args[2]) ? "" : args[2];
        if (!this.manager.setPermission(args[1], permission)) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "updated");
    }

    private void delete(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.usage("/" + label + " remove <generator>"));
            return;
        }
        if (!this.manager.delete(args[1])) {
            this.send(player, this.message("not-found"));
            return;
        }
        this.ok(player, "deleted");
    }

    private void list(Player player) {
        List<String> ids = this.manager.getGeneratorIds();
        if (ids.isEmpty()) {
            this.send(player, this.message("not-found"));
            return;
        }
        player.sendMessage(ColorUtils.info("Generatory (" + ids.size() + "):"));
        for (String id : ids) {
            Generator generator = this.manager.getGenerator(id);
            if (generator == null) {
                continue;
            }
            String permission = generator.getPermission() == null || generator.getPermission().isBlank() ? "brak" : generator.getPermission();
            player.sendMessage(ColorUtils.colorizeComponent("&8• &f" + id
                + " &7| blok: &f" + generator.getDefaultBlock().name()
                + " &7| czas: &f" + this.manager.formatDuration(generator.getTime())
                + " &7| perm: &f" + permission));
        }
    }

    private void reload(Player player) {
        this.manager.reload();
        this.send(player, this.message("reloaded"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.5F);
    }

    private void reset(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.usage("/" + label + " reset <generator|*>"));
            return;
        }

        String target = args[1];
        if ("*".equals(target) || "all".equalsIgnoreCase(target)) {
            int count = this.manager.resetAllGenerators();
            this.send(player, "&#60FF8B✔ &#C1C1C1Zresetowano &f" + count + " &#C1C1C1generatorow z animacja.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
            return;
        }

        if (!this.manager.resetGenerator(target)) {
            this.send(player, this.message("not-found"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.8F);
            return;
        }

        this.send(player, "&#60FF8B✔ &#C1C1C1Zresetowano generator &f" + target + "&#C1C1C1 z animacja.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
    }

    private void blockRegen(Player player, String[] args, BlockRegenerateManager regenerateManager, String materialLabel, String commandName) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT).trim() : "";
        switch (action) {
            case "save" -> {
                int added = regenerateManager.scanAndSaveCuboid(player);
                int total = regenerateManager.getCount();
                if (added > 0) {
                    this.send(player, "&#60FF8B✔ &#C1C1C1Dodano &f" + added + " &#C1C1C1nowych " + materialLabel + " (łącznie w bazie: &f" + total + "&7).");
                    player.playSound(player.getLocation(), Sound.BLOCK_BEEHIVE_ENTER, 0.8F, 1.2F);
                } else {
                    this.send(player, "&#FF5C5C✖ &#C1C1C1Nie znaleziono nowych &f" + materialLabel + " &#C1C1C1w sześcianie 50³ &7(załadowane chunki) lub wszystkie już zapisane. Baza: &f" + total);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.8F);
                }
            }
            case "hologram" -> {
                if (regenerateManager.setHologramLocation(player.getLocation())) {
                    this.send(player, "&#60FF8B✔ &#C1C1C1Ustawiono hologram regeneracji &f" + materialLabel + "&#C1C1C1 w Twojej lokalizacji.");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
                } else {
                    this.send(player, "&#FF5C5C✖ &#C1C1C1Nie udało się ustawić hologramu.");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.8F);
                }
            }
            default -> this.send(player, "&#FF5C5CUżyj: /generator " + commandName + " save &7| &f/generator " + commandName + " hologram");
        }
    }

    private void sendHelp(Player player, String label) {
        ColorUtils.sendUsage(player,
            "/" + label + " create <nazwa> <radius> <height> <blok> <czas> [permisja]",
            "/" + label + " addblock <generator> <blok> <szansa>",
            "/" + label + " clearblocks <generator>",
            "/" + label + " move <generator>",
            "/" + label + " clone <generator> <nazwa>",
            "/" + label + " renamehologram <generator> <treść>",
            "/" + label + " tp <generator>",
            "/" + label + " height <generator> <wysokość>",
            "/" + label + " radius <generator> <wielkość>",
            "/" + label + " settime <generator> <czas>",
            "/" + label + " setblock <generator> <blok>",
            "/" + label + " setpermission <generator> <permisja|none>",
            "/" + label + " remove <generator>",
            "/" + label + " list",
            "/" + label + " reload",
            "/" + label + " reset <generator|*>",
            "/" + label + " honeyregenerate save",
            "/" + label + " honeyregenerate hologram",
            "/" + label + " cyanpowderregenerate save",
            "/" + label + " cyanpowderregenerate hologram"
        );
    }

    private boolean validSize(int value) {
        return value >= 1 && value <= 30;
    }

    private Material parseMaterial(String input) {
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private int parseInt(String input, int def) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private double parseDouble(String input, double def) {
        try {
            return Double.parseDouble(input.trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private void ok(Player player, String messageKey) {
        this.send(player, this.message(messageKey));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.5F);
    }

    private void send(Player player, String message) {
        player.sendMessage(ColorUtils.colorizeComponent(this.message("prefix") + message));
    }

    private String message(String key) {
        return this.manager.getMessage(key);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return this.filter(List.of("create", "addblock", "clearblocks", "move", "clone", "renamehologram", "tp",
                "height", "radius", "settime", "setblock", "setpermission", "remove", "delete", "list", "reload", "reset",
                "honeyregenrate", "honeyregenerate", "cyanregenrate", "cyanregenerate", "cyanpowderregenerate"), args[0]);
        }

        List<String> ids = this.manager.getGeneratorIds();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "clearblocks", "move", "renamehologram", "tp", "delete", "remove", "clone", "addblock",
                     "height", "radius", "settime", "setblock", "setpermission", "setperm" -> this.filter(ids, args[1]);
                case "create" -> {
                    List<String> out = new ArrayList<>(ids);
                    out.addAll(List.of("diamenty", "zloto", "zelazo", "emeraldy"));
                    yield this.filter(out, args[1]);
                }
                case "honeyregenrate", "honeyregenerate", "cyanregenrate", "cyanregenerate", "cyanpowderregenerate" -> this.filter(List.of("save", "hologram"), args[1]);
                case "reset" -> {
                    List<String> options = new ArrayList<>(ids);
                    options.add("*");
                    yield this.filter(options, args[1]);
                }
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "addblock", "setblock" -> this.filter(this.materialSuggestions(), args[2]);
                case "height", "radius" -> this.filter(List.of("2", "3", "5"), args[2]);
                case "settime" -> this.filter(List.of("30s", "60s", "2m"), args[2]);
                case "create" -> this.filter(List.of("2", "3", "5"), args[2]);
                case "setpermission", "setperm" -> this.filter(List.of("none", "holly.generators.mine.gracz",
                    "holly.generators.mine.vip", "holly.generators.mine.svip"), args[2]);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 4) {
            return switch (sub) {
                case "create" -> this.filter(List.of("2", "3", "5"), args[3]);
                case "addblock" -> this.filter(List.of("10", "25", "50"), args[3]);
                default -> Collections.emptyList();
            };
        }
        if (args.length == 5 && sub.equals("create")) {
            return this.filter(this.materialSuggestions(), args[4]);
        }
        if (args.length == 6 && sub.equals("create")) {
            return this.filter(List.of("30s", "60s", "2m"), args[5]);
        }
        if (args.length == 7 && sub.equals("create")) {
            return this.filter(List.of("holly.generators.mine.gracz", "holly.generators.mine.vip",
                "holly.generators.mine.svip"), args[6]);
        }
        return Collections.emptyList();
    }

    private List<String> materialSuggestions() {
        return Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .map(Enum::name)
            .toList();
    }

    private List<String> filter(List<String> options, String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
