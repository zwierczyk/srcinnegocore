package dev.grzybalson.config.modules.coinflip;

import dev.grzybalson.anarchiagg.util.notice.NoticeType;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import org.bukkit.Material;

import java.util.List;

@Names(strategy=NameStrategy.HYPHEN_CASE, modifier=NameModifier.TO_LOWER_CASE)
public class CoinflipConfig extends OkaeriConfig {

    private Settings settings = new Settings();
    private Gui gui = new Gui();
    private Messages messages = new Messages();

    public boolean isEnabled() {
        return true;
    }

    public Settings getSettings() {
        return this.settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public Gui getGui() {
        return this.gui;
    }

    public void setGui(Gui gui) {
        this.gui = gui;
    }

    public Messages getMessages() {
        return this.messages;
    }

    public void setMessages(Messages messages) {
        this.messages = messages;
    }

    public boolean migrateBrokenDefaults() {
        boolean changed = false;
        if (this.settings == null) {
            this.settings = new Settings();
            changed = true;
        }
        if (this.gui == null) {
            this.gui = new Gui();
            changed = true;
        }
        if (this.messages == null) {
            this.messages = new Messages();
            changed = true;
        }
        if (CoinflipConfig.isBlank(this.messages.getBroadcast())) {
            this.messages.setBroadcast("&#6EF3B8✔ &8Ξ &#6EF3B8Coinflip &8〢 &f%winner% &7wygrał z &f%loser% &8• &#00FF4F%amount_formatted%");
            changed = true;
        }
        if (CoinflipConfig.isBlank(this.messages.getBroadcastMoney())) {
            this.messages.setBroadcastMoney("&#6EF3B8✔ &8Ξ &#6EF3B8Coinflip &8〢 &f%winner% &7wygrał z &f%loser% &8• &#00FF4F%amount%$");
            changed = true;
        }
        if (CoinflipConfig.isBlank(this.messages.getBroadcastShards())) {
            this.messages.setBroadcastShards("&#6EF3B8✔ &8Ξ &#6EF3B8Coinflip &8〢 &f%winner% &7wygrał z &f%loser% &8• &#B857FF%amount% odłamków");
            changed = true;
        }
        if (CoinflipConfig.isBlank(this.settings.getMoneyDisplayName())) {
            this.settings.setMoneyDisplayName("pieniadze");
            changed = true;
        }
        if (CoinflipConfig.isBlank(this.settings.getShardsDisplayName())) {
            this.settings.setShardsDisplayName("odlamki");
            changed = true;
        }
        if (CoinflipConfig.isBlank(this.settings.getGamesMongoCollection())) {
            this.settings.setGamesMongoCollection("coinflip_games");
            changed = true;
        }
        if (CoinflipConfig.isBlank(this.messages.getDuelRefunded())) {
            this.messages.setDuelRefunded("&eCoinflip zostal przerwany. Zwrocono stawke &f%amount% &e(%currency%).");
            changed = true;
        }
        if (this.gui.getInfoItem() == null || CoinflipConfig.looksBroken(this.gui.getInfoItem().getName()) || CoinflipConfig.looksBroken(this.gui.getInfoItem().getLore())) {
            this.gui.setInfoItem(CoinflipConfig.defaultInfoItem());
            changed = true;
        }
        if (this.gui.getOwnGameHead() == null) {
            this.gui.setOwnGameHead(CoinflipConfig.defaultOwnGameHead());
            changed = true;
        }
        return changed;
    }

    private static GuiItem defaultInfoItem() {
        return new GuiItem(Material.BOOK, "&#6EF3B8&lCOINFLIP", List.of("", "&#FF0048* &aTworz zaklady z innymi graczami", "&#70F36ERywalizuj o to, co najlepsze!", "", "&fJak zrobic coinflip?", "&8>> &f/coinflip &dstworz &fkasa/odlamki ilosc"));
    }

    private static GuiItem defaultOwnGameHead() {
        return new GuiItem(Material.PLAYER_HEAD, "&cCoinflip &8- &f%creator%", List.of("", "&8>> &7Gracz: &f%creator%", "&8>> &7Tryb: &f%currency%", "&8>> &7Stawka: &a%amount%", "", "&cKliknij, aby usunac i odzyskac stawke!"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean looksBroken(List<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (!CoinflipConfig.looksBroken(line)) continue;
            return true;
        }
        return false;
    }

    private static boolean looksBroken(String value) {
        return value != null && (value.indexOf(258) >= 0 || value.indexOf(313) >= 0 || value.indexOf(196) >= 0 || value.indexOf(226) >= 0 || value.indexOf(225) >= 0 || value.indexOf(201) >= 0 || value.indexOf(280) >= 0);
    }

    public static class Settings extends OkaeriConfig {
        private String mongoCollection = "coinflip_rewards";
        private String gamesMongoCollection = "coinflip_games";
        private double minMoneyBet = 100.0;
        private double maxMoneyBet = 1.0E9;
        private int minShardsBet = 1;
        private int maxShardsBet = 256;
        private int animationTicks = 80;
        private int animationStepTicks = 4;
        private List<Integer> animationDelayTicks = List.of(
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                2, 2, 2, 2, 2,
                3, 3, 3, 3,
                4, 4, 5,
                6, 8, 10,
                13, 16,
                20,
                5,
                30
        );
        private String animationSound = "block.note_block.pling";
        private float animationVolume = 1.0f;
        private float animationPitch = 1.2f;
        private int guiRefreshSeconds = 2;
        private String moneyDisplayName = "pieniadze";
        private String shardsDisplayName = "odlamki";

        public String getMongoCollection() {
            return this.mongoCollection;
        }

        public void setMongoCollection(String mongoCollection) {
            this.mongoCollection = mongoCollection;
        }

        public String getGamesMongoCollection() {
            return this.gamesMongoCollection;
        }

        public void setGamesMongoCollection(String gamesMongoCollection) {
            this.gamesMongoCollection = gamesMongoCollection;
        }

        public double getMinMoneyBet() {
            return this.minMoneyBet;
        }

        public void setMinMoneyBet(double minMoneyBet) {
            this.minMoneyBet = minMoneyBet;
        }

        public double getMaxMoneyBet() {
            return this.maxMoneyBet;
        }

        public void setMaxMoneyBet(double maxMoneyBet) {
            this.maxMoneyBet = maxMoneyBet;
        }

        public int getMinShardsBet() {
            return this.minShardsBet;
        }

        public void setMinShardsBet(int minShardsBet) {
            this.minShardsBet = minShardsBet;
        }

        public int getMaxShardsBet() {
            return this.maxShardsBet;
        }

        public void setMaxShardsBet(int maxShardsBet) {
            this.maxShardsBet = maxShardsBet;
        }

        public int getAnimationTicks() {
            return this.animationTicks;
        }

        public void setAnimationTicks(int animationTicks) {
            this.animationTicks = animationTicks;
        }

        public int getAnimationStepTicks() {
            return this.animationStepTicks;
        }

        public void setAnimationStepTicks(int animationStepTicks) {
            this.animationStepTicks = animationStepTicks;
        }

        public List<Integer> getAnimationDelayTicks() {
            return this.animationDelayTicks;
        }

        public void setAnimationDelayTicks(List<Integer> animationDelayTicks) {
            this.animationDelayTicks = animationDelayTicks;
        }

        public int[] resolveAnimationDelayTicks() {
            if (this.animationDelayTicks == null || this.animationDelayTicks.isEmpty()) {
                return new int[]{
                        1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                        2, 2, 2, 2, 2,
                        3, 3, 3, 3,
                        4, 4, 5,
                        6, 8, 10,
                        13, 16,
                        20,
                        5,
                        30
                };
            }
            int[] out = new int[this.animationDelayTicks.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = Math.max(1, this.animationDelayTicks.get(i));
            }
            return out;
        }

        public String getAnimationSound() {
            return this.animationSound;
        }

        public void setAnimationSound(String animationSound) {
            this.animationSound = animationSound;
        }

        public float getAnimationVolume() {
            return this.animationVolume;
        }

        public void setAnimationVolume(float animationVolume) {
            this.animationVolume = animationVolume;
        }

        public float getAnimationPitch() {
            return this.animationPitch;
        }

        public void setAnimationPitch(float animationPitch) {
            this.animationPitch = animationPitch;
        }

        public int getGuiRefreshSeconds() {
            return this.guiRefreshSeconds;
        }

        public void setGuiRefreshSeconds(int guiRefreshSeconds) {
            this.guiRefreshSeconds = guiRefreshSeconds;
        }

        public String getMoneyDisplayName() {
            return this.moneyDisplayName;
        }

        public void setMoneyDisplayName(String moneyDisplayName) {
            this.moneyDisplayName = moneyDisplayName;
        }

        public String getShardsDisplayName() {
            return this.shardsDisplayName;
        }

        public void setShardsDisplayName(String shardsDisplayName) {
            this.shardsDisplayName = shardsDisplayName;
        }
    }

    public static class Gui extends OkaeriConfig {
        private String mainTitle = "&7Coinflip";
        private String claimTitle = "&7Coinflip";
        private String duelTitle = "&8Coinflip &7- &f%creator% &8vs &f%opponent%";
        private GuiItem infoItem = CoinflipConfig.defaultInfoItem();
        private GuiItem closeItem = new GuiItem(Material.BARRIER, "&cZamknij", List.of());
        private GuiItem nextPageItem = new GuiItem(Material.SPECTRAL_ARROW, "&aNastepna strona", List.of());
        private GuiItem previousPageItem = new GuiItem(Material.SPECTRAL_ARROW, "&cPoprzednia strona", List.of());
        private GuiItem sortItem = new GuiItem(Material.REPEATER, "&fSortowanie", List.of("", "&8>> %all_line%", "&8>> %shards_line%", "&8>> %money_line%", "", "&aKliknij, aby przelaczyc!"));
        private GuiItem gameHead = new GuiItem(Material.PLAYER_HEAD, "&aCoinflip &8- &f%creator%", List.of("", "&8>> &7Gracz: &f%creator%", "&8>> &7Tryb: &f%currency%", "&8>> &7Stawka: &a%amount%", "", "&aKliknij, aby dolaczyc!"));
        private GuiItem gameHeadMoney = new GuiItem(Material.PLAYER_HEAD, "&aCoinflip &8- &f%creator%", List.of(" &7⏩ &7Utworzona przez: &6%creator%", " &7⏩ &7Ilość: &a%amount%$", "", "&cKliknij tutaj, aby zagrać!"));
        private GuiItem gameHeadShards = new GuiItem(Material.PLAYER_HEAD, "&dCoinflip &8- &f%creator%", List.of(" &7⏩ &7Utworzona przez: &6%creator%", " &7⏩ &7Ilość&7: &fx%amount% &x&8&A&4&B&B&8&lS&x&9&1&4&D&C&2&lm&x&9&7&4&E&C&C&lo&x&9&E&5&0&D&6&lc&x&A&4&5&2&E&1&lz&x&A&B&5&4&E&B&ly&x&B&1&5&5&F&5&lc&x&B&8&5&7&F&F&lh &lo&ld&lł&la&lm&lk&ló&lw", "", "&cKliknij tutaj, aby wylosować wygranego!"));
        private GuiItem ownGameHead = CoinflipConfig.defaultOwnGameHead();
        private GuiItem ownGameHeadMoney = new GuiItem(Material.PLAYER_HEAD, "&cCoinflip &8- &f%creator%", List.of(" &7⏩ &7Utworzona przez: &6%creator%", " &7⏩ &7Ilość: &a%amount%$", "", "&cKliknij, aby usunac i odzyskac stawke!"));
        private GuiItem ownGameHeadShards = new GuiItem(Material.PLAYER_HEAD, "&cCoinflip &8- &f%creator%", List.of(" &7⏩ &7Utworzona przez: &6%creator%", " &7⏩ &7Ilość&7: &fx%amount% &x&8&A&4&B&B&8&lS&x&9&1&4&D&C&2&lm&x&9&7&4&E&C&C&lo&x&9&E&5&0&D&6&lc&x&A&4&5&2&E&1&lz&x&A&B&5&4&E&B&ly&x&B&1&5&5&F&5&lc&x&B&8&5&7&F&F&lh &lo&ld&lł&la&lm&lk&ló&lw", "", "&cKliknij, aby usunac i odzyskac stawke!"));
        private GuiItem moneyClaimItem = new GuiItem(Material.EMERALD, "&a&lKASA DO ODEBRANIA", List.of("", " &8> &7Kwota: &a%amount%$", "", " &aKliknij, aby odebrac."));
        private GuiItem shardsClaimItem = new GuiItem(Material.AMETHYST_SHARD, "&d&lODLAMKI DO ODEBRANIA", List.of("", " &8> &7Ilosc: &d%amount%", "", " &aKliknij, aby odebrac."));
        private GuiItem emptyClaimItem = new GuiItem(Material.HOPPER, "&cBrak nagrod", List.of("", " &7Nie masz aktualnie nic do odebrania."));
        private String selectedSortLine = "&a&n%name%";
        private String unselectedSortLine = "&f%name%";
        private String allSortName = "WSZYSTKIE";
        private String shardsSortName = "ODLAMKI";
        private String moneySortName = "PIENIADZE";

        public String getMainTitle() {
            return this.mainTitle;
        }

        public void setMainTitle(String mainTitle) {
            this.mainTitle = mainTitle;
        }

        public String getClaimTitle() {
            return this.claimTitle;
        }

        public void setClaimTitle(String claimTitle) {
            this.claimTitle = claimTitle;
        }

        public String getDuelTitle() {
            return this.duelTitle;
        }

        public void setDuelTitle(String duelTitle) {
            this.duelTitle = duelTitle;
        }

        public GuiItem getInfoItem() {
            return this.infoItem;
        }

        public void setInfoItem(GuiItem infoItem) {
            this.infoItem = infoItem;
        }

        public GuiItem getCloseItem() {
            return this.closeItem;
        }

        public void setCloseItem(GuiItem closeItem) {
            this.closeItem = closeItem;
        }

        public GuiItem getNextPageItem() {
            return this.nextPageItem;
        }

        public void setNextPageItem(GuiItem nextPageItem) {
            this.nextPageItem = nextPageItem;
        }

        public GuiItem getPreviousPageItem() {
            return this.previousPageItem;
        }

        public void setPreviousPageItem(GuiItem previousPageItem) {
            this.previousPageItem = previousPageItem;
        }

        public GuiItem getSortItem() {
            return this.sortItem;
        }

        public void setSortItem(GuiItem sortItem) {
            this.sortItem = sortItem;
        }

        public GuiItem getGameHead() {
            return this.gameHead;
        }

        public GuiItem getGameHead(dev.grzybalson.modules.coinflip.CoinflipCurrency currency) {
            if (currency == dev.grzybalson.modules.coinflip.CoinflipCurrency.MONEY && gameHeadMoney != null) {
                return gameHeadMoney;
            }
            if (currency == dev.grzybalson.modules.coinflip.CoinflipCurrency.SHARDS && gameHeadShards != null) {
                return gameHeadShards;
            }
            return getGameHead();
        }

        public void setGameHead(GuiItem gameHead) {
            this.gameHead = gameHead;
        }

        public GuiItem getGameHeadMoney() {
            return this.gameHeadMoney;
        }

        public void setGameHeadMoney(GuiItem gameHeadMoney) {
            this.gameHeadMoney = gameHeadMoney;
        }

        public GuiItem getGameHeadShards() {
            return this.gameHeadShards;
        }

        public void setGameHeadShards(GuiItem gameHeadShards) {
            this.gameHeadShards = gameHeadShards;
        }

        public GuiItem getOwnGameHead() {
            return this.ownGameHead == null ? this.gameHead : this.ownGameHead;
        }

        public GuiItem getOwnGameHead(dev.grzybalson.modules.coinflip.CoinflipCurrency currency) {
            if (currency == dev.grzybalson.modules.coinflip.CoinflipCurrency.MONEY && ownGameHeadMoney != null) {
                return ownGameHeadMoney;
            }
            if (currency == dev.grzybalson.modules.coinflip.CoinflipCurrency.SHARDS && ownGameHeadShards != null) {
                return ownGameHeadShards;
            }
            return getOwnGameHead();
        }

        public void setOwnGameHead(GuiItem ownGameHead) {
            this.ownGameHead = ownGameHead;
        }

        public GuiItem getOwnGameHeadMoney() {
            return this.ownGameHeadMoney;
        }

        public void setOwnGameHeadMoney(GuiItem ownGameHeadMoney) {
            this.ownGameHeadMoney = ownGameHeadMoney;
        }

        public GuiItem getOwnGameHeadShards() {
            return this.ownGameHeadShards;
        }

        public void setOwnGameHeadShards(GuiItem ownGameHeadShards) {
            this.ownGameHeadShards = ownGameHeadShards;
        }

        public GuiItem getMoneyClaimItem() {
            return this.moneyClaimItem;
        }

        public void setMoneyClaimItem(GuiItem moneyClaimItem) {
            this.moneyClaimItem = moneyClaimItem;
        }

        public GuiItem getShardsClaimItem() {
            return this.shardsClaimItem;
        }

        public void setShardsClaimItem(GuiItem shardsClaimItem) {
            this.shardsClaimItem = shardsClaimItem;
        }

        public GuiItem getEmptyClaimItem() {
            return this.emptyClaimItem;
        }

        public void setEmptyClaimItem(GuiItem emptyClaimItem) {
            this.emptyClaimItem = emptyClaimItem;
        }

        public String getSelectedSortLine() {
            return this.selectedSortLine;
        }

        public void setSelectedSortLine(String selectedSortLine) {
            this.selectedSortLine = selectedSortLine;
        }

        public String getUnselectedSortLine() {
            return this.unselectedSortLine;
        }

        public void setUnselectedSortLine(String unselectedSortLine) {
            this.unselectedSortLine = unselectedSortLine;
        }

        public String getAllSortName() {
            return this.allSortName;
        }

        public void setAllSortName(String allSortName) {
            this.allSortName = allSortName;
        }

        public String getShardsSortName() {
            return this.shardsSortName;
        }

        public void setShardsSortName(String shardsSortName) {
            this.shardsSortName = shardsSortName;
        }

        public String getMoneySortName() {
            return this.moneySortName;
        }

        public void setMoneySortName(String moneySortName) {
            this.moneySortName = moneySortName;
        }
    }

    public static class Messages extends OkaeriConfig {
        private NoticeType type = NoticeType.CHAT;
        private String disabled = "&cCoinflip jest aktualnie wylaczony.";
        private String invalidCurrency = "&cNieprawidlowa waluta. Uzyj: kasa lub odlamki.";
        private String invalidAmount = "&cStawka musi byc wieksza od zera.";
        private String onlyWholeShards = "&cOdlamki musza byc liczba calkowita.";
        private String amountTooLow = "&cMinimalna stawka dla tej waluty to &f%amount%&c.";
        private String amountTooHigh = "&cMaksymalna stawka dla tej waluty to &f%amount%&c.";
        private String notEnoughMoney = "&cNie masz wystarczajaco pieniedzy.";
        private String notEnoughShards = "&cNie masz wystarczajaco odlamkow.";
        private String gameCreated = "&aUtworzono coinflip za &f%amount% &a(%currency%).";
        private String gameCreatedMoney = "&aUtworzono coinflip za &f%amount%$ &a(Pieniądze).";
        private String gameCreatedShards = "&aUtworzono coinflip za &f%amount% &a(Smocze Odłamki).";

        public String getGameCreated(dev.grzybalson.modules.coinflip.CoinflipCurrency currency) {
            if (currency == dev.grzybalson.modules.coinflip.CoinflipCurrency.MONEY && gameCreatedMoney != null && !gameCreatedMoney.isBlank()) {
                return gameCreatedMoney;
            }
            if (currency == dev.grzybalson.modules.coinflip.CoinflipCurrency.SHARDS && gameCreatedShards != null && !gameCreatedShards.isBlank()) {
                return gameCreatedShards;
            }
            return this.gameCreated;
        }
        private String gameCreatedSubtitle = "&aStworzono coinflipa!";
        private String gameCancelled = "&aUsunieto coinflip za &f%amount% &a(%currency%) i zwrocono stawke.";
        private String gameUnavailable = "&cTa rozgrywka nie jest juz dostepna.";
        private String ownGame = "&cNie mozesz dolaczyc do swojej rozgrywki.";
        private String duelStarted = "&aDolaczyles do rozgrywki gracza &f%creator%&a.";
        private String duelRefunded = "&eCoinflip zostal przerwany. Zwrocono stawke &f%amount% &e(%currency%).";
        private String winner = "&aWygrales coinflip z &f%opponent% &ai otrzymujesz &f%amount% &a(%currency%).";
        private String loser = "&cPrzegrales coinflip z &f%opponent%&c.";
        private boolean broadcastEnabled = true;
        private String broadcast = "&#6EF3B8✔ &8Ξ &#6EF3B8Coinflip &8〢 &f%winner% &7wygrał z &f%loser% &8• &#00FF4F%amount_formatted%";
        private String broadcastMoney = "&#6EF3B8✔ &8Ξ &#6EF3B8Coinflip &8〢 &f%winner% &7wygrał z &f%loser% &8• &#00FF4F%amount%$";
        private String broadcastShards = "&#6EF3B8✔ &8Ξ &#6EF3B8Coinflip &8〢 &f%winner% &7wygrał z &f%loser% &8• &#B857FF%amount% odłamków";
        private String rewardSaved = "&aNagroda zostala zapisana.";
        private String moneyRewardPaid = "&aWygrana zostala dodana na konto.";
        private String shardsRewardSaved = "&aOdlamki zostaly zapisane. Odbierz je komenda &f/coinflip claim&a.";
        private String claimNothing = "&cNie masz nic do odebrania.";
        private String claimMoney = "&aOdebrano &f%amount%$ &ana konto.";
        private String claimShards = "&aOdebrano &f%amount% &aodlamkow.";
        private String claimError = "&cWystapil blad podczas obslugi nagrody.";

        public NoticeType getType() {
            return this.type;
        }

        public void setType(NoticeType type) {
            this.type = type;
        }

        public String getDisabled() {
            return this.disabled;
        }

        public void setDisabled(String disabled) {
            this.disabled = disabled;
        }

        public String getInvalidCurrency() {
            return this.invalidCurrency;
        }

        public void setInvalidCurrency(String invalidCurrency) {
            this.invalidCurrency = invalidCurrency;
        }

        public String getInvalidAmount() {
            return this.invalidAmount;
        }

        public void setInvalidAmount(String invalidAmount) {
            this.invalidAmount = invalidAmount;
        }

        public String getOnlyWholeShards() {
            return this.onlyWholeShards;
        }

        public void setOnlyWholeShards(String onlyWholeShards) {
            this.onlyWholeShards = onlyWholeShards;
        }

        public String getAmountTooLow() {
            return this.amountTooLow;
        }

        public void setAmountTooLow(String amountTooLow) {
            this.amountTooLow = amountTooLow;
        }

        public String getAmountTooHigh() {
            return this.amountTooHigh;
        }

        public void setAmountTooHigh(String amountTooHigh) {
            this.amountTooHigh = amountTooHigh;
        }

        public String getNotEnoughMoney() {
            return this.notEnoughMoney;
        }

        public void setNotEnoughMoney(String notEnoughMoney) {
            this.notEnoughMoney = notEnoughMoney;
        }

        public String getNotEnoughShards() {
            return this.notEnoughShards;
        }

        public void setNotEnoughShards(String notEnoughShards) {
            this.notEnoughShards = notEnoughShards;
        }

        public String getGameCreated() {
            return this.gameCreated;
        }

        public void setGameCreated(String gameCreated) {
            this.gameCreated = gameCreated;
        }

        public String getGameCreatedSubtitle() {
            return this.gameCreatedSubtitle;
        }

        public void setGameCreatedSubtitle(String gameCreatedSubtitle) {
            this.gameCreatedSubtitle = gameCreatedSubtitle;
        }

        public String getGameCancelled() {
            return this.gameCancelled;
        }

        public void setGameCancelled(String gameCancelled) {
            this.gameCancelled = gameCancelled;
        }

        public String getGameUnavailable() {
            return this.gameUnavailable;
        }

        public void setGameUnavailable(String gameUnavailable) {
            this.gameUnavailable = gameUnavailable;
        }

        public String getOwnGame() {
            return this.ownGame;
        }

        public void setOwnGame(String ownGame) {
            this.ownGame = ownGame;
        }

        public String getDuelStarted() {
            return this.duelStarted;
        }

        public void setDuelStarted(String duelStarted) {
            this.duelStarted = duelStarted;
        }

        public String getDuelRefunded() {
            return this.duelRefunded;
        }

        public void setDuelRefunded(String duelRefunded) {
            this.duelRefunded = duelRefunded;
        }

        public String getWinner() {
            return this.winner;
        }

        public void setWinner(String winner) {
            this.winner = winner;
        }

        public String getLoser() {
            return this.loser;
        }

        public void setLoser(String loser) {
            this.loser = loser;
        }

        public boolean isBroadcastEnabled() {
            return this.broadcastEnabled;
        }

        public void setBroadcastEnabled(boolean broadcastEnabled) {
            this.broadcastEnabled = broadcastEnabled;
        }

        public String getBroadcast() {
            return this.broadcast;
        }

        public String getBroadcast(dev.grzybalson.modules.coinflip.CoinflipCurrency currency) {
            if (currency == dev.grzybalson.modules.coinflip.CoinflipCurrency.MONEY && this.broadcastMoney != null && !this.broadcastMoney.isBlank()) {
                return this.broadcastMoney;
            }
            if (currency == dev.grzybalson.modules.coinflip.CoinflipCurrency.SHARDS && this.broadcastShards != null && !this.broadcastShards.isBlank()) {
                return this.broadcastShards;
            }
            return this.broadcast;
        }

        public void setBroadcast(String broadcast) {
            this.broadcast = broadcast;
        }

        public String getBroadcastMoney() {
            return this.broadcastMoney;
        }

        public void setBroadcastMoney(String broadcastMoney) {
            this.broadcastMoney = broadcastMoney;
        }

        public String getBroadcastShards() {
            return this.broadcastShards;
        }

        public void setBroadcastShards(String broadcastShards) {
            this.broadcastShards = broadcastShards;
        }

        public String getRewardSaved() {
            return this.rewardSaved;
        }

        public void setRewardSaved(String rewardSaved) {
            this.rewardSaved = rewardSaved;
        }

        public String getMoneyRewardPaid() {
            return this.moneyRewardPaid;
        }

        public void setMoneyRewardPaid(String moneyRewardPaid) {
            this.moneyRewardPaid = moneyRewardPaid;
        }

        public String getShardsRewardSaved() {
            return this.shardsRewardSaved;
        }

        public void setShardsRewardSaved(String shardsRewardSaved) {
            this.shardsRewardSaved = shardsRewardSaved;
        }

        public String getClaimNothing() {
            return this.claimNothing;
        }

        public void setClaimNothing(String claimNothing) {
            this.claimNothing = claimNothing;
        }

        public String getClaimMoney() {
            return this.claimMoney;
        }

        public void setClaimMoney(String claimMoney) {
            this.claimMoney = claimMoney;
        }

        public String getClaimShards() {
            return this.claimShards;
        }

        public void setClaimShards(String claimShards) {
            this.claimShards = claimShards;
        }

        public String getClaimError() {
            return this.claimError;
        }

        public void setClaimError(String claimError) {
            this.claimError = claimError;
        }
    }

    public static class GuiItem extends OkaeriConfig {
        private Material material;
        private String name;
        private List<String> lore;
        private int customModelData = 0;

        public GuiItem() {
        }

        public GuiItem(Material material, String name, List<String> lore) {
            this.material = material;
            this.name = name;
            this.lore = lore;
        }

        public Material getMaterial() {
            return this.material;
        }

        public void setMaterial(Material material) {
            this.material = material;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }
    }
}
