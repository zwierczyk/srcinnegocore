package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class ClanConfig extends OkaeriConfig {

    private int minMembersForRanking = 3;

    private int hypestarPackVersion = 0;

    private int startingElo = 1000;

    private int minTagLength = 3;
    private int maxTagLength = 6;

    private int maxMembers = 100;
    private int maxDeputies = 2;
    private double creationCost = 1.0;

    private double pointsMultiplier = 1.0D;
    private double pointsPercent = 0.026D;
    private double minusMultiplier = 1.15D;
    private double turboPointsMulti = 1.5D;

    private boolean panelAccess = true;
    private boolean clansEnabled = true;
    private String clanDisableMessage = "&cKlany są wyłączone do godziny &419:30!";

    private int eloGainOnKill = 15;

    private int eloLossOnDeath = 10;

    private String clanCreatedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie utworzono klan &f{clan}&a!";
    private String clanDeletedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Klan został usunięty!";
    private String clanInviteSentMessage = "&#00FF3B✔ &8Ξ &#4EFA76Wysłano zaproszenie do &f{player}&a!";
    private String clanInviteReceivedMessage = "&#00E0FFℹ &8Ξ &fOtrzymałeś zaproszenie do klanu &f{clan}&f! Kliknij &a[DOŁĄCZ] &6aby dołączyć.";
    private String clanJoinedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Dołączyłeś do klanu &f{clan}&a!";
    private String clanLeftMessage = "&#00FF3B✔ &8Ξ &#4EFA76Opuściłeś klan!";
    private String clanKickedMessage = "&#FF0000✘ &8Ξ &cZostałeś wyrzucony z klanu!";
    private String clanAlreadyInClanMessage = "&#FF0000✘ &8Ξ &cJesteś już w klanie!";
    private String clanNotInClanMessage = "&#FF0000✘ &8Ξ &cNie jesteś w żadnym klanie!";
    private String clanNotLeaderMessage = "&#FF0000✘ &8Ξ &cNie jesteś liderem klanu!";
    private String clanNotLeaderOrDeputyMessage = "&#FF0000✘ &8Ξ &cMusisz być liderem albo zastępcą klanu!";
    private String clanLeaderCannotLeaveMessage = "&#FF0000✘ &8Ξ &cLider nie może opuścić klanu. Przekaż lidera albo usuń klan.";
    private String clanTargetAlreadyInClanMessage = "&#FF0000✘ &8Ξ &cTen gracz jest już w klanie!";
    private String clanTargetNotInYourClanMessage = "&#FF0000✘ &8Ξ &cTen gracz nie jest członkiem Twojego klanu!";
    private String clanFriendlyFireBlockedMessage = "&#FF0000✘ &8Ξ &cPvP klanowe jest wyłączone. Nie możesz bić gracza &f{player}&c.";
    private String clanPvpEnabledMessage = "&#00FF3B✔ &8Ξ &#4EFA76PvP między członkami klanu zostało włączone.";
    private String clanPvpDisabledMessage = "&#00FF3B✔ &8Ξ &#4EFA76PvP między członkami klanu zostało wyłączone.";
    private String clanAnnouncementSentMessage = "&#00FF3B✔ &8Ξ &#4EFA76Wysłano ogłoszenie klanowe!";
    private String clanTagExistsMessage = "&#FF0000✘ &8Ξ &cKlan z tym tagiem już istnieje!";
    private String clanNotEnoughMoneyMessage = "&#FF0000✘ &8Ξ &cNie masz wystarczająco pieniędzy! Potrzebujesz &f{cost}$&c.";
    private String clanPlayerNotFoundMessage = "&#FF0000✘ &8Ξ &cGracz nie został znaleziony!";
    private String clanNoInviteMessage = "&#FF0000✘ &8Ξ &cNie masz żadnego zaproszenia do klanu!";
    private String clanDeputySetMessage = "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono &f{player} &ajako zastępcę!";
    private String clanHelpRequestMessage = "&#00E0FFℹ &8Ξ &f{player} &7prosi o pomoc! Lokalizacja: &e{x}, {y}, {z}";
    private String clanChatPrefix = "!";
    private String clanChatFormat = "&2🎣 &8(&f{PLAYER} &8-> &b{CLAN}&8) &8| &a{MESSAGE}";
    private String clanAnnouncementFormat = "&8[&c&lKLAN&8] &e{message}";
    private String createClanError = "&4☠ &8| &cNie można aktualnie założyć klanu, Spróbuj ponownie później!";
    private String clanCreateBroadcast = "&f⚔ &8| &fGracz &#0F8E00{PLAYER} &fstworzył klan o nazwie &8[&#0F8E00{CLAN}&8]";
    private String clanDeleteBroadcast = "&f⚔ &8| &fGracz &#8E0000{PLAYER} &fusunął klan o nazwie &8[&#8E0000{CLAN}&8]";
    private String userJoinClanBroadcast = "&f⚔ &8| &fGracz &#0F8E00{PLAYER} &fdołączył do klanu o nazwie &8[&#0F8E00{CLAN}&8]";

    private String clanUpgradePurchasedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie ulepszono!";
    private String clanUpgradeNeedPreviousMessage = "&#FF0000✘ &8Ξ &cNajpierw musisz kupić poprzedni poziom ulepszenia!";
    private String clanUpgradeNotEnoughCoinsMessage = "&#FF0000✘ &8Ξ &cKlan nie ma wystarczającej ilości {currency}! Potrzebujesz &f{price}&c.";
    private String clanUpgradeDisabledMessage = "&#FF0000✘ &8Ξ &cUlepszenia klanu są wyłączone!";
    private String clanDepositSuccessMessage = "&#00FF3B✔ &8Ξ &#4EFA76Wpłacono &f{amount} &ado skarbca klanu ({currency})!";
    private String clanDepositInvalidAmountMessage = "&#FF0000✘ &8Ξ &cPodaj poprawną kwotę większą od 0!";
    private String clanDepositNotEnoughMoneyMessage = "&#FF0000✘ &8Ξ &cNie masz wystarczająco pieniędzy! Potrzebujesz &f{amount}$&c.";

    private List<String> clanInfoLayout = List.of(
        "&7",
        "      &8»  &f⚔ &8| &8[&#FF0003{CLAN}&8] &8| &f⚔  &8«",
        "&7",
        "    &f★ &8| &#FF0003&lɪɴꜰᴏʀᴍᴀᴄᴊᴇ &8| &f★",
        "&7",
        "  &8▶ &7Założyciel: &f{LEADER}",
        "  &8▶ &7Zastępcy: &f{DEPUTIES}",
        "  &8▶ &7Członkowie: &f{MEMBERS}",
        "&7",
        "    &f❖ &8| &#FF0003&lꜱᴛᴀᴛʏꜱᴛʏᴋɪ &8| &f❖",
        "&7",
        "  &8▶ &7Punkty: &f{POINTS}",
        "  &8▶ &7Zabójstwa: &f{KILLS}",
        "  &8▶ &7Śmierci: &f{DEATHS}",
        "&7"
    );

    private List<String> clanHelpMessage = List.of(
        "&7",
        "            &8»  &f⚔ &8| &#FF0003&lᴋʟᴀɴʏ &8| &f⚔  &8«",
        "&7",
        " &8▶ &8/&fklan &#FF3D3D&nstworz&r &f<tag> &8- &7Tworzy klan",
        " &8▶ &8/&fklan &#FF3D3D&nusun&r &8- &7Usuwa klan",
        " &8▶ &8/&fklan &#FF3D3D&nopusc&r &8- &7Opuszcza aktualny klan",
        " &8▶ &8/&fklan &#FF3D3D&ndolacz&r &f<tag> &8- &7Dołącza do podanego klanu",
        " &8▶ &8/&fklan &#FF3D3D&nwyrzuc&r &f<nick> &8- &7Wyrzuca gracza z klanu",
        " &8▶ &8/&fklan &#FF3D3D&nzapros&r &f<nick> &8- &7Zaprasza gracza do klanu",
        " &8▶ &8/&fklan &#FF3D3D&nlider&r &f<nick> &8- &7Ustawia nowego lidera klanu",
        " &8▶ &8/&fklan &#FF3D3D&nzastepca&r &f<nick> &8- &7Dodaje nowego zastępce do klanu",
        " &8▶ &8/&fklan &#FF3D3D&ninfo&r &f<tag> &8- &7Podaje informacje o klanie",
        " &8▶ &8/&fklan &#FF3D3D&nulepsz&r &8- &7Otwiera ulepszenia klanu",
        "&7"
    );

    private String clanCoinDisplay = "&x&B&1&B&0&4&5&lᴍ&x&B&5&B&2&5&2&lᴏ&x&B&8&B&4&5&F&lɴ&x&B&C&B&6&6&C&lᴇ&x&B&F&B&7&7&8&lᴛ&x&C&3&B&9&8&5&lᴀ &x&C&A&B&D&9&F&lᴋ&x&C&6&B&B&9&0&lʟ&x&C&2&B&9&8&1&lᴀ&x&B&E&B&7&7&2&lɴ&x&B&9&B&4&6&3&lᴏ&x&B&5&B&2&5&4&lᴡ&x&B&1&B&0&4&5&lᴀ";

    private String clanNoDeputyText = "Brak";
    private String clanNotRankedText = "-";
    private String clanPvpEnabledText = "&aWłączone";
    private String clanPvpDisabledText = "&cWyłączone";

    private ClanUpgradesSettings upgrades = ClanUpgradePack.createUpgrades();

    public boolean applyHypestarPackIfNeeded() {
        if (hypestarPackVersion >= ClanUpgradePack.VERSION) {
            return migrateLegacyGlyphs();
        }
        ClanUpgradePack.apply(this);
        migrateLegacyGlyphs();
        save();
        return true;
    }

    /** Stare glify resource-packa (普/聿) bez fontu wychodza jako chinskie znaki — zamien na emotki. */
    public boolean migrateLegacyGlyphs() {
        boolean changed = false;
        String create = migrateGlyph(clanCreateBroadcast);
        String delete = migrateGlyph(clanDeleteBroadcast);
        String join = migrateGlyph(userJoinClanBroadcast);
        if (!create.equals(clanCreateBroadcast)) {
            clanCreateBroadcast = create;
            changed = true;
        }
        if (!delete.equals(clanDeleteBroadcast)) {
            clanDeleteBroadcast = delete;
            changed = true;
        }
        if (!join.equals(userJoinClanBroadcast)) {
            userJoinClanBroadcast = join;
            changed = true;
        }

        List<String> info = migrateGlyphList(clanInfoLayout);
        if (!info.equals(clanInfoLayout)) {
            clanInfoLayout = info;
            changed = true;
        }
        List<String> help = migrateGlyphList(clanHelpMessage);
        if (!help.equals(clanHelpMessage)) {
            clanHelpMessage = help;
            changed = true;
        }

        if (changed) {
            save();
        }
        return changed;
    }

    private static String migrateGlyph(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        return input.replace("普", "⚔").replace("聿", "❖");
    }

    private static List<String> migrateGlyphList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return input == null ? List.of() : input;
        }
        List<String> out = new ArrayList<>(input.size());
        for (String line : input) {
            out.add(migrateGlyph(line));
        }
        return out;
    }

    public int getHypestarPackVersion() { return hypestarPackVersion; }
    public void setHypestarPackVersion(int hypestarPackVersion) { this.hypestarPackVersion = hypestarPackVersion; }
    public int getMinMembersForRanking() { return minMembersForRanking; }
    public int getStartingElo() { return startingElo; }
    public void setStartingElo(int startingElo) { this.startingElo = startingElo; }
    public int getMinTagLength() { return minTagLength; }
    public void setMinTagLength(int minTagLength) { this.minTagLength = minTagLength; }
    public int getMaxTagLength() { return maxTagLength; }
    public void setMaxTagLength(int maxTagLength) { this.maxTagLength = maxTagLength; }
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
    public int getMaxDeputies() { return maxDeputies; }
    public void setMaxDeputies(int maxDeputies) { this.maxDeputies = maxDeputies; }
    public double getCreationCost() { return creationCost; }
    public void setCreationCost(double creationCost) { this.creationCost = creationCost; }
    public double getPointsMultiplier() { return pointsMultiplier; }
    public void setPointsMultiplier(double pointsMultiplier) { this.pointsMultiplier = pointsMultiplier; }
    public double getPointsPercent() { return pointsPercent; }
    public void setPointsPercent(double pointsPercent) { this.pointsPercent = pointsPercent; }
    public double getMinusMultiplier() { return minusMultiplier; }
    public void setMinusMultiplier(double minusMultiplier) { this.minusMultiplier = minusMultiplier; }
    public double getTurboPointsMulti() { return turboPointsMulti; }
    public void setTurboPointsMulti(double turboPointsMulti) { this.turboPointsMulti = turboPointsMulti; }
    public boolean isPanelAccess() { return panelAccess; }
    public void setPanelAccess(boolean panelAccess) { this.panelAccess = panelAccess; }
    public boolean isClansEnabled() { return clansEnabled; }
    public void setClansEnabled(boolean clansEnabled) { this.clansEnabled = clansEnabled; }
    public String getClanDisableMessage() { return clanDisableMessage; }
    public void setClanDisableMessage(String clanDisableMessage) { this.clanDisableMessage = clanDisableMessage; }
    public int getEloGainOnKill() { return eloGainOnKill; }
    public int getEloLossOnDeath() { return eloLossOnDeath; }

    public String getClanCreatedMessage() { return clanCreatedMessage; }
    public String getClanDeletedMessage() { return clanDeletedMessage; }
    public String getClanInviteSentMessage() { return clanInviteSentMessage; }
    public String getClanInviteReceivedMessage() { return clanInviteReceivedMessage; }
    public String getClanJoinedMessage() { return clanJoinedMessage; }
    public String getClanLeftMessage() { return clanLeftMessage; }
    public String getClanKickedMessage() { return clanKickedMessage; }
    public String getClanAlreadyInClanMessage() { return clanAlreadyInClanMessage; }
    public String getClanNotInClanMessage() { return clanNotInClanMessage; }
    public String getClanNotLeaderMessage() { return clanNotLeaderMessage; }
    public String getClanNotLeaderOrDeputyMessage() { return clanNotLeaderOrDeputyMessage; }
    public String getClanLeaderCannotLeaveMessage() { return clanLeaderCannotLeaveMessage; }
    public String getClanTargetAlreadyInClanMessage() { return clanTargetAlreadyInClanMessage; }
    public String getClanTargetNotInYourClanMessage() { return clanTargetNotInYourClanMessage; }
    public String getClanFriendlyFireBlockedMessage() { return clanFriendlyFireBlockedMessage; }
    public String getClanPvpEnabledMessage() { return clanPvpEnabledMessage; }
    public String getClanPvpDisabledMessage() { return clanPvpDisabledMessage; }
    public String getClanAnnouncementSentMessage() { return clanAnnouncementSentMessage; }
    public String getClanTagExistsMessage() { return clanTagExistsMessage; }
    public String getClanNotEnoughMoneyMessage() { return clanNotEnoughMoneyMessage; }
    public String getClanPlayerNotFoundMessage() { return clanPlayerNotFoundMessage; }
    public String getClanNoInviteMessage() { return clanNoInviteMessage; }
    public String getClanDeputySetMessage() { return clanDeputySetMessage; }
    public String getClanHelpRequestMessage() { return clanHelpRequestMessage; }
    public String getClanChatPrefix() { return clanChatPrefix == null || clanChatPrefix.isBlank() ? "!" : clanChatPrefix; }
    public void setClanChatPrefix(String clanChatPrefix) { this.clanChatPrefix = clanChatPrefix; }
    public String getClanChatFormat() { return clanChatFormat; }
    public void setClanChatFormat(String clanChatFormat) { this.clanChatFormat = clanChatFormat; }
    public String getClanAnnouncementFormat() { return clanAnnouncementFormat; }
    public String getCreateClanError() { return createClanError; }
    public void setCreateClanError(String createClanError) { this.createClanError = createClanError; }
    public String getClanCreateBroadcast() { return clanCreateBroadcast; }
    public void setClanCreateBroadcast(String clanCreateBroadcast) { this.clanCreateBroadcast = clanCreateBroadcast; }
    public String getClanDeleteBroadcast() { return clanDeleteBroadcast; }
    public void setClanDeleteBroadcast(String clanDeleteBroadcast) { this.clanDeleteBroadcast = clanDeleteBroadcast; }
    public String getUserJoinClanBroadcast() { return userJoinClanBroadcast; }
    public void setUserJoinClanBroadcast(String userJoinClanBroadcast) { this.userJoinClanBroadcast = userJoinClanBroadcast; }
    public String getClanUpgradePurchasedMessage() { return clanUpgradePurchasedMessage; }
    public String getClanUpgradeNeedPreviousMessage() { return clanUpgradeNeedPreviousMessage; }
    public String getClanUpgradeNotEnoughCoinsMessage() { return clanUpgradeNotEnoughCoinsMessage; }
    public String getClanUpgradeDisabledMessage() { return clanUpgradeDisabledMessage; }
    public String getClanDepositSuccessMessage() { return clanDepositSuccessMessage; }
    public String getClanDepositInvalidAmountMessage() { return clanDepositInvalidAmountMessage; }
    public String getClanDepositNotEnoughMoneyMessage() { return clanDepositNotEnoughMoneyMessage; }
    public List<String> getClanInfoLayout() { return clanInfoLayout; }
    public void setClanInfoLayout(List<String> clanInfoLayout) { this.clanInfoLayout = clanInfoLayout != null ? clanInfoLayout : List.of(); }
    public List<String> getClanHelpMessage() { return clanHelpMessage; }
    public void setClanHelpMessage(List<String> clanHelpMessage) { this.clanHelpMessage = clanHelpMessage != null ? clanHelpMessage : List.of(); }
    public String getClanCoinDisplay() { return clanCoinDisplay; }
    public void setClanCoinDisplay(String clanCoinDisplay) { this.clanCoinDisplay = clanCoinDisplay; }
    public String getClanNoDeputyText() { return clanNoDeputyText; }
    public String getClanNotRankedText() { return clanNotRankedText; }
    public String getClanPvpEnabledText() { return clanPvpEnabledText; }
    public String getClanPvpDisabledText() { return clanPvpDisabledText; }
    public ClanUpgradesSettings getUpgrades() { return upgrades; }
    public void setUpgrades(ClanUpgradesSettings upgrades) { this.upgrades = upgrades != null ? upgrades : ClanUpgradePack.createUpgrades(); }

    public UpgradeCategory findUpgradeCategory(String type) {
        if (upgrades == null || upgrades.getCategories() == null || type == null) {
            return null;
        }
        String key = type.trim().toUpperCase(Locale.ROOT);
        UpgradeCategory direct = upgrades.getCategories().get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, UpgradeCategory> entry : upgrades.getCategories().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class ClanUpgradesSettings extends OkaeriConfig {
        private boolean enabled = true;
        private String guiTitle = "&8Ulepszenia klanu";
        private String stagesGuiTitle = "&8Poziomy ulepszenia";
        private String currencyName = "&dMoneta klanowa";
        private int guiRows = 6;
        private int backSlot = 49;
        private String backName = "&cPowrót";
        private UpgradeGuiItem upgradeInfo = new UpgradeGuiItem();
        private UpgradeGuiItem upgradeItem = new UpgradeGuiItem();
        private Map<String, UpgradeCategory> categories = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getGuiTitle() { return guiTitle; }
        public void setGuiTitle(String guiTitle) { this.guiTitle = guiTitle; }
        public String getStagesGuiTitle() { return stagesGuiTitle; }
        public void setStagesGuiTitle(String stagesGuiTitle) { this.stagesGuiTitle = stagesGuiTitle; }
        public String getCurrencyName() { return currencyName; }
        public void setCurrencyName(String currencyName) { this.currencyName = currencyName; }
        public int getGuiRows() { return guiRows; }
        public void setGuiRows(int guiRows) { this.guiRows = guiRows; }
        public int getBackSlot() { return backSlot; }
        public void setBackSlot(int backSlot) { this.backSlot = backSlot; }
        public String getBackName() { return backName; }
        public void setBackName(String backName) { this.backName = backName; }
        public UpgradeGuiItem getUpgradeInfo() { return upgradeInfo; }
        public void setUpgradeInfo(UpgradeGuiItem upgradeInfo) { this.upgradeInfo = upgradeInfo; }
        public UpgradeGuiItem getUpgradeItem() { return upgradeItem; }
        public void setUpgradeItem(UpgradeGuiItem upgradeItem) { this.upgradeItem = upgradeItem; }
        public Map<String, UpgradeCategory> getCategories() { return categories; }
        public void setCategories(Map<String, UpgradeCategory> categories) {
            this.categories = categories != null ? categories : new LinkedHashMap<>();
        }
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class UpgradeGuiItem extends OkaeriConfig {
        private String material = "PAPER";
        private String display = "{NAME}";
        private List<String> lore = new ArrayList<>();

        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        public String getDisplay() { return display; }
        public void setDisplay(String display) { this.display = display; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>(); }
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class UpgradeCategory extends OkaeriConfig {
        private String name = "&fUlepszenie";
        private String material = "PAPER";
        private int slot = 22;
        private String description = "";
        private List<UpgradeStage> stages = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        public int getSlot() { return slot; }
        public void setSlot(int slot) { this.slot = slot; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<UpgradeStage> getStages() { return stages; }
        public void setStages(List<UpgradeStage> stages) {
            this.stages = stages != null ? new ArrayList<>(stages) : new ArrayList<>();
        }

        public UpgradeStage findStage(int stageNumber) {
            if (stages == null) {
                return null;
            }
            for (UpgradeStage stage : stages) {
                if (stage != null && stage.getStage() == stageNumber) {
                    return stage;
                }
            }
            return null;
        }
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class UpgradeStage extends OkaeriConfig {
        private int stage = 1;
        private int slot = 20;
        private int price = 50;
        private double amplifier = 1.0;
        private double chance = 0.0;

        public int getStage() { return stage; }
        public void setStage(int stage) { this.stage = stage; }
        public int getSlot() { return slot; }
        public void setSlot(int slot) { this.slot = slot; }
        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }
        public double getAmplifier() { return amplifier; }
        public void setAmplifier(double amplifier) { this.amplifier = amplifier; }
        public double getChance() { return chance; }
        public void setChance(double chance) { this.chance = chance; }
    }
}
