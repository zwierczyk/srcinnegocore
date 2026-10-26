package dev.grzybalson.commands.daily;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DailyRewardManager {

    public static final ZoneId WARSAW_ZONE = ZoneId.of("Europe/Warsaw");
    private static final long CLAIM_COOLDOWN_MS = TimeUnit.HOURS.toMillis(24);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('§').hexColors().build();
    private static final List<Integer> DEFAULT_SLOTS = Arrays.asList(10, 11, 12, 13, 14, 15, 16);
    private static final List<Integer> ORDERED_GUI_SLOTS = Arrays.asList(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43,
        46, 47, 48, 49, 50, 51, 52
    );

    private final HollyCore plugin;
    private final File configFile;
    private final File playersFile;

    private YamlConfiguration config;

    private MongoCollection<Document> collection;
    private final Map<UUID, DailyPlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<DailyPlayerData>> inflightLoads = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingSaves = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> claimLocks = new ConcurrentHashMap<>();

    public DailyRewardManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = DailyPaths.getFile(plugin, "daily.yml");
        this.playersFile = DailyPaths.getFile(plugin, "players.yml");
    }

    public void reload() {
        DailyPaths.ensureResource(plugin, "daily.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);

        try {
            if (plugin.getMongoManager() != null && plugin.getMongoManager().isAvailable()
                && plugin.getConfigManager() != null && plugin.getConfigManager().getDatabaseConfig() != null) {
                String collectionName = plugin.getConfigManager().getDatabaseConfig().getDbPrefix() + "_daily";
                this.collection = plugin.getMongoManager().getCollection(collectionName);
            } else {
                this.collection = null;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Nie udało się pobrać kolekcji MongoDB dla daily: " + e.getMessage());
            this.collection = null;
        }

        if (collection == null) {
            plugin.getLogger().warning("[Daily] MongoDB niedostępne — dane graczy nie będą zapisywane.");
        } else {
            migrateAllYamlPlayersIfPresent();
            plugin.getMongoManager().toFuture(
                collection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true))
            ).exceptionally(error -> {
                plugin.getLogger().warning("[Daily] Unikalny indeks uuid nie został utworzony: " + error.getMessage());
                return null;
            });
        }

        ensureDefaults();

        cache.clear();
        inflightLoads.clear();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            loadPlayer(onlinePlayer.getUniqueId(), true);
        }
    }

    private void ensureDefaults() {
        boolean changed = false;

        changed |= setIfMissing("gui.title", "&8Codzienna Nagroda");
        changed |= setIfMissing("gui.rows", 5);
        changed |= setIfMissing("gui.reward-material", "CHEST_MINECART");
        changed |= setIfMissing("gui.claimable-lore", "&a» Kliknij LPM lub PPM aby odebrać nagrodę!");
        changed |= setIfMissing("gui.locked-lore", "&c» Nie możesz jeszcze odebrać tej nagrody!");
        changed |= setIfMissing("gui.claimed-lore", "&7» Tę nagrodę odebrałeś już dzisiaj.");
        changed |= setIfMissing("gui.empty-reward-line", "&7Brak skonfigurowanych przedmiotów");
        changed |= setIfMissing("gui.footer-lore", Collections.emptyList());
        changed |= setIfMissing("messages.claimed", "&#00FF3B✔ &8Ξ &#4EFA76Odebrałeś codzienną nagrodę &f#{day}&a!");
        changed |= setIfMissing("messages.already-claimed", "&#FF0000✘ &8Ξ &cNie możesz jeszcze odebrać tej nagrody!");
        changed |= setIfMissing("messages.no-reward", "&#FF0000✘ &8Ξ &cTa nagroda nie ma jeszcze ustawionych przedmiotów.");
        changed |= setIfMissing("messages.editor-saved", "&#00FF3B✔ &8Ξ &#4EFA76Zapisano nagrodę dzienną &f#{day}&a.");
        changed |= setIfMissing("messages.reloaded", "&#00FF3B✔ &8Ξ &#4EFA76Przeładowano daily.");
        changed |= setIfMissing("messages.only-player", "&#FF0000✘ &8Ξ &cTa komenda jest tylko dla gracza.");
        changed |= setIfMissing("messages.invalid-day", "&#FF0000✘ &8Ξ &cPodaj poprawny numer dnia.");
        changed |= setIfMissing("messages.loading", "&#FFAA00✘ &8Ξ &eŁadowanie danych nagród… spróbuj za chwilę.");

        String currentClaimLore = config.getString("gui.claimable-lore", "");
        if (currentClaimLore.isBlank() || currentClaimLore.equals("&a» Kliknij PPM aby odebrać nagrodę!")) {
            config.set("gui.claimable-lore", "&a» Kliknij LPM lub PPM aby odebrać nagrodę!");
            changed = true;
        }

        List<String> footerLore = config.getStringList("gui.footer-lore");
        if (!footerLore.isEmpty()) {
            config.set("gui.footer-lore", Collections.emptyList());
            changed = true;
        }

        for (int i = 1; i <= 7; i++) {
            changed |= ensureDayExists(i, false);
        }

        if (changed) {
            saveConfig();
        }
    }

    private boolean ensureDayExists(int day, boolean saveImmediately) {
        if (day <= 0) {
            return false;
        }

        boolean changed = false;
        String base = "days." + day;
        changed |= setIfMissing(base + ".display-name", "&bCODZIENNA NAGRODA #" + day);
        changed |= setIfMissing(base + ".items", "");

        if (day <= DEFAULT_SLOTS.size()) {
            changed |= setIfMissing(base + ".slot", DEFAULT_SLOTS.get(day - 1));
        }

        if (changed && saveImmediately) {
            saveConfig();
        }
        return changed;
    }

    private boolean setIfMissing(String path, Object value) {
        if (config.isSet(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udało się zapisać addons/daily/daily.yml: " + exception.getMessage());
        }
    }

    public void savePlayers() {
        // Dane graczy wyłącznie w Mongo — players.yml nie jest już zapisywane.
    }

    public Inventory createMainGui(Player player) {
        int size = plugin.getGuiFactory().getInventorySize(config.getInt("gui.rows", 5));
        Inventory inventory = Bukkit.createInventory(new DailyMainHolder(), size, ColorUtils.colorizeComponent(config.getString("gui.title", "&8Codzienna Nagroda")));
        plugin.getGuiFactory().applyDefaultTemplate(inventory);

        DailyProgress progress = getProgress(player.getUniqueId());
        for (int day : getConfiguredDays()) {
            int slot = getDaySlot(day);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, createDisplayItem(day, progress));
            }
        }
        return inventory;
    }

    public Inventory createEditorGui(int day) {
        ensureDayExists(day, true);
        DailyEditHolder holder = new DailyEditHolder(day);
        Inventory inventory = Bukkit.createInventory(holder, plugin.getGuiFactory().getDefaultSize(), ColorUtils.colorizeComponent("&8Edycja daily #" + day));
        ItemStack[] items = getRewardItems(day);
        for (int i = 0; i < Math.min(items.length, inventory.getSize()); i++) {
            ItemStack item = items[i];
            if (item != null && !item.getType().isAir()) {
                inventory.setItem(i, item.clone());
            }
        }
        holder.setInventory(inventory);
        return inventory;
    }

    public void saveEditorInventory(int day, Inventory inventory) {
        ensureDayExists(day, false);

        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }

        config.set("days." + day + ".items", items.isEmpty() ? "" : ItemSerializer.serializeItems(items.toArray(new ItemStack[0])));
        saveConfig();
    }

    public CompletableFuture<DailyPlayerData> loadPlayer(UUID uuid) {
        return loadPlayer(uuid, false);
    }

    /**
     * @param forceRefresh true = zawsze odczyt z Mongo (join / claim), żeby lobby nie brało pustego cache.
     */
    public CompletableFuture<DailyPlayerData> loadPlayer(UUID uuid, boolean forceRefresh) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (!forceRefresh) {
            DailyPlayerData cached = cache.get(uuid);
            if (cached != null && cached.isLoaded()) {
                return CompletableFuture.completedFuture(cached);
            }
            CompletableFuture<DailyPlayerData> inflight = inflightLoads.get(uuid);
            if (inflight != null) {
                return inflight;
            }
        } else {
            CompletableFuture<DailyPlayerData> inflight = inflightLoads.get(uuid);
            if (inflight != null && !inflight.isDone()) {
                // Po dokończeniu bieżącego loada odczytaj świeżo z Mongo.
                return inflight.thenCompose(ignored -> loadPlayer(uuid, true));
            }
        }

        CompletableFuture<DailyPlayerData> future = fetchPlayerFromMongo(uuid);
        inflightLoads.put(uuid, future);
        return future;
    }

    private CompletableFuture<DailyPlayerData> fetchPlayerFromMongo(UUID uuid) {
        if (collection == null) {
            DailyPlayerData empty = new DailyPlayerData(uuid);
            empty.markLoaded();
            empty.markClean();
            cache.put(uuid, empty);
            inflightLoads.remove(uuid);
            return CompletableFuture.completedFuture(empty);
        }

        CompletableFuture<DailyPlayerData> future = plugin.getMongoManager()
            .toListFuture(collection.find(Filters.eq("uuid", uuid.toString())))
            .thenApply(documents -> {
                DailyPlayerData data;
                if (documents == null || documents.isEmpty()) {
                    data = new DailyPlayerData(uuid);
                } else {
                    Document best = pickBestDailyDocument(documents);
                    data = documentToData(uuid, best);
                    deleteDuplicateDailyDocuments(documents, best);
                }
                data.markLoaded();
                data.markClean();
                cache.put(uuid, data);
                return data;
            }).exceptionally(throwable -> {
                plugin.getLogger().warning("Błąd wczytywania daily z MongoDB dla " + uuid + ": " + throwable.getMessage());
                DailyPlayerData localData = cache.get(uuid);
                if (localData == null || !localData.isLoaded()) {
                    localData = new DailyPlayerData(uuid);
                    localData.markLoaded();
                    localData.markClean();
                    cache.put(uuid, localData);
                }
                return localData;
            });

        future.whenComplete((data, error) -> inflightLoads.remove(uuid));
        return future;
    }

    private DailyPlayerData documentToData(UUID uuid, Document document) {
        DailyPlayerData data = new DailyPlayerData(uuid);
        data.setLastClaimedDay(document.getInteger("lastClaimedDay", 0));
        data.setLastClaimDate(document.getString("lastClaimDate"));
        data.setLastClaimAt(readLong(document, "lastClaimAt"));
        data.markClean();
        return data;
    }

    private Document pickBestDailyDocument(List<Document> documents) {
        Document best = documents.get(0);
        for (int i = 1; i < documents.size(); i++) {
            Document candidate = documents.get(i);
            if (isBetterDailyDocument(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean isBetterDailyDocument(Document candidate, Document current) {
        long candidateAt = readLong(candidate, "lastClaimAt");
        long currentAt = readLong(current, "lastClaimAt");
        if (candidateAt != currentAt) {
            return candidateAt > currentAt;
        }
        int candidateDay = candidate.getInteger("lastClaimedDay", 0);
        int currentDay = current.getInteger("lastClaimedDay", 0);
        if (candidateDay != currentDay) {
            return candidateDay > currentDay;
        }
        String candidateDate = candidate.getString("lastClaimDate");
        String currentDate = current.getString("lastClaimDate");
        if (candidateDate == null) {
            return false;
        }
        if (currentDate == null) {
            return true;
        }
        return candidateDate.compareTo(currentDate) > 0;
    }

    private void deleteDuplicateDailyDocuments(List<Document> documents, Document keep) {
        if (documents.size() < 2 || keep == null) {
            return;
        }
        Object keepId = keep.get("_id");
        List<Object> extraIds = new ArrayList<>();
        for (Document document : documents) {
            Object id = document.get("_id");
            if (id != null && keepId != null && !id.equals(keepId)) {
                extraIds.add(id);
            }
        }
        if (extraIds.isEmpty()) {
            return;
        }
        plugin.getMongoManager().toFuture(collection.deleteMany(Filters.in("_id", extraIds)))
            .exceptionally(error -> {
                plugin.getLogger().warning("[Daily] Nie udało się usunąć zduplikowanych dokumentów: " + error.getMessage());
                return null;
            });
    }

    private static long readLong(Document document, String key) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private void migrateAllYamlPlayersIfPresent() {
        if (collection == null || !playersFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playersFile);
        int migrated = 0;
        for (String key : yaml.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            DailyPlayerData data = new DailyPlayerData(uuid);
            data.setLastClaimedDay(yaml.getInt(key + ".last-claimed-day", 0));
            data.setLastClaimDate(yaml.getString(key + ".last-claim-date"));
            if (data.getLastClaimedDay() <= 0
                && (data.getLastClaimDate() == null || data.getLastClaimDate().isBlank())) {
                continue;
            }
            savePlayerData(data);
            migrated++;
        }
        if (!playersFile.delete()) {
            plugin.getLogger().warning("[Daily] Nie udało się usunąć starego players.yml — skasuj ręcznie.");
        } else if (migrated > 0) {
            plugin.getLogger().info("[Daily] Zmigrowano " + migrated + " graczy z players.yml → MongoDB.");
        }
    }

    public void unloadPlayer(UUID uuid) {
        flushPendingSave(uuid);
        DailyPlayerData data = cache.remove(uuid);
        inflightLoads.remove(uuid);
        claimLocks.remove(uuid);
        if (data != null && data.isDirty()) {
            try {
                savePlayerData(data).orTimeout(3L, TimeUnit.SECONDS).join();
            } catch (Exception exception) {
                plugin.getLogger().warning("[Daily] Timeout zapisu przy quit dla " + uuid + ": " + exception.getMessage());
            }
        }
    }

    private void flushPendingSave(UUID uuid) {
        CompletableFuture<Void> pending = pendingSaves.remove(uuid);
        if (pending == null) {
            return;
        }
        try {
            pending.orTimeout(3L, TimeUnit.SECONDS).join();
        } catch (Exception exception) {
            plugin.getLogger().warning("[Daily] Timeout oczekiwania na zapis dla " + uuid + ": " + exception.getMessage());
        }
    }

    public CompletableFuture<Void> savePlayerData(DailyPlayerData data) {
        if (data == null || data.getUuid() == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (collection == null) {
            data.markClean();
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = data.getUuid();
        Document doc = new Document("uuid", uuid.toString())
            .append("lastClaimedDay", data.getLastClaimedDay())
            .append("lastClaimDate", data.getLastClaimDate())
            .append("lastClaimAt", data.getLastClaimAt())
            .append("updatedAt", System.currentTimeMillis());

        CompletableFuture<Void> saveFuture = plugin.getMongoManager().toFuture(
            collection.replaceOne(
                Filters.eq("uuid", uuid.toString()),
                doc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
            )
        ).thenAccept(result -> data.markClean())
            .exceptionally(throwable -> {
                plugin.getLogger().warning("Nie udało się zapisać daily w MongoDB dla " + uuid + ": " + throwable.getMessage());
                // Zostaw dirty — unload spróbuje ponownie.
                return null;
            });

        pendingSaves.put(uuid, saveFuture);
        saveFuture.whenComplete((ok, err) -> pendingSaves.remove(uuid, saveFuture));
        return saveFuture;
    }

    public void savePlayerAsync(DailyPlayerData data) {
        savePlayerData(data);
    }

    /** Tylko załadowane dane — nigdy nie tworzy pustego wpisu przed Mongo. */
    public DailyPlayerData getPlayerData(UUID uuid) {
        DailyPlayerData data = cache.get(uuid);
        if (data != null && data.isLoaded()) {
            return data;
        }
        return null;
    }

    public boolean isPlayerLoaded(UUID uuid) {
        DailyPlayerData data = cache.get(uuid);
        return data != null && data.isLoaded();
    }

    /**
     * Atomowy odbiór w Mongo: drugi serwer / ponowne kliknięcie po lobby nie da 2× nagrody.
     */
    public void claim(Player player, int day) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        AtomicBoolean lock = claimLocks.computeIfAbsent(uuid, ignored -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            return;
        }

        loadPlayer(uuid, true).whenComplete((loaded, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    lock.set(false);
                    return;
                }
                if (error != null || loaded == null || !loaded.isLoaded()) {
                    lock.set(false);
                    player.sendMessage(ColorUtils.colorizeComponent(config.getString(
                        "messages.loading",
                        "&#FFAA00✘ &8Ξ &eŁadowanie danych nagród… spróbuj za chwilę."
                    )));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                DailyProgress progress = getProgressFromData(loaded);
                if (!progress.canClaimToday() || progress.nextClaimDay() != day) {
                    lock.set(false);
                    player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.already-claimed")));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.openInventory(createMainGui(player));
                    return;
                }

                ItemStack[] rewards = getRewardItems(day);
                if (rewards.length == 0) {
                    lock.set(false);
                    player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.no-reward")));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                long now = System.currentTimeMillis();
                String today = LocalDate.now(WARSAW_ZONE).toString();
                tryClaimInMongo(uuid, day, today, now)
                    .whenComplete((claimed, claimError) -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                if (!player.isOnline()) {
                                    return;
                                }
                                if (claimError != null) {
                                    plugin.getLogger().warning("[Daily] Błąd claim Mongo dla " + uuid + ": " + claimError.getMessage());
                                    player.sendMessage(ColorUtils.colorizeComponent(config.getString(
                                        "messages.loading",
                                        "&#FFAA00✘ &8Ξ &eŁadowanie danych nagród… spróbuj za chwilę."
                                    )));
                                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                                    return;
                                }
                                if (!Boolean.TRUE.equals(claimed)) {
                                    loadPlayer(uuid, true).thenAccept(fresh ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (fresh != null) {
                                                cache.put(uuid, fresh);
                                            }
                                            if (player.isOnline()) {
                                                player.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.already-claimed")));
                                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                                                player.openInventory(createMainGui(player));
                                            }
                                        })
                                    );
                                    return;
                                }

                                for (ItemStack reward : rewards) {
                                    if (reward == null || reward.getType().isAir()) {
                                        continue;
                                    }
                                    player.getInventory().addItem(reward.clone()).values()
                                        .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                                }

                                loaded.setLastClaimedDay(day);
                                loaded.setLastClaimDate(today);
                                loaded.setLastClaimAt(now);
                                loaded.markLoaded();
                                cache.put(uuid, loaded);

                                // Zapisz natychmiast do bazy danych
                                savePlayerData(loaded);

                                player.sendMessage(ColorUtils.colorizeComponent(
                                    config.getString("messages.claimed").replace("{day}", String.valueOf(day))
                                ));
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                                player.openInventory(createMainGui(player));
                            } finally {
                                lock.set(false);
                            }
                        });
                    });
            })
        );
    }

    /**
     * Atomowy odbiór: 24h od ostatniego claimu, bez upsertu na istniejący dokument
     * (upsert przy braku matcha potrafił zrobić drugi wpis i znowu dawać dzień 1).
     */
    private CompletableFuture<Boolean> tryClaimInMongo(UUID uuid, int day, String today, long now) {
        if (collection == null) {
            DailyPlayerData data = cache.computeIfAbsent(uuid, DailyPlayerData::new);
            if (!isCooldownElapsed(data, now, today)) {
                return CompletableFuture.completedFuture(false);
            }
            data.setLastClaimedDay(day);
            data.setLastClaimDate(today);
            data.setLastClaimAt(now);
            data.markLoaded();
            data.markClean();
            return CompletableFuture.completedFuture(true);
        }

        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
            .upsert(false)
            .returnDocument(ReturnDocument.AFTER);

        Bson updates = Updates.combine(
            Updates.set("lastClaimedDay", day),
            Updates.set("lastClaimDate", today),
            Updates.set("lastClaimAt", now),
            Updates.set("updatedAt", now)
        );

        return plugin.getMongoManager().toFuture(
            collection.findOneAndUpdate(readyToClaimFilter(uuid, today, now), updates, options)
        ).thenCompose(document -> {
            if (isSuccessfulClaim(document, day, now)) {
                return CompletableFuture.completedFuture(true);
            }
            return plugin.getMongoManager()
                .toFuture(collection.find(Filters.eq("uuid", uuid.toString())).first())
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    Document insert = new Document("uuid", uuid.toString())
                        .append("lastClaimedDay", day)
                        .append("lastClaimDate", today)
                        .append("lastClaimAt", now)
                        .append("updatedAt", now);
                    return plugin.getMongoManager().toFuture(collection.insertOne(insert))
                        .thenApply(result -> true);
                });
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[Daily] tryClaimInMongo failed for " + uuid + ": " + throwable.getMessage());
            return false;
        });
    }

    private Bson readyToClaimFilter(UUID uuid, String today, long now) {
        long readyBefore = now - CLAIM_COOLDOWN_MS;
        return Filters.and(
            Filters.eq("uuid", uuid.toString()),
            Filters.or(
                Filters.lte("lastClaimAt", readyBefore),
                Filters.and(
                    Filters.exists("lastClaimAt", false),
                    Filters.or(
                        Filters.exists("lastClaimDate", false),
                        Filters.eq("lastClaimDate", null),
                        Filters.ne("lastClaimDate", today)
                    )
                )
            )
        );
    }

    private boolean isSuccessfulClaim(Document document, int day, long now) {
        return document != null
            && day == document.getInteger("lastClaimedDay", -1)
            && now == readLong(document, "lastClaimAt");
    }

    public DailyProgress getProgress(UUID uuid) {
        DailyPlayerData playerData = getPlayerData(uuid);
        if (playerData == null) {
            // Niezaładowane — wszystko zablokowane, żeby nie dało się odebrać na pustym cache.
            return new DailyProgress(0, 1, false, null);
        }
        return getProgressFromData(playerData);
    }

    private DailyProgress getProgressFromData(DailyPlayerData playerData) {
        int lastClaimedDay = playerData.getLastClaimedDay();
        LocalDate lastClaimDate = parseLastClaimDate(playerData.getLastClaimDate());
        int maxDay = Math.max(1, getConfiguredDays().stream().mapToInt(Integer::intValue).max().orElse(7));

        boolean neverClaimed = playerData.getLastClaimAt() <= 0
            && (playerData.getLastClaimDate() == null || playerData.getLastClaimDate().isBlank())
            && lastClaimedDay <= 0;

        if (neverClaimed) {
            return new DailyProgress(0, 1, true, null);
        }

        int nextDay = wrapDay(lastClaimedDay + 1, maxDay);
        long now = System.currentTimeMillis();
        String today = LocalDate.now(WARSAW_ZONE).toString();
        boolean canClaim = isCooldownElapsed(playerData, now, today);
        return new DailyProgress(lastClaimedDay, nextDay, canClaim, lastClaimDate);
    }

    private boolean isCooldownElapsed(DailyPlayerData data, long now, String today) {
        if (data.getLastClaimAt() <= 0
            && (data.getLastClaimDate() == null || data.getLastClaimDate().isBlank())
            && data.getLastClaimedDay() <= 0) {
            return true;
        }
        if (data.getLastClaimAt() > 0) {
            return now - data.getLastClaimAt() >= CLAIM_COOLDOWN_MS;
        }
        String lastClaimDateRaw = data.getLastClaimDate();
        if (lastClaimDateRaw == null || lastClaimDateRaw.isBlank()) {
            return true;
        }
        return !lastClaimDateRaw.equals(today);
    }

    private LocalDate parseLastClaimDate(String lastClaimDateRaw) {
        if (lastClaimDateRaw == null || lastClaimDateRaw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(lastClaimDateRaw);
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<Integer> getConfiguredDays() {
        ConfigurationSection section = config.getConfigurationSection("days");
        if (section == null) {
            return Arrays.asList(1, 2, 3, 4, 5, 6, 7);
        }

        List<Integer> days = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                int day = Integer.parseInt(key);
                if (day > 0) {
                    days.add(day);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (days.isEmpty()) {
            return Arrays.asList(1, 2, 3, 4, 5, 6, 7);
        }
        Collections.sort(days);
        return days;
    }

    public int getDaySlot(int day) {
        String path = "days." + day + ".slot";
        if (config.isSet(path)) {
            int configuredSlot = config.getInt(path, -1);
            if (configuredSlot >= 0 && configuredSlot < plugin.getGuiFactory().getInventorySize(config.getInt("gui.rows", 5))) {
                return configuredSlot;
            }
        }

        List<Integer> days = getConfiguredDays();
        int index = days.indexOf(day);
        if (index >= 0 && index < ORDERED_GUI_SLOTS.size()) {
            return ORDERED_GUI_SLOTS.get(index);
        }

        return -1;
    }

    public ItemStack[] getRewardItems(int day) {
        String serialized = config.getString("days." + day + ".items", "");
        ItemStack[] items = ItemSerializer.deserializeItems(serialized);
        return items == null ? new ItemStack[0] : items;
    }

    public String getMessage(String path) {
        return config.getString("messages." + path, "");
    }

    private ItemStack createDisplayItem(int day, DailyProgress progress) {
        Material material = Material.matchMaterial(config.getString("gui.reward-material", "CHEST_MINECART"));
        if (material == null) {
            material = Material.CHEST_MINECART;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(ColorUtils.colorizeComponent(config.getString("days." + day + ".display-name", "&bCODZIENNA NAGRODA #" + day))
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (progress.canClaimToday() && progress.nextClaimDay() == day) {
            ColorUtils.appendLore(lore, config.getString("gui.claimable-lore"));
        } else if (!progress.canClaimToday() && progress.lastClaimedDay() == day) {
            ColorUtils.appendLore(lore, config.getString("gui.claimed-lore"));
        } else {
            ColorUtils.appendLore(lore, config.getString("gui.locked-lore"));
        }

        lore.add(Component.empty());
        appendRewardPreview(lore, getRewardItems(day));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void appendRewardPreview(List<Component> lore, ItemStack[] rewards) {
        if (rewards.length == 0) {
            ColorUtils.appendLore(lore, config.getString("gui.empty-reward-line"));
            return;
        }

        for (ItemStack reward : rewards) {
            if (reward == null || reward.getType().isAir()) {
                continue;
            }
            String name = getReadableItemName(reward);
            ColorUtils.appendLore(lore, "&7» &f" + reward.getAmount() + "x " + name);
        }
    }

    private String getReadableItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta() != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName() && meta.getDisplayName() != null && !meta.getDisplayName().isBlank()) {
                String name = meta.getDisplayName();
                if (name.matches("(?i)^[a-f0-9]{6}[A-Za-z].*")) {
                    name = "&#" + name;
                }
                return name;
            }
            if (meta.displayName() != null) {
                return LEGACY.serialize(meta.displayName());
            }
        }
        return prettifyMaterial(item.getType());
    }

    private String prettifyMaterial(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private int wrapDay(int day, int maxDay) {
        if (maxDay <= 0) {
            return 1;
        }
        if (day <= maxDay) {
            return Math.max(1, day);
        }
        return 1;
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    public static final class DailyMainHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public static final class DailyEditHolder implements InventoryHolder {
        private final int day;
        private Inventory inventory;

        public DailyEditHolder(int day) {
            this.day = day;
        }

        public int getDay() {
            return day;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public record DailyProgress(int lastClaimedDay, int nextClaimDay, boolean canClaimToday, LocalDate lastClaimDate) {
    }
}
