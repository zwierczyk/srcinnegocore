package dev.grzybalson.modules.achievement;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.modules.achievement.AchievementConfig;
import dev.grzybalson.network.NatsService;
import dev.grzybalson.utils.ItemSerializer;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AchievementManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final ConcurrentHashMap<UUID, PlayerAchievementData> cache;

    public AchievementManager(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection("achievements");
        this.cache = new ConcurrentHashMap<>();
    }

    public CompletableFuture<PlayerAchievementData> loadPlayer(UUID uuid) {
        if (cache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(cache.get(uuid));
        }

        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("uuid", uuid.toString())).first()
        ).thenApply(doc -> {
            PlayerAchievementData data;
            if (doc == null) {
                data = new PlayerAchievementData(uuid);
            } else {
                data = PlayerAchievementData.fromDocument(doc);
            }
            cache.put(uuid, data);
            return data;
        });
    }

    public CompletableFuture<Void> savePlayer(UUID uuid) {
        PlayerAchievementData data = cache.get(uuid);
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                data.toDocument(),
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid).whenComplete((res, err) -> cache.remove(uuid));
    }

    public PlayerAchievementData getCachedData(UUID uuid) {
        return cache.get(uuid);
    }

    public void updatePlayerName(UUID uuid, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        PlayerAchievementData data = cache.get(uuid);
        if (data != null) {
            data.setName(name);
        }
    }

    public CompletableFuture<Void> syncPlayerStatistics(UUID uuid, String name, int kills, int deaths) {
        return loadPlayer(uuid).thenCompose(data -> {
            boolean changed = false;
            String sanitizedName = name != null ? name : "";
            if (!sanitizedName.isBlank() && !sanitizedName.equals(data.getName())) {
                data.setName(sanitizedName);
                changed = true;
            }

            int sanitizedKills = Math.max(0, kills);
            if (sanitizedKills != data.getKills()) {
                data.setKills(sanitizedKills);
                changed = true;
            }

            int sanitizedDeaths = Math.max(0, deaths);
            if (sanitizedDeaths != data.getDeaths()) {
                data.setDeaths(sanitizedDeaths);
                changed = true;
            }

            if (!changed) {
                return CompletableFuture.completedFuture(null);
            }
            return savePlayer(uuid).thenRun(() -> {
                if (plugin.getTopManager() != null) {
                    plugin.getTopManager().rememberPlayerName(uuid, sanitizedName);
                    plugin.getTopManager().invalidatePlayer(uuid);
                    plugin.getTopManager().requestRefresh();
                }
                if (plugin.getHollyPlaceholderExpansion() != null) {
                    plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                }
            });
        });
    }

    public CompletableFuture<Void> syncPlayerStatistics(Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        UUID uuid = player.getUniqueId();
        return loadPlayer(uuid).thenCompose(data -> {
            String name = player.getName();
            boolean nameChanged = name != null && !name.isBlank() && !name.equals(data.getName());
            if (nameChanged) {
                data.setName(name);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    try {
                        player.setStatistic(Statistic.PLAYER_KILLS, Math.max(0, data.getKills()));
                        player.setStatistic(Statistic.DEATHS, Math.max(0, data.getDeaths()));
                    } catch (Throwable ignored) {}
                }
            });

            CompletableFuture<Void> saveFuture = nameChanged ? savePlayer(uuid) : CompletableFuture.completedFuture(null);
            return saveFuture.thenRun(() -> {
                if (plugin.getTopManager() != null) {
                    if (name != null && !name.isBlank()) {
                        plugin.getTopManager().rememberPlayerName(uuid, name);
                    }
                    plugin.getTopManager().invalidatePlayer(uuid);
                    plugin.getTopManager().requestRefresh();
                }
                if (plugin.getHollyPlaceholderExpansion() != null) {
                    plugin.getHollyPlaceholderExpansion().invalidatePlayer(uuid);
                }
            });
        });
    }

    public void incrementKills(UUID uuid) {
        PlayerAchievementData data = cache.get(uuid);
        if (data != null) {
            data.setKills(data.getKills() + 1);
        }
    }

    public void incrementDeaths(UUID uuid) {
        PlayerAchievementData data = cache.get(uuid);
        if (data != null) {
            data.setDeaths(data.getDeaths() + 1);
        }
    }

    public void incrementBlocksMined(UUID uuid) {
        PlayerAchievementData data = cache.get(uuid);
        if (data != null) {
            data.setBlocksMined(data.getBlocksMined() + 1);
            if (data.getBlocksMined() % 20 == 0) {
                savePlayer(uuid);
            }
        }
    }

    public void incrementCasesOpened(UUID uuid) {
        PlayerAchievementData data = cache.get(uuid);
        if (data != null) {
            data.setCasesOpened(data.getCasesOpened() + 1);
            savePlayer(uuid);
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().requestRefresh();
            }
        }
    }

    public void incrementCasesOpened(Player player) {
        if (player != null) {
            incrementCasesOpened(player.getUniqueId());
        }
    }

    public void incrementTotemPops(UUID uuid) {
        PlayerAchievementData data = cache.get(uuid);
        if (data != null) {
            data.setTotemPops(data.getTotemPops() + 1);
            savePlayer(uuid);
            if (plugin.getTopManager() != null) {
                plugin.getTopManager().requestRefresh();
            }
        }
    }

    public void incrementTotemPops(Player player) {
        if (player != null) {
            incrementTotemPops(player.getUniqueId());
        }
    }

    public int getProgress(UUID uuid, AchievementType type) {
        PlayerAchievementData data = cache.get(uuid);
        if (data == null) return 0;

        return switch (type) {
            case KILLS -> data.getKills();
            case DEATHS -> data.getDeaths();
            case BLOCKS_MINED -> data.getBlocksMined();
            case PLAYTIME -> {
                var statsData = plugin.getStatsDataManager() != null
                    ? plugin.getStatsDataManager().getCachedStats(uuid)
                    : null;
                if (statsData != null) {
                    yield (int) Math.min(Integer.MAX_VALUE, statsData.getTotalPlaytime());
                }
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    yield player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) * 50;
                }
                yield 0;
            }
            case CRATES -> data.getCasesOpened();
            case POINTS -> plugin.getEloManager() != null ? plugin.getEloManager().getElo(uuid) : 0;
            case EARNED_POINTS -> data.getEarnedPoints();
        };
    }

    public boolean isCompleted(UUID uuid, AchievementType type, int tier) {
        AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();
        Map<Integer, Integer> tiers = config.getTiersForType(type);
        Integer required = tiers.get(tier);
        if (required == null) return false;

        int progress = getProgress(uuid, type);
        return progress >= required;
    }

    public int getClaimedLevel(UUID uuid, AchievementType type) {
        PlayerAchievementData data = cache.get(uuid);
        if (data == null) return 0;
        return data.getClaimedLevels().getOrDefault(type.name(), 0);
    }

    public boolean isClaimed(UUID uuid, AchievementType type, int tier) {
        return getClaimedLevel(uuid, type) >= tier;
    }

    public boolean canClaimTier(UUID uuid, AchievementType type, int tier) {
        return tier == getClaimedLevel(uuid, type) + 1;
    }

    public void incrementEarnedPoints(UUID uuid, int amount) {
        if (amount <= 0) return;
        PlayerAchievementData data = cache.get(uuid);
        if (data != null) {
            data.setEarnedPoints(data.getEarnedPoints() + amount);
            savePlayer(uuid);
        }
    }

    public void applyRemoteClaim(UUID uuid, AchievementType type, int tier) {
        PlayerAchievementData data = cache.get(uuid);
        if (data == null) return;
        int current = data.getClaimedLevels().getOrDefault(type.name(), 0);
        if (tier > current) {
            data.getClaimedLevels().put(type.name(), tier);
        }
    }

    public void publishClaimSync(UUID uuid, AchievementType type, int tier) {
        if (plugin.getNatsService() == null || !plugin.getNatsService().isConnected()) {
            return;
        }
        plugin.getNatsService().publish(NatsService.ACHIEVEMENT_CLAIM, new Document()
            .append("uuid", uuid.toString())
            .append("type", type.name())
            .append("tier", tier)
            .append("port", plugin.getServer().getPort()));
    }

    public boolean claimAchievement(Player player, AchievementType type, int tier) {
        UUID uuid = player.getUniqueId();

        if (!canClaimTier(uuid, type, tier)) return false;
        if (!isCompleted(uuid, type, tier)) return false;

        AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();
        String rewardKey = type.name() + "_" + tier;
        String rewardData = config.getReward(rewardKey);

        if (rewardData == null || rewardData.isEmpty()) return false;

        ItemStack[] rewards = ItemSerializer.deserializeItems(rewardData);
        if (rewards == null || rewards.length == 0) return false;

        for (ItemStack reward : rewards) {
            if (reward != null && !reward.getType().isAir()) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(reward.clone());
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }

        PlayerAchievementData data = cache.get(uuid);
        if (data != null) {
            data.getClaimedLevels().put(type.name(), tier);
        }

        savePlayer(uuid);
        publishClaimSync(uuid, type, tier);
        return true;
    }

    public ItemStack[] getRewardItems(AchievementType type, int tier) {
        AchievementConfig config = plugin.getModuleConfigManager().getAchievementConfig();
        String rewardKey = type.name() + "_" + tier;
        String rewardData = config.getReward(rewardKey);
        if (rewardData == null || rewardData.isEmpty()) return null;
        return ItemSerializer.deserializeItems(rewardData);
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayer(uuid);
        }
    }

    public CompletableFuture<Void> resetStats(UUID uuid, boolean resetKills, boolean resetDeaths, boolean resetBlocksMined, boolean clearClaimed) {
        return loadPlayer(uuid).thenCompose(data -> {
            if (resetKills) {
                data.setKills(0);
            }
            if (resetDeaths) {
                data.setDeaths(0);
            }
            if (resetBlocksMined) {
                data.setBlocksMined(0);
            }
            if (clearClaimed) {
                data.getClaimedLevels().clear();
            }
            return savePlayer(uuid);
        });
    }

    public void resetCachedCombatStats() {
        for (PlayerAchievementData data : cache.values()) {
            data.setKills(0);
            data.setDeaths(0);
        }
    }

    public static class PlayerAchievementData {
        private final UUID uuid;
        private String name;
        private int kills;
        private int deaths;
        private int blocksMined;
        private int casesOpened;
        private int totemPops;
        private int earnedPoints;
        private final Map<String, Integer> claimedLevels;

        public PlayerAchievementData(UUID uuid) {
            this.uuid = uuid;
            this.name = "";
            this.kills = 0;
            this.deaths = 0;
            this.blocksMined = 0;
            this.casesOpened = 0;
            this.totemPops = 0;
            this.earnedPoints = 0;
            this.claimedLevels = new ConcurrentHashMap<>();
        }

        public static PlayerAchievementData fromDocument(Document doc) {
            PlayerAchievementData data = new PlayerAchievementData(
                UUID.fromString(doc.getString("uuid"))
            );
            String name = doc.getString("name");
            data.name = name != null ? name : "";
            data.kills = doc.getInteger("kills", 0);
            data.deaths = doc.getInteger("deaths", 0);
            data.blocksMined = doc.getInteger("blocksMined", 0);
            data.casesOpened = doc.getInteger("casesOpened", doc.getInteger("cases", 0));
            data.totemPops = doc.getInteger("totemPops", doc.getInteger("totems", doc.getInteger("totempops", 0)));
            data.earnedPoints = doc.getInteger("earnedPoints", 0);

            Document levelsDoc = doc.get("claimedLevels", Document.class);
            if (levelsDoc != null) {
                for (String key : levelsDoc.keySet()) {
                    data.claimedLevels.put(key, levelsDoc.getInteger(key, 0));
                }
            }

            List<String> claimedList = doc.getList("claimed", String.class);
            if (claimedList != null && data.claimedLevels.isEmpty()) {
                for (String entry : claimedList) {
                    int separator = entry.lastIndexOf('_');
                    if (separator <= 0) continue;
                    String typeName = entry.substring(0, separator);
                    try {
                        int tier = Integer.parseInt(entry.substring(separator + 1));
                        data.claimedLevels.merge(typeName, tier, Math::max);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return data;
        }

        public Document toDocument() {
            Document levelsDoc = new Document();
            claimedLevels.forEach(levelsDoc::append);
            return new Document()
                .append("uuid", uuid.toString())
                .append("name", name)
                .append("kills", kills)
                .append("deaths", deaths)
                .append("blocksMined", blocksMined)
                .append("casesOpened", casesOpened)
                .append("totemPops", totemPops)
                .append("earnedPoints", earnedPoints)
                .append("claimedLevels", levelsDoc);
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name != null ? name : ""; }
        public int getKills() { return kills; }
        public void setKills(int kills) { this.kills = kills; }
        public int getDeaths() { return deaths; }
        public void setDeaths(int deaths) { this.deaths = deaths; }
        public int getBlocksMined() { return blocksMined; }
        public void setBlocksMined(int blocksMined) { this.blocksMined = blocksMined; }
        public int getCasesOpened() { return casesOpened; }
        public void setCasesOpened(int casesOpened) { this.casesOpened = casesOpened; }
        public int getTotemPops() { return totemPops; }
        public void setTotemPops(int totemPops) { this.totemPops = totemPops; }
        public int getEarnedPoints() { return earnedPoints; }
        public void setEarnedPoints(int earnedPoints) { this.earnedPoints = earnedPoints; }
        public Map<String, Integer> getClaimedLevels() { return claimedLevels; }
    }
}
