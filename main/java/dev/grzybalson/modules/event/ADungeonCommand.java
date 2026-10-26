package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.event.dungeon.DungeonSessionManager;
import dev.grzybalson.modules.event.dungeon.DungeonWorldService;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ADungeonCommand implements CommandExecutor, TabCompleter {

    private final EventManager manager;
    private final DungeonSessionManager dungeon;

    public ADungeonCommand(HollyCore plugin, EventManager manager, DungeonSessionManager dungeon) {
        this.manager = manager;
        this.dungeon = dungeon;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give")) {
            return handleGive(sender, label, args);
        }
        if (sub.equals("setitemwejscie") || sub.equals("setticket") || sub.equals("setwejscie")) {
            return handleSetItemWejscie(sender, label);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTylko gracz!"));
            return true;
        }
        if (!player.hasPermission("holly.adungeon.admin")) {
            player.sendMessage(ColorUtils.noPermission(label, "holly.adungeon.admin"));
            return true;
        }
        return switch (sub) {
            case "setspawn" -> handleSetSpawn(player);
            case "setloc" -> handleSetLoc(player, args);
            case "add" -> handleAdd(player, args);
            case "addcam" -> handleAddCam(player, args);
            case "tutocam" -> handleTutoCam(player, args);
            case "skipfala" -> handleSkipFala(player);
            case "admin" -> handleAdmin(player, args);
            case "reload" -> {
                dungeon.reloadSettings();
                player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Przeladowano config dungeona."));
                yield true;
            }
            case "coinprize" -> handleCoinPrize(player, args);
            case "coinreward" -> handleCoinReward(player, args);
            case "reward" -> handleReward(player, args);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean handleAdmin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cUzycie: &e/adungeon admin <skipfala|end>"
            ));
            return true;
        }
        if (args[1].equalsIgnoreCase("skipfala")) {
            return handleSkipFala(player);
        }
        if (args[1].equalsIgnoreCase("end") || args[1].equalsIgnoreCase("stop")
            || args[1].equalsIgnoreCase("forceend")) {
            return handleAdminEnd(player, args);
        }
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#FF0000✘ &8Ξ &cUzycie: &e/adungeon admin <skipfala|end [nick]>"
        ));
        return true;
    }

    private boolean handleAdminEnd(Player player, String[] args) {
        var session = dungeon.getByPlayer(player.getUniqueId());
        if (session == null) {
            session = dungeon.getByWorld(player.getWorld().getName());
        }
        if (session == null && args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target != null) {
                session = dungeon.getByPlayer(target.getUniqueId());
            }
        }
        if (session == null) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cBrak aktywnej sesji (wejdz do swiata / podaj nick)."
            ));
            return true;
        }
        session.adminForceEnd();
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Zakonczono sesje dungeona (gracze odblokowani + spawn)."
        ));
        manager.playSuccess(player);
        return true;
    }

    private boolean handleSkipFala(Player player) {
        var session = dungeon.getByPlayer(player.getUniqueId());
        if (session == null) {
            session = dungeon.getByWorld(player.getWorld().getName());
        }
        if (session == null) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie jestes w aktywnej sesji dungeona!"));
            return true;
        }
        if (!session.adminSkipFala()) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cNie da sie teraz pominac (tutorial/prepare/upgrade/koniec)."
            ));
            return true;
        }
        player.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Pominieto fale/bossa."));
        manager.playSuccess(player);
        return true;
    }

    private boolean handleSetItemWejscie(CommandSender sender, String label) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTylko gracz!"));
            return true;
        }
        if (!player.hasPermission("holly.adungeon.admin")) {
            player.sendMessage(ColorUtils.noPermission(label, "holly.adungeon.admin"));
            return true;
        }
        var hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cTrzymaj wejsciowke w rece (z CMD/lore)."));
            return true;
        }
        if (!manager.setTicketFromHand(hand)) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie udalo sie zapisac wejsciowki."));
            return true;
        }
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono wejsciowke dungeona z itemu w rece (typ, CMD, lore, meta)."
        ));
        manager.playSuccess(player);
        return true;
    }

    private boolean handleGive(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("holly.adungeon.give") && !sender.hasPermission("holly.adungeon.admin")) {
            sender.sendMessage(ColorUtils.noPermission(label, "holly.adungeon.give"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cUzycie: &e/" + label + " give <nick> <ilosc>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cGracz &e" + args[1] + " &cmusi byc online!"));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cUzycie: &e/" + label + " give <nick> <ilosc>"));
            return true;
        }
        if (amount <= 0 || amount > 64) {
            sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cIlosc musi byc z zakresu &e1-64&c!"));
            return true;
        }
        manager.giveTickets(target, amount);
        if (sender instanceof Player player) {
            manager.send(player, "ticket-given", Map.of("AMOUNT", String.valueOf(amount), "PLAYER", target.getName()));
        } else {
            sender.sendMessage(ColorUtils.colorizeComponent(
                "&#00FF3B✔ &8Ξ &#4EFA76Nadano &f" + amount + " &awejsciowek graczowi &f" + target.getName() + "&a!"
            ));
        }
        return true;
    }

    private boolean handleCoinPrize(Player player, String[] args) {
        if (args.length < 2) {
            int current = dungeon.getSettings().getInt("coin-prize-amount", 0);
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FFD900⚠ &8Ξ &7Aktualna baza coinprize: &e" + current
                    + " &7(darmowe x1, tickety = koszt×3)"
            ));
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cUzycie: &e/adungeon coinprize <kwota>"
            ));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cKwota musi byc liczba! &e/adungeon coinprize <kwota>"
            ));
            return true;
        }
        if (amount < 0) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cKwota nie moze byc ujemna!"));
            return true;
        }
        if (!dungeon.setCoinPrizeAmount(amount)) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie udalo sie zapisac coinprize."));
            return true;
        }
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono coinprize: &e" + amount
                + " &a(wygrana = baza × mnoznik; 1 ticket = x3 → &e" + (amount * 3) + "&a)"
        ));
        manager.playSuccess(player);
        return true;
    }

    private boolean handleCoinReward(Player player, String[] args) {
        if (args.length < 2) {
            int current = dungeon.getSettings().getInt("coin-reward-amount", 0);
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FFD900⚠ &8Ξ &7Aktualna baza coinreward (za bossa): &e" + current
                    + " &7(darmowe x1, tickety = koszt×3)"
            ));
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cUzycie: &e/adungeon coinreward <kwota>"
            ));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cKwota musi byc liczba! &e/adungeon coinreward <kwota>"
            ));
            return true;
        }
        if (amount < 0) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cKwota nie moze byc ujemna!"));
            return true;
        }
        if (!dungeon.setCoinRewardAmount(amount)) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie udalo sie zapisac coinreward."));
            return true;
        }
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono coinreward: &e" + amount
                + " &a(za bossa = baza × mnoznik; 1 ticket = x3 → &e" + (amount * 3) + "&a)"
        ));
        manager.playSuccess(player);
        return true;
    }

    private boolean handleReward(Player player, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("boss")) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cUzycie: &e/adungeon reward boss <1|2|3> <free|premium>"
            ));
            return true;
        }
        int bossLevel = parseInt(args[2], -1);
        if (bossLevel < 1 || bossLevel > 3) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cPoziom bossa musi byc &e1&c, &e2 &club &e3&c!"
            ));
            return true;
        }
        String mode = args[3].toLowerCase(Locale.ROOT);
        boolean premium;
        if (mode.equals("free") || mode.equals("darmowe") || mode.equals("darmowy")) {
            premium = false;
        } else if (mode.equals("premium") || mode.equals("ticket") || mode.equals("tickety")) {
            premium = true;
        } else {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cUzycie: &e/adungeon reward boss " + bossLevel + " <free|premium>"
            ));
            return true;
        }
        int count = dungeon.getBossRewards(bossLevel, premium).size();
        new DungeonBossRewardEditGUI(manager.getPlugin(), player, manager, bossLevel, premium, true).open();
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Edycja puli &f" + (premium ? "premium" : "free")
                + " &abossa &e" + bossLevel + " &7(" + count + " itemow) &8— &fosobna od "
                + (premium ? "free" : "premium") + "&7."
        ));
        manager.playSuccess(player);
        return true;
    }

    private boolean handleSetSpawn(Player player) {
        if (!dungeon.requireTemplateWorld(player)) {
            return true;
        }
        dungeon.getLocations().setPlayerSpawn(player.getLocation());
        ok(player, "spawn graczy");
        return true;
    }

    private boolean handleSetLoc(Player player, String[] args) {
        if (!dungeon.requireTemplateWorld(player)) {
            return true;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("boss")) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cUzycie: &e/adungeon setloc boss <1-3>"));
            return true;
        }
        int level = parseInt(args[2], -1);
        if (level < 1 || level > 3) {
            player.sendMessage(ColorUtils.colorizeComponent(dungeon.getSettings().getString("messages.loc-invalid",
                "&#FF0000✘ &8Ξ &cNieprawidlowe argumenty.")));
            return true;
        }
        dungeon.getLocations().setBoss(level, player.getLocation());
        ok(player, "boss poziomu " + level);
        return true;
    }

    private boolean handleAdd(Player player, String[] args) {
        if (!dungeon.requireTemplateWorld(player)) {
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cUzycie: &e/adungeon add <1-3> <fala>"));
            return true;
        }
        int level = parseInt(args[1], -1);
        int wave = parseInt(args[2], -1);
        int maxWave = level == 3 ? 1 : 6;
        if (level < 1 || level > 3 || wave < 1 || wave > maxWave) {
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF0000✘ &8Ξ &cPoziom 1-2: fale 1-6 | Poziom 3: fala 1"
            ));
            return true;
        }
        dungeon.getLocations().setWave(level, wave, player.getLocation());
        ok(player, "fala " + wave + " poziomu " + level);
        return true;
    }

    private boolean handleAddCam(Player player, String[] args) {
        if (!dungeon.requireTemplateWorld(player)) {
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cUzycie: &e/adungeon addcam <1-3>"));
            return true;
        }
        int level = parseInt(args[1], -1);
        if (level < 1 || level > 3) {
            player.sendMessage(ColorUtils.colorizeComponent(dungeon.getSettings().getString("messages.loc-invalid",
                "&#FF0000✘ &8Ξ &cNieprawidlowe argumenty.")));
            return true;
        }
        dungeon.getLocations().setBossCamera(level, player.getLocation());
        ok(player, "kamera bossa poziomu " + level);
        return true;
    }

    private boolean handleTutoCam(Player player, String[] args) {
        if (!dungeon.requireTemplateWorld(player)) {
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("add")) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cUzycie: &e/adungeon tutocam add"));
            return true;
        }
        dungeon.getLocations().addTutorialCamera(player.getLocation());
        ok(player, "punkt kamery tutoriala");
        return true;
    }

    private void ok(Player player, String what) {
        player.sendMessage(ColorUtils.colorizeComponent(
            dungeon.getSettings().getString("messages.loc-set", "&#00FF3B✔ &8Ξ &#4EFA76Zapisano &f{WHAT}&a.")
                .replace("{WHAT}", what)
        ));
        manager.playSuccess(player);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cUzycie:"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " give <nick> <ilosc>"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " setitemwejscie"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " setspawn"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " setloc boss <1-3>"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " add <1-3> <fala>"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " addcam <1-3>"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " tutocam add"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " skipfala &7(lub admin skipfala)"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " admin end [nick] &7(force stop sesji)"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " coinprize <kwota>"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " coinreward <kwota>"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " reward boss <1|2|3> <free|premium>"));
        sender.sendMessage(ColorUtils.colorizeComponent(" &8• &e/" + label + " reload"));
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of(
                "give", "setitemwejscie", "setspawn", "setloc", "add", "addcam", "tutocam", "skipfala", "admin",
                "coinprize", "coinreward", "reward", "reload"
            ));
            return filter(base, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
                case "setloc" -> filter(List.of("boss"), args[1]);
                case "add", "addcam" -> filter(List.of("1", "2", "3"), args[1]);
                case "tutocam" -> filter(List.of("add"), args[1]);
                case "admin" -> filter(List.of("skipfala", "end"), args[1]);
                case "reward" -> filter(List.of("boss"), args[1]);
                case "coinprize", "coinreward" -> filter(List.of("1000", "2500", "5000", "10000"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("end") || args[1].equalsIgnoreCase("stop")
                || args[1].equalsIgnoreCase("forceend"))) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[2]);
            }
            if (args[0].equalsIgnoreCase("give")) {
                return filter(List.of("1", "5", "10", "16", "32", "64"), args[2]);
            }
            if (args[0].equalsIgnoreCase("setloc") && args[1].equalsIgnoreCase("boss")) {
                return filter(List.of("1", "2", "3"), args[2]);
            }
            if (args[0].equalsIgnoreCase("reward") && args[1].equalsIgnoreCase("boss")) {
                return filter(List.of("1", "2", "3"), args[2]);
            }
            if (args[0].equalsIgnoreCase("add")) {
                int level = parseInt(args[1], 1);
                if (level == 3) {
                    return filter(List.of("1"), args[2]);
                }
                return filter(List.of("1", "2", "3", "4", "5", "6"), args[2]);
            }
        }
        if (args.length == 4
            && args[0].equalsIgnoreCase("reward")
            && args[1].equalsIgnoreCase("boss")) {
            return filter(List.of("free", "premium"), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }
}
