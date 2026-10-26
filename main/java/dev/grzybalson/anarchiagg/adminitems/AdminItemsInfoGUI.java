package dev.grzybalson.anarchiagg.adminitems;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bson.Document;
import dev.grzybalson.HollyCore;


import dev.grzybalson.anarchiagg.util.SchedulerUtil;
import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AdminItemsInfoGUI implements Listener {

    private static final int DETAIL_SIZE = 54;
    private static final int ITEMS_SIZE = 54;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int PAGE_SIZE = 45;
    private static final long SNAPSHOT_INITIAL_DELAY_TICKS = 20L * 300L;
    private static final long SNAPSHOT_PERIOD_TICKS = 20L * 900L;
    private static final long DETAIL_REFRESH_MILLIS = 15_000L;

    private final HollyCore plugin;
    private final AdminItemsInfoDatabase database;
    private final Map<String, CachedScan> scanCache = new ConcurrentHashMap<>();
    private final Set<String> scanRefreshInProgress = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean fullSnapshotInProgress = new AtomicBoolean();
    private BukkitTask snapshotTask;

    public AdminItemsInfoGUI(HollyCore plugin) {
        this.plugin = plugin;
        this.database = new AdminItemsInfoDatabase(plugin);
    }

    public void startSnapshotTask() {
        if (snapshotTask != null) return;

        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAllSnapshots, SNAPSHOT_INITIAL_DELAY_TICKS, SNAPSHOT_PERIOD_TICKS);
    }

    public void shutdown() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
        scanCache.clear();
        scanRefreshInProgress.clear();
        fullSnapshotInProgress.set(false);
    }

    public void openItems(Player player) {
        openItems(player, true);
    }

    private void openItems(Player player, boolean playSound) {
        List<AdminItemDefinition> definitions = definitions();
        Inventory inventory = Bukkit.createInventory(new ItemsHolder(definitions), ITEMS_SIZE,
                Component.text(dev.grzybalson.utils.ColorUtils.colorize("&8AdminItems - info")));

        for (AdminItemDefinition definition : definitions) {
            ItemStack icon = definition.displayItem();
            if (icon == null) continue;

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setLore(color(List.of(
                        "",
                        "&8x &fKliknij, aby sprawdzic posiadaczy",
                        "&8x &7Liczy ekwipunek, enderchest, shulkery i rynek"
                )));
                icon.setItemMeta(meta);
            }
            inventory.setItem(definition.slot(), icon);
        }

        player.openInventory(inventory);
        if (playSound) {
            SoundsUtil.gui(player);
        }
    }

    private void openDetails(Player player, AdminItemDefinition definition, int page) {
        openDetails(player, definition, page, true);
    }

    private void openDetails(Player player, AdminItemDefinition definition, int page, boolean playSound) {
        CachedScan cached = scanCache.get(definition.cacheKey());
        if (cached != null) {
            openDetails(player, definition, page, cached.scan(), playSound);
            refreshDetailsIfStale(player, definition, page, cached.cachedAtMillis());
            return;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            Document document = database.getSnapshot(definition.cacheKey());
            ScanResult databaseScan = scanResultFromDocument(document);
            SchedulerUtil.runSync(plugin, () -> {
                if (!player.isOnline()) return;

                cacheScan(definition, databaseScan);
                openDetails(player, definition, page, databaseScan, playSound);
                refreshDetailsIfStale(player, definition, page, 0L);
            });
        });
    }

    private void refreshDetailsIfStale(Player player, AdminItemDefinition definition, int page, long cachedAtMillis) {
        if (System.currentTimeMillis() - cachedAtMillis < DETAIL_REFRESH_MILLIS) {
            return;
        }
        refreshDetails(player.getUniqueId(), definition, page);
    }

    private void refreshDetails(UUID playerId, AdminItemDefinition definition, int page) {
        if (!scanRefreshInProgress.add(definition.cacheKey())) {
            return;
        }

        List<AdminItemDefinition> definitions = List.of(definition);
        captureScanData(definitions, capture -> {
            boolean accepted = SchedulerUtil.runAsync(plugin, () -> {
                try {
                    ScanResult scan = countAll(definitions, capture.players(), capture.marketOffers())
                            .getOrDefault(definition, emptyScan());
                    SnapshotWrite snapshot = snapshot(
                            definition,
                            scan,
                            capture.onlinePlayerIds(),
                            true,
                            System.currentTimeMillis()
                    );
                    Document saved = database.saveSnapshot(snapshot);
                    ScanResult mergedScan = scanResultFromDocument(saved);

                    SchedulerUtil.runSync(plugin, () -> {
                        cacheScan(definition, mergedScan);

                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null && player.isOnline() && isViewingDetails(player, definition)) {
                            refreshDetailsInventory(player, definition, page, mergedScan);
                        }
                    });
                } finally {
                    scanRefreshInProgress.remove(definition.cacheKey());
                }
            });
            if (!accepted) {
                scanRefreshInProgress.remove(definition.cacheKey());
            }
        });
    }

    private boolean isViewingDetails(Player player, AdminItemDefinition definition) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        return holder instanceof DetailsHolder detailsHolder
                && detailsHolder.definition().cacheKey().equals(definition.cacheKey());
    }

    private void openDetails(Player player, AdminItemDefinition definition, int page, ScanResult scan, boolean playSound) {
        player.openInventory(createDetailsInventory(definition, page, scan));
        if (playSound) {
            SoundsUtil.gui(player);
        }
    }

    private void refreshDetailsInventory(Player player, AdminItemDefinition definition, int page, ScanResult scan) {
        Inventory openInventory = player.getOpenInventory().getTopInventory();
        if (openInventory == null || openInventory.getSize() != DETAIL_SIZE || !isViewingDetails(player, definition)) {
            return;
        }

        openInventory.setContents(createDetailsInventory(definition, page, scan).getContents());
    }

    private Inventory createDetailsInventory(AdminItemDefinition definition, int page, ScanResult scan) {
        int maxPage = Math.max(0, (scan.players().size() - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, maxPage));

        Inventory inventory = Bukkit.createInventory(new DetailsHolder(definition, currentPage), DETAIL_SIZE,
                Component.text(dev.grzybalson.utils.ColorUtils.colorize("&8Info - " + definition.plainName())));

        int from = currentPage * PAGE_SIZE;
        int to = Math.min(scan.players().size(), from + PAGE_SIZE);
        for (int index = from; index < to; index++) {
            PlayerCount count = scan.players().get(index);
            inventory.setItem(index - from, buildPlayerHead(count));
        }

        inventory.setItem(PREVIOUS_SLOT, navigation(Material.ARROW, "&ePoprzednia strona", "&7Strona: &f" + (currentPage + 1) + "&7/&f" + (maxPage + 1)));
        inventory.setItem(BACK_SLOT, navigation(Material.BARRIER, "&cPowrot", "&7Wroc do listy eventowek"));
        inventory.setItem(NEXT_SLOT, navigation(Material.ARROW, "&eNastepna strona", "&7Strona: &f" + (currentPage + 1) + "&7/&f" + (maxPage + 1)));

        ItemStack summary = definition.displayItem();
        if (summary != null) {
            ItemMeta meta = summary.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize("&6Suma na serwerze: &f" + scan.total()));
                meta.setLore(color(List.of(
                        "&8x &fGracze z itemem: &e" + scan.players().size(),
                        "&8x &fEkwipunki: &e" + scan.inventoryTotal(),
                        "&8x &fEnderchesty: &e" + scan.enderChestTotal(),
                        "&8x &fShulkery: &e" + scan.shulkerTotal(),
                        "&8x &fRynek: &e" + scan.marketTotal()
                )));
                summary.setItemMeta(meta);
                inventory.setItem(47, summary);
            }
        }

        return inventory;
    }

    public void saveAllSnapshots() {
        if (!fullSnapshotInProgress.compareAndSet(false, true)) {
            return;
        }

        List<AdminItemDefinition> definitions = definitions();
        if (definitions.isEmpty()) {
            fullSnapshotInProgress.set(false);
            return;
        }

        captureScanData(definitions, capture -> {
            boolean accepted = SchedulerUtil.runAsync(plugin, () -> {
                try {
                    Map<AdminItemDefinition, ScanResult> scans = countAll(definitions, capture.players(), capture.marketOffers());
                    long updatedAt = System.currentTimeMillis();
                    List<SnapshotWrite> snapshots = new ArrayList<>(definitions.size());

                    for (AdminItemDefinition definition : definitions) {
                        ScanResult scan = scans.getOrDefault(definition, emptyScan());
                        snapshots.add(snapshot(definition, scan, capture.onlinePlayerIds(), true, updatedAt));
                    }

                    cacheSavedDocuments(database.saveSnapshots(snapshots), definitions);
                } finally {
                    fullSnapshotInProgress.set(false);
                }
            });
            if (!accepted) {
                fullSnapshotInProgress.set(false);
            }
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ItemsHolder itemsHolder) {
            event.setCancelled(true);
            AdminItemDefinition definition = itemsHolder.bySlot().get(event.getRawSlot());
            if (definition == null) return;

            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
            openDetails(player, definition, 0, false);
            return;
        }

        if (holder instanceof DetailsHolder detailsHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == BACK_SLOT) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.9f);
                openItems(player, false);
                return;
            }
            if (slot == PREVIOUS_SLOT && detailsHolder.page() > 0) {
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 0.9f);
                openDetails(player, detailsHolder.definition(), detailsHolder.page() - 1, false);
                return;
            }
            if (slot == NEXT_SLOT) {
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.1f);
                openDetails(player, detailsHolder.definition(), detailsHolder.page() + 1, false);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        savePlayerSnapshots(event.getPlayer());
    }

    private void savePlayerSnapshots(Player player) {
        List<SnapshotWrite> snapshots = new ArrayList<>();
        List<AdminItemDefinition> definitions = definitions();
        PlayerInventorySnapshot playerSnapshot = playerSnapshot(player);
        Map<NamespacedKey, CountBreakdown> counts = countPlayer(playerSnapshot, indexDefinitions(definitions));
        String playerId = player.getUniqueId().toString();
        List<String> replacedPlayerIds = List.of(playerId);
        long updatedAt = System.currentTimeMillis();

        for (AdminItemDefinition definition : definitions) {
            CountBreakdown count = counts.getOrDefault(definition.key(), new CountBreakdown());
            PlayerCount playerCount = new PlayerCount(player.getUniqueId(), player.getName(), count);
            mergePlayerCount(definition, playerCount);
            snapshots.add(new SnapshotWrite(
                    definition.cacheKey(),
                    definition.plainName(),
                    count.total(),
                    count.inventory,
                    count.enderChest,
                    count.inventoryShulkers + count.enderChestShulkers,
                    0,
                    List.of(playerDocument(playerCount)),
                    replacedPlayerIds,
                    updatedAt,
                    false
            ));
        }

        SchedulerUtil.runAsync(plugin, () -> cacheSavedDocuments(database.saveSnapshots(snapshots), definitions));
    }

    private void captureScanData(List<AdminItemDefinition> definitions, Consumer<ScanCapture> callback) {
        boolean scheduled = SchedulerUtil.runSync(plugin, () -> {
            List<PlayerInventorySnapshot> players = Bukkit.getOnlinePlayers().stream()
                    .map(this::playerSnapshot)
                    .toList();
            List<String> onlinePlayerIds = players.stream()
                    .map(player -> player.uuid().toString())
                    .toList();

            callback.accept(new ScanCapture(players, marketSnapshots(), onlinePlayerIds));
        });

        if (!scheduled) {
            scanRefreshInProgress.removeAll(definitions.stream().map(AdminItemDefinition::cacheKey).toList());
            fullSnapshotInProgress.set(false);
        }
    }

    private PlayerInventorySnapshot playerSnapshot(Player player) {
        return new PlayerInventorySnapshot(
                player.getUniqueId(),
                player.getName(),
                cloneContents(player.getInventory().getContents()),
                cloneContents(player.getEnderChest().getContents())
        );
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null || contents.length == 0) return new ItemStack[0];

        ItemStack[] cloned = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            cloned[index] = item == null ? null : item.clone();
        }
        return cloned;
    }

    private List<MarketOfferSnapshot> marketSnapshots() {
        return List.of();
    }

    private void cacheSavedDocuments(List<Document> documents, List<AdminItemDefinition> definitions) {
        if (documents == null || documents.isEmpty()) return;

        Map<String, AdminItemDefinition> definitionsByKey = new HashMap<>();
        for (AdminItemDefinition definition : definitions) {
            definitionsByKey.put(definition.cacheKey(), definition);
        }

        for (Document document : documents) {
            String itemKey = document.getString("itemKey");
            AdminItemDefinition definition = definitionsByKey.get(itemKey);
            if (definition != null) {
                cacheScan(definition, scanResultFromDocument(document));
            }
        }
    }

    private Map<AdminItemDefinition, ScanResult> countAll(List<AdminItemDefinition> definitions,
                                                          List<PlayerInventorySnapshot> players,
                                                          List<MarketOfferSnapshot> marketOffers) {
        Map<NamespacedKey, ScanAccumulator> accumulators = indexDefinitions(definitions);
        if (accumulators.isEmpty()) return Map.of();

        for (PlayerInventorySnapshot player : players) {
            Map<NamespacedKey, CountBreakdown> playerCounts = countPlayer(player, accumulators);
            for (Map.Entry<NamespacedKey, CountBreakdown> entry : playerCounts.entrySet()) {
                ScanAccumulator accumulator = accumulators.get(entry.getKey());
                if (accumulator != null) {
                    accumulator.addPlayer(player, entry.getValue());
                }
            }
        }

        for (MarketOfferSnapshot offer : marketOffers) {
            Map<NamespacedKey, CountBreakdown> offerCounts = new HashMap<>();
            countItemAndShulkerContents(offer.item(), accumulators, offerCounts, CountBucket.MARKET, 0);

            for (Map.Entry<NamespacedKey, CountBreakdown> entry : offerCounts.entrySet()) {
                ScanAccumulator accumulator = accumulators.get(entry.getKey());
                if (accumulator != null) {
                    accumulator.addMarket(offer.sellerUuid(), offer.sellerName(), entry.getValue().market);
                }
            }
        }

        Map<AdminItemDefinition, ScanResult> scans = new HashMap<>();
        for (ScanAccumulator accumulator : accumulators.values()) {
            scans.put(accumulator.definition, accumulator.result());
        }
        return scans;
    }

    private Map<NamespacedKey, CountBreakdown> countPlayer(PlayerInventorySnapshot player,
                                                           Map<NamespacedKey, ScanAccumulator> definitionsByKey) {
        Map<NamespacedKey, CountBreakdown> counts = new HashMap<>();

        countContents(player.inventoryContents(), definitionsByKey, counts, CountBucket.INVENTORY, CountBucket.INVENTORY_SHULKER);
        countContents(player.enderChestContents(), definitionsByKey, counts, CountBucket.ENDER_CHEST, CountBucket.ENDER_CHEST_SHULKER);
        return counts;
    }

    private SnapshotWrite snapshot(AdminItemDefinition definition,
                                   ScanResult scan,
                                   List<String> onlinePlayerIds,
                                   boolean refreshMarket,
                                   long updatedAt) {
        List<Document> players = new ArrayList<>();
        for (PlayerCount player : scan.players()) {
            players.add(playerDocument(player));
        }

        return new SnapshotWrite(
                definition.cacheKey(),
                definition.plainName(),
                scan.total(),
                scan.inventoryTotal(),
                scan.enderChestTotal(),
                scan.shulkerTotal(),
                scan.marketTotal(),
                players,
                onlinePlayerIds,
                updatedAt,
                refreshMarket
        );
    }

    private Document playerDocument(PlayerCount player) {
        CountBreakdown count = player.count();
        return new Document("uuid", player.uuid().toString())
                .append("name", player.name())
                .append("total", count.total())
                .append("inventory", count.inventory)
                .append("enderChest", count.enderChest)
                .append("inventoryShulkers", count.inventoryShulkers)
                .append("enderChestShulkers", count.enderChestShulkers)
                .append("market", count.market);
    }

    private ScanResult scanResultFromDocument(Document document) {
        if (document == null) {
            return new ScanResult(List.of(), 0, 0, 0, 0);
        }

        List<PlayerCount> players = new ArrayList<>();
        int inventoryTotal = 0;
        int enderChestTotal = 0;
        int shulkerTotal = 0;
        int marketTotal = 0;

        for (Document playerDocument : document.getList("players", Document.class, List.of())) {
            String uuidText = playerDocument.getString("uuid");
            if (uuidText == null || uuidText.isBlank()) continue;

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            CountBreakdown count = new CountBreakdown();
            count.inventory = intValue(playerDocument, "inventory");
            count.enderChest = intValue(playerDocument, "enderChest");
            count.inventoryShulkers = intValue(playerDocument, "inventoryShulkers");
            count.enderChestShulkers = intValue(playerDocument, "enderChestShulkers");
            count.market = intValue(playerDocument, "market");
            if (count.total() <= 0) continue;

            String name = playerDocument.getString("name");
            players.add(new PlayerCount(uuid, name == null || name.isBlank() ? uuidText : name, count));

            inventoryTotal += count.inventory;
            enderChestTotal += count.enderChest;
            shulkerTotal += count.inventoryShulkers + count.enderChestShulkers;
            marketTotal += count.market;
        }

        players.sort(Comparator
                .comparingInt((PlayerCount count) -> count.count().total()).reversed()
                .thenComparing(PlayerCount::name, String.CASE_INSENSITIVE_ORDER));

        return new ScanResult(players, inventoryTotal, enderChestTotal, shulkerTotal, marketTotal);
    }

    private void countContents(ItemStack[] contents,
                               Map<NamespacedKey, ScanAccumulator> definitionsByKey,
                               Map<NamespacedKey, CountBreakdown> counts,
                               CountBucket directBucket,
                               CountBucket shulkerBucket) {
        if (contents == null) return;

        for (ItemStack item : contents) {
            addAdminItem(item, definitionsByKey, counts, directBucket);
            if (isShulker(item)) {
                countShulkerContents(item, definitionsByKey, counts, shulkerBucket, 0);
            }
        }
    }

    private void countItemAndShulkerContents(ItemStack item,
                                             Map<NamespacedKey, ScanAccumulator> definitionsByKey,
                                             Map<NamespacedKey, CountBreakdown> counts,
                                             CountBucket bucket,
                                             int depth) {
        if (item == null || item.getType() == Material.AIR) return;

        addAdminItem(item, definitionsByKey, counts, bucket);
        if (isShulker(item)) {
            countShulkerContents(item, definitionsByKey, counts, bucket, depth);
        }
    }

    private void countShulkerContents(ItemStack shulkerItem,
                                      Map<NamespacedKey, ScanAccumulator> definitionsByKey,
                                      Map<NamespacedKey, CountBreakdown> counts,
                                      CountBucket bucket,
                                      int depth) {
        if (depth > 3) return;

        ItemStack[] contents = shulkerContents(shulkerItem);
        for (ItemStack content : contents) {
            countItemAndShulkerContents(content, definitionsByKey, counts, bucket, depth + 1);
        }
    }

    private void addAdminItem(ItemStack item,
                              Map<NamespacedKey, ScanAccumulator> definitionsByKey,
                              Map<NamespacedKey, CountBreakdown> counts,
                              CountBucket bucket) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        for (NamespacedKey key : data.getKeys()) {
            if (!definitionsByKey.containsKey(key) || !data.has(key, PersistentDataType.BYTE)) {
                continue;
            }
            addCount(counts.computeIfAbsent(key, ignored -> new CountBreakdown()), bucket, item.getAmount());
        }
    }

    private ItemStack[] shulkerContents(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return new ItemStack[0];
        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof BlockStateMeta meta) || !meta.hasBlockState()) return new ItemStack[0];

        BlockState state = meta.getBlockState();
        if (!(state instanceof ShulkerBox shulkerBox)) return new ItemStack[0];

        return shulkerBox.getSnapshotInventory().getStorageContents();
    }

    private boolean isShulker(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getType().name().endsWith("SHULKER_BOX");
    }

    private ItemStack buildPlayerHead(PlayerCount count) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        boolean online = Bukkit.getPlayer(count.uuid()) != null;
        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize("&e" + count.name() + " &8- &f" + count.count().total() + " szt."));
        meta.setLore(color(List.of(
                "&8x &fStatus: " + (online ? "&aOnline" : "&cOffline"),
                "&8x &fEkwipunek: &e" + count.count().inventory,
                "&8x &fEnderchest: &e" + count.count().enderChest,
                "&8x &fShulkery w eq: &e" + count.count().inventoryShulkers,
                "&8x &fShulkery w enderchescie: &e" + count.count().enderChestShulkers,
                "&8x &fRynek: &e" + count.count().market
        )));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navigation(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(dev.grzybalson.utils.ColorUtils.colorize(name));
        meta.setLore(color(List.of(lore)));
        item.setItemMeta(meta);
        return item;
    }

    private List<AdminItemDefinition> definitions() {
        List<AdminItemDefinition> definitions = new ArrayList<>();
        add(definitions, 0, "lasso", AdminItemsGUI.KEY_LASSO);
        add(definitions, 1, "rozga", AdminItemsGUI.KEY_ROZGA);
        add(definitions, 2, "granat", AdminItemsGUI.KEY_DYNIO_GRANAT);
        add(definitions, 3, "klatwa", AdminItemsGUI.KEY_KLATWA_GORNIKA);
        add(definitions, 4, "pokeball", AdminItemsGUI.KEY_POKEBALL);
        add(definitions, 5, "kolce", AdminItemsGUI.KEY_KOLCE);
        add(definitions, 6, "puszka", AdminItemsGUI.KEY_PUSZKA_PANDORY);
        add(definitions, 7, "lodowa", AdminItemsGUI.KEY_LODOWA_ROZDZKA);
        add(definitions, 8, "nos", AdminItemsGUI.KEY_NOS_OLAFA);
        add(definitions, 9, "mikstura", AdminItemsGUI.KEY_MIKSTURA_ODMLODZENIA);
        add(definitions, 10, "koza", AdminItemsGUI.KEY_KOZA);
        add(definitions, 11, "hak", AdminItemsGUI.KEY_HAK);
        add(definitions, 12, "tornado", AdminItemsGUI.KEY_TORNADO);
        add(definitions, 13, "lupa", AdminItemsGUI.KEY_LUPA_OSWIECENIA);
        add(definitions, 14, "chorus", AdminItemsGUI.KEY_CHORUS_UCIECZKI);
        add(definitions, 15, "trojzab", AdminItemsGUI.KEY_TROJZAB_ZNISZCZENIA);
        add(definitions, 16, "torba", AdminItemsGUI.KEY_TORBA_KANGURA);
        add(definitions, 17, "klata", AdminItemsGUI.KEY_GWIEZDNA_KLATA);
        add(definitions, 18, "czaszka", AdminItemsGUI.KEY_ZLOTA_CZASZKA);
        add(definitions, 19, "smocza", AdminItemsGUI.KEY_SMOCZA_FAJERWERKA);
        add(definitions, 20, "antycobweb", AdminItemsGUI.KEY_ANTY_COBWEB);
        add(definitions, 21, "japko", AdminItemsGUI.KEY_NIESKONCZONE_ZLOTE_JAPKO);
        add(definitions, 22, "kartypiekla", AdminItemsGUI.KEY_KARTY_PIEKLA);
        add(definitions, 23, "krysztal", AdminItemsGUI.KEY_MAGICZNY_KRYSZTAL);
        add(definitions, 24, "laskawu", AdminItemsGUI.KEY_LASKA_MISTRZA_WU);
        add(definitions, 25, "sercezimy", AdminItemsGUI.KEY_SERCE_ZIMY);
        add(definitions, 26, "smoczykiel", AdminItemsGUI.KEY_SMOCZY_KIEL);
        return definitions;
    }

    private void add(List<AdminItemDefinition> definitions, int slot, String cacheKey, NamespacedKey key) {
        ItemStack item = AdminItemsGUI.getCachedItem(cacheKey);
        if (item == null || key == null) return;
        definitions.add(new AdminItemDefinition(slot, cacheKey, key, item));
    }

    private List<String> color(List<String> lore) {
        return lore.stream().map(dev.grzybalson.utils.ColorUtils::colorize).toList();
    }

    private int intValue(Document document, String key) {
        Object value = document.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Map<NamespacedKey, ScanAccumulator> indexDefinitions(List<AdminItemDefinition> definitions) {
        Map<NamespacedKey, ScanAccumulator> definitionsByKey = new HashMap<>();
        for (AdminItemDefinition definition : definitions) {
            definitionsByKey.put(definition.key(), new ScanAccumulator(definition));
        }
        return definitionsByKey;
    }

    private ScanResult emptyScan() {
        return new ScanResult(List.of(), 0, 0, 0, 0);
    }

    private void cacheScan(AdminItemDefinition definition, ScanResult scan) {
        if (definition == null || scan == null) return;
        scanCache.put(definition.cacheKey(), new CachedScan(scan, System.currentTimeMillis()));
    }

    private void mergePlayerCount(AdminItemDefinition definition, PlayerCount playerCount) {
        CachedScan cached = scanCache.get(definition.cacheKey());
        if (cached == null) return;

        List<PlayerCount> players = new ArrayList<>(cached.scan().players());
        players.removeIf(count -> count.uuid().equals(playerCount.uuid()));
        if (playerCount.count().total() > 0) {
            players.add(playerCount);
        }
        players.sort(Comparator
                .comparingInt((PlayerCount count) -> count.count().total()).reversed()
                .thenComparing(PlayerCount::name, String.CASE_INSENSITIVE_ORDER));

        int inventoryTotal = 0;
        int enderChestTotal = 0;
        int shulkerTotal = 0;
        int marketTotal = 0;
        for (PlayerCount count : players) {
            inventoryTotal += count.count().inventory;
            enderChestTotal += count.count().enderChest;
            shulkerTotal += count.count().inventoryShulkers + count.count().enderChestShulkers;
            marketTotal += count.count().market;
        }

        cacheScan(definition, new ScanResult(players, inventoryTotal, enderChestTotal, shulkerTotal, marketTotal));
    }

    private void addCount(CountBreakdown count, CountBucket bucket, int amount) {
        switch (bucket) {
            case INVENTORY -> count.inventory += amount;
            case INVENTORY_SHULKER -> count.inventoryShulkers += amount;
            case ENDER_CHEST -> count.enderChest += amount;
            case ENDER_CHEST_SHULKER -> count.enderChestShulkers += amount;
            case MARKET -> count.market += amount;
        }
    }

    private record ItemsHolder(Map<Integer, AdminItemDefinition> bySlot) implements InventoryHolder {
        private ItemsHolder(List<AdminItemDefinition> definitions) {
            this(toSlotMap(definitions));
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record DetailsHolder(AdminItemDefinition definition, int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record AdminItemDefinition(int slot, String cacheKey, NamespacedKey key, ItemStack item) {
        private ItemStack displayItem() {
            return item.clone();
        }

        private String plainName() {
            ItemMeta meta = item.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return cacheKey;
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            return stripped == null || stripped.isBlank() ? cacheKey : stripped;
        }
    }

    private record PlayerCount(UUID uuid, String name, CountBreakdown count) {
    }

    private record PlayerInventorySnapshot(UUID uuid, String name, ItemStack[] inventoryContents, ItemStack[] enderChestContents) {
    }

    private record MarketOfferSnapshot(UUID sellerUuid, String sellerName, ItemStack item) {
    }

    private record ScanCapture(List<PlayerInventorySnapshot> players, List<MarketOfferSnapshot> marketOffers, List<String> onlinePlayerIds) {
    }

    private enum CountBucket {
        INVENTORY,
        INVENTORY_SHULKER,
        ENDER_CHEST,
        ENDER_CHEST_SHULKER,
        MARKET
    }

    private static final class ScanAccumulator {
        private final AdminItemDefinition definition;
        private final Map<UUID, PlayerCount> counts = new HashMap<>();
        private int inventoryTotal;
        private int enderChestTotal;
        private int shulkerTotal;
        private int marketTotal;

        private ScanAccumulator(AdminItemDefinition definition) {
            this.definition = definition;
        }

        private void addPlayer(PlayerInventorySnapshot player, CountBreakdown count) {
            if (count.total() <= 0) return;

            counts.put(player.uuid(), new PlayerCount(player.uuid(), player.name(), count));
            inventoryTotal += count.inventory;
            enderChestTotal += count.enderChest;
            shulkerTotal += count.inventoryShulkers + count.enderChestShulkers;
        }

        private void addMarket(UUID sellerUuid, String sellerName, int amount) {
            if (amount <= 0) return;

            PlayerCount playerCount = counts.computeIfAbsent(sellerUuid,
                    uuid -> new PlayerCount(uuid, sellerName, new CountBreakdown()));
            playerCount.count().market += amount;
            marketTotal += amount;
        }

        private ScanResult result() {
            List<PlayerCount> players = new ArrayList<>(counts.values());
            players.removeIf(count -> count.count().total() <= 0);
            players.sort(Comparator
                    .comparingInt((PlayerCount count) -> count.count().total()).reversed()
                    .thenComparing(PlayerCount::name, String.CASE_INSENSITIVE_ORDER));

            return new ScanResult(players, inventoryTotal, enderChestTotal, shulkerTotal, marketTotal);
        }
    }

    private static final class CountBreakdown {
        private int inventory;
        private int inventoryShulkers;
        private int enderChest;
        private int enderChestShulkers;
        private int market;

        private int total() {
            return inventory + inventoryShulkers + enderChest + enderChestShulkers + market;
        }
    }

    private record ScanResult(List<PlayerCount> players, int inventoryTotal, int enderChestTotal, int shulkerTotal, int marketTotal) {
        private int total() {
            return inventoryTotal + enderChestTotal + shulkerTotal + marketTotal;
        }
    }

    private record CachedScan(ScanResult scan, long cachedAtMillis) {
    }

    public record SnapshotWrite(
            String itemKey,
            String itemName,
            int total,
            int inventoryTotal,
            int enderChestTotal,
            int shulkerTotal,
            int marketTotal,
            List<Document> players,
            List<String> onlinePlayerIds,
            long updatedAt,
            boolean refreshMarket
    ) {
    }

    private static Map<Integer, AdminItemDefinition> toSlotMap(List<AdminItemDefinition> definitions) {
        Map<Integer, AdminItemDefinition> bySlot = new HashMap<>();
        for (AdminItemDefinition definition : definitions) {
            bySlot.put(definition.slot(), definition);
        }
        return bySlot;
    }
}
