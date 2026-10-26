package dev.grzybalson.commands;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.util.InvseeUtil;
import dev.grzybalson.modules.itemsync.InventorySyncManager;
import dev.grzybalson.modules.itemsync.PlayerInventorySnapshot;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /invsee — online LIVE (wspólne GUI dla wielu adminów) + offline przez ItemSync.
 */
public class InvseeCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int GUI_SIZE = 45;

    private final HollyCore plugin;
    /** targetUUID → live sesja (jedno GUI dla wszystkich viewerów). */
    private final Map<UUID, LiveSession> liveByTarget = new ConcurrentHashMap<>();

    public InvseeCommand(HollyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerOnly()
            ));
            return true;
        }

        if (!player.hasPermission("Holly.cmd.invsee")) {
            player.sendMessage(ColorUtils.noPermission(label, "Holly.cmd.invsee"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ColorUtils.usage("/invsee <nick>"));
            return true;
        }

        String input = args[0].trim();
        Player online = Bukkit.getPlayerExact(input);
        if (online == null) {
            online = Bukkit.getPlayer(input);
        }
        if (online != null) {
            openOnlineLive(player, online);
            return true;
        }

        openOffline(player, input);
        return true;
    }

    private void openOnlineLive(Player viewer, Player target) {
        LiveSession session = liveByTarget.computeIfAbsent(target.getUniqueId(), id -> createLiveSession(target));
        session.viewers.add(viewer.getUniqueId());
        pullFromPlayer(session);
        viewer.openInventory(session.gui);
        int others = Math.max(0, session.viewers.size() - 1);
        viewer.sendMessage(ColorUtils.success(
            "Otwarto &aLIVE &aekwipunek &f" + target.getName()
                + (others > 0 ? " &7(&f" + (others + 1) + " &7osób w invsee)" : "")
                + "&a."
        ));
    }

    private LiveSession createLiveSession(Player target) {
        InvseeHolder holder = new InvseeHolder(target.getUniqueId(), target.getName(), false);
        Inventory gui = Bukkit.createInventory(
            holder,
            GUI_SIZE,
            ColorUtils.colorizeComponent("&8InvSee &aLIVE&8: &f" + target.getName())
        );
        LiveSession session = new LiveSession(target.getUniqueId(), target.getName(), gui, holder);
        // Backup tick — pickup/armor czasem omija eventy.
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.syncing || session.viewers.isEmpty()) {
                return;
            }
            Player online = Bukkit.getPlayer(session.targetId);
            if (online == null || !online.isOnline()) {
                return;
            }
            pullFromPlayer(session);
        }, 10L, 10L);
        return session;
    }

    private void pullFromPlayer(LiveSession session) {
        Player target = Bukkit.getPlayer(session.targetId);
        if (target == null || !target.isOnline() || session.syncing) {
            return;
        }
        session.syncing = true;
        try {
            InvseeUtil.copyToGUI(target, session.gui);
            decorateArmorRow(session.gui);
        } finally {
            session.syncing = false;
        }
    }

    private void pushToPlayer(LiveSession session) {
        Player target = Bukkit.getPlayer(session.targetId);
        if (target == null || !target.isOnline() || session.syncing) {
            return;
        }
        session.syncing = true;
        try {
            InvseeUtil.copyToPlayer(session.gui, target);
        } finally {
            session.syncing = false;
        }
    }

    private void schedulePush(LiveSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!liveByTarget.containsKey(session.targetId)) {
                return;
            }
            pushToPlayer(session);
            // Po pushu znormalizuj GUI z targeta (armor/sloty) — wszyscy viewerzy widzą to samo.
            pullFromPlayer(session);
        });
    }

    private void schedulePull(UUID targetId) {
        LiveSession session = liveByTarget.get(targetId);
        if (session == null || session.viewers.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> pullFromPlayer(session));
    }

    private void openOffline(Player viewer, String name) {
        InventorySyncManager sync = plugin.getInventorySyncManager();
        if (sync == null || !sync.isEnabled()) {
            viewer.sendMessage(ColorUtils.error("ItemSync jest wyłączony — nie da się otworzyć offline invsee."));
            return;
        }

        UUID uuid = resolveOfflineUuid(name);
        if (uuid == null) {
            viewer.sendMessage(ColorUtils.colorizeComponent(
                plugin.getConfigManager().getMessagesConfig().getPlayerNotFound()
            ));
            return;
        }

        String displayName = resolveOfflineName(uuid, name);
        viewer.sendMessage(ColorUtils.colorizeComponent(
            "&#00E0FFℹ &8Ξ &fŁaduję offline ekwipunek gracza &f" + displayName + "&7..."
        ));

        sync.loadSnapshot(uuid).thenAccept(snapshot -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (snapshot == null) {
                viewer.sendMessage(ColorUtils.error("Brak zapisanych danych ekwipunku dla &f" + displayName + "&c."));
                return;
            }

            Player onlineNow = Bukkit.getPlayer(uuid);
            if (onlineNow != null) {
                openOnlineLive(viewer, onlineNow);
                return;
            }

            Inventory gui = Bukkit.createInventory(
                new InvseeHolder(uuid, displayName, true),
                GUI_SIZE,
                ColorUtils.colorizeComponent("&8Offline InvSee: &f" + displayName)
            );
            InvseeUtil.copyToGUI(toInvseeArray(snapshot), gui);
            decorateArmorRow(gui);
            viewer.openInventory(gui);
            viewer.sendMessage(ColorUtils.success("Otwarto offline ekwipunek gracza &f" + displayName + "&a."));
        })).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) {
                    viewer.sendMessage(ColorUtils.error("Nie udało się wczytać offline EQ: &f" + throwable.getMessage()));
                }
            });
            return null;
        });
    }

    private void decorateArmorRow(Inventory gui) {
        ItemStack filler = glass();
        for (int slot = 41; slot < GUI_SIZE; slot++) {
            gui.setItem(slot, filler.clone());
        }
    }

    private ItemStack glass() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack[] toInvseeArray(PlayerInventorySnapshot snapshot) {
        ItemStack[] items = new ItemStack[41];
        ItemStack[] storage = snapshot.inventory();
        if (storage != null) {
            System.arraycopy(storage, 0, items, 0, Math.min(36, storage.length));
        }
        ItemStack[] armor = snapshot.armor();
        if (armor != null) {
            items[39] = armor.length > 0 ? armor[0] : null;
            items[38] = armor.length > 1 ? armor[1] : null;
            items[37] = armor.length > 2 ? armor[2] : null;
            items[36] = armor.length > 3 ? armor[3] : null;
        }
        ItemStack[] extra = snapshot.extra();
        items[40] = extra != null && extra.length > 0 ? extra[0] : null;
        return items;
    }

    private ItemStack[] storageFromGui(Inventory gui) {
        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack item = gui.getItem(i);
            storage[i] = item == null || item.getType().isAir() ? null : item.clone();
        }
        return storage;
    }

    private ItemStack[] armorFromGui(Inventory gui) {
        return new ItemStack[] {
            cloneOrNull(gui.getItem(39)),
            cloneOrNull(gui.getItem(38)),
            cloneOrNull(gui.getItem(37)),
            cloneOrNull(gui.getItem(36))
        };
    }

    private ItemStack[] extraFromGui(Inventory gui) {
        return new ItemStack[] { cloneOrNull(gui.getItem(40)) };
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvseeClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeHolder holder)) {
            return;
        }
        if (holder.offline()) {
            int raw = event.getRawSlot();
            if (raw >= 41 && raw < GUI_SIZE) {
                event.setCancelled(true);
            }
            return;
        }
        int raw = event.getRawSlot();
        if (raw >= 41 && raw < GUI_SIZE) {
            event.setCancelled(true);
            return;
        }
        LiveSession session = liveByTarget.get(holder.uuid());
        if (session == null) {
            return;
        }
        schedulePush(session);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvseeDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvseeHolder holder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot >= 41 && slot < GUI_SIZE) {
                event.setCancelled(true);
                return;
            }
        }
        if (holder.offline()) {
            return;
        }
        LiveSession session = liveByTarget.get(holder.uuid());
        if (session != null) {
            schedulePush(session);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof InvseeHolder holder)) {
            return;
        }

        Inventory gui = event.getInventory();
        if (!holder.offline()) {
            LiveSession session = liveByTarget.get(holder.uuid());
            if (session != null) {
                session.viewers.remove(viewer.getUniqueId());
                pushToPlayer(session);
                if (session.viewers.isEmpty()) {
                    endLiveSession(holder.uuid());
                }
            } else {
                Player online = Bukkit.getPlayer(holder.uuid());
                if (online != null && online.isOnline()) {
                    InvseeUtil.copyToPlayer(gui, online);
                }
            }
            return;
        }

        Player online = Bukkit.getPlayer(holder.uuid());
        if (online != null && online.isOnline()) {
            InvseeUtil.copyToPlayer(gui, online);
            return;
        }

        InventorySyncManager sync = plugin.getInventorySyncManager();
        if (sync == null || !sync.isEnabled()) {
            return;
        }

        ItemStack[] storage = storageFromGui(gui);
        ItemStack[] armor = armorFromGui(gui);
        ItemStack[] extra = extraFromGui(gui);

        sync.loadSnapshot(holder.uuid()).thenCompose(existing -> {
            PlayerInventorySnapshot base = existing;
            if (base == null) {
                base = emptySnapshot(holder.uuid(), holder.name());
            }
            PlayerInventorySnapshot updated = new PlayerInventorySnapshot(
                holder.uuid(),
                holder.name(),
                false,
                false,
                storage,
                armor,
                extra,
                base.enderChest(),
                base.potionEffects(),
                base.location(),
                base.health(),
                base.maxHealth(),
                base.foodLevel(),
                base.level(),
                base.exp(),
                base.totalExp(),
                base.heldSlot(),
                base.walkSpeed(),
                base.flySpeed(),
                base.allowFlight(),
                base.flying(),
                base.sprinting(),
                base.fireTicks(),
                base.saturation(),
                base.exhaustion(),
                base.op(),
                base.gameMode()
            );
            return sync.saveSnapshot(updated);
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[InvSee] Nie udalo sie zapisac offline EQ " + holder.name() + ": " + throwable.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof InvseeHolder) {
            return;
        }
        if (!liveByTarget.containsKey(player.getUniqueId())) {
            return;
        }
        schedulePull(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof InvseeHolder) {
            return;
        }
        if (!liveByTarget.containsKey(player.getUniqueId())) {
            return;
        }
        schedulePull(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!liveByTarget.containsKey(player.getUniqueId())) {
            return;
        }
        schedulePull(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetDrop(PlayerDropItemEvent event) {
        if (!liveByTarget.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        schedulePull(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetSwap(PlayerSwapHandItemsEvent event) {
        if (!liveByTarget.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        schedulePull(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Target wyszedl — zamknij live viewerom.
        LiveSession asTarget = liveByTarget.get(id);
        if (asTarget != null) {
            for (UUID viewerId : Set.copyOf(asTarget.viewers)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()
                    && viewer.getOpenInventory().getTopInventory().getHolder() instanceof InvseeHolder holder
                    && holder.uuid().equals(id)) {
                    viewer.closeInventory();
                    viewer.sendMessage(ColorUtils.error("Gracz &f" + asTarget.targetName + " &cwyszedł — zamknięto LIVE invsee."));
                }
            }
            endLiveSession(id);
        }
        // Viewer wyszedl — wypisz z sesji.
        for (LiveSession session : List.copyOf(liveByTarget.values())) {
            if (session.viewers.remove(id) && session.viewers.isEmpty()) {
                endLiveSession(session.targetId);
            }
        }
    }

    private void endLiveSession(UUID targetId) {
        LiveSession session = liveByTarget.remove(targetId);
        if (session == null) {
            return;
        }
        if (session.task != null) {
            session.task.cancel();
        }
        Player online = Bukkit.getPlayer(targetId);
        if (online != null && online.isOnline()) {
            session.syncing = false;
            pushToPlayer(session);
        }
    }

    private PlayerInventorySnapshot emptySnapshot(UUID uuid, String name) {
        return new PlayerInventorySnapshot(
            uuid, name, false, false,
            new ItemStack[36], new ItemStack[4], new ItemStack[1], new ItemStack[27],
            List.of(), null, 20.0, 20.0, 20, 0, 0f, 0, 0,
            0.2f, 0.1f, false, false, false, 0, 5f, 0f, false, org.bukkit.GameMode.SURVIVAL
        );
    }

    private UUID resolveOfflineUuid(String name) {
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null && offline.getUniqueId() != null && (offline.hasPlayedBefore() || offline.getName() != null)) {
            return offline.getUniqueId();
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(name)) {
                return player.getUniqueId();
            }
        }
        if (plugin.getJoinInfoManager() != null) {
            UUID uuid = plugin.getJoinInfoManager().getUuidByName(name);
            if (uuid != null) {
                return uuid;
            }
        }
        if (plugin.getTopManager() != null) {
            UUID uuid = plugin.getTopManager().getUuidByName(name);
            if (uuid != null) {
                return uuid;
            }
        }
        OfflinePlayer fallback = Bukkit.getOfflinePlayer(name);
        if (fallback.hasPlayedBefore()) {
            return fallback.getUniqueId();
        }
        return null;
    }

    private String resolveOfflineName(UUID uuid, String fallback) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        if (plugin.getJoinInfoManager() != null) {
            String stored = plugin.getJoinInfoManager().getPlayerName(uuid);
            if (stored != null && !stored.isBlank()) {
                return stored;
            }
        }
        return fallback;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("Holly.cmd.invsee")) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }
        return completions;
    }

    public record InvseeHolder(UUID uuid, String name, boolean offline) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class LiveSession {
        private final UUID targetId;
        private final String targetName;
        private final Inventory gui;
        private final InvseeHolder holder;
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        private volatile boolean syncing;
        private BukkitTask task;

        private LiveSession(UUID targetId, String targetName, Inventory gui, InvseeHolder holder) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.gui = gui;
            this.holder = holder;
        }
    }
}
