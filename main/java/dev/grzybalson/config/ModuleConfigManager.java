package dev.grzybalson.config;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.guis.GuiTemplateConfig;
import dev.grzybalson.config.modules.clearlag.ClearlagBlacklistConfig;
import dev.grzybalson.config.modules.clearlag.ClearlagMessagesConfig;
import dev.grzybalson.config.modules.commands.CommandsMessagesConfig;
import dev.grzybalson.config.modules.kits.KitsConfig;
import dev.grzybalson.config.modules.clans.EloSettingsConfig;
import dev.grzybalson.modules.infogui.InfoGuiDefinition;
import dev.grzybalson.config.modules.warps.WarpsConfig;
import dev.grzybalson.config.modules.warps.WarpDataConfig;
import dev.grzybalson.config.modules.messages.JoinQuitConfig;
import dev.grzybalson.config.modules.bossbar.BossBarConfig;
import dev.grzybalson.config.modules.achievement.AchievementConfig;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;

import java.io.File;
import java.io.IOException;
import dev.grzybalson.config.modules.home.HomeConfig;
import dev.grzybalson.config.modules.czek.CzekConfig;
import dev.grzybalson.config.modules.coinflip.CoinflipConfig;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ModuleConfigManager {
    private static final String[] DEFAULT_CONFIG_RESOURCES = {
        "addons/clans/elo.yml",
        "addons/clearlag/messages.yml",
        "addons/clearlag/blacklist.yml",
        "addons/messages/messages.yml",
        "guis/template.yml",
        "addons/kit/gui.yml",
        "addons/messages/join_quit.yml",
        "addons/ranks/ranks.yml",
        "addons/media/media.yml",
        "addons/warps/warps-settings.yml",
        "addons/warps/warps.yml",
        "addons/bossbar/bossbar.yml",
        "addons/achievement/achievement.yml",
        "guis/home.yml",
        "guis/coinflip.yml",
        "guis/trener.yml",
        "addons/czek/czek.yml",
        "addons/coinflip/coinflip.yml",
        "addons/killeffect/killeffect.yml",
        "addons/flamehub-customitems/flamehub-customitems.yml",
        "addons/trener/trener.yml"
    };

    private final HollyCore plugin;

    private ClearlagMessagesConfig clearlagMessagesConfig;
    private ClearlagBlacklistConfig clearlagBlacklistConfig;
    private EloSettingsConfig eloSettingsConfig;
    private KitsConfig kitsConfig;
    private InfoGuiDefinition ranksGui;
    private InfoGuiDefinition mediaGui;
    private CommandsMessagesConfig commandsMessagesConfig;
    private GuiTemplateConfig guiTemplateConfig;
    private WarpsConfig warpsConfig;
    private WarpDataConfig warpDataConfig;
    private JoinQuitConfig joinQuitConfig;
    private BossBarConfig bossBarConfig;
    private AchievementConfig achievementConfig;
    private HomeConfig homeConfig;
    private CzekConfig czekConfig;
    private CoinflipConfig coinflipConfig;

    public ModuleConfigManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        File dataFolder = plugin.getDataFolder();
        File addonsFolder = HollyDataPaths.getAddonsFolder(plugin);
        ensureDefaultConfigResources();

        File clansFolder = new File(addonsFolder, "clans");
        if (!clansFolder.exists()) clansFolder.mkdirs();

        eloSettingsConfig = eu.okaeri.configs.ConfigManager.create(EloSettingsConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(clansFolder, "elo.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File clearlagFolder = new File(addonsFolder, "clearlag");
        if (!clearlagFolder.exists()) clearlagFolder.mkdirs();

        clearlagMessagesConfig = eu.okaeri.configs.ConfigManager.create(ClearlagMessagesConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(clearlagFolder, "messages.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        clearlagBlacklistConfig = eu.okaeri.configs.ConfigManager.create(ClearlagBlacklistConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(clearlagFolder, "blacklist.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File messagesFolder = new File(addonsFolder, "messages");
        if (!messagesFolder.exists()) messagesFolder.mkdirs();

        commandsMessagesConfig = eu.okaeri.configs.ConfigManager.create(CommandsMessagesConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(messagesFolder, "messages.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File guiFolder = new File(dataFolder, "guis");
        if (!guiFolder.exists()) guiFolder.mkdirs();
        migrateLegacyGuiFile(new File(dataFolder, "gui/template.yml"), new File(guiFolder, "template.yml"));
        migrateLegacyGuiFile(new File(addonsFolder, "guis/template.yml"), new File(guiFolder, "template.yml"));

        guiTemplateConfig = eu.okaeri.configs.ConfigManager.create(GuiTemplateConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(guiFolder, "template.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File kitFolder = new File(addonsFolder, "kit");
        if (!kitFolder.exists()) kitFolder.mkdirs();

        kitsConfig = eu.okaeri.configs.ConfigManager.create(KitsConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(kitFolder, "gui.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        joinQuitConfig = eu.okaeri.configs.ConfigManager.create(JoinQuitConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(messagesFolder, "join_quit.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File ranksFolder = new File(addonsFolder, "ranks");
        if (!ranksFolder.exists()) ranksFolder.mkdirs();
        File ranksFile = new File(ranksFolder, "ranks.yml");
        migrateLegacyRanksFile(ranksFile);
        ranksGui = InfoGuiDefinition.load(plugin, "addons/ranks/ranks.yml", ranksFile);

        File mediaFolder = new File(addonsFolder, "media");
        if (!mediaFolder.exists()) mediaFolder.mkdirs();
        File mediaFile = new File(mediaFolder, "media.yml");
        mediaGui = InfoGuiDefinition.load(plugin, "addons/media/media.yml", mediaFile);

        File warpsFolder = new File(addonsFolder, "warps");
        if (!warpsFolder.exists()) warpsFolder.mkdirs();

        warpsConfig = eu.okaeri.configs.ConfigManager.create(WarpsConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(warpsFolder, "warps-settings.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        warpDataConfig = eu.okaeri.configs.ConfigManager.create(WarpDataConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(warpsFolder, "warps.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File bossbarFolder = new File(addonsFolder, "bossbar");
        if (!bossbarFolder.exists()) bossbarFolder.mkdirs();

        bossBarConfig = eu.okaeri.configs.ConfigManager.create(BossBarConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(bossbarFolder, "bossbar.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File achievementFolder = new File(addonsFolder, "achievement");
        if (!achievementFolder.exists()) achievementFolder.mkdirs();

        achievementConfig = eu.okaeri.configs.ConfigManager.create(AchievementConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(achievementFolder, "achievement.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        migrateLegacyGuiFile(new File(addonsFolder, "home/home.yml"), new File(guiFolder, "home.yml"));

        homeConfig = eu.okaeri.configs.ConfigManager.create(HomeConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(guiFolder, "home.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File czekFolder = new File(addonsFolder, "czek");
        if (!czekFolder.exists()) czekFolder.mkdirs();

        czekConfig = eu.okaeri.configs.ConfigManager.create(CzekConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(czekFolder, "czek.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });

        File coinflipFolder = new File(addonsFolder, "coinflip");
        if (!coinflipFolder.exists()) coinflipFolder.mkdirs();

        coinflipConfig = eu.okaeri.configs.ConfigManager.create(CoinflipConfig.class, (it) -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(coinflipFolder, "coinflip.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
    }

    private void ensureDefaultConfigResources() {
        for (String resource : DEFAULT_CONFIG_RESOURCES) {
            HollyDataPaths.ensureResource(plugin, resource);
        }
    }

    public void reloadAll() {
        ensureDefaultConfigResources();
        eloSettingsConfig.load(true);
        clearlagMessagesConfig.load(true);
        clearlagBlacklistConfig.load(true);
        commandsMessagesConfig.load(true);
        guiTemplateConfig.load(true);
        kitsConfig.load(true);
        joinQuitConfig.load(true);
        reloadRanksGui();
        reloadMediaGui();
        warpsConfig.load(true);
        warpDataConfig.load(true);
        bossBarConfig.load(true);
        achievementConfig.load(true);
        homeConfig.load(true);
        czekConfig.load(true);
        if (coinflipConfig != null) {
            coinflipConfig.load(true);
            if (coinflipConfig.migrateBrokenDefaults()) {
                coinflipConfig.save();
            }
        }
    }

    public EloSettingsConfig getEloSettingsConfig() { return eloSettingsConfig; }
    public ClearlagMessagesConfig getClearlagMessagesConfig() { return clearlagMessagesConfig; }
    public ClearlagBlacklistConfig getClearlagBlacklistConfig() { return clearlagBlacklistConfig; }
    public CommandsMessagesConfig getCommandsMessagesConfig() { return commandsMessagesConfig; }
    public GuiTemplateConfig getGuiTemplateConfig() { return guiTemplateConfig; }
    public KitsConfig getKitsConfig() { return kitsConfig; }
    public JoinQuitConfig getJoinQuitConfig() { return joinQuitConfig; }
    public InfoGuiDefinition getRanksGui() { return ranksGui; }
    public InfoGuiDefinition getMediaGui() { return mediaGui; }
    public void reloadRanksGui() {
        File ranksFile = new File(HollyDataPaths.getAddonFolder(plugin, "ranks"), "ranks.yml");
        migrateLegacyRanksFile(ranksFile);
        if (ranksGui == null) {
            ranksGui = InfoGuiDefinition.load(plugin, "addons/ranks/ranks.yml", ranksFile);
        } else {
            ranksGui.reload();
        }
    }
    public void reloadMediaGui() {
        File mediaFile = new File(HollyDataPaths.getAddonFolder(plugin, "media"), "media.yml");
        if (mediaGui == null) {
            mediaGui = InfoGuiDefinition.load(plugin, "addons/media/media.yml", mediaFile);
        } else {
            mediaGui.reload();
        }
    }
    public WarpsConfig getWarpsConfig() { return warpsConfig; }
    public WarpDataConfig getWarpDataConfig() { return warpDataConfig; }
    public BossBarConfig getBossBarConfig() { return bossBarConfig; }
    public AchievementConfig getAchievementConfig() { return achievementConfig; }
    public HomeConfig getHomeConfig() { return homeConfig; }
    public CzekConfig getCzekConfig() { return czekConfig; }
    public CoinflipConfig getCoinflipConfig() { return coinflipConfig; }

    private void migrateLegacyRanksFile(File ranksFile) {
        if (!ranksFile.exists()) {
            return;
        }
        try {
            String content = Files.readString(ranksFile.toPath());
            if (content.contains("guiTitle:") || content.contains("\nguiRows:") || content.contains("- name:")) {
                File backup = new File(ranksFile.getParentFile(), "ranks.yml.legacy-backup");
                Files.copy(ranksFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.saveResource("addons/ranks/ranks.yml", true);
                plugin.getLogger().info("Zmigrowano stary format ranks.yml (backup: ranks.yml.legacy-backup).");
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zmigrowac ranks.yml: " + exception.getMessage());
        }
    }

    private void migrateLegacyGuiFile(File oldFile, File newFile) {
        if (!oldFile.exists()) {
            return;
        }
        if (newFile.exists()) {
            oldFile.delete();
            deleteEmptyParents(oldFile.getParentFile());
            return;
        }

        try {
            Files.move(oldFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            deleteEmptyParents(oldFile.getParentFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się przenieść starego GUI configu " + oldFile.getName() + ": " + exception.getMessage());
        }
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
