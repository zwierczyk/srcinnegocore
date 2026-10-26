package dev.grzybalson.license;

import dev.grzybalson.HollyCore;
import dev.grzybalson.internal.HollyIntegrityGuard;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public final class LicenseSlotLimiter implements Listener {

    public static final int MAX_PLAYERS = 150;

    private static final long ENFORCE_DELAY_TICKS = 1L;
    private static final long ENFORCE_PERIOD_TICKS = 20L * 10L;
    private static final String SERVER_PROPERTIES_MAX_PLAYERS = "max-players";

    private final HollyCore plugin;
    private final File serverPropertiesFile;
    private BukkitTask task;

    public LicenseSlotLimiter(HollyCore plugin) {
        this.plugin = plugin;
        this.serverPropertiesFile = new File("server.properties");
    }

    public void start() {
        HollyIntegrityGuard.requireStrong(plugin, "license-slots:start");
        stop();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        enforce();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::enforce, ENFORCE_DELAY_TICKS, ENFORCE_PERIOD_TICKS);

        plugin.getLogger().info("[LicenseSlots] Sloty serwera zostaly zablokowane na " + MAX_PLAYERS + ".");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(ServerListPingEvent event) {
        event.setMaxPlayers(MAX_PLAYERS);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();

        if (onlinePlayers >= MAX_PLAYERS) {
            event.disallow(PlayerLoginEvent.Result.KICK_FULL, "Nie znaleziono przedmiotów /odbierz.");
            return;
        }

        if (event.getResult() == PlayerLoginEvent.Result.KICK_FULL) {
            event.allow();
        }
    }

    private void enforce() {
        HollyIntegrityGuard.requireBound(plugin, "license-slots:enforce");
        enforceRuntimeSlots();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::enforceServerProperties);
    }

    private void enforceRuntimeSlots() {
        if (Bukkit.getServer().getMaxPlayers() != MAX_PLAYERS) {
            Bukkit.getServer().setMaxPlayers(MAX_PLAYERS);
        }
    }

    private void enforceServerProperties() {
        Properties properties = new Properties();

        if (serverPropertiesFile.exists()) {
            try (FileInputStream inputStream = new FileInputStream(serverPropertiesFile)) {
                properties.load(inputStream);
            } catch (IOException exception) {
                plugin.getLogger().warning("[LicenseSlots] Nie mozna odczytac server.properties: " + exception.getMessage());
                return;
            }
        }

        String expectedValue = String.valueOf(MAX_PLAYERS);
        if (expectedValue.equals(properties.getProperty(SERVER_PROPERTIES_MAX_PLAYERS))) {
            return;
        }

        properties.setProperty(SERVER_PROPERTIES_MAX_PLAYERS, expectedValue);

        try (FileOutputStream outputStream = new FileOutputStream(serverPropertiesFile)) {
            properties.store(outputStream, "HollyCore forced license slot limit");
        } catch (IOException exception) {
            plugin.getLogger().warning("[LicenseSlots] Nie mozna zapisac server.properties: " + exception.getMessage());
        }
    }
}
