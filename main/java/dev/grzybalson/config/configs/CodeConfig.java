package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class CodeConfig extends OkaeriConfig {

    private Map<String, CodeEntry> codes = new HashMap<>();

    private boolean requireDiscordReward = true;
    private String codeNotFound = "&#FF0000✘ &8Ξ &cKod nie istnieje lub wygasł!";
    private String codeAlreadyUsed = "&#FF0000✘ &8Ξ &cJuż odebrałeś ten kod!";
    private String codeNotActive = "&#FF0000✘ &8Ξ &cKod nie jest aktywny!";
    private String codeDiscordRequired = "&#FF0000✘ &8Ξ &cNajpierw odbierz nagrodę Discord, żeby móc używać &e/kod&c! &7(/nagroda)";
    private String codeTimeRequired = "&#FF0000✘ &8Ξ &cMusisz spędzić jeszcze &e{time} &cna serwerze aby odebrać ten kod!";
    private String codeRedeemed = "&#00FF3B✔ &8Ξ &#4EFA76Odebrałeś kod &e{code}&a!";
    private boolean codeRedeemMessageEnabled = true;
    private boolean codeBroadcastEnabled = true;
    private List<String> codeBroadcast = new ArrayList<>(List.of(
        "&8[&6✦&8] &e{player} &7odebrał kod &e{code}&7!",
        "&7Jeśli też chcesz, wpisz &e/kod {code}"
    ));
    private String codeStarted = "&#00FF3B✔ &8Ξ &#4EFA76Aktywowano kod &e{code}&a!";
    private String codeStopped = "&#00FF3B✔ &8Ξ &#4EFA76Zatrzymano kod &e{code}&a!";
    private String codeCreated = "&#00FF3B✔ &8Ξ &#4EFA76Stworzono kod &e{code}&a!";
    private String codeDeleted = "&#00FF3B✔ &8Ξ &#4EFA76Usunięto kod &e{code}&a!";
    private String codeReloaded = "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano konfigurację kodów!";
    private String codeItemAdded = "&#00FF3B✔ &8Ξ &#4EFA76Dodano przedmiot do kodu &e{code}&a!";
    private String codeResetAll = "&#00FF3B✔ &8Ξ &#4EFA76Zresetowano użycie kodu &e{code} &adla wszystkich graczy!";
    private String codeResetPlayer = "&#00FF3B✔ &8Ξ &#4EFA76Zresetowano użycie kodu &e{code} &adla gracza &e{player}&a!";
    private String codeList = "&#00E0FFℹ &8Ξ &fDostępne kody: &e{codes}";
    private String codeUsage = "&#FF0000✘ &8Ξ &cPoprawne użycie: &#FF0000\n &8• &4/kod <kod>";

    private String bossbarFormat = "&e&lKOD: &f{code} &8| &7Kliknij &e/kod {code} &7aby odebrać!";
    private String bossbarColor = "YELLOW";
    private String bossbarStyle = "SOLID";
    private double bossbarProgress = 1.0;
    private boolean bossbarEnabled = true;

    private RankGiveaway rankGiveaway = new RankGiveaway();

    public RankGiveaway getRankGiveaway() { return rankGiveaway; }
    public void setRankGiveaway(RankGiveaway rankGiveaway) { this.rankGiveaway = rankGiveaway; }

    public Map<String, CodeEntry> getCodes() { return codes; }
    public void setCodes(Map<String, CodeEntry> codes) { this.codes = codes; }

    public boolean isRequireDiscordReward() { return requireDiscordReward; }
    public String getCodeNotFound() { return codeNotFound; }
    public String getCodeAlreadyUsed() { return codeAlreadyUsed; }
    public String getCodeNotActive() { return codeNotActive; }
    public String getCodeDiscordRequired() { return codeDiscordRequired; }
    public String getCodeTimeRequired() { return codeTimeRequired; }
    public String getCodeRedeemed() { return codeRedeemed; }
    public boolean isCodeRedeemMessageEnabled() { return codeRedeemMessageEnabled; }
    public boolean isCodeBroadcastEnabled() { return codeBroadcastEnabled; }
    public List<String> getCodeBroadcast() { return codeBroadcast; }
    public String getCodeStarted() { return codeStarted; }
    public String getCodeStopped() { return codeStopped; }
    public String getCodeCreated() { return codeCreated; }
    public String getCodeDeleted() { return codeDeleted; }
    public String getCodeReloaded() { return codeReloaded; }
    public String getCodeItemAdded() { return codeItemAdded; }
    public String getCodeResetAll() { return codeResetAll; }
    public String getCodeResetPlayer() { return codeResetPlayer; }
    public String getCodeList() { return codeList; }
    public String getCodeUsage() { return codeUsage; }
    public String getBossbarFormat() { return bossbarFormat; }
    public String getBossbarColor() { return bossbarColor; }
    public String getBossbarStyle() { return bossbarStyle; }
    public double getBossbarProgress() { return bossbarProgress; }
    public boolean isBossbarEnabled() { return bossbarEnabled; }

    public void addCode(String name, CodeEntry entry) {
        codes.put(name.toLowerCase(), entry);
        this.save();
    }

    public void removeCode(String name) {
        codes.remove(name.toLowerCase());
        this.save();
    }

    public CodeEntry getCode(String name) {
        return codes.get(name.toLowerCase());
    }

    public static class CodeEntry extends OkaeriConfig {
        private String name;
        private int requiredPlaytimeMinutes = 0;
        private List<String> items = new ArrayList<>();
        private List<String> commands = new ArrayList<>();
        private boolean active = false;
        private boolean broadcastEnabled = true;
        private boolean bossbarEnabled = true;
        private String bossbarFormat;
        private String bossbarColor;
        private String bossbarStyle;
        private Double bossbarProgress;

        public CodeEntry() {}

        public CodeEntry(String name, int requiredPlaytimeMinutes) {
            this.name = name;
            this.requiredPlaytimeMinutes = requiredPlaytimeMinutes;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getRequiredPlaytimeMinutes() { return requiredPlaytimeMinutes; }
        public void setRequiredPlaytimeMinutes(int requiredPlaytimeMinutes) { this.requiredPlaytimeMinutes = requiredPlaytimeMinutes; }
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
        public List<String> getCommands() { return commands; }
        public void setCommands(List<String> commands) { this.commands = commands; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public boolean isBroadcastEnabled() { return broadcastEnabled; }
        public void setBroadcastEnabled(boolean broadcastEnabled) { this.broadcastEnabled = broadcastEnabled; }
        public boolean isBossbarEnabled() { return bossbarEnabled; }
        public void setBossbarEnabled(boolean bossbarEnabled) { this.bossbarEnabled = bossbarEnabled; }
        public String getBossbarFormat() { return bossbarFormat; }
        public void setBossbarFormat(String bossbarFormat) { this.bossbarFormat = bossbarFormat; }
        public String getBossbarColor() { return bossbarColor; }
        public void setBossbarColor(String bossbarColor) { this.bossbarColor = bossbarColor; }
        public String getBossbarStyle() { return bossbarStyle; }
        public void setBossbarStyle(String bossbarStyle) { this.bossbarStyle = bossbarStyle; }
        public Double getBossbarProgress() { return bossbarProgress; }
        public void setBossbarProgress(Double bossbarProgress) { this.bossbarProgress = bossbarProgress; }

        public void addItem(String itemData) {
            items.add(itemData);
        }

        public void addCommand(String command) {
            commands.add(command);
        }
    }

    public static class RankGiveaway extends OkaeriConfig {
        private boolean active = false;
        private String availableFrom = "20:10";
        private String timezone = "Europe/Warsaw";
        private int maxWinners = 50;
        private String grantDuration = "1h";
        private String commandTemplate = "lp user {player} parent addtemp {group} {duration}";
        private String announcementTitle = "&a&lGZ!!!";
        private String announcementSubtitle = "&f{player}";
        private String rankSubtitle = "&7Losowanie rangi...";
        private String timeSubtitle = "&7Czas: &f{time}";
        private String drawStarted = "&#00FF3B✔ &8Ξ &#4EFA76Rozpoczynam losowanie!";
        private String notActive = "&#FF0000✘ &8Ξ &cRozdanie rang nie jest aktywne!";
        private String notAvailableYet = "&#FF0000✘ &8Ξ &cRozdanie rang bedzie dostepne od &f{time}&c!";
        private String alreadyWon = "&#FF0000✘ &8Ξ &cJuz wylosowales range w tym rozdanieu!";
        private String limitReached = "&#FF0000✘ &8Ξ &cLimit &f{limit} &closowan zostal juz wyczerpany!";
        private String waitForDraw = "&#FFB800⚠ &8Ξ &eTrwa losowanie! Sprobuj ponownie za chwile.";
        private List<String> fakeDurations = new ArrayList<>(List.of("1h", "10h", "1d", "7d", "30d"));
        private Map<String, RankPoolEntry> ranks = defaultRankPool();

        public boolean isActive() { return active; }
        public String getAvailableFrom() { return availableFrom; }
        public String getTimezone() { return timezone; }
        public int getMaxWinners() { return maxWinners; }
        public String getGrantDuration() { return grantDuration; }
        public String getCommandTemplate() { return commandTemplate; }
        public String getAnnouncementTitle() { return announcementTitle; }
        public String getAnnouncementSubtitle() { return announcementSubtitle; }
        public String getRankSubtitle() { return rankSubtitle; }
        public String getTimeSubtitle() { return timeSubtitle; }
        public String getDrawStarted() { return drawStarted; }
        public String getNotActive() { return notActive; }
        public String getNotAvailableYet() { return notAvailableYet; }
        public String getAlreadyWon() { return alreadyWon; }
        public String getLimitReached() { return limitReached; }
        public String getWaitForDraw() { return waitForDraw; }
        public List<String> getFakeDurations() { return fakeDurations; }
        public Map<String, RankPoolEntry> getRanks() { return ranks; }

        private static Map<String, RankPoolEntry> defaultRankPool() {
            Map<String, RankPoolEntry> pool = new LinkedHashMap<>();
            pool.put("vip", new RankPoolEntry("vip", "&f&x&7&B&D&7&7&E&lᴠ&lɪ&lᴘ", 35, -1));
            pool.put("svip", new RankPoolEntry("svip", "&f&x&D&7&C&D&7&B&lꜱ&lᴠ&lɪ&lᴘ", 30, -1));
            pool.put("legenda", new RankPoolEntry("legenda", "&f&x&F&F&8&7&0&0&lʟ&lᴇ&lɢ&lᴇ&lɴ&lᴅ&lᴀ", 20, -1));
            pool.put("og", new RankPoolEntry("og", "&f&x&0&0&F&F&1&1&lᴏ&x&5&4&B&0&5&A&lɢ", 10, 10));
            pool.put("peak", new RankPoolEntry("peak", "&x&0&0&F&4&F&F&lᴘ&x&0&0&C&D&D&6&lᴇ&x&0&0&B&7&B&F&lᴀ&x&0&0&9&8&9&F&lᴋ", 5, 10));
            return pool;
        }
    }

    public static class RankPoolEntry extends OkaeriConfig {
        private String group;
        private String title;
        private int weight = 1;
        private int limit = -1;

        public RankPoolEntry() {}

        public RankPoolEntry(String group, String title, int weight, int limit) {
            this.group = group;
            this.title = title;
            this.weight = weight;
            this.limit = limit;
        }

        public String getGroup() { return group; }
        public String getTitle() { return title; }
        public int getWeight() { return weight; }
        public int getLimit() { return limit; }
    }
}
