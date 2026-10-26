package dev.grzybalson.modules.moderation;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.database.MongoManager;
import org.bson.Document;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persistencja moderacji wyłącznie przez MongoDB. Jednorazowa migracja legacy YAML → Mongo.
 */
public class ModerationStorage {

    private static final String BANS_COLLECTION = "moderation_bans";
    private static final String MUTES_COLLECTION = "moderation_mutes";
    private static final String ALTS_COLLECTION = "moderation_alts";
    private static final String REPORTS_COLLECTION = "moderation_reports";

    private final HollyCore plugin;
    private final File dataFolder;
    private final File bansFile;
    private final File mutesFile;
    private final File altsFile;
    private final File reportsFile;

    private final boolean mongo;
    private MongoCollection<Document> bansCollection;
    private MongoCollection<Document> mutesCollection;
    private MongoCollection<Document> altsCollection;
    private MongoCollection<Document> reportsCollection;

    public ModerationStorage(HollyCore plugin) {
        this.plugin = plugin;
        this.dataFolder = HollyDataPaths.getAddonFolder(plugin, "moderation");
        this.bansFile = new File(dataFolder, "bans.yml");
        this.mutesFile = new File(dataFolder, "mutes.yml");
        this.altsFile = new File(dataFolder, "alts.yml");
        this.reportsFile = new File(dataFolder, "reports.yml");

        MongoManager mongoManager = plugin.getMongoManager();
        this.mongo = mongoManager != null && mongoManager.isAvailable();
        if (mongo) {
            this.bansCollection = mongoManager.getCollection(BANS_COLLECTION);
            this.mutesCollection = mongoManager.getCollection(MUTES_COLLECTION);
            this.altsCollection = mongoManager.getCollection(ALTS_COLLECTION);
            this.reportsCollection = mongoManager.getCollection(REPORTS_COLLECTION);
            ensureIndexes();
        } else {
            plugin.getLogger().warning("[Moderation] MongoDB niedostępne — bany/muty nie będą zapisywane.");
        }
    }

    public boolean usesMongo() {
        return mongo;
    }

    public CompletableFuture<LoadResult> loadAll() {
        if (!mongo) {
            return CompletableFuture.completedFuture(LoadResult.empty());
        }
        return migrateLegacyYamlIfPresent()
            .thenCompose(v -> loadFromMongo())
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "[Moderation] Błąd ładowania: " + ex.getMessage(), ex);
                return LoadResult.empty();
            });
    }

    public void saveBan(ModerationPunishment ban) {
        if (!mongo || ban == null) {
            return;
        }
        Document doc = punishmentToDocument(ban);
        plugin.getMongoManager().toFuture(
            bansCollection.replaceOne(
                Filters.eq("uuid", ban.uuid().toString()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać bana do Mongo: " + ex.getMessage());
            return null;
        });
    }

    public void removeBan(UUID uuid) {
        if (!mongo || uuid == null) {
            return;
        }
        plugin.getMongoManager().toFuture(
            bansCollection.deleteOne(Filters.eq("uuid", uuid.toString()))
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Nie udało się usunąć bana z Mongo: " + ex.getMessage());
            return null;
        });
    }

    public void clearAllBans() {
        if (!mongo) {
            return;
        }
        plugin.getMongoManager().toFuture(bansCollection.deleteMany(new Document()))
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING, "Nie udało się wyczyścić banów w Mongo: " + ex.getMessage());
                return null;
            });
    }

    public void saveMute(ModerationPunishment mute) {
        if (!mongo || mute == null) {
            return;
        }
        Document doc = punishmentToDocument(mute);
        plugin.getMongoManager().toFuture(
            mutesCollection.replaceOne(
                Filters.eq("uuid", mute.uuid().toString()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać mute do Mongo: " + ex.getMessage());
            return null;
        });
    }

    public void removeMute(UUID uuid) {
        if (!mongo || uuid == null) {
            return;
        }
        plugin.getMongoManager().toFuture(
            mutesCollection.deleteOne(Filters.eq("uuid", uuid.toString()))
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Nie udało się usunąć mute z Mongo: " + ex.getMessage());
            return null;
        });
    }

    public void saveAltLink(String ip, UUID uuid, String name) {
        if (!mongo || ip == null || ip.isBlank() || uuid == null) {
            return;
        }
        String normalizedIp = normalizeIp(ip);
        Document doc = new Document("uuid", uuid.toString())
            .append("name", name == null ? "" : name)
            .append("nameLower", name == null ? "" : name.toLowerCase(Locale.ROOT))
            .append("ip", normalizedIp)
            .append("lastSeen", System.currentTimeMillis());
        plugin.getMongoManager().toFuture(
            altsCollection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać alts do Mongo: " + ex.getMessage());
            return null;
        });
    }

    public void saveReportList(UUID victimUuid, Collection<ReportEntry> entries) {
        if (!mongo || victimUuid == null) {
            return;
        }
        List<Document> docs = new ArrayList<>();
        for (ReportEntry entry : entries) {
            docs.add(new Document("name", entry.name())
                .append("uuid", entry.uuid() == null ? "" : entry.uuid().toString())
                .append("bannedAt", entry.bannedAt()));
        }
        Document doc = new Document("uuid", victimUuid.toString())
            .append("entries", docs);
        plugin.getMongoManager().toFuture(
            reportsCollection.replaceOne(
                Filters.eq("uuid", victimUuid.toString()),
                doc,
                new ReplaceOptions().upsert(true)
            )
        ).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Nie udało się zapisać listy zgłoszeń do Mongo: " + ex.getMessage());
            return null;
        });
    }

    public void clearReportList(UUID victimUuid) {
        if (!mongo || victimUuid == null) {
            return;
        }
        plugin.getMongoManager().toFuture(
            reportsCollection.deleteOne(Filters.eq("uuid", victimUuid.toString()))
        );
    }

    private void ensureIndexes() {
        MongoManager mongoManager = plugin.getMongoManager();
        mongoManager.toFuture(bansCollection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true)));
        mongoManager.toFuture(mutesCollection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true)));
        mongoManager.toFuture(altsCollection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true)));
        mongoManager.toFuture(altsCollection.createIndex(Indexes.ascending("ip")));
        mongoManager.toFuture(altsCollection.createIndex(Indexes.ascending("nameLower")));
        mongoManager.toFuture(reportsCollection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true)));
    }

    private CompletableFuture<Void> migrateLegacyYamlIfPresent() {
        boolean any = bansFile.isFile() || mutesFile.isFile() || altsFile.isFile() || reportsFile.isFile();
        if (!any) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                LoadResult legacy = loadFromYaml();
                int migrated = 0;
                MongoManager mongoManager = plugin.getMongoManager();
                ReplaceOptions upsert = new ReplaceOptions().upsert(true);

                for (ModerationPunishment ban : legacy.bans().values()) {
                    mongoManager.toFuture(bansCollection.replaceOne(
                        Filters.eq("uuid", ban.uuid().toString()),
                        punishmentToDocument(ban),
                        upsert
                    )).join();
                    migrated++;
                }
                for (ModerationPunishment mute : legacy.mutes().values()) {
                    mongoManager.toFuture(mutesCollection.replaceOne(
                        Filters.eq("uuid", mute.uuid().toString()),
                        punishmentToDocument(mute),
                        upsert
                    )).join();
                    migrated++;
                }
                for (AltProfile profile : legacy.profiles().values()) {
                    Document doc = new Document("uuid", profile.uuid().toString())
                        .append("name", profile.name() == null ? "" : profile.name())
                        .append("nameLower", profile.name() == null ? "" : profile.name().toLowerCase(Locale.ROOT))
                        .append("ip", normalizeIp(profile.ip()))
                        .append("lastSeen", profile.lastSeen());
                    mongoManager.toFuture(altsCollection.replaceOne(
                        Filters.eq("uuid", profile.uuid().toString()),
                        doc,
                        upsert
                    )).join();
                    migrated++;
                }
                for (Map.Entry<UUID, List<ReportEntry>> entry : legacy.reports().entrySet()) {
                    List<Document> docs = new ArrayList<>();
                    for (ReportEntry report : entry.getValue()) {
                        docs.add(new Document("name", report.name())
                            .append("uuid", report.uuid() == null ? "" : report.uuid().toString())
                            .append("bannedAt", report.bannedAt()));
                    }
                    Document doc = new Document("uuid", entry.getKey().toString()).append("entries", docs);
                    mongoManager.toFuture(reportsCollection.replaceOne(
                        Filters.eq("uuid", entry.getKey().toString()),
                        doc,
                        upsert
                    )).join();
                    migrated++;
                }

                deleteQuietly(bansFile);
                deleteQuietly(mutesFile);
                deleteQuietly(altsFile);
                deleteQuietly(reportsFile);
                if (migrated > 0) {
                    plugin.getLogger().info("[Moderation] Zmigrowano " + migrated + " wpisów YAML → MongoDB.");
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "[Moderation] Migracja YAML nieudana: " + ex.getMessage(), ex);
            }
        });
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.isFile() && !file.delete()) {
            // zostaw — admin może skasować ręcznie
        }
    }

    private CompletableFuture<LoadResult> loadFromMongo() {
        MongoManager mongoManager = plugin.getMongoManager();
        CompletableFuture<List<Document>> bansFuture = mongoManager.toListFuture(bansCollection.find());
        CompletableFuture<List<Document>> mutesFuture = mongoManager.toListFuture(mutesCollection.find());
        CompletableFuture<List<Document>> altsFuture = mongoManager.toListFuture(altsCollection.find());
        CompletableFuture<List<Document>> reportsFuture = mongoManager.toListFuture(reportsCollection.find());

        return CompletableFuture.allOf(bansFuture, mutesFuture, altsFuture, reportsFuture)
            .thenApply(v -> {
                Map<UUID, ModerationPunishment> bans = new ConcurrentHashMap<>();
                Map<UUID, ModerationPunishment> mutes = new ConcurrentHashMap<>();
                Map<String, Set<UUID>> ipToPlayers = new ConcurrentHashMap<>();
                Map<UUID, AltProfile> profiles = new ConcurrentHashMap<>();
                Map<UUID, List<ReportEntry>> reports = new ConcurrentHashMap<>();
                Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

                long now = System.currentTimeMillis();
                for (Document doc : bansFuture.join()) {
                    ModerationPunishment ban = documentToPunishment(doc, ModerationPunishment.Type.BAN);
                    if (ban != null && ban.isActive(now)) {
                        bans.put(ban.uuid(), ban);
                        indexName(nameIndex, ban.name(), ban.uuid());
                    }
                }
                for (Document doc : mutesFuture.join()) {
                    ModerationPunishment mute = documentToPunishment(doc, ModerationPunishment.Type.MUTE);
                    if (mute != null && mute.isActive(now)) {
                        mutes.put(mute.uuid(), mute);
                        indexName(nameIndex, mute.name(), mute.uuid());
                    }
                }
                for (Document doc : altsFuture.join()) {
                    UUID uuid = parseUuid(doc.getString("uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    String name = doc.getString("name");
                    String ip = normalizeIp(doc.getString("ip"));
                    long lastSeen = doc.getLong("lastSeen") == null ? 0L : doc.getLong("lastSeen");
                    profiles.put(uuid, new AltProfile(uuid, name, ip, lastSeen));
                    if (!ip.isBlank()) {
                        ipToPlayers.computeIfAbsent(ip, key -> ConcurrentHashMap.newKeySet()).add(uuid);
                    }
                    indexName(nameIndex, name, uuid);
                }
                for (Document doc : reportsFuture.join()) {
                    UUID uuid = parseUuid(doc.getString("uuid"));
                    if (uuid == null) {
                        continue;
                    }
                    List<ReportEntry> entries = new ArrayList<>();
                    Object raw = doc.get("entries");
                    if (raw instanceof List<?> list) {
                        for (Object item : list) {
                            if (item instanceof Document entryDoc) {
                                ReportEntry entry = reportFromDocument(entryDoc);
                                if (entry != null) {
                                    entries.add(entry);
                                }
                            }
                        }
                    }
                    if (!entries.isEmpty()) {
                        reports.put(uuid, entries);
                    }
                }
                return new LoadResult(bans, mutes, ipToPlayers, profiles, reports, nameIndex);
            });
    }

    private LoadResult loadFromYaml() {
        Map<UUID, ModerationPunishment> bans = new ConcurrentHashMap<>();
        Map<UUID, ModerationPunishment> mutes = new ConcurrentHashMap<>();
        Map<String, Set<UUID>> ipToPlayers = new ConcurrentHashMap<>();
        Map<UUID, AltProfile> profiles = new ConcurrentHashMap<>();
        Map<UUID, List<ReportEntry>> reports = new ConcurrentHashMap<>();
        Map<String, UUID> nameIndex = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();

        loadPunishmentsYaml(bansFile, "bans", ModerationPunishment.Type.BAN, bans, nameIndex, now);
        loadPunishmentsYaml(mutesFile, "mutes", ModerationPunishment.Type.MUTE, mutes, nameIndex, now);

        if (altsFile.isFile()) {
            YamlConfiguration altsYaml = YamlConfiguration.loadConfiguration(altsFile);
            ConfigurationSection players = altsYaml.getConfigurationSection("players");
            if (players != null) {
                for (String key : players.getKeys(false)) {
                    ConfigurationSection section = players.getConfigurationSection(key);
                    if (section == null) {
                        continue;
                    }
                    UUID uuid = parseUuid(section.getString("uuid", key));
                    if (uuid == null) {
                        continue;
                    }
                    String name = section.getString("name", "");
                    String ip = normalizeIp(section.getString("ip", ""));
                    long lastSeen = section.getLong("lastSeen", 0L);
                    profiles.put(uuid, new AltProfile(uuid, name, ip, lastSeen));
                    if (!ip.isBlank()) {
                        ipToPlayers.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(uuid);
                    }
                    indexName(nameIndex, name, uuid);
                }
            }
        }

        if (reportsFile.isFile()) {
            YamlConfiguration reportsYaml = YamlConfiguration.loadConfiguration(reportsFile);
            ConfigurationSection reportsSection = reportsYaml.getConfigurationSection("reports");
            if (reportsSection != null) {
                for (String key : reportsSection.getKeys(false)) {
                    UUID uuid = parseUuid(key);
                    if (uuid == null) {
                        continue;
                    }
                    List<ReportEntry> entries = new ArrayList<>();
                    List<?> rawList = reportsSection.getList(key);
                    if (rawList != null) {
                        for (Object item : rawList) {
                            if (!(item instanceof Map<?, ?> rawMap)) {
                                continue;
                            }
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) rawMap;
                            String name = String.valueOf(map.getOrDefault("name", ""));
                            UUID killerUuid = parseUuid(String.valueOf(map.getOrDefault("uuid", "")));
                            long bannedAt = 0L;
                            Object bannedRaw = map.get("bannedAt");
                            if (bannedRaw instanceof Number number) {
                                bannedAt = number.longValue();
                            }
                            if (!name.isBlank() && !"null".equalsIgnoreCase(name)) {
                                entries.add(new ReportEntry(killerUuid, name, bannedAt));
                            }
                        }
                    }
                    if (!entries.isEmpty()) {
                        reports.put(uuid, entries);
                    }
                }
            }
        }

        return new LoadResult(bans, mutes, ipToPlayers, profiles, reports, nameIndex);
    }

    private void loadPunishmentsYaml(
        File file,
        String root,
        ModerationPunishment.Type type,
        Map<UUID, ModerationPunishment> target,
        Map<String, UUID> nameIndex,
        long now
    ) {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection(root);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            UUID uuid = parseUuid(entry.getString("uuid", key));
            if (uuid == null) {
                continue;
            }
            ModerationPunishment punishment = new ModerationPunishment(
                type,
                uuid,
                entry.getString("name", key),
                entry.getString("ip", ""),
                entry.getString("reason", "Brak powodu"),
                entry.getString("staff", "Konsola"),
                entry.getLong("createdAt", now),
                entry.getLong("expiresAt", 0L),
                entry.getBoolean("ipBan", false)
            );
            if (punishment.isActive(now)) {
                target.put(uuid, punishment);
                indexName(nameIndex, punishment.name(), uuid);
            }
        }
    }

    private Document punishmentToDocument(ModerationPunishment punishment) {
        return new Document("uuid", punishment.uuid().toString())
            .append("name", punishment.name())
            .append("nameLower", punishment.name() == null ? "" : punishment.name().toLowerCase(Locale.ROOT))
            .append("ip", punishment.ip())
            .append("reason", punishment.reason())
            .append("staff", punishment.staff())
            .append("createdAt", punishment.createdAt())
            .append("expiresAt", punishment.expiresAt())
            .append("ipBan", punishment.ipBan())
            .append("type", punishment.type().name());
    }

    private ModerationPunishment documentToPunishment(Document doc, ModerationPunishment.Type type) {
        UUID uuid = parseUuid(doc.getString("uuid"));
        if (uuid == null) {
            return null;
        }
        Long expiresAt = doc.getLong("expiresAt");
        Long createdAt = doc.getLong("createdAt");
        return new ModerationPunishment(
            type,
            uuid,
            doc.getString("name"),
            doc.getString("ip"),
            doc.getString("reason"),
            doc.getString("staff"),
            createdAt == null ? System.currentTimeMillis() : createdAt,
            expiresAt == null ? 0L : expiresAt,
            Boolean.TRUE.equals(doc.getBoolean("ipBan"))
        );
    }

    private ReportEntry reportFromDocument(Document doc) {
        String name = doc.getString("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        UUID uuid = parseUuid(doc.getString("uuid"));
        Long bannedAt = doc.getLong("bannedAt");
        return new ReportEntry(uuid, name, bannedAt == null ? 0L : bannedAt);
    }

    private static void indexName(Map<String, UUID> nameIndex, String name, UUID uuid) {
        if (name == null || name.isBlank() || uuid == null) {
            return;
        }
        nameIndex.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    static String normalizeIp(String ip) {
        if (ip == null) {
            return "";
        }
        String trimmed = ip.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int percent = trimmed.indexOf('%');
        if (percent >= 0) {
            trimmed = trimmed.substring(0, percent);
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && trimmed.contains(".")) {
            trimmed = trimmed.substring(0, colon);
        }
        return trimmed;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record AltProfile(UUID uuid, String name, String ip, long lastSeen) {}

    public record ReportEntry(UUID uuid, String name, long bannedAt) {}

    public record LoadResult(
        Map<UUID, ModerationPunishment> bans,
        Map<UUID, ModerationPunishment> mutes,
        Map<String, Set<UUID>> ipToPlayers,
        Map<UUID, AltProfile> profiles,
        Map<UUID, List<ReportEntry>> reports,
        Map<String, UUID> nameIndex
    ) {
        public LoadResult {
            bans = bans == null ? new ConcurrentHashMap<>() : bans;
            mutes = mutes == null ? new ConcurrentHashMap<>() : mutes;
            ipToPlayers = ipToPlayers == null ? new ConcurrentHashMap<>() : ipToPlayers;
            profiles = profiles == null ? new ConcurrentHashMap<>() : profiles;
            reports = reports == null ? new ConcurrentHashMap<>() : reports;
            nameIndex = nameIndex == null ? new ConcurrentHashMap<>() : nameIndex;
        }

        public static LoadResult empty() {
            return new LoadResult(
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>()
            );
        }

        public Set<UUID> copyIpPlayers(String ip) {
            Set<UUID> set = ipToPlayers.get(ip);
            if (set == null || set.isEmpty()) {
                return Collections.emptySet();
            }
            return new LinkedHashSet<>(set);
        }
    }
}
