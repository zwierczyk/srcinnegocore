package dev.grzybalson.config;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.*;
import dev.grzybalson.config.guis.GuiTemplateConfig;
import dev.grzybalson.config.modules.renderdistance.RenderDistanceConfig;
import dev.grzybalson.internal.HollyIntegrityGuard;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private static final String[] DEFAULT_CONFIG_RESOURCES = {
        "license.yml",
        "database.yml",
        "addons/afk/afk.yml",
        "addons/automessages/automessages.yml",
        "addons/clans/clans.yml",
        "addons/clearlag/clearlag.yml",
        "addons/combat/combat.yml",
        "addons/commands/distributeadmin.yml",
        "addons/commands/sectors.yml",
        "addons/commands/spawn.yml",
        "addons/commands/vanish.yml",
        "addons/commands/restart.yml",
        "addons/commands/discord.yml",
        "addons/commands/timer.yml",
        "addons/commands/serverinfo.yml",
        "addons/commands/render.yml",
        "addons/commands/odbierz.yml",
        "addons/commands/player.yml",
        "addons/economy/economy.yml",
        "addons/economy/wallet.yml",
        "addons/economy/blockseconomy.yml",
        "addons/economy/turbodrop.yml",
        "addons/zonebox/zonebox.yml",
        "addons/antigrief/antigrief.yml",
        "addons/szaman/szaman.yml",
        "addons/patroni/patroni.yml",
        "addons/lockedfeatures/lockedfeatures.yml",
        "addons/antilag/antilag.yml",
        "guis/template.yml",
        "guis/settings.yml",
        "guis/event.yml",
        "addons/dungeon/dungeon.yml",
        "guis/coinflip.yml",
        "guis/zywioly.yml",
        "addons/zywioly/zywioly.yml",
        "guis/trener.yml",
        "addons/kit/kit.yml",
        "addons/market/market.yml",
        "addons/market/filters.yml",
        "addons/messages/messages.yml",
        "addons/messages/ignore.yml",
        "addons/shop/shop.yml",
        "addons/sounds/sounds.yml",
        "addons/customhit/customhit.yml",
        "addons/end/end.yml",
        "addons/hearts/hearts.yml",
        "addons/core/core.yml",
        "addons/rtp/rtp.yml",
        "addons/codes/codes.yml",
        "addons/creatormode/creatormode.yml",
        "addons/backup/backup.yml",
        "addons/logi/logi.yml",
        "addons/check/check.yml",
        "addons/boss/boss.yml",
        "addons/chat/chat.yml",
        "addons/chat/lpc.yml",
        "addons/tablist/tablist.yml",
        "addons/renderdistance/render-distance.yml",
        "addons/armorstars/armorstars.yml",
        "addons/iplimit/iplimit.yml",
        "addons/tops/tops.yml",
        "addons/itemstorage/items.yml",
        "addons/itemshop/itemshop.yml",
        "addons/dropkeys/dropkeys.yml",
        "addons/moderation/moderation.yml"
    };

    private final HollyCore plugin;

    private MainConfig mainConfig;
    private DatabaseConfig databaseConfig;
    private MessagesConfig messagesConfig;
    private SpawnConfig spawnConfig;
    private AutoMessageConfig autoMessageConfig;
    private ShopConfig shopConfig;
    private GUIConfig guiConfig;
    private EconomyConfig economyConfig;
    private IgnoreConfig ignoreConfig;
    private AfkConfig afkConfig;
    private ClearLagConfig clearLagConfig;
    private WalletConfig walletConfig;
    private AntiLagConfig antiLagConfig;
    private AdminConfig adminConfig;
    private VanishConfig vanishConfig;
    private SoundsConfig soundsConfig;
    private ClanConfig clanConfig;
    private CombatConfig combatConfig;
    private RestartConfig restartConfig;
    private KitConfig kitConfig;
    private MarketConfig marketConfig;
    private MarketFiltersConfig marketFiltersConfig;
    private GuiTemplateConfig guiTemplateConfig;
    private RtpConfig rtpConfig;
    private CodeConfig codeConfig;
    private CreatorModeConfig creatorModeConfig;
    private BackupConfig backupConfig;
    private CheckConfig checkConfig;
    private ChatConfig chatConfig;
    private LpcChatConfig lpcChatConfig;
    private LicenseConfig licenseConfig;
    private CustomHitConfig customHitConfig;
    private EndModuleConfig endModuleConfig;
    private HeartsConfig heartsConfig;
    private LogiConfig logiConfig;
    private DiscordConfig discordConfig;
    private PlayerCommandConfig playerCommandConfig;
    private RenderDistanceConfig renderDistanceConfig;
    private ArmorStarsConfig armorStarsConfig;
    private IpLimitConfig ipLimitConfig;

    public ConfigManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void loadAllConfigs() {
        HollyIntegrityGuard.preflight(plugin, "config:load");

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File addonsFolder = HollyDataPaths.getAddonsFolder(plugin);
        File commandsFolder = new File(addonsFolder, "commands");
        if (!commandsFolder.exists()) commandsFolder.mkdirs();
        migrateLegacyCommandsConfig(commandsFolder);
        ensureDefaultConfigResources();

        licenseConfig = eu.okaeri.configs.ConfigManager.create(LicenseConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(HollyDataPaths.getRootFile(plugin, "license.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        licenseConfig.save();

        File afkFolder = new File(addonsFolder, "afk");
        if (!afkFolder.exists()) afkFolder.mkdirs();

        afkConfig = eu.okaeri.configs.ConfigManager.create(AfkConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(afkFolder, "afk.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        afkConfig.save();

        File automessagesFolder = new File(addonsFolder, "automessages");
        if (!automessagesFolder.exists()) automessagesFolder.mkdirs();

        autoMessageConfig = eu.okaeri.configs.ConfigManager.create(AutoMessageConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(automessagesFolder, "automessages.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        autoMessageConfig.save();

        File clansFolder = new File(addonsFolder, "clans");
        if (!clansFolder.exists()) clansFolder.mkdirs();

        clanConfig = eu.okaeri.configs.ConfigManager.create(ClanConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(clansFolder, "clans.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        clanConfig.save();
        clanConfig.applyHypestarPackIfNeeded();

        File clearlagFolder = new File(addonsFolder, "clearlag");
        if (!clearlagFolder.exists()) clearlagFolder.mkdirs();

        clearLagConfig = eu.okaeri.configs.ConfigManager.create(ClearLagConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(clearlagFolder, "clearlag.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        clearLagConfig.save();

        File combatFolder = new File(addonsFolder, "combat");
        if (!combatFolder.exists()) combatFolder.mkdirs();

        combatConfig = eu.okaeri.configs.ConfigManager.create(CombatConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(combatFolder, "combat.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        combatConfig.save();

        adminConfig = eu.okaeri.configs.ConfigManager.create(AdminConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(commandsFolder, "distributeadmin.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        adminConfig.save();

        spawnConfig = eu.okaeri.configs.ConfigManager.create(SpawnConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(commandsFolder, "spawn.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        spawnConfig.save();

        vanishConfig = eu.okaeri.configs.ConfigManager.create(VanishConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(commandsFolder, "vanish.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        vanishConfig.save();

        restartConfig = eu.okaeri.configs.ConfigManager.create(RestartConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(commandsFolder, "restart.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        restartConfig.save();

        discordConfig = eu.okaeri.configs.ConfigManager.create(DiscordConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(commandsFolder, "discord.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        discordConfig.save();

        playerCommandConfig = eu.okaeri.configs.ConfigManager.create(PlayerCommandConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(commandsFolder, "player.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        playerCommandConfig.save();

        databaseConfig = eu.okaeri.configs.ConfigManager.create(DatabaseConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(HollyDataPaths.getRootFile(plugin, "database.yml"));
            it.withRemoveOrphans(true);
            it.saveDefaults();
            it.load(true);
        });
        databaseConfig.save();

        File economyFolder = new File(addonsFolder, "economy");
        if (!economyFolder.exists()) economyFolder.mkdirs();

        economyConfig = eu.okaeri.configs.ConfigManager.create(EconomyConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(economyFolder, "economy.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        economyConfig.save();

        walletConfig = eu.okaeri.configs.ConfigManager.create(WalletConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(economyFolder, "wallet.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        walletConfig.save();


        File antiLagFolder = new File(addonsFolder, "antilag");
        if (!antiLagFolder.exists()) antiLagFolder.mkdirs();

        antiLagConfig = eu.okaeri.configs.ConfigManager.create(AntiLagConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(antiLagFolder, "antilag.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        antiLagConfig.save();

        File guiFolder = new File(dataFolder, "guis");
        if (!guiFolder.exists()) guiFolder.mkdirs();
        migrateLegacyGuiFile(new File(dataFolder, "gui/config.yml"), new File(guiFolder, "settings.yml"));
        migrateLegacyGuiFile(new File(dataFolder, "gui/templates.yml"), new File(guiFolder, "template.yml"));
        migrateLegacyGuiFile(new File(dataFolder, "guis/config.yml"), new File(guiFolder, "settings.yml"));
        migrateLegacyGuiFile(new File(dataFolder, "guis/templates.yml"), new File(guiFolder, "template.yml"));
        migrateLegacyGuiFile(new File(guiFolder, "config.yml"), new File(guiFolder, "settings.yml"));

        guiConfig = eu.okaeri.configs.ConfigManager.create(GUIConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(guiFolder, "settings.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        guiConfig.save();

        guiTemplateConfig = eu.okaeri.configs.ConfigManager.create(GuiTemplateConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(guiFolder, "template.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        guiTemplateConfig.save();

        File kitFolder = new File(addonsFolder, "kit");
        if (!kitFolder.exists()) kitFolder.mkdirs();

        kitConfig = eu.okaeri.configs.ConfigManager.create(KitConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(kitFolder, "kit.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        kitConfig.save();

        File marketFolder = new File(addonsFolder, "market");
        if (!marketFolder.exists()) marketFolder.mkdirs();

        marketConfig = eu.okaeri.configs.ConfigManager.create(MarketConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(marketFolder, "market.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        marketConfig.save();

        marketFiltersConfig = eu.okaeri.configs.ConfigManager.create(MarketFiltersConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(marketFolder, "filters.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        marketFiltersConfig.save();

        File messagesFolder = new File(addonsFolder, "messages");
        if (!messagesFolder.exists()) messagesFolder.mkdirs();

        messagesConfig = eu.okaeri.configs.ConfigManager.create(MessagesConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(messagesFolder, "messages.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        messagesConfig.save();

        ignoreConfig = eu.okaeri.configs.ConfigManager.create(IgnoreConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(messagesFolder, "ignore.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        ignoreConfig.save();

        File shopFolder = new File(addonsFolder, "shop");
        if (!shopFolder.exists()) shopFolder.mkdirs();

        shopConfig = eu.okaeri.configs.ConfigManager.create(ShopConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(shopFolder, "shop.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        shopConfig.save();

        File soundsFolder = new File(addonsFolder, "sounds");
        if (!soundsFolder.exists()) soundsFolder.mkdirs();

        soundsConfig = eu.okaeri.configs.ConfigManager.create(SoundsConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(soundsFolder, "sounds.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        soundsConfig.save();

        File customHitFolder = new File(addonsFolder, "customhit");
        if (!customHitFolder.exists()) customHitFolder.mkdirs();

        customHitConfig = eu.okaeri.configs.ConfigManager.create(CustomHitConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(customHitFolder, "customhit.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        customHitConfig.save();

        File endFolder = new File(addonsFolder, "end");
        if (!endFolder.exists()) endFolder.mkdirs();

        endModuleConfig = eu.okaeri.configs.ConfigManager.create(EndModuleConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(endFolder, "end.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        endModuleConfig.save();

        File heartsFolder = new File(addonsFolder, "hearts");
        if (!heartsFolder.exists()) heartsFolder.mkdirs();

        heartsConfig = eu.okaeri.configs.ConfigManager.create(HeartsConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(heartsFolder, "hearts.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        heartsConfig.save();

        mainConfig = eu.okaeri.configs.ConfigManager.create(MainConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(HollyDataPaths.getAddonFolder(plugin, "core"), "core.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        mainConfig.save();

        File rtpFolder = new File(addonsFolder, "rtp");
        if (!rtpFolder.exists()) rtpFolder.mkdirs();

        rtpConfig = eu.okaeri.configs.ConfigManager.create(RtpConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(rtpFolder, "rtp.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        rtpConfig.save();

        File codesFolder = new File(addonsFolder, "codes");
        if (!codesFolder.exists()) codesFolder.mkdirs();
        File codeConfigFile = new File(codesFolder, "codes.yml");
        repairYamlDocumentEndMarkers(codeConfigFile);

        codeConfig = eu.okaeri.configs.ConfigManager.create(CodeConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(codeConfigFile);
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        codeConfig.save();

        File creatorModeFolder = new File(addonsFolder, "creatormode");
        if (!creatorModeFolder.exists()) creatorModeFolder.mkdirs();

        creatorModeConfig = eu.okaeri.configs.ConfigManager.create(CreatorModeConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(creatorModeFolder, "creatormode.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        creatorModeConfig.save();

        File backupFolder = new File(addonsFolder, "backup");
        if (!backupFolder.exists()) backupFolder.mkdirs();

        backupConfig = eu.okaeri.configs.ConfigManager.create(BackupConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(backupFolder, "backup.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        backupConfig.save();

        File logiFolder = new File(addonsFolder, "logi");
        if (!logiFolder.exists()) logiFolder.mkdirs();

        logiConfig = eu.okaeri.configs.ConfigManager.create(LogiConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(logiFolder, "logi.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        logiConfig.save();

        File checkFolder = new File(addonsFolder, "check");
        if (!checkFolder.exists()) checkFolder.mkdirs();

        checkConfig = eu.okaeri.configs.ConfigManager.create(CheckConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(checkFolder, "check.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        checkConfig.save();

        File chatFolder = new File(addonsFolder, "chat");
        if (!chatFolder.exists()) chatFolder.mkdirs();

        chatConfig = eu.okaeri.configs.ConfigManager.create(ChatConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(chatFolder, "chat.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        if (chatConfig.migrateHoverDefaults()) {
            chatConfig.save();
        } else {
            chatConfig.save();
        }

        lpcChatConfig = eu.okaeri.configs.ConfigManager.create(LpcChatConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(chatFolder, "lpc.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        lpcChatConfig.save();

        File renderDistanceFolder = new File(addonsFolder, "renderdistance");
        if (!renderDistanceFolder.exists()) renderDistanceFolder.mkdirs();

        renderDistanceConfig = eu.okaeri.configs.ConfigManager.create(RenderDistanceConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(renderDistanceFolder, "render-distance.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        renderDistanceConfig.save();

        File armorstarsFolder = new File(addonsFolder, "armorstars");
        if (!armorstarsFolder.exists()) armorstarsFolder.mkdirs();

        armorStarsConfig = eu.okaeri.configs.ConfigManager.create(ArmorStarsConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(armorstarsFolder, "armorstars.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        armorStarsConfig.save();

        File ipLimitFolder = new File(addonsFolder, "iplimit");
        if (!ipLimitFolder.exists()) ipLimitFolder.mkdirs();

        ipLimitConfig = eu.okaeri.configs.ConfigManager.create(IpLimitConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(ipLimitFolder, "iplimit.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        ipLimitConfig.save();

        plugin.getLogger().info("All configs loaded successfully!");
    }

    private void ensureDefaultConfigResources() {
        for (String resource : DEFAULT_CONFIG_RESOURCES) {
            if (isExclusiveAddonResource(resource) && !hasExclusiveAddonsLicenseInFile()) {
                continue;
            }
            HollyDataPaths.ensureResource(plugin, resource);
        }
    }

    private boolean isExclusiveAddonResource(String resource) {
        if (resource == null || !resource.startsWith("addons/")) {
            return false;
        }

        String[] parts = resource.split("/");
        return parts.length > 1 && plugin.isExclusiveLicenseAddon(parts[1]);
    }

    private boolean hasExclusiveAddonsLicenseInFile() {
        File licenseFile = HollyDataPaths.getRootFile(plugin, "license.yml");
        if (!licenseFile.exists()) {
            return false;
        }

        String licenseKey = YamlConfiguration.loadConfiguration(licenseFile).getString("license-key", "");
        return HollyCore.EXCLUSIVE_ADDONS_LICENSE_KEY.equalsIgnoreCase(licenseKey == null ? "" : licenseKey.trim());
    }

    private void repairYamlDocumentEndMarkers(File file) {
        if (!file.exists() || !file.isFile()) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            List<String> repairedLines = new ArrayList<>(lines.size());
            boolean repaired = false;

            for (String line : lines) {
                if (isYamlDocumentEndMarker(line)) {
                    repaired = true;
                    continue;
                }
                repairedLines.add(line);
            }

            if (!repaired) {
                return;
            }

            File backup = new File(file.getParentFile(), file.getName() + ".broken-" + System.currentTimeMillis());
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.write(file.toPath(), repairedLines, StandardCharsets.UTF_8);
            plugin.getLogger().warning("Repaired YAML document marker in " + file.getPath() + " (backup: " + backup.getName() + ")");
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not repair YAML document marker in " + file.getPath() + ": " + exception.getMessage());
        }
    }

    private boolean isYamlDocumentEndMarker(String line) {
        String normalizedLine = line.startsWith("\uFEFF") ? line.substring(1) : line;
        if (!normalizedLine.startsWith("...")) {
            return false;
        }
        if (normalizedLine.length() == 3) {
            return true;
        }
        char next = normalizedLine.charAt(3);
        return Character.isWhitespace(next) || next == '#';
    }

    private void migrateLegacyCommandsConfig(File commandsFolder) {
        File oldCommands = new File(commandsFolder, "commands.yml");
        File sectors = new File(commandsFolder, "sectors.yml");
        File distributeAdmin = new File(commandsFolder, "distributeadmin.yml");
        if (oldCommands.exists()) {
            if (!sectors.exists()) {
                try {
                    Files.copy(oldCommands.toPath(), sectors.toPath());
                } catch (IOException exception) {
                    plugin.getLogger().warning("Nie udalo sie skopiowac starego commands/commands.yml do sectors.yml: " + exception.getMessage());
                }
            }
            if (!distributeAdmin.exists()) {
                try {
                    Files.copy(oldCommands.toPath(), distributeAdmin.toPath());
                } catch (IOException exception) {
                    plugin.getLogger().warning("Nie udalo sie skopiowac starego commands/commands.yml do distributeadmin.yml: " + exception.getMessage());
                }
            }
        }

        File legacy = new File(commandsFolder, "config.yml");
        if (!legacy.exists()) {
            return;
        }

        if (!sectors.exists()) {
            try {
                Files.copy(legacy.toPath(), sectors.toPath());
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udalo sie skopiowac starego commands/config.yml do sectors.yml: " + exception.getMessage());
            }
        }

        if (!distributeAdmin.exists()) {
            try {
                Files.copy(legacy.toPath(), distributeAdmin.toPath());
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udalo sie skopiowac starego commands/config.yml do distributeadmin.yml: " + exception.getMessage());
            }
        }

        if (sectors.exists() && distributeAdmin.exists()) {
            try {
                archiveLegacyFile(legacy, "addons");
                plugin.getLogger().info("Przeniesiono stare commands/config.yml do backups/migrations/addons.");
            } catch (IOException exception) {
                plugin.getLogger().warning("Nie udalo sie zarchiwizowac starego commands/config.yml: " + exception.getMessage());
            }
        }
    }

    public void reloadAllConfigs() {
        HollyIntegrityGuard.requireBound(plugin, "config:reload");
        HollyDataMigrator.migrate(plugin);

        mainConfig.load(true);
        databaseConfig.load(true);
        messagesConfig.load(true);
        spawnConfig.load(true);
        autoMessageConfig.load(true);
        shopConfig.load(true);
        guiConfig.load(true);
        economyConfig.load(true);
        ignoreConfig.load(true);
        afkConfig.load(true);
        clearLagConfig.load(true);
        walletConfig.load(true);
        adminConfig.load(true);
        vanishConfig.load(true);
        soundsConfig.load(true);
        clanConfig.load(true);
        clanConfig.applyHypestarPackIfNeeded();
        combatConfig.load(true);
        combatConfig.sanitizeKillSoundSettings();
        restartConfig.load(true);
        kitConfig.load(true);
        marketConfig.load(true);
        guiTemplateConfig.load(true);
        rtpConfig.load(true);
        codeConfig.load(true);
        creatorModeConfig.load(true);
        backupConfig.load(true);
        chatConfig.load(true);
        lpcChatConfig.load(true);
        customHitConfig.load(true);
        endModuleConfig.load(true);
        heartsConfig.load(true);
        logiConfig.load(true);
        checkConfig.load(true);
        discordConfig.load(true);
        if (playerCommandConfig != null) {
            playerCommandConfig.load(true);
        }
        antiLagConfig.load(true);
        renderDistanceConfig.load(true);
        if (ipLimitConfig != null) {
            ipLimitConfig.load(true);
        }
        if (armorStarsConfig != null) {
            armorStarsConfig.load(true);
        }
    }

    public void reloadIpLimitConfig() {
        if (ipLimitConfig != null) {
            ipLimitConfig.load(true);
        }
    }

    public void reloadAfkConfig() {
        afkConfig.load(true);
    }

    public void reloadCreatorModeConfig() {
        creatorModeConfig.load(true);
    }

    public void reloadVanishConfig() {
        vanishConfig.load(true);
    }

    public void reloadCodeConfig() {
        codeConfig.load(true);
    }

    public void reloadAntiLagConfig() {
        antiLagConfig.load(true);
    }

    public void reloadBackupConfig() {
        backupConfig.load(true);
    }

    public void reloadClearLagConfig() {
        clearLagConfig.load(true);
    }

    public void reloadWalletConfig() {
        walletConfig.load(true);
    }

    public void reloadLpcChatConfig() {
        lpcChatConfig.load(true);
    }

    public void reloadChatConfig() {
        chatConfig.load(true);
        if (chatConfig.migrateHoverDefaults()) {
            chatConfig.save();
        }
    }

    public void reloadAdminConfig() {
        adminConfig.load(true);
    }

    public void reloadDiscordConfig() {
        discordConfig.load(true);
    }

    public void reloadPlayerCommandConfig() {
        if (playerCommandConfig != null) {
            playerCommandConfig.load(true);
        }
    }

    public void reloadRenderDistanceConfig() {
        renderDistanceConfig.load(true);
    }

    public void reloadArmorStarsConfig() {
        if (armorStarsConfig != null) {
            armorStarsConfig.load(true);
        }
    }

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public SpawnConfig getSpawnConfig() {
        return spawnConfig;
    }

    public AutoMessageConfig getAutoMessageConfig() {
        return autoMessageConfig;
    }

    public ShopConfig getShopConfig() {
        return shopConfig;
    }

    public GUIConfig getGuiConfig() {
        return guiConfig;
    }

    public EconomyConfig getEconomyConfig() {
        return economyConfig;
    }

    public IgnoreConfig getIgnoreConfig() {
        return ignoreConfig;
    }

    public AfkConfig getAfkConfig() {
        return afkConfig;
    }

    public ClearLagConfig getClearLagConfig() {
        return clearLagConfig;
    }

    public WalletConfig getWalletConfig() {
        return walletConfig;
    }

    public AntiLagConfig getAntiLagConfig() {
        return antiLagConfig;
    }

    public AdminConfig getAdminConfig() {
        return adminConfig;
    }

    public VanishConfig getVanishConfig() {
        return vanishConfig;
    }

    public SoundsConfig getSoundsConfig() {
        return soundsConfig;
    }

    public ClanConfig getClanConfig() {
        return clanConfig;
    }

    public void reloadClanConfig() {
        if (clanConfig == null) {
            return;
        }
        clanConfig.load(true);
        clanConfig.applyHypestarPackIfNeeded();
    }

    public CombatConfig getCombatConfig() {
        return combatConfig;
    }

    public RestartConfig getRestartConfig() {
        return restartConfig;
    }


    public KitConfig getKitConfig() {
        return kitConfig;
    }

    public MarketConfig getMarketConfig() {
        return marketConfig;
    }

    public MarketFiltersConfig getMarketFiltersConfig() {
        return marketFiltersConfig;
    }

    public GuiTemplateConfig getGuiTemplateConfig() {
        return guiTemplateConfig;
    }

    public RtpConfig getRtpConfig() {
        return rtpConfig;
    }

    public CodeConfig getCodeConfig() {
        return codeConfig;
    }

    public CreatorModeConfig getCreatorModeConfig() {
        return creatorModeConfig;
    }

    public BackupConfig getBackupConfig() {
        return backupConfig;
    }

    public CheckConfig getCheckConfig() {
        return checkConfig;
    }

    public ChatConfig getChatConfig() {
        return chatConfig;
    }

    public LpcChatConfig getLpcChatConfig() {
        return lpcChatConfig;
    }

    public LicenseConfig getLicenseConfig() {
        HollyIntegrityGuard.preflight(plugin, "config:get:license");
        return licenseConfig;
    }

    public CustomHitConfig getCustomHitConfig() {
        return customHitConfig;
    }

    public EndModuleConfig getEndModuleConfig() {
        return endModuleConfig;
    }

    public HeartsConfig getHeartsConfig() {
        return heartsConfig;
    }

    public LogiConfig getLogiConfig() {
        return logiConfig;
    }

    public DiscordConfig getDiscordConfig() {
        return discordConfig;
    }

    public PlayerCommandConfig getPlayerCommandConfig() {
        return playerCommandConfig;
    }

    public RenderDistanceConfig getRenderDistanceConfig() {
        return renderDistanceConfig;
    }

    public ArmorStarsConfig getArmorStarsConfig() {
        return armorStarsConfig;
    }

    public IpLimitConfig getIpLimitConfig() {
        return ipLimitConfig;
    }


    private void migrateLegacyGuiFile(File oldFile, File newFile) {
        if (!oldFile.exists() || oldFile.equals(newFile)) {
            return;
        }

        try {
            File parent = newFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!newFile.exists()) {
                Files.move(oldFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                deleteEmptyParents(oldFile.getParentFile());
                return;
            }

            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(oldFile);
            YamlConfiguration target = YamlConfiguration.loadConfiguration(newFile);
            for (var entry : legacy.getValues(true).entrySet()) {
                if (entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection) {
                    continue;
                }
                target.set(entry.getKey(), entry.getValue());
            }
            target.save(newFile);
            archiveLegacyFile(oldFile, "gui");
            deleteEmptyParents(oldFile.getParentFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się przenieść starego GUI configu " + oldFile.getName() + ": " + exception.getMessage());
        }
    }

    private void archiveLegacyFile(File file, String category) throws IOException {
        File backupFolder = new File(plugin.getDataFolder(), "backups/migrations/" + category);
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
        String safeParent = file.getParentFile() == null ? "root" : file.getParentFile().getName();
        File backup = new File(backupFolder, safeParent + "-" + file.getName() + ".bak");
        Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteEmptyParents(File folder) {
        File dataFolder = plugin.getDataFolder();
        while (folder != null && !folder.equals(dataFolder)) {
            String[] children = folder.list();
            if (children == null || children.length > 0) {
                return;
            }
            File parent = folder.getParentFile();
            folder.delete();
            folder = parent;
        }
    }

}
