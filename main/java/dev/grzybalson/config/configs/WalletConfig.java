package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class WalletConfig extends OkaeriConfig {

    private boolean enabled = true;
    private String currencyName = "wPLN";
    private MainGui gui = new MainGui();
    private BalanceItem balanceItem = new BalanceItem();
    private BuyBar buyBar = new BuyBar();
    private RechargeAnnouncement rechargeAnnouncement = new RechargeAnnouncement();
    private LogsGui logsGui = new LogsGui();
    private Messages messages = new Messages();
    private AdminProductStats adminProductStats = new AdminProductStats();
    private Map<Integer, ServiceCategory> services = new HashMap<>();

    public WalletConfig() {
        if (services.isEmpty()) {
            ServiceCategory category = new ServiceCategory();
            category.getItem().getMeta().setDisplay("&b&lRangi premium");
            category.getItem().getMeta().setLore(new ArrayList<>(List.of(
                "",
                " &8» &fKliknij aby przejść dalej!",
                ""
            )));

            WalletProduct vip = new WalletProduct();
            vip.setSlot(10);
            vip.getItem().setMaterial("LEATHER_HELMET");
            vip.getItem().getMeta().setDisplay("&f&lVIP &8(&730 dni&8)");
            vip.getItem().getMeta().setLore(new ArrayList<>(List.of(
                "",
                " &8» &fCena: &a5.0 wPLN",
                "",
                " &aKliknij, aby zakupić"
            )));
            vip.setPrice(5.0);
            vip.setCommands(new ArrayList<>(List.of("lp user {PLAYER} parent addtemp vip 30d")));
            category.getCategories().add(vip);
            services.put(21, category);
        }
    }

    public boolean isEnabled() { return enabled; }
    public String getCurrencyName() { return currencyName; }
    public MainGui getGui() { return gui; }
    public BalanceItem getBalanceItem() { return balanceItem; }
    public BuyBar getBuyBar() { return buyBar; }
    public RechargeAnnouncement getRechargeAnnouncement() { return rechargeAnnouncement; }
    public LogsGui getLogsGui() { return logsGui; }
    public Messages getMessages() { return messages; }
    public AdminProductStats getAdminProductStats() { return adminProductStats; }
    public Map<Integer, ServiceCategory> getServices() { return services; }

    public static class MainGui extends OkaeriConfig {
        private String title = "&8Portfel";
        private int rows = 5;
        public String getTitle() { return title; }
        public int getRows() { return rows; }
    }

    public static class BalanceItem extends OkaeriConfig {
        private int slot = 40;
        private DisplayItem item = new DisplayItem();

        public BalanceItem() {
            item.setMaterial("GOLD_INGOT");
            item.getMeta().setDisplay("&6&lTwój portfel");
            item.getMeta().setLore(new ArrayList<>(List.of(
                "",
                " &8» &fStan konta: &a{BALANCE} {CURRENCY}",
                ""
            )));
        }

        public int getSlot() { return slot; }
        public DisplayItem getItem() { return item; }
    }

    public static class BuyBar extends OkaeriConfig {
        private boolean enabled = true;
        private String title = "&8‹ &7Gracz &f{PLAYER} &7zakupił &f{SERVICE} &7za &a{PRICE} {CURRENCY} &8›";
        private String barStyle = "SEGMENTED_6";
        private String barColor = "RED";
        private int ticks = 80;
        private double rainbowMinPrice = 100.0;
        private int rainbowIntervalTicks = 4;
        private String premiumSound = "ENTITY_ENDER_DRAGON_DEATH";

        public boolean isEnabled() { return enabled; }
        public String getTitle() { return title; }
        public String getBarStyle() { return barStyle; }
        public String getBarColor() { return barColor; }
        public int getTicks() { return ticks; }
        public double getRainbowMinPrice() { return rainbowMinPrice; }
        public int getRainbowIntervalTicks() { return rainbowIntervalTicks; }
        public String getPremiumSound() { return premiumSound; }
    }

    public static class RechargeAnnouncement extends OkaeriConfig {
        private String soundName = "ENTITY_PLAYER_LEVELUP";
        private List<RechargeSound> amountSounds = new ArrayList<>(List.of(
            new RechargeSound(5.0, "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f, 1.0f),
            new RechargeSound(20.0, "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f),
            new RechargeSound(50.0, "UI_TOAST_CHALLENGE_COMPLETE", 1.0f, 1.0f),
            new RechargeSound(100.0, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, 1.0f)
        ));
        private int titleFadeIn = 10;
        private int titleStay = 60;
        private int titleFadeOut = 10;
        private List<String> messages = new ArrayList<>(List.of(
            "",
            "&8‹◆ &a&lPORTFEL &8◆›",
            " &8× &7Gracz &f{PLAYER} &7doładował portfel o &a{AMOUNT} {CURRENCY}",
            ""
        ));
        private String title = "&a&lPORTFEL";
        private String subTitle = "&7Gracz &f{PLAYER} &7doładował &a{AMOUNT} {CURRENCY}";

        public String getSoundName() { return soundName; }
        public List<RechargeSound> getAmountSounds() { return amountSounds; }
        public int getTitleFadeIn() { return titleFadeIn; }
        public int getTitleStay() { return titleStay; }
        public int getTitleFadeOut() { return titleFadeOut; }
        public List<String> getMessages() { return messages; }
        public String getTitle() { return title; }
        public String getSubTitle() { return subTitle; }
    }

    public static class RechargeSound extends OkaeriConfig {
        private double amount = 0.0;
        private String soundName = "ENTITY_PLAYER_LEVELUP";
        private float volume = 1.0f;
        private float pitch = 1.0f;

        public RechargeSound() {
        }

        public RechargeSound(double amount, String soundName, float volume, float pitch) {
            this.amount = amount;
            this.soundName = soundName;
            this.volume = volume;
            this.pitch = pitch;
        }

        public double getAmount() { return amount; }
        public String getSoundName() { return soundName; }
        public float getVolume() { return volume; }
        public float getPitch() { return pitch; }
    }

    public static class Messages extends OkaeriConfig {
        private String disabled = "&#FF0000✘ &8Ξ &cSystem portfela jest wyłączony.";
        private String playerOnly = "&#FF0000✘ &8Ξ &cTa komenda jest tylko dla gracza.";
        private String noPermission = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/portfel reload &8(&4Holly.cmd.aportfel&8)";
        private String usageAdmin = "&#FF0000✘ &8Ξ &cPoprawne użycie: &#FF0000\n &8• &4/aportfel add <nick> <ilość>\n &8• &4/aportfel remove <nick> <ilość>\n &8• &4/aportfel set <nick> <ilość>\n &8• &4/aportfel doladowanie <nick> <ilość>\n &8• &4/aportfel logs <nick>\n &8• &4/aportfel total\n &8• &4/aportfel reload";
        private String invalidAmount = "&#FF0000✘ &8Ξ &cPodaj poprawną liczbę.";
        private String playerNotFound = "&#FF0000✘ &8Ξ &cNie znaleziono gracza &4{PLAYER}&c.";
        private String balanceSelf = "&#00FF3B✔ &8Ξ &7Stan konta: &a{BALANCE} {CURRENCY}";
        private String balanceTarget = "&#00FF3B✔ &8Ξ &7Stan konta gracza &f{PLAYER}&7: &a{BALANCE} {CURRENCY}";
        private String added = "&#00FF3B✔ &8Ξ &#4EFA76Dodano &f{AMOUNT} {CURRENCY} &agraczowi &f{PLAYER}&a.";
        private String removed = "&#00FF3B✔ &8Ξ &#4EFA76Usunięto &f{AMOUNT} {CURRENCY} &agraczowi &f{PLAYER}&a.";
        private String set = "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono stan konta gracza &f{PLAYER} &ana &f{AMOUNT} {CURRENCY}&a.";
        private String insufficientFunds = "&#FF0000✘ &8Ξ &cNie masz wystarczająco {CURRENCY}.";
        private String bought = "&#00FF3B✔ &8Ξ &#4EFA76Zakupiono usługę &f{SERVICE} &aza &f{PRICE} {CURRENCY}&a.";
        private String boughtConsole = "&#00FF3B✔ &8Ξ &#4EFA76Wykonano zakup usługi &f{SERVICE}&a.";
        private String rechargeAdded = "&#00FF3B✔ &8Ξ &#4EFA76Doładowano portfel gracza &f{PLAYER} &akwotą &f{AMOUNT} {CURRENCY}&a.";
        private String total = "&#00FF3B✔ &8Ξ &7Łączna wartość doładowań serwera: &a{TOTAL} {CURRENCY}";
        private String noLogs = "&#FF0000✘ &8Ξ &cBrak logów portfela dla tego gracza.";
        private String reloaded = "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano konfigurację portfela.";

        public String getDisabled() { return disabled; }
        public String getPlayerOnly() { return playerOnly; }
        public String getNoPermission() { return noPermission; }
        public String getUsageAdmin() { return usageAdmin; }
        public String getInvalidAmount() { return invalidAmount; }
        public String getPlayerNotFound() { return playerNotFound; }
        public String getBalanceSelf() { return balanceSelf; }
        public String getBalanceTarget() { return balanceTarget; }
        public String getAdded() { return added; }
        public String getRemoved() { return removed; }
        public String getSet() { return set; }
        public String getInsufficientFunds() { return insufficientFunds; }
        public String getBought() { return bought; }
        public String getBoughtConsole() { return boughtConsole; }
        public String getRechargeAdded() { return rechargeAdded; }
        public String getTotal() { return total; }
        public String getNoLogs() { return noLogs; }
        public String getReloaded() { return reloaded; }
    }

    public static class LogsGui extends OkaeriConfig {
        private String title = "&8Logi portfela &7{PLAYER}";
        private int rows = 5;
        private int previousSlot = 39;
        private int nextSlot = 41;
        private int backSlot = 40;

        public String getTitle() { return title; }
        public int getRows() { return rows; }
        public int getPreviousSlot() { return previousSlot; }
        public int getNextSlot() { return nextSlot; }
        public int getBackSlot() { return backSlot; }
    }

    public static class AdminProductStats extends OkaeriConfig {
        private boolean enabled = true;
        private String permission = "Holly.cmd.aportfel";
        private List<String> lore = new ArrayList<>(List.of(
            "",
            "&8&m--------------------",
            "&c&lSTATYSTYKI ADMINA",
            "&7Kupilo: &f{BUYERS} &7osob &8(&f{PURCHASES} &7zakupow&8)",
            "&7Zarobek: &a{REVENUE} {CURRENCY}",
            "&8&m--------------------"
        ));

        public boolean isEnabled() { return enabled; }
        public String getPermission() { return permission; }
        public List<String> getLore() { return lore; }
    }

    public static class ServiceCategory extends OkaeriConfig {
        private String sound = "UI_BUTTON_CLICK";
        private DisplayItem item = new DisplayItem();
        private String guiTitle = "&8Portfel";
        private int guiRows = 5;
        private List<WalletProduct> categories = new ArrayList<>();

        public String getSound() { return sound; }
        public DisplayItem getItem() { return item; }
        public String getGuiTitle() { return guiTitle; }
        public int getGuiRows() { return guiRows; }
        public List<WalletProduct> getCategories() { return categories; }
    }

    public static class WalletProduct extends OkaeriConfig {
        private int slot = 0;
        private String sound = "ENTITY_EXPERIENCE_ORB_PICKUP";
        private DisplayItem item = new DisplayItem();
        private double price = 0.0;
        private List<String> commands = new ArrayList<>();
        private List<String> playerCommands = new ArrayList<>();
        private boolean closeAfterBuy = true;

        public int getSlot() { return slot; }
        public void setSlot(int slot) { this.slot = slot; }
        public String getSound() { return sound; }
        public DisplayItem getItem() { return item; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public List<String> getCommands() { return commands; }
        public void setCommands(List<String> commands) { this.commands = commands; }
        public List<String> getPlayerCommands() { return playerCommands; }
        public boolean isCloseAfterBuy() { return closeAfterBuy; }
    }

    public static class DisplayItem extends OkaeriConfig {
        private String material = "STONE";
        private int amount = 1;
        private String headValue = "";
        private ItemMetaData meta = new ItemMetaData();

        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        public int getAmount() { return amount; }
        public String getHeadValue() { return headValue; }
        public ItemMetaData getMeta() { return meta; }
    }

    public static class ItemMetaData extends OkaeriConfig {
        private String display = "&fPrzedmiot";
        private List<String> lore = new ArrayList<>();
        private int customModelData = 0;
        private boolean unbreakable = false;
        private List<String> itemFlags = new ArrayList<>();

        public String getDisplay() { return display; }
        public void setDisplay(String display) { this.display = display; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public int getCustomModelData() { return customModelData; }
        public boolean isUnbreakable() { return unbreakable; }
        public List<String> getItemFlags() { return itemFlags; }
    }
}
