package dev.grzybalson.modules.hollydamage;

import dev.grzybalson.HollyCore;
import org.bukkit.Bukkit;

public final class HollyDamageService {

    private final HollyCore plugin;
    private final HollyDamageConfig config;
    private boolean listenersRegistered;

    public HollyDamageService(HollyCore plugin) {
        this.plugin = plugin;
        this.config = new HollyDamageConfig(plugin);
        if (!config.isEnabled()) {
            plugin.getLogger().info("[HollyDamage] disabled in damage.yml");
            return;
        }
        Bukkit.getPluginManager().registerEvents(new HollyDamageListener(plugin, config), plugin);
        Bukkit.getPluginManager().registerEvents(new HollyDamageArmorListener(config), plugin);
        Bukkit.getPluginManager().registerEvents(new ArrowDebugListener(plugin, config), plugin);
        listenersRegistered = true;
        plugin.getLogger().info("[HollyDamage] enabled (mode: " + config.getMode()
            + ", profile: " + config.getActiveSkypvpProfileId() + ")");
    }

    public void reload() {
        config.reload();
        if (config.isEnabled() && !listenersRegistered) {
            plugin.getLogger().warning("[HollyDamage] enabled=true w damage.yml, ale listenery nie zostaly"
                + " zarejestrowane przy starcie - wymagany restart serwera.");
        }
    }

    public HollyDamageConfig getConfig() {
        return config;
    }
}
