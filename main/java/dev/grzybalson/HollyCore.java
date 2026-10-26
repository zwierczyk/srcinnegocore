package dev.grzybalson;

import dev.grzybalson.anarchiagg.AnarchiaGGConfigManager;
import dev.grzybalson.anarchiagg.AnarchiaGGModuleManager;

import dev.grzybalson.commands.*;
import dev.grzybalson.commands.admin.ChCommand;
import dev.grzybalson.commands.admin.RozdajAdminCommand;
import dev.grzybalson.commands.economy.EcoCommand;
import dev.grzybalson.commands.economy.MoneyCommand;
import dev.grzybalson.commands.shop.ShopCommand;
import dev.grzybalson.commands.shop.AdminShopCommand;
import dev.grzybalson.commands.pickup.PickupCommand;
import dev.grzybalson.commands.wallet.APortfelCommand;
import dev.grzybalson.commands.wallet.GetbackCommand;
import dev.grzybalson.commands.wallet.PortfelCommand;
import dev.grzybalson.config.ConfigManager;
import dev.grzybalson.config.configs.LpcChatConfig;
import dev.grzybalson.config.anarchiagg.AnarchiaGGModulesConfig;
import dev.grzybalson.database.MongoManager;
import dev.grzybalson.economy.HollyEconomy;
import dev.grzybalson.economy.VaultHook;
import dev.grzybalson.listeners.PlayerListener;
import dev.grzybalson.listeners.TpaConfirmListener;
import dev.grzybalson.listeners.GUIListener;
import dev.grzybalson.listeners.StaticMenuListener;
import dev.grzybalson.listeners.ClanChatListener;
import dev.grzybalson.listeners.ClanPvpListener;
import dev.grzybalson.listeners.CommandCooldownListener;
import dev.grzybalson.listeners.ElytraFireworkWearListener;
import dev.grzybalson.managers.*;
import dev.grzybalson.modules.combat.CombatDisplayListener;
import dev.grzybalson.modules.combat.CombatManager;
import dev.grzybalson.modules.anticheat.AnticheatManager;
import dev.grzybalson.modules.unique.UniqueItemService;
import dev.grzybalson.modules.diagnostics.ServerDiagnosticsService;
import dev.grzybalson.modules.groupteleport.GroupTeleportCommand;
import dev.grzybalson.modules.groupteleport.GroupTeleportInteractListener;
import dev.grzybalson.modules.groupteleport.GroupTeleportListener;
import dev.grzybalson.modules.groupteleport.GroupTeleportManager;
import dev.grzybalson.modules.boss.BossCommand;
import dev.grzybalson.modules.boss.BossManager;
import dev.grzybalson.modules.moderation.ModerationCommands;
import dev.grzybalson.modules.moderation.ModerationListener;
import dev.grzybalson.modules.moderation.ModerationManager;
import dev.grzybalson.modules.report.ReportStore;
import dev.grzybalson.modules.sectors.SectorService;
import dev.grzybalson.modules.itemsync.InventorySyncCommand;
import dev.grzybalson.modules.itemsync.InventorySyncListener;
import dev.grzybalson.modules.itemsync.InventorySyncManager;
import dev.grzybalson.modules.shulkerbox.ShulkerBoxListener;
import dev.grzybalson.network.NatsService;
import dev.grzybalson.menus.StaticMenuManager;
import dev.grzybalson.modules.combat.CombatListener;
import dev.grzybalson.managers.MultiActionBarService;
import dev.grzybalson.modules.antigrief.AntiGriefManager;
import dev.grzybalson.modules.customhit.ArmorToughnessListener;
import dev.grzybalson.modules.customhit.CustomHitDamageListener;
import dev.grzybalson.modules.customhit.CustomHitsService;
import dev.grzybalson.modules.customhit.DiamondArmorKnockbackListener;
import dev.grzybalson.modules.customhit.PvpMaxDamageCapListener;
import dev.grzybalson.modules.customhit.LevitationKnockbackListener;
import dev.grzybalson.modules.end.EndModuleListener;
import dev.grzybalson.modules.generator.GeneratorCommand;
import dev.grzybalson.modules.generator.GeneratorManager;
import dev.grzybalson.modules.restart.RestartManager;
import dev.grzybalson.modules.kit.KitManager;
import dev.grzybalson.modules.market.MarketManager;
import dev.grzybalson.modules.mobs.MobsManager;
import dev.grzybalson.modules.upgrade.UpgradeBestEffectsListener;
import dev.grzybalson.modules.upgrade.UpgradeManager;
import dev.grzybalson.modules.piniata.PiniataCommand;
import dev.grzybalson.modules.piniata.PiniataManager;
import dev.grzybalson.modules.minestaranvil.MinestarAnvilManager;
import dev.grzybalson.modules.minestarogcustomitems.MinestarOGCustomItemsManager;
import dev.grzybalson.modules.oghubcustomitems.OgHubCustomItemsManager;
import dev.grzybalson.modules.masakracustomitems.MasakraCustomItemsManager;
import dev.grzybalson.modules.flamehubcustomitems.FlameHubCustomItemsManager;
import dev.grzybalson.modules.flamehubcustomitems.commands.FlameItemsCommand;
import dev.grzybalson.modules.flamehubcustomitems.commands.FlameReloadCommand;
import dev.grzybalson.modules.flamehubcustomitems.commands.FlameResetCommand;
import dev.grzybalson.modules.oghubszafka.OgHubSzafkaManager;
import dev.grzybalson.modules.oghubzmianki.OgHubZmiankiManager;
import dev.grzybalson.modules.patrons.PatronManager;
import dev.grzybalson.modules.lockedfeatures.LockedFeaturesManager;
import dev.grzybalson.modules.zonebox.ZoneBoxManager;
import dev.grzybalson.gui.BlockEconomyGUI;
import dev.grzybalson.gui.GuiFactory;
import dev.grzybalson.gui.ShopGUI;
import dev.grzybalson.config.ModuleConfigManager;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.modules.ranking.EloManager;
import dev.grzybalson.modules.ranking.EloListener;
import dev.grzybalson.modules.ranking.KillTitleAnimator;
import dev.grzybalson.modules.teleport.TeleportAnimator;
import dev.grzybalson.modules.warp.WarpManager;
import dev.grzybalson.modules.warp.WarpListener;
import dev.grzybalson.modules.worldborder.WorldBorderManager;
import dev.grzybalson.modules.renderdistance.RenderDistanceCommand;
import dev.grzybalson.modules.renderdistance.RenderDistanceListener;
import dev.grzybalson.modules.renderdistance.RenderDistanceManager;
import dev.grzybalson.listeners.JoinQuitListener;
import dev.grzybalson.listeners.CommandBlockerListener;
import dev.grzybalson.listeners.ButtonTeleportListener;
import dev.grzybalson.listeners.ChatCooldownListener;
import dev.grzybalson.listeners.LpcChatListener;
import dev.grzybalson.listeners.DeepslateInteractListener;
import dev.grzybalson.placeholders.CorePlaceholderExpansion;
import dev.grzybalson.placeholders.HollyPlaceholderExpansion;
import dev.grzybalson.placeholders.HollyServicePlaceholderExpansion;
import dev.grzybalson.placeholders.PortfelPlaceholderExpansion;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import dev.grzybalson.utils.StartupLogger;
import dev.grzybalson.modules.bossbar.BossBarManager;
import dev.grzybalson.managers.CreatorModeManager;
import dev.grzybalson.managers.ActionBarManager;
import dev.grzybalson.listeners.CreatorModeListener;
import dev.grzybalson.modules.backup.BackupManager;
import dev.grzybalson.modules.achievement.AchievementListener;
import dev.grzybalson.modules.achievement.AchievementManager;
import dev.grzybalson.modules.achievement.AchievementType;
import dev.grzybalson.modules.backup.BackupListener;
import dev.grzybalson.modules.backup.BackupGUIListener;
import dev.grzybalson.modules.cases.CaseCommand;
import dev.grzybalson.modules.cases.CaseListener;
import dev.grzybalson.modules.cases.CaseManager;
import dev.grzybalson.modules.event.ADungeonCommand;
import dev.grzybalson.modules.event.DungeonSkipCommand;
import dev.grzybalson.modules.event.EventBossBarListener;
import dev.grzybalson.modules.event.EventCommand;
import dev.grzybalson.modules.event.EventGUIListener;
import dev.grzybalson.modules.event.EventManager;
import dev.grzybalson.modules.trash.TrashListener;
import dev.grzybalson.listeners.WeatherListener;
import dev.grzybalson.listeners.PortalListener;
import dev.grzybalson.listeners.CheckListener;
import dev.grzybalson.listeners.EntityLimitListener;
import dev.grzybalson.listeners.InventorySecurityListener;
import dev.grzybalson.listeners.RestartFreezeListener;
import dev.grzybalson.listeners.IpLimitListener;
import dev.grzybalson.listeners.CoreStartupGate;
import dev.grzybalson.listeners.ItemAttributeListener;
import dev.grzybalson.listeners.LegacyShopItemSanitizer;
import dev.grzybalson.listeners.WalletGUIListener;
import dev.grzybalson.listeners.VplnVoucherListener;
import dev.grzybalson.listeners.WorldGameRuleListener;
import dev.grzybalson.listeners.SettingsGUIListener;
import dev.grzybalson.database.StatsDataManager;
import dev.grzybalson.commands.daily.AdminDailyCommand;
import dev.grzybalson.commands.daily.DailyRewardCommand;
import java.util.UUID;
import dev.grzybalson.commands.daily.DailyRewardListener;
import dev.grzybalson.commands.daily.DailyRewardManager;
import dev.grzybalson.listeners.BlockEnvironmentListener;
import dev.grzybalson.listeners.NoEnvironmentalDamageListener;
import dev.grzybalson.internal.HollyIntegrityGuard;
import dev.grzybalson.internal.HollyRuntime;
import dev.grzybalson.license.LicenseManager;
import dev.grzybalson.license.LicenseSlotLimiter;
import dev.grzybalson.modules.discordreward.DiscordRewardManager;
import dev.grzybalson.modules.discordreward.DiscordRewardMenuListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HollyCore extends JavaPlugin {

    private static final int BSTATS_PLUGIN_ID = 30567;
    public static final String EXCLUSIVE_ADDONS_LICENSE_KEY = "VU1T5-RVDBG-O0ZHV-2KYGG-6Q98F";
    private static final Set<String> EXCLUSIVE_LICENSE_ADDONS = Set.of();

    private static HollyCore instance;
    private StartupLogger startupLogger;
    private ConfigManager configManager;
    private MongoManager mongoManager;
    private HollyEconomy economy;
    private VaultHook vaultHook;
    private CooldownManager cooldownManager;
    private TeleportRequestManager teleportRequestManager;
    private AutoMessageManager autoMessageManager;
    private AutoCommandsManager autoCommandsManager;
    private PickupManager pickupManager;
    private IgnoreManager ignoreManager;
    private AfkManager afkManager;
    private ClearLagManager clearLagManager;
    private TopManager topManager;
    private dev.grzybalson.modules.tops.TopsManager topsManager;
    private VanishManager vanishManager;
    private SoundManager soundManager;
    private ClanManager clanManager;
    private CombatManager combatManager;
    private AnticheatManager anticheatManager;
    private NatsService natsService;
    private UniqueItemService uniqueItemService;
    private SectorService sectorService;
    private ServerDiagnosticsService serverDiagnosticsService;
    private InventorySyncManager inventorySyncManager;
    private ShulkerBoxListener shulkerBoxListener;
    private RewardNotificationManager rewardNotificationManager;
    private StaticMenuManager staticMenuManager;
    private RestartManager restartManager;
    private KitManager kitManager;
    private MarketManager marketManager;
    private MobsManager mobsManager;
    private UpgradeManager upgradeManager;
    private PiniataManager piniataManager;
    private ZoneBoxManager zoneBoxManager;
    private dev.grzybalson.managers.TurboDropManager turboDropManager;
    private dev.grzybalson.modules.szaman.PerksManager szamanManager;
    private dev.grzybalson.modules.sigil.SigilManager sigilManager;
    private dev.grzybalson.modules.mission.MissionManager missionManager;
    private dev.grzybalson.modules.trade.TradeManager tradeManager;
    private dev.grzybalson.modules.dropkeys.DropKeysManager dropKeysManager;
    private AntiGriefManager antiGriefManager;
    private CommandCooldownListener commandCooldownListener;
    private GuiFactory guiFactory;
    private ModuleConfigManager moduleConfigManager;
    private EloManager eloManager;
    private TeleportAnimator teleportAnimator;
    private WarpManager warpManager;
    private dev.grzybalson.modules.home.HomeManager homeManager;
    private BossBarManager bossBarManager;
    private SidebarManager sidebarManager;
    private TablistManager tablistManager;
    private IncognitoManager incognitoManager;
    private SpooferLoadTestSimulator spooferLoadTestSimulator;
    private IdleManager idleManager;
    private GroupTeleportManager groupTeleportManager;
    private BossManager bossManager;
    private ModerationManager moderationManager;
    private ReportStore reportStore;
    private dev.grzybalson.modules.itemstorage.ItemStorageManager itemStorageManager;
    private CodeManager codeManager;
    private RankGiveawayManager rankGiveawayManager;
    private AutoTitleManager autoTitleManager;
    private EnderChestManager enderChestManager;
    private CreatorModeManager creatorModeManager;
    private ActionBarManager actionBarManager;
    private dev.grzybalson.modules.clan.ClanArmorManager clanArmorManager;
    private BackupManager backupManager;
    private dev.grzybalson.modules.deathkeep.DeathKeepListener deathKeepListener;
    private dev.grzybalson.modules.backup.RollbackManager rollbackManager;
    private KillstreakManager killstreakManager;
    private CheckManager checkManager;
    private StatsDataManager statsDataManager;
    private AchievementManager achievementManager;
    private AchievementListener achievementListener;
    private CustomHitsService customHitsService;
    private dev.grzybalson.modules.hollydamage.HollyDamageService hollyDamageService;
    private EndModuleListener endModuleListener;
    private GeneratorManager generatorManager;
    private AnarchiaGGConfigManager anarchiaGGConfigManager;
    private AnarchiaGGModuleManager anarchiaGGModuleManager;
    private DailyRewardManager dailyRewardManager;
    private CaseManager caseManager;
    private MinestarAnvilManager minestarAnvilManager;
    private MinestarOGCustomItemsManager minestarOGCustomItemsManager;
    private OgHubCustomItemsManager ogHubCustomItemsManager;
    private MasakraCustomItemsManager masakraCustomItemsManager;
    private FlameHubCustomItemsManager flameHubCustomItemsManager;
    private OgHubSzafkaManager ogHubSzafkaManager;
    private dev.grzybalson.modules.mslfkostiumy.MslfKostiumyManager mslfKostiumyManager;
    private dev.grzybalson.modules.masakraszafka.MasakraSzafkaManager masakraSzafkaManager;
    private OgHubZmiankiManager ogHubZmiankiManager;
    private PatronManager patronManager;
    private LockedFeaturesManager lockedFeaturesManager;
    private WalletManager walletManager;
    private WalletProductStatsService walletProductStatsService;
    private WebhookLogManager webhookLogManager;
    private UserSettingsManager userSettingsManager;
    private DiscordRewardManager discordRewardManager;
    private TitleAddonManager titleAddonManager;
    private WorldBorderManager worldBorderManager;
    private RenderDistanceManager renderDistanceManager;
    private dev.grzybalson.modules.worlds.WorldManager worldManager;
    private HollyPlaceholderExpansion hollyPlaceholderExpansion;
    private LicenseSlotLimiter licenseSlotLimiter;
    private SpawnCommand spawnCommand;
    private RtpCommand rtpCommand;
    private TimerCommand timerCommand;
    private dev.grzybalson.modules.worlds.WorldCommand worldCommand;
    private dev.grzybalson.modules.itemshop.ItemShopCommand itemShopCommand;
    private YamlConfiguration addonsConfig;
    private dev.grzybalson.anarchiagg.coach.CoachManager coachManager;
    private dev.grzybalson.anarchiagg.adminitems.AdminItemsInfoGUI adminItemsInfoGUI;
    private dev.grzybalson.modules.coinflip.CoinflipManager coinflipManager;
    private dev.grzybalson.modules.killeffect.KillEffectManager killEffectManager;
    private dev.grzybalson.modules.coinflip.CoinflipMenuManager coinflipMenuManager;
    private dev.grzybalson.modules.coinflip.CoinflipDatabase coinflipDatabase;
    private dev.grzybalson.modules.zywioly.ZywiolyManager zywiolyManager;
    private EntityLimitListener entityLimitListener;
    private NoEnvironmentalDamageListener noEnvironmentalDamageListener;
    private dev.grzybalson.listeners.WorldBorderHitFixListener worldBorderHitFixListener;
    private dev.grzybalson.managers.JoinInfoManager joinInfoManager;
    private EventManager eventManager;
    private dev.grzybalson.modules.event.dungeon.DungeonSessionManager dungeonSessionManager;
    private dev.grzybalson.modules.mslf.MslfManager mslfManager;
    private dev.grzybalson.modules.mslfszczesciarz.MslfSzczesciarzManager mslfSzczesciarzManager;


    @Override
    public void onLoad() {
        instance = this;
        HollyIntegrityGuard.preflight(this, "plugin:load");
        try {
            com.github.retrooper.packetevents.PacketEvents.setAPI(io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder.build(this));
            com.github.retrooper.packetevents.PacketEvents.getAPI().getSettings().checkForUpdates(false).bStats(false);
            com.github.retrooper.packetevents.PacketEvents.getAPI().load();
        } catch (Throwable t) {
            getLogger().warning("PacketEvents init warning in onLoad: " + t.getMessage());
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        CoreStartupGate.reset();
        Bukkit.getPluginManager().registerEvents(new CoreStartupGate(this), this);
        HollyIntegrityGuard.preflight(this, "plugin:enable");
        try {
            if (com.github.retrooper.packetevents.PacketEvents.getAPI() != null && !com.github.retrooper.packetevents.PacketEvents.getAPI().isInitialized()) {
                com.github.retrooper.packetevents.PacketEvents.getAPI().init();
            }
        } catch (Throwable t) {
            getLogger().warning("PacketEvents init warning in onEnable: " + t.getMessage());
        }
        startupLogger = new StartupLogger(this);
        startupLogger.start();
        startupLogger.progress("PACK", 5);


        NBTUtils.init(this);

        // Marker builda — jak tego NIE MA w konsoli, serwer nadal odpala STARY HollyCore.
        getLogger().warning("[FlameHub] BOOTSTRAP_MARK_V3");
        forceFlameHubBootstrap();

        dev.grzybalson.config.HollyDataMigrator.migrate(this);
        // Auto-merge brakujacych kluczy + jednorazowe force-patche wiadomosci po update JAR.
        dev.grzybalson.config.ConfigAutoUpdater.run(this);
        loadAddonToggles();
        startupLogger.progress("PACK", 25);
        if (isAddonEnabled("anarchiagg")) {
            anarchiaGGConfigManager = new AnarchiaGGConfigManager(this);
            anarchiaGGConfigManager.load();
        }
        unregisterDisabledAddonCommands();

        configManager = new ConfigManager(this);
        configManager.loadAllConfigs();
        startupLogger.addModule("Konfiguracja", true);
        startupLogger.animateProgress("FILE", 1, 100, 4);

        startupLogger.progress("AUTH", 5);
        if (!HollyRuntime.verifyAndBind(this, configManager.getLicenseConfig())) {
            Bukkit.getConsoleSender().sendMessage("§c§l[HollyCore] Plugin zostal wylaczony z powodu bledu weryfikacji uslug!");
            CoreStartupGate.markFailed();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        HollyIntegrityGuard.requireStrong(this, "startup:service");
        HollyIntegrityGuard.startWatchdog(this);
        startupLogger.progress("AUTH", 100);
        startupLogger.addModule("Uslugi", true);

        if (LicenseManager.isFixedSlotLicense(configManager.getLicenseConfig().getLicenseKey())) {
            licenseSlotLimiter = new LicenseSlotLimiter(this);
            licenseSlotLimiter.start();
        }
        startupLogger.addModule("Limit slotow uslug", licenseSlotLimiter != null);
        if (isAddonEnabled("titles")) {
            titleAddonManager = new TitleAddonManager(this);
        }
        startupLogger.addModule("Title Addon", isAddonEnabled("titles") && titleAddonManager != null && titleAddonManager.isEnabled());

        moduleConfigManager = new ModuleConfigManager(this);
        moduleConfigManager.loadAll();
        startupLogger.addModule("Moduły Konfiguracji", true);
        startupLogger.progress("LINK", 45);

        if (anarchiaGGConfigManager == null) {
            anarchiaGGConfigManager = new AnarchiaGGConfigManager(this);
            anarchiaGGConfigManager.load();
        }
        if (!isAddonEnabled("anarchiagg") && anarchiaGGModuleManager != null) {
            anarchiaGGModuleManager.disableAll();
            anarchiaGGModuleManager = null;
        }
        startupLogger.addModule("AnarchiaGG Konfiguracja", true);

        if (isAddonEnabled("daily")) {
            dailyRewardManager = new DailyRewardManager(this);
            dailyRewardManager.reload();
        }
        startupLogger.addModule("Daily", isAddonEnabled("daily"));

        if (isAddonEnabled("cases")) {
            caseManager = new CaseManager(this);
            caseManager.reload();
        }
        startupLogger.addModule("Skrzynki", isAddonEnabled("cases"));

        String damageSystem = resolveDamageSystem();
        if ("hollyservice".equals(damageSystem)) {
            hollyDamageService = new dev.grzybalson.modules.hollydamage.HollyDamageService(this);
            startupLogger.addModule("HollyDamage", hollyDamageService != null);
            startupLogger.addModule("CustomHits", false);
        } else if ("customhit".equals(damageSystem)) {
            customHitsService = new CustomHitsService(this);
            startupLogger.addModule("CustomHits", true);
            startupLogger.addModule("HollyDamage", false);
        } else {
            startupLogger.addModule("CustomHits", false);
            startupLogger.addModule("HollyDamage", false);
        }

        if (isAddonEnabled("end")) {
            endModuleListener = new EndModuleListener(this);
        }
        startupLogger.addModule("End", isAddonEnabled("end"));

        if (isAddonEnabled("generator")) {
            generatorManager = new GeneratorManager(this);
        }
        startupLogger.addModule("Generatory", isAddonEnabled("generator") && generatorManager != null);

        webhookLogManager = new WebhookLogManager(this);
        startupLogger.addModule("Logi Webhook", isAddonEnabled("logi"));


        // Mongo MUSI być gotowe przed Managerami (getCollection w konstruktorach).
        // Async connect powodował IllegalStateException i wyłączał cały HollyCore.
        mongoManager = new MongoManager(this);
        boolean mongoReachable = false;
        try {
            mongoReachable = mongoManager.connect();
        } catch (Throwable throwable) {
            getLogger().log(java.util.logging.Level.SEVERE, "MongoDB connect failed", throwable);
        }
        startupLogger.addModule("MongoDB", mongoReachable);
        startupLogger.progress("LINK", 60);
        if (!mongoReachable) {
            getLogger().severe("[MongoDB] Niedostępne przy starcie — funkcje DB będą ograniczone / włączą się po reconnect.");
        }

        // NATS może zostać async — nie jest wymagane w konstruktorach managerów.
        natsService = new NatsService(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            boolean connected = natsService.connect();
            Bukkit.getScheduler().runTask(this, () -> {
                startupLogger.addModule("NATS", connected);
                startupLogger.progress("SYNC", 18);
            });
        });

        cooldownManager = new CooldownManager();
        joinInfoManager = new dev.grzybalson.managers.JoinInfoManager(this);
        Bukkit.getPluginManager().registerEvents(joinInfoManager, this);
        teleportRequestManager = new TeleportRequestManager(this);
        if (isAddonEnabled("commands")) pickupManager = new PickupManager(this);
        if (isAddonEnabled("messages")) ignoreManager = new IgnoreManager(this);
        topManager = new TopManager(this);
        try {
            statsDataManager = new StatsDataManager(this);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            statsDataManager = null;
            getLogger().severe("[Stats] Wyłączone — Mongo niedostępne: " + ex.getMessage());
        }
        try {
            userSettingsManager = new UserSettingsManager(this);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            userSettingsManager = null;
            getLogger().severe("[UserSettings] Wyłączone — Mongo niedostępne: " + ex.getMessage());
        }
        if (isAddonEnabled("commands")) vanishManager = new VanishManager(this);
        if (isAddonEnabled("sounds")) soundManager = new SoundManager(this);
        if (isAddonEnabled("clans")) clanManager = new ClanManager(this);
        if (isAddonEnabled("combat")) combatManager = new CombatManager(this);
        if (isAddonEnabled("anticheat")) {
            anticheatManager = new AnticheatManager(this);
            anticheatManager.start();
        }
        actionBarManager = new ActionBarManager(this);
        MultiActionBarService.initialize(this);
        MultiActionBarService multiActionBar = MultiActionBarService.get();
        if (multiActionBar != null) {
            multiActionBar.setOnChange(actionBarManager.changeListener());
        }
        if (isWalletAddonEnabled()) {
            try {
                walletManager = new WalletManager(this);
                walletProductStatsService = new WalletProductStatsService(this);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                walletManager = null;
                walletProductStatsService = null;
                getLogger().severe("[Portfel] Wyłączony — Mongo niedostępne: " + ex.getMessage());
            }
        }
        if (isAddonEnabled("commands")) {
            restartManager = new RestartManager(this);
            RestartFreezeListener freezeListener = new RestartFreezeListener(this);
            restartManager.setFreezeListener(freezeListener);
            Bukkit.getPluginManager().registerEvents(freezeListener, this);
        }
        if (isAddonEnabled("unique")) {
            uniqueItemService = new UniqueItemService(this);
            uniqueItemService.start();
        }
        if (isAddonEnabled("kit")) kitManager = new KitManager(this);
        if (isAddonEnabled("market")) {
            try {
                marketManager = new MarketManager(this);
                marketManager.load();
                marketManager.startExpirationTask();
                getLogger().info("[Rynek] Tryb lokalny — MongoDB + cache na tym serwerze.");
            } catch (IllegalStateException | IllegalArgumentException ex) {
                marketManager = null;
                getLogger().severe("[Rynek] Wyłączony — Mongo niedostępne: " + ex.getMessage());
            }
        }
        if (isAddonEnabled("sectors")) {
            sectorService = new SectorService(this);
            serverDiagnosticsService = new ServerDiagnosticsService(this);
            serverDiagnosticsService.start();
        }
        if (sectorService != null) {
            if (uniqueItemService != null) {
                uniqueItemService.reloadMaterials();
            }
            sectorService.start();
        }
        if (isAddonEnabled("shulkerbox")) {
            shulkerBoxListener = new ShulkerBoxListener(this);
        }
        if (isMobsFeatureEnabled()) {
            mobsManager = new MobsManager(this);
        }
        commandCooldownListener = new CommandCooldownListener(this);
        guiFactory = new GuiFactory(this);
        rewardNotificationManager = new RewardNotificationManager(this);
        staticMenuManager = new StaticMenuManager(this);
        if (isAddonEnabled("upgrade")) {
            upgradeManager = new UpgradeManager(this);
        }
        if (isAddonEnabled("piniata")) {
            piniataManager = new PiniataManager(this);
        }
        if (isAddonEnabled("minestarAnvil")) {
            minestarAnvilManager = new MinestarAnvilManager(this);
        }
        if (isAddonEnabled("minestarOGCustomItems")) {
            minestarOGCustomItemsManager = new MinestarOGCustomItemsManager(this);
        }
        MasakraCustomItemsManager.ensureConfigFile(this);
        if (isAddonEnabled("masakra-customitems")) {
            masakraCustomItemsManager = new MasakraCustomItemsManager(this);
        }
        if (isAddonEnabled("oghub-customitems")) {
            ogHubCustomItemsManager = new OgHubCustomItemsManager(this);
        }
        // FlameHub CustomItems — zawsze włączone; config wymuszany wcześniej w forceFlameHubBootstrap().
        try {
            flameHubCustomItemsManager = new FlameHubCustomItemsManager(this);
            getLogger().warning("[FlameHub] WLACZONY OK | /flameitems | config=plugins/"
                    + getName() + "/addons/flamehub-customitems/flamehub-customitems.yml");
        } catch (Throwable throwable) {
            flameHubCustomItemsManager = null;
            getLogger().log(java.util.logging.Level.SEVERE,
                    "[FlameHub] CRASH przy starcie flamehub-customitems", throwable);
        }
        if (isAddonEnabled("patroni")) {
            patronManager = new PatronManager(this);
        }
        if (isAddonEnabled("lockedfeatures")) {
            lockedFeaturesManager = new LockedFeaturesManager(this);
        }
        if (isAddonEnabled("zonebox")) {
            zoneBoxManager = new ZoneBoxManager(this);
        }
        if (zoneBoxManager != null && generatorManager != null) {
            zoneBoxManager.setGeneratorVolumeCheck(generatorManager::isInGeneratorVolume);
            generatorManager.syncZoneBoxAllowedBlocks();
        }
        if (isAddonEnabled("clans")) eloManager = new EloManager(this);
        teleportAnimator = new TeleportAnimator(this);
        if (isAddonEnabled("warps")) warpManager = new WarpManager(this);
        if (isAddonEnabled("home") || isAddonEnabled("homes")) {
            homeManager = new dev.grzybalson.modules.home.HomeManager(this);
        }

        startupLogger.addModule("Cooldown Manager", true);
        startupLogger.addModule("Teleportacja", true);
        startupLogger.addModule("Home System", homeManager != null);
        startupLogger.addModule("TPA", isAddonEnabled("commands") && isAddonEnabled("tpa"));
        startupLogger.addModule("Pickup System", isAddonEnabled("commands"));
        startupLogger.addModule("Ignore System", isAddonEnabled("messages"));
        startupLogger.addModule("Top Manager", true);
        startupLogger.addModule("Stats System", true);
        startupLogger.addModule("Vanish", isAddonEnabled("commands"));
        startupLogger.addModule("Dźwięki", isAddonEnabled("sounds"));
        startupLogger.addModule("Klany", isAddonEnabled("clans"));
        startupLogger.addModule("Combat System", isAddonEnabled("combat"));
        startupLogger.addModule("Anticheat", isAddonEnabled("anticheat") && anticheatManager != null);
        startupLogger.addModule("Portfel", isWalletAddonEnabled());
        startupLogger.addModule("Restart Manager", isAddonEnabled("commands"));
        startupLogger.addModule("Kity", isAddonEnabled("kit"));
        startupLogger.addModule("Rynek", isAddonEnabled("market"));
        startupLogger.addModule("Sektory", isAddonEnabled("sectors") && sectorService != null);
        startupLogger.addModule("Unique UID", isAddonEnabled("unique") && uniqueItemService != null);
        startupLogger.addModule("ArmorStars", isAddonEnabled("armorstars"));
        startupLogger.addModule("ShulkerBox", isAddonEnabled("shulkerbox") && shulkerBoxListener != null);
        startupLogger.addModule("Moby", isMobsFeatureEnabled() && mobsManager != null);
        startupLogger.addModule("Ulepsz", isAddonEnabled("upgrade"));
        startupLogger.addModule("minestarAnvil", isAddonEnabled("minestarAnvil"));
        startupLogger.addModule("minestarOGCustomItems", isAddonEnabled("minestarOGCustomItems") && minestarOGCustomItemsManager != null);
        startupLogger.addModule("oghub-customitems", isAddonEnabled("oghub-customitems") && ogHubCustomItemsManager != null);
        startupLogger.addModule("flamehub-customitems", isFlameHubCustomItemsEnabled() && flameHubCustomItemsManager != null);
        startupLogger.addModule("Patroni", isAddonEnabled("patroni"));
        startupLogger.addModule("LockedFeatures", isAddonEnabled("lockedfeatures"));
        if (isAddonEnabled("blockseconomy")) {
            turboDropManager = new dev.grzybalson.managers.TurboDropManager(this);
            Bukkit.getPluginManager().registerEvents(turboDropManager, this);
        }
        startupLogger.addModule("TurboDrop", isAddonEnabled("blockseconomy") && turboDropManager != null);
        startupLogger.addModule("ZoneBox", isAddonEnabled("zonebox"));
        startupLogger.addModule("BlocksEconomy", isAddonEnabled("zonebox") && isAddonEnabled("blockseconomy") && isAddonEnabled("economy"));
        startupLogger.addModule("GUI Factory", true);
        startupLogger.addModule("ELO Ranking", isAddonEnabled("clans"));
        startupLogger.addModule("Warpy", isAddonEnabled("warps"));

        if (isAddonEnabled("economy")) {
            try {
                economy = new HollyEconomy(this);
                vaultHook = new VaultHook(this);
                vaultHook.hook();
            } catch (IllegalStateException | IllegalArgumentException ex) {
                economy = null;
                vaultHook = null;
                getLogger().severe("[Economy] Wyłączona — Mongo niedostępne: " + ex.getMessage());
            }
        }
        startupLogger.addModule("Ekonomia (Vault)", isAddonEnabled("economy") && economy != null);

        if (isAddonEnabled("szaman")) {
            szamanManager = new dev.grzybalson.modules.szaman.PerksManager(this);
        }
        startupLogger.addModule("Szaman", isAddonEnabled("szaman"));

        if (isAddonEnabled("sigil")) {
            sigilManager = new dev.grzybalson.modules.sigil.SigilManager(this);
        }
        startupLogger.addModule("Sigil", isAddonEnabled("sigil"));

        if (isAddonEnabled("mission")) {
            missionManager = new dev.grzybalson.modules.mission.MissionManager(this);
        }
        startupLogger.addModule("Misje", isAddonEnabled("mission"));

        if (isAddonEnabled("trade")) {
            tradeManager = new dev.grzybalson.modules.trade.TradeManager(this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.trade.TradeListener(this, tradeManager), this);
        }
        startupLogger.addModule("Wymiana", isAddonEnabled("trade"));

        if (isAddonEnabled("dropkeys")) {
            dropKeysManager = new dev.grzybalson.modules.dropkeys.DropKeysManager(this);
        }
        startupLogger.addModule("DropKeys", isAddonEnabled("dropkeys"));

        if (isAddonEnabled("itemstorage")) {
            itemStorageManager = new dev.grzybalson.modules.itemstorage.ItemStorageManager(this);
        }
        startupLogger.addModule("ItemStorage", isAddonEnabled("itemstorage"));
        startupLogger.addModule("ItemShop", isAddonEnabled("itemshop"));

        if (isAddonEnabled("antigrief")) {
            antiGriefManager = new AntiGriefManager(this);
        }
        startupLogger.addModule("AntiGrief", isAddonEnabled("antigrief"));

        if (isAddonEnabled("discordreward")) {
            discordRewardManager = new DiscordRewardManager(this);
        }
        startupLogger.addModule("Discord Reward", discordRewardManager != null && discordRewardManager.isEnabled());

        if (isAddonEnabled("automessages")) {
            autoMessageManager = new AutoMessageManager(this);
            autoMessageManager.start();
        }
        startupLogger.addModule("AutoMessage", isAddonEnabled("automessages"));

        if (isAddonEnabled("autocommands")) {
            autoCommandsManager = new AutoCommandsManager(this);
            autoCommandsManager.start();
        }
        startupLogger.addModule("AutoCommands", isAddonEnabled("autocommands"));

        if (isAddonEnabled("afk")) {
            afkManager = new AfkManager(this);
            afkManager.start();
        }
        startupLogger.addModule("AFK System", isAddonEnabled("afk"));

        if (isAddonEnabled("clearlag")) {
            clearLagManager = new ClearLagManager(this);
            clearLagManager.start();
        }
        startupLogger.addModule("ClearLag", isAddonEnabled("clearlag"));

        statsDataManager.startAutoSave();
        topManager.start();

        if (isAddonEnabled("bossbar")) {
            bossBarManager = new BossBarManager(this);
            bossBarManager.start();
        }
        startupLogger.addModule("BossBar System", isAddonEnabled("bossbar"));

        if (isAddonEnabled("afk") && isAddonEnabled("warps")) {
            idleManager = new IdleManager(this);
            idleManager.start();
        }

        if (isAddonEnabled("trener") && getCoachConfig() != null) {
            dev.grzybalson.anarchiagg.coach.CoachDatabase coachDb = new dev.grzybalson.anarchiagg.coach.CoachDatabase(this, getCoachConfig());
            coachManager = new dev.grzybalson.anarchiagg.coach.CoachManager(this, getCoachConfig(), coachDb);
            coachManager.startActionBarTask();
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.coach.listener.CoachGUIListener(coachManager, getCoachConfig()), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.coach.listener.CoachPerkListener(this, coachManager), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.coach.listener.CoachPlayerListener(coachManager), this);

            dev.grzybalson.anarchiagg.coach.CoachCommand coachCmd = new dev.grzybalson.anarchiagg.coach.CoachCommand(coachManager, getCoachConfig());
            if (getCommand("trener") != null) {
                getCommand("trener").setExecutor(coachCmd);
                getCommand("trener").setTabCompleter(coachCmd);
            }
        }
        startupLogger.addModule("Trener", isAddonEnabled("trener"));

        if (isAddonEnabled("adminitems") && getAdminItemsConfig() != null) {
            dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI.prepareCache(this);
            adminItemsInfoGUI = new dev.grzybalson.anarchiagg.adminitems.AdminItemsInfoGUI(this);
            adminItemsInfoGUI.startSnapshotTask();
            dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldowns.start(this);

            dev.grzybalson.anarchiagg.adminitems.listener.GoatListener goatListener = new dev.grzybalson.anarchiagg.adminitems.listener.GoatListener(this);
            dev.grzybalson.anarchiagg.adminitems.listener.LassoListener lassoListener = new dev.grzybalson.anarchiagg.adminitems.listener.LassoListener(this);

            Bukkit.getPluginManager().registerEvents(adminItemsInfoGUI, this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.AdminItemsCooldownListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.AdminItemsCraftListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.AdminItemsStackSanitizer(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.AntiCobwebListener(this, getAdminItemsConfig()), this);
            Bukkit.getPluginManager().registerEvents(lassoListener, this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.RodListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.PumpkinGrenadeListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.MinerCurseListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.SpikesListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.PokeBallListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.IceWandListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.PandoraBoxListener(this), this);
            Bukkit.getPluginManager().registerEvents(goatListener, this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.RejuvenationPotionListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.OlafNoseListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.EscapeChorusListener(this, goatListener), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.DestructionTridentListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.HookListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.StarChestplateListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.EnlightenmentMagnifierListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.TornadoListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.GoldenSkullListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.DragonFireworkListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.InfiniteGoldenAppleListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.KangarooBagListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.HellCardsListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.MagicCrystalListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.WuStaffListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.WinterHeartListener(this), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.DragonFangListener(this), this);

            dev.grzybalson.anarchiagg.adminitems.AdminItemsCommand adminItemsCmd = new dev.grzybalson.anarchiagg.adminitems.AdminItemsCommand(this, adminItemsInfoGUI);
            if (getCommand("adminitems") != null) {
                getCommand("adminitems").setExecutor(adminItemsCmd);
                getCommand("adminitems").setTabCompleter(adminItemsCmd);
            }
            dev.grzybalson.anarchiagg.adminitems.AdminItemsClearCommand clearCmd = new dev.grzybalson.anarchiagg.adminitems.AdminItemsClearCommand();
            if (getCommand("adminitemsclear") != null) {
                getCommand("adminitemsclear").setExecutor(clearCmd);
            }
            dev.grzybalson.anarchiagg.adminitems.ResetCooldownCommand resetCdCmd = new dev.grzybalson.anarchiagg.adminitems.ResetCooldownCommand(this);
            if (getCommand("resetujcooldown") != null) {
                getCommand("resetujcooldown").setExecutor(resetCdCmd);
            }
        }
        // Torba z /oghub-customitems musi działać nawet bez addonu adminitems.
        if (isAddonEnabled("oghub-customitems") && !isAddonEnabled("adminitems")) {
            if (dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI.KEY_TORBA_KANGURA == null) {
                dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI.KEY_TORBA_KANGURA =
                    new org.bukkit.NamespacedKey(this, "admin_item_torba_kangura");
            }
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.anarchiagg.adminitems.listener.KangarooBagListener(this), this);
        }
        startupLogger.addModule("AdminItems", isAddonEnabled("adminitems"));

        if (isAddonEnabled("mslf-szczesciarz")) {
            try {
                mslfSzczesciarzManager = new dev.grzybalson.modules.mslfszczesciarz.MslfSzczesciarzManager(this);
            } catch (Throwable throwable) {
                getLogger().log(java.util.logging.Level.SEVERE, "[HollyCore] Nie udalo sie zaladowac mslf-szczesciarz", throwable);
                mslfSzczesciarzManager = null;
            }
        }
        startupLogger.addModule("MSLF-Szczesciarz", isAddonEnabled("mslf-szczesciarz") && mslfSzczesciarzManager != null);

        if (isAddonEnabled("mslf")) {
            try {
                mslfManager = new dev.grzybalson.modules.mslf.MslfManager(this);
                dev.grzybalson.modules.mslf.MslfCommand mslfCmd = new dev.grzybalson.modules.mslf.MslfCommand(mslfManager);
                if (getCommand("mslf") != null) {
                    getCommand("mslf").setExecutor(mslfCmd);
                    getCommand("mslf").setTabCompleter(mslfCmd);
                }
            } catch (Throwable throwable) {
                getLogger().log(java.util.logging.Level.SEVERE, "[HollyCore] Nie udalo sie zaladowac MSLF", throwable);
                mslfManager = null;
            }
        }
        startupLogger.addModule("MSLF", isAddonEnabled("mslf") && mslfManager != null);

        if (isAddonEnabled("coinflip") && moduleConfigManager != null && moduleConfigManager.getCoinflipConfig() != null) {
            dev.grzybalson.config.modules.coinflip.CoinflipConfig coinflipConfig = moduleConfigManager.getCoinflipConfig();
            coinflipConfig.migrateBrokenDefaults();
            coinflipDatabase = new dev.grzybalson.modules.coinflip.CoinflipDatabase(this, coinflipConfig.getSettings().getMongoCollection(), coinflipConfig.getSettings().getGamesMongoCollection());
            coinflipManager = new dev.grzybalson.modules.coinflip.CoinflipManager(this, coinflipConfig, coinflipDatabase);
            coinflipMenuManager = new dev.grzybalson.modules.coinflip.CoinflipMenuManager(this, coinflipConfig, coinflipManager, coinflipDatabase);
            coinflipManager.setMenuManager(coinflipMenuManager);

            Bukkit.getPluginManager().registerEvents(coinflipMenuManager, this);

            dev.grzybalson.modules.coinflip.CoinflipCommand coinflipCmd = new dev.grzybalson.modules.coinflip.CoinflipCommand(coinflipManager, coinflipMenuManager, coinflipConfig);
            registerCommandExecutor("coinflip", coinflipCmd);
        }
        startupLogger.addModule("Coinflip", isAddonEnabled("coinflip"));

        if (isAddonEnabled("zywioly")) {
            try {
                zywiolyManager = new dev.grzybalson.modules.zywioly.ZywiolyManager(this);
                dev.grzybalson.modules.zywioly.ZywiolyCommand zywiolyCmd = new dev.grzybalson.modules.zywioly.ZywiolyCommand(zywiolyManager);
                registerCommandExecutor("zywioly", zywiolyCmd);
                registerCommandTabCompleter("zywioly", zywiolyCmd);
            } catch (Throwable throwable) {
                getLogger().log(java.util.logging.Level.SEVERE, "[HollyCore] Nie udalo sie zaladowac zywiołów", throwable);
                zywiolyManager = null;
            }
        }
        startupLogger.addModule("Żywioły", isAddonEnabled("zywioly") && zywiolyManager != null);

        if (isAddonEnabled("killeffect")) {
            killEffectManager = new dev.grzybalson.modules.killeffect.KillEffectManager(this);
            Bukkit.getPluginManager().registerEvents(killEffectManager, this);
            registerCommandExecutor("killeffect", killEffectManager);
            registerCommandTabCompleter("killeffect", killEffectManager);
        }
        startupLogger.addModule("KillEffect", isAddonEnabled("killeffect"));

        startupLogger.addModule("Idle/AFK Teleport", isAddonEnabled("afk") && isAddonEnabled("warps"));

        if (isAddonEnabled("codes")) {
            codeManager = new CodeManager(this);
            rankGiveawayManager = new RankGiveawayManager(this);
        }
        startupLogger.addModule("Kody", isAddonEnabled("codes"));

        // MSLF ma wlasny lekki EC — nie lacz z ciezkim addonem enderchest.
        boolean mslfOwnsEc = isAddonEnabled("mslf")
            && mslfManager != null
            && mslfManager.enderChestService() != null;
        if (isAddonEnabled("enderchest") && !mslfOwnsEc) {
            try {
                enderChestManager = new EnderChestManager(this);
            } catch (IllegalStateException | IllegalArgumentException ex) {
                enderChestManager = null;
                getLogger().severe("[EnderChest] Wyłączony — Mongo niedostępne: " + ex.getMessage());
            }
        }
        startupLogger.addModule("EnderChest", enderChestManager != null);
        startupLogger.addModule("MSLF EnderChest", mslfOwnsEc);

        if (isAddonEnabled("itemsync")) {
            inventorySyncManager = new InventorySyncManager(this);
            inventorySyncManager.start();
        }
        startupLogger.addModule("ItemSync", isAddonEnabled("itemsync") && inventorySyncManager != null);

        if (isAddonEnabled("creatormode")) {
            creatorModeManager = new CreatorModeManager(this);
        }
        startupLogger.addModule("Tryb Twórcy", isAddonEnabled("creatormode"));

        if (isAddonEnabled("clans")) {
            clanArmorManager = new dev.grzybalson.modules.clan.ClanArmorManager(this);
            clanArmorManager.init();
        }
        startupLogger.addModule("Clan Armor (PacketEvents)", clanArmorManager != null && clanArmorManager.isEnabled());

        if (isAddonEnabled("backup")) {
            backupManager = new BackupManager(this);
            deathKeepListener = new dev.grzybalson.modules.deathkeep.DeathKeepListener(this);
            Bukkit.getPluginManager().registerEvents(deathKeepListener, this);
            backupManager.start();
            rollbackManager = new dev.grzybalson.modules.backup.RollbackManager(this, backupManager.getDataManager());
        }
        startupLogger.addModule("Backup System", isAddonEnabled("backup"));

        killstreakManager = new KillstreakManager(this);
        startupLogger.addModule("Killstreak System", true);

        if (isAddonEnabled("achievement")) {
            achievementManager = new AchievementManager(this);
            achievementListener = new AchievementListener(this);
            if (natsService != null && natsService.isConnected()) {
                natsService.subscribe(NatsService.ACHIEVEMENT_CLAIM, doc -> {
                    try {
                        UUID uuid = UUID.fromString(doc.getString("uuid"));
                        AchievementType type = AchievementType.valueOf(doc.getString("type"));
                        int tier = doc.getInteger("tier", 0);
                        int sourcePort = doc.getInteger("port", -1);
                        if (sourcePort != getServer().getPort() && tier > 0) {
                            achievementManager.applyRemoteClaim(uuid, type, tier);
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        startupLogger.addModule("Achievements", isAddonEnabled("achievement"));

        if (isAddonEnabled("check")) {
            checkManager = new CheckManager(this);
        }
        startupLogger.addModule("Check System", isAddonEnabled("check"));
        startupLogger.addModule("Limit IP", isAddonEnabled("iplimit"));
        startupLogger.addModule("FancyNPC Push",
            isAddonEnabled("fancynpc-push") && Bukkit.getPluginManager().getPlugin("FancyNpcs") != null);

        boolean papiHooked = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papiHooked) {
            hollyPlaceholderExpansion = new HollyPlaceholderExpansion(this);
            boolean registered = hollyPlaceholderExpansion.register();
            boolean coreRegistered = new CorePlaceholderExpansion(hollyPlaceholderExpansion).register();
            Bukkit.getPluginManager().registerEvents(hollyPlaceholderExpansion, this);
            boolean walletRegistered = isWalletAddonEnabled() && walletManager != null && new PortfelPlaceholderExpansion(this).register();
            new dev.grzybalson.placeholders.TotempopPlaceholderExpansion(this).register();
            new HollyServicePlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered: holly=" + registered + ", core=" + coreRegistered + ", wallet=" + walletRegistered);
        }
        startupLogger.addModule("PlaceholderAPI", papiHooked);

        if (isAddonEnabled("tablist")) {
            sidebarManager = new SidebarManager(this);
            sidebarManager.start();
            incognitoManager = new IncognitoManager(this);
            incognitoManager.start();
            tablistManager = new TablistManager(this);
            tablistManager.start();
        }
        spooferLoadTestSimulator = new SpooferLoadTestSimulator(this);
        spooferLoadTestSimulator.start();
        if (isAddonEnabled("worldborder")) {
            worldBorderManager = new WorldBorderManager(this);
            worldBorderManager.start();
        }
        if (isAddonEnabled("renderdistance")) {
            renderDistanceManager = new RenderDistanceManager(this);
            renderDistanceManager.start();
        }
        if (isAddonEnabled("worlds")) {
            worldManager = new dev.grzybalson.modules.worlds.WorldManager(this);
            worldManager.start();
        }
        // Tops po WorldManager — worldy z /world musza byc zaladowane przed spawnem NPC.
        if (isAddonEnabled("tops")) {
            topsManager = new dev.grzybalson.modules.tops.TopsManager(this);
            topsManager.init();
        }
        if (isAddonEnabled("groupteleport")) {
            groupTeleportManager = new GroupTeleportManager(this);
        }
        if (isAddonEnabled("boss")) {
            bossManager = new BossManager(this);
            bossManager.start();
        }
        if (isAddonEnabled("moderation")) {
            moderationManager = new ModerationManager(this);
        }
        if (isAddonEnabled("report") || isAddonEnabled("combat")) {
            reportStore = new ReportStore(this);
        }
        startupLogger.addModule("Tablist Scoreboard", isAddonEnabled("tablist") && sidebarManager != null);
        startupLogger.addModule("Tablist", isAddonEnabled("tablist") && tablistManager != null);
        startupLogger.addModule("WorldBorder", isAddonEnabled("worldborder") && worldBorderManager != null);
        startupLogger.addModule("RenderDistance", isAddonEnabled("renderdistance") && renderDistanceManager != null && renderDistanceManager.isOperational());
        startupLogger.addModule("Worlds (/world)", isAddonEnabled("worlds") && worldManager != null);
        startupLogger.addModule("Tops NPC", isAddonEnabled("tops") && topsManager != null);
        startupLogger.addModule("GroupTeleport", isAddonEnabled("groupteleport") && groupTeleportManager != null);
        startupLogger.addModule("Boss", isAddonEnabled("boss") && bossManager != null);
        startupLogger.addModule("Moderation", isAddonEnabled("moderation") && moderationManager != null);
        startupLogger.addModule("FastReport", reportStore != null);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        registerCommands();
        registerListeners();
        startupLogger.progress("CORE", 70);
        // Po rejestracji komend jeszcze raz zdejmujemy wyłączone addony z CommandMap.
        // Dzięki temu przy addons.yml: false komenda zachowuje się tak, jakby w ogóle nie istniała
        // — nie działa, nie pokazuje usage z plugin.yml i znika z TAB-a.
        unregisterDisabledAddonCommands();
        if (flameHubCustomItemsManager != null) {
            restorePluginCommands(List.of("flameitems", "flamereset", "flamereload"));
        }

        if (isAddonEnabled("anarchiagg") && anarchiaGGConfigManager != null) {
            anarchiaGGModuleManager = new AnarchiaGGModuleManager(this, anarchiaGGConfigManager.getModulesConfig());
            anarchiaGGModuleManager.enableAll();
            startupLogger.addModule("AnarchiaGG Eventówki", anarchiaGGConfigManager.getModulesConfig().isEventowki());
            startupLogger.addModule("AnarchiaGG Szafka", anarchiaGGConfigManager.getModulesConfig().isSzafka());
            startupLogger.addModule("AnarchiaGG Kowal", anarchiaGGConfigManager.getModulesConfig().isKowal());
            startupLogger.addModule("AnarchiaGG Księgi", anarchiaGGConfigManager.getModulesConfig().isKsiegi());
            startupLogger.addModule("AnarchiaGG Zmianki", anarchiaGGConfigManager.getModulesConfig().isZmianki());
        } else {
            startupLogger.addModule("AnarchiaGG Eventówki", false);
            startupLogger.addModule("AnarchiaGG Szafka", false);
            startupLogger.addModule("AnarchiaGG Kowal", false);
            startupLogger.addModule("AnarchiaGG Księgi", false);
            startupLogger.addModule("AnarchiaGG Zmianki", false);
        }

        // Szafka OGHub musi powstac po AnarchiaGG, zeby przejac komendy /szafka i /aszafka.
        if (isAddonEnabled("oghub-szafka")) {
            if (isAnarchiaGGSzafkaEnabled()) {
                getLogger().warning("Addon oghub-szafka jest wlaczony razem z szafka AnarchiaGG - komendy /szafka i /aszafka przejmuje oghub-szafka.");
            }
            ogHubSzafkaManager = new OgHubSzafkaManager(this);
        }
        startupLogger.addModule("oghub-szafka", isAddonEnabled("oghub-szafka") && ogHubSzafkaManager != null);

        if (isAddonEnabled("mslf-kostiumy")) {
            try {
                mslfKostiumyManager = new dev.grzybalson.modules.mslfkostiumy.MslfKostiumyManager(this);
            } catch (Throwable throwable) {
                getLogger().log(java.util.logging.Level.SEVERE, "[HollyCore] Nie udalo sie zaladowac mslf-kostiumy", throwable);
                mslfKostiumyManager = null;
            }
        }
        startupLogger.addModule("MSLF-Kostiumy", isAddonEnabled("mslf-kostiumy") && mslfKostiumyManager != null);

        if (isAddonEnabled(dev.grzybalson.modules.masakraszafka.MasakraSzafkaManager.ADDON_NAME)) {
            if (isAnarchiaGGSzafkaEnabled() || isAddonEnabled("oghub-szafka") || isAddonEnabled("mslf-kostiumy")) {
                getLogger().warning("Addon masakra-szafka jest wlaczony razem z inna szafka - komendy /szafka i /aszafka przejmuje masakra-szafka.");
            }
            try {
                masakraSzafkaManager = new dev.grzybalson.modules.masakraszafka.MasakraSzafkaManager(this);
            } catch (Throwable throwable) {
                getLogger().log(java.util.logging.Level.SEVERE, "[HollyCore] Nie udalo sie zaladowac masakra-szafka", throwable);
                masakraSzafkaManager = null;
            }
        }
        startupLogger.addModule("Masakra-Szafka", masakraSzafkaManager != null);

        // Zmianki OGHub — osobny addon (przejmuje /zmianki gdy wlaczony).
        if (isAddonEnabled("oghubzmianki")) {
            if (anarchiaGGConfigManager != null && anarchiaGGConfigManager.getModulesConfig() != null
                && anarchiaGGConfigManager.getModulesConfig().isZmianki()) {
                getLogger().warning("Addon oghubzmianki jest wlaczony razem z anarchiagg.zmianki — komendy przejmuje oghubzmianki.");
            }
            ogHubZmiankiManager = new OgHubZmiankiManager(this);
        }
        startupLogger.addModule("oghubzmianki", isAddonEnabled("oghubzmianki") && ogHubZmiankiManager != null);

        new Metrics(this, BSTATS_PLUGIN_ID);
        startupLogger.addModule("bStats", true);


        if (dev.grzybalson.core.HollyLiveReload.isActive()) {
            dev.grzybalson.core.HollyLiveReload.schedulePostEnableWarmup(this);
        } else {
            // OPTYMALIZACJA: Lazy loading - cache warming w tle po 100 tickach (5s), nie blokuje startu
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (achievementManager != null) {
                    Bukkit.getOnlinePlayers().forEach(player -> achievementManager.loadPlayer(player.getUniqueId()));
                }
                if (missionManager != null) {
                    Bukkit.getOnlinePlayers().forEach(player -> missionManager.loadPlayer(player.getUniqueId()));
                }
            }, 100L);
        }

        HollyIntegrityGuard.requireStrong(this, "startup:ready");
        // Finale + wpuszczenie graczy dopiero gdy serwer domknie ładowanie WSZYSTKICH pluginów.
        armStartupFinaleAfterAllPlugins();
    }

    /**
     * StartupLogger i {@link CoreStartupGate#markReady()} dopiero po {@link org.bukkit.event.server.ServerLoadEvent}
     * (wszystkie pluginy włączone). Do tego czasu join jest zablokowany.
     */
    private void armStartupFinaleAfterAllPlugins() {
        final java.util.concurrent.atomic.AtomicBoolean finaleArmed =
            new java.util.concurrent.atomic.AtomicBoolean(false);

        Runnable finish = () -> {
            if (!finaleArmed.compareAndSet(false, true)) {
                return;
            }
            if (startupLogger == null) {
                CoreStartupGate.markReady();
                return;
            }
            startupLogger.printStartup();
            startupLogger.playStartupFinale();
        };

        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onServerLoad(org.bukkit.event.server.ServerLoadEvent event) {
                finish.run();
            }
        }, this);

        // Late-enable / /reload gdy STARTUP już było — domknij w następnym ticku
        // (na zimnym starcie ServerLoadEvent zdąży wcześniej i ten fallback nic nie zrobi).
        Bukkit.getScheduler().runTask(this, () -> {
            if (!finaleArmed.get()) {
                finish.run();
            }
        });

        // Bezpiecznik: gdy coś zawiesi finale, i tak otwórz join.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!CoreStartupGate.isReady()) {
                getLogger().warning("StartupLogger finale timeout — odblokowuję join.");
                finish.run();
                if (!CoreStartupGate.isReady()) {
                    CoreStartupGate.markReady();
                }
            }
        }, 1200L);
    }

    @Override
    public void onDisable() {
        CoreStartupGate.markNotReady();
        if (piniataManager != null) {
            piniataManager.shutdown();
        }
        if (dungeonSessionManager != null) {
            dungeonSessionManager.shutdownAll();
        }
        if (bossBarManager != null) {
            bossBarManager.stop();
        }
        if (sidebarManager != null) {
            sidebarManager.shutdown();
        }
        if (tablistManager != null) {
            if (dev.grzybalson.core.HollyLiveReload.isActive()) {
                tablistManager.softShutdown();
            } else {
                tablistManager.shutdown();
            }
        }
        if (worldBorderManager != null) {
            worldBorderManager.shutdown();
        }
        if (renderDistanceManager != null) {
            renderDistanceManager.shutdown();
        }
        if (autoMessageManager != null) {
            autoMessageManager.stop();
        }
        if (autoCommandsManager != null) {
            autoCommandsManager.stop();
        }
        if (afkManager != null) {
            afkManager.stop();
        }
        if (clearLagManager != null) {
            clearLagManager.stop();
        }
        if (noEnvironmentalDamageListener != null) {
            noEnvironmentalDamageListener.stop();
        }
        if (worldBorderHitFixListener != null) {
            worldBorderHitFixListener.stop();
            worldBorderHitFixListener = null;
        }
        if (turboDropManager != null) {
            turboDropManager.stopTurboDrop();
        }
        if (statsDataManager != null) {
            statsDataManager.stopAutoSave();
            statsDataManager.saveActiveSessions(false);
        }
        if (topsManager != null) {
            topsManager.shutdown();
        }
        if (sigilManager != null) {
            sigilManager.shutdown();
        }
        if (topManager != null) {
            topManager.stop();
        }
        if (vanishManager != null) {
            vanishManager.shutdown();
        }
        if (combatManager != null) {
            combatManager.shutdown();
        }
        if (pickupManager != null) {
            pickupManager.shutdown();
        }
        if (idleManager != null) {
            idleManager.stop();
        }
        if (codeManager != null) {
            codeManager.shutdown();
        }
        if (rankGiveawayManager != null) {
            rankGiveawayManager.shutdown();
        }
        if (autoTitleManager != null) {
            autoTitleManager.shutdown();
        }
        if (restartManager != null) {
            restartManager.shutdown();
        }
        if (warpManager != null) {
            warpManager.shutdown();
        }
        if (teleportAnimator != null) {
            teleportAnimator.shutdown();
        }
        if (homeManager != null) {
            homeManager.shutdown();
        }
        if (creatorModeManager != null) {
            creatorModeManager.shutdown();
        }
        if (actionBarManager != null) {
            actionBarManager.shutdown();
        }
        MultiActionBarService.shutdownGlobal();
        if (clanArmorManager != null) {
            clanArmorManager.shutdown();
        }
        if (backupManager != null) {
            backupManager.shutdown();
        }
        if (achievementManager != null && !dev.grzybalson.core.HollyLiveReload.isActive()) {
            achievementManager.saveAll();
        }
        if (endModuleListener != null) {
            endModuleListener.shutdown();
        }
        if (antiGriefManager != null) {
            antiGriefManager.shutdown();
        }
        if (szamanManager != null) {
            Bukkit.getOnlinePlayers().forEach(szamanManager::removeLiveHealth);
        }
        if (vaultHook != null) {
            vaultHook.unhook();
        }
        if (anarchiaGGModuleManager != null) {
            anarchiaGGModuleManager.disableAll();
        }
        if (ogHubSzafkaManager != null) {
            ogHubSzafkaManager.disable();
        }
        if (mslfKostiumyManager != null) {
            mslfKostiumyManager.disable();
        }
        if (masakraSzafkaManager != null) {
            masakraSzafkaManager.disable();
        }
        if (mslfManager != null) {
            mslfManager.disable();
        }
        if (ogHubZmiankiManager != null) {
            ogHubZmiankiManager.disable();
        }
        if (licenseSlotLimiter != null) {
            licenseSlotLimiter.stop();
        }
        if (mobsManager != null) {
            mobsManager.stop();
        }
        if (coinflipManager != null) {
            coinflipManager.shutdown();
        }
        if (zywiolyManager != null) {
            zywiolyManager.shutdown();
        }
        if (tradeManager != null) {
            tradeManager.shutdown();
        }

        if (caseManager != null) {
            caseManager.shutdown();
        }
        if (patronManager != null) {
            patronManager.shutdown();
        }
        if (minestarOGCustomItemsManager != null) {
            minestarOGCustomItemsManager.shutdown();
        }
        if (masakraCustomItemsManager != null) {
            masakraCustomItemsManager.shutdown();
        }
        if (ogHubCustomItemsManager != null) {
            ogHubCustomItemsManager.shutdown();
        }
        if (flameHubCustomItemsManager != null) {
            flameHubCustomItemsManager.shutdown();
        }
        if (generatorManager != null) {
            generatorManager.shutdown();
        }
        KillTitleAnimator.shutdown();
        if (discordRewardManager != null) {
            discordRewardManager.shutdown();
        }
        if (economy != null && economy.getDataManager() != null) {
            if (dev.grzybalson.core.HollyLiveReload.isActive()) {
                economy.getDataManager().flushNow();
            } else {
                economy.getDataManager().close();
            }
        }
        if (anticheatManager != null) {
            anticheatManager.shutdown();
        }
        if (missionManager != null) {
            missionManager.getDataManager().shutdown();
        }
        if (uniqueItemService != null) {
            uniqueItemService.shutdown();
        }
        if (inventorySyncManager != null) {
            inventorySyncManager.shutdown();
        }
        if (sectorService != null) {
            sectorService.shutdown();
        }
        if (serverDiagnosticsService != null) {
            serverDiagnosticsService.shutdown();
        }
        if (spooferLoadTestSimulator != null) {
            spooferLoadTestSimulator.stop();
        }
        if (groupTeleportManager != null) {
            groupTeleportManager.shutdown();
        }
        if (bossManager != null) {
            bossManager.shutdown();
        }
        if (moderationManager != null) {
            moderationManager.shutdown();
        }
        if (shulkerBoxListener != null) {
            shulkerBoxListener.shutdown();
        }
        if (natsService != null) {
            natsService.shutdown();
        }
        if (joinInfoManager != null) {
            joinInfoManager.shutdown();
        }
        if (mongoManager != null) {
            mongoManager.disconnect();
        }
        HollyIntegrityGuard.stopWatchdog();
        try {
            if (com.github.retrooper.packetevents.PacketEvents.getAPI() != null && com.github.retrooper.packetevents.PacketEvents.getAPI().isInitialized()) {
                com.github.retrooper.packetevents.PacketEvents.getAPI().terminate();
            }
        } catch (Throwable t) {
            getLogger().warning("PacketEvents terminate warning in onDisable: " + t.getMessage());
        }
        if (startupLogger != null) {
            startupLogger.printShutdown();
        }
    }

    private void registerCommands() {
        HollyIntegrityGuard.requireBound(this, "commands:register");
        CoreCommand coreCommand = new CoreCommand(this);
        registerCommandExecutor("core", coreCommand);
        registerCommandTabCompleter("core", coreCommand);

        AdminCommand adminCommand = new AdminCommand(this);
        registerCommandExecutor("admin", adminCommand);
        registerCommandTabCompleter("admin", adminCommand);

        GuiCommand guiCommand = new GuiCommand(this);
        registerCommandExecutor("guis", guiCommand);
        registerCommandTabCompleter("guis", guiCommand);

        GetbackCommand getbackCommand = new GetbackCommand(this);
        registerCommandExecutor("getback", getbackCommand);
        registerCommandTabCompleter("getback", getbackCommand);
        Bukkit.getPluginManager().registerEvents(new dev.grzybalson.listeners.GetbackGUIListener(), this);

        if (isAddonEnabled("renderdistance") && renderDistanceManager != null) {
            RenderDistanceCommand renderDistanceCommand = new RenderDistanceCommand(renderDistanceManager);
            registerCommandExecutor("renderdistance", renderDistanceCommand);
            registerCommandTabCompleter("renderdistance", renderDistanceCommand);
        }

        if (isAddonEnabled("armorstars")) {
            ArmorStarsCommand armorStarsCommand = new ArmorStarsCommand(this);
            registerCommandExecutor("armorstars", armorStarsCommand);
            registerCommandTabCompleter("armorstars", armorStarsCommand);
        }

        if (isAddonEnabled("cases") && caseManager != null) {
            CaseCommand caseCommand = new CaseCommand(caseManager);
            registerCommandExecutor("case", caseCommand);
            registerCommandTabCompleter("case", caseCommand);
        }

        if (isAddonEnabled("commands")) {
        registerCommandExecutor("tp", new TpCommand(this));
        if (isAddonEnabled("tpa")) {
            TpaCommand tpaCommand = new TpaCommand(this);
            registerCommandExecutor("tpa", tpaCommand);
            registerCommandTabCompleter("tpa", tpaCommand);
        }
        registerCommandExecutor("tphere", new TpHereCommand(this));
        registerCommandExecutor("s", new TpHereCommand(this));
        registerCommandExecutor("tpaccept", new TpAcceptCommand(this));
        registerCommandExecutor("tpdeny", new TpDenyCommand(this));
        GamemodeCommand gamemodeCommand = new GamemodeCommand(this);
        registerCommandExecutor("gm", gamemodeCommand);
        registerCommandTabCompleter("gm", gamemodeCommand);
        registerCommandExecutor("flyspeed", new FlySpeedCommand(this));
        registerCommandExecutor("fly", new FlyCommand(this));
        BroadcastCommand broadcastCommand = new BroadcastCommand(this);
        registerCommandExecutor("bc", broadcastCommand);
        registerCommandTabCompleter("bc", broadcastCommand);
        autoTitleManager = new AutoTitleManager(this);
        AutoTitleCommand autoTitleCommand = new AutoTitleCommand(this);
        registerCommandExecutor("autotitle", autoTitleCommand);
        registerCommandTabCompleter("autotitle", autoTitleCommand);
        TitleCommand titleCommand = new TitleCommand(this);
        registerCommandExecutor("title", titleCommand);
        registerCommandTabCompleter("title", titleCommand);
        }
        if (isAddonEnabled("enderchest") && enderChestManager != null) {
            EnderchestCommand enderchestCommand = new EnderchestCommand(this);
            registerCommandExecutor("ec", enderchestCommand);
            registerCommandExecutor("enderchest", enderchestCommand);
        } else {
            // Bez ciezkiego addonu /ec ma i tak dzialac — komenda sama wybiera backend (MSLF EC, potem vanilla).
            dev.grzybalson.modules.mslf.MslfEnderchestCommand mslfEc =
                new dev.grzybalson.modules.mslf.MslfEnderchestCommand(this);
            registerCommandExecutor("ec", mslfEc);
            registerCommandExecutor("enderchest", mslfEc);
        }
        warnIfCommandTakenByOtherPlugin("ec");
        warnIfCommandTakenByOtherPlugin("enderchest");
        if (inventorySyncManager != null) {
            InventorySyncCommand inventorySyncCommand = new InventorySyncCommand(this, inventorySyncManager);
            registerCommandExecutor("save-all", inventorySyncCommand);
            registerCommandExecutor("saveall", inventorySyncCommand);
            registerCommandTabCompleter("save-all", inventorySyncCommand);
            registerCommandTabCompleter("saveall", inventorySyncCommand);
        }
        if (isAddonEnabled("commands")) {
        if (isAddonEnabled("spawn")) {
            registerCommandExecutor("setspawn", new SetSpawnCommand(this));
            spawnCommand = new SpawnCommand(this);
            registerCommandExecutor("spawn", spawnCommand);
        }
        MsgCommand msgCommand = new MsgCommand(this);
        registerCommandExecutor("msg", msgCommand);
        registerCommandTabCompleter("msg", msgCommand);
        registerCommandExecutor("r", new ReplyCommand(this));
        registerCommandExecutor("reply", new ReplyCommand(this));
        }
        if (isAddonEnabled("economy")) {
            registerCommandExecutor("eco", new EcoCommand(this));
            registerCommandExecutor("money", new MoneyCommand(this));
            PayCommand payCommand = new PayCommand(this);
            registerCommandExecutor("pay", payCommand);
            registerCommandTabCompleter("pay", payCommand);
        }
        if (isAddonEnabled("shop")) {
            ShopCommand shopCommand = new ShopCommand(this);
            registerCommandExecutor("sklep", shopCommand);
            registerCommandTabCompleter("sklep", shopCommand);
            registerCommandExecutor("skup", shopCommand);
            registerCommandTabCompleter("skup", shopCommand);
            registerCommandExecutor("asklep", new AdminShopCommand(this));
        }
        if (isAddonEnabled("upgrade")) {
            UpgradeCommand upgradeCommand = new UpgradeCommand(this);
            registerCommandExecutor("ulepsz", upgradeCommand);
            registerCommandTabCompleter("ulepsz", upgradeCommand);

            ArmorCreateCommand armorCreateCommand = new ArmorCreateCommand(this);
            registerCommandExecutor("armorcreate", armorCreateCommand);
            registerCommandTabCompleter("armorcreate", armorCreateCommand);
        }
        if (isAddonEnabled("commands")) {
            registerCommandExecutor("usunlore", new RemoveLoreCommand());
            SkullCommand skullCommand = new SkullCommand(this);
            registerCommandExecutor("skull", skullCommand);
            registerCommandTabCompleter("skull", skullCommand);
        }
        if (isAddonEnabled("patroni") && patronManager != null) {
            PatronCommand patronCommand = new PatronCommand(this);
            registerCommandExecutor("patron", patronCommand);
            registerCommandTabCompleter("patron", patronCommand);
        }
        registerCommandExecutor("misje", new MisjeCommand(this));
        if (dropKeysManager != null) {
            DropKeysCommand dropKeysCommand = new DropKeysCommand(this);
            registerCommandExecutor("dropkeys", dropKeysCommand);
            registerCommandTabCompleter("dropkeys", dropKeysCommand);
        }
        if (isAddonEnabled("zonebox") && zoneBoxManager != null) {
            ZoneBoxCommand zoneBoxCommand = new ZoneBoxCommand(zoneBoxManager);
            registerCommandExecutor("zonebox", zoneBoxCommand);
            registerCommandTabCompleter("zonebox", zoneBoxCommand);

            if (isAddonEnabled("blockseconomy")) {
                BlockEconomyCommand blockEconomyCommand = new BlockEconomyCommand(this, zoneBoxManager);
                registerCommandExecutor("blockseconomy", blockEconomyCommand);
                registerCommandTabCompleter("blockseconomy", blockEconomyCommand);
                BlockEconomyGUI.registerListener(this);
                DrwalCommand drwalCommand = new DrwalCommand(this, zoneBoxManager);
                registerCommandExecutor("drwal", drwalCommand);
                Bukkit.getPluginManager().registerEvents(drwalCommand, this);

                if (turboDropManager != null) {
                    dev.grzybalson.commands.TurboDropCommand turboDropCommand = new dev.grzybalson.commands.TurboDropCommand(this, turboDropManager);
                    registerCommandExecutor("turbodrop", turboDropCommand);
                    registerCommandTabCompleter("turbodrop", turboDropCommand);
                }
            }
        }
        if (isAddonEnabled("szaman") && szamanManager != null) {
            SzamanCommand szamanCommand = new SzamanCommand(this);
            registerCommandExecutor("szaman", szamanCommand);
            registerCommandTabCompleter("szaman", szamanCommand);
        }
        if (isAddonEnabled("sigil") && sigilManager != null) {
            SigilCommand sigilCommand = new SigilCommand(this);
            registerCommandExecutor("sigil", sigilCommand);
            registerCommandTabCompleter("sigil", sigilCommand);
        }
        if (isAddonEnabled("itemshop")) {
            dev.grzybalson.modules.itemshop.ItemShopCommand itemShopCommand = new dev.grzybalson.modules.itemshop.ItemShopCommand(this);
            this.itemShopCommand = itemShopCommand;
            registerCommandExecutor("itemshop", itemShopCommand);
            registerCommandTabCompleter("itemshop", itemShopCommand);
        }
        if (isAddonEnabled("trade") && tradeManager != null) {
            dev.grzybalson.modules.trade.TradeCommand tradeCommand = new dev.grzybalson.modules.trade.TradeCommand(this);
            registerCommandExecutor("trade", tradeCommand);
            registerCommandTabCompleter("trade", tradeCommand);
            registerCommandExecutor("wymiana", tradeCommand);
            registerCommandTabCompleter("wymiana", tradeCommand);
        }
        if (isAddonEnabled("itemstorage") && itemStorageManager != null) {
            registerCommandExecutor("itemstorage", new dev.grzybalson.modules.itemstorage.ItemStorageCommand(this));
        }
        if (isAddonEnabled("commands")) {
            registerCommandExecutor("odbierz", new PickupCommand(this));
        }
        if (isAddonEnabled("czek")) {
            dev.grzybalson.commands.CzekCommand czekCommand = new dev.grzybalson.commands.CzekCommand(this);
            registerCommandExecutor("czek", czekCommand);
            registerCommandTabCompleter("czek", czekCommand);
            Bukkit.getPluginManager().registerEvents(czekCommand, this);
        }

        if (isAddonEnabled("messages")) registerCommandExecutor("ignore", new IgnoreCommand(this));
        if (isAddonEnabled("afk")) registerCommandExecutor("afk", new AfkCommand(this));
        if (isAddonEnabled("afk")) {
            TurboAfkCommand turboAfkCommand = new TurboAfkCommand(this);
            registerCommandExecutor("turboafk", turboAfkCommand);
            registerCommandTabCompleter("turboafk", turboAfkCommand);
        }
        if (isAddonEnabled("clearlag")) registerCommandExecutor("clearlag", new ClearLagCommand(this));
        if (isAddonEnabled("commands")) {
            ChCommand chCommand = new ChCommand(this);
            registerCommandExecutor("ch", chCommand);
            registerCommandTabCompleter("ch", chCommand);
            registerCommandExecutor("sector", chCommand);
            registerCommandTabCompleter("sector", chCommand);
            registerCommandExecutor("sektor", chCommand);
            registerCommandTabCompleter("sektor", chCommand);
            registerCommandExecutor("sektory", chCommand);
            registerCommandTabCompleter("sektory", chCommand);

            HelpopCommand helpopCommand = new HelpopCommand(this);
            registerCommandExecutor("helpop", helpopCommand);
            registerCommandTabCompleter("helpop", helpopCommand);
            AdminChatCommand adminChatCommand = new AdminChatCommand(this);
            registerCommandExecutor("adminchat", adminChatCommand);
            registerCommandTabCompleter("adminchat", adminChatCommand);
        }
        if (isAddonEnabled("chat")) {
            ChatCommand chatCommand = new ChatCommand(this);
            registerCommandExecutor("chat", chatCommand);
            registerCommandTabCompleter("chat", chatCommand);
            LpcCommand lpcCommand = new LpcCommand(this);
            registerCommandExecutor("lpc", lpcCommand);
            registerCommandTabCompleter("lpc", lpcCommand);
        }
        registerCommandExecutor("ustawienia", new UstawieniaCommand(this));

        if (isAddonEnabled("event")) {
            try {
                eventManager = new EventManager(this);
                dungeonSessionManager = new dev.grzybalson.modules.event.dungeon.DungeonSessionManager(this, eventManager);
                eventManager.setDungeonSessions(dungeonSessionManager);
                EventCommand eventCommand = new EventCommand(this, eventManager);
                registerCommandExecutor("event", eventCommand);
                registerCommandTabCompleter("event", eventCommand);
                ADungeonCommand aDungeonCommand = new ADungeonCommand(this, eventManager, dungeonSessionManager);
                registerCommandExecutor("adungeon", aDungeonCommand);
                registerCommandTabCompleter("adungeon", aDungeonCommand);
            } catch (Throwable throwable) {
                getLogger().log(java.util.logging.Level.SEVERE, "[HollyCore] Nie udalo sie zaladowac addonu event/adungeon", throwable);
                eventManager = null;
                dungeonSessionManager = null;
            }
        }

        if (piniataManager != null) {
            PiniataCommand piniataCommand = new PiniataCommand(this, piniataManager);
            registerCommandExecutor("piniata", piniataCommand);
            registerCommandTabCompleter("piniata", piniataCommand);
            registerCommandExecutor("adminevent", piniataCommand);
            registerCommandTabCompleter("adminevent", piniataCommand);
        }

        if (isAddonEnabled("commands") || isAddonEnabled("chat")) {
            registerCommandExecutor("hollyplayeraction", new PlayerActionCommand(this));
        }
        if (isAddonEnabled("commands")) {
            RozdajAdminCommand rozdajCmd = new RozdajAdminCommand(this);
            registerCommandExecutor("rozdaj", rozdajCmd);
            registerCommandTabCompleter("rozdaj", rozdajCmd);
            registerCommandExecutor("rozdajadmin", rozdajCmd);
            registerCommandTabCompleter("rozdajadmin", rozdajCmd);
        }
        if (isAddonEnabled("commands")) {
            VanishCommand vanishCmd = new VanishCommand(this);
            registerCommandExecutor("vanish", vanishCmd);
            registerCommandTabCompleter("vanish", vanishCmd);
        }

        if (isAddonEnabled("clans")) {
            ClanCommand clanCommand = new ClanCommand(this);
            registerCommandExecutor("klan", clanCommand);
            registerCommandTabCompleter("klan", clanCommand);

            ResetujRankingCommand resetujRankingCommand = new ResetujRankingCommand(this);
            registerCommandExecutor("resetujranking", resetujRankingCommand);
        }

        if (isAddonEnabled("commands")) {
            RestartAdminCommand restartAdminCommand = new RestartAdminCommand(this);
            registerCommandExecutor("restartadmin", restartAdminCommand);
            registerCommandTabCompleter("restartadmin", restartAdminCommand);
        }

        if (isAddonEnabled("kit")) {
            KitCommand kitCommand = new KitCommand(this);
            registerCommandExecutor("kit", kitCommand);
            registerCommandTabCompleter("kit", kitCommand);

            AdminKitCommand adminKitCommand = new AdminKitCommand(this);
            registerCommandExecutor("akit", adminKitCommand);
            registerCommandTabCompleter("akit", adminKitCommand);

            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.kit.KitListener(this), this);
        }

        if (isAddonEnabled("tops") && topsManager != null) {
            dev.grzybalson.modules.tops.TopsCommand topsCommand = new dev.grzybalson.modules.tops.TopsCommand(topsManager);
            registerCommandExecutor("tops", topsCommand);
            registerCommandTabCompleter("tops", topsCommand);
        }

        if (isAddonEnabled("market")) {
            MarketCommand marketCommand = new MarketCommand(this);
            registerCommandExecutor("rynek", marketCommand);
            registerCommandTabCompleter("rynek", marketCommand);
            registerCommandExecutor("wystaw", marketCommand);
            registerCommandTabCompleter("wystaw", marketCommand);
            registerCommandExecutor("amarket", marketCommand);
            registerCommandTabCompleter("amarket", marketCommand);
        }

        if (isMobsFeatureEnabled()) {
            MobsCommand mobsCommand = new MobsCommand(this);
            registerCommandExecutor("mobs", mobsCommand);
            registerCommandTabCompleter("mobs", mobsCommand);
        }

        if (isAddonEnabled("achievement")) {
            registerCommandExecutor("osiagniecia", new AchievementCommand(this));

            AdminAchievementCommand adminAchievementCommand = new AdminAchievementCommand(this);
            registerCommandExecutor("aosiagniecia", adminAchievementCommand);
            registerCommandTabCompleter("aosiagniecia", adminAchievementCommand);
            Bukkit.getPluginManager().registerEvents(adminAchievementCommand, this);
        }



        if (isAddonEnabled("ranks")) {
            RanksCommand ranksCommand = new RanksCommand(this);
            registerCommandExecutor("rangi", ranksCommand);
            registerCommandTabCompleter("rangi", ranksCommand);
        }

        if (isAddonEnabled("media")) {
            MediaCommand mediaCommand = new MediaCommand(this);
            registerCommandExecutor("media", mediaCommand);
            registerCommandTabCompleter("media", mediaCommand);

            NadajMediaCommand nadajMediaCommand = new NadajMediaCommand(this);
            registerCommandExecutor("nadajmedia", nadajMediaCommand);
            registerCommandTabCompleter("nadajmedia", nadajMediaCommand);
        }

        if (isAddonEnabled("warps")) {
            WarpCommand warpCommand = new WarpCommand(this);
            registerCommandExecutor("warp", warpCommand);
            registerCommandTabCompleter("warp", warpCommand);

            SetWarpCommand setWarpCommand = new SetWarpCommand(this);
            registerCommandExecutor("setwarp", setWarpCommand);
            registerCommandTabCompleter("setwarp", setWarpCommand);

            DelWarpCommand delWarpCommand = new DelWarpCommand(this);
            registerCommandExecutor("delwarp", delWarpCommand);
            registerCommandTabCompleter("delwarp", delWarpCommand);
        }

        if (homeManager != null) {
            dev.grzybalson.commands.HomeCommand homeCommand = new dev.grzybalson.commands.HomeCommand(this, homeManager);
            registerCommandExecutor("home", homeCommand);
            registerCommandTabCompleter("home", homeCommand);

            dev.grzybalson.commands.SetHomeCommand setHomeCommand = new dev.grzybalson.commands.SetHomeCommand(this, homeManager);
            registerCommandExecutor("sethome", setHomeCommand);
            registerCommandTabCompleter("sethome", setHomeCommand);

            dev.grzybalson.commands.DelHomeCommand delHomeCommand = new dev.grzybalson.commands.DelHomeCommand(this, homeManager);
            registerCommandExecutor("delhome", delHomeCommand);
            registerCommandTabCompleter("delhome", delHomeCommand);
        }

        if (isAddonEnabled("commands")) {
            registerCommandExecutor("gamma", new dev.grzybalson.commands.GammaCommand(this));
        }

        if (isAddonEnabled("rtp")) {
            rtpCommand = new RtpCommand(this);
            registerCommandExecutor("rtp", rtpCommand);
        }
        if (isAddonEnabled("kit")) registerCommandExecutor("losuj-zestaw", new LosujZestawCommand(this));

        if (isAddonEnabled("codes")) {
            KodCommand kodCommand = new KodCommand(this);
            registerCommandExecutor("kod", kodCommand);
            registerCommandTabCompleter("kod", kodCommand);

            AKodCommand aKodCommand = new AKodCommand(this);
            registerCommandExecutor("akod", aKodCommand);
            registerCommandTabCompleter("akod", aKodCommand);
        }

        if (isAddonEnabled("bossbar")) {
            BossBarCommand bossBarCommand = new BossBarCommand(this);
            registerCommandExecutor("bossbar", bossBarCommand);
            registerCommandTabCompleter("bossbar", bossBarCommand);
        }

        if (isAddonEnabled("tablist") && tablistManager != null) {
            TablistCommand tablistCommand = new TablistCommand(tablistManager, sidebarManager);
            registerCommandExecutor("tablist", tablistCommand);
            registerCommandTabCompleter("tablist", tablistCommand);
            registerCommandExecutor("incognito", new IncognitoCommand(this));
        }

        if (isAddonEnabled("commands")) {
            TimerCommand timerCommand = new TimerCommand(this);
            this.timerCommand = timerCommand;
            registerCommandExecutor("timer", timerCommand);
            registerCommandTabCompleter("timer", timerCommand);
            Bukkit.getPluginManager().registerEvents(timerCommand, this);

            ServerInfoCommand serverInfoCommand = new ServerInfoCommand(this);
            registerCommandExecutor("serverinfo", serverInfoCommand);
            registerCommandTabCompleter("serverinfo", serverInfoCommand);

            RenderCommand renderCommand = new RenderCommand(this);
            registerCommandExecutor("render", renderCommand);
            registerCommandTabCompleter("render", renderCommand);

            GraczCommand graczCommand = new GraczCommand(this);
            registerCommandExecutor("gracz", graczCommand);
            registerCommandTabCompleter("gracz", graczCommand);
        }

        if (isAddonEnabled("worlds") && worldManager != null) {
            dev.grzybalson.modules.worlds.WorldCommand worldCommand =
                new dev.grzybalson.modules.worlds.WorldCommand(this, worldManager);
            this.worldCommand = worldCommand;
            registerCommandExecutor("world", worldCommand);
            registerCommandTabCompleter("world", worldCommand);
            registerCommandExecutor("w", worldCommand);
            registerCommandTabCompleter("w", worldCommand);
        }

        if (isAddonEnabled("groupteleport") && groupTeleportManager != null) {
            GroupTeleportCommand gtpCommand = new GroupTeleportCommand(groupTeleportManager);
            registerCommandExecutor("gtp", gtpCommand);
            registerCommandTabCompleter("gtp", gtpCommand);
            registerCommandExecutor("groupteleport", gtpCommand);
            registerCommandTabCompleter("groupteleport", gtpCommand);
            registerCommandExecutor("teparka", gtpCommand);
            registerCommandTabCompleter("teparka", gtpCommand);
        }

        if (isAddonEnabled("commands")) {
            RepairCommand repairCommand = new RepairCommand(this);
            registerCommandExecutor("repair", repairCommand);
            registerCommandTabCompleter("repair", repairCommand);
        }

        if (isAddonEnabled("automessages")) {
            AutoMessageCommand autoMessageCommand = new AutoMessageCommand(this);
            registerCommandExecutor("automessage", autoMessageCommand);
            registerCommandTabCompleter("automessage", autoMessageCommand);
        }

        if (isAddonEnabled("autocommands")) {
            AutoCommandsCommand autoCommandsCommand = new AutoCommandsCommand(this);
            registerCommandExecutor("autocommands", autoCommandsCommand);
            registerCommandTabCompleter("autocommands", autoCommandsCommand);
        }

        if (isAddonEnabled("creatormode")) {
            CreatorModeCommand creatorModeCmd = new CreatorModeCommand(this);
            registerCommandExecutor("tryb-tworca", creatorModeCmd);
            registerCommandTabCompleter("tryb-tworca", creatorModeCmd);
            AdminCreatorModeCommand adminCreatorModeCmd = new AdminCreatorModeCommand(this);
            registerCommandExecutor("atryb-tworca", adminCreatorModeCmd);
            registerCommandTabCompleter("atryb-tworca", adminCreatorModeCmd);
        }

        if (isAddonEnabled("commands")) {
            registerCommandExecutor("trash", new TrashCommand(this));
        }

        if (isAddonEnabled("backup")) {
            BackupCommand backupCommand = new BackupCommand(this);
            registerCommandExecutor("backup", backupCommand);
            registerCommandTabCompleter("backup", backupCommand);

            RollbackCommand rollbackCommand = new RollbackCommand(this);
            registerCommandExecutor("rollback", rollbackCommand);
            registerCommandTabCompleter("rollback", rollbackCommand);

            registerCommandExecutor("odbierzbackup", new OdbierzBackupCommand(this));
            registerCommandExecutor("backupverify", new BackupVerifyCommand(this));
        }

        if (isAddonEnabled("commands")) {
            InvseeCommand invseeCommand = new InvseeCommand(this);
            registerCommandExecutor("invsee", invseeCommand);
            registerCommandTabCompleter("invsee", invseeCommand);
            Bukkit.getPluginManager().registerEvents(invseeCommand, this);

            EcseeCommand ecseeCommand = new EcseeCommand(this);
            registerCommandExecutor("ecsee", ecseeCommand);
            registerCommandTabCompleter("ecsee", ecseeCommand);

            SearchItemCommand searchItemCommand = new SearchItemCommand(this);
            registerCommandExecutor("szukaj", searchItemCommand);
            registerCommandTabCompleter("szukaj", searchItemCommand);
            Bukkit.getPluginManager().registerEvents(searchItemCommand, this);

            LpExpireCommand lpExpireCommand = new LpExpireCommand(this);
            registerCommandExecutor("lpexpire", lpExpireCommand);
            registerCommandTabCompleter("lpexpire", lpExpireCommand);

            DeletstatsCommand deletstatsCommand = new DeletstatsCommand(this);
            registerCommandExecutor("deletstats", deletstatsCommand);
            registerCommandTabCompleter("deletstats", deletstatsCommand);

            AdminstatsCommand adminstatsCommand = new AdminstatsCommand(this);
            registerCommandExecutor("adminstats", adminstatsCommand);
            registerCommandTabCompleter("adminstats", adminstatsCommand);

            StatsCommand statsCommand = new StatsCommand(this);
            registerCommandExecutor("stats", statsCommand);
            registerCommandTabCompleter("stats", statsCommand);

            DiscordCommand discordCommand = new DiscordCommand(this);
            registerCommandExecutor("discord", discordCommand);
            registerCommandTabCompleter("discord", discordCommand);
        }

        if (isAddonEnabled("discordreward")) {
            RewardCommand rewardCommand = new RewardCommand(this);
            registerCommandExecutor("nagroda", rewardCommand);
            registerCommandTabCompleter("nagroda", rewardCommand);
        }

        if (isAddonEnabled("check")) {
            CheckCommand checkCommand = new CheckCommand(this);
            registerCommandExecutor("sprawdz", checkCommand);
            registerCommandTabCompleter("sprawdz", checkCommand);
        }

        if (reportStore != null) {
            FastReportCommand fastReportCommand = new FastReportCommand(this, reportStore);
            registerCommandExecutor("szybkiezgloszenie", fastReportCommand);
        }

        if (moderationManager != null) {
            ModerationCommands moderationCommands = new ModerationCommands(moderationManager);
            registerCommandExecutor("ban", moderationCommands);
            registerCommandTabCompleter("ban", moderationCommands);
            registerCommandExecutor("tempban", moderationCommands);
            registerCommandTabCompleter("tempban", moderationCommands);
            registerCommandExecutor("banip", moderationCommands);
            registerCommandTabCompleter("banip", moderationCommands);
            registerCommandExecutor("tempbanip", moderationCommands);
            registerCommandTabCompleter("tempbanip", moderationCommands);
            registerCommandExecutor("mute", moderationCommands);
            registerCommandTabCompleter("mute", moderationCommands);
            registerCommandExecutor("tempmute", moderationCommands);
            registerCommandTabCompleter("tempmute", moderationCommands);
            registerCommandExecutor("unban", moderationCommands);
            registerCommandTabCompleter("unban", moderationCommands);
            registerCommandExecutor("unmute", moderationCommands);
            registerCommandTabCompleter("unmute", moderationCommands);
            registerCommandExecutor("kick", moderationCommands);
            registerCommandTabCompleter("kick", moderationCommands);
            registerCommandExecutor("alts", moderationCommands);
            registerCommandTabCompleter("alts", moderationCommands);
            registerCommandExecutor("wyczysclista", moderationCommands);
            registerCommandExecutor("zglos", moderationCommands);
            registerCommandTabCompleter("zglos", moderationCommands);
        }

        if (bossManager != null) {
            BossCommand bossCommand = new BossCommand(bossManager);
            registerCommandExecutor("boss", bossCommand);
            registerCommandTabCompleter("boss", bossCommand);
        }

        dev.grzybalson.modules.customhit.CustomHitCommand customHitCommand = new dev.grzybalson.modules.customhit.CustomHitCommand(this);
        registerCommandExecutor("customhit", customHitCommand);
        registerCommandTabCompleter("customhit", customHitCommand);

        if (masakraCustomItemsManager != null) {
            registerCommandExecutor("krzysmcitems", masakraCustomItemsManager);
            registerCommandTabCompleter("krzysmcitems", masakraCustomItemsManager);
        }
        if (isAddonEnabled("oghub-customitems") && ogHubCustomItemsManager != null) {
            registerCommandExecutor("customitems", ogHubCustomItemsManager);
            registerCommandTabCompleter("customitems", ogHubCustomItemsManager);
            bindCustomItemsAliases(ogHubCustomItemsManager, ogHubCustomItemsManager);
        } else if (isAddonEnabled("minestarOGCustomItems") && minestarOGCustomItemsManager != null) {
            registerCommandExecutor("customitems", minestarOGCustomItemsManager);
            registerCommandTabCompleter("customitems", minestarOGCustomItemsManager);
            bindCustomItemsAliases(minestarOGCustomItemsManager, minestarOGCustomItemsManager);
        }

        if (flameHubCustomItemsManager != null) {
            FlameItemsCommand flameItemsCommand = new FlameItemsCommand(
                    flameHubCustomItemsManager.getConfigManager(),
                    flameHubCustomItemsManager.getItemManager());
            registerCommandExecutor("flameitems", flameItemsCommand);
            registerCommandTabCompleter("flameitems", flameItemsCommand);
            FlameResetCommand flameResetCommand = new FlameResetCommand(
                    flameHubCustomItemsManager.getConfigManager(),
                    flameHubCustomItemsManager.getCooldownManager(),
                    flameHubCustomItemsManager.getEffectManager());
            registerCommandExecutor("flamereset", flameResetCommand);
            registerCommandTabCompleter("flamereset", flameResetCommand);
            FlameReloadCommand flameReloadCommand = new FlameReloadCommand(flameHubCustomItemsManager.getConfigManager());
            registerCommandExecutor("flamereload", flameReloadCommand);
            registerCommandTabCompleter("flamereload", flameReloadCommand);
            restorePluginCommands(List.of("flameitems", "flamereset", "flamereload"));
        }

        if (isAddonEnabled("commands")) {
            registerCommandExecutor("lobby", new LobbyCommand(this));
        }
        if (isWalletAddonEnabled()) {
            PortfelCommand portfelCommand = new PortfelCommand(this);
            registerCommandExecutor("portfel", portfelCommand);
            registerCommandTabCompleter("portfel", portfelCommand);
            APortfelCommand aPortfelCommand = new APortfelCommand(this);
            registerCommandExecutor("aportfel", aPortfelCommand);
            registerCommandTabCompleter("aportfel", aPortfelCommand);
        }

        if (isAddonEnabled("generator") && generatorManager != null) {
            GeneratorCommand generatorCommand = new GeneratorCommand(generatorManager);
            registerCommandExecutor("generator", generatorCommand);
            registerCommandTabCompleter("generator", generatorCommand);
        }

        if (isAddonEnabled("daily")) {
            DailyRewardCommand dailyRewardCommand = new DailyRewardCommand(dailyRewardManager);
            registerCommandExecutor("daily", dailyRewardCommand);
            registerCommandTabCompleter("daily", dailyRewardCommand);

            AdminDailyCommand adminDailyCommand = new AdminDailyCommand(dailyRewardManager);
            registerCommandExecutor("adaily", adminDailyCommand);
            registerCommandTabCompleter("adaily", adminDailyCommand);
        }
    }

    /** Gdy inny plugin trzyma ten sam label, nasza komenda dziala tylko jako /hollycore:<label>. */
    private void warnIfCommandTakenByOtherPlugin(String label) {
        PluginCommand registered = getServer().getPluginCommand(label);
        if (registered != null && registered.getPlugin() != this) {
            getLogger().warning("Komenda /" + label + " jest zajeta przez plugin "
                + registered.getPlugin().getName() + " — HollyCore wystawia ja jako /hollycore:" + label
                + ". Usun ta komende w tamtym pluginie albo dodaj alias w commands.yml serwera.");
        }
    }

    private void registerCommandExecutor(String name, CommandExecutor executor) {
        HollyIntegrityGuard.requireBound(this, "commands:executor:" + name);
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Nie mozna zarejestrowac komendy /" + name + ", bo nie ma jej w plugin.yml.");
            return;
        }
        String permission = command.getPermission();
        if (permission != null && !permission.isBlank()) {
            command.setPermissionMessage(ColorUtils.colorizeLegacy(ColorUtils.formatNoPermission(name, permission)));
        }
        command.setExecutor(executor);
    }

    private void bindCustomItemsAliases(CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand adminItems = getCommand("adminitems");
        if (adminItems != null) {
            adminItems.setPermission("Holly.customitems.admin");
        }
        registerCommandExecutor("adminitems", executor);
        registerCommandTabCompleter("adminitems", tabCompleter);
    }

    private void registerCommandTabCompleter(String name, TabCompleter tabCompleter) {
        HollyIntegrityGuard.requireBound(this, "commands:tab:" + name);
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Nie mozna zarejestrowac tab-completera komendy /" + name + ", bo nie ma jej w plugin.yml.");
            return;
        }
        command.setTabCompleter(tabCompleter);
    }

    private void loadAddonToggles() {
        File file = new File(getDataFolder(), "addons.yml");
        if (!file.exists()) {
            saveResource("addons.yml", false);
        }
        addonsConfig = YamlConfiguration.loadConfiguration(file);
        if (addonsConfig.contains("addons.marketanarchia")) {
            addonsConfig.set("addons.marketanarchia", null);
            try {
                addonsConfig.save(file);
            } catch (IOException exception) {
                getLogger().warning("Nie udalo sie usunac marketanarchia z addons.yml: " + exception.getMessage());
            }
        }
        appendMissingAddonToggles(file);
    }

    public void reloadAddonToggles() {
        loadAddonToggles();
    }

    public void refreshDisabledAddonCommands() {
        unregisterDisabledAddonCommands();
    }

    private void appendMissingAddonToggles(File file) {
        try (InputStream inputStream = getResource("addons.yml")) {
            if (inputStream == null) {
                return;
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
            boolean changed = false;

            for (String path : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(path) || addonsConfig.contains(path)) {
                    continue;
                }
                addonsConfig.set(path, defaults.get(path));
                changed = true;
            }

            if (changed) {
                addonsConfig.save(file);
            }
        } catch (IOException exception) {
            getLogger().warning("Nie udalo sie uzupelnic addons.yml o nowe wpisy: " + exception.getMessage());
        }
    }

    /**
     * Wymusza klucz w addons.yml + plik configu FlameHub.
     * Nie zależy od toggle / ConfigAutoUpdater / obfuskacji ścieżek w innych klasach.
     */
    private void forceFlameHubBootstrap() {
        try {
            File data = getDataFolder();
            if (!data.exists() && !data.mkdirs()) {
                getLogger().severe("[FlameHub] Nie moge utworzyc dataFolder: " + data.getAbsolutePath());
            }

            File addonsYml = new File(data, "addons.yml");
            if (!addonsYml.exists()) {
                saveResource("addons.yml", false);
            }
            YamlConfiguration live = YamlConfiguration.loadConfiguration(addonsYml);
            if (!live.getBoolean("addons.flamehub-customitems", false)) {
                live.set("addons.flamehub-customitems", true);
                live.save(addonsYml);
                getLogger().warning("[FlameHub] Dopisano addons.flamehub-customitems: true do " + addonsYml.getAbsolutePath());
            } else {
                getLogger().warning("[FlameHub] addons.yml juz ma flamehub-customitems=true @ " + addonsYml.getAbsolutePath());
            }
            // Odswiez runtime toggles jesli juz zaladowane.
            if (addonsConfig != null) {
                addonsConfig.set("addons.flamehub-customitems", true);
            }

            File folder = new File(data, "addons/flamehub-customitems");
            if (!folder.exists() && !folder.mkdirs()) {
                getLogger().severe("[FlameHub] Nie moge utworzyc folderu: " + folder.getAbsolutePath());
            }
            File configFile = new File(folder, "flamehub-customitems.yml");
            if (!configFile.exists() || configFile.length() == 0L) {
                try (InputStream in = getResource("addons/flamehub-customitems/flamehub-customitems.yml")) {
                    if (in == null) {
                        getLogger().severe("[FlameHub] BRAK resource w JAR: addons/flamehub-customitems/flamehub-customitems.yml");
                    } else {
                        java.nio.file.Files.copy(in, configFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        getLogger().warning("[FlameHub] Utworzono config: " + configFile.getAbsolutePath());
                    }
                }
            } else {
                getLogger().warning("[FlameHub] Config juz istnieje: " + configFile.getAbsolutePath());
            }
        } catch (Throwable throwable) {
            getLogger().log(java.util.logging.Level.SEVERE, "[FlameHub] forceFlameHubBootstrap FAIL", throwable);
        }
    }

    public boolean isFlameHubCustomItemsEnabled() {
        return true;
    }

    public boolean isAddonEnabled(String addonName) {
        if (HollyRuntime.hasBoundState(this)) {
            HollyIntegrityGuard.requireBound(this, "addons:" + addonName);
        }
        if (addonsConfig == null) {
            return true;
        }
        if ("flamehub-customitems".equalsIgnoreCase(addonName)
                || "flamehub".equalsIgnoreCase(addonName)
                || "hooditems".equalsIgnoreCase(addonName)) {
            return true;
        }
        if (isExclusiveLicenseAddon(addonName) && !hasExclusiveAddonsLicense()) {
            return false;
        }
        return addonsConfig.getBoolean("addons." + addonName, true);
    }

    /**
     * Wybór systemu obrażeń z addons.yml:
     * {@code damage-system: customhit | hollyservice | none}
     * Alternatywnie {@code hollydamage: true} wymusza hollyservice.
     */
    public String resolveDamageSystem() {
        if (addonsConfig != null) {
            String raw = addonsConfig.getString("addons.damage-system", null);
            if (raw != null && !raw.isBlank()) {
                String normalized = raw.trim().toLowerCase(Locale.ROOT);
                if (normalized.equals("hollyservice") || normalized.equals("holly") || normalized.equals("hollydamage")
                    || normalized.equals("service") || normalized.equals("engine")) {
                    return "hollyservice";
                }
                if (normalized.equals("none") || normalized.equals("off") || normalized.equals("vanilla")
                    || normalized.equals("disabled")) {
                    return "none";
                }
                if (normalized.equals("customhit") || normalized.equals("custom") || normalized.equals("hits")) {
                    return "customhit";
                }
            }
            if (addonsConfig.getBoolean("addons.hollydamage", false)) {
                return "hollyservice";
            }
            if (!addonsConfig.getBoolean("addons.customhit", true)) {
                return "none";
            }
        }
        return "customhit";
    }

    public boolean isExclusiveLicenseAddon(String addonName) {
        return addonName != null && EXCLUSIVE_LICENSE_ADDONS.contains(addonName.toLowerCase(Locale.ROOT));
    }

    public boolean hasExclusiveAddonsLicense() {
        if (configManager == null || configManager.getLicenseConfig() == null) {
            return true;
        }

        String licenseKey = configManager.getLicenseConfig().getLicenseKey();
        return EXCLUSIVE_ADDONS_LICENSE_KEY.equalsIgnoreCase(licenseKey == null ? "" : licenseKey.trim());
    }


    /**
     * Bez {@link ShopGUI} — przy starcie przez HollyLoader ładowanie ShopGUI potrafi
     * wywalić {@code zip file closed} (PluginClassLoader) i wyłączyć cały plugin.
     */
    public boolean isMobsLicenseAllowed() {
        try {
            if (configManager == null) {
                return true;
            }
            var shopConfig = configManager.getShopConfig();
            var licenseConfig = configManager.getLicenseConfig();
            if (shopConfig == null || licenseConfig == null) {
                return true;
            }
            String requiredLicense = shopConfig.getLicenseShopRequiredLicense();
            String licenseKey = licenseConfig.getLicenseKey();
            if (requiredLicense == null || requiredLicense.isBlank()) {
                return true;
            }
            return requiredLicense.trim().equalsIgnoreCase(licenseKey == null ? "" : licenseKey.trim());
        } catch (Throwable throwable) {
            getLogger().warning("[Mobs] Pominięto check licencji (classloader/jar): " + throwable.getMessage());
            return true;
        }
    }

    public boolean isMobsFeatureEnabled() {
        return isAddonEnabled("mobs") && isMobsLicenseAllowed();
    }

    public boolean isWalletAddonEnabled() {
        if (Bukkit.getPluginManager().getPlugin("HollyPortfel") != null) {
            return false;
        }
        return isAddonEnabled("economy") && isAddonEnabled("wallet") && isAddonEnabled("portfel");
    }

    private void unregisterDisabledAddonCommands() {
        try {
            unregisterDisabledAddonCommands0();
        } catch (Throwable throwable) {
            // HollyLoader potrafi zamknąć JAR w trakcie enable — nie wyłączaj całego core.
            getLogger().log(java.util.logging.Level.SEVERE,
                "[HollyCore] Nie udało się odpiąć komend wyłączonych addonów", throwable);
        }
    }

    private void unregisterDisabledAddonCommands0() {
        Map<String, List<String>> addonCommands = new LinkedHashMap<>();
        addonCommands.put("anarchiagg", List.of("anarchiaitems", "aszafka", "kowal", "akowal", "ksiegi"));
        addonCommands.put("oghubzmianki", List.of("zmianki", "azmianki", "zmiankibattle"));
        addonCommands.put("daily", List.of("daily", "adaily"));
        addonCommands.put("cases", List.of("case"));
        addonCommands.put("enderchest", List.of("ec", "enderchest"));
        addonCommands.put("itemsync", List.of("save-all", "saveall"));
        addonCommands.put("afk", List.of("afk"));
        addonCommands.put("automessages", List.of("automessage"));
        addonCommands.put("autocommands", List.of("autocommands"));
        addonCommands.put("clans", List.of("klan", "resetujranking"));
        addonCommands.put("clearlag", List.of("clearlag"));
        addonCommands.put("codes", List.of("kod", "akod"));
        addonCommands.put("tpa", List.of("tpa"));
        addonCommands.put("commands", List.of(
            "tp", "tpa", "tphere", "s", "tpaccept", "tpdeny", "gm", "flyspeed", "fly",
            "bc", "title", "setspawn", "spawn", "msg", "r", "reply", "ch",
            "rozdajadmin", "odbierz", "restartadmin", "vanish", "gamma", "timer", "serverinfo", "repair", "trash",
            "invsee", "ecsee", "szukaj", "lpexpire", "deletstats", "adminstats", "stats", "discord", "lobby", "helpop", "adminchat", "gracz",
            "skull", "glowa", "glowka", "head"
        ));
        addonCommands.put("creatormode", List.of("tryb-tworca", "atryb-tworca"));
        addonCommands.put("customhit", List.of("customhit"));
        addonCommands.put("killeffect", List.of("killeffect"));
        addonCommands.put("generator", List.of("generator"));
        addonCommands.put("renderdistance", List.of("renderdistance"));
        addonCommands.put("economy", List.of("eco", "pay", "money", "portfel", "aportfel"));
        addonCommands.put("wallet", List.of("portfel", "aportfel", "getback"));
        addonCommands.put("portfel", List.of("portfel", "aportfel", "getback"));
        addonCommands.put("kit", List.of("kit", "akit", "losuj-zestaw"));
        addonCommands.put("tops", List.of("tops"));
        addonCommands.put("market", List.of("rynek", "wystaw", "amarket"));
        addonCommands.put("mobs", List.of("mobs"));
        addonCommands.put("messages", List.of("ignore"));
        addonCommands.put("ranks", List.of("rangi"));
        addonCommands.put("media", List.of("media", "nadajmedia"));
        addonCommands.put("rtp", List.of("rtp"));
        addonCommands.put("shop", List.of("sklep", "asklep", "skup"));
        addonCommands.put("bossbar", List.of("bossbar"));
        addonCommands.put("tablist", List.of("tablist"));
        addonCommands.put("discordreward", List.of("nagroda"));
        addonCommands.put("upgrade", List.of("ulepsz", "itemupgrader", "itemupgrade", "armorcreate"));
        addonCommands.put("piniata", List.of("piniata", "adminevent"));
        addonCommands.put("minestarAnvil", List.of());
        addonCommands.put("minestarOGCustomItems", List.of("customitems"));
        addonCommands.put("oghub-customitems", List.of("customitems"));
        addonCommands.put("masakra-customitems", List.of("krzysmcitems"));
        addonCommands.put("flamehub-customitems", List.of("flameitems", "flamereset", "flamereload"));
        addonCommands.put("oghub-szafka", List.of("szafka", "aszafka"));
        addonCommands.put("mslf-kostiumy", List.of("mkostiumy", "amkostiumy", "kostium", "kostiumy", "szafka", "aszafka"));
        addonCommands.put("masakra-szafka", List.of("szafka", "aszafka"));
        addonCommands.put("patroni", List.of("patron", "patroni", "patrony"));
        addonCommands.put("warps", List.of("warp", "setwarp", "delwarp"));
        addonCommands.put("achievement", List.of("osiagniecia", "aosiagniecia"));
        addonCommands.put("check", List.of("sprawdz"));
        addonCommands.put("chat", List.of("chat"));
        addonCommands.put("backup", List.of("backup", "backupverify", "odbierzbackup"));
        addonCommands.put("zonebox", List.of("zonebox", "blockseconomy", "drwal", "turbodrop"));
        addonCommands.put("blockseconomy", List.of("blockseconomy", "drwal", "turbodrop"));
        addonCommands.put("szaman", List.of("szaman"));
        addonCommands.put("sigil", List.of("sigil"));
        addonCommands.put("mission", List.of("misje"));
        addonCommands.put("dropkeys", List.of("dropkeys", "klucze"));
        addonCommands.put("itemstorage", List.of("itemstorage"));
        addonCommands.put("itemshop", List.of("itemshop", "nadajitemshop"));
        addonCommands.put("trade", List.of("trade", "wymiana"));
        addonCommands.put("trener", List.of("trener"));
        addonCommands.put("adminitems", List.of("adminitems", "adminitemsclear", "resetujcooldown"));
        addonCommands.put("mslf", List.of("mslf", "mslfgui", "lfms"));
        addonCommands.put("mslf-szczesciarz", List.of("szczesciarz", "aszczesciarz"));
        addonCommands.put("coinflip", List.of("coinflip"));
        addonCommands.put("zywioly", List.of("zywioly"));
        addonCommands.put("moderation", List.of(
            "ban", "tempban", "banip", "tempbanip", "mute", "tempmute", "unban", "unmute", "kick", "alts", "wyczysclista", "zglos"
        ));
        addonCommands.put("armorstars", List.of("armorstars"));
        addonCommands.put("home", List.of("home", "sethome", "delhome"));
        addonCommands.put("elytra", List.of());
        addonCommands.put("czek", List.of("czek"));
        addonCommands.put("event", List.of("event", "adungeon", "skip"));
        addonCommands.put("titles", List.of());
        addonCommands.put("unique", List.of());
        addonCommands.put("wallet", List.of("portfel", "aportfel", "getback"));
        addonCommands.put("spawn", List.of("spawn", "setspawn"));
        addonCommands.put("antilag", List.of("entitylimit"));


        List<String> disabledCommands = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : addonCommands.entrySet()) {
            if ("home".equals(entry.getKey())) {
                if (!isAddonEnabled("home") && !isAddonEnabled("homes")) {
                    disabledCommands.addAll(entry.getValue());
                }
                continue;
            }
            if (!isAddonEnabled(entry.getKey())) {
                disabledCommands.addAll(entry.getValue());
            }
        }

        if (isAddonEnabled("minestarOGCustomItems") || isAddonEnabled("oghub-customitems")) {
            disabledCommands.removeIf(command ->
                command.equalsIgnoreCase("customitems") || command.equalsIgnoreCase("adminitems"));
        }

        if (isAnarchiaGGSzafkaEnabled()) {
            disabledCommands.removeIf(command ->
                command.equalsIgnoreCase("aszafka")
                    || command.equalsIgnoreCase("szafka")
                    || command.equalsIgnoreCase("garderoba")
            );
        }

        if (isAddonEnabled("market")) {
            disabledCommands.removeIf(command ->
                command.equalsIgnoreCase("rynek")
                    || command.equalsIgnoreCase("market")
                    || command.equalsIgnoreCase("wystaw")
                    || command.equalsIgnoreCase("amarket")
                    || command.equalsIgnoreCase("arynek")
            );
        }

        addDisabledAnarchiaGGModuleCommands(disabledCommands);

        // oghub-szafka / mslf-kostiumy / masakra-szafka dostarczaja wlasna szafke — komendy zostaja aktywne
        // nawet gdy modul AnarchiaGG.szafka jest wylaczony.
        if (isAddonEnabled("oghub-szafka") || isAddonEnabled("mslf-kostiumy") || isAddonEnabled("masakra-szafka")) {
            disabledCommands.removeIf(command ->
                command.equalsIgnoreCase("aszafka")
                    || command.equalsIgnoreCase("szafka")
                    || command.equalsIgnoreCase("garderoba")
                    || command.equalsIgnoreCase("kostium")
                    || command.equalsIgnoreCase("kostiumy")
                    || command.equalsIgnoreCase("pet")
                    || command.equalsIgnoreCase("pety")
            );
        }

        if (isMslfEnderChestCommandActive()) {
            disabledCommands.removeIf(command ->
                command.equalsIgnoreCase("ec") || command.equalsIgnoreCase("enderchest")
            );
        }

        if (isAddonEnabled("oghubzmianki")) {
            disabledCommands.removeIf(command ->
                command.equalsIgnoreCase("zmianki")
                    || command.equalsIgnoreCase("azmianki")
                    || command.equalsIgnoreCase("zmiankibattle")
            );
        }

        if (configManager != null && isAddonEnabled("mobs") && !isMobsLicenseAllowed()) {
            disabledCommands.add("mobs");
        }

        hidePluginCommands(disabledCommands);

        if (isMslfEnderChestCommandActive()) {
            restorePluginCommands(List.of("ec", "enderchest"));
        }
    }

    /** /ec z lekkim EC MSLF (priorytet nad addonem enderchest). */
    public boolean isMslfEnderChestCommandActive() {
        return isAddonEnabled("mslf")
            && mslfManager != null
            && mslfManager.enderChestService() != null;
    }

    private void addDisabledAnarchiaGGModuleCommands(List<String> disabledCommands) {
        if (!isAddonEnabled("anarchiagg") || anarchiaGGConfigManager == null) {
            return;
        }

        AnarchiaGGModulesConfig modules = anarchiaGGConfigManager.getModulesConfig();
        if (modules == null) {
            return;
        }

        if (!modules.isEventowki()) {
            disabledCommands.add("anarchiaitems");
        }
        if (!modules.isSzafka()) {
            disabledCommands.add("szafka");
            disabledCommands.add("aszafka");
        }
        if (!modules.isKowal()) {
            disabledCommands.add("kowal");
            disabledCommands.add("akowal");
        }
        if (!modules.isKsiegi()) {
            disabledCommands.add("ksiegi");
        }
        if (!modules.isZmianki() && !isAddonEnabled("oghubzmianki")) {
            disabledCommands.add("zmianki");
            disabledCommands.add("azmianki");
            disabledCommands.add("zmiankibattle");
        }
    }

    private boolean isAnarchiaGGSzafkaEnabled() {
        if (!isAddonEnabled("anarchiagg") || anarchiaGGConfigManager == null) {
            return false;
        }

        AnarchiaGGModulesConfig modules = anarchiaGGConfigManager.getModulesConfig();
        return modules != null && modules.isSzafka();
    }

    public void hidePluginCommands(Collection<String> commandNames) {
        if (commandNames == null || commandNames.isEmpty()) {
            return;
        }

        try {
            CommandMap commandMap = getServerCommandMap();
            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            Set<Command> commandsToRemove = new HashSet<>();
            Set<String> disabledCommandNames = new HashSet<>();
            Set<String> disabledLabels = new HashSet<>();

            for (String commandName : commandNames) {
                String normalizedCommandName = commandName.toLowerCase(Locale.ROOT);
                disabledCommandNames.add(normalizedCommandName);
                disabledLabels.add(normalizedCommandName);
                PluginCommand pluginCommand = getCommand(commandName);
                if (pluginCommand != null) {
                    commandsToRemove.add(pluginCommand);
                    disabledLabels.addAll(pluginCommand.getAliases().stream()
                        .map(alias -> alias.toLowerCase(Locale.ROOT))
                        .toList());
                }
            }

            List<String> labelsToRemove = new ArrayList<>();
            Set<Command> unregisteredCommands = new HashSet<>();

            for (Map.Entry<String, Command> entry : new ArrayList<>(knownCommands.entrySet())) {
                String rawLabel = entry.getKey().toLowerCase(Locale.ROOT);
                String label = stripPluginNamespace(rawLabel);
                Command command = entry.getValue();
                if (!isOwnedCommand(command)) {
                    continue;
                }
                String commandName = command.getName().toLowerCase(Locale.ROOT);
                boolean shouldRemove = commandsToRemove.contains(command)
                    || disabledCommandNames.contains(commandName);
                if (shouldRemove) {
                    labelsToRemove.add(entry.getKey());
                    disabledLabels.add(label);
                    disabledLabels.add(commandName);
                    if (unregisteredCommands.add(command)) {
                        command.unregister(commandMap);
                    }
                }
            }

            for (String labelToRemove : labelsToRemove) {
                knownCommands.remove(labelToRemove);
            }

            removeHelpTopics(disabledLabels);
            syncServerCommands();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("Nie udalo sie ukryc komend wylaczonych addonow: " + exception.getMessage());
        }
    }

    /**
     * Przywraca komendy HollyCore na CommandMap (np. gdy wcześniej zdejmowano je
     * bo wyłączony był modul AnarchiaGG.szafka, a potem wlacza sie mslf-kostiumy).
     */
    public void restorePluginCommands(Collection<String> commandNames) {
        if (commandNames == null || commandNames.isEmpty()) {
            return;
        }
        try {
            CommandMap commandMap = getServerCommandMap();
            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            String fallbackPrefix = getDescription().getName().toLowerCase(Locale.ROOT);
            for (String commandName : commandNames) {
                PluginCommand pluginCommand = getCommand(commandName);
                if (pluginCommand == null) {
                    continue;
                }
                String name = pluginCommand.getName().toLowerCase(Locale.ROOT);
                if (!knownCommands.containsKey(name)) {
                    commandMap.register(fallbackPrefix, pluginCommand);
                } else if (knownCommands.get(name) != pluginCommand) {
                    knownCommands.put(name, pluginCommand);
                    knownCommands.put(fallbackPrefix + ":" + name, pluginCommand);
                    for (String alias : pluginCommand.getAliases()) {
                        String aliasKey = alias.toLowerCase(Locale.ROOT);
                        knownCommands.put(aliasKey, pluginCommand);
                        knownCommands.put(fallbackPrefix + ":" + aliasKey, pluginCommand);
                    }
                }
            }
            syncServerCommands();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("Nie udalo sie przywrocic komend: " + exception.getMessage());
        }
    }

    private void syncServerCommands() {
        try {
            Method syncCommandsMethod = getServer().getClass().getDeclaredMethod("syncCommands");
            syncCommandsMethod.setAccessible(true);
            syncCommandsMethod.invoke(getServer());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Starsze implementacje serwera mogą nie posiadać syncCommands().
            // Sama CommandMap i tak jest już wyczyszczona.
        }
    }

    @SuppressWarnings("unchecked")
    private void removeHelpTopics(Collection<String> commandLabels) {
        if (commandLabels == null || commandLabels.isEmpty()) {
            return;
        }
        try {
            Object helpMap = getServer().getHelpMap();
            Field helpTopicsField = findField(helpMap.getClass(), "helpTopics");
            helpTopicsField.setAccessible(true);
            Map<String, ?> helpTopics = (Map<String, ?>) helpTopicsField.get(helpMap);
            helpTopics.entrySet().removeIf(entry -> {
                String topic = entry.getKey().toLowerCase(Locale.ROOT);
                if (!topic.startsWith("/")) {
                    return false;
                }
                String label = stripPluginNamespace(topic.substring(1));
                return commandLabels.contains(label);
            });
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // HelpMap nie jest krytyczna; najważniejsze jest usunięcie z CommandMap/TAB.
        }
    }

    private CommandMap getServerCommandMap() throws ReflectiveOperationException {
        Method getCommandMapMethod = getServer().getClass().getMethod("getCommandMap");
        return (CommandMap) getCommandMapMethod.invoke(getServer());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap commandMap) throws ReflectiveOperationException {
        Field knownCommandsField = findField(commandMap.getClass(), "knownCommands");
        knownCommandsField.setAccessible(true);
        return (Map<String, Command>) knownCommandsField.get(commandMap);
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private boolean isOwnedCommand(Command command) {
        return command instanceof PluginCommand pluginCommand && pluginCommand.getPlugin().equals(this);
    }

    private String stripPluginNamespace(String label) {
        String prefix = getName().toLowerCase(Locale.ROOT) + ":";
        if (label.startsWith(prefix)) {
            return label.substring(prefix.length());
        }
        return label;
    }

    private void registerListeners() {
        HollyIntegrityGuard.requireBound(this, "listeners:register");
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        new dev.grzybalson.listeners.PlayerCollisionGuard(this).install();
        noEnvironmentalDamageListener = new NoEnvironmentalDamageListener(this);
        Bukkit.getPluginManager().registerEvents(noEnvironmentalDamageListener, this);
        worldBorderHitFixListener = new dev.grzybalson.listeners.WorldBorderHitFixListener(this);
        Bukkit.getPluginManager().registerEvents(worldBorderHitFixListener, this);
        Bukkit.getPluginManager().registerEvents(new BlockEnvironmentListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(new StaticMenuListener(this), this);
        if (shulkerBoxListener != null) {
            Bukkit.getPluginManager().registerEvents(shulkerBoxListener, this);
        }
        Bukkit.getPluginManager().registerEvents(new LegacyShopItemSanitizer(this), this);
        Bukkit.getPluginManager().registerEvents(new dev.grzybalson.listeners.TrapdoorInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new dev.grzybalson.listeners.PvpDenyEffectDamageListener(), this);
        if (caseManager != null) Bukkit.getPluginManager().registerEvents(new CaseListener(this, caseManager), this);
        if (minestarAnvilManager != null) Bukkit.getPluginManager().registerEvents(minestarAnvilManager, this);
        if (minestarOGCustomItemsManager != null) Bukkit.getPluginManager().registerEvents(minestarOGCustomItemsManager, this);
        if (ogHubCustomItemsManager != null) Bukkit.getPluginManager().registerEvents(ogHubCustomItemsManager, this);
        if (patronManager != null) Bukkit.getPluginManager().registerEvents(patronManager, this);
        if (lockedFeaturesManager != null) Bukkit.getPluginManager().registerEvents(lockedFeaturesManager, this);
        if (isAddonEnabled("clans")) Bukkit.getPluginManager().registerEvents(new ClanChatListener(this), this);
        if (isAddonEnabled("clans")) Bukkit.getPluginManager().registerEvents(new ClanPvpListener(this), this);
        if (isAddonEnabled("combat")) Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        if (isAddonEnabled("combat")) Bukkit.getPluginManager().registerEvents(new CombatDisplayListener(this), this);
        if (groupTeleportManager != null) {
            Bukkit.getPluginManager().registerEvents(new GroupTeleportListener(groupTeleportManager), this);
            Bukkit.getPluginManager().registerEvents(new GroupTeleportInteractListener(groupTeleportManager), this);
        }
        if (bossManager != null) {
            Bukkit.getPluginManager().registerEvents(bossManager.getHoglinHandler(), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.boss.BossEntityListener(bossManager), this);
        }
        if (moderationManager != null) {
            Bukkit.getPluginManager().registerEvents(new ModerationListener(moderationManager), this);
        }
        if (customHitsService != null) {
            Bukkit.getPluginManager().registerEvents(new CustomHitDamageListener(customHitsService), this);
            Bukkit.getPluginManager().registerEvents(new ArmorToughnessListener(customHitsService), this);
            Bukkit.getPluginManager().registerEvents(new PvpMaxDamageCapListener(customHitsService), this);
            Bukkit.getPluginManager().registerEvents(new LevitationKnockbackListener(customHitsService), this);
            Bukkit.getPluginManager().registerEvents(new DiamondArmorKnockbackListener(customHitsService), this);
        }
        if (szamanManager != null) Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.szaman.PerksListener(szamanManager), this);
        if (sigilManager != null) {
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.sigil.SigilListener(sigilManager), this);
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.sigil.SigilEffectsListener(sigilManager), this);
        }
        if (missionManager != null) {
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.mission.MissionListener(this, missionManager), this);
        }
        if (dropKeysManager != null) {
            Bukkit.getPluginManager().registerEvents(dropKeysManager, this);
        }
        if (isAddonEnabled("elytra")) {
            Bukkit.getPluginManager().registerEvents(new ElytraFireworkWearListener(this), this);
        }
        if (antiGriefManager != null) Bukkit.getPluginManager().registerEvents(antiGriefManager, this);
        if (isAddonEnabled("commands")) Bukkit.getPluginManager().registerEvents(commandCooldownListener, this);
        if (isAddonEnabled("clans")) Bukkit.getPluginManager().registerEvents(new EloListener(this), this);
        if (teleportAnimator != null) {
            Bukkit.getPluginManager().registerEvents(teleportAnimator, this);
        }
        if (isAddonEnabled("warps")) Bukkit.getPluginManager().registerEvents(new WarpListener(this), this);
        if (homeManager != null) Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.home.HomeListener(this, homeManager), this);
        if (zoneBoxManager != null) Bukkit.getPluginManager().registerEvents(zoneBoxManager, this);
        if (isAddonEnabled("messages")) Bukkit.getPluginManager().registerEvents(new JoinQuitListener(this), this);
        if (isAddonEnabled("commands")) Bukkit.getPluginManager().registerEvents(new CommandBlockerListener(this), this);
        if (isAddonEnabled("commands")) Bukkit.getPluginManager().registerEvents(new ButtonTeleportListener(this), this);
        if (isAddonEnabled("commands")) Bukkit.getPluginManager().registerEvents(new TpaConfirmListener(this), this);
        if (isAddonEnabled("chat")) Bukkit.getPluginManager().registerEvents(new LpcChatListener(this), this);
        if (isAddonEnabled("commands")) Bukkit.getPluginManager().registerEvents(new DeepslateInteractListener(this), this);
        if (enderChestManager != null) Bukkit.getPluginManager().registerEvents(enderChestManager, this);
        if (inventorySyncManager != null) {
            Bukkit.getPluginManager().registerEvents(new InventorySyncListener(this, inventorySyncManager), this);
        }
        if (isAddonEnabled("creatormode")) Bukkit.getPluginManager().registerEvents(new CreatorModeListener(this), this);
        if (isAddonEnabled("backup")) {
            Bukkit.getPluginManager().registerEvents(new BackupListener(this, backupManager, deathKeepListener), this);
            Bukkit.getPluginManager().registerEvents(new BackupGUIListener(this), this);
        }
        if (isAddonEnabled("commands")) Bukkit.getPluginManager().registerEvents(new TrashListener(this), this);
        if (isAddonEnabled("commands")) Bukkit.getPluginManager().registerEvents(new WeatherListener(this), this);
        if (isAddonEnabled("warps") || isAddonEnabled("spawn")) Bukkit.getPluginManager().registerEvents(new PortalListener(this), this);
        if (isAddonEnabled("check")) Bukkit.getPluginManager().registerEvents(new CheckListener(this), this);
        if (isAddonEnabled("antilag")) {
            entityLimitListener = new EntityLimitListener(this);
            Bukkit.getPluginManager().registerEvents(entityLimitListener, this);
            dev.grzybalson.commands.EntityLimitCommand entityLimitCmd = new dev.grzybalson.commands.EntityLimitCommand(entityLimitListener);
            registerCommandExecutor("entitylimit", entityLimitCmd);
            registerCommandTabCompleter("entitylimit", entityLimitCmd);
        }
        dev.grzybalson.commands.EqSeeCommand eqSeeCmd = new dev.grzybalson.commands.EqSeeCommand(this);
        registerCommandExecutor("eqsee", eqSeeCmd);
        registerCommandTabCompleter("eqsee", eqSeeCmd);
        if (isAddonEnabled("commands")) Bukkit.getPluginManager().registerEvents(new InventorySecurityListener(this), this);
        if (isWalletAddonEnabled()) {
            Bukkit.getPluginManager().registerEvents(new WalletGUIListener(), this);
            Bukkit.getPluginManager().registerEvents(new VplnVoucherListener(this), this);
        }
        if (achievementListener != null) Bukkit.getPluginManager().registerEvents(achievementListener, this);
        if (mobsManager != null) Bukkit.getPluginManager().registerEvents(mobsManager, this);
        if (dailyRewardManager != null) Bukkit.getPluginManager().registerEvents(new DailyRewardListener(dailyRewardManager), this);
        if (discordRewardManager != null) Bukkit.getPluginManager().registerEvents(new DiscordRewardMenuListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SettingsGUIListener(), this);
        if (isAddonEnabled("event")) {
            Bukkit.getPluginManager().registerEvents(new EventGUIListener(eventManager), this);
            Bukkit.getPluginManager().registerEvents(new EventBossBarListener(this), this);
            if (dungeonSessionManager != null) {
                Bukkit.getPluginManager().registerEvents(
                    new dev.grzybalson.modules.event.dungeon.DungeonGameplayListener(this, dungeonSessionManager), this);
                registerCommandExecutor("skip", new DungeonSkipCommand(dungeonSessionManager));
            }
        }
        if (titleAddonManager != null) {
            Bukkit.getPluginManager().registerEvents(titleAddonManager, this);
        }
        if (sidebarManager != null) Bukkit.getPluginManager().registerEvents(sidebarManager, this);
        if (worldBorderManager != null) Bukkit.getPluginManager().registerEvents(worldBorderManager, this);
        if (renderDistanceManager != null) Bukkit.getPluginManager().registerEvents(new RenderDistanceListener(this, renderDistanceManager), this);
        if (vanishManager != null) Bukkit.getPluginManager().registerEvents(vanishManager, this);
        Bukkit.getPluginManager().registerEvents(new WorldGameRuleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ItemAttributeListener(this), this);
        if (upgradeManager != null) {
            Bukkit.getPluginManager().registerEvents(new UpgradeBestEffectsListener(this), this);
        }
        if (piniataManager != null) {
            Bukkit.getPluginManager().registerEvents(piniataManager, this);
        }
        if (clanArmorManager != null) Bukkit.getPluginManager().registerEvents(clanArmorManager, this);
        if (endModuleListener != null) Bukkit.getPluginManager().registerEvents(endModuleListener, this);
        if (isAddonEnabled("iplimit")) Bukkit.getPluginManager().registerEvents(new IpLimitListener(this), this);
        if (isAddonEnabled("fancynpc-push") && Bukkit.getPluginManager().getPlugin("FancyNpcs") != null) {
            Bukkit.getPluginManager().registerEvents(new dev.grzybalson.modules.fancynpc.FancyNpcPushManager(this), this);
        }
        if (masakraCustomItemsManager != null) Bukkit.getPluginManager().registerEvents(masakraCustomItemsManager, this);
    }

    public CustomHitsService getCustomHitsService() {
        return customHitsService;
    }

    public dev.grzybalson.modules.hollydamage.HollyDamageService getHollyDamageService() {
        return hollyDamageService;
    }

    public static HollyCore getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        HollyRuntime.require(this, "api:config");
        return configManager;
    }

    public MongoManager getMongoManager() {
        HollyRuntime.require(this, "api:mongo");
        return mongoManager;
    }

    public HollyEconomy getEconomy() {
        HollyRuntime.require(this, "api:economy");
        return economy;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public TeleportRequestManager getTeleportRequestManager() {
        return teleportRequestManager;
    }

    public AutoMessageManager getAutoMessageManager() {
        return autoMessageManager;
    }

    public AutoCommandsManager getAutoCommandsManager() {
        return autoCommandsManager;
    }

    public PickupManager getPickupManager() {
        HollyRuntime.require(this, "api:pickup");
        return pickupManager;
    }

    public IgnoreManager getIgnoreManager() {
        return ignoreManager;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public ClearLagManager getClearLagManager() {
        return clearLagManager;
    }


    public TopManager getTopManager() {
        return topManager;
    }

    public dev.grzybalson.modules.tops.TopsManager getTopsManager() {
        return topsManager;
    }

    public VanishManager getVanishManager() {
        return vanishManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public GroupTeleportManager getGroupTeleportManager() {
        return groupTeleportManager;
    }

    public AnticheatManager getAnticheatManager() {
        return anticheatManager;
    }

    public NatsService getNatsService() {
        return natsService;
    }

    public UniqueItemService getUniqueItemService() {
        return uniqueItemService;
    }

    public SectorService getSectorService() {
        return sectorService;
    }

    public ServerDiagnosticsService getServerDiagnosticsService() {
        return serverDiagnosticsService;
    }

    public InventorySyncManager getInventorySyncManager() {
        return inventorySyncManager;
    }

    public ShulkerBoxListener getShulkerBoxListener() {
        return shulkerBoxListener;
    }

    public RewardNotificationManager getRewardNotificationManager() {
        return rewardNotificationManager;
    }

    public StaticMenuManager getStaticMenuManager() {
        return staticMenuManager;
    }

    public RestartManager getRestartManager() {
        return restartManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }

    public MobsManager getMobsManager() {
        return mobsManager;
    }

    public UpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    public PiniataManager getPiniataManager() {
        return piniataManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public ModerationManager getModerationManager() {
        return moderationManager;
    }

    public ReportStore getReportStore() {
        return reportStore;
    }

    public PatronManager getPatronManager() {
        return patronManager;
    }

    public LockedFeaturesManager getLockedFeaturesManager() {
        return lockedFeaturesManager;
    }

    public ZoneBoxManager getZoneBoxManager() {
        return zoneBoxManager;
    }

    public dev.grzybalson.managers.TurboDropManager getTurboDropManager() {
        return turboDropManager;
    }

    public dev.grzybalson.modules.szaman.PerksManager getSzamanManager() {
        return szamanManager;
    }

    public dev.grzybalson.modules.sigil.SigilManager getSigilManager() {
        return sigilManager;
    }

    public dev.grzybalson.modules.mission.MissionManager getMissionManager() {
        return missionManager;
    }

    public dev.grzybalson.modules.trade.TradeManager getTradeManager() {
        return tradeManager;
    }

    public dev.grzybalson.modules.dropkeys.DropKeysManager getDropKeysManager() {
        return dropKeysManager;
    }

    public dev.grzybalson.modules.itemstorage.ItemStorageManager getItemStorageManager() {
        return itemStorageManager;
    }

    public GuiFactory getGuiFactory() {
        HollyRuntime.require(this, "api:gui");
        return guiFactory;
    }

    public ModuleConfigManager getModuleConfigManager() {
        return moduleConfigManager;
    }

    public EloManager getEloManager() {
        return eloManager;
    }

    /** Aktywny plik JAR pluginu (do hot-reload /admin loadnew). */
    public java.io.File getPluginJarFile() {
        return getFile();
    }

    public dev.grzybalson.modules.home.HomeManager getHomeManager() {
        return homeManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public TeleportAnimator getTeleportAnimator() {
        return teleportAnimator;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public SidebarManager getSidebarManager() {
        return sidebarManager;
    }

    public TablistManager getTablistManager() {
        return tablistManager;
    }

    public IncognitoManager getIncognitoManager() {
        return incognitoManager;
    }

    public StartupLogger getStartupLogger() {
        return startupLogger;
    }

    public CodeManager getCodeManager() {
        HollyRuntime.require(this, "api:codes");
        return codeManager;
    }

    public RankGiveawayManager getRankGiveawayManager() {
        HollyRuntime.require(this, "api:codes");
        return rankGiveawayManager;
    }

    public AutoTitleManager getAutoTitleManager() {
        return autoTitleManager;
    }

    public EnderChestManager getEnderChestManager() {
        HollyRuntime.require(this, "api:enderchest");
        return enderChestManager;
    }

    public CreatorModeManager getCreatorModeManager() {
        return creatorModeManager;
    }

    public ActionBarManager getActionBarManager() {
        return actionBarManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public dev.grzybalson.modules.backup.RollbackManager getRollbackManager() {
        return rollbackManager;
    }

    public KillstreakManager getKillstreakManager() {
        return killstreakManager;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public StatsDataManager getStatsDataManager() {
        return statsDataManager;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public AchievementListener getAchievementListener() {
        return achievementListener;
    }

    public AnarchiaGGModuleManager getAnarchiaGGModuleManager() {
        return anarchiaGGModuleManager;
    }

    public AnarchiaGGConfigManager getAnarchiaGGConfigManager() {
        return anarchiaGGConfigManager;
    }

    public CaseManager getCaseManager() {
        return caseManager;
    }

    public MinestarOGCustomItemsManager getMinestarOGCustomItemsManager() {
        return minestarOGCustomItemsManager;
    }

    public MasakraCustomItemsManager getMasakraCustomItemsManager() {
        return masakraCustomItemsManager;
    }

    public OgHubCustomItemsManager getOgHubCustomItemsManager() {
        return ogHubCustomItemsManager;
    }

    public FlameHubCustomItemsManager getFlameHubCustomItemsManager() {
        return flameHubCustomItemsManager;
    }

    public OgHubSzafkaManager getOgHubSzafkaManager() {
        return ogHubSzafkaManager;
    }

    public dev.grzybalson.modules.mslfkostiumy.MslfKostiumyManager getMslfKostiumyManager() {
        return mslfKostiumyManager;
    }

    public dev.grzybalson.modules.masakraszafka.MasakraSzafkaManager getMasakraSzafkaManager() {
        return masakraSzafkaManager;
    }

    public OgHubZmiankiManager getOgHubZmiankiManager() {
        return ogHubZmiankiManager;
    }

    public WalletManager getWalletManager() {
        HollyRuntime.require(this, "api:wallet");
        return walletManager;
    }

    public WalletProductStatsService getWalletProductStatsService() {
        return walletProductStatsService;
    }

    public dev.grzybalson.database.WalletDataManager getWalletDataManager() {
        return walletManager != null ? walletManager.getDataManager() : null;
    }

    public WebhookLogManager getWebhookLogManager() {
        return webhookLogManager;
    }

    public UserSettingsManager getUserSettingsManager() {
        return userSettingsManager;
    }

    public DiscordRewardManager getDiscordRewardManager() {
        return discordRewardManager;
    }

    public TitleAddonManager getTitleAddonManager() {
        return titleAddonManager;
    }

    public RenderDistanceManager getRenderDistanceManager() {
        return renderDistanceManager;
    }

    public DailyRewardManager getDailyRewardManager() {
        return dailyRewardManager;
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }

    public WorldBorderManager getWorldBorderManager() {
        return worldBorderManager;
    }

    public AntiGriefManager getAntiGriefManager() {
        return antiGriefManager;
    }

    public HollyPlaceholderExpansion getHollyPlaceholderExpansion() {
        return hollyPlaceholderExpansion;
    }

    public dev.grzybalson.managers.JoinInfoManager getJoinInfoManager() {
        return joinInfoManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public dev.grzybalson.modules.event.dungeon.DungeonSessionManager getDungeonSessionManager() {
        return dungeonSessionManager;
    }

    public dev.grzybalson.modules.mslf.MslfManager getMslfManager() {
        return mslfManager;
    }

    public dev.grzybalson.modules.mslfszczesciarz.MslfSzczesciarzManager getMslfSzczesciarzManager() {
        return mslfSzczesciarzManager;
    }

    public SpawnCommand getSpawnCommand() {
        return spawnCommand;
    }

    public RtpCommand getRtpCommand() {
        return rtpCommand;
    }

    public TimerCommand getTimerCommand() {
        return timerCommand;
    }

    public dev.grzybalson.modules.worlds.WorldCommand getWorldCommand() {
        return worldCommand;
    }

    public dev.grzybalson.modules.itemshop.ItemShopCommand getItemShopCommand() {
        return itemShopCommand;
    }

    public dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig getAdminItemsConfig() {
        return anarchiaGGConfigManager != null ? anarchiaGGConfigManager.getAdminItemsConfig() : null;
    }

    public dev.grzybalson.anarchiagg.coach.CoachConfig getCoachConfig() {
        return anarchiaGGConfigManager != null ? anarchiaGGConfigManager.getCoachConfig() : null;
    }

    public dev.grzybalson.anarchiagg.coach.CoachManager getCoachManager() {
        return coachManager;
    }

    public dev.grzybalson.anarchiagg.adminitems.AdminItemsInfoGUI getAdminItemsInfoGUI() {
        return adminItemsInfoGUI;
    }

    public dev.grzybalson.modules.coinflip.CoinflipManager getCoinflipManager() {
        return coinflipManager;
    }

    public dev.grzybalson.modules.zywioly.ZywiolyManager getZywiolyManager() {
        return zywiolyManager;
    }

    public dev.grzybalson.modules.killeffect.KillEffectManager getKillEffectManager() {
        return killEffectManager;
    }

    public EntityLimitListener getEntityLimitListener() {
        return entityLimitListener;
    }

    /**
     * Chat jak od prawdziwego gracza — używane przez Holly-Spoofer (/spf chat).
     * Soft API: wołane reflectem bez twardej zależności.
     */
    public void broadcastSpoofedChat(String nick, String message) {
        broadcastSpoofedChat(nick, message, null, "default");
    }

    public void broadcastSpoofedChat(String nick, String message, java.util.UUID uuid) {
        broadcastSpoofedChat(nick, message, uuid, "default");
    }

    public void broadcastSpoofedChat(String nick, String message, java.util.UUID uuid, String luckPermsGroup) {
        if (nick == null || nick.isBlank() || message == null) {
            return;
        }
        if (configManager == null) {
            return;
        }
        LpcChatConfig config = configManager.getLpcChatConfig();
        if (config == null || !config.isEnableChat()) {
            return;
        }

        String groupName = (luckPermsGroup == null || luckPermsGroup.isBlank()) ? "default" : luckPermsGroup;
        LpcChatConfig.ChatGroup chatGroup = config.findGroup(groupName);
        if (chatGroup == null) {
            chatGroup = config.findBestGroup(java.util.List.of(groupName));
        }
        if (chatGroup == null) {
            chatGroup = config.findGroup("default");
        }
        if (chatGroup == null || chatGroup.getMessageFormat() == null || chatGroup.getMessageFormat().isBlank()) {
            return;
        }

        if (uuid != null && eloManager != null) {
            eloManager.ensureSpoofedElo(uuid);
        }

        String rankingVal = resolveSpoofedRankingValue(uuid);
        String prefix = resolveSpoofedChatPrefix(nick, groupName);
        String nickColor = resolveSpoofedChatNameColor(nick, groupName);

        String formatted = chatGroup.getMessageFormat()
            .replace("{TAG}", prefix != null ? prefix : "")
            .replace("{RANK}", groupName)
            .replace("{NICK}", (nickColor != null ? nickColor : "") + nick)
            .replace("{CLAN}", "");

        formatted = formatted
            .replace("%holly_ranking%", rankingVal)
            .replace("%core_ranking%", rankingVal)
            .replace("%holly_elo%", rankingVal)
            .replace("%core_elo%", rankingVal)
            .replace("%core_points%", rankingVal)
            .replace("%holly_position%", rankingVal)
            .replace("%core_position%", rankingVal)
            .replace("%holly_rank%", rankingVal)
            .replace("%core_rank%", rankingVal);

        if (tablistManager != null) {
            formatted = tablistManager.resolveSpoofedChatPlaceholders(nick, uuid, groupName, formatted);
        } else if (formatted.contains("%")) {
            // Fallback bez tablist — nie pokazuj surowych %holly_*%
            formatted = formatted.replaceAll("%holly_[A-Za-z0-9_]+%", "");
            formatted = formatted.replaceAll("%[A-Za-z0-9_\\-]+%", "");
        }

        sendPublicChatFiltered(uuid, ColorUtils.colorizeWithPlainPlaceholder(formatted, "{MESSAGE}", message));
    }

    /**
     * Lekki format chatu botów — bez tablist placeholderów i zapisu ELO (masowy /spf chat).
     */
    public void broadcastSpoofedChatLight(String nick, String message, java.util.UUID uuid, String luckPermsGroup) {
        if (nick == null || nick.isBlank() || message == null) {
            return;
        }
        if (configManager == null) {
            return;
        }
        LpcChatConfig config = configManager.getLpcChatConfig();
        if (config == null || !config.isEnableChat()) {
            return;
        }

        String groupName = (luckPermsGroup == null || luckPermsGroup.isBlank()) ? "default" : luckPermsGroup;
        LpcChatConfig.ChatGroup chatGroup = config.findGroup(groupName);
        if (chatGroup == null) {
            chatGroup = config.findBestGroup(java.util.List.of(groupName));
        }
        if (chatGroup == null) {
            chatGroup = config.findGroup("default");
        }
        if (chatGroup == null || chatGroup.getMessageFormat() == null || chatGroup.getMessageFormat().isBlank()) {
            return;
        }

        String rankingVal = resolveSpoofedRankingValue(uuid);
        String prefix = resolveSpoofedChatPrefix(nick, groupName);
        String nickColor = resolveSpoofedChatNameColor(nick, groupName);

        String formatted = chatGroup.getMessageFormat()
            .replace("{TAG}", prefix != null ? prefix : "")
            .replace("{RANK}", groupName)
            .replace("{NICK}", (nickColor != null ? nickColor : "") + nick)
            .replace("{CLAN}", "");

        formatted = formatted
            .replace("%holly_ranking%", rankingVal)
            .replace("%core_ranking%", rankingVal)
            .replace("%holly_elo%", rankingVal)
            .replace("%core_elo%", rankingVal)
            .replace("%core_points%", rankingVal)
            .replace("%holly_position%", rankingVal)
            .replace("%core_position%", rankingVal)
            .replace("%holly_rank%", rankingVal)
            .replace("%core_rank%", rankingVal);

        if (tablistManager != null) {
            formatted = tablistManager.resolveSpoofedChatPlaceholders(nick, uuid, groupName, formatted);
        } else if (formatted.contains("%")) {
            formatted = formatted.replaceAll("%holly_[A-Za-z0-9_]+%", "");
            formatted = formatted.replaceAll("%[A-Za-z0-9_\\-]+%", "");
        }

        sendPublicChatFiltered(uuid, ColorUtils.colorizeWithPlainPlaceholder(formatted, "{MESSAGE}", message));
    }

    public void broadcastSpoofedChatLight(String nick, String message, java.util.UUID uuid) {
        broadcastSpoofedChatLight(nick, message, uuid, "default");
    }

    private void sendPublicChatFiltered(java.util.UUID speakerUuid, net.kyori.adventure.text.Component message) {
        if (message == null) {
            return;
        }
        IgnoreManager ignore = ignoreManager;
        if (ignore == null) {
            Bukkit.broadcast(message);
            return;
        }
        for (org.bukkit.entity.Player viewer : Bukkit.getOnlinePlayers()) {
            if (speakerUuid != null) {
                if (ignore.isIgnored(viewer.getUniqueId(), speakerUuid)) {
                    continue;
                }
            } else if (ignore.isIgnoringAll(viewer.getUniqueId())) {
                continue;
            }
            viewer.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private String resolveSpoofedRankingValue(java.util.UUID uuid) {
        if (uuid != null && eloManager != null) {
            return String.valueOf(eloManager.getElo(uuid));
        }
        return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(950, 1201));
    }

    private String resolveSpoofedChatPrefix(String nick, String groupName) {
        if (tablistManager != null) {
            String prefix = tablistManager.resolveSpoofedChatPrefix(nick, groupName);
            if (prefix != null && !prefix.isBlank()) {
                return prefix;
            }
        }
        String prefix = resolveSpoofedGroupPrefix(groupName);
        if (prefix != null && !prefix.isBlank()) {
            return prefix;
        }
        return dev.grzybalson.utils.TexturePackRankIcons.prefixFor(groupName);
    }

    private String resolveSpoofedChatNameColor(String nick, String groupName) {
        if (tablistManager != null) {
            String color = tablistManager.resolveSpoofedChatNameColor(nick, groupName);
            if (color != null && !color.isBlank()) {
                return color;
            }
        }
        return resolveSpoofedNickColor(groupName);
    }

    private String resolveSpoofedGroupPrefix(String groupName) {
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
                return "";
            }
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.group.Group group = lp.getGroupManager().getGroup(groupName);
            if (group == null) {
                return "";
            }
            String prefix = group.getCachedData().getMetaData().getPrefix();
            return prefix != null ? prefix : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String resolveSpoofedNickColor(String groupName) {
        if (tablistManager != null) {
            String color = tablistManager.getRankNameColorForGroup(groupName);
            return color != null ? color : "";
        }
        return "";
    }
}
