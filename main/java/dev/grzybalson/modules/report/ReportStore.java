package dev.grzybalson.modules.report;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Szybkie zgłoszenia (/szybkiezgloszenie) — wyłącznie MongoDB.
 */
public final class ReportStore {

    private static final String COLLECTION = "fast_reports";

    private final HollyCore plugin;
    private final Map<String, ReportEntry> reports = new ConcurrentHashMap<>();
    private final AtomicBoolean mongoReady = new AtomicBoolean(false);
    private MongoCollection<Document> collection;

    public ReportStore(HollyCore plugin) {
        this.plugin = plugin;
        bindMongo();
        if (plugin.getMongoManager() != null) {
            plugin.getMongoManager().onAvailable(() -> Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                bindMongo();
                migrateLegacyYamlIfPresent();
            }));
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::migrateLegacyYamlIfPresent);
    }

    private void bindMongo() {
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            mongoReady.set(false);
            plugin.getLogger().warning("[Report] MongoDB niedostępne — zgłoszenia nie będą zapisywane.");
            return;
        }
        try {
            collection = plugin.getMongoManager().getCollection(COLLECTION);
            plugin.getMongoManager().toFuture(
                collection.createIndex(
                    Indexes.ascending("reporterUuid", "targetUuid"),
                    new IndexOptions().unique(true)
                )
            );
            loadFromMongo();
            mongoReady.set(true);
        } catch (RuntimeException exception) {
            mongoReady.set(false);
            plugin.getLogger().log(Level.WARNING, "[Report] Błąd Mongo: " + exception.getMessage(), exception);
        }
    }

    private void loadFromMongo() {
        if (collection == null || plugin.getMongoManager() == null) {
            return;
        }
        try {
            List<Document> docs = plugin.getMongoManager().toListFuture(collection.find()).join();
            reports.clear();
            for (Document doc : docs) {
                ReportEntry entry = fromDocument(doc);
                if (entry != null) {
                    reports.put(key(entry.reporterUuid(), entry.targetUuid()), entry);
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[Report] Nie udało się wczytać zgłoszeń z Mongo: " + exception.getMessage());
        }
    }

    public boolean hasReport(UUID reporter, UUID target) {
        return reports.containsKey(key(reporter, target));
    }

    public void insert(UUID reporterUuid, String reporterName, UUID targetUuid, String targetName) {
        ReportEntry entry = new ReportEntry(
            UUID.randomUUID().toString(),
            reporterName == null ? "" : reporterName,
            reporterUuid,
            targetName == null ? "" : targetName,
            targetUuid,
            System.currentTimeMillis()
        );
        reports.put(key(reporterUuid, targetUuid), entry);
        saveAsync(entry);
    }

    private void saveAsync(ReportEntry entry) {
        if (!mongoReady.get() || collection == null || plugin.getMongoManager() == null) {
            return;
        }
        Document doc = toDocument(entry);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getMongoManager().toFuture(
                    collection.replaceOne(
                        Filters.and(
                            Filters.eq("reporterUuid", entry.reporterUuid().toString()),
                            Filters.eq("targetUuid", entry.targetUuid().toString())
                        ),
                        doc,
                        new ReplaceOptions().upsert(true)
                    )
                ).join();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "[Report] Zapis Mongo nieudany: " + exception.getMessage());
            }
        });
    }

    /** Jednorazowa migracja starego reports.yml → Mongo, potem kasuje plik. */
    private void migrateLegacyYamlIfPresent() {
        File file = new File(plugin.getDataFolder(), "addons/report/reports.yml");
        if (!file.isFile()) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            List<?> raw = yaml.getList("reports");
            int migrated = 0;
            if (raw != null) {
                for (Object entry : raw) {
                    if (!(entry instanceof Map<?, ?> map)) {
                        continue;
                    }
                    String reporterUuid = String.valueOf(map.get("reporter-uuid"));
                    String targetUuid = String.valueOf(map.get("target-uuid"));
                    if (reporterUuid.isBlank() || targetUuid.isBlank()
                        || "null".equals(reporterUuid) || "null".equals(targetUuid)) {
                        continue;
                    }
                    UUID reporter;
                    UUID target;
                    try {
                        reporter = UUID.fromString(reporterUuid);
                        target = UUID.fromString(targetUuid);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    if (hasReport(reporter, target)) {
                        continue;
                    }
                    ReportEntry report = new ReportEntry(
                        stringOr(map, "id", UUID.randomUUID().toString()),
                        stringOr(map, "reporter-name", ""),
                        reporter,
                        stringOr(map, "target-name", ""),
                        target,
                        toLong(map.get("created-at"))
                    );
                    reports.put(key(reporter, target), report);
                    if (mongoReady.get() && collection != null && plugin.getMongoManager() != null) {
                        plugin.getMongoManager().toFuture(
                            collection.replaceOne(
                                Filters.and(
                                    Filters.eq("reporterUuid", reporter.toString()),
                                    Filters.eq("targetUuid", target.toString())
                                ),
                                toDocument(report),
                                new ReplaceOptions().upsert(true)
                            )
                        ).join();
                    }
                    migrated++;
                }
            }
            if (!file.delete()) {
                plugin.getLogger().warning("[Report] Nie udało się usunąć starego reports.yml — skasuj ręcznie.");
            } else if (migrated > 0) {
                plugin.getLogger().info("[Report] Zmigrowano " + migrated + " zgłoszeń z YAML → MongoDB.");
            }
            File parent = file.getParentFile();
            if (parent != null && parent.isDirectory()) {
                String[] left = parent.list();
                if (left != null && left.length == 0 && !parent.delete()) {
                    // folder może zostać — nie krytyczne
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[Report] Migracja YAML nieudana: " + exception.getMessage());
        }
    }

    private static Document toDocument(ReportEntry entry) {
        return new Document()
            .append("id", entry.id())
            .append("reporterName", entry.reporterName())
            .append("reporterUuid", entry.reporterUuid().toString())
            .append("targetName", entry.targetName())
            .append("targetUuid", entry.targetUuid().toString())
            .append("createdAt", entry.createdAt());
    }

    private static ReportEntry fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        try {
            return new ReportEntry(
                doc.getString("id") == null ? UUID.randomUUID().toString() : doc.getString("id"),
                doc.getString("reporterName") == null ? "" : doc.getString("reporterName"),
                UUID.fromString(doc.getString("reporterUuid")),
                doc.getString("targetName") == null ? "" : doc.getString("targetName"),
                UUID.fromString(doc.getString("targetUuid")),
                doc.get("createdAt") instanceof Number number ? number.longValue() : System.currentTimeMillis()
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String key(UUID reporter, UUID target) {
        return reporter + ":" + target;
    }

    private static String stringOr(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.equals("null") ? fallback : text;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return System.currentTimeMillis();
        }
    }

    public record ReportEntry(
        String id,
        String reporterName,
        UUID reporterUuid,
        String targetName,
        UUID targetUuid,
        long createdAt
    ) {
    }
}
