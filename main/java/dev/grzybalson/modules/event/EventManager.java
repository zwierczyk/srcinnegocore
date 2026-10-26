package dev.grzybalson.modules.event;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EventManager {

    public static final String TICKET_NBT = "dungeon_ticket";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final HollyCore plugin;
    private final File configFile;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, DungeonParty> partiesByCode = new ConcurrentHashMap<>();
    private final Map<UUID, String> partyByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> typedAnvilCodes = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> readyCountdowns = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, org.bukkit.Location>> readyLiftOrigins = new ConcurrentHashMap<>();
    private boolean joinAnvilPacketListenerRegistered;
    private YamlConfiguration config;
    private dev.grzybalson.modules.event.dungeon.DungeonSessionManager dungeonSessions;

    public EventManager(HollyCore plugin) {
        this.plugin = plugin;
        dev.grzybalson.internal.HollyIntegrityGuard.requireBound(plugin, "scheduler:queue");
        this.configFile = new File(plugin.getDataFolder(), "guis/event.yml");
        load();
    }

    public void setDungeonSessions(dev.grzybalson.modules.event.dungeon.DungeonSessionManager dungeonSessions) {
        this.dungeonSessions = dungeonSessions;
    }

    public dev.grzybalson.modules.event.dungeon.DungeonSessionManager getDungeonSessions() {
        return dungeonSessions;
    }

    public HollyCore getPlugin() {
        return plugin;
    }

    public YamlConfiguration getConfig() {
        load();
        return config;
    }

    public void openMain(Player player) {
        try {
            load();
            DungeonParty party = getParty(player);
            if (party != null && !party.isStarted()) {
                playOpen(player);
                new DungeonLobbyGUI(plugin, player, this, party).open();
                return;
            }
            playOpen(player);
            new EventGUI(plugin, player, this).open();
        } catch (Throwable throwable) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Blad otwierania /event dla " + player.getName(), throwable);
            player.sendMessage(ColorUtils.error("Nie udalo sie otworzyc menu event. Sprawdz logi."));
        }
    }

    public void openCreate(Player player) {
        load();
        if (getParty(player) != null) {
            playError(player);
            send(player, "already-in-party");
            return;
        }
        if (!hasCleanInventoryForDungeon(player)) {
            playError(player);
            send(player, "inventory-not-empty");
            return;
        }
        playOpen(player);
        new DungeonCreateGUI(plugin, player, this).open();
    }

    public void openBossRewards(Player player) {
        load();
        playOpen(player);
        new DungeonBossRewardsMenuGUI(plugin, player, this).open();
    }

    public void openPublic(Player player) {
        load();
        if (getParty(player) != null) {
            playError(player);
            send(player, "already-in-party");
            return;
        }
        if (!hasCleanInventoryForDungeon(player)) {
            playError(player);
            send(player, "inventory-not-empty");
            return;
        }
        playOpen(player);
        new DungeonPublicGUI(plugin, player, this).open();
    }

    public void openJoinAnvil(Player player) {
        load();
        ensureJoinAnvilPacketListener();
        if (getParty(player) != null) {
            playError(player);
            send(player, "already-in-party");
            return;
        }
        if (!hasCleanInventoryForDungeon(player)) {
            playError(player);
            send(player, "inventory-not-empty");
            return;
        }
        typedAnvilCodes.remove(player.getUniqueId());
        DungeonJoinAnvilHolder holder = new DungeonJoinAnvilHolder();
        var inventory = Bukkit.createInventory(
            holder,
            org.bukkit.event.inventory.InventoryType.ANVIL,
            ColorUtils.colorizeComponent(config.getString("join-anvil-title", "&8Kod party"))
        );
        holder.setInventory(inventory);
        inventory.setItem(0, createJoinInputItem());
        playOpen(player);
        player.openInventory(inventory);
    }

    public void ensureJoinAnvilPacketListener() {
        if (joinAnvilPacketListenerRegistered) {
            return;
        }
        try {
            if (com.github.retrooper.packetevents.PacketEvents.getAPI() == null) {
                return;
            }
            com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager().registerListener(
                new com.github.retrooper.packetevents.event.PacketListenerAbstract(
                    com.github.retrooper.packetevents.event.PacketListenerPriority.NORMAL
                ) {
                    @Override
                    public void onPacketReceive(com.github.retrooper.packetevents.event.PacketReceiveEvent event) {
                        if (event.getPacketType() != com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.NAME_ITEM) {
                            return;
                        }
                        Object rawPlayer = event.getPlayer();
                        if (!(rawPlayer instanceof Player player)) {
                            return;
                        }
                        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof DungeonJoinAnvilHolder anvilHolder)) {
                            return;
                        }
                        com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem packet =
                            new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientNameItem(event);
                        String code = sanitizePartyCode(packet.getItemName());
                        typedAnvilCodes.put(player.getUniqueId(), code);
                        anvilHolder.setLastCode(code);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof DungeonJoinAnvilHolder openHolder)) {
                                return;
                            }
                            org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
                            if (top instanceof org.bukkit.inventory.AnvilInventory anvilInventory) {
                                anvilInventory.setRepairCost(0);
                            }
                            String latest = typedAnvilCodes.getOrDefault(player.getUniqueId(), code);
                            openHolder.setLastCode(latest);
                            top.setItem(2, createJoinResultItem(latest.isEmpty() ? "..." : latest));
                        });
                    }
                }
            );
            joinAnvilPacketListenerRegistered = true;
        } catch (Throwable ignored) {
        }
    }

    public String getTypedJoinCode(UUID uuid) {
        return typedAnvilCodes.getOrDefault(uuid, "");
    }

    public void clearTypedJoinCode(UUID uuid) {
        if (uuid != null) {
            typedAnvilCodes.remove(uuid);
        }
    }

    public ItemStack createJoinInputItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Bez displayName — Paper wtedy nie prefilluje pola rename.
            List<Component> lore = new ArrayList<>();
            for (String line : config.getStringList("join-anvil.input-lore")) {
                lore.add(noItalic(ColorUtils.colorizeComponent(line)));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createJoinResultItem(String code) {
        String safe = sanitizePartyCode(code);
        if (safe.isEmpty()) {
            safe = "...";
        }
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(ColorUtils.colorizeComponent(
                config.getString("join-anvil.result-name", "&aDolacz: &#FFD200{CODE}").replace("{CODE}", safe)
            )));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        if (!"...".equals(safe)) {
            NBTUtils.setString(item, "dungeon_join_code", safe);
        }
        return item;
    }

    public String sanitizePartyCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = raw.trim().toUpperCase(Locale.ROOT)
            .replace('Ł', 'L')
            .replaceAll("[^A-Z0-9]", "");
        if (cleaned.isEmpty() || "...".equals(cleaned) || "KODPARTY".equals(cleaned) || "WPISZKOD".equals(cleaned)) {
            return "";
        }
        return cleaned;
    }

    public boolean createParty(Player leader, int maxSize, boolean publicParty, int ticketCost) {
        if (getParty(leader) != null) {
            playError(leader);
            send(leader, "already-in-party");
            return false;
        }
        if (!hasCleanInventoryForDungeon(leader)) {
            playError(leader);
            send(leader, "inventory-not-empty");
            return false;
        }
        int size = Math.max(1, Math.min(5, maxSize));
        int tickets = Math.max(0, Math.min(5, ticketCost));
        if (tickets > 0 && countTickets(leader) < tickets) {
            playError(leader);
            send(leader, "not-enough-tickets", Map.of("AMOUNT", String.valueOf(tickets)));
            return false;
        }

        if (tickets > 0 && !removeTickets(leader, tickets)) {
            playError(leader);
            send(leader, "not-enough-tickets", Map.of("AMOUNT", String.valueOf(tickets)));
            return false;
        }

        String code = generateUniqueCode();
        DungeonParty party = new DungeonParty(code, leader.getUniqueId(), leader.getName(), size, publicParty, tickets);
        partiesByCode.put(code, party);
        partyByPlayer.put(leader.getUniqueId(), code);

        playSuccess(leader);
        send(leader, "party-created", Map.of(
            "CODE", code,
            "SIZE", String.valueOf(size),
            "TICKETS", tickets > 0 ? String.valueOf(tickets) : "Darmowe",
            "VISIBILITY", publicParty ? "Publiczne" : "Prywatne"
        ));
        if (tickets > 0) {
            send(leader, "ticket-taken", Map.of("AMOUNT", String.valueOf(tickets)));
        }
        playOpen(leader);
        new DungeonLobbyGUI(plugin, leader, this, party).open();
        return true;
    }

    public boolean joinPartyByCode(Player player, String rawCode) {
        String code = sanitizePartyCode(rawCode);
        if (code.isEmpty()) {
            playError(player);
            send(player, "invalid-code");
            return false;
        }
        DungeonParty party = partiesByCode.get(code);
        if (party == null || party.isStarted()) {
            playError(player);
            send(player, "party-not-found", Map.of("CODE", code));
            return false;
        }
        return joinParty(player, party);
    }

    public boolean joinParty(Player player, DungeonParty party) {
        if (party == null || party.isStarted()) {
            playError(player);
            send(player, "party-not-found", Map.of("CODE", "?"));
            return false;
        }
        if (getParty(player) != null) {
            playError(player);
            send(player, "already-in-party");
            return false;
        }
        if (!hasCleanInventoryForDungeon(player)) {
            playError(player);
            send(player, "inventory-not-empty");
            return false;
        }
        if (party.isFull()) {
            playError(player);
            send(player, "party-full");
            return false;
        }
        if (party.getTicketCost() > 0) {
            if (countTickets(player) < party.getTicketCost()) {
                playError(player);
                send(player, "not-enough-tickets", Map.of("AMOUNT", String.valueOf(party.getTicketCost())));
                return false;
            }
            if (!removeTickets(player, party.getTicketCost())) {
                playError(player);
                send(player, "not-enough-tickets", Map.of("AMOUNT", String.valueOf(party.getTicketCost())));
                return false;
            }
        }
        if (!party.addMember(player.getUniqueId(), player.getName())) {
            if (party.getTicketCost() > 0) {
                giveTicketsSilent(player, party.getTicketCost());
            }
            playError(player);
            send(player, "party-full");
            return false;
        }
        partyByPlayer.put(player.getUniqueId(), party.getCode());
        playSuccess(player);
        send(player, "party-joined", Map.of("LEADER", party.getLeaderName(), "CODE", party.getCode()));
        if (party.getTicketCost() > 0) {
            send(player, "ticket-taken", Map.of("AMOUNT", String.valueOf(party.getTicketCost())));
        }
        broadcast(party, "member-joined", Map.of("PLAYER", player.getName()), player.getUniqueId());
        broadcastSound(party, true, player.getUniqueId());
        refreshLobby(party);
        playOpen(player);
        new DungeonLobbyGUI(plugin, player, this, party).open();
        return true;
    }

    public void leaveParty(Player player) {
        DungeonParty party = getParty(player);
        if (party == null) {
            playError(player);
            send(player, "not-in-party");
            return;
        }
        if (party.isLeader(player.getUniqueId())) {
            disbandParty(party, true);
            return;
        }
        party.removeMember(player.getUniqueId());
        partyByPlayer.remove(player.getUniqueId());
        refundPartyTicket(player, party);
        playSuccess(player);
        send(player, "left-party");
        broadcast(party, "member-left", Map.of("PLAYER", player.getName()), null);
        broadcastSound(party, false, null);
        cancelReadyCountdown(party, true);
        party.resetReady();
        refreshLobby(party);
        openMain(player);
    }

    /** Lider: Q na glowke w lobby = wyrzuc z party. */
    public void kickFromParty(Player leader, UUID targetId) {
        DungeonParty party = getParty(leader);
        if (party == null || party.isStarted() || !party.isLeader(leader.getUniqueId())) {
            playError(leader);
            return;
        }
        if (targetId == null || targetId.equals(leader.getUniqueId()) || !party.isMember(targetId)) {
            playError(leader);
            return;
        }
        String targetName = party.getMembers().get(targetId).getName();
        party.removeMember(targetId);
        partyByPlayer.remove(targetId);
        cancelReadyCountdown(party, true);
        party.resetReady();

        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            refundPartyTicket(target, party);
            target.closeInventory();
            playError(target);
            send(target, "kicked-from-party", Map.of("LEADER", leader.getName()));
            ColorUtils.sendTitle(target,
                "<gradient:#FF5C5C:#FF9B00><bold>WYRZUCONO</bold></gradient></shadow>",
                "Lider wyrzucil Cie z party</shadow>",
                8, 55, 12);
        }
        playSuccess(leader);
        send(leader, "member-kicked", Map.of("PLAYER", targetName));
        broadcast(party, "member-left", Map.of("PLAYER", targetName), null);
        broadcastSound(party, false, null);
        refreshLobby(party);
    }

    public void disbandParty(DungeonParty party, boolean notify) {
        if (party == null) {
            return;
        }
        cancelReadyCountdown(party, false);
        partiesByCode.remove(party.getCode());
        for (DungeonParty.Member member : party.getMemberList()) {
            partyByPlayer.remove(member.getUuid());
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online == null || !online.isOnline()) {
                continue;
            }
            if (!party.isStarted()) {
                refundPartyTicket(online, party);
            }
            if (notify) {
                if (party.isLeader(member.getUuid())) {
                    playSuccess(online);
                    send(online, "disbanded");
                } else {
                    playError(online);
                    send(online, "kicked-disband");
                    ColorUtils.sendTitle(online,
                        "<gradient:#FF5C5C:#FF9B00><bold>PARTY USUNIETE</bold></gradient></shadow>",
                        "Lider zamknal lobby</shadow>",
                        8, 50, 12);
                }
            }
            online.closeInventory();
        }
    }

    private void refundPartyTicket(Player player, DungeonParty party) {
        if (player == null || party == null || party.isStarted() || party.getTicketCost() <= 0) {
            return;
        }
        giveTicketsSilent(player, party.getTicketCost());
        send(player, "ticket-returned", Map.of("AMOUNT", String.valueOf(party.getTicketCost())));
    }

    public void toggleReady(Player player) {
        DungeonParty party = getParty(player);
        if (party == null || party.isStarted()) {
            playError(player);
            send(player, "not-in-party");
            return;
        }
        if (!hasCleanInventoryForDungeon(player)) {
            playError(player);
            send(player, "inventory-not-empty");
            party.setReady(player.getUniqueId(), false);
            refreshLobby(party);
            return;
        }

        boolean next = !party.isReady(player.getUniqueId());
        party.setReady(player.getUniqueId(), next);
        if (next) {
            playSuccess(player);
        } else {
            playError(player);
            cancelReadyCountdown(party, true);
        }
        send(player, next ? "ready-on" : "ready-off");
        refreshLobby(party);

        if (party.allReady()) {
            beginReadyCountdown(party);
        } else if (next) {
            broadcast(party, "waiting-ready", Map.of(
                "READY", String.valueOf(party.getReadyCount()),
                "TOTAL", String.valueOf(party.getMemberCount())
            ), null);
        }
    }

    private void beginReadyCountdown(DungeonParty party) {
        if (party == null || party.isStarted()) {
            return;
        }
        cancelReadyCountdown(party, false);

        Map<UUID, org.bukkit.Location> origins = new ConcurrentHashMap<>();
        for (DungeonParty.Member member : party.getMemberList()) {
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online == null || !online.isOnline()) {
                continue;
            }
            online.closeInventory();
            origins.put(online.getUniqueId(), online.getLocation().clone());
            online.setGameMode(org.bukkit.GameMode.SPECTATOR);
            online.setAllowFlight(true);
            online.setFlying(true);
            online.setFlySpeed(0.0F);
            online.setGravity(false);
            online.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            ColorUtils.sendTitle(online,
                "<gradient:#00C1FF:#00FFC1><bold>START</bold></gradient></shadow>",
                "Lecimy w gore — start za &e5s</shadow>",
                0, 25, 5);
            online.playSound(online.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.1F);
        }
        readyLiftOrigins.put(party.getCode(), origins);
        broadcast(party, "ready-countdown", Map.of("SECONDS", "5"), null);

        // Soft start clone juz w trakcie lotu — mniej czekania na koncu.
        if (dungeonSessions != null) {
            dungeonSessions.preloadClone(party);
        }

        final int totalTicks = 100; // 5s
        final int[] tick = {0};
        final int[] lastSecond = {5};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (party.isStarted() || !partiesByCode.containsKey(party.getCode()) || !party.allReady()) {
                cancelReadyCountdown(party, false);
                return;
            }
            int t = tick[0]++;
            double progress = Math.min(1.0D, t / (double) totalTicks);
            double eased = progress * progress * (3.0D - 2.0D * progress); // smoothstep
            int secondsLeft = Math.max(1, (int) Math.ceil((totalTicks - t) / 20.0D));
            if (secondsLeft != lastSecond[0] && t > 0) {
                lastSecond[0] = secondsLeft;
                for (DungeonParty.Member member : party.getMemberList()) {
                    Player online = Bukkit.getPlayer(member.getUuid());
                    if (online == null || !online.isOnline()) {
                        continue;
                    }
                    ColorUtils.sendTitle(online,
                        "<gradient:#00C1FF:#00FFC1><bold>START</bold></gradient></shadow>",
                        "Start za <gradient:#FFD900:#FF9B00>" + secondsLeft + "s</gradient></shadow>",
                        0, 25, 5);
                    online.playSound(online.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F,
                        secondsLeft <= 2 ? 1.6F : 1.1F);
                }
                broadcast(party, "ready-countdown", Map.of("SECONDS", String.valueOf(secondsLeft)), null);
            }

            Map<UUID, org.bukkit.Location> liftOrigins = readyLiftOrigins.get(party.getCode());
            if (liftOrigins != null) {
                for (Map.Entry<UUID, org.bukkit.Location> entry : liftOrigins.entrySet()) {
                    Player online = Bukkit.getPlayer(entry.getKey());
                    if (online == null || !online.isOnline()) {
                        continue;
                    }
                    org.bukkit.Location base = entry.getValue();
                    org.bukkit.Location want = base.clone().add(0.0D, eased * 48.0D, 0.0D);
                    want.setYaw(base.getYaw());
                    want.setPitch((float) (12.0D + eased * 68.0D));
                    smoothSpectatorChase(online, want, 0.55D);
                }
            }

            if (t >= totalTicks) {
                BukkitTask self = readyCountdowns.remove(party.getCode());
                if (self != null) {
                    self.cancel();
                }
                readyLiftOrigins.remove(party.getCode());
                startDungeonPlaceholder(party);
            }
        }, 1L, 1L);
        readyCountdowns.put(party.getCode(), task);
    }

    /** Client interpolates velocity (plynne ~wysokie FPS), teleport = 20fps chop. */
    public static void smoothSpectatorChase(Player player, org.bukkit.Location want, double chase) {
        if (player == null || want == null || want.getWorld() == null) {
            return;
        }
        if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.0F);
        player.setGravity(false);
        org.bukkit.Location cur = player.getLocation();
        if (cur.getWorld() == null || !cur.getWorld().equals(want.getWorld())) {
            player.teleport(want);
            return;
        }
        org.bukkit.util.Vector delta = want.toVector().subtract(cur.toVector());
        double dist = delta.length();
        if (dist < 0.03D) {
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        } else {
            // Cap speed so nie przelatuje targetu.
            double speed = Math.min(dist * chase, 2.8D);
            player.setVelocity(delta.normalize().multiply(speed));
        }
        try {
            player.setRotation(want.getYaw(), want.getPitch());
        } catch (Throwable ignored) {
            cur.setYaw(want.getYaw());
            cur.setPitch(want.getPitch());
            player.teleport(cur);
        }
    }

    private void cancelReadyCountdown(DungeonParty party, boolean notify) {
        if (party == null) {
            return;
        }
        BukkitTask task = readyCountdowns.remove(party.getCode());
        if (task != null) {
            task.cancel();
            if (notify) {
                broadcast(party, "ready-countdown-cancel", Map.of(), null);
            }
        }
        Map<UUID, org.bukkit.Location> origins = readyLiftOrigins.remove(party.getCode());
        if (origins != null && !party.isStarted()) {
            for (Map.Entry<UUID, org.bukkit.Location> entry : origins.entrySet()) {
                Player online = Bukkit.getPlayer(entry.getKey());
                if (online == null || !online.isOnline()) {
                    continue;
                }
                online.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                online.setGravity(true);
                online.teleport(entry.getValue());
                online.setGameMode(org.bukkit.GameMode.ADVENTURE);
                online.setAllowFlight(false);
                online.setFlying(false);
                online.setFlySpeed(0.1F);
            }
            if (dungeonSessions != null) {
                dungeonSessions.cancelPreload(party);
            }
            refreshLobby(party);
        }
    }

    private void startDungeonPlaceholder(DungeonParty party) {
        if (dungeonSessions == null) {
            broadcast(party, "dungeon-started", Map.of(), null);
            return;
        }
        dungeonSessions.startFromParty(party);
    }

    public void handleQuit(Player player) {
        if (dungeonSessions != null && dungeonSessions.getByPlayer(player.getUniqueId()) != null) {
            // DungeonGameplayListener zarzadza quit/rejoin w trakcie runu.
            return;
        }
        DungeonParty party = getParty(player);
        if (party == null) {
            return;
        }
        if (party.isStarted()) {
            return;
        }
        if (party.isLeader(player.getUniqueId())) {
            disbandParty(party, false);
            return;
        }
        party.removeMember(player.getUniqueId());
        partyByPlayer.remove(player.getUniqueId());
        refundPartyTicket(player, party);
        cancelReadyCountdown(party, true);
        party.resetReady();
        broadcast(party, "member-left", Map.of("PLAYER", player.getName()), null);
        refreshLobby(party);
    }

    public DungeonParty getParty(Player player) {
        return getParty(player.getUniqueId());
    }

    public DungeonParty getParty(UUID uuid) {
        String code = partyByPlayer.get(uuid);
        if (code == null) {
            return null;
        }
        return partiesByCode.get(code);
    }

    public Collection<DungeonParty> getPublicParties() {
        List<DungeonParty> list = new ArrayList<>();
        for (DungeonParty party : partiesByCode.values()) {
            if (party.isPublicParty() && !party.isStarted() && !party.isFull()) {
                list.add(party);
            }
        }
        return list;
    }

    public void refreshLobby(DungeonParty party) {
        if (party == null) {
            return;
        }
        for (DungeonParty.Member member : party.getMemberList()) {
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online == null || !online.isOnline()) {
                continue;
            }
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof DungeonLobbyGUI) {
                new DungeonLobbyGUI(plugin, online, this, party).open();
            }
        }
    }

    public boolean hasCleanInventoryForDungeon(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) {
            if (!isAllowedDungeonItem(item)) {
                return false;
            }
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        ItemStack offhand = inventory.getItemInOffHand();
        return isAllowedDungeonItem(offhand);
    }

    private boolean isAllowedDungeonItem(ItemStack item) {
        return item == null || item.getType().isAir() || isDungeonTicket(item);
    }

    public ItemStack createTicket(int amount) {
        load();
        ItemStack item = null;
        String serialized = config.getString("ticket.serialized", "");
        if (serialized != null && !serialized.isBlank()) {
            item = dev.grzybalson.utils.ItemSerializer.deserializeItem(serialized);
        }
        if (item == null || item.getType().isAir()) {
            Material material = Material.matchMaterial(config.getString("ticket.material", "PAPER"));
            if (material == null) {
                material = Material.PAPER;
            }
            item = new ItemStack(material, 1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(noItalic(ColorUtils.colorizeComponent(config.getString("ticket.name", "&d&lWEJSCIOWKA DUNGEON"))));
                List<Component> lore = new ArrayList<>();
                for (String line : config.getStringList("ticket.lore")) {
                    lore.add(noItalic(ColorUtils.colorizeComponent(line)));
                }
                meta.lore(lore);
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        } else {
            item = item.clone();
        }
        item.setAmount(Math.max(1, amount));
        NBTUtils.setString(item, TICKET_NBT, "1");
        return item;
    }

    /**
     * Bierze item z ręki i ustawia go jako wejsciowke dungeona (typ, CMD, lore, meta).
     */
    public boolean setTicketFromHand(ItemStack source) {
        if (source == null || source.getType().isAir()) {
            return false;
        }
        load();
        ItemStack template = source.clone();
        template.setAmount(1);
        NBTUtils.setString(template, TICKET_NBT, "1");

        config.set("ticket.material", template.getType().name());
        ItemMeta meta = template.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                try {
                    var display = meta.displayName();
                    if (display != null) {
                        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(display);
                        config.set("ticket.name", "&d&l" + plain);
                    }
                } catch (Throwable ignored) {
                    config.set("ticket.name", config.getString("ticket.name", "&d&lWEJSCIOWKA DUNGEON"));
                }
            }
            if (meta.hasCustomModelData()) {
                config.set("ticket.custom-model-data", meta.getCustomModelData());
            }
        }
        config.set("ticket.serialized", dev.grzybalson.utils.ItemSerializer.serializeItem(template));
        try {
            config.save(configFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("Nie udalo sie zapisac wejsciowki dungeona: " + exception.getMessage());
            return false;
        }
        return true;
    }

    public boolean isDungeonTicket(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String marker = NBTUtils.getString(item, TICKET_NBT);
        return marker != null && !marker.isBlank();
    }

    public int countTickets(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDungeonTicket(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public boolean removeTickets(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isDungeonTicket(item)) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(i, null);
            }
            remaining -= take;
        }
        return remaining <= 0;
    }

    public void giveTickets(Player target, int amount) {
        giveTickets(target, amount, true);
    }

    public void giveTicketsSilent(Player target, int amount) {
        giveTickets(target, amount, false);
    }

    public void giveTickets(Player target, int amount, boolean notify) {
        if (amount <= 0) {
            return;
        }
        ItemStack ticket = createTicket(amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(ticket);
        for (ItemStack left : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), left);
        }
        if (notify) {
            send(target, "ticket-received", Map.of("AMOUNT", String.valueOf(amount)));
        }
    }

    public void send(Player player, String key) {
        send(player, key, Map.of());
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        load();
        String raw = config.getString("messages." + key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        player.sendMessage(ColorUtils.colorizeComponent(raw));
    }

    public void broadcast(DungeonParty party, String key, Map<String, String> placeholders, UUID except) {
        for (DungeonParty.Member member : party.getMemberList()) {
            if (except != null && except.equals(member.getUuid())) {
                continue;
            }
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online != null && online.isOnline()) {
                send(online, key, placeholders);
            }
        }
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder builder = new StringBuilder(5);
            for (int i = 0; i < 5; i++) {
                builder.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String code = builder.toString();
            if (!partiesByCode.containsKey(code)) {
                return code;
            }
        }
        return UUID.randomUUID().toString().substring(0, 5).toUpperCase(Locale.ROOT);
    }

    public void playClick(Player player) {
        if (plugin.getSoundManager() != null) {
            plugin.getSoundManager().playGuiClick(player);
        }
    }

    public void playOpen(Player player) {
        if (plugin.getSoundManager() != null) {
            plugin.getSoundManager().playGuiOpen(player);
        }
    }

    public void playSuccess(Player player) {
        if (plugin.getSoundManager() != null) {
            plugin.getSoundManager().playSuccess(player);
        }
    }

    public void playError(Player player) {
        if (plugin.getSoundManager() != null) {
            plugin.getSoundManager().playError(player);
        }
    }

    private void broadcastSound(DungeonParty party, boolean join, UUID except) {
        for (DungeonParty.Member member : party.getMemberList()) {
            if (except != null && except.equals(member.getUuid())) {
                continue;
            }
            Player online = Bukkit.getPlayer(member.getUuid());
            if (online == null || !online.isOnline() || plugin.getSoundManager() == null) {
                continue;
            }
            if (join) {
                plugin.getSoundManager().playClanJoin(online);
            } else {
                plugin.getSoundManager().playClanLeave(online);
            }
        }
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public void reload() {
        load();
        if (dungeonSessions != null) {
            dungeonSessions.reloadSettings();
        }
    }

    private void load() {
        if (!configFile.exists()) {
            plugin.saveResource("guis/event.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }
}
