package dev.grzybalson.modules.zywioly;

import dev.grzybalson.HollyCore;
import dev.grzybalson.economy.HollyEconomy;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ZywiolyManager {

    private final HollyCore plugin;
    private final ZywiolyConfig config;
    private final ZywiolyMenus menus;
    private final ZywiolySignPrompt signs;
    private final Map<String, ZywiolyGame> games = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCreate> pending = new ConcurrentHashMap<>();

    public ZywiolyManager(HollyCore plugin) {
        this.plugin = plugin;
        this.config = new ZywiolyConfig(plugin);
        this.signs = new ZywiolySignPrompt(plugin);
        this.menus = new ZywiolyMenus(plugin, this, config);
        Bukkit.getPluginManager().registerEvents(menus, plugin);
        signs.register();
    }

    public void reload() {
        config.reload();
        menus.refreshMains();
    }

    public void shutdown() {
        for (ZywiolyGame game : new ArrayList<>(games.values())) {
            if (game.cancelIfOpen()) {
                refund(game.creatorUuid(), game.amount());
            }
        }
        games.clear();
        pending.clear();
        HandlerList.unregisterAll(menus);
    }

    public ZywiolyConfig config() {
        return config;
    }

    public ZywiolyMenus menus() {
        return menus;
    }

    public void openMain(Player player) {
        menus.openMain(player, 0);
    }

    public void beginCreate(Player player) {
        if (hasOpenGame(player.getUniqueId())) {
            send(player, config.message("already-in-game"));
            return;
        }
        String[] lines = {
            "",
            config.signLine(1, "------------"),
            config.signLine(2, "Wpisz kwotę"),
            config.signLine(3, "gry w żywioły")
        };
        signs.open(player, lines, raw -> {
            if (!player.isOnline()) {
                return;
            }
            Double parsed = MoneyAmountUtil.parse(raw);
            if (parsed == null || !MoneyAmountUtil.isPositiveFinite(parsed)) {
                send(player, config.message("invalid-amount"));
                return;
            }
            startPickForCreate(player, parsed);
        });
    }

    public void startPickForCreate(Player player, double amount) {
        String error = validateAmount(player, amount);
        if (error != null) {
            send(player, error);
            return;
        }
        if (hasOpenGame(player.getUniqueId())) {
            send(player, config.message("already-in-game"));
            return;
        }
        pending.put(player.getUniqueId(), new PendingCreate(amount, null, false));
        menus.openPick(player, amount, false, null);
    }

    public void startPickForJoin(Player player, String gameId) {
        ZywiolyGame game = games.get(gameId);
        if (game == null || !game.isOpen()) {
            send(player, config.message("unavailable"));
            menus.openMain(player, 0);
            return;
        }
        if (game.creatorUuid().equals(player.getUniqueId())) {
            send(player, config.message("own-game"));
            return;
        }
        String error = validateAmount(player, game.amount());
        if (error != null) {
            send(player, error);
            return;
        }
        pending.put(player.getUniqueId(), new PendingCreate(game.amount(), gameId, true));
        menus.openPick(player, game.amount(), true, gameId);
    }

    public void cancelPick(Player player) {
        pending.remove(player.getUniqueId());
        send(player, config.message("pick-cancelled"));
        menus.openMain(player, 0);
    }

    public void selectElement(Player player, ZywiolyElement element) {
        PendingCreate create = pending.remove(player.getUniqueId());
        if (create == null) {
            menus.openMain(player, 0);
            return;
        }
        if (create.joining) {
            joinGame(player, create.gameId, element);
            return;
        }
        createGame(player, create.amount, element);
    }

    public void cancelOwnGame(Player player, String gameId) {
        ZywiolyGame game = games.get(gameId);
        if (game == null || !game.creatorUuid().equals(player.getUniqueId())) {
            send(player, config.message("unavailable"));
            return;
        }
        if (!game.cancelIfOpen()) {
            send(player, config.message("unavailable"));
            return;
        }
        games.remove(gameId);
        refund(player.getUniqueId(), game.amount());
        send(player, config.message("cancelled").replace("%kwota%", format(game.amount())));
        menus.refreshMains();
        player.closeInventory();
    }

    List<ZywiolyGame> openGames() {
        List<ZywiolyGame> list = new ArrayList<>();
        for (ZywiolyGame game : games.values()) {
            if (game.isOpen()) {
                list.add(game);
            }
        }
        list.sort(Comparator.comparingDouble(ZywiolyGame::amount).reversed());
        return list;
    }

    private void createGame(Player player, double amount, ZywiolyElement element) {
        String error = validateAmount(player, amount);
        if (error != null) {
            send(player, error);
            return;
        }
        if (hasOpenGame(player.getUniqueId())) {
            send(player, config.message("already-in-game"));
            return;
        }
        HollyEconomy economy = plugin.getEconomy();
        if (economy == null) {
            send(player, config.message("no-economy"));
            return;
        }
        UUID uuid = player.getUniqueId();
        economy.removeBalance(uuid, amount).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                if (Boolean.TRUE.equals(ok)) {
                    economy.addBalance(uuid, amount);
                }
                return;
            }
            if (!Boolean.TRUE.equals(ok)) {
                send(player, config.message("not-enough"));
                return;
            }
            ZywiolyGame game = new ZywiolyGame(UUID.randomUUID().toString(), uuid, player.getName(), amount, element);
            games.put(game.id(), game);
            send(player, config.message("created-self").replace("%kwota%", format(amount)));
            broadcast(config.message("created-broadcast")
                .replace("%nick%", player.getName())
                .replace("%kwota%", format(amount)));
            menus.openMain(player, 0);
            menus.refreshMains();
        }));
    }

    private void joinGame(Player player, String gameId, ZywiolyElement element) {
        ZywiolyGame game = games.get(gameId);
        if (game == null || !game.isOpen()) {
            send(player, config.message("unavailable"));
            menus.openMain(player, 0);
            return;
        }
        HollyEconomy economy = plugin.getEconomy();
        if (economy == null) {
            send(player, config.message("no-economy"));
            return;
        }
        UUID uuid = player.getUniqueId();
        double amount = game.amount();
        economy.removeBalance(uuid, amount).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                if (Boolean.TRUE.equals(ok)) {
                    economy.addBalance(uuid, amount);
                }
                return;
            }
            if (!Boolean.TRUE.equals(ok)) {
                send(player, config.message("not-enough"));
                return;
            }
            ZywiolyGame current = games.get(gameId);
            if (current == null || !current.reserveOpponent(uuid, player.getName(), element)) {
                economy.addBalance(uuid, amount);
                send(player, config.message("unavailable"));
                menus.openMain(player, 0);
                menus.refreshMains();
                return;
            }
            games.remove(gameId);
            menus.refreshMains();
            startRoll(current);
        }));
    }

    private void startRoll(ZywiolyGame game) {
        Player creator = Bukkit.getPlayer(game.creatorUuid());
        Player opponent = Bukkit.getPlayer(game.opponentUuid());
        ZywiolyElement rolled = ZywiolyElement.values()[ThreadLocalRandom.current().nextInt(ZywiolyElement.values().length)];
        menus.startRoll(creator, opponent, game, rolled, () -> finish(game, rolled));
    }

    private void finish(ZywiolyGame game, ZywiolyElement rolled) {
        game.markDone();
        UUID creator = game.creatorUuid();
        UUID opponent = game.opponentUuid();
        double amount = game.amount();
        boolean creatorHit = rolled == game.creatorElement();
        boolean opponentHit = rolled == game.opponentElement();

        if (creatorHit && !opponentHit) {
            payout(creator, amount * 2.0D);
            notifyWinner(creator, opponent, game.creatorName(), game.opponentName(), amount);
            return;
        }
        if (opponentHit && !creatorHit) {
            payout(opponent, amount * 2.0D);
            notifyWinner(opponent, creator, game.opponentName(), game.creatorName(), amount);
            return;
        }
        refund(creator, amount);
        refund(opponent, amount);
        broadcast(config.message("draw")
            .replace("%nick%", game.creatorName() == null ? "?" : game.creatorName())
            .replace("%opponent%", game.opponentName() == null ? "?" : game.opponentName())
            .replace("%kwota%", format(amount)));
        closeRollInventories(creator, opponent);
    }

    private void notifyWinner(UUID winner, UUID loser, String winnerName, String loserName, double amount) {
        Player winPlayer = winner == null ? null : Bukkit.getPlayer(winner);
        Player losePlayer = loser == null ? null : Bukkit.getPlayer(loser);
        if (winPlayer != null) {
            send(winPlayer, config.message("you-won").replace("%payout%", format(amount * 2.0D)));
        }
        if (losePlayer != null) {
            send(losePlayer, config.message("you-lost").replace("%nick%", winnerName));
        }
        broadcast(config.message("winner-broadcast")
            .replace("%nick%", winnerName)
            .replace("%kwota%", format(amount))
            .replace("%loser%", loserName == null ? "?" : loserName));
        closeRollInventories(winner, loser);
    }

    private void closeRollInventories(UUID first, UUID second) {
        menus.endRoll(first, second);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            closeIfRoll(first);
            closeIfRoll(second);
        }, 40L);
    }

    private void closeIfRoll(UUID uuid) {
        if (uuid == null) {
            return;
        }
        menus.endRoll(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof ZywiolyHolder holder
            && holder.type() == ZywiolyHolder.Type.ROLL) {
            player.closeInventory();
        }
    }

    private void payout(UUID uuid, double amount) {
        HollyEconomy economy = plugin.getEconomy();
        if (economy != null && uuid != null && amount > 0.0D) {
            economy.addBalance(uuid, amount);
        }
    }

    private void refund(UUID uuid, double amount) {
        payout(uuid, amount);
    }

    private boolean hasOpenGame(UUID uuid) {
        for (ZywiolyGame game : games.values()) {
            if (game.isOpen() && game.creatorUuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    String validateAmount(Player player, double amount) {
        HollyEconomy economy = plugin.getEconomy();
        if (economy == null) {
            return config.message("no-economy");
        }
        if (!MoneyAmountUtil.isPositiveFinite(amount)) {
            return config.message("invalid-amount");
        }
        double min = config.minBet();
        if (amount + 1.0E-6D < min) {
            return config.message("min-bet").replace("%min%", format(min));
        }
        double balance = economy.getBalance(player.getUniqueId());
        double max = balance * (config.maxBalancePercent() / 100.0D);
        if (max + 1.0E-6D < min) {
            double need = min / (config.maxBalancePercent() / 100.0D);
            return config.message("need-more-for-min").replace("%need%", format(need));
        }
        if (amount - 1.0E-6D > max) {
            return config.message("max-percent")
                .replace("%percent%", format(config.maxBalancePercent()))
                .replace("%max%", format(max));
        }
        if (balance + 1.0E-6D < amount) {
            return config.message("not-enough");
        }
        return null;
    }

    static String format(double amount) {
        return MoneyAmountUtil.formatCompact(amount);
    }

    void send(Player player, String message) {
        if (player != null && message != null && !message.isBlank()) {
            player.sendMessage(ColorUtils.colorizeComponent(message));
        }
    }

    private void broadcast(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Bukkit.getServer().sendMessage(ColorUtils.colorizeComponent(message));
    }

    private record PendingCreate(double amount, String gameId, boolean joining) {
    }
}
