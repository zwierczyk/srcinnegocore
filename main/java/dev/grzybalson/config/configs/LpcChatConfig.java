package dev.grzybalson.config.configs;

import dev.grzybalson.utils.LuckPermsGroups;
import eu.okaeri.configs.OkaeriConfig;

import java.util.ArrayList;
import java.util.List;

public class LpcChatConfig extends OkaeriConfig {

    private boolean enableChat = true;
    private List<String> blockedWorld = new ArrayList<>();
    private List<ChatGroup> chatGroups = new ArrayList<>(List.of(new ChatGroup()));
    private String blockedMessage = "&#FF0000✘ &8Ξ &cTa wiadomość zawiera zablokowane słowo.";
    private String groupNotConfiguredMessage = "&#FF0000✘ &8Ξ &cTwoja ranga LuckPerms nie jest skonfigurowana na chacie.";
    private String cooldownMessage = "&#FF0000✘ &8Ξ &cMożesz napisać następną wiadomość za &f{TIME}ms&c.";
    private String chatDisabledMessage = "&#FF0000✘ &8Ξ &cChat jest obecnie wyłączony.";
    private String bypassCooldownPermission = "holly.chat.bypasscooldown";
    private String bypassBlockedWordsPermission = "holly.chat.bypassblocked";

    private boolean antiAdEnabled = true;
    private boolean antiAdBlockLinks = true;
    private boolean antiAdBlockDiscord = true;
    private String antiAdMessage = "&#FF0000✘ &8Ξ &cReklamy, linki i zaproszenia Discord są zabronione.";
    private String bypassAntiAdPermission = "holly.chat.bypassad";
    private List<String> antiAdAllowedDomains = new ArrayList<>(List.of(
        "masakra.gg",
        "www.masakra.gg",
        "dc.masakra.gg"
    ));
    private List<String> antiAdBlockedPhrases = new ArrayList<>(List.of(
        "gz najlepszy",
        "najlepszy gz",
        "mod na kopanie",
        "mody na kopanie",
        "bezpieczny mod",
        "bezpieczne mody",
        "bezpieczny yt",
        "bezpieczne yt",
        "calkiem bezpieczn",
        "całkiem bezpieczn",
        "xray mod",
        "x-ray mod",
        "mod xray",
        "pobierz moda",
        "pobierz mod",
        "najlepszy mod",
        "najlepsze mody",
        "wejdz na serwer",
        "wejdź na serwer",
        "gramy na",
        "zapraszam na dc",
        "zapraszam na discord",
        "dolacz na dc",
        "dołącz na dc",
        "discord gg",
        "dc gg",
        "dsc gg"
    ));

    public boolean isEnableChat() {
        return enableChat;
    }

    public List<String> getBlockedWorld() {
        return blockedWorld;
    }

    public List<ChatGroup> getChatGroups() {
        return chatGroups;
    }

    public String getBlockedMessage() {
        return blockedMessage;
    }

    public String getGroupNotConfiguredMessage() {
        return groupNotConfiguredMessage;
    }

    public String getCooldownMessage() {
        return cooldownMessage;
    }

    public String getChatDisabledMessage() {
        return chatDisabledMessage;
    }

    public String getBypassCooldownPermission() {
        return bypassCooldownPermission;
    }

    public String getBypassBlockedWordsPermission() {
        return bypassBlockedWordsPermission;
    }

    public boolean isAntiAdEnabled() {
        return antiAdEnabled;
    }

    public boolean isAntiAdBlockLinks() {
        return antiAdBlockLinks;
    }

    public boolean isAntiAdBlockDiscord() {
        return antiAdBlockDiscord;
    }

    public String getAntiAdMessage() {
        return antiAdMessage;
    }

    public String getBypassAntiAdPermission() {
        return bypassAntiAdPermission;
    }

    public List<String> getAntiAdAllowedDomains() {
        return antiAdAllowedDomains;
    }

    public List<String> getAntiAdBlockedPhrases() {
        return antiAdBlockedPhrases;
    }

    public ChatGroup findGroup(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (ChatGroup group : chatGroups) {
            if (group.getGroupName() != null && group.getGroupName().equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }

    public ChatGroup findBestGroup(Iterable<String> ownedGroups) {
        if (ownedGroups == null) {
            return findGroup("default");
        }
        ChatGroup best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (String name : ownedGroups) {
            ChatGroup group = findGroup(name);
            if (group == null) {
                continue;
            }
            int index = LuckPermsGroups.rankIndex(name);
            if (index < bestIndex) {
                bestIndex = index;
                best = group;
            }
        }
        return best != null ? best : findGroup("default");
    }

    public static class ChatGroup extends OkaeriConfig {
        private String groupName = "default";
        private String messageFormat = "&#DBE200%holly_ranking%ュ {TAG}&f{NICK} &8» &7{MESSAGE}";
        private long cooldown = 3000L;

        public String getGroupName() {
            return groupName;
        }

        public String getMessageFormat() {
            return messageFormat;
        }

        public long getCooldown() {
            return cooldown;
        }
    }
}
