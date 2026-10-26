package dev.grzybalson.config.modules.czek;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Arrays;
import java.util.List;

public class CzekConfig extends OkaeriConfig {

    private boolean enabled = true;
    private String itemName = "&x&8&A&F&F&0&0&lᴄ&x&8&4&F&3&0&2&lᴢ&x&7&E&E&7&0&3&lᴇ&x&7&9&D&B&0&5&lᴋ &x&6&D&C&3&0&8&lᴘ&x&6&7&B&7&0&9&lɪ&x&6&C&C&1&0&8&lᴇ&x&7&1&C&C&0&6&lɴ&x&7&6&D&6&0&5&lɪ&x&7&B&E&0&0&4&lᴇ&x&8&0&E&A&0&3&lᴢ&x&8&5&F&5&0&1&lɴ&x&8&A&F&F&0&0&lʏ";
    private String itemMaterial = "PAPER";
    private List<String> itemLore = Arrays.asList(
        "",
        "&8 » &fWartość czeku wynosi &2$${AMOUNT}",
        "",
        "&8 » &aKliknij &2PPM &aaby wpłacić czek!"
    );

    private String messageCreated = "&#00FF3B✔ &8Ξ &#4EFA76Stworzono czek o wartości &e$${AMOUNT}&a!";
    private String messageDeposited = "&#00FF3B✔ &8Ξ &#4EFA76Wpłacono czek o wartości &e$${AMOUNT}&a na konto!";
    private String messageInvalidAmount = "&#FF0000✘ &8Ξ &cPodaj poprawną kwotę czeku!";
    private String messageNotEnoughMoney = "&#FF0000✘ &8Ξ &cNie posiadasz wystarczająco pieniędzy!";
    private String messageInventoryFull = "&#FF0000✘ &8Ξ &cTwój ekwipunek jest pełny!";
    private String messageError = "&#FF0000✘ &8Ξ &cWystąpił błąd podczas przetwarzania czeku!";
    private String messageReloaded = "&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie przeładowano konfigurację czeków!";

    public boolean isEnabled() { return enabled; }
    public String getItemName() { return itemName; }
    public String getItemMaterial() { return itemMaterial; }
    public List<String> getItemLore() { return itemLore; }
    public String getMessageCreated() { return messageCreated; }
    public String getMessageDeposited() { return messageDeposited; }
    public String getMessageInvalidAmount() { return messageInvalidAmount; }
    public String getMessageNotEnoughMoney() { return messageNotEnoughMoney; }
    public String getMessageInventoryFull() { return messageInventoryFull; }
    public String getMessageError() { return messageError; }
    public String getMessageReloaded() { return messageReloaded; }
}
