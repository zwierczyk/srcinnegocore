package dev.grzybalson.commands;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import dev.grzybalson.HollyCore;
import dev.grzybalson.modules.market.MarketItem;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchItemCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int GUI_SIZE = 54;
    private static final int[] CONTENT_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int ITEMS_PER_PAGE = CONTENT_SLOTS.length;
    private static final int PREVIOUS_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final HollyCore plugin;
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SearchSession> sessions = new ConcurrentHashMap<>();
    private final NamespacedKey uniqueUidKey;
    private final NamespacedKey eventItemIdKey;

    public SearchItemCommand(HollyCore plugin) {
        this.plugin = plugin;
        this.uniqueUidKey = new NamespacedKey(plugin, "uid");
        this.eventItemIdKey = new NamespacedKey(plugin, "event-item-id");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(plugin.getConfigManager().getMessagesConfig().getPlayerOnly()));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.szukaj")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.szukaj"));
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cWeź szukany przedmiot do ręki i wpisz &f/szukaj&c."));
            return true;
        }

        if (!searching.add(player.getUniqueId())) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FFB800⚠ &8Ξ &eWyszukiwanie już trwa. Poczekaj na wynik."));
            return true;
        }

        ItemStack template = normalizeForSearch(hand);
        SearchSession session = new SearchSession(player.getUniqueId(), template);
        sessions.put(player.getUniqueId(), session);

        Set<UUID> onlineUuids = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            onlineUuids.add(online.getUniqueId());
            session.addResult(online.getUniqueId(), online.getName(), "EQ",
                countSimilar(template, online.getInventory().getContents()), true, null, template);
            session.addResult(online.getUniqueId(), online.getName(), "ENDER CHEST",
                countSimilar(template, online.getEnderChest().getContents()), true, null, template);
            var mslf = plugin.getMslfManager();
            var mslfEc = mslf != null ? mslf.enderChestService() : null;
            if (mslfEc != null) {
                ItemStack[] fullEc = mslfEc.captureContents(online);
                int extra = countSimilar(template, fullEc) - countSimilar(template, online.getEnderChest().getContents());
                if (extra > 0) {
                    session.addResult(online.getUniqueId(), online.getName(), "ENDER CHEST MSLF",
                        extra, true, "Dodatkowy EC", template);
                }
            }
        }

        if (plugin.getMarketManager() != null && plugin.getMarketManager().getDataManager() != null) {
            for (MarketItem marketItem : plugin.getMarketManager().getDataManager().getAllItems()) {
                int amount = countSimilar(template, marketItem.getItem());
                session.addResult(
                    marketItem.getSellerUuid(),
                    resolveName(marketItem.getSellerUuid(), marketItem.getSellerName()),
                    "RYNEK",
                    amount,
                    Bukkit.getPlayer(marketItem.getSellerUuid()) != null,
                    "ID: " + marketItem.getId(),
                    template
                );
            }
        }

        // GUI od razu — wyniki online/rynek już w środku, reszta dociąga na żywo.
        openOrRefresh(player, session, true);
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &fSzukam… wyniki pojawiają się na bieżąco w GUI."
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.55f, 1.4f);

        AtomicInteger pending = new AtomicInteger(4);
        AtomicBoolean finished = new AtomicBoolean(false);

        Runnable tryFinish = () -> {
            if (pending.decrementAndGet() > 0 || !finished.compareAndSet(false, true)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                session.markDone();
                searching.remove(player.getUniqueId());
                if (!player.isOnline()) {
                    sessions.remove(player.getUniqueId());
                    return;
                }
                openOrRefresh(player, session, false);
                player.sendMessage(ColorUtils.colorizeComponent(
                    "&#00FF3B✔ &8Ξ &#4EFA76Szukanie zakończone. Wpisy: &f" + session.resultsView().size()
                        + "&a. &7Stron: &f" + Math.max(1, session.maxPage() + 1) + "&a."
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.6f);
            });
        };

        safeSearch(() -> searchLatestSnapshots(template, onlineUuids), "snapshoty")
            .whenComplete((results, error) -> pushAsyncResults(player, session, results, tryFinish));

        safeSearch(() -> searchExtraEnderChests(template), "extra EC")
            .whenComplete((results, error) -> pushAsyncResults(player, session, results, tryFinish));

        safeSearch(() -> searchMslfEnderChests(template, onlineUuids), "MSLF EC")
            .whenComplete((results, error) -> pushAsyncResults(player, session, results, tryFinish));

        safeSearch(() -> searchItemSync(template, onlineUuids), "ItemSync")
            .whenComplete((results, error) -> pushAsyncResults(player, session, results, tryFinish));

        // timeout bezpieczeństwa
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (finished.compareAndSet(false, true)) {
                session.markDone();
                searching.remove(player.getUniqueId());
                if (player.isOnline()) {
                    openOrRefresh(player, session, false);
                    player.sendMessage(ColorUtils.colorizeComponent(
                        "&#FFB800⚠ &8Ξ &eSzukanie zakończone timeoutem. Wpisy: &f" + session.resultsView().size()
                    ));
                }
            }
        }, 20L * 50L);

        return true;
    }

    private void pushAsyncResults(Player player, SearchSession session, List<SearchResult> results, Runnable onDone) {
        try {
            if (results != null && !results.isEmpty()) {
                for (SearchResult result : results) {
                    session.addResult(
                        result.playerUuid(),
                        resolveName(result.playerUuid(), result.playerName()),
                        result.source(),
                        result.amount(),
                        result.online(),
                        result.note(),
                        result.displayItem()
                    );
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && sessions.get(player.getUniqueId()) == session) {
                        openOrRefresh(player, session, false);
                    }
                });
            }
        } finally {
            onDone.run();
        }
    }

    private CompletableFuture<List<SearchResult>> safeSearch(
        java.util.function.Supplier<CompletableFuture<List<SearchResult>>> supplier,
        String label
    ) {
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            return supplier.get()
                .orTimeout(40, TimeUnit.SECONDS)
                .exceptionally(error -> {
                    plugin.getLogger().warning("[szukaj] " + label + ": " + error.getMessage());
                    return List.of();
                });
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[szukaj] " + label + ": " + exception.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private CompletableFuture<List<SearchResult>> searchLatestSnapshots(ItemStack template, Set<UUID> skipOnline) {
        List<Bson> pipeline = new ArrayList<>();
        if (!skipOnline.isEmpty()) {
            pipeline.add(Aggregates.match(Filters.nin(
                "playerUuid",
                skipOnline.stream().map(UUID::toString).toList()
            )));
        }
        pipeline.add(Aggregates.match(Filters.eq("type", "global_snapshot")));
        pipeline.add(Aggregates.sort(Sorts.descending("createdAt")));
        pipeline.add(Aggregates.group(
            "$playerUuid",
            Accumulators.first("playerName", "$playerName"),
            Accumulators.first("createdAt", "$createdAt"),
            Accumulators.first("inventory", "$inventory"),
            Accumulators.first("armor", "$armor"),
            Accumulators.first("enderChest", "$enderChest")
        ));
        pipeline.add(Aggregates.project(Projections.fields(
            Projections.computed("playerUuid", "$_id"),
            Projections.include("playerName", "createdAt", "inventory", "armor", "enderChest")
        )));

        return plugin.getMongoManager().toListFuture(
            plugin.getMongoManager().getCollection("player_snapshots").aggregate(pipeline)
        ).thenApply(docs -> {
            List<SearchResult> results = new ArrayList<>();
            for (Document doc : docs) {
                try {
                    String uuidRaw = doc.getString("playerUuid");
                    if (uuidRaw == null && doc.get("_id") != null) {
                        uuidRaw = String.valueOf(doc.get("_id"));
                    }
                    if (uuidRaw == null || uuidRaw.isBlank()) {
                        continue;
                    }
                    UUID uuid = UUID.fromString(uuidRaw);
                    String playerName = resolveName(uuid, doc.getString("playerName"));
                    Date createdAt = doc.getDate("createdAt");
                    String age = createdAt == null ? null : "Snapshot: " + DATE_FORMAT.format(createdAt);

                    ItemStack[] inventory = ItemSerializer.deserializeItems(doc.getString("inventory"));
                    ItemStack[] armor = ItemSerializer.deserializeItems(doc.getString("armor"));
                    ItemStack[] enderChest = ItemSerializer.deserializeItems(doc.getString("enderChest"));

                    int inventoryCount = countSimilar(template, inventory) + countSimilar(template, armor);
                    int enderCount = countSimilar(template, enderChest);

                    addIfFound(results, template, uuid, playerName, "EQ OFFLINE", inventoryCount, false, age);
                    addIfFound(results, template, uuid, playerName, "ENDER CHEST OFFLINE", enderCount, false, age);
                } catch (Exception ignored) {
                }
            }
            return results;
        });
    }

    private CompletableFuture<List<SearchResult>> searchExtraEnderChests(ItemStack template) {
        return plugin.getMongoManager().toListFuture(
            plugin.getMongoManager().getCollection("enderchest_extra").find()
        ).thenApply(docs -> {
            List<SearchResult> results = new ArrayList<>();
            for (Document doc : docs) {
                try {
                    UUID uuid = UUID.fromString(doc.getString("playerUuid"));
                    String playerName = resolveName(uuid, null);
                    Map<Integer, ItemStack[]> pages = readExtraPages(doc);
                    for (Map.Entry<Integer, ItemStack[]> pageEntry : pages.entrySet()) {
                        int amount = countSimilar(template, pageEntry.getValue());
                        addIfFound(results, template, uuid, playerName,
                            "ENDER CHEST STRONA " + pageEntry.getKey(), amount,
                            Bukkit.getPlayer(uuid) != null, "Dodatkowy EC");
                    }
                } catch (Exception ignored) {
                }
            }
            return results;
        });
    }

    private CompletableFuture<List<SearchResult>> searchMslfEnderChests(ItemStack template, Set<UUID> skipOnline) {
        return plugin.getMongoManager().toListFuture(
            plugin.getMongoManager().getCollection("mslf_enderchest").find()
        ).thenApply(docs -> {
            List<SearchResult> results = new ArrayList<>();
            for (Document doc : docs) {
                try {
                    Object id = doc.get("_id");
                    if (id == null) {
                        continue;
                    }
                    UUID uuid = UUID.fromString(String.valueOf(id));
                    if (skipOnline.contains(uuid)) {
                        continue;
                    }
                    String playerName = resolveName(uuid, doc.getString("name"));
                    ItemStack[] contents = ItemSerializer.deserializeItems(doc.getString("contents"));
                    int amount = countSimilar(template, contents);
                    addIfFound(results, template, uuid, playerName, "ENDER CHEST MSLF", amount, false, "Dodatkowy EC");
                } catch (Exception ignored) {
                }
            }
            return results;
        });
    }

    private CompletableFuture<List<SearchResult>> searchItemSync(ItemStack template, Set<UUID> skipOnline) {
        if (plugin.getInventorySyncManager() == null || !plugin.getInventorySyncManager().isEnabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        String collection = plugin.getInventorySyncManager().getCollectionName();
        return plugin.getMongoManager().toListFuture(
            plugin.getMongoManager().getCollection(collection).find()
                .projection(Projections.include("_id", "playerName", "name", "inventory", "armor", "enderChest", "enderchest"))
        ).thenApply(docs -> {
            List<SearchResult> results = new ArrayList<>();
            for (Document doc : docs) {
                try {
                    Object id = doc.get("_id");
                    if (id == null) {
                        continue;
                    }
                    UUID uuid = UUID.fromString(String.valueOf(id));
                    if (skipOnline.contains(uuid)) {
                        continue;
                    }
                    String playerName = resolveName(uuid,
                        doc.getString("playerName") != null ? doc.getString("playerName") : doc.getString("name"));
                    ItemStack[] inventory = ItemSerializer.deserializeItems(
                        firstString(doc, "inventory"));
                    ItemStack[] armor = ItemSerializer.deserializeItems(firstString(doc, "armor"));
                    ItemStack[] enderChest = ItemSerializer.deserializeItems(
                        firstString(doc, "enderChest", "enderchest"));
                    int inventoryCount = countSimilar(template, inventory) + countSimilar(template, armor);
                    int enderCount = countSimilar(template, enderChest);
                    addIfFound(results, template, uuid, playerName, "EQ OFFLINE", inventoryCount, false, "ItemSync");
                    addIfFound(results, template, uuid, playerName, "ENDER CHEST OFFLINE", enderCount, false, "ItemSync");
                } catch (Exception ignored) {
                }
            }
            return results;
        });
    }

    private static String firstString(Document doc, String... keys) {
        for (String key : keys) {
            String value = doc.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Map<Integer, ItemStack[]> readExtraPages(Document doc) {
        Map<Integer, ItemStack[]> pages = new LinkedHashMap<>();
        Document storedPages = doc != null ? doc.get("pages", Document.class) : null;
        if (storedPages != null) {
            for (String key : storedPages.keySet()) {
                try {
                    int page = Integer.parseInt(key);
                    if (page >= 1) {
                        ItemStack[] contents = ItemSerializer.deserializeItems(storedPages.getString(key));
                        if (contents != null) {
                            pages.put(page, contents);
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        addLegacyPage(pages, doc, 2, "page2", "extraSlots");
        addLegacyPage(pages, doc, 3, "page3", null);
        addLegacyPage(pages, doc, 4, "page4", null);
        return pages;
    }

    private void addLegacyPage(Map<Integer, ItemStack[]> pages, Document doc, int page, String field, String fallbackField) {
        if (pages.containsKey(page)) {
            return;
        }
        String serialized = doc != null ? doc.getString(field) : null;
        if ((serialized == null || serialized.isBlank()) && fallbackField != null && doc != null) {
            serialized = doc.getString(fallbackField);
        }
        ItemStack[] contents = ItemSerializer.deserializeItems(serialized);
        if (contents != null) {
            pages.put(page, contents);
        }
    }

    private void addIfFound(List<SearchResult> results, ItemStack template, UUID uuid, String playerName,
                            String source, int amount, boolean online, String note) {
        if (amount <= 0) {
            return;
        }
        results.add(new SearchResult(uuid, playerName == null ? uuid.toString() : playerName,
            source, amount, online, note, template.clone()));
    }

    private int countSimilar(ItemStack template, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        int count = isSameItemIgnoringAmountAndDamage(template, item) ? item.getAmount() : 0;
        return count + countInsideContainer(template, item);
    }

    private int countInsideContainer(ItemStack template, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta) || !blockMeta.hasBlockState()) {
            return 0;
        }
        if (!(blockMeta.getBlockState() instanceof ShulkerBox shulker)) {
            return 0;
        }
        return countSimilar(template, shulker.getInventory().getContents());
    }

    private boolean isSameItemIgnoringAmountAndDamage(ItemStack template, ItemStack item) {
        ItemStack right = normalizeForSearch(item);
        if (right.isSimilar(template)) {
            return true;
        }
        String leftId = taggedItemId(template);
        String rightId = taggedItemId(right);
        if (leftId != null && leftId.equals(rightId)) {
            return true;
        }
        if (template.getType() != right.getType()) {
            return false;
        }
        String leftName = plainName(template);
        String rightName = plainName(right);
        return leftName != null && leftName.equals(rightName);
    }

    private ItemStack normalizeForSearch(ItemStack source) {
        ItemStack copy = source.clone();
        copy.setAmount(1);
        if (plugin.getUniqueItemService() != null) {
            plugin.getUniqueItemService().clearUid(copy);
        }
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(0);
        }
        meta.getPersistentDataContainer().remove(uniqueUidKey);
        copy.setItemMeta(meta);
        return copy;
    }

    private String taggedItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(eventItemIdKey, PersistentDataType.STRING);
    }

    private String plainName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        Component name = meta.displayName();
        if (name == null) {
            return null;
        }
        String plain = PLAIN_TEXT.serialize(name).trim();
        return plain.isEmpty() ? null : plain;
    }

    private int countSimilar(ItemStack template, ItemStack[] items) {
        if (items == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : items) {
            count += countSimilar(template, item);
        }
        return count;
    }

    /** Preferuj prawdziwy nick — nie UUID z snapshotów / OfflinePlayer. */
    private String resolveName(UUID uuid, String preferred) {
        if (preferred != null && !preferred.isBlank() && !looksLikeUuid(preferred)) {
            return preferred;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        if (plugin.getJoinInfoManager() != null) {
            String stored = plugin.getJoinInfoManager().getPlayerName(uuid);
            if (stored != null && !stored.isBlank() && !looksLikeUuid(stored)) {
                return stored;
            }
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank() && !looksLikeUuid(offline.getName())) {
            return offline.getName();
        }
        return preferred != null && !preferred.isBlank() ? preferred : uuid.toString();
    }

    private static boolean looksLikeUuid(String value) {
        if (value == null || value.length() < 32) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }
    }

    private void openOrRefresh(Player player, SearchSession session, boolean forceOpen) {
        List<SearchResult> results = session.resultsView();
        int maxPage = Math.max(0, (int) Math.ceil(results.size() / (double) ITEMS_PER_PAGE) - 1);
        int page = Math.max(0, Math.min(session.page(), maxPage));
        session.setPage(page);

        Inventory open = player.getOpenInventory() != null ? player.getOpenInventory().getTopInventory() : null;
        boolean sameGui = open != null
            && open.getHolder() instanceof SearchGuiHolder holder
            && holder.owner().equals(player.getUniqueId());

        if (sameGui && !forceOpen) {
            SearchGuiHolder holder = (SearchGuiHolder) open.getHolder();
            holder.update(results, page, maxPage);
            renderInto(open, session.template(), results, page, maxPage);
            return;
        }

        SearchGuiHolder holder = new SearchGuiHolder(player.getUniqueId(), session.template().clone(), results, page, maxPage);
        Inventory inventory = Bukkit.createInventory(holder, GUI_SIZE, ColorUtils.colorizeComponent(
            "&8Szukaj itemu &7(" + (page + 1) + "/" + (maxPage + 1)
                + (session.searching() ? " &e…" : "") + ")"
        ));
        holder.inventory(inventory);
        renderInto(inventory, session.template(), results, page, maxPage);
        player.openInventory(inventory);
        player.setItemOnCursor(null);
    }

    private void renderInto(Inventory inventory, ItemStack template, List<SearchResult> results, int page, int maxPage) {
        for (int slot = 0; slot < GUI_SIZE; slot++) {
            inventory.setItem(slot, null);
        }

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(results.size(), start + ITEMS_PER_PAGE);
        for (int i = start; i < end; i++) {
            int contentIndex = i - start;
            if (contentIndex >= CONTENT_SLOTS.length) {
                break;
            }
            inventory.setItem(CONTENT_SLOTS[contentIndex], createResultItem(results.get(i)));
        }

        if (results.isEmpty()) {
            inventory.setItem(22, simpleItem(Material.BARRIER, "&cBrak wyników (jeszcze)",
                List.of("&7Szukanie trwa — wyniki pojawią się tu na żywo.")));
        }

        ItemStack filler = simpleItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int fillerSlot = 45; fillerSlot < GUI_SIZE; fillerSlot++) {
            inventory.setItem(fillerSlot, filler);
        }

        inventory.setItem(INFO_SLOT, createInfoItem(template, results.size(), page, maxPage));
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, simpleItem(Material.ARROW, "&aPoprzednia strona", List.of(
                "&7Kliknij, aby cofnąć.",
                "&7Strona: &f" + page + "&7/&f" + (maxPage + 1)
            )));
        }
        if (page < maxPage) {
            inventory.setItem(NEXT_SLOT, simpleItem(Material.ARROW, "&aNastępna strona", List.of(
                "&7Kliknij, aby przejść dalej.",
                "&7Strona: &f" + (page + 2) + "&7/&f" + (maxPage + 1)
            )));
        }
    }

    private ItemStack createResultItem(SearchResult result) {
        ItemStack item = result.displayItem().clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String nick = resolveName(result.playerUuid(), result.playerName());
        meta.displayName(ColorUtils.colorizeComponent("&f" + nick + " &8• &7x" + result.amount()));
        List<Component> lore = new ArrayList<>();
        lore.add(ColorUtils.colorizeComponent(""));
        lore.add(ColorUtils.colorizeComponent("&8• &7Nick: &f" + nick));
        lore.add(ColorUtils.colorizeComponent("&8• &7Ilość: &f" + result.amount()));
        lore.add(ColorUtils.colorizeComponent("&8• &7Gdzie: &f" + result.source()));
        lore.add(ColorUtils.colorizeComponent("&8• &7Status: " + (result.online() ? "&aonline" : "&coffline")));
        lore.add(ColorUtils.colorizeComponent("&8• &7UUID: &8" + result.playerUuid()));
        if (result.note() != null && !result.note().isBlank()) {
            lore.add(ColorUtils.colorizeComponent("&8• &7Info: &f" + result.note()));
        }
        lore.add(ColorUtils.colorizeComponent(""));
        lore.add(ColorUtils.colorizeComponent("&8Tylko podgląd — nie da się zabrać."));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem(ItemStack template, int resultCount, int page, int maxPage) {
        ItemStack item = template.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtils.colorizeComponent("&aSzukany przedmiot"));
            meta.lore(List.of(
                ColorUtils.colorizeComponent(""),
                ColorUtils.colorizeComponent("&8• &7Wyniki: &f" + resultCount),
                ColorUtils.colorizeComponent("&8• &7Strona: &f" + (page + 1) + "&7/&f" + (maxPage + 1)),
                ColorUtils.colorizeComponent(""),
                ColorUtils.colorizeComponent("&7Porównywanie ignoruje &filość, zużycie i UUID&7,"),
                ColorUtils.colorizeComponent("&7więc unikalne / uszkodzone itemy też są wykrywane.")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack simpleItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(ColorUtils.colorizeComponent(name));
        meta.lore(lore.stream().map(ColorUtils::colorizeComponent).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SearchGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
            || event.getAction() == InventoryAction.HOTBAR_SWAP
            || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD
            || event.getClick().isCreativeAction()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        SearchSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == PREVIOUS_SLOT && holder.page() > 0) {
            session.setPage(holder.page() - 1);
            openOrRefresh(player, session, false);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } else if (slot == NEXT_SLOT && holder.page() < holder.maxPage()) {
            session.setPage(holder.page() + 1);
            openOrRefresh(player, session, false);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SearchGuiHolder) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        searching.remove(event.getPlayer().getUniqueId());
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof SearchGuiHolder)) {
            return;
        }
        // Sesja zostaje do końca searchu — żeby refresh mógł znów otworzyć GUI gdy dociągnie wyniki.
        // Czyścimy dopiero po finish / quit.
        if (!searching.contains(player.getUniqueId())) {
            sessions.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof SearchGuiHolder) {
            event.setCancelled(true);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return new ArrayList<>();
    }

    private record SearchResult(UUID playerUuid, String playerName, String source, int amount, boolean online, String note, ItemStack displayItem) {}

    private final class SearchSession {
        private final UUID owner;
        private final ItemStack template;
        private final Map<String, SearchResult> byKey = new ConcurrentHashMap<>();
        private final AtomicBoolean searchingFlag = new AtomicBoolean(true);
        private volatile int page;

        private SearchSession(UUID owner, ItemStack template) {
            this.owner = owner;
            this.template = template;
        }

        private void addResult(UUID uuid, String name, String source, int amount, boolean online, String note, ItemStack display) {
            if (amount <= 0 || uuid == null) {
                return;
            }
            String key = uuid + "|" + source;
            byKey.merge(key, new SearchResult(uuid, name, source, amount, online, note, display.clone()),
                (old, neu) -> neu.amount() >= old.amount() ? neu : old);
        }

        private List<SearchResult> resultsView() {
            List<SearchResult> list = new ArrayList<>(byKey.values());
            list.sort(Comparator
                .comparingInt(SearchResult::amount).reversed()
                .thenComparing(SearchResult::playerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SearchResult::source));
            return list;
        }

        private ItemStack template() {
            return template;
        }

        private int page() {
            return page;
        }

        private void setPage(int page) {
            this.page = page;
        }

        private int maxPage() {
            int size = byKey.size();
            return Math.max(0, (int) Math.ceil(size / (double) ITEMS_PER_PAGE) - 1);
        }

        private boolean searching() {
            return searchingFlag.get() && searching.contains(owner);
        }

        private void markDone() {
            searchingFlag.set(false);
        }
    }

    private static final class SearchGuiHolder implements InventoryHolder {
        private final UUID owner;
        private final ItemStack template;
        private List<SearchResult> results;
        private int page;
        private int maxPage;
        private Inventory inventory;

        private SearchGuiHolder(UUID owner, ItemStack template, List<SearchResult> results, int page, int maxPage) {
            this.owner = owner;
            this.template = template;
            this.results = results;
            this.page = page;
            this.maxPage = maxPage;
        }

        private void update(List<SearchResult> results, int page, int maxPage) {
            this.results = results;
            this.page = page;
            this.maxPage = maxPage;
        }

        public UUID owner() {
            return owner;
        }

        public ItemStack template() {
            return template;
        }

        public List<SearchResult> results() {
            return results;
        }

        public int page() {
            return page;
        }

        public int maxPage() {
            return maxPage;
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
