package dev.grzybalson.modules.zmiankabattle.command;

import dev.grzybalson.utils.ColorUtils;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import dev.grzybalson.modules.zmiankabattle.ZmiankaBattle;
import dev.grzybalson.modules.zmiankabattle.invite.Invite;
import dev.grzybalson.modules.zmiankabattle.util.SoundResolver;
import dev.grzybalson.modules.zmiankabattle.util.Text;
import dev.grzybalson.modules.zmiankabattle.util.WorldGuardRegionGate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ZmiankiCommand implements CommandExecutor, TabCompleter {

    private final ZmiankaBattle plugin;

    public ZmiankiCommand(ZmiankaBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.error("Ta komenda jest tylko dla gracza."));
            return true;
        }

        FileConfiguration cfg = plugin.getConfig();

        if (args.length == 0) {
            sendUsage(player, cfg);
            return true;
        }

        // /zmianki bitwa zakceptuj <nick>
        if (args[0].equalsIgnoreCase("bitwa")) {
            return handleAccept(player, args, cfg);
        }

        // /zmianki global <kwota>
        if (args[0].equalsIgnoreCase("global")) {
            return handleGlobal(player, args, cfg);
        }

        // /zmianki wyzwij <nick> <kwota>
        if (args[0].equalsIgnoreCase("wyzwij")) {
            return handleDirectInvite(player, args, cfg, true);
        }

        // kompatybilność: /zmianki <nick> <kwota>
        return handleDirectInvite(player, args, cfg, false);
    }

    private boolean handleAccept(Player player, String[] args, FileConfiguration cfg) {
        if (args.length < 3) {
            ColorUtils.sendUsage(player, "/zmianki bitwa zaakceptuj <nick>");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (!action.equals("zakceptuj") && !action.equals("zaakceptuj") && !action.equals("accept")) {
            ColorUtils.sendUsage(player, "/zmianki bitwa zaakceptuj <nick>");
            return true;
        }

        if (!plugin.ensureEconomy()) {
            err(player, cfg.getString("messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę."), Map.of());
            return true;
        }

        Economy eco = plugin.getEconomy();
        if (eco == null) {
            err(player, cfg.getString("messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę."), Map.of());
            return true;
        }

        String inviterName = args[2];
        Player inviter = Bukkit.getPlayerExact(inviterName);
        if (inviter == null || !inviter.isOnline()) {
            err(player, cfg.getString("messages.errors.inviter-offline"), Map.of());
            return true;
        }


        UUID inviterId = inviter.getUniqueId();
        Invite inv = plugin.invites().peekInvite(player, inviterId);
        if (inv == null) {
            err(player, cfg.getString("messages.errors.no-invite"), Map.of());
            return true;
        }

        if (!player.getWorld().equals(inviter.getWorld())) {
            err(player, cfg.getString("messages.errors.different-world"), Map.of());
            return true;
        }

        WorldGuardRegionGate.CheckResult acceptRegion = plugin.regions().check(WorldGuardRegionGate.Action.ACCEPT, player, inviter);
        if (!acceptRegion.ok()) {
            handleRegionFailure(player, cfg, acceptRegion);
            return true;
        }

        if (plugin.battles().isInBattle(player.getUniqueId()) || plugin.battles().isInBattle(inviterId)) {
            err(player, cfg.getString("messages.errors.already-battle"), Map.of());
            return true;
        }

        double amount = inv.amount();
        if (!eco.has(player, amount)) {
            err(player, cfg.getString("messages.errors.no-money"), Map.of(
                    "{ilość}", Text.formatMoney(amount),
                    "{ilosc}", Text.formatMoney(amount)
            ));
            return true;
        }
        if (!eco.has(inviter, amount)) {
            err(player, cfg.getString("messages.errors.other-no-money"), Map.of());
            return true;
        }

        plugin.invites().takeInvite(player, inviterId);

        boolean started = plugin.battles().startBattle(inviter, player, amount);
        if (!started) {
            return true;
        }

        plugin.sounds().play(player, SoundResolver.Type.CLICK);
        plugin.sounds().play(inviter, SoundResolver.Type.CLICK);
        return true;
    }

    private boolean handleGlobal(Player player, String[] args, FileConfiguration cfg) {
        if (args.length < 2) {
            sendUsage(player, cfg);
            return true;
        }

        Double amount = parseAmount(args[1]);
        if (amount == null) {
            sendUsage(player, cfg);
            return true;
        }

        double min = cfg.getDouble("settings.min-bet", 100.0);
        if (amount < min) {
            err(player, cfg.getString("messages.errors.too-low"), Map.of("{min}", Text.formatMoney(min)));
            return true;
        }

        if (plugin.battles().isInBattle(player.getUniqueId())) {
            err(player, cfg.getString("messages.errors.already-battle"), Map.of());
            return true;
        }

        if (plugin.globals().hasOpenChallenge(player.getUniqueId())) {
            err(player, cfg.getString("messages.errors.global-already-open",
                    "&cMasz już otwarte globalne zmianki."), Map.of());
            return true;
        }

        if (plugin.invites().isBusy(player.getUniqueId())) {
            err(player, cfg.getString("messages.errors.invite-busy"), Map.of());
            return true;
        }

        WorldGuardRegionGate.CheckResult challengeRegion = plugin.regions().check(WorldGuardRegionGate.Action.CHALLENGE, player, null);
        if (!challengeRegion.ok()) {
            handleRegionFailure(player, cfg, challengeRegion);
            return true;
        }

        if (!plugin.ensureEconomy()) {
            err(player, cfg.getString("messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę."), Map.of());
            return true;
        }

        Economy eco = plugin.getEconomy();
        if (eco == null) {
            err(player, cfg.getString("messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę."), Map.of());
            return true;
        }

        if (!eco.has(player, amount)) {
            err(player, cfg.getString("messages.errors.no-money"), Map.of(
                    "{ilość}", Text.formatMoney(amount),
                    "{ilosc}", Text.formatMoney(amount)
            ));
            return true;
        }

        boolean ok = plugin.globals().openGlobal(player, amount);
        if (!ok) {
            err(player, cfg.getString("messages.errors.global-already-open",
                    "&cMasz już otwarte globalne zmianki."), Map.of());
            return true;
        }

        plugin.sounds().play(player, SoundResolver.Type.CLICK);
        return true;
    }

    private boolean handleDirectInvite(Player player, String[] args, FileConfiguration cfg, boolean prefixed) {
        int nameIndex = prefixed ? 1 : 0;
        int amountIndex = prefixed ? 2 : 1;

        if (args.length <= amountIndex) {
            sendUsage(player, cfg);
            return true;
        }

        if (!plugin.ensureEconomy()) {
            err(player, cfg.getString("messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę."), Map.of());
            return true;
        }

        Economy eco = plugin.getEconomy();
        if (eco == null) {
            err(player, cfg.getString("messages.errors.economy-unavailable",
                    "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę."), Map.of());
            return true;
        }

        String targetName = args[nameIndex];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            err(player, cfg.getString("messages.errors.player-offline"), Map.of());
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            err(player, cfg.getString("messages.errors.cannot-self"), Map.of());
            return true;
        }

        WorldGuardRegionGate.CheckResult challengeRegion = plugin.regions().check(WorldGuardRegionGate.Action.CHALLENGE, player, target);
        if (!challengeRegion.ok()) {
            handleRegionFailure(player, cfg, challengeRegion);
            return true;
        }

        Double amount = parseAmount(args[amountIndex]);
        if (amount == null) {
            sendUsage(player, cfg);
            return true;
        }

        double min = cfg.getDouble("settings.min-bet", 100.0);
        if (amount < min) {
            err(player, cfg.getString("messages.errors.too-low"), Map.of(
                    "{min}", Text.formatMoney(min)
            ));
            return true;
        }

        if (plugin.battles().isInBattle(player.getUniqueId()) || plugin.battles().isInBattle(target.getUniqueId())) {
            err(player, cfg.getString("messages.errors.already-battle"), Map.of());
            return true;
        }

        if (plugin.globals().hasOpenChallenge(player.getUniqueId())) {
            err(player, cfg.getString("messages.errors.global-already-open",
                    "&cMasz już otwarte globalne zmianki."), Map.of());
            return true;
        }
        if (plugin.globals().hasOpenChallenge(target.getUniqueId())) {
            err(player, cfg.getString("messages.errors.target-busy"), Map.of(
                    "{player}", target.getName(),
                    "{playername}", target.getName()
            ));
            return true;
        }

        if (plugin.invites().isBusy(player.getUniqueId())) {
            err(player, cfg.getString("messages.errors.invite-busy"), Map.of());
            return true;
        }
        if (plugin.invites().isBusy(target.getUniqueId())) {
            err(player, cfg.getString("messages.errors.target-busy"), Map.of(
                    "{player}", target.getName(),
                    "{playername}", target.getName()
            ));
            return true;
        }

        if (!eco.has(player, amount)) {
            err(player, cfg.getString("messages.errors.no-money"), Map.of(
                    "{ilość}", Text.formatMoney(amount),
                    "{ilosc}", Text.formatMoney(amount)
            ));
            return true;
        }

        boolean ok = plugin.invites().createInvite(player, target, amount);
        if (!ok) {
            if (plugin.invites().isBusy(player.getUniqueId())) {
                err(player, cfg.getString("messages.errors.invite-busy"), Map.of());
            } else {
                err(player, cfg.getString("messages.errors.target-busy"), Map.of(
                        "{player}", target.getName(),
                        "{playername}", target.getName()
                ));
            }
            return true;
        }
        plugin.sounds().play(player, SoundResolver.Type.CLICK);
        return true;
    }

    private void handleRegionFailure(Player player, FileConfiguration cfg, WorldGuardRegionGate.CheckResult result) {
        switch (result.reason()) {
            case WORLDGUARD_UNAVAILABLE -> err(player, cfg.getString("messages.errors.worldguard-unavailable",
                    "&cWorldGuard jest niedostępny. Nie można sprawdzić regionu."), Map.of());
            case REGION_NOT_FOUND -> err(player, cfg.getString("messages.errors.region-not-found",
                    "&#FF0000✘ &8Ξ &cRegion WorldGuard &f{region} &cnie istnieje w tym świecie."), Map.of(
                    "{region}", result.region()
            ));
            case OTHER_NOT_IN_REGION -> err(player, cfg.getString("messages.errors.other-not-in-region",
                    "&cGracz &f{player}&c musi być w regionie &f{region}&c."), Map.of(
                    "{player}", (result.otherPlayerName() == null ? "?" : result.otherPlayerName()),
                    "{playername}", (result.otherPlayerName() == null ? "?" : result.otherPlayerName()),
                    "{region}", result.region()
            ));
            case ACTOR_NOT_IN_REGION -> err(player, cfg.getString("messages.errors.region-not-allowed",
                    "&cMożesz użyć tej komendy tylko w regionie &f{region}&c."), Map.of(
                    "{region}", result.region()
            ));
            default -> {
            }
        }
    }

    private void sendUsage(Player player, FileConfiguration cfg) {
        ColorUtils.sendUsage(player,
            "/zmianki global <kwota>",
            "/zmianki wyzwij <nick> <kwota>",
            "/zmianki <nick> <kwota>"
        );
        player.sendMessage(ColorUtils.info(
            "Minimalna stawka: &f" + Text.formatMoney(cfg.getDouble("settings.min-bet", 100.0))
        ));
    }

    private Double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void err(Player p, String msg, Map<String, String> ph) {
        if (msg == null) return;
        p.sendMessage(Text.color(Text.apply(msg, ph)));
        plugin.sounds().play(p, SoundResolver.Type.ERROR);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> online = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());

        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            out.add("bitwa");
            out.add("global");
            out.add("wyzwij");
            out.addAll(online);
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("bitwa")) {
            return List.of("zakceptuj");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bitwa")) {
            return online;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("wyzwij")) {
            return online;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("global")) {
            return List.of("100", "500", "1000");
        }

        return List.of();
    }
}
