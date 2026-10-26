package dev.grzybalson.modules.tops;

import dev.grzybalson.HollyCore;
import dev.grzybalson.database.ClanDataManager.Clan;
import dev.grzybalson.managers.TopManager;
import dev.grzybalson.modules.npc.Npc;
import dev.grzybalson.modules.npc.NpcPacketSender;
import dev.grzybalson.modules.npc.NpcVisibilityTracker;
import dev.grzybalson.modules.npc.SkinFetcher;
import com.github.retrooper.packetevents.PacketEvents;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TopsManager implements Listener {

    private static final List<String> ALL_TYPES = List.of(
        "POINTS", "CLAN", "KILLS", "DEATHS", "TIME", "CRATES", "BLOCKS", "MONEY", "SWORD", "ZBROJA"
    );

    private final HollyCore plugin;
    private final File configFile;
    private volatile FileConfiguration config;

    private BukkitTask refreshTask;
    private BukkitTask visibilityTask;

    private final Map<String, Npc> topsNpcs = new ConcurrentHashMap<>();
    private final AtomicInteger idGen = new AtomicInteger(200000);
    private final Map<String, List<TopEntry>> cachedTops = new HashMap<>();
    private final NpcVisibilityTracker visibilityTracker = new NpcVisibilityTracker();
    private final Map<String, String> pendingSkinById = new ConcurrentHashMap<>();
    private final EquipmentTopTracker equipmentTopTracker = new EquipmentTopTracker();
    /** Snapshot typow topek budowany na main threadzie — async refresh nie dotyka wtedy YamlConfiguration. */
    private volatile Set<String> neededTypesSnapshot = Collections.emptySet();
    /** Ostatnio wypchniete linie/pozycja hologramu — pozwala pominac forceUpdate gdy nic sie nie zmienilo. */
    private final Map<String, List<String>> lastPushedLines = new HashMap<>();
    private final Map<String, String> lastPushedLocKey = new HashMap<>();

    public TopsManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "addons/tops/tops.yml");
        this.reloadConfigFile();
        this.ensureDefaults();
    }

    public void init() {
        if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) {
            plugin.getLogger().warning("[Tops] PacketEvents nie gotowy — NPC topek moga nie dzialac.");
        }
        if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
            plugin.getLogger().warning("[Tops] FancyHolograms nie znaleziony — hologramy topek nie beda dzialac.");
        }

        NpcPacketSender.init(plugin);
        NpcPacketSender.setVisibilityTracker(visibilityTracker);

        // Worldy (MV itd.) moga nie byc gotowe w onEnable — laduj z opoznieniem + retry.
        Bukkit.getScheduler().runTaskLater(plugin, this::loadNpcs, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, this::loadNpcs, 100L);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        this.rebuildNeededTypesSnapshot();

        // OPTYMALIZACJA: Zwiększone interwały - było 600L -> 1200L (60s zamiast 30s)
        long interval = Math.max(100L, config.getLong("refresh-interval-ticks", 1200L));
        this.refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            this.refreshCaches();
            Bukkit.getScheduler().runTask(plugin, this::refreshAllHolograms);
        }, 100L, interval);

        // OPTYMALIZACJA: Visibility check co 80 ticków zamiast 40 (4s zamiast 2s)
        this.visibilityTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTopsVisibility, 80L, 80L);
    }

    public void shutdown() {
        if (this.refreshTask != null) this.refreshTask.cancel();
        if (this.visibilityTask != null) this.visibilityTask.cancel();
        for (String id : getTopIds()) {
            removeHologramSafe("tops_" + id);
        }
        for (Npc npc : this.topsNpcs.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                NpcPacketSender.hideNpcFrom(p, npc);
            }
        }
        this.topsNpcs.clear();
        this.lastPushedLines.clear();
        this.lastPushedLocKey.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.scheduleShowTopsNpcs(event.getPlayer(), 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline()) {
                this.equipmentTopTracker.update(player);
            }
        }, 15L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.visibilityTracker.forgetPlayer(event.getPlayer().getUniqueId());
        this.equipmentTopTracker.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        this.scheduleShowTopsNpcs(event.getPlayer(), 10L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        this.scheduleShowTopsNpcs(event.getPlayer(), 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        this.scheduleShowTopsNpcs(event.getPlayer(), 10L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, this::loadMissingNpcs, 5L);
    }

    private void scheduleShowTopsNpcs(Player player, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            showTopsNpcsToBatched(player);
        }, delayTicks);
    }

    private void showTopsNpcsToBatched(Player player) {
        if (topsNpcs.isEmpty()) {
            return;
        }
        List<Npc> nearby = new ArrayList<>();
        for (Npc npc : topsNpcs.values()) {
            if (NpcPacketSender.shouldSeeNpc(player, npc)) {
                nearby.add(npc);
            }
        }
        if (nearby.isEmpty()) {
            return;
        }
        if (nearby.size() <= 3) {
            for (Npc npc : nearby) {
                NpcPacketSender.showNpcTo(player, npc);
            }
            return;
        }
        showTopsNpcBatch(player, nearby, 0);
    }

    private void showTopsNpcBatch(Player player, List<Npc> npcs, int index) {
        if (!player.isOnline()) {
            return;
        }
        int end = Math.min(index + 3, npcs.size());
        for (int i = index; i < end; i++) {
            NpcPacketSender.showNpcTo(player, npcs.get(i));
        }
        if (end < npcs.size()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> showTopsNpcBatch(player, npcs, end), 2L);
        }
    }

    private void showTopsNpcsTo(Player player) {
        showTopsNpcsToBatched(player);
    }

    public void reapplyNametags(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        NpcPacketSender.reapplyNametags(player, this.topsNpcs.values());
    }

    public void refreshAllNpcs() {
        for (Npc npc : this.topsNpcs.values()) {
            NpcPacketSender.refreshNpcFullyForAll(npc);
        }
    }

    public void reload() {
        reloadConfigFile();
        ensureDefaults();
        // Linie/offset mogly sie zmienic w configu — wyzeruj memo, zeby kazdy hologram zostal wypchniety.
        this.lastPushedLines.clear();
        this.lastPushedLocKey.clear();
        rebuildNeededTypesSnapshot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            loadNpcs();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                refreshCaches();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    refreshAllHolograms();
                    refreshAllNpcs();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        reapplyNametags(player);
                    }
                });
            });
        });
    }

    private void refreshTopsVisibility() {
        if (this.topsNpcs.isEmpty()) {
            return;
        }
        double maxDistSq = 96.0D * 96.0D;
        for (Player player : Bukkit.getOnlinePlayers()) {
            World playerWorld = player.getWorld();
            Location playerLoc = player.getLocation();
            for (Npc npc : this.topsNpcs.values()) {
                Location npcLoc = npc.getLocation();
                boolean inWorld = playerWorld.equals(npcLoc.getWorld());
                boolean nearby = inWorld && playerLoc.distanceSquared(npcLoc) <= maxDistSq;
                boolean visible = NpcPacketSender.isVisibleTo(player, npc);
                if (nearby && !visible) {
                    NpcPacketSender.showNpcTo(player, npc);
                } else if ((!nearby || !inWorld) && visible) {
                    NpcPacketSender.hideNpcFrom(player, npc);
                }
            }
        }
    }

    private void loadNpcs() {
        reloadConfigFile();
        int loaded = 0;
        int skipped = 0;
        for (String id : getTopIds()) {
            if (this.topsNpcs.containsKey(id)) {
                loaded++;
                continue;
            }
            if (trySpawnNpc(id)) {
                loaded++;
            } else {
                skipped++;
            }
        }

        this.rebuildNeededTypesSnapshot();

        if (skipped > 0) {
            plugin.getLogger().warning("[Tops] " + skipped + " NPC pominiętych (brak worlda) — retry przy WorldLoad.");
        } else if (loaded > 0) {
            plugin.getLogger().info("[Tops] Zaladowano " + loaded + " NPC topek.");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                showTopsNpcsTo(p);
            }
        }, 20L);
    }

    private void loadMissingNpcs() {
        int spawned = 0;
        for (String id : getTopIds()) {
            if (this.topsNpcs.containsKey(id)) {
                continue;
            }
            if (trySpawnNpc(id)) {
                spawned++;
            }
        }
        this.rebuildNeededTypesSnapshot();
        if (spawned > 0) {
            plugin.getLogger().info("[Tops] Doladowano " + spawned + " NPC topek po zaladowaniu worlda.");
            for (Player p : Bukkit.getOnlinePlayers()) {
                showTopsNpcsTo(p);
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                refreshCaches();
                Bukkit.getScheduler().runTask(plugin, this::refreshAllHolograms);
            });
        }
    }

    private boolean trySpawnNpc(String id) {
        String path = "entries." + id;
        String worldName = config.getString(path + ".world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return false;
        }

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");
        Location loc = new Location(world, x, y, z, yaw, pitch);

        Npc npc = new Npc("tops_" + id, this.idGen.incrementAndGet(), loc);
        npc.setDisplayName("@none");

        String skinName = config.getString(path + ".skin-name", null);
        if (skinName != null) {
            npc.setSkinName(skinName);
            npc.setSkinTexture(config.getString(path + ".skin-texture", null));
            npc.setSkinSignature(config.getString(path + ".skin-signature", null));
        }

        this.topsNpcs.put(id, npc);
        this.applyRankSword(id, config.getInt(path + ".position", 0));
        return true;
    }

    private void saveSkinData(String id, Npc npc) {
        String path = "entries." + id;
        if (npc.getSkinName() != null) {
            config.set(path + ".skin-name", npc.getSkinName());
            config.set(path + ".skin-texture", npc.getSkinTexture());
            config.set(path + ".skin-signature", npc.getSkinSignature());
        }
        saveConfigFile();
    }

    private void ensureDefaults() {
        if (!config.isSet("refresh-interval-ticks")) {
            config.set("refresh-interval-ticks", 600);
        }

        this.setTypeDefaults("POINTS",
            "&#FFAA00&l★ ᴛᴏᴘᴋᴀ ʀᴀɴᴋɪɴɢᴜ ★",
            "&f{TOP} &f{NAME}", "&#FFAA00{VALUE} &#C1C1C1pkt.", 2.15);

        this.setTypeDefaults("CLAN",
            "&#FFAA00&l★ ᴛᴏᴘᴋᴀ ᴋʟᴀɴᴏᴡ ★",
            "&f{TOP} &f{NAME}", "&#FFAA00{VALUE} &#C1C1C1pkt.", 2.15);

        this.setTypeDefaults("KILLS",
            "&#FF5C5C&l⚔ ᴛᴏᴘᴋᴀ ᴢᴀʙᴏ́ᴊꜱᴛᴡ ⚔",
            "&f{TOP} &f{NAME}", "&#FF5C5C{VALUE} &#C1C1C1zabójstw", 2.15);

        this.setTypeDefaults("DEATHS",
            "&#C1C1C1&l☠ ᴛᴏᴘᴋᴀ ꜱ́ᴍɪᴇʀᴄɪ ☠",
            "&f{TOP} &f{NAME}", "&#C1C1C1{VALUE} &#777777śmierci", 2.15);

        this.setTypeDefaults("TIME",
            "&#89E2FF&l⏰ ᴛᴏᴘᴋᴀ ᴄᴢᴀꜱᴜ ɢʀʏ ⏰",
            "&f{TOP} &f{NAME}", "&#89E2FF{VALUE}", 2.15);

        this.setTypeDefaults("CRATES",
            "&#DD5FFF&l✨ ᴛᴏᴘᴋᴀ ꜱᴋʀᴢʏɴᴇᴋ ✨",
            "&f{TOP} &f{NAME}", "&#DD5FFF{VALUE} &#C1C1C1otwartych", 2.15);

        this.setTypeDefaults("BLOCKS",
            "&#60FF8B&l⛏ ᴛᴏᴘᴋᴀ ʙʟᴏᴋᴏ́ᴡ ⛏",
            "&f{TOP} &f{NAME}", "&#60FF8B{VALUE} &#C1C1C1wykopanych", 2.15);

        this.setTypeDefaults("MONEY",
            "&#FFD700&l✦ ᴛᴏᴘᴋᴀ ᴋᴀꜱʏ ✦",
            "&f{TOP} &f{NAME}", "&#FFD700{VALUE} &#C1C1C1$", 2.15);

        this.setTypeDefaults("SWORD",
            "&#6CC9FF&l⚔ ᴛᴏᴘᴋᴀ ᴍɪᴇᴄᴢᴀ ⚔",
            "&f{TOP} &f{NAME}", "&#6CC9FF{VALUE} &#C1C1C1poziom", 2.15);

        this.setTypeDefaults("ZBROJA",
            "&#98FF6C&l🛡 ᴛᴏᴘᴋᴀ ᴢʙʀᴏᴊɪ 🛡",
            "&f{TOP} &f{NAME}", "&#98FF6C{VALUE} &#C1C1C1poziom", 2.15);

        saveConfigFile();
    }

    private void setTypeDefaults(String type, String line1, String line2, String line3, double holoOffsetY) {
        String base = "types." + type;
        if (!config.isSet(base + ".hologram-offset-y")) {
            config.set(base + ".hologram-offset-y", holoOffsetY);
        } else if (config.getDouble(base + ".hologram-offset-y") == 2.6D) {
            // Stary domyslny offset — dopasuj do size 0.7 nad glowa NPC.
            config.set(base + ".hologram-offset-y", holoOffsetY);
        }
        if (!config.isSet(base + ".lines")) {
            config.set(base + ".lines", List.of(line1, line2, line3));
        }
    }

    public List<String> getTopIds() {
        ConfigurationSection section = config.getConfigurationSection("entries");
        return section != null ? new ArrayList<>(section.getKeys(false)) : Collections.emptyList();
    }

    public void createTop(String type, int position, Location location) {
        String normalized = EquipmentTopTracker.normalizeType(type);
        String id = normalized.toLowerCase(Locale.ROOT) + "_" + position;
        String path = "entries." + id;
        config.set(path + ".type", normalized);
        config.set(path + ".position", position);
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getX());
        config.set(path + ".y", location.getY());
        config.set(path + ".z", location.getZ());
        config.set(path + ".yaw", (double) location.getYaw());
        config.set(path + ".pitch", (double) location.getPitch());
        saveConfigFile();
        this.rebuildNeededTypesSnapshot();

        Npc npc = new Npc("tops_" + id, this.idGen.incrementAndGet(), location);
        npc.setDisplayName("@none");
        this.topsNpcs.put(id, npc);
        this.applyRankSword(id, position);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(location.getWorld())) {
                NpcPacketSender.showNpcTo(p, npc);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            this.refreshCacheForType(normalized);
            Bukkit.getScheduler().runTask(plugin, () -> this.refreshHologram(id));
        });
    }

    public void deleteTop(String id) {
        Npc npc = this.topsNpcs.remove(id);
        if (npc != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                NpcPacketSender.hideNpcFrom(p, npc);
            }
        }
        config.set("entries." + id, null);
        saveConfigFile();
        this.rebuildNeededTypesSnapshot();
        this.lastPushedLines.remove("tops_" + id);
        this.lastPushedLocKey.remove("tops_" + id);
        removeHologramSafe("tops_" + id);
    }

    /** Musi byc wolane z main threadu — czyta YamlConfiguration. */
    private void rebuildNeededTypesSnapshot() {
        Set<String> neededTypes = new HashSet<>();
        for (String id : getTopIds()) {
            neededTypes.add(EquipmentTopTracker.normalizeType(config.getString("entries." + id + ".type", "POINTS")));
        }
        this.neededTypesSnapshot = Set.copyOf(neededTypes);
    }

    private void refreshCaches() {
        for (String type : this.neededTypesSnapshot) {
            this.refreshCacheForType(type);
        }
    }

    private void refreshCacheForType(String type) {
        try {
            String normalized = EquipmentTopTracker.normalizeType(type);
            List<TopEntry> list = switch (normalized) {
                case "POINTS" -> fromTopManager("elo");
                case "KILLS" -> fromTopManager("kills");
                case "DEATHS" -> fromTopManager("deaths");
                case "TIME" -> fromTopManager("playtime");
                case "MONEY" -> fromTopManager("money");
                case "CRATES" -> fromTopManager("cases");
                case "BLOCKS" -> fromTopManager("blocks");
                case "CLAN" -> fetchClanTop(100);
                case "SWORD" -> {
                    this.equipmentTopTracker.scanOnlinePlayers();
                    yield this.equipmentTopTracker.topSword(100);
                }
                case "ZBROJA" -> {
                    this.equipmentTopTracker.scanOnlinePlayers();
                    yield this.equipmentTopTracker.topArmor(100);
                }
                default -> Collections.emptyList();
            };
            synchronized (this.cachedTops) {
                this.cachedTops.put(normalized, list);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Tops] Blad odswiezania cache " + type + ": " + e.getMessage());
        }
    }

    private List<TopEntry> fromTopManager(String topType) {
        TopManager topManager = plugin.getTopManager();
        if (topManager == null) return Collections.emptyList();

        List<TopManager.TopEntry> source = topManager.getCachedTop(topType);
        List<TopEntry> result = new ArrayList<>(source.size());
        for (TopManager.TopEntry entry : source) {
            if (entry != null && entry.getName() != null) {
                result.add(new TopEntry(entry.getName(), entry.getValue(), entry.getIdentityKey()));
            }
        }
        return result;
    }

    private List<TopEntry> fetchClanTop(int limit) {
        if (plugin.getClanManager() == null) return Collections.emptyList();
        try {
            List<Clan> clans = plugin.getClanManager().getTopClans(limit).join();
            List<TopEntry> result = new ArrayList<>();
            for (Clan clan : clans) {
                if (clan != null && clan.getTag() != null) {
                    result.add(new TopEntry(clan.getTag(), clan.getElo(), "clan:" + clan.getTag()));
                }
            }
            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("[Tops] Blad topki klanow: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public void refreshAllHolograms() {
        for (String id : getTopIds()) {
            this.refreshHologram(id);
        }
    }

    private void refreshHologram(String id) {
        String path = "entries." + id;
        String type = EquipmentTopTracker.normalizeType(config.getString(path + ".type", "POINTS"));
        int position = config.getInt(path + ".position", 1);

        List<TopEntry> tops;
        synchronized (this.cachedTops) {
            tops = this.cachedTops.getOrDefault(type, Collections.emptyList());
        }

        String name = null;
        double rawValue = 0;
        String identity = null;
        if (position >= 1 && position <= tops.size()) {
            TopEntry entry = tops.get(position - 1);
            name = entry.name();
            rawValue = entry.value();
            identity = entry.identity();
        }

        String worldName = config.getString(path + ".world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        double offsetY = config.getDouble("types." + type + ".hologram-offset-y", 2.15);
        Location holoLoc = new Location(world, x, y + offsetY, z);

        String displayName = name != null ? name : "---";
        String formattedValue = this.formatValue(type, rawValue);

        List<String> configLines = config.getStringList("types." + type + ".lines");
        if (configLines.isEmpty()) {
            configLines = List.of("&f{NAME}", "&7{VALUE}");
        }

        String topEmoji = resolveTopEmoji(position);

        List<String> resolvedLines = new ArrayList<>();
        for (String line : configLines) {
            resolvedLines.add(line
                .replace("{NAME}", displayName)
                .replace("{VALUE}", formattedValue)
                .replace("{POSITION}", String.valueOf(position))
                .replace("{TOP}", topEmoji)
            );
        }

        String holoId = "tops_" + id;
        try {
            upsertFancyHologram(holoId, holoLoc, resolvedLines);
        } catch (Throwable t) {
            plugin.getLogger().warning("[Tops] Blad hologramu " + holoId + ": " + t.getMessage());
        }

        this.applyRankSword(id, position);
        this.updateNpcSkin(id, type, name, identity);
    }

    private void applyRankSword(String id, int position) {
        Npc npc = this.topsNpcs.get(id);
        if (npc == null) return;

        Material sword = switch (position) {
            case 1 -> Material.DIAMOND_SWORD;
            case 2 -> Material.IRON_SWORD;
            case 3 -> Material.WOODEN_SWORD;
            default -> null;
        };

        // Miecz zalezy wylacznie od pozycji — nie rozsylaj pakietu gdy przedmiot sie nie zmienil.
        Material target = sword != null ? sword : Material.AIR;
        ItemStack currentItem = npc.getEquipment().get("mainhand");
        if (currentItem != null && currentItem.getType() == target) {
            return;
        }

        npc.setEquipment("mainhand", new ItemStack(target));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (NpcPacketSender.isVisibleTo(player, npc)) {
                NpcPacketSender.sendEquipment(player, npc);
            }
        }
    }

    private void upsertFancyHologram(String holoId, Location location, List<String> lines) {
        if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
            return;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        Hologram hologram = manager.getHologram(holoId).orElse(null);

        if (hologram == null) {
            TextHologramData data = new TextHologramData(holoId, location);
            data.setText(lines);
            applyTopsHologramStyle(data);
            hologram = manager.create(data);
            manager.addHologram(hologram);
            // Hologram stworzony na nowo — wymus pelny push przy nastepnym przebiegu.
            this.lastPushedLines.remove(holoId);
            this.lastPushedLocKey.remove(holoId);
            return;
        }

        // Tekst i pozycja zmieniaja sie rzadko — bez zmian nie wysylaj metadanych do viewerow.
        String locKey = locationKey(location);
        if (lines.equals(this.lastPushedLines.get(holoId)) && locKey.equals(this.lastPushedLocKey.get(holoId))) {
            return;
        }

        if (hologram.getData() instanceof TextHologramData textData) {
            textData.setText(lines);
            textData.setLocation(location);
            applyTopsHologramStyle(textData);
        }

        try {
            hologram.forceUpdate();
        } catch (Throwable ignored) {
            hologram.queueUpdate();
        }

        this.lastPushedLines.put(holoId, new ArrayList<>(lines));
        this.lastPushedLocKey.put(holoId, locKey);
    }

    private String locationKey(Location location) {
        World world = location.getWorld();
        return (world != null ? world.getName() : "?")
            + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ();
    }

    private void applyTopsHologramStyle(TextHologramData data) {
        try {
            data.setPersistent(false);
        } catch (Throwable ignored) {
        }
        data.setTextShadow(true);
        data.setBackground(Hologram.TRANSPARENT);
        data.setScale(new org.joml.Vector3f(0.7f, 0.7f, 0.7f));
        try {
            data.setTextAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
        } catch (Throwable ignored) {
        }
        try {
            data.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        } catch (Throwable ignored) {
        }
    }

    private void updateNpcSkin(String id, String type, String name, String identity) {
        Npc npc = this.topsNpcs.get(id);
        if (npc == null) return;

        if (name == null || name.isBlank() || "---".equals(name)) {
            return;
        }

        String skinName;
        if (type.equalsIgnoreCase("CLAN") && plugin.getClanManager() != null) {
            Clan clan = plugin.getClanManager().getCachedClan(name);
            if (clan == null) {
                try {
                    clan = plugin.getClanManager().getClan(name).join();
                } catch (Exception ignored) {
                }
            }
            skinName = clan != null ? resolvePlayerName(clan.getLeader()) : name;
        } else {
            skinName = name;
        }

        if (skinName == null || skinName.isBlank() || "---".equals(skinName)) {
            return;
        }

        if (skinName.equalsIgnoreCase(npc.getSkinName())
            && npc.getSkinTexture() != null
            && npc.getSkinSignature() != null) {
            return;
        }

        final String targetSkin = skinName;
        this.pendingSkinById.put(id, targetSkin);

        SkinFetcher.fetchSkin(targetSkin).thenAccept(skinData -> {
            if (skinData == null) {
                this.pendingSkinById.remove(id, targetSkin);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!targetSkin.equals(this.pendingSkinById.get(id))) {
                    return;
                }
                Npc current = this.topsNpcs.get(id);
                if (current == null) return;

                current.setSkinName(targetSkin);
                current.setSkinTexture(skinData[0]);
                current.setSkinSignature(skinData[1]);
                this.pendingSkinById.remove(id, targetSkin);
                this.saveSkinData(id, current);
                NpcPacketSender.refreshNpcSkinForAll(current);
            });
        });
    }

    private String resolvePlayerName(UUID uuid) {
        if (uuid == null) return "---";
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null && !offline.getName().isBlank()) {
            return offline.getName();
        }
        return "---";
    }

    private static final Map<Integer, String> TOP_EMOJIS = Map.of(
        1, "➊",
        2, "➋",
        3, "➌",
        4, "➍",
        5, "➎",
        6, "➏",
        7, "➐",
        8, "➑",
        9, "➒",
        10, "➓"
    );

    private String resolveTopEmoji(int position) {
        return TOP_EMOJIS.getOrDefault(position, "#" + position);
    }

    private String formatValue(String type, double rawValue) {
        if (type.equalsIgnoreCase("TIME")) return formatPlaytime(rawValue);
        if (type.equalsIgnoreCase("MONEY")) return formatMoney(rawValue);
        if (type.equalsIgnoreCase("SWORD") || type.equalsIgnoreCase("ZBROJA")) {
            return dev.grzybalson.utils.ArmorStarsUtil.formatLevel(rawValue);
        }
        NumberFormat nf = NumberFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return nf.format(rawValue);
    }

    private String formatPlaytime(double value) {
        // HollyCore trzyma playtime w ms
        long totalMillis = Math.max(0L, Math.round(value));
        long totalSeconds = totalMillis / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m");
        return sb.toString().trim();
    }

    private String formatMoney(double amount) {
        if (amount >= 1_000_000) return String.format(Locale.US, "%.1fm", amount / 1_000_000);
        if (amount >= 1_000) return String.format(Locale.US, "%.1fk", amount / 1_000);
        return String.format(Locale.US, "%.0f", amount);
    }

    private void removeHologramSafe(String holoId) {
        try {
            if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
                return;
            }
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            manager.getHologram(holoId).ifPresent(manager::removeHologram);
        } catch (Throwable ignored) {
        }
    }

    private void reloadConfigFile() {
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("[Tops] Nie mozna utworzyc tops.yml: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[Tops] Nie mozna zapisac tops.yml: " + e.getMessage());
        }
    }

    public List<String> getTypes() {
        return ALL_TYPES;
    }

    public record TopEntry(String name, double value, String identity) {}
}
