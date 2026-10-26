package dev.grzybalson.config.configs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClanUpgradePack {

    static final int VERSION = 1;

    private static final int[] STAGE_SLOTS = {
        10, 19, 28, 37, 38, 39, 30, 21, 12, 13, 14, 23, 32, 41, 42, 43, 34, 25, 16
    };

    private ClanUpgradePack() {
    }

    static ClanConfig.ClanUpgradesSettings createUpgrades() {
        ClanConfig.ClanUpgradesSettings settings = new ClanConfig.ClanUpgradesSettings();
        settings.setEnabled(true);
        settings.setGuiTitle("&8Ulepszenia klanu");
        settings.setStagesGuiTitle("&8Poziomy ulepszenia");
        settings.setCurrencyName("&dMoneta klanowa");
        settings.setGuiRows(6);
        settings.setBackSlot(49);
        settings.setBackName("&cPowrót");
        settings.setUpgradeInfo(infoTemplate());
        settings.setUpgradeItem(itemTemplate());

        Map<String, ClanConfig.UpgradeCategory> categories = new LinkedHashMap<>();
        categories.put("DROP_SHARD", category(
            "&bOdłamki z zabójstw",
            "IRON_SWORD",
            20,
            "&7Zwiększa drop odłamków z zabójstwa o &f{CHANCE}%",
            shardStages()
        ));
        categories.put("RANKING", category(
            "&6Większy ranking z zabójstw",
            "SHIELD",
            22,
            "&7Zwiększa ranking z zabójstw o &fx{AMPLIFIER}",
            multiplierStages()
        ));
        categories.put("BOOST_DROP", category(
            "&6Większy drop z generatorów",
            "GOLDEN_PICKAXE",
            24,
            "&7Większy drop z generatorów o &fx{AMPLIFIER}",
            multiplierStages()
        ));
        settings.setCategories(categories);
        return settings;
    }

    static void apply(ClanConfig config) {
        config.setStartingElo(1000);
        config.setMinTagLength(3);
        config.setMaxTagLength(6);
        config.setMaxMembers(100);
        config.setMaxDeputies(2);
        config.setCreationCost(1.0D);
        config.setPointsMultiplier(1.0D);
        config.setPointsPercent(0.026D);
        config.setMinusMultiplier(1.15D);
        config.setTurboPointsMulti(1.5D);
        config.setPanelAccess(true);
        config.setClansEnabled(true);
        config.setClanDisableMessage("&cKlany są wyłączone do godziny &419:30!");
        config.setClanChatPrefix("!");
        config.setClanChatFormat("&2🎣 &8(&f{PLAYER} &8-> &b{CLAN}&8) &8| &a{MESSAGE}");
        config.setCreateClanError("&4☠ &8| &cNie można aktualnie założyć klanu, Spróbuj ponownie później!");
        config.setClanCreateBroadcast("&f⚔ &8| &fGracz &#0F8E00{PLAYER} &fstworzył klan o nazwie &8[&#0F8E00{CLAN}&8]");
        config.setClanDeleteBroadcast("&f⚔ &8| &fGracz &#8E0000{PLAYER} &fusunął klan o nazwie &8[&#8E0000{CLAN}&8]");
        config.setUserJoinClanBroadcast("&f⚔ &8| &fGracz &#0F8E00{PLAYER} &fdołączył do klanu o nazwie &8[&#0F8E00{CLAN}&8]");
        config.setClanInfoLayout(new ArrayList<>(List.of(
            "&7",
            "      &8»  &f⚔ &8| &8[&#FF0003{CLAN}&8] &8| &f⚔  &8«",
            "&7",
            "    &f★ &8| &#FF0003&lɪɴꜰᴏʀᴍᴀᴄᴊᴇ &8| &f★",
            "&7",
            "  &8▶ &7Założyciel: &f{LEADER}",
            "  &8▶ &7Zastępcy: &f{DEPUTIES}",
            "  &8▶ &7Członkowie: &f{MEMBERS}",
            "&7",
            "    &f❖ &8| &#FF0003&lꜱᴛᴀᴛʏꜱᴛʏᴋɪ &8| &f❖",
            "&7",
            "  &8▶ &7Punkty: &f{POINTS}",
            "  &8▶ &7Zabójstwa: &f{KILLS}",
            "  &8▶ &7Śmierci: &f{DEATHS}",
            "&7"
        )));
        config.setClanHelpMessage(new ArrayList<>(List.of(
            "&7",
            "            &8»  &f⚔ &8| &#FF0003&lᴋʟᴀɴʏ &8| &f⚔  &8«",
            "&7",
            " &8▶ &8/&fklan &#FF3D3D&nstworz&r &f<tag> &8- &7Tworzy klan",
            " &8▶ &8/&fklan &#FF3D3D&nusun&r &8- &7Usuwa klan",
            " &8▶ &8/&fklan &#FF3D3D&nopusc&r &8- &7Opuszcza aktualny klan",
            " &8▶ &8/&fklan &#FF3D3D&ndolacz&r &f<tag> &8- &7Dołącza do podanego klanu",
            " &8▶ &8/&fklan &#FF3D3D&nwyrzuc&r &f<nick> &8- &7Wyrzuca gracza z klanu",
            " &8▶ &8/&fklan &#FF3D3D&nzapros&r &f<nick> &8- &7Zaprasza gracza do klanu",
            " &8▶ &8/&fklan &#FF3D3D&nlider&r &f<nick> &8- &7Ustawia nowego lidera klanu",
            " &8▶ &8/&fklan &#FF3D3D&nzastepca&r &f<nick> &8- &7Dodaje nowego zastępce do klanu",
            " &8▶ &8/&fklan &#FF3D3D&ninfo&r &f<tag> &8- &7Podaje informacje o klanie",
            " &8▶ &8/&fklan &#FF3D3D&nulepsz&r &8- &7Otwiera ulepszenia klanu",
            "&7"
        )));
        config.setClanCoinDisplay("&x&B&1&B&0&4&5&lᴍ&x&B&5&B&2&5&2&lᴏ&x&B&8&B&4&5&F&lɴ&x&B&C&B&6&6&C&lᴇ&x&B&F&B&7&7&8&lᴛ&x&C&3&B&9&8&5&lᴀ &x&C&A&B&D&9&F&lᴋ&x&C&6&B&B&9&0&lʟ&x&C&2&B&9&8&1&lᴀ&x&B&E&B&7&7&2&lɴ&x&B&9&B&4&6&3&lᴏ&x&B&5&B&2&5&4&lᴡ&x&B&1&B&0&4&5&lᴀ");
        config.setUpgrades(createUpgrades());
        config.setHypestarPackVersion(VERSION);
    }

    private static ClanConfig.UpgradeGuiItem infoTemplate() {
        ClanConfig.UpgradeGuiItem item = new ClanConfig.UpgradeGuiItem();
        item.setMaterial("LIGHT_GRAY_DYE");
        item.setDisplay("{NAME}");
        item.setLore(List.of(
            "",
            " &8× &7W tej kategorii są dostępne &f{UPGRADES}&7 ulepszenia.",
            "",
            "&fKliknij, aby otworzyć!"
        ));
        return item;
    }

    private static ClanConfig.UpgradeGuiItem itemTemplate() {
        ClanConfig.UpgradeGuiItem item = new ClanConfig.UpgradeGuiItem();
        item.setMaterial("LIGHT_GRAY_DYE");
        item.setDisplay("{NAME} &f#{STAGE}");
        item.setLore(List.of(
            "",
            " &8× &7Cena: &fx{PRICE} {SHARD}",
            " &8× &f{DESCRIPTION}",
            "",
            "&fKliknij, aby ulepszyć"
        ));
        return item;
    }

    private static ClanConfig.UpgradeCategory category(String name, String material, int slot, String description,
                                                       List<ClanConfig.UpgradeStage> stages) {
        ClanConfig.UpgradeCategory category = new ClanConfig.UpgradeCategory();
        category.setName(name);
        category.setMaterial(material);
        category.setSlot(slot);
        category.setDescription(description);
        category.setStages(stages);
        return category;
    }

    private static List<ClanConfig.UpgradeStage> shardStages() {
        List<ClanConfig.UpgradeStage> stages = new ArrayList<>();
        for (int i = 0; i < STAGE_SLOTS.length; i++) {
            int stage = i + 1;
            stages.add(stage(stage, STAGE_SLOTS[i], 50 * stage, 1.0D, 0.5D * stage));
        }
        return stages;
    }

    private static List<ClanConfig.UpgradeStage> multiplierStages() {
        List<ClanConfig.UpgradeStage> stages = new ArrayList<>();
        for (int i = 0; i < STAGE_SLOTS.length; i++) {
            int stage = i + 1;
            stages.add(stage(stage, STAGE_SLOTS[i], 50 * stage, 1.1D + (i * 0.05D), 100.0D));
        }
        return stages;
    }

    private static ClanConfig.UpgradeStage stage(int stage, int slot, int price, double amplifier, double chance) {
        ClanConfig.UpgradeStage upgradeStage = new ClanConfig.UpgradeStage();
        upgradeStage.setStage(stage);
        upgradeStage.setSlot(slot);
        upgradeStage.setPrice(price);
        upgradeStage.setAmplifier(amplifier);
        upgradeStage.setChance(chance);
        return upgradeStage;
    }
}
