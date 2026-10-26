package dev.grzybalson.modules.event;

import dev.grzybalson.utils.ClickCooldown;
import dev.grzybalson.utils.NBTUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class EventGUIListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final EventManager manager;

    public EventGUIListener(EventManager manager) {
        this.manager = manager;
        manager.ensureJoinAnvilPacketListener();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();

        if (holder instanceof DungeonJoinAnvilHolder anvilHolder) {
            // Blokuj shift / wrzucanie itemow do kowadla i z ekwipunku.
            event.setCancelled(true);
            if (rawSlot != 2) {
                return;
            }
            if (!ClickCooldown.tryUse(player, "dungeon-join-anvil", 500L)) {
                return;
            }

            String code = manager.sanitizePartyCode(resolveJoinCode(event, player, anvilHolder));
            if (code.isEmpty()) {
                manager.playError(player);
                manager.send(player, "invalid-code");
                return;
            }

            final String joinCode = code;
            manager.clearTypedJoinCode(player.getUniqueId());
            Bukkit.getScheduler().runTask(manager.getPlugin(), () -> manager.joinPartyByCode(player, joinCode));
            return;
        }

        if (!isEventGuiHolder(holder)) {
            return;
        }

        // Edycja nagród bossa: pozwól wkładać itemy (jak kit editor).
        if (holder instanceof DungeonBossRewardEditGUI editGUI) {
            handleBossRewardEditClick(event, player, editGUI, event.getView().getTopInventory());
            return;
        }

        // Zawsze cancel w naszym GUI — takze shift-click z dolnego ekwipunku.
        event.setCancelled(true);

        if (rawSlot < 0 || rawSlot >= topSize) {
            return;
        }

        if (holder instanceof EventGUI gui) {
            if (!ClickCooldown.tryUse(player, "event-gui", 500L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        if (holder instanceof DungeonBossRewardsMenuGUI gui) {
            if (!ClickCooldown.tryUse(player, "event-boss-rewards-menu", 400L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        if (holder instanceof DungeonCreateGUI gui) {
            if (!ClickCooldown.tryUse(player, "event-create-gui", 250L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        if (holder instanceof DungeonLobbyGUI gui) {
            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                event.setCancelled(true);
                if (!ClickCooldown.tryUse(player, "event-lobby-kick", 400L)) {
                    return;
                }
                gui.handleDrop(rawSlot);
                return;
            }
            if (!ClickCooldown.tryUse(player, "event-lobby-gui", 400L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        if (holder instanceof DungeonPublicGUI gui) {
            if (!ClickCooldown.tryUse(player, "event-public-gui", 400L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        if (holder instanceof dev.grzybalson.modules.event.dungeon.DungeonUpgradeGUI gui) {
            if (!ClickCooldown.tryUse(player, "dungeon-upgrade-gui", 300L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        if (holder instanceof dev.grzybalson.modules.event.dungeon.DungeonWave12GUI gui) {
            if (!ClickCooldown.tryUse(player, "dungeon-wave12-gui", 400L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        if (holder instanceof dev.grzybalson.modules.event.dungeon.DungeonCoinShopGUI gui) {
            if (!ClickCooldown.tryUse(player, "dungeon-coin-shop", 250L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        if (holder instanceof dev.grzybalson.modules.event.dungeon.DungeonWipeReviveGUI gui) {
            if (!ClickCooldown.tryUse(player, "dungeon-wipe-revive", 400L)) {
                return;
            }
            manager.playClick(player);
            gui.handleClick(rawSlot);
            return;
        }
        // DungeonUpgradeWaitGUI — tylko cancel, bez klikow.
    }

    private void handleBossRewardEditClick(
        InventoryClickEvent event,
        Player player,
        DungeonBossRewardEditGUI editGUI,
        Inventory topInventory
    ) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) {
            event.setCancelled(true);
            return;
        }

        boolean topInventoryClick = clickedInventory.equals(topInventory);
        if (topInventoryClick) {
            int slot = event.getRawSlot();
            if (!editGUI.isEditableSlot(slot)) {
                event.setCancelled(true);
                editGUI.handleActionClick(slot);
                return;
            }
            if (!editGUI.isEditable()) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack item = topInventory.getItem(slot);
                if (item == null || item.getType().isAir()) {
                    return;
                }
                var leftover = player.getInventory().addItem(item.clone());
                if (leftover.isEmpty()) {
                    topInventory.setItem(slot, null);
                } else {
                    topInventory.setItem(slot, leftover.values().iterator().next());
                }
                player.updateInventory();
            }
            return;
        }

        if (!editGUI.isEditable()) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) {
                return;
            }
            int remaining = editGUI.addItemToEditableSlots(item.clone());
            if (remaining <= 0) {
                event.getClickedInventory().setItem(event.getSlot(), null);
            } else {
                item.setAmount(remaining);
                event.getClickedInventory().setItem(event.getSlot(), item);
            }
            player.updateInventory();
        }
    }

    private boolean isEventGuiHolder(InventoryHolder holder) {
        return holder instanceof EventGUI
            || holder instanceof DungeonCreateGUI
            || holder instanceof DungeonLobbyGUI
            || holder instanceof DungeonPublicGUI
            || holder instanceof DungeonJoinAnvilHolder
            || holder instanceof DungeonBossRewardsMenuGUI
            || holder instanceof DungeonBossRewardEditGUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonUpgradeGUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonUpgradeWaitGUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonWave12GUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonCoinShopGUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonWipeReviveGUI;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof DungeonJoinAnvilHolder holder)) {
            return;
        }

        Player player = null;
        if (event.getViewers() != null && !event.getViewers().isEmpty() && event.getViewers().get(0) instanceof Player viewer) {
            player = viewer;
        }

        String code = "";
        if (player != null) {
            code = manager.sanitizePartyCode(manager.getTypedJoinCode(player.getUniqueId()));
        }
        if (code.isEmpty()) {
            code = manager.sanitizePartyCode(resolvePreparedCode(event));
        }
        if (code.isEmpty()) {
            code = manager.sanitizePartyCode(holder.lastCode());
        }

        holder.setLastCode(code);
        event.getInventory().setRepairCost(0);
        event.setResult(manager.createJoinResultItem(code.isEmpty() ? "..." : code));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof DungeonJoinAnvilHolder) {
            manager.clearTypedJoinCode(player.getUniqueId());
        }
        if (event.getInventory().getHolder() instanceof dev.grzybalson.modules.event.dungeon.DungeonWave12GUI) {
            if (manager.getDungeonSessions() != null) {
                var session = manager.getDungeonSessions().getByPlayer(player.getUniqueId());
                if (session != null) {
                    session.reopenWave12IfNeeded(player);
                }
            }
        }
        if (event.getInventory().getHolder() instanceof dev.grzybalson.modules.event.dungeon.DungeonUpgradeGUI
            || event.getInventory().getHolder() instanceof dev.grzybalson.modules.event.dungeon.DungeonUpgradeWaitGUI) {
            if (manager.getDungeonSessions() != null) {
                var session = manager.getDungeonSessions().getByPlayer(player.getUniqueId());
                if (session != null) {
                    session.reopenUpgradeIfNeeded(player);
                }
            }
        }
        if (event.getInventory().getHolder() instanceof dev.grzybalson.modules.event.dungeon.DungeonCoinShopGUI) {
            if (manager.getDungeonSessions() != null) {
                var session = manager.getDungeonSessions().getByPlayer(player.getUniqueId());
                if (session != null) {
                    // Zamkniecie GUI = pomin shop.
                    Bukkit.getScheduler().runTask(manager.getPlugin(), () -> session.finishCoinShop(player));
                }
            }
        }
        if (event.getInventory().getHolder() instanceof dev.grzybalson.modules.event.dungeon.DungeonWipeReviveGUI) {
            if (manager.getDungeonSessions() != null) {
                var session = manager.getDungeonSessions().getByPlayer(player.getUniqueId());
                if (session != null && session.isWipeReviveOpen()) {
                    session.reopenWipeReviveIfNeeded(player);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize);
        if (!touchesTop) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof DungeonBossRewardEditGUI editGUI) {
            for (int raw : event.getRawSlots()) {
                if (raw < topSize && !editGUI.isEditableSlot(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (!editGUI.isEditable()) {
                event.setCancelled(true);
            }
            return;
        }
        if (holder instanceof EventGUI
            || holder instanceof DungeonCreateGUI
            || holder instanceof DungeonLobbyGUI
            || holder instanceof DungeonPublicGUI
            || holder instanceof DungeonJoinAnvilHolder
            || holder instanceof DungeonBossRewardsMenuGUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonUpgradeGUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonUpgradeWaitGUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonWave12GUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonCoinShopGUI
            || holder instanceof dev.grzybalson.modules.event.dungeon.DungeonWipeReviveGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearTypedJoinCode(event.getPlayer().getUniqueId());
        manager.handleQuit(event.getPlayer());
    }

    private String resolveJoinCode(InventoryClickEvent event, Player player, DungeonJoinAnvilHolder holder) {
        String fromPacket = manager.sanitizePartyCode(manager.getTypedJoinCode(player.getUniqueId()));
        if (!fromPacket.isEmpty()) {
            return fromPacket;
        }

        String fromHolder = manager.sanitizePartyCode(holder.lastCode());
        if (!fromHolder.isEmpty()) {
            return fromHolder;
        }

        ItemStack current = event.getCurrentItem();
        String fromNbt = manager.sanitizePartyCode(NBTUtils.getString(current, "dungeon_join_code"));
        if (!fromNbt.isEmpty()) {
            return fromNbt;
        }

        String fromName = extractCodeFromItemName(current);
        if (!fromName.isEmpty()) {
            return fromName;
        }

        return manager.sanitizePartyCode(resolveRenameText(event.getView().getTopInventory()));
    }

    private String resolvePreparedCode(PrepareAnvilEvent event) {
        String rename = resolveRenameText(event.getInventory());
        String sanitizedRename = manager.sanitizePartyCode(rename);
        if (!sanitizedRename.isEmpty()) {
            return sanitizedRename;
        }

        ItemStack result = event.getResult();
        String fromResult = extractCodeFromItemName(result);
        if (!fromResult.isEmpty()) {
            return fromResult;
        }

        ItemStack first = event.getInventory().getItem(0);
        return extractCodeFromItemName(first);
    }

    private String extractCodeFromItemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return "";
        }
        String plain = PLAIN.serialize(meta.displayName()).trim();
        if (plain.isEmpty()) {
            return "";
        }
        String lower = plain.toLowerCase();
        if (lower.contains("dolacz")) {
            int idx = lower.lastIndexOf(':');
            if (idx >= 0 && idx + 1 < plain.length()) {
                return manager.sanitizePartyCode(plain.substring(idx + 1));
            }
        }
        return manager.sanitizePartyCode(plain);
    }

    private String resolveRenameText(Inventory inventory) {
        if (inventory instanceof AnvilInventory anvilInventory) {
            String text = anvilInventory.getRenameText();
            return text == null ? "" : text;
        }
        return "";
    }
}
