package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class MessagesConfig extends OkaeriConfig {

    private String noPermission = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/komenda &8(&4permisja&8)";
    private String playerNotFound = "&#FF0000✘ &8Ξ &cNie znaleziono gracza!";
    private String playerOnly = "&#FF0000✘ &8Ξ &cTa komenda jest tylko dla graczy!";
    private String invalidArguments = "&#FF0000✘ &8Ξ &cNieprawidłowe argumenty!";
    private String cooldownMessage = "&#FF0000✘ &8Ξ &cMusisz odczekać jeszcze &4{sec}s &cprzed następnym użyciem!";

    private String success = "&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie użyto kodu!";
    private String teleported = "&#00FF3B✔ &8Ξ &#4EFA76Pomyślnie teleportowano!";
    private String gamemodeChanged = "&#00FF3B✔ &8Ξ &#4EFA76Zmieniono tryb gry na &2{mode}&a!";
    private String gamemodeTitle = "&aTryb gry";
    private String gamemodeSubtitle = "&7Zmieniono na &2{mode}";
    private int gamemodeTitleFadeIn = 5;
    private int gamemodeTitleStay = 30;
    private int gamemodeTitleFadeOut = 10;
    private String flyspeedChanged = "&#00FF3B✔ &8Ξ &#4EFA76Zmieniono prędkość latania na &e{speed}&a!";
    private String flyEnabled = "&#00FF3B✔ &8Ξ &#4EFA76Włączono latanie!";
    private String flyDisabled = "&#00FF3B✔ &8Ξ &#4EFA76Wyłączono latanie!";
    private String flyEnabledOther = "&#00FF3B✔ &8Ξ &#4EFA76Włączono latanie dla gracza &e{player}&a!";
    private String flyDisabledOther = "&#00FF3B✔ &8Ξ &#4EFA76Wyłączono latanie dla gracza &e{player}&a!";

    private String tpaRequestSent = "&#00E0FFℹ &8Ξ &fWysłano prośbę o teleportację do &e{player}&7!";
    private String tpaRequestReceived = "&#00E0FFℹ &8Ξ &e{player} &7chce się do Ciebie teleportować!";
    private String tpaAccepted = "&#00FF3B✔ &8Ξ &#4EFA76Zaakceptowano prośbę o teleportację!";
    private String tpaDenied = "&#FF0000✘ &8Ξ &cOdrzucono prośbę o teleportację!";
    private String tpaExpired = "&#FF0000✘ &8Ξ &cProśba o teleportację wygasła!";
    private String noTpaRequest = "&#FF0000✘ &8Ξ &cNie masz żadnych próśb o teleportację!";

    private String spawnSet = "&a» &7Punkt spawnu został ustawiony!";
    private String spawnNotSet = "&c» &7Punkt spawnu nie jest jeszcze ustawiony!";
    private String spawnTeleported = "&a» &7Przeteleportowano na spawn!";
    private String spawnTeleportingSubtitle = "&fZostaniesz przeniesiony za &#77F454{TIME}";
    private String spawnTeleportSuccessTitle = "&#77F454SUKCES";
    private String spawnTeleportSuccessSubtitle = "&aPomyślnie zostałeś przeniesiony";
    private String spawnTeleportCancelled = "&c» &7Teleportacja przerwana &8(&#AAAAAAruszyłeś się&8)!";
    private String spawnTeleportOther = "&a» &7Teleportowano &f{player} &7na spawn.";

    private String balanceMessage = "&#00E0FFℹ &8Ξ &fTwój stan konta: &e{balance}$";
    private String moneyAdded = "&#00FF3B✔ &8Ξ &#4EFA76Dodano &e{amount}$ &agraczowi &e{player}&a!";
    private String moneyRemoved = "&#00FF3B✔ &8Ξ &#4EFA76Usunięto &e{amount}$ &agraczowi &e{player}&a!";
    private String moneySet = "&#00FF3B✔ &8Ξ &#4EFA76Ustawiono stan konta gracza &e{player} &ana &e{amount}$&a!";
    private String moneyReset = "&#00FF3B✔ &8Ξ &#4EFA76Zresetowano stan konta gracza &e{player}&a!";
    private String notEnoughMoney = "&#FF0000✘ &8Ξ &cNie masz wystarczająco pieniędzy!";
    private String payUsage = "&#FF0000✘ &8Ξ &cPoprawne użycie: &#FF0000\n &8• &4/pay <nick> <ilość>\n &8• &7Skróty: &f1k &71m &71mil &71b";
    private String payInvalidAmount = "&#FF0000✘ &8Ξ &cPodaj poprawną ilość większą od 0!";
    private String paySelf = "&#FF0000✘ &8Ξ &cNie możesz przelać pieniędzy samemu sobie!";
    private String paySent = "&#00FF3B✔ &8Ξ &#4EFA76Przelałeś &e{amount}$ &agraczowi &e{player}&a!";
    private String payReceived = "&#00FF3B✔ &8Ξ &#4EFA76Otrzymałeś &e{amount}$ &aod gracza &e{player}&a!";

    private String shopItemAdded = "&#00FF3B✔ &8Ξ &#4EFA76Dodano przedmiot do sklepu!";
    private String shopItemBought = "&#00FF3B✔ &8Ξ &#4EFA76Kupiono przedmiot!";
    private String shopNotEnoughPlaytime = "&#FF0000✘ &8Ξ &cNie masz wystarczająco czasu gry!";

    private String pickupCreated = "&#00FF3B✔ &8Ξ &#4EFA76Stworzono przedmiot &e{name}&a!";
    private String pickupGiven = "&#00FF3B✔ &8Ξ &#4EFA76Dano przedmioty graczowi &e{player}&a!";
    private String pickupGivenAll = "&#00FF3B✔ &8Ξ &#4EFA76Dano przedmioty wszystkim graczom!";
    private String pickupReceived = "&#00FF3B✔ &8Ξ &#4EFA76Odebrano przedmioty!";
    private String pickupEmpty = "&#FF0000✘ &8Ξ &cNie masz nic do odebrania!";
    private String pickupBossbar = "&#E8D5FFɴɪᴇᴏᴅᴇʙʀᴀɴᴇ ᴘʀᴢᴇᴅᴍɪᴏᴛʏ &8• &#C9A0FF/odbierz";

    private String msgFormat = "&8[&e{sender} &8-> &e{receiver}&8] &7{message}";
    private String noReply = "&#FF0000✘ &8Ξ &cNie masz komu odpowiedzieć!";

    private String tpaTeleportingMessage = "&#00E0FFℹ &8Ξ &fTeleportacja za &e5 &7sekund... Nie ruszaj się!";
    private String tpaTeleportCancelled = "&#FF0000✘ &8Ξ &cTeleportacja anulowana - ruszyłeś się!";

    private String walletPurchaseSuccess = "&#00FF3B✔ &8Ξ &#4EFA76Zakupiono przedmiot za &e{price} wPLN&a!";

    private String chatCooldownMessage = "&#FF0000✘ &8Ξ &cMusisz odczekać &4{time}s &czanim napiszesz ponownie!";
    private int chatCooldownSeconds = 2;
    private String chatCooldownBypassPermission = "Holly.chat.bypass";

    private String afkTeleportMessage = "&#00E0FFℹ &8Ξ &fZostałeś przeteleportowany na AFK z powodu bezczynności.";
    private int afkTimeSeconds = 300;
    private String afkWarpName = "afk";
    private boolean afkTeleportEnabled = true;

    private String losujZestawNoKits = "&#FF0000✘ &8Ξ &cNie masz dostępnych zestawów do wylosowania!";
    private String losujZestawSuccess = "&#00FF3B✔ &8Ξ &#4EFA76Wylosowałeś zestaw &e{kit}&a!";


    private String clanNotInClan = "&#FF0000✘ &8Ξ &cNie jesteś w klanie!";
    private String clanAlreadyInClan = "&#FF0000✘ &8Ξ &cJuż jesteś w klanie!";
    private String clanCreated = "&#00FF3B✔ &8Ξ &#4EFA76Stworzono klan &e{tag}&a!";
    private String clanDeleted = "&#00FF3B✔ &8Ξ &#4EFA76Usunięto klan!";
    private String clanInviteSent = "&#00FF3B✔ &8Ξ &#4EFA76Wysłano zaproszenie do &e{player}&a!";
    private String clanInviteReceived = "&#00E0FFℹ &8Ξ &fOtrzymałeś zaproszenie do klanu &e{clan}&7!";
    private String clanJoined = "&#00FF3B✔ &8Ξ &#4EFA76Dołączyłeś do klanu &e{clan}&a!";
    private String clanLeft = "&#00FF3B✔ &8Ξ &#4EFA76Opuściłeś klan!";
    private String clanKicked = "&#00FF3B✔ &8Ξ &#4EFA76Wyrzucono gracza &e{player} &az klanu!";
    private String clanNoPermission = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/klan &8(&4permisja-klanu&8)";
    private String armorStandLimitReached = "&#FF0000✘ &8Ξ &cOsiągnięto limit 3 stojaków na zbroje na jeden chunk!";
    private String minecartLimitReached = "&#FF0000✘ &8Ξ &cOsiągnięto limit 3 wagoników na jeden chunk!";
    private String inventoryBlockedDuringRestart = "&#FFB800⚠ &8Ξ &eInterakcje z ekwipunkiem są zablokowane na czas restartu!";
    private String commandsBlockedDuringRestart = "&#FFB800⚠ &8Ξ &eKomendy są zablokowane na czas restartu!";

    public String getNoPermission() { return noPermission; }
    public String getPlayerNotFound() { return playerNotFound; }
    public String getPlayerOnly() { return playerOnly; }
    public String getInvalidArguments() { return invalidArguments; }
    public String getCooldownMessage() { return cooldownMessage; }
    public String getSuccess() { return success; }
    public String getTeleported() { return teleported; }
    public String getGamemodeChanged() { return gamemodeChanged; }
    public String getGamemodeTitle() { return gamemodeTitle; }
    public String getGamemodeSubtitle() { return gamemodeSubtitle; }
    public int getGamemodeTitleFadeIn() { return gamemodeTitleFadeIn; }
    public int getGamemodeTitleStay() { return gamemodeTitleStay; }
    public int getGamemodeTitleFadeOut() { return gamemodeTitleFadeOut; }
    public String getFlyspeedChanged() { return flyspeedChanged; }
    public String getFlyEnabled() { return flyEnabled; }
    public String getFlyDisabled() { return flyDisabled; }
    public String getFlyEnabledOther() { return flyEnabledOther; }
    public String getFlyDisabledOther() { return flyDisabledOther; }
    public String getTpaRequestSent() { return tpaRequestSent; }
    public String getTpaRequestReceived() { return tpaRequestReceived; }
    public String getTpaAccepted() { return tpaAccepted; }
    public String getTpaDenied() { return tpaDenied; }
    public String getTpaExpired() { return tpaExpired; }
    public String getNoTpaRequest() { return noTpaRequest; }
    public String getSpawnSet() { return spawnSet; }
    public String getSpawnNotSet() { return spawnNotSet; }
    public String getSpawnTeleported() { return spawnTeleported; }
    public String getSpawnTeleportingSubtitle() { return spawnTeleportingSubtitle; }
    public String getSpawnTeleportSuccessTitle() { return spawnTeleportSuccessTitle; }
    public String getSpawnTeleportSuccessSubtitle() { return spawnTeleportSuccessSubtitle; }
    public String getSpawnTeleportCancelled() { return spawnTeleportCancelled; }
    public String getSpawnTeleportOther() { return spawnTeleportOther; }
    public String getBalanceMessage() { return balanceMessage; }
    public String getMoneyAdded() { return moneyAdded; }
    public String getMoneyRemoved() { return moneyRemoved; }
    public String getMoneySet() { return moneySet; }
    public String getMoneyReset() { return moneyReset; }
    public String getNotEnoughMoney() { return notEnoughMoney; }
    public String getPayUsage() { return payUsage; }
    public String getPayInvalidAmount() { return payInvalidAmount; }
    public String getPaySelf() { return paySelf; }
    public String getPaySent() { return paySent; }
    public String getPayReceived() { return payReceived; }
    public String getShopItemAdded() { return shopItemAdded; }
    public String getShopItemBought() { return shopItemBought; }
    public String getShopNotEnoughPlaytime() { return shopNotEnoughPlaytime; }
    public String getPickupCreated() { return pickupCreated; }
    public String getPickupGiven() { return pickupGiven; }
    public String getPickupGivenAll() { return pickupGivenAll; }
    public String getPickupReceived() { return pickupReceived; }
    public String getPickupEmpty() { return pickupEmpty; }
    public String getPickupBossbar() { return pickupBossbar; }
    public String getMsgFormat() { return msgFormat; }
    public String getNoReply() { return noReply; }
    public String getTpaTeleportingMessage() { return tpaTeleportingMessage; }
    public String getTpaTeleportCancelled() { return tpaTeleportCancelled; }
    public String getWalletPurchaseSuccess() { return walletPurchaseSuccess; }
    public String getClanNotInClan() { return clanNotInClan; }
    public String getClanAlreadyInClan() { return clanAlreadyInClan; }
    public String getClanCreated() { return clanCreated; }
    public String getClanDeleted() { return clanDeleted; }
    public String getClanInviteSent() { return clanInviteSent; }
    public String getClanInviteReceived() { return clanInviteReceived; }
    public String getClanJoined() { return clanJoined; }
    public String getClanLeft() { return clanLeft; }
    public String getClanKicked() { return clanKicked; }
    public String getClanNoPermission() { return clanNoPermission; }
    public String getArmorStandLimitReached() { return armorStandLimitReached; }
    public String getMinecartLimitReached() { return minecartLimitReached; }
    public String getInventoryBlockedDuringRestart() { return inventoryBlockedDuringRestart; }
    public String getCommandsBlockedDuringRestart() { return commandsBlockedDuringRestart; }
    public String getChatCooldownMessage() { return chatCooldownMessage; }
    public int getChatCooldownSeconds() { return chatCooldownSeconds; }
    public String getChatCooldownBypassPermission() { return chatCooldownBypassPermission; }
    public String getAfkTeleportMessage() { return afkTeleportMessage; }
    public int getAfkTimeSeconds() { return afkTimeSeconds; }
    public String getAfkWarpName() { return afkWarpName; }
    public boolean isAfkTeleportEnabled() { return afkTeleportEnabled; }
    public String getLosujZestawNoKits() { return losujZestawNoKits; }
    public String getLosujZestawSuccess() { return losujZestawSuccess; }
}
