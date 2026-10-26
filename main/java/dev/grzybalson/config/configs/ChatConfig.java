package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class ChatConfig extends OkaeriConfig {

    private String chatClearMessage = "&#00FF3B✔ &8Ξ &#4EFA76Chat został wyczyszczony przez &e{player}&a!";
    private String chatDisabledMessage = "&#FF0000✘ &8Ξ &cChat został wyłączony przez &e{player}&c!";
    private String chatEnabledMessage = "&#00FF3B✔ &8Ξ &#4EFA76Chat został włączony przez &e{player}&a!";
    private String chatIsDisabledMessage = "&#FF0000✘ &8Ξ &cChat jest aktualnie wyłączony!";
    private String chatPermission = "Holly.cmd.chat";
    private String chatClearPermission = "Holly.cmd.chat.clear";
    private String chatTogglePermission = "Holly.cmd.chat.toggle";
    private String noPermissionMessage = "&#FF0000✘ &8Ξ &cNie posiadasz dostępu do &#FF0000/chat &8(&4Holly.cmd.chat&8)";
    private int clearLines = 100;

    private String chatUsage = "&#FF0000✘ &8Ξ &cPoprawne użycie: &#FF0000\n &8• &4/chat <clear|off|on>";

    /**
     * Hover przy najechaniu na nick / wiadomość.
     * Placeholdery: {prefix} {player} {nick} {money} {kills} {deaths} {playtime} {rank}
     */
    private boolean hoverEnabled = true;
    private String hoverClickCommand = "/hollyplayeraction {player}";
    private List<String> hoverLines = new ArrayList<>(Arrays.asList(
        "{prefix}&7{player}",
        "&fStan konta: &e{money}",
        "&fZabójstwa: &c{kills}"
    ));

    // Legacy keys (migracja → hover-lines)
    private String hoverHeader = "{prefix}&7{player}";
    private String hoverNick = "{prefix}&7{player}";
    private String hoverRank = "&7Ranga: &f{rank}";
    private String hoverKills = "&fZabójstwa: &c{kills}";
    private String hoverDeaths = "&7Śmierci: &f{deaths}";
    private String hoverMoney = "&fStan konta: &e{money}";
    private String hoverPlaytime = "&7Czas gry: &b{playtime}";
    private String hoverClickInfo = "&aKliknij, aby wysłać prośbę o teleportację!";

    // Player action GUI
    private String playerActionGuiTitle = "&8Opcje gracza: &e{player}";
    private int playerActionGuiRows = 3;
    private String tpaButtonName = "&a&lTeleportacja (TPA)";
    private String tpaButtonMaterial = "ENDER_PEARL";
    private int tpaButtonSlot = 11;
    private int tpaButtonCustomModelData = 0;
    private String tpaButtonLore = "&7Kliknij, aby wysłać prośbę o teleportację";
    private String cancelButtonName = "&c&lAnuluj";
    private String cancelButtonMaterial = "BARRIER";
    private int cancelButtonSlot = 15;
    private int cancelButtonCustomModelData = 0;
    private String cancelButtonLore = "&7Kliknij, aby zamknąć";
    private String tpaSentFromGui = "&#00FF3B✔ &8Ξ &#4EFA76Wysłano prośbę o teleportację do &e{player}&a!";

    public String getChatClearMessage() { return chatClearMessage; }
    public String getChatDisabledMessage() { return chatDisabledMessage; }
    public String getChatEnabledMessage() { return chatEnabledMessage; }
    public String getChatIsDisabledMessage() { return chatIsDisabledMessage; }
    public String getChatPermission() { return chatPermission; }
    public String getChatClearPermission() { return chatClearPermission; }
    public String getChatTogglePermission() { return chatTogglePermission; }
    public String getNoPermissionMessage() { return noPermissionMessage; }
    public int getClearLines() { return clearLines; }
    public String getChatUsage() { return chatUsage; }

    public boolean isHoverEnabled() { return hoverEnabled; }
    public String getHoverClickCommand() {
        return hoverClickCommand == null || hoverClickCommand.isBlank()
            ? "/hollyplayeraction {player}"
            : hoverClickCommand;
    }
    public List<String> getHoverLines() {
        if (hoverLines != null && !hoverLines.isEmpty()) {
            return hoverLines;
        }
        return List.of(
            getHoverHeader(),
            hoverMoney == null ? "&fStan konta: &e{money}" : hoverMoney,
            hoverKills == null ? "&fZabójstwa: &c{kills}" : hoverKills
        );
    }

    public String getHoverHeader() {
        if (hoverHeader != null && !hoverHeader.isBlank()) {
            return hoverHeader;
        }
        return hoverNick != null && !hoverNick.isBlank() ? hoverNick : "{prefix}&7{player}";
    }
    public String getHoverNick() { return hoverNick; }
    public String getHoverRank() { return hoverRank; }
    public String getHoverKills() { return hoverKills; }
    public String getHoverDeaths() { return hoverDeaths; }
    public String getHoverMoney() { return hoverMoney; }
    public String getHoverPlaytime() { return hoverPlaytime; }
    public String getHoverClickInfo() { return hoverClickInfo; }

    public boolean migrateHoverDefaults() {
        boolean changed = false;
        if (hoverLines == null) {
            hoverLines = new ArrayList<>();
            changed = true;
        }
        if (hoverLines.isEmpty()) {
            hoverLines.addAll(List.of(
                "{prefix}&7{player}",
                "&fStan konta: &e{money}",
                "&fZabójstwa: &c{kills}"
            ));
            changed = true;
        }
        if (isBlank(hoverClickCommand)) {
            hoverClickCommand = "/hollyplayeraction {player}";
            changed = true;
        }
        if (isBlank(hoverHeader) || looksOldHeader(hoverHeader) || looksOldHeader(hoverNick)) {
            hoverHeader = "{prefix}&7{player}";
            hoverNick = "{prefix}&7{player}";
            changed = true;
        }
        if (isBlank(hoverMoney) || hoverMoney.contains("Kasa") || hoverMoney.contains("💰")) {
            hoverMoney = "&fStan konta: &e{money}";
            changed = true;
        }
        if (isBlank(hoverKills) || hoverKills.contains("⚔") || hoverKills.contains("&a{kills}")) {
            hoverKills = "&fZabójstwa: &c{kills}";
            changed = true;
        }
        // Podmień stare linie w hover-lines jeśli wyglądają na legacy.
        List<String> migrated = new ArrayList<>();
        boolean linesChanged = false;
        for (String line : hoverLines) {
            if (line == null) {
                continue;
            }
            String next = line;
            if (line.contains("Nick:") || (line.contains("{player}") && !line.contains("{prefix}") && line.contains("Nick"))) {
                next = "{prefix}&7{player}";
            } else if (line.contains("Kasa") || line.contains("💰")) {
                next = "&fStan konta: &e{money}";
            } else if (line.contains("⚔") || (line.contains("Zabójstwa") && line.contains("&a{kills}"))) {
                next = "&fZabójstwa: &c{kills}";
            }
            if (!next.equals(line)) {
                linesChanged = true;
            }
            migrated.add(next);
        }
        if (linesChanged) {
            hoverLines = migrated;
            changed = true;
        }
        return changed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean looksOldHeader(String value) {
        if (value == null) {
            return true;
        }
        return value.contains("Nick:") || !value.contains("{prefix}");
    }

    public String getPlayerActionGuiTitle() { return playerActionGuiTitle; }
    public int getPlayerActionGuiRows() { return playerActionGuiRows; }
    public String getTpaButtonName() { return tpaButtonName; }
    public String getTpaButtonMaterial() { return tpaButtonMaterial; }
    public int getTpaButtonSlot() { return tpaButtonSlot; }
    public int getTpaButtonCustomModelData() { return tpaButtonCustomModelData; }
    public String getTpaButtonLore() { return tpaButtonLore; }
    public String getCancelButtonName() { return cancelButtonName; }
    public String getCancelButtonMaterial() { return cancelButtonMaterial; }
    public int getCancelButtonSlot() { return cancelButtonSlot; }
    public int getCancelButtonCustomModelData() { return cancelButtonCustomModelData; }
    public String getCancelButtonLore() { return cancelButtonLore; }
    public String getTpaSentFromGui() { return tpaSentFromGui; }
}
