package dev.grzybalson.modules.coinflip;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoinflipDatabase {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    private final HollyCore plugin;
    private final MongoCollection<Document> rewardsCollection;
    private final MongoCollection<Document> gamesCollection;

    public CoinflipDatabase(HollyCore plugin, String rewardsCollectionName, String gamesCollectionName) {
        this.plugin = plugin;
        String safeRewardsCollection = rewardsCollectionName == null || rewardsCollectionName.isBlank() ? "coinflip_rewards" : rewardsCollectionName;
        String safeGamesCollection = gamesCollectionName == null || gamesCollectionName.isBlank() ? "coinflip_games" : gamesCollectionName;
        this.rewardsCollection = plugin.getMongoManager().getCollection(safeRewardsCollection);
        this.gamesCollection = plugin.getMongoManager().getCollection(safeGamesCollection);

        plugin.getMongoManager().toFuture(this.rewardsCollection.createIndex(new Document("nameLower", 1)));
        plugin.getMongoManager().toFuture(this.gamesCollection.createIndex(new Document("status", 1).append("createdAt", -1)));
        plugin.getMongoManager().toFuture(this.gamesCollection.createIndex(new Document("creatorUuid", 1).append("status", 1)));
    }

    public CompletableFuture<CoinflipRewards> getRewards(String playerName) {
        return plugin.getMongoManager().toFuture(rewardsCollection.find(Filters.eq("_id", id(playerName))).first())
                .thenApply(doc -> {
                    if (doc == null) {
                        return new CoinflipRewards(0.0, 0.0);
                    }
                    return new CoinflipRewards(
                            readDouble(doc, CoinflipCurrency.MONEY.getDatabaseField()),
                            readDouble(doc, CoinflipCurrency.SHARDS.getDatabaseField())
                    );
                });
    }

    public CompletableFuture<Void> addReward(String playerName, CoinflipCurrency currency, double amount) {
        if (amount <= 0.0) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getMongoManager().toFuture(
                rewardsCollection.updateOne(
                        Filters.eq("_id", id(playerName)),
                        Updates.combine(
                                Updates.inc(currency.getDatabaseField(), amount),
                                Updates.set("name", playerName),
                                Updates.set("nameLower", id(playerName)),
                                Updates.set("updatedAt", System.currentTimeMillis())
                        ),
                        new UpdateOptions().upsert(true)
                )
        ).thenApply(r -> null);
    }

    public CompletableFuture<Double> claimReward(String playerName, CoinflipCurrency currency) {
        return plugin.getMongoManager().toFuture(
                rewardsCollection.findOneAndUpdate(
                        Filters.and(
                                Filters.eq("_id", id(playerName)),
                                Filters.gt(currency.getDatabaseField(), 0.0)
                        ),
                        Updates.combine(
                                Updates.set(currency.getDatabaseField(), 0.0),
                                Updates.set("name", playerName),
                                Updates.set("nameLower", id(playerName)),
                                Updates.set("updatedAt", System.currentTimeMillis())
                        ),
                        new FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE)
                )
        ).thenApply(before -> before == null ? 0.0 : readDouble(before, currency.getDatabaseField()));
    }

    public CompletableFuture<List<GameSession>> loadOpenGames() {
        return plugin.getMongoManager().toListFuture(
                gamesCollection.find(Filters.eq("status", STATUS_OPEN)).sort(Sorts.descending("createdAt"))
        ).thenApply(docs -> {
            List<GameSession> output = new ArrayList<>();
            for (Document doc : docs) {
                GameSession session = sessionFromDocument(doc);
                if (session != null) {
                    output.add(session);
                }
            }
            return output;
        });
    }

    public CompletableFuture<List<StoredGame>> loadInProgressGames() {
        return plugin.getMongoManager().toListFuture(
                gamesCollection.find(Filters.eq("status", STATUS_IN_PROGRESS))
        ).thenApply(docs -> {
            List<StoredGame> output = new ArrayList<>();
            for (Document doc : docs) {
                StoredGame game = storedGameFromDocument(doc);
                if (game != null && game.opponentUuid() != null && game.opponentName() != null && !game.opponentName().isBlank()) {
                    output.add(game);
                }
            }
            return output;
        });
    }

    public CompletableFuture<Void> saveGame(GameSession session) {
        if (session == null) {
            return CompletableFuture.completedFuture(null);
        }

        return plugin.getMongoManager().toFuture(
                gamesCollection.updateOne(
                        Filters.eq("_id", session.getId()),
                        Updates.combine(
                                Updates.set("creatorUuid", session.getCreatorUuid().toString()),
                                Updates.set("creatorName", session.getCreatorName()),
                                Updates.set("creatorNameLower", id(session.getCreatorName())),
                                Updates.set("currency", session.getCurrency().name()),
                                Updates.set("amount", session.getAmount()),
                                Updates.set("status", STATUS_OPEN),
                                Updates.set("createdAt", session.getCreatedAtMillis()),
                                Updates.set("updatedAt", System.currentTimeMillis())
                        ),
                        new UpdateOptions().upsert(true)
                )
        ).thenApply(r -> null);
    }

    public CompletableFuture<Boolean> markGameInProgress(GameSession session, UUID opponentUuid, String opponentName) {
        if (session == null || opponentUuid == null || opponentName == null || opponentName.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getMongoManager().toFuture(
                gamesCollection.updateOne(
                        Filters.and(
                                Filters.eq("_id", session.getId()),
                                Filters.eq("status", STATUS_OPEN)
                        ),
                        Updates.combine(
                                Updates.set("status", STATUS_IN_PROGRESS),
                                Updates.set("opponentUuid", opponentUuid.toString()),
                                Updates.set("opponentName", opponentName),
                                Updates.set("opponentNameLower", id(opponentName)),
                                Updates.set("updatedAt", System.currentTimeMillis())
                        )
                )
        ).thenApply(result -> result != null && result.getModifiedCount() > 0);
    }

    public CompletableFuture<Void> reopenGame(String id) {
        return plugin.getMongoManager().toFuture(
                gamesCollection.updateOne(
                        Filters.and(
                                Filters.eq("_id", id),
                                Filters.eq("status", STATUS_IN_PROGRESS)
                        ),
                        Updates.combine(
                                Updates.set("status", STATUS_OPEN),
                                Updates.unset("opponentUuid"),
                                Updates.unset("opponentName"),
                                Updates.unset("opponentNameLower"),
                                Updates.set("updatedAt", System.currentTimeMillis())
                        )
                )
        ).thenApply(r -> null);
    }

    public CompletableFuture<Void> closeGame(String id, String status) {
        String safeStatus = status == null || status.isBlank() ? "FINISHED" : status;
        return plugin.getMongoManager().toFuture(
                gamesCollection.updateOne(
                        Filters.eq("_id", id),
                        Updates.combine(
                                Updates.set("status", safeStatus),
                                Updates.set("closedAt", System.currentTimeMillis()),
                                Updates.set("updatedAt", System.currentTimeMillis())
                        )
                )
        ).thenApply(r -> null);
    }

    public CompletableFuture<Boolean> cancelOpenGame(GameSession session) {
        if (session == null) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getMongoManager().toFuture(
                gamesCollection.updateOne(
                        Filters.and(
                                Filters.eq("_id", session.getId()),
                                Filters.eq("creatorUuid", session.getCreatorUuid().toString()),
                                Filters.eq("status", STATUS_OPEN)
                        ),
                        Updates.combine(
                                Updates.set("status", "CANCELLED"),
                                Updates.set("closedAt", System.currentTimeMillis()),
                                Updates.set("updatedAt", System.currentTimeMillis())
                        )
                )
        ).thenApply(result -> result != null && result.getModifiedCount() > 0);
    }

    public void shutdown() {
    }

    private double readDouble(Document document, String field) {
        Object value = document.get(field);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private long readLong(Document document, String field) {
        Object value = document.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return System.currentTimeMillis();
    }

    private GameSession sessionFromDocument(Document doc) {
        StoredGame game = storedGameFromDocument(doc);
        if (game == null) {
            return null;
        }

        return new GameSession(
                game.id(),
                game.creatorUuid(),
                game.creatorName(),
                game.currency(),
                game.amount(),
                game.createdAtMillis()
        );
    }

    private StoredGame storedGameFromDocument(Document doc) {
        if (doc == null) {
            return null;
        }

        try {
            String id = doc.getString("_id");
            String creatorUuid = doc.getString("creatorUuid");
            String creatorName = doc.getString("creatorName");
            String currencyName = doc.getString("currency");
            if (id == null || creatorUuid == null || creatorName == null || currencyName == null) {
                return null;
            }

            CoinflipCurrency currency = CoinflipCurrency.valueOf(currencyName);
            double amount = readDouble(doc, "amount");
            if (amount <= 0.0) {
                return null;
            }

            UUID opponentUuid = uuidOrNull(doc.getString("opponentUuid"));
            return new StoredGame(
                    id,
                    UUID.fromString(creatorUuid),
                    creatorName,
                    currency,
                    amount,
                    readLong(doc, "createdAt"),
                    opponentUuid,
                    doc.getString("opponentName")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String id(String playerName) {
        if (playerName == null) {
            return "";
        }
        return playerName.toLowerCase(Locale.ROOT);
    }

    public record StoredGame(String id, UUID creatorUuid, String creatorName, CoinflipCurrency currency, double amount,
                             long createdAtMillis, UUID opponentUuid, String opponentName) {
    }
}
