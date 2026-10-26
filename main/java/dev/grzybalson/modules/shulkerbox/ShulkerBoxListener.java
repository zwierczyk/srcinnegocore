package dev.grzybalson.modules.shulkerbox;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.util.SoundsUtil;
import dev.grzybalson.modules.combat.CombatManager;
import dev.grzybalson.modules.sectors.SectorService;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Portable shulker (PPM w ręce).
 * <p>
 * Ważne anty-dupe:
 * - NIGDY nie otwieramy żywego {@code BlockStateMeta#getBlockState().getInventory()} —
 *   tylko kopię w {@link Bukkit#createInventory}.
 * - Zapis z powrotem do itemu w slocie (nie „wiszącej” referencji).
 * - Przy błędzie NIE wrzucamy zawartości GUI do EQ (to był gotowy dupe).
 * - ItemSync / Unique muszą flushować albo pomijać gracza z otwartym shulkerem —
 *   inaczej snapshot trzyma pełny NBT + itemy już wyjęte do EQ.
 */
public final class ShulkerBoxListener implements Listener {

    private final HollyCore plugin;
    private final Map<UUID, ShulkerUser> shulkers = new HashMap<>();
    private final NamespacedKey key;

    public ShulkerBoxListener(HollyCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "shulker-modified");
    }

    public boolean isViewing(UUID uuid) {
        return uuid != null && shulkers.containsKey(uuid);
    }

    /**
     * Zapisuje otwarty portable shulker do itemu i zamyka GUI.
     * Wywoływane przed ItemSync save / quit / sektor, żeby snapshot nie złapał dupe'a.
     */
    public void flush(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        ShulkerUser user = shulkers.get(uuid);
        if (user == null) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof PortableShulkerHolder) {
            persist(player, user, top);
        }
        shulkers.remove(uuid);
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof PortableShulkerHolder) {
            player.closeInventory();
        }
    }

    public void shutdown() {
        for (UUID uuid : Map.copyOf(shulkers).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                flush(player);
            }
        }
        shulkers.clear();
    }

    /** Flush wszystkich otwartych portable shulkerów (np. przed restartem). */
    public void flushAll() {
        shutdown();
    }

    @EventHandler(ignoreCancelled = true)
    public void handle(PlayerInteractEntityEvent event) {
        if (!isViewing(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void handle(PlayerCommandPreprocessEvent event) {
        if (!isViewing(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void handle(PlayerInteractEvent event) {
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK) && !event.getAction().equals(Action.RIGHT_CLICK_AIR)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !Tag.SHULKER_BOXES.isTagged(item.getType())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        SectorService sectors = plugin.getSectorService();
        if (sectors != null && sectors.isTransferring(player.getUniqueId())) {
            return;
        }
        if (isViewing(player.getUniqueId())) {
            return;
        }
        CombatManager combatManager = plugin.getCombatManager();
        if (combatManager != null && combatManager.isInCombat(player)) {
            SoundsUtil.error(player);
            player.sendMessage(ColorUtils.colorizeComponent("&cNie możesz otwierać &4shulkerów &cpodczas &4walki!"));
            return;
        }
        if (!(item.getItemMeta() instanceof BlockStateMeta blockMeta)
            || !(blockMeta.getBlockState() instanceof ShulkerBox box)) {
            return;
        }

        // Walidacja przed otwarciem - sprawdź czy zawartość nie spowoduje problemów
        try {
            ItemStack[] contents = box.getInventory().getContents();
            if (contents != null) {
                // Sprawdź czy nie ma za dużo duplikatów tego samego itemu
                java.util.Map<org.bukkit.Material, Integer> itemCounts = new java.util.HashMap<>();
                int totalShulkers = 0;

                for (ItemStack stack : contents) {
                    if (stack == null || stack.getType().isAir()) {
                        continue;
                    }

                    // Zlicz shulkery wewnątrz
                    if (Tag.SHULKER_BOXES.isTagged(stack.getType())) {
                        totalShulkers++;
                        if (totalShulkers > 10) {
                            SoundsUtil.error(player);
                            player.sendMessage(ColorUtils.colorizeComponent("&cTen shulker zawiera zbyt wiele zagnieżdżonych shulkerów!"));
                            return;
                        }
                    }

                    // Zlicz duplikaty
                    itemCounts.merge(stack.getType(), stack.getAmount(), Integer::sum);
                }

                // Sprawdź czy nie ma podejrzanie dużej ilości jednego itemu
                for (Integer count : itemCounts.values()) {
                    if (count > 64 * 15) { // Więcej niż 15 stacków = podejrzane
                        plugin.getLogger().warning("[ShulkerBox] Gracz " + player.getName()
                            + " próbował otworzyć shulker z podejrzaną ilością itemów (" + count + ")");
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[ShulkerBox] Błąd walidacji shulkera dla " + player.getName() + ": " + ex.getMessage());
            SoundsUtil.error(player);
            player.sendMessage(ColorUtils.colorizeComponent("&cNie można otworzyć tego shulkera - zawartość jest uszkodzona!"));
            return;
        }

        int slot = event.getHand() == EquipmentSlot.OFF_HAND ? -1 : player.getInventory().getHeldItemSlot();

        PortableShulkerHolder holder = new PortableShulkerHolder(player.getUniqueId());
        Inventory view = Bukkit.createInventory(holder, InventoryType.SHULKER_BOX);
        holder.bind(view);
        view.setContents(cloneContents(box.getInventory().getContents()));
        // Najpierw otwarcie — openInventory zwraca null, gdy InventoryOpenEvent został anulowany
        // (np. blokada podczas restartu). Dopiero po sukcesie oznaczamy item i rejestrujemy gracza,
        // inaczej zostaje martwy stan "viewing" bez GUI (brak InventoryCloseEvent = nic go nie czyści).
        if (player.openInventory(view) == null) {
            return;
        }
        ItemMeta marked = item.getItemMeta();
        if (marked != null) {
            marked.getPersistentDataContainer().set(this.key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(marked);
        }
        this.shulkers.put(player.getUniqueId(), new ShulkerUser(item.getType(), slot));
        SoundsUtil.shulkerOpen(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void handle(PlayerDeathEvent event) {
        if (!isViewing(event.getEntity().getUniqueId())) {
            return;
        }
        flush(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handle(PlayerQuitEvent event) {
        flush(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void handle(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isViewing(player.getUniqueId())) {
            return;
        }
        ShulkerUser user = shulkers.get(player.getUniqueId());
        if (user == null) {
            return;
        }

        // Blokuj przenoszenie samego otwartego shulkera / nestowanie shulkerów.
        if ((event.getCurrentItem() != null && Tag.SHULKER_BOXES.isTagged(event.getCurrentItem().getType()))
            || (event.getCursor() != null && Tag.SHULKER_BOXES.isTagged(event.getCursor().getType()))) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == InventoryAction.HOTBAR_SWAP || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            ItemStack hotbarItem = (event.getHotbarButton() == -1)
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItem(event.getHotbarButton());
            if (hotbarItem != null && Tag.SHULKER_BOXES.isTagged(hotbarItem.getType())) {
                event.setCancelled(true);
                return;
            }
            if (event.getHotbarButton() == user.getSlot() || (user.getSlot() == -1 && event.getHotbarButton() == -1)) {
                event.setCancelled(true);
                return;
            }
        }
        // Klik w slot z otwartym shulkerem w dolnym EQ.
        if (event.getClickedInventory() instanceof PlayerInventory) {
            int raw = event.getSlot();
            if (user.getSlot() == -1) {
                // offhand w PlayerInventory ma slot 40 w niektórych mapowaniach; bezpieczniej po eq slot
                if (event.getSlot() == 40) {
                    event.setCancelled(true);
                }
            } else if (raw == user.getSlot()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void handle(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isViewing(player.getUniqueId())) {
            return;
        }
        if (event.getOldCursor() != null && Tag.SHULKER_BOXES.isTagged(event.getOldCursor().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void handle(PlayerSwapHandItemsEvent event) {
        if (!isViewing(event.getPlayer().getUniqueId())) {
            return;
        }
        // HIGH (nie MONITOR) — cancel musi zadziałać przed swapem, inaczej dupe.
        event.setCancelled(true);
        flush(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void handle(BlockPlaceEvent event) {
        if (!isViewing(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void handle(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player) || !isViewing(player.getUniqueId())) {
            return;
        }
        flush(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void handle(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isViewing(player.getUniqueId())) {
            return;
        }
        ItemStack item = event.getItemDrop().getItemStack();
        if (!Tag.SHULKER_BOXES.isTagged(item.getType())) {
            return;
        }
        // Nie pozwalaj wyrzucić otwartego shulkera — to klasyczny wektor dupe.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handle(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof PortableShulkerHolder)) {
            return;
        }
        HumanEntity humanEntity = event.getPlayer();
        if (!(humanEntity instanceof Player player)) {
            return;
        }
        ShulkerUser shulkerUser = this.shulkers.remove(player.getUniqueId());
        if (shulkerUser == null) {
            return;
        }
        persist(player, shulkerUser, inventory);
        SoundsUtil.shulkerClose(player);
        Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
    }

    private void persist(Player player, ShulkerUser user, Inventory inventory) {
        try {
            ResolvedShulker resolved = resolveOpenShulkerWithSlot(player, user);
            if (resolved == null || resolved.item() == null
                || !Tag.SHULKER_BOXES.isTagged(resolved.item().getType())) {
                // Item zniknął — nie dumpujemy GUI do EQ (dupe). Zawartość ginie z itemem.
                plugin.getLogger().warning("[ShulkerBox] Brak itemu shulkera przy zapisie dla " + player.getName()
                    + " — pominięto writeback (anty-dupe).");
                return;
            }
            ItemStack itemStack = resolved.item();
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (!(itemMeta instanceof BlockStateMeta meta)) {
                return;
            }
            meta.getPersistentDataContainer().remove(this.key);
            if (!(meta.getBlockState() instanceof ShulkerBox shulker)) {
                return;
            }
            shulker.getInventory().setContents(cloneContents(inventory.getContents()));
            meta.setBlockState((BlockState) shulker);
            itemStack.setItemMeta(meta);
            // Zawsze zapisuj w slocie gdzie item faktycznie jest — nie w starym user.slot
            // (po F-swap / przesunięciu stary slot = kopia → dupe).
            placeShulker(player, resolved.slot(), itemStack);
        } catch (Exception ex) {
            plugin.getLogger().warning("[ShulkerBox] Błąd zapisu portable shulkera dla "
                + player.getName() + ": " + ex.getMessage());
            ex.printStackTrace();
            // Świadomie BEZ addItem(inventory.getContents()) — to duplikowało całą zawartość.
        }
    }

    private record ResolvedShulker(ItemStack item, int slot) {
    }

    private ResolvedShulker resolveOpenShulkerWithSlot(Player player, ShulkerUser user) {
        PlayerInventory inv = player.getInventory();
        if (user.getSlot() == -1) {
            ItemStack off = inv.getItemInOffHand();
            if (off != null && Tag.SHULKER_BOXES.isTagged(off.getType()) && isMarked(off)) {
                return new ResolvedShulker(off, -1);
            }
            if (off != null && off.getType() == user.getType()) {
                return new ResolvedShulker(off, -1);
            }
            return null;
        }
        ItemStack inSlot = inv.getItem(user.getSlot());
        if (inSlot != null && Tag.SHULKER_BOXES.isTagged(inSlot.getType()) && isMarked(inSlot)) {
            return new ResolvedShulker(inSlot, user.getSlot());
        }
        if (inSlot != null && inSlot.getType() == user.getType()) {
            return new ResolvedShulker(inSlot, user.getSlot());
        }
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && Tag.SHULKER_BOXES.isTagged(stack.getType()) && isMarked(stack)) {
                return new ResolvedShulker(stack, i);
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && Tag.SHULKER_BOXES.isTagged(off.getType()) && isMarked(off)) {
            return new ResolvedShulker(off, -1);
        }
        return null;
    }

    private ItemStack resolveOpenShulker(Player player, ShulkerUser user) {
        ResolvedShulker resolved = resolveOpenShulkerWithSlot(player, user);
        return resolved != null ? resolved.item() : null;
    }

    private void placeShulker(Player player, int slot, ItemStack itemStack) {
        if (slot == -1) {
            player.getInventory().setItemInOffHand(itemStack);
        } else {
            player.getInventory().setItem(slot, itemStack);
        }
    }

    private boolean isMarked(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.key, PersistentDataType.BYTE);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        if (contents == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null || contents[i].getType().isAir() ? null : contents[i].clone();
        }
        return copy;
    }

    private static final class PortableShulkerHolder implements InventoryHolder {
        private final UUID viewer;
        private Inventory inventory;

        private PortableShulkerHolder(UUID viewer) {
            this.viewer = viewer;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @SuppressWarnings("unused")
        private UUID viewer() {
            return viewer;
        }
    }
}
