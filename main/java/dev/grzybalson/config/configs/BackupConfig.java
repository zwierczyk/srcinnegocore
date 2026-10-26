package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;

import java.util.ArrayList;
import java.util.List;

public class BackupConfig extends OkaeriConfig {

    private int snapshotIntervalMinutes = 15;
    private int snapshotTtlHours = 48;
    /** 0 = backupy śmierci nie wygasają (TTL jest tylko na snapshotach cofki). */
    private int deathsTtlHours = 0;
    private int maxDeathsPerPlayer = 300;
    private String discordWebhookUrl = "";
    private boolean discordNotificationsEnabled = false;
    private String discordBotToken = "";
    private String discordGuildId = "";
    private List<String> discordStaffRoleIds = new ArrayList<>(List.of(
        "1489380672974884965",
        "1489379224451027225",
        "1489380136967999699",
        "1489379423315693639",
        "1487914156077219891",
        "1487621669248827413",
        "1527204984544821248"
    ));
    private List<String> discordTicketCategoryIds = new ArrayList<>(List.of(
        "1498691464920698930"
    ));
    private String discordModeName = "";
    private String discordRegistryDatabase = "hollycore_network";
    private String pendingBossBarText = "&e⚠ Masz backup do odebrania! Użyj &f/odbierzbackup";
    private boolean deathKeepEnabled = true;
    private double deathKeepPercent = 0.35D;
    private double deathKeepBoostPercent = 0.50D;
    private Zapowiedz zapowiedz = new Zapowiedz();
    private Messages messages = new Messages();

    public static class Zapowiedz extends OkaeriConfig {
        private boolean enabled = false;
        private String timezone = "Europe/Warsaw";
        private String eventDate = "2026-08-26";
        private int rewardStartHour = 18;
        private int rewardEndHour = 19;
        private String bannerUrl = "https://i.imgur.com/Komqfo4.png";
        private String rewardDbPrefix = "skypvp1";
        private boolean remindersEnabled = true;
        private int reminderDispatchIntervalSeconds = 10;
        private String reminderStatusChannelId = "1528310546758369393";
        private Mongo mongo = new Mongo();
        private java.util.List<Reward> rewards = new java.util.ArrayList<>(java.util.List.of(
            new Reward("rzadka", 3),
            new Reward("skarbow", 1)
        ));

        public static class Mongo extends OkaeriConfig {
            private String dbPrefix = "";
            private String name = "";
            private String pass = "";
            private String authSource = "admin";
            private String ip = "localhost";
            private int port = 27017;

            public String getDbPrefix() { return dbPrefix == null ? "" : dbPrefix.trim(); }
            public String getName() { return name == null ? "" : name; }
            public String getPass() { return pass == null ? "" : pass; }
            public String getAuthSource() { return authSource == null || authSource.isBlank() ? "admin" : authSource.trim(); }
            public String getIp() { return ip == null || ip.isBlank() ? "localhost" : ip.trim(); }
            public int getPort() { return port <= 0 ? 27017 : port; }
            public boolean hasCredentials() { return getName() != null && !getName().isBlank(); }
        }

        public static class Reward extends OkaeriConfig {
            private String caseName = "rzadka";
            private int amount = 1;

            public Reward() {
            }

            public Reward(String caseName, int amount) {
                this.caseName = caseName;
                this.amount = amount;
            }

            public String getCaseName() { return caseName == null ? "" : caseName.trim(); }
            public int getAmount() { return Math.max(1, amount); }
        }

        public boolean isEnabled() { return enabled; }
        public String getTimezone() { return timezone == null || timezone.isBlank() ? "Europe/Warsaw" : timezone.trim(); }
        public String getEventDate() { return eventDate == null || eventDate.isBlank() ? "2026-08-26" : eventDate.trim(); }
        public int getRewardStartHour() { return Math.max(0, Math.min(23, rewardStartHour)); }
        public int getRewardEndHour() { return Math.max(1, Math.min(24, rewardEndHour)); }
        public String getBannerUrl() { return bannerUrl == null ? "" : bannerUrl.trim(); }
        public String getRewardDbPrefix() { return rewardDbPrefix == null ? "" : rewardDbPrefix.trim(); }
        public boolean isRemindersEnabled() { return remindersEnabled; }
        public int getReminderDispatchIntervalSeconds() { return 10; }
        public String getReminderStatusChannelId() {
            return reminderStatusChannelId == null || reminderStatusChannelId.isBlank()
                ? "1528310546758369393"
                : reminderStatusChannelId.trim();
        }
        public Mongo getMongo() { return mongo == null ? new Mongo() : mongo; }
        public java.util.List<Reward> getRewards() { return rewards == null ? java.util.List.of() : rewards; }
    }

    public static class Messages extends OkaeriConfig {
        private String backupRequested = "&#00E0FFℹ &8Ξ &fProśba o backup została wysłana do weryfikacji.";
        private String backupAccepted = "&#00FF3B✔ &8Ξ &#4EFA76Backup został nadany! Użyj &f/odbierzbackup &#4EFA76aby odebrać przedmioty.";
        private String backupDenied = "&#FF0000✘ &8Ξ &cTwoja prośba o backup została odrzucona.";
        private String backupPending = "&#FFB800⚠ &8Ξ &eMasz oczekujący backup! Użyj &f/odbierzbackup &eaby go odebrać.";
        private String noBackupPending = "&#FF0000✘ &8Ξ &cNie masz żadnego oczekującego backupu.";
        private String inventoryNotEmpty = "&#FF0000✘ &8Ξ &cMusisz mieć całkowicie pusty ekwipunek, zbroję i drugą rękę, aby odebrać backup!";
        private String backupReceived = "&#00FF3B✔ &8Ξ &#4EFA76Otrzymałeś swój backup!";
        private String noDeathsFound = "&#FF0000✘ &8Ξ &cNie znaleziono żadnych backupów śmierci dla tego gracza.";
        private String playerNotFound = "&#FF0000✘ &8Ξ &cNie znaleziono gracza o podanym nicku.";
        private String noPermission = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/backup &8(&4holly.cmd.backup.verify&8)";
        private String backupListEmpty = "&#FF0000✘ &8Ξ &cBrak oczekujących zgłoszeń backupów.";
        private String backupDeleted = "&#00FF3B✔ &8Ξ &#4EFA76Zgłoszenie backupu zostało usunięte.";
        private String backupReloaded = "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano konfigurację backupów.";
        private String backupEqGranted = "&#00FF3B✔ &8Ξ &#4EFA76Backup ekwipunku został nadany.";
        private String backupStatsRestored = "&#00FF3B✔ &8Ξ &#4EFA76Statystyki zostały przywrócone.";
        private String backupStatsRestoredTarget = "&#00FF3B✔ &8Ξ &#4EFA76Twoje statystyki zostały przywrócone przez administrację.";

        public String getBackupRequested() { return backupRequested; }
        public String getBackupAccepted() { return backupAccepted; }
        public String getBackupDenied() { return backupDenied; }
        public String getBackupPending() { return backupPending; }
        public String getNoBackupPending() { return noBackupPending; }
        public String getInventoryNotEmpty() { return inventoryNotEmpty; }
        public String getBackupReceived() { return backupReceived; }
        public String getNoDeathsFound() { return noDeathsFound; }
        public String getPlayerNotFound() { return playerNotFound; }
        public String getNoPermission() { return noPermission; }
        public String getBackupListEmpty() { return backupListEmpty; }
        public String getBackupDeleted() { return backupDeleted; }
        public String getBackupReloaded() { return backupReloaded; }
        public String getBackupEqGranted() { return backupEqGranted; }
        public String getBackupStatsRestored() { return backupStatsRestored; }
        public String getBackupStatsRestoredTarget() { return backupStatsRestoredTarget; }
    }

    public int getSnapshotIntervalMinutes() { return snapshotIntervalMinutes; }
    public int getSnapshotTtlHours() { return snapshotTtlHours; }
    public int getDeathsTtlHours() { return deathsTtlHours; }
    public int getMaxDeathsPerPlayer() { return maxDeathsPerPlayer; }
    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
    public boolean isDiscordNotificationsEnabled() { return discordNotificationsEnabled; }
    public String getDiscordBotToken() { return discordBotToken; }
    public String getDiscordGuildId() { return discordGuildId; }
    public List<String> getDiscordStaffRoleIds() { return discordStaffRoleIds == null ? List.of() : discordStaffRoleIds; }
    public List<String> getDiscordTicketCategoryIds() { return discordTicketCategoryIds == null ? List.of() : discordTicketCategoryIds; }
    public String getDiscordModeName() { return discordModeName == null ? "" : discordModeName.trim(); }
    public String getDiscordRegistryDatabase() {
        return discordRegistryDatabase == null || discordRegistryDatabase.isBlank() ? "hollycore_network" : discordRegistryDatabase.trim();
    }
    public String getPendingBossBarText() { return pendingBossBarText; }
    public boolean getDeathKeepEnabled() { return deathKeepEnabled; }
    public double getDeathKeepPercent() { return deathKeepPercent <= 0 ? 0.35D : deathKeepPercent; }
    public double getDeathKeepBoostPercent() { return deathKeepBoostPercent <= 0 ? 0.50D : deathKeepBoostPercent; }
    public Zapowiedz getZapowiedz() { return zapowiedz == null ? new Zapowiedz() : zapowiedz; }
    public Messages getMessages() { return messages; }
}
