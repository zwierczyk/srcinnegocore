package dev.grzybalson.managers;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.PickupDataManager;
import dev.grzybalson.modules.unique.UniqueItemService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PickupManager {

    private final HollyCore plugin;
    private final PickupDataManager dataManager;
    private final Map<UUID, BossBar> activeBossBars;
    private final Map<String, String> pickupPackages;
    private final Map<UUID, BukkitTask> pendingRepinTasks;
    private BukkitTask keepBottomTask;

    public PickupManager(HollyCore plugin) {
        this.plugin = plugin;
        this.dataManager = new PickupDataManager(plugin);
        this.activeBossBars = new ConcurrentHashMap<>();
        this.pickupPackages = new ConcurrentHashMap<>();
        this.pendingRepinTasks = new ConcurrentHashMap<>();
        loadPackages();
        startKeepBottomTask();
    }

    private void loadPackages() {
        dataManager.loadPackages().thenAccept(packages -> {
            pickupPackages.clear();
            pickupPackages.putAll(packages);
            plugin.getLogger().info("Loaded " + packages.size() + " pickup packages");
        });
    }

    public void reload() {
        dataManager.reload();
        loadPackages();
        // Odswiez tekst/kolor istniejacych barow bez usuwania z kolejki.
        String message = plugin.getConfigManager().getMessagesConfig().getPickupBossbar();
        for (Map.Entry<UUID, BossBar> entry : activeBossBars.entrySet()) {
            BossBar bar = entry.getValue();
            bar.name(ColorUtils.colorizeComponent(message));
            bar.progress(0.0f);
            bar.color(BossBar.Color.PURPLE);
            bar.overlay(BossBar.Overlay.PROGRESS);
        }
    }

    public void shutdown() {
        if (keepBottomTask != null) {
            keepBottomTask.cancel();
            keepBottomTask = null;
        }
        for (BukkitTask task : pendingRepinTasks.values()) {
            task.cancel();
        }
        pendingRepinTasks.clear();
        for (UUID uuid : activeBossBars.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hideBossBar(player);
            }
        }
        activeBossBars.clear();
    }

    public void createPackage(String name, ItemStack item) {
        ItemStack template = prepareGrantedItem(item);
        if (template == null) {
            return;
        }
        String serialized = ItemSerializer.serializeItem(template);
        pickupPackages.put(name, serialized);
        dataManager.savePackage(name, serialized);
    }

    public ItemStack getPackage(String name) {
        String data = pickupPackages.get(name);
        if (data == null) return null;
        return ItemSerializer.deserializeItem(data);
    }

    public boolean packageExists(String name) {
        return pickupPackages.containsKey(name);
    }

    /**
     * Szablon /odbierz i granty ze sklepu nie moga niesc UID z EQ opa.
     * Skaner unique kasowalby ten sam UID u wszystkich bez bypassu.
     */
    public ItemStack prepareGrantedItem(ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return null;
        }
        ItemStack copy = source.clone();
        UniqueItemService unique = plugin.getUniqueItemService();
        if (unique != null) {
            unique.clearUid(copy);
        }
        return copy;
    }

    public void givePackage(UUID playerUUID, String packageName, int amount) {
        ItemStack item = prepareGrantedItem(getPackage(packageName));
        if (item == null) return;

        PickupDataManager.PickupItem pickupItem = new PickupDataManager.PickupItem(packageName, item, amount);
        dataManager.addPickupItem(playerUUID, pickupItem).thenRun(() -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, () -> showBossBar(player));
            }
        });
    }

    public void givePackageToAll(String packageName, int amount) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            givePackage(player.getUniqueId(), packageName, amount);
        }
    }

    public void giveItemDirect(UUID playerUUID, String sourceName, ItemStack item) {
        giveItemDirect(playerUUID, sourceName, item, 1);
    }

    public void giveItemDirect(UUID playerUUID, String sourceName, ItemStack item, int amount) {
        ItemStack granted = prepareGrantedItem(item);
        if (granted == null) {
            return;
        }
        PickupDataManager.PickupItem pickupItem = new PickupDataManager.PickupItem(sourceName, granted, Math.max(1, amount));
        dataManager.addPickupItem(playerUUID, pickupItem).thenRun(() -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, () -> showBossBar(player));
            }
        });
    }

    public void checkAndShowBossBar(Player player) {
        dataManager.hasPickupItems(player.getUniqueId()).thenAccept(hasItems -> {
            if (hasItems) {
                Bukkit.getScheduler().runTask(plugin, () -> showBossBar(player));
            }
        });
    }

    public void showBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        BossBar existing = activeBossBars.get(uuid);
        if (existing != null) {
            // Juz aktywny — tylko upewnij sie, ze jest widoczny (bez hide/show flicker).
            player.showBossBar(existing);
            return;
        }

        String message = plugin.getConfigManager().getMessagesConfig().getPickupBossbar();
        BossBar bossBar = BossBar.bossBar(
            ColorUtils.colorizeComponent(message),
            0.0f,
            BossBar.Color.PURPLE,
            BossBar.Overlay.PROGRESS
        );

        BossBar raced = activeBossBars.putIfAbsent(uuid, bossBar);
        if (raced == null) {
            player.showBossBar(bossBar);
            schedulePinToBottom(player);
        } else {
            player.showBossBar(raced);
        }
    }

    /**
     * Przenosi bossbar odbierz na sam dol kolejki (ostatni show = dol).
     * Uzywa tej samej instancji — bez tworzenia nowego bara.
     */
    public void pinToBottom(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        BossBar bar = activeBossBars.get(player.getUniqueId());
        if (bar == null) {
            return;
        }
        // Pin tylko gdy bar nie jest juz ostatni — bez zbędnego flickeru.
        BossBar last = null;
        int count = 0;
        for (BossBar candidate : player.activeBossBars()) {
            last = candidate;
            count++;
        }
        if (count == 0 || last == bar) {
            return;
        }
        player.hideBossBar(bar);
        player.showBossBar(bar);
    }

    /**
     * Wywoluj po pokazaniu innego Adventure bossbara, zeby /odbierz zostal na dole.
     */
    public void schedulePinToBottom(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!activeBossBars.containsKey(uuid)) {
            return;
        }
        BukkitTask previous = pendingRepinTasks.remove(uuid);
        if (previous != null) {
            previous.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRepinTasks.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                pinToBottom(online);
            }
        }, 2L);
        pendingRepinTasks.put(uuid, task);
    }

    private void startKeepBottomTask() {
        // Co kilka sekund delikatnie dociagnij do dolu (gdy inne bary sie pojawia bez hooka).
        keepBottomTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeBossBars.isEmpty()) {
                return;
            }
            for (UUID uuid : activeBossBars.keySet()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                int count = 0;
                for (BossBar ignored : player.activeBossBars()) {
                    count++;
                    if (count > 1) {
                        break;
                    }
                }
                if (count > 1) {
                    pinToBottom(player);
                }
            }
        }, 60L, 60L);
    }

    public void hideBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask pending = pendingRepinTasks.remove(uuid);
        if (pending != null) {
            pending.cancel();
        }
        BossBar bossBar = activeBossBars.remove(uuid);
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    public boolean hasActiveBossBar(Player player) {
        return player != null && activeBossBars.containsKey(player.getUniqueId());
    }

    public void claimItems(Player player) {
        dataManager.getPickupItems(player.getUniqueId()).thenAccept(items -> {
            if (items.isEmpty()) {
                player.sendMessage(ColorUtils.colorizeComponent(
                    plugin.getConfigManager().getMessagesConfig().getPickupEmpty()
                ));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (PickupDataManager.PickupItem pickupItem : items) {
                    ItemStack item = prepareGrantedItem(pickupItem.getItem());
                    if (item != null) {
                        item.setAmount(pickupItem.getAmount());
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                        for (ItemStack leftoverItem : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                        }
                    }
                }

                dataManager.clearPickupItems(player.getUniqueId()).thenRun(() -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        hideBossBar(player);
                        player.sendMessage(ColorUtils.colorizeComponent(
                            plugin.getConfigManager().getMessagesConfig().getPickupReceived()
                        ));
                    });
                });
            });
        });
    }

    public PickupDataManager getPickupDataManager() {
        return dataManager;
    }

    public Map<String, String> getPickupPackages() {
        return pickupPackages;
    }
}
