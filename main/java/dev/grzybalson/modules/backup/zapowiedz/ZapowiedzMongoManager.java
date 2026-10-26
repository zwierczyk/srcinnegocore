package dev.grzybalson.modules.backup.zapowiedz;

import com.mongodb.MongoCredential;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.BackupConfig;
import org.bson.Document;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ZapowiedzMongoManager {

    private static final long PING_TIMEOUT_MS = 15_000L;

    private final HollyCore plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private volatile boolean available;

    public ZapowiedzMongoManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        available = false;
        close(false);

        BackupConfig.Zapowiedz.Mongo mongo = plugin.getConfigManager().getBackupConfig().getZapowiedz().getMongo();
        if (mongo.getDbPrefix().isBlank()) {
            plugin.getLogger().warning("[Zapowiedz] MongoDB wyłączone: brak skonfigurowanej bazy danych (db-prefix).");
            return false;
        }

        try {
            MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .applyToConnectionPoolSettings(builder -> builder.maxSize(20).minSize(0))
                .applyToSocketSettings(builder ->
                    builder.connectTimeout(10_000, TimeUnit.MILLISECONDS)
                        .readTimeout(15_000, TimeUnit.MILLISECONDS))
                .applyToClusterSettings(builder ->
                    builder.hosts(List.of(new ServerAddress(mongo.getIp(), mongo.getPort())))
                        .serverSelectionTimeout(15_000, TimeUnit.MILLISECONDS));

            if (mongo.hasCredentials()) {
                settingsBuilder.credential(MongoCredential.createCredential(
                    mongo.getName(),
                    mongo.getAuthSource(),
                    mongo.getPass().toCharArray()
                ));
            }

            mongoClient = MongoClients.create(settingsBuilder.build());
            database = mongoClient.getDatabase(mongo.getDbPrefix());

            toFuture(database.runCommand(new Document("ping", 1)))
                .get(PING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            available = true;
            plugin.getLogger().info("[Zapowiedz] Połączono z MongoDB (" + mongo.getDbPrefix() + ").");
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("[Zapowiedz] MongoDB niedostępne: " + exception.getMessage());
            close(false);
            return false;
        }
    }

    public void disconnect() {
        available = false;
        close(true);
    }

    private void close(boolean log) {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
            database = null;
            if (log) {
                plugin.getLogger().info("[Zapowiedz] Rozłączono MongoDB.");
            }
        }
    }

    public boolean isAvailable() {
        return available && database != null;
    }

    public MongoCollection<Document> announcements() {
        requireDatabase();
        return database.getCollection("zapowiedz_announcements");
    }

    public MongoCollection<Document> registrations() {
        requireDatabase();
        return database.getCollection("zapowiedz_registrations");
    }

    public MongoCollection<Document> remindersSent() {
        requireDatabase();
        return database.getCollection("zapowiedz_reminders_sent");
    }

    private void requireDatabase() {
        if (database == null) {
            throw new IllegalStateException("Zapowiedz MongoDB nie jest połączone.");
        }
    }

    public <T> CompletableFuture<T> toFuture(Publisher<T> publisher) {
        CompletableFuture<T> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<>() {
            private T value;

            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(T t) {
                this.value = t;
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(value);
            }
        });
        return future;
    }

    public <T> CompletableFuture<List<T>> toListFuture(Publisher<T> publisher) {
        CompletableFuture<List<T>> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<>() {
            private final List<T> values = new java.util.ArrayList<>();

            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                values.add(t);
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(values);
            }
        });
        return future;
    }
}
