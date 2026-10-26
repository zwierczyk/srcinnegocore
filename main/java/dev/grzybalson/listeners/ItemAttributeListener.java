package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.NBTUtils;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ItemAttributeListener implements Listener {

    private static final String SHARP_DAMAGE_NAME = "holly_sharp_netherite_damage";
    private static final String SHARP_SPEED_NAME = "holly_sharp_netherite_speed";
    private static final String UPGRADE_TYPE_KEY = "holly_upgrade_type";
    private static final double NETHERITE_SWORD_DAMAGE_MODIFIER = 7.0D;
    private static final double NETHERITE_SWORD_SPEED_MODIFIER = -2.4D;
    private static final String LEATHER_ARMOR_NAME = "holly_leather_netherite_armor";
    private static final String LEATHER_TOUGHNESS_NAME = "holly_leather_netherite_toughness";
    private static final String LEATHER_KB_NAME = "holly_leather_netherite_kb";

    private static final AttributeModifier SHARP_DAMAGE_MODIFIER = new AttributeModifier(
        uuidFor(SHARP_DAMAGE_NAME),
        SHARP_DAMAGE_NAME,
        NETHERITE_SWORD_DAMAGE_MODIFIER,
        AttributeModifier.Operation.ADD_NUMBER,
        EquipmentSlot.HAND
    );
    private static final AttributeModifier SHARP_SPEED_MODIFIER = new AttributeModifier(
        uuidFor(SHARP_SPEED_NAME),
        SHARP_SPEED_NAME,
        NETHERITE_SWORD_SPEED_MODIFIER,
        AttributeModifier.Operation.ADD_NUMBER,
        EquipmentSlot.HAND
    );
    private static final Map<Material, ArmorModifiers> LEATHER_MODIFIERS = createLeatherModifiers();
    private static final ArmorModifiers SKYPVP_HEAD_MODIFIERS = new ArmorModifiers(
        EquipmentSlot.HEAD,
        new AttributeModifier(
            uuidFor(LEATHER_ARMOR_NAME + "_PLAYER_HEAD"),
            LEATHER_ARMOR_NAME,
            3.0D,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HEAD
        ),
        new AttributeModifier(
            uuidFor(LEATHER_TOUGHNESS_NAME + "_PLAYER_HEAD"),
            LEATHER_TOUGHNESS_NAME,
            3.0D,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HEAD
        ),
        new AttributeModifier(
            uuidFor(LEATHER_KB_NAME + "_PLAYER_HEAD"),
            LEATHER_KB_NAME,
            0.1D,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HEAD
        )
    );

    private static final int NORMALIZE_SLOTS_PER_TICK = 10;

    private final HollyCore plugin;
    private final Map<UUID, PendingNormalization> pendingNormalizations = new HashMap<>();
    private final Set<UUID> scheduledNormalizations = new HashSet<>();
    private final Set<UUID> activeInventoryScans = new HashSet<>();

    public ItemAttributeListener(HollyCore plugin) {
        this.plugin = plugin;
    }

    /** Pelna normalizacja to 41 slotow z ItemMeta — przy fali wejsc rozrzucamy ja po tickach. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int online = plugin.getServer().getOnlinePlayers().size();
        long delay = (online > 150 ? 12L : 2L)
            + Math.floorMod(player.getUniqueId().hashCode(), online > 150 ? 40 : 20);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                queueFullNormalization(player);
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        queueSlotNormalization(event.getPlayer(), event.getNewSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryAction action = event.getAction();
        if (action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.UNKNOWN) {
            queueFullNormalization(player);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }

        PlayerInventory playerInventory = player.getInventory();
        if (clicked.equals(playerInventory)) {
            queueSlotNormalization(player, event.getSlot());
        } else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // The destination slot is selected by Bukkit and can merge multiple stacks.
            queueFullNormalization(player);
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0) {
            queueSlotNormalization(player, hotbarButton);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                queueSlotNormalization(player, event.getView().convertSlot(rawSlot));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        queueSlotsNormalization(player, inventory.getHeldItemSlot(), 36, 37, 38, 39, 40);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (item == null || !needsAttributeNormalization(item)) {
            return;
        }
        queueFullNormalization(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        queueFullNormalization(event.getEnchanter());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingNormalizations.remove(uuid);
        scheduledNormalizations.remove(uuid);
        activeInventoryScans.remove(uuid);
    }

    private void queueFullNormalization(Player player) {
        if (!canNormalize(player)) {
            return;
        }
        PendingNormalization pending = pendingNormalizations.computeIfAbsent(
            player.getUniqueId(), ignored -> new PendingNormalization()
        );
        pending.fullScan = true;
        pending.slots.clear();
        scheduleNormalization(player);
    }

    private void queueSlotNormalization(Player player, int slot) {
        if (!canNormalize(player) || slot < 0 || slot > 40) {
            return;
        }
        PendingNormalization pending = pendingNormalizations.computeIfAbsent(
            player.getUniqueId(), ignored -> new PendingNormalization()
        );
        if (!pending.fullScan) {
            pending.slots.add(slot);
        }
        scheduleNormalization(player);
    }

    private void queueSlotsNormalization(Player player, int... slots) {
        for (int slot : slots) {
            queueSlotNormalization(player, slot);
        }
    }

    private boolean canNormalize(Player player) {
        return player != null && player.isOnline() && plugin.isEnabled();
    }

    private void scheduleNormalization(Player player) {
        UUID uuid = player.getUniqueId();
        if (!scheduledNormalizations.add(uuid)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            scheduledNormalizations.remove(uuid);
            PendingNormalization pending = pendingNormalizations.remove(uuid);
            if (pending == null || !player.isOnline()) {
                return;
            }
            if (pending.fullScan) {
                normalizeInventoryBatched(player);
            } else {
                normalizeSlots(player, pending.slots);
            }
        }, 2L);
    }

    private void normalizeInventoryBatched(Player player) {
        UUID uuid = player.getUniqueId();
        if (!activeInventoryScans.add(uuid)) {
            return;
        }
        normalizeInventoryBatch(player, 0);
    }

    private void normalizeInventoryBatch(Player player, int startSlot) {
        if (!canNormalize(player)) {
            activeInventoryScans.remove(player.getUniqueId());
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int online = plugin.getServer().getOnlinePlayers().size();
        int perTick = online > 150 ? 6 : NORMALIZE_SLOTS_PER_TICK;
        long stepDelay = online > 150 ? 2L : 1L;
        int end = Math.min(startSlot + perTick, 41);
        for (int slot = startSlot; slot < end; slot++) {
            normalizeSlot(inventory, slot);
        }
        if (end < 41) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> normalizeInventoryBatch(player, end), stepDelay);
            return;
        }
        activeInventoryScans.remove(player.getUniqueId());
    }

    private void normalizeInventory(Player player) {
        normalizeInventoryBatched(player);
    }

    private void normalizeSlots(Player player, Set<Integer> slots) {
        if (slots.isEmpty()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot : slots) {
            normalizeSlot(inventory, slot);
        }
    }

    private void normalizeSlot(PlayerInventory inventory, int slot) {
        ItemStack current = inventory.getItem(slot);
        ItemStack normalized = normalizeItem(current);
        if (normalized != current) {
            inventory.setItem(slot, normalized);
        }
    }

    private ItemStack normalizeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return item;
        }

        Material type = item.getType();
        boolean isLeather = isLeatherArmor(type);
        boolean isSkyPvpHead = isSkyPvpUpgradeHelmet(item);
        boolean isSwd = isSword(type);
        boolean hasDamageAll = item.containsEnchantment(Enchantment.DAMAGE_ALL);

        if (!isLeather && !isSkyPvpHead && !isSwd && !hasDamageAll) {
            return item;
        }

        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;

        boolean shouldBeSharpWeapon = hasDamageAll && shouldUseSharpnessNetheriteSwordAttributes(item, meta);
        boolean hasSharpWeaponModifiers = meta != null && hasSharpnessNetheriteSwordAttributes(meta);
        boolean shouldBeLeatherArmor = isLeather;
        boolean shouldBeSkyPvpHead = isSkyPvpHead;

        if (!shouldBeSharpWeapon && !hasSharpWeaponModifiers && !shouldBeLeatherArmor && !shouldBeSkyPvpHead) {
            return item;
        }

        if (meta == null) {
            return item;
        }

        boolean changed = false;
        if (shouldBeSharpWeapon) {
            changed |= ensureSharpnessNetheriteSwordAttributes(meta);
        } else if (hasSharpWeaponModifiers) {
            changed |= removeSharpnessNetheriteSwordAttributes(meta);
        }
        if (shouldBeLeatherArmor) {
            changed |= ensureLeatherNetheriteKnockback(meta, type, item);
        }
        if (shouldBeSkyPvpHead) {
            changed |= ensureSkyPvpHeadAttributes(meta);
        }

        if (!changed) {
            return item;
        }

        ItemStack copy = item.clone();
        copy.setItemMeta(meta);
        return copy;
    }

    private boolean isSkyPvpUpgradeHelmet(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }
        return "HELMET".equalsIgnoreCase(NBTUtils.getString(item, UPGRADE_TYPE_KEY));
    }

    private boolean ensureSkyPvpHeadAttributes(ItemMeta meta) {
        ArmorModifiers modifiers = SKYPVP_HEAD_MODIFIERS;
        boolean attrsOk = hasOnlyModifier(meta, Attribute.GENERIC_ARMOR, LEATHER_ARMOR_NAME, modifiers.armor().getAmount(), modifiers.slot())
            && hasOnlyModifier(meta, Attribute.GENERIC_ARMOR_TOUGHNESS, LEATHER_TOUGHNESS_NAME, modifiers.toughness().getAmount(), modifiers.slot())
            && hasOnlyModifier(meta, Attribute.GENERIC_KNOCKBACK_RESISTANCE, LEATHER_KB_NAME, modifiers.knockback().getAmount(), modifiers.slot());
        boolean hideOk = meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES);
        if (attrsOk && hideOk) {
            return false;
        }

        if (!attrsOk) {
            meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
            meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
            meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, modifiers.armor());
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, modifiers.toughness());
            meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, modifiers.knockback());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return true;
    }

    private boolean isSkyPvpUpgradeItem(ItemStack item) {
        String style = NBTUtils.getString(item, "holly_upgrade_style");
        return style != null && style.equalsIgnoreCase("SKYPVP");
    }

    private boolean shouldUseSharpnessNetheriteSwordAttributes(ItemStack item, ItemMeta meta) {
        if (item == null || !item.containsEnchantment(Enchantment.DAMAGE_ALL)) {
            return false;
        }
        if (plugin.getUpgradeManager() != null && plugin.getUpgradeManager().isExcalibur(item, meta)) {
            return false;
        }
        if (isUpgradeSword(item)) {
            return true;
        }
        Material type = item.getType();
        if (type == Material.DIAMOND_SWORD || type == Material.NETHERITE_SWORD) {
            return false;
        }

        boolean candidate = isSword(type)
            || meta != null
            && (meta.hasDisplayName()
            || meta.hasLore()
            || meta.hasCustomModelData()
            || !meta.getPersistentDataContainer().getKeys().isEmpty());
        return candidate;
    }

    private boolean hasSharpnessNetheriteSwordAttributes(ItemMeta meta) {
        return meta != null && (
            hasNamedAttributeModifier(meta, Attribute.GENERIC_ATTACK_DAMAGE, SHARP_DAMAGE_NAME) ||
            hasNamedAttributeModifier(meta, Attribute.GENERIC_ATTACK_SPEED, SHARP_SPEED_NAME)
        );
    }

    private boolean ensureSharpnessNetheriteSwordAttributes(ItemMeta meta) {
        boolean changed = false;
        if (!hasSharpnessNetheriteSwordAttribute(meta, Attribute.GENERIC_ATTACK_DAMAGE, SHARP_DAMAGE_NAME, NETHERITE_SWORD_DAMAGE_MODIFIER)) {
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, SHARP_DAMAGE_MODIFIER);
            changed = true;
        }
        if (!hasSharpnessNetheriteSwordAttribute(meta, Attribute.GENERIC_ATTACK_SPEED, SHARP_SPEED_NAME, NETHERITE_SWORD_SPEED_MODIFIER)) {
            meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, SHARP_SPEED_MODIFIER);
            changed = true;
        }
        if (!meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            changed = true;
        }
        return changed;
    }

    private boolean removeSharpnessNetheriteSwordAttributes(ItemMeta meta) {
        boolean changed = false;
        changed |= removeNamedAttributeModifier(meta, Attribute.GENERIC_ATTACK_DAMAGE, SHARP_DAMAGE_NAME);
        changed |= removeNamedAttributeModifier(meta, Attribute.GENERIC_ATTACK_SPEED, SHARP_SPEED_NAME);
        return changed;
    }

    private boolean hasSharpnessNetheriteSwordAttribute(ItemMeta meta, Attribute attribute, String name, double amount) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null || modifiers.size() != 1) {
            return false;
        }

        AttributeModifier modifier = modifiers.iterator().next();
        return modifier.getName().equalsIgnoreCase(name)
            && Math.abs(modifier.getAmount() - amount) < 0.0001D
            && modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER
            && modifier.getSlot() == EquipmentSlot.HAND;
    }

    private boolean hasNamedAttributeModifier(ItemMeta meta, Attribute attribute, String name) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null || modifiers.isEmpty()) {
            return false;
        }
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean removeNamedAttributeModifier(ItemMeta meta, Attribute attribute, String name) {
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null || modifiers.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (AttributeModifier modifier : List.copyOf(modifiers)) {
            if (modifier.getName().equalsIgnoreCase(name)) {
                changed |= meta.removeAttributeModifier(attribute, modifier);
            }
        }
        return changed;
    }

    private boolean isSword(Material material) {
        return material != null && material.name().endsWith("_SWORD");
    }

    private boolean isUpgradeSword(ItemStack item) {
        return "SWORD".equalsIgnoreCase(NBTUtils.getString(item, UPGRADE_TYPE_KEY));
    }

    private boolean ensureLeatherNetheriteKnockback(ItemMeta meta, Material type, ItemStack item) {
        ArmorModifiers modifiers = LEATHER_MODIFIERS.get(type);
        if (modifiers == null) {
            return false;
        }

        boolean attrsOk = hasOnlyModifier(meta, Attribute.GENERIC_ARMOR, LEATHER_ARMOR_NAME, modifiers.armor().getAmount(), modifiers.slot())
            && hasOnlyModifier(meta, Attribute.GENERIC_ARMOR_TOUGHNESS, LEATHER_TOUGHNESS_NAME, modifiers.toughness().getAmount(), modifiers.slot())
            && hasOnlyModifier(meta, Attribute.GENERIC_KNOCKBACK_RESISTANCE, LEATHER_KB_NAME, modifiers.knockback().getAmount(), modifiers.slot());
        boolean hideOk = meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES);
        if (attrsOk && hideOk) {
            return false;
        }

        if (!attrsOk) {
            meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
            meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
            meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, modifiers.armor());
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, modifiers.toughness());
            meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, modifiers.knockback());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return true;
    }

    private boolean hasOnlyModifier(ItemMeta meta, Attribute attribute, String name, double amount, EquipmentSlot slot) {
        if (!meta.hasAttributeModifiers()) {
            return false;
        }
        Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null || modifiers.size() != 1) {
            return false;
        }
        AttributeModifier modifier = modifiers.iterator().next();
        return modifier.getName().equalsIgnoreCase(name)
            && Math.abs(modifier.getAmount() - amount) < 0.0001D
            && modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER
            && modifier.getSlot() == slot;
    }

    private boolean isLeatherArmor(Material material) {
        return LEATHER_MODIFIERS.containsKey(material);
    }

    private boolean needsAttributeNormalization(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material type = item.getType();
        return isLeatherArmor(type) || isSword(type) || isSkyPvpUpgradeHelmet(item)
            || item.containsEnchantment(Enchantment.DAMAGE_ALL);
    }

    private static Map<Material, ArmorModifiers> createLeatherModifiers() {
        Map<Material, ArmorModifiers> result = new EnumMap<>(Material.class);
        addLeatherModifiers(result, Material.LEATHER_HELMET, EquipmentSlot.HEAD, 3.0D);
        addLeatherModifiers(result, Material.LEATHER_CHESTPLATE, EquipmentSlot.CHEST, 8.0D);
        addLeatherModifiers(result, Material.LEATHER_LEGGINGS, EquipmentSlot.LEGS, 6.0D);
        addLeatherModifiers(result, Material.LEATHER_BOOTS, EquipmentSlot.FEET, 3.0D);
        return Map.copyOf(result);
    }

    private static void addLeatherModifiers(
        Map<Material, ArmorModifiers> target,
        Material material,
        EquipmentSlot slot,
        double armorPoints
    ) {
        target.put(material, new ArmorModifiers(
            slot,
            new AttributeModifier(
                uuidFor(LEATHER_ARMOR_NAME + "_" + material.name()),
                LEATHER_ARMOR_NAME,
                armorPoints,
                AttributeModifier.Operation.ADD_NUMBER,
                slot
            ),
            new AttributeModifier(
                uuidFor(LEATHER_TOUGHNESS_NAME + "_" + material.name()),
                LEATHER_TOUGHNESS_NAME,
                3.0D,
                AttributeModifier.Operation.ADD_NUMBER,
                slot
            ),
            new AttributeModifier(
                uuidFor(LEATHER_KB_NAME + "_" + material.name()),
                LEATHER_KB_NAME,
                0.1D,
                AttributeModifier.Operation.ADD_NUMBER,
                slot
            )
        ));
    }

    private static UUID uuidFor(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class PendingNormalization {
        private boolean fullScan;
        private final Set<Integer> slots = new HashSet<>();
    }

    private record ArmorModifiers(
        EquipmentSlot slot,
        AttributeModifier armor,
        AttributeModifier toughness,
        AttributeModifier knockback
    ) {
    }
}
