package dev.grzybalson.database;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ClanDataManager {

    private static final long FAILED_LOOKUP_COOLDOWN_MILLIS = 10_000L;

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final Map<String, Clan> clanCache;
    private final Map<UUID, String> playerClanCache;
    private final Set<UUID> noClanCache;
    private final Map<String, CompletableFuture<Clan>> pendingClanLookups;
    private final Map<UUID, CompletableFuture<String>> pendingPlayerClanLookups;
    private final Map<String, Long> failedClanLookupUntil;
    private final Map<UUID, Long> failedPlayerClanLookupUntil;

    public ClanDataManager(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection(
            plugin.getConfigManager().getDatabaseConfig().getDbPrefix() + "_clans"
        );
        this.clanCache = new ConcurrentHashMap<>();
        this.playerClanCache = new ConcurrentHashMap<>();
        this.noClanCache = ConcurrentHashMap.newKeySet();
        this.pendingClanLookups = new ConcurrentHashMap<>();
        this.pendingPlayerClanLookups = new ConcurrentHashMap<>();
        this.failedClanLookupUntil = new ConcurrentHashMap<>();
        this.failedPlayerClanLookupUntil = new ConcurrentHashMap<>();
        ensureIndexes();
    }

    private void ensureIndexes() {
        plugin.getMongoManager().toFuture(
            collection.createIndex(Indexes.ascending("tag"), new IndexOptions().unique(true))
        ).exceptionally(throwable -> {
            plugin.getLogger().warning("Nie udało się utworzyć unikalnego indeksu klanów po tagu: " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> createClan(String tag, UUID leaderUuid) {
        String normalizedTag = normalizeTag(tag);

        Document doc = new Document()
            .append("tag", normalizedTag)
            .append("name", normalizedTag)
            .append("leader", leaderUuid.toString())
            .append("deputy", null)
            .append("members", Arrays.asList(leaderUuid.toString()))
            .append("elo", plugin.getConfigManager().getClanConfig().getStartingElo())
            .append("kills", 0)
            .append("deaths", 0)
            .append("pvp", false)
            .append("coins", 0)
            .append("upgrades", new Document())
            .append("createdAt", System.currentTimeMillis());

        return plugin.getMongoManager().toFuture(collection.insertOne(doc)).thenAccept(result -> {
            Clan clan = new Clan(normalizedTag, normalizedTag, leaderUuid, plugin.getConfigManager().getClanConfig().getStartingElo());
            clan.getMembers().add(leaderUuid);
            clanCache.put(normalizedTag, clan);
            playerClanCache.put(leaderUuid, normalizedTag);
            noClanCache.remove(leaderUuid);
            notifyClanPlaceholderUpdate(Collections.singleton(leaderUuid), true);
        });
    }

    public CompletableFuture<Boolean> deleteClan(String tag) {
        String normalizedTag = normalizeTag(tag);

        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("tag", normalizedTag)).first()
        ).thenCompose(existingDoc -> {
            if (existingDoc == null) {
                clanCache.remove(normalizedTag);
                playerClanCache.entrySet().removeIf(entry -> normalizedTag.equalsIgnoreCase(entry.getValue()));
                return CompletableFuture.completedFuture(false);
            }

            Clan clan = documentToClan(existingDoc);

            return plugin.getMongoManager().toFuture(
                collection.deleteMany(Filters.eq("tag", normalizedTag))
            ).thenApply(result -> {
                boolean deleted = result != null && result.getDeletedCount() > 0;
                if (deleted) {
                    clanCache.remove(normalizedTag);
                    pendingClanLookups.remove(normalizedTag);
                    failedClanLookupUntil.remove(normalizedTag);
                    for (UUID member : clan.getMembers()) {
                        playerClanCache.remove(member);
                        noClanCache.add(member);
                        pendingPlayerClanLookups.remove(member);
                        failedPlayerClanLookupUntil.remove(member);
                    }
                    playerClanCache.entrySet().removeIf(entry -> normalizedTag.equalsIgnoreCase(entry.getValue()));
                    notifyClanPlaceholderUpdate(clan.getMembers(), true);
                }
                return deleted;
            });
        });
    }

    public CompletableFuture<Boolean> clanExists(String tag) {
        String normalizedTag = normalizeTag(tag);

        return plugin.getMongoManager().toFuture(
            collection.countDocuments(Filters.eq("tag", normalizedTag))
        ).thenApply(count -> {
            boolean exists = count != null && count > 0;
            if (!exists) {
                clanCache.remove(normalizedTag);
            }
            return exists;
        });
    }

    public CompletableFuture<Clan> getClan(String tag) {
        String normalizedTag = normalizeTag(tag);
        if (normalizedTag.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        Clan cached = clanCache.get(normalizedTag);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        if (isLookupCoolingDown(failedClanLookupUntil, normalizedTag)) {
            return CompletableFuture.completedFuture(null);
        }

        return pendingClanLookups.computeIfAbsent(normalizedTag, lookupTag -> {
            CompletableFuture<Clan> lookup = plugin.getMongoManager().toFuture(
                collection.find(Filters.eq("tag", lookupTag)).first()
            ).thenApply(doc -> {
                if (doc != null) {
                    Clan clan = documentToClan(doc);
                    clanCache.put(lookupTag, clan);
                    return clan;
                }
                clanCache.remove(lookupTag);
                return null;
            });

            lookup.whenComplete((clan, throwable) -> {
                pendingClanLookups.remove(lookupTag, lookup);
                rememberLookupResult(failedClanLookupUntil, lookupTag, throwable);
            });
            return lookup;
        });
    }

    public CompletableFuture<String> getPlayerClan(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.completedFuture(null);
        }

        String cached = playerClanCache.get(playerUuid);
        if (cached != null) {
            String normalizedCachedTag = normalizeTag(cached);
            Clan cachedClan = clanCache.get(normalizedCachedTag);
            if (cachedClan != null && cachedClan.isMember(playerUuid)) {
                return CompletableFuture.completedFuture(normalizedCachedTag);
            }
        }

        if (noClanCache.contains(playerUuid) || isLookupCoolingDown(failedPlayerClanLookupUntil, playerUuid)) {
            return CompletableFuture.completedFuture(null);
        }

        return pendingPlayerClanLookups.computeIfAbsent(playerUuid, lookupUuid -> {
            CompletableFuture<String> lookup = plugin.getMongoManager().toFuture(
                collection.find(Filters.in("members", lookupUuid.toString())).first()
            ).thenApply(doc -> {
                if (doc != null) {
                    String normalizedTag = normalizeTag(doc.getString("tag"));
                    playerClanCache.put(lookupUuid, normalizedTag);
                    noClanCache.remove(lookupUuid);
                    clanCache.computeIfAbsent(normalizedTag, key -> documentToClan(doc));
                    return normalizedTag;
                }
                playerClanCache.remove(lookupUuid);
                noClanCache.add(lookupUuid);
                return null;
            });

            lookup.whenComplete((clanTag, throwable) -> {
                pendingPlayerClanLookups.remove(lookupUuid, lookup);
                rememberLookupResult(failedPlayerClanLookupUntil, lookupUuid, throwable);
            });
            return lookup;
        });
    }

    public String getCachedPlayerClan(UUID playerUuid) {
        String cached = playerClanCache.get(playerUuid);
        if (cached == null) {
            return null;
        }

        String normalizedCachedTag = normalizeTag(cached);
        Clan cachedClan = clanCache.get(normalizedCachedTag);
        if (cachedClan != null && cachedClan.isMember(playerUuid)) {
            return normalizedCachedTag;
        }

        return null;
    }

    public boolean hasCachedPlayerClanLookup(UUID playerUuid) {
        return noClanCache.contains(playerUuid) || getCachedPlayerClan(playerUuid) != null;
    }

    public Clan getCachedClan(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        return clanCache.get(normalizeTag(tag));
    }

    public CompletableFuture<Void> addMember(String tag, UUID playerUuid) {
        String normalizedTag = normalizeTag(tag);
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.addToSet("members", playerUuid.toString())
        )).thenAccept(result -> {
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.getMembers().add(playerUuid);
            }
            playerClanCache.put(playerUuid, normalizedTag);
            noClanCache.remove(playerUuid);
            notifyClanPlaceholderUpdate(Collections.singleton(playerUuid), true);
        });
    }

    public CompletableFuture<Void> removeMember(String tag, UUID playerUuid) {
        String normalizedTag = normalizeTag(tag);
        Clan cached = clanCache.get(normalizedTag);
        boolean clearDeputy = cached != null && cached.isDeputy(playerUuid);

        var update = clearDeputy
            ? Updates.combine(Updates.pull("members", playerUuid.toString()), Updates.unset("deputy"))
            : Updates.pull("members", playerUuid.toString());

        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            update
        )).thenAccept(result -> {
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.getMembers().remove(playerUuid);
                if (clearDeputy) {
                    clan.setDeputy(null);
                }
            }
            playerClanCache.remove(playerUuid);
            noClanCache.add(playerUuid);
            notifyClanPlaceholderUpdate(Collections.singleton(playerUuid), true);
        });
    }

    public CompletableFuture<Void> setDeputy(String tag, UUID deputyUuid) {
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizeTag(tag)),
            Updates.set("deputy", deputyUuid != null ? deputyUuid.toString() : null)
        )).thenAccept(result -> {
            Clan clan = clanCache.get(normalizeTag(tag));
            if (clan != null) {
                clan.setDeputy(deputyUuid);
            }
        });
    }

    public CompletableFuture<Void> setLeader(String tag, UUID newLeaderUuid, UUID newDeputyUuid) {
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizeTag(tag)),
            Updates.combine(
                Updates.set("leader", newLeaderUuid.toString()),
                Updates.set("deputy", newDeputyUuid != null ? newDeputyUuid.toString() : null)
            )
        )).thenAccept(result -> {
            Clan clan = clanCache.get(normalizeTag(tag));
            if (clan != null) {
                clan.setLeader(newLeaderUuid);
                clan.setDeputy(newDeputyUuid);
            }
        });
    }

    public CompletableFuture<Void> setPvp(String tag, boolean enabled) {
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizeTag(tag)),
            Updates.set("pvp", enabled)
        )).thenAccept(result -> {
            Clan clan = clanCache.get(normalizeTag(tag));
            if (clan != null) {
                clan.setPvpEnabled(enabled);
            }
        });
    }

    public CompletableFuture<Void> setCoins(String tag, int coins) {
        String normalizedTag = normalizeTag(tag);
        int safeCoins = Math.max(0, coins);
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.set("coins", safeCoins)
        )).thenAccept(result -> {
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.setCoins(safeCoins);
            }
        });
    }

    public CompletableFuture<Void> addCoins(String tag, int amount) {
        String normalizedTag = normalizeTag(tag);
        if (amount == 0) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.inc("coins", amount)
        )).thenAccept(result -> {
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.setCoins(Math.max(0, clan.getCoins() + amount));
            }
        });
    }

    public CompletableFuture<Boolean> purchaseUpgrade(String tag, String upgradeType, int newLevel, int price) {
        String normalizedTag = normalizeTag(tag);
        String typeKey = upgradeType == null ? "" : upgradeType.trim().toUpperCase(Locale.ROOT);
        if (typeKey.isBlank() || newLevel < 1 || price < 0) {
            return CompletableFuture.completedFuture(false);
        }

        Clan cached = clanCache.get(normalizedTag);
        if (cached == null) {
            return CompletableFuture.completedFuture(false);
        }

        int currentLevel = cached.getUpgradeLevel(typeKey);
        if (currentLevel + 1 != newLevel || cached.getCoins() < price) {
            return CompletableFuture.completedFuture(false);
        }

        Document upgradesDoc = upgradesToDocument(cached.getUpgrades());
        upgradesDoc.put(typeKey, newLevel);
        int newCoins = cached.getCoins() - price;

        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.combine(
                Updates.set("coins", newCoins),
                Updates.set("upgrades", upgradesDoc)
            )
        )).thenApply(result -> {
            if (result == null || result.getMatchedCount() == 0) {
                return false;
            }
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.setCoins(newCoins);
                clan.getUpgrades().put(typeKey, newLevel);
            }
            return true;
        });
    }

    public CompletableFuture<Void> updateElo(String tag, int newElo) {
        String normalizedTag = normalizeTag(tag);
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.set("elo", newElo)
        )).thenAccept(result -> {
            if (result == null || result.getMatchedCount() == 0) {
                clanCache.remove(normalizedTag);
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
                return;
            }
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.setElo(newElo);
                notifyClanPlaceholderUpdate(clan.getMembers(), true);
            } else {
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
            }
        });
    }

    public CompletableFuture<Void> incrementKills(String tag) {
        String normalizedTag = normalizeTag(tag);
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.inc("kills", 1)
        )).thenAccept(result -> {
            if (result == null || result.getMatchedCount() == 0) {
                clanCache.remove(normalizedTag);
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
                return;
            }
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.setKills(clan.getKills() + 1);
                notifyClanPlaceholderUpdate(clan.getMembers(), true);
            } else {
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
            }
        });
    }

    public CompletableFuture<Void> recordKill(String tag, int newElo) {
        String normalizedTag = normalizeTag(tag);
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.combine(
                Updates.set("elo", newElo),
                Updates.inc("kills", 1)
            )
        )).thenAccept(result -> {
            if (result == null || result.getMatchedCount() == 0) {
                clanCache.remove(normalizedTag);
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
                return;
            }
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.setElo(newElo);
                clan.setKills(clan.getKills() + 1);
                notifyClanPlaceholderUpdate(clan.getMembers(), true);
            } else {
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
            }
        });
    }

    public CompletableFuture<Void> incrementDeaths(String tag) {
        String normalizedTag = normalizeTag(tag);
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.inc("deaths", 1)
        )).thenAccept(result -> {
            if (result == null || result.getMatchedCount() == 0) {
                clanCache.remove(normalizedTag);
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
                return;
            }
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.setDeaths(clan.getDeaths() + 1);
                notifyClanPlaceholderUpdate(clan.getMembers(), true);
            } else {
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
            }
        });
    }

    public CompletableFuture<Void> recordDeath(String tag, int newElo) {
        String normalizedTag = normalizeTag(tag);
        return plugin.getMongoManager().toFuture(collection.updateOne(
            Filters.eq("tag", normalizedTag),
            Updates.combine(
                Updates.set("elo", newElo),
                Updates.inc("deaths", 1)
            )
        )).thenAccept(result -> {
            if (result == null || result.getMatchedCount() == 0) {
                clanCache.remove(normalizedTag);
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
                return;
            }
            Clan clan = clanCache.get(normalizedTag);
            if (clan != null) {
                clan.setElo(newElo);
                clan.setDeaths(clan.getDeaths() + 1);
                notifyClanPlaceholderUpdate(clan.getMembers(), true);
            } else {
                notifyClanPlaceholderUpdate(Collections.emptySet(), true);
            }
        });
    }

    public CompletableFuture<List<Clan>> getTopClans(int limit) {
        List<Clan> clans = new ArrayList<>();
        return plugin.getMongoManager().toListFuture(
            collection.find().sort(new Document("elo", -1)).limit(limit)
        ).thenApply(docs -> {
            for (Document doc : docs) {
                try {
                    Clan clan = documentToClan(doc);
                    if (isValidClan(clan)) {
                        clans.add(clan);
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Pominieto uszkodzony wpis klanu w topce: " + exception.getMessage());
                }
            }
            return clans;
        });
    }

    private String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().toUpperCase(Locale.ROOT);
    }

    private <T> boolean isLookupCoolingDown(Map<T, Long> cooldowns, T key) {
        Long until = cooldowns.get(key);
        if (until == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (until > now) {
            return true;
        }

        cooldowns.remove(key, until);
        return false;
    }

    private <T> void rememberLookupResult(Map<T, Long> cooldowns, T key, Throwable throwable) {
        if (throwable == null) {
            cooldowns.remove(key);
            return;
        }
        cooldowns.put(key, System.currentTimeMillis() + FAILED_LOOKUP_COOLDOWN_MILLIS);
    }

    private void notifyClanPlaceholderUpdate(Collection<UUID> playerUuids, boolean refreshTop) {
        if (plugin.getHollyPlaceholderExpansion() != null) {
            for (UUID uuid : playerUuids) {
                plugin.getHollyPlaceholderExpansion().refreshPlayerClan(uuid);
            }
            if (refreshTop) {
                plugin.getHollyPlaceholderExpansion().refreshTopClans();
            }
        }

        if (plugin.getTablistManager() != null) {
            if (refreshTop) {
                plugin.getTablistManager().refreshLeaderboardRows();
            }
            if (playerUuids == null || playerUuids.isEmpty()) {
                plugin.getTablistManager().refreshAllIdentities();
            } else {
                for (UUID uuid : playerUuids) {
                    plugin.getTablistManager().refreshPlayerIdentity(uuid);
                }
            }
        }
    }

    private Clan documentToClan(Document doc) {
        String tag = doc.getString("tag");
        String name = doc.getString("name");
        UUID leader = UUID.fromString(doc.getString("leader"));
        int elo = doc.getInteger("elo", 1000);

        Clan clan = new Clan(tag, name, leader, elo);

        String deputyStr = doc.getString("deputy");
        if (deputyStr != null) {
            clan.setDeputy(UUID.fromString(deputyStr));
        }

        List<String> memberStrings = doc.getList("members", String.class);
        if (memberStrings != null) {
            for (String memberStr : memberStrings) {
                UUID memberUuid = UUID.fromString(memberStr);
                clan.getMembers().add(memberUuid);
                playerClanCache.put(memberUuid, tag);
                noClanCache.remove(memberUuid);
            }
        }

        clan.setKills(doc.getInteger("kills", 0));
        clan.setDeaths(doc.getInteger("deaths", 0));
        clan.setPvpEnabled(false);
        clan.setCoins(doc.getInteger("coins", 0));

        Object upgradesRaw = doc.get("upgrades");
        if (upgradesRaw instanceof Document upgradesDoc) {
            for (String key : upgradesDoc.keySet()) {
                Object value = upgradesDoc.get(key);
                if (value instanceof Number number) {
                    clan.getUpgrades().put(key.toUpperCase(Locale.ROOT), number.intValue());
                }
            }
        } else if (upgradesRaw instanceof Map<?, ?> upgradesMap) {
            for (Map.Entry<?, ?> entry : upgradesMap.entrySet()) {
                if (entry.getKey() == null || !(entry.getValue() instanceof Number number)) {
                    continue;
                }
                clan.getUpgrades().put(String.valueOf(entry.getKey()).toUpperCase(Locale.ROOT), number.intValue());
            }
        }

        return clan;
    }

    private Document upgradesToDocument(Map<String, Integer> upgrades) {
        Document document = new Document();
        if (upgrades == null || upgrades.isEmpty()) {
            return document;
        }
        for (Map.Entry<String, Integer> entry : upgrades.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            document.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
        }
        return document;
    }

    private boolean isValidClan(Clan clan) {
        return clan != null
            && clan.getTag() != null
            && !clan.getTag().isBlank()
            && clan.getLeader() != null
            && !clan.getMembers().isEmpty();
    }

    public void invalidateCache(UUID playerUuid) {
        playerClanCache.remove(playerUuid);
        noClanCache.remove(playerUuid);
        pendingPlayerClanLookups.remove(playerUuid);
        failedPlayerClanLookupUntil.remove(playerUuid);
    }

    public void disableAllPvp() {
        for (Clan clan : clanCache.values()) {
            clan.setPvpEnabled(false);
        }
    }

    public void clearAllCaches() {
        clanCache.clear();
        playerClanCache.clear();
        noClanCache.clear();
        pendingPlayerClanLookups.clear();
        failedPlayerClanLookupUntil.clear();
    }

    public CompletableFuture<Long> resetAllRankingStats() {
        final int resetElo = 1000;
        return plugin.getMongoManager().toFuture(
            collection.updateMany(
                new Document(),
                Updates.combine(
                    Updates.set("kills", 0),
                    Updates.set("deaths", 0),
                    Updates.set("elo", resetElo)
                )
            )
        ).thenApply(result -> {
            for (Clan clan : clanCache.values()) {
                clan.setKills(0);
                clan.setDeaths(0);
                clan.setElo(resetElo);
            }
            notifyClanPlaceholderUpdate(Collections.emptySet(), true);
            return result != null ? result.getModifiedCount() : 0L;
        });
    }

    public static class Clan {
        private final String tag;
        private final String name;
        private UUID leader;
        private UUID deputy;
        private final Set<UUID> members;
        private int elo;
        private int kills;
        private int deaths;
        private boolean pvpEnabled;
        private int coins;
        private final Map<String, Integer> upgrades;

        public Clan(String tag, String name, UUID leader, int elo) {
            this.tag = tag;
            this.name = name;
            this.leader = leader;
            this.elo = elo;
            this.members = new HashSet<>();
            this.kills = 0;
            this.deaths = 0;
            this.pvpEnabled = false;
            this.coins = 0;
            this.upgrades = new ConcurrentHashMap<>();
        }

        public String getTag() { return tag; }
        public String getName() { return tag; }
        public UUID getLeader() { return leader; }
        public void setLeader(UUID leader) { this.leader = leader; }
        public UUID getDeputy() { return deputy; }
        public void setDeputy(UUID deputy) { this.deputy = deputy; }
        public Set<UUID> getMembers() { return members; }
        public int getElo() { return elo; }
        public void setElo(int elo) { this.elo = elo; }
        public int getKills() { return kills; }
        public void setKills(int kills) { this.kills = kills; }
        public int getDeaths() { return deaths; }
        public void setDeaths(int deaths) { this.deaths = deaths; }
        public boolean isPvpEnabled() { return pvpEnabled; }
        public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
        public int getCoins() { return coins; }
        public void setCoins(int coins) { this.coins = Math.max(0, coins); }
        public Map<String, Integer> getUpgrades() { return upgrades; }

        public int getUpgradeLevel(String type) {
            if (type == null || type.isBlank()) {
                return 0;
            }
            return upgrades.getOrDefault(type.trim().toUpperCase(Locale.ROOT), 0);
        }

        public boolean isLeader(UUID uuid) { return leader != null && leader.equals(uuid); }
        public boolean isDeputy(UUID uuid) { return deputy != null && deputy.equals(uuid); }
        public boolean isLeaderOrDeputy(UUID uuid) { return isLeader(uuid) || isDeputy(uuid); }
        public boolean isMember(UUID uuid) { return members.contains(uuid); }
    }
}
