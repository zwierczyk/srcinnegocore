package dev.grzybalson.config.anarchiagg;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class ZmiankaBattleConfig extends OkaeriConfig {
    private Settings settings = new Settings();
    private Messages messages = new Messages();
    private Battle battle = new Battle();
    private Sounds sounds = new Sounds();
    private Bossbars bossbars = new Bossbars();

    public Settings getSettings() { return settings; }
    public Messages getMessages() { return messages; }
    public Battle getBattle() { return battle; }
    public Sounds getSounds() { return sounds; }
    public Bossbars getBossbars() { return bossbars; }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Settings extends OkaeriConfig {
        private double minBet = 100.0D;
        private int inviteExpireSeconds = 30;
        private int rollStepTicks = 2;
        private int rollSoundEveryUpdates = 2;
        private int rollPauseBetweenPlayersTicks = 20;
        private int winBossbarDurationTicks = 40;
        private int guiCloseAfterFinishTicks = -1;
        private Worldguard worldguard = new Worldguard();
        private Global global = new Global();
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Worldguard extends OkaeriConfig {
        private boolean enabled = true;
        private String region = "spawn";
        private boolean restrictChallenge = true;
        private boolean restrictAccept = true;
        private boolean enforceStayInRegion = true;
        private boolean cancelInviteOnLeave = true;
        private boolean cancelBattleOnLeave = true;
        private boolean instantFinishBattleOnLeave = false;
        private boolean cancelGlobalOnLeave = true;
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Global extends OkaeriConfig {
        private int radius = 8;
        private int durationSeconds = 60;
        private int notifyIntervalSeconds = 10;
        private double hologramYOffset = 2.35D;
        private int loopPeriodTicks = 4;
        private int hologramUpdateIntervalTicks = 6;
        private double hologramMinMoveDistance = 0.35D;
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Messages extends OkaeriConfig {
        private List<String> inviteReceived = List.of(
                "&fGracz §x§2§7§A§4§E§C{player}&f wysyła ci §x§2§7§A§4§E§Cbitwę &fna zmianki o §x§2§7§A§4§E§C{ilość}",
                "&fWpisz §x§2§7§A§4§E§C/zmianki bitwa zakceptuj {playername}&f lub §x§2§7§A§4§E§C§lKLIKNIJ TUTAJ"
        );
        private String inviteSent = "&#00FF3B✔ &8Ξ &#4EFA76Wysłałeś zaproszenie do bitwy na zmianki graczowi &f{player} &#4EFA76o &f{ilość}&#4EFA76.";
        private String inviteExpiredInviter = "&cTwoje zaproszenie do &f{player}&c wygasło.";
        private String inviteExpiredInvited = "&cZaproszenie od &f{player}&c wygasło.";
        private String inviteCancelledLeftRegion = "&#FF0000✘ &8Ξ &cZaproszenie na zmianki zostało anulowane, ponieważ &f{player} &copuścił region &f{region}&c.";
        private String battleStarted = "&aBitwa na zmianki wystartowała! Stawka: &f{ilość}&a.";
        private String battleEnded = "&7Wynik: &f{winer} &7({winer_percent}%) vs &f{loser} &7({loser_percent}%).";
        private String globalOpened = "&aWłączyłeś globalne zmianki o &f{ilosc}$&a. &7Przez {seconds}s gracze w promieniu {radius} kratek mogą kliknąć hologram.";
        private String globalExpired = "&cGlobalne zmianki o &f{ilosc}$&c wygasły.";
        private String globalCancelledLeftRegion = "&#FF0000✘ &8Ξ &cGlobalne zmianki zostały anulowane, ponieważ opuściłeś region &f{region}&c.";
        private List<String> globalNearby = List.of(
                "&3&lOTWARTA GRA &8- &b{player}",
                "&7Stawka: &3{ilosc}$ &8| &7Pozostało: &e{seconds}s",
                "&eKliknij hologram nad nim."
        );
        private Errors errors = new Errors();
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Errors extends OkaeriConfig {
    private String usage = "&#FF0000✘ &8Ξ &cPoprawne użycie: &#FF0000\n &8• &4/zmianki global <kwota>\n &8• &4/zmianki wyzwij <nick> <kwota> &7(min {min})";
    private String usageAccept = "&#FF0000✘ &8Ξ &cPoprawne użycie: &#FF0000\n &8• &4/zmianki bitwa zaakceptuj <nick>";
        private String playerOffline = "&cTen gracz jest offline.";
        private String cannotSelf = "&cNie możesz zaprosić samego siebie.";
        private String tooLow = "&cMinimalna kwota to &f{min}&c.";
        private String noInvite = "&cNie masz zaproszenia od tego gracza.";
        private String differentWorld = "&cMusicie być w tym samym świecie.";
        private String noMoney = "&cBrakuje ci pieniędzy. Potrzebujesz: &f{ilość}&c.";
        private String otherNoMoney = "&cPrzeciwnik nie ma pieniędzy.";
        private String alreadyBattle = "&cKtoś z was jest już w bitwie.";
        private String inviterOffline = "&cGracz, który cię zaprosił, jest offline.";
        private String inviteBusy = "&cMasz już aktywne zaproszenie na zmianki. Poczekaj aż wygaśnie lub zostanie zaakceptowane.";
        private String targetBusy = "&cGracz &f{player}&c ma już aktywne zaproszenie na zmianki lub jest w trakcie bitwy.";
        private String economyUnavailable = "&cEkonomia jeszcze się ładuje. Spróbuj za chwilę.";
        private String worldguardUnavailable = "&cWorldGuard jest niedostępny. Nie można sprawdzić regionu.";
        private String regionNotFound = "&#FF0000✘ &8Ξ &cRegion WorldGuard &f{region} &cnie istnieje w tym świecie.";
        private String regionNotAllowed = "&cMożesz użyć tej akcji tylko w regionie &f{region}&c.";
        private String otherNotInRegion = "&cGracz &f{player}&c musi być w regionie &f{region}&c.";
        private String globalAlreadyOpen = "&cMasz już otwarte globalne zmianki.";
        private String globalTooFar = "&cMusisz być w promieniu 8 kratek od gracza.";
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Battle extends OkaeriConfig {
        private Gui gui = new Gui();
        private PercentRange percentRange = new PercentRange();
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Gui extends OkaeriConfig {
        private String title = "&8Zmianki - {ilość}";
        private int rows = 3;
        private Slots slots = new Slots();
        private PercentItem percentItem = new PercentItem();
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Slots extends OkaeriConfig {
        private int inviterHead = 10;
        private int inviterPercent = 11;
        private int invitedPercent = 15;
        private int invitedHead = 16;
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class PercentItem extends OkaeriConfig {
        private String name = "&fSzansa: &e{percent}%";
        private List<String> lore = List.of("&7Wylosowana wartość");
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class PercentRange extends OkaeriConfig {
        private int min = -24;
        private int max = 52;
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Sounds extends OkaeriConfig {
        private String click = "ui.click";
        private String error = "villager.no";
        private String succes = "expieriene_pickup";
        private String roll = "block.dispenser.dispense";
        private String win_sound = "player_lvlup";
        private String lose_sound = "villager.hurt";
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class Bossbars extends OkaeriConfig {
        private BossbarEntry createAlert = new BossbarEntry("red", "&cGracz &4{inviter_name} &ctoczy bitwę na zmianki z &4{invited_name}&c o kwotę &4{ilość}&c!");
        private BossbarEntry winAlert = new BossbarEntry("red", "&cGracz &4{winer}&c wygrał z graczem &c{loser} &co kwotę &4{ilość}&c!");
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class BossbarEntry extends OkaeriConfig {
        private String color;
        private String title;

        public BossbarEntry() {
            this("red", "");
        }

        public BossbarEntry(String color, String title) {
            this.color = color;
            this.title = title;
        }
    }
}
