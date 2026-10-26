package dev.grzybalson.config;

import dev.grzybalson.utils.ColorUtils;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HollyDataMigrator {
    private static final Set<String> SHOP_MULTI_LINE_LORE_PATHS = Set.of(
        "price-lore", "balance-lore", "can-afford-lore", "cannot-afford-lore",
        "time-price-lore", "playtime-lore", "not-enough-time-lore",
        "kills-price-lore", "player-kills-lore", "not-enough-kills-lore",
        "wallet-price-lore", "wallet-balance-lore", "not-enough-wallet-lore",
        "sell-price-lore", "required-amount-lore", "player-has-lore",
        "can-sell-lore", "cannot-sell-lore",
        "disabled-item-lore", "disabled-sell-item-lore"
    );

    private static final String[] LEGACY_ADDON_FOLDERS = {
        "anarchiagg", "daily", "autocommands", "enderchest",
        "afk", "automessages", "clans", "clearlag", "combat", "commands", "economy",
        "kit", "market", "messages", "shop", "sounds", "customhit", "end",
        "hearts", "rtp", "codes", "creatormode", "backup", "logi", "check", "chat",
        "bossbar", "achievement", "ranks", "warps", "antilag", "iplimit"
    };

    private HollyDataMigrator() {}

    public static void migrate(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File addonsFolder = HollyDataPaths.getAddonsFolder(plugin);
        File addonsConfig = HollyDataPaths.getRootFile(plugin, "addons.yml");
        if (!addonsConfig.exists()) {
            try {
                plugin.saveResource("addons.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String folderName : LEGACY_ADDON_FOLDERS) {
            File legacy = new File(dataFolder, folderName);
            if (!legacy.exists()) {
                continue;
            }
            File target = new File(addonsFolder, folderName);
            mergeMove(plugin, legacy, target);
        }

        moveSingleFile(plugin, new File(dataFolder, "license/config.yml"), HollyDataPaths.getRootFile(plugin, "license.yml"));
        moveSingleFile(plugin, new File(dataFolder, "database/config.yml"), HollyDataPaths.getRootFile(plugin, "database.yml"));

        migrateLegacyAddonGuiFolder(plugin, addonsFolder, dataFolder);
        migrateRootAddonsConfig(plugin, addonsFolder);
        migrateLegacyRewardGui(plugin, dataFolder);
        normalizeRootGuiFolder(plugin, dataFolder);
        migrateAddonConfigNames(plugin, addonsFolder);
        migrateMessageFormats(plugin, dataFolder);
    }

    private static void migrateLegacyAddonGuiFolder(JavaPlugin plugin, File addonsFolder, File dataFolder) {
        File legacyFolder = new File(addonsFolder, "guis");
        if (!legacyFolder.isDirectory()) {
            return;
        }

        File guiFolder = new File(dataFolder, "guis");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }

        migrateGuiYaml(plugin, new File(legacyFolder, "config.yml"), new File(guiFolder, "settings.yml"));
        migrateGuiYaml(plugin, new File(legacyFolder, "settings.yml"), new File(guiFolder, "settings.yml"));
        migrateGuiYaml(plugin, new File(legacyFolder, "templates.yml"), new File(guiFolder, "template.yml"));
        migrateGuiYaml(plugin, new File(legacyFolder, "template.yml"), new File(guiFolder, "template.yml"));
        migrateGuiYaml(plugin, new File(legacyFolder, "event.yml"), new File(guiFolder, "event.yml"));
        migrateGuiYaml(plugin, new File(legacyFolder, "nagroda.yml"), new File(guiFolder, "nagroda.yml"));

        archiveRemainingGuiFiles(plugin, legacyFolder);
        deleteIfEmpty(legacyFolder);
    }

    private static void migrateGuiYaml(JavaPlugin plugin, File legacy, File target) {
        if (!legacy.isFile()) {
            return;
        }
        if (target.exists()) {
            mergeYamlOverride(plugin, legacy, target, "guis");
        } else {
            moveSingleFile(plugin, legacy, target);
        }
    }

    private static void archiveRemainingGuiFiles(JavaPlugin plugin, File folder) {
        File[] children = folder.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                archiveRemainingGuiFiles(plugin, child);
                deleteIfEmpty(child);
                continue;
            }
            try {
                archiveLegacyFile(plugin, child, "guis-unused");
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udało się usunąć starego pliku GUI " + child.getPath() + ": " + exception.getMessage());
            }
        }
    }

    private static void migrateRootAddonsConfig(JavaPlugin plugin, File addonsFolder) {
        File legacy = new File(addonsFolder, "config.yml");
        if (!legacy.isFile()) {
            return;
        }

        File target = HollyDataPaths.getRootFile(plugin, "addons.yml");
        mergeYamlOverride(plugin, legacy, target, "addons");
    }

    private static void migrateLegacyRewardGui(JavaPlugin plugin, File dataFolder) {
        File legacy = new File(dataFolder, "guis/nagroda.yml");
        if (!legacy.isFile()) {
            return;
        }

        File target = new File(HollyDataPaths.getAddonFolder(plugin, "discordreward"), "discordreward.yml");
        if (!target.exists()) {
            try {
                plugin.saveResource("addons/discordreward/discordreward.yml", false);
            } catch (IllegalArgumentException ignored) {
            }
        }

        YamlConfiguration oldYaml = YamlConfiguration.loadConfiguration(legacy);
        YamlConfiguration targetYaml = YamlConfiguration.loadConfiguration(target);
        copyIfPresent(oldYaml, "title", targetYaml, "gui.title");
        if (oldYaml.contains("rows")) {
            targetYaml.set("gui.size", Math.max(1, Math.min(6, oldYaml.getInt("rows", 5))) * 9);
        }
        copyIfPresent(oldYaml, "template", targetYaml, "gui.template");
        copyIfPresent(oldYaml, "filler.enabled", targetYaml, "gui.filler.enabled");
        copyIfPresent(oldYaml, "filler.material", targetYaml, "gui.filler.material");
        copyIfPresent(oldYaml, "filler.name", targetYaml, "gui.filler.name");
        copyIfPresent(oldYaml, "status.available", targetYaml, "status.available");
        copyIfPresent(oldYaml, "status.claimed", targetYaml, "status.claimed");
        copyIfPresent(oldYaml, "items.discord.slot", targetYaml, "reward.slot");
        copyIfPresent(oldYaml, "items.discord.displayName", targetYaml, "reward.displayName");
        copyIfPresent(oldYaml, "items.discord.reward", targetYaml, "reward.reward");
        copyIfPresent(oldYaml, "items.discord.link", targetYaml, "reward.link");
        copyIfPresent(oldYaml, "items.discord.clickable", targetYaml, "reward.clickable");
        copyIfPresent(oldYaml, "items.discord.head", targetYaml, "reward.head");
        copyIfPresent(oldYaml, "items.discord.commands", targetYaml, "reward.commands");
        copyIfPresent(oldYaml, "items.discord.itemStack", targetYaml, "reward.itemStack");

        try {
            targetYaml.save(target);
            archiveLegacyFile(plugin, legacy, "guis");
            deleteEmptyParents(legacy.getParentFile(), plugin.getDataFolder());
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się przenieść guis/nagroda.yml do discordreward.yml: " + exception.getMessage());
        }
    }

    private static void normalizeRootGuiFolder(JavaPlugin plugin, File dataFolder) {
        File guiFolder = new File(dataFolder, "guis");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }

        migrateGuiYaml(plugin, new File(guiFolder, "config.yml"), new File(guiFolder, "settings.yml"));
        migrateGuiYaml(plugin, new File(guiFolder, "templates.yml"), new File(guiFolder, "template.yml"));

        Set<String> allowed = Set.of("template.yml", "settings.yml", "event.yml");
        File[] children = guiFolder.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isFile() && !allowed.contains(child.getName().toLowerCase())) {
                try {
                    archiveLegacyFile(plugin, child, "guis-unused");
                } catch (IOException exception) {
                    plugin.getLogger().warning("Nie udało się usunąć starego pliku guis/" + child.getName() + ": " + exception.getMessage());
                }
            }
        }
    }

    private static void copyIfPresent(YamlConfiguration source, String sourcePath,
                                      YamlConfiguration target, String targetPath) {
        if (source.contains(sourcePath)) {
            target.set(targetPath, source.get(sourcePath));
        }
    }

    private static void mergeYamlOverride(JavaPlugin plugin, File legacy, File target, String category) {
        YamlConfiguration oldYaml = YamlConfiguration.loadConfiguration(legacy);
        YamlConfiguration targetYaml = YamlConfiguration.loadConfiguration(target);
        for (Map.Entry<String, Object> entry : oldYaml.getValues(true).entrySet()) {
            if (entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection) {
                continue;
            }
            targetYaml.set(entry.getKey(), entry.getValue());
        }
        try {
            targetYaml.save(target);
            archiveLegacyFile(plugin, legacy, category);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się przenieść starego " + legacy.getPath() + ": " + exception.getMessage());
        }
    }

    private static void archiveLegacyFile(JavaPlugin plugin, File source, String category) throws IOException {
        File backupFolder = new File(plugin.getDataFolder(), "backups/migrations/" + category);
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
        String relative;
        try {
            relative = plugin.getDataFolder().toPath().relativize(source.toPath()).toString();
        } catch (IllegalArgumentException ignored) {
            relative = source.getName();
        }
        String safeName = relative.replace('\\', '-').replace('/', '-').replace(':', '-');
        File backup = new File(backupFolder, safeName + ".bak");
        Files.move(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void migrateAddonConfigNames(JavaPlugin plugin, File addonsFolder) {
        migrateAddonConfigDirectory(plugin, addonsFolder, true);
    }

    private static void migrateAddonConfigDirectory(JavaPlugin plugin, File directory, boolean root) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        if (!root && !directory.getName().equalsIgnoreCase("commands")) {
            File legacy = new File(directory, "config.yml");
            if (legacy.isFile()) {
                String addonName = directory.getName();
                boolean hasSeparateDataFile = addonName.equalsIgnoreCase("cases")
                    || addonName.equalsIgnoreCase("warps");
                File target = new File(directory, addonName + (hasSeparateDataFile ? "-settings.yml" : ".yml"));

                if (target.exists()) {
                    mergeLegacyYaml(plugin, legacy, target);
                } else {
                    moveSingleFile(plugin, legacy, target);
                }
                if (!legacy.exists()) {
                    plugin.getLogger().info("Zmieniono nazwę configu addonu " + addonName + ": " + target.getName());
                }
            }
        }

        for (File child : children) {
            if (child.isDirectory()) {
                migrateAddonConfigDirectory(plugin, child, false);
            }
        }
    }

    private static void mergeLegacyYaml(JavaPlugin plugin, File legacy, File target) {
        YamlConfiguration oldYaml = new YamlConfiguration();
        YamlConfiguration targetYaml = new YamlConfiguration();
        oldYaml.options().parseComments(true);
        targetYaml.options().parseComments(true);
        try {
            oldYaml.load(legacy);
            targetYaml.load(target);
            for (Map.Entry<String, Object> entry : oldYaml.getValues(true).entrySet()) {
                if (entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection) {
                    continue;
                }
                targetYaml.set(entry.getKey(), entry.getValue());
            }
            targetYaml.save(target);

            archiveLegacyFile(plugin, legacy, "addons");
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Nie udało się połączyć starego configu " + legacy.getPath()
                + " z " + target.getPath() + ": " + exception.getMessage());
        }
    }

    private static void migrateMessageFormats(JavaPlugin plugin, File root) {
        List<File> yamlFiles = new ArrayList<>();
        collectYamlFiles(root, yamlFiles);
        int changedFiles = 0;

        for (File file : yamlFiles) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().parseComments(true);
            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException exception) {
                plugin.getLogger().warning("Nie udało się odczytać wiadomości z " + file.getPath() + ": " + exception.getMessage());
                continue;
            }

            boolean changed = false;
            Map<String, Object> values = new LinkedHashMap<>(yaml.getValues(true));
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String text) {
                    String normalized = ColorUtils.normalizeLegacyMessagePrefix(text);
                    if (isShopLorePath(file, entry.getKey())) {
                        yaml.set(entry.getKey(), List.of(normalized));
                        changed = true;
                    } else if (!normalized.equals(text)) {
                        yaml.set(entry.getKey(), normalized);
                        changed = true;
                    }
                    continue;
                }
                if (!(value instanceof List<?> list)) {
                    continue;
                }

                List<Object> migrated = new ArrayList<>(list.size());
                boolean listChanged = false;
                for (Object element : list) {
                    if (element instanceof String text) {
                        String normalized = ColorUtils.normalizeLegacyMessagePrefix(text);
                        migrated.add(normalized);
                        listChanged |= !normalized.equals(text);
                    } else {
                        migrated.add(element);
                    }
                }
                if (listChanged) {
                    yaml.set(entry.getKey(), migrated);
                    changed = true;
                }
            }

            changed |= migrateKnownPresentationDefaults(file, yaml);

            if (!changed) {
                continue;
            }
            try {
                createMigrationBackup(plugin, file);
                yaml.save(file);
                changedFiles++;
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udało się zaktualizować wiadomości w " + file.getPath() + ": " + exception.getMessage());
            }
        }

        if (changedFiles > 0) {
            plugin.getLogger().info("Zaktualizowano stary format wiadomości w " + changedFiles + " plikach YAML.");
        }
    }


    private static boolean migrateKnownPresentationDefaults(File file, YamlConfiguration yaml) {
        String path = file.getPath().replace('\\', '/').toLowerCase();
        boolean changed = false;

        if (path.endsWith("/addons/shop/shop.yml")) {
            changed |= replaceKnownVariants(yaml, "price-lore",
                List.of("", "&8▸ &fCena zakupu:", "&8  &#FFDD57{price}$"),
                List.of("&7 Cena: &e{price}$"),
                List.of("&8▸ &fCena: &#FFDD57{price}$"));
            changed |= replaceKnownVariants(yaml, "balance-lore",
                List.of("&8▸ &fStan Twojego konta:", "&8  &#00E0FF{balance}$"),
                List.of("&7 Twoje monety: &e{balance}$"),
                List.of("&8▸ &fTwoje monety: &#FFDD57{balance}$"));
            changed |= replaceKnownVariants(yaml, "can-afford-lore",
                List.of("", "&#4EFA76✔ Stać Cię na ten przedmiot", "&fKliknij, aby przejść do zakupu"),
                List.of("&a Kliknij, aby kupić!"),
                List.of("", "&#4EFA76✔ Kliknij, aby kupić"));
            changed |= replaceKnownVariants(yaml, "cannot-afford-lore",
                List.of("", "&#FF5C5C✘ Brakuje Ci środków", "&7Zdobądź więcej monet i spróbuj ponownie"),
                List.of("&c Brak środków!"),
                List.of("", "&#FF5C5C✘ Brakuje Ci środków"));
            changed |= replaceKnownVariants(yaml, "time-price-lore",
                List.of("", "&8▸ &fWymagany czas gry:", "&8  &#00E0FF{time}"),
                List.of("&7 Wymagany czas: &b{time}"),
                List.of("&8▸ &fWymagany czas: &#00E0FF{time}"));
            changed |= replaceKnownVariants(yaml, "playtime-lore",
                List.of("&8▸ &fTwój aktualny czas:", "&8  &#B388FF{time}"),
                List.of("&7 Twój czas gry: &b{time}"),
                List.of("&8▸ &fTwój czas gry: &#00E0FF{time}"));
            changed |= replaceKnownVariants(yaml, "not-enough-time-lore",
                List.of("", "&#FF5C5C✘ Masz za mało czasu gry"),
                List.of("&c✘ Za mało czasu gry!"));
            changed |= replaceKnownVariants(yaml, "kills-price-lore",
                List.of("", "&8▸ &fWymagane zabójstwa:", "&8  &#FF5C5C{kills}"),
                List.of("&7 Wymagane zabójstwa: &c{kills}"),
                List.of("&8▸ &fWymagane zabójstwa: &#FF5C5C{kills}"));
            changed |= replaceKnownVariants(yaml, "player-kills-lore",
                List.of("&8▸ &fTwoje zabójstwa:", "&8  &#FFB800{kills}"),
                List.of("&7⚔ Twoje zabójstwa: &c{kills}"),
                List.of("&8▸ &fTwoje zabójstwa: &#FF5C5C{kills}"));
            changed |= replaceKnownVariants(yaml, "not-enough-kills-lore",
                List.of("", "&#FF5C5C✘ Masz za mało zabójstw"),
                List.of("&c✘ Za mało zabójstw!"));
            changed |= replaceKnownVariants(yaml, "wallet-price-lore",
                List.of("", "&8▸ &fCena zakupu:", "&8  &#FFDD57{price} wPLN"),
                List.of("&7Cena: &e{price} wPLN"),
                List.of("&8▸ &fCena: &#FFDD57{price} wPLN"));
            changed |= replaceKnownVariants(yaml, "wallet-balance-lore",
                List.of("&8▸ &fStan portfela:", "&8  &#00E0FF{balance} wPLN"),
                List.of("&7Twoje wPLN: &e{balance}"),
                List.of("&8▸ &fTwoje wPLN: &#FFDD57{balance}"));
            changed |= replaceKnownVariants(yaml, "not-enough-wallet-lore",
                List.of("", "&#FF5C5C✘ Masz za mało wPLN"),
                List.of("&cNie stać Cię!"));
            changed |= replaceKnownVariants(yaml, "sell-price-lore",
                List.of("", "&8▸ &fOtrzymasz za sprzedaż:", "&8  &#4EFA76{price}$"),
                List.of("&7 Otrzymasz: &a{price}$"),
                List.of("&8▸ &fOtrzymasz: &#4EFA76{price}$"));
            changed |= replaceKnownVariants(yaml, "required-amount-lore",
                List.of("&8▸ &fWymagana liczba:", "&8  &#FFDD57{amount} szt."),
                List.of("&7 Wymagana ilość: &e{amount}"),
                List.of("&8▸ &fWymagana ilość: &#FFDD57{amount} szt."));
            changed |= replaceKnownVariants(yaml, "player-has-lore",
                List.of("&8▸ &fPosiadasz obecnie:", "&8  &#00E0FF{amount} szt."),
                List.of("&7 Posiadasz: &e{amount}"),
                List.of("&8▸ &fPosiadasz: &#00E0FF{amount} szt."));
            changed |= replaceKnownVariants(yaml, "can-sell-lore",
                List.of("", "&#4EFA76✔ Kliknij, aby sprzedać"),
                List.of("&a✔ Kliknij, aby sprzedać!"));
            changed |= replaceKnownVariants(yaml, "cannot-sell-lore",
                List.of("", "&#FF5C5C✘ Nie masz wymaganych przedmiotów"),
                List.of("&c✘ Brak przedmiotów!"));

            changed |= replaceKnownVariants(yaml, "purchase-confirm-title", "&8✦ Potwierdzenie zakupu",
                "&8KUPNO PRZEDMIOTOW");
            changed |= replaceKnownVariants(yaml, "purchase-preview-name", "&#00E0FF&l{ITEM}", "&f{ITEM}");
            changed |= replaceKnownVariants(yaml, "purchase-preview-lore",
                List.of("", "&8▸ &fWybrana ilość: &#00E0FF{AMOUNT} szt.", "&8▸ &fŁączny koszt: &#FFDD57{PRICE}$", ""),
                List.of("", "&8» &7Aktualna ilość: &f{AMOUNT} szt.", "&8» &7Cena: &a${PRICE}", ""));
            changed |= replaceKnownVariants(yaml, "purchase-confirm-name", "&#4EFA76&lZATWIERDŹ ZAKUP",
                "&a&lZATWIERDZ KUPNO");
            changed |= replaceKnownVariants(yaml, "purchase-confirm-lore",
                List.of("", "&8▸ &fLiczba produktów: &#00E0FF{AMOUNT} szt.", "&8▸ &fKoszt zakupu: &#FFDD57{PRICE}$", "", "&#4EFA76✔ Kliknij, aby zatwierdzić transakcję"),
                List.of("", "&8» &7Ilość produktów: &fx{AMOUNT}", "&8» &7Koszt zakupu: &a${PRICE}", "", "&aKLIKNIJ, ABY ZATWIERDZIC TRANSAKCJE!"));
            changed |= replaceKnownVariants(yaml, "purchase-cancel-name", "&#FF5C5C&lANULUJ ZAKUP", "&c&lAnuluj");
            changed |= replaceKnownVariants(yaml, "purchase-cancel-lore",
                List.of("", "&7Anuluj zakup i wróć do sklepu.", "", "&#FF5C5C✘ Kliknij, aby anulować"),
                List.of("", "&7Kliknij, aby wrócić do sklepu."));
        }

        if (path.endsWith("/addons/commands/odbierz.yml")) {
            changed |= replaceKnownVariants(yaml, "gui.item.lore",
                List.of("", "&8▸ &fNazwa przedmiotu: &#00E0FF{NAME}", "&8▸ &fLiczba przedmiotów: &#FFB800{AMOUNT} szt.", "", "&#4EFA76✔ Kliknij, aby odebrać te przedmioty"),
                List.of("", "&8▸ &fPrzedmiot: &#00E0FF{NAME}", "&8▸ &fIlość: &#FFB800{AMOUNT} szt.", "", "&#4EFA76✔ Kliknij, aby odebrać"));
            changed |= replaceKnownVariants(yaml, "gui.claim-all.lore",
                List.of("", "&8▸ &fPrzedmioty do odebrania: &#00E0FF{PACKAGES}", "&8▸ &fŁącznie przedmiotów: &#FFB800{ITEMS} szt.", "", "&#4EFA76✔ Kliknij, aby odebrać wszystko"),
                List.of("", "&7Odbierzesz wszystkie oczekujące przedmioty", "&7bez klikania każdego z osobna.", "", "&#4EFA76✔ Kliknij, aby odebrać wszystko"));
            changed |= replaceKnownVariants(yaml, "gui.empty.lore",
                List.of("", "&7Nie masz obecnie żadnych przedmiotów", "&7oczekujących na odebranie."),
                List.of("", "&7Nie masz teraz żadnych przedmiotów", "&7oczekujących do odebrania."));
        }

        if (path.endsWith("/addons/worldborder/worldborder.yml")) {
            changed |= replaceKnownTextContaining(yaml, "bossbar.title", "border", "&#FF5C5C⚠ &fGranica mapy &8• &7pozostało &#FFDD57{distance}m");
            changed |= replaceKnownTextContaining(yaml, "messages.blocked-action", "worldborder", "&#FF0000✘ &8Ξ &cNie możesz tego robić przy granicy mapy.");
            changed |= replaceKnownTextContaining(yaml, "messages.blocked-teleport", "worldborder", "&#FF0000✘ &8Ξ &cNie możesz teleportować się w pobliże granicy mapy.");
            if (!yaml.contains("title.enabled")) {
                yaml.set("title.enabled", true);
                yaml.set("title.title", "&#FF5C5C&lUWAGA!");
                yaml.set("title.subtitle", "&fZbliżasz się do granicy mapy &8• &#FFDD57{distance}m");
                yaml.set("title.fade-in-ticks", 5);
                yaml.set("title.stay-ticks", 35);
                yaml.set("title.fade-out-ticks", 10);
                changed = true;
            }
        }

        if (path.endsWith("/addons/zonebox/zonebox.yml")) {
            changed |= replaceKnownTextContaining(yaml, "messages.blocked", "bloku", "&#FF5C5C✘ &8• &fTego bloku nie możesz tutaj wykopać.");
            changed |= replaceKnownTextContaining(yaml, "messages.place-blocked", "bloku", "&#FF5C5C✘ &8• &#FF5C5CTego bloku nie możesz tutaj postawić.");
            changed |= replaceKnownTextContaining(yaml, "messages.economy-place-blocked", "block", "&#FF5C5C✘ &8• &fBloków z ekonomii nie można stawiać.");
            changed |= replaceKnownTextContaining(yaml, "messages.craft-blocked", "craft", "&#FF5C5C✘ &8• &fCrafting jest wyłączony na tym trybie.");
        }

        return changed;
    }

    private static boolean replaceKnown(YamlConfiguration yaml, String path, Object oldValue, Object newValue) {
        Object current = yaml.get(path);
        if (!java.util.Objects.equals(current, oldValue)) {
            return false;
        }
        yaml.set(path, newValue);
        return true;
    }

    private static boolean replaceKnownVariants(YamlConfiguration yaml, String path, Object newValue, Object... oldValues) {
        Object current = yaml.get(path);
        if (java.util.Objects.equals(current, newValue)) {
            return false;
        }
        for (Object oldValue : oldValues) {
            if (java.util.Objects.equals(current, oldValue)) {
                yaml.set(path, newValue);
                return true;
            }
        }
        return false;
    }

    private static boolean replaceKnownTextContaining(YamlConfiguration yaml, String path, String token, String replacement) {
        String current = yaml.getString(path);
        if (current == null || !current.toLowerCase().contains(token.toLowerCase())) {
            return false;
        }
        if (current.equals(replacement)) {
            return false;
        }
        yaml.set(path, replacement);
        return true;
    }

    private static void createMigrationBackup(JavaPlugin plugin, File file) throws IOException {
        File backupFolder = new File(plugin.getDataFolder(), "backups/migrations/messages");
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
        String relative;
        try {
            relative = plugin.getDataFolder().toPath().relativize(file.toPath()).toString();
        } catch (IllegalArgumentException ignored) {
            relative = file.getName();
        }
        String safeName = relative.replace('\\', '-').replace('/', '-').replace(':', '-');
        File backup = new File(backupFolder, safeName + ".bak");
        if (backup.exists()) {
            return;
        }
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static boolean isShopLorePath(File file, String path) {
        String normalizedPath = file.getPath().replace('\\', '/').toLowerCase();
        return normalizedPath.endsWith("/addons/shop/shop.yml") && SHOP_MULTI_LINE_LORE_PATHS.contains(path);
    }

    private static void collectYamlFiles(File file, List<File> output) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            if (file.getName().toLowerCase().endsWith(".yml")) {
                output.add(file);
            }
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collectYamlFiles(child, output);
        }
    }

    private static void moveSingleFile(JavaPlugin plugin, File source, File target) {
        if (!source.exists() || target.exists()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            deleteEmptyParents(source.getParentFile(), plugin.getDataFolder());
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się przenieść pliku " + source.getPath() + " -> " + target.getPath() + ": " + exception.getMessage());
        }
    }

    private static void mergeMove(JavaPlugin plugin, File source, File target) {
        if (!source.exists()) {
            return;
        }
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdirs();
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    mergeMove(plugin, child, new File(target, child.getName()));
                }
            }
            deleteIfEmpty(source);
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (target.exists()) {
            return;
        }

        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            try {
                Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyException) {
                plugin.getLogger().warning("Nie udało się przenieść pliku " + source.getPath() + " -> " + target.getPath() + ": " + copyException.getMessage());
            }
        }
    }

    private static void deleteIfEmpty(File folder) {
        File[] children = folder.listFiles();
        if (children != null && children.length == 0) {
            folder.delete();
        }
    }

    private static void deleteEmptyParents(File folder, File stopAt) {
        File current = folder;
        while (current != null && !current.equals(stopAt)) {
            File[] children = current.listFiles();
            if (children != null && children.length == 0) {
                current.delete();
                current = current.getParentFile();
                continue;
            }
            break;
        }
    }
}
