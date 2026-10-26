package dev.grzybalson.modules.backup;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import dev.grzybalson.HollyCore;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wspólny rejestr trybów (db-prefix) + kolejka nadań, żeby jeden bot Discord
 * obsługiwał kilka serwerów HollyCore na tym samym klastrze Mongo.
 */
public final class BackupModeHub {

    private static final long MODE_TTL_MS = 25_000L;
    private static final long LEADER_TTL_MS = 20_000L;

    private final HollyCore plugin;
    private final BackupManager backupManager;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public BackupModeHub(HollyCore plugin, BackupManager backupManager) {
        this.plugin = plugin;
        this.backupManager = backupManager;
    }

    public String localPrefix() {
        String prefix = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
        return prefix == null || prefix.isBlank() ? "hollycore" : prefix.trim();
    }

    public String localName() {
        String configured = plugin.getConfigManager().getBackupConfig().getDiscordModeName();
        if (!configured.isBlank()) {
            return configured;
        }
        return pretty(localPrefix());
    }

    public CompletableFuture<Void> heartbeat() {
        MongoCollection<Document> modes = modes();
        if (modes == null) {
            return CompletableFuture.completedFuture(null);
        }
        Document doc = new Document("prefix", localPrefix())
            .append("name", localName())
            .append("lastSeen", Date.from(Instant.now()))
            .append("onlinePlayers", org.bukkit.Bukkit.getOnlinePlayers().size());
        return plugin.getMongoManager().toFuture(
            modes.replaceOne(Filters.eq("_id", localPrefix()), doc.append("_id", localPrefix()),
                new com.mongodb.client.model.ReplaceOptions().upsert(true))
        ).thenApply(result -> null);
    }

    public CompletableFuture<Boolean> claimLeadership() {
        String token = token();
        if (token.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        MongoCollection<Document> leaders = leaders();
        if (leaders == null) {
            return CompletableFuture.completedFuture(true);
        }
        Date stale = Date.from(Instant.now().minusMillis(LEADER_TTL_MS));
        Date now = Date.from(Instant.now());
        return plugin.getMongoManager().toFuture(
            leaders.findOneAndUpdate(
                Filters.and(
                    Filters.eq("_id", "discord-bot"),
                    Filters.or(
                        Filters.eq("prefix", localPrefix()),
                        Filters.lt("lastSeen", stale),
                        Filters.exists("prefix", false)
                    )
                ),
                Updates.combine(
                    Updates.set("prefix", localPrefix()),
                    Updates.set("name", localName()),
                    Updates.set("lastSeen", now)
                ),
                new FindOneAndUpdateOptions().upsert(false).returnDocument(ReturnDocument.AFTER)
            )
        ).thenCompose(doc -> {
            if (doc != null) {
                return CompletableFuture.completedFuture(localPrefix().equals(doc.getString("prefix")));
            }
            return plugin.getMongoManager().toFuture(leaders.find(Filters.eq("_id", "discord-bot")).first())
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    Document created = new Document("_id", "discord-bot")
                        .append("prefix", localPrefix())
                        .append("name", localName())
                        .append("lastSeen", now);
                    return plugin.getMongoManager().toFuture(leaders.insertOne(created))
                        .thenApply(result -> true)
                        .exceptionally(error -> {
                            if (isDuplicateKey(error)) {
                                return false;
                            }
                            plugin.getLogger().warning("[Backup DC] Nie udało się wybrać lidera bota: " + error.getMessage());
                            return false;
                        });
                });
        }).exceptionally(error -> {
            if (isDuplicateKey(error)) {
                return false;
            }
            plugin.getLogger().warning("[Backup DC] Nie udało się wybrać lidera bota: " + error.getMessage());
            return false;
        });
    }

    private static boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof com.mongodb.MongoCommandException command && command.getErrorCode() == 11000) {
                return true;
            }
            if (current instanceof com.mongodb.MongoWriteException write && write.getError() != null && write.getError().getCode() == 11000) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (message.contains("E11000") || normalized.contains("duplicate key")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public CompletableFuture<List<Mode>> onlineModes() {
        MongoCollection<Document> modes = modes();
        if (modes == null) {
            return CompletableFuture.completedFuture(List.of(local()));
        }
        Date fresh = Date.from(Instant.now().minusMillis(MODE_TTL_MS));
        return plugin.getMongoManager().toListFuture(
            modes.find(Filters.gte("lastSeen", fresh))
        ).thenApply(docs -> {
            List<Mode> out = new ArrayList<>();
            boolean self = false;
            for (Document doc : docs) {
                String prefix = doc.getString("prefix");
                if (prefix == null || prefix.isBlank()) {
                    prefix = doc.getString("_id");
                }
                if (prefix == null || prefix.isBlank()) {
                    continue;
                }
                String name = doc.getString("name");
                out.add(new Mode(prefix, name == null || name.isBlank() ? pretty(prefix) : name));
                if (prefix.equalsIgnoreCase(localPrefix())) {
                    self = true;
                }
            }
            if (!self) {
                out.add(local());
            }
            out.sort(Comparator.comparing(mode -> mode.name().toLowerCase(Locale.ROOT)));
            return out;
        }).exceptionally(error -> List.of(local()));
    }

    public Mode local() {
        return new Mode(localPrefix(), localName());
    }

    public Mode resolve(String raw, List<Mode> modes) {
        if (raw == null || raw.isBlank()) {
            return modes.size() == 1 ? modes.get(0) : null;
        }
        String needle = raw.trim();
        for (Mode mode : modes) {
            if (mode.prefix().equalsIgnoreCase(needle) || mode.name().equalsIgnoreCase(needle)) {
                return mode;
            }
        }
        String lower = needle.toLowerCase(Locale.ROOT);
        Mode partial = null;
        for (Mode mode : modes) {
            if (mode.prefix().toLowerCase(Locale.ROOT).contains(lower)
                || mode.name().toLowerCase(Locale.ROOT).contains(lower)) {
                if (partial != null) {
                    return null;
                }
                partial = mode;
            }
        }
        return partial;
    }

    public CompletableFuture<String> submitJob(String mode, String action, String deathId, String staff) {
        MongoCollection<Document> jobs = jobs();
        if (jobs == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Brak rejestru Mongo dla trybów."));
        }
        Document doc = new Document("_id", new ObjectId())
            .append("mode", mode)
            .append("action", action)
            .append("deathId", deathId)
            .append("staff", staff)
            .append("status", "pending")
            .append("createdAt", Date.from(Instant.now()));
        ObjectId id = doc.getObjectId("_id");
        return plugin.getMongoManager().toFuture(jobs.insertOne(doc)).thenCompose(result -> waitForJob(id));
    }

    public CompletableFuture<Void> processLocalJobs() {
        MongoCollection<Document> jobs = jobs();
        if (jobs == null) {
            return CompletableFuture.completedFuture(null);
        }
        // Tylko jeden przebieg naraz — reload odpala tickDiscordHub obok cyklicznego taska
        // i oba widzialy te same "pending" zadania, co dawalo podwojne nadanie EQ.
        if (!processing.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getMongoManager().toListFuture(
            jobs.find(Filters.and(
                Filters.eq("mode", localPrefix()),
                Filters.eq("status", "pending")
            )).limit(8)
        ).thenCompose(docs -> {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (Document doc : docs) {
                chain = chain.thenCompose(v -> runJob(doc));
            }
            return chain;
        }).whenComplete((result, error) -> processing.set(false));
    }

    private CompletableFuture<Void> runJob(Document doc) {
        ObjectId id = doc.getObjectId("_id");
        String action = doc.getString("action");
        String deathId = doc.getString("deathId");
        String staff = doc.getString("staff");
        UUID staffUuid = UUID.nameUUIDFromBytes(("discord:" + staff).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MongoCollection<Document> jobs = jobs();
        if (jobs == null || id == null) {
            return CompletableFuture.completedFuture(null);
        }
        // Atomowe przejecie zadania: kto pierwszy przestawi "pending" -> "running", ten je wykonuje.
        return plugin.getMongoManager().toFuture(
            jobs.findOneAndUpdate(
                Filters.and(
                    Filters.eq("_id", id),
                    Filters.eq("status", "pending")
                ),
                Updates.combine(
                    Updates.set("status", "running"),
                    Updates.set("startedAt", Date.from(Instant.now()))
                ),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            )
        ).thenCompose(claimed -> {
            if (claimed == null) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            return backupManager.getDeathById(deathId).thenCompose(death -> {
                if (death == null) {
                    return finishJob(id, "error", "Nie znaleziono backupu");
                }
                CompletableFuture<Void> work = CompletableFuture.completedFuture(null);
                if ("eq".equals(action) || "all".equals(action)) {
                    work = work.thenCompose(v -> backupManager.grantBackup(
                        death.playerUuid(), death.playerName(), death.id(), staffUuid, staffUuid
                    ));
                }
                if ("stats".equals(action) || "all".equals(action)) {
                    work = work.thenCompose(v -> backupManager.restoreStats(death, staffUuid).thenApply(ok -> null));
                }
                return work.thenCompose(v -> finishJob(id, "done", null))
                    .exceptionally(error -> {
                        finishJob(id, "error", error.getMessage());
                        return null;
                    });
            });
        });
    }

    private CompletableFuture<Void> finishJob(ObjectId id, String status, String error) {
        MongoCollection<Document> jobs = jobs();
        if (jobs == null || id == null) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getMongoManager().toFuture(
            jobs.updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                    Updates.set("status", status),
                    Updates.set("error", error),
                    Updates.set("finishedAt", Date.from(Instant.now()))
                )
            )
        ).thenApply(result -> null);
    }

    private CompletableFuture<String> waitForJob(ObjectId id) {
        if (id == null) {
            return CompletableFuture.completedFuture("ok");
        }
        return pollJob(id, 0);
    }

    private CompletableFuture<String> pollJob(ObjectId id, int attempt) {
        if (attempt > 30) {
            return CompletableFuture.completedFuture("Tryb nie odpowiedział w czasie. Sprawdź, czy serwer tego trybu działa.");
        }
        MongoCollection<Document> jobs = jobs();
        if (jobs == null) {
            return CompletableFuture.completedFuture("ok");
        }
        return plugin.getMongoManager().toFuture(jobs.find(Filters.eq("_id", id)).first())
            .thenCompose(doc -> {
                if (doc == null) {
                    return CompletableFuture.completedFuture("ok");
                }
                String status = doc.getString("status");
                if ("done".equals(status)) {
                    return CompletableFuture.completedFuture("ok");
                }
                if ("error".equals(status)) {
                    String error = doc.getString("error");
                    return CompletableFuture.completedFuture(error == null ? "Błąd nadawania" : error);
                }
                CompletableFuture<String> delay = new CompletableFuture<>();
                org.bukkit.Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () ->
                    pollJob(id, attempt + 1).whenComplete((value, error) -> {
                        if (error != null) {
                            delay.completeExceptionally(error);
                        } else {
                            delay.complete(value);
                        }
                    }), 10L);
                return delay;
            });
    }

    private MongoCollection<Document> modes() {
        MongoDatabase database = registry();
        return database == null ? null : database.getCollection("backup_modes");
    }

    private MongoCollection<Document> leaders() {
        MongoDatabase database = registry();
        return database == null ? null : database.getCollection("backup_discord_leader");
    }

    private MongoCollection<Document> jobs() {
        MongoDatabase database = registry();
        return database == null ? null : database.getCollection("backup_discord_jobs");
    }

    private MongoDatabase registry() {
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return null;
        }
        return plugin.getMongoManager().getDatabase(plugin.getConfigManager().getBackupConfig().getDiscordRegistryDatabase());
    }

    private String token() {
        String token = plugin.getConfigManager().getBackupConfig().getDiscordBotToken();
        return token == null ? "" : token.trim();
    }

    static String pretty(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "hollycore";
        }
        int dash = prefix.lastIndexOf('-');
        String tail = dash >= 0 && dash < prefix.length() - 1 ? prefix.substring(dash + 1) : prefix;
        return tail.replace('_', ' ');
    }

    public record Mode(String prefix, String name) {
        public String label() {
            if (name.equalsIgnoreCase(prefix)) {
                return name;
            }
            return name + " · " + prefix;
        }
    }
}
