package dev.grzybalson.modules.discordreward;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class DiscordRewardClaimDataManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;

    public DiscordRewardClaimDataManager(HollyCore plugin, String collectionName) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection(collectionName);
        ensureIndexes();
    }

    public CompletableFuture<ClaimResult> claim(String discordId, UUID minecraftUuid, String minecraftName, String rewardType) {
        Document document = new Document("discordId", discordId)
            .append("minecraftUuid", minecraftUuid.toString())
            .append("minecraftName", minecraftName)
            .append("minecraftNameLower", normalizeName(minecraftName))
            .append("reward", rewardType)
            .append("claimedAt", Instant.now().toString());

        return plugin.getMongoManager().toFuture(collection.insertOne(document))
            .thenApply(result -> ClaimResult.SUCCESS)
            .exceptionally(throwable -> {
                if (isDuplicateKey(throwable)) {
                    return ClaimResult.ALREADY_CLAIMED;
                }
                throw new CompletionException(throwable);
            });
    }

    public CompletableFuture<Boolean> hasMinecraftClaim(UUID minecraftUuid, String minecraftName) {
        return plugin.getMongoManager().toFuture(collection.find(Filters.or(
            Filters.eq("minecraftUuid", minecraftUuid.toString()),
            Filters.eq("minecraftNameLower", normalizeName(minecraftName))
        )).first()).thenApply(document -> document != null);
    }

    private void ensureIndexes() {
        createUniqueIndex("discordId");
        createUniqueIndex("minecraftUuid");
        createUniqueIndex("minecraftNameLower");
    }

    private void createUniqueIndex(String field) {
        plugin.getMongoManager().toFuture(collection.createIndex(
            Indexes.ascending(field),
            new IndexOptions().unique(true).name("unique_" + field)
        )).exceptionally(throwable -> {
            plugin.getLogger().warning("Nie udalo sie utworzyc indeksu Mongo dla nagrody Discord (" + field + "): " + throwable.getMessage());
            return null;
        });
    }

    private boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
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

    private String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED
    }
}
