package dev.grzybalson.modules.kit;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.KitConfig;
import dev.grzybalson.modules.unique.UniqueItemService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.HeadUtils;
import dev.grzybalson.utils.ItemSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class KitManager {

    private static final String FIRST_JOIN_COLLECTION = "kit_first_join";
    private static final String START_KIT_COLLECTION = "kit_start_once";
    private static final String START_KIT_NAME = "start";

    private final HollyCore plugin;
    private final Map<String, Kit> kits;
    private final Map<UUID, Map<String, Long>> kitCooldowns;
    private final Set<UUID> startKitBusy;
    private final Set<UUID> memoryStartClaims;

    public KitManager(HollyCore plugin) {
        this.plugin = plugin;
        this.kits = new ConcurrentHashMap<>();
        this.kitCooldowns = new ConcurrentHashMap<>();
        this.startKitBusy = ConcurrentHashMap.newKeySet();
        this.memoryStartClaims = ConcurrentHashMap.newKeySet();
        loadKits();
    }

    public void loadKits() {
        kits.clear();
        KitConfig config = plugin.getConfigManager().getKitConfig();

        for (Map.Entry<String, KitConfig.KitData> entry : config.getKits().entrySet()) {
            String name = entry.getKey();
            KitConfig.KitData kitData = entry.getValue();

            List<ItemStack> items = stripUidsForTemplate(deserializeKitItems(kitData.getItems()));

            ItemStack icon = loadKitIcon(name, kitData);

            Kit kit = new Kit(
                name,
                kitData.getDisplayName(),
                kitData.getPermission(),
                kitData.getCooldown(),
                kitData.getSlot(),
                icon,
                items,
                kitData.getLore(),
                kitData.getPrefix()
            );
            kits.put(name.toLowerCase(), kit);
        }

        plugin.getLogger().info("Loaded " + kits.size() + " kits from config.");
    }

    private ItemStack loadKitIcon(String kitName, KitConfig.KitData kitData) {
        // 1) hollyservice: icon = MATERIAL albo base64 head value
        ItemStack resolved = resolveIconValue(kitData.getIcon());
        if (resolved != null) {
            return resolved;
        }

        // 2) HollyCore: icon-material + icon-head-value
        String materialName = kitData.getIconMaterial();
        String headValue = kitData.getIconHeadValue();
        if (materialName != null && !materialName.isBlank()) {
            Material material = Material.matchMaterial(materialName.trim());
            if (material == null) {
                material = Material.getMaterial(materialName.trim().toUpperCase(Locale.ROOT));
            }
            if (material != null && material.isItem()) {
                ItemStack icon = new ItemStack(material);
                if (material == Material.PLAYER_HEAD && headValue != null && !headValue.isBlank()) {
                    return HeadUtils.setHeadTextureLikeShop(icon, headValue);
                }
                return icon;
            }
            plugin.getLogger().warning("Nieznany material ikony dla kita '" + kitName + "': " + materialName);
        }

        // 3) legacy: icon jako zserializowany ItemStack
        if (kitData.getIcon() != null && !kitData.getIcon().isBlank()) {
            ItemStack serialized = ItemSerializer.deserializeItem(kitData.getIcon());
            if (serialized != null && !serialized.getType().isAir()) {
                return serialized;
            }
        }

        return new ItemStack(Material.CHEST);
    }

    /**
     * 1:1 z hollyservice KitGui.resolveIcon — MATERIAL albo base64 texture.
     */
    public static ItemStack resolveIconValue(String iconValue) {
        if (iconValue == null || iconValue.isBlank()) {
            return null;
        }
        String trimmed = iconValue.trim();

        Material material = Material.matchMaterial(trimmed);
        if (material == null) {
            try {
                material = Material.valueOf(trimmed.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                material = null;
            }
        }
        if (material != null && material.isItem()) {
            return new ItemStack(material);
        }

        // Base64 texture / URL hash — jak w hollyservice (length > 30)
        if (trimmed.length() > 30 && !trimmed.contains(";")) {
            return HeadUtils.setHeadTextureLikeShop(new ItemStack(Material.PLAYER_HEAD), trimmed);
        }

        return null;
    }

    public void saveKit(Kit kit) {
        List<ItemStack> cleanItems = stripUidsForTemplate(kit.getItems());
        kit.setItems(cleanItems);

        List<String> itemsData = new ArrayList<>();
        String serializedItems = ItemSerializer.serializeItems(cleanItems.toArray(new ItemStack[0]));
        if (serializedItems != null) {
            itemsData.add(serializedItems);
        }

        // Zapis jak hollyservice: icon = MATERIAL lub head-value
        String iconValue = "CHEST";
        String iconMaterial = "";
        String iconHeadValue = "";
        if (kit.getIcon() != null && !kit.getIcon().getType().isAir()) {
            ItemStack icon = kit.getIcon();
            if (icon.getType() == Material.PLAYER_HEAD && HeadUtils.isCustomHead(icon)) {
                String detected = HeadUtils.getBase64FromHead(icon);
                if (detected != null && !detected.isBlank()) {
                    iconValue = detected;
                    iconMaterial = Material.PLAYER_HEAD.name();
                    iconHeadValue = detected;
                } else {
                    iconValue = Material.PLAYER_HEAD.name();
                    iconMaterial = Material.PLAYER_HEAD.name();
                }
            } else {
                iconValue = icon.getType().name();
                iconMaterial = icon.getType().name();
            }
        }

        KitConfig.KitData kitData = new KitConfig.KitData(
            kit.getDisplayName(),
            kit.getPermission(),
            kit.getCooldown(),
            kit.getSlot(),
            iconValue,
            iconMaterial,
            iconHeadValue,
            itemsData,
            kit.getLore(),
            kit.getPrefix()
        );

        plugin.getConfigManager().getKitConfig().addKit(kit.getName(), kitData);
        kits.put(kit.getName().toLowerCase(), kit);
    }

    public boolean setKitIcon(String kitId, String iconRaw) {
        Kit kit = getKit(kitId);
        if (kit == null) {
            return false;
        }
        ItemStack icon = resolveIconValue(iconRaw);
        if (icon == null) {
            // PLAYER_HEAD + osobny value
            if (iconRaw != null && iconRaw.length() > 30) {
                icon = HeadUtils.setHeadTextureLikeShop(new ItemStack(Material.PLAYER_HEAD), iconRaw.trim());
            } else {
                return false;
            }
        }
        kit.setIcon(icon);
        saveKit(kit);
        return true;
    }

    private List<ItemStack> deserializeKitItems(List<String> itemsData) {
        List<ItemStack> items = new ArrayList<>();
        if (itemsData == null || itemsData.isEmpty()) {
            return items;
        }

        if (itemsData.size() == 1) {
            ItemStack[] serializedItems = ItemSerializer.deserializeItems(itemsData.get(0));
            if (serializedItems != null) {
                for (ItemStack item : serializedItems) {
                    if (item != null) {
                        items.add(item);
                    }
                }
                return items;
            }
        }

        for (String data : itemsData) {
            ItemStack item = ItemSerializer.deserializeItem(data);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    public void deleteKit(String name) {
        plugin.getConfigManager().getKitConfig().removeKit(name);
        kits.remove(name.toLowerCase());
    }

    public Kit getKit(String name) {
        return kits.get(name.toLowerCase());
    }

    public Collection<Kit> getAllKits() {
        return kits.values();
    }

    public boolean isStartKit(String kitName) {
        return kitName != null && START_KIT_NAME.equalsIgnoreCase(kitName);
    }

    public void tryGiveStartKit(Player player, Runnable onSuccess, Runnable onFail) {
        if (player == null || !player.isOnline()) {
            if (onFail != null) {
                onFail.run();
            }
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!startKitBusy.add(uuid)) {
            return;
        }

        KitConfig config = plugin.getConfigManager().getKitConfig();
        var rewardManager = plugin.getDiscordRewardManager();
        if (rewardManager == null) {
            startKitBusy.remove(uuid);
            player.sendMessage(ColorUtils.colorizeComponent(config.getStartKitDiscordRequired()));
            if (onFail != null) {
                onFail.run();
            }
            return;
        }

        rewardManager.hasMinecraftClaim(uuid, player.getName()).whenComplete((claimed, throwable) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    startKitBusy.remove(uuid);
                    if (onFail != null) {
                        onFail.run();
                    }
                    return;
                }
                if (throwable != null || claimed == null || !claimed) {
                    startKitBusy.remove(uuid);
                    player.sendMessage(ColorUtils.colorizeComponent(config.getStartKitDiscordRequired()));
                    if (onFail != null) {
                        onFail.run();
                    }
                    return;
                }
                claimStartKitOnce(uuid).whenComplete((firstTime, claimError) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        startKitBusy.remove(uuid);
                        if (!player.isOnline()) {
                            if (onFail != null) {
                                onFail.run();
                            }
                            return;
                        }
                        if (claimError != null || firstTime == null || !firstTime) {
                            player.sendMessage(ColorUtils.colorizeComponent(config.getStartKitAlreadyClaimed()));
                            if (onFail != null) {
                                onFail.run();
                            }
                            return;
                        }
                        if (giveKitInternal(player, START_KIT_NAME)) {
                            if (onSuccess != null) {
                                onSuccess.run();
                            }
                        } else if (onFail != null) {
                            onFail.run();
                        }
                    })
                );
            })
        );
    }

    public boolean giveKit(Player player, String kitName) {
        if (isStartKit(kitName)) {
            tryGiveStartKit(player, null, null);
            return false;
        }
        return giveKitInternal(player, kitName);
    }

    private boolean giveKitInternal(Player player, String kitName) {
        Kit kit = getKit(kitName);
        if (kit == null) {
            return false;
        }

        KitConfig config = plugin.getConfigManager().getKitConfig();

        if (!player.hasPermission(kit.getPermission())) {
            String message = config.getNoPermission()
                .replace("{NAME}", kit.getName())
                .replace("{KIT}", kit.getPrefix());
            player.sendMessage(ColorUtils.colorizeComponent(message));
            return false;
        }

        long remaining = getRemainingCooldown(player, kitName);
        if (remaining > 0) {
            String message = config.getCooldown()
                .replace("{TIME}", Kit.formatTime(remaining))
                .replace("{time}", Kit.formatTime(remaining))
                .replace("{NAME}", kit.getDisplayName())
                .replace("{KIT}", kit.getPrefix());
            player.sendMessage(ColorUtils.colorizeComponent(message));
            return false;
        }

        UniqueItemService uniqueItems = plugin.getUniqueItemService();
        for (ItemStack item : kit.getItems()) {
            if (item != null) {
                ItemStack copy = item.clone();
                if (uniqueItems != null) {
                    // Bez UUID w kicie — skaner EQ nada je dopiero w inventory gracza.
                    uniqueItems.clearUid(copy);
                }
                prepareSigilFromKit(copy);
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(copy);
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }

        suppressSigilUseAfterKit(player);
        setCooldown(player, kitName, kit.getCooldown());

        String message = config.getReceived()
            .replace("{NAME}", kit.getDisplayName())
            .replace("{KIT}", kit.getPrefix())
            .replace("{kit}", kit.getDisplayName());
        player.sendMessage(ColorUtils.colorizeComponent(message));

        return true;
    }

    public boolean giveFirstJoinKit(Player player) {
        KitConfig config = plugin.getConfigManager().getKitConfig();
        String kitName = config.getFirstJoinKit();
        return giveStarterKit(player, kitName, true);
    }

    /**
     * Pierwsze wejście — tylko raz na gracza (zapis w MongoDB).
     */
    public CompletableFuture<Boolean> tryGiveFirstJoinKit(Player player) {
        if (player == null) {
            return CompletableFuture.completedFuture(false);
        }
        KitConfig config = plugin.getConfigManager().getKitConfig();
        String kitName = config.getFirstJoinKit();
        if (kitName == null || kitName.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        if (isCreativeLike(player)) {
            return CompletableFuture.completedFuture(false);
        }

        UUID uuid = player.getUniqueId();
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            if (player.hasPlayedBefore()) {
                return CompletableFuture.completedFuture(false);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    giveStarterKit(player, kitName, true);
                }
            });
            return CompletableFuture.completedFuture(true);
        }

        return claimFirstJoinKit(uuid, kitName).thenApply(claimed -> {
            if (!claimed) {
                return false;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    giveStarterKit(player, kitName, true);
                }
            });
            return true;
        });
    }

    public void giveDeathKit(Player player) {
        // Wyłączone — kit gracz tylko przy pierwszym wejściu (tryGiveFirstJoinKit).
    }

    private CompletableFuture<Boolean> claimFirstJoinKit(UUID uuid, String kitName) {
        return plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection(FIRST_JOIN_COLLECTION).findOneAndUpdate(
                Filters.eq("uuid", uuid.toString()),
                Updates.combine(
                    Updates.setOnInsert("uuid", uuid.toString()),
                    Updates.setOnInsert("kit", kitName.toLowerCase(Locale.ROOT)),
                    Updates.setOnInsert("givenAt", new Date())
                ),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.BEFORE)
            )
        ).thenApply(before -> before == null);
    }

    private CompletableFuture<Boolean> claimStartKitOnce(UUID uuid) {
        if (plugin.getMongoManager() == null || !plugin.getMongoManager().isAvailable()) {
            return CompletableFuture.completedFuture(memoryStartClaims.add(uuid));
        }
        return plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection(START_KIT_COLLECTION).findOneAndUpdate(
                Filters.eq("uuid", uuid.toString()),
                Updates.combine(
                    Updates.setOnInsert("uuid", uuid.toString()),
                    Updates.setOnInsert("kit", START_KIT_NAME),
                    Updates.setOnInsert("givenAt", new Date())
                ),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.BEFORE)
            )
        ).thenApply(before -> before == null);
    }

    private boolean giveStarterKit(Player player, String kitName, boolean sendMessage) {
        if (player == null || !player.isOnline() || kitName == null || kitName.isBlank()) {
            return false;
        }
        if (isCreativeLike(player)) {
            return false;
        }
        Kit kit = getKit(kitName);
        if (kit == null) {
            plugin.getLogger().warning("[Kit] Brak kita: " + kitName);
            return false;
        }

        UniqueItemService uniqueItems = plugin.getUniqueItemService();
        for (ItemStack item : kit.getItems()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack copy = item.clone();
            if (uniqueItems != null) {
                uniqueItems.clearUid(copy);
            }
            prepareSigilFromKit(copy);
            HashMap<Integer, ItemStack> leftover = addItemToStorageOnly(player, copy);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        suppressSigilUseAfterKit(player);
        if (sendMessage) {
            KitConfig config = plugin.getConfigManager().getKitConfig();
            String message = config.getReceived()
                .replace("{NAME}", kit.getDisplayName())
                .replace("{KIT}", kit.getPrefix())
                .replace("{kit}", kit.getDisplayName());
            player.sendMessage(ColorUtils.colorizeComponent(message));
        }
        return true;
    }

    private static boolean isCreativeLike(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    /**
     * {@link PlayerInventory#addItem(ItemStack)} w Paperze wypełnia też sloty zbroi (36–39).
     * Kit ma trafiać tylko do głównego EQ (0–35), bez auto-zakładania.
     */
    private static HashMap<Integer, ItemStack> addItemToStorageOnly(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        if (player == null || item == null || item.getType().isAir()) {
            return leftover;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack remaining = item.clone();

        for (int slot = 0; slot < 36 && remaining.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                inventory.setItem(slot, remaining);
                return leftover;
            }
            if (existing.isSimilar(remaining) && existing.getAmount() < existing.getMaxStackSize()) {
                int move = Math.min(remaining.getAmount(), existing.getMaxStackSize() - existing.getAmount());
                existing.setAmount(existing.getAmount() + move);
                remaining.setAmount(remaining.getAmount() - move);
                inventory.setItem(slot, existing);
            }
        }

        if (remaining.getAmount() > 0) {
            leftover.put(0, remaining);
        }
        return leftover;
    }

    private void prepareSigilFromKit(ItemStack item) {
        var sigilManager = plugin.getSigilManager();
        if (sigilManager == null || item == null) {
            return;
        }
        sigilManager.prepareGrantedSigilItem(item);
    }

    private void suppressSigilUseAfterKit(Player player) {
        var sigilManager = plugin.getSigilManager();
        if (sigilManager == null || player == null) {
            return;
        }
        sigilManager.suppressItemUse(player, 2500L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sigilManager.refreshPhysicalSigilItems(player);
            }
        }, 2L);
    }

    public void setCooldown(Player player, String kitName, int seconds) {
        kitCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
            .put(kitName.toLowerCase(), System.currentTimeMillis() + (seconds * 1000L));

        saveCooldown(player.getUniqueId(), kitName, seconds);
    }

    public long getRemainingCooldown(Player player, String kitName) {
        Map<String, Long> playerCooldowns = kitCooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;

        Long expiry = playerCooldowns.get(kitName.toLowerCase());
        if (expiry == null) return 0;

        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }

    public boolean isOnCooldown(Player player, String kitName) {
        return getRemainingCooldown(player, kitName) > 0;
    }

    private void saveCooldown(UUID uuid, String kitName, int seconds) {
        org.bson.Document doc = new org.bson.Document()
            .append("uuid", uuid.toString())
            .append("kit", kitName.toLowerCase())
            .append("expiry", System.currentTimeMillis() + (seconds * 1000L));

        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("kit_cooldowns")
                .replaceOne(
                    new org.bson.Document("uuid", uuid.toString()).append("kit", kitName.toLowerCase()),
                    doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true)
                )
        );
    }

    public void loadPlayerCooldowns(UUID uuid) {
        plugin.getMongoManager().toListFuture(
            plugin.getMongoManager().getCollection("kit_cooldowns")
                .find(new org.bson.Document("uuid", uuid.toString()))
        ).thenAccept(docs -> {
            Map<String, Long> cooldowns = new ConcurrentHashMap<>();
            for (org.bson.Document doc : docs) {
                String kit = doc.getString("kit");
                Long expiry = doc.getLong("expiry");
                if (kit != null && expiry != null && expiry > System.currentTimeMillis()) {
                    cooldowns.put(kit, expiry);
                }
            }
            if (!cooldowns.isEmpty()) {
                kitCooldowns.put(uuid, cooldowns);
            }
        });
    }

    public void unloadPlayerCooldowns(UUID uuid) {
        kitCooldowns.remove(uuid);
    }

    public void resetCooldown(UUID uuid, String kitName) {
        Map<String, Long> playerCooldowns = kitCooldowns.get(uuid);
        if (playerCooldowns != null) {
            playerCooldowns.remove(kitName.toLowerCase());
        }

        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("kit_cooldowns")
                .deleteOne(new org.bson.Document("uuid", uuid.toString()).append("kit", kitName.toLowerCase()))
        );
    }

    public void resetAllCooldowns(UUID uuid) {
        kitCooldowns.remove(uuid);
        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("kit_cooldowns")
                .deleteMany(new org.bson.Document("uuid", uuid.toString()))
        );
    }

    public void setKitItemsFromInventory(String kitId, ItemStack[] contents) {
        Kit kit = getKit(kitId);
        if (kit == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        if (contents != null) {
            for (ItemStack item : contents) {
                if (item != null && !item.getType().isAir()) {
                    items.add(item.clone());
                }
            }
        }
        kit.setItems(stripUidsForTemplate(items));
        saveKit(kit);
    }

    private List<ItemStack> stripUidsForTemplate(List<ItemStack> source) {
        List<ItemStack> cleaned = new ArrayList<>();
        if (source == null) {
            return cleaned;
        }
        UniqueItemService uniqueItems = plugin.getUniqueItemService();
        for (ItemStack item : source) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack copy = item.clone();
            if (uniqueItems != null) {
                uniqueItems.clearUid(copy);
            }
            cleaned.add(copy);
        }
        return cleaned;
    }

    private String formatTime(long seconds) {
        return Kit.formatTime(seconds);
    }

    public boolean hasPermission(Player player, String kitName) {
        Kit kit = getKit(kitName);
        return kit != null && player.hasPermission(kit.getPermission());
    }
}
