package dev.grzybalson.modules.masakraszafka;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ClickCooldown;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MasakraSzafkaManager implements Listener, CommandExecutor, TabCompleter {
    public static final String ADDON_NAME = "masakra-szafka";
    private static final String RESOURCE = "addons/masakra-szafka/masakra-szafka.yml";
    private static final String ADMIN_PERMISSION = "Holly.szafka.admin";
    private static final Set<String> ITEM_SECTIONS = Set.of("costumes", "talismans");

    private final HollyCore plugin;
    private final File file;
    private final SzafkaStorage storage;
    private final SzafkaVisuals visuals;
    private final SzafkaEffects effects;
    private final SzafkaVouchers vouchers;
    private final SzafkaMenu menu;
    private final boolean packetsActive;
    private final Map<UUID, SzafkaProfile> profiles = new HashMap<>();
    private final Map<UUID, SzafkaPerks> perks = new HashMap<>();
    private final Map<UUID, String> nameTagSuffixes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loading = new HashMap<>();
    private final Map<UUID, List<Consumer<SzafkaProfile>>> pendingChanges = new HashMap<>();
    private final Map<UUID, PendingClaim> claims = new HashMap<>();
    private final Map<UUID, Integer> revisions = new HashMap<>();
    private final BukkitTask task;
    private SzafkaCatalog catalog;
    private long loadSequence;
    private int ticks;

    public MasakraSzafkaManager(HollyCore plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), ADDON_NAME + ".yml");
        SzafkaCatalog loaded = readCatalog();
        catalog = loaded != null ? loaded : new SzafkaCatalog(bundled(), plugin.getLogger());
        storage = new SzafkaStorage(plugin);
        visuals = new SzafkaVisuals(this);
        visuals.reload(catalog);
        packetsActive = visuals.register();
        if (!packetsActive) plugin.getLogger().warning("[masakra-szafka] PacketEvents niedostępne — kostiumy nie będą widoczne dla innych graczy.");
        effects = new SzafkaEffects(this);
        vouchers = new SzafkaVouchers(plugin, this);
        menu = new SzafkaMenu(plugin, this);
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(this, plugin);
        pluginManager.registerEvents(effects, plugin);
        pluginManager.registerEvents(vouchers, plugin);
        pluginManager.registerEvents(menu, plugin);
        registerCommands();
        if (plugin.getMongoManager() != null) plugin.getMongoManager().onAvailable(() -> sync(this::loadMissing));
        Bukkit.getOnlinePlayers().forEach(this::load);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("[masakra-szafka] Szafka włączona: " + catalog.items(SzafkaItem.Kind.COSTUME).size() + " kostiumów, "
            + catalog.items(SzafkaItem.Kind.TALISMAN).size() + " talizmanów. Config: " + file.getPath());
    }

    public boolean reload() {
        SzafkaCatalog loaded = readCatalog();
        if (loaded == null) return false;
        catalog = loaded;
        visuals.reload(catalog);
        for (Player player : Bukkit.getOnlinePlayers()) {
            SzafkaProfile profile = profiles.get(player.getUniqueId());
            if (profile != null && normalize(profile)) save(player.getUniqueId(), profile);
            apply(player);
        }
        menu.refreshOpen();
        return true;
    }

    public void disable() {
        task.cancel();
        menu.closeAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            SzafkaPerks active = perks.get(player.getUniqueId());
            if (active != null) effects.clear(player, active, SzafkaPerks.NONE);
        }
        if (packetsActive) visuals.unregister();
        try {
            CompletableFuture.allOf(claims.values().stream().map(PendingClaim::future).toArray(CompletableFuture[]::new))
                .get(3, TimeUnit.SECONDS);
        } catch (Exception exception) {
            plugin.getLogger().warning("[masakra-szafka] Nie wszystkie bony zdążyły się zapisać: " + exception.getMessage());
        }
        List.copyOf(claims.keySet()).forEach(id -> finishClaim(id, true));
        pendingChanges.forEach((uuid, changes) -> storage.update(uuid, profile -> changes.forEach(change -> change.accept(profile))));
        pendingChanges.clear();
        storage.flush(3000L);
        profiles.clear();
        perks.clear();
        revisions.clear();
        nameTagSuffixes.clear();
    }

    public String nameTagSuffix(Player player) {
        return player == null ? "" : nameTagSuffixes.getOrDefault(player.getUniqueId(), "");
    }

    public double miningBonus(Player player, Location location) {
        double percent = perks(player).miningPercent();
        if (percent <= 0 || effectsBlocked(player.getLocation()) || effectsBlocked(location)) return 0;
        return percent / 100.0;
    }

    public boolean multipliesDrop(Material mined, ItemStack drop, Location location) {
        if (drop == null || drop.getMaxStackSize() <= 1 || drop.hasItemMeta()) return false;
        var generators = plugin.getGeneratorManager();
        if (generators != null && location != null && generators.findGeneratorAt(location) != null) return true;
        return catalog.isMiningBlock(mined) && drop.getType() != mined;
    }

    HollyCore plugin() {
        return plugin;
    }

    SzafkaCatalog catalog() {
        return catalog;
    }

    SzafkaProfile profile(UUID uuid) {
        return profiles.get(uuid);
    }

    int revision(UUID uuid) {
        return revisions.getOrDefault(uuid, 0);
    }

    SzafkaPerks perks(Player player) {
        return perks.getOrDefault(player.getUniqueId(), SzafkaPerks.NONE);
    }

    SzafkaItem activeCostume(SzafkaProfile profile, long now) {
        SzafkaItem costume = catalog.item(SzafkaItem.Kind.COSTUME, profile.costume());
        return costume != null && profile.owns(SzafkaItem.Kind.COSTUME, costume.id(), now) ? costume : null;
    }

    List<SzafkaItem> activeTalismans(SzafkaProfile profile, long now) {
        List<SzafkaItem> active = new ArrayList<>();
        for (String id : profile.talismans()) {
            SzafkaItem talisman = catalog.item(SzafkaItem.Kind.TALISMAN, id);
            if (talisman != null && profile.owns(SzafkaItem.Kind.TALISMAN, id, now) && active.size() < catalog.maxTalismans) active.add(talisman);
        }
        return active;
    }

    List<String> propertyLines(SzafkaItem item) {
        String format = catalog.text("gui.property-line", "&8↪ &7{LINE}");
        return item.properties().stream().map(line -> format.replace("{LINE}", line)).toList();
    }

    boolean effectsBlocked(Location location) {
        if (location == null || location.getWorld() == null) return true;
        String world = location.getWorld().getName();
        if (catalog.blockedWorlds.stream().anyMatch(world::equalsIgnoreCase)) return true;
        return !catalog.blockedRegions.isEmpty() && Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && inBlockedRegion(location);
    }

    private boolean inBlockedRegion(Location location) {
        var regions = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(location.getWorld()));
        if (regions == null) return false;
        for (String name : catalog.blockedRegions) {
            var region = regions.getRegion(name.toLowerCase(Locale.ROOT));
            if (region != null && (region.getId().equals("__global__") || region.contains(BukkitAdapter.asBlockVector(location)))) return true;
        }
        return false;
    }

    void toggle(Player player, SzafkaItem item) {
        UUID uuid = player.getUniqueId();
        SzafkaProfile profile = profiles.get(uuid);
        Map<String, String> values = Map.of("NAME", item.name(), "MAX", String.valueOf(catalog.maxTalismans));
        if (profile == null) {
            fail(player, "not-loaded", values);
            return;
        }
        if (!profile.owns(item.kind(), item.id(), System.currentTimeMillis())) {
            fail(player, "not-owned", values);
            return;
        }
        boolean normalized = normalize(profile);
        if (item.kind() == SzafkaItem.Kind.COSTUME) {
            if (profile.unequip(SzafkaItem.Kind.COSTUME, item.id())) {
                send(player, "costume-unequipped", values);
                catalog.play(player, "unequip");
            } else {
                profile.equipCostume(item.id());
                send(player, "costume-equipped", values);
                catalog.play(player, "equip");
            }
        } else if (profile.unequip(SzafkaItem.Kind.TALISMAN, item.id())) {
            send(player, "talisman-unequipped", values);
            catalog.play(player, "talisman-unequip");
        } else if (profile.equipTalisman(item.id(), catalog.maxTalismans)) {
            send(player, "talisman-equipped", values);
            catalog.play(player, "talisman-equip");
        } else {
            if (normalized) save(uuid, profile);
            fail(player, "talisman-slots-full", values);
            return;
        }
        save(uuid, profile);
        apply(player);
    }

    void redeem(Player player, EquipmentSlot hand, SzafkaVouchers.Voucher voucher) {
        if (!ClickCooldown.tryUse(player, "masakra-szafka-voucher", 400L)) return;
        UUID uuid = player.getUniqueId();
        SzafkaItem item = catalog.item(voucher.kind(), voucher.item());
        if (item == null) {
            fail(player, "voucher-unknown", Map.of());
            return;
        }
        if (!profiles.containsKey(uuid)) {
            fail(player, "not-loaded", Map.of());
            return;
        }
        if (!storage.available()) {
            fail(player, "storage-error", Map.of());
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        SzafkaVouchers.Voucher check = vouchers.read(held);
        if (check == null || !check.id().equals(voucher.id()) || claims.containsKey(voucher.id())) return;
        ItemStack returned = held.asOne();
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, held.getAmount() > 0 ? held : null);
        CompletableFuture<Boolean> future = storage.claimVoucher(voucher.id(), uuid, item, voucher.duration());
        claims.put(voucher.id(), new PendingClaim(uuid, player.getName(), item, voucher, returned, future));
        future.whenComplete((claimed, error) -> sync(() -> finishClaim(voucher.id(), false)));
    }

    private void finishClaim(UUID voucherId, boolean closing) {
        PendingClaim claim = claims.remove(voucherId);
        if (claim == null) return;
        CompletableFuture<Boolean> future = claim.future();
        Boolean claimed = future.isDone() && !future.isCompletedExceptionally() ? future.join() : null;
        Player online = closing ? null : Bukkit.getPlayer(claim.player());
        if (claimed == null) {
            plugin.getLogger().warning("[masakra-szafka] Nie zapisano bonu " + voucherId + " gracza " + claim.name() + ".");
            giveBack(claim.player(), claim.returned(), claim.voucher());
            if (online != null) fail(online, "storage-error", Map.of());
        } else if (!claimed) {
            plugin.getLogger().warning("[masakra-szafka] Odrzucono zduplikowany bon " + voucherId + " (" + claim.item().id() + ") od " + claim.name() + ".");
            if (online != null) fail(online, "voucher-used", Map.of());
        } else if (closing) {
            storage.update(claim.player(), profile -> applyGrant(profile, claim.item(), claim.voucher().duration()));
        } else {
            grant(claim.player(), claim.item(), claim.voucher().duration());
        }
    }

    private void grant(UUID uuid, SzafkaItem item, long duration) {
        Player player = Bukkit.getPlayer(uuid);
        SzafkaProfile profile = profiles.get(uuid);
        Consumer<SzafkaProfile> change = target -> applyGrant(target, item, duration);
        if (profile == null) {
            if (player != null) {
                pendingChanges.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(change);
                if (!loading.containsKey(uuid)) load(player);
            } else {
                storage.update(uuid, change).exceptionally(error -> {
                    plugin.getLogger().severe("[masakra-szafka] Nie zapisano " + item.id() + " dla " + uuid + ": " + error.getMessage());
                    return null;
                });
            }
            return;
        }
        long expiry = applyGrant(profile, item, duration);
        save(uuid, profile);
        if (player == null) return;
        apply(player);
        Map<String, String> values = Map.of("NAME", item.name(), "DURATION", catalog.formatDuration(duration), "EXPIRES", catalog.formatDate(expiry));
        send(player, "redeemed", values);
        ColorUtils.sendTitle(player, SzafkaCatalog.replace(catalog.text("titles.redeem-title", ""), values),
            SzafkaCatalog.replace(catalog.text("titles.redeem-subtitle", ""), values), 5, 50, 15);
        catalog.play(player, "redeem");
        menu.refreshOpen();
    }

    private long applyGrant(SzafkaProfile profile, SzafkaItem item, long duration) {
        long expiry = profile.grant(item.kind(), item.id(), duration, System.currentTimeMillis());
        if (item.kind() == SzafkaItem.Kind.COSTUME) profile.equipCostume(item.id());
        else profile.equipTalisman(item.id(), catalog.maxTalismans);
        return expiry;
    }

    private void giveBack(UUID uuid, ItemStack item, SzafkaVouchers.Voucher voucher) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            plugin.getLogger().severe("[masakra-szafka] Gracz " + uuid + " wyszedł przed zwrotem bonu " + voucher.id()
                + " (" + voucher.kind().label + " " + voucher.item() + ", " + voucher.duration() + " ms). Wydaj go ponownie ręcznie.");
            return;
        }
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private void load(Player player) {
        UUID uuid = player.getUniqueId();
        long token = ++loadSequence;
        loading.put(uuid, token);
        storage.load(uuid).whenComplete((profile, error) -> sync(() -> {
            if (!Objects.equals(loading.get(uuid), token)) return;
            loading.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online == null) return;
            if (error != null) {
                plugin.getLogger().warning("[masakra-szafka] Nie wczytano szafki " + online.getName() + ": " + error.getMessage());
                return;
            }
            profiles.put(uuid, profile);
            List<SzafkaProfile.Expired> expired = profile.purge(System.currentTimeMillis());
            boolean changed = !expired.isEmpty() | normalize(profile);
            List<Consumer<SzafkaProfile>> changes = pendingChanges.remove(uuid);
            if (changes != null) {
                changes.forEach(change -> change.accept(profile));
                changed = true;
            }
            if (changed) save(uuid, profile);
            apply(online);
            notifyExpired(online, expired);
        }));
    }

    private void loadMissing() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!profiles.containsKey(player.getUniqueId()) && !loading.containsKey(player.getUniqueId())) load(player);
        }
    }

    private boolean normalize(SzafkaProfile profile) {
        boolean changed = false;
        if (profile.costume() != null && catalog.item(SzafkaItem.Kind.COSTUME, profile.costume()) == null) {
            changed = profile.unequip(SzafkaItem.Kind.COSTUME, profile.costume());
        }
        for (String id : List.copyOf(profile.talismans())) {
            if (catalog.item(SzafkaItem.Kind.TALISMAN, id) == null) changed |= profile.unequip(SzafkaItem.Kind.TALISMAN, id);
        }
        int before = profile.talismans().size();
        profile.trimTalismans(catalog.maxTalismans);
        return changed || before != profile.talismans().size();
    }

    private void apply(Player player) {
        UUID uuid = player.getUniqueId();
        SzafkaProfile profile = profiles.get(uuid);
        long now = System.currentTimeMillis();
        SzafkaPerks combined = SzafkaPerks.NONE;
        StringJoiner symbols = new StringJoiner(catalog.text("nametag.separator", " "));
        if (profile != null) {
            SzafkaItem costume = activeCostume(profile, now);
            if (costume != null) combined = combined.plus(costume.perks());
            for (SzafkaItem talisman : activeTalismans(profile, now)) {
                combined = combined.plus(talisman.perks());
                if (!talisman.symbol().isBlank()) symbols.add(talisman.symbol());
            }
        }
        revisions.merge(uuid, 1, Integer::sum);
        SzafkaPerks previous = perks.put(uuid, combined);
        if (previous != null) effects.clear(player, previous, combined);
        effects.tick(player, combined);
        String suffix = catalog.nameTagEnabled && symbols.length() > 0
            ? catalog.text("nametag.format", " {SYMBOLS}").replace("{SYMBOLS}", symbols.toString()) : "";
        String previousSuffix = suffix.isEmpty() ? nameTagSuffixes.remove(uuid) : nameTagSuffixes.put(uuid, suffix);
        if (!suffix.equals(previousSuffix == null ? "" : previousSuffix) && plugin.getTablistManager() != null) {
            plugin.getTablistManager().refreshNameTag(player);
        }
        refreshVisual(player);
    }

    private void refreshVisual(Player player) {
        if (!packetsActive || !player.isOnline()) return;
        SzafkaProfile profile = profiles.get(player.getUniqueId());
        SzafkaItem costume = profile == null ? null : activeCostume(profile, System.currentTimeMillis());
        if (costume != null && catalog.hideWhenInvisible && player.hasPotionEffect(PotionEffectType.INVISIBILITY)) costume = null;
        visuals.update(player, costume);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean retry = ++ticks % 10 == 0 && storage.available();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            SzafkaProfile profile = profiles.get(uuid);
            if (profile == null) {
                if (retry && !loading.containsKey(uuid)) load(player);
                continue;
            }
            List<SzafkaProfile.Expired> expired = profile.purge(now);
            if (expired.isEmpty()) {
                effects.tick(player, perks(player));
                continue;
            }
            save(uuid, profile);
            apply(player);
            notifyExpired(player, expired);
        }
        menu.refreshOpen();
    }

    private void notifyExpired(Player player, List<SzafkaProfile.Expired> expired) {
        if (expired.isEmpty()) return;
        for (SzafkaProfile.Expired entry : expired) {
            SzafkaItem item = catalog.item(entry.kind(), entry.id());
            send(player, "expired", Map.of("NAME", item != null ? item.name() : entry.id()));
        }
        catalog.play(player, "expire");
    }

    private void save(UUID uuid, SzafkaProfile profile) {
        storage.save(uuid, profile.toDocument(uuid)).exceptionally(error -> {
            plugin.getLogger().severe("[masakra-szafka] Nie zapisano szafki " + uuid + ": " + error.getMessage());
            return null;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        loading.remove(uuid);
        profiles.remove(uuid);
        perks.remove(uuid);
        revisions.remove(uuid);
        nameTagSuffixes.remove(uuid);
        if (packetsActive) visuals.forget(player);
        List<Consumer<SzafkaProfile>> changes = pendingChanges.remove(uuid);
        if (changes != null) storage.update(uuid, profile -> changes.forEach(change -> change.accept(profile)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> refreshVisual(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshVisual(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player && PotionEffectType.INVISIBILITY.equals(event.getModifiedType())) {
            Bukkit.getScheduler().runTask(plugin, () -> refreshVisual(player));
        }
    }

    private void registerCommands() {
        PluginCommand open = plugin.getCommand("szafka");
        if (open != null) {
            open.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) {
                    send(sender, "only-player", Map.of());
                } else if (!profiles.containsKey(player.getUniqueId())) {
                    fail(player, "not-loaded", Map.of());
                } else {
                    menu.open(player);
                }
                return true;
            });
            open.setTabCompleter((sender, command, alias, args) -> List.of());
        }
        PluginCommand admin = plugin.getCommand("aszafka");
        if (admin != null) {
            admin.setExecutor(this);
            admin.setTabCompleter(this);
        }
        plugin.restorePluginCommands(List.of("szafka", "aszafka"));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            send(sender, "no-permission", Map.of());
            return true;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "reload" -> send(sender, reload() ? "reloaded" : "reload-failed", Map.of("FILE", file.getPath()));
            case "give" -> give(sender, args);
            case "zabierz", "take" -> take(sender, args);
            default -> send(sender, "usage", Map.of());
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        SzafkaItem.Kind kind = args.length < 4 ? null : SzafkaItem.Kind.parse(args[1]);
        if (kind == null) {
            send(sender, "usage", Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            send(sender, "player-offline", Map.of("PLAYER", args[2]));
            return;
        }
        SzafkaItem item = catalog.item(kind, args[3].toLowerCase(Locale.ROOT));
        if (item == null) {
            send(sender, "unknown-item", Map.of("ID", args[3]));
            return;
        }
        long duration = args.length > 4 ? SzafkaCatalog.parseDuration(args[4]) : item.defaultDuration();
        if (duration <= 0) {
            send(sender, "invalid-duration", Map.of("VALUE", args[4]));
            return;
        }
        int amount = 1;
        if (args.length > 5) {
            try {
                amount = Integer.parseInt(args[5]);
            } catch (NumberFormatException ignored) {
                amount = 0;
            }
            if (amount < 1 || amount > 64) {
                send(sender, "invalid-amount", Map.of("VALUE", args[5]));
                return;
            }
        }
        for (int index = 0; index < amount; index++) {
            target.getInventory().addItem(vouchers.create(catalog, item, duration)).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        }
        send(sender, "given", Map.of("AMOUNT", String.valueOf(amount), "NAME", item.name(),
            "DURATION", catalog.formatDuration(duration), "PLAYER", target.getName()));
    }

    private void take(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "usage", Map.of());
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "player-offline", Map.of("PLAYER", args[1]));
            return;
        }
        SzafkaProfile profile = profiles.get(target.getUniqueId());
        if (profile == null) {
            send(sender, "target-not-loaded", Map.of("PLAYER", target.getName()));
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        SzafkaItem item = catalog.find(id);
        Map<String, String> values = Map.of("NAME", item != null ? item.name() : id, "PLAYER", target.getName());
        SzafkaItem.Kind kind = profile.owned(SzafkaItem.Kind.COSTUME).containsKey(id) ? SzafkaItem.Kind.COSTUME
            : profile.owned(SzafkaItem.Kind.TALISMAN).containsKey(id) ? SzafkaItem.Kind.TALISMAN : null;
        if (kind == null || !profile.revoke(kind, id)) {
            send(sender, "target-not-owned", values);
            return;
        }
        save(target.getUniqueId(), profile);
        apply(target);
        menu.refreshOpen();
        send(sender, "removed", values);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) return List.of();
        List<String> options = new ArrayList<>();
        String action = args.length > 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) {
            options.addAll(List.of("give", "zabierz", "reload"));
        } else if (action.equals("give")) {
            SzafkaItem.Kind kind = SzafkaItem.Kind.parse(args[1]);
            switch (args.length) {
                case 2 -> options.addAll(List.of("kostium", "talizman"));
                case 3 -> Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
                case 4 -> {
                    if (kind != null) catalog.items(kind).forEach(item -> options.add(item.id()));
                }
                case 5 -> options.addAll(List.of("1d", "7d", "14d", "30d", "perm"));
                case 6 -> options.addAll(List.of("1", "5", "10"));
                default -> {
                }
            }
        } else if (action.equals("zabierz") || action.equals("take")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            } else if (args.length == 3) {
                Player target = Bukkit.getPlayerExact(args[1]);
                SzafkaProfile profile = target == null ? null : profiles.get(target.getUniqueId());
                if (profile != null) for (SzafkaItem.Kind kind : SzafkaItem.Kind.values()) options.addAll(profile.owned(kind).keySet());
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    private void send(CommandSender sender, String key, Map<String, String> values) {
        String message = SzafkaCatalog.replace(catalog.message(key), values);
        if (!message.isBlank()) sender.sendMessage(ColorUtils.colorizeComponent(message));
    }

    private void fail(Player player, String key, Map<String, String> values) {
        send(player, key, values);
        catalog.play(player, "error");
    }

    private void sync(Runnable runnable) {
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) runnable.run();
        else Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private SzafkaCatalog readCatalog() {
        try {
            if (!file.exists()) {
                Files.createDirectories(file.toPath().toAbsolutePath().getParent());
                try (InputStream input = Objects.requireNonNull(plugin.getResource(RESOURCE), "Brak domyślnego configu szafki")) {
                    Files.copy(input, file.toPath());
                }
            }
            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            if (addMissingDefaults(config)) config.save(file);
            return new SzafkaCatalog(config, plugin.getLogger());
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            plugin.getLogger().severe("[masakra-szafka] Nie można wczytać " + file.getAbsolutePath()
                + ". Plik nie został nadpisany — popraw YAML i użyj /aszafka reload. " + exception.getMessage());
            return null;
        }
    }

    private boolean addMissingDefaults(YamlConfiguration config) {
        YamlConfiguration defaults = bundled();
        boolean changed = false;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || ITEM_SECTIONS.contains(path.split("\\.", 2)[0]) || config.contains(path)) continue;
            config.set(path, defaults.get(path));
            changed = true;
        }
        return changed;
    }

    private record PendingClaim(UUID player, String name, SzafkaItem item, SzafkaVouchers.Voucher voucher, ItemStack returned,
                                CompletableFuture<Boolean> future) {
    }

    private YamlConfiguration bundled() {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(RESOURCE)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Nie można odczytać domyślnego configu szafki", exception);
        }
    }
}
