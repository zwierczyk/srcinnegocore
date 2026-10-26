package dev.grzybalson.economy;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.EconomyDataManager;
import dev.grzybalson.internal.HollyRuntime;
import dev.grzybalson.utils.MoneyAmountUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HollyEconomy {

    private final HollyCore plugin;
    private final EconomyDataManager dataManager;

    public HollyEconomy(HollyCore plugin) {
        HollyRuntime.require(plugin, "economy:init");
        this.plugin = plugin;
        this.dataManager = new EconomyDataManager(plugin);
    }

    public double getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    public double getBalance(UUID uuid) {
        HollyRuntime.require(plugin, "economy:balance");
        return dataManager.getCachedBalance(uuid);
    }

    public CompletableFuture<Double> getBalanceAsync(UUID uuid) {
        return dataManager.getBalance(uuid);
    }

    public CompletableFuture<Void> setBalance(UUID uuid, double amount) {
        HollyRuntime.require(plugin, "economy:set");
        return dataManager.setBalance(uuid, amount);
    }

    public CompletableFuture<Void> flushNow() {
        return dataManager.flushNow();
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, double amount) {
        HollyRuntime.require(plugin, "economy:add");
        return dataManager.addBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, double amount) {
        HollyRuntime.require(plugin, "economy:remove");
        return dataManager.removeBalance(uuid, amount);
    }

    public CompletableFuture<Boolean> hasBalance(UUID uuid, double amount) {
        return dataManager.hasBalance(uuid, amount);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return MoneyAmountUtil.isNonNegativeFinite(amount) && getBalance(player) >= amount;
    }

    public void loadPlayer(UUID uuid) {
        dataManager.getBalance(uuid);
    }

    public CompletableFuture<Void> touchPlayer(UUID uuid, String name) {
        return dataManager.touchPlayer(uuid, name);
    }

    public void invalidateCache(UUID uuid) {
        dataManager.invalidateCache(uuid);
    }

    public EconomyDataManager getDataManager() {
        return dataManager;
    }

    public String formatMoney(double amount) {
        String symbol = plugin.getConfigManager().getEconomyConfig().getCurrencySymbol();
        return MoneyAmountUtil.formatCompact(amount) + (symbol == null ? "$" : symbol);
    }
}
