package dev.grzybalson.anarchiagg;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.anarchiagg.AnarchiaGGModulesConfig;
import dev.grzybalson.anarchiagg.customitems.DeathReset;
import dev.grzybalson.anarchiagg.customitems.EventItemContainerListener;
import dev.grzybalson.anarchiagg.customitems.EventowkiCommand;
import dev.grzybalson.anarchiagg.customitems.handlers.FlowerPlaceBlocker;
import dev.grzybalson.anarchiagg.customitems.handlers.RegionHandler;
import dev.grzybalson.anarchiagg.customitems.items.*;
import dev.grzybalson.anarchiagg.customitems.util.EventItemRegistry;
import dev.grzybalson.anarchiagg.customitems.util.EventItemUseGuard;
import dev.grzybalson.anarchiagg.ksiegi.KsiegiAnvilListener;
import dev.grzybalson.anarchiagg.ksiegi.KsiegiCommand;
import dev.grzybalson.anarchiagg.ksiegi.KsiegiDeathListener;
import dev.grzybalson.anarchiagg.ksiegi.KsiegiEffectsListener;
import dev.grzybalson.anarchiagg.ksiegi.KsiegiGUIListener;
import dev.grzybalson.anarchiagg.ksiegi.KsiegiManager;
import dev.grzybalson.pvp.PvpEnable;
import dev.grzybalson.anarchiagg.szafa.BoostHandler;
import dev.grzybalson.anarchiagg.szafa.ItemCommand;
import dev.grzybalson.anarchiagg.szafa.SzafkaConfig;
import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIHandler;
import dev.grzybalson.anarchiagg.szafa.gui.SzafaGUIListener;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyArmorHandler;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyDatabase;
import dev.grzybalson.anarchiagg.szafa.kostiumy.KostiumyHandler;
import dev.grzybalson.anarchiagg.szafa.papugi.PapugiDatabase;
import dev.grzybalson.anarchiagg.szafa.papugi.PapugiHandler;
import dev.grzybalson.anarchiagg.szafa.papugi.PapugiVisualHandler;
import dev.grzybalson.anarchiagg.szafa.pety.HealthDisplay;
import dev.grzybalson.anarchiagg.szafa.pety.PetyDatabase;
import dev.grzybalson.anarchiagg.szafa.pety.PetyHandler;
import dev.grzybalson.anarchiagg.szafa.pety.PetyVisualHandler;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiCommand;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiDamageListener;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiGUIHandler;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiManager;
import dev.grzybalson.anarchiagg.zmianki.ZmiankiUseListener;
import dev.grzybalson.anarchiagg.zmiankibattle.ZmiankaBattle;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class AnarchiaGGModuleManager {
    private final HollyCore plugin;
    private final AnarchiaGGModulesConfig modules;

    private HealthDisplay healthDisplay;
    private KostiumyDatabase kostiumyDatabase;
    private PetyDatabase petyDatabase;
    private PetyHandler petyHandler;
    private PetyVisualHandler petyVisualHandler;
    private PapugiDatabase papugiDatabase;
    private KsiegiEffectsListener ksiegiEffectsListener;
    private ZmiankaBattle zmiankaBattle;
    private final List<Listener> eventowkiListeners = new ArrayList<>();
    private final List<BukkitTask> eventowkiTasks = new ArrayList<>();

    public AnarchiaGGModuleManager(HollyCore plugin, AnarchiaGGModulesConfig modules) {
        this.plugin = plugin;
        this.modules = modules;
    }

    public void enableAll() {
        AnarchiaGGSettings.getConfig(plugin);

        if (modules.isEventowki()) {
            enableEventowki();
        } else {
            registerDisabledCommand("anarchiaitems", "eventówki");
        }

        if (modules.isSzafka()) {
            enableSzafka();
        } else if (!plugin.isAddonEnabled("oghub-szafka") && !plugin.isAddonEnabled("mslf-kostiumy")
            && !plugin.isAddonEnabled("masakra-szafka")) {
            // oghub-szafka / mslf-kostiumy / masakra-szafka dostarczaja /szafka — nie chowamy wtedy komend.
            registerDisabledCommand("szafka", "szafka");
            registerDisabledCommand("aszafka", "szafka");
        }

        if (modules.isKowal()) {
            enableKowal();
        } else {
            registerDisabledCommand("kowal", "kowal");
            registerDisabledCommand("akowal", "kowal");
        }

        if (modules.isKsiegi()) {
            enableKsiegi();
        } else {
            registerDisabledCommand("ksiegi", "księgi");
        }

        if (modules.isZmianki() && !plugin.isAddonEnabled("oghubzmianki")) {
            enableZmianki();
        } else if (!plugin.isAddonEnabled("oghubzmianki")) {
            registerDisabledCommand("zmianki", "zmianki");
            registerDisabledCommand("azmianki", "zmianki");
            registerDisabledCommand("zmiankibattle", "zmianki");
        }

    }

    public void disableAll() {
        disableEventowki();
        disableSzafkaRuntime();

        if (zmiankaBattle != null) {
            zmiankaBattle.onDisable();
        }

        PvpEnable.pvpDisable(plugin);
    }

    public void reloadEventowki() {
        if (!modules.isEventowki()) {
            hideDisabledCommands(List.of("anarchiaitems"));
            return;
        }

        disableEventowki();
        AnarchiaGGSettings.reload(plugin);
        enableEventowki();
    }

    public void reloadAllModules() {
        reloadEventowki();
        if (modules.isSzafka()) {
            SzafkaConfig.ensureDefaults(plugin);
            if (petyHandler != null) {
                petyHandler.reload();
            }
        }
    }

    private void disableEventowki() {
        for (BukkitTask task : eventowkiTasks) {
            task.cancel();
        }
        eventowkiTasks.clear();

        for (Listener listener : eventowkiListeners) {
            HandlerList.unregisterAll(listener);
        }
        eventowkiListeners.clear();

        HydroKlatka.cleanupCagesOnDisable();
        Balonik.balonikDisable();
        BlokWidmo.onDisable();
        UlepszonaElytra.onDisable();
        JajkoOlafa.disable();
        CudownaLatarnia.disable();
        RozdzkaIluzjonisty.disable();
        RogJednorozca.disable();
        PiekielnyMiecz.disable();
        SuperNieskonczonaMarchewka.disable(plugin);
    }

    private void disableSzafkaRuntime() {
        if (ksiegiEffectsListener != null) {
            ksiegiEffectsListener.cancel();
        }

        if (healthDisplay != null) {
            healthDisplay.clear();
        }

        if (petyVisualHandler != null) {
            petyVisualHandler.cleanupAllStands();
        }

        BoostHandler.onDisable();
        KostiumyArmorHandler.cleanupArmorOnDisable();

        if (kostiumyDatabase != null) {
            kostiumyDatabase.close();
        }
        if (petyDatabase != null) {
            petyDatabase.close();
        }
        if (papugiDatabase != null) {
            papugiDatabase.close();
        }
    }

    private void enableEventowki() {
        AnarchiaGGPaths.ensureResource(plugin, "customitems/guidata.yml");
        EventowkiCommand.ensureDefaultGuiItems(plugin);
        NowyExcalibur.ensureDefaults(plugin);
        EventItemUseGuard.ensureDefaultCooldowns(plugin);
        EventItemRegistry.syncAllKnownItemsToConfig(plugin);

        RegionHandler regionHandler = new RegionHandler();
        HydroKlatka hydroKlatka = new HydroKlatka(plugin, regionHandler);

        registerEventowkiListener(new FlowerPlaceBlocker(plugin));
        Lizak lizak = new Lizak(plugin);
        registerEventowkiListener(lizak);
        eventowkiTasks.add(lizak.runTaskTimer(plugin, 0L, 10L));
        eventowkiTasks.add(new RozaKupidyna(plugin).runTaskTimer(plugin, 0L, 40L));
        eventowkiTasks.add(new Korona(plugin).runTaskTimer(plugin, 0L, 40L));
        registerEventowkiListener(hydroKlatka);
        registerEventowkiListener(new Balonik(plugin, regionHandler));
        registerEventowkiListener(new WataCukrowa(plugin, regionHandler));
        registerEventowkiListener(new HydroTrojzab(plugin, regionHandler));
        registerEventowkiListener(new BlokWidmo(plugin, regionHandler));
        registerEventowkiListener(new TurboTrap(plugin, hydroKlatka, regionHandler));
        UlepszonaElytra elytra = new UlepszonaElytra(plugin, regionHandler);
        registerEventowkiListener(elytra);
        registerEventowkiListener(new Parawan(plugin, regionHandler));
        registerEventowkiListener(new Sakwa(plugin));
        registerEventowkiListener(new BombardaMaxima(plugin, regionHandler));
        registerEventowkiListener(new BoskiTopor(plugin, regionHandler));
        registerEventowkiListener(new ZlamaneSerce(plugin, regionHandler));
        registerEventowkiListener(new WedkaNielota(plugin, regionHandler));
        registerEventowkiListener(new WedkaSurfera(plugin, regionHandler));
        registerEventowkiListener(new KroliczyMiecz(plugin, regionHandler));
        registerEventowkiListener(new KostkaRubika(plugin, regionHandler));
        registerEventowkiListener(new MarchewkowyMiecz(plugin, regionHandler));
        registerEventowkiListener(new SiekieraGrincza(plugin, regionHandler));
        registerEventowkiListener(new Kosa(plugin, regionHandler));
        registerEventowkiListener(new KrewWampira(plugin, regionHandler));
        registerEventowkiListener(new TotemUlaskawienia(plugin));
        registerEventowkiListener(new SniezkaTeleportacji(plugin, regionHandler));
        registerEventowkiListener(new ZatrutyOlowek(plugin, regionHandler));
        registerEventowkiListener(new Excalibur(plugin, regionHandler));
        registerEventowkiListener(new NowyExcalibur(plugin, regionHandler));
        NowyExcalibur.refreshOnlineItems(plugin);
        eventowkiTasks.add(plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            () -> NowyExcalibur.refreshOnlineItems(plugin),
            400L,
            400L
        ));
        registerEventowkiListener(new WampirzeJablko(plugin));
        registerEventowkiListener(new Piernik(plugin, regionHandler));
        registerEventowkiListener(new CiepleMleko(plugin, regionHandler));
        registerEventowkiListener(new LopataGrincza(plugin, regionHandler));
        registerEventowkiListener(new SmoczyMiecz(plugin, regionHandler));
        registerEventowkiListener(new JajkoOlafa(plugin, regionHandler));
        registerEventowkiListener(new CudownaLatarnia(plugin, regionHandler));
        registerEventowkiListener(new RozdzkaIluzjonisty(plugin, regionHandler));
        registerEventowkiListener(new Sniezynka(plugin, regionHandler));
        registerEventowkiListener(new Dynamit(plugin, regionHandler));
        registerEventowkiListener(new MarchewkowaKusza(plugin, regionHandler));
        registerEventowkiListener(new LukKupidyna(plugin, regionHandler));
        registerEventowkiListener(new RogJednorozca(plugin, regionHandler));
        registerEventowkiListener(new PiekielnyMiecz(plugin, regionHandler));
        registerEventowkiListener(new SuperNieskonczonaMarchewka(plugin, regionHandler));
        registerEventowkiListener(new dev.grzybalson.anarchiagg.customitems.items.AnarchiczneSerce(plugin, regionHandler));
        registerEventowkiListener(new dev.grzybalson.anarchiagg.customitems.items.UsuwaczPajeczyn(plugin, regionHandler));
        registerEventowkiListener(new Kokos(plugin, regionHandler));
        registerEventowkiListener(new BombaKapitanaHak(plugin, regionHandler));
        registerEventowkiListener(new LinaCecco(plugin, regionHandler));
        registerEventowkiListener(new UsciskVadera(plugin, regionHandler));
        registerEventowkiListener(new RetrospekcjaSkywalkera(plugin, regionHandler));

        EventowkiCommand eventowkiCommand = new EventowkiCommand(plugin);
        registerCommand("anarchiaitems", eventowkiCommand, eventowkiCommand);
        registerEventowkiListener(eventowkiCommand);
        registerEventowkiListener(new EventItemContainerListener(plugin));
        registerEventowkiListener(new DeathReset(plugin));
    }

    private void registerEventowkiListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        eventowkiListeners.add(listener);
    }

    private void enableSzafka() {
        SzafkaConfig.ensureDefaults(plugin);

        this.kostiumyDatabase = new KostiumyDatabase(plugin);
        KostiumyHandler kostiumyHandler = new KostiumyHandler(plugin, this.kostiumyDatabase);
        KostiumyArmorHandler kostiumyArmorHandler = new KostiumyArmorHandler(plugin, kostiumyHandler);
        plugin.getServer().getPluginManager().registerEvents(kostiumyArmorHandler, plugin);

        this.petyDatabase = new PetyDatabase(plugin);
        this.petyHandler = new PetyHandler(plugin, this.petyDatabase);
        PetyHandler petyHandler = this.petyHandler;

        this.papugiDatabase = new PapugiDatabase(plugin);
        PapugiHandler papugiHandler = new PapugiHandler(plugin, this.papugiDatabase);
        PapugiVisualHandler papugiVisualHandler = new PapugiVisualHandler(plugin, papugiHandler);
        plugin.getServer().getPluginManager().registerEvents(papugiVisualHandler, plugin);

        SzafaGUIHandler guiHandler = new SzafaGUIHandler(kostiumyHandler, petyHandler, papugiHandler, plugin);
        this.petyVisualHandler = new PetyVisualHandler(plugin, petyHandler, guiHandler);
        PetyVisualHandler petyVisualHandler = this.petyVisualHandler;
        plugin.getServer().getPluginManager().registerEvents(petyVisualHandler, plugin);

        plugin.getServer().getPluginManager().registerEvents(new SzafaGUIListener(guiHandler, kostiumyArmorHandler, petyVisualHandler, papugiVisualHandler), plugin);

        org.bukkit.configuration.file.YamlConfiguration szafkaConfig = SzafkaConfig.getConfig(plugin);
        dev.grzybalson.anarchiagg.szafa.RegionHandler szafkaRegionHandler = new dev.grzybalson.anarchiagg.szafa.RegionHandler(plugin, szafkaConfig);
        BoostHandler boostHandler = new BoostHandler(plugin, kostiumyHandler, petyHandler, papugiHandler, szafkaRegionHandler);
        plugin.getServer().getPluginManager().registerEvents(boostHandler, plugin);

        this.healthDisplay = new HealthDisplay(boostHandler);
        plugin.getServer().getPluginManager().registerEvents(this.healthDisplay, plugin);
        this.healthDisplay.runTaskTimer(plugin, 0L, 10L);

        ItemCommand itemCommand = new ItemCommand(plugin, kostiumyHandler, petyHandler, papugiHandler, guiHandler);
        registerCommand("aszafka", itemCommand, itemCommand);
        registerCommand("szafka", (sender, command, label, args) -> {
            if (sender instanceof Player player) {
                guiHandler.openGUI(player);
                return true;
            }
            return false;
        }, null);
    }

    private void enableKowal() {
        PvpEnable.pvpEnable(plugin);
    }

    private void enableZmianki() {
        ZmiankiManager zmiankiManager = new ZmiankiManager(plugin);
        ZmiankiGUIHandler zmiankiGUIHandler = new ZmiankiGUIHandler(zmiankiManager);

        this.zmiankaBattle = new ZmiankaBattle(plugin);
        this.zmiankaBattle.onEnable();

        ZmiankiCommand zmiankiCommand = new ZmiankiCommand(zmiankiManager, zmiankiGUIHandler, zmiankaBattle.getCommandHandler());

        registerCommand("zmianki", zmiankiCommand, zmiankiCommand);
        registerCommand("azmianki", zmiankiCommand, zmiankiCommand);
        // /kowal zostaje przy module kowala (PvpEnable). Bonus na miecz = /zmianki.

        // Osobne aliasy bitew też muszą mieć executor, ale permission dalej sprawdza ZmiankaBattleCommand.
        if (zmiankaBattle.getCommandHandler() != null && plugin.getCommand("zmiankibattle") != null) {
            plugin.getCommand("zmiankibattle").setExecutor(zmiankaBattle.getCommandHandler());
            plugin.getCommand("zmiankibattle").setTabCompleter(zmiankaBattle.getCommandHandler());
        }

        plugin.getServer().getPluginManager().registerEvents(zmiankiGUIHandler, plugin);
        plugin.getServer().getPluginManager().registerEvents(new ZmiankiUseListener(zmiankiManager, zmiankiGUIHandler), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ZmiankiDamageListener(zmiankiManager), plugin);
    }

    private void enableKsiegi() {
        KsiegiManager ksiegiManager = new KsiegiManager(plugin);
        ksiegiManager.ensureResource();
        ksiegiManager.reload();

        KsiegiCommand ksiegiCommand = new KsiegiCommand(ksiegiManager);
        registerCommand("ksiegi", ksiegiCommand, ksiegiCommand);

        plugin.getServer().getPluginManager().registerEvents(new KsiegiGUIListener(ksiegiManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new KsiegiAnvilListener(ksiegiManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new KsiegiDeathListener(plugin, ksiegiManager), plugin);

        this.ksiegiEffectsListener = new KsiegiEffectsListener(plugin, ksiegiManager);
        plugin.getServer().getPluginManager().registerEvents(this.ksiegiEffectsListener, plugin);
        // Default 60t — przy staggerze i tak każdy dostanie passywy w <3s.
        long refreshTicks = Math.max(40L, AnarchiaGGSettings.getConfig(plugin).getLong("ksiegi.refresh-ticks", 60L));
        this.ksiegiEffectsListener.runTaskTimer(plugin, 0L, refreshTicks);
    }


    private void registerDisabledCommand(String commandName, String moduleName) {
        hideDisabledCommands(List.of(commandName));
    }

    private void hideDisabledCommands(List<String> commandNames) {
        plugin.hidePluginCommands(commandNames);
    }

    private boolean registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("Nie mozna zarejestrowac komendy /" + name + ", bo nie ma jej w plugin.yml.");
            return false;
        }
        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
        return true;
    }

    public PetyHandler getPetyHandler() {
        return petyHandler;
    }
}
