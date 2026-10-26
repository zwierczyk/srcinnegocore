package dev.grzybalson.anarchiagg.coach.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import dev.grzybalson.anarchiagg.coach.CoachManager;
import dev.grzybalson.anarchiagg.coach.CoachSkill;
import dev.grzybalson.anarchiagg.coach.gui.CoachMenuGUI;
import dev.grzybalson.anarchiagg.coach.gui.SkillTreeGUI;
import dev.grzybalson.anarchiagg.coach.gui.SkillUpgradeGUI;
import dev.grzybalson.anarchiagg.coach.gui.StrengthMenuGUI;
import dev.grzybalson.anarchiagg.coach.gui.StrengthUpgradeGUI;
import dev.grzybalson.anarchiagg.coach.CoachConfig;

public class CoachGUIListener implements Listener {

    private final CoachManager manager;
    private final CoachConfig config;

    public CoachGUIListener(CoachManager manager, CoachConfig config) {
        this.manager = manager;
        this.config = config;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof CoachMenuGUI)
                && !(holder instanceof SkillTreeGUI)
                && !(holder instanceof SkillUpgradeGUI)
                && !(holder instanceof StrengthMenuGUI)
                && !(holder instanceof StrengthUpgradeGUI)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) return;

        if (holder instanceof CoachMenuGUI) {
            handleMainClick(player, event.getSlot());
            return;
        }

        if (holder instanceof SkillTreeGUI) {
            handleSkillTreeClick(player, (SkillTreeGUI) holder, event.getSlot());
            return;
        }

        if (holder instanceof SkillUpgradeGUI) {
            handleSkillUpgradeClick(player, (SkillUpgradeGUI) holder, event.getSlot());
            return;
        }

        if (holder instanceof StrengthMenuGUI) {
            handleStrengthTreeClick(player, (StrengthMenuGUI) holder, event.getSlot());
            return;
        }

        if (holder instanceof StrengthUpgradeGUI) {
            handleStrengthUpgradeClick(player, (StrengthUpgradeGUI) holder, event.getSlot());
        }
    }

    private void handleSkillTreeClick(Player player, SkillTreeGUI gui, int slot) {
        if (gui.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        int clickedLevel = gui.getClickedLevel(slot);
        if (clickedLevel <= 0) return;

        int current = manager.getData(player.getUniqueId()).getLevel(gui.getSkill());
        if (clickedLevel == current + 1) {
            new SkillUpgradeGUI(manager, config, player, gui.getSkill(), clickedLevel).open();
        }
    }

    private void handleSkillUpgradeClick(Player player, SkillUpgradeGUI gui, int slot) {
        if (gui.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        boolean upgraded;
        if (gui.isKillUnlockSlot(slot)) {
            upgraded = manager.tryUpgradeSkill(player, gui.getSkill(), gui.getTargetLevel());
        } else if (gui.isShardUnlockSlot(slot)) {
            upgraded = manager.tryUpgradeSkillWithShards(player, gui.getSkill(), gui.getTargetLevel());
        } else {
            return;
        }

        if (upgraded) {
            new SkillTreeGUI(manager, config, player, gui.getSkill()).open();
        }
    }

    private void handleStrengthTreeClick(Player player, StrengthMenuGUI gui, int slot) {
        if (gui.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        int clickedLevel = gui.getClickedLevel(slot);
        if (clickedLevel <= 0) return;

        int current = manager.getData(player.getUniqueId()).getLevel(CoachSkill.STRENGTH);
        if (clickedLevel == current + 1) {
            new StrengthUpgradeGUI(manager, config, player, clickedLevel).open();
        }
    }

    private void handleStrengthUpgradeClick(Player player, StrengthUpgradeGUI gui, int slot) {
        if (gui.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        boolean upgraded;
        if (gui.isKillUnlockSlot(slot)) {
            upgraded = manager.tryUpgradeStrength(player, gui.getTargetLevel());
        } else if (gui.isShardUnlockSlot(slot)) {
            upgraded = manager.tryUpgradeStrengthWithShards(player, gui.getTargetLevel());
        } else {
            return;
        }

        if (upgraded) {
            new StrengthMenuGUI(manager, config, player).open();
        }
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == config.getGui().getMainCloseSlot()) {
            player.closeInventory();
            return;
        }
        for (CoachSkill skill : CoachSkill.values()) {
            if (manager.isSkillEnabled(skill) && slot == getSkillSlot(skill)) {
                new SkillTreeGUI(manager, config, player, skill).open();
                return;
            }
        }
    }

    private int getSkillSlot(CoachSkill skill) {
        return switch (skill) {
            case STRENGTH -> config.getGui().getStrengthItem().getSlot();
            case DEXTERITY -> config.getGui().getDexterityItem().getSlot();
            case DEFENSE -> config.getGui().getDefenseItem().getSlot();
            case LUCK -> config.getGui().getLuckItem().getSlot();
            case INTELLIGENCE -> config.getGui().getIntelligenceItem().getSlot();
        };
    }
}
