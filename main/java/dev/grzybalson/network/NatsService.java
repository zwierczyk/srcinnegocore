package dev.grzybalson.network;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.DatabaseConfig;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class NatsService {

    public static final String MARKET_UPDATE = "hollycore.market.update";
    public static final String MARKET_ADD = "hollycore.market.add";
    public static final String MARKET_BUY = "hollycore.market.buy";
    public static final String ACHIEVEMENT_CLAIM = "hollycore.achievement.claim";
    public static final String SECTOR_HEARTBEAT = "hollycore.sector.heartbeat";
    public static final String UNIQUE_REMOVE = "hollycore.unique.remove";
    public static final String MSLF_ENDERCHEST = "hollycore.mslf.enderchest";
    public static final String SECTOR_TRANSFER_PREFIX = "hollycore.sector.transfer.";

    private final HollyCore plugin;
    private final Map<String, Dispatcher> dispatchers = new ConcurrentHashMap<>();
    private volatile Connection connection;

    public NatsService(HollyCore plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        DatabaseConfig.NatsSettings nats = plugin.getConfigManager().getDatabaseConfig().getNats();
        try {
            Options options = new Options.Builder()
                .server(nats.toUri())
                .connectionName("hollycore-" + plugin.getServer().getPort())
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(2))
                .pingInterval(Duration.ofSeconds(10))
                .build();
            connection = Nats.connect(options);
            plugin.getLogger().info("Connected to NATS at " + nats.toUri());
            return true;
        } catch (Exception exception) {
            connection = null;
            plugin.getLogger().warning("NATS is unavailable (" + nats.toUri() + "). Local features still work, live sector sync is off. " + exception.getMessage());
            return false;
        }
    }

    public boolean isConnected() {
        return connection != null && connection.getStatus() == Connection.Status.CONNECTED;
    }

    public void publish(String subject, Document payload) {
        if (!isConnected() || payload == null) {
            return;
        }
        try {
            connection.publish(subject, payload.toJson().getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to publish NATS " + subject + ": " + exception.getMessage());
        }
    }

    public void subscribe(String subject, Consumer<Document> handler) {
        if (!isConnected() || dispatchers.containsKey(subject)) {
            return;
        }
        Dispatcher dispatcher = connection.createDispatcher(message -> {
            try {
                Document document = Document.parse(new String(message.getData(), StandardCharsets.UTF_8));
                Bukkit.getScheduler().runTask(plugin, () -> handler.accept(document));
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to handle NATS " + subject + ": " + exception.getMessage());
            }
        });
        dispatcher.subscribe(subject);
        dispatchers.put(subject, dispatcher);
    }

    public CompletableFuture<Document> request(String subject, Document payload, long timeoutMs) {
        if (!isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("NATS offline"));
        }
        byte[] data = payload.toJson().getBytes(StandardCharsets.UTF_8);
        return CompletableFuture.supplyAsync(() -> {
            try {
                Message message = connection.request(subject, data, Duration.ofMillis(timeoutMs));
                if (message == null || message.getData() == null) {
                    return null;
                }
                return Document.parse(new String(message.getData(), StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CompletionException(interrupted);
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        });
    }

    public void reply(String replyTo, Document payload) {
        if (!isConnected() || replyTo == null || replyTo.isBlank()) {
            return;
        }
        try {
            connection.publish(replyTo, payload.toJson().getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to reply on NATS: " + exception.getMessage());
        }
    }

    public void subscribeReplies(String subject, ReplyHandler handler) {
        if (!isConnected() || dispatchers.containsKey(subject)) {
            return;
        }
        Dispatcher dispatcher = connection.createDispatcher(message -> {
            try {
                Document document = Document.parse(new String(message.getData(), StandardCharsets.UTF_8));
                Bukkit.getScheduler().runTask(plugin, () -> handler.handle(document, message.getReplyTo()));
            } catch (Exception exception) {
                plugin.getLogger().warning("Failed to handle NATS reply " + subject + ": " + exception.getMessage());
            }
        });
        dispatcher.subscribe(subject);
        dispatchers.put(subject, dispatcher);
    }

    public void shutdown() {
        dispatchers.clear();
        Connection current = connection;
        connection = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
            }
        }
    }

    @FunctionalInterface
    public interface ReplyHandler {
        void handle(Document document, String replyTo);
    }
}
