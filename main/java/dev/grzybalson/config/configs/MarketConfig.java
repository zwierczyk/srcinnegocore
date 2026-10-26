package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class MarketConfig extends OkaeriConfig {

    private String marketGuiTitle = "&5&lRynek";

    private int marketGuiRows = 6;

    @CustomKey("product-info")
    private List<String> productInfo = Arrays.asList(
        "",
        "&f★ &fSprzedawca: &b{seller}",
        "&f$ &fCena: &a${price} &8(${price_int})",
        "&f⌚ &fWygasa za: &e{expires}",
        "",
        "{action}"
    );

    private String listingSellerLine = "&f★ &fSprzedawca: &b{seller}";
    private String listingPriceLine = "&f$ &fCena: &a${price} &8(${price_int})";
    private String listingExpiresLine = "&f⌚ &fWygasa za: &e{expires}";
    private String listingWithdrawAction = "&f✖ &cKliknij, aby wycofać przedmiot";
    private String listingBuyAction = "&f✔ &aKliknij, aby zakupić przedmiot";
    private String listingNoMoneyAction = "&f✖ &cNie posiadasz wystarczającej ilości pieniędzy!";
    private String listingMissingMoneyAction = "&f✖ &cBrakuje ci dokładnie: &#DD2525${missing}";
    private String listingAdminAction = "&f✖ &cKliknij z shiftem, aby usunąć &8[&4ADMIN&8]";

    private String myItemsGuiTitle = "&5&lTwoje Oferty";

    private double maxPrice = 10_000_000_000.0;

    private double minPrice = 0.16;

    private int maxItemsPerPlayer = 20;
    private int defaultMaxItems = 20;
    private double commission = 0.05;
    private int expirationDays = 4;
    private String itemExpiredMessage = "&#00E0FFℹ &8Ξ &fTwój przedmiot &e{item} &7wygasł na rynku i został przeniesiony do wygasłych.";
    private String expiredItemsGuiTitle = "&8» &c&lWygasłe przedmioty";

    // Cooldown
    private int actionCooldownMs = 1000;
    private String cooldownMessage = "&#FF0000✘ &8Ξ &cPoczekaj chwilę przed kolejną akcją!";

    private String itemListedMessage = "&aWystawiono przedmiot na rynek za &2${price}";

    private String itemListedVisibilityMessage = "&7Przedmiot będzie do kupna za &e{time}";

    private String itemBoughtMessage = "&#00FF3B✔ &#4EFA76Kupiłeś przedmiot za &e{price}$&a!";
    private String itemSoldMessage = "&#00FF3B✔ &#4EFA76Sprzedałeś przedmiot za &e{price}$&a! &8(prowizja: &c{commission}$&8)";
    private String offlineSaleMessage = "&ePodczas Twojej nieobecności sprzedano przedmioty na łączną kwotę &6{total}$&e!";

    private String itemWithdrawnMessage = "&#00FF3B✔ &#4EFA76Wycofałeś przedmiot z rynku!";

    private String invalidPriceMessage = "&cPodana cena musi być większa niż &4$0.16";

    private String noItemInHandMessage = "&cWeź przedmiot do &4ręki &caby go wystawić!";

    private String maxItemsReachedMessage = "&cPosiadasz limit &4wystawionych &cprzedmiotów na &4rynku!";

    private String blockedItemMessage = "&cTego przedmiotu nie możesz wystawić na &4rynku!";

    private long showTimeSeconds = 60;
    private long showTimeFastSeconds = 5;
    private boolean fastShowTime = false;

    private List<String> blockedMaterialSuffixes = List.of("_BOX");

    private String notEnoughMoneyMessage = "&#FF0000✘ &cNie masz wystarczająco pieniędzy!";

    private String itemNotFoundMessage = "&#FF0000✘ &cTen przedmiot już nie istnieje na rynku!";

    private String cannotBuyOwnItemMessage = "&#FF0000✘ &cNie możesz kupić własnego przedmiotu!";

    private String confirmGuiTitle = "&8» &c&lPotwierdzenie zakupu";
    private String confirmButtonName = "&a&lPOTWIERDŹ ZAKUP";
    private String confirmButtonLore = "&8» &7Kliknij, aby kupić za &a{price}$";
    private String cancelButtonName = "&c&lANULUJ";
    private String cancelButtonLore = "&8» &7Kliknij, aby wrócić do rynku";

    private String myItemsName = "&6&lMOJE PRZEDMIOTY";

    private String sortName = "&e&lSORTOWANIE";

    private String filterArmor = "&b&lZBROJE";
    private String filterTools = "&b&lNARZĘDZIA";
    private String filterEventItems = "&b&lEVENTÓWKI";
    private String filterPremium = "&b&lPREMIUM";

    private String filterEnabledLore = "&aFiltr włączony";

    private String filterDisabledLore = "&cFiltr wyłączony";

    private String filterInstructionLore = "&7LPM - włącz filtr\n&7PPM - wyłącz filtr";

    private String sortNewest = "&aOd najnowszych";
    private String sortOldest = "&eOd najstarszych";
    private String sortCheapest = "&bOd najtańszych";
    private String sortExpensive = "&cOd najdroższych";

    private String refreshButtonName = "&c&lODŚWIEŻ STRONĘ";
    private String refreshButtonLore = "&8» &7Kliknij &cPRAWYM&7, aby odświeżyć";
    private String infoButtonName = "&c&lINFORMACJE";
    private String sortButtonName = "&c&lSORTOWANIE PRZEDMIOTÓW";
    private String sortButtonLore = "&c✎ Aktualnie wybrałeś:";
    private String expiredButtonName = "&c&lWYGASŁE PRZEDMIOTY";
    private String expiredButtonLore = "&8» &7Kliknij &cPRAWYM&7, aby otworzyć &cwygasłe &7itemy";
    private String myItemsButtonName = "&c&lTWOJE PRZEDMIOTY";
    private String myItemsButtonLore = "&8» &7Kliknij &cPRAWYM&7, aby otworzyć &ctwoje &7itemy";
    private String infoLore1 = "&c✎ Informacje na temat rynku:";
    private String infoLore2 = "&8» &7Jest to &fmarket &7na którym możesz";
    private String infoLore3 = "&8» &7sprzedawać oraz kupować &fprzedmioty";
    private String infoLore4 = "&8» &7Aby sprzedać przedmiot musisz wpisać:";
    private String infoLore5 = "&8» &a/rynek sell <cena>";
    private String infoLore6 = "&8» &7Obecnie sprzedajesz &f{current}&7/&f{max} &7rzeczy";

    // Slots
    private int filterArmorSlot = 0;
    private int filterToolsSlot = 9;
    private int filterEventSlot = 18;
    private int filterPremiumSlot = 27;
    private int prevPageSlot = 39;
    private int sortSlot = 40;
    private int nextPageSlot = 41;
    private int refreshSlot = 37;
    private int infoSlot = 4;
    private int expiredSlot = 38;
    private int myItemsButtonSlot = 42;

    // Materials
    private String filterArmorMaterial = "DIAMOND_HELMET";
    private String filterToolsMaterial = "DIAMOND_PICKAXE";
    private String filterEventMaterial = "FISHING_ROD";
    private String filterPremiumMaterial = "GOLD_INGOT";
    private String prevPageMaterial = "ARROW";
    private String nextPageMaterial = "ARROW";
    private String sortMaterial = "HOPPER";
    private String refreshMaterial = "RED_SHULKER_BOX";
    private String infoMaterial = "BOOK";
    private String expiredMaterial = "BELL";
    private String myItemsButtonMaterial = "ENDER_CHEST";

    // Navigation names
    private String prevPageName = "&d&lPoprzednia strona";
    private String nextPageName = "&d&lNastępna strona";
    private String sortButtonClickLore = "&8» &7Kliknij &cPRAWYM&7, aby zmienić sortowanie";

    // CustomModelData for all buttons
    private int filterArmorCustomModelData = 0;
    private int filterToolsCustomModelData = 0;
    private int filterEventCustomModelData = 0;
    private int filterPremiumCustomModelData = 0;
    private int prevPageCustomModelData = 0;
    private int nextPageCustomModelData = 0;
    private int sortCustomModelData = 0;
    private int refreshCustomModelData = 0;
    private int infoCustomModelData = 0;
    private int expiredCustomModelData = 0;
    private int myItemsButtonCustomModelData = 0;

    // Confirm GUI config
    private int confirmGuiRows = 5;
    private int confirmPreviewSlot = 13;
    private int confirmButtonSlot = 21;
    private int cancelButtonSlot = 23;
    private String confirmButtonMaterial = "LIME_DYE";
    private String cancelButtonMaterial = "RED_DYE";
    private int confirmButtonCustomModelData = 0;
    private int cancelButtonCustomModelData = 0;

    // Expired items GUI config
    private String expiredGuiBackButtonName = "&c&lPowrót do rynku";
    private String expiredGuiBackButtonMaterial = "BARRIER";
    private int expiredGuiBackSlot = 40;
    private int expiredGuiPrevPageSlot = 39;
    private int expiredGuiNextPageSlot = 41;
    private String expiredGuiClaimLore = "&cKliknij, aby odebrać";
    private String expiredGuiEmptyMessage = "&7Nie masz wygasłych przedmiotów.";

    // Category items - materials that belong to each category
    private List<String> categoryArmorItems = Arrays.asList(
        "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS",
        "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS",
        "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
        "GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS",
        "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
        "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS",
        "TURTLE_HELMET", "SHIELD", "ELYTRA"
    );

    private List<String> categoryToolsItems = Arrays.asList(
        "WOODEN_SWORD", "STONE_SWORD", "IRON_SWORD", "GOLDEN_SWORD", "DIAMOND_SWORD", "NETHERITE_SWORD",
        "WOODEN_AXE", "STONE_AXE", "IRON_AXE", "GOLDEN_AXE", "DIAMOND_AXE", "NETHERITE_AXE",
        "WOODEN_PICKAXE", "STONE_PICKAXE", "IRON_PICKAXE", "GOLDEN_PICKAXE", "DIAMOND_PICKAXE", "NETHERITE_PICKAXE",
        "WOODEN_SHOVEL", "STONE_SHOVEL", "IRON_SHOVEL", "GOLDEN_SHOVEL", "DIAMOND_SHOVEL", "NETHERITE_SHOVEL",
        "WOODEN_HOE", "STONE_HOE", "IRON_HOE", "GOLDEN_HOE", "DIAMOND_HOE", "NETHERITE_HOE",
        "BOW", "CROSSBOW", "TRIDENT"
    );

    private List<String> categoryEventItems = Arrays.asList(
        "FISHING_ROD", "ENDER_PEARL", "ENDER_EYE", "TOTEM_OF_UNDYING",
        "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE", "END_CRYSTAL"
    );

    private List<String> categoryPremiumItems = Arrays.asList(
        "GOLD_INGOT", "GOLD_BLOCK", "DIAMOND", "DIAMOND_BLOCK",
        "EMERALD", "EMERALD_BLOCK", "NETHERITE_INGOT", "NETHERITE_BLOCK",
        "BEACON", "NETHER_STAR", "DRAGON_EGG", "HEART_OF_THE_SEA"
    );

    private boolean premiumIncludesEnchanted = true;

    public String getMarketGuiTitle() {
        return marketGuiTitle;
    }

    public int getMarketGuiRows() {
        return marketGuiRows;
    }

    public List<String> getProductInfo() {
        return productInfo != null ? productInfo : List.of();
    }

    public String getListingSellerLine() {
        return listingSellerLine;
    }

    public String getListingPriceLine() {
        return listingPriceLine;
    }

    public String getListingExpiresLine() {
        return listingExpiresLine;
    }

    public String getListingWithdrawAction() {
        return listingWithdrawAction;
    }

    public String getListingBuyAction() {
        return listingBuyAction;
    }

    public String getListingNoMoneyAction() {
        return listingNoMoneyAction;
    }

    public String getListingMissingMoneyAction() {
        return listingMissingMoneyAction;
    }

    public String getListingAdminAction() {
        return listingAdminAction;
    }

    public String getMyItemsGuiTitle() {
        return myItemsGuiTitle;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public double getMinPrice() {
        return minPrice;
    }

    public int getMaxItemsPerPlayer() {
        return maxItemsPerPlayer;
    }

    public double getCommission() {
        return commission;
    }

    public String getItemListedMessage() {
        return itemListedMessage;
    }

    public String getItemListedVisibilityMessage() {
        return itemListedVisibilityMessage;
    }

    public String getBlockedItemMessage() {
        return blockedItemMessage;
    }

    public List<String> getBlockedMaterialSuffixes() {
        return blockedMaterialSuffixes == null ? List.of("_BOX") : blockedMaterialSuffixes;
    }

    public long getListingVisibilityMs() {
        long seconds = fastShowTime ? showTimeFastSeconds : showTimeSeconds;
        return Math.max(0L, seconds) * 1000L;
    }

    public boolean isBlockedItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String materialName = item.getType().name();
        for (String suffix : getBlockedMaterialSuffixes()) {
            if (suffix != null && !suffix.isBlank() && materialName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public String getItemBoughtMessage() {
        return itemBoughtMessage;
    }

    public String getItemSoldMessage() {
        return itemSoldMessage;
    }

    public String getOfflineSaleMessage() {
        return offlineSaleMessage;
    }

    public String getItemWithdrawnMessage() {
        return itemWithdrawnMessage;
    }

    public String getInvalidPriceMessage() {
        return invalidPriceMessage;
    }

    public String getNoItemInHandMessage() {
        return noItemInHandMessage;
    }

    public String getMaxItemsReachedMessage() {
        return maxItemsReachedMessage;
    }

    public String getNotEnoughMoneyMessage() {
        return notEnoughMoneyMessage;
    }

    public String getItemNotFoundMessage() {
        return itemNotFoundMessage;
    }

    public String getCannotBuyOwnItemMessage() {
        return cannotBuyOwnItemMessage;
    }

    public String getConfirmGuiTitle() { return confirmGuiTitle; }
    public String getConfirmButtonName() { return confirmButtonName; }
    public String getConfirmButtonLore() { return confirmButtonLore; }
    public String getCancelButtonName() { return cancelButtonName; }
    public String getCancelButtonLore() { return cancelButtonLore; }

    public String getMyItemsName() {
        return myItemsName;
    }

    public String getSortName() {
        return sortName;
    }

    public String getFilterArmor() {
        return filterArmor;
    }

    public String getFilterTools() {
        return filterTools;
    }

    public String getFilterEventItems() {
        return filterEventItems;
    }

    public String getFilterPremium() {
        return filterPremium;
    }

    public String getFilterEnabledLore() {
        return filterEnabledLore;
    }

    public String getFilterDisabledLore() {
        return filterDisabledLore;
    }

    public String getFilterInstructionLore() {
        return filterInstructionLore;
    }

    public String getSortNewest() {
        return sortNewest;
    }

    public String getSortOldest() {
        return sortOldest;
    }

    public String getSortCheapest() {
        return sortCheapest;
    }

    public String getSortExpensive() {
        return sortExpensive;
    }

    public String getRefreshButtonName() { return refreshButtonName; }
    public String getRefreshButtonLore() { return refreshButtonLore; }
    public String getInfoButtonName() { return infoButtonName; }
    public String getSortButtonName() { return sortButtonName; }
    public String getSortButtonLore() { return sortButtonLore; }
    public String getExpiredButtonName() { return expiredButtonName; }
    public String getExpiredButtonLore() { return expiredButtonLore; }
    public String getMyItemsButtonName() { return myItemsButtonName; }
    public String getMyItemsButtonLore() { return myItemsButtonLore; }
    public String getInfoLore1() { return infoLore1; }
    public String getInfoLore2() { return infoLore2; }
    public String getInfoLore3() { return infoLore3; }
    public String getInfoLore4() { return infoLore4; }
    public String getInfoLore5() { return infoLore5; }
    public String getInfoLore6() { return infoLore6; }

    public int getDefaultMaxItems() {
        return defaultMaxItems;
    }

    public int getExpirationDays() {
        return expirationDays;
    }

    public String getItemExpiredMessage() {
        return itemExpiredMessage;
    }

    public String getExpiredItemsGuiTitle() {
        return expiredItemsGuiTitle;
    }

    // Slot getters
    public int getFilterArmorSlot() { return filterArmorSlot; }
    public int getFilterToolsSlot() { return filterToolsSlot; }
    public int getFilterEventSlot() { return filterEventSlot; }
    public int getFilterPremiumSlot() { return filterPremiumSlot; }
    public int getPrevPageSlot() { return prevPageSlot; }
    public int getSortSlot() { return sortSlot; }
    public int getNextPageSlot() { return nextPageSlot; }
    public int getRefreshSlot() { return refreshSlot; }
    public int getInfoSlot() { return infoSlot; }
    public int getExpiredSlot() { return expiredSlot; }
    public int getMyItemsButtonSlot() { return myItemsButtonSlot; }

    // Material getters
    public String getFilterArmorMaterial() { return filterArmorMaterial; }
    public String getFilterToolsMaterial() { return filterToolsMaterial; }
    public String getFilterEventMaterial() { return filterEventMaterial; }
    public String getFilterPremiumMaterial() { return filterPremiumMaterial; }
    public String getPrevPageMaterial() { return prevPageMaterial; }
    public String getNextPageMaterial() { return nextPageMaterial; }
    public String getSortMaterial() { return sortMaterial; }
    public String getRefreshMaterial() { return refreshMaterial; }
    public String getInfoMaterial() { return infoMaterial; }
    public String getExpiredMaterial() { return expiredMaterial; }
    public String getMyItemsButtonMaterial() { return myItemsButtonMaterial; }

    public String getPrevPageName() { return prevPageName; }
    public String getNextPageName() { return nextPageName; }
    public String getSortButtonClickLore() { return sortButtonClickLore; }

    // Category items getters
    public List<String> getCategoryArmorItems() { return categoryArmorItems; }
    public List<String> getCategoryToolsItems() { return categoryToolsItems; }
    public List<String> getCategoryEventItems() { return categoryEventItems; }
    public List<String> getCategoryPremiumItems() { return categoryPremiumItems; }
    public boolean isPremiumIncludesEnchanted() { return premiumIncludesEnchanted; }

    // Cooldown getters
    public int getActionCooldownMs() { return actionCooldownMs; }
    public String getCooldownMessage() { return cooldownMessage; }

    // CustomModelData getters
    public int getFilterArmorCustomModelData() { return filterArmorCustomModelData; }
    public int getFilterToolsCustomModelData() { return filterToolsCustomModelData; }
    public int getFilterEventCustomModelData() { return filterEventCustomModelData; }
    public int getFilterPremiumCustomModelData() { return filterPremiumCustomModelData; }
    public int getPrevPageCustomModelData() { return prevPageCustomModelData; }
    public int getNextPageCustomModelData() { return nextPageCustomModelData; }
    public int getSortCustomModelData() { return sortCustomModelData; }
    public int getRefreshCustomModelData() { return refreshCustomModelData; }
    public int getInfoCustomModelData() { return infoCustomModelData; }
    public int getExpiredCustomModelData() { return expiredCustomModelData; }
    public int getMyItemsButtonCustomModelData() { return myItemsButtonCustomModelData; }

    // Confirm GUI getters
    public int getConfirmGuiRows() { return confirmGuiRows; }
    public int getConfirmPreviewSlot() { return confirmPreviewSlot; }
    public int getConfirmButtonSlot() { return confirmButtonSlot; }
    public int getCancelButtonSlot() { return cancelButtonSlot; }
    public String getConfirmButtonMaterial() { return confirmButtonMaterial; }
    public String getCancelButtonMaterial() { return cancelButtonMaterial; }
    public int getConfirmButtonCustomModelData() { return confirmButtonCustomModelData; }
    public int getCancelButtonCustomModelData() { return cancelButtonCustomModelData; }

    // Expired items GUI getters
    public String getExpiredGuiBackButtonName() { return expiredGuiBackButtonName; }
    public String getExpiredGuiBackButtonMaterial() { return expiredGuiBackButtonMaterial; }
    public int getExpiredGuiBackSlot() { return expiredGuiBackSlot; }
    public int getExpiredGuiPrevPageSlot() { return expiredGuiPrevPageSlot; }
    public int getExpiredGuiNextPageSlot() { return expiredGuiNextPageSlot; }
    public String getExpiredGuiClaimLore() { return expiredGuiClaimLore; }
    public String getExpiredGuiEmptyMessage() { return expiredGuiEmptyMessage; }
}
