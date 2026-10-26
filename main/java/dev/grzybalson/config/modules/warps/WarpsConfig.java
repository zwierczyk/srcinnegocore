package dev.grzybalson.config.modules.warps;

import eu.okaeri.configs.OkaeriConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WarpsConfig extends OkaeriConfig {

    private String guiTitle = "&8&lWARPY";
    private int guiRows = 5;

    private int teleportDelay = 5;

    private boolean cancelOnMove = true;

    private boolean cancelOnDamage = true;

    private String countdownTitle = "&e&lTELEPORTACJA";

    private String countdownSubtitle = "&7Za &f{time} &7sekund...";

    private String teleportingSubtitle = "&fZostaniesz przeniesiony za &#77F454{TIME}";

    private String teleportSuccessTitle = "&#77F454☺";

    private String teleportSuccessSubtitle = "&aPomyślnie zostałeś przeniesiony";

    private String teleportSuccessMessage = "&a» &7Przeteleportowano do warpa &f{warp}&7!";

    private String teleportCancelledMessage = "&c» &7Teleportacja przerwana &8(&#AAAAAAruszyłeś się&8)!";

    private String warpNotFoundMessage = "&#FF0000✘ &8Ξ &cWarp nie został znaleziony!";

    private String warpCreatedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Utworzono warp &e{warp}&a!";

    private String warpUpdatedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Zaktualizowano pozycję warpa &e{warp}&a!";

    private String warpDeletedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Usunięto warp &e{warp}&a!";

    private String warpExistsMessage = "&#FF0000✘ &8Ξ &cWarp o tej nazwie już istnieje!";

    private String noPermissionMessage = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/warp &8(&4Holly.cmd.warp&8)";

    private String warpClickToTeleport = "&aKliknij aby się teleportować";
    private String warpNoPermissionLore = "&cBrak uprawnień!";

    private String defaultItemMaterial = "ENDER_PEARL";

    private List<String> defaultLore = Arrays.asList(
        "",
        "&7Kliknij aby się teleportować",
        ""
    );

    public String getGuiTitle() { return guiTitle; }
    public int getGuiRows() { return guiRows; }
    public int getTeleportDelay() { return teleportDelay; }
    public boolean isCancelOnMove() { return cancelOnMove; }
    public boolean isCancelOnDamage() { return cancelOnDamage; }
    public String getCountdownTitle() { return countdownTitle; }
    public String getCountdownSubtitle() { return countdownSubtitle; }
    public String getTeleportingSubtitle() { return teleportingSubtitle; }
    public String getTeleportSuccessTitle() { return teleportSuccessTitle; }
    public String getTeleportSuccessSubtitle() { return teleportSuccessSubtitle; }
    public String getTeleportSuccessMessage() { return teleportSuccessMessage; }
    public String getTeleportCancelledMessage() { return teleportCancelledMessage; }
    public String getWarpNotFoundMessage() { return warpNotFoundMessage; }
    public String getWarpCreatedMessage() { return warpCreatedMessage; }
    public String getWarpUpdatedMessage() { return warpUpdatedMessage; }
    public String getWarpDeletedMessage() { return warpDeletedMessage; }
    public String getWarpExistsMessage() { return warpExistsMessage; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public String getWarpClickToTeleport() { return warpClickToTeleport; }
    public String getWarpNoPermissionLore() { return warpNoPermissionLore; }
    public String getDefaultItemMaterial() { return defaultItemMaterial; }
    public List<String> getDefaultLore() { return defaultLore; }
}
