package dev.grzybalson.modules.backup.zapowiedz;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Updates;
import dev.grzybalson.HollyCore;
import org.bson.Document;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ZapowiedzRepository {

    private final HollyCore plugin;
    private final ZapowiedzMongoManager mongo;

    public ZapowiedzRepository(HollyCore plugin, ZapowiedzMongoManager mongo) {
        this.plugin = plugin;
        this.mongo = mongo;
    }

    public void ensureIndexes() {
        if (!mongo.isAvailable()) {
            return;
        }
        mongo.toFuture(mongo.registrations().createIndex(
            Indexes.compoundIndex(Indexes.ascending("messageId"), Indexes.ascending("discordUserId")),
            new IndexOptions().unique(true)
        ));
        mongo.toFuture(mongo.registrations().createIndex(
            Indexes.compoundIndex(Indexes.ascending("messageId"), Indexes.ascending("minecraftNickLower")),
            new IndexOptions().unique(true)
        ));
        mongo.toFuture(mongo.registrations().createIndex(Indexes.ascending("minecraftNickLower")));
        mongo.toFuture(mongo.registrations().createIndex(
            Indexes.compoundIndex(Indexes.ascending("minecraftNickLower"), Indexes.ascending("rewarded"))
        ));
        mongo.toFuture(mongo.remindersSent().createIndex(Indexes.ascending("reminderKey")));
    }

    public CompletableFuture<Void> saveAnnouncement(String messageId, String channelId, String guildId, String createdBy) {
        Document doc = new Document("_id", messageId)
            .append("channelId", channelId)
            .append("guildId", guildId)
            .append("createdBy", createdBy)
            .append("createdAt", Instant.now());
        return mongo.toFuture(
            mongo.announcements().replaceOne(Filters.eq("_id", messageId), doc, new ReplaceOptions().upsert(true))
        ).thenApply(result -> null);
    }

    public CompletableFuture<Long> countParticipants(String messageId) {
        return mongo.toFuture(mongo.registrations().countDocuments(Filters.eq("messageId", messageId)));
    }

    public CompletableFuture<Boolean> isDiscordRegistered(String messageId, String discordUserId) {
        return mongo.toFuture(
            mongo.registrations().countDocuments(Filters.and(
                Filters.eq("messageId", messageId),
                Filters.eq("discordUserId", discordUserId)
            ))
        ).thenApply(count -> count != null && count > 0);
    }

    public CompletableFuture<Boolean> isNickTaken(String messageId, String nickLower) {
        return mongo.toFuture(
            mongo.registrations().countDocuments(Filters.and(
                Filters.eq("messageId", messageId),
                Filters.eq("minecraftNickLower", nickLower)
            ))
        ).thenApply(count -> count != null && count > 0);
    }

    public CompletableFuture<Void> register(String messageId, String discordUserId, String minecraftNick) {
        String nickLower = minecraftNick.toLowerCase(Locale.ROOT);
        Document doc = new Document()
            .append("messageId", messageId)
            .append("discordUserId", discordUserId)
            .append("minecraftNick", minecraftNick)
            .append("minecraftNickLower", nickLower)
            .append("registeredAt", Instant.now())
            .append("rewarded", false);
        return mongo.toFuture(mongo.registrations().insertOne(doc)).thenApply(result -> null);
    }

    public CompletableFuture<List<Document>> findPendingRewards(String nickLower) {
        return mongo.toListFuture(
            mongo.registrations().find(Filters.and(
                Filters.eq("minecraftNickLower", nickLower),
                Filters.eq("rewarded", false)
            ))
        );
    }

    public CompletableFuture<Boolean> markRewarded(String registrationId) {
        return mongo.toFuture(
            mongo.registrations().updateOne(
                Filters.and(Filters.eq("_id", new org.bson.types.ObjectId(registrationId)), Filters.eq("rewarded", false)),
                Updates.combine(
                    Updates.set("rewarded", true),
                    Updates.set("rewardedAt", Instant.now())
                )
            )
        ).thenApply(result -> result != null && result.getModifiedCount() > 0);
    }

    public CompletableFuture<Document> getAnnouncement(String messageId) {
        return mongo.toFuture(mongo.announcements().find(Filters.eq("_id", messageId)).first());
    }

    public CompletableFuture<List<Document>> findAllRegistrations() {
        return mongo.toListFuture(mongo.registrations().find());
    }

    public CompletableFuture<Void> markReminderSent(String reminderKey, String discordUserId) {
        Document doc = new Document("_id", reminderKey + ":" + discordUserId)
            .append("reminderKey", reminderKey)
            .append("discordUserId", discordUserId)
            .append("sentAt", Instant.now());
        return mongo.toFuture(
            mongo.remindersSent().replaceOne(Filters.eq("_id", doc.getString("_id")), doc, new ReplaceOptions().upsert(true))
        ).thenApply(result -> null);
    }

    public CompletableFuture<List<RegistrationEntry>> findUnsentForReminder(String reminderKey) {
        CompletableFuture<List<Document>> registrationsFuture = findAllRegistrations();
        CompletableFuture<List<Document>> sentFuture = mongo.toListFuture(
            mongo.remindersSent().find(Filters.eq("reminderKey", reminderKey))
        );
        return registrationsFuture.thenCombine(sentFuture, (registrations, sentDocs) -> {
            java.util.Set<String> sentIds = new java.util.HashSet<>();
            for (Document sent : sentDocs) {
                String discordUserId = sent.getString("discordUserId");
                if (discordUserId != null) {
                    sentIds.add(discordUserId);
                }
            }
            java.util.LinkedHashMap<String, String> unique = new java.util.LinkedHashMap<>();
            for (Document registration : registrations) {
                String discordUserId = registration.getString("discordUserId");
                String minecraftNick = registration.getString("minecraftNick");
                if (discordUserId != null && minecraftNick != null) {
                    unique.putIfAbsent(discordUserId, minecraftNick);
                }
            }
            List<RegistrationEntry> unsent = new java.util.ArrayList<>();
            unique.forEach((discordUserId, minecraftNick) -> {
                if (!sentIds.contains(discordUserId)) {
                    unsent.add(new RegistrationEntry(discordUserId, minecraftNick));
                }
            });
            return unsent;
        });
    }

    public record RegistrationEntry(String discordUserId, String minecraftNick) {}
}
