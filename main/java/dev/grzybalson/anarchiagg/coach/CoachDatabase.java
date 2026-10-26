package dev.grzybalson.anarchiagg.coach;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.coach.data.PlayerCoachData;
import org.bson.Document;
import org.reactivestreams.Publisher;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoachDatabase {

    private final HollyCore plugin;
    private final CoachConfig config;
    private MongoClient customClient;
    private MongoCollection<Document> collection;

    public CoachDatabase(HollyCore plugin, CoachConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.connect();
    }

    public CoachDatabase(HollyCore plugin) {
        this(plugin, plugin != null ? plugin.getCoachConfig() : null);
    }

    public synchronized void connect() {
        close();
        if (config == null || config.getMongodb() == null || !config.getMongodb().isEnabled()) {
            return;
        }

        CoachConfig.MongoDbSettings settings = config.getMongodb();
        String uri = settings.getUri();
        String targetCollection = settings.getCollection();

        if (uri != null && !uri.isBlank()) {
            try {
                ConnectionString connString = new ConnectionString(uri);
                MongoClientSettings clientSettings = MongoClientSettings.builder()
                        .applyConnectionString(connString)
                        .build();
                this.customClient = MongoClients.create(clientSettings);

                String dbName = settings.getDatabase();
                if (dbName == null || dbName.isBlank()) {
                    dbName = connString.getDatabase();
                }
                if (dbName == null || dbName.isBlank()) {
                    dbName = (plugin != null && plugin.getConfigManager() != null && plugin.getConfigManager().getDatabaseConfig() != null)
                            ? plugin.getConfigManager().getDatabaseConfig().getDatabaseName()
                            : "hollycore";
                }

                MongoDatabase db = this.customClient.getDatabase(dbName);
                this.collection = db.getCollection(targetCollection);
                if (plugin != null) {
                    plugin.getLogger().info("[Trener] Polaczono z wlasna baza MongoDB: " + dbName + " (kolekcja: " + targetCollection + ")");
                }
                return;
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().warning("[Trener] Blad laczenia z wlasnym MongoDB URI: " + e.getMessage() + ", probuje uzyc bazy glownej...");
                }
            }
        }

        if (plugin != null && plugin.getMongoManager() != null) {
            try {
                String dbName = settings.getDatabase();
                if (dbName != null && !dbName.isBlank()) {
                    MongoDatabase db = plugin.getMongoManager().getDatabase(dbName);
                    if (db != null) {
                        this.collection = db.getCollection(targetCollection);
                    } else {
                        this.collection = plugin.getMongoManager().getCollection(targetCollection);
                    }
                } else {
                    this.collection = plugin.getMongoManager().getCollection(targetCollection);
                }
            } catch (Exception e) {
                if (plugin != null) {
                    plugin.getLogger().warning("[Trener] Nie udalo sie zainicjalizowac kolekcji Trenera w MongoDB: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void close() {
        if (customClient != null) {
            try {
                customClient.close();
            } catch (Exception ignored) {
            }
            customClient = null;
        }
        collection = null;
    }

    private <T> CompletableFuture<T> toFuture(Publisher<T> publisher) {
        if (plugin != null && plugin.getMongoManager() != null) {
            return plugin.getMongoManager().toFuture(publisher);
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        publisher.subscribe(new org.reactivestreams.Subscriber<T>() {
            private T result;

            @Override
            public void onSubscribe(org.reactivestreams.Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(T t) {
                this.result = t;
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(result);
            }
        });
        return future;
    }

    public PlayerCoachData load(UUID uuid) {
        PlayerCoachData data = new PlayerCoachData(uuid);
        if (collection == null) {
            data.markLoadFailed();
            return data;
        }

        try {
            Document doc = toFuture(
                    collection.find(Filters.eq("_id", uuid.toString())).first()
            ).join();

            if (doc == null) {
                data.markLoadedFromDatabase();
                return data;
            }

            data.setStrengthKills(doc.getInteger("strengthKills", 0));
            Document levels = doc.get("levels", Document.class);
            if (levels != null) {
                for (CoachSkill skill : CoachSkill.values()) {
                    data.setLevel(skill, levels.getInteger(skill.getId(), 0));
                }
            }
            Document kills = doc.get("kills", Document.class);
            if (kills != null) {
                for (CoachSkill skill : CoachSkill.values()) {
                    data.setKills(skill, kills.getInteger(skill.getId(), data.getKills(skill)));
                }
            }
            data.markLoadedFromDatabase();
        } catch (Throwable exception) {
            data.markLoadFailed();
            if (plugin != null) {
                plugin.getLogger().warning("[Trener] Blad wczytywania danych " + uuid + ": " + exception.getMessage());
            }
        }
        return data;
    }

    public void save(PlayerCoachData data) {
        if (collection == null || data == null || !data.canPersist()) {
            return;
        }

        Document levels = new Document();
        Document kills = new Document();
        for (CoachSkill skill : CoachSkill.values()) {
            levels.append(skill.getId(), data.getLevel(skill));
            kills.append(skill.getId(), data.getKills(skill));
        }

        Document doc = new Document("_id", data.getUuid().toString())
                .append("levels", levels)
                .append("kills", kills)
                .append("strengthKills", data.getStrengthKills());

        toFuture(
                collection.replaceOne(Filters.eq("_id", data.getUuid().toString()), doc, new ReplaceOptions().upsert(true))
        );
    }

    public void reset(UUID uuid) {
        if (collection == null) {
            return;
        }
        toFuture(
                collection.deleteOne(Filters.eq("_id", uuid.toString()))
        );
    }

    public MongoCollection<Document> getCollection() {
        return collection;
    }
}
