package dev.grzybalson.modules.mslf;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import dev.grzybalson.HollyCore;
import dev.grzybalson.network.NatsService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lekki enderchest MSLF gdy addon {@code enderchest} jest wyłączony:
 * powiększanie o 1 rząd (max 6), Mongo + sync NATS.
 */
public final class MslfEnderChestService implements Listener {

    public static final String NATS_SUBJECT = NatsService.MSLF_ENDERCHEST;
    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 6;
    private static final int VANILLA_SIZE = 27;

    private final HollyCore plugin;
    private final MongoCollection<Document> collection;
    private final String serverId;
    private final Map<UUID, Integer> rowsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> contentsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRemoteTs = new ConcurrentHashMap<>();

    public MslfEnderChestService(HollyCore plugin) {
        this.plugin = plugin;
        this.collection = plugin.getMongoManager().getCollection("mslf_enderchest");
        this.serverId = Bukkit.getServer().getName() + ":" + Bukkit.getPort();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        subscribeNats();
        Bukkit.getOnlinePlayers().forEach(p -> loadPlayer(p.getUniqueId(), p));
        plugin.getLogger().info("[MSLF] Lekki enderchest (Mongo+NATS) aktywny — addon enderchest wylaczony.");
    }

    public boolean expand(Player player) {
        UUID uuid = player.getUniqueId();
        int rows = rowsByPlayer.getOrDefault(uuid, MIN_ROWS);
        if (rows >= MAX_ROWS) {
            player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cEnderchest ma juz maksymalnie &f6 &crzedow."));
            return false;
        }
        rows++;
        rowsByPlayer.put(uuid, rows);
        ensureContentsSize(uuid, rows * 9);
        saveAndBroadcast(player, rows, contentsByPlayer.get(uuid));
        player.sendMessage(ColorUtils.colorizeComponent(
            "&#00FF3B✔ &8Ξ &#4EFA76Powiększono enderchest do &f" + rows + " &arzędów (&f" + (rows * 9) + " &aslotów)."
        ));
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1F, 1.3F);
        return true;
    }

    public int rowsOf(UUID uuid) {
        return rowsByPlayer.getOrDefault(uuid, MIN_ROWS);
    }

    /**
     * Pełna zawartość EC (do 54 slotów) — do snapshotów /cofka.
     * Scala open GUI + pierwsze 27 z vanilla.
     */
    public ItemStack[] captureContents(Player player) {
        if (player == null) {
            return new ItemStack[VANILLA_SIZE];
        }
        UUID uuid = player.getUniqueId();
        int rows = rowsByPlayer.getOrDefault(uuid, MIN_ROWS);
        ItemStack[] contents = contentsByPlayer.get(uuid);
        if (contents == null) {
            contents = normalize(player.getEnderChest().getContents(), rows * 9);
        } else {
            contents = normalize(contents, rows * 9);
            ItemStack[] vanilla = player.getEnderChest().getContents();
            for (int i = 0; i < VANILLA_SIZE; i++) {
                contents[i] = vanilla[i] == null ? null : vanilla[i].clone();
            }
        }
        if (player.getOpenInventory() != null
            && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
            && holder.owner.equals(uuid)) {
            ItemStack[] open = player.getOpenInventory().getTopInventory().getContents();
            contents = normalize(open, rows * 9);
        }
        contentsByPlayer.put(uuid, contents);
        return normalize(contents, rows * 9);
    }

    /** Przywróć pełny EC z snapshotu (po cofce). */
    public void restoreFromSnapshot(Player player, ItemStack[] contents) {
        if (player == null) {
            return;
        }
        if (contents == null) {
            // Brak danych EC w snapshocie != pusty EC — nie nadpisuj tego co gracz ma zapisane.
            plugin.getLogger().warning("[MSLF-EC] restoreFromSnapshot: brak danych EC dla " + player.getName()
                + " — pomijam (EC bez zmian).");
            return;
        }
        int rows = MIN_ROWS;
        if (contents.length > 0) {
            rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, (int) Math.ceil(contents.length / 9.0)));
        }
        saveAndBroadcast(player, rows, contents);
    }

    /** Po restore kolekcji Mongo — przeładuj wszystkich online. */
    public void reloadAllOnlineFromMongo() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            loadPlayer(player.getUniqueId(), player);
        }
    }

    private void subscribeNats() {
        NatsService nats = plugin.getNatsService();
        if (nats == null || !nats.isConnected()) {
            return;
        }
        nats.subscribe(NATS_SUBJECT, this::handleRemote);
    }

    private void handleRemote(Document doc) {
        if (doc == null) {
            return;
        }
        String from = doc.getString("server");
        if (serverId.equals(from)) {
            return;
        }
        String uuidRaw = doc.getString("uuid");
        if (uuidRaw == null || uuidRaw.isBlank()) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidRaw);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        long ts = doc.getLong("ts") == null ? 0L : doc.getLong("ts");
        Long prev = lastRemoteTs.get(uuid);
        if (prev != null && prev >= ts) {
            return;
        }
        lastRemoteTs.put(uuid, ts);
        int rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, doc.getInteger("rows", MIN_ROWS)));
        ItemStack[] contents = ItemSerializer.deserializeItems(doc.getString("contents"));
        rowsByPlayer.put(uuid, rows);
        contentsByPlayer.put(uuid, normalize(contents, rows * 9));

        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            applyVanillaSlice(online, contentsByPlayer.get(uuid));
        }
    }

    private void loadPlayer(UUID uuid, Player player) {
        plugin.getMongoManager().subscribe(
            collection.find(Filters.eq("_id", uuid.toString())).first(),
            doc -> Bukkit.getScheduler().runTask(plugin, () -> applyLoaded(uuid, player, doc)),
            err -> plugin.getLogger().warning("[MSLF-EC] Load fail " + uuid + ": " + err.getMessage())
        );
    }

    private void applyLoaded(UUID uuid, Player player, Document doc) {
        int rows = MIN_ROWS;
        ItemStack[] contents;
        if (doc != null) {
            rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, doc.getInteger("rows", MIN_ROWS)));
            contents = ItemSerializer.deserializeItems(doc.getString("contents"));
            Long ts = doc.getLong("updatedAt");
            if (ts != null) {
                lastRemoteTs.put(uuid, ts);
            }
        } else {
            contents = player != null ? player.getEnderChest().getContents().clone() : new ItemStack[VANILLA_SIZE];
        }
        rowsByPlayer.put(uuid, rows);
        ItemStack[] normalized = normalize(contents, rows * 9);
        if (player != null) {
            // merge vanilla if mongo empty on first slots
            ItemStack[] vanilla = player.getEnderChest().getContents();
            for (int i = 0; i < Math.min(VANILLA_SIZE, normalized.length); i++) {
                if (normalized[i] == null && vanilla[i] != null) {
                    normalized[i] = vanilla[i].clone();
                }
            }
            applyVanillaSlice(player, normalized);
        }
        contentsByPlayer.put(uuid, normalized);
    }

    private void applyVanillaSlice(Player player, ItemStack[] contents) {
        Inventory ec = player.getEnderChest();
        for (int i = 0; i < VANILLA_SIZE; i++) {
            ItemStack item = contents != null && i < contents.length ? contents[i] : null;
            ec.setItem(i, item == null ? null : item.clone());
        }
    }

    private void ensureContentsSize(UUID uuid, int size) {
        ItemStack[] current = contentsByPlayer.get(uuid);
        contentsByPlayer.put(uuid, normalize(current, size));
    }

    private ItemStack[] normalize(ItemStack[] source, int size) {
        ItemStack[] out = new ItemStack[size];
        if (source == null) {
            return out;
        }
        for (int i = 0; i < Math.min(size, source.length); i++) {
            out[i] = source[i] == null ? null : source[i].clone();
        }
        return out;
    }

    private void saveAndBroadcast(Player player, int rows, ItemStack[] contents) {
        UUID uuid = player.getUniqueId();
        ItemStack[] normalized = normalize(contents, rows * 9);
        contentsByPlayer.put(uuid, normalized);
        rowsByPlayer.put(uuid, rows);
        applyVanillaSlice(player, normalized);

        long ts = System.currentTimeMillis();
        String serialized = ItemSerializer.serializeItems(normalized);
        Document doc = new Document("_id", uuid.toString())
            .append("name", player.getName())
            .append("rows", rows)
            .append("contents", serialized)
            .append("updatedAt", ts);
        plugin.getMongoManager().subscribe(
            collection.replaceOne(Filters.eq("_id", uuid.toString()), doc, new ReplaceOptions().upsert(true)),
            ignored -> {
            },
            err -> plugin.getLogger().warning("[MSLF-EC] Save fail " + player.getName() + ": " + err.getMessage())
        );

        NatsService nats = plugin.getNatsService();
        if (nats != null && nats.isConnected()) {
            nats.publish(NATS_SUBJECT, new Document()
                .append("uuid", uuid.toString())
                .append("rows", rows)
                .append("contents", serialized)
                .append("server", serverId)
                .append("ts", ts));
        }
    }

    public void openCustom(Player player) {
        // Enderchest w walce jest zablokowany tak samo jak vanillowy (CombatListener).
        if (plugin.getCombatManager() != null && plugin.getCombatManager().blockEnderchest(player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int rows = rowsByPlayer.getOrDefault(uuid, MIN_ROWS);
        ItemStack[] contents = contentsByPlayer.computeIfAbsent(uuid, id -> {
            ItemStack[] vanilla = player.getEnderChest().getContents();
            return normalize(vanilla, rows * 9);
        });
        contents = normalize(contents, rows * 9);
        contentsByPlayer.put(uuid, contents);

        Holder holder = new Holder(uuid);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
            ColorUtils.colorizeComponent("&5EnderChest &7(" + rows + " rz.)"));
        holder.inventory = inv;
        for (int i = 0; i < contents.length; i++) {
            inv.setItem(i, contents[i] == null ? null : contents[i].clone());
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1F, 1F);
    }

    /** Wymuś zapis pełnego EC przed transferem sektora (zanim ktoś wyczyści vanilla). */
    public void saveForSectorTransfer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        ItemStack[] contents = captureContents(player);
        int rows = rowsByPlayer.getOrDefault(player.getUniqueId(), Math.max(MIN_ROWS, contents.length / 9));
        saveAndBroadcast(player, rows, contents);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer().getUniqueId(), event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        int rows = rowsByPlayer.getOrDefault(uuid, MIN_ROWS);
        ItemStack[] contents = contentsByPlayer.get(uuid);
        ItemStack[] vanilla = player.getEnderChest().getContents();
        boolean vanillaEmpty = isEmpty(vanilla);

        if (contents == null) {
            contents = normalize(vanilla, rows * 9);
        } else {
            contents = normalize(contents, rows * 9);
            // Nie nadpisuj cache pustym vanilla (np. po clear przed transferem sektora).
            if (!vanillaEmpty) {
                for (int i = 0; i < VANILLA_SIZE; i++) {
                    contents[i] = vanilla[i] == null ? null : vanilla[i].clone();
                }
            }
        }
        if (player.getOpenInventory() != null
            && player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
            && holder.owner.equals(uuid)) {
            contents = normalize(player.getOpenInventory().getTopInventory().getContents(), rows * 9);
        }
        saveAndBroadcast(player, rows, contents);
        rowsByPlayer.remove(uuid);
        contentsByPlayer.remove(uuid);
    }

    private static boolean isEmpty(ItemStack[] items) {
        if (items == null) {
            return true;
        }
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractChest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) {
            return;
        }
        event.setCancelled(true);
        openCustom(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpenVanilla(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }
        if (event.getInventory().getHolder() instanceof Holder) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> openCustom(player));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (!holder.owner.equals(player.getUniqueId())) {
            return;
        }
        int rows = rowsByPlayer.getOrDefault(player.getUniqueId(), event.getInventory().getSize() / 9);
        ItemStack[] contents = normalize(event.getInventory().getContents(), rows * 9);
        saveAndBroadcast(player, rows, contents);
    }

    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
