package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.ClanConfig;
import dev.grzybalson.database.ClanDataManager;
import dev.grzybalson.database.ClanDataManager.Clan;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;

public class ClanManager {

    private final HollyCore plugin;
    private final ClanDataManager dataManager;
    private final Map<UUID, String> pendingInvites;

    public ClanManager(HollyCore plugin) {
        this.plugin = plugin;
        this.dataManager = new ClanDataManager(plugin);
        this.pendingInvites = new ConcurrentHashMap<>();
        this.dataManager.disableAllPvp();
    }

    public CompletableFuture<ClanCreateResult> createClan(Player player, String tag) {
        UUID playerUuid = player.getUniqueId();
        String normalizedTag = tag == null ? "" : tag.trim().toUpperCase(Locale.ROOT);

        return getPlayerClan(playerUuid).thenCompose(existingClan -> {
            if (existingClan != null) {
                return CompletableFuture.completedFuture(ClanCreateResult.ALREADY_IN_CLAN);
            }

            return dataManager.clanExists(normalizedTag).thenCompose(exists -> {
                if (exists) {
                    return CompletableFuture.completedFuture(ClanCreateResult.TAG_EXISTS);
                }

                double cost = plugin.getConfigManager().getClanConfig().getCreationCost();
                if (cost <= 0.0D) {
                    return createClanRecord(player, normalizedTag, playerUuid, 0.0D, false);
                }

                return plugin.getEconomy().hasBalance(playerUuid, cost).thenCompose(hasBalance -> {
                    if (!hasBalance) {
                        return CompletableFuture.completedFuture(ClanCreateResult.NOT_ENOUGH_MONEY);
                    }

                    return plugin.getEconomy().removeBalance(playerUuid, cost).thenCompose(success -> {
                        if (!success) {
                            return CompletableFuture.completedFuture(ClanCreateResult.PAYMENT_FAILED);
                        }

                        return createClanRecord(player, normalizedTag, playerUuid, cost, true);
                    });
                });
            });
        });
    }



    private CompletableFuture<ClanCreateResult> createClanRecord(Player player, String normalizedTag, UUID playerUuid, double paidCost, boolean refundOnFailure) {
        return dataManager.createClan(normalizedTag, playerUuid)
            .handle((ignored, throwable) -> {
                if (throwable == null) {
                    return CompletableFuture.completedFuture(ClanCreateResult.SUCCESS);
                }

                Throwable cause = unwrapThrowable(throwable);
                plugin.getLogger().warning("Nie udało się utworzyć klanu '" + normalizedTag + "' dla gracza " + player.getName() + ": " + cause.getMessage());

                if (!refundOnFailure || paidCost <= 0.0D) {
                    return CompletableFuture.completedFuture(isDuplicateKeyError(cause)
                        ? ClanCreateResult.TAG_EXISTS
                        : ClanCreateResult.CREATE_FAILED);
                }

                return plugin.getEconomy().addBalance(playerUuid, paidCost)
                    .exceptionally(refundThrowable -> {
                        Throwable refundCause = unwrapThrowable(refundThrowable);
                        plugin.getLogger().warning("Nie udało się zwrócić pieniędzy za nieudane utworzenie klanu dla " + player.getName() + ": " + refundCause.getMessage());
                        return false;
                    })
                    .thenApply(refunded -> isDuplicateKeyError(cause)
                        ? ClanCreateResult.TAG_EXISTS
                        : ClanCreateResult.CREATE_FAILED);
            })
            .thenCompose(resultFuture -> resultFuture);
    }

    public CompletableFuture<Boolean> deleteClan(Player player) {
        UUID playerUuid = player.getUniqueId();

        return getPlayerClan(playerUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null || !clan.isLeader(playerUuid)) {
                    return CompletableFuture.completedFuture(false);
                }

                return dataManager.deleteClan(clanTag);
            });
        });
    }

    public CompletableFuture<Boolean> invitePlayer(Player inviter, Player target) {
        UUID inviterUuid = inviter.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        return getPlayerClan(inviterUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null || !clan.isLeaderOrDeputy(inviterUuid)) {
                    return CompletableFuture.completedFuture(false);
                }

                return getPlayerClan(targetUuid).thenApply(targetClan -> {
                    if (targetClan != null) {
                        return false;
                    }

                    pendingInvites.put(targetUuid, clanTag);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        pendingInvites.remove(targetUuid);
                    }, 20 * 60);

                    return true;
                });
            });
        });
    }

    public CompletableFuture<Boolean> acceptInvite(Player player) {
        UUID playerUuid = player.getUniqueId();
        String clanTag = pendingInvites.remove(playerUuid);

        if (clanTag == null) {
            return CompletableFuture.completedFuture(false);
        }

        return getPlayerClan(playerUuid).thenCompose(existingClan -> {
            if (existingClan != null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null) {
                    return CompletableFuture.completedFuture(false);
                }

                int maxMembers = plugin.getConfigManager().getClanConfig().getMaxMembers();
                if (clan.getMembers().size() >= maxMembers) {
                    return CompletableFuture.completedFuture(false);
                }

                return dataManager.addMember(clanTag, playerUuid).thenApply(v -> true);
            });
        });
    }

    public CompletableFuture<Boolean> leaveClan(Player player) {
        UUID playerUuid = player.getUniqueId();

        return getPlayerClan(playerUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null) {
                    return CompletableFuture.completedFuture(false);
                }

                if (clan.isLeader(playerUuid)) {
                    return CompletableFuture.completedFuture(false);
                }

                return dataManager.removeMember(clanTag, playerUuid).thenApply(v -> true);
            });
        });
    }

    public CompletableFuture<Boolean> kickMember(Player kicker, UUID targetUuid) {
        UUID kickerUuid = kicker.getUniqueId();

        return getPlayerClan(kickerUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null) {
                    return CompletableFuture.completedFuture(false);
                }
                return kickMemberInternal(kickerUuid, clanTag, clan, targetUuid);
            });
        });
    }

    public CompletableFuture<Boolean> kickMemberByName(Player kicker, String targetName) {
        UUID kickerUuid = kicker.getUniqueId();

        return getPlayerClan(kickerUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null) {
                    return CompletableFuture.completedFuture(false);
                }

                UUID targetUuid = findMemberUuidByName(clan, targetName);
                if (targetUuid == null) {
                    return CompletableFuture.completedFuture(false);
                }

                return kickMemberInternal(kickerUuid, clanTag, clan, targetUuid);
            });
        });
    }

    private CompletableFuture<Boolean> kickMemberInternal(UUID kickerUuid, String clanTag, ClanDataManager.Clan clan, UUID targetUuid) {
        if (!clan.isLeaderOrDeputy(kickerUuid)) {
            return CompletableFuture.completedFuture(false);
        }

        if (!clan.isMember(targetUuid) || clan.isLeader(targetUuid)) {
            return CompletableFuture.completedFuture(false);
        }

        if (clan.isDeputy(targetUuid) && !clan.isLeader(kickerUuid)) {
            return CompletableFuture.completedFuture(false);
        }

        return dataManager.removeMember(clanTag, targetUuid).thenApply(v -> true);
    }

    private UUID findMemberUuidByName(ClanDataManager.Clan clan, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return null;
        }
        String search = targetName.trim();
        for (UUID memberUuid : clan.getMembers()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(memberUuid);
            String memberName = offline.getName();
            if (memberName != null && memberName.equalsIgnoreCase(search)) {
                return memberUuid;
            }
        }
        Player online = Bukkit.getPlayer(search);
        if (online != null && clan.isMember(online.getUniqueId())) {
            return online.getUniqueId();
        }
        return null;
    }

    public CompletableFuture<Boolean> setDeputy(Player leader, UUID deputyUuid) {
        UUID leaderUuid = leader.getUniqueId();

        return getPlayerClan(leaderUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null || !clan.isLeader(leaderUuid)) {
                    return CompletableFuture.completedFuture(false);
                }

                if (!clan.isMember(deputyUuid)) {
                    return CompletableFuture.completedFuture(false);
                }

                return dataManager.setDeputy(clanTag, deputyUuid).thenApply(v -> true);
            });
        });
    }

    public CompletableFuture<Boolean> transferLeader(Player leader, UUID newLeaderUuid) {
        UUID leaderUuid = leader.getUniqueId();

        return getPlayerClan(leaderUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null || !clan.isLeader(leaderUuid)) {
                    return CompletableFuture.completedFuture(false);
                }

                if (!clan.isMember(newLeaderUuid) || leaderUuid.equals(newLeaderUuid)) {
                    return CompletableFuture.completedFuture(false);
                }

                return dataManager.setLeader(clanTag, newLeaderUuid, leaderUuid).thenApply(v -> true);
            });
        });
    }

    public CompletableFuture<Boolean> togglePvp(Player player) {
        UUID playerUuid = player.getUniqueId();

        return getPlayerClan(playerUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(null);
            }

            return dataManager.getClan(clanTag).thenCompose(clan -> {
                if (clan == null || !clan.isLeaderOrDeputy(playerUuid)) {
                    return CompletableFuture.completedFuture(null);
                }

                boolean newState = !clan.isPvpEnabled();
                return dataManager.setPvp(clanTag, newState).thenApply(v -> newState);
            });
        });
    }

    public CompletableFuture<Boolean> sendAnnouncement(Player sender, String message) {
        UUID senderUuid = sender.getUniqueId();

        return getPlayerClan(senderUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            return dataManager.getClan(clanTag).thenApply(clan -> {
                if (clan == null || !clan.isLeaderOrDeputy(senderUuid)) {
                    return false;
                }

                String formatted = plugin.getConfigManager().getClanConfig().getClanAnnouncementFormat()
                    .replace("{player}", sender.getName());
                Component announcement = dev.grzybalson.utils.ColorUtils.colorizeWithPlainPlaceholder(formatted, "{message}", message);

                for (UUID memberUuid : clan.getMembers()) {
                    Player member = Bukkit.getPlayer(memberUuid);
                    if (member != null && member.isOnline()) {
                        member.sendMessage(announcement);
                        plugin.getSoundManager().playClanAnnouncement(member);
                    }
                }

                return true;
            });
        });
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return unwrapThrowable(throwable.getCause());
        }
        return throwable;
    }

    private boolean isDuplicateKeyError(Throwable throwable) {
        String message = throwable.getMessage();
        return message != null && message.contains("E11000");
    }

    public enum ClanCreateResult {
        SUCCESS,
        ALREADY_IN_CLAN,
        TAG_EXISTS,
        NOT_ENOUGH_MONEY,
        PAYMENT_FAILED,
        CREATE_FAILED
    }

    public CompletableFuture<String> getPlayerClan(UUID playerUuid) {
        return dataManager.getPlayerClan(playerUuid);
    }

    public CompletableFuture<Clan> getClan(String tag) {
        return dataManager.getClan(tag);
    }

    public String getCachedPlayerClan(UUID playerUuid) {
        return dataManager.getCachedPlayerClan(playerUuid);
    }

    public boolean hasCachedPlayerClanLookup(UUID playerUuid) {
        return dataManager.hasCachedPlayerClanLookup(playerUuid);
    }

    public Clan getCachedClan(String tag) {
        return dataManager.getCachedClan(tag);
    }

    public void preloadPlayerClan(UUID playerUuid) {
        String cachedClan = getCachedPlayerClan(playerUuid);
        if (cachedClan != null) {
            if (getCachedClan(cachedClan) == null) {
                getClan(cachedClan).exceptionally(throwable -> null);
            }
            return;
        }
        if (hasCachedPlayerClanLookup(playerUuid)) {
            return;
        }

        getPlayerClan(playerUuid).thenAccept(clanTag -> {
            if (clanTag != null) {
                getClan(clanTag);
            }
        }).exceptionally(throwable -> null);
    }

    public CompletableFuture<List<Clan>> getTopClans(int limit) {
        return dataManager.getTopClans(limit);
    }

    public void handleKill(UUID killerUuid, UUID victimUuid) {
        getPlayerClan(killerUuid).thenAccept(killerTag -> getPlayerClan(victimUuid).thenAccept(victimTag -> {
            if (killerTag != null && killerTag.equalsIgnoreCase(victimTag)) {
                return;
            }

            CompletableFuture<Clan> killerFuture = killerTag != null
                ? getClan(killerTag)
                : CompletableFuture.completedFuture(null);
            CompletableFuture<Clan> victimFuture = victimTag != null
                ? getClan(victimTag)
                : CompletableFuture.completedFuture(null);

            killerFuture.thenAccept(killerClan -> victimFuture.thenAccept(victimClan -> {
                ClanConfig config = plugin.getConfigManager().getClanConfig();
                int victimElo = victimClan != null ? victimClan.getElo() : config.getStartingElo();
                int gain = (int) Math.round(victimElo * config.getPointsPercent() * config.getPointsMultiplier());
                if (gain < 1 && (killerClan != null || victimClan != null)) {
                    gain = Math.max(1, config.getEloGainOnKill());
                }
                if (killerClan != null) {
                    gain = applyRankingAmplifier(gain, killerClan);
                    if (plugin.getSzamanManager() != null) {
                        double turbo = plugin.getSzamanManager().getTurboRankingBonus(killerUuid);
                        if (turbo > 1.0D) {
                            gain = Math.max(gain, (int) Math.ceil(gain * config.getTurboPointsMulti()));
                        }
                    }
                    dataManager.recordKill(killerTag, killerClan.getElo() + gain);
                }
                if (victimClan != null) {
                    int loss = Math.max(0, (int) Math.round(gain * config.getMinusMultiplier()));
                    if (loss <= 0) {
                        loss = config.getEloLossOnDeath();
                    }
                    dataManager.recordDeath(victimTag, Math.max(0, victimClan.getElo() - loss));
                }
            }));
        }));
    }

    public int applyRankingAmplifier(int base, Clan clan) {
        double amplifier = getAmplifier(clan, "RANKING");
        if (amplifier <= 0.0D) {
            return base;
        }
        if (amplifier >= 1.0D) {
            return Math.max(base, (int) Math.ceil(base * amplifier));
        }
        return Math.max(base, (int) Math.ceil(base * (1.0D + (amplifier / 100.0D))));
    }

    /**
     * DROP_SHARD: szansa na odłamek z ItemStorage (SHARD) przy killu.
     * Bazowo 15% jak Hypestar + dodatkowa szansa z ulepszenia klanu.
     */
    public void tryDropShardOnKill(Player killer) {
        if (killer == null || !killer.isOnline()) {
            return;
        }
        if (plugin.getItemStorageManager() == null) {
            return;
        }

        boolean baseDrop = dev.grzybalson.anarchiagg.util.ValueUtil.getChance(15.0D);
        boolean upgradeDrop = false;
        int upgradeAmount = 1;

        String tag = getCachedPlayerClan(killer.getUniqueId());
        if (tag != null) {
            Clan clan = getCachedClan(tag);
            if (clan != null && getUpgradeLevel(clan, "DROP_SHARD") > 0) {
                double chance = getChance(clan, "DROP_SHARD");
                double amplifier = getAmplifier(clan, "DROP_SHARD");
                if (chance > 0.0D && dev.grzybalson.anarchiagg.util.ValueUtil.getChance(chance)) {
                    upgradeDrop = true;
                    upgradeAmount = Math.max(1, (int) Math.round(amplifier <= 0.0D ? 1.0D : amplifier));
                }
            }
        }

        if (!baseDrop && !upgradeDrop) {
            return;
        }

        int amount = 0;
        if (baseDrop) {
            amount += 1;
        }
        if (upgradeDrop) {
            amount += upgradeAmount;
        }
        giveItemStorageItem(killer, "SHARD", amount);
    }

    public void giveItemStorageItem(Player player, String key, int amount) {
        if (player == null || amount <= 0 || plugin.getItemStorageManager() == null) {
            return;
        }
        int remaining = amount;
        while (remaining > 0) {
            org.bukkit.inventory.ItemStack item = plugin.getItemStorageManager().createItem(key, remaining);
            if (item == null) {
                return;
            }
            int given = item.getAmount();
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(item);
            for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                if (drop != null && !drop.getType().isAir()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            remaining -= given;
        }
    }

    public int getUpgradeLevel(Clan clan, String type) {
        if (clan == null) {
            return 0;
        }
        return clan.getUpgradeLevel(type);
    }

    public double getAmplifier(Clan clan, String type) {
        ClanConfig.UpgradeStage stage = resolveOwnedStage(clan, type);
        return stage != null ? stage.getAmplifier() : 0.0D;
    }

    public double getChance(Clan clan, String type) {
        ClanConfig.UpgradeStage stage = resolveOwnedStage(clan, type);
        return stage != null ? stage.getChance() : 0.0D;
    }

    private ClanConfig.UpgradeStage resolveOwnedStage(Clan clan, String type) {
        if (clan == null || type == null || type.isBlank()) {
            return null;
        }
        int level = clan.getUpgradeLevel(type);
        if (level <= 0) {
            return null;
        }
        ClanConfig.UpgradeCategory category = plugin.getConfigManager().getClanConfig().findUpgradeCategory(type);
        if (category == null) {
            return null;
        }
        return category.findStage(level);
    }

    public CompletableFuture<Boolean> purchaseUpgrade(String clanTag, String type, int newLevel, int price) {
        return dataManager.purchaseUpgrade(clanTag, type, newLevel, price);
    }

    public CompletableFuture<Boolean> depositCoins(Player player, int amount) {
        if (player == null || amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        UUID playerUuid = player.getUniqueId();
        return getPlayerClan(playerUuid).thenCompose(clanTag -> {
            if (clanTag == null) {
                return CompletableFuture.completedFuture(false);
            }

            // Hypestar: wpłata fizycznych monet klanowych (ItemStorage CLAN_COIN).
            if (plugin.getItemStorageManager() != null && plugin.getItemStorageManager().getItem("CLAN_COIN") != null) {
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        future.complete(false);
                        return;
                    }
                    ItemStack template = plugin.getItemStorageManager().getItem("CLAN_COIN");
                    if (template == null || countSimilar(player, template) < amount) {
                        future.complete(false);
                        return;
                    }
                    removeSimilar(player, template, amount);
                    dataManager.addCoins(clanTag, amount)
                        .thenAccept(v -> future.complete(true))
                        .exceptionally(throwable -> {
                            giveItemStorageItem(player, "CLAN_COIN", amount);
                            future.complete(false);
                            return null;
                        });
                });
                return future;
            }

            // Fallback: ekonomia 1:1 gdy brak CLAN_COIN w ItemStorage.
            return plugin.getEconomy().hasBalance(playerUuid, amount).thenCompose(hasBalance -> {
                if (!hasBalance) {
                    return CompletableFuture.completedFuture(false);
                }
                return plugin.getEconomy().removeBalance(playerUuid, amount).thenCompose(removed -> {
                    if (!removed) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return dataManager.addCoins(clanTag, amount)
                        .thenApply(v -> true)
                        .exceptionally(throwable -> {
                            plugin.getEconomy().addBalance(playerUuid, amount);
                            return false;
                        });
                });
            });
        });
    }

    private int countSimilar(Player player, ItemStack template) {
        int total = 0;
        ItemStack expected = template.clone();
        expected.setAmount(1);
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            ItemStack check = stack.clone();
            check.setAmount(1);
            if (check.isSimilar(expected)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeSimilar(Player player, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack expected = template.clone();
        expected.setAmount(1);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            ItemStack check = stack.clone();
            check.setAmount(1);
            if (!check.isSimilar(expected)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            int left = stack.getAmount() - take;
            if (left <= 0) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(left);
            }
            remaining -= take;
        }
        player.updateInventory();
    }

    public boolean hasPendingInvite(UUID playerUuid) {
        return pendingInvites.containsKey(playerUuid);
    }

    public String getPendingInviteClan(UUID playerUuid) {
        return pendingInvites.get(playerUuid);
    }

    public ClanDataManager getDataManager() {
        return dataManager;
    }

    public void invalidateCache(UUID playerUuid) {
        dataManager.invalidateCache(playerUuid);
    }
}
