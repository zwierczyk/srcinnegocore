package dev.grzybalson.anarchiagg.zmiankibattle.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import dev.grzybalson.anarchiagg.zmiankibattle.battle.Battle;

public final class BattleGuiHolder implements InventoryHolder {

    private final Battle battle;

    public BattleGuiHolder(Battle battle) {
        this.battle = battle;
    }

    public Battle getBattle() {
        return battle;
    }

    @Override
    public Inventory getInventory() {
        // holder nie trzyma instancji inventory
        throw new UnsupportedOperationException();
    }
}
