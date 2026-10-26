package dev.grzybalson.anarchiagg.adminitems.listener;

import dev.grzybalson.anarchiagg.util.ItemCompatibilityUtil;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsConfig;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI;
import dev.grzybalson.anarchiagg.adminitems.AdminItemsRegionUtil;

import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.utils.ColorUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KangarooBagListener implements Listener {

    private static final int NAV_PREV = 45;
    private static final int NAV_PAGE = 49;
    private static final int NAV_NEXT = 53;
    private static final String BAG_TITLE = "&#FFBF65&lWorek ninja";

    private final HollyCore plugin;
    private final AdminItemsConfig config;
    private final NamespacedKey bagUidKey;
    private final NamespacedKey navKey;
    private final KangarooBagStorage storage;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> openableBags = new HashMap<>();
    private boolean shutdown;

    public KangarooBagListener(HollyCore plugin) {
        this.plugin = plugin;
        this.config = plugin.getAdminItemsConfig();
        this.bagUidKey = new NamespacedKey(plugin, "kangaroo_bag_uid");
        this.navKey = new NamespacedKey(plugin, "kangaroo_bag_nav");
        this.storage = new KangarooBagStorage(plugin);
    }

    private Inventory openBagGui(Player player, UUID bagUid, int page) {
        int totalPages = Math.max(1, this.storage.pageCount(bagUid));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        KangarooBagHolder holder = new KangarooBagHolder(bagUid, safePage, totalPages);
        Inventory inventory = Bukkit.createInventory(
            holder,
            KangarooBagStorage.GUI_SIZE,
            ColorUtils.colorize(BAG_TITLE + " &8· &7strona &f" + (safePage + 1) + "&7/&f" + totalPages)
        );
        holder.setInventory(inventory);

        ItemStack[] pageItems = this.storage.getPage(bagUid, safePage);
        for (int i = 0; i < KangarooBagStorage.PAGE_CONTENT; i++) {
            inventory.setItem(i, pageItems[i]);
        }
        inventory.setItem(NAV_PREV, navItem(Material.ARROW, "&7« Poprzednia", "prev",
            List.of("&8× &7Strona &f" + (safePage + 1) + "&7/&f" + totalPages)));
        inventory.setItem(NAV_PAGE, navItem(Material.PAPER, "&fStrona &e" + (safePage + 1) + "&7/&e" + totalPages, "page",
            List.of("&8× &7Worek ninja — 5 rzędów + strony")));
        inventory.setItem(NAV_NEXT, navItem(Material.ARROW, "&7Następna »", "next",
            List.of("&8× &7Strona &f" + (safePage + 1) + "&7/&f" + totalPages)));

        this.openableBags.put(bagUid, inventory);
        if (player != null) {
            player.openInventory(inventory);
        }
        return inventory;
    }

    private ItemStack navItem(Material material, String name, String navId, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(ColorUtils.colorize(line));
            }
            meta.setLore(colored);
            meta.getPersistentDataContainer().set(this.navKey, PersistentDataType.STRING, navId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isNavItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(this.navKey, PersistentDataType.STRING);
    }

    private Inventory getBagInventory(UUID bagUid) {
        Inventory existing = this.openableBags.get(bagUid);
        if (existing != null && existing.getHolder() instanceof KangarooBagHolder) {
            return existing;
        }
        return this.openBagGui(null, bagUid, 0);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity();
        Player killer = killed.getKiller();
        if (killer == null) {
            return;
        }
        if (event.getKeepInventory() || (this.plugin.getCreatorModeManager() != null
            && this.plugin.getCreatorModeManager().shouldPreventDrops(killed, killer))) {
            return;
        }

        BagReference targetBag = this.findFirstBag(killer);
        if (targetBag == null) {
            return;
        }

        AdminItemsConfig.KangarooBagSettings settings = this.config != null ? this.config.getKangarooBag() : null;
        if (settings != null && !settings.isEnabled()) {
            return;
        }
        if (AdminItemsRegionUtil.isInBlockedRegion(killer, this.plugin)
                || AdminItemsRegionUtil.isInBlockedRegion(killed, this.plugin)) {
            return;
        }

        UUID killerUUID = killer.getUniqueId();
        long now = System.currentTimeMillis();
        Long cooldownEnd = this.cooldowns.get(killerUUID);
        if (cooldownEnd != null && cooldownEnd > now) {
            return;
        }
        if (this.hasGoldenSkull(killed)) {
            return;
        }

        if (!event.getKeepInventory() && this.plugin.getMasakraCustomItemsManager() != null) {
            this.plugin.getMasakraCustomItemsManager().collectMisakoDrops(killer, killed, event.getDrops());
        }
        List<ItemStack> drops = event.getDrops();
        if (drops == null || drops.isEmpty()) {
            return;
        }

        ArrayList<ItemStack> dropsCopy = new ArrayList<>();
        for (ItemStack item : drops) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.getType() == Material.TOTEM_OF_UNDYING || this.isKangarooBag(item)) {
                ItemCompatibilityUtil.dropItemNaturally(killed, item);
                continue;
            }
            dropsCopy.add(item.clone());
        }
        drops.clear();

        List<ItemStack> leftover = this.storage.deposit(targetBag.uid(), dropsCopy);
        boolean addedAny = leftover.size() < dropsCopy.size()
            || leftover.stream().mapToInt(i -> i == null ? 0 : i.getAmount()).sum()
                < dropsCopy.stream().mapToInt(i -> i == null ? 0 : i.getAmount()).sum();
        for (ItemStack left : leftover) {
            if (left != null && !left.getType().isAir()) {
                ItemCompatibilityUtil.dropItemNaturally(killed, left);
            }
        }

        Inventory open = this.openableBags.get(targetBag.uid());
        if (open != null && open.getHolder() instanceof KangarooBagHolder openHolder) {
            this.refreshPageContents(open, openHolder);
        }

        if (addedAny) {
            String storedMsg = settings != null ? settings.getMessageItemsStored()
                : "&#FFBF65&lWorek ninja &8:: &aPrzedmioty ofiary trafiły bezpośrednio do twojego worka!";
            killer.sendMessage(dev.grzybalson.utils.ColorUtils.colorize(storedMsg));
            killer.playSound(killer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            int cooldownSeconds = settings != null ? settings.getCooldown() : 5;
            if (cooldownSeconds > 0) {
                this.cooldowns.put(killerUUID, now + (long) cooldownSeconds * 1000L);
            }
        }
    }

    private void refreshPageContents(Inventory inventory, KangarooBagHolder holder) {
        ItemStack[] pageItems = this.storage.getPage(holder.uid(), holder.page());
        for (int i = 0; i < KangarooBagStorage.PAGE_CONTENT; i++) {
            inventory.setItem(i, pageItems[i]);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (!this.isKangarooBag(item)) {
            return;
        }

        event.setCancelled(true);
        UUID bagUid = this.ensureBagUid(item);
        if (bagUid == null) {
            return;
        }

        Player player = event.getPlayer();
        this.openBagGui(player, bagUid, 0);
        SoundsUtil.gui(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        KangarooBagHolder holder = this.getBagHolder(event);
        if (holder == null) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        boolean clickedTopInventory = event.getRawSlot() >= 0
                && event.getRawSlot() < event.getView().getTopInventory().getSize();

        if (clickedTopInventory && event.getRawSlot() >= KangarooBagStorage.PAGE_CONTENT) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            String nav = clickedItem != null && clickedItem.hasItemMeta()
                ? clickedItem.getItemMeta().getPersistentDataContainer().get(this.navKey, PersistentDataType.STRING)
                : null;
            if (nav == null) {
                return;
            }
            this.saveBag(holder.uid(), event.getView().getTopInventory(), holder.page());
            int target = holder.page();
            if ("prev".equals(nav)) {
                target = Math.max(0, holder.page() - 1);
            } else if ("next".equals(nav)) {
                target = holder.page() + 1;
                // pozwól otworzyć nową pustą stronę gdy bieżąca pełna
                int pages = Math.max(1, this.storage.pageCount(holder.uid()));
                if (target > pages) {
                    target = pages;
                }
                if (target >= pages) {
                    // wymuś istnienie kolejnej strony przez zapis pustej
                    this.storage.savePage(holder.uid(), target, new ItemStack[KangarooBagStorage.PAGE_CONTENT]);
                }
            } else {
                return;
            }
            this.openBagGui(player, holder.uid(), target);
            SoundsUtil.gui(player);
            return;
        }

        if (clickedTopInventory && this.isNavItem(cursorItem)) {
            event.setCancelled(true);
            return;
        }

        if (clickedTopInventory && this.isKangarooBag(cursorItem)) {
            event.setCancelled(true);
            return;
        }

        if (clickedTopInventory && event.getHotbarButton() >= 0) {
            ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (this.isKangarooBag(hotbarItem) || this.isNavItem(hotbarItem)) {
                event.setCancelled(true);
                return;
            }
        }

        if (!clickedTopInventory && this.isKangarooBag(clickedItem) && event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        this.scheduleSave(holder.uid(), event.getView().getTopInventory(), holder.page());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        KangarooBagHolder holder = this.getBagHolder(event);
        if (holder == null) {
            return;
        }

        if (this.isKangarooBag(event.getOldCursor()) || this.isNavItem(event.getOldCursor())) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= KangarooBagStorage.PAGE_CONTENT && rawSlot < KangarooBagStorage.GUI_SIZE) {
                event.setCancelled(true);
                return;
            }
        }

        this.scheduleSave(holder.uid(), event.getView().getTopInventory(), holder.page());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        if (!(inventory.getHolder() instanceof KangarooBagHolder holder)) {
            return;
        }

        this.saveBag(holder.uid(), inventory, holder.page());

        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (this.shutdown || !inventory.getViewers().isEmpty()) {
                return;
            }
            this.saveBag(holder.uid(), inventory, holder.page());
            dev.grzybalson.anarchiagg.util.MapCompatibilityUtil.remove(this.openableBags, holder.uid(), inventory);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (this.isKangarooBag(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;

        for (Map.Entry<UUID, Inventory> entry : this.openableBags.entrySet()) {
            Inventory inv = entry.getValue();
            int page = 0;
            if (inv.getHolder() instanceof KangarooBagHolder holder) {
                page = holder.page();
            }
            this.saveBag(entry.getKey(), inv, page);
        }
        this.openableBags.clear();
        this.cooldowns.clear();
        this.storage.shutdown();
    }

    private void scheduleSave(UUID uid, Inventory inventory, int page) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.shutdown) {
                this.saveBag(uid, inventory, page);
            }
        });
    }

    private void saveBag(UUID uid, Inventory inventory, int page) {
        if (uid == null || inventory == null || this.shutdown) {
            return;
        }
        ItemStack[] pageContents = new ItemStack[KangarooBagStorage.PAGE_CONTENT];
        for (int i = 0; i < KangarooBagStorage.PAGE_CONTENT; i++) {
            ItemStack item = inventory.getItem(i);
            pageContents[i] = (item == null || item.getType().isAir() || this.isNavItem(item)) ? null : item.clone();
        }
        this.storage.savePage(uid, page, pageContents);
    }

    private BagReference findFirstBag(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!this.isKangarooBag(item)) {
                continue;
            }

            UUID uid = this.ensureBagUid(item);
            if (uid != null) {
                player.getInventory().setItem(slot, item);
                return new BagReference(uid);
            }
        }
        return null;
    }

    private UUID ensureBagUid(ItemStack item) {
        if (!this.isKangarooBag(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String stored = meta.getPersistentDataContainer().get(this.bagUidKey, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            try {
                return UUID.fromString(stored);
            } catch (IllegalArgumentException ignored) {
            }
        }

        UUID uid = UUID.randomUUID();
        meta.getPersistentDataContainer().set(this.bagUidKey, PersistentDataType.STRING, uid.toString());
        item.setItemMeta(meta);
        return uid;
    }

    private boolean isKangarooBag(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        NamespacedKey oghubItemKey = new NamespacedKey(this.plugin, "oghub-custom-item-id");
        String oghubId = meta.getPersistentDataContainer().get(oghubItemKey, PersistentDataType.STRING);
        boolean oghubTorba = oghubId != null && oghubId.equalsIgnoreCase("torba");
        boolean taggedTorba = AdminItemsGUI.KEY_TORBA_KANGURA != null
            && meta.getPersistentDataContainer().has(AdminItemsGUI.KEY_TORBA_KANGURA, PersistentDataType.BYTE);
        if ((oghubTorba || taggedTorba) && item.getType() == Material.BROWN_DYE) {
            return true;
        }
        if (item.getType() != Material.ORANGE_SHULKER_BOX) {
            return false;
        }
        if (taggedTorba) {
            return true;
        }
        AdminItemsConfig.KangarooBagSettings settings = this.config != null ? this.config.getKangarooBag() : null;
        return settings != null
                && meta.hasCustomModelData()
                && meta.getCustomModelData() == settings.getCustomModelData();
    }

    private boolean hasGoldenSkull(Player player) {
        AdminItemsConfig.GoldenSkullSettings skullSettings = this.config != null ? this.config.getGoldenSkull() : null;
        if (skullSettings == null || !skullSettings.isEnabled()) {
            return false;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
                continue;
            }
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta.hasCustomModelData() && meta.getCustomModelData() == skullSettings.getCustomModelData()) {
                return true;
            }
        }
        return false;
    }

    private String getBagTitle() {
        return BAG_TITLE;
    }

    private KangarooBagHolder getBagHolder(InventoryInteractEvent event) {
        if (event.getView() == null) {
            return null;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        return holder instanceof KangarooBagHolder bagHolder ? bagHolder : null;
    }

    private record BagReference(UUID uid) {
    }

    private static final class KangarooBagHolder implements InventoryHolder {
        private final UUID uid;
        private final int page;
        private final int totalPages;
        private Inventory inventory;

        private KangarooBagHolder(UUID uid, int page, int totalPages) {
            this.uid = uid;
            this.page = page;
            this.totalPages = totalPages;
        }

        private UUID uid() {
            return this.uid;
        }

        private int page() {
            return this.page;
        }

        private int totalPages() {
            return this.totalPages;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }
}
