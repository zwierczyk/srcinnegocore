package dev.grzybalson.modules.minestarogcustomitems;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.grzybalson.HollyCore;
import dev.grzybalson.config.HollyDataPaths;
import dev.grzybalson.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MinestarOGCustomItemsManager implements Listener, CommandExecutor, TabCompleter {
    private static final String RESOURCE_PATH = "addons/minestarOGCustomItems/minestarOGCustomItems.yml";
    private static final String ADDON_NAME = "minestarOGCustomItems";
    private static final String ITEM_TAG = "minestarog-custom-item-id";
    private static final String PROJECTILE_TAG = "minestarog-custom-projectile-id";
    private static final Set<Material> PROJECTILE_MATERIALS = Set.of(Material.SNOWBALL, Material.SPLASH_POTION, Material.LINGERING_POTION);

    private final HollyCore plugin;
    private final File configFile;
    private final NamespacedKey itemKey;
    private final NamespacedKey projectileKey;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<Material, List<Definition>> definitionsByMaterial = new EnumMap<>(Material.class);
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingProjectiles = new ConcurrentHashMap<>();
    private final List<List<BlockState>> activeTraps = Collections.synchronizedList(new ArrayList<>());

    private YamlConfiguration config;
    private BukkitTask bootsTask;

    public MinestarOGCustomItemsManager(HollyCore plugin) {
        this.plugin = plugin;
        this.configFile = new File(HollyDataPaths.getAddonFolder(plugin, ADDON_NAME), ADDON_NAME + ".yml");
        this.itemKey = new NamespacedKey(plugin, ITEM_TAG);
        this.projectileKey = new NamespacedKey(plugin, PROJECTILE_TAG);
        reload();
        startBootsTask();
    }

    public void reload() {
        HollyDataPaths.ensureResource(plugin, RESOURCE_PATH);
        this.config = YamlConfiguration.loadConfiguration(configFile);
        ensureCobwebRemoverDefault();
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
                plugin.getLogger().warning("Pominieto MinestarOG custom item " + rawId + ": nieprawidlowy material/item.");
                continue;
            }

            String id = normalizeId(rawId);
            ActionType action = parseAction(config.getString("items." + rawId + ".action", "NONE"));
            int slot = config.getInt("items." + rawId + ".slot", definitions.size());
            Definition definition = new Definition(id, rawId, action, slot, tagItem(item, id));
            definitions.put(id, definition);
            definitionsByMaterial.computeIfAbsent(definition.item().getType(), ignored -> new ArrayList<>()).add(definition);
        }
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
            config.set(path + ".name", "&6&lUsuwacz Pajęczyn");
            config.set(path + ".lore", List.of(
                "&8&m--------------------------",
                "&7Magiczny proszek, który &frozrywa pajęczyny",
                "&7wokół gracza w jednej chwili.",
                "",
                "&8✕ &7Użycie: &ePPM",
                "&8✕ &7Zasięg: &63 kratki",
                "&8✕ &7Cooldown: &610 sekund",
                "&8✕ &7Zużycie: &cnie",
                "",
                "&8✕ &7Przedmiot można trafić w &dMistycznej &7Skrzyni",
                "&8&m--------------------------"
            ));
            config.set(path + ".glow", true);
            config.set(path + ".cooldown-seconds", 10);
            config.set(path + ".radius", 3);
            config.set(path + ".consume", false);
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
                plugin.getLogger().log(Level.WARNING, "Nie udało się dopisać Usuwacza Pajęczyn do MinestarOG CustomItems config.yml", exception);
            }
        }
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
        restoreAllTraps();
        cooldowns.clear();
        pendingProjectiles.clear();
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
        GuiHolder holder = new GuiHolder();
        Inventory inventory = Bukkit.createInventory(holder, getGuiSize(), color(config.getString("settings.gui-title", "&8MinestarOG CustomItems")));
        holder.setInventory(inventory);

        definitions.values().stream()
            .sorted(Comparator.comparingInt(Definition::slot))
            .forEach(definition -> {
                if (definition.slot() >= 0 && definition.slot() < inventory.getSize()) {
                    inventory.setItem(definition.slot(), definition.item().clone());
                }
            });

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        Definition definition = getDefinition(identify(event.getCurrentItem()));
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        Definition definition = getDefinition(identifyAndTag(item));
        if (definition == null) {
            return;
        }

        Player player = event.getPlayer();
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
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, seconds(definition, "effect-duration-seconds", 5) * 20, integer(definition, "amplifier", 1), true, true, true));
                playUseFeedback(player, Sound.BLOCK_LANTERN_PLACE);
                consumeIfNeeded(player, event.getHand(), item, definition);
            }
            case COBWEB_REMOVER -> {
                event.setCancelled(true);
                if (!startUse(player, definition, item, true)) return;
                removeCobwebsAround(player, definition);
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
            default -> {
            }
        }
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFish(PlayerFishEvent event) {
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
        resetVisualCooldowns(event.getPlayer());
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

        int cooldownSeconds = seconds(definition, "cooldown-seconds", config.getInt("settings.default-cooldown-seconds", 30));
        if (definition.action() == ActionType.DRAGON_FIREWORK) {
            cooldownSeconds = 0;
        }
        if (cooldownSeconds > 0) {
            cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(definition.id(), System.currentTimeMillis() + cooldownSeconds * 1000L);
            if (item != null && !item.getType().isAir()) {
                player.setCooldown(item.getType(), toTicks(cooldownSeconds));
            }
        }
        return true;
    }

    private void applyNearbyEffect(Player source, Definition definition, PotionEffectType type, boolean includeSelf) {
        double radius = decimal(definition, "radius", 7.0);
        PotionEffect effect = new PotionEffect(type, seconds(definition, "effect-duration-seconds", 5) * 20, integer(definition, "amplifier", 0), true, true, true);

        for (Player target : source.getWorld().getPlayers()) {
            if (!includeSelf && target.getUniqueId().equals(source.getUniqueId())) {
                continue;
            }
            if (target.getLocation().distanceSquared(source.getLocation()) <= radius * radius && !isBlocked(target.getLocation())) {
                target.addPotionEffect(effect);
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
                    if (isReplaceable(block.getType()) && !isBlocked(block.getLocation())) {
                        changed.add(block.getState());
                        block.setType(Material.COBWEB, false);
                    }
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
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, seconds(definition, "effect-duration-seconds", 4) * 20, integer(definition, "amplifier", 1), true, true, true));
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
            Definition boots = getDefinitionByAction(ActionType.SONIC_BOOTS);
            if (boots == null) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack bootsItem = player.getInventory().getBoots();
                String id = identify(bootsItem);
                if (boots.id().equals(id) && !isBlocked(player.getLocation())) {
                    applySonicBootsSpeed(player, boots);
                }
            }
        }, 20L, 20L);
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, amplifier, true, false, true), true);
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
            return serialized;
        }

        Material material = Material.matchMaterial(config.getString(base + ".material", "STONE"));
        if (material == null || material.isAir()) {
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(config.getString(base + ".name", configKey)));
            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList(base + ".lore")) {
                lore.add(color(line));
            }
            meta.setLore(lore);

            if (config.isInt(base + ".custom-model-data")) {
                meta.setCustomModelData(config.getInt(base + ".custom-model-data"));
            }
            if (config.getBoolean(base + ".glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }

        if (item.getItemMeta() instanceof FireworkMeta fireworkMeta) {
            fireworkMeta.setPower(Math.max(1, config.getInt(base + ".firework-flight-duration", 1)));
            item.setItemMeta(fireworkMeta);
        }
        return item;
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

        List<Definition> materialDefinitions = definitionsByMaterial.get(item.getType());
        if (materialDefinitions == null || materialDefinitions.isEmpty()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String id = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
            if (id != null && definitions.containsKey(id)) {
                return id;
            }
        }

        for (Definition definition : materialDefinitions) {
            if (isSimilarWithoutPluginTag(item, definition.item())) {
                return definition.id();
            }
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
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie sprawdzic regionu WorldGuard dla MinestarOG custom itemu.", exception);
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

    private void resetVisualCooldowns(Player player) {
        Set<Material> materials = new HashSet<>();
        definitions.values().forEach(definition -> materials.add(definition.item().getType()));
        materials.forEach(material -> player.setCooldown(material, 0));
    }

    private int getGuiSize() {
        int configured = config.getInt("settings.gui-size", 27);
        int rows = Math.max(1, Math.min(6, (int) Math.ceil(configured / 9.0)));
        return rows * 9;
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
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac configu MinestarOG CustomItems.", exception);
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
        COBWEB_REMOVER
    }

    private record Definition(String id, String configKey, ActionType action, int slot, ItemStack item) {
    }

    private static final class GuiHolder implements InventoryHolder {
        private Inventory inventory;

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
