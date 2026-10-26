package dev.grzybalson.modules.coinflip;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.coach.CoachManager;
import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;
import dev.grzybalson.anarchiagg.util.TextUtil;
import dev.grzybalson.anarchiagg.util.notice.Notice;
import dev.grzybalson.config.modules.coinflip.CoinflipConfig;
import dev.grzybalson.economy.HollyEconomy;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class CoinflipManager {
    private final HollyCore plugin;
    private final CoinflipConfig config;
    private final CoinflipDatabase database;
    private final Map<String, GameSession> games = new ConcurrentHashMap<>();
    private final Map<String, GameSession> activeDuels = new ConcurrentHashMap<>();
    private CoinflipMenuManager menuManager;

    public CoinflipManager(HollyCore plugin, CoinflipConfig config, CoinflipDatabase database) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.recoverInterruptedGames();
        this.loadOpenGames();
    }

    public void setMenuManager(CoinflipMenuManager menuManager) {
        this.menuManager = menuManager;
    }

    public void createGame(Player player, CoinflipCurrency currency, double amount) {
        if (!this.config.isEnabled()) {
            this.send(player, this.config.getMessages().getDisabled());
            return;
        }
        String validation = this.validateAmount(currency, amount);
        if (validation != null) {
            this.send(player, validation);
            return;
        }
        if (currency == CoinflipCurrency.SHARDS) {
            this.createShardsGame(player, currency, amount);
            return;
        }

        HollyEconomy economy = this.plugin.getEconomy();
        if (economy == null) {
            this.send(player, this.config.getMessages().getClaimError());
            return;
        }

        UUID playerUuid = player.getUniqueId();
        economy.removeBalance(playerUuid, amount).thenAccept(withdrawn -> {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (!player.isOnline()) {
                    if (withdrawn) {
                        economy.addBalance(playerUuid, amount);
                    }
                    return;
                }
                if (!withdrawn) {
                    this.send(player, this.config.getMessages().getNotEnoughMoney());
                    return;
                }
                this.addGame(player, currency, amount);
            });
        });
    }

    public void joinGame(Player player, String id) {
        GameSession session = this.games.get(id);
        if (session == null || !session.isOpen()) {
            this.send(player, this.config.getMessages().getGameUnavailable());
            this.refreshMenus();
            return;
        }
        if (session.getCreatorUuid().equals(player.getUniqueId())) {
            this.send(player, this.config.getMessages().getOwnGame());
            return;
        }
        String playerName = player.getName();
        UUID playerUuid = player.getUniqueId();
        if (!session.reserveOpponent(playerUuid, playerName)) {
            this.send(player, this.config.getMessages().getGameUnavailable());
            this.refreshMenus();
            return;
        }
        if (session.getCurrency() == CoinflipCurrency.SHARDS) {
            this.joinShardsGame(player, session);
            return;
        }

        HollyEconomy economy = this.plugin.getEconomy();
        if (economy == null) {
            session.releaseOpponent();
            this.send(player, this.config.getMessages().getClaimError());
            this.refreshMenus();
            return;
        }

        economy.removeBalance(playerUuid, session.getAmount()).thenAccept(withdrawn -> {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                if (!player.isOnline()) {
                    if (withdrawn) {
                        economy.addBalance(playerUuid, session.getAmount());
                    }
                    session.releaseOpponent();
                    this.refreshMenus();
                    return;
                }
                if (!withdrawn) {
                    session.releaseOpponent();
                    this.send(player, this.config.getMessages().getNotEnoughMoney());
                    this.refreshMenus();
                    return;
                }
                this.startReservedGame(player, session);
            });
        });
    }

    public void cancelGame(Player player, String id) {
        GameSession session = this.games.get(id);
        if (session == null || !session.isOpen()) {
            this.send(player, this.config.getMessages().getGameUnavailable());
            this.refreshMenus();
            return;
        }
        if (!session.getCreatorUuid().equals(player.getUniqueId())) {
            this.send(player, this.config.getMessages().getGameUnavailable());
            this.refreshMenus();
            return;
        }
        this.games.remove(session.getId());
        this.database.cancelOpenGame(session).whenComplete((cancelled, exception) -> {
            if (exception != null) {
                if (session.isOpen()) {
                    this.games.putIfAbsent(session.getId(), session);
                }
                this.sync(() -> {
                    this.send(player, this.config.getMessages().getClaimError());
                    this.refreshMenus();
                    TextUtil.sendConsole("&eNie mozna usunac gry coinflip: " + exception.getMessage());
                });
                return;
            }
            if (!Boolean.TRUE.equals(cancelled)) {
                if (session.isOpen()) {
                    this.games.putIfAbsent(session.getId(), session);
                }
                this.sync(() -> {
                    this.send(player, this.config.getMessages().getGameUnavailable());
                    this.refreshMenus();
                });
                return;
            }
            this.refund(session.getCreatorUuid(), session.getCreatorName(), session.getCurrency(), session.getAmount());
            this.sync(() -> {
                if (player.isOnline()) {
                    this.send(player, this.replace(this.config.getMessages().getGameCancelled(), session.getCurrency(), session.getAmount(), null));
                    player.closeInventory();
                }
                this.refreshMenus();
            });
        });
        this.refreshMenus();
    }

    public void claim(Player player, CoinflipCurrency currency) {
        String playerName = player.getName();
        if (currency == CoinflipCurrency.MONEY) {
            this.database.claimReward(playerName, currency).whenComplete((amount, error) -> {
                if (error != null) {
                    this.sync(() -> this.send(player, this.config.getMessages().getClaimError()));
                    TextUtil.sendConsole("&eNie mozna odebrac nagrody coinflip: " + error.getMessage());
                    return;
                }
                if (amount == null || amount <= 0.0) {
                    this.sync(() -> this.send(player, this.config.getMessages().getClaimNothing()));
                    return;
                }
                this.refundMoney(player.getUniqueId(), amount);
                this.sync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    this.send(player, this.replace(this.config.getMessages().getClaimMoney(), currency, amount, null));
                    if (this.menuManager != null) {
                        this.menuManager.openClaim(player);
                    }
                });
            });
            return;
        }
        this.database.claimReward(playerName, currency).whenComplete((amount, error) -> {
            if (error != null) {
                this.sync(() -> this.send(player, this.config.getMessages().getClaimError()));
                TextUtil.sendConsole("&eNie mozna odebrac odlamkow coinflip: " + error.getMessage());
                return;
            }
            if (amount == null || amount <= 0.0) {
                this.sync(() -> this.send(player, this.config.getMessages().getClaimNothing()));
                return;
            }
            this.sync(() -> {
                if (!player.isOnline()) {
                    this.database.addReward(playerName, currency, amount);
                    return;
                }
                this.giveShards(player, (int) Math.floor(amount));
                this.send(player, this.replace(this.config.getMessages().getClaimShards(), currency, amount, null));
                if (this.menuManager != null) {
                    this.menuManager.openClaim(player);
                }
            });
        });
    }

    public List<GameSession> getGames(CoinflipFilter filter) {
        return this.games.values().stream()
                .filter(GameSession::isOpen)
                .filter(game -> filter.accepts(game.getCurrency()))
                .sorted(Comparator.comparingLong(GameSession::getCreatedAtMillis).reversed())
                .toList();
    }

    public boolean isOwnOpenGame(Player player, String id) {
        if (player == null || id == null) {
            return false;
        }
        GameSession session = this.games.get(id);
        return session != null && session.isOpen() && session.getCreatorUuid().equals(player.getUniqueId());
    }

    public String formatAmount(CoinflipCurrency currency, double amount) {
        if (currency == CoinflipCurrency.SHARDS) {
            return MoneyAmountUtil.formatCompact(amount);
        }
        if (this.plugin.getEconomy() != null) {
            return this.plugin.getEconomy().formatMoney(amount);
        }
        return MoneyAmountUtil.formatCompact(amount);
    }

    public String currencyName(CoinflipCurrency currency) {
        String configuredName = currency == CoinflipCurrency.MONEY
                ? this.config.getSettings().getMoneyDisplayName()
                : this.config.getSettings().getShardsDisplayName();
        if (configuredName == null || configuredName.isBlank()) {
            return currency.getDisplayName();
        }
        return configuredName;
    }

    public CoinflipConfig getConfig() {
        return this.config;
    }

    public HollyCore getPlugin() {
        return this.plugin;
    }

    public CoinflipDatabase getDatabase() {
        return this.database;
    }

    public void shutdown() {
        if (this.menuManager != null) {
            this.menuManager.shutdown();
        }
        for (GameSession session : new ArrayList<>(this.games.values())) {
            if (!session.isOpen()) continue;
            this.database.saveGame(session);
        }
        for (GameSession session : new ArrayList<>(this.activeDuels.values())) {
            this.refundInterruptedDuel(session);
        }
        this.games.clear();
        this.activeDuels.clear();
        this.database.shutdown();
    }

    private void loadOpenGames() {
        this.database.loadOpenGames().whenComplete((sessions, exception) -> {
            if (exception != null) {
                TextUtil.sendConsole("&eNie mozna wczytac aktywnych gier coinflip: " + exception.getMessage());
                return;
            }
            if (sessions != null) {
                for (GameSession session : sessions) {
                    this.games.put(session.getId(), session);
                }
                if (!this.games.isEmpty()) {
                    TextUtil.sendConsole("&aWczytano aktywne gry coinflip: " + this.games.size());
                }
            }
        });
    }

    private void recoverInterruptedGames() {
        this.database.loadInProgressGames().whenComplete((interruptedGames, exception) -> {
            if (exception != null) {
                TextUtil.sendConsole("&eNie mozna odzyskac przerwanych gier coinflip: " + exception.getMessage());
                return;
            }
            if (interruptedGames != null && !interruptedGames.isEmpty()) {
                for (CoinflipDatabase.StoredGame game : interruptedGames) {
                    this.refund(game.creatorUuid(), game.creatorName(), game.currency(), game.amount());
                    if (game.opponentUuid() != null) {
                        this.refund(game.opponentUuid(), game.opponentName(), game.currency(), game.amount());
                    }
                    this.database.closeGame(game.id(), "REFUNDED");
                }
                TextUtil.sendConsole("&eZwrocono przerwane gry coinflip po restarcie: " + interruptedGames.size());
            }
        });
    }

    private void createShardsGame(Player player, CoinflipCurrency currency, double amount) {
        CoachManager coachManager = this.plugin.getCoachManager();
        int shardAmount = (int) Math.floor(amount);
        if (coachManager == null || coachManager.countShards(player) < shardAmount) {
            this.send(player, this.config.getMessages().getNotEnoughShards());
            return;
        }
        if (!coachManager.withdrawShardsIfEnough(player, shardAmount)) {
            this.send(player, this.config.getMessages().getNotEnoughShards());
            return;
        }
        this.addGame(player, currency, shardAmount);
    }

    private void joinShardsGame(Player player, GameSession session) {
        CoachManager coachManager = this.plugin.getCoachManager();
        int shardAmount = (int) Math.floor(session.getAmount());
        if (coachManager == null || !coachManager.withdrawShardsIfEnough(player, shardAmount)) {
            session.releaseOpponent();
            this.send(player, this.config.getMessages().getNotEnoughShards());
            this.refreshMenus();
            return;
        }
        this.startReservedGame(player, session);
    }

    private void addGame(Player player, CoinflipCurrency currency, double amount) {
        String playerName = player.getName();
        GameSession session = new GameSession(UUID.randomUUID().toString(), player.getUniqueId(), playerName, currency, amount);
        this.database.saveGame(session).whenComplete((v, exception) -> {
            if (exception != null) {
                this.refund(player.getUniqueId(), playerName, currency, amount);
                this.sync(() -> {
                    TextUtil.sendConsole("&eNie mozna zapisac gry coinflip: " + exception.getMessage());
                    this.send(player, this.config.getMessages().getClaimError());
                });
                return;
            }
            this.sync(() -> {
                this.games.put(session.getId(), session);
                if (player.isOnline()) {
                    this.send(player, this.replace(this.config.getMessages().getGameCreated(currency), currency, amount, null));
                    this.sendSubtitle(player, this.replace(this.config.getMessages().getGameCreatedSubtitle(), currency, amount, null));
                }
                this.refreshMenus();
            });
        });
    }

    private void startReservedGame(Player joiner, GameSession session) {
        UUID joinerUuid = joiner.getUniqueId();
        String joinerName = joiner.getName();
        this.database.markGameInProgress(session, joinerUuid, joinerName).whenComplete((marked, exception) -> {
            if (exception != null || !Boolean.TRUE.equals(marked)) {
                session.releaseOpponent();
                this.refund(joinerUuid, joinerName, session.getCurrency(), session.getAmount());
                this.sync(() -> {
                    this.send(joiner, this.config.getMessages().getGameUnavailable());
                    if (exception != null) {
                        TextUtil.sendConsole("&eNie mozna zarezerwowac gry coinflip: " + exception.getMessage());
                    }
                    this.refreshMenus();
                });
                return;
            }
            this.sync(() -> this.finishReservedGameStart(joiner, session, true));
        });
    }

    private void finishReservedGameStart(Player joiner, GameSession session, boolean marked) {
        String joinerName = joiner.getName();
        UUID joinerUuid = joiner.getUniqueId();
        if (!marked) {
            session.releaseOpponent();
            this.refund(joinerUuid, joinerName, session.getCurrency(), session.getAmount());
            this.send(joiner, this.config.getMessages().getGameUnavailable());
            this.refreshMenus();
            return;
        }
        if (!session.startDuel()) {
            this.database.reopenGame(session.getId());
            session.reopen();
            this.refund(joinerUuid, joinerName, session.getCurrency(), session.getAmount());
            this.send(joiner, this.config.getMessages().getGameUnavailable());
            this.refreshMenus();
            return;
        }
        this.games.remove(session.getId());
        this.activeDuels.put(session.getId(), session);
        this.refreshMenus();
        Player creator = Bukkit.getPlayer(session.getCreatorUuid());
        if (creator == null || !creator.isOnline() || !joiner.isOnline()) {
            this.refund(joinerUuid, joinerName, session.getCurrency(), session.getAmount());
            this.storeReward(session.getCreatorUuid(), session.getCreatorName(), session.getCurrency(), session.getAmount());
            this.closeDuel(session, "REFUNDED");
            if (joiner.isOnline()) {
                this.send(joiner, this.config.getMessages().getGameUnavailable());
            }
            return;
        }
        this.send(joiner, this.replace(this.config.getMessages().getDuelStarted(), session.getCurrency(), session.getAmount(), session.getCreatorName()));
        this.startAnimation(session, creator, joiner);
    }

    private void startAnimation(GameSession session, Player creator, Player opponent) {
        if (this.menuManager == null) {
            this.finishDuel(session, creator, opponent);
            return;
        }
        Player winner = ThreadLocalRandom.current().nextBoolean() ? creator : opponent;
        Player loser = winner.getUniqueId().equals(creator.getUniqueId()) ? opponent : creator;
        double reward = session.getAmount() * 2.0;

        this.menuManager.startDuelScrollAnimation(creator, opponent, session, winner, () -> {
            Player liveWinner = Bukkit.getPlayer(winner.getUniqueId());
            Player liveLoser = Bukkit.getPlayer(loser.getUniqueId());
            if (liveWinner == null && liveLoser == null) {
                this.refundInterruptedDuel(session);
                return;
            }
            this.storeWinnerReward(winner, loser, session, reward);
        }, () -> this.refundInterruptedDuel(session));
    }

    void interruptDuelAnimation(String sessionId) {
        if (this.menuManager != null) {
            this.menuManager.interruptDuelScroll(sessionId);
        }
    }

    private void finishDuel(GameSession session, Player creator, Player opponent) {
        Player winner = ThreadLocalRandom.current().nextBoolean() ? creator : opponent;
        Player loser = winner.getUniqueId().equals(creator.getUniqueId()) ? opponent : creator;
        double reward = session.getAmount() * 2.0;
        this.storeWinnerReward(winner, loser, session, reward);
    }

    private void refundInterruptedDuel(GameSession session) {
        if (!this.activeDuels.containsKey(session.getId())) {
            return;
        }
        this.refund(session.getCreatorUuid(), session.getCreatorName(), session.getCurrency(), session.getAmount());
        if (session.getOpponentUuid() != null) {
            this.refund(session.getOpponentUuid(), session.getOpponentName(), session.getCurrency(), session.getAmount());
        }
        this.closeDuel(session, "REFUNDED");
        this.sync(() -> this.notifyDuelRefunded(session));
    }

    private void notifyDuelRefunded(GameSession session) {
        String message = this.replace(this.config.getMessages().getDuelRefunded(), session.getCurrency(), session.getAmount(), session.getCreatorName());
        Player creator = Bukkit.getPlayer(session.getCreatorUuid());
        if (creator != null && creator.isOnline()) {
            creator.closeInventory();
            this.send(creator, message);
        }
        if (session.getOpponentUuid() != null) {
            Player opponent = Bukkit.getPlayer(session.getOpponentUuid());
            if (opponent != null && opponent.isOnline()) {
                opponent.closeInventory();
                this.send(opponent, message);
            }
        }
    }

    private void closeDuel(GameSession session, String status) {
        this.activeDuels.remove(session.getId());
        this.database.closeGame(session.getId(), status);
    }

    private void refund(UUID uuid, String playerName, CoinflipCurrency currency, double amount) {
        if (currency == CoinflipCurrency.MONEY) {
            this.refundMoney(uuid, amount);
            return;
        }
        this.database.addReward(playerName, currency, amount);
    }

    private void refundMoney(UUID uuid, double amount) {
        HollyEconomy economy = this.plugin.getEconomy();
        if (economy != null && uuid != null) {
            economy.addBalance(uuid, amount);
        }
    }

    private void storeWinnerReward(Player winner, Player loser, GameSession session, double reward) {
        String winnerName = winner.getName();
        String loserName = loser.getName();
        if (session.getCurrency() == CoinflipCurrency.MONEY) {
            this.refundMoney(winner.getUniqueId(), reward);
            this.closeDuel(session, "FINISHED");
            this.sync(() -> this.finishRewardMessages(winner, loser, loserName, winnerName, session, reward, this.config.getMessages().getMoneyRewardPaid()));
            return;
        }

        double shards = Math.floor(reward);
        this.database.addReward(winnerName, CoinflipCurrency.SHARDS, shards).whenComplete((ignored, error) -> this.sync(() -> {
            if (error != null) {
                TextUtil.sendConsole("&eNie mozna zapisac odlamkow coinflip: " + error.getMessage());
                this.send(winner, this.config.getMessages().getClaimError());
                return;
            }
            this.closeDuel(session, "FINISHED");
            this.finishRewardMessages(winner, loser, loserName, winnerName, session, reward, this.config.getMessages().getShardsRewardSaved());
        }));
    }

    private void finishRewardMessages(Player winner, Player loser, String loserName, String winnerName, GameSession session, double reward, String savedMessage) {
        if (winner.isOnline()) {
            this.send(winner, this.replace(this.config.getMessages().getWinner(), session.getCurrency(), reward, loserName));
            this.send(winner, savedMessage);
            winner.closeInventory();
        }
        if (loser.isOnline()) {
            this.send(loser, this.replace(this.config.getMessages().getLoser(), session.getCurrency(), session.getAmount(), winnerName));
            loser.closeInventory();
        }
        this.broadcastWin(winnerName, loserName, session, reward);
    }

    private void broadcastWin(String winnerName, String loserName, GameSession session, double reward) {
        CoinflipConfig.Messages messages = this.config.getMessages();
        if (messages == null || !messages.isBroadcastEnabled()) {
            return;
        }
        String template = messages.getBroadcast(session.getCurrency());
        if (template == null || template.isBlank()) {
            return;
        }
        String text = this.replace(template, session.getCurrency(), reward, loserName)
            .replace("%winner%", winnerName == null ? "" : winnerName)
            .replace("%loser%", loserName == null ? "" : loserName)
            .replace("%player%", winnerName == null ? "" : winnerName)
            .replace("%bet%", this.formatAmount(session.getCurrency(), session.getAmount()));
        Bukkit.broadcast(ColorUtils.colorizeComponent(text));
    }

    private void storeReward(UUID uuid, String playerName, CoinflipCurrency currency, double amount) {
        if (currency == CoinflipCurrency.MONEY) {
            this.refundMoney(uuid, amount);
            return;
        }
        this.database.addReward(playerName, currency, amount);
    }

    private void giveShards(Player player, int amount) {
        CoachManager coachManager = this.plugin.getCoachManager();
        if (coachManager == null || amount <= 0) {
            return;
        }
        int stackAmount;
        for (int left = amount; left > 0; left -= stackAmount) {
            stackAmount = Math.min(64, left);
            ItemStack item = coachManager.createShardItem(stackAmount);
            HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                ItemCompatibilityUtil.dropItemNaturally(player, leftover);
            }
        }
    }

    private String validateAmount(CoinflipCurrency currency, double amount) {
        double max;
        if (amount <= 0.0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return this.config.getMessages().getInvalidAmount();
        }
        if (currency.isWholeNumber() && amount != Math.floor(amount)) {
            return this.config.getMessages().getOnlyWholeShards();
        }
        double min = currency == CoinflipCurrency.MONEY ? this.config.getSettings().getMinMoneyBet() : (double) this.config.getSettings().getMinShardsBet();
        max = currency == CoinflipCurrency.MONEY ? this.config.getSettings().getMaxMoneyBet() : (double) this.config.getSettings().getMaxShardsBet();
        if (amount < min) {
            return this.config.getMessages().getAmountTooLow().replace("%amount%", this.formatAmount(currency, min));
        }
        if (amount > max) {
            return this.config.getMessages().getAmountTooHigh().replace("%amount%", this.formatAmount(currency, max));
        }
        return null;
    }

    private void playAnimationSound(Player player) {
        String sound = this.normalizeSound(this.config.getSettings().getAnimationSound());
        if (sound != null && !sound.isBlank()) {
            player.playSound(player.getLocation(), sound, this.config.getSettings().getAnimationVolume(), this.config.getSettings().getAnimationPitch());
        }
    }

    private String normalizeSound(String sound) {
        if (sound == null || sound.isBlank()) {
            return "";
        }
        String value = sound.trim();
        if (value.equalsIgnoreCase("BLOCK_NOTE_BLOCK_PLING") || value.equalsIgnoreCase("BLOCK.NOTE_BLOCK.PLING")) {
            return "block.note_block.pling";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private void refreshMenus() {
        if (this.menuManager != null) {
            this.menuManager.refreshMainViews();
        }
    }

    private void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(this.plugin, runnable);
    }

    private void send(Player player, String message) {
        if (player != null && player.isOnline() && message != null && !message.isBlank()) {
            Notice.sendP(player, this.config.getMessages().getType(), message);
        }
    }

    private void sendSubtitle(Player player, String subtitle) {
        if (player != null && player.isOnline() && subtitle != null && !subtitle.isBlank()) {
            TextUtil.sendTitle(player, "", subtitle, 10, 40, 10);
        }
    }

    private String replace(String text, CoinflipCurrency currency, double amount, String opponent) {
        String output = text == null ? "" : text;
        String name = this.currencyName(currency);
        String formattedAmt = this.formatAmount(currency, amount);
        String symbol = currency == CoinflipCurrency.MONEY ? "$" : "odłamków";
        String amtWithSymbol = currency == CoinflipCurrency.MONEY ? (formattedAmt + "$") : (formattedAmt + " odłamków");

        output = output.replace("%currency%", name);
        output = output.replace("%currency_name%", name);
        output = output.replace("%currency_symbol%", symbol);
        output = output.replace("%amount%", formattedAmt);
        output = output.replace("%amount_formatted%", amtWithSymbol);
        if (opponent != null) {
            output = output.replace("%opponent%", opponent).replace("%creator%", opponent);
        }
        return output;
    }
}
