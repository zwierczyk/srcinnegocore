package dev.grzybalson.core;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.AnarchiaGGSettings;
import dev.grzybalson.config.ConfigManager;
import dev.grzybalson.config.ModuleConfigManager;
import dev.grzybalson.config.modules.coinflip.CoinflipConfig;
import dev.grzybalson.managers.ActionBarManager;
import dev.grzybalson.modules.achievement.AchievementListener;
import dev.grzybalson.modules.generator.GeneratorManager;
import dev.grzybalson.modules.tops.TopsManager;
import dev.grzybalson.modules.unique.UniqueItemService;

import org.bukkit.Bukkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class CoreReloadService {

    private final HollyCore plugin;
    private final Map<String, Consumer<HollyCore>> moduleReloaders;

    public CoreReloadService(HollyCore plugin) {
        this.plugin = plugin;
        this.moduleReloaders = buildModuleReloaders();
    }

    private static final List<String> MODULE_SUGGESTIONS = List.of(
        "all", "plugin", "config", "core", "upgrade", "piniata", "tablist", "scoreboard", "sidebar", "tops", "top",
        "discordreward", "zonebox", "blockeconomy", "daily", "cases", "generator",
        "killeffect", "customhit", "flamehub-customitems", "flamehub", "szaman", "sigil", "mission", "misje", "dropkeys", "klucze", "patron", "lockedfeatures", "mobs", "bossbar", "clearlag", "afk", "codes",
        "automessage", "autocommands", "enderchest", "itemsync", "renderdistance", "worldborder", "pickup",
        "turbodrop", "antigrief", "title", "titles", "guis", "market", "kit", "trener", "anarchia", "customitems", "oghub-customitems",
        "oghub-szafka", "szafka", "mslf-kostiumy", "mkostiumy", "masakra-szafka",
        "combat", "anticheat", "elo", "clans", "achievement", "event", "coinflip", "zywioly", "warps", "home", "spawn",
        "messages", "joinquit", "ranks", "media", "czek", "sounds", "rewardnotification", "player", "gracz",
        "boss", "groupteleport", "teparka", "itemstorage", "unique", "vanish", "creatormode", "chat",
        "backup", "wallet", "iplimit", "hearts", "armorstars", "rtp", "shop", "economy", "restart", "timer", "worlds", "itemshop",
        "elytra", "antilag",
        "loadnew", "plugin", "jar"
    );

    public List<String> getAvailableModules() {
        return MODULE_SUGGESTIONS;
    }

    public void reload(String moduleId) {
        String target = normalize(moduleId);

        if ("all".equals(target)) {
            reloadAll();
            return;
        }

        if ("config".equals(target)) {
            plugin.getConfigManager().reloadAllConfigs();
            plugin.getModuleConfigManager().reloadAll();
            return;
        }

        if ("plugin".equals(target) || "jar".equals(target) || "loadnew".equals(target)) {
            reloadPluginLive();
            return;
        }

        Consumer<HollyCore> reloader = moduleReloaders.get(target);
        if (reloader == null) {
            throw new IllegalArgumentException("Nie znaleziono modulu: " + target);
        }
        reloader.accept(plugin);
    }

    private void reloadPluginLive() {
        HollyPluginReloader.Result result = HollyPluginReloader.loadNew(plugin, null);
        plugin.getLogger().info("[Core] loadnew (" + result.mode() + "): " + result.message());
        if (result.detail() != null) {
            plugin.getLogger().info("[Core] loadnew detail: " + result.detail());
        }
    }

    private void reloadAll() {
        plugin.reloadAddonToggles();
        plugin.getConfigManager().reloadAllConfigs();
        plugin.getModuleConfigManager().reloadAll();

        if (plugin.getAnarchiaGGConfigManager() != null) {
            plugin.getAnarchiaGGConfigManager().reload();
        }

        scheduleHeavyReload(1L, this::reloadLightModules);
        scheduleHeavyReload(2L, this::reloadDisplayModules);
        scheduleHeavyReload(3L, this::reloadContentModules);
        plugin.refreshDisabledAddonCommands();
    }

    private void reloadLightModules() {
        runIfPresent(plugin.getDiscordRewardManager(), m -> m.reload());
        runIfPresent(plugin.getZoneBoxManager(), m -> {
            m.reload();
            m.reloadBlockEconomy();
            runIfPresent(plugin.getGeneratorManager(), GeneratorManager::syncZoneBoxAllowedBlocks);
        });
        runIfPresent(plugin.getDailyRewardManager(), m -> m.reload());
        runIfPresent(plugin.getGeneratorManager(), GeneratorManager::reload);
        runIfPresent(plugin.getKillEffectManager(), m -> m.reload());
        runIfPresent(plugin.getFlameHubCustomItemsManager(), m -> m.reload());
        runIfPresent(plugin.getCustomHitsService(), m -> m.reload());
        runIfPresent(plugin.getHollyDamageService(), m -> m.reload());
        runIfPresent(plugin.getSzamanManager(), m -> m.reload());
        runIfPresent(plugin.getSigilManager(), m -> m.reload());
        runIfPresent(plugin.getMissionManager(), m -> m.reload());
        runIfPresent(plugin.getDropKeysManager(), m -> m.reload());
        runIfPresent(plugin.getPatronManager(), m -> m.reload());
        runIfPresent(plugin.getLockedFeaturesManager(), m -> m.reload());
        runIfPresent(plugin.getMobsManager(), m -> m.reload());
        runIfPresent(plugin.getBossBarManager(), m -> m.reload());
        runIfPresent(plugin.getClearLagManager(), m -> m.reload());
        runIfPresent(plugin.getAfkManager(), m -> m.reload());
        runIfPresent(plugin.getCodeManager(), m -> m.reload());
        runIfPresent(plugin.getAutoMessageManager(), m -> m.reload());
        runIfPresent(plugin.getAutoCommandsManager(), m -> m.reload());
        runIfPresent(plugin.getRenderDistanceManager(), m -> m.reload());
        runIfPresent(plugin.getWorldBorderManager(), m -> m.reload());
        runIfPresent(plugin.getPickupManager(), m -> m.reload());
        runIfPresent(plugin.getTurboDropManager(), m -> m.reload());
        runIfPresent(plugin.getAntiGriefManager(), m -> m.reload());
        runIfPresent(plugin.getTitleAddonManager(), m -> m.reload());
        runIfPresent(plugin.getCoachManager(), m -> m.reloadRuntime());
        runIfPresent(plugin.getAchievementListener(), AchievementListener::reloadAllowedBlocks);
        runIfPresent(plugin.getEventManager(), m -> m.reload());
        runIfPresent(plugin.getBossManager(), m -> m.reload());
        runIfPresent(plugin.getGroupTeleportManager(), m -> m.reload());
        runIfPresent(plugin.getItemStorageManager(), m -> m.reload());
        runIfPresent(plugin.getUniqueItemService(), UniqueItemService::reloadMaterials);
        runIfPresent(plugin.getRewardNotificationManager(), m -> m.reload());
        runIfPresent(plugin.getActionBarManager(), ActionBarManager::refreshCombatNotificationType);
        runIfPresent(plugin.getTimerCommand(), m -> m.reload());
        runIfPresent(plugin.getWorldCommand(), m -> m.reload());
        runIfPresent(plugin.getItemShopCommand(), m -> m.reload());
    }

    private void reloadDisplayModules() {
        if (!plugin.isEnabled()) {
            return;
        }
        runIfPresent(plugin.getSidebarManager(), m -> m.reload());
        runIfPresent(plugin.getTablistManager(), m -> m.reload());
        runIfPresent(plugin.getTopsManager(), TopsManager::reload);
        runIfPresent(plugin.getTopManager(), m -> m.requestRefresh());
    }

    private void reloadContentModules() {
        if (!plugin.isEnabled()) {
            return;
        }
        runIfPresent(plugin.getUpgradeManager(), m -> m.reload());
        runIfPresent(plugin.getPiniataManager(), m -> m.reload());
        runIfPresent(plugin.getCaseManager(), m -> m.reload());
        runIfPresent(plugin.getEnderChestManager(), m -> m.reload());
        runIfPresent(plugin.getInventorySyncManager(), m -> m.reload());
        runIfPresent(plugin.getMinestarOGCustomItemsManager(), m -> m.reload());
        runIfPresent(plugin.getOgHubCustomItemsManager(), m -> m.reload());
        runIfPresent(plugin.getOgHubSzafkaManager(), m -> m.reload());
        runIfPresent(plugin.getMslfKostiumyManager(), m -> m.reload());
        runIfPresent(plugin.getMasakraSzafkaManager(), m -> m.reload());
        runIfPresent(plugin.getMarketManager(), m -> m.load());
        runIfPresent(plugin.getKitManager(), m -> m.loadKits());
        if (plugin.isAddonEnabled("anarchiagg")) {
            AnarchiaGGSettings.reload(plugin);
            if (plugin.getAnarchiaGGModuleManager() != null) {
                plugin.getAnarchiaGGModuleManager().reloadAllModules();
            }
        }
    }

    private void scheduleHeavyReload(long delayTicks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isEnabled()) {
                task.run();
            }
        }, delayTicks);
    }

    private Map<String, Consumer<HollyCore>> buildModuleReloaders() {
        Map<String, Consumer<HollyCore>> reloaders = new LinkedHashMap<>();

        reloaders.put("config", p -> { /* handled in reload() */ });

        Consumer<HollyCore> tablistReload = p -> {
            runIfPresent(p.getSidebarManager(), m -> m.reload());
            runIfPresent(p.getTablistManager(), m -> m.reload());
            runIfPresent(p.getTopsManager(), TopsManager::reload);
        };
        reloaders.put("tablist", tablistReload);
        reloaders.put("scoreboard", tablistReload);
        reloaders.put("sidebar", p -> runIfPresent(p.getSidebarManager(), m -> m.reload()));
        reloaders.put("tops", p -> runIfPresent(p.getTopsManager(), TopsManager::reload));
        reloaders.put("top", p -> runIfPresent(p.getTopManager(), m -> m.requestRefresh()));

        reloaders.put("upgrade", p -> runIfPresent(p.getUpgradeManager(), m -> m.reload()));
        reloaders.put("piniata", p -> runIfPresent(p.getPiniataManager(), m -> m.reload()));
        reloaders.put("discordreward", p -> runIfPresent(p.getDiscordRewardManager(), m -> m.reload()));
        reloaders.put("nagroda", reloaders.get("discordreward"));
        reloaders.put("zonebox", p -> runIfPresent(p.getZoneBoxManager(), m -> {
            m.reload();
            m.reloadBlockEconomy();
            runIfPresent(p.getGeneratorManager(), GeneratorManager::syncZoneBoxAllowedBlocks);
        }));
        reloaders.put("blockeconomy", p -> runIfPresent(p.getZoneBoxManager(), m -> m.reloadBlockEconomy()));
        reloaders.put("daily", p -> runIfPresent(p.getDailyRewardManager(), m -> m.reload()));
        reloaders.put("cases", p -> runIfPresent(p.getCaseManager(), m -> m.reload()));
        reloaders.put("generator", p -> runIfPresent(p.getGeneratorManager(), GeneratorManager::reload));
        reloaders.put("killeffect", p -> runIfPresent(p.getKillEffectManager(), m -> m.reload()));
        reloaders.put("flamehub-customitems", p -> runIfPresent(p.getFlameHubCustomItemsManager(), m -> m.reload()));
        reloaders.put("flamehub", reloaders.get("flamehub-customitems"));
        reloaders.put("customhit", p -> runIfPresent(p.getCustomHitsService(), m -> m.reload()));
        reloaders.put("hollydamage", p -> runIfPresent(p.getHollyDamageService(), m -> m.reload()));
        reloaders.put("damage", reloaders.get("hollydamage"));
        reloaders.put("szaman", p -> runIfPresent(p.getSzamanManager(), m -> m.reload()));
        reloaders.put("sigil", p -> runIfPresent(p.getSigilManager(), m -> m.reload()));
        reloaders.put("patron", p -> runIfPresent(p.getPatronManager(), m -> m.reload()));
        reloaders.put("patroni", reloaders.get("patron"));
        reloaders.put("lockedfeatures", p -> runIfPresent(p.getLockedFeaturesManager(), m -> m.reload()));
        reloaders.put("misje", p -> runIfPresent(p.getMissionManager(), m -> m.reload()));
        reloaders.put("mission", reloaders.get("misje"));
        reloaders.put("dropkeys", p -> runIfPresent(p.getDropKeysManager(), m -> m.reload()));
        reloaders.put("klucze", reloaders.get("dropkeys"));
        reloaders.put("mobs", p -> runIfPresent(p.getMobsManager(), m -> m.reload()));
        reloaders.put("bossbar", p -> runIfPresent(p.getBossBarManager(), m -> m.reload()));
        reloaders.put("clearlag", p -> runIfPresent(p.getClearLagManager(), m -> m.reload()));
        reloaders.put("afk", p -> runIfPresent(p.getAfkManager(), m -> m.reload()));
        reloaders.put("codes", p -> runIfPresent(p.getCodeManager(), m -> m.reload()));
        reloaders.put("kod", reloaders.get("codes"));
        reloaders.put("automessage", p -> runIfPresent(p.getAutoMessageManager(), m -> m.reload()));
        reloaders.put("autocommands", p -> runIfPresent(p.getAutoCommandsManager(), m -> m.reload()));
        reloaders.put("enderchest", p -> runIfPresent(p.getEnderChestManager(), m -> m.reload()));
        reloaders.put("itemsync", p -> runIfPresent(p.getInventorySyncManager(), m -> m.reload()));
        reloaders.put("renderdistance", p -> runIfPresent(p.getRenderDistanceManager(), m -> m.reload()));
        reloaders.put("worldborder", p -> runIfPresent(p.getWorldBorderManager(), m -> m.reload()));
        reloaders.put("pickup", p -> runIfPresent(p.getPickupManager(), m -> m.reload()));
        reloaders.put("odbierz", reloaders.get("pickup"));
        reloaders.put("turbodrop", p -> runIfPresent(p.getTurboDropManager(), m -> m.reload()));
        reloaders.put("antigrief", p -> runIfPresent(p.getAntiGriefManager(), m -> m.reload()));
        reloaders.put("title", p -> runIfPresent(p.getTitleAddonManager(), m -> m.reload()));
        reloaders.put("titles", reloaders.get("title"));
        reloaders.put("market", p -> {
            if (p.getConfigManager().getMarketFiltersConfig() != null) {
                p.getConfigManager().getMarketFiltersConfig().load(true);
            }
            runIfPresent(p.getMarketManager(), m -> {
                m.reloadFilters();
                m.load();
            });
        });
        reloaders.put("kit", p -> runIfPresent(p.getKitManager(), m -> m.loadKits()));
        reloaders.put("trener", p -> runIfPresent(p.getCoachManager(), m -> m.reloadRuntime()));
        reloaders.put("coach", reloaders.get("trener"));
        reloaders.put("anarchia", p -> reloadAnarchia(p));
        reloaders.put("anarchiagg", reloaders.get("anarchia"));
        reloaders.put("customitems", p -> {
            runIfPresent(p.getMinestarOGCustomItemsManager(), m -> m.reload());
            runIfPresent(p.getOgHubCustomItemsManager(), m -> m.reload());
        });
        reloaders.put("oghub-customitems", reloaders.get("customitems"));
        reloaders.put("oghub-szafka", p -> runIfPresent(p.getOgHubSzafkaManager(), m -> m.reload()));
        reloaders.put("szafka", p -> {
            runIfPresent(p.getOgHubSzafkaManager(), m -> m.reload());
            runIfPresent(p.getMasakraSzafkaManager(), m -> m.reload());
        });
        reloaders.put("mslf-kostiumy", p -> runIfPresent(p.getMslfKostiumyManager(), m -> m.reload()));
        reloaders.put("mkostiumy", reloaders.get("mslf-kostiumy"));
        reloaders.put("masakra-szafka", p -> runIfPresent(p.getMasakraSzafkaManager(), m -> m.reload()));

        reloaders.put("combat", this::reloadCombat);
        reloaders.put("anticheat", p -> runIfPresent(p.getAnticheatManager(), m -> m.reload()));
        reloaders.put("elo", p -> {
            ModuleConfigManager modules = p.getModuleConfigManager();
            if (modules != null && modules.getEloSettingsConfig() != null) {
                modules.getEloSettingsConfig().load(true);
            }
        });
        reloaders.put("clans", p -> p.getConfigManager().reloadClanConfig());
        reloaders.put("clan", reloaders.get("clans"));
        reloaders.put("warps", p -> {
            ModuleConfigManager modules = p.getModuleConfigManager();
            if (modules == null) {
                return;
            }
            if (modules.getWarpsConfig() != null) {
                modules.getWarpsConfig().load(true);
            }
            if (modules.getWarpDataConfig() != null) {
                modules.getWarpDataConfig().load(true);
            }
        });
        reloaders.put("home", p -> {
            ModuleConfigManager modules = p.getModuleConfigManager();
            if (modules != null && modules.getHomeConfig() != null) {
                modules.getHomeConfig().load(true);
            }
        });
        reloaders.put("spawn", p -> {
            if (p.getConfigManager().getSpawnConfig() != null) {
                p.getConfigManager().getSpawnConfig().load(true);
            }
        });
        reloaders.put("player", p -> p.getConfigManager().reloadPlayerCommandConfig());
        reloaders.put("gracz", reloaders.get("player"));
        reloaders.put("messages", p -> {
            if (p.getConfigManager().getMessagesConfig() != null) {
                p.getConfigManager().getMessagesConfig().load(true);
            }
            ModuleConfigManager modules = p.getModuleConfigManager();
            if (modules != null && modules.getCommandsMessagesConfig() != null) {
                modules.getCommandsMessagesConfig().load(true);
            }
        });
        reloaders.put("joinquit", p -> {
            ModuleConfigManager modules = p.getModuleConfigManager();
            if (modules != null && modules.getJoinQuitConfig() != null) {
                modules.getJoinQuitConfig().load(true);
            }
        });
        reloaders.put("ranks", p -> p.getModuleConfigManager().reloadRanksGui());
        reloaders.put("media", p -> p.getModuleConfigManager().reloadMediaGui());
        reloaders.put("czek", p -> {
            ModuleConfigManager modules = p.getModuleConfigManager();
            if (modules != null && modules.getCzekConfig() != null) {
                modules.getCzekConfig().load(true);
            }
        });
        reloaders.put("coinflip", this::reloadCoinflip);
        reloaders.put("zywioly", p -> {
            if (p.getZywiolyManager() != null) {
                p.getZywiolyManager().reload();
            }
        });
        reloaders.put("sounds", p -> {
            if (p.getConfigManager().getSoundsConfig() != null) {
                p.getConfigManager().getSoundsConfig().load(true);
            }
        });
        reloaders.put("achievement", p -> runIfPresent(p.getAchievementListener(), AchievementListener::reloadAllowedBlocks));
        reloaders.put("event", p -> runIfPresent(p.getEventManager(), m -> m.reload()));
        reloaders.put("rewardnotification", p -> runIfPresent(p.getRewardNotificationManager(), m -> m.reload()));
        reloaders.put("boss", p -> runIfPresent(p.getBossManager(), m -> m.reload()));
        reloaders.put("groupteleport", p -> runIfPresent(p.getGroupTeleportManager(), m -> m.reload()));
        reloaders.put("teparka", reloaders.get("groupteleport"));
        reloaders.put("gtp", reloaders.get("groupteleport"));
        reloaders.put("itemstorage", p -> runIfPresent(p.getItemStorageManager(), m -> m.reload()));
        reloaders.put("unique", p -> runIfPresent(p.getUniqueItemService(), UniqueItemService::reloadMaterials));
        reloaders.put("vanish", p -> p.getConfigManager().reloadVanishConfig());
        reloaders.put("creatormode", p -> p.getConfigManager().reloadCreatorModeConfig());
        reloaders.put("chat", p -> {
            p.getConfigManager().reloadChatConfig();
            p.getConfigManager().reloadLpcChatConfig();
        });
        reloaders.put("lpc", reloaders.get("chat"));
        reloaders.put("backup", p -> p.getConfigManager().reloadBackupConfig());
        reloaders.put("wallet", p -> p.getConfigManager().reloadWalletConfig());
        reloaders.put("portfel", reloaders.get("wallet"));
        reloaders.put("iplimit", p -> p.getConfigManager().reloadIpLimitConfig());
        reloaders.put("hearts", p -> {
            if (p.getConfigManager().getHeartsConfig() != null) {
                p.getConfigManager().getHeartsConfig().load(true);
            }
        });
        reloaders.put("armorstars", p -> {
            p.getConfigManager().reloadArmorStarsConfig();
            runIfPresent(p.getTablistManager(), m -> {
                m.refreshSpooferNameTags();
                m.refreshAllIdentities();
            });
        });
        reloaders.put("rtp", p -> {
            if (p.getConfigManager().getRtpConfig() != null) {
                p.getConfigManager().getRtpConfig().load(true);
            }
        });
        reloaders.put("shop", p -> {
            if (p.getConfigManager().getShopConfig() != null) {
                p.getConfigManager().getShopConfig().load(true);
            }
        });
        reloaders.put("economy", p -> {
            if (p.getConfigManager().getEconomyConfig() != null) {
                p.getConfigManager().getEconomyConfig().load(true);
            }
        });
        reloaders.put("restart", p -> {
            if (p.getConfigManager().getRestartConfig() != null) {
                p.getConfigManager().getRestartConfig().load(true);
            }
        });
        reloaders.put("core", p -> {
            if (p.getConfigManager().getMainConfig() != null) {
                p.getConfigManager().getMainConfig().load(true);
            }
        });
        reloaders.put("main", reloaders.get("core"));
        reloaders.put("guis", this::reloadGuis);
        reloaders.put("timer", p -> runIfPresent(p.getTimerCommand(), m -> m.reload()));
        reloaders.put("worlds", p -> runIfPresent(p.getWorldCommand(), m -> m.reload()));
        reloaders.put("itemshop", p -> runIfPresent(p.getItemShopCommand(), m -> m.reload()));

        return reloaders;
    }

    private void reloadCombat(HollyCore plugin) {
        ConfigManager configs = plugin.getConfigManager();
        if (configs.getCombatConfig() != null) {
            configs.getCombatConfig().load(true);
            configs.getCombatConfig().sanitizeKillSoundSettings();
        }
        runIfPresent(plugin.getActionBarManager(), ActionBarManager::refreshCombatNotificationType);
    }

    private void reloadCoinflip(HollyCore plugin) {
        ModuleConfigManager modules = plugin.getModuleConfigManager();
        if (modules == null) {
            return;
        }
        CoinflipConfig config = modules.getCoinflipConfig();
        if (config == null) {
            return;
        }
        config.load(true);
        if (config.migrateBrokenDefaults()) {
            config.save();
        }
    }

    private void reloadGuis(HollyCore plugin) {
        ConfigManager configs = plugin.getConfigManager();
        if (configs.getGuiConfig() != null) {
            configs.getGuiConfig().load(true);
        }
        if (configs.getGuiTemplateConfig() != null) {
            configs.getGuiTemplateConfig().load(true);
        }
        ModuleConfigManager modules = plugin.getModuleConfigManager();
        if (modules != null && modules.getGuiTemplateConfig() != null) {
            modules.getGuiTemplateConfig().load(true);
        }
        runIfPresent(plugin.getStaticMenuManager(), m -> m.reload());
    }

    private void reloadAnarchia(HollyCore plugin) {
        if (plugin.getAnarchiaGGConfigManager() != null) {
            plugin.getAnarchiaGGConfigManager().reload();
        }
        AnarchiaGGSettings.reload(plugin);
        if (plugin.getAnarchiaGGModuleManager() != null) {
            plugin.getAnarchiaGGModuleManager().reloadAllModules();
        }
    }

    private static String normalize(String moduleId) {
        if (moduleId == null || moduleId.isBlank()) {
            return "all";
        }
        return moduleId.trim().toLowerCase(Locale.ROOT);
    }

    private static <T> void runIfPresent(T instance, Consumer<T> action) {
        if (instance != null) {
            action.accept(instance);
        }
    }
}
