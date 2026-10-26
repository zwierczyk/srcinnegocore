package dev.grzybalson.modules.masakraszafka;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.database.MongoManager;
import org.bson.Document;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SzafkaStorage {
    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);

    private final HollyCore plugin;
    private final Map<UUID, CompletableFuture<Void>> queues = new ConcurrentHashMap<>();

    SzafkaStorage(HollyCore plugin) {
        this.plugin = plugin;
        if (plugin.getMongoManager() == null) plugin.getLogger().severe("[masakra-szafka] Brak MongoManager — szafka nie będzie zapisywana.");
    }

    boolean available() {
        MongoManager mongo = plugin.getMongoManager();
        return mongo != null && mongo.isAvailable();
    }

    CompletableFuture<SzafkaProfile> load(UUID uuid) {
        return enqueue(uuid, () -> mongo().toFuture(collection("masakra_szafka").find(Filters.eq("_id", uuid.toString())).first())
            .thenApply(SzafkaProfile::from));
    }

    CompletableFuture<Void> save(UUID uuid, Document snapshot) {
        return enqueue(uuid, () -> mongo().toFuture(collection("masakra_szafka").replaceOne(Filters.eq("_id", uuid.toString()), snapshot, UPSERT))
            .thenApply(ignored -> null));
    }

    CompletableFuture<Void> update(UUID uuid, Consumer<SzafkaProfile> mutation) {
        return enqueue(uuid, () -> {
            MongoCollection<Document> profiles = collection("masakra_szafka");
            return mongo().toFuture(profiles.find(Filters.eq("_id", uuid.toString())).first())
                .thenCompose(document -> {
                    SzafkaProfile profile = SzafkaProfile.from(document);
                    mutation.accept(profile);
                    return mongo().toFuture(profiles.replaceOne(Filters.eq("_id", uuid.toString()), profile.toDocument(uuid), UPSERT));
                })
                .thenApply(ignored -> null);
        });
    }

    CompletableFuture<Boolean> claimVoucher(UUID voucherId, UUID player, SzafkaItem item, long duration) {
        MongoCollection<Document> vouchers;
        try {
            vouchers = collection("masakra_szafka_vouchers");
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        Document document = new Document("_id", voucherId.toString())
            .append("player", player.toString())
            .append("kind", item.kind().label)
            .append("item", item.id())
            .append("duration", duration)
            .append("claimed", System.currentTimeMillis());
        return mongo().toFuture(vouchers.insertOne(document)).handle((result, error) -> {
            if (error == null) return true;
            if (isDuplicate(error)) return false;
            throw new CompletionException(error);
        });
    }

    void flush(long timeoutMillis) {
        try {
            CompletableFuture.allOf(queues.values().toArray(CompletableFuture[]::new)).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("[masakra-szafka] Nie wszystkie zapisy zdążyły się zakończyć: " + exception.getMessage());
        }
    }

    private <T> CompletableFuture<T> enqueue(UUID uuid, Supplier<CompletableFuture<T>> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<Void> tail = new CompletableFuture<>();
        CompletableFuture<Void> previous = queues.put(uuid, tail);
        (previous == null ? CompletableFuture.<Void>completedFuture(null) : previous).whenComplete((ignored, ignoredError) -> {
            CompletableFuture<T> running;
            try {
                running = task.get();
            } catch (RuntimeException exception) {
                running = CompletableFuture.failedFuture(exception);
            }
            running.whenComplete((value, error) -> {
                queues.remove(uuid, tail);
                tail.complete(null);
                if (error != null) result.completeExceptionally(error);
                else result.complete(value);
            });
        });
        return result;
    }

    private MongoManager mongo() {
        MongoManager mongo = plugin.getMongoManager();
        if (mongo == null) throw new IllegalStateException("MongoDB niedostępne");
        return mongo;
    }

    private MongoCollection<Document> collection(String name) {
        String prefix = plugin.getConfigManager().getDatabaseConfig().getDbPrefix();
        return mongo().getCollection(prefix != null && !prefix.isBlank() ? prefix + "_" + name : name);
    }

    private static boolean isDuplicate(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof MongoWriteException write && write.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) return true;
        }
        return false;
    }
}
