package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class ShopConfig extends OkaeriConfig {

    private String shopTitle = "&8Lista Sklepów";
    private String disabledCategoryMessage = "&#FF0000✘ &8Ξ &cTa kategoria sklepu jest wyłączona.";
    private String allCategoriesDisabledMessage = "&#FF0000✘ &8Ξ &cWszystkie kategorie sklepu są obecnie wyłączone.";

    private boolean moneyShopEnabled = true;
    private boolean timeShopEnabled = true;
    private boolean sellShopEnabled = true;
    private boolean killsShopEnabled = true;
    private boolean walletShopEnabled = true;
    private boolean licenseShopEnabled = true;
    private String licenseShopPermission = "Holly.shop.license";
    private String licenseShopRequiredLicense = "CLWGJ-GCXRW-COSUO-M3AER-GX9G1";
    private String moneyShopTitle = "&8Sklep za hajs";
    private String timeShopTitle = "&8Sklep za czas gry";
    private String killsShopTitle = "&8Sklep za zabójstwa";
    private String walletShopTitle = "&8Sklep za wPLN";
    private String licenseShopTitle = "&8Sklep licencyjny";

    private int shopRows = 5;
    private Map<Integer, ShopItem> moneyItems = new HashMap<>();
    private Map<Integer, ShopItem> timeItems = new HashMap<>();
    private Map<Integer, ShopItem> killsItems = new HashMap<>();
    private Map<Integer, ShopItem> walletItems = new HashMap<>();
    private Map<Integer, ShopItem> licenseItems = new HashMap<>();
    private Map<Integer, SellItem> sellItems = new HashMap<>();

    private String sellShopTitle = "&8Skup przedmiotów";
    private String itemBoughtMessage = "&#00FF3B✔ &8Ξ &#4EFA76Zakupiono przedmiot!";
    private String itemSoldMessage = "&#00FF3B✔ &8Ξ &#4EFA76Sprzedano przedmiot za &e{price}$&a!";
    private String itemSoldAllMessage = "&#00FF3B✔ &8Ξ &#4EFA76Sprzedano &e{amount} &aprzedmiotów za &e{price}$&a!";
    private String notEnoughItemsMessage = "&#FF0000✘ &8Ξ &cNie masz wystarczająco przedmiotów!";
    private String notEnoughMoneyMessage = "&#FF0000✘ &8Ξ &cNie masz wystarczająco pieniędzy!";
    private String notEnoughPlaytimeMessage = "&#FF0000✘ &8Ξ &cNie masz wystarczająco czasu gry!";
    private String itemDisabledMessage = "&#FF0000✘ &8Ξ &cTen przedmiot jest obecnie wyłączony z zakupu.";
    private String sellItemDisabledMessage = "&#FF0000✘ &8Ξ &cTen przedmiot jest obecnie wyłączony ze sprzedaży.";
    private Object disabledItemLore = List.of("", "&#FF5C5C✘ Ten przedmiot jest wyłączony z zakupu!");
    private Object disabledSellItemLore = List.of("", "&#FF5C5C✘ Ten przedmiot jest wyłączony ze skupu!");

    private String moneyShopItemName = "&#00FC25&lꜱ&#21FD42&lᴋ&#43FE5E&lʟ&#64FE7B&lᴇ&#85FF97&lᴘ &#83FF95&lᴢ&#82FF94&lᴀ\n&#61FE78&lʜ&#41FE5C&lᴀ&#20FD41&lᴊ&#00FC25&lꜱ";
    private List<String> moneyShopItemLore = List.of(
        "",
        "&#00FC25$ Informacje o kategorii.",
        "  &8♯ &fᴡ &bᴛᴇᴊ&f ᴋᴀᴛᴇɢᴏʀɪɪ &aᴋᴜᴘɪꜱᴢ&f ᴘᴏᴛʀᴢᴇʙɴᴇ",
        "  &8♯ &fᴄɪ &eᴡꜱᴢʏꜱᴛᴋɪᴇ&f ʀᴢᴇᴄᴢʏ ᴅᴏ &cᴡᴀʟᴋɪ&f ᴢ ɢʀᴀᴄᴢᴀᴍɪ!",
        "",
        "&7Kliknij PPM, aby zobaczyć!"
    );
    private String moneyShopItemMaterial = "PLAYER_HEAD";
    private String moneyShopItemHeadValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDJmYTA2ZjIzMTZiODkwNTY1N2I3MjVjN2I4ZDVhNTBlNDIzYTc5ZWVkNzY1OGNlZmEwNTg3YzA5ODhhNTNkMyJ9fX0=";
    private int moneyShopItemSlot = 20;

    private String timeShopItemName = "&#FFCD49&lꜱ&#FFD35D&lᴋ&#FFD871&lʟ&#FFDE85&lᴇ&#FFE399&lᴘ &#FFE294&lᴢ&#FFE192&lᴀ\n&#FFDB7E&lᴄ&#FFD76C&lᴢ&#FFD25B&lᴀ&#FFCD49&lꜱ";
    private List<String> timeShopItemLore = List.of(
        "",
        "&#FFCD49⌚ Informacje o kategorii.",
        "  &8♯ &fᴡ &bᴛᴇᴊ&f ᴋᴀᴛᴇɢᴏʀɪɪ ᴍᴏżᴇꜱᴢ &aᴋᴜᴘɪć",
        "  &8♯ &cᴄᴇɴɴᴇ&f ᴘʀᴢᴇᴅᴍɪᴏᴛʏ ᴢᴀ &eᴘʀᴢᴇɢʀᴀɴʏ&f ᴄᴢᴀꜱ!",
        "",
        "&7Kliknij PPM, aby zobaczyć!"
    );
    private String timeShopItemMaterial = "PLAYER_HEAD";
    private String timeShopItemHeadValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGZiNmZhN2FkNGEzZTUzMTY1NWQwNGVjOGI5YTQ1ZmRlYjQ1YjdmNDg2OTI4NzAxZTE5ZjkwYzZiZGZhYjRhIn19fQ==";
    private int timeShopItemSlot = 22;

    private String sellShopItemName = "&#63B6F8&lꜱ&#6FBCF9&lᴋ&#7AC2FB&lᴜ&#86C8FC&lᴘ &#9DD4FF&lᴘ&#9AD3FF&lʀ&#97D2FF&lᴢ&#95D0FF&lᴇ&#92CFFF&lᴅ&#8FCEFF&lᴍ&#86C9FE&lɪ&#7DC4FC&lᴏ&#75C0FB&lᴛ&#6CBBF9&ló&#63B6F8&lᴡ";
    private List<String> sellShopItemLore = List.of(
        "",
        "&#63B6F8⌚ Informacje o kategorii.",
        "  &8♯ &fᴡ &bᴛᴇᴊ&f ᴋᴀᴛᴇɢᴏʀɪɪ &aꜱᴋᴜᴘ&f ꜱᴡᴏᴊᴇ ",
        "  &8♯ &f&eᴘʀᴢᴇᴅᴍɪᴏᴛʏ&f ɪ &cᴏᴛʀᴢʏᴍᴀᴊ&f ꜱᴇʀᴡᴇʀᴏᴡᴇ &aᴅᴏʟᴀʀʏ!",
        "",
        "&7Kliknij PPM, aby zobaczyć!"
    );
    private String sellShopItemMaterial = "PLAYER_HEAD";
    private String sellShopItemHeadValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjkyNzNjYjNjNzllY2Q1M2MxM2I0ZDcxZTlhZmQ5MTlkMWRjYmExNDEwYzI1MzhiOGZlMDk5ZWNkNjFhNmNiNCJ9fX0=";
    private int sellShopItemSlot = 24;

    private String killsShopItemName = "&#FF4141&lꜱ&#FF4848&lᴋ&#FF4F4F&lʟ&#FF5757&lᴇ&#FF5E5E&lᴘ &#FF6C6C&lᴢ&#FF6A6A&lᴀ\n&#FF6565&lᴢ&#FF6262&lᴀ&#FF6060&lʙ&#FF5B5B&ló&#FF5656&lᴊ&#FF5151&lꜱ&#FF4B4B&lᴛ&#FF4646&lᴡ&#FF4141&lᴀ";
    private List<String> killsShopItemLore = List.of(
        "",
        "&#FF4141⚔ Informacje o kategorii.",
        "  &8♯ &fᴡ &bᴛᴇᴊ&f ᴋᴀᴛᴇɢᴏʀɪɪ &aᴋᴜᴘ&c ᴄᴇɴɴᴇ ",
        "  &8♯ &eᴘʀᴢᴇᴅᴍɪᴏᴛʏ&f ᴢᴀ ꜱᴡᴏᴊᴇ &cᴋɪʟʟᴇ!",
        "",
        "&7Kliknij PPM, aby zobaczyć!"
    );
    private String killsShopItemMaterial = "PLAYER_HEAD";
    private String killsShopItemHeadValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzM3NDMxZjgwYjBkMWNiYzE0ZjEyNjE2NDVmODYxMjQ5ODAxZTJjNzViYzY0OTU1YjQwZTM5YzliYjUzZTFmIn19fQ==";
    private int killsShopItemSlot = 31;

    private String walletShopItemName = "&e&lSklep za wPLN";
    private List<String> walletShopItemLore = List.of(
        "&7Kliknij, aby otworzyć",
        "&7sklep za wPLN."
    );
    private String walletShopItemMaterial = "RED_WOOL";
    private String walletShopItemHeadValue = "";
    private int walletShopItemSlot = 33;

    private String licenseShopItemName = "&6&lSklep licencyjny";
    private List<String> licenseShopItemLore = List.of(
        "&7Kliknij, aby otworzyc",
        "&7specjalny sklep."
    );
    private String licenseShopItemMaterial = "ORANGE_WOOL";
    private String licenseShopItemHeadValue = "";
    private int licenseShopItemSlot = 21;

    private String backButtonName = "&c&l◀ Powrót";
    private List<String> backButtonLore = List.of("", "&7Wróć do menu głównego");

    private Object priceLore = List.of("", "&8▸ &fCena zakupu:", "&8  &#FFDD57{price}$");
    private Object balanceLore = List.of("&8▸ &fStan Twojego konta:", "&8  &#00E0FF{balance}$");
    private Object canAffordLore = List.of("", "&#4EFA76✔ Stać Cię na ten przedmiot", "&fKliknij, aby przejść do zakupu");
    private Object cannotAffordLore = List.of("", "&#FF5C5C✘ Brakuje Ci środków", "&7Zdobądź więcej monet i spróbuj ponownie");

    private Object timePriceLore = List.of("", "&8▸ &fWymagany czas gry:", "&8  &#00E0FF{time}");
    private Object playtimeLore = List.of("&8▸ &fTwój aktualny czas:", "&8  &#B388FF{time}");
    private Object notEnoughTimeLore = List.of("", "&#FF5C5C✘ Masz za mało czasu gry");

    private Object killsPriceLore = List.of("", "&8▸ &fWymagane zabójstwa:", "&8  &#FF5C5C{kills}");
    private Object playerKillsLore = List.of("&8▸ &fTwoje zabójstwa:", "&8  &#FFB800{kills}");
    private Object notEnoughKillsLore = List.of("", "&#FF5C5C✘ Masz za mało zabójstw");
    private String notEnoughKillsMessage = "&#FF0000✘ &8Ξ &cNie masz wystarczająco zabójstw!";

    private Object walletPriceLore = List.of("", "&8▸ &fCena zakupu:", "&8  &#FFDD57{price} wPLN");
    private Object walletBalanceLore = List.of("&8▸ &fStan portfela:", "&8  &#00E0FF{balance} wPLN");
    private Object notEnoughWalletLore = List.of("", "&#FF5C5C✘ Masz za mało wPLN");
    private String notEnoughWalletMessage = "&#FF0000✘ &8Ξ &cNie masz wystarczająco wPLN!";


    private String purchaseConfirmTitle = "&8✦ Potwierdzenie zakupu";
    private int purchasePreviewSlot = 10;
    // Object for backward compatibility: old/broken configs may have saved these as {} maps,
    // single numbers, comma-separated strings, or normal YAML lists.
    private Object purchaseQuickSlots = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
    private Object purchaseQuickAmounts = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
    private Object purchaseAddSlots = List.of(14, 23, 32);
    private Object purchaseAddAmounts = List.of(64, 10, 1);
    private Object purchaseRemoveSlots = List.of(13, 22, 31);
    private Object purchaseRemoveAmounts = List.of(64, 10, 1);
    private int purchaseConfirmSlot = 30;
    private int purchaseCancelSlot = 40;
    private String purchasePreviewName = "&#00E0FF&l{ITEM}";
    // Object for backward compatibility: older configs saved this as a single String.
    private Object purchasePreviewLore = List.of(
        "",
        "&8▸ &fWybrana ilość: &#00E0FF{AMOUNT} szt.",
        "&8▸ &fŁączny koszt: &#FFDD57{PRICE}$",
        ""
    );
    private String purchaseQuickName = "&#4EFA76Ustaw ilość: &f{AMOUNT} szt.";
    private List<String> purchaseQuickLore = List.of("", "&7Ustaw zakup na &#00E0FF{AMOUNT} szt.", "", "&#4EFA76✔ Kliknij, aby wybrać");
    private String purchaseAddName = "&#4EFA76Dodaj &f{AMOUNT} szt.";
    private List<String> purchaseAddLore = List.of("", "&7Dodaj &#4EFA76{AMOUNT} szt. &7do zakupu", "", "&#4EFA76✔ Kliknij, aby dodać");
    private String purchaseRemoveName = "&#FF5C5COdejmij &f{AMOUNT} szt.";
    private List<String> purchaseRemoveLore = List.of("", "&7Odejmij &#FF5C5C{AMOUNT} szt. &7z zakupu", "", "&#FF5C5C✘ Kliknij, aby odjąć");
    private String purchaseConfirmName = "&#4EFA76&lZATWIERDŹ ZAKUP";
    private List<String> purchaseConfirmLore = List.of(
        "",
        "&8▸ &fLiczba produktów: &#00E0FF{AMOUNT} szt.",
        "&8▸ &fKoszt zakupu: &#FFDD57{PRICE}$",
        "",
        "&#4EFA76✔ Kliknij, aby zatwierdzić transakcję"
    );
    private String purchaseCancelName = "&#FF5C5C&lANULUJ ZAKUP";
    private List<String> purchaseCancelLore = List.of("", "&7Anuluj zakup i wróć do sklepu.", "", "&#FF5C5C✘ Kliknij, aby anulować");

    private Object sellPriceLore = List.of("", "&8▸ &fOtrzymasz za sprzedaż:", "&8  &#4EFA76{price}$");
    private Object requiredAmountLore = List.of("&8▸ &fWymagana liczba:", "&8  &#FFDD57{amount} szt.");
    private Object playerHasLore = List.of("&8▸ &fPosiadasz obecnie:", "&8  &#00E0FF{amount} szt.");
    private Object canSellLore = List.of("", "&#4EFA76✔ Kliknij, aby sprzedać");
    private Object cannotSellLore = List.of("", "&#FF5C5C✘ Nie masz wymaganych przedmiotów");

    public String getShopTitle() { return shopTitle; }
    public String getDisabledCategoryMessage() { return disabledCategoryMessage; }
    public String getAllCategoriesDisabledMessage() { return allCategoriesDisabledMessage; }
    public boolean isMoneyShopEnabled() { return moneyShopEnabled; }
    public boolean isTimeShopEnabled() { return timeShopEnabled; }
    public boolean isSellShopEnabled() { return sellShopEnabled; }
    public boolean isKillsShopEnabled() { return killsShopEnabled; }
    public boolean isWalletShopEnabled() { return walletShopEnabled; }
    public boolean isLicenseShopEnabled() { return licenseShopEnabled; }
    public String getLicenseShopPermission() { return licenseShopPermission; }
    public String getLicenseShopRequiredLicense() { return licenseShopRequiredLicense; }
    public String getMoneyShopTitle() { return moneyShopTitle; }
    public String getTimeShopTitle() { return timeShopTitle; }
    public String getKillsShopTitle() { return killsShopTitle; }
    public String getWalletShopTitle() { return walletShopTitle; }
    public String getLicenseShopTitle() { return licenseShopTitle; }
    public int getShopRows() { return shopRows; }
    public Map<Integer, ShopItem> getMoneyItems() { return moneyItems; }
    public Map<Integer, ShopItem> getTimeItems() { return timeItems; }
    public Map<Integer, ShopItem> getKillsItems() { return killsItems; }
    public Map<Integer, ShopItem> getWalletItems() { return walletItems; }
    public Map<Integer, ShopItem> getLicenseItems() { return licenseItems; }
    public Map<Integer, SellItem> getSellItems() { return sellItems; }
    public String getSellShopTitle() { return sellShopTitle; }
    public String getItemBoughtMessage() { return itemBoughtMessage; }
    public String getItemSoldMessage() { return itemSoldMessage; }
    public String getItemSoldAllMessage() { return itemSoldAllMessage; }
    public String getNotEnoughItemsMessage() { return notEnoughItemsMessage; }
    public String getNotEnoughMoneyMessage() { return notEnoughMoneyMessage; }
    public String getNotEnoughPlaytimeMessage() { return notEnoughPlaytimeMessage; }

    public String getMoneyShopItemName() { return moneyShopItemName; }
    public List<String> getMoneyShopItemLore() { return moneyShopItemLore; }
    public String getMoneyShopItemMaterial() { return moneyShopItemMaterial; }
    public String getMoneyShopItemHeadValue() { return moneyShopItemHeadValue; }
    public int getMoneyShopItemSlot() { return moneyShopItemSlot; }
    public String getTimeShopItemName() { return timeShopItemName; }
    public List<String> getTimeShopItemLore() { return timeShopItemLore; }
    public String getTimeShopItemMaterial() { return timeShopItemMaterial; }
    public String getTimeShopItemHeadValue() { return timeShopItemHeadValue; }
    public int getTimeShopItemSlot() { return timeShopItemSlot; }
    public String getSellShopItemName() { return sellShopItemName; }
    public List<String> getSellShopItemLore() { return sellShopItemLore; }
    public String getSellShopItemMaterial() { return sellShopItemMaterial; }
    public String getSellShopItemHeadValue() { return sellShopItemHeadValue; }
    public int getSellShopItemSlot() { return sellShopItemSlot; }
    public String getBackButtonName() { return backButtonName; }
    public List<String> getBackButtonLore() { return backButtonLore; }
    public List<String> getPriceLore() { return asStringList(priceLore); }
    public List<String> getBalanceLore() { return asStringList(balanceLore); }
    public List<String> getCanAffordLore() { return asStringList(canAffordLore); }
    public List<String> getCannotAffordLore() { return asStringList(cannotAffordLore); }
    public List<String> getTimePriceLore() { return asStringList(timePriceLore); }
    public List<String> getPlaytimeLore() { return asStringList(playtimeLore); }
    public List<String> getNotEnoughTimeLore() { return asStringList(notEnoughTimeLore); }
    public String getItemDisabledMessage() { return itemDisabledMessage; }
    public String getSellItemDisabledMessage() { return sellItemDisabledMessage; }
    public List<String> getDisabledItemLore() { return asStringList(disabledItemLore); }
    public List<String> getDisabledSellItemLore() { return asStringList(disabledSellItemLore); }


    private static List<String> asStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object element : list) {
                result.add(String.valueOf(element));
            }
            return result;
        }
        return List.of(String.valueOf(value));
    }

    private static int[] asIntArray(Object value, int... fallback) {
        if (value == null) {
            return fallback;
        }

        List<Integer> numbers = new ArrayList<>();

        if (value instanceof List<?> list) {
            for (Object element : list) {
                Integer parsed = parseInt(element);
                if (parsed != null) numbers.add(parsed);
            }
        } else if (value instanceof Map<?, ?> map) {
            // Broken Okaeri configs sometimes saved empty lists as {}.
            // If it is empty, use sane defaults instead of crashing/returning empty.
            if (map.isEmpty()) return fallback;
            for (Object element : map.values()) {
                Integer parsed = parseInt(element);
                if (parsed != null) numbers.add(parsed);
            }
        } else if (value instanceof Number number) {
            numbers.add(number.intValue());
        } else {
            String text = String.valueOf(value).trim();
            if (text.isEmpty() || text.equals("{}") || text.equals("[]")) return fallback;
            for (String part : text.split(",")) {
                Integer parsed = parseInt(part.trim());
                if (parsed != null) numbers.add(parsed);
            }
        }

        if (numbers.isEmpty()) {
            return fallback;
        }

        int[] result = new int[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            result[i] = numbers.get(i);
        }
        return result;
    }

    private static Integer parseInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getPurchaseConfirmTitle() { return purchaseConfirmTitle; }
    public int getPurchasePreviewSlot() { return purchasePreviewSlot; }
    public int[] getPurchaseQuickSlots() { return asIntArray(purchaseQuickSlots, 0, 1, 2, 3, 4, 5, 6, 7, 8); }
    public int[] getPurchaseQuickAmounts() { return asIntArray(purchaseQuickAmounts, 1, 2, 3, 4, 5, 6, 7, 8, 9); }
    public int[] getPurchaseAddSlots() { return asIntArray(purchaseAddSlots, 14, 23, 32); }
    public int[] getPurchaseAddAmounts() { return asIntArray(purchaseAddAmounts, 64, 10, 1); }
    public int[] getPurchaseRemoveSlots() { return asIntArray(purchaseRemoveSlots, 13, 22, 31); }
    public int[] getPurchaseRemoveAmounts() { return asIntArray(purchaseRemoveAmounts, 64, 10, 1); }
    public int getPurchaseConfirmSlot() { return purchaseConfirmSlot; }
    public int getPurchaseCancelSlot() { return purchaseCancelSlot; }
    public String getPurchasePreviewName() { return purchasePreviewName; }
    public List<String> getPurchasePreviewLore() { return asStringList(purchasePreviewLore); }
    public String getPurchaseQuickName() { return purchaseQuickName; }
    public List<String> getPurchaseQuickLore() { return purchaseQuickLore; }
    public String getPurchaseAddName() { return purchaseAddName; }
    public List<String> getPurchaseAddLore() { return purchaseAddLore; }
    public String getPurchaseRemoveName() { return purchaseRemoveName; }
    public List<String> getPurchaseRemoveLore() { return purchaseRemoveLore; }
    public String getPurchaseConfirmName() { return purchaseConfirmName; }
    public List<String> getPurchaseConfirmLore() { return purchaseConfirmLore; }
    public String getPurchaseCancelName() { return purchaseCancelName; }
    public List<String> getPurchaseCancelLore() { return purchaseCancelLore; }

    public List<String> getSellPriceLore() { return asStringList(sellPriceLore); }
    public List<String> getRequiredAmountLore() { return asStringList(requiredAmountLore); }
    public List<String> getPlayerHasLore() { return asStringList(playerHasLore); }
    public List<String> getCanSellLore() { return asStringList(canSellLore); }
    public List<String> getCannotSellLore() { return asStringList(cannotSellLore); }

    public String getKillsShopItemName() { return killsShopItemName; }
    public List<String> getKillsShopItemLore() { return killsShopItemLore; }
    public String getKillsShopItemMaterial() { return killsShopItemMaterial; }
    public String getKillsShopItemHeadValue() { return killsShopItemHeadValue; }
    public int getKillsShopItemSlot() { return killsShopItemSlot; }
    public String getWalletShopItemName() { return walletShopItemName; }
    public List<String> getWalletShopItemLore() { return walletShopItemLore; }
    public String getWalletShopItemMaterial() { return walletShopItemMaterial; }
    public String getWalletShopItemHeadValue() { return walletShopItemHeadValue; }
    public int getWalletShopItemSlot() { return walletShopItemSlot; }
    public String getLicenseShopItemName() { return licenseShopItemName; }
    public List<String> getLicenseShopItemLore() { return licenseShopItemLore; }
    public String getLicenseShopItemMaterial() { return licenseShopItemMaterial; }
    public String getLicenseShopItemHeadValue() { return licenseShopItemHeadValue; }
    public int getLicenseShopItemSlot() { return licenseShopItemSlot; }
    public List<String> getKillsPriceLore() { return asStringList(killsPriceLore); }
    public List<String> getPlayerKillsLore() { return asStringList(playerKillsLore); }
    public List<String> getNotEnoughKillsLore() { return asStringList(notEnoughKillsLore); }
    public String getNotEnoughKillsMessage() { return notEnoughKillsMessage; }
    public List<String> getWalletPriceLore() { return asStringList(walletPriceLore); }
    public List<String> getWalletBalanceLore() { return asStringList(walletBalanceLore); }
    public List<String> getNotEnoughWalletLore() { return asStringList(notEnoughWalletLore); }
    public String getNotEnoughWalletMessage() { return notEnoughWalletMessage; }

    public void addMoneyItem(int slot, ShopItem item) {
        moneyItems.put(slot, item);
        this.save();
    }

    public void addTimeItem(int slot, ShopItem item) {
        timeItems.put(slot, item);
        this.save();
    }

    public void addSellItem(int slot, SellItem item) {
        sellItems.put(slot, item);
        this.save();
    }

    public void addKillsItem(int slot, ShopItem item) {
        killsItems.put(slot, item);
        this.save();
    }

    public void addWalletItem(int slot, ShopItem item) {
        walletItems.put(slot, item);
        this.save();
    }

    public void addLicenseItem(int slot, ShopItem item) {
        licenseItems.put(slot, item);
        this.save();
    }

    public static class ShopItem extends OkaeriConfig {
        private String itemData;
        @CustomKey("display-name")
        private String displayName;
        private List<String> lore;
        private double price;
        private long timePrice;
        private int killsPrice;
        private double walletPrice;
        private boolean enabled = true;

        public ShopItem() {}

        public ShopItem(String itemData, double price) {
            this.itemData = itemData;
            this.price = price;
            this.timePrice = 0;
            this.killsPrice = 0;
            this.walletPrice = 0;
        }

        public ShopItem(String itemData, long timePrice) {
            this.itemData = itemData;
            this.price = 0;
            this.timePrice = timePrice;
            this.killsPrice = 0;
            this.walletPrice = 0;
        }

        public ShopItem(String itemData, int killsPrice, boolean isKills) {
            this.itemData = itemData;
            this.price = 0;
            this.timePrice = 0;
            this.killsPrice = killsPrice;
            this.walletPrice = 0;
        }

        public ShopItem(String itemData, double walletPrice, boolean isWallet, boolean dummy) {
            this.itemData = itemData;
            this.price = 0;
            this.timePrice = 0;
            this.killsPrice = 0;
            this.walletPrice = walletPrice;
        }

        public String getItemData() { return itemData; }
        public void setItemData(String itemData) { this.itemData = itemData; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public long getTimePrice() { return timePrice; }
        public void setTimePrice(long timePrice) { this.timePrice = timePrice; }
        public int getKillsPrice() { return killsPrice; }
        public void setKillsPrice(int killsPrice) { this.killsPrice = killsPrice; }
        public double getWalletPrice() { return walletPrice; }
        public void setWalletPrice(double walletPrice) { this.walletPrice = walletPrice; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class SellItem extends OkaeriConfig {
        private String material;
        @CustomKey("display-name")
        private String displayName;
        private List<String> lore;
        private double sellPrice;
        private int amount;
        private String itemData;
        private boolean enabled = true;

        public SellItem() {}

        public SellItem(String material, double sellPrice, int amount) {
            this(material, sellPrice, amount, null);
        }

        public SellItem(String material, double sellPrice, int amount, String itemData) {
            this.material = material;
            this.sellPrice = sellPrice;
            this.amount = amount;
            this.itemData = itemData;
        }

        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public double getSellPrice() { return sellPrice; }
        public void setSellPrice(double sellPrice) { this.sellPrice = sellPrice; }
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        public String getItemData() { return itemData; }
        public void setItemData(String itemData) { this.itemData = itemData; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
