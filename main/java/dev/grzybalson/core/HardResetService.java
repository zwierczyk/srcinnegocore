package dev.grzybalson.core;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HardResetService {

    private final HollyCore plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HardResetService(HollyCore plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running.get();
    }

    public CompletableFuture<Integer> hardReset(String prefix) {
        String safePrefix = prefix == null ? "" : prefix.trim();
        if (safePrefix.isBlank() || !safePrefix.matches("[A-Za-z0-9_-]{1,64}")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Nieprawidlowy db-prefix"));
        }
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Hardreset juz trwa"));
        }

        String currentPrefix = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
        boolean currentMode = currentPrefix != null && currentPrefix.equalsIgnoreCase(safePrefix);

        return plugin.getMongoManager().wipePrefixCollections(safePrefix)
            .whenComplete((count, error) -> {
                try {
                    if (error == null && currentMode) {
                        Bukkit.getScheduler().runTask(plugin, this::resetLiveRuntimeState);
                    }
                } finally {
                    running.set(false);
                }
            });
    }

    private void resetLiveRuntimeState() {
        if (plugin.getEconomy() != null && plugin.getEconomy().getDataManager() != null) {
            plugin.getEconomy().getDataManager().clearAllCaches();
        }
        if (plugin.getEloManager() != null) {
            plugin.getEloManager().clearAllCaches();
        }
        if (plugin.getClanManager() != null && plugin.getClanManager().getDataManager() != null) {
            plugin.getClanManager().getDataManager().clearAllCaches();
        }
        if (plugin.getWalletManager() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getWalletManager().invalidateCache(player.getUniqueId());
            }
        }
        if (plugin.getMarketManager() != null) {
            try {
                plugin.getMarketManager().load();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[Core] Hardreset: nie udalo sie przeladowac rynku: " + throwable.getMessage());
            }
        }
        if (plugin.getDailyRewardManager() != null) {
            try {
                plugin.getDailyRewardManager().reload();
            } catch (Throwable ignored) {
            }
        }
        if (plugin.getKillstreakManager() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    plugin.getKillstreakManager().resetKillstreak(player.getUniqueId());
                } catch (Throwable ignored) {
                }
            }
        }
        if (plugin.getSzamanManager() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    plugin.getSzamanManager().unloadPlayer(player.getUniqueId());
                } catch (Throwable ignored) {
                }
            }
        }
        if (plugin.getSigilManager() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    plugin.getSigilManager().unloadPlayer(player.getUniqueId());
                } catch (Throwable ignored) {
                }
            }
        }
        if (plugin.getEnderChestManager() != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    plugin.getEnderChestManager().clearPlayerData(player.getUniqueId());
                } catch (Throwable ignored) {
                }
            }
        }
        if (plugin.getTopManager() != null) {
            plugin.getTopManager().requestRefresh();
        }
        if (plugin.getHollyPlaceholderExpansion() != null) {
            plugin.getHollyPlaceholderExpansion().invalidateAll();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            try {
                player.getInventory().setExtraContents(null);
            } catch (Throwable ignored) {
            }
            player.getEnderChest().clear();
            player.setLevel(0);
            player.setExp(0.0F);
            player.setTotalExperience(0);
            player.setHealth(Math.min(20.0D, player.getMaxHealth()));
            player.setFoodLevel(20);
            player.setSaturation(5.0F);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.sendMessage(ColorUtils.colorizeComponent(
                "&#FF5C5C✘ &8• &#FF5C5CWykonano hardreset bazy — Twoje dane tego trybu zostaly wyczyszczone."
            ));
            if (plugin.getInventorySyncManager() != null) {
                plugin.getInventorySyncManager().savePlayer(player, false);
            }
        }

        plugin.getLogger().warning("[Core] Hardreset: wyczyszczono runtime caches + ekwipunek online (ten prefix).");
    }

    public String normalizePrefix(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public boolean isCurrentPrefix(String prefix) {
        String current = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
        return current != null && current.equalsIgnoreCase(normalizePrefix(prefix));
    }
}
