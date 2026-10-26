package dev.grzybalson.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import net.kyori.adventure.text.Component;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class EnderChestManager implements Listener {

    private static final int SELECTOR_SIZE = 45;
    private static final int VANILLA_PAGE_SIZE = 27;
    private static final int DEFAULT_PAGE_ROWS = 3;
    private static final int MIN_PAGE_ROWS = 1;
    private static final int MAX_PAGE_ROWS = 6;
    private static final int SLOTS_PER_ROW = 9;
    private static final long PAGE_OPEN_THROTTLE_MILLIS = 200L;
    private static final int MAX_CONCURRENT_EXTRA_PAGE_SAVES = 6;
    private static final int MAX_EXTRA_PAGE_SAVE_ATTEMPTS = 3;
    private static final long EXTRA_PAGE_SAVE_RETRY_DELAY_TICKS = 40L;
    private static final List<Integer> SELECTOR_PAGE_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    );

    private final HollyCore plugin;
    private final Map<UUID, Map<Integer, ItemStack[]>> extraPages;
    private final Map<UUID, Map<Integer, Integer>> pageHashes;
    private final Map<UUID, ItemStack[]> pendingVanillaPages;
    private final Map<UUID, Long> pageOpenThrottle;
    /** ownerUuid:page → viewer aktualnie edytujący (exclusive lock anty-dupe). */
    private final ConcurrentHashMap<String, UUID> pageEditors;
    private final Set<UUID> enderChestInventoryViewers;
    private final Map<PageSaveKey, PendingPageSave> pendingExtraPageSaves;
    private final Queue<PageSaveKey> extraPageSaveQueue;
    private final Set<PageSaveKey> queuedExtraPageSaves;
    private final MongoCollection<Document> collection;
    private final MongoCollection<Document> recoveryCollection;
    private final Map<UUID, Map<Integer, String>> recoveryPagesCache;
    private final Set<UUID> recoveryCacheLoaded;
    private final File configFile;
    private int runningExtraPageSaves;
    private volatile Map<Integer, Integer> configuredPageSlotsCache;
    private volatile Map<Integer, Integer> configuredSlotPagesCache;
    private volatile Map<Integer, ItemStack> selectorTemplateCache = Map.of();
    private YamlConfiguration config;

    public EnderChestManager(HollyCore plugin) {
        this.plugin = plugin;
        this.extraPages = new ConcurrentHashMap<>();
        this.pageHashes = new ConcurrentHashMap<>();
        this.pendingVanillaPages = new ConcurrentHashMap<>();
        this.pageOpenThrottle = new ConcurrentHashMap<>();
        this.pageEditors = new ConcurrentHashMap<>();
        this.enderChestInventoryViewers = ConcurrentHashMap.newKeySet();
        this.pendingExtraPageSaves = new ConcurrentHashMap<>();
        this.extraPageSaveQueue = new ConcurrentLinkedQueue<>();
        this.queuedExtraPageSaves = ConcurrentHashMap.newKeySet();
        this.collection = plugin.getMongoManager().getCollection("enderchest_extra");
        this.recoveryCollection = plugin.getMongoManager().getCollection("enderchest_extra_recovery");
        this.recoveryPagesCache = new ConcurrentHashMap<>();
        this.recoveryCacheLoaded = ConcurrentHashMap.newKeySet();
        this.configFile = new File(dev.grzybalson.config.HollyDataPaths.getAddonFolder(plugin, "enderchest"), "enderchest.yml");
        ensureDefaultConfig();
        this.config = YamlConfiguration.loadConfiguration(this.configFile);
        addMissingConfigDefaults();
        reloadGuiTemplateCache();
        migrateLegacyYamlRecovery();

        Bukkit.getOnlinePlayers().forEach(player -> loadExtraPages(player.getUniqueId()));
    }

    public void reload() {
        ensureDefaultConfig();
        this.config = YamlConfiguration.loadConfiguration(this.configFile);
        addMissingConfigDefaults();
        this.configuredPageSlotsCache = null;
        this.configuredSlotPagesCache = null;
        reloadGuiTemplateCache();
    }

    public void openEnderChestFor(Player viewer, Player owner) {
        // W walce wlasny enderchest jest zablokowany — podglad cudzego (admin) zostaje.
        if (viewer.equals(owner) && plugin.getCombatManager() != null
            && plugin.getCombatManager().blockEnderchest(viewer)) {
            return;
        }
        SelectorHolder holder = new SelectorHolder(owner.getUniqueId());
        Inventory selector = Bukkit.createInventory(
            holder,
            SELECTOR_SIZE,
            ColorUtils.colorizeComponent(getString("selector.title", "&8EnderChesty"))
        );
        holder.inventory(selector);
        applyGuiTemplate(selector);

        Map<Integer, Integer> pageSlots = getConfiguredPageSlots();
        pageSlots.forEach((page, slot) -> selector.setItem(slot, createPageItem(owner, viewer, page)));

        viewer.openInventory(selector);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    public void openEnderChestPageFor(Player viewer, Player owner, int page) {
        if (viewer == null || owner == null) {
            return;
        }
        int normalizedPage = Math.max(1, page);
        if (!hasAccess(owner, normalizedPage) && !canBypassAccess(viewer, owner)) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            viewer.sendMessage(ColorUtils.error("Nie masz dostępu do tego enderchesta."));
            return;
        }
        if (requiresMongo(normalizedPage) && !ensureMongoAvailable(viewer)) {
            return;
        }
        openChestPage(viewer, owner, normalizedPage);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        loadExtraPages(player.getUniqueId());

        ItemStack[] pendingContents = pendingVanillaPages.remove(player.getUniqueId());
        if (pendingContents != null) {
            Inventory enderChest = player.getEnderChest();
            for (int i = 0; i < VANILLA_PAGE_SIZE; i++) {
                ItemStack item = pendingContents[i];
                enderChest.setItem(i, item == null ? null : item.clone());
            }
            setExtraPage(player.getUniqueId(), 1, pendingContents);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        enderChestInventoryViewers.remove(player.getUniqueId());
        pageOpenThrottle.remove(player.getUniqueId());
        releasePageLocksHeldBy(player.getUniqueId());
        ItemStack[] contents = snapshotPageContents(player.getUniqueId(), 1, player.getEnderChest(), VANILLA_PAGE_SIZE);
        setExtraPage(player.getUniqueId(), 1, contents);
        saveExtraPage(player.getUniqueId(), player.getName(), 1, contents);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer) || !enderChestInventoryViewers.contains(viewer.getUniqueId())) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        if (holder instanceof OfflineSelectorHolder offlineSelectorHolder) {
            handleOfflineSelectorClick(event, offlineSelectorHolder, topInventory);
            return;
        }
        if (!(holder instanceof SelectorHolder selectorHolder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory() != topInventory) {
            return;
        }

        int page = pageFromSlot(event.getRawSlot());
        if (page == -1) {
            return;
        }

        Player owner = plugin.getServer().getPlayer(selectorHolder.ownerUuid());
        if (owner == null) {
            viewer.closeInventory();
            viewer.sendMessage(ColorUtils.error("Ten gracz nie jest już online."));
            return;
        }

        if (!hasAccess(owner, page) && !canBypassAccess(viewer, owner)) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            viewer.sendMessage(ColorUtils.error("Nie masz dostępu do tego enderchesta."));
            return;
        }

        if (isPageOpenThrottled(viewer)) {
            return;
        }
        if (requiresMongo(page) && !ensureMongoAvailable(viewer)) {
            return;
        }
        openChestPage(viewer, owner, page);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            enderChestInventoryViewers.remove(player.getUniqueId());
        }

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof OfflinePageHolder offlinePageHolder) {
            UUID ownerUuid = offlinePageHolder.ownerUuid();
            int page = offlinePageHolder.page();
            if (event.getPlayer() instanceof Player closer) {
                releasePageLock(ownerUuid, page, closer.getUniqueId());
            }
            ItemStack[] contents = snapshotPageContents(ownerUuid, page, inventory, inventory.getSize());
            if (page == 1) {
                pendingVanillaPages.put(ownerUuid, contents);
            }
            setExtraPage(ownerUuid, page, contents);
            saveExtraPage(ownerUuid, offlinePageHolder.ownerName(), page, contents);
            if (event.getPlayer() instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
            }
            return;
        }
        if (!(holder instanceof PageHolder pageHolder)) {
            return;
        }

        UUID ownerUuid = pageHolder.ownerUuid();
        int page = pageHolder.page();
        if (event.getPlayer() instanceof Player closer) {
            releasePageLock(ownerUuid, page, closer.getUniqueId());
        }

        ItemStack[] contents = snapshotPageContents(ownerUuid, page, inventory, inventory.getSize());

        if (page == 1) {
            Player owner = plugin.getServer().getPlayer(ownerUuid);
            if (owner != null && owner.isOnline()) {
                Inventory realEnderChest = owner.getEnderChest();
                for (int i = 0; i < VANILLA_PAGE_SIZE; i++) {
                    ItemStack item = contents[i];
                    realEnderChest.setItem(i, item == null ? null : item.clone());
                }
            } else {
                pendingVanillaPages.put(ownerUuid, contents);
            }
        }

        setExtraPage(ownerUuid, page, contents);
        saveExtraPage(ownerUuid, resolveOnlineName(ownerUuid), page, contents);

        if (event.getPlayer() instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isEnderChestHolder(event.getInventory().getHolder())) {
            enderChestInventoryViewers.add(player.getUniqueId());
        }
    }

    private boolean isEnderChestHolder(InventoryHolder holder) {
        return holder instanceof SelectorHolder
            || holder instanceof PageHolder
            || holder instanceof OfflineSelectorHolder
            || holder instanceof OfflinePageHolder;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnderChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        // Blokuj otwieranie enderchesta podczas walki
        if (plugin.getCombatManager() != null && plugin.getCombatManager().blockEnderchest(player)) {
            return;
        }

        openEnderChestFor(player, player);
    }

    public void openOfflineEnderChestFor(Player viewer, OfflinePlayer owner) {
        if (owner == null) {
            return;
        }
        openOfflineEnderChestFor(viewer, owner.getName() != null ? owner.getName() : owner.getUniqueId().toString(), 1);
    }

    public void openOfflineEnderChestFor(Player viewer, String ownerName, int requestedPage) {
        if (viewer == null || ownerName == null || ownerName.isBlank()) {
            return;
        }

        int page = Math.max(1, requestedPage);
        String lookupName = ownerName.trim();
        if (!ensureMongoAvailable(viewer)) {
            return;
        }
        viewer.sendMessage(ColorUtils.colorizeComponent("&#00E0FFℹ &8Ξ &fŁaduję offline enderchest gracza &f" + lookupName + "&7..."));

        resolveOfflineOwner(lookupName)
            .thenCompose(owner -> {
                if (owner == null) {
                    return CompletableFuture.completedFuture(new OfflineEnderChestLoad(null, Map.of()));
                }
                return loadOfflineEnderChest(owner.uuid()).thenApply(pages -> new OfflineEnderChestLoad(owner, pages));
            })
            .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                if (result.owner() == null) {
                    viewer.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()));
                    return;
                }

                ItemStack[] contents = result.pages().getOrDefault(page, new ItemStack[getPageSize(page)]);
                openOfflineChestPage(viewer, result.owner().uuid(), result.owner().name(), page, contents);
                viewer.sendMessage(ColorUtils.colorizeComponent("&#00FF3B✔ &8Ξ &#4EFA76Otwarto offline enderchest gracza &f" + result.owner().name() + " &7(strona " + page + ")&a."));
            }))
            .exceptionally(throwable -> {
                Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie udało się wczytać offline EC: &f" + throwable.getMessage())));
                return null;
            });
    }

    private CompletableFuture<OfflineOwner> resolveOfflineOwner(String input) {
        try {
            UUID uuid = UUID.fromString(input);
            return CompletableFuture.completedFuture(new OfflineOwner(uuid, input));
        } catch (IllegalArgumentException ignored) {
        }

        String lower = input.toLowerCase(Locale.ROOT);
        Pattern exactName = Pattern.compile("^" + Pattern.quote(input) + "$", Pattern.CASE_INSENSITIVE);

        return plugin.getMongoManager().toFuture(
            collection.find(Filters.or(
                Filters.eq("playerNameLower", lower),
                Filters.regex("playerName", exactName)
            )).first()
        ).thenCompose(extraDoc -> {
            OfflineOwner fromExtra = ownerFromDocument(extraDoc, input);
            if (fromExtra != null) {
                return CompletableFuture.completedFuture(fromExtra);
            }

            return plugin.getMongoManager().toFuture(
                plugin.getMongoManager().getCollection("player_snapshots")
                    .find(Filters.regex("playerName", exactName))
                    .sort(Sorts.descending("createdAt"))
                    .first()
            ).thenApply(snapshotDoc -> ownerFromDocument(snapshotDoc, input));
        });
    }

    private OfflineOwner ownerFromDocument(Document doc, String fallbackName) {
        if (doc == null) {
            return null;
        }
        String uuidString = doc.getString("playerUuid");
        if (uuidString == null || uuidString.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(uuidString);
            String name = doc.getString("playerName");
            if (name == null || name.isBlank()) {
                name = fallbackName;
            }
            return new OfflineOwner(uuid, name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private CompletableFuture<Map<Integer, ItemStack[]>> loadOfflineEnderChest(UUID ownerUuid) {
        CompletableFuture<ItemStack[]> vanillaPage = loadLatestSnapshotEnderChest(ownerUuid);
        CompletableFuture<Map<Integer, ItemStack[]>> extraPagesFuture = loadExtraPagesFromDatabase(ownerUuid);

        return vanillaPage.thenCombine(extraPagesFuture, (vanilla, extras) -> {
            Map<Integer, ItemStack[]> pages = new LinkedHashMap<>();
            ItemStack[] extraPageOne = extras.get(1);
            ItemStack[] pageOne = hasAnyItem(extraPageOne) ? mergePageArrays(extraPageOne, vanilla, 1) : (vanilla != null ? vanilla : new ItemStack[getPageSize(1)]);
            pages.put(1, normalizePage(pageOne, 1));
            extras.entrySet().stream()
                .filter(entry -> entry.getKey() > 1)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> pages.put(entry.getKey(), normalizePage(entry.getValue(), entry.getKey())));
            return pages;
        });
    }

    private CompletableFuture<ItemStack[]> loadLatestSnapshotEnderChest(UUID ownerUuid) {
        return plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("player_snapshots")
                .find(Filters.eq("playerUuid", ownerUuid.toString()))
                .sort(Sorts.descending("createdAt"))
                .first()
        ).thenApply(doc -> {
            if (doc == null) {
                return new ItemStack[getPageSize(1)];
            }
            ItemStack[] contents = deserializeFirstPresent(doc, "enderChest", "enderchest", "ender_chest", "ec", "enderChestContents");
            return normalizePage(contents, 1);
        });
    }

    private CompletableFuture<Map<Integer, ItemStack[]>> loadExtraPagesFromDatabase(UUID ownerUuid) {
        return plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("playerUuid", ownerUuid.toString()))
                .projection(Projections.include("pages", "page1", "enderChest", "page2", "extraSlots", "page3", "page4", "page5"))
                .first()
        ).thenApply(doc -> {
            Map<Integer, ItemStack[]> pages = readExtraPagesFromDocument(doc);
            pages.putAll(readRecoveryPages(ownerUuid));
            return pages;
        });
    }

    private Map<Integer, ItemStack[]> readExtraPagesFromDocument(Document doc) {
        Map<Integer, ItemStack[]> pages = new LinkedHashMap<>();
        if (doc == null) {
            return pages;
        }

        Document storedPages = doc.get("pages", Document.class);
        if (storedPages != null) {
            for (String key : storedPages.keySet()) {
                try {
                    int page = Integer.parseInt(key);
                    if (page >= 1) {
                        pages.put(page, normalizePage(ItemSerializer.deserializeItems(storedPages.getString(key)), page));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        addLegacyOfflinePage(pages, doc, 1, "page1", "enderChest");
        addLegacyOfflinePage(pages, doc, 1, "enderchest", "ender_chest");
        addLegacyOfflinePage(pages, doc, 2, "page2", "extraSlots");
        addLegacyOfflinePage(pages, doc, 3, "page3", null);
        addLegacyOfflinePage(pages, doc, 4, "page4", null);
        addLegacyOfflinePage(pages, doc, 5, "page5", null);
        return pages;
    }

    private void addLegacyOfflinePage(Map<Integer, ItemStack[]> pages, Document doc, int page, String field, String fallbackField) {
        if (pages.containsKey(page)) {
            return;
        }
        String serialized = doc.getString(field);
        if ((serialized == null || serialized.isBlank()) && fallbackField != null) {
            serialized = doc.getString(fallbackField);
        }
        ItemStack[] contents = ItemSerializer.deserializeItems(serialized);
        if (contents != null) {
            pages.put(page, normalizePage(contents, page));
        }
    }

    private ItemStack[] normalizePage(ItemStack[] contents, int page) {
        return normalizePageToSize(contents, getPageSize(page));
    }

    private ItemStack[] normalizePageToSize(ItemStack[] contents, int minimumSize) {
        int size = normalizeInventorySize(Math.max(minimumSize, contents != null ? contents.length : 0));
        ItemStack[] normalized = new ItemStack[size];
        if (contents == null) {
            return normalized;
        }
        for (int i = 0; i < Math.min(size, contents.length); i++) {
            normalized[i] = contents[i] == null ? null : contents[i].clone();
        }
        return normalized;
    }

    private int getPageSize(int page) {
        return getPageRows(page) * SLOTS_PER_ROW;
    }

    private int getPageRows(int page) {
        int rows = config.getInt("pages." + page + ".rows", DEFAULT_PAGE_ROWS);
        return Math.max(MIN_PAGE_ROWS, Math.min(MAX_PAGE_ROWS, rows));
    }

    private int normalizeInventorySize(int requestedSize) {
        int rows = (int) Math.ceil(Math.max(SLOTS_PER_ROW, requestedSize) / (double) SLOTS_PER_ROW);
        rows = Math.max(MIN_PAGE_ROWS, Math.min(MAX_PAGE_ROWS, rows));
        return rows * SLOTS_PER_ROW;
    }

    private ItemStack[] readVanillaEnderChest(Player owner) {
        ItemStack[] contents = new ItemStack[VANILLA_PAGE_SIZE];
        Inventory source = owner.getEnderChest();
        for (int i = 0; i < VANILLA_PAGE_SIZE; i++) {
            ItemStack item = source.getItem(i);
            contents[i] = item == null ? null : item.clone();
        }
        return contents;
    }

    private ItemStack[] mergePageArrays(ItemStack[] stored, ItemStack[] visible, int page) {
        int visibleLength = visible != null ? visible.length : 0;
        ItemStack[] merged = normalizePageToSize(stored, Math.max(getPageSize(page), visibleLength));
        if (visible == null) {
            return merged;
        }
        for (int i = 0; i < Math.min(merged.length, visible.length); i++) {
            merged[i] = visible[i] == null ? null : visible[i].clone();
        }
        return merged;
    }

    private ItemStack[] snapshotPageContents(UUID ownerUuid, int page, Inventory visibleInventory, int visibleSlots) {
        ItemStack[] stored = extraPages
            .computeIfAbsent(ownerUuid, ignored -> new ConcurrentHashMap<>())
            .get(page);
        int minimumSize = Math.max(getPageSize(page), Math.max(visibleSlots, stored != null ? stored.length : 0));
        ItemStack[] merged = normalizePageToSize(stored, minimumSize);
        for (int i = 0; i < Math.min(visibleSlots, merged.length); i++) {
            ItemStack item = visibleInventory != null && i < visibleInventory.getSize() ? visibleInventory.getItem(i) : null;
            merged[i] = item == null ? null : item.clone();
        }
        return merged;
    }

    private void openOfflineSelector(Player viewer, UUID ownerUuid, String ownerName, Map<Integer, ItemStack[]> pages) {
        OfflineSelectorHolder holder = new OfflineSelectorHolder(ownerUuid, ownerName, pages);
        Inventory selector = Bukkit.createInventory(
            holder,
            SELECTOR_SIZE,
            ColorUtils.colorizeComponent("&8Offline EC: &f" + ownerName)
        );
        holder.inventory(selector);
        applyGuiTemplate(selector);

        Map<Integer, Integer> pageSlots = getConfiguredPageSlots();
        pageSlots.forEach((page, slot) -> selector.setItem(slot, createOfflinePageItem(ownerName, page, pages.containsKey(page))));

        viewer.openInventory(selector);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    private void handleOfflineSelectorClick(InventoryClickEvent event, OfflineSelectorHolder holder, Inventory topInventory) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != topInventory) {
            return;
        }
        int page = pageFromSlot(event.getRawSlot());
        if (page == -1) {
            return;
        }
        if (isPageOpenThrottled(viewer)) {
            return;
        }
        openOfflineChestPage(viewer, holder, page);
    }

    private boolean isPageOpenThrottled(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long previous = pageOpenThrottle.put(uuid, now);
        return previous != null && now - previous < PAGE_OPEN_THROTTLE_MILLIS;
    }

    private void openOfflineChestPage(Player viewer, OfflineSelectorHolder selectorHolder, int page) {
        if (!tryAcquirePageLock(viewer, selectorHolder.ownerUuid(), page)) {
            return;
        }
        OfflinePageHolder holder = new OfflinePageHolder(selectorHolder.ownerUuid(), selectorHolder.ownerName(), page);
        int pageSize = getPageSize(page);
        Inventory inventory = Bukkit.createInventory(
            holder,
            pageSize,
            ColorUtils.colorizeComponent("&8Offline EC " + page + ": &f" + selectorHolder.ownerName())
        );
        holder.inventory(inventory);

        ItemStack[] contents = normalizePage(selectorHolder.pages().get(page), page);
        for (int i = 0; i < pageSize; i++) {
            ItemStack item = contents[i];
            inventory.setItem(i, item == null ? null : item.clone());
        }

        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    private void openOfflineChestPage(Player viewer, UUID ownerUuid, String ownerName, int page, ItemStack[] contents) {
        if (!tryAcquirePageLock(viewer, ownerUuid, page)) {
            return;
        }
        OfflinePageHolder holder = new OfflinePageHolder(ownerUuid, ownerName, page);
        int pageSize = getPageSize(page);
        Inventory inventory = Bukkit.createInventory(
            holder,
            pageSize,
            ColorUtils.colorizeComponent("&8Offline EC " + page + ": &f" + ownerName)
        );
        holder.inventory(inventory);

        ItemStack[] normalized = normalizePage(contents, page);
        for (int i = 0; i < pageSize; i++) {
            ItemStack item = normalized[i];
            inventory.setItem(i, item == null ? null : item.clone());
        }

        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    private ItemStack createOfflinePageItem(String ownerName, int page, boolean hasData) {
        ItemStack item = new ItemStack(hasData ? Material.ENDER_CHEST : Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(ColorUtils.colorizeComponent("&5&lOffline EnderChest &f" + page));
        meta.lore(List.of(
            ColorUtils.colorizeComponent(""),
            ColorUtils.colorizeComponent(" &8• &7Gracz: &f" + ownerName),
            ColorUtils.colorizeComponent(" &8• &7Tryb: &#00FF3Bedycja"),
            ColorUtils.colorizeComponent(""),
            ColorUtils.colorizeComponent(" &aKliknij, aby otworzyć")
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void openChestPage(Player viewer, Player owner, int page) {
        if (requiresMongo(page) && !ensureMongoAvailable(viewer)) {
            return;
        }
        if (!tryAcquirePageLock(viewer, owner.getUniqueId(), page)) {
            return;
        }
        PageHolder holder = new PageHolder(owner.getUniqueId(), page);
        int pageSize = getPageSize(page);
        Inventory inventory = Bukkit.createInventory(
            holder,
            pageSize,
            chestTitle(owner, page)
        );
        holder.inventory(inventory);

        if (page == 1) {
            ItemStack[] items = mergePageArrays(getExtraPage(owner.getUniqueId(), page), readVanillaEnderChest(owner), page);
            for (int i = 0; i < pageSize; i++) {
                inventory.setItem(i, items[i]);
            }
        } else {
            ItemStack[] items = normalizePage(getExtraPage(owner.getUniqueId(), page), page);
            for (int i = 0; i < pageSize; i++) {
                inventory.setItem(i, items[i]);
            }
        }

        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
    }

    private static String pageEditorKey(UUID ownerUuid, int page) {
        return ownerUuid + ":" + page;
    }

    private boolean tryAcquirePageLock(Player viewer, UUID ownerUuid, int page) {
        if (viewer == null || ownerUuid == null) {
            return false;
        }
        String key = pageEditorKey(ownerUuid, page);
        UUID viewerId = viewer.getUniqueId();
        UUID existing = pageEditors.putIfAbsent(key, viewerId);
        if (existing == null || existing.equals(viewerId)) {
            pageEditors.put(key, viewerId);
            return true;
        }
        Player editor = Bukkit.getPlayer(existing);
        if (editor == null || !editor.isOnline()) {
            pageEditors.put(key, viewerId);
            return true;
        }
        viewer.sendMessage(ColorUtils.error("Ta strona enderchesta jest już otwarta przez innego gracza."));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        return false;
    }

    private void releasePageLock(UUID ownerUuid, int page, UUID viewerId) {
        if (ownerUuid == null || viewerId == null) {
            return;
        }
        pageEditors.remove(pageEditorKey(ownerUuid, page), viewerId);
    }

    private void releasePageLocksHeldBy(UUID viewerId) {
        if (viewerId == null) {
            return;
        }
        pageEditors.entrySet().removeIf(entry -> viewerId.equals(entry.getValue()));
    }


    private void applyGuiTemplate(Inventory inventory) {
        if (inventory == null || inventory.getSize() != SELECTOR_SIZE) return;
        selectorTemplateCache.forEach((slot, item) -> inventory.setItem(slot, item.clone()));
    }

    private void reloadGuiTemplateCache() {
        File templateFile = new File(plugin.getDataFolder(), "guis/template.yml");
        if (!templateFile.exists()) {
            try {
                plugin.saveResource("guis/template.yml", false);
            } catch (IllegalArgumentException ignored) {
                selectorTemplateCache = Map.of();
                return;
            }
        }

        YamlConfiguration guiTemplate = YamlConfiguration.loadConfiguration(templateFile);
        String sectionName = "template" + SELECTOR_SIZE;
        ConfigurationSection section = guiTemplate.getConfigurationSection(sectionName);
        if (section == null) {
            selectorTemplateCache = Map.of();
            return;
        }

        String itemName = guiTemplate.getString("backgroundItemName", "&c");
        boolean hideAttributes = guiTemplate.getBoolean("hideAttributes", true);
        Map<Integer, ItemStack> cachedItems = new LinkedHashMap<>();
        for (String materialName : section.getKeys(false)) {
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) continue;
            ItemStack decoration = new ItemStack(material);
            ItemMeta meta = decoration.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtils.colorizeComponent(itemName));
                if (hideAttributes) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                decoration.setItemMeta(meta);
            }
            for (int slot : section.getIntegerList(materialName)) {
                if (slot >= 0 && slot < SELECTOR_SIZE) {
                    cachedItems.put(slot, decoration.clone());
                }
            }
        }
        selectorTemplateCache = Collections.unmodifiableMap(cachedItems);
    }

    private Component chestTitle(Player owner, int page) {
        return ColorUtils.colorizeComponent(
            getString("chest.title", "&8EnderChest {PAGE}")
                .replace("{PAGE}", String.valueOf(page))
                .replace("{PLAYER}", owner.getName())
        );
    }

    private boolean hasAccess(Player owner, int page) {
        if (page <= 1) {
            return true;
        }
        String permission = getPagePermission(page);
        return permission == null || permission.isBlank() || owner.hasPermission(permission);
    }

    private boolean canBypassAccess(Player viewer, Player owner) {
        if (viewer.getUniqueId().equals(owner.getUniqueId())) {
            return false;
        }
        return viewer.hasPermission("Holly.cmd.ec.others") || viewer.hasPermission("Holly.cmd.ecsee");
    }

    private boolean requiresMongo(int page) {
        return page > 1;
    }

    private boolean ensureMongoAvailable(Player viewer) {
        if (plugin.getMongoManager().isAvailable()) {
            return true;
        }
        if (viewer != null) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            viewer.sendMessage(ColorUtils.error(
                "MongoDB jest niedostępne. Dodatkowe i offline enderchesty są zablokowane, żeby nie stracić przedmiotów."
            ));
        }
        return false;
    }

    private int pageFromSlot(int slot) {
        return getConfiguredSlotPages().getOrDefault(slot, -1);
    }

    private ItemStack createPageItem(Player owner, Player viewer, int page) {
        boolean available = (hasAccess(owner, page) || canBypassAccess(viewer, owner))
            && (!requiresMongo(page) || plugin.getMongoManager().isAvailable());
        String rankName = getPageRankName(page);

        ItemStack item = new ItemStack(available ? Material.ENDER_CHEST : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String namePath = available ? "selector.page-name" : "selector.locked-name";
        String defaultName = available ? "&5&lEnderChest &f{PAGE}" : "&c&lEnderChest &f{PAGE}";
        String formattedName = getString(namePath, defaultName)
            .replace("{PAGE}", String.valueOf(page))
            .replace("{RANK}", rankName)
            .replace("{PLAYER}", owner.getName());

        List<String> loreLines = getStringList(
            available ? "selector.open-lore" : "selector.locked-lore",
            available
                ? List.of("", " &8• &7Ranga: &f{RANK}", "", " &aKliknij LPM lub PPM, aby otworzyć")
                : List.of("", " &8• &7Ranga: &f{RANK}", "", " &cBrak dostępu do tego enderchesta")
        );

        meta.displayName(ColorUtils.colorizeComponent(formattedName));
        meta.lore(loreLines.stream()
            .map(line -> line
                .replace("{PAGE}", String.valueOf(page))
                .replace("{RANK}", rankName)
                .replace("{PLAYER}", owner.getName()))
            .map(ColorUtils::colorizeComponent)
            .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private List<Integer> getConfiguredPages() {
        ConfigurationSection section = config.getConfigurationSection("pages");
        if (section == null) {
            return List.of(1, 2, 3, 4);
        }

        List<Integer> pages = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            try {
                int page = Integer.parseInt(key);
                if (page > 0) {
                    pages.add(page);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (!pages.contains(1)) {
            pages.add(1);
        }

        pages.sort(Comparator.naturalOrder());
        return pages;
    }

    private Map<Integer, Integer> getConfiguredPageSlots() {
        Map<Integer, Integer> cached = configuredPageSlotsCache;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            cached = configuredPageSlotsCache;
            if (cached != null) {
                return cached;
            }

            List<Integer> pages = getConfiguredPages();
            Map<Integer, Integer> result = new LinkedHashMap<>();
            List<Integer> freeFallbackSlots = new ArrayList<>(SELECTOR_PAGE_SLOTS);

            for (int page : pages) {
                int configuredSlot = config.getInt("pages." + page + ".slot", -1);
                if (configuredSlot >= 0 && configuredSlot < SELECTOR_SIZE && !result.containsValue(configuredSlot)) {
                    result.put(page, configuredSlot);
                    freeFallbackSlots.remove(Integer.valueOf(configuredSlot));
                }
            }

            for (int page : pages) {
                if (result.containsKey(page)) {
                    continue;
                }
                if (freeFallbackSlots.isEmpty()) {
                    break;
                }
                result.put(page, freeFallbackSlots.remove(0));
            }

            cached = Collections.unmodifiableMap(new LinkedHashMap<>(result));
            configuredPageSlotsCache = cached;
            return cached;
        }
    }

    private Map<Integer, Integer> getConfiguredSlotPages() {
        Map<Integer, Integer> cached = configuredSlotPagesCache;
        if (cached != null) {
            return cached;
        }
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : getConfiguredPageSlots().entrySet()) {
            result.put(entry.getValue(), entry.getKey());
        }
        cached = Collections.unmodifiableMap(result);
        configuredSlotPagesCache = cached;
        return cached;
    }

    private String getPagePermission(int page) {
        String configured = getString("pages." + page + ".permission", null);
        if (configured != null) {
            return configured;
        }
        return page <= 1 ? "" : "Holly.cmd.ec.page." + page;
    }

    private String getPageRankName(int page) {
        String fallback = switch (page) {
            case 1 -> "Gracz";
            case 2 -> "VIP";
            case 3 -> "SVIP";
            case 4 -> "ELITA";
            default -> "Strona " + page;
        };
        return getString("pages." + page + ".rank", fallback);
    }

    private String getString(String path, String def) {
        return config.getString(path, def);
    }

    private List<String> getStringList(String path, List<String> def) {
        List<String> list = config.getStringList(path);
        return list == null || list.isEmpty() ? def : list;
    }

    private void ensureDefaultConfig() {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (configFile.exists()) {
            return;
        }

        try {
            plugin.saveResource("addons/enderchest/enderchest.yml", false);
        } catch (IllegalArgumentException exception) {
            try {
                configFile.createNewFile();
            } catch (IOException ioException) {
                plugin.getLogger().warning("Nie udało się utworzyć enderchest/enderchest.yml");
            }
        }
    }

    private void addMissingConfigDefaults() {
        boolean changed = false;
        for (int page = 1; page <= 5; page++) {
            String basePath = "pages." + page;
            if (!config.contains(basePath + ".rows")) {
                config.set(basePath + ".rows", DEFAULT_PAGE_ROWS);
                changed = true;
            }
        }
        if (!config.contains("pages.5.slot")) {
            config.set("pages.5.slot", 22);
            changed = true;
        }
        if (!config.contains("pages.5.rank")) {
            config.set("pages.5.rank", "&dMVP");
            changed = true;
        }
        if (!config.contains("pages.5.permission")) {
            config.set("pages.5.permission", "Holly.cmd.ec.page.5");
            changed = true;
        }

        if (!changed) {
            return;
        }

        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac domyslnych ustawien enderchest/enderchest.yml: " + exception.getMessage());
        }
    }

    private ItemStack[] getExtraPage(UUID playerUuid, int page) {
        return extraPages
            .computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>())
            .computeIfAbsent(page, ignored -> new ItemStack[getPageSize(page)]);
    }

    private void setExtraPage(UUID playerUuid, int page, ItemStack[] contents) {
        extraPages
            .computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>())
            .put(page, contents);
    }

    private void saveExtraPage(UUID playerUuid, String playerName, int page, ItemStack[] contents) {
        if (playerUuid == null || page < 1) {
            return;
        }

        ItemStack[] normalizedContents = normalizePage(contents, page);
        PageSaveKey key = new PageSaveKey(playerUuid, page);
        pendingExtraPageSaves.put(key, new PendingPageSave(playerUuid, playerName, page, normalizedContents, 0, 1));
        enqueueExtraPageSave(key);
    }

    private void enqueueExtraPageSave(PageSaveKey key) {
        if (queuedExtraPageSaves.add(key)) {
            extraPageSaveQueue.add(key);
        }
        drainExtraPageSaveQueue();
    }

    private synchronized void drainExtraPageSaveQueue() {
        while (runningExtraPageSaves < MAX_CONCURRENT_EXTRA_PAGE_SAVES) {
            PageSaveKey key = extraPageSaveQueue.poll();
            if (key == null) {
                return;
            }

            queuedExtraPageSaves.remove(key);
            PendingPageSave save = pendingExtraPageSaves.remove(key);
            if (save == null) {
                continue;
            }

            runningExtraPageSaves++;
            startExtraPageSave(save);
        }
    }

    private void startExtraPageSave(PendingPageSave save) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int pageHash = hashPage(save.contents());
            Map<Integer, Integer> playerPageHashes = pageHashes.computeIfAbsent(save.playerUuid(), ignored -> new ConcurrentHashMap<>());
            Integer previousHash = playerPageHashes.get(save.page());
            if (previousHash != null && previousHash == pageHash) {
                releaseExtraPageSaveSlot();
                return;
            }
            playerPageHashes.put(save.page(), pageHash);
            PendingPageSave hashed = new PendingPageSave(
                save.playerUuid(),
                save.playerName(),
                save.page(),
                save.contents(),
                pageHash,
                save.attempt()
            );

            String serialized = ItemSerializer.serializeItems(hashed.contents());
            if (serialized == null) {
                clearPageHash(hashed);
                releaseExtraPageSaveSlot();
                return;
            }

            writeRecoveryPage(hashed, serialized);
            plugin.getMongoManager().toFuture(
                collection.updateOne(
                    Filters.eq("playerUuid", save.playerUuid().toString()),
                    new Document("$set", buildExtraPageDocument(hashed, serialized)),
                    new UpdateOptions().upsert(true)
                )
            ).whenComplete((result, throwable) -> finishExtraPageSave(hashed, throwable));
        });
    }

    private Document buildExtraPageDocument(PendingPageSave save, String serialized) {
        Document set = new Document()
            .append("playerUuid", save.playerUuid().toString())
            .append("updatedAt", new Date())
            .append("pages." + save.page(), serialized);

        String playerName = save.playerName();
        if (playerName != null && !playerName.isBlank()) {
            set.append("playerName", playerName);
            set.append("playerNameLower", playerName.toLowerCase(Locale.ROOT));
        }

        if (save.page() == 1) {
            set.append("page1", serialized);
            set.append("enderChest", serialized);
        } else if (save.page() == 2) {
            set.append("page2", serialized);
            set.append("extraSlots", serialized);
        } else if (save.page() == 3) {
            set.append("page3", serialized);
        } else if (save.page() == 4) {
            set.append("page4", serialized);
        } else if (save.page() == 5) {
            set.append("page5", serialized);
        }
        return set;
    }

    private void finishExtraPageSave(PendingPageSave save, Throwable throwable) {
        try {
            if (throwable != null) {
                retryOrLogExtraPageSave(save, throwable);
            } else {
                clearRecoveryPage(save.playerUuid(), save.page());
            }
        } finally {
            releaseExtraPageSaveSlot();
        }
    }

    private void retryOrLogExtraPageSave(PendingPageSave save, Throwable throwable) {
        if (!isCurrentPageHash(save)) {
            return;
        }

        if (save.attempt() < MAX_EXTRA_PAGE_SAVE_ATTEMPTS) {
            PendingPageSave retry = new PendingPageSave(
                save.playerUuid(),
                save.playerName(),
                save.page(),
                save.contents(),
                save.pageHash(),
                save.attempt() + 1
            );
            PageSaveKey key = new PageSaveKey(save.playerUuid(), save.page());
            pendingExtraPageSaves.putIfAbsent(key, retry);
            long retryDelay = EXTRA_PAGE_SAVE_RETRY_DELAY_TICKS * save.attempt();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> enqueueExtraPageSave(key), retryDelay);
            return;
        }

        clearPageHash(save);
        plugin.getLogger().warning(
            "Nie udalo sie zapisac strony enderchesta gracza " + save.playerUuid()
                + " po " + save.attempt() + " probach: " + throwable.getMessage()
        );
    }

    private boolean isCurrentPageHash(PendingPageSave save) {
        Map<Integer, Integer> hashes = pageHashes.get(save.playerUuid());
        Integer currentHash = hashes != null ? hashes.get(save.page()) : null;
        return currentHash != null && currentHash == save.pageHash();
    }

    private void clearPageHash(PendingPageSave save) {
        pageHashes.computeIfPresent(save.playerUuid(), (ignored, hashes) -> {
            hashes.remove(save.page(), save.pageHash());
            return hashes.isEmpty() ? null : hashes;
        });
    }

    private void releaseExtraPageSaveSlot() {
        synchronized (this) {
            runningExtraPageSaves = Math.max(0, runningExtraPageSaves - 1);
        }
        drainExtraPageSaveQueue();
    }

    private String resolveOnlineName(UUID playerUuid) {
        Player online = Bukkit.getPlayer(playerUuid);
        return online != null ? online.getName() : null;
    }

    private boolean hasAnyItem(ItemStack[] contents) {
        if (contents == null) {
            return false;
        }
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private ItemStack[] deserializeFirstPresent(Document doc, String... fields) {
        if (doc == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            String serialized = doc.getString(field);
            if (serialized == null || serialized.isBlank()) {
                continue;
            }
            ItemStack[] items = ItemSerializer.deserializeItems(serialized);
            if (items != null) {
                return items;
            }
        }
        return null;
    }

    private void loadExtraPages(UUID playerUuid) {
        if (!plugin.getMongoManager().isAvailable()) {
            Map<Integer, ItemStack[]> recoveredPages = readRecoveryPages(playerUuid);
            if (!recoveredPages.isEmpty()) {
                Map<Integer, ItemStack[]> pages = extraPages.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
                pages.putAll(recoveredPages);
                Map<Integer, Integer> hashes = pageHashes.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
                recoveredPages.forEach((page, contents) -> hashes.put(page, hashPage(contents)));
            }
            return;
        }

        plugin.getMongoManager().toFuture(
            collection.find(Filters.eq("playerUuid", playerUuid.toString()))
                .projection(Projections.include("pages", "page1", "enderChest", "page2", "extraSlots", "page3", "page4", "page5"))
                .first()
        ).thenAccept(doc -> {
            Map<Integer, ItemStack[]> recoveredPages = readRecoveryPages(playerUuid);
            Map<Integer, ItemStack[]> pages = extraPages.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
            if (doc == null && recoveredPages.isEmpty() && !pages.isEmpty()) {
                return;
            }
            pages.clear();

            Document storedPages = doc != null ? doc.get("pages", Document.class) : null;
            if (storedPages != null) {
                Map<Integer, String> serializedPages = new LinkedHashMap<>();
                for (String key : storedPages.keySet()) {
                    try {
                        int page = Integer.parseInt(key);
                        if (page >= 1) {
                            serializedPages.put(page, storedPages.getString(key));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                serializedPages.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> pages.put(entry.getKey(), deserializePage(entry.getValue(), entry.getKey())));
            }

            // Zgodność ze starymi polami
            if (!pages.containsKey(1)) {
                ItemStack[] pageOne = deserializePage(doc != null ? doc.getString("page1") : null, doc != null ? doc.getString("enderChest") : null, 1);
                if (hasAnyItem(pageOne)) {
                    pages.put(1, pageOne);
                }
            }
            if (!pages.containsKey(2)) {
                pages.put(2, deserializePage(doc != null ? doc.getString("page2") : null, doc != null ? doc.getString("extraSlots") : null, 2));
            }
            if (!pages.containsKey(3)) {
                pages.put(3, deserializePage(doc != null ? doc.getString("page3") : null, null, 3));
            }
            if (!pages.containsKey(4)) {
                pages.put(4, deserializePage(doc != null ? doc.getString("page4") : null, null, 4));
            }
            if (!pages.containsKey(5)) {
                pages.put(5, deserializePage(doc != null ? doc.getString("page5") : null, null, 5));
            }
            pages.putAll(recoveredPages);
            Map<Integer, Integer> hashes = pageHashes.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
            hashes.clear();
            pages.forEach((page, contents) -> hashes.put(page, hashPage(contents)));
        }).exceptionally(throwable -> {
            Map<Integer, ItemStack[]> recoveredPages = readRecoveryPages(playerUuid);
            if (!recoveredPages.isEmpty()) {
                Map<Integer, ItemStack[]> pages = extraPages.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
                pages.putAll(recoveredPages);
                Map<Integer, Integer> hashes = pageHashes.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
                recoveredPages.forEach((page, contents) -> hashes.put(page, hashPage(contents)));
            }
            return null;
        });
    }

    private int hashPage(ItemStack[] contents) {
        int result = 1;
        if (contents == null) {
            return result;
        }
        for (ItemStack item : contents) {
            result = 31 * result + (item == null || item.getType() == Material.AIR ? 0 : item.hashCode());
        }
        return result;
    }

    private ItemStack[] deserializePage(String serialized) {
        return deserializePage(serialized, null, 1);
    }

    private ItemStack[] deserializePage(String serialized, int page) {
        return deserializePage(serialized, null, page);
    }

    private ItemStack[] deserializePage(String serialized, String fallback, int page) {
        if ((serialized == null || serialized.isBlank()) && fallback != null && !fallback.isBlank()) {
            serialized = fallback;
        }

        ItemStack[] contents = ItemSerializer.deserializeItems(serialized);
        return normalizePage(contents, page);
    }

    private synchronized void writeRecoveryPage(PendingPageSave save, String serialized) {
        if (serialized == null || serialized.isBlank()) {
            return;
        }

        UUID playerUuid = save.playerUuid();
        recoveryPagesCache
            .computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>())
            .put(save.page(), serialized);
        recoveryCacheLoaded.add(playerUuid);

        if (!plugin.getMongoManager().isAvailable()) {
            return;
        }

        Document set = new Document()
            .append("playerUuid", playerUuid.toString())
            .append("updatedAt", new Date())
            .append("pages." + save.page(), serialized);
        if (save.playerName() != null && !save.playerName().isBlank()) {
            set.append("playerName", save.playerName());
            set.append("playerNameLower", save.playerName().toLowerCase(Locale.ROOT));
        }

        try {
            plugin.getMongoManager().toFuture(
                recoveryCollection.updateOne(
                    Filters.eq("playerUuid", playerUuid.toString()),
                    new Document("$set", set),
                    new UpdateOptions().upsert(true)
                )
            ).join();
        } catch (Exception exception) {
            plugin.getLogger().warning(
                "Nie udalo sie zapisac recovery enderchesta gracza " + playerUuid + ": " + exception.getMessage()
            );
        }
    }

    private synchronized void clearRecoveryPage(UUID playerUuid, int page) {
        Map<Integer, String> cachedPages = recoveryPagesCache.get(playerUuid);
        if (cachedPages != null) {
            cachedPages.remove(page);
            if (cachedPages.isEmpty()) {
                recoveryPagesCache.remove(playerUuid);
            }
        }

        if (!plugin.getMongoManager().isAvailable()) {
            return;
        }

        try {
            plugin.getMongoManager().toFuture(
                recoveryCollection.updateOne(
                    Filters.eq("playerUuid", playerUuid.toString()),
                    new Document("$unset", new Document("pages." + page, ""))
                        .append("$set", new Document("updatedAt", new Date()))
                )
            ).thenCompose(result -> plugin.getMongoManager().toFuture(
                recoveryCollection.find(Filters.eq("playerUuid", playerUuid.toString()))
                    .projection(Projections.include("pages"))
                    .first()
            )).thenCompose(doc -> {
                Document pages = doc != null ? doc.get("pages", Document.class) : null;
                if (pages == null || pages.isEmpty()) {
                    return plugin.getMongoManager().toFuture(
                        recoveryCollection.deleteOne(Filters.eq("playerUuid", playerUuid.toString()))
                    ).thenApply(ignored -> null);
                }
                return CompletableFuture.completedFuture(null);
            }).join();
        } catch (Exception exception) {
            plugin.getLogger().warning(
                "Nie udalo sie wyczyscic recovery enderchesta gracza " + playerUuid + ": " + exception.getMessage()
            );
        }
    }

    private synchronized Map<Integer, ItemStack[]> readRecoveryPages(UUID playerUuid) {
        ensureRecoveryCacheLoaded(playerUuid);

        Map<Integer, String> cachedPages = recoveryPagesCache.get(playerUuid);
        if (cachedPages == null || cachedPages.isEmpty()) {
            return Map.of();
        }

        Map<Integer, ItemStack[]> pages = new LinkedHashMap<>();
        cachedPages.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> pages.put(entry.getKey(), normalizePage(ItemSerializer.deserializeItems(entry.getValue()), entry.getKey())));
        return pages;
    }

    private void ensureRecoveryCacheLoaded(UUID playerUuid) {
        if (recoveryCacheLoaded.contains(playerUuid)) {
            return;
        }

        if (!plugin.getMongoManager().isAvailable()) {
            recoveryCacheLoaded.add(playerUuid);
            return;
        }

        // Nie blokuj głównego wątku .join()-em przy joinie / otwarciu EC.
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ensureRecoveryCacheLoaded(playerUuid));
            return;
        }

        try {
            Document doc = plugin.getMongoManager().toFuture(
                recoveryCollection.find(Filters.eq("playerUuid", playerUuid.toString()))
                    .projection(Projections.include("pages"))
                    .first()
            ).join();
            applyRecoveryDocumentToCache(playerUuid, doc);
            recoveryCacheLoaded.add(playerUuid);
        } catch (Exception exception) {
            plugin.getLogger().warning(
                "Nie udalo sie wczytac recovery enderchesta gracza " + playerUuid + ": " + exception.getMessage()
            );
        }
    }

    private void applyRecoveryDocumentToCache(UUID playerUuid, Document doc) {
        Document pagesSection = doc != null ? doc.get("pages", Document.class) : null;
        if (pagesSection == null || pagesSection.isEmpty()) {
            recoveryPagesCache.remove(playerUuid);
            return;
        }

        Map<Integer, String> pages = recoveryPagesCache.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>());
        pages.clear();
        for (String key : pagesSection.keySet()) {
            try {
                int page = Integer.parseInt(key);
                if (page < 1) {
                    continue;
                }
                String serialized = pagesSection.getString(key);
                if (serialized == null || serialized.isBlank()) {
                    continue;
                }
                pages.put(page, serialized);
            } catch (NumberFormatException ignored) {
            }
        }
        if (pages.isEmpty()) {
            recoveryPagesCache.remove(playerUuid);
        }
    }

    private void migrateLegacyYamlRecovery() {
        File legacyFolder = new File(plugin.getDataFolder(), "recovery/enderchest-extra");
        if (!legacyFolder.isDirectory()) {
            return;
        }

        File[] files = legacyFolder.listFiles((dir, name) -> name != null && name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            deleteRecursively(legacyFolder);
            return;
        }

        int migrated = 0;
        for (File file : files) {
            try {
                String fileName = file.getName();
                UUID playerUuid = UUID.fromString(fileName.substring(0, fileName.length() - 4));
                YamlConfiguration recovery = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection pagesSection = recovery.getConfigurationSection("pages");
                if (pagesSection == null) {
                    if (!file.delete()) {
                        plugin.getLogger().warning("Nie udalo sie usunac starego recovery enderchesta: " + file.getPath());
                    }
                    continue;
                }

                String playerName = recovery.getString("player-name");
                for (String key : pagesSection.getKeys(false)) {
                    try {
                        int page = Integer.parseInt(key);
                        String serialized = pagesSection.getString(key);
                        if (page < 1 || serialized == null || serialized.isBlank()) {
                            continue;
                        }
                        writeRecoveryPage(new PendingPageSave(playerUuid, playerName, page, null, 0, 1), serialized);
                        migrated++;
                    } catch (NumberFormatException ignored) {
                    }
                }

                if (!file.delete()) {
                    plugin.getLogger().warning("Nie udalo sie usunac starego recovery enderchesta: " + file.getPath());
                }
            } catch (Exception exception) {
                plugin.getLogger().warning(
                    "Nie udalo sie zmigrowac recovery enderchesta z " + file.getName() + ": " + exception.getMessage()
                );
            }
        }

        deleteRecursively(legacyFolder);
        if (migrated > 0) {
            plugin.getLogger().info("Zmigrowano " + migrated + " stron recovery enderchesta z YAML do MongoDB.");
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete() && file.exists()) {
            plugin.getLogger().warning("Nie udalo sie usunac: " + file.getPath());
        }
    }

    public CompletableFuture<Void> clearPlayerData(UUID playerUuid) {
        extraPages.remove(playerUuid);
        pageHashes.remove(playerUuid);
        pendingVanillaPages.remove(playerUuid);
        pendingExtraPageSaves.keySet().removeIf(key -> key.playerUuid().equals(playerUuid));
        queuedExtraPageSaves.removeIf(key -> key.playerUuid().equals(playerUuid));
        extraPageSaveQueue.removeIf(key -> key.playerUuid().equals(playerUuid));
        recoveryPagesCache.remove(playerUuid);
        recoveryCacheLoaded.remove(playerUuid);

        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null) {
            Runnable clearChest = () -> online.getEnderChest().clear();
            if (Bukkit.isPrimaryThread()) {
                clearChest.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, clearChest);
            }
        }

        CompletableFuture<Void> mainDelete = plugin.getMongoManager().toFuture(
            collection.deleteOne(Filters.eq("playerUuid", playerUuid.toString()))
        ).thenApply(result -> null);
        CompletableFuture<Void> recoveryDelete = plugin.getMongoManager().toFuture(
            recoveryCollection.deleteOne(Filters.eq("playerUuid", playerUuid.toString()))
        ).thenApply(result -> null);
        return CompletableFuture.allOf(mainDelete, recoveryDelete);
    }

    private record OfflineOwner(UUID uuid, String name) {
    }

    private record OfflineEnderChestLoad(OfflineOwner owner, Map<Integer, ItemStack[]> pages) {
    }

    private record PageSaveKey(UUID playerUuid, int page) {
    }

    private record PendingPageSave(UUID playerUuid, String playerName, int page, ItemStack[] contents, int pageHash, int attempt) {
    }

    private static final class OfflineSelectorHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private final String ownerName;
        private final Map<Integer, ItemStack[]> pages;
        private Inventory inventory;

        private OfflineSelectorHolder(UUID ownerUuid, String ownerName, Map<Integer, ItemStack[]> pages) {
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.pages = pages;
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public String ownerName() {
            return ownerName;
        }

        public Map<Integer, ItemStack[]> pages() {
            return pages;
        }

        public void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class OfflinePageHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private final String ownerName;
        private final int page;
        private Inventory inventory;

        private OfflinePageHolder(UUID ownerUuid, String ownerName, int page) {
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
            this.page = page;
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public String ownerName() {
            return ownerName;
        }

        public int page() {
            return page;
        }

        public void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class SelectorHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private Inventory inventory;

        private SelectorHolder(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class PageHolder implements InventoryHolder {
        private final UUID ownerUuid;
        private final int page;
        private Inventory inventory;

        private PageHolder(UUID ownerUuid, int page) {
            this.ownerUuid = ownerUuid;
            this.page = page;
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public int page() {
            return page;
        }

        public void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
