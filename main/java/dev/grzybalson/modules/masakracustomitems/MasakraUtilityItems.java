package dev.grzybalson.modules.masakracustomitems;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;

/** The three utility abilities share the existing item IDs, cooldowns and region settings. */
final class MasakraUtilityItems implements Listener {
    static final String WEB = "usuwacz-pajeczyn";
    static final String BAG = "torba-misako";
    static final String LOTUS = "kwiat-lotosu";
    private static final Set<String> IDS = Set.of(WEB, BAG, LOTUS);
    private static final Set<Material> HAZARDS = Set.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
        Material.CACTUS, Material.MAGMA_BLOCK, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
        Material.WITHER_ROSE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE);
    private final HollyCore plugin;
    private final MasakraCustomItemsManager items;
    private final NamespacedKey bagKey;
    private final NamespacedKey itemKey;
    private final Map<UUID, BagMenu> openBags = new HashMap<>();
    private final Map<UUID, LotusReturn> returns = new HashMap<>();
    private final BukkitTask ticker;
    private MisakoBagStorage storage;

    MasakraUtilityItems(HollyCore plugin, MasakraCustomItemsManager items) {
        this.plugin = plugin;
        this.items = items;
        bagKey = new NamespacedKey(plugin, "masakra-bag-id");
        itemKey = new NamespacedKey(plugin, "masakra-item");
        try {
            storage = new MisakoBagStorage(HollyDataPaths.getAddonFolder(plugin, "masakra-customitems")
                .toPath().resolve("misako-bags.db"));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "[Masakra] Nie mozna otworzyc bazy toreb Misako. Torby zablokowane.", exception);
        }
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLotus, 2L, 2L);
    }

    UUID identifyBag(ItemStack stack) {
        if (!items.id(stack).equals(BAG) || stack.getAmount() != 1) return null;
        ItemMeta meta = stack.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(bagKey, PersistentDataType.STRING);
        if (stored != null) {
            try { return UUID.fromString(stored); }
            catch (IllegalArgumentException invalid) { return null; }
        }
        UUID id = UUID.randomUUID();
        meta.getPersistentDataContainer().set(bagKey, PersistentDataType.STRING, id.toString());
        stack.setItemMeta(meta);
        return id;
    }

    private UUID findBag(Player player, UUID required) {
        ItemStack[] slots = player.getInventory().getContents();
        for (int i = 0; i < slots.length; i++) {
            if (!items.id(slots[i]).equals(BAG)) continue;
            UUID id = identifyBag(slots[i]);
            if (id != null) player.getInventory().setItem(i, slots[i]);
            if (id != null && (required == null || required.equals(id))) return id;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        String id = items.id(event.getItem());
        if (!IDS.contains(id) || (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        if (event.useItemInHand() == Event.Result.DENY) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) use(event.getPlayer(), event.getItem(), id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        ItemStack item = event.getHand() == EquipmentSlot.HAND ? event.getPlayer().getInventory().getItemInMainHand()
            : event.getPlayer().getInventory().getItemInOffHand();
        String id = items.id(item);
        if (!IDS.contains(id)) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) use(event.getPlayer(), item, id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (IDS.contains(items.id(event.getItemInHand()))) event.setCancelled(true);
    }

    private void use(Player player, ItemStack item, String id) {
        if (id.equals(BAG)) { openBag(player, item); return; }
        if (id.equals(LOTUS)) { markLotus(player); return; }
        if (!items.start(player, WEB, null, false)) return;
        int radius = Math.max(1, Math.min(6, items.settings().getInt("items." + WEB + ".radius", 3)));
        Location origin = player.getLocation();
        World world = player.getWorld();
        int removed = 0;
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) {
            int by = origin.getBlockY() + y;
            if (by < world.getMinHeight() || by >= world.getMaxHeight()) continue;
            for (int z = -radius; z <= radius; z++) {
                if (x * x + y * y + z * z > radius * radius) continue;
                int bx = origin.getBlockX() + x, bz = origin.getBlockZ() + z;
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
                Block block = world.getBlockAt(bx, by, bz);
                if (block.getType() != Material.COBWEB || items.blocked(block.getLocation(), WEB, false)) continue;
                // Let WorldGuard and other block protections veto each individual cobweb.
                BlockBreakEvent breaking = new BlockBreakEvent(block, player);
                breaking.setDropItems(false);
                breaking.setExpToDrop(0);
                Bukkit.getPluginManager().callEvent(breaking);
                if (breaking.isCancelled() || block.getType() != Material.COBWEB) continue;
                block.setType(Material.AIR, false);
                world.spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.01);
                removed++;
            }
        }
        tell(player, "web-removed", "{COUNT}", String.valueOf(removed));
        items.playItemSound(player, WEB, "used");
    }

    private void openBag(Player player, ItemStack stack) {
        if (items.blocked(player.getLocation(), BAG, false)) { tell(player, "blocked-region"); return; }
        if (storage == null) { tell(player, "bag-error"); return; }
        UUID id = identifyBag(stack);
        if (id == null) { tell(player, "bag-invalid"); return; }
        BagMenu previous = openBags.get(id);
        if (previous != null && !previous.owner.equals(player.getUniqueId())) { tell(player, "bag-in-use"); return; }
        try {
            ItemStack[] contents = readBag(id);
            player.closeInventory();
            BagMenu menu = new BagMenu(id, player.getUniqueId());
            menu.inventory = Bukkit.createInventory(menu, MisakoBagItems.SIZE,
                ColorUtils.colorizeComponent(items.settings().getString("items." + BAG + ".gui-title", "&8Torba Misako")));
            menu.inventory.setContents(contents);
            openBags.put(id, menu);
            player.openInventory(menu.inventory);
            if (player.getOpenInventory().getTopInventory() != menu.inventory) openBags.remove(id, menu);
            else items.playItemSound(player, BAG, "used");
        } catch (Exception exception) { failure(player, exception); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBagClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BagMenu menu)) return;
        boolean alreadyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (alreadyCancelled || event instanceof InventoryCreativeEvent || !(event.getWhoClicked() instanceof Player player)) return;
        if (!menu.owner.equals(player.getUniqueId()) || openBags.get(menu.id) != menu
            || findBag(player, menu.id) == null || player.isDead()) return;
        if (event.getRawSlot() >= menu.inventory.getSize()) {
            // Ordinary placement/pickup below the GUI is safe. No shift-transfer, double-click,
            // number keys, drops or bag movement may involve the displayed storage snapshot.
            if ((event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)
                && event.getClickedInventory() == player.getInventory()
                && !items.id(event.getCurrentItem()).equals(BAG) && !items.id(event.getCursor()).equals(BAG)) {
                event.setCancelled(false);
            }
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || !(event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT
            || event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) return;
        try {
            // Re-read durable state for EVERY click; never restore a GUI snapshot on close.
            ItemStack[] contents = readBag(menu.id);
            ItemStack source = contents[slot];
            if (MisakoBagItems.empty(source)) { menu.inventory.setContents(contents); return; }
            ItemStack[] playerSlots = null;
            ItemStack cursor = player.getItemOnCursor();
            ItemStack grant;
            int count;
            if (event.isShiftClick()) {
                playerSlots = Arrays.stream(player.getInventory().getStorageContents())
                    .map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
                count = MisakoBagItems.insert(playerSlots, source);
                grant = null;
            } else {
                if (!MisakoBagItems.empty(cursor) && !cursor.isSimilar(source)) return;
                int free = source.getMaxStackSize() - (MisakoBagItems.empty(cursor) ? 0 : cursor.getAmount());
                count = Math.min(free, event.getClick() == ClickType.RIGHT ? (source.getAmount() + 1) / 2 : source.getAmount());
                grant = source.clone();
                grant.setAmount(count + (MisakoBagItems.empty(cursor) ? 0 : cursor.getAmount()));
            }
            if (count <= 0) { tell(player, "inventory-full"); return; }
            if (count == source.getAmount()) contents[slot] = null;
            else source.setAmount(source.getAmount() - count);
            writeBag(menu.id, contents); // Commit removal BEFORE exposing the transferred items.
            menu.inventory.setContents(contents);
            if (playerSlots != null) player.getInventory().setStorageContents(playerSlots);
            else player.setItemOnCursor(grant);
            savePlayer(player);
        } catch (Exception exception) { failure(player, exception); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBagDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BagMenu) event.setCancelled(true);
    }

    @EventHandler
    public void onBagClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BagMenu menu) openBags.remove(menu.id, menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof BagMenu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof BagMenu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        cancelPlayer(event.getEntity());
        Player killer = event.getEntity().getKiller();
        if (event.getKeepInventory() || killer == null || killer.equals(event.getEntity())) return;
        if (plugin.getCreatorModeManager() != null
            && plugin.getCreatorModeManager().shouldPreventDrops(event.getEntity(), killer)) return;
        if (!items.blocked(event.getEntity().getLocation(), BAG, false)) collectDrops(killer, event.getDrops());
    }

    void collectDrops(Player killer, List<ItemStack> drops) {
        if (storage == null || drops.isEmpty() || !items.settings().getBoolean("items." + BAG + ".enabled", true)
            || items.blocked(killer.getLocation(), BAG, false)) return;
        UUID id = findBag(killer, null);
        if (id == null) return;
        try {
            ItemStack[] contents = readBag(id);
            Map<Integer, Integer> accepted = new LinkedHashMap<>();
            int count = 0;
            for (int i = 0; i < drops.size(); i++) {
                ItemStack drop = drops.get(i);
                if (!eligible(drop)) continue;
                int moved = MisakoBagItems.insert(contents, drop);
                if (moved > 0) { accepted.put(i, moved); count += moved; }
            }
            if (count == 0) return; // Full bag: leave all remaining drops in the normal loot path.
            writeBag(id, contents);
            for (int i = drops.size() - 1; i >= 0; i--) {
                int moved = accepted.getOrDefault(i, 0);
                if (moved == 0) continue;
                ItemStack drop = drops.get(i);
                if (moved == drop.getAmount()) drops.remove(i);
                else { ItemStack remaining = drop.clone(); remaining.setAmount(drop.getAmount() - moved); drops.set(i, remaining); }
            }
            BagMenu menu = openBags.get(id);
            if (menu != null) menu.inventory.setContents(contents);
            tell(killer, "bag-stored", "{COUNT}", String.valueOf(count));
            items.playItemSound(killer, BAG, "received");
            savePlayer(killer);
        } catch (Exception exception) { failure(killer, exception); }
    }

    private boolean eligible(ItemStack stack) {
        if (MisakoBagItems.empty(stack) || !stack.hasItemMeta()) return false;
        // Disabled event abilities still have their original identity. Bags are always excluded.
        String eventId = stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (BAG.equals(eventId)) return false;
        if (eventId != null && items.settings().isConfigurationSection("items." + eventId)) return true;
        return NBTUtils.getString(stack, "holly_upgrade_type") != null
            && NBTUtils.getInt(stack, "holly_upgrade_level") != null;
    }

    private ItemStack[] readBag(UUID id) throws Exception {
        String encoded = storage.load(id);
        ItemStack[] contents = new ItemStack[MisakoBagItems.SIZE];
        if (encoded == null) return contents;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(encoded);
        if (yaml.getInt("version") != 1) throw new IllegalStateException("Unknown Misako bag data version");
        for (int i = 0; i < contents.length; i++) {
            Object value = yaml.get("slots." + i);
            if (value != null && !(value instanceof ItemStack)) throw new IllegalStateException("Invalid Misako bag slot " + i);
            if (value instanceof ItemStack stack) contents[i] = stack.clone();
        }
        return contents;
    }

    private void writeBag(UUID id, ItemStack[] contents) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        for (int i = 0; i < contents.length; i++) if (!MisakoBagItems.empty(contents[i])) yaml.set("slots." + i, contents[i]);
        storage.save(id, yaml.saveToString());
    }

    private void savePlayer(Player player) {
        player.saveData();
        if (plugin.getInventorySyncManager() != null) plugin.getInventorySyncManager().forceSavePlayer(player);
    }

    private void failure(Player player, Exception exception) {
        plugin.getLogger().log(Level.SEVERE, "[Masakra] Operacja torby Misako przerwana: " + player.getUniqueId(), exception);
        tell(player, "bag-error");
    }

    private void markLotus(Player player) {
        if (returns.containsKey(player.getUniqueId())) { tell(player, "lotus-active"); return; }
        Location destination = player.getLocation().clone();
        if (player.isInsideVehicle() || !safeDestination(destination)) { tell(player, "lotus-unsafe"); return; }
        if (!items.start(player, LOTUS, null, false)) return;
        long delay = Math.max(1, Math.min(120, items.settings().getLong("items." + LOTUS + ".return-seconds", 20))) * 1000L;
        returns.put(player.getUniqueId(), new LotusReturn(destination, System.currentTimeMillis() + delay));
        tell(player, "lotus-marked", "{TIME}", String.valueOf(delay / 1000));
        blossom(destination, false);
        items.playItemSound(player, LOTUS, "used");
    }

    private void tickLotus() {
        long now = System.currentTimeMillis();
        for (var entry : new ArrayList<>(returns.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            LotusReturn pending = entry.getValue();
            if (player == null || !player.isOnline() || player.isDead()) { returns.remove(entry.getKey()); continue; }
            if (player.getWorld() != pending.destination.getWorld()
                || !items.settings().getBoolean("items." + LOTUS + ".enabled", true)
                || items.blocked(player.getLocation(), LOTUS, false) || items.blocked(pending.destination, LOTUS, false)) {
                returns.remove(entry.getKey());
                tell(player, "lotus-cancelled");
                player.sendActionBar(net.kyori.adventure.text.Component.empty());
                continue;
            }
            long remaining = pending.until - now;
            if (remaining <= 0) {
                returns.remove(entry.getKey()); // At most one teleport, including re-entrant events.
                player.sendActionBar(net.kyori.adventure.text.Component.empty());
                if (player.isInsideVehicle() || !safeDestination(pending.destination)) { tell(player, "lotus-unsafe"); continue; }
                Location departure = player.getLocation().clone();
                if (!player.teleport(pending.destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                    tell(player, "lotus-cancelled"); continue;
                }
                player.setFallDistance(0);
                departure.getWorld().spawnParticle(Particle.PORTAL, departure.clone().add(0, 1, 0), 55, 0.4, 0.7, 0.4, 0.15);
                blossom(player.getLocation(), true);
                items.playItemSound(player, LOTUS, "received");
                tell(player, "lotus-returned");
                continue;
            }
            int seconds = (int) ((remaining + 999) / 1000);
            if (now - pending.lastActionBar >= 500) {
                player.sendActionBar(ColorUtils.colorizeComponent(items.settings().getString("messages.lotus-countdown",
                    "&dKwiat lotosu &8| &fPowrot za &d{TIME} s").replace("{TIME}", String.valueOf(seconds))));
                pending.lastActionBar = now;
            }
            if (seconds != pending.lastSecond) {
                pending.lastSecond = seconds;
                if (seconds <= 3) player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.2f + (3 - seconds) * 0.2f);
            }
            // Two shrinking, rising pink helices gather the player into the return portal.
            if (remaining <= 2000) {
                double progress = 1 - remaining / 2000.0;
                double angle = now / 90.0;
                double radius = 1.0 - progress * 0.7;
                Location center = player.getLocation();
                for (int i = 0; i < 10; i++) {
                    double a = angle + i * Math.PI / 5;
                    double height = ((i / 10.0 + progress) % 1) * 2.2;
                    Location point = center.clone().add(Math.cos(a) * radius, height, Math.sin(a) * radius);
                    player.getWorld().spawnParticle(Particle.REDSTONE, point, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 115, 205), 1.2f));
                    player.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private boolean safeDestination(Location location) {
        World world = location.getWorld();
        if (world == null || Bukkit.getWorld(world.getUID()) != world || !world.getWorldBorder().isInside(location)
            || location.getY() < world.getMinHeight() + 1 || location.getY() >= world.getMaxHeight() - 2) return false;
        Block feet = location.getBlock(), head = feet.getRelative(0, 1, 0), floor = feet.getRelative(0, -1, 0);
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid()
            && floor.getType().isSolid() && !HAZARDS.contains(feet.getType())
            && !HAZARDS.contains(head.getType()) && !HAZARDS.contains(floor.getType());
    }

    private void blossom(Location location, boolean arrival) {
        World world = location.getWorld();
        for (int i = 0; i < 72; i++) {
            double angle = i * Math.PI / 36;
            double radius = 0.8 + 0.45 * Math.cos(angle * 6);
            world.spawnParticle(Particle.REDSTONE, location.clone().add(Math.cos(angle) * radius, 0.15,
                Math.sin(angle) * radius), 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.fromRGB(255, 130, 220), 1.3f));
        }
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 0.8, 0), arrival ? 40 : 15, 0.4, 0.5, 0.4, 0.03);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { cancelPlayer(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        if (returns.containsKey(event.getPlayer().getUniqueId())) tell(event.getPlayer(), "lotus-cancelled");
        cancelPlayer(event.getPlayer());
    }

    private void cancelPlayer(Player player) {
        if (returns.remove(player.getUniqueId()) != null) player.sendActionBar(net.kyori.adventure.text.Component.empty());
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BagMenu) player.closeInventory();
    }

    void reset() {
        for (UUID id : new ArrayList<>(returns.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) { tell(player, "lotus-cancelled"); player.sendActionBar(net.kyori.adventure.text.Component.empty()); }
        }
        returns.clear();
        for (BagMenu menu : new ArrayList<>(openBags.values())) {
            Player player = Bukkit.getPlayer(menu.owner);
            if (player != null && player.getOpenInventory().getTopInventory() == menu.inventory) player.closeInventory();
        }
        openBags.clear();
    }

    void shutdown() {
        ticker.cancel();
        reset();
        HandlerList.unregisterAll(this);
        if (storage != null) {
            try { storage.close(); }
            catch (Exception exception) { plugin.getLogger().log(Level.SEVERE, "[Masakra] Blad zamykania bazy toreb", exception); }
        }
    }

    private void tell(Player player, String key, String... replacements) {
        String message = items.settings().getString("messages." + key, "&c" + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        player.sendMessage(ColorUtils.colorizeComponent(message));
    }

    private static final class BagMenu implements InventoryHolder {
        private final UUID id;
        private final UUID owner;
        private Inventory inventory;
        private BagMenu(UUID id, UUID owner) { this.id = id; this.owner = owner; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private static final class LotusReturn {
        private final Location destination;
        private final long until;
        private long lastActionBar;
        private int lastSecond = -1;
        private LotusReturn(Location destination, long until) { this.destination = destination.clone(); this.until = until; }
    }
}
