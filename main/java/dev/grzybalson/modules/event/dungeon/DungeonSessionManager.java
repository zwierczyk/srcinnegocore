package dev.grzybalson.modules.event.dungeon;

import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.event.DungeonParty;
import dev.grzybalson.modules.event.EventManager;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.PlayerHealthBase;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonSessionManager {

    private final HollyCore plugin;
    private final EventManager eventManager;
    private final DungeonWorldService worldService;
    private final DungeonLocations locations;
    private final File settingsFile;
    private YamlConfiguration settings;
    private final Map<UUID, DungeonSession> sessionsByPlayer = new ConcurrentHashMap<>();
    private final Map<String, DungeonSession> sessionsByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonSession> rejoinWindows = new ConcurrentHashMap<>();
    private final Set<UUID> ignoreWorldQuit = ConcurrentHashMap.newKeySet();

    private final Map<String, java.util.concurrent.CompletableFuture<World>> pendingClones = new ConcurrentHashMap<>();

    public DungeonSessionManager(HollyCore plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.worldService = new DungeonWorldService(plugin);
        File locFile = new File(plugin.getDataFolder(), "addons/dungeon/locations.yml");
        this.locations = new DungeonLocations(locFile);
        this.settingsFile = new File(plugin.getDataFolder(), "addons/dungeon/dungeon.yml");
        reloadSettings();
        Bukkit.getScheduler().runTaskLater(plugin, this::restorePersistedSessions, 40L);
    }

    public void reloadSettings() {
        try {
            File parent = settingsFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (!settingsFile.exists()) {
                try {
                    plugin.saveResource("addons/dungeon/dungeon.yml", false);
                } catch (IllegalArgumentException missing) {
                    plugin.getLogger().warning("[Dungeon] Brak addons/dungeon/dungeon.yml w jarze — tworze pusty config.");
                    settingsFile.createNewFile();
                }
            }
            settings = YamlConfiguration.loadConfiguration(settingsFile);
            boolean balanceChanged = false;
            if (!settings.isSet("coin-pickup-vault")) {
                settings.set("coin-pickup-vault", 1);
                balanceChanged = true;
            }
            if (!settings.isSet("mob-hp-mult")) {
                settings.set("mob-hp-mult", 0.75D);
                balanceChanged = true;
            }
            if (!settings.isSet("boss-hp-mult")) {
                settings.set("boss-hp-mult", 0.72D);
                balanceChanged = true;
            }
            // Stare agresywne skalowanie party → latwiejsze wartosci.
            if (settings.getDouble("party-hp-per-player", 0.14D) >= 0.28D) {
                settings.set("party-hp-per-player", 0.14D);
                balanceChanged = true;
            }
            if (settings.getDouble("party-boss-hp-per-player", 0.12D) >= 0.25D) {
                settings.set("party-boss-hp-per-player", 0.12D);
                balanceChanged = true;
            }
            if (settings.getInt("party-mobs-per-player", 1) >= 2) {
                settings.set("party-mobs-per-player", 1);
                balanceChanged = true;
            }
            if (settings.getDouble("mob-damage-mult", 0.60D) >= 0.68D) {
                settings.set("mob-damage-mult", 0.60D);
                balanceChanged = true;
            }
            if (!settings.isSet("mob-damage-mult")) {
                settings.set("mob-damage-mult", 0.60D);
                balanceChanged = true;
            }
            if (settings.getDouble("solo-boss-hp-mult", 0.55D) >= 0.60D) {
                settings.set("solo-boss-hp-mult", 0.55D);
                balanceChanged = true;
            }
            if (balanceChanged) {
                try {
                    settings.save(settingsFile);
                    plugin.getLogger().info("[Dungeon] Zaktualizowano balans HP/skalowania party (latwiej).");
                } catch (Exception ignored) {
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Dungeon] Blad ladowania dungeon.yml", exception);
            settings = new YamlConfiguration();
        }
        locations.reload();
    }

    public DungeonLocations getLocations() {
        return locations;
    }

    public YamlConfiguration getSettings() {
        return settings;
    }

    public boolean setCoinPrizeAmount(int amount) {
        if (settings == null) {
            reloadSettings();
        }
        settings.set("coin-prize-amount", Math.max(0, amount));
        if (!settings.contains("coin-prize-command")) {
            settings.set("coin-prize-command", "eco give {player} {amount}");
        }
        try {
            settings.save(settingsFile);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Dungeon] Blad zapisu coin-prize-amount", exception);
            return false;
        }
    }

    /**
     * Vault money multiplier: free = x1; 1 ticket = x3 … 5 tickets = x7.
     */
    public static int moneyMultiplier(int ticketCost) {
        if (ticketCost <= 0) {
            return 1;
        }
        return ticketCost + 2;
    }

    /** Ile itemow z bossa: darmowe = 1, N ticketow = N itemow. */
    public static int bossItemCount(int ticketCost) {
        if (ticketCost <= 0) {
            return 1;
        }
        return ticketCost;
    }

    public boolean setCoinRewardAmount(int amount) {
        if (settings == null) {
            reloadSettings();
        }
        settings.set("coin-reward-amount", Math.max(0, amount));
        try {
            settings.save(settingsFile);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Dungeon] Blad zapisu coin-reward-amount", exception);
            return false;
        }
    }

    public List<ItemStack> getBossRewards(int bossLevel) {
        return getBossRewards(bossLevel, true);
    }

    public List<ItemStack> getBossRewards(int bossLevel, boolean premium) {
        if (settings == null) {
            reloadSettings();
        }
        migrateLegacyBossRewardsIfNeeded();
        // Osobne pule — bez fallbacku free↔premium (GUI i gra widza tylko wybrana).
        return readBossRewardList(bossRewardsPath(bossLevel, premium));
    }

    private List<ItemStack> readBossRewardList(String path) {
        List<ItemStack> out = new ArrayList<>();
        List<?> raw = settings.getList(path);
        if (raw == null || raw.isEmpty()) {
            // Czasem YAML trzyma liste pod sekcja zamiast List.
            if (settings.isConfigurationSection(path)) {
                org.bukkit.configuration.ConfigurationSection section = settings.getConfigurationSection(path);
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        ItemStack stack = coerceItemStack(section.get(key));
                        if (stack != null) {
                            out.add(stack);
                        }
                    }
                }
            }
            return out;
        }
        for (Object entry : raw) {
            ItemStack stack = coerceItemStack(entry);
            if (stack != null) {
                out.add(stack);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack coerceItemStack(Object entry) {
        if (entry == null) {
            return null;
        }
        if (entry instanceof ItemStack stack) {
            if (stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                return null;
            }
            return stack.clone();
        }
        if (entry instanceof org.bukkit.configuration.ConfigurationSection section) {
            return coerceItemStack(section.getValues(false));
        }
        if (entry instanceof Map<?, ?> map) {
            try {
                java.util.LinkedHashMap<String, Object> typed = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        typed.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                Object deserialized = org.bukkit.configuration.serialization.ConfigurationSerialization
                    .deserializeObject(typed);
                if (deserialized instanceof ItemStack stack) {
                    if (stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                        return null;
                    }
                    return stack.clone();
                }
                ItemStack fallback = ItemStack.deserialize(typed);
                if (fallback != null && fallback.getType() != Material.AIR && fallback.getAmount() > 0) {
                    return fallback;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public boolean saveBossRewards(int bossLevel, List<ItemStack> items) {
        return saveBossRewards(bossLevel, true, items);
    }

    public boolean saveBossRewards(int bossLevel, boolean premium, List<ItemStack> items) {
        if (bossLevel < 1 || bossLevel > 3) {
            return false;
        }
        if (settings == null) {
            reloadSettings();
        }
        migrateLegacyBossRewardsIfNeeded();
        List<ItemStack> cleaned = new ArrayList<>();
        if (items != null) {
            for (ItemStack stack : items) {
                if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                    cleaned.add(stack.clone());
                }
            }
        }
        String path = bossRewardsPath(bossLevel, premium);
        // Zapisuje TYLKO wybrana pule — druga (free/premium) zostaje nietknieta.
        settings.set(path, cleaned);
        try {
            settings.save(settingsFile);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[Dungeon] Blad zapisu " + path, exception);
            return false;
        }
    }

    private static String bossRewardsPath(int bossLevel, boolean premium) {
        return "boss-rewards." + (premium ? "premium" : "free") + "." + bossLevel;
    }

    /**
     * Stary format boss-rewards.1 → free.1 i premium.1 tylko gdy dana strona jest pusta.
     * Nigdy nie nadpisuje juz ustawionych nagrod.
     */
    private void migrateLegacyBossRewardsIfNeeded() {
        if (settings == null) {
            return;
        }
        boolean changed = false;
        for (int level = 1; level <= 3; level++) {
            String legacy = "boss-rewards." + level;
            List<ItemStack> legacyItems = readBossRewardList(legacy);
            if (legacyItems.isEmpty() && !settings.isList(legacy) && !settings.isConfigurationSection(legacy)) {
                continue;
            }
            if (!legacyItems.isEmpty()) {
                String freePath = bossRewardsPath(level, false);
                String premiumPath = bossRewardsPath(level, true);
                if (readBossRewardList(freePath).isEmpty()) {
                    settings.set(freePath, cloneStacks(legacyItems));
                    changed = true;
                }
                if (readBossRewardList(premiumPath).isEmpty()) {
                    settings.set(premiumPath, cloneStacks(legacyItems));
                    changed = true;
                }
            }
            if (settings.contains(legacy)) {
                settings.set(legacy, null);
                changed = true;
            }
        }
        // Dopilnuj struktury free/premium.1-3 bez czyszczenia list.
        for (int level = 1; level <= 3; level++) {
            for (boolean premium : new boolean[]{false, true}) {
                String path = bossRewardsPath(level, premium);
                if (!settings.isSet(path)) {
                    settings.set(path, new ArrayList<ItemStack>());
                    changed = true;
                }
            }
        }
        if (changed) {
            try {
                settings.save(settingsFile);
            } catch (Exception exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[Dungeon] Blad migracji boss-rewards", exception);
            }
        }
    }

    private static List<ItemStack> cloneStacks(List<ItemStack> source) {
        List<ItemStack> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (ItemStack stack : source) {
            if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                out.add(stack.clone());
            }
        }
        return out;
    }

    /** Collects all non-air items from the player's 36 storage slots. */
    public static List<ItemStack> collectInventoryRewards(Player player) {
        List<ItemStack> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        if (storage == null) {
            return out;
        }
        for (ItemStack stack : storage) {
            if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                out.add(stack.clone());
            }
        }
        return out;
    }

    public DungeonWorldService getWorldService() {
        return worldService;
    }

    public void startFromParty(DungeonParty party) {
        for (DungeonParty.Member member : party.getMemberList()) {
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online == null || !online.isOnline()) {
                party.setReady(member.getUuid(), false);
                eventManager.refreshLobby(party);
                return;
            }
            if (!eventManager.hasCleanInventoryForDungeon(online)) {
                eventManager.send(online, "inventory-not-empty");
                party.setReady(member.getUuid(), false);
                eventManager.refreshLobby(party);
                return;
            }
        }

        if (!locations.isReadyForStart()) {
            for (DungeonParty.Member member : party.getMemberList()) {
                Player online = Bukkit.getPlayer(member.getUuid());
                if (online != null) {
                    online.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.not-configured",
                        "&#FF0000✘ &8Ξ &cDungeon nie jest skonfigurowany!")));
                    eventManager.playError(online);
                }
            }
            party.resetReady();
            eventManager.refreshLobby(party);
            return;
        }

        World template = Bukkit.getWorld(DungeonWorldService.TEMPLATE);
        if (template == null && !new File(Bukkit.getWorldContainer(), DungeonWorldService.TEMPLATE + "/level.dat").exists()) {
            for (DungeonParty.Member member : party.getMemberList()) {
                Player online = Bukkit.getPlayer(member.getUuid());
                if (online != null) {
                    online.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.world-missing",
                        "&#FF0000✘ &8Ξ &cBrak swiata ninjaevent!")));
                }
            }
            party.resetReady();
            eventManager.refreshLobby(party);
            return;
        }

        party.setStarted(true);
        for (DungeonParty.Member member : party.getMemberList()) {
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online == null) {
                continue;
            }
            online.closeInventory();
            online.setGameMode(GameMode.SPECTATOR);
            online.setAllowFlight(true);
            online.setFlying(true);
            online.setFlySpeed(0.0F);
            ColorUtils.sendTitle(online,
                "<gradient:#00C1FF:#00FFC1><bold>START</bold></gradient></shadow>",
                "Wchodzimy do dungeona...</shadow>",
                5, 40, 8);
            online.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.cloning",
                "&#FFAA00⚠ &8Ξ &eJuz prawie... dungeon sie laduje!")));
            eventManager.playSuccess(online);
        }
        beginCloneAndStart(party);
    }

    /** Start klonowania w trakcie 5s lotu — mniej lagow na koncu. */
    public void preloadClone(DungeonParty party) {
        if (party == null) {
            return;
        }
        pendingClones.computeIfAbsent(party.getCode(), code -> worldService.cloneTemplateAsync());
    }

    public void cancelPreload(DungeonParty party) {
        if (party == null) {
            return;
        }
        java.util.concurrent.CompletableFuture<World> future = pendingClones.remove(party.getCode());
        if (future != null) {
            future.whenComplete((world, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (world != null) {
                    worldService.unloadAndDelete(world);
                }
            }));
        }
    }

    private void beginCloneAndStart(DungeonParty party) {
        java.util.concurrent.CompletableFuture<World> future = pendingClones.remove(party.getCode());
        if (future == null) {
            future = worldService.cloneTemplateAsync();
        }
        future.whenComplete((world, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || world == null) {
                for (DungeonParty.Member member : party.getMemberList()) {
                    Player online = Bukkit.getPlayer(member.getUuid());
                    if (online != null) {
                        online.setGravity(true);
                        online.setGameMode(GameMode.ADVENTURE);
                        online.setAllowFlight(false);
                        online.setFlying(false);
                        online.setFlySpeed(0.1F);
                        online.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.clone-fail",
                            "&#FF0000✘ &8Ξ &cNie udalo sie sklonowac swiata dungeona!")));
                        eventManager.giveTickets(online, party.getTicketCost());
                    }
                }
                party.setStarted(false);
                party.resetReady();
                eventManager.refreshLobby(party);
                return;
            }

            DungeonSession session = new DungeonSession(plugin, this, party, world, locations, settings);
            sessionsByWorld.put(world.getName(), session);
            for (UUID uuid : session.getPlayers()) {
                sessionsByPlayer.put(uuid, session);
            }
            for (DungeonParty.Member member : party.getMemberList()) {
                sessionsByPlayer.put(member.getUuid(), session);
            }
            for (DungeonParty.Member member : party.getMemberList()) {
                Player online = Bukkit.getPlayer(member.getUuid());
                if (online != null) {
                    online.setGravity(true);
                    online.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.starting",
                        "&#00FF3B✔ &8Ξ &#4EFA76Jestescie w dungeonie!")));
                    eventManager.playSuccess(online);
                }
            }
            session.start();
        }));
    }

    public void bindPlayer(UUID uuid, DungeonSession session) {
        if (uuid != null && session != null) {
            sessionsByPlayer.put(uuid, session);
        }
    }

    public DungeonSession getByPlayer(UUID uuid) {
        return sessionsByPlayer.get(uuid);
    }

    public DungeonSession getByWorld(String worldName) {
        return sessionsByWorld.get(worldName);
    }

    public void markRejoin(UUID uuid, DungeonSession session) {
        rejoinWindows.put(uuid, session);
    }

    public boolean tryRejoin(Player player) {
        DungeonSession session = rejoinWindows.get(player.getUniqueId());
        if (session == null) {
            session = sessionsByPlayer.get(player.getUniqueId());
        }
        if (session == null) {
            return false;
        }
        boolean ok = session.tryRejoin(player);
        if (ok) {
            rejoinWindows.remove(player.getUniqueId());
            sessionsByPlayer.put(player.getUniqueId(), session);
        }
        return ok;
    }

    public void markIgnoreWorldQuit(UUID uuid, long ticks) {
        ignoreWorldQuit.add(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> ignoreWorldQuit.remove(uuid), Math.max(1L, ticks));
    }

    public boolean shouldIgnoreWorldQuit(UUID uuid) {
        return ignoreWorldQuit.contains(uuid);
    }

    public boolean isInActiveDungeon(UUID uuid) {
        // Takze Phase.ENDED (5s do endSession) — inaczej /lobby wyciaga EQ dungeona.
        return sessionsByPlayer.containsKey(uuid);
    }

    /** Czy gracz jest jeszcze przypiety do sesji (tez po win/lose, przed teleportem). */
    public boolean isBoundToDungeon(UUID uuid) {
        return sessionsByPlayer.containsKey(uuid);
    }

    /** Natychmiast zdejmij kit dungeona (przed /lobby / quit). */
    public void stripDungeonInventory(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        // Zamknij GUI zamiast clear top — inaczej portable shulker / EC / skrzynia
        // dostaje pusty writeback i gubi itemy.
        if (plugin.getShulkerBoxListener() != null
            && plugin.getShulkerBoxListener().isViewing(player.getUniqueId())) {
            plugin.getShulkerBoxListener().flush(player);
        }
        try {
            player.closeInventory();
        } catch (Throwable ignored) {
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
        player.updateInventory();
        resetDungeonPlayerStats(player);
        if (plugin.getInventorySyncManager() != null) {
            plugin.getInventorySyncManager().suppressSaveBriefly(player.getUniqueId(), 15_000L);
        }
    }

    /**
     * Zdejmij bonus HP / absorption / buffy dungeona — inaczej serca zostają po wyjściu.
     */
    public void resetDungeonPlayerStats(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
        } catch (Throwable ignored) {
        }
        try {
            player.setAbsorptionAmount(0.0D);
        } catch (Throwable ignored) {
        }
        PlayerHealthBase.resetBaseMaxHealth(plugin, player, true);
        try {
            if (plugin.getOgHubSzafkaManager() != null
                && plugin.getOgHubSzafkaManager().getBoostHandler() != null) {
                plugin.getOgHubSzafkaManager().getBoostHandler().refreshHearts(player);
            }
        } catch (Throwable ignored) {
        }
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    public void endSession(DungeonSession session, boolean won) {
        if (session == null) {
            return;
        }
        String worldName = session.getWorld() != null ? session.getWorld().getName() : null;
        if (worldName != null && !sessionsByWorld.containsKey(worldName)
            && session.getPlayers().stream().noneMatch(sessionsByPlayer::containsKey)) {
            // Juz zakonczona / drugi call po adminForceEnd.
            return;
        }
        session.shutdown();
        for (UUID uuid : session.getPlayers()) {
            sessionsByPlayer.remove(uuid);
            rejoinWindows.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                int refund = session.takeBankedTickets(uuid);
                stripDungeonInventory(player);
                forceSurvivalNoFlight(player);
                if (refund > 0) {
                    eventManager.giveTicketsSilent(player, refund);
                }
                teleportToServerSpawn(player);
            }
        }
        if (worldName != null) {
            sessionsByWorld.remove(worldName);
            deleteSessionSnapshot(worldName);
            worldService.unloadAndDelete(session.getWorld());
        }
        // Party juz skonczone — nie oddawaj ticketow wejscia (zuzyte na gre).
        eventManager.disbandParty(session.getParty(), false);
    }

    private File activeSessionsDir() {
        File dir = new File(plugin.getDataFolder(), "addons/dungeon/active-sessions");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private File snapshotFile(String worldName) {
        return new File(activeSessionsDir(), worldName + ".yml");
    }

    private void saveSessionSnapshot(DungeonSession session) {
        if (session == null || session.getWorld() == null) {
            return;
        }
        String worldName = session.getWorld().getName();
        try {
            org.bukkit.configuration.file.YamlConfiguration yaml = session.toRestartSnapshot();
            yaml.save(snapshotFile(worldName));
            plugin.getLogger().info("[Dungeon] Zapisano sesje do kontynuacji po restarcie: " + worldName);
        } catch (Exception exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "[Dungeon] Nie udalo sie zapisac sesji " + worldName, exception);
        }
    }

    private void deleteSessionSnapshot(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }
        File file = snapshotFile(worldName);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("[Dungeon] Nie usunieto snapshotu: " + file.getName());
        }
    }

    /**
     * Restart: nie koncz runow — zapisz postep, zostaw swiaty, gracze wroca po starcie.
     */
    public void shutdownAll() {
        for (DungeonSession session : new java.util.ArrayList<>(sessionsByWorld.values())) {
            if (session == null || session.getPhase() == DungeonSession.Phase.ENDED) {
                continue;
            }
            try {
                session.prepareForServerRestart();
                saveSessionSnapshot(session);
                World world = session.getWorld();
                if (world != null) {
                    World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                    if (plugin.getConfigManager() != null && plugin.getConfigManager().getSpawnConfig() != null
                        && plugin.getConfigManager().getSpawnConfig().isSpawnSet()) {
                        var cfg = plugin.getConfigManager().getSpawnConfig();
                        World spawnWorld = Bukkit.getWorld(cfg.getWorld());
                        if (spawnWorld != null) {
                            fallback = spawnWorld;
                        }
                    }
                    if (fallback != null) {
                        Location safe = fallback.getSpawnLocation();
                        if (plugin.getConfigManager() != null && plugin.getConfigManager().getSpawnConfig() != null
                            && plugin.getConfigManager().getSpawnConfig().isSpawnSet()) {
                            var cfg = plugin.getConfigManager().getSpawnConfig();
                            if (fallback.getName().equals(cfg.getWorld())) {
                                safe = new Location(fallback, cfg.getX(), cfg.getY(), cfg.getZ(), cfg.getYaw(), cfg.getPitch());
                            }
                        }
                        for (Player online : new ArrayList<>(world.getPlayers())) {
                            ignoreWorldQuit.add(online.getUniqueId());
                            online.teleport(safe);
                            online.sendMessage(ColorUtils.colorizeComponent(
                                "<gradient:#FFD900:#00FFC1>Restart</gradient> &8— &fTwoja sesja dungeona zostala zapisana. Wrocisz po starcie!</shadow>"
                            ));
                        }
                    }
                    worldService.unloadKeep(world);
                }
            } catch (Throwable throwable) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[Dungeon] Blad soft-shutdown sesji", throwable);
            }
        }
        sessionsByPlayer.clear();
        sessionsByWorld.clear();
        rejoinWindows.clear();
    }

    /** Po starcie pluginu: wczytaj zapisane sesje i pozwol graczom grac dalej. */
    public void restorePersistedSessions() {
        File dir = activeSessionsDir();
        File[] files = dir.listFiles((d, name) -> name != null && name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }
        int restored = 0;
        for (File file : files) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
                String worldName = yaml.getString("world");
                if (worldName == null || worldName.isBlank() || !DungeonWorldService.isDungeonWorld(worldName)) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    continue;
                }
                if (sessionsByWorld.containsKey(worldName)) {
                    continue;
                }
                World world = worldService.loadExistingSync(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[Dungeon] Brak swiata dla snapshotu " + worldName + " — usuwam zapis.");
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    continue;
                }
                List<String> playerIds = yaml.getStringList("players");
                List<String> playerNames = yaml.getStringList("player-names");
                if (playerIds.isEmpty()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    worldService.unloadAndDelete(world);
                    continue;
                }
                UUID leaderId;
                try {
                    String leaderRaw = yaml.getString("leader-id", playerIds.get(0));
                    leaderId = UUID.fromString(leaderRaw);
                } catch (IllegalArgumentException ex) {
                    leaderId = UUID.fromString(playerIds.get(0));
                }
                String leaderName = yaml.getString("leader-name", "player");
                int ticketCost = Math.max(0, yaml.getInt("ticket-cost", 0));
                String code = yaml.getString("party-code", "RST");
                DungeonParty party = new DungeonParty(code, leaderId, leaderName, Math.max(playerIds.size(), 5), false, ticketCost);
                for (int i = 0; i < playerIds.size(); i++) {
                    try {
                        UUID uuid = UUID.fromString(playerIds.get(i));
                        String name = i < playerNames.size() ? playerNames.get(i) : "player";
                        if (!party.isMember(uuid)) {
                            party.addMember(uuid, name);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                // Gdy addMember nie wpuscil (limit) — dopisz recznie.
                for (int i = 0; i < playerIds.size(); i++) {
                    try {
                        UUID uuid = UUID.fromString(playerIds.get(i));
                        if (party.isMember(uuid)) {
                            continue;
                        }
                        String name = i < playerNames.size() ? playerNames.get(i) : "player";
                        party.getMembers().put(uuid, new DungeonParty.Member(uuid, name, false));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                party.setStarted(true);
                DungeonSession session = new DungeonSession(plugin, this, party, world, locations, settings);
                session.applyRestartSnapshot(yaml);
                sessionsByWorld.put(worldName, session);
                for (UUID uuid : session.getPlayers()) {
                    sessionsByPlayer.put(uuid, session);
                    rejoinWindows.put(uuid, session);
                }
                session.startAfterServerRestart();
                restored++;
            } catch (Throwable throwable) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "[Dungeon] Blad przywracania " + file.getName(), throwable);
            }
        }
        if (restored > 0) {
            plugin.getLogger().info("[Dungeon] Przywrocono " + restored + " sesji po restarcie serwera.");
        }
    }

    public void teleportToServerSpawn(Player player) {
        ignoreWorldQuit.add(player.getUniqueId());
        try {
            forceSurvivalNoFlight(player);
            if (plugin.getConfigManager() != null && plugin.getConfigManager().getSpawnConfig() != null
                && plugin.getConfigManager().getSpawnConfig().isSpawnSet()) {
                var cfg = plugin.getConfigManager().getSpawnConfig();
                World world = Bukkit.getWorld(cfg.getWorld());
                if (world != null) {
                    player.teleport(new Location(world, cfg.getX(), cfg.getY(), cfg.getZ(), cfg.getYaw(), cfg.getPitch()));
                    forceSurvivalNoFlight(player);
                    return;
                }
            }
            if (!Bukkit.getWorlds().isEmpty()) {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            forceSurvivalNoFlight(player);
        } finally {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ignoreWorldQuit.remove(player.getUniqueId());
                forceSurvivalNoFlight(player);
            });
            Bukkit.getScheduler().runTaskLater(plugin, () -> forceSurvivalNoFlight(player), 5L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> forceSurvivalNoFlight(player), 20L);
        }
    }

    /** 100% reset GM3/fly po dungeonie. */
    public void forceSurvivalNoFlight(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            player.setSpectatorTarget(null);
        } catch (Throwable ignored) {
        }
        if (player.getGameMode() != GameMode.SURVIVAL
            && player.getGameMode() != GameMode.CREATIVE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFlySpeed(0.1F);
        player.setWalkSpeed(0.2F);
        player.setGravity(true);
    }

    public boolean requireTemplateWorld(Player player) {
        if (player.getWorld() == null || !DungeonWorldService.TEMPLATE.equalsIgnoreCase(player.getWorld().getName())) {
            player.sendMessage(ColorUtils.colorizeComponent(settings.getString("messages.must-be-in-template",
                "&#FF0000✘ &8Ξ &cMusisz stac w swiecie &eninjaevent&c!")));
            return false;
        }
        return true;
    }
}
