package dev.grzybalson.anarchiagg.zmiankibattle;

import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.zmiankibattle.battle.BattleManager;
import dev.grzybalson.anarchiagg.zmiankibattle.command.ZmiankaBattleCommand;
import dev.grzybalson.anarchiagg.zmiankibattle.global.GlobalChallengeManager;
import dev.grzybalson.anarchiagg.zmiankibattle.gui.GuiListener;
import dev.grzybalson.anarchiagg.zmiankibattle.invite.InviteManager;
import dev.grzybalson.anarchiagg.zmiankibattle.util.BossBarTracker;
import dev.grzybalson.anarchiagg.zmiankibattle.util.SoundResolver;
import dev.grzybalson.anarchiagg.zmiankibattle.util.WorldGuardRegionGate;
import dev.grzybalson.anarchiagg.zmiankibattle.util.WorldGuardRegionStayEnforcer;
import dev.grzybalson.anarchiagg.AnarchiaGGPaths;
import dev.grzybalson.config.anarchiagg.ZmiankaBattleConfig;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import java.io.File;
import java.util.logging.Logger;

public final class ZmiankaBattle implements Listener {

    private final HollyCore holly;
    private FileConfiguration config;
    private volatile Economy economy;
    private SoundResolver sounds;
    private BossBarTracker bossBars;
    private WorldGuardRegionGate regionGate;

    private InviteManager inviteManager;
    private BattleManager battleManager;
    private GlobalChallengeManager globalChallengeManager;
    private ZmiankaBattleCommand commandHandler;

    public ZmiankaBattle(HollyCore holly) {
        this.holly = holly;
    }

    public void onEnable() {
        loadConfig();

        if (tryHookEconomy()) {
            getLogger().info("[ZmiankaBattle] Podłączono ekonomię: " + economy.getName());
        } else {
            getLogger().warning("[ZmiankaBattle] Nie znaleziono jeszcze providera ekonomii (Vault). Moduł czeka na rejestrację usługi...");
        }

        this.sounds = new SoundResolver(getConfig());
        this.bossBars = new BossBarTracker(holly);
        this.regionGate = new WorldGuardRegionGate(this);

        this.battleManager = new BattleManager(this);
        this.inviteManager = new InviteManager(this);
        this.globalChallengeManager = new GlobalChallengeManager(this);

        this.commandHandler = new ZmiankaBattleCommand(this);
        registerCommand("zmiankibattle", commandHandler);
        registerCommand("zmianka-battle", commandHandler);

        Bukkit.getPluginManager().registerEvents(this, holly);
        Bukkit.getPluginManager().registerEvents(new GuiListener(), holly);
        Bukkit.getPluginManager().registerEvents(inviteManager, holly);
        Bukkit.getPluginManager().registerEvents(battleManager, holly);
        Bukkit.getPluginManager().registerEvents(globalChallengeManager, holly);
        Bukkit.getPluginManager().registerEvents(bossBars, holly);
        Bukkit.getPluginManager().registerEvents(new WorldGuardRegionStayEnforcer(this), holly);

        getLogger().info("[ZmiankaBattle] Moduł włączony.");
    }

    public void onDisable() {
        if (bossBars != null) bossBars.clearAll();
        if (globalChallengeManager != null) globalChallengeManager.clearAll();
        if (battleManager != null) battleManager.stopAll();
        if (inviteManager != null) inviteManager.clearAll();
        getLogger().info("[ZmiankaBattle] Moduł wyłączony.");
    }

    private void registerCommand(String name, ZmiankaBattleCommand cmd) {
        PluginCommand command = holly.getCommand(name);
        if (command == null) return;
        command.setExecutor(cmd);
        command.setTabCompleter(cmd);
    }

    public void loadConfig() {
        File out = AnarchiaGGPaths.getFile(holly, "zmiankibattle/zmiankibattle.yml");
        eu.okaeri.configs.ConfigManager.create(ZmiankaBattleConfig.class, it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(out);
            it.withRemoveOrphans(false);
            it.saveDefaults();
            it.load(true);
        });
        this.config = YamlConfiguration.loadConfiguration(out);
    }

    public HollyCore holly() {
        return holly;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public Logger getLogger() {
        return holly.getLogger();
    }

    public Server getServer() {
        return holly.getServer();
    }

    public synchronized boolean ensureEconomy() {
        return economy != null || tryHookEconomy();
    }

    private synchronized boolean tryHookEconomy() {
        if (economy != null) return true;

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            return false;
        }

        economy = rsp.getProvider();
        return true;
    }

    private synchronized void refreshEconomyFromServices() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        economy = (rsp == null ? null : rsp.getProvider());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        if (economy == null && tryHookEconomy()) {
            getLogger().info("[ZmiankaBattle] Podłączono ekonomię po pełnym starcie serwera: " + economy.getName());
        }
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent e) {
        if (!Economy.class.isAssignableFrom(e.getProvider().getService())) return;

        boolean wasNull = (economy == null);
        refreshEconomyFromServices();

        if (economy != null && (wasNull || e.getProvider().getPriority() == ServicePriority.Highest || e.getProvider().getPriority() == ServicePriority.Normal)) {
            getLogger().info("[ZmiankaBattle] Wykryto provider ekonomii: " + economy.getName());
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent e) {
        if (!Economy.class.isAssignableFrom(e.getProvider().getService())) return;

        Economy old = economy;
        refreshEconomyFromServices();

        if (old != null && economy == null) {
            getLogger().warning("[ZmiankaBattle] Provider ekonomii został wyrejestrowany. Moduł będzie czekał na nowy provider.");
        } else if (economy != null && (old == null || !economy.getClass().equals(old.getClass()))) {
            getLogger().info("[ZmiankaBattle] Przełączono provider ekonomii na: " + economy.getName());
        }
    }

    public Economy getEconomy() {
        return economy;
    }

    public ZmiankaBattleCommand getCommandHandler() {
        return commandHandler;
    }

    public SoundResolver sounds() {
        return sounds;
    }

    public BossBarTracker bossBars() {
        return bossBars;
    }

    public WorldGuardRegionGate regions() {
        return regionGate;
    }

    public InviteManager invites() {
        return inviteManager;
    }

    public BattleManager battles() {
        return battleManager;
    }

    public GlobalChallengeManager globals() {
        return globalChallengeManager;
    }
}
