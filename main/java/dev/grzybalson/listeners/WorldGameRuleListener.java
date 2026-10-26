package dev.grzybalson.listeners;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldGameRuleListener implements Listener {

    private final HollyCore plugin;

    public WorldGameRuleListener(HollyCore plugin) {
        this.plugin = plugin;
        applyToLoadedWorlds();
    }

    private void applyToLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            apply(world);
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        apply(event.getWorld());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    private void apply(World world) {
        setRule(world, GameRule.SHOW_DEATH_MESSAGES, false);
        setRule(world, GameRule.ANNOUNCE_ADVANCEMENTS, false);
        setRule(world, GameRule.SEND_COMMAND_FEEDBACK, false);
        setRule(world, GameRule.COMMAND_BLOCK_OUTPUT, false);
        setRule(world, GameRule.LOG_ADMIN_COMMANDS, false);
        setRule(world, GameRule.DO_WEATHER_CYCLE, false);
        setRule(world, GameRule.DO_MOB_SPAWNING, false);
    }

    private <T> void setRule(World world, GameRule<T> gameRule, T value) {
        Boolean changed = world.setGameRule(gameRule, value);
        if (changed == null) {
            plugin.getLogger().warning("Nie udalo sie ustawic gamerule " + gameRule.getName() + " dla swiata " + world.getName());
        }
    }
}
