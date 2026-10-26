package dev.grzybalson.config.modules.kits;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Arrays;
import java.util.List;

public class KitsConfig extends OkaeriConfig {

    private String guiTitle = "&8&lZESTAWY";

    private int guiRows = 5;

    private String kitItemName = "&c&l{kit}";

    private String kitAvailableLore = "&aDostępny do odbioru!";

    private String kitCooldownLore = "&cOdczekaj: &f{time}";

    private String kitNoPermissionLore = "&cBrak uprawnień!";

    private String kitPreviewLore = "&7Kliknij, aby zobaczyć podgląd";

    private String kitClaimLore = "&7Odbierz zestaw w podglądzie";

    private String kitReceivedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Otrzymałeś zestaw &e{kit}&a!";

    private String kitCooldownMessage = "&#FF0000✘ &8Ξ &cMusisz odczekać &4{time} &czanim odbierzesz ten zestaw!";

    private String kitNoPermissionMessage = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/kit &8(&4permisja-zestawu&8)";

    private String kitNotFoundMessage = "&#FF0000✘ &8Ξ &cZestaw nie został znaleziony!";

    private String claimButtonName = "&#7CFC00&lᴏᴅʙɪᴇʀᴢ ᴢᴇꜱᴛᴀᴡ";

    private String claimButtonLore = "&7Kliknij, aby odebrać &f{kit}";

    private String claimNoPermissionName = "&#FF5C5C&lʙʀᴀᴋ ᴘᴇʀᴍɪꜱᴊɪ";

    private String claimNoPermissionLore = "&7Nie posiadasz permisji do tego kita";

    private String claimCooldownName = "&#FF8300&lᴄᴏᴏʟᴅᴏᴡɴ";

    private String claimCooldownLore = "&7Pozostało: &#FFFFFF{time}";

    private String backItemMaterial = "ARROW";

    private String backItemName = "&c&lPowrót";

    private List<String> backItemLore = Arrays.asList("&7Kliknij, aby wrócić");

    private boolean supportBase64Heads = true;

    public String getGuiTitle() { return guiTitle; }
    public int getGuiRows() { return guiRows; }
    public String getKitItemName() { return kitItemName; }
    public String getKitAvailableLore() { return kitAvailableLore; }
    public String getKitCooldownLore() { return kitCooldownLore; }
    public String getKitNoPermissionLore() { return kitNoPermissionLore; }
    public String getKitPreviewLore() { return kitPreviewLore; }
    public String getKitClaimLore() { return kitClaimLore; }
    public String getKitReceivedMessage() { return kitReceivedMessage; }
    public String getKitCooldownMessage() { return kitCooldownMessage; }
    public String getKitNoPermissionMessage() { return kitNoPermissionMessage; }
    public String getKitNotFoundMessage() { return kitNotFoundMessage; }
    public String getClaimButtonName() { return claimButtonName; }
    public String getClaimButtonLore() { return claimButtonLore; }
    public String getClaimNoPermissionName() { return claimNoPermissionName; }
    public String getClaimNoPermissionLore() { return claimNoPermissionLore; }
    public String getClaimCooldownName() { return claimCooldownName; }
    public String getClaimCooldownLore() { return claimCooldownLore; }
    public String getBackItemMaterial() { return backItemMaterial; }
    public String getBackItemName() { return backItemName; }
    public List<String> getBackItemLore() { return backItemLore; }
    public boolean isSupportBase64Heads() { return supportBase64Heads; }
}
