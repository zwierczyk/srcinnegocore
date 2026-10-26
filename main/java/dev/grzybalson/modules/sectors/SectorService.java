package dev.grzybalson.modules.sectors;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.config.configs.SectorConfig;
import dev.grzybalson.modules.diagnostics.ServerDiagnosticsService;
import dev.grzybalson.network.NatsService;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.ItemSerializer;
import dev.grzybalson.utils.PlayerInventories;
import dev.grzybalson.utils.SpooferCountHelper;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SectorService implements Listener {

    private final HollyCore plugin;
    private final SectorConfig config;
    private final Map<String, SectorInfo> sectors = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingTransfers = new ConcurrentHashMap<>();
    private final Set<UUID> transferringPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask heartbeatTask;

    public SectorService(HollyCore plugin) {
        this.plugin = plugin;
        File folder = HollyDataPaths.getAddonFolder(plugin, "sectors");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.config = eu.okaeri.configs.ConfigManager.create(SectorConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(new File(folder, "sectors.yml"));
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        this.config.save();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        NatsService nats = plugin.getNatsService();
        if (nats != null && nats.isConnected()) {
            nats.subscribe(NatsService.SECTOR_HEARTBEAT, this::handleHeartbeat);
            nats.subscribeReplies(NatsService.SECTOR_TRANSFER_PREFIX + config.getCurrentSector(), this::handleTransferRequest);
        }
        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::publishHeartbeat, 20L, 20L);
        plugin.getLogger().info("Sector " + config.getCurrentSector() + " is online.");
    }

    public void shutdown() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
    }

    public SectorConfig getConfig() {
        return config;
    }

    public String getCurrentSector() {
        return config.getCurrentSector();
    }

    private void refreshLocalOnline() {
        String current = config.getCurrentSector();
        if (current == null || current.isBlank()) {
            return;
        }
        SectorInfo info = sectors.get(current.toLowerCase(Locale.ROOT));
        if (info == null) {
            return;
        }
        sectors.put(current.toLowerCase(Locale.ROOT), withLiveOnline(info));
    }

    private SectorInfo withLiveOnline(SectorInfo info) {
        int live = SpooferCountHelper.getServerVisibleCount(info.getName());
        if (live < 0 && info.getName().equalsIgnoreCase(config.getCurrentSector())) {
            live = SpooferCountHelper.getVisibleOnlineCount();
        }
        return live < 0 ? info : info.withOnline(live);
    }

    public Collection<SectorInfo> getOnlineSectors() {
        return sectors.values().stream()
            .map(this::withLiveOnline)
            .filter(SectorInfo::isAlive)
            .sorted(Comparator.comparing(SectorInfo::getName))
            .toList();
    }

    public SectorInfo getSector(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return sectors.get(name.toLowerCase(Locale.ROOT));
    }

    public void openStatusGui(Player player) {
        refreshLocalOnline();
        new SectorStatusGUI(plugin, this, player).open();
    }

    public List<SectorInfo> getSectorsForGui() {
        List<String> channels = config.getChannels();
        if (channels.isEmpty()) {
            return new ArrayList<>(getOnlineSectors());
        }

        List<SectorInfo> result = new ArrayList<>();
        for (String channel : channels) {
            if (channel == null || channel.isBlank()) {
                continue;
            }
            SectorInfo info = sectors.get(channel.toLowerCase(Locale.ROOT));
            if (info != null && info.isAlive()) {
                result.add(withLiveOnline(info));
            } else {
                result.add(new SectorInfo(channel, 0, 0, 0, 0, 0));
            }
        }
        return result;
    }

    public boolean isTransferring(UUID uuid) {
        return transferringPlayers.contains(uuid) || pendingTransfers.containsKey(uuid);
    }

    public void openGui(Player player) {
        refreshLocalOnline();
        new SectorGUI(plugin, this, player).open();
    }

    public void transfer(Player player, String target) {
        if (target.equalsIgnoreCase(config.getCurrentSector())) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getAlreadyHere()));
            return;
        }
        SectorInfo info = sectors.get(target.toLowerCase());
        if (info == null || !info.isAlive()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getOffline()));
            return;
        }
        if (plugin.getCombatManager() != null && !plugin.getCombatManager().isInSafeRegionOrPvpDeny(player.getLocation())) {
            String spawn = config.getSpawnRegion();
            if (spawn != null && !spawn.isBlank()) {
                player.sendMessage(ColorUtils.colorizeComponent(config.getOnlySpawn()));
                return;
            }
        }

        // Najpierw zapisz pełny MSLF EC (wszystkie rzędy) — potem snapshot + clear.
        var mslf = plugin.getMslfManager();
        if (mslf != null && mslf.enderChestService() != null) {
            mslf.enderChestService().saveForSectorTransfer(player);
        }

        Document snapshot = snapshot(player);
        if (plugin.getInventorySyncManager() != null && plugin.getInventorySyncManager().isEnabled()) {
            plugin.getInventorySyncManager().savePlayer(player, true);
        }
        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("sector_transfers").replaceOne(
                Filters.eq("_id", player.getUniqueId().toString()),
                snapshot,
                new ReplaceOptions().upsert(true)
            )
        ).thenAccept(ignored -> Bukkit.getScheduler().runTask(plugin, () -> requestTransfer(player, target)));
    }

    private void requestTransfer(Player player, String target) {
        NatsService nats = plugin.getNatsService();
        if (nats == null || !nats.isConnected()) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getTransferFailed()));
            return;
        }
        Document request = new Document("uuid", player.getUniqueId().toString())
            .append("from", config.getCurrentSector())
            .append("name", player.getName());
        nats.request(NatsService.SECTOR_TRANSFER_PREFIX + target, request, config.getTransferTimeoutMs())
            .whenComplete((response, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || response == null || !"ACCEPT".equalsIgnoreCase(response.getString("status"))) {
                    player.sendMessage(ColorUtils.colorizeComponent(config.getTransferFailed()));
                    return;
                }
                transferringPlayers.add(player.getUniqueId());
                player.sendMessage(ColorUtils.colorizeComponent(config.getTransferring().replace("{sector}", target)));
                player.getInventory().clear();
                player.getEnderChest().clear();
                player.updateInventory();
                connectProxy(player, target);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    transferringPlayers.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(ColorUtils.colorizeComponent(config.getTransferFailed()));
                        applySnapshot(player);
                    }
                }, 80L);
            }));
    }

    private void handleHeartbeat(Document document) {
        String name = document.getString("name");
        if (name == null || name.isBlank()) {
            return;
        }
        sectors.put(name.toLowerCase(), fromHeartbeat(document));
    }

    private SectorInfo fromHeartbeat(Document document) {
        String name = document.getString("name");
        return new SectorInfo(
            name,
            document.getDouble("tps") == null ? 20.0 : document.getDouble("tps"),
            document.getDouble("load") == null ? 0.0 : document.getDouble("load"),
            document.getInteger("online", 0),
            document.getInteger("max", 0),
            document.getLong("time") == null ? System.currentTimeMillis() : document.getLong("time"),
            document.getDouble("mspt") == null ? 0.0 : document.getDouble("mspt"),
            document.getDouble("cpu") == null ? 0.0 : document.getDouble("cpu"),
            document.getDouble("ramGb") == null ? 0.0 : document.getDouble("ramGb"),
            document.getDouble("avgPing") == null ? 0.0 : document.getDouble("avgPing"),
            document.getString("sectorType"),
            document.getString("world"),
            document.getString("border"),
            document.getInteger("entities", 0),
            document.getInteger("chunks", 0),
            document.getInteger("items", 0),
            document.getInteger("monsters", 0),
            SectorInfo.safeStringList(document.get("lagSources")),
            SectorInfo.safeStringList(document.get("predictions"))
        );
    }

    private void handleTransferRequest(Document document, String replyTo) {
        String uuidRaw = document.getString("uuid");
        if (uuidRaw == null) {
            plugin.getNatsService().reply(replyTo, new Document("status", "ERROR"));
            return;
        }
        UUID uuid = UUID.fromString(uuidRaw);
        pendingTransfers.put(uuid, System.currentTimeMillis());
        plugin.getNatsService().reply(replyTo, new Document("status", "ACCEPT").append("sector", config.getCurrentSector()));
    }

    private void publishHeartbeat() {
        ServerDiagnosticsService diagnosticsService = plugin.getServerDiagnosticsService();
        ServerDiagnosticsService.DiagnosticsSnapshot snapshot = diagnosticsService != null
            ? diagnosticsService.getLatest()
            : null;
        if ((snapshot == null || snapshot.capturedAt() <= 0L) && diagnosticsService != null) {
            snapshot = diagnosticsService.captureSnapshot();
        }

        double tps = snapshot != null ? snapshot.tps() : 20.0;
        double load = 0.0;
        try {
            load = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        } catch (Exception ignored) {
        }
        if (load < 0.0D && snapshot != null) {
            load = snapshot.processCpu();
        }

        long now = System.currentTimeMillis();
        int visibleOnline = SpooferCountHelper.getVisibleOnlineCount();
        Document heartbeat = new Document("name", config.getCurrentSector())
            .append("tps", tps)
            .append("load", load)
            .append("online", visibleOnline)
            .append("max", snapshot != null ? snapshot.maxPlayers() : Bukkit.getMaxPlayers())
            .append("time", now);

        SectorInfo sectorInfo;
        if (snapshot != null && snapshot.capturedAt() > 0L) {
            heartbeat.append("mspt", snapshot.mspt())
                .append("cpu", snapshot.processCpu())
                .append("ramGb", snapshot.ramUsedGb())
                .append("avgPing", snapshot.avgPingMs())
                .append("sectorType", snapshot.sectorType())
                .append("world", snapshot.worldName())
                .append("border", snapshot.borderText())
                .append("entities", snapshot.entities())
                .append("chunks", snapshot.loadedChunks())
                .append("items", snapshot.items())
                .append("monsters", snapshot.monsters())
                .append("lagSources", snapshot.lagSources())
                .append("predictions", snapshot.predictions());
            sectorInfo = fromHeartbeat(heartbeat);
        } else {
            sectorInfo = new SectorInfo(
                config.getCurrentSector(),
                tps,
                load,
                visibleOnline,
                Bukkit.getMaxPlayers(),
                now
            );
        }

        sectors.put(config.getCurrentSector().toLowerCase(), sectorInfo);
        NatsService nats = plugin.getNatsService();
        if (nats != null) {
            nats.publish(NatsService.SECTOR_HEARTBEAT, heartbeat);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        pendingTransfers.remove(player.getUniqueId());
        transferringPlayers.remove(player.getUniqueId());
        applySnapshot(player);
    }

    private void applySnapshot(Player player) {
        plugin.getMongoManager().toFuture(
            plugin.getMongoManager().getCollection("sector_transfers").findOneAndDelete(Filters.eq("_id", player.getUniqueId().toString()))
        ).thenAccept(document -> {
            if (document == null) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> restore(player, document));
        });
    }

    private Document snapshot(Player player) {
        ItemStack[] ender = player.getEnderChest().getContents();
        var mslf = plugin.getMslfManager();
        if (mslf != null && mslf.enderChestService() != null) {
            ender = mslf.enderChestService().captureContents(player);
        }
        return new Document("_id", player.getUniqueId().toString())
            .append("inventory", ItemSerializer.serializeItems(player.getInventory().getStorageContents()))
            .append("armor", ItemSerializer.serializeItems(player.getInventory().getArmorContents()))
            .append("extra", ItemSerializer.serializeItems(player.getInventory().getExtraContents()))
            .append("ender", ItemSerializer.serializeItems(ender))
            .append("exp", player.getTotalExperience())
            .append("health", player.getHealth())
            .append("food", player.getFoodLevel())
            .append("gamemode", player.getGameMode().name());
    }

    private void restore(Player player, Document document) {
        ItemStack[] inv = safeItems(document.getString("inventory"), "inventory", player);
        ItemStack[] armor = safeItems(document.getString("armor"), "armor", player);
        ItemStack[] extra = safeItems(document.getString("extra"), "extra", player);
        ItemStack[] ender = safeItems(document.getString("ender"), "ender", player);

        if (extra != null) {
            PlayerInventories.apply(player, inv, armor, extra);
        } else {
            PlayerInventories.applyStorageAndArmor(player, inv, armor);
        }
        var mslf = plugin.getMslfManager();
        if (ender != null && mslf != null && mslf.enderChestService() != null) {
            mslf.enderChestService().restoreFromSnapshot(player, ender);
        } else if (ender != null) {
            player.getEnderChest().setContents(ender);
        }
        if (plugin.getInventorySyncManager() != null) {
            plugin.getInventorySyncManager().markSectorRestored(player.getUniqueId());
        }
        if (document.getInteger("exp") != null) {
            player.setTotalExperience(document.getInteger("exp"));
        }
        if (document.getDouble("health") != null) {
            player.setHealth(Math.min(player.getMaxHealth(), document.getDouble("health")));
        }
        if (document.getInteger("food") != null) {
            player.setFoodLevel(document.getInteger("food"));
        }
        if (document.getString("gamemode") != null) {
            try {
                player.setGameMode(GameMode.valueOf(document.getString("gamemode")));
            } catch (Exception ignored) {
            }
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.updateInventory();
    }

    private ItemStack[] safeItems(String data, String field, Player player) {
        if (data == null || data.isBlank()) {
            return new ItemStack[0];
        }
        ItemStack[] items = ItemSerializer.deserializeItems(data);
        if (items == null) {
            plugin.getLogger().warning("[Sectors] Nie udalo sie odczytac snapshotu transferu dla "
                + player.getName() + " (" + player.getUniqueId() + "), pole=" + field
                + ". Pominieto nadpisanie tego pola.");
            return null;
        }
        return items;
    }

    private void connectProxy(Player player, String server) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", stream.toByteArray());
        } catch (Exception exception) {
            player.sendMessage(ColorUtils.colorizeComponent(config.getTransferFailed()));
        }
    }
}
