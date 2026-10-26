package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.ArrayList;
import java.util.List;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class SectorConfig extends OkaeriConfig {

    private String currentSector = "boxpvp-1";

    private String spawnRegion = "spawn";

    private long transferTimeoutMs = 3500L;

    private String guiTitle = "&8Kanały";
    private int guiRows = 4;

    private int startChannelSlot = 10;

    private List<String> channels = new ArrayList<>(List.of(
        "boxpvp-1", "boxpvp-2", "boxpvp-3", "boxpvp-4", "boxpvp-5", "boxpvp-6", "boxpvp-7"
    ));

    private SectorGuiItemConfig onlineItem = SectorGuiItemConfig.onlineDefault();
    private SectorGuiItemConfig offlineItem = SectorGuiItemConfig.offlineDefault();
    private SectorGuiItemConfig currentItem = SectorGuiItemConfig.currentDefault();

    private String alreadyHere = "&#FF0000✘ &8Ξ &cJestes juz na tym sektorze!";
    private String offline = "&#FF0000✘ &8Ξ &cTen sektor jest offline!";
    private String transferring = "&#00FF3B✔ &8Ξ &#4EFA76Laczenie z sektorem &f{sector}&a...";
    private String transferFailed = "&#FF0000✘ &8Ξ &cNie udalo sie polaczyc z sektorem.";
    private String onlySpawn = "&#FF0000✘ &8Ξ &cKanal zmienisz tylko na spawnie!";

    private List<String> uniqueMaterials = new ArrayList<>(List.of(
        "PLAYER_HEAD", "NETHER_STAR", "TOTEM_OF_UNDYING"
    ));

    @Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
    public static class SectorGuiItemConfig extends OkaeriConfig {
        private String material = "MINECART";
        private int amount = 1;
        private String name = "&7Kanał: &f{SECTORNAME}";
        private List<String> lore = List.of();

        public static SectorGuiItemConfig onlineDefault() {
            SectorGuiItemConfig item = new SectorGuiItemConfig();
            item.material = "MINECART";
            item.amount = 1;
            item.name = "&#2BFF55⚐ &7Kanał: &#2BFF55{SECTORNAME}";
            item.lore = List.of(
                "",
                "&#2BFF55Informacje o serwerze:",
                " &8× &7Graczy online: &f{ONLINE}",
                " &8× &7Stabilość serwera: &f{TPS}",
                " &8× &7Obciążenie: &f{LOAD}%",
                "&f",
                "&#00FF2D⏭ &#2BFF55Kliknij, aby połączyć z tym kanałem"
            );
            return item;
        }

        public static SectorGuiItemConfig offlineDefault() {
            SectorGuiItemConfig item = new SectorGuiItemConfig();
            item.material = "TNT_MINECART";
            item.amount = 1;
            item.name = "&#FF2B2B⚐ &7Kanał: &#FF2B2B{SECTORNAME}";
            item.lore = List.of(
                "",
                "&#FF2B2BInformacje o serwerze:",
                " &8× &7Graczy online: &f-",
                " &8× &7Dostępny: &c✘",
                "&f",
                "&#FF0000⏭ &#FF2B2BKliknij, aby połączyć z tym kanałem"
            );
            return item;
        }

        public static SectorGuiItemConfig currentDefault() {
            SectorGuiItemConfig item = new SectorGuiItemConfig();
            item.material = "CHEST_MINECART";
            item.amount = 1;
            item.name = "&#FFD12B⚐ &7Kanał: &#FFD12B{SECTORNAME}";
            item.lore = List.of(
                "",
                "&#FFD12BInformacje o serwerze:",
                " &8× &7Graczy online: &f{ONLINE}",
                " &8× &7Stabilość serwera: &f{TPS}",
                " &8× &7Obciążenie: &f{LOAD}%",
                "&f",
                "&#FF8300⏭ &#FFD12BJesteś już połączony z tym kanałem"
            );
            return item;
        }

        public String getMaterial() {
            return material == null || material.isBlank() ? "MINECART" : material;
        }

        public int getAmount() {
            return Math.max(1, Math.min(64, amount));
        }

        public String getName() {
            return name == null ? "" : name;
        }

        public List<String> getLore() {
            return lore == null ? List.of() : lore;
        }
    }

    public String getCurrentSector() {
        return currentSector == null || currentSector.isBlank() ? "boxpvp-1" : currentSector;
    }

    public String getSpawnRegion() {
        return spawnRegion;
    }

    public long getTransferTimeoutMs() {
        return Math.max(1000L, transferTimeoutMs);
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiRows() {
        return Math.max(1, Math.min(6, guiRows));
    }

    public int getStartChannelSlot() {
        return Math.max(0, startChannelSlot);
    }

    public List<String> getChannels() {
        return channels == null ? List.of() : channels;
    }

    public SectorGuiItemConfig getOnlineItem() {
        return onlineItem == null ? SectorGuiItemConfig.onlineDefault() : onlineItem;
    }

    public SectorGuiItemConfig getOfflineItem() {
        return offlineItem == null ? SectorGuiItemConfig.offlineDefault() : offlineItem;
    }

    public SectorGuiItemConfig getCurrentItem() {
        return currentItem == null ? SectorGuiItemConfig.currentDefault() : currentItem;
    }

    public String getAlreadyHere() {
        return alreadyHere;
    }

    public String getOffline() {
        return offline;
    }

    public String getTransferring() {
        return transferring;
    }

    public String getTransferFailed() {
        return transferFailed;
    }

    public String getOnlySpawn() {
        return onlySpawn;
    }

    public List<String> getUniqueMaterials() {
        return uniqueMaterials;
    }
}
