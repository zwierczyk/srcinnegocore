package dev.grzybalson.anarchiagg.szafa.kostiumy;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KostiumyArmorHandler implements Listener {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
    private static final long MOVE_REFRESH_COOLDOWN_MILLIS = 3000L;
    private static final long DAMAGE_ARMOR_RESTORE_DELAY_TICKS = 10L;
    private static final Attribute[] ALL_ATTRIBUTES = Attribute.values();
    private static KostiumyArmorHandler instance;

    private final JavaPlugin plugin;
    private final KostiumyHandler kostiumyHandler;
    private final Map<UUID, String> currentKostiumCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMoveRefresh = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingDamageRestoreTasks = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> visualArmorCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> temporarilyRemovedArmor = ConcurrentHashMap.newKeySet();

    public KostiumyArmorHandler(JavaPlugin plugin, KostiumyHandler kostiumyHandler) {
        this.plugin = plugin;
        this.kostiumyHandler = kostiumyHandler;
        instance = this;
        startArmorRefreshTask();
    }

    public static KostiumyArmorHandler getInstance() {
        return instance;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!currentKostiumCache.containsKey(uuid)) return;

        long now = System.currentTimeMillis();
        Long last = lastMoveRefresh.get(uuid);
        if (last != null && now - last < MOVE_REFRESH_COOLDOWN_MILLIS) return;
        lastMoveRefresh.put(uuid, now);
        markDirty(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        markDirty(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (event.getPlayer().isOnline()) {
                markDirty(event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        currentKostiumCache.remove(uuid);
        lastMoveRefresh.remove(uuid);
        dirtyPlayers.remove(uuid);
        cancelDamageRestore(uuid);
    }

    @EventHandler
    public void onPlayerChangeToCreative(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.CREATIVE) {
            currentKostiumCache.remove(event.getPlayer().getUniqueId());
            removeArmor(event.getPlayer());
        } else {
            markDirty(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        String activeKostium = currentKostiumCache.get(uuid);
        if (activeKostium == null) return;
        if (event.getFinalDamage() <= 0.0D || player.isDead()) return;
        if (temporarilyRemovedArmor.add(uuid)) {
            sendRealArmor(player);
        }
        scheduleDamageRestore(player, activeKostium);
    }

    private void startArmorRefreshTask() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (dirtyPlayers.isEmpty()) return;
            List<UUID> uuids = new ArrayList<>(dirtyPlayers);
            dirtyPlayers.clear();
            for (UUID uuid : uuids) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    currentKostiumCache.remove(uuid);
                    lastMoveRefresh.remove(uuid);
                    cancelDamageRestore(uuid);
                    continue;
                }
                processPlayer(player);
            }
        }, 20L, 20L);
    }

    private void processPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (player.getGameMode() == GameMode.CREATIVE) {
            if (currentKostiumCache.remove(uuid) != null) removeArmor(player);
            return;
        }
        String activeKostium = this.kostiumyHandler.getDatabase().getActiveKostium(uuid);
        if (activeKostium == null || activeKostium.isBlank()) {
            if (currentKostiumCache.remove(uuid) != null) removeArmor(player);
            return;
        }
        String expiration = this.kostiumyHandler.getDatabase().getKostiumExpiration(uuid);
        if (expiration != null && !expiration.isBlank()) {
            try {
                LocalDateTime expirationTime = LocalDateTime.parse(expiration.trim(), DATE_FORMATTER);
                if (LocalDateTime.now().isAfter(expirationTime)) {
                    this.kostiumyHandler.getDatabase().removeActiveKostium(uuid);
                    currentKostiumCache.remove(uuid);
                    removeArmor(player);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        currentKostiumCache.put(uuid, activeKostium);
        // Zawsze odśwież przy dirty — po async load DB poprzedni null/brak paczki = niewidoczny kostium.
        refreshArmor(player, activeKostium);
    }

    public void refreshArmor(Player player, String kostiumName) {
        KostiumyHandler.KostiumData kostiumData = this.kostiumyHandler.getKostiumy().get(kostiumName);
        if (kostiumData == null || PacketEvents.getAPI() == null) return;
        this.temporarilyRemovedArmor.remove(player.getUniqueId());
        boolean hasElytra = player.getInventory().getChestplate() != null && player.getInventory().getChestplate().getType() == Material.ELYTRA;
        ItemStack helmet = getVisualArmorItem(kostiumName, kostiumData, "helmet");
        ItemStack chest = hasElytra
                ? createPacketVisualItem(player.getInventory().getChestplate(), null, false)
                : getVisualArmorItem(kostiumName, kostiumData, "chestplate");
        ItemStack legs = getVisualArmorItem(kostiumName, kostiumData, "leggings");
        ItemStack boots = getVisualArmorItem(kostiumName, kostiumData, "boots");
        sendEquipmentToViewers(player, helmet, chest, legs, boots, false);
    }

    public void refreshPlayer(Player player) {
        if (player != null && player.isOnline()) {
            markDirty(player);
        }
    }

    public void removeArmor(Player player) {
        UUID uuid = player.getUniqueId();
        currentKostiumCache.remove(uuid);
        lastMoveRefresh.remove(uuid);
        dirtyPlayers.remove(uuid);
        cancelDamageRestore(uuid);
        sendRealArmor(player);
    }

    private void sendRealArmor(Player player) {
        if (PacketEvents.getAPI() == null) return;
        ItemStack helmet = createPacketVisualItem(player.getInventory().getHelmet(), null, false);
        ItemStack chest = createPacketVisualItem(player.getInventory().getChestplate(), null, false);
        ItemStack legs = createPacketVisualItem(player.getInventory().getLeggings(), null, false);
        ItemStack boots = createPacketVisualItem(player.getInventory().getBoots(), null, false);
        sendEquipmentToViewers(player, helmet, chest, legs, boots, true);
    }

    private void sendEquipmentToViewers(Player player, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots, boolean restoreReal) {
        List<Equipment> equipment = new ArrayList<>(4);
        equipment.add(new Equipment(EquipmentSlot.HELMET, SpigotConversionUtil.fromBukkitItemStack(orAir(helmet))));
        equipment.add(new Equipment(EquipmentSlot.CHEST_PLATE, SpigotConversionUtil.fromBukkitItemStack(orAir(chest))));
        equipment.add(new Equipment(EquipmentSlot.LEGGINGS, SpigotConversionUtil.fromBukkitItemStack(orAir(legs))));
        equipment.add(new Equipment(EquipmentSlot.BOOTS, SpigotConversionUtil.fromBukkitItemStack(orAir(boots))));
        WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(player.getEntityId(), equipment);
        for (Player viewer : getNearbyViewers(player)) {
            try {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
            } catch (Exception e) {
                this.plugin.getLogger().warning("Blad wysylania pakietu armor: " + e.getMessage());
            }
        }
        sendSelfVisual(player, helmet, chest, legs, boots, restoreReal);
    }

    private void sendSelfVisual(Player player, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots, boolean restoreReal) {
        try {
            Map<org.bukkit.inventory.EquipmentSlot, ItemStack> map = new EnumMap<>(org.bukkit.inventory.EquipmentSlot.class);
            map.put(org.bukkit.inventory.EquipmentSlot.HEAD, orAir(helmet));
            map.put(org.bukkit.inventory.EquipmentSlot.CHEST, orAir(chest));
            map.put(org.bukkit.inventory.EquipmentSlot.LEGS, orAir(legs));
            map.put(org.bukkit.inventory.EquipmentSlot.FEET, orAir(boots));
            player.sendEquipmentChange(player, map);
        } catch (Throwable exception) {
            if (!restoreReal) {
                this.plugin.getLogger().warning("Blad wysylania kostiumu do wlasciciela: " + exception.getMessage());
            }
        }
    }

    private static ItemStack orAir(ItemStack item) {
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private void scheduleDamageRestore(Player player, String activeKostium) {
        UUID uuid = player.getUniqueId();
        BukkitTask previousTask = this.pendingDamageRestoreTasks.remove(uuid);
        if (previousTask != null) {
            previousTask.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            this.pendingDamageRestoreTasks.remove(uuid);
            this.temporarilyRemovedArmor.remove(uuid);
            if (!player.isOnline() || player.getGameMode() == GameMode.CREATIVE) {
                return;
            }

            String current = this.currentKostiumCache.get(uuid);
            if (activeKostium.equals(current)) {
                refreshArmor(player, activeKostium);
            } else {
                markDirty(player);
            }
        }, DAMAGE_ARMOR_RESTORE_DELAY_TICKS);
        this.pendingDamageRestoreTasks.put(uuid, task);
    }

    private void cancelDamageRestore(UUID uuid) {
        BukkitTask task = this.pendingDamageRestoreTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        this.temporarilyRemovedArmor.remove(uuid);
    }

    private List<Player> getNearbyViewers(Player target) {
        // Sending equipment packets to every online player is an easy way to saturate network bandwidth.
        // Limit to viewers that are likely to actually track the entity client-side.
        if (target == null || !target.isOnline()) {
            return List.of();
        }

        int viewDistanceChunks = Math.max(2, Bukkit.getViewDistance());
        // Add a small buffer to avoid "popping" at chunk border.
        int maxRangeBlocks = (viewDistanceChunks * 16) + 16;
        // Hard cap: view-distance based ranges can be huge and still create packet storms on busy hubs.
        maxRangeBlocks = Math.min(maxRangeBlocks, 128);
        double maxDistSq = maxRangeBlocks * (double) maxRangeBlocks;

        List<Player> viewers = new ArrayList<>();
        var targetLoc = target.getLocation();
        var targetWorld = target.getWorld();

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer == null || !viewer.isOnline()) continue;
            if (viewer == target) continue;
            if (viewer.getWorld() != targetWorld) continue;
            if (!viewer.canSee(target)) continue;
            if (viewer.getLocation().distanceSquared(targetLoc) > maxDistSq) continue;
            viewers.add(viewer);
        }
        return viewers;
    }

    private void markDirty(Player player) {
        this.dirtyPlayers.add(player.getUniqueId());
    }

    private ItemStack getVisualArmorItem(String kostiumName, KostiumyHandler.KostiumData kostiumData, String slotKey) {
        if (kostiumData == null) {
            return new ItemStack(Material.AIR);
        }

        String itemKey = kostiumData.armor().get(slotKey);
        if (itemKey == null) {
            return new ItemStack(Material.AIR);
        }

        String color = KostiumyHandler.getEffectiveColor(kostiumName, kostiumData.displayName(), kostiumData.color());
        String cacheKey = kostiumName + '\u0000' + slotKey + '\u0000' + itemKey + '\u0000' + (color == null ? "" : color);
        return this.visualArmorCache.computeIfAbsent(cacheKey, ignored -> {
            ItemStack item = this.kostiumyHandler.getKostiumItem(itemKey);
            if ("helmet".equals(slotKey) && (kostiumName.equalsIgnoreCase("grinch") || kostiumName.equalsIgnoreCase("grincz"))) {
                item = dev.grzybalson.utils.HeadUtils.setHeadTextureLikeShop(
                        new ItemStack(Material.PLAYER_HEAD),
                        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTgzOGQ2MzBkNDk2ZjIxMjg1ZDQ5MDk5YzNmZDhkOTIxNDQyMTcyYTUxNjBjMWI1ZDQyYTUwODBmOWNmODE4YSJ9fX0="
                );
            } else if ("helmet".equals(slotKey) && kostiumName.equalsIgnoreCase("ninja")) {
                item = dev.grzybalson.utils.HeadUtils.setHeadTextureLikeShop(
                        new ItemStack(Material.PLAYER_HEAD),
                        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ4MTFlOWQxYzBlNTQyNDEwYWFiNDQ4ZDM4M2ExNDI1ZGU5NzhhOGVkNzQyYmY2ZjIyNThmZmQ5MzdjOTQ5MCJ9fX0="
                );
            } else if ("helmet".equals(slotKey) && kostiumName.equalsIgnoreCase("goat")) {
                item = dev.grzybalson.utils.HeadUtils.setHeadTextureLikeShop(
                        new ItemStack(Material.PLAYER_HEAD),
                        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWQxN2YzZGFiMDFiYWRmZGU3MDU2ZGJmMDhjMmYxYjRkOTJlZDIzY2ViNjEzY2FiNDViOGM3YzUyOTRmOGUwOSJ9fX0="
                );
            } else if (item == null) {
                Material armorMat = switch (slotKey) {
                    case "helmet" -> Material.LEATHER_HELMET;
                    case "chestplate" -> Material.LEATHER_CHESTPLATE;
                    case "leggings" -> Material.LEATHER_LEGGINGS;
                    case "boots" -> Material.LEATHER_BOOTS;
                    default -> null;
                };
                if (armorMat != null) {
                    item = new ItemStack(armorMat);
                } else {
                    return new ItemStack(Material.AIR);
                }
            }
            return createPacketVisualItem(item, color, true);
        }).clone();
    }

    private ItemStack createPacketVisualItem(ItemStack source, String colorHex, boolean stripEnchantments) {
        if (source == null || source.getType().isAir()) {
            return new ItemStack(Material.AIR);
        }

        ItemStack item = source.clone();
        item.setAmount(1);
        if (stripEnchantments && !item.getEnchantments().isEmpty()) {
            for (var enchantment : new ArrayList<>(item.getEnchantments().keySet())) {
                item.removeEnchantment(enchantment);
            }
        }

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) {
            return item;
        }

        itemMeta.displayName(null);
        itemMeta.lore(null);
        itemMeta.setUnbreakable(false);
        if (!itemMeta.getItemFlags().isEmpty()) {
            itemMeta.removeItemFlags(itemMeta.getItemFlags().toArray(new ItemFlag[0]));
        }
        if (itemMeta.hasAttributeModifiers()) {
            for (Attribute attribute : ALL_ATTRIBUTES) {
                itemMeta.removeAttributeModifier(attribute);
            }
        }
        for (NamespacedKey key : new ArrayList<>(itemMeta.getPersistentDataContainer().getKeys())) {
            itemMeta.getPersistentDataContainer().remove(key);
        }
        if (itemMeta instanceof Damageable damageable) {
            damageable.setDamage(0);
        }
        if (itemMeta instanceof LeatherArmorMeta leatherMeta) {
            applyLeatherColor(leatherMeta, colorHex);
        }

        item.setItemMeta(itemMeta);
        return item;
    }

    private void applyLeatherColor(LeatherArmorMeta meta, String colorHex) {
        if (colorHex == null || colorHex.isBlank()) {
            return;
        }
        try {
            Color color = Color.fromRGB(Integer.parseInt(colorHex.replace("#", ""), 16));
            meta.setColor(color);
        } catch (Exception ignored) {
        }
    }

    public void clearVisualCache() {
        this.visualArmorCache.clear();
    }

    public static void cleanupArmorOnDisable() {
        if (instance == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) if (player.getGameMode() != GameMode.CREATIVE) instance.removeArmor(player);
    }
}
