package dev.grzybalson.config.modules.commands;

import eu.okaeri.configs.OkaeriConfig;

public class CommandsMessagesConfig extends OkaeriConfig {

    private String tpSuccessMessage = "&#00FF3B✔ &8Ξ &#4EFA76Teleportowano do gracza &e{player}&a!";
    private String tpHereSuccessMessage = "&#00FF3B✔ &8Ξ &#4EFA76Teleportowano gracza &e{player} &ado siebie!";
    private String tpPlayerNotFoundMessage = "&#FF0000✘ &8Ξ &cGracz nie został znaleziony!";
    private String tpaRequestSentMessage = "&#00FF3B✔ &8Ξ &#4EFA76Wysłano prośbę o teleportację do &e{player}&a!";
    private String tpaRequestReceivedMessage = "&#00E0FFℹ &8Ξ &f{player} &7prosi o teleportację! Wpisz &a/tpaccept &7lub &c/tpdeny&7.";
    private String tpaAcceptedMessage = "&#00FF3B✔ &8Ξ &#4EFA76Zaakceptowano prośbę o teleportację!";
    private String tpaDeniedMessage = "&#FF0000✘ &8Ξ &cOdrzucono prośbę o teleportację!";
    private String tpaNoPendingMessage = "&#FF0000✘ &8Ξ &cNie masz żadnych oczekujących próśb!";
    private String tpaExpiredMessage = "&#FF0000✘ &8Ξ &cProśba o teleportację wygasła!";
    private String gmChangedSelfMessage = "&#00FF3B✔ &8Ξ &#4EFA76Zmieniono tryb gry na &2{gamemode}&a!";
    private String gmChangedOtherMessage = "&#00FF3B✔ &8Ξ &#4EFA76Zmieniono tryb gry gracza &e{player} &ana &2{gamemode}&a!";
    private String gmInvalidMessage = "&#FF0000✘ &8Ξ &cNieprawidłowy tryb gry! Użyj: 0, 1, 2, 3 lub survival, creative, adventure, spectator";
    private String flyEnabledMessage = "&#00FF3B✔ &8Ξ &#4EFA76Włączono latanie!";
    private String flyDisabledMessage = "&#FF0000✘ &8Ξ &cWyłączono latanie!";
    private String flyEnabledOtherMessage = "&#00FF3B✔ &8Ξ &#4EFA76Włączono latanie dla &e{player}&a!";
    private String flyDisabledOtherMessage = "&#FF0000✘ &8Ξ &cWyłączono latanie dla &e{player}&c!";
    private String healSelfMessage = "&#00FF3B✔ &8Ξ &#4EFA76Zostałeś uleczony!";
    private String healOtherMessage = "&#00FF3B✔ &8Ξ &#4EFA76Uleczono gracza &e{player}&a!";
    private String feedSelfMessage = "&#00FF3B✔ &8Ξ &#4EFA76Nakarmiono!";
    private String feedOtherMessage = "&#00FF3B✔ &8Ξ &#4EFA76Nakarmiono gracza &e{player}&a!";
    private String godEnabledMessage = "&#00FF3B✔ &8Ξ &#4EFA76Włączono tryb boga!";
    private String godDisabledMessage = "&#FF0000✘ &8Ξ &cWyłączono tryb boga!";
    private String vanishEnabledMessage = "&#00FF3B✔ &8Ξ &#4EFA76Jesteś teraz niewidzialny!";
    private String vanishDisabledMessage = "&#FF0000✘ &8Ξ &cJesteś teraz widoczny!";
    private String noPermissionMessage = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/komenda &8(&4permisja&8)";
    private String playerOnlyMessage = "&#FF0000✘ &8Ξ &cTa komenda jest tylko dla graczy!";
    private String usagePrefix = "&#FF0000✘ &8Ξ &cPoprawne użycie: &#FF0000";


    public String getTpSuccessMessage() { return tpSuccessMessage; }
    public String getTpHereSuccessMessage() { return tpHereSuccessMessage; }
    public String getTpPlayerNotFoundMessage() { return tpPlayerNotFoundMessage; }
    public String getTpaRequestSentMessage() { return tpaRequestSentMessage; }
    public String getTpaRequestReceivedMessage() { return tpaRequestReceivedMessage; }
    public String getTpaAcceptedMessage() { return tpaAcceptedMessage; }
    public String getTpaDeniedMessage() { return tpaDeniedMessage; }
    public String getTpaNoPendingMessage() { return tpaNoPendingMessage; }
    public String getTpaExpiredMessage() { return tpaExpiredMessage; }
    public String getGmChangedSelfMessage() { return gmChangedSelfMessage; }
    public String getGmChangedOtherMessage() { return gmChangedOtherMessage; }
    public String getGmInvalidMessage() { return gmInvalidMessage; }
    public String getFlyEnabledMessage() { return flyEnabledMessage; }
    public String getFlyDisabledMessage() { return flyDisabledMessage; }
    public String getFlyEnabledOtherMessage() { return flyEnabledOtherMessage; }
    public String getFlyDisabledOtherMessage() { return flyDisabledOtherMessage; }
    public String getHealSelfMessage() { return healSelfMessage; }
    public String getHealOtherMessage() { return healOtherMessage; }
    public String getFeedSelfMessage() { return feedSelfMessage; }
    public String getFeedOtherMessage() { return feedOtherMessage; }
    public String getGodEnabledMessage() { return godEnabledMessage; }
    public String getGodDisabledMessage() { return godDisabledMessage; }
    public String getVanishEnabledMessage() { return vanishEnabledMessage; }
    public String getVanishDisabledMessage() { return vanishDisabledMessage; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public String getPlayerOnlyMessage() { return playerOnlyMessage; }
    public String getUsagePrefix() { return usagePrefix; }

}
