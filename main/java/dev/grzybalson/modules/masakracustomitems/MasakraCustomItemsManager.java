package dev.grzybalson.modules.masakracustomitems;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.modules.upgrade.UpgradeType;
import dev.grzybalson.utils.ColorUtils;
import dev.grzybalson.utils.HeadUtils;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Independent event items; abilities are identified exclusively by persistent IDs. */
public final class MasakraCustomItemsManager implements Listener, CommandExecutor, TabCompleter {
    private static final String RESOURCE = "addons/masakra-customitems/masakra-customitems.yml";
    private static final String PERMISSION = "Holly.masakra.items";
    private final HollyCore plugin;
    private final NamespacedKey itemKey;
    private final MasakraConfigStore configStore;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Mark> marks = new HashMap<>();
    private final Map<UUID, Long> reflections = new HashMap<>();
    private final Map<UUID, Long> shields = new HashMap<>();
    private final Map<UUID, Set<UUID>> shieldNotified = new HashMap<>();
    private final Map<UUID, Double> redirectedHits = new HashMap<>();
    private final Map<UUID, List<GlowTeam>> glowTeams = new HashMap<>();
    private final Map<String, ItemSound> itemSounds = new HashMap<>();
    private final Map<UUID, Map<String, Long>> soundCooldowns = new HashMap<>();
    private final Set<UUID> activeHeads = new HashSet<>();
    private final BukkitTask task;
    private YamlConfiguration config;
    private MasakraUtilityItems utilityItems;
    private org.bukkit.profile.PlayerProfile headProfile;
    private long headProfileRequest;

    public MasakraCustomItemsManager(HollyCore plugin) {
        this.plugin = plugin;
        configStore = new MasakraConfigStore(plugin.getDataFolder(), () -> plugin.getResource(RESOURCE));
        itemKey = new NamespacedKey(plugin, "masakra-item");
        if (!reload()) {
            // Keep a broken user file untouched; run with bundled values until it is corrected.
            try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(RESOURCE)), StandardCharsets.UTF_8)) {
                config = YamlConfiguration.loadConfiguration(reader);
            } catch (IOException exception) {
                throw new IllegalStateException("Nie można odczytać domyślnego configu Masakra", exception);
            }
            loadSounds(false);
            loadHeadProfile();
        }
        utilityItems = new MasakraUtilityItems(plugin, this);
        Bukkit.getPluginManager().registerEvents(utilityItems, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 10L);
    }

    public static void ensureConfigFile(HollyCore plugin) {
        try {
            new MasakraConfigStore(plugin.getDataFolder(), () -> plugin.getResource(RESOURCE)).ensureFile();
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("[Masakra] Nie można przygotować configu; istniejący plik pozostawiono bez zmian: " + exception.getMessage());
        }
    }

    public boolean reload() {
        YamlConfiguration loaded;
        try {
            loaded = configStore.load();
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("[Masakra] Nie można wczytać " + configStore.getFile().getAbsolutePath()
                + ". Plik nie został nadpisany. Popraw YAML i użyj /krzysmcitems reload. " + exception.getMessage());
            return false;
        }
        clearEffects();
        config = loaded;
        loadSounds(true);
        loadHeadProfile();
        plugin.getLogger().info("[Masakra] Config: " + configStore.getFile().getAbsolutePath());
        return true;
    }

    private void loadSounds(boolean saveMissingOptions) {
        itemSounds.clear();
        soundCooldowns.clear();
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(RESOURCE)), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = false;
            for (String id : defaults.getConfigurationSection("items").getKeys(false)) {
                if (!config.isConfigurationSection("items." + id)) continue;
                String base = path(id) + "sounds";
                var section = defaults.getConfigurationSection(base);
                if (section == null) continue;
                for (var entry : section.getValues(true).entrySet()) {
                    if (entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection) continue;
                    if (!config.contains(base + "." + entry.getKey())) {
                        config.set(base + "." + entry.getKey(), entry.getValue());
                        changed = true;
                    }
                }
            }
            if (changed && saveMissingOptions) configStore.save(config);
        } catch (IOException exception) {
            plugin.getLogger().warning("[Masakra] Nie udało się zapisać ustawień dźwięków: " + exception.getMessage());
        }
        for (String id : ids()) for (String event : List.of("used", "received")) {
            String base = path(id) + "sounds." + event;
            String name = config.getString(base + ".name", "NONE").trim();
            if (!config.getBoolean(base + ".enabled", true) || name.equalsIgnoreCase("NONE") || name.isEmpty()) continue;
            try {
                Sound sound = Sound.valueOf(name.toUpperCase(Locale.ROOT));
                double volume = config.getDouble(base + ".volume", 0.8);
                double pitch = config.getDouble(base + ".pitch", 1.0);
                itemSounds.put(id + ":" + event, new ItemSound(sound,
                    (float) (Double.isFinite(volume) ? Math.max(0, Math.min(4, volume)) : 0.8),
                    (float) (Double.isFinite(pitch) ? Math.max(0.5, Math.min(2, pitch)) : 1.0),
                    Math.max(0, config.getLong(base + ".min-interval-ms", 250))));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("[Masakra] Nieznany dźwięk " + name + " w " + base + "; pominięto ten dźwięk.");
            }
        }
    }

    private void loadHeadProfile() {
        long request = ++headProfileRequest;
        String owner = config.getString("items.glowa-krzysimira.owner", "Krzysimir_YT").trim();
        headProfile = null;
        if (!owner.matches("[A-Za-z0-9_]{1,16}")) {
            plugin.getLogger().warning("[Masakra] Nieprawidłowy nick właściciela główki: " + owner);
            return;
        }
        String texture = config.getString("items.glowa-krzysimira.texture", "");
        headProfile = texturedProfile(owner, texture);
        if (headProfile != null) return;

        File cacheFile = new File(HollyDataPaths.getAddonFolder(plugin, "masakra-customitems"), "skin-cache.yml");
        YamlConfiguration cache = YamlConfiguration.loadConfiguration(cacheFile);
        if (owner.equalsIgnoreCase(cache.getString("owner", ""))) {
            headProfile = texturedProfile(owner, cache.getString("texture", ""));
        }

        // Resolve the Mojang UUID before asking the session service for textures.
        // update() on a name-only Bukkit profile can pass a null UUID to that
        // service on offline/proxy servers. Force online lookup semantics here.
        PlayerProfile requested = Bukkit.createProfile(null, owner);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                requested.complete(false, true);
                if (requested.getId() == null) {
                    plugin.getLogger().warning("[Masakra] Nie udało się ustalić UUID Mojang gracza " + owner
                        + ". Zachowano zapisany skin; możesz też ustawić items.glowa-krzysimira.texture.");
                    return;
                }
                requested.complete(true, true);
                if (!requested.hasTextures() || requested.getTextures().getSkin() == null) {
                    plugin.getLogger().warning("[Masakra] Brak profilu premium lub tekstury dla " + owner
                        + ". Zachowano zapisany skin; możesz też ustawić items.glowa-krzysimira.texture.");
                    return;
                }
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (request != headProfileRequest) return;
                    headProfile = requested;
                    String value = requested.getProperties().stream().filter(p -> p.getName().equals("textures"))
                        .map(ProfileProperty::getValue).findFirst().orElse("");
                    cache.set("owner", owner);
                    cache.set("texture", value);
                    try {
                        cache.save(cacheFile);
                    } catch (IOException exception) {
                        plugin.getLogger().warning("[Masakra] Nie udało się zapisać skina: " + exception.getMessage());
                    }
                });
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("[Masakra] Pobranie skina " + owner + " nie powiodło się; zachowano zapisany skin: "
                    + exception.getMessage());
            }
        });
    }

    private org.bukkit.profile.PlayerProfile texturedProfile(String owner, String texture) {
        String value = HeadUtils.normalizeToBase64(texture);
        if (value == null || value.isBlank()) return null;
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), owner);
            profile.setProperty(new ProfileProperty("textures", value));
            return profile.getTextures().getSkin() != null ? profile : null;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[Masakra] Nieprawidłowa zapisana tekstura główki: " + exception.getMessage());
            return null;
        }
    }

    private String path(String id) { return "items." + id + "."; }
    private boolean enabled(String id) { return config.getBoolean(path(id) + "enabled", true); }
    private List<String> ids() {
        var section = config.getConfigurationSection("items");
        return section == null ? List.of() : section.getKeys(false).stream().filter(this::enabled).toList();
    }

    private String storedId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        String id = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return id != null && config.isConfigurationSection("items." + id) ? id : "";
    }

    String id(ItemStack item) {
        String id = storedId(item);
        return !id.isEmpty() && enabled(id) ? id : "";
    }

    void resetCooldown(UUID playerId) {
        cooldowns.remove(playerId);
        soundCooldowns.remove(playerId);
    }

    void resetAllCooldowns() {
        cooldowns.clear();
        soundCooldowns.clear();
    }

    YamlConfiguration settings() { return config; }

    /** Removes only successfully stored items from the supplied real loot list. */
    public void collectMisakoDrops(Player killer, Player victim, List<ItemStack> drops) {
        if (plugin.getCreatorModeManager() != null
            && plugin.getCreatorModeManager().shouldPreventDrops(victim, killer)) return;
        if (utilityItems != null && !blocked(victim.getLocation(), MasakraUtilityItems.BAG, false)) utilityItems.collectDrops(killer, drops);
    }

    public boolean isMarked(Player player) {
        Mark mark = marks.get(player.getUniqueId());
        return mark != null && mark.until > System.currentTimeMillis() && !blocked(player.getLocation(), "topor-zlotej-goraczki", true);
    }

    public double getMiningMultiplier(Player player, ItemStack tool, Location location) {
        return id(tool).equals("kilof-6-3-3") && !blocked(location, "kilof-6-3-3", false)
            && !blocked(player.getLocation(), "kilof-6-3-3", false)
            ? 1.0 + Math.max(0, config.getDouble("items.kilof-6-3-3.bonus-percent", 15)) / 100.0 : 1.0;
    }

    private ItemStack createItem(String id) {
        Material material = Material.matchMaterial(config.getString(path(id) + "material", "STONE"));
        ItemStack item = new ItemStack(material == null || !material.isItem() ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtils.colorizeComponent(config.getString(path(id) + "name", id)).decoration(TextDecoration.ITALIC, false));
        meta.lore(itemLore(id, null));
        meta.setCustomModelData(config.getInt(path(id) + "custom-model-data", 0));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        if (meta instanceof SkullMeta skull && headProfile != null) skull.setOwnerProfile(headProfile);
        if (!meta.equals(item.getItemMeta())) item.setItemMeta(meta);
        if (id.equals(MasakraUtilityItems.BAG) && utilityItems != null) utilityItems.identifyBag(item);
        syncItem(item);
        return item;
    }

    private ItemStack bestTemplate(UpgradeType type) {
        var upgrade = plugin.getUpgradeManager();
        if (upgrade == null) return null;
        return upgrade.getBestConfiguredItem(type, type == UpgradeType.HELMET ? Material.PLAYER_HEAD : null);
    }

    private List<net.kyori.adventure.text.Component> itemLore(String id, ItemStack best) {
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : config.getStringList(path(id) + "lore")) {
            if (line.trim().equals("{UPGRADE_LORE}")) {
                if (best != null && plugin.getUpgradeManager() != null) {
                    UpgradeType type = id.equals("glowa-krzysimira") ? UpgradeType.HELMET : UpgradeType.PICKAXE;
                    for (String stat : plugin.getUpgradeManager().getEnchantmentLore(best, type)) {
                        lore.add(ColorUtils.colorizeComponent(stat).decoration(TextDecoration.ITALIC, false));
                    }
                }
                continue;
            }
            line = line.replace("{COOLDOWN}", String.valueOf(config.getInt(path(id) + "cooldown-seconds", 30)))
                .replace("{DURATION}", String.valueOf(config.getInt(path(id) + "duration-seconds", 3)))
                .replace("{RADIUS}", String.valueOf(Math.max(1, Math.min(6, config.getInt(path(id) + "radius", 3)))))
                .replace("{RETURN_TIME}", String.valueOf(Math.max(1, Math.min(120, config.getLong(path(id) + "return-seconds", 20)))))
                .replace("{CAPACITY}", String.valueOf(MisakoBagItems.SIZE))
                .replace("{BONUS}", String.valueOf(config.getInt(path(id) + "bonus-percent", 15)))
                .replace("{DAMAGE_BONUS}", String.valueOf(config.getInt(path(id) + "bonus-damage-percent", 25)))
                .replace("{REDUCTION}", String.valueOf(config.getInt(path(id) + "reduction-percent", 30)));
            lore.add(ColorUtils.colorizeComponent(line).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }

    /** Called on every inventory access, use and periodic refresh, including items from storage. */
    private void syncItem(ItemStack item) {
        String id = storedId(item);
        if (id.isEmpty()) return;
        if (id.equals(MasakraUtilityItems.BAG)) {
            Material material = Material.matchMaterial(config.getString(path(id) + "material", "CYAN_DYE"));
            if (material != null && material.isItem() && item.getType() != material) {
                // Retain the bag's PDC identity when updating an already issued bag's colour.
                ItemMeta previous = item.getItemMeta();
                item.setType(material);
                item.setItemMeta(previous);
            }
        }
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        if (meta instanceof Damageable damageable) damageable.setDamage(0);
        if (!enabled(id)) {
            if (!meta.equals(item.getItemMeta())) item.setItemMeta(meta);
            return;
        }
        boolean head = id.equals("glowa-krzysimira");
        boolean pickaxe = id.equals("kilof-6-3-3");
        ItemStack best = head || pickaxe ? bestTemplate(head ? UpgradeType.HELMET : UpgradeType.PICKAXE) : null;
        meta.displayName(ColorUtils.colorizeComponent(config.getString(path(id) + "name", id)).decoration(TextDecoration.ITALIC, false));
        meta.lore(itemLore(id, best));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        if (head || pickaxe) {
            for (Enchantment enchant : new HashSet<>(meta.getEnchants().keySet())) meta.removeEnchant(enchant);
            if (best != null) best.getEnchantments().forEach((enchant, level) -> meta.addEnchant(enchant, level, true));
        }
        if (head) {
            if (meta instanceof SkullMeta skull && headProfile != null && headProfile.getTextures().getSkin() != null) {
                skull.setOwnerProfile(headProfile);
            }
            // Use the real PLAYER_HEAD upgrade's armor attributes, preserving each operation.
            for (Attribute attribute : List.of(Attribute.GENERIC_ARMOR, Attribute.GENERIC_ARMOR_TOUGHNESS, Attribute.GENERIC_KNOCKBACK_RESISTANCE)) {
                meta.removeAttributeModifier(attribute);
                Collection<AttributeModifier> modifiers = best != null && best.hasItemMeta()
                    ? best.getItemMeta().getAttributeModifiers(attribute) : null;
                if (modifiers != null) for (AttributeModifier modifier : modifiers) meta.addAttributeModifier(attribute, modifier);
            }
        }
        if (!meta.equals(item.getItemMeta())) item.setItemMeta(meta);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) { message(sender, "no-permission"); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("resetcooldown")) {
            if (args.length != 2) { message(sender, "usage"); return true; }
            if (args[1].equalsIgnoreCase("all")) {
                resetAllCooldowns();
                message(sender, "cooldown-reset-all");
                return true;
            }
            Player online = Bukkit.getPlayerExact(args[1]);
            OfflinePlayer target = online != null ? online : Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.cooldown-reset-not-found",
                    "&cNie znaleziono gracza {PLAYER}.").replace("{PLAYER}", args[1])));
                return true;
            }
            resetCooldown(target.getUniqueId());
            sender.sendMessage(ColorUtils.colorizeComponent(config.getString("messages.cooldown-reset-player",
                "&aZresetowano cooldowny gracza {PLAYER}.").replace("{PLAYER}", Objects.toString(target.getName(), args[1]))));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (reload()) message(sender, "reloaded");
            else sender.sendMessage(ColorUtils.colorizeComponent("&cBłąd configu: &f" + configStore.getFile().getPath()
                + "&c. Popraw YAML; Twoje ustawienia nie zostały nadpisane."));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("config")) {
            sender.sendMessage(ColorUtils.colorizeComponent("&7Config Masakra: &f" + configStore.getFile().getPath()));
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !ids().contains(args[2])) { message(sender, "usage"); return true; }
            give(target, args[2]); return true;
        }
        if (args.length != 0) { message(sender, "usage"); return true; }
        if (!(sender instanceof Player player)) { message(sender, "only-player"); return true; }
        Menu menu = new Menu();
        int rows = Math.max(1, Math.min(6, config.getInt("gui.rows", 6)));
        menu.inventory = Bukkit.createInventory(menu, rows * 9, ColorUtils.colorizeComponent(config.getString("gui.title", "&8Przedmioty eventowe")));
        for (String id : ids()) {
            int slot = config.getInt(path(id) + "slot", menu.slots.size());
            if (slot >= 0 && slot < menu.inventory.getSize()) { menu.slots.put(slot, id); menu.inventory.setItem(slot, createItem(id)); }
        }
        player.openInventory(menu.inventory);
        return true;
    }

    private void give(Player player, String id) {
        if (player.getInventory().firstEmpty() == -1) { message(player, "inventory-full"); return; }
        player.getInventory().addItem(createItem(id));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        List<String> options = args.length == 1 ? List.of("reload", "config", "give", "resetcooldown")
            : args.length == 2 && args[0].equalsIgnoreCase("resetcooldown")
                ? java.util.stream.Stream.concat(java.util.stream.Stream.of("all"), Bukkit.getOnlinePlayers().stream().map(Player::getName)).toList()
            : args.length == 2 && args[0].equalsIgnoreCase("give") ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
            : args.length == 3 && args[0].equalsIgnoreCase("give") ? ids() : List.of();
        return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission(PERMISSION)) return;
        String id = menu.slots.get(event.getRawSlot());
        if (id != null && event.getClick() != ClickType.DOUBLE_CLICK) give(player, id);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        for (ItemStack item : event.getInventory().getContents()) syncItem(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        String id = id(event.getItem());
        if (!Set.of("krwisty-kiel", "ognisty-koszmar", "glowa-krzysimira").contains(id)) return;
        // RIGHT_CLICK_AIR can be pre-cancelled because a dye has no vanilla action.
        if (event.useItemInHand() == Event.Result.DENY) return;
        event.setCancelled(true); // In particular, event heads and campfires must never be placed.
        Player player = event.getPlayer();
        if (id.equals("glowa-krzysimira")) {
            if (blocked(player.getLocation(), id, false)) { message(player, "blocked-region"); return; }
            ItemStack helmet = player.getInventory().getHelmet();
            if (helmet != null && !helmet.getType().isAir()) { message(player, "helmet-occupied"); return; }
            syncItem(event.getItem());
            ItemStack equipped = event.getItem().clone(); equipped.setAmount(1);
            event.getItem().setAmount(event.getItem().getAmount() - 1);
            player.getInventory().setHelmet(equipped);
            activeHeads.add(player.getUniqueId());
            title(player, id, "used", player); return;
        }
        if (!start(player, id, null, false)) return;
        long until = System.currentTimeMillis() + duration(id);
        if (id.equals("krwisty-kiel")) reflections.put(player.getUniqueId(), until);
        else {
            shields.put(player.getUniqueId(), until);
            shieldNotified.remove(player.getUniqueId());
        }
        title(player, id, "used", player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) return;
        Player player = event.getPlayer();
        String id = id(player.getInventory().getItemInMainHand());
        if (!id.equals("lapka-ziomeczka")) return;
        event.setCancelled(true);
        if (!start(player, id, target, false)) return;
        int ticks = (int) (duration(id) / 50);
        target.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, ticks, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0));
        title(player, id, "used", target); title(target, id, "received", player);
    }

    boolean start(Player player, String id, Player target, boolean hostile) {
        if (blocked(player.getLocation(), id, hostile) || (target != null && blocked(target.getLocation(), id, hostile))) {
            message(player, "blocked-region"); return false;
        }
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long now = System.currentTimeMillis();
        long remaining = playerCooldowns.getOrDefault(id, 0L) - now;
        if (remaining > 0) {
            player.sendActionBar(ColorUtils.colorizeComponent(config.getString("messages.cooldown", "&cOdczekaj {TIME} s.")
                .replace("{TIME}", String.valueOf((remaining + 999) / 1000))));
            return false;
        }
        playerCooldowns.put(id, now + Math.max(0, config.getLong(path(id) + "cooldown-seconds", 30)) * 1000L);
        return true;
    }

    /** Registered after the PvP damage calculators; operates on their resulting damage. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)) return;
        Double redirected = redirectedHits.get(victim.getUniqueId());
        if (redirected != null) { setFinalBeforeAbsorption(event, redirected); return; }
        if (active(reflections, victim) && !blocked(victim.getLocation(), "krwisty-kiel", true)
            && !blocked(attacker.getLocation(), "krwisty-kiel", true)) {
            double damage = beforeAbsorption(event);
            event.setCancelled(true);
            event.setDamage(0);
            if (damage > 0) {
                redirectedHits.put(attacker.getUniqueId(), damage);
                try { attacker.damage(damage, victim); }
                finally { redirectedHits.remove(attacker.getUniqueId()); }
                title(attacker, "krwisty-kiel", "received", victim);
            }
            return;
        }
        double multiplier = 1;
        Mark mark = marks.get(victim.getUniqueId());
        if (mark != null && mark.owner.equals(attacker.getUniqueId()) && isMarked(victim)
            && !blocked(attacker.getLocation(), "topor-zlotej-goraczki", true)) {
            multiplier *= 1 + config.getDouble("items.topor-zlotej-goraczki.bonus-damage-percent", 25) / 100;
        }
        if (active(shields, victim) && !blocked(victim.getLocation(), "ognisty-koszmar", true)
            && !blocked(attacker.getLocation(), "ognisty-koszmar", true)) {
            multiplier *= 1 - Math.max(0, Math.min(100, config.getDouble("items.ognisty-koszmar.reduction-percent", 30))) / 100;
            if (shieldNotified.computeIfAbsent(victim.getUniqueId(), ignored -> new HashSet<>()).add(attacker.getUniqueId())) {
                title(attacker, "ognisty-koszmar", "received", victim);
            }
        }
        if (multiplier != 1) setFinalBeforeAbsorption(event, beforeAbsorption(event) * Math.max(0, multiplier));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAxeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof Player target)
            || player.equals(target) || !redirectedHits.isEmpty() || beforeAbsorption(event) <= 0
            || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        String id = id(player.getInventory().getItemInMainHand());
        if (!id.equals("topor-zlotej-goraczki") || !start(player, id, target, true)) return;
        Mark old = marks.get(target.getUniqueId());
        marks.put(target.getUniqueId(), new Mark(player.getUniqueId(), System.currentTimeMillis() + duration(id),
            old != null ? old.wasGlowing : target.isGlowing()));
        target.setGlowing(true);
        refreshGlow(target);
        target.getWorld().strikeLightningEffect(target.getLocation());
        title(player, id, "used", target); title(target, id, "received", player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (isMarked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDurability(PlayerItemDamageEvent event) {
        if (!storedId(event.getItem()).isEmpty()) {
            event.setCancelled(true);
            syncItem(event.getItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (Set.of("glowa-krzysimira", "ognisty-koszmar").contains(id(event.getItemInHand()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPrepareMine(BlockBreakEvent event) {
        syncItem(event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMiningSound(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String id = id(player.getInventory().getItemInMainHand());
        if (!id.equals("kilof-6-3-3") || player.getGameMode() != GameMode.SURVIVAL
            || blocked(player.getLocation(), id, false) || blocked(event.getBlock().getLocation(), id, false)) return;
        playItemSound(player, id, "used");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!id(tool).equals("kilof-6-3-3") || player.getGameMode() != GameMode.SURVIVAL || !event.isDropItems()) return;
        if (blocked(event.getBlock().getLocation(), "kilof-6-3-3", false) || blocked(player.getLocation(), "kilof-6-3-3", false)) return;
        // Golden tools cannot harvest many ores. Use the best pickaxe's harvest tier and enchants.
        ItemStack harvestTool = bestTemplate(UpgradeType.PICKAXE);
        if (harvestTool == null) {
            harvestTool = new ItemStack(Material.NETHERITE_PICKAXE);
            harvestTool.addUnsafeEnchantments(tool.getEnchantments());
        }
        List<ItemStack> drops = event.getBlock().getDrops(harvestTool, player).stream().map(ItemStack::clone).toList();
        event.setDropItems(false);
        Location location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        double multiplier = getMiningMultiplier(player, tool, location);
        var szafka = plugin.getMasakraSzafkaManager();
        Material mined = event.getBlock().getType();
        Location blockLocation = event.getBlock().getLocation();
        double wardrobeBonus = szafka != null ? szafka.miningBonus(player, blockLocation) : 0;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled()) return;
            for (ItemStack drop : drops) {
                // Never multiply containers or their contents (shulkers, bundles, etc.).
                double rate = multiplier - 1 + (wardrobeBonus > 0 && szafka.multipliesDrop(mined, drop, blockLocation) ? wardrobeBonus : 0);
                double expectedBonus = drop.getMaxStackSize() > 1 && !drop.hasItemMeta() ? drop.getAmount() * rate : 0;
                int bonus = (int) expectedBonus + (ThreadLocalRandom.current().nextDouble() < expectedBonus % 1 ? 1 : 0);
                int remaining = drop.getAmount() + bonus;
                while (remaining > 0) {
                    ItemStack stack = drop.clone(); stack.setAmount(Math.min(stack.getMaxStackSize(), remaining));
                    location.getWorld().dropItemNaturally(location, stack); remaining -= stack.getAmount();
                }
            }
        });
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID uuid : new ArrayList<>(marks.keySet())) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || !isMarked(target)) clearMark(uuid);
        }
        reflections.values().removeIf(until -> until <= now);
        shields.values().removeIf(until -> until <= now);
        shieldNotified.keySet().removeIf(uuid -> !shields.containsKey(uuid));
        cooldowns.values().forEach(map -> map.values().removeIf(until -> until <= now));
        cooldowns.values().removeIf(Map::isEmpty);
        soundCooldowns.values().forEach(map -> map.values().removeIf(until -> until <= now));
        soundCooldowns.values().removeIf(Map::isEmpty);
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) syncItem(item);
            Inventory top = player.getOpenInventory().getTopInventory();
            for (ItemStack item : top.getContents()) syncItem(item);
            if (id(player.getInventory().getHelmet()).equals("glowa-krzysimira")
                && !blocked(player.getLocation(), "glowa-krzysimira", false)) {
                if (activeHeads.add(player.getUniqueId())) playItemSound(player, "glowa-krzysimira", "used");
                player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 30, 0, true, false, true));
            } else {
                activeHeads.remove(player.getUniqueId());
            }
        }
    }

    boolean blocked(Location location, String id, boolean hostile) {
        if (location == null || location.getWorld() == null) return true;
        if (config.getStringList("blocked-worlds").stream().anyMatch(w -> w.equalsIgnoreCase(location.getWorld().getName()))) return true;
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        var manager = container.get(BukkitAdapter.adapt(location.getWorld()));
        Set<String> names = new HashSet<>(config.getStringList("blocked-regions"));
        names.addAll(config.getStringList(path(id) + "blocked-regions"));
        if (manager != null) for (String name : names) {
            var region = manager.getRegion(name.toLowerCase(Locale.ROOT));
            if (region != null && (name.equalsIgnoreCase("__global__") || region.contains(BukkitAdapter.asBlockVector(location)))) return true;
        }
        return hostile && config.getBoolean("respect-pvp-deny", true)
            && container.createQuery().getApplicableRegions(BukkitAdapter.adapt(location)).queryState(null, Flags.PVP) == StateFlag.State.DENY;
    }

    private void refreshGlow(Player target) {
        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().refreshPlayerIdentity(target.getUniqueId());
            return;
        }
        if (glowTeams.containsKey(target.getUniqueId())) return;
        List<GlowTeam> states = new ArrayList<>();
        Set<Scoreboard> boards = Collections.newSetFromMap(new IdentityHashMap<>());
        Bukkit.getOnlinePlayers().forEach(viewer -> boards.add(viewer.getScoreboard()));
        for (Scoreboard board : boards) {
            Team previous = board.getEntryTeam(target.getName());
            String name = "mk" + target.getUniqueId().toString().replace("-", "").substring(0, 14);
            Team team = board.getTeam(name);
            if (team == null) team = board.registerNewTeam(name);
            team.setColor(ChatColor.RED);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.addEntry(target.getName());
            states.add(new GlowTeam(team, previous));
        }
        glowTeams.put(target.getUniqueId(), states);
    }

    private void clearMark(UUID uuid) {
        Mark mark = marks.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (mark != null && player != null) player.setGlowing(mark.wasGlowing);
        List<GlowTeam> states = glowTeams.remove(uuid);
        if (states != null) for (GlowTeam state : states) {
            for (String entry : new HashSet<>(state.team.getEntries())) {
                state.team.removeEntry(entry);
                if (state.previous != null && state.previous.getScoreboard() != null) state.previous.addEntry(entry);
            }
            state.team.unregister();
        }
        if (player != null && plugin.getTablistManager() != null) plugin.getTablistManager().refreshPlayerIdentity(uuid);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { clearPlayer(event.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { clearPlayer(event.getEntity()); }

    private void clearPlayer(Player player) {
        clearMark(player.getUniqueId());
        for (UUID uuid : new ArrayList<>(marks.keySet())) if (marks.get(uuid).owner.equals(player.getUniqueId())) clearMark(uuid);
        reflections.remove(player.getUniqueId()); shields.remove(player.getUniqueId()); shieldNotified.remove(player.getUniqueId());
        activeHeads.remove(player.getUniqueId());
        soundCooldowns.remove(player.getUniqueId());
        // Keep unexpired cooldowns through death/relog; the periodic task removes expired entries.
    }

    private void clearEffects() {
        if (utilityItems != null) utilityItems.reset();
        for (UUID uuid : new ArrayList<>(marks.keySet())) clearMark(uuid);
        reflections.clear(); shields.clear(); shieldNotified.clear();
    }

    public void shutdown() {
        if (utilityItems != null) utilityItems.shutdown();
        ++headProfileRequest; task.cancel(); clearEffects(); cooldowns.clear();
        activeHeads.clear(); soundCooldowns.clear(); itemSounds.clear();
    }
    private long duration(String id) { return Math.max(1, config.getLong(path(id) + "duration-seconds", 3)) * 1000L; }
    private boolean active(Map<UUID, Long> map, Player player) { return map.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis(); }
    private Player attacker(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Player p ? p
            : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
    }
    private static double beforeAbsorption(EntityDamageEvent event) {
        double value = event.getFinalDamage();
        if (event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)) value -= event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION);
        return Math.max(0, value);
    }
    private static void setFinalBeforeAbsorption(EntityDamageEvent event, double target) {
        for (int i = 0; i < 8; i++) {
            double current = beforeAbsorption(event);
            if (Math.abs(current - target) < 0.0001) break;
            if (target <= 0 || current <= 0) { event.setDamage(0); break; }
            event.setDamage(Math.min(4096, event.getDamage() * target / current));
        }
    }
    private void title(Player player, String id, String type, Player other) {
        String base = path(id) + "titles." + type;
        player.sendTitle(ColorUtils.colorize(config.getString(base + ".title", "&6Przedmiot eventowy")),
            ColorUtils.colorize(config.getString(base + ".subtitle", "&f{PLAYER}").replace("{PLAYER}", other.getName())), 5, 40, 10);
        playItemSound(player, id, type);
    }
    void playItemSound(Player player, String id, String event) {
        String key = id + ":" + event;
        ItemSound sound = itemSounds.get(key);
        if (sound == null || sound.volume <= 0) return;
        long now = System.currentTimeMillis();
        Map<String, Long> playerSounds = soundCooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        if (playerSounds.getOrDefault(key, 0L) > now) return;
        playerSounds.put(key, now + sound.minIntervalMs);
        player.playSound(player.getLocation(), sound.sound, sound.volume, sound.pitch);
    }
    private void message(CommandSender sender, String key) {
        sender.sendMessage(ColorUtils.colorizeComponent(config.getString("messages." + key, "&c" + key)));
    }
    private record Mark(UUID owner, long until, boolean wasGlowing) { }
    private record GlowTeam(Team team, Team previous) { }
    private record ItemSound(Sound sound, float volume, float pitch, long minIntervalMs) { }
    private static final class Menu implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, String> slots = new HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
