package dev.grzybalson.modules.unique;

import dev.grzybalson.HollyCore;
import dev.grzybalson.network.NatsService;
import dev.grzybalson.utils.ColorUtils;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port logiki Hypestar UniqueManager:
 * - UUID nadawane TYLKO skanem EQ (nie przy /ulepsz get / kit give)
 * - Holly.unique.bypass = hypestyar.uuid.disable (bez skanu / bez strip)
 * - duplikaty w EQ kasowane; cross-player przez NATS + broadcast do Holly.unique.view
 */
public final class UniqueItemService {

    public static final String BYPASS_PERMISSION = "Holly.unique.bypass";
    public static final String VIEW_PERMISSION = "Holly.unique.view";

    private static final int PLAYERS_PER_PASS = 8;
    private static final int DEDUPE_PLAYERS_PER_PASS = 6;
    private static final long REMOTE_REMOVE_MIN_INTERVAL_MS = 1000L;

    private final HollyCore plugin;
    private final NamespacedKey key;
    private final Set<Material> extraMaterials = EnumSet.noneOf(Material.class);
    private final Map<UUID, String> lastPublished = new HashMap<>();
    private final Map<UUID, Long> lastRemoteRemoveAt = new HashMap<>();
    private volatile List<Player> uniqueViewersCache = List.of();
    private volatile long uniqueViewersCacheAt;
    private BukkitTask applyTask;
    private BukkitTask dedupeTask;
    private int scanCursor;
    private int dedupeCursor;

    public UniqueItemService(HollyCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "uid");
    }

    public void start() {
        reloadMaterials();
        NatsService nats = plugin.getNatsService();
        if (nats != null && nats.isConnected()) {
            nats.subscribe(NatsService.UNIQUE_REMOVE, this::handleRemoteRemove);
        }
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                clearOwnerCaches(event.getPlayer().getUniqueId());
            }
        }, plugin);
        // ApplyIdScheduler ~ Hypestar (wolniej przy dużej populacji)
        applyTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanApplyUids, 200L, 200L);
        // DeleteDuplicatesScheduler ~ Hypestar (per-gracz w EQ)
        dedupeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanLocalDuplicates, 280L, 280L);
    }

    public void clearOwnerCaches(UUID owner) {
        if (owner == null) {
            return;
        }
        lastPublished.remove(owner);
        lastRemoteRemoveAt.remove(owner);
    }

    public void shutdown() {
        if (applyTask != null) {
            applyTask.cancel();
            applyTask = null;
        }
        if (dedupeTask != null) {
            dedupeTask.cancel();
            dedupeTask = null;
        }
    }

    public void reloadMaterials() {
        extraMaterials.clear();
        if (plugin.getSectorService() == null) {
            return;
        }
        for (String name : plugin.getSectorService().getConfig().getUniqueMaterials()) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                extraMaterials.add(material);
            }
        }
    }

    /**
     * CraftItemStack kopiuje ItemMeta przy KAZDYM wywolaniu, wiec hasItemMeta()+getItemMeta()
     * to dwie pelne kopie na item. Przy skanie EQ setek graczy bylo to widoczne w profilu.
     */
    public String getUid(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private String getUid(ItemMeta meta) {
        return meta == null ? null : meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** Jedna kopia ItemMeta zamiast osobnych applyUid()+getUid() (po dwie kopie kazda). */
    private String applyAndGetUid(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String existing = getUid(meta);
        if (existing != null) {
            return existing;
        }
        if (!shouldTag(item) || !meta.hasDisplayName()) {
            return null;
        }
        String uid = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, uid);
        item.setItemMeta(meta);
        return uid;
    }

    /**
     * Nadaje UUID tylko gdy brak.
     * Jezeli item juz ma UUID — NIGDY go nie zmieniamy.
     */
    public void applyUid(ItemStack item) {
        if (item == null || item.getType().isAir() || !shouldTag(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        // Jak Hypestar ApplyIdScheduler: bez display name nie tagujemy
        if (meta == null || !meta.hasDisplayName() || getUid(meta) != null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
    }

    /**
     * Usuwa UID z itemu (szablony kitow / itemupgrader).
     * NIE nadaje nowego — UUID pojawia sie dopiero gdy item lezy w EQ skanowanego gracza.
     */
    public void clearUid(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        clearShulkerUids(item);
        if (!item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return;
        }
        meta.getPersistentDataContainer().remove(key);
        item.setItemMeta(meta);
    }

    /** Przywraca istniejący UID (np. po /ulepsz) — bez losowania nowego. */
    public void setUid(ItemStack item, String uid) {
        if (item == null || item.getType().isAir() || uid == null || uid.isBlank()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, uid);
        item.setItemMeta(meta);
    }

    private void clearShulkerUids(ItemStack item) {
        if (!Tag.SHULKER_BOXES.isTagged(item.getType()) || !(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return;
        }
        if (!(meta.getBlockState() instanceof ShulkerBox box)) {
            return;
        }
        ItemStack[] inside = box.getInventory().getContents();
        boolean changed = false;
        for (ItemStack nested : inside) {
            if (nested == null || nested.getType().isAir()) {
                continue;
            }
            clearUid(nested);
            changed = true;
        }
        if (!changed) {
            return;
        }
        box.getInventory().setContents(inside);
        meta.setBlockState(box);
        item.setItemMeta(meta);
    }

    private boolean shouldTag(ItemStack item) {
        if (item.getMaxStackSize() <= 1) {
            return true;
        }
        if (extraMaterials.contains(item.getType())) {
            return true;
        }
        // /aitems — stackowalne też dostają UUID u gracza bez bypass (szablon GUI bez UID).
        return isAdminItem(item);
    }

    /** PDC admin_item_* z AdminItemsGUI — bez zależności od konkretnych Keys. */
    private boolean isAdminItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        for (NamespacedKey namespacedKey : meta.getPersistentDataContainer().getKeys()) {
            if (namespacedKey.getKey().startsWith("admin_item_")) {
                return true;
            }
        }
        return false;
    }

    private void scanApplyUids() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            scanCursor = 0;
            return;
        }
        if (scanCursor >= players.size()) {
            scanCursor = 0;
        }

        int scanned = 0;
        int online = players.size();
        int pass = online > 150 ? 1 : (online > 80 ? 2 : PLAYERS_PER_PASS);
        int enderEvery = online > 150 ? 16 : 8;
        while (scanned < pass && !players.isEmpty()) {
            if (scanCursor >= players.size()) {
                scanCursor = 0;
            }
            Player player = players.get(scanCursor++);
            scanned++;
            if (player == null || !player.isOnline() || player.hasPermission(BYPASS_PERMISSION)) {
                continue;
            }
            if (plugin.getEventManager() != null
                && plugin.getEventManager().getDungeonSessions() != null
                && plugin.getEventManager().getDungeonSessions().isBoundToDungeon(player.getUniqueId())) {
                continue;
            }
            // setItemMeta na shulkerze przy otwartym portable GUI = desync / dupe.
            if (plugin.getShulkerBoxListener() != null
                && plugin.getShulkerBoxListener().isViewing(player.getUniqueId())) {
                continue;
            }
            List<String> owned = new ArrayList<>();
            applyAndCollect(player.getInventory().getStorageContents(), owned);
            applyAndCollect(player.getInventory().getArmorContents(), owned);
            applyAndCollect(player.getInventory().getExtraContents(), owned);
            if ((scanCursor % enderEvery) == 0) {
                applyAndCollect(player.getEnderChest().getContents(), owned);
            }
            publishOwnedIfChanged(player.getUniqueId(), owned);
        }
    }

    private void applyAndCollect(ItemStack[] contents, List<String> owned) {
        if (contents == null) {
            return;
        }
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            // Stackowalne bez extraMaterials — nigdy nie tagujemy, pomijamy meta.
            if (!shouldTag(item)) {
                continue;
            }
            if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
                applyShulker(item, owned);
            }
            String uid = applyAndGetUid(item);
            if (uid != null) {
                owned.add(uid);
            }
        }
    }

    private void applyShulker(ItemStack item, List<String> owned) {
        if (!Tag.SHULKER_BOXES.isTagged(item.getType()) || !(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return;
        }
        if (!(meta.getBlockState() instanceof ShulkerBox box)) {
            return;
        }
        ItemStack[] inside = box.getInventory().getContents();
        applyAndCollect(inside, owned);
        box.getInventory().setContents(inside);
        meta.setBlockState(box);
        item.setItemMeta(meta);
    }

    /** Hypestar DeleteDuplicatesScheduler — tylko w ramach jednego gracza (batch). */
    private void scanLocalDuplicates() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            dedupeCursor = 0;
            return;
        }
        int scanned = 0;
        int online = players.size();
        int pass = online > 150 ? 1 : (online > 80 ? 2 : DEDUPE_PLAYERS_PER_PASS);
        int enderEvery = online > 150 ? 16 : 8;
        while (scanned < pass && !players.isEmpty()) {
            if (dedupeCursor >= players.size()) {
                dedupeCursor = 0;
            }
            Player player = players.get(dedupeCursor++);
            scanned++;
            if (player == null || !player.isOnline() || player.hasPermission(BYPASS_PERMISSION)) {
                continue;
            }
            if (plugin.getEventManager() != null
                && plugin.getEventManager().getDungeonSessions() != null
                && plugin.getEventManager().getDungeonSessions().isBoundToDungeon(player.getUniqueId())) {
                continue;
            }
            if (plugin.getShulkerBoxListener() != null
                && plugin.getShulkerBoxListener().isViewing(player.getUniqueId())) {
                continue;
            }
            removeDuplicatesInArray(player.getInventory().getStorageContents());
            removeDuplicatesInArray(player.getInventory().getArmorContents());
            removeDuplicatesInArray(player.getInventory().getExtraContents());
            if ((dedupeCursor % enderEvery) == 0) {
                removeDuplicatesInArray(player.getEnderChest().getContents());
            }
        }
    }

    private void removeDuplicatesInArray(ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (ItemStack stack : contents) {
            if (stack == null || !stack.hasItemMeta()) {
                continue;
            }
            String id = getUid(stack);
            if (id == null) {
                continue;
            }
            if (seen.add(id)) {
                continue;
            }
            // Stackowalne (PLAYER_HEAD / NETHER_STAR / TOTEM…): split stosu kopiuje PDC → ten sam UID.
            // Kasowanie = false-positive „losowe” zniknięcie. Nadaj nowy UID zamiast wipe.
            if (stack.getMaxStackSize() > 1) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    String fresh = UUID.randomUUID().toString();
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, fresh);
                    stack.setItemMeta(meta);
                    seen.add(fresh);
                }
                continue;
            }
            stack.setAmount(0);
        }
    }

    private void publishOwnedIfChanged(UUID owner, List<String> uids) {
        if (uids.isEmpty()) {
            lastPublished.remove(owner);
            return;
        }
        // Transfer sektora / dungeon: nie ogłaszaj ownershipu na chwilowym EQ (kit / clear).
        if (plugin.getSectorService() != null && plugin.getSectorService().isTransferring(owner)) {
            return;
        }
        if (plugin.getEventManager() != null
            && plugin.getEventManager().getDungeonSessions() != null
            && plugin.getEventManager().getDungeonSessions().isBoundToDungeon(owner)) {
            return;
        }
        NatsService nats = plugin.getNatsService();
        if (nats == null || !nats.isConnected()) {
            return;
        }
        // Sort = stabilny fingerprint przy przestawianiu slotów (mniej zbędnych NATS wipe).
        List<String> sorted = new ArrayList<>(uids);
        sorted.sort(String::compareTo);
        String fingerprint = String.join(",", sorted);
        if (fingerprint.equals(lastPublished.get(owner))) {
            return;
        }
        lastPublished.put(owner, fingerprint);
        nats.publish(NatsService.UNIQUE_REMOVE, new Document("owner", owner.toString()).append("uids", sorted));
    }

    private void handleRemoteRemove(Document document) {
        String ownerRaw = document.getString("owner");
        UUID owner;
        try {
            owner = UUID.fromString(ownerRaw);
        } catch (Exception exception) {
            return;
        }
        List<String> raw = document.getList("uids", String.class);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastRemoteRemoveAt.get(owner);
        if (last != null && now - last < REMOTE_REMOVE_MIN_INTERVAL_MS) {
            return;
        }
        lastRemoteRemoveAt.put(owner, now);
        removeDuplicatesForOwner(owner, new HashSet<>(raw));
    }

    /**
     * Hypestar UniqueManager.removeDuplicate — kasuje te UID u wszystkich oprocz ownera
     * (+ broadcast). Uzywane przy odbiorze backupu.
     */
    public void removeDuplicatesForOwner(UUID owner, Set<String> uids) {
        if (owner == null || uids == null || uids.isEmpty()) {
            return;
        }
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }
        Runnable task = () -> removeDuplicatesForOwnerSync(owner, uids, online);
        if (online.size() <= 40) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
            return;
        }
        // Duży online — rozłóż skan EQ na ticki (NATS nie może zabić TPS).
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer != null) {
                removeDuplicatesInArray(ownerPlayer.getInventory().getStorageContents());
                removeDuplicatesInArray(ownerPlayer.getInventory().getArmorContents());
                removeDuplicatesInArray(ownerPlayer.getInventory().getExtraContents());
            }
        });
        int perTick = Math.max(2, Math.min(4, online.size() / 60));
        for (int i = 0; i < online.size(); i += perTick) {
            int end = Math.min(i + perTick, online.size());
            List<Player> batch = online.subList(i, end);
            long delay = i / perTick;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player player : batch) {
                    if (!player.isOnline()) {
                        continue;
                    }
                    if (player.getUniqueId().equals(owner) || player.hasPermission(BYPASS_PERMISSION)) {
                        if (player.getUniqueId().equals(owner)) {
                            removeDuplicatesInArray(player.getInventory().getStorageContents());
                            removeDuplicatesInArray(player.getInventory().getArmorContents());
                            removeDuplicatesInArray(player.getInventory().getExtraContents());
                        }
                        continue;
                    }
                    boolean changed = scanAndRemovePlayerInventories(player, uids);
                    if (changed) {
                        player.updateInventory();
                    }
                }
            }, delay);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeDroppedItemsOnce(uids), (online.size() / perTick) + 1L);
    }

    private void removeDuplicatesForOwnerSync(UUID owner, Set<String> uids, List<Player> online) {
        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null) {
            removeDuplicatesInArray(ownerPlayer.getInventory().getStorageContents());
            removeDuplicatesInArray(ownerPlayer.getInventory().getArmorContents());
            removeDuplicatesInArray(ownerPlayer.getInventory().getExtraContents());
        }

        for (Player player : online) {
            if (player.getUniqueId().equals(owner) || player.hasPermission(BYPASS_PERMISSION)) {
                continue;
            }
            if (scanAndRemovePlayerInventories(player, uids)) {
                player.updateInventory();
            }
        }
        removeDroppedItemsOnce(uids);
    }

    private boolean scanAndRemovePlayerInventories(Player player, Set<String> uids) {
        boolean changed = false;
        changed |= broadcastAndRemove(player.getInventory().getStorageContents(), uids, "EKWIPUNEK", player.getName(), false);
        changed |= broadcastAndRemove(player.getInventory().getArmorContents(), uids, "ZBROJA", player.getName(), false);
        changed |= broadcastAndRemove(player.getInventory().getExtraContents(), uids, "EKWIPUNEK", player.getName(), false);
        changed |= broadcastAndRemove(player.getEnderChest().getContents(), uids, "ENDERCHEST", player.getName(), false);
        return changed;
    }

    private void removeDroppedItemsOnce(Set<String> uids) {
        java.util.Set<UUID> scannedWorlds = new java.util.HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            if (world == null || !scannedWorlds.add(world.getUID())) {
                continue;
            }
            for (Item drop : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = drop.getItemStack();
                String id = getUid(stack);
                if (id != null && uids.contains(id)) {
                    drop.remove();
                }
            }
        }
    }

    /** 1:1 z Hypestar UniqueManager.broadcastAndRemove */
    private boolean broadcastAndRemove(ItemStack[] contents, Set<String> uids, String loc, String playerName) {
        return broadcastAndRemove(contents, uids, loc, playerName, true);
    }

    private boolean broadcastAndRemove(ItemStack[] contents, Set<String> uids, String loc, String playerName, boolean notify) {
        if (contents == null) {
            return false;
        }
        boolean changed = false;
        for (ItemStack stack : contents) {
            if (stack == null || !stack.hasItemMeta()) {
                continue;
            }
            String id = getUid(stack);
            if (id == null || !uids.contains(id)) {
                continue;
            }
            if (notify) {
                String display = stack.getItemMeta().hasDisplayName()
                    ? stack.getItemMeta().getDisplayName()
                    : stack.getType().name();
                String plain = ChatColor.stripColor(display);
                String message = "&8[&4ITEMS-UUID&8] &cZabrano &4x" + stack.getAmount()
                    + " &c" + plain + " &cz &4" + loc + " &cgracza &4" + playerName;
                broadcastToViewers(message);
            }
            stack.setAmount(0);
            changed = true;
        }
        return changed;
    }

    private void broadcastToViewers(String message) {
        var component = ColorUtils.colorizeComponent(message);
        long now = System.currentTimeMillis();
        List<Player> viewers = uniqueViewersCache;
        if (now - uniqueViewersCacheAt > 30_000L) {
            List<Player> fresh = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission(VIEW_PERMISSION)) {
                    fresh.add(online);
                }
            }
            uniqueViewersCache = List.copyOf(fresh);
            uniqueViewersCacheAt = now;
            viewers = uniqueViewersCache;
        }
        for (Player online : viewers) {
            online.sendMessage(component);
        }
        plugin.getLogger().info(ChatColor.stripColor(ColorUtils.colorize(message)));
    }
}
