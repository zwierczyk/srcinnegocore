package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Exclude;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class DatabaseConfig extends OkaeriConfig {

    private String dbPrefix = "hollycore";

    private String name = "admin";

    private String pass = "password";

    private String authSource = "admin";

    private String ip = "localhost";

    private int port = 27017;

    private NatsSettings nats = new NatsSettings();

    @Exclude
    private int maxPoolSize = 50;

    @Exclude
    private int minPoolSize = 0;

    @Exclude
    private int maxWaitTimeMs = 15000;

    @Exclude
    private int connectTimeoutMs = 10000;

    @Exclude
    private int readTimeoutMs = 15000;

    @Exclude
    private int serverSelectionTimeoutMs = 15000;

    @Exclude
    private int startupPingTimeoutMs = 20000;

    @Exclude
    private boolean retryWrites = true;

    @Exclude
    private boolean retryReads = true;

    @Exclude
    private boolean quietDriverLogs = true;

    public String getDbPrefix() {
        return dbPrefix;
    }

    public String getName() {
        return name;
    }

    public String getPass() {
        return pass;
    }

    public String getAuthSource() {
        return authSource == null || authSource.isBlank() ? "admin" : authSource;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public NatsSettings getNats() {
        return nats == null ? new NatsSettings() : nats;
    }

    public int getMaxPoolSize() {
        return Math.max(1, maxPoolSize);
    }

    public int getMinPoolSize() {
        return Math.max(0, Math.min(minPoolSize, getMaxPoolSize()));
    }

    public int getMaxWaitTimeMs() {
        return Math.max(1000, maxWaitTimeMs);
    }

    public int getConnectTimeoutMs() {
        return Math.max(1000, connectTimeoutMs);
    }

    public int getReadTimeoutMs() {
        return Math.max(1000, readTimeoutMs);
    }

    public int getServerSelectionTimeoutMs() {
        return Math.max(1000, serverSelectionTimeoutMs);
    }

    public int getStartupPingTimeoutMs() {
        return Math.max(1000, startupPingTimeoutMs);
    }

    public boolean isRetryWrites() {
        return retryWrites;
    }

    public boolean isRetryReads() {
        return retryReads;
    }

    public boolean isQuietDriverLogs() {
        return quietDriverLogs;
    }

    public boolean hasCredentials() {
        return name != null && !name.isBlank();
    }

    public String getDatabaseName() {
        return dbPrefix;
    }

    public String getPlayersCollection() {
        return dbPrefix + "_players";
    }

    public String getEconomyCollection() {
        return dbPrefix + "_economy";
    }

    public String getPickupCollection() {
        return dbPrefix + "_pickup";
    }

    public String getStatsCollection() {
        return dbPrefix + "_stats";
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class NatsSettings extends OkaeriConfig {

        private String ip = "localhost";

        private int port = 4222;

        /** Włącz synchronizację rynku między sektorami przez NATS. */
        private boolean marketSync = true;

        /** Port Bukkit sektora-master (ten sam wpis na wszystkich sektorach). 0 = użyj market-master. */
        private int marketMasterPort = 0;

        /** Ustaw true tylko na sektorze-master, gdy market-master-port = 0. */
        private boolean marketMaster = false;

        public String getIp() {
            return ip == null || ip.isBlank() ? "localhost" : ip;
        }

        public int getPort() {
            return port <= 0 ? 4222 : port;
        }

        public boolean isMarketSync() {
            return marketSync;
        }

        public int getMarketMasterPort() {
            return marketMasterPort;
        }

        public boolean isMarketMaster() {
            return marketMaster;
        }

        public String toUri() {
            return "nats://" + getIp() + ":" + getPort();
        }
    }
}
