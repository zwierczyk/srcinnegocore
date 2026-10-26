package dev.grzybalson.modules.mslf;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.io.File;

public final class MslfManager {

    private final HollyCore plugin;
    private final MslfSettings settings;
    private final MslfItems items;
    private final MslfEffectsListener effectsListener;
    private MslfItemsGUI gui;
    private MslfItemRegionsGUI regionGui;
    private MslfEnderChestService enderChestService;

    public MslfManager(HollyCore plugin) {
        this.plugin = plugin;
        this.settings = new MslfSettings(plugin);
        this.items = new MslfItems(plugin, settings);
        this.effectsListener = new MslfEffectsListener(plugin, this);
        this.gui = new MslfItemsGUI(plugin, this);
        this.regionGui = new MslfItemRegionsGUI(this);
        Bukkit.getPluginManager().registerEvents(gui, plugin);
        Bukkit.getPluginManager().registerEvents(regionGui, plugin);
        Bukkit.getPluginManager().registerEvents(effectsListener, plugin);
        File schemDir = new File(plugin.getDataFolder(), "addons/mslf/schematics");
        if (!schemDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            schemDir.mkdirs();
        }
        // Przy MSLF lekki EC (powiekszanie itemem) — nie lacz z ciezkim addonem enderchest.
        if (plugin.isAddonEnabled("mslf")) {
            try {
                this.enderChestService = new MslfEnderChestService(plugin);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[MSLF] Nie udalo sie uruchomic lekkiego enderchesta: " + throwable.getMessage());
                this.enderChestService = null;
            }
        }
        plugin.getLogger().info("[MSLF] LF Minestar items zaladowane (" + MslfItemType.values().length + " typow).");
    }

    public void disable() {
        if (effectsListener != null) {
            effectsListener.restoreCastles();
        }
    }

    public void reload() {
        settings.reload();
        // Stary listener GUI trzeba wyrejestrowac, inaczej kazdy reload dokłada kopie handlerow.
        if (gui != null) {
            HandlerList.unregisterAll(gui);
        }
        gui = new MslfItemsGUI(plugin, this);
        Bukkit.getPluginManager().registerEvents(gui, plugin);
        if (regionGui == null) {
            regionGui = new MslfItemRegionsGUI(this);
            Bukkit.getPluginManager().registerEvents(regionGui, plugin);
        }
    }

    public void openGui(Player player) {
        if (gui == null) {
            gui = new MslfItemsGUI(plugin, this);
            Bukkit.getPluginManager().registerEvents(gui, plugin);
        }
        gui.open(player);
    }

    public void openRegionGui(Player player, MslfItemType type) {
        if (regionGui == null) {
            regionGui = new MslfItemRegionsGUI(this);
            Bukkit.getPluginManager().registerEvents(regionGui, plugin);
        }
        regionGui.open(player, type);
    }

    public void resetCooldowns(Player target) {
        if (target == null) {
            return;
        }
        plugin.getCooldownManager().clearCooldownsWithPrefix(target.getUniqueId(), "mslf:");
        resetVisualCooldowns(target);
    }

    public int resetAllCooldowns() {
        plugin.getCooldownManager().clearCooldownsWithPrefix("mslf:");
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetVisualCooldowns(player);
            count++;
        }
        return count;
    }

    private void resetVisualCooldowns(Player player) {
        if (player == null) {
            return;
        }
        for (MslfItemType type : MslfItemType.values()) {
            if (!type.hasUseCooldown() || type == MslfItemType.KOKOS) {
                continue;
            }
            MslfSettings.ItemDef def = settings.item(type);
            if (def != null && def.material() != null) {
                player.setCooldown(def.material(), 0);
            }
            player.setCooldown(type.defaultMaterial(), 0);
        }
    }

    public MslfSettings settings() {
        return settings;
    }

    public MslfItems items() {
        return items;
    }

    public HollyCore plugin() {
        return plugin;
    }

    public MslfEnderChestService enderChestService() {
        return enderChestService;
    }
}
