package dev.grzybalson.modules.cases;

import dev.grzybalson.HollyCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LightningStrike;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CaseListener implements Listener {

    private final HollyCore plugin;
    private final CaseManager manager;
    private final Map<UUID, PendingChanceInput> pendingChanceInputs = new ConcurrentHashMap<>();
    private final Set<UUID> caseInventoryViewers = ConcurrentHashMap.newKeySet();

    public CaseListener(HollyCore plugin, CaseManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        CaseDefinition definition = manager.findByBlock(event.getClickedBlock());
        if (definition == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!definition.enabled()) {
            player.sendMessage(manager.colorMessage("messages.case-disabled", Map.of("case", definition.name())));
            return;
        }
        player.openInventory(manager.createPreviewInventory(definition));
        manager.playOpenGuiSound(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        CaseDefinition definition = manager.findLuckyBlockCase(event.getItemInHand());
        if (definition == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        boolean shiftInstant = player.isSneaking();
        CaseOpenType openType = shiftInstant ? CaseOpenType.NO_ANIMATION : manager.getLuckyBlockOpenType();
        // NinjaBox zawsze animacja swiatowa (nie GUI), chyba ze shift — wtedy od razu item.
        if (!shiftInstant && NinjaBoxWorldAnimator.isNinjaBoxCase(definition)) {
            openType = CaseOpenType.ANIMATION;
        }
        CaseOpenResult result = manager.tryPrepareOpening(player, definition.name(), openType, event.getHand());
        if (result == null) {
            return;
        }

        if (shiftInstant) {
            if (player.getOpenInventory() != null) {
                player.closeInventory();
            }
            manager.finishOpening(player, result, CaseOpenType.NO_ANIMATION, false);
        } else if (NinjaBoxWorldAnimator.isNinjaBoxCase(definition)) {
            manager.startNinjaBoxWorldAnimation(player, result, event.getBlockPlaced().getLocation());
        } else if (openType == CaseOpenType.ANIMATION) {
            manager.startAnimation(player, result);
        } else {
            manager.finishOpening(player, result, CaseOpenType.NO_ANIMATION, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        // Guard by holder — never rely only on caseInventoryViewers (open-during-close can desync it).
        if (holder instanceof CasePreviewHolder previewHolder) {
            caseInventoryViewers.add(player.getUniqueId());
            handlePreviewClick(event, player, previewHolder);
            return;
        }
        if (holder instanceof CaseResultHolder resultHolder) {
            caseInventoryViewers.add(player.getUniqueId());
            handleResultClick(event, player, resultHolder);
            return;
        }
        if (holder instanceof CaseAnimationHolder animationHolder) {
            caseInventoryViewers.add(player.getUniqueId());
            // Never allow taking strip/reward items from animation GUI.
            event.setCancelled(true);
            if (animationHolder.isFinished()) {
                handleFinishedAnimationClick(event, player, animationHolder);
            }
            return;
        }
        if (holder instanceof CaseEditHolder editHolder) {
            caseInventoryViewers.add(player.getUniqueId());
            handleEditClick(event, player, editHolder);
            return;
        }
        if (holder instanceof CaseChanceAnvilHolder anvilHolder) {
            caseInventoryViewers.add(player.getUniqueId());
            handleAnvilClick(event, player, anvilHolder);
            return;
        }
        caseInventoryViewers.remove(player.getUniqueId());
    }

    private void handlePreviewClick(InventoryClickEvent event, Player player, CasePreviewHolder holder) {
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (manager.isCaseBattleSlot(event.getRawSlot())) {
            manager.showCaseBattleDisabledTitle(player);
            return;
        }

        CaseOpenType type = manager.getPreviewButtonType(event.getRawSlot());
        if (type == CaseOpenType.ANIMATION || type == CaseOpenType.NO_ANIMATION) {
            CaseOpenResult result = manager.tryPrepareOpening(player, holder.caseName(), type);
            if (result == null) {
                return;
            }
            if (type == CaseOpenType.ANIMATION) {
                manager.startAnimation(player, result);
            } else {
                manager.finishOpening(player, result, CaseOpenType.NO_ANIMATION, true);
            }
        }
    }

    private void handleResultClick(InventoryClickEvent event, Player player, CaseResultHolder holder) {
        handleResultClick(event, player, holder.caseName(), holder.previousOpenType());
    }

    private void handleFinishedAnimationClick(InventoryClickEvent event, Player player, CaseAnimationHolder holder) {
        handleResultClick(event, player, holder.caseName(), CaseOpenType.ANIMATION);
    }

    private void handleResultClick(InventoryClickEvent event, Player player, String caseName, CaseOpenType previousOpenType) {
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        CaseOpenType type = manager.getResultButtonType(event.getRawSlot());
        if (type == CaseOpenType.CLOSE) {
            player.closeInventory();
            return;
        }
        if (type == CaseOpenType.OPEN_AGAIN) {
            CaseOpenResult result = manager.tryPrepareOpening(player, caseName, previousOpenType);
            if (result == null) {
                return;
            }
            if (previousOpenType == CaseOpenType.ANIMATION) {
                manager.startAnimation(player, result);
            } else {
                manager.finishOpening(player, result, CaseOpenType.NO_ANIMATION, true);
            }
        }
    }

    private void handleEditClick(InventoryClickEvent event, Player player, CaseEditHolder holder) {
        Inventory topInventory = event.getView().getTopInventory();
        if (event.getRawSlot() >= topInventory.getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        CaseOpenType type = manager.getEditorButtonType(event.getRawSlot(), topInventory.getSize());
        if (type == CaseOpenType.SAVE) {
            event.setCancelled(true);
            manager.saveEditorInventory(holder.caseName(), topInventory);
            player.sendMessage(manager.colorMessage("messages.editor-saved", Map.of("case", holder.caseName())));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (event.isRightClick() && event.isShiftClick() && clicked != null && !clicked.getType().isAir()) {
            event.setCancelled(true);
            int rewardIndex = manager.findRewardIndexInEditor(topInventory, event.getRawSlot());
            if (rewardIndex < 0) {
                return;
            }
            manager.saveEditorInventory(holder.caseName(), topInventory);
            pendingChanceInputs.put(player.getUniqueId(), new PendingChanceInput(holder.caseName(), rewardIndex));
            player.closeInventory();
            player.sendMessage(manager.colorMessage("messages.chance-input-start", Map.of("case", holder.caseName())));
            return;
        }

        event.setCancelled(false);
    }

    private void handleAnvilClick(InventoryClickEvent event, Player player, CaseChanceAnvilHolder holder) {
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        event.setCancelled(true);
        if (event.getRawSlot() != 2) {
            return;
        }

        Double chance = manager.parseChanceResult(event.getCurrentItem());
        if (chance == null) {
            chance = manager.parseChance(getRenameText(event.getView().getTopInventory()));
        }
        if (chance == null) {
            chance = manager.parseChance(holder.lastChanceInput());
        }
        if (chance == null) {
            player.sendMessage(manager.colorMessage("messages.invalid-chance", Map.of("case", holder.caseName())));
            return;
        }

        if (!manager.setRewardChance(holder.caseName(), holder.rewardIndex(), chance)) {
            player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", holder.caseName())));
            return;
        }

        player.sendMessage(manager.colorMessage("messages.chance-set", Map.of(
            "case", holder.caseName(),
            "chance", manager.formatChance(chance)
        )));

        CaseDefinition definition = manager.getCase(holder.caseName());
        if (definition != null) {
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(manager.createEditorInventory(definition)));
        } else {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaseChanceAnvilHolder holder)) {
            return;
        }
        String chanceInput = resolvePreparedChanceInput(event);
        holder.setLastChanceInput(chanceInput);
        event.setResult(manager.createChanceResult(holder.lastChanceInput()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        if (holder instanceof CasePreviewHolder || holder instanceof CaseResultHolder
            || holder instanceof CaseAnimationHolder || holder instanceof CaseChanceAnvilHolder) {
            caseInventoryViewers.add(player.getUniqueId());
            event.setCancelled(true);
            return;
        }
        if (holder instanceof CaseEditHolder) {
            caseInventoryViewers.add(player.getUniqueId());
            Set<Integer> saveSlots = manager.getSaveSlots(topInventory.getSize());
            for (int slot : event.getRawSlots()) {
                if (saveSlots.contains(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isCaseHolder(event.getInventory().getHolder())) {
            caseInventoryViewers.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            caseInventoryViewers.remove(player.getUniqueId());
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CaseAnimationHolder animationHolder
            && event.getPlayer() instanceof Player player) {
            // Wipe strip items so nothing can be scooped from a closing/desynced GUI.
            event.getInventory().clear();
            // Drop any cursor item taken from the case GUI (dupe vector on close).
            discardCaseCursor(player);
            if (!animationHolder.isFinished()) {
                manager.cancelAnimationAndShowReward(player);
            }
            return;
        }
        if (holder instanceof CaseResultHolder && event.getPlayer() instanceof Player player) {
            event.getInventory().clear();
            discardCaseCursor(player);
            return;
        }
        if (!(holder instanceof CaseEditHolder editHolder)) {
            return;
        }
        manager.saveEditorInventory(editHolder.caseName(), event.getInventory());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        pendingChanceInputs.remove(player.getUniqueId());
        caseInventoryViewers.remove(player.getUniqueId());
        manager.cancelAnimationAndShowReward(player);
        manager.handleQuit(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChanceChatInput(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingChanceInput pending = pendingChanceInputs.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (message.equalsIgnoreCase("anuluj") || message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(manager.colorMessage("messages.chance-input-cancelled", Map.of("case", pending.caseName()))));
            return;
        }

        Double chance = manager.parseChance(message);
        if (chance == null) {
            pendingChanceInputs.put(player.getUniqueId(), pending);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(manager.colorMessage("messages.invalid-chance", Map.of("case", pending.caseName())));
                player.sendMessage(manager.colorMessage("messages.chance-input-start", Map.of("case", pending.caseName())));
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!manager.setRewardChance(pending.caseName(), pending.rewardIndex(), chance)) {
                player.sendMessage(manager.colorMessage("messages.case-not-found", Map.of("case", pending.caseName())));
                return;
            }
            player.sendMessage(manager.colorMessage("messages.chance-set", Map.of(
                "case", pending.caseName(),
                "chance", manager.formatChance(chance)
            )));

            CaseDefinition definition = manager.getCase(pending.caseName());
            if (definition != null) {
                player.openInventory(manager.createEditorInventory(definition));
            }
        });
    }

    private void discardCaseCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            player.setItemOnCursor(null);
        }
    }

    private boolean isCaseHolder(InventoryHolder holder) {
        return holder instanceof CasePreviewHolder
            || holder instanceof CaseResultHolder
            || holder instanceof CaseAnimationHolder
            || holder instanceof CaseEditHolder
            || holder instanceof CaseChanceAnvilHolder;
    }

    /** Piorun / fajerwerki z animacji Ninja Box — bez obrazen. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNinjaBoxLightningDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.LIGHTNING
            && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
            && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        if (manager.isNinjaBoxFxNear(player.getLocation(), 14.0D)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNinjaBoxFxEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (event.getDamager() instanceof LightningStrike
            || (event.getDamager() instanceof Firework fw
            && (fw.getScoreboardTags().contains("holly_ninjabox")
            || fw.getScoreboardTags().contains("holly_ninjabox_fx")))) {
            if (manager.isNinjaBoxFxNear(event.getEntity().getLocation(), 14.0D)
                || event.getDamager().getScoreboardTags().contains("holly_ninjabox")
                || event.getDamager().getScoreboardTags().contains("holly_ninjabox_fx")) {
                event.setCancelled(true);
            }
        }
    }

    private String getRenameText(Inventory inventory) {
        if (inventory instanceof AnvilInventory anvilInventory) {
            return anvilInventory.getRenameText();
        }
        return "";
    }

    private String resolvePreparedChanceInput(PrepareAnvilEvent event) {
        Double naturalResultChance = manager.parsePlainChanceItemName(event.getResult());
        if (naturalResultChance != null) {
            return manager.formatChance(naturalResultChance);
        }

        String renameText = getRenameText(event.getInventory());
        if (manager.parseChance(renameText) != null) {
            return renameText;
        }

        return null;
    }

    private record PendingChanceInput(String caseName, int rewardIndex) {
    }
}
