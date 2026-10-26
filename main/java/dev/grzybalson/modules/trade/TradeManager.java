package dev.grzybalson.modules.trade;

import dev.grzybalson.HollyCore;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TradeManager {

    private record TradeRequest(UUID requester, UUID target, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final HollyCore plugin;
    private final TradeConfig config;
    private final Map<UUID, TradeRequest> pendingByTarget = new HashMap<>();
    private final Map<UUID, TradeRequest> pendingByRequester = new HashMap<>();
    private final Map<UUID, TradeSession> sessionsByPlayer = new HashMap<>();
    private final Map<UUID, TradeGui> openTradeGuis = new HashMap<>();
    private final Map<UUID, TradeWithdrawGui> withdrawGuis = new HashMap<>();
    private BukkitTask cleanupTask;

    public TradeManager(HollyCore plugin) {
        this.plugin = plugin;
        this.config = new TradeConfig(plugin);
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCleanup, 20L, 20L);
    }

    public TradeConfig getConfig() {
        return config;
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        cancelAllTrades("shutdown");
        pendingByTarget.clear();
        pendingByRequester.clear();
    }

    /**
     * Anuluje wszystkie aktywne trade'y i synchronicznie zwraca oferty do EQ / na ziemię.
     * Używane przy restarcie / ItemSync / śmierci — bez deferred withdraw (dupe/loss).
     */
    public void cancelAllTrades(String reason) {
        // Ta sama sesja siedzi w mapie pod dwoma kluczami — przetwarzamy ją tylko raz.
        Set<TradeSession> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TradeSession session : new HashMap<>(sessionsByPlayer).values()) {
            if (session == null || session.isCompleting()) {
                continue;
            }
            if (!seen.add(session)) {
                continue;
            }
            forceCancelSession(session, reason);
        }
        pendingByTarget.clear();
        pendingByRequester.clear();
    }

    public void cancelTradeFor(UUID uuid, String reason) {
        if (uuid == null) {
            return;
        }
        TradeSession session = sessionsByPlayer.get(uuid);
        if (session != null && !session.isCompleting()) {
            forceCancelSession(session, reason);
        }
    }

    private void forceCancelSession(TradeSession session, String reason) {
        List<ItemStack> itemsA = TradeGui.collectOfferItems(session.offerFor(session.getPlayerA()));
        List<ItemStack> itemsB = TradeGui.collectOfferItems(session.offerFor(session.getPlayerB()));
        clearOffer(session.offerFor(session.getPlayerA()));
        clearOffer(session.offerFor(session.getPlayerB()));
        removeSession(session);

        Player a = Bukkit.getPlayer(session.getPlayerA());
        Player b = Bukkit.getPlayer(session.getPlayerB());
        returnOfferSync(a, itemsA);
        returnOfferSync(b, itemsB);
        if (a != null && a.isOnline()) {
            try {
                a.closeInventory();
            } catch (Throwable ignored) {
            }
            if (reason != null && !reason.isBlank()) {
                msg(a, "&c✕ &8Ξ &cTrade anulowany (&e" + reason + "&c). Itemy wróciły.");
            }
        }
        if (b != null && b.isOnline()) {
            try {
                b.closeInventory();
            } catch (Throwable ignored) {
            }
            if (reason != null && !reason.isBlank()) {
                msg(b, "&c✕ &8Ξ &cTrade anulowany (&e" + reason + "&c). Itemy wróciły.");
            }
        }
    }

    private void returnOfferSync(Player player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (player != null && player.isOnline()) {
            deliverItems(player, items);
            return;
        }
        // Offline: nie ma gdzie wrócić — drop pomijamy (brak lokalizacji).
    }

    public void reload() {
        config.reload();
    }

    public boolean isTrading(UUID uuid) {
        return sessionsByPlayer.containsKey(uuid);
    }

    public TradeSession getSession(UUID uuid) {
        return sessionsByPlayer.get(uuid);
    }

    public void tryTrade(Player initiator, Player target) {
        if (plugin.getRestartManager() != null && plugin.getRestartManager().isRestarting()) {
            msg(initiator, "&c✕ &8Ξ &cTrade jest zablokowany podczas restartu serwera!");
            return;
        }
        if (initiator.getUniqueId().equals(target.getUniqueId())) {
            msg(initiator, config.format("self"));
            return;
        }
        if (isTrading(initiator.getUniqueId()) || isTrading(target.getUniqueId())) {
            msg(initiator, isTrading(initiator.getUniqueId()) ? config.format("you-trading") : config.format("already-trading"));
            return;
        }
        if (!canReach(initiator, target)) {
            msg(initiator, config.format("too-far"));
            return;
        }

        TradeRequest incoming = pendingByTarget.get(initiator.getUniqueId());
        if (incoming != null && incoming.expired()) {
            clearRequest(initiator.getUniqueId(), incoming.requester());
            incoming = null;
        }
        if (incoming != null && incoming.requester().equals(target.getUniqueId())) {
            clearRequest(initiator.getUniqueId(), target.getUniqueId());
            startSession(target, initiator);
            return;
        }

        TradeRequest outgoing = pendingByRequester.get(initiator.getUniqueId());
        if (outgoing != null && outgoing.expired()) {
            clearRequest(outgoing.target(), initiator.getUniqueId());
            outgoing = null;
        }
        if (outgoing != null && outgoing.target().equals(target.getUniqueId())) {
            msg(initiator, config.format("pending-wait", "{PLAYER}", target.getName()));
            return;
        }
        if (outgoing != null) {
            msg(initiator, config.format("pending"));
            return;
        }

        long expires = System.currentTimeMillis() + config.requestTimeoutSeconds() * 1000L;
        TradeRequest request = new TradeRequest(initiator.getUniqueId(), target.getUniqueId(), expires);
        // Nadpisujemy prośbę innego gracza — usuń jego osierocony wpis, inaczej nigdy nie wygaśnie.
        TradeRequest previous = pendingByTarget.get(target.getUniqueId());
        if (previous != null) {
            pendingByRequester.remove(previous.requester());
        }
        pendingByTarget.put(target.getUniqueId(), request);
        pendingByRequester.put(initiator.getUniqueId(), request);

        msg(initiator, config.format("sent", "{PLAYER}", target.getName()));
        msg(target, config.format("received", "{PLAYER}", initiator.getName()));
    }
    private void startSession(Player requester, Player accepter) {
        TradeSession session = new TradeSession(requester.getUniqueId(), accepter.getUniqueId());
        sessionsByPlayer.put(requester.getUniqueId(), session);
        sessionsByPlayer.put(accepter.getUniqueId(), session);

        msg(requester, config.format("accepted", "{PLAYER}", accepter.getName()));
        msg(accepter, config.format("accepted", "{PLAYER}", requester.getName()));

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Sesja mogła zniknąć w tym ticku (quit / sync / restart) — nie otwieraj osieroconego GUI.
            if (sessionsByPlayer.get(requester.getUniqueId()) != session
                || sessionsByPlayer.get(accepter.getUniqueId()) != session) {
                return;
            }
            Player a = Bukkit.getPlayer(requester.getUniqueId());
            Player b = Bukkit.getPlayer(accepter.getUniqueId());
            if (a != null && a.isOnline()) {
                openTradeGui(a, session);
            }
            if (b != null && b.isOnline()) {
                openTradeGui(b, session);
            }
        });
    }

    private void openTradeGui(Player player, TradeSession session) {
        TradeGui gui = new TradeGui(plugin, session, player);
        openTradeGuis.put(player.getUniqueId(), gui);
        gui.open(player);
        playSound(player, "open");
    }

    public void refreshTradeGui(TradeSession session) {
        for (UUID uuid : List.of(session.getPlayerA(), session.getPlayerB())) {
            TradeGui gui = openTradeGuis.get(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (gui != null && player != null && player.isOnline()) {
                gui.refresh();
            }
        }
    }

    public boolean addItemToOffer(Player player, TradeSession session, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemStack[] offer = session.offerFor(player.getUniqueId());
        int empty = firstEmpty(offer);
        if (empty < 0) {
            return false;
        }
        offer[empty] = stack.clone();
        return true;
    }

    public boolean removeFromOfferSlot(Player player, TradeSession session, int guiSlot) {
        int index = TradeSlots.offerIndex(guiSlot, session.isSideA(player.getUniqueId()));
        if (index < 0) {
            return false;
        }
        ItemStack[] offer = session.offerFor(player.getUniqueId());
        ItemStack stack = offer[index];
        if (stack == null || stack.getType().isAir()) {
            return false;
        }

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        offer[index] = null;
        return true;
    }

    public void toggleReady(Player player, TradeSession session) {
        boolean next = !session.isReady(player.getUniqueId());
        session.setReady(player.getUniqueId(), next);
        playSound(player, "ready");
        msg(player, next ? config.format("ready-on") : config.format("ready-off"));
        refreshTradeGui(session);

        if (session.bothReady()) {
            completeTrade(session);
        }
    }

    private void completeTrade(TradeSession session) {
        if (session.isCompleting()) {
            return;
        }
        session.setCompleting(true);

        Player a = Bukkit.getPlayer(session.getPlayerA());
        Player b = Bukkit.getPlayer(session.getPlayerB());

        List<ItemStack> giveToB = TradeGui.collectOfferItems(session.offerFor(session.getPlayerA()));
        List<ItemStack> giveToA = TradeGui.collectOfferItems(session.offerFor(session.getPlayerB()));

        clearOffer(session.offerFor(session.getPlayerA()));
        clearOffer(session.offerFor(session.getPlayerB()));

        if (a != null) {
            deliverItems(a, giveToA);
            msg(a, config.format("complete", "{PLAYER}", b != null ? b.getName() : "?"));
            playSound(a, "complete");
            a.closeInventory();
        }
        if (b != null) {
            deliverItems(b, giveToB);
            msg(b, config.format("complete", "{PLAYER}", a != null ? a.getName() : "?"));
            playSound(b, "complete");
            b.closeInventory();
        }

        removeSession(session);
    }

    public void handleTradeClose(Player player) {
        TradeSession session = sessionsByPlayer.get(player.getUniqueId());
        if (session == null || session.isCompleting()) {
            openTradeGuis.remove(player.getUniqueId());
            return;
        }

        UUID partnerId = session.partnerOf(player.getUniqueId());
        Player partner = partnerId != null ? Bukkit.getPlayer(partnerId) : null;

        List<ItemStack> ownItems = TradeGui.collectOfferItems(session.offerFor(player.getUniqueId()));
        List<ItemStack> partnerItems = partnerId != null
            ? TradeGui.collectOfferItems(session.offerFor(partnerId))
            : List.of();

        clearOffer(session.offerFor(player.getUniqueId()));
        if (partnerId != null) {
            clearOffer(session.offerFor(partnerId));
        }

        openTradeGuis.remove(player.getUniqueId());
        if (partnerId != null) {
            openTradeGuis.remove(partnerId);
        }
        removeSession(session);

        if (partner != null && partner.isOnline()) {
            partner.closeInventory();
            msg(partner, config.format("partner-quit", "{PLAYER}", player.getName()));
        }

        if (!ownItems.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> openWithdrawGui(player, ownItems));
        }
        if (partner != null && partner.isOnline() && !partnerItems.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> openWithdrawGui(partner, partnerItems));
        }
    }

    public void handleWithdrawClose(Player player, TradeWithdrawGui gui) {
        withdrawGuis.remove(player.getUniqueId());
        deliverItems(player, gui.getItems());
    }

    public void handleWithdrawClick(Player player, TradeWithdrawGui gui, int rawSlot) {
        int[] contentSlots = plugin.getGuiFactory().getContentSlots(gui.getInventory());
        int index = -1;
        for (int i = 0; i < contentSlots.length; i++) {
            if (contentSlots[i] == rawSlot) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= gui.getItems().size()) {
            return;
        }
        ItemStack shown = gui.getInventory().getItem(rawSlot);
        if (shown == null || shown.getType().isAir()) {
            return;
        }

        ItemStack stack = gui.getItems().remove(index);
        // Lista się przesunęła — przerysuj całą zawartość, inaczej sloty rozjadą się z listą.
        for (int i = 0; i < contentSlots.length; i++) {
            ItemStack render = i < gui.getItems().size() ? gui.getItems().get(i).clone() : null;
            gui.getInventory().setItem(contentSlots[i], render);
        }
        if (stack != null && !stack.getType().isAir()) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            playSound(player, "remove");
        }
        if (gui.getItems().isEmpty()) {
            player.closeInventory();
        }
    }

    private void openWithdrawGui(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        TradeWithdrawGui gui = new TradeWithdrawGui(plugin, player, items);
        withdrawGuis.put(player.getUniqueId(), gui);
        gui.open(player);
    }

    public void handleQuit(Player player) {
        pendingByTarget.remove(player.getUniqueId());
        TradeRequest outgoing = pendingByRequester.remove(player.getUniqueId());
        if (outgoing != null) {
            TradeRequest current = pendingByTarget.get(outgoing.target());
            if (current != null && current.requester().equals(player.getUniqueId())) {
                pendingByTarget.remove(outgoing.target());
            }
        }

        TradeSession session = sessionsByPlayer.get(player.getUniqueId());
        if (session == null || session.isCompleting()) {
            openTradeGuis.remove(player.getUniqueId());
            withdrawGuis.remove(player.getUniqueId());
            return;
        }

        List<ItemStack> own = TradeGui.collectOfferItems(session.offerFor(player.getUniqueId()));
        clearOffer(session.offerFor(player.getUniqueId()));

        Player partner = session.otherPlayer(player);
        if (partner != null && partner.isOnline()) {
            msg(partner, config.format("partner-quit"));
            partner.closeInventory();
            List<ItemStack> partnerItems = TradeGui.collectOfferItems(session.offerFor(partner.getUniqueId()));
            clearOffer(session.offerFor(partner.getUniqueId()));
            removeSession(session);
            if (!partnerItems.isEmpty()) {
                openWithdrawGui(partner, partnerItems);
            }
        } else {
            removeSession(session);
        }

        for (ItemStack item : own) {
            if (item != null && !item.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
            }
        }

        openTradeGuis.remove(player.getUniqueId());
        withdrawGuis.remove(player.getUniqueId());
    }

    private void removeSession(TradeSession session) {
        sessionsByPlayer.remove(session.getPlayerA());
        sessionsByPlayer.remove(session.getPlayerB());
        openTradeGuis.remove(session.getPlayerA());
        openTradeGuis.remove(session.getPlayerB());
    }

    private void deliverItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private void clearOffer(ItemStack[] offer) {
        for (int i = 0; i < offer.length; i++) {
            offer[i] = null;
        }
    }

    private int firstEmpty(ItemStack[] offer) {
        for (int i = 0; i < offer.length; i++) {
            if (offer[i] == null || offer[i].getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    private boolean canReach(Player a, Player b) {
        if (!a.getWorld().equals(b.getWorld())) {
            return false;
        }
        return a.getLocation().distance(b.getLocation()) <= config.maxDistance();
    }

    private void clearRequest(UUID targetId, UUID requesterId) {
        // Usuwamy wpis celu tylko gdy należy do podanego nadawcy — inaczej skasowalibyśmy cudzą prośbę.
        TradeRequest current = pendingByTarget.get(targetId);
        if (current != null && (requesterId == null || current.requester().equals(requesterId))) {
            pendingByTarget.remove(targetId);
        }
        if (requesterId != null) {
            pendingByRequester.remove(requesterId);
        }
    }

    private void tickCleanup() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, TradeRequest> entry : new HashMap<>(pendingByTarget).entrySet()) {
            TradeRequest request = entry.getValue();
            if (now <= request.expiresAt()) {
                continue;
            }
            clearRequest(entry.getKey(), request.requester());
            Player target = Bukkit.getPlayer(entry.getKey());
            Player requester = Bukkit.getPlayer(request.requester());
            if (target != null) {
                msg(target, config.format("timeout"));
            }
            if (requester != null) {
                msg(requester, config.format("timeout"));
            }
        }
    }

    public void playSound(Player player, String key) {
        Sound sound = switch (key) {
            case "add" -> config.sound("add", Sound.UI_BUTTON_CLICK);
            case "remove" -> config.sound("remove", Sound.UI_BUTTON_CLICK);
            case "ready" -> config.sound("ready", Sound.BLOCK_NOTE_BLOCK_PLING);
            case "complete" -> config.sound("complete", Sound.ENTITY_PLAYER_LEVELUP);
            case "deny" -> config.sound("deny", Sound.BLOCK_NOTE_BLOCK_BASS);
            default -> config.sound("open", Sound.BLOCK_CHEST_OPEN);
        };
        player.playSound(player.getLocation(), sound, 0.8f, 1.2f);
    }

    private void msg(Player player, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        player.sendMessage(ColorUtils.colorizeComponent(message));
    }

    public boolean isWithdrawGui(InventoryHolder holder) {
        return holder instanceof TradeWithdrawGui;
    }

    public boolean isTradeGui(InventoryHolder holder) {
        return holder instanceof TradeGui;
    }
}
