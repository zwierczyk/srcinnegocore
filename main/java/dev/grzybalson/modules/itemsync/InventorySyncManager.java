package dev.grzybalson.modules.itemsync;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.config.configs.SpawnConfig;
import dev.grzybalson.managers.MultiActionBarService;
import dev.grzybalson.modules.itemsync.event.PlayerInventoryLoadedEvent;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import dev.grzybalson.utils.PlayerHealthBase;
import dev.grzybalson.utils.PlayerInventories;
import dev.grzybalson.utils.TimeUtil;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class InventorySyncManager {

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final File configFile;
    private YamlConfiguration config;
    private BukkitTask autoSaveTask;
    private final Cache<UUID, PlayerInventorySnapshot> pendingJoinCache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build();
    private final java.util.Set<UUID> sectorRestoredPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Cache<UUID, Boolean> warnCooldown = Caffeine.newBuilder()
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build();
    /** Fingerprint EQ — autosave pomija gdy nic się nie zmieniło. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, Integer> lastSaveFingerprint =
        new java.util.concurrent.ConcurrentHashMap<>();
    /** Po deathkeep/strip — nie zapisuj EQ zanim restore/stabilizacja skończy się. */
    private final java.util.concurrent.ConcurrentHashMap<UUID, Long> suppressEmptySaveUntil =
        new java.util.concurrent.ConcurrentHashMap<>();

    public InventorySyncManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, "itemsync"), "itemsync.yml");
        reload();
        this.collection = plugin.getMongoManager().getCollection(getCollectionName());
    }

    public void start() {
        stopAutoSave();
        if (!isEnabled()) {
            return;
        }
        int seconds = Math.max(30, config.getInt("auto-save-seconds", 60));
        // Capture na main thread, zapis Mongo async — bez async Bukkit API.
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::saveAllOnlineStaggered,
            seconds * 20L,
            seconds * 20L
        );
    }

    public void shutdown() {
        stopAutoSave();
        if (isEnabled() && config.getBoolean("save-on-disable", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                savePlayer(player, false);
            }
        }
        pendingJoinCache.invalidateAll();
        sectorRestoredPlayers.clear();
        suppressEmptySaveUntil.clear();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, "addons/itemsync/itemsync.yml");
        if (!configFile.exists()) {
            File parent = configFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        boolean changed = false;
        changed |= setDefault("enabled", true);
        changed |= setDefault("collection", "playerdata");
        changed |= setDefault("auto-save-seconds", 60);
        changed |= setDefault("auto-save-players-per-tick", 4);
        changed |= setDefault("rejoin-cooldown-ms", 5000L);
        changed |= setDefault("load-on-join", true);
        changed |= setDefault("save-on-quit", true);
        changed |= setDefault("save-on-disable", true);
        changed |= setDefault("teleport-on-load", true);
        changed |= setDefault("teleport-to-spawn-on-death", true);
        changed |= setDefault("sync-location", true);
        changed |= setDefault("sync-stats", true);
        changed |= setDefault("sync-potion-effects", true);
        changed |= setDefault("sync-ender-chest", true);
        changed |= setDefault("sync-offhand", true);
        changed |= setDefault("clear-death-flag-after-load", true);
        changed |= setDefault("protect-non-empty-inventory-on-empty-load", true);
        changed |= setDefault("protect-non-empty-ender-chest-on-empty-load", true);
        changed |= setDefault("protect-empty-save", true);
        changed |= setDefault("messages.reload", "&#00FF3B✔ &8Ξ &#4EFA76Przeladowano itemsync.yml.");
        changed |= setDefault("messages.save-all", "&#00FF3B✔ &8Ξ &#4EFA76Zapisano dane &#4EFA76{count} &fgraczy.");
        changed |= setDefault("messages.rejoin-cooldown", "&#FF5C5C✘ &8• &fPoczekaj chwile przed ponownym dolaczeniem.");
        changed |= setDefault("dev-actionbar.enabled", true);
        changed |= setDefault("dev-actionbar.permission", "Holly.itemsync.loadspeed");
        changed |= setDefault("dev-actionbar.channel", "ITEMSYNC_TIMING");
        changed |= setDefault("dev-actionbar.duration-ticks", 60L);
        changed |= setDefault("dev-actionbar.load-message", "&8• &aWczytano twoje dane w &2{time}");
        changed |= setDefault("dev-actionbar.save-message", "&8• &aZapisano twoje dane w &2{time}");
        changed |= migrateLegacyPxMessage("dev-actionbar.load-message", "&8• &aWczytano twoje dane w &2{time}");
        changed |= migrateLegacyPxMessage("dev-actionbar.save-message", "&8• &aZapisano twoje dane w &2{time}");
        if (changed) {
            saveConfig();
        }
        start();
    }

    public boolean isEnabled() {
        return config != null && config.getBoolean("enabled", true);
    }

    public String getCollectionName() {
        return config != null ? config.getString("collection", "playerdata") : "playerdata";
    }

    /** Wszystkie UUID z ItemSync — do pełnego snapshotu /cofka (nie tylko konta ekonomii). */
    public CompletableFuture<java.util.Set<UUID>> listAllPlayerUuids() {
        if (!isEnabled() || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(java.util.Set.of());
        }
        return plugin.getMongoManager().toListFuture(
            collection.find().projection(com.mongodb.client.model.Projections.include("_id"))
        ).thenApply(docs -> {
            java.util.Set<UUID> uuids = new java.util.HashSet<>();
            if (docs == null) {
                return uuids;
            }
            for (Document doc : docs) {
                Object id = doc.get("_id");
                if (id == null) {
                    continue;
                }
                try {
                    uuids.add(UUID.fromString(String.valueOf(id)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return uuids;
        }).exceptionally(ex -> {
            plugin.getLogger().warning("[ItemSync] listAllPlayerUuids: " + ex.getMessage());
            return java.util.Set.of();
        });
    }

    public long getRejoinCooldownMillis() {
        return Math.max(0L, config.getLong("rejoin-cooldown-ms", 5000L));
    }

    public String getRejoinCooldownMessage() {
        return config.getString("messages.rejoin-cooldown", "&#FF5C5C✘ &8• &fPoczekaj chwile przed ponownym dolaczeniem.");
    }

    public boolean configLoadOnJoin() {
        return config.getBoolean("load-on-join", true);
    }

    public boolean configSaveOnQuit() {
        return config.getBoolean("save-on-quit", true);
    }

    public String getSaveAllMessage(int count) {
        return config.getString("messages.save-all", "&#00FF3B✔ &8Ξ &#4EFA76Zapisano dane &#4EFA76{count} &fgraczy.")
            .replace("{count}", String.valueOf(count));
    }

    public String getReloadMessage() {
        return config.getString("messages.reload", "&#00FF3B✔ &8Ξ &#4EFA76Przeladowano itemsync.yml.");
    }

    public void cacheForJoin(UUID uuid, PlayerInventorySnapshot snapshot) {
        if (uuid != null && snapshot != null) {
            pendingJoinCache.put(uuid, snapshot);
        }
    }

    public PlayerInventorySnapshot pollCachedJoin(UUID uuid) {
        PlayerInventorySnapshot snapshot = pendingJoinCache.getIfPresent(uuid);
        if (snapshot != null) {
            pendingJoinCache.invalidate(uuid);
        }
        return snapshot;
    }

    public void invalidateCachedJoin(UUID uuid) {
        pendingJoinCache.invalidate(uuid);
    }

    public void markSectorRestored(UUID uuid) {
        if (uuid != null) {
            sectorRestoredPlayers.add(uuid);
        }
    }

    public boolean consumeSectorRestored(UUID uuid) {
        return uuid != null && sectorRestoredPlayers.remove(uuid);
    }

    /** Flaga zyje tylko w obrebie jednej sesji — po quicie moglaby zmylic kolejny join. */
    public void clearSectorRestored(UUID uuid) {
        if (uuid != null) {
            sectorRestoredPlayers.remove(uuid);
        }
    }

    public boolean shouldSkipSave(UUID uuid) {
        if (uuid == null) {
            return true;
        }
        if (plugin.getSectorService() != null && plugin.getSectorService().isTransferring(uuid)) {
            return true;
        }
        // Kit dungeona / strip nie może nadpisać prawdziwego EQ w Mongo.
        if (plugin.getEventManager() != null
            && plugin.getEventManager().getDungeonSessions() != null
            && plugin.getEventManager().getDungeonSessions().isBoundToDungeon(uuid)) {
            return true;
        }
        Long suppressUntil = suppressEmptySaveUntil.get(uuid);
        return suppressUntil != null && suppressUntil > System.currentTimeMillis();
    }

    /**
     * Po tymczasowym clear (deathkeep / strip) — blokuj tylko autosave pustego EQ przez chwilę.
     * Quit / forceSave zawsze nadpisują Mongo (anty-dupe: kill → DC → rejoin).
     */
    public void suppressSaveBriefly(UUID uuid, long millis) {
        if (uuid == null || millis <= 0L) {
            return;
        }
        long until = System.currentTimeMillis() + millis;
        suppressEmptySaveUntil.merge(uuid, until, Math::max);
    }

    /** Wymusza zapis mimo suppressEmptySaveUntil (quit / deathkeep). */
    public void forceSavePlayer(Player player) {
        savePlayer(player, false, true);
    }

    public CompletableFuture<PlayerInventorySnapshot> loadSnapshot(UUID uuid) {
        if (!plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("_id", uuid.toString())).first()
        ).thenApply(this::fromDocument);
    }

    public CompletableFuture<Void> saveSnapshot(PlayerInventorySnapshot snapshot) {
        if (snapshot == null || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        // Snapshot ma sklonowane itemy, wiec serializacja (NBT + GZIP) moze zejsc z glownego watku.
        // Przy wylaczaniu pluginu serializujemy synchronicznie, zeby zapis zdazyl przed zamknieciem.
        if (!plugin.isEnabled()) {
            return writeDocument(snapshot, toDocument(snapshot));
        }
        return CompletableFuture
            .supplyAsync(() -> toDocument(snapshot))
            .thenCompose(document -> writeDocument(snapshot, document));
    }

    private CompletableFuture<Void> writeDocument(PlayerInventorySnapshot snapshot, Document document) {
        return plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("_id", snapshot.uuid().toString()),
                document,
                new ReplaceOptions().upsert(true)
            )
        ).thenApply(result -> null);
    }

    /** Po cofce serwera — nadpisuje EQ/EC w Mongo, żeby load-on-join nie przywracał stan sprzed rollbacku. */
    public CompletableFuture<Void> saveRollbackInventory(
        UUID uuid,
        String playerName,
        ItemStack[] inventory,
        ItemStack[] armor,
        ItemStack[] enderChest
    ) {
        if (!isEnabled() || uuid == null || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(null);
        }
        return loadSnapshot(uuid).thenCompose(existing -> {
            PlayerInventorySnapshot snapshot;
            if (existing != null) {
                snapshot = new PlayerInventorySnapshot(
                    uuid,
                    playerName != null && !playerName.isBlank() ? playerName : existing.playerName(),
                    false,
                    false,
                    cloneItems(inventory),
                    cloneItems(armor),
                    PlayerInventories.extraOf(inventory, null),
                    cloneItems(enderChest),
                    existing.potionEffects(),
                    existing.location(),
                    existing.health(),
                    existing.maxHealth(),
                    existing.foodLevel(),
                    existing.level(),
                    existing.exp(),
                    existing.totalExp(),
                    existing.heldSlot(),
                    existing.walkSpeed(),
                    existing.flySpeed(),
                    existing.allowFlight(),
                    existing.flying(),
                    existing.sprinting(),
                    existing.fireTicks(),
                    existing.saturation(),
                    existing.exhaustion(),
                    existing.op(),
                    existing.gameMode()
                );
            } else {
                snapshot = new PlayerInventorySnapshot(
                    uuid,
                    playerName != null && !playerName.isBlank() ? playerName : uuid.toString(),
                    false,
                    false,
                    cloneItems(inventory),
                    cloneItems(armor),
                    PlayerInventories.extraOf(inventory, null),
                    cloneItems(enderChest),
                    List.of(),
                    null,
                    20.0D,
                    20.0D,
                    20,
                    0,
                    0.0F,
                    0,
                    0,
                    0.2F,
                    0.1F,
                    false,
                    false,
                    false,
                    0,
                    5.0F,
                    0.0F,
                    false,
                    org.bukkit.GameMode.SURVIVAL
                );
            }
            return saveSnapshot(snapshot);
        });
    }

    public PlayerInventorySnapshot capture(Player player, boolean redirecting) {
        boolean death = player.isDead() || player.getHealth() <= 0.0D;
        PlayerInventorySnapshot.StoredLocation storedLocation = captureLocation(player, death);

        ItemStack[] enderChest = cloneItems(player.getEnderChest().getContents());
        var mslf = plugin.getMslfManager();
        if (mslf != null && mslf.enderChestService() != null) {
            enderChest = cloneItems(mslf.enderChestService().captureContents(player));
        }

        return new PlayerInventorySnapshot(
            player.getUniqueId(),
            player.getName(),
            death,
            redirecting,
            cloneItems(player.getInventory().getStorageContents()),
            cloneItems(player.getInventory().getArmorContents()),
            cloneItems(player.getInventory().getExtraContents()),
            enderChest,
            capturePotionEffects(player),
            storedLocation,
            death ? 20.0D : player.getHealth(),
            captureBaseMaxHealth(player),
            player.getFoodLevel(),
            player.getLevel(),
            player.getExp(),
            player.getTotalExperience(),
            player.getInventory().getHeldItemSlot(),
            player.getWalkSpeed(),
            player.getFlySpeed(),
            player.getAllowFlight(),
            player.isFlying(),
            player.isSprinting(),
            player.getFireTicks(),
            player.getSaturation(),
            player.getExhaustion(),
            false,
            player.getGameMode()
        );
    }

    public void apply(Player player, PlayerInventorySnapshot snapshot) {
        apply(player, snapshot, 0L);
    }

    public void apply(Player player, PlayerInventorySnapshot snapshot, long loadStartedAt) {
        if (player == null || !player.isOnline() || snapshot == null) {
            return;
        }

        if (config.getBoolean("sync-stats", true)) {
            applyStats(player, snapshot);
        }

        if (config.getBoolean("teleport-on-load", true)) {
            applyLocation(player, snapshot);
        }

        boolean snapshotInvEmpty = !hasAnyItems(snapshot.inventory()) && !hasAnyItems(snapshot.armor()) && !hasAnyItems(snapshot.extra());
        boolean snapshotEcEmpty = !hasAnyItems(snapshot.enderChest());
        boolean playerInvHasItems = hasAnyItems(player.getInventory().getContents())
            || hasAnyItems(player.getInventory().getArmorContents())
            || hasAnyItems(player.getInventory().getExtraContents());
        boolean playerEcHasItems = hasAnyItems(player.getEnderChest().getContents());

        boolean protectInv = config.getBoolean("protect-non-empty-inventory-on-empty-load", true)
            && snapshotInvEmpty && playerInvHasItems && !snapshot.death();
        boolean protectEc = config.getBoolean("protect-non-empty-ender-chest-on-empty-load", true)
            && snapshotEcEmpty && playerEcHasItems;

        if (protectInv || protectEc) {
            warnRateLimited(player.getUniqueId(), "[ItemSync] Zablokowano potencjalnie niebezpieczny empty-load dla "
                + player.getName() + " (" + player.getUniqueId() + ") invProtect=" + protectInv + " ecProtect=" + protectEc);
        }

        if (!protectInv) {
            if (config.getBoolean("sync-offhand", true)) {
                PlayerInventories.apply(
                    player,
                    safeItems(snapshot.inventory()),
                    safeItems(snapshot.armor()),
                    safeItems(snapshot.extra())
                );
            } else {
                PlayerInventories.applyStorageAndArmor(
                    player,
                    safeItems(snapshot.inventory()),
                    safeItems(snapshot.armor())
                );
            }
        }
        if (!protectEc && config.getBoolean("sync-ender-chest", true)) {
            ItemStack[] ender = safeItems(snapshot.enderChest());
            var mslf = plugin.getMslfManager();
            if (mslf != null && mslf.enderChestService() != null && ender != null && ender.length > 27) {
                mslf.enderChestService().restoreFromSnapshot(player, ender);
            } else {
                player.getEnderChest().setContents(ender);
            }
        }

        if (config.getBoolean("sync-potion-effects", true)) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            for (PotionEffect effect : snapshot.potionEffects()) {
                if (effect != null) {
                    player.addPotionEffect(effect);
                }
            }
        }

        player.updateInventory();
        Bukkit.getPluginManager().callEvent(new PlayerInventoryLoadedEvent(player, snapshot));

        if (plugin.getBackupManager() != null) {
            plugin.getBackupManager().updatePlayerSession(player);
        }

        if (config.getBoolean("clear-death-flag-after-load", true) && snapshot.death()) {
            saveSnapshot(snapshot.withDeath(false));
        }

        if (loadStartedAt > 0L) {
            notifyDevTiming(player, "load", System.currentTimeMillis() - loadStartedAt);
        }
    }

    public void clearSaveFingerprint(UUID uuid) {
        if (uuid != null) {
            lastSaveFingerprint.remove(uuid);
            suppressEmptySaveUntil.remove(uuid);
        }
    }

    public void savePlayer(Player player, boolean redirecting) {
        savePlayer(player, redirecting, false);
    }

    public void savePlayer(Player player, boolean redirecting, boolean force) {
        if (!isEnabled() || player == null) {
            return;
        }
        if (!force && !redirecting && shouldSkipSave(player.getUniqueId())) {
            return;
        }
        // Trade: oferty nie są w EQ — anuluj i zwróć przed snapshotem (anty-loss / anty-desync).
        if (plugin.getTradeManager() != null
            && plugin.getTradeManager().isTrading(player.getUniqueId())) {
            plugin.getTradeManager().cancelTradeFor(player.getUniqueId(), "sync");
        }
        // Portable shulker: NBT itemu jest „pełne” dopóki GUI nie zapisze.
        // Snapshot w trakcie = pełny shulker + itemy już w EQ → dupe.
        if (plugin.getShulkerBoxListener() != null
            && plugin.getShulkerBoxListener().isViewing(player.getUniqueId())) {
            plugin.getShulkerBoxListener().flush(player);
        }
        long saveStartedAt = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        PlayerInventorySnapshot snapshot = capture(player, redirecting);
        // Fingerprint pomija tylko autosave — force (quit / deathkeep) musi zapisac zawsze.
        boolean trackFingerprint = !force && !redirecting && !snapshot.death();
        int computedFingerprint = 0;
        if (trackFingerprint) {
            computedFingerprint = inventoryFingerprint(snapshot);
            Integer previous = lastSaveFingerprint.get(uuid);
            if (previous != null && previous == computedFingerprint) {
                return;
            }
        } else {
            lastSaveFingerprint.remove(uuid);
        }
        boolean emptySnapshot = !hasAnyItems(snapshot.inventory())
            && !hasAnyItems(snapshot.armor())
            && !hasAnyItems(snapshot.extra())
            && !hasAnyItems(snapshot.enderChest());
        // Pusty zapis blokujemy tylko zaraz po wejsciu / transferze (race),
        // NIE przy normalnym autosave pustego EQ po smierci — to spamowalo logi i moglo wiesiac I/O.
        if (config.getBoolean("protect-empty-save", true)
            && !redirecting
            && !snapshot.death()
            && emptySnapshot
            && player.getTicksLived() < 20 * 15) {
            warnRateLimited(player.getUniqueId(), "[ItemSync] Pominieto wczesny pusty zapis dla "
                + player.getName() + " (" + player.getUniqueId() + ")");
            return;
        }
        final boolean markFingerprint = trackFingerprint;
        final int savedFingerprint = computedFingerprint;
        saveSnapshot(snapshot).thenAccept(ignored -> {
            // Fingerprint dopiero po realnym zapisie — pominiety write (Mongo offline) nie moze udawac zapisanego.
            if (markFingerprint && plugin.getMongoManager().isAvailable()) {
                lastSaveFingerprint.put(uuid, savedFingerprint);
            }
            long elapsed = System.currentTimeMillis() - saveStartedAt;
            // Przy onDisable scheduler rzuca IllegalPluginAccessException i udawaloby to blad zapisu.
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> notifyDevTiming(player, "save", elapsed));
            }
        }).exceptionally(error -> {
            lastSaveFingerprint.remove(uuid);
            plugin.getLogger().warning("Nie udalo sie zapisac danych gracza " + uuid + ": " + error.getMessage());
            return null;
        });
    }

    private int inventoryFingerprint(PlayerInventorySnapshot snapshot) {
        int hash = 1;
        hash = 31 * hash + itemsFingerprint(snapshot.inventory());
        hash = 31 * hash + itemsFingerprint(snapshot.armor());
        hash = 31 * hash + itemsFingerprint(snapshot.extra());
        hash = 31 * hash + itemsFingerprint(snapshot.enderChest());
        hash = 31 * hash + snapshot.level();
        hash = 31 * hash + Float.hashCode(snapshot.exp());
        hash = 31 * hash + snapshot.foodLevel();
        hash = 31 * hash + Double.hashCode(snapshot.health());
        hash = 31 * hash + (snapshot.gameMode() != null ? snapshot.gameMode().ordinal() : 0);
        return hash;
    }

    private int itemsFingerprint(ItemStack[] items) {
        if (items == null) {
            return 0;
        }
        int hash = 1;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                hash = 31 * hash;
                continue;
            }
            hash = 31 * hash + item.getType().ordinal();
            hash = 31 * hash + item.getAmount();
            hash = 31 * hash + (item.hasItemMeta() ? item.getItemMeta().hashCode() : 0);
        }
        return hash;
    }

    public int saveAllOnline() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayer(player, false);
            count++;
        }
        return count;
    }

    private void saveAllOnlineAsync() {
        saveAllOnlineStaggered();
    }

    private void saveAllOnlineStaggered() {
        if (!isEnabled()) {
            return;
        }
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            return;
        }
        int configured = Math.max(1, config.getInt("auto-save-players-per-tick", 4));
        int perTick = configured;
        if (players.size() > 80) {
            perTick = Math.max(2, Math.min(configured, players.size() / 50));
        }
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            long delay = i / perTick;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    savePlayer(player, false);
                }
            }, delay);
        }
    }

    private void applyStats(Player player, PlayerInventorySnapshot snapshot) {
        if (snapshot.gameMode() != null) {
            player.setGameMode(snapshot.gameMode());
        }
        // Freeze dungeon/event zapisuje walk/fly 0 — nie przywracaj tego po syncu (blokuje WASD przy fly).
        float walk = snapshot.walkSpeed();
        float fly = snapshot.flySpeed();
        player.setWalkSpeed(walk <= 0.001F ? 0.2F : Math.min(1.0F, walk));
        player.setFlySpeed(fly <= 0.001F ? 0.1F : Math.min(1.0F, fly));
        applyMaxHealth(player, snapshot.maxHealth());
        double max = player.getMaxHealth();
        double health = snapshot.health();
        if (!Double.isFinite(health) || health <= 0.0D) {
            health = Math.min(20.0D, max);
        }
        player.setHealth(Math.max(0.1D, Math.min(health, max)));
        player.setFoodLevel(snapshot.foodLevel());
        player.setLevel(snapshot.level());
        player.setExp(snapshot.exp());
        player.setTotalExperience(snapshot.totalExp());
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying());
        player.setSprinting(snapshot.sprinting());
        player.setFireTicks(snapshot.fireTicks());
            player.setSaturation(snapshot.saturation());
            player.setExhaustion(snapshot.exhaustion());
            player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, snapshot.heldSlot())));
    }

    /**
     * Zapisujemy tylko bazę GENERIC_MAX_HEALTH — bez modifiera kostiumu ExtraHearts.
     * Stary zapis getMaxHealth() pieczerował bonus w bazę i mnożył serca przez sektory.
     */
    private double captureBaseMaxHealth(Player player) {
        return PlayerHealthBase.resolveBaseMaxHealth(plugin, player);
    }

    private void applyMaxHealth(Player player, double snapshotMaxHealth) {
        // Zawsze świeża baza (10 / 30+anarchiczne) — snapshot i kostium nie pieczerują HP.
        PlayerHealthBase.resetBaseMaxHealth(plugin, player, true);
    }

    private void applyLocation(Player player, PlayerInventorySnapshot snapshot) {
        Location target = null;
        SpawnConfig spawnConfig = plugin.getConfigManager().getSpawnConfig();
        if (snapshot.death() && config.getBoolean("teleport-to-spawn-on-death", true)) {
            target = resolveSpawnLocation();
        } else if (config.getBoolean("sync-location", true) && snapshot.location() != null) {
            String worldName = snapshot.location().world();
            // Sektor: wyjscie z pvp/event -> wejscie na /spawn; inne mapy = restore loc.
            if (spawnConfig != null && spawnConfig.isSpawnOnJoin() && spawnConfig.isForceSpawnWorld(worldName)) {
                target = resolveSpawnLocation();
            } else {
                target = snapshot.location().toLocation();
            }
        }
        if (target != null && target.getWorld() != null) {
            player.teleportAsync(target);
        }
    }

    private PlayerInventorySnapshot.StoredLocation captureLocation(Player player, boolean death) {
        if (death && config.getBoolean("teleport-to-spawn-on-death", true)) {
            Location spawn = resolveSpawnLocation();
            if (spawn != null && spawn.getWorld() != null) {
                return new PlayerInventorySnapshot.StoredLocation(
                    spawn.getWorld().getName(),
                    spawn.getX(),
                    spawn.getY(),
                    spawn.getZ(),
                    spawn.getYaw(),
                    spawn.getPitch()
                );
            }
        }

        Location location = player.getLocation();
        World world = location.getWorld();
        return new PlayerInventorySnapshot.StoredLocation(
            world != null ? world.getName() : "world",
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    private Location resolveSpawnLocation() {
        SpawnConfig spawnConfig = plugin.getConfigManager().getSpawnConfig();
        if (spawnConfig == null || !spawnConfig.isSpawnSet()) {
            World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            return defaultWorld != null ? defaultWorld.getSpawnLocation() : null;
        }
        World world = Bukkit.getWorld(spawnConfig.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(
            world,
            spawnConfig.getX(),
            spawnConfig.getY(),
            spawnConfig.getZ(),
            spawnConfig.getYaw(),
            spawnConfig.getPitch()
        );
    }

    private List<PotionEffect> capturePotionEffects(Player player) {
        return new ArrayList<>(player.getActivePotionEffects());
    }

    private ItemStack[] safeItems(ItemStack[] items) {
        return items == null ? new ItemStack[0] : items;
    }

    private void warnRateLimited(UUID uuid, String message) {
        if (uuid == null || warnCooldown.getIfPresent(uuid) != null) {
            return;
        }
        warnCooldown.put(uuid, Boolean.TRUE);
        plugin.getLogger().warning(message);
    }

    private boolean hasAnyItems(ItemStack[] items) {
        if (items == null) {
            return false;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        if (items == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }

    private Document toDocument(PlayerInventorySnapshot snapshot) {
        Document sectorPlayer = new Document()
            .append("inventory", ItemSerializer.serializeItems(snapshot.inventory()))
            .append("armor", ItemSerializer.serializeItems(snapshot.armor()))
            .append("extra", ItemSerializer.serializeItems(snapshot.extra()))
            .append("enderChest", ItemSerializer.serializeItems(snapshot.enderChest()))
            .append("health", snapshot.health())
            .append("maxHealth", snapshot.maxHealth())
            .append("foodLevel", snapshot.foodLevel())
            .append("level", snapshot.level())
            .append("exp", snapshot.exp())
            .append("totalExp", snapshot.totalExp())
            .append("heldSlot", snapshot.heldSlot())
            .append("walkSpeed", snapshot.walkSpeed())
            .append("flySpeed", snapshot.flySpeed())
            .append("allowFlight", snapshot.allowFlight())
            .append("flying", snapshot.flying())
            .append("sprinting", snapshot.sprinting())
            .append("fireTicks", snapshot.fireTicks())
            .append("saturation", snapshot.saturation())
            .append("exhaustion", snapshot.exhaustion())
            // Op zostaje na serwerze (ops.json) — wspólna baza nie przenosi go na inne instancje.
            .append("op", false)
            .append("gameMode", snapshot.gameMode() != null ? snapshot.gameMode().name() : GameMode.SURVIVAL.name())
            .append("potionEffects", serializePotionEffects(snapshot.potionEffects()));

        if (snapshot.location() != null) {
            sectorPlayer.append("location", new Document()
                .append("worldName", snapshot.location().world())
                .append("x", snapshot.location().x())
                .append("y", snapshot.location().y())
                .append("z", snapshot.location().z())
                .append("yaw", snapshot.location().yaw())
                .append("pitch", snapshot.location().pitch()));
        }

        return new Document("_id", snapshot.uuid().toString())
            .append("uuid", snapshot.uuid().toString())
            .append("playerName", snapshot.playerName())
            .append("playerNameLower", snapshot.playerName() != null ? snapshot.playerName().toLowerCase(Locale.ROOT) : null)
            .append("death", snapshot.death())
            .append("redirecting", snapshot.redirecting())
            .append("updatedAt", new Date())
            .append("sectorPlayer", sectorPlayer)
            .append("itemStacks", sectorPlayer.getString("inventory"))
            .append("armor", sectorPlayer.getString("armor"))
            .append("enderchest", sectorPlayer.getString("enderChest"));
    }

    private PlayerInventorySnapshot fromDocument(Document document) {
        if (document == null) {
            return null;
        }

        UUID uuid;
        try {
            String rawUuid = document.getString("_id");
            if (rawUuid == null) {
                rawUuid = document.getString("uuid");
            }
            if (rawUuid == null) {
                return null;
            }
            uuid = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Document sectorPlayer = document.get("sectorPlayer", Document.class);
        if (sectorPlayer == null) {
            sectorPlayer = document;
        }

        PlayerInventorySnapshot.StoredLocation location = readLocation(sectorPlayer.get("location", Document.class));

        GameMode gameMode = GameMode.SURVIVAL;
        String gameModeRaw = sectorPlayer.getString("gameMode");
        if (gameModeRaw != null) {
            try {
                gameMode = GameMode.valueOf(gameModeRaw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new PlayerInventorySnapshot(
            uuid,
            document.getString("playerName"),
            document.getBoolean("death", false),
            document.getBoolean("redirecting", false),
            deserializeFirst(sectorPlayer, "inventory", "itemStacks"),
            deserializeFirst(sectorPlayer, "armor"),
            deserializeFirst(sectorPlayer, "extra"),
            deserializeFirst(sectorPlayer, "enderChest", "enderchest"),
            deserializePotionEffects(sectorPlayer.getList("potionEffects", Document.class)),
            location,
            readDouble(sectorPlayer, "health", 20.0D),
            readDouble(sectorPlayer, "maxHealth", 20.0D),
            sectorPlayer.getInteger("foodLevel", 20),
            sectorPlayer.getInteger("level", 0),
            readFloat(sectorPlayer, "exp", 0.0F),
            sectorPlayer.getInteger("totalExp", 0),
            sectorPlayer.getInteger("heldSlot", 0),
            readFloat(sectorPlayer, "walkSpeed", 0.2F),
            readFloat(sectorPlayer, "flySpeed", 0.1F),
            sectorPlayer.getBoolean("allowFlight", false),
            sectorPlayer.getBoolean("flying", false),
            sectorPlayer.getBoolean("sprinting", false),
            sectorPlayer.getInteger("fireTicks", 0),
            readFloat(sectorPlayer, "saturation", 5.0F),
            readFloat(sectorPlayer, "exhaustion", 0.0F),
            false,
            gameMode
        );
    }

    private PlayerInventorySnapshot.StoredLocation readLocation(Document locationDoc) {
        if (locationDoc == null) {
            return null;
        }
        return new PlayerInventorySnapshot.StoredLocation(
            locationDoc.getString("worldName"),
            readDouble(locationDoc, "x", 0.0D),
            readDouble(locationDoc, "y", 64.0D),
            readDouble(locationDoc, "z", 0.0D),
            readFloat(locationDoc, "yaw", 0.0F),
            readFloat(locationDoc, "pitch", 0.0F)
        );
    }

    private ItemStack[] deserializeFirst(Document document, String... fields) {
        for (String field : fields) {
            String serialized = document.getString(field);
            if (serialized == null || serialized.isBlank()) {
                continue;
            }
            ItemStack[] items = ItemSerializer.deserializeItems(serialized);
            if (items != null) {
                return items;
            }
        }
        return new ItemStack[0];
    }

    private List<Document> serializePotionEffects(List<PotionEffect> effects) {
        List<Document> documents = new ArrayList<>();
        if (effects == null) {
            return documents;
        }
        for (PotionEffect effect : effects) {
            if (effect == null || effect.getType() == null) {
                continue;
            }
            documents.add(new Document()
                .append("type", effect.getType().getName())
                .append("duration", effect.getDuration())
                .append("amplifier", effect.getAmplifier())
                .append("ambient", effect.isAmbient())
                .append("particles", effect.hasParticles())
                .append("icon", effect.hasIcon()));
        }
        return documents;
    }

    private List<PotionEffect> deserializePotionEffects(List<Document> documents) {
        List<PotionEffect> effects = new ArrayList<>();
        if (documents == null) {
            return effects;
        }
        for (Document document : documents) {
            if (document == null) {
                continue;
            }
            String typeName = document.getString("type");
            if (typeName == null || typeName.isBlank()) {
                continue;
            }
            PotionEffectType type = PotionEffectType.getByName(typeName);
            if (type == null) {
                continue;
            }
            effects.add(new PotionEffect(
                type,
                document.getInteger("duration", 0),
                document.getInteger("amplifier", 0),
                document.getBoolean("ambient", false),
                document.getBoolean("particles", true),
                document.getBoolean("icon", true)
            ));
        }
        return effects;
    }

    private double readDouble(Document document, String key, double fallback) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    private float readFloat(Document document, String key, float fallback) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return fallback;
    }

    private void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
    }

    private void notifyDevTiming(Player player, String type, long elapsedMs) {
        if (player == null || !player.isOnline() || config == null) {
            return;
        }
        if (!config.getBoolean("dev-actionbar.enabled", true)) {
            return;
        }
        String permission = config.getString("dev-actionbar.permission", "Holly.itemsync.loadspeed");
        if (permission == null || permission.isBlank() || !player.hasPermission(permission)) {
            return;
        }

        String messageKey = "load".equals(type) ? "dev-actionbar.load-message" : "dev-actionbar.save-message";
        String fallback = "&8• &a" + ("load".equals(type) ? "Wczytano" : "Zapisano") + " twoje dane w &2{time}";
        String template = config.getString(messageKey, fallback);
        String message = template.replace("{time}", TimeUtil.convert(Math.max(0L, elapsedMs)));
        String channel = config.getString("dev-actionbar.channel", "ITEMSYNC_TIMING");
        long durationTicks = Math.max(20L, config.getLong("dev-actionbar.duration-ticks", 60L));

        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.send(player, channel, message, durationTicks);
        } else {
            player.sendActionBar(ColorUtils.colorizeComponent(message));
        }
    }

    private boolean setDefault(String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    private boolean migrateLegacyPxMessage(String path, String replacement) {
        String current = config.getString(path);
        if (current == null || !current.contains("[PX]")) {
            return false;
        }
        config.set(path, replacement);
        return true;
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac itemsync.yml: " + exception.getMessage());
        }
    }
}
