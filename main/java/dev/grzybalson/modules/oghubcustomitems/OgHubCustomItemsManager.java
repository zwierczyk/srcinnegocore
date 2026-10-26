package dev.grzybalson.modules.oghubcustomitems;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.grzybalson.HollyCore;
import dev.grzybalson.anarchiagg.customitems.util.EventItemTools;
import dev.grzybalson.anarchiagg.customitems.util.EventowkiEffects;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.managers.MultiActionBarService;
import dev.grzybalson.utils.ColorUtils;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class OgHubCustomItemsManager implements Listener, CommandExecutor, TabCompleter {
    private static final String RESOURCE_PATH = "addons/oghub-customitems/oghub-customitems.yml";
    private static final String ADDON_NAME = "oghub-customitems";
    private static final int DISPLAY_VERSION = 7;
    private static final int GUI_SIZE = 54;
    private static final int GUI_CONTENT_SLOTS = 36; // rzędy 1-4
    private static final int GUI_BAG_SLOT = 36; // pierwszy slot 5. rzędu (36-44)
    private static final int GUI_PREV_SLOT = 45;
    private static final int GUI_PAGE_SLOT = 49;
    private static final int GUI_NEXT_SLOT = 53;
    private static final String GUI_NAV_PREV = "oghub-gui-prev";
    private static final String GUI_NAV_NEXT = "oghub-gui-next";
    private static final String GUI_NAV_PAGE = "oghub-gui-page";
    private static final String WOREK_NINJA_NAME =
        "&x&1&A&1&A&1&A&lᴡ&x&2&4&2&4&2&4&lᴏ&x&2&E&2&E&2&E&lʀ&x&3&8&3&8&3&8&lᴇ&x&4&2&4&2&4&2&lᴋ&x&4&C&4&C&4&C&l &x&5&6&5&6&5&6&lɴ&x&6&0&6&0&6&0&lɪ&x&6&A&6&A&6&A&lɴ&x&7&4&7&4&7&4&lᴊ&x&7&E&7&E&7&E&lᴀ";
    private static final String LASKA_MISTRZA_WU_NAME =
        "&x&F&F&D&9&0&0&lʟ&x&F&F&C&C&0&0&lᴀ&x&F&F&B&F&0&0&lꜱ&x&F&F&B&2&0&0&lᴋ&x&F&F&A&5&0&0&lᴀ&x&F&F&9&8&0&0&l &x&F&F&8&B&0&0&lᴍ&x&F&F&7&E&0&0&lɪ&x&F&F&7&1&0&0&lꜱ&x&F&F&6&4&0&0&lᴛ&x&F&F&5&7&0&0&lʀ&x&F&F&4&A&0&0&lᴀ&x&F&F&3&D&0&0&l &x&F&F&3&0&0&0&lᴡ&x&F&F&2&3&0&0&lᴜ";
    private static final List<String> WOREK_NINJA_LORE = List.of(
        "",
        "&8× &7Trzymając &fworek ninja &7w ekwipunku",
        "&8× &7Trafiają do niego wszystkie &ecenne",
        "&8× &7przedmioty z &czabitych graczy&7!",
        "",
        "&8× &7Kliknij &cprawym&7, aby otworzyć!",
        "",
        "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
    );
    private static final String ITEM_TAG = "oghub-custom-item-id";
    private static final String PROJECTILE_TAG = "oghub-custom-projectile-id";
    private static final Set<Material> PROJECTILE_MATERIALS = Set.of(Material.SNOWBALL, Material.SPLASH_POTION, Material.LINGERING_POTION);
    private static final Set<Material> SAND_CASTLE_PLACEABLE = Set.of(Material.COBWEB, Material.OAK_PLANKS, Material.WHITE_WOOL);
    private static final double SAND_CASTLE_BARRIER_EXTRA_BLOCKS = 7.0;

    private final HollyCore plugin;
    private final File configFile;
    private final NamespacedKey itemKey;
    private final NamespacedKey projectileKey;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<Material, List<Definition>> definitionsByMaterial = new EnumMap<>(Material.class);
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> turtleShellUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> vaderStunnedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> winterHeartUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dragonFangUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> easyBoostScaleUntil = new ConcurrentHashMap<>();
    /** Gracze, których Easy Boost zmniejszył — muszą wrócić do 1.0 (nawet po DC / fail timerze). */
    private final Set<UUID> easyBoostNeedsReset = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> easyBoostScaleTasks = new ConcurrentHashMap<>();
    private BukkitTask easyBoostWatchdog;
    private final Map<UUID, BukkitTask> vaderActionBars = new ConcurrentHashMap<>();
    private final Map<UUID, SkywalkerRecall> skywalkerRecalls = new ConcurrentHashMap<>();
    private final Map<UUID, SandCastleFight> sandCastles = new ConcurrentHashMap<>();
    private final Set<UUID> sandCastleLocked = ConcurrentHashMap.newKeySet();
    private final List<List<BlockState>> activeTraps = Collections.synchronizedList(new ArrayList<>());

    // Nowe eventowki
    private final Map<UUID, Long> serceWampiraUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> egzekutorUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> egzekutorDamageUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> egzekutorModes = new ConcurrentHashMap<>();
    private final Map<UUID, MaskaNpcRun> maskaRuns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<PotionEffectType, ActivePotionEffect>> activePotionEffects = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> egzekutorTasks = new ConcurrentHashMap<>();
    private final Map<UUID, String> rdzenNeutralizacjiMode = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rdzenNeutralizacjiSelfUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rdzenNeutralizacjiOthersUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> rdzenActionBars = new ConcurrentHashMap<>();

    private YamlConfiguration config;
    private BukkitTask bootsTask;
    private boolean sandCastlePacketListenerRegistered;

    public OgHubCustomItemsManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, ADDON_NAME), ADDON_NAME + ".yml");
        this.itemKey = new NamespacedKey(plugin, ITEM_TAG);
        this.projectileKey = new NamespacedKey(plugin, PROJECTILE_TAG);
        reload();
        startBootsTask();
        startEasyBoostWatchdog();
        registerSandCastlePacketListener();
        plugin.getServer().getPluginManager().registerEvents(new EasyBoostMigrationListener(plugin, this), plugin);
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        this.config = YamlConfiguration.loadConfiguration(configFile);
        ensureCobwebRemoverDefault();
        ensureEventowkiDefaults();
        migrateStarChestplateToEasyBoost();
        refreshDisplaysFromResource();
        this.definitions.clear();
        this.definitionsByMaterial.clear();

        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return;
        }

        for (String rawId : section.getKeys(false)) {
            if (!config.getBoolean("items." + rawId + ".enabled", true)) {
                continue;
            }

            ItemStack item = loadItem(rawId);
            if (item == null || item.getType().isAir()) {
                plugin.getLogger().warning("Pominieto OGHub custom item " + rawId + ": nieprawidlowy material/item.");
                continue;
            }

            String id = normalizeId(rawId);
            ActionType action = parseAction(config.getString("items." + rawId + ".action", "NONE"));
            int slot = config.getInt("items." + rawId + ".slot", definitions.size());
            Definition definition = new Definition(id, rawId, action, slot, tagItem(item, id));
            definitions.put(id, definition);
            definitionsByMaterial.computeIfAbsent(definition.item().getType(), ignored -> new ArrayList<>()).add(definition);
        }
        refreshOnlineCustomItemDisplays();
    }

    private void refreshOnlineCustomItemDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean changed = false;
            PlayerInventory inventory = player.getInventory();
            for (ItemStack item : inventory.getContents()) {
                changed |= refreshConfiguredDisplay(item);
            }
            for (ItemStack item : inventory.getArmorContents()) {
                changed |= refreshConfiguredDisplay(item);
            }
            for (ItemStack item : player.getEnderChest().getContents()) {
                changed |= refreshConfiguredDisplay(item);
            }
            for (ItemStack item : player.getOpenInventory().getTopInventory().getContents()) {
                changed |= refreshConfiguredDisplay(item);
            }
            if (changed) {
                player.updateInventory();
            }
        }
    }

    private boolean refreshConfiguredDisplay(ItemStack item) {
        String id = identifyTaggedOnly(item);
        Definition definition = getDefinition(id);
        if (definition == null) {
            return false;
        }
        ItemMeta currentMeta = item.getItemMeta();
        ItemMeta configuredMeta = definition.item().getItemMeta();
        if (currentMeta == null || configuredMeta == null) {
            return false;
        }
        currentMeta.setDisplayName(configuredMeta.hasDisplayName() ? configuredMeta.getDisplayName() : null);
        currentMeta.setLore(configuredMeta.hasLore() ? configuredMeta.getLore() : null);
        item.setItemMeta(currentMeta);
        return true;
    }

    private void migrateStarChestplateToEasyBoost() {
        if (!config.contains("items.star-chestplate")) {
            return;
        }
        if (config.contains("items.easy-boost")) {
            config.set("items.star-chestplate", null);
            saveConfig();
            return;
        }
        ConfigurationSection old = config.getConfigurationSection("items.star-chestplate");
        if (old == null) {
            return;
        }
        config.set("items.easy-boost.enabled", true);
        config.set("items.easy-boost.action", "EASY_BOOST");
        config.set("items.easy-boost.slot", old.getInt("slot", 19));
        config.set("items.easy-boost.material", "POTION");
        config.set("items.easy-boost.name",
            "&x&3&D&C&1&F&F&lᴇ&x&4&8&C&6&F&F&lᴀ&x&5&4&C&B&F&F&lꜱ&x&5&F&D&0&F&F&lʏ&x&6&A&D&5&F&F&l &x&7&5&D&A&F&F&lʙ&x&8&1&D&F&F&F&lᴏ&x&8&C&E&4&F&F&lᴏ&x&9&7&E&9&F&F&lꜱ&x&A&2&E&E&F&F&lᴛ");
        config.set("items.easy-boost.lore", List.of(
            "",
            "&8× &7Po &ckliknięciu &7prawym kurczysz się",
            "&8× &7do skali &f0.4 &7na &e5 sekund",
            "&8× &7oraz dostajesz &cSiłę I &7na &f1 minutę&7!",
            "",
            "&8× &7Cooldown: &f2 minuty",
            "",
            "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
        ));
        config.set("items.easy-boost.glow", true);
        config.set("items.easy-boost.custom-model-data", 1);
        config.set("items.easy-boost.cooldown-seconds", 120);
        config.set("items.easy-boost.scale", 0.4);
        config.set("items.easy-boost.scale-seconds", 5);
        config.set("items.easy-boost.strength-seconds", 60);
        config.set("items.easy-boost.strength-amplifier", 0);
        config.set("items.easy-boost.consume", false);
        config.set("items.star-chestplate", null);
        saveConfig();
    }

    private void ensureCobwebRemoverDefault() {
        boolean changed = false;

        if (!config.contains("items.usuwacz-pajeczyn")) {
            int slot = findFreeConfiguredSlot(19);
            String path = "items.usuwacz-pajeczyn";
            config.set(path + ".enabled", true);
            config.set(path + ".action", "COBWEB_REMOVER");
            config.set(path + ".slot", slot);
            config.set(path + ".material", "BLAZE_POWDER");
            config.set(path + ".name", "&x&F&F&3&B&3&B&lᴜ&x&F&F&4&1&3&7&lꜱ&x&F&F&4&8&3&3&lᴜ&x&F&F&4&E&2&F&lᴡ&x&F&F&5&5&2&B&lᴀ&x&F&F&5&B&2&7&lᴄ&x&F&F&6&1&2&3&lᴢ&x&F&F&6&8&1&F&l &x&F&F&6&E&1&C&lᴘ&x&F&F&7&5&1&8&lᴀ&x&F&F&7&B&1&4&lᴊ&x&F&F&8&1&1&0&lᴇ&x&F&F&8&8&0&C&lᴄ&x&F&F&8&E&0&8&lᴢ&x&F&F&9&5&0&4&lʏ&x&F&F&9&B&0&0&lɴ");
            config.set(path + ".lore", List.of(
                "",
                "&8× &7Kliknij &cprawym&7, aby usunąć &fcobweby",
                "&8× &7w promieniu &e3 kratek &7wokół Ciebie!",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ));
            config.set(path + ".glow", true);
            config.set(path + ".cooldown-seconds", 10);
            config.set(path + ".teparca-cooldown-seconds", 5);
            config.set(path + ".radius", 3);
            config.set(path + ".consume", false);
            changed = true;
        }

        if (config.contains("items.antiweb") && !config.contains("items.antiweb.teparca-cooldown-seconds")) {
            config.set("items.antiweb.teparca-cooldown-seconds", 5);
            changed = true;
        }
        if (config.contains("items.usuwacz-pajeczyn") && !config.contains("items.usuwacz-pajeczyn.teparca-cooldown-seconds")) {
            config.set("items.usuwacz-pajeczyn.teparca-cooldown-seconds", 5);
            changed = true;
        }

        int slot = config.getInt("items.usuwacz-pajeczyn.slot", 19);
        int requiredSize = ((Math.max(0, slot) / 9) + 1) * 9;
        int currentSize = config.getInt("settings.gui-size", 27);
        if (currentSize < requiredSize) {
            config.set("settings.gui-size", Math.min(54, Math.max(27, requiredSize)));
            changed = true;
        }

        if (changed) {
            try {
                config.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Nie udało się dopisać Usuwacza Pajęczyn do OGHub CustomItems config.yml", exception);
            }
        }
    }

    private void ensureEventowkiDefaults() {
        boolean changed = false;
        changed |= ensureEventItem(
            "kokos",
            "KOKOS",
            20,
            "GOLDEN_APPLE",
            "&6&lKokos",
            List.of(
                "",
                "&8× &7Trzymając &cprawy &7zjadasz &fkokosa&7,",
                "&8× &7aby zyskać &eOdporność I &7(30s), &dRegenerację II &7(20s)",
                "&8× &7i &c6 złotych serc&7. Przedmiot jest &fjednorazowy&7!",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("cooldown-seconds", 0),
                Map.entry("resistance-seconds", 30),
                Map.entry("regen-seconds", 20),
                Map.entry("golden-hearts", 6),
                Map.entry("consume", true)
            )
        );
        changed |= ensureEventItem(
            "bombakapitana",
            "BOMBA_KAPITANA",
            21,
            "FIREWORK_STAR",
            "&c&lBomba Kapitana Hak",
            List.of(
                "",
                "&8× &7Rzucona &cPPM &7bomba wybucha, &foślepiając",
                "&8× &7graczy w promieniu &e5 kratek &7oraz &cblokując",
                "&8× &7używanie &felytry &7na &f2s&7. Przedmiot jest &fjednorazowy&7!",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("cooldown-seconds", 5),
                Map.entry("radius", 5),
                Map.entry("blindness-ticks", 100),
                Map.entry("elytra-lock-millis", 2000),
                Map.entry("consume", true)
            )
        );
        changed |= ensureEventItem(
            "linacecco",
            "LINA_CECCO",
            22,
            "LEAD",
            "&e&lLina Cecco",
            List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym wyrzucasz &flinę",
                "&8× &7na odległość &f15 kratek&7. Trafiony",
                "&8× &7gracz zostanie &cprzyciągnięty &7do Ciebie!",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("cooldown-seconds", 0),
                Map.entry("range", 15),
                Map.entry("consume", false)
            )
        );
        changed |= ensureEventItem(
            "usciskvadera",
            "USCISK_VADERA",
            23,
            "BLACK_DYE",
            "&8&lUścisk Vadera",
            List.of(
                "",
                "&8× &7Po &cuderzeniu &7gracza tym &fprzedmiotem",
                "&8× &7Unosisz go w &fgórę &7na &e2 kratki",
                "&8× &7oraz całkowicie &cblokujesz &7jego &fakcje!",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("cooldown-seconds", 0),
                Map.entry("stun-ticks", 80),
                Map.entry("lift-blocks", 2.0),
                Map.entry("damage-multiplier", 0.7),
                Map.entry("consume", false)
            )
        );
        changed |= ensureEventItem(
            "retrospekcjaskywalkera",
            "RETROSPEKCJA_SKYWALKERA",
            24,
            "LIGHT_BLUE_DYE",
            "&b&lRetrospekcja Skywalkera",
            List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym zapisuje Twoją &fpozycję&7.",
                "&8× &7Po upływie &e6 sekund &7zostajesz",
                "&8× &7automatycznie &ccofnięty &7w to samo miejsce!",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("cooldown-seconds", 0),
                Map.entry("delay-ticks", 120),
                Map.entry("rewind-ticks", 16),
                Map.entry("consume", false)
            )
        );
        changed |= ensureEventItem(
            "torba",
            "TORBA",
            GUI_BAG_SLOT,
            "BROWN_DYE",
            WOREK_NINJA_NAME,
            WOREK_NINJA_LORE,
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 5),
                Map.entry("consume", false)
            )
        );
        changed |= ensureEventItem(
            "kartypiekla",
            "KARTY_PIEKLA",
            25,
            "RED_DYE",
            "&x&F&F&2&D&2&D&lᴋ&x&F&F&3&A&2&A&lᴀ&x&F&F&4&7&2&7&lʀ&x&F&F&5&4&2&4&lᴛ&x&F&F&6&1&2&1&lʏ&x&F&F&6&E&1&E&l &x&F&F&7&B&1&B&lᴘ&x&F&F&8&8&1&8&lɪ&x&F&F&9&5&1&5&lᴇ&x&F&F&A&2&1&2&lᴋ&x&F&F&A&F&0&F&lʟ&x&F&F&B&C&0&C&lᴀ",
            List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym osoby w &f5 &7kratkach",
                "&8× &7Losują &cSiłę I&7, &8Spowolnienie III",
                "&8× &7lub &dRegen V &7na &f10 sekund&7!",
                "",
                "&8× &cPrzedmiot jednorazowy"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 0),
                Map.entry("radius", 5),
                Map.entry("effect-duration-seconds", 10),
                Map.entry("consume", true)
            )
        );
        changed |= ensureEventItem(
            "magicznykrysztal",
            "MAGICZNY_KRYSZTAL",
            26,
            "AMETHYST_SHARD",
            "&x&B&2&6&6&F&F&lᴍ&x&B&8&7&0&F&F&lᴀ&x&B&E&7&A&F&F&lɢ&x&C&4&8&4&F&F&lɪ&x&C&A&8&E&F&F&lᴄ&x&D&0&9&8&F&F&lᴢ&x&D&6&A&2&F&F&lɴ&x&D&C&A&C&F&F&lʏ&x&E&2&B&6&F&F&l &x&E&8&C&0&F&F&lᴋ&x&E&E&C&A&F&F&lʀ&x&F&4&D&4&F&F&lʏ&x&F&A&D&E&F&F&lꜱ&x&F&F&E&8&F&F&lᴢ&x&F&F&F&2&F&F&lᴛ&x&F&F&F&F&F&F&lᴀ&x&F&F&F&F&F&F&lʟ",
            List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym tym &fkryształem",
                "&8× &7Otrzymujesz &e10 złotych serc",
                "&8× &7które chronią Cię w walce!",
                "",
                "&8× &cPrzedmiot jednorazowy"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 0),
                Map.entry("golden-hearts", 10),
                Map.entry("absorption-duration-seconds", 120),
                Map.entry("consume", true)
            )
        );
        changed |= ensureEventItem(
            "laskamistrzawu",
            "LASKA_MISTRZA_WU",
            27,
            "BLAZE_ROD",
            LASKA_MISTRZA_WU_NAME,
            List.of(
                "",
                "&8× &7Po &cuderzeniu &7gracza tą &flaską",
                "&8× &7Odrzucasz go mocą &cOdrzutu II&7!",
                "&8× &7Cooldown użycia: &f70 sekund",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 2),
                Map.entry("cooldown-seconds", 70),
                Map.entry("knockback-level", 2),
                Map.entry("consume", false)
            )
        );
        changed |= ensureEventItem(
            "sercezimy",
            "SERCE_ZIMY",
            28,
            "BLUE_ICE",
            "&x&7&F&D&F&F&F&lꜱ&x&7&0&D&5&F&F&lᴇ&x&6&1&C&B&F&F&lʀ&x&5&2&C&1&F&F&lᴄ&x&4&3&B&7&F&F&lᴇ&x&3&4&A&D&F&F&l &x&2&5&A&3&F&F&lᴢ&x&1&6&9&9&F&F&lɪ&x&0&7&8&F&F&F&lᴍ&x&0&0&8&5&F&F&lʏ",
            List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym zostajesz",
                "&8× &bzamrożony &7na &f4 sekundy&7!",
                "&8× &7Nie ruszasz się, ale &fnikt Cię nie bije",
                "&8× &7Możesz normalnie &fjeść",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 0),
                Map.entry("duration-seconds", 4),
                Map.entry("consume", false)
            )
        );
        changed |= ensureEventItem(
            "smoczykiel",
            "SMOCZY_KIEL",
            29,
            "PRISMARINE_SHARD",
            "&x&C&8&0&0&F&F&lꜱ&x&C&0&0&0&F&0&lᴍ&x&B&8&0&0&E&1&lᴏ&x&B&0&0&0&D&2&lᴄ&x&A&8&0&0&C&3&lᴢ&x&A&0&0&0&B&4&lʏ&x&9&8&0&0&A&5&l &x&9&0&0&0&9&6&lᴋ&x&8&8&0&0&8&7&lɪ&x&8&0&0&0&7&8&lᴇ&x&7&8&0&0&6&9&lʟ",
            List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym przez &f4s",
                "&8× &7Masz &e10% &7szans, że uderzony gracz",
                "&8× &7Dostanie &fpiorun &7za &c2.5 HP&7!",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 0),
                Map.entry("duration-seconds", 4),
                Map.entry("lightning-chance", 10.0),
                Map.entry("lightning-damage", 5.0),
                Map.entry("consume", false)
            )
        );

        changed |= ensureEventItem(
            "sercewampira",
            "SERCE_WAMPIRA",
            30,
            "ORANGE_DYE",
            "&x&F&F&6&B&6&B&lꜱ&x&F&F&5&E&5&E&lᴇ&x&F&F&5&1&5&1&lʀ&x&F&F&4&4&4&4&lᴄ&x&F&F&3&7&3&7&lᴇ&x&F&F&2&A&2&A&l &x&F&F&1&D&1&D&lᴡ&x&F&F&1&0&1&0&lᴀ&x&F&F&0&3&0&3&lᴍ&x&E&E&0&0&0&0&lᴘ&x&D&D&0&0&0&0&lɪ&x&C&C&0&0&0&0&lʀ&x&B&B&0&0&0&0&lᴀ",
            List.of(
                "",
                "&8× &7Kliknij &cprawym &7przyciskiem, aby",
                "&8× &7aktywować efekt na &f5 sekund&7!",
                "",
                "&8× &7Podczas efektu każde Twoje uderzenie",
                "&8× &7leczy Cię za &c30% &7zadanych obrażeń.",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 0),
                Map.entry("heal-percentage", 0.30),
                Map.entry("duration-ticks", 100),
                Map.entry("consume", false)
            )
        );

        changed |= ensureEventItem(
            "egzekutor",
            "EGZEKUTOR",
            31,
            "PURPLE_DYE",
            "&e&lEGZEKUTOR",
            List.of(
                "",
                "&8× &7Kliknij &cprawym &7przyciskiem, aby",
                "&8× &7wystrzelić w cel &cstrumień ognia&7.",
                "&8× &7Shift + PPM zmienia tryb:",
                "&8× &eBlokada &7— podświetlenie i blokada elytry",
                "&8× &cEgzekucja &7— podświetlenie i &c+15% obrażeń",
                "",
                "&8× &7Zasięg: &f30 kratek",
                "&8× &7Cooldown: &f10 sekund",
                "",
                "&8× &7Przedmiot pochodzi ze skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 10),
                Map.entry("range", 30.0),
                Map.entry("elytra-lock-ticks", 100),
                Map.entry("damage-multiplier", 1.15),
                Map.entry("glowing-duration-ticks", 100),
                Map.entry("consume", false)
            )
        );

        changed |= ensureEventItem(
            "maskazludzenia",
            "MASKA_ZLUDZENIA",
            32,
            "MAGENTA_DYE",
            "&x&F&F&6&B&F&F&lᴍ&x&F&F&6&0&F&F&lᴀ&x&F&F&5&5&F&F&lꜱ&x&F&F&4&A&F&F&lᴋ&x&F&F&3&F&F&F&lᴀ&x&F&F&3&4&F&F&l &x&F&F&2&9&F&F&lᴢ&x&F&F&1&E&F&F&lʟ&x&F&F&1&3&F&F&lᴜ&x&F&F&0&8&F&F&lᴅ&x&E&E&0&0&F&F&lᴢ&x&D&D&0&0&F&F&lᴇ&x&C&C&0&0&F&F&lɴ&x&B&B&0&0&F&F&lɪ&x&A&A&0&0&F&F&lᴀ",
            List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym tworzysz swojego",
                "&8× &fklona&7, który idzie do przodu!",
                "",
                "&8× &7Klon nosi Twój &fskóra i ekwipunek&7,",
                "&8× &7może zmylić przeciwników &fna arenie&7!",
                "",
                "&8× &7Czas trwania: &f10 sekund",
                "&8× &7Cooldown: &f30 sekund",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 30),
                Map.entry("duration", 10),
                Map.entry("npc-speed", 0.2),
                Map.entry("consume", false)
            )
        );

        changed |= ensureEventItem(
            "rdzenneutralizacji",
            "RDZEN_NEUTRALIZACJI",
            33,
            "LIME_DYE",
            "&x&0&0&F&F&0&0&lʀ&x&1&0&F&0&0&0&lᴅ&x&2&0&E&1&0&0&lᴢ&x&3&0&D&2&0&0&lᴇ&x&4&0&C&3&0&0&lɴ&x&5&0&B&4&0&0&l &x&6&0&A&5&0&0&lɴ&x&7&0&9&6&0&0&lᴇ&x&8&0&8&7&0&0&lᴜ&x&9&0&7&8&0&0&lᴛ&x&A&0&6&9&0&0&lʀ&x&B&0&5&A&0&0&lᴀ&x&C&0&4&B&0&0&lʟ&x&D&0&3&C&0&0&lɪ&x&E&0&2&D&0&0&lᴢ&x&F&0&1&E&0&0&lᴀ&x&F&F&0&F&0&0&lᴄ&x&F&F&0&0&0&0&lᴊ&x&F&F&0&0&0&0&lɪ",
            List.of(
                "",
                "&8× &7Ma &f2 tryby&7: &aDla Ciebie &7lub &cDla Graczy Obok",
                "&8× &eShift + LPM &7na &fSpawnie &7zmienia tryb!",
                "",
                "&8× &aTryb: Dla Ciebie",
                "&8× &7Anuluje &cwszystkie eventówki &7działające na Ciebie",
                "&8× &7(np. zamek, pistolet, serce zimowe)",
                "",
                "&8× &cTryb: Dla Graczy Obok",
                "&8× &7Anuluje eventówki dla graczy w promieniu &f5 kratek",
                "&8× &7(np. wyłącza ich serce wampira, zamek itp.)",
                "",
                "&8× &7Czas działania: &f10 sekund",
                "&8× &7Cooldown: &f30 sekund",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ),
            Map.<String, Object>ofEntries(
                Map.entry("custom-model-data", 1),
                Map.entry("cooldown-seconds", 30),
                Map.entry("duration-ticks", 200),
                Map.entry("radius", 5.0),
                Map.entry("consume", false)
            )
        );

        String egzekutorPath = "items.egzekutor";
        if (config.contains(egzekutorPath)) {
            if (!"&e&lEGZEKUTOR".equals(config.getString(egzekutorPath + ".name", ""))) {
                config.set(egzekutorPath + ".name", "&e&lEGZEKUTOR");
                changed = true;
            }
            List<String> lore = new ArrayList<>(config.getStringList(egzekutorPath + ".lore"));
            if (lore.stream().noneMatch(line -> line.toLowerCase(Locale.ROOT).contains("shift + ppm"))) {
                int at = lore.indexOf("&8× &7Przedmiot pochodzi z skrzyni &dmistycznej");
                if (at < 0) at = lore.size();
                lore.add(at++, "&8× &7Shift + PPM zmienia tryb:");
                lore.add(at++, "&8× &eBlokada &7— podświetlenie i blokada elytry");
                lore.add(at, "&8× &cEgzekucja &7— podświetlenie i &c+15% obrażeń");
                config.set(egzekutorPath + ".lore", lore);
                changed = true;
            }
        }

        if (config.contains("items.kokos") && !config.contains("items.kokos.item")
            && !"GOLDEN_APPLE".equalsIgnoreCase(config.getString("items.kokos.material", ""))) {
            config.set("items.kokos.material", "GOLDEN_APPLE");
            changed = true;
        }

        if (config.contains("items.torba")) {
            if (!"BROWN_DYE".equalsIgnoreCase(config.getString("items.torba.material", ""))) {
                config.set("items.torba.material", "BROWN_DYE");
                changed = true;
            }
            if (config.getInt("items.torba.slot", -1) != GUI_BAG_SLOT) {
                config.set("items.torba.slot", GUI_BAG_SLOT);
                changed = true;
            }
            if (config.getInt("items.torba.custom-model-data", 0) != 1) {
                config.set("items.torba.custom-model-data", 1);
                changed = true;
            }
            // name/lore bierz z configu — nie nadpisuj hardcoded WOREK_NINJA_*
        }

        for (String key : List.of("kartypiekla", "magicznykrysztal", "sercezimy", "smoczykiel")) {
            if (!config.contains("items." + key)) {
                continue;
            }
            if (config.getInt("items." + key + ".custom-model-data", 0) != 1) {
                config.set("items." + key + ".custom-model-data", 1);
                changed = true;
            }
        }
        if (config.contains("items.laskamistrzawu")) {
            if (config.getInt("items.laskamistrzawu.custom-model-data", 0) != 2) {
                config.set("items.laskamistrzawu.custom-model-data", 2);
                changed = true;
            }
        }

        if (config.contains("items.kartypiekla")
            && !"RED_DYE".equalsIgnoreCase(config.getString("items.kartypiekla.material", ""))) {
            config.set("items.kartypiekla.material", "RED_DYE");
            changed = true;
        }

        if (config.contains("items.laskamistrzawu")
            && !LASKA_MISTRZA_WU_NAME.equals(config.getString("items.laskamistrzawu.name", ""))) {
            config.set("items.laskamistrzawu.name", LASKA_MISTRZA_WU_NAME);
            changed = true;
        }

        if (config.getInt("settings.gui-size", 27) < GUI_SIZE) {
            config.set("settings.gui-size", GUI_SIZE);
            changed = true;
        }

        if (config.contains("items.sercezimy") && config.getBoolean("items.sercezimy.consume", true)) {
            config.set("items.sercezimy.consume", false);
            config.set("items.sercezimy.lore", List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym zostajesz",
                "&8× &bzamrożony &7na &f4 sekundy&7!",
                "&8× &7Nie ruszasz się, ale &fnikt Cię nie bije",
                "&8× &7Możesz normalnie &fjeść",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ));
            changed = true;
        }
        if (config.contains("items.smoczykiel") && config.getBoolean("items.smoczykiel.consume", true)) {
            config.set("items.smoczykiel.consume", false);
            config.set("items.smoczykiel.lore", List.of(
                "",
                "&8× &7Po &ckliknięciu &7prawym przez &f4s",
                "&8× &7Masz &e10% &7szans, że uderzony gracz",
                "&8× &7Dostanie &fpiorun &7za &c2.5 HP&7!",
                "",
                "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
            ));
            changed = true;
        }

        int maxSlot = 0;
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                maxSlot = Math.max(maxSlot, config.getInt("items." + key + ".slot", 0));
            }
        }
        int requiredSize = ((Math.max(0, maxSlot) / 9) + 1) * 9;
        int currentSize = config.getInt("settings.gui-size", 27);
        if (currentSize < requiredSize) {
            config.set("settings.gui-size", Math.min(54, Math.max(27, requiredSize)));
            changed = true;
        }

        if (changed) {
            try {
                config.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Nie udało się dopisać eventówek do OGHub CustomItems.", exception);
            }
        }
    }

    private static boolean isPassiveEventowka(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "DRAGON_FIREWORK", "LAVA_BOOTS", "SONIC_BOOTS", "STAR_CHESTPLATE" -> true;
            default -> false;
        };
    }

    private void refreshDisplaysFromResource() {
        if (config.getInt("settings.display-version", 0) >= DISPLAY_VERSION) {
            return;
        }
        YamlConfiguration resource;
        try (InputStream stream = plugin.getResource(RESOURCE_PATH)) {
            if (stream == null) {
                return;
            }
            resource = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie wczytac display OGHub CustomItems z jar.", exception);
            return;
        }

        ConfigurationSection items = resource.getConfigurationSection("items");
        if (items == null) {
            return;
        }
        boolean changed = false;
        for (String key : items.getKeys(false)) {
            if (!config.contains("items." + key)) {
                continue;
            }
            String name = resource.getString("items." + key + ".name");
            List<String> lore = resource.getStringList("items." + key + ".lore");
            if (name != null && !name.isBlank()) {
                config.set("items." + key + ".name", name);
                changed = true;
            }
            if (!lore.isEmpty()) {
                config.set("items." + key + ".lore", lore);
                changed = true;
            }
        }
        config.set("settings.display-version", DISPLAY_VERSION);
        changed = true;
        if (changed) {
            try {
                config.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac display OGHub CustomItems.", exception);
            }
        }
    }

    private boolean ensureEventItem(
        String key,
        String action,
        int preferredSlot,
        String material,
        String name,
        List<String> lore,
        Map<String, Object> extras
    ) {
        if (config.contains("items." + key)) {
            return false;
        }
        String path = "items." + key;
        config.set(path + ".enabled", true);
        config.set(path + ".action", action);
        config.set(path + ".slot", findFreeConfiguredSlot(preferredSlot));
        config.set(path + ".material", material);
        config.set(path + ".name", name);
        config.set(path + ".lore", lore);
        config.set(path + ".glow", false);
        config.set(path + ".custom-model-data", 1);
        config.set(path + ".cooldown-seconds", 0);
        extras.forEach((extraKey, extraValue) -> config.set(path + "." + extraKey, extraValue));
        return true;
    }

    private int findFreeConfiguredSlot(int preferredSlot) {
        Set<Integer> usedSlots = new HashSet<>();
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                usedSlots.add(config.getInt("items." + key + ".slot", -1));
            }
        }
        if (!usedSlots.contains(preferredSlot)) {
            return preferredSlot;
        }
        for (int slot = 0; slot < 54; slot++) {
            if (!usedSlots.contains(slot)) {
                return slot;
            }
        }
        return preferredSlot;
    }

    public void shutdown() {
        if (bootsTask != null) {
            bootsTask.cancel();
            bootsTask = null;
        }
        if (easyBoostWatchdog != null) {
            easyBoostWatchdog.cancel();
            easyBoostWatchdog = null;
        }
        for (UUID uuid : Set.copyOf(easyBoostScaleTasks.keySet())) {
            cancelEasyBoostScaleTask(uuid);
        }
        for (UUID uuid : Set.copyOf(easyBoostNeedsReset)) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                applyPlayerScale(online, 1.0D);
            }
        }
        easyBoostScaleUntil.clear();
        easyBoostNeedsReset.clear();
        restoreAllTraps();
        new HashSet<>(sandCastles.values()).forEach(this::endSandCastle);
        new HashSet<>(vaderStunnedUntil.keySet()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                clearVaderStun(player);
            }
        });
        vaderStunnedUntil.clear();
        vaderActionBars.values().forEach(BukkitTask::cancel);
        vaderActionBars.clear();
        skywalkerRecalls.values().forEach(recall -> {
            recall.bar().cancel();
            recall.finish().cancel();
        });
        skywalkerRecalls.clear();
        EventowkiEffects.shutdownAll();
        cooldowns.clear();
        pendingProjectiles.clear();
        for (MaskaNpcRun run : Set.copyOf(maskaRuns.values())) run.stop();
        maskaRuns.clear();
        activePotionEffects.clear();
        egzekutorDamageUntil.clear();
        egzekutorModes.clear();
        turtleShellUntil.clear();
        winterHeartUntil.clear();
        dragonFangUntil.clear();
        serceWampiraUntil.clear();
        egzekutorUntil.clear();
        egzekutorTasks.values().forEach(BukkitTask::cancel);
        egzekutorTasks.clear();
        rdzenNeutralizacjiMode.clear();
        rdzenNeutralizacjiSelfUntil.clear();
        rdzenNeutralizacjiOthersUntil.clear();
        rdzenActionBars.values().forEach(BukkitTask::cancel);
        rdzenActionBars.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!hasAdminPermission(sender)) {
            send(sender, "settings.messages.no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player player)) {
                send(sender, "settings.messages.only-player");
                return true;
            }
            openGui(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reload();
                send(sender, "settings.messages.reloaded");
                return true;
            }
            case "resetcooldown", "resetujcooldown" -> {
                handleResetCooldown(sender, label, args);
                return true;
            }
            case "setitem" -> {
                handleSetItem(sender, label, args);
                return true;
            }
            default -> {
                ColorUtils.sendUsage(sender,
                    "/" + label + " reload",
                    "/" + label + " resetcooldown <nick|all>",
                    "/" + label + " setitem <nazwa>"
                );
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!hasAdminPermission(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(List.of("gui", "reload", "resetcooldown", "setitem"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetcooldown")) {
            List<String> values = new ArrayList<>();
            values.add("all");
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
            return filter(values, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setitem")) {
            return filter(definitions.keySet(), args[1]);
        }
        return Collections.emptyList();
    }

    public void openGui(Player player) {
        openGui(player, 0);
    }

    public void openGui(Player player, int page) {
        List<Definition> pagedItems = definitions.values().stream()
            .filter(definition -> definition.action() != ActionType.TORBA)
            .sorted(Comparator.comparingInt(Definition::slot).thenComparing(Definition::id))
            .toList();
        int totalPages = Math.max(1, (int) Math.ceil(pagedItems.size() / (double) GUI_CONTENT_SLOTS));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        GuiHolder holder = new GuiHolder(safePage, totalPages);
        Inventory inventory = Bukkit.createInventory(
            holder,
            GUI_SIZE,
            color(config.getString("settings.gui-title", "&7OGHub CustomItems"))
        );
        holder.setInventory(inventory);

        int start = safePage * GUI_CONTENT_SLOTS;
        int end = Math.min(start + GUI_CONTENT_SLOTS, pagedItems.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, pagedItems.get(index).item().clone());
        }

        Definition bag = getDefinitionByAction(ActionType.TORBA);
        if (bag != null) {
            // Cały 5. rząd zostawiamy na worek (wyraźnie row 5), reszta pusta.
            for (int slot = 36; slot <= 44; slot++) {
                inventory.setItem(slot, null);
            }
            inventory.setItem(GUI_BAG_SLOT, bag.item().clone());
        }

        // 6. rząd = nawigacja stron
        inventory.setItem(GUI_PREV_SLOT, navItem(Material.ARROW, "&7« Poprzednia", GUI_NAV_PREV,
            List.of("&8× &7Strona &f" + (safePage + 1) + "&7/&f" + totalPages)));
        inventory.setItem(GUI_PAGE_SLOT, navItem(Material.PAPER, "&fStrona &e" + (safePage + 1) + "&7/&e" + totalPages, GUI_NAV_PAGE,
            List.of("&8× &7Użyj strzałek, aby zmienić stronę")));
        inventory.setItem(GUI_NEXT_SLOT, navItem(Material.ARROW, "&7Następna »", GUI_NAV_NEXT,
            List.of("&8× &7Strona &f" + (safePage + 1) + "&7/&f" + totalPages)));

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        player.openInventory(inventory);
    }

    private ItemStack navItem(Material material, String name, String navId, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(color(line));
            }
            meta.setLore(colored);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, navId);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        String tagged = current == null || current.getItemMeta() == null
            ? null
            : current.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (GUI_NAV_PREV.equals(tagged)) {
            if (holder.page() > 0) {
                openGui(player, holder.page() - 1);
            }
            return;
        }
        if (GUI_NAV_NEXT.equals(tagged)) {
            if (holder.page() + 1 < holder.totalPages()) {
                openGui(player, holder.page() + 1);
            }
            return;
        }
        if (GUI_NAV_PAGE.equals(tagged)) {
            return;
        }

        Definition definition = getDefinition(identify(current));
        if (definition == null) {
            return;
        }

        ItemStack give = definition.item().clone();
        give.setAmount(1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(give);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
        send(player, "settings.messages.gui-give", Map.of("%item%", definition.id()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTurtleShellEquipClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean helmetSlot = isPlayerHelmetSlot(event);

        if (helmetSlot && isTurtleShellItem(cursor)) {
            event.setCancelled(true);
            unequipTurtleShell(player);
            return;
        }

        if (helmetSlot && event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0 && isTurtleShellItem(player.getInventory().getItem(hotbar))) {
                event.setCancelled(true);
                unequipTurtleShell(player);
                return;
            }
        }

        if (helmetSlot && event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
            && isTurtleShellItem(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            unequipTurtleShell(player);
            return;
        }

        if (isTurtleShellItem(current)
            && event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
            && event.getClickedInventory() instanceof PlayerInventory
            && (event.getView().getTopInventory() instanceof PlayerInventory
                || event.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING)) {
            event.setCancelled(true);
            unequipTurtleShell(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTurtleShellEquipDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            return;
        }
        if (!isTurtleShellItem(event.getOldCursor()) && !isTurtleShellItem(event.getCursor())) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (event.getView().getSlotType(rawSlot) == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR
                && event.getView().convertSlot(rawSlot) == 39) {
                event.setCancelled(true);
                unequipTurtleShell(player);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTurtleShellArmorChange(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event) {
        if (event.getSlotType() != com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.SlotType.HEAD) {
            return;
        }
        if (isTurtleShellItem(event.getNewItem())) {
            unequipTurtleShell(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTurtleShellDispense(org.bukkit.event.block.BlockDispenseArmorEvent event) {
        if (event.getTargetEntity() instanceof Player && isTurtleShellItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        ItemStack item = event.getItem();
        Definition definition = getDefinition(identifyAndTag(item));
        if (definition == null) {
            return;
        }

        Player player = event.getPlayer();
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (definition.action() == ActionType.RDZEN_NEUTRALIZACJI && player.isSneaking() && leftClick
            && event.getHand() == EquipmentSlot.HAND) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            toggleRdzenMode(player, item);
            return;
        }
        if (definition.action() == ActionType.EGZEKUTOR && player.isSneaking() && rightClick
            && event.getHand() == EquipmentSlot.HAND) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            toggleEgzekutorMode(player);
            return;
        }
        if (!rightClick) {
            return;
        }
        if (isVaderStunned(player)) {
            if (item != null && item.getType().isEdible()) {
                return;
            }
            event.setCancelled(true);
            return;
        }
        switch (definition.action()) {
            case WARDEN_HEART -> {
                event.setCancelled(true);
                if (!startUse(player, definition, item, true)) return;
                applyNearbyEffect(player, definition, PotionEffectType.BLINDNESS, false);
                playUseFeedback(player, Sound.ENTITY_WARDEN_HEARTBEAT);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case HARRY_WAND -> {
                event.setCancelled(true);
                if (!startUse(player, definition, item, true)) return;
                applyNearbyEffect(player, definition, PotionEffectType.LEVITATION, true);
                playUseFeedback(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case LAMPION -> {
                event.setCancelled(true);
                if (!startUse(player, definition, item, true)) return;
                applyEventPotionEffect(player, new PotionEffect(PotionEffectType.REGENERATION, seconds(definition, "effect-duration-seconds", 5) * 20, integer(definition, "amplifier", 1), true, true, true));
                playUseFeedback(player, Sound.BLOCK_LANTERN_PLACE);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case COBWEB_REMOVER -> {
                event.setCancelled(true);
                if (!startUse(player, definition, item, true)) return;
                removeCobwebsAround(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case TURTLE_SHELL -> {
                event.setCancelled(true);
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                unequipTurtleShell(player);
                if (!startUse(player, definition, item, true)) return;
                int duration = Math.max(1, seconds(definition, "effect-duration-seconds", 5));
                turtleShellUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 1000L);
                playUseFeedback(player, Sound.ITEM_SHIELD_BLOCK);
                send(player, "settings.messages.turtle-shell", Map.of("%time%", String.valueOf(duration)));
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case SAND_CASTLE -> {
                event.setCancelled(true);
                tryStartSandCastle(player, event.getHand(), item, definition);
            }
            case WATER_PISTOL -> {
                event.setCancelled(true);
                if (!startUse(player, definition, item, true)) return;
                shootWaterPistol(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case DRAGON_FIREWORK -> {
                if (cancelIfFireworkBlocked(event, player)) return;
                restoreItemAfterVanillaUse(player, event.getHand(), player.getInventory().getHeldItemSlot(), item, definition);
            }
            case WEB_GRENADE, TELEPORT_SNOWBALL, KNOCKBACK_GRENADE -> {
                if (!PROJECTILE_MATERIALS.contains(item.getType())) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                if (!startUse(player, definition, item, true)) {
                    return;
                }
                launchCustomProjectile(player, event.getHand(), item, definition);
            }
            case KOKOS -> {
                if (isBlocked(player.getLocation()) || getRemainingCooldownMillis(player, definition.id()) > 0L) {
                    event.setCancelled(true);
                    event.setUseItemInHand(Event.Result.DENY);
                    startUse(player, definition, item, true);
                }
            }
            case BOMBA_KAPITANA -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                throwBombaKapitana(player, item, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case LINA_CECCO -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                throwLinaCecco(player, item, definition);
            }
            case RETROSPEKCJA_SKYWALKERA -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (skywalkerRecalls.containsKey(player.getUniqueId())) {
                    return;
                }
                if (!startUse(player, definition, item, true)) return;
                startSkywalkerRecall(player, definition);
            }
            case TORBA -> {
                // Otwarcie / zbieranie dropu obsługuje KangarooBagListener (AdminItems lub rejestracja pod OGHub).
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
            }
            case KARTY_PIEKLA -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                useHellCards(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case MAGICZNY_KRYSZTAL -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                useMagicCrystal(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case LASKA_MISTRZA_WU -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
            }
            case SERCE_ZIMY -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                useWinterHeart(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case SMOCZY_KIEL -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                useDragonFang(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case SERCE_WAMPIRA -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                activateSerceWampira(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case EASY_BOOST -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                useEasyBoost(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case EGZEKUTOR -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                useEgzekutor(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case MASKA_ZLUDZENIA -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                useMaskaZludzenia(player, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case RDZEN_NEUTRALIZACJI -> {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                if (!startUse(player, definition, item, true)) return;
                useRdzenNeutralizacji(player, item, definition);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKokosPrepareEat(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENCHANTED_GOLDEN_APPLE) {
            return;
        }
        Definition definition = getDefinition(identify(item));
        if (definition == null || definition.action() != ActionType.KOKOS) {
            return;
        }
        item.setType(Material.GOLDEN_APPLE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKokosConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        Definition easyOrAny = getDefinition(identify(item));
        if (easyOrAny != null && easyOrAny.action() == ActionType.EASY_BOOST) {
            event.setCancelled(true);
            return;
        }
        ItemMeta meta = item.getItemMeta();
        String taggedId = meta == null ? null : meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        Definition definition = getDefinition(taggedId);
        if (definition == null || definition.action() != ActionType.KOKOS) {
            return;
        }
        Player player = event.getPlayer();
        if (!startUse(player, definition, item, true)) {
            event.setCancelled(true);
            return;
        }
        player.setCooldown(Material.GOLDEN_APPLE, 0);
        player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, 0);
        Bukkit.getScheduler().runTask(plugin, () -> applyKokos(player, definition));
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyKokos(player, definition), 2L);
    }

    private boolean cancelIfFireworkBlocked(PlayerInteractEvent event, Player player) {
        if (!cancelIfFireworkBlocked(player)) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    private boolean cancelIfFireworkBlocked(Player player) {
        long remainingMillis = getFireworkBlockRemainingMillis(player);
        if (remainingMillis <= 0L) {
            return false;
        }

        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        player.sendMessage(ColorUtils.colorizeComponent("&#FF0000✘ &8Ξ &cNie mozesz uzywac fajerwerek ani haka jeszcze &f" + seconds + "s&c."));
        return true;
    }

    private long getFireworkBlockRemainingMillis(Player player) {
        long remainingMillis = 0L;
        if (plugin.getSzamanManager() != null) {
            remainingMillis = Math.max(remainingMillis, plugin.getSzamanManager().getFireworkBlockRemainingMillis(player.getUniqueId()));
        }
        return remainingMillis;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }

        String id = identifyProjectile(event.getEntity());
        if (id == null) {
            id = pendingProjectiles.remove(player.getUniqueId());
        }
        if (id != null && definitions.containsKey(id)) {
            event.getEntity().getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, id);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileHit(ProjectileHitEvent event) {
        String id = event.getEntity().getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING);
        Definition definition = getDefinition(id);
        if (definition == null) {
            return;
        }

        switch (definition.action()) {
            case WEB_GRENADE -> handleWebGrenade(event, definition);
            case TELEPORT_SNOWBALL -> handleTeleportSnowball(event, definition);
            case KNOCKBACK_GRENADE -> handleKnockbackGrenade(event, definition);
            case WATER_PISTOL -> handleWaterPistolProjectile(event, definition);
            case BOMBA_KAPITANA -> handleBombaKapitanaHit(event, definition);
            case LINA_CECCO -> handleLinaCeccoHit(event, definition);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getCaught() instanceof Player hooked && isCastleDimensionIsolated(event.getPlayer(), hooked)) {
            event.setCancelled(true);
            return;
        }
        Definition definition = getHeldDefinition(event.getPlayer(), ActionType.HOOK);
        if (definition == null) {
            return;
        }

        PlayerFishEvent.State state = event.getState();
        if (state != PlayerFishEvent.State.IN_GROUND && state != PlayerFishEvent.State.REEL_IN && state != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }

        Player player = event.getPlayer();
        if (cancelIfFireworkBlocked(player)) {
            event.setCancelled(true);
            return;
        }
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!startUse(player, definition, rod, true)) {
            event.setCancelled(true);
            return;
        }

        pullPlayer(player, event.getHook().getLocation(), definition);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        resetVisualCooldowns(player);
        for (MaskaNpcRun activeMaska : maskaRuns.values()) {
            activeMaska.spawnFor(player);
        }
        unequipTurtleShell(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> hideJoinerFromCastles(player), 3L);
        // GENERIC_SCALE / Pehkui bywa zapisane w playerdata — po wejsciu zawsze zdejmij stuck scale.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Long until = easyBoostScaleUntil.get(player.getUniqueId());
            if (until != null && until > System.currentTimeMillis()) {
                return;
            }
            clearEasyBoostScale(player, true);
        }, 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !isEasyBoostScaleActive(player.getUniqueId())) {
                clearEasyBoostScale(player, true);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        SandCastleFight fight = sandCastles.remove(uuid);
        unlockSandCastle(uuid);
        if (fight != null) {
            fight.participants.remove(uuid);
            restoreCastleVisibility(event.getPlayer());
            if (!hasOnlineParticipant(fight)) {
                endSandCastle(fight);
            }
        }
        MaskaNpcRun maskaRun = maskaRuns.remove(uuid);
        if (maskaRun != null) maskaRun.stop();
        clearEventPotionEffects(event.getPlayer());
        egzekutorModes.remove(uuid);
        turtleShellUntil.remove(uuid);
        winterHeartUntil.remove(uuid);
        dragonFangUntil.remove(uuid);
        serceWampiraUntil.remove(uuid);
        egzekutorDamageUntil.remove(uuid);
        egzekutorUntil.remove(uuid);
        BukkitTask egzTask = egzekutorTasks.remove(uuid);
        if (egzTask != null) egzTask.cancel();
        rdzenNeutralizacjiSelfUntil.remove(uuid);
        rdzenNeutralizacjiOthersUntil.remove(uuid);
        BukkitTask rdzenTask = rdzenActionBars.remove(uuid);
        if (rdzenTask != null) rdzenTask.cancel();
        cancelEasyBoostScaleTask(uuid);
        if (easyBoostScaleUntil.remove(uuid) != null || easyBoostNeedsReset.contains(uuid)) {
            clearEasyBoostScale(event.getPlayer(), true);
        }
        cooldowns.remove(uuid);
        clearVaderStun(event.getPlayer());
        EventowkiEffects.cancelSkywalkerRewind(event.getPlayer(), true);
        clearSkywalker(event.getPlayer(), true);
    }

    @EventHandler
    public void onSandCastleWorldChange(PlayerChangedWorldEvent event) {
        leaveSandCastleIfLeftWorld(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Definition definition = getDefinition(identifyAndTag(event.getBow()));
        if (definition == null || definition.action() != ActionType.PULL_CROSSBOW) {
            return;
        }
        if (!startUse(player, definition, event.getBow(), true)) {
            event.setCancelled(true);
            return;
        }
        event.getProjectile().getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, definition.id());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            Long executionUntil = egzekutorDamageUntil.get(victim.getUniqueId());
            if (executionUntil != null) {
                if (executionUntil <= System.currentTimeMillis() || isPlayerNeutralized(victim)) {
                    egzekutorDamageUntil.remove(victim.getUniqueId());
                } else {
                    Definition egzekutor = getDefinitionByAction(ActionType.EGZEKUTOR);
                    event.setDamage(event.getDamage() * (egzekutor == null ? 1.15D : decimal(egzekutor, "damage-multiplier", 1.15D)));
                }
            }
            Player castleAttacker = castlePlayerOf(event.getDamager());
            if (castleAttacker != null && isCastleDimensionIsolated(castleAttacker, victim)) {
                event.setCancelled(true);
                return;
            }
            if (isWinterHeartFrozen(victim)) {
                event.setCancelled(true);
                return;
            }
            Long until = turtleShellUntil.get(victim.getUniqueId());
            if (until != null) {
                if (until < System.currentTimeMillis()) {
                    turtleShellUntil.remove(victim.getUniqueId());
                } else if (event.getDamager() instanceof Player attacker && !event.isCancelled()) {
                    event.setCancelled(true);
                    reflectTurtleShell(victim, attacker, event.getFinalDamage());
                    return;
                }
            }
        }

        Entity damager = event.getDamager();
        if (event.getEntity() instanceof Player victim && damager instanceof Projectile projectile
            && projectile.getShooter() instanceof Player shooter) {
            Definition definition = getDefinition(projectile.getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING));
            if (definition != null && !isBlocked(victim.getLocation()) && !isBlocked(shooter.getLocation())) {
                if (definition.action() == ActionType.PULL_CROSSBOW) {
                    if (isPlayerNeutralized(shooter)) {
                        return;
                    }
                    double range = Math.max(1.0D, decimal(definition, "range", 15.0D));
                    if (shooter.getLocation().distanceSquared(victim.getLocation()) > range * range) {
                        return;
                    }
                    // NIE canceluj eventu - strzała musi zadać obrażenia, żeby gracza "trafiła"
                    // event.setCancelled(true);
                    if (victim.isGliding()) {
                        victim.setGliding(false);
                    }
                    EventowkiEffects.pullToShooter(plugin, victim, shooter, range);
                    send(shooter, "settings.messages.pull-success");
                    send(victim, "settings.messages.pull-victim");
                    projectile.remove();
                    // Teraz możemy zredukować obrażenia do minimum
                    event.setDamage(0.01);
                    return;
                }
                if (isPlayerNeutralized(shooter)) {
                    return;
                }
                if (definition.action() == ActionType.WATER_PISTOL) {
                    event.setCancelled(true);
                    applyWaterPistolHit(shooter, victim, definition);
                    projectile.remove();
                    return;
                }
            }
        }

        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (isVaderStunned(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (isPlayerNeutralized(attacker)) return;
        Definition vampire = getDefinitionByAction(ActionType.SERCE_WAMPIRA);
        if (vampire != null) trySerceWampiraHeal(event, attacker, victim, vampire);

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        Definition definition = getDefinition(identifyAndTag(hand));
        if (definition == null) {
            tryDragonFangProc(event, attacker, victim);
            return;
        }
        if (definition.action() == ActionType.STUN_BONE) {
            if (!startUse(attacker, definition, hand, true)) {
                return;
            }
            if (Math.random() * 100.0 <= decimal(definition, "chance", 50.0)) {
                victim.setCooldown(Material.FIREWORK_ROCKET, integer(definition, "firework-cooldown-ticks", 60));
                send(attacker, "settings.messages.stun-success");
                send(victim, "settings.messages.stun-victim");
                victim.playSound(victim.getLocation(), Sound.ENCHANT_THORNS_HIT, 1f, 1f);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_ARMOR_STAND_HIT, 1f, 1f);
            } else {
                send(attacker, "settings.messages.stun-fail");
                attacker.playSound(attacker.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
            }
            consumeIfNeeded(attacker, EquipmentSlot.HAND, hand, definition);
            return;
        }
        if (definition.action() == ActionType.USCISK_VADERA) {
            if (!startUse(attacker, definition, hand, true)) {
                return;
            }
            applyUsciskVadera(attacker, victim, definition);
            consumeIfNeeded(attacker, EquipmentSlot.HAND, hand, definition);
            return;
        }
        if (definition.action() == ActionType.LASKA_MISTRZA_WU) {
            if (!startUse(attacker, definition, hand, true)) {
                event.setCancelled(true);
            }
            return;
        }
        tryDragonFangProc(event, attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCastleEnvironmentDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isInSandCastle(player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL
            || cause == EntityDamageEvent.DamageCause.VOID
            || cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL
            || cause == EntityDamageEvent.DamageCause.FALLING_BLOCK
            || cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
            event.setDamage(0.0D);
            player.setFallDistance(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) {
            return;
        }
        if (EventItemTools.isElytraLocked(player) || isVaderStunned(player) || EventowkiEffects.isHoverLocked(player)) {
            event.setCancelled(true);
            player.setGliding(false);
            return;
        }
        if (!shouldStopCastleFlight(player, player.getLocation())) {
            return;
        }
        event.setCancelled(true);
        denyCastleElytra(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCastleFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!shouldStopCastleFlight(player, player.getLocation())) {
            return;
        }
        event.setCancelled(true);
        denyCastleElytra(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCastleElytraMove(PlayerMoveEvent event) {
        if (sandCastles.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        boolean gliding = player.isGliding() || (player.isFlying() && player.getGameMode() != GameMode.CREATIVE
            && player.getGameMode() != GameMode.SPECTATOR);
        boolean blockChanged = from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ();
        if (!gliding && !blockChanged) {
            return;
        }
        if (!isCastleParticipantInWorld(player, to) && !isCastleParticipantInWorld(player, from)) {
            return;
        }
        if (gliding) {
            denyCastleElytra(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCastleWorldSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) {
            return;
        }
        for (org.bukkit.entity.LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player other && isCastleDimensionIsolated(thrower, other)) {
                event.setIntensity(affected, 0.0D);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCastleWorldInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Player other && isCastleDimensionIsolated(event.getPlayer(), other)) {
            event.setCancelled(true);
            return;
        }
        ItemStack used = event.getPlayer().getInventory().getItem(event.getHand());
        Definition definition = getDefinition(identifyAndTag(used));
        if (definition != null && definition.action() == ActionType.LINA_CECCO) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVaderStunInteract(PlayerInteractEvent event) {
        if (!isVaderStunned(event.getPlayer())) {
            return;
        }
        if (shouldBlockVaderStunInteract(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVaderStunMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        boolean winterFrozen = isWinterHeartFrozen(player);
        if (!isVaderStunned(player) && !EventowkiEffects.isHoverLocked(player) && !winterFrozen) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || hasOnlyLookChange(from, to)) {
            return;
        }
        if (winterFrozen) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
            return;
        }
        EventowkiEffects.clampHoverMove(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWinterHeartDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isWinterHeartFrozen(player)) {
            return;
        }
        event.setCancelled(true);
    }

    private static boolean hasOnlyLookChange(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    private static boolean shouldBlockVaderStunInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            return true;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        ItemStack item = event.getItem();
        return item == null || !item.getType().isEdible();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVaderStunDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isVaderStunned(player)) {
            return;
        }
        Definition definition = getDefinitionByAction(ActionType.USCISK_VADERA);
        double multiplier = definition == null ? 0.70D : decimal(definition, "damage-multiplier", 0.70D);
        event.setDamage(event.getDamage() * multiplier);
    }

    @EventHandler
    public void onVaderStunDrop(PlayerDropItemEvent event) {
        if (isVaderStunned(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEventowkiDeath(PlayerDeathEvent event) {
        cancelActiveEventEffects(event.getEntity());
    }

    @EventHandler
    public void onEasyBoostRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!isEasyBoostScaleActive(player.getUniqueId())) {
                clearEasyBoostScale(player, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCastleFirework(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREWORK_ROCKET) {
            return;
        }
        Player player = event.getPlayer();
        if (!shouldStopCastleFlight(player, player.getLocation())) {
            return;
        }
        event.setCancelled(true);
        denyCastleElytra(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSandCastleGhostClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR
            && action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        Location ghost = findCastleGhostBlock(player, event.getClickedBlock());
        if (ghost == null) {
            return;
        }
        // Ghost/packet blok — od razu podsyłamy packet, żeby klient nie "usuwał" widma.
        // Jedzenie (np. golden apple) zostawiamy odblokowane.
        boolean rightClick = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR;
        ItemStack item = event.getItem();
        if (rightClick && tryPlaceCastleBlock(player, ghost, event.getBlockFace(), castlePlaceableType(item))) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            refreshCastleGhost(player, ghost, false);
            return;
        }

        boolean allowEat = rightClick && isUsingConsumable(player, event.getHand(), item);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (allowEat) {
            event.setUseItemInHand(Event.Result.ALLOW);
        } else {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
        }
        refreshCastleGhost(player, ghost, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSandCastlePlace(BlockPlaceEvent event) {
        if (!allowSandCastleBuild(event.getPlayer(), event.getBlockPlaced().getLocation(), event.getBlockPlaced().getType())) {
            return;
        }
        event.setCancelled(false);
        SandCastleFight fight = sandCastles.get(event.getPlayer().getUniqueId());
        if (fight != null) {
            fight.placedBlocks.add(event.getBlockPlaced().getLocation().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSandCastlePlaceForce(BlockPlaceEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        Material type = event.getBlockPlaced().getType();
        if (!allowSandCastleBuild(player, event.getBlock().getLocation(), type)) {
            return;
        }
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        Block block = event.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (fight == null || fight.ended || !player.isOnline() || !sandCastles.containsKey(player.getUniqueId())) {
                return;
            }
            if (!block.getType().isAir()) {
                return;
            }
            block.setType(type, false);
            fight.placedBlocks.add(block.getLocation().clone());
            ItemStack hand = event.getItemInHand();
            if (player.getGameMode() != GameMode.CREATIVE && hand != null && hand.getType() == type && hand.getAmount() > 0) {
                hand.setAmount(hand.getAmount() - 1);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSandCastleBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (!SAND_CASTLE_PLACEABLE.contains(type)) {
            return;
        }
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended) {
            fight = findSandCastleFightAt(location);
        }
        if (fight == null || fight.ended || !fight.participants.contains(player.getUniqueId())) {
            return;
        }
        if (!isInsideSandCastle(fight, location)) {
            return;
        }
        event.setCancelled(false);
        Location blockLocation = location.clone();
        fight.placedBlocks.removeIf(stored -> stored != null
            && stored.getWorld() != null
            && stored.getWorld().equals(blockLocation.getWorld())
            && stored.getBlockX() == blockLocation.getBlockX()
            && stored.getBlockY() == blockLocation.getBlockY()
            && stored.getBlockZ() == blockLocation.getBlockZ());
    }

    private void registerSandCastlePacketListener() {
        if (sandCastlePacketListenerRegistered) {
            return;
        }
        try {
            if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) {
                return;
            }
            PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
                @Override
                public void onPacketReceive(PacketReceiveEvent event) {
                    handleCastlePacketReceive(event);
                }

                @Override
                public void onPacketSend(PacketSendEvent event) {
                    handleCastlePacketSend(event);
                }
            });
            sandCastlePacketListenerRegistered = true;
        } catch (Throwable ignored) {
        }
    }

    private void handleCastlePacketReceive(PacketReceiveEvent event) {
        try {
            handleCastlePacketReceive0(event);
        } catch (Throwable ignored) {
        }
    }

    private void handleCastlePacketReceive0(PacketReceiveEvent event) {
        Player player = resolvePacketPlayer(event.getPlayer(), event);
        if (player == null) {
            return;
        }
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended || fight.schematicAnimation == null || fight.schematicAnimation.isUndoing()) {
            return;
        }
        Location ghost = null;
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
            Vector3i pos = wrapper.getBlockPosition();
            ghost = castleGhostAt(fight, player.getWorld(), pos.getX(), pos.getY(), pos.getZ());
            Material placeable = castlePlaceableType(player, wrapper.getHand());
            if (ghost != null && placeable != null) {
                event.setCancelled(true);
                BlockFace face = toBukkitBlockFace(wrapper.getFace());
                Location ghostLocation = ghost.clone();
                Bukkit.getScheduler().runTask(plugin, () -> tryPlaceCastleBlock(player, ghostLocation, face, placeable));
            } else if (ghost != null && !isHoldingConsumable(player)) {
                // Nie blokuj jedzenia / pitia — tylko kliknięcie w ghost blok bez consumable.
                event.setCancelled(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            Vector3i pos = wrapper.getBlockPosition();
            ghost = castleGhostAt(fight, player.getWorld(), pos.getX(), pos.getY(), pos.getZ());
            if (ghost != null) {
                event.setCancelled(true);
            }
        }
        if (ghost != null) {
            // Natychmiastowy packet block — zanim klient "zniknie" widmo po kliknięciu.
            refreshCastleGhost(player, ghost, true);
        }
    }

    private void handleCastlePacketSend(PacketSendEvent event) {
        try {
            handleCastlePacketSend0(event);
        } catch (Throwable ignored) {
        }
    }

    private void handleCastlePacketSend0(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.BLOCK_CHANGE
            && event.getPacketType() != PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            return;
        }
        Player player = resolvePacketPlayer(event.getPlayer(), event);
        if (player == null || player.getWorld() == null) {
            return;
        }
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended || fight.schematicAnimation == null || fight.schematicAnimation.isUndoing()) {
            return;
        }
        World world = player.getWorld();
        SchematicPaster.Animation animation = fight.schematicAnimation;
        if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange wrapper = new WrapperPlayServerBlockChange(event);
            Vector3i pos = wrapper.getBlockPosition();
            if (rewriteCastleBlockChange(world, animation, pos.getX(), pos.getY(), pos.getZ(), wrapper)) {
                event.markForReEncode(true);
            }
            return;
        }
        try {
            WrapperPlayServerMultiBlockChange wrapper = new WrapperPlayServerMultiBlockChange(event);
            ClientVersion version = event.getUser() != null
                ? event.getUser().getClientVersion()
                : PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
            boolean changed = false;
            for (WrapperPlayServerMultiBlockChange.EncodedBlock encoded : wrapper.getBlocks()) {
                BlockData fake = animation.getShown(world, encoded.getX(), encoded.getY(), encoded.getZ());
                if (fake == null || !isWorldAir(world, encoded.getX(), encoded.getY(), encoded.getZ())) {
                    continue;
                }
                WrappedBlockState desired = wrapCastleBlock(fake);
                if (desired != null && encoded.getBlockState(version).getGlobalId() != desired.getGlobalId()) {
                    encoded.setBlockState(desired);
                    changed = true;
                }
            }
            if (changed) {
                event.markForReEncode(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private Collection<SandCastleFight> activeSandCastles() {
        LinkedHashSet<SandCastleFight> fights = new LinkedHashSet<>();
        for (SandCastleFight fight : sandCastles.values()) {
            if (fight != null && !fight.ended) {
                fights.add(fight);
            }
        }
        return fights;
    }

    private boolean rewriteCastleBlockChange(
        World world,
        SchematicPaster.Animation animation,
        int x,
        int y,
        int z,
        WrapperPlayServerBlockChange wrapper
    ) {
        BlockData fake = animation.getShown(world, x, y, z);
        if (fake == null || !isWorldAir(world, x, y, z)) {
            return false;
        }
        WrappedBlockState desired = wrapCastleBlock(fake);
        if (desired == null || wrapper.getBlockState().getGlobalId() == desired.getGlobalId()) {
            return false;
        }
        wrapper.setBlockState(desired);
        return true;
    }

    private Location findCastleGhostBlock(Player player, Block clicked) {
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended || fight.schematicAnimation == null) {
            return null;
        }
        if (clicked != null && !clicked.getType().isAir()) {
            return null;
        }
        if (clicked != null && fight.schematicAnimation.isShown(clicked.getLocation())) {
            return clicked.getLocation();
        }
        Location traced = fight.schematicAnimation.rayTrace(player, 6.0);
        if (traced != null && traced.getWorld() != null && !isWorldAir(traced.getWorld(), traced.getBlockX(), traced.getBlockY(), traced.getBlockZ())) {
            return null;
        }
        return traced;
    }

    private Location castleGhostAt(SandCastleFight fight, World world, int x, int y, int z) {
        if (fight == null || fight.schematicAnimation == null || world == null) {
            return null;
        }
        if (fight.schematicAnimation.getShown(world, x, y, z) == null) {
            return null;
        }
        if (!isWorldAir(world, x, y, z)) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    private void refreshCastleGhost(Player player, Location location, boolean fromNetty) {
        sendCastleBlockPackets(player, location);
        if (fromNetty) {
            Bukkit.getScheduler().runTask(plugin, () -> resendCastleAround(player, location));
            Bukkit.getScheduler().runTaskLater(plugin, () -> resendCastleAround(player, location), 2L);
            return;
        }
        resendCastleAround(player, location);
        Bukkit.getScheduler().runTask(plugin, () -> resendCastleAround(player, location));
        Bukkit.getScheduler().runTaskLater(plugin, () -> resendCastleAround(player, location), 2L);
    }

    private void resendCastleAround(Player player, Location location) {
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended || fight.schematicAnimation == null || location == null) {
            return;
        }
        fight.schematicAnimation.resendAround(player, location);
    }

    private void sendCastleBlockPackets(Player player, Location location) {
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (player == null || location == null || fight == null || fight.ended || fight.schematicAnimation == null) {
            return;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        sendCastleBlockPacket(player, fight.schematicAnimation, x, y, z);
        sendCastleBlockPacket(player, fight.schematicAnimation, x + 1, y, z);
        sendCastleBlockPacket(player, fight.schematicAnimation, x - 1, y, z);
        sendCastleBlockPacket(player, fight.schematicAnimation, x, y + 1, z);
        sendCastleBlockPacket(player, fight.schematicAnimation, x, y - 1, z);
        sendCastleBlockPacket(player, fight.schematicAnimation, x, y, z + 1);
        sendCastleBlockPacket(player, fight.schematicAnimation, x, y, z - 1);
    }

    private void sendCastleBlockPacket(Player player, SchematicPaster.Animation animation, int x, int y, int z) {
        if (player.getWorld() == null || !isWorldAir(player.getWorld(), x, y, z)) {
            return;
        }
        BlockData data = animation.getShown(player.getWorld(), x, y, z);
        if (data == null) {
            return;
        }
        WrappedBlockState state = wrapCastleBlock(data);
        if (state == null) {
            return;
        }
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                player,
                new WrapperPlayServerBlockChange(new Vector3i(x, y, z), state.getGlobalId())
            );
        } catch (Throwable ignored) {
        }
    }

    private WrappedBlockState wrapCastleBlock(BlockData data) {
        if (data == null) {
            return null;
        }
        try {
            return SpigotConversionUtil.fromBukkitBlockData(data);
        } catch (Throwable ignored) {
            try {
                return WrappedBlockState.getByString(data.getAsString());
            } catch (Throwable ignoredToo) {
                return null;
            }
        }
    }

    private boolean isWorldAir(World world, int x, int y, int z) {
        try {
            return world.getBlockAt(x, y, z).getType().isAir();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private Player resolvePacketPlayer(Object raw, Object event) {
        if (raw instanceof Player player) {
            return player;
        }
        try {
            if (event instanceof PacketReceiveEvent receive && receive.getUser() != null && receive.getUser().getUUID() != null) {
                return Bukkit.getPlayer(receive.getUser().getUUID());
            }
            if (event instanceof PacketSendEvent send && send.getUser() != null && send.getUser().getUUID() != null) {
                return Bukkit.getPlayer(send.getUser().getUUID());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void tryStartSandCastle(Player caster, EquipmentSlot hand, ItemStack item, Definition definition) {
        if (isInSandCastle(caster) || isInsideAnySandCastle(caster.getLocation())) {
            send(caster, "settings.messages.sand-castle-blocked");
            return;
        }
        if (isBlocked(caster.getLocation())) {
            send(caster, "settings.messages.sand-castle-blocked");
            return;
        }
        if (!tryLockSandCastle(caster.getUniqueId())) {
            return;
        }

        double radius = Math.max(1.0, decimal(definition, "radius", 5.0));
        List<Player> captured = new ArrayList<>();
        try {
            for (Player nearby : caster.getWorld().getPlayers()) {
                if (nearby.getUniqueId().equals(caster.getUniqueId())) {
                    continue;
                }
                if (nearby.getLocation().distanceSquared(caster.getLocation()) > radius * radius) {
                    continue;
                }
                if (isBlocked(nearby.getLocation()) || isInSandCastle(nearby) || isInsideAnySandCastle(nearby.getLocation())) {
                    continue;
                }
                if (!tryLockSandCastle(nearby.getUniqueId())) {
                    continue;
                }
                captured.add(nearby);
            }
            if (captured.isEmpty()) {
                unlockSandCastle(caster.getUniqueId());
                send(caster, "settings.messages.sand-castle-empty");
                return;
            }
            if (!startUse(caster, definition, item, true)) {
                unlockSandCastle(caster.getUniqueId());
                for (Player nearby : captured) {
                    unlockSandCastle(nearby.getUniqueId());
                }
                return;
            }
            startSandCastle(caster, captured, definition, radius);
            consumeIfNeeded(caster, hand, item, definition);
        } catch (RuntimeException exception) {
            unlockSandCastle(caster.getUniqueId());
            for (Player nearby : captured) {
                unlockSandCastle(nearby.getUniqueId());
            }
            throw exception;
        }
    }

    private boolean isInSandCastle(Player player) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (sandCastleLocked.contains(id)) {
            return true;
        }
        SandCastleFight fight = sandCastles.get(id);
        return fight != null && !fight.ended;
    }

    private boolean isInsideAnySandCastle(Location location) {
        if (location == null) {
            return false;
        }
        for (SandCastleFight fight : activeSandCastles()) {
            if (isInsideSandCastle(fight, location)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldStopCastleFlight(Player player, Location location) {
        return isCastleParticipantInWorld(player, location);
    }

    private boolean isCastleParticipantInWorld(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null || !isInSandCastle(player)) {
            return false;
        }
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended || fight.center == null || fight.center.getWorld() == null) {
            return false;
        }
        return location.getWorld().equals(fight.center.getWorld());
    }

    private void denyCastleElytra(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setGliding(false);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
        }
        player.setCooldown(Material.ELYTRA, Math.max(40, player.getCooldown(Material.ELYTRA)));
        player.setCooldown(Material.FIREWORK_ROCKET, Math.max(40, player.getCooldown(Material.FIREWORK_ROCKET)));
    }

    private void clearCastleElytraCooldown(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getCooldown(Material.ELYTRA) <= 40) {
            player.setCooldown(Material.ELYTRA, 0);
        }
        if (player.getCooldown(Material.FIREWORK_ROCKET) <= 40) {
            player.setCooldown(Material.FIREWORK_ROCKET, 0);
        }
    }

    private boolean tryLockSandCastle(UUID uuid) {
        return uuid != null && sandCastleLocked.add(uuid);
    }

    private void unlockSandCastle(UUID uuid) {
        if (uuid != null) {
            sandCastleLocked.remove(uuid);
        }
    }

    private boolean isConsumable(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        Material type = item.getType();
        return type.isEdible()
            || type == Material.POTION
            || type == Material.MILK_BUCKET
            || type == Material.HONEY_BOTTLE;
    }

    private boolean isHoldingConsumable(Player player) {
        if (player == null) {
            return false;
        }
        return isConsumable(player.getInventory().getItemInMainHand())
            || isConsumable(player.getInventory().getItemInOffHand());
    }

    private boolean isUsingConsumable(Player player, EquipmentSlot hand, ItemStack item) {
        if (isConsumable(item)) {
            return true;
        }
        if (player == null) {
            return false;
        }
        if (hand == EquipmentSlot.HAND) {
            return isConsumable(player.getInventory().getItemInOffHand());
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            return isConsumable(player.getInventory().getItemInMainHand());
        }
        return isHoldingConsumable(player);
    }

    private void teleportIntoCastle(Player player, Location dest, WorldBorder border) {
        if (player == null || dest == null || dest.getWorld() == null) {
            return;
        }
        Location target = dest.clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(target);
        player.setFallDistance(0.0F);
        if (border != null) {
            player.setWorldBorder(border);
        }
    }

    private void startSandCastle(Player caster, List<Player> others, Definition definition, double radius) {
        Location center = SchematicPaster.alignFeet(caster.getLocation());
        int duration = Math.max(3, seconds(definition, "duration-seconds", 10));
        double barrierRadius = radius + SAND_CASTLE_BARRIER_EXTRA_BLOCKS;
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(center);
        border.setSize(barrierRadius * 2.0);
        border.setDamageBuffer(0);
        border.setDamageAmount(1);

        Set<UUID> participants = new HashSet<>();
        participants.add(caster.getUniqueId());
        teleportIntoCastle(caster, center, border);
        for (Player other : others) {
            if (other == null || !other.isOnline()) {
                if (other != null) {
                    unlockSandCastle(other.getUniqueId());
                }
                continue;
            }
            SandCastleFight existing = sandCastles.get(other.getUniqueId());
            if (existing != null && !existing.ended) {
                unlockSandCastle(other.getUniqueId());
                continue;
            }
            teleportIntoCastle(other, center, border);
            participants.add(other.getUniqueId());
            tryLockSandCastle(other.getUniqueId());
        }
        if (participants.size() <= 1) {
            unlockSandCastle(caster.getUniqueId());
            for (Player other : others) {
                if (other != null) {
                    unlockSandCastle(other.getUniqueId());
                }
            }
            caster.setWorldBorder(null);
            send(caster, "settings.messages.sand-castle-empty");
            return;
        }

        SandCastleFight fight = new SandCastleFight(participants, center, barrierRadius);
        fight.endsAt = System.currentTimeMillis() + duration * 1000L;
        List<Player> viewers = new ArrayList<>();
        for (UUID id : participants) {
            Player participant = Bukkit.getPlayer(id);
            if (participant != null && participant.isOnline()) {
                viewers.add(participant);
            }
        }
        fight.schematicAnimation = SchematicPaster.startAnimated(
            plugin,
            center,
            text(definition, "schematic", "HypeStarZamek"),
            viewers
        );
        Location stand = fight.schematicAnimation != null
            ? fight.schematicAnimation.standLocation()
            : center;
        if (stand.getWorld() != null) {
            fight.center.setWorld(stand.getWorld());
            fight.center.setX(stand.getX());
            fight.center.setY(stand.getY());
            fight.center.setZ(stand.getZ());
            border.setCenter(stand);
        }
        for (UUID id : participants) {
            sandCastles.put(id, fight);
            tryLockSandCastle(id);
            Player participant = Bukkit.getPlayer(id);
            if (participant != null) {
                teleportIntoCastle(participant, stand, border);
                denyCastleElytra(participant);
            }
        }
        isolateCastlePlayers(fight);
        snapshotCastlePlaceables(fight);
        fight.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long left = fight.endsAt - System.currentTimeMillis();
            if (fight.ended || left <= 0L || !hasOnlineParticipant(fight)) {
                endSandCastle(fight);
                return;
            }
            String time = formatMillis(left);
            if (fight.schematicAnimation != null) {
                fight.schematicAnimation.resend();
            }
            isolateCastlePlayers(fight);
            for (UUID id : new HashSet<>(fight.participants)) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                if (leaveSandCastleIfLeftWorld(player)) {
                    if (fight.ended) {
                        return;
                    }
                    continue;
                }
                player.setGliding(false);
                player.setFallDistance(0.0F);
                denyCastleElytra(player);
                if (player.getLocation().getY() < fight.center.getY() - 0.75D) {
                    teleportIntoCastle(player, fight.center, player.getWorldBorder());
                }
                sendSandCastleActionBar(player, time);
            }
        }, 0L, 20L);
    }

    private void sendSandCastleActionBar(Player player, String time) {
        String message = config.getString(
            "settings.messages.sand-castle-start",
            "&#FD7A7A⚠ &8Ξ &#FD7A7AZamek Piaskowy &8〢 &#FD7A7APozostało: &f%time%s"
        ).replace("%time%", time == null ? "0s" : time);
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.send(player, "sand-castle", message, 60L);
            return;
        }
        player.sendActionBar(ColorUtils.colorizeComponent(message));
    }

    private void shootWaterPistol(Player shooter, Definition definition) {
        Player hit = rayTracePlayer(shooter, 32.0, 0.9);
        if (hit != null && !isBlocked(hit.getLocation()) && !isBlocked(shooter.getLocation())) {
            applyWaterPistolHit(shooter, hit, definition);
        }

        Location eye = shooter.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location spawn = eye.clone().add(direction.clone().multiply(0.8));
        Snowball snowball = shooter.getWorld().spawn(spawn, Snowball.class);
        snowball.setShooter(shooter);
        snowball.setVelocity(direction.multiply(Math.max(0.8, decimal(definition, "projectile-speed", 2.2))));
        snowball.setGravity(true);
        snowball.setItem(new ItemStack(Material.ICE));
        snowball.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, definition.id());
        shooter.getWorld().spawnParticle(Particle.WATER_SPLASH, spawn, 10, 0.2, 0.2, 0.2, 0.05);
        playUseFeedback(shooter, Sound.ENTITY_PLAYER_SPLASH);
    }

    private void handleWaterPistolProjectile(ProjectileHitEvent event, Definition definition) {
        event.getEntity().remove();
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        Player victim = null;
        if (event.getHitEntity() instanceof Player hitPlayer && !hitPlayer.getUniqueId().equals(shooter.getUniqueId())) {
            victim = hitPlayer;
        } else if (event.getHitEntity() == null) {
            victim = nearestPlayer(event.getEntity().getLocation(), shooter, 2.25);
        }
        if (victim == null || isBlocked(victim.getLocation()) || isBlocked(shooter.getLocation())) {
            return;
        }
        applyWaterPistolHit(shooter, victim, definition);
    }

    private Player rayTracePlayer(Player shooter, double distance, double raySize) {
        RayTraceResult result = shooter.getWorld().rayTraceEntities(
            shooter.getEyeLocation(),
            shooter.getEyeLocation().getDirection(),
            distance,
            raySize,
            entity -> entity instanceof Player player
                && !player.getUniqueId().equals(shooter.getUniqueId())
                && player.isValid()
                && player.getGameMode() != GameMode.SPECTATOR
        );
        return result != null && result.getHitEntity() instanceof Player player ? player : null;
    }

    private Player nearestPlayer(Location location, Player shooter, double radius) {
        if (location.getWorld() == null) {
            return null;
        }
        Player closest = null;
        double closestDistance = radius * radius;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getUniqueId().equals(shooter.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance <= closestDistance) {
                closestDistance = distance;
                closest = player;
            }
        }
        return closest;
    }

    private void applyWaterPistolHit(Player shooter, Player victim, Definition definition) {
        if (isPlayerNeutralized(victim)) return;
        int duration = Math.max(1, seconds(definition, "effect-duration-seconds", 7));
        int amplifier = Math.max(0, integer(definition, "amplifier", 0));
        PotionEffect levitation = new PotionEffect(PotionEffectType.LEVITATION, duration * 20, amplifier, false, true, true);
        applyEventPotionEffect(shooter, levitation);
        applyEventPotionEffect(victim, levitation);
        if (shooter.isGliding()) {
            shooter.setGliding(false);
        }
        if (victim.isGliding()) {
            victim.setGliding(false);
        }
        send(shooter, "settings.messages.water-pistol-hit", Map.of("%player%", victim.getName()));
        send(victim, "settings.messages.water-pistol-victim", Map.of("%player%", shooter.getName()));
    }

    private boolean hasOnlineParticipant(SandCastleFight fight) {
        for (UUID id : fight.participants) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                return true;
            }
        }
        return false;
    }

    private boolean allowSandCastleBuild(Player player, Location location, Material material) {
        if (player == null || location == null || !SAND_CASTLE_PLACEABLE.contains(material)) {
            return false;
        }
        return isInsideSandCastle(sandCastles.get(player.getUniqueId()), location);
    }

    private boolean tryPlaceCastleBlock(Player player, Location clicked, BlockFace face, Material type) {
        if (player == null || clicked == null || type == null || !SAND_CASTLE_PLACEABLE.contains(type)) {
            return false;
        }
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended) {
            return false;
        }
        Location target = clicked.clone();
        if (face != null && face.isCartesian()) {
            target.add(face.getModX(), face.getModY(), face.getModZ());
        }
        if (!isInsideSandCastle(fight, target)) {
            return false;
        }
        Block block = target.getBlock();
        if (!block.getType().isAir()) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE && !consumeCastlePlaceable(player, type)) {
            return false;
        }
        block.setType(type, false);
        fight.placedBlocks.add(block.getLocation().clone());
        return true;
    }

    private boolean consumeCastlePlaceable(Player player, Material type) {
        PlayerInventory inventory = player.getInventory();
        if (takeOneCastleItem(inventory.getItemInMainHand(), type)) {
            return true;
        }
        return takeOneCastleItem(inventory.getItemInOffHand(), type);
    }

    private boolean takeOneCastleItem(ItemStack item, Material type) {
        if (item == null || item.getType() != type || item.getAmount() <= 0) {
            return false;
        }
        item.setAmount(item.getAmount() - 1);
        return true;
    }

    private Material castlePlaceableType(ItemStack item) {
        if (item == null || !SAND_CASTLE_PLACEABLE.contains(item.getType())) {
            return null;
        }
        return item.getType();
    }

    private Material castlePlaceableType(Player player, InteractionHand hand) {
        if (player == null) {
            return null;
        }
        ItemStack item = hand == InteractionHand.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        return castlePlaceableType(item);
    }

    private static BlockFace toBukkitBlockFace(Object face) {
        if (face == null) {
            return BlockFace.UP;
        }
        try {
            return BlockFace.valueOf(face.toString());
        } catch (IllegalArgumentException ignored) {
            return BlockFace.UP;
        }
    }

    private void snapshotCastlePlaceables(SandCastleFight fight) {
        fight.preexistingPlaceables.clear();
        visitCastlePlaceables(fight, block -> {
            if (block.getType() == Material.COBWEB) {
                return;
            }
            fight.preexistingPlaceables.add(blockKey(block.getLocation()));
        });
    }

    private void clearNewCastlePlaceables(SandCastleFight fight) {
        visitCastlePlaceables(fight, block -> {
            if (block.getType() == Material.COBWEB) {
                block.setType(Material.AIR, false);
                return;
            }
            if (fight.preexistingPlaceables.contains(blockKey(block.getLocation()))) {
                return;
            }
            block.setType(Material.AIR, false);
        });
    }

    private SandCastleFight findSandCastleFightAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (SandCastleFight fight : activeSandCastles()) {
            if (isInsideSandCastle(fight, location)) {
                return fight;
            }
        }
        return null;
    }

    private void clearCastleWebTraps(SandCastleFight fight) {
        Iterator<List<BlockState>> iterator = activeTraps.iterator();
        while (iterator.hasNext()) {
            List<BlockState> trap = iterator.next();
            boolean insideCastle = false;
            for (BlockState state : trap) {
                if (state == null || state.getWorld() == null) {
                    continue;
                }
                if (isInsideSandCastle(fight, state.getLocation())) {
                    insideCastle = true;
                    break;
                }
            }
            if (!insideCastle) {
                continue;
            }
            iterator.remove();
            for (BlockState state : trap) {
                if (state == null || state.getWorld() == null) {
                    continue;
                }
                Block block = state.getBlock();
                if (block.getType() == Material.COBWEB) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void visitCastlePlaceables(SandCastleFight fight, java.util.function.Consumer<Block> consumer) {
        if (fight == null || fight.center == null || fight.center.getWorld() == null) {
            return;
        }
        World world = fight.center.getWorld();
        int radius = (int) Math.ceil(fight.radius) + 1;
        int cx = fight.center.getBlockX();
        int cy = fight.center.getBlockY();
        int cz = fight.center.getBlockZ();
        int minY = Math.max(world.getMinHeight(), cy - 12);
        int maxY = Math.min(world.getMaxHeight() - 1, cy + 20);
        double radiusSq = fight.radius * fight.radius;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                double dx = (x + 0.5) - fight.center.getX();
                double dz = (z + 0.5) - fight.center.getZ();
                if ((dx * dx) + (dz * dz) > radiusSq) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (SAND_CASTLE_PLACEABLE.contains(block.getType())) {
                        consumer.accept(block);
                    }
                }
            }
        }
    }

    private static String blockKey(Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private boolean isInsideSandCastle(SandCastleFight fight, Location location) {
        if (fight == null || fight.ended || fight.center == null || location == null || location.getWorld() == null) {
            return false;
        }
        if (fight.center.getWorld() == null || !location.getWorld().equals(fight.center.getWorld())) {
            return false;
        }
        double dx = (location.getBlockX() + 0.5) - fight.center.getX();
        double dz = (location.getBlockZ() + 0.5) - fight.center.getZ();
        double radius = fight.radius + 0.75;
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    private void endSandCastle(SandCastleFight fight) {
        if (fight.ended) {
            return;
        }
        fight.ended = true;
        if (fight.task != null) {
            fight.task.cancel();
            fight.task = null;
        }
        try {
            if (fight.schematicAnimation != null) {
                fight.schematicAnimation.undo();
                fight.schematicAnimation = null;
            }
            clearCastleWebTraps(fight);
            for (Location location : fight.placedBlocks) {
                if (location.getWorld() == null) {
                    continue;
                }
                Block block = location.getBlock();
                if (SAND_CASTLE_PLACEABLE.contains(block.getType())) {
                    block.setType(Material.AIR, false);
                }
            }
            clearNewCastlePlaceables(fight);
        } finally {
            fight.placedBlocks.clear();
            for (UUID id : new HashSet<>(fight.participants)) {
                sandCastles.remove(id, fight);
                unlockSandCastle(id);
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    player.setWorldBorder(null);
                    clearCastleElytraCooldown(player);
                    restoreCastleVisibility(player);
                    MultiActionBarService service = MultiActionBarService.get();
                    if (service != null) {
                        service.remove(player.getUniqueId(), "sand-castle");
                    }
                }
            }
        }
    }

    private boolean leaveSandCastleIfLeftWorld(Player player) {
        if (player == null) {
            return false;
        }
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended || fight.center == null || fight.center.getWorld() == null) {
            return false;
        }
        if (player.getWorld().equals(fight.center.getWorld())) {
            return false;
        }
        leaveSandCastle(player, fight);
        return true;
    }

    private void leaveSandCastle(Player player, SandCastleFight fight) {
        if (player == null || fight == null) {
            return;
        }
        UUID id = player.getUniqueId();
        sandCastles.remove(id, fight);
        unlockSandCastle(id);
        fight.participants.remove(id);
        player.setWorldBorder(null);
        clearCastleElytraCooldown(player);
        restoreCastleVisibility(player);
        MultiActionBarService service = MultiActionBarService.get();
        if (service != null) {
            service.remove(id, "sand-castle");
        }
        if (!hasOnlineParticipantInCastleWorld(fight)) {
            endSandCastle(fight);
        }
    }

    private boolean hasOnlineParticipantInCastleWorld(SandCastleFight fight) {
        if (fight == null || fight.center == null || fight.center.getWorld() == null) {
            return false;
        }
        World world = fight.center.getWorld();
        for (UUID id : fight.participants) {
            Player online = Bukkit.getPlayer(id);
            if (online != null && online.isOnline() && online.getWorld().equals(world)) {
                return true;
            }
        }
        return false;
    }

    private void isolateCastlePlayers(SandCastleFight fight) {
        if (fight == null || fight.ended) {
            return;
        }
        List<Player> members = new ArrayList<>();
        for (UUID id : fight.participants) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.isOnline()) {
                members.add(member);
            }
        }
        if (members.isEmpty()) {
            return;
        }
        for (Player outsider : Bukkit.getOnlinePlayers()) {
            if (fight.participants.contains(outsider.getUniqueId())) {
                continue;
            }
            for (Player member : members) {
                outsider.hidePlayer(plugin, member);
                member.hidePlayer(plugin, outsider);
            }
        }
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                members.get(i).showPlayer(plugin, members.get(j));
                members.get(j).showPlayer(plugin, members.get(i));
            }
        }
    }

    private void hideJoinerFromCastles(Player joined) {
        if (joined == null || sandCastles.isEmpty()) {
            return;
        }
        for (SandCastleFight fight : activeSandCastles()) {
            if (fight.participants.contains(joined.getUniqueId())) {
                continue;
            }
            for (UUID id : fight.participants) {
                Player member = Bukkit.getPlayer(id);
                if (member == null || !member.isOnline()) {
                    continue;
                }
                joined.hidePlayer(plugin, member);
                member.hidePlayer(plugin, joined);
            }
        }
    }

    private void restoreCastleVisibility(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player) || shouldRemainCastleHidden(player, online)) {
                continue;
            }
            player.showPlayer(plugin, online);
            online.showPlayer(plugin, player);
        }
    }

    private boolean shouldRemainCastleHidden(Player first, Player second) {
        if (first == null || second == null) {
            return true;
        }
        if (isVanished(first) || isVanished(second)) {
            return true;
        }
        var teleport = plugin.getGroupTeleportManager();
        if (teleport != null) {
            var firstSession = teleport.getByPlayer(first);
            var secondSession = teleport.getByPlayer(second);
            if (firstSession != null || secondSession != null) {
                return firstSession == null || secondSession == null || firstSession != secondSession;
            }
        }
        SandCastleFight firstFight = sandCastles.get(first.getUniqueId());
        SandCastleFight secondFight = sandCastles.get(second.getUniqueId());
        if (firstFight != null && !firstFight.ended && firstFight != secondFight) {
            return true;
        }
        return secondFight != null && !secondFight.ended && secondFight != firstFight;
    }

    private boolean isVanished(Player player) {
        return plugin.getVanishManager() != null && plugin.getVanishManager().isVanished(player);
    }

    private boolean isCastleDimensionIsolated(Player first, Player second) {
        if (first == null || second == null || first.equals(second)) {
            return false;
        }
        SandCastleFight firstFight = activeFightOf(first);
        SandCastleFight secondFight = activeFightOf(second);
        if (firstFight == null && secondFight == null) {
            return false;
        }
        if (firstFight != null && firstFight == secondFight) {
            return false;
        }
        return true;
    }

    private SandCastleFight activeFightOf(Player player) {
        if (player == null) {
            return null;
        }
        SandCastleFight fight = sandCastles.get(player.getUniqueId());
        if (fight == null || fight.ended) {
            return null;
        }
        return fight;
    }

    private Player castlePlayerOf(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private String formatMillis(long millis) {
        long secondsValue = Math.max(1L, (millis + 999L) / 1000L);
        return secondsValue + "s";
    }

    private void launchCustomProjectile(Player player, EquipmentSlot hand, ItemStack item, Definition definition) {
        ItemStack projectileItem = definition.item().clone();
        projectileItem.setAmount(1);

        Projectile projectile;
        if (item.getType() == Material.SNOWBALL) {
            Snowball snowball = player.launchProjectile(Snowball.class);
            snowball.setItem(projectileItem);
            projectile = snowball;
            player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 1f);
        } else {
            ThrownPotion potion = player.launchProjectile(ThrownPotion.class);
            potion.setItem(projectileItem);
            projectile = potion;
            player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, 1f, 1f);
        }

        projectile.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, definition.id());
        if (consume(definition)) {
            consumeIfNeeded(player, hand, item, definition);
        } else {
            setHandItem(player, hand, item.clone());
            syncInventory(player);
        }
    }

    private void handleResetCooldown(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            ColorUtils.sendUsage(sender, "/" + label + " resetcooldown <nick|all>");
            return;
        }

        if (args[1].equalsIgnoreCase("all")) {
            cooldowns.clear();
            Bukkit.getOnlinePlayers().forEach(this::resetVisualCooldowns);
            send(sender, "settings.messages.reset-all");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ColorUtils.colorizeComponent(color("&#FF0000✘ &8Ξ &cNie znaleziono gracza &4" + args[1] + "&c.")));
            return;
        }

        cooldowns.remove(target.getUniqueId());
        resetVisualCooldowns(target);
        send(sender, "settings.messages.reset-player", Map.of("%player%", target.getName()));
    }

    private void handleSetItem(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "settings.messages.only-player");
            return;
        }
        if (args.length < 2) {
            ColorUtils.sendUsage(player, "/" + label + " setitem <nazwa>");
            return;
        }

        String id = normalizeId(args[1]);
        Definition existing = definitions.get(id);
        if (existing == null) {
            send(player, "settings.messages.unknown-item", Map.of("%item%", args[1]));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(player, "settings.messages.setitem-empty-hand");
            return;
        }

        ItemStack saved = tagItem(hand.clone(), id);
        saved.setAmount(1);
        config.set("items." + existing.configKey() + ".item", saved);
        saveConfig();
        reload();
        player.getInventory().setItemInMainHand(tagItem(hand, id));
        send(player, "settings.messages.setitem-saved", Map.of("%item%", id));
    }

    private boolean startUse(Player player, Definition definition, ItemStack item, boolean showMessages) {
        if (isPlayerNeutralized(player)) {
            if (showMessages) {
                EventItemTools.sendCastleActionBar(player, "rdzen-neutralizacji",
                    "&#FF6B6B⚠ &8Ξ &#FF6B6BRdzeń Neutralizacji &8〢 &#FF6B6BNie możesz używać eventówek podczas neutralizacji!");
            }
            return false;
        }
        if (isBlocked(player.getLocation())) {
            if (showMessages) send(player, "settings.messages.blocked-region");
            return false;
        }

        long remainingMillis = getRemainingCooldownMillis(player, definition.id());
        if (remainingMillis > 0L) {
            if (showMessages) {
                long remainingSeconds = Math.max(1L, (long) Math.ceil(remainingMillis / 1000.0));
                send(player, "settings.messages.cooldown", Map.of("%time%", String.valueOf(remainingSeconds)));
            }
            return false;
        }

        int cooldownSeconds = eventowkaCooldownSeconds(player, definition);
        if (cooldownSeconds > 0) {
            cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(definition.id(), System.currentTimeMillis() + cooldownSeconds * 1000L);
            // Kokos = GOLDEN_APPLE — nie dawaj vanilla cooldownu na material (blokuje zwykle jablka).
            if (item != null && !item.getType().isAir() && definition.action() != ActionType.KOKOS) {
                player.setCooldown(item.getType(), toTicks(cooldownSeconds));
            }
            if (definition.action() == ActionType.KOKOS) {
                player.setCooldown(Material.GOLDEN_APPLE, 0);
                player.setCooldown(Material.ENCHANTED_GOLDEN_APPLE, 0);
            }
        }
        return true;
    }

    private boolean applyEventPotionEffect(Player target, PotionEffect effect) {
        return applyEventPotionEffect(target, effect, false);
    }

    private boolean applyEventPotionEffect(Player target, PotionEffect effect, boolean force) {
        if (target == null || effect == null || isPlayerNeutralized(target)) return false;
        boolean applied = force ? target.addPotionEffect(effect, true) : target.addPotionEffect(effect);
        if (applied) {
            activePotionEffects.computeIfAbsent(target.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(effect.getType(), new ActivePotionEffect(effect.getAmplifier(),
                    System.currentTimeMillis() + effect.getDuration() * 50L));
        }
        return applied;
    }

    private void clearEventPotionEffects(Player player) {
        Map<PotionEffectType, ActivePotionEffect> effects = activePotionEffects.remove(player.getUniqueId());
        if (effects == null) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<PotionEffectType, ActivePotionEffect> entry : effects.entrySet()) {
            PotionEffect current = player.getPotionEffect(entry.getKey());
            ActivePotionEffect tracked = entry.getValue();
            long remainingTicks = Math.max(0L, (tracked.expiresAtMillis() - now) / 50L);
            if (current != null && current.getAmplifier() == tracked.amplifier()
                && current.getDuration() <= remainingTicks + 40L) {
                player.removePotionEffect(entry.getKey());
            }
        }
    }

    private int eventowkaCooldownSeconds(Player player, Definition definition) {
        if (definition.action() == ActionType.DRAGON_FIREWORK
            || isPassiveEventowka(definition.action().name())) {
            return 0;
        }
        if (definition.action() == ActionType.COBWEB_REMOVER && isOnTeparca(player)) {
            return seconds(definition, "teparca-cooldown-seconds", 5);
        }
        return seconds(definition, "cooldown-seconds", config.getInt("settings.default-cooldown-seconds", 30));
    }

    private boolean isOnTeparca(Player player) {
        if (player == null || plugin.getGroupTeleportManager() == null) {
            return false;
        }
        return plugin.getGroupTeleportManager().isInTeleportWorld(player);
    }

    private void applyNearbyEffect(Player source, Definition definition, PotionEffectType type, boolean includeSelf) {
        double radius = decimal(definition, "radius", 7.0);
        PotionEffect effect = new PotionEffect(type, seconds(definition, "effect-duration-seconds", 5) * 20, integer(definition, "amplifier", 0), true, true, true);

        for (Player target : source.getWorld().getPlayers()) {
            if (isPlayerNeutralized(target)) continue;
            if (!includeSelf && target.getUniqueId().equals(source.getUniqueId())) {
                continue;
            }
            if (target.getLocation().distanceSquared(source.getLocation()) <= radius * radius
                && !isBlocked(target.getLocation()) && !isPlayerNeutralized(target)) {
                applyEventPotionEffect(target, effect);
            }
        }
    }

    private void removeCobwebsAround(Player player, Definition definition) {
        int radius = Math.min(8, Math.max(0, integer(definition, "radius", 3)));
        Block center = player.getLocation().getBlock();
        int removed = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (block.getType() == Material.COBWEB) {
                        block.setType(Material.AIR, false);
                        removed++;
                    }
                }
            }
        }

        if (removed > 0) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SPIDER_DEATH, 0.8f, 1.4f);
        } else {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
        }
    }

    private void handleWebGrenade(ProjectileHitEvent event, Definition definition) {
        Location center = resolveHitLocation(event);
        if (center == null || isBlocked(center)) {
            return;
        }

        int radius = Math.max(0, integer(definition, "trap-radius", 1));
        int height = 1;
        List<BlockState> changed = new ArrayList<>();
        Location base = center.getBlock().getLocation();

        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = base.clone().add(x, y, z).getBlock();
                    if (!isReplaceable(block.getType()) || isBlocked(block.getLocation())) {
                        continue;
                    }
                    Location blockLocation = block.getLocation();
                    SandCastleFight castleFight = findSandCastleFightAt(blockLocation);
                    block.setType(Material.COBWEB, false);
                    if (castleFight != null) {
                        castleFight.placedBlocks.add(blockLocation.clone());
                        continue;
                    }
                    changed.add(block.getState());
                }
            }
        }

        if (!changed.isEmpty()) {
            activeTraps.add(changed);
            center.getWorld().playSound(center, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 1f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> restoreTrap(changed), seconds(definition, "effect-duration-seconds", 6) * 20L);
        }
    }

    private void handleTeleportSnowball(ProjectileHitEvent event, Definition definition) {
        if (!(event.getEntity() instanceof Snowball) || !(event.getEntity().getShooter() instanceof Player attacker) || !(event.getHitEntity() instanceof Player victim)) {
            return;
        }
        if (!attacker.getWorld().equals(victim.getWorld()) || isBlocked(attacker.getLocation()) || isBlocked(victim.getLocation())) {
            return;
        }
        if (Math.random() > decimal(definition, "chance", 0.65)) {
            return;
        }

        attacker.teleport(victim.getLocation());
        applyEventPotionEffect(victim, new PotionEffect(PotionEffectType.SLOW, seconds(definition, "effect-duration-seconds", 4) * 20, integer(definition, "amplifier", 1), true, true, true));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
    }

    private void handleKnockbackGrenade(ProjectileHitEvent event, Definition definition) {
        Location center = resolveHitLocation(event);
        if (center == null || isBlocked(center)) {
            return;
        }

        double radius = decimal(definition, "radius", 6.0);
        double power = decimal(definition, "knockback-power", 1.7);
        double yPower = decimal(definition, "knockback-y", 0.45);
        for (Player target : center.getWorld().getPlayers()) {
            if (target.getLocation().distanceSquared(center) > radius * radius || isBlocked(target.getLocation())) {
                continue;
            }
            Vector direction = target.getLocation().toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.01) {
                direction = target.getLocation().getDirection();
            }
            target.setVelocity(direction.normalize().multiply(power).setY(yPower));
        }
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.4f);
    }

    private void pullPlayer(Player player, Location hookLocation, Definition definition) {
        if (hookLocation == null || isBlocked(hookLocation)) {
            send(player, "settings.messages.blocked-region");
            return;
        }

        Vector vector = hookLocation.toVector().subtract(player.getLocation().toVector());
        if (vector.lengthSquared() < 0.01) {
            return;
        }
        double y = hookLocation.getY() > player.getLocation().getY()
            ? decimal(definition, "pull-y", 0.45)
            : Math.max(0.0, decimal(definition, "pull-y-down", 0.08));
        vector.setY(0);
        if (vector.lengthSquared() < 0.01) {
            vector = player.getLocation().getDirection().setY(0);
        }
        if (vector.lengthSquared() < 0.01) {
            return;
        }
        player.setVelocity(vector.normalize().multiply(decimal(definition, "pull-power", 1.8)).setY(y));
    }

    private void startBootsTask() {
        if (bootsTask != null) {
            bootsTask.cancel();
        }
        bootsTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Definition sonicBoots = getDefinitionByAction(ActionType.SONIC_BOOTS);
            Definition lavaBoots = getDefinitionByAction(ActionType.LAVA_BOOTS);
            if (sonicBoots == null && lavaBoots == null) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isBlocked(player.getLocation())) {
                    continue;
                }
                // Tylko PDC — bez isSimilar/NBT clone na timerze (400+ graczy).
                String bootsId = identifyTaggedOnly(player.getInventory().getBoots());
                if (sonicBoots != null && sonicBoots.id().equals(bootsId)) {
                    applySonicBootsSpeed(player, sonicBoots);
                }
                if (lavaBoots != null && lavaBoots.id().equals(bootsId)) {
                    applyEventPotionEffect(player, new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 40, 0, true, false, true), true);
                    applyEventPotionEffect(player, new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, true), true);
                    applyEventPotionEffect(player, new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, true, false, true), true);
                }
            }
        }, 40L, 40L);
    }

    @SuppressWarnings("deprecation")
    private void applySonicBootsSpeed(Player player, Definition boots) {
        int amplifier = integer(boots, "amplifier", 1);
        int durationTicks = Math.max(80, seconds(boots, "effect-duration-seconds", 4) * 20);
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (current != null && current.getAmplifier() > amplifier) {
            return;
        }
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() < 60) {
            applyEventPotionEffect(player, new PotionEffect(PotionEffectType.SPEED, durationTicks, amplifier, true, false, true), true);
        }
    }

    private Definition getDefinitionByAction(ActionType action) {
        for (Definition definition : definitions.values()) {
            if (definition.action() == action) {
                return definition;
            }
        }
        return null;
    }

    private Definition getHeldDefinition(Player player, ActionType action) {
        PlayerInventory inventory = player.getInventory();
        Definition main = getDefinition(identifyAndTag(inventory.getItemInMainHand()));
        if (main != null && main.action() == action) {
            return main;
        }
        Definition off = getDefinition(identifyAndTag(inventory.getItemInOffHand()));
        return off != null && off.action() == action ? off : null;
    }

    private Definition getDefinition(String id) {
        return id == null ? null : definitions.get(id);
    }

    public ItemStack getConfiguredItem(String rawId) {
        Definition definition = getDefinition(normalizeId(rawId));
        return definition == null ? null : definition.item().clone();
    }

    public ItemStack createEasyBoostItem() {
        Definition definition = getDefinitionByAction(ActionType.EASY_BOOST);
        if (definition != null) {
            return definition.item().clone();
        }
        ItemStack fallback = getConfiguredItem("easy-boost");
        return fallback != null ? fallback : getConfiguredItem("star-chestplate");
    }

    public String identifyCustomItem(ItemStack item) {
        return identify(item);
    }

    private long getRemainingCooldownMillis(Player player, String id) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        long nextUseAt = playerCooldowns.getOrDefault(id, 0L);
        if (nextUseAt <= now) {
            playerCooldowns.remove(id);
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(player.getUniqueId());
            }
            return 0L;
        }
        return nextUseAt - now;
    }

    private ItemStack loadItem(String configKey) {
        String base = "items." + configKey;
        ItemStack serialized = config.getItemStack(base + ".item");
        if (serialized != null && !serialized.getType().isAir()) {
            serialized.setAmount(1);
            applyConfiguredDisplay(serialized, base);
            applyCustomModelData(serialized, base);
            return serialized;
        }

        Material material = Material.matchMaterial(config.getString(base + ".material", "STONE"));
        if (material == null || material.isAir()) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        applyConfiguredDisplay(item, base);
        if (config.getBoolean(base + ".glow", false)) {
            ItemMeta glowMeta = item.getItemMeta();
            if (glowMeta != null) {
                glowMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                glowMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(glowMeta);
            }
        }
        applyCustomModelData(item, base);

        if (item.getType() == Material.POTION && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potionMeta) {
            try {
                potionMeta.setBasePotionType(org.bukkit.potion.PotionType.WATER);
            } catch (Throwable ignored) {
                try {
                    potionMeta.setBasePotionData(new org.bukkit.potion.PotionData(org.bukkit.potion.PotionType.WATER));
                } catch (Throwable ignored2) {
                }
            }
            item.setItemMeta(potionMeta);
            applyConfiguredDisplay(item, base);
            applyCustomModelData(item, base);
        }

        if (item.getItemMeta() instanceof FireworkMeta fireworkMeta) {
            fireworkMeta.setPower(Math.max(1, config.getInt(base + ".firework-flight-duration", 1)));
            item.setItemMeta(fireworkMeta);
        }
        int knockbackLevel = config.getInt(base + ".knockback-level", 0);
        if (knockbackLevel > 0) {
            ItemMeta knockMeta = item.getItemMeta();
            if (knockMeta != null) {
                knockMeta.addEnchant(Enchantment.KNOCKBACK, knockbackLevel, true);
                item.setItemMeta(knockMeta);
            }
        }
        return item;
    }

    private void applyConfiguredDisplay(ItemStack item, String base) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        String name = config.getString(base + ".name");
        if (name != null && !name.isBlank()) {
            meta.setDisplayName(color(name));
        }
        List<String> configuredLore = config.getStringList(base + ".lore");
        if (!configuredLore.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : configuredLore) {
                lore.add(color(line));
            }
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    private void applyCustomModelData(ItemStack item, String base) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        int customModelData = 1;
        if (config.contains(base + ".custom-model-data")) {
            customModelData = config.getInt(base + ".custom-model-data");
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        item.setItemMeta(meta);
    }

    private String identifyProjectile(Projectile projectile) {
        if (projectile instanceof Snowball snowball) {
            return identify(snowball.getItem());
        }
        if (projectile instanceof ThrownPotion potion) {
            return identify(potion.getItem());
        }
        return null;
    }

    private String identifyAndTag(ItemStack item) {
        String id = identify(item);
        if (id != null && item != null && !item.getType().isAir()) {
            Definition definition = definitions.get(id);
            if (definition != null && definition.action() == ActionType.KOKOS) {
                if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                    item.setType(Material.GOLDEN_APPLE);
                }
                return id;
            }
            ItemMeta meta = item.getItemMeta();
            String taggedId = meta == null ? null : meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
            if (!id.equals(taggedId)) {
                tagItem(item, id);
            }
        }
        return id;
    }

    private String identify(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        String tagged = identifyTaggedOnly(item);
        if (tagged != null) {
            return tagged;
        }

        List<Definition> materialDefinitions = definitionsByMaterial.get(item.getType());
        if (materialDefinitions == null || materialDefinitions.isEmpty()) {
            return null;
        }

        for (Definition definition : materialDefinitions) {
            if (isSimilarWithoutPluginTag(item, definition.item())) {
                return definition.id();
            }
        }
        return null;
    }

    /** Szybka ścieżka tylko PDC — bez clone/isSimilar (timery / ticki). */
    private String identifyTaggedOnly(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id != null && definitions.containsKey(id)) {
            return id;
        }
        return null;
    }

    private boolean isSimilarWithoutPluginTag(ItemStack first, ItemStack second) {
        if (first == null || second == null || first.getType() != second.getType()) {
            return false;
        }
        ItemStack firstClone = stripPluginTag(first.clone());
        ItemStack secondClone = stripPluginTag(second.clone());
        firstClone.setAmount(1);
        secondClone.setAmount(1);
        return firstClone.isSimilar(secondClone);
    }

    private ItemStack tagItem(ItemStack item, String id) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        if ("torba".equalsIgnoreCase(id)) {
            if (dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI.KEY_TORBA_KANGURA == null) {
                dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI.KEY_TORBA_KANGURA =
                    new NamespacedKey(plugin, "admin_item_torba_kangura");
            }
            meta.getPersistentDataContainer().set(
                dev.grzybalson.anarchiagg.adminitems.AdminItemsGUI.KEY_TORBA_KANGURA,
                PersistentDataType.BYTE,
                (byte) 1
            );
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack stripPluginTag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(itemKey);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void restoreItemAfterVanillaUse(Player player, EquipmentSlot hand, int mainHandSlot, ItemStack usedItem, Definition definition) {
        if (consume(definition)) {
            return;
        }

        ItemStack original = usedItem == null ? definition.item().clone() : usedItem.clone();
        int amount = Math.max(1, original.getAmount());
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerInventory inventory = player.getInventory();
            ItemStack current = hand == EquipmentSlot.OFF_HAND ? inventory.getItemInOffHand() : inventory.getItem(mainHandSlot);
            boolean sameItem = current != null
                && !current.getType().isAir()
                && (definition.id().equals(identify(current)) || isSimilarWithoutPluginTag(current, original));
            boolean consumed = current == null
                || current.getType().isAir()
                || (sameItem && current.getAmount() < amount);
            if (consumed) {
                ItemStack restored = original.clone();
                restored.setAmount(amount);
                if (hand == EquipmentSlot.OFF_HAND) {
                    inventory.setItemInOffHand(restored);
                } else {
                    inventory.setItem(mainHandSlot, restored);
                }
            }
        });
    }

    private void consumeIfNeeded(Player player, EquipmentSlot hand, ItemStack item, Definition definition) {
        if (!consume(definition) || item == null || item.getType().isAir()) {
            return;
        }

        int newAmount = item.getAmount() - 1;
        ItemStack replacement = null;
        if (newAmount > 0) {
            replacement = item.clone();
            replacement.setAmount(newAmount);
        }

        setHandItem(player, hand, replacement);
        syncInventory(player);
    }

    private void setHandItem(Player player, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(item);
        } else {
            player.getInventory().setItemInMainHand(item);
        }
    }

    private void syncInventory(Player player) {
        player.updateInventory();
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private boolean consume(Definition definition) {
        if (definition.action() == ActionType.WEB_GRENADE
            || definition.action() == ActionType.TELEPORT_SNOWBALL
            || definition.action() == ActionType.KNOCKBACK_GRENADE) {
            return false;
        }
        return config.getBoolean("items." + definition.configKey() + ".consume", false);
    }

    private boolean isBlocked(Location location) {
        return isInBlockedRegion(location);
    }

    /** Publiczny check regionów z `blocked-regions` (używane też przez kostiumy). */
    public boolean isInBlockedRegion(Location location) {
        if (location == null || location.getWorld() == null) {
            return true;
        }

        List<String> blockedRegions = config.getStringList("blocked-regions");
        if (blockedRegions.isEmpty()) {
            return false;
        }

        Plugin worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (worldGuardPlugin == null || !worldGuardPlugin.isEnabled()) {
            return false;
        }

        try {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) {
                return false;
            }

            BlockVector3 vector = BukkitAdapter.asBlockVector(location);
            for (String regionName : blockedRegions) {
                ProtectedRegion region = regionManager.getRegion(regionName);
                if (region != null && region.contains(vector)) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie sprawdzic regionu WorldGuard dla OGHub custom itemu.", exception);
        }
        return false;
    }

    private Location resolveHitLocation(ProjectileHitEvent event) {
        if (event.getHitEntity() != null) {
            return event.getHitEntity().getLocation();
        }
        if (event.getHitBlock() != null) {
            return event.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
        }
        return event.getEntity().getLocation();
    }

    private boolean isReplaceable(Material material) {
        return material == Material.AIR
            || material == Material.CAVE_AIR
            || material == Material.VOID_AIR
            || material == Material.SHORT_GRASS
            || material == Material.TALL_GRASS
            || material == Material.FERN
            || material == Material.LARGE_FERN
            || material == Material.SNOW;
    }

    private void restoreTrap(List<BlockState> states) {
        activeTraps.remove(states);
        for (BlockState state : states) {
            if (state.getBlock().getType() == Material.COBWEB) {
                state.update(true, false);
            }
        }
    }

    private void restoreAllTraps() {
        List<List<BlockState>> traps = new ArrayList<>(activeTraps);
        activeTraps.clear();
        traps.forEach(this::restoreTrap);
    }

    private boolean isTurtleShellItem(ItemStack item) {
        Definition definition = getDefinition(identify(item));
        return definition != null && definition.action() == ActionType.TURTLE_SHELL;
    }

    private boolean isPlayerHelmetSlot(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR && event.getSlot() == 39) {
            return true;
        }
        return event.getRawSlot() == 5
            && event.getClickedInventory() instanceof PlayerInventory
            && (event.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING
                || event.getView().getTopInventory() instanceof PlayerInventory);
    }

    private void reflectTurtleShell(Player victim, Player attacker, double amount) {
        if (plugin.getCombatManager() != null) {
            plugin.getCombatManager().tagPlayer(victim, attacker);
        }
        if (amount > 0.0D && attacker.isValid() && !attacker.isDead()) {
            attacker.damage(amount);
        }
        attacker.playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
        send(attacker, "settings.messages.turtle-shell-reflect");
    }

    private void unequipTurtleShell(Player player) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            ItemStack helmet = player.getInventory().getHelmet();
            if (!isTurtleShellItem(helmet)) {
                return;
            }
            player.getInventory().setHelmet(null);
            player.getInventory().addItem(helmet).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.updateInventory();
        });
    }

    private void resetVisualCooldowns(Player player) {
        Set<Material> materials = new HashSet<>();
        definitions.values().forEach(definition -> materials.add(definition.item().getType()));
        materials.forEach(material -> player.setCooldown(material, 0));
    }

    private int getGuiSize() {
        return GUI_SIZE;
    }

    private int integer(Definition definition, String key, int fallback) {
        return config.getInt("items." + definition.configKey() + "." + key, fallback);
    }

    private int seconds(Definition definition, String key, int fallback) {
        return Math.max(0, integer(definition, key, fallback));
    }

    private double decimal(Definition definition, String key, double fallback) {
        return config.getDouble("items." + definition.configKey() + "." + key, fallback);
    }

    private String text(Definition definition, String key, String fallback) {
        String value = config.getString("items." + definition.configKey() + "." + key, fallback);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int toTicks(int seconds) {
        long ticks = seconds * 20L;
        return ticks > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    private ActionType parseAction(String rawAction) {
        if (rawAction == null) {
            return ActionType.NONE;
        }
        try {
            return ActionType.valueOf(rawAction.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ActionType.NONE;
        }
    }

    private String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT)
            .replace(' ', '-')
            .replace('_', '-')
            .replaceAll("[^a-z0-9-]", "");
    }

    private List<String> filter(Collection<String> values, String current) {
        String lowered = current == null ? "" : current.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(Objects::nonNull)
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered))
            .distinct()
            .sorted()
            .toList();
    }

    private void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac configu OGHub CustomItems.", exception);
        }
    }

    private void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    private void send(CommandSender sender, String path, Map<String, String> replacements) {
        String message = config.getString(path, "");
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        sender.sendMessage(color(message));
    }

    private String color(String text) {
        return ColorUtils.colorize(text == null ? "" : text);
    }

    private boolean hasAdminPermission(CommandSender sender) {
        String permission = config.getString("settings.admin-permission", "Holly.customitems.admin");
        return sender.hasPermission(permission) || sender.isOp();
    }

    private void playUseFeedback(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1f, 1f);
    }

    private void applyKokos(Player player, Definition definition) {
        if (player == null || !player.isOnline()) {
            return;
        }
        int resistanceSeconds = Math.max(1, seconds(definition, "resistance-seconds", 15));
        int regenSeconds = Math.max(1, seconds(definition, "regen-seconds", 10));
        int goldenHearts = Math.max(1, integer(definition, "golden-hearts", 3));
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        applyEventPotionEffect(player, new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, resistanceSeconds * 20, 0, true, true, true));
        applyEventPotionEffect(player, new PotionEffect(PotionEffectType.REGENERATION, regenSeconds * 20, 1, true, true, true));
        int absorptionAmplifier = Math.max(0, (goldenHearts / 2) - 1);
        applyEventPotionEffect(player, new PotionEffect(PotionEffectType.ABSORPTION, 20 * 60, absorptionAmplifier, true, true, true));
    }

    private void throwBombaKapitana(Player player, ItemStack item, Definition definition) {
        ItemStack projectileItem = item.clone();
        projectileItem.setAmount(1);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Snowball snowball = player.getWorld().spawn(eye.clone().add(direction.clone().multiply(0.6)), Snowball.class);
        snowball.setShooter(player);
        snowball.setItem(projectileItem);
        snowball.setVelocity(direction.multiply(1.35));
        snowball.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, definition.id());
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.6f);
    }

    private void handleBombaKapitanaHit(ProjectileHitEvent event, Definition definition) {
        Location hit = event.getEntity().getLocation();
        event.getEntity().remove();
        if (hit.getWorld() == null) {
            return;
        }
        hit.getWorld().playSound(hit, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        hit.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, hit, 2, 0.2, 0.2, 0.2, 0);
        hit.getWorld().spawnParticle(Particle.SMOKE_LARGE, hit, 18, 0.6, 0.4, 0.6, 0.02);

        double radius = Math.max(0.5D, decimal(definition, "radius", 5.0D));
        int blindnessTicks = Math.max(20, integer(definition, "blindness-ticks", 100));
        long elytraLockMillis = Math.max(1L, (long) decimal(definition, "elytra-lock-millis", 2000D));
        Player shooter = event.getEntity().getShooter() instanceof Player player ? player : null;
        for (Player nearby : hit.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(hit) > radius * radius) {
                continue;
            }
            if (shooter != null && nearby.getUniqueId().equals(shooter.getUniqueId())) {
                continue;
            }
            if (isBlocked(nearby.getLocation())) {
                continue;
            }
            applyEventPotionEffect(nearby, new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, true, false, true));
            EventItemTools.lockElytra(nearby, elytraLockMillis);
            nearby.playSound(nearby.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.4f);
        }
    }

    private void throwLinaCecco(Player player, ItemStack item, Definition definition) {
        ItemStack projectileItem = item.clone();
        projectileItem.setAmount(1);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Snowball snowball = player.getWorld().spawn(eye.clone().add(direction.clone().multiply(0.5)), Snowball.class);
        snowball.setShooter(player);
        snowball.setItem(projectileItem);
        snowball.setVelocity(direction.multiply(1.55));
        snowball.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, definition.id());
        player.playSound(player.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 1.0f, 1.3f);

        Location origin = player.getLocation().clone();
        double range = Math.max(1.0D, decimal(definition, "range", 15.0D));
        double maxRangeSq = range * range;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!snowball.isValid() || snowball.isDead()) {
                task.cancel();
                return;
            }
            Player hit = EventowkiEffects.findPullTarget(snowball, player, 1.85D);
            if (hit != null) {
                task.cancel();
                pullLinaCecco(player, hit, snowball, definition);
                return;
            }
            if (snowball.getLocation().distanceSquared(origin) > maxRangeSq) {
                snowball.remove();
                task.cancel();
            }
        }, 1L, 1L);
    }

    private void handleLinaCeccoHit(ProjectileHitEvent event, Definition definition) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            event.getEntity().remove();
            return;
        }
        Player victim = event.getHitEntity() instanceof Player hit
            ? hit
            : EventowkiEffects.findPullTarget(event.getEntity(), shooter, 2.35D);
        pullLinaCecco(shooter, victim, event.getEntity(), definition);
    }

    private void pullLinaCecco(Player shooter, Player victim, org.bukkit.entity.Entity projectile, Definition definition) {
        if (projectile != null && !EventowkiEffects.claimEntity(projectile)) {
            return;
        }
        if (projectile != null) {
            projectile.remove();
        }
        if (victim == null || shooter == null || victim.getUniqueId().equals(shooter.getUniqueId()) || victim.getWorld() != shooter.getWorld()) {
            return;
        }
        double range = Math.max(1.0D, decimal(definition, "range", 15.0D));
        if (shooter.getLocation().distanceSquared(victim.getLocation()) > range * range) {
            return;
        }
        if (isBlocked(victim.getLocation()) || isBlocked(shooter.getLocation())) {
            return;
        }
        EventowkiEffects.pullToShooter(plugin, victim, shooter, range);
    }

    private void useHellCards(Player player, Definition definition) {
        int radius = Math.max(1, integer(definition, "radius", 5));
        int durationTicks = Math.max(1, seconds(definition, "effect-duration-seconds", 10)) * 20;
        Location origin = player.getLocation();
        PotionEffectType type;
        int amplifier;
        String effectName;
        int roll = ThreadLocalRandom.current().nextInt(3);
        if (roll == 0) {
            type = PotionEffectType.INCREASE_DAMAGE;
            amplifier = 0;
            effectName = "&cSiłę I";
        } else if (roll == 1) {
            type = PotionEffectType.SLOW;
            amplifier = 2;
            effectName = "&8Spowolnienie III";
        } else {
            type = PotionEffectType.REGENERATION;
            amplifier = 4;
            effectName = "&dRegenerację V";
        }
        int affected = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (target.getLocation().distanceSquared(origin) > radius * (double) radius) {
                continue;
            }
            if (isBlocked(target.getLocation())) {
                continue;
            }
            applyEventPotionEffect(target, new PotionEffect(type, durationTicks, amplifier, false, true, true));
            affected++;
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0F, 0.7F);
        player.sendMessage(ColorUtils.colorize(
            definition.item().getItemMeta() != null && definition.item().getItemMeta().hasDisplayName()
                ? definition.item().getItemMeta().getDisplayName() + " &8:: &aNałożyłeś " + effectName + " &ana &f" + affected + " &aosób!"
                : "&aNałożyłeś " + effectName + " &ana &f" + affected + " &aosób!"
        ));
    }

    private void useMagicCrystal(Player player, Definition definition) {
        int hearts = Math.max(1, integer(definition, "golden-hearts", 10));
        int amplifier = Math.max(0, (hearts / 2) - 1);
        int durationTicks = Math.max(1, seconds(definition, "absorption-duration-seconds", 120)) * 20;
        double absorptionHp = hearts * 2.0D;
        player.removePotionEffect(PotionEffectType.ABSORPTION);
        applyEventPotionEffect(player, new PotionEffect(PotionEffectType.ABSORPTION, durationTicks, amplifier, false, true, true));
        player.setAbsorptionAmount(absorptionHp);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        player.sendMessage(ColorUtils.colorize("&aOtrzymałeś &e" + hearts + " &azłotych serc!"));
    }

    private void useWinterHeart(Player player, Definition definition) {
        int durationSeconds = Math.max(1, seconds(definition, "duration-seconds", 4));
        winterHeartUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationSeconds * 1000L);
        player.setVelocity(player.getVelocity().multiply(0));
        player.setFreezeTicks(durationSeconds * 20);
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 0.5F);
        player.sendMessage(ColorUtils.colorize("&bJesteś zamrożony na &f" + durationSeconds + "s&b!"));
    }

    private void useDragonFang(Player player, Definition definition) {
        int durationSeconds = Math.max(1, seconds(definition, "duration-seconds", 4));
        dragonFangUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationSeconds * 1000L);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6F, 1.4F);
        player.sendMessage(ColorUtils.colorize("&aAktywowano na &f" + durationSeconds + "s&a!"));
    }

    private void useEasyBoost(Player player, Definition definition) {
        double scale = decimal(definition, "scale", 0.4D);
        int scaleSeconds = Math.max(1, seconds(definition, "scale-seconds", 5));
        int strengthSeconds = Math.max(1, seconds(definition, "strength-seconds", 60));
        int strengthAmp = Math.max(0, integer(definition, "strength-amplifier", 0));

        UUID uuid = player.getUniqueId();
        cancelEasyBoostScaleTask(uuid);

        long until = System.currentTimeMillis() + scaleSeconds * 1000L;
        applyPlayerScale(player, scale);
        easyBoostNeedsReset.add(uuid);
        easyBoostScaleUntil.put(uuid, until);
        applyEventPotionEffect(player, new PotionEffect(
            PotionEffectType.INCREASE_DAMAGE, strengthSeconds * 20, strengthAmp, true, true, true), true);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.6F);
        player.sendMessage(ColorUtils.colorize(
            "&bEasy Boost&7: skala &f" + scale + " &7na &e" + scaleSeconds + "s &7+ &cSiła I &7na &f" + strengthSeconds + "s&7!"
        ));

        final long activationUntil = until;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            easyBoostScaleTasks.remove(uuid);
            Long current = easyBoostScaleUntil.get(uuid);
            // Nowszy boost nadpisal ten timer — nie ruszaj.
            if (current != null && current > activationUntil) {
                return;
            }
            easyBoostScaleUntil.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                clearEasyBoostScale(online, true);
            } else {
                easyBoostNeedsReset.add(uuid);
            }
        }, scaleSeconds * 20L + 5L);
        easyBoostScaleTasks.put(uuid, task);
    }

    private boolean isEasyBoostScaleActive(UUID uuid) {
        Long until = easyBoostScaleUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    private void cancelEasyBoostScaleTask(UUID uuid) {
        BukkitTask task = easyBoostScaleTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void clearEasyBoostScale(Player player, boolean dropTracking) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (dropTracking) {
            easyBoostScaleUntil.remove(uuid);
            easyBoostNeedsReset.remove(uuid);
            cancelEasyBoostScaleTask(uuid);
        }
        applyPlayerScale(player, 1.0D);
        // Pehkui interpoluje — dociśnij jeszcze 2x.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !isEasyBoostScaleActive(uuid)) {
                applyPlayerScale(player, 1.0D);
            }
        }, 5L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !isEasyBoostScaleActive(uuid)) {
                applyPlayerScale(player, 1.0D);
                easyBoostNeedsReset.remove(uuid);
            }
        }, 25L);
    }

    private void startEasyBoostWatchdog() {
        if (easyBoostWatchdog != null) {
            easyBoostWatchdog.cancel();
        }
        easyBoostWatchdog = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID uuid : Set.copyOf(easyBoostScaleUntil.keySet())) {
                Long until = easyBoostScaleUntil.get(uuid);
                if (until == null || until > now) {
                    continue;
                }
                easyBoostScaleUntil.remove(uuid);
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    clearEasyBoostScale(online, true);
                } else {
                    easyBoostNeedsReset.add(uuid);
                }
            }
            for (UUID uuid : Set.copyOf(easyBoostNeedsReset)) {
                if (isEasyBoostScaleActive(uuid)) {
                    continue;
                }
                Player online = Bukkit.getPlayer(uuid);
                if (online != null && online.isOnline()) {
                    clearEasyBoostScale(online, true);
                }
            }
        }, 40L, 40L);
    }

    private static Attribute cachedScaleAttribute;
    private static boolean scaleAttributeResolved;

    private static void applyPlayerScale(Player player, double scale) {
        if (player == null || !player.isOnline()) {
            return;
        }
        double target = scale <= 0.0D ? 1.0D : scale;
        Attribute scaleAttr = resolveScaleAttribute();
        if (scaleAttr != null) {
            AttributeInstance instance = player.getAttribute(scaleAttr);
            if (instance != null) {
                for (org.bukkit.attribute.AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                    try {
                        instance.removeModifier(modifier);
                    } catch (Throwable ignored) {
                    }
                }
                double base = Math.abs(target - 1.0D) < 0.001D ? instance.getDefaultValue() : target;
                if (Math.abs(base - 1.0D) < 0.001D) {
                    base = 1.0D;
                }
                instance.setBaseValue(base);
            }
        }
        try {
            Class<?> scaleTypesClass = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            java.lang.reflect.Field baseField = scaleTypesClass.getField("BASE");
            Object baseType = baseField.get(null);
            java.lang.reflect.Method getScaleDataMethod = baseType.getClass().getMethod("getScaleData", org.bukkit.entity.Entity.class);
            Object scaleData = getScaleDataMethod.invoke(baseType, player);
            float f = (float) target;
            try {
                java.lang.reflect.Method setScaleMethod = scaleData.getClass().getMethod("setScale", float.class);
                setScaleMethod.invoke(scaleData, f);
            } catch (NoSuchMethodException ignored) {
            }
            try {
                java.lang.reflect.Method setScaleMethod = scaleData.getClass().getMethod("setScale", float.class, boolean.class);
                setScaleMethod.invoke(scaleData, f, true);
            } catch (NoSuchMethodException ignored) {
            }
            java.lang.reflect.Method setTargetScaleMethod = scaleData.getClass().getMethod("setTargetScale", float.class);
            setTargetScaleMethod.invoke(scaleData, f);
        } catch (Throwable ignored) {
        }
    }

    private static Attribute resolveScaleAttribute() {
        if (scaleAttributeResolved) {
            return cachedScaleAttribute;
        }
        scaleAttributeResolved = true;
        for (String candidate : new String[]{"GENERIC_SCALE", "SCALE"}) {
            try {
                cachedScaleAttribute = Attribute.valueOf(candidate);
                return cachedScaleAttribute;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private void tryDragonFangProc(EntityDamageByEntityEvent event, Player attacker, Player victim) {
        if (isPlayerNeutralized(attacker) || isPlayerNeutralized(victim) || !isDragonFangActive(attacker.getUniqueId())) {
            return;
        }
        if (isBlocked(attacker.getLocation()) || isBlocked(victim.getLocation())) {
            return;
        }
        Definition definition = getDefinitionByAction(ActionType.SMOCZY_KIEL);
        double chance = definition == null ? 10.0D : decimal(definition, "lightning-chance", 10.0D);
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return;
        }
        double damage = definition == null ? 5.0D : decimal(definition, "lightning-damage", 5.0D);
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        event.setDamage(Math.max(0.0D, damage));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5F, 1.3F);
    }

    private boolean isWinterHeartFrozen(Player player) {
        Long until = winterHeartUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            winterHeartUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private boolean isDragonFangActive(UUID uuid) {
        Long until = dragonFangUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            dragonFangUntil.remove(uuid);
            return false;
        }
        return true;
    }

    private void applyUsciskVadera(Player attacker, Player victim, Definition definition) {
        if (isPlayerNeutralized(victim)) return;
        int stunTicks = Math.max(20, integer(definition, "stun-ticks", 80));
        double liftBlocks = decimal(definition, "lift-blocks", 2.0D);
        long until = System.currentTimeMillis() + stunTicks * 50L;
        vaderStunnedUntil.put(victim.getUniqueId(), until);
        EventowkiEffects.startHoverLock(plugin, victim, liftBlocks);
        applyEventPotionEffect(victim, new PotionEffect(PotionEffectType.SLOW, stunTicks, 255, false, false, false));
        startVaderActionBar(victim, until);
        Bukkit.getScheduler().runTaskLater(plugin, () -> clearVaderStun(victim), stunTicks);
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 0.5f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
    }

    private void startVaderActionBar(Player victim, long untilMillis) {
        BukkitTask previous = vaderActionBars.remove(victim.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!victim.isOnline() || !isVaderStunned(victim)) {
                BukkitTask current = vaderActionBars.remove(victim.getUniqueId());
                if (current != null) {
                    current.cancel();
                }
                return;
            }
            long left = Math.max(0L, vaderStunnedUntil.getOrDefault(victim.getUniqueId(), 0L) - System.currentTimeMillis());
            String seconds = String.format(Locale.US, "%.1f", left / 1000.0D);
            EventItemTools.sendCastleActionBar(
                victim,
                "uscisk-vadera",
                "&#B266FF⚠ &8Ξ &#B266FFUścisk Vadera &8〢 &#B266FFPozostało: &f" + seconds + "s"
            );
        }, 0L, 5L);
        vaderActionBars.put(victim.getUniqueId(), task);
    }

    private boolean isVaderStunned(Player player) {
        Long until = vaderStunnedUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private void clearVaderStun(Player player) {
        vaderStunnedUntil.remove(player.getUniqueId());
        EventowkiEffects.stopHoverLock(player);
        BukkitTask task = vaderActionBars.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void startSkywalkerRecall(Player player, Definition definition) {
        int delayTicks = Math.max(20, integer(definition, "delay-ticks", 120));
        int rewindTicks = Math.max(8, integer(definition, "rewind-ticks", 16));
        Location saved = player.getLocation().clone();
        long endsAt = System.currentTimeMillis() + delayTicks * 50L;
        BukkitTask bar = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !skywalkerRecalls.containsKey(player.getUniqueId())) {
                return;
            }
            long left = Math.max(0L, endsAt - System.currentTimeMillis());
            String seconds = String.format(Locale.US, "%.1f", left / 1000.0D);
            EventItemTools.sendCastleActionBar(
                player,
                "skywalker",
                "&#7AD4FF⚠ &8Ξ &#7AD4FFRetrospekcja Skywalkera &8〢 &#7AD4FFPowrót za: &f" + seconds + "s"
            );
        }, 0L, 5L);
        BukkitTask finish = Bukkit.getScheduler().runTaskLater(plugin, () -> startSkywalkerRewind(player, rewindTicks), delayTicks);
        skywalkerRecalls.put(player.getUniqueId(), new SkywalkerRecall(saved, bar, finish));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
    }

    private void startSkywalkerRewind(Player player, int rewindTicks) {
        SkywalkerRecall recall = skywalkerRecalls.get(player.getUniqueId());
        if (recall == null || !player.isOnline()) {
            clearSkywalker(player, false);
            return;
        }
        recall.bar().cancel();
        Location from = player.getLocation().clone();
        Location to = recall.saved();
        if (from.getWorld() == null || to.getWorld() == null || from.getWorld() != to.getWorld()) {
            clearSkywalker(player, false);
            return;
        }

        hideSkywalker(player);
        healSkywalker(player);
        applyEventPotionEffect(player, new PotionEffect(PotionEffectType.INVISIBILITY, rewindTicks + 40, 0, false, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.6f);
        EventowkiEffects.startSkywalkerRewind(plugin, player, from, to, rewindTicks, finished -> {
            if (finished != null && finished.isOnline()) {
                healSkywalker(finished);
                showSkywalker(finished);
                finished.removePotionEffect(PotionEffectType.INVISIBILITY);
                finished.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.4f);
                if (to.getWorld() != null) {
                    to.getWorld().spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.2);
                }
            }
            clearSkywalker(player, false);
        });
    }

    private void hideSkywalker(Player player) {
        player.setInvisible(true);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.hidePlayer(plugin, player);
            }
        }
    }

    private void showSkywalker(Player player) {
        player.setInvisible(false);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(plugin, player);
        }
    }

    private void healSkywalker(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = max != null ? max.getValue() : 20.0D;
        player.setHealth(maxHealth);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
    }

    private void clearSkywalker(Player player, boolean show) {
        SkywalkerRecall recall = skywalkerRecalls.remove(player.getUniqueId());
        if (recall != null) {
            recall.bar().cancel();
            recall.finish().cancel();
        }
        if (show) {
            showSkywalker(player);
        }
    }

    // ========== NOWE EVENTOWKI ==========

    private void activateSerceWampira(Player player, Definition definition) {
        int durationTicks = Math.max(20, integer(definition, "duration-ticks", 100));
        serceWampiraUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationTicks * 50L);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.3f);
        EventItemTools.sendCastleActionBar(player, "serce-wampira",
            "&#FF6B6B⚠ &8Ξ &#FF6B6BSerce Wampira &8〢 &#FF6B6BAktywne przez &f" + String.format(Locale.US, "%.1f", durationTicks / 20.0D) + "s");
    }

    private void trySerceWampiraHeal(EntityDamageByEntityEvent event, Player attacker, Player victim, Definition definition) {
        UUID uuid = attacker.getUniqueId();
        Long until = serceWampiraUntil.get(uuid);
        if (until == null) return;
        if (until <= System.currentTimeMillis()) {
            serceWampiraUntil.remove(uuid);
            return;
        }

        double healPercentage = decimal(definition, "heal-percentage", 0.30);
        double damage = event.getFinalDamage();
        double healAmount = damage * healPercentage;

        if (healAmount > 0 && attacker.isOnline()) {
            double currentHealth = attacker.getHealth();
            double maxHealth = attacker.getMaxHealth();
            double newHealth = Math.min(maxHealth, currentHealth + healAmount);
            attacker.setHealth(newHealth);

            EventItemTools.sendCastleActionBar(
                attacker,
                "serce-wampira",
                String.format("&#FF6B6B⚠ &8Ξ &#FF6B6BSerce Wampira &8〢 &#FF6B6B+%.1f ❤", healAmount)
            );
        }
    }

    private void useEgzekutor(Player player, Definition definition) {
        Player target = getTargetPlayer(player, decimal(definition, "range", 30.0));
        if (target == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.0f);
            EventItemTools.sendCastleActionBar(
                player,
                "egzekutor",
                "&#9D4EDD⚠ &8Ξ &#9D4EDDEgzekutor &8〢 &#9D4EDDBrak celu!"
            );
            return;
        }

        if (isBlocked(target.getLocation()) || isPlayerNeutralized(target)) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.0f);
            return;
        }

        shootFireBeam(player, target);
        applyEgzekutorEffects(target, definition,
            "EXECUTION".equals(egzekutorModes.getOrDefault(player.getUniqueId(), "LOCK")));

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.2f);
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT_ON_FIRE, 1.0f, 0.8f);

        EventItemTools.sendCastleActionBar(
            player,
            "egzekutor",
            "&#9D4EDD⚠ &8Ξ &#9D4EDDEgzekutor &8〢 &#9D4EDDTrafiono: &f" + target.getName()
        );
    }

    private Player getTargetPlayer(Player shooter, double range) {
        Location eyeLoc = shooter.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        RayTraceResult result = shooter.getWorld().rayTraceEntities(
            eyeLoc,
            direction,
            range,
            0.5,
            entity -> entity instanceof Player target && !target.equals(shooter) && !target.isDead()
                && !isPlayerNeutralized(target)
        );

        if (result != null && result.getHitEntity() instanceof Player) {
            return (Player) result.getHitEntity();
        }
        return null;
    }

    private void shootFireBeam(Player shooter, Player target) {
        Location start = shooter.getEyeLocation();
        Location end = target.getEyeLocation();
        Vector direction = end.toVector().subtract(start.toVector()).normalize();
        double distance = start.distance(end);
        double step = 0.5;

        for (double d = 0; d < distance; d += step) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));
            particleLoc.getWorld().spawnParticle(Particle.FLAME, particleLoc, 2, 0.1, 0.1, 0.1, 0.02);
            particleLoc.getWorld().spawnParticle(Particle.SMOKE_NORMAL, particleLoc, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private void applyEgzekutorEffects(Player target, Definition definition, boolean executionMode) {
        int lockTicks = Math.max(1, integer(definition, "elytra-lock-ticks", 100));
        int glowTicks = Math.max(1, integer(definition, "glowing-duration-ticks", 100));
        int durationTicks = Math.max(lockTicks, glowTicks);
        long until = System.currentTimeMillis() + durationTicks * 50L;
        if (executionMode) {
            egzekutorDamageUntil.put(target.getUniqueId(), until);
        } else {
            EventItemTools.lockElytra(target, lockTicks * 50L);
        }
        applyEventPotionEffect(target, new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0, false, false, true));
        egzekutorUntil.put(target.getUniqueId(), until);

        BukkitTask oldTask = egzekutorTasks.remove(target.getUniqueId());
        if (oldTask != null) oldTask.cancel();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            egzekutorUntil.remove(target.getUniqueId());
            egzekutorDamageUntil.remove(target.getUniqueId());
            egzekutorTasks.remove(target.getUniqueId());
            EventItemTools.sendCastleActionBar(target, "egzekutor-hit",
                "&#9D4EDD⚠ &8Ξ &#9D4EDDEgzekutor &8〢 &#9D4EDDEfekt wygasł");
        }, durationTicks);
        egzekutorTasks.put(target.getUniqueId(), task);
        EventItemTools.sendCastleActionBar(target, "egzekutor-hit", executionMode
            ? "&#FF6B6B⚠ &8Ξ &#FF6B6BEgzekutor &8〢 &#FF6B6BOtrzymujesz &f+15% obrażeń"
            : "&#FF6B6B⚠ &8Ξ &#FF6B6BEgzekutor &8〢 &#FF6B6BZablokowano elytrę!");
    }

    private void toggleEgzekutorMode(Player player) {
        String next = "EXECUTION".equals(egzekutorModes.getOrDefault(player.getUniqueId(), "LOCK"))
            ? "LOCK" : "EXECUTION";
        egzekutorModes.put(player.getUniqueId(), next);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        EventItemTools.sendCastleActionBar(player, "egzekutor",
            "&#FFFF00⚠ &8Ξ &#FFFF00Egzekutor &8〢 &fTryb: " + ("LOCK".equals(next)
                ? "&eBlokada elytry" : "&cEgzekucja (+15% obrażeń)"));
    }

    private void useMaskaZludzenia(Player player, Definition definition) {
        if (!isFancyNpcsReady()) {
            player.sendMessage(ColorUtils.colorizeComponent("&c⚠ &8Ξ &cMaska Złudzenia &8〢 &7FancyNpcs 2.9.0 nie jest włączony."));
            return;
        }
        MaskaNpcRun previous = maskaRuns.remove(player.getUniqueId());
        if (previous != null) previous.stop();
        Vector direction = player.getLocation().getDirection().setY(0.0D);
        if (direction.lengthSquared() <= 0.0D) direction = new Vector(0.0D, 0.0D, 1.0D);
        else direction.normalize();
        Location spawn = player.getLocation().clone().add(direction.clone().multiply(0.8D));
        MaskaNpcRun run = new MaskaNpcRun(player, spawn, direction, player.getInventory().getItemInMainHand());
        maskaRuns.put(player.getUniqueId(), run);
        run.start(Math.max(20, seconds(definition, "duration", 10) * 20), Math.max(0.05D, decimal(definition, "npc-speed", 0.2D)));
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 1f);
        EventItemTools.sendCastleActionBar(player, "maska-zludzenia",
            "&#FF6BFF⚠ &8Ξ &#FF6BFFMaska Złudzenia &8〢 &fKlon ruszył do przodu!");
    }

    private boolean isFancyNpcsReady() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled("FancyNpcs") && FancyNpcsPlugin.get() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void toggleRdzenMode(Player player, ItemStack item) {
        String worldName = player.getWorld().getName().toLowerCase(Locale.ROOT);
        if (!worldName.equals("spawn")) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            EventItemTools.sendCastleActionBar(
                player,
                "rdzen-neutralizacji",
                "&#FF6B6B⚠ &8Ξ &#FF6B6BRdzeń Neutralizacji &8〢 &#FF6B6BTryb można zmienić tylko na spawnie!"
            );
            return;
        }

        UUID uuid = player.getUniqueId();
        String currentMode = rdzenNeutralizacjiMode.getOrDefault(uuid, "SELF");
        String newMode = "SELF".equals(currentMode) ? "OTHERS" : "SELF";
        rdzenNeutralizacjiMode.put(uuid, newMode);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);

        String modeText = "SELF".equals(newMode)
            ? "&aTYLKO DLA CIEBIE"
            : "&cDLA GRACZY OBOK";

        dev.grzybalson.utils.ColorUtils.sendTitle(
            player,
            "",
            "&#00FF00Rdzeń Neutralizacji &8〢 &fTryb: " + modeText
        );
    }

    private void useRdzenNeutralizacji(Player player, ItemStack item, Definition definition) {
        UUID uuid = player.getUniqueId();
        String mode = rdzenNeutralizacjiMode.getOrDefault(uuid, "SELF");
        int durationTicks = integer(definition, "duration-ticks", 200);

        if ("SELF".equals(mode)) {
            activateRdzenSelfMode(player, durationTicks);
        } else {
            activateRdzenOthersMode(player, definition, durationTicks);
        }
    }

    private void activateRdzenSelfMode(Player player, int durationTicks) {
        long until = System.currentTimeMillis() + durationTicks * 50L;
        rdzenNeutralizacjiSelfUntil.put(player.getUniqueId(), until);
        cancelActiveEventEffects(player);

        player.getWorld().spawnParticle(
            Particle.VILLAGER_HAPPY,
            player.getLocation().add(0, 1, 0),
            30, 0.5, 0.5, 0.5, 0.1
        );
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);

        startRdzenActionBar(player, until, true);

        dev.grzybalson.utils.ColorUtils.sendTitle(
            player,
            "",
            "&#00FF00Rdzeń Neutralizacji &8〢 &aAktywny (Ty)"
        );
    }

    private void activateRdzenOthersMode(Player player, Definition definition, int durationTicks) {
        double radius = decimal(definition, "radius", 5.0);
        List<Player> nearbyPlayers = new ArrayList<>();

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player nearby && !nearby.equals(player)) {
                nearbyPlayers.add(nearby);
            }
        }

        if (nearbyPlayers.isEmpty()) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            EventItemTools.sendCastleActionBar(
                player,
                "rdzen-neutralizacji",
                "&#FF6B6B⚠ &8Ξ &#FF6B6BRdzeń Neutralizacji &8〢 &#FF6B6BBrak graczy w zasięgu!"
            );
            return;
        }

        player.getWorld().spawnParticle(
            Particle.SPELL_WITCH,
            player.getLocation().add(0, 1, 0),
            50, radius, 1, radius, 0.1
        );
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 0.8f);

        long until = System.currentTimeMillis() + durationTicks * 50L;
        for (Player target : nearbyPlayers) {
            rdzenNeutralizacjiOthersUntil.put(target.getUniqueId(), until);
            cancelActiveEventEffects(target);
            startRdzenActionBar(target, until, false);

            target.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.2f);
            EventItemTools.sendCastleActionBar(
                target,
                "rdzen-neutralizacji-hit",
                "&#FF6B6B⚠ &8Ξ &#FF6B6BRdzeń Neutralizacji &8〢 &#FF6B6BTwoje eventówki zostały zneutralizowane!"
            );
        }

        dev.grzybalson.utils.ColorUtils.sendTitle(
            player,
            "",
            "&#00FF00Rdzeń Neutralizacji &8〢 &aZneutralizowano: &f" + nearbyPlayers.size()
        );
    }

    private void cancelActiveEventEffects(Player player) {
        UUID uuid = player.getUniqueId();
        SandCastleFight sandCastleFight = sandCastles.get(uuid);
        if (sandCastleFight != null && !sandCastleFight.ended) {
            endSandCastle(sandCastleFight);
        }
        boolean skywalkerActive = skywalkerRecalls.containsKey(uuid);
        clearEventPotionEffects(player);
        turtleShellUntil.remove(uuid);
        if (winterHeartUntil.remove(uuid) != null) player.setFreezeTicks(0);
        dragonFangUntil.remove(uuid);
        serceWampiraUntil.remove(uuid);
        egzekutorUntil.remove(uuid);
        egzekutorDamageUntil.remove(uuid);
        BukkitTask egzekutorTask = egzekutorTasks.remove(uuid);
        if (egzekutorTask != null) egzekutorTask.cancel();
        EventItemTools.clearElytraLock(player);
        clearVaderStun(player);
        if (easyBoostScaleUntil.containsKey(uuid) || easyBoostNeedsReset.contains(uuid)) {
            clearEasyBoostScale(player, true);
        }
        EventowkiEffects.cancelSkywalkerRewind(player, true);
        clearSkywalker(player, skywalkerActive);
        MaskaNpcRun maskaRun = maskaRuns.remove(uuid);
        if (maskaRun != null) maskaRun.stop();
    }

    private void startRdzenActionBar(Player player, long untilMillis, boolean isSelfMode) {
        BukkitTask previous = rdzenActionBars.remove(player.getUniqueId());
        if (previous != null) {
            previous.cancel();
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !isRdzenActive(player.getUniqueId(), isSelfMode)) {
                BukkitTask current = rdzenActionBars.remove(player.getUniqueId());
                if (current != null) {
                    current.cancel();
                }
                return;
            }

            long left = Math.max(0L, untilMillis - System.currentTimeMillis());
            String seconds = String.format(Locale.US, "%.1f", left / 1000.0);

            String message = isSelfMode
                ? "&#00FF00⚠ &8Ξ &#00FF00Ochrona Aktywna &8〢 &#00FF00Pozostało: &f" + seconds + "s"
                : "&#FF6B6B⚠ &8Ξ &#FF6B6BZneutralizowano &8〢 &#FF6B6BPozostało: &f" + seconds + "s";

            EventItemTools.sendCastleActionBar(
                player,
                isSelfMode ? "rdzen-neutralizacji" : "rdzen-neutralizacji-hit",
                message
            );
        }, 0L, 20L);

        rdzenActionBars.put(player.getUniqueId(), task);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            rdzenActionBars.remove(player.getUniqueId());
            if (isSelfMode) {
                rdzenNeutralizacjiSelfUntil.remove(player.getUniqueId());
            } else {
                rdzenNeutralizacjiOthersUntil.remove(player.getUniqueId());
            }
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.2f);
                EventItemTools.sendCastleActionBar(
                    player,
                    isSelfMode ? "rdzen-neutralizacji" : "rdzen-neutralizacji-hit",
                    "&#00FF00⚠ &8Ξ &#00FF00Rdzeń Neutralizacji &8〢 &#00FF00Neutralizacja wygasła"
                );
            }
        }, (int)((untilMillis - System.currentTimeMillis()) / 50L));
    }

    private boolean isRdzenActive(UUID uuid, boolean checkSelf) {
        Map<UUID, Long> map = checkSelf ? rdzenNeutralizacjiSelfUntil : rdzenNeutralizacjiOthersUntil;
        Long until = map.get(uuid);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            map.remove(uuid);
            return false;
        }
        return true;
    }

    public boolean isPlayerNeutralized(Player player) {
        if (player == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        return isRdzenActive(uuid, true) || isRdzenActive(uuid, false);
    }

    // ========== KONIEC NOWYCH EVENTOWEK ==========

    private enum ActionType {
        NONE,
        WARDEN_HEART,
        HOOK,
        DRAGON_FIREWORK,
        HARRY_WAND,
        WEB_GRENADE,
        TELEPORT_SNOWBALL,
        KNOCKBACK_GRENADE,
        SONIC_BOOTS,
        LAMPION,
        COBWEB_REMOVER,
        STUN_BONE,
        PULL_CROSSBOW,
        LAVA_BOOTS,
        TURTLE_SHELL,
        SAND_CASTLE,
        WATER_PISTOL,
        STAR_CHESTPLATE,
        EASY_BOOST,
        KOKOS,
        BOMBA_KAPITANA,
        LINA_CECCO,
        USCISK_VADERA,
        RETROSPEKCJA_SKYWALKERA,
        TORBA,
        KARTY_PIEKLA,
        MAGICZNY_KRYSZTAL,
        LASKA_MISTRZA_WU,
        SERCE_ZIMY,
        SMOCZY_KIEL,
        SERCE_WAMPIRA,
        EGZEKUTOR,
        MASKA_ZLUDZENIA,
        RDZEN_NEUTRALIZACJI
    }

    private record Definition(String id, String configKey, ActionType action, int slot, ItemStack item) {
    }

    private record SkywalkerRecall(Location saved, BukkitTask bar, BukkitTask finish) {
    }

    private record ActivePotionEffect(int amplifier, long expiresAtMillis) {
    }

    private final class MaskaNpcRun {
        private final Player owner;
        private final Npc npc;
        private final Vector direction;
        private Location location;
        private BukkitTask movementTask;
        private BukkitTask expiryTask;
        private double speed;
        private boolean stopped;

        private MaskaNpcRun(Player owner, Location location, Vector direction, ItemStack heldItem) {
            this.owner = owner;
            this.location = location.clone();
            this.direction = direction.clone().normalize();
            PlayerProfile profile = owner.getPlayerProfile();
            SkinData skin = null;
            for (ProfileProperty property : profile.getProperties()) {
                if ("textures".equalsIgnoreCase(property.getName())) {
                    skin = new SkinData(owner.getUniqueId().toString(), SkinData.SkinVariant.AUTO,
                        property.getValue(), property.getSignature());
                    break;
                }
            }
            if (skin == null) {
                try {
                    skin = FancyNpcsPlugin.get().getSkinManager().getByUUID(owner.getUniqueId(), SkinData.SkinVariant.AUTO);
                } catch (Throwable ignored) {
                }
            }
            if (skin == null) skin = new SkinData(owner.getUniqueId().toString(), SkinData.SkinVariant.AUTO);

            EntityEquipment equipment = owner.getEquipment();
            Map<NpcEquipmentSlot, ItemStack> copy = new EnumMap<>(NpcEquipmentSlot.class);
            if (equipment != null) {
                addEquipment(copy, NpcEquipmentSlot.MAINHAND, heldItem);
                addEquipment(copy, NpcEquipmentSlot.OFFHAND, equipment.getItemInOffHand());
                addEquipment(copy, NpcEquipmentSlot.HEAD, equipment.getHelmet());
                addEquipment(copy, NpcEquipmentSlot.CHEST, equipment.getChestplate());
                addEquipment(copy, NpcEquipmentSlot.LEGS, equipment.getLeggings());
                addEquipment(copy, NpcEquipmentSlot.FEET, equipment.getBoots());
            }
            NpcData data = new NpcData("holly_maska_" + owner.getUniqueId().toString().replace("-", ""),
                UUID.randomUUID(), this.location.clone())
                .setType(EntityType.PLAYER)
                .setDisplayName(owner.getName())
                .setShowInTab(false)
                .setSpawnEntity(true)
                .setCollidable(false)
                .setTurnToPlayer(false)
                .setSkinData(skin)
                .setEquipment(copy);
            this.npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            this.npc.setSaveToFile(false);
        }

        private void start(int durationTicks, double speed) {
            this.speed = speed;
            expiryTask = Bukkit.getScheduler().runTaskLater(plugin, this::stop, durationTicks);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    if (stopped || maskaRuns.get(owner.getUniqueId()) != this) return;
                    FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
                    npc.create();
                    npc.spawnForAll();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (stopped || maskaRuns.get(owner.getUniqueId()) != this) {
                            stop();
                            return;
                        }
                        movementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::move, 1L, 1L);
                    });
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.WARNING, "Nie udalo sie utworzyc NPC Maski Zludzenia.", throwable);
                    Bukkit.getScheduler().runTask(plugin, this::stop);
                }
            });
        }

        private void move() {
            if (stopped || !owner.isOnline() || owner.isDead() || location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                stop();
                return;
            }
            location.add(direction.clone().multiply(speed));
            location.setYaw(owner.getLocation().getYaw());
            location.setPitch(0.0F);
            npc.getData().setLocation(location.clone());
            npc.moveForAll();
        }

        private void spawnFor(Player viewer) {
            if (stopped || viewer == null || !viewer.isOnline() || viewer.getWorld() != location.getWorld()) return;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    if (!stopped) npc.spawn(viewer);
                } catch (Throwable ignored) {
                }
            });
        }

        private void stop() {
            if (stopped) return;
            stopped = true;
            maskaRuns.remove(owner.getUniqueId(), this);
            if (movementTask != null) movementTask.cancel();
            if (expiryTask != null) expiryTask.cancel();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    npc.removeForAll();
                } catch (Throwable ignored) {
                } finally {
                    try {
                        FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        private void addEquipment(Map<NpcEquipmentSlot, ItemStack> equipment, NpcEquipmentSlot slot, ItemStack item) {
            if (item != null && !item.getType().isAir()) equipment.put(slot, item.clone());
        }
    }

    private static final class GuiHolder implements InventoryHolder {
        private final int page;
        private final int totalPages;
        private Inventory inventory;

        private GuiHolder(int page, int totalPages) {
            this.page = page;
            this.totalPages = totalPages;
        }

        private int page() {
            return page;
        }

        private int totalPages() {
            return totalPages;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final class SandCastleFight {
        private final Set<UUID> participants;
        private final Location center;
        private final double radius;
        private final List<Location> placedBlocks = new ArrayList<>();
        private final Set<String> preexistingPlaceables = new HashSet<>();
        private SchematicPaster.Animation schematicAnimation;
        private BukkitTask task;
        private long endsAt;
        private boolean ended;

        private SandCastleFight(Set<UUID> participants, Location center, double radius) {
            this.participants = participants;
            this.center = center;
            this.radius = radius;
        }
    }
}
