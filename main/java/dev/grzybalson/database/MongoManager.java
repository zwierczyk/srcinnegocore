package dev.grzybalson.database;

import com.mongodb.MongoCredential;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.DatabaseConfig;
import dev.grzybalson.internal.HollyRuntime;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MongoManager {

    private static final long UNAVAILABLE_LOG_THROTTLE_MILLIS = 60_000L;
    private static volatile boolean driverLogFilterInstalled;

    private final HollyCore plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private volatile boolean available;
    private volatile Throwable lastFailure;
    private volatile long lastUnavailableLogAt;

    private final Object reconnectLock = new Object();
    private volatile boolean reconnecting;
    private final List<Runnable> availableListeners = new CopyOnWriteArrayList<>();

    public MongoManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        HollyRuntime.require(plugin, "mongo:connect");
        DatabaseConfig config = plugin.getConfigManager().getDatabaseConfig();
        configureDriverLogging(config);
        available = false;
        lastFailure = null;

        try {
            closeClient(true, false);

            CodecRegistry pojoCodecRegistry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
            );

            MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
                .codecRegistry(pojoCodecRegistry)
                .retryWrites(config.isRetryWrites())
                .retryReads(config.isRetryReads())
                .applyToConnectionPoolSettings(builder ->
                    builder.maxSize(config.getMaxPoolSize())
                           .minSize(config.getMinPoolSize())
                           .maxWaitTime(config.getMaxWaitTimeMs(), TimeUnit.MILLISECONDS)
                )
                .applyToSocketSettings(builder ->
                    builder.connectTimeout(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                           .readTimeout(config.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                )
                .applyToClusterSettings(builder ->
                    builder.hosts(List.of(new ServerAddress(config.getIp(), config.getPort())))
                           .serverSelectionTimeout(config.getServerSelectionTimeoutMs(), TimeUnit.MILLISECONDS)
                );

            if (config.hasCredentials()) {
                settingsBuilder.credential(MongoCredential.createCredential(
                    config.getName(),
                    config.getAuthSource(),
                    config.getPass().toCharArray()
                ));
            }

            MongoClientSettings settings = settingsBuilder.build();

            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(config.getDatabaseName());

            if (!ping(config)) {
                closeClient(false, false);
                triggerReconnect();
                return false;
            }

            available = true;
            lastFailure = null;
            plugin.getLogger().info("Connected to MongoDB successfully!");
            notifyAvailable();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to MongoDB: " + e.getMessage());
            lastFailure = e;
            available = false;
            closeClient(false, false);
            triggerReconnect();
            return false;
        }
    }

    public void disconnect() {
        available = false;
        closeClient(true, true);
    }

    private void closeClient(boolean clearDatabase, boolean log) {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
            if (clearDatabase) {
                database = null;
            }
            if (log) {
                plugin.getLogger().info("Disconnected from MongoDB.");
            }
        }
    }

    public MongoCollection<Document> getCollection(String name) {
        HollyRuntime.require(plugin, "mongo:collection:" + name);
        ensureDatabaseBound();
        if (database == null) {
            throw new IllegalStateException("MongoDB database is not initialized.");
        }
        return database.getCollection(name);
    }

    public MongoDatabase getDatabase() {
        HollyRuntime.require(plugin, "mongo:database");
        ensureDatabaseBound();
        if (database == null) {
            throw new IllegalStateException("MongoDB database is not initialized.");
        }
        return database;
    }

    /** Lazy bind gdy client już istnieje, a database nie zdążyło się ustawić. */
    private void ensureDatabaseBound() {
        if (database != null || mongoClient == null) {
            return;
        }
        try {
            database = mongoClient.getDatabase(
                plugin.getConfigManager().getDatabaseConfig().getDatabaseName()
            );
        } catch (RuntimeException ignored) {
        }
    }

    public MongoDatabase getDatabase(String name) {
        HollyRuntime.require(plugin, "mongo:database:" + name);
        if (mongoClient == null) {
            return null;
        }
        return mongoClient.getDatabase(name);
    }

    /**
     * Usuwa WYŁĄCZNIE kolekcje w bazie o nazwie = prefix.
     * Nie rusza innych baz / innych prefixów na tym samym Mongo.
     */
    public CompletableFuture<Integer> wipePrefixCollections(String prefix) {
        if (!isAvailable() || mongoClient == null) {
            return unavailableFuture();
        }
        if (prefix == null || prefix.isBlank() || !prefix.matches("[A-Za-z0-9_-]{1,64}")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Nieprawidlowy db-prefix"));
        }

        MongoDatabase target = mongoClient.getDatabase(prefix.trim());
        return toListFuture(target.listCollectionNames()).thenCompose(names -> {
            if (names == null || names.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            List<CompletableFuture<Void>> drops = new ArrayList<>();
            for (String collectionName : names) {
                drops.add(toFuture(target.getCollection(collectionName).drop()).thenApply(v -> null));
            }
            return CompletableFuture.allOf(drops.toArray(new CompletableFuture[0]))
                .thenApply(v -> names.size());
        });
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public boolean isAvailable() {
        return available && mongoClient != null && database != null;
    }

    public void onAvailable(Runnable listener) {
        if (listener != null) {
            availableListeners.add(listener);
        }
    }

    public static boolean isSkippedBecauseUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("operation skipped to protect player data")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public Throwable getLastFailure() {
        return lastFailure;
    }

    private boolean ping(DatabaseConfig config) {
        try {
            subscribeOne(database.runCommand(new Document("ping", 1)))
                .get(config.getStartupPingTimeoutMs(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception exception) {
            lastFailure = exception;
            maybeLogUnavailable();
            return false;
        }
    }

    private void configureDriverLogging(DatabaseConfig config) {
        if (!config.isQuietDriverLogs()) {
            return;
        }
        installDriverLogFilter();
        setLoggerLevel("com.mongodb", Level.WARNING);
        setLoggerLevel("com.mongodb.driver", Level.WARNING);
        setLoggerLevel("org.mongodb.driver", Level.WARNING);
        setLoggerLevel("org.mongodb.driver.cluster", Level.WARNING);
        setLoggerLevel("dev.grzybalson.libs.mongodb", Level.WARNING);
        setLoggerLevel("dev.grzybalson.libs.mongodb.driver", Level.WARNING);
        setLoggerLevel("dev.grzybalson.libs.mongodb.driver.cluster", Level.WARNING);
    }

    private void setLoggerLevel(String loggerName, Level level) {
        Logger logger = Logger.getLogger(loggerName);
        logger.setLevel(level);
    }

    private void installDriverLogFilter() {
        if (driverLogFilterInstalled) {
            return;
        }
        synchronized (MongoManager.class) {
            if (driverLogFilterInstalled) {
                return;
            }
            Logger root = Logger.getLogger("");
            for (Handler handler : root.getHandlers()) {
                Filter previous = handler.getFilter();
                handler.setFilter(record -> isAllowedDriverRecord(record)
                    && (previous == null || previous.isLoggable(record)));
            }
            driverLogFilterInstalled = true;
        }
    }

    private boolean isAllowedDriverRecord(LogRecord record) {
        String loggerName = record.getLoggerName();
        if (loggerName == null || record.getLevel().intValue() >= Level.WARNING.intValue()) {
            return true;
        }
        return !loggerName.startsWith("com.mongodb")
            && !loggerName.startsWith("org.mongodb.driver")
            && !loggerName.startsWith("dev.grzybalson.libs.mongodb");
    }

    private boolean isConnectionError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();

            // Wolny find()/sort() (read timeout) NIE oznacza, że Mongo padło.
            if (className.contains("MongoSocketReadTimeout")
                || className.contains("MongoOperationTimeout")
                || className.contains("MongoExecutionTimeout")) {
                return false;
            }

            if (className.contains("MongoSocketOpen")
                || className.contains("MongoSocketClosed")
                || className.contains("MongoServerUnavailable")
                || className.contains("MongoNotPrimary")
                || className.contains("MongoNodeIsRecovering")
                || current instanceof java.net.ConnectException
                || current instanceof java.net.NoRouteToHostException
                || current instanceof java.net.UnknownHostException) {
                return true;
            }

            if (className.contains("MongoTimeoutException")
                && (message.contains("server selection")
                    || message.contains("waiting for a server")
                    || message.contains("while waiting to connect")
                    || message.contains("no server chosen"))) {
                return true;
            }

            if (message.contains("connection refused")
                || message.contains("unable to connect")
                || (message.contains("timed out after") && message.contains("waiting to connect"))) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }

    private void markUnavailable(Throwable throwable) {
        lastFailure = throwable;
        boolean wasAvailable = available;
        available = false;
        // Nie zamykamy klienta — driver i istniejące kolekcje mogą wrócić po ping.
        if (wasAvailable) {
            maybeLogUnavailable();
        }
        triggerReconnect();
    }

    private void markAvailable() {
        boolean wasUnavailable = !available;
        available = true;
        lastFailure = null;
        if (wasUnavailable) {
            plugin.getLogger().info("MongoDB is back online! Database operations are re-enabled.");
            notifyAvailable();
        }
    }

    private void notifyAvailable() {
        for (Runnable listener : availableListeners) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Mongo reconnect listener failed: " + exception.getMessage());
            }
        }
    }

    private void triggerReconnect() {
        if (reconnecting) {
            return;
        }
        synchronized (reconnectLock) {
            if (reconnecting) {
                return;
            }
            reconnecting = true;
        }
        runReconnectTask();
    }

    private void runReconnectTask() {
        if (!plugin.isEnabled()) {
            synchronized (reconnectLock) {
                reconnecting = false;
            }
            return;
        }

        if (isAvailable()) {
            synchronized (reconnectLock) {
                reconnecting = false;
            }
            return;
        }

        org.bukkit.Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                if (mongoClient == null || database == null) {
                    plugin.getLogger().info("MongoDB client is not initialized. Attempting full reconnect...");
                    if (connect()) {
                        synchronized (reconnectLock) {
                            reconnecting = false;
                        }
                        return;
                    }
                } else {
                    DatabaseConfig config = plugin.getConfigManager().getDatabaseConfig();
                    try {
                        subscribeOne(database.runCommand(new Document("ping", 1)))
                            .get(Math.min(5000, config.getStartupPingTimeoutMs()), TimeUnit.MILLISECONDS);
                        markAvailable();
                        synchronized (reconnectLock) {
                            reconnecting = false;
                        }
                        return;
                    } catch (Exception pingException) {
                        lastFailure = pingException;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error during MongoDB reconnection check: " + rootMessage(e));
            }

            runReconnectTask();
        }, 100L); // check every 5 seconds (100 ticks)
    }

    private <T> CompletableFuture<T> unavailableFuture() {
        maybeLogUnavailable();
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException(
            "MongoDB is unavailable; operation skipped to protect player data."
        ));
        return future;
    }

    private void maybeLogUnavailable() {
        long now = System.currentTimeMillis();
        if (now - lastUnavailableLogAt < UNAVAILABLE_LOG_THROTTLE_MILLIS) {
            return;
        }
        lastUnavailableLogAt = now;
        String cause = lastFailure != null ? rootMessage(lastFailure) : "unknown";
        plugin.getLogger().warning("MongoDB is unavailable; skipping database operation. Cause: " + cause);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null && !message.isBlank() ? message : current.getClass().getSimpleName();
    }

    public <T> CompletableFuture<T> toFuture(Publisher<T> publisher) {
        if (!isAvailable()) {
            return unavailableFuture();
        }
        return subscribeOne(publisher);
    }

    private <T> CompletableFuture<T> subscribeOne(Publisher<T> publisher) {
        CompletableFuture<T> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<T>() {
            private T result;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(T t) {
                result = t;
            }

            @Override
            public void onError(Throwable t) {
                if (isConnectionError(t)) {
                    markUnavailable(t);
                }
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(result);
            }
        });
        return future;
    }

    public <T> void subscribe(Publisher<T> publisher, Consumer<T> onNext, Consumer<Throwable> onError) {
        if (!isAvailable()) {
            if (onError != null) {
                onError.accept(new IllegalStateException(
                    "MongoDB is unavailable; operation skipped to protect player data."
                ));
            }
            maybeLogUnavailable();
            return;
        }
        publisher.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                if (onNext != null) {
                    onNext.accept(t);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (isConnectionError(t)) {
                    markUnavailable(t);
                }
                if (onError != null) {
                    onError.accept(t);
                }
            }

            @Override
            public void onComplete() {
            }
        });
    }

    public <T> CompletableFuture<List<T>> toListFuture(Publisher<T> publisher) {
        if (!isAvailable()) {
            return unavailableFuture();
        }
        CompletableFuture<List<T>> future = new CompletableFuture<>();
        List<T> results = new ArrayList<>();
        publisher.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T t) {
                results.add(t);
            }

            @Override
            public void onError(Throwable t) {
                if (isConnectionError(t)) {
                    markUnavailable(t);
                }
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(results);
            }
        });
        return future;
    }
}
