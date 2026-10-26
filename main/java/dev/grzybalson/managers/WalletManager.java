package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.WalletDataManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WalletManager {

    private final HollyCore plugin;
    private final WalletDataManager dataManager;

    public WalletManager(HollyCore plugin) {
        this.plugin = plugin;
        this.dataManager = new WalletDataManager(plugin);
    }

    public CompletableFuture<Double> getBalance(UUID uuid) {
        return dataManager.getBalance(uuid);
    }

    public double getCachedBalance(UUID uuid) {
        return dataManager.getCachedBalance(uuid);
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail) {
        return addBalance(uuid, amount, source, actorName, action, actorUuid, detail, null);
    }

    public CompletableFuture<Boolean> addBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail, String playerName) {
        return dataManager.addBalance(uuid, amount, source, actorName, action, actorUuid, detail, playerName);
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail) {
        return removeBalance(uuid, amount, source, actorName, action, actorUuid, detail, null);
    }

    public CompletableFuture<Boolean> removeBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail, String playerName) {
        return dataManager.removeBalance(uuid, amount, source, actorName, action, actorUuid, detail, playerName);
    }

    public CompletableFuture<Double> setBalance(UUID uuid, double amount, String source, String actorName, String action, String actorUuid, String detail) {
        return dataManager.setBalance(uuid, amount, source, actorName, action, actorUuid, detail);
    }

    public CompletableFuture<Boolean> hasBalance(UUID uuid, double amount) {
        return dataManager.hasBalance(uuid, amount);
    }

    public CompletableFuture<List<WalletDataManager.WalletLog>> getLogs(UUID uuid) {
        return dataManager.getLogs(uuid);
    }

    public CompletableFuture<Double> getTotalRechargeValue() {
        return dataManager.getTotalRechargeValue();
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

    public WalletDataManager getDataManager() {
        return dataManager;
    }
}
