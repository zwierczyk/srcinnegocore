package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class KitConfig extends OkaeriConfig {

    private String guiTitle = "  &#FFD89B&lᴋɪᴛʏ";
    private int guiRows = 5;
    private String kitEditGuiTitle = "&8Edycja kita: &f{kit}";
    private String kitPreviewGuiTitle = "&8Zestawy";

    private String header = "&f";
    private String title = "  &#FFD89B&lᴋɪᴛʏ";
    private String created = " &#60FF8B&l✔ &#C1C1C1ᴋɪᴛ &#FFFFFF&l{NAME} &#C1C1C1ᴜᴛᴡᴏʀᴢᴏɴʏ";
    private String deleted = " &#60FF8B&l✔ &#C1C1C1ᴋɪᴛ &#FFFFFF&l{NAME} &#C1C1C1ᴜꜱᴜɴɪᴇᴛʏ";
    private String notFound = " &#FF5C5C&l✖ &#C1C1C1ᴋɪᴛ &#FFFFFF{NAME} &#C1C1C1ɴɪᴇ ɪꜱᴛɴɪᴇᴊᴇ";
    private String alreadyExists = " &#FF5C5C&l✖ &#C1C1C1ᴋɪᴛ &#FFFFFF{NAME} &#C1C1C1ᴊᴜᴢ ɪꜱᴛɴɪᴇᴊᴇ";
    private String received = " &#60FF8B&l✔ &#C1C1C1ᴏᴅᴇʙʀᴀɴᴏ ᴢᴇꜱᴛᴀᴡ &#FFFFFF&l{NAME}";
    private String cooldown = " &#FF5C5C&l✖ &#C1C1C1ᴍᴜꜱɪꜱᴢ ᴘᴏᴄᴢᴇᴋᴀᴄ ᴊᴇꜱᴢᴄᴢᴇ &#FFFFFF&l{TIME}";
    private String noPermission = " &#FF5C5C&l✖ &#C1C1C1ɴɪᴇ ᴘᴏꜱɪᴀᴅᴀꜱᴢ ᴘᴇʀᴍɪꜱᴊɪ ᴅᴏ ᴋɪᴛᴀ &#FFFFFF{NAME}";
    private String itemsSaved = " &#60FF8B&l✔ &#C1C1C1ᴢᴀᴘɪꜱᴀɴᴏ &#FFFFFF&l{COUNT} &#C1C1C1ᴘʀᴢᴇᴅᴍɪᴏᴛᴏᴡ ᴅᴏ ᴋɪᴛᴀ &#FFFFFF&l{NAME}";
    private String loreAdded = " &#60FF8B&l✔ &#C1C1C1ᴅᴏᴅᴀɴᴏ ʟᴏʀᴇ ᴅᴏ ᴋɪᴛᴀ &#FFFFFF&l{NAME}";
    private String loreRemoved = " &#60FF8B&l✔ &#C1C1C1ᴜꜱᴜɴɪᴇᴛᴏ ʟɪɴɪᴇ ʟᴏʀᴇ #{LINE} ᴢ ᴋɪᴛᴀ &#FFFFFF&l{NAME}";
    private String guiClaimButton = "&#60FF8B&lᴏᴅʙɪᴇʀᴢ ᴢᴇꜱᴛᴀᴡ";
    private String guiClaimLore = "&7Kliknij, aby odebrać zestaw";
    private String helpCreate = "  &#89E2FF/akit create &8<&#C1C1C1nazwa&8> <&#C1C1C1displayName&8> <&#C1C1C1blok&8> <&#C1C1C1perm&8> <&#C1C1C1cooldown&8> <&#C1C1C1slot&8>";
    private String helpDelete = "  &#89E2FF/akit delete &8<&#C1C1C1nazwa&8> &8— &7Usuń kit";
    private String helpSetitems = "  &#89E2FF/akit setitems &8<&#C1C1C1nazwa&8> &8— &7Zapisz eq jako kit items";
    private String helpAddlore = "  &#89E2FF/akit addlore &8<&#C1C1C1nazwa&8> <&#C1C1C1tekst&8> &8— &7Dodaj linię lore";
    private String helpRemovelore = "  &#89E2FF/akit removelore &8<&#C1C1C1nazwa&8> <&#C1C1C1linia&8> &8— &7Usuń linię lore";
    private String helpList = "  &#89E2FF/akit list &8— &7Lista kitów";
    private String helpGui = "  &#89E2FF/akit gui &8— &7Otwórz GUI kitów";
    private String helpSetprefix = "  &#89E2FF/akit setprefix &8<&#C1C1C1nazwa&8> <&#C1C1C1tekst&8> &8— &7Tekst dla &#FFFFFF{KIT}";
    private String helpSeticon = "  &#89E2FF/akit seticon &8<&#C1C1C1nazwa&8> <&#C1C1C1MATERIAL|head-value&8> &8— &7Ikona w GUI";
    private String helpEdit = "  &#89E2FF/akit edit &8<&#C1C1C1nazwa&8> &8— &7Edytuj przedmioty kita";
    private String helpReload = "  &#89E2FF/akit reload &8— &7Przeładuj kity";
    private String helpResetcooldown = "  &#89E2FF/akit resetcooldown &8<&#C1C1C1nick|*&8> [&#C1C1C1kit|*&8]";
    private List<String> guiItemFormat = List.of(
        "",
        "  &8▪ &7Dostęp dla: {KIT}",
        "  &8▪ &7Czas oczekiwania: &f{COOLDOWN}",
        "  &8▪ &7Status: {STATUS}",
        "",
        "  &8▪ &#56FF00Kliknij, aby &#2D8600podejrzeć &#56FF00zestaw"
    );
    private String guiStatusAvailable = "&#60FF8Bᴅᴏꜱᴛᴇᴘɴʏ";
    private String guiStatusUnavailable = "&#FF5C5Cʙʀᴀᴋ ᴘᴇʀᴍɪꜱᴊɪ";
    private String prefixSet = " &#60FF8B&l✔ &#C1C1C1Ustawiono tekst &#FFFFFF{PREFIX} &#C1C1C1dla kita &#FFFFFF&l{NAME}";

    private String kitReceivedMessage = " &#60FF8B&l✔ &#C1C1C1ᴏᴅᴇʙʀᴀɴᴏ ᴢᴇꜱᴛᴀᴡ &#FFFFFF&l{NAME}";
    private String kitCooldownMessage = " &#FF5C5C&l✖ &#C1C1C1ᴍᴜꜱɪꜱᴢ ᴘᴏᴄᴢᴇᴋᴀᴄ ᴊᴇꜱᴢᴄᴢᴇ &#FFFFFF&l{TIME}";
    private String kitNotFoundMessage = " &#FF5C5C&l✖ &#C1C1C1ᴋɪᴛ &#FFFFFF{NAME} &#C1C1C1ɴɪᴇ ɪꜱᴛɴɪᴇᴊᴇ";
    private String kitNoPermissionMessage = " &#FF5C5C&l✖ &#C1C1C1ɴɪᴇ ᴘᴏꜱɪᴀᴅᴀꜱᴢ ᴘᴇʀᴍɪꜱᴊɪ ᴅᴏ ᴋɪᴛᴀ &#FFFFFF{NAME}";
    private String kitSavedMessage = " &#60FF8B&l✔ &#C1C1C1ᴢᴀᴘɪꜱᴀɴᴏ kit &#FFFFFF&l{NAME}";
    private String kitDeletedMessage = " &#60FF8B&l✔ &#C1C1C1ᴋɪᴛ &#FFFFFF&l{NAME} &#C1C1C1ᴜꜱᴜɴɪᴇᴛʏ";
    private String kitCooldownResetMessage = " &#60FF8B&l✔ &#C1C1C1Zresetowano cooldown kita &#FFFFFF{NAME} &#C1C1C1dla &#FFFFFF{PLAYER}";
    private String kitReloadedMessage = " &#60FF8B&l✔ &#C1C1C1Konfiguracja kitów została przeładowana!";

    private String backItemMaterial = "BARRIER";
    private String backItemName = "&#FFD89B&lᴘᴏᴡʀᴏᴛ";
    private List<String> backItemLore = List.of("", "&8▸ &#C1C1C1Wróć do listy kitów");

    private int defaultCooldownHours = 24;
    private String firstJoinKit = "gracz";
    private String deathKit = "";
    private String startKitDiscordRequired = " &#FF5C5C&l✖ &#C1C1C1ᴋɪᴛ ꜱᴛᴀʀᴛ ᴡʏᴍᴀɢᴀ ᴏᴅᴇʙʀᴀɴᴇᴊ ɴᴀɢʀᴏᴅʏ ᴢ ᴅɪꜱᴄᴏʀᴅᴀ";
    private String startKitAlreadyClaimed = " &#FF5C5C&l✖ &#C1C1C1ᴋɪᴛ ꜱᴛᴀʀᴛ ᴍᴏᴢɴᴀ ᴏᴅᴇʙʀᴀᴄ ᴛʏʟᴋᴏ ʀᴀᴢ";
    private Map<String, Integer> kitCooldowns = new HashMap<>();
    private Map<String, KitData> kits = new HashMap<>();

    public String getGuiTitle() { return guiTitle; }
    public int getGuiRows() { return guiRows; }
    public String getKitEditGuiTitle() { return kitEditGuiTitle; }
    public String getKitPreviewGuiTitle() { return kitPreviewGuiTitle; }
    public String getHeader() { return header; }
    public String getTitle() { return title; }
    public String getCreated() { return created; }
    public String getDeleted() { return deleted; }
    public String getNotFound() { return notFound; }
    public String getAlreadyExists() { return alreadyExists; }
    public String getReceived() { return received; }
    public String getCooldown() { return cooldown; }
    public String getNoPermission() { return noPermission; }
    public String getItemsSaved() { return itemsSaved; }
    public String getLoreAdded() { return loreAdded; }
    public String getLoreRemoved() { return loreRemoved; }
    public String getGuiClaimButton() { return guiClaimButton; }
    public String getGuiClaimLore() { return guiClaimLore; }
    public String getHelpCreate() { return helpCreate; }
    public String getHelpDelete() { return helpDelete; }
    public String getHelpSetitems() { return helpSetitems; }
    public String getHelpAddlore() { return helpAddlore; }
    public String getHelpRemovelore() { return helpRemovelore; }
    public String getHelpList() { return helpList; }
    public String getHelpGui() { return helpGui; }
    public String getHelpSetprefix() { return helpSetprefix; }
    public String getHelpSeticon() { return helpSeticon != null ? helpSeticon : "  &#89E2FF/akit seticon &8<&#C1C1C1nazwa&8> <&#C1C1C1MATERIAL|head-value&8>"; }
    public String getHelpEdit() { return helpEdit; }
    public String getHelpReload() { return helpReload; }
    public String getHelpResetcooldown() { return helpResetcooldown; }
    public List<String> getGuiItemFormat() { return guiItemFormat == null ? List.of() : guiItemFormat; }
    public String getGuiStatusAvailable() { return guiStatusAvailable; }
    public String getGuiStatusUnavailable() { return guiStatusUnavailable; }
    public String getPrefixSet() { return prefixSet; }
    public String getKitReceivedMessage() { return kitReceivedMessage != null ? kitReceivedMessage : received; }
    public String getKitCooldownMessage() { return kitCooldownMessage != null ? kitCooldownMessage : cooldown; }
    public String getKitNotFoundMessage() { return kitNotFoundMessage != null ? kitNotFoundMessage : notFound; }
    public String getKitNoPermissionMessage() { return kitNoPermissionMessage != null ? kitNoPermissionMessage : noPermission; }
    public String getKitSavedMessage() { return kitSavedMessage; }
    public String getKitDeletedMessage() { return kitDeletedMessage != null ? kitDeletedMessage : deleted; }
    public String getKitCooldownResetMessage() { return kitCooldownResetMessage; }
    public String getKitReloadedMessage() { return kitReloadedMessage; }
    public String getBackItemMaterial() { return backItemMaterial; }
    public String getBackItemName() { return backItemName; }
    public List<String> getBackItemLore() { return backItemLore; }
    public int getDefaultCooldownHours() { return defaultCooldownHours; }
    public String getFirstJoinKit() { return firstJoinKit; }
    public String getDeathKit() { return deathKit; }
    public String getStartKitDiscordRequired() { return startKitDiscordRequired; }
    public String getStartKitAlreadyClaimed() { return startKitAlreadyClaimed; }
    public Map<String, Integer> getKitCooldowns() { return kitCooldowns; }
    public Map<String, KitData> getKits() { return kits; }

    public int getCooldownForKit(String kitName) {
        return kitCooldowns.getOrDefault(kitName.toLowerCase(), defaultCooldownHours);
    }

    public void addKit(String name, KitData kitData) {
        kits.put(name.toLowerCase(), kitData);
        this.save();
    }

    public void removeKit(String name) {
        kits.remove(name.toLowerCase());
        this.save();
    }

    public KitData getKit(String name) {
        return kits.get(name.toLowerCase());
    }

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class KitData extends OkaeriConfig {
        private String displayName = "";
        private String permission = "";
        private int cooldown = 3600;
        private int slot = 0;
        private String icon = "";
        private String iconMaterial = "";
        private String iconHeadValue = "";
        private List<String> items = new ArrayList<>();
        private List<String> lore = new ArrayList<>();
        private String prefix = "";

        public KitData() {}

        public KitData(
            String displayName,
            String permission,
            int cooldown,
            int slot,
            String icon,
            String iconMaterial,
            String iconHeadValue,
            List<String> items,
            List<String> lore,
            String prefix
        ) {
            this.displayName = displayName;
            this.permission = permission;
            this.cooldown = cooldown;
            this.slot = slot;
            this.icon = icon;
            this.iconMaterial = iconMaterial;
            this.iconHeadValue = iconHeadValue;
            this.items = items != null ? items : new ArrayList<>();
            this.lore = lore != null ? lore : new ArrayList<>();
            this.prefix = prefix != null ? prefix : "";
        }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getPermission() { return permission; }
        public void setPermission(String permission) { this.permission = permission; }
        public int getCooldown() { return cooldown; }
        public void setCooldown(int cooldown) { this.cooldown = cooldown; }
        public int getSlot() { return slot; }
        public void setSlot(int slot) { this.slot = slot; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public String getIconMaterial() { return iconMaterial; }
        public void setIconMaterial(String iconMaterial) { this.iconMaterial = iconMaterial; }
        public String getIconHeadValue() { return iconHeadValue; }
        public void setIconHeadValue(String iconHeadValue) { this.iconHeadValue = iconHeadValue; }
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
        public List<String> getLore() { return lore == null ? List.of() : lore; }
        public void setLore(List<String> lore) { this.lore = lore != null ? lore : new ArrayList<>(); }
        public String getPrefix() { return prefix == null ? "" : prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix != null ? prefix : ""; }
    }
}
